package mkt.popularst.week;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import common.Cux_Common_Utl;
import common.fnd.FndGlobal;
import common.sal.util.InStockAvailableDays;
import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
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
import mkt.popular.aos_mkt_pop_common;
import mkt.popularst.ppc.aos_mkt_popppcst_init;
import sal.quote.CommData;
import sal.sche.aos_sal_sche_pub.aos_sal_sche_pub;
import sal.synciface.imp.aos_sal_import_pub;

public class aos_mkt_popweek_init extends AbstractTask {

	/** 系统管理员 **/
	public final static String system = Cux_Common_Utl.SYSTEM;

	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		executerun();// 多线程执行
	}

	public static void executerun() {
		// CommData.init();
		Calendar Today = Calendar.getInstance();
		int week = Today.get(Calendar.DAY_OF_WEEK);
		Boolean CopyFlag = aos_mkt_pop_common.GetCopyFlag("PPC_ST_WEEK", week);
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
		// 获取传入参数 国别
		Object p_ou_code = params.get("p_ou_code");
		Object p_org_id = aos_sal_import_pub.get_import_id(p_ou_code, "bd_country");
		// 删除数据
		Calendar date = Calendar.getInstance();
		date.set(Calendar.HOUR_OF_DAY, 0);
		date.set(Calendar.MINUTE, 0);
		date.set(Calendar.SECOND, 0);
		date.set(Calendar.MILLISECOND, 0);
		Date Today = date.getTime();
		int aos_weekofyear = date.get(Calendar.WEEK_OF_YEAR);
		String aos_dayofweek = Cux_Common_Utl.WeekOfDay[Calendar.DAY_OF_WEEK - 1];
		QFilter filter_id = new QFilter("aos_orgid.number", "=", p_ou_code);
		QFilter filter_date = new QFilter("aos_makedate", "=", Today);
		QFilter[] filters_st = new QFilter[] { filter_id, filter_date };
		DeleteServiceHelper.delete("aos_mkt_pop_weekst", filters_st);
		// 初始化数据
		HashMap<String, Map<String, Object>> PopOrgInfo = AosMktGenerate.GeneratePopOrgInfo(p_ou_code);
		BigDecimal EXPOSURE = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "EXPOSURE").get("aos_value");// 国别曝光标准
		HashMap<String, BigDecimal> KeyRpt7GroupExp = AosMktGenerate.GenerateKeyRpt7GroupExp(p_ou_code);
		List<Map<String, Object>> ItemQtyList = AosMktGenerate.GenerateItemQtyList(p_ou_code);
		Map<String, Object> ItemOverseaQtyMap = ItemQtyList.get(0);// 海外库存
		HashMap<String, Map<String, Object>> KeyRpt7Map = AosMktGenerate.GenerateKeyRpt7(p_ou_code);
		HashMap<String, Map<String, String>> itemPoint = aos_mkt_popppcst_init.GenerateItemPoint(p_org_id);
		// 物料词信息
		HashMap<String, Map<String, Object>> KeyRpt = AosMktGenerate.GenerateKeyRpt(p_ou_code); // 关键词报告

		filter_date = new QFilter("aos_date", "=", Today);
		QFilter filter_ppcorg = new QFilter("aos_orgid.number", "=", p_ou_code);
//		QFilter filter_ava = new QFilter("aos_entryentity.aos_keystatus", "=", "AVAILABLE");
		QFilter filter_ava = new QFilter("aos_keystatus", "=", "AVAILABLE");
		QFilter[] filters_ppc = new QFilter[] { filter_date, filter_ppcorg, filter_ava };
//		String SelectColumn = "aos_entryentity.aos_itemnumer aos_itemnumer," + "aos_entryentity.aos_itemid aos_itemid,"
//				+ "aos_entryentity.aos_itemname aos_itemname," + "aos_entryentity.aos_season aos_season,"
//				+ "1 aos_count";
//		DataSet aos_mkt_pop_ppcstS = QueryServiceHelper.queryDataSet("aos_mkt_popweek_init.ppcst", "aos_mkt_pop_ppcst",
//				SelectColumn, filters_ppc, null);
		
		String SelectColumn = "aos_itemnumer,aos_itemid,aos_itemname,aos_season,1 aos_count";
		DataSet aos_mkt_pop_ppcstS = QueryServiceHelper.queryDataSet("aos_mkt_popweek_init.ppcst", "aos_mkt_ppcst_data",
				SelectColumn, filters_ppc, null);
		String[] GroupBy = new String[] { "aos_itemid", "aos_itemnumer", "aos_itemname", "aos_season" };
		aos_mkt_pop_ppcstS = aos_mkt_pop_ppcstS.groupBy(GroupBy).sum("aos_count").finish();
		// 循环销售组别
		QFilter group_org = new QFilter("aos_org.number", "=", p_ou_code);// 海外公司
		QFilter group_enable = new QFilter("enable", "=", 1);// 可用
		QFilter[] filters_group = new QFilter[] { group_org, group_enable };
		DynamicObjectCollection aos_salgroup = QueryServiceHelper.query("aos_salgroup", "id", filters_group);
		for (DynamicObject group : aos_salgroup) {
			// 循环今日生成的PPCST数据
			DynamicObject aos_mkt_pop_weekst = BusinessDataServiceHelper.newDynamicObject("aos_mkt_pop_weekst");
			aos_mkt_pop_weekst.set("aos_orgid", p_org_id);
			aos_mkt_pop_weekst.set("billstatus", "A");
			aos_mkt_pop_weekst.set("aos_statush", "A");
			aos_mkt_pop_weekst.set("aos_weekofyear", aos_weekofyear);
			aos_mkt_pop_weekst.set("aos_dayofweek", aos_dayofweek);
			aos_mkt_pop_weekst.set("aos_makedate", Today);
			aos_mkt_pop_weekst.set("aos_makeby", system);
			aos_mkt_pop_weekst.set("aos_groupid", group.get("id"));
			DynamicObjectCollection aos_entryentityS = aos_mkt_pop_weekst.getDynamicObjectCollection("aos_entryentity");
			DataSet Copy = aos_mkt_pop_ppcstS.copy();
			while (Copy.hasNext()) {
				String group_id = group.getString("id");
				Row aos_mkt_pop_ppcst = Copy.next();
				long item_id = aos_mkt_pop_ppcst.getLong("aos_itemid");
				// 销售组别
				String itemCategoryId = CommData.getItemCategoryId(item_id + "");
				if (itemCategoryId == null || "".equals(itemCategoryId)) {
					continue;
				}
				String salOrg = CommData.getSalOrgV2(p_org_id + "", itemCategoryId); // 小类获取组别
				if (salOrg == null || "".equals(salOrg)) {
					continue;
				}
				if (!salOrg.equals(group_id))
					continue;
				String orgid_str = p_org_id + "";
				String itemid_str = Long.toString(item_id);
				int aos_count = aos_mkt_pop_ppcst.getInteger("aos_count");
				String aos_itemnumer = aos_mkt_pop_ppcst.getString("aos_itemnumer");
				String aos_itemname = aos_mkt_pop_ppcst.getString("aos_itemname");
				String aos_season = aos_mkt_pop_ppcst.getString("aos_season");
				BigDecimal aos_impressions = BigDecimal.ZERO;
				if (KeyRpt7GroupExp.get(aos_itemnumer) != null)
					aos_impressions = KeyRpt7GroupExp.get(aos_itemnumer);
				if (aos_count > 10)
					continue;// 本期计算之后广告组可用词<=10个词
				if (aos_impressions.compareTo(EXPOSURE.multiply(BigDecimal.valueOf(0.7))) >= 0)
					continue;// 最近7天组SKU曝光<自动广告组曝光标准*0.7

				Object item_overseaqty = ItemOverseaQtyMap.get(item_id + "");// 海外库存
				if (item_overseaqty == null || item_overseaqty.equals("null"))
					item_overseaqty = 0;

				String category = (String) SalUtil.getCategoryByItemId(item_id + "").get("name");
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
				int availableDays = InStockAvailableDays.calInstockSalDays(orgid_str, itemid_str);

				DynamicObject aos_entryentity = aos_entryentityS.addNew();
				aos_entryentity.set("aos_itemid", item_id);
				aos_entryentity.set("aos_itemname", aos_itemname);
				aos_entryentity.set("aos_season", aos_season);
				aos_entryentity.set("aos_keyqty", aos_count);
				aos_entryentity.set("aos_avaqty", availableDays);
				aos_entryentity.set("aos_osqty", item_overseaqty);
				aos_entryentity.set("aos_category1", aos_category1);
				aos_entryentity.set("aos_category2", aos_category2);
				aos_entryentity.set("aos_category3", aos_category3);
				aos_entryentity.set("aos_itemnumer", aos_itemnumer);
			}
			Copy.close();

			// 子单据体
			for (DynamicObject aos_entryentity : aos_entryentityS) {
				DynamicObjectCollection aos_subentryentityS = aos_entryentity
						.getDynamicObjectCollection("aos_subentryentity");
				String aos_itemnumer = aos_entryentity.getString("aos_itemnumer");
				String aos_category1 = aos_entryentity.getString("aos_category1");
				String aos_category2 = aos_entryentity.getString("aos_category2");
				String aos_category3 = aos_entryentity.getString("aos_category3");
				String aos_itemname = aos_entryentity.getString("aos_itemname");

				Map<String, Object> KeyRptDetail = KeyRpt.get(aos_itemnumer);
				if (FndGlobal.IsNull(KeyRptDetail))
					KeyRptDetail = new HashMap<String, Object>();
				// 给词信息添加 物料品类关键词信息
				Map<String, String> itemPointDetail = itemPoint
						.get(aos_category1 + "~" + aos_category2 + "~" + aos_category3 + "~" + aos_itemname);
				if (FndGlobal.IsNull(itemPointDetail))
					itemPointDetail = new HashMap<String, String>();
				for (String key : itemPointDetail.keySet()) {
					KeyRptDetail.put(key + "~PHRASE", key + "~PHRASE");
					KeyRptDetail.put(key + "~EXACT", key + "~EXACT");
				}

				if (KeyRptDetail != null) {
					for (String aos_targetcomb : KeyRptDetail.keySet()) {
						String aos_keyword = aos_targetcomb.split("~")[0];
						String aos_match_type = aos_targetcomb.split("~")[1];
						// String aos_keystatus = StBaseDetail.get(aos_targetcomb) + "";
						DynamicObject aos_subentryentity = aos_subentryentityS.addNew();
						aos_subentryentity.set("aos_keyword", aos_keyword);
						aos_subentryentity.set("aos_match_type", aos_match_type);

						aos_subentryentity.set("aos_valid", true);
						aos_subentryentity.set("aos_status", "paused");

						String key = aos_itemnumer + "~" + aos_targetcomb;
						if (KeyRpt7Map.get(key) != null) {
							Map<String, Object> KeyRpt7MapD = KeyRpt7Map.get(key);
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
								aos_subentryentity.set("aos_express", aos_impressions);
								aos_subentryentity.set("aos_roi", KeyRpt7MapD.get("aos_roi"));
								aos_subentryentity.set("aos_rate", aos_exprate);
							}
						}
					}
				}
			}
			// 保存数据
			OperationServiceHelper.executeOperate("save", "aos_mkt_pop_weekst",
					new DynamicObject[] { aos_mkt_pop_weekst }, OperateOption.create());

		}

		aos_mkt_pop_ppcstS.close();
	}

}
