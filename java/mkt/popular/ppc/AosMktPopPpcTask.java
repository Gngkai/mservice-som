package mkt.popular.ppc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import mkt.popular.sale.AosMktPopAdjsTask;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;

import common.CommonDataSom;
import common.Cux_Common_Utl;
import common.StringComUtils;
import common.fnd.*;
import common.sal.util.InStockAvailableDays;
import common.sal.util.QFBuilder;
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
import kd.bos.db.DB;
import kd.bos.db.DBRoute;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.exception.KDException;
import kd.bos.orm.ORM;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import mkt.common.MktComUtil;
import mkt.common.AosMktCacheUtil;
import mkt.popular.AosMktPopUtil;
import mkt.popular.sale.AosMktPopAddTask;
import mkt.progress.iface.ItemInfoUtil;

/**
 * @author aosom
 * @version PPC初始化任务-调度任务类
 */
public class AosMktPopPpcTask extends AbstractTask {
    public static final String URL =
        "https://open.feishu.cn/open-apis/bot/v2/hook/242e7160-8309-4312-8586-5fe58482d8c5";
    private static final DistributeSessionlessCache CACHE =
        CacheFactory.getCommonCacheFactory().getDistributeSessionlessCache("mkt_redis");
    private static final String DB_MKT = "aos.mkt";
    private static final AosomLog LOGGER = AosomLog.init("aos_mkt_popppc_init");

    static {
        LOGGER.setService("AOS.MMS");
        LOGGER.setDomain("MMS.POPULAR");
    }

    public static void manualitemClick(String aosOuCode) {
        // 删除数据
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        DeleteServiceHelper.delete("aos_mkt_popular_ppc",
            new QFilter("aos_orgid.number", "=", aosOuCode).and("aos_date", "=", today.getTime()).toArray());
        // 初始化数据
        CommonDataSom.init();
        AosMktCacheUtil.initRedis("ppc");
        Map<String, Object> params = new HashMap<>(16);
        params.put("p_ou_code", aosOuCode);
        doOperate(params);
    }

    private static void executerun() {
        // 初始化数据
        CommonDataSom.init();
        AosMktCacheUtil.initRedis("ppc");

        Calendar todayCalendar = Calendar.getInstance();
        int hour = todayCalendar.get(Calendar.HOUR_OF_DAY);
        int week = todayCalendar.get(Calendar.DAY_OF_WEEK);
        QFilter qfTime;
        DynamicObject dynamicObject = QueryServiceHelper.queryOne("aos_mkt_base_orgvalue", "aos_value",
            new QFilter[] {new QFilter("aos_type", QCP.equals, "TIME")});
        int time = 16;
        if (dynamicObject != null) {
            time = dynamicObject.getBigDecimal("aos_value").intValue();
        }
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
            LOGGER.info("MKT_PPC操作平台数据源  " + pOuCode);
            Map<String, Object> params = new HashMap<>(16);
            params.put("p_ou_code", pOuCode);
            doOperate(params);
        }
        // 生成销售加回数据
        if (AosMktPopUtil.getCopyFlag(sign.ppcAdd.name, week)) {
            AosMktPopAddTask.run();
        }
    }

    public static void doOperate(Map<String, Object> params) {
        Object pOuCode = params.get("p_ou_code");
        try {
            // 获取缓存
            byte[] serializeItem = CACHE.getByteValue("item");
            HashMap<String, Map<String, Object>> item = SerializationUtils.deserialize(serializeItem);
            // 海外在库数量
            Map<String, Object> itemOverseaqtyMap = item.get("item_overseaqty");
            // 在途数量
            Map<String, Object> itemIntransqtyMap = item.get("item_intransqty");
            // 最大库龄
            Map<String, Object> itemMaxageMap = item.get("item_maxage");
            // 日期参数
            byte[] serializeDatepara = CACHE.getByteValue("datepara");
            HashMap<String, Map<String, Object>> datepara = SerializationUtils.deserialize(serializeDatepara);
            // 备货天数
            Map<String, Object> aosShpdayMap = datepara.get("aos_shp_day");
            // 清关天数
            Map<String, Object> aosCleardayMap = datepara.get("aos_clear_day");
            // 海运天数
            Map<String, Object> aosFreightdayMap = datepara.get("aos_freight_day");
            // PPC系列与组创建日期
            byte[] serializePpcInfo = CACHE.getByteValue("mkt_ppcinfo");
            HashMap<String, Map<String, Object>> ppcInfo = SerializationUtils.deserialize(serializePpcInfo);
            // PPC系列与组创建日期
            byte[] serializePpcInfoSerial = CACHE.getByteValue("mkt_ppcinfoSerial");
            HashMap<String, Map<String, Object>> ppcInfoSerial = SerializationUtils.deserialize(serializePpcInfoSerial);
            // 营销国别参数表
            byte[] serializePoporgInfo = CACHE.getByteValue("mkt_poporginfo");
            HashMap<String, Map<String, Object>> popOrgInfo = SerializationUtils.deserialize(serializePoporgInfo);
            // 预测汇总表
            byte[] serializeSalSummary = CACHE.getByteValue("mkt_salsummary");
            HashMap<String, Map<String, Object>> salSummary = SerializationUtils.deserialize(serializeSalSummary);
            // PPC昨日数据
            byte[] serializePpcYester = CACHE.getByteValue("mkt_ppcyest");
            HashMap<String, Map<String, Object>> ppcYester = SerializationUtils.deserialize(serializePpcYester);
            // PPC昨日数据系列
            byte[] serializePpcyestSerial = CACHE.getByteValue("mkt_ppcyestSerial");
            HashMap<String, Map<String, Object>> ppcYesterSerial =
                SerializationUtils.deserialize(serializePpcyestSerial);
            // Amazon每日价格
            byte[] serializeDailyPrice = CACHE.getByteValue("mkt_dailyprice");
            HashMap<String, Object> dailyPrice = SerializationUtils.deserialize(serializeDailyPrice);
            // SKU报告3日
            byte[] serializeSkurpt3 = CACHE.getByteValue("mkt_skurpt3");
            HashMap<String, Map<String, Object>> skuRpt3 = SerializationUtils.deserialize(serializeSkurpt3);
            // SKU报告7日
            byte[] serializeSkurpt = CACHE.getByteValue("mkt_skurpt");
            HashMap<String, Map<String, Object>> skuRpt = SerializationUtils.deserialize(serializeSkurpt);
            // SKU报告14日
            byte[] serializeSkurpt14 = CACHE.getByteValue("mkt_skurpt14");
            HashMap<String, Map<String, Object>> skuRpt14 = SerializationUtils.deserialize(serializeSkurpt14);
            // 建议价格
            byte[] serializeAdprice = CACHE.getByteValue("mkt_adprice");
            HashMap<String, Map<String, Object>> adPrice = SerializationUtils.deserialize(serializeAdprice);
            // SKU报告系列14日
            byte[] serializeSkurptSerial14 = CACHE.getByteValue("mkt_skurptSerial14");
            HashMap<String, Map<String, Object>> skuRptSerial14 =
                SerializationUtils.deserialize(serializeSkurptSerial14);
            // SKU报告系列7日
            byte[] serializeSkurptSerial7 = CACHE.getByteValue("mkt_skurptSerial7");
            HashMap<String, Map<String, Object>> skuRptSerial7 = SerializationUtils.deserialize(serializeSkurptSerial7);
            // SKU报告系列近3日
            byte[] serializeSkurpt3Serial = CACHE.getByteValue("mkt_skurpt3Serial");
            HashMap<String, Map<String, Map<String, Object>>> skuRpt3Serial =
                SerializationUtils.deserialize(serializeSkurpt3Serial);
            // SKU报告系列1日
            byte[] serializeSkurptdetailSerial = CACHE.getByteValue("mkt_skurptDetailSerial");
            HashMap<String, BigDecimal> skuRptDetailSerial =
                SerializationUtils.deserialize(serializeSkurptdetailSerial);
            // 预测汇总表
            byte[] serializeSalSummarySerial = CACHE.getByteValue("mkt_salsummarySerial");
            HashMap<String, Map<String, Object>> salSummaryMap =
                SerializationUtils.deserialize(serializeSalSummarySerial);
            // SKU报表三日平均
            byte[] serializeSkurpt3avg = CACHE.getByteValue("mkt_skurpt3avg");
            HashMap<String, Map<String, Object>> skuRpt3Avg = SerializationUtils.deserialize(serializeSkurpt3avg);
            // 国别货号滞销类型
            byte[] serializeBaseStItem = CACHE.getByteValue("mkt_basestitem");
            HashMap<String, Object> baseStItem = SerializationUtils.deserialize(serializeBaseStItem);
            // SKU报告1日
            byte[] serializeSkuRptDetail = CACHE.getByteValue("mkt_skurptDetail");
            HashMap<String, Map<String, Object>> skuRptDetail = SerializationUtils.deserialize(serializeSkuRptDetail);
            // 物料可售库存
            byte[] mktItemavadays = CACHE.getByteValue("mkt_itemavadays");
            HashMap<String, Integer> mapItemavadays = SerializationUtils.deserialize(mktItemavadays);
            // 获取传入参数 国别
            Object orgId = FndGlobal.get_import_id(pOuCode, "bd_country");
            // 国别安全库存
            int safeQty = ItemInfoUtil.getSafeQty(orgId);
            // 获取当前日期
            Calendar date = Calendar.getInstance();
            date.set(Calendar.HOUR_OF_DAY, 0);
            date.set(Calendar.MINUTE, 0);
            date.set(Calendar.SECOND, 0);
            date.set(Calendar.MILLISECOND, 0);
            Date todayD = date.getTime();
            // 获取单号
            int year = date.get(Calendar.YEAR) - 2000;
            int month = date.get(Calendar.MONTH) + 1;
            int day = date.get(Calendar.DAY_OF_MONTH);
            int week = date.get(Calendar.DAY_OF_WEEK);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            date.add(Calendar.DAY_OF_MONTH, -1);
            String yester = dateFormat.format(date.getTime());
            date.add(Calendar.DAY_OF_MONTH, -1);
            String yester2 = dateFormat.format(date.getTime());
            date.add(Calendar.DAY_OF_MONTH, -1);
            String yester3 = dateFormat.format(date.getTime());
            String aosBillno = "SP" + pOuCode + year + month + day;
            // 如果是 2467 则赋值昨日数据 直接退出
            boolean copyFlag = AosMktPopUtil.getCopyFlag("PPC_SP", week);
            if (!copyFlag) {
                DynamicObject aosMktPopularPpc = BusinessDataServiceHelper.newDynamicObject("aos_mkt_popular_ppc");
                Calendar calendar = Calendar.getInstance();
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                Date dateTo = calendar.getTime();
                calendar.add(Calendar.DAY_OF_MONTH, -1);
                Date dateFrom = calendar.getTime();
                // 日期格式化
                SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                String dateFromStr = writeFormat.format(dateFrom);
                String dateToStr = writeFormat.format(dateTo);
                QFilter filterDateFrom = new QFilter("aos_date", ">=", dateFromStr);
                QFilter filterDateTo = new QFilter("aos_date", "<", dateToStr);
                QFilter filterOrg = new QFilter("aos_orgid", "=", orgId);
                QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterOrg};
                long fid = QueryServiceHelper.queryOne("aos_mkt_popular_ppc", "id", filters).getLong("id");
                DynamicObject aosMktPopPpclast = BusinessDataServiceHelper.loadSingle(fid, "aos_mkt_popular_ppc");
                aosMktPopularPpc.set("aos_orgid", orgId);
                aosMktPopularPpc.set("billstatus", "A");
                aosMktPopularPpc.set("aos_status", "A");
                aosMktPopularPpc.set("aos_adjusts", true);
                aosMktPopularPpc.set("aos_adjustsp", true);
                aosMktPopularPpc.set("aos_budgetp", true);
                aosMktPopularPpc.set("aos_billno", aosBillno);
                Object aosPoptype = FndGlobal.get_import_id("SP", "aos_mkt_base_poptype");
                aosMktPopularPpc.set("aos_poptype", aosPoptype);
                aosMktPopularPpc.set("aos_date", todayD);
                Object platformId = FndGlobal.get_import_id("AMAZON", "aos_sal_channel");
                aosMktPopularPpc.set("aos_channel", platformId);
                DynamicObjectCollection aosEntryentityS =
                    aosMktPopularPpc.getDynamicObjectCollection("aos_entryentity");
                DynamicObjectCollection aosEntryentityLastS =
                    aosMktPopPpclast.getDynamicObjectCollection("aos_entryentity");
                aosEntryentityS.clear();
                for (DynamicObject aosEntryentityLast : aosEntryentityLastS) {
                    DynamicObject dyn = aosEntryentityS.addNew();
                    StringComUtils.setValue(aosEntryentityLast, dyn);
                }
                SaveServiceHelper.saveOperate("aos_mkt_popular_ppc", new DynamicObject[] {aosMktPopularPpc},
                    OperateOption.create());
                return;
            }
            // 获取春夏品 秋冬品开始结束日期 营销日期参数表
            Date summerSpringStart = MktComUtil.getDateRange("aos_datefrom", "SS", orgId);
            Date summerSpringFirst = DateUtils.setDays(DateUtils.addDays(summerSpringStart, 63), 1);
            Date summerSpringEnd = MktComUtil.getDateRange("aos_dateto", "SS", orgId);
            Date autumnWinterStart = MktComUtil.getDateRange("aos_datefrom", "AW", orgId);
            Date autumnWinterFirst = DateUtils.setDays(DateUtils.addDays(autumnWinterStart, 63), 1);
            Date autumnWinterEnd = MktComUtil.getDateRange("aos_dateto", "AW", orgId);
            // 春夏品不剔除日期
            Date summerSpringUnStart = MktComUtil.getDateRange("aos_datefrom", "SSUN", orgId);
            // 秋冬品不剔除日期
            Date autumnWinterUnStart = MktComUtil.getDateRange("aos_datefrom", "AWUN", orgId);
            // 春夏品不剔除日期
            Date summerSpringUnEnd = MktComUtil.getDateRange("aos_dateto", "SSUN", orgId);
            // 判断是否季末
            Date summerSpringSeasonEnd = DateUtils.setDays(DateUtils.addDays(summerSpringEnd, -32), 1);
            // 秋冬品不剔除日期
            Date autumnWinterUnEnd = MktComUtil.getDateRange("aos_dateto", "AWUN", orgId);
            Date autumnWinterSeasonEnd = DateUtils.setDays(DateUtils.addDays(autumnWinterEnd, -32), 1);
            // 万圣节
            Date halloweenStart = MktComUtil.getDateRange("aos_datefrom", "Halloween", orgId);
            // 万圣节
            Date halloweenEnd = MktComUtil.getDateRange("aos_dateto", "Halloween", orgId);
            // 圣诞节
            Date christmasStart = MktComUtil.getDateRange("aos_datefrom", "Christmas", orgId);
            // 圣诞节
            Date christmasEnd = MktComUtil.getDateRange("aos_dateto", "Christmas", orgId);
            Map<String, Integer> productNoMap = new HashMap<>(16);
            Map<String, BigDecimal> productNoBidMap = new HashMap<>(16);
            // 获取营销国别参数
            // AM营收占比
            BigDecimal popOrgAmSaleRate = (BigDecimal)popOrgInfo.get(orgId + "~" + "AMSALE_RATE").get("aos_value");
            // AM付费营收占比
            BigDecimal popOrgAmAffRate = (BigDecimal)popOrgInfo.get(orgId + "~" + "AMAFF_RATE").get("aos_value");
            // 国别标准ROI
            BigDecimal popOrgRoist = (BigDecimal)popOrgInfo.get(orgId + "~" + "ROIST").get("aos_value");
            // 很差
            BigDecimal worst = (BigDecimal)popOrgInfo.get(orgId + "~" + "WORST").get("aos_value");
            // 差
            BigDecimal worry = (BigDecimal)popOrgInfo.get(orgId + "~" + "WORRY").get("aos_value");
            // 标准
            BigDecimal standard = (BigDecimal)popOrgInfo.get(orgId + "~" + "STANDARD").get("aos_value");
            // 好
            BigDecimal well = (BigDecimal)popOrgInfo.get(orgId + "~" + "WELL").get("aos_value");
            // 优
            BigDecimal excellent = (BigDecimal)popOrgInfo.get(orgId + "~" + "EXCELLENT").get("aos_value");
            // 国别首次出价均价
            BigDecimal popOrgFirstBid = (BigDecimal)popOrgInfo.get(orgId + "~" + "FIRSTBID").get("aos_value");
            // 最高标准1(定价<200)
            BigDecimal high1 = (BigDecimal)popOrgInfo.get(orgId + "~" + "HIGH1").get("aos_value");
            // 最高标准2(200<=定价<500)
            BigDecimal high2 = (BigDecimal)popOrgInfo.get(orgId + "~" + "HIGH2").get("aos_value");
            // 最高标准3(定价>=500)
            BigDecimal high3 = (BigDecimal)popOrgInfo.get(orgId + "~" + "HIGH3").get("aos_value");
            // 国别曝光标准
            BigDecimal exposure = (BigDecimal)popOrgInfo.get(orgId + "~" + "EXPOSURE").get("aos_value");
            // 转化率国别标准
            BigDecimal roiStandard = (BigDecimal)popOrgInfo.get(orgId + "~" + "ROISTANDARD").get("aos_value");
            // 国别标准均价
            BigDecimal standardFix = (BigDecimal)popOrgInfo.get(orgId + "~" + "STANDARDFIX").get("aos_value");
            // 国别海外库存标准
            BigDecimal qtyStandard = (BigDecimal)popOrgInfo.get(orgId + "~" + "QTYSTANDARD").get("aos_value");
            // 出价标准~最低出价
            BigDecimal lowestBid = (BigDecimal)popOrgInfo.get(orgId + "~" + "LOWESTBID").get("aos_value");
            // 国别转化率差标准
            BigDecimal exRateLowSt = getExRateLowSt(pOuCode, "差");
            BigDecimal exRateWellSt = getExRateLowSt(pOuCode, "优");
            BigDecimal exRateGoodSt = getExRateLowSt(pOuCode, "好");
            BigDecimal lowClick = (BigDecimal)popOrgInfo.get(orgId + "~" + "国别低点击标准(7天)").get("aos_value");

            Map<String, BigDecimal> orgCpcMap = queryOrgCpc(orgId.toString());
            HashMap<String, Object> act = generateAct();
            Map<String, BigDecimal> vatMap = generateVat(orgId);
            Map<String, BigDecimal> costMap = initItemCost();
            Map<String, BigDecimal> shipFee = generateShipFee();
            BigDecimal aosVatAmount = vatMap.get("aos_vat_amount");
            BigDecimal aosAmPlatform = vatMap.get("aos_am_platform");
            // 平台上架信息
            Set<String> onlineSkuSet = generateOnlineSkuSet(orgId);
            Set<String> deseaon = generateDeseaon();
            Set<String> mustSet = generateMustSet(orgId);
            // 国别大类点击标准
            Map<String, Integer> orgCateClickMap = genOrgCateClick(orgId);

            // 获取昨天的所有差ROI剔除数据
            DynamicObjectCollection list =
                QueryServiceHelper.query("aos_mkt_popular_ppc", "aos_entryentity.aos_itemnumer aos_itemnumer",
                    new QFilter[] {new QFilter("aos_date", QCP.like, yester + "%"),
                        new QFilter("aos_orgid.number", QCP.equals, pOuCode),
                        new QFilter("aos_entryentity.aos_roiflag", QCP.equals, true),});
            Set<String> roiItemSet = new HashSet<>();
            for (DynamicObject obj : list) {
                roiItemSet.add(obj.getString("aos_itemnumer"));
            }
            // 获取昨天的所有定价毛利剔除数据
            DynamicObjectCollection listPro =
                QueryServiceHelper.query("aos_mkt_popular_ppc", "aos_entryentity.aos_itemnumer aos_itemnumer",
                    new QFilter[] {new QFilter("aos_date", QCP.like, yester + "%"),
                        new QFilter("aos_orgid.number", QCP.equals, pOuCode),
                        new QFilter("aos_entryentity.aos_groupstatus", QCP.equals, "PRO"),});
            Set<String> proItemSet = new HashSet<>();
            for (DynamicObject obj : listPro) {
                proItemSet.add(obj.getString("aos_itemnumer"));
            }

            // 获取昨天的所有可用组
            DynamicObjectCollection lastAva =
                QueryServiceHelper.query("aos_mkt_popular_ppc", "aos_entryentity.aos_itemnumer aos_itemnumer",
                    new QFilter[] {new QFilter("aos_date", QCP.like, yester + "%"),
                        new QFilter("aos_orgid.number", QCP.equals, pOuCode),
                        new QFilter("aos_entryentity.aos_groupstatus", QCP.equals, "AVAILABLE"),});
            Set<String> lastAvaSet = new HashSet<>();
            for (DynamicObject obj : lastAva) {
                lastAvaSet.add(obj.getString("aos_itemnumer"));
            }

            // 最近一次上线日期
            Map<String, Date> onlineDate = new HashMap<>(16);
            DynamicObjectCollection dyns = QueryServiceHelper.query("aos_mkt_sp_date",
                "aos_entryentity.aos_itemstr aos_itemstr," + "aos_entryentity.aos_online aos_online",
                new QFilter[] {new QFilter("aos_orgid", QCP.equals, orgId)});
            for (DynamicObject d : dyns) {
                onlineDate.put(d.getString("aos_itemstr"), d.getDate("aos_online"));
            }

            // 获取项鑫报告数据
            DynamicObjectCollection listRpt = QueryServiceHelper.query("aos_mkt_tmp", "aos_group",
                new QFilter[] {new QFilter("aos_orgid.number", QCP.equals, pOuCode)});
            Set<String> rptSet = new HashSet<>();
            for (DynamicObject obj : listRpt) {
                rptSet.add(obj.getString("aos_group"));
            }
            // 营销出价调整幅度参数表
            String selectColumn = "aos_roi," + "aos_roitype," + "aos_exposure," + "aos_exposurerate," + "aos_avadays,"
                + "aos_avadaysvalue," + "aos_costrate," + "aos_costratevalue," + "aos_rate,aos_level,"
                + "aos_roi3,aos_roitype3";
            DataSet aosMktBsadjrateS = QueryServiceHelper.queryDataSet("aos_mkt_popppc_init." + pOuCode,
                "aos_mkt_bsadjrate", selectColumn, null, "aos_level");
            // 营销预算调整系数表 到国别维度
            selectColumn = "aos_ratefrom,aos_rateto,aos_roi,aos_adjratio";
            DataSet aosMktBsadjparaS = QueryServiceHelper.queryDataSet("aos_mkt_popppc_init." + pOuCode,
                "aos_mkt_bsadjpara", selectColumn, new QFilter("aos_orgid", "=", orgId).toArray(), null);
            // 每日价格
            HashMap<String, String> mktProductinfo = generateProductInfo();
            // 循环国别物料
            QFilter filterOu = new QFilter("aos_contryentry.aos_nationality.number", "=", pOuCode);
            QFilter filterType = new QFilter("aos_protype", "=", "N");
            QFilter[] filters = new QFilter[] {filterOu, filterType};
            String selectField = "id,aos_productno," + "number," + "aos_cn_name,"
                + "aos_contryentry.aos_nationality.id aos_orgid,"
                + "aos_contryentry.aos_nationality.number aos_orgnumber,"
                + "aos_contryentry.aos_seasonseting.number aos_seasonseting,"
                + "aos_contryentry.aos_seasonseting.name aos_season,"
                + "aos_contryentry.aos_seasonseting.id aos_seasonid,"
                + "aos_contryentry.aos_contryentrystatus aos_contryentrystatus,"
                + "aos_contryentry.aos_festivalseting.number aos_festivalseting,"
                + "aos_contryentry.aos_firstindate aos_firstindate," + "aos_contryentry.aos_is_saleout aos_is_saleout,"
                + "aos_isrepliceold," + "aos_oldnumber," + "aos_contryentry.aos_onshelfreplace aos_onshelfreplace";
            DynamicObjectCollection bdMaterialS =
                QueryServiceHelper.query("bd_material", selectField, filters, "aos_productno");
            DynamicObject aosMktPopularPpc = BusinessDataServiceHelper.newDynamicObject("aos_mkt_popular_ppc");
            aosMktPopularPpc.set("aos_orgid", orgId);
            aosMktPopularPpc.set("billstatus", "A");
            aosMktPopularPpc.set("aos_status", "A");
            aosMktPopularPpc.set("aos_billno", aosBillno);
            aosMktPopularPpc.set("aos_poptype", FndGlobal.get_import_id("SP", "aos_mkt_base_poptype"));
            aosMktPopularPpc.set("aos_date", todayD);
            aosMktPopularPpc.set("aos_channel", FndGlobal.get_import_id("AMAZON", "aos_sal_channel"));
            DynamicObjectCollection aosEntryentityS = aosMktPopularPpc.getDynamicObjectCollection("aos_entryentity");
            Set<String> eliminateItemSet = getEliminateItem(orgId.toString());
            // 初始化历史记录
            FndLog log = FndLog.init("MKT_PPC数据源初始化", pOuCode.toString() + year + month + day);
            // 获取7天内的入库的物料的最新时间，并且根据时间对物料分组
            Map<String, List<String>> mapWareHoseDateToItem = query7daysWareHouseItem(orgId, todayD);
            // 判断7天内有入库的物料，入库前30天是否断货 记录物料的是否存在不断货，存在为true,全断货为false
            Map<String, Boolean> mapItemToOutage = new HashMap<>(16);
            // 具体判断断货
            for (Map.Entry<String, List<String>> entry : mapWareHoseDateToItem.entrySet()) {
                // 先全设置物料为断货
                for (String itemId : entry.getValue()) {
                    if (!mapItemToOutage.containsKey(itemId)) {
                        mapItemToOutage.put(itemId, false);
                    }
                }
                // 判断是否存在不断货
                judge30DaysStock(orgId, entry.getKey(), entry.getValue(), mapItemToOutage);
            }
            // 获取物料前一天的特殊行
            Map<String, String> mapItemToAdd = querySaleAdd(orgId);
            // 获取固定剔除剔除物料
            List<String> listItemRejectIds = getRejectItem(orgId);

            HashMap<String, String> rptPro = getRptPro(orgId);

            for (DynamicObject bdMaterial : bdMaterialS) {
                // 判断是否跳过
                long itemIdLong = bdMaterial.getLong("id");
                long orgIdLong = bdMaterial.getLong("aos_orgid");
                String orgIdStr = Long.toString(orgIdLong);
                String itemIdStr = Long.toString(itemIdLong);
                String aosItemnumer = bdMaterial.getString("number");
                String aosProductno = bdMaterial.getString("aos_productno");

                if (rptPro.containsKey(aosItemnumer)) {
                    aosProductno = rptPro.get(aosItemnumer);
                }

                int aosShpDay = (int)aosShpdayMap.get(orgIdStr);
                int aosFreightDay = (int)aosCleardayMap.get(orgIdStr);
                int aosClearDay = (int)aosFreightdayMap.get(orgIdStr);
                Boolean aosIsSaleout = bdMaterial.getBoolean("aos_is_saleout");

                if (eliminateItemSet.contains(itemIdStr)) {
                    log.add(aosItemnumer + "营销已剔除!");
                    continue;
                }

                if (!onlineSkuSet.contains(itemIdStr)) {
                    log.add(aosItemnumer + "平台上架信息不存在!");
                    continue;
                }

                String bseStItem = null;
                if (baseStItem.get(orgIdStr + "~" + itemIdStr) != null) {
                    bseStItem = String.valueOf(baseStItem.get(orgIdStr + "~" + itemIdStr));
                }
                // 获取AMAZON店铺货号 如果没有则跳过

                // 获取老货号
                Boolean aosIsRepliceOld = bdMaterial.getBoolean("aos_isrepliceold");
                String aosOldNumber = bdMaterial.getString("aos_oldnumber");
                Boolean aosOnShelfReplace = bdMaterial.getBoolean("aos_onshelfreplace");

                String aosShopsku;
                if (aosIsRepliceOld && FndGlobal.IsNotNull(aosOldNumber) && aosOnShelfReplace) {
                    aosShopsku = mktProductinfo.get(pOuCode + "~" + aosOldNumber);
                } else {
                    aosShopsku = mktProductinfo.get(pOuCode + "~" + aosItemnumer);
                }

                if (FndGlobal.IsNull(aosShopsku)) {
                    log.add(aosItemnumer + "AMAZON店铺货号不存在");
                    continue;
                }
                Date aosFirstindate = bdMaterial.getDate("aos_firstindate");
                String aosSeasonpro = bdMaterial.getString("aos_seasonseting");
                String aosCnName = bdMaterial.getString("aos_cn_name");
                String aosSeason = bdMaterial.getString("aos_season");
                Object aosSeasonid = bdMaterial.get("aos_seasonid");
                if (aosSeasonpro == null) {
                    aosSeasonpro = "";
                }
                String aosItemstatus = bdMaterial.getString("aos_contryentrystatus");
                String aosFestivalseting = bdMaterial.getString("aos_festivalseting");
                if (aosFestivalseting == null) {
                    aosFestivalseting = "";
                }
                if (aosSeasonid == null) {
                    log.add(aosItemnumer + "季节属性不存在");
                    continue;
                }

                Object aosAge = itemMaxageMap.getOrDefault(orgIdStr + "~" + itemIdStr, 0);
                Object itemOverseaqty = itemOverseaqtyMap.getOrDefault(orgIdStr + "~" + itemIdStr, 0);
                if ("C".equals(aosItemstatus) && (int)itemOverseaqty == 0) {
                    continue;// 终止状态且无海外库存 跳过
                }
                Object itemIntransqty = itemIntransqtyMap.getOrDefault(orgIdStr + "~" + itemIdStr, 0);

                // 产品号不存在 跳过
                if (Cux_Common_Utl.IsNull(aosProductno)) {
                    log.add(aosItemnumer + "产品号不存在");
                    continue;
                }

                int auto = 0;
                if (productNoMap.get(aosProductno + "-AUTO") != null) {
                    auto = productNoMap.get(aosProductno + "-AUTO");
                }
                productNoMap.put(aosProductno + "-AUTO", auto);

                // =====是否新系列新组判断=====
                Map<String, Object> ppcInfoMap = ppcInfo.get(orgIdStr + "~" + itemIdStr);
                Map<String, Object> ppcInfoSerialMap = ppcInfoSerial.get(orgIdStr + "~" + aosProductno + "-AUTO");
                Object aosMakeDate = null;
                Object aosGroupDate = null;
                if (ppcInfoMap != null) {
                    aosGroupDate = ppcInfoMap.getOrDefault("aos_groupdate", todayD);
                }

                if (aosGroupDate == null) {
                    aosGroupDate = todayD;
                }

                if (ppcInfoSerialMap != null) {
                    aosMakeDate = ppcInfoSerialMap.getOrDefault("aos_makedate", todayD);
                }

                if (aosMakeDate == null) {
                    aosMakeDate = todayD;
                }

                // =====结束是否新系列新组判断=====
                String itemCategoryName = CommonDataSom.getItemCategoryName(itemIdStr);
                String aosCategory1 = "";
                String aosCategory2 = "";
                String aosCategory3 = "";
                if (!"".equals(itemCategoryName) && null != itemCategoryName) {
                    String[] split = itemCategoryName.split(",");
                    if (split.length == 3) {
                        aosCategory1 = split[0];
                        aosCategory2 = split[1];
                        aosCategory3 = split[2];
                    }
                }

                // 点击数标准
                int clickStd = orgCateClickMap.getOrDefault(aosCategory1, 0);

                // 获取销售组别并赋值
                // 2.获取产品小类
                String itemCategoryId = CommonDataSom.getItemCategoryId(String.valueOf(itemIdStr));
                if (itemCategoryId == null || "".equals(itemCategoryId)) {
                    log.add(aosItemnumer + "产品类别不存在自动剔除");
                    continue;
                }
                // 3.根据小类获取组别
                String salOrg = CommonDataSom.getSalOrgV2(String.valueOf(orgId), itemCategoryId);
                if (salOrg == null || "".equals(salOrg)) {
                    log.add(aosItemnumer + "组别不存在自动剔除");
                    continue;
                }

                // Sku报告
                Map<String, Object> skuRptMap14 = skuRpt14.get(orgIdStr + "~" + itemIdStr);
                // 14天ROI
                BigDecimal roi14Days = BigDecimal.ZERO;
                // 昨日花费
                BigDecimal lastSpend = BigDecimal.ZERO;
                // 14天点击
                BigDecimal aosClicks14Days = BigDecimal.ZERO;
                int aosOnline = 0;
                if (skuRptMap14 != null && ((BigDecimal)skuRptMap14.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                    roi14Days = ((BigDecimal)skuRptMap14.get("aos_total_sales"))
                        .divide((BigDecimal)skuRptMap14.get("aos_spend"), 2, RoundingMode.HALF_UP);
                    aosClicks14Days = (BigDecimal)skuRptMap14.get("aos_clicks");
                    aosOnline = (int)skuRptMap14.get("aos_online");
                }
                // Sku报告1日数据
                Map<String, Object> skuRptDetailMap = skuRptDetail.get(orgId + "~" + itemIdStr);
                if (!Cux_Common_Utl.IsNull(skuRptDetailMap)) {
                    lastSpend = (BigDecimal)skuRptDetailMap.get("aos_spend");
                }
                // =====开始定价毛利剔除判断=====
                Object fixValue = dailyPrice.getOrDefault(orgIdStr + "~" + itemIdStr, BigDecimal.ZERO);
                BigDecimal inventoryCost = costMap.getOrDefault(orgIdStr + "~" + itemIdStr, BigDecimal.ZERO);
                BigDecimal expressFee = shipFee.getOrDefault(orgIdStr + "~" + itemIdStr, BigDecimal.ZERO);
                BigDecimal profitrate =
                    SalUtil.calProfitrate((BigDecimal)fixValue, aosVatAmount, aosAmPlatform, inventoryCost, expressFee);

                log.add(aosItemnumer + " FixValue =" + fixValue + "\n" + " inventoryCost =" + inventoryCost + "\n"
                    + " expressFee =" + expressFee + "\n" + " aos_vat_amount =" + aosVatAmount + "\n"
                    + " aos_am_platform =" + aosAmPlatform + "\n");

                log.add(aosItemnumer + "(" + fixValue + " / (1 + " + aosVatAmount + ") - " + inventoryCost + " - "
                    + fixValue + " / (1 +  " + aosVatAmount + ") * " + aosAmPlatform + " - " + expressFee + " / (1 + "
                    + aosVatAmount + "+)) / (" + fixValue + " / (1 + " + aosVatAmount + "+))");

                // 上次出价
                Object aosLastbid = BigDecimal.ZERO;
                // 默认昨日数据
                Map<String, Object> ppcYesterMap = ppcYester.get(orgIdStr + "~" + itemIdStr);
                if (ppcYesterMap != null) {
                    // 昨日出价
                    aosLastbid = ppcYesterMap.get("aos_bid");
                }
                // 7日ROI Sku报告7日
                Map<String, Object> skuRptMap = skuRpt.get(orgIdStr + "~" + itemIdStr);
                // 7天ROI
                BigDecimal roi7Days = BigDecimal.ZERO;
                BigDecimal spend7Days;
                if (skuRptMap != null && ((BigDecimal)skuRptMap.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                    spend7Days = (BigDecimal)skuRptMap.get("aos_spend");
                    roi7Days =
                        ((BigDecimal)skuRptMap.get("aos_total_sales")).divide(spend7Days, 2, RoundingMode.HALF_UP);
                }
                // 7日转化率
                BigDecimal aosRptRoi = BigDecimal.ZERO;
                BigDecimal aosClicks;
                // Sku报告
                Map<String, Object> skuRptSerialMap = skuRptSerial14.get(orgIdStr + "~" + aosProductno + "-AUTO");
                if (skuRptSerialMap != null
                    && ((BigDecimal)skuRptSerialMap.get("aos_clicks")).compareTo(BigDecimal.ZERO) != 0) {
                    aosClicks = (BigDecimal)skuRptSerialMap.get("aos_clicks");
                    aosRptRoi =
                        ((BigDecimal)skuRptSerialMap.get("aos_total_order")).divide(aosClicks, 3, RoundingMode.HALF_UP);
                }

                Map<String, Object> dayMap = new HashMap<>(16);
                // 可售天数
                int availableDays = InStockAvailableDays.calInstockSalDaysForDayMin(orgIdStr, itemIdStr, dayMap);

                String saleAdd = "false";
                if (mapItemToAdd.containsKey(aosItemnumer)) {
                    saleAdd = mapItemToAdd.get(aosItemnumer);
                }

                Object aosActivity = act.get(orgIdStr + "~" + itemIdStr);
                boolean actFlag = aosActivity != null && aosActivity.toString().contains("7DD");
                // 7DD活动不参与剔除

                // 初始化插入参数
                Map<String, Object> insertMap = new HashMap<>(16);
                insertMap.put("item_id", itemIdStr);
                insertMap.put("aos_productno", aosProductno);
                insertMap.put("aos_itemnumer", aosItemnumer);
                insertMap.put("date", todayD);
                insertMap.put("aos_shopsku", aosShopsku);
                insertMap.put("aos_seasonseting", aosSeason);
                insertMap.put("aos_contryentrystatus", aosItemstatus);
                insertMap.put("aos_cn_name", aosCnName);
                insertMap.put("aos_makedate", aosMakeDate);
                insertMap.put("aos_groupdate", aosGroupDate);
                insertMap.put("aos_bid", lowestBid);
                insertMap.put("aos_basestitem", bseStItem);
                insertMap.put("aos_category1", aosCategory1);
                insertMap.put("aos_category2", aosCategory2);
                insertMap.put("aos_category3", aosCategory3);
                insertMap.put("aos_sal_group", salOrg);
                insertMap.put("aos_roi14days", roi14Days);
                insertMap.put("aos_lastspend", lastSpend);
                insertMap.put("aos_profit", profitrate);
                insertMap.put("aos_age", aosAge);
                insertMap.put("aos_lastbid", aosLastbid);
                insertMap.put("aos_roi7days", roi7Days);
                insertMap.put("aos_avadays", availableDays);
                insertMap.put("aos_overseaqty", itemOverseaqty);
                insertMap.put("aos_online", onlineDate.get(aosItemnumer));
                insertMap.put("aos_is_saleout", aosIsSaleout);
                insertMap.put("aos_special", saleAdd);
                insertMap.put("aos_firstindate", aosFirstindate);
                insertMap.put("aos_rpt_roi", aosRptRoi);

                boolean olFlag = true;

                if (olFlag) {

                    // 剔除海外库存
                    if ((int)itemOverseaqty <= qtyStandard.intValue() && !"E".equals(aosItemstatus)
                        && !"A".equals(aosItemstatus) && (int)itemIntransqty == 0) {
                        log.add(aosItemnumer + "海外库存剔除 国别库存标准 =" + itemOverseaqty + "<=" + qtyStandard);
                        log.add(aosItemnumer + "海外库存剔除 在途 =" + itemIntransqty);
                        insertData(aosEntryentityS, insertMap, "LOWQTY");
                        continue;
                    }
                    // 低库存剔除
                    if ((int)itemOverseaqty < safeQty) {
                        log.add(aosItemnumer + "低库存剔除 海外库存小于安全库存 =" + itemOverseaqty + "<" + safeQty);
                        insertData(aosEntryentityS, insertMap, "LOWQTY");
                        continue;
                    }
                    // 固定sku剔除
                    if (listItemRejectIds.contains(itemIdStr)) {
                        log.add(aosItemnumer + "  固定剔除物料");
                        insertData(aosEntryentityS, insertMap, "ROI");
                        continue;
                    }
                    // 判断是否是必推货号
                    boolean mustFlag = !mustSet.contains(itemIdStr);
                    if (mustFlag) {
                        // 节日品低库存剔除
                        boolean lowFlag = ((int)itemOverseaqty < 10
                            && ("其它节日装饰".equals(aosCategory2) || "圣诞装饰".equals(aosCategory2)));
                        if (lowFlag) {
                            log.add(aosItemnumer + "节日品 低库存小于10剔除  =" + itemOverseaqty);
                            insertData(aosEntryentityS, insertMap, "LOWQTY");
                            continue;
                        }

                        // 非节日品剔除预断货 节日品节日2天内剔除 圣诞装饰 这两个中类不做预断货剔除 销量为3日均与7日均的最小值
                        if (FndGlobal.IsNull(aosFestivalseting) && !"圣诞装饰".equals(aosCategory2)
                            && !"其它节日装饰".equals(aosCategory2)) {

                            // 剔除过季品
                            // 春夏品过季
                            boolean ssFlag = ("SPRING".equals(aosSeasonpro) || "SPRING_SUMMER".equals(aosSeasonpro)
                                || "SUMMER".equals(aosSeasonpro))
                                && (todayD.before(summerSpringStart) || todayD.after(summerSpringEnd));
                            if (ssFlag) {
                                log.add(aosItemnumer + "过季品剔除");
                                insertData(aosEntryentityS, insertMap, "SEASON");
                                continue;
                            } else {
                                if (deseaon.contains(aosCategory1 + "~" + aosCategory2 + "~" + aosCategory3)) {
                                    log.add(aosItemnumer + "不按季节属性推广品类");
                                    insertData(aosEntryentityS, insertMap, "DESEASON");
                                    continue;
                                }
                            }

                            boolean awFlag = (("AUTUMN_WINTER".equals(aosSeasonpro) || "WINTER".equals(aosSeasonpro))
                                && (todayD.before(autumnWinterStart) || todayD.after(autumnWinterEnd)));
                            if (awFlag) {
                                insertData(aosEntryentityS, insertMap, "SEASON");
                                log.add(aosItemnumer + "过季品剔除");
                                continue;
                            }

                            log.add(aosItemnumer + "非节日品预断货参数数据" + "item_intransqty =" + itemIntransqty
                                + "item_overseaqty =" + itemOverseaqty + " 可售库存天数 =" + availableDays + " 3日销量 ="
                                + dayMap.get("for3day") + " 7日销量 =" + dayMap.get("for7day"));

                            // 季节品 季节品 季末达标 CONTINUE; 剔除
                            boolean isSeasonEnd = false;
                            if ("SPRING".equals(aosSeasonpro) || "SPRING_SUMMER".equals(aosSeasonpro)
                                || "SUMMER".equals(aosSeasonpro)) {
                                if (todayD.after(summerSpringSeasonEnd)) {
                                    // 季末 判断是否达标
                                    isSeasonEnd = true;
                                    float seasonRate = MktComUtil.getSeasonRate(orgIdLong, itemIdLong, aosSeasonpro,
                                        itemOverseaqty, month);
                                    if (!MktComUtil.isSeasonRate(aosSeasonpro, month, seasonRate)) {
                                        insertData(aosEntryentityS, insertMap, "OFFSALE");
                                        continue;
                                    }
                                }
                            }
                            if ("AUTUMN_WINTER".equals(aosSeasonpro) || "WINTER".equals(aosSeasonpro)) {
                                // 判断是否季末
                                if (todayD.after(autumnWinterSeasonEnd)) {
                                    isSeasonEnd = true;
                                    // 季末 判断是否达标
                                    float seasonRate = MktComUtil.getSeasonRate(orgIdLong, itemIdLong, aosSeasonpro,
                                        itemOverseaqty, month);
                                    if (!MktComUtil.isSeasonRate(aosSeasonpro, month, seasonRate)) {
                                        insertData(aosEntryentityS, insertMap, "OFFSALE");
                                        continue;
                                    }
                                }
                            }
                            // (海外在库+在途数量)/7日均销量)<30 或 满足销售预断货逻辑 则为营销预断货逻辑 且 不能为季节品季末 且可售天数小于45
                            int itemavadays = mapItemavadays.getOrDefault(pOuCode + "~" + aosItemnumer, 0);
                            // 预断货标记
                            boolean offSaleFlag = (((availableDays < 30)
                                || ((MktComUtil.isPreSaleOut(orgIdLong, itemIdLong, (int)itemIntransqty, aosShpDay,
                                    aosFreightDay, aosClearDay, availableDays)) && itemavadays < 45))
                                && !isSeasonEnd);
                            if (offSaleFlag) {
                                insertData(aosEntryentityS, insertMap, "OFFSALE");
                                continue;
                            }
                        } else {
                            // 万圣是否在日期内
                            boolean halloween =
                                (("HALLOWEEN".equals(aosFestivalseting) || "其它节日装饰".equals(aosCategory2))
                                    && (!(todayD.after(halloweenStart) && todayD.before(halloweenEnd))));
                            if (halloween) {
                                insertData(aosEntryentityS, insertMap, "SEASON");
                                log.add(aosItemnumer + "节日品剔除");
                                continue;
                            }
                            // 圣诞是否在日期内
                            boolean christmas = (("CHRISTMAS".equals(aosFestivalseting) || "圣诞装饰".equals(aosCategory2))
                                && (!(todayD.after(christmasStart) && todayD.before(christmasEnd))));
                            if (christmas) {
                                insertData(aosEntryentityS, insertMap, "SEASON");
                                log.add(aosItemnumer + "节日品剔除");
                                continue;
                            }
                        }
                        // 周24持续剔除
                        boolean thurTuesDayRoiFlag = ((week == Calendar.THURSDAY || week == Calendar.TUESDAY)
                            && roiItemSet.contains(aosItemnumer));
                        if (thurTuesDayRoiFlag) {
                            insertData(aosEntryentityS, insertMap, "ROI");
                            log.add(aosItemnumer + "差ROI自动剔除");
                            continue;
                        }
                        // 周24持续剔除
                        boolean thurTuesDayProFlag = ((week == Calendar.THURSDAY || week == Calendar.TUESDAY)
                            && proItemSet.contains(aosItemnumer));
                        if (thurTuesDayProFlag) {
                            insertData(aosEntryentityS, insertMap, "PRO");
                            log.add(aosItemnumer + "定价低毛利自动剔除");
                            continue;
                        }
                        // =====各类标记=====
                        // 新品
                        boolean newFlag =
                            aosFirstindate == null || (("E".equals(aosItemstatus) || "A".equals(aosItemstatus))
                                && (aosFirstindate != null) && MktComUtil.getBetweenDays(todayD, aosFirstindate) <= 14);
                        // 春夏品
                        boolean springSummerFlag =
                            ("SPRING".equals(aosSeasonpro) || "SPRING_SUMMER".equals(aosSeasonpro)
                                || "SUMMER".equals(aosSeasonpro) || "SPRING-SUMMER-CONVENTIONAL".equals(aosSeasonpro))
                                && (todayD.after(summerSpringUnStart) && todayD.before(summerSpringUnEnd));
                        // 秋冬品
                        boolean autumnWinterFlag =
                            ("AUTUMN_WINTER".equals(aosSeasonpro) || "WINTER".equals(aosSeasonpro))
                                && (todayD.after(autumnWinterUnStart) && todayD.before(autumnWinterUnEnd));
                        // 14日在线
                        boolean online14Days =
                            aosOnline >= 10 && MktComUtil.getBetweenDays(todayD, (Date)aosGroupDate) > 14;
                        // 7日在线
                        boolean online7Days = aosOnline >= 5 && MktComUtil.getBetweenDays(todayD, (Date)aosGroupDate) > 7;
                        // =====开始ROI剔除判断=====
                        boolean roiFlag = false;
                        String roiType = null;
                        // a.14 日在线 14天ROI＜4；且7天<8(不包括新品、季节品)
                        if (online14Days && roi14Days.compareTo(BigDecimal.valueOf(4)) < 0
                            && aosClicks14Days.compareTo(BigDecimal.valueOf(clickStd)) > 0 && !newFlag
                            && !springSummerFlag && !autumnWinterFlag) {
                            roiFlag = true;
                            roiType = "a";
                        }
                        // b.7日在线 秋冬品7日ROI<3； 所有秋冬
                        if (online7Days && autumnWinterFlag && roi14Days.compareTo(BigDecimal.valueOf(3)) < 0) {
                            roiFlag = true;
                            roiType = "b";
                        }
                        // c.在线7天，不到14天，点击＞国别标准(US300,其它150)，ROI＜4(不包括季节品)
                        if (online7Days && !online14Days && roi14Days.compareTo(BigDecimal.valueOf(4)) < 0
                            && aosClicks14Days.compareTo(BigDecimal.valueOf(clickStd)) > 0 && !springSummerFlag
                            && !autumnWinterFlag) {
                            roiFlag = true;
                            roiType = "c";
                        }
                        // d.历史剔除 持续剔除 项鑫报告中不存在才处理
                        if (roiItemSet.contains(aosItemnumer) && !rptSet.contains(aosItemnumer)) {
                            roiFlag = true;
                            roiType = "d";
                        }

                        // ==以下为ROI加回 ==//
                        // d2.爆品加回
                        if (FndGlobal.IsNotNull(aosIsSaleout) && aosIsSaleout) {
                            roiFlag = false;
                            roiType = "d2";
                        }
                        // e.秋冬品历史剔除 7月ROI> 8 加回
                        if (autumnWinterFlag && roiItemSet.contains(aosItemnumer)
                            && roi14Days.compareTo(BigDecimal.valueOf(8)) > 0) {
                            roiFlag = false;
                            roiType = "e";
                        }
                        // f.过去14日ROI大于7 则加回
                        if (roi14Days.compareTo(BigDecimal.valueOf(7)) > 0 && roiItemSet.contains(aosItemnumer)) {
                            roiFlag = false;
                            roiType = "f";
                        }
                        // g.最近一次入库(七天内)前30天全断货不剔除
                        if (mapItemToOutage.containsKey(itemIdStr)) {
                            roiFlag = false;
                            roiType = "g";
                        }
                        // h.新品14天内不剔除；
                        if (newFlag) {
                            roiFlag = false;
                            roiType = "h";
                        }
                        // i.圣诞装饰默认差ROI不剔除
                        if ("圣诞装饰".equals(aosCategory2) || "其它节日装饰".equals(aosCategory2)) {
                            roiFlag = false;
                            roiType = "i";
                        }
                        // j.7DD不剔除
                        if (actFlag) {
                            roiFlag = false;
                            roiType = "ji";
                        }
                        // k.
                        if ("true".equals(saleAdd)) {
                            roiFlag = false;
                            roiType = "k";
                        }

                        // 周2周4不剔除
                        if (week == Calendar.TUESDAY && week == Calendar.THURSDAY) {
                            roiFlag = false;
                        }
                        // 差转换
                        boolean exFlag = true;
                        // 进行差ROI 与差转换不剔除判断
                        // ①季节品：季初，差ROI，差转化；
                        boolean seasonFlag = ((("SPRING".equals(aosSeasonpro) || "SPRING_SUMMER".equals(aosSeasonpro)
                            || "SUMMER".equals(aosSeasonpro))
                            && (todayD.after(summerSpringStart) && todayD.before(summerSpringFirst)))
                            || (("AUTUMN_WINTER".equals(aosSeasonpro) || "WINTER".equals(aosSeasonpro))
                                && (todayD.after(autumnWinterStart) && todayD.before(autumnWinterFirst))));
                        if (seasonFlag) {
                            roiFlag = false;
                            // 差转换
                            exFlag = false;
                        }
                        // ②首次入库≤30天：差ROI、差转化；
                        if (FndGlobal.IsNotNull(aosFirstindate)
                            && FndDate.GetBetweenDays(todayD, aosFirstindate) <= 30) {
                            roiFlag = false;
                            // 差转换
                            exFlag = false;
                        }
                        // TODO ③节日品：预断货、差ROI、差转化；

                        // ④爆品：差ROI、差转化；
                        if (FndGlobal.IsNotNull(aosIsSaleout) && aosIsSaleout) {
                            roiFlag = false;
                            exFlag = false;
                        }

                        // 进行差ROI 与差转换加回
                        // ①当过去14天ROI≥8，或转化率>国别标准
                        if (roi14Days.compareTo(BigDecimal.valueOf(8)) > 0 || aosRptRoi.compareTo(roiStandard) > 0) {
                            roiFlag = false;
                            exFlag = false;
                        }
                        // ②断货天数＞30，再入库;
                        if (mapItemToOutage.containsKey(itemIdStr)) {
                            roiFlag = false;
                            exFlag = false;
                        }
                        // ③本周参加AM-7DD活动的SKU；
                        if (actFlag) {
                            roiFlag = false;
                            exFlag = false;
                        }

                        if (roiFlag) {
                            insertData(aosEntryentityS, insertMap, "ROI");
                            log.add(aosItemnumer + " " + roiType + " 差ROI自动剔除");
                            continue;
                        }

                        // 差转化率剔除
                        boolean lowChangeFlag =
                            ((aosRptRoi.compareTo(exRateLowSt) <= 0 && roi14Days.compareTo(BigDecimal.valueOf(8)) < 0
                                && aosClicks14Days.compareTo(BigDecimal.valueOf(clickStd)) > 0 && exFlag)
                                && !"圣诞装饰".equals(aosCategory2) && !"其它节日装饰".equals(aosCategory2));
                        if (lowChangeFlag) {
                            insertData(aosEntryentityS, insertMap, "差转化率剔除");
                            log.add(aosItemnumer + " 差转化率自动剔除");
                            continue;
                        }

                        // 低定价毛利剔除
                        /* boolean ProFlag = !proItemSet.contains(aos_itemnumer) && Profitrate.compareTo(BigDecimal.valueOf(0.08)) < 0;
                        // a.历史中未剔除 且定价毛利＜8%；；
                        // b.历史剔除 持续剔除 项鑫报告中不存在才处理
                        if (proItemSet.contains(aos_itemnumer) && !rptSet.contains(aos_itemnumer)) {
                            ProFlag = true;
                        }
                        // c.历史中剔除 前14天ROI>7 或等于0 且定价毛利>=12% 加回
                        if (proItemSet.contains(aos_itemnumer)
                                && (Roi14Days.compareTo(BigDecimal.valueOf(7)) > 0
                                || Roi7Days.compareTo(BigDecimal.ZERO) == 0)
                                && Profitrate.compareTo(BigDecimal.valueOf(0.12)) >= 0) {
                            ProFlag = false;
                        }
                        // e.秋冬品不剔除；
                        if (AutumnWinterFlag)
                            ProFlag = false;
                        // f.新品不剔除
                        if (NewFlag)
                            ProFlag = false;
                        // g.节日品不剔除
                        if ("圣诞装饰".equals(aos_category2)) {
                            ProFlag = false;
                        }
                        // h.7DD不剔除
                        if (actFlag) {
                            ProFlag = false;
                        }
                        
                        // k.周135 正常剔除 销售加回不剔除
                        if (week != Calendar.TUESDAY && week != Calendar.THURSDAY && "true".equals(saleAdd)) {
                            ProFlag = false;
                        }
                        
                        // k.周24剔除135部分
                        if (week == Calendar.TUESDAY && week == Calendar.THURSDAY) {
                            ProFlag = false;
                        }*/

                        // 2023.08.07 不做低毛利剔除
                        boolean proFlag = false;

                        // 特殊广告不进低毛利剔除
                        if (proFlag && !"true".equals(saleAdd)) {
                            insertData(aosEntryentityS, insertMap, "PRO");
                            log.add(aosItemnumer + "定价低毛利自动剔除");
                            continue;
                        }
                    } else {
                        // 必推货号 判断预断货
                        if (FndGlobal.IsNull(aosFestivalseting) && !"圣诞装饰".equals(aosCategory2)) {
                            // (海外在库+在途数量)/7日均销量)<30 或 满足销售预断货逻辑 则为营销预断货逻辑 且 不能为季节品季末 且可售天数小于45
                            int itemavadays = mapItemavadays.getOrDefault(pOuCode + "~" + aosItemnumer, 0);
                            boolean preSaleOut = (((availableDays < 30)
                                || ((MktComUtil.isPreSaleOut(orgIdLong, itemIdLong, (int)itemIntransqty, aosShpDay,
                                    aosFreightDay, aosClearDay, availableDays)) && itemavadays < 45)));
                            if (preSaleOut) {
                                insertData(aosEntryentityS, insertMap, "OFFSALE");
                                continue;
                            }
                        }
                    }
                }

                // 赋值数据
                DynamicObject aosEntryentity = aosEntryentityS.addNew();
                aosEntryentity.set("aos_itemid", itemIdLong);
                aosEntryentity.set("aos_productno", aosProductno + "-AUTO");
                aosEntryentity.set("aos_itemnumer", aosItemnumer);
                aosEntryentity.set("aos_groupstatus", "AVAILABLE");
                aosEntryentity.set("aos_serialstatus", "UNAVAILABLE");
                aosEntryentity.set("aos_shopsku", aosShopsku);
                aosEntryentity.set("aos_seasonseting", aosSeason);
                aosEntryentity.set("aos_contryentrystatus", aosItemstatus);
                aosEntryentity.set("aos_cn_name", aosCnName);
                aosEntryentity.set("aos_sal_group", salOrg);
                aosEntryentity.set("aos_makedate", aosMakeDate);
                aosEntryentity.set("aos_groupdate", aosGroupDate);
                aosEntryentity.set("aos_basestitem", bseStItem);
                aosEntryentity.set("aos_category1", aosCategory1);
                aosEntryentity.set("aos_category2", aosCategory2);
                aosEntryentity.set("aos_category3", aosCategory3);
                aosEntryentity.set("aos_profit", profitrate);
                aosEntryentity.set("aos_roi14days", roi14Days);
                aosEntryentity.set("aos_lastspend", lastSpend);
                aosEntryentity.set("aos_age", aosAge);
                aosEntryentity.set("aos_avadays", availableDays);
                aosEntryentity.set("aos_special", saleAdd);
                aosEntryentity.set("aos_overseaqty", itemOverseaqty);
                aosEntryentity.set("aos_online", onlineDate.get(aosItemnumer));
                aosEntryentity.set("aos_is_saleout", aosIsSaleout);
                aosEntryentity.set("aos_firstindate", aosFirstindate);
                aosEntryentity.set("aos_rpt_roi", aosRptRoi);

                if (FndGlobal.IsNotNull(onlineDate.get(aosItemnumer))
                    && MktComUtil.getBetweenDays(todayD, onlineDate.get(aosItemnumer)) < 14) {
                    aosEntryentity.set("aos_offline", "Y");
                } else {
                    aosEntryentity.set("aos_offline", "N");
                }

                if (aosActivity != null) {
                    aosEntryentity.set("aos_activity", aosActivity);
                }
                productNoMap.put(aosProductno + "-AUTO", auto + 1);
            }

            // 新品
            Map<String, BigDecimal> newSerialMap = new HashMap<>(16);
            for (DynamicObject aosEntryentity : aosEntryentityS) {
                // 新系列预算计算 = MAX(广告系列中SKU预测营收*AM营收占比*AM付费营收占比/国别标准ROI，2*广告系列中SKU个数)
                long orgIdLong = (long)orgId;
                long itemIdLong = aosEntryentity.getLong("aos_itemid");
                Map<String, Object> ppcInfoMap = ppcInfo.get(orgIdLong + "~" + itemIdLong);
                if (ppcInfoMap != null) {
                    continue; // 非新品 直接跳过 只需要合计新品
                }
                String aosProductno = aosEntryentity.getString("aos_productno");
                Map<String, Object> summary = salSummary.get(orgIdLong + "~" + itemIdLong);
                if (summary != null) {
                    // 广告系列中SKU预测营收 广告系列中可用SKU个数
                    BigDecimal aosSales = ((BigDecimal)salSummary.get(orgIdLong + "~" + itemIdLong).get("aos_sales"))
                        .divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
                    int aosScheQty = productNoMap.get(aosProductno);
                    BigDecimal budget1 = aosSales.multiply(popOrgAmSaleRate).multiply(popOrgAmAffRate)
                        .divide(popOrgRoist, 2, RoundingMode.HALF_UP);
                    BigDecimal budget2 = BigDecimal.valueOf(aosScheQty * 2);
                    BigDecimal newBudget = max(budget1, budget2);
                    if (newSerialMap.get(aosProductno + "-AUTO") != null) {
                        newSerialMap.put(aosProductno + "-AUTO",
                            newBudget.add(newSerialMap.get(aosProductno + "-AUTO")));
                    } else {
                        newSerialMap.put(aosProductno, newBudget);
                    }
                }
            }

            // 循环计算 预算
            for (DynamicObject aosEntryentity : aosEntryentityS) {
                String aosGroupstatus = aosEntryentity.getString("aos_groupstatus");
                if (!"AVAILABLE".equals(aosGroupstatus)) {
                    continue;// 剔除不计算出价与预算
                }
                long orgIdLong = (long)orgId;
                long itemIdLong = aosEntryentity.getLong("aos_itemid");
                String aosProductno = aosEntryentity.getString("aos_productno");
                String aosItemnumer = aosEntryentity.getString("aos_itemnumer");
                BigDecimal aosRptRoi = aosEntryentity.getBigDecimal("aos_rpt_roi");
                // =====是否新系列新组判断=====
                boolean isNewGroupFlag = false;
                Map<String, Object> ppcInfoMap = ppcInfo.get(orgIdLong + "~" + itemIdLong);
                Map<String, Object> ppcInfoSerialMap = ppcInfoSerial.get(orgIdLong + "~" + aosProductno);
                Object aosMakeDate = null;
                Object aosGroupDate = null;
                if (ppcInfoMap != null) {
                    aosGroupDate = ppcInfoMap.get("aos_groupdate");
                }
                if (aosGroupDate == null) {
                    aosGroupDate = todayD;
                    isNewGroupFlag = true;
                }
                if (ppcInfoSerialMap != null) {
                    aosMakeDate = ppcInfoSerialMap.getOrDefault("aos_makedate", todayD);
                }
                if (aosMakeDate == null) {
                    aosMakeDate = todayD;
                }
                // =====Start 出价&预算 参数=====
                // 原为7日
                Map<String, Object> skuRptMap = skuRpt.get(orgIdLong + "~" + itemIdLong);
                // 原为3日
                Map<String, Object> skuRptMap3 = skuRpt3.get(orgIdLong + "~" + itemIdLong);
                // 默认昨日数据
                Map<String, Object> ppcYesterMap = ppcYester.get(orgIdLong + "~" + itemIdLong);
                Map<String, Object> ppcYesterSerialMap = ppcYesterSerial.get(orgIdLong + "~" + aosProductno);
                // 7天ROI
                BigDecimal roi7Days = BigDecimal.ZERO;
                BigDecimal aosClicks = BigDecimal.ZERO;
                if (skuRptMap != null && ((BigDecimal)skuRptMap.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                    aosClicks = (BigDecimal)skuRptMap.get("aos_clicks");
                    roi7Days = ((BigDecimal)skuRptMap.get("aos_total_sales"))
                        .divide((BigDecimal)skuRptMap.get("aos_spend"), 2, RoundingMode.HALF_UP);
                }
                // 3天ROI
                BigDecimal roi3Days = BigDecimal.ZERO;
                if (skuRptMap3 != null && ((BigDecimal)skuRptMap3.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                    roi3Days = ((BigDecimal)skuRptMap3.get("aos_total_sales"))
                        .divide((BigDecimal)skuRptMap3.get("aos_spend"), 2, RoundingMode.HALF_UP);
                }
                // 花出率 =报告中花费/预算
                BigDecimal costRate = BigDecimal.ZERO;
                // 系列花出率 = 前一天花出/预算 Sku报告1日数据
                BigDecimal lastSpendObj = skuRptDetailSerial.get(orgId + "~" + aosProductno);
                BigDecimal lastSpend = BigDecimal.ZERO;
                if (!Cux_Common_Utl.IsNull(lastSpendObj)) {
                    lastSpend = lastSpendObj;
                }
                if (ppcYesterSerialMap != null) {
                    // 昨日预算
                    BigDecimal lastBudget = (BigDecimal)ppcYesterSerialMap.get("aos_budget");
                    if (lastBudget.compareTo(BigDecimal.ZERO) != 0) {
                        costRate = lastSpend.divide(lastBudget, 2, RoundingMode.HALF_UP);
                    }
                }
                if (getBetweenDays(todayD, (Date)aosMakeDate) <= 3)
                // 全新系列3天内花出率最低为1
                {
                    costRate = max(costRate, BigDecimal.valueOf(1));
                }
                // =====计算出价=====
                BigDecimal aosBid;// 出价
                Object aosLastpricedate = todayD;
                Object aosLastbid = BigDecimal.ZERO;
                if (ppcYesterMap != null) {
                    // 最近出价调整日期 初始化默认为昨日日期 后面再重新赋值
                    aosLastpricedate = ppcYesterMap.get("aos_lastpricedate");
                    aosEntryentity.set("aos_lastpricedate", aosLastpricedate);
                    // 昨日出价
                    aosLastbid = ppcYesterMap.get("aos_bid");
                    if (aosLastpricedate == null) {
                        aosLastpricedate = todayD;
                    }
                }
                Map<String, Object> skuRpt3AvgMap = skuRpt3Avg.get(orgId + "~" + itemIdLong);
                BigDecimal impress3Avg = BigDecimal.ZERO;
                if (skuRpt3AvgMap != null) {
                    impress3Avg = (BigDecimal)skuRpt3AvgMap.get("aos_impressions");
                }

                // 最高出价 = MIN(市场价，最高标准)
                BigDecimal highValue;
                // 建议价 报告中获取
                BigDecimal adviceValue = BigDecimal.ZERO;
                // 市场最高价 报告中获取
                BigDecimal maxValue = BigDecimal.ZERO;
                // 市场价 公式计算
                BigDecimal marketValue = BigDecimal.ZERO;
                // =====计算市场价=====
                Map<String, Object> adPriceMap = adPrice.get(orgIdLong + "~" + aosItemnumer);
                if (adPriceMap != null) {
                    adviceValue = (BigDecimal)adPriceMap.get("aos_bid_suggest");
                    maxValue = (BigDecimal)adPriceMap.get("aos_bid_rangeend");
                }
                // 如果建议价为空 或为0 取国别cpc
                if (adPriceMap == null || adviceValue.compareTo(BigDecimal.ZERO) == 0) {
                    String aosCategory1 = aosEntryentity.getString("aos_category1");
                    String aosCategory2 = aosEntryentity.getString("aos_category2");
                    String aosCategory3 = aosEntryentity.getString("aos_category3");
                    adviceValue = orgCpcMap.getOrDefault(aosCategory1 + aosCategory2 + aosCategory3, BigDecimal.ZERO);
                }
                // 定价 Amazon每日价格
                Object fixValue = dailyPrice.get(orgIdLong + "~" + itemIdLong);
                // 最高标准 根据国别定价筛选
                BigDecimal highStandard = getHighStandard(fixValue, high1, high2, high3);
                // 最高出价 = MIN(市场价，最高标准)
                highValue = BigDecimal.ZERO;
                int avaDays = aosEntryentity.getInt("aos_avadays");
                // 根据可售库存天数计算市场价
                if (avaDays < 90) {
                    // 可售库存天数 < 90 市场价= min(建议价*0.7,最高标准)
                    BigDecimal value1 = adviceValue.multiply(BigDecimal.valueOf(0.7));
                    marketValue = min(value1, highStandard);
                } else if (avaDays >= 90 && avaDays <= 120) {
                    // 可售库存天数 90-120 市场价=建议价
                    marketValue = adviceValue;
                } else if (avaDays > 120 && avaDays <= 180) {
                    // 可售库存天数120-180 市场价=max(市场最高价*0.7,建议价)
                    BigDecimal value1 = maxValue.multiply(BigDecimal.valueOf(0.7));
                    marketValue = max(value1, adviceValue);
                } else if (avaDays > 180) {
                    // 可售库存天数>180 市场价=市场最高价
                    marketValue = maxValue;
                }
                // =====End计算市场价=====

                if (isNewGroupFlag) {
                    // 1.首次出价 新组
                    aosBid = popOrgFirstBid;
                    // 判断为首次时需要调整出价日期
                    aosLastpricedate = todayD;
                    aosEntryentity.set("aos_lastpricedate", aosLastpricedate);
                    log.add(aosItemnumer + "出价1 =" + aosBid);
                } else if (ppcYesterMap != null
                    && !"AVAILABLE".equals(ppcYesterMap.get("aos_groupstatus").toString())) {
                    // 1.1.首次出价/2 前一次为空 或 前一次不可用
                    aosBid = popOrgFirstBid.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                    // 判断为首次时需要调整出价日期
                    aosLastpricedate = todayD;
                    aosEntryentity.set("aos_lastpricedate", aosLastpricedate);
                    log.add(aosItemnumer + "出价1.1 =" + aosBid);
                } else if ((getBetweenDays(todayD, (Date)aosLastpricedate) >= 3)
                    && (getBetweenDays(todayD, (Date)aosGroupDate) > 7)) {
                    // 3.出价3天内不调整 今天与最近出价日期做判断 且 不为 首次建组日后7天内
                    BigDecimal lastBid = (BigDecimal)aosLastbid;
                    Map<String, Object> getAdjustRateMap = new HashMap<>(16);
                    getAdjustRateMap.put("Roi3Days", roi3Days);
                    getAdjustRateMap.put("Roi7Days", roi7Days);
                    getAdjustRateMap.put("ExpouseRate", impress3Avg);
                    getAdjustRateMap.put("AvaDays", avaDays);
                    getAdjustRateMap.put("CostRate", costRate);
                    getAdjustRateMap.put("PopOrgRoist", popOrgRoist);
                    getAdjustRateMap.put("WORST", worst);
                    getAdjustRateMap.put("WORRY", worry);
                    getAdjustRateMap.put("STANDARD", standard);
                    getAdjustRateMap.put("WELL", well);
                    getAdjustRateMap.put("EXCELLENT", excellent);
                    getAdjustRateMap.put("EXPOSURE", exposure);
                    getAdjustRateMap.put("aos_rpt_roi", aosRptRoi);
                    getAdjustRateMap.put("exRateWellSt", exRateWellSt);
                    getAdjustRateMap.put("lowClick", lowClick);
                    getAdjustRateMap.put("aos_clicks", aosClicks);

                    Map<String, Object> adjustRateMap =
                        getAdjustRate(getAdjustRateMap, aosMktBsadjrateS, log, aosItemnumer);
                    // 调整幅度 出价调整幅度参数表中获取
                    BigDecimal adjustRate = (BigDecimal)adjustRateMap.get("AdjustRate");
                    int aosLevel = (int)adjustRateMap.get("aos_level");

                    log.add(aosItemnumer + "调整幅度 =" + adjustRate);
                    log.add(aosItemnumer + "优先级 =" + aosLevel);
                    log.add(aosItemnumer + "上次出价 =" + lastBid);
                    // 计算出价逻辑 本次出价=上次出价*(1+调整幅度) 如果优先级为1则本次出价=MAX(/*建议价*0.7*/国别首次出价均价,上次出价*(1+调整幅度))
                    if (aosLevel != 1) {
                        aosBid = lastBid.multiply(adjustRate.add(BigDecimal.valueOf(1)));
                    } else {
                        aosBid = max(popOrgFirstBid.multiply(BigDecimal.valueOf(0.5)),
                            lastBid.multiply(adjustRate.add(BigDecimal.valueOf(1.15))));
                    }
                    log.add(aosItemnumer + "出价4 =" + aosBid);
                } else {
                    // 4.其他情况下不调整出价与出价日期 为昨日信息 本次出价=上次出价
                    aosBid = (BigDecimal)aosLastbid;
                    log.add(aosItemnumer + "出价5 =" + aosBid);
                }
                // =====End计算出价=====
                log.add(aosItemnumer + "花出率 =" + costRate);
                log.add(aosItemnumer + "曝光 =" + impress3Avg);
                log.add(aosItemnumer + "7日ROI =" + roi7Days);
                log.add(aosItemnumer + "计算出价与最高价取最小值 " + aosBid + "&" + highValue);

                // 2.1 计算最高出价
                if (avaDays < 120) {
                    highValue = min(max(adviceValue, aosBid), highStandard);
                } else {
                    highValue = min(max(maxValue, aosBid), highStandard);
                }
                // 计算出价不能超过最高出价 第一次计算出价
                aosBid = min(aosBid, highValue);
                // 新品第二次出价 移动至最高出价计算完后判断
                if (getBetweenDays(todayD, (Date)aosGroupDate) <= 3) {
                    // 2.今天为第二次出价
                    // 2.2 根据市场价计算出价 = min(市场价*1.1，最高出价*0.5)
                    BigDecimal value1 = marketValue.multiply(BigDecimal.valueOf(1.1));
                    BigDecimal value2 = highValue.multiply(BigDecimal.valueOf(0.5));
                    aosBid = min(value1, value2);
                    // 需要调整出价日期
                    aosLastpricedate = todayD;
                    aosEntryentity.set("aos_lastpricedate", aosLastpricedate);
                    log.add(aosItemnumer + "出价3 =" + aosBid);
                }
                // 计算出价不能超过最高出价
                aosBid = min(aosBid, highValue);
                // 如果此时出价为0则默认为初始价
                if (aosBid.compareTo(BigDecimal.ZERO) == 0) {
                    aosBid = popOrgFirstBid.multiply(BigDecimal.valueOf(0.5));
                }
                // 出价保留两位
                aosBid = aosBid.setScale(2, RoundingMode.HALF_UP);
                aosEntryentity.set("aos_lastbid", aosLastbid);
                aosEntryentity.set("aos_bid", aosBid);
                try {
                    if (aosBid.compareTo((BigDecimal)aosLastbid) != 0) {
                        aosEntryentity.set("aos_lastpricedate", new Date());
                    }
                } catch (Exception ignored) {
                }

                aosEntryentity.set("aos_bid_ori", aosBid);
                // 7日ROI
                aosEntryentity.set("aos_roi7days", roi7Days);
                // 市场价
                aosEntryentity.set("aos_marketprice", marketValue);
                // 最高价
                aosEntryentity.set("aos_highvalue", highValue);
                // 定价
                aosEntryentity.set("aos_fixvalue", fixValue);
                if (productNoBidMap.get(aosProductno) != null) {
                    aosBid = aosBid.add(productNoBidMap.get(aosProductno));
                }
                productNoBidMap.put(aosProductno, aosBid);
            }
            // =====计算 系列预算=====
            for (DynamicObject aosEntryentity : aosEntryentityS) {
                String aosGroupstatus = aosEntryentity.getString("aos_groupstatus");
                if (!"AVAILABLE".equals(aosGroupstatus)) {
                    continue;// 剔除不计算出价与预算
                }
                BigDecimal aosBudget;// 预算 分新系列
                long orgIdLong = (long)orgId;
                String aosProductno = aosEntryentity.getString("aos_productno");
                String aosItemnumer = aosEntryentity.getString("aos_itemnumer");
                // =====是否新系列新组判断=====
                boolean isNewSerialFlag = false;
                Map<String, Object> ppcInfoSerialMap = ppcInfoSerial.get(orgIdLong + "~" + aosProductno);
                Object aosMakeDate = null;
                if (ppcInfoSerialMap != null) {
                    aosMakeDate = ppcInfoSerialMap.get("aos_makedate");
                }
                if (aosMakeDate == null) {
                    aosMakeDate = todayD;
                    isNewSerialFlag = true;
                }
                BigDecimal roi7DaysSerial = BigDecimal.ZERO;
                Map<String, Object> ppcYesterSerialMap = ppcYesterSerial.get(orgId + "~" + aosProductno);
                // Sku报告
                Map<String, Object> skuRptMapSerial = skuRptSerial7.get(orgIdLong + "~" + aosProductno);
                if (skuRptMapSerial != null
                    && ((BigDecimal)skuRptMapSerial.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                    roi7DaysSerial = ((BigDecimal)skuRptMapSerial.get("aos_total_sales"))
                        .divide((BigDecimal)skuRptMapSerial.get("aos_spend"), 2, RoundingMode.HALF_UP);
                }
                BigDecimal roiLast1Days = BigDecimal.ZERO;
                BigDecimal roiLast2Days = BigDecimal.ZERO;
                BigDecimal roiLast3Days = BigDecimal.ZERO;
                Map<String, Map<String, Object>> skuRpt3SerialMap = skuRpt3Serial.get(orgId + "~" + aosProductno);
                if (skuRpt3SerialMap != null) {
                    Map<String, Object> roiLast1DaysMap = skuRpt3SerialMap.get(yester);
                    Map<String, Object> roiLast2DaysMap = skuRpt3SerialMap.get(yester2);
                    Map<String, Object> roiLast3DaysMap = skuRpt3SerialMap.get(yester3);
                    if (roiLast1DaysMap != null
                        && ((BigDecimal)roiLast1DaysMap.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                        roiLast1Days = ((BigDecimal)roiLast1DaysMap.get("aos_total_sales"))
                            .divide((BigDecimal)roiLast1DaysMap.get("aos_spend"), 2, RoundingMode.HALF_UP);
                    }
                    if (roiLast2DaysMap != null
                        && ((BigDecimal)roiLast2DaysMap.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                        roiLast2Days = ((BigDecimal)roiLast2DaysMap.get("aos_total_sales"))
                            .divide((BigDecimal)roiLast2DaysMap.get("aos_spend"), 2, RoundingMode.HALF_UP);
                    }
                    if (roiLast3DaysMap != null
                        && ((BigDecimal)roiLast3DaysMap.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                        roiLast3Days = ((BigDecimal)roiLast3DaysMap.get("aos_total_sales"))
                            .divide((BigDecimal)roiLast3DaysMap.get("aos_spend"), 2, RoundingMode.HALF_UP);
                    }
                }
                // 花出率 =报告中花费/预算
                BigDecimal costRate = BigDecimal.ZERO;
                // 系列花出率 = 前一天花出/预算
                BigDecimal lastSpendObj = skuRptDetailSerial.get(orgId + "~" + aosProductno);
                BigDecimal lastSpend = BigDecimal.ZERO;
                if (!Cux_Common_Utl.IsNull(lastSpendObj)) {
                    lastSpend = lastSpendObj;
                }
                if (ppcYesterSerialMap != null) {
                    BigDecimal lastBudget = (BigDecimal)ppcYesterSerialMap.get("aos_budget");
                    log.add(aosItemnumer + "预算3 昨日花出 =" + lastSpend);
                    log.add(aosItemnumer + "预算3 昨日预算 =" + lastBudget);
                    if (lastBudget.compareTo(BigDecimal.ZERO) != 0) {
                        costRate = lastSpend.divide(lastBudget, 2, RoundingMode.HALF_UP);
                    }
                }
                if (getBetweenDays(todayD, (Date)aosMakeDate) <= 3)
                // 全新系列3天内花出率最低为1
                {
                    costRate = max(costRate, BigDecimal.valueOf(1));
                }

                boolean threeDayFlag = ((roi7DaysSerial.compareTo(popOrgRoist) < 0)
                    && (((roiLast1Days.compareTo(popOrgRoist) >= 0) && (roiLast2Days.compareTo(popOrgRoist) >= 0))
                        || ((roiLast1Days.compareTo(popOrgRoist) >= 0) && (roiLast3Days.compareTo(popOrgRoist) >= 0))
                        || ((roiLast2Days.compareTo(popOrgRoist) >= 0) && (roiLast3Days.compareTo(popOrgRoist) >= 0))));

                if (isNewSerialFlag) {
                    // a.新系列 预算=新系列预算
                    BigDecimal newBudget = BigDecimal.ZERO;
                    // 新系列预算计算 = MAX(广告系列中SKU预测营收*AM营收占比*AM付费营收占比/国别标准ROI，2*广告系列中SKU个数)
                    Map<String, Object> summary = salSummaryMap.get(orgIdLong + "~" + aosProductno);
                    if (summary != null) {
                        // 广告系列中SKU预测营收 广告系列中可用SKU个数
                        BigDecimal aosSales = ((BigDecimal)summary.get("aos_sales")).divide(BigDecimal.valueOf(30), 2,
                            RoundingMode.HALF_UP);
                        int aosScheQty = productNoMap.get(aosProductno);
                        BigDecimal budget1 = aosSales.multiply(popOrgAmSaleRate).multiply(popOrgAmAffRate)
                            .divide(popOrgRoist, 2, RoundingMode.HALF_UP);
                        BigDecimal budget2 = BigDecimal.valueOf(aosScheQty * 2);
                        newBudget = max(budget1, budget2);
                        log.add(aosItemnumer + "新系列budget1 =" + budget1);
                        log.add(aosItemnumer + "新系列budget2 =" + budget2);
                        log.add(aosItemnumer + "新系列NewBudget =" + newBudget);
                        log.add(aosItemnumer + "新系列aos_sales =" + aosSales);
                        log.add(aosItemnumer + "新系列PopOrgAMSaleRate =" + popOrgAmSaleRate);
                        log.add(aosItemnumer + "新系列PopOrgAMAffRate =" + popOrgAmAffRate);
                        log.add(aosItemnumer + "新系列PopOrgRoist =" + popOrgRoist);
                        log.add(aosItemnumer + "新系列aos_sche_qty =" + aosScheQty);
                    }
                    aosBudget = newBudget;
                    log.add(aosItemnumer + "预算1 =" + aosBudget);
                } else if (threeDayFlag) {
                    // b.当7天ROI＜国别标准时，前3天ROI中任意2天ROI≥国别标准ROI，则预算维持上次设置金额
                    BigDecimal lastBudget = BigDecimal.ZERO;
                    if (ppcYesterSerialMap != null) {
                        // 上次设置金额
                        lastBudget = (BigDecimal)ppcYesterSerialMap.get("aos_budget");
                    }
                    aosBudget = lastBudget;
                    log.add(aosItemnumer + "预算2 =" + aosBudget);
                } else {
                    // c.计算预算
                    BigDecimal lastBudget = BigDecimal.ZERO;
                    if (ppcYesterSerialMap != null) {
                        // 昨日设置金额
                        lastBudget = (BigDecimal)ppcYesterSerialMap.get("aos_budget");
                    }
                    Map<String, Object> getBudgetRateMap = new HashMap<>(16);
                    String roiType = getRoiType(popOrgRoist, worst, worry, standard, well, excellent, roi7DaysSerial);
                    // 花出率
                    getBudgetRateMap.put("CostRate", costRate);
                    getBudgetRateMap.put("RoiType", roiType);
                    // 花出率获取对应预算调整系数
                    BigDecimal budgetRate = getBudgetRate(getBudgetRateMap, aosMktBsadjparaS);
                    BigDecimal newSerial = BigDecimal.ZERO;
                    BigDecimal aosBid = BigDecimal.ZERO;
                    if (productNoBidMap.get(aosProductno) != null) {
                        aosBid = productNoBidMap.get(aosProductno);
                    }
                    if (newSerialMap.get(aosProductno) != null) {
                        newSerial = newSerialMap.get(aosProductno);
                    }
                    BigDecimal budget1 = lastBudget.multiply(budgetRate).add(newSerial);
                    BigDecimal budget2 = aosBid.multiply(BigDecimal.valueOf(20));
                    aosBudget = max(budget1, budget2);
                    log.add(aosItemnumer + "预算3 =" + aosBudget);
                    log.add(aosItemnumer + "预算3 花出率 =" + costRate);
                    log.add(aosItemnumer + "预算3 7日Roi =" + roi7DaysSerial);
                    log.add(aosItemnumer + "预算3 上次预算 =" + lastBudget);
                    log.add(aosItemnumer + "预算3 花出率对应系数 =" + budgetRate);
                    log.add(aosItemnumer + "预算3 新系列 =" + newSerial);
                    log.add(aosItemnumer + "预算3 aos_bid =" + aosBid);
                    log.add(aosItemnumer + "预算3 budget1 =" + budget1);
                    log.add(aosItemnumer + "预算3 budget2 =" + budget2);
                }
                aosEntryentity.set("aos_budget", max(aosBudget, BigDecimal.valueOf(2)));
                aosEntryentity.set("aos_budget_ori", max(aosBudget, BigDecimal.valueOf(2)));
                // =====End计算预算=====
            }
            aosMktBsadjrateS.close();// 关闭Dataset
            aosMktBsadjparaS.close();
            // 保存正式表
            OperationResult operationrst = SaveServiceHelper.saveOperate("aos_mkt_popular_ppc",
                new DynamicObject[] {aosMktPopularPpc}, OperateOption.create());
            Object fid = operationrst.getSuccessPkIds().get(0);

            // 重新剔除后 对于置顶位置出价
            for (DynamicObject aosEntryentity : aosEntryentityS) {
                String aosGroupstatus = aosEntryentity.getString("aos_groupstatus");
                if (!"AVAILABLE".equals(aosGroupstatus)) {
                    aosEntryentity.set("aos_budget", 0);
                    aosEntryentity.set("aos_bid", 0);
                    aosEntryentity.set("aos_budget_ori", 0);
                    aosEntryentity.set("aos_bid_ori", 0);
                    continue;
                }
                long orgIdLong = (long)orgId;
                long itemIdLong = aosEntryentity.getLong("aos_itemid");
                String aosProductno = aosEntryentity.getString("aos_productno");
                // 出价逻辑结束后 对于置顶位置出价
                BigDecimal aosTopprice = BigDecimal.ZERO;
                // Sku报告
                Map<String, Object> skuRptMap14 = skuRpt14.get(orgIdLong + "~" + itemIdLong);
                // 系列SKU平均店铺14天转化率
                BigDecimal roi14Days = BigDecimal.ZERO;
                if (skuRptMap14 != null && ((BigDecimal)skuRptMap14.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                    roi14Days = ((BigDecimal)skuRptMap14.get("aos_total_sales"))
                        .divide((BigDecimal)skuRptMap14.get("aos_spend"), 2, RoundingMode.HALF_UP);
                }
                // 广告系列中可用SKU个数 用于计算平均
                int aosScheQty = productNoMap.get(aosProductno);
                BigDecimal serialAvaFix =
                    getSerialAvaFix(aosProductno, fid).divide(BigDecimal.valueOf(aosScheQty), 2, RoundingMode.HALF_UP);
                log.add("置顶位置出价 " + "Roi14Days =" + roi14Days);
                log.add("置顶位置出价 " + "RoiStandard =" + roiStandard);
                log.add("置顶位置出价 " + "SerialAvaFix =" + serialAvaFix);
                log.add("置顶位置出价 " + "StandardFix =" + standardFix);
                log.add("置顶位置出价 " + "aos_sche_qty =" + aosScheQty);

                if (roi14Days.compareTo(roiStandard) > 0 && serialAvaFix.compareTo(standardFix) > 0) {
                    aosTopprice = BigDecimal.valueOf(0.2);
                }

                Map<String, Object> skuRpt3AvgMap = skuRpt3Avg.get(orgId + "~" + itemIdLong);
                // 曝光3天平均
                BigDecimal impress3Avg = BigDecimal.ZERO;
                if (skuRpt3AvgMap != null) {
                    impress3Avg = (BigDecimal)skuRpt3AvgMap.get("aos_impressions");
                }

                // 曝光点击率7天
                BigDecimal aosExprate = BigDecimal.ZERO;
                BigDecimal aosAvgexp;
                BigDecimal aosClicks = BigDecimal.ZERO;
                // 7日转化率
                BigDecimal aosRptRoi = BigDecimal.ZERO;
                // Sku报告
                Map<String, Object> skuRptSerialMap = skuRptSerial14.get(orgIdLong + "~" + aosProductno);
                if (skuRptSerialMap != null
                    && ((BigDecimal)skuRptSerialMap.get("aos_clicks")).compareTo(BigDecimal.ZERO) != 0) {
                    aosClicks = (BigDecimal)skuRptSerialMap.get("aos_clicks");
                    aosRptRoi =
                        ((BigDecimal)skuRptSerialMap.get("aos_total_order")).divide(aosClicks, 3, RoundingMode.HALF_UP);
                }
                // 7天订单转化率
                Map<String, Object> skuRptMap = skuRpt.get(orgIdLong + "~" + itemIdLong);
                if (skuRptMap != null) {
                    aosAvgexp = (BigDecimal)skuRptMap.get("aos_impressions");
                    aosClicks = (BigDecimal)skuRptMap.get("aos_clicks");
                    if (aosAvgexp.compareTo(BigDecimal.ZERO) != 0) {
                        aosExprate = aosClicks.divide(aosAvgexp, 6, RoundingMode.HALF_UP);
                    }
                }

                if (impress3Avg.compareTo(exposure.multiply(BigDecimal.valueOf(3))) > 0
                    && aosExprate.compareTo(BigDecimal.valueOf(0.002)) > 0) {
                    aosTopprice = BigDecimal.valueOf(0.2);
                }

                if (aosRptRoi.compareTo(exRateGoodSt) > 0 && aosClicks.compareTo(BigDecimal.valueOf(50)) > 0) {
                    aosTopprice = BigDecimal.valueOf(0.2);
                }

                aosEntryentity.set("aos_topprice", aosTopprice);
                log.add("置顶位置出价 " + "aos_topprice =" + aosTopprice);
            }
            Map<String, BigDecimal> availableBudgetMap = new HashMap<>(16);
            Map<String, BigDecimal> preDayExposureMap = getPreDayExposure(orgId.toString());
            for (DynamicObject aosEntryentity : aosEntryentityS) {
                // 获取前一日曝光
                String aosItemnumer = aosEntryentity.getString("aos_itemnumer");
                BigDecimal preDayExposure = preDayExposureMap.getOrDefault(aosItemnumer, BigDecimal.ZERO);
                aosEntryentity.set("aos_predayexposure", preDayExposure);
                // 系列
                String aosProductno = aosEntryentity.getString("aos_productno");
                // 组状态
                String aosGroupstatus = aosEntryentity.getString("aos_groupstatus");
                BigDecimal aosBudget = aosEntryentity.getBigDecimal("aos_budget");
                if ("AVAILABLE".equals(aosGroupstatus)) {
                    availableBudgetMap.put(aosProductno, aosBudget);
                }
            }
            // 删除日期参数表
            DeleteServiceHelper.delete("aos_mkt_sp_date", new QFilter("aos_orgid", "=", orgId).toArray());
            DynamicObject aosMktSpDate = BusinessDataServiceHelper.newDynamicObject("aos_mkt_sp_date");
            aosMktSpDate.set("aos_orgid", orgId);
            aosMktSpDate.set("aos_date", todayD);
            DynamicObjectCollection aosMktSpDateS = aosMktSpDate.getDynamicObjectCollection("aos_entryentity");
            // 循环设置 最终系列状态 与不可用系列预算
            for (DynamicObject aosEntryentity : aosEntryentityS) {
                String aosProductno = aosEntryentity.getString("aos_productno");
                String aosItemnumer = aosEntryentity.getString("aos_itemnumer");
                DynamicObject spDateLine = aosMktSpDateS.addNew();
                if (availableBudgetMap.containsKey(aosProductno)) {
                    aosEntryentity.set("aos_serialstatus", "AVAILABLE");
                    aosEntryentity.set("aos_budget", availableBudgetMap.get(aosProductno));
                    aosEntryentity.set("aos_budget_ori", availableBudgetMap.get(aosProductno));
                    if (!lastAvaSet.contains(aosItemnumer)) {
                        spDateLine.set("aos_itemstr", aosItemnumer);
                        spDateLine.set("aos_online", todayD);
                    } else {
                        spDateLine.set("aos_itemstr", aosItemnumer);
                        spDateLine.set("aos_online", aosEntryentity.get("aos_online"));
                    }
                } else {
                    aosEntryentity.set("aos_budget", 1);
                    aosEntryentity.set("aos_budget_ori", 1);
                    spDateLine.set("aos_itemstr", aosItemnumer);
                    spDateLine.set("aos_online", aosEntryentity.get("aos_online"));
                }
            }
            // 保存日期参数表
            OperationServiceHelper.executeOperate("save", "aos_mkt_sp_date", new DynamicObject[] {aosMktSpDate},
                OperateOption.create());
            // 保存正式表
            OperationServiceHelper.executeOperate("save", "aos_mkt_popular_ppc", new DynamicObject[] {aosMktPopularPpc},
                OperateOption.create());
            // 保存日志表
            log.finnalSave();
            // 开始生成 出价调整(销售)
            Map<String, Object> adjs = new HashMap<>(16);
            adjs.put("p_ou_code", pOuCode);
            AosMktPopAdjsTask.executerun(adjs);
        } catch (Exception e) {
            String message = e.toString();
            String exceptionStr = SalUtil.getExceptionStr(e);
            String messageStr = message + "\r\n" + exceptionStr;
            LOGGER.error("PPC推广SP初始化失败!" + messageStr, e);
            FndError.sendMMS("PPC推广SP初始化失败!" + messageStr);
        }
    }

    private static Map<String, Integer> genOrgCateClick(Object pOrgId) {
        DynamicObjectCollection list = QueryServiceHelper.query("aos_base_cateclick", "aos_category1,aos_click",
            new QFilter("aos_orgid", QCP.equals, pOrgId).toArray());
        return list.stream().collect(
            Collectors.toMap(obj -> obj.getString("aos_category1"), obj -> obj.getInt("aos_click"), (k1, k2) -> k1));
    }

    public static BigDecimal getExRateLowSt(Object pOuCode, String level) {
        DynamicObject aosMktRatestd = QueryServiceHelper.queryOne("aos_mkt_ratestd", "aos_" + pOuCode,
            new QFilter("aos_project", QCP.equals, level).toArray());
        return aosMktRatestd.getBigDecimal(0);
    }

    private static HashMap<String, String> generateProductInfo() {
        HashMap<String, String> productInfo = new HashMap<>(16);
        QFilter filterAma = new QFilter("aos_platformfid.number", "=", "AMAZON");
        QFilter filterMainshop = new QFilter("aos_shopfid.aos_is_mainshop", "=", true);
        QFilter[] filters = new QFilter[] {filterAma, filterMainshop};
        String selectColumn =
            "aos_orgid.number aos_orgid," + "aos_item_code.number aos_itemid," + "aos_shopsku aos_productid";
        DynamicObjectCollection bdMaterialS = QueryServiceHelper.query("aos_sync_invprice", selectColumn, filters);
        for (DynamicObject bdMaterial : bdMaterialS) {
            productInfo.put(bdMaterial.getString("aos_orgid") + "~" + bdMaterial.getString("aos_itemid"),
                bdMaterial.getString("aos_productid"));
        }
        return productInfo;
    }

    /**
     * SKU推广报告
     */
    public static HashMap<String, String> getRptPro(Object pOrgId) {
        HashMap<String, String> rptPro = new HashMap<>(16);
        DynamicObjectCollection skuRptS = QueryServiceHelper.query("aos_base_skupoprpt",
            "aos_entryentity.aos_ad_sku aos_ad_sku," + "aos_entryentity.aos_cam_name aos_cam_name",
            new QFilter("aos_orgid", QCP.equals, pOrgId).and("aos_entryentity.aos_cam_name", QCP.like, "GJ-%")
                .toArray());
        for (DynamicObject skuRpt : skuRptS) {
            rptPro.put(skuRpt.getString("aos_ad_sku"), skuRpt.getString("aos_cam_name").replace("-AUTO", ""));
        }
        return rptPro;
    }

    /**
     * 平台上架信息
     **/
    private static Set<String> generateOnlineSkuSet(Object pOrgId) {
        DynamicObjectCollection list =
            QueryServiceHelper.query("aos_sync_inv_ctl", "aos_item", new QFilter("aos_ou", QCP.equals, pOrgId)
                .and("createtime", QCP.less_than, FndDate.add_days(new Date(), -2)).toArray());
        return list.stream().map(obj -> obj.getString("aos_item")).collect(Collectors.toSet());
    }

    /**
     * 不按季节属性推广品类
     **/
    private static Set<String> generateDeseaon() {
        DynamicObjectCollection list =
            QueryServiceHelper.query("aos_mkt_deseason", "aos_category1,aos_category2,aos_category3", new QFilter[] {});
        return list.stream().map(obj -> obj.getString("aos_category1") + "~" + obj.getString("aos_category2") + "~"
            + obj.getString("aos_category3")).collect(Collectors.toSet());
    }

    /**
     * 营销必推货号
     * 
     * @param pOrgId 国别
     * @return Set
     */
    private static Set<String> generateMustSet(Object pOrgId) {
        DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_mktitem", "aos_itemid",
            new QFilter("aos_orgid", QCP.equals, pOrgId).toArray());
        return list.stream().map(obj -> obj.getString("aos_itemid")).collect(Collectors.toSet());
    }

    public static HashMap<String, BigDecimal> generateVat(Object pOrgId) {
        HashMap<String, BigDecimal> vatMap = new HashMap<>(16);
        QFilter filterOrg = new QFilter("aos_org.id", "=", pOrgId);
        QFilter[] filters = new QFilter[] {filterOrg};
        DynamicObject aosSalOrgCalP = QueryServiceHelper.queryOne("aos_sal_org_cal_p",
            "aos_org.number aos_orgnum,aos_vat_amount,aos_am_platform", filters);
        vatMap.put("aos_vat_amount", aosSalOrgCalP.getBigDecimal("aos_vat_amount"));
        vatMap.put("aos_am_platform", aosSalOrgCalP.getBigDecimal("aos_am_platform"));
        return vatMap;
    }

    private static BigDecimal getSerialAvaFix(String aosProductno, Object fid) {
        DataSet productno = null;
        BigDecimal serialAvaFix = BigDecimal.ZERO;
        try {
            String algoKey = "aos_mkt_popular_ppc.IsExists." + aosProductno;
            String sql = " select sum(r.fk_aos_fixvalue) from tk_aos_mkt_popular_ppc_r r" + " where 1=1 "
                + " and r.fk_aos_productno = ? " + " and r.fk_aos_groupstatus IN ('AVAILABLE','SAL_ADD') "
                + " and r.fid = ? ";
            Object[] params = {aosProductno, fid};
            productno = DB.queryDataSet(algoKey, DBRoute.of(DB_MKT), sql, params);
            while (productno.hasNext()) {
                Row row = productno.next();
                serialAvaFix = row.getBigDecimal(0);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (productno != null) {
                productno.close();
            }
        }
        return serialAvaFix;
    }

    private static Map<String, Object> getAdjustRate(Map<String, Object> getAdjustRateMap, DataSet aosMktBsadjrateS,
        FndLog aosSyncLog, String aosItemnumer) {
        Map<String, Object> adjustRateMap = new HashMap<>(16);
        BigDecimal adjustRate = BigDecimal.ZERO;
        Object roi3Days = getAdjustRateMap.get("Roi3Days");
        Object roi7Days = getAdjustRateMap.get("Roi7Days");
        Object expouseRate = getAdjustRateMap.get("ExpouseRate");
        Object popOrgRoist = getAdjustRateMap.get("PopOrgRoist");
        Object worst = getAdjustRateMap.get("WORST");
        Object worry = getAdjustRateMap.get("WORRY");
        Object standard = getAdjustRateMap.get("STANDARD");
        Object well = getAdjustRateMap.get("WELL");
        Object excellent = getAdjustRateMap.get("EXCELLENT");
        Object exposure = getAdjustRateMap.get("EXPOSURE");
        Object aosRptRoi = getAdjustRateMap.get("aos_rpt_roi");
        Object exRateWellSt = getAdjustRateMap.get("exRateWellSt");
        Object lowClick = getAdjustRateMap.get("lowClick");
        Object aosClicks = getAdjustRateMap.get("aos_clicks");
        DataSet mktBsadjrateS = aosMktBsadjrateS.copy().orderBy(new String[] {"aos_level"});
        String rule = "";
        int aosLevel = 0;
        while (mktBsadjrateS.hasNext()) {
            Row mktBsadjrate = mktBsadjrateS.next();
            String rule1 = "";
            String rule2 = "";
            String rule5 = "";
            String aosRoitype = mktBsadjrate.getString("aos_roitype");
            if (FndGlobal.IsNotNull(aosRoitype)) {
                BigDecimal value;
                switch (aosRoitype) {
                    case "WORST":
                        value = (BigDecimal)worst;
                        break;
                    case "WORRY":
                        value = (BigDecimal)worry;
                        break;
                    case "STANDARD":
                        value = (BigDecimal)standard;
                        break;
                    case "WELL":
                        value = (BigDecimal)well;
                        break;
                    case "EXCELLENT":
                        value = (BigDecimal)excellent;
                        break;
                    default:
                        value = BigDecimal.ZERO;
                }
                // ROI类型 要转化为对应的值
                BigDecimal roiValue = value.add((BigDecimal)popOrgRoist);
                rule1 = roi7Days + mktBsadjrate.getString("aos_roi") + roiValue;
            }
            String aosExposure = mktBsadjrate.getString("aos_exposure");
            if (FndGlobal.IsNotNull(aosExposure)) {
                if (!"".equals(rule1)) {
                    rule2 = "&&" + expouseRate + aosExposure
                        + mktBsadjrate.getBigDecimal("aos_exposurerate").multiply((BigDecimal)exposure);
                } else {
                    rule2 = expouseRate + aosExposure
                        + mktBsadjrate.getBigDecimal("aos_exposurerate").multiply((BigDecimal)exposure);
                }
            }
            String aosRoitype3 = mktBsadjrate.getString("aos_roitype3");
            if (FndGlobal.IsNotNull(aosRoitype3)) {
                BigDecimal value;
                switch (aosRoitype3) {
                    case "WORST":
                        value = (BigDecimal)worst;
                        break;
                    case "WORRY":
                        value = (BigDecimal)worry;
                        break;
                    case "STANDARD":
                        value = (BigDecimal)standard;
                        break;
                    case "WELL":
                        value = (BigDecimal)well;
                        break;
                    case "EXCELLENT":
                        value = (BigDecimal)excellent;
                        break;
                    default:
                        value = BigDecimal.ZERO;
                }
                // ROI类型 要转化为对应的值
                BigDecimal roiValue = value.add((BigDecimal)popOrgRoist);
                rule5 = "&&" + roi3Days + mktBsadjrate.getString("aos_roi3") + roiValue;
            }
            rule = rule1 + rule2 + rule5;
            if (mktBsadjrate.getInteger("aos_level") == 2) {
                rule = rule + " && " + aosRptRoi + " >= " + exRateWellSt + " && " + aosClicks + " >= " + lowClick;
            }
            boolean condition = getResult(rule);
            aosSyncLog.add(aosItemnumer + "rule =" + rule);
            aosSyncLog.add(aosItemnumer + "rule5 =" + rule5);
            aosSyncLog.add(aosItemnumer + "condition =" + condition);
            if (condition) {
                adjustRate = mktBsadjrate.getBigDecimal("aos_rate");
                aosLevel = mktBsadjrate.getInteger("aos_level");
                break;
            }
        }
        mktBsadjrateS.close();
        adjustRateMap.put("AdjustRate", adjustRate);
        adjustRateMap.put("rule", rule);
        adjustRateMap.put("aos_level", aosLevel);
        return adjustRateMap;
    }

    private static boolean getResult(String rule) {
        boolean result = false;
        try {
            ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
            ScriptEngine scriptEngine = scriptEngineManager.getEngineByName("nashorn");
            result = Boolean.parseBoolean(String.valueOf(scriptEngine.eval(rule)));
        } catch (Exception ignored) {
        }
        return result;
    }

    private static BigDecimal getHighStandard(Object fixValue, BigDecimal high1, BigDecimal high2, BigDecimal high3) {
        int compareValue1 = 200;
        int compareValue2 = 500;
        if (fixValue == null || ((BigDecimal)fixValue).compareTo(BigDecimal.valueOf(compareValue1)) < 0) {
            return high1;
        } else if (((BigDecimal)fixValue).compareTo(BigDecimal.valueOf(compareValue1)) >= 0
            && ((BigDecimal)fixValue).compareTo(BigDecimal.valueOf(compareValue2)) < 0) {
            return high2;
        } else {
            return high3;
        }
    }

    private static BigDecimal getBudgetRate(Map<String, Object> getBudgetRateMap, DataSet aosMktBsadjparaS) {
        BigDecimal budgetRate = BigDecimal.ZERO;
        Object roiType = getBudgetRateMap.get("RoiType");
        BigDecimal costRate = (BigDecimal)getBudgetRateMap.get("CostRate");
        DataSet mktBsadjparaS = aosMktBsadjparaS.copy();
        while (mktBsadjparaS.hasNext()) {
            Row mktBsadjpara = mktBsadjparaS.next();
            String aosRoi = mktBsadjpara.getString("aos_roi");
            BigDecimal aosRatefrom = mktBsadjpara.getBigDecimal("aos_ratefrom");
            BigDecimal aosRateto = mktBsadjpara.getBigDecimal("aos_rateto");
            if (!aosRoi.equals(roiType.toString())) {
                continue;
            }
            boolean condition = costRate.compareTo(aosRatefrom) >= 0 && costRate.compareTo(aosRateto) <= 0;
            if (condition) {
                budgetRate = mktBsadjpara.getBigDecimal("aos_adjratio");
                break;
            }
        }
        mktBsadjparaS.close();
        return budgetRate;
    }

    private static BigDecimal max(BigDecimal value1, BigDecimal value2) {
        if (value1.compareTo(value2) > -1) {
            return value1;
        } else {
            return value2;
        }
    }

    private static BigDecimal min(BigDecimal value1, BigDecimal value2) {
        if (value1.compareTo(value2) > -1) {
            return value2;
        } else {
            return value1;
        }
    }

    private static void insertData(DynamicObjectCollection aosEntryentityS, Map<String, Object> insertMap,
        String type) {
        DynamicObject aosEntryentity = aosEntryentityS.addNew();
        Object aosSpecial = insertMap.get("aos_special");
        Object aosOnline = insertMap.get("aos_online");
        if (sign.roi.name.equals(type) || sign.pro.name.equals(type)) {
            aosSpecial = false;
        }
        aosEntryentity.set("aos_itemid", insertMap.get("item_id"));
        aosEntryentity.set("aos_productno", insertMap.get("aos_productno") + "-AUTO");
        aosEntryentity.set("aos_itemnumer", insertMap.get("aos_itemnumer"));
        aosEntryentity.set("aos_makedate", insertMap.get("aos_makedate"));
        aosEntryentity.set("aos_groupdate", insertMap.get("aos_groupdate"));
        aosEntryentity.set("aos_eliminatedate", insertMap.get("date"));
        aosEntryentity.set("aos_groupstatus", type);
        aosEntryentity.set("aos_serialstatus", "UNAVAILABLE");
        aosEntryentity.set("aos_shopsku", insertMap.get("aos_shopsku"));
        aosEntryentity.set("aos_budget", insertMap.get("aos_budget"));
        aosEntryentity.set("aos_bid", insertMap.get("aos_bid"));
        aosEntryentity.set("aos_lastpricedate", insertMap.get("aos_lastpricedate"));
        aosEntryentity.set("aos_avadays", insertMap.get("aos_avadays"));
        aosEntryentity.set("aos_seasonseting", insertMap.get("aos_seasonseting"));
        aosEntryentity.set("aos_contryentrystatus", insertMap.get("aos_contryentrystatus"));
        aosEntryentity.set("aos_cn_name", insertMap.get("aos_cn_name"));
        aosEntryentity.set("aos_lastbid", insertMap.get("aos_lastbid"));
        aosEntryentity.set("aos_basestitem", insertMap.get("aos_basestitem"));
        aosEntryentity.set("aos_category1", insertMap.get("aos_category1"));
        aosEntryentity.set("aos_category2", insertMap.get("aos_category2"));
        aosEntryentity.set("aos_category3", insertMap.get("aos_category3"));
        aosEntryentity.set("aos_sal_group", insertMap.get("aos_sal_group"));
        aosEntryentity.set("aos_tmp", insertMap.get("aos_tmp"));
        aosEntryentity.set("aos_roi14days", insertMap.get("aos_roi14days"));
        aosEntryentity.set("aos_lastspend", insertMap.get("aos_lastspend"));
        aosEntryentity.set("aos_profit", insertMap.get("aos_profit"));
        aosEntryentity.set("aos_age", insertMap.get("aos_age"));
        aosEntryentity.set("aos_roi7days", insertMap.get("aos_roi7days"));
        aosEntryentity.set("aos_special", aosSpecial);
        aosEntryentity.set("aos_overseaqty", insertMap.get("aos_overseaqty"));
        aosEntryentity.set("aos_online", aosOnline);
        aosEntryentity.set("aos_is_saleout", insertMap.get("aos_is_saleout"));
        aosEntryentity.set("aos_firstindate", insertMap.get("aos_firstindate"));
        aosEntryentity.set("aos_rpt_roi", insertMap.get("aos_rpt_roi"));
        int forteen = 14;
        if (FndGlobal.IsNotNull(aosOnline) && MktComUtil.getBetweenDays(new Date(), (Date)aosOnline) < forteen) {
            aosEntryentity.set("aos_offline", "Y");
        } else {
            aosEntryentity.set("aos_offline", "N");
        }
        if (sign.roi.name.equals(type)) {
            aosEntryentity.set("aos_roiflag", true);
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

    private static String getRoiType(BigDecimal popOrgRoist, BigDecimal worst, BigDecimal worry, BigDecimal standard,
        BigDecimal well, BigDecimal excellent, BigDecimal roi7DaysSerial) {
        String roiType = null;
        if (roi7DaysSerial.compareTo(popOrgRoist.add(worst)) < 0) {
            roiType = "WORST";
        } else if (roi7DaysSerial.compareTo(popOrgRoist.add(worry)) >= 0
            && roi7DaysSerial.compareTo(popOrgRoist.add(standard)) < 0) {
            roiType = "WORRY";
        } else if (roi7DaysSerial.compareTo(popOrgRoist.add(standard)) >= 0
            && roi7DaysSerial.compareTo(popOrgRoist.add(well)) < 0) {
            roiType = "STANDARD";
        } else if (roi7DaysSerial.compareTo(popOrgRoist.add(well)) >= 0
            && roi7DaysSerial.compareTo(popOrgRoist.add(excellent)) < 0) {
            roiType = "WELL";
        } else if (roi7DaysSerial.compareTo(popOrgRoist.add(excellent)) >= 0) {
            roiType = "EXCELLENT";
        }
        return roiType;
    }

    private static Map<String, BigDecimal> queryOrgCpc(String aosOrgid) {
        String selectFileds = "aos_category1," + "aos_category2," + "aos_category3," + "aos_bid";
        DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_orgcpc", selectFileds,
            new QFilter[] {new QFilter("aos_orgid", QCP.equals, aosOrgid)});
        return list.stream()
            .collect(Collectors.toMap(
                obj -> obj.getString("aos_category1") + obj.getString("aos_category2") + obj.getString("aos_category3"),
                obj -> obj.getBigDecimal("aos_bid"), (k1, k2) -> k1));
    }

    public static Map<String, BigDecimal> generateShipFee() {
        Map<String, BigDecimal> map = new HashMap<>(16);
        DynamicObjectCollection dyns = QueryServiceHelper.query("aos_quo_skuquery",
            "aos_orgid.id aos_orgid,aos_lowest_fee,aos_itemid.id aos_itemid", null);
        for (DynamicObject d : dyns) {
            BigDecimal aosLowestFee = d.getBigDecimal("aos_lowest_fee");
            String aosItemCode = d.getString("aos_itemid");
            String aosOuCode = d.getString("aos_orgid");
            map.put(aosOuCode + "~" + aosItemCode, aosLowestFee);
        }
        return map;
    }

    public static Map<String, BigDecimal> initItemCost() {
        Map<String, BigDecimal> map = new HashMap<>(16);
        DynamicObjectCollection dyns = QueryServiceHelper.query("aos_sync_invcost",
            "aos_orgid.id aos_orgid," + "aos_itemid.id aos_itemid," + "aos_item_cost",
            new QFilter[] {new QFilter("aos_isnew", QCP.equals, true)});
        for (DynamicObject d : dyns) {
            BigDecimal aosItemCost = d.getBigDecimal("aos_item_cost");
            String aosOrgid = d.getString("aos_orgid");
            String aosItemid = d.getString("aos_itemid");
            map.put(aosOrgid + "~" + aosItemid, aosItemCost);
        }
        return map;
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
     * 获取前一天曝光
     * 
     * @param aosOrgid 国别
     * @return Map<String, BigDecimal>
     */
    private static Map<String, BigDecimal> getPreDayExposure(String aosOrgid) {
        Calendar instance = Calendar.getInstance();
        String aosDate = DateFormatUtils.format(instance.getTime(), "yyyy-MM-dd");
        instance.add(Calendar.DAY_OF_MONTH, -1);
        String aosDate1 = DateFormatUtils.format(instance.getTime(), "yyyy-MM-dd");
        QFilter qFilter = new QFilter("aos_orgid", QCP.equals, aosOrgid);
        qFilter.and(new QFilter("aos_date", QCP.like, aosDate + "%"));
        qFilter.and(new QFilter("aos_entryentity.aos_date_l", QCP.like, aosDate1 + "%"));
        String selectFields =
            "aos_entryentity.aos_ad_name aos_ad_name," + "aos_entryentity.aos_impressions aos_impressions";
        DynamicObjectCollection aosBaseSkupoprpt =
            QueryServiceHelper.query("aos_base_skupoprpt", selectFields, qFilter.toArray());
        return aosBaseSkupoprpt.stream().collect(Collectors.toMap(obj -> obj.getString("aos_ad_name"),
            obj -> obj.getBigDecimal("aos_impressions"), (k1, k2) -> k1));
    }

    /**
     * 营销剔除
     * 
     * @param aosOrgid 国别
     * @return Set<String>
     */
    private static Set<String> getEliminateItem(String aosOrgid) {
        DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_dataeliminate", "aos_itemid",
            new QFilter[] {new QFilter("aos_orgid", QCP.equals, aosOrgid)});
        return list.stream().map(obj -> obj.getString("aos_itemid")).collect(Collectors.toSet());
    }

    /**
     * 获取物料事物处理表中 获取国别物料维度的最大入库日期
     *
     * @param orgid 国别主键
     * @param wareHouseDate 入库时间
     * @return 物料的入库时间，同一入库时间下的所有物料
     */
    private static Map<String, List<String>> query7daysWareHouseItem(Object orgid, Date wareHouseDate) {
        SimpleDateFormat simDay = new SimpleDateFormat("yyyy-MM-dd");
        LocalDate localWareHouse = wareHouseDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        localWareHouse = localWareHouse.minusDays(6);
        QFilter filterOrg = new QFilter("aos_ou", "=", orgid);
        QFilter filterDate = new QFilter("aos_trans_date", ">=", localWareHouse.toString());
        String algo = "mkt.popular.ppc.aos_mkt_popppc_init.Query7daysWareHouseItem";
        DataSet ds = QueryServiceHelper.queryDataSet(algo, "aos_sync_inv_trans", "aos_item,aos_trans_date",
            new QFilter[] {filterOrg, filterDate}, null);
        ds = ds.groupBy(new String[] {"aos_item"}).max("aos_trans_date").finish();
        // 物料和其最新入库时间
        DynamicObjectCollection dycItemAndDate = ORM.create().toPlainDynamicObjectCollection(ds);
        ds.close();
        return dycItemAndDate.stream().collect(Collectors.groupingBy(dy -> simDay.format(dy.getDate("aos_trans_date")),
            Collectors.mapping(dy -> dy.getString("aos_item"), Collectors.toList())));
    }

    /**
     * 判断入库前30是否全断货
     *
     * @param orgid orgid
     * @param wareHouseDate 入库时间
     * @param listItem 物料id
     * @param mapItemToOutage 记录物料的断货情况，存在不断货为true，全断货为false
     */
    private static void judge30DaysStock(Object orgid, String wareHouseDate, List<String> listItem,
        Map<String, Boolean> mapItemToOutage) {
        LocalDate localWarehouseDate = LocalDate.parse(wareHouseDate);
        // 判断入库的前30天，是否存在不断货
        QFilter filterOrg = new QFilter("aos_entryentity.aos_orgid", "=", orgid);
        QFilter filterTo = new QFilter("aos_date", "<=", localWarehouseDate.toString());
        QFilter filterFrom = new QFilter("aos_date", ">=", localWarehouseDate.minusDays(30).toString());
        QFilter filterItem = new QFilter("aos_entryentity.aos_itemid", QFilter.in, listItem);
        // 不缺货
        QFilter filterNoShortage = new QFilter("aos_entryentity.aos_flag", "!=", true);
        QFilter[] qfs = new QFilter[] {filterOrg, filterItem, filterTo, filterFrom, filterNoShortage};
        List<String> listNoShortage = QueryServiceHelper
            .query("aos_sync_offsale_bak", "aos_entryentity.aos_itemid aos_itemid", qfs).stream()
            .map(dy -> dy.getString("aos_itemid")).filter(FndGlobal::IsNotNull).distinct().collect(Collectors.toList());
        // 将存在不断货的信息修改为true
        for (String noShortageItem : listNoShortage) {
            mapItemToOutage.put(noShortageItem, true);
        }
    }

    /**
     * 获取前7天的销售加回数据
     **/
    private static Map<String, String> querySaleAdd(Object orgid) {
        QFilter filterOrg = new QFilter("aos_orgid", "=", orgid);
        LocalDate now = LocalDate.now();
        QFilter qFilterDateMin = new QFilter("aos_date", ">=", now.minusDays(1).toString());
        QFilter qFilterDateMax = new QFilter("aos_date", "<", now.toString());
        QFilter[] qfs = new QFilter[] {filterOrg, qFilterDateMin, qFilterDateMax};
        StringJoiner str = new StringJoiner(",");
        str.add("aos_entryentity.aos_itemnumer aos_itemnumer");
        str.add("aos_entryentity.aos_special aos_special");
        return QueryServiceHelper.query("aos_mkt_popular_ppc", str.toString(), qfs).stream()
            .collect(Collectors.toMap(dy -> dy.getString("aos_itemnumer"), dy -> dy.getString("aos_special")));
    }

    /**
     * 获取固定剔除物料
     **/
    private static List<String> getRejectItem(Object orgid) {
        QFBuilder builder = new QFBuilder();
        builder.add("aos_ou", "=", orgid);
        builder.add("aos_type", "=", "sp");
        builder.add("aos_item", "!=", "");
        return QueryServiceHelper.query("aos_mkt_reject", "aos_item", builder.toArray()).stream()
            .map(dy -> dy.getString("aos_item")).distinct().collect(Collectors.toList());
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        executerun();
        FndWebHook.send(URL, "PPC推广SP初始化已成功生成!");
    }

    /**
     * 标识枚举类
     */
    private enum sign {
        /**
         * 销售加回判断
         */
        ppcAdd("PPC_ADD"),
        /**
         * PRO
         */
        pro("PRO"),
        /**
         * ROI
         */
        roi("ROI");

        /**
         * 名称
         */
        private final String name;

        /**
         * 构造方法
         *
         * @param name 名称
         */
        sign(String name) {
            this.name = name;
        }
    }
}
