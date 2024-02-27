package mkt.act.rule.es;

import com.alibaba.fastjson.JSONObject;
import common.fnd.FndLog;
import common.sal.util.InStockAvailableDays;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import mkt.act.dao.ActShopPriceDao;
import mkt.act.dao.impl.ActShopPriceImpl;
import mkt.act.rule.ActStrategy;
import mkt.act.rule.ActUtil;

import java.math.BigDecimal;
import java.util.*;

public class AliexpressES implements ActStrategy {
    @Override
    public void doOperation(DynamicObject object) throws Exception {
        String aosOrgnum = object.getDynamicObject("aos_nationality").getString("number");
        Date start = object.getDate("aos_startdate");
        Date end = object.getDate("aos_enddate1");
        // 国别id
        String aosOrgid = object.getDynamicObject("aos_nationality").getString("id");
        String aosChannelid = object.getDynamicObject("aos_channel").getString("id");
        String aosShopid = object.getDynamicObject("aos_shop").getString("id");
        String aosShopnum = object.getDynamicObject("aos_shop").getString("number");
        String aosActtypenum = object.getDynamicObject("aos_acttype").getString("number");
        FndLog log = FndLog.init("AliexpressES", aosOrgnum);
        // 折扣信息
        BigDecimal aosDisamt = object.getBigDecimal("aos_disamt");
        BigDecimal aosDisstrength = object.getBigDecimal("aos_disstrength");
        // 至活动日间隔天数
        int currentToAct = ActUtil.currentToActivityDateBetweenDays(start);
        Set<String> apartFromAmzItem = ActUtil.queryApartFromAmzAndEbayItem(aosOrgid, new String[] {"AMAZON"}, start);
        // 剔除同国别同期活动
        Set<String> samePeriodActivitiesSet = ActUtil.querySamePeriodActivities(aosOrgid, null, start, end);
        // 同店铺
        Set<String> daysBeforeAndAfterActItem =
            ActUtil.query30DaysBeforeAndAfterActItem(aosOrgid, null, new String[] {aosShopid}, start, 30);
        // 第一次过滤选品清单
        List<DynamicObject> firstFilterList = new ArrayList<>();
        // 物料list
        List<String> itemFilterList = new ArrayList<>();
        DynamicObjectCollection selectList = ActUtil.queryActSelectList(aosOrgnum);
        for (DynamicObject obj : selectList) {
            String aosSku = obj.getString("aos_sku");
            String aosSeasonattr = obj.getString("aos_seasonattr");
            // 剔除过季品
            if (ActUtil.isOutSeason(start, aosSeasonattr)) {
                log.add(aosSku + " 季节属性:" + aosSeasonattr + " 过季");
                continue;
            }

            if (apartFromAmzItem.contains(aosSku)) {
                log.add(aosSku + "剔除除亚马逊外活动日过去30天已提报平台活动个数＞3的sku");
                continue;
            }

            if (samePeriodActivitiesSet.contains(aosSku)) {
                log.add(aosSku + "剔除除剔除同国别同期活动");
                continue;
            }

            if (daysBeforeAndAfterActItem.contains(aosSku)) {
                log.add(aosSku + "剔除同店铺前后30天已提报活动的SKU");
                continue;
            }
            firstFilterList.add(obj);
            itemFilterList.add(aosSku);
        }
        Map<String, Integer> nonPlatItemSet = ActUtil.queryNonPlatQty(aosOrgid);
        Map<String, DynamicObject> priceMap =
            ActUtil.getAsinAndPriceFromPrice(aosOrgid, aosShopid, aosChannelid, itemFilterList);
        Map<String, DynamicObject> itemInfo = ActUtil.getItemInfo(itemFilterList, aosOrgid);
        Map<String, BigDecimal> itemNewCostMap = ActUtil.queryItemNewCost(aosOrgid);
        // 查询节日销售时间
        Map<String, String[]> festivalStartAndEnd = ActUtil.queryFestivalStartAndEnd(aosOrgid);
        // 店铺价格
        List<String> listNoPriceItem = new ArrayList<>(itemFilterList.size());
        String entityId = object.getString("id");
        ActShopPriceDao actShopPriceDao = new ActShopPriceImpl();
        Map<String, BigDecimal> mapShopPrice =
            actShopPriceDao.QueryOrgChannelShopItemNumberPrice(aosOrgid, aosChannelid, aosShopid, itemFilterList);
        // 第二次过滤
        List<JSONObject> secFilterList = new ArrayList<>();
        for (DynamicObject obj : firstFilterList) {
            String aosSku = obj.getString("aos_sku");
            String aosSeasonattr = obj.getString("aos_seasonattr");
            DynamicObject itemObj = itemInfo.get(aosSku);
            if (itemObj == null) {
                log.add(aosSku + "取不到物料信息");
                continue;
            }
            String aosItemid = itemObj.getString("aos_itemid");
            // ASIN
            DynamicObject priceObj = priceMap.get(aosSku);
            String asin = "";
            BigDecimal aosCurrentprice;
            if (priceObj == null) {
                // 每日价格不存在
                if (mapShopPrice.containsKey(aosItemid)) {
                    // 店铺价格存在
                    aosCurrentprice = mapShopPrice.get(aosItemid);
                } else {
                    log.add(aosSku + " 未获取到价格");
                    listNoPriceItem.add(aosSku);
                    continue;
                }
            } else {
                // ASIN码
                asin = priceObj.getString("aos_asin");
                // 当前价格
                aosCurrentprice = priceObj.getBigDecimal("aos_currentprice");
            }
            String aosFestivalseting = itemObj.getString("aos_festivalseting");
            // 自有仓库库存数量
            Integer ownWarehouseQty = nonPlatItemSet.getOrDefault(aosSku, 0);
            if (ownWarehouseQty <= 50) {
                log.add(aosSku + "自有仓库<=50 自有仓库库存数量:" + ownWarehouseQty);
                continue;
            }

            // 预计活动日可售天数
            int salDaysForAct = InStockAvailableDays.calInstockSalDaysForAct(aosOrgid, aosItemid, start);
            // 常规品: 预计活动日可售天数>= 90
            if ("REGULAR".equals(aosSeasonattr) || "SPRING-SUMMER-CONVENTIONAL".equals(aosSeasonattr)) {
                if (salDaysForAct < 90) {
                    log.add(aosSku + "常规品: 预计活动日可售天数< 90");
                    continue;
                }
            }

            int springSummerProToActStartDateBetweenDays =
                ActUtil.springSummerProToActStartDateBetweenDays(aosSeasonattr, start);
            if (salDaysForAct < springSummerProToActStartDateBetweenDays) {
                log.add(aosSku + "春夏品: 预计活动日可售天数=" + salDaysForAct + " 季末-预计活动日="
                    + springSummerProToActStartDateBetweenDays);
                continue;
            }
            int autumnWinterProToActStartDateBetweenDays =
                ActUtil.autumnWinterProToActStartDateBetweenDays(aosSeasonattr, start);
            if (salDaysForAct < autumnWinterProToActStartDateBetweenDays) {
                log.add(aosSku + "春夏品: 预计活动日可售天数=" + salDaysForAct + " 季末-预计活动日="
                    + autumnWinterProToActStartDateBetweenDays);
                continue;
            }
            int holidayAndActStartDateBetweenDays =
                ActUtil.holidayProToActStartDateBetweenDays(festivalStartAndEnd.get(aosFestivalseting), start);
            if (salDaysForAct < holidayAndActStartDateBetweenDays) {
                log.add(aosSku + "节日品: 预计活动日可售天数=" + salDaysForAct + " 季末-预计活动日=" + holidayAndActStartDateBetweenDays);
                continue;
            }

            String aosItemname = obj.getString("aos_itemname");
            String aosCategory1 = obj.getString("aos_category1");
            String aosCategory2 = obj.getString("aos_category2");
            // 平台仓库数量
            BigDecimal aosPlatfqty = obj.getBigDecimal("aos_platfqty");
            BigDecimal aosPlatavgqty = obj.getBigDecimal("aos_platavgqty");
            // 最大库龄
            BigDecimal aosItemmaxage = obj.getBigDecimal("aos_itemmaxage");
            String aosBrand = itemObj.getString("aos_brand");
            // 海外在库数量
            int aosOverseaqty = obj.getInt("aos_overseaqty");
            BigDecimal aosItemcost = itemNewCostMap.getOrDefault(aosItemid, BigDecimal.ZERO);
            BigDecimal aosInvcostamt = BigDecimal.valueOf(aosOverseaqty).multiply(aosItemcost);
            // 物料成本
            String aosTypedetail = obj.getString("aos_typedetail");
            int aosQty = 50;
            int nonPlatQty = InStockAvailableDays.getNonPlatQty(aosOrgid, aosItemid);
            // 线上7天日均销量 R
            float r = InStockAvailableDays.getOrgItemOnlineAvgQty(aosOrgid, aosItemid);
            BigDecimal aosPreactqty = BigDecimal.valueOf(nonPlatQty)
                .subtract(BigDecimal.valueOf(r).multiply(BigDecimal.valueOf(currentToAct)));
            // 活动价=Min(当前价格*(1-折扣力度),官网现价*85%,eBay当前售价*0.85)
            BigDecimal actPrice;
            if (aosDisstrength != null && aosDisstrength.compareTo(BigDecimal.ZERO) != 0) {
                actPrice = aosCurrentprice.multiply(BigDecimal.ONE.subtract(aosDisstrength));
            } else {
                actPrice = aosCurrentprice.subtract(aosDisamt);
            }

            JSONObject itemObject = new JSONObject();
            itemObject.put("aos_itemnum", aosItemid);
            itemObject.put("aos_itemname", aosItemname);
            itemObject.put("aos_category_stat1", aosCategory1);
            itemObject.put("aos_category_stat2", aosCategory2);
            itemObject.put("aos_postid", asin);
            itemObject.put("aos_l_startdate", start);
            itemObject.put("aos_enddate", end);
            itemObject.put("aos_actqty", aosQty);
            itemObject.put("aos_l_actstatus", "A");
            itemObject.put("aos_price", aosCurrentprice);
            itemObject.put("aos_actprice", actPrice);
            // 当前库存
            itemObject.put("aos_currentqty", aosOverseaqty);
            // 预计活动日库存 非平台仓数量-R*(预计活动日-今天)
            itemObject.put("aos_preactqty", aosPreactqty);
            // 预计活动日可售天数
            itemObject.put("aos_preactavadays", salDaysForAct);
            // 平台仓当前库存数
            itemObject.put("aos_platqty", aosPlatfqty);
            // 最大库龄
            itemObject.put("aos_age", aosItemmaxage);
            // 过去7天国别日均销量
            itemObject.put("aos_7orgavgqty", r);
            // 过去7天平台日均销量
            itemObject.put("aos_7platavgqty", aosPlatavgqty);
            // 折扣力度
            itemObject.put("aos_discountdegree", aosDisstrength);
            // 折扣金额
            itemObject.put("aos_discountprice", aosDisamt);
            // 折扣金额
            itemObject.put("aos_typedetail", aosTypedetail);
            // 季节属性
            itemObject.put("aos_seasonattr", aosSeasonattr);
            // 品牌
            itemObject.put("aos_brand", aosBrand);
            // 库存金额
            itemObject.put("aos_invcostamt", aosInvcostamt);
            secFilterList.add(itemObject);
        }

        DynamicObjectCollection aosSalActplanentity = object.getDynamicObjectCollection("aos_sal_actplanentity");
        aosSalActplanentity.clear();
        // 生成活动选品清单
        for (JSONObject obj : secFilterList) {
            DynamicObject lineObj = aosSalActplanentity.addNew();
            for (String key : obj.keySet()) {
                lineObj.set(key, obj.get(key));
            }
        }

        SaveServiceHelper.save(new DynamicObject[] {object});
        DynamicObject dynamicObject = BusinessDataServiceHelper.loadSingle(object.getPkValue(), "aos_act_select_plan");
        ActUtil.setProfitRate(dynamicObject);

        // 计算活动毛利率
        Map<String, BigDecimal> orgLowestProfitRate = ActUtil.getOrgLowestProfitRate(aosOrgid);
        ActUtil.setLowestProfitRate(object.getPkValue(), orgLowestProfitRate);

        // 保存日志表
        log.finnalSave();
        ActUtil.SaveItme(entityId, listNoPriceItem);

        Map<String, List<DynamicObject>> resultMap;
        DynamicObject afterProfitRateObject =
            BusinessDataServiceHelper.loadSingle(object.getPkValue(), "aos_act_select_plan");
        DynamicObjectCollection objectArr = afterProfitRateObject.getDynamicObjectCollection("aos_sal_actplanentity");
        boolean cond = (("ES-PC COMPONENTES Y MULTIMEDIA SLU".equals(aosShopnum) || "ES-Fnac.es".equals(aosShopnum))
            && "Weekly deal".equals(aosActtypenum));
        boolean cond2 = ("ES-Tienda Animal".equals(aosShopnum) && "Theme Event".equals(aosActtypenum));
        boolean cond3 = ("ES-Materiales de fabrica".equals(aosShopnum) && "Theme Event".equals(aosActtypenum));
        boolean cond4 = ("ES-FNAC Portugal".equals(aosShopnum) && "Theme Event".equals(aosActtypenum));
        if (cond) {
            // 按销售组别分组
            Map<String, List<DynamicObject>> groupMap = groupBy(aosOrgid, itemFilterList, objectArr);
            // 计算销售组比例
            Map<String, Float> rateMap = ActUtil.calGroupItemQtyRate(groupMap);
            // 按活动增量倒序
            Map<String, List<DynamicObject>> groupActIncrementMap = ActUtil.orderByActivityIncrementDesc(groupMap);
            // 按库存金额倒序
            Map<String, List<DynamicObject>> groupInvCostAmtMap = ActUtil.orderByInvCostAmtDesc(groupMap);

            int totalQty = 20;
            float incrementRate = 0.5f;
            resultMap = ActUtil.fillResult(groupMap, groupActIncrementMap, groupInvCostAmtMap, rateMap, totalQty,
                incrementRate);
        } else if (cond2) {
            // 按销售组别分组
            Map<String, List<DynamicObject>> groupMap = groupBy(aosOrgid, itemFilterList, objectArr);
            // 计算销售组比例
            Map<String, Float> rateMap = ActUtil.calGroupItemQtyRate(groupMap);
            // 按活动增量倒序
            Map<String, List<DynamicObject>> groupActIncrementMap = ActUtil.orderByActivityIncrementDesc(groupMap);
            // 按库存金额倒序
            Map<String, List<DynamicObject>> groupInvCostAmtMap = ActUtil.orderByInvCostAmtDesc(groupMap);

            int totalQty = 40;
            float incrementRate = 0.5f;
            resultMap = ActUtil.fillResult(groupMap, groupActIncrementMap, groupInvCostAmtMap, rateMap, totalQty,
                incrementRate);
        } else if (cond3) {
            // 按销售组别分组
            Map<String, List<DynamicObject>> groupMap = groupBy(aosOrgid, itemFilterList, objectArr);
            // 计算销售组比例
            Map<String, Float> rateMap = ActUtil.calGroupItemQtyRate(groupMap);
            // 按活动增量倒序
            Map<String, List<DynamicObject>> groupActIncrementMap = ActUtil.orderByActivityIncrementDesc(groupMap);
            // 按库存金额倒序
            Map<String, List<DynamicObject>> groupInvCostAmtMap = ActUtil.orderByInvCostAmtDesc(groupMap);

            int totalQty = 20;
            float incrementRate = 0.5f;
            resultMap = ActUtil.fillResult(groupMap, groupActIncrementMap, groupInvCostAmtMap, rateMap, totalQty,
                incrementRate);
        } else if (cond4) {
            // 按销售组别分组
            Map<String, List<DynamicObject>> groupMap = groupBy(aosOrgid, itemFilterList, objectArr);
            // 计算销售组比例
            Map<String, Float> rateMap = ActUtil.calGroupItemQtyRate(groupMap);
            // 按活动增量倒序
            Map<String, List<DynamicObject>> groupActIncrementMap = ActUtil.orderByActivityIncrementDesc(groupMap);
            // 按库存金额倒序
            Map<String, List<DynamicObject>> groupInvCostAmtMap = ActUtil.orderByInvCostAmtDesc(groupMap);

            int totalQty = 50;
            float incrementRate = 0.5f;
            resultMap = ActUtil.fillResult(groupMap, groupActIncrementMap, groupInvCostAmtMap, rateMap, totalQty,
                incrementRate);
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

    /**
     * 按销售组别比列分SKU
     * 
     * @param aosOrgid 国别
     * @param itemFilterList 货号组
     * @param objectArr 活动
     * @return sku
     */
    private Map<String, List<DynamicObject>> groupBy(String aosOrgid, List<String> itemFilterList,
        DynamicObjectCollection objectArr) {
        // 物料及其小类
        Map<String, String> itemCtgMap = ActUtil.queryItemCtg(itemFilterList);
        // 小类对应组别
        Map<String, String> ctgAndGroupMap = ActUtil.queryCtgAndGroup(aosOrgid);

        Map<String, List<DynamicObject>> groupMap = new HashMap<>(16);
        // 分组
        for (DynamicObject object1 : objectArr) {
            DynamicObject aosItemnum = object1.getDynamicObject("aos_itemnum");
            String aosItemid = aosItemnum.getString("id");
            // 小类
            String aosCategory1 = itemCtgMap.get(aosItemid);
            // 获取组别
            String aosGroupid = ctgAndGroupMap.get(aosCategory1);
            if (aosGroupid == null) {
                continue;
            }
            List<DynamicObject> groupList = groupMap.computeIfAbsent(aosGroupid, k -> new ArrayList<>());
            groupList.add(object1);
        }
        return groupMap;
    }
}
