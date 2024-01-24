package mkt.popular.promot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import common.fnd.FndLog;
import org.apache.commons.lang3.SerializationUtils;

import common.Cux_Common_Utl;
import common.fnd.FndDate;
import common.fnd.FndGlobal;
import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.JoinType;
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

/**
 * @author aosom
 * @version 出价调整推广-调度任务类
 */
public class AosMktPopAdjpTask extends AbstractTask {
    public final static String SYSTEM = getSystemId();
    public final static int TWOH = 200;
    public final static int TWO = 2;
    public final static int FIVE = 5;
    public final static String AOS_PPCBID = "aos_ppcbid";
    public final static String AOS_TOPPRICE = "aos_topprice";
    private static final Log logger = LogFactory.getLog(AosMktPopAdjpTask.class);
    private static final DistributeSessionlessCache CACHE =
        CacheFactory.getCommonCacheFactory().getDistributeSessionlessCache("mkt_redis");

    public static String getSystemId() {
        QFilter qfUser = new QFilter("number", "=", "system");
        DynamicObject dyUser = QueryServiceHelper.queryOne("bos_user", "id", new QFilter[] {qfUser});
        return dyUser.get("id").toString();
    }

    private static BigDecimal getHighStandard(Object fixValue, BigDecimal high1, BigDecimal high2, BigDecimal high3) {
        if (fixValue == null || ((BigDecimal)fixValue).compareTo(BigDecimal.valueOf(TWOH)) < 0) {
            return high1;
        } else if (((BigDecimal)fixValue).compareTo(BigDecimal.valueOf(TWOH)) >= 0
            && ((BigDecimal)fixValue).compareTo(BigDecimal.valueOf(500)) < 0) {
            return high2;
        } else {
            return high3;
        }
    }

    private static int getBetweenDays(Date endDay, Date fromDay) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(endDay);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        endDay = cal.getTime();
        cal.setTime(fromDay);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        fromDay = cal.getTime();
        return (int)((endDay.getTime() - fromDay.getTime()) / (1000 * 3600 * 24));
    }

    public static void executerun(Map<String, Object> param) {
        try {
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
            Object pOuCode = param.get("p_ou_code");
            // 删除数据
            Calendar today = Calendar.getInstance();
            today.set(Calendar.HOUR_OF_DAY, 0);
            today.set(Calendar.MINUTE, 0);
            today.set(Calendar.SECOND, 0);
            today.set(Calendar.MILLISECOND, 0);
            Date todayD = today.getTime();
            int year = today.get(Calendar.YEAR) - 2000;
            int month = today.get(Calendar.MONTH) + 1;
            int day = today.get(Calendar.DAY_OF_MONTH);
            QFilter filterId = new QFilter("aos_orgid.number", "=", pOuCode);
            QFilter filterDate = new QFilter("aos_makedate", "=", todayD);
            QFilter[] filtersAdj = new QFilter[] {filterId, filterDate};
            DeleteServiceHelper.delete("aos_mkt_popular_adjpn", filtersAdj);
            DeleteServiceHelper.delete("aos_mkt_popadjustp_data", filtersAdj);
            Object pOrgId = FndGlobal.get_import_id(pOuCode, "bd_country");
            // SKU报告14日
            byte[] serializeSkurpt14 = CACHE.getByteValue("mkt_skurpt14Detail_total");
            HashMap<String, Map<String, Map<String, Object>>> skuRpt14Detail =
                SerializationUtils.deserialize(serializeSkurpt14);
            // SKU报告1日
            byte[] serializeSkurptdetail = CACHE.getByteValue("mkt_skurptDetail");
            HashMap<String, Map<String, Object>> skuRptDetail = SerializationUtils.deserialize(serializeSkurptdetail);
            // SKU报告1日
            byte[] serializeSkurptdetailSerial = CACHE.getByteValue("mkt_skurptDetailSerial");
            HashMap<String, Map<String, Object>> skuRptDetailSerial =
                SerializationUtils.deserialize(serializeSkurptdetailSerial);
            // PPC昨日数据 系列
            byte[] serializePpcYesterSerial = CACHE.getByteValue("mkt_ppcyestSerial");
            HashMap<String, Map<String, Object>> ppcYesterSerial =
                SerializationUtils.deserialize(serializePpcYesterSerial);
            // 建议价格近三天
            byte[] serializeAdviceprice3 = CACHE.getByteValue("mkt_adviceprice3");
            HashMap<String, Map<String, Map<String, Object>>> advicePrice3Day =
                SerializationUtils.deserialize(serializeAdviceprice3);
            // 建议价格
            byte[] serializeAdprice = CACHE.getByteValue("mkt_adprice");
            HashMap<String, Map<String, Object>> adPrice = SerializationUtils.deserialize(serializeAdprice);
            // PPC系列与组创建日期
            byte[] serializePpcInfo = CACHE.getByteValue("mkt_ppcinfo");
            HashMap<String, Map<String, Object>> ppcInfo = SerializationUtils.deserialize(serializePpcInfo);
            // SKU报表三日平均
            byte[] serializeSkurpt3avg = CACHE.getByteValue("mkt_skurpt3avg");
            HashMap<String, Map<String, Object>> skuRpt3Avg = SerializationUtils.deserialize(serializeSkurpt3avg);
            // 营销国别参数表
            byte[] serializePoporgInfo = CACHE.getByteValue("mkt_poporginfo");
            HashMap<String, Map<String, Object>> popOrgInfo = SerializationUtils.deserialize(serializePoporgInfo);
            // SKU报告7日
            byte[] serializeSkurpt = CACHE.getByteValue("mkt_skurpt");
            HashMap<String, Map<String, Object>> skuRpt = SerializationUtils.deserialize(serializeSkurpt);
            // Amazon每日价格
            byte[] serializeDailyPrice = CACHE.getByteValue("mkt_dailyprice");
            HashMap<String, Object> dailyPrice = SerializationUtils.deserialize(serializeDailyPrice);
            // SKU花费标准
            BigDecimal skuCostStandard = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "SKUCOST").get("aos_value");
            // SKU曝光标准
            BigDecimal skuExptandard = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "EXPOSURE").get("aos_value");
            // 国别标准ROI
            BigDecimal popOrgRoist = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "ROIST").get("aos_value");
            // SKU曝光点击
            BigDecimal skuexp = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "SKUEXP").get("aos_value");
            // 最高标准1(定价<200)
            BigDecimal high1 = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "HIGH1").get("aos_value");
            // 最高标准2(200<=定价<500)
            BigDecimal high2 = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "HIGH2").get("aos_value");
            // 最高标准3(定价>=500)
            BigDecimal high3 = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "HIGH3").get("aos_value");
            HashMap<String, Object> act = generateAct();
            HashMap<String, Object> rank = generateRank(pOrgId);
            Map<String, Object> cateSeason = getCateSeason(pOuCode);
            QFilter qfDate = new QFilter("aos_date", "=", todayD);
            QFilter qfOrg = new QFilter("aos_orgid.number", "=", pOuCode);
            QFilter qfType = new QFilter("aos_entryentity.aos_groupstatus", "=", "AVAILABLE");
            QFilter qfRoi = new QFilter("aos_entryentity.aos_roiflag", "=", false);
            QFilter[] filters = new QFilter[] {qfDate, qfOrg, qfType, qfRoi};
            String selectField = "aos_billno,aos_orgid,aos_poptype,aos_entryentity.aos_productno aos_productno,"
                + "aos_entryentity.aos_seasonseting aos_seasonseting,"
                + "aos_entryentity.aos_contryentrystatus aos_contryentrystatus,"
                + "aos_entryentity.aos_cn_name aos_cn_name," + "aos_entryentity.aos_itemnumer aos_itemnumer,"
                + "aos_entryentity.aos_avadays aos_avadays," + "aos_entryentity.aos_bid aos_bid,"
                + "aos_entryentity.aos_lastpricedate aos_lastpricedate," + "aos_entryentity.aos_lastbid aos_lastbid,"
                + "aos_entryentity.aos_groupdate aos_groupdate," + "aos_entryentity.aos_roi7days aos_roi7days,"
                + "aos_entryentity.aos_itemid aos_itemid," + "aos_entryentity.aos_sal_group aos_sal_group,"
                + "aos_entryentity.id aos_ppcentryid," + "id," + "aos_entryentity.aos_highvalue aos_highvalue,"
                + "aos_entryentity.aos_basestitem aos_basestitem," + "aos_entryentity.aos_age aos_age,"
                + "aos_entryentity.aos_overseaqty aos_overseaqty," + "aos_entryentity.aos_is_saleout aos_is_saleout,"
                + "aos_entryentity.aos_category1 aos_category1," + "aos_entryentity.aos_category2 aos_category2,"
                + "aos_entryentity.aos_special aos_special," + "aos_entryentity.aos_offline aos_offline";
            DynamicObjectCollection aosMktPopularPpcS =
                QueryServiceHelper.query("aos_mkt_popular_ppc", selectField, filters, "aos_entryentity.aos_productno");
            DynamicObject head = aosMktPopularPpcS.get(0);
            DynamicObject aosMktPopularAdjpn = BusinessDataServiceHelper.newDynamicObject("aos_mkt_popular_adjpn");
            aosMktPopularAdjpn.set("aos_billno", head.get("aos_billno"));
            aosMktPopularAdjpn.set("aos_orgid", pOrgId);
            aosMktPopularAdjpn.set("billstatus", "A");
            aosMktPopularAdjpn.set("aos_status", "A");
            aosMktPopularAdjpn.set("aos_makeby", SYSTEM);
            aosMktPopularAdjpn.set("aos_makedate", todayD);
            aosMktPopularAdjpn.set("aos_ppcid", head.get("id"));
            OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_popular_adjpn",
                new DynamicObject[] {aosMktPopularAdjpn}, OperateOption.create());
            Object aosSourceid = operationrst.getSuccessPkIds().get(0);
            DynamicObject aosMktPopadjustpData = BusinessDataServiceHelper.newDynamicObject("aos_mkt_popadjustp_data");
            aosMktPopadjustpData.set("aos_billno", head.get("aos_billno"));
            aosMktPopadjustpData.set("aos_orgid", pOrgId);
            aosMktPopadjustpData.set("billstatus", "A");
            aosMktPopadjustpData.set("aos_sourceid", aosSourceid);
            aosMktPopadjustpData.set("aos_makedate", todayD);
            // 获取 出价调整(销售)相同单号表中的物料 这些物料需要做排除 出价调整(销售)先生成 出价调整(推广)后生成
            QFilter qfBillno = new QFilter("aos_billno", "=", head.get("aos_billno"));
            filters = new QFilter[] {qfBillno};
            selectField = "aos_detailentry.aos_itemid aos_itemid";
            DynamicObjectCollection aosMktPopadjustsDataS =
                QueryServiceHelper.query("aos_mkt_popadjusts_data", selectField, filters);
            Map<String, Object> adjustpMap = new HashMap<>(16);
            for (DynamicObject aosMktPopadjustsData : aosMktPopadjustsDataS) {
                String aosItemid = String.valueOf(aosMktPopadjustsData.get("aos_itemid"));
                adjustpMap.put(aosItemid, aosItemid);
            }
            DynamicObjectCollection aosDetailentryS =
                aosMktPopadjustpData.getDynamicObjectCollection("aos_detailentry");
            FndLog log = FndLog.init("MKT_出价调整(推广)", pOuCode.toString() + year + month + day);
            for (DynamicObject aosMktPopularPpc : aosMktPopularPpcS) {
                long itemId = aosMktPopularPpc.getLong("aos_itemid");
                String aosItemnumer = aosMktPopularPpc.getString("aos_itemnumer");
                if (adjustpMap.get(String.valueOf(itemId)) != null) {
                    log.add(aosItemnumer + "出价调整(销售)中存在跳过");
                    continue;// 出价调整(销售)中如果存在则 出价调整(推广)不生成
                }
                // Sku报告
                Map<String, Object> skuRptDetailMap = skuRptDetail.get(pOrgId + "~" + itemId);
                BigDecimal aosAvgexp = BigDecimal.ZERO;
                BigDecimal aosClicks = BigDecimal.ZERO;
                BigDecimal aosGroupcost = BigDecimal.ZERO;
                if (skuRptDetailMap != null) {
                    aosAvgexp = (BigDecimal)skuRptDetailMap.get("aos_impressions");
                    aosClicks = (BigDecimal)skuRptDetailMap.get("aos_clicks");
                    aosGroupcost = (BigDecimal)skuRptDetailMap.get("aos_spend");
                }
                BigDecimal aosExprate = BigDecimal.ZERO;
                if (aosAvgexp.compareTo(BigDecimal.ZERO) != 0) {
                    aosExprate = aosClicks.divide(aosAvgexp, 6, RoundingMode.HALF_UP);
                }
                BigDecimal aosCpc = BigDecimal.ZERO;
                if (aosClicks.compareTo(BigDecimal.ZERO) != 0) {
                    aosCpc = aosGroupcost.divide(aosClicks, 6, RoundingMode.HALF_UP);
                }
                // 对于类型进行计算

                Map<String, Object> skuRpt3AvgMap = skuRpt3Avg.get(pOrgId + "~" + itemId);
                BigDecimal impress3Avg = BigDecimal.ZERO;
                if (skuRpt3AvgMap != null) {
                    impress3Avg = (BigDecimal)skuRpt3AvgMap.get("aos_impressions");
                }
                // Sku报告
                // 定价 Amazon每日价格
                // 最高标准 根据国别定价筛选
                String aosSeasonseting = aosMktPopularPpc.getString("aos_seasonseting");
                DynamicObject aosDetailentry = aosDetailentryS.addNew();
                aosDetailentry.set("aos_poptype", head.get("aos_poptype"));
                aosDetailentry.set("aos_productno", aosMktPopularPpc.get("aos_productno"));
                aosDetailentry.set("aos_season", aosSeasonseting);
                aosDetailentry.set("aos_itemstatus", aosMktPopularPpc.get("aos_contryentrystatus"));
                aosDetailentry.set("aos_itemname", aosMktPopularPpc.get("aos_cn_name"));
                aosDetailentry.set("aos_itemnumer", aosMktPopularPpc.get("aos_itemnumer"));
                aosDetailentry.set("aos_avadays", aosMktPopularPpc.getInt("aos_avadays"));
                aosDetailentry.set("aos_calprice", aosMktPopularPpc.get("aos_bid"));
                aosDetailentry.set("aos_adjprice", aosMktPopularPpc.get("aos_bid"));
                aosDetailentry.set("aos_lastdate", aosMktPopularPpc.get("aos_lastpricedate"));
                aosDetailentry.set("aos_lastprice", aosMktPopularPpc.get("aos_lastbid"));
                aosDetailentry.set("aos_grouproi", aosMktPopularPpc.get("aos_roi7days"));
                aosDetailentry.set("aos_itemid", itemId);
                aosDetailentry.set("aos_sal_group", aosMktPopularPpc.get("aos_sal_group"));
                aosDetailentry.set("aos_ppcentryid", aosMktPopularPpc.get("aos_ppcentryid"));
                // 最高价
                aosDetailentry.set("aos_highvalue", aosMktPopularPpc.get("aos_highvalue"));
                // 滞销类型
                aosDetailentry.set("aos_basestitem", aosMktPopularPpc.get("aos_basestitem"));
                // 最大库龄
                aosDetailentry.set("aos_age", aosMktPopularPpc.get("aos_age"));
                aosDetailentry.set("aos_is_saleout", aosMktPopularPpc.get("aos_is_saleout"));
                aosDetailentry.set("aos_overseaqty", aosMktPopularPpc.get("aos_overseaqty"));
                aosDetailentry.set("aos_offline", aosMktPopularPpc.get("aos_offline"));
                aosDetailentry.set("aos_special", aosMktPopularPpc.get("aos_special"));
                String aosCategory1 = aosMktPopularPpc.getString("aos_category1");
                String aosCategory2 = aosMktPopularPpc.getString("aos_category2");
                Object aosSeasonattr = cateSeason.getOrDefault(aosCategory1 + "~" + aosCategory2, null);
                aosDetailentry.set("aos_seasonattr", aosSeasonattr);
                // AM搜索排名
                aosDetailentry.set("aos_rank", rank.get(String.valueOf(itemId)));
                Object aosActivity = act.get(pOrgId + "~" + itemId);
                if (aosActivity != null) {
                    aosDetailentry.set("aos_activity", aosActivity);
                }
                BigDecimal aosBidSuggest = BigDecimal.ZERO;
                // 组
                Map<String, Object> adPriceMap = adPrice.get(pOrgId + "~" + aosItemnumer);
                if (adPriceMap != null) {
                    aosBidSuggest = (BigDecimal)adPriceMap.get("aos_bid_suggest");
                }
                // 建议价
                aosDetailentry.set("aos_bidsuggest", aosBidSuggest);
                aosDetailentry.set("aos_avgexp", impress3Avg);
                if (skuRpt3AvgMap != null) {
                    impress3Avg = (BigDecimal)skuRpt3AvgMap.get("aos_impressions");
                }
                if (impress3Avg.compareTo(BigDecimal.ZERO) != 0) {
                    aosExprate = aosClicks.divide(impress3Avg, 6, RoundingMode.HALF_UP);
                }
                aosDetailentry.set("aos_exprate", aosExprate);
                aosDetailentry.set("aos_groupcost", aosGroupcost);
                BigDecimal aosSpend = BigDecimal.ZERO;
                // Sku报告1日数据
                Object lastSpendObj = skuRptDetailSerial.get(pOrgId + "~" + aosMktPopularPpc.get("aos_productno"));
                if (!Cux_Common_Utl.IsNull(lastSpendObj)) {
                    aosSpend = (BigDecimal)lastSpendObj;
                }
                BigDecimal aosSerialrate = BigDecimal.ZERO;
                // 系列花出率 = 前一天花出/预算
                Map<String, Object> ppcYesterMap =
                    ppcYesterSerial.get(pOrgId + "~" + aosMktPopularPpc.get("aos_productno"));
                if (ppcYesterMap != null) {
                    // 昨日出价
                    BigDecimal aosBudget = (BigDecimal)ppcYesterMap.get("aos_budget");
                    if (aosBudget.compareTo(BigDecimal.ZERO) != 0) {
                        aosSerialrate = aosSpend.divide(aosBudget, 2, RoundingMode.HALF_UP);
                    }
                }
                aosDetailentry.set("aos_serialrate", aosSerialrate);
                // 系统建议策略 aos_sys
                BigDecimal aosLastprice = aosDetailentry.getBigDecimal("aos_lastprice");
                BigDecimal aosCalprice = aosDetailentry.getBigDecimal("aos_calprice");
                if (aosLastprice.compareTo(BigDecimal.ZERO) != 0 && aosCalprice.compareTo(aosLastprice) > 0) {
                    aosDetailentry.set("aos_sys", "提出价");
                }
                if (aosCalprice.compareTo(aosLastprice) < 0) {
                    aosDetailentry.set("aos_sys", "降出价");
                }
                // CPC
                aosDetailentry.set("aos_cpc", aosCpc);
                // 调整比例
                // 建议价
                BigDecimal aosBidsuggest = aosDetailentry.getBigDecimal("aos_bidsuggest");
                // 手动调整
                BigDecimal aosAdjprice = aosDetailentry.getBigDecimal("aos_adjprice");
                BigDecimal aosRatio = BigDecimal.ZERO;
                if (aosBidsuggest.compareTo(BigDecimal.ZERO) != 0) {
                    aosRatio = aosAdjprice.divide(aosBidsuggest, 2, RoundingMode.HALF_UP);
                }
                aosDetailentry.set("aos_ratio", aosRatio);
            }

            for (DynamicObject aosDetailentry : aosDetailentryS) {
                try {
                    String aosItemnumer = aosDetailentry.getString("aos_itemnumer");
                    // Sku报告
                    Map<String, Map<String, Object>> advicePrice3DayMap =
                        advicePrice3Day.get(pOrgId + "~" + aosItemnumer);
                    if (advicePrice3DayMap == null) {
                        continue;
                    }
                    DynamicObjectCollection aosSubentryentityS =
                        aosDetailentry.getDynamicObjectCollection("aos_subentryentity1");
                    // 循环ROI
                    for (String dateStr : advicePrice3DayMap.keySet()) {
                        Map<String, Object> advicePrice3DayMapD = advicePrice3DayMap.get(dateStr);
                        BigDecimal aosBidSuggest = (BigDecimal)advicePrice3DayMapD.get("aos_bid_suggest");
                        BigDecimal aosBidRangestart = (BigDecimal)advicePrice3DayMapD.get("aos_bid_rangestart");
                        BigDecimal aosBidRangeend = (BigDecimal)advicePrice3DayMapD.get("aos_bid_rangeend");
                        DynamicObject aosSubentryentity = aosSubentryentityS.addNew();
                        Date aosDateD2 = df.parse(dateStr);
                        aosSubentryentity.set("aos_date_d2", aosDateD2);
                        aosSubentryentity.set("aos_bid_suggest", aosBidSuggest);
                        aosSubentryentity.set("aos_bid_rangestart", aosBidRangestart);
                        aosSubentryentity.set("aos_bid_rangeend", aosBidRangeend);
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
            // 组数据( 15天 ) 循环 单据体循环
            // 查找15天前的 ppc平台数据
            // 查询条件
            List<QFilter> listQfs = new ArrayList<>(10);
            // 状态可用
            QFilter qfStatus = new QFilter("aos_entryentity.aos_groupstatus", "=", "AVAILABLE");
            listQfs.add(qfStatus);
            Calendar cal = new GregorianCalendar();
            cal.setTime(df.parse(df.format(today.getTime())));
            // 待删除
            cal.add(Calendar.DATE, 1);
            // 制单日期小于今天
            QFilter qfDateMax = new QFilter("aos_date", "<", cal.getTime());
            listQfs.add(qfDateMax);
            cal.add(Calendar.DATE, -15);
            // 制单日期大于等于15天前
            QFilter qfDateMin = new QFilter("aos_date", ">=", cal.getTime());
            listQfs.add(qfDateMin);
            // 国别
            QFilter qfOrg2 = new QFilter("aos_orgid", "=", pOrgId);
            listQfs.add(qfOrg2);
            int size = listQfs.size();
            QFilter[] qfs = listQfs.toArray(new QFilter[size]);
            // 查询字段
            StringJoiner str = new StringJoiner(",");
            str.add("substring(aos_date,1,10) as aos_date");
            // 物料
            str.add("aos_entryentity.aos_itemid as aos_itemid");
            // 出价
            str.add("aos_entryentity.aos_bid as aos_ppcbid");
            // 置顶位置出价
            str.add("aos_entryentity.aos_topprice as aos_topprice");
            DynamicObjectCollection dycPpc = QueryServiceHelper.query("aos_mkt_popular_ppc", str.toString(), qfs);
            Map<String, BigDecimal[]> mapPpc = dycPpc.stream()
                .collect(Collectors.toMap(e -> e.getString("aos_date") + "?" + e.getString("aos_itemid"), e -> {
                    BigDecimal[] big = new BigDecimal[2];
                    big[0] = BigDecimal.ZERO;
                    if (e.getBigDecimal(AOS_PPCBID) != null) {
                        big[0] = e.getBigDecimal("aos_ppcbid");
                    }
                    big[1] = BigDecimal.ZERO;
                    if (e.getBigDecimal(AOS_TOPPRICE) != null) {
                        big[1] = e.getBigDecimal("aos_topprice");
                    }
                    return big;
                }));
            for (DynamicObject aosDetailentry : aosDetailentryS) {
                try {
                    long itemId = aosDetailentry.getLong("aos_itemid");
                    // Sku报告
                    Map<String, Map<String, Object>> skuRptMap14 = skuRpt14Detail.get(pOrgId + "~" + itemId);
                    if (skuRptMap14 == null) {
                        continue;
                    }
                    DynamicObjectCollection aosSubentryentityS =
                        aosDetailentry.getDynamicObjectCollection("aos_subentryentity");
                    // 循环ROI
                    for (String dateStr : skuRptMap14.keySet()) {
                        Map<String, Object> skuRptMap14D = skuRptMap14.get(dateStr);
                        BigDecimal aosExpouse = (BigDecimal)skuRptMap14D.get("aos_impressions");
                        BigDecimal aosRoi = (BigDecimal)skuRptMap14D.get("aos_roi");
                        BigDecimal aosBid = (BigDecimal)skuRptMap14D.get("aos_bid");
                        BigDecimal aosCost = (BigDecimal)skuRptMap14D.get("aos_spend");
                        DynamicObject aosSubentryentity = aosSubentryentityS.addNew();
                        Date aosDateD = df.parse(dateStr);
                        aosSubentryentity.set("aos_date_d", aosDateD);
                        aosSubentryentity.set("aos_expouse", aosExpouse);
                        aosSubentryentity.set("aos_roi", aosRoi);
                        aosSubentryentity.set("aos_bid", aosBid);
                        aosSubentryentity.set("aos_cost", aosCost);
                        String key = df.format(aosDateD) + "?" + itemId;
                        if (mapPpc.containsKey(key)) {
                            BigDecimal[] value = mapPpc.get(key);
                            aosSubentryentity.set("aos_ppcbid", value[0]);
                            aosSubentryentity.set("aos_topprice", value[1]);
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }

            for (DynamicObject aosDetailentry : aosDetailentryS) {
                String aosItemnumer = aosDetailentry.getString("aos_itemnumer");
                int aosAvadays = aosDetailentry.getInt("aos_avadays");
                long itemId = aosDetailentry.getLong("aos_itemid");
                BigDecimal aosBid = aosDetailentry.getBigDecimal("aos_calprice");
                BigDecimal aosExprate = aosDetailentry.getBigDecimal("aos_exprate");
                String aosSeasonseting = aosDetailentry.getString("aos_season");
                // 对于类型进行计算
                Map<String, Object> ppcInfoMap = ppcInfo.get(pOrgId + "~" + itemId);
                // 首次建组日期
                Object aosGroupdate = null;
                if (ppcInfoMap != null) {
                    aosGroupdate = ppcInfoMap.get("aos_groupdate");
                }
                if (aosGroupdate == null) {
                    aosGroupdate = todayD;
                }
                Map<String, Object> skuRpt3AvgMap = skuRpt3Avg.get(pOrgId + "~" + itemId);
                BigDecimal spend3Avg = BigDecimal.ZERO;
                BigDecimal impress3Avg = BigDecimal.ZERO;
                if (skuRpt3AvgMap != null) {
                    spend3Avg = (BigDecimal)skuRpt3AvgMap.get("aos_spend");
                    impress3Avg = (BigDecimal)skuRpt3AvgMap.get("aos_impressions");
                }
                // Sku报告
                Map<String, Object> skuRptMap = skuRpt.get(pOrgId + "~" + itemId);
                // 7天ROI
                BigDecimal roi7Days = BigDecimal.ZERO;
                if (skuRptMap != null && ((BigDecimal)skuRptMap.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                    roi7Days = ((BigDecimal)skuRptMap.get("aos_total_sales"))
                        .divide((BigDecimal)skuRptMap.get("aos_spend"), 2, RoundingMode.HALF_UP);
                }
                // 定价 Amazon每日价格
                Object fixValue = dailyPrice.get(pOrgId + "~" + itemId);
                // 最高标准 根据国别定价筛选
                BigDecimal highStandard = getHighStandard(fixValue, high1, high2, high3);
                log.add(aosItemnumer + "前3天日平均花费 =" + spend3Avg);
                log.add(aosItemnumer + "SKU花费标准=" + skuCostStandard);
                log.add(aosItemnumer + "7天ROI=" + roi7Days);
                log.add(aosItemnumer + "国别ROI标准=" + popOrgRoist);
                log.add(aosItemnumer + "可售库存天数=" + aosAvadays);
                log.add(aosItemnumer + "出价=" + aosBid);
                log.add(aosItemnumer + "出价最高标准=" + highStandard);
                log.add(aosItemnumer + "前3天日平均花费=" + impress3Avg);
                log.add(aosItemnumer + "国别标准SKU花费标准=" + skuExptandard);
                log.add(aosItemnumer + "曝光点击率=" + aosExprate);
                log.add(aosItemnumer + "曝光点击国别标准=" + skuexp);
                boolean spendbool = spend3Avg.multiply(BigDecimal.valueOf(3))
                    .compareTo(skuCostStandard.multiply(BigDecimal.valueOf(3))) > 0;
                boolean cond = ((getBetweenDays(todayD, (Date)aosGroupdate) > 7) && spendbool
                    && (roi7Days.compareTo(BigDecimal.valueOf(5)) < 0)
                    && ("春夏产品".equals(aosSeasonseting) || "春夏常规品".equals(aosSeasonseting)));
                if ((getBetweenDays(todayD, (Date)aosGroupdate) > 7) && spendbool
                    && (roi7Days.compareTo(popOrgRoist) < 0) && !"春夏产品".equals(aosSeasonseting)
                    && !"春夏常规品".equals(aosSeasonseting)) {
                    // 1.ROI差花费高 首次建组>7天，前3天日平均花费≥SKU花费标准(取参数表<SKU花费标准>)，且
                    // 7天ROI＜国别ROI标准;
                    aosDetailentry.set("aos_type", "ROILOW");
                } else if (cond) {
                    // 1.ROI差花费高 首次建组>7天，前3天日平均花费≥SKU花费标准(取参数表<SKU花费标准>)，且
                    // 7天ROI＜国别ROI标准;
                    aosDetailentry.set("aos_type", "ROILOW");
                } else if ((roi7Days.compareTo(popOrgRoist) > 0)
                    && (impress3Avg.multiply(BigDecimal.valueOf(3))
                        .compareTo(skuExptandard.multiply(BigDecimal.valueOf(3)).multiply(BigDecimal.valueOf(0.5))) > 0)
                    && (spend3Avg.multiply(BigDecimal.valueOf(3)).compareTo(
                        skuCostStandard.multiply(BigDecimal.valueOf(3)).multiply(BigDecimal.valueOf(0.2))) < 0)
                    && (aosExprate.compareTo(skuexp) < 0)) {
                    // 2.ROI好花费低
                    // 7天ROI＞标准，前3曝光＞国别标准(取参数表<曝光标准>)，前3天日平均花费＜国别标准*20%，曝光点击率＜国别标准(取参数表<曝光点击>);
                    aosDetailentry.set("aos_type", "ROIHIGH");
                } else {
                    aosDetailentry.set("aos_type", "OTHER");
                }

            }
            // 保存正式表
            OperationServiceHelper.executeOperate("save", "aos_mkt_popadjustp_data",
                new DynamicObject[] {aosMktPopadjustpData}, OperateOption.create());
            // 保存日志表
            log.finnalSave();
        } catch (Exception e) {
            String message = e.toString();
            String exceptionStr = SalUtil.getExceptionStr(e);
            String messageStr = message + "\r\n" + exceptionStr;
            logger.error(messageStr);
        } finally {
            try {
                setCache();
            } catch (Exception e) {
                String message = e.toString();
                String exceptionStr = SalUtil.getExceptionStr(e);
                String messageStr = message + "\r\n" + exceptionStr;
                logger.error(messageStr);
            }
        }
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

    private static HashMap<String, Object> generateRank(Object pOrgId) {
        HashMap<String, Object> rank = new HashMap<>(16);
        QFilter filterOrg = new QFilter("aos_orgid", "=", pOrgId);
        QFilter[] filters = new QFilter[] {filterOrg};
        DynamicObjectCollection aosBaseRankS =
            QueryServiceHelper.query("aos_base_rank", "aos_itemid,aos_rank", filters);
        for (DynamicObject aosBaseRank : aosBaseRankS) {
            rank.put(aosBaseRank.getString("aos_itemid"), aosBaseRank.get("aos_rank"));
        }
        return rank;
    }

    public static HashMap<String, Object> generateAct() {
        HashMap<String, Object> act = new HashMap<>(16);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        Date dateFrom = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, 7);
        Date dateTo = calendar.getTime();
        // 日期格式化
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterDate1 = new QFilter("aos_sal_actplanentity.aos_l_startdate", ">=", dateFromStr)
            .and("aos_sal_actplanentity.aos_l_startdate", "<=", dateToStr);
        QFilter filterDate2 = new QFilter("aos_sal_actplanentity.aos_enddate", ">=", dateFromStr)
            .and("aos_sal_actplanentity.aos_enddate", "<=", dateToStr);
        QFilter filterDate3 = new QFilter("aos_sal_actplanentity.aos_l_startdate", "<=", dateFromStr)
            .and("aos_sal_actplanentity.aos_enddate", ">=", dateToStr);
        QFilter filterDate = filterDate1.or(filterDate2).or(filterDate3);
        QFilter filterAma = new QFilter("aos_channel.number", "=", "AMAZON");
        QFilter[] filters = new QFilter[] {filterDate, filterAma};
        String select =
            "aos_nationality.id aos_orgid,aos_shop,aos_acttype,aos_sal_actplanentity.aos_itemnum.id aos_itemid,"
                + "aos_acttype.number aos_acttypeNumber,aos_sal_actplanentity.aos_l_startdate aos_l_startdate,aos_sal_actplanentity.aos_enddate aos_enddate";
        DataSet aosActSelectPlanS = QueryServiceHelper.queryDataSet("aos_mkt_popadjs_init.GenerateAct",
            "aos_act_select_plan", select, filters, "aos_makedate");
        // 查询店铺活动类型参数
        String selectFields = "aos_org,aos_shop,aos_acttype";
        DataSet aosSalActTypePs =
            QueryServiceHelper.queryDataSet("aos_mkt_popadjs_init.GenerateAct", "aos_sal_act_type_p", selectFields,
                new QFilter[] {new QFilter("aos_assessment.number", QCP.equals, "Y")}, null);
        aosActSelectPlanS = aosActSelectPlanS.join(aosSalActTypePs, JoinType.INNER).on("aos_orgid", "aos_org")
            .on("aos_shop", "aos_shop").on("aos_acttype", "aos_acttype")
            .select("aos_orgid", "aos_itemid", "aos_acttypeNumber", "aos_l_startdate", "aos_enddate").finish();
        String[] groupBy =
            new String[] {"aos_orgid", "aos_itemid", "aos_acttypeNumber", "aos_l_startdate", "aos_enddate"};
        aosActSelectPlanS = aosActSelectPlanS.groupBy(groupBy).finish();
        SimpleDateFormat sim = new SimpleDateFormat("MM-dd");
        while (aosActSelectPlanS.hasNext()) {
            Row aosActSelectPlan = aosActSelectPlanS.next();
            StringBuilder sign = new StringBuilder();
            if (aosActSelectPlan.get("aos_acttypeNumber") != null) {
                sign.append(aosActSelectPlan.getString("aos_acttypeNumber"));
            }
            sign.append("(");
            if (aosActSelectPlan.get("aos_l_startdate") != null) {
                sign.append(sim.format(aosActSelectPlan.getDate("aos_l_startdate")));
            }
            sign.append("-");
            if (aosActSelectPlan.get("aos_enddate") != null) {
                sign.append(sim.format(aosActSelectPlan.getDate("aos_enddate")));
            }
            sign.append(")");
            act.put(aosActSelectPlan.getString("aos_orgid") + "~" + aosActSelectPlan.getLong("aos_itemid"),
                sign.toString());
        }
        aosActSelectPlanS.close();
        aosSalActTypePs.close();
        return act;
    }

    /**
     * 在缓存里面添加完成国别数量，当所有国别都完成了以后，发送邮件
     */
    synchronized public static void setCache() throws ParseException {
        // 判断是否完成
        Optional<String> op = Optional.ofNullable(CACHE.get("saveOrgs"));
        // 保存的国家数量
        String orgs = op.orElse("0");
        int saveOrgs = Integer.parseInt(orgs);
        saveOrgs++;
        // 获取当前时间，判断是早上还是下午，早上是欧洲，下午是美加
        Date dateNow = new Date();
        SimpleDateFormat sim = new SimpleDateFormat("yyyy-MM-dd");
        Calendar cal = new GregorianCalendar();
        cal.setTime(sim.parse(sim.format(dateNow)));
        cal.add(Calendar.HOUR, 16);
        int type = dateNow.compareTo(cal.getTime());
        // 早上 欧洲
        if (type <= 0) {
            if (saveOrgs >= FIVE) {
                // 缓存清空
                CACHE.remove("saveOrgs");
            } else {
                CACHE.put("saveOrgs", String.valueOf(saveOrgs));
            }
        }
        // 下午 美加
        if (type > 0) {
            if (saveOrgs >= TWO) {
                // 缓存清空
                CACHE.remove("saveOrgs");
            } else {
                CACHE.put("saveOrgs", String.valueOf(saveOrgs));
            }
        }
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {}
}