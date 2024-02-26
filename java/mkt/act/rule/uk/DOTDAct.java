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
import org.apache.commons.lang3.time.DateFormatUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author aosom
 */
public class DOTDAct implements ActStrategy {
    @Override
    public void doOperation(DynamicObject originObj) throws Exception {
        String ouCode = originObj.getDynamicObject("aos_nationality").getString("number");
        Date start = originObj.getDate("aos_startdate");
        Date end = originObj.getDate("aos_enddate1");
        // 国别id
        String aosOrgid = originObj.getDynamicObject("aos_nationality").getString("id");
        String aosChannelid = originObj.getDynamicObject("aos_channel").getString("id");
        String aosShopid = originObj.getDynamicObject("aos_shop").getString("id");
        // 折扣信息
        BigDecimal aosDisamt = originObj.getBigDecimal("aos_disamt");
        BigDecimal aosDisstrength = originObj.getBigDecimal("aos_disstrength");
        // 满足review条件的物料
        Map<String, DynamicObject> reviewItemSet = ActUtil.queryReview(aosOrgid);
        // 自有仓库可用量大于等于30的物料
        Map<String, DynamicObject> nonPlatItemSet = ActUtil.queryNonPlatQtyLgN(aosOrgid, 30);
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
            String aosSku = obj.getString("aos_sku");
            String aosSeasonattr = obj.getString("aos_seasonattr");
            // 当前国别Review分数>=4和Review个数>=5的物料
            DynamicObject reviewObject = reviewItemSet.get(aosSku);
            if (reviewObject != null) {
                BigDecimal aosReview = reviewObject.getBigDecimal("aos_review");
                BigDecimal aosStars = reviewObject.getBigDecimal("aos_stars");
                if (!(aosReview.compareTo(BigDecimal.valueOf(5)) >= 0
                    && aosStars.compareTo(BigDecimal.valueOf(4)) >= 0)) {
                    continue;
                }
            } else {
                continue;
            }
            // 剔除过季品
            if (ActUtil.isOutSeason(start, aosSeasonattr)) {
                continue;
            }

            firstFilterList.add(obj);
            itemFilterList.add(aosSku);
        }
        Map<String, DynamicObject> itemInfo = ActUtil.getItemInfo(itemFilterList, aosOrgid);

        // 查询节日销售时间
        Map<String, String[]> festivalStartAndEnd = ActUtil.queryFestivalStartAndEnd(aosOrgid);

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
        DynamicObjectCollection beforeAfter30List =
            QueryServiceHelper.query("aos_act_select_plan", "aos_sal_actplanentity.aos_itemnum aos_itemnum",
                new QFilter[] {new QFilter("aos_nationality", QCP.equals, aosOrgid),
                    new QFilter("aos_actstatus", QCP.not_equals, "C"),
                    new QFilter("aos_acttype.number", QCP.equals, "DOTD"),
                    new QFilter("aos_sal_actplanentity.aos_l_actstatus", QCP.equals, "A"),
                    new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.large_equals, before30)
                        .and(new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.less_equals, after30))
                        .or(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.large_equals, before30)
                            .and(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.less_equals, after30)))
                        .or(new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.large_equals, before30)
                            .and(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.less_equals, after30)))});
        Set<String> beforeAfter30Set =
            beforeAfter30List.stream().map(obj -> obj.getString("aos_itemnum")).collect(Collectors.toSet());
        // ②预计活动日前后7天，活动类型为LD/7DD，且状态为正常的在线ID；
        Calendar actDate7 = Calendar.getInstance();
        actDate7.setTime(start);
        actDate7.add(Calendar.DAY_OF_MONTH, -7);
        // 30天前
        String before7 = DateFormatUtils.format(actDate7.getTime(), "yyyy-MM-dd");
        // 30天后
        actDate30.add(Calendar.DAY_OF_MONTH, 14);
        String after7 = DateFormatUtils.format(actDate7.getTime(), "yyyy-MM-dd");
        DynamicObjectCollection beforeAfter7List =
            QueryServiceHelper.query("aos_act_select_plan", "aos_sal_actplanentity.aos_itemnum aos_itemnum",
                new QFilter[] {new QFilter("aos_nationality", QCP.equals, aosOrgid),
                    new QFilter("aos_sal_actplanentity.aos_l_actstatus", QCP.equals, "A"),
                    new QFilter("aos_acttype.number", QCP.equals, "LD")
                        .or(new QFilter("aos_acttype.number", QCP.equals, "7DD")),
                    new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.large_equals, before7)
                        .and(new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.less_equals, after7))
                        .or(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.large_equals, before7)
                            .and(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.less_equals, after7)))
                        .or(new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.large_equals, before7)
                            .and(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.less_equals, after7)))});
        Set<String> beforeAfter7Set =
            beforeAfter7List.stream().map(obj -> obj.getString("aos_itemnum")).collect(Collectors.toSet());
        // 第二次过滤
        List<JSONObject> secFilterList = new ArrayList<>();
        Map<String, DynamicObject> priceMap =
            ActUtil.getAsinAndPriceFromPrice(aosOrgid, aosShopid, aosChannelid, itemFilterList);
        DynamicObject orgActivityQty = ActUtil.getOrgActivityQty(aosOrgid);
        // 查询最低价
        Map<String, DynamicObject> lowestPriceMap = ActUtil.queryLowestPrice(aosOrgid, itemFilterList);
        // 店铺价格
        List<String> listNoPriceItem = new ArrayList<>(itemFilterList.size());
        String entityId = originObj.getString("id");
        ActShopPriceDao actShopPriceDao = new ActShopPriceImpl();
        Map<String, BigDecimal> mapShopPrice =
            actShopPriceDao.QueryOrgChannelShopItemNumberPrice(aosOrgid, aosChannelid, aosShopid, itemFilterList);
        for (DynamicObject obj : firstFilterList) {
            String aosSku = obj.getString("aos_sku");
            String aosSeasonattr = obj.getString("aos_seasonattr");
            DynamicObject itemObj = itemInfo.get(aosSku);
            if (itemObj == null) {
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
                    listNoPriceItem.add(aosSku);
                    continue;
                }
            } else {
                // ASIN码
                asin = priceObj.getString("aos_asin");
                // 当前价格
                aosCurrentprice = priceObj.getBigDecimal("aos_currentprice");
            }

            DynamicObject lowestPriceObj = lowestPriceMap.get(aosItemid);
            if (lowestPriceObj == null) {
                continue;
            }

            String aosFestivalseting = itemObj.getString("aos_festivalseting");
            // 选择自有仓库库存>= 30 或平台仓可售天数>=120天的物料
            int platSaleDays = InStockAvailableDays.calAvailableDaysByPlatQty(aosOrgid, aosItemid);
            if (!(nonPlatItemSet.containsKey(aosSku) || platSaleDays >= 120)) {
                continue;
            }
            // 剔除：②预计活动日前后7天，活动类型为LD/7DD，且状态为正常的在线ID；
            if (beforeAfter7Set.contains(aosItemid)) {
                continue;
            }
            if (beforeAfter30Set.contains(aosItemid)) {
                continue;
            }
            // 预计活动日可售天数
            int salDaysForAct = InStockAvailableDays.calInstockSalDaysForAct(aosOrgid, aosItemid, start);
            // 常规品: 预计活动日可售天数>= 90
            if ("REGULAR".equals(aosSeasonattr) || "SPRING-SUMMER-CONVENTIONAL".equals(aosSeasonattr)) {
                if (salDaysForAct < 90) {
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
                // 07-05
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
                    continue;
                }
            }
            String aosItemname = obj.getString("aos_itemname");
            String aosCategory1 = obj.getString("aos_category1");
            String aosCategory2 = obj.getString("aos_category2");
            // 平台仓库数量
            BigDecimal aosPlatfqty = obj.getBigDecimal("aos_platfqty");
            BigDecimal aosPlatavgqty = obj.getBigDecimal("aos_platavgqty");
            // 最大库龄
            BigDecimal aosItemmaxage = obj.getBigDecimal("aos_itemmaxage");
            // 海外在库数量
            int aosOverseaqty = obj.getInt("aos_overseaqty");
            String aosTypedetail = obj.getString("aos_typedetail");
            // 活动数量
            int aosLowestqty = orgActivityQty.getInt("aos_lowestqty");
            BigDecimal aosInventoryratio = orgActivityQty.getBigDecimal("aos_inventoryratio");
            int aosQty = Math.max(aosLowestqty, BigDecimal.valueOf(aosOverseaqty).multiply(aosInventoryratio)
                .setScale(0, RoundingMode.HALF_UP).intValue());
            // review
            DynamicObject reviewObj = reviewItemSet.get(aosSku);
            BigDecimal aosReview = reviewObj.getBigDecimal("aos_review");
            BigDecimal aosStars = reviewObj.getBigDecimal("aos_stars");
            // 非平台可用量
            int nonPlatQty = InStockAvailableDays.getNonPlatQty(aosOrgid, aosItemid);
            // 线上7天日均销量 R
            float r = InStockAvailableDays.getOrgItemOnlineAvgQty(aosOrgid, aosItemid);
            BigDecimal aosPreactqty = BigDecimal.valueOf(nonPlatQty)
                .subtract(BigDecimal.valueOf(r).multiply(BigDecimal.valueOf(currentToAct)));
            // 当年最低价
            BigDecimal minPriceYear = lowestPriceObj.getBigDecimal("aos_min_price");
            // 30天最低价
            BigDecimal minPrice30 = lowestPriceObj.getBigDecimal("aos_min_price30");
            // 活动价 = min(当年最低定价，当前价格*折扣力度,过去30天最低定价*0.95)
            BigDecimal actPrice = aosCurrentprice.multiply(BigDecimal.ONE.subtract(aosDisstrength));
            if (minPriceYear.compareTo(actPrice) < 0) {
                actPrice = minPriceYear;
            }
            if (minPrice30.multiply(BigDecimal.valueOf(0.95)).compareTo(actPrice) < 0) {
                actPrice = minPrice30.multiply(BigDecimal.valueOf(0.95));
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
            // review分数
            itemObject.put("aos_stars", aosStars);
            // review个数
            itemObject.put("aos_reviewqty", aosReview);
            // 类型细分
            itemObject.put("aos_typedetail", aosTypedetail);
            // 季节属性
            itemObject.put("aos_seasonattr", aosSeasonattr);
            secFilterList.add(itemObject);
        }
        if (secFilterList.isEmpty())
        {
            return;
        }
        DynamicObjectCollection aosSalActplanentity = originObj.getDynamicObjectCollection("aos_sal_actplanentity");
        aosSalActplanentity.clear();
        // 生成活动选品清单
        for (JSONObject obj : secFilterList) {
            DynamicObject lineObj = aosSalActplanentity.addNew();
            for (String key : obj.keySet()) {
                lineObj.set(key, obj.get(key));
            }
        }
        SaveServiceHelper.save(new DynamicObject[] {originObj});
        DynamicObject dynamicObject =
            BusinessDataServiceHelper.loadSingle(originObj.getPkValue(), "aos_act_select_plan");
        ActUtil.setProfitRate(dynamicObject);
        // 计算活动毛利率
        Map<String, BigDecimal> orgLowestProfitRate = ActUtil.getOrgLowestProfitRate(aosOrgid);
        ActUtil.setLowestProfitRate(originObj.getPkValue(), orgLowestProfitRate);
        ActUtil.SaveItme(entityId, listNoPriceItem);
    }
}
