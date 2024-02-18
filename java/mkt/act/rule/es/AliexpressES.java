package mkt.act.rule.es;

import com.alibaba.fastjson.JSONObject;
import common.sal.util.InStockAvailableDays;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import mkt.act.dao.ActShopPriceDao;
import mkt.act.dao.impl.ActShopPriceImpl;
import mkt.act.rule.ActStrategy;
import mkt.act.rule.ActUtil;
import mkt.common.MktComUtil;

import java.math.BigDecimal;
import java.util.*;

public class AliexpressES implements ActStrategy {
    @Override
    public void doOperation(DynamicObject object) throws Exception {
        String aos_orgnum = object.getDynamicObject("aos_nationality").getString("number");
        Date start = object.getDate("aos_startdate");
        Date end = object.getDate("aos_enddate1");
        // 国别id
        String aos_orgid = object.getDynamicObject("aos_nationality").getString("id");
        String aos_channelid = object.getDynamicObject("aos_channel").getString("id");
        String aos_shopid = object.getDynamicObject("aos_shop").getString("id");
        String aos_shopnum = object.getDynamicObject("aos_shop").getString("number");
        String aos_acttypenum = object.getDynamicObject("aos_acttype").getString("number");

        DynamicObject aos_sync_log = ActUtil.getCommonLog("AliexpressES", aos_orgnum);
        DynamicObjectCollection aos_sync_logS = aos_sync_log.getDynamicObjectCollection("aos_entryentity");

        // 折扣信息
        BigDecimal aos_disamt = object.getBigDecimal("aos_disamt");
        BigDecimal aos_disstrength = object.getBigDecimal("aos_disstrength");
        // 至活动日间隔天数
        int currentToAct = ActUtil.currentToActivityDateBetweenDays(start);

        Set<String> apartFromAmzItem = ActUtil.queryApartFromAmzAndEbayItem(aos_orgid, new String[]{"AMAZON"}, start);
        // 剔除同国别同期活动
        Set<String> samePeriodActivitiesSet = ActUtil.querySamePeriodActivities(aos_orgid, null, start, end);
        // 同店铺
        Set<String> daysBeforeAndAfterActItem = ActUtil.query30DaysBeforeAndAfterActItem(aos_orgid, null,new String[]{aos_shopid}, start, 30);
        // 第一次过滤选品清单
        List<DynamicObject> firstFilterList = new ArrayList<>();
        // 物料list
        List<String> itemFilterList = new ArrayList<>();
        DynamicObjectCollection selectList = ActUtil.queryActSelectList(aos_orgnum);
        for (DynamicObject obj : selectList) {
            String aos_sku = obj.getString("aos_sku");
            String aos_seasonattr = obj.getString("aos_seasonattr");
            // 剔除过季品
            if (ActUtil.isOutSeason(start, aos_seasonattr)) {
                MktComUtil.putSyncLog(aos_sync_logS, aos_sku + " 季节属性:" + aos_seasonattr + " 过季");
                continue;
            }

            if (apartFromAmzItem.contains(aos_sku)) {
                MktComUtil.putSyncLog(aos_sync_logS, aos_sku + "剔除除亚马逊外活动日过去30天已提报平台活动个数＞3的sku");
                continue;
            }

            if (samePeriodActivitiesSet.contains(aos_sku)) {
                MktComUtil.putSyncLog(aos_sync_logS, aos_sku + "剔除除剔除同国别同期活动");
                continue;
            }

            if (daysBeforeAndAfterActItem.contains(aos_sku)) {
                MktComUtil.putSyncLog(aos_sync_logS, aos_sku + " 剔除同店铺前后30天已提报活动的SKU");
                continue;
            }
            firstFilterList.add(obj);
            itemFilterList.add(aos_sku);
        }
        Map<String, Integer> nonPlatItemSet = ActUtil.queryNonPlatQty(aos_orgid);
//        DynamicObject orgActivityQty = ActUtil.getOrgActivityQty(aos_orgid);
        Map<String, DynamicObject> priceMap = ActUtil.getAsinAndPriceFromPrice(aos_orgid, aos_shopid, aos_channelid, itemFilterList);
        Map<String, DynamicObject> itemInfo = ActUtil.getItemInfo(itemFilterList, aos_orgid);
        Map<String, BigDecimal> itemNewCostMap = ActUtil.queryItemNewCost(aos_orgid);
        // 查询节日销售时间
        Map<String, String[]> festivalStartAndEnd = ActUtil.queryFestivalStartAndEnd(aos_orgid);

        //店铺价格
        List<String> list_noPriceItem = new ArrayList<>(itemFilterList.size());
        String entityId = object.getString("id");
        ActShopPriceDao actShopPriceDao = new ActShopPriceImpl();
        Map<String, BigDecimal> map_shopPrice = actShopPriceDao.QueryOrgChannelShopItemNumberPrice(aos_orgid, aos_channelid, aos_shopid, itemFilterList);

        // 第二次过滤
        List<JSONObject> secFilterList = new ArrayList<>();
        for (DynamicObject obj : firstFilterList) {
            String aos_sku = obj.getString("aos_sku");
            String aos_seasonattr = obj.getString("aos_seasonattr");
            DynamicObject itemObj = itemInfo.get(aos_sku);
            if (itemObj == null) {
                MktComUtil.putSyncLog(aos_sync_logS, aos_sku + "取不到物料信息");
                continue;
            }
            String aos_itemid = itemObj.getString("aos_itemid");

            // ASIN
            DynamicObject priceObj = priceMap.get(aos_sku);
            String asin = "";
            BigDecimal aos_currentprice;
            if (priceObj == null) {	//每日价格不存在
                if (map_shopPrice.containsKey(aos_itemid)){ //店铺价格存在
                    aos_currentprice = map_shopPrice.get(aos_itemid);
                }
                else {
                    MktComUtil.putSyncLog(aos_sync_logS, aos_sku + " 未获取到价格");
                    list_noPriceItem.add(aos_sku);
                    continue;
                }
            }
            else {
                asin = priceObj.getString("aos_asin");// ASIN码
                aos_currentprice = priceObj.getBigDecimal("aos_currentprice");// 当前价格
            }


            String aos_festivalseting = itemObj.getString("aos_festivalseting");
            // 自有仓库库存数量
            Integer ownWarehouseQty = nonPlatItemSet.getOrDefault(aos_sku, 0);
            if (!(ownWarehouseQty > 50)) {
                MktComUtil.putSyncLog(aos_sync_logS, aos_sku + "自有仓库<=50 自有仓库库存数量:" + ownWarehouseQty);
                continue;
            }

            // 预计活动日可售天数
            int salDaysForAct = InStockAvailableDays.calInstockSalDaysForAct(aos_orgid, aos_itemid, start);
            // 常规品: 预计活动日可售天数>= 90
            if ("REGULAR".equals(aos_seasonattr) || "SPRING-SUMMER-CONVENTIONAL".equals(aos_seasonattr)) {
                if (salDaysForAct < 90) {
                    MktComUtil.putSyncLog(aos_sync_logS, aos_sku + "常规品: 预计活动日可售天数< 90");
                    continue;
                }
            }

            int springSummerProToActStartDateBetweenDays = ActUtil.springSummerProToActStartDateBetweenDays(aos_seasonattr, start);
            if (salDaysForAct < springSummerProToActStartDateBetweenDays) {
                MktComUtil.putSyncLog(aos_sync_logS, aos_sku + "春夏品: 预计活动日可售天数=" + salDaysForAct + " 季末-预计活动日=" + springSummerProToActStartDateBetweenDays);
                continue;
            }
            int autumnWinterProToActStartDateBetweenDays = ActUtil.autumnWinterProToActStartDateBetweenDays(aos_seasonattr, start);
            if (salDaysForAct < autumnWinterProToActStartDateBetweenDays) {
                MktComUtil.putSyncLog(aos_sync_logS, aos_sku + "春夏品: 预计活动日可售天数=" + salDaysForAct + " 季末-预计活动日=" + autumnWinterProToActStartDateBetweenDays);
                continue;
            }
            int holidayAndActStartDateBetweenDays = ActUtil.holidayProToActStartDateBetweenDays(festivalStartAndEnd.get(aos_festivalseting), start);
            if (salDaysForAct < holidayAndActStartDateBetweenDays) {
                MktComUtil.putSyncLog(aos_sync_logS, aos_sku + "节日品: 预计活动日可售天数=" + salDaysForAct + " 季末-预计活动日=" + holidayAndActStartDateBetweenDays);
                continue;
            }


            String aos_itemname = obj.getString("aos_itemname");
            String aos_category1 = obj.getString("aos_category1");
            String aos_category2 = obj.getString("aos_category2");
            // 平台仓库数量
            BigDecimal aos_platfqty = obj.getBigDecimal("aos_platfqty");
            BigDecimal aos_platavgqty = obj.getBigDecimal("aos_platavgqty");
            // 最大库龄
            BigDecimal aos_itemmaxage = obj.getBigDecimal("aos_itemmaxage");
            String aos_brand = itemObj.getString("aos_brand");
            // 海外在库数量
            int aos_overseaqty = obj.getInt("aos_overseaqty");
            BigDecimal aos_itemcost = itemNewCostMap.getOrDefault(aos_itemid, BigDecimal.ZERO);
            BigDecimal aos_invcostamt = BigDecimal.valueOf(aos_overseaqty).multiply(aos_itemcost);
            // 物料成本
            String aos_typedetail = obj.getString("aos_typedetail");
//            int aos_lowestqty = orgActivityQty.getInt("aos_lowestqty");
//            BigDecimal aos_inventoryratio = orgActivityQty.getBigDecimal("aos_inventoryratio");

//            int aos_qty = Math.max(aos_lowestqty, BigDecimal.valueOf(aos_overseaqty).multiply(aos_inventoryratio).setScale(0, BigDecimal.ROUND_HALF_UP).intValue());
            int aos_qty = 50;
            int nonPlatQty = InStockAvailableDays.getNonPlatQty(aos_orgid, aos_itemid);
            // 线上7天日均销量 R
            float R = InStockAvailableDays.getOrgItemOnlineAvgQty(aos_orgid, aos_itemid);
            BigDecimal aos_preactqty = BigDecimal.valueOf(nonPlatQty).subtract(BigDecimal.valueOf(R).multiply(BigDecimal.valueOf(currentToAct)));

            // 活动价=Min(当前价格*(1-折扣力度),官网现价*85%,eBay当前售价*0.85)
            BigDecimal actPrice;
            if (aos_disstrength != null && aos_disstrength.compareTo(BigDecimal.ZERO) != 0) {
                actPrice = aos_currentprice.multiply(BigDecimal.ONE.subtract(aos_disstrength));
            } else {
                actPrice = aos_currentprice.subtract(aos_disamt);
            }

            JSONObject itemObject = new JSONObject();
            itemObject.put("aos_itemnum", aos_itemid);
            itemObject.put("aos_itemname", aos_itemname);
            itemObject.put("aos_category_stat1", aos_category1);
            itemObject.put("aos_category_stat2", aos_category2);
            itemObject.put("aos_postid", asin);
            itemObject.put("aos_l_startdate", start);
            itemObject.put("aos_enddate", end);
            itemObject.put("aos_actqty", aos_qty);
            itemObject.put("aos_l_actstatus", "A");
            itemObject.put("aos_price", aos_currentprice);
            itemObject.put("aos_actprice", actPrice);
            itemObject.put("aos_currentqty", aos_overseaqty);// 当前库存
            itemObject.put("aos_preactqty", aos_preactqty);// 预计活动日库存 非平台仓数量-R*(预计活动日-今天)
            itemObject.put("aos_preactavadays", salDaysForAct);// 预计活动日可售天数
            itemObject.put("aos_platqty", aos_platfqty);// 平台仓当前库存数
            itemObject.put("aos_age", aos_itemmaxage);// 最大库龄
            itemObject.put("aos_7orgavgqty", R);// 过去7天国别日均销量
            itemObject.put("aos_7platavgqty", aos_platavgqty);// 过去7天平台日均销量
            itemObject.put("aos_discountdegree", aos_disstrength);// 折扣力度
            itemObject.put("aos_discountprice", aos_disamt);// 折扣金额
            itemObject.put("aos_typedetail", aos_typedetail);// 折扣金额
            itemObject.put("aos_seasonattr", aos_seasonattr);// 季节属性
            itemObject.put("aos_brand", aos_brand);// 品牌
            itemObject.put("aos_invcostamt", aos_invcostamt);// 库存金额
//            itemObject.put("aos_stars", aos_stars);// review分数
//            itemObject.put("aos_reviewqty", aos_review);// review个数
            secFilterList.add(itemObject);
        }

        DynamicObjectCollection aos_sal_actplanentity = object.getDynamicObjectCollection("aos_sal_actplanentity");
        aos_sal_actplanentity.clear();

        // 生成活动选品清单
        for (JSONObject obj : secFilterList) {
            DynamicObject lineObj = aos_sal_actplanentity.addNew();
            for (String key : obj.keySet()) {
                lineObj.set(key, obj.get(key));
            }
        }

        SaveServiceHelper.save(new DynamicObject[] {object});
        DynamicObject dynamicObject = BusinessDataServiceHelper.loadSingle(object.getPkValue(), "aos_act_select_plan");
        ActUtil.setProfitRate(dynamicObject);

        // 计算活动毛利率
        Map<String, BigDecimal> orgLowestProfitRate = ActUtil.getOrgLowestProfitRate(aos_orgid);
        ActUtil.setLowestProfitRate(object.getPkValue(), orgLowestProfitRate);

        // 保存日志表
        SaveServiceHelper.save(new DynamicObject[]{aos_sync_log});
        ActUtil.SaveItme(entityId,list_noPriceItem);

        Map<String, List<DynamicObject>> resultMap;
        DynamicObject afterProfitRateObject = BusinessDataServiceHelper.loadSingle(object.getPkValue(),"aos_act_select_plan");
        DynamicObjectCollection objectArr = afterProfitRateObject.getDynamicObjectCollection("aos_sal_actplanentity");
        if (("ES-PC COMPONENTES Y MULTIMEDIA SLU".equals(aos_shopnum) || "ES-Fnac.es".equals(aos_shopnum)) && "Weekly deal".equals(aos_acttypenum)) {
            // 按销售组别分组
            Map<String, List<DynamicObject>> groupMap = groupBy(aos_orgid, itemFilterList, objectArr);
            // 计算销售组比例
            Map<String, Float> rateMap = ActUtil.calGroupItemQtyRate(groupMap);
            // 按活动增量倒序
            Map<String, List<DynamicObject>> groupActIncrementMap = ActUtil.orderByActivityIncrementDesc(groupMap);
            // 按库存金额倒序
            Map<String, List<DynamicObject>> groupInvCostAmtMap = ActUtil.orderByInvCostAmtDesc(groupMap);

            int totalQty = 20;
            float incrementRate = 0.5f;
            resultMap = ActUtil.fillResult(groupMap, groupActIncrementMap, groupInvCostAmtMap, rateMap, totalQty, incrementRate);
        } else if ("ES-Tienda Animal".equals(aos_shopnum) && "Theme Event".equals(aos_acttypenum)) {
            // 按销售组别分组
            Map<String, List<DynamicObject>> groupMap = groupBy(aos_orgid, itemFilterList, objectArr);
            // 计算销售组比例
            Map<String, Float> rateMap = ActUtil.calGroupItemQtyRate(groupMap);
            // 按活动增量倒序
            Map<String, List<DynamicObject>> groupActIncrementMap = ActUtil.orderByActivityIncrementDesc(groupMap);
            // 按库存金额倒序
            Map<String, List<DynamicObject>> groupInvCostAmtMap = ActUtil.orderByInvCostAmtDesc(groupMap);

            int totalQty = 40;
            float incrementRate = 0.5f;
            resultMap = ActUtil.fillResult(groupMap, groupActIncrementMap, groupInvCostAmtMap, rateMap, totalQty, incrementRate);
        } else if ("ES-Materiales de fabrica".equals(aos_shopnum) && "Theme Event".equals(aos_acttypenum)) {
            // 按销售组别分组
            Map<String, List<DynamicObject>> groupMap = groupBy(aos_orgid, itemFilterList, objectArr);
            // 计算销售组比例
            Map<String, Float> rateMap = ActUtil.calGroupItemQtyRate(groupMap);
            // 按活动增量倒序
            Map<String, List<DynamicObject>> groupActIncrementMap = ActUtil.orderByActivityIncrementDesc(groupMap);
            // 按库存金额倒序
            Map<String, List<DynamicObject>> groupInvCostAmtMap = ActUtil.orderByInvCostAmtDesc(groupMap);

            int totalQty = 20;
            float incrementRate = 0.5f;
            resultMap = ActUtil.fillResult(groupMap, groupActIncrementMap, groupInvCostAmtMap, rateMap, totalQty, incrementRate);
        }  else if ("ES-FNAC Portugal".equals(aos_shopnum) && "Theme Event".equals(aos_acttypenum)) {
            // 按销售组别分组
            Map<String, List<DynamicObject>> groupMap = groupBy(aos_orgid, itemFilterList, objectArr);
            // 计算销售组比例
            Map<String, Float> rateMap = ActUtil.calGroupItemQtyRate(groupMap);
            // 按活动增量倒序
            Map<String, List<DynamicObject>> groupActIncrementMap = ActUtil.orderByActivityIncrementDesc(groupMap);
            // 按库存金额倒序
            Map<String, List<DynamicObject>> groupInvCostAmtMap = ActUtil.orderByInvCostAmtDesc(groupMap);

            int totalQty = 50;
            float incrementRate = 0.5f;
            resultMap = ActUtil.fillResult(groupMap, groupActIncrementMap, groupInvCostAmtMap, rateMap, totalQty, incrementRate);
        } else {
            return;
        }

        List<DynamicObject> resultList = new ArrayList<>();
        for (String key : resultMap.keySet()) {
            resultList.addAll(resultMap.get(key));
        }
        objectArr.clear();
        objectArr.addAll(resultList);
        SaveServiceHelper.save(new DynamicObject[] {afterProfitRateObject});
    }

    // 按销售组别比列分SKU
    private Map<String, List<DynamicObject>> groupBy(String aos_orgid,
                                                     List<String> itemFilterList,
                                                     DynamicObjectCollection objectArr) {
        // 物料及其小类
        Map<String, String> itemCtgMap = ActUtil.queryItemCtg(itemFilterList);
        // 小类对应组别
        Map<String, String> ctgAndGroupMap = ActUtil.queryCtgAndGroup(aos_orgid);

        Map<String, List<DynamicObject>> groupMap = new HashMap<>();
        // 分组
        for (DynamicObject object1 : objectArr) {
            DynamicObject aos_itemnum = object1.getDynamicObject("aos_itemnum");
            String aos_itemid = aos_itemnum.getString("id");

            // 小类
            String aos_category1 = itemCtgMap.get(aos_itemid);

            // 获取组别
            String aos_groupid = ctgAndGroupMap.get(aos_category1);
            if (aos_groupid == null) continue;

            List<DynamicObject> groupList = groupMap.computeIfAbsent(aos_groupid, k -> new ArrayList<>());
            groupList.add(object1);
        }
        return groupMap;
    }
}
