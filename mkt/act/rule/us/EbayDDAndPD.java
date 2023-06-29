package mkt.act.rule.us;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

public class EbayDDAndPD implements ActStrategy {
	@Override
	public void doOperation(DynamicObject object) throws Exception {
		System.out.println("=======Start doOperation=======");
		/** 数据层 **/
		String aos_orgnum = object.getDynamicObject("aos_nationality").getString("number");// 国别
		String aos_orgid = object.getDynamicObject("aos_nationality").getString("id"); // 国别id
		Date start = object.getDate("aos_startdate");// 活动开始日
		Date end = object.getDate("aos_enddate1"); // 活动结束日
		String aos_channelid = object.getDynamicObject("aos_channel").getString("id");// 平台id
		String aos_shopid = object.getDynamicObject("aos_shop").getString("id");// 店铺id
		int currentToAct = ActUtil.currentToActivityDateBetweenDays(start);// 至活动日间隔天数
		BigDecimal aos_disamt = object.getBigDecimal("aos_disamt");// 折扣信息
		BigDecimal aos_disstrength = object.getBigDecimal("aos_disstrength");// 折扣力度
		Map<String, Integer> nonPlatItemSet = ActUtil.queryNonPlatQty(aos_orgid);// 非平台可用量
		Map<String, DynamicObject> reviewItemSet = ActUtil.queryReview(aos_orgid);// 满足review条件的物料
		DynamicObjectCollection aos_mkt_actselectS = ActUtil.queryActSelectList(aos_orgnum);// 活动选品清单
		List<String> itemFilterList = GenerateItemList(aos_mkt_actselectS);// 所有该国别下物料
		List<JSONObject> InsertList = new ArrayList<>();// 导入对象
		int total = aos_mkt_actselectS.size();// 总行数
		int row = 1;// 当前行
		Map<String, DynamicObject> itemInfo = ActUtil.getItemInfo(itemFilterList, aos_orgid);// 物料信息
		Map<String, DynamicObject> priceMap = ActUtil.getAsinAndPriceFromPrice(aos_orgid, aos_shopid, aos_channelid,
				itemFilterList);// 每日价格
		DynamicObject orgActivityQty = ActUtil.getOrgActivityQty(aos_orgid);// 活动数量
		Map<String, DynamicObject> lowestPriceMap = ActUtil.queryLowestPrice(aos_orgid, itemFilterList);// 最低价
		HashMap<String, Object> Group = DotdUS.GenerateGroup();// 产品类别数据
		Map<String, String[]> festivalStartAndEnd = ActUtil.queryFestivalStartAndEnd(aos_orgid);// 查询节日销售时间
		Set<String> sameShopAndSamePeriodItem = ActUtil.querySamePeriodActivities(aos_orgid, aos_shopid, start, end); // 剔除同店铺同期活动
		//店铺价格
		List<String> list_noPriceItem = new ArrayList<>(itemFilterList.size());
		String entityId = object.getString("id");
		ActShopPriceDao actShopPriceDao = new ActShopPriceImpl();
		Map<String, BigDecimal> map_shopPrice = actShopPriceDao.QueryOrgChannelShopItemNumberPrice(aos_orgid, aos_channelid, aos_shopid, itemFilterList);

		/** 日志对象 **/
		DynamicObject aos_sync_log = ActUtil.getCommonLog("DotdUS", aos_orgnum);// 日志对象
		DynamicObjectCollection aos_sync_logS = aos_sync_log.getDynamicObjectCollection("aos_entryentity");// 日志行
		for (DynamicObject aos_mkt_actselect : aos_mkt_actselectS) {
			System.out.println("进度" + "(" + row + "/" + total + ")");
			row++;
			/** 行数据层 **/
			String aos_sku = aos_mkt_actselect.getString("aos_sku");// 物料编码
			String aos_seasonattr = aos_mkt_actselect.getString("aos_seasonattr");// 季节属性
			String aos_itemname = aos_mkt_actselect.getString("aos_itemname");// 产品名称
			String aos_category1 = aos_mkt_actselect.getString("aos_category1");// 产品大类
			String aos_category2 = aos_mkt_actselect.getString("aos_category2");// 产品中类
			BigDecimal aos_platfqty = aos_mkt_actselect.getBigDecimal("aos_platfqty");// 平台仓库数量
			BigDecimal aos_platavgqty = aos_mkt_actselect.getBigDecimal("aos_platavgqty");// 平台仓日均销量
			BigDecimal aos_itemmaxage = aos_mkt_actselect.getBigDecimal("aos_itemmaxage");// 最大库龄
			int aos_overseaqty = aos_mkt_actselect.getInt("aos_overseaqty");// 海外在库数量
			String aos_typedetail = aos_mkt_actselect.getString("aos_typedetail");// 活动选品类型细分

			int aos_lowestqty = orgActivityQty.getInt("aos_lowestqty");// 活动数量
			BigDecimal aos_inventoryratio = orgActivityQty.getBigDecimal("aos_inventoryratio");// 活动数量占比

			int aos_qty = Math.max(aos_lowestqty, BigDecimal.valueOf(aos_overseaqty).multiply(aos_inventoryratio)
					.setScale(0, BigDecimal.ROUND_HALF_UP).intValue());// 可提报活动数量
			/** 标准剔除 **/
			// 物料信息
			DynamicObject itemObj = itemInfo.get(aos_sku);
			if (itemObj == null) {
				MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + " 未获取到物料信息");
				continue;
			}
			String aos_itemid = itemObj.getString("aos_itemid");// 物料id
			int nonPlatQty = InStockAvailableDays.getNonPlatQty(aos_orgid, aos_itemid);// 非平台可用量
			float R = InStockAvailableDays.getOrgItemOnlineAvgQty(aos_orgid, aos_itemid);// 线上7天日均销量 R
			BigDecimal aos_preactqty = BigDecimal.valueOf(nonPlatQty)
					.subtract(BigDecimal.valueOf(R).multiply(BigDecimal.valueOf(currentToAct)));// 预计活动日库存
			int salDaysForAct = InStockAvailableDays.calInstockSalDaysForAct(aos_orgid, aos_itemid, start);// 预计活动日可售天数

			String aos_festivalseting = itemObj.getString("aos_festivalseting");

			Object Category2 = Group.get(aos_sku);
			if (Category2 == null) {
				MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + " 类别信息不存在");
				continue;
			}

			// 物料每日价格
			DynamicObject priceObj = priceMap.get(aos_sku);
			String asin = "";
			BigDecimal aos_currentprice;
			if (priceObj == null) {	//每日价格不存在
				if (map_shopPrice.containsKey(aos_itemid)){ //店铺价格存在
					aos_currentprice = map_shopPrice.get(aos_itemid);
				}
				else {
					list_noPriceItem.add(aos_sku);
					MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + " 未获取到价格");
					continue;
				}
			}
			else {
				asin = priceObj.getString("aos_asin");// ASIN码
				aos_currentprice = priceObj.getBigDecimal("aos_currentprice");// 当前价格
			}

			BigDecimal actPrice = aos_currentprice.multiply(BigDecimal.valueOf(0.9));// 活动价
			DynamicObject lowestPriceObj = lowestPriceMap.get(aos_itemid);// 最低价
			if (lowestPriceObj == null) {
				MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + " 未取到最低价");
				continue;
			}

			// Review
			DynamicObject reviewObj = reviewItemSet.get(aos_sku);
			if (reviewObj == null) {
				MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + " 未获取到Review");
				continue;
			}
			BigDecimal aos_review = reviewObj.getBigDecimal("aos_review");
			BigDecimal aos_stars = reviewObj.getBigDecimal("aos_stars");

			/** 客制化剔除 **/
			// 自有仓库库存数量
			int ownWarehouseQuantity = nonPlatItemSet.getOrDefault(aos_sku, 0);
			if (ownWarehouseQuantity == 0) {
				MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + " 自有仓库存库存为0");
				continue;
			}

			// 自有仓库存>80
			if (!(ownWarehouseQuantity > 80)) {
				MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + " 自有仓库存 >80；");
				continue;
			}

			// 常规品: 预计活动日可售天数>= 90
			// 预计活动日可售天数
			if ("REGULAR".equals(aos_seasonattr) || "SPRING-SUMMER-CONVENTIONAL".equals(aos_seasonattr)) {
				if (salDaysForAct < 90) {
					MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + " 预计可售天数:" + salDaysForAct + " 常规品: 预计活动日可售天数 <90 ");
					continue;
				}
			}

			int springSummerProToActStartDateBetweenDays = ActUtil
					.springSummerProToActStartDateBetweenDays(aos_seasonattr, start);
			if (salDaysForAct < springSummerProToActStartDateBetweenDays) {
				MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + "春夏品: 预计活动日可售天数=" + salDaysForAct + " 季末-预计活动日="
						+ springSummerProToActStartDateBetweenDays);
				continue;
			}
			int autumnWinterProToActStartDateBetweenDays = ActUtil
					.autumnWinterProToActStartDateBetweenDays(aos_seasonattr, start);
			if (salDaysForAct < autumnWinterProToActStartDateBetweenDays) {
				MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + "春夏品: 预计活动日可售天数=" + salDaysForAct + " 季末-预计活动日="
						+ autumnWinterProToActStartDateBetweenDays);
				continue;
			}
			int holidayAndActStartDateBetweenDays = ActUtil
					.holidayProToActStartDateBetweenDays(festivalStartAndEnd.get(aos_festivalseting), start);
			if (salDaysForAct < holidayAndActStartDateBetweenDays) {
				MKTCom.Put_SyncLog(aos_sync_logS,
						aos_sku + "节日品: 预计活动日可售天数=" + salDaysForAct + " 季末-预计活动日=" + holidayAndActStartDateBetweenDays);
				continue;
			}

			// 剔除活动日，非当季SKU
			if (ActUtil.isOutSeason(start, aos_seasonattr)) {
				MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + " 季节属性:" + aos_seasonattr + " 剔除活动日，非当季SKU");
				continue;
			}

			// 剔除同店铺同期活动
			if (sameShopAndSamePeriodItem.contains(aos_sku)) {
				MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + "剔除同店铺同期活动");
				continue;
			}

			// 当前国别Review分数>=3或review分数==0
			if (reviewObj != null) {
				if (!(aos_stars.compareTo(BigDecimal.valueOf(0)) == 0
						|| aos_stars.compareTo(BigDecimal.valueOf(3)) >= 0)) {
					MKTCom.Put_SyncLog(aos_sync_logS, aos_sku + " Review分数:" + aos_stars);
					continue;
				}
			}

			/** 数据导入 **/
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
			itemObject.put("aos_stars", aos_stars);// review分数
			itemObject.put("aos_reviewqty", aos_review);// review个数
			itemObject.put("aos_typedetail", aos_typedetail);// 类型细分
			itemObject.put("aos_seasonattr", aos_seasonattr);// 季节属性
			InsertList.add(itemObject);
		}

		if (InsertList.isEmpty())
			return;
		DynamicObjectCollection aos_sal_actplanentity = object.getDynamicObjectCollection("aos_sal_actplanentity");
		aos_sal_actplanentity.clear();
		// 生成活动选品清单
		for (JSONObject obj : InsertList) {
			DynamicObject lineObj = aos_sal_actplanentity.addNew();
			for (String key : obj.keySet()) {
				lineObj.set(key, obj.get(key));
			}
		}

		// TODO
		// 前4期活动效果平均增量sku倒序（优先判断）*50%+库存金额倒序SKU*50%（按销售组sku个数比例分配），活动总数=200（相同的在线ID只算1个名额）；

		SaveServiceHelper.save(new DynamicObject[] { object });
		DynamicObject dynamicObject = BusinessDataServiceHelper.loadSingle(object.getPkValue(), "aos_act_select_plan");
		ActUtil.setProfitRate(dynamicObject);
		// 计算活动毛利率
		Map<String, BigDecimal> orgLowestProfitRate = ActUtil.getOrgLowestProfitRate(aos_orgid);
		ActUtil.setLowestProfitRate(object.getPkValue(), orgLowestProfitRate);
		// 保存日志表
		SaveServiceHelper.save(new DynamicObject[] { aos_sync_log });
		ActUtil.SaveItme(entityId,list_noPriceItem);
	}

	/** 初始化物料List **/
	private List<String> GenerateItemList(DynamicObjectCollection aos_mkt_actselectS) {
		List<String> itemFilterList = new ArrayList<>();
		for (DynamicObject obj : aos_mkt_actselectS) {
			String aos_sku = obj.getString("aos_sku");
			itemFilterList.add(aos_sku);
		}
		return itemFilterList;
	}

}