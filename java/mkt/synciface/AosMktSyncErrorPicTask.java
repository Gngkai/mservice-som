package mkt.synciface;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import common.Cux_Common_Utl;
import common.fnd.FndDate;
import common.fnd.FndGlobal;
import common.fnd.FndMsg;
import common.sal.util.SalUtil;
import io.prestosql.jdbc.$internal.okhttp3.*;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import mkt.common.api.AosMktClApiUtil;
import mkt.openapi.ListingReq.utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * @author aosom
 * @since 2024/2/22 15:12
 * @version CL错误图片同步接口
 */
public class AosMktSyncErrorPicTask extends AbstractTask {
    public final static int TWO = 2;
    public final static int THREE = 3;
    public final static int FOUR = 4;

    public static void process() {
        try {
            Date today = FndDate.zero(new Date());
            Date yesterday = FndDate.add_days(FndDate.zero(new Date()), -1);
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
                String sku = jsObj.getString("SEGMENT1");
                DynamicObject aosItem = FndGlobal.getBase(sku, "bd_material");
                if (FndGlobal.IsNull(aosItem)) {
                    continue;// 货号不存在 跳过
                }
                Date firstInstockDate = dateFormat.parse(jsObj.getString("SCM_FIRST_IN_DATE"));
                DynamicObject aosMktWaitDesign = BusinessDataServiceHelper.newDynamicObject("aos_mkt_wait_design");
                // 国别
                aosMktWaitDesign.set("aos_orgid", FndGlobal.getBase(country, "bd_country"));
                // 语言
                aosMktWaitDesign.set("aos_language", jsObj.getString("LANGUAGE"));
                // SKU
                aosMktWaitDesign.set("aos_itemid", aosItem);
                // 报错
                aosMktWaitDesign.set("aos_error", jsObj.getString("IMAGE_VALID_ERROR"));
                // 产品状态
                aosMktWaitDesign.set("aos_item_status", jsObj.getString("ITEM_STATUS_CODE"));
                // 供应链数量
                aosMktWaitDesign.set("aos_scm_qty", Integer.valueOf(jsObj.getString("SCM_INVENTORY_NUM")));
                // 供应链状态
                aosMktWaitDesign.set("aos_scm_status", jsObj.getString("SCM_INVENTORY_STATUS"));
                // 首次入库日期
                aosMktWaitDesign.set("aos_instock_date", firstInstockDate);
                // 生成Listing优化需求表
                String billno = createListingReq(aosMktWaitDesign);
                // Listing单号
                aosMktWaitDesign.set("aos_billno", billno);
                aosMktWaitDesign.set("createtime", today);
                SaveServiceHelper.save(new DynamicObject[] {aosMktWaitDesign});
            }

            // 刷新设计需求状态
            DynamicObject[] waitS =
                BusinessDataServiceHelper.load("aos_mkt_wait_design", "aos_billno,aos_list_status", null);
            for (DynamicObject wait : waitS) {
                String aosBillno = wait.getString("aos_billno");
                DynamicObject aosMktDesignreq = QueryServiceHelper.queryOne("aos_mkt_designreq", "aos_status",
                    new QFilter("aos_orignbill", QCP.equals, aosBillno).toArray());
                if (FndGlobal.IsNotNull(aosMktDesignreq)) {
                    wait.set("aos_list_status", aosMktDesignreq.get("aos_status"));
                }
                SaveServiceHelper.save(new DynamicObject[] {wait});
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 生成Listing优化需求表
     *
     * @param aosMktWaitDesign 设计待处理
     * @return 单号
     */
    private static String createListingReq(DynamicObject aosMktWaitDesign) {
        String orgId = aosMktWaitDesign.getDynamicObject("aos_orgid").getString("id");
        String itemId = aosMktWaitDesign.getDynamicObject("aos_itemid").getString("id");
        String itemNum = aosMktWaitDesign.getDynamicObject("aos_itemid").getString("number");
        String aosError = aosMktWaitDesign.getString("aos_error");
        aosError = aosError.replace("Path does not exist ---https://cls3.s3.amazonaws.com/", "");
        aosError = aosError.replace(";", "、");
        Date lastMonth = FndDate.add_days(FndDate.zero(new Date()), -30);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        String lastMonthStr = writeFormat.format(lastMonth);
        DynamicObject lastListingReq = QueryServiceHelper.queryOne("aos_mkt_listing_req", "id",
            new QFilter("createtime", QCP.large_equals, lastMonthStr).and("aos_orgid", QCP.equals, orgId)
                .and("aos_entryentity.aos_itemid", QCP.equals, itemId)
                .and("aos_entryentity.aos_requirepic", QCP.equals, aosError).toArray());
        if (FndGlobal.IsNotNull(lastListingReq)) {
            return "";
        }
        // 申请人 根据sku取自【品类人员对应表】-【设计.姓名】
        String category = (String)SalUtil.getCategoryByItemId(itemId).get("name");
        String[] categoryGroup = category.split(",");
        String aosCategory1 = null;
        String aosCategory2 = null;
        int categoryLength = categoryGroup.length;
        if (categoryLength > 1) {
            aosCategory1 = categoryGroup[0];
            aosCategory2 = categoryGroup[1];
        }
        String aosUser = "";
        QFilter filterCategory1 = new QFilter("aos_category1", "=", aosCategory1);
        QFilter filterCategory2 = new QFilter("aos_category2", "=", aosCategory2);
        QFilter[] filters = new QFilter[] {filterCategory1, filterCategory2};
        DynamicObject aosMktProguser = QueryServiceHelper.queryOne("aos_mkt_proguser", "aos_designer", filters);
        if (aosMktProguser != null) {
            aosUser = aosMktProguser.getString("aos_designer");
        }
        List<DynamicObject> mapList = Cux_Common_Utl.GetUserOrg(aosUser);
        // 生成单据
        DynamicObject aosMktListingReq = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_req");
        aosMktListingReq.set("aos_orgid", orgId);
        aosMktListingReq.set("aos_type", "四者一致");
        aosMktListingReq.set("aos_requireby", aosUser);
        aosMktListingReq.set("aos_designer", aosUser);
        aosMktListingReq.set("aos_importance", "紧急");
        aosMktListingReq.set("aos_orgid", orgId);
        aosMktListingReq.set("aos_autoflag", true);
        aosMktListingReq.set("aos_requiredate", new Date());
        aosMktListingReq.set("aos_status", "申请人");

        if (mapList != null) {
            if (mapList.size() >= THREE && mapList.get(TWO) != null) {
                aosMktListingReq.set("aos_organization1", mapList.get(2).get("id"));
            }
            if (mapList.size() >= FOUR && mapList.get(THREE) != null) {
                aosMktListingReq.set("aos_organization2", mapList.get(3).get("id"));
            }
        }
        DynamicObjectCollection entityS = aosMktListingReq.getDynamicObjectCollection("aos_entryentity");
        DynamicObject entity = entityS.addNew();
        entity.set("aos_itemid", itemId);
        entity.set("aos_category1", aosCategory1);
        entity.set("aos_requirepic", "CL图片缺失:" + aosError);
        DynamicObject dyItem = utils.getItem(itemNum);
        entity.set("aos_segment3", dyItem.getString("aos_productno"));
        entity.set("aos_itemname", dyItem.getString("name"));
        entity.set("aos_orgtext", utils.getOrderCountry(dyItem));
        entity.set("aos_broitem", utils.getBroItem(dyItem.getString("number"), dyItem.getString("aos_productno")));
        SaveServiceHelper.saveOperate("aos_mkt_listing_req", new DynamicObject[] {aosMktListingReq},
            OperateOption.create());
        new mkt.progress.listing.AosMktListingReqBill().aosSubmit(aosMktListingReq, "B");
        return aosMktListingReq.getString("billno");
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
