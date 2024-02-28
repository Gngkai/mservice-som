package mkt.act.rule.Import;

import common.fnd.FndGlobal;
import common.fnd.FndLog;
import common.sal.sys.basedata.dao.ItemCategoryDao;
import common.sal.sys.basedata.dao.ItemDao;
import common.sal.sys.basedata.dao.impl.ItemCategoryDaoImpl;
import common.sal.sys.basedata.dao.impl.ItemDaoImpl;
import common.sal.util.DateUtil;
import common.sal.util.QFBuilder;
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
import mkt.common.MktComUtil;

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
    public static final Log logger = LogFactory.getLog("mkt.act.rule.EventRule");
    //活动库单据
    public   DynamicObject typEntity;
    //活动选品单据
    public final DynamicObject actPlanEntity;
    //活动库每行规则的执行结果，key：project+seq,value: (item:result)
    public Map<String,Map<String,Boolean>> rowResults;
    //国别
    public  DynamicObject orgEntity;
    //国别下所有符合的物料，方便后续筛选
    public List<DynamicObject> itemInfoes;
    //日志
    public   FndLog fndLog;
    //店铺价格剔除表以内的全部物料信息
    public   List<DynamicObject> shopItemInfo;
    //物料对应的品类
    public Map<String,DynamicObject> cateItem;
    /**
     * 设置 物料帖子ID
     */
    Map<String,String> map_asin;
    Map<String,List<DynamicObject>> map_asinToItem;
    //工具对象
    public EventRuleUtil util;

    public EventRule(DynamicObject typeEntity, DynamicObject dy_main){
        try {
            this.typEntity = typeEntity;
            this.actPlanEntity = dy_main;
            this.fndLog = FndLog.init("活动选品明细导入", actPlanEntity.getString("billno"));
            orgEntity = dy_main.getDynamicObject("aos_nationality");
            util = new EventRuleUtil(this);
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
        if (FndGlobal.IsNull(actPlanEntity.get("aos_rule_v"))) {
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
        util = new EventRuleUtil(this);
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
        String ruleValue = actPlanEntity.getString("aos_rule_v");
        String[] parameterKey = new String[0];
        if (FndGlobal.IsNotNull(ruleValue)){
          parameterKey = FormulaEngine.extractVariables(ruleValue);
        }
        //选品数
        int selectQty = 100;
        if (FndGlobal.IsNotNull(typEntity.get("aos_qty"))) {
            selectQty = Integer.parseInt(typEntity.getString("aos_qty"));
        }


        // 计数类型 根据活动计数维度进行拆分，如果是sku，则按照物料维度计数；否则按照item ID 维度计数
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

        //将筛选完成的物料填入活动选品表中  选品数
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

        //判断公式是否为空
        boolean ruleValueIsNull = FndGlobal.IsNotNull(ruleValue);

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
            boolean result = true;
            if (ruleValueIsNull){
              result = Boolean.parseBoolean(FormulaEngine.execExcelFormula(ruleValue, parameters).toString());
            }

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
        util.setItemAsin();
        Map<String,Object> parameters = new HashMap<>(parameterKey.length);
        //根据国别品类占比确定每个品类的数量
        Map<String, Integer> cateQty = new HashMap<>();
        //最大占比的品类
        String maxProportCate = calCateQty(selectQty, cateQty);
        //备用数据，即最大品类占比下对应的物料，如果其他品类缺少数据，就从中取数补上
        int backCateSize = cateQty.getOrDefault("maxCate", 0);
        //通过公式筛选的物料，能够填入单据的数据
        Set<DynamicObject> filterItem = new HashSet<>();
        //记录每个品类已经填入数据
        Map<String,Integer> alreadyFilledCate = new HashMap<>();
        //记录每个品类下的帖子ID，以及每个物料是否满足条件
        Map<String,Map<String,List<DynamicObject>>> map_fillInfo = new HashMap<>();

        //判断公式是否为空
        boolean ruleValueIsNull = FndGlobal.IsNotNull(ruleValue);

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
            //物料能够填入的ASIN 数
            int cateTotalQty = cateQty.get(cate);
            //物料已经填入的ASIN 数
            int fillQty = alreadyFilledCate.getOrDefault(cate, 0);
            //如果填入已满，且不是最大占比的品类，剔除
            if (fillQty >= cateTotalQty && !cate.equals(maxProportCate)){
                fndLog.add(itemNum+"   物料品类已满剔除");
                continue;
            }
            //如果填入已满，且是最大占比的品类
            else if (fillQty >= cateTotalQty && cate.equals(maxProportCate)){
                //判断备用数据是否已经满了
                cate = "maxCate";
                fillQty = alreadyFilledCate.getOrDefault(cate, 0);
                if (fillQty >= backCateSize){
                    fndLog.add(itemNum+"   物料品类已满剔除");
                    continue;
                }
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
                boolean result = true;
                if (ruleValueIsNull){
                    result = Boolean.parseBoolean(FormulaEngine.execExcelFormula(ruleValue, parameters).toString());
                }
                if (result){
                    List<DynamicObject> list = fillData.computeIfAbsent(itemAsin + "/t", k -> new ArrayList<>());
                    list.add(itemInfoe);
                    fillQty++;
                    alreadyFilledCate.put(cate,fillQty);
                }
                else{
                    if (addAll){
                        List<DynamicObject> list = fillData.computeIfAbsent(itemAsin + "/f", k -> new ArrayList<>());
                        list.add(itemInfoe);
                    }

                }
            }
        }
        //记录已经填入的帖子ID
        List<String> fillAsin = new ArrayList<>(selectQty);
        //筛选完成，将数据填入;类别维度遍历

        for (Map.Entry<String, Map<String, List<DynamicObject>>> cateEntry : map_fillInfo.entrySet()) {
            //最大的品类,查找全部的数量，用以补充其他品类
            if (cateEntry.getKey().equals("maxCate")) {
                continue;
            } else {
               fillData(cateEntry.getValue(), fillAsin, filterItem, addAll, cateQty.getOrDefault(cateEntry.getKey(), 0));
            }
        }

        //将数量不足的品类 用其他品类数据补上
        for (Map.Entry<String, Integer> entry : cateQty.entrySet()) {
            //品类名
            String cate = entry.getKey();
            //如果是备用品类，跳过
            if (cate.equals("maxCate")) {
                continue;
            }

            //应该填入的总数
            int cateAllQty = entry.getValue();
            //获取需要继续填入的数
            int needFillQty = cateAllQty - alreadyFilledCate.getOrDefault(cate, 0);
            if (needFillQty<=0) {
                continue;
            }
            //最多的品类填入
            int missQty = needFillQty - fillData(map_fillInfo.get("maxCate"), fillAsin, filterItem, addAll,needFillQty);
            alreadyFilledCate.put(cate,cateAllQty - missQty);
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
    public int fillData( Map<String, List<DynamicObject>> cateEntry, List<String> fillAsin, Set<DynamicObject> filterItem,
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
    public String calCateQty(int selectQty,Map<String, Integer> result){
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
    public void addData(Set<DynamicObject> filterItem){
        DynamicObjectCollection dyc = actPlanEntity.getDynamicObjectCollection("aos_sal_actplanentity");
        Date startdate = actPlanEntity.getDate("aos_startdate");
        Date enddate = actPlanEntity.getDate("aos_enddate1");
        //获取ASIN
        util.setItemAsin();
        //店铺现价
        util.setCurrentPrice();
        //活动价
        util.setItemActPrice();
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
            addNewRow.set("aos_price",util.itemCurrentPrice.getOrDefault(itemid,BigDecimal.ZERO));
            addNewRow.set("aos_actprice",util.itemActPrice.getOrDefault(itemid,BigDecimal.ZERO));
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
            if (weightMap!=null && weightMap.containsKey(itemid)){
                addNewRow.set("aos_sort",weightMap.get(itemid));
            }
            //设置毛利率
            if (util.itemActProfit.containsKey(itemid)) {
                Map<String, BigDecimal> actProInfo = util.itemActProfit.get(itemid);
                addNewRow.set("aos_profit", actProInfo.getOrDefault("aos_profit",BigDecimal.ZERO));
                addNewRow.set("aos_item_cost", actProInfo.getOrDefault("aos_item_cost",BigDecimal.ZERO));
                addNewRow.set("aos_lowest_fee", actProInfo.getOrDefault("aos_lowest_fee",BigDecimal.ZERO));
                addNewRow.set("aos_plat_rate", actProInfo.getOrDefault("aos_plat_rate",BigDecimal.ZERO));
                addNewRow.set("aos_vat_amount", actProInfo.getOrDefault("aos_vat_amount",BigDecimal.ZERO));
                addNewRow.set("aos_excval", actProInfo.getOrDefault("aos_excval",BigDecimal.ZERO));
            }
        }
        fndLog.finnalSave();
    }

    /**
     * 判断活动选品表中的数据是否符合规定
     */
    public void addData(){
        String ruleValue = actPlanEntity.getString("aos_rule_v");
        String[] parameterKey = FormulaEngine.extractVariables(ruleValue);
        Map<String,Object> parameters = new HashMap<>(parameterKey.length);

        DynamicObjectCollection dyc = actPlanEntity.getDynamicObjectCollection("aos_sal_actplanentity");
        Date startdate = actPlanEntity.getDate("aos_startdate");
        Date enddate = actPlanEntity.getDate("aos_enddate1");


        Map<String,DynamicObject> map_itemInfo = new HashMap<>(itemInfoes.size());
        for (DynamicObject infoe : itemInfoes) {
            map_itemInfo.put(infoe.getString("id"),infoe);
        }

        //获取ASIN
        util.setItemAsin();
        //店铺现价
        util.setCurrentPrice();

        //判断规则是否为空
        boolean ruleIsNull = FndGlobal.IsNotNull(ruleValue);

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
                boolean result = true;
                if (ruleIsNull){
                    result = Boolean.parseBoolean(FormulaEngine.execExcelFormula(ruleValue, parameters).toString());
                }

                if (result) {
                    row.set("aos_fit","Y");
                    row.set("aos_price",util.itemCurrentPrice.getOrDefault(itemid,BigDecimal.ZERO));
                    row.set("aos_actprice",util.itemActPrice.getOrDefault(itemid,BigDecimal.ZERO));
                }
                else {
                    row.set("aos_fit","N");
                }
                //设置毛利率
                if (util.itemActProfit.containsKey(itemid)) {
                    Map<String, BigDecimal> actProInfo = util.itemActProfit.get(itemid);
                    row.set("aos_profit", actProInfo.getOrDefault("aos_profit",BigDecimal.ZERO));
                    row.set("aos_item_cost", actProInfo.getOrDefault("aos_item_cost",BigDecimal.ZERO));
                    row.set("aos_lowest_fee", actProInfo.getOrDefault("aos_lowest_fee",BigDecimal.ZERO));
                    row.set("aos_plat_rate", actProInfo.getOrDefault("aos_plat_rate",BigDecimal.ZERO));
                    row.set("aos_vat_amount", actProInfo.getOrDefault("aos_vat_amount",BigDecimal.ZERO));
                    row.set("aos_excval", actProInfo.getOrDefault("aos_excval",BigDecimal.ZERO));
                }

            }


        }
        fndLog.finnalSave();
    }

    /**
     * 获取国别下的物料
     */
    public void setItemInfo(List<String> itemids){
        //先根据活动库中的品类筛选出合适的物料
        ItemCategoryDao categoryDao = new ItemCategoryDaoImpl();
        QFBuilder builder = new QFBuilder();
        DynamicObjectCollection cateRowEntity = actPlanEntity.getDynamicObjectCollection("aos_entryentity1");

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

        //判断是否参与考核，如果参与考核，需要取值物料清单 和 周销售推荐清单,不在清单中的，剔除
        List<String> actSelectList = null;
        if (typEntity.get("aos_assessment")!=null) {
            if ("Y".equals(typEntity.getDynamicObject("aos_assessment").getString("number"))) {
                builder.clear();
                LocalDate now = LocalDate.now();
                builder.add("createtime",">=",now.toString());
                builder.add("createtime","<",now.plusDays(1).toString());
                builder.add("aos_entryentity.aos_orgid","=",orgEntity.getString("number"));
                DynamicObjectCollection actDyc = QueryServiceHelper.query("aos_mkt_actselect", "aos_entryentity.aos_sku aos_sku", builder.toArray());


                //查找 周销售推荐清单 中的数据
                builder.clear();
                builder.add("aos_org","=",orgEntity.getString("id"));
                Calendar instance = Calendar.getInstance();
                Date date = actPlanEntity.getDate("aos_startdate");
                if (date!=null){
                    instance.setTime(date);
                    DateUtil.setTodayCalendar(instance);
                    builder.add("entryentity.aos_date_e",">=",instance.getTime());
                }
                date = actPlanEntity.getDate("aos_enddate1");
                if (date!=null){
                    instance.setTime(date);
                    DateUtil.setTodayCalendar(instance);
                    builder.add("entryentity.aos_date_s","<=",instance.getTime());
                }
                DynamicObjectCollection proposeDyc = QueryServiceHelper.query("aos_act_propose", "entryentity.aos_item aos_item", builder.toArray());
                actSelectList = new ArrayList<>(actDyc.size()+proposeDyc.size());
                for (DynamicObject dy : actDyc) {
                    actSelectList.add(dy.getString("aos_sku"));
                }
                for (DynamicObject dy : proposeDyc) {
                    actSelectList.add(dy.getString("aos_item"));
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

        //记录ID和物料的对应关系,用于后面优先级排序
        Map<String,DynamicObject> itemMap = new HashMap<>(dyc.size());
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

            if (!map_actProfit.containsKey(itemid) || !map_actProfit.get(itemid) ){
                logRow.add("活动毛利剔除，剔除");
                fndLog.add(logRow.toString());
                continue;
            }
            if (fillItem.contains(itemid) || itemInfoes.contains(row)){
                logRow.add("重复物料，剔除");
                fndLog.add(logRow.toString());
                continue;
            }

            itemMap.put(itemid,row);
            fillItem.add(itemid);
            itemInfoes.add(row);
        }
        logger.info("单号： {}  毛利率计算后物料长度：{} ",actPlanEntity.getString("billno"),itemInfoes.size());

        //进行优先级排序
        List<String> sortItem = setWeight();
        itemInfoes.clear();
        for (String itemid : sortItem) {
            if (itemMap.containsKey(itemid)){
                DynamicObject itemInfo = itemMap.get(itemid);
                String number = itemInfo.getString("number");
                fndLog.add("编码： "+number+" 总分数： "+weightMap.get(itemid));
                itemInfoes.add(itemMap.get(itemid));
            }
        }

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
    public List<String> getShopItem(){
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
    public Set<String>  getRuleItem(){
        DynamicObjectCollection ruleEntityRows = actPlanEntity.getDynamicObjectCollection("aos_entryentity3");
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

        //判断是否要超过两次的活动，eBay、AM、WF渠道，除黑五网一，平台推荐产品 不做剔除
        boolean isMoreThanTwo = true;
        //eBay、AM
        if (actPlanEntity.getDynamicObject("aos_channel")!=null) {
            String channel = actPlanEntity.getDynamicObject("aos_channel").getString("number");
            if (channel.equals("AMAZON") || channel.equals("EBAY") ) {
                isMoreThanTwo = false;
            }
        }
        //wf
        if (isMoreThanTwo){
            DynamicObject aos_shop = actPlanEntity.getDynamicObject("aos_shop");
            String number = aos_shop.getString("number");
            if (number.equals(orgEntity.getString("number")+"-Wayfair")) {
                isMoreThanTwo = false;
            }
        }
        //黑五网一
        if (isMoreThanTwo){
            String aos_act_type = actPlanEntity.getString("aos_act_type");
            if (FndGlobal.IsNotNull(aos_act_type) && (aos_act_type.equals("B") || aos_act_type.equals("C"))) {
                isMoreThanTwo = false;
            }
        }
        //平台推荐产品
        if (isMoreThanTwo){
            String aos_suggest = typEntity.getString("aos_suggest");
            if (FndGlobal.IsNotNull(aos_suggest) && aos_suggest.equals("是")) {
                isMoreThanTwo = false;
            }
        }

        //需要查超过两次的活动
        if (isMoreThanTwo){
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
            //大型活动
            List<String> actType = Arrays.asList("B", "C");
            builder.add("aos_act_type",QFilter.not_in,actType);
            //活动为平台活动
            builder.add("aos_acttype",QFilter.in,listActTypes);
            builder.add("aos_sal_actplanentity.aos_itemnum","!=","");
            //活动时间
            builder.add("aos_sal_actplanentity.aos_enddate",">=",endDate.withDayOfMonth(1).toString());
            builder.add("aos_sal_actplanentity.aos_enddate","<",endDate.withDayOfMonth(1).plusMonths(1).toString());

            dyc = QueryServiceHelper.query("aos_act_select_plan",
                    "aos_sal_actplanentity.aos_itemnum aos_itemnum,aos_sal_actplanentity.aos_itemnum.number number", builder.toArray());

            Map<String,Integer> itemActNumber = new HashMap<>(dyc.size());
            for (DynamicObject dy : dyc) {
                String item = dy.getString("aos_itemnum");
                int actFreq = itemActNumber.getOrDefault(item, 0)+1;
                if (actFreq==3){
                    result.add(item);
                }
                itemActNumber.put(item,actFreq);
            }
        }


        return result;
    }

    /**
     * 解析规则
     */
    public  void parsingRowRule (){
        //规则单据体
        DynamicObjectCollection rulEntity = actPlanEntity.getDynamicObjectCollection("aos_entryentity2");
        StringBuilder rule = new StringBuilder();
        for (DynamicObject row : rulEntity) {
            //先拼凑公式
            //序列号
            String seq = row.getString("seq");
            rule.setLength(0);
            //规则类型
            String project = row.getString("aos_act_project");
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

    public Map<String,String> rowRuleName;
    /**
     * 判断公式的类型并且执行
     * @param rule      公式
     * @param dataType  公式类型
     * @param row       公式行
     * @return  每个物料对应的执行结果
     */
    public Map<String,Boolean> cachedData (String rule,String dataType,DynamicObject row){
        Map<String,Boolean> result = new HashMap<>(itemInfoes.size());
        //获取下拉列表
        rowRuleName = MktComUtil.getComboMap("aos_sal_act_type_p", "aos_project");
        FormulaResults formulaResults = new FormulaResults(this);
        switch (dataType){
            case "overseasStock":
                //海外库存
                util.setItemStock();
                formulaResults.getFormulaResultLong(dataType,rule,result,util.itemSotck);
                break;
            case "saleDays":
                //可售天数
                util.setSaleDays();
                formulaResults.getFormulaResult(dataType,rule,result, util.itemSaleDay);
                break;
            case "itemStatus":
                //物料状态
                util.setItemStatus();
                formulaResults.getFormulaResultStr(dataType,rule,result,util.itemStatus);
                break;
            case "season":
                //季节属性
                util.setItemSeason();
                formulaResults.getFormulaResultStr(dataType,rule,result, util.itemSeason);
                break;
            case "countyAverage":
                //国别日均
                getItemAverage(dataType,rule,result,row);
                break;
            case "activityDayInv":
                //活动库存
                util.setActInv();
                formulaResults.getFormulaResult(dataType,rule,result,util.itemActInv);
                break;
            case "activityDay":
                //活动日可售天数
                util.setActSaleDay();
                formulaResults.getFormulaResult(dataType,rule,result,util.itemActSalDay);
                break;
            case "reviewScore":
                //review 分数
                util.setReview();
                formulaResults.getFormulaResultBde(dataType,rule,result,util.reviewStars);
                break;
            case "reviewNumber":
                //review 个数
                util.setReview();
                formulaResults.getFormulaResult(dataType,rule,result,util.reviewQty);
                break;
            case "price":
                //定价
                util.setItemPrice();
                formulaResults.getFormulaResultBde(dataType,rule,result,util.itemPrice);
                break;
            case "activityPrice":
                //活动价格
                util.setItemActPrice();
                formulaResults.getFormulaResultBde(dataType,rule,result,util.itemActPrice);
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
                util.setItemUnsale();
                formulaResults.getFormulaResultStr(dataType,rule,result,util.itemUnsaleTyep);
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
                util.setItemNoPlatStock();
                formulaResults.getFormulaResultLong(dataType,rule,result,util.itemNoPlatStock);
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
                util.setItemPlatSalDay();
                formulaResults.getFormulaResultBde(dataType,rule,result,util.platSalDay);
                break;
        }
        return result;
    }

    /**
     * 执行国别日均的表达式
     * @param key       参数名
     * @param rule      公式
     * @param result    结果
     * @param row       行
     */
    public void getItemAverage(String key,String rule,Map<String,Boolean> result,DynamicObject row){
        int days = 7;
        if (FndGlobal.IsNotNull(row.get("aos_rule_day"))) {
            days = row.getInt("aos_rule_day");
        }
        util.setItemAverageByDay(days);
        Map<String,Object> paramars = new HashMap<>();
        //对应天数的国别日均
        Map<String, BigDecimal> itemAverageFoDay = util.itemAverage.get(String.valueOf(days));

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

    /**
     * 执行关于爆品 公式
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    public void getHotIteme(String key,String rule,Map<String,Boolean> result){
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

    /**
     * 执行关于店铺历史销量 公式
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    public void getItemShopSale(String key,String rule,Map<String,Boolean> result,DynamicObject row){
        int day = 7;
        if (FndGlobal.IsNotNull(row.get("aos_rule_day"))) {
            day = row.getInt("aos_rule_day");
        }
        util.setItemShopSale(day);
        Map<Long, Integer> shopSale = util.itemShopSale.get(String.valueOf(day));
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

    /**
     * 执行关于周滞销 公式
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    public void getItemWeekUnsale(String key,String rule,Map<String,Boolean> result){
        util.setItemWeekUnsale();
        Map<String,Object> paramars = new HashMap<>();
        for (DynamicObject itemInfoe : itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            Object value = util.itemWeekUnsale.getOrDefault(itemid,"0");
            fndLog.add(itemInfoe.getString("number")+"  "+rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }

    /**
     * 执行关于最大库龄 公式
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    public void getItemMaxAge(String key,String rule,Map<String,Boolean> result){
        util.setItemMaxAge();
        Map<String,Object> paramars = new HashMap<>();
        for (DynamicObject itemInfoe : itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            Object value = util.itemMaxAge.getOrDefault(Long.parseLong(itemid),0);
            fndLog.add(itemInfoe.getString("number")+"  "+rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }

    /**
     * 执行关于平台仓库 公式
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    public void getItemPlatStock(String key,String rule,Map<String,Boolean> result){
        util.setItemPlatStock();
        Map<String,Object> paramars = new HashMap<>();
        for (DynamicObject itemInfoe : itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            Object value = util.itemPlatStock.getOrDefault(Long.parseLong(itemid),0);
            fndLog.add(itemInfoe.getString("number")+"  "+rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
            result.put(itemid,Boolean.parseBoolean(FormulaEngine.execExcelFormula(rule,paramars).toString()));
        }
    }

    /**
     * 执行活动毛利率的公式
     */
    public void getActProfit(String key,Map<String,Boolean> result){
        //首先计算活动毛利率，先获取活动毛利率计算公式
        util.setItemActProfit();
        //可售天数
        util.setSaleDays();
        //7天日均
        util.setItemAverageByDay(7);
        Map<String, BigDecimal> itemSaleAve = util.itemAverage.get(String.valueOf(7));
        //物料滞销类型
        util.setItemUnsale();
        //设置季节品的阶段
        util.setSeasonStage();
        //毛利标准
        util.setProfitStand();
        //判断是否低动销
        for (DynamicObject itemInfoe : itemInfoes) {
            StringJoiner str = new StringJoiner(" , ");
            str.add(key);
            //物料id
            String itemId = itemInfoe.getString("id");
            str.add(itemInfoe.getString("number"));
            //获取物料毛利
            BigDecimal itemProfit = BigDecimal.ZERO;
            if (util.itemActProfit.containsKey(itemId)){
                itemProfit = util.itemActProfit.get(itemId).getOrDefault("value",BigDecimal.ZERO);
            }
            str.add("毛利率：  "+itemProfit);
            //可售天数
            int saleDay = util.itemSaleDay.getOrDefault(itemId, 0);
            str.add("可售天数： "+saleDay);
            //7天日均销量
            float saleAve = itemSaleAve.getOrDefault(itemId, BigDecimal.ZERO).floatValue();
            str.add("7天日均销量： "+saleAve);
            String type = MktComUtil.getRegularUn(orgEntity.getString("number"), saleDay, saleAve);
            str.add("滞销类型： "+type);
            if ("低动销".equals(type)){
                if (util.profitStand.containsKey("低动销1") && util.profitStand.get("低动销1").contains(itemId) ){
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
                if (util.profitStand.containsKey(stand) && util.profitStand.get(stand).contains(itemId)){
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
            str.add("物料滞销类型  "+util.itemUnsaleTyep.get(itemId));
            fndLog.add(str.toString());

            if (util.itemUnsaleTyep.containsKey(itemId)){
                String unsaleType = util.itemUnsaleTyep.get(itemId);
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
                if (util.profitStand.containsKey(stand) && util.profitStand.get(stand).contains(itemId)){
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
                if (util.seasonStage.containsKey(itemInfoe.getString("festival"))){
                    type = util.seasonStage.get(itemInfoe.getString("festival"));
                }
            }
            //判断是否是季节品
            if (FndGlobal.IsNull(type) && FndGlobal.IsNotNull(itemInfoe.get("seasonpro"))){
                if (util.seasonStage.containsKey(itemInfoe.getString("seasonpro"))){
                    type = util.seasonStage.get(itemInfoe.getString("seasonpro"));
                }
            }
            // 当季
            if (FndGlobal.IsNotNull(type)){
                if (util.profitStand.containsKey(type) && util.profitStand.get(type).contains(itemId)){
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
                    if (util.itemUnsaleTyep.containsKey(itemId) && "SPRING".equals(util.itemUnsaleTyep.get(itemId))){
                        stand = "季节品4";
                    }
                    else {
                        stand = "季节品5";
                    }
                    //判断是否满足标准
                    if (util.profitStand.containsKey(stand) && util.profitStand.get(stand).contains(itemId)){
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
                if (util.profitStand.containsKey("爆品") && util.profitStand.get("爆品").contains(itemId)){
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

    /**
     * 计算物料的优先级
     */
    Map<String,BigDecimal> weightMap;
    public List<String> setWeight(){
        weightMap = new HashMap<>(itemInfoes.size());
        DynamicObjectCollection weightEntityRows = actPlanEntity.getDynamicObjectCollection("aos_entryentity4");
        for (DynamicObject row : weightEntityRows) {
            String project = row.getString("aos_pr_project");
            BigDecimal  prWeight = row.getBigDecimal("aos_pr_weight");
            String prWay = row.getString("aos_pr_way");
            int prDay = row.getInt("aos_pr_day");
            if (FndGlobal.IsNull(project) || FndGlobal.IsNull(prWeight)){
                continue;
            }
            switch (project){
                case "爆品":
                    //计算爆品的分数
                    util.setSaleOutWeight(prWeight);
                    break;
                case "销售推荐":
                    util.setRecommendWeight(prWeight);
                    break;
                case "历史活动增量":
                    if (FndGlobal.IsNotNull(prDay) && prDay>0){
                        util.setHistoryWeight(prDay,prWeight,prWay);
                    }
                    break;
                case "店铺过去销量":
                    if (FndGlobal.IsNotNull(prDay) && prDay>0){
                        util.setShopHistoryWeight(prDay,prWeight,prWay);
                    }
                    break;
                case "国别日均":
                    if (FndGlobal.IsNotNull(prDay) && prDay>0){
                        util.setOrgAvgWeight(prDay,prWeight,prWay);
                    }
                    break;
                case "预计活动日库存数量":
                    util.setActStockWeight(prWeight,prWay);
                    break;
                case "预计活动日可售天数":
                    util.setActSaleDayWeight(prWeight,prWay);
                    break;
                case "当前可售天数":
                    util.setSalDayWeight(prWeight,prWay);
                    break;
                case "当前库存金额":
                    util.setStockPriceWeight(prWeight,prWay);
                    break;
                case "活动毛利率":
                    util.setActProWeight(prWeight,prWay);
                    break;
                case "平台仓库存数量":
                    util.setPlantStockWeight(prWeight,prWay);
                    break;
                default:
                    break;
            }
        }
        //根据分数进行降序排序
        return   util.sortDesc(weightMap);
    }

}
