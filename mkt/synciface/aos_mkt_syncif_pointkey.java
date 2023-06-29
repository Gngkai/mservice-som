package mkt.synciface;

import java.util.Calendar;
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
import kd.bos.exception.KDException;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;

/**
 * 关键词建议价 每周一早3点执行 执行前清除数据
 * @author aosom
 *
 */
public class aos_mkt_syncif_pointkey extends AbstractTask {
	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		orgLoop(param);
	}

	public static void orgLoop(Map<String, Object> param) {
		DeleteServiceHelper.delete("aos_base_pointadv", null);
		QFilter oversea_flag = new QFilter("aos_is_oversea_ou.number", "=", "Y");// 海外公司
		QFilter oversea_flag2 = new QFilter("aos_isomvalid", "=", true);
		QFilter[] filters_ou = new QFilter[] { oversea_flag, oversea_flag2 };
		DynamicObjectCollection bd_country = QueryServiceHelper.query("bd_country", "id,number", filters_ou);
		for (DynamicObject ou : bd_country) {
			do_operate(param, ou.getString("number"));
		}
	}

	public static void do_operate(Map<String, Object> param, String ou) {
		Map<String, Object> map = GenerateCountry();
		Object p_org_id = map.get(ou);
		Calendar Today = Calendar.getInstance();
		Today.set(Calendar.HOUR_OF_DAY, 0);
		Today.set(Calendar.MINUTE, 0);
		Today.set(Calendar.SECOND, 0);
		Today.set(Calendar.MILLISECOND, 0);
		DynamicObject aos_base_advrpt = BusinessDataServiceHelper.newDynamicObject("aos_base_pointadv");
		aos_base_advrpt.set("billstatus", "A");
		aos_base_advrpt.set("aos_date", Today.getTime());
		aos_base_advrpt.set("aos_org", p_org_id);
		DynamicObjectCollection aos_entryentityS = aos_base_advrpt.getDynamicObjectCollection("entryentity");
		param.put("ou_name", ou);
		int pageSize = 10000;
		param.put("pageSize", pageSize);
		int limit = 10;
		for (int page = 1; page <= limit; page++) {
			param.put("pageNum", page);
			JSONObject MKT_KEY_POINT = ComImpl2.GetCursorEsb(param, "MKT_KEY_POINT");
			JSONArray p_ret_cursor = MKT_KEY_POINT.getJSONArray("p_real_model");
			int pages = MKT_KEY_POINT.getInteger("pages");
			limit = pages;
			int length = p_ret_cursor.size();
			System.out.println("limit" + limit);
			System.out.println("page:" + page);
			if (length > 0) {
				for (int i = 0; i < length; i++) {
					JSONObject AdPopRpt = p_ret_cursor.getJSONObject(i);
					Object aos_ad_name = AdPopRpt.get("SKU");
					Object aos_bid_rangeend = AdPopRpt.get("RANGEEND");
					Object aos_bid_rangestart = AdPopRpt.get("RANGESTART");
					Object aos_bid_suggest = AdPopRpt.get("SUGGESTED");
					Object aos_keyword = AdPopRpt.get("KEYWORD");
					Object aos_match_type = AdPopRpt.get("MATCHTYPE");
					DynamicObject aos_entryentity = aos_entryentityS.addNew();
					aos_entryentity.set("aos_orgid", p_org_id);
					aos_entryentity.set("aos_ad_name", aos_ad_name);
					aos_entryentity.set("aos_bid_rangeend", aos_bid_rangeend);
					aos_entryentity.set("aos_bid_rangestart", aos_bid_rangestart);
					aos_entryentity.set("aos_bid_suggest", aos_bid_suggest);
					aos_entryentity.set("aos_keyword", aos_keyword);
					aos_entryentity.set("aos_match_type", aos_match_type);
				}
			}
		}

		OperationServiceHelper.executeOperate("save", "aos_base_pointadv", new DynamicObject[] { aos_base_advrpt },
				OperateOption.create());
	}

	public static Map<String, Object> GenerateCountry() {
		Map<String, Object> map = new HashMap<>();
		DynamicObjectCollection dyns = QueryServiceHelper.query("bd_country", "id,number", null);
		for (DynamicObject d : dyns) {
			map.put(d.getString("number"), d.get("id"));
		}
		return map;
	}
}
