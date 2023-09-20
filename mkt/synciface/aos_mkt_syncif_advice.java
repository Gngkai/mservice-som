package mkt.synciface;

import java.util.Calendar;
import java.util.Map;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import common.sal.impl.ComImpl;
import common.sal.impl.ComImpl2;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.exception.KDException;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import sal.synciface.imp.aos_sal_import_pub;

public class aos_mkt_syncif_advice extends AbstractTask {
	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		do_operate(param);
	}

	public static void do_operate(Map<String, Object> param) {
		
		JSONObject obj = ComImpl2.GetCursorEsb(param, "CUXADVICE_MMS","CUX_MMS_BASIC");
		JSONArray p_ret_cursor = obj.getJSONArray("p_real_model");
		
		
		int length = p_ret_cursor.size();
		Calendar Today = Calendar.getInstance();
		Today.set(Calendar.HOUR_OF_DAY, 0);
		Today.set(Calendar.MINUTE, 0);
		Today.set(Calendar.SECOND, 0);
		Today.set(Calendar.MILLISECOND, 0);
		if (length > 0) {
			DynamicObject aos_base_advrpt = BusinessDataServiceHelper.newDynamicObject("aos_base_advrpt");
			DynamicObjectCollection aos_entryentityS = aos_base_advrpt.getDynamicObjectCollection("aos_entryentity");
			aos_base_advrpt.set("billstatus", "A");
			aos_base_advrpt.set("aos_date", Today.getTime());
			for (int i = 0; i < length; i++) {
				JSONObject AdPopRpt = (JSONObject) p_ret_cursor.get(i);
				Object aos_ou_name = AdPopRpt.get("aos_ou_name");
				Object aos_ad_name = AdPopRpt.get("aos_ad_name");
				Object aos_bid_rangeend = AdPopRpt.get("aos_bid_rangeend");
				Object aos_bid_rangestart = AdPopRpt.get("aos_bid_rangestart");
				Object aos_bid_suggest = AdPopRpt.get("aos_bid_suggest");
				Object p_org_id = aos_sal_import_pub.get_import_id(aos_ou_name, "bd_country");
				DynamicObject aos_entryentity = aos_entryentityS.addNew();
				aos_entryentity.set("aos_orgid", p_org_id);
				aos_entryentity.set("aos_ad_name", aos_ad_name);
				aos_entryentity.set("aos_bid_rangeend", aos_bid_rangeend);
				aos_entryentity.set("aos_bid_rangestart", aos_bid_rangestart);
				aos_entryentity.set("aos_bid_suggest", aos_bid_suggest);
			}
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_base_advrpt",
					new DynamicObject[] { aos_base_advrpt }, OperateOption.create());
			if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
			}
		}
	}
}