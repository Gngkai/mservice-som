package mkt.synciface;

import java.util.Map;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import common.fnd.FndGlobal;
import common.sal.impl.ComImpl2;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.exception.KDException;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;

/**
 * @author aosom
 * @version AM排名接口-调度任务类
 */
public class AosMktSyncRankTask extends AbstractTask {
    public static void doOperate(Map<String, Object> param) {
        DeleteServiceHelper.delete("aos_base_rank", null);
        JSONObject obj = ComImpl2.GetCursorEsb(param, "CUXRANK_MMS", "CUX_MMS_BASIC");
        JSONArray pRetCursor = obj.getJSONArray("p_real_model");
        int length = pRetCursor.size();
        if (length > 0) {
            for (int i = 0; i < length; i++) {
                JSONObject rankJson = pRetCursor.getJSONObject(i);
                Object aosOrgid = FndGlobal.get_import_id(rankJson.get("ou_name"), "bd_country");
                Object aosItemid = FndGlobal.get_import_id(rankJson.get("sku"), "bd_material");
                DynamicObject aosBaseRank = BusinessDataServiceHelper.newDynamicObject("aos_base_rank");
                aosBaseRank.set("aos_orgid", aosOrgid);
                aosBaseRank.set("aos_itemid", aosItemid);
                aosBaseRank.set("aos_rank", rankJson.get("rank"));
                OperationServiceHelper.executeOperate("save", "aos_base_rank", new DynamicObject[] {aosBaseRank},
                    OperateOption.create());
            }
        }
    }
    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        doOperate(param);
    }
}