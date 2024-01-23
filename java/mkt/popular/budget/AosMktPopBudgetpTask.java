package mkt.popular.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import common.CommonDataSom;
import org.apache.commons.lang3.SerializationUtils;

import common.Cux_Common_Utl;
import common.fnd.FndDate;
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
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.exception.KDException;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.threads.ThreadPools;
import mkt.common.aos_mkt_common_redis;

/**
 * @author aosom
 * @version 预算调整-调度任务类
 */
public class AosMktPopBudgetpTask extends AbstractTask {
    public final static String SYSTEM = getSystemId();
    private static final DistributeSessionlessCache CACHE =
        CacheFactory.getCommonCacheFactory().getDistributeSessionlessCache("mkt_redis");
    private static final Log logger = LogFactory.getLog(AosMktPopBudgetpTask.class);

    public static Object getImportId2(Object code, String bill) {
        try {
            Object id;
            QFilter filter = new QFilter("name", "=", code);
            QFilter[] filters = new QFilter[] {filter};
            String selectFields = "id";
            DynamicObject dynamicObject = QueryServiceHelper.queryOne(bill, selectFields, filters);
            if (dynamicObject == null) {
                id = null;
            } else {
                id = dynamicObject.getLong("id");
            }
            return id;
        } catch (Exception e) {
            return null;
        }
    }

    public static String getSystemId() {
        QFilter qfUser = new QFilter("number", "=", "system");
        DynamicObject dyUser = QueryServiceHelper.queryOne("bos_user", "id", new QFilter[] {qfUser});
        return dyUser.get("id").toString();
    }

    public static void manualitemClick(Map<String, Object> param) {
        executerun(param);
    }

    public static void executerun(Map<String, Object> param) {
        // 初始化数据
        Object pOuCode = param.get("p_ou_code");
        QFilter overseaFlag = new QFilter("aos_is_oversea_ou.number", "=", "Y");
        QFilter qfOrg = new QFilter("number", "=", pOuCode);
        QFilter[] filtersOu = new QFilter[] {overseaFlag, qfOrg};
        DynamicObjectCollection bdCountry = QueryServiceHelper.query("bd_country", "id,number", filtersOu);
        for (DynamicObject ou : bdCountry) {
            logger.info("MKT_PPC预算调整_" + pOuCode);
            Map<String, Object> params = new HashMap<>(16);
            params.put("p_ou_code", ou.getString("number"));
            MktPopBudpRunnable mktPopBudpRunnable = new MktPopBudpRunnable(params);
            ThreadPools.executeOnce("MKT_PPC预算调整_" + pOuCode, mktPopBudpRunnable);
        }
    }

    public static void doOperate(Map<String, Object> params) {
        logger.info("===== into aos_mkt_popbudgetp_init =====");
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String pOuCode = (String)params.get("p_ou_code");
        Object pOrgId = FndGlobal.get_import_id(pOuCode, "bd_country");
        // SKU报告系列7日
        byte[] serializeSkurptSerial7 = CACHE.getByteValue("mkt_skurptSerial7");
        HashMap<String, Map<String, Object>> skuRptSerial = SerializationUtils.deserialize(serializeSkurptSerial7);
        // 营销国别参数表
        byte[] serializePoporgInfo = CACHE.getByteValue("mkt_poporginfo");
        HashMap<String, Map<String, Object>> popOrgInfo = SerializationUtils.deserialize(serializePoporgInfo);
        // serializePoporgInfo
        byte[] serializeSkurptdetailSerial = CACHE.getByteValue("mkt_skurptDetailSerial");
        HashMap<String, Map<String, Object>> skuRptDetailSerial =
            SerializationUtils.deserialize(serializeSkurptdetailSerial);
        // PPC昨日数据
        byte[] serializePpcYesterSerial = CACHE.getByteValue("mkt_ppcyestSerial");
        HashMap<String, Map<String, Object>> ppcYesterSerial = SerializationUtils.deserialize(serializePpcYesterSerial);
        // PPC7日
        byte[] serializePpcyster7Serial = CACHE.getByteValue("mkt_ppcyster7Serial");
        HashMap<String, Map<String, Map<String, Object>>> ppcYsterSerial7 =
            SerializationUtils.deserialize(serializePpcyster7Serial);
        // SKU报告系列近3日
        byte[] serializeSkurpt7Serial = CACHE.getByteValue("mkt_skurpt7Serial");
        HashMap<String, Map<String, Map<String, Object>>> skuRpt7Serial =
            SerializationUtils.deserialize(serializeSkurpt7Serial);
        logger.info("===== end redis =====");
        // 国别标准ROI
        BigDecimal popOrgRoist = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "ROIST").get("aos_value");
        // 很差
        BigDecimal worst = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "WORST").get("aos_value");
        // 标准
        BigDecimal standard = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "STANDARD").get("aos_value");
        // 好
        BigDecimal well = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "WELL").get("aos_value");
        // 优
        BigDecimal excellent = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "EXCELLENT").get("aos_value");
        Date today = FndDate.zero(new Date());
        QFilter filterId = new QFilter("aos_orgid", "=", pOrgId);
        QFilter filterDate = new QFilter("aos_makedate", "=", today);
        QFilter[] filtersAdj = new QFilter[] {filterId, filterDate};
        DeleteServiceHelper.delete("aos_mkt_pop_budgetp", filtersAdj);
        DeleteServiceHelper.delete("aos_mkt_popbudget_data", filtersAdj);
        HashMap<String, BigDecimal> adjsMap = initAdjsMap(pOuCode, today);
        QFilter qfDate = new QFilter("aos_date", "=", today);
        QFilter qfOrg = new QFilter("aos_orgid.number", "=", pOuCode);
        QFilter qfType = new QFilter("aos_entryentity.aos_groupstatus", "=", "AVAILABLE");
        Map<String, Object> cateSeason = getCateSeason(pOuCode);
        // 设置出价调整日期Start
        QFilter[] filters = new QFilter[] {qfDate, qfOrg};
        String select = "id";
        DynamicObject ppc = QueryServiceHelper.queryOne("aos_mkt_popular_ppc", select, filters);
        long fid = ppc.getLong("id");
        ppc = BusinessDataServiceHelper.loadSingle(fid, "aos_mkt_popular_ppc");
        DynamicObjectCollection aosBidDiff = ppc.getDynamicObjectCollection("aos_entryentity");
        for (DynamicObject d : aosBidDiff) {
            BigDecimal aosBid = d.getBigDecimal("aos_bid");
            BigDecimal aosBidOri = d.getBigDecimal("aos_bid_ori");
            if (aosBid.compareTo(aosBidOri) != 0) {
                d.set("aos_lastpricedate", today);
            }
        }
        OperationServiceHelper.executeOperate("save", "aos_mkt_popular_ppc", new DynamicObject[] {ppc},
            OperateOption.create());
        // 设置出价调整日期 End
        filters = new QFilter[] {qfDate, qfOrg, qfType};
        String selectField = "1 aos_skucount,aos_billno,aos_orgid,aos_channel,aos_poptype,"
            + "aos_orgid.number aos_orgnumber," + "aos_entryentity.aos_productno aos_productno,"
            + " aos_entryentity.aos_category1 aos_category1 , aos_entryentity.aos_category2 aos_category2,"
            + "aos_entryentity.aos_seasonseting aos_seasonseting," + "aos_entryentity.aos_budget aos_budget,"
            + "id, case when aos_entryentity.aos_salemanual = 'Y' then 1 "
            + "  when aos_entryentity.aos_salemanual = 'N' then 0 end as aos_salemanual";
        DataSet aosMktPopularPpcS = QueryServiceHelper.queryDataSet("aos_mkt_popbudgetp_init" + "." + pOuCode,
            "aos_mkt_popular_ppc", selectField, filters, null);
        String[] groupBy = new String[] {"aos_orgid", "aos_productno", "aos_seasonseting", "aos_billno", "aos_channel",
            "aos_poptype", "aos_orgnumber", "aos_budget", "id", "aos_category1", "aos_category2"};
        aosMktPopularPpcS = aosMktPopularPpcS.groupBy(groupBy).sum("aos_skucount").max("aos_salemanual").finish();
        int count = 0;
        DynamicObject aosMktPopbudgetData = BusinessDataServiceHelper.newDynamicObject("aos_mkt_popbudget_data");
        DynamicObjectCollection aosEntryentityS = aosMktPopbudgetData.getDynamicObjectCollection("aos_entryentity");
        while (aosMktPopularPpcS.hasNext()) {
            Row aosMktPopularPpc = aosMktPopularPpcS.next();
            count++;
            if (count == 1) {
                // 初始化 预算调整(推广)
                DynamicObject aosMktPopBudgetp = BusinessDataServiceHelper.newDynamicObject("aos_mkt_pop_budgetp");
                aosMktPopBudgetp.set("aos_billno", aosMktPopularPpc.get("aos_billno"));
                aosMktPopBudgetp.set("aos_orgid", pOrgId);
                aosMktPopBudgetp.set("billstatus", "A");
                aosMktPopBudgetp.set("aos_status", "A");
                aosMktPopBudgetp.set("aos_ppcid", aosMktPopularPpc.get("id"));
                aosMktPopBudgetp.set("aos_makeby", SYSTEM);
                aosMktPopBudgetp.set("aos_makedate", today);
                OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_pop_budgetp",
                    new DynamicObject[] {aosMktPopBudgetp}, OperateOption.create());
                Object aosSourceid = operationrst.getSuccessPkIds().get(0);
                // 初始化 预算调整(推广)数据表
                aosMktPopbudgetData.set("aos_billno", aosMktPopularPpc.get("aos_billno"));
                aosMktPopbudgetData.set("billno", aosMktPopularPpc.get("aos_billno"));
                aosMktPopbudgetData.set("aos_orgid", pOrgId);
                aosMktPopbudgetData.set("billstatus", "A");
                aosMktPopbudgetData.set("aos_sourceid", aosSourceid);
                aosMktPopbudgetData.set("aos_makedate", today);
            }
            Object aosChannel = aosMktPopularPpc.get("aos_channel");
            Object aosPoptype = aosMktPopularPpc.get("aos_poptype");
            String aosProductno = aosMktPopularPpc.getString("aos_productno");
            Object aosSeasonseting = aosMktPopularPpc.get("aos_seasonseting");
            Object aosSkucount = aosMktPopularPpc.get("aos_skucount");
            Object aosSalemanual = aosMktPopularPpc.get("aos_salemanual");
            BigDecimal aosBudget = aosMktPopularPpc.getBigDecimal("aos_budget");
            Object aosSeasonsetingid = getImportId2(aosSeasonseting, "aos_scm_seasonatt");
            // 7天ROI 系列维度
            BigDecimal roi7DaysSerial = BigDecimal.ZERO;
            // Sku报告
            Map<String, Object> skuRptMapSerial = skuRptSerial.get(pOrgId + "~" + aosProductno);
            // 默认昨日数据
            Map<String, Object> ppcYesterSerialMap = ppcYesterSerial.get(pOrgId + "~" + aosProductno);
            if (skuRptMapSerial != null
                && ((BigDecimal)skuRptMapSerial.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                roi7DaysSerial = ((BigDecimal)skuRptMapSerial.get("aos_total_sales"))
                    .divide((BigDecimal)skuRptMapSerial.get("aos_spend"), 6, RoundingMode.HALF_UP);
            }
            String aosRoitype = null;
            if (roi7DaysSerial.compareTo(BigDecimal.ZERO) != 0) {
                aosRoitype = getRoiType(popOrgRoist, worst, standard, well, excellent, roi7DaysSerial);
            }
            // 花出率 =报告中花费/预算
            BigDecimal costRate = BigDecimal.ZERO;
            BigDecimal lastSpend = BigDecimal.ZERO;
            BigDecimal lastBudget = BigDecimal.ZERO;
            // Sku报告1日数据
            Object lastSpendObj = skuRptDetailSerial.get(pOrgId + "~" + aosMktPopularPpc.get("aos_productno"));
            if (!Cux_Common_Utl.IsNull(lastSpendObj)) {
                lastSpend = (BigDecimal)lastSpendObj;
            }
            if (ppcYesterSerialMap != null) {
                // 昨日预算
                lastBudget = (BigDecimal)ppcYesterSerialMap.get("aos_budget");
                if (lastBudget.compareTo(BigDecimal.ZERO) != 0) {
                    costRate = lastSpend.divide(lastBudget, 2, RoundingMode.HALF_UP);
                }
            }
            DynamicObject aosEntryentity = aosEntryentityS.addNew();
            aosEntryentity.set("aos_channel_r", aosChannel);
            aosEntryentity.set("aos_poptype_r", aosPoptype);
            aosEntryentity.set("aos_serialname", aosProductno);
            aosEntryentity.set("aos_season_r", aosSeasonsetingid);
            aosEntryentity.set("aos_roi7days", roi7DaysSerial);
            aosEntryentity.set("aos_roitype", aosRoitype);
            aosEntryentity.set("aos_skucount", aosSkucount);
            aosEntryentity.set("aos_lastrate", costRate);
            aosEntryentity.set("aos_lastspend_r", lastSpend);
            aosEntryentity.set("aos_lastbudget", lastBudget);
            aosEntryentity.set("aos_calbudget", aosBudget);
            String aosCategory1 = aosMktPopularPpc.getString("aos_category1");
            String aosCategory2 = aosMktPopularPpc.getString("aos_category2");
            Object aosSeasonattr = cateSeason.getOrDefault(aosCategory1 + "~" + aosCategory2, null);
            aosEntryentity.set("aos_seasonattr", aosSeasonattr);
            if ((int)aosSalemanual == 1) {
                aosEntryentity.set("aos_saldescription", "销售手调");
            }
            // 系列获取 出价调整 金额
            BigDecimal adjsBid = BigDecimal.ZERO;
            if (adjsMap.get(aosProductno) != null) {
                adjsBid = adjsMap.get(aosProductno);
            }
            aosEntryentity.set("aos_adjbudget", adjsBid);
            aosEntryentity.set("aos_popbudget", max(aosBudget, adjsBid));
            BigDecimal aosLastbudget = aosEntryentity.getBigDecimal("aos_lastbudget");
            BigDecimal aosCalbudget = aosEntryentity.getBigDecimal("aos_calbudget");
            if (aosLastbudget.compareTo(BigDecimal.ZERO) != 0 && aosCalbudget.compareTo(aosLastbudget) > 0) {
                aosEntryentity.set("aos_sys", "加预算");
            }
            if (aosCalbudget.compareTo(aosLastbudget) < 0) {
                aosEntryentity.set("aos_sys", "减预算");
            }
        }
        aosMktPopularPpcS.close();
        // 子表初始化
        for (DynamicObject aosEntryentity : aosEntryentityS) {
            try {
                String aosSerialname = aosEntryentity.getString("aos_serialname");
                Map<String, Map<String, Object>> ppcYster7SerialMap = ppcYsterSerial7.get(pOrgId + "~" + aosSerialname);
                Map<String, Map<String, Object>> skuRpt7SerialMap = skuRpt7Serial.get(pOrgId + "~" + aosSerialname);
                if (ppcYster7SerialMap == null) {
                    continue;
                }
                DynamicObjectCollection aosSubentryentityS =
                    aosEntryentity.getDynamicObjectCollection("aos_subentryentity");
                Map<String, Object> skuRpt7SerialMapD = new HashMap<>(16);
                // 循环ROI
                for (String dateStr : ppcYster7SerialMap.keySet()) {
                    Date aosDateD = df.parse(dateStr);
                    Map<String, Object> ppcYster7MapD = ppcYster7SerialMap.get(dateStr);
                    BigDecimal aosBudget = BigDecimal.ZERO;
                    Object aosSkucount = null;
                    if (ppcYster7MapD != null) {
                        aosBudget = (BigDecimal)ppcYster7MapD.get("aos_budget");
                        aosSkucount = ppcYster7MapD.get("aos_skucount");
                    }
                    BigDecimal aosRoiD = BigDecimal.ZERO;
                    BigDecimal aosSpendD = BigDecimal.ZERO;
                    BigDecimal aosSpendrateD = BigDecimal.ZERO;
                    if (skuRpt7SerialMap != null) {
                        skuRpt7SerialMapD = skuRpt7SerialMap.get(dateStr);
                    }
                    if (skuRpt7SerialMapD != null) {
                        aosRoiD = (BigDecimal)skuRpt7SerialMapD.get("aos_roi");
                        aosSpendD = (BigDecimal)skuRpt7SerialMapD.get("aos_spend");
                        if (aosSpendD != null && aosBudget.compareTo(BigDecimal.ZERO) != 0) {
                            aosSpendrateD = aosSpendD.divide(aosBudget, 2, RoundingMode.HALF_UP);
                        }
                    }
                    DynamicObject aosSubentryentity = aosSubentryentityS.addNew();
                    aosSubentryentity.set("aos_date_d", aosDateD);
                    aosSubentryentity.set("aos_budget_d", aosBudget);
                    aosSubentryentity.set("aos_skucount_d", aosSkucount);
                    aosSubentryentity.set("aos_roi_d", aosRoiD);
                    aosSubentryentity.set("aos_spend_d", aosSpendD);
                    aosSubentryentity.set("aos_spendrate_d", aosSpendrateD);
                }
            } catch (Exception ex) {
                String message = ex.toString();
                String exceptionStr = SalUtil.getExceptionStr(ex);
                String messageStr = message + "\r\n" + exceptionStr;
                logger.error(messageStr);
            }
        }
        OperationServiceHelper.executeOperate("save", "aos_mkt_popbudget_data",
            new DynamicObject[] {aosMktPopbudgetData}, OperateOption.create());
    }

    private static Map<String, Object> getCateSeason(Object pOuCode) {
        HashMap<String, Object> cateSeason = new HashMap<>(16);
        DynamicObjectCollection aosMktCateseasonS =
            QueryServiceHelper.query("aos_mkt_cateseason", "aos_category1," + "aos_category2,aos_festname",
                new QFilter("aos_startdate", QCP.large_equals, FndDate.add_days(new Date(), 7))
                    .and("aos_endate", QCP.less_equals, FndDate.add_days(new Date(), 7))
                    .and("aos_orgid.number", QCP.equals, pOuCode).toArray());
        for (DynamicObject aosMktCateseason : aosMktCateseasonS) {
            String aosCategory1 = aosMktCateseason.getString("aos_category1");
            String aosCategory2 = aosMktCateseason.getString("aos_category2");
            String aosFestname = aosMktCateseason.getString("aos_festname");
            Object lastObject = cateSeason.get("aos_category1" + "~" + "aos_category2");
            if (FndGlobal.IsNull(lastObject)) {
                cateSeason.put(aosCategory1 + "~" + aosCategory2, aosFestname);
            } else {
                cateSeason.put(aosCategory1 + "~" + aosCategory2, lastObject + "/" + aosFestname);
            }
        }
        return cateSeason;
    }

    private static HashMap<String, BigDecimal> initAdjsMap(String pOuCode, Date today) {
        HashMap<String, BigDecimal> initAdjs = new HashMap<>(16);
        String selectColumn = "aos_entryentity.aos_bid * 20 aos_bid," + "aos_entryentity.aos_productno aos_productno";
        QFilter qfDate = new QFilter("aos_date", "=", today);
        QFilter filterBill = new QFilter("aos_orgid.number", "=", pOuCode);
        QFilter[] filters = new QFilter[] {qfDate, filterBill};
        DataSet aosMktPopularPpcS = QueryServiceHelper.queryDataSet("aos_mkt_popbudgetp_init" + "." + "InitAdjsMap",
            "aos_mkt_popular_ppc", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_productno"};
        aosMktPopularPpcS = aosMktPopularPpcS.groupBy(groupBy).sum("aos_bid").finish();
        while (aosMktPopularPpcS.hasNext()) {
            Row aosMktPopularPpc = aosMktPopularPpcS.next();
            initAdjs.put(aosMktPopularPpc.getString("aos_productno"), aosMktPopularPpc.getBigDecimal("aos_bid"));
        }
        aosMktPopularPpcS.close();
        return initAdjs;
    }

    private static String getRoiType(BigDecimal popOrgRoist, BigDecimal worst, BigDecimal standard, BigDecimal well,
        BigDecimal excellent, BigDecimal roi7DaysSerial) {
        String roiType;
        int i = roi7DaysSerial.compareTo(popOrgRoist.add(worst));
        if (i < 0) {
            roiType = "WORST";
        } else {
            int i1 = roi7DaysSerial.compareTo(popOrgRoist.add(standard));
            if (i1 < 0) {
                roiType = "WORRY";
            } else {
                int i2 = roi7DaysSerial.compareTo(popOrgRoist.add(well));
                if (i2 < 0) {
                    roiType = "STANDARD";
                } else {
                    int i3 = roi7DaysSerial.compareTo(popOrgRoist.add(excellent));
                    if (i3 < 0) {
                        roiType = "WELL";
                    } else {
                        roiType = "EXCELLENT";
                    }
                }
            }
        }
        return roiType;
    }

    private static BigDecimal max(BigDecimal value1, BigDecimal value2) {
        if (value1.compareTo(value2) > -1) {
            return value1;
        } else {
            return value2;
        }
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        // 多线程执行
        executerun(param);
    }

    static class MktPopBudpRunnable implements Runnable {
        private Map<String, Object> params = new HashMap<>();

        public MktPopBudpRunnable(Map<String, Object> param) {
            this.params = param;
        }

        @Override
        public void run() {
            try {
                CommonDataSom.init();
                aos_mkt_common_redis.init_redis("ppcbudget_p");
                doOperate(params);
            } catch (Exception e) {
                String message = e.toString();
                String exceptionStr = SalUtil.getExceptionStr(e);
                String messageStr = message + "\r\n" + exceptionStr;
                logger.error(messageStr);
            }
        }
    }
}