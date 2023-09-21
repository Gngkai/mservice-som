package mkt.act.rule;

import common.fms.util.FieldUtils;
import common.fnd.FndGlobal;
import common.fnd.FndLog;
import common.sal.sys.basedata.dao.ItemCategoryDao;
import common.sal.sys.basedata.dao.ItemDao;
import common.sal.sys.basedata.dao.impl.ItemCategoryDaoImpl;
import common.sal.sys.basedata.dao.impl.ItemDaoImpl;
import common.sal.sys.sync.dao.*;
import common.sal.sys.sync.dao.impl.*;
import common.sal.sys.sync.service.AvailableDaysService;
import common.sal.sys.sync.service.ItemCacheService;
import common.sal.sys.sync.service.impl.AvailableDaysServiceImpl;
import common.sal.sys.sync.service.impl.ItemCacheServiceImpl;
import kd.bos.algo.Algo;
import kd.bos.algo.AlgoContext;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.ErrorCode;
import kd.bos.exception.KDException;
import kd.bos.formula.FormulaEngine;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.fi.bd.util.QFBuilder;
import mkt.act.rule.service.ActPlanService;
import mkt.act.rule.service.impl.ActPlanServiceImpl;
import mkt.common.MKTCom;
import sal.act.ActShopProfit.aos_sal_act_from;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * @author create by gk
 * @date 2023/8/21 14:11
 * @action  活动规则信息
 */
public class EventRule {
    private static final Log logger = LogFactory.getLog("mkt.act.rule.EventRule");
    //活动库单据
    private  DynamicObject typEntity;
    private DynamicObject actPlanEntity;
    //活动库每行规则的执行结果，key：project+seq,value: (item:result)
    private  Map<String,Map<String,Boolean>> rowResults;
    //国别
    private final DynamicObject orgEntity;
    //国别下所有的物料，方便后续筛选
    private DynamicObjectCollection itemInfoes;
    //日志
    private  FndLog fndLog;
    //物料对应的品类
    private Map<String,DynamicObject> cateItem;
    public EventRule(DynamicObject typeEntity,DynamicObject dy_main){
        try {
            this.typEntity = typeEntity;
            this.actPlanEntity = dy_main;
            this.fndLog = FndLog.init("活动选品明细导入", actPlanEntity.getString("billno"));
            orgEntity = dy_main.getDynamicObject("aos_nationality");
            //获取用于计算的物料
            setItemInfo(null);
            //解析行公式
            rowResults = new HashMap<>();

            parsingRowRule();
            implementFormula();
        }catch (Exception e){
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            if (logger.isErrorEnabled()) {
                logger.error(sw.toString());
            }
            throw new KDException(new ErrorCode("活动选品明细导入异常",e.getMessage()));
        }

    }

    public EventRule(DynamicObject dy_main){
        this.actPlanEntity = dy_main;
        orgEntity = dy_main.getDynamicObject("aos_nationality");
        QFBuilder builder = new QFBuilder();
        if (orgEntity==null){
            return;
        }
        if (actPlanEntity.get("aos_channel")==null){
            return;
        }
        if (actPlanEntity.get("aos_shop")==null){
            return;
        }
        if (actPlanEntity.get("aos_acttype")==null){
            return;
        }
        this.fndLog = FndLog.init("活动选品明细导入", actPlanEntity.getString("billno"));

        builder.add("aos_org","=",orgEntity.getPkValue());
        builder.add("aos_channel","=",actPlanEntity.getDynamicObject("aos_channel").getPkValue());
        builder.add("aos_shop","=",actPlanEntity.getDynamicObject("aos_shop").getPkValue());
        builder.add("aos_acttype","=",actPlanEntity.getDynamicObject("aos_acttype").getPkValue());
        DynamicObject type = QueryServiceHelper.queryOne("aos_sal_act_type_p", "id", builder.toArray());
        if (type==null) {
            return;
        }
        //获取活动规则
        typEntity = BusinessDataServiceHelper.loadSingle(type.getString("id"), "aos_sal_act_type_p");
        //判断公式是否维护了
        if (FndGlobal.IsNull(typEntity.get("aos_rule_v"))) {
            return;
        }

        //获取用于计算的物料
        DynamicObjectCollection entityRows = actPlanEntity.getDynamicObjectCollection("aos_sal_actplanentity");
        List<String> itemids = new ArrayList<>(entityRows.size());
        for (DynamicObject row : entityRows) {
            if (row.getDynamicObject("aos_itemnum")!=null) {
                itemids.add(row.getDynamicObject("aos_itemnum").getString("id"));
            }
        }
        setItemInfo(itemids);

        //解析行公式
        rowResults = new HashMap<>();
        parsingRowRule();
        addData(itemids);
    }

    /**
     * 执行选品公式，判断物料是否应该剔除
     */
    public void implementFormula(){
        String ruleValue = typEntity.getString("aos_rule_v");
        String[] parameterKey = FormulaEngine.extractVariables(ruleValue);
        Map<String,Object> parameters = new HashMap<>(parameterKey.length);
        //选品数
        int selectQty = 100;
        if (FndGlobal.IsNotNull(typEntity.get("aos_qty"))) {
            selectQty = Integer.parseInt(typEntity.getString("aos_qty"));
        }
        //根据国别品类占比确定每个品类的数量
        Map<String, Integer> cateQty = new HashMap<>();
        //最大占比的品类
        String maxProportCate = calCateQty(selectQty, cateQty);
        //通过公式筛选的物料，能够填入单据的数据
        List<DynamicObject> filterItem = new ArrayList<>(selectQty);
        //备用数据，即最大品类占比下对应的物料，如果其他品类缺少数据，就从中取数补上
        int backCateSize = cateQty.getOrDefault("maxCate", 0);
        List<DynamicObject> backupData = new ArrayList<>(backCateSize);
        //记录每个品类已经填入数据
        Map<String,Integer> alreadyFilledCate = new HashMap<>();
        for (Map.Entry<String, Integer> entry : cateQty.entrySet()) {
            alreadyFilledCate.put(entry.getKey(),0);
        }
        //筛选数据
        for (DynamicObject itemInfoe : itemInfoes) {
            String itemId = itemInfoe.getString("id");
            //获取物料品类
            String cate = cateItem.get(itemId).getString("gnumber").split(",")[0];
            if (!alreadyFilledCate.containsKey(cate)){
                continue;
            }
            //判断品类是否填满
            int alreadyQty = alreadyFilledCate.get(cate);
            //品类能填入的最大数据
            int cateTotalQty = cateQty.get(cate);
            //填入类型
            String fillType;
            //正常填入该品类
            if (alreadyQty < cateTotalQty){
                fillType = "A";
            }
            //能够填入备用数据
            else if (cate.equals(maxProportCate) && backupData.size()< backCateSize){
               fillType = "B";
            }
            //品类已满，跳过
            else {
                continue;
            }
            parameters.clear();
            for (String key : parameterKey) {
                parameters.put(key,rowResults.get(key).get(itemId));
            }
            //执行公式
            boolean result = Boolean.parseBoolean(FormulaEngine.execExcelFormula(ruleValue, parameters).toString());
            if (result){
                //正常填入该品类
                if (fillType.equals("A")){
                    filterItem.add(itemInfoe);
                    alreadyQty++;
                    alreadyFilledCate.put(cate,alreadyQty);
                }
                //填入备用数据
                else{
                    backupData.add(itemInfoe);
                }
            }
        }
        //缺失物料时，将备用数据填入
        int missQty = selectQty - filterItem.size();
        int fillBackupDataSize = backupData.size();
        //确定填入的长度
        missQty = Math.min(missQty, fillBackupDataSize);
        for (int index = 0; index < missQty; index++) {
            filterItem.add(backupData.get(index));
        }
        //将筛选完成的物料填入活动选品表中
        //选品数

        addData(filterItem,selectQty);
    }

    /**
     * 根据国别品类的占比计算 每个品类的数量
     */
    private String calCateQty(int selectQty,Map<String, Integer> result){
        //获取占比
        QFBuilder builder = new QFBuilder();
        builder.add("aos_org","=",orgEntity.getPkValue());
        List<String> selelct = Arrays.asList("aos_home_c", "aos_patio_b", "aos_rattan_e", "aos_sports_a", "aos_pet_d", "aos_baby_f");

        String maxProportCate = "";
        Map<String,BigDecimal> cateProport = new HashMap<>();
        BigDecimal maxProport = BigDecimal.ZERO;
        //计算每个品类的占比
        for (String value : selelct) {
            //占比为空，排除
            if (FndGlobal.IsNull(actPlanEntity.get(value))) {
                continue;
            }
            BigDecimal percentage = actPlanEntity.getBigDecimal(value);
            //占比为0，排除
            if (percentage.compareTo(BigDecimal.ZERO)<=0){
                continue;
            }
            String cateNumber = value.substring(value.length() - 1);
            if (maxProport.compareTo(percentage)<0) {
                maxProport = percentage;
                maxProportCate = cateNumber;
            }
            cateProport.put(cateNumber,percentage);
        }
        List<String> filterCate = new ArrayList<>(cateProport.keySet());
        //已经填入的品类数
        int alreadyPopulated = 0;
        for (String value : filterCate) {
            //最后一个品类，最后一个品类用：总数-已经填入的数量；不是最后一个品类：总数*占比
            boolean lastCate = filterCate.indexOf(value) == filterCate.size() - 1;
            if (lastCate){
                result.put(value,selectQty-alreadyPopulated);
            }
            else{
                int qty = new BigDecimal(selectQty).multiply(cateProport.get(value)).setScale(0,BigDecimal.ROUND_HALF_UP).intValue();
                alreadyPopulated += qty;
                result.put(value,qty);
            }
        }
        //最大品类的备用数
        int maxCateStandQty = selectQty - result.get(maxProportCate);
        result.put("maxCate",maxCateStandQty);

        return maxProportCate;
    }

    /**
     * 向活动选品表中添加数据
     */
    private void addData(List<DynamicObject> filterItem,int selectQty){
        selectQty = Math.min(selectQty, filterItem.size());
        DynamicObjectCollection dyc = actPlanEntity.getDynamicObjectCollection("aos_sal_actplanentity");
        Date startdate = actPlanEntity.getDate("aos_startdate");
        Date enddate = actPlanEntity.getDate("aos_enddate1");
        //查找帖子id
        Object shopid = actPlanEntity.getDynamicObject("aos_shop").getPkValue();
        List<String> list_filterItem = new ArrayList<>(filterItem.size());
        for (DynamicObject info : filterItem) {
            list_filterItem.add(info.getString("id"));
        }
        Map<String, String> map_asin = ActUtil.queryOrgShopItemASIN(orgEntity.getPkValue(),shopid, list_filterItem);

        for (int index = 0; index <selectQty; index++) {
            DynamicObject itemInfo = filterItem.get(index);
            DynamicObject addNewRow = dyc.addNew();
            String itemid = itemInfo.getString("id");
            addNewRow.set("aos_itemnum",itemid);
            addNewRow.set("aos_itemname",itemInfo.getString("name"));
            addNewRow.set("aos_l_startdate",startdate);
            addNewRow.set("aos_enddate",enddate);
            addNewRow.set("aos_postid",map_asin.get(itemid));
            addNewRow.set("aos_is_saleout",itemInfo.get("aos_is_saleout"));
            addNewRow.set("aos_fit","Y");
            if (cateItem.containsKey(itemid)){
                String cateName = cateItem.get(itemid).getString("gname");
                String[] split = cateName.split(",");
                if (split.length>0){
                    addNewRow.set("aos_category_stat1",split[0]);
                }
                if (split.length>1){
                    addNewRow.set("aos_category_stat2",split[1]);
                }
            }
        }
        SaveServiceHelper.save(new DynamicObject[]{actPlanEntity});
        // 赋值库存信息、活动信息
        ActPlanService actPlanService = new ActPlanServiceImpl();
        actPlanEntity = BusinessDataServiceHelper.loadSingle(actPlanEntity.getPkValue(),"aos_act_select_plan");
        actPlanService.updateActInfo(actPlanEntity);
        fndLog.finnalSave();
    }

    /**
     * 判断活动选品表中的数据是否符合规定
     */
    private void addData(List<String> list_filterItem){
        String ruleValue = typEntity.getString("aos_rule_v");
        String[] parameterKey = FormulaEngine.extractVariables(ruleValue);
        Map<String,Object> parameters = new HashMap<>(parameterKey.length);

        DynamicObjectCollection dyc = actPlanEntity.getDynamicObjectCollection("aos_sal_actplanentity");
        Date startdate = actPlanEntity.getDate("aos_startdate");
        Date enddate = actPlanEntity.getDate("aos_enddate1");
        //查找帖子id
        Object shopid = actPlanEntity.getDynamicObject("aos_shop").getPkValue();
        Map<String, String> map_asin = ActUtil.queryOrgShopItemASIN(orgEntity.getPkValue(),shopid, list_filterItem);

        Map<String,DynamicObject> map_itemInfo = new HashMap<>(itemInfoes.size());
        for (DynamicObject infoe : itemInfoes) {
            map_itemInfo.put(infoe.getString("id"),infoe);
        }

        for (DynamicObject row : dyc) {
            if (row.getDynamicObject("aos_itemnum")==null) {
                continue;
            }
            String itemid = row.getDynamicObject("aos_itemnum").getString("id");
            row.set("aos_itemname",row.getDynamicObject("aos_itemnum").getString("name"));
            row.set("aos_postid",map_asin.get(itemid));
            if (row.get("aos_l_startdate") ==null){
                row.set("aos_l_startdate",startdate);
            }
            if (row.get("aos_enddate")==null){
                row.set("aos_enddate",enddate);

            }

            DynamicObject info = map_itemInfo.get(itemid);
            if (info==null){
                row.set("aos_fit","N");
            }
            else {
                row.set("aos_is_saleout",info.getString("aos_is_saleout"));
                //执行公式
                parameters.clear();
                for (String key : parameterKey) {
                    parameters.put(key,rowResults.get(key).get(itemid));
                }
                //执行公式
                boolean result = Boolean.parseBoolean(FormulaEngine.execExcelFormula(ruleValue, parameters).toString());
                if (result) {
                    row.set("aos_fit","Y");
                }
                else {
                    row.set("aos_fit","N");
                }
            }
        }
        fndLog.finnalSave();
    }

    /**
     * 获取国别下的物料
     */
    private void setItemInfo(List<String> itemids){
        //先根据活动库中的品类筛选出合适的物料
        ItemCategoryDao categoryDao = new ItemCategoryDaoImpl();
        QFBuilder builder = new QFBuilder();
        DynamicObjectCollection cateRowEntity = typEntity.getDynamicObjectCollection("aos_entryentity1");

        //获取多次活动的SKU
        Set<String> ruleItem = getRuleItem();

        cateItem = new HashMap<>();
        StringJoiner str = new StringJoiner(",");
        str.add("material");
        str.add("group.name gname");
        str.add("group.number gnumber");
        for (DynamicObject row : cateRowEntity) {
            builder.clear();
            if (row.get("aos_cate")!=null) {
                builder.add("group","=",row.getDynamicObject("aos_cate").getPkValue());
            }
            if (FndGlobal.IsNotNull(row.get("aos_name"))){
                builder.add("material.name","=",row.getString("aos_name"));
            }
            if (itemids!=null && itemids.size()>0){
                builder.add("material",QFilter.in,itemids);
            }
            if (ruleItem!=null){
                builder.add("material",QFilter.not_in,ruleItem);
            }
            DynamicObjectCollection cateResults = categoryDao.queryData(str.toString(), builder);
            for (DynamicObject result : cateResults) {
                cateItem.put(result.getString("material"),result);
            }
        }



        ItemDao itemDao = new ItemDaoImpl();
        StringJoiner selectFields = new StringJoiner(",");
        selectFields.add("id");
        selectFields.add("name");
        selectFields.add("number");
        selectFields.add("aos_contryentry.aos_contryentrystatus aos_contryentrystatus");
        selectFields.add("aos_contryentry.aos_seasonseting aos_seasonseting");
        selectFields.add("aos_contryentry.aos_seasonseting.name season");
        selectFields.add("aos_contryentry.aos_is_saleout aos_is_saleout");
        selectFields.add("aos_contryentry.aos_festivalseting.number festival");
        selectFields.add("aos_contryentry.aos_seasonseting.aos_seasonal_pro.number seasonpro");
        QFilter filter = new QFilter("aos_contryentry.aos_nationality","=",orgEntity.getPkValue());
        filter.and(new QFilter("id",QFilter.in,cateItem.keySet()));
        if (itemids!=null && itemids.size()>0){
          filter.and(new QFilter("id",QFilter.in,itemids));
        }
        itemInfoes = itemDao.listItemObj(selectFields.toString(),filter,null);
    }

    /**
     * 获取去重规则剔除的物料
     */
    private Set<String>  getRuleItem(){
        DynamicObjectCollection ruleEntityRows = typEntity.getDynamicObjectCollection("aos_entryentity3");
        Set<String> result = new HashSet<>();
        LocalDate startdate = actPlanEntity.getDate("aos_startdate").toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate endDate = actPlanEntity.getDate("aos_enddate1").toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate now = LocalDate.now();

        QFBuilder builder = new QFBuilder();
        //规则单据体
        for (DynamicObject row : ruleEntityRows) {
            String project = row.getString("aos_re_project");
            if (FndGlobal.IsNull(project)) {
                continue;
            }
            builder.clear();
            builder.add("aos_nationality","=",orgEntity.getPkValue());
            if (row.get("aos_re_channel")!=null) {
                builder.add("aos_channel","=",row.getDynamicObject("aos_re_channel").getPkValue());
            }
            if (row.get("aos_re_shop")!=null) {
                builder.add("aos_shop","=",row.getDynamicObject("aos_re_shop").getPkValue());
            }
            if (row.get("aos_re_act")!=null){
                builder.add("aos_shop","=",row.getDynamicObject("aos_re_act").getPkValue());
            }
            //同期
            if (project.equals("sameTerm")){
                builder.add("aos_sal_actplanentity.aos_l_startdate","<=",  endDate.plusDays(1).toString());
                builder.add("aos_sal_actplanentity.aos_enddate",">=",startdate.toString());
            }
            //过去几天
            else {
                int day = 0;
                if (row.get("aos_frame")!=null) {
                    day = row.getInt("aos_frame");
                }
                builder.add("aos_sal_actplanentity.aos_enddate",">=",now.minusDays(day).toString());
                builder.add("aos_sal_actplanentity.aos_l_startdate","<",now.toString());
            }
            DynamicObjectCollection dyc = QueryServiceHelper.query("aos_act_select_plan",
                    "aos_sal_actplanentity.aos_itemnum aos_itemnum,aos_sal_actplanentity.aos_itemnum.number number", builder.toArray());
            for (DynamicObject dy : dyc) {
                fndLog.add(dy.getString("number")+"  规则单据活动剔除");
                result.add(dy.getString("aos_itemnum"));
            }
        }
        //本月平台活动超过2次的物料

        //首先查找所有的平台活动
        builder.clear();
        builder.add("aos_org","=",orgEntity.getPkValue());
        builder.add("aos_assessment.number","=","Y");
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_sal_act_type_p", "aos_acttype", builder.toArray());
        List<String> listActTypes = new ArrayList<>(dyc.size());
        for (DynamicObject dy : dyc) {
            listActTypes.add(dy.getString("aos_acttype"));
        }
        //查找物料的活动次数
        builder.clear();
        builder.add("aos_nationality","=",orgEntity.getPkValue());
        List<String> channels = Arrays.asList("AMAZON", "EBAY");
        builder.add("aos_channel.number",QFilter.not_in,channels);
        String shop = orgEntity.getString("number")+"-Wayfair";
        builder.add("aos_shop.number","!=",shop);
        List<String> actType = Arrays.asList("B", "C");
        builder.add("aos_act_type",QFilter.not_in,actType);
        builder.add("aos_sal_actplanentity.aos_itemnum","!=","");

        dyc = QueryServiceHelper.query("aos_act_select_plan",
                "aos_sal_actplanentity.aos_itemnum aos_itemnum,aos_sal_actplanentity.aos_itemnum.number number", builder.toArray());

        Map<String,Integer> itemActNumber = new HashMap<>(dyc.size());
        for (DynamicObject dy : dyc) {
            String item = dy.getString("aos_itemnum");
            int actFreq = itemActNumber.getOrDefault(item, 0)+1;
            if (actFreq>2){
                fndLog.add(dy.getString("number")+"  多次活动剔除");

            }
            else {
                itemActNumber.put(item,actFreq);
            }
        }
        return result;
    }


    /**
     * 解析规则
     */
    private  void parsingRowRule (){
        //规则单据体
        DynamicObjectCollection rulEntity = typEntity.getDynamicObjectCollection("aos_entryentity2");
        StringBuilder rule = new StringBuilder();
        for (DynamicObject row : rulEntity) {
            //先拼凑公式
            //序列号
            String seq = row.getString("seq");
            rule.setLength(0);
            //规则类型
            String project = row.getString("aos_project");
            rule.append(project);
            rule.append(" ");
            //匹配类型
            String condite = row.getString("aos_condite");
            rule.append(condite);
            rule.append(" ");
            //值
            String value = row.getString("aos_rule_value");

            if (condite.equals("in")){
                String[] split = value.split(",");
                rule.append(" ( ");
                for (int i = 0; i < split.length; i++) {
                    rule.append("'");
                    rule.append(split[i]);
                    rule.append("'");
                    if (i<split.length-1)
                        rule.append(",");
                }
                rule.append(" ) ");
            }
            else {
                rule.append(value);
            }
            //全部物料执行这一行的表达式，并且降结果返回
            Map<String, Boolean> formulaResults = cachedData(rule.toString(), project, row);
            rowResults.put("project"+seq,formulaResults);
        }
    }

    /**
     * 解析价格公式
     * @param formal    公式
     * @return  key 公式标识： value：itemid : value
     */
    private Map<String, Map<String, BigDecimal>>  parsingPriceRule(String formal){
        String[] values = FormulaEngine.extractVariables(formal);
        Map<String, Map<String, BigDecimal>> parameters = new HashMap<>();
        for (String value : values) {
            //匹配成功
            boolean match = false;
            switch (value){
                case "currentPrice":
                    match = true;
                    setCurrentPrice();
                    parameters.put(value,itemCurrentPrice);
                    break;
                case "webPrice":
                    match = true;
                    setWebPrice();
                    parameters.put(value,itemWebPrice);
                    break;
                case "vrp":
                    match = true;
                    setVrpPrice();
                    parameters.put(value,itemVrpPrice);
                    break;
                case "discount":
                    match = true;
                    parameters.put(value,setDiscount());
                    break;
            }
            //上面没有匹配成功则说明 需要用到天数
            if (!match){
                String caption = value.substring(0, value.length() - 2);
                String day = value.substring(value.length() - 2);
                //店铺过去最低定价
                if (caption.equals("shopPrice")){
                    setPastPrice(day);
                    parameters.put(value,pastPrices.get(day));
                }
                //店铺过去最低成交价
                else if (caption.equals("shopSale")){
                    setPastSale(day);
                    parameters.put(value,pastSale.get(day));
                }
            }
        }
        return parameters;
    }

    private Map<String,String> rowRuleName;

    /**
     * 判断公式的类型并且执行
     * @param rule      公式
     * @param dataType  公式类型
     * @param row       公式行
     * @return  每个物料对应的执行结果
     */
    private Map<String,Boolean> cachedData (String rule,String dataType,DynamicObject row){
        Map<String,Boolean> result = new HashMap<>(itemInfoes.size());
        //获取下拉列表
        rowRuleName = FieldUtils.getComboMap("aos_sal_act_type_p", "aos_project");
        switch (dataType){
            case "overseasStock":
                //海外库存
                getItemStock(dataType,rule,result);
                break;
            case "saleDays":
                //可售天数
                getSaleDays(dataType,rule,result);
                break;
            case "itemStatus":
                //物料状态
                getItemStatus(dataType,rule,result);
                break;
            case "season":
                //季节属性
                getItemSeason(dataType,rule,result);
                break;
            case "countyAverage":
                //国别日均
                getItemAverage(dataType,rule,result,row);
                break;
            case "activityDayInv":
                //活动库存
                getActInv(dataType,rule,result);
                break;
            case "activityDay":
                //活动日可售天数
                getActSaleDay(dataType,rule,result);
                break;
            case "reviewScore":
                //review 分数
                getRewvieStars(dataType,rule,result);
                break;
            case "reviewNumber":
                //review 个数
                getRewvieNumber(dataType,rule,result);
                break;
            case "price":
                //定价
                getItemPrice(dataType,rule,result);
                break;
            case "activityPrice":
                //活动价格
                getItemActPrice(dataType,rule,result);
                break;
            case "hot":
                //爆品
                getHotIteme(dataType,rule,result);
                break;
            case "historicalSales":
                //店铺历史销量(销售订单)
                getItemShopSale(dataType,rule,result,row);
                break;
            case "unsalType":
                //滞销类型
                getItemUnsale(dataType,rule,result);
                break;
            case "weekUnsold":
                //周滞销
                getItemWeekUnsale(dataType,rule,result);
                break;
            case "inventAge":
                //最大库龄
                getItemMaxAge(dataType,rule,result);
                break;
            case "platform":
                //平台仓库存
                getItemPlatStock(dataType,rule,result);
                break;
            case "ownWarehouse":
                getItemNoPlatStock(dataType,rule,result);
                break;
            case "actProfit":
                getActProfit(dataType,result);
                break;
        }
        return result;
    }

    private Map<Long,Integer> itemSotck;
    /**
     * 海外库存
     */
    private void setItemStock(){
        if (itemSotck!=null) {
           return;
        }
        itemSotck = new HashMap<>();
        ItemStockDao stockDao = new ItemStockDaoImpl();
        //获取国别下的所有物料的海外库存
        itemSotck = stockDao.listItemOverSeaStock(orgEntity.getLong("id"));
    }
    /**
     * 执行关于海外库存的表达式
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    private void getItemStock(String key,String rule,Map<String,Boolean> result){
        //获取海外库存
        setItemStock();
        //执行公式的参数
        Map<String,Object> paramars = new HashMap<>();
        //遍历物料
        for (DynamicObject itemInfoe : itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            //获取该物料的库存
            Object value = itemSotck.getOrDefault(Long.parseLong(itemid),0);
            //添加日志
            fndLog.add(itemInfoe.getString("number")+"  "+rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            //执行公式并且将执行结果添加到返回结果里面
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }

    private Map<String,Integer> itemSaleDay;
    /**
     * 可售天数
     */
    private void setSaleDays(){
        if (itemSaleDay !=null) {
            return;
        }
        //缓存海外库存
        setItemStock();
        //缓存7日日均销量
        setItemAverageByDay(7);
        Map<String, BigDecimal> itemAverageSale = itemAverage.get("7");
        Date startdate = new Date();
        AvailableDaysService service = new AvailableDaysServiceImpl();
        itemSaleDay = new HashMap<>(itemInfoes.size());
        for (DynamicObject itemInfoe : itemInfoes) {
            String itemID = itemInfoe.getString("id");
            int stock = itemSotck.getOrDefault(Long.parseLong(itemID), 0);
            BigDecimal sale = itemAverageSale.getOrDefault(itemID, BigDecimal.ZERO);
            int day = service.calAvailableDays(orgEntity.getLong("id"), Long.parseLong(itemID), stock, sale, startdate);
            itemSaleDay.put(itemID,day);
        }
    }
    /**
     * 执行关于可售天数的表达式
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    private void getSaleDays(String key,String rule,Map<String,Boolean> result){
        setSaleDays();
        Map<String,Object> paramars = new HashMap<>();
        for (DynamicObject itemInfoe : itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            Object value = itemSaleDay.getOrDefault(itemid,0);
            fndLog.add(itemInfoe.getString("number")+"  "+rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }

    private Map<String,String> itemStatus;
    /**
     * 物料状态
     */
    private void setItemStatus(){
        if (itemStatus!=null) {
            return;
        }
        itemStatus = new HashMap<>(itemInfoes.size());
        //获取下拉列表
        Map<String, String> countryStatus = FieldUtils.getComboMap("bd_material", "aos_contryentrystatus");

        for (DynamicObject infoe : itemInfoes) {
            String itemid = infoe.getString("id");
            String status = infoe.getString("aos_contryentrystatus");
            itemStatus.put(itemid,countryStatus.get(status));
        }
    }
    /**
     * 执行关于物料状态的表达式
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    private void getItemStatus(String key,String rule,Map<String,Boolean> result){
        setItemStatus();
        Map<String,Object> paramars = new HashMap<>();
        for (DynamicObject itemInfoe : itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            Object value = itemStatus.getOrDefault(itemid,"0");
            fndLog.add(itemInfoe.getString("number")+"  "+rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }

    private Map<String,String> itemSeason;
    /**
     * 季节属性
     */
    private void setItemSeason(){
        if (itemSeason!=null) {
            return;
        }
        itemSeason = new HashMap<>(itemInfoes.size());
        for (DynamicObject result :itemInfoes) {
            itemSeason.put(result.getString("id"),result.getString("season"));
        }
    }
    /**
     * 执行关于物料季节属性
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    private void getItemSeason(String key,String rule,Map<String,Boolean> result){
        setItemSeason();
        Map<String,Object> paramars = new HashMap<>();
        for (DynamicObject itemInfoe : itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            Object value = itemSeason.getOrDefault(itemid,"0");
            fndLog.add(itemInfoe.getString("number")+"  "+rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }

    /**
     * 执行国别日均的表达式
     * @param key       参数名
     * @param rule      公式
     * @param result    结果
     * @param row       行
     */
    private void getItemAverage(String key,String rule,Map<String,Boolean> result,DynamicObject row){
        int days = 7;
        if (FndGlobal.IsNotNull(row.get("aos_rule_day")))
            days = row.getInt("aos_rule_day");
        setItemAverageByDay(days);
        Map<String,Object> paramars = new HashMap<>();
        //对应天数的国别日均
        Map<String, BigDecimal> itemAverageFoDay = itemAverage.get(String.valueOf(days));

        for (DynamicObject itemInfoe : itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            Object value = itemAverageFoDay.getOrDefault(itemid,BigDecimal.ZERO);
            fndLog.add(itemInfoe.getString("number")+"  "+rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }

    private Map<String,Map<String, BigDecimal>> itemAverage;
    /**
     * 获取国别日均
     * @param day 日期
     */
    private void setItemAverageByDay(int day){
        if (itemAverage == null){
            itemAverage = new HashMap<>();
        }
        if (!itemAverage.containsKey(String.valueOf(day))){
            String number = orgEntity.getString("number");
            Calendar instance = Calendar.getInstance();
            //美加前推一天
            if (number.equals("US") || number.equals("CA")){
                instance.add(Calendar.DATE,-1);
            }
            //设置开始时间和结束时间
            Date endDate = instance.getTime();
            instance.add(Calendar.DATE,-day);
            Date startDate = instance.getTime();
            OrderLineDao orderLineDao = new OrderLineDaoImpl();
            //获取销量
            Map<Long, Integer> saleInfo = orderLineDao.listItemSalesByOrgId(orgEntity.getLong("id"), startDate, endDate);
            Map<String,BigDecimal> itemSale = new HashMap<>(saleInfo.size());
            if (day!=0){
                BigDecimal bd_day = new BigDecimal(day);
                //计算日均
                for (Map.Entry<Long, Integer> entry : saleInfo.entrySet()) {
                    BigDecimal value = new BigDecimal(entry.getValue()).divide(bd_day,4,BigDecimal.ROUND_HALF_UP);
                    itemSale.put(entry.getKey().toString(),value);
                }
            }
            else {
                for (Map.Entry<Long, Integer> entry : saleInfo.entrySet()) {
                    itemSale.put(entry.getKey().toString(),new BigDecimal(entry.getValue()));
                }
            }
            itemAverage.put(String.valueOf(day),itemSale);
        }
    }

    private Map<String,Integer> itemActInv;
    /**
     * 国别库存
     */
    private void setActInv(){
        if (itemActInv!=null)
            return;
        //先获取海外库存
        setItemStock();
        //取活动前的入库数量
        Date actStartdate = actPlanEntity.getDate("aos_startdate");
        if (actStartdate == null){
            actStartdate = new Date();
        }
        WarehouseStockDao warehouseStockDao = new WarehouseStockDaoImpl();
        Map<String, Integer> itemRevenue = warehouseStockDao.getItemRevenue(orgEntity.getPkValue(), actStartdate);
        //设置国别7天日均
        setItemAverageByDay(7);
        Map<String, BigDecimal> averageSales = itemAverage.get("7");

        //region 记录当前时间到活动时间这段期间的销量 ：（活动日-当前日期）*国别7天日均 * 季节系数，日均计算时加季节系数
        Map<String,BigDecimal> scheItemSale = new HashMap<>(itemInfoes.size());
        //判断现在离活动开始差的月数
        LocalDate actStartDate = actStartdate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate nowDate = LocalDate.now();
        int months = (actStartDate.getYear() - nowDate.getYear())*12 + (actStartDate.getMonthValue() - nowDate.getMonthValue());
        //记录开始时间段和结束时间段
        LocalDate startDate = nowDate,endDate = nowDate;
        ItemCacheService cacheService = new ItemCacheServiceImpl();
        //月份
        int monthValue;
        //这段期间的天数差
        BigDecimal saleDay;
        for (int monthIndex = 0; monthIndex < months+1; monthIndex++) {
            //最后一个之前的时间如 总时间是 7.20-9.21，这段期间为 7.20-8.1 ; 8.1-9.1
            if (monthIndex<months){
                //结束时间
                endDate = endDate.plusMonths(1).withDayOfMonth(1);
                monthValue = startDate.getMonthValue();
                saleDay = new BigDecimal(endDate.toEpochDay() - startDate.toEpochDay());
            }
            //最后一个月：9.1 - 9.21
            else {
                endDate = actStartDate;
                monthValue = startDate.getMonthValue();
                saleDay = new BigDecimal(endDate.toEpochDay() - startDate.toEpochDay());
            }
            //计算这段时间的销量：（活动日-当前日期）*国别7天日均*月份系数
            for (DynamicObject itemInfoe : itemInfoes) {
                String itemId = itemInfoe.getString("id");
                BigDecimal saleValue = scheItemSale.getOrDefault(itemId, BigDecimal.ZERO);

                //获取日均销量
                BigDecimal itemAveSale = averageSales.getOrDefault(itemId, BigDecimal.ZERO);
                //获取系数
                BigDecimal coefficient = cacheService.getCoefficient(orgEntity.getLong("id"), Long.parseLong(itemId), monthValue);
                itemAveSale = itemAveSale.multiply(saleDay).multiply(coefficient);

                saleValue = saleValue.add(itemAveSale);
                scheItemSale.put(itemId,saleValue);
            }
            startDate = endDate;
        }
        //endregion

        itemActInv = new HashMap<>(itemInfoes.size());
        //计算活动日库存 （当前库存+在途）-活动前的销量
        for (DynamicObject itemInfoe : itemInfoes) {
            String itemid = itemInfoe.getString("id");
            //海外库存
            int stock = itemSotck.getOrDefault(Long.parseLong(itemid), 0);
            //在库
            int revenue = itemRevenue.getOrDefault(itemid, 0);
            //活动前的这段时间的销量
            BigDecimal sale = scheItemSale.getOrDefault(itemid, BigDecimal.ZERO);
            int value = BigDecimal.valueOf(stock + revenue).subtract(sale).setScale(0, BigDecimal.ROUND_HALF_UP).intValue();
            itemActInv.put(itemid,value);
        }

    }
    /**
     * 执行关于国别库存
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    private void getActInv(String key,String rule,Map<String,Boolean> result){
        setActInv();
        Map<String,Object> paramars = new HashMap<>();
        for (DynamicObject itemInfoe : itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            Object value = itemActInv.getOrDefault(itemid,0);
            fndLog.add(itemInfoe.getString("number")+"  "+rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }

    private Map<String,Integer> itemActSalDay;
    /**
     * 活动可售天数
     */
    private void setActSaleDay(){
        if (itemActSalDay !=null)
            return;
        itemActSalDay = new HashMap<>(itemInfoes.size());
        //设置活动库存
        setActInv();
        //设置7天日均销量
        setItemAverageByDay(7);
        Map<String, BigDecimal> itemAverageSale = itemAverage.get("7");
        itemSaleDay = new HashMap<>();
        Date startdate = actPlanEntity.getDate("aos_startdate");
        AvailableDaysService service = new AvailableDaysServiceImpl();
        for (DynamicObject itemInfoe : itemInfoes) {
            String itemID = itemInfoe.getString("id");
            int stock = itemActInv.getOrDefault(itemID, 0);
            BigDecimal sale = itemAverageSale.getOrDefault(itemID, BigDecimal.ZERO);
            int day = service.calAvailableDays(orgEntity.getLong("id"), Long.parseLong(itemID), stock, sale, startdate);
            itemActSalDay.put(itemID,day);
        }
    }
    /**
     * 执行关于活动可售天数
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    private void getActSaleDay(String key,String rule,Map<String,Boolean> result){
        setActSaleDay();
        Map<String,Object> paramars = new HashMap<>();
        for (DynamicObject itemInfoe : itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            Object value = itemActSalDay.getOrDefault(itemid,0);
            fndLog.add(itemInfoe.getString("number")+"  "+rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }

    private Map<String,BigDecimal> reviewStars;
    private Map<String,Integer> reviewQty;
    private void setReview(){
        if (reviewStars!=null)
            return;
        reviewStars = new HashMap<>();
        reviewQty = new HashMap<>();
        QFBuilder builder = new QFBuilder();
        builder.add("aos_orgid","=",orgEntity.getPkValue());
        builder.add("aos_itemid","!=","");

        String select = "aos_itemid,aos_stars,aos_review";
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_sync_review", select, builder.toArray(), "modifytime asc");
        for (DynamicObject dy : dyc) {
            String itemid = dy.getString("aos_itemid");
            BigDecimal value = BigDecimal.ZERO;
            if (FndGlobal.IsNotNull(dy.get("aos_stars"))) {
                value = dy.getBigDecimal("aos_stars");
            }
            reviewStars.put(itemid,value);
            int qty = 0;
            if (FndGlobal.IsNotNull("aos_review")) {
                qty = dy.getInt("aos_review");
            }
            reviewQty.put(itemid,qty);
        }
    }
    /**
     * 执行关于Review 分数
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    private void getRewvieStars(String key,String rule,Map<String,Boolean> result){
       setReview();
        Map<String,Object> paramars = new HashMap<>();
        for (DynamicObject itemInfoe : itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            Object value = reviewStars.getOrDefault(itemid,BigDecimal.ZERO);
            fndLog.add(itemInfoe.getString("number")+"  "+rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }

    /**
     * 执行关于Review 个数
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    private void getRewvieNumber(String key,String rule,Map<String,Boolean> result){
        setReview();
        Map<String,Object> paramars = new HashMap<>();
        for (DynamicObject itemInfoe : itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            Object value = reviewQty.getOrDefault(itemid,0);
            fndLog.add(itemInfoe.getString("number")+"  "+rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }

    private Map<String,BigDecimal> itemPrice;
    /**
     * 定价
     */
    private void setItemPrice(){
        if (itemPrice!=null)
            return;
        ItemPriceDao itemPriceDao = new ItemPriceDaoImpl();
        Object shopid = actPlanEntity.getDynamicObject("aos_shop").getPkValue();
        itemPrice = itemPriceDao.queryShopItemPrice(orgEntity.getPkValue(),shopid);
    }
    /**
     * 执行关于定价 公式
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    private void getItemPrice(String key,String rule,Map<String,Boolean> result){
        setItemPrice();
        Map<String,Object> paramars = new HashMap<>();
        for (DynamicObject itemInfoe : itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            Object value = itemPrice.getOrDefault(itemid,BigDecimal.ZERO);
            fndLog.add(itemInfoe.getString("number")+"  "+rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }

    private Map<String,BigDecimal> itemActPrice;
    /**
     * 计算活动价格
     */
    private void setItemActPrice(){
        if (itemActPrice!=null) {
            return;
        }

        itemActPrice = new HashMap<>(itemInfoes.size());
        //获取活动价公式
        String priceformula = typEntity.getString("aos_priceformula_v");
        if (FndGlobal.IsNull(priceformula)) {
            return;
        }
        //获取公式中参数的值
        Map<String, Map<String, BigDecimal>> parameters = parsingPriceRule(priceformula);

        //设置定价习俗
        BigDecimal custom;
        if (FndGlobal.IsNotNull(typEntity.getString("aos_custom"))) {
            custom = new BigDecimal("0."+typEntity.getString("aos_custom"));
        }
        else {
            custom = new BigDecimal("0.99");
        }

        Map<String,Object> calParameters = new HashMap<>();
        for (DynamicObject itemInfoe : itemInfoes) {
            calParameters.clear();
            String itemid = itemInfoe.getString("id");
            //获取物料对应的公式中每个参数对应的值
            for (Map.Entry<String, Map<String, BigDecimal>> entry : parameters.entrySet()) {
                calParameters.put(entry.getKey(),entry.getValue().getOrDefault(itemid,BigDecimal.ZERO));
            }
            BigDecimal price = new BigDecimal(FormulaEngine.execExcelFormula(priceformula, calParameters).toString());
            price = price.setScale(0,BigDecimal.ROUND_DOWN);
            price = price.subtract(BigDecimal.ONE).add(custom);
            itemActPrice.put(itemid,price);
        }
    }
    /**
     * 执行关于活动价格 公式
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    private void getItemActPrice(String key,String rule,Map<String,Boolean> result){
        setItemActPrice();
        Map<String,Object> paramars = new HashMap<>();
        for (DynamicObject itemInfoe : itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            Object value = itemActPrice.getOrDefault(itemid,BigDecimal.ZERO);
            fndLog.add(itemInfoe.getString("number")+"  "+rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }

    /**
     * 执行关于爆品 公式
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    private void getHotIteme(String key,String rule,Map<String,Boolean> result){
        Map<String,Object> paramars = new HashMap<>();
        for (DynamicObject itemInfoe : itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            Object value ;

            if (itemInfoe.getBoolean("aos_is_saleout")){
               value = "是";
            }
            else {
                value = "否";
            }

            fndLog.add(itemInfoe.getString("number")+"  "+rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }

    private Map<String,Map<Long,Integer>> itemShopSale;

    /**
     * 计算店铺销量
     * @param day 天数
     */
    private void setItemShopSale(int day){
        if (itemShopSale==null) {
            itemShopSale = new HashMap<>();
        }
        //判断传入天数天数是否为空
        if (FndGlobal.IsNull(day))
            return;
        //判断这种天数的销量是否已经计算过
        if (itemShopSale.containsKey(String.valueOf(day)))
            return;
        //设置查找销量的开始时间和结束时间
        long orgID = orgEntity.getLong("id");
        long shopId = actPlanEntity.getDynamicObject("aos_shop").getLong("id");
        Calendar instance = Calendar.getInstance();
        Date end = instance.getTime();
        instance.add(Calendar.DATE,-day);
        Date start = instance.getTime();
        //计算销量
        OrderLineDao orderLineDao = new OrderLineDaoImpl();
        Map<Long, Integer> result = orderLineDao.listItemSales(orgID, null, shopId, start, end);
        itemShopSale.put(String.valueOf(day),result);
    }
    /**
     * 执行关于店铺历史销量 公式
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    private void getItemShopSale(String key,String rule,Map<String,Boolean> result,DynamicObject row){
        int day = 7;
        if (FndGlobal.IsNotNull(row.get("aos_rule_day")))
            day = row.getInt("aos_rule_day");
        setItemShopSale(day);
        Map<Long, Integer> shopSale = itemShopSale.get(String.valueOf(day));
        Map<String,Object> paramars = new HashMap<>();
        for (DynamicObject itemInfoe : itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            Object value = shopSale.getOrDefault(Long.parseLong(itemid),0);
            fndLog.add(itemInfoe.getString("number")+"  "+rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }

    private Map<String,String> itemUnsaleTyep;
    /**
     * 查找物料滞销类型
     */
    private void setItemUnsale(){
        if (itemUnsaleTyep!=null) {
            return;
        }
        //获取下拉列表
        Map<String, String> typeValue = FieldUtils.getComboMap("aos_base_stitem", "aos_type");
        QFBuilder builder = new QFBuilder();
        builder.add("aos_orgid","=",orgEntity.getPkValue());
        builder.add("aos_itemid","!=","");
        builder.add("aos_type","!=","");
        DynamicObjectCollection results = QueryServiceHelper.query("aos_base_stitem", "aos_itemid,aos_type", builder.toArray());
        itemUnsaleTyep = new HashMap<>(results.size());
        for (DynamicObject result : results) {
            itemUnsaleTyep.put(result.getString("aos_itemid"),typeValue.get(result.getString("aos_type")));
        }
    }
    /**
     * 执行关于物料滞销 公式
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    private void getItemUnsale(String key,String rule,Map<String,Boolean> result){
        setItemUnsale();
        Map<String,Object> paramars = new HashMap<>();
        for (DynamicObject itemInfoe : itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            Object value = itemUnsaleTyep.getOrDefault(itemid,"0");
            fndLog.add(itemInfoe.getString("number")+"  "+rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }

    private Map<String,String> itemWeekUnsale;
    /**
     * 获取物料周滞销类型数据
     */
    private void setItemWeekUnsale(){
        if (itemWeekUnsale!=null) {
            return;
        }
        itemWeekUnsale = new HashMap<>(itemInfoes.size());
        //查找最新一期的海外滞销报价日期
        QFBuilder builder = new QFBuilder();
        builder.add("aos_org","=",orgEntity.getPkValue());
        DynamicObject dy = QueryServiceHelper.queryOne("aos_sal_quo_ur", "max(aos_sche_date) aos_sche_date", builder.toArray());
        LocalDate date = dy.getDate("aos_sche_date").toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        builder.add("aos_sche_date",">=",date.toString());
        builder.add("aos_sche_date","<",date.plusDays(1));
        builder.add("aos_entryentity.aos_sku","!=","");
        builder.add("aos_entryentity.aos_unsalable_type","!=","");

        StringJoiner str = new StringJoiner(",");
        str.add("aos_entryentity.aos_sku aos_sku");
        str.add("aos_entryentity.aos_unsalable_type aos_unsalable_type");
        DynamicObjectCollection results = QueryServiceHelper.query("aos_sal_quo_ur", str.toString(), builder.toArray());
        for (DynamicObject row : results) {
            itemWeekUnsale.put(row.getString("aos_sku"),row.getString("aos_unsalable_type"));
        }
    }
    /**
     * 执行关于周滞销 公式
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    private void getItemWeekUnsale(String key,String rule,Map<String,Boolean> result){
        setItemWeekUnsale();
        Map<String,Object> paramars = new HashMap<>();
        for (DynamicObject itemInfoe : itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            Object value = itemWeekUnsale.getOrDefault(itemid,"0");
            fndLog.add(itemInfoe.getString("number")+"  "+rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }

    private Map<Long,Integer> itemMaxAge;
    /**
     * 最大库龄
     */
    private void setItemMaxAge(){
        if (itemMaxAge!=null)
            return;
        ItemAgeDao ageDao = new ItemAgeDaoImpl();
        itemMaxAge = ageDao.listItemMaxAgeByOrgId(orgEntity.getLong("id"));
    }
    /**
     * 执行关于最大库龄 公式
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    private void getItemMaxAge(String key,String rule,Map<String,Boolean> result){
        setItemMaxAge();
        Map<String,Object> paramars = new HashMap<>();
        for (DynamicObject itemInfoe : itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            Object value = itemMaxAge.getOrDefault(Long.parseLong(itemid),0);
            fndLog.add(itemInfoe.getString("number")+"  "+rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }

    private Map<Long,Integer> itemPlatStock;
    /**
     * 平台仓库存
     */
    private void setItemPlatStock(){
        if (itemPlatStock!=null)
            return;
        itemPlatStock = new HashMap<>(itemInfoes.size());
        setItemStock();
        setItemNoPlatStock();
        for (DynamicObject itemInfoe : itemInfoes) {
            Long itemID = itemInfoe.getLong("id");
            Integer stock = itemSotck.getOrDefault(itemID, 0);
            Integer noPlatStock = itemNoPlatStock.getOrDefault(itemID, 0);
            itemPlatStock.put(itemID,(stock-noPlatStock));
        }
    }
    /**
     * 执行关于平台仓库 公式
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    private void getItemPlatStock(String key,String rule,Map<String,Boolean> result){
        setItemPlatStock();
        Map<String,Object> paramars = new HashMap<>();
        for (DynamicObject itemInfoe : itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            Object value = itemPlatStock.getOrDefault(Long.parseLong(itemid),0);
            fndLog.add(itemInfoe.getString("number")+"  "+rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }

    private Map<Long,Integer> itemNoPlatStock;
    /**
     * 非平台仓库存
     */
    private void setItemNoPlatStock(){
        if (itemNoPlatStock!=null)
            return;
        ItemStockDao itemStockDao = new ItemStockDaoImpl();
        itemNoPlatStock = itemStockDao.listItemNoPlatformStock(orgEntity.getLong("id"));
    }
    /**
     * 执行关于非平台仓库 公式
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    private void getItemNoPlatStock(String key,String rule,Map<String,Boolean> result){
        setItemNoPlatStock();
        Map<String,Object> paramars = new HashMap<>();
        for (DynamicObject itemInfoe : itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            Object value = itemNoPlatStock.getOrDefault(Long.parseLong(itemid),0);
            fndLog.add(itemInfoe.getString("number")+"  "+rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }

    /**
     * 执行活动毛利率的公式
     */
    private void getActProfit(String key,Map<String,Boolean> result){
        //首先计算活动毛利率，先获取活动毛利率计算公式
        setItemActProfit();
        //可售天数
        setSaleDays();
        //7天日均
        setItemAverageByDay(7);
        Map<String, BigDecimal> itemSaleAve = this.itemAverage.get(String.valueOf(7));
        //毛利标准
        setProfitStand();
        //物料滞销类型
        setItemUnsale();
        //设置季节品的阶段
        setSeasonStage();
        //判断是否低动销
        for (DynamicObject itemInfoe : itemInfoes) {
            StringJoiner str = new StringJoiner(" , ");
            str.add(key);
            //物料id
            String itemId = itemInfoe.getString("id");
            str.add(itemInfoe.getString("number"));
            //获取物料毛利
            BigDecimal itemProfit = BigDecimal.ZERO;
            if (itemActProfit.containsKey(itemId)){
                itemProfit = itemActProfit.get(itemId).getOrDefault("value",BigDecimal.ZERO);
            }
            str.add("毛利率：  "+itemProfit);
            //可售天数
            int saleDay = itemSaleDay.getOrDefault(itemId, 0);
            str.add("可售天数： "+saleDay);
            //7天日均销量
            float saleAve = itemSaleAve.getOrDefault(itemId, BigDecimal.ZERO).floatValue();
            str.add("日均销量： "+saleAve);
            String type = MKTCom.Get_RegularUn(orgEntity.getString("number"), saleDay, saleAve);
            fndLog.add(str.toString());
            if (type.equals("低动销")){
                BigDecimal stand = profitStand.getOrDefault("A",BigDecimal.ZERO);
                if (itemProfit.compareTo(stand)>=0){
                    result.put(itemId,true);
                }
            }
            else if (type.equals("低周转")){
                BigDecimal stand;
                if (saleAve<=3){
                    stand = profitStand.getOrDefault("B",BigDecimal.ZERO);
                }else {
                    stand = profitStand.getOrDefault("C",BigDecimal.ZERO);
                }
                if (itemProfit.compareTo(stand)>=0){
                    result.put(itemId,true);
                }
            }

        }
        //判断是否常规品滞销类型
        for (DynamicObject itemInfoe : itemInfoes) {
            String itemId = itemInfoe.getString("id");
            if (result.containsKey(itemId)) {
                continue;
            }
            if (itemUnsaleTyep.containsKey(itemId)){
                String unsaleType = itemUnsaleTyep.get(itemId);
                //货多销少;货少销少
                if (unsaleType.equals("货多销少") || unsaleType.equals("货少销少")) {
                    if (itemActProfit.containsKey(itemId)){
                        Map<String, BigDecimal> profit = itemActProfit.get(itemId);
                        BigDecimal fee = profit.getOrDefault("aos_lowest_fee", BigDecimal.ZERO);
                        BigDecimal cost = profit.getOrDefault("aos_item_cost", BigDecimal.ZERO);
                        BigDecimal price = itemPrice.getOrDefault(itemId, BigDecimal.ZERO);
                        price = fee
                                .add(price.multiply(BigDecimal.valueOf(0.15)))
                                .add(cost.multiply(BigDecimal.valueOf(0.3)));
                        BigDecimal actPrice = itemActPrice.getOrDefault(itemId, BigDecimal.ZERO);
                        if (actPrice.compareTo(price)>=0){
                            result.put(itemId,true);
                        }
                    }
                }
                //新品销少
                else if (unsaleType.equals("新品销少")){
                    //获取物料毛利
                    BigDecimal itemProfit = BigDecimal.ZERO;
                    if (itemActProfit.containsKey(itemId)){
                        itemProfit = itemActProfit.get(itemId).getOrDefault("value",BigDecimal.ZERO);
                    }
                    BigDecimal stand = profitStand.get("D");
                    if (itemProfit.compareTo(stand)>=0){
                        result.put(itemId,true);
                    }
                }

            }
        }
        //季节品/节日品
        for (DynamicObject itemInfoe : itemInfoes) {
            String itemId = itemInfoe.getString("id");
            if (result.containsKey(itemId)) {
                continue;
            }

            String type = null;
            //判断是否是节日品
            if (FndGlobal.IsNotNull(itemInfoe.get("festival"))){
                if (seasonStage.containsKey(itemInfoe.getString("festival"))){
                    type = seasonStage.get(itemInfoe.getString("festival"));
                }
            }
            //判断是否是季节品
            if (FndGlobal.IsNull(type) && FndGlobal.IsNotNull(itemInfoe.get("seasonpro"))){
                if (seasonStage.containsKey(itemInfoe.getString("seasonpro"))){
                    type = seasonStage.get(itemInfoe.getString("seasonpro"));
                }
            }

            if (FndGlobal.IsNotNull(type)){
                BigDecimal itemProfit = BigDecimal.ZERO;
                if (itemActProfit.containsKey(itemId)){
                    itemProfit = itemActProfit.get(itemId).getOrDefault("value",BigDecimal.ZERO);
                }
                BigDecimal stand = profitStand.getOrDefault(type,BigDecimal.ZERO);
                if (itemProfit.compareTo(stand)>=0){
                    result.put(itemId,true);
                }
            }
        }

        //爆品
        //爆品毛利标准
        BigDecimal saleOutStand = profitStand.getOrDefault("H", BigDecimal.ZERO);
        for (DynamicObject itemInfoe : itemInfoes) {
            String itemId = itemInfoe.getString("id");
            if (result.containsKey(itemId)) {
                continue;
            }
            //爆品
            if (itemInfoe.getBoolean("aos_is_saleout")){
                BigDecimal itemProfit = BigDecimal.ZERO;
                if (itemActProfit.containsKey(itemId)){
                    itemProfit = itemActProfit.get(itemId).getOrDefault("value",BigDecimal.ZERO);
                }
                if (itemProfit.compareTo(saleOutStand)>=0){
                    result.put(itemId,true);
                }
                else
                    result.put(itemId,false);
            }
            else {
                result.put(itemId,false);
            }
        }
    }
    Map<String,BigDecimal> profitStand;
    /**
     * 获取毛利率标准
     */
    private void setProfitStand(){
        if (profitStand!=null) {
            return;
        }
        profitStand = new HashMap<>();
        String select = "aos_type2,aos_pro_"+(orgEntity.getString("number").toLowerCase())+" aos_pro";
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_act_pro", select, null);
        for (DynamicObject dy : dyc) {
            profitStand.put(dy.getString("aos_type2"),dy.getBigDecimal("aos_pro"));
        }
    }

    Map<String,String> seasonStage;
    /**
     * 设置季节品和节日品的 季初、季中，季末
     */
    private void setSeasonStage(){
        if (seasonStage!=null)
            return;
        seasonStage = new HashMap<>();
        QFBuilder builder = new QFBuilder();
        builder.add("aos_orgid","=",orgEntity.getPkValue());
        //获取数据
        Date now = new Date();
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_base_date", "aos_type,aos_datefrom,aos_dateto", builder.toArray());
        for (DynamicObject row : dyc) {
            String type = row.getString("aos_type");
            //阶段开始时间
            Date startDate = row.getDate("aos_datefrom");
            //阶段结束时间
            Date endDate = row.getDate("aos_dateto");
            //如果当前日期出于这个阶段中，那么毛利率标准取这个阶段的数据
            if (startDate.before(now) && now.before(endDate)){
                //圣诞节 季初
                switch (type) {
                    case "CH-1":
                        seasonStage.put("CHRISTMAS", "E");
                        break;
                    //圣诞 季中
                    case "CH-2":
                        seasonStage.put("CHRISTMAS", "F");
                        break;
                    //圣诞 季末
                    case "CH-3":
                        seasonStage.put("CHRISTMAS", "G");
                        break;
                    //万圣节 季初
                    case "HA-E-1":
                    case "HA-U-1":
                        seasonStage.put("HALLOWEEN", "E");
                        break;
                    //万圣节 季中
                    case "HA-E-2":
                    case "HA-U-2":
                        seasonStage.put("HALLOWEEN", "F");
                        break;
                    //万圣节 季末
                    case "HA-E-3":
                    case "HA-U-3":
                        seasonStage.put("HALLOWEEN", "G");
                        break;
                    //春夏品 季初
                    case "SS-1":
                        seasonStage.put("SPRING_SUMMER_PRO", "E");
                        break;
                    //春夏品 季中
                    case "SS-2":
                        seasonStage.put("SPRING_SUMMER_PRO", "F");
                        break;
                    //春夏品 季中
                    case "SS-3":
                        seasonStage.put("SPRING_SUMMER_PRO", "G");
                        break;
                    //秋冬品 季初
                    case "AW-1":
                        seasonStage.put("AUTUMN_WINTER_PRO", "E");
                        break;
                    //秋冬品 季中
                    case "AW-2":
                        seasonStage.put("AUTUMN_WINTER_PRO", "F");
                        break;
                    //秋冬品 季末
                    case "AW-3":
                        seasonStage.put("AUTUMN_WINTER_PRO", "G");
                        break;
                }
            }
        }
    }

    /**
     * 计算毛利率
     */
    Map<String, Map<String, BigDecimal>> itemActProfit;
    private void setItemActProfit() {
        if (itemActProfit == null)
            return;
        //获取活动价格
        setItemActPrice();
        //获取原价
        setItemPrice();
        Map<String,BigDecimal[]> map_itemPrice = new HashMap<>(itemInfoes.size());
        for (DynamicObject itemInfoe : itemInfoes) {
            String itemID = itemInfoe.getString("id");
            BigDecimal [] price = new BigDecimal[2];
            price[0] = itemPrice.getOrDefault(itemID,BigDecimal.ZERO);
            price[1] = itemActPrice.getOrDefault(itemID,BigDecimal.ZERO);
            map_itemPrice.put(itemID,price);
        }
        String shopId = actPlanEntity.getDynamicObject("aos_shop").getString("id");
        try {
            itemActProfit = aos_sal_act_from.get_formula(orgEntity.getString("id"), shopId, map_itemPrice, "/");
        }catch (Exception e){
            e.printStackTrace();
            throw new KDException(new ErrorCode("计算毛利率异常",e.getMessage()));
        }

    }

    private Map<String,BigDecimal> itemCurrentPrice;
    /**
     * 计算店铺当前价格
     */
    private void setCurrentPrice(){
        if (itemCurrentPrice!=null) {
            return;
        }
        itemCurrentPrice = new HashMap<>(itemInfoes.size());
        QFBuilder builder = new QFBuilder();
        builder.add("aos_org","=",orgEntity.getPkValue());
        builder.add("aos_type","=","currentPrice");
        builder.add("aos_shop","=",actPlanEntity.getDynamicObject("aos_shop").getPkValue());
        builder.add("aos_item","!=","");
        builder.add("aos_price",">",0);
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_shop_price", "aos_item,aos_price", builder.toArray());
        for (DynamicObject dy : dyc) {
            itemCurrentPrice.put(dy.getString("aos_item"),dy.getBigDecimal("aos_price"));
        }
        //缺失价格的物料
        List<String> missingPrice = new ArrayList<>(itemInfoes.size());
        for (DynamicObject itemInfoe : itemInfoes) {
            if (!itemCurrentPrice.containsKey(itemInfoe.getString("id"))){
                missingPrice.add(itemInfoe.getString("id"));
            }
        }
        //查找每日价格中的数据
        ItemPriceDao priceDao = new ItemPriceDaoImpl();
        Map<String, BigDecimal> results = priceDao.queryShopItemPrice(orgEntity.getPkValue(), actPlanEntity.getDynamicObject("aos_shop").getPkValue());
        for (Map.Entry<String, BigDecimal> entry : results.entrySet()) {
            if (missingPrice.contains(entry.getKey()))
                itemCurrentPrice.put(entry.getKey(),entry.getValue());
        }
    }

    private Map<String,BigDecimal> itemWebPrice;
    /**
     * 计算官网当前价格
     */
    private void setWebPrice(){
        if (itemWebPrice!=null) {
            return;
        }
        itemWebPrice = new HashMap<>(itemInfoes.size());
        QFBuilder builder = new QFBuilder();
        builder.add("aos_org","=",orgEntity.getPkValue());
        builder.add("aos_type","=","webPrice");
        builder.add("aos_shop","=",actPlanEntity.getDynamicObject("aos_shop").getPkValue());
        builder.add("aos_item","!=","");
        builder.add("aos_price",">",0);
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_shop_price", "aos_item,aos_price", builder.toArray());
        for (DynamicObject dy : dyc) {
            itemWebPrice.put(dy.getString("aos_item"),dy.getBigDecimal("aos_price"));
        }
        //缺失价格的物料
        List<String> missingPrice = new ArrayList<>(itemInfoes.size());
        for (DynamicObject itemInfoe : itemInfoes) {
            if (!itemWebPrice.containsKey(itemInfoe.getString("id"))){
                missingPrice.add(itemInfoe.getString("id"));
            }
        }
        //查找每日价格中的数据
        ItemPriceDao priceDao = new ItemPriceDaoImpl();
        Map<String, BigDecimal> results = priceDao.queryWebItemPrice(orgEntity.getPkValue());
        for (Map.Entry<String, BigDecimal> entry : results.entrySet()) {
            if (missingPrice.contains(entry.getKey())) {
                itemWebPrice.put(entry.getKey(),entry.getValue());
            }
        }
    }

    private Map<String,BigDecimal> itemVrpPrice;
    /**
     * 计算Vrp价格
     */
    private void setVrpPrice(){
        if (itemVrpPrice!=null) {
            return;
        }
        itemVrpPrice = new HashMap<>(itemInfoes.size());
        QFBuilder builder = new QFBuilder();
        builder.add("aos_org","=",orgEntity.getPkValue());
        builder.add("aos_shop","=",actPlanEntity.getDynamicObject("aos_shop").getPkValue());
        builder.add("aos_type","=","vrp");
        builder.add("aos_item","!=","");
        builder.add("aos_price",">",0);
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_shop_price", "aos_item,aos_price", builder.toArray());
        for (DynamicObject dy : dyc) {
            itemVrpPrice.put(dy.getString("aos_item"),dy.getBigDecimal("aos_price"));
        }
    }

    /**
     * 设置折扣
     * @return 折扣
     */
    private Map<String,BigDecimal> setDiscount(){
        BigDecimal discount = actPlanEntity.getBigDecimal("aos_disstrength");
        Map<String,BigDecimal> result = new HashMap<>(itemInfoes.size());
        for (DynamicObject infoe : itemInfoes) {
            result.put(infoe.getString("id"),discount);
        }
        return result;
    }

    private Map<String,Map<String,BigDecimal>> pastPrices;
    /**
     * 店铺过去最低定价价格
     * @param day 天数
     */
    private void setPastPrice(String day){
        if (pastPrices==null)
            pastPrices = new HashMap<>();
        if (pastPrices.containsKey(day))
            return;
        Map<String,BigDecimal> results = new HashMap<>(itemInfoes.size());
        QFBuilder builder = new QFBuilder();
        LocalDate now = LocalDate.now();
        builder.add("aos_date","<",now.toString());
        builder.add("aos_date",">=",now.minusDays(Integer.parseInt(day)).toString());
        builder.add("aos_orgid","=",orgEntity.getPkValue());
        builder.add("aos_shopfid","=",actPlanEntity.getDynamicObject("aos_shop").getPkValue());
        try (AlgoContext context  = Algo.newContext()){
            String algo = "mkt.act.rule.EventRule.setPastPrice";
            DataSet dataSet = QueryServiceHelper.queryDataSet(algo, "aos_mkt_invprz_bak", "aos_itemid,aos_currentprice", builder.toArray(),null);
            DataSet groupDataSet = dataSet.groupBy(new String[]{"aos_itemid"}).min("aos_currentprice").finish();
            for (Row row : groupDataSet) {
                results.put(row.getString("aos_itemid"),row.getBigDecimal("aos_currentprice"));
            }
        }
        pastPrices.put(day,results);
    }

    private Map<String,Map<String,BigDecimal>> pastSale;
    /**
     * 店铺过去卖价价格
     * @param day 天数
     */
    private void setPastSale(String day){
        if (pastSale==null)
            pastSale = new HashMap<>();
        if (pastSale.containsKey(day))
            return;
        QFBuilder builder = new QFBuilder();
        builder.add("aos_org","=",orgEntity.getPkValue());
        builder.add("aos_shopfid","=",actPlanEntity.getDynamicObject("aos_shop").getPkValue());
        LocalDate now = LocalDate.now();
        builder.add("aos_local_date","<",now.toString());
        builder.add("aos_local_date",">=",now.minusDays(Long.parseLong(day)).toString());
        builder.add("aos_item_fid","!=","");
        builder.add("aos_trans_price",">",0);
        OrderLineDao orderLineDao = new OrderLineDaoImpl();
        String select = "aos_item_fid,aos_trans_price";
        Map<String,BigDecimal> results = new HashMap<>(itemInfoes.size());
        DynamicObjectCollection orderInfoes = orderLineDao.queryOrderInfo(select, builder);
        for (DynamicObject row : orderInfoes) {
            String itemFid = row.getString("aos_item_fid");
            BigDecimal value = BigDecimal.ZERO;
            if (results.containsKey(itemFid))
                value = results.get(itemFid);
            if (value.compareTo(row.getBigDecimal("aos_trans_price"))<0) {
                value = row.getBigDecimal("aos_trans_price");
            }
            results.put(itemFid,value);
        }
        pastSale.put(day,results);
    }
}
