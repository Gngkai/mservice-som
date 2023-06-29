package mkt.synciface;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import org.apache.commons.lang.time.DateUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import common.HttpUtils;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;


public class aos_mkt_syncif_connect {
	public final static String httpip = "https://esb.aosom.com:8282";
	public final static String username = "admin";
	public final static String password = "admin";
	public final static String Token = "/login/getMKTToken/v1.0.0";
	// ESB商店Token参数
	public final static String ESBGetStoreTokenUrl = "https://esb.aosom.com:8282/token?grant_type=client_credentials";
	public final static String pass = "Basic YktUZnZOWFZjSktFVlN0bUhNQ3F4NVVUOHY4YTo3MDNXdkxQMU9Eb1FuYzhoMXNibGZQbzVPNG9h";
	
	
	/**
	 * 
	 * @Title: get_connect
	 * @Description: 通用GET方法 接口同步获取数据只用此方法
	 * @Author: Jack
	 * @date: 2021年11月2日 上午8:16:01
	 * @param adress
	 * @return Return JSONObject
	 * @version
	 */
	public static JSONObject get_connect(String adress, Map<String, Object> param, String token) {
		JSONObject json = null;
		String lasturl = null;
		String ShopToken = get_Shop_Token();
		System.out.println("http =" + httpip + adress);
		if (adress.equals(Token))
			lasturl = httpip + adress + "?username=" + username + "&password=" + password;
		else {
			lasturl = httpip + adress + "?";
			for (String key : param.keySet()) {
				lasturl += (key + "=" + param.get(key) + "&");
			}
			lasturl = lasturl.substring(0, lasturl.length() - 1);
		}
		
		System.out.println("lasturl =" + lasturl);
		System.out.println("token =" + token);
		try {
			URL url = new URL(lasturl);
			HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
			urlConn.setRequestProperty("accept", "*/*");
			urlConn.setRequestProperty("connection", "Keep-Alive");
			urlConn.setRequestProperty("Accept-Charset", "utf-8");
			System.out.println("ShopToken =" + ShopToken);
			urlConn.setRequestProperty("Authorization", ShopToken);
			urlConn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
			if (!adress.equals(Token))
				urlConn.setRequestProperty("token", token);
			urlConn.setDoOutput(true);
			urlConn.setDoInput(true);
			urlConn.setRequestMethod("GET");// GET和POST必须全大写
			urlConn.connect();
			int code = urlConn.getResponseCode();// 获得响应码
			System.out.println("code =" + code);
			if (code == 200) {// 响应成功，获得响应的数据
				InputStream is = urlConn.getInputStream();// 得到数据流（输入流）
				JSONParser jsonParser = new JSONParser();
				json = (JSONObject) jsonParser.parse(new InputStreamReader(is, "UTF-8"));
			}
			urlConn.disconnect(); // 断开连接
		} catch (Exception e) {
		}
		return json;
	}

	
	public static String get_Shop_Token() {
		String ShopToken = null;
		try {
			ShopToken = HttpUtils.getAccessTokenFromIPAAS(ESBGetStoreTokenUrl, pass);
		} catch (KeyManagementException | NoSuchAlgorithmException | IOException e) {
		}
		return ShopToken;
	}

	/**
	 * 
	 * @Title: post_connect
	 * @Description: 通用POST方法
	 * @Author: Jack
	 * @date: 2021年10月25日 下午3:26:10
	 * @param adress
	 * @param param
	 * @return Return JSONObject
	 * @version
	 */
	public static JSONObject post_connect(String adress, Map<String, Object> param, String token) {
		JSONObject json = null;
		String lasturl = httpip + adress;
		String ShopToken = get_Shop_Token();
		try {
			URL url = new URL(lasturl);
			HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
			urlConn.setRequestProperty("Accept-Charset", "utf-8");
			urlConn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
			urlConn.setRequestMethod("POST");
			urlConn.setDoOutput(true);
			urlConn.setDoInput(true);
			urlConn.setUseCaches(false);
			urlConn.setRequestProperty("token", token);
			urlConn.setRequestProperty("Authorization", ShopToken);

			String body = "{";
			for (String key : param.keySet()) {
				body += "\"" + key + "\"" + ":" + "\"" + param.get(key) + "\"" + ",";
			}
			body = body.substring(0, body.length() - 1);
			body += "}";

			System.out.println("body =" + body);
			urlConn.connect();
			urlConn.getOutputStream().write(body.getBytes("UTF-8"));

			int code = urlConn.getResponseCode();// 获得响应码
			System.out.println("code =" + code);
			if (code == 200) {// 响应成功，获得响应的数据
			}
			urlConn.disconnect(); // 断开连接
		} catch (Exception e) {
			e.printStackTrace();
		}
		return json;
	}

	public static String get_Token() {
		com.alibaba.fastjson.JSONObject requestJson = new com.alibaba.fastjson.JSONObject();
		requestJson.put("username", username);
		requestJson.put("password", password);
		String lasturl = httpip + Token;
		String token = postToken(lasturl, requestJson);
		System.out.println(token);
		return token;
	}

	public static String postToken(String url, com.alibaba.fastjson.JSONObject requestJson) {
		StringBuffer result = new StringBuffer();
		HttpURLConnection conn = null;
		OutputStream out = null;
		BufferedReader reader = null;
		String ShopToken = get_Shop_Token();
		try {
			URL restUrl = new URL(url);
			conn = (HttpURLConnection) restUrl.openConnection();
			conn.setRequestMethod("POST");
			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setRequestProperty("accept", "*/*");
			conn.setRequestProperty("connection", "Keep-Alive");
			conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
			conn.setRequestProperty("Authorization", ShopToken);

			conn.connect();
			out = conn.getOutputStream();
			out.write(requestJson.toJSONString().getBytes());
			out.flush();
			int responseCode = conn.getResponseCode();
			if (responseCode != 200) {
				throw new RuntimeException("Error responseCode:" + responseCode);
			}
			/*String output = null;
			reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"));
			while ((output = reader.readLine()) != null) {
				result.append(output);
			}*/
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("调用接口出错：param+" + requestJson);
		} finally {
			try {
				if (reader != null) {
					reader.close();
				}
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
		com.alibaba.fastjson.JSONObject JSONObject_fast = new com.alibaba.fastjson.JSONObject();
		JSONObject_fast = com.alibaba.fastjson.JSONObject.parseObject(result.toString());
		String token = JSONObject_fast.getJSONObject("data").getString("token");
		return token;
	}

	public static Date parse_date(String aos_order_date_str) {
		Date date_Format = null;
		SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		try {
			if (aos_order_date_str.equals("")) {
				date_Format = null;
			} else {
				date_Format = DF.parse(aos_order_date_str);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return date_Format;
	}

	/**
	 * 
	 * @Title: get_url
	 * @Description: 获取接口地址信息
	 * @Author: Jack
	 * @date: 2021年11月8日 下午2:06:11
	 * @param bill
	 * @return Return String
	 * @version
	 */
	public static String get_url(String bill) {
		String url = null;
		QFilter filter = new QFilter("aos_code", "=", bill);
		QFilter[] filters = new QFilter[] { filter };
		String selectFields = "aos_url";
		DynamicObject aos_sync_common_bill = QueryServiceHelper.queryOne("aos_sync_common_bill", selectFields, filters);
		if (aos_sync_common_bill != null) {
			url = aos_sync_common_bill.getString("aos_url");
		}
		return url;
	}

	/**
	 * 
	 * @Title: get_time
	 * @Description: 获取上次接口同步日期
	 * @Author: Jack
	 * @date: 2021年11月11日 上午10:43:23
	 * @param bill
	 * @return Return String
	 * @version
	 */
	public static String get_time(String bill) {
		String time = "";
		QFilter filter = new QFilter("aos_code", "=", bill);
		QFilter[] filters = new QFilter[] { filter };
		String selectFields = "aos_entryentity.aos_time";
		DynamicObjectCollection aos_sync_common_bill = QueryServiceHelper.query("aos_sync_common_bill", selectFields,
				filters, "aos_entryentity.aos_time desc");
		for (DynamicObject d : aos_sync_common_bill) {
			if (d.get("aos_entryentity.aos_time") != null) {
				time = d.get("aos_entryentity.aos_time").toString();
				time = time.substring(0, time.length() - 2);
				time = time.replace(" ", "%20");
				break;
			}
		}
		return time;
	}

	/**
	 * 
	 * @Title: set_log
	 * @Description: 插入接口同步日志
	 * @Author: Jack
	 * @date: 2021年11月11日 上午10:43:14
	 * @param bill
	 * @param status
	 *            Return void
	 * @version
	 * @param p_sys_datetime
	 */
	public static void set_log(String bill, String status, String p_sys_datetime) {
		try {
			System.out.println("status =" + status);
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			QFilter filter = new QFilter("aos_code", "=", bill);
			DynamicObject para = BusinessDataServiceHelper.loadSingle("aos_sync_common_bill", "id", filter.toArray());
			Object fid = para.get("id");
			DynamicObject aos_sync_common_bill = BusinessDataServiceHelper.loadSingle(fid, "aos_sync_common_bill");
			DynamicObjectCollection bill_rows = aos_sync_common_bill.getDynamicObjectCollection("aos_entryentity");
			DynamicObject bill_row = bill_rows.addNew();
			Date aos_time = null;
			if (p_sys_datetime != null) {
				aos_time = sdf.parse(p_sys_datetime);
				bill_row.set("aos_time", aos_time);// 同步日期 真实值
				aos_time = DateUtils.addHours(aos_time, 14);
				bill_row.set("aos_localtime", aos_time);// 当地日期 用于显示
			}
			bill_row.set("aos_log", status);// 同步状态
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_sync_common_bill",
					new DynamicObject[] { aos_sync_common_bill }, OperateOption.create());
			if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
			}
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

}
