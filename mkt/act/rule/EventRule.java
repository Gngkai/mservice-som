package mkt.act.rule;

import common.fms.util.FieldUtils;
import common.fnd.FndGlobal;
import common.fnd.FndLog;
import common.sal.sys.basedata.dao.ItemDao;
import common.sal.sys.basedata.dao.impl.ItemDaoImpl;
import common.sal.sys.sync.dao.*;
import common.sal.sys.sync.dao.impl.*;
import common.sal.sys.sync.service.AvailableDaysService;
import common.sal.sys.sync.service.ItemCacheService;
import common.sal.sys.sync.service.impl.AvailableDaysServiceImpl;
import common.sal.sys.sync.service.impl.ItemCacheServiceImpl;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.formula.FormulaEngine;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.fi.bd.util.QFBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * @author create by gk
 * @date 2023/8/21 14:11
 * @action  活动规则信息
 */
public class EventRule {
    //活动库单据
    private DynamicObject typEntity,actPlanEntity;
    //活动库每行规则的执行结果，key：project+seq,value: (item:result)
    private Map<String,Map<String,Boolean>> rowResults;
    //国别
    private DynamicObject orgEntity;
    //国别下所有的物料，方便后续筛选
    private DynamicObjectCollection itemInfoes;
    //日志
    private  FndLog fndLog;
    EventRule(DynamicObject typeEntity,DynamicObject dy_main){
        this.typEntity = typeEntity;
        this.actPlanEntity = dy_main;
        orgEntity = dy_main.getDynamicObject("aos_nationality");
        //获取用于计算的物料
        setItemInfo();
        //解析行公式
        rowResults = new HashMap<>();
        this.fndLog = FndLog.init("活动选品明细导入", actPlanEntity.getString("billno"));
        parsingRowRule();
    }

    /**
     * 执行选品公式，判断物料是否应该剔除
     */
    public void implementFormula(){
        String ruleValue = typEntity.getString("aos_rule_v");
        String[] parameterKey = FormulaEngine.extractVariables(ruleValue);
        Map<String,Object> parameters = new HashMap<>(parameterKey.length);
        List<DynamicObject> filterItem = new ArrayList<>(itemInfoes.size());
        for (DynamicObject itemInfoe : itemInfoes) {
            parameters.clear();
            String itemId = itemInfoe.getString("id");
            for (String key : parameterKey) {
                parameters.put(key,rowResults.get(key).get(itemId));
            }
            //执行公式
            Boolean result = Boolean.parseBoolean(FormulaEngine.execExcelFormula(ruleValue, parameters).toString());
            if (result){
                filterItem.add(itemInfoe);
            }
        }
        //将筛选完成的物料填入活动选品表中
        addData(filterItem);
        fndLog.finnalSave();
    }

    /**
     * 向活动选品表中添加数据
     */
    private void addData(List<DynamicObject> filterItem){
        //选品数
        int selectQty = 100;
        if (FndGlobal.IsNotNull(typEntity.get("aos_qty"))) {
            selectQty = Integer.parseInt(typEntity.getString("aos_qty"));
        }
        selectQty = selectQty < filterItem.size() ? selectQty:filterItem.size();
        DynamicObjectCollection dyc = actPlanEntity.getDynamicObjectCollection("aos_sal_actplanentity");
        for (int index = 0; index <selectQty; index++) {
            DynamicObject itemInfo = filterItem.get(index);
            DynamicObject addNewRow = dyc.addNew();
            addNewRow.set("aos_itemnum",itemInfo.getString("id"));
            addNewRow.set("aos_itemname",itemInfo.getString("name"));
        }
        SaveServiceHelper.save(new DynamicObject[]{actPlanEntity});
    }

    /**
     * 获取国别下的物料
     */
    private void setItemInfo(){
        //先根据活动库中的品类筛选出合适的物料
        QFBuilder builder = new QFBuilder();
        DynamicObjectCollection cateRowEntity = typEntity.getDynamicObjectCollection("aos_entryentity1");
        for (DynamicObject row : cateRowEntity) {
//            builder.add("")
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
        QFilter filter = new QFilter("aos_contryentry.aos_nationality","=",orgEntity.getPkValue());
        itemInfoes = itemDao.listItemObj(selectFields.toString(),filter,null);
    }

    /**
     * 解析规则
     */
    private  void parsingRowRule (){
        //规则单据体
        DynamicObjectCollection rulEntity = typEntity.getDynamicObjectCollection("aos_entryentity2");
        StringBuilder rule = new StringBuilder();
        for (DynamicObject row : rulEntity) {
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
        itemSotck = stockDao.listItemOverSeaStock(orgEntity.getLong("id"));
    }
    /**
     * 执行关于海外库存的表达式
     * @param key      参数名
     * @param rule     公式
     * @param result   结果
     */
    private void getItemStock(String key,String rule,Map<String,Boolean> result){
        setItemStock();
        Map<String,Object> paramars = new HashMap<>();
        for (DynamicObject itemInfoe : itemInfoes)
        {
            paramars.clear();
            String itemid = itemInfoe.getString("id");
            Object value = itemSotck.getOrDefault(Long.parseLong(itemid),0);
            fndLog.add(itemInfoe.getString("number")+"  "+rowRuleName.get(key)+" : "+value);
            paramars.put(key,value);
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
            int stock = itemSotck.getOrDefault(itemID, 0);
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
     * 国别日均
     * @param day
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
            Date endDate = instance.getTime();
            instance.add(Calendar.DATE,-day);
            Date startDate = instance.getTime();
            OrderLineDao orderLineDao = new OrderLineDaoImpl();
            Map<Long, Integer> saleInfo = orderLineDao.listItemSalesByOrgId(orgEntity.getLong("id"), startDate, endDate);
            Map<String,BigDecimal> itemSale = new HashMap<>(saleInfo.size());
            if (day!=0){
                BigDecimal bd_day = new BigDecimal(day);
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
            if (monthIndex<monthIndex){
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
            int stock = itemSotck.getOrDefault(itemid, 0);
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
        //
        Map<String,Object> calParameters = new HashMap<>();
        for (DynamicObject itemInfoe : itemInfoes) {
            calParameters.clear();
            String itemid = itemInfoe.getString("id");
            //获取物料对应的公式中每个参数对应的值
            for (Map.Entry<String, Map<String, BigDecimal>> entry : parameters.entrySet()) {
                calParameters.put(entry.getKey(),entry.getValue().getOrDefault(itemid,BigDecimal.ZERO));
            }
            itemActPrice.put(itemid,new BigDecimal(FormulaEngine.execExcelFormula(priceformula, calParameters).toString()));
        }
    }
    /**
     * 执行关于定价 公式
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
    private void setItemShopSale(int day){
        if (itemShopSale==null) {
            itemShopSale = new HashMap<>();
        }
        if (FndGlobal.IsNull(day))
            return;
        if (itemShopSale.containsKey(String.valueOf(day)))
            return;

        long orgID = orgEntity.getLong("id");
        long shopId = actPlanEntity.getDynamicObject("aos_shop").getLong("id");
        Calendar instance = Calendar.getInstance();
        Date end = instance.getTime();
        instance.add(Calendar.DATE,-day);
        Date start = instance.getTime();

        OrderLineDao orderLineDao = new OrderLineDaoImpl();
        Map<Long, Integer> result = orderLineDao.listItemSales(orgID, null, shopId, start, end);
        itemShopSale.put(String.valueOf(day),result);
    }
    /**
     * 执行关于店铺历史销量
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
     * 计算店铺当前价格
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
     * 计算店铺当前价格
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
