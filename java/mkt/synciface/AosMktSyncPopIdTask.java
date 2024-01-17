package mkt.synciface;

import java.util.Map;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import common.sal.impl.ComImpl2;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.exception.KDException;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

/**
 * @author aosom
 * @version 推广ID接口-调度任务类
 * @deprecated 未启用
 */
public class AosMktSyncPopIdTask extends AbstractTask {
    public static void doOperate(Map<String, Object> param) {
        JSONObject obj = ComImpl2.GetCursorEsb(param, "CUXPOPID_MMS", "CUX_MMS_BASIC");
        JSONArray pRetCursor = obj.getJSONArray("p_real_model");
        int length = pRetCursor.size();
        if (length > 0) {
            // 删除接口表中原有数据
            DeleteServiceHelper.delete("aos_base_popid", null);
            for (int i = 0; i < length; i++) {
                JSONObject skuPopRpt = (JSONObject)pRetCursor.get(i);
                // 插入新的数据
                DynamicObject aosBasePopid = BusinessDataServiceHelper.newDynamicObject("aos_base_popid");
                aosBasePopid.set("billstatus", "A");
                aosBasePopid.set("aos_productno", skuPopRpt.get("aos_productno"));
                aosBasePopid.set("aos_itemnumer", skuPopRpt.get("aos_itemnumer"));
                aosBasePopid.set("aos_shopsku", skuPopRpt.get("aos_shopsku"));
                aosBasePopid.set("aos_serialid", skuPopRpt.get("aos_serialid"));
                aosBasePopid.set("aos_groupid", skuPopRpt.get("aos_groupid"));
                aosBasePopid.set("aos_shopskuid", skuPopRpt.get("aos_shopskuid"));
                SaveServiceHelper.saveOperate("aos_base_popid", new DynamicObject[] {aosBasePopid},
                    OperateOption.create());
            }
        }
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        doOperate(param);
    }
}