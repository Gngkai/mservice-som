package mkt.act.rule.uk;

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
import mkt.common.MKTCom;

import java.math.BigDecimal;
import java.util.*;

public class Mano implements ActStrategy {
	@Override
	public void doOperation(DynamicObject object) throws Exception{
		// 国别id
		String aos_orgid = object.getDynamicObject("aos_nationality").getString("id");
		String aos_orgnum = object.getDynamicObject("aos_nationality").getString("number");
		Date start = object.getDate("aos_startdate");
		Date end = object.getDate("aos_enddate1");
		String aos_channelid = object.getDynamicObject("aos_channel").getString("id");
		String aos_shopid = object.getDynamicObject("aos_shop").getString("id");
		String aos_shopnum = object.getDynamicObject("aos_shop").getString("number");
		String aos_acttypenum = object.getDynamicObject("aos_acttype").getString("number");

		// 折扣信息
		BigDecimal aos_disamt = object.getBigDecimal("aos_disamt");
		BigDecimal aos_disstrength = object.getBigDecimal("aos_disstrength");
		// 至活动日间隔天数
		Calendar instance = Calendar.getInstance();
		instance.set(Calendar.HOUR_OF_DAY, 0);
		instance.set(Calendar.MINUTE, 0);
		instance.set(Calendar.SECOND, 0);
		instance.set(Calendar.MILLISECOND, 0);
		// 当前日期
		long current = instance.getTime().getTime();
		instance.setTime(start);
		long actTime = instance.getTime().getTime();
		// 间隔天数
		long currentToAct = ActUtil.betweenDays(current, actTime);
		System.out.println("间隔天数 = " + currentToAct);

		Set<String> apartFromAmzAndEbayItem = ActUtil.queryApartFromAmzAndEbayItem(aos_orgid, new String[]{"AMAZON", "EBAY"},start);
		// 自有仓库可用量大于等于30的物料
		Map<String, DynamicObject> nonPlatItemSet = ActUtil.queryNonPlatQtyLgN(aos_orgid, 30);
		// 剔除同店铺同期活动
		Set<String> sameShopAndSamePeriodItem = ActUtil.querySamePeriodActivities(aos_orgid, aos_shopid, start, end);
		// 第一次过滤选品清单
		List<DynamicObject> firstFilterList = new ArrayList<>();
		// 物料list
		List<String> itemFilterList = new ArrayList<>();

		DynamicObject aos_sync_log = ActUtil.getCommonLog("ManoUK", aos_orgnum);
		DynamicObjectCollection aos_sync_logS = aos_sync_log.getDynamicObjectCollection("aos_entryentity");


		DynamicObjectCollection selectList = ActUtil.queryActSelectList(aos_orgnum);
		for (DynamicObject obj : selectList) {
			String aos_sku = obj.getString("aos_sku");
			String aos_seasonattr = obj.getString("aos_seasonattr");

			if (ActUtil.isOutSeason(start, aos_seasonattr)){
				MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + " 季节属性:" + aos_seasonattr + " 过季");
				continue;
			}
			if (apartFromAmzAndEbayItem.contains(aos_sku)) {
				MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + "剔除除亚马逊、eBay外活动日过去30天已提报平台活动个数＞3的sku");
				continue;
			}
			if (!nonPlatItemSet.containsKey(aos_sku)) {
				MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + "自有仓库<30");
				continue;
			}
			if (sameShopAndSamePeriodItem.contains(aos_sku)) {
				MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + "剔除同店铺同期活动");
				continue;
			}

			firstFilterList.add(obj);
			itemFilterList.add(aos_sku);
		}
		// 第二次过滤
		Map<String, DynamicObject> priceMap = ActUtil.getAsinAndPriceFromPrice(aos_orgid, aos_shopid, aos_channelid, itemFilterList);
		DynamicObject orgActivityQty = ActUtil.getOrgActivityQty(aos_orgid);
		// 查询节日销售时间
		Map<String, String[]> festivalStartAndEnd = ActUtil.queryFestivalStartAndEnd(aos_orgid);

		Map<String, Integer> manoZeroSalesMap = ActUtil.queryZeroSalesMano(aos_orgid, aos_shopid);
		// 选择满足库存条件的sku
		Map<String, DynamicObject> itemInfo = ActUtil.getItemInfo(itemFilterList, aos_orgid);

		//店铺价格
		List<String> list_noPriceItem = new ArrayList<>(itemFilterList.size());
		String entityId = object.getString("id");
		ActShopPriceDao actShopPriceDao = new ActShopPriceImpl();
		Map<String, BigDecimal> map_shopPrice = actShopPriceDao.QueryOrgChannelShopItemNumberPrice(aos_orgid, aos_channelid, aos_shopid, itemFilterList);

		List<JSONObject> secFilterList = new ArrayList<>();
		for (DynamicObject obj : firstFilterList) {
			String aos_sku = obj.getString("aos_sku");
			String aos_seasonattr = obj.getString("aos_seasonattr");
			DynamicObject itemObj = itemInfo.get(aos_sku);
			if (itemObj == null) {
				MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + "取不到物料信息");
				continue;
			}
			String aos_itemid = itemObj.getString("aos_itemid");
			String aos_festivalseting = itemObj.getString("aos_festivalseting");
			String aos_brand = itemObj.getString("aos_brand");

			// ASIN
			DynamicObject priceObj = priceMap.get(aos_sku);
			String asin = "";
			BigDecimal aos_currentprice;
			if (priceObj == null) {	//每日价格不存在
				if (map_shopPrice.containsKey(aos_itemid)){ //店铺价格存在
					aos_currentprice = map_shopPrice.get(aos_itemid);
				}
				else {
					MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + " 未获取到价格");
					list_noPriceItem.add(aos_sku);
					continue;
				}
			}
			else {
				asin = priceObj.getString("aos_asin");// ASIN码
				aos_currentprice = priceObj.getBigDecimal("aos_currentprice");// 当前价格
			}

			//mano Coupon额外条件 mano过去30天销量=0；
			if ("UK-ManoMano".equals(aos_shopnum) && "Coupon".equals(aos_acttypenum)) {
				Integer sales = manoZeroSalesMap.getOrDefault(aos_itemid, 0);
				if (sales == 0) {
					MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + "Coupon额外条件 mano过去30天销量=0");
					continue;
				}
			}

			// 预计活动日可售天数
			int salDaysForAct = InStockAvailableDays.calInstockSalDaysForAct(aos_orgid, aos_itemid, start);
			// System.out.println("salDaysForAct = " + salDaysForAct);
			// 常规品: 预计活动日可售天数>= 90
			if ("REGULAR".equals(aos_seasonattr) || "SPRING-SUMMER-CONVENTIONAL".equals(aos_seasonattr)) {
				if (salDaysForAct < 90) {
					MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + "常规品: 预计活动日可售天数< 90");
					continue;
				}
			}

			Calendar seasonEnd = Calendar.getInstance();

			// 春夏品
			if ("SPRING".equals(aos_seasonattr) || "SPRING_SUMMER".equals(aos_seasonattr)
					|| "SUMMER".equals(aos_seasonattr)) {
				// 8月31
				seasonEnd.set(Calendar.MONTH, 7);
				seasonEnd.set(Calendar.DAY_OF_MONTH, 31);

				long betweenDays = ActUtil.betweenDays(seasonEnd.getTime().getTime(), start.getTime());
				if (salDaysForAct < betweenDays) {
					MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + "春夏品: 预计活动日可售天数=" + salDaysForAct + " 季末-预计活动日=" + betweenDays);
					continue;
				}

			}

			if ("AUTUMN_WINTER".equals(aos_seasonattr) || "WINTER".equals(aos_seasonattr)) {
				// 3月31
				seasonEnd.set(Calendar.MONTH, 2);
				seasonEnd.set(Calendar.DAY_OF_MONTH, 31);
				if (start.getTime() > seasonEnd.getTime().getTime()) {
					seasonEnd.add(Calendar.YEAR, +1);
				}
				long betweenDays = ActUtil.betweenDays(seasonEnd.getTime().getTime(), start.getTime());
				if (salDaysForAct < betweenDays) {
					MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + "秋冬品: 预计活动日可售天数=" + salDaysForAct + " 季末-预计活动日=" + betweenDays);
					continue;
				}
			}

			// 选品池中季节/节日品,预计活动日可售天数≥(季末-预计活动日)
			// 节日品
			Calendar endDate = Calendar.getInstance();
			Calendar startDate = Calendar.getInstance();
			String[] dateArr = festivalStartAndEnd.get(aos_festivalseting);
			if (dateArr != null) {
				String startDateArr = dateArr[0];
				String[] startArr = startDateArr.split("-");
				startDate.set(Calendar.MONTH, Integer.parseInt(startArr[0]) - 1);
				startDate.set(Calendar.DAY_OF_MONTH, Integer.parseInt(startArr[1]));

				String endDateArr = dateArr[1];
				String[] endArr = endDateArr.split("-");
				endDate.set(Calendar.MONTH, Integer.parseInt(endArr[0]) - 1);
				endDate.set(Calendar.DAY_OF_MONTH, Integer.parseInt(endArr[1]));

				if (endDate.before(startDate)) {
					endDate.add(Calendar.YEAR, +1);
				}
				Date festivalEnd = endDate.getTime();

				long betweenDays = ActUtil.betweenDays(festivalEnd.getTime(), start.getTime());
				if (salDaysForAct < betweenDays) {
					MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + "节日品: 预计活动日可售天数=" + salDaysForAct + " 季末-预计活动日=" + betweenDays);
					continue;
				}
			}


			String aos_itemname = obj.getString("aos_itemname");
			String aos_category1 = obj.getString("aos_category1");
			String aos_category2 = obj.getString("aos_category2");
			// 平台仓库数量
			BigDecimal aos_platfqty = obj.getBigDecimal("aos_platfqty");
			BigDecimal aos_platavgqty = obj.getBigDecimal("aos_platavgqty");
			// 最大库龄
			BigDecimal aos_itemmaxage = obj.getBigDecimal("aos_itemmaxage");

			// 海外在库数量
			int aos_overseaqty = obj.getInt("aos_overseaqty");
			String aos_typedetail = obj.getString("aos_typedetail");
			int aos_lowestqty = orgActivityQty.getInt("aos_lowestqty");
			BigDecimal aos_inventoryratio = orgActivityQty.getBigDecimal("aos_inventoryratio");

			int aos_qty = Math.max(aos_lowestqty, BigDecimal.valueOf(aos_overseaqty).multiply(aos_inventoryratio).setScale(0, BigDecimal.ROUND_HALF_UP).intValue());


			int nonPlatQty = InStockAvailableDays.getNonPlatQty(aos_orgid, aos_itemid);
			// 线上7天日均销量 R
			float R = InStockAvailableDays.getOrgItemOnlineAvgQty(aos_orgid, aos_itemid);
			BigDecimal aos_preactqty = BigDecimal.valueOf(nonPlatQty).subtract(BigDecimal.valueOf(R).multiply(BigDecimal.valueOf(currentToAct)));

			// 活动价 = min(当年最低定价，当前价格*折扣力度,过去30天最低定价*0.95)
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
			itemObject.put("aos_platqty", aos_platfqty);// 平台仓当前库存数
			itemObject.put("aos_age", aos_itemmaxage);// 最大库龄
			itemObject.put("aos_7orgavgqty", R);// 过去7天国别日均销量
			itemObject.put("aos_7platavgqty", aos_platavgqty);// 过去7天平台日均销量
			itemObject.put("aos_discountdegree", aos_disstrength);// 折扣力度
			itemObject.put("aos_discountprice", aos_disamt);// 折扣金额
			itemObject.put("aos_typedetail", aos_typedetail);// 折扣金额
			itemObject.put("aos_seasonattr", aos_seasonattr);// 季节属性
			itemObject.put("aos_brand", aos_brand);// 品牌
//            itemObject.put("aos_invcostamt", "");// 库存金额
//            itemObject.put("aos_stars", aos_stars);// review分数
//            itemObject.put("aos_reviewqty", aos_review);// review个数
			secFilterList.add(itemObject);
		}

		DynamicObjectCollection aos_sal_actplanentity = object.getDynamicObjectCollection("aos_sal_actplanentity");
		aos_sal_actplanentity.clear();

		// 生成活动选品清单
		for (JSONObject obj : secFilterList) {
			DynamicObject lineObj = aos_sal_actplanentity.addNew();
			for (String key : obj.keySet()) {
				lineObj.set(key, obj.get(key));
			}
		}

		SaveServiceHelper.save(new DynamicObject[] { object });
		DynamicObject dynamicObject = BusinessDataServiceHelper.loadSingle(object.getPkValue(),
				"aos_act_select_plan");
		ActUtil.setProfitRate(dynamicObject);

		// 计算活动毛利率
		Map<String, BigDecimal> orgLowestProfitRate = ActUtil.getOrgLowestProfitRate(aos_orgid);
		ActUtil.setLowestProfitRate(object.getPkValue(), orgLowestProfitRate);

		// 保存日志表
		SaveServiceHelper.save(new DynamicObject[]{aos_sync_log});
		ActUtil.SaveItme(entityId,list_noPriceItem);
	}

}
