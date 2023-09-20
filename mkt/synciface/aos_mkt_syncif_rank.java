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
import kd.bos.servicehelper.operation.OperationServiceHelper;
import sal.synciface.imp.aos_sal_import_pub;

public class aos_mkt_syncif_rank extends AbstractTask {
	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		do_operate(param);
	}

	public static void do_operate(Map<String, Object> param) {
		DeleteServiceHelper.delete("aos_base_rank", null);
		JSONObject obj = ComImpl2.GetCursorEsb(param, "CUXRANK_MMS","CUX_MMS_BASIC");
		JSONArray p_ret_cursor = obj.getJSONArray("p_real_model");
		
		int length = p_ret_cursor.size();
		if (length > 0) {
			for (int i = 0; i < length; i++) {
				JSONObject RankJson = (JSONObject) p_ret_cursor.get(i);
				Object aos_orgid = aos_sal_import_pub.get_import_id(RankJson.get("ou_name"), "bd_country");
				Object aos_itemid = aos_sal_import_pub.get_import_id(RankJson.get("sku"), "bd_material");
				DynamicObject aos_base_rank = BusinessDataServiceHelper.newDynamicObject("aos_base_rank");
				aos_base_rank.set("aos_orgid", aos_orgid);
				aos_base_rank.set("aos_itemid", aos_itemid);
				aos_base_rank.set("aos_rank", RankJson.get("rank"));
				OperationServiceHelper.executeOperate("save", "aos_base_rank", new DynamicObject[] { aos_base_rank },
						OperateOption.create());
			}
		}
	}
}