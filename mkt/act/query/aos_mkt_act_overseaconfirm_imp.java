package mkt.act.query;

import com.alibaba.fastjson.JSONObject;
import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.cache.CacheFactory;
import kd.bos.cache.DistributeSessionlessCache;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.db.DB;
import kd.bos.db.DBRoute;
import kd.bos.entity.plugin.ImportLogger;
import kd.bos.form.plugin.impt.BatchImportPlugin;
import kd.bos.form.plugin.impt.ImportBillData;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import kd.tmc.creditm.business.service.usecredit.BaseUseCreditService;
import mkt.act.rule.ActUtil;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class aos_mkt_act_overseaconfirm_imp extends BatchImportPlugin {
    private static final DistributeSessionlessCache cache = CacheFactory.getCommonCacheFactory()
            .getDistributeSessionlessCache("ComImp");

    private Map<String, DynamicObject> itemInfo = null;
    private Map<String, DynamicObject> costParam = null;
    private Map<String, Integer> orderQty = null;
    private Map<String, Integer> nonPlatQtyMap = null;
    private Map<String, Integer> platQtyMap = null;
    private Map<String, BigDecimal> priceMap = null;
    private Map<String, BigDecimal> costMap = null;
    Map<String, BigDecimal> ShipFee = null;

    private synchronized void before() {
        long currentUserId = UserServiceHelper.getCurrentUserId();
        String key = "aos_mkt_act_overseaconfirm_list:" + currentUserId;
        String ImpInitFlag = cache.get(key);
        if (itemInfo == null || ImpInitFlag == null) {
            itemInfo = queryItemInfo();
            orderQty = queryOrderQtyN();
            costParam = queryVatAndPlatform();
            priceMap = queryPrice();
            nonPlatQtyMap = queryNonPlatQty();
            platQtyMap = queryPlatQty();
            costMap = initItemCost();
            ShipFee = GenerateShipFee();
            cache.put(key, "Y", 900);
        }
    }

    private Map<String, Integer> queryOrderQtyN() {
        String algoKey = this.getClass().getName() + "_queryOrderQty";
        Calendar instance = Calendar.getInstance();
        instance.add(Calendar.DAY_OF_MONTH, -7);
        String format = DateFormatUtils.format(instance.getTime(), "yyyy-MM-dd");
        String selectFields = "aos_org.number aos_orgnum,aos_item_fid.number aos_itemnum, aos_order_qty";
        List<QFilter> orderFilter = SalUtil.getOrderFilter();
        DataSet dataSet = QueryServiceHelper.queryDataSet(algoKey,"aos_sync_om_order_r", selectFields, new QFilter[]{
                new QFilter("aos_order_date", QCP.large_equals, format),orderFilter.get(0)
        }, null);
        dataSet = dataSet.groupBy(new String[]{"aos_orgnum", "aos_itemnum"}).sum("aos_order_qty").finish();

        Map<String, Integer> result = new HashMap<>();
        while (dataSet.hasNext()) {
            Row next = dataSet.next();
            String aos_orgnum = next.getString("aos_orgnum");
            String aos_itemnum = next.getString("aos_itemnum");
            Integer aos_order_qty = next.getInteger("aos_order_qty");
            result.put(aos_orgnum+ aos_itemnum, aos_order_qty);
        }
        return result;
    }

    private Map<String, BigDecimal> initItemCost() {
        String sql = "select tasi.fk_aos_orgid aos_orgid,tasi.fk_aos_itemid aos_itemid,tasi.fk_aos_item_cost aos_item_cost" +
                " from tk_aos_sync_invcost tasi " +
                " where 1=1 " +
                " and not exists (select 1 from tk_aos_sync_invcost b" +
                " where 1=1 " +
                " and b.fk_aos_orgid = tasi.fk_aos_orgid" +
                " and b.fk_aos_itemid = tasi.fk_aos_itemid" +
                " and b.fk_aos_creation_date > tasi.fk_aos_creation_date) and tasi.fk_aos_item_cost is not null";
        DataSet ds = DB.queryDataSet("queryItemCost", DBRoute.of("eip"), sql, null);
        Map<String, BigDecimal> result = new HashMap<>();
        while (ds.hasNext()) {
            Row next = ds.next();
            String orgId = next.getString("aos_orgid");
            String itemId = next.getString("aos_itemid");
            BigDecimal aos_sync_invcost = next.getBigDecimal("aos_item_cost");
            if (aos_sync_invcost == null) {
                aos_sync_invcost = BigDecimal.ZERO;
            }
            result.put(orgId +itemId, aos_sync_invcost);
        }

        return result;
    }

    private Map<String, DynamicObject> queryItemInfo() {
        String selectFields = "material.aos_contryentry.aos_nationality.number aos_orgnum," +
                "material.aos_contryentry.aos_nationality aos_orgid," +
                "material aos_itemid," +
                "material.number aos_itemnum," +
                "material.name aos_itemname," +
                "group.name aos_category," +
                "material.aos_contryentry.aos_seasonseting.number aos_seasonattr," +
                "material.aos_contryentry.aos_contryentrystatus aos_contryentrystatus";
        DynamicObjectCollection list = QueryServiceHelper.query("bd_materialgroupdetail", selectFields, null);
        return list.stream().collect(Collectors.toMap(obj -> obj.getString("aos_orgnum")+obj.getString("aos_itemnum"), obj -> obj, (key1, key2) -> key1));
    }

    private Map<String, DynamicObject> queryVatAndPlatform() {
        DynamicObjectCollection list = QueryServiceHelper.query("aos_sal_org_cal_p",
                "aos_org.number aos_orgnum,aos_vat_amount,aos_am_platform", null);
        return list.stream().collect(Collectors.toMap(obj -> obj.getString("aos_orgnum"), obj -> obj, (k1, k2) -> k1));
    }

    private Map<String, BigDecimal> queryPrice() {
        String selectFields = "aos_orgid.number aos_orgnum,aos_item_code.number aos_itemnum,aos_shopfid.number aos_shopnum,aos_currentprice";
        DynamicObjectCollection list = QueryServiceHelper.query("aos_sync_invprice", selectFields, null);
        return list
                .stream()
                .collect(Collectors.toMap(obj -> obj.getString("aos_orgnum") + obj.getString("aos_itemnum") + obj.getString("aos_shopnum"),
                        obj -> obj.getBigDecimal("aos_currentprice"), (k1, k2) -> k1));
    }


    private Map<String, Integer> queryNonPlatQty() {
        DynamicObjectCollection list = QueryServiceHelper.query("aos_sync_invou_value", "aos_ou.number aos_orgnum, aos_item.number aos_itemnum, aos_instock_qty", null);
        return list.stream().collect(Collectors.toMap(obj -> obj.getString("aos_orgnum")+ obj.getString("aos_itemnum"), obj -> obj.getInt("aos_instock_qty"), (k1, k2) -> k1));
    }

    private Map<String, Integer> queryPlatQty() {
        String selectFields = "aos_ou.number aos_orgnum, aos_item.number aos_itemnum, aos_subinv.aos_belongshop.number aos_shopnum,aos_available_qty";
        DynamicObjectCollection list = QueryServiceHelper.query("aos_sync_invsub_value", selectFields, new QFilter[]{
                new QFilter("aos_subinv.aos_belongshop", QCP.not_equals, 0)
        });
        return list.stream().collect(Collectors.toMap(obj -> obj.getString("aos_orgnum")+obj.getString("aos_itemnum")+obj.getString("aos_shopnum"), obj -> obj.getInt("aos_available_qty"), (k1, k2) -> k1));
    }

    public static Map<String, BigDecimal> GenerateShipFee() {
        Map<String, BigDecimal> map = new HashMap<>();
        DynamicObjectCollection dyns = QueryServiceHelper.query("aos_quo_skuquery",
                "aos_orgid.number aos_orgid,aos_lowest_fee,aos_itemid.number aos_itemid", null);
        for (DynamicObject d : dyns) {
            BigDecimal aos_lowest_fee = d.getBigDecimal("aos_lowest_fee");
            String aos_item_code = d.getString("aos_itemid");
            String aos_ou_code = d.getString("aos_orgid");
            map.put(aos_ou_code + "~" + aos_item_code, aos_lowest_fee);
        }
        return map;
    }
    @Override
    protected void beforeSave(List<ImportBillData> billdatas, ImportLogger logger) {

        Calendar instance = Calendar.getInstance();
        instance.set(Calendar.HOUR_OF_DAY, 0);
        instance.set(Calendar.MINUTE, 0);
        instance.set(Calendar.SECOND, 0);
        instance.set(Calendar.MILLISECOND, 0);

        Date currentDate = instance.getTime();

        before();
        for (ImportBillData data : billdatas) {
            JSONObject object = data.getData();
            JSONObject aos_org = object.getJSONObject("aos_orgid");
            String aos_orgnum = aos_org.getString("number");

            JSONObject aos_item = object.getJSONObject("aos_itemid");
            String aos_itemnum = aos_item.getString("number");

            JSONObject aos_shop = object.getJSONObject("aos_shopid");
            String aos_shopnum = aos_shop.getString("number");
            Date aos_startdate = object.getDate("aos_startdate");
            long betweenDays = ActUtil.betweenDays(currentDate.getTime(), aos_startdate.getTime());

            DynamicObject itemInfoObject = itemInfo.get(aos_orgnum + aos_itemnum);
            if (itemInfoObject == null) {
                continue;
            }
            String aos_itemname = itemInfoObject.getString("aos_itemname");
            String aos_seasonattr = itemInfoObject.getString("aos_seasonattr");
            String aos_contryentrystatus = itemInfoObject.getString("aos_contryentrystatus");
            String aos_category = itemInfoObject.getString("aos_category");
            String aos_itemid = itemInfoObject.getString("aos_itemid");
            String aos_orgid = itemInfoObject.getString("aos_orgid");

            String aos_category1_name = "";
            String aos_category2_name = "";
            String[] split = aos_category.split(",");
            if (split.length > 2) {
                aos_category1_name = split[0];
                aos_category2_name = split[1];
            }
            object.put("aos_category1_name", aos_category1_name);
            object.put("aos_category2_name", aos_category2_name);
            object.put("aos_itemname", aos_itemname);

            JSONObject seasonattrObject = new JSONObject();
            seasonattrObject.put("number", aos_seasonattr);
            object.put("aos_seasonattrid", seasonattrObject);
            object.put("aos_itemstatus", aos_contryentrystatus);

            float avgQty = (float) (orderQty.getOrDefault(aos_orgnum+aos_itemnum, 0)/ 7.0);
            object.put("aos_7daysavgsales", BigDecimal.valueOf(avgQty).setScale(10, BigDecimal.ROUND_HALF_UP));

            DynamicObject paramObject = costParam.get(aos_orgnum);
            BigDecimal aos_vat = BigDecimal.ZERO;
            BigDecimal aos_am_platform = BigDecimal.ZERO;
            if (paramObject != null) {
                aos_vat = paramObject.getBigDecimal("aos_vat_amount");
                aos_am_platform = paramObject.getBigDecimal("aos_am_platform");
            }

            // 快递费
            //快递费结存表 参数(国别+SKU)
            BigDecimal expressFee = ShipFee.getOrDefault(aos_orgnum + "~" + aos_itemnum, BigDecimal.ZERO);
//            float expressFee = 0;
//            JSONObject aosomLowestFee = TMSService.getAosomLowestFee(aos_orgnum, aos_itemid);
//            if (StringUtils.equals(aosomLowestFee.getString("code"), "S")) {
//                expressFee = aosomLowestFee.getJSONObject("expressFee").getFloat("express");//快递费
//            }

            // 存货成本
            BigDecimal inventoryCost = costMap.getOrDefault(aos_orgid + aos_itemid, BigDecimal.ZERO);
            // 活动价格
            BigDecimal aos_activityprice = BigDecimal.valueOf(Float.parseFloat(object.getString("aos_activityprice")));
            // 当前价格
            BigDecimal aos_currentprice = priceMap.getOrDefault(aos_orgnum + aos_itemnum + aos_shopnum, BigDecimal.ZERO);

            BigDecimal aos_profitrate = SalUtil.calProfitrate(aos_currentprice, aos_vat, aos_am_platform, inventoryCost, expressFee);
            BigDecimal aos_activityprofitrate = SalUtil.calProfitrate(aos_activityprice, aos_vat, aos_am_platform, inventoryCost, expressFee);

            // 自有仓库存
            Integer nonPlatQty = nonPlatQtyMap.getOrDefault(aos_orgnum + aos_itemnum, 0);
            Integer platQty = platQtyMap.getOrDefault(aos_orgnum + aos_itemnum + aos_shopnum, 0);
            object.put("aos_expressfee", expressFee);
            object.put("aos_platformrate", aos_am_platform.setScale(2, BigDecimal.ROUND_HALF_UP));
            object.put("aos_vat", aos_vat.setScale(2, BigDecimal.ROUND_HALF_UP));
            object.put("aos_cost", inventoryCost.setScale(2, BigDecimal.ROUND_HALF_UP));
            object.put("aos_currentprice", aos_currentprice.setScale(2, BigDecimal.ROUND_HALF_UP));
            object.put("aos_currentprofitrate", aos_profitrate.setScale(3, BigDecimal.ROUND_HALF_UP));
            object.put("aos_activityprofitrate", aos_activityprofitrate.setScale(3, BigDecimal.ROUND_HALF_UP));

            int aos_currentinventory = nonPlatQty+platQty;
            object.put("aos_currentinventory", aos_currentinventory);
            object.put("aos_currentdays", avgQty == 0 ? 0 : BigDecimal.valueOf(aos_currentinventory).divide(BigDecimal.valueOf(avgQty), 0, BigDecimal.ROUND_HALF_UP));
            BigDecimal aos_activityinventory = BigDecimal.valueOf(aos_currentinventory).subtract(BigDecimal.valueOf(avgQty).multiply(BigDecimal.valueOf(betweenDays)));
            object.put("aos_activityinventory", aos_activityinventory.setScale(0, BigDecimal.ROUND_HALF_UP));
            object.put("aos_activitydays", avgQty == 0 ? 0 : aos_activityinventory.divide(BigDecimal.valueOf(avgQty), 0, BigDecimal.ROUND_HALF_UP));

        }
    }
}
