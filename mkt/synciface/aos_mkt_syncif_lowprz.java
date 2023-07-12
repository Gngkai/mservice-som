package mkt.synciface;

import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;

import common.sal.impl.ComImpl;
import common.sal.impl.ComImpl2;

import java.util.HashMap;
import java.util.Map;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

public class aos_mkt_syncif_lowprz extends AbstractTask {
	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		// 多线程执行
		executerun();
	}

	public static void executerun() {
		DeleteServiceHelper.delete("aos_base_lowprz", null);
		Map<String, Object> param = new HashMap<>();
		do_operate(param);

	}

	public static void do_operate(Map<String, Object> param) {

		JSONObject obj = ComImpl2.GetCursorEsb(param, "CUXLOW_MMS");
		JSONArray p_ret_cursor = obj.getJSONArray("p_real_model");
		
		
		Map<String, String> countryIdMap = queryCountryIdMap();
		Map<String, String> materialIdMap = queryMaterialIdMap();
		int length = p_ret_cursor.size();
		System.out.println("length = " + length);
		for (Object o : p_ret_cursor) {
			JSONObject LowPrzRpt = (JSONObject) o;
			DynamicObject aos_base_lowprz = BusinessDataServiceHelper.newDynamicObject("aos_base_lowprz");
			String aos_itemnum = (String) LowPrzRpt.get("MHSKU");
			String ou_name = (String) LowPrzRpt.get("OU_NAME");
			aos_base_lowprz.set("billstatus", "A");
			aos_base_lowprz.set("aos_orgid", countryIdMap.getOrDefault(ou_name, "0"));
			aos_base_lowprz.set("aos_itemid", materialIdMap.getOrDefault(aos_itemnum, "0"));
			aos_base_lowprz.set("aos_min_price", LowPrzRpt.get("min_price"));
			aos_base_lowprz.set("aos_min_price30", LowPrzRpt.get("min_price30"));
			aos_base_lowprz.set("aos_min_price365", LowPrzRpt.get("min_price365"));
			OperationServiceHelper.executeOperate("save", "aos_base_lowprz",
					new DynamicObject[]{aos_base_lowprz}, OperateOption.create());
		}
	}

	/**
	 * 国别id与简称对应
	 * @return
	 */
	public static Map<String, String> queryCountryIdMap() {
		// 将物料id取出来做转化 转成物料基础资料
		DynamicObjectCollection bd_country = QueryServiceHelper.query("bd_country", "id,number", null);
		Map<String, String> countryMap = new HashMap<>();
		for (DynamicObject obj:bd_country) {
			String number = obj.getString("number");
			String id = obj.getString("id");
			countryMap.put(number, id);
		}
		return countryMap;
	}

	/**
	 * 物料id与货号对应
	 * @return
	 */
	public static Map<String, String> queryMaterialIdMap() {
		// 将国别id取出来做转化 转化成国家地区基础资料
		DynamicObjectCollection bd_material = QueryServiceHelper.query("bd_material", "id, number", null);
		Map<String, String> skuMap = new HashMap<>();
		for (DynamicObject obj:bd_material) {
			String number = obj.getString("number");
			String id = obj.getString("id");
			skuMap.put(number, id);
		}
		return skuMap;
	}
}