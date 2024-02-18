package mkt.common;

import common.fnd.FndDate;
import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author aosom
 * @version 推广数据查询工具类
 */
public class AosMktGenUtil {

    /**
     * 根据国别查询店铺货号
     * 
     * @param pOuCode 国别
     * @return productInfo
     */
    public static HashMap<String, String> generateShopSku(Object pOuCode) {
        HashMap<String, String> productInfo = new HashMap<>(16);
        QFilter filterAma = new QFilter("aos_platformfid.number", "=", "AMAZON");
        QFilter filterMainshop = new QFilter("aos_shopfid.aos_is_mainshop", "=", true);
        QFilter[] filters = new QFilter[] {filterAma, filterMainshop};
        String selectColumn = "aos_item_code aos_itemid," + "aos_shopsku aos_productid";
        DynamicObjectCollection bdMaterialS = QueryServiceHelper.query("aos_sync_invprice", selectColumn, filters);
        for (DynamicObject bdMaterial : bdMaterialS) {
            productInfo.put(bdMaterial.getString("aos_itemid"), bdMaterial.getString("aos_productid"));
        }
        return productInfo;
    }

    /**
     * 根据国别编码查询物料信息
     * 
     * @param pOuCode 国别编码
     * 
     * @return itemQtyList
     */
    public static List<Map<String, Object>> generateItemQtyList(Object pOuCode) {
        List<Map<String, Object>> itemQtyList = new ArrayList<>();
        Map<String, Object> itemOverseaqty = new HashMap<>(16);
        Map<String, Object> itemIntransqty = new HashMap<>(16);
        QFilter filterOrg = new QFilter("aos_ou.number", QCP.equals, pOuCode);
        QFilter[] filters = new QFilter[] {filterOrg};
        DynamicObjectCollection aosSyncInvouValueS = QueryServiceHelper.query("aos_sync_invou_value",
            "aos_item.id aos_itemid,(aos_noplatform_qty+aos_fba_qty) as aos_instock_qty,aos_intrans_qty", filters);
        for (DynamicObject aosSyncInvouValue : aosSyncInvouValueS) {
            itemOverseaqty.put(aosSyncInvouValue.getString("aos_itemid"), aosSyncInvouValue.getInt("aos_instock_qty"));
            itemIntransqty.put(aosSyncInvouValue.getString("aos_itemid"), aosSyncInvouValue.getInt("aos_intrans_qty"));
        }
        // 0 海外库存数量
        itemQtyList.add(itemOverseaqty);
        // 1 在途数量
        itemQtyList.add(itemIntransqty);
        return itemQtyList;
    }

    /**
     * 根据国别编码查询全程天数信息
     * 
     * @param pOuCode 国别编码
     * @return mapList
     */
    public static List<Integer> generateShpDay(Object pOuCode) {
        List<Integer> mapList = new ArrayList<>();
        QFilter filterOrg = new QFilter("aos_org.number", QCP.equals, pOuCode);
        QFilter[] filters = new QFilter[] {filterOrg};
        DynamicObjectCollection aosScmFcorderParaS =
            QueryServiceHelper.query("aos_scm_fcorder_para", "aos_shp_day,aos_clear_day,aos_freight_day", filters);
        for (DynamicObject aosScmFcorderPara : aosScmFcorderParaS) {
            // 0 备货天数
            mapList.add(aosScmFcorderPara.getInt("aos_shp_day"));
            // 1 海运天数
            mapList.add(aosScmFcorderPara.getInt("aos_freight_day"));
            // 2 清关天数
            mapList.add(aosScmFcorderPara.getInt("aos_clear_day"));
        }
        return mapList;
    }

    /**
     * 根据国别编码查询词创建日期
     * 
     * @param pOuCode 国别编码
     * @return keyDateMap
     */
    public static HashMap<String, Object> generateLastKeyDate(Object pOuCode) {
        HashMap<String, Object> keyDateMap = new HashMap<>(16);
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter[] filters = new QFilter[] {filterOrg};
        String selectColumn = "aos_itemnumer,aos_keyword,aos_keydate,aos_match_type";
        DataSet aosMktPopPpcstS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateLastKeyDate" + pOuCode,
            "aos_mkt_ppcst_data", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_itemnumer", "aos_keyword", "aos_match_type"};
        aosMktPopPpcstS = aosMktPopPpcstS.groupBy(groupBy).min("aos_keydate").finish();
        while (aosMktPopPpcstS.hasNext()) {
            Row aosMktPopPpcst = aosMktPopPpcstS.next();
            keyDateMap.put(aosMktPopPpcst.getString("aos_itemnumer") + "~" + aosMktPopPpcst.getString("aos_keyword")
                + "~" + aosMktPopPpcst.getString("aos_match_type"), aosMktPopPpcst.get("aos_keydate"));
        }
        aosMktPopPpcstS.close();
        return keyDateMap;
    }

    /**
     * 根据国别编码查询组创建日期
     * 
     * @param pOuCode 国别编码
     * @return groupDateMap
     */
    public static HashMap<String, Object> generateLastGroupDate(Object pOuCode) {
        HashMap<String, Object> groupDateMap = new HashMap<>(16);
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter[] filters = new QFilter[] {filterOrg};
        String selectColumn = "aos_itemnumer,aos_groupdate";
        DataSet aosMktPopPpcstS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateLastGroupDate" + pOuCode,
            "aos_mkt_ppcst_data", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_itemnumer"};
        aosMktPopPpcstS = aosMktPopPpcstS.groupBy(groupBy).min("aos_groupdate").finish();
        while (aosMktPopPpcstS.hasNext()) {
            Row aosMktPopPpcst = aosMktPopPpcstS.next();
            groupDateMap.put(aosMktPopPpcst.getString("aos_itemnumer"), aosMktPopPpcst.get("aos_groupdate"));
        }
        aosMktPopPpcstS.close();
        return groupDateMap;
    }

    /**
     * 根据国别编码查询系列创建日期
     * 
     * @param pOuCode 国别编码
     * @return serialDateMap
     */
    public static HashMap<String, Object> generateLastSerialDate(Object pOuCode) {
        HashMap<String, Object> serialDateMap = new HashMap<>(16);
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter[] filters = new QFilter[] {filterOrg};
        String selectColumn = "aos_productno,aos_makedate";
        DataSet aosMktPopPpcstS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateLastSerialDate" + pOuCode,
            "aos_mkt_ppcst_data", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_productno"};
        aosMktPopPpcstS = aosMktPopPpcstS.groupBy(groupBy).min("aos_makedate").finish();
        while (aosMktPopPpcstS.hasNext()) {
            Row aosMktPopPpcst = aosMktPopPpcstS.next();
            serialDateMap.put(aosMktPopPpcst.getString("aos_productno"), aosMktPopPpcst.get("aos_makedate"));
        }
        aosMktPopPpcstS.close();
        return serialDateMap;
    }

    /**
     * 根据国别编码查询推广关键词
     * 
     * @param pOuCode 国别编码
     * @return keyRpt
     */
    public static HashMap<String, Map<String, Object>> generateKeyInit(Object pOuCode) {
        HashMap<String, Map<String, Object>> keyRpt = new HashMap<>(16);
        // 第零部分 ST关键词初始化表数据
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter filterStatus = new QFilter("aos_status", QCP.not_equals, "paused");
        QFilter[] filters = new QFilter[] {filterOrg, filterStatus};
        String selectColumn = "aos_itemnumer,aos_keyword,aos_match_type";
        DataSet aosMktPopBasestS = QueryServiceHelper.queryDataSet("AosMktGenerate.GeneratePpcStLastItemName" + pOuCode,
            "aos_mkt_pop_basest", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_itemnumer", "aos_keyword", "aos_match_type"};
        aosMktPopBasestS = aosMktPopBasestS.groupBy(groupBy).finish();
        while (aosMktPopBasestS.hasNext()) {
            Row aosMktPopBasest = aosMktPopBasestS.next();
            String aosItemnumer = aosMktPopBasest.getString("aos_itemnumer");
            String aosKeyword = aosMktPopBasest.getString("aos_keyword");
            String aosMatchType = aosMktPopBasest.getString("aos_match_type");
            Map<String, Object> info = keyRpt.get(aosItemnumer);
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(aosKeyword + "~" + aosMatchType, aosKeyword + "~" + aosMatchType);
            keyRpt.put(aosItemnumer, info);
        }
        aosMktPopBasestS.close();
        // 第二部分 销售手动建组界面上周数据 aos_mkt_pop_addst
        Calendar date = Calendar.getInstance();
        date.set(Calendar.HOUR_OF_DAY, 0);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);
        int weekOfYearLast = date.get(Calendar.WEEK_OF_YEAR) - 1;
        QFilter filterWeek = new QFilter("aos_weekofyear", QCP.equals, weekOfYearLast);
        filters = new QFilter[] {filterWeek, filterOrg};
        selectColumn = "aos_entryentity.aos_itemid.number aos_ad_name,"
            + "aos_entryentity.aos_subentryentity.aos_keyword aos_keyword";
        DataSet aosMktPopAddstS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateKeyRpt.add" + pOuCode,
            "aos_mkt_pop_addst", selectColumn, filters, null);
        while (aosMktPopAddstS.hasNext()) {
            Row aosMktPopAddst = aosMktPopAddstS.next();
            String aosKeyword = aosMktPopAddst.getString("aos_keyword");
            String aosAdName = aosMktPopAddst.getString("aos_ad_name");
            Map<String, Object> info = keyRpt.get(aosAdName);
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(aosKeyword + "~" + "EXACT", aosKeyword + "~" + "EXACT");
            info.put(aosKeyword + "~" + "PHRASE", aosKeyword + "~" + "PHRASE");
            keyRpt.put(aosAdName, info);
        }
        aosMktPopAddstS.close();
        // 第三部分 销售周调整表中上周数据
        QFilter filterType = new QFilter("aos_entryentity.aos_subentryentity.aos_valid", QCP.equals, false);
        filters = new QFilter[] {filterOrg, filterType};
        selectColumn = "aos_entryentity.aos_itemid.number aos_ad_name,"
            + "aos_entryentity.aos_subentryentity.aos_keyword aos_keyword,"
            + "aos_entryentity.aos_subentryentity.aos_match_type aos_match_type";
        DataSet aosMktPopWeekstS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateKeyRpt.week" + pOuCode,
            "aos_mkt_pop_weekst", selectColumn, filters, null);
        while (aosMktPopWeekstS.hasNext()) {
            Row aosMktPopWeekst = aosMktPopWeekstS.next();
            Map<String, Object> info = keyRpt.get(aosMktPopWeekst.getString("aos_ad_name"));
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(aosMktPopWeekst.getString("aos_keyword") + "~" + aosMktPopWeekst.getString("aos_match_type"),
                aosMktPopWeekst.getString("aos_keyword") + "~" + aosMktPopWeekst.getString("aos_match_type"));
            keyRpt.put(aosMktPopWeekst.getString("aos_ad_name"), info);
        }
        aosMktPopWeekstS.close();
        return keyRpt;
    }

    /**
     * 初始化组对应关键词+匹配方式 存在四个来源
     * 
     * @param pOuCode 国别编码
     * @return keyRpt
     */
    public static HashMap<String, Map<String, Object>> generateKeyRpt(Object pOuCode) {
        HashMap<String, Map<String, Object>> keyRpt = new HashMap<>(16);
        // 第一部分 关键词报告
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
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<=", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter filterMatch = new QFilter("aos_entryentity.aos_cam_name", "like", "%-KW%");
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterMatch, filterOrg};
        String selectColumn = "aos_entryentity.aos_ad_name aos_ad_name,aos_entryentity.aos_targeting aos_targeting,"
            + "aos_entryentity.aos_match_type aos_match_type";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateKeyRpt" + pOuCode,
            "aos_base_pointrpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_ad_name", "aos_targeting", "aos_match_type"};
        aosBaseSkupoprptS = aosBaseSkupoprptS.groupBy(groupBy).finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            Map<String, Object> info = keyRpt.get(aosBaseSkupoprpt.getString("aos_ad_name"));
            if (info == null) {
                info = new HashMap<>(16);
            }
            String key =
                aosBaseSkupoprpt.getString("aos_targeting") + "~" + aosBaseSkupoprpt.getString("aos_match_type");
            info.put(key, key);
            keyRpt.put(aosBaseSkupoprpt.getString("aos_ad_name"), info);
        }
        aosBaseSkupoprptS.close();
        // 第二部分 销售手动建组界面上周数据
        Calendar date = Calendar.getInstance();
        date.set(Calendar.HOUR_OF_DAY, 0);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);
        int weekOfYearLast = date.get(Calendar.WEEK_OF_YEAR) - 1;
        QFilter filterWeek = new QFilter("aos_weekofyear", QCP.equals, weekOfYearLast);
        // 新增在线
        QFilter filterStatus = new QFilter("aos_entryentity.aos_subentryentity.aos_keystatus", QCP.equals, "ADD");
        filters = new QFilter[] {filterWeek, filterOrg, filterStatus};
        selectColumn = "aos_entryentity.aos_itemid.number aos_ad_name,"
            + "aos_entryentity.aos_subentryentity.aos_keyword aos_keyword,"
            + "aos_entryentity.aos_subentryentity.aos_keytype aos_keytype";
        DataSet aosMktPopAddstS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateKeyRpt.add" + pOuCode,
            "aos_mkt_pop_addst", selectColumn, filters, null);
        while (aosMktPopAddstS.hasNext()) {
            Row aosMktPopAddst = aosMktPopAddstS.next();
            String aosKeyword = aosMktPopAddst.getString("aos_keyword");
            String aosAdName = aosMktPopAddst.getString("aos_ad_name");
            String aosKeytype = aosMktPopAddst.getString("aos_keytype");
            Map<String, Object> info = keyRpt.get(aosAdName);
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(aosKeyword + "~" + aosKeytype, aosKeyword + "~" + aosKeytype);
            keyRpt.put(aosAdName, info);
        }
        aosMktPopAddstS.close();
        // 第三部分 销售周调整表中上周数据
        QFilter filterType = new QFilter("aos_entryentity.aos_subentryentity.aos_valid", QCP.equals, false);
        filters = new QFilter[] {filterOrg, filterType};
        selectColumn = "aos_entryentity.aos_itemid.number aos_ad_name,"
            + "aos_entryentity.aos_subentryentity.aos_keyword aos_keyword,"
            + "aos_entryentity.aos_subentryentity.aos_match_type aos_match_type";
        DataSet aosMktPopWeekstS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateKeyRpt.week" + pOuCode,
            "aos_mkt_pop_weekst", selectColumn, filters, null);
        while (aosMktPopWeekstS.hasNext()) {
            Row aosMktPopWeekst = aosMktPopWeekstS.next();
            Map<String, Object> info = keyRpt.get(aosMktPopWeekst.getString("aos_ad_name"));
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(aosMktPopWeekst.getString("aos_keyword") + "~" + aosMktPopWeekst.getString("aos_match_type"),
                aosMktPopWeekst.getString("aos_keyword") + "~" + aosMktPopWeekst.getString("aos_match_type"));
            keyRpt.put(aosMktPopWeekst.getString("aos_ad_name"), info);
        }
        aosMktPopWeekstS.close();
        // 第四部分 ST关键词初始化表数据
        DynamicObjectCollection aosMktPopBasestS = QueryServiceHelper.query("aos_mkt_pop_basest",
            "aos_itemnumer,aos_keyword,aos_match_type", filterOrg.toArray());
        for (DynamicObject aosMktPopBasest : aosMktPopBasestS) {
            Map<String, Object> info =
                keyRpt.computeIfAbsent(aosMktPopBasest.getString("aos_itemnumer"), k -> new HashMap<>(16));
            info.put(aosMktPopBasest.getString("aos_keyword") + "~" + aosMktPopBasest.getString("aos_match_type"),
                aosMktPopBasest.getString("aos_keyword") + "~" + aosMktPopBasest.getString("aos_match_type"));
            keyRpt.put(aosMktPopBasest.getString("aos_itemnumer"), info);
        }
        return keyRpt;
    }

    /**
     *
     * @param pOuCode 国别编码
     * @return orderMonth
     */
    public static HashMap<String, Integer> generateOrderMonth(Object pOuCode) {
        HashMap<String, Integer> orderMonth = new HashMap<>(16);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        today.add(Calendar.DAY_OF_MONTH, -15);
        QFilter filterOrg = new QFilter("aos_org.number", QCP.equals, pOuCode);
        QFilter filterDate = new QFilter("aos_order_date", QCP.large_equals, sdf.format(today.getTime()));
        List<QFilter> orderFilter = SalUtil.getOrderFilter();
        orderFilter.add(filterOrg);
        orderFilter.add(filterDate);
        int size = orderFilter.size();
        QFilter[] filters = orderFilter.toArray(new QFilter[size]);
        DataSet orderDataSet = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateOrderMonth" + pOuCode,
            "aos_sync_om_order_r", "aos_org,aos_item_fid,aos_order_qty", filters, null);
        orderDataSet = orderDataSet.groupBy(new String[] {"aos_org", "aos_item_fid"}).sum("aos_order_qty").finish();
        while (orderDataSet.hasNext()) {
            Row aosSyncOmonthSummary = orderDataSet.next();
            orderMonth.put(aosSyncOmonthSummary.getString("aos_item_fid"),
                aosSyncOmonthSummary.getInteger("aos_order_qty"));
        }
        orderDataSet.close();
        return orderMonth;
    }

    public static HashMap<String, Integer> generateOrder7Days(Object pOuCode) {
        HashMap<String, Integer> orderMonth = new HashMap<>(16);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        today.add(Calendar.DAY_OF_MONTH, -7);
        QFilter filterOrg = new QFilter("aos_org.number", QCP.equals, pOuCode);
        QFilter filterDate = new QFilter("aos_order_date", QCP.large_equals, sdf.format(today.getTime()));
        List<QFilter> orderFilter = SalUtil.getOrderFilter();
        orderFilter.add(filterOrg);
        orderFilter.add(filterDate);
        int size = orderFilter.size();
        QFilter[] filters = orderFilter.toArray(new QFilter[size]);
        DataSet orderDataSet = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateOrder7Days" + pOuCode,
            "aos_sync_om_order_r", "aos_org,aos_item_fid,aos_order_qty", filters, null);
        orderDataSet = orderDataSet.groupBy(new String[] {"aos_org", "aos_item_fid"}).sum("aos_order_qty").finish();
        while (orderDataSet.hasNext()) {
            Row aosSyncOmonthSummary = orderDataSet.next();
            orderMonth.put(aosSyncOmonthSummary.getString("aos_item_fid"),
                aosSyncOmonthSummary.getInteger("aos_order_qty"));
        }
        orderDataSet.close();
        return orderMonth;
    }

    public static HashMap<String, Object> generatePpcStLast(Object pOuCode) {
        HashMap<String, Object> ppcYester = new HashMap<>(16);
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
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter filterDateFrom = new QFilter("aos_date", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_date", "<", dateToStr);
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterOrg};
        String selectColumn = "aos_itemid";
        DataSet aosMktPopPpcstS = QueryServiceHelper.queryDataSet("AosMktGenerate.GeneratePpcStLast" + pOuCode,
            "aos_mkt_ppcst_data", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_itemid"};
        aosMktPopPpcstS = aosMktPopPpcstS.groupBy(groupBy).finish();
        while (aosMktPopPpcstS.hasNext()) {
            Row aosMktPopPpcst = aosMktPopPpcstS.next();
            ppcYester.put(aosMktPopPpcst.getString("aos_itemid"), aosMktPopPpcst.get("aos_itemid"));
        }
        aosMktPopPpcstS.close();
        return ppcYester;
    }

    public static HashMap<String, Map<String, Object>> generatePpcStLastItemName(Object pOuCode) {
        HashMap<String, Map<String, Object>> ppcYester = new HashMap<>(16);
        HashMap<String, Map<String, Object>> stBase = AosMktGenUtil.generateStBase(pOuCode);
        // 国别物料
        QFilter filterOu = new QFilter("aos_contryentry.aos_nationality.number", "=", pOuCode);
        QFilter filterType = new QFilter("aos_protype", "=", "N");
        QFilter[] filters = new QFilter[] {filterOu, filterType};
        String selectField = "number,aos_cn_name,id";
        DynamicObjectCollection bdMaterialS =
            QueryServiceHelper.query("bd_material", selectField, filters, "aos_productno");
        for (DynamicObject bdMaterial : bdMaterialS) {
            String aosItemname = bdMaterial.getString("aos_cn_name");
            String aosItemnumer = bdMaterial.getString("number");
            String aosItemid = bdMaterial.getString("id");
            if (stBase.get(aosItemnumer) != null) {
                Map<String, Object> stBaseDetail = stBase.get(aosItemnumer);
                if (stBaseDetail != null) {
                    for (String aosTargetcomb : stBaseDetail.keySet()) {
                        Map<String, Object> info = ppcYester.get(aosItemname);
                        if (info == null) {
                            info = new HashMap<>(16);
                        }
                        info.put(aosTargetcomb, aosItemid);
                        ppcYester.put(aosItemname, info);
                    }
                }
            }
        }
        return ppcYester;
    }

    public static HashMap<String, Map<String, Object>> generatePopOrgInfo(Object pOuCode) {
        HashMap<String, Map<String, Object>> popOrgInfo = new HashMap<>(16);
        String selectColumn = "aos_orgid," + "aos_type," + "aos_value,aos_condition1";
        DataSet aosMktBaseOrgvalueS = QueryServiceHelper.queryDataSet("AosMktGenerate.GeneratePopOrgInfo" + pOuCode,
            "aos_mkt_base_orgvalue", selectColumn, null, null);
        while (aosMktBaseOrgvalueS.hasNext()) {
            Row aosMktBaseOrgvalue = aosMktBaseOrgvalueS.next();
            HashMap<String, Object> info = new HashMap<>(16);
            info.put("aos_value", aosMktBaseOrgvalue.get("aos_value"));
            info.put("aos_condition1", aosMktBaseOrgvalue.get("aos_condition1"));
            popOrgInfo.put(aosMktBaseOrgvalue.getLong("aos_orgid") + "~" + aosMktBaseOrgvalue.get("aos_type"), info);
        }
        aosMktBaseOrgvalueS.close();
        return popOrgInfo;
    }

    /**
     * 7日关键词报告数据 曝光汇总
     * 
     * @param pOuCode 国别编码
     * @return keyRpt
     */
    public static HashMap<String, BigDecimal> generateKeyRpt7GroupExp(Object pOuCode) {
        HashMap<String, BigDecimal> keyRpt = new HashMap<>(16);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date dateTo = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, -7);
        Date dateFrom = calendar.getTime();
        // 日期格式化
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<=", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter filterMatch = new QFilter("aos_entryentity.aos_cam_name", "like", "%-KW%");
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterMatch, filterOrg};
        String selectColumn =
            "aos_entryentity.aos_ad_name aos_ad_name," + "aos_entryentity.aos_impressions aos_impressions";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateKeyRpt",
            "aos_base_pointrpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_ad_name"};
        aosBaseSkupoprptS = aosBaseSkupoprptS.groupBy(groupBy).sum("aos_impressions").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            keyRpt.put(aosBaseSkupoprpt.getString("aos_ad_name"), aosBaseSkupoprpt.getBigDecimal("aos_impressions"));
        }
        aosBaseSkupoprptS.close();
        return keyRpt;
    }

    public static HashMap<String, Map<String, Object>> generatePpcStTodayKeyStatus(Object pOuCode) {
        HashMap<String, Map<String, Object>> ppcToday = new HashMap<>(16);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date today = calendar.getTime();
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter filterDate = new QFilter("aos_date", "=", today);
        QFilter[] filters = new QFilter[] {filterDate, filterOrg};
        String selectColumn = "aos_itemid,aos_keyword,aos_match_type,aos_keystatus";
        DataSet aosMktPopPpcstS = QueryServiceHelper.queryDataSet(
            "AosMktGenerate.GeneratePpcStTodayKeyStatus" + pOuCode, "aos_mkt_pop_ppcst", selectColumn, filters, null);
        while (aosMktPopPpcstS.hasNext()) {
            Row aosMktPopPpcst = aosMktPopPpcstS.next();
            String aosItemid = String.valueOf(aosMktPopPpcst.getLong("aos_itemid"));
            Map<String, Object> info = ppcToday.get(aosItemid);
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(aosMktPopPpcst.getString("aos_keyword") + "~" + aosMktPopPpcst.getString("aos_match_type"),
                aosMktPopPpcst.getString("aos_keystatus"));
            ppcToday.put(aosItemid, info);
        }
        aosMktPopPpcstS.close();
        return ppcToday;
    }

    public static HashMap<String, Map<String, Object>> generateKeyRpt7(Object pOuCode) {
        HashMap<String, Map<String, Object>> keyRpt = new HashMap<>(16);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date dateTo = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, -7);
        Date dateFrom = calendar.getTime();
        // 日期格式化
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<=", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter filterMatch = new QFilter("aos_entryentity.aos_cam_name", "like", "%-KW%");
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterMatch, filterOrg};
        String selectColumn = "aos_entryentity.aos_ad_name aos_ad_name,aos_entryentity.aos_targeting aos_targeting,"
            + "aos_entryentity.aos_match_type aos_match_type," + "aos_entryentity.aos_clicks aos_clicks,"
            + "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_impressions aos_impressions,"
            + "aos_entryentity.aos_sales aos_sales";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateKeyRpt",
            "aos_base_pointrpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_ad_name", "aos_targeting", "aos_match_type"};
        aosBaseSkupoprptS = aosBaseSkupoprptS.groupBy(groupBy).sum("aos_clicks").sum("aos_spend").sum("aos_impressions")
            .sum("aos_sales").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            String key = aosBaseSkupoprpt.getString("aos_ad_name") + "~" + aosBaseSkupoprpt.getString("aos_targeting")
                + "~" + aosBaseSkupoprpt.getString("aos_match_type");
            Map<String, Object> info = keyRpt.get(key);
            if (info == null) {
                info = new HashMap<>(16);
            }
            BigDecimal aosRoi = BigDecimal.ZERO;
            if (aosBaseSkupoprpt.get("aos_spend") != null
                && ((BigDecimal)aosBaseSkupoprpt.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                aosRoi = ((BigDecimal)aosBaseSkupoprpt.get("aos_sales"))
                    .divide((BigDecimal)aosBaseSkupoprpt.get("aos_spend"), 6, RoundingMode.HALF_UP);
            }
            info.put("aos_clicks", aosBaseSkupoprpt.getBigDecimal("aos_clicks"));
            info.put("aos_spend", aosBaseSkupoprpt.getBigDecimal("aos_spend"));
            info.put("aos_impressions", aosBaseSkupoprpt.getBigDecimal("aos_impressions"));
            info.put("aos_roi", aosRoi);
            keyRpt.put(key, info);
        }
        aosBaseSkupoprptS.close();
        return keyRpt;
    }

    public static HashMap<String, Map<String, Object>> generateKeyRpt1(Object pOuCode) {
        HashMap<String, Map<String, Object>> keyRpt = new HashMap<>(16);
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
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<=", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter filterMatch = new QFilter("aos_entryentity.aos_cam_name", "like", "%-KW%");
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterMatch, filterOrg};
        String selectColumn = "aos_entryentity.aos_ad_name aos_ad_name,aos_entryentity.aos_targeting aos_targeting,"
            + "aos_entryentity.aos_match_type aos_match_type," + "aos_entryentity.aos_clicks aos_clicks,"
            + "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_impressions aos_impressions,"
            + "aos_entryentity.aos_sales aos_sales";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateKeyRpt",
            "aos_base_pointrpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_ad_name", "aos_targeting", "aos_match_type"};
        aosBaseSkupoprptS = aosBaseSkupoprptS.groupBy(groupBy).sum("aos_clicks").sum("aos_spend").sum("aos_impressions")
            .sum("aos_sales").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            String key = aosBaseSkupoprpt.getString("aos_ad_name") + "~" + aosBaseSkupoprpt.getString("aos_targeting")
                + "~" + aosBaseSkupoprpt.getString("aos_match_type");
            Map<String, Object> info = keyRpt.get(key);
            if (info == null) {
                info = new HashMap<>(16);
            }
            BigDecimal aosRoi = BigDecimal.ZERO;
            if (aosBaseSkupoprpt.get("aos_spend") != null
                && ((BigDecimal)aosBaseSkupoprpt.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                aosRoi = ((BigDecimal)aosBaseSkupoprpt.get("aos_sales"))
                    .divide((BigDecimal)aosBaseSkupoprpt.get("aos_spend"), 6, RoundingMode.HALF_UP);
            }
            info.put("aos_clicks", aosBaseSkupoprpt.getBigDecimal("aos_clicks"));
            info.put("aos_spend", aosBaseSkupoprpt.getBigDecimal("aos_spend"));
            info.put("aos_impressions", aosBaseSkupoprpt.getBigDecimal("aos_impressions"));
            info.put("aos_roi", aosRoi);
            keyRpt.put(key, info);
        }
        aosBaseSkupoprptS.close();
        return keyRpt;
    }

    public static HashMap<String, Map<String, Object>> generateAdvPrice(Object pOuCode) {
        HashMap<String, Map<String, Object>> adPrice = new HashMap<>(16);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date aosDate = calendar.getTime();
        // 日期格式化
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        String aosDateStr = writeFormat.format(aosDate);
        QFilter filterOrg = new QFilter("aos_entryentity.aos_orgid.number", QCP.equals, pOuCode);
        QFilter filterDate = new QFilter("aos_date", "=", aosDateStr);
        QFilter[] filters = new QFilter[] {filterDate, filterOrg};
        String selectColumn = "aos_entryentity.aos_orgid aos_orgid," + "aos_entryentity.aos_ad_name aos_ad_name,"
            + "aos_entryentity.aos_bid_suggest aos_bid_suggest,"
            + "aos_entryentity.aos_bid_rangestart aos_bid_rangestart,"
            + "aos_entryentity.aos_bid_rangeend aos_bid_rangeend";
        DataSet aosBaseAdvrptS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateAdvPrice" + pOuCode,
            "aos_base_advrpt", selectColumn, filters, null);
        while (aosBaseAdvrptS.hasNext()) {
            Row aosBaseAdvrpt = aosBaseAdvrptS.next();
            Map<String, Object> info = new HashMap<>(16);
            info.put("aos_bid_suggest", aosBaseAdvrpt.get("aos_bid_suggest"));
            info.put("aos_bid_rangestart", aosBaseAdvrpt.get("aos_bid_rangestart"));
            info.put("aos_bid_rangeend", aosBaseAdvrpt.get("aos_bid_rangeend"));
            adPrice.put(aosBaseAdvrpt.getString("aos_ad_name"), info);
        }
        aosBaseAdvrptS.close();
        return adPrice;
    }

    public static HashMap<String, Map<String, Object>> generateAdvKeyPrice(Object pOuCode) {
        HashMap<String, Map<String, Object>> adPrice = new HashMap<>(16);
        QFilter filterOrg = new QFilter("entryentity.aos_orgid.number", QCP.equals, pOuCode);
        QFilter[] filters = new QFilter[] {filterOrg};
        String selectColumn = "entryentity.aos_orgid aos_orgid," + "entryentity.aos_ad_name aos_ad_name,"
            + "entryentity.aos_keyword aos_keyword," + "entryentity.aos_match_type aos_match_type,"
            + "entryentity.aos_bid_suggest aos_bid_suggest," + "entryentity.aos_bid_rangestart aos_bid_rangestart,"
            + "entryentity.aos_bid_rangeend aos_bid_rangeend";
        DataSet aosBaseAdvrptS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateAdvKeyPrice" + pOuCode,
            "aos_base_pointadv", selectColumn, filters, null);
        while (aosBaseAdvrptS.hasNext()) {
            Row aosBaseAdvrpt = aosBaseAdvrptS.next();
            Map<String, Object> info = new HashMap<>(16);
            String key = aosBaseAdvrpt.getString("aos_ad_name") + "~" + aosBaseAdvrpt.getString("aos_keyword") + "~"
                + aosBaseAdvrpt.getString("aos_match_type");
            info.put("aos_bid_suggest", aosBaseAdvrpt.get("aos_bid_suggest"));
            info.put("aos_bid_rangestart", aosBaseAdvrpt.get("aos_bid_rangestart"));
            info.put("aos_bid_rangeend", aosBaseAdvrpt.get("aos_bid_rangeend"));
            adPrice.put(key, info);
        }
        aosBaseAdvrptS.close();
        return adPrice;
    }

    public static HashMap<String, Map<String, Object>> generateAdvKeyPrice14(Object pOuCode) {
        HashMap<String, Map<String, Object>> keyRpt14 = new HashMap<>(16);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date dateTo = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, -14);
        Date dateFrom = calendar.getTime();
        // 日期格式化
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter filterDateFrom = new QFilter("aos_date", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_date", "<=", dateToStr);
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterOrg};
        String selectColumn =
            "aos_date ," + "entryentity.aos_ad_name aos_ad_name," + "entryentity.aos_keyword aos_keyword,"
                + "entryentity.aos_match_type aos_match_type," + "entryentity.aos_bid_suggest aos_bid_suggest";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateAdvKeyPrice14" + pOuCode,
            "aos_base_pointadv", selectColumn, filters, null);
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            String key = aosBaseSkupoprpt.getString("aos_ad_name") + "~" + aosBaseSkupoprpt.getString("aos_keyword")
                + "~" + aosBaseSkupoprpt.getString("aos_match_type");
            Date aosDateL = aosBaseSkupoprpt.getDate("aos_date");
            String aosDateStr = df.format(aosDateL);
            Map<String, Object> info = keyRpt14.get(key);
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(aosDateStr, aosBaseSkupoprpt.get("aos_bid_suggest"));
            keyRpt14.put(key, info);
        }
        aosBaseSkupoprptS.close();
        return keyRpt14;
    }

    public static HashMap<String, Object> generateDailyPrice(Object pOuCode) {
        HashMap<String, Object> dailyPrice = new HashMap<>(16);
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter[] filters = new QFilter[] {filterOrg};
        String selectColumn = "aos_orgid," + "aos_item_code aos_itemid," + "aos_currentprice";
        DynamicObjectCollection aosSyncInvpriceS = QueryServiceHelper.query("aos_sync_invprice", selectColumn, filters);
        for (DynamicObject aosSyncInvprice : aosSyncInvpriceS) {
            dailyPrice.put(aosSyncInvprice.getString("aos_itemid"), aosSyncInvprice.get("aos_currentprice"));
        }
        return dailyPrice;
    }

    public static HashMap<String, Object> generateAvaDays(Object pOuCode) {
        HashMap<String, Object> itemAvaDays = new HashMap<>(16);
        QFilter filterOrg = new QFilter("aos_ou_code", QCP.equals, pOuCode);
        QFilter[] filters = new QFilter[] {filterOrg};
        String selectColumn = "aos_ou_code," + "aos_item_code," + "aos_7avadays";
        DynamicObjectCollection aosSalDwInvavadayS =
            QueryServiceHelper.query("aos_sal_dw_invavadays", selectColumn, filters);
        for (DynamicObject aosSalDwInvavaday : aosSalDwInvavadayS) {
            itemAvaDays.put(aosSalDwInvavaday.getString("aos_item_code"), aosSalDwInvavaday.get("aos_7avadays"));
        }
        return itemAvaDays;
    }

    public static HashMap<String, Map<String, Object>> generatePpcYesterSt(Object pOuCode) {
        HashMap<String, Map<String, Object>> ppcYester = new HashMap<>(16);
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
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterOrg};
        String selectColumn = "aos_itemnumer,aos_keyword,aos_match_type,aos_budget,aos_bid,aos_lastpricedate";
        DataSet aosMktPopularPpcS = QueryServiceHelper.queryDataSet("AosMktGenerate.GeneratePpcYesterSt" + pOuCode,
            "aos_mkt_ppcst_data", selectColumn, filters, null);
        while (aosMktPopularPpcS.hasNext()) {
            Row aosMktPopularPpc = aosMktPopularPpcS.next();
            HashMap<String, Object> info = new HashMap<>(16);
            String key = aosMktPopularPpc.getString("aos_itemnumer") + "~" + aosMktPopularPpc.getString("aos_keyword")
                + "~" + aosMktPopularPpc.getString("aos_match_type");
            // 上次预算
            info.put("aos_budget", aosMktPopularPpc.get("aos_budget"));
            // 上次出价
            info.put("aos_bid", aosMktPopularPpc.get("aos_bid"));
            info.put("aos_lastpricedate", aosMktPopularPpc.get("aos_lastpricedate"));
            ppcYester.put(key, info);
        }
        aosMktPopularPpcS.close();
        return ppcYester;
    }

    public static HashMap<String, Map<String, Object>> generatePpcTodaySt(Object pOuCode) {
        HashMap<String, Map<String, Object>> ppcToday = new HashMap<>(16);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date today = calendar.getTime();
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter filterDate = new QFilter("aos_date", "=", today);
        QFilter[] filters = new QFilter[] {filterDate, filterOrg};
        String selectColumn = "aos_itemnumer,aos_keyword,aos_match_type,aos_budget," + "aos_bid,aos_keystatus";
        DataSet aosMktPopularPpcS = QueryServiceHelper.queryDataSet("AosMktGenerate.GeneratePpcTodaySt" + pOuCode,
            "aos_mkt_ppcst_data", selectColumn, filters, null);
        while (aosMktPopularPpcS.hasNext()) {
            Row aosMktPopularPpc = aosMktPopularPpcS.next();
            HashMap<String, Object> info = new HashMap<>(16);
            String key = aosMktPopularPpc.getString("aos_itemnumer") + "~" + aosMktPopularPpc.getString("aos_keyword")
                + "~" + aosMktPopularPpc.getString("aos_match_type");
            // 今日出价
            info.put("aos_bid", aosMktPopularPpc.get("aos_bid"));
            // 剔词
            info.put("aos_keystatus", aosMktPopularPpc.get("aos_keystatus"));
            ppcToday.put(key, info);
        }
        aosMktPopularPpcS.close();
        return ppcToday;
    }

    public static HashMap<String, Object> generatePpcTodayStGroup(Object pOuCode) {
        HashMap<String, Object> ppcToday = new HashMap<>(16);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date today = calendar.getTime();
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter filterDate = new QFilter("aos_date", "=", today);
        QFilter[] filters = new QFilter[] {filterDate, filterOrg};
        String selectColumn = "aos_itemnumer," + "1 aos_count";
        DataSet aosMktPopularPpcS = QueryServiceHelper.queryDataSet("AosMktGenerate.GeneratePpcTodayStGroup" + pOuCode,
            "aos_mkt_ppcst_data", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_itemnumer"};
        aosMktPopularPpcS = aosMktPopularPpcS.groupBy(groupBy).sum("aos_count").finish();
        while (aosMktPopularPpcS.hasNext()) {
            Row aosMktPopularPpc = aosMktPopularPpcS.next();
            String aosItemnumer = aosMktPopularPpc.getString("aos_itemnumer");
            int aosCount = aosMktPopularPpc.getInteger("aos_count");
            ppcToday.put(aosItemnumer, aosCount);
        }
        aosMktPopularPpcS.close();
        return ppcToday;
    }

    public static HashMap<String, Object> generatePpcTodayStSerialGroup(Object pOuCode) {
        HashMap<String, Object> ppcToday = new HashMap<>(16);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date today = calendar.getTime();
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter filterDate = new QFilter("aos_date", "=", today);
        QFilter[] filters = new QFilter[] {filterDate, filterOrg};
        String selectColumn = "aos_productno ,aos_itemnumer";
        DataSet aosMktPopularPpcS =
            QueryServiceHelper.queryDataSet("AosMktGenerate.GeneratePpcTodayStSerialGroup" + pOuCode,
                "aos_mkt_ppcst_data", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_productno", "aos_itemnumer"};
        aosMktPopularPpcS = aosMktPopularPpcS.groupBy(groupBy).finish();
        while (aosMktPopularPpcS.hasNext()) {
            Row aosMktPopularPpc = aosMktPopularPpcS.next();
            String aosProductno = aosMktPopularPpc.getString("aos_productno");
            ppcToday.merge(aosProductno, 1, (a, b) -> (int)a + (int)b);
        }
        aosMktPopularPpcS.close();
        return ppcToday;
    }

    /**
     * 今日系列维度关键词个数
     * 
     * @param pOuCode 物料编码
     * @return ppcToday
     */
    public static HashMap<String, Object> generatePpcTodayStSerial(Object pOuCode) {
        HashMap<String, Object> ppcToday = new HashMap<>(16);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date today = calendar.getTime();
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter filterDate = new QFilter("aos_date", "=", today);
        QFilter[] filters = new QFilter[] {filterDate, filterOrg};
        String selectColumn = "aos_productno," + "1 aos_count";
        DataSet aosMktPopularPpcS = QueryServiceHelper.queryDataSet("AosMktGenerate.GeneratePpcTodayStSerial" + pOuCode,
            "aos_mkt_ppcst_data", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_productno"};
        aosMktPopularPpcS = aosMktPopularPpcS.groupBy(groupBy).sum("aos_count").finish();
        while (aosMktPopularPpcS.hasNext()) {
            Row aosMktPopularPpc = aosMktPopularPpcS.next();
            String aosProductno = aosMktPopularPpc.getString("aos_productno");
            int aosCount = aosMktPopularPpc.getInteger("aos_count");
            ppcToday.put(aosProductno, aosCount);
        }
        aosMktPopularPpcS.close();
        return ppcToday;
    }

    /**
     * ST数据源昨日系列预算
     * 
     * @param pOuCode 国别编码
     * @return ppcToday
     */
    public static HashMap<String, Object> generatePpcYeStSerial(Object pOuCode) {
        HashMap<String, Object> ppcToday = new HashMap<>(16);
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
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterOrg};
        String selectColumn = "aos_productno,aos_budget";
        DataSet aosMktPopularPpcS = QueryServiceHelper.queryDataSet("AosMktGenerate.GeneratePpcYeStSerial" + pOuCode,
            "aos_mkt_ppcst_data", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_productno", "aos_budget"};
        aosMktPopularPpcS = aosMktPopularPpcS.groupBy(groupBy).finish();
        while (aosMktPopularPpcS.hasNext()) {
            Row aosMktPopularPpc = aosMktPopularPpcS.next();
            String aosProductno = aosMktPopularPpc.getString("aos_productno");
            BigDecimal aosBudget = aosMktPopularPpc.getBigDecimal("aos_budget");
            ppcToday.put(aosProductno, aosBudget);
        }
        aosMktPopularPpcS.close();
        return ppcToday;
    }

    public static HashMap<String, Map<String, Object>> generateSkuRpt7(Object pOuCode) {
        HashMap<String, Map<String, Object>> keyRpt7 = new HashMap<>(16);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date dateTo = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, -7);
        Date dateFrom = calendar.getTime();
        // 日期格式化
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<=", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterOrg};
        String selectColumn = "aos_entryentity.aos_ad_name aos_ad_name,"
            + "aos_entryentity.aos_targeting aos_targeting," + "aos_entryentity.aos_match_type aos_match_type,"
            + "aos_entryentity.aos_impressions aos_impressions," + "aos_entryentity.aos_spend aos_spend,"
            + "aos_entryentity.aos_sales aos_sales," + "aos_entryentity.aos_clicks aos_clicks";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateSkuRpt7" + pOuCode,
            "aos_base_pointrpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_ad_name", "aos_targeting", "aos_match_type"};
        aosBaseSkupoprptS = aosBaseSkupoprptS.groupBy(groupBy).sum("aos_impressions").sum("aos_spend").sum("aos_sales")
            .sum("aos_clicks").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            HashMap<String, Object> info = new HashMap<>(16);
            String key = aosBaseSkupoprpt.getString("aos_ad_name") + "~" + aosBaseSkupoprpt.getString("aos_targeting")
                + "~" + aosBaseSkupoprpt.getString("aos_match_type");
            info.put("aos_spend", aosBaseSkupoprpt.get("aos_spend"));
            info.put("aos_sales", aosBaseSkupoprpt.get("aos_sales"));
            info.put("aos_impressions", aosBaseSkupoprpt.get("aos_impressions"));
            info.put("aos_clicks", aosBaseSkupoprpt.get("aos_clicks"));
            keyRpt7.put(key, info);
        }
        aosBaseSkupoprptS.close();
        return keyRpt7;
    }

    public static HashMap<String, Map<String, Map<String, Object>>> generateSkuRpt14(Object pOuCode) {
        HashMap<String, Map<String, Map<String, Object>>> keyRpt14 = new HashMap<>(16);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date dateTo = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, -14);
        Date dateFrom = calendar.getTime();
        // 日期格式化
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<=", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterOrg};
        String selectColumn =
            "aos_entryentity.aos_ad_name aos_ad_name," + "aos_entryentity.aos_targeting aos_targeting,"
                + "aos_entryentity.aos_match_type aos_match_type," + "aos_entryentity.aos_impressions aos_impressions,"
                + "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_sales aos_sales,"
                + "aos_entryentity.aos_clicks aos_clicks," + "aos_entryentity.aos_date_l aos_date_l";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateSkuRpt14" + pOuCode,
            "aos_base_pointrpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_ad_name", "aos_targeting", "aos_match_type", "aos_date_l"};
        aosBaseSkupoprptS = aosBaseSkupoprptS.groupBy(groupBy).sum("aos_impressions").sum("aos_spend").sum("aos_sales")
            .sum("aos_clicks").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            HashMap<String, Object> detail = new HashMap<>(16);
            String key = aosBaseSkupoprpt.getString("aos_ad_name") + "~" + aosBaseSkupoprpt.getString("aos_targeting")
                + "~" + aosBaseSkupoprpt.getString("aos_match_type");
            detail.put("aos_spend", aosBaseSkupoprpt.get("aos_spend"));
            detail.put("aos_sales", aosBaseSkupoprpt.get("aos_sales"));
            detail.put("aos_impressions", aosBaseSkupoprpt.get("aos_impressions"));
            detail.put("aos_clicks", aosBaseSkupoprpt.get("aos_clicks"));
            BigDecimal aosRoi = BigDecimal.ZERO;
            if (aosBaseSkupoprpt.get("aos_spend") != null
                && ((BigDecimal)aosBaseSkupoprpt.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                aosRoi = ((BigDecimal)aosBaseSkupoprpt.get("aos_sales"))
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
            Map<String, Map<String, Object>> info = keyRpt14.get(key);
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(aosDateStr, detail);
            keyRpt14.put(key, info);
        }
        aosBaseSkupoprptS.close();
        return keyRpt14;
    }

    public static HashMap<String, Map<String, Map<String, Object>>> generateSkuRpt7Serial(Object pOuCode) {
        HashMap<String, Map<String, Map<String, Object>>> keyRpt7 = new HashMap<>(16);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date dateTo = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, -7);
        Date dateFrom = calendar.getTime();
        // 日期格式化
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<=", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterOrg};
        String selectColumn =
            "aos_entryentity.aos_cam_name aos_cam_name," + "aos_entryentity.aos_impressions aos_impressions,"
                + "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_sales aos_sales,"
                + "aos_entryentity.aos_clicks aos_clicks," + "aos_entryentity.aos_date_l aos_date_l";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateSkuRpt7Serial" + pOuCode,
            "aos_base_pointrpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_cam_name", "aos_date_l"};
        aosBaseSkupoprptS = aosBaseSkupoprptS.groupBy(groupBy).sum("aos_impressions").sum("aos_spend").sum("aos_sales")
            .sum("aos_clicks").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            HashMap<String, Object> detail = new HashMap<>(16);
            String key = aosBaseSkupoprpt.getString("aos_cam_name");
            detail.put("aos_spend", aosBaseSkupoprpt.get("aos_spend"));
            detail.put("aos_sales", aosBaseSkupoprpt.get("aos_sales"));
            detail.put("aos_impressions", aosBaseSkupoprpt.get("aos_impressions"));
            detail.put("aos_clicks", aosBaseSkupoprpt.get("aos_clicks"));
            BigDecimal aosRoi = BigDecimal.ZERO;
            if (aosBaseSkupoprpt.get("aos_spend") != null
                && ((BigDecimal)aosBaseSkupoprpt.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                aosRoi = ((BigDecimal)aosBaseSkupoprpt.get("aos_sales"))
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
            Map<String, Map<String, Object>> info = keyRpt7.get(key);
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(aosDateStr, detail);
            keyRpt7.put(key, info);
        }
        aosBaseSkupoprptS.close();
        return keyRpt7;
    }

    public static HashMap<String, Map<String, Object>> gnerateSalSummarySerial(Object pOuCode) {
        HashMap<String, Map<String, Object>> salSummary = new HashMap<>(16);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        Date dateFrom = calendar.getTime();
        calendar.add(Calendar.MONTH, 1);
        Date dateTo = calendar.getTime();
        // 日期格式化
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterOrg = new QFilter("aos_org.number", QCP.equals, pOuCode);
        QFilter filterType = new QFilter("aos_rsche_no", "=", "SKU调整表");
        QFilter filterDateFrom = new QFilter("aos_month", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_month", "<", dateToStr);
        QFilter[] filters = new QFilter[] {filterType, filterDateFrom, filterDateTo, filterOrg};
        String selectColumn = "aos_sku.aos_productno||'-AUTO' aos_productno," + "aos_price * aos_sche_qty aos_sales,"
            + "aos_sche_qty,createtime";
        DataSet aosSalScheSummaryS = QueryServiceHelper.queryDataSet(
            "AosMktGenerate.GenerateSalSummarySerial" + pOuCode, "aos_sal_sche_summary", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_productno"};
        aosSalScheSummaryS = aosSalScheSummaryS.groupBy(groupBy).maxP("createtime", "aos_sales")
            .maxP("createtime", "aos_sche_qty").finish();
        while (aosSalScheSummaryS.hasNext()) {
            Row aosSalScheSummary = aosSalScheSummaryS.next();
            HashMap<String, Object> info = new HashMap<>(16);
            info.put("aos_sales", aosSalScheSummary.get("aos_sales"));
            info.put("aos_sche_qty", aosSalScheSummary.get("aos_sche_qty"));
            salSummary.put(aosSalScheSummary.getString("aos_productno"), info);
        }
        aosSalScheSummaryS.close();
        return salSummary;
    }

    public static HashMap<String, Map<String, Object>> generatePpcYesterSerial(Object pOuCode) {
        HashMap<String, Map<String, Object>> ppcYester = new HashMap<>(16);
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
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter qfType = new QFilter("aos_groupstatus", "=", "AVAILABLE");
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, qfType, filterOrg};
        String selectColumn = "aos_productno," + "aos_budget";
        DataSet aosMktPopularPpcS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateSalSummarySerial",
            "aos_mkt_ppcst_data", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_productno", "aos_budget"};
        aosMktPopularPpcS = aosMktPopularPpcS.groupBy(groupBy).finish();
        while (aosMktPopularPpcS.hasNext()) {
            Row aosMktPopularPpc = aosMktPopularPpcS.next();
            HashMap<String, Object> info = new HashMap<>(16);
            // 上次预算
            info.put("aos_budget", aosMktPopularPpc.get("aos_budget"));
            ppcYester.put(aosMktPopularPpc.getString("aos_productno"), info);
        }
        aosMktPopularPpcS.close();
        return ppcYester;
    }

    public static HashMap<String, Map<String, Object>> generateSkuRptSerial(Object pOuCode) {
        HashMap<String, Map<String, Object>> skuRpt = new HashMap<>(16);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date dateTo = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, -7);
        Date dateFrom = calendar.getTime();
        // 日期格式化
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<=", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterOrg};
        String selectColumn = "aos_entryentity.aos_cam_name aos_productno," + "aos_entryentity.aos_spend aos_spend,"
            + "aos_entryentity.aos_sales aos_sales";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateSkuRptSerial" + pOuCode,
            "aos_base_pointrpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_productno"};
        aosBaseSkupoprptS = aosBaseSkupoprptS.groupBy(groupBy).sum("aos_spend").sum("aos_sales").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            HashMap<String, Object> info = new HashMap<>(16);
            info.put("aos_spend", aosBaseSkupoprpt.get("aos_spend"));
            info.put("aos_total_sales", aosBaseSkupoprpt.get("aos_sales"));
            skuRpt.put(aosBaseSkupoprpt.getString("aos_productno"), info);
        }
        aosBaseSkupoprptS.close();
        return skuRpt;
    }

    public static HashMap<String, Map<String, Object>> generateSkuRptDetailSerial(Object pOuCode) {
        HashMap<String, Map<String, Object>> skuRptDetail = new HashMap<>(16);
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
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<=", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterOrg};
        String selectColumn = "aos_entryentity.aos_cam_name aos_productno," + "aos_entryentity.aos_spend aos_spend";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet(
            "AosMktGenerate.GenerateSkuRptDetailSerial" + pOuCode, "aos_base_pointrpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_productno"};
        aosBaseSkupoprptS = aosBaseSkupoprptS.groupBy(groupBy).sum("aos_spend").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            Map<String, Object> info = new HashMap<>(16);
            info.put("aos_spend", aosBaseSkupoprpt.get("aos_spend"));
            skuRptDetail.put(aosBaseSkupoprpt.getString("aos_productno"), info);
        }
        aosBaseSkupoprptS.close();
        return skuRptDetail;
    }

    public static HashMap<String, Map<String, Map<String, Object>>> generateSkuRpt3SerialObject(Object pOuCode) {
        HashMap<String, Map<String, Map<String, Object>>> skuRpt = new HashMap<>(16);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date dateTo = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, -3);
        Date dateFrom = calendar.getTime();
        // 日期格式化
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<=", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterOrg};
        String selectColumn = "aos_entryentity.aos_cam_name aos_productno," + "aos_entryentity.aos_date_l aos_date_l,"
            + "aos_entryentity.aos_impressions aos_impressions," + "aos_entryentity.aos_spend aos_spend,"
            + "aos_entryentity.aos_sales aos_sales," + "aos_entryentity.aos_clicks aos_clicks";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet(
            "AosMktGenerate.GenerateSkuRpt3SerialObject" + pOuCode, "aos_base_pointrpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_productno", "aos_date_l"};
        aosBaseSkupoprptS = aosBaseSkupoprptS.groupBy(groupBy).sum("aos_clicks").sum("aos_impressions").sum("aos_spend")
            .sum("aos_sales").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            Map<String, Object> detail = new HashMap<>(16);
            detail.put("aos_spend", aosBaseSkupoprpt.get("aos_spend"));
            detail.put("aos_total_sales", aosBaseSkupoprpt.get("aos_sales"));
            detail.put("aos_impressions", aosBaseSkupoprpt.get("aos_impressions"));
            BigDecimal aosRoi = BigDecimal.ZERO;
            if (aosBaseSkupoprpt.get("aos_spend") != null
                && ((BigDecimal)aosBaseSkupoprpt.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
                aosRoi = ((BigDecimal)aosBaseSkupoprpt.get("aos_sales"))
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
            Map<String, Map<String, Object>> info = skuRpt.get(aosBaseSkupoprpt.getString("aos_productno"));
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(aosDateStr, detail);
            skuRpt.put(aosBaseSkupoprpt.getString("aos_productno"), info);
        }
        aosBaseSkupoprptS.close();
        return skuRpt;
    }

    public static HashMap<String, Map<String, Object>> generateSkuRpt3Group(String pOuCode) {
        HashMap<String, Map<String, Object>> keyRpt3 = new HashMap<>(16);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date dateTo = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, -3);
        Date dateFrom = calendar.getTime();
        // 日期格式化
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<=", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterOrg};
        String selectColumn = "aos_entryentity.aos_ad_name aos_ad_name,"
            + "aos_entryentity.aos_impressions aos_impressions," + "aos_entryentity.aos_spend aos_spend,"
            + "aos_entryentity.aos_sales aos_sales," + "aos_entryentity.aos_clicks aos_clicks";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateSkuRpt3Group" + pOuCode,
            "aos_base_pointrpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_ad_name"};
        aosBaseSkupoprptS = aosBaseSkupoprptS.groupBy(groupBy).sum("aos_impressions").sum("aos_spend").sum("aos_sales")
            .sum("aos_clicks").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            HashMap<String, Object> info = new HashMap<>(16);
            String key = aosBaseSkupoprpt.getString("aos_ad_name");
            info.put("aos_spend", aosBaseSkupoprpt.get("aos_spend"));
            info.put("aos_sales", aosBaseSkupoprpt.get("aos_sales"));
            info.put("aos_impressions", aosBaseSkupoprpt.get("aos_impressions"));
            info.put("aos_clicks", aosBaseSkupoprpt.get("aos_clicks"));
            keyRpt3.put(key, info);
        }
        aosBaseSkupoprptS.close();
        return keyRpt3;
    }

    public static HashMap<String, Map<String, Object>> generateSkuRpt7Group(String pOuCode) {
        HashMap<String, Map<String, Object>> keyRpt7 = new HashMap<>(16);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date dateTo = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, -7);
        Date dateFrom = calendar.getTime();
        // 日期格式化
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter filterDateFrom = new QFilter("aos_entryentity.aos_date_l", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_entryentity.aos_date_l", "<=", dateToStr);
        QFilter filterDate = new QFilter("aos_date", "=", dateToStr);
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterDate, filterOrg};
        String selectColumn = "aos_entryentity.aos_ad_name aos_ad_name,"
            + "aos_entryentity.aos_impressions aos_impressions," + "aos_entryentity.aos_spend aos_spend,"
            + "aos_entryentity.aos_sales aos_sales," + "aos_entryentity.aos_clicks aos_clicks";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateSkuRpt7Group" + pOuCode,
            "aos_base_pointrpt", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_ad_name"};
        aosBaseSkupoprptS = aosBaseSkupoprptS.groupBy(groupBy).sum("aos_impressions").sum("aos_spend").sum("aos_sales")
            .sum("aos_clicks").finish();
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            HashMap<String, Object> info = new HashMap<>(16);
            String key = aosBaseSkupoprpt.getString("aos_ad_name");
            info.put("aos_spend", aosBaseSkupoprpt.get("aos_spend"));
            info.put("aos_sales", aosBaseSkupoprpt.get("aos_sales"));
            info.put("aos_impressions", aosBaseSkupoprpt.get("aos_impressions"));
            info.put("aos_clicks", aosBaseSkupoprpt.get("aos_clicks"));
            keyRpt7.put(key, info);
        }
        aosBaseSkupoprptS.close();
        return keyRpt7;
    }

    public static HashMap<String, Map<String, Object>> generateKeyDetailToday(String pOuCode, long pGroupId) {
        HashMap<String, Map<String, Object>> keyDetailToday = new HashMap<>(16);
        Date today = FndDate.zero(new Date());
        QFilter qfDate = new QFilter("aos_date", "=", today);
        QFilter qfOrg = new QFilter("aos_orgid.number", "=", pOuCode);
        QFilter qfType = new QFilter("aos_groupstatus", "=", "AVAILABLE");
        QFilter qfTypekey = new QFilter("aos_keystatus", "=", "AVAILABLE");
        QFilter qfGroup = new QFilter("aos_sal_group", "=", pGroupId);
        QFilter[] filters = new QFilter[] {qfDate, qfOrg, qfType, qfTypekey, qfGroup};
        String selectColumn = "aos_itemnumer aos_ad_name,aos_keyword,aos_match_type";
        DataSet aosBaseSkupoprptS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateKeyDetailToday" + pOuCode,
            "aos_mkt_ppcst_data", selectColumn, filters, null);
        while (aosBaseSkupoprptS.hasNext()) {
            Row aosBaseSkupoprpt = aosBaseSkupoprptS.next();
            String key = aosBaseSkupoprpt.getString("aos_ad_name");
            Map<String, Object> info = keyDetailToday.get(key);
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(aosBaseSkupoprpt.getString("aos_keyword") + "~" + aosBaseSkupoprpt.getString("aos_match_type"),
                aosBaseSkupoprpt.getString("aos_keyword") + "~" + aosBaseSkupoprpt.getString("aos_match_type"));
            keyDetailToday.put(key, info);
        }
        aosBaseSkupoprptS.close();
        return keyDetailToday;
    }

    public static HashMap<String, Map<String, Map<String, Object>>> generateYesterStSerial7D(String pOuCode) {
        HashMap<String, Map<String, Map<String, Object>>> ppcYster7 = new HashMap<>(16);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date dateTo = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, -7);
        Date dateFrom = calendar.getTime();
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter filterDateFrom = new QFilter("aos_date", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_date", "<=", dateToStr);
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterOrg};
        String selectColumn = "aos_date,aos_budget,aos_productno,1 aos_keycount";
        DataSet aosMktPopularPpcS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateYesterSTSerial7D" + pOuCode,
            "aos_mkt_ppcst_data", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_date", "aos_productno", "aos_budget"};
        aosMktPopularPpcS = aosMktPopularPpcS.groupBy(groupBy).sum("aos_keycount").finish();
        while (aosMktPopularPpcS.hasNext()) {
            Row aosMktPopularPpc = aosMktPopularPpcS.next();
            Map<String, Object> detail = new HashMap<>(16);
            detail.put("aos_budget", aosMktPopularPpc.get("aos_budget"));
            detail.put("aos_keycount", aosMktPopularPpc.get("aos_keycount"));
            Date aosDate = aosMktPopularPpc.getDate("aos_date");
            String aosDateStr = df.format(aosDate);
            String key = aosMktPopularPpc.getString("aos_productno");
            Map<String, Map<String, Object>> info = ppcYster7.get(key);
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(aosDateStr, detail);
            ppcYster7.put(key, info);
        }
        aosMktPopularPpcS.close();
        return ppcYster7;
    }

    public static HashMap<String, Map<String, Object>> generateYesterStSerial7G(String pOuCode) {
        HashMap<String, Map<String, Object>> ppcYster7 = new HashMap<>(16);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        Date dateTo = calendar.getTime();
        calendar.add(Calendar.DAY_OF_MONTH, -7);
        Date dateFrom = calendar.getTime();
        // 日期格式化
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String dateFromStr = writeFormat.format(dateFrom);
        String dateToStr = writeFormat.format(dateTo);
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter filterDateFrom = new QFilter("aos_date", ">=", dateFromStr);
        QFilter filterDateTo = new QFilter("aos_date", "<=", dateToStr);
        QFilter[] filters = new QFilter[] {filterDateFrom, filterDateTo, filterOrg};
        String selectColumn = "aos_date,aos_productno,aos_itemnumer";
        DataSet aosMktPopularPpcS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateYesterSTSerial7G" + pOuCode,
            "aos_mkt_ppcst_data", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_date", "aos_productno", "aos_itemnumer"};
        aosMktPopularPpcS = aosMktPopularPpcS.groupBy(groupBy).finish();
        while (aosMktPopularPpcS.hasNext()) {
            Row aosMktPopularPpc = aosMktPopularPpcS.next();
            Date aosDate = aosMktPopularPpc.getDate("aos_date");
            String aosDateStr = df.format(aosDate);
            String key = aosMktPopularPpc.getString("aos_productno");
            Map<String, Object> info = ppcYster7.get(key);
            if (info == null) {
                info = new HashMap<>(16);
            }

            if (info.get(aosDateStr) == null) {
                info.put(aosDateStr, 1);
            } else {
                info.put(aosDateStr, (int)info.get(aosDateStr) + 1);
            }
            ppcYster7.put(key, info);
        }
        aosMktPopularPpcS.close();
        return ppcYster7;
    }

    /** 初始化组关键词 **/
    public static HashMap<String, Map<String, Object>> generateStBase(Object pOuCode) {
        HashMap<String, Map<String, Object>> stBase = new HashMap<>(16);
        QFilter filterOrg = new QFilter("aos_orgid.number", QCP.equals, pOuCode);
        QFilter[] filters = new QFilter[] {filterOrg};
        String selectColumn = "aos_itemnumer,aos_keyword,aos_match_type,aos_keystatus aos_status";
        DataSet aosMktPopBasestS = QueryServiceHelper.queryDataSet("AosMktGenerate.GeneratePpcStLastItemName" + pOuCode,
            "aos_mkt_pop_basest", selectColumn, filters, null);
        String[] groupBy = new String[] {"aos_itemnumer", "aos_keyword", "aos_match_type", "aos_status"};
        aosMktPopBasestS = aosMktPopBasestS.groupBy(groupBy).finish();
        while (aosMktPopBasestS.hasNext()) {
            Row aosMktPopBasest = aosMktPopBasestS.next();
            String aosItemnumer = aosMktPopBasest.getString("aos_itemnumer");
            String aosKeyword = aosMktPopBasest.getString("aos_keyword");
            String aosMatchType = aosMktPopBasest.getString("aos_match_type");
            String aosStatus = aosMktPopBasest.getString("aos_status");
            Map<String, Object> info = stBase.get(aosItemnumer);
            if (info == null) {
                info = new HashMap<>(16);
            }
            info.put(aosKeyword + "~" + aosMatchType, aosStatus);
            stBase.put(aosItemnumer, info);
        }
        aosMktPopBasestS.close();
        return stBase;
    }

}
