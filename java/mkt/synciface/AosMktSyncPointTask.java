package mkt.synciface;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import common.fnd.FndDate;
import common.fnd.FndGlobal;
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

/**
 * @author aosom
 * @version 关键词报告接口-调度任务类
 */
public class AosMktSyncPointTask extends AbstractTask {
    public static void executerun() {
        DeleteServiceHelper.delete("aos_base_pointrpt", null);
        QFilter qfTime;
        Calendar today = Calendar.getInstance();
        int hour = today.get(Calendar.HOUR_OF_DAY);
        QFilter overseaFlag = new QFilter("aos_is_oversea_ou.number", "=", "Y");
        QFilter overseaFlag2 = new QFilter("aos_isomvalid", "=", true);
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
        QFilter[] filtersOu = new QFilter[] {overseaFlag, overseaFlag2, qfTime};
        DynamicObjectCollection bdCountry = QueryServiceHelper.query("bd_country", "id,number", filtersOu);
        for (DynamicObject ou : bdCountry) {
            String pOuCode = ou.getString("number");
            Map<String, Object> params = new HashMap<>(16);
            params.put("ou_name", pOuCode);
            doOperate(params);
        }
    }

    public static void doOperate(Map<String, Object> param) {
        Object pOuCode = param.get("ou_name");
        Object pOrgId = FndGlobal.get_import_id(pOuCode, "bd_country");
        Date today = FndDate.zero(new Date());
        DynamicObject aosBasePointrpt = BusinessDataServiceHelper.newDynamicObject("aos_base_pointrpt");
        aosBasePointrpt.set("billstatus", "A");
        aosBasePointrpt.set("aos_orgid", pOrgId);
        aosBasePointrpt.set("aos_date", today);
        DynamicObjectCollection aosEntryentityS = aosBasePointrpt.getDynamicObjectCollection("aos_entryentity");
        String org = AosMktSyncPointKeyTask.getOrg(pOuCode.toString());
        DynamicObjectCollection rptS = QueryServiceHelper.query("aos_base_pointrpt_dp",
            "aos_date_l,aos_cam_name,aos_ad_name,aos_targeting,aos_match_type,"
                + "aos_search_term,aos_impressions,aos_clicks,aos_spend,aos_sales,"
                + "aos_total_order,aos_total_units,aos_same_sku_units,aos_other_sku_units,"
                + "aos_same_sku_sales,aos_other_sku_sales,aos_up_date",
            new QFilter("aos_org", QCP.equals, org).toArray());
        for (DynamicObject rpt : rptS) {
            DynamicObject aosEntryentity = aosEntryentityS.addNew();
            aosEntryentity.set("aos_date_l", rpt.get("aos_date_l"));
            aosEntryentity.set("aos_cam_name", rpt.get("aos_cam_name"));
            aosEntryentity.set("aos_ad_name", rpt.get("aos_ad_name"));
            aosEntryentity.set("aos_targeting", rpt.get("aos_targeting"));
            aosEntryentity.set("aos_match_type", rpt.get("aos_match_type"));
            aosEntryentity.set("aos_search_term", rpt.get("aos_search_term"));
            aosEntryentity.set("aos_impressions", rpt.get("aos_impressions"));
            aosEntryentity.set("aos_clicks", rpt.get("aos_clicks"));
            aosEntryentity.set("aos_spend", rpt.get("aos_spend"));
            aosEntryentity.set("aos_sales", rpt.get("aos_sales"));
            aosEntryentity.set("aos_total_order", rpt.get("aos_total_order"));
            aosEntryentity.set("aos_total_units", rpt.get("aos_total_units"));
            aosEntryentity.set("aos_same_sku_units", rpt.get("aos_same_sku_units"));
            aosEntryentity.set("aos_other_sku_units", rpt.get("aos_other_sku_units"));
            aosEntryentity.set("aos_same_sku_sales", rpt.get("aos_same_sku_sales"));
            aosEntryentity.set("aos_other_sku_sales", rpt.get("aos_other_sku_sales"));
            aosEntryentity.set("aos_up_date", rpt.get("aos_up_date"));
        }
        SaveServiceHelper.save(new DynamicObject[] {aosBasePointrpt});
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        executerun();
    }
}