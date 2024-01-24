package mkt.popularst.sale;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import common.fnd.FndGlobal;
import common.fnd.FndLog;
import common.fnd.FndMsg;
import org.apache.commons.lang3.time.DateUtils;

import common.Cux_Common_Utl;
import common.sal.util.InStockAvailableDays;
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
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.threads.ThreadPool;
import kd.bos.threads.ThreadPools;
import mkt.common.AosMktGenerate;
import mkt.common.MKTCom;
import mkt.popularst.promot.AosMktPopAdjpstTask;

/**
 * @author aosom
 * @version ST出价调整销售-调度任务类
 */
public class AosMktPopAdjstTask extends AbstractTask {
    public final static String SYSTEM = Cux_Common_Utl.SYSTEM;
    private static final DistributeSessionlessCache CACHE =
        CacheFactory.getCommonCacheFactory().getDistributeSessionlessCache("mkt_redis");

    public static void executerun(Map<String, Object> param) {
        // 获取传入国别参数
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
        // 删除出价调整销售
        DeleteServiceHelper.delete("aos_mkt_pop_adjst", filtersAdj);
        // 删除出价调整销售数据表
        DeleteServiceHelper.delete("aos_mkt_pop_adjstdt", filtersAdj);
        // 放置国别组缓存
        CACHE.put(pOuCode + "_GroupSizeCountST", 0 + "", 3600);
        // 循环销售可用海外公司
        QFilter overseaFlag = new QFilter("aos_is_oversea_ou.number", "=", "Y");
        QFilter overseaFlag2 = new QFilter("aos_isomvalid", "=", true);
        // 海外公司
        QFilter qfOu = new QFilter("number", "=", pOuCode);
        QFilter[] filtersOu = new QFilter[] {overseaFlag, overseaFlag2, qfOu};
        DynamicObjectCollection bdCountry = QueryServiceHelper.query("bd_country", "id,number", filtersOu);
        // 调用线程池
        ThreadPool threadPool = ThreadPools.newFixedThreadPool("aos_mkt_popadjst_init." + pOuCode, 4);
        for (DynamicObject ou : bdCountry) {
            // 获取国别可用销售组个数
            QFilter groupOrg = new QFilter("aos_org.number", "=", ou.get("number"));
            QFilter groupEnable = new QFilter("enable", "=", 1);
            QFilter[] filtersGroup = new QFilter[] {groupOrg, groupEnable};
            DynamicObjectCollection aosSalgroup = QueryServiceHelper.query("aos_salgroup", "id", filtersGroup);
            CACHE.put(pOuCode + "_GroupSizeST", String.valueOf(aosSalgroup.size()), 3600);
            for (DynamicObject group : aosSalgroup) {
                long groupId = group.getLong("id");
                Map<String, Object> params = new HashMap<>(16);
                params.put("p_ou_code", pOuCode);
                params.put("p_group_id", groupId);
                doOperate(params);
            }
        }
        threadPool.close();
    }

    public static void doOperate(Map<String, Object> params) {
        // 获取传入参数
        String pOuCode = (String)params.get("p_ou_code");
        long pGroupId = (long)params.get("p_group_id");
        Object pOrgId = FndGlobal.get_import_id(pOuCode, "bd_country");
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        Date todayD = today.getTime();
        int year = today.get(Calendar.YEAR) - 2000;
        int month = today.get(Calendar.MONTH) + 1;
        int day = today.get(Calendar.DAY_OF_MONTH);
        // 初始化数据
        // 3日组报告
        HashMap<String, Map<String, Object>> skuRpt3Avg = AosMktGenerate.GenerateSkuRpt3Group(pOuCode);
        // 7日组报告
        HashMap<String, Map<String, Object>> skuRpt = AosMktGenerate.GenerateSkuRpt7Group(pOuCode);
        // 今日可用词
        HashMap<String, Map<String, Object>> keyDetailToday = AosMktGenerate.GenerateKeyDetailToday(pOuCode, pGroupId);
        // 半月销量
        HashMap<String, Integer> orderMonth = AosMktGenerate.GenerateOrderMonth(pOuCode);
        HashMap<String, Map<String, Object>> keyRpt7Map = AosMktGenerate.GenerateKeyRpt7(pOuCode);
        // 昨日数据
        HashMap<String, Map<String, Object>> ppcYester = AosMktGenerate.GeneratePpcYesterSt(pOuCode);
        // 昨日数据
        HashMap<String, Map<String, Object>> ppcToday = AosMktGenerate.GeneratePpcTodaySt(pOuCode);
        // 关键词建议价格
        HashMap<String, Map<String, Object>> adPriceKey = AosMktGenerate.GenerateAdvKeyPrice(pOuCode);
        List<Map<String, Object>> itemQtyList = AosMktGenerate.GenerateItemQtyList(pOuCode);
        // 海外库存
        Map<String, Object> itemOverseaQtyMap = itemQtyList.get(0);
        // 营销国别标准参数
        HashMap<String, Map<String, Object>> popOrgInfo = AosMktGenerate.GeneratePopOrgInfo(pOuCode);
        // 国别曝光标准BigDecimal
        BigDecimal exposure = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "EXPOSURE").get("aos_value");
        // 前7天日均销量标准
        BigDecimal avgSales7Std = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "7AVGSALES").get("aos_value");
        Date summerSpringEnd = MKTCom.Get_DateRange("aos_dateto", "SS", pOrgId);
        Date autumnWinterEnd = MKTCom.Get_DateRange("aos_dateto", "AW", pOrgId);
        // 判断是否季末
        Date summerSpringSeasonEnd = DateUtils.setDays(DateUtils.addDays(summerSpringEnd, -32), 1);
        Date autumnWinterSeasonEnd = DateUtils.setDays(DateUtils.addDays(autumnWinterEnd, -32), 1);
        QFilter qfDate = new QFilter("aos_date", "=", todayD);
        QFilter qfOrg = new QFilter("aos_orgid.number", "=", pOuCode);
        QFilter qfType = new QFilter("aos_keystatus", "=", "AVAILABLE");
        QFilter qfGroup = new QFilter("aos_sal_group", "=", pGroupId);
        QFilter[] filters = new QFilter[] {qfDate, qfOrg, qfType, qfGroup};
        String selectField = "aos_sourceid id,aos_billno," + "aos_itemnumer,"
            + "aos_itemname,aos_season,aos_category1,aos_category2,aos_category3,"
            + "aos_avadays,aos_itemid,1 aos_count";
        DataSet aosMktPopPpcstS = QueryServiceHelper.queryDataSet("aos_mkt_popadjst_init.do_operate" + pOuCode,
            "aos_mkt_ppcst_data", selectField, filters, null);
        String[] groupBy = new String[] {"id", "aos_billno", "aos_itemnumer", "aos_itemname", "aos_season",
            "aos_category1", "aos_category2", "aos_category3", "aos_avadays", "aos_itemid"};
        aosMktPopPpcstS = aosMktPopPpcstS.groupBy(groupBy).sum("aos_count").finish();
        int row = 0;
        // 初始化日志
        FndLog log = FndLog.init("MKT_ST出价调整(销售)", pOuCode + year + month + day);
        String isContinue = FndMsg.getStatic("IS_CONTINUE");
        DynamicObject aosMktPopAdjstdt = null;
        DynamicObjectCollection aosDetailentryS = null;
        while (aosMktPopPpcstS.hasNext()) {
            Row aosMktPopPpcst = aosMktPopPpcstS.next();
            row++;
            // 首次循环时新建头
            if (row == 1) {
                DynamicObject aosMktPopAdjst = BusinessDataServiceHelper.newDynamicObject("aos_mkt_pop_adjst");
                aosMktPopAdjst.set("aos_billno", aosMktPopPpcst.get("aos_billno"));
                aosMktPopAdjst.set("aos_ppcid", aosMktPopPpcst.get("id"));
                aosMktPopAdjst.set("aos_orgid", pOrgId);
                aosMktPopAdjst.set("billstatus", "A");
                aosMktPopAdjst.set("aos_status", "A");
                aosMktPopAdjst.set("aos_makeby", SYSTEM);
                aosMktPopAdjst.set("aos_makedate", todayD);
                aosMktPopAdjst.set("aos_groupid", pGroupId);
                OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_pop_adjst",
                    new DynamicObject[] {aosMktPopAdjst}, OperateOption.create());
                Object aosSourceid = operationrst.getSuccessPkIds().get(0);
                aosMktPopAdjstdt = BusinessDataServiceHelper.newDynamicObject("aos_mkt_pop_adjstdt");
                aosMktPopAdjstdt.set("aos_billno", aosMktPopPpcst.get("aos_billno"));
                aosMktPopAdjstdt.set("aos_orgid", pOrgId);
                aosMktPopAdjstdt.set("billstatus", "A");
                aosMktPopAdjstdt.set("aos_groupid", pGroupId);
                aosMktPopAdjstdt.set("aos_sourceid", aosSourceid);
                aosMktPopAdjstdt.set("aos_makedate", todayD);
                aosDetailentryS = aosMktPopAdjstdt.getDynamicObjectCollection("aos_detailentry");
            }

            // 全部跳过 不生成数据
            if ("Y".equals(isContinue)) {
                continue;
            }

            // 插入ST出价调整(销售)数据
            long itemId = aosMktPopPpcst.getLong("aos_itemid");
            String aosItemnumer = aosMktPopPpcst.getString("aos_itemnumer");
            int aosAvadays = aosMktPopPpcst.getInteger("aos_avadays");
            Map<String, Object> skuRpt3AvgMap = skuRpt3Avg.get(aosItemnumer);
            BigDecimal impress3Avg = BigDecimal.ZERO;
            if (skuRpt3AvgMap != null) {
                impress3Avg = (BigDecimal)skuRpt3AvgMap.get("aos_impressions");
            }
            log.add(aosItemnumer + "三天平均曝光=" + impress3Avg + " 国别标准=" + exposure);
            // PPC数据源 出价调整(销售)筛选逻辑
            // 3天平均曝光<国别标准
            if (impress3Avg.compareTo(exposure) >= 0) {
                continue;
            }
            // 只筛选0销量 低销量
            int halfMonthTotalSales = (int)Cux_Common_Utl.nvl(orderMonth.get(String.valueOf(itemId)));
            float r = InStockAvailableDays.getOrgItemOnlineAvgQty(pOrgId.toString(), String.valueOf(itemId));
            Boolean regularUn = MKTCom.Get_RegularUn((long)pOrgId, itemId, pOuCode, aosAvadays, r, halfMonthTotalSales);
            log.add(aosItemnumer + "非0销量低销量 标记=" + regularUn);
            if (regularUn) {
                continue;
            }
            // 前7天日均销量 < 国别标准
            if (BigDecimal.valueOf(r).compareTo(avgSales7Std) >= 0) {
                log.add(aosItemnumer + "前7天日均销量=" + r + " 国别标准=" + avgSales7Std);
                continue;
            }
            // 判断季节品是否季末有剩余
            String aosSeason = aosMktPopPpcst.getString("aos_season");
            String aosSeasonpro = "";
            if ("春季产品".equals(aosSeason)) {
                aosSeasonpro = "SPRING";
            }
            switch (aosSeason) {
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
            // 海外库存
            Object itemOverseaqty = itemOverseaQtyMap.get(String.valueOf(itemId));
            if (FndGlobal.IsNull(itemOverseaqty)) {
                itemOverseaqty = 0;
            }
            if ("SPRING".equals(aosSeasonpro) || "SPRING_SUMMER".equals(aosSeasonpro) || "SUMMER".equals(aosSeasonpro)
                || "SPRING-SUMMER-CONVENTIONAL".equals(aosSeasonpro)) {
                if (todayD.after(summerSpringSeasonEnd)) {
                    // 季末 判断是否达标
                    float seasonRate = MKTCom.Get_SeasonRate((long)pOrgId, itemId, aosSeasonpro, itemOverseaqty, month);
                    if (!MKTCom.Is_SeasonRate(aosSeasonpro, month, seasonRate)) {
                        log.add(aosItemnumer + "季末达标");
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
            // 开始行赋值
            DynamicObject aosDetailentry = aosDetailentryS.addNew();
            aosDetailentry.set("aos_itemid", aosMktPopPpcst.get("aos_itemid"));
            aosDetailentry.set("aos_cn_name", aosMktPopPpcst.get("aos_itemname"));
            aosDetailentry.set("aos_seasonseting", aosMktPopPpcst.get("aos_season"));
            aosDetailentry.set("aos_categroy1", aosMktPopPpcst.get("aos_category1"));
            aosDetailentry.set("aos_categroy2", aosMktPopPpcst.get("aos_category2"));
            aosDetailentry.set("aos_categroy3", aosMktPopPpcst.get("aos_category3"));
            aosDetailentry.set("aos_avadays", aosMktPopPpcst.get("aos_avadays"));
            // 关键词个数
            aosDetailentry.set("aos_count", aosMktPopPpcst.get("aos_count"));
            // 7日组报告字段
            Map<String, Object> skuRptMap = skuRpt.get(aosItemnumer);
            // 7天ROI
            BigDecimal roi7Days = BigDecimal.ZERO;
            // 点击
            BigDecimal aosClick = BigDecimal.ZERO;
            // 曝光
            BigDecimal aosImpressions = BigDecimal.ZERO;
            // 营收
            BigDecimal aosSales = BigDecimal.ZERO;
            // 花费
            BigDecimal aosSpend = BigDecimal.ZERO;
            if (skuRptMap != null) {
                aosClick = (BigDecimal)skuRptMap.get("aos_clicks");
                // 曝光
                aosImpressions = (BigDecimal)skuRptMap.get("aos_impressions");
                // 营收
                aosSales = (BigDecimal)skuRptMap.get("aos_sales");
                // 花费
                aosSpend = (BigDecimal)skuRptMap.get("aos_spend");
            }
            if (skuRptMap != null && (aosSpend).compareTo(BigDecimal.ZERO) != 0) {
                roi7Days = aosSales.divide(aosSpend, 2, RoundingMode.HALF_UP);
            }
            aosDetailentry.set("aos_roi", roi7Days);
            aosDetailentry.set("aos_click", aosClick);
            aosDetailentry.set("aos_spend", aosSpend);
            aosDetailentry.set("aos_expouse", aosImpressions);
            // 关键词循环 最后反写关键词个数
            DynamicObjectCollection aosGroupentryS = aosDetailentry.getDynamicObjectCollection("aos_groupentry");
            // 关键词报告明细
            Map<String, Object> keyRptDetail = keyDetailToday.get(aosItemnumer);
            for (String aosTargetcomb : keyRptDetail.keySet()) {
                String aosKeyword = aosTargetcomb.split("~")[0];
                String aosMatchType = aosTargetcomb.split("~")[1];
                DynamicObject aosGroupentry = aosGroupentryS.addNew();
                aosGroupentry.set("aos_keyword", aosKeyword);
                aosGroupentry.set("aos_match_type", aosMatchType);
                // 获取7日关键词报告数据
                String key = aosItemnumer + "~" + aosTargetcomb;
                if (keyRpt7Map.get(key) != null) {
                    Map<String, Object> keyRpt7MapD = keyRpt7Map.get(key);
                    if (keyRpt7MapD != null) {
                        BigDecimal aosClickR = (BigDecimal)keyRpt7MapD.get("aos_clicks");
                        BigDecimal aosSpendR = (BigDecimal)keyRpt7MapD.get("aos_spend");
                        BigDecimal aosRoiR = (BigDecimal)keyRpt7MapD.get("aos_roi");
                        BigDecimal aosExpouseR = (BigDecimal)keyRpt7MapD.get("aos_impressions");
                        aosGroupentry.set("aos_click_r", aosClickR);
                        aosGroupentry.set("aos_spend_r", aosSpendR);
                        aosGroupentry.set("aos_roi_r", aosRoiR);
                        aosGroupentry.set("aos_expouse_r", aosExpouseR);
                        if (aosClickR != null && aosSpendR.compareTo(BigDecimal.ZERO) != 0) {
                            BigDecimal aosCtrR = aosClickR.divide(aosSpendR, 2, RoundingMode.HALF_UP);
                            aosGroupentry.set("aos_ctr", aosCtrR);
                        }
                    }
                }
                // 获取昨日词维度ST数据源
                Map<String, Object> ppcYesterMap = ppcYester.get(key);
                if (ppcYesterMap != null) {
                    // 昨日出价
                    Object aosLastbid = ppcYesterMap.get("aos_bid");
                    aosGroupentry.set("aos_lastprice", aosLastbid);
                }
                // 获取今日词维度ST数据源
                Map<String, Object> ppcTodayMap = ppcToday.get(key);
                if (ppcTodayMap != null) {
                    // 本次出价
                    Object aosBid = ppcTodayMap.get("aos_bid");
                    // 词状态
                    Object aosKeystatus = ppcTodayMap.get("aos_keystatus");
                    aosGroupentry.set("aos_calprice", aosBid);
                    aosGroupentry.set("aos_manualprz", aosBid);
                    if (!"AVAILABLE".equals(aosKeystatus)) {
                        aosGroupentry.set("aos_valid_flag", true);
                    }
                }
                // 组
                Map<String, Object> adPriceMap = adPriceKey.get(key);
                BigDecimal aosAdviceprz = BigDecimal.ZERO;
                BigDecimal aosHighprz = BigDecimal.ZERO;
                BigDecimal aosLowprz = BigDecimal.ZERO;
                if (adPriceMap != null) {
                    aosAdviceprz = (BigDecimal)adPriceMap.get("aos_bid_suggest");
                    aosHighprz = (BigDecimal)adPriceMap.get("aos_bid_rangeend");
                    aosLowprz = (BigDecimal)adPriceMap.get("aos_bid_rangestart");
                }
                aosGroupentry.set("aos_adviceprz", aosAdviceprz);
                aosGroupentry.set("aos_highprz", aosHighprz);
                aosGroupentry.set("aos_lowprz", aosLowprz);
            }
        }
        aosMktPopPpcstS.close();
        if (aosMktPopAdjstdt != null) {
            // 保存ST出价调整(销售)数据表
            OperationServiceHelper.executeOperate("save", "aos_mkt_pop_adjstdt", new DynamicObject[] {aosMktPopAdjstdt},
                OperateOption.create());
        }
        log.finnalSave();
        int groupSizeCount = groupSizeCountAdd(pOuCode);
        int groupSize = Integer.parseInt(CACHE.get(pOuCode + "_GroupSizeST"));
        // 本国家 出价调整(销售)已全部执行完毕 后 执行 出价调整(推广)
        if (groupSizeCount == groupSize) {
            Map<String, Object> adjs = new HashMap<>(16);
            adjs.put("p_ou_code", pOuCode);
            AosMktPopAdjpstTask.executerun(adjs);
        }
    }

    private synchronized static int groupSizeCountAdd(String pOuCode) {
        int groupSizeCount = Integer.parseInt(CACHE.get(pOuCode + "_GroupSizeCountST")) + 1;
        CACHE.put(pOuCode + "_GroupSizeCountST", String.valueOf(groupSizeCount));
        return groupSizeCount;
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        // 调度任务 触发多线程
        executerun(param);
    }
}