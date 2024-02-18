package mkt.popularst.budget;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import common.CommonDataSom;
import common.Cux_Common_Utl;
import common.fnd.FndDate;
import common.fnd.FndGlobal;
import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.exception.KDException;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.threads.ThreadPools;
import mkt.common.AosMktGenUtil;

/**
 * @author aosom
 * @version ST预算调整-调度任务类
 */
public class AosMktPopBudpstTask extends AbstractTask {
    public final static String SYSTEM = getSystemId();
    private static final Log logger = LogFactory.getLog(AosMktPopBudpstTask.class);

    public static String getSystemId() {
        QFilter qfUser = new QFilter("number", "=", "system");
        DynamicObject dyUser = QueryServiceHelper.queryOne("bos_user", "id", new QFilter[] {qfUser});
        return dyUser.get("id").toString();
    }

    public static void executerun(Map<String, Object> param) {
        // 初始化数据
        Object pOuCode = param.get("p_ou_code");
        QFilter overseaFlag = new QFilter("aos_is_oversea_ou.number", "=", "Y");
        QFilter qfOrg = new QFilter("number", "=", pOuCode);
        QFilter[] filtersOu = new QFilter[] {overseaFlag, qfOrg};
        DynamicObjectCollection bdCountry = QueryServiceHelper.query("bd_country", "id,number", filtersOu);
        for (DynamicObject ou : bdCountry) {
            logger.info("MKT_PPC预算调整ST_" + pOuCode);
            Map<String, Object> params = new HashMap<>(16);
            params.put("p_ou_code", ou.getString("number"));
            MktPopBudpStRunnable mktPopBudpStRunnable = new MktPopBudpStRunnable(params);
            ThreadPools.executeOnce("MKT_PPC预算调整ST_" + pOuCode, mktPopBudpStRunnable);
        }
    }

    public static void doOperate(Map<String, Object> params) {
        Cux_Common_Utl.Log(logger, "===== into aos_mkt_popbudgetp_init =====");
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String pOuCode = (String)params.get("p_ou_code");
        Object pOrgId = FndGlobal.get_import_id(pOuCode, "bd_country");
        // 删除数据
        Date today = FndDate.zero(new Date());
        QFilter filterId = new QFilter("aos_orgid", "=", pOrgId);
        QFilter filterDate = new QFilter("aos_makedate", "=", today);
        QFilter[] filtersAdj = new QFilter[] {filterId, filterDate};
        DeleteServiceHelper.delete("aos_mkt_pop_budgetst", filtersAdj);
        DeleteServiceHelper.delete("aos_mkt_popbudget_stdt", filtersAdj);
        // 初始化数据
        // 营销国别标准参数
        HashMap<String, Map<String, Object>> popOrgInfo = AosMktGenUtil.generatePopOrgInfo(pOuCode);
        // 7日关键词报告系列
        HashMap<String, Map<String, Object>> skuRpt7Serial = AosMktGenUtil.generateSkuRptSerial(pOuCode);
        // 今日系列维度关键词个数
        HashMap<String, Object> ppcTodaySerial = AosMktGenUtil.generatePpcTodayStSerial(pOuCode);
        // ST数据源昨日系列预算
        HashMap<String, Object> ppcYesterSerial = AosMktGenUtil.generatePpcYeStSerial(pOuCode);
        HashMap<String, Map<String, Object>> skuRptDetailSerial = AosMktGenUtil.generateSkuRptDetailSerial(pOuCode);
        HashMap<String, Object> ppcTodaySerialGroup = AosMktGenUtil.generatePpcTodayStSerialGroup(pOuCode);
        HashMap<String, BigDecimal> adjsMap = initAdjsMap(pOuCode, today);
        HashMap<String, Map<String, Map<String, Object>>> skuRpt7SerialD =
            AosMktGenUtil.generateSkuRpt7Serial(pOuCode);
        HashMap<String, Map<String, Map<String, Object>>> ppcYsterSerial7 =
            AosMktGenUtil.generateYesterStSerial7D(pOuCode);
        HashMap<String, Map<String, Object>> ppcYsterSerialG = AosMktGenUtil.generateYesterStSerial7G(pOuCode);
        // 国别标准ROI
        BigDecimal popOrgRoist = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "ROIST").get("aos_value");
        // 很差
        BigDecimal worst = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "WORST").get("aos_value");
        // 差
        BigDecimal worry = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "WORRY").get("aos_value");
        // 标准
        BigDecimal standard = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "STANDARD").get("aos_value");
        // 好
        BigDecimal well = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "WELL").get("aos_value");
        // 优
        BigDecimal excellent = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "EXCELLENT").get("aos_value");
        QFilter qfDate = new QFilter("aos_date", "=", today);
        QFilter qfOrg = new QFilter("aos_orgid.number", "=", pOuCode);
        // 设置出价调整日期Start
        QFilter[] filters = new QFilter[] {qfDate, qfOrg};
        String select = "id";
        DynamicObject ppc = QueryServiceHelper.queryOne("aos_mkt_pop_ppcst", select, filters);
        long fid = ppc.getLong("id");
        ppc = BusinessDataServiceHelper.loadSingle(fid, "aos_mkt_pop_ppcst");
        DynamicObjectCollection aosBidDiff = ppc.getDynamicObjectCollection("aos_entryentity");
        for (DynamicObject d : aosBidDiff) {
            BigDecimal aosBid = d.getBigDecimal("aos_bid");
            BigDecimal aosBidOri = d.getBigDecimal("aos_bid_ori");
            if (aosBid.compareTo(aosBidOri) != 0) {
                d.set("aos_lastpricedate", today);
            }
        }
        OperationServiceHelper.executeOperate("save", "aos_mkt_pop_ppcst", new DynamicObject[] {ppc},
            OperateOption.create());
        // 开始循环今日ST数据源数据
        filters = new QFilter[] {qfDate, qfOrg};
        String selectField = "aos_sourceid id,aos_billno,aos_channel,aos_productno," + "aos_season,aos_budget,"
            + " case when aos_salemanual = 'Y' then 1 " + " when aos_salemanual = 'N' then 0 end as aos_salemanual ";
        DataSet aosMktPopularPpcS = QueryServiceHelper.queryDataSet("aos_mkt_popbudpst_init" + "." + pOuCode,
            "aos_mkt_ppcst_data", selectField, filters, null);
        String[] groupBy =
            new String[] {"id", "aos_billno", "aos_channel", "aos_productno", "aos_season", "aos_budget"};
        aosMktPopularPpcS = aosMktPopularPpcS.groupBy(groupBy).max("aos_salemanual").finish();
        int count = 0;
        DynamicObject aosMktPopbudgetData = BusinessDataServiceHelper.newDynamicObject("aos_mkt_popbudget_stdt");
        DynamicObjectCollection aosEntryentityS = aosMktPopbudgetData.getDynamicObjectCollection("aos_budgetentry");
        while (aosMktPopularPpcS.hasNext()) {
            Row aosMktPopularPpc = aosMktPopularPpcS.next();
            count++;
            if (count == 1) {
                // 初始化 预算调整(推广)
                DynamicObject aosMktPopBudgetp = BusinessDataServiceHelper.newDynamicObject("aos_mkt_pop_budgetst");
                aosMktPopBudgetp.set("aos_billno", aosMktPopularPpc.get("aos_billno"));
                aosMktPopBudgetp.set("aos_orgid", pOrgId);
                aosMktPopBudgetp.set("billstatus", "A");
                aosMktPopBudgetp.set("aos_status", "A");
                aosMktPopBudgetp.set("aos_ppcid", aosMktPopularPpc.get("id"));
                aosMktPopBudgetp.set("aos_makeby", SYSTEM);
                aosMktPopBudgetp.set("aos_makedate", today);
                OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_pop_budgetst",
                    new DynamicObject[] {aosMktPopBudgetp}, OperateOption.create());
                Object aosSourceid = operationrst.getSuccessPkIds().get(0);
                // 初始化 预算调整(推广)数据表
                aosMktPopbudgetData.set("aos_billno", aosMktPopularPpc.get("aos_billno"));
                aosMktPopbudgetData.set("billno", aosMktPopularPpc.get("aos_billno"));
                aosMktPopbudgetData.set("aos_orgid", pOrgId);
                aosMktPopbudgetData.set("billstatus", "A");
                aosMktPopbudgetData.set("aos_billstatus", "A");
                aosMktPopbudgetData.set("aos_sourceid", aosSourceid);
                aosMktPopbudgetData.set("aos_makedate", today);
            }
            Object aosChannel = aosMktPopularPpc.get("aos_channel");
            String aosProductno = aosMktPopularPpc.getString("aos_productno");
            Object aosSeasonseting = aosMktPopularPpc.get("aos_season");
            BigDecimal aosBudget = aosMktPopularPpc.getBigDecimal("aos_budget");
            DynamicObject aosEntryentity = aosEntryentityS.addNew();
            aosEntryentity.set("aos_channel_r", aosChannel);
            aosEntryentity.set("aos_poptype_r", "ST");
            aosEntryentity.set("aos_serialname", aosProductno);
            aosEntryentity.set("aos_season_r", aosSeasonseting);
            aosEntryentity.set("aos_skucount", ppcTodaySerialGroup.get(aosProductno));
            aosEntryentity.set("aos_calbudget", aosBudget);
            // 系列获取 出价调整 金额
            BigDecimal adjsBid = BigDecimal.ZERO;
            if (adjsMap.get(aosProductno) != null) {
                adjsBid = adjsMap.get(aosProductno);
            }
            aosEntryentity.set("aos_adjbudget", adjsBid);
            aosEntryentity.set("aos_popbudget", Cux_Common_Utl.Max(aosBudget, adjsBid));
            int aosSalemanual = aosMktPopularPpc.getInteger("aos_salemanual");
            if (aosSalemanual == 1) {
                aosEntryentity.set("aos_saldescription", "销售手调");
            }
            // 关键词报告
            Map<String, Object> skuRpt7SerialMap = skuRpt7Serial.get(aosProductno);
            // 7天ROI系列
            BigDecimal aosRoi7days = BigDecimal.ZERO;
            BigDecimal aosSales;
            BigDecimal aosSpend;
            if (skuRpt7SerialMap != null) {
                aosSales = (BigDecimal)skuRpt7SerialMap.get("aos_total_sales");
                aosSpend = (BigDecimal)skuRpt7SerialMap.get("aos_spend");
                if (aosSales != null && aosSpend.compareTo(BigDecimal.ZERO) != 0) {
                    aosRoi7days = (aosSales.divide(aosSpend, 2, RoundingMode.HALF_UP));
                }
            }
            String aosRoitype = getRoiType(popOrgRoist, worst, worry, standard, well, excellent, aosRoi7days);
            aosEntryentity.set("aos_roi7days", aosRoi7days);
            aosEntryentity.set("aos_roitype", aosRoitype);
            aosEntryentity.set("aos_kwcount", ppcTodaySerial.get(aosProductno));
            // Sku报告1日数据
            Map<String, Object> skuRptDetailSerialMap = skuRptDetailSerial.get(aosProductno);
            // 花出率 =报告中花费/预算
            BigDecimal costRate = BigDecimal.ZERO;
            BigDecimal lastSpend = BigDecimal.ZERO;
            BigDecimal lastBudget = BigDecimal.ZERO;
            if (ppcYesterSerial.get(aosProductno) != null) {
                lastBudget = (BigDecimal)ppcYesterSerial.get(aosProductno);
            }
            // 系列花出率 = 前一天花出/预算
            if (skuRptDetailSerialMap != null) {
                lastSpend = (BigDecimal)skuRptDetailSerialMap.get("aos_spend");
                if (lastBudget.compareTo(BigDecimal.ZERO) != 0) {
                    costRate = lastSpend.divide(lastBudget, 2, RoundingMode.HALF_UP);
                }
            }
            aosEntryentity.set("aos_lastrate", costRate);
            aosEntryentity.set("aos_lastspend_r", lastSpend);
            aosEntryentity.set("aos_lastbudget", lastBudget);
        }

        // 子表初始化
        for (DynamicObject aosEntryentity : aosEntryentityS) {
            try {
                String aosSerialname = aosEntryentity.getString("aos_serialname");
                // PPC7日
                Map<String, Map<String, Object>> ppcYster7SerialMap = ppcYsterSerial7.get(aosSerialname);
                // PPC7日
                Map<String, Object> ppcYster7SerialgMap = ppcYsterSerialG.get(aosSerialname);
                // PPC7日
                Map<String, Map<String, Object>> skuRpt7SerialMap = skuRpt7SerialD.get(aosSerialname);
                if (ppcYster7SerialMap == null) {
                    continue;
                }
                if (ppcYster7SerialgMap == null) {
                    continue;
                }
                DynamicObjectCollection aosSubentryentityS =
                    aosEntryentity.getDynamicObjectCollection("aos_detailentry");
                Map<String, Object> skuRpt7SerialMapD = new HashMap<>(16);
                // 循环ROI
                for (String dateStr : ppcYster7SerialMap.keySet()) {
                    Date aosDateD = df.parse(dateStr);
                    Map<String, Object> ppcYster7MapD = ppcYster7SerialMap.get(dateStr);
                    Object aosSkucount = ppcYster7SerialgMap.get(dateStr);
                    BigDecimal aosBudget = BigDecimal.ZERO;
                    Object aosKeycount = null;
                    if (ppcYster7MapD != null) {
                        aosBudget = (BigDecimal)ppcYster7MapD.get("aos_budget");
                        aosKeycount = ppcYster7MapD.get("aos_keycount");
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
                    aosSubentryentity.set("aos_roi_d", aosRoiD);
                    aosSubentryentity.set("aos_spend_d", aosSpendD);
                    aosSubentryentity.set("aos_spendrate_d", aosSpendrateD);
                    aosSubentryentity.set("aos_budget_d", aosBudget);
                    aosSubentryentity.set("aos_kwcount_d", aosKeycount);
                    aosSubentryentity.set("aos_skucount_d", aosSkucount);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                String message = ex.toString();
                String exceptionStr = SalUtil.getExceptionStr(ex);
                String messageStr = message + "\r\n" + exceptionStr;
                logger.error(messageStr);
            }
        }
        OperationServiceHelper.executeOperate("save", "aos_mkt_popbudget_stdt",
            new DynamicObject[] {aosMktPopbudgetData}, OperateOption.create());
        aosMktPopularPpcS.close();
    }

    private static String getRoiType(BigDecimal popOrgRoist, BigDecimal worst, BigDecimal worry, BigDecimal standard,
        BigDecimal well, BigDecimal excellent, BigDecimal roi7DaysSerial) {
        String roiType = null;
        if (roi7DaysSerial.compareTo(popOrgRoist.add(worst)) < 0) {
            roiType = "WORST";
        } else {
            int i = roi7DaysSerial.compareTo(popOrgRoist.add(standard));
            if (roi7DaysSerial.compareTo(popOrgRoist.add(worry)) >= 0 && i < 0) {
                roiType = "WORRY";
            } else {
                int i1 = roi7DaysSerial.compareTo(popOrgRoist.add(well));
                if (i >= 0 && i1 < 0) {
                    roiType = "STANDARD";
                } else {
                    int i2 = roi7DaysSerial.compareTo(popOrgRoist.add(excellent));
                    if (i1 >= 0 && i2 < 0) {
                        roiType = "WELL";
                    } else if (i2 >= 0) {
                        roiType = "EXCELLENT";
                    }
                }
            }
        }
        return roiType;
    }

    private static HashMap<String, BigDecimal> initAdjsMap(String pOuCode, Date today) {
        HashMap<String, BigDecimal> initAdjs = new HashMap<>(16);
        String selectColumn = "aos_bid * 2 aos_bid," + "aos_productno";
        QFilter qfDate = new QFilter("aos_date", "=", today);
        QFilter filterBill = new QFilter("aos_orgid.number", "=", pOuCode);
        QFilter[] filters = new QFilter[] {qfDate, filterBill};
        DataSet aosMktPopularPpcS = QueryServiceHelper.queryDataSet("aos_mkt_popbudpst_init" + "." + "InitAdjsMap",
            "aos_mkt_pop_ppcst", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_productno"};
        aosMktPopularPpcS = aosMktPopularPpcS.groupBy(groupBy).sum("aos_bid").finish();
        while (aosMktPopularPpcS.hasNext()) {
            Row aosMktPopularPpc = aosMktPopularPpcS.next();
            initAdjs.put(aosMktPopularPpc.getString("aos_productno"), aosMktPopularPpc.getBigDecimal("aos_bid"));
        }
        aosMktPopularPpcS.close();
        return initAdjs;
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        // 多线程执行
        executerun(param);
    }

    static class MktPopBudpStRunnable implements Runnable {
        private final Map<String, Object> params;

        public MktPopBudpStRunnable(Map<String, Object> param) {
            this.params = param;
        }

        @Override
        public void run() {
            try {
                CommonDataSom.init();
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