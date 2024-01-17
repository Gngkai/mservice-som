package mkt.synciface;

import java.util.Date;
import java.util.Map;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import common.fnd.FndDate;
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

/**
 * @author aosom
 * @version 流量转化率报告接口-调度任务类
 * @deprecated 未启用
 */
public class AosMktSyncFlowTask extends AbstractTask {
    public static Object getShopId(Object shopName, Object orgId) {
        try {
            Object id = 0;
            QFilter filterName = new QFilter("name", "=", shopName);
            QFilter filterOrg = new QFilter("aos_org", "=", orgId);
            QFilter[] filters = new QFilter[] {filterName, filterOrg};
            String selectFields = "id";
            DynamicObject dynamicObject = QueryServiceHelper.queryOne("aos_sal_shop", selectFields, filters);
            if (dynamicObject == null) {
                id = 0;
            } else {
                id = dynamicObject.getLong("id");
            }
            return id;
        } catch (Exception e) {
            return 0;
        }
    }

    public static void doOperate(Map<String, Object> param) {
        JSONObject obj = ComImpl2.GetCursorEsb(param, "CUXFLOW_MMS", "CUX_MMS_BASIC");
        JSONArray pRetCursor = obj.getJSONArray("p_real_model");
        int length = pRetCursor.size();
        Date today = FndDate.zero(new Date());
        if (length > 0) {
            DynamicObject aosBaseFlowrpt = BusinessDataServiceHelper.newDynamicObject("aos_base_flowrpt");
            DynamicObjectCollection aosEntryentityS = aosBaseFlowrpt.getDynamicObjectCollection("aos_entryentity");
            aosBaseFlowrpt.set("billstatus", "A");
            aosBaseFlowrpt.set("aos_date", today);
            for (int i = 0; i < length; i++) {
                JSONObject flowRpt = (JSONObject)pRetCursor.get(i);
                Object aosOuName = flowRpt.get("aos_ou_name");
                Object aosSalesChannel = flowRpt.get("aos_sales_channel");
                Object aosStores = flowRpt.get("aos_stores");
                Object aosAsin = flowRpt.get("aos_asin");
                Object aosSku = flowRpt.get("aos_sku");
                Object aosSessions = flowRpt.get("aos_sessions");
                Object aosPageViews = flowRpt.get("aos_page_views");
                Object aosUnitsOrder = flowRpt.get("aos_units_order");
                String aosUpDateStr = flowRpt.get("aos_up_date").toString();
                Date aosUpDate = AosMktSyncConnectUtil.parseDate(aosUpDateStr);
                Object pOrgId = FndGlobal.get_import_id(aosOuName, "bd_country");
                // 货号id
                Object itemId = FndGlobal.get_import_id(aosSku, "bd_material");
                Object platformId = FndGlobal.get_import_id(aosSalesChannel, "aos_sal_channel");
                Object shopId = getShopId(aosStores, pOrgId);
                DynamicObject aosEntryentity = aosEntryentityS.addNew();
                aosEntryentity.set("aos_orgid", pOrgId);
                aosEntryentity.set("aos_itemid", itemId);
                aosEntryentity.set("aos_sales_channel", platformId);
                aosEntryentity.set("aos_stores", shopId);
                aosEntryentity.set("aos_asin", aosAsin);
                aosEntryentity.set("aos_sessions", aosSessions);
                aosEntryentity.set("aos_page_views", aosPageViews);
                aosEntryentity.set("aos_units_order", aosUnitsOrder);
                aosEntryentity.set("aos_up_date", aosUpDate);
            }
            OperationServiceHelper.executeOperate("save", "aos_base_flowrpt", new DynamicObject[] {aosBaseFlowrpt},
                OperateOption.create());
        }
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        // 流量转化率报告每天下午四点更新数据
        doOperate(param);
    }
}