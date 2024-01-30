package mkt.popular.sale;

import common.CommonDataSom;
import common.Cux_Common_Utl;
import common.fnd.FndGlobal;
import common.fnd.FndLog;
import common.fnd.FndMsg;
import common.sal.util.InStockAvailableDays;
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
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import mkt.common.AosMktGenerate;
import mkt.common.MKTCom;
import mkt.common.AosMktCacheUtil;
import mkt.popular.promot.AosMktPopAdjpTask;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.time.DateUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author aosom
 * @version 出价调整销售-调度任务类
 */
public class AosMktPopAdjsTask extends AbstractTask {
    public final static String SYSTEM = Cux_Common_Utl.SYSTEM;
    public final static int TWOHUNDRED = 200;
    public final static String AOS_PPCBID = "aos_ppcbid";
    public final static String AOS_TOPPRICE = "aos_topprice";

    private static final DistributeSessionlessCache CACHE =
        CacheFactory.getCommonCacheFactory().getDistributeSessionlessCache("mkt_redis");
    public static void executerun(Map<String, Object> param) {
        Object pOuCode = param.get("p_ou_code");
        // 删除数据
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        Date todayTime = today.getTime();
        QFilter filterId = new QFilter("aos_orgid.number", "=", pOuCode);
        QFilter filterDate = new QFilter("aos_makedate", "=", todayTime);
        QFilter[] filtersAdj = new QFilter[] {filterId, filterDate};
        DeleteServiceHelper.delete("aos_mkt_popular_adjs", filtersAdj);
        DeleteServiceHelper.delete("aos_mkt_popadjusts_data", filtersAdj);
        // 初始化数据
        QFilter overseaFlag = new QFilter("aos_is_oversea_ou.number", "=", "Y");
        QFilter overseaFlag2 = new QFilter("aos_isomvalid", "=", true);
        // 海外公司
        QFilter qfOu = new QFilter("number", "=", pOuCode);
        QFilter[] filtersOu = new QFilter[] {overseaFlag, overseaFlag2, qfOu};
        DynamicObjectCollection bdCountry = QueryServiceHelper.query("bd_country", "id,number", filtersOu);
        for (DynamicObject ou : bdCountry) {
            // 海外公司
            QFilter groupOrg = new QFilter("aos_org.number", "=", ou.get("number"));
            QFilter groupEnable = new QFilter("enable", "=", 1);
            QFilter[] filtersGroup = new QFilter[] {groupOrg, groupEnable};
            DynamicObjectCollection aosSalgroup = QueryServiceHelper.query("aos_salgroup", "id", filtersGroup);
            for (DynamicObject group : aosSalgroup) {
                long groupId = group.getLong("id");
                Map<String, Object> params = new HashMap<>(16);
                params.put("p_ou_code", pOuCode);
                params.put("p_group_id", groupId);
                doOperate(params);
            }
            // 全部执行完后生成 出价调整推广
            Map<String, Object> adjs = new HashMap<>(16);
            adjs.put("p_ou_code", pOuCode);
            AosMktPopAdjpTask.executerun(adjs);
        }
    }

    private static BigDecimal getHighStandard(Object fixValue, BigDecimal high1, BigDecimal high2, BigDecimal high3) {
        if (fixValue == null || ((BigDecimal)fixValue).compareTo(BigDecimal.valueOf(TWOHUNDRED)) < 0) {
            return high1;
        } else if (((BigDecimal)fixValue).compareTo(BigDecimal.valueOf(TWOHUNDRED)) >= 0
            && ((BigDecimal)fixValue).compareTo(BigDecimal.valueOf(500)) < 0) {
            return high2;
        } else {
            return high3;
        }
    }

    public static void doOperate(Map<String, Object> params) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String pOuCode = (String)params.get("p_ou_code");
        long pGroupId = (long)params.get("p_group_id");
        Object pOrgId = FndGlobal.get_import_id(pOuCode, "bd_country");
        // 建议价格
        byte[] serializeAdprice = CACHE.getByteValue("mkt_adprice");
        HashMap<String, Map<String, Object>> adPrice = SerializationUtils.deserialize(serializeAdprice);
        // SKU报告1日
        byte[] serializeSkurptdetail = CACHE.getByteValue("mkt_skurptDetail");
        HashMap<String, Map<String, Object>> skuRptDetail = SerializationUtils.deserialize(serializeSkurptdetail);
        // SKU报告14日
        byte[] serializeSkurpt7 = CACHE.getByteValue("mkt_skurpt14Detail");
        HashMap<String, Map<String, Map<String, Object>>> skuRpt7Detail =
            SerializationUtils.deserialize(serializeSkurpt7);
        // PPC14日
        byte[] serializePpcyster7 = CACHE.getByteValue("mkt_ppcyster14");
        HashMap<String, Map<String, Map<String, Object>>> ppcYster7 =
            SerializationUtils.deserialize(serializePpcyster7);
        // 营销国别参数表
        byte[] serializePoporgInfo = CACHE.getByteValue("mkt_poporginfo");
        HashMap<String, Map<String, Object>> popOrgInfo = SerializationUtils.deserialize(serializePoporgInfo);
        // SKU报表三日平均
        byte[] serializeSkurpt3avg = CACHE.getByteValue("mkt_skurpt3avg");
        HashMap<String, Map<String, Object>> skuRpt3Avg = SerializationUtils.deserialize(serializeSkurpt3avg);
        // Amazon每日价格
        byte[] serializeDailyPrice = CACHE.getByteValue("mkt_dailyprice");
        HashMap<String, Object> dailyPrice = SerializationUtils.deserialize(serializeDailyPrice);
        // AD报告7日
        byte[] serializeAdviceprice7 = CACHE.getByteValue("mkt_adviceprice7");
        HashMap<String, Map<String, Map<String, Object>>> advicePrice7Day =
            SerializationUtils.deserialize(serializeAdviceprice7);
        // PPC昨日数据 系列
        byte[] serializePpcYesterSerial = CACHE.getByteValue("mkt_ppcyestSerial");
        HashMap<String, Map<String, Object>> ppcYesterSerial = SerializationUtils.deserialize(serializePpcYesterSerial);
        // SKU报告1日
        byte[] serializeSkurptdetailSerial = CACHE.getByteValue("mkt_skurptDetailSerial");
        HashMap<String, Map<String, Object>> skuRptDetailSerial =
            SerializationUtils.deserialize(serializeSkurptdetailSerial);
        // SKU报告7日
        byte[] serializeSkurpt = CACHE.getByteValue("mkt_skurpt");
        HashMap<String, Map<String, Object>> skuRpt = SerializationUtils.deserialize(serializeSkurpt);
        // PPC系列与组创建日期
        byte[] serializePpcInfo = CACHE.getByteValue("mkt_ppcinfo");
        HashMap<String, Map<String, Object>> ppcInfo = SerializationUtils.deserialize(serializePpcInfo);
        // 获取缓存
        byte[] serializeItem = CACHE.getByteValue("item");
        HashMap<String, Map<String, Object>> item = SerializationUtils.deserialize(serializeItem);
        // 海外在库数量
        Map<String, Object> itemOverseaqtyMap = item.get("item_overseaqty");
        // 半月销量
        HashMap<String, Integer> orderMonth = AosMktGenerate.GenerateOrderMonth(pOuCode);
        // SKU曝光标准
        BigDecimal skuExptandard = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "EXPOSURE").get("aos_value");
        // 国别标准ROI
        BigDecimal popOrgRoist = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "ROIST").get("aos_value");
        // 国别曝光标准
        BigDecimal exposure = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "EXPOSURE").get("aos_value");
        // 最高标准1(定价<200)
        BigDecimal high1 = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "HIGH1").get("aos_value");
        // 最高标准2(200<=定价<500)
        BigDecimal high2 = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "HIGH2").get("aos_value");
        // 最高标准3(定价>=500)
        BigDecimal high3 = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "HIGH3").get("aos_value");
        // SKU花费标准
        BigDecimal skuCostStandard = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "SKUCOST").get("aos_value");
        // 前7天日均销量标准
        BigDecimal avgSales7Std = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "7AVGSALES").get("aos_value");
        Date summerSpringEnd = MKTCom.Get_DateRange("aos_dateto", "SS", pOrgId);
        Date autumnWinterEnd = MKTCom.Get_DateRange("aos_dateto", "AW", pOrgId);
        // 判断是否季末
        Date summerSpringSeasonEnd = DateUtils.setDays(DateUtils.addDays(summerSpringEnd, -32), 1);
        Date autumnWinterSeasonEnd = DateUtils.setDays(DateUtils.addDays(autumnWinterEnd, -32), 1);
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        Date todayD = today.getTime();
        int year = today.get(Calendar.YEAR) - 2000;
        int month = today.get(Calendar.MONTH) + 1;
        int day = today.get(Calendar.DAY_OF_MONTH);
        // AM平台活动
        HashMap<String, Object> act = generateAct();
        QFilter qfDate = new QFilter("aos_date", "=", todayD);
        QFilter qfOrg = new QFilter("aos_orgid.number", "=", pOuCode);
        QFilter qfType = new QFilter("aos_entryentity.aos_groupstatus", "=", "AVAILABLE");
        QFilter qfGroup = new QFilter("aos_entryentity.aos_sal_group", "=", pGroupId);
        QFilter qfRoi = new QFilter("aos_entryentity.aos_roiflag", "=", false);
        QFilter[] filters = new QFilter[] {qfDate, qfOrg, qfType, qfGroup, qfRoi};
        String selectField = "aos_billno,aos_orgid," + "aos_orgid.number aos_orgnumber,"
            + "aos_poptype,aos_entryentity.aos_productno aos_productno,"
            + "aos_entryentity.aos_seasonseting aos_seasonseting,"
            + "aos_entryentity.aos_contryentrystatus aos_contryentrystatus,"
            + "aos_entryentity.aos_cn_name aos_cn_name," + "aos_entryentity.aos_itemnumer aos_itemnumer,"
            + "aos_entryentity.aos_avadays aos_avadays," + "aos_entryentity.aos_bid aos_bid,"
            + "aos_entryentity.aos_lastpricedate aos_lastpricedate," + "aos_entryentity.aos_lastbid aos_lastbid,"
            + "aos_entryentity.aos_groupdate aos_groupdate," + "aos_entryentity.aos_roi7days aos_roi7days,"
            + "aos_entryentity.aos_itemid aos_itemid," + "aos_entryentity.aos_sal_group aos_sal_group,"
            + "aos_entryentity.id aos_ppcentryid," + "id," + "aos_entryentity.aos_highvalue aos_highvalue,"
            + "aos_entryentity.aos_basestitem aos_basestitem";
        DynamicObjectCollection aosMktPopularPpcS =
            QueryServiceHelper.query("aos_mkt_popular_ppc", selectField, filters, "aos_entryentity.aos_productno");
        int rows = aosMktPopularPpcS.size();
        if (rows == 0) {
            return;
        }
        DynamicObject head = aosMktPopularPpcS.get(0);
        DynamicObject aosMktPopularAdjs = BusinessDataServiceHelper.newDynamicObject("aos_mkt_popular_adjs");
        aosMktPopularAdjs.set("aos_billno", head.get("aos_billno"));
        aosMktPopularAdjs.set("aos_ppcid", head.get("id"));
        aosMktPopularAdjs.set("aos_orgid", pOrgId);
        aosMktPopularAdjs.set("billstatus", "A");
        aosMktPopularAdjs.set("aos_status", "A");
        aosMktPopularAdjs.set("aos_makeby", SYSTEM);
        aosMktPopularAdjs.set("aos_makedate", todayD);
        aosMktPopularAdjs.set("aos_groupid", pGroupId);
        OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_popular_adjs",
            new DynamicObject[] {aosMktPopularAdjs}, OperateOption.create());
        Object aosSourceid = operationrst.getSuccessPkIds().get(0);
        DynamicObject aosMktPopadjustsData = BusinessDataServiceHelper.newDynamicObject("aos_mkt_popadjusts_data");
        aosMktPopadjustsData.set("aos_billno", head.get("aos_billno"));
        aosMktPopadjustsData.set("aos_orgid", pOrgId);
        aosMktPopadjustsData.set("billstatus", "A");
        aosMktPopadjustsData.set("aos_groupid", pGroupId);
        aosMktPopadjustsData.set("aos_sourceid", aosSourceid);
        aosMktPopadjustsData.set("aos_makedate", todayD);
        DynamicObjectCollection aosDetailentryS = aosMktPopadjustsData.getDynamicObjectCollection("aos_detailentry");
        FndLog log = FndLog.init("MKT_出价调整(销售)", pOuCode + year + month + day);
        String isContinue = FndMsg.getStatic("IS_CONTINUE");
        for (DynamicObject aosMktPopularPpc : aosMktPopularPpcS) {
            // 目前全部跳过
            if ("Y".equals(isContinue)) {
                continue;
            }
            // 数据层
            long itemId = aosMktPopularPpc.getLong("aos_itemid");
            String aosItemnumer = aosMktPopularPpc.getString("aos_itemnumer");
            String aosOrgnumber = aosMktPopularPpc.getString("aos_orgnumber");
            int aosAvadays = aosMktPopularPpc.getInt("aos_avadays");
            BigDecimal aosBid = aosMktPopularPpc.getBigDecimal("aos_bid");
            // 滞销类型
            String aosBasestitem = aosMktPopularPpc.getString("aos_basestitem");
            String aosSeasonseting = aosMktPopularPpc.getString("aos_seasonseting");
            String aosSeasonpro = "";
            if ("春季产品".equals(aosSeasonseting)) {
                aosSeasonpro = "SPRING";
            }
            switch (aosSeasonseting) {
                case "春季产品":
                    aosSeasonpro = "SPRING";
                    break;
                case "春夏产品":
                    aosSeasonpro = "SPRING_SUMMER";
                    break;
                case "夏季产品":
                    aosSeasonpro = "SUMMER";
                    break;
                case "春夏常规品":
                    aosSeasonpro = "SPRING-SUMMER-CONVENTIONAL";
                    break;
                case "秋冬产品":
                    aosSeasonpro = "AUTUMN_WINTER";
                    break;
                case "冬季产品":
                    aosSeasonpro = "WINTER";
                    break;
                default:
                    break;
            }
            Map<String, Object> skuRpt3AvgMap = skuRpt3Avg.get(pOrgId + "~" + itemId);
            BigDecimal spend3Avg = BigDecimal.ZERO;
            BigDecimal impress3Avg = BigDecimal.ZERO;
            if (skuRpt3AvgMap != null) {
                spend3Avg = (BigDecimal)skuRpt3AvgMap.get("aos_spend");
                impress3Avg = (BigDecimal)skuRpt3AvgMap.get("aos_impressions");
            }
            log.add(aosItemnumer + "三天平均曝光=" + impress3Avg + " 国别标准=" + exposure);
            // Sku报告
            Map<String, Object> skuRptDetailMap = skuRptDetail.get(pOrgId + "~" + itemId);
            if (skuRptDetailMap == null) {
                continue;
            }
            String category = CommonDataSom.getItemCategoryName(String.valueOf(itemId));
            if (category == null || "".equals(category)) {
                continue;
            }
            // Sku报告
            Map<String, Object> skuRptMap = skuRpt.get(pOrgId + "~" + itemId);
            // 7天ROI
            BigDecimal roi7Days = BigDecimal.ZERO;
            if (skuRptMap != null && ((BigDecimal)skuRptMap.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                roi7Days = ((BigDecimal)skuRptMap.get("aos_total_sales")).divide((BigDecimal)skuRptMap.get("aos_spend"),
                    2, RoundingMode.HALF_UP);
            }
            Map<String, Object> ppcInfoMap = ppcInfo.get(pOrgId + "~" + itemId);
            // 首次建组日期
            Object aosGroupdate = null;
            if (ppcInfoMap != null) {
                aosGroupdate = ppcInfoMap.get("aos_groupdate");
            }
            if (aosGroupdate == null) {
                aosGroupdate = todayD;
            }
            // PPC数据源 出价调整(销售)筛选逻辑
            boolean isUnsalable = true;
            // 对于滞销品
            if ("NORMAL".equals(aosBasestitem) || "HARD".equals(aosBasestitem)) {
                if ((aosAvadays < 90) && (spend3Avg.compareTo(skuCostStandard) > 0)) {
                    // 1.可售低花费高 可售天数＜90天(在途+在库数量计算可售天数)，前3天日平均花费≥国别标准SKU花费标准;
                    continue;// 直接剔除留给营销调整出价
                } else if ((getBetweenDays(todayD, (Date)aosGroupdate) > 7)
                    && (spend3Avg.compareTo(skuCostStandard) > 0) && (roi7Days.compareTo(popOrgRoist) < 0)
                    && (impress3Avg.compareTo(skuExptandard) > 0)) {
                    // 2.ROI差花费高 首次建组>7天，前3天日平均花费≥SKU花费标准(取参数表<SKU花费标准>)，且
                    // 7天ROI＜国别ROI标准，且前3平均曝光>国别标准(取参数表<曝光标准>);
                    // 直接剔除留给营销调整出价
                    continue;
                } else if ("AUTUMN_WINTER".equals(aosSeasonpro)) {
                    // 秋冬产品 直接剔除留给营销调整出价
                    continue;
                } else if (!Cux_Common_Utl.IsNull(act.get(pOrgId + "~" + itemId))) {
                    // 未来七天有活动
                    continue;
                } else {
                    // 其余滞销类型不做任何判断必进出价调整(销售)
                    isUnsalable = false;
                }
            }
            if (isUnsalable) {
                // 1.3天平均曝光<国别标准
                if (impress3Avg.compareTo(exposure) >= 0) {
                    continue;
                }
                // 2.计算出价<出价最高标准
                // 定价 Amazon每日价格
                Object fixValue = dailyPrice.get(pOrgId + "~" + itemId);
                // 最高标准 根据国别定价筛选
                BigDecimal highStandard = getHighStandard(fixValue, high1, high2, high3);
                log.add(aosItemnumer + "计算出价=" + aosBid + " 最高标准=" + highStandard);
                if (aosBid.compareTo(highStandard) >= 0) {
                    continue;
                }
                // 3.只筛选0销量 低销量
                float r = InStockAvailableDays.getOrgItemOnlineAvgQty(pOrgId.toString(), String.valueOf(itemId));
                int halfMonthTotalSales = (int)Cux_Common_Utl.nvl(orderMonth.get(String.valueOf(itemId)));
                Boolean regularUn =
                    MKTCom.Get_RegularUn((long)pOrgId, itemId, aosOrgnumber, aosAvadays, r, halfMonthTotalSales);
                log.add(aosItemnumer + "非0销量低销量 标记=" + regularUn);
                if (regularUn) {
                    continue;
                }
                // 前7天日均销量 < 国别标准
                if (BigDecimal.valueOf(r).compareTo(avgSales7Std) >= 0) {
                    log.add(aosItemnumer + "前7天日均销量=" + r + " 国别标准=" + avgSales7Std);
                    continue;
                }
            }
            // 判断是或否最近推广调价日期<= 3天
            Date aosLastpricedate = aosMktPopularPpc.getDate("aos_lastpricedate");
            // 判断季节品是否季末有剩余
            Object itemOverseaqty = itemOverseaqtyMap.get(pOrgId + "~" + itemId);
            if (FndGlobal.IsNull(itemOverseaqty)) {
                itemOverseaqty = 0;
            }
            if ("SPRING".equals(aosSeasonpro) || "SPRING_SUMMER".equals(aosSeasonpro) || "SUMMER".equals(aosSeasonpro)
                || "SPRING-SUMMER-CONVENTIONAL".equals(aosSeasonpro)) {
                if (todayD.after(summerSpringSeasonEnd)) {
                    // 季末 判断是否达标
                    float seasonRate = MKTCom.Get_SeasonRate((long)pOrgId, itemId, aosSeasonpro, itemOverseaqty, month);
                    if (!MKTCom.Is_SeasonRate(aosSeasonpro, month, seasonRate)) {
                        log.add(aosItemnumer + "// 季末 判断是否达标");
                        continue;
                    }
                }
            }
            if ("AUTUMN_WINTER".equals(aosSeasonpro) || "WINTER".equals(aosSeasonpro)) {
                // 判断是否季末
                if (todayD.after(autumnWinterSeasonEnd)) {
                    // 季末 判断是否达标
                    float seasonRate = MKTCom.Get_SeasonRate((long)pOrgId, itemId, aosSeasonpro, itemOverseaqty, month);
                    if (!MKTCom.Is_SeasonRate(aosSeasonpro, month, seasonRate)) {
                        continue;
                    }
                }
            }
            String[] categoryGroup = category.split(",");
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
            BigDecimal aosBidSuggest = BigDecimal.ZERO;
            BigDecimal aosBidRangestart = BigDecimal.ZERO;
            BigDecimal aosBidRangeend = BigDecimal.ZERO;
            Map<String, Object> adPriceMap = adPrice.get(pOrgId + "~" + aosItemnumer);
            if (adPriceMap != null) {
                aosBidSuggest = (BigDecimal)adPriceMap.get("aos_bid_suggest");
                aosBidRangestart = (BigDecimal)adPriceMap.get("aos_bid_rangestart");
                aosBidRangeend = (BigDecimal)adPriceMap.get("aos_bid_rangeend");
            }
            // 开始行赋值
            DynamicObject aosDetailentry = aosDetailentryS.addNew();
            // 滞销类型
            aosDetailentry.set("aos_basestitem", aosBasestitem);
            aosDetailentry.set("aos_productno", aosMktPopularPpc.get("aos_productno"));
            aosDetailentry.set("aos_itemnumer", aosItemnumer);
            aosDetailentry.set("aos_categroy1", aosCategory1);
            aosDetailentry.set("aos_categroy2", aosCategory2);
            aosDetailentry.set("aos_categroy3", aosCategory3);
            aosDetailentry.set("aos_avadays", aosMktPopularPpc.get("aos_avadays"));
            aosDetailentry.set("aos_contryentrystatus", aosMktPopularPpc.get("aos_contryentrystatus"));
            aosDetailentry.set("aos_seasonseting", aosMktPopularPpc.get("aos_seasonseting"));
            aosDetailentry.set("aos_cn_name", aosMktPopularPpc.get("aos_cn_name"));
            aosDetailentry.set("aos_highvalue", aosMktPopularPpc.get("aos_highvalue"));
            Object aosActivity = act.get(pOrgId + "~" + itemId);
            if (aosActivity != null) {
                aosDetailentry.set("aos_activity", aosActivity);
            }
            aosDetailentry.set("aos_calprice", aosMktPopularPpc.get("aos_bid"));
            aosDetailentry.set("aos_adjprice", aosMktPopularPpc.get("aos_bid"));
            aosDetailentry.set("aos_lastdate", aosLastpricedate);
            aosDetailentry.set("aos_bidsuggest", aosBidSuggest);
            aosDetailentry.set("aos_lastprice", aosMktPopularPpc.get("aos_lastbid"));
            // 设置比例
            BigDecimal bigTemp = BigDecimal.ZERO;
            // 计算出价
            BigDecimal bigCalprice = aosMktPopularPpc.getBigDecimal("aos_bid");
            if (aosBidSuggest.compareTo(BigDecimal.ZERO) != 0) {
                bigTemp = bigCalprice.divide(aosBidSuggest, 5, RoundingMode.HALF_UP);
            }
            aosDetailentry.set("aos_ratio", bigTemp);
            aosDetailentry.set("aos_bidrangestart", aosBidRangestart);
            aosDetailentry.set("aos_bidrangeend", aosBidRangeend);
            aosDetailentry.set("aos_roi", aosMktPopularPpc.get("aos_roi7days"));
            aosDetailentry.set("aos_itemid", aosMktPopularPpc.get("aos_itemid"));
            aosDetailentry.set("aos_ppcentryid", aosMktPopularPpc.get("aos_ppcentryid"));
            aosDetailentry.set("aos_expouse", impress3Avg);
            BigDecimal aosSpend = BigDecimal.ZERO;
            // Sku报告1日数据
            Object lastSpendObj = skuRptDetailSerial.get(pOrgId + "~" + aosMktPopularPpc.get("aos_productno"));
            if (!Cux_Common_Utl.IsNull(lastSpendObj)) {
                aosSpend = (BigDecimal)lastSpendObj;
            }
            // 系列花出率 = 前一天花出/预算
            BigDecimal aosSerialrate = BigDecimal.ZERO;
            // 默认昨日数据 昨日没有则为空
            Map<String, Object> ppcYesterMap =
                ppcYesterSerial.get(pOrgId + "~" + aosMktPopularPpc.get("aos_productno"));
            if (ppcYesterMap != null) {
                // 昨日出价
                BigDecimal aosBudget = (BigDecimal)ppcYesterMap.get("aos_budget");
                if (aosBudget.compareTo(BigDecimal.ZERO) != 0) {
                    aosSerialrate = aosSpend.divide(aosBudget, 2, RoundingMode.HALF_UP);
                }
            }
            aosDetailentry.set("aos_costrate", aosSerialrate);
            BigDecimal aosGroupcost = (BigDecimal)skuRptDetailMap.get("aos_spend");
            aosDetailentry.set("aos_spend", aosGroupcost);
        }
        // 查找15天前的 ppc平台数据
        List<QFilter> listQfs = new ArrayList<>(10);
        QFilter qfStatus = new QFilter("aos_entryentity.aos_groupstatus", "=", "AVAILABLE");
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
        listQfs.add(new QFilter("aos_orgid", "=", pOrgId));
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
                Map<String, Map<String, Object>> skuRptMap7 = skuRpt7Detail.get(pOrgId + "~" + itemId);
                if (skuRptMap7 == null) {
                    continue;
                }
                // PPC7日
                Map<String, Map<String, Object>> ppcYster7Map = ppcYster7.get(pOrgId + "~" + itemId);
                if (ppcYster7Map == null) {
                    continue;
                }
                DynamicObjectCollection aosGroupentryS = aosDetailentry.getDynamicObjectCollection("aos_groupentry");
                // 循环ROI
                for (String dateStr : skuRptMap7.keySet()) {
                    Map<String, Object> skuRptMap7D = skuRptMap7.get(dateStr);
                    BigDecimal aosExpouse = (BigDecimal)skuRptMap7D.get("aos_impressions");
                    BigDecimal aosRoi = (BigDecimal)skuRptMap7D.get("aos_roi");
                    BigDecimal aosBid = (BigDecimal)skuRptMap7D.get("aos_bid");
                    BigDecimal aosCost = (BigDecimal)skuRptMap7D.get("aos_spend");
                    DynamicObject aosGroupentry = aosGroupentryS.addNew();
                    Date aosDateD = df.parse(dateStr);
                    aosGroupentry.set("aos_date_g", aosDateD);
                    aosGroupentry.set("aos_exp_g", aosExpouse);
                    aosGroupentry.set("aos_roi_g", aosRoi);
                    aosGroupentry.set("aos_bid_g", aosBid);
                    aosGroupentry.set("aos_spend_g", aosCost);
                    BigDecimal aosBudgetG = BigDecimal.ZERO;
                    Map<String, Object> ppcYster7MapD = ppcYster7Map.get(dateStr);
                    if (ppcYster7MapD != null) {
                        aosBudgetG = (BigDecimal)ppcYster7MapD.get("aos_budget");
                    }
                    // 预算
                    aosGroupentry.set("aos_budget_g", aosBudgetG);
                    String key = df.format(aosDateD) + "?" + itemId;
                    if (mapPpc.containsKey(key)) {
                        BigDecimal[] value = mapPpc.get(key);
                        aosGroupentry.set("aos_ppcbid", value[0]);
                        aosGroupentry.set("aos_topprice", value[1]);
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        for (DynamicObject aosDetailentry : aosDetailentryS) {
            try {
                String aosItemnumer = aosDetailentry.getString("aos_itemnumer");
                Map<String, Map<String, Object>> advicePrice7DayMap = advicePrice7Day.get(pOrgId + "~" + aosItemnumer);
                if (advicePrice7DayMap == null) {
                    continue;
                }
                DynamicObjectCollection aosMarketentryS = aosDetailentry.getDynamicObjectCollection("aos_marketentry");
                // 循环ROI
                for (String dateStr : advicePrice7DayMap.keySet()) {
                    Date aosDateD = df.parse(dateStr);
                    Map<String, Object> advicePrice7DayMapD = advicePrice7DayMap.get(dateStr);
                    BigDecimal aosMarketprice = BigDecimal.ZERO;
                    if (advicePrice7DayMapD != null) {
                        aosMarketprice = (BigDecimal)advicePrice7DayMapD.get("aos_bid_suggest");
                    }
                    DynamicObject aosMarketentry = aosMarketentryS.addNew();
                    aosMarketentry.set("aos_date_p", aosDateD);
                    aosMarketentry.set("aos_marketprice", aosMarketprice);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        OperationServiceHelper.executeOperate("save", "aos_mkt_popadjusts_data",
            new DynamicObject[] {aosMktPopadjustsData}, OperateOption.create());
        log.finnalSave();
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
        // "Y");
        QFilter[] filters = new QFilter[] {filterDate, filterAma};
        String select =
            "aos_nationality.id aos_orgid,aos_shop,aos_acttype,aos_sal_actplanentity.aos_itemnum.id aos_itemid,"
                + "aos_acttype.number aos_acttypeNumber,aos_sal_actplanentity.aos_l_startdate aos_l_startdate,aos_sal_actplanentity.aos_enddate aos_enddate";
        DataSet aosActSelectPlanS = QueryServiceHelper.queryDataSet("aos_mkt_popadjs_init.GenerateAct",
            "aos_act_select_plan", select, filters, "aos_makedate");
        // 查询店铺活动类型参数
        String selectFields = "aos_org,aos_shop,aos_acttype";
        DataSet aosSalActTypeS =
            QueryServiceHelper.queryDataSet("aos_mkt_popadjs_init.GenerateAct", "aos_sal_act_type_p", selectFields,
                new QFilter[] {new QFilter("aos_assessment.number", QCP.equals, "Y")}, null);
        aosActSelectPlanS = aosActSelectPlanS.join(aosSalActTypeS, JoinType.INNER).on("aos_orgid", "aos_org")
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
        aosSalActTypeS.close();
        aosActSelectPlanS.close();
        return act;
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        // 初始化数据
        CommonDataSom.init();
        AosMktCacheUtil.initRedis("ppc");
        QFilter overseaFlag = new QFilter("aos_is_oversea_ou.number", "=", "Y");
        QFilter overseaFlag2 = new QFilter("aos_isomvalid", "=", true);
        QFilter[] filtersOu = new QFilter[] {overseaFlag, overseaFlag2};
        DynamicObjectCollection bdCountry = QueryServiceHelper.query("bd_country", "id,number", filtersOu);
        for (DynamicObject ou : bdCountry) {
            param.put("p_ou_code", ou.get("number"));
            executerun(param);
        }
    }
}