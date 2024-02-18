package mkt.popularst.add;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import common.CommonDataSom;
import common.Cux_Common_Utl;
import common.fnd.FndGlobal;
import common.fnd.FndLog;
import common.sal.util.InStockAvailableDays;
import common.sal.util.SalUtil;
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
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import mkt.common.AosMktGenUtil;
import mkt.common.MktComUtil;
import mkt.popular.AosMktPopUtil;

/**
 * @author aosom
 */
public class AosMktPopAddsTask extends AbstractTask {

    /** 系统管理员 **/
    public final static String SYSTEM = Cux_Common_Utl.SYSTEM;

    public static void executerun(int pHour) {
        Calendar today = Calendar.getInstance();
        int week = today.get(Calendar.DAY_OF_WEEK);
        boolean copyFlag = AosMktPopUtil.getCopyFlag("PPC_ST_ADD", week);
        if (!copyFlag) {
            return;// 如果不是周一 直接跳过
        }
        QFilter qfTime;
        DynamicObject dynamicObject = QueryServiceHelper.queryOne("aos_mkt_base_orgvalue", "aos_value",
            new QFilter[] {new QFilter("aos_type", QCP.equals, "TIME")});
        int time = 16;
        if (dynamicObject != null) {
            time = dynamicObject.getBigDecimal("aos_value").intValue();
        }
        if (pHour < time) {
            qfTime = new QFilter("aos_is_north_america.number", QCP.not_equals, "Y");
        } else {
            qfTime = new QFilter("aos_is_north_america.number", QCP.equals, "Y");
        }
        // 调用线程池
        QFilter overseaFlag = new QFilter("aos_is_oversea_ou.number", "=", "Y");
        QFilter overseaFlag2 = new QFilter("aos_isomvalid", "=", true);
        QFilter[] filtersOu = new QFilter[] {overseaFlag, overseaFlag2, qfTime};
        DynamicObjectCollection bdCountry = QueryServiceHelper.query("bd_country", "id,number", filtersOu);
        for (DynamicObject ou : bdCountry) {
            String pOuCode = ou.getString("number");
            Map<String, Object> params = new HashMap<>(16);
            params.put("p_ou_code", pOuCode);
            doOperate(params);
        }
    }

    public static void doOperate(Map<String, Object> params) {
        // 传入参数
        Object pOuCode = params.get("p_ou_code");
        Object pOrgId = FndGlobal.get_import_id(pOuCode, "bd_country");
        long orgId = (long)pOrgId;
        String orgidStr = pOrgId.toString();
        // 删除数据
        Calendar date = Calendar.getInstance();
        date.set(Calendar.HOUR_OF_DAY, 0);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);
        Date today = date.getTime();
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        String todayStr = writeFormat.format(today);
        // 当前月
        int month = date.get(Calendar.MONTH) + 1;
        int aosWeekofyear = date.get(Calendar.WEEK_OF_YEAR);
        String aosDayofweek = Cux_Common_Utl.WeekOfDay[Calendar.DAY_OF_WEEK - 1];
        QFilter filterId = new QFilter("aos_orgid.number", "=", pOuCode);
        QFilter filterDate = new QFilter("aos_makedate", "=", today);
        QFilter[] filtersSt = new QFilter[] {filterId, filterDate};
        DeleteServiceHelper.delete("aos_mkt_pop_addst", filtersSt);
        // 通用数据
        HashMap<String, Object> ppcStLastMap = AosMktGenUtil.generatePpcStLast(pOuCode);
        List<Map<String, Object>> itemQtyList = AosMktGenUtil.generateItemQtyList(pOuCode);
        HashMap<String, Map<String, Object>> keyRpt7Map = AosMktGenUtil.generateKeyRpt7(pOuCode);
        // 海外库存
        Map<String, Object> itemOverseaQtyMap = itemQtyList.get(0);
        // 在途数量
        Map<String, Object> itemIntransQtyMap = itemQtyList.get(1);
        // 全程天数信息
        List<Integer> mapList = AosMktGenUtil.generateShpDay(pOuCode);
        // 备货天数
        int aosShpDay = mapList.get(0);
        // 海运天数
        int aosFreightDay = mapList.get(1);
        // 清关天数
        int aosClearDay = mapList.get(2);
        Date summerSpringStart = MktComUtil.getDateRange("aos_datefrom", "SS", pOrgId);
        Date summerSpringEnd = MktComUtil.getDateRange("aos_dateto", "SS", pOrgId);
        Date autumnWinterStart = MktComUtil.getDateRange("aos_datefrom", "AW", pOrgId);
        Date autumnWinterEnd = MktComUtil.getDateRange("aos_dateto", "AW", pOrgId);
        Set<String> specialSet = getSpecial(orgidStr, todayStr);
        HashMap<String, Map<String, String>> itemPoint = generateItemPoint(pOrgId);
        // 查询国别物料
        QFilter filterOu = new QFilter("aos_contryentry.aos_nationality.number", "=", pOrgId);
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
        // 查询销售组别
        QFilter groupOrg = new QFilter("aos_org.number", "=", pOuCode);
        QFilter groupEnable = new QFilter("enable", "=", 1);
        QFilter[] filtersGroup = new QFilter[] {groupOrg, groupEnable};
        DynamicObjectCollection aosSalgroup = QueryServiceHelper.query("aos_salgroup", "id", filtersGroup);
        FndLog log = FndLog.init("MKT_PPC_ST手动建组", pOuCode.toString());
        // 销售组别循环
        for (DynamicObject group : aosSalgroup) {
            String groupId = group.getString("id");
            // 初始化头数据
            DynamicObject aosMktPopAddst = BusinessDataServiceHelper.newDynamicObject("aos_mkt_pop_addst");
            aosMktPopAddst.set("aos_orgid", pOrgId);
            aosMktPopAddst.set("billstatus", "A");
            aosMktPopAddst.set("aos_status", "A");
            aosMktPopAddst.set("aos_weekofyear", aosWeekofyear);
            aosMktPopAddst.set("aos_dayofweek", aosDayofweek);
            aosMktPopAddst.set("aos_makedate", today);
            aosMktPopAddst.set("aos_makeby", SYSTEM);
            aosMktPopAddst.set("aos_groupid", groupId);
            DynamicObjectCollection aosEntryentityS = aosMktPopAddst.getDynamicObjectCollection("aos_entryentity");
            // 国别物料循环
            for (DynamicObject bdMaterial : bdMaterialS) {
                // 物料编码
                String aosItemnumer = bdMaterial.getString("number");
                // ID
                long itemId = bdMaterial.getLong("id");
                String itemidStr = Long.toString(itemId);
                // 产品类别
                String itemCategoryId = CommonDataSom.getItemCategoryId(String.valueOf(itemId));
                if (itemCategoryId == null || "".equals(itemCategoryId)) {
                    log.add(aosItemnumer + "产品类别不存在");
                    continue;
                }
                // 销售组别 小类获取组别
                String salOrg = CommonDataSom.getSalOrgV2(String.valueOf(pOrgId), itemCategoryId);
                if (salOrg == null || "".equals(salOrg)) {
                    log.add(aosItemnumer + "销售组别不存在");
                    continue;
                }
                if (!salOrg.equals(groupId)) {
                    continue;
                }
                // 物料品名
                String aosCnName = bdMaterial.getString("name");
                // 季节属性
                String aosSeasonpro = bdMaterial.getString("aos_seasonseting");
                if (aosSeasonpro == null) {
                    aosSeasonpro = "";
                }
                Object aosSeasonid = bdMaterial.get("aos_seasonid");
                if (aosSeasonid == null) {
                    continue;
                }
                // 海外库存
                Object itemOverseaqty = itemOverseaQtyMap.get(itemidStr);
                if (FndGlobal.IsNull(itemOverseaqty)) {
                    itemOverseaqty = 0;
                }
                // 在途数量
                Object itemIntransqty = itemIntransQtyMap.get(itemidStr);
                if (FndGlobal.IsNull(itemIntransqty)) {
                    itemIntransqty = 0;
                }
                // 可售天数
                Map<String, Object> dayMap = new HashMap<>(16);
                int availableDays = InStockAvailableDays.calInstockSalDaysForDayMin(orgidStr, itemidStr, dayMap);
                // 特殊广告货号过滤
                if (!specialSet.contains(aosItemnumer)) {
                    log.add(aosItemnumer + "不为今日SP特殊广告");
                    continue;
                }
                if ("REGULAR".equals(aosSeasonpro)) {
                    // 常规品过滤
                    if ((int)itemOverseaqty <= 50) {
                        log.add(aosItemnumer + "常规品海外库存必须大于50");
                        continue;
                    }
                    if (ppcStLastMap.get(itemidStr) != null) {
                        log.add(aosItemnumer + "昨日PPCST中不能存在");
                        continue;
                    }
                    if ((availableDays < 30) && (MktComUtil.isPreSaleOut(orgId, itemId, (int)itemIntransqty, aosShpDay,
                        aosFreightDay, aosClearDay, availableDays))) {
                        log.add(aosItemnumer + " 非预断货");
                        continue;
                    }
                } else {
                    // 季节品过滤
                    boolean cond = (("SPRING".equals(aosSeasonpro) || "SPRING_SUMMER".equals(aosSeasonpro)
                        || "SUMMER".equals(aosSeasonpro))
                        && (today.before(summerSpringStart) || today.after(summerSpringEnd)));
                    if (cond) {
                        log.add(aosItemnumer + "剔除过季品");
                        continue;
                    }
                    cond = (("AUTUMN_WINTER".equals(aosSeasonpro) || "WINTER".equals(aosSeasonpro))
                        && (today.before(autumnWinterStart) || today.after(autumnWinterEnd)));
                    if (cond) {
                        log.add(aosItemnumer + "剔除过季品");
                        continue;
                    }
                    if ((int)itemOverseaqty <= 50) {
                        log.add(aosItemnumer + "季节品海外库存必须大于50");
                        continue;
                    }
                    float seasonRate = MktComUtil.getSeasonRate(orgId, itemId, aosSeasonpro, itemOverseaqty, month);
                    cond = (!(ppcStLastMap.get(itemidStr) == null
                        || (seasonRate < 0.9 && !MktComUtil.isSeasonRate(aosSeasonpro, month, seasonRate))));
                    if (cond) {
                        log.add(aosItemnumer + " 累计完成率" + seasonRate);
                        continue;
                    }
                }
                // 大中小类
                String category = (String)SalUtil.getCategoryByItemId(itemidStr).get("name");
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
                Map<String, String> itemPointDetail =
                    itemPoint.get(aosCategory1 + "~" + aosCategory2 + "~" + aosCategory3 + "~" + aosCnName);

                if (FndGlobal.IsNull(itemPointDetail)) {
                    continue;
                }
                // 行明细
                DynamicObject aosEntryentity = aosEntryentityS.addNew();
                aosEntryentity.set("aos_itemid", itemId);
                aosEntryentity.set("aos_itemname", aosCnName);
                aosEntryentity.set("aos_category1", aosCategory1);
                aosEntryentity.set("aos_category2", aosCategory2);
                aosEntryentity.set("aos_category3", aosCategory3);
                aosEntryentity.set("aos_avaqty", availableDays);
                aosEntryentity.set("aos_osqty", itemOverseaqty);
                aosEntryentity.set("aos_season", aosSeasonid);
                aosEntryentity.set("aos_contryentrystatus", bdMaterial.get("aos_contryentrystatus"));
                aosEntryentity.set("aos_special", true);
                DynamicObjectCollection aosSubentryentityS =
                    aosEntryentity.getDynamicObjectCollection("aos_subentryentity");
                for (String key : itemPointDetail.keySet()) {
                    // EXACT
                    DynamicObject aosSubentryentity = aosSubentryentityS.addNew();
                    aosSubentryentity.set("aos_keyword", key);
                    aosSubentryentity.set("aos_match", "EXACT");
                    aosSubentryentity.set("aos_keytype", "特定");
                    aosSubentryentity.set("aos_keysource", itemPointDetail.get(key));
                    aosSubentryentity.set("aos_keystatus", "ON");
                    if (keyRpt7Map.get(aosItemnumer + "~" + key) != null) {
                        Map<String, Object> keyRpt7MapD = keyRpt7Map.get(aosItemnumer + "~" + key + "~EXACT");
                        if (keyRpt7MapD != null) {
                            BigDecimal aosExprate = BigDecimal.ZERO;
                            BigDecimal aosImpressions;
                            BigDecimal aosClicks;
                            aosImpressions = (BigDecimal)keyRpt7MapD.get("aos_impressions");
                            aosClicks = (BigDecimal)keyRpt7MapD.get("aos_clicks");
                            if (aosImpressions.compareTo(BigDecimal.ZERO) != 0) {
                                aosExprate = aosClicks.divide(aosImpressions, 6, RoundingMode.HALF_UP);
                            }
                            aosSubentryentity.set("aos_click", aosClicks);
                            aosSubentryentity.set("aos_spend", keyRpt7MapD.get("aos_spend"));
                            aosSubentryentity.set("aos_exposure", aosImpressions);
                            aosSubentryentity.set("aos_roi", keyRpt7MapD.get("aos_roi"));
                            aosSubentryentity.set("aos_rate", aosExprate);
                        }
                    }
                    // PHRASE
                    aosSubentryentity = aosSubentryentityS.addNew();
                    aosSubentryentity.set("aos_keyword", key);
                    aosSubentryentity.set("aos_match", "PHRASE");
                    aosSubentryentity.set("aos_keytype", "特定");
                    aosSubentryentity.set("aos_keysource", itemPointDetail.get(key));
                    aosSubentryentity.set("aos_keystatus", "ON");
                    if (keyRpt7Map.get(aosItemnumer + "~" + key) != null) {
                        Map<String, Object> keyRpt7MapD = keyRpt7Map.get(aosItemnumer + "~" + key + "~PHRASE");
                        if (keyRpt7MapD != null) {
                            BigDecimal aosExprate = BigDecimal.ZERO;
                            BigDecimal aosImpressions;
                            BigDecimal aosClicks;
                            aosImpressions = (BigDecimal)keyRpt7MapD.get("aos_impressions");
                            aosClicks = (BigDecimal)keyRpt7MapD.get("aos_clicks");
                            if (aosImpressions.compareTo(BigDecimal.ZERO) != 0) {
                                aosExprate = aosClicks.divide(aosImpressions, 6, RoundingMode.HALF_UP);
                            }
                            aosSubentryentity.set("aos_click", aosClicks);
                            aosSubentryentity.set("aos_spend", keyRpt7MapD.get("aos_spend"));
                            aosSubentryentity.set("aos_exposure", aosImpressions);
                            aosSubentryentity.set("aos_roi", keyRpt7MapD.get("aos_roi"));
                            aosSubentryentity.set("aos_rate", aosExprate);
                        }
                    }
                }
            }
            // 保存数据
            OperationServiceHelper.executeOperate("save", "aos_mkt_pop_addst", new DynamicObject[] {aosMktPopAddst},
                OperateOption.create());
            // 保存日志表
            log.finnalSave();
        }
    }

    public static HashMap<String, Map<String, String>> generateItemPoint(Object pOrgId) {
        HashMap<String, Map<String, String>> itemPoint = new HashMap<>(16);
        // 品名关键词库
        DynamicObjectCollection aosMktItempointS = QueryServiceHelper.query("aos_mkt_point",
            "aos_category1," + "aos_category2,aos_category3,"
                + "aos_itemnamecn aos_itemname, aos_linentity.aos_keyword aos_keyword",
            new QFilter("aos_orgid", QCP.equals, pOrgId).toArray());
        for (DynamicObject aosMktItempoint : aosMktItempointS) {
            String key = aosMktItempoint.getString("aos_category1") + "~" + aosMktItempoint.getString("aos_category2")
                + "~" + aosMktItempoint.getString("aos_category3") + "~" + aosMktItempoint.getString("aos_itemname");
            String point = aosMktItempoint.getString("aos_point");
            Map<String, String> info = itemPoint.get(key);
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(point, "F");
            itemPoint.put(key, info);
        }
        // SKU关键词库
        DynamicObjectCollection aosMktKeywordS = QueryServiceHelper.query("aos_mkt_keyword",
            "aos_category1," + "aos_category2,aos_category3,"
                + "aos_itemname,aos_entryentity1.aos_pr_keyword aos_point",
            new QFilter("aos_orgid", QCP.equals, pOrgId).toArray());
        for (DynamicObject aosMktKeyword : aosMktKeywordS) {
            String key = aosMktKeyword.getString("aos_category1") + "~" + aosMktKeyword.getString("aos_category2") + "~"
                + aosMktKeyword.getString("aos_category3") + "~" + aosMktKeyword.getString("aos_itemname");
            String point = aosMktKeyword.getString("aos_point");
            Map<String, String> info = itemPoint.get(key);
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(point, "S");
            itemPoint.put(key, info);
        }
        return itemPoint;
    }

    private static Set<String> getSpecial(String aosOrgid, String todayStr) {
        DynamicObjectCollection list =
            QueryServiceHelper.query("aos_mkt_popular_ppc", "aos_entryentity.aos_itemnumer aos_itemnumer",
                new QFilter("aos_orgid", QCP.equals, aosOrgid).and("aos_date", QCP.large_equals, todayStr)
                    .and("aos_entryentity.aos_special", QCP.equals, true).toArray());
        return list.stream().map(obj -> obj.getString("aos_itemnumer")).collect(Collectors.toSet());
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {}

}
