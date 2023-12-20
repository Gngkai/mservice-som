package mkt.synciface;

import com.alibaba.fastjson.JSON;
import common.fnd.FndMsg;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;


import java.util.HashMap;
import java.util.Map;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

public class aos_mkt_syncif_lowprz extends AbstractTask {
    public static void executerun() {
        DeleteServiceHelper.delete("aos_base_lowprz", null);
        Map<String, Object> param = new HashMap<>();
        do_operate(param);

    }

    public static void do_operate(Map<String, Object> param) {

		FndMsg.debug("======into do_operate======");
//		JSONObject obj = ComImpl2.GetCursorEsb(param, "CUXLOW_MMS","CUX_MMS_BASIC");
//		JSONArray p_ret_cursor = obj.getJSONArray("p_real_model");

        try {
			// 创建 URL 对象
			URL url = new URL("https://api044.aosom.com:3443/daservice/da/listing/minprice");

			// 创建 HttpURLConnection 对象，并打开连接
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");

			// 发送 GET 请求
			int responseCode = connection.getResponseCode();

			// 读取响应内容
			BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			String line;
			StringBuilder response = new StringBuilder();
			while ((line = reader.readLine()) != null) {
				response.append(line);
			}
			reader.close();

			// 打印响应内容
//			System.out.println("Response Code: " + responseCode);
//			System.out.println("Response Body: " + response.toString());

			// 关闭连接
			connection.disconnect();
            String postjson =response.toString();
            JSONObject obj = JSON.parseObject(postjson);
            JSONArray p_ret_cursor = obj.getJSONObject("data").getJSONArray("p_real_model");
            Map<String, String> countryIdMap = queryCountryIdMap();
            Map<String, String> materialIdMap = queryMaterialIdMap();
            int length = p_ret_cursor.size();
            FndMsg.debug("length:" + length);
            for (Object o : p_ret_cursor) {
				JSONObject LowPrzRpt = (JSONObject) o;
                DynamicObject aos_base_lowprz = BusinessDataServiceHelper.newDynamicObject("aos_base_lowprz");
                String aos_itemnum = (String) LowPrzRpt.get("MHSKU");
                String ou_name = (String) LowPrzRpt.get("OU_NAME");
                aos_base_lowprz.set("billstatus", "A");
                aos_base_lowprz.set("aos_orgid", countryIdMap.getOrDefault(ou_name, "0"));
                aos_base_lowprz.set("aos_itemid", materialIdMap.getOrDefault(aos_itemnum, "0"));
                aos_base_lowprz.set("aos_min_price", LowPrzRpt.get("MIN_PRICE"));
                aos_base_lowprz.set("aos_min_price30", LowPrzRpt.get("MIN_PRICE30"));
                aos_base_lowprz.set("aos_min_price365", LowPrzRpt.get("MIN_PRICE365"));
                OperationServiceHelper.executeOperate("save", "aos_base_lowprz",
                        new DynamicObject[]{aos_base_lowprz}, OperateOption.create());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
		FndMsg.debug("======end======");
    }

    /**
     * 国别id与简称对应
     *
     * @return
     */
    public static Map<String, String> queryCountryIdMap() {
        // 将物料id取出来做转化 转成物料基础资料
        DynamicObjectCollection bd_country = QueryServiceHelper.query("bd_country", "id,number", null);
        Map<String, String> countryMap = new HashMap<>();
        for (DynamicObject obj : bd_country) {
            String number = obj.getString("number");
            String id = obj.getString("id");
            countryMap.put(number, id);
        }
        return countryMap;
    }

    /**
     * 物料id与货号对应
     *
     * @return
     */
    public static Map<String, String> queryMaterialIdMap() {
        // 将国别id取出来做转化 转化成国家地区基础资料
        DynamicObjectCollection bd_material = QueryServiceHelper.query("bd_material", "id, number", null);
        Map<String, String> skuMap = new HashMap<>();
        for (DynamicObject obj : bd_material) {
            String number = obj.getString("number");
            String id = obj.getString("id");
            skuMap.put(number, id);
        }
        return skuMap;
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        // 多线程执行
        executerun();
    }
}