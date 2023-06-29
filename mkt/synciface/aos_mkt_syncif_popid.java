package mkt.synciface;

import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import common.sal.impl.ComImpl;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.exception.KDException;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

public class aos_mkt_syncif_popid extends AbstractTask {
	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		do_operate(param);
	}

	public static void do_operate(Map<String, Object> param) {
		JSONArray p_ret_cursor = ComImpl.GetCursorMms(param, "CUXPOPID_MMS");
		int length = p_ret_cursor.size();
		if (length > 0) {
			// 删除接口表中原有数据
			DeleteServiceHelper.delete("aos_base_popid", null);
			for (int i = 0; i < length; i++) {
				JSONObject SkuPopRpt = (JSONObject) p_ret_cursor.get(i);
				// 插入新的数据
				DynamicObject aos_base_popid = BusinessDataServiceHelper.newDynamicObject("aos_base_popid");
				aos_base_popid.set("billstatus", "A");
				aos_base_popid.set("aos_productno", SkuPopRpt.get("aos_productno"));
				aos_base_popid.set("aos_itemnumer", SkuPopRpt.get("aos_itemnumer"));
				aos_base_popid.set("aos_shopsku", SkuPopRpt.get("aos_shopsku"));
				aos_base_popid.set("aos_serialid", SkuPopRpt.get("aos_serialid"));
				aos_base_popid.set("aos_groupid", SkuPopRpt.get("aos_groupid"));
				aos_base_popid.set("aos_shopskuid", SkuPopRpt.get("aos_shopskuid"));
				SaveServiceHelper.saveOperate("aos_base_popid", new DynamicObject[] { aos_base_popid },
						OperateOption.create());
			}
		}
	}
}