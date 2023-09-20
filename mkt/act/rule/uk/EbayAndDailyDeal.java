package mkt.act.rule.uk;

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
import mkt.common.MKTCom;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * lch
 * 2022-06-28
 */
public class EbayAndDailyDeal implements ActStrategy {

    @Override
    public void doOperation(DynamicObject object) throws Exception{
        // 国别id
        String aos_orgid = object.getDynamicObject("aos_nationality").getString("id");
        String ouCode = object.getDynamicObject("aos_nationality").getString("number");
        Date start = object.getDate("aos_startdate");
        Date end = object.getDate("aos_enddate1");

        // 折扣信息
        BigDecimal aos_disamt = object.getBigDecimal("aos_disamt");
        BigDecimal aos_disstrength = object.getBigDecimal("aos_disstrength");

        String aos_channelid = object.getDynamicObject("aos_channel").getString("id");
        String aos_shopid = object.getDynamicObject("aos_shop").getString("id");
        String aos_acttypenum = object.getDynamicObject("aos_acttype").getString("number");
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
        Map<String, DynamicObject> nonPlatItemSet = ActUtil.queryNonPlatQtyLgN(aos_orgid, 30);
        // 第一次过滤选品清单
        List<DynamicObject> firstFilterList = new ArrayList<>();
        // 物料list
        List<String> itemFilterList = new ArrayList<>();

        Calendar date = Calendar.getInstance();
        int year = date.get(Calendar.YEAR);// 当前年
        int month = date.get(Calendar.MONTH) + 1;// 当前月
        int day = date.get(Calendar.DAY_OF_MONTH);// 当前日
        DynamicObject aos_sync_log = BusinessDataServiceHelper.newDynamicObject("aos_sync_log");
        aos_sync_log.set("aos_type_code", "EbayAndDailyDeal");
        aos_sync_log.set("aos_groupid", ouCode + year + month + day);
        aos_sync_log.set("billstatus", "A");
        DynamicObjectCollection aos_sync_logS = aos_sync_log.getDynamicObjectCollection("aos_entryentity");

        DynamicObjectCollection selectList = ActUtil.queryActSelectList(ouCode);
        for (DynamicObject obj : selectList) {
            String aos_sku = obj.getString("aos_sku");
            String aos_seasonattr = obj.getString("aos_seasonattr");
            // 剔除过季品
            if (ActUtil.isOutSeason(start, aos_seasonattr)) {
                MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + " 季节属性:" + aos_seasonattr + " 过季");
                continue;
            }

            firstFilterList.add(obj);
            itemFilterList.add(aos_sku);
        }
        Map<String, DynamicObject> priceMap = ActUtil.getAsinAndPriceFromPrice(aos_orgid, aos_shopid, aos_channelid, itemFilterList);
        // 第二次过滤
        // 查询节日销售时间
        Map<String, String[]> festivalStartAndEnd = ActUtil.queryFestivalStartAndEnd(aos_orgid);

        DynamicObject orgActivityQty = ActUtil.getOrgActivityQty(aos_orgid);
        // 选择满足库存条件的sku

        //店铺价格
        List<String> list_noPriceItem = new ArrayList<>(itemFilterList.size());
        String entityId = object.getString("id");
        ActShopPriceDao actShopPriceDao = new ActShopPriceImpl();
        Map<String, BigDecimal> map_shopPrice = actShopPriceDao.QueryOrgChannelShopItemNumberPrice(aos_orgid, aos_channelid, aos_shopid, itemFilterList);

        Map<String, DynamicObject> itemInfo = ActUtil.getItemInfo(itemFilterList, aos_orgid);
        List<JSONObject> secFilterList = new ArrayList<>();
        for (DynamicObject obj : firstFilterList) {
            String aos_sku = obj.getString("aos_sku");
            String aos_seasonattr = obj.getString("aos_seasonattr");
            DynamicObject itemObj = itemInfo.get(aos_sku);
            if (itemObj == null) {
                MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + " 未获取到物料信息");
                continue;
            }
            String aos_itemid = itemObj.getString("aos_itemid");
            String aos_festivalseting = itemObj.getString("aos_festivalseting");
            // ASIN
            DynamicObject priceObj = priceMap.get(aos_sku);
            String asin = "";
            BigDecimal aos_currentprice;
            if (priceObj == null) {	//每日价格不存在
                if (map_shopPrice.containsKey(aos_itemid)){ //店铺价格存在
                    aos_currentprice = map_shopPrice.get(aos_itemid);
                }
                else {
                    MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + " 未获取到价格");
                    list_noPriceItem.add(aos_sku);
                    continue;
                }
            }
            else {
                asin = priceObj.getString("aos_asin");// ASIN码
                aos_currentprice = priceObj.getBigDecimal("aos_currentprice");// 当前价格
            }

            // 选择自有仓库库存>= 30
            if (!nonPlatItemSet.containsKey(aos_sku)) {
                MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + " 自有仓库库存小于30");
                continue;
            }

            // 预计活动日可售天数
            int salDaysForAct = InStockAvailableDays.calInstockSalDaysForAct(aos_orgid, aos_itemid, start);
            // 常规品: 预计活动日可售天数>= 90
            if ("REGULAR".equals(aos_seasonattr) || "SPRING-SUMMER-CONVENTIONAL".equals(aos_seasonattr)) {
                if (salDaysForAct < 90) {
                    MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + " 预计可售天数:" + salDaysForAct +" 常规品: 预计活动日可售天数 <90 ");
                    continue;
                }
            }

            // 春夏品
            Calendar seasonEnd = Calendar.getInstance();
            if ("SPRING".equals(aos_seasonattr) || "SPRING_SUMMER".equals(aos_seasonattr) || "SUMMER".equals(aos_seasonattr)) {
                // 8月31
                seasonEnd.set(Calendar.MONTH, 7);
                seasonEnd.set(Calendar.DAY_OF_MONTH, 31);

                long betweenDays = ActUtil.betweenDays(seasonEnd.getTime().getTime(), start.getTime());
                if (salDaysForAct < betweenDays) {
                    MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + "春夏品:季末-预计活动日=" + betweenDays + " 预计活动日可售天数:" + salDaysForAct);
                    continue;
                }
            }

            if ("AUTUMN_WINTER".equals(aos_seasonattr) || "WINTER".equals(aos_seasonattr)) {
                // 3月31
                seasonEnd.set(Calendar.MONTH, 2);
                seasonEnd.set(Calendar.DAY_OF_MONTH, 31);
                if (start.getTime() > seasonEnd.getTime().getTime()) {
                    seasonEnd.add(Calendar.YEAR, +1);
                }
                long betweenDays = ActUtil.betweenDays(seasonEnd.getTime().getTime(), start.getTime());
                if (salDaysForAct < betweenDays) {
                    MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + "秋冬品:季末-预计活动日=" + betweenDays + " 预计活动日可售天数:" + salDaysForAct);
                    continue;
                }
            }

            // 选品池中季节/节日品,预计活动日可售天数≥(季末-预计活动日)
            // 节日品
            Calendar endDate = Calendar.getInstance();
            Calendar startDate = Calendar.getInstance();
            String[] dateArr = festivalStartAndEnd.get(aos_festivalseting);
            if (dateArr != null) {
                String startDateArr = dateArr[0];
                String[] startArr = startDateArr.split("-");
                startDate.set(Calendar.MONTH, Integer.parseInt(startArr[0]) -1);
                startDate.set(Calendar.DAY_OF_MONTH, Integer.parseInt(startArr[1]));

                String endDateArr = dateArr[1];
                String[] endArr = endDateArr.split("-");
                endDate.set(Calendar.MONTH, Integer.parseInt(endArr[0]) -1);
                endDate.set(Calendar.DAY_OF_MONTH, Integer.parseInt(endArr[1]));

                if (endDate.before(startDate)) {
                    endDate.add(Calendar.YEAR, +1);
                }
                Date festivalEnd = endDate.getTime();

                long betweenDays = ActUtil.betweenDays(festivalEnd.getTime(), start.getTime());
                if (salDaysForAct < betweenDays) {
                    MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + "节日品:节日末-预计活动日=" + betweenDays + " 预计活动日可售天数:" + salDaysForAct + "节日末 ： " + festivalEnd);
                    continue;
                }
            }

            String aos_itemname = obj.getString("aos_itemname");
            String aos_category1 = obj.getString("aos_category1");
            String aos_category2 = obj.getString("aos_category2");

            //seller offer：活动总数=200，且宠物、婴童不参加；
            if ("Seller Offer".equals(aos_acttypenum)) {
                if ("宠物用品".equals(aos_category1) || "婴儿产品&玩具及游戏".equals(aos_category1)) continue;
            }
            // 平台仓库数量
            BigDecimal aos_platfqty = obj.getBigDecimal("aos_platfqty");
            BigDecimal aos_platavgqty = obj.getBigDecimal("aos_platavgqty");
            // 最大库龄
            BigDecimal aos_itemmaxage = obj.getBigDecimal("aos_itemmaxage");
            String aos_typedetail = obj.getString("aos_typedetail");
            // 海外在库数量
            int aos_overseaqty = obj.getInt("aos_overseaqty");
            // 活动数量
            int aos_lowestqty = orgActivityQty.getInt("aos_lowestqty");
            BigDecimal aos_inventoryratio = orgActivityQty.getBigDecimal("aos_inventoryratio");

            int aos_qty = Math.max(aos_lowestqty, BigDecimal.valueOf(aos_overseaqty).multiply(aos_inventoryratio).setScale(0, BigDecimal.ROUND_HALF_UP).intValue());
            int nonPlatQty = InStockAvailableDays.getNonPlatQty(aos_orgid, aos_itemid);
            // 线上7天日均销量 R
            float R = InStockAvailableDays.getOrgItemOnlineAvgQty(aos_orgid, aos_itemid);
            BigDecimal aos_preactqty = BigDecimal.valueOf(nonPlatQty).subtract(BigDecimal.valueOf(R).multiply(BigDecimal.valueOf(currentToAct)));

            // 活动价 = min(当年最低定价，当前价格*折扣力度,过去30天最低定价*0.95)
            BigDecimal actPrice;
            if ("Seller Offer".equals(aos_acttypenum)) {
                actPrice = aos_currentprice;
            } else {
                if (aos_disstrength != null && aos_disstrength.compareTo(BigDecimal.ZERO) != 0) {
                    actPrice = aos_currentprice.multiply(BigDecimal.ONE.subtract(aos_disstrength));
                } else {
                    actPrice = aos_currentprice.subtract(aos_disamt);
                }
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
            itemObject.put("aos_typedetail", aos_typedetail);// 类型细分
            itemObject.put("aos_seasonattr", aos_seasonattr);// 季节属性
//            itemObject.put("aos_invcostamt", "");// 库存金额
//            itemObject.put("aos_stars", aos_stars);// review分数
//            itemObject.put("aos_reviewqty", aos_review);// review个数
            secFilterList.add(itemObject);
        }

        // 获取物料成本结存
        Map<String, BigDecimal> invCostMap = ActUtil.queryInvCost(aos_orgid, itemFilterList);
        // 获取海外库存数量
        Map<String, BigDecimal> overSeaInStockNumMap = ActUtil.queryOverSeaInStockNum(aos_orgid, itemFilterList);
        for (JSONObject obj : secFilterList) {
            String aos_itemid = obj.getString("aos_itemnum");
            // 存货成本
            BigDecimal invCost = invCostMap.getOrDefault(aos_itemid, BigDecimal.ZERO);

            // 海外库存数量
            BigDecimal overSeaQty = overSeaInStockNumMap.getOrDefault(aos_itemid, BigDecimal.ZERO);

            // 库存金额
            BigDecimal inStockAmt = invCost.multiply(overSeaQty);
            obj.put("aos_invcostamt", inStockAmt);
        }

        DynamicObjectCollection aos_sal_actplanentity = object.getDynamicObjectCollection("aos_sal_actplanentity");
        aos_sal_actplanentity.clear();

        for (JSONObject obj : secFilterList) {
            DynamicObject lineObj = aos_sal_actplanentity.addNew();
            for (String key : obj.keySet()) {
                lineObj.set(key, obj.get(key));
            }
        }

        SaveServiceHelper.save(new DynamicObject[]{object});
        DynamicObject dynamicObject = BusinessDataServiceHelper.loadSingle(object.getPkValue(), "aos_act_select_plan");
        ActUtil.setProfitRate(dynamicObject);

        // 计算活动毛利率
        Map<String, BigDecimal> orgLowestProfitRate = ActUtil.getOrgLowestProfitRate(aos_orgid);
        ActUtil.setLowestProfitRate(object.getPkValue(), orgLowestProfitRate);


        // 物料及其小类
        Map<String, String> itemCtgMap = ActUtil.queryItemCtg(itemFilterList);
        // 小类对应组别
        Map<String, String> ctgAndGroupMap = ActUtil.queryCtgAndGroup(aos_orgid);
        DynamicObject afterProfitRateObject = BusinessDataServiceHelper.loadSingle(object.getPkValue(),"aos_act_select_plan");
        DynamicObjectCollection aos_sal_actplanentity1 = afterProfitRateObject.getDynamicObjectCollection("aos_sal_actplanentity");

        Map<String, List<DynamicObject>> groupMap = new HashMap<>();
        int sum = 0;
        // 分组
        for (DynamicObject object1 : aos_sal_actplanentity1) {
            DynamicObject aos_itemnum = object1.getDynamicObject("aos_itemnum");
            String aos_itemid = aos_itemnum.getString("id");

            // 小类
            String aos_category1 = itemCtgMap.get(aos_itemid);

            // 获取组别
            String aos_groupid = ctgAndGroupMap.get(aos_category1);
            if (aos_groupid == null) continue;

            List<DynamicObject> groupList = groupMap.computeIfAbsent(aos_groupid, k -> new ArrayList<>());
            groupList.add(object1);
            sum++;
        }

        // 按库存金额排序
        for (String key : groupMap.keySet()) {
            List<DynamicObject> groupList = groupMap.get(key);
            List<DynamicObject> collect = groupList.stream().sorted((obj1, obj2) -> {
                BigDecimal aos_invcostamt = obj1.getBigDecimal("aos_invcostamt");
                BigDecimal aos_invcostamt1 = obj2.getBigDecimal("aos_invcostamt");
                return aos_invcostamt1.compareTo(aos_invcostamt);
            }).collect(Collectors.toList());
            groupMap.put(key, collect);
        }

        List<List<DynamicObject>> resultList = new ArrayList<>();
        for (String key : groupMap.keySet()) {
            resultList.add(groupMap.get(key));
        }

        // 排序 将数量少的拍在前面 数量多的拍在后面
        List<List<DynamicObject>> collect = resultList.stream().sorted((list1, list2) -> list1.size() -list2.size()).collect(Collectors.toList());

        List<DynamicObject> thirdFilterList = new ArrayList<>();
        if ("Seller Offer".equals(aos_acttypenum)) {
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
            //大类比例
            int groupCount = 0;
            int count = 30;
            for (List<DynamicObject> list : collect) {
                ++groupCount;
                if (groupCount == collect.size()) {
                    for (DynamicObject obj : list) {
                        thirdFilterList.add(obj);
                        if (--count <= 0) break;
                    }
                } else {
                    int size = list.size();
                    // 比例
                    BigDecimal rate = BigDecimal.valueOf(size).divide(BigDecimal.valueOf(sum), 3, BigDecimal.ROUND_HALF_UP).multiply(BigDecimal.valueOf(30)).setScale(0, BigDecimal.ROUND_HALF_UP);
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

        aos_sal_actplanentity1.clear();

        aos_sal_actplanentity1.addAll(thirdFilterList);

        SaveServiceHelper.save(new DynamicObject[] {afterProfitRateObject});
        // 保存日志表
        SaveServiceHelper.save(new DynamicObject[]{aos_sync_log});
        ActUtil.SaveItme(entityId,list_noPriceItem);
    }
}
