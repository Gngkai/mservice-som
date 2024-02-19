package mkt.progress.listing.hotpoint;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import common.fnd.FndMsg;
import kd.bos.context.RequestContext;
import kd.bos.exception.KDException;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.util.HttpClientUtils;
import mkt.common.api.AosMktClApiUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * @author aosom
 * @since 2024/2/19 11:31
 * @version 爆品质量打分文案同步-调度任务类
 */
public class AosMktListingHotDocTask extends AbstractTask {
    public static void process() {
        HashMap<String, String> header = new HashMap<>(16);
//        header.put("Content-Type", "application/x-www-form-urlencoded");
        header.put("Authorization", "Bearer " + AosMktClApiUtil.getClToken());
        HashMap<String, Object> body = new HashMap<>(16);
//        body.put("grant_type", "client_credentials");
        body.put("Country", "US");
        Map<String, Object> params = new HashMap<>(16);
        params.put("header", header);
        params.put("body", body);
        try {
            String url = "http://54.82.42.126:9400/getErrorImageList";
            FndMsg.debug("url:" + url);
            @SuppressWarnings("unchecked")
            String postjson = HttpClientUtils.post(url, (Map<String, String>)params.get("header"),
                (Map<String, Object>)params.get("body"), 1000 * 60, 1000 * 60);
            JSONArray jsonArr = JSON.parseArray(postjson);
            int length = jsonArr.size();
            for (int i = 0; i < length; i++) {
                JSONObject jsObj = jsonArr.getJSONObject(i);
                FndMsg.debug(jsObj);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 实现
     *
     * @param requestContext 请求上下文
     * @param map 参数
     * @throws KDException 异常
     */
    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        process();
    }
}
