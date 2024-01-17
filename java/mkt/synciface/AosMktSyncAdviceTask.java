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
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;

/**
 * @author aosom
 * @version 建议价接口-调度任务类
 */
public class AosMktSyncAdviceTask extends AbstractTask {
    public static void doOperate(Map<String, Object> param) {
        JSONObject obj = ComImpl2.GetCursorEsb(param, "CUXADVICE_MMS", "CUX_MMS_BASIC");
        JSONArray pRetCursor = obj.getJSONArray("p_real_model");
        int length = pRetCursor.size();
        Date today = FndDate.zero(new Date());
        if (length > 0) {
            DynamicObject aosBaseAdvrpt = BusinessDataServiceHelper.newDynamicObject("aos_base_advrpt");
            DynamicObjectCollection aosEntryentityS = aosBaseAdvrpt.getDynamicObjectCollection("aos_entryentity");
            aosBaseAdvrpt.set("billstatus", "A");
            aosBaseAdvrpt.set("aos_date", today);
            for (int i = 0; i < length; i++) {
                JSONObject adPopRpt = pRetCursor.getJSONObject(i);
                Object aosOuName = adPopRpt.get("aos_ou_name");
                Object aosAdName = adPopRpt.get("aos_ad_name");
                Object aosBidRangeend = adPopRpt.get("aos_bid_rangeend");
                Object aosBidRangestart = adPopRpt.get("aos_bid_rangestart");
                Object aosBidSuggest = adPopRpt.get("aos_bid_suggest");
                Object pOrgId = FndGlobal.get_import_id(aosOuName, "bd_country");
                DynamicObject aosEntryentity = aosEntryentityS.addNew();
                aosEntryentity.set("aos_orgid", pOrgId);
                aosEntryentity.set("aos_ad_name", aosAdName);
                aosEntryentity.set("aos_bid_rangeend", aosBidRangeend);
                aosEntryentity.set("aos_bid_rangestart", aosBidRangestart);
                aosEntryentity.set("aos_bid_suggest", aosBidSuggest);
            }
            OperationServiceHelper.executeOperate("save", "aos_base_advrpt", new DynamicObject[] {aosBaseAdvrpt},
                OperateOption.create());
        }
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        doOperate(param);
    }
}