package mkt.common.api;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * @author aosom
 * @since 2024/2/19 13:09
 */
public class AosMktClApiUtil {
    public static String getClToken() {
        try {
            // 创建URL对象
            URL url = new URL("http://54.82.42.126:9400/api/Login?role=CL&SecretKey=AosomCL@admin1");
            // 创建HTTP连接
            HttpURLConnection conn = (HttpURLConnection)url.openConnection();
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
}
