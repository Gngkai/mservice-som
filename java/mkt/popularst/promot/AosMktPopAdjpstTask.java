package mkt.popularst.promot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import common.Cux_Common_Utl;
import common.fnd.FndGlobal;
import common.fnd.FndLog;
import common.sal.util.SalUtil;
import kd.bos.cache.CacheFactory;
import kd.bos.cache.DistributeSessionlessCache;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.db.DB;
import kd.bos.db.DBRoute;
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
import mkt.common.AosMktGenerate;

/**
 * @author aosom
 * @version ST出价调整推广-调度任务类
 */
public class AosMktPopAdjpstTask extends AbstractTask {
    public final static String SYSTEM = Cux_Common_Utl.SYSTEM;
    private static final String DB_MKT = "aos.mkt";
    private static final int TWOH = 200;
    private static final int TWO = 2;
    private static final int FIVE = 5;
    private static final String AOS_PPCBID = "aos_ppcbid";

    private static final DistributeSessionlessCache CACHE =
        CacheFactory.getCommonCacheFactory().getDistributeSessionlessCache("mkt_redis");
    private static final Log logger = LogFactory.getLog(AosMktPopAdjpstTask.class);

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
            String pOuCode = (String)param.get("p_ou_code");
            Object pOrgId = FndGlobal.get_import_id(pOuCode, "bd_country");
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
            QFilter filterOrg = new QFilter("aos_orgid.number", "=", pOuCode);
            QFilter filterDate = new QFilter("aos_makedate", "=", todayD);
            QFilter[] filtersAdj = new QFilter[] {filterOrg, filterDate};
            DeleteServiceHelper.delete("aos_mkt_pop_adjpst", filtersAdj);
            DeleteServiceHelper.delete("aos_mkt_pop_adjpstdt", filtersAdj);
            // 初始化数据
            // 营销国别标准参数
            HashMap<String, Map<String, Object>> popOrgInfo = AosMktGenerate.GeneratePopOrgInfo(pOuCode);
            // 3日组报告
            HashMap<String, Map<String, Object>> skuRpt3Avg = AosMktGenerate.GenerateSkuRpt3Group(pOuCode);
            // 7日组报告
            HashMap<String, Map<String, Object>> skuRptGroup = AosMktGenerate.GenerateSkuRpt7Group(pOuCode);
            HashMap<String, Map<String, Object>> skuRpt = AosMktGenerate.GenerateSkuRpt7(pOuCode);
            // 词昨日报告
            HashMap<String, Map<String, Object>> skuRpt1 = AosMktGenerate.GenerateKeyRpt1(pOuCode);
            // 词每日报告
            HashMap<String, Map<String, Map<String, Object>>> keyRpt14 = AosMktGenerate.GenerateSkuRpt14(pOuCode);
            // 昨日数据
            HashMap<String, Map<String, Object>> ppcYester = AosMktGenerate.GeneratePpcYesterSt(pOuCode);
            // 亚马逊定价
            HashMap<String, Object> dailyPrice = AosMktGenerate.GenerateDailyPrice(pOuCode);
            // 今日数据源组关键词个数
            HashMap<String, Object> ppcTodayGroup = AosMktGenerate.GeneratePpcTodayStGroup(pOuCode);
            // 关键词建议价格
            HashMap<String, Map<String, Object>> adPriceKey = AosMktGenerate.GenerateAdvKeyPrice(pOuCode);
            HashMap<String, Map<String, Object>> adPriceKey14 = AosMktGenerate.GenerateAdvKeyPrice14(pOuCode);
            // 营销国别参数
            // 最高标准1(定价<200)
            BigDecimal high1 = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "HIGH1").get("aos_value");
            // 最高标准2(200<=定价<500)
            BigDecimal high2 = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "HIGH2").get("aos_value");
            // 最高标准3(定价>=500)
            BigDecimal high3 = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "HIGH3").get("aos_value");
            // 国别标准ROI
            BigDecimal popOrgRoist = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "ROIST").get("aos_value");
            // SKU花费标准
            BigDecimal skuCostStandard = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "SKUCOST").get("aos_value");
            // SKU曝光标准
            BigDecimal skuExptandard = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "EXPOSURE").get("aos_value");
            // SKU曝光点击
            BigDecimal skuExp = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "SKUEXP").get("aos_value");
            QFilter qfDate = new QFilter("aos_date", "=", todayD);
            QFilter qfOrg = new QFilter("aos_orgid.number", "=", pOuCode);
            QFilter qfType = new QFilter("aos_keystatus", "=", "AVAILABLE");
            QFilter[] filters = new QFilter[] {qfDate, qfOrg, qfType};
            String selectField =
                "aos_sourceid id,aos_billno,aos_productno," + "aos_season,aos_category1,aos_itemname,aos_itemnumer,"
                    + "aos_keyword,aos_match_type,aos_roi7days,aos_impressions,"
                    + "aos_lastbid,aos_highvalue,aos_lastpricedate,aos_groupdate,aos_itemid,"
                    + "aos_lastbid aos_lastprice,aos_avadays," + "id aos_entryid," + "aos_bid";
            DynamicObjectCollection aosMktPopPpcstS =
                QueryServiceHelper.query("aos_mkt_ppcst_data", selectField, filters, "aos_entryentity.aos_productno");
            DynamicObject head = aosMktPopPpcstS.get(0);
            String aosBillno = head.getString("aos_billno");
            DynamicObject aosMktPopAdjpst = BusinessDataServiceHelper.newDynamicObject("aos_mkt_pop_adjpst");
            aosMktPopAdjpst.set("aos_billno", aosBillno);
            aosMktPopAdjpst.set("aos_orgid", pOrgId);
            aosMktPopAdjpst.set("billstatus", "A");
            aosMktPopAdjpst.set("aos_status", "A");
            aosMktPopAdjpst.set("aos_makeby", SYSTEM);
            aosMktPopAdjpst.set("aos_makedate", todayD);
            aosMktPopAdjpst.set("aos_ppcid", head.get("id"));
            OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_pop_adjpst",
                new DynamicObject[] {aosMktPopAdjpst}, OperateOption.create());
            Object aosSourceid = operationrst.getSuccessPkIds().get(0);
            DynamicObject aosMktPopAdjpstdt = BusinessDataServiceHelper.newDynamicObject("aos_mkt_pop_adjpstdt");
            aosMktPopAdjpstdt.set("aos_billno", aosBillno);
            aosMktPopAdjpstdt.set("aos_orgid", pOrgId);
            aosMktPopAdjpstdt.set("billstatus", "A");
            aosMktPopAdjpstdt.set("aos_sourceid", aosSourceid);
            aosMktPopAdjpstdt.set("aos_makedate", todayD);
            // 获取 出价调整(销售)相同单号表中的物料 这些物料需要做排除 出价调整(销售)先生成 出价调整(推广)后生成
            Map<String, Object> adjustpMap = generateAdjustpMap(aosBillno);
            DynamicObjectCollection aosDetailentryS = aosMktPopAdjpstdt.getDynamicObjectCollection("aos_detailentry");
            FndLog log = FndLog.init("MKT_ST出价调整(推广)", pOuCode + year + month + day);
            for (DynamicObject aosMktPopPpcst : aosMktPopPpcstS) {
                long itemId = aosMktPopPpcst.getLong("aos_itemid");
                String aosItemnumer = aosMktPopPpcst.getString("aos_itemnumer");
                String aosProductno = aosMktPopPpcst.getString("aos_productno");
                String aosSeason = aosMktPopPpcst.getString("aos_season");
                String aosCategory1 = aosMktPopPpcst.getString("aos_category1");
                String aosItemname = aosMktPopPpcst.getString("aos_itemname");
                String aosKeyword = aosMktPopPpcst.getString("aos_keyword");
                String aosMatchtype = aosMktPopPpcst.getString("aos_match_type");
                BigDecimal aosLastprice = aosMktPopPpcst.getBigDecimal("aos_lastprice");
                Date aosLastpricedate = aosMktPopPpcst.getDate("aos_lastpricedate");
                BigDecimal aosHighvalue = aosMktPopPpcst.getBigDecimal("aos_highvalue");
                Date aosGroupdate = aosMktPopPpcst.getDate("aos_groupdate");
                int aosAvadays = aosMktPopPpcst.getInt("aos_avadays");
                long aosEntryid = aosMktPopPpcst.getLong("aos_entryid");
                BigDecimal aosBid = aosMktPopPpcst.getBigDecimal("aos_bid");

                if (adjustpMap.get(String.valueOf(itemId)) != null) {
                    log.add(aosItemnumer + "ST出价调整(销售)中存在跳过");
                    continue;// 出价调整(销售)中如果存在则 出价调整(推广)不生成
                }
                DynamicObject aosDetailentry = aosDetailentryS.addNew();
                aosDetailentry.set("aos_poptype", "ST");
                aosDetailentry.set("aos_productno", aosProductno);
                aosDetailentry.set("aos_season", aosSeason);
                aosDetailentry.set("aos_category1", aosCategory1);
                aosDetailentry.set("aos_itemname", aosItemname);
                aosDetailentry.set("aos_itemnumer", aosItemnumer);
                aosDetailentry.set("aos_keyword", aosKeyword);
                aosDetailentry.set("aos_matchtype", aosMatchtype);
                aosDetailentry.set("aos_lastprice", aosLastprice);
                aosDetailentry.set("aos_lastdate", aosLastpricedate);
                aosDetailentry.set("aos_highvalue", aosHighvalue);
                aosDetailentry.set("aos_groupdate", aosGroupdate);
                aosDetailentry.set("aos_avadays", aosAvadays);
                aosDetailentry.set("aos_itemid", itemId);
                aosDetailentry.set("aos_entryid", aosEntryid);
                aosDetailentry.set("aos_calprice", aosBid);
                aosDetailentry.set("aos_adjprice", aosBid);
                String key = aosItemnumer + "~" + aosKeyword + "~" + aosMatchtype;
                // 建议价 // 组
                Map<String, Object> adPriceMap = adPriceKey.get(key);
                BigDecimal aosBidsuggest = BigDecimal.ZERO;
                if (adPriceMap != null) {
                    aosBidsuggest = (BigDecimal)adPriceMap.get("aos_bid_suggest");
                }
                aosDetailentry.set("aos_bidsuggest", aosBidsuggest);
                // 7日组维度词曝光点击率
                Map<String, Object> skuRptMap = skuRpt.get(key);
                // 点击
                BigDecimal aosClick = BigDecimal.ZERO;
                // 曝光
                BigDecimal aosImpressions = BigDecimal.ZERO;
                // 投入
                BigDecimal aosSpend = BigDecimal.ZERO;
                // 曝光点击率
                BigDecimal aosCtr = BigDecimal.ZERO;
                // 营收
                BigDecimal aosSales = BigDecimal.ZERO;
                // ROI
                BigDecimal aosRoi = BigDecimal.ZERO;
                if (skuRptMap != null) {
                    aosClick = (BigDecimal)skuRptMap.get("aos_clicks");
                    // 曝光
                    aosImpressions = (BigDecimal)skuRptMap.get("aos_impressions");
                    // 花费
                    aosSpend = (BigDecimal)skuRptMap.get("aos_spend");
                    // 营收
                    aosSales = (BigDecimal)skuRptMap.get("aos_sales");
                }
                if (aosImpressions.compareTo(BigDecimal.ZERO) != 0 && aosClick != null) {
                    aosCtr = aosClick.divide(aosImpressions, 6, RoundingMode.HALF_UP);
                }
                if (aosSpend.compareTo(BigDecimal.ZERO) != 0 && aosSales != null) {
                    aosRoi = aosSales.divide(aosSpend, 6, RoundingMode.HALF_UP);
                }
                aosDetailentry.set("aos_exposure", aosImpressions);
                aosDetailentry.set("aos_ctr", aosCtr);
                aosDetailentry.set("aos_cost", aosSpend);
                aosDetailentry.set("aos_roi", aosRoi);
                // 系列花出率 ST数据源前一天花出/预算
                BigDecimal aosSerialrate = BigDecimal.ZERO;
                // 昨日预算
                BigDecimal aosBudgetlast = BigDecimal.ZERO;
                // 前一天花出
                BigDecimal aosSpendlast = BigDecimal.ZERO;
                Map<String, Object> skuRpt1Map = skuRpt1.get(key);
                if (skuRpt1Map != null && skuRpt1Map.get("aos_spend") != null) {
                    aosSpendlast = (BigDecimal)skuRpt1Map.get("aos_spend");
                }
                Map<String, Object> ppcYesterMap = ppcYester.get(key);
                if (ppcYesterMap != null && ppcYesterMap.get("aos_budget") != null) {
                    // 昨日预算
                    aosBudgetlast = (BigDecimal)ppcYesterMap.get("aos_budget");
                }
                if (ppcYesterMap != null) {
                    if (aosBudgetlast.compareTo(BigDecimal.ZERO) != 0) {
                        aosSerialrate = aosSpendlast.divide(aosBudgetlast, 2, RoundingMode.HALF_UP);
                    }
                }
                aosDetailentry.set("aos_serialrate", aosSerialrate);
            }

            for (DynamicObject aosDetailentry : aosDetailentryS) {
                String aosItemnumer = aosDetailentry.getString("aos_itemnumer");
                int aosAvadays = aosDetailentry.getInt("aos_avadays");
                long itemId = aosDetailentry.getLong("aos_itemid");
                BigDecimal aosBid = aosDetailentry.getBigDecimal("aos_calprice");
                BigDecimal aosExprate = aosDetailentry.getBigDecimal("aos_ctr");
                Date aosGroupdate = aosDetailentry.getDate("aos_groupdate");
                Map<String, Object> skuRpt3AvgMap = skuRpt3Avg.get(aosItemnumer);
                BigDecimal spend3Avg = BigDecimal.ZERO;
                BigDecimal impress3Avg = BigDecimal.ZERO;
                if (skuRpt3AvgMap != null) {
                    spend3Avg = (BigDecimal)skuRpt3AvgMap.get("aos_spend");
                    impress3Avg = (BigDecimal)skuRpt3AvgMap.get("aos_impressions");
                }
                // Sku报告
                Map<String, Object> skuRptMap = skuRptGroup.get(aosItemnumer);
                // 7天ROI
                BigDecimal roi7Days = BigDecimal.ZERO;
                if (skuRptMap != null && ((BigDecimal)skuRptMap.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                    roi7Days = ((BigDecimal)skuRptMap.get("aos_sales")).divide((BigDecimal)skuRptMap.get("aos_spend"),
                        2, RoundingMode.HALF_UP);
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
                log.add(aosItemnumer + "曝光点击国别标准=" + skuExp);
                // 对于类型进行计算
                if ((aosAvadays < 90) && (spend3Avg.compareTo(skuCostStandard) > 0)) {
                    // 1.可售低花费高 可售天数＜90天(在途+在库数量计算可售天数)，前3天日平均花费≥国别标准SKU花费标准;
                    aosDetailentry.set("aos_type", "AVALOW");
                } else if ((getBetweenDays(todayD, aosGroupdate) > 7) && (spend3Avg.compareTo(skuCostStandard) > 0)
                    && (roi7Days.compareTo(popOrgRoist) < 0) && (impress3Avg.compareTo(skuExptandard) > 0)) {
                    // 2.ROI差花费高 首次建组>7天，前3天日平均花费≥SKU花费标准(取参数表<SKU花费标准>)，且
                    // 7天ROI＜国别ROI标准，且前3平均曝光>国别标准(取参数表<曝光标准>);
                    aosDetailentry.set("aos_type", "ROILOW");
                } else if ((aosAvadays > 120) && (impress3Avg.compareTo(skuExptandard) < 0)
                    && (aosBid.compareTo(highStandard) < 0))
                // 3.曝光低可售高 可售天数＞120天(在途+在库数量计算)，前3平均曝光＜国别标准(取参数表<曝光标准>)*50%，出价<出价最高标准(根据定价分别
                // 取参数表<出价最高标准>);
                {
                    aosDetailentry.set("aos_type", "EXPLOW");
                } else if ((roi7Days.compareTo(popOrgRoist) > 0)
                    && (impress3Avg.compareTo(skuExptandard.multiply(BigDecimal.valueOf(0.5))) > 0)
                    && (spend3Avg.compareTo(skuCostStandard.multiply(BigDecimal.valueOf(0.2))) < 0)
                    && (aosExprate.compareTo(skuExp) < 0))
                // 4.ROI好花费低
                // 7天ROI＞标准，前3曝光＞国别标准(取参数表<曝光标准>)，前3天日平均花费＜国别标准*20%，曝光点击率＜国别标准(取参数表<曝光点击>);
                {
                    aosDetailentry.set("aos_type", "ROIHIGH");
                } else
                // 5.其他
                {
                    aosDetailentry.set("aos_type", "OTHER");
                }
            }
            // 查找15天前的 ppc平台数据
            List<QFilter> listQfs = new ArrayList<>(10);
            QFilter qfStatus = new QFilter("aos_entryentity.aos_keystatus", "=", "AVAILABLE");
            listQfs.add(qfStatus);
            Calendar cal = new GregorianCalendar();
            try {
                cal.setTime(df.parse(df.format(today.getTime())));
            } catch (ParseException e1) {
                e1.printStackTrace();
            }
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
            // 出价
            str.add("aos_bid as aos_ppcbid");
            // 组名
            str.add("aos_itemnumer as aos_itemnumer");
            // 关键词
            str.add("aos_keyword as aos_keyword");
            // 匹配类型
            str.add("aos_match_type as aos_match_type");
            DynamicObjectCollection dycPpc = QueryServiceHelper.query("aos_mkt_ppcst_data", str.toString(), qfs);
            Map<String,
                BigDecimal[]> mapPpc = dycPpc.stream()
                    .collect(Collectors.toMap(e -> e.getString("aos_date") + "?" + e.getString("aos_itemnumer") + "~"
                        + e.getString("aos_keyword") + "~" + e.getString("aos_match_type"), e -> {
                            BigDecimal[] big = new BigDecimal[2];
                            big[0] = BigDecimal.ZERO;
                            if (e.getBigDecimal(AOS_PPCBID) != null) {
                                big[0] = e.getBigDecimal("aos_ppcbid");
                            }
                            return big;
                        }));
            // 构造子单据体词数据15天
            for (DynamicObject aosDetailentry : aosDetailentryS) {
                String aosItemnumer = aosDetailentry.getString("aos_itemnumer");
                String aosKeyword = aosDetailentry.getString("aos_keyword");
                String aosMatchtype = aosDetailentry.getString("aos_matchtype");
                String key = aosItemnumer + "~" + aosKeyword + "~" + aosMatchtype;
                Map<String, Map<String, Object>> skuRptMap14 = keyRpt14.get(key);
                if (skuRptMap14 == null) {
                    continue;
                }
                DynamicObjectCollection aosWordentryS = aosDetailentry.getDynamicObjectCollection("aos_wordentry");
                for (String dateStr : skuRptMap14.keySet()) {
                    Map<String, Object> adPriceKey14D = adPriceKey14.get(dateStr);
                    BigDecimal aosWordadprice = BigDecimal.ZERO;
                    if (adPriceKey14D != null) {
                        aosWordadprice = (BigDecimal)adPriceKey14D.get("aos_bid_suggest");
                    }
                    Map<String, Object> skuRptMap14D = skuRptMap14.get(dateStr);
                    BigDecimal aosExpouse = (BigDecimal)skuRptMap14D.get("aos_impressions");
                    BigDecimal aosRoi = (BigDecimal)skuRptMap14D.get("aos_roi");
                    BigDecimal aosBid = (BigDecimal)skuRptMap14D.get("aos_bid");
                    BigDecimal aosCost = (BigDecimal)skuRptMap14D.get("aos_spend");
                    BigDecimal aosClick = (BigDecimal)skuRptMap14D.get("aos_clicks");
                    BigDecimal aosWordctr = BigDecimal.ZERO;
                    if (aosExpouse.compareTo(BigDecimal.ZERO) != 0 && aosClick != null) {
                        aosWordctr = aosClick.divide(aosExpouse, 6, RoundingMode.HALF_UP);
                    }
                    DynamicObject aosWordentry = aosWordentryS.addNew();
                    Date aosDateD = df.parse(dateStr);
                    aosWordentry.set("aos_worddate", aosDateD);
                    aosWordentry.set("aos_wordroi", aosRoi);
                    aosWordentry.set("aos_wordex", aosExpouse);
                    aosWordentry.set("aos_wordctr", aosWordctr);
                    aosWordentry.set("aos_wordbid", aosBid);
                    aosWordentry.set("aos_wordcost", aosCost);
                    aosWordentry.set("aos_wordadprice", aosWordadprice);
                    String key2 = df.format(aosDateD) + "?" + key;
                    if (mapPpc.containsKey(key2)) {
                        BigDecimal[] value = mapPpc.get(key2);
                        aosWordentry.set("aos_wordsetbid", value[0]);
                    }
                }
            }
            // 构造子单据体 组数据
            for (DynamicObject aosDetailentry : aosDetailentryS) {
                String aosItemnumer = aosDetailentry.getString("aos_itemnumer");
                String aosGroupitemname = aosDetailentry.getString("aos_itemname");
                // 7日关键词报告词数据
                Map<String, Object> skuRptMap = skuRptGroup.get(aosItemnumer);
                BigDecimal roi7Days = BigDecimal.ZERO;
                BigDecimal aosSpend = BigDecimal.ZERO;
                BigDecimal aosSales;
                BigDecimal aosImpressions = BigDecimal.ZERO;
                if (skuRptMap != null) {
                    // 投入
                    aosSpend = (BigDecimal)skuRptMap.get("aos_spend");
                    // 营收
                    aosSales = (BigDecimal)skuRptMap.get("aos_sales");
                    // 曝光
                    aosImpressions = (BigDecimal)skuRptMap.get("aos_impressions");
                    if (aosSales != null && ((BigDecimal)skuRptMap.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                        roi7Days = ((BigDecimal)skuRptMap.get("aos_sales"))
                            .divide((BigDecimal)skuRptMap.get("aos_spend"), 2, RoundingMode.HALF_UP);
                    }
                }
                DynamicObjectCollection aosGroupskuentryS =
                    aosDetailentry.getDynamicObjectCollection("aos_groupskuentry");
                DynamicObject aosGroupskuentry = aosGroupskuentryS.addNew();
                aosGroupskuentry.set("aos_groupsku", aosItemnumer);
                aosGroupskuentry.set("aos_groupitemname", aosGroupitemname);
                aosGroupskuentry.set("aos_groupkwnum", ppcTodayGroup.get(aosItemnumer));
                aosGroupskuentry.set("aos_grouproi", roi7Days);
                aosGroupskuentry.set("aos_groupex", aosImpressions);
                aosGroupskuentry.set("aos_groupcost", aosSpend);
                aosGroupskuentry.set("aos_buybox", null);
            }
            // 保存正式表
            OperationResult operationrst1 = OperationServiceHelper.executeOperate("save", "aos_mkt_pop_adjpstdt",
                new DynamicObject[] {aosMktPopAdjpstdt}, OperateOption.create());
            Object fid = operationrst1.getSuccessPkIds().get(0);
            String sql =
                " DELETE FROM tk_aos_mkt_pop_adjpstdt_r r WHERE 1=1 " + " and r.FId = ?  and r.fk_aos_type = '' ";
            Object[] params = {fid};
            DB.execute(DBRoute.of(DB_MKT), sql, params);
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

    private static Map<String, Object> generateAdjustpMap(String aosBillno) {
        Map<String, Object> adjustpMap = new HashMap<>(16);
        QFilter qfBillno = new QFilter("aos_billno", "=", aosBillno);
        QFilter[] filters = new QFilter[] {qfBillno};
        String selectField = "aos_detailentry.aos_itemid aos_itemid";
        DynamicObjectCollection aosMktPopadjustsDataS =
            QueryServiceHelper.query("aos_mkt_pop_adjstdt", selectField, filters);
        for (DynamicObject aosMktPopadjustsData : aosMktPopadjustsDataS) {
            String aosItemid = String.valueOf(aosMktPopadjustsData.get("aos_itemid"));
            adjustpMap.put(aosItemid, aosItemid);
        }
        return adjustpMap;
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
                CACHE.remove("saveOrgs");
            } else {
                CACHE.put("saveOrgs", String.valueOf(saveOrgs));
            }
        }
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {}
}