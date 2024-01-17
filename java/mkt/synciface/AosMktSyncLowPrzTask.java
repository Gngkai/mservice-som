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

/**
 * @author aosom
 * @version 活动最低价接口-调度任务类
 */
public class AosMktSyncLowPrzTask extends AbstractTask {
    public static void executerun() {
        DeleteServiceHelper.delete("aos_base_lowprz", null);
        doOperate();
    }

    public static void doOperate() {
        try {
            // 创建 URL 对象
            URL url = new URL("https://api044.aosom.com:3443/daservice/da/listing/minprice");
            // 创建 HttpURLConnection 对象，并打开连接
            HttpURLConnection connection = (HttpURLConnection)url.openConnection();
            connection.setRequestMethod("GET");
            // 发送 GET 请求
            // 读取响应内容
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            StringBuilder response = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            connection.disconnect();
            String postjson = response.toString();
            JSONObject obj = JSON.parseObject(postjson);
            JSONArray pRetCursor = obj.getJSONObject("data").getJSONArray("p_real_model");
            Map<String, String> countryIdMap = queryCountryIdMap();
            Map<String, String> materialIdMap = queryMaterialIdMap();
            int length = pRetCursor.size();
            FndMsg.debug("length:" + length);
            for (Object o : pRetCursor) {
                JSONObject lowPrzRpt = (JSONObject)o;
                DynamicObject aosBaseLowPrz = BusinessDataServiceHelper.newDynamicObject("aos_base_lowprz");
                String aosItemnum = (String)lowPrzRpt.get("MHSKU");
                String ouName = (String)lowPrzRpt.get("OU_NAME");
                aosBaseLowPrz.set("billstatus", "A");
                aosBaseLowPrz.set("aos_orgid", countryIdMap.getOrDefault(ouName, "0"));
                aosBaseLowPrz.set("aos_itemid", materialIdMap.getOrDefault(aosItemnum, "0"));
                aosBaseLowPrz.set("aos_min_price", lowPrzRpt.get("MIN_PRICE"));
                aosBaseLowPrz.set("aos_min_price30", lowPrzRpt.get("MIN_PRICE30"));
                aosBaseLowPrz.set("aos_min_price365", lowPrzRpt.get("MIN_PRICE365"));
                OperationServiceHelper.executeOperate("save", "aos_base_lowprz", new DynamicObject[] {aosBaseLowPrz},
                    OperateOption.create());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 国别id与简称对应
     *
     */
    public static Map<String, String> queryCountryIdMap() {
        // 将物料id取出来做转化 转成物料基础资料
        DynamicObjectCollection bdCountry = QueryServiceHelper.query("bd_country", "id,number", null);
        Map<String, String> countryMap = new HashMap<>(16);
        for (DynamicObject obj : bdCountry) {
            String number = obj.getString("number");
            String id = obj.getString("id");
            countryMap.put(number, id);
        }
        return countryMap;
    }

    /**
     * 物料id与货号对应
     */
    public static Map<String, String> queryMaterialIdMap() {
        // 将国别id取出来做转化 转化成国家地区基础资料
        DynamicObjectCollection bdMaterial = QueryServiceHelper.query("bd_material", "id, number", null);
        Map<String, String> skuMap = new HashMap<>(16);
        for (DynamicObject obj : bdMaterial) {
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