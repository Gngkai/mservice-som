package mkt.act.rule.uk;

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
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * lch 2022-06-28
 */
public class EbayAndDailyDeal implements ActStrategy {
    public final static String SELLER = "Seller Offer";

    @Override
    public void doOperation(DynamicObject object) throws Exception {
        // 国别id
        String aosOrgid = object.getDynamicObject("aos_nationality").getString("id");
        String ouCode = object.getDynamicObject("aos_nationality").getString("number");
        Date start = object.getDate("aos_startdate");
        Date end = object.getDate("aos_enddate1");
        // 折扣信息
        BigDecimal aosDisamt = object.getBigDecimal("aos_disamt");
        BigDecimal aosDisstrength = object.getBigDecimal("aos_disstrength");
        String aosChannelid = object.getDynamicObject("aos_channel").getString("id");
        String aosShopid = object.getDynamicObject("aos_shop").getString("id");
        String aosActtypenum = object.getDynamicObject("aos_acttype").getString("number");
        // 至活动日间隔天数
        Calendar instance = Calendar.getInstance();
        instance.set(Calendar.HOUR_OF_DAY, 0);
        instance.set(Calendar.MINUTE, 0);
        instance.set(Calendar.SECOND, 0);
        instance.set(Calendar.MILLISECOND, 0);
        // 当前日期
        long current = instance.getTime().getTime();
        instance.setTime(start);
        long actTime = instance.getTime().getTime();
        // 间隔天数
        long currentToAct = ActUtil.betweenDays(current, actTime);
        // 自有仓库可用量大于等于30的物料
        Map<String, DynamicObject> nonPlatItemSet = ActUtil.queryNonPlatQtyLgN(aosOrgid, 30);
        // 第一次过滤选品清单
        List<DynamicObject> firstFilterList = new ArrayList<>();
        // 物料list
        List<String> itemFilterList = new ArrayList<>();
        Calendar date = Calendar.getInstance();
        // 当前年
        int year = date.get(Calendar.YEAR);
        // 当前月
        int month = date.get(Calendar.MONTH) + 1;
        // 当前日
        int day = date.get(Calendar.DAY_OF_MONTH);
        FndLog log = FndLog.init("EbayAndDailyDeal", ouCode + year + month + day);
        DynamicObjectCollection selectList = ActUtil.queryActSelectList(ouCode);
        for (DynamicObject obj : selectList) {
            String aosSku = obj.getString("aos_sku");
            String aosSeasonattr = obj.getString("aos_seasonattr");
            // 剔除过季品
            if (ActUtil.isOutSeason(start, aosSeasonattr)) {
                log.add(aosSku + " 季节属性:" + aosSeasonattr + " 过季");
                continue;
            }
            firstFilterList.add(obj);
            itemFilterList.add(aosSku);
        }
        Map<String, DynamicObject> priceMap =
            ActUtil.getAsinAndPriceFromPrice(aosOrgid, aosShopid, aosChannelid, itemFilterList);
        // 第二次过滤
        // 查询节日销售时间
        Map<String, String[]> festivalStartAndEnd = ActUtil.queryFestivalStartAndEnd(aosOrgid);
        DynamicObject orgActivityQty = ActUtil.getOrgActivityQty(aosOrgid);
        // 选择满足库存条件的sku
        // 店铺价格
        List<String> listNoPriceItem = new ArrayList<>(itemFilterList.size());
        String entityId = object.getString("id");
        ActShopPriceDao actShopPriceDao = new ActShopPriceImpl();
        Map<String, BigDecimal> mapShopPrice =
            actShopPriceDao.QueryOrgChannelShopItemNumberPrice(aosOrgid, aosChannelid, aosShopid, itemFilterList);
        Map<String, DynamicObject> itemInfo = ActUtil.getItemInfo(itemFilterList, aosOrgid);
        List<JSONObject> secFilterList = new ArrayList<>();
        for (DynamicObject obj : firstFilterList) {
            String aosSku = obj.getString("aos_sku");
            String aosSeasonattr = obj.getString("aos_seasonattr");
            DynamicObject itemObj = itemInfo.get(aosSku);
            if (itemObj == null) {
                log.add(aosSku + " 未获取到物料信息");
                continue;
            }
            String aosItemid = itemObj.getString("aos_itemid");
            String aosFestivalseting = itemObj.getString("aos_festivalseting");
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

            // 选择自有仓库库存>= 30
            if (!nonPlatItemSet.containsKey(aosSku)) {
                log.add(aosSku + " 自有仓库库存小于30");
                continue;
            }

            // 预计活动日可售天数
            int salDaysForAct = InStockAvailableDays.calInstockSalDaysForAct(aosOrgid, aosItemid, start);
            // 常规品: 预计活动日可售天数>= 90
            if ("REGULAR".equals(aosSeasonattr) || "SPRING-SUMMER-CONVENTIONAL".equals(aosSeasonattr)) {
                if (salDaysForAct < 90) {
                    log.add(aosSku + " 预计可售天数:" + salDaysForAct + " 常规品: 预计活动日可售天数 <90 ");
                    continue;
                }
            }

            // 春夏品
            Calendar seasonEnd = Calendar.getInstance();
            if ("SPRING".equals(aosSeasonattr) || "SPRING_SUMMER".equals(aosSeasonattr)
                || "SUMMER".equals(aosSeasonattr)) {
                // 8月31
                seasonEnd.set(Calendar.MONTH, 7);
                seasonEnd.set(Calendar.DAY_OF_MONTH, 31);
                long betweenDays = ActUtil.betweenDays(seasonEnd.getTime().getTime(), start.getTime());
                if (salDaysForAct < betweenDays) {
                    log.add(aosSku + "春夏品:季末-预计活动日=" + betweenDays + " 预计活动日可售天数:" + salDaysForAct);
                    continue;
                }
            }

            if ("AUTUMN_WINTER".equals(aosSeasonattr) || "WINTER".equals(aosSeasonattr)) {
                // 3月31
                seasonEnd.set(Calendar.MONTH, 2);
                seasonEnd.set(Calendar.DAY_OF_MONTH, 31);
                if (start.getTime() > seasonEnd.getTime().getTime()) {
                    seasonEnd.add(Calendar.YEAR, +1);
                }
                long betweenDays = ActUtil.betweenDays(seasonEnd.getTime().getTime(), start.getTime());
                if (salDaysForAct < betweenDays) {
                    log.add(aosSku + "秋冬品:季末-预计活动日=" + betweenDays + " 预计活动日可售天数:" + salDaysForAct);
                    continue;
                }
            }

            // 选品池中季节/节日品,预计活动日可售天数≥(季末-预计活动日)
            // 节日品
            Calendar endDate = Calendar.getInstance();
            Calendar startDate = Calendar.getInstance();
            String[] dateArr = festivalStartAndEnd.get(aosFestivalseting);
            if (dateArr != null) {
                String startDateArr = dateArr[0];
                String[] startArr = startDateArr.split("-");
                startDate.set(Calendar.MONTH, Integer.parseInt(startArr[0]) - 1);
                startDate.set(Calendar.DAY_OF_MONTH, Integer.parseInt(startArr[1]));
                String endDateArr = dateArr[1];
                String[] endArr = endDateArr.split("-");
                endDate.set(Calendar.MONTH, Integer.parseInt(endArr[0]) - 1);
                endDate.set(Calendar.DAY_OF_MONTH, Integer.parseInt(endArr[1]));
                if (endDate.before(startDate)) {
                    endDate.add(Calendar.YEAR, +1);
                }
                Date festivalEnd = endDate.getTime();
                long betweenDays = ActUtil.betweenDays(festivalEnd.getTime(), start.getTime());
                if (salDaysForAct < betweenDays) {
                    log.add(aosSku + "节日品:节日末-预计活动日=" + betweenDays + " 预计活动日可售天数:" + salDaysForAct + "节日末 ： "
                        + festivalEnd);
                    continue;
                }
            }
            String aosItemname = obj.getString("aos_itemname");
            String aosCategory1 = obj.getString("aos_category1");
            String aosCategory2 = obj.getString("aos_category2");
            // seller offer：活动总数=200，且宠物、婴童不参加；
            if ("Seller Offer".equals(aosActtypenum)) {
                if ("宠物用品".equals(aosCategory1) || "婴儿产品&玩具及游戏".equals(aosCategory1)) {
                    continue;
                }
            }
            // 平台仓库数量
            BigDecimal aosPlatfqty = obj.getBigDecimal("aos_platfqty");
            BigDecimal aosPlatavgqty = obj.getBigDecimal("aos_platavgqty");
            // 最大库龄
            BigDecimal aosItemmaxage = obj.getBigDecimal("aos_itemmaxage");
            String aosTypedetail = obj.getString("aos_typedetail");
            // 海外在库数量
            int aosOverseaqty = obj.getInt("aos_overseaqty");
            // 活动数量
            int aosLowestqty = orgActivityQty.getInt("aos_lowestqty");
            BigDecimal aosInventoryratio = orgActivityQty.getBigDecimal("aos_inventoryratio");
            int aosQty = Math.max(aosLowestqty, BigDecimal.valueOf(aosOverseaqty).multiply(aosInventoryratio)
                .setScale(0, RoundingMode.HALF_UP).intValue());
            int nonPlatQty = InStockAvailableDays.getNonPlatQty(aosOrgid, aosItemid);
            // 线上7天日均销量 R
            float r = InStockAvailableDays.getOrgItemOnlineAvgQty(aosOrgid, aosItemid);
            BigDecimal aosPreactqty = BigDecimal.valueOf(nonPlatQty)
                .subtract(BigDecimal.valueOf(r).multiply(BigDecimal.valueOf(currentToAct)));
            // 活动价 = min(当年最低定价，当前价格*折扣力度,过去30天最低定价*0.95)
            BigDecimal actPrice;
            if ("Seller Offer".equals(aosActtypenum)) {
                actPrice = aosCurrentprice;
            } else {
                if (aosDisstrength != null && aosDisstrength.compareTo(BigDecimal.ZERO) != 0) {
                    actPrice = aosCurrentprice.multiply(BigDecimal.ONE.subtract(aosDisstrength));
                } else {
                    actPrice = aosCurrentprice.subtract(aosDisamt);
                }
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
            // 类型细分
            itemObject.put("aos_typedetail", aosTypedetail);
            // 季节属性
            itemObject.put("aos_seasonattr", aosSeasonattr);
            secFilterList.add(itemObject);
        }
        // 获取物料成本结存
        Map<String, BigDecimal> invCostMap = ActUtil.queryInvCost(aosOrgid, itemFilterList);
        // 获取海外库存数量
        Map<String, BigDecimal> overSeaInStockNumMap = ActUtil.queryOverSeaInStockNum(aosOrgid, itemFilterList);
        for (JSONObject obj : secFilterList) {
            String aosItemid = obj.getString("aos_itemnum");
            // 存货成本
            BigDecimal invCost = invCostMap.getOrDefault(aosItemid, BigDecimal.ZERO);
            // 海外库存数量
            BigDecimal overSeaQty = overSeaInStockNumMap.getOrDefault(aosItemid, BigDecimal.ZERO);
            // 库存金额
            BigDecimal inStockAmt = invCost.multiply(overSeaQty);
            obj.put("aos_invcostamt", inStockAmt);
        }
        DynamicObjectCollection aosSalActplanentity = object.getDynamicObjectCollection("aos_sal_actplanentity");
        aosSalActplanentity.clear();
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
        // 物料及其小类
        Map<String, String> itemCtgMap = ActUtil.queryItemCtg(itemFilterList);
        // 小类对应组别
        Map<String, String> ctgAndGroupMap = ActUtil.queryCtgAndGroup(aosOrgid);
        DynamicObject afterProfitRateObject =
            BusinessDataServiceHelper.loadSingle(object.getPkValue(), "aos_act_select_plan");
        DynamicObjectCollection aosSalActplanentity1 =
            afterProfitRateObject.getDynamicObjectCollection("aos_sal_actplanentity");
        Map<String, List<DynamicObject>> groupMap = new HashMap<>(16);
        int sum = 0;
        // 分组
        for (DynamicObject object1 : aosSalActplanentity1) {
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
            sum++;
        }

        // 按库存金额排序
        for (String key : groupMap.keySet()) {
            List<DynamicObject> groupList = groupMap.get(key);
            List<DynamicObject> collect = groupList.stream().sorted((obj1, obj2) -> {
                BigDecimal aosInvcostamt = obj1.getBigDecimal("aos_invcostamt");
                BigDecimal aosInvcostamt1 = obj2.getBigDecimal("aos_invcostamt");
                return aosInvcostamt1.compareTo(aosInvcostamt);
            }).collect(Collectors.toList());
            groupMap.put(key, collect);
        }

        List<List<DynamicObject>> resultList = new ArrayList<>();
        for (String key : groupMap.keySet()) {
            resultList.add(groupMap.get(key));
        }

        // 排序 将数量少的拍在前面 数量多的拍在后面
        List<List<DynamicObject>> collect =
            resultList.stream().sorted((list1, list2) -> list1.size() - list2.size()).collect(Collectors.toList());

        List<DynamicObject> thirdFilterList = new ArrayList<>();
        if (SELLER.equals(aosActtypenum)) {
            for (List<DynamicObject> list : collect) {
                int count = 0;
                for (DynamicObject obj : list) {
                    if (count < 200) {
                        thirdFilterList.add(obj);
                        count++;
                    }
                }
            }
        } else {
            // 大类比例
            int groupCount = 0;
            int count = 30;
            for (List<DynamicObject> list : collect) {
                ++groupCount;
                if (groupCount == collect.size()) {
                    for (DynamicObject obj : list) {
                        thirdFilterList.add(obj);
                        if (--count <= 0) {
                            break;
                        }
                    }
                } else {
                    int size = list.size();
                    // 比例
                    BigDecimal rate = BigDecimal.valueOf(size).divide(BigDecimal.valueOf(sum), 3, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(30)).setScale(0, RoundingMode.HALF_UP);
                    // 计数 如果达到组别比例就不再取值
                    int z = 0;
                    for (DynamicObject obj : list) {
                        if (BigDecimal.valueOf(z).compareTo(rate) >= 0) {
                            break;
                        }
                        thirdFilterList.add(obj);
                        z++;
                        count--;
                    }
                }
            }
        }
        aosSalActplanentity1.clear();
        aosSalActplanentity1.addAll(thirdFilterList);
        SaveServiceHelper.save(new DynamicObject[] {afterProfitRateObject});
        // 保存日志表
        log.finnalSave();
        ActUtil.SaveItme(entityId, listNoPriceItem);
    }
}
