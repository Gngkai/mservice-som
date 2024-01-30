package mkt.common;

import common.fnd.FndDate;
import common.fnd.FndGlobal;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.cache.CacheFactory;
import kd.bos.cache.DistributeSessionlessCache;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import org.apache.commons.lang3.SerializationUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author aosom
 * @version 推广缓存-工具类
 */
public class AosMktCacheUtil {
    private static final DistributeSessionlessCache CACHE =
        CacheFactory.getCommonCacheFactory().getDistributeSessionlessCache("mkt_redis");
    private static final String ACT = "act";
    private static final String PPC = "ppc";
    private static final String PPCADJ = "ppcadj";
    private static final String PPCADJSAL = "ppcadj_sal";
    private static final String PPCBUDGETP = "ppcbudget_p";

    public static void initRedis(String type) {
        if (ACT.equals(type)) {
            initItemorg();// 国别物料维度
            initDatepara();// 物流周期表
        } else if (PPC.equals(type)) {
            initItemorg();// 国别物料维度
            initDatepara();// 物流周期表
            initProductid();// 店铺货号关联关系
            initPpcdate();// ppc数据源最小系列与组日期
            initPoporg();// 营销国别参数
            initSalsummary();// 销售汇总表当月数据
            initPpcyester();// ppc数据源昨日数据
            initItemavadays();// 库存可售天数
            initDailyprice();// 每日价格
            initSkurpt();// SKU推广报告7日汇总
            initSkurpt3();// SKU推广报告3日汇总
            initSkurpt14();// SKU推广报告14日汇总
            initSkurpt14Serial();// SKU推广报告14日系列
            initPpcyester14();// PPC14日
            initAdvpirce();// 建议价格
            initSkurptDetail();// SKU推广报告1日
            initSkurptSerial();// SKU推广报告7日系列
            initSkurpt3Serial();// SKU推广报告3日系列
            initSalsummaryserial();// 预测汇总表系列
            initBasestitem();// 国别物料滞销类型
            // 出价调整(销售)
            initSkurpt3avg();// 前三日平均
            initSkurpt7Detail();// SKU推广报告7日
            initPpcyester7();// ppc数据源前7日数据
            initAdvpirce7Day();// 建议价格前7日数据
            // 出价调整(预算)
            initSkurpt14Detail();// SKU推广报告14日
            initAdvpirce3Day();// 建议价格近三天
            initPpcyesterSerial();// PPC昨日 系列维度
            initSkurptDetailSerial();// SKU推广报告1日系列
            initPpcdateSerial();
            initSkurptSerial14();
            initSkurpt14DetailTotal();
        } else if (PPCADJ.equals(type)) {
            initPoporg();// 营销国别参数
            initSkurptDetail();// SKU推广报告1日
            initPpcyester();// ppc数据源昨日数据
            initAdvpirce3Day();// 建议价格近三天
            initPpcdate();// ppc数据源最小系列与组日期
            initSkurpt3avg();// 前三日平均
            initSkurpt();// SKU推广报告7日汇总
            initDailyprice();// 每日价格
            initSkurptDetailSerial();// SKU推广报告1日系列
            initSkurpt14Detail();// SKU推广报告14日
            initPpcyester14();// PPC14日
        } else if (PPCADJSAL.equals(type)) {
            initSkurpt3avg();// 前三日平均
            initPoporg();// 营销国别参数
            initAdvpirce();// 建议价格
            initSkurptDetail();// SKU推广报告1日
            initSkurpt7Detail();// SKU推广报告7日
            initPpcyester7();// ppc数据源前7日数据
            initDailyprice();// 每日价格
            initPpcyesterSerial();// PPC昨日 系列维度
            initSkurpt14Detail();// SKU推广报告14日
            initPpcyester14();// PPC14日
        } else if (PPCBUDGETP.equals(type)) {
            initSkurptSerial();// SKU推广报告7日系列
            initPoporg();// 营销国别参数
            initSkurptDetailSerial();// SKU推广报告1日系列
            initPpcyesterSerial();// PPC昨日数据系列维度
            initPpcyesterSerial7();// ppc数据源前7日数据 系列维度
            initSkurpt7Serial();// SKU推广报告7日系列
        }
    }

    private static void initSkurpt7Serial() {
        HashMap<String, Map<String, Map<String, Object>>> skuRpt = new HashMap<>(16);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        Date dateTo = FndDate.zero(new Date());
        String dateToStr = writeFormat.format(dateTo);
        Date dateFrom = FndDate.add_days(dateTo, -7);
        String dateFromStr = writeFormat.format(dateFrom);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter filterMatch = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterMatch};
        String selectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku.aos_productno||'-AUTO' aos_productno,"
            + "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_total_sales aos_total_sales,"
            + "aos_entryentity.aos_date_l aos_date_l";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_skurpt7Serial",
            "aos_base_skupoprpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_orgid", "aos_productno", "aos_date_l"};
        aosBaseSkupoprptS = aosBaseSkupoprptS.groupBy(groupBy).sum("aos_total_sales").sum("aos_spend").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            Map<String, Object> detail = new HashMap<>(16);
            detail.put("aos_spend", aosBaseSkupoprpt.get("aos_spend"));
            BigDecimal aosRoi = BigDecimal.ZERO;
            if (aosBaseSkupoprpt.get("aos_spend") != null
                && ((BigDecimal)aosBaseSkupoprpt.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                aosRoi = ((BigDecimal)aosBaseSkupoprpt.get("aos_total_sales"))
                    .divide((BigDecimal)aosBaseSkupoprpt.get("aos_spend"), 6, RoundingMode.HALF_UP);
            }
            detail.put("aos_roi", aosRoi);
            Date aosDateL = aosBaseSkupoprpt.getDate("aos_date_l");
            String aosDateStr = df.format(aosDateL);
            Map<String, Map<String, Object>> info =
                skuRpt.get(aosBaseSkupoprpt.getLong("aos_orgid") + "~" + aosBaseSkupoprpt.getString("aos_productno"));
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(aosDateStr, detail);
            skuRpt.put(aosBaseSkupoprpt.getLong("aos_orgid") + "~" + aosBaseSkupoprpt.getString("aos_productno"), info);
        }
        aosBaseSkupoprptS.close();
        byte[] serializeSkurpt = SerializationUtils.serialize(skuRpt);
        CACHE.put("mkt_skurpt7Serial", serializeSkurpt, 3600);
    }

    private static void initPpcyesterSerial7() {
        HashMap<String, Map<String, Map<String, Object>>> ppcYster7 = new HashMap<>(16);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        Date dateTo = FndDate.zero(new Date());
        String dateToStr = writeFormat.format(dateTo);
        Date dateFrom = FndDate.add_days(dateTo, -7);
        String dateFromStr = writeFormat.format(dateFrom);
        QFilter filterDateFrom = new QFilter("aos_date", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_date", "<=", dateToStr);
        QFilter qfType = new QFilter("aos_entryentity.aos_groupstatus", "=", "AVAILABLE");
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, qfType};
        String selectColumn = "aos_orgid,aos_date," + "aos_entryentity.aos_budget aos_budget,"
            + "aos_entryentity.aos_productno aos_productno,1 aos_skucount";
        DataSet aosMktPopularPpcS = QueryServiceHelper.queryDataSet(
            "aos_mkt_common_redis" + "." + "init_ppcyesterSerial7", "aos_mkt_popular_ppc", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_orgid", "aos_date", "aos_productno", "aos_budget"};
        aosMktPopularPpcS = aosMktPopularPpcS.groupBy(groupBy).sum("aos_skucount").finish();
        while (aosMktPopularPpcS.hasNext()) {
            Row aosMktPopularPpc = aosMktPopularPpcS.next();
            Map<String, Object> detail = new HashMap<>(16);
            detail.put("aos_budget", aosMktPopularPpc.get("aos_budget"));
            detail.put("aos_skucount", aosMktPopularPpc.get("aos_skucount"));
            Date aosDate = aosMktPopularPpc.getDate("aos_date");
            String aosDateStr = df.format(aosDate);
            Map<String, Map<String, Object>> info = ppcYster7
                .get(aosMktPopularPpc.getLong("aos_orgid") + "~" + aosMktPopularPpc.getString("aos_productno"));
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(aosDateStr, detail);
            ppcYster7.put(aosMktPopularPpc.getLong("aos_orgid") + "~" + aosMktPopularPpc.getString("aos_productno"),
                info);
        }
        aosMktPopularPpcS.close();
        byte[] serializePpcyster7 = SerializationUtils.serialize(ppcYster7);
        CACHE.put("mkt_ppcyster7Serial", serializePpcyster7, 3600);
    }

    private static void initPpcyesterSerial() {
        HashMap<String, Map<String, Object>> ppcYester = new HashMap<>(16);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        Date dateTo = FndDate.zero(new Date());
        String dateToStr = writeFormat.format(dateTo);
        Date dateFrom = FndDate.add_days(dateTo, -1);
        String dateFromStr = writeFormat.format(dateFrom);
        QFilter filterDateFrom = new QFilter("aos_date", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_date", "<", dateToStr);
        QFilter qfType = new QFilter("aos_entryentity.aos_groupstatus", "=", "AVAILABLE");
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, qfType};
        String selectColumn =
            "aos_orgid,aos_entryentity.aos_productno aos_productno," + "aos_entryentity.aos_budget aos_budget";
        DataSet aosMktPopularPpcS = QueryServiceHelper.queryDataSet(
            "aos_mkt_common_redis" + "." + "init_ppcyesterSerial", "aos_mkt_popular_ppc", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_orgid", "aos_productno", "aos_budget"};
        aosMktPopularPpcS = aosMktPopularPpcS.groupBy(groupBy).finish();
        while (aosMktPopularPpcS.hasNext()) {
            Row aosMktPopularPpc = aosMktPopularPpcS.next();
            HashMap<String, Object> info = new HashMap<>(16);
            info.put("aos_budget", aosMktPopularPpc.get("aos_budget"));
            ppcYester.put(aosMktPopularPpc.getLong("aos_orgid") + "~" + aosMktPopularPpc.getString("aos_productno"),
                info);
        }
        aosMktPopularPpcS.close();
        byte[] serializePpcYester = SerializationUtils.serialize(ppcYester);
        CACHE.put("mkt_ppcyestSerial", serializePpcYester, 3600);
    }

    private static void initSkurpt14Serial() {
        HashMap<String, Map<String, Object>> skuRpt = new HashMap<>(16);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        Date dateTo = FndDate.zero(new Date());
        Date dateFrom = FndDate.add_days(dateTo, -14);
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter filterMatch = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterMatch};
        String selectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku.aos_productno||'-AUTO' aos_productno,"
            + "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_total_sales aos_total_sales,"
            + "aos_entryentity.aos_impressions aos_impressions";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet(
            "aos_mkt_common_redis" + "." + "init_skurpt14Serial", "aos_base_skupoprpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_orgid", "aos_productno"};
        aosBaseSkupoprptS =
            aosBaseSkupoprptS.groupBy(groupBy).sum("aos_spend").sum("aos_total_sales").sum("aos_impressions").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            HashMap<String, Object> info = new HashMap<>(16);
            info.put("aos_spend", aosBaseSkupoprpt.get("aos_spend"));
            info.put("aos_total_sales", aosBaseSkupoprpt.get("aos_total_sales"));
            info.put("aos_impressions", aosBaseSkupoprpt.get("aos_impressions"));
            skuRpt.put(aosBaseSkupoprpt.getLong("aos_orgid") + "~" + aosBaseSkupoprpt.getString("aos_productno"), info);
        }
        aosBaseSkupoprptS.close();
        byte[] serializeSkurpt = SerializationUtils.serialize(skuRpt);
        CACHE.put("mkt_skurpt14Serial", serializeSkurpt, 3600);
    }

    private static void initSalsummaryserial() {
        HashMap<String, Map<String, Object>> salSummary = new HashMap<>(16);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        Date dateFrom = FndDate.zero(new Date());
        String dateFromStr = writeFormat.format(dateFrom);
        Date dateTo = FndDate.add_days(dateFrom, 1);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterType = new QFilter("aos_rsche_no", "=", "SKU调整表");
        QFilter filterDateFrom = new QFilter("aos_month", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_month", "<", dateToStr);
        QFilter[] filters = new QFilter[] {filterType, filterDateFrom, filterDateTo};
        String selectColumn = "aos_org aos_orgid," + "aos_sku.aos_productno||'-AUTO' aos_productno,"
            + "aos_price * aos_sche_qty aos_sales," + "aos_sche_qty,createtime";
        DataSet aosSalScheSummaryS =
            QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_salsummaryserial",
                "aos_sal_sche_summary", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_orgid", "aos_productno"};
        aosSalScheSummaryS = aosSalScheSummaryS.groupBy(groupBy).maxP("createtime", "aos_sales")
            .maxP("createtime", "aos_sche_qty").finish();
        while (aosSalScheSummaryS.hasNext()) {
            Row aosSalScheSummary = aosSalScheSummaryS.next();
            HashMap<String, Object> info = new HashMap<>(16);
            info.put("aos_sales", aosSalScheSummary.get("aos_sales"));
            info.put("aos_sche_qty", aosSalScheSummary.get("aos_sche_qty"));
            salSummary.put(aosSalScheSummary.get("aos_orgid") + "~" + aosSalScheSummary.get("aos_productno"), info);
        }
        aosSalScheSummaryS.close();
        byte[] serializeSalSummary = SerializationUtils.serialize(salSummary);
        CACHE.put("mkt_salsummarySerial", serializeSalSummary, 3600);
    }

    private static void initSkurptDetailSerial() {
        HashMap<String, Object> skuRptDetail = new HashMap<>(16);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        Date dateTo = FndDate.zero(new Date());
        String dateToStr = writeFormat.format(dateTo);
        Date dateFrom = FndDate.add_days(dateTo, -1);
        String dateFromStr = writeFormat.format(dateFrom);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<=", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter filterMatch = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterMatch};
        String selectColumn =
            "aos_orgid," + "aos_entryentity.aos_cam_name aos_productno," + "aos_entryentity.aos_spend aos_spend";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_skurptDetail",
            "aos_base_skupoprpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_orgid", "aos_productno"};
        aosBaseSkupoprptS = aosBaseSkupoprptS.groupBy(groupBy).sum("aos_spend").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            skuRptDetail.put(aosBaseSkupoprpt.getLong("aos_orgid") + "~" + aosBaseSkupoprpt.getString("aos_productno"),
                aosBaseSkupoprpt.getBigDecimal("aos_spend"));
        }
        aosBaseSkupoprptS.close();
        byte[] serializeSkurptdetail = SerializationUtils.serialize(skuRptDetail);
        CACHE.put("mkt_skurptDetailSerial", serializeSkurptdetail, 3600);
    }

    private static void initSkurpt3Serial() {
        HashMap<String, Map<String, Map<String, Object>>> skuRpt = new HashMap<>(16);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        Date dateTo = FndDate.zero(new Date());
        String dateToStr = writeFormat.format(dateTo);
        Date dateFrom = FndDate.add_days(dateTo, -3);
        String dateFromStr = writeFormat.format(dateFrom);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter filterMatch = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterMatch};
        String selectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku.aos_productno||'-AUTO' aos_productno,"
            + "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_total_sales aos_total_sales,"
            + "aos_entryentity.aos_impressions aos_impressions," + "aos_entryentity.aos_date_l aos_date_l,"
            + "aos_entryentity.aos_clicks aos_clicks";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet(
            "aos_mkt_common_redis" + "." + "init_skurpt14Detail", "aos_base_skupoprpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_orgid", "aos_productno", "aos_date_l"};
        aosBaseSkupoprptS = aosBaseSkupoprptS.groupBy(groupBy).sum("aos_clicks").sum("aos_impressions").sum("aos_spend")
            .sum("aos_total_sales").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            Map<String, Object> detail = new HashMap<>(16);
            detail.put("aos_spend", aosBaseSkupoprpt.get("aos_spend"));
            detail.put("aos_total_sales", aosBaseSkupoprpt.get("aos_total_sales"));
            detail.put("aos_impressions", aosBaseSkupoprpt.get("aos_impressions"));
            BigDecimal aosRoi = BigDecimal.ZERO;
            if (aosBaseSkupoprpt.get("aos_spend") != null
                && ((BigDecimal)aosBaseSkupoprpt.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                aosRoi = ((BigDecimal)aosBaseSkupoprpt.get("aos_total_sales"))
                    .divide((BigDecimal)aosBaseSkupoprpt.get("aos_spend"), 2, RoundingMode.HALF_UP);
            }
            detail.put("aos_roi", aosRoi);
            BigDecimal aosBid = BigDecimal.ZERO;
            if (aosBaseSkupoprpt.get("aos_clicks") != null
                && ((BigDecimal)aosBaseSkupoprpt.get("aos_clicks")).compareTo(BigDecimal.ZERO) != 0) {
                aosBid = ((BigDecimal)aosBaseSkupoprpt.get("aos_spend"))
                    .divide((BigDecimal)aosBaseSkupoprpt.get("aos_clicks"), 2, RoundingMode.HALF_UP);
            }
            detail.put("aos_bid", aosBid);
            Date aosDateL = aosBaseSkupoprpt.getDate("aos_date_l");
            String aosDateStr = df.format(aosDateL);
            Map<String, Map<String, Object>> info =
                skuRpt.get(aosBaseSkupoprpt.getLong("aos_orgid") + "~" + aosBaseSkupoprpt.getString("aos_productno"));
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(aosDateStr, detail);
            skuRpt.put(aosBaseSkupoprpt.getLong("aos_orgid") + "~" + aosBaseSkupoprpt.getString("aos_productno"), info);
        }
        aosBaseSkupoprptS.close();
        byte[] serializeSkurpt = SerializationUtils.serialize(skuRpt);
        CACHE.put("mkt_skurpt3Serial", serializeSkurpt, 3600);
    }

    private static void initSkurptSerial() {
        HashMap<String, Map<String, Object>> skuRpt = new HashMap<>(16);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        Date dateTo = FndDate.zero(new Date());
        String dateToStr = writeFormat.format(dateTo);
        Date dateFrom = FndDate.add_days(dateTo, -7);
        String dateFromStr = writeFormat.format(dateFrom);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter filterMatch = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterMatch};
        String selectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku.aos_productno||'-AUTO' aos_productno,"
            + "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_total_sales aos_total_sales,"
            + "aos_entryentity.aos_clicks aos_clicks,aos_entryentity.aos_total_order aos_total_order";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_skurpt",
            "aos_base_skupoprpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_orgid", "aos_productno"};
        aosBaseSkupoprptS = aosBaseSkupoprptS.groupBy(groupBy).sum("aos_spend").sum("aos_total_sales").sum("aos_clicks")
            .sum("aos_total_order").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            HashMap<String, Object> info = new HashMap<>(16);
            info.put("aos_spend", aosBaseSkupoprpt.get("aos_spend"));
            info.put("aos_total_sales", aosBaseSkupoprpt.get("aos_total_sales"));
            info.put("aos_clicks", aosBaseSkupoprpt.get("aos_clicks"));
            info.put("aos_total_order", aosBaseSkupoprpt.get("aos_total_order"));
            skuRpt.put(aosBaseSkupoprpt.getLong("aos_orgid") + "~" + aosBaseSkupoprpt.getString("aos_productno"), info);
        }
        aosBaseSkupoprptS.close();
        byte[] serializeSkurpt = SerializationUtils.serialize(skuRpt);
        CACHE.put("mkt_skurptSerial7", serializeSkurpt, 3600);
    }

    private static void initSkurptSerial14() {
        HashMap<String, Map<String, Object>> skuRpt = new HashMap<>(16);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        Date dateTo = FndDate.zero(new Date());
        Date dateFrom = FndDate.add_days(dateTo, -14);
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter filterMatch = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterMatch};
        String selectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku.aos_productno||'-AUTO' aos_productno,"
            + "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_total_sales aos_total_sales,"
            + "aos_entryentity.aos_clicks aos_clicks,aos_entryentity.aos_total_order aos_total_order";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_skurpt",
            "aos_base_skupoprpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_orgid", "aos_productno"};
        aosBaseSkupoprptS = aosBaseSkupoprptS.groupBy(groupBy).sum("aos_spend").sum("aos_total_sales").sum("aos_clicks")
            .sum("aos_total_order").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            HashMap<String, Object> info = new HashMap<>(16);
            info.put("aos_spend", aosBaseSkupoprpt.get("aos_spend"));
            info.put("aos_total_sales", aosBaseSkupoprpt.get("aos_total_sales"));
            info.put("aos_clicks", aosBaseSkupoprpt.get("aos_clicks"));
            info.put("aos_total_order", aosBaseSkupoprpt.get("aos_total_order"));
            skuRpt.put(aosBaseSkupoprpt.getLong("aos_orgid") + "~" + aosBaseSkupoprpt.getString("aos_productno"), info);
        }
        aosBaseSkupoprptS.close();
        byte[] serializeSkurpt = SerializationUtils.serialize(skuRpt);
        CACHE.put("mkt_skurptSerial14", serializeSkurpt, 3600);
    }

    private static void initPpcyester7() {
        HashMap<String, Map<String, Map<String, Object>>> ppcYster7 = new HashMap<>(16);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        Date dateTo = FndDate.zero(new Date());
        Date dateFrom = FndDate.add_days(dateTo, -7);
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterDateFrom = new QFilter("aos_date", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_date", "<=", dateToStr);
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo};
        String selectColumn = "aos_orgid,aos_date," + "aos_entryentity.aos_itemid aos_itemid,"
            + "aos_entryentity.aos_marketprice aos_marketprice," + "aos_entryentity.aos_budget aos_budget";
        DataSet aosMktPopularPpcS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_ppcyester7",
            "aos_mkt_popular_ppc", selectColumn, filters, null);
        while (aosMktPopularPpcS.hasNext()) {
            Row aosMktPopularPpc = aosMktPopularPpcS.next();
            Map<String, Object> detail = new HashMap<>(16);
            detail.put("aos_marketprice", aosMktPopularPpc.get("aos_marketprice"));
            detail.put("aos_budget", aosMktPopularPpc.get("aos_budget"));
            Date aosDate = aosMktPopularPpc.getDate("aos_date");
            String aosDateStr = df.format(aosDate);
            Map<String, Map<String, Object>> info =
                ppcYster7.get(aosMktPopularPpc.getLong("aos_orgid") + "~" + aosMktPopularPpc.getLong("aos_itemid"));
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(aosDateStr, detail);
            ppcYster7.put(aosMktPopularPpc.getLong("aos_orgid") + "~" + aosMktPopularPpc.getLong("aos_itemid"), info);
        }
        aosMktPopularPpcS.close();
        byte[] serializePpcyster7 = SerializationUtils.serialize(ppcYster7);
        CACHE.put("mkt_ppcyster7", serializePpcyster7, 3600);
    }

    private static void initPpcyester14() {
        HashMap<String, Map<String, Map<String, Object>>> ppcYster7 = new HashMap<>(16);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        Date dateTo = FndDate.zero(new Date());
        Date dateFrom = FndDate.add_days(dateTo, -7);
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterDateFrom = new QFilter("aos_date", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_date", "<=", dateToStr);
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo};
        String selectColumn = "aos_orgid,aos_date," + "aos_entryentity.aos_itemid aos_itemid,"
            + "aos_entryentity.aos_marketprice aos_marketprice," + "aos_entryentity.aos_budget aos_budget";
        DataSet aosMktPopularPpcS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_ppcyester7",
            "aos_mkt_popular_ppc", selectColumn, filters, null);
        while (aosMktPopularPpcS.hasNext()) {
            Row aosMktPopularPpc = aosMktPopularPpcS.next();
            Map<String, Object> detail = new HashMap<>(16);
            detail.put("aos_marketprice", aosMktPopularPpc.get("aos_marketprice"));
            detail.put("aos_budget", aosMktPopularPpc.get("aos_budget"));
            Date aosDate = aosMktPopularPpc.getDate("aos_date");
            String aosDateStr = df.format(aosDate);
            Map<String, Map<String, Object>> info =
                ppcYster7.get(aosMktPopularPpc.getLong("aos_orgid") + "~" + aosMktPopularPpc.getLong("aos_itemid"));
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(aosDateStr, detail);
            ppcYster7.put(aosMktPopularPpc.getLong("aos_orgid") + "~" + aosMktPopularPpc.getLong("aos_itemid"), info);
        }
        aosMktPopularPpcS.close();
        byte[] serializePpcyster7 = SerializationUtils.serialize(ppcYster7);
        CACHE.put("mkt_ppcyster14", serializePpcyster7, 3600);
    }

    private static void initSkurpt7Detail() {
        HashMap<String, Map<String, Map<String, Object>>> skuRpt7 = new HashMap<>(16);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        Date dateTo = FndDate.zero(new Date());
        String dateToStr = writeFormat.format(dateTo);
        Date dateFrom = FndDate.add_days(dateTo, -7);
        String dateFromStr = writeFormat.format(dateFrom);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter filterMatch = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterMatch};
        String selectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku aos_itemid,"
            + "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_total_sales aos_total_sales,"
            + "aos_entryentity.aos_impressions aos_impressions," + "aos_entryentity.aos_date_l aos_date_l,"
            + "aos_entryentity.aos_clicks aos_clicks";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet(
            "aos_mkt_common_redis" + "." + "init_skurpt14Detail", "aos_base_skupoprpt", selectColumn, filters, null);

        String[] groupBy = new String[] {"aos_orgid", "aos_itemid", "aos_date_l"};
        aosBaseSkupoprptS = aosBaseSkupoprptS.groupBy(groupBy).sum("aos_spend").sum("aos_total_sales")
            .sum("aos_impressions").sum("aos_clicks").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            Map<String, Object> detail = new HashMap<>(16);
            detail.put("aos_spend", aosBaseSkupoprpt.get("aos_spend"));
            detail.put("aos_total_sales", aosBaseSkupoprpt.get("aos_total_sales"));
            detail.put("aos_impressions", aosBaseSkupoprpt.get("aos_impressions"));
            BigDecimal aosRoi = BigDecimal.ZERO;
            if (aosBaseSkupoprpt.get("aos_spend") != null
                && ((BigDecimal)aosBaseSkupoprpt.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                aosRoi = ((BigDecimal)aosBaseSkupoprpt.get("aos_total_sales"))
                    .divide((BigDecimal)aosBaseSkupoprpt.get("aos_spend"), 2, RoundingMode.HALF_UP);
            }
            detail.put("aos_roi", aosRoi);
            BigDecimal aosBid = BigDecimal.ZERO;
            if (aosBaseSkupoprpt.get("aos_clicks") != null
                && ((BigDecimal)aosBaseSkupoprpt.get("aos_clicks")).compareTo(BigDecimal.ZERO) != 0) {
                aosBid = ((BigDecimal)aosBaseSkupoprpt.get("aos_spend"))
                    .divide((BigDecimal)aosBaseSkupoprpt.get("aos_clicks"), 2, RoundingMode.HALF_UP);
            }
            detail.put("aos_bid", aosBid);
            Date aosDateL = aosBaseSkupoprpt.getDate("aos_date_l");
            String aosDateStr = df.format(aosDateL);
            Map<String, Map<String, Object>> info =
                skuRpt7.get(aosBaseSkupoprpt.getLong("aos_orgid") + "~" + aosBaseSkupoprpt.getLong("aos_itemid"));
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(aosDateStr, detail);
            skuRpt7.put(aosBaseSkupoprpt.getLong("aos_orgid") + "~" + aosBaseSkupoprpt.getLong("aos_itemid"), info);
        }
        aosBaseSkupoprptS.close();
        byte[] serializeSkurpt7 = SerializationUtils.serialize(skuRpt7);
        CACHE.put("mkt_skurpt7Detail", serializeSkurpt7, 3600);
    }

    private static void initSkurpt3avg() {
        HashMap<String, Map<String, Object>> skuRpt3Avg = new HashMap<>(16);
        HashMap<String, Integer> qtyMap = new HashMap<>(16);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        Date dateTo = FndDate.zero(new Date());
        Date dateFrom = FndDate.add_days(dateTo, -3);
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter filterMatch = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterMatch};
        DataSet skuRptSetS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis.qty", "aos_base_skupoprpt",
            "aos_orgid,aos_entryentity.aos_ad_sku aos_itemid," + "aos_entryentity.aos_shopsku aos_shopsku", filters,
            null);
        skuRptSetS = skuRptSetS.distinct();
        while (skuRptSetS.hasNext()) {
            Row skuRptSet = skuRptSetS.next();
            String key = skuRptSet.getString("aos_orgid") + "~" + skuRptSet.getString("aos_itemid");
            if (FndGlobal.IsNull(qtyMap.get(key))) {
                qtyMap.put(key, 1);
            } else {
                qtyMap.put(key, qtyMap.get(key) + 1);
            }
        }
        skuRptSetS.close();
        String selectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku aos_itemid,"
            + "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_impressions aos_impressions";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_skurpt3avg",
            "aos_base_skupoprpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_orgid", "aos_itemid"};
        aosBaseSkupoprptS = aosBaseSkupoprptS.groupBy(groupBy).avg("aos_spend").avg("aos_impressions").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            Map<String, Object> info = new HashMap<>(16);
            String key = aosBaseSkupoprpt.getLong("aos_orgid") + "~" + aosBaseSkupoprpt.getLong("aos_itemid");
            info.put("aos_spend", aosBaseSkupoprpt.get("aos_spend"));
            BigDecimal aosImpressions = aosBaseSkupoprpt.getBigDecimal("aos_impressions")
                .multiply(BigDecimal.valueOf(qtyMap.getOrDefault(key, 1)));
            info.put("aos_impressions", aosImpressions);
            skuRpt3Avg.put(key, info);
        }
        aosBaseSkupoprptS.close();
        byte[] serializeSkurpt3avg = SerializationUtils.serialize(skuRpt3Avg);
        CACHE.put("mkt_skurpt3avg", serializeSkurpt3avg, 3600);
    }

    private static void initAdvpirce3Day() {
        HashMap<String, Map<String, Map<String, Object>>> advicePrice3Day = new HashMap<>(16);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        Date dateTo = FndDate.zero(new Date());
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterDateFrom = new QFilter("aos_date", ">=", dateToStr);
        QFilter filterDateTo = new QFilter("aos_date", "<=", dateToStr);
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo};
        String selectColumn = "aos_entryentity.aos_orgid aos_orgid," + "aos_entryentity.aos_ad_name aos_ad_name,"
            + "aos_entryentity.aos_bid_suggest aos_bid_suggest,"
            + "aos_entryentity.aos_bid_rangestart aos_bid_rangestart,"
            + "aos_entryentity.aos_bid_rangeend aos_bid_rangeend," + "aos_date";
        DataSet aosBaseAdvrptS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_advpirce3Day",
            "aos_base_advrpt", selectColumn, filters, null);
        while (aosBaseAdvrptS.hasNext()) {
            Row aosBaseAdvrpt = aosBaseAdvrptS.next();
            Map<String, Object> detail = new HashMap<>(16);
            detail.put("aos_bid_suggest", aosBaseAdvrpt.get("aos_bid_suggest"));
            detail.put("aos_bid_rangestart", aosBaseAdvrpt.get("aos_bid_rangestart"));
            detail.put("aos_bid_rangeend", aosBaseAdvrpt.get("aos_bid_rangeend"));
            Date aosDate = aosBaseAdvrpt.getDate("aos_date");
            String aosDateStr = df.format(aosDate);
            Map<String, Map<String, Object>> info =
                advicePrice3Day.get(aosBaseAdvrpt.getLong("aos_orgid") + "~" + aosBaseAdvrpt.getString("aos_ad_name"));
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(aosDateStr, detail);
            advicePrice3Day.put(aosBaseAdvrpt.getLong("aos_orgid") + "~" + aosBaseAdvrpt.getString("aos_ad_name"),
                info);
        }
        aosBaseAdvrptS.close();
        byte[] serializeAdviceprice3 = SerializationUtils.serialize(advicePrice3Day);
        CACHE.put("mkt_adviceprice3", serializeAdviceprice3, 3600);
    }

    private static void initAdvpirce7Day() {
        HashMap<String, Map<String, Map<String, Object>>> advicePrice7Day = new HashMap<>(16);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        Date dateTo = FndDate.zero(new Date());
        String dateToStr = writeFormat.format(dateTo);
        Date dateFrom = FndDate.add_days(dateTo, -6);
        String dateFromStr = writeFormat.format(dateFrom);
        QFilter filterDateFrom = new QFilter("aos_date", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_date", "<=", dateToStr);
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo};
        String selectColumn = "aos_entryentity.aos_orgid aos_orgid," + "aos_entryentity.aos_ad_name aos_ad_name,"
            + "aos_entryentity.aos_bid_suggest aos_bid_suggest,"
            + "aos_entryentity.aos_bid_rangestart aos_bid_rangestart,"
            + "aos_entryentity.aos_bid_rangeend aos_bid_rangeend," + "aos_date";
        DataSet aosBaseAdvrptS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_advpirce7Day",
            "aos_base_advrpt", selectColumn, filters, null);
        while (aosBaseAdvrptS.hasNext()) {
            Row aosBaseAdvrpt = aosBaseAdvrptS.next();
            Map<String, Object> detail = new HashMap<>(16);
            detail.put("aos_bid_suggest", aosBaseAdvrpt.get("aos_bid_suggest"));
            detail.put("aos_bid_rangestart", aosBaseAdvrpt.get("aos_bid_rangestart"));
            detail.put("aos_bid_rangeend", aosBaseAdvrpt.get("aos_bid_rangeend"));
            Date aosDate = aosBaseAdvrpt.getDate("aos_date");
            String aosDateStr = df.format(aosDate);
            Map<String, Map<String, Object>> info =
                advicePrice7Day.get(aosBaseAdvrpt.getLong("aos_orgid") + "~" + aosBaseAdvrpt.getString("aos_ad_name"));
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(aosDateStr, detail);
            advicePrice7Day.put(aosBaseAdvrpt.getLong("aos_orgid") + "~" + aosBaseAdvrpt.getString("aos_ad_name"),
                info);
        }
        aosBaseAdvrptS.close();
        byte[] serializeAdviceprice7 = SerializationUtils.serialize(advicePrice7Day);
        CACHE.put("mkt_adviceprice7", serializeAdviceprice7, 3600);
    }

    private static void initSkurptDetail() {
        HashMap<String, Map<String, Object>> skuRptDetail = new HashMap<>(16);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        Date dateTo = FndDate.zero(new Date());
        Date dateFrom = FndDate.add_days(dateTo, -1);
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<=", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter filterMatch = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterMatch};
        String selectColumn =
            "aos_orgid," + "aos_entryentity.aos_ad_sku aos_itemid," + "aos_entryentity.aos_spend aos_spend,"
                + "aos_entryentity.aos_impressions aos_impressions," + "aos_entryentity.aos_clicks aos_clicks";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_skurptDetail",
            "aos_base_skupoprpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_orgid", "aos_itemid"};
        aosBaseSkupoprptS =
            aosBaseSkupoprptS.groupBy(groupBy).sum("aos_spend").sum("aos_impressions").sum("aos_clicks").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            Map<String, Object> info = new HashMap<>(16);
            info.put("aos_spend", aosBaseSkupoprpt.get("aos_spend"));
            info.put("aos_impressions", aosBaseSkupoprpt.get("aos_impressions"));
            info.put("aos_clicks", aosBaseSkupoprpt.get("aos_clicks"));
            skuRptDetail.put(aosBaseSkupoprpt.getLong("aos_orgid") + "~" + aosBaseSkupoprpt.getLong("aos_itemid"),
                info);
        }
        aosBaseSkupoprptS.close();
        byte[] serializeSkurptdetail = SerializationUtils.serialize(skuRptDetail);
        CACHE.put("mkt_skurptDetail", serializeSkurptdetail, 3600);
    }

    private static void initAdvpirce() {
        HashMap<String, Map<String, Object>> adPrice = new HashMap<>(16);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date aosDate = FndDate.zero(new Date());
        String aosDateStr = writeFormat.format(aosDate);
        QFilter filterDate = new QFilter("aos_date", "=", aosDateStr);
        QFilter[] filters = new QFilter[] {filterDate};
        String selectColumn = "aos_entryentity.aos_orgid aos_orgid," + "aos_entryentity.aos_ad_name aos_ad_name,"
            + "aos_entryentity.aos_bid_suggest aos_bid_suggest,"
            + "aos_entryentity.aos_bid_rangestart aos_bid_rangestart,"
            + "aos_entryentity.aos_bid_rangeend aos_bid_rangeend";
        DataSet aosBaseAdvrptS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_advpirce",
            "aos_base_advrpt", selectColumn, filters, null);
        while (aosBaseAdvrptS.hasNext()) {
            Row aosBaseAdvrpt = aosBaseAdvrptS.next();
            Map<String, Object> info = new HashMap<>(16);
            info.put("aos_bid_suggest", aosBaseAdvrpt.get("aos_bid_suggest"));
            info.put("aos_bid_rangestart", aosBaseAdvrpt.get("aos_bid_rangestart"));
            info.put("aos_bid_rangeend", aosBaseAdvrpt.get("aos_bid_rangeend"));
            adPrice.put(aosBaseAdvrpt.getLong("aos_orgid") + "~" + aosBaseAdvrpt.getString("aos_ad_name"), info);
        }
        aosBaseAdvrptS.close();
        byte[] serializeAdprice = SerializationUtils.serialize(adPrice);
        CACHE.put("mkt_adprice", serializeAdprice, 3600);
    }

    private static void initBasestitem() {
        HashMap<String, Object> baseStItem = new HashMap<>(16);
        String selectColumn = "aos_orgid," + "aos_itemid," + "aos_type";
        DataSet aosBaseStiteS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_basestitem",
            "aos_base_stitem", selectColumn, null, null);
        while (aosBaseStiteS.hasNext()) {
            Row aosBaseStite = aosBaseStiteS.next();
            baseStItem.put(aosBaseStite.getLong("aos_orgid") + "~" + aosBaseStite.getLong("aos_itemid"),
                aosBaseStite.getString("aos_type"));
        }
        aosBaseStiteS.close();
        byte[] serializeBaseStItem = SerializationUtils.serialize(baseStItem);
        CACHE.put("mkt_basestitem", serializeBaseStItem, 3600);
    }

    private static void initSkurpt14Detail() {
        HashMap<String, Map<String, Map<String, Object>>> skuRpt = new HashMap<>(16);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        Date dateTo = FndDate.zero(new Date());
        Date dateFrom = FndDate.add_days(dateTo, -14);
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter filterMatch = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterMatch};
        String selectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku aos_itemid,"
            + "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_total_sales aos_total_sales,"
            + "aos_entryentity.aos_impressions aos_impressions," + "aos_entryentity.aos_date_l aos_date_l,"
            + "aos_entryentity.aos_clicks aos_clicks";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet(
            "aos_mkt_common_redis" + "." + "init_skurpt14Detail", "aos_base_skupoprpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_orgid", "aos_itemid", "aos_date_l"};
        aosBaseSkupoprptS = aosBaseSkupoprptS.groupBy(groupBy).sum("aos_spend").sum("aos_total_sales")
            .sum("aos_impressions").sum("aos_clicks").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            Map<String, Object> detail = new HashMap<>(16);
            detail.put("aos_spend", aosBaseSkupoprpt.get("aos_spend"));
            detail.put("aos_total_sales", aosBaseSkupoprpt.get("aos_total_sales"));
            detail.put("aos_impressions", aosBaseSkupoprpt.get("aos_impressions"));
            BigDecimal aosRoi = BigDecimal.ZERO;
            if (aosBaseSkupoprpt.get("aos_spend") != null
                && ((BigDecimal)aosBaseSkupoprpt.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                aosRoi = ((BigDecimal)aosBaseSkupoprpt.get("aos_total_sales"))
                    .divide((BigDecimal)aosBaseSkupoprpt.get("aos_spend"), 2, RoundingMode.HALF_UP);
            }
            detail.put("aos_roi", aosRoi);
            BigDecimal aosBid = BigDecimal.ZERO;
            if (aosBaseSkupoprpt.get("aos_clicks") != null
                && ((BigDecimal)aosBaseSkupoprpt.get("aos_clicks")).compareTo(BigDecimal.ZERO) != 0) {
                aosBid = ((BigDecimal)aosBaseSkupoprpt.get("aos_spend"))
                    .divide((BigDecimal)aosBaseSkupoprpt.get("aos_clicks"), 2, RoundingMode.HALF_UP);
            }
            detail.put("aos_bid", aosBid);
            Date aosDateL = aosBaseSkupoprpt.getDate("aos_date_l");
            String aosDateStr = df.format(aosDateL);
            Map<String, Map<String, Object>> info =
                skuRpt.get(aosBaseSkupoprpt.getLong("aos_orgid") + "~" + aosBaseSkupoprpt.getLong("aos_itemid"));
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(aosDateStr, detail);
            skuRpt.put(aosBaseSkupoprpt.getLong("aos_orgid") + "~" + aosBaseSkupoprpt.getLong("aos_itemid"), info);
        }
        aosBaseSkupoprptS.close();
        byte[] serializeSkurpt = SerializationUtils.serialize(skuRpt);
        CACHE.put("mkt_skurpt14Detail", serializeSkurpt, 3600);
    }

    private static void initSkurpt14DetailTotal() {
        HashMap<String, Map<String, Map<String, Object>>> skuRpt = new HashMap<>(16);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        Date dateTo = FndDate.zero(new Date());
        Date dateFrom = FndDate.add_days(dateTo, -14);
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<=", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter filterMatch = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterMatch};
        String selectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku aos_itemid,"
            + "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_total_sales aos_total_sales,"
            + "aos_entryentity.aos_impressions aos_impressions," + "aos_entryentity.aos_date_l aos_date_l,"
            + "aos_entryentity.aos_clicks aos_clicks";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet(
            "aos_mkt_common_redis" + "." + "init_skurpt14Detail", "aos_base_skupoprpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_orgid", "aos_itemid", "aos_date_l"};
        aosBaseSkupoprptS = aosBaseSkupoprptS.groupBy(groupBy).sum("aos_spend").sum("aos_total_sales")
            .sum("aos_impressions").sum("aos_clicks").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            Map<String, Object> detail = new HashMap<>(16);
            detail.put("aos_spend", aosBaseSkupoprpt.get("aos_spend"));
            detail.put("aos_total_sales", aosBaseSkupoprpt.get("aos_total_sales"));
            detail.put("aos_impressions", aosBaseSkupoprpt.get("aos_impressions"));
            BigDecimal aosRoi = BigDecimal.ZERO;
            if (aosBaseSkupoprpt.get("aos_spend") != null
                && ((BigDecimal)aosBaseSkupoprpt.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                aosRoi = ((BigDecimal)aosBaseSkupoprpt.get("aos_total_sales"))
                    .divide((BigDecimal)aosBaseSkupoprpt.get("aos_spend"), 2, RoundingMode.HALF_UP);
            }
            detail.put("aos_roi", aosRoi);
            BigDecimal aosBid = BigDecimal.ZERO;
            if (aosBaseSkupoprpt.get("aos_clicks") != null
                && ((BigDecimal)aosBaseSkupoprpt.get("aos_clicks")).compareTo(BigDecimal.ZERO) != 0) {
                aosBid = ((BigDecimal)aosBaseSkupoprpt.get("aos_spend"))
                    .divide((BigDecimal)aosBaseSkupoprpt.get("aos_clicks"), 2, RoundingMode.HALF_UP);
            }
            detail.put("aos_bid", aosBid);
            Date aosDateL = aosBaseSkupoprpt.getDate("aos_date_l");
            String aosDateStr = df.format(aosDateL);
            Map<String, Map<String, Object>> info =
                skuRpt.get(aosBaseSkupoprpt.getLong("aos_orgid") + "~" + aosBaseSkupoprpt.getLong("aos_itemid"));
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(aosDateStr, detail);
            skuRpt.put(aosBaseSkupoprpt.getLong("aos_orgid") + "~" + aosBaseSkupoprpt.getLong("aos_itemid"), info);
        }
        aosBaseSkupoprptS.close();
        byte[] serializeSkurpt = SerializationUtils.serialize(skuRpt);
        CACHE.put("mkt_skurpt14Detail_total", serializeSkurpt, 3600);
    }

    private static void initSkurpt() {
        HashMap<String, Map<String, Object>> skuRpt = new HashMap<>(16);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        Date dateTo = FndDate.zero(new Date());
        Date dateFrom = FndDate.add_days(dateTo, -7);
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter filterMatch = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterMatch};
        String selectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku aos_itemid,"
            + "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_total_sales aos_total_sales,"
            + "aos_entryentity.aos_impressions aos_impressions," + "aos_entryentity.aos_clicks aos_clicks,"
            + "aos_entryentity.aos_total_order aos_total_order";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_skurpt",
            "aos_base_skupoprpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_orgid", "aos_itemid"};
        aosBaseSkupoprptS = aosBaseSkupoprptS.groupBy(groupBy).sum("aos_spend").sum("aos_total_sales")
            .sum("aos_impressions").sum("aos_clicks").sum("aos_total_order").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            HashMap<String, Object> info = new HashMap<>(16);
            info.put("aos_spend", aosBaseSkupoprpt.get("aos_spend"));
            info.put("aos_total_sales", aosBaseSkupoprpt.get("aos_total_sales"));
            info.put("aos_impressions", aosBaseSkupoprpt.get("aos_impressions"));
            info.put("aos_clicks", aosBaseSkupoprpt.get("aos_clicks"));
            info.put("aos_total_order", aosBaseSkupoprpt.get("aos_total_order"));
            skuRpt.put(aosBaseSkupoprpt.getLong("aos_orgid") + "~" + aosBaseSkupoprpt.getLong("aos_itemid"), info);
        }
        aosBaseSkupoprptS.close();
        byte[] serializeSkurpt = SerializationUtils.serialize(skuRpt);
        CACHE.put("mkt_skurpt", serializeSkurpt, 3600);
    }

    private static void initSkurpt3() {
        HashMap<String, Map<String, Object>> skuRpt = new HashMap<>(16);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        Date dateTo = FndDate.zero(new Date());
        Date dateFrom = FndDate.add_days(dateTo, -3);
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter filterMatch = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterMatch};
        String selectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku aos_itemid,"
            + "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_total_sales aos_total_sales,"
            + "aos_entryentity.aos_impressions aos_impressions," + "aos_entryentity.aos_clicks aos_clicks";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_skurpt",
            "aos_base_skupoprpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_orgid", "aos_itemid"};
        aosBaseSkupoprptS = aosBaseSkupoprptS.groupBy(groupBy).sum("aos_spend").sum("aos_total_sales")
            .sum("aos_impressions").sum("aos_clicks").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            HashMap<String, Object> info = new HashMap<>(16);
            info.put("aos_spend", aosBaseSkupoprpt.get("aos_spend"));
            info.put("aos_total_sales", aosBaseSkupoprpt.get("aos_total_sales"));
            info.put("aos_impressions", aosBaseSkupoprpt.get("aos_impressions"));
            info.put("aos_clicks", aosBaseSkupoprpt.get("aos_clicks"));
            skuRpt.put(aosBaseSkupoprpt.getLong("aos_orgid") + "~" + aosBaseSkupoprpt.getLong("aos_itemid"), info);
        }
        aosBaseSkupoprptS.close();
        byte[] serializeSkurpt = SerializationUtils.serialize(skuRpt);
        CACHE.put("mkt_skurpt3", serializeSkurpt, 3600);
    }

    private static void initSkurpt14() {
        HashMap<String, Map<String, Object>> skuRpt = new HashMap<>(16);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        Date dateTo = FndDate.zero(new Date());
        Date dateFrom = FndDate.add_days(dateTo, -14);
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter filterMatch = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterMatch};
        String selectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku aos_itemid,"
            + "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_total_sales aos_total_sales,"
            + "aos_entryentity.aos_clicks aos_clicks," + "aos_entryentity.aos_impressions aos_impressions,"
            + "case when aos_entryentity.aos_impressions > 0 " + "then 1 "
            + "when aos_entryentity.aos_impressions <= 0 " + "then 0 " + "end as aos_impcount," + "1 as aos_online ";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_skurpt14",
            "aos_base_skupoprpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_orgid", "aos_itemid"};
        aosBaseSkupoprptS = aosBaseSkupoprptS.groupBy(groupBy).sum("aos_spend").sum("aos_total_sales").sum("aos_clicks")
            .sum("aos_impressions").sum("aos_impcount").sum("aos_online").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            HashMap<String, Object> info = new HashMap<>(16);
            info.put("aos_spend", aosBaseSkupoprpt.get("aos_spend"));
            info.put("aos_total_sales", aosBaseSkupoprpt.get("aos_total_sales"));
            info.put("aos_impressions", aosBaseSkupoprpt.get("aos_impressions"));
            info.put("aos_impcount", aosBaseSkupoprpt.get("aos_impcount"));
            info.put("aos_online", aosBaseSkupoprpt.get("aos_online"));
            info.put("aos_clicks", aosBaseSkupoprpt.get("aos_clicks"));
            skuRpt.put(aosBaseSkupoprpt.getLong("aos_orgid") + "~" + aosBaseSkupoprpt.getLong("aos_itemid"), info);
        }
        aosBaseSkupoprptS.close();
        byte[] serializeSkurpt = SerializationUtils.serialize(skuRpt);
        CACHE.put("mkt_skurpt14", serializeSkurpt, 3600);
    }

    private static void initDailyprice() {
        HashMap<String, Object> dailyPrice = new HashMap<>(16);
        String selectColumn = "aos_orgid," + "aos_item_code aos_itemid," + "aos_currentprice";
        QFilter filterMatch = new QFilter("aos_platformfid.number", "=", "AMAZON");
        QFilter[] filters = new QFilter[] {filterMatch};
        DynamicObjectCollection aosSyncInvpriceS = QueryServiceHelper.query("aos_sync_invprice", selectColumn, filters);
        for (DynamicObject aosSyncInvprice : aosSyncInvpriceS) {
            dailyPrice.put(aosSyncInvprice.getLong("aos_orgid") + "~" + aosSyncInvprice.getLong("aos_itemid"),
                aosSyncInvprice.get("aos_currentprice"));
        }
        byte[] serializeDailyPrice = SerializationUtils.serialize(dailyPrice);
        CACHE.put("mkt_dailyprice", serializeDailyPrice, 3600);
    }

    private static void initItemavadays() {
        HashMap<String, Object> itemAvaDays = new HashMap<>(16);
        String selectColumn = "aos_ou_code," + "aos_item_code," + "aos_7avadays";
        DynamicObjectCollection aosSalDwInvavadayS =
            QueryServiceHelper.query("aos_sal_dw_invavadays", selectColumn, null);
        for (DynamicObject aosSalDwInvavaday : aosSalDwInvavadayS) {
            itemAvaDays.put(aosSalDwInvavaday.get("aos_ou_code") + "~" + aosSalDwInvavaday.get("aos_item_code"),
                aosSalDwInvavaday.get("aos_7avadays"));
        }
        byte[] serializeItemAvaDays = SerializationUtils.serialize(itemAvaDays);
        CACHE.put("mkt_itemavadays", serializeItemAvaDays, 3600);
    }

    private static void initPpcyester() {
        HashMap<String, Map<String, Object>> ppcYester = new HashMap<>(16);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        Date dateTo = FndDate.zero(new Date());
        Date dateFrom = FndDate.add_days(dateTo, -1);
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterDateFrom = new QFilter("aos_date", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_date", "<", dateToStr);
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo};
        String selectColumn = "aos_orgid," + "aos_entryentity.aos_itemid aos_itemid,"
            + "aos_entryentity.aos_groupstatus aos_groupstatus," + "aos_entryentity.aos_budget aos_budget,"
            + "aos_entryentity.aos_bid aos_bid," + "aos_entryentity.aos_lastpricedate aos_lastpricedate";
        DataSet aosMktPopularPpcS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_ppcyester",
            "aos_mkt_popular_ppc", selectColumn, filters, null);
        while (aosMktPopularPpcS.hasNext()) {
            Row aosMktPopularPpc = aosMktPopularPpcS.next();
            HashMap<String, Object> info = new HashMap<>(16);
            // 上次预算
            info.put("aos_budget", aosMktPopularPpc.get("aos_budget"));
            // 上次出价
            info.put("aos_bid", aosMktPopularPpc.get("aos_bid"));
            info.put("aos_groupstatus", aosMktPopularPpc.get("aos_groupstatus"));
            info.put("aos_lastpricedate", aosMktPopularPpc.get("aos_lastpricedate"));
            ppcYester.put(aosMktPopularPpc.getLong("aos_orgid") + "~" + aosMktPopularPpc.getLong("aos_itemid"), info);
        }
        aosMktPopularPpcS.close();
        byte[] serializePpcYester = SerializationUtils.serialize(ppcYester);
        CACHE.put("mkt_ppcyest", serializePpcYester, 3600);
    }

    private static void initSalsummary() {
        HashMap<String, Map<String, Object>> salSummary = new HashMap<>(16);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        Date dateFrom = FndDate.zero(new Date());
        Date dateTo = FndDate.add_days(dateFrom, 1);
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterType = new QFilter("aos_rsche_no", "=", "SKU调整表");
        QFilter filterDateFrom = new QFilter("aos_month", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_month", "<", dateToStr);
        QFilter[] filters = new QFilter[] {filterType, filterDateFrom, filterDateTo};
        String selectColumn =
            "aos_org aos_orgid," + "aos_sku aos_itemid," + "aos_price * aos_sche_qty aos_sales," + "aos_sche_qty";
        DynamicObjectCollection aosSalScheSummaryS =
            QueryServiceHelper.query("aos_sal_sche_summary", selectColumn, filters, "createtime");
        for (DynamicObject aosSalScheSummary : aosSalScheSummaryS) {
            HashMap<String, Object> info = new HashMap<>(16);
            info.put("aos_sales", aosSalScheSummary.get("aos_sales"));
            info.put("aos_sche_qty", aosSalScheSummary.get("aos_sche_qty"));
            salSummary.put(aosSalScheSummary.get("aos_orgid") + "~" + aosSalScheSummary.get("aos_itemid"), info);
        }
        byte[] serializeSalSummary = SerializationUtils.serialize(salSummary);
        CACHE.put("mkt_salsummary", serializeSalSummary, 3600);
    }

    private static void initPoporg() {
        HashMap<String, Map<String, Object>> popOrgInfo = new HashMap<>(16);
        String selectColumn = "aos_orgid," + "aos_type," + "aos_value,aos_condition1";
        DataSet aosMktBaseOrgvalueS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_poporg",
            "aos_mkt_base_orgvalue", selectColumn, null, null);
        while (aosMktBaseOrgvalueS.hasNext()) {
            Row aosMktBaseOrgvalue = aosMktBaseOrgvalueS.next();
            HashMap<String, Object> info = new HashMap<>(16);
            info.put("aos_value", aosMktBaseOrgvalue.get("aos_value"));
            info.put("aos_condition1", aosMktBaseOrgvalue.get("aos_condition1"));
            popOrgInfo.put(aosMktBaseOrgvalue.getLong("aos_orgid") + "~" + aosMktBaseOrgvalue.get("aos_type"), info);
        }
        aosMktBaseOrgvalueS.close();
        byte[] serializePoporgInfo = SerializationUtils.serialize(popOrgInfo);
        CACHE.put("mkt_poporginfo", serializePoporgInfo, 3600);
    }

    private static void initPpcdate() {
        HashMap<String, Map<String, Object>> ppcInfo = new HashMap<>(16);
        String selectColumn =
            "aos_orgid," + "aos_entryentity.aos_itemid aos_itemid," + "aos_entryentity.aos_groupdate aos_groupdate";
        DataSet aosMktPopularPpcS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_ppcdate",
            "aos_mkt_popular_ppc", selectColumn, null, null);
        String[] groupBy = new String[] {"aos_orgid", "aos_itemid"};
        aosMktPopularPpcS = aosMktPopularPpcS.groupBy(groupBy).min("aos_groupdate").finish();
        while (aosMktPopularPpcS.hasNext()) {
            Row aosMktPopularPpc = aosMktPopularPpcS.next();
            HashMap<String, Object> info = new HashMap<>(16);
            info.put("aos_groupdate", aosMktPopularPpc.get("aos_groupdate"));
            ppcInfo.put(aosMktPopularPpc.getLong("aos_orgid") + "~" + aosMktPopularPpc.getLong("aos_itemid"), info);
        }
        aosMktPopularPpcS.close();
        byte[] serializePpcInfo = SerializationUtils.serialize(ppcInfo);
        CACHE.put("mkt_ppcinfo", serializePpcInfo, 3600);
    }

    private static void initPpcdateSerial() {
        HashMap<String, Map<String, Object>> ppcInfo = new HashMap<>(16);
        String selectColumn =
            "aos_orgid," + "aos_entryentity.aos_makedate aos_makedate," + "aos_entryentity.aos_productno aos_productno";
        DataSet aosMktPopularPpcS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_ppcdate",
            "aos_mkt_popular_ppc", selectColumn, null, null);
        String[] groupBy = new String[] {"aos_orgid", "aos_productno"};
        aosMktPopularPpcS = aosMktPopularPpcS.groupBy(groupBy).min("aos_makedate").finish();
        while (aosMktPopularPpcS.hasNext()) {
            Row aosMktPopularPpc = aosMktPopularPpcS.next();
            HashMap<String, Object> info = new HashMap<>(16);
            info.put("aos_makedate", aosMktPopularPpc.get("aos_makedate"));
            ppcInfo.put(aosMktPopularPpc.getLong("aos_orgid") + "~" + aosMktPopularPpc.getString("aos_productno"),
                info);
        }
        aosMktPopularPpcS.close();
        byte[] serializePpcInfo = SerializationUtils.serialize(ppcInfo);
        CACHE.put("mkt_ppcinfoSerial", serializePpcInfo, 3600);
    }

    private static void initProductid() {
        HashMap<String, String> productInfo = new HashMap<>(16);
        QFilter filterAma = new QFilter("aos_platformfid.number", "=", "AMAZON");
        QFilter filterMainshop = new QFilter("aos_shopfid.aos_is_mainshop", "=", true);
        QFilter[] filters = new QFilter[] {filterAma, filterMainshop};
        String selectColumn = "aos_orgid aos_orgid," + "aos_item_code aos_itemid," + "aos_shopsku aos_productid";
        DynamicObjectCollection bdMaterialS = QueryServiceHelper.query("aos_sync_invprice", selectColumn, filters);
        for (DynamicObject bdMaterial : bdMaterialS) {
            productInfo.put(bdMaterial.getLong("aos_orgid") + "~" + bdMaterial.getLong("aos_itemid"),
                bdMaterial.getString("aos_productid"));
        }
        byte[] serializeProductInfo = SerializationUtils.serialize(productInfo);
        CACHE.put("mkt_productinfo", serializeProductInfo, 3600);
    }

    private static void initDatepara() {
        HashMap<String, Map<String, Object>> datepara = new HashMap<>(16);
        datepara.put("aos_shp_day", initAosShpDay().get(0));
        datepara.put("aos_clear_day", initAosShpDay().get(1));
        datepara.put("aos_freight_day", initAosShpDay().get(2));
        byte[] serializeDatepara = SerializationUtils.serialize(datepara);
        CACHE.put("datepara", serializeDatepara, 3600);
    }

    private static List<Map<String, Object>> initAosShpDay() {
        List<Map<String, Object>> mapList = new ArrayList<>();
        Map<String, Object> aosShpdayMap = new HashMap<>(16);
        Map<String, Object> aosCleardayMap = new HashMap<>(16);
        Map<String, Object> aosFreightdayMap = new HashMap<>(16);
        DynamicObjectCollection aosScmFcorderParaS = QueryServiceHelper.query("aos_scm_fcorder_para",
            "aos_org.id aos_orgid,aos_shp_day,aos_clear_day,aos_freight_day", null);
        for (DynamicObject aosScmFcorderPara : aosScmFcorderParaS) {
            String aosOrgid = aosScmFcorderPara.getString("aos_orgid");
            int aosShpDay = aosScmFcorderPara.getInt("aos_shp_day");
            int aosClearDay = aosScmFcorderPara.getInt("aos_clear_day");
            int aosFreightDay = aosScmFcorderPara.getInt("aos_freight_day");
            aosShpdayMap.put(aosOrgid, aosShpDay);
            aosCleardayMap.put(aosOrgid, aosClearDay);
            aosFreightdayMap.put(aosOrgid, aosFreightDay);
        }
        mapList.add(aosShpdayMap);
        mapList.add(aosCleardayMap);
        mapList.add(aosFreightdayMap);
        return mapList;
    }

    private static void initItemorg() {
        HashMap<String, Map<String, Object>> item = new HashMap<>(16);
        // 最大库龄
        item.put("item_maxage", initItemMaxage().get(0));
        // 海外库存数量
        item.put("item_overseaqty", initItemQty().get(0));
        // 在途数量
        item.put("item_intransqty", initItemQty().get(1));
        byte[] serializeItem = SerializationUtils.serialize(item);
        CACHE.put("item", serializeItem, 3600);
    }

    private static List<Map<String, Object>> initItemQty() {
        List<Map<String, Object>> mapList = new ArrayList<>();
        Map<String, Object> itemOverseaqty = new HashMap<>(16);
        Map<String, Object> itemIntransqty = new HashMap<>(16);
        DynamicObjectCollection aosSyncInvouValueS = QueryServiceHelper.query("aos_sync_invou_value",
            "aos_ou.id aos_orgid,aos_item.id aos_itemid,(aos_noplatform_qty+aos_fba_qty) as aos_instock_qty,aos_intrans_qty",
            null);
        for (DynamicObject aosSyncInvouValue : aosSyncInvouValueS) {
            String aosOrgid = aosSyncInvouValue.getString("aos_orgid");
            String aosItemid = aosSyncInvouValue.getString("aos_itemid");
            int aosInstockQty = aosSyncInvouValue.getInt("aos_instock_qty");
            int aosIntransQty = aosSyncInvouValue.getInt("aos_intrans_qty");
            itemOverseaqty.put(aosOrgid + "~" + aosItemid, aosInstockQty);
            itemIntransqty.put(aosOrgid + "~" + aosItemid, aosIntransQty);
        }
        // 0 海外库存数量
        mapList.add(itemOverseaqty);
        // 1 在途数量
        mapList.add(itemIntransqty);
        return mapList;
    }

    private static List<Map<String, Object>> initItemMaxage() {
        List<Map<String, Object>> mapList = new ArrayList<>();
        Map<String, Object> itemMaxage = new HashMap<>(16);
        DynamicObjectCollection aosSyncItemageS = QueryServiceHelper.query("aos_sync_itemage",
            "aos_orgid.id aos_orgid,aos_itemid.id aos_itemid,aos_item_maxage", null);
        for (DynamicObject aosSyncItemage : aosSyncItemageS) {
            String aosOrgid = aosSyncItemage.getString("aos_orgid");
            String aosItemid = aosSyncItemage.getString("aos_itemid");
            int aosItemMaxage = aosSyncItemage.getInt("aos_item_maxage");
            itemMaxage.put(aosOrgid + "~" + aosItemid, aosItemMaxage);
        }
        // 0 最大库龄
        mapList.add(itemMaxage);
        return mapList;
    }
}
