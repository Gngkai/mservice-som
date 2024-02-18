package mkt.popularst.ppc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import common.CommonDataSom;
import common.fnd.FndDate;
import common.fnd.FndGlobal;
import common.fnd.FndMsg;
import common.Cux_Common_Utl;
import common.sal.util.InStockAvailableDays;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import mkt.common.AosMktGenUtil;
import mkt.common.MKTCom;
import mkt.popular.AosMktPopUtil;
import mkt.popularst.add.AosMktPopAddsTask;
import mkt.popularst.sale.AosMktPopStaddTask;
import mkt.popularst.sale.AosMktPopAdjstTask;
import mkt.popularst.week.AosMktPopWeekTask;

/**
 * @author aosom
 * @version ST-调度任务类
 */
public class AosMktPopPpcstTask extends AbstractTask {
    private static final String KW = "KW";
    private static final String ST = "ST";
    private static final String AMAZON = "AMAZON";
    private static final int TWOHUNDRED = 200;

    public static void executerun() {
        FndMsg.debug("=======开始推广ST生成核心=======");
        // 当前时间
        Calendar today = Calendar.getInstance();
        // 当前工作日
        int week = today.get(Calendar.DAY_OF_WEEK);
        // 判断今日是否执行
        boolean copyFlag = AosMktPopUtil.getCopyFlag("PPC_ST", week);
        // 若今日需要执行则初始化通用计算数据
        if (copyFlag) {
            CommonDataSom.init();
        }
        // 当前整点
        int pHour = today.get(Calendar.HOUR_OF_DAY);
        // 根据当前整点判断执行的是欧洲与美加
        QFilter qfTime = AosMktPopUtil.getEuOrUsQf(pHour);
        // 获取所有当前整点应执行的销售国别
        DynamicObjectCollection bdCountry =
            QueryServiceHelper.query("bd_country", "id,number", new QFilter("aos_is_oversea_ou.number", QCP.equals, "Y")
                .and("aos_isomvalid", QCP.equals, true).and(qfTime).toArray());
        for (DynamicObject ou : bdCountry) {
            Map<String, Object> params = new HashMap<>(16);
            params.put("p_ou_code", ou.getString("number"));
            // 主方法
            doOperate(params);
        }
        // 生成销售手动调整ST
        AosMktPopAddsTask.executerun(pHour);
        // 生成销售周调整ST
        AosMktPopWeekTask.executerun(pHour);
        // 生成ST销售加回
        AosMktPopStaddTask.stInit();
    }

    public static void doOperate(Map<String, Object> params) {
        // 日期格式化
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        // 国别编码
        Object pOuCode = params.get("p_ou_code");
        // 国别id
        Object pOrgId = FndGlobal.get_import_id(pOuCode, "bd_country");
        // 删除该国别今日已生成的数据
        Calendar date = Calendar.getInstance();
        date.set(Calendar.HOUR_OF_DAY, 0);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);
        Date today = date.getTime();
        // 删除ST主表数据
        DeleteServiceHelper.delete("aos_mkt_pop_ppcst",
            new QFilter("aos_orgid.number", QCP.equals, pOuCode).and("aos_date", QCP.equals, today).toArray());
        // 删除ST数据表数据
        DeleteServiceHelper.delete("aos_mkt_ppcst_data",
            new QFilter("aos_orgid.number", QCP.equals, pOuCode).and("aos_date", QCP.equals, today).toArray());
        // 获取单号
        int year = date.get(Calendar.YEAR) - 2000;
        int month = date.get(Calendar.MONTH) + 1;
        int day = date.get(Calendar.DAY_OF_MONTH);
        String aosBillno = ST + pOuCode + year + month + day;
        // 推广类型
        Object aosPoptype = FndGlobal.get_import_id(ST, "aos_mkt_base_poptype");
        // 推广平台
        Object platformId = FndGlobal.get_import_id(AMAZON, "aos_sal_channel");
        // 当前周
        int week = date.get(Calendar.DAY_OF_WEEK);
        // 如果不是执行周期中的日期则复制昨日数据
        boolean copyFlag = AosMktPopUtil.getCopyFlag("PPC_ST", week);
        if (!copyFlag) {
            copyLastDayData(pOrgId, aosBillno, aosPoptype, today, platformId);
            return;
        }
        // 获取前三日
        date.add(Calendar.DAY_OF_MONTH, -1);
        String yester = df.format(date.getTime());
        date.add(Calendar.DAY_OF_MONTH, -1);
        String yester2 = df.format(date.getTime());
        date.add(Calendar.DAY_OF_MONTH, -1);
        String yester3 = df.format(date.getTime());
        // 获取春夏品 秋冬品开始结束日期 营销日期参数表
        // 记录系列是否可用
        Map<String, String> productAvaMap = new HashMap<>(16);
        // 记录系列可用组个数
        Map<String, Integer> productNoMap = new HashMap<>(16);
        Date summerSpringStart = MKTCom.Get_DateRange("aos_datefrom", "SS", pOrgId);
        Date summerSpringEnd = MKTCom.Get_DateRange("aos_dateto", "SS", pOrgId);
        Date autumnWinterStart = MKTCom.Get_DateRange("aos_datefrom", "AW", pOrgId);
        Date autumnWinterEnd = MKTCom.Get_DateRange("aos_dateto", "AW", pOrgId);
        // 通用数据 店铺货号
        HashMap<String, String> productInfo = AosMktGenUtil.generateShopSku(pOuCode);
        List<Map<String, Object>> itemQtyList = AosMktGenUtil.generateItemQtyList(pOuCode);
        // 海外库存
        Map<String, Object> itemOverseaQtyMap = itemQtyList.get(0);
        // 在途数量
        Map<String, Object> itemIntransQtyMap = itemQtyList.get(1);
        // 组创建日期
        HashMap<String, Object> groupDateMap = AosMktGenUtil.generateLastGroupDate(pOuCode);
        // 词创建日期
        HashMap<String, Object> keyDateMap = AosMktGenUtil.generateLastKeyDate(pOuCode);
        // 词创建日期
        HashMap<String, Object> serialDateMap = AosMktGenUtil.generateLastSerialDate(pOuCode);
        // 关键词报告 非常重要！
        HashMap<String, Map<String, Object>> keyRpt = AosMktGenUtil.generateKeyRpt(pOuCode);
        // 关键词建议价格
        HashMap<String, Map<String, Object>> adPriceKey = AosMktGenUtil.generateAdvKeyPrice(pOuCode);
        // 亚马逊定价
        HashMap<String, Object> dailyPrice = AosMktGenUtil.generateDailyPrice(pOuCode);
        // 营销国别标准参数
        HashMap<String, Map<String, Object>> popOrgInfo = AosMktGenUtil.generatePopOrgInfo(pOuCode);
        // 可售天数
        HashMap<String, Object> itemAvaDays = AosMktGenUtil.generateAvaDays(pOuCode);
        // 昨日数据
        HashMap<String, Map<String, Object>> ppcYester = AosMktGenUtil.generatePpcYesterSt(pOuCode);
        // 7日词报告
        HashMap<String, Map<String, Object>> keyRpt7 = AosMktGenUtil.generateSkuRpt7(pOuCode);
        // 预测汇总表
        HashMap<String, Map<String, Object>> salSummary = AosMktGenUtil.gnerateSalSummarySerial(pOuCode);
        HashMap<String, Map<String, Object>> ppcYesterSerial = AosMktGenUtil.generatePpcYesterSerial(pOuCode);
        HashMap<String, Map<String, Object>> skuRptSerial = AosMktGenUtil.generateSkuRptSerial(pOuCode);
        HashMap<String, Map<String, Object>> skuRptDetailSerial = AosMktGenUtil.generateSkuRptDetailSerial(pOuCode);
        HashMap<String, Map<String, Map<String, Object>>> skuRpt3Serial =
            AosMktGenUtil.generateSkuRpt3SerialObject(pOuCode);
        Set<String> manualSet = generateManual(pOuCode);
        Set<String> weekSet = generateWeek(pOuCode);
        HashMap<String, Map<String, String>> itemPoint = generateItemPoint(pOrgId);
        // 全程天数信息
        List<Integer> mapList = AosMktGenUtil.generateShpDay(pOuCode);
        // 备货天数
        int aosShpDay = mapList.get(0);
        // 海运天数
        int aosFreightDay = mapList.get(1);
        // 清关天数
        int aosClearDay = mapList.get(2);
        // 营销国别参数
        // 最高标准1(定价<200)
        BigDecimal high1 = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "HIGH1").get("aos_value");
        // 最高标准2(200<=定价<500)
        BigDecimal high2 = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "HIGH2").get("aos_value");
        // 最高标准3(定价>=500)
        BigDecimal high3 = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "HIGH3").get("aos_value");
        // 国别首次出价均价
        BigDecimal popOrgFirstBid = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "FIRSTBID").get("aos_value");
        BigDecimal lowestbid = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "LOWESTBID").get("aos_value");
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
        // AM营收占比
        BigDecimal popOrgAmSaleRate = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "AMSALE_RATE").get("aos_value");
        // AM付费营收占比
        BigDecimal popOrgAmAffRate = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "AMAFF_RATE").get("aos_value");
        // 推广词点击
        BigDecimal pointClick = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "POINT_CLICK").get("aos_value");
        // 营销出价调整幅度参数表
        String selectColumn = "aos_exposure,aos_exposurerate,aos_roi,aos_roitype,aos_rate,aos_level";
        DataSet aosMktBsadjrateSt = QueryServiceHelper.queryDataSet("aos_mkt_bsadjratest." + pOuCode,
            "aos_mkt_bsadjratest", selectColumn, null, "aos_level");
        // 营销预算调整系数表 到国别维度
        QFilter filterOrg = new QFilter("aos_orgid", "=", pOrgId);
        QFilter[] filtersAdjpara = new QFilter[] {filterOrg};
        selectColumn = "aos_ratefrom,aos_rateto,aos_roi,aos_adjratio";
        DataSet aosMktBsadjparaS = QueryServiceHelper.queryDataSet("aos_mkt_popppc_init." + pOuCode,
            "aos_mkt_bsadjpara", selectColumn, filtersAdjpara, null);
        // 查询国别物料
        QFilter filterOu = new QFilter("aos_contryentry.aos_nationality.number", "=", pOuCode);
        QFilter filterType = new QFilter("aos_protype", "=", "N");
        QFilter[] filters = new QFilter[] {filterOu, filterType};
        String selectField = "id,aos_productno," + "number," + "name," + "aos_contryentry.aos_nationality.id aos_orgid,"
            + "aos_contryentry.aos_nationality.number aos_orgnumber,"
            + "aos_contryentry.aos_seasonseting.number aos_seasonseting,"
            + "aos_contryentry.aos_seasonseting.name aos_season," + "aos_contryentry.aos_seasonseting.id aos_seasonid,"
            + "aos_contryentry.aos_contryentrystatus aos_contryentrystatus,"
            + "aos_contryentry.aos_festivalseting.number aos_festivalseting,"
            + "aos_contryentry.aos_firstindate aos_firstindate";
        DynamicObjectCollection bdMaterialS =
            QueryServiceHelper.query("bd_material", selectField, filters, "aos_productno");
        // 初始化ST头信息
        DynamicObject aosMktPopPpcst = BusinessDataServiceHelper.newDynamicObject("aos_mkt_pop_ppcst");
        aosMktPopPpcst.set("aos_orgid", pOrgId);
        aosMktPopPpcst.set("billstatus", "A");
        aosMktPopPpcst.set("aos_billno", aosBillno);
        aosMktPopPpcst.set("aos_poptype", aosPoptype);
        aosMktPopPpcst.set("aos_date", today);
        aosMktPopPpcst.set("aos_channel", platformId);
        SaveServiceHelper.save(new DynamicObject[] {aosMktPopPpcst});
        DynamicObjectCollection aosEntryentityS = aosMktPopPpcst.getDynamicObjectCollection("aos_entryentity");
        // 循环1:国别物料
        for (DynamicObject bdMaterial : bdMaterialS) {
            long itemId = bdMaterial.getLong("id");
            long orgId = bdMaterial.getLong("aos_orgid");
            String orgidStr = Long.toString(orgId);
            String itemidStr = Long.toString(itemId);
            // 物料编码
            String aosItemnumer = bdMaterial.getString("number");
            // 产品号
            String aosProductno = bdMaterial.getString("aos_productno");
            // 物料品名
            String aosItemname = bdMaterial.getString("name");
            // 物料状态
            String aosItemstatus = bdMaterial.getString("aos_contryentrystatus");
            // 节日属性
            String aosFestivalseting = bdMaterial.getString("aos_festivalseting");
            if (aosFestivalseting == null) {
                aosFestivalseting = "";
            }
            // 季节属性
            String aosSeason = bdMaterial.getString("aos_season");
            String aosSeasonpro = bdMaterial.getString("aos_seasonseting");
            if (aosSeasonpro == null) {
                aosSeasonpro = "";
            }
            Object aosSeasonid = bdMaterial.get("aos_seasonid");
            if (aosSeasonid == null) {
                continue;
            }
            // 海外库存 终止状态且无海外库存 跳过
            Object itemOverseaqty = itemOverseaQtyMap.get(itemidStr);
            if (FndGlobal.IsNull(itemOverseaqty)) {
                itemOverseaqty = 0;
            }
            if ("C".equals(aosItemstatus) && (int)itemOverseaqty == 0) {
                continue;
            }
            // 产品号 在途数量
            Object itemIntransqty = itemIntransQtyMap.get(String.valueOf(itemId));
            if (FndGlobal.IsNull(itemIntransqty)) {
                itemIntransqty = 0;
            }
            if (FndGlobal.IsNull(aosProductno)) {
                continue;
            }
            // 获取AMAZON店铺货号
            String aosShopsku = productInfo.get(String.valueOf(itemId));
            if (FndGlobal.IsNull(aosShopsku)) {
                continue;
            }
            // 可售库存天数7日
            if (itemAvaDays.get(aosItemnumer) == null) {
                continue;
            }
            int aosAvadays = (int)itemAvaDays.get(aosItemnumer);
            // 产品类别
            String itemCategoryId = CommonDataSom.getItemCategoryId(String.valueOf(itemId));
            if (itemCategoryId == null || "".equals(itemCategoryId)) {
                continue;
            }
            // 销售组别
            String salOrg = CommonDataSom.getSalOrgV2(String.valueOf(pOrgId), itemCategoryId);
            if (salOrg == null || "".equals(salOrg)) {
                continue;
            }
            // 物料词信息 无词信息 跳过
            Map<String, Object> keyRptDetail = keyRpt.get(aosItemnumer);
            if (FndGlobal.IsNull(keyRptDetail)) {
                continue;
            }
            // 大中小类
            String itemCategoryName = CommonDataSom.getItemCategoryName(String.valueOf(itemId));
            String aosCategory1 = "";
            String aosCategory2 = "";
            String aosCategory3 = "";
            if (FndGlobal.IsNotNull(itemCategoryName)) {
                String[] split = itemCategoryName.split(",");
                if (split.length == 3) {
                    aosCategory1 = split[0];
                    aosCategory2 = split[1];
                    aosCategory3 = split[2];
                }
            }
            // 新系列新组逻辑
            int kw = 0;
            if (productNoMap.get(aosProductno + "-" + KW) != null) {
                kw = productNoMap.get(aosProductno + "-" + KW);
            }
            productNoMap.put(aosProductno + "-" + KW, kw);
            // 系列创建日期
            Object aosMakedate;
            aosMakedate = serialDateMap.get(aosProductno + "-" + KW);
            if (aosMakedate == null) {
                aosMakedate = today;
            }
            // 组创建日期
            Object aosGroupdate;
            aosGroupdate = groupDateMap.get(aosItemnumer);
            if (aosGroupdate == null) {
                aosGroupdate = today;
            }
            // 给词信息添加 物料品类关键词信息
            Map<String, String> itemPointDetail =
                itemPoint.get(aosCategory1 + "~" + aosCategory2 + "~" + aosCategory3 + "~" + aosItemname);
            if (FndGlobal.IsNotNull(itemPointDetail) && !itemPointDetail.isEmpty()) {
                for (String key : itemPointDetail.keySet()) {
                    keyRptDetail.put(key + "~PHRASE", key + "~PHRASE");
                    keyRptDetail.put(key + "~EXACT", key + "~EXACT");
                }
            }
            // SKU剔除
            Map<String, Object> insertMap = new HashMap<>(16);
            insertMap.put("aos_itemid", itemId);
            insertMap.put("aos_productno", aosProductno);
            insertMap.put("aos_itemnumer", aosItemnumer);
            insertMap.put("aos_shopsku", aosShopsku);
            insertMap.put("aos_makedate", aosMakedate);
            insertMap.put("aos_groupdate", aosGroupdate);
            insertMap.put("aos_eliminatedate", today);
            insertMap.put("KeyRptDetail", keyRptDetail);
            insertMap.put("KeyDateMap", keyDateMap);
            insertMap.put("aos_itemname", aosItemname);
            insertMap.put("aos_season", aosSeason);
            insertMap.put("aos_avadays", aosAvadays);
            // 最低出价
            insertMap.put("aos_bid", lowestbid);
            // 销售组别
            insertMap.put("aos_sal_group", salOrg);
            // 产品大类
            insertMap.put("aos_category1", aosCategory1);
            // 产品中类
            insertMap.put("aos_category2", aosCategory2);
            // 产品小类
            insertMap.put("aos_category3", aosCategory3);
            insertMap.put("aos_itemstatus", aosItemstatus);
            insertMap.put("Today", today);
            // .剔除无词组
            if (FndGlobal.IsNull(keyRptDetail)) {
                insertMap.put("aos_reason", "无词组剔除");
                insertData(aosEntryentityS, insertMap);
                continue;
            }
            // .剔除过季品
            boolean cond = ((FndGlobal.IsNotNull(aosSeasonpro))
                && ("SPRING".equals(aosSeasonpro) || "SPRING_SUMMER".equals(aosSeasonpro)
                    || "SUMMER".equals(aosSeasonpro))
                && (today.before(summerSpringStart) || today.after(summerSpringEnd)));
            if (cond) {
                insertMap.put("aos_reason", "过季品剔除");
                insertData(aosEntryentityS, insertMap);
                continue;
            }
            cond = ((FndGlobal.IsNotNull(aosSeasonpro))
                && ("AUTUMN_WINTER".equals(aosSeasonpro) || "WINTER".equals(aosSeasonpro))
                && (today.before(autumnWinterStart) || today.after(autumnWinterEnd)));
            if (cond) {
                insertMap.put("aos_reason", "过季品剔除");
                insertData(aosEntryentityS, insertMap);
                continue;
            }
            Map<String, Object> dayMap = new HashMap<>(16);
            // 可售天数
            int availableDays = InStockAvailableDays.calInstockSalDaysForDayMin(orgidStr, itemidStr, dayMap);
            if (FndGlobal.IsNull(aosFestivalseting)) {
                if (((availableDays < 30) && (MKTCom.Is_PreSaleOut(orgId, itemId, (int)itemIntransqty, aosShpDay,
                    aosFreightDay, aosClearDay, availableDays)))) {
                    insertMap.put("aos_reason", "预断货剔除");
                    insertData(aosEntryentityS, insertMap);
                    continue;
                }
            }
            // 循环关键词表
            for (String aosTargetcomb : keyRptDetail.keySet()) {
                // 关键词
                String aosKeyword = aosTargetcomb.split("~")[0];
                if (Cux_Common_Utl.IsNull(aosKeyword)) {
                    continue;
                }
                // 匹配方式
                String aosMatchType = aosTargetcomb.split("~")[1];
                // 词创建日
                Object aosKeydate;
                aosKeydate = keyDateMap.get(aosItemnumer + "~" + aosTargetcomb);
                if (aosKeydate == null) {
                    aosKeydate = today;
                }
                // 词报告
                String key = aosItemnumer + "~" + aosTargetcomb;
                Map<String, Object> keyRptMap = keyRpt7.get(key);
                // 无词报告信息剔除
                if (FndGlobal.IsNull(keyRptMap)) {
                    keyInsertData(aosEntryentityS, insertMap, aosKeyword, aosMatchType, aosKeydate);
                    continue;
                }
                // .手动建组剔除
                if (manualSet.contains(key)) {
                    insertMap.put("aos_reason", "手动建组剔除");
                    insertData(aosEntryentityS, insertMap);
                    continue;
                }
                // .周调整剔除
                if (weekSet.contains(key)) {
                    insertMap.put("aos_reason", "周调整剔除");
                    insertData(aosEntryentityS, insertMap);
                    continue;
                }
                // 7天词ROI
                BigDecimal roi7Days = BigDecimal.ZERO;
                BigDecimal aosImpressions = BigDecimal.ZERO;
                BigDecimal aosClicks = BigDecimal.ZERO;
                if (keyRptMap != null && ((BigDecimal)keyRptMap.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                    roi7Days = ((BigDecimal)keyRptMap.get("aos_sales")).divide((BigDecimal)keyRptMap.get("aos_spend"),
                        2, RoundingMode.HALF_UP);
                }
                if (keyRptMap != null) {
                    aosImpressions = (BigDecimal)keyRptMap.get("aos_impressions");
                    aosClicks = (BigDecimal)keyRptMap.get("aos_clicks");
                }
                // 周一触发 词剔除判断
                if (week == 2 && roi7Days.compareTo(BigDecimal.valueOf(3.5)) < 0
                    && FndDate.GetBetweenDays(today, (Date)aosKeydate) > 14 && aosClicks.compareTo(pointClick) > 0) {
                    insertMap.put("aos_reason", "差ROI剔除");
                    keyInsertData(aosEntryentityS, insertMap, aosKeyword, aosMatchType, aosKeydate);
                    continue;
                }
                // 赋值数据
                DynamicObject aosEntryentity = aosEntryentityS.addNew();
                aosEntryentity.set("aos_itemid", itemId);
                aosEntryentity.set("aos_productno", aosProductno + "-" + KW);
                aosEntryentity.set("aos_itemnumer", aosItemnumer);
                aosEntryentity.set("aos_itemstatus", aosItemstatus);
                aosEntryentity.set("aos_keyword", aosKeyword);
                aosEntryentity.set("aos_match_type", aosMatchType);
                aosEntryentity.set("aos_shopsku", aosShopsku);
                aosEntryentity.set("aos_makedate", aosMakedate);
                aosEntryentity.set("aos_groupdate", aosGroupdate);
                aosEntryentity.set("aos_keydate", aosKeydate);
                aosEntryentity.set("aos_keystatus", "AVAILABLE");
                aosEntryentity.set("aos_groupstatus", "AVAILABLE");
                aosEntryentity.set("aos_serialstatus", "AVAILABLE");
                aosEntryentity.set("aos_itemname", aosItemname);
                aosEntryentity.set("aos_season", aosSeason);
                aosEntryentity.set("aos_avadays", aosAvadays);
                aosEntryentity.set("aos_roi7days", roi7Days);
                aosEntryentity.set("aos_impressions", aosImpressions);
                aosEntryentity.set("aos_sal_group", salOrg);
                aosEntryentity.set("aos_category1", aosCategory1);
                aosEntryentity.set("aos_category2", aosCategory2);
                aosEntryentity.set("aos_category3", aosCategory3);
                // 存在一个词可用则系列可用
                productAvaMap.put(aosProductno + "-" + KW, "AVAILABLE");
                // 累计可用组个数
                productNoMap.put(aosProductno + "-" + KW, kw + 1);
            }
        }
        // 循环2 重新循环设置系列状态 只要存在一个可用即为可用
        for (DynamicObject aosEntryentity : aosEntryentityS) {
            String aosProductno = aosEntryentity.getString("aos_productno");
            if ("AVAILABLE".equals(aosProductno)) {
                continue; // 只判断不可用系列
            }
            if (productAvaMap.get(aosProductno) != null) {
                aosEntryentity.set("aos_serialstatus", "AVAILABLE");
            }
        }
        // 循环3 计算出价
        Map<String, BigDecimal> productNoBidMap = new HashMap<>(16);
        for (DynamicObject aosEntryentity : aosEntryentityS) {
            String aosGroupstatus = aosEntryentity.getString("aos_groupstatus");
            if (!"AVAILABLE".equals(aosGroupstatus)) {
                continue; // 只判断可用组
            }
            String aosKeystatus = aosEntryentity.getString("aos_keystatus");
            if (!"AVAILABLE".equals(aosKeystatus)) {
                continue; // 只判断可用词
            }
            long itemId = aosEntryentity.getLong("aos_itemid");
            String aosItemnumer = aosEntryentity.getString("aos_itemnumer");
            int aosAvadays = aosEntryentity.getInt("aos_avadays");
            String aosProductno = aosEntryentity.getString("aos_productno");
            String aosKeyword = aosEntryentity.getString("aos_keyword");
            String aosMatchType = aosEntryentity.getString("aos_match_type");
            BigDecimal aosRoi7days = aosEntryentity.getBigDecimal("aos_roi7days");
            BigDecimal aosImpressions = aosEntryentity.getBigDecimal("aos_impressions");
            String key = aosItemnumer + "~" + aosKeyword + "~" + aosMatchType;
            boolean isNewGroupFlag = false;
            Date aosGroupdate = aosEntryentity.getDate("aos_groupdate");
            if (aosGroupdate.equals(today)) {
                isNewGroupFlag = true;
            }
            BigDecimal highValue;
            // 建议价 报告中获取
            BigDecimal adviceValue = BigDecimal.ZERO;
            // 市场最高价 报告中获取
            BigDecimal maxValue = BigDecimal.ZERO;
            // 市场价 公式计算
            BigDecimal marketValue;
            // =====计算市场价=====
            // 组
            Map<String, Object> adPriceMap = adPriceKey.get(key);
            if (adPriceMap != null) {
                adviceValue = (BigDecimal)adPriceMap.get("aos_bid_suggest");
                maxValue = (BigDecimal)adPriceMap.get("aos_bid_rangeend");
            }
            // 定价 Amazon每日价格
            Object fixValue = dailyPrice.get(String.valueOf(itemId));
            // 最高标准 根据国别定价筛选
            BigDecimal highStandard = getHighStandard(fixValue, high1, high2, high3);
            // 最高出价 = MIN(市场价，最高标准)
            // 根据可售库存天数计算市场价
            if (aosAvadays < 90) {
                // 可售库存天数 < 90 市场价= min(建议价*0.7,最高标准)
                BigDecimal value1 = adviceValue.multiply(BigDecimal.valueOf(0.7));
                marketValue = Cux_Common_Utl.Min(value1, highStandard);
            } else if (aosAvadays <= 120) {
                // 可售库存天数 90-120 市场价=建议价
                marketValue = adviceValue;
            } else if (aosAvadays <= 180) {
                // 可售库存天数120-180 市场价=max(市场最高价*0.7,建议价)
                BigDecimal value1 = maxValue.multiply(BigDecimal.valueOf(0.7));
                marketValue = Cux_Common_Utl.Max(value1, adviceValue);
            } else {
                // 可售库存天数>180 市场价=市场最高价
                marketValue = maxValue;
            }
            // =====End计算市场价=====
            // 最高出价 = MIN(市场价，最高标准)
            highValue = Cux_Common_Utl.Min(marketValue, highStandard);
            BigDecimal aosBid;
            Object aosLastpricedate = null;
            Object aosLastbid = BigDecimal.ZERO;
            // 默认昨日数据
            Map<String, Object> ppcYesterMap = ppcYester.get(aosItemnumer + "~" + aosKeyword + "~" + aosMatchType);
            if (ppcYesterMap != null) {
                aosLastpricedate = ppcYesterMap.get("aos_lastpricedate");
                // 昨日出价
                aosLastbid = ppcYesterMap.get("aos_bid");
                if (aosLastpricedate == null) {
                    aosLastpricedate = today;
                }
            }
            if (isNewGroupFlag) {
                // 1.首次出价 新组或前一次为非可用组 首次出价规定：MIN(自动广告组市场价*0.7，国别均价)
                aosBid = Cux_Common_Utl.Min(marketValue.multiply(BigDecimal.valueOf(0.7)), popOrgFirstBid);
                // 判断为首次时需要调整出价日期
                aosLastpricedate = today;
            } else {
                // 3.出价3天内不调整 今天与最近出价日期做判断 且 不为 首次建组日后7天内
                // 上次出价 昨日出价缓存中获取
                BigDecimal lastBid = (BigDecimal)aosLastbid;
                Map<String, Object> getAdjustRateMap = new HashMap<>(16);
                // 七日词ROI
                getAdjustRateMap.put("aos_roi7days", aosRoi7days);
                // 七日词曝光
                getAdjustRateMap.put("aos_impressions", aosImpressions);
                getAdjustRateMap.put("PopOrgRoist", popOrgRoist);
                // 很差
                getAdjustRateMap.put("WORST", worst);
                // 差
                getAdjustRateMap.put("WORRY", worry);
                // 标准
                getAdjustRateMap.put("STANDARD", standard);
                // 好
                getAdjustRateMap.put("WELL", well);
                // 优
                getAdjustRateMap.put("EXCELLENT", excellent);
                Map<String, Object> adjustRateMap = getAdjustRate(getAdjustRateMap, aosMktBsadjrateSt);
                // 调整幅度 出价调整幅度参数表中获取
                BigDecimal adjustRate = (BigDecimal)adjustRateMap.get("AdjustRate");
                aosBid = lastBid.multiply(adjustRate.add(BigDecimal.valueOf(1)));
            }
            // 计算出价不能高于最高出价
            aosBid = Cux_Common_Utl.Min(aosBid, highValue);
            // 计算出价不能低于最低出价
            aosBid = Cux_Common_Utl.Max(aosBid, lowestbid);
            aosEntryentity.set("aos_bid", aosBid);
            aosEntryentity.set("aos_bid_ori", aosBid);
            aosEntryentity.set("aos_avadays", aosAvadays);
            aosEntryentity.set("aos_lastbid", aosLastbid);
            // 市场价
            aosEntryentity.set("aos_marketprice", marketValue);
            // 最高价
            aosEntryentity.set("aos_highvalue", highValue);
            // 定价
            aosEntryentity.set("aos_fixvalue", fixValue);
            // 上次出价调整日期
            aosEntryentity.set("aos_lastpricedate", aosLastpricedate);
            if (productNoBidMap.get(aosProductno) != null) {
                aosBid = aosBid.add(productNoBidMap.get(aosProductno));
            }
            productNoBidMap.put(aosProductno, aosBid);
        }
        // 新品
        Map<String, BigDecimal> newSerialMap = new HashMap<>(16);
        for (DynamicObject aosEntryentity : aosEntryentityS) {
            Date aosMakedate = aosEntryentity.getDate("aos_makedate");
            // =====是否新系列新组判断=====
            boolean isNewSerialFlag = aosMakedate.equals(today);
            // 新系列预算计算 = MAX(广告系列中SKU预测营收*AM营收占比*AM付费营收占比/国别标准ROI，2*广告系列中SKU个数)
            String aosProductno = aosEntryentity.getString("aos_productno");
            if (!isNewSerialFlag) {
                continue; // 非新品 直接跳过 只需要合计新品
            }
            Map<String, Object> summary = salSummary.get(aosProductno);
            if (summary != null) {
                // 广告系列中SKU预测营收
                BigDecimal aosSales = ((BigDecimal)salSummary.get(aosProductno).get("aos_sales"))
                    .divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
                // 词出价合计
                BigDecimal serialTotalBid = productNoBidMap.get(aosProductno);
                BigDecimal budget1 = aosSales.multiply(popOrgAmSaleRate).multiply(popOrgAmAffRate).divide(popOrgRoist,
                    2, RoundingMode.HALF_UP);
                BigDecimal budget2 = serialTotalBid.multiply(BigDecimal.valueOf(2));
                BigDecimal newBudget = Cux_Common_Utl.Max(budget1, budget2);
                newSerialMap.merge(aosProductno, newBudget, (a, b) -> b.add(a));
            }
        }
        // 循环4 计算预算 此时组系列词状态已定
        for (DynamicObject aosEntryentity : aosEntryentityS) {
            String aosSerialstatus = aosEntryentity.getString("aos_serialstatus");
            if (!"AVAILABLE".equals(aosSerialstatus)) {
                continue;// 系列剔除不计算出价与预算
            }
            String aosProductno = aosEntryentity.getString("aos_productno");
            Date aosMakedate = aosEntryentity.getDate("aos_makedate");
            boolean isNewSerialFlag = aosMakedate.equals(today);
            BigDecimal aosBudget = BigDecimal.ZERO;
            BigDecimal roi7DaysSerial = BigDecimal.ZERO;
            Map<String, Object> ppcYesterSerialMap = ppcYesterSerial.get(aosProductno);
            // Sku报告
            Map<String, Object> skuRptMapSerial = skuRptSerial.get(aosProductno);
            if (skuRptMapSerial != null
                && ((BigDecimal)skuRptMapSerial.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                roi7DaysSerial = ((BigDecimal)skuRptMapSerial.get("aos_total_sales"))
                    .divide((BigDecimal)skuRptMapSerial.get("aos_spend"), 2, RoundingMode.HALF_UP);
            }
            // 昨天ROI
            BigDecimal roiLast1Days = BigDecimal.ZERO;
            // 前天ROI
            BigDecimal roiLast2Days = BigDecimal.ZERO;
            // 大前天ROI
            BigDecimal roiLast3Days = BigDecimal.ZERO;
            // Sku报告
            Map<String, Map<String, Object>> skuRpt3SerialMap = skuRpt3Serial.get(aosProductno);
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
            // Sku报告1日数据
            Map<String, Object> skuRptDetailSerialMap = skuRptDetailSerial.get(aosProductno);
            // 系列花出率 = 前一天花出/预算
            if (skuRptDetailSerialMap != null && ppcYesterSerialMap != null) {
                BigDecimal lastSpend = (BigDecimal)skuRptDetailSerialMap.get("aos_spend");
                // 昨日预算
                BigDecimal lastBudget = (BigDecimal)ppcYesterSerialMap.get("aos_budget");
                if (lastBudget.compareTo(BigDecimal.ZERO) != 0) {
                    costRate = lastSpend.divide(lastBudget, 2, RoundingMode.HALF_UP);
                }
            }
            if (Cux_Common_Utl.GetBetweenDays(today, aosMakedate) <= 3) {
                // 全新系列3天内花出率最低为1
                costRate = Cux_Common_Utl.Max(costRate, BigDecimal.valueOf(1));
            }
            boolean cond = ((roi7DaysSerial.compareTo(popOrgRoist) < 0)
                && (((roiLast1Days.compareTo(popOrgRoist) >= 0) && (roiLast2Days.compareTo(popOrgRoist) >= 0))
                    || ((roiLast1Days.compareTo(popOrgRoist) >= 0) && (roiLast3Days.compareTo(popOrgRoist) >= 0))
                    || ((roiLast2Days.compareTo(popOrgRoist) >= 0) && (roiLast3Days.compareTo(popOrgRoist) >= 0))));
            if (isNewSerialFlag) {
                // a.新系列 预算=新系列预算
                // 新系列预算计算 = MAX(广告系列中SKU预测营收*AM营收占比*AM付费营收占比/国别标准ROI，2*广告系列中SKU个数)
                Map<String, Object> summary = salSummary.get(aosProductno);
                if (summary != null) {
                    // 广告系列中SKU预测营收
                    BigDecimal aosSales =
                        ((BigDecimal)summary.get("aos_sales")).divide(BigDecimal.valueOf(30), 2, RoundingMode.HALF_UP);
                    // 词出价合计
                    BigDecimal serialTotalBid = productNoBidMap.get(aosProductno);
                    BigDecimal budget1 = aosSales.multiply(popOrgAmSaleRate).multiply(popOrgAmAffRate)
                        .divide(popOrgRoist, 2, RoundingMode.HALF_UP);
                    BigDecimal budget2 = serialTotalBid.multiply(BigDecimal.valueOf(2));
                    aosBudget = Cux_Common_Utl.Max(budget1, budget2);
                }
            } else if (cond) {
                // b.当7天ROI＜国别标准时，前3天ROI中任意2天ROI≥国别标准ROI，则预算维持上次设置金额
                BigDecimal lastbudget = BigDecimal.ZERO;
                if (ppcYesterSerialMap != null) {
                    // 上次设置金额
                    lastbudget = (BigDecimal)ppcYesterSerialMap.get("aos_budget");
                }
                aosBudget = lastbudget;
            } else {
                // c.计算预算
                BigDecimal lastbudget = BigDecimal.ZERO;
                if (ppcYesterSerialMap != null) {
                    // 昨日设置金额
                    lastbudget = (BigDecimal)ppcYesterSerialMap.get("aos_budget");
                }
                Map<String, Object> getBudgetRateMap = new HashMap<>(16);
                String roiType = getRoiType(popOrgRoist, worst, worry, standard, well, excellent, roi7DaysSerial);
                // 花出率
                getBudgetRateMap.put("CostRate", costRate);
                getBudgetRateMap.put("RoiType", roiType);
                // 花出率获取对应预算调整系数
                Map<String, Object> budgetMap = getBudgetRate(getBudgetRateMap, aosMktBsadjparaS);
                BigDecimal budgetRate = (BigDecimal)budgetMap.get("BudgetRate");
                BigDecimal newSerial = BigDecimal.ZERO;
                BigDecimal aosBid = BigDecimal.ZERO;
                if (productNoBidMap.get(aosProductno) != null) {
                    aosBid = productNoBidMap.get(aosProductno);
                }
                if (newSerialMap.get(aosProductno) != null) {
                    newSerial = newSerialMap.get(aosProductno);
                }
                BigDecimal budget1 = lastbudget.multiply(budgetRate).add(newSerial);
                BigDecimal budget2 = aosBid.multiply(BigDecimal.valueOf(20));
                aosBudget = Cux_Common_Utl.Max(budget1, budget2);
            }
            aosEntryentity.set("aos_budget", Cux_Common_Utl.Max(aosBudget, BigDecimal.valueOf(2)));
            aosEntryentity.set("aos_budget_ori", Cux_Common_Utl.Max(aosBudget, BigDecimal.valueOf(2)));
        }
        aosMktBsadjrateSt.close();// 关闭Dataset
        aosMktBsadjparaS.close();
        // 生成ST数据表
        genDataTableSt(aosEntryentityS, aosMktPopPpcst.getPkValue(), pOrgId, today, aosBillno);
        aosEntryentityS.clear();
        // 保存ST单据
        SaveServiceHelper.save(new DynamicObject[] {aosMktPopPpcst});
        // 开始生成 出价调整(销售)
        Map<String, Object> adjs = new HashMap<>(16);
        adjs.put("p_ou_code", pOuCode);
        AosMktPopAdjstTask.executerun(adjs);
    }

    private static void genDataTableSt(DynamicObjectCollection aosEntryentityS, Object fid, Object pOrgId, Date today,
        String aosBillno) {
        int size = aosEntryentityS.size();
        int seq = 1;
        // 账单明细集合
        List<DynamicObject> dataS = new ArrayList<>();
        for (DynamicObject aosEntryentity : aosEntryentityS) {
            DynamicObject data = BusinessDataServiceHelper.newDynamicObject("aos_mkt_ppcst_data");
            data.set("aos_sourceid", fid);
            data.set("aos_orgid", pOrgId);
            data.set("aos_date", today);
            data.set("aos_productno", aosEntryentity.get("aos_productno"));
            data.set("aos_itemnumer", aosEntryentity.get("aos_itemnumer"));
            data.set("aos_shopsku", aosEntryentity.get("aos_shopsku"));
            data.set("aos_keyword", aosEntryentity.get("aos_keyword"));
            data.set("aos_match_type", aosEntryentity.get("aos_match_type"));
            data.set("aos_itemstatus", aosEntryentity.get("aos_itemstatus"));
            data.set("aos_type", aosEntryentity.get("aos_type"));
            data.set("aos_budget", aosEntryentity.get("aos_budget"));
            data.set("aos_bid", aosEntryentity.get("aos_bid"));
            data.set("aos_serialstatus", aosEntryentity.get("aos_serialstatus"));
            data.set("aos_groupstatus", aosEntryentity.get("aos_groupstatus"));
            data.set("aos_keystatus", aosEntryentity.get("aos_keystatus"));
            data.set("aos_valid_flag", aosEntryentity.get("aos_valid_flag"));
            data.set("aos_reason", aosEntryentity.get("aos_reason"));
            data.set("aos_makedate", aosEntryentity.get("aos_makedate"));
            data.set("aos_groupdate", aosEntryentity.get("aos_groupdate"));
            data.set("aos_keydate", aosEntryentity.get("aos_keydate"));
            data.set("aos_eliminatedate", aosEntryentity.get("aos_eliminatedate"));
            data.set("aos_lastpricedate", aosEntryentity.get("aos_lastpricedate"));
            data.set("aos_itemid", aosEntryentity.get("aos_itemid"));
            data.set("aos_itemname", aosEntryentity.get("aos_itemname"));
            data.set("aos_season", aosEntryentity.get("aos_season"));
            data.set("aos_sal_group", aosEntryentity.get("aos_sal_group"));
            data.set("aos_category1", aosEntryentity.get("aos_category1"));
            data.set("aos_category2", aosEntryentity.get("aos_category2"));
            data.set("aos_category3", aosEntryentity.get("aos_category3"));
            data.set("aos_budget_ori", aosEntryentity.get("aos_budget_ori"));
            data.set("aos_bid_ori", aosEntryentity.get("aos_bid_ori"));
            data.set("aos_avadays", aosEntryentity.get("aos_avadays"));
            data.set("aos_salemanual", aosEntryentity.get("aos_salemanual"));
            data.set("aos_roi7days", aosEntryentity.get("aos_roi7days"));
            data.set("aos_impressions", aosEntryentity.get("aos_impressions"));
            data.set("aos_lastbid", aosEntryentity.get("aos_lastbid"));
            data.set("aos_marketprice", aosEntryentity.get("aos_marketprice"));
            data.set("aos_highvalue", aosEntryentity.get("aos_highvalue"));
            data.set("aos_fixvalue", aosEntryentity.get("aos_fixvalue"));
            data.set("aos_billno", aosBillno);
            dataS.add(data);
            if (dataS.size() >= 5000 || seq == size) {
                DynamicObject[] dataArray = dataS.toArray(new DynamicObject[0]);
                SaveServiceHelper.save(dataArray);
                dataS.clear();
            }
            ++seq;
        }
    }

    private static void copyLastDayData(Object pOrgId, String aosBillno, Object aosPoptype, Date today,
        Object platformId) {
        DynamicObject aosMktPopPpcst = BusinessDataServiceHelper.newDynamicObject("aos_mkt_pop_ppcst");
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date dateTo = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, -1);
        Date dateFrom = calendar.getTime();
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterDateFrom = new QFilter("aos_date", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_date", "<", dateToStr);
        QFilter filterOrg = new QFilter("aos_orgid", "=", pOrgId);
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterOrg};
        long lastFid = QueryServiceHelper.queryOne("aos_mkt_pop_ppcst", "id", filters).getLong("id");
        DynamicObject aosMktPopPpclast = BusinessDataServiceHelper.loadSingle(lastFid, "aos_mkt_pop_ppcst");
        if (aosMktPopPpclast == null) {
            return;// 没有昨日数据则直接退出
        }
        aosMktPopPpcst.set("aos_orgid", pOrgId);
        aosMktPopPpcst.set("billstatus", "A");
        aosMktPopPpcst.set("aos_billno", aosBillno);
        aosMktPopPpcst.set("aos_poptype", aosPoptype);
        aosMktPopPpcst.set("aos_date", today);
        aosMktPopPpcst.set("aos_channel", platformId);
        SaveServiceHelper.save(new DynamicObject[] {aosMktPopPpcst});
        Object fid = aosMktPopPpcst.getPkValue();
        // 查询所有昨日数据
        DynamicObjectCollection aosMktPpcStDataS = QueryServiceHelper.query("aos_mkt_ppcst_data",
            "aos_productno,aos_itemnumer,aos_shopsku,aos_keyword,"
                + "aos_match_type,aos_itemstatus,aos_type,aos_budget,"
                + "aos_bid,aos_serialstatus,aos_groupstatus,aos_keystatus,"
                + "aos_valid_flag,aos_reason,aos_makedate,aos_groupdate,"
                + "aos_keydate,aos_eliminatedate,aos_lastpricedate,aos_itemid,"
                + "aos_itemname,aos_season,aos_sal_group,aos_category1,aos_category2,"
                + "aos_category3,aos_budget_ori,aos_bid_ori,aos_avadays,aos_salemanual,"
                + "aos_roi7days,aos_impressions,aos_lastbid,aos_marketprice," + "aos_highvalue,aos_fixvalue",
            new QFilter("aos_sourceid", QCP.equals, lastFid).toArray());
        int size = aosMktPpcStDataS.size();
        // 账单明细集合
        List<DynamicObject> dataS = new ArrayList<>();
        // 序列号
        int seq = 1;
        // 循环昨日数据生成本日数据
        for (DynamicObject aosMktPpcStData : aosMktPpcStDataS) {
            DynamicObject data = BusinessDataServiceHelper.newDynamicObject("aos_mkt_ppcst_data");
            data.set("aos_sourceid", fid);
            data.set("aos_orgid", pOrgId);
            data.set("aos_date", today);
            data.set("aos_productno", aosMktPpcStData.get("aos_productno"));
            data.set("aos_itemnumer", aosMktPpcStData.get("aos_itemnumer"));
            data.set("aos_shopsku", aosMktPpcStData.get("aos_shopsku"));
            data.set("aos_keyword", aosMktPpcStData.get("aos_keyword"));
            data.set("aos_match_type", aosMktPpcStData.get("aos_match_type"));
            data.set("aos_itemstatus", aosMktPpcStData.get("aos_itemstatus"));
            data.set("aos_type", aosMktPpcStData.get("aos_type"));
            data.set("aos_budget", aosMktPpcStData.get("aos_budget"));
            data.set("aos_bid", aosMktPpcStData.get("aos_bid"));
            data.set("aos_serialstatus", aosMktPpcStData.get("aos_serialstatus"));
            data.set("aos_groupstatus", aosMktPpcStData.get("aos_groupstatus"));
            data.set("aos_keystatus", aosMktPpcStData.get("aos_keystatus"));
            data.set("aos_valid_flag", aosMktPpcStData.get("aos_valid_flag"));
            data.set("aos_reason", aosMktPpcStData.get("aos_reason"));
            data.set("aos_makedate", aosMktPpcStData.get("aos_makedate"));
            data.set("aos_groupdate", aosMktPpcStData.get("aos_groupdate"));
            data.set("aos_keydate", aosMktPpcStData.get("aos_keydate"));
            data.set("aos_eliminatedate", aosMktPpcStData.get("aos_eliminatedate"));
            data.set("aos_lastpricedate", aosMktPpcStData.get("aos_lastpricedate"));
            data.set("aos_itemid", aosMktPpcStData.get("aos_itemid"));
            data.set("aos_itemname", aosMktPpcStData.get("aos_itemname"));
            data.set("aos_season", aosMktPpcStData.get("aos_season"));
            data.set("aos_sal_group", aosMktPpcStData.get("aos_sal_group"));
            data.set("aos_category1", aosMktPpcStData.get("aos_category1"));
            data.set("aos_category2", aosMktPpcStData.get("aos_category2"));
            data.set("aos_category3", aosMktPpcStData.get("aos_category3"));
            data.set("aos_budget_ori", aosMktPpcStData.get("aos_budget_ori"));
            data.set("aos_bid_ori", aosMktPpcStData.get("aos_bid_ori"));
            data.set("aos_avadays", aosMktPpcStData.get("aos_avadays"));
            data.set("aos_salemanual", aosMktPpcStData.get("aos_salemanual"));
            data.set("aos_roi7days", aosMktPpcStData.get("aos_roi7days"));
            data.set("aos_impressions", aosMktPpcStData.get("aos_impressions"));
            data.set("aos_lastbid", aosMktPpcStData.get("aos_lastbid"));
            data.set("aos_marketprice", aosMktPpcStData.get("aos_marketprice"));
            data.set("aos_highvalue", aosMktPpcStData.get("aos_highvalue"));
            data.set("aos_fixvalue", aosMktPpcStData.get("aos_fixvalue"));
            data.set("aos_billno", aosBillno);
            dataS.add(data);
            if (dataS.size() >= 5000 || seq == size) {
                DynamicObject[] dataArray = dataS.toArray(new DynamicObject[0]);
                SaveServiceHelper.save(dataArray);
                dataS.clear();
            }
            ++seq;
        }

    }

    private static void keyInsertData(DynamicObjectCollection aosEntryentityS, Map<String, Object> insertMap,
        String aosKeyword, String aosMatchType, Object aosKeydate) {
        DynamicObject aosEntryentity = aosEntryentityS.addNew();
        Object itemId = insertMap.get("aos_itemid");
        Object aosProductno = insertMap.get("aos_productno");
        Object aosItemnumer = insertMap.get("aos_itemnumer");
        Object aosShopsku = insertMap.get("aos_shopsku");
        Object aosMakedate = insertMap.get("aos_makedate");
        Object aosGroupdate = insertMap.get("aos_groupdate");
        Object aosEliminatedate = insertMap.get("aos_eliminatedate");
        Object aosItemname = insertMap.get("aos_itemname");
        Object aosSeason = insertMap.get("aos_season");
        Object aosAvadays = insertMap.get("aos_avadays");
        Object aosBid = insertMap.get("aos_bid");
        Object aosSalGroup = insertMap.get("aos_sal_group");
        // 产品大类
        Object aosCategory1 = insertMap.get("aos_category1");
        // 产品中类
        Object aosCategory2 = insertMap.get("aos_category2");
        // 产品小类
        Object aosCategory3 = insertMap.get("aos_category3");
        Object aosItemstatus = insertMap.get("aos_itemstatus");
        aosEntryentity.set("aos_itemid", itemId);
        aosEntryentity.set("aos_productno", aosProductno + "-" + KW);
        aosEntryentity.set("aos_itemnumer", aosItemnumer);
        aosEntryentity.set("aos_makedate", aosMakedate);
        aosEntryentity.set("aos_groupdate", aosGroupdate);
        aosEntryentity.set("aos_keydate", aosKeydate);
        aosEntryentity.set("aos_keystatus", "UNAVAILABLE");
        aosEntryentity.set("aos_groupstatus", "UNAVAILABLE");
        aosEntryentity.set("aos_serialstatus", "UNAVAILABLE");
        aosEntryentity.set("aos_keyword", aosKeyword);
        aosEntryentity.set("aos_match_type", aosMatchType);
        aosEntryentity.set("aos_shopsku", aosShopsku);
        aosEntryentity.set("aos_eliminatedate", aosEliminatedate);
        aosEntryentity.set("aos_itemname", aosItemname);
        aosEntryentity.set("aos_season", aosSeason);
        aosEntryentity.set("aos_avadays", aosAvadays);
        aosEntryentity.set("aos_bid", aosBid);
        aosEntryentity.set("aos_bid_ori", aosBid);
        aosEntryentity.set("aos_sal_group", aosSalGroup);
        aosEntryentity.set("aos_category1", aosCategory1);
        aosEntryentity.set("aos_category2", aosCategory2);
        aosEntryentity.set("aos_category3", aosCategory3);
        aosEntryentity.set("aos_itemstatus", aosItemstatus);
    }

    public static HashMap<String, Map<String, String>> generateItemPoint(Object pOrgId) {
        HashMap<String, Map<String, String>> itemPoint = new HashMap<>(16);
        // 品名关键词库
        DynamicObjectCollection aosMktItemPointS = QueryServiceHelper.query("aos_mkt_itempoint",
            "aos_category1," + "aos_category2,aos_category3," + "aos_itemname,aos_point",
            new QFilter("aos_orgid", QCP.equals, pOrgId).toArray());
        for (DynamicObject aosMktItemPoint : aosMktItemPointS) {
            String key = aosMktItemPoint.getString("aos_category1") + "~" + aosMktItemPoint.getString("aos_category2")
                + "~" + aosMktItemPoint.getString("aos_category3") + "~" + aosMktItemPoint.getString("aos_itemname");
            String point = aosMktItemPoint.getString("aos_point");
            Map<String, String> info = itemPoint.get(key);
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(point, point);
            itemPoint.put(key, info);
        }
        // SKU关键词库
        DynamicObjectCollection aosMktKeywordS = QueryServiceHelper.query("aos_mkt_keyword",
            "aos_category1," + "aos_category2,aos_category3," + "aos_itemname,aos_entryentity.aos_mainvoc aos_point",
            new QFilter("aos_orgid", QCP.equals, pOrgId).toArray());
        for (DynamicObject aosMktKeyword : aosMktKeywordS) {
            String key = aosMktKeyword.getString("aos_category1") + "~" + aosMktKeyword.getString("aos_category2") + "~"
                + aosMktKeyword.getString("aos_category3") + "~" + aosMktKeyword.getString("aos_itemname");
            String point = aosMktKeyword.getString("aos_point");
            if (FndGlobal.IsNull(point)) {
                continue;
            }
            Map<String, String> info = itemPoint.get(key);
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(point, point);
            itemPoint.put(key, info);
        }
        return itemPoint;
    }

    private static Map<String, Object> getAdjustRate(Map<String, Object> getAdjustRateMap, DataSet aosMktBsadjrateSt) {
        Map<String, Object> adjustRateMap = new HashMap<>(16);
        BigDecimal adjustRate = BigDecimal.ZERO;
        // 七日词ROI
        Object aosRoi7days = getAdjustRateMap.get("aos_roi7days");
        // 七日词曝光
        Object aosImpressions = getAdjustRateMap.get("aos_impressions");
        Object popOrgRoist = getAdjustRateMap.get("PopOrgRoist");
        // 很差
        Object worst = getAdjustRateMap.get("WORST");
        // 差
        Object worry = getAdjustRateMap.get("WORRY");
        // 标准
        Object standard = getAdjustRateMap.get("STANDARD");
        // 好
        Object well = getAdjustRateMap.get("WELL");
        // 优
        Object excellent = getAdjustRateMap.get("EXCELLENT");
        String[] orderBy = {"aos_level"};
        DataSet aosMktBsadjrateS = aosMktBsadjrateSt.copy().orderBy(orderBy);
        String rule = "";
        int aosLevel = 0;
        while (aosMktBsadjrateS.hasNext()) {
            Row mktBsadjrate = aosMktBsadjrateS.next();
            String rule1 = "";
            String rule2 = "";
            String aosRoitype = mktBsadjrate.getString("aos_roitype");
            if (FndGlobal.IsNotNull(aosRoitype)) {
                BigDecimal value = BigDecimal.ZERO;
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
                        break;
                }
                // ROI类型 要转化为对应的值
                BigDecimal roiValue = value.add((BigDecimal)popOrgRoist);
                rule1 = aosRoi7days + mktBsadjrate.getString("aos_roi") + roiValue;
            }
            String aosExposure = mktBsadjrate.getString("aos_exposure");
            if (FndGlobal.IsNotNull(aosExposure)) {
                if (FndGlobal.IsNotNull(rule1)) {
                    rule2 = "&&" + aosImpressions + aosExposure + mktBsadjrate.getBigDecimal("aos_exposurerate");
                } else {
                    rule2 = aosImpressions + aosExposure + mktBsadjrate.getBigDecimal("aos_exposurerate");
                }
            }
            rule = rule1 + rule2;
            boolean condition = Cux_Common_Utl.GetResult(rule);
            if (condition) {
                adjustRate = mktBsadjrate.getBigDecimal("aos_rate");
                aosLevel = mktBsadjrate.getInteger("aos_level");
                break;
            }
        }
        aosMktBsadjrateS.close();
        adjustRateMap.put("AdjustRate", adjustRate);
        adjustRateMap.put("rule", rule);
        adjustRateMap.put("aos_level", aosLevel);
        return adjustRateMap;
    }

    private static void insertData(DynamicObjectCollection aosEntryentityS, Map<String, Object> insertMap) {
        Object itemId = insertMap.get("aos_itemid");
        Object aosProductno = insertMap.get("aos_productno");
        Object aosItemnumer = insertMap.get("aos_itemnumer");
        Object aosShopsku = insertMap.get("aos_shopsku");
        Object aosMakedate = insertMap.get("aos_makedate");
        Object aosGroupdate = insertMap.get("aos_groupdate");
        Object aosEliminatedate = insertMap.get("aos_eliminatedate");
        Object aosItemname = insertMap.get("aos_itemname");
        Object aosSeason = insertMap.get("aos_season");
        Object aosReason = insertMap.get("aos_reason");
        Object aosAvadays = insertMap.get("aos_avadays");
        Object aosBid = insertMap.get("aos_bid");
        Object aosSalGroup = insertMap.get("aos_sal_group");
        // 产品大类
        Object aosCategory1 = insertMap.get("aos_category1");
        // 产品中类
        Object aosCategory2 = insertMap.get("aos_category2");
        // 产品小类
        Object aosCategory3 = insertMap.get("aos_category3");
        Object aosItemstatus = insertMap.get("aos_itemstatus");
        Object today = insertMap.get("Today");
        @SuppressWarnings("unchecked")
        Map<String, Object> keyRptDetail = (Map<String, Object>)insertMap.get("KeyRptDetail");
        @SuppressWarnings("unchecked")
        Map<String, Object> keyDateMap = (Map<String, Object>)insertMap.get("KeyDateMap");

        for (String aosTargetcomb : keyRptDetail.keySet()) {
            String aosKeyword = aosTargetcomb.split("~")[0];
            String aosMatchType = aosTargetcomb.split("~")[1];
            Object aosKeydate = null;
            if (keyDateMap != null) {
                aosKeydate = keyDateMap.get(aosItemnumer + "~" + aosTargetcomb);
            }
            if (aosKeydate == null) {
                aosKeydate = today;
            }
            DynamicObject aosEntryentity = aosEntryentityS.addNew();
            aosEntryentity.set("aos_itemid", itemId);
            aosEntryentity.set("aos_productno", aosProductno + "-" + KW);
            aosEntryentity.set("aos_itemnumer", aosItemnumer);
            aosEntryentity.set("aos_makedate", aosMakedate);
            aosEntryentity.set("aos_groupdate", aosGroupdate);
            aosEntryentity.set("aos_keydate", aosKeydate);
            aosEntryentity.set("aos_keystatus", "UNAVAILABLE");
            aosEntryentity.set("aos_groupstatus", "UNAVAILABLE");
            aosEntryentity.set("aos_serialstatus", "UNAVAILABLE");
            aosEntryentity.set("aos_keyword", aosKeyword);
            aosEntryentity.set("aos_match_type", aosMatchType);
            aosEntryentity.set("aos_shopsku", aosShopsku);
            aosEntryentity.set("aos_eliminatedate", aosEliminatedate);
            aosEntryentity.set("aos_itemname", aosItemname);
            aosEntryentity.set("aos_season", aosSeason);
            aosEntryentity.set("aos_avadays", aosAvadays);
            aosEntryentity.set("aos_bid", aosBid);
            aosEntryentity.set("aos_bid_ori", aosBid);
            aosEntryentity.set("aos_sal_group", aosSalGroup);
            aosEntryentity.set("aos_category1", aosCategory1);
            aosEntryentity.set("aos_category2", aosCategory2);
            aosEntryentity.set("aos_category3", aosCategory3);
            aosEntryentity.set("aos_itemstatus", aosItemstatus);
            aosEntryentity.set("aos_reason", aosReason);
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

    private static Map<String, Object> getBudgetRate(Map<String, Object> getBudgetRateMap, DataSet aosMktBsadjparaS) {
        Map<String, Object> budgetMap = new HashMap<>(16);
        BigDecimal budgetRate = BigDecimal.ZERO;
        String rule = "";
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
        budgetMap.put("BudgetRate", budgetRate);
        budgetMap.put("rule", rule);
        return budgetMap;
    }


    private static Set<String> generateManual(Object pOuCode) {
        // 第二部分 销售手动建组界面上周数据
        Calendar date = Calendar.getInstance();
        date.set(Calendar.HOUR_OF_DAY, 0);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);
        int weekOfYearLast = date.get(Calendar.WEEK_OF_YEAR) - 1;
        QFilter filterWeek = new QFilter("aos_weekofyear", QCP.equals, weekOfYearLast);
        QFilter filterStatus = new QFilter("aos_entryentity.aos_subentryentity.aos_keystatus", QCP.equals, "OFF");
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter[] filters = new QFilter[] {filterWeek, filterOrg, filterStatus};
        String selectColumn = "aos_entryentity.aos_itemid.number aos_ad_name,"
            + "aos_entryentity.aos_subentryentity.aos_keyword aos_keyword,"
            + "aos_entryentity.aos_subentryentity.aos_keytype aos_keytype";
        DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_pop_addst", selectColumn, filters);
        return list.stream().map(obj -> obj.getString("aos_ad_name") + "~" + obj.getString("aos_keyword") + "~"
            + obj.getString("aos_keytype")).collect(Collectors.toSet());
    }

    private static Set<String> generateWeek(Object pOuCode) {
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter filterType = new QFilter("aos_entryentity.aos_subentryentity.aos_valid", QCP.equals, true);
        QFilter[] filters = new QFilter[] {filterOrg, filterType};
        String selectColumn = "aos_entryentity.aos_itemid.number aos_ad_name,"
            + "aos_entryentity.aos_subentryentity.aos_keyword aos_keyword,"
            + "aos_entryentity.aos_subentryentity.aos_match_type aos_match_type";
        DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_pop_weekst", selectColumn, filters);
        return list.stream().map(obj -> obj.getString("aos_ad_name") + "~" + obj.getString("aos_keyword") + "~"
            + obj.getString("aos_match_type")).collect(Collectors.toSet());
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        executerun();// EU
    }

}
