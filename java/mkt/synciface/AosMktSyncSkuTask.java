package mkt.synciface;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import common.fnd.FndGlobal;
import kd.bos.exception.ErrorCode;
import common.fnd.FndDate;
import common.fnd.FndWebHook;
import common.sal.impl.ComImpl2;
import common.sal.util.SalUtil;
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
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.threads.ThreadPools;

/**
 * @author aosom
 * @version SKU报告接口-调度任务类
 */
public class AosMktSyncSkuTask extends AbstractTask {
    public static void executerun() {
        QFilter qfTime;
        Calendar today = Calendar.getInstance();
        int hour = today.get(Calendar.HOUR_OF_DAY);
        // 海外公司
        QFilter overseaFlag = new QFilter("aos_is_oversea_ou.number", QCP.equals, "Y");
        QFilter overseaFlag2 = new QFilter("aos_isomvalid", "=", true);
        DynamicObject dynamicObject = QueryServiceHelper.queryOne("aos_mkt_base_orgvalue", "aos_value",
            new QFilter[] {new QFilter("aos_type", QCP.equals, "TIME")});
        int time = 16;
        if (dynamicObject != null) {
            time = dynamicObject.getBigDecimal("aos_value").intValue();
        }
        if (hour < time) {
            qfTime = new QFilter("aos_is_north_america.number", QCP.not_equals, "Y");
        } else {
            qfTime = new QFilter("aos_is_north_america.number", QCP.equals, "Y");
        }
        QFilter[] filtersOu = new QFilter[] {overseaFlag, overseaFlag2, qfTime};
        DynamicObjectCollection bdCountry = QueryServiceHelper.query("bd_country", "id,number", filtersOu);
        LocalDate end = LocalDate.now().minusDays(1);
        LocalDate start = end.minusDays(14);
        for (DynamicObject ou : bdCountry) {
            String pOuCode = ou.getString("number");
            Map<String, Object> params = new HashMap<>(16);
            params.put("ou_name", pOuCode);
            params.put("start_date", start.toString());
            params.put("end_date", end.toString());
            MktSkuRunnable mktSkuRunnable = new MktSkuRunnable(params);
            ThreadPools.executeOnce("MKT_SKU报告接口_" + pOuCode, mktSkuRunnable);
        }
    }

    public static void doOperate(Map<String, Object> param) {
        JSONObject obj = ComImpl2.GetCursorEsb(param, "CUXSKU_MMS", "CUX_MMS_BASIC");
        JSONArray pRetCursor = obj.getJSONArray("p_real_model");
        int length = pRetCursor.size();
        Object pOuCode = param.get("ou_name");
        Object pOrgId = FndGlobal.get_import_id(pOuCode, "bd_country");
        Date today = FndDate.zero(new Date());
        if (length > 0) {
            DynamicObject aosBaseSkupoprpt = BusinessDataServiceHelper.newDynamicObject("aos_base_skupoprpt");
            DynamicObjectCollection aosEntryentityS = aosBaseSkupoprpt.getDynamicObjectCollection("aos_entryentity");
            aosBaseSkupoprpt.set("billstatus", "A");
            aosBaseSkupoprpt.set("aos_orgid", pOrgId);
            aosBaseSkupoprpt.set("aos_date", today);
            int d = 0;
            for (int i = 0; i < length; i++) {
                d++;
                JSONObject skuPopRpt = pRetCursor.getJSONObject(i);
                // 日期
                String aosDateStr = skuPopRpt.get("aos_date").toString();
                Date aosDateL = AosMktSyncConnectUtil.parseDate(aosDateStr);
                // 系列名称
                Object aosCamName = skuPopRpt.get("aos_cam_name");
                if (!aosCamName.toString().toUpperCase().contains("TEST")) {
                    // 广告组名称
                    Object aosAdName = skuPopRpt.get("aos_ad_name");
                    // 店铺货号
                    Object aosShopsku = skuPopRpt.get("aos_ad_sku");
                    // 货号id
                    Object itemId = FndGlobal.get_import_id(aosAdName, "bd_material");
                    // 广告ASIN
                    Object aosAdAsin = skuPopRpt.get("aos_ad_asin");
                    // 曝光量
                    Object aosImpressions = skuPopRpt.get("aos_impressions");
                    // 点击量
                    Object aosClicks = skuPopRpt.get("aos_clicks");
                    // 花费金额
                    Object aosSpend = skuPopRpt.get("aos_spend");
                    // 总销售金额
                    Object aosTotalSales = skuPopRpt.get("aos_total_sales");
                    // 总订单量
                    Object aosTotalOrder = skuPopRpt.get("aos_total_order");
                    // 总销售数量
                    Object aosTotalUnits = skuPopRpt.get("aos_total_units");
                    // 广告货号销量
                    Object aosSameSkuUnits = skuPopRpt.get("aos_same_sku_units");
                    // 其他货号销量
                    Object aosOtherSkuUnits = skuPopRpt.get("aos_other_sku_units");
                    // 广告货号销售
                    Object aosSameSkuSales = skuPopRpt.get("aos_same_sku_sales");
                    // 其他货号销售
                    Object aosOtherSkuSale = skuPopRpt.get("aos_other_sku_sale");
                    // 更新日期
                    String aosUpDateStr = skuPopRpt.get("aos_up_date").toString();
                    Date aosUpDate = AosMktSyncConnectUtil.parseDate(aosUpDateStr);
                    DynamicObject aosEntryentity = aosEntryentityS.addNew();
                    aosEntryentity.set("aos_date_l", aosDateL);
                    aosEntryentity.set("aos_cam_name", aosCamName);
                    aosEntryentity.set("aos_ad_name", aosAdName);
                    aosEntryentity.set("aos_ad_sku", itemId);
                    aosEntryentity.set("aos_shopsku", aosShopsku);
                    aosEntryentity.set("aos_ad_asin", aosAdAsin);
                    aosEntryentity.set("aos_impressions", aosImpressions);
                    aosEntryentity.set("aos_clicks", aosClicks);
                    aosEntryentity.set("aos_spend", aosSpend);
                    aosEntryentity.set("aos_total_sales", aosTotalSales);
                    aosEntryentity.set("aos_total_order", aosTotalOrder);
                    aosEntryentity.set("aos_total_units", aosTotalUnits);
                    aosEntryentity.set("aos_same_sku_units", aosSameSkuUnits);
                    aosEntryentity.set("aos_other_sku_units", aosOtherSkuUnits);
                    aosEntryentity.set("aos_same_sku_sales", aosSameSkuSales);
                    aosEntryentity.set("aos_other_sku_sale", aosOtherSkuSale);
                    aosEntryentity.set("aos_up_date", aosUpDate);
                }
                if (d > 5000 || i == length - 1) {
                    OperationServiceHelper.executeOperate("save", "aos_base_skupoprpt",
                        new DynamicObject[] {aosBaseSkupoprpt}, OperateOption.create());
                    d = 0;
                }
            }

            // 查询最大日期
            DynamicObject maxQuery =
                QueryServiceHelper.queryOne("aos_base_skupoprpt", "max(aos_entryentity.aos_date_l) aos_date_l",
                    new QFilter("aos_date", QCP.equals, today).and("aos_orgid", QCP.equals, pOrgId).toArray());
            Date aosDateL = maxQuery.getDate("aos_date_l");
            if (FndDate.add_days(today, -1).compareTo(aosDateL) > 0) {
                FndWebHook.send(FndWebHook.urlMms, pOuCode + ":SP推广SKU报告数据未获取到最新值!");
            }
        }
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        executerun();
    }

    static class MktSkuRunnable implements Runnable {
        private Map<String, Object> params = new HashMap<>();

        public MktSkuRunnable(Map<String, Object> param) {
            this.params = param;
        }

        @Override
        public void run() {
            try {
                doOperate(params);
            } catch (Exception e) {
                String message = e.toString();
                String exceptionStr = SalUtil.getExceptionStr(e);
                String messageStr = message + "\r\n" + exceptionStr;
                FndWebHook.send(FndWebHook.urlMms, "获取SP推广SKU报告数据异常:" + messageStr);
                throw new KDException(new ErrorCode("获取SP推广SKU报告数据异常", exceptionStr));
            }
        }
    }
}