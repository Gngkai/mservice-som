package mkt.synciface;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import kd.bos.exception.ErrorCode;
import common.fnd.FndDate;
import common.fnd.FndWebHook;
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

public class aos_mkt_syncif_sku extends AbstractTask {
	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		executerun();
	}

	public static void executerun() {
		QFilter qf_time = null;
		Calendar Today = Calendar.getInstance();
		int hour = Today.get(Calendar.HOUR_OF_DAY);
		long is_oversea_flag = aos_sal_sche_pub.get_lookup_values("AOS_YES_NO", "Y");
		QFilter oversea_flag = new QFilter("aos_is_oversea_ou", QCP.equals, is_oversea_flag);// 海外公司
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
		LocalDate end = LocalDate.now().minusDays(1);
		LocalDate start = end.minusDays(14);
		for (DynamicObject ou : bd_country) {
			String p_ou_code = ou.getString("number");
			Map<String, Object> params = new HashMap<>();
			params.put("ou_name", p_ou_code);
			MktSkuRunnable mktSkuRunnable = new MktSkuRunnable(params);
			ThreadPools.executeOnce("MKT_SKU报告接口_" + p_ou_code, mktSkuRunnable);
		}
	}

	static class MktSkuRunnable implements Runnable {
		private Map<String, Object> params = new HashMap<>();

		public MktSkuRunnable(Map<String, Object> param) {
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
				throw new KDException(new ErrorCode("获取关键词报告异常", exceptionStr));
			}
		}
	}

	public static void do_operate(Map<String, Object> param) {
		JSONObject obj = ComImpl2.GetCursorEsb(param, "CUXSKU_MMS", "CUX_MMS_BASIC");
		JSONArray p_ret_cursor = obj.getJSONArray("data");
		int length = p_ret_cursor.size();
		System.out.println(length);
		Object p_ou_code = param.get("ou_name");
		Object p_org_id = aos_sal_import_pub.get_import_id(p_ou_code, "bd_country");
		Calendar Today = Calendar.getInstance();
		Today.set(Calendar.HOUR_OF_DAY, 0);
		Today.set(Calendar.MINUTE, 0);
		Today.set(Calendar.SECOND, 0);
		Today.set(Calendar.MILLISECOND, 0);
		
		Date today = Today.getTime();
		
		if (length > 0) {
			DynamicObject aos_base_skupoprpt = BusinessDataServiceHelper.newDynamicObject("aos_base_skupoprpt");
			DynamicObjectCollection aos_entryentityS = aos_base_skupoprpt.getDynamicObjectCollection("aos_entryentity");
			aos_base_skupoprpt.set("billstatus", "A");
			aos_base_skupoprpt.set("aos_orgid", p_org_id);
			aos_base_skupoprpt.set("aos_date", today);
			int d = 0;
			for (int i = 0; i < length; i++) {
				d++;
				JSONObject SkuPopRpt = (JSONObject) p_ret_cursor.get(i);
				String aos_date_l_str = SkuPopRpt.get("aos_date").toString();// 日期
				Date aos_date_l = aos_mkt_syncif_connect.parse_date(aos_date_l_str);
				Object aos_cam_name = SkuPopRpt.get("aos_cam_name");// 系列名称
				Object aos_ad_name = SkuPopRpt.get("aos_ad_name");// 广告组名称
				Object aos_shopsku = SkuPopRpt.get("aos_ad_sku");// 店铺货号
				Object item_id = aos_sal_import_pub.get_import_id(aos_ad_name, "bd_material");// 货号id
				Object aos_ad_asin = SkuPopRpt.get("aos_ad_asin");// 广告ASIN
				Object aos_impressions = SkuPopRpt.get("aos_impressions");// 曝光量
				Object aos_clicks = SkuPopRpt.get("aos_clicks");// 点击量
				Object aos_spend = SkuPopRpt.get("aos_spend");// 花费金额
				Object aos_total_sales = SkuPopRpt.get("aos_total_sales");// 总销售金额
				Object aos_total_order = SkuPopRpt.get("aos_total_order");// 总订单量
				Object aos_total_units = SkuPopRpt.get("aos_total_units");// 总销售数量
				Object aos_same_sku_units = SkuPopRpt.get("aos_same_sku_units");// 广告货号销量
				Object aos_other_sku_units = SkuPopRpt.get("aos_other_sku_units");// 其他货号销量
				Object aos_same_sku_sales = SkuPopRpt.get("aos_same_sku_sales");// 广告货号销售
				Object aos_other_sku_sale = SkuPopRpt.get("aos_other_sku_sale");// 其他货号销售
				String aos_up_date_str = SkuPopRpt.get("aos_up_date").toString();// 更新日期
				Date aos_up_date = aos_mkt_syncif_connect.parse_date(aos_up_date_str);
				DynamicObject aos_entryentity = aos_entryentityS.addNew();
				aos_entryentity.set("aos_date_l", aos_date_l);
				aos_entryentity.set("aos_cam_name", aos_cam_name);
				aos_entryentity.set("aos_ad_name", aos_ad_name);
				aos_entryentity.set("aos_ad_sku", item_id);
				aos_entryentity.set("aos_shopsku", aos_shopsku);// 店铺SKU
				aos_entryentity.set("aos_ad_asin", aos_ad_asin);
				aos_entryentity.set("aos_impressions", aos_impressions);
				aos_entryentity.set("aos_clicks", aos_clicks);
				aos_entryentity.set("aos_spend", aos_spend);
				aos_entryentity.set("aos_total_sales", aos_total_sales);
				aos_entryentity.set("aos_total_order", aos_total_order);
				aos_entryentity.set("aos_total_units", aos_total_units);
				aos_entryentity.set("aos_same_sku_units", aos_same_sku_units);
				aos_entryentity.set("aos_other_sku_units", aos_other_sku_units);
				aos_entryentity.set("aos_same_sku_sales", aos_same_sku_sales);
				aos_entryentity.set("aos_other_sku_sale", aos_other_sku_sale);
				aos_entryentity.set("aos_up_date", aos_up_date);
				if (d > 5000 || i == length - 1) {
					OperationServiceHelper.executeOperate("save", "aos_base_skupoprpt",
							new DynamicObject[] { aos_base_skupoprpt }, OperateOption.create());
					d = 0;
				}
			}

			// 查询最大日期
			DynamicObject maxQuery = QueryServiceHelper.queryOne("aos_base_skupoprpt",
					"max(aos_entryentity.aos_date_l) aos_date_l", new QFilter("aos_date", QCP.equals, today)
							.and("aos_orgid", QCP.equals, p_org_id).toArray());
			Date aos_date_l = maxQuery.getDate("aos_date_l");
			if (FndDate.add_days(today, -1).compareTo(aos_date_l) > 0) 
				FndWebHook.send(FndWebHook.urlMms, p_ou_code +":SP推广SKU报告数据未获取到最新值!");
		}
	}
}