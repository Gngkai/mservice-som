package mkt.synciface;

import java.util.Date;
import java.util.HashMap;
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
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;

/**
 * @author aosom
 * @version 最低定价结存-调度任务类
 */
public class AosMktPriceBakTask extends AbstractTask {
    public static void doOperate(Map<String, Object> param) {
        Date today = FndDate.zero(new Date());
        // 删除今日数据
        DeleteServiceHelper.delete("aos_base_przbak", new QFilter[] {new QFilter("aos_date", "=", today)});
        JSONObject obj = ComImpl2.GetCursorEsb(param, "CUXPRZBAK_MMS", "CUX_MMS_BASIC");
        JSONArray pRetCursor = obj.getJSONArray("p_real_model");
        int length = pRetCursor.size();
        if (length > 0) {
            DynamicObject aosBasePrzbak = BusinessDataServiceHelper.newDynamicObject("aos_base_przbak");
            aosBasePrzbak.set("aos_date", today);
            DynamicObjectCollection aosEntryentityS = aosBasePrzbak.getDynamicObjectCollection("aos_entryentity");
            for (int i = 0; i < length; i++) {
                HashMap<String, Object> rankJson = (HashMap<String, Object>)pRetCursor.get(i);
                Object aosOrgid = FndGlobal.get_import_id(rankJson.get("OU_NAME"), "bd_country");
                Object aosItemid = FndGlobal.get_import_id(rankJson.get("MHSKU"), "bd_material");
                DynamicObject aosEntryentity = aosEntryentityS.addNew();
                aosEntryentity.set("aos_orgid", aosOrgid);
                aosEntryentity.set("aos_itemid", aosItemid);
                aosEntryentity.set("aos_currentprice", rankJson.get("MIN_PRICE"));
            }
            OperationServiceHelper.executeOperate("save", "aos_base_przbak", new DynamicObject[] {aosBasePrzbak},
                OperateOption.create());
        }
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        doOperate(param);
    }
}