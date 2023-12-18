package mkt.act.rule.Import;

import common.CommonDataSomAct;
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
import common.sal.util.QFBuilder;
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
import mkt.act.rule.ActUtil;
import mkt.common.MKTCom;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * @author create by gk
 * @date 2023/8/21 14:11
 * @action  活动规则,用于活动信息表的明细导入
 */
public class EventRule {
    private static final Log logger = LogFactory.getLog("mkt.act.rule.EventRule");
    //活动库单据
    private  DynamicObject typEntity;
    private final DynamicObject actPlanEntity;
    //活动库每行规则的执行结果，key：project+seq,value: (item:result)
    private  Map<String,Map<String,Boolean>> rowResults;
    //国别
    private final DynamicObject orgEntity;
    //国别下所有符合的物料，方便后续筛选
    private List<DynamicObject> itemInfoes;
    //日志
    private  FndLog fndLog;
    //店铺价格剔除表以内的全部物料信息
    private  List<DynamicObject> shopItemInfo;


    //物料对应的品类
    private Map<String,DynamicObject> cateItem;

    public EventRule(DynamicObject typeEntity, DynamicObject dy_main){
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
        addData();
    }

    /**
     * 执行选品公式，判断物料是否应该剔除
     */
    public void implementFormula(){
        String ruleValue = typEntity.getString("aos_rule_v");
        String[] parameterKey = new String[0];
        if (FndGlobal.IsNotNull(ruleValue)){
          parameterKey = FormulaEngine.extractVariables(ruleValue);
        }
        //选品数
        int selectQty = 100;
        if (FndGlobal.IsNotNull(typEntity.get("aos_qty"))) {
            selectQty = Integer.parseInt(typEntity.getString("aos_qty"));
        }


        //根据活动计数维度进行拆分，如果是sku，则按照物料维度计数；否则按照item ID 维度计数
        //计数类型
        String countType = typEntity.getString("aos_count");

        Set<DynamicObject> filterItem ;
        //item 计数
        if (FndGlobal.IsNotNull(countType) && countType.equals("item")){
            filterItem = implementFormulaItem(parameterKey,selectQty,ruleValue);
        }
        //sku 计数
        else {
           filterItem = implementFormulaSku(parameterKey,selectQty,ruleValue);
        }


        //将筛选完成的物料填入活动选品表中
        //选品数
        addData(filterItem);
    }

    /**
     * sku类型的转入数据
     */
    public Set<DynamicObject> implementFormulaSku( String[] parameterKey,int selectQty,String ruleValue){
        Map<String,Object> parameters = new HashMap<>(parameterKey.length);
        //根据国别品类占比确定每个品类的数量
        Map<String, Integer> cateQty = new HashMap<>();
        //最大占比的品类
        String maxProportCate = calCateQty(selectQty, cateQty);
        //通过公式筛选的物料，能够填入单据的数据
        Set<DynamicObject> filterItem = new HashSet<>();
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
            if (!cateItem.containsKey(itemId)){
                continue ;
            }


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
                if ("A".equals(fillType)){
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
        return filterItem;
    }

    /**
     * item类型的转入数据
     * @param parameterKey  公式中包含的参数
     * @param selectQty     帖子ID 数
     * @param ruleValue     公式
     * @return  符合条件的物料
     */
    public Set<DynamicObject> implementFormulaItem(String[] parameterKey,int selectQty,String ruleValue){
        //Item Id 类型时;判断是否全部sku都参加
        boolean addAll = false;
        String whole = typEntity.getString("aos_whole");
        if (FndGlobal.IsNotNull(whole) && "Y".equals(whole)) {
            addAll = true;
        }
        //获取每个物料的Asin
        setItemAsin();
        Map<String,Object> parameters = new HashMap<>(parameterKey.length);
        //根据国别品类占比确定每个品类的数量
        Map<String, Integer> cateQty = new HashMap<>();
        //最大占比的品类
        String maxProportCate = calCateQty(selectQty, cateQty);
        //通过公式筛选的物料，能够填入单据的数据
        Set<DynamicObject> filterItem = new HashSet<>();
        //记录每个品类已经填入数据
        Map<String,Integer> alreadyFilledCate = new HashMap<>();
        //记录每个品类下的帖子ID，以及每个物料是否满足条件
        Map<String,Map<String,List<DynamicObject>>> map_fillInfo = new HashMap<>();
        //筛选数据
        for (DynamicObject itemInfoe : itemInfoes) {
            String itemId = itemInfoe.getString("id");
            String itemNum = itemInfoe.getString("number");
            //首先获取帖子ID
            if (!map_asin.containsKey(itemId)) {
                fndLog.add(itemNum+"   不存在帖子ID剔除");
                continue;
            }
            String itemAsin = map_asin.get(itemId);
            //获取物料品类
            if (!cateItem.containsKey(itemId)){
                fndLog.add(itemNum+"   不存在品类剔除");
                continue;
            }
            String cate = cateItem.get(itemId).getString("gnumber").split(",")[0];
            //不需要这个品类，剔除
            if (!cateQty.containsKey(cate)){
                fndLog.add(itemNum+"   物料品类剔除");
                continue;
            }
            //品类已满
            if (map_fillInfo.size()>= selectQty && !map_fillInfo.containsKey(cate)) {
                fndLog.add(itemNum+"   品类已满");
                continue;
            }
            //已经填入的该品类下的数据
            Map<String, List<DynamicObject>> fillData = map_fillInfo.computeIfAbsent(cate, k -> new HashMap<>());

            //判断是否需要执行公式
            boolean needImp = true;
            //全部 id转入
            if (addAll){
                //判断这个品类是否已经 有执行结果为 true 的数据
                if (fillData.containsKey(itemAsin+"/t")){
                    List<DynamicObject> list = fillData.computeIfAbsent(itemAsin + "/f", k -> new ArrayList<>());
                    list.add(itemInfoe);
                    needImp = false;
                }
            }

            if (needImp){
                parameters.clear();
                for (String key : parameterKey) {
                    parameters.put(key,rowResults.get(key).get(itemId));
                }
                //执行公式
                boolean result = Boolean.parseBoolean(FormulaEngine.execExcelFormula(ruleValue, parameters).toString());
                if (result){
                    List<DynamicObject> list = fillData.computeIfAbsent(itemAsin + "/t", k -> new ArrayList<>());
                    list.add(itemInfoe);
                }
                else{
                    List<DynamicObject> list = fillData.computeIfAbsent(itemAsin + "/f", k -> new ArrayList<>());
                    list.add(itemInfoe);
                }
            }
        }
        //记录已经填入的帖子ID
        List<String> fillAsin = new ArrayList<>(selectQty);
        //筛选完成，将数据填入
        //类别维度遍历
        for (Map.Entry<String, Map<String, List<DynamicObject>>> cateEntry : map_fillInfo.entrySet()) {
            int alradyFillQty;
            //最大的品类,查找全部的数量，用以补充其他品类
            if (cateEntry.getKey().equals(maxProportCate)) {
                alradyFillQty = fillData(cateEntry.getValue(), fillAsin, filterItem, addAll, cateQty.getOrDefault(cateEntry.getKey(), 0));
            }
            else{
                alradyFillQty = fillData(cateEntry.getValue(), fillAsin, filterItem, addAll, selectQty);
            }
            alreadyFilledCate.put(cateEntry.getKey(),alradyFillQty);
        }

        //将数量不足的品类 用其他品类数据补上
        for (Map.Entry<String, Integer> entry : cateQty.entrySet()) {
            //类名
            String cate = entry.getKey();
            //应该填入的总数
            int cateAllQty = entry.getValue();
            //获取需要继续填入的数
            int alreadyFillQty = cateAllQty - alreadyFilledCate.getOrDefault(cate, 0);
            if (alreadyFillQty<=0) {
                continue;
            }
            //先将最多的品类填入
            alreadyFillQty = alreadyFillQty - fillData(map_fillInfo.get(maxProportCate), fillAsin, filterItem, addAll,alreadyFillQty);
            alreadyFilledCate.put(cate,cateAllQty - alreadyFillQty);
        }
        return filterItem;
    }

    /**
     * 填充数据
     * @param cateEntry 品类下对应的 asin数据
     * @param fillAsin  已经填入的asin
     * @param filterItem    已经填入的物料
     * @param addAll        是否全部转入
     * @param fillAllQty    填入aisn个数
     * @return 已经填充数
     */
    private int fillData( Map<String, List<DynamicObject>> cateEntry, List<String> fillAsin, Set<DynamicObject> filterItem,
                           boolean addAll, int fillAllQty){
        if (fillAllQty<=0) {
            return 0;
        }
        if (cateEntry ==null) {
            return 0;
        }
        int alreadyFillQty = 0;
        //Asin维度遍历
        for (Map.Entry<String, List<DynamicObject>> asinEntry : cateEntry.entrySet()) {
            if (asinEntry.getKey().contains("/t")) {
                String asin = asinEntry.getKey().split("/")[0];
                //已经填入过了，跳过这个id
                if (fillAsin.contains(asin)) {
                    continue;
                }
                filterItem.addAll(asinEntry.getValue());
                //全部转入
                if (addAll) {
                    //将结果为false的也加入其中
                    if (cateEntry.containsKey(asin+"/f")){
                        filterItem.addAll( cateEntry.get(asin + "/f"));
                    }
                    //将最开始去掉的物料也加入
                    for (DynamicObject row : map_asinToItem.get(asin)) {
                        if (!filterItem.contains(row)) {
                            fndLog.add(row.getString("number")+"   同Asin转入");
                            filterItem.add(row);
                        }
                    }
                }
                alreadyFillQty++;
                //如果品类数足够，跳出这个品类循环
                if (alreadyFillQty >= fillAllQty){
                    break;
                }
            }
        }
        return alreadyFillQty;
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
    private void addData(Set<DynamicObject> filterItem){
        DynamicObjectCollection dyc = actPlanEntity.getDynamicObjectCollection("aos_sal_actplanentity");
        Date startdate = actPlanEntity.getDate("aos_startdate");
        Date enddate = actPlanEntity.getDate("aos_enddate1");
        //获取ASIN
        setItemAsin();
        //店铺现价
        setCurrentPrice();
        //活动价
        setItemActPrice();
        for (DynamicObject itemInfo : filterItem) {
            DynamicObject addNewRow = dyc.addNew();
            String itemid = itemInfo.getString("id");
            addNewRow.set("aos_itemnum",itemid);
            addNewRow.set("aos_itemname",itemInfo.getString("name"));
            addNewRow.set("aos_l_startdate",startdate);
            addNewRow.set("aos_enddate",enddate);
            addNewRow.set("aos_postid",map_asin.get(itemid));
            addNewRow.set("aos_is_saleout",itemInfo.get("aos_is_saleout"));
            addNewRow.set("aos_fit","Y");
            addNewRow.set("aos_price",itemCurrentPrice.getOrDefault(itemid,BigDecimal.ZERO));
            addNewRow.set("aos_actprice",itemActPrice.getOrDefault(itemid,BigDecimal.ZERO));
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
        fndLog.finnalSave();
    }

    /**
     * 判断活动选品表中的数据是否符合规定
     */
    private void addData(){
        String ruleValue = typEntity.getString("aos_rule_v");
        String[] parameterKey = FormulaEngine.extractVariables(ruleValue);
        Map<String,Object> parameters = new HashMap<>(parameterKey.length);

        DynamicObjectCollection dyc = actPlanEntity.getDynamicObjectCollection("aos_sal_actplanentity");
        Date startdate = actPlanEntity.getDate("aos_startdate");
        Date enddate = actPlanEntity.getDate("aos_enddate1");


        Map<String,DynamicObject> map_itemInfo = new HashMap<>(itemInfoes.size());
        for (DynamicObject infoe : itemInfoes) {
            map_itemInfo.put(infoe.getString("id"),infoe);
        }

        //店铺现价
        setCurrentPrice();

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
                    row.set("aos_price",itemCurrentPrice.getOrDefault(itemid,BigDecimal.ZERO));
                    row.set("aos_actprice",itemActPrice.getOrDefault(itemid,BigDecimal.ZERO));
                }
                else {
                    row.set("aos_fit","N");
                }
            }
        }
        fndLog.finnalSave();
    }


    /**
     * 设置 物料帖子ID
     */
    Map<String,String> map_asin;
    Map<String,List<DynamicObject>> map_asinToItem;
    private void setItemAsin(){
        if (map_asin!=null) {
            return;
        }
        Object shopid = actPlanEntity.getDynamicObject("aos_shop").getPkValue();
        List<String> list_filterItem = new ArrayList<>(shopItemInfo.size());
        for (DynamicObject info :shopItemInfo) {
            list_filterItem.add(info.getString("id"));
        }
        map_asin = ActUtil.queryOrgShopItemASIN(orgEntity.getPkValue(),shopid,list_filterItem);

        //根据帖子ID将物料进行分组
        map_asinToItem = new HashMap<>();
        for (DynamicObject row : shopItemInfo) {
            String itemID = row.getString("id");

            if (map_asin.containsKey(itemID)) {
                String asin = map_asin.get(itemID);
                map_asinToItem.computeIfAbsent(asin,k->new ArrayList<>()).add(row);
            }
        }
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
        logger.info("单号： {} 多次活动sku：{} ",actPlanEntity.getString("billno"),ruleItem.size());

        //通过品类筛选的物料
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
        selectFields.add("aos_contryentry.aos_contrybrand.name aos_contrybrand");
        QFilter filter = new QFilter("aos_contryentry.aos_nationality","=",orgEntity.getPkValue());

        //判断是否参与考核，如果参与考核，需要取值物料清单,不在清单中的，剔除
        List<String> actSelectList = null;
        if (typEntity.get("aos_assessment")!=null) {
            if ("Y".equals(typEntity.getDynamicObject("aos_assessment").getString("number"))) {
                builder.clear();
                LocalDate now = LocalDate.now();
                builder.add("createtime",">=",now.toString());
                builder.add("createtime","<",now.plusDays(1).toString());
                builder.add("aos_entryentity.aos_orgid","=",orgEntity.getString("number"));
                DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_actselect", "aos_entryentity.aos_sku aos_sku", builder.toArray());
                actSelectList = new ArrayList<>(dyc.size());
                for (DynamicObject dy : dyc) {
                    actSelectList.add(dy.getString("aos_sku"));
                }
            }
        }

        //判断是否在店铺价格维护表中在线，不在线，剔除
        List<String> shopPriceItemList = getShopItem();


        DynamicObjectCollection dyc = itemDao.listItemObj(selectFields.toString(), filter, null);
        logger.info("单号： {}  查询物料长度：{} ",actPlanEntity.getString("billno"),dyc.size());
        shopItemInfo = new ArrayList<>(dyc.size());

        for (DynamicObject row : dyc) {
            String itemid = row.getString("id");
            //不在店铺价格参数表中，剔除
            if (shopPriceItemList.contains(itemid)) {
                shopItemInfo.add(row);
            }
        }

        itemInfoes = new ArrayList<>(dyc.size());
        itemInfoes.addAll(dyc);
        //设置毛利率过滤
        Map<String,Boolean> map_actProfit = new HashMap<>(dyc.size());
        getActProfit("actProfit",map_actProfit);
        itemInfoes = new ArrayList<>(dyc.size());
        List<String> fillItem = new ArrayList<>(dyc.size());
        for (DynamicObject row : dyc) {
            String itemid = row.getString("id");
            String itemNum = row.getString("number");
            StringJoiner logRow = new StringJoiner(" ; ");
            logRow.add(itemNum);

            //添加品类过滤
            if (cateItem.size()>0 && !cateItem.containsKey(itemid)){
                logRow.add("不在品类中，剔除");
                fndLog.add(logRow.toString());
                continue;
            }

            //多次参加活动的剔除
            if (ruleItem.contains(itemid)){
                logRow.add("多次参加活动，剔除");
                fndLog.add(logRow.toString());
                continue;
            }

            //不在给定的sku范围内的，剔除
            if (itemids!=null && itemids.size()>0 && !itemids.contains(itemid)){
                logRow.add("不在给定的sku范围内的，剔除");
                fndLog.add(logRow.toString());
                continue;
            }

            //参与考核，且不在选品清单中
            if (actSelectList!= null && !actSelectList.contains(itemNum)){
                logRow.add("参与考核，且不在选品清单中，剔除");
                fndLog.add(logRow.toString());
                continue;
            }

            //不在店铺价格参数表中，剔除
            if (!shopPriceItemList.contains(itemid)) {
                logRow.add("不在店铺价格维护表中，剔除");
                fndLog.add(logRow.toString());
                continue;
            }

            if (!map_actProfit.containsKey(itemid) || !map_actProfit.get(itemid) || fillItem.contains(itemid) ){
                logRow.add("活动毛利剔除，剔除");
                fndLog.add(logRow.toString());
                continue;
            }
            fillItem.add(itemid);
            itemInfoes.add(row);
        }
        logger.info("单号： {}  毛利率计算后物料长度：{} ",actPlanEntity.getString("billno"),itemInfoes.size());

        //设置物料品类
        builder.clear();
        builder.add("material","!=","");
        DynamicObjectCollection cateResults = categoryDao.queryData(str.toString(), builder);
        cateItem.clear();
        for (DynamicObject result : cateResults) {
            cateItem.put(result.getString("material"),result);
        }
    }

    /**
     * 获取在店铺价格维护表中在线 的物料
     */
    private List<String> getShopItem(){
        //判断是否，不在线，剔除
        QFBuilder builder = new QFBuilder();
        builder.add("aos_org","=",orgEntity.getPkValue());
        builder.add("aos_item","!=","");
        //店铺
        if (actPlanEntity.get("aos_shop")!=null) {
            builder.add("aos_shop","=",actPlanEntity.getDynamicObject("aos_shop").getPkValue());
        }


        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_shop_price", "aos_item", builder.toArray());
        List<String> results = new ArrayList<>(dyc.size());
        for (DynamicObject dy : dyc) {
            results.add(dy.getString("aos_item"));
        }
        return results;
    }

    /**
     * 获取去重规则剔除的物料
     */
    private Set<String>  getRuleItem(){
        DynamicObjectCollection ruleEntityRows = typEntity.getDynamicObjectCollection("aos_entryentity3");
        Set<String> result = new HashSet<>();
        LocalDate startdate = actPlanEntity.getDate("aos_startdate").toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate endDate = actPlanEntity.getDate("aos_enddate1").toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

        QFBuilder builder = new QFBuilder();
        //规则单据体，先根据规则单据体中规则，筛选出应该剔除的物料
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
                builder.add("aos_acttype","=",row.getDynamicObject("aos_re_act").getPkValue());
            }
            //同期
            switch (project) {
                case "sameTerm":
                    builder.add("aos_sal_actplanentity.aos_l_startdate", "<=", endDate.plusDays(1).toString());
                    builder.add("aos_sal_actplanentity.aos_enddate", ">=", startdate.toString());
                    break;
                //活动开始日前几天
                case "pastDays": {
                    int day = 0;
                    if (row.get("aos_frame") != null) {
                        day = row.getInt("aos_frame");
                    }
                    builder.add("aos_sal_actplanentity.aos_enddate", ">=", startdate.minusDays(day).toString());
                    builder.add("aos_sal_actplanentity.aos_l_startdate", "<", startdate.toString());
                    break;
                }
                //活动结束日后几天
                case "endDays": {
                    int day = 0;
                    if (row.get("aos_frame") != null) {
                        day = row.getInt("aos_frame");
                    }
                    builder.add("aos_sal_actplanentity.aos_enddate", ">=", endDate.toString());
                    builder.add("aos_sal_actplanentity.aos_l_startdate", "<", endDate.plusDays(day + 1).toString());

                    break;
                }
                default:
                    continue;
            }
            DynamicObjectCollection dyc = QueryServiceHelper.query("aos_act_select_plan",
                    "aos_sal_actplanentity.aos_itemnum aos_itemnum,aos_sal_actplanentity.aos_itemnum.number number", builder.toArray());
            for (DynamicObject dy : dyc) {
                result.add(dy.getString("aos_itemnum"));
            }
        }

        //本月平台活动超过2次的物料
        LocalDate now = LocalDate.now();
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
        //大型活动
        List<String> actType = Arrays.asList("B", "C");
        builder.add("aos_act_type",QFilter.not_in,actType);
        //活动为平台活动
        builder.add("aos_acttype",QFilter.in,listActTypes);
        builder.add("aos_sal_actplanentity.aos_itemnum","!=","");
        //活动时间
        builder.add("aos_sal_actplanentity.aos_enddate",">=",now.withDayOfMonth(1).toString());
        builder.add("aos_sal_actplanentity.aos_enddate","<",now.withDayOfMonth(1).plusMonths(1).toString());

        dyc = QueryServiceHelper.query("aos_act_select_plan",
                "aos_sal_actplanentity.aos_itemnum aos_itemnum,aos_sal_actplanentity.aos_itemnum.number number", builder.toArray());

        Map<String,Integer> itemActNumber = new HashMap<>(dyc.size());
        for (DynamicObject dy : dyc) {
            String item = dy.getString("aos_itemnum");
            int actFreq = itemActNumber.getOrDefault(item, 0)+1;
            if (actFreq==3){
                fndLog.add(dy.getString("number")+"  多次活动剔除");
                result.add(item);
            }
            itemActNumber.put(item,actFreq);
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

            if ("in".equals(condite)){
                String[] split = value.split(",");
                rule.append(" ( ");
                for (int i = 0; i < split.length; i++) {
                    rule.append("'");
                    rule.append(split[i]);
                    rule.append("'");
                    if (i<split.length-1) {
                        rule.append(",");
                    }
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
                if ("shopPrice".equals(caption)){
                    setPastPrice(day);
                    parameters.put(value,pastPrices.get(day));
                }
                //店铺过去最低成交价
                else if ("shopSale".equals(caption)){
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
        rowRuleName = MKTCom.getComboMap("aos_sal_act_type_p", "aos_project");
        FormulaResults formulaResults = new FormulaResults(orgEntity,itemInfoes,fndLog,rowRuleName);
        switch (dataType){
            case "overseasStock":
                //海外库存
                setItemStock();
                formulaResults.getFormulaResultLong(dataType,rule,result,itemSotck);
                break;
            case "saleDays":
                //可售天数
                setSaleDays();
                formulaResults.getFormulaResult(dataType,rule,result, itemSaleDay);
                break;
            case "itemStatus":
                //物料状态
                setItemStatus();
                formulaResults.getFormulaResultStr(dataType,rule,result,itemStatus);
                break;
            case "season":
                //季节属性
                setItemSeason();
                formulaResults.getFormulaResultStr(dataType,rule,result, itemSeason);
                break;
            case "countyAverage":
                //国别日均
                getItemAverage(dataType,rule,result,row);
                break;
            case "activityDayInv":
                //活动库存
                setActInv();
                formulaResults.getFormulaResult(dataType,rule,result,itemActInv);
                break;
            case "activityDay":
                //活动日可售天数
                setActSaleDay();
                formulaResults.getFormulaResult(dataType,rule,result,itemActSalDay);
                break;
            case "reviewScore":
                //review 分数
                setReview();
                formulaResults.getFormulaResultBde(dataType,rule,result,reviewStars);
                break;
            case "reviewNumber":
                //review 个数
                setReview();
                formulaResults.getFormulaResult(dataType,rule,result,reviewQty);
                break;
            case "price":
                //定价
                setItemPrice();
                formulaResults.getFormulaResultBde(dataType,rule,result,itemPrice);
                break;
            case "activityPrice":
                //活动价格
                setItemActPrice();
                formulaResults.getFormulaResultBde(dataType,rule,result,itemActPrice);
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
                setItemUnsale();
                formulaResults.getFormulaResultStr(dataType,rule,result,itemUnsaleTyep);
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
                //非平台仓
                setItemNoPlatStock();
                formulaResults.getFormulaResultLong(dataType,rule,result,itemNoPlatStock);
                break;
            case "actProfit":
                //活动毛利率
                getActProfit(dataType,result);
                break;
            case "festive":
                //执行关于节日的公式
                formulaResults.getItemFestive(dataType,rule,result);
                break;
            case "brands":
                //品牌
                formulaResults.getFormulaResult(dataType,rule,result);
                break;
            case "platformSalDay":
                //平台仓可售天数
                setItemPlatSalDay();
                formulaResults.getFormulaResultBde(dataType,rule,result,platSalDay);
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
        Map<String, String> countryStatus = MKTCom.getComboMap("bd_material", "aos_contryentrystatus");

        for (DynamicObject infoe : itemInfoes) {
            String itemid = infoe.getString("id");
            String status = infoe.getString("aos_contryentrystatus");
            itemStatus.put(itemid,countryStatus.get(status));
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
     * 执行国别日均的表达式
     * @param key       参数名
     * @param rule      公式
     * @param result    结果
     * @param row       行
     */
    private void getItemAverage(String key,String rule,Map<String,Boolean> result,DynamicObject row){
        int days = 7;
        if (FndGlobal.IsNotNull(row.get("aos_rule_day"))) {
            days = row.getInt("aos_rule_day");
        }
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
            if ("US".equals(number) || "CA".equals(number)){
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
        if (itemActInv!=null) {
            return;
        }
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

    private Map<String,Integer> itemActSalDay;
    /**
     * 活动可售天数
     */
    private void setActSaleDay(){
        if (itemActSalDay !=null) {
            return;
        }
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

    private Map<String,BigDecimal> reviewStars;
    private Map<String,Integer> reviewQty;
    private void setReview(){
        if (reviewStars!=null) {
            return;
        }
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

    private Map<String,BigDecimal> itemPrice;
    /**
     * 定价
     */
    private void setItemPrice(){
        if (itemPrice!=null) {
            return;
        }
        ItemPriceDao itemPriceDao = new ItemPriceDaoImpl();
        Object shopid = actPlanEntity.getDynamicObject("aos_shop").getPkValue();
        itemPrice = itemPriceDao.queryShopItemPrice(orgEntity.getPkValue(),shopid);
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
                calParameters.put(entry.getKey(),entry.getValue().getOrDefault(itemid,BigDecimal.ONE));
            }
            BigDecimal price = new BigDecimal(FormulaEngine.execExcelFormula(priceformula, calParameters).toString());
            price = price.setScale(0,BigDecimal.ROUND_DOWN);
            price = price.subtract(BigDecimal.ONE).add(custom);
            itemActPrice.put(itemid,price);
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
        if (FndGlobal.IsNull(day)) {
            return;
        }
        //判断这种天数的销量是否已经计算过
        if (itemShopSale.containsKey(String.valueOf(day))) {
            return;
        }
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
        if (FndGlobal.IsNotNull(row.get("aos_rule_day"))) {
            day = row.getInt("aos_rule_day");
        }
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
        Map<String, String> typeValue = MKTCom.getComboMap("aos_base_stitem", "aos_type");
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
        if (itemMaxAge!=null) {
            return;
        }
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
        if (itemPlatStock!=null) {
            return;
        }
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
        if (itemNoPlatStock!=null) {
            return;
        }
        ItemStockDao itemStockDao = new ItemStockDaoImpl();
        itemNoPlatStock = itemStockDao.listItemNoPlatformStock(orgEntity.getLong("id"));
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
        //物料滞销类型
        setItemUnsale();
        //设置季节品的阶段
        setSeasonStage();
        //毛利标准
        setProfitStand();
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
            str.add("7天日均销量： "+saleAve);
            String type = MKTCom.Get_RegularUn(orgEntity.getString("number"), saleDay, saleAve);
            str.add("滞销类型： "+type);
            if ("低动销".equals(type)){
                if (profitStand.containsKey("低动销1") && profitStand.get("低动销1").contains(itemId) ){
                    result.put(itemId,true);
                }
            }
            else if ("低周转".equals(type)){
                String stand;
                if (saleAve<=3){
                    stand = "低周转1";
                }else {
                    stand = "低周转2";
                }
                if (profitStand.containsKey(stand) && profitStand.get(stand).contains(itemId)){
                    result.put(itemId,true);
                }
            }
            fndLog.add(str.toString());
        }

        //判断是否常规品滞销类型
        for (DynamicObject itemInfoe : itemInfoes) {
            String itemId = itemInfoe.getString("id");
            if (result.containsKey(itemId)) {
                continue;
            }
            StringJoiner str = new StringJoiner(" , ");
            str.add(itemInfoe.getString("number"));
            str.add("物料滞销类型  "+itemUnsaleTyep.get(itemId));
            fndLog.add(str.toString());

            if (itemUnsaleTyep.containsKey(itemId)){
                String unsaleType = itemUnsaleTyep.get(itemId);
                String stand;
                //货多销少;货少销少
                switch (unsaleType) {
                    case "货多销少":
                        stand = "常规滞销1";
                        break;
                    case "货少销少":
                        stand = "常规滞销2";
                        break;
                    //新品销少
                    case "新品销少":
                        stand = "常规滞销3";
                        break;
                    default:
                        continue;
                }
                if (profitStand.containsKey(stand) && profitStand.get(stand).contains(itemId)){
                    result.put(itemId,true);
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
            // 当季
            if (FndGlobal.IsNotNull(type)){
                if (profitStand.containsKey(type) && profitStand.get(type).contains(itemId)){
                    result.put(itemId,true);
                }
            }
            // 非当季
            else {
                String seasonpro = itemInfoe.getString("seasonpro");
                //春夏品
                if (FndGlobal.IsNotNull(seasonpro) && "SPRING_SUMMER_PRO".equals(seasonpro)){
                    String stand;
                    //判断是否 春夏滞销
                    if (itemUnsaleTyep.containsKey(itemId) && "SPRING".equals(itemUnsaleTyep.get(itemId))){
                        stand = "季节品4";
                    }
                    else {
                        stand = "季节品5";
                    }
                    //判断是否满足标准
                    if (profitStand.containsKey(stand) && profitStand.get(stand).contains(itemId)){
                        result.put(itemId,true);
                    }
                }
            }
            fndLog.add(itemInfoe.getString("number")+"  季节品滞销判断  "+type);
        }

        //爆品
        for (DynamicObject itemInfoe : itemInfoes) {
            String itemId = itemInfoe.getString("id");
            if (result.containsKey(itemId)) {
                continue;
            }
            //爆品
            fndLog.add(itemInfoe.getString("number")+"  爆品类型判断  "+itemInfoe.getBoolean("aos_is_saleout"));
            if (itemInfoe.getBoolean("aos_is_saleout")){
                if (profitStand.containsKey("爆品") && profitStand.get("爆品").contains(itemId)){
                    result.put(itemId,true);
                }
                else {
                    result.put(itemId,false);
                }
            }
            else {
                result.put(itemId,false);
            }
        }
    }

    Map<String,List<String>> profitStand;
    /**
     * 获取符合各个毛利率标准的物料
     */
    private void setProfitStand(){
        if (profitStand!=null) {
            return;
        }
        //计算最惨定价
        setMinPrice();
        profitStand = new HashMap<>();
        String select = "aos_type1,aos_use,aos_pro_"+(orgEntity.getString("number").toLowerCase())+" aos_pro";
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_act_pro", select, null);
        for (DynamicObject dy : dyc) {
            //项目名称
            String project = dy.getString("aos_type1");
            //采用最低定价
            if (dy.getBoolean("aos_use")) {
                profitStand.put(project,minPriceItem);
            }
            //比较毛利率
            else {
                //毛利标准
                BigDecimal proStand = dy.getBigDecimal("aos_pro");
                if (proStand == null){
                    proStand = BigDecimal.ZERO;
                }
                List<String> filterItems = new ArrayList<>(itemInfoes.size());
                for (DynamicObject itemInfoe : itemInfoes) {
                    String itemId = itemInfoe.getString("id");
                    BigDecimal itemProfit = BigDecimal.ZERO;
                    if (itemActProfit.containsKey(itemId)){
                        itemProfit = itemActProfit.get(itemId).getOrDefault("value",BigDecimal.ZERO);
                    }
                    if (itemProfit.compareTo(proStand)>=0){
                        filterItems.add(itemId);
                    }
                }
                profitStand.put(project,filterItems);
            }
        }
    }

    /**
     * 计算满足最惨定价的物料
     */
    List<String> minPriceItem;
    private void setMinPrice(){
        if (minPriceItem!=null) {
            return;
        }
        setItemActProfit();
        minPriceItem = new ArrayList<>(itemInfoes.size());
        for (DynamicObject itemInfoe : itemInfoes) {
            String itemId = itemInfoe.getString("id");
            StringJoiner str = new StringJoiner(" , ");
            str.add(itemInfoe.getString("number"));
            if (itemActProfit.containsKey(itemId)){
                Map<String, BigDecimal> profit = itemActProfit.get(itemId);
                BigDecimal fee = profit.getOrDefault("aos_lowest_fee", BigDecimal.ZERO);
                str.add("fee : "+fee);
                BigDecimal cost = profit.getOrDefault("aos_item_cost", BigDecimal.ZERO);
                str.add("cost : "+cost);
                BigDecimal price = itemPrice.getOrDefault(itemId, BigDecimal.ZERO);
                price = fee
                        .add(price.multiply(BigDecimal.valueOf(0.15)))
                        .add(cost.multiply(BigDecimal.valueOf(0.3)));
                str.add("price : "+price);
                BigDecimal actPrice = itemActPrice.getOrDefault(itemId, BigDecimal.ZERO);
                str.add("actprice: "+actPrice);
                if (actPrice.compareTo(price)>=0){
                    minPriceItem.add(itemId);
                }else {
                    str.add("不满足最惨定价");
                }
            }
            else {
                str.add("活动毛利为空，最惨价 fales");
            }
            fndLog.add(str.toString());
        }
    }

    Map<String,String> seasonStage;
    /**
     * 设置季节品和节日品的 季初、季中，季末
     */
    private void setSeasonStage(){
        if (seasonStage!=null) {
            return;
        }
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
                    case "ACCH-1":
                        seasonStage.put("CHRISTMAS", "季节品1");
                        break;
                    //圣诞 季中
                    case "ACCH-2":
                        seasonStage.put("CHRISTMAS", "季节品2");
                        break;
                    //圣诞 季末
                    case "ACCH-3":
                        seasonStage.put("CHRISTMAS", "季节品3");
                        break;
                    //万圣节 季初
                    case "ACHA-1":
                        seasonStage.put("HALLOWEEN", "季节品1");
                        break;
                    //万圣节 季中
                    case "ACHA-2":
                        seasonStage.put("HALLOWEEN", "季节品2");
                        break;
                    //万圣节 季末
                    case "ACHA-3":
                        seasonStage.put("HALLOWEEN", "季节品3");
                        break;
                    //春夏品 季初
                    case "ACTSS-1":
                        seasonStage.put("SPRING_SUMMER_PRO", "季节品1");
                        break;
                    //春夏品 季中
                    case "ACTSS-2":
                        seasonStage.put("SPRING_SUMMER_PRO", "季节品2");
                        break;
                    //春夏品 季中
                    case "ACTSS-3":
                        seasonStage.put("SPRING_SUMMER_PRO", "季节品3");
                        break;
                    //秋冬品 季初
                    case "ACTAW-1":
                        seasonStage.put("AUTUMN_WINTER_PRO", "季节品1");
                        break;
                    //秋冬品 季中
                    case "ACTAW-2":
                        seasonStage.put("AUTUMN_WINTER_PRO", "季节品2");
                        break;
                    //秋冬品 季末
                    case "ACTAW-3":
                        seasonStage.put("AUTUMN_WINTER_PRO", "季节品3");
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
        if (itemActProfit != null) {
            return;
        }
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
            itemActProfit = CommonDataSomAct.get_formula2(orgEntity.getString("id"), shopId, map_itemPrice, "/");
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
            if (missingPrice.contains(entry.getKey())) {
                itemCurrentPrice.put(entry.getKey(),entry.getValue());
            }
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
        if (discount ==null) {
            discount = BigDecimal.ONE;
        }
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
        if (pastPrices==null) {
            pastPrices = new HashMap<>();
        }
        if (pastPrices.containsKey(day)) {
            return;
        }
        Map<String,BigDecimal> results = new HashMap<>(itemInfoes.size());
        QFBuilder builder = new QFBuilder();
        LocalDate now = LocalDate.now();
        builder.add("aos_date","<",now.toString());
        builder.add("aos_date",">=",now.minusDays(Integer.parseInt(day)).toString());
        builder.add("aos_orgid","=",orgEntity.getPkValue());
        builder.add("aos_shopfid","=",actPlanEntity.getDynamicObject("aos_shop").getPkValue());
        try (AlgoContext ignored = Algo.newContext()){
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
        if (pastSale==null) {
            pastSale = new HashMap<>();
        }
        if (pastSale.containsKey(day)) {
            return;
        }
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
            if (results.containsKey(itemFid)) {
                value = results.get(itemFid);
            }
            if (value.compareTo(row.getBigDecimal("aos_trans_price"))<0) {
                value = row.getBigDecimal("aos_trans_price");
            }
            results.put(itemFid,value);
        }
        pastSale.put(day,results);
    }

    private Map<String,BigDecimal> platSalDay;
    /**
     * 设置平台仓可售天数
     */
    private void setItemPlatSalDay (){
        if (platSalDay!=null) {
            return;
        }
        platSalDay = new HashMap<>();

        //获取7天店铺销量
        setItemShopSale(7);
        Map<Long, Integer> shopSalQty = itemShopSale.getOrDefault("7",new HashMap<>());
        //获取平台仓库存和非平台仓库存
        setItemPlatStock();
        setItemNoPlatStock();
        for (DynamicObject itemInfoe : itemInfoes) {
            long itemID = Long.parseLong(itemInfoe.getString("id"));
            //库存
            int sock = itemPlatStock.getOrDefault(itemID,0);
            if (sock ==0){
                sock = itemNoPlatStock.getOrDefault(itemID,0);
            }
            //销量
            int salQty = shopSalQty.getOrDefault(itemID, 0);
            if (sock == 0){
                platSalDay.put(String.valueOf(itemID),new BigDecimal(0));
            }
            else {
                if (salQty ==0 ){
                    platSalDay.put(String.valueOf(itemID),new BigDecimal(999));
                }
                else{
                    platSalDay.put(String.valueOf(itemID),new BigDecimal(sock).divide(new BigDecimal(salQty),1,BigDecimal.ROUND_UP));
                }
            }
        }
    }

 }
