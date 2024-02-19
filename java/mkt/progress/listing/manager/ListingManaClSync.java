package mkt.progress.listing.manager;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import common.fnd.FndGlobal;
import common.fnd.FndMsg;
import common.sal.util.SalUtil;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.ErrorCode;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.threads.ThreadPools;
import kd.bos.util.HttpClientUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListingManaClSync extends AbstractTask {
    public static void process() {
        DynamicObjectCollection bdCountryS =
                QueryServiceHelper.query("bd_country", "id,number",
                        new QFilter("aos_isomvalid", QCP.equals, true)
                                .toArray());
        String clToken = getClToken();
        FndMsg.debug("clToken:" + clToken);
        HashMap<String, String> header = new HashMap<>();
        header.put("Content-Type", "application/x-www-form-urlencoded");
        header.put("Authorization", "Bearer " + clToken);
        HashMap<String, Object> body = new HashMap<>();
        body.put("grant_type", "client_credentials");
        FndMsg.debug("countrys:" + bdCountryS.size());

        for (DynamicObject bdCountry : bdCountryS) {
            Map<String, Object> params = new HashMap<>();
            params.put("header", header);
            params.put("body", body);
            String ouCode = bdCountry.getString("number");
            FndMsg.debug("ouCode:" + ouCode);
            String orgId = bdCountry.getString("id");
            params.put("ouCode", ouCode);
            params.put("orgId", orgId);
            ManaClRunnable manaClRunnable = new ManaClRunnable(params);
            ThreadPools.executeOnce("CL链接同步接口" + ouCode, manaClRunnable);
        }
    }

    private static void do_operate(Map<String, Object> params) {
        try {
            String url = "http://54.82.42.126:9400/api/SST/getListingViewReport?Country=" + params.get("ouCode");
            String postjson = HttpClientUtils.post(url, (Map<String, String>) params.get("header"), (Map<String, Object>) params.get("body"), 1000 * 60, 1000 * 60);
            JSONArray jsonArr = JSON.parseArray(postjson);
            int length = jsonArr.size();
            FndMsg.debug("jsonSize:" + params.get("ouCode") + "~" + length);

            // 获取国别ID
            String orgId = String.valueOf(params.get("orgId"));

            // 删除表中该国别数据
            DeleteServiceHelper.delete("aos_mkt_clurl",
                    new QFilter("aos_orgid", QCP.equals, orgId).toArray());

            Map<String, String> platMap = getPlatMap();
            Map<String, String> shopMap = getShopMap(orgId);
            Map<String, String> itemMap = getItemMap();

            List<DynamicObject> aosMktClUrlS = new ArrayList<>();
            for (int i = 0; i < length; i++) {
                JSONObject jsObj = jsonArr.getJSONObject(i);
                FndMsg.debug(jsObj);
                DynamicObject aosMktClUrl = BusinessDataServiceHelper.newDynamicObject("aos_mkt_clurl");
                aosMktClUrl.set("aos_orgid", orgId);
                aosMktClUrl.set("aos_platformid", platMap.get(jsObj.getString("PLATFORM")));
                aosMktClUrl.set("aos_shopid", shopMap.get(jsObj.getString("SHOP_NAME")));
                aosMktClUrl.set("aos_itemid", itemMap.get(jsObj.getString("SEGMENT1")));
                aosMktClUrl.set("aos_shopsku", jsObj.getString("SHELF_SKU"));
                aosMktClUrl.set("aos_url", jsObj.getString("LISTING_URL"));
                aosMktClUrlS.add(aosMktClUrl);
                if (aosMktClUrlS.size() >= 5000 || i == length - 1) {
                    DynamicObject[] aosMktClUrlArray = aosMktClUrlS.toArray(new DynamicObject[0]);
                    SaveServiceHelper.save(aosMktClUrlArray);
                    aosMktClUrlS.clear();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Map<String, String> getItemMap() {
        Map<String, String> itemMap = new HashMap<>();
        DynamicObjectCollection dyns = QueryServiceHelper.query("bd_material",
                "id,number", new QFilter("aos_protype", QCP.equals, "N").toArray());
        for (DynamicObject d : dyns) {
            itemMap.put(d.getString("number"), d.getString("id"));
        }
        return itemMap;
    }

    /**
     * 获取国别下店铺名称对应ID
     *
     * @param orgId
     * @return
     */
    private static Map<String, String> getShopMap(String orgId) {
        Map<String, String> shopMap = new HashMap<>();
        DynamicObjectCollection dyns = QueryServiceHelper.query("aos_sal_shop",
                "id,name", new QFilter("aos_org.id", QCP.equals, orgId).toArray());
        for (DynamicObject d : dyns) {
            shopMap.put(d.getString("name"), d.getString("id"));
        }
        return shopMap;
    }


    public static Map<String, String> getPlatMap() {
        Map<String, String> platMap = new HashMap<>();
        DynamicObjectCollection dyns = QueryServiceHelper.query("aos_sal_channel",
                "id,number", null);
        for (DynamicObject d : dyns) {
            platMap.put(d.getString("number"), d.getString("id"));
        }
        return platMap;
    }

    public static String getClToken() {
        try {
            // 创建URL对象
            URL url = new URL("http://54.82.42.126:9400/api/Login?role=CL&SecretKey=AosomCL@admin1");
            // 创建HTTP连接
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            // 发送请求并获取响应代码
            int responseCode = conn.getResponseCode();
            // 读取响应内容
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            JSONObject json = JSON.parseObject(response.toString());
            return json.getString("token");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 实现
     *
     * @param requestContext 请求上下文
     * @param map            参数
     * @throws KDException 异常
     */
    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        process();
    }

    static class ManaClRunnable implements Runnable {
        private Map<String, Object> params = new HashMap<>();

        public ManaClRunnable(Map<String, Object> param) {
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
                throw new KDException(new ErrorCode("CL链接同步接口异常", exceptionStr));
            }
        }
    }

}
