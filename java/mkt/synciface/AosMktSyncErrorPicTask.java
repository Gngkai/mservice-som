package mkt.synciface;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import common.fnd.FndDate;
import common.fnd.FndGlobal;
import common.fnd.FndMsg;
import io.prestosql.jdbc.$internal.okhttp3.*;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.exception.KDException;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import mkt.common.api.AosMktClApiUtil;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * @author aosom
 * @since 2024/2/22 15:12
 * @version CL错误图片同步接口
 */
public class AosMktSyncErrorPicTask extends AbstractTask {
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

    public static void process() {
        try {
            Date yesterday = FndDate.add_days(FndDate.zero(new Date()), -1);
            //DeleteServiceHelper.delete("aos_mkt_cl_error", new QFilter("aos_date", QCP.equals, yesterday).toArray());
            DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            String dateStr = sdf.format(yesterday);
            OkHttpClient client = new OkHttpClient().newBuilder().build();
            MediaType mediaType = MediaType.parse("application/json");
            // 上线后参数调整
            RequestBody body = RequestBody.create(mediaType, "{\"SYNC_TIME\": \"" + dateStr + "\"}");
            String clToken = AosMktClApiUtil.getClToken();
            Request request = new Request.Builder().url("http://54.82.42.126:9400/api/SST/getErrorImageList")
                .method("POST", body).addHeader("User-Agent", "Apifox/1.0.0 (https://apifox.com)")
                .addHeader("Content-Type", "application/json").addHeader("Authorization", "Bearer " + clToken).build();
            Response response = client.newCall(request).execute();
            JSONArray jsonArr = JSON.parseArray(response.body().string());
            int length = jsonArr.size();
            for (int i = 0; i < length; i++) {
                JSONObject jsObj = jsonArr.getJSONObject(i);
                 FndMsg.debug(jsObj);
                String country = jsObj.getString("Country");
                // 货号
                String sku = jsObj.getString("SEGMENT1");
                Date firstInstockDate = dateFormat.parse(jsObj.getString("SCM_FIRST_IN_DATE"));
                DynamicObject aosMktWaitDesign = BusinessDataServiceHelper.newDynamicObject("aos_mkt_wait_design");
                // 国别
                aosMktWaitDesign.set("aos_orgid", FndGlobal.getBase(country, "bd_country"));
                // 语言

                aosMktWaitDesign.set("aos_itemid", FndGlobal.getBase(sku, "bd_material"));
                aosMktWaitDesign.set("aos_error", jsObj.getString("IMAGE_VALID_ERROR"));
                aosMktWaitDesign.set("aos_scm_qty", Integer.valueOf(jsObj.getString("SCM_INVENTORY_NUM")));
                aosMktWaitDesign.set("aos_instock_date", firstInstockDate);



                SaveServiceHelper.save(new DynamicObject[] {aosMktWaitDesign});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
