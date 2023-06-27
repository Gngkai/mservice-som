package mkt.popularst.add_s;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import common.Cux_Common_Utl;
import common.fnd.FndGlobal;
import common.fnd.FndLog;
import common.fnd.FndMsg;
import common.sal.util.InStockAvailableDays;
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
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import mkt.common.AosMktGenerate;
import mkt.common.MKTCom;
import mkt.popular.aos_mkt_pop_common;
import sal.quote.CommData;
import sal.sche.aos_sal_sche_pub.aos_sal_sche_pub;
import sal.synciface.imp.aos_sal_import_pub;

public class aos_mkt_popadds_init extends AbstractTask {

	/** 系统管理员 **/
	public final static String system = Cux_Common_Utl.SYSTEM;

	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		executerun();// 多线程执行
	}

	public static void executerun() {
		// CommData.init();
		Calendar Today = Calendar.getInstance();
		int week = Today.get(Calendar.DAY_OF_WEEK);
		Boolean CopyFlag = aos_mkt_pop_common.GetCopyFlag("PPC_ST_ADD", week);
		if (!CopyFlag) {
			return;// 如果不是周一 直接跳过
		}
		int hour = Today.get(Calendar.HOUR_OF_DAY);
		System.out.println("hour =" + hour);
		long is_oversea_flag = aos_sal_sche_pub.get_lookup_values("AOS_YES_NO", "Y");
		QFilter qf_time = null;
		DynamicObject dynamicObject = QueryServiceHelper.queryOne("aos_mkt_base_orgvalue", "aos_value",
				new QFilter[] { new QFilter("aos_type", QCP.equals, "TIME") });
		int time = 16;
		if (dynamicObject != null)
			time = dynamicObject.getBigDecimal("aos_value").intValue();
		if (hour < time)
			qf_time = new QFilter("aos_is_north_america", QCP.not_equals, is_oversea_flag);
		else
			qf_time = new QFilter("aos_is_north_america", QCP.equals, is_oversea_flag);
		// 调用线程池
		QFilter oversea_flag = new QFilter("aos_is_oversea_ou", "=", is_oversea_flag);// 海外公司
		QFilter oversea_flag2 = new QFilter("aos_isomvalid", "=", true);
		QFilter[] filters_ou = new QFilter[] { oversea_flag, oversea_flag2, qf_time };
		DynamicObjectCollection bd_country = QueryServiceHelper.query("bd_country", "id,number", filters_ou);
		for (DynamicObject ou : bd_country) {
			String p_ou_code = ou.getString("number");
			Map<String, Object> params = new HashMap<>();
			params.put("p_ou_code", p_ou_code);
			do_operate(params);
		}
	}

	public static void do_operate(Map<String, Object> params) {
		FndMsg.debug("=====Into 手动建组初始化=====");
		// 传入参数
		Object p_ou_code = params.get("p_ou_code");
		Object p_org_id = aos_sal_import_pub.get_import_id(p_ou_code, "bd_country");
		long org_id = (long) p_org_id;
		String orgid_str = p_org_id.toString();
		// 删除数据
		Calendar date = Calendar.getInstance();
		date.set(Calendar.HOUR_OF_DAY, 0);
		date.set(Calendar.MINUTE, 0);
		date.set(Calendar.SECOND, 0);
		date.set(Calendar.MILLISECOND, 0);
		Date Today = date.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String today_str = writeFormat.format(Today);
		int month = date.get(Calendar.MONTH) + 1;// 当前月
		int aos_weekofyear = date.get(Calendar.WEEK_OF_YEAR);
		String aos_dayofweek = Cux_Common_Utl.WeekOfDay[Calendar.DAY_OF_WEEK - 1];
		QFilter filter_id = new QFilter("aos_orgid.number", "=", p_ou_code);
		QFilter filter_date = new QFilter("aos_makedate", "=", Today);
		QFilter[] filters_st = new QFilter[] { filter_id, filter_date };
		DeleteServiceHelper.delete("aos_mkt_pop_addst", filters_st);
		// 通用数据
		HashMap<String, Object> PpcStLastMap = AosMktGenerate.GeneratePpcStLast(p_ou_code);
		List<Map<String, Object>> ItemQtyList = AosMktGenerate.GenerateItemQtyList(p_ou_code);
		HashMap<String, Map<String, Object>> KeyRpt7Map = AosMktGenerate.GenerateKeyRpt7(p_ou_code);
		Map<String, Object> ItemOverseaQtyMap = ItemQtyList.get(0);// 海外库存
		Map<String, Object> ItemIntransQtyMap = ItemQtyList.get(1);// 在途数量
		List<Integer> MapList = AosMktGenerate.GenerateShpDay(p_ou_code);// 全程天数信息
		int aos_shp_day = MapList.get(0);// 备货天数
		int aos_freight_day = MapList.get(1);// 海运天数
		int aos_clear_day = MapList.get(2);// 清关天数
		Date SummerSpringStart = MKTCom.Get_DateRange("aos_datefrom", "SS", p_org_id);
		Date SummerSpringEnd = MKTCom.Get_DateRange("aos_dateto", "SS", p_org_id);
		Date AutumnWinterStart = MKTCom.Get_DateRange("aos_datefrom", "AW", p_org_id);
		Date AutumnWinterEnd = MKTCom.Get_DateRange("aos_dateto", "AW", p_org_id);
		Set<String> specialSet = getSpecial(orgid_str, today_str);
		HashMap<String, Map<String, String>> itemPoint = GenerateItemPoint(p_org_id);
		// 查询国别物料
		QFilter filter_ou = new QFilter("aos_contryentry.aos_nationality.number", "=", p_ou_code);
		QFilter filter_type = new QFilter("aos_protype", "=", "N");
		QFilter[] filters = new QFilter[] { filter_ou, filter_type };
		String SelectField = "id,aos_productno," + "number," + "name," + "aos_contryentry.aos_nationality.id aos_orgid,"
				+ "aos_contryentry.aos_nationality.number aos_orgnumber,"
				+ "aos_contryentry.aos_seasonseting.number aos_seasonseting,"
				+ "aos_contryentry.aos_seasonseting.name aos_season,"
				+ "aos_contryentry.aos_seasonseting.id aos_seasonid,"
				+ "aos_contryentry.aos_contryentrystatus aos_contryentrystatus,"
				+ "aos_contryentry.aos_festivalseting.number aos_festivalseting,"
				+ "aos_contryentry.aos_firstindate aos_firstindate";
		DynamicObjectCollection bd_materialS = QueryServiceHelper.query("bd_material", SelectField, filters,
				"aos_productno");
		// 查询销售组别
		QFilter group_org = new QFilter("aos_org.number", "=", p_ou_code);// 海外公司
		QFilter group_enable = new QFilter("enable", "=", 1);// 可用
		QFilter[] filters_group = new QFilter[] { group_org, group_enable };
		DynamicObjectCollection aos_salgroup = QueryServiceHelper.query("aos_salgroup", "id", filters_group);
		FndLog log = FndLog.init("MKT_PPC_ST手动建组", p_ou_code.toString());
		// 销售组别循环
		for (DynamicObject group : aos_salgroup) {
			FndMsg.debug("=====Into  销售组别循环=====");
			String group_id = group.getString("id");
			// 初始化头数据
			DynamicObject aos_mkt_pop_addst = BusinessDataServiceHelper.newDynamicObject("aos_mkt_pop_addst");
			aos_mkt_pop_addst.set("aos_orgid", p_org_id);
			aos_mkt_pop_addst.set("billstatus", "A");
			aos_mkt_pop_addst.set("aos_status", "A");
			aos_mkt_pop_addst.set("aos_weekofyear", aos_weekofyear);
			aos_mkt_pop_addst.set("aos_dayofweek", aos_dayofweek);
			aos_mkt_pop_addst.set("aos_makedate", Today);
			aos_mkt_pop_addst.set("aos_makeby", system);
			aos_mkt_pop_addst.set("aos_groupid", group_id);
			DynamicObjectCollection aos_entryentityS = aos_mkt_pop_addst.getDynamicObjectCollection("aos_entryentity");
			// 国别物料循环
			for (DynamicObject bd_material : bd_materialS) {
				// 物料编码
				String aos_itemnumer = bd_material.getString("number");
				// ID
				long item_id = bd_material.getLong("id");
				String itemid_str = Long.toString(item_id);
				// 产品类别
				String itemCategoryId = CommData.getItemCategoryId(item_id + "");
				if (itemCategoryId == null || "".equals(itemCategoryId)) {
					log.add(aos_itemnumer + "产品类别不存在");
					continue;
				}
				// 销售组别
				String salOrg = CommData.getSalOrgV2(p_org_id + "", itemCategoryId); // 小类获取组别
				if (salOrg == null || "".equals(salOrg)) {
					log.add(aos_itemnumer + "销售组别不存在");
					continue;
				}
				if (!salOrg.equals(group_id))
					continue;
				// 物料品名
				String aos_cn_name = bd_material.getString("name");
				// 节日属性
				String aos_festivalseting = bd_material.getString("aos_festivalseting");
				if (aos_festivalseting == null)
					aos_festivalseting = "";
				// 季节属性
				String aos_seasonpro = bd_material.getString("aos_seasonseting");
				if (aos_seasonpro == null)
					aos_seasonpro = "";
				Object aos_seasonid = bd_material.get("aos_seasonid");
				if (aos_seasonid == null)
					continue;
				// 海外库存
				Object item_overseaqty = ItemOverseaQtyMap.get(itemid_str);
				if (FndGlobal.IsNull(item_overseaqty))
					item_overseaqty = 0;
				// 在途数量
				Object item_intransqty = ItemIntransQtyMap.get(itemid_str);
				if (FndGlobal.IsNull(item_intransqty))
					item_intransqty = 0;
				// 可售天数
				Map<String, Object> DayMap = new HashMap<>();
				int availableDays = InStockAvailableDays.calInstockSalDaysForDayMin(orgid_str, itemid_str, DayMap);
				// 特殊广告货号过滤
				if (!specialSet.contains(aos_itemnumer)) {
					log.add(aos_itemnumer + "不为今日SP特殊广告");
					continue;
				}
				if ("REGULAR".equals(aos_seasonpro)) {
					// 常规品过滤
					if ((int) item_overseaqty <= 50) {
						log.add(aos_itemnumer + "常规品海外库存必须大于50");
						continue;
					}
					if (PpcStLastMap.get(itemid_str) != null) {
						log.add(aos_itemnumer + "昨日PPCST中不能存在");
						continue;
					}
					if ((availableDays < 30) && (MKTCom.Is_PreSaleOut(org_id, item_id, (int) item_intransqty,
							aos_shp_day, aos_freight_day, aos_clear_day, availableDays))) {
						log.add(aos_itemnumer + " 非预断货");
						continue;
					}
				} else {
					// 季节品过滤
					if ((aos_seasonpro != null)
							&& (aos_seasonpro.equals("SPRING") || aos_seasonpro.equals("SPRING_SUMMER")
									|| aos_seasonpro.equals("SUMMER"))
							&& (Today.before(SummerSpringStart) || Today.after(SummerSpringEnd))) {
						log.add(aos_itemnumer + "剔除过季品");
						continue;

					}
					if ((aos_seasonpro != null)
							&& (aos_seasonpro.equals("AUTUMN_WINTER") || aos_seasonpro.equals("WINTER"))
							&& (Today.before(AutumnWinterStart) || Today.after(AutumnWinterEnd))) {
						log.add(aos_itemnumer + "剔除过季品");
						continue;
					}
					if ((int) item_overseaqty <= 50) {
						log.add(aos_itemnumer + "季节品海外库存必须大于50");
						continue;
					}
					float SeasonRate = MKTCom.Get_SeasonRate(org_id, item_id, aos_seasonpro, item_overseaqty, month);
					if (!(PpcStLastMap.get(itemid_str) == null
							|| (SeasonRate < 0.9 && !MKTCom.Is_SeasonRate(aos_seasonpro, month, SeasonRate)))) {
						log.add(aos_itemnumer + " 累计完成率" + SeasonRate);
						continue;
					}
				}
				// 大中小类
				String category = (String) SalUtil.getCategoryByItemId(itemid_str).get("name");
				String[] category_group = category.split(",");
				String aos_category1 = null;
				String aos_category2 = null;
				String aos_category3 = null;
				int category_length = category_group.length;
				if (category_length > 0)
					aos_category1 = category_group[0];
				if (category_length > 1)
					aos_category2 = category_group[1];
				if (category_length > 2)
					aos_category3 = category_group[2];

				Map<String, String> itemPointDetail = itemPoint
						.get(aos_category1 + "~" + aos_category2 + "~" + aos_category3 + "~" + aos_cn_name);

				if (FndGlobal.IsNull(itemPointDetail))
					continue;

				// 行明细
				DynamicObject aos_entryentity = aos_entryentityS.addNew();
				aos_entryentity.set("aos_itemid", item_id);
				aos_entryentity.set("aos_itemname", aos_cn_name);
				aos_entryentity.set("aos_category1", aos_category1);
				aos_entryentity.set("aos_category2", aos_category2);
				aos_entryentity.set("aos_category3", aos_category3);
				aos_entryentity.set("aos_avaqty", availableDays);
				aos_entryentity.set("aos_osqty", item_overseaqty);
				aos_entryentity.set("aos_season", aos_seasonid);
				aos_entryentity.set("aos_contryentrystatus", bd_material.get("aos_contryentrystatus"));
				aos_entryentity.set("aos_special", true);

				DynamicObjectCollection aos_subentryentityS = aos_entryentity
						.getDynamicObjectCollection("aos_subentryentity");

				for (String key : itemPointDetail.keySet()) {
					// EXACT
					DynamicObject aos_subentryentity = aos_subentryentityS.addNew();
					aos_subentryentity.set("aos_keyword", key);
					aos_subentryentity.set("aos_match", "EXACT");
					aos_subentryentity.set("aos_keytype", "特定");
					aos_subentryentity.set("aos_keysource", itemPointDetail.get(key));
					aos_subentryentity.set("aos_keystatus", "ON");

					if (KeyRpt7Map.get(aos_itemnumer + "~" + key) != null) {
						Map<String, Object> KeyRpt7MapD = KeyRpt7Map.get(aos_itemnumer + "~" + key + "~EXACT");
						if (KeyRpt7MapD != null) {
							BigDecimal aos_exprate = BigDecimal.ZERO;
							BigDecimal aos_impressions = BigDecimal.ZERO;
							BigDecimal aos_clicks = BigDecimal.ZERO;
							aos_impressions = (BigDecimal) KeyRpt7MapD.get("aos_impressions");
							aos_clicks = (BigDecimal) KeyRpt7MapD.get("aos_clicks");
							if (aos_impressions.compareTo(BigDecimal.ZERO) != 0)
								aos_exprate = aos_clicks.divide(aos_impressions, 6, BigDecimal.ROUND_HALF_UP);
							aos_subentryentity.set("aos_click", aos_clicks);
							aos_subentryentity.set("aos_spend", KeyRpt7MapD.get("aos_spend"));
							aos_subentryentity.set("aos_exposure", aos_impressions);
							aos_subentryentity.set("aos_roi", KeyRpt7MapD.get("aos_roi"));
							aos_subentryentity.set("aos_rate", aos_exprate);
						}
					}

					// PHRASE
					aos_subentryentity = aos_subentryentityS.addNew();
					aos_subentryentity.set("aos_keyword", key);
					aos_subentryentity.set("aos_match", "PHRASE");
					aos_subentryentity.set("aos_keytype", "特定");
					aos_subentryentity.set("aos_keysource", itemPointDetail.get(key));
					aos_subentryentity.set("aos_keystatus", "ON");

					if (KeyRpt7Map.get(aos_itemnumer + "~" + key) != null) {
						Map<String, Object> KeyRpt7MapD = KeyRpt7Map.get(aos_itemnumer + "~" + key + "~PHRASE");
						if (KeyRpt7MapD != null) {
							BigDecimal aos_exprate = BigDecimal.ZERO;
							BigDecimal aos_impressions = BigDecimal.ZERO;
							BigDecimal aos_clicks = BigDecimal.ZERO;
							aos_impressions = (BigDecimal) KeyRpt7MapD.get("aos_impressions");
							aos_clicks = (BigDecimal) KeyRpt7MapD.get("aos_clicks");
							if (aos_impressions.compareTo(BigDecimal.ZERO) != 0)
								aos_exprate = aos_clicks.divide(aos_impressions, 6, BigDecimal.ROUND_HALF_UP);
							aos_subentryentity.set("aos_click", aos_clicks);
							aos_subentryentity.set("aos_spend", KeyRpt7MapD.get("aos_spend"));
							aos_subentryentity.set("aos_exposure", aos_impressions);
							aos_subentryentity.set("aos_roi", KeyRpt7MapD.get("aos_roi"));
							aos_subentryentity.set("aos_rate", aos_exprate);
						}
					}

				}
			}

			FndMsg.debug("=====准备保存=====");
			// 保存数据
			OperationServiceHelper.executeOperate("save", "aos_mkt_pop_addst",
					new DynamicObject[] { aos_mkt_pop_addst }, OperateOption.create());
			// 保存日志表
			log.finnalSave();
		}
	}

	public static HashMap<String, Map<String, String>> GenerateItemPoint(Object p_org_id) {
		HashMap<String, Map<String, String>> itemPoint = new HashMap<>();
		// 品名关键词库
		DynamicObjectCollection aos_mkt_itempointS = QueryServiceHelper.query("aos_mkt_itempoint",
				"aos_category1," + "aos_category2,aos_category3," + "aos_itemname,aos_point",
				new QFilter("aos_orgid", QCP.equals, p_org_id).toArray());
		for (DynamicObject aos_mkt_itempoint : aos_mkt_itempointS) {
			String key = aos_mkt_itempoint.getString("aos_category1") + "~"
					+ aos_mkt_itempoint.getString("aos_category2") + "~" + aos_mkt_itempoint.getString("aos_category3")
					+ "~" + aos_mkt_itempoint.getString("aos_itemname");
			String point = aos_mkt_itempoint.getString("aos_point");
			Map<String, String> info = itemPoint.get(key);
			if (info == null)
				info = new HashMap<>();
			info.put(point, "F");
			itemPoint.put(key, info);
		}
		// SKU关键词库
		DynamicObjectCollection aos_mkt_keywordS = QueryServiceHelper.query("aos_mkt_keyword",
				"aos_category1," + "aos_category2,aos_category3,"
						+ "aos_itemname,aos_entryentity.aos_mainvoc aos_point",
				new QFilter("aos_orgid", QCP.equals, p_org_id).toArray());
		for (DynamicObject aos_mkt_keyword : aos_mkt_keywordS) {
			String key = aos_mkt_keyword.getString("aos_category1") + "~" + aos_mkt_keyword.getString("aos_category2")
					+ "~" + aos_mkt_keyword.getString("aos_category3") + "~"
					+ aos_mkt_keyword.getString("aos_itemname");
			String point = aos_mkt_keyword.getString("aos_point");
			Map<String, String> info = itemPoint.get(key);
			if (info == null)
				info = new HashMap<>();
			info.put(point, "S");
			itemPoint.put(key, info);
		}
		return itemPoint;
	}

	// 营销特殊广告货号
	private static Set<String> getSpecial(String aos_orgid, String today_str) {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_popular_ppc",
				"aos_entryentity.aos_itemnumer aos_itemnumer",
				new QFilter("aos_orgid", QCP.equals, aos_orgid).and("aos_date", QCP.large_equals, today_str)
						.and("aos_entryentity.aos_special", QCP.equals, true).toArray());
		return list.stream().map(obj -> obj.getString("aos_itemnumer")).collect(Collectors.toSet());
	}

}
