package mkt.act.rule.dotd;

import common.sal.sys.basedata.dao.ItemCategoryDao;
import common.sal.sys.basedata.dao.impl.ItemCategoryDaoImpl;
import common.sal.util.SalUtil;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class GenerateData {

    public static void genSaleActRule(DynamicObject originObj) {

        long pkValue  = (long) originObj.getPkValue();
        // 国别
        String billno = originObj.getString("billno");
        // 国别
        String aos_orgid = originObj.getDynamicObject("aos_nationality").getString("id");
        // 渠道
        String aos_channelid = originObj.getDynamicObject("aos_channel").getString("id");
        // 店铺
        String aos_shopid = originObj.getDynamicObject("aos_shop").getString("id");
        // 活动类型
        String aos_acttypeid = originObj.getDynamicObject("aos_acttype").getString("id");
        // 开始日期
        Date aos_startdate = originObj.getDate("aos_startdate");
        // 结束日期
        Date aos_enddate1 = originObj.getDate("aos_enddate1");
        // 折扣金额
        BigDecimal aos_discountprice_h = originObj.getBigDecimal("aos_disamt");
        // 折扣力度
        BigDecimal aos_discountdegree_h = originObj.getBigDecimal("aos_disstrength");
        // 获取当前当前单据所有物料
        List<String> itemList = new ArrayList<>();
        DynamicObjectCollection aos_sal_actplanentity = originObj.getDynamicObjectCollection("aos_sal_actplanentity");
        aos_sal_actplanentity.forEach((x) -> {
            itemList.add(x.getDynamicObject("aos_itemnum").getString("id"));
        });
        // 获取当前国别+物料的店铺货号
        Map<String, String> shopskuMap = getShopSkuByOrgIdAndItemList(aos_orgid, itemList);
        // 获取物料季节属性
        Map<String, String> seasonAttrMap = getSeasonAttrByByOrgIdAndItemList(aos_orgid, itemList);
        // 获取物料分类
        ItemCategoryDao itemCategoryDao = new ItemCategoryDaoImpl();
        Map<String, String> categoryMap = itemCategoryDao.getItemCategoryByItemList(itemList);
        // 当前国别所有组别
        Map<String, String> groupMap = getItemGroupByOrg(aos_orgid);
        // 给物料分组
        Map<String, List<DynamicObject>> dataMap = new HashMap<>();
        aos_sal_actplanentity.forEach((obj) -> {
            // 物料
            String aos_itemid = obj.getDynamicObject("aos_itemnum").getString("id");
            // 获取类别
            String aos_categoryid = categoryMap.get(aos_itemid);
            // 获取组别
            String aos_groupid = groupMap.get(aos_categoryid);
            if (StringUtils.isEmpty(aos_groupid)) return;
            List<DynamicObject> groupDataList = dataMap.computeIfAbsent(aos_groupid, k -> new ArrayList<>());
            groupDataList.add(obj);
        });

        // 当前日期
        Calendar currentDate = Calendar.getInstance();
        // 生成单据
        dataMap.forEach((aos_groupid, objList) -> {
            if (objList.isEmpty()) return;
            // 设置单据头
            DynamicObject activityruleBill = BusinessDataServiceHelper.newDynamicObject("aos_mkt_activityrule");

            // 设置单据体
            DynamicObjectCollection aos_entryentity = activityruleBill.getDynamicObjectCollection("aos_entryentity");
            objList.forEach((obj) -> {
                // 新增一行
                DynamicObject lineObj = aos_entryentity.addNew();
                // 物料
                String aos_itemid = obj.getDynamicObject("aos_itemnum").getString("id");
                lineObj.set("aos_itemid", aos_itemid);

                // 大类
                String aos_category1 = obj.getString("aos_category_stat1");
                lineObj.set("aos_category1", aos_category1);
                // 中类
                String aos_category2 = obj.getString("aos_category_stat2");
                lineObj.set("aos_category2", aos_category2);
                // 季节属性
                String aos_seasonattr = seasonAttrMap.get(aos_itemid);
                lineObj.set("aos_seasonattr", aos_seasonattr);
                // 店铺货号
                lineObj.set("aos_shopsku", shopskuMap.get(aos_itemid));
                // 帖子id
                String aos_onlineid = obj.getString("aos_postid");
                lineObj.set("aos_onlineid", aos_onlineid);
                // 源单行id
                String entryid = obj.getString("id");
                lineObj.set("aos_orilineid", entryid);
                // 当前库存 aos_currentqty
                BigDecimal aos_currentqty = obj.getBigDecimal("aos_currentqty");
                lineObj.set("aos_currentqty", aos_currentqty);
                // 预计活动日库存 aos_actqty
                BigDecimal aos_preactqty = obj.getBigDecimal("aos_preactqty");
                lineObj.set("aos_preactqty", aos_preactqty);
                // 预计活动日可售天数 aos_actavadays
                BigDecimal aos_preactavadays = obj.getBigDecimal("aos_preactavadays");
                lineObj.set("aos_preactavadays", aos_preactavadays);
                // 平台仓当前库存数 aos_platqty
                BigDecimal aos_platqty = obj.getBigDecimal("aos_platqty");
                lineObj.set("aos_platqty", aos_platqty);
                // 非平台可用量 aos_nonplatqty
                BigDecimal aos_nonplatqty = obj.getBigDecimal("aos_nonplatqty");
                lineObj.set("aos_nonplatqty", aos_nonplatqty);
                // 当前库龄 aos_age
                BigDecimal aos_age = obj.getBigDecimal("aos_age");
                lineObj.set("aos_age", aos_age);
                // 过去7天国别日均销量 aos_7orgavgqty
                BigDecimal aos_7orgavgqty = obj.getBigDecimal("aos_7orgavgqty");
                lineObj.set("aos_7orgavgqty", aos_7orgavgqty);
                // 过去7天平台日均销量 aos_7platavgqty
                BigDecimal aos_7platavgqty = obj.getBigDecimal("aos_7platavgqty");
                lineObj.set("aos_7platavgqty", aos_7platavgqty);
                // AM当前定价 aos_currentprice
                BigDecimal aos_currentprice = obj.getBigDecimal("aos_price");
                lineObj.set("aos_currentprice", aos_currentprice);
                // 活动价 aos_actprice
                BigDecimal aos_actprice = obj.getBigDecimal("aos_actprice");
                lineObj.set("aos_actprice", aos_actprice);
                // 活动个数 aos_actnum
                BigDecimal aos_actnum = obj.getBigDecimal("aos_actqty");
                lineObj.set("aos_actnum", aos_actnum);
                // 活动折扣力度 aos_discountdegree
                BigDecimal aos_discountdegree = obj.getBigDecimal("aos_discountdegree");
                lineObj.set("aos_discountdegree", aos_discountdegree);
                // 折扣金额 aos_discountprice
                BigDecimal aos_discountprice = obj.getBigDecimal("aos_discountprice");
                lineObj.set("aos_discountprice", aos_discountprice);
                // 活动毛利率 aos_actprofitrate
                BigDecimal aos_actprofitrate = obj.getBigDecimal("aos_profit");
                lineObj.set("aos_actprofitrate", aos_actprofitrate);
                //是否参加 默认是
                lineObj.set("aos_cancel","Y");
                
                

                lineObj.set("aos_plandate", obj.get("aos_plandate"));
                lineObj.set("aos_platformreq", obj.get("aos_platformreq"));
                lineObj.set("aos_other", obj.get("aos_other"));
                lineObj.set("aos_is_saleout", obj.get("aos_is_saleout"));
                lineObj.set("aos_offsale1", obj.get("aos_offsale1"));
                
            });

            if (aos_entryentity != null && aos_entryentity.size() > 0) {
                activityruleBill.set("billno", SalUtil.getBillNo(activityruleBill));
                activityruleBill.set("aos_orgid", aos_orgid);
                activityruleBill.set("aos_groupid", aos_groupid);
                activityruleBill.set("aos_channelid", aos_channelid);
                activityruleBill.set("aos_shopid", aos_shopid);
                activityruleBill.set("aos_acttypeid", aos_acttypeid);
                activityruleBill.set("aos_startdate", aos_startdate);
                activityruleBill.set("aos_enddate", aos_enddate1);
                activityruleBill.set("aos_makeby", SalUtil.SYSTEM_MEMBER);
                activityruleBill.set("aos_makedate", currentDate);
                activityruleBill.set("billstatus", "A");
                activityruleBill.set("aos_discountprice_h", aos_discountprice_h);
                activityruleBill.set("aos_discountdegree_h", aos_discountdegree_h);
                activityruleBill.set("aos_source", pkValue);
                activityruleBill.set("aos_sourcebillno", billno);
                SaveServiceHelper.save(new DynamicObject[]{activityruleBill});
            }
        });

        originObj.set("aos_gensalconfirm", true);
        SaveServiceHelper.save(new DynamicObject[]{originObj});
    }
    // 获取当前国别+物料的店铺货号
    private static Map<String, String> getShopSkuByOrgIdAndItemList(String aos_orgid, List<String> itemList) {
        DynamicObjectCollection shopskuList = QueryServiceHelper.query("aos_sync_inv_ctl", "aos_item aos_itemid, aos_productid aos_shopsku", new QFilter[]{
                new QFilter("aos_ou", QCP.equals, aos_orgid),
                new QFilter("aos_item", QCP.in, itemList)
        });
        return shopskuList.stream().collect(Collectors.toMap(obj -> obj.getString("aos_itemid"), obj -> obj.getString("aos_shopsku"), (key1, key2) -> key1));
    }

    // 获取物料季节属性
    private static Map<String, String> getSeasonAttrByByOrgIdAndItemList(String aos_orgid, List<String> itemList) {
        DynamicObjectCollection seasonAttrList = QueryServiceHelper.query("bd_material", "id aos_itemid, aos_contryentry.aos_seasonseting aos_seasonattr", new QFilter[]{
                new QFilter("aos_contryentry.aos_nationality", QCP.equals, aos_orgid),
                new QFilter("id", QCP.in, itemList)
        });
        return seasonAttrList.stream().collect(Collectors.toMap(obj -> obj.getString("aos_itemid"), obj -> obj.getString("aos_seasonattr"), (key1, key2) -> key1));
    }

    // 当前国别所有组别
    private static Map<String, String> getItemGroupByOrg(String aos_orgid) {
        DynamicObjectCollection groupCategoryList = QueryServiceHelper.query("aos_sal_group", "aos_sal_org aos_groupid,aos_category aos_categoryid", new QFilter[]{
                new QFilter("aos_org", QCP.equals, aos_orgid)
        });
        return groupCategoryList
                .stream()
                .collect(Collectors.toMap(obj -> obj.getString("aos_categoryid"), obj -> obj.getString("aos_groupid"), (key1, key2) -> key1));
    }
}
