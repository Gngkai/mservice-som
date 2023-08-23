package mkt.act.rule;

import common.fms.util.FieldUtils;
import common.fnd.FndGlobal;
import common.sal.sys.basedata.dao.ItemDao;
import common.sal.sys.basedata.dao.impl.ItemDaoImpl;
import common.sal.sys.sync.dao.ItemStockDao;
import common.sal.sys.sync.dao.OrderLineDao;
import common.sal.sys.sync.dao.WarehouseStockDao;
import common.sal.sys.sync.dao.impl.ItemStockDaoImpl;
import common.sal.sys.sync.dao.impl.OrderLineDaoImpl;
import common.sal.sys.sync.dao.impl.WarehouseStockDaoImpl;
import common.sal.sys.sync.service.ItemCacheService;
import common.sal.sys.sync.service.impl.ItemCacheServiceImpl;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
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
        rowRule = new HashMap<>();
        rowRuleType = new HashMap<>();
        parsingRule();
        orgEntity = dy_main.getDynamicObject("aos_nationality");
        setItemInfo();
    }
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
        }
    }

    Map<Long,Integer> itemSotck;
    private void setItemStock(){
        if (itemSotck!=null) {
            return;
        }
        itemSotck = new HashMap<>();
        ItemStockDao stockDao = new ItemStockDaoImpl();
        itemSotck = stockDao.listItemOverSeaStock(orgEntity.getLong("id"));
    }

    Map<String,Integer> itemSaleDay;
    private void setSaleDays(){
        if (itemSaleDay !=null) {
            return;
        }
        QFBuilder builder = new QFBuilder();
        builder.add("aos_ou_code","=",orgEntity.getString("number"));
        builder.add("aos_item_code","!=","");
        builder.add("aos_7avadays",">=",0);
        DynamicObjectCollection results = QueryServiceHelper.query("aos_sal_dw_invavadays", "aos_item_code,aos_7avadays", builder.toArray());
        for (DynamicObject result : results) {
            itemSaleDay.put(result.getString("aos_item_code"),result.getInt("aos_7avadays"));
        }
    }

    Map<String,String> itemStatus;
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


    /*
    Map<String,Integer> itemActSaleDay(){
        //设置活动库存
        setActInv();
        //设置7天日均销量
        setItemAverageByDay(7);

    }
    */


}
