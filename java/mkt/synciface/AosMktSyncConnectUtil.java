package mkt.synciface;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import common.HttpUtils;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;

/**
 * @author aosom
 * @version 营销接口连接-工具类
 */
public class AosMktSyncConnectUtil {
    public final static String HTTPIP = "https://esb.aosom.com:8282";
    public final static String USERNAME = "admin";
    public final static String PASSWORD = "admin";
    public final static String TOKEN = "/login/getMKTToken/v1.0.0";
    public final static String ESBGETSTORETOKENURL = "https://esb.aosom.com:8282/token?grant_type=client_credentials";
    public final static String PASS =
        "Basic YktUZnZOWFZjSktFVlN0bUhNQ3F4NVVUOHY4YTo3MDNXdkxQMU9Eb1FuYzhoMXNibGZQbzVPNG9h";
    public final static int SUCCEEDCODE = 200;

    public static String getShopToken() {
        String shopToken = null;
        try {
            shopToken = HttpUtils.getAccessTokenFromIPAAS(ESBGETSTORETOKENURL, PASS);
        } catch (KeyManagementException | NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
        }
        return shopToken;
    }

    public static String getToken() {
        com.alibaba.fastjson.JSONObject requestJson = new com.alibaba.fastjson.JSONObject();
        requestJson.put("username", USERNAME);
        requestJson.put("password", PASSWORD);
        String lasturl = HTTPIP + TOKEN;
        return postToken(lasturl, requestJson);
    }

    public static String postToken(String url, com.alibaba.fastjson.JSONObject requestJson) {
        String result = "";
        HttpURLConnection conn = null;
        OutputStream out = null;
        String shopToken = getShopToken();
        try {
            URL restUrl = new URL(url);
            conn = (HttpURLConnection)restUrl.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setRequestProperty("accept", "*/*");
            conn.setRequestProperty("connection", "Keep-Alive");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Authorization", shopToken);
            conn.connect();
            out = conn.getOutputStream();
            out.write(requestJson.toJSONString().getBytes());
            out.flush();
            int responseCode = conn.getResponseCode();
            if (responseCode != SUCCEEDCODE) {
                throw new RuntimeException("Error responseCode:" + responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("调用接口出错：param+" + requestJson);
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
                if (conn != null) {
                    conn.disconnect();
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
        com.alibaba.fastjson.JSONObject json = com.alibaba.fastjson.JSONObject.parseObject(result);
        return json.getJSONObject("data").getString("token");
    }

    public static Date parseDate(String aosOrderDateStr) {
        Date dateFormat = null;
        SimpleDateFormat dF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try {
            if (!"".equals(aosOrderDateStr)) {
                dateFormat = dF.parse(aosOrderDateStr);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return dateFormat;
    }

    public static String getTime(String bill) {
        String time = "";
        QFilter filter = new QFilter("aos_code", "=", bill);
        QFilter[] filters = new QFilter[] {filter};
        String selectFields = "aos_entryentity.aos_time";
        DynamicObjectCollection aosSyncCommonBill =
            QueryServiceHelper.query("aos_sync_common_bill", selectFields, filters, "aos_entryentity.aos_time desc");
        for (DynamicObject d : aosSyncCommonBill) {
            if (d.get("aos_entryentity.aos_time") != null) {
                time = d.get("aos_entryentity.aos_time").toString();
                time = time.substring(0, time.length() - 2);
                time = time.replace(" ", "%20");
                break;
            }
        }
        return time;
    }
}
