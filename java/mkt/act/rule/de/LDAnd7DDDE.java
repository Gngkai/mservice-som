package mkt.act.rule.de;

import com.alibaba.fastjson.JSONObject;
import common.sal.util.InStockAvailableDays;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import mkt.act.dao.ActShopPriceDao;
import mkt.act.dao.impl.ActShopPriceImpl;
import mkt.act.rule.ActStrategy;
import mkt.act.rule.ActUtil;
import mkt.common.MktComUtil;

import java.math.BigDecimal;
import java.util.*;

public class LDAnd7DDDE implements ActStrategy {
    @Override
    public void doOperation(DynamicObject object) throws Exception {
        String aos_orgnum = object.getDynamicObject("aos_nationality").getString("number");
        Date start = object.getDate("aos_startdate");
        Date end = object.getDate("aos_enddate1");
        // 国别id
        String aos_orgid = object.getDynamicObject("aos_nationality").getString("id");
        String aos_channelid = object.getDynamicObject("aos_channel").getString("id");
        String aos_shopid = object.getDynamicObject("aos_shop").getString("id");
        String aos_shopnum = object.getDynamicObject("aos_shop").getString("number");

        // 日志
        DynamicObject aos_sync_log = ActUtil.getCommonLog("DotdDE", aos_orgnum);
        DynamicObjectCollection aos_sync_logS = aos_sync_log.getDynamicObjectCollection("aos_entryentity");

        // 至活动日间隔天数
        int currentToAct = ActUtil.currentToActivityDateBetweenDays(start);

        // 折扣信息
        BigDecimal aos_disamt = object.getBigDecimal("aos_disamt");
        BigDecimal aos_disstrength = object.getBigDecimal("aos_disstrength");

        // 物料list
        List<String> itemFilterList = new ArrayList<>();
        DynamicObjectCollection selectList = ActUtil.queryActSelectList(aos_orgnum);
        for (DynamicObject obj : selectList) {
            String aos_sku = obj.getString("aos_sku");
            itemFilterList.add(aos_sku);
        }

        // ①预计活动日前后30天，活动类型为DOTD，且状态为正常的在线ID、
        Set<String> beforeAfter30Set = ActUtil.queryCullActTypeItem(aos_orgid, start, new String[]{"DOTD", "LD", "7DD", "Tracker"}, 30);

        Map<String, DynamicObject> itemInfo = ActUtil.getItemInfo(itemFilterList, aos_orgid);

        Map<String, DynamicObject> priceMap = ActUtil.getAsinAndPriceFromPrice(aos_orgid, aos_shopid, aos_channelid, itemFilterList);
        // 非平台仓数量
        Map<String, Integer> noPlatQtyMap = ActUtil.queryNoPlatQty(aos_orgid);
        // 查询节日销售时间
        Map<String, String[]> festivalStartAndEnd = ActUtil.queryFestivalStartAndEnd(aos_orgid);
        // 选择满足库存条件的sku
        Map<String, List<Date[]>> actDateMap = ActUtil.query2ActDate(aos_orgid, new String[]{"LD", "7DD"}, 90);
        DynamicObject orgActivityQty = ActUtil.getOrgActivityQty(aos_orgid);
        // 平台仓数量
        Map<String, Integer> platformQtyMap = ActUtil.queryPlatformQty();

        //店铺价格
        List<String> list_noPriceItem = new ArrayList<>(itemFilterList.size());
        String entityId = object.getString("id");
        ActShopPriceDao actShopPriceDao = new ActShopPriceImpl();
        Map<String, BigDecimal> map_shopPrice = actShopPriceDao.QueryOrgChannelShopItemNumberPrice(aos_orgid, aos_channelid, aos_shopid, itemFilterList);

        List<JSONObject> secFilterList = new ArrayList<>();
        for (DynamicObject obj : selectList) {
            String aos_sku = obj.getString("aos_sku");
            String aos_seasonattr = obj.getString("aos_seasonattr");
            DynamicObject itemObj = itemInfo.get(aos_sku);
            if (itemObj == null) {
                MktComUtil.putSyncLog(aos_sync_logS, aos_sku + " 未获取到物料信息");
                continue;
            }

            String aos_itemid = itemObj.getString("aos_itemid");

            // ASIN
            DynamicObject priceObj = priceMap.get(aos_sku);
            String asin = "";
            BigDecimal aos_currentprice;
            if (priceObj == null) {	//每日价格不存在
                if (map_shopPrice.containsKey(aos_itemid)){ //店铺价格存在
                    aos_currentprice = map_shopPrice.get(aos_itemid);
                }
                else {
                    MktComUtil.putSyncLog(aos_sync_logS, aos_sku + " 未获取到价格");
                    list_noPriceItem.add(aos_sku);
                    continue;
                }
            }
            else {
                asin = priceObj.getString("aos_asin");// ASIN码
                aos_currentprice = priceObj.getBigDecimal("aos_currentprice");// 当前价格
            }


            String aos_festivalseting = itemObj.getString("aos_festivalseting");

            // 剔除过季品
            if (ActUtil.isOutSeason(start, aos_seasonattr)) {
                MktComUtil.putSyncLog(aos_sync_logS, aos_sku + " 季节属性:" + aos_seasonattr + " 过季");
                continue;
            }

            // 自有仓库数量
            Integer noPlatQty = noPlatQtyMap.getOrDefault(aos_sku, 0);
            // 平台仓数量
            Integer platformQty = platformQtyMap.getOrDefault(aos_shopnum+aos_sku, 0);
            int platSaleDays = InStockAvailableDays.calInstockSalDays(aos_orgid, aos_itemid, platformQty);

            if (!(noPlatQty >30 || platSaleDays >= 90)) {
                MktComUtil.putSyncLog(aos_sync_logS, aos_sku + " 自有仓库存>30；或平台仓可售天数≥90天；");
                continue;
            }

            // 常规品: 预计活动日可售天数>= 90
            // 预计活动日可售天数
            int salDaysForAct = InStockAvailableDays.calInstockSalDaysForAct(aos_orgid, aos_itemid, start);
            if ("REGULAR".equals(aos_seasonattr) || "SPRING-SUMMER-CONVENTIONAL".equals(aos_seasonattr)) {
                if (salDaysForAct < 90) {
                    MktComUtil.putSyncLog(aos_sync_logS, aos_sku + " 预计可售天数:" + salDaysForAct +" 常规品: 预计活动日可售天数 <90 ");
                    continue;
                }
            }

            int springSummerProToActStartDateBetweenDays = ActUtil.springSummerProToActStartDateBetweenDays(aos_seasonattr, start);
            if (salDaysForAct < springSummerProToActStartDateBetweenDays) {
                MktComUtil.putSyncLog(aos_sync_logS, aos_sku + "春夏品: 预计活动日可售天数=" + salDaysForAct + " 季末-预计活动日=" + springSummerProToActStartDateBetweenDays);
                continue;
            }
            int autumnWinterProToActStartDateBetweenDays = ActUtil.autumnWinterProToActStartDateBetweenDays(aos_seasonattr, start);
            if (salDaysForAct < autumnWinterProToActStartDateBetweenDays) {
                MktComUtil.putSyncLog(aos_sync_logS, aos_sku + "春夏品: 预计活动日可售天数=" + salDaysForAct + " 季末-预计活动日=" + autumnWinterProToActStartDateBetweenDays);
                continue;
            }
            int holidayAndActStartDateBetweenDays = ActUtil.holidayProToActStartDateBetweenDays(festivalStartAndEnd.get(aos_festivalseting), start);
            if (salDaysForAct < holidayAndActStartDateBetweenDays) {
                MktComUtil.putSyncLog(aos_sync_logS, aos_sku + "节日品: 预计活动日可售天数=" + salDaysForAct + " 季末-预计活动日=" + holidayAndActStartDateBetweenDays);
                continue;
            }

            if (beforeAfter30Set.contains(aos_itemid)) {
                MktComUtil.putSyncLog(aos_sync_logS, aos_sku + " 剔除①预计活动日前后30天，活动类型为DOTD/LD/7DD/Tracker，且状态为正常的在线ID");
                continue;
            }

            List<Date[]> dates = actDateMap.get(aos_itemid);
            if (dates != null) {
                // 查到两次活动
                int sum = 0;
                for (Date[] date : dates) {
                    sum = sum + ActUtil.queryActQty(aos_orgid, aos_shopid, date[0], date[1]);
                }
                if ((float)sum / (float) 2 <= 3) {
                    MktComUtil.putSyncLog(aos_sync_logS, aos_sku + " 近3个月内，最近2次LD/7DD均销量≤3");
                    continue;
                }
            }

            String aos_itemname = obj.getString("aos_itemname");
            String aos_category1 = obj.getString("aos_category1");
            String aos_category2 = obj.getString("aos_category2");
            String aos_typedetail = obj.getString("aos_typedetail");
            int aos_lowestqty = orgActivityQty.getInt("aos_lowestqty");
            BigDecimal aos_inventoryratio = orgActivityQty.getBigDecimal("aos_inventoryratio");
            int aos_overseaqty = noPlatQty + platformQty;
            int aos_qty = Math.max(aos_lowestqty, BigDecimal.valueOf(aos_overseaqty).multiply(aos_inventoryratio).setScale(0, BigDecimal.ROUND_HALF_UP).intValue());
            // 线上7天日均销量 R
            float R = InStockAvailableDays.getOrgItemOnlineAvgQty(aos_orgid, aos_itemid);
            BigDecimal aos_preactqty = BigDecimal.valueOf(noPlatQty).subtract(BigDecimal.valueOf(R).multiply(BigDecimal.valueOf(currentToAct)));
            BigDecimal aos_platavgqty = obj.getBigDecimal("aos_platavgqty");
            // 最大库龄
            BigDecimal aos_itemmaxage = obj.getBigDecimal("aos_itemmaxage");
            BigDecimal actPrice;
            if (aos_disstrength != null && aos_disstrength.compareTo(BigDecimal.ZERO) != 0) {
                actPrice = aos_currentprice.multiply(BigDecimal.ONE.subtract(aos_disstrength));
            } else {
                actPrice = aos_currentprice.subtract(aos_disamt);
            }

            JSONObject itemObject = new JSONObject();
            itemObject.put("aos_itemnum", aos_itemid);
            itemObject.put("aos_itemname", aos_itemname);
            itemObject.put("aos_category_stat1", aos_category1);
            itemObject.put("aos_category_stat2", aos_category2);
            itemObject.put("aos_postid", asin);
            itemObject.put("aos_l_startdate", start);
            itemObject.put("aos_enddate", end);
            itemObject.put("aos_actqty", aos_qty);
            itemObject.put("aos_l_actstatus", "A");
            itemObject.put("aos_price", aos_currentprice);
            itemObject.put("aos_actprice", actPrice);
            itemObject.put("aos_currentqty", aos_overseaqty);// 当前库存
            itemObject.put("aos_preactqty", aos_preactqty);// 预计活动日库存 非平台仓数量-R*(预计活动日-今天)
            itemObject.put("aos_preactavadays", salDaysForAct);// 预计活动日可售天数
            itemObject.put("aos_platqty", platformQty);// 平台仓当前库存数
            itemObject.put("aos_age", aos_itemmaxage);// 最大库龄
            itemObject.put("aos_7orgavgqty", R);// 过去7天国别日均销量
            itemObject.put("aos_7platavgqty", aos_platavgqty);// 过去7天平台日均销量
            itemObject.put("aos_discountdegree", aos_disstrength);// 折扣力度
            itemObject.put("aos_discountprice", aos_disamt);// 折扣金额
//            itemObject.put("aos_invcostamt", "");// 库存金额
//            itemObject.put("aos_stars", aos_stars);// review分数
//            itemObject.put("aos_reviewqty", aos_review);// review个数
            itemObject.put("aos_typedetail", aos_typedetail);// 类型细分
            itemObject.put("aos_seasonattr", aos_seasonattr);// 季节属性
            secFilterList.add(itemObject);
        }

        // 生成活动选品清单
        DynamicObjectCollection aos_sal_actplanentity = object.getDynamicObjectCollection("aos_sal_actplanentity");
        aos_sal_actplanentity.clear();

        for (JSONObject obj : secFilterList) {
            DynamicObject lineObj = aos_sal_actplanentity.addNew();
            for (String key : obj.keySet()) {
                lineObj.set(key, obj.get(key));
            }
        }

        SaveServiceHelper.save(new DynamicObject[]{object});
        DynamicObject dynamicObject = BusinessDataServiceHelper.loadSingle(object.getPkValue(), "aos_act_select_plan");
        ActUtil.setProfitRate(dynamicObject);


        // 计算活动毛利率
        Map<String, BigDecimal> orgLowestProfitRate = ActUtil.getOrgLowestProfitRate(aos_orgid);
        ActUtil.setLowestProfitRate(object.getPkValue(), orgLowestProfitRate);

        // 保存日志表
        SaveServiceHelper.save(new DynamicObject[]{aos_sync_log});
        ActUtil.SaveItme(entityId,list_noPriceItem);
    }
}
