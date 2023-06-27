package mkt.synciface;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import common.fnd.FndMsg;
import common.sal.impl.ComImpl;
import common.sal.impl.ComImpl2;
import common.sal.util.SalUtil;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.threads.ThreadPools;
import sal.sche.aos_sal_sche_pub.aos_sal_sche_pub;
import sal.synciface.imp.aos_sal_import_pub;

public class aos_mkt_syncif_point extends AbstractTask {

	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		executerun();
	}

	public static void executerun() {
		QFilter qf_time = null;
		Calendar Today = Calendar.getInstance();
		int hour = Today.get(Calendar.HOUR_OF_DAY);
		long is_oversea_flag = aos_sal_sche_pub.get_lookup_values("AOS_YES_NO", "Y");
		QFilter oversea_flag = new QFilter("aos_is_oversea_ou", "=", is_oversea_flag);// 海外公司
		QFilter oversea_flag2 = new QFilter("aos_isomvalid", "=", true);
		DynamicObject dynamicObject = QueryServiceHelper.queryOne("aos_mkt_base_orgvalue", "aos_value",
				new QFilter[] { new QFilter("aos_type", QCP.equals, "TIME") });
		int time = 16;
		if (dynamicObject != null)
			time = dynamicObject.getBigDecimal("aos_value").intValue();
		if (hour < time)
			qf_time = new QFilter("aos_is_north_america", QCP.not_equals, is_oversea_flag);
		else
			qf_time = new QFilter("aos_is_north_america", QCP.equals, is_oversea_flag);
		QFilter[] filters_ou = new QFilter[] { oversea_flag, oversea_flag2, qf_time };
		DynamicObjectCollection bd_country = QueryServiceHelper.query("bd_country", "id,number", filters_ou);
		for (DynamicObject ou : bd_country) {
			String p_ou_code = ou.getString("number");
			Map<String, Object> params = new HashMap<>();
			params.put("ou_name", p_ou_code);
			do_operate(params);
			/*
			 * MktPotRunnable mktPotRunnable = new MktPotRunnable(params);
			 * ThreadPools.executeOnce("MKT_关键词报告接口_" + p_ou_code, mktPotRunnable);
			 */
		}
	}

	static class MktPotRunnable implements Runnable {
		private Map<String, Object> params = new HashMap<>();

		public MktPotRunnable(Map<String, Object> param) {
			this.params = param;
		}

		@Override
		public void run() {
			try {
				do_operate(params);
			} catch (Exception e) {
				String message = e.toString();
				String exceptionStr = SalUtil.getExceptionStr(e);
				String messageStr = message + "\r\n" + exceptionStr;
				System.out.println(messageStr);
			}
		}
	}

	public static void do_operate(Map<String, Object> param) {
		Object p_ou_code = param.get("ou_name");
		Object p_org_id = aos_sal_import_pub.get_import_id(p_ou_code, "bd_country");
		Calendar Today = Calendar.getInstance();
		Today.set(Calendar.HOUR_OF_DAY, 0);
		Today.set(Calendar.MINUTE, 0);
		Today.set(Calendar.SECOND, 0);
		Today.set(Calendar.MILLISECOND, 0);
		DynamicObject aos_base_pointrpt = BusinessDataServiceHelper.newDynamicObject("aos_base_pointrpt");
		aos_base_pointrpt.set("billstatus", "A");
		aos_base_pointrpt.set("aos_orgid", p_org_id);
		aos_base_pointrpt.set("aos_date", Today.getTime());
		DynamicObjectCollection aos_entryentityS = aos_base_pointrpt.getDynamicObjectCollection("aos_entryentity");
		param.put("ou_name", p_ou_code);
		int pageSize = 10000;
		param.put("pageSize", pageSize);
		int limit = 10;
		for (int page = 1; page <= limit; page++) {
			param.put("pageNum", page);
			JSONObject MKT_KEY_POINT = ComImpl2.GetCursorEsb(param, "MKT_POINT");
			JSONArray p_ret_cursor = MKT_KEY_POINT.getJSONArray("p_real_model");
			int pages = MKT_KEY_POINT.getInteger("pages");
			limit = pages;
			int length = p_ret_cursor.size();
			System.out.println("limit" + limit);
			System.out.println("page:" + page);
			if (length > 0) {
				for (int i = 0; i < length; i++) {
					JSONObject TargetRpt = p_ret_cursor.getJSONObject(i);
					String aos_date_l_str = TargetRpt.get("date").toString();// 日期
					Date aos_date_l = aos_mkt_syncif_connect.parse_date(aos_date_l_str);
					Object aos_cam_name = TargetRpt.get("cam_name");
					Object aos_ad_name = TargetRpt.get("ad_name");
					Object aos_targeting = TargetRpt.get("targeting");
					Object aos_match_type = TargetRpt.get("match_type");
					Object aos_search_term = TargetRpt.get("search_term");
					Object aos_impressions = TargetRpt.get("impressions");
					Object aos_clicks = TargetRpt.get("clicks");
					Object aos_spend = TargetRpt.get("spend");
					Object aos_sales = TargetRpt.get("sales");
					Object aos_total_order = TargetRpt.get("total_order");
					Object aos_total_units = TargetRpt.get("total_units");
					Object aos_same_sku_units = TargetRpt.get("same_sku_units");
					Object aos_other_sku_units = TargetRpt.get("other_sku_units");
					Object aos_same_sku_sales = TargetRpt.get("same_sku_sales");
					Object aos_other_sku_sales = TargetRpt.get("other_sku_sales");
					String aos_up_date_str = TargetRpt.get("up_date").toString();// 更新日期
					Date aos_up_date = aos_mkt_syncif_connect.parse_date(aos_up_date_str);
					DynamicObject aos_entryentity = aos_entryentityS.addNew();
					aos_entryentity.set("aos_date_l", aos_date_l);
					aos_entryentity.set("aos_cam_name", aos_cam_name);
					aos_entryentity.set("aos_ad_name", aos_ad_name);
					aos_entryentity.set("aos_targeting", aos_targeting);
					aos_entryentity.set("aos_match_type", aos_match_type);
					aos_entryentity.set("aos_search_term", aos_search_term);
					aos_entryentity.set("aos_impressions", aos_impressions);
					aos_entryentity.set("aos_clicks", aos_clicks);
					aos_entryentity.set("aos_spend", aos_spend);
					aos_entryentity.set("aos_sales", aos_sales);
					aos_entryentity.set("aos_total_order", aos_total_order);
					aos_entryentity.set("aos_total_units", aos_total_units);
					aos_entryentity.set("aos_same_sku_units", aos_same_sku_units);
					aos_entryentity.set("aos_other_sku_units", aos_other_sku_units);
					aos_entryentity.set("aos_same_sku_sales", aos_same_sku_sales);
					aos_entryentity.set("aos_other_sku_sales", aos_other_sku_sales);
					aos_entryentity.set("aos_up_date", aos_up_date);
				}
			}
		}
		OperationServiceHelper.executeOperate("save", "aos_base_pointrpt", new DynamicObject[] { aos_base_pointrpt },
				OperateOption.create());
	}
}