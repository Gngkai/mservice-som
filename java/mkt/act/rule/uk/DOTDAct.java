package mkt.act.rule.uk;

import com.alibaba.fastjson.JSONObject;
import common.sal.util.InStockAvailableDays;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import mkt.act.dao.ActShopPriceDao;
import mkt.act.dao.impl.ActShopPriceImpl;
import mkt.act.rule.ActStrategy;
import mkt.act.rule.ActUtil;
import mkt.common.MKTCom;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class DOTDAct implements ActStrategy {
    @Override
    public void doOperation(DynamicObject originObj) throws Exception{
        String ouCode = originObj.getDynamicObject("aos_nationality").getString("number");
        Date start = originObj.getDate("aos_startdate");
        Date end = originObj.getDate("aos_enddate1");
        // 国别id
        String aos_orgid = originObj.getDynamicObject("aos_nationality").getString("id");
        String aos_channelid = originObj.getDynamicObject("aos_channel").getString("id");
        String aos_shopid = originObj.getDynamicObject("aos_shop").getString("id");

        // 折扣信息
        BigDecimal aos_disamt = originObj.getBigDecimal("aos_disamt");
        BigDecimal aos_disstrength = originObj.getBigDecimal("aos_disstrength");

        // 满足review条件的物料
        Map<String, DynamicObject> reviewItemSet = ActUtil.queryReview(aos_orgid);

        // 自有仓库可用量大于等于30的物料
        Map<String, DynamicObject> nonPlatItemSet = ActUtil.queryNonPlatQtyLgN(aos_orgid, 30);

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
        // 第一次过滤选品清单
        List<DynamicObject> firstFilterList = new ArrayList<>();
        // 物料list
        List<String> itemFilterList = new ArrayList<>();
        DynamicObjectCollection selectList = ActUtil.queryActSelectList(ouCode);

        for (DynamicObject obj : selectList) {
            String aos_sku = obj.getString("aos_sku");
            String aos_seasonattr = obj.getString("aos_seasonattr");
            // 当前国别Review分数>=4和Review个数>=5的物料
            DynamicObject reviewObject = reviewItemSet.get(aos_sku);
            if (reviewObject != null) {
                BigDecimal aos_review = reviewObject.getBigDecimal("aos_review");
                BigDecimal aos_stars = reviewObject.getBigDecimal("aos_stars");
                if (!(aos_review.compareTo(BigDecimal.valueOf(5)) >= 0 && aos_stars.compareTo(BigDecimal.valueOf(4)) >= 0)) {
                    continue;
                }
            } else {
                continue;
            }
            // 剔除过季品
            if (ActUtil.isOutSeason(start, aos_seasonattr)) continue;

            firstFilterList.add(obj);
            itemFilterList.add(aos_sku);
        }
        Map<String, DynamicObject> itemInfo = ActUtil.getItemInfo(itemFilterList, aos_orgid);

        // 查询节日销售时间
        Map<String, String[]> festivalStartAndEnd = ActUtil.queryFestivalStartAndEnd(aos_orgid);

        // 选择满足库存条件的sku
        // 剔除
        // ①预计活动日前后30天，活动类型为DOTD，且状态为正常的在线ID、
        Calendar actDate30 = Calendar.getInstance();
        actDate30.setTime(start);
        actDate30.add(Calendar.DAY_OF_MONTH, -30);
        // 30天前
        String before30 = DateFormatUtils.format(actDate30.getTime(), "yyyy-MM-dd");
        // 30天后
        actDate30.add(Calendar.DAY_OF_MONTH, 60);
        String after30 = DateFormatUtils.format(actDate30.getTime(), "yyyy-MM-dd");
        DynamicObjectCollection beforeAfter30List = QueryServiceHelper.query("aos_act_select_plan", "aos_sal_actplanentity.aos_itemnum aos_itemnum", new QFilter[]{
                new QFilter("aos_nationality", QCP.equals, aos_orgid),
                new QFilter("aos_actstatus", QCP.not_equals, "C"),// 活动类型为我DOTD
                new QFilter("aos_acttype.number", QCP.equals, "DOTD"),// 活动类型为我DOTD
                new QFilter("aos_sal_actplanentity.aos_l_actstatus", QCP.equals, "A"),
                new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.large_equals, before30).and(new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.less_equals, after30))//始日期<=制作日期<=结束日期
                        .or(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.large_equals, before30).and(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.less_equals, after30)))//开始日期 > 制作日期
                        .or(new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.large_equals, before30).and(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.less_equals, after30)))// 过去3天的活动
        });
        Set<String> beforeAfter30Set = beforeAfter30List.stream().map(obj -> obj.getString("aos_itemnum")).collect(Collectors.toSet());
        // ②预计活动日前后7天，活动类型为LD/7DD，且状态为正常的在线ID；

        Calendar actDate7 = Calendar.getInstance();
        actDate7.setTime(start);
        actDate7.add(Calendar.DAY_OF_MONTH, -7);
        // 30天前
        String before7 = DateFormatUtils.format(actDate7.getTime(), "yyyy-MM-dd");
        // 30天后
        actDate30.add(Calendar.DAY_OF_MONTH, 14);
        String after7 = DateFormatUtils.format(actDate7.getTime(), "yyyy-MM-dd");
        DynamicObjectCollection beforeAfter7List = QueryServiceHelper.query("aos_act_select_plan", "aos_sal_actplanentity.aos_itemnum aos_itemnum", new QFilter[]{
                new QFilter("aos_nationality", QCP.equals, aos_orgid),
                new QFilter("aos_sal_actplanentity.aos_l_actstatus", QCP.equals, "A"),
                new QFilter("aos_acttype.number", QCP.equals, "LD").or(new QFilter("aos_acttype.number", QCP.equals, "7DD")),// 活动类型为我DOTD
                new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.large_equals, before7).and(new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.less_equals, after7))//始日期<=制作日期<=结束日期
                        .or(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.large_equals, before7).and(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.less_equals, after7)))//开始日期 > 制作日期
                        .or(new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.large_equals, before7).and(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.less_equals, after7)))// 过去3天的活动
        });
        Set<String> beforeAfter7Set = beforeAfter7List.stream().map(obj -> obj.getString("aos_itemnum")).collect(Collectors.toSet());

        // 第二次过滤
        List<JSONObject> secFilterList = new ArrayList<>();
        Map<String, DynamicObject> priceMap = ActUtil.getAsinAndPriceFromPrice(aos_orgid, aos_shopid, aos_channelid, itemFilterList);

        DynamicObject orgActivityQty = ActUtil.getOrgActivityQty(aos_orgid);

        // 查询最低价
        Map<String, DynamicObject> lowestPriceMap = ActUtil.queryLowestPrice(aos_orgid, itemFilterList);
        //店铺价格
        List<String> list_noPriceItem = new ArrayList<>(itemFilterList.size());
        String entityId = originObj.getString("id");
        ActShopPriceDao actShopPriceDao = new ActShopPriceImpl();
        Map<String, BigDecimal> map_shopPrice = actShopPriceDao.QueryOrgChannelShopItemNumberPrice(aos_orgid, aos_channelid, aos_shopid, itemFilterList);

        for (DynamicObject obj : firstFilterList) {
            String aos_sku = obj.getString("aos_sku");

            String aos_seasonattr = obj.getString("aos_seasonattr");
            DynamicObject itemObj = itemInfo.get(aos_sku);

//            if (!"921-349V70".equals(aos_sku)) continue;
            if (itemObj == null) continue;
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
                    list_noPriceItem.add(aos_sku);
                    continue;
                }
            }
            else {
                asin = priceObj.getString("aos_asin");// ASIN码
                aos_currentprice = priceObj.getBigDecimal("aos_currentprice");// 当前价格
            }

            DynamicObject lowestPriceObj = lowestPriceMap.get(aos_itemid);
            if (lowestPriceObj == null) continue;

            String aos_festivalseting = itemObj.getString("aos_festivalseting");
            // 选择自有仓库库存>= 30 或平台仓可售天数>=120天的物料
            int platSaleDays = InStockAvailableDays.calAvailableDaysByPlatQty(aos_orgid, aos_itemid);
            if (!(nonPlatItemSet.containsKey(aos_sku) || platSaleDays >= 120)) continue;
            // 剔除：②预计活动日前后7天，活动类型为LD/7DD，且状态为正常的在线ID；
            if (beforeAfter7Set.contains(aos_itemid)) continue;
            if (beforeAfter30Set.contains(aos_itemid)) continue;
            // 预计活动日可售天数
            int salDaysForAct = InStockAvailableDays.calInstockSalDaysForAct(aos_orgid, aos_itemid, start);
            // 常规品: 预计活动日可售天数>= 90
            if ("REGULAR".equals(aos_seasonattr) || "SPRING-SUMMER-CONVENTIONAL".equals(aos_seasonattr)) {
                if (salDaysForAct < 90) continue;
            }

            // 春夏品

            Calendar seasonEnd = Calendar.getInstance();
            if ("SPRING".equals(aos_seasonattr) || "SPRING_SUMMER".equals(aos_seasonattr) || "SUMMER".equals(aos_seasonattr)) {
                // 8月31
                seasonEnd.set(Calendar.MONTH, 7);
                seasonEnd.set(Calendar.DAY_OF_MONTH, 31);

                long betweenDays = ActUtil.betweenDays(seasonEnd.getTime().getTime(), start.getTime());
                if (salDaysForAct < betweenDays) continue;
            }

            if ("AUTUMN_WINTER".equals(aos_seasonattr) || "WINTER".equals(aos_seasonattr)) {
                // 3月31
                seasonEnd.set(Calendar.MONTH, 2);
                seasonEnd.set(Calendar.DAY_OF_MONTH, 31);
                if (start.getTime() > seasonEnd.getTime().getTime()) {
                    seasonEnd.add(Calendar.YEAR, +1);
                }
                long betweenDays = ActUtil.betweenDays(seasonEnd.getTime().getTime(), start.getTime());
                if (salDaysForAct < betweenDays) continue;
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
                // 07-05
                String endDateArr = dateArr[1];
                String[] endArr = endDateArr.split("-");
                endDate.set(Calendar.MONTH, Integer.parseInt(endArr[0]) -1);
                endDate.set(Calendar.DAY_OF_MONTH, Integer.parseInt(endArr[1]));

                if (endDate.before(startDate)) {
                    endDate.add(Calendar.YEAR, +1);
                }
                Date festivalEnd = endDate.getTime();

                long betweenDays = ActUtil.betweenDays(festivalEnd.getTime(), start.getTime());
                if (salDaysForAct < betweenDays) continue;
            }

            String aos_itemname = obj.getString("aos_itemname");
            String aos_category1 = obj.getString("aos_category1");
            String aos_category2 = obj.getString("aos_category2");

            // 平台仓库数量
            BigDecimal aos_platfqty = obj.getBigDecimal("aos_platfqty");
            BigDecimal aos_platavgqty = obj.getBigDecimal("aos_platavgqty");
            // 最大库龄
            BigDecimal aos_itemmaxage = obj.getBigDecimal("aos_itemmaxage");

            // 海外在库数量
            int aos_overseaqty = obj.getInt("aos_overseaqty");

            String aos_typedetail = obj.getString("aos_typedetail");
            // 活动数量
            int aos_lowestqty = orgActivityQty.getInt("aos_lowestqty");
            BigDecimal aos_inventoryratio = orgActivityQty.getBigDecimal("aos_inventoryratio");

            int aos_qty = Math.max(aos_lowestqty, BigDecimal.valueOf(aos_overseaqty).multiply(aos_inventoryratio).setScale(0, BigDecimal.ROUND_HALF_UP).intValue());

            // review
            DynamicObject reviewObj = reviewItemSet.get(aos_sku);
            BigDecimal aos_review = reviewObj.getBigDecimal("aos_review");
            BigDecimal aos_stars = reviewObj.getBigDecimal("aos_stars");

            //
            // 非平台可用量
            int nonPlatQty = InStockAvailableDays.getNonPlatQty(aos_orgid, aos_itemid);
            // 线上7天日均销量 R
            float R = InStockAvailableDays.getOrgItemOnlineAvgQty(aos_orgid, aos_itemid);
            BigDecimal aos_preactqty = BigDecimal.valueOf(nonPlatQty).subtract(BigDecimal.valueOf(R).multiply(BigDecimal.valueOf(currentToAct)));


            // 当年最低价
            BigDecimal minPriceYear = lowestPriceObj.getBigDecimal("aos_min_price");
            // 30天最低价
            BigDecimal minPrice30 = lowestPriceObj.getBigDecimal("aos_min_price30");
            // 活动价 = min(当年最低定价，当前价格*折扣力度,过去30天最低定价*0.95)
            BigDecimal actPrice = aos_currentprice.multiply(BigDecimal.ONE.subtract(aos_disstrength));
            if (minPriceYear.compareTo(actPrice) < 0) {
                actPrice = minPriceYear;
            }

            if (minPrice30.multiply(BigDecimal.valueOf(0.95)).compareTo(actPrice) < 0) {
                actPrice = minPrice30.multiply(BigDecimal.valueOf(0.95));
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
//            itemObject.put("aos_invcostamt", "");// 库存金额
            itemObject.put("aos_stars", aos_stars);// review分数
            itemObject.put("aos_reviewqty", aos_review);// review个数
            itemObject.put("aos_typedetail", aos_typedetail);// 类型细分
            itemObject.put("aos_seasonattr", aos_seasonattr);// 季节属性
            secFilterList.add(itemObject);
        }

        if (secFilterList.isEmpty()) return;


        DynamicObjectCollection aos_sal_actplanentity = originObj.getDynamicObjectCollection("aos_sal_actplanentity");
        aos_sal_actplanentity.clear();

        // 生成活动选品清单
        for (JSONObject obj : secFilterList) {
            DynamicObject lineObj = aos_sal_actplanentity.addNew();
            for (String key : obj.keySet()) {
                lineObj.set(key, obj.get(key));
            }
        }
        SaveServiceHelper.save(new DynamicObject[]{originObj});
        DynamicObject dynamicObject = BusinessDataServiceHelper.loadSingle(originObj.getPkValue(), "aos_act_select_plan");
        ActUtil.setProfitRate(dynamicObject);

        // 计算活动毛利率
        Map<String, BigDecimal> orgLowestProfitRate = ActUtil.getOrgLowestProfitRate(aos_orgid);
        ActUtil.setLowestProfitRate(originObj.getPkValue(), orgLowestProfitRate);
        ActUtil.SaveItme(entityId,list_noPriceItem);
    }
}
