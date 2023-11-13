package mkt.act.rule;

import com.grapecity.documents.excel.B;
import common.CommonDataSomAct;
import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public class ActUtil {
	/** 活动计划和选品 保存价格剔除货号 **/
	public static void SaveItme (String EntityID,List<String> list_itemNUmber){
		if (list_itemNUmber == null || list_itemNUmber.size() == 0)
			return;
		QFilter filter_id = new QFilter("id","=",EntityID);
		DynamicObject dy = QueryServiceHelper.queryOne("aos_act_select_plan", "billno", new QFilter[]{filter_id});
		if (dy==null)
			return;
		String billno = dy.getString("billno");
		QFilter filter = new QFilter("aos_billno","=",billno);
		DeleteServiceHelper.delete("aos_mkt_noprice",new QFilter[]{filter});
		DynamicObject dy_noprice = BusinessDataServiceHelper.newDynamicObject("aos_mkt_noprice");
		dy_noprice.set("aos_billno",billno);
		dy_noprice.set("billstatus","A");
		DynamicObjectCollection dyc = dy_noprice.getDynamicObjectCollection("entryentity");
		for (String item : list_itemNUmber) {
			DynamicObject dy_new = dyc.addNew();
			dy_new.set("aos_item",item);
		}
		SaveServiceHelper.save(new DynamicObject[]{dy_noprice});
	}
	/**
	 *
	 * @param date 开始日期
	 * @param seasonattr 季节属性
	 * @return 是否过季  true 是 false 否
	 */
	public static boolean isOutSeason(Date date, String seasonattr) {
		String seasonpro = "";
		if ("AUTUMN_WINTER".equals(seasonattr) || "WINTER".equals(seasonattr)) {
			seasonpro = "AUTUMN_WINTER_PRO";
		} else if ("SPRING".equals(seasonattr) || "SPRING_SUMMER".equals(seasonattr) || "SUMMER".equals(seasonattr)) {
			seasonpro = "SPRING_SUMMER_PRO";
		}
		Calendar instance = Calendar.getInstance();
		instance.setTime(date);
		int month = instance.get(Calendar.MONTH);
		return ("SPRING_SUMMER_PRO".equals(seasonpro) && month > Calendar.AUGUST)
				|| ("AUTUMN_WINTER_PRO".equals(seasonpro) && month > Calendar.MARCH && month < Calendar.AUGUST);
	}

	public static Map<String, BigDecimal> queryVrpPrice(String aos_orgid) {
		String selectFields = "aos_itemid,aos_vrp";
		DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_vrp", selectFields,
				new QFilter[] { new QFilter("aos_orgid", QCP.equals, aos_orgid) });

		return list.stream().collect(Collectors.toMap(obj -> obj.getString("aos_itemid"),
				obj -> obj.getBigDecimal("aos_vrp"), (k1, k2) -> k1));
	}

	// 查询Review分数和Review个数
	public static Map<String, DynamicObject> queryReview(String aos_orgid) {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_sync_review",
				"aos_itemid.number aos_itemnum,aos_review,aos_stars",
				new QFilter[] { new QFilter("aos_orgid", QCP.equals, aos_orgid) });
		return list.stream().collect(Collectors.toMap(obj -> obj.getString("aos_itemnum"), obj -> obj, (k1, k2) -> k1));
	}

	// 获取非平台可用量大于30的物料
	public static Map<String, DynamicObject> queryNonPlatQtyLgN(String aos_orgid, int n) {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_sync_invou_value",
				"aos_item.number aos_itemnum,aos_noplatform_qty,aos_fba_qty",
				new QFilter[] { new QFilter("aos_ou", QCP.equals, aos_orgid),
						new QFilter("aos_noplatform_qty", QCP.large_equals, n) });
		return list.stream().collect(Collectors.toMap(obj -> obj.getString("aos_itemnum"), obj -> obj, (k1, k2) -> k1));
	}

	public static Map<String, Integer> queryNonPlatQty(String aos_orgid) {
		String selectFields = "aos_item.number aos_itemnum,aos_noplatform_qty";
		DynamicObjectCollection list = QueryServiceHelper.query("aos_sync_invou_value", selectFields,
				new QFilter[] { new QFilter("aos_ou", QCP.equals, aos_orgid) });
		return list.stream().collect(Collectors.toMap(obj -> obj.getString("aos_itemnum"),
				obj -> obj.getInt("aos_noplatform_qty"), (k1, k2) -> k1));
	}

	/**
	 * 平台仓数量
	 * 
	 * @return KEY: 店铺编码+货号编码 VALUE:平台仓数量
	 */
	public static Map<String, Integer> queryPlatformQty() {
		String algoKey = "queryPlatFormQty";
		String selectFields = "aos_subinv.aos_belongshop.number aos_belongshop," + "aos_item.number aos_itemnum,"
				+ "aos_available_qty";
		DataSet dataSet = QueryServiceHelper.queryDataSet(algoKey, "aos_sync_invsub_value", selectFields,
				new QFilter[] { new QFilter("aos_subinv.aos_belongshop", QCP.is_notnull, null) }, null);
		dataSet = dataSet.groupBy(new String[] { "aos_belongshop", "aos_itemnum" }).sum("aos_available_qty").finish();

		Map<String, Integer> result = new HashMap<>();
		while (dataSet.hasNext()) {
			Row next = dataSet.next();
			String aos_belongshop = next.getString("aos_belongshop");
			String aos_itemnum = next.getString("aos_itemnum");
			Integer aos_available_qty = next.getInteger("aos_available_qty");
			result.put(aos_belongshop + aos_itemnum, aos_available_qty);
		}

		selectFields = "aos_item.number aos_itemnum," + "aos_available_qty";
		DataSet dsvDataSet = QueryServiceHelper.queryDataSet(algoKey, "aos_sync_invsub_value", selectFields,
				new QFilter[] { new QFilter("aos_subinv.name", QCP.equals, "DSV") }, null);

		dsvDataSet = dsvDataSet.groupBy(new String[] { "aos_itemnum" }).sum("aos_available_qty").finish();

		while (dsvDataSet.hasNext()) {
			Row next = dsvDataSet.next();
			String aos_itemnum = next.getString("aos_itemnum");
			Integer aos_available_qty = next.getInteger("aos_available_qty");
			result.put("IT-eMag" + aos_itemnum, aos_available_qty);
			result.put("IT-eMAG.BG" + aos_itemnum, aos_available_qty);
			result.put("IT-emag.hu" + aos_itemnum, aos_available_qty);
			result.put("IT-Vivre.ro" + aos_itemnum, aos_available_qty);
		}
		return result;
	}

	/**
	 * 非平台仓数量
	 * 
	 * @param aos_orgid
	 *            国别
	 * @return KEY:货号编码 VALUE:非平台仓数量
	 */
	public static Map<String, Integer> queryNoPlatQty(String aos_orgid) {
		String algoKey = "getNoPlatQty";
		String selectFields = "aos_item.number aos_itemnum," + "aos_available_qty";
		QFilter qFilter = new QFilter("aos_subinv.aos_belongshop", QCP.is_null, null);
		qFilter.and(new QFilter("aos_subinv.name", QCP.not_equals, "DSV"));
		qFilter.and(new QFilter("aos_ou", QCP.equals, aos_orgid));
		DataSet dataSet = QueryServiceHelper.queryDataSet(algoKey, "aos_sync_invsub_value", selectFields,
				qFilter.toArray(), null);
		dataSet = dataSet.groupBy(new String[] { "aos_itemnum" }).sum("aos_available_qty").finish();

		Map<String, Integer> result = new HashMap<>();
		while (dataSet.hasNext()) {
			Row next = dataSet.next();
			String aos_itemnum = next.getString("aos_itemnum");
			Integer aos_available_qty = next.getInteger("aos_available_qty");
			result.put(aos_itemnum, aos_available_qty);
		}
		return result;
	}

	// 获取活动数量Obj
	public static DynamicObject getOrgActivityQty(String aos_orgid) {
		return QueryServiceHelper.queryOne("aos_mkt_activityqty", "aos_lowestqty,aos_inventoryratio",
				new QFilter[] { new QFilter("aos_orgid", QCP.equals, aos_orgid) });
	}

	// 获取最低毛利
	public static Map<String, BigDecimal> getOrgLowestProfitRate(String aos_orgid) {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_activitypara", "aos_type,aos_profit",
				new QFilter[] { new QFilter("aos_orgid", QCP.equals, aos_orgid) });
		return list.stream().collect(Collectors.toMap(obj -> obj.getString("aos_type"),
				obj -> obj.getBigDecimal("aos_profit"), (k1, k2) -> k1));
	}

	public static void setLowestProfitRate(Object billid, Map<String, BigDecimal> orgLowestProfitRate) {
		DynamicObject dynamicObject1 = BusinessDataServiceHelper.loadSingle(billid, "aos_act_select_plan");
		DynamicObjectCollection aos_sal_actplanentity1 = dynamicObject1
				.getDynamicObjectCollection("aos_sal_actplanentity");
		if (aos_sal_actplanentity1.size() == 0)
			return;
		Iterator<DynamicObject> iterator = aos_sal_actplanentity1.iterator();
		while (iterator.hasNext()) {
			DynamicObject object = iterator.next();
			// 活动毛利率
			BigDecimal aos_profit = BigDecimal.ZERO;
			if (object!=null && object.get("aos_profit")!=null)
				aos_profit = object.getBigDecimal("aos_profit");

			// 类型细分
			String aos_typedetail = object.getString("aos_typedetail");
			BigDecimal lowestProfitRate = orgLowestProfitRate.getOrDefault(aos_typedetail, BigDecimal.ZERO);

			// 如果活动毛利率 小于 最低毛利率

			if (aos_profit.compareTo(lowestProfitRate) < 0) {
				iterator.remove();
			}
		}
		SaveServiceHelper.save(new DynamicObject[] { dynamicObject1 });
	}

	public static DynamicObjectCollection queryActSelectList(String ouCode) {
		String yyyyMd = DateFormatUtils.format(Calendar.getInstance(), "yyyyMd");
		String selectFields = "aos_entryentity.aos_sku aos_sku," + "aos_entryentity.aos_category1 aos_category1,"
				+ "aos_entryentity.aos_category2 aos_category2," + "aos_entryentity.aos_itemname aos_itemname,"
				+ "aos_entryentity.aos_seasonpro aos_seasonattr," + "aos_entryentity.aos_qty aos_qty,"
				+ "aos_entryentity.aos_overseaqty aos_overseaqty," + "aos_entryentity.aos_platfqty aos_platfqty,"
				+ "aos_entryentity.aos_platavgqty aos_platavgqty," + "aos_entryentity.aos_itemmaxage aos_itemmaxage,"
				+ "aos_entryentity.aos_typedetail aos_typedetail," + "0 as aos_invcostamt";
		return QueryServiceHelper.query("aos_mkt_actselect", selectFields,
				new QFilter[] { new QFilter("billno", QCP.equals, ouCode + yyyyMd) });
	}

	public static Map<String, DynamicObject> getAsinAndPriceFromPrice(String aos_orgid, String aos_shopid,
			String aos_channelid, List<String> itemList) {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_sync_invprice",
				"aos_item_code.number aos_sku,aos_asin,aos_currentprice",
				new QFilter[] { new QFilter("aos_orgid", QCP.equals, aos_orgid),
						new QFilter("aos_shopfid", QCP.equals, aos_shopid),
						new QFilter("aos_platformfid", QCP.equals, aos_channelid),
						new QFilter("aos_item_code.number", QCP.in, itemList), },
				"aos_asin desc");
		return list.stream()
				.collect(Collectors.toMap(obj -> obj.getString("aos_sku"), obj -> obj, (key1, key2) -> key1));
	}

	public static Map<String, DynamicObject> getAmazonPrice(String aos_orgid, List<String> itemList) {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_sync_invprice",
				"aos_item_code.number aos_sku,aos_asin,aos_currentprice",
				new QFilter[] { new QFilter("aos_orgid", QCP.equals, aos_orgid),
						new QFilter("aos_platformfid.number", QCP.equals, "AMAZON"),
						new QFilter("aos_item_code.number", QCP.in, itemList) });
		return list.stream()
				.collect(Collectors.toMap(obj -> obj.getString("aos_sku"), obj -> obj, (key1, key2) -> key1));
	}

	public static void setProfitRate(DynamicObject dy_the) throws Exception {
		String orgid = dy_the.getDynamicObject("aos_nationality").get("id").toString();
		String shopid = dy_the.getDynamicObject("aos_shop").get("id").toString();
		DynamicObjectCollection dyc_ent = dy_the.getDynamicObjectCollection("aos_sal_actplanentity");

		// 对引入模板中的活动结束时间进行筛选，并进行赋值
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		Date latedate = sdf.parse("2019-11-1");
		for (DynamicObject dynamicObject : dyc_ent) {
			if (dynamicObject.get("aos_l_actstatus").equals("A")) {
				Date aos_enddate = (Date) dynamicObject.get("aos_enddate");
				if (latedate.compareTo(aos_enddate) < 0) {
					latedate = aos_enddate;
				}
			}
		}
		if (!latedate.equals(sdf.parse("2019-11-1"))) {
			dy_the.set("aos_enddate1", latedate);
		}

		// 获取物料和价格
		Map<String, BigDecimal[]> map_itemAndPrice = dyc_ent.stream().collect(Collectors
				.toMap(dy -> dyc_ent.indexOf(dy) + "/" + dy.getDynamicObject("aos_itemnum").getString("id"), dy -> {
					BigDecimal[] big = new BigDecimal[2];
					big[0] = dy.getBigDecimal("aos_price");
					big[1] = dy.getBigDecimal("aos_actprice");
					return big;
				}));
		// 获取每个物料的开始时间和结束时间('/' 为分割符)
		Map<String, String> map_itemToDate = new HashMap<>();
		for (int i = 0; i < dyc_ent.size(); i++) {
			DynamicObject dy = dyc_ent.get(i);
			if (dy.get("aos_l_startdate") != null && dy.get("aos_enddate") != null) {
				String itemKey = i + "/" + dy.getDynamicObject("aos_itemnum").getString("id");
				LocalDate date_s = dy.getDate("aos_l_startdate").toInstant().atZone(ZoneId.systemDefault())
						.toLocalDate();
				LocalDate date_e = dy.getDate("aos_enddate").toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
				map_itemToDate.put(itemKey, date_s.toString() + "/" + date_e.toString());
			}
		}
		// 计算毛利率
		Map<String, Map<String, BigDecimal>> map_for = CommonDataSomAct.get_formula(orgid, shopid, map_itemToDate,
				map_itemAndPrice);
		List<String> list_mapItem = new ArrayList<>(map_for.keySet());
		dyc_ent.stream()
				.filter(dy -> list_mapItem
						.contains(dyc_ent.indexOf(dy) + "/" + dy.getDynamicObject("aos_itemnum").getString("id")))
				.forEach(dy -> {
					String itemkey = dyc_ent.indexOf(dy) + "/" + dy.getDynamicObject("aos_itemnum").getString("id");
					Map<String, BigDecimal> map_the = map_for.get(itemkey);
					dy.set("aos_profit", map_the.get("value"));
					dy.set("aos_item_cost", map_the.get("aos_item_cost"));
					dy.set("aos_lowest_fee", map_the.get("aos_lowest_fee"));
					dy.set("aos_plat_rate", map_the.get("aos_plat_rate"));
					dy.set("aos_vat_amount", map_the.get("aos_vat_amount"));
					dy.set("aos_excval", map_the.get("aos_excval"));
				});

		dyc_ent.stream().filter(dy -> dy.getString("aos_itemname") == null || dy.getString("aos_itemname").equals(""))
				.forEach(dy -> {
					String name = dy.getDynamicObject("aos_itemnum").getString("name");
					dy.set("aos_itemname", name);
				});
		SaveServiceHelper.save(new DynamicObject[] { dy_the });
	}

	public static Map<String, String[]> queryFestivalStartAndEnd(String aos_orgid) {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_season_saltime",
				"aos_festivalattr,aos_start,aos_end",
				new QFilter[] { new QFilter("aos_orgid", QCP.equals, aos_orgid) });
		return list.stream().collect(Collectors.toMap(obj -> obj.getString("aos_festivalattr"),
				obj -> new String[] { obj.getString("aos_start"), obj.getString("aos_end") }, (key1, key2) -> key1));
	}

	public static long betweenDays(long time1, long time2) {
		return Math.abs(time1 - time2) / (1000 * 3600 * 24);
	}

	// 获取物料id
	public static Map<String, DynamicObject> getItemInfo(List<String> itemList, String aos_orgid) {
		DynamicObjectCollection list = QueryServiceHelper.query("bd_material",
				"number aos_sku, id aos_itemid,aos_contryentry.aos_festivalseting aos_festivalseting,aos_contryentry.aos_contrybrand.name aos_brand,"
						+ "aos_contryentry.aos_contryentrystatus aos_contryentrystatus",
				new QFilter[] { new QFilter("number", QCP.in, itemList),
						new QFilter("aos_contryentry.aos_nationality", QCP.equals, aos_orgid) });
		return list.stream()
				.collect(Collectors.toMap(obj -> obj.getString("aos_sku"), obj -> obj, (key1, key2) -> key1));
	}

	/**
	 *
	 * @param aos_orgid
	 *            国别
	 * @param actType
	 *            活动类型
	 * @param days
	 *            近n天
	 * @return
	 */
	public static Map<String, List<Date[]>> query2ActDate(String aos_orgid, String[] actType, int days) {
		// 获取近3个月的活动
		Calendar instance = Calendar.getInstance();
		String endDate = DateFormatUtils.format(instance.getTime(), "yyyy-MM-dd");
		instance.add(Calendar.DAY_OF_MONTH, -days);
		String startDate = DateFormatUtils.format(instance.getTime(), "yyyy-MM-dd");
		String selectFields = "aos_sal_actplanentity.aos_itemnum aos_itemnum,aos_sal_actplanentity.aos_l_startdate aos_l_startdate,aos_sal_actplanentity.aos_enddate aos_enddate";
		DynamicObjectCollection list = QueryServiceHelper.query("aos_act_select_plan", selectFields,
				new QFilter[] { new QFilter("aos_nationality", QCP.equals, aos_orgid),
						new QFilter("aos_acttype.number", QCP.in, actType),
						new QFilter("aos_sal_actplanentity.aos_l_actstatus", QCP.equals, "A"),
						new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.large_equals, startDate),
						new QFilter("aos_sal_actplanentity.aos_enddate", QCP.less_equals, endDate) },
				"aos_sal_actplanentity.aos_l_startdate desc");
		Map<String, List<Date[]>> itemDateMap = new HashMap<>();
		for (DynamicObject obj : list) {
			String aos_itemid = obj.getString("aos_itemnum");
			Date aos_l_startdate = obj.getDate("aos_l_startdate");
			Date aos_enddate = obj.getDate("aos_enddate");

			List<Date[]> dateList = itemDateMap.computeIfAbsent(aos_itemid, k -> new ArrayList<>(2));
			if (dateList.size() > 2)
				continue;// 超过两次活动不要
			Date[] date = new Date[2];
			date[0] = aos_l_startdate;
			date[1] = aos_enddate;
			dateList.add(date);
		}

		Iterator<String> iterator = itemDateMap.keySet().iterator();
		while (iterator.hasNext()) {
			String next = iterator.next();
			List<Date[]> dates = itemDateMap.get(next);
			if (dates.size() < 2)
				iterator.remove();
		}
		return itemDateMap;
	}

	// 获取某段时间活动数量
	public static int queryActQty(String aos_orgid, String aos_shopid, Date start, Date end) {
		List<QFilter> orderFilter = SalUtil.getOrderFilter();
		DynamicObject dynamicObject = QueryServiceHelper.queryOne("aos_sync_om_order_r",
				"sum(aos_order_qty) aos_order_qty",
				new QFilter[] { new QFilter("aos_org", QCP.equals, aos_orgid),
						new QFilter("aos_shopfid", QCP.equals, aos_shopid),
						new QFilter("aos_local_date", QCP.large_equals, DateFormatUtils.format(start, "yyyy-MM-dd")),
						new QFilter("aos_local_date", QCP.less_equals, DateFormatUtils.format(end, "yyyy-MM-dd")),
						orderFilter.get(0) });
		return dynamicObject == null ? 0 : dynamicObject.getInt("aos_order_qty");
	}

	// 获取当前价格
	public static Map<String, BigDecimal> queryCurrentPrice(String aos_orgid, String aos_shopid) {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_sync_invprice",
				"aos_item_code aos_itemid,aos_currentprice",
				new QFilter[] { new QFilter("aos_orgid", QCP.equals, aos_orgid),
						new QFilter("aos_shopfid", QCP.equals, aos_shopid) });
		return list.stream().collect(Collectors.toMap(obj -> obj.getString("aos_itemid"),
				obj -> obj.getBigDecimal("aos_currentprice"), (key1, key2) -> key1));
	}

	public static Map<String, String> queryCtgAndGroup(String aos_orgid) {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_sal_group", "aos_category,aos_sal_org aos_groupid",
				new QFilter[] { new QFilter("aos_org", QCP.equals, aos_orgid) });
		return list.stream().collect(Collectors.toMap(obj -> obj.getString("aos_category"),
				obj -> obj.getString("aos_groupid"), (key1, key2) -> key2));
	}

	// 查询品类
	public static Map<String, String> queryItemCtg(List<String> itemList) {
		DynamicObjectCollection list = QueryServiceHelper.query("bd_materialgroupdetail",
				"material aos_itemid, group aos_categoryid",
				new QFilter[] { new QFilter("standard.name", QCP.equals, "物料基本分类标准"),
						new QFilter("material.number", QCP.in, itemList) });
		return list.stream().collect(Collectors.toMap(obj -> obj.getString("aos_itemid"),
				obj -> obj.getString("aos_categoryid"), (key1, key2) -> key2));
	}

	// 查询品类
	public static Map<String, String> queryItemCtgStr(List<String> itemList) {
		DynamicObjectCollection list = QueryServiceHelper.query("bd_materialgroupdetail",
				"material aos_itemid, group.name aos_category",
				new QFilter[] { new QFilter("standard.name", QCP.equals, "物料基本分类标准"),
						new QFilter("material.number", QCP.in, itemList) });
		return list.stream().collect(Collectors.toMap(obj -> obj.getString("aos_itemid"),
				obj -> obj.getString("aos_category"), (key1, key2) -> key2));
	}

	// 查询存货成本
	public static Map<String, BigDecimal> queryInvCost(String aos_orgid, List<String> itemFilterList) {
		DynamicObjectCollection invcostList = QueryServiceHelper.query("aos_sync_invcost", "aos_itemid,aos_item_cost",
				new QFilter[] { new QFilter("aos_orgid", QCP.equals, aos_orgid),
						new QFilter("aos_itemid.number", QCP.in, itemFilterList) },
				"aos_creation_date desc");
		return invcostList.stream().collect(Collectors.toMap(obj -> obj.getString("aos_itemid"),
				obj -> obj.getBigDecimal("aos_item_cost"), (key1, key2) -> key1));
	}

	// 查询海外库存数量
	public static Map<String, BigDecimal> queryOverSeaInStockNum(String aos_orgid, List<String> itemFilterList) {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_sync_invou_value",
				"aos_item aos_itemid,aos_instock_qty", new QFilter[] { new QFilter("aos_ou", QCP.equals, aos_orgid),
						new QFilter("aos_item.number", QCP.in, itemFilterList) });
		return list.stream().collect(Collectors.toMap(obj -> obj.getString("aos_itemid"),
				obj -> obj.getBigDecimal("aos_instock_qty"), (key1, key2) -> key1));
	}

	// 查询除亚马逊、eBay外活动日过去30天已提报或活动结束平台活动个数＞3的sku
	public static Set<String> queryApartFromAmzAndEbayItem(String aos_orgid, String[] channelArr, Date date) {

		String[] statusArr = { "B", "D" };

		Calendar instance = Calendar.getInstance();
		instance.setTime(date);
		String endDate = DateFormatUtils.format(instance.getTime(), "yyyy-MM-dd");
		instance.add(Calendar.DAY_OF_MONTH, -30);
		String startDate = DateFormatUtils.format(instance.getTime(), "yyyy-MM-dd");

		QFilter qFilter1 = new QFilter("aos_nationality", QCP.equals, aos_orgid)
				.and(new QFilter("aos_channel.number", QCP.not_in, channelArr))
				.and(new QFilter("aos_actstatus", QCP.in, statusArr))
				.and(new QFilter("aos_sal_actplanentity.aos_l_actstatus", QCP.equals, "A"));

		QFilter qFilter = new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.large_equals, startDate)
				.and(new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.less_equals, endDate))
				.or(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.large_equals, startDate)
						.and(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.less_equals, endDate)))
				.or(new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.large_equals, startDate)
						.and(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.less_equals, endDate)));
		qFilter1 = qFilter1.and(qFilter);

		DataSet dataSet = QueryServiceHelper.queryDataSet("queryApartFromAmzAndEbayItem", "aos_act_select_plan",
				"aos_sal_actplanentity.aos_itemnum.number aos_itennum, 1 as count", qFilter1.toArray(), null);
		DataSet finish = dataSet.groupBy(new String[] { "aos_itennum" }).sum("count").finish();
		dataSet.close();
		Set<String> itemSet = new HashSet<>();
		while (finish.hasNext()) {
			Row next = finish.next();
			String aos_itennum = next.getString("aos_itennum");
			Integer count = next.getInteger("count");
			if (count > 3) {
				itemSet.add(aos_itennum);
			}
		}
		return itemSet;
	}

	// 同店铺同期活动
	public static Set<String> querySamePeriodActivities(String aos_orgid, String aos_shopid, Date start, Date end) {
		Calendar instance = Calendar.getInstance();
		// 活动开始日期
		instance.setTime(start);
		String startDate = DateFormatUtils.format(instance.getTime(), "yyyy-MM-dd");

		// 活动结束日期
		instance.setTime(end);
		String endDate = DateFormatUtils.format(instance.getTime(), "yyyy-MM-dd");

		QFilter qFilter = new QFilter("aos_nationality", QCP.equals, aos_orgid)
				.and(new QFilter("aos_sal_actplanentity.aos_l_actstatus", QCP.equals, "A"));

		if (!"".equals(aos_shopid) && null != aos_shopid) {
			qFilter.and(new QFilter("aos_shop", QCP.equals, aos_shopid));
		}
		QFilter qFilter1 = new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.less_equals, endDate)
				.and(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.large_equals, startDate));
		qFilter = qFilter.and(qFilter1);
		DynamicObjectCollection list = QueryServiceHelper.query("aos_act_select_plan",
				"aos_sal_actplanentity.aos_itemnum.number aos_itennum", qFilter.toArray());
		return list.stream().map(obj -> obj.getString("aos_itennum")).collect(Collectors.toSet());
	}

	// mano过去30天销量=0；
	public static Map<String, Integer> queryZeroSalesMano(String aos_orgid, String aos_shopid) {
		Calendar instance = Calendar.getInstance();
		instance.add(Calendar.DAY_OF_MONTH, -30);
		String startDate = DateFormatUtils.format(instance.getTime(), "yyyy-MM-dd");
		List<QFilter> orderFilter = SalUtil.getOrderFilter();
		// 查询过去30天订单
		DataSet dataSet = QueryServiceHelper.queryDataSet("queryZeroSalesMano", "aos_sync_om_order_r",
				"aos_item_fid,aos_order_qty",
				new QFilter[] { new QFilter("aos_org", QCP.equals, aos_orgid),
						new QFilter("aos_order_date", QCP.large_equals, startDate),
						new QFilter("aos_shopfid", QCP.equals, aos_shopid), orderFilter.get(0) },
				null);
		DataSet finish = dataSet.groupBy(new String[] { "aos_item_fid" }).sum("aos_order_qty").finish();
		dataSet.close();
		Map<String, Integer> result = new HashMap<>();
		while (finish.hasNext()) {
			Row next = finish.next();
			result.put(next.getString("aos_item_fid"), next.getInteger("aos_order_qty"));
		}
		return result;
	}

	public static HashMap<String, Integer> GenOrderData(String aos_orgid, String aos_platformid) {
		HashMap<String, Integer> OrderData = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -30);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		QFilter filter_org = new QFilter("aos_org", "=", aos_orgid);
		QFilter filter_date_from = new QFilter("aos_order_date", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_order_date", "<=", date_to_str);
		QFilter filter_platform = new QFilter("aos_platformfid.id", "=", aos_platformid);
		List<QFilter> orderFilter = SalUtil.getOrderFilter();
		orderFilter.add(filter_org);
		orderFilter.add(filter_date_from);
		orderFilter.add(filter_date_to);
		orderFilter.add(filter_platform);
		QFilter[] filters = orderFilter.toArray(new QFilter[orderFilter.size()]);
		String SelectColumn = "aos_org aos_orgid,aos_item_fid.number aos_item,aos_order_qty";
		DataSet aos_sync_om_order_rS = QueryServiceHelper.queryDataSet("TheRange.GenOrderData", "aos_sync_om_order_r",
				SelectColumn, filters, "");
		String[] GroupBy = new String[] { "aos_orgid", "aos_item" };
		aos_sync_om_order_rS = aos_sync_om_order_rS.groupBy(GroupBy).sum("aos_order_qty").finish();
		while (aos_sync_om_order_rS.hasNext()) {
			Row aos_sync_om_order_r = aos_sync_om_order_rS.next();
			OrderData.put(aos_sync_om_order_r.getString("aos_item"), aos_sync_om_order_r.getInteger("aos_order_qty"));
		}
		aos_sync_om_order_rS.close();
		return OrderData;
	}

	// 查询物料库龄
	public static Map<String, Integer> queryItemAge(String aos_orgid) {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_sync_itemage",
				"aos_itemid.number aos_itemnum,aos_item_maxage",
				new QFilter[] { new QFilter("aos_orgid", QCP.equals, aos_orgid) });
		return list.stream().collect(Collectors.toMap(obj -> obj.getString("aos_itemnum"),
				obj -> obj.getInt("aos_item_maxage"), (k1, k2) -> k1));
	}

	// 查询最低价信息
	public static Map<String, DynamicObject> queryLowestPrice(String aos_orgid, List<String> itemNumList) {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_base_lowprz",
				"aos_itemid,aos_min_price,aos_min_price30,aos_min_price365",
				new QFilter[] { new QFilter("aos_itemid.number", QCP.in, itemNumList),
						new QFilter("aos_orgid", QCP.equals, aos_orgid) });

		return list.stream().collect(Collectors.toMap(obj -> obj.getString("aos_itemid"), obj -> obj, (k1, k2) -> k1));
	}

	// 获取自有仓库库存数量
	public static Map<String, Integer> queryPlatWarehouseQty(String aos_orgid, String aos_shopid) {
		String selectFields = "aos_item aos_itemid, aos_available_qty";
		DynamicObjectCollection list = QueryServiceHelper.query("aos_sync_invsub_value", selectFields,
				new QFilter[] { new QFilter("aos_ou", QCP.equals, aos_orgid),
						new QFilter("aos_subinv.aos_belongshop", QCP.equals, aos_shopid) });

		return list.stream().collect(Collectors.toMap(obj -> obj.getString("aos_itemid"),
				obj -> obj.getInt("aos_available_qty"), (k1, k2) -> k1));
	}

	/**
	 *
	 * @param aos_orgid
	 *            国别
	 * @param start
	 *            活动开始日期
	 * @param actType
	 *            需要剔除的活动类型
	 * @param days
	 *            前后n天
	 */
	public static Set<String> queryCullActTypeItem(String aos_orgid, Date start, String[] actType, int days) {
		// ①预计活动日前后30天，活动类型为DOTD，且状态为正常的在线ID、
		Calendar actDate30 = Calendar.getInstance();
		actDate30.setTime(start);
		actDate30.add(Calendar.DAY_OF_MONTH, -days);
		// 30天前
		String before30 = DateFormatUtils.format(actDate30.getTime(), "yyyy-MM-dd");
		// 30天后
		actDate30.add(Calendar.DAY_OF_MONTH, days * 2);
		String after30 = DateFormatUtils.format(actDate30.getTime(), "yyyy-MM-dd");
		DynamicObjectCollection beforeAfter30List = QueryServiceHelper.query("aos_act_select_plan",
				"aos_sal_actplanentity.aos_itemnum aos_itemnum",
				new QFilter[] { new QFilter("aos_nationality", QCP.equals, aos_orgid),
						new QFilter("aos_actstatus", QCP.not_equals, "C"), // 活动类型为我DOTD
						new QFilter("aos_acttype.number", QCP.in, actType), // 活动类型为我DOTD
						new QFilter("aos_sal_actplanentity.aos_l_actstatus", QCP.equals, "A"),
						new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.less_equals, after30)
								.and(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.less_equals, before30))// 始日期<=制作日期<=结束日期
				});

		return beforeAfter30List.stream().map(obj -> obj.getString("aos_itemnum")).collect(Collectors.toSet());
	}

	/**
	 * 获取通用日志表单
	 * 
	 * @param aos_type_code
	 * @param aos_orgnum
	 * @return
	 */
	public static DynamicObject getCommonLog(String aos_type_code, String aos_orgnum) {
		Calendar date1 = Calendar.getInstance();
		int year = date1.get(Calendar.YEAR);// 当前年
		int month = date1.get(Calendar.MONTH) + 1;// 当前月
		int day = date1.get(Calendar.DAY_OF_MONTH);// 当前日
		DynamicObject aos_sync_log = BusinessDataServiceHelper.newDynamicObject("aos_sync_log");
		aos_sync_log.set("aos_type_code", aos_type_code);
		aos_sync_log.set("aos_groupid", aos_orgnum + year + month + day);
		aos_sync_log.set("billstatus", "A");
		return aos_sync_log;
	}

	// 活动日前后30天已提报活动的SKU
	public static Set<String> query30DaysBeforeAndAfterActItem(String aos_orgid, Date start, int days) {
		Calendar actDate30 = Calendar.getInstance();
		actDate30.setTime(start);
		actDate30.add(Calendar.DAY_OF_MONTH, -days);
		// 30天前
		String before30 = DateFormatUtils.format(actDate30.getTime(), "yyyy-MM-dd");
		// 30天后
		actDate30.add(Calendar.DAY_OF_MONTH, days * 2);
		String after30 = DateFormatUtils.format(actDate30.getTime(), "yyyy-MM-dd");
		String selectFields = "aos_sal_actplanentity.aos_itemnum aos_itemnum";
		DynamicObjectCollection list = QueryServiceHelper.query("aos_act_select_plan", selectFields,
				new QFilter[] { new QFilter("aos_nationality", QCP.equals, aos_orgid),
						new QFilter("aos_actstatus", QCP.not_equals, "C"),
						new QFilter("aos_sal_actplanentity.aos_l_actstatus", QCP.equals, "A"),
						new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.less_equals, after30)
								.and(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.less_equals, before30))// 始日期<=制作日期<=结束日期
				});
		return list.stream().map(obj -> obj.getString("aos_itemnum")).collect(Collectors.toSet());
	}

	// 活动日前后30天已提报活动的SKU
	public static Set<String> query30DaysBeforeAndAfterActItem(String aos_orgid, String[] aos_platnum,
			String[] aos_shopnum, Date start, int days) {
		Calendar actDate30 = Calendar.getInstance();
		actDate30.setTime(start);
		actDate30.add(Calendar.DAY_OF_MONTH, -days);
		// 30天前
		String before30 = DateFormatUtils.format(actDate30.getTime(), "yyyy-MM-dd");
		// 30天后
		actDate30.add(Calendar.DAY_OF_MONTH, days * 2);
		String after30 = DateFormatUtils.format(actDate30.getTime(), "yyyy-MM-dd");
		String selectFields = "aos_sal_actplanentity.aos_itemnum aos_itemnum";

		QFilter qFilter = new QFilter("aos_nationality", QCP.equals, aos_orgid);
		qFilter.and(new QFilter("aos_actstatus", QCP.not_equals, "C"));
		qFilter.and(new QFilter("aos_sal_actplanentity.aos_l_actstatus", QCP.equals, "A"));
		qFilter.and(new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.less_equals, after30));
		qFilter.and(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.less_equals, before30));
		if (aos_shopnum != null) {
			qFilter.and(new QFilter("aos_shop.number", QCP.in, aos_shopnum));
		}

		if (aos_platnum != null) {
			qFilter.and(new QFilter("aos_channel.number", QCP.in, aos_platnum));
		}
		DynamicObjectCollection list = QueryServiceHelper.query("aos_act_select_plan", selectFields, qFilter.toArray());
		return list.stream().map(obj -> obj.getString("aos_itemnum")).collect(Collectors.toSet());
	}

	public static int holidayProToActStartDateBetweenDays(String[] dateArr, Date start) {
		// 节日品
		Calendar endDate = Calendar.getInstance();
		Calendar startDate = Calendar.getInstance();
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

			return (int) ActUtil.betweenDays(festivalEnd.getTime(), start.getTime());
		}
		return 0;
	}

	// 春夏品预计活动日可售天数≥(季末-预计活动日)
	public static int springSummerProToActStartDateBetweenDays(String aos_seasonattr, Date start) {
		Calendar seasonEnd = Calendar.getInstance();
		// 春夏品
		if ("SPRING".equals(aos_seasonattr) || "SPRING_SUMMER".equals(aos_seasonattr)
				|| "SUMMER".equals(aos_seasonattr)) {
			// 8月31
			seasonEnd.set(Calendar.MONTH, 7);
			seasonEnd.set(Calendar.DAY_OF_MONTH, 31);
			return (int) betweenDays(seasonEnd.getTime().getTime(), start.getTime());
		}
		return 0;
	}

	public static int autumnWinterProToActStartDateBetweenDays(String aos_seasonattr, Date start) {
		Calendar seasonEnd = Calendar.getInstance();
		if ("AUTUMN_WINTER".equals(aos_seasonattr) || "WINTER".equals(aos_seasonattr)) {
			// 3月31
			seasonEnd.set(Calendar.MONTH, 2);
			seasonEnd.set(Calendar.DAY_OF_MONTH, 31);
			if (start.getTime() > seasonEnd.getTime().getTime()) {
				seasonEnd.add(Calendar.YEAR, +1);
			}
			return (int) betweenDays(seasonEnd.getTime().getTime(), start.getTime());
		}
		return 0;
	}

	public static int currentToActivityDateBetweenDays(Date start) {
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
		return (int) ActUtil.betweenDays(current, actTime);
	}

	public static Map<String, BigDecimal> queryItemNewCost(String aos_orgid) {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_sync_invcost", "aos_itemid,aos_item_cost",
				new QFilter[] { new QFilter("aos_isnew", QCP.equals, true),
						new QFilter("aos_orgid", QCP.equals, aos_orgid) });

		return list.stream().collect(Collectors.toMap(obj -> obj.getString("aos_itemid"),
				obj -> obj.getBigDecimal("aos_item_cost"), (k1, k2) -> k1));
	}

	// 按活动增量倒序
	public static Map<String, List<DynamicObject>> orderByActivityIncrementDesc(
			Map<String, List<DynamicObject>> groupMap) {
		Map<String, List<DynamicObject>> groupActIncrementMap = new HashMap<>();
		// TODO: 2022/9/1
		/*
		 * for (String key : groupMap.keySet()) { }
		 */
		return groupActIncrementMap;
	}

	// 按库存金额倒序
	public static Map<String, List<DynamicObject>> orderByInvCostAmtDesc(Map<String, List<DynamicObject>> groupMap) {
		Map<String, List<DynamicObject>> groupInvCostAmtMap = new HashMap<>();
		for (String key : groupMap.keySet()) {
			List<DynamicObject> groupList = groupMap.get(key);
			List<DynamicObject> collect = groupList.stream().sorted((obj1, obj2) -> {
				BigDecimal aos_invcostamt = obj1.getBigDecimal("aos_invcostamt");
				BigDecimal aos_invcostamt1 = obj2.getBigDecimal("aos_invcostamt");
				return aos_invcostamt1.compareTo(aos_invcostamt);
			}).collect(Collectors.toList());
			groupInvCostAmtMap.put(key, collect);
		}
		return groupInvCostAmtMap;
	}

	// 填充结果
	public static Map<String, List<DynamicObject>> fillResult(Map<String, List<DynamicObject>> groupMap,
			Map<String, List<DynamicObject>> groupActIncrementMap, Map<String, List<DynamicObject>> groupInvCostAmtMap,
			Map<String, Float> rateMap, int totalQty, float incrementRate) {
		Map<String, List<DynamicObject>> resultMap = new HashMap<>();
		for (String key : groupMap.keySet()) {
			// 数量比例
			float qtyRate = rateMap.getOrDefault(key, 0f);
			// 按活动sku总数计算出当前组别的数量
			int itemQty = (int) (totalQty * qtyRate);
			// 通过当前组别数量 计算出活动增量所需sku数量
			int incrementQty = (int) (itemQty * incrementRate);
			// 先填充活动增量
			List<DynamicObject> resultList = new ArrayList<>();
			List<DynamicObject> dynamicObjectList = groupActIncrementMap.get(key);

			if (incrementQty > 0) {
				for (DynamicObject obj : dynamicObjectList) {
					if (resultList.size() < incrementQty) {
						resultList.add(obj);
					}
				}
			}

			// 不够的用库存金额补齐
			List<DynamicObject> dynamicObjectList1 = groupInvCostAmtMap.get(key);
			for (DynamicObject obj : dynamicObjectList1) {
				if (resultList.size() < totalQty) {
					resultList.add(obj);
				}
			}
			resultMap.put(key, resultList);
		}
		return resultMap;
	}

	// 计算销售组比例
	public static Map<String, Float> calGroupItemQtyRate(Map<String, List<DynamicObject>> groupMap) {
		int sum = 0;
		for (String key : groupMap.keySet()) {
			List<DynamicObject> list = groupMap.get(key);
			sum += list.size();
		}
		Map<String, Float> rateMap = new HashMap<>();
		for (String key : groupMap.keySet()) {
			List<DynamicObject> list = groupMap.get(key);
			float rate = (float) list.size() / sum;
			rateMap.put(key, rate);
		}
		return rateMap;
	}

	// 按销售组别比列分SKU
	public static Map<String, List<DynamicObject>> groupBy(String aos_orgid, List<String> itemFilterList,
			DynamicObjectCollection objectArr) {
		// 物料及其小类
		Map<String, String> itemCtgMap = ActUtil.queryItemCtg(itemFilterList);
		// 小类对应组别
		Map<String, String> ctgAndGroupMap = ActUtil.queryCtgAndGroup(aos_orgid);

		Map<String, List<DynamicObject>> groupMap = new HashMap<>();
		// 分组
		for (DynamicObject object1 : objectArr) {
			DynamicObject aos_itemnum = object1.getDynamicObject("aos_itemnum");
			String aos_itemid = aos_itemnum.getString("id");

			// 小类
			String aos_category1 = itemCtgMap.get(aos_itemid);

			// 获取组别
			String aos_groupid = ctgAndGroupMap.get(aos_category1);
			if (aos_groupid == null)
				continue;

			List<DynamicObject> groupList = groupMap.computeIfAbsent(aos_groupid, k -> new ArrayList<>());
			groupList.add(object1);
		}
		return groupMap;
	}

	public static Map<String, Integer> queryPlatformSales(String aos_orgid, String aos_platformid, int days) {
		Calendar instance = Calendar.getInstance();
		instance.add(Calendar.DAY_OF_MONTH, -days);
		String startDate = DateFormatUtils.format(instance.getTime(), "yyyy-MM-dd");
		List<QFilter> orderFilter = SalUtil.getOrderFilter();
		DataSet dataSet = QueryServiceHelper.queryDataSet("queryPlatformSales", "aos_sync_om_order_r",
				"aos_item_fid,aos_order_qty",
				new QFilter[] { new QFilter("aos_org", QCP.equals, aos_orgid),
						new QFilter("aos_order_date", QCP.large_equals, startDate),
						new QFilter("aos_platformfid", QCP.equals, aos_platformid), orderFilter.get(0) },
				null);
		DataSet finish = dataSet.groupBy(new String[] { "aos_item_fid" }).sum("aos_order_qty").finish();
		dataSet.close();
		Map<String, Integer> result = new HashMap<>();
		while (finish.hasNext()) {
			Row next = finish.next();
			result.put(next.getString("aos_item_fid"), next.getInteger("aos_order_qty"));
		}
		return result;
	}

	/**
	 * 根据国别获取活动计划清单中最新的数据
	 * 
	 * @param orgNumber
	 *            国别编码
	 * @return ActSelectDynamcobject
	 */
	public static DynamicObject queryNewestActSelectData(String orgNumber) {
		QFilter filter_org = new QFilter("billno", QFilter.like, orgNumber + "%");
		DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_actselect", "id", new QFilter[] { filter_org },
				"createtime desc", 1);
		if (dyc.size() == 0)
			return null;
		else
			return BusinessDataServiceHelper.loadSingle(dyc.get(0).getString("id"), "aos_mkt_actselect");
	}

	/**
	 * 获取物料结存海外库存数量
	 * 
	 * @param orgID
	 *            国别主键
	 * @param date
	 *            日期
	 * @param list_itemNumber
	 *            物料编码
	 */
	public static Map<String, Integer> queryBalanceOverseasInventory(Object orgID, String date,
			List<String> list_itemNumber) {
		QFilter filter_org = new QFilter("aos_ou", QFilter.equals, orgID);
		QFilter filter_date = new QFilter("aos_date", QFilter.equals, date);
		QFilter filter_item = new QFilter("aos_item.number", QFilter.in, list_itemNumber);
		StringJoiner str = new StringJoiner(",");
		str.add("aos_item.number aos_item");
		str.add("aos_qty");
		DynamicObjectCollection dyc = QueryServiceHelper.query("aos_sync_itemqty_forday", str.toString(),
				new QFilter[] { filter_org, filter_item, filter_date });
		Map<String, Integer> map_re = new HashMap<>();
		for (DynamicObject dy : dyc) {
			String item = dy.getString("aos_item");
			if (map_re.containsKey(item)) {
				int qty = map_re.get(item);
				if (dy.get("aos_qty") != null)
					qty += dy.getInt("aos_qty");
				map_re.put(item, qty);
			} else {
				if (dy.get("aos_qty") != null)
					map_re.put(item, dy.getInt("aos_qty"));
			}

		}
		return map_re;
	}

	/**
	 * 通过国别物料库存获取物料海外库存数量
	 * 
	 * @param orgID
	 *            国别主键
	 * @param list_itemNumber
	 *            物料编码
	 */
	public static Map<String, Integer> queryItemOverseasInventory(Object orgID, List<String> list_itemNumber) {
		QFilter filter_org = new QFilter("aos_ou", QFilter.equals, orgID);
		QFilter filter_item = new QFilter("aos_item.number", QFilter.in, list_itemNumber);
		StringJoiner str = new StringJoiner(",");
		str.add("aos_item.number aos_item");
		str.add("aos_instock_qty");
		DynamicObjectCollection dyc = QueryServiceHelper.query("aos_sync_invou_value", str.toString(),
				new QFilter[] { filter_org, filter_item });
		Map<String, Integer> map_re = new HashMap<>();
		for (DynamicObject dy : dyc) {
			String item = dy.getString("aos_item");
			if (map_re.containsKey(item)) {
				int qty = map_re.get(item);
				if (dy.get("aos_instock_qty") != null)
					qty += dy.getInt("aos_instock_qty");
				map_re.put(item, qty);
			} else {
				if (dy.get("aos_instock_qty") != null)
					map_re.put(item, dy.getInt("aos_instock_qty"));
			}
		}
		return map_re;
	}

	/**
	 * 获取国别活动的品类分配比例
	 * 
	 * @param orgID
	 *            主键
	 * @param actID
	 *            活动主键
	 * @return cateName to rate
	 */
	public static Map<String, BigDecimal> queryActCateAllocate(Object orgID, Object actID) {
		QFilter filter_org = new QFilter("aos_orgid", QFilter.equals, orgID);
		QFilter filter_act = new QFilter("aos_acttype", QFilter.equals, actID);
		StringJoiner str = new StringJoiner(",");
		str.add("entryentity.aos_cate.name aos_cate");
		str.add("entryentity.aos_proport aos_proport");
		DynamicObjectCollection dyc = QueryServiceHelper.query("aos_act_cate_pro", str.toString(),
				new QFilter[] { filter_org, filter_act });
		List<DynamicObject> collect = dyc.stream().sorted(Comparator.comparing(dy -> dy.getBigDecimal("aos_proport")))
				.collect(Collectors.toList());
		Map<String, BigDecimal> map_re = new LinkedHashMap<>();
		for (DynamicObject dy : collect) {
			map_re.put(dy.getString("aos_cate"), dy.getBigDecimal("aos_proport"));
		}
		return map_re;
	}

	public static Map<String, String> queryOrgShopItemASIN(Object orgid, Object shopid, List<String> list_itemid) {
		QFilter filter_org = new QFilter("aos_orgid", QFilter.equals, orgid);
		QFilter filter_shop = new QFilter("aos_shopfid", QFilter.equals, shopid);
		QFilter filter_item = new QFilter("aos_item_code", QFilter.in, list_itemid);
		QFilter filter_asin = new QFilter("aos_asin", QFilter.not_equals, "");
		QFilter[] qfs = new QFilter[] { filter_org, filter_shop, filter_item, filter_asin };
		return QueryServiceHelper.query("aos_sync_invprice", "aos_item_code,aos_asin", qfs).stream().collect(Collectors
				.toMap(dy -> dy.getString("aos_item_code"), dy -> dy.getString("aos_asin"), (key1, key2) -> key1));
	}

	/**
	 * 获取对应国别的VAT
	 *
	 * @param orgid
	 * @return
	 */
	public static BigDecimal get_VAT(Object orgid) {
		BigDecimal vat = BigDecimal.ZERO;
		QFilter filter_id = new QFilter("aos_org", "=", orgid);
		QFilter[] filters = new QFilter[] { filter_id };
		DynamicObject aos_sal_org_cal_p = QueryServiceHelper.queryOne("aos_sal_org_cal_p", "aos_vat_amount", filters);
		if (aos_sal_org_cal_p != null) {
			vat = aos_sal_org_cal_p.getBigDecimal(0);
		}
		return vat;
	}

	/**
	 * 查找实时的对美元的汇率
	 * 
	 * @param org_number
	 * @return
	 */
	public static BigDecimal get_realTimeCurrency(String org_number) {
		BigDecimal big_currency = new BigDecimal(1);
		if (org_number.equalsIgnoreCase("US"))
			return big_currency;
		// 查找美元的源码(目标币)
		QFilter qf_us = new QFilter("number", "=", "US");
		DynamicObject dy = QueryServiceHelper.queryOne("bd_country", "id,name,number,aos_curren_code.number",
				new QFilter[] { qf_us });
		String us_curren = dy.get("aos_curren_code.number").toString();
		// 查找原币的源码
		QFilter qf_org = new QFilter("number", "=", org_number);
		dy = QueryServiceHelper.queryOne("bd_country", "id,name,number,aos_curren_code.number",
				new QFilter[] { qf_org });
		String orgcur = dy.get("aos_curren_code.number").toString();

		big_currency = SalUtil.getExchangeRate(orgcur, us_curren);

		return big_currency;
	}
}
