package mkt.popularst.week;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import common.CommonDataSom;
import common.Cux_Common_Utl;
import common.fnd.FndGlobal;
import common.sal.util.InStockAvailableDays;
import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
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
import mkt.popular.AosMktPopUtil;
import mkt.popularst.ppc.AosMktPopPpcstTask;

/**
 * @author aosom
 * @version 销售周调整-调度任务类
 */
public class AosMktPopWeekTask extends AbstractTask {
    /** 系统管理员 **/
    public final static String SYSTEM = Cux_Common_Utl.SYSTEM;

    public static void executerun(int pHour) {
        Calendar today = Calendar.getInstance();
        int week = today.get(Calendar.DAY_OF_WEEK);
        boolean copyFlag = AosMktPopUtil.getCopyFlag("PPC_ST_WEEK", week);
        if (!copyFlag) {
            return;
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
        // 获取传入参数 国别
        Object pOuCode = params.get("p_ou_code");
        Object pOrgId = FndGlobal.get_import_id(pOuCode, "bd_country");
        // 删除数据
        Calendar date = Calendar.getInstance();
        date.set(Calendar.HOUR_OF_DAY, 0);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);
        Date today = date.getTime();
        int aosWeekofyear = date.get(Calendar.WEEK_OF_YEAR);
        String aosDayofweek = Cux_Common_Utl.WeekOfDay[Calendar.DAY_OF_WEEK - 1];
        QFilter filterId = new QFilter("aos_orgid.number", "=", pOuCode);
        QFilter filterDate = new QFilter("aos_makedate", "=", today);
        QFilter[] filtersSt = new QFilter[] {filterId, filterDate};
        DeleteServiceHelper.delete("aos_mkt_pop_weekst", filtersSt);
        // 初始化数据
        HashMap<String, Map<String, Object>> popOrgInfo = AosMktGenUtil.generatePopOrgInfo(pOuCode);
        BigDecimal exposure = (BigDecimal)popOrgInfo.get(pOrgId + "~" + "EXPOSURE").get("aos_value");
        HashMap<String, BigDecimal> keyRpt7GroupExp = AosMktGenUtil.generateKeyRpt7GroupExp(pOuCode);
        List<Map<String, Object>> itemQtyList = AosMktGenUtil.generateItemQtyList(pOuCode);
        // 海外库存
        Map<String, Object> itemOverseaQtyMap = itemQtyList.get(0);
        HashMap<String, Map<String, Object>> keyRpt7Map = AosMktGenUtil.generateKeyRpt7(pOuCode);
        HashMap<String, Map<String, String>> itemPoint = AosMktPopPpcstTask.generateItemPoint(pOrgId);
        // 物料词信息 关键词报告
        HashMap<String, Map<String, Object>> keyRpt = AosMktGenUtil.generateKeyRpt(pOrgId);
        filterDate = new QFilter("aos_date", "=", today);
        QFilter filterPpcorg = new QFilter("aos_orgid.number", "=", pOuCode);
        QFilter filterAva = new QFilter("aos_keystatus", "=", "AVAILABLE");
        QFilter[] filtersPpc = new QFilter[] {filterDate, filterPpcorg, filterAva};
        String selectColumn = "aos_itemnumer,aos_itemid,aos_itemname,aos_season,1 aos_count";
        DataSet aosMktPopPpcstS = QueryServiceHelper.queryDataSet("aos_mkt_popweek_init.ppcst", "aos_mkt_ppcst_data",
            selectColumn, filtersPpc, null);
        String[] groupBy = new String[] {"aos_itemid", "aos_itemnumer", "aos_itemname", "aos_season"};
        aosMktPopPpcstS = aosMktPopPpcstS.groupBy(groupBy).sum("aos_count").finish();
        // 循环销售组别 海外公司
        QFilter groupOrg = new QFilter("aos_org.number", "=", pOuCode);
        // 可用
        QFilter groupEnable = new QFilter("enable", "=", 1);
        QFilter[] filtersGroup = new QFilter[] {groupOrg, groupEnable};
        DynamicObjectCollection aosSalgroup = QueryServiceHelper.query("aos_salgroup", "id", filtersGroup);
        for (DynamicObject group : aosSalgroup) {
            // 循环今日生成的PPCST数据
            DynamicObject aosMktPopWeekst = BusinessDataServiceHelper.newDynamicObject("aos_mkt_pop_weekst");
            aosMktPopWeekst.set("aos_orgid", pOrgId);
            aosMktPopWeekst.set("billstatus", "A");
            aosMktPopWeekst.set("aos_statush", "A");
            aosMktPopWeekst.set("aos_weekofyear", aosWeekofyear);
            aosMktPopWeekst.set("aos_dayofweek", aosDayofweek);
            aosMktPopWeekst.set("aos_makedate", today);
            aosMktPopWeekst.set("aos_makeby", SYSTEM);
            aosMktPopWeekst.set("aos_groupid", group.get("id"));
            DynamicObjectCollection aosEntryentityS = aosMktPopWeekst.getDynamicObjectCollection("aos_entryentity");
            DataSet copy = aosMktPopPpcstS.copy();
            while (copy.hasNext()) {
                String groupId = group.getString("id");
                Row aosMktPopPpcst = copy.next();
                long itemId = aosMktPopPpcst.getLong("aos_itemid");
                // 销售组别
                String itemCategoryId = CommonDataSom.getItemCategoryId(String.valueOf(itemId));
                if (itemCategoryId == null || "".equals(itemCategoryId)) {
                    continue;
                }
                // 小类获取组别
                String salOrg = CommonDataSom.getSalOrgV2(String.valueOf(pOrgId), itemCategoryId);
                if (salOrg == null || "".equals(salOrg)) {
                    continue;
                }
                if (!salOrg.equals(groupId)) {
                    continue;
                }
                String orgidStr = String.valueOf(pOrgId);
                String itemidStr = Long.toString(itemId);
                int aosCount = aosMktPopPpcst.getInteger("aos_count");
                String aosItemnumer = aosMktPopPpcst.getString("aos_itemnumer");
                String aosItemname = aosMktPopPpcst.getString("aos_itemname");
                String aosSeason = aosMktPopPpcst.getString("aos_season");
                BigDecimal aosImpressions = BigDecimal.ZERO;
                if (keyRpt7GroupExp.get(aosItemnumer) != null) {
                    aosImpressions = keyRpt7GroupExp.get(aosItemnumer);
                }
                if (aosCount > 10) {
                    // 本期计算之后广告组可用词<=10个词
                    continue;
                }
                if (aosImpressions.compareTo(exposure.multiply(BigDecimal.valueOf(0.7))) >= 0) {
                    // 最近7天组SKU曝光<自动广告组曝光标准*0.7
                    continue;
                }
                // 海外库存
                Object itemOverseaqty = itemOverseaQtyMap.get(String.valueOf(itemId));
                if (FndGlobal.IsNull(itemOverseaqty)) {
                    itemOverseaqty = 0;
                }
                String category = (String)SalUtil.getCategoryByItemId(String.valueOf(itemId)).get("name");
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
                int availableDays = InStockAvailableDays.calInstockSalDays(orgidStr, itemidStr);
                DynamicObject aosEntryentity = aosEntryentityS.addNew();
                aosEntryentity.set("aos_itemid", itemId);
                aosEntryentity.set("aos_itemname", aosItemname);
                aosEntryentity.set("aos_season", aosSeason);
                aosEntryentity.set("aos_keyqty", aosCount);
                aosEntryentity.set("aos_avaqty", availableDays);
                aosEntryentity.set("aos_osqty", itemOverseaqty);
                aosEntryentity.set("aos_category1", aosCategory1);
                aosEntryentity.set("aos_category2", aosCategory2);
                aosEntryentity.set("aos_category3", aosCategory3);
                aosEntryentity.set("aos_itemnumer", aosItemnumer);
            }
            copy.close();
            // 子单据体
            for (DynamicObject aosEntryentity : aosEntryentityS) {
                DynamicObjectCollection aosSubentryentityS =
                    aosEntryentity.getDynamicObjectCollection("aos_subentryentity");
                String aosItemnumer = aosEntryentity.getString("aos_itemnumer");
                String aosCategory1 = aosEntryentity.getString("aos_category1");
                String aosCategory2 = aosEntryentity.getString("aos_category2");
                String aosCategory3 = aosEntryentity.getString("aos_category3");
                String aosItemname = aosEntryentity.getString("aos_itemname");
                Map<String, Object> keyRptDetail = keyRpt.get(aosItemnumer);
                if (FndGlobal.IsNull(keyRptDetail)) {
                    keyRptDetail = new HashMap<String, Object>(16);
                }
                // 给词信息添加 物料品类关键词信息
                Map<String, String> itemPointDetail =
                    itemPoint.get(aosCategory1 + "~" + aosCategory2 + "~" + aosCategory3 + "~" + aosItemname);
                if (FndGlobal.IsNull(itemPointDetail)) {
                    itemPointDetail = new HashMap<>(16);
                }
                for (String key : itemPointDetail.keySet()) {
                    keyRptDetail.put(key + "~PHRASE", key + "~PHRASE");
                    keyRptDetail.put(key + "~EXACT", key + "~EXACT");
                }
                if (keyRptDetail != null) {
                    for (String aosTargetcomb : keyRptDetail.keySet()) {
                        String aosKeyword = aosTargetcomb.split("~")[0];
                        String aosMatchType = aosTargetcomb.split("~")[1];
                        DynamicObject aosSubentryentity = aosSubentryentityS.addNew();
                        aosSubentryentity.set("aos_keyword", aosKeyword);
                        aosSubentryentity.set("aos_match_type", aosMatchType);
                        aosSubentryentity.set("aos_valid", true);
                        aosSubentryentity.set("aos_status", "paused");
                        String key = aosItemnumer + "~" + aosTargetcomb;
                        if (keyRpt7Map.get(key) != null) {
                            Map<String, Object> keyRpt7MapD = keyRpt7Map.get(key);
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
                                aosSubentryentity.set("aos_express", aosImpressions);
                                aosSubentryentity.set("aos_roi", keyRpt7MapD.get("aos_roi"));
                                aosSubentryentity.set("aos_rate", aosExprate);
                            }
                        }
                    }
                }
            }
            // 保存数据
            OperationServiceHelper.executeOperate("save", "aos_mkt_pop_weekst", new DynamicObject[] {aosMktPopWeekst},
                OperateOption.create());
        }
        aosMktPopPpcstS.close();
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {}
}
