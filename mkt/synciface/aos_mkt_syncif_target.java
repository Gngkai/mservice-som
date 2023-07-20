package mkt.synciface;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
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
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.threads.ThreadPools;
import sal.sche.aos_sal_sche_pub.aos_sal_sche_pub;
import sal.synciface.imp.aos_sal_import_pub;

public class aos_mkt_syncif_target extends AbstractTask {

	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		executerun();
	}

	public static void executerun() {
		long is_oversea_flag = aos_sal_sche_pub.get_lookup_values("AOS_YES_NO", "Y");
		QFilter oversea_flag = new QFilter("aos_is_oversea_ou", "=", is_oversea_flag);// 海外公司
		QFilter[] filters_ou = new QFilter[] { oversea_flag };
		DynamicObjectCollection bd_country = QueryServiceHelper.query("bd_country", "id,number", filters_ou);
		for (DynamicObject ou : bd_country) {
			String p_ou_code = ou.getString("number");
			Map<String, Object> params = new HashMap<>();
			params.put("ou_name", p_ou_code);
			MktTgtRunnable mktTgtRunnable = new MktTgtRunnable(params);
			ThreadPools.executeOnce("MKT_Target报告接口_" + p_ou_code, mktTgtRunnable);
		}
	}

	static class MktTgtRunnable implements Runnable {
		private Map<String, Object> params = new HashMap<>();

		public MktTgtRunnable(Map<String, Object> param) {
			this.params = param;
		}

		@Override
		public void run() {
			try {
				do_operate(params);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public static void do_operate(Map<String, Object> param) {
		

		JSONObject obj = ComImpl2.GetCursorEsb(param, "CUXTARGET_MMS");
		JSONArray p_ret_cursor = obj.getJSONArray("p_real_model");
		
		int length = p_ret_cursor.size();
		System.out.println(length);
		Object p_ou_code = param.get("ou_name");
		Object p_org_id = aos_sal_import_pub.get_import_id(p_ou_code, "bd_country");
		Calendar Today = Calendar.getInstance();
		Today.set(Calendar.HOUR_OF_DAY, 0);
		Today.set(Calendar.MINUTE, 0);
		Today.set(Calendar.SECOND, 0);
		Today.set(Calendar.MILLISECOND, 0);
		if (length > 0) {
			DynamicObject aos_base_targetrpt = BusinessDataServiceHelper.newDynamicObject("aos_base_targetrpt");
			DynamicObjectCollection aos_entryentityS = aos_base_targetrpt.getDynamicObjectCollection("aos_entryentity");
			aos_base_targetrpt.set("billstatus", "A");
			aos_base_targetrpt.set("aos_orgid", p_org_id);
			aos_base_targetrpt.set("aos_date", Today.getTime());
			for (int i = 0; i < length; i++) {
				JSONObject TargetRpt = (JSONObject) p_ret_cursor.get(i);
				String aos_date_l_str = TargetRpt.get("aos_date").toString();// 日期
				Date aos_date_l = aos_mkt_syncif_connect.parse_date(aos_date_l_str);
				Object aos_cam_name = TargetRpt.get("aos_cam_name");
				Object aos_ad_name = TargetRpt.get("aos_ad_name");
				Object aos_targeting = TargetRpt.get("aos_targeting");
				Object aos_match_type = TargetRpt.get("aos_match_type");
				Object aos_impressions = TargetRpt.get("aos_impressions");
				Object aos_clicks = TargetRpt.get("aos_clicks");
				Object aos_spend = TargetRpt.get("aos_spend");
				Object aos_total_sale = TargetRpt.get("aos_total_sale");
				Object aos_total_order = TargetRpt.get("aos_total_order");
				Object aos_total_units = TargetRpt.get("aos_total_units");
				Object aos_same_sku_units = TargetRpt.get("aos_same_sku_units");
				Object aos_other_sku_units = TargetRpt.get("aos_other_sku_units");
				Object aos_same_sku_sales = TargetRpt.get("aos_same_sku_sales");
				Object aos_other_sku_sales = TargetRpt.get("aos_other_sku_sales");
				Object aos_up_date = TargetRpt.get("aos_up_date");
				DynamicObject aos_entryentity = aos_entryentityS.addNew();
				aos_entryentity.set("aos_date_l", aos_date_l);
				aos_entryentity.set("aos_cam_name", aos_cam_name);
				aos_entryentity.set("aos_ad_name", aos_ad_name);
				aos_entryentity.set("aos_targeting", aos_targeting);
				aos_entryentity.set("aos_match_type", aos_match_type);
				aos_entryentity.set("aos_impressions", aos_impressions);
				aos_entryentity.set("aos_clicks", aos_clicks);
				aos_entryentity.set("aos_spend", aos_spend);
				aos_entryentity.set("aos_total_sale", aos_total_sale);
				aos_entryentity.set("aos_total_order", aos_total_order);
				aos_entryentity.set("aos_total_units", aos_total_units);
				aos_entryentity.set("aos_same_sku_units", aos_same_sku_units);
				aos_entryentity.set("aos_other_sku_units", aos_other_sku_units);
				aos_entryentity.set("aos_same_sku_sales", aos_same_sku_sales);
				aos_entryentity.set("aos_other_sku_sales", aos_other_sku_sales);
				aos_entryentity.set("aos_up_date", aos_up_date);
			}
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_base_targetrpt",
					new DynamicObject[] { aos_base_targetrpt }, OperateOption.create());
			if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
			}
		}
	}
}