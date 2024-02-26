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
 * @author aosom
 */
public class Matalan implements ActStrategy {
    public final static String RYMAN = "UK-Ryman";
    public final static String LEAFTLET = "Leaftlet";

    @Override
    public void doOperation(DynamicObject object) throws Exception {
        String aosOrgid = object.getDynamicObject("aos_nationality").getString("id");
        String aosOrgnum = object.getDynamicObject("aos_nationality").getString("number");
        Date start = object.getDate("aos_startdate");
        Date end = object.getDate("aos_enddate1");
        String aosChannelid = object.getDynamicObject("aos_channel").getString("id");
        String aosShopid = object.getDynamicObject("aos_shop").getString("id");
        String aosShopnum = object.getDynamicObject("aos_shop").getString("number");
        String aosActtypenum = object.getDynamicObject("aos_acttype").getString("number");
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
        Set<String> apartFromAmzAndEbayItem =
            ActUtil.queryApartFromAmzAndEbayItem(aosOrgid, new String[] {"AMAZON", "EBAY"}, start);
        // 自有仓库可用量大于等于30的物料
        Map<String, DynamicObject> nonPlatItemSet = ActUtil.queryNonPlatQtyLgN(aosOrgid, 30);
        // 剔除同店铺同期活动
        Set<String> sameShopAndSamePeriodItem = ActUtil.querySamePeriodActivities(aosOrgid, aosShopid, start, end);
        // 第一次过滤选品清单
        List<DynamicObject> firstFilterList = new ArrayList<>();
        // 物料list
        List<String> itemFilterList = new ArrayList<>();
        // 销售订单数据 平台30天销量
        HashMap<String, Integer> orderData = ActUtil.GenOrderData(aosOrgid, aosChannelid);
        FndLog log = FndLog.init("MatalanUK", aosOrgnum);
        DynamicObjectCollection selectList = ActUtil.queryActSelectList(aosOrgnum);
        for (DynamicObject obj : selectList) {
            String aosSku = obj.getString("aos_sku");
            String aosSeasonattr = obj.getString("aos_seasonattr");
            // 剔除过季品
            if (ActUtil.isOutSeason(start, aosSeasonattr)) {
                log.add(aosSku + " 季节属性:" + aosSeasonattr + " 过季");
                continue;
            }
            // 剔除除亚马逊、eBay外活动日过去30天已提报平台活动个数＞3的sku
            if (apartFromAmzAndEbayItem.contains(aosSku)) {
                log.add(aosSku + "剔除除亚马逊、eBay外活动日过去30天已提报平台活动个数＞3的sku");
                continue;
            }
            // 剔除同店铺同期活动
            if (sameShopAndSamePeriodItem.contains(aosSku)) {
                log.add(aosSku + "剔除同店铺同期活动");
                continue;
            }
            firstFilterList.add(obj);
            itemFilterList.add(aosSku);
        }
        DynamicObject orgActivityQty = ActUtil.getOrgActivityQty(aosOrgid);
        // 第二次过滤
        Map<String, DynamicObject> priceMap =
            ActUtil.getAsinAndPriceFromPrice(aosOrgid, aosShopid, aosChannelid, itemFilterList);
        // 获取亚马逊价格
        Map<String, DynamicObject> amazonPrice = ActUtil.getAmazonPrice(aosOrgid, itemFilterList);
        // 查询节日销售时间
        Map<String, String[]> festivalStartAndEnd = ActUtil.queryFestivalStartAndEnd(aosOrgid);
        // 选择满足库存条件的sku
        Map<String, DynamicObject> itemInfo = ActUtil.getItemInfo(itemFilterList, aosOrgid);

        // 店铺价格
        List<String> listNoPriceItem = new ArrayList<>(itemFilterList.size());
        String entityId = object.getString("id");
        ActShopPriceDao actShopPriceDao = new ActShopPriceImpl();
        Map<String, BigDecimal> mapShopPrice =
            actShopPriceDao.QueryOrgChannelShopItemNumberPrice(aosOrgid, aosChannelid, aosShopid, itemFilterList);

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
            DynamicObject priceObj = priceMap.get(aosSku);

            // ASIN
            String asin = "";
            BigDecimal aosCurrentprice;
            if (priceObj == null) {
                // 每日价格不存在
                priceObj = amazonPrice.get(aosSku);
                if (priceObj == null) {
                    // 亚马逊价格
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
            } else {
                // ASIN码
                asin = priceObj.getString("aos_asin");
                // 当前价格
                aosCurrentprice = priceObj.getBigDecimal("aos_currentprice");
            }

            // 自有仓库<30；
            if (!nonPlatItemSet.containsKey(aosSku)) {
                log.add(aosSku + " 自有仓库库存小于30");
                continue;
            }

            String aosFestivalseting = itemObj.getString("aos_festivalseting");
            // Ryman Leaftlet条件平台过去30天销量>0
            if ("UK-Ryman".equals(aosShopnum) && "Leaftlet".equals(aosActtypenum)) {
                if (orderData.get(aosSku) == null || orderData.get(aosSku) == 0) {
                    log.add(aosSku + "平台过去30天销量为0");
                    continue;
                }
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

            Calendar startDate = Calendar.getInstance();
            Calendar endDate = Calendar.getInstance();
            // 选品池中季节/节日品,预计活动日可售天数≥(季末-预计活动日)
            // 节日品
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
            String aosItemname = obj.getString("aos_itemname");
            String aosCategory1 = obj.getString("aos_category1");
            String aosCategory2 = obj.getString("aos_category2");
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
        // 按类别分类

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

        if (RYMAN.equals(aosShopnum) && LEAFTLET.equals(aosActtypenum)) {
            // 分组+剔除
            Map<String, List<DynamicObject>> categoryMap = new HashMap<>(16);
            DynamicObject afterProfitRateObject =
                BusinessDataServiceHelper.loadSingle(object.getPkValue(), "aos_act_select_plan");
            DynamicObjectCollection aosSalActplanentity1 =
                afterProfitRateObject.getDynamicObjectCollection("aos_sal_actplanentity");
            for (DynamicObject lineObject : aosSalActplanentity1) {
                String aosCategoryStat1 = lineObject.getString("aos_category_stat1");
                List<DynamicObject> jsonObjects = categoryMap.computeIfAbsent(aosCategoryStat1, k -> new ArrayList<>());
                if (jsonObjects.size() < 10) {
                    jsonObjects.add(lineObject);
                }
            }
            aosSalActplanentity1.clear();
            for (String key : categoryMap.keySet()) {
                aosSalActplanentity1.addAll(categoryMap.get(key));
            }
            SaveServiceHelper.save(new DynamicObject[] {afterProfitRateObject});
        }

        // 保存日志表
        log.finnalSave();
        ActUtil.SaveItme(entityId, listNoPriceItem);
    }
}
