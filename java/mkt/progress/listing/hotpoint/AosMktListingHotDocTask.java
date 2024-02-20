package mkt.progress.listing.hotpoint;

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
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import mkt.common.api.AosMktClApiUtil;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * @author aosom
 * @since 2024/2/19 11:31
 * @version 爆品质量打分文案同步-调度任务类
 */
public class AosMktListingHotDocTask extends AbstractTask {
    public static void process() {
        try {
            Date yesterday = FndDate.add_days(FndDate.zero(new Date()), -1);
            DeleteServiceHelper.delete("aos_mkt_cl_error", new QFilter("aos_date", QCP.equals, yesterday).toArray());
            DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            String dateStr = sdf.format(yesterday);
            OkHttpClient client = new OkHttpClient().newBuilder().build();
            MediaType mediaType = MediaType.parse("application/json");
            // 上线后参数调整
            RequestBody body = RequestBody.create(mediaType, "{\"SYNC_TIME\": \"" + dateStr + "\"}");
            String clToken = AosMktClApiUtil.getClToken();
            Request request = new Request.Builder().url("http://54.82.42.126:9400/api/SST/GetErrorImageList")
                .method("POST", body).addHeader("User-Agent", "Apifox/1.0.0 (https://apifox.com)")
                .addHeader("Content-Type", "application/json").addHeader("Authorization", "Bearer " + clToken).build();
            Response response = client.newCall(request).execute();
            JSONArray jsonArr = JSON.parseArray(response.body().string());
            int length = jsonArr.size();
            for (int i = 0; i < length; i++) {
                JSONObject jsObj = jsonArr.getJSONObject(i);
                // 国别
                String country = jsObj.getString("Country");
                // 货号
                String sku = jsObj.getString("SEGMENT1");
                FndMsg.debug(i + " sku:" + sku);
                // 员工号
                String userNum = jsObj.getString("F_Account");
                String imageValidFlag = jsObj.getString("IMAGE_VALID_FLAG");
                String imageValidError = jsObj.getString("IMAGE_VALID_ERROR");
                String amazonTitle = jsObj.getString("Amazon_title");
                Date clCreationDate = dateFormat.parse(jsObj.getString("CREATION_DATE"));
                Date clUpdateDate = dateFormat.parse(jsObj.getString("LAST_UPDATE_DATE"));
                DynamicObject aosMktClError = BusinessDataServiceHelper.newDynamicObject("aos_mkt_cl_error");
                aosMktClError.set("aos_orgid", FndGlobal.getBase(country, "bd_country"));
                aosMktClError.set("aos_itemid", FndGlobal.getBase(sku, "bd_material"));
                aosMktClError.set("aos_user", FndGlobal.getBase(userNum, "bos_user"));
                aosMktClError.set("aos_imageflag", imageValidFlag);
                aosMktClError.set("aos_imagerror", imageValidError);
                aosMktClError.set("aos_amatitel", amazonTitle);
                aosMktClError.set("aos_clcreatedate", clCreationDate);
                aosMktClError.set("aos_clupdatedate", clUpdateDate);
                aosMktClError.set("aos_date", yesterday);
                SaveServiceHelper.save(new DynamicObject[] {aosMktClError});
                // 如果是爆品 需要生成爆品质量打分-文案
                AosMktListingHotUtil.createHotFromCl(aosMktClError);
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
