package mkt.popular.sale;

import com.alibaba.nacos.common.utils.Pair;
import common.CommonDataSom;
import common.Cux_Common_Utl;
import common.fnd.FndGlobal;
import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.cache.CacheFactory;
import kd.bos.cache.DistributeSessionlessCache;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import mkt.common.MktComUtil;
import mkt.common.AosMktCacheUtil;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author aosom
 * @version 销售加回-调度任务类
 */
public class AosMktPopAddTask extends AbstractTask {
    public final static String SYSTEM = Cux_Common_Utl.SYSTEM;
    private static final Log logger = LogFactory.getLog(AosMktPopAddTask.class);
    private static final DistributeSessionlessCache CACHE =
        CacheFactory.getCommonCacheFactory().getDistributeSessionlessCache("mkt_redis");

    public static void run() {
        Calendar today = Calendar.getInstance();
        int hour = today.get(Calendar.HOUR_OF_DAY);
        QFilter qfTime;
        DynamicObject dynamicObject = QueryServiceHelper.queryOne("aos_mkt_base_orgvalue", "aos_value",
            new QFilter[] {new QFilter("aos_type", QCP.equals, "TIME")});
        int time = 16;
        if (dynamicObject != null) {
            time = dynamicObject.getBigDecimal("aos_value").intValue();
        }
        // 海外公司
        if (hour < time) {
            qfTime = new QFilter("aos_is_north_america.number", QCP.not_equals, "Y");
        } else {
            qfTime = new QFilter("aos_is_north_america.number", QCP.equals, "Y");
        }
        QFilter overseaFlag = new QFilter("aos_is_oversea_ou.number", "=", "Y");
        QFilter overseaFlag2 = new QFilter("aos_isomvalid", "=", true);
        QFilter[] filtersOu = new QFilter[] {overseaFlag, overseaFlag2, qfTime};
        DynamicObjectCollection bdCountry = QueryServiceHelper.query("bd_country", "id,number", filtersOu);
        for (DynamicObject ou : bdCountry) {
            String pOuCode = ou.getString("number");
            Map<String, Object> params = new HashMap<>(16);
            params.put("p_ou_code", pOuCode);
            executerun(params);
        }
    }

    public static void executerun(Map<String, Object> param) {
        Object pOuCode = param.get("p_ou_code");
        // 删除数据
        Calendar today = Calendar.getInstance();
        today.add(Calendar.DAY_OF_MONTH, -1);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        today.set(Calendar.HOUR, 0);
        HashMap<String, Object> act = AosMktPopAdjsTask.generateAct();
        QFilter groupOrg = new QFilter("aos_org.number", "=", pOuCode);
        QFilter groupEnable = new QFilter("enable", "=", 1);
        QFilter[] filtersGroup = new QFilter[] {groupOrg, groupEnable};
        DynamicObjectCollection aosSalgroup = QueryServiceHelper.query("aos_salgroup", "id", filtersGroup);
        for (DynamicObject group : aosSalgroup) {
            Map<String, Object> params = new HashMap<>(16);
            params.put("p_ou_code", pOuCode);
            params.put("p_group_id", group.getLong("id"));
            doOperate(params, act);
        }
    }

    public static void doOperate(Map<String, Object> params, HashMap<String, Object> act) {
        String pOuCode = (String)params.get("p_ou_code");
        long pGroupId = (long)params.get("p_group_id");
        Object pOrgId = FndGlobal.get_import_id(pOuCode, "bd_country");
        boolean aosIsNorthAmerica = QueryServiceHelper
            .queryOne("bd_country", "aos_is_north_america", new QFilter[] {new QFilter("id", "=", pOrgId)})
            .getBoolean("aos_is_north_america");
        Calendar today = Calendar.getInstance();
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        today.set(Calendar.HOUR_OF_DAY, 0);
        Date now = today.getTime();
        int month = today.get(Calendar.MONTH) + 1;
        today.add(Calendar.DAY_OF_MONTH, -1);
        Date todayD = today.getTime();
        // 数据初始化
        HashMap<String, Object> lastBid = generateLastBid(pOuCode, todayD);
        // 营销国别参数表
        byte[] serializePoporgInfo = CACHE.getByteValue("mkt_poporginfo");
        HashMap<String, Map<String, Object>> popOrgInfo = SerializationUtils.deserialize(serializePoporgInfo);
        // SKU报告1日
        byte[] serializeSkurptdetail = CACHE.getByteValue("mkt_skurptDetail");
        HashMap<String, Map<String, Object>> skuRptDetail = SerializationUtils.deserialize(serializeSkurptdetail);
        // SKU报表三日平均
        byte[] serializeSkurpt3avg = CACHE.getByteValue("mkt_skurpt3avg");
        HashMap<String, Map<String, Object>> skuRpt3Avg = SerializationUtils.deserialize(serializeSkurpt3avg);
        // 获取缓存
        byte[] serializeItem = CACHE.getByteValue("item");
        HashMap<String, Map<String, Object>> item = SerializationUtils.deserialize(serializeItem);
        // 海外在库数量
        Map<String, Object> itemOverseaqtyMap = item.get("item_overseaqty");
        // 最大库龄
        Map<String, Object> itemMaxageMap = item.get("item_maxage");
        // 30日销量
        HashMap<String, Integer> order30 = generateOrder(pOrgId, 30);
        // 7日销量
        HashMap<String, Integer> order7 = generateOrder(pOrgId, 7);
        HashMap<String, Map<String, Object>> itemMap = generateItem(pOrgId);
        // 圣诞季初开始
        Date christmasStart = MktComUtil.getDateRange("aos_datefrom", "CH-1", pOrgId);
        // 圣诞季中结束
        Date christmasEnd = MktComUtil.getDateRange("aos_dateto", "CH-2", pOrgId);
        // 万圣欧洲季初开始
        Date hae1 = MktComUtil.getDateRange("aos_datefrom", "HA-E-1", pOrgId);
        // 万圣欧洲季中结束
        Date hae2 = MktComUtil.getDateRange("aos_dateto", "HA-E-2", pOrgId);
        // 万圣北美季初开始
        Date hau1 = MktComUtil.getDateRange("aos_datefrom", "HA-U-1", pOrgId);
        // 万圣北美季中结束
        Date hau2 = MktComUtil.getDateRange("aos_dateto", "HA-U-2", pOrgId);
        BigDecimal multi = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "MULTI").get("aos_value");
        // 平台上架信息
        Set<String> categorySet = generateCategorySet(pOrgId);
        Set<String> category = generateCategory(pOrgId);
        Set<String> stItem = generateStItem(pOrgId);
        Set<String> season = generateSeaSet(pOrgId, todayD);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        String nowStr = writeFormat.format(now);
        // 今天PPC单据
        QFilter qfDate = new QFilter("aos_date", ">=", nowStr);
        QFilter qfOrg = new QFilter("aos_orgid.number", "=", pOuCode);
        QFilter[] filters = new QFilter[] {qfDate, qfOrg};
        String selectField = "id";
        DynamicObject aosMktPopularPpcn = QueryServiceHelper.queryOne("aos_mkt_popular_ppc", selectField, filters);
        if (aosMktPopularPpcn == null) {
            return;
        }
        Object nowid = aosMktPopularPpcn.get("id");
        qfDate = new QFilter("aos_date", "=", nowStr);
        qfOrg = new QFilter("aos_orgid.number", "=", pOuCode);
        QFilter qfRoi =
            new QFilter("aos_entryentity.aos_roiflag", "=", true).or("aos_entryentity.aos_groupstatus", "=", "PRO");
        QFilter qfGroup = new QFilter("aos_entryentity.aos_sal_group.id", "=", pGroupId);
        filters = new QFilter[] {qfDate, qfOrg, qfGroup, qfRoi};
        selectField = "aos_billno,aos_orgid," + "aos_orgid.number aos_orgnumber,"
            + "aos_poptype,aos_entryentity.aos_productno aos_productno,"
            + "aos_entryentity.aos_seasonseting aos_seasonseting,"
            + "aos_entryentity.aos_contryentrystatus aos_contryentrystatus,"
            + "aos_entryentity.aos_cn_name aos_cn_name," + "aos_entryentity.aos_itemnumer aos_itemnumer,"
            + "aos_entryentity.aos_avadays aos_avadays," + "aos_entryentity.aos_bid aos_bid,"
            + "aos_entryentity.aos_lastpricedate aos_lastpricedate," + "aos_entryentity.aos_lastbid aos_lastbid,"
            + "aos_entryentity.aos_groupdate aos_groupdate," + "aos_entryentity.aos_roi7days aos_roi7days,"
            + "aos_entryentity.aos_itemid aos_itemid," + "aos_entryentity.aos_sal_group aos_sal_group,"
            + "aos_entryentity.id aos_ppcentryid," + "id," + "aos_entryentity.aos_highvalue aos_highvalue,"
            + "aos_entryentity.aos_basestitem aos_basestitem," + "aos_entryentity.aos_roiflag aos_roiflag,"
            + "aos_entryentity.aos_groupstatus aos_groupstatus," + "aos_entryentity.aos_special aos_special," // 特殊广告
            + "aos_entryentity.aos_eliminatedate aos_eliminatedate";
        DynamicObjectCollection aosMktPopularPpcS =
            QueryServiceHelper.query("aos_mkt_popular_ppc", selectField, filters, "aos_entryentity.aos_productno");
        int rows = aosMktPopularPpcS.size();
        logger.info("rows =" + rows);
        if (rows == 0) {
            return;
        }
        DynamicObject head = aosMktPopularPpcS.get(0);
        // 销售加回
        DynamicObject aosMktPopadds = BusinessDataServiceHelper.newDynamicObject("aos_mkt_popadds");
        aosMktPopadds.set("aos_billno", head.get("aos_billno"));
        aosMktPopadds.set("aos_ppcid", nowid);
        aosMktPopadds.set("aos_orgid", pOrgId);
        aosMktPopadds.set("billstatus", "A");
        aosMktPopadds.set("aos_status", "A");
        aosMktPopadds.set("aos_makeby", SYSTEM);
        aosMktPopadds.set("aos_makedate", now);
        aosMktPopadds.set("aos_groupid", pGroupId);
        OperationServiceHelper.executeOperate("save", "aos_mkt_popadds", new DynamicObject[] {aosMktPopadds},
            OperateOption.create());
        DynamicObjectCollection aosEntryentityS = aosMktPopadds.getDynamicObjectCollection("aos_entryentity");
        // 获取所有的物料编码
        List<String> listItemNumber = aosMktPopularPpcS.stream().map(dy -> dy.getString("aos_itemnumer"))
            .filter(Objects::nonNull).collect(Collectors.toList());
        // 分别从常规和排名获取改善措施
        Pair<Map<String, String>, Map<String, String>> pairImprove = queryImproveStep(pOrgId, listItemNumber);
        for (DynamicObject aosMktPopularPpc : aosMktPopularPpcS) {
            String aosItemnumer = aosMktPopularPpc.getString("aos_itemnumer");
            boolean aosRoiflag = aosMktPopularPpc.getBoolean("aos_roiflag");
            String aosGroupstatus = aosMktPopularPpc.getString("aos_groupstatus");
            String aosProductno = aosMktPopularPpc.getString("aos_productno");
            String aosSeasonseting = aosMktPopularPpc.getString("aos_seasonseting");
            long itemId = aosMktPopularPpc.getLong("aos_itemid");
            BigDecimal aosRoi = aosMktPopularPpc.getBigDecimal("aos_roi7days");
            BigDecimal aosBid = aosMktPopularPpc.getBigDecimal("aos_bid");
            Date aosLastpricedate = aosMktPopularPpc.getDate("aos_lastpricedate");
            String aosCnName = aosMktPopularPpc.getString("aos_cn_name");
            // 特殊广告
            Object aosSpecial = aosMktPopularPpc.get("aos_special");
            Map<String, Object> skuRpt3AvgMap = skuRpt3Avg.get(pOrgId + "~" + itemId);
            BigDecimal impress3Avg = BigDecimal.ZERO;
            if (skuRpt3AvgMap != null) {
                impress3Avg = (BigDecimal)skuRpt3AvgMap.get("aos_impressions");
            }
            String categoryStr = CommonDataSom.getItemCategoryName(String.valueOf(itemId));
            if (FndGlobal.IsNull(categoryStr)) {
                continue;
            }
            String[] categoryGroup = categoryStr.split(",");
            String aosCategory1 = null;
            String aosCategory2 = null;
            String aosCategory3 = null;
            int categoryLength = categoryGroup.length;
            if (categoryLength > 0) {
                aosCategory1 = categoryGroup[0];
            }
            if (categoryLength > 1) {
                aosCategory2 = categoryGroup[1];
            }
            if (categoryLength > 2) {
                aosCategory3 = categoryGroup[2];
            }
            // Sku报告
            Map<String, Object> skuRptDetailMap = skuRptDetail.get(pOrgId + "~" + itemId);
            BigDecimal aosGroupcost = BigDecimal.ZERO;
            if (skuRptDetailMap != null) {
                aosGroupcost = (BigDecimal)skuRptDetailMap.get("aos_spend");
            }
            Object itemOverseaqty = itemOverseaqtyMap.get(pOrgId + "~" + itemId);
            if (FndGlobal.IsNull(itemOverseaqty)) {
                itemOverseaqty = 0;
            }
            Object aosActivity = act.get(pOrgId + "~" + itemId);
            // 七日销量
            float aos7Sales = order7.getOrDefault(String.valueOf(itemId), 0);
            float aos30Sales = order30.getOrDefault(String.valueOf(itemId), 0);
            Object aosAge = itemMaxageMap.getOrDefault(pOrgId + "~" + itemId, 0);
            // 对于类型
            String aosType;
            // 相关标记
            // 1.节日品标记
            boolean chrisFlag = false;
            boolean cond =
                (now.after(christmasStart) && now.before(christmasEnd) || now == christmasStart || now == christmasEnd);
            if (cond) {
                chrisFlag = true;
            }
            boolean hallowFlag = false;
            cond = (aosIsNorthAmerica && hau1 != null && hau2 != null
                && (now.after(hau1) && now.before(hau2) || now.equals(hau1) || now.equals(hau2)));
            boolean cond2 = (!aosIsNorthAmerica && hae1 != null && hae2 != null
                && (now.after(hae1) && now.before(hae2) || now.equals(hae1) || now.equals(hae2)));
            if (cond) {
                hallowFlag = true;
            } else if (cond2) {
                hallowFlag = true;
            }
            // 2.新品标记
            boolean newFlag = false;
            Date aosFirstindate = null;
            String aosItemstatus = null;
            String aosSeasonpro = null;
            if (itemMap.get(String.valueOf(itemId)) != null) {
                if (itemMap.get(String.valueOf(itemId)).get("aos_firstindate") != null) {
                    aosFirstindate = (Date)itemMap.get(String.valueOf(itemId)).get("aos_firstindate");
                }
                if (itemMap.get(String.valueOf(itemId)).get("aos_contryentrystatus") != null) {
                    aosItemstatus = (String)itemMap.get(String.valueOf(itemId)).get("aos_contryentrystatus");
                }
                if (itemMap.get(String.valueOf(itemId)).get("aos_seasonseting") != null) {
                    aosSeasonpro = (String)itemMap.get(String.valueOf(itemId)).get("aos_seasonseting");
                }
            }
            cond = aosFirstindate == null || ("E".equals(aosItemstatus) || "A".equals(aosItemstatus))
                && MktComUtil.getBetweenDays(todayD, aosFirstindate) <= 30;
            if (cond) {
                newFlag = true;
            }
            // 3.品牌季节品当季标记
            // 春夏品
            boolean springSummerFlag = false;
            cond =
                "SPRING".equals(aosSeasonpro) || "SPRING_SUMMER".equals(aosSeasonpro) || "SUMMER".equals(aosSeasonpro);
            if (cond) {
                springSummerFlag = true;
            }
            // 秋冬品
            boolean autumnWinterFlag = false;
            cond = (("AUTUMN_WINTER".equals(aosSeasonpro) || "WINTER".equals(aosSeasonpro)));
            if (cond) {
                autumnWinterFlag = true;
            }
            // 4.季节品 分季标记
            boolean rangeFlag = false;
            if (aosSeasonpro == null) {
                aosSeasonpro = "";
            }
            float seasonRate = 0;
            if (springSummerFlag || autumnWinterFlag) {
                seasonRate = MktComUtil.getSeasonRate((long)pOrgId, itemId, aosSeasonpro, itemOverseaqty, month);
            }

            cond = springSummerFlag && (month == 2 || month == 3 || month == 4);
            cond2 = autumnWinterFlag && (month == 8 || month == 9 || month == 10);
            boolean cond3 = (springSummerFlag && (month == 5 || month == 6 || month == 7 || month == 8 || month == 9)
                && MktComUtil.isSeasonRate(aosSeasonpro, month, seasonRate));
            boolean cond4 = (autumnWinterFlag && (month == 11 || month == 12 || month == 1 || month == 2 || month == 3)
                && MktComUtil.isSeasonRate(aosSeasonpro, month, seasonRate));
            if (cond) {
                rangeFlag = true;
            } else if (cond2) {
                rangeFlag = true;
            } else if (cond3) {
                rangeFlag = true;
            } else if (cond4) {
                rangeFlag = true;
            }
            // 开始类型判断
            cond = (("其它节日装饰".equals(aosCategory2) && hallowFlag) || ("圣诞装饰".equals(aosCategory2) && chrisFlag));
            cond2 = categorySet.contains(aosCategory1 + "~" + aosCategory2) && (int)itemOverseaqty > multi.intValue()
                && (!springSummerFlag || autumnWinterFlag);
            if (cond) {
                // 1.节日品
                aosType = "FES";
            } else if (newFlag) {
                // 2.新品大产品
                aosType = "NEW";
            } else if (cond2) {
                // 3.品牌产品
                aosType = "BRA";
            } else if ((int)itemOverseaqty > multi.intValue() && rangeFlag) {
                // 4.季节品
                aosType = "SEA";
            } else if (category.contains(aosCategory1 + "~" + aosCategory2 + "~" + aosCategory3)) {
                // 5.特殊品类
                aosType = "CAT";
            } else if (!Cux_Common_Utl.IsNull(aosActivity) && (String.valueOf(aosActivity)).contains("7DD")) {
                // 6.活动产品
                aosType = "ACT";
            } else if (stItem.contains(String.valueOf(itemId))) {
                // 7.货多销少
                aosType = "LES";
            } else if (FndGlobal.IsNotNull(season) && season.contains(String.valueOf(itemId))) {
                // 8.季节品调价表-降价
                aosType = "DIS";
            } else {
                // 9.其余类型不需要加回
                continue;
            }
            // 对于需要进入销售加回表的数据
            DynamicObject aosEntryentity = aosEntryentityS.addNew();
            aosEntryentity.set("aos_productno", aosProductno);
            aosEntryentity.set("aos_itemnumer", aosItemnumer);
            aosEntryentity.set("aos_categroy1", aosCategory1);
            aosEntryentity.set("aos_categroy2", aosCategory2);
            aosEntryentity.set("aos_categroy3", aosCategory3);
            aosEntryentity.set("aos_roi", aosRoi);
            aosEntryentity.set("aos_expouse", impress3Avg);
            aosEntryentity.set("aos_spend", aosGroupcost);
            aosEntryentity.set("aos_bid", aosBid);
            // 海外可用库存
            aosEntryentity.set("aos_avadays", itemOverseaqty);
            aosEntryentity.set("aos_lastdate", aosLastpricedate);
            aosEntryentity.set("aos_7_sales", aos7Sales);
            aosEntryentity.set("aos_30_sales", aos30Sales);
            aosEntryentity.set("aos_age", aosAge);
            aosEntryentity.set("aos_itemname", aosCnName);
            aosEntryentity.set("aos_type", aosType);
            // 季节属性
            aosEntryentity.set("aos_seasonseting", aosSeasonseting);
            // 剔除前一次出价
            aosEntryentity.set("aos_lastbid", lastBid.get(aosItemnumer));
            // 特殊广告
            aosEntryentity.set("aos_special", aosSpecial);
            if (aosActivity != null) {
                aosEntryentity.set("aos_activity", aosActivity);
            }
            // 常规品滞销存在措施
            if (pairImprove.getFirst().containsKey(aosItemnumer)) {
                aosEntryentity.set("aos_improvedesc", pairImprove.getFirst().get(aosItemnumer));
            } else {
                // 排名改善存在措施
                if (pairImprove.getSecond().containsKey(aosItemnumer)) {
                    aosEntryentity.set("aos_improvedesc", pairImprove.getSecond().get(aosItemnumer));
                }
            }
            String aosEliminatereason;
            if (aosRoiflag) {
                aosEliminatereason = "ROI";
            } else {
                aosEliminatereason = aosGroupstatus;
            }
            // 剔除原因
            aosEntryentity.set("aos_eliminatereason", aosEliminatereason);
        }
        OperationServiceHelper.executeOperate("save", "aos_mkt_popadds", new DynamicObject[] {aosMktPopadds},
            OperateOption.create());
    }

    /**
     * 获取改善措施
     *
     * @param orgid 国别
     * @param listNumber 物料编码
     * @return itemToImprove
     */
    private static Pair<Map<String, String>, Map<String, String>> queryImproveStep(Object orgid,
        List<String> listNumber) {
        // 获取常规品的改善措施
        LocalDate lastWeek = LocalDate.now().minusDays(8);
        QFilter filterOrg = new QFilter("aos_orgid", QFilter.equals, orgid);
        QFilter filterItem = new QFilter("aos_entryentity.aos_itemid.number", QFilter.in, listNumber);
        QFilter filterDate = new QFilter("aos_makedate", ">=", lastWeek.toString());
        QFilter filterMeasure = new QFilter("aos_entryentity.aos_measure", "!=", "");
        QFilter[] qfs = new QFilter[] {filterItem, filterDate, filterOrg, filterMeasure};
        StringJoiner str = new StringJoiner(",");
        str.add("aos_entryentity.aos_itemid.number item");
        str.add("aos_entryentity.aos_measure aos_measure");
        Map<String, String> mapRountine =
            QueryServiceHelper.query("aos_sal_rpusdetail", str.toString(), qfs).stream().collect(
                Collectors.toMap(dy -> dy.getString("item"), dy -> dy.getString("aos_measure"), (key1, key2) -> key2));
        filterMeasure.__setProperty("aos_entryentity.aos_improvedesc");
        qfs = new QFilter[] {filterOrg, filterItem, filterDate, filterMeasure};
        str = new StringJoiner(",");
        str.add("aos_entryentity.aos_itemid.number item");
        str.add("aos_entryentity.aos_improvedesc aos_improvedesc");
        Map<String, String> mapRank =
            QueryServiceHelper.query("aos_sal_rankdetail", str.toString(), qfs).stream().collect(Collectors
                .toMap(dy -> dy.getString("item"), dy -> dy.getString("aos_improvedesc"), (key1, key2) -> key2));
        return Pair.with(mapRountine, mapRank);
    }

    public static HashMap<String, Object> generateLastBid(String pOuCode, Date today) {
        HashMap<String, Object> lastBid = new HashMap<>(16);
        QFilter filterOrg = new QFilter("aos_orgid.number", "=", pOuCode);
        QFilter filterDate = new QFilter("aos_date", "<=", today);
        QFilter filterBid = new QFilter("aos_entryentity.aos_bid", QCP.not_equals, null);
        QFilter[] filters = new QFilter[] {filterOrg, filterDate, filterBid};
        String select =
            "aos_entryentity.aos_itemnumer aos_itemnumer," + "aos_entryentity.aos_bid aos_bid, " + "aos_date";
        DataSet aosMktPopularPpcS =
            QueryServiceHelper.queryDataSet("GenerateLastBid" + pOuCode, "aos_mkt_popular_ppc", select, filters, null);
        String[] groupBy = new String[] {"aos_itemnumer"};
        aosMktPopularPpcS = aosMktPopularPpcS.groupBy(groupBy).maxP("aos_date", "aos_bid").finish();
        while (aosMktPopularPpcS.hasNext()) {
            Row aosMktPopularPpc = aosMktPopularPpcS.next();
            lastBid.put(aosMktPopularPpc.getString("aos_itemnumer"), aosMktPopularPpc.getBigDecimal("aos_bid"));
        }
        aosMktPopularPpcS.close();
        return lastBid;
    }

    /** 近30日销量 **/
    private static HashMap<String, Integer> generateOrder(Object pOrgId, int day) {
        HashMap<String, Integer> order = new HashMap<>(16);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        today.add(Calendar.DAY_OF_MONTH, -day);
        QFilter filterDate = new QFilter("aos_order_date", QCP.large_equals, sdf.format(today.getTime()));
        QFilter filterOrg = new QFilter("aos_org.id", QCP.equals, pOrgId);
        List<QFilter> orderFilter = SalUtil.getOrderFilter();
        orderFilter.add(filterDate);
        orderFilter.add(filterOrg);
        int size = orderFilter.size();
        QFilter[] filters = orderFilter.toArray(new QFilter[size]);
        DataSet orderDataSet = QueryServiceHelper.queryDataSet("aos_mkt_popadds_init_GenerateOrderMonth",
            "aos_sync_om_order_r", "aos_org,aos_item_fid aos_itemid,aos_order_qty", filters, null);
        orderDataSet = orderDataSet.groupBy(new String[] {"aos_org", "aos_itemid"}).sum("aos_order_qty").finish();
        while (orderDataSet.hasNext()) {
            Row aosSyncOmonthSummary = orderDataSet.next();
            int aosTotalQty = aosSyncOmonthSummary.getInteger("aos_order_qty");
            String aosItemid = aosSyncOmonthSummary.getString("aos_itemid");
            order.put(aosItemid, aosTotalQty);
        }
        orderDataSet.close();
        return order;
    }

    private static Set<String> generateStItem(Object aosOrgid) {
        DynamicObjectCollection list = QueryServiceHelper.query("aos_base_stitem", "aos_itemid",
            new QFilter[] {new QFilter("aos_orgid", QCP.equals, aosOrgid)});
        return list.stream().map(obj -> obj.getString("aos_itemid")).collect(Collectors.toSet());
    }

    private static Set<String> generateCategory(Object aosOrgid) {
        DynamicObjectCollection list =
            QueryServiceHelper.query("aos_mkt_specategory", "aos_category1,aos_category2,aos_category3",
                new QFilter[] {new QFilter("aos_orgid", QCP.equals, aosOrgid)});
        return list.stream().map(obj -> obj.getString("aos_category1") + "~" + obj.getString("aos_category2") + "~"
            + obj.getString("aos_category3")).collect(Collectors.toSet());
    }

    private static HashMap<String, Map<String, Object>> generateItem(Object pOrgId) {
        HashMap<String, Map<String, Object>> itemMap = new HashMap<>(16);
        QFilter filterOu = new QFilter("aos_contryentry.aos_nationality.id", "=", pOrgId);
        QFilter filterType = new QFilter("aos_protype", "=", "N");
        QFilter[] filters = new QFilter[] {filterOu, filterType};
        String selectField = "id,aos_productno," + "number," + "aos_cn_name,"
            + "aos_contryentry.aos_nationality.id aos_orgid," + "aos_contryentry.aos_nationality.number aos_orgnumber,"
            + "aos_contryentry.aos_seasonseting.number aos_seasonseting,"
            + "aos_contryentry.aos_seasonseting.name aos_season," + "aos_contryentry.aos_seasonseting.id aos_seasonid,"
            + "aos_contryentry.aos_contryentrystatus aos_contryentrystatus,"
            + "aos_contryentry.aos_festivalseting.number aos_festivalseting,"
            + "aos_contryentry.aos_firstindate aos_firstindate";
        DynamicObjectCollection bdMaterialS =
            QueryServiceHelper.query("bd_material", selectField, filters, "aos_productno");
        for (DynamicObject bdMaterial : bdMaterialS) {
            String aosItemid = bdMaterial.getString("id");
            Map<String, Object> info = itemMap.get(aosItemid);
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put("aos_contryentrystatus", bdMaterial.get("aos_contryentrystatus"));
            info.put("aos_firstindate", bdMaterial.get("aos_firstindate"));
            info.put("aos_seasonseting", bdMaterial.get("aos_seasonseting"));
            itemMap.put(aosItemid, info);
        }
        return itemMap;
    }

    private static Set<String> generateCategorySet(Object pOrgId) {
        DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_category", "aos_category1,aos_category2",
            new QFilter[] {new QFilter("aos_orgid", QCP.equals, pOrgId)});
        return list.stream().map(obj -> obj.getString("aos_category1") + "~" + obj.getString("aos_category2"))
            .collect(Collectors.toSet());
    }

    private static Set<String> generateSeaSet(Object pOrgId, Date today) {
        DynamicObjectCollection list = QueryServiceHelper.query("aos_sal_quo_sa", "aos_entryentity.aos_sku.id aos_sku",
            new QFilter("aos_org", QCP.equals, pOrgId).and("aos_sche_date", QCP.large_than, today)
                .and("aos_entryentity.aos_adjtype", QCP.equals, "降价").toArray());
        return list.stream().map(obj -> obj.getString("aos_sku")).collect(Collectors.toSet());
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        CommonDataSom.init();
        AosMktCacheUtil.initRedis("ppc");
        run();
    }
}