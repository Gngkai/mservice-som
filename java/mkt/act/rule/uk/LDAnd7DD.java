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

/**
 * lch 2022-06-28
 */
public class LDAnd7DD implements ActStrategy {
    @Override
    public void doOperation(DynamicObject object) throws Exception {
        // 国别id
        String aosOrgid = object.getDynamicObject("aos_nationality").getString("id");
        String aosOrgnum = object.getDynamicObject("aos_nationality").getString("number");
        Date start = object.getDate("aos_startdate");
        Date end = object.getDate("aos_enddate1");
        String aosChannelid = object.getDynamicObject("aos_channel").getString("id");
        String aosShopid = object.getDynamicObject("aos_shop").getString("id");
        // 自有仓库可用量大于等于30的物料
        Map<String, DynamicObject> nonPlatItemSet = ActUtil.queryNonPlatQtyLgN(aosOrgid, 30);
        // 折扣信息
        BigDecimal aosDisamt = object.getBigDecimal("aos_disamt");
        BigDecimal aosDisstrength = object.getBigDecimal("aos_disstrength");
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
        FndLog log = FndLog.init("LDAnd7DD", aosOrgnum);
        DynamicObjectCollection selectList = ActUtil.queryActSelectList(aosOrgnum);
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
        Set<String> beforeAfter7Set = ActUtil.queryCullActTypeItem(aosOrgid, start, new String[] {"DOTD"}, 7);
        Map<String, DynamicObject> itemInfo = ActUtil.getItemInfo(itemFilterList, aosOrgid);
        // 查询节日销售时间
        Map<String, String[]> festivalStartAndEnd = ActUtil.queryFestivalStartAndEnd(aosOrgid);
        DynamicObject orgActivityQty = ActUtil.getOrgActivityQty(aosOrgid);
        // 选择满足库存条件的sku
        Map<String, List<Date[]>> actDateMap = ActUtil.query2ActDate(aosOrgid, new String[] {"LD", "7DD"}, 90);
        Map<String, DynamicObject> priceMap =
            ActUtil.getAsinAndPriceFromPrice(aosOrgid, aosShopid, aosChannelid, itemFilterList);
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
            // 选择自有仓库库存>= 30 或平台仓可售天数>=120天的物料
            int platSaleDays = InStockAvailableDays.calAvailableDaysByPlatQty(aosOrgid, aosItemid);
            if (!(nonPlatItemSet.containsKey(aosSku) || platSaleDays >= 90)) {
                log.add(aosSku + "自有仓库<30 平台仓可售天数:" + platSaleDays);
                continue;
            }

            // 剔除：②预计活动日前后7天，活动类型为DOTD，且状态为正常的在线ID；
            if (beforeAfter7Set.contains(aosItemid)) {
                log.add(aosSku + "②预计活动日前后7天，活动类型为DOTD，且状态为正常的在线ID");
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

            Calendar seasonEnd = Calendar.getInstance();
            // 春夏品
            if ("SPRING".equals(aosSeasonattr) || "SPRING_SUMMER".equals(aosSeasonattr)
                || "SUMMER".equals(aosSeasonattr)) {
                // 8月31
                seasonEnd.set(Calendar.MONTH, 7);
                seasonEnd.set(Calendar.DAY_OF_MONTH, 31);
                long betweenDays = ActUtil.betweenDays(seasonEnd.getTime().getTime(), start.getTime());
                if (salDaysForAct < betweenDays) {
                    log.add(aosSku + "春夏品: 预计活动日可售天数=" + salDaysForAct + " 季末-预计活动日=" + betweenDays);
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
                    log.add(aosSku + "秋冬品: 预计活动日可售天数=" + salDaysForAct + " 季末-预计活动日=" + betweenDays);
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
                    log.add(aosSku + "节日品: 预计活动日可售天数=" + salDaysForAct + " 季末-预计活动日=" + betweenDays);
                    continue;
                }
            }

            List<Date[]> dates = actDateMap.get(aosItemid);
            if (dates != null) {
                // 查到两次活动
                int sum = 0;
                for (Date[] date : dates) {
                    sum = sum + ActUtil.queryActQty(aosOrgid, aosShopid, date[0], date[1]);
                }
                if ((float)sum / (float)2 <= 3) {
                    log.add(aosSku + " 剔除活动日前后");
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
            // 活动数量
            int aosLowestqty = orgActivityQty.getInt("aos_lowestqty");
            BigDecimal aosInventoryratio = orgActivityQty.getBigDecimal("aos_inventoryratio");
            int aosQty = Math.max(aosLowestqty, BigDecimal.valueOf(aosOverseaqty).multiply(aosInventoryratio)
                .setScale(0, RoundingMode.HALF_UP).intValue());
            String aosTypedetail = obj.getString("aos_typedetail");
            int nonPlatQty = InStockAvailableDays.getNonPlatQty(aosOrgid, aosItemid);
            // 线上7天日均销量 R
            float r = InStockAvailableDays.getOrgItemOnlineAvgQty(aosOrgid, aosItemid);
            BigDecimal aosPreactqty = BigDecimal.valueOf(nonPlatQty)
                .subtract(BigDecimal.valueOf(r).multiply(BigDecimal.valueOf(currentToAct)));
            // 活动价 = min(当年最低定价，当前价格*折扣力度,过去30天最低定价*0.95)
            BigDecimal actPrice = BigDecimal.ZERO;
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
            secFilterList.add(itemObject);
        }

        // 生成活动选品清单
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
        ActUtil.SaveItme(entityId, listNoPriceItem);
    }
}
