package mkt.progress.listing.manager;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListingManaKeyWordSync extends AbstractTask {
    public static void process() {
        DynamicObjectCollection bdCountryS =
                QueryServiceHelper.query("bd_country", "id,number",
                        new QFilter("aos_isomvalid", QCP.equals, true)
                                .toArray());
        String clToken = ListingManaClSync.getClToken();
        FndMsg.debug("clToken:" + clToken);
        HashMap<String, String> header = new HashMap<>();
        header.put("Content-Type", "application/x-www-form-urlencoded");
        header.put("Authorization", "Bearer " + clToken);
        HashMap<String, Object> body = new HashMap<>();
        body.put("grant_type", "client_credentials");
        FndMsg.debug("countrys:"+bdCountryS.size());

        for (DynamicObject bdCountry : bdCountryS) {
            Map<String, Object> params = new HashMap<>();
            params.put("header", header);
            params.put("body", body);
            String ouCode = bdCountry.getString("number");
            String orgId = bdCountry.getString("id");
            FndMsg.debug("ouCode:" + ouCode);
            params.put("ouCode", ouCode);
            params.put("orgId", orgId);
            KeyWordRunnable runnable = new KeyWordRunnable(params);
            ThreadPools.executeOnce("CL埋词个数接口" + ouCode, runnable);
        }
    }

    private static void do_operate(Map<String, Object> params) {
        try {
            String url = "http://54.82.42.126:9400/api/SST/getKeywordsReport?Country=" + params.get("ouCode");
            String postjson = HttpClientUtils.post(url, (Map<String, String>) params.get("header"),
                    (Map<String, Object>) params.get("body"), 1000 * 60, 1000 * 60);
            JSONArray jsonArr = JSON.parseArray(postjson);
            int length = jsonArr.size();
            FndMsg.debug("jsonSize:" + params.get("ouCode") + "~" + length);

            // 获取国别ID
            String orgId = String.valueOf(params.get("orgId"));

            // 删除表中该国别数据
            DeleteServiceHelper.delete("aos_mkt_clkeyword",
                    new QFilter("aos_orgid", QCP.equals, orgId).toArray());
            Map<String, String> itemMap = ListingManaClSync.getItemMap();

            List<DynamicObject> aosMktKeyWordS = new ArrayList<>();

            for (int i = 0; i < length; i++) {
                JSONObject jsObj = jsonArr.getJSONObject(i);
                String aosKeyWordUsd = jsObj.getString("keyworD_USED");
                DynamicObject aosMktKeyWord = BusinessDataServiceHelper.newDynamicObject("aos_mkt_clkeyword");
                aosMktKeyWord.set("aos_orgid", orgId);
                aosMktKeyWord.set("aos_itemid", itemMap.get(jsObj.getString("segmenT1")));
                aosMktKeyWord.set("aos_keyword", jsObj.getString("keyword"));
                aosMktKeyWord.set("aos_keyword_usd", aosKeyWordUsd);
                aosMktKeyWord.set("aos_keycount", aosKeyWordUsd.split(",").length);

                aosMktKeyWordS.add(aosMktKeyWord);
                if (aosMktKeyWordS.size() >= 5000 || i == length - 1) {
                    DynamicObject[] aosMktKeyWordArray = aosMktKeyWordS.toArray(new DynamicObject[0]);
                    SaveServiceHelper.save(aosMktKeyWordArray);
                    aosMktKeyWordS.clear();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
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

    static class KeyWordRunnable implements Runnable {
        private Map<String, Object> params = new HashMap<>();

        public KeyWordRunnable(Map<String, Object> param) {
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
                throw new KDException(new ErrorCode("获取埋词个数异常", exceptionStr));
            }
        }
    }

}
