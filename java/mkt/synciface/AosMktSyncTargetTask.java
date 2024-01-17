package mkt.synciface;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import common.fnd.FndGlobal;
import common.sal.impl.ComImpl2;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.threads.ThreadPools;

/**
 * @author aosom
 * @version Target同步接口-调度任务类
 * @deprecated 未启用
 */
public class AosMktSyncTargetTask extends AbstractTask {
    public static void executerun() {
        // 海外公司
        QFilter overseaFlag = new QFilter("aos_is_oversea_ou.number", "=", "Y");
        QFilter[] filtersOu = new QFilter[] {overseaFlag};
        DynamicObjectCollection bdCountry = QueryServiceHelper.query("bd_country", "id,number", filtersOu);
        for (DynamicObject ou : bdCountry) {
            String pOuCode = ou.getString("number");
            Map<String, Object> params = new HashMap<>(16);
            params.put("ou_name", pOuCode);
            MktTgtRunnable mktTgtRunnable = new MktTgtRunnable(params);
            ThreadPools.executeOnce("MKT_Target报告接口_" + pOuCode, mktTgtRunnable);
        }
    }

    public static void doOperate(Map<String, Object> param) {
        JSONObject obj = ComImpl2.GetCursorEsb(param, "CUXTARGET_MMS", "CUX_MMS_BASIC");
        JSONArray pRetCursor = obj.getJSONArray("p_real_model");
        int length = pRetCursor.size();
        Object pOuCode = param.get("ou_name");
        Object pOrgId = FndGlobal.get_import_id(pOuCode, "bd_country");
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        if (length > 0) {
            DynamicObject aosBaseTargetrpt = BusinessDataServiceHelper.newDynamicObject("aos_base_targetrpt");
            DynamicObjectCollection aosEntryentityS = aosBaseTargetrpt.getDynamicObjectCollection("aos_entryentity");
            aosBaseTargetrpt.set("billstatus", "A");
            aosBaseTargetrpt.set("aos_orgid", pOrgId);
            aosBaseTargetrpt.set("aos_date", today.getTime());
            for (int i = 0; i < length; i++) {
                JSONObject targetRpt = (JSONObject)pRetCursor.get(i);
                // 日期
                String aosDateStr = targetRpt.get("aos_date").toString();
                Date aosDateL = AosMktSyncConnectUtil.parseDate(aosDateStr);
                DynamicObject aosEntryentity = aosEntryentityS.addNew();
                aosEntryentity.set("aos_date_l", aosDateL);
                aosEntryentity.set("aos_cam_name", targetRpt.get("aos_cam_name"));
                aosEntryentity.set("aos_ad_name", targetRpt.get("aos_ad_name"));
                aosEntryentity.set("aos_targeting", targetRpt.get("aos_targeting"));
                aosEntryentity.set("aos_match_type", targetRpt.get("aos_match_type"));
                aosEntryentity.set("aos_impressions", targetRpt.get("aos_impressions"));
                aosEntryentity.set("aos_clicks", targetRpt.get("aos_clicks"));
                aosEntryentity.set("aos_spend", targetRpt.get("aos_spend"));
                aosEntryentity.set("aos_total_sale", targetRpt.get("aos_total_sale"));
                aosEntryentity.set("aos_total_order", targetRpt.get("aos_total_order"));
                aosEntryentity.set("aos_total_units", targetRpt.get("aos_total_units"));
                aosEntryentity.set("aos_same_sku_units", targetRpt.get("aos_same_sku_units"));
                aosEntryentity.set("aos_other_sku_units", targetRpt.get("aos_other_sku_units"));
                aosEntryentity.set("aos_same_sku_sales", targetRpt.get("aos_same_sku_sales"));
                aosEntryentity.set("aos_other_sku_sales", targetRpt.get("aos_other_sku_sales"));
                aosEntryentity.set("aos_up_date", targetRpt.get("aos_up_date"));
            }
            OperationServiceHelper.executeOperate("save", "aos_base_targetrpt", new DynamicObject[] {aosBaseTargetrpt},
                OperateOption.create());
        }
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        executerun();
    }

    static class MktTgtRunnable implements Runnable {
        private Map<String, Object> params = new HashMap<>();

        public MktTgtRunnable(Map<String, Object> param) {
            this.params = param;
        }

        @Override
        public void run() {
            try {
                doOperate(params);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}