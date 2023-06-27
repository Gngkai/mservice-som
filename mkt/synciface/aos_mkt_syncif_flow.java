package mkt.synciface;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import common.sal.impl.ComImpl;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import sal.synciface.imp.aos_sal_import_pub;

public class aos_mkt_syncif_flow extends AbstractTask {

	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		// 流量转化率报告每天下午四点更新数据
		do_operate(param);
	}

	public static void do_operate(Map<String, Object> param) {
		JSONArray p_ret_cursor = ComImpl.GetCursorMms(param, "CUXFLOW_MMS");
		int length = p_ret_cursor.size();
		System.out.println("length =" + length);
		Calendar Today = Calendar.getInstance();
		Today.set(Calendar.HOUR_OF_DAY, 0);
		Today.set(Calendar.MINUTE, 0);
		Today.set(Calendar.SECOND, 0);
		Today.set(Calendar.MILLISECOND, 0);
		if (length > 0) {
			DynamicObject aos_base_flowrpt = BusinessDataServiceHelper.newDynamicObject("aos_base_flowrpt");
			DynamicObjectCollection aos_entryentityS = aos_base_flowrpt.getDynamicObjectCollection("aos_entryentity");
			aos_base_flowrpt.set("billstatus", "A");
			aos_base_flowrpt.set("aos_date", Today.getTime());
			for (int i = 0; i < length; i++) {
				JSONObject FlowRpt = (JSONObject) p_ret_cursor.get(i);
				Object aos_ou_name = FlowRpt.get("aos_ou_name");
				Object aos_sales_channel = FlowRpt.get("aos_sales_channel");
				Object aos_stores = FlowRpt.get("aos_stores");
				Object aos_asin = FlowRpt.get("aos_asin");
				Object aos_sku = FlowRpt.get("aos_sku");
				Object aos_sessions = FlowRpt.get("aos_sessions");
				Object aos_page_views = FlowRpt.get("aos_page_views");
				Object aos_units_order = FlowRpt.get("aos_units_order");
				String aos_up_date_str = FlowRpt.get("aos_up_date").toString();
				Date aos_up_date = aos_mkt_syncif_connect.parse_date(aos_up_date_str);
				Object p_org_id = aos_sal_import_pub.get_import_id(aos_ou_name, "bd_country");
				Object item_id = aos_sal_import_pub.get_import_id(aos_sku, "bd_material");// 货号id
				Object platform_id = aos_sal_import_pub.get_import_id(aos_sales_channel, "aos_sal_channel");
				Object shop_id = aos_sal_import_pub.get_shop_id(aos_stores, p_org_id);
				DynamicObject aos_entryentity = aos_entryentityS.addNew();
				aos_entryentity.set("aos_orgid", p_org_id);
				aos_entryentity.set("aos_itemid", item_id);
				aos_entryentity.set("aos_sales_channel", platform_id);
				aos_entryentity.set("aos_stores", shop_id);
				aos_entryentity.set("aos_asin", aos_asin);
				aos_entryentity.set("aos_sessions", aos_sessions);
				aos_entryentity.set("aos_page_views", aos_page_views);
				aos_entryentity.set("aos_units_order", aos_units_order);
				aos_entryentity.set("aos_up_date", aos_up_date);
			}
			OperationServiceHelper.executeOperate("save", "aos_base_flowrpt",
					new DynamicObject[] { aos_base_flowrpt }, OperateOption.create());
		}
	}
}