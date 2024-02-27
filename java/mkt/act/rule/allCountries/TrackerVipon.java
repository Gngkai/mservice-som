package mkt.act.rule.allCountries;

import com.alibaba.nacos.common.utils.Pair;
import common.CommonDataSomAct;
import common.Cux_Common_Utl;
import common.fnd.AosomLog;
import common.fnd.FndLog;
import common.sal.sys.basedata.dao.SeasonAttrDao;
import common.sal.sys.basedata.dao.impl.SeasonAttrDaoImpl;
import common.sal.util.SalUtil;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.isc.util.misc.Quad;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import mkt.act.rule.ActStrategy;
import mkt.act.rule.ActUtil;
import mkt.act.rule.service.ActPlanService;
import mkt.act.rule.service.impl.ActPlanServiceImpl;
import mkt.common.MktComUtil;
import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.TreeBidiMap;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author create by ?
 * @since 2022/10/7 17:14
 */
public class TrackerVipon implements ActStrategy {
    private static final AosomLog LOGGER = AosomLog.init("TrackerVipon");
    static {
        LOGGER.setService("AOS.MMS");
        LOGGER.setDomain("MMS.ACT");
    }

    /** 获取常规产品 **/
    private static Map<String, String> getConventItem(Object orgId) {
        Map<String, String> mapRe = new HashMap<>(16);
        SeasonAttrDao seasonAttrDao = new SeasonAttrDaoImpl();
        List<String> listConventSeason = seasonAttrDao.getConventionalProductSeasonName();
        QFilter filterSeason = new QFilter("aos_entryentity.aos_seasonal_attr", QFilter.in, listConventSeason);
        // 滞销报价中的物料
        QFilter filterOrg = new QFilter("aos_org", "=", orgId);
        LocalDate now = LocalDate.now();
        QFilter filterDate = new QFilter("aos_sche_date", ">=", now.minusDays(6).toString());
        List<String> listType = Arrays.asList("低: 0销量", "高: 0销量");
        QFilter filterType = new QFilter("aos_entryentity.aos_unsalable_type", QFilter.in, listType);
        QFilter[] qfs = new QFilter[] {filterSeason, filterDate, filterOrg, filterType};
        DynamicObjectCollection dycUr =
            QueryServiceHelper.query("aos_sal_quo_ur", "aos_entryentity.aos_sku aos_sku", qfs);
        for (DynamicObject dyUr : dycUr) {
            mapRe.put(dyUr.getString("aos_sku"), "零销量");
        }
        // 滞销清单
        filterOrg = new QFilter("aos_orgid", "=", orgId);
        DynamicObjectCollection dycSt =
            QueryServiceHelper.query("aos_base_stitem", "aos_itemid", new QFilter[] {filterOrg});
        List<String> listItemid =
            dycSt.stream().map(dy -> dy.getString("aos_itemid")).distinct().collect(Collectors.toList());
        QFilter filterId = new QFilter("id", QFilter.in, listItemid);
        filterOrg.__setProperty("aos_contryentry.aos_nationality");
        filterSeason =
            new QFilter("aos_contryentry.aos_seasonseting", QFilter.in, seasonAttrDao.getConventionalProductSeason());
        StringJoiner str = new StringJoiner(",");
        str.add("id");
        DynamicObjectCollection dycItem =
            QueryServiceHelper.query("bd_material", str.toString(), new QFilter[] {filterOrg, filterId, filterSeason});
        for (DynamicObject dySt : dycItem) {
            mapRe.put(dySt.getString("id"), "货多销少");
        }
        return mapRe;
    }

    /** 获取季节品/节日品 **/
    private static Map<String, String> getSeasonItem(Object orgId, FndLog log) {
        Map<String, String> mapRe = new HashMap<>(16);
        QFilter filterOrg = new QFilter("aos_org", "=", orgId);
        // 最新的周一
        LocalDate now = LocalDate.now();
        now = now.with(DayOfWeek.MONDAY);
        QFilter filterFrom = new QFilter("aos_sche_date", ">=", now.toString());
        QFilter filterTo = new QFilter("aos_sche_date", "<", now.plusDays(1).toString());
        QFilter filterType = new QFilter("aos_entryentity.aos_adjtype", "=", "降价");
        List<String> listItem = QueryServiceHelper
            .query("aos_sal_quo_sa", "aos_entryentity.aos_sku aos_sku",
                new QFilter[] {filterFrom, filterTo, filterOrg, filterType})
            .stream().map(dy -> dy.getString("aos_sku")).distinct().collect(Collectors.toList());
        // 季节品的季节属性
        SeasonAttrDao seasonAttrDao = new SeasonAttrDaoImpl();
        List<String> listSeason = seasonAttrDao.getSeasonalProductSeason();
        // 查找物料中对应的季节属性
        QFilter filterId = new QFilter("id", QFilter.in, listItem);
        filterOrg.__setProperty("aos_contryentry.aos_nationality");
        StringJoiner str = new StringJoiner(",");
        str.add("id");
        str.add("number");
        str.add("aos_contryentry.aos_seasonseting aos_seasonseting");
        str.add("aos_contryentry.aos_festivalseting aos_festivalseting");
        DynamicObjectCollection dycItem =
            QueryServiceHelper.query("bd_material", str.toString(), new QFilter[] {filterOrg, filterId});

        for (DynamicObject dy : dycItem) {
            // 季节属性属于季节品 或者 节日属性不为空
            if (listSeason.contains(dy.getString("aos_seasonseting"))
                || !Cux_Common_Utl.IsNull(dy.get("aos_festivalseting"))) {
                mapRe.put(dy.getString("id"), "季节滞销");
            } else {
                log.add(dy.getString("number") + " 剔除;  是否季节品： " + listSeason.contains(dy.getString("aos_seasonseting"))
                    + " 是否是节日品： " + !Cux_Common_Utl.IsNull(dy.get("aos_festivalseting")));
            }
        }
        return mapRe;
    }

    /**
     * 根据库存过滤物料
     * 
     * @param orgId org
     * @return filterItemid
     */
    private static Map<String, Integer> filterItemByStock(String orgId, BidiMap<String, String> bidiMapItemId,
        FndLog log) {
        List<String> listItemNumber = new ArrayList<>(bidiMapItemId.values());
        // 获取结存的15天前的海外库存
        LocalDate localNow = LocalDate.now();
        Map<String, Integer> mapStockBefore =
            ActUtil.queryBalanceOverseasInventory(orgId, localNow.minusDays(15).toString(), listItemNumber);
        // 获取当前库存
        Map<String, Integer> mapStock = ActUtil.queryItemOverseasInventory(orgId, listItemNumber);
        // 获取安全库存的标准
        QFilter filterObject = new QFilter("aos_project.name", QFilter.equals, "安全库存＞");
        QFilter filterOrg = new QFilter("aos_org", QFilter.equals, orgId);
        DynamicObject dyStand =
            QueryServiceHelper.queryOne("aos_sal_quo_m_coe", "aos_value", new QFilter[] {filterObject, filterOrg});
        int stand = 0;
        boolean cond = (dyStand != null && dyStand.get("aos_value") != null);
        if (cond) {
            stand = dyStand.getBigDecimal("aos_value").intValue();
        }
        Map<String, Integer> mapRe = new HashMap<>(16);
        for (String item : listItemNumber) {
            StringJoiner str = new StringJoiner(";");
            str.add(item);
            boolean create = true;
            if (!mapStock.containsKey(item)) {
                str.add("当前库存不存在 剔除");
                create = false;
            }
            if (!mapStockBefore.containsKey(item)) {
                str.add("前15天库存不存在 剔除");
                create = false;
            }
            if (create) {
                int stockNow = mapStock.get(item);
                str.add("当前库存： " + stockNow);
                int stockBefore = mapStockBefore.get(item);
                str.add("之前库存： " + stockBefore);
                str.add("安全标准： " + stand);
                if (stockNow > 50 && stockBefore > stand) {
                    mapRe.put(item, stockNow);
                } else {
                    str.add("库存不满足");
                }
            }
            log.add(str.toString());
        }
        return mapRe;
    }

    /**
     * 根据海外滞销库存过滤物料
     * 
     * @param orgId orgid
     * @param listItem itemid
     */
    private static Map<String, String> filterItemByUnsale(String orgId, List<String> listItem) {
        QFilter filterOrg = new QFilter("aos_org", QFilter.equals, orgId);
        QFilter filterItem = new QFilter("aos_entryentity.aos_sku.number", QFilter.in, listItem);
        LocalDate localDate = LocalDate.now().minusDays(7);
        QFilter filterDate = new QFilter("aos_sche_date", ">=", localDate.toString());
        StringJoiner str = new StringJoiner(",");
        str.add("aos_entryentity.aos_sku.number aos_sku");
        str.add("aos_entryentity.aos_unsalable_type aos_unsalable_type");
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_sal_quo_ur", str.toString(),
            new QFilter[] {filterOrg, filterItem, filterDate});
        return dyc.stream().collect(Collectors.toMap(dy -> dy.getString("aos_sku"),
            dy -> dy.getString("aos_unsalable_type"), (key1, key2) -> key1));
    }

    /**
     * 根据活动过滤物料
     * 
     * @param org orgid
     * @param dateS act begin date
     * @param dateE act end date
     * @param listItem itemid
     * @return filterItemid
     */
    private static List<String> filterItemByAct(String billno, String org, String dateS, String dateE,
        LocalDate localMake, List<String> listItem, FndLog log, BidiMap<String, String> bidiMapItemId) {
        // 根据国别+抓客活动开始和结束期间(头信息)判断该SKU是否在其他活动计划表中正在进行：LD、DOTD和7DD，且活动开始和结束时间（行上）与抓客期间重叠，
        // 且头状态或行状态非“手工关闭”；
        QFilter filterOrg = new QFilter("aos_nationality", QFilter.equals, org);
        List<String> listActType = Arrays.asList("LD", "DOTD", "7DD");
        QFilter filterActType = new QFilter("aos_acttype.number", QFilter.in, listActType);
        // 情况1 行开始>= 抓客开始 且 行开始 <= 抓客结束
        QFilter filterBegin = new QFilter("aos_sal_actplanentity.aos_l_startdate", ">=", dateS);
        QFilter filterOver = new QFilter("aos_sal_actplanentity.aos_l_startdate", "<=", dateE);
        QFilter filter1 = filterBegin.and(filterOver);
        // 情况2 行结束>=抓客开始 且 行结束<= 抓客结束
        filterBegin = new QFilter("aos_sal_actplanentity.aos_enddate", ">=", dateS);
        filterOver = new QFilter("aos_sal_actplanentity.aos_enddate", "<=", dateE);
        QFilter filter2 = filterBegin.and(filterOver);
        // 情况3 行开始<=抓客开始,且 行结束>= 抓客结束
        QFilter filterDate = filter1.or(filter2);
        QFilter filterHeadStatus = new QFilter("aos_actstatus", "!=", "C");
        QFilter filterRowStatus = new QFilter("aos_sal_actplanentity.aos_l_actstatus", "!=", "B");
        QFilter[] qfs = new QFilter[] {filterOrg, filterActType, filterDate, filterHeadStatus, filterRowStatus};
        DynamicObjectCollection dycOtherAct =
            QueryServiceHelper.query("aos_act_select_plan", "aos_sal_actplanentity.aos_itemnum aos_itemnum", qfs);
        List<String> listOtherActItem =
            dycOtherAct.stream().map(dy -> dy.getString("aos_itemnum")).collect(Collectors.toList());
        LOGGER.info("TrackerVipon  其他活动剔除物料数量：  " + listOtherActItem.size());
        // 根据国别判断录入日期8天内该SKU是否在其他“Tracker/Vipon”的活动计划中，且头状态或行状态非“手工关闭”
        filterActType = new QFilter("aos_acttype.number", QFilter.equals, "Tracker/Vipon");
        QFilter filterBillno = new QFilter("billno", "!=", billno);
        QFilter filterMakeE = new QFilter("aos_makedate", "<", localMake.plusDays(1));
        QFilter filterMakeS = new QFilter("aos_makedate", ">=", localMake.minusDays(8));
        qfs = new QFilter[] {filterBillno, filterOrg, filterActType, filterMakeE, filterMakeS, filterHeadStatus,
            filterRowStatus};
        DynamicObjectCollection dycTheAct =
            QueryServiceHelper.query("aos_act_select_plan", "aos_sal_actplanentity.aos_itemnum aos_itemnum", qfs);
        List<String> listSameAct =
            dycTheAct.stream().map(dy -> dy.getString("aos_itemnum")).collect(Collectors.toList());
        LOGGER.info("TrackerVipon  相同活动剔除物料数量：  " + listSameAct.size());
        List<String> listRe = new ArrayList<>(listItem.size());
        for (String item : listItem) {
            if (!listOtherActItem.contains(item) && !listSameAct.contains(item)) {
                listRe.add(item);
            } else {
                log.add(bidiMapItemId.get(item) + "存在重复活动 剔除");
            }
        }
        return listRe;
    }

    /**
     * 根据折扣价剔除
     * 
     * @param orgid 国别
     * @param shopid 店铺
     * @param listItem 货号
     * @param log 日志
     * @param bidiMapItemId 货号
     * @return 每日价格
     * @throws Exception 异常
     */
    private static Quad filterItemByPrice(Object orgid, Object shopid, List<String> listItem, FndLog log,
        BidiMap<String, String> bidiMapItemId) throws Exception {
        List<String> listRe = new ArrayList<>(listItem.size());
        // 获取亚马逊价格
        DynamicObject dyAmazonShop = SalUtil.get_orgMainShop(orgid);
        Map<String, DynamicObject> mapAmazonPrice =
            SalUtil.get_shopPrice((String)orgid, dyAmazonShop.getString("id"), listItem);
        // 物料成本(入库成本)
        Map<String, BigDecimal> mapCost = common.sal.util.SalUtil.get_NewstCost((String)orgid, listItem);
        // 最低快递费
        Map<String, BigDecimal> mapFee =
            common.sal.util.SalUtil.getMinExpressFee((String)orgid, (String)shopid, listItem);
        // 记录原价和活动价格
        Map<String, BigDecimal[]> mapItemPrice = new HashMap<>(16);
        // 记录需提价
        Map<String, BigDecimal> mapIncrease = new HashMap<>(16);
        for (String item : listItem) {
            StringJoiner str = new StringJoiner(";");
            str.add(bidiMapItemId.get(item));
            // 亚马逊价格
            BigDecimal bigAmprice = BigDecimal.ZERO;
            str.add("amprice: ");
            if (mapAmazonPrice.containsKey(item)) {
                bigAmprice = mapAmazonPrice.get(item).getBigDecimal("aos_currentprice");
                str.add("存在");
            }
            str.add(bigAmprice + " ");
            BigDecimal bigFee = mapFee.getOrDefault(item, BigDecimal.ZERO);
            str.add("快递费：  " + bigFee);
            BigDecimal bigCost = mapCost.getOrDefault(item, BigDecimal.ZERO);
            str.add("成本：  " + bigCost);
            BigDecimal bigPrice = bigFee.add(bigAmprice.multiply(BigDecimal.valueOf(0.15)))
                .add(bigCost.multiply(BigDecimal.valueOf(0.3)));
            bigPrice = bigPrice.setScale(3, RoundingMode.HALF_UP);
            str.add("最惨定价:  " + bigPrice);
            BigDecimal bigDiscount = bigAmprice.multiply(BigDecimal.valueOf(0.5));
            bigDiscount = bigDiscount.setScale(3, RoundingMode.HALF_UP);
            if (bigDiscount.compareTo(bigPrice) < 0) {
                bigDiscount = bigPrice;
                // 计算需提价
                BigDecimal bidIncrease = bigDiscount.multiply(BigDecimal.valueOf(2));
                // 计算提交后的利率
                if (bigAmprice.doubleValue() != 0) {
                    BigDecimal bidIncreaseRate =
                        (bidIncrease.subtract(bigAmprice)).divide(bigAmprice, 2, RoundingMode.HALF_UP);
                    if (bidIncreaseRate.doubleValue() > 0.3) {
                        str.add("剔除");
                        continue;
                    }
                }
                mapIncrease.put(item, bidIncrease);
            }
            listRe.add(item);
            BigDecimal[] bigItemPrice = new BigDecimal[2];
            bigItemPrice[0] = bigAmprice;
            bigItemPrice[1] = bigDiscount;
            mapItemPrice.put(item, bigItemPrice);
            str.add("折扣价：  " + bigDiscount);
            log.add(str.toString());
        }
        // 计算毛利率
        // 计算毛利率
        Map<String, Map<String, BigDecimal>> mapProfit =
            CommonDataSomAct.get_formula(orgid.toString(), shopid.toString(), null, mapItemPrice);
        return new Quad(mapProfit, mapItemPrice, listRe, mapIncrease);
    }

    /**
     * 根据比例分配数量
     * 
     * @param mapProport 每个品类占的比例
     * @param total 总量
     * @return cate to qty
     */
    private static Map<String, Integer> allocateQty(Map<String, BigDecimal> mapProport, int total) {
        Map<String, Integer> mapRe = new HashMap<>(16);
        // 已经分配量
        int assigned = 0;
        int index = 0, size = mapProport.size() - 1;
        for (Map.Entry<String, BigDecimal> entry : mapProport.entrySet()) {
            int qty;
            if (index == size) {
                qty = total - assigned;
            } else {
                qty = new BigDecimal(total).multiply(entry.getValue()).setScale(0, RoundingMode.HALF_UP).intValue();
            }
            mapRe.put(entry.getKey(), qty);
            assigned += qty;
            index++;
        }
        return mapRe;
    }

    private static BidiMap<String, String> mapItemIDToNumber(List<String> listItem) {
        BidiMap<String, String> mapRe = new TreeBidiMap<>();
        QFilter filterNumber = new QFilter("id", QFilter.in, listItem);
        DynamicObjectCollection dyc =
            QueryServiceHelper.query("bd_material", "id,number", new QFilter[] {filterNumber});
        for (DynamicObject dy : dyc) {
            mapRe.put(dy.getString("id"), dy.getString("number"));
        }
        return mapRe;
    }

    /**
     * 计算库存金额
     * 
     * @param orgid org
     * @param listFilterItem itemID
     * @param bidiMapIdToNumber itemID to itemNumber
     * @param mapItemStock itemNumber to StockQty
     * @return itemId to StockPrice (sort by stockPrice DESC)
     */
    private static Map<String, BigDecimal> calInventQty(Object orgid, List<String> listFilterItem,
        BidiMap<String, String> bidiMapIdToNumber, Map<String, Integer> mapItemStock,
        LinkedHashMap<String, BigDecimal> linkMapRe, FndLog log) {
        Map<String, BigDecimal> mapRe = new HashMap<>(16);
        // 获取最新入库成本
        Map<String, BigDecimal> mapCost = SalUtil.get_NewstCost(String.valueOf(orgid), listFilterItem);
        for (Map.Entry<String, BigDecimal> entry : mapCost.entrySet()) {
            if (bidiMapIdToNumber.containsKey(entry.getKey())) {
                String itemNumber = bidiMapIdToNumber.get(entry.getKey());
                if (mapItemStock.containsKey(itemNumber)) {
                    BigDecimal bigStockPrice = entry.getValue().multiply(new BigDecimal(mapItemStock.get(itemNumber)))
                        .setScale(3, RoundingMode.HALF_UP);
                    mapRe.put(entry.getKey(), bigStockPrice);
                    log.add(itemNumber + "入库成本：" + entry.getValue() + "  库存金额： " + bigStockPrice);
                }
            }
        }
        List<Map.Entry<String, BigDecimal>> listRe = mapRe.entrySet().stream().sorted((ent1, ent2) -> {
            if (ent1.getValue().compareTo(ent2.getValue()) >= 0) {
                return -1;
            } else {
                return 1;
            }
        }).collect(Collectors.toList());
        for (Map.Entry<String, BigDecimal> entry : listRe) {
            linkMapRe.put(entry.getKey(), entry.getValue());
        }
        return mapCost;
    }

    private static List<String> fillItem(List<String> listFilterItem, Map<String, String> mapConventItem,
        Map<String, String> mapSeasonItem, Map<String, Integer> mapCateStand, Map<String, List<String>> mapCate) {
        List<String> listAllfilledItem = new ArrayList<>();
        List<String> listSeasonFill = new ArrayList<>();
        Map<String, Integer> mapCateFill = new HashMap<>(16);
        for (String item : listFilterItem) {
            if (mapConventItem.containsKey(item)) {
                if (mapCate.containsKey(item)) {
                    List<String> listCate = mapCate.get(item);
                    if (listCate.size() > 0) {
                        String cate = listCate.get(0);
                        int fillCount = mapCateFill.computeIfAbsent(cate, kye -> 0);
                        int stand = mapCateStand.getOrDefault(cate, 0);
                        if (fillCount < stand) {
                            listAllfilledItem.add(item);
                            fillCount++;
                            mapCateFill.put(cate, fillCount);
                        }
                    }
                }
            } else if (mapSeasonItem.containsKey(item)) {
                if (listSeasonFill.size() < 50) {
                    listSeasonFill.add(item);
                    listAllfilledItem.add(item);
                }
            }
            if (listAllfilledItem.size() >= 150) {
                break;
            }
        }
        return listAllfilledItem;
    }

    private static Map<String, DynamicObject> queryItemInfo(Object orgid, List<String> listItemid) {
        QFilter filterItem = new QFilter("id", QFilter.in, listItemid);
        QFilter filterOrg = new QFilter("aos_contryentry.aos_nationality", QFilter.equals, orgid);
        QFilter[] qfs = new QFilter[] {filterItem, filterOrg};
        StringJoiner str = new StringJoiner(",");
        str.add("id aos_itemnum");
        str.add("name aos_itemname");
        str.add("aos_contryentry.aos_seasonseting.number aos_seasonattr");
        str.add("aos_contryentry.aos_contrybrand.name aos_brand");
        return QueryServiceHelper.query("bd_material", str.toString(), qfs).stream()
            .collect(Collectors.toMap(dy -> dy.getString("aos_itemnum"), dy -> dy, (key1, key2) -> key1));
    }

    private static Map<String, Integer> calItemActQty(List<String> listItem, BidiMap<String, String> mapItemIdToNumber,
        Map<String, Integer> mapStock) {
        Map<String, Integer> mapRe = new HashMap<>(16);
        for (String itemid : listItem) {
            String itemNumber = mapItemIdToNumber.get(itemid);
            int stock = mapStock.getOrDefault(itemNumber, 0);
            stock = BigDecimal.valueOf(stock).multiply(BigDecimal.valueOf(0.1)).setScale(0, RoundingMode.HALF_UP)
                .intValue();
            int min = Math.min(stock, 20);
            mapRe.put(itemid, min);
        }
        return mapRe;
    }

    private static Pair<Map<String, BigDecimal>, Map<String, BigDecimal>> queryRevenueAndCost(Object orgid,
        String orgNumber, Object shopid, List<String> listItem, Map<String, BigDecimal[]> mapPrice,
        Map<String, BigDecimal> mapCost, Map<String, Integer> mapActQty) {
        // 获取快递费
        Map<String, BigDecimal> mapFee = SalUtil.getMinExpressFee(orgid.toString(), shopid.toString(), listItem);
        // 平台费率
        QFilter filterShop = new QFilter("id", QFilter.equals, shopid);
        DynamicObject dyShop =
            QueryServiceHelper.queryOne("aos_sal_shop", "aos_plat_rate,aos_vat", new QFilter[] {filterShop});
        BigDecimal bigPlatRate = BigDecimal.ZERO, bigVat;
        boolean cond = (dyShop != null && dyShop.get("aos_plat_rate") != null);
        if (cond) {
            bigPlatRate = dyShop.getBigDecimal("aos_plat_rate");
        }
        boolean cond1 = (dyShop != null && dyShop.get("aos_vat") != null
            && dyShop.getBigDecimal("aos_vat").compareTo(BigDecimal.ZERO) > 0);
        // vat
        if (cond1) {
            bigVat = dyShop.getBigDecimal("aos_vat");
        } else {
            bigVat = ActUtil.get_VAT(orgid);
        }
        // 汇率
        BigDecimal bigExchange = ActUtil.get_realTimeCurrency(orgNumber);
        Map<String, Map<String, BigDecimal>> mapItemInfo = new HashMap<>(16);
        for (String item : listItem) {
            Map<String, BigDecimal> map = new HashMap<>(16);
            BigDecimal[] bigPrice = mapPrice.get(item);
            // 原价和活动价格
            map.put("aos_price", bigPrice[0]);
            map.put("aos_actprice", bigPrice[1]);
            // 入库成本
            map.put("aos_item_cost", mapCost.get(item));
            // 快递费
            map.put("aos_lowest_fee", mapFee.getOrDefault(item, BigDecimal.ZERO));
            // 平台费率
            map.put("aos_plat_rate", bigPlatRate);
            // vat
            map.put("aos_vat_amount", bigVat);
            // 汇率
            map.put("aos_excval", bigExchange);
            // 活动数量
            map.put("aos_actqty", new BigDecimal(mapActQty.get(item)));
            mapItemInfo.put(item, map);
        }
        Map<String, BigDecimal> mapCalCost =
            CommonDataSomAct.get_cost(orgid.toString(), shopid.toString(), mapItemInfo);
        Map<String, BigDecimal> mapCalRevenue =
            CommonDataSomAct.get_revenue(orgid.toString(), shopid.toString(), mapItemInfo);
        return Pair.with(mapCalCost, mapCalRevenue);
    }

    /** 查找类型细分 **/
    private static Map<String, String> queryTypedatail(String aosOrgnum, List<String> listItemId) {
        DynamicObjectCollection selectList = ActUtil.queryActSelectList(aosOrgnum);
        return selectList.stream().filter(dy -> listItemId.contains(dy.getString("aos_sku"))).collect(Collectors
            .toMap(dy -> dy.getString("aos_sku"), dy -> dy.getString("aos_typedetail"), (key1, key2) -> key1));
    }

    /** 查找大类中类 **/
    public static Map<String, List<String>> getItemCate(List<String> listItem) {
        Map<String, List<String>> mapRe = new HashMap<>(16);
        QFilter filterItem = new QFilter("material", QFilter.in, listItem);
        QFilter filterStandard = new QFilter("standard.number", QFilter.equals, "JBFLBZ");
        DynamicObjectCollection dycData = QueryServiceHelper.query("bd_materialgroupdetail",
            "group.name group,material", new QFilter[] {filterItem, filterStandard});
        for (DynamicObject dy : dycData) {
            List<String> listCate = Arrays.stream(dy.getString("group").split(",")).collect(Collectors.toList());
            mapRe.put(dy.getString("material"), listCate);
        }
        return mapRe;
    }

    @Override
    public void doOperation(DynamicObject object) throws Exception {
        // 获取国别
        Object actEntityId = object.get("id");
        String billno = object.getString("billno");
        String orgId = object.getDynamicObject("aos_nationality").getString("id");
        String orgNumber = object.getDynamicObject("aos_nationality").getString("number");
        String channel = object.getDynamicObject("aos_channel").getString("id");
        String actId = object.getDynamicObject("aos_acttype").getString("id");
        String shopid = object.getDynamicObject("aos_shop").getString("id");
        Date dateActBegin = object.getDate("aos_startdate");
        LocalDate localS = dateActBegin.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        Date dateActEnd = object.getDate("aos_enddate1");
        LocalDate localE = dateActEnd.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate localMake = object.getDate("aos_makedate").toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        // 获取日志
        FndLog log = FndLog.init(billno + "    TrackerVipon", billno);
        // 获取常规品
        Map<String, String> mapConventItem = getConventItem(orgId);
        log.add("常规基础数据获取完成 根据抓客规则获取数据 " + mapConventItem.keySet().size());
        LOGGER.info(billno + "  常规基础数据获取完成 根据抓客规则获取数据  " + mapConventItem.keySet().size());
        // 获取季节品
        Map<String, String> mapSeasonItem = getSeasonItem(orgId, log);
        log.add("季节基础数据获取完成 根据抓客规则获取数据 " + mapSeasonItem.keySet().size());
        LOGGER.info(billno + "  季节基础数据获取完成 根据抓客规则获取数据  " + mapSeasonItem.keySet().size());
        // 获取各品类的分配比例
        Map<String, BigDecimal> mapCateAllocate = ActUtil.queryActCateAllocate(orgId, actId);
        // 确定每个品类的分配数量
        Map<String, Integer> mapSaleQty = allocateQty(mapCateAllocate, 100);
        LOGGER.info(billno + "抓客规则获取国别品类分配比例完成");
        try {
            // 常规品和季节品的物料ID (之后的过滤以此为基础)
            List<String> listFilterItem = new ArrayList<>();
            listFilterItem.addAll(mapConventItem.keySet());
            listFilterItem.addAll(mapSeasonItem.keySet());
            // 获取物料的主键与编码的对应关系
            BidiMap<String, String> bidiMapItemId = mapItemIDToNumber(listFilterItem);
            // 获取 15天库存>安全库存，且库存>50 的物料
            Map<String, Integer> mapStock = filterItemByStock(orgId, bidiMapItemId, log);
            listFilterItem.clear();
            for (String key : mapStock.keySet()) {
                listFilterItem.add(bidiMapItemId.getKey(key));
            }
            LOGGER.info(billno + "   TrackerVipon抓客规则  物料经过库存过滤：  " + listFilterItem.size());
            // 根据活动进行过滤
            listFilterItem = filterItemByAct(billno, orgId, localS.toString(), localE.toString(), localMake,
                listFilterItem, log, bidiMapItemId);
            LOGGER.info(billno + "   TrackerVipon抓客规则  物料经过活动过滤：  " + listFilterItem.size());
            // 根据最惨定价过滤过滤
            Quad quad = filterItemByPrice(orgId, shopid, listFilterItem, log, bidiMapItemId);
            @SuppressWarnings("unchecked")
            Map<String, Map<String, BigDecimal>> mapItemProfit = (Map<String, Map<String, BigDecimal>>)quad.getA();
            LOGGER.info(billno + "   TrackerVipon抓客规则  获取毛利率：  " + mapItemProfit.size());
            @SuppressWarnings("unchecked")
            Map<String, BigDecimal[]> mapItemPirce = (Map<String, BigDecimal[]>)quad.getB();
            LOGGER.info(billno + "   TrackerVipon抓客规则  获取价格：  " + mapItemPirce.size());
            listFilterItem = (List<String>)quad.getC();
            LOGGER.info(billno + "   TrackerVipon抓客规则  根据活动毛利率过滤：  " + listFilterItem.size());
            @SuppressWarnings("unchecked")
            Map<String, BigDecimal> mapIncrease = (Map<String, BigDecimal>)quad.getD();
            // 获取库存金额，并且根据金额排序排列
            LinkedHashMap<String, BigDecimal> linkMapInventPrice = new LinkedHashMap<>();
            Map<String, BigDecimal> mapItemCost =
                calInventQty(orgId, listFilterItem, bidiMapItemId, mapStock, linkMapInventPrice, log);
            listFilterItem = new ArrayList<>(linkMapInventPrice.keySet());
            LOGGER.info(billno + "   TrackerVipon抓客规则  获取库存金额并且倒序：  " + listFilterItem.size());
            // 查找物料分类
            Map<String, List<String>> mapCate = getItemCate(listFilterItem);
            // 选择物料填入单据
            listFilterItem = fillItem(listFilterItem, mapConventItem, mapSeasonItem, mapSaleQty, mapCate);
            LOGGER.info(billno + "   TrackerVipon  选择物料完成   ：  " + listFilterItem.size());
            // 查找帖子id
            Map<String, String> mapAsin = ActUtil.queryOrgShopItemASIN(orgId, shopid, listFilterItem);
            LOGGER.info(billno + "   TrackerVipon  查找帖子id完成   ：  " + mapAsin.size());
            // 物料携带的的其他基础资料
            Map<String, DynamicObject> mapItemInfo = queryItemInfo(orgId, listFilterItem);
            LOGGER.info(billno + "   TrackerVipon  查找物料信息完成   ：  " + mapItemInfo.size());
            // 计算活动数量
            Map<String, Integer> mapItemActQty = calItemActQty(listFilterItem, bidiMapItemId, mapStock);
            LOGGER.info(billno + "   TrackerVipon  计算活动数量完成   ：  " + mapItemActQty.size());
            // 营收和成本
            Pair<Map<String, BigDecimal>, Map<String, BigDecimal>> pairCal =
                queryRevenueAndCost(orgId, orgNumber, shopid, listFilterItem, mapItemPirce, mapItemCost, mapItemActQty);
            Map<String, BigDecimal> mapCalCost = pairCal.getFirst();
            Map<String, BigDecimal> mapCalReven = pairCal.getSecond();
            LOGGER.info(billno + "   TrackerVipon  计算营收完成  ：  " + mapCalReven.size());
            LOGGER.info(billno + "   TrackerVipon  计算成本完成  ：  " + mapCalCost.size());
            // 填充数据
            DynamicObjectCollection dycActEnt = object.getDynamicObjectCollection("aos_sal_actplanentity");
            // 物料的所有字段
            List<String> listItemFields = new ArrayList<>();
            if (mapItemInfo.size() > 0) {
                DynamicObject itemInfo = mapItemInfo.values().stream().collect(Collectors.toList()).get(0);
                listItemFields = itemInfo.getDynamicObjectType().getProperties().stream().map(pro -> pro.getName())
                    .collect(Collectors.toList());
            }
            // detialType
            Map<String, String> mapItemToType = queryTypedatail(orgNumber, listFilterItem);
            LOGGER.info(billno + "   TrackerVipon  填充数据开始   ");
            for (String itemid : listFilterItem) {
                String itemNumber = bidiMapItemId.get(itemid);
                DynamicObject dyNew = dycActEnt.addNew();
                // 物料基础信息
                if (mapItemInfo.containsKey(itemid)) {
                    DynamicObject dy = mapItemInfo.get(itemid);
                    for (String field : listItemFields) {
                        dyNew.set(field, dy.get(field));
                    }
                }
                // 活动价、原价
                if (mapItemPirce.containsKey(itemid)) {
                    BigDecimal[] bigPrice = mapItemPirce.get(itemid);
                    dyNew.set("aos_price", bigPrice[0]);
                    dyNew.set("aos_actprice", bigPrice[1]);
                    if (mapItemProfit.containsKey(itemid)) {
                        Map<String, BigDecimal> map = mapItemProfit.get(itemid);
                        dyNew.set("aos_profit", map.get("aos_profit"));
                        dyNew.set("aos_item_cost", map.get("aos_item_cost"));
                        dyNew.set("aos_lowest_fee", map.get("aos_lowest_fee"));
                        dyNew.set("aos_plat_rate", map.get("aos_plat_rate"));
                        dyNew.set("aos_vat_amount", map.get("aos_vat_amount"));
                        dyNew.set("aos_excval", map.get("aos_excval"));
                    }
                }

                // 大类中类
                if (mapCate.containsKey(itemid)) {
                    List<String> listCate = mapCate.get(itemid);
                    if (listCate.size() > 0) {
                        dyNew.set("aos_category_stat1", listCate.get(0));
                    }
                    if (listCate.size() > 1) {
                        dyNew.set("aos_category_stat2", listCate.get(1));
                    }
                }
                // 帖子id
                if (mapAsin.containsKey(itemid)) {
                    dyNew.set("aos_postid", mapAsin.get(itemid));
                }
                // 活动开始时间和结束时间
                dyNew.set("aos_l_startdate", dateActBegin);
                dyNew.set("aos_enddate", dateActEnd);
                // 活动数量
                if (mapItemActQty.containsKey(itemid)) {
                    dyNew.set("aos_actqty", mapItemActQty.get(itemid));
                }
                // 需提价
                if (mapIncrease.containsKey(itemid)) {
                    dyNew.set("aos_increase", mapIncrease.get(itemid));
                }
                // 活动状态
                dyNew.set("aos_l_actstatus", "A");
                // 营收
                if (mapCalReven.containsKey(itemid)) {
                    dyNew.set("aos_revenue_tax_free", mapCalReven.get(itemid));
                }
                // 成本
                if (mapCalCost.containsKey(itemid)) {
                    dyNew.set("aos_cost_tax_free", mapCalCost.get(itemid));
                }
                // detialTyep
                if (mapItemToType.containsKey(itemid)) {
                    dyNew.set("aos_typedetail", mapItemToType.get(itemid));
                }
                if (mapConventItem.containsKey(itemid)) {
                    dyNew.set("aos_typedetail", mapConventItem.get(itemid));
                } else {
                    dyNew.set("aos_typedetail", mapSeasonItem.get(itemid));
                }
            }
            LOGGER.info(billno + "   TrackerVipon  填充数据结束   ");
            SaveServiceHelper.save(new DynamicObject[] {object});
            object = BusinessDataServiceHelper.loadSingle(object.getPkValue(), "aos_act_select_plan");
            // 赋值库存信息、活动信息
            ActPlanService actPlanService = new ActPlanServiceImpl();
            actPlanService.updateActInfo(object);
            // 统计行信息到头表
            CommonDataSomAct.collectRevenueCost(object);
            SaveServiceHelper.save(new DynamicObject[] {object});
            LOGGER.info(billno + "   TrackerVipon  头表填充数据   ");
        } catch (Exception e) {
            LOGGER.error("抓客生成失败", e);
            throw e;
        } finally {
            log.finnalSave();
        }
    }
}
