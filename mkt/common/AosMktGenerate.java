package mkt.common;

import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

public class AosMktGenerate {

	/**
	 * 初始化店铺货号
	 * 
	 * @param p_ou_code
	 * 
	 * @return
	 */
	public static HashMap<String, String> GenerateShopSKU(Object p_ou_code) {
		HashMap<String, String> ProductInfo = new HashMap<>();
		QFilter filter_ama = new QFilter("aos_platformfid.number", "=", "AMAZON");
		QFilter filter_mainshop = new QFilter("aos_shopfid.aos_is_mainshop", "=", true);
		QFilter[] filters = new QFilter[] { filter_ama, filter_mainshop };
		String SelectColumn = "aos_item_code aos_itemid," + "aos_shopsku aos_productid";
		DynamicObjectCollection bd_materialS = QueryServiceHelper.query("aos_sync_invprice", SelectColumn, filters);
		for (DynamicObject bd_material : bd_materialS) {
			ProductInfo.put(bd_material.getString("aos_itemid"), bd_material.getString("aos_productid"));
		}
		return ProductInfo;
	}

	/**
	 * 初始化国别物料信息
	 * 
	 * @param p_ou_code
	 * 
	 * @return
	 */
	public static List<Map<String, Object>> GenerateItemQtyList(Object p_ou_code) {
		List<Map<String, Object>> ItemQtyList = new ArrayList<Map<String, Object>>();
		Map<String, Object> item_overseaqty = new HashMap<String, Object>();
		Map<String, Object> item_intransqty = new HashMap<String, Object>();
		QFilter filter_org = new QFilter("aos_ou.number", QCP.equals, p_ou_code);
		QFilter[] filters = new QFilter[] { filter_org };
		DynamicObjectCollection aos_sync_invou_valueS = QueryServiceHelper.query("aos_sync_invou_value",
				"aos_item.id aos_itemid,(aos_noplatform_qty+aos_fba_qty) as aos_instock_qty,aos_intrans_qty", filters);
		for (DynamicObject aos_sync_invou_value : aos_sync_invou_valueS) {
			item_overseaqty.put(aos_sync_invou_value.getString("aos_itemid"),
					aos_sync_invou_value.getInt("aos_instock_qty"));
			item_intransqty.put(aos_sync_invou_value.getString("aos_itemid"),
					aos_sync_invou_value.getInt("aos_intrans_qty"));
		}
		ItemQtyList.add(item_overseaqty);// 0 海外库存数量
		ItemQtyList.add(item_intransqty);// 1 在途数量
		return ItemQtyList;
	}

	/**
	 * 初始化全程天数信息
	 * 
	 * @return
	 */
	public static List<Integer> GenerateShpDay(Object p_ou_code) {
		List<Integer> MapList = new ArrayList<Integer>();
		QFilter filter_org = new QFilter("aos_org.number", QCP.equals, p_ou_code);
		QFilter[] filters = new QFilter[] { filter_org };
		DynamicObjectCollection aos_scm_fcorder_paraS = QueryServiceHelper.query("aos_scm_fcorder_para",
				"aos_shp_day,aos_clear_day,aos_freight_day", filters);
		for (DynamicObject aos_scm_fcorder_para : aos_scm_fcorder_paraS) {
			MapList.add(aos_scm_fcorder_para.getInt("aos_shp_day"));// 0 备货天数
			MapList.add(aos_scm_fcorder_para.getInt("aos_freight_day"));// 1 海运天数
			MapList.add(aos_scm_fcorder_para.getInt("aos_clear_day"));// 2 清关天数
		}
		return MapList;
	}

	/**
	 * 初始化词创建日期
	 * 
	 * @return
	 */
	public static HashMap<String, Object> GenerateLastKeyDate(Object p_ou_code) {
		HashMap<String, Object> KeyDateMap = new HashMap<>();
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter[] filters = new QFilter[] { filter_org };
		String SelectColumn = "aos_entryentity.aos_itemnumer aos_itemnumer,aos_entryentity.aos_keyword aos_keyword,"
				+ "aos_entryentity.aos_keydate aos_keydate,aos_entryentity.aos_match_type aos_match_type";
		DataSet aos_mkt_pop_ppcstS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateLastKeyDate" + p_ou_code,
				"aos_mkt_pop_ppcst", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_itemnumer", "aos_keyword", "aos_match_type" };
		aos_mkt_pop_ppcstS = aos_mkt_pop_ppcstS.groupBy(GroupBy).min("aos_keydate").finish();
		while (aos_mkt_pop_ppcstS.hasNext()) {
			Row aos_mkt_pop_ppcst = aos_mkt_pop_ppcstS.next();
			KeyDateMap.put(
					aos_mkt_pop_ppcst.getString("aos_itemnumer") + "~" + aos_mkt_pop_ppcst.getString("aos_keyword")
							+ "~" + aos_mkt_pop_ppcst.getString("aos_match_type"),
					aos_mkt_pop_ppcst.get("aos_keydate"));
		}
		aos_mkt_pop_ppcstS.close();
		return KeyDateMap;
	}

	/**
	 * 初始化组创建日期
	 * 
	 * @param p_ou_code
	 * @return
	 */
	public static HashMap<String, Object> GenerateLastGroupDate(Object p_ou_code) {
		HashMap<String, Object> GroupDateMap = new HashMap<>();
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter[] filters = new QFilter[] { filter_org };
		String SelectColumn = "aos_entryentity.aos_itemnumer aos_itemnumer,"
				+ "aos_entryentity.aos_groupdate aos_groupdate";
		DataSet aos_mkt_pop_ppcstS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateLastGroupDate" + p_ou_code,
				"aos_mkt_pop_ppcst", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_itemnumer" };
		aos_mkt_pop_ppcstS = aos_mkt_pop_ppcstS.groupBy(GroupBy).min("aos_groupdate").finish();
		while (aos_mkt_pop_ppcstS.hasNext()) {
			Row aos_mkt_pop_ppcst = aos_mkt_pop_ppcstS.next();
			GroupDateMap.put(aos_mkt_pop_ppcst.getString("aos_itemnumer"), aos_mkt_pop_ppcst.get("aos_groupdate"));
		}
		aos_mkt_pop_ppcstS.close();
		return GroupDateMap;
	}

	/**
	 * 系列创建日期
	 * 
	 * @param p_ou_code
	 * @return
	 */
	public static HashMap<String, Object> GenerateLastSerialDate(Object p_ou_code) {
		HashMap<String, Object> SerialDateMap = new HashMap<>();
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter[] filters = new QFilter[] { filter_org };
		String SelectColumn = "aos_entryentity.aos_productno aos_productno,"
				+ "aos_entryentity.aos_makedate aos_makedate";
		DataSet aos_mkt_pop_ppcstS = QueryServiceHelper.queryDataSet(
				"AosMktGenerate.GenerateLastSerialDate" + p_ou_code, "aos_mkt_pop_ppcst", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_productno" };
		aos_mkt_pop_ppcstS = aos_mkt_pop_ppcstS.groupBy(GroupBy).min("aos_makedate").finish();
		while (aos_mkt_pop_ppcstS.hasNext()) {
			Row aos_mkt_pop_ppcst = aos_mkt_pop_ppcstS.next();
			SerialDateMap.put(aos_mkt_pop_ppcst.getString("aos_productno"), aos_mkt_pop_ppcst.get("aos_makedate"));
		}
		aos_mkt_pop_ppcstS.close();
		return SerialDateMap;
	}

	public static HashMap<String, Map<String, Object>> GenerateKeyInit(Object p_ou_code) {
		HashMap<String, Map<String, Object>> KeyRpt = new HashMap<>();
		// 第零部分 ST关键词初始化表数据
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter filter_status = new QFilter("aos_status", QCP.not_equals, "paused");
		QFilter[] filters = new QFilter[] { filter_org, filter_status };
		String SelectColumn = "aos_itemnumer,aos_keyword,aos_match_type";
		DataSet aos_mkt_pop_basestS = QueryServiceHelper.queryDataSet(
				"AosMktGenerate.GeneratePpcStLastItemName" + p_ou_code, "aos_mkt_pop_basest", SelectColumn, filters,
				null);
		String[] GroupBy = new String[] { "aos_itemnumer", "aos_keyword", "aos_match_type" };
		aos_mkt_pop_basestS = aos_mkt_pop_basestS.groupBy(GroupBy).finish();
		while (aos_mkt_pop_basestS.hasNext()) {
			Row aos_mkt_pop_basest = aos_mkt_pop_basestS.next();
			String aos_itemnumer = aos_mkt_pop_basest.getString("aos_itemnumer");
			String aos_keyword = aos_mkt_pop_basest.getString("aos_keyword");
			String aos_match_type = aos_mkt_pop_basest.getString("aos_match_type");
			Map<String, Object> Info = KeyRpt.get(aos_itemnumer);
			if (Info == null)
				Info = new HashMap<>();
			Info.put(aos_keyword + "~" + aos_match_type, aos_keyword + "~" + aos_match_type);
			KeyRpt.put(aos_itemnumer, Info);
		}
		aos_mkt_pop_basestS.close();

		// 第二部分 销售手动建组界面上周数据 aos_mkt_pop_addst
		Calendar date = Calendar.getInstance();
		date.set(Calendar.HOUR_OF_DAY, 0);
		date.set(Calendar.MINUTE, 0);
		date.set(Calendar.SECOND, 0);
		date.set(Calendar.MILLISECOND, 0);
		int WeekOfYearLast = date.get(Calendar.WEEK_OF_YEAR) - 1;
		QFilter filter_week = new QFilter("aos_weekofyear", QCP.equals, WeekOfYearLast);
		filters = new QFilter[] { filter_week, filter_org };
		SelectColumn = "aos_entryentity.aos_itemid.number aos_ad_name,"
				+ "aos_entryentity.aos_subentryentity.aos_keyword aos_keyword";
		DataSet aos_mkt_pop_addstS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateKeyRpt.add" + p_ou_code,
				"aos_mkt_pop_addst", SelectColumn, filters, null);
		while (aos_mkt_pop_addstS.hasNext()) {
			Row aos_mkt_pop_addst = aos_mkt_pop_addstS.next();
			String aos_keyword = aos_mkt_pop_addst.getString("aos_keyword");
			String aos_ad_name = aos_mkt_pop_addst.getString("aos_ad_name");
			Map<String, Object> Info = KeyRpt.get(aos_ad_name);
			if (Info == null)
				Info = new HashMap<>();
			Info.put(aos_keyword + "~" + "EXACT", aos_keyword + "~" + "EXACT");
			Info.put(aos_keyword + "~" + "PHRASE", aos_keyword + "~" + "PHRASE");
			KeyRpt.put(aos_ad_name, Info);
		}
		aos_mkt_pop_addstS.close();

		// 第三部分 销售周调整表中上周数据
		QFilter filter_type = new QFilter("aos_entryentity.aos_subentryentity.aos_valid", QCP.equals, false);
		filters = new QFilter[] { /* filter_week, */ filter_org, filter_type };
		SelectColumn = "aos_entryentity.aos_itemid.number aos_ad_name,"
				+ "aos_entryentity.aos_subentryentity.aos_keyword aos_keyword,"
				+ "aos_entryentity.aos_subentryentity.aos_match_type aos_match_type";
		DataSet aos_mkt_pop_weekstS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateKeyRpt.week" + p_ou_code,
				"aos_mkt_pop_weekst", SelectColumn, filters, null);
		while (aos_mkt_pop_weekstS.hasNext()) {
			Row aos_mkt_pop_weekst = aos_mkt_pop_weekstS.next();
			Map<String, Object> Info = KeyRpt.get(aos_mkt_pop_weekst.getString("aos_ad_name"));
			if (Info == null)
				Info = new HashMap<>();
			Info.put(aos_mkt_pop_weekst.getString("aos_keyword") + "~" + aos_mkt_pop_weekst.getString("aos_match_type"),
					aos_mkt_pop_weekst.getString("aos_keyword") + "~" + aos_mkt_pop_weekst.getString("aos_match_type"));
			KeyRpt.put(aos_mkt_pop_weekst.getString("aos_ad_name"), Info);
		}
		aos_mkt_pop_weekstS.close();

		return KeyRpt;
	}

	/**
	 * 初始化组对应关键词+匹配方式 存在四个来源
	 * 
	 * @param p_ou_code
	 * @return
	 */
	public static HashMap<String, Map<String, Object>> GenerateKeyRpt(Object p_ou_code) {
		HashMap<String, Map<String, Object>> KeyRpt = new HashMap<>();
		// 第一部分 关键词报告
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -1);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<=", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", date_to_str);
		QFilter filter_match = new QFilter("aos_entryentity.aos_cam_name", "like", "%-KW%");
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_match, filter_org };
		String SelectColumn = "aos_entryentity.aos_ad_name aos_ad_name,aos_entryentity.aos_targeting aos_targeting,"
				+ "aos_entryentity.aos_match_type aos_match_type";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateKeyRpt" + p_ou_code,
				"aos_base_pointrpt", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_ad_name", "aos_targeting", "aos_match_type" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			Map<String, Object> Info = KeyRpt.get(aos_base_skupoprpt.getString("aos_ad_name"));
			if (Info == null)
				Info = new HashMap<>();
			String key = aos_base_skupoprpt.getString("aos_targeting") + "~"
					+ aos_base_skupoprpt.getString("aos_match_type");
			Info.put(key, key);
			KeyRpt.put(aos_base_skupoprpt.getString("aos_ad_name"), Info);
		}
		aos_base_skupoprptS.close();
		// 第二部分 销售手动建组界面上周数据
		Calendar date = Calendar.getInstance();
		date.set(Calendar.HOUR_OF_DAY, 0);
		date.set(Calendar.MINUTE, 0);
		date.set(Calendar.SECOND, 0);
		date.set(Calendar.MILLISECOND, 0);
		int WeekOfYearLast = date.get(Calendar.WEEK_OF_YEAR) - 1;
		QFilter filter_week = new QFilter("aos_weekofyear", QCP.equals, WeekOfYearLast);
		QFilter filter_status = new QFilter("aos_entryentity.aos_subentryentity.aos_keystatus", QCP.equals, "ADD");// 新增在线
		filters = new QFilter[] { filter_week, filter_org, filter_status };
		SelectColumn = "aos_entryentity.aos_itemid.number aos_ad_name,"
				+ "aos_entryentity.aos_subentryentity.aos_keyword aos_keyword,"
				+ "aos_entryentity.aos_subentryentity.aos_keytype aos_keytype";
		DataSet aos_mkt_pop_addstS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateKeyRpt.add" + p_ou_code,
				"aos_mkt_pop_addst", SelectColumn, filters, null);
		while (aos_mkt_pop_addstS.hasNext()) {
			Row aos_mkt_pop_addst = aos_mkt_pop_addstS.next();
			String aos_keyword = aos_mkt_pop_addst.getString("aos_keyword");
			String aos_ad_name = aos_mkt_pop_addst.getString("aos_ad_name");
			String aos_keytype = aos_mkt_pop_addst.getString("aos_keytype");
			Map<String, Object> Info = KeyRpt.get(aos_ad_name);
			if (Info == null)
				Info = new HashMap<>();
			Info.put(aos_keyword + "~" + aos_keytype, aos_keyword + "~" + aos_keytype);
			KeyRpt.put(aos_ad_name, Info);
		}
		aos_mkt_pop_addstS.close();
		// 第三部分 销售周调整表中上周数据
		QFilter filter_type = new QFilter("aos_entryentity.aos_subentryentity.aos_valid", QCP.equals, false);
		filters = new QFilter[] { /* filter_week, */ filter_org, filter_type };
		SelectColumn = "aos_entryentity.aos_itemid.number aos_ad_name,"
				+ "aos_entryentity.aos_subentryentity.aos_keyword aos_keyword,"
				+ "aos_entryentity.aos_subentryentity.aos_match_type aos_match_type";
		DataSet aos_mkt_pop_weekstS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateKeyRpt.week" + p_ou_code,
				"aos_mkt_pop_weekst", SelectColumn, filters, null);
		while (aos_mkt_pop_weekstS.hasNext()) {
			Row aos_mkt_pop_weekst = aos_mkt_pop_weekstS.next();
			Map<String, Object> Info = KeyRpt.get(aos_mkt_pop_weekst.getString("aos_ad_name"));
			if (Info == null)
				Info = new HashMap<>();
			Info.put(aos_mkt_pop_weekst.getString("aos_keyword") + "~" + aos_mkt_pop_weekst.getString("aos_match_type"),
					aos_mkt_pop_weekst.getString("aos_keyword") + "~" + aos_mkt_pop_weekst.getString("aos_match_type"));
			KeyRpt.put(aos_mkt_pop_weekst.getString("aos_ad_name"), Info);
		}
		aos_mkt_pop_weekstS.close();
		// 第四部分 ST关键词初始化表数据
		DynamicObjectCollection aos_mkt_pop_basestS = QueryServiceHelper.query("aos_mkt_pop_basest",
				"aos_itemnumer,aos_keyword,aos_match_type", filter_org.toArray());
		for (DynamicObject aos_mkt_pop_basest : aos_mkt_pop_basestS) {
			Map<String, Object> Info = KeyRpt.computeIfAbsent(aos_mkt_pop_basest.getString("aos_itemnumer"),
					k -> new HashMap<>());
			Info.put(aos_mkt_pop_basest.getString("aos_keyword") + "~" + aos_mkt_pop_basest.getString("aos_match_type"),
					aos_mkt_pop_basest.getString("aos_keyword") + "~" + aos_mkt_pop_basest.getString("aos_match_type"));
			KeyRpt.put(aos_mkt_pop_basest.getString("aos_itemnumer"), Info);
		}
		return KeyRpt;
	}

	public static HashMap<String, Integer> GenerateOrderMonth(Object p_ou_code) {
		HashMap<String, Integer> OrderMonth = new HashMap<>();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		Calendar today = Calendar.getInstance();
		today.set(Calendar.HOUR_OF_DAY, 0);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.SECOND, 0);
		today.set(Calendar.MILLISECOND, 0);
		today.add(Calendar.DAY_OF_MONTH, -15);
		QFilter filter_org = new QFilter("aos_org.number", QCP.equals, p_ou_code);
		QFilter filter_date = new QFilter("aos_order_date", QCP.large_equals, sdf.format(today.getTime()));
		List<QFilter> orderFilter = SalUtil.getOrderFilter();
		orderFilter.add(filter_org);
		orderFilter.add(filter_date);
		QFilter[] filters = orderFilter.toArray(new QFilter[orderFilter.size()]);

		DataSet orderDataSet = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateOrderMonth" + p_ou_code,
				"aos_sync_om_order_r", "aos_org,aos_item_fid,aos_order_qty", filters, null);
		orderDataSet = orderDataSet.groupBy(new String[] { "aos_org", "aos_item_fid" }).sum("aos_order_qty").finish();
		while (orderDataSet.hasNext()) {
			Row aos_sync_omonth_summary = orderDataSet.next();
			OrderMonth.put(aos_sync_omonth_summary.getString("aos_item_fid"),
					aos_sync_omonth_summary.getInteger("aos_order_qty"));
		}
		orderDataSet.close();
		return OrderMonth;
	}

	public static HashMap<String, Integer> GenerateOrder7Days(Object p_ou_code) {
		HashMap<String, Integer> OrderMonth = new HashMap<>();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		Calendar today = Calendar.getInstance();
		today.set(Calendar.HOUR_OF_DAY, 0);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.SECOND, 0);
		today.set(Calendar.MILLISECOND, 0);
		today.add(Calendar.DAY_OF_MONTH, -7);
		QFilter filter_org = new QFilter("aos_org.number", QCP.equals, p_ou_code);
		QFilter filter_date = new QFilter("aos_order_date", QCP.large_equals, sdf.format(today.getTime()));
		List<QFilter> orderFilter = SalUtil.getOrderFilter();
		orderFilter.add(filter_org);
		orderFilter.add(filter_date);
		QFilter[] filters = orderFilter.toArray(new QFilter[orderFilter.size()]);
		DataSet orderDataSet = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateOrder7Days" + p_ou_code,
				"aos_sync_om_order_r", "aos_org,aos_item_fid,aos_order_qty", filters, null);
		orderDataSet = orderDataSet.groupBy(new String[] { "aos_org", "aos_item_fid" }).sum("aos_order_qty").finish();
		while (orderDataSet.hasNext()) {
			Row aos_sync_omonth_summary = orderDataSet.next();
			OrderMonth.put(aos_sync_omonth_summary.getString("aos_item_fid"),
					aos_sync_omonth_summary.getInteger("aos_order_qty"));
		}
		orderDataSet.close();
		return OrderMonth;
	}

	public static HashMap<String, Object> GeneratePpcStLast(Object p_ou_code) {
		HashMap<String, Object> PpcYester = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -1);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter filter_date_from = new QFilter("aos_date", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_date", "<", date_to_str);
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_org };
		String SelectColumn = "aos_entryentity.aos_itemid aos_itemid";
		DataSet aos_mkt_pop_ppcstS = QueryServiceHelper.queryDataSet("AosMktGenerate.GeneratePpcStLast" + p_ou_code,
				"aos_mkt_pop_ppcst", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_itemid" };
		aos_mkt_pop_ppcstS = aos_mkt_pop_ppcstS.groupBy(GroupBy).finish();
		while (aos_mkt_pop_ppcstS.hasNext()) {
			Row aos_mkt_pop_ppcst = aos_mkt_pop_ppcstS.next();
			PpcYester.put(aos_mkt_pop_ppcst.getString("aos_itemid"), aos_mkt_pop_ppcst.get("aos_itemid"));
		}
		aos_mkt_pop_ppcstS.close();
		return PpcYester;
	}

	public static HashMap<String, Map<String, Object>> GeneratePpcStLastItemName(Object p_ou_code) {
		HashMap<String, Map<String, Object>> PpcYester = new HashMap<>();
		HashMap<String, Map<String, Object>> StBase = AosMktGenerate.GenerateStBase(p_ou_code);
		// 国别物料
		QFilter filter_ou = new QFilter("aos_contryentry.aos_nationality.number", "=", p_ou_code);
		QFilter filter_type = new QFilter("aos_protype", "=", "N");
		QFilter[] filters = new QFilter[] { filter_ou, filter_type };
		String SelectField = "number,aos_cn_name,id";
		DynamicObjectCollection bd_materialS = QueryServiceHelper.query("bd_material", SelectField, filters,
				"aos_productno");
		for (DynamicObject bd_material : bd_materialS) {
			String aos_itemname = bd_material.getString("aos_cn_name");
			String aos_itemnumer = bd_material.getString("number");
			String aos_itemid = bd_material.getString("id");
			if (StBase.get(aos_itemnumer) != null) {
				Map<String, Object> StBaseDetail = StBase.get(aos_itemnumer);
				if (StBaseDetail != null) {
					for (String aos_targetcomb : StBaseDetail.keySet()) {
						Map<String, Object> Info = PpcYester.get(aos_itemname);
						if (Info == null)
							Info = new HashMap<>();
						Info.put(aos_targetcomb, aos_itemid);
						PpcYester.put(aos_itemname, Info);
					}
				}
			}
		}

		return PpcYester;
	}

	public static HashMap<String, Map<String, Object>> GeneratePopOrgInfo(Object p_ou_code) {
		HashMap<String, Map<String, Object>> PopOrgInfo = new HashMap<>();
		String SelectColumn = "aos_orgid," + "aos_type," + "aos_value,aos_condition1";
		DataSet aos_mkt_base_orgvalueS = QueryServiceHelper.queryDataSet(
				"AosMktGenerate.GeneratePopOrgInfo" + p_ou_code, "aos_mkt_base_orgvalue", SelectColumn, null, null);
		while (aos_mkt_base_orgvalueS.hasNext()) {
			Row aos_mkt_base_orgvalue = aos_mkt_base_orgvalueS.next();
			HashMap<String, Object> Info = new HashMap<>();
			Info.put("aos_value", aos_mkt_base_orgvalue.get("aos_value"));
			Info.put("aos_condition1", aos_mkt_base_orgvalue.get("aos_condition1"));
			PopOrgInfo.put(aos_mkt_base_orgvalue.getLong("aos_orgid") + "~" + aos_mkt_base_orgvalue.get("aos_type"),
					Info);
		}
		aos_mkt_base_orgvalueS.close();
		return PopOrgInfo;
	}

	/**
	 * 7日关键词报告数据 曝光汇总
	 * 
	 * @param p_ou_code
	 * @return
	 */
	public static HashMap<String, BigDecimal> GenerateKeyRpt7GroupExp(Object p_ou_code) {
		HashMap<String, BigDecimal> KeyRpt = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -7);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<=", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", date_to_str);
		QFilter filter_match = new QFilter("aos_entryentity.aos_cam_name", "like", "%-KW%");
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_match, filter_org };
		String SelectColumn = "aos_entryentity.aos_ad_name aos_ad_name,"
				+ "aos_entryentity.aos_impressions aos_impressions";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateKeyRpt",
				"aos_base_pointrpt", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_ad_name" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).sum("aos_impressions").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			KeyRpt.put(aos_base_skupoprpt.getString("aos_ad_name"),
					aos_base_skupoprpt.getBigDecimal("aos_impressions"));
		}
		aos_base_skupoprptS.close();
		return KeyRpt;
	}

	public static HashMap<String, Map<String, Object>> GeneratePpcStTodayKeyStatus(Object p_ou_code) {
		HashMap<String, Map<String, Object>> PpcToday = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date Today = calendar.getTime();
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter filter_date = new QFilter("aos_date", "=", Today);
		QFilter[] filters = new QFilter[] { filter_date, filter_org };
		String SelectColumn = "aos_entryentity.aos_itemid aos_itemid," + "aos_entryentity.aos_keyword aos_keyword,"
				+ "aos_entryentity.aos_match_type aos_match_type," + "aos_entryentity.aos_keystatus aos_keystatus ";
		DataSet aos_mkt_pop_ppcstS = QueryServiceHelper.queryDataSet(
				"AosMktGenerate.GeneratePpcStTodayKeyStatus" + p_ou_code, "aos_mkt_pop_ppcst", SelectColumn, filters,
				null);
		while (aos_mkt_pop_ppcstS.hasNext()) {
			Row aos_mkt_pop_ppcst = aos_mkt_pop_ppcstS.next();
			String aos_itemid = aos_mkt_pop_ppcst.getLong("aos_itemid") + "";
			Map<String, Object> Info = PpcToday.get(aos_itemid);
			if (Info == null)
				Info = new HashMap<>();
			Info.put(aos_mkt_pop_ppcst.getString("aos_keyword") + "~" + aos_mkt_pop_ppcst.getString("aos_match_type"),
					aos_mkt_pop_ppcst.getString("aos_keystatus"));
			PpcToday.put(aos_itemid, Info);
		}
		aos_mkt_pop_ppcstS.close();
		return PpcToday;
	}

	public static HashMap<String, Map<String, Object>> GenerateKeyRpt7(Object p_ou_code) {
		HashMap<String, Map<String, Object>> KeyRpt = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -7);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<=", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", date_to_str);
		QFilter filter_match = new QFilter("aos_entryentity.aos_cam_name", "like", "%-KW%");
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_match, filter_org };
		String SelectColumn = "aos_entryentity.aos_ad_name aos_ad_name,aos_entryentity.aos_targeting aos_targeting,"
				+ "aos_entryentity.aos_match_type aos_match_type," + "aos_entryentity.aos_clicks aos_clicks,"
				+ "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_impressions aos_impressions,"
				+ "aos_entryentity.aos_sales aos_sales";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateKeyRpt",
				"aos_base_pointrpt", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_ad_name", "aos_targeting", "aos_match_type" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).sum("aos_clicks").sum("aos_spend")
				.sum("aos_impressions").sum("aos_sales").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			String key = aos_base_skupoprpt.getString("aos_ad_name") + "~"
					+ aos_base_skupoprpt.getString("aos_targeting") + "~"
					+ aos_base_skupoprpt.getString("aos_match_type");
			Map<String, Object> Info = KeyRpt.get(key);
			if (Info == null)
				Info = new HashMap<>();
			BigDecimal aos_roi = BigDecimal.ZERO;
			if (aos_base_skupoprpt.get("aos_spend") != null
					&& ((BigDecimal) aos_base_skupoprpt.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
				aos_roi = ((BigDecimal) aos_base_skupoprpt.get("aos_sales"))
						.divide((BigDecimal) aos_base_skupoprpt.get("aos_spend"), 6, BigDecimal.ROUND_HALF_UP);
			}

			Info.put("aos_clicks", aos_base_skupoprpt.getBigDecimal("aos_clicks"));
			Info.put("aos_spend", aos_base_skupoprpt.getBigDecimal("aos_spend"));
			Info.put("aos_impressions", aos_base_skupoprpt.getBigDecimal("aos_impressions"));
			Info.put("aos_roi", aos_roi);
			KeyRpt.put(key, Info);
		}
		aos_base_skupoprptS.close();
		return KeyRpt;
	}

	public static HashMap<String, Map<String, Object>> GenerateKeyRpt1(Object p_ou_code) {
		HashMap<String, Map<String, Object>> KeyRpt = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -1);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<=", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", date_to_str);
		QFilter filter_match = new QFilter("aos_entryentity.aos_cam_name", "like", "%-KW%");
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_match, filter_org };
		String SelectColumn = "aos_entryentity.aos_ad_name aos_ad_name,aos_entryentity.aos_targeting aos_targeting,"
				+ "aos_entryentity.aos_match_type aos_match_type," + "aos_entryentity.aos_clicks aos_clicks,"
				+ "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_impressions aos_impressions,"
				+ "aos_entryentity.aos_sales aos_sales";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateKeyRpt",
				"aos_base_pointrpt", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_ad_name", "aos_targeting", "aos_match_type" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).sum("aos_clicks").sum("aos_spend")
				.sum("aos_impressions").sum("aos_sales").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			String key = aos_base_skupoprpt.getString("aos_ad_name") + "~"
					+ aos_base_skupoprpt.getString("aos_targeting") + "~"
					+ aos_base_skupoprpt.getString("aos_match_type");
			Map<String, Object> Info = KeyRpt.get(key);
			if (Info == null)
				Info = new HashMap<>();
			BigDecimal aos_roi = BigDecimal.ZERO;
			if (aos_base_skupoprpt.get("aos_spend") != null
					&& ((BigDecimal) aos_base_skupoprpt.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
				aos_roi = ((BigDecimal) aos_base_skupoprpt.get("aos_sales"))
						.divide((BigDecimal) aos_base_skupoprpt.get("aos_spend"), 6, BigDecimal.ROUND_HALF_UP);
			}
			Info.put("aos_clicks", aos_base_skupoprpt.getBigDecimal("aos_clicks"));
			Info.put("aos_spend", aos_base_skupoprpt.getBigDecimal("aos_spend"));
			Info.put("aos_impressions", aos_base_skupoprpt.getBigDecimal("aos_impressions"));
			Info.put("aos_roi", aos_roi);
			KeyRpt.put(key, Info);
		}
		aos_base_skupoprptS.close();
		return KeyRpt;
	}

	public static HashMap<String, Map<String, Object>> GenerateAdvPrice(Object p_ou_code) {
		HashMap<String, Map<String, Object>> AdPrice = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date aos_date = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String aos_date_str = writeFormat.format(aos_date);
		QFilter filter_org = new QFilter("aos_entryentity.aos_orgid.number", QCP.equals, p_ou_code);
		QFilter filter_date = new QFilter("aos_date", "=", aos_date_str);
		QFilter[] filters = new QFilter[] { filter_date, filter_org };
		String SelectColumn = "aos_entryentity.aos_orgid aos_orgid," + "aos_entryentity.aos_ad_name aos_ad_name,"
				+ "aos_entryentity.aos_bid_suggest aos_bid_suggest,"
				+ "aos_entryentity.aos_bid_rangestart aos_bid_rangestart,"
				+ "aos_entryentity.aos_bid_rangeend aos_bid_rangeend";
		DataSet aos_base_advrptS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateAdvPrice" + p_ou_code,
				"aos_base_advrpt", SelectColumn, filters, null);
		while (aos_base_advrptS.hasNext()) {
			Row aos_base_advrpt = aos_base_advrptS.next();
			Map<String, Object> Info = new HashMap<>();
			Info.put("aos_bid_suggest", aos_base_advrpt.get("aos_bid_suggest"));
			Info.put("aos_bid_rangestart", aos_base_advrpt.get("aos_bid_rangestart"));
			Info.put("aos_bid_rangeend", aos_base_advrpt.get("aos_bid_rangeend"));
			AdPrice.put(aos_base_advrpt.getString("aos_ad_name"), Info);
		}
		aos_base_advrptS.close();
		return AdPrice;
	}

	public static HashMap<String, Map<String, Object>> GenerateAdvKeyPrice(Object p_ou_code) {
		HashMap<String, Map<String, Object>> AdPrice = new HashMap<>();
		QFilter filter_org = new QFilter("entryentity.aos_orgid.number", QCP.equals, p_ou_code);
		QFilter[] filters = new QFilter[] { filter_org };
		String SelectColumn = "entryentity.aos_orgid aos_orgid," + "entryentity.aos_ad_name aos_ad_name,"
				+ "entryentity.aos_keyword aos_keyword," + "entryentity.aos_match_type aos_match_type,"
				+ "entryentity.aos_bid_suggest aos_bid_suggest," + "entryentity.aos_bid_rangestart aos_bid_rangestart,"
				+ "entryentity.aos_bid_rangeend aos_bid_rangeend";
		DataSet aos_base_advrptS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateAdvKeyPrice" + p_ou_code,
				"aos_base_pointadv", SelectColumn, filters, null);
		while (aos_base_advrptS.hasNext()) {
			Row aos_base_advrpt = aos_base_advrptS.next();
			Map<String, Object> Info = new HashMap<>();
			String key = aos_base_advrpt.getString("aos_ad_name") + "~" + aos_base_advrpt.getString("aos_keyword") + "~"
					+ aos_base_advrpt.getString("aos_match_type");
			Info.put("aos_bid_suggest", aos_base_advrpt.get("aos_bid_suggest"));
			Info.put("aos_bid_rangestart", aos_base_advrpt.get("aos_bid_rangestart"));
			Info.put("aos_bid_rangeend", aos_base_advrpt.get("aos_bid_rangeend"));
			AdPrice.put(key, Info);
		}
		aos_base_advrptS.close();
		return AdPrice;
	}

	public static HashMap<String, Map<String, Object>> GenerateAdvKeyPrice14(Object p_ou_code) {
		HashMap<String, Map<String, Object>> KeyRpt14 = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -14);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter filter_date_from = new QFilter("aos_date", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_date", "<=", date_to_str);

		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_org };
		String SelectColumn = "aos_date ," + "entryentity.aos_ad_name aos_ad_name,"
				+ "entryentity.aos_keyword aos_keyword," + "entryentity.aos_match_type aos_match_type,"
				+ "entryentity.aos_bid_suggest aos_bid_suggest";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet(
				"AosMktGenerate.GenerateAdvKeyPrice14" + p_ou_code, "aos_base_pointadv", SelectColumn, filters, null);
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			String key = aos_base_skupoprpt.getString("aos_ad_name") + "~" + aos_base_skupoprpt.getString("aos_keyword")
					+ "~" + aos_base_skupoprpt.getString("aos_match_type");
			Date aos_date_l = aos_base_skupoprpt.getDate("aos_date");
			String aos_date_str = DF.format(aos_date_l);
			Map<String, Object> Info = KeyRpt14.get(key);
			if (Info == null)
				Info = new HashMap<>();
			Info.put(aos_date_str, aos_base_skupoprpt.get("aos_bid_suggest"));
			KeyRpt14.put(key, Info);
		}
		aos_base_skupoprptS.close();
		return KeyRpt14;
	}

	public static HashMap<String, Object> GenerateDailyPrice(Object p_ou_code) {
		HashMap<String, Object> DailyPrice = new HashMap<>();
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter[] filters = new QFilter[] { filter_org };
		String SelectColumn = "aos_orgid," + "aos_item_code aos_itemid," + "aos_currentprice";
		DynamicObjectCollection aos_sync_invpriceS = QueryServiceHelper.query("aos_sync_invprice", SelectColumn,
				filters);
		for (DynamicObject aos_sync_invprice : aos_sync_invpriceS) {
			DailyPrice.put(aos_sync_invprice.getString("aos_itemid"), aos_sync_invprice.get("aos_currentprice"));
		}
		return DailyPrice;
	}

	public static HashMap<String, Object> GenerateAvaDays(Object p_ou_code) {
		HashMap<String, Object> ItemAvaDays = new HashMap<>();
		QFilter filter_org = new QFilter("aos_ou_code", QCP.equals, p_ou_code);
		QFilter[] filters = new QFilter[] { filter_org };
		String SelectColumn = "aos_ou_code," + "aos_item_code," + "aos_7avadays";
		DynamicObjectCollection aos_sal_dw_invavadayS = QueryServiceHelper.query("aos_sal_dw_invavadays", SelectColumn,
				filters);
		for (DynamicObject aos_sal_dw_invavaday : aos_sal_dw_invavadayS) {
			ItemAvaDays.put(aos_sal_dw_invavaday.getString("aos_item_code"), aos_sal_dw_invavaday.get("aos_7avadays"));
		}
		return ItemAvaDays;
	}

	public static HashMap<String, Map<String, Object>> GeneratePpcYesterSt(Object p_ou_code) {
		HashMap<String, Map<String, Object>> PpcYester = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -1);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		QFilter filter_date_from = new QFilter("aos_date", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_date", "<", date_to_str);
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_org };
		String SelectColumn = "aos_entryentity.aos_itemnumer aos_itemnumer,"
				+ "aos_entryentity.aos_keyword aos_keyword," + "aos_entryentity.aos_match_type aos_match_type,"
				+ "aos_entryentity.aos_budget aos_budget," + "aos_entryentity.aos_bid aos_bid,"
				+ "aos_entryentity.aos_lastpricedate aos_lastpricedate";
		DataSet aos_mkt_popular_ppcS = QueryServiceHelper.queryDataSet("AosMktGenerate.GeneratePpcYesterSt" + p_ou_code,
				"aos_mkt_pop_ppcst", SelectColumn, filters, null);
		while (aos_mkt_popular_ppcS.hasNext()) {
			Row aos_mkt_popular_ppc = aos_mkt_popular_ppcS.next();
			HashMap<String, Object> Info = new HashMap<>();
			String key = aos_mkt_popular_ppc.getString("aos_itemnumer") + "~"
					+ aos_mkt_popular_ppc.getString("aos_keyword") + "~"
					+ aos_mkt_popular_ppc.getString("aos_match_type");
			Info.put("aos_budget", aos_mkt_popular_ppc.get("aos_budget"));// 上次预算
			Info.put("aos_bid", aos_mkt_popular_ppc.get("aos_bid"));// 上次出价
			Info.put("aos_lastpricedate", aos_mkt_popular_ppc.get("aos_lastpricedate"));
			PpcYester.put(key, Info);
		}
		aos_mkt_popular_ppcS.close();
		return PpcYester;
	}

	public static HashMap<String, Map<String, Object>> GeneratePpcTodaySt(Object p_ou_code) {
		HashMap<String, Map<String, Object>> PpcToday = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date Today = calendar.getTime();
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter filter_date = new QFilter("aos_date", "=", Today);
		QFilter[] filters = new QFilter[] { filter_date, filter_org };
		String SelectColumn = "aos_entryentity.aos_itemnumer aos_itemnumer,"
				+ "aos_entryentity.aos_keyword aos_keyword," + "aos_entryentity.aos_match_type aos_match_type,"
				+ "aos_entryentity.aos_budget aos_budget," + "aos_entryentity.aos_bid aos_bid,"
				+ "aos_entryentity.aos_keystatus aos_keystatus";
		DataSet aos_mkt_popular_ppcS = QueryServiceHelper.queryDataSet("AosMktGenerate.GeneratePpcTodaySt" + p_ou_code,
				"aos_mkt_pop_ppcst", SelectColumn, filters, null);
		while (aos_mkt_popular_ppcS.hasNext()) {
			Row aos_mkt_popular_ppc = aos_mkt_popular_ppcS.next();
			HashMap<String, Object> Info = new HashMap<>();
			String key = aos_mkt_popular_ppc.getString("aos_itemnumer") + "~"
					+ aos_mkt_popular_ppc.getString("aos_keyword") + "~"
					+ aos_mkt_popular_ppc.getString("aos_match_type");
			Info.put("aos_bid", aos_mkt_popular_ppc.get("aos_bid"));// 今日出价
			Info.put("aos_keystatus", aos_mkt_popular_ppc.get("aos_keystatus"));// 剔词
			PpcToday.put(key, Info);
		}
		aos_mkt_popular_ppcS.close();
		return PpcToday;
	}

	public static HashMap<String, Object> GeneratePpcTodayStGroup(Object p_ou_code) {
		HashMap<String, Object> PpcToday = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date Today = calendar.getTime();
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter filter_date = new QFilter("aos_date", "=", Today);
		QFilter[] filters = new QFilter[] { filter_date, filter_org };
		String SelectColumn = "aos_entryentity.aos_itemnumer aos_itemnumer," + "1 aos_count";
		DataSet aos_mkt_popular_ppcS = QueryServiceHelper.queryDataSet(
				"AosMktGenerate.GeneratePpcTodayStGroup" + p_ou_code, "aos_mkt_pop_ppcst", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_itemnumer" };
		aos_mkt_popular_ppcS = aos_mkt_popular_ppcS.groupBy(GroupBy).sum("aos_count").finish();
		while (aos_mkt_popular_ppcS.hasNext()) {
			Row aos_mkt_popular_ppc = aos_mkt_popular_ppcS.next();
			String aos_itemnumer = aos_mkt_popular_ppc.getString("aos_itemnumer");
			int aos_count = aos_mkt_popular_ppc.getInteger("aos_count");
			PpcToday.put(aos_itemnumer, aos_count);
		}
		aos_mkt_popular_ppcS.close();
		return PpcToday;
	}

	public static HashMap<String, Object> GeneratePpcTodayStSerialGroup(Object p_ou_code) {
		HashMap<String, Object> PpcToday = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date Today = calendar.getTime();
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter filter_date = new QFilter("aos_date", "=", Today);
		QFilter[] filters = new QFilter[] { filter_date, filter_org };
		String SelectColumn = "aos_entryentity.aos_productno aos_productno ,"
				+ "aos_entryentity.aos_itemnumer aos_itemnumer";
		DataSet aos_mkt_popular_ppcS = QueryServiceHelper.queryDataSet(
				"AosMktGenerate.GeneratePpcTodayStSerialGroup" + p_ou_code, "aos_mkt_pop_ppcst", SelectColumn, filters,
				null);
		String[] GroupBy = new String[] { "aos_productno", "aos_itemnumer" };
		aos_mkt_popular_ppcS = aos_mkt_popular_ppcS.groupBy(GroupBy).finish();
		while (aos_mkt_popular_ppcS.hasNext()) {
			Row aos_mkt_popular_ppc = aos_mkt_popular_ppcS.next();
			String aos_productno = aos_mkt_popular_ppc.getString("aos_productno");
			if (PpcToday.get(aos_productno) == null)
				PpcToday.put(aos_productno, 1);
			else
				PpcToday.put(aos_productno, (int) PpcToday.get(aos_productno) + 1);
		}
		aos_mkt_popular_ppcS.close();
		return PpcToday;
	}

	/**
	 * 今日系列维度关键词个数
	 * 
	 * @param p_ou_code
	 * @return
	 */
	public static HashMap<String, Object> GeneratePpcTodayStSerial(Object p_ou_code) {
		HashMap<String, Object> PpcToday = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date Today = calendar.getTime();
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter filter_date = new QFilter("aos_date", "=", Today);
		QFilter[] filters = new QFilter[] { filter_date, filter_org };
		String SelectColumn = "aos_entryentity.aos_productno aos_productno," + "1 aos_count";
		DataSet aos_mkt_popular_ppcS = QueryServiceHelper.queryDataSet(
				"AosMktGenerate.GeneratePpcTodayStSerial" + p_ou_code, "aos_mkt_pop_ppcst", SelectColumn, filters,
				null);
		String[] GroupBy = new String[] { "aos_productno" };
		aos_mkt_popular_ppcS = aos_mkt_popular_ppcS.groupBy(GroupBy).sum("aos_count").finish();
		while (aos_mkt_popular_ppcS.hasNext()) {
			Row aos_mkt_popular_ppc = aos_mkt_popular_ppcS.next();
			String aos_productno = aos_mkt_popular_ppc.getString("aos_productno");
			int aos_count = aos_mkt_popular_ppc.getInteger("aos_count");
			PpcToday.put(aos_productno, aos_count);
		}
		aos_mkt_popular_ppcS.close();
		return PpcToday;
	}

	/**
	 * ST数据源昨日系列预算
	 * 
	 * @param p_ou_code
	 * @return
	 */
	public static HashMap<String, Object> GeneratePpcYeStSerial(Object p_ou_code) {
		HashMap<String, Object> PpcToday = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -1);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		QFilter filter_date_from = new QFilter("aos_date", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_date", "<", date_to_str);
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_org };
		String SelectColumn = "aos_entryentity.aos_productno aos_productno," + "aos_entryentity.aos_budget aos_budget";
		DataSet aos_mkt_popular_ppcS = QueryServiceHelper.queryDataSet(
				"AosMktGenerate.GeneratePpcYeStSerial" + p_ou_code, "aos_mkt_pop_ppcst", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_productno", "aos_budget" };
		aos_mkt_popular_ppcS = aos_mkt_popular_ppcS.groupBy(GroupBy).finish();
		while (aos_mkt_popular_ppcS.hasNext()) {
			Row aos_mkt_popular_ppc = aos_mkt_popular_ppcS.next();
			String aos_productno = aos_mkt_popular_ppc.getString("aos_productno");
			BigDecimal aos_budget = aos_mkt_popular_ppc.getBigDecimal("aos_budget");
			PpcToday.put(aos_productno, aos_budget);
		}
		aos_mkt_popular_ppcS.close();
		return PpcToday;
	}

	public static HashMap<String, Map<String, Object>> GenerateSkuRpt7(Object p_ou_code) {
		HashMap<String, Map<String, Object>> KeyRpt7 = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -7);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<=", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", date_to_str);
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_org };
		String SelectColumn = "aos_entryentity.aos_ad_name aos_ad_name,"
				+ "aos_entryentity.aos_targeting aos_targeting," + "aos_entryentity.aos_match_type aos_match_type,"
				+ "aos_entryentity.aos_impressions aos_impressions," + "aos_entryentity.aos_spend aos_spend,"
				+ "aos_entryentity.aos_sales aos_sales," + "aos_entryentity.aos_clicks aos_clicks";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateSkuRpt7" + p_ou_code,
				"aos_base_pointrpt", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_ad_name", "aos_targeting", "aos_match_type" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).sum("aos_impressions").sum("aos_spend")
				.sum("aos_sales").sum("aos_clicks").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			HashMap<String, Object> Info = new HashMap<>();
			String key = aos_base_skupoprpt.getString("aos_ad_name") + "~"
					+ aos_base_skupoprpt.getString("aos_targeting") + "~"
					+ aos_base_skupoprpt.getString("aos_match_type");
			Info.put("aos_spend", aos_base_skupoprpt.get("aos_spend"));
			Info.put("aos_sales", aos_base_skupoprpt.get("aos_sales"));
			Info.put("aos_impressions", aos_base_skupoprpt.get("aos_impressions"));
			Info.put("aos_clicks", aos_base_skupoprpt.get("aos_clicks"));
			KeyRpt7.put(key, Info);
		}
		aos_base_skupoprptS.close();
		return KeyRpt7;
	}

	public static HashMap<String, Map<String, Map<String, Object>>> GenerateSkuRpt14(Object p_ou_code) {
		HashMap<String, Map<String, Map<String, Object>>> KeyRpt14 = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -14);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<=", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", date_to_str);
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_org };
		String SelectColumn = "aos_entryentity.aos_ad_name aos_ad_name,"
				+ "aos_entryentity.aos_targeting aos_targeting," + "aos_entryentity.aos_match_type aos_match_type,"
				+ "aos_entryentity.aos_impressions aos_impressions," + "aos_entryentity.aos_spend aos_spend,"
				+ "aos_entryentity.aos_sales aos_sales," + "aos_entryentity.aos_clicks aos_clicks,"
				+ "aos_entryentity.aos_date_l aos_date_l";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateSkuRpt14" + p_ou_code,
				"aos_base_pointrpt", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_ad_name", "aos_targeting", "aos_match_type", "aos_date_l" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).sum("aos_impressions").sum("aos_spend")
				.sum("aos_sales").sum("aos_clicks").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			HashMap<String, Object> Detail = new HashMap<>();
			String key = aos_base_skupoprpt.getString("aos_ad_name") + "~"
					+ aos_base_skupoprpt.getString("aos_targeting") + "~"
					+ aos_base_skupoprpt.getString("aos_match_type");
			Detail.put("aos_spend", aos_base_skupoprpt.get("aos_spend"));
			Detail.put("aos_sales", aos_base_skupoprpt.get("aos_sales"));
			Detail.put("aos_impressions", aos_base_skupoprpt.get("aos_impressions"));
			Detail.put("aos_clicks", aos_base_skupoprpt.get("aos_clicks"));

			BigDecimal aos_roi = BigDecimal.ZERO;
			if (aos_base_skupoprpt.get("aos_spend") != null
					&& ((BigDecimal) aos_base_skupoprpt.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
				aos_roi = ((BigDecimal) aos_base_skupoprpt.get("aos_sales"))
						.divide((BigDecimal) aos_base_skupoprpt.get("aos_spend"), 2, BigDecimal.ROUND_HALF_UP);
			}
			Detail.put("aos_roi", aos_roi);
			BigDecimal aos_bid = BigDecimal.ZERO;
			if (aos_base_skupoprpt.get("aos_clicks") != null
					&& ((BigDecimal) aos_base_skupoprpt.get("aos_clicks")).compareTo(BigDecimal.ZERO) != 0) {
				aos_bid = ((BigDecimal) aos_base_skupoprpt.get("aos_spend"))
						.divide((BigDecimal) aos_base_skupoprpt.get("aos_clicks"), 2, BigDecimal.ROUND_HALF_UP);
			}
			Detail.put("aos_bid", aos_bid);

			Date aos_date_l = aos_base_skupoprpt.getDate("aos_date_l");
			String aos_date_str = DF.format(aos_date_l);
			Map<String, Map<String, Object>> Info = KeyRpt14.get(key);
			if (Info == null)
				Info = new HashMap<>();
			Info.put(aos_date_str, Detail);
			KeyRpt14.put(key, Info);
		}
		aos_base_skupoprptS.close();
		return KeyRpt14;
	}

	public static HashMap<String, Map<String, Map<String, Object>>> GenerateSkuRpt7Serial(Object p_ou_code) {
		HashMap<String, Map<String, Map<String, Object>>> KeyRpt7 = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -7);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<=", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", date_to_str);
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_org };
		String SelectColumn = "aos_entryentity.aos_cam_name aos_cam_name,"
				+ "aos_entryentity.aos_impressions aos_impressions," + "aos_entryentity.aos_spend aos_spend,"
				+ "aos_entryentity.aos_sales aos_sales," + "aos_entryentity.aos_clicks aos_clicks,"
				+ "aos_entryentity.aos_date_l aos_date_l";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet(
				"AosMktGenerate.GenerateSkuRpt7Serial" + p_ou_code, "aos_base_pointrpt", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_cam_name", "aos_date_l" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).sum("aos_impressions").sum("aos_spend")
				.sum("aos_sales").sum("aos_clicks").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			HashMap<String, Object> Detail = new HashMap<>();
			String key = aos_base_skupoprpt.getString("aos_cam_name");
			Detail.put("aos_spend", aos_base_skupoprpt.get("aos_spend"));
			Detail.put("aos_sales", aos_base_skupoprpt.get("aos_sales"));
			Detail.put("aos_impressions", aos_base_skupoprpt.get("aos_impressions"));
			Detail.put("aos_clicks", aos_base_skupoprpt.get("aos_clicks"));
			BigDecimal aos_roi = BigDecimal.ZERO;
			if (aos_base_skupoprpt.get("aos_spend") != null
					&& ((BigDecimal) aos_base_skupoprpt.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
				aos_roi = ((BigDecimal) aos_base_skupoprpt.get("aos_sales"))
						.divide((BigDecimal) aos_base_skupoprpt.get("aos_spend"), 2, BigDecimal.ROUND_HALF_UP);
			}
			Detail.put("aos_roi", aos_roi);
			BigDecimal aos_bid = BigDecimal.ZERO;
			if (aos_base_skupoprpt.get("aos_clicks") != null
					&& ((BigDecimal) aos_base_skupoprpt.get("aos_clicks")).compareTo(BigDecimal.ZERO) != 0) {
				aos_bid = ((BigDecimal) aos_base_skupoprpt.get("aos_spend"))
						.divide((BigDecimal) aos_base_skupoprpt.get("aos_clicks"), 2, BigDecimal.ROUND_HALF_UP);
			}
			Detail.put("aos_bid", aos_bid);

			Date aos_date_l = aos_base_skupoprpt.getDate("aos_date_l");
			String aos_date_str = DF.format(aos_date_l);
			Map<String, Map<String, Object>> Info = KeyRpt7.get(key);
			if (Info == null)
				Info = new HashMap<>();
			Info.put(aos_date_str, Detail);
			KeyRpt7.put(key, Info);
		}
		aos_base_skupoprptS.close();
		return KeyRpt7;
	}

	public static HashMap<String, Map<String, Object>> GenerateSalSummarySerial(Object p_ou_code) {
		HashMap<String, Map<String, Object>> SalSummary = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		calendar.set(Calendar.DAY_OF_MONTH, 1);
		Date date_from = calendar.getTime();
		calendar.add(Calendar.MONTH, 1);
		Date date_to = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		QFilter filter_org = new QFilter("aos_org.number", QCP.equals, p_ou_code);
		QFilter filter_type = new QFilter("aos_rsche_no", "=", "SKU调整表");
		QFilter filter_date_from = new QFilter("aos_month", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_month", "<", date_to_str);
		QFilter[] filters = new QFilter[] { filter_type, filter_date_from, filter_date_to, filter_org };
		String SelectColumn = "aos_sku.aos_productno||'-AUTO' aos_productno," + "aos_price * aos_sche_qty aos_sales,"
				+ "aos_sche_qty,createtime";
		DataSet aos_sal_sche_summaryS = QueryServiceHelper.queryDataSet(
				"AosMktGenerate.GenerateSalSummarySerial" + p_ou_code, "aos_sal_sche_summary", SelectColumn, filters,
				null);
		String[] GroupBy = new String[] { "aos_productno" };
		aos_sal_sche_summaryS = aos_sal_sche_summaryS.groupBy(GroupBy).maxP("createtime", "aos_sales")
				.maxP("createtime", "aos_sche_qty").finish();
		while (aos_sal_sche_summaryS.hasNext()) {
			Row aos_sal_sche_summary = aos_sal_sche_summaryS.next();
			HashMap<String, Object> Info = new HashMap<>();
			Info.put("aos_sales", aos_sal_sche_summary.get("aos_sales"));
			Info.put("aos_sche_qty", aos_sal_sche_summary.get("aos_sche_qty"));
			SalSummary.put(aos_sal_sche_summary.getString("aos_productno"), Info);
		}
		aos_sal_sche_summaryS.close();
		return SalSummary;
	}

	public static HashMap<String, Map<String, Object>> GeneratePpcYesterSerial(Object p_ou_code) {
		HashMap<String, Map<String, Object>> PpcYester = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -1);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		QFilter filter_date_from = new QFilter("aos_date", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_date", "<", date_to_str);
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter Qf_type = new QFilter("aos_entryentity.aos_groupstatus", "=", "AVAILABLE");
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, Qf_type, filter_org };
		String SelectColumn = "aos_entryentity.aos_productno aos_productno," + "aos_entryentity.aos_budget aos_budget";
		DataSet aos_mkt_popular_ppcS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateSalSummarySerial",
				"aos_mkt_pop_ppcst", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_productno", "aos_budget" };
		aos_mkt_popular_ppcS = aos_mkt_popular_ppcS.groupBy(GroupBy).finish();
		while (aos_mkt_popular_ppcS.hasNext()) {
			Row aos_mkt_popular_ppc = aos_mkt_popular_ppcS.next();
			HashMap<String, Object> Info = new HashMap<>();
			Info.put("aos_budget", aos_mkt_popular_ppc.get("aos_budget"));// 上次预算
			PpcYester.put(aos_mkt_popular_ppc.getString("aos_productno"), Info);
		}
		aos_mkt_popular_ppcS.close();
		return PpcYester;
	}

	public static HashMap<String, Map<String, Object>> GenerateSkuRptSerial(Object p_ou_code) {
		HashMap<String, Map<String, Object>> SkuRpt = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -7);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<=", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", date_to_str);
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_org };
		String SelectColumn = "aos_entryentity.aos_cam_name aos_productno," + "aos_entryentity.aos_spend aos_spend,"
				+ "aos_entryentity.aos_sales aos_sales";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateSkuRptSerial" + p_ou_code,
				"aos_base_pointrpt", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_productno" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).sum("aos_spend").sum("aos_sales").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			HashMap<String, Object> Info = new HashMap<>();
			Info.put("aos_spend", aos_base_skupoprpt.get("aos_spend"));
			Info.put("aos_total_sales", aos_base_skupoprpt.get("aos_sales"));
			SkuRpt.put(aos_base_skupoprpt.getString("aos_productno"), Info);
		}
		aos_base_skupoprptS.close();
		return SkuRpt;
	}

	public static HashMap<String, Map<String, Object>> GenerateSkuRptDetailSerial(Object p_ou_code) {
		HashMap<String, Map<String, Object>> SkuRptDetail = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -1);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<=", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", date_to_str);
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_org };
		String SelectColumn = "aos_entryentity.aos_cam_name aos_productno," + "aos_entryentity.aos_spend aos_spend";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet(
				"AosMktGenerate.GenerateSkuRptDetailSerial" + p_ou_code, "aos_base_pointrpt", SelectColumn, filters,
				null);
		String[] GroupBy = new String[] { "aos_productno" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).sum("aos_spend").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			Map<String, Object> Info = new HashMap<>();
			Info.put("aos_spend", aos_base_skupoprpt.get("aos_spend"));
			SkuRptDetail.put(aos_base_skupoprpt.getString("aos_productno"), Info);
		}
		aos_base_skupoprptS.close();
		return SkuRptDetail;
	}

	public static HashMap<String, Map<String, Map<String, Object>>> GenerateSkuRpt3SerialObject(Object p_ou_code) {
		HashMap<String, Map<String, Map<String, Object>>> SkuRpt = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -3);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<=", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", date_to_str);
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_org };
		String SelectColumn = "aos_entryentity.aos_cam_name aos_productno," + "aos_entryentity.aos_date_l aos_date_l,"
				+ "aos_entryentity.aos_impressions aos_impressions," + "aos_entryentity.aos_spend aos_spend,"
				+ "aos_entryentity.aos_sales aos_sales," + "aos_entryentity.aos_clicks aos_clicks";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet(
				"AosMktGenerate.GenerateSkuRpt3SerialObject" + p_ou_code, "aos_base_pointrpt", SelectColumn, filters,
				null);
		String[] GroupBy = new String[] { "aos_productno", "aos_date_l" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).sum("aos_clicks").sum("aos_impressions")
				.sum("aos_spend").sum("aos_sales").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			Map<String, Object> Detail = new HashMap<>();
			Detail.put("aos_spend", aos_base_skupoprpt.get("aos_spend"));
			Detail.put("aos_total_sales", aos_base_skupoprpt.get("aos_sales"));
			Detail.put("aos_impressions", aos_base_skupoprpt.get("aos_impressions"));
			BigDecimal aos_roi = BigDecimal.ZERO;
			if (aos_base_skupoprpt.get("aos_spend") != null
					&& ((BigDecimal) aos_base_skupoprpt.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
				aos_roi = ((BigDecimal) aos_base_skupoprpt.get("aos_sales"))
						.divide((BigDecimal) aos_base_skupoprpt.get("aos_spend"), 2, BigDecimal.ROUND_HALF_UP);
			}
			Detail.put("aos_roi", aos_roi);
			BigDecimal aos_bid = BigDecimal.ZERO;
			if (aos_base_skupoprpt.get("aos_clicks") != null
					&& ((BigDecimal) aos_base_skupoprpt.get("aos_clicks")).compareTo(BigDecimal.ZERO) != 0) {
				aos_bid = ((BigDecimal) aos_base_skupoprpt.get("aos_spend"))
						.divide((BigDecimal) aos_base_skupoprpt.get("aos_clicks"), 2, BigDecimal.ROUND_HALF_UP);
			}
			Detail.put("aos_bid", aos_bid);
			Date aos_date_l = aos_base_skupoprpt.getDate("aos_date_l");
			String aos_date_str = DF.format(aos_date_l);

			Map<String, Map<String, Object>> Info = SkuRpt.get(aos_base_skupoprpt.getString("aos_productno"));
			if (Info == null)
				Info = new HashMap<>();

			Info.put(aos_date_str, Detail);
			SkuRpt.put(aos_base_skupoprpt.getString("aos_productno"), Info);
		}
		aos_base_skupoprptS.close();
		return SkuRpt;
	}

	public static HashMap<String, Map<String, Object>> GenerateSkuRpt3Group(String p_ou_code) {
		HashMap<String, Map<String, Object>> KeyRpt3 = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -3);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<=", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", date_to_str);
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_org };
		String SelectColumn = "aos_entryentity.aos_ad_name aos_ad_name,"
				+ "aos_entryentity.aos_impressions aos_impressions," + "aos_entryentity.aos_spend aos_spend,"
				+ "aos_entryentity.aos_sales aos_sales," + "aos_entryentity.aos_clicks aos_clicks";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateSkuRpt3Group" + p_ou_code,
				"aos_base_pointrpt", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_ad_name" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).sum("aos_impressions").sum("aos_spend")
				.sum("aos_sales").sum("aos_clicks").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			HashMap<String, Object> Info = new HashMap<>();
			String key = aos_base_skupoprpt.getString("aos_ad_name");
			Info.put("aos_spend", aos_base_skupoprpt.get("aos_spend"));
			Info.put("aos_sales", aos_base_skupoprpt.get("aos_sales"));
			Info.put("aos_impressions", aos_base_skupoprpt.get("aos_impressions"));
			Info.put("aos_clicks", aos_base_skupoprpt.get("aos_clicks"));
			KeyRpt3.put(key, Info);
		}
		aos_base_skupoprptS.close();
		return KeyRpt3;
	}

	public static HashMap<String, Map<String, Object>> GenerateSkuRpt7Group(String p_ou_code) {
		HashMap<String, Map<String, Object>> KeyRpt7 = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -7);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<=", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", date_to_str);
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_org };
		String SelectColumn = "aos_entryentity.aos_ad_name aos_ad_name,"
				+ "aos_entryentity.aos_impressions aos_impressions," + "aos_entryentity.aos_spend aos_spend,"
				+ "aos_entryentity.aos_sales aos_sales," + "aos_entryentity.aos_clicks aos_clicks";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet("AosMktGenerate.GenerateSkuRpt7Group" + p_ou_code,
				"aos_base_pointrpt", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_ad_name" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).sum("aos_impressions").sum("aos_spend")
				.sum("aos_sales").sum("aos_clicks").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			HashMap<String, Object> Info = new HashMap<>();
			String key = aos_base_skupoprpt.getString("aos_ad_name");
			Info.put("aos_spend", aos_base_skupoprpt.get("aos_spend"));
			Info.put("aos_sales", aos_base_skupoprpt.get("aos_sales"));
			Info.put("aos_impressions", aos_base_skupoprpt.get("aos_impressions"));
			Info.put("aos_clicks", aos_base_skupoprpt.get("aos_clicks"));
			KeyRpt7.put(key, Info);
		}
		aos_base_skupoprptS.close();
		return KeyRpt7;
	}

	public static HashMap<String, Map<String, Object>> GenerateKeyDetailToday(String p_ou_code, long p_group_id) {
		HashMap<String, Map<String, Object>> KeyDetailToday = new HashMap<>();
		Calendar today = Calendar.getInstance();
		today.set(Calendar.HOUR_OF_DAY, 0);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.SECOND, 0);
		today.set(Calendar.MILLISECOND, 0);
		Date Today = today.getTime();
		QFilter Qf_Date = new QFilter("aos_date", "=", Today);
		QFilter Qf_Org = new QFilter("aos_orgid.number", "=", p_ou_code);
		QFilter Qf_type = new QFilter("aos_entryentity.aos_groupstatus", "=", "AVAILABLE");
		QFilter Qf_typekey = new QFilter("aos_entryentity.aos_keystatus", "=", "AVAILABLE");
		QFilter Qf_group = new QFilter("aos_entryentity.aos_sal_group", "=", p_group_id);
		QFilter[] filters = new QFilter[] { Qf_Date, Qf_Org, Qf_type, Qf_group, Qf_typekey };
		String SelectColumn = "aos_entryentity.aos_itemnumer aos_ad_name," + "aos_entryentity.aos_keyword aos_keyword,"
				+ "aos_entryentity.aos_match_type aos_match_type";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet(
				"AosMktGenerate.GenerateKeyDetailToday" + p_ou_code, "aos_mkt_pop_ppcst", SelectColumn, filters, null);
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			String key = aos_base_skupoprpt.getString("aos_ad_name");
			Map<String, Object> Info = KeyDetailToday.get(key);
			if (Info == null)
				Info = new HashMap<>();
			Info.put(aos_base_skupoprpt.getString("aos_keyword") + "~" + aos_base_skupoprpt.getString("aos_match_type"),
					aos_base_skupoprpt.getString("aos_keyword") + "~" + aos_base_skupoprpt.getString("aos_match_type"));
			KeyDetailToday.put(key, Info);
		}
		aos_base_skupoprptS.close();
		return KeyDetailToday;
	}

	public static HashMap<String, Map<String, Map<String, Object>>> GenerateYesterSTSerial7D(String p_ou_code) {
		HashMap<String, Map<String, Map<String, Object>>> PpcYster7 = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -7);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter filter_date_from = new QFilter("aos_date", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_date", "<=", date_to_str);
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_org };
		String SelectColumn = "aos_date," + "aos_entryentity.aos_budget aos_budget,"
				+ "aos_entryentity.aos_productno aos_productno,1 aos_keycount";
		DataSet aos_mkt_popular_ppcS = QueryServiceHelper.queryDataSet(
				"AosMktGenerate.GenerateYesterSTSerial7D" + p_ou_code, "aos_mkt_pop_ppcst", SelectColumn, filters,
				null);
		String[] GroupBy = new String[] { "aos_date", "aos_productno", "aos_budget" };
		aos_mkt_popular_ppcS = aos_mkt_popular_ppcS.groupBy(GroupBy).sum("aos_keycount").finish();
		while (aos_mkt_popular_ppcS.hasNext()) {
			Row aos_mkt_popular_ppc = aos_mkt_popular_ppcS.next();
			Map<String, Object> Detail = new HashMap<>();
			Detail.put("aos_budget", aos_mkt_popular_ppc.get("aos_budget"));
			Detail.put("aos_keycount", aos_mkt_popular_ppc.get("aos_keycount"));
			Date aos_date = aos_mkt_popular_ppc.getDate("aos_date");
			String aos_date_str = DF.format(aos_date);
			String key = aos_mkt_popular_ppc.getString("aos_productno");
			Map<String, Map<String, Object>> Info = PpcYster7.get(key);
			if (Info == null)
				Info = new HashMap<>();
			Info.put(aos_date_str, Detail);
			PpcYster7.put(key, Info);
		}
		aos_mkt_popular_ppcS.close();
		return PpcYster7;
	}

	public static HashMap<String, Map<String, Object>> GenerateYesterSTSerial7G(String p_ou_code) {
		HashMap<String, Map<String, Object>> PpcYster7 = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -7);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter filter_date_from = new QFilter("aos_date", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_date", "<=", date_to_str);
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_org };
		String SelectColumn = "aos_date," + "aos_entryentity.aos_productno aos_productno,"
				+ "aos_entryentity.aos_itemnumer aos_itemnumer";
		DataSet aos_mkt_popular_ppcS = QueryServiceHelper.queryDataSet(
				"AosMktGenerate.GenerateYesterSTSerial7G" + p_ou_code, "aos_mkt_pop_ppcst", SelectColumn, filters,
				null);
		String[] GroupBy = new String[] { "aos_date", "aos_productno", "aos_itemnumer" };
		aos_mkt_popular_ppcS = aos_mkt_popular_ppcS.groupBy(GroupBy).finish();
		while (aos_mkt_popular_ppcS.hasNext()) {
			Row aos_mkt_popular_ppc = aos_mkt_popular_ppcS.next();
			Date aos_date = aos_mkt_popular_ppc.getDate("aos_date");
			String aos_date_str = DF.format(aos_date);
			String key = aos_mkt_popular_ppc.getString("aos_productno");
			Map<String, Object> Info = PpcYster7.get(key);
			if (Info == null)
				Info = new HashMap<>();

			if (Info.get(aos_date_str) == null)
				Info.put(aos_date_str, 1);
			else
				Info.put(aos_date_str, (int) Info.get(aos_date_str) + 1);
			PpcYster7.put(key, Info);
		}
		aos_mkt_popular_ppcS.close();
		return PpcYster7;
	}

	/** 初始化组关键词 **/
	public static HashMap<String, Map<String, Object>> GenerateStBase(Object p_ou_code) {
		HashMap<String, Map<String, Object>> StBase = new HashMap<>();
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter[] filters = new QFilter[] { filter_org };
		String SelectColumn = "aos_itemnumer,aos_keyword,aos_match_type,aos_keystatus aos_status";
		DataSet aos_mkt_pop_basestS = QueryServiceHelper.queryDataSet(
				"AosMktGenerate.GeneratePpcStLastItemName" + p_ou_code, "aos_mkt_pop_basest", SelectColumn, filters,
				null);
		String[] GroupBy = new String[] { "aos_itemnumer", "aos_keyword", "aos_match_type", "aos_status" };
		aos_mkt_pop_basestS = aos_mkt_pop_basestS.groupBy(GroupBy).finish();
		while (aos_mkt_pop_basestS.hasNext()) {
			Row aos_mkt_pop_basest = aos_mkt_pop_basestS.next();
			String aos_itemnumer = aos_mkt_pop_basest.getString("aos_itemnumer");
			String aos_keyword = aos_mkt_pop_basest.getString("aos_keyword");
			String aos_match_type = aos_mkt_pop_basest.getString("aos_match_type");
			String aos_status = aos_mkt_pop_basest.getString("aos_status");
			Map<String, Object> Info = StBase.get(aos_itemnumer);
			if (Info == null)
				Info = new HashMap<>();
			Info.put(aos_keyword + "~" + aos_match_type, aos_status);
			StBase.put(aos_itemnumer, Info);
		}
		aos_mkt_pop_basestS.close();
		return StBase;
	}

}
