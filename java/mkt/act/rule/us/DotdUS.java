package mkt.act.rule.us;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.alibaba.fastjson.JSONObject;

import common.fnd.FndLog;
import common.sal.util.InStockAvailableDays;
import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import mkt.act.dao.ActShopPriceDao;
import mkt.act.dao.impl.ActShopPriceImpl;
import mkt.act.rule.ActStrategy;
import mkt.act.rule.ActUtil;

public class DotdUS implements ActStrategy {
    public static HashMap<String, Object> generateGroup() {
        HashMap<String, Object> group = new HashMap<>(16);
        DynamicObjectCollection bdMaterialgroupdetailS =
            QueryServiceHelper.query("bd_materialgroupdetail", "material.number material,group.name group", null);
        for (DynamicObject bdMaterialgroupdetail : bdMaterialgroupdetailS) {
            String aosItemnumber = bdMaterialgroupdetail.getString("material");
            String groupDetail = bdMaterialgroupdetail.getString("group");
            String[] groupList = groupDetail.split(",");
            if (groupList.length >= 2) {
                group.put(aosItemnumber, groupList[1]);
            }
        }
        return group;
    }

    @Override
    public void doOperation(DynamicObject object) throws Exception {
        // 国别
        String aosOrgnum = object.getDynamicObject("aos_nationality").getString("number");
        // 国别id
        String aosOrgid = object.getDynamicObject("aos_nationality").getString("id");
        // 活动开始日
        Date start = object.getDate("aos_startdate");
        // 活动结束日
        Date end = object.getDate("aos_enddate1");
        // 平台id
        String aosChannelid = object.getDynamicObject("aos_channel").getString("id");
        // 店铺id
        String aosShopid = object.getDynamicObject("aos_shop").getString("id");
        // 至活动日间隔天数
        int currentToAct = ActUtil.currentToActivityDateBetweenDays(start);
        // 折扣信息
        BigDecimal aosDisamt = object.getBigDecimal("aos_disamt");
        // 折扣力度
        BigDecimal aosDisstrength = object.getBigDecimal("aos_disstrength");
        // 非平台可用量
        Map<String, Integer> nonPlatItemSet = ActUtil.queryNonPlatQty(aosOrgid);
        // 满足review条件的物料
        Map<String, DynamicObject> reviewItemSet = ActUtil.queryReview(aosOrgid);
        // 活动选品清单
        DynamicObjectCollection aosMktActselectS = ActUtil.queryActSelectList(aosOrgnum);
        // 所有该国别下物料
        List<String> itemFilterList = generateItemList(aosMktActselectS);
        // 导入对象
        List<JSONObject> insertList = new ArrayList<>();
        // ①预计活动日前后30天，活动类型为xxx，且状态为正常的在线ID、
        Set<String> beforeAfter30Set = ActUtil.queryCullActTypeItem(aosOrgid, start, new String[] {"DOTD"}, 30);
        // ①预计活动日前后7天，活动类型为xxx，且状态为正常的在线ID、
        Set<String> beforeAfter7Set = ActUtil.queryCullActTypeItem(aosOrgid, start, new String[] {"LD", "7DD"}, 7);
        // 物料信息
        Map<String, DynamicObject> itemInfo = ActUtil.getItemInfo(itemFilterList, aosOrgid);
        // 每日价格
        Map<String, DynamicObject> priceMap =
            ActUtil.getAsinAndPriceFromPrice(aosOrgid, aosShopid, aosChannelid, itemFilterList);
        // 活动数量
        DynamicObject orgActivityQty = ActUtil.getOrgActivityQty(aosOrgid);
        // 最低价
        Map<String, DynamicObject> lowestPriceMap = ActUtil.queryLowestPrice(aosOrgid, itemFilterList);
        // 产品类别数据
        HashMap<String, Object> group = generateGroup();
        // 品类30天销售金额
        HashMap<String, BigDecimal> orderAmt = generateOrder(group, aosOrgid);
        // 店铺价格
        List<String> listNoPriceItem = new ArrayList<>(itemFilterList.size());
        String entityId = object.getString("id");
        ActShopPriceDao actShopPriceDao = new ActShopPriceImpl();
        Map<String, BigDecimal> mapShopPrice =
            actShopPriceDao.QueryOrgChannelShopItemNumberPrice(aosOrgid, aosChannelid, aosShopid, itemFilterList);
        FndLog log = FndLog.init("DotdUS", LocalDate.now().toString());
        for (DynamicObject aosMktActselect : aosMktActselectS) {
            // 物料编码
            String aosSku = aosMktActselect.getString("aos_sku");
            // 季节属性
            String aosSeasonattr = aosMktActselect.getString("aos_seasonattr");
            // 产品名称
            String aosItemname = aosMktActselect.getString("aos_itemname");
            // 产品大类
            String aosCategory1 = aosMktActselect.getString("aos_category1");
            // 产品中类
            String aosCategory2 = aosMktActselect.getString("aos_category2");
            // 平台仓库数量
            BigDecimal aosPlatfqty = aosMktActselect.getBigDecimal("aos_platfqty");
            // 平台仓日均销量
            BigDecimal aosPlatavgqty = aosMktActselect.getBigDecimal("aos_platavgqty");
            // 最大库龄
            BigDecimal aosItemmaxage = aosMktActselect.getBigDecimal("aos_itemmaxage");
            // 海外在库数量
            int aosOverseaqty = aosMktActselect.getInt("aos_overseaqty");
            // 活动选品类型细分
            String aosTypedetail = aosMktActselect.getString("aos_typedetail");
            // 活动数量
            int aosLowestqty = orgActivityQty.getInt("aos_lowestqty");
            // 活动数量占比
            BigDecimal aosInventoryratio = orgActivityQty.getBigDecimal("aos_inventoryratio");
            // 可提报活动数量
            int aosQty = Math.max(aosLowestqty, BigDecimal.valueOf(aosOverseaqty).multiply(aosInventoryratio)
                .setScale(0, RoundingMode.HALF_UP).intValue());
            // 物料信息
            DynamicObject itemObj = itemInfo.get(aosSku);
            if (itemObj == null) {
                log.add(aosSku + ":未获取到物料信息");
                continue;
            }
            // 物料id
            String aosItemid = itemObj.getString("aos_itemid");
            // 非平台可用量
            int nonPlatQty = InStockAvailableDays.getNonPlatQty(aosOrgid, aosItemid);
            // 线上7天日均销量 R
            float r = InStockAvailableDays.getOrgItemOnlineAvgQty(aosOrgid, aosItemid);
            // 预计活动日库存
            BigDecimal aosPreactqty = BigDecimal.valueOf(nonPlatQty)
                .subtract(BigDecimal.valueOf(r).multiply(BigDecimal.valueOf(currentToAct)));
            // 预计活动日可售天数
            int salDaysForAct = InStockAvailableDays.calInstockSalDaysForAct(aosOrgid, aosItemid, start);
            // 非平台仓可售天数
            int platSaleDays = InStockAvailableDays.calAvailableDaysByPlatQty(aosOrgid, aosItemid);
            Object category2 = group.get(aosSku);
            if (category2 == null) {
                log.add(aosSku + ":类别信息不存在");
                continue;
            }
            // 物料每日价格
            DynamicObject priceObj = priceMap.get(aosSku);
            String asin = "";
            BigDecimal aosCurrentprice;
            if (priceObj == null) {
                // 每日价格不存在
                if (mapShopPrice.containsKey(aosItemid)) {
                    // 店铺价格存在
                    aosCurrentprice = mapShopPrice.get(aosItemid);
                } else {
                    listNoPriceItem.add(aosSku);
                    log.add(aosSku + " 未获取到价格");
                    continue;
                }
            } else {
                // ASIN码
                asin = priceObj.getString("aos_asin");
                // 当前价格
                aosCurrentprice = priceObj.getBigDecimal("aos_currentprice");
            }
            // 活动价
            BigDecimal actPrice = aosCurrentprice.multiply(BigDecimal.ONE.subtract(aosDisstrength));
            // 最低价
            DynamicObject lowestPriceObj = lowestPriceMap.get(aosItemid);
            if (lowestPriceObj == null) {
                log.add(aosSku + " 未取到最低价");
                continue;
            }
            // 30天最低价
            BigDecimal minPrice30 = lowestPriceObj.getBigDecimal("aos_min_price30");
            if (minPrice30.multiply(BigDecimal.valueOf(0.95)).compareTo(actPrice) < 0) {
                actPrice = minPrice30.multiply(BigDecimal.valueOf(0.95));
            }
            // Review
            DynamicObject reviewObj = reviewItemSet.get(aosSku);
            if (reviewObj == null) {
                log.add(aosSku + " 未获取到Review");
                continue;
            }
            BigDecimal aosReview = reviewObj.getBigDecimal("aos_review");
            BigDecimal aosStars = reviewObj.getBigDecimal("aos_stars");
            // 自有仓库库存数量
            int ownWarehouseQuantity = nonPlatItemSet.getOrDefault(aosSku, 0);
            if (ownWarehouseQuantity == 0) {
                log.add(aosSku + " 自有仓库存库存为0");
                continue;
            }

            // 自有仓库存>30；或平台仓可售天数≥60天
            if (!(ownWarehouseQuantity > 30 || platSaleDays >= 60)) {
                log.add(aosSku + " 自有仓库存>30；或平台仓可售天数≥60天;");
                continue;
            }

            // 剔除①预计活动日前后30天，活动类型为DOTD，且状态为正常的在线ID
            if (beforeAfter30Set.contains(aosItemid)) {
                log.add(aosSku + " 剔除①预计活动日前后30天，活动类型为DOTD，且状态为正常的在线ID");
                continue;
            }

            // 剔除①预计活动日前后7天，活动类型为LD/7DD，且状态为正常的在线ID
            if (beforeAfter7Set.contains(aosItemid)) {
                log.add(aosSku + " 剔除①预计活动日前后7天，活动类型为LD/7DD，且状态为正常的在线ID");
                continue;
            }

            // 剔除活动日，非当季SKU
            if (ActUtil.isOutSeason(start, aosSeasonattr)) {
                log.add(aosSku + " 季节属性:" + aosSeasonattr + " 剔除活动日，非当季SKU");
                continue;
            }

            // 当前国别Review分数>=4或review分数==0
            if (!(aosStars.compareTo(BigDecimal.valueOf(0)) == 0 || aosStars.compareTo(BigDecimal.valueOf(4)) >= 0)) {
                log.add(aosSku + " Review分数:" + aosStars);
                continue;
            }

            // 同中类提报的产品过去30天销售额>17.5W
            BigDecimal orderamt = orderAmt.get(String.valueOf(category2));
            if (orderamt == null) {
                log.add(aosSku + " 同中类过去30天无销售");
                continue;
            } else if (orderamt.compareTo(BigDecimal.valueOf(175000)) <= 0) {
                log.add(aosSku + " 同中类提报的产品过去30天销售额<=17.5W");
                continue;
            }

            JSONObject itemObject = new JSONObject();
            itemObject.put("aos_itemnum", aosItemid);
            itemObject.put("aos_itemname", aosItemname);
            itemObject.put("aos_category_stat1", aosCategory1);
            itemObject.put("aos_category_stat2", aosCategory2);
            itemObject.put("aos_postid", asin);
            itemObject.put("aos_l_startdate", start);
            itemObject.put("aos_enddate", end);
            itemObject.put("aos_actqty", aosQty);
            itemObject.put("aos_l_actstatus", "A");
            itemObject.put("aos_price", aosCurrentprice);
            itemObject.put("aos_actprice", actPrice);
            // 当前库存
            itemObject.put("aos_currentqty", aosOverseaqty);
            // 预计活动日库存 非平台仓数量-R*(预计活动日-今天)
            itemObject.put("aos_preactqty", aosPreactqty);
            // 预计活动日可售天数
            itemObject.put("aos_preactavadays", salDaysForAct);
            // 平台仓当前库存数
            itemObject.put("aos_platqty", aosPlatfqty);
            // 最大库龄
            itemObject.put("aos_age", aosItemmaxage);
            // 过去7天国别日均销量
            itemObject.put("aos_7orgavgqty", r);
            // 过去7天平台日均销量
            itemObject.put("aos_7platavgqty", aosPlatavgqty);
            // 折扣力度
            itemObject.put("aos_discountdegree", aosDisstrength);
            // 折扣金额
            itemObject.put("aos_discountprice", aosDisamt);
            // review分数
            itemObject.put("aos_stars", aosStars);
            // review个数
            itemObject.put("aos_reviewqty", aosReview);
            // 类型细分
            itemObject.put("aos_typedetail", aosTypedetail);
            // 季节属性
            itemObject.put("aos_seasonattr", aosSeasonattr);
            insertList.add(itemObject);
        }

        if (insertList.isEmpty()) {
            return;
        }
        DynamicObjectCollection aosSalActplanentity = object.getDynamicObjectCollection("aos_sal_actplanentity");
        aosSalActplanentity.clear();
        // 生成活动选品清单
        for (JSONObject obj : insertList) {
            DynamicObject lineObj = aosSalActplanentity.addNew();
            for (String key : obj.keySet()) {
                lineObj.set(key, obj.get(key));
            }
        }
        SaveServiceHelper.save(new DynamicObject[] {object});
        DynamicObject dynamicObject = BusinessDataServiceHelper.loadSingle(object.getPkValue(), "aos_act_select_plan");
        ActUtil.setProfitRate(dynamicObject);
        // 计算活动毛利率
        Map<String, BigDecimal> orgLowestProfitRate = ActUtil.getOrgLowestProfitRate(aosOrgid);
        ActUtil.setLowestProfitRate(object.getPkValue(), orgLowestProfitRate);
        ActUtil.SaveItme(entityId, listNoPriceItem);
        // 保存日志表
        log.finnalSave();
    }

    /** 初始化物料List **/
    private List<String> generateItemList(DynamicObjectCollection aosMktActselectS) {
        List<String> itemFilterList = new ArrayList<>();
        for (DynamicObject obj : aosMktActselectS) {
            String aosSku = obj.getString("aos_sku");
            itemFilterList.add(aosSku);
        }
        return itemFilterList;
    }

    /** 品类30天金额 **/
    public HashMap<String, BigDecimal> generateOrder(HashMap<String, Object> group, String aosOrgid) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        today.add(Calendar.DAY_OF_MONTH, -30);
        QFilter filterDate = new QFilter("aos_order_date", QCP.large_equals, sdf.format(today.getTime()));
        QFilter filterOrg = new QFilter("aos_org.id", QCP.equals, aosOrgid);
        List<QFilter> orderFilter = SalUtil.getOrderFilter();
        orderFilter.add(filterDate);
        orderFilter.add(filterOrg);
        int size = orderFilter.size();
        QFilter[] filters = orderFilter.toArray(new QFilter[size]);
        DataSet orderDataSet = QueryServiceHelper.queryDataSet(this.getClass().getName() + "_GenerateOrderMonth",
            "aos_sync_om_order_r", "aos_org,aos_item_fid.number aos_itemnumber,aos_totalamt", filters, null);
        orderDataSet = orderDataSet.groupBy(new String[] {"aos_org", "aos_itemnumber"}).sum("aos_totalamt").finish();
        HashMap<String, BigDecimal> order = new HashMap<>(16);
        while (orderDataSet.hasNext()) {
            Row aosSyncOmonthSummary = orderDataSet.next();
            BigDecimal aosTotalamt = aosSyncOmonthSummary.getBigDecimal("aos_totalamt");
            String aosItemnumber = aosSyncOmonthSummary.getString("aos_itemnumber");
            Object category2 = group.get(aosItemnumber);
            if (category2 != null) {
                order.merge(String.valueOf(category2), aosTotalamt, (a, b) -> b.add(a));
            }
        }
        orderDataSet.close();
        return order;
    }

}