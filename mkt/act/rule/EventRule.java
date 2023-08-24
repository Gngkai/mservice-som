package mkt.act.rule;

import common.fms.util.FieldUtils;
import common.fnd.FndGlobal;
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
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
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
    DynamicObject typEntity,actPlanEntity;
    //活动库每行规则
    Map<String,String> rowRule,rowRuleType;
    //国别
    DynamicObject orgEntity;
    //国别下所有的物料，方便后续筛选
    DynamicObjectCollection itemInfoes;

    EventRule(Object typeID,DynamicObject dy_main){
        this.typEntity = BusinessDataServiceHelper.loadSingle(typeID, "aos_sal_act_type_p");
        this.actPlanEntity = dy_main;
        //活动规则标识
        String aos_rule_v = typEntity.getString("aos_rule_v");
        //活动价计算公式标识

        parsingPriceRule(typEntity.getString("aos_priceformula_v"));
        setItemInfo();
        rowRule = new HashMap<>();
        rowRuleType = new HashMap<>();
        parsingRule();
        orgEntity = dy_main.getDynamicObject("aos_nationality");

    }

    /**
     * 获取国别下的物料
     */
    private void setItemInfo(){
        ItemDao itemDao = new ItemDaoImpl();
        StringJoiner selectFields = new StringJoiner(",");
        selectFields.add("id");
        selectFields.add("name");
        selectFields.add("number");
        selectFields.add("aos_contryentry.aos_contryentrystatus aos_contryentrystatus");
        selectFields.add("aos_contryentry.aos_seasonseting aos_seasonseting");

        QFilter filter = new QFilter("aos_contryentry.aos_nationality","=",orgEntity.getPkValue());
        itemInfoes = itemDao.listItemObj(selectFields.toString(),filter,null);
    }

    /**
     * 解析价格公式
     * @param formal    公式
     */
    private void parsingPriceRule(String formal){
        String[] values = FormulaEngine.extractVariables(formal);
        for (String value : values) {
            //匹配成功
            boolean match = false;
            switch (value){
                case "currentPrice":
                    match = true;
                    break;
                case "webPrice":
                    match = true;
                    break;
                case "vrp":
                    match = true;
                    break;
            }
            //上面没有匹配成功则说明 需要用到天数
            if (!match){
                String caption = value.substring(0, value.length() - 2);
                String day = value.substring(value.length() - 2);
                //店铺过去最低定价
                if (caption.equals("shopPrice")){

                }
                //店铺过去最低成交价
                else if (caption.equals("shopSale")){

                }

            }
        }
    }

    /**
     * 解析规则
     */
    private  void parsingRule (){
        //规则单据体
        DynamicObjectCollection rulEntity = typEntity.getDynamicObjectCollection("aos_entryentity2");
        StringBuilder rule = new StringBuilder();
        for (DynamicObject row : rulEntity) {
            //序列号
            String seq = row.getString("seq");
            rule.setLength(0);
            //规则类型
            String project = row.getString("aos_project");
            cachedData(project,row);
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
            rowRule.put(seq,rule.toString());
            rowRuleType.put(seq,project);
        }
    }

    private void cachedData (String dataType,DynamicObject row){
        switch (dataType){
            case "overseasStock":
                //海外库存
                setItemStock(); break;
            case "saleDays":
                //可售天数
                setSaleDays();
                break;
            case "itemStatus":
                //物料状态
                setItemStatus();
                break;
            case "season":
                //季节属性
                setItemSeason();
                break;
            case "countyAverage":
                //国别日均
                setItemAverage(row);
                break;
            case "activityDayInv":
                //活动库存
                setActInv();
                break;
            case "activityDay":
                //活动日可售天数
                setActSaleDay();
                break;
            case "reviewScore":
            case "reviewNumber":
                //review 分数和个数
                setReview();
                break;
            case "price":
                //定价
                setItemPrice();
                break;
            case "activityPrice":
                //活动价格
                //setItemActPrice();
                break;
            case "hot":
                //爆品
                break;
            case "historicalSales":
                //店铺历史销量(销售订单)
                setItemShopSale(row);
                break;
            case "unsalType":
                //滞销类型
                setItemUnsale();
                break;
            case "weekUnsold":
                //周滞销
                setItemWeekUnsale();
                break;
            case "inventAge":
                //最大库龄
                setItemMaxAge();
                break;
            case "platform":
                //平台仓库存
                setItemPlatStock();
                break;
            case "ownWarehouse":
                setItemNoPlatStock();
                break;
        }
    }

    Map<Long,Integer> itemSotck;
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

    Map<String,Integer> itemSaleDay;
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

    Map<String,String> itemStatus;
    /**
     * 物料状态
     */
    private void setItemStatus(){
        if (itemSotck!=null) {
            return;
        }
        ItemDao itemDao = new ItemDaoImpl();
        Map<Long, String> result = itemDao.listItemStatus(orgEntity.getLong("id"));
        //获取下拉列表
        Map<String, String> countryStatus = FieldUtils.getComboMap("bd_material", "aos_contryentrystatus");
        itemSotck = new HashMap<>(result.size());
        for (Map.Entry<Long, String> entry : result.entrySet()) {
            itemStatus.put(entry.getKey().toString(),countryStatus.get(entry.getValue()));
        }

    }

    Map<String,String> itemSeason;
    /**
     * 季节属性
     */
    private void setItemSeason(){
        if (itemSeason!=null) {
            return;
        }
        ItemDao itemDao = new ItemDaoImpl();
        StringJoiner str = new StringJoiner(",");
        str.add("id");
        str.add("aos_contryentry.aos_seasonseting.name aos_seasonseting");
        QFilter filter = new QFilter("aos_contryentry.aos_nationality","=",orgEntity.getPkValue());
        DynamicObjectCollection results = itemDao.listItemObj(str.toString(), filter, null);
        itemSeason = new HashMap<>(results.size());
        for (DynamicObject result : results) {
            itemSeason.put(result.getString("id"),result.getString("aos_seasonseting"));
        }
    }

    /**
     *
     * @param row
     */
    private void setItemAverage(DynamicObject row){
        int days = 7;
        if (FndGlobal.IsNotNull(row.get("aos_rule_day")))
            days = row.getInt("aos_rule_day");
        setItemAverageByDay(days);
    }

    Map<String,Map<String, BigDecimal>> itemAverage;
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

    Map<String,Integer> itemActInv;
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

    Map<String,Integer> itemActSalDay;
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

    Map<String,BigDecimal> reviewStars;
    Map<String,Integer> reviewQty;
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

    Map<String,BigDecimal> itemPrice;
    private void setItemPrice(){
        if (itemPrice!=null)
            return;
        ItemPriceDao itemPriceDao = new ItemPriceDaoImpl();
        Object shopid = actPlanEntity.getDynamicObject("aos_shop").getPkValue();
        itemPrice = itemPriceDao.queryShopItemPrice(orgEntity.getPkValue(),shopid);
    }

    Map<String,BigDecimal> itemActPrice;
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
        //
    }

    Map<String,Map<Long,Integer>> itemShopSale;
    private void setItemShopSale(DynamicObject row){
        if (itemShopSale==null) {
            itemShopSale = new HashMap<>();
        }
        int day = row.getInt("aos_rule_day");
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

    Map<String,String> itemUnsaleTyep;
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

    Map<String,String> itemWeekUnsale;
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

    Map<Long,Integer> itemMaxAge;
    private void setItemMaxAge(){
        if (itemMaxAge!=null)
            return;
        ItemAgeDao ageDao = new ItemAgeDaoImpl();
        itemMaxAge = ageDao.listItemMaxAgeByOrgId(orgEntity.getLong("id"));
    }

    Map<Long,Integer> itemPlatStock;
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

    Map<Long,Integer> itemNoPlatStock;
    private void setItemNoPlatStock(){
        if (itemNoPlatStock!=null)
            return;
        ItemStockDao itemStockDao = new ItemStockDaoImpl();
        itemNoPlatStock = itemStockDao.listItemNoPlatformStock(orgEntity.getLong("id"));
    }

    private void setCurrentPrice(){

    }
}
