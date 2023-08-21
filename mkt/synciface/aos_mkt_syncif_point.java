package mkt.synciface;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import common.sal.util.SalUtil;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import sal.sche.aos_sal_sche_pub.aos_sal_sche_pub;
import sal.synciface.imp.aos_sal_import_pub;

public class aos_mkt_syncif_point extends AbstractTask {

	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		executerun();
	}

	public static void executerun() {
		DeleteServiceHelper.delete("aos_base_pointrpt", null);
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
		Date today = Today.getTime();
		DynamicObject aos_base_pointrpt = BusinessDataServiceHelper.newDynamicObject("aos_base_pointrpt");
		aos_base_pointrpt.set("billstatus", "A");
		aos_base_pointrpt.set("aos_orgid", p_org_id);
		aos_base_pointrpt.set("aos_date", today);
		DynamicObjectCollection aos_entryentityS = aos_base_pointrpt.getDynamicObjectCollection("aos_entryentity");

		String org = aos_mkt_syncif_pointkey.getOrg(p_ou_code.toString());
		DynamicObjectCollection rptS = QueryServiceHelper.query("aos_base_pointrpt_dp",
				"aos_date_l,aos_cam_name,aos_ad_name,aos_targeting,aos_match_type,"
						+ "aos_search_term,aos_impressions,aos_clicks,aos_spend,aos_sales,"
						+ "aos_total_order,aos_total_units,aos_same_sku_units,aos_other_sku_units,"
						+ "aos_same_sku_sales,aos_other_sku_sales,aos_up_date",
				new QFilter("aos_org", QCP.equals, org).toArray());
		for (DynamicObject rpt : rptS) {
			DynamicObject aos_entryentity = aos_entryentityS.addNew();
			aos_entryentity.set("aos_date_l", rpt.get("aos_date_l"));
			aos_entryentity.set("aos_cam_name", rpt.get("aos_cam_name"));
			aos_entryentity.set("aos_ad_name", rpt.get("aos_ad_name"));
			aos_entryentity.set("aos_targeting", rpt.get("aos_targeting"));
			aos_entryentity.set("aos_match_type", rpt.get("aos_match_type"));
			aos_entryentity.set("aos_search_term", rpt.get("aos_search_term"));
			aos_entryentity.set("aos_impressions", rpt.get("aos_impressions"));
			aos_entryentity.set("aos_clicks", rpt.get("aos_clicks"));
			aos_entryentity.set("aos_spend", rpt.get("aos_spend"));
			aos_entryentity.set("aos_sales", rpt.get("aos_sales"));
			aos_entryentity.set("aos_total_order", rpt.get("aos_total_order"));
			aos_entryentity.set("aos_total_units", rpt.get("aos_total_units"));
			aos_entryentity.set("aos_same_sku_units", rpt.get("aos_same_sku_units"));
			aos_entryentity.set("aos_other_sku_units", rpt.get("aos_other_sku_units"));
			aos_entryentity.set("aos_same_sku_sales", rpt.get("aos_same_sku_sales"));
			aos_entryentity.set("aos_other_sku_sales", rpt.get("aos_other_sku_sales"));
			aos_entryentity.set("aos_up_date", rpt.get("aos_up_date"));
		}
		SaveServiceHelper.save(new DynamicObject[] { aos_base_pointrpt });

		// 查询最大日期
//		DynamicObject maxQuery = QueryServiceHelper.queryOne("aos_base_pointrpt",
//				"max(aos_entryentity.aos_date_l) aos_date_l", new QFilter("aos_date", QCP.equals, today)
//						.and("aos_orgid", QCP.equals, p_org_id).toArray());
//		Date aos_date_l = maxQuery.getDate("aos_date_l");
//		if (FndDate.add_days(today, -1).compareTo(aos_date_l) > 0) 
//			FndWebHook.send(FndWebHook.urlMms, p_ou_code +":ST推广关键词报告数据未获取到最新值!");
	}
}