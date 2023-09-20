package mkt.popular.adjust_s;

import common.Cux_Common_Utl;
import common.sal.util.InStockAvailableDays;
import kd.bos.algo.DataSet;
import kd.bos.algo.JoinType;
import kd.bos.algo.Row;
import kd.bos.cache.CacheFactory;
import kd.bos.cache.DistributeSessionlessCache;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.operate.result.OperationResult;
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
import mkt.common.aos_mkt_common_redis;
import mkt.popular.adjust_p.aos_mkt_popadjp_init;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.time.DateUtils;
import sal.quote.CommData;
import sal.sche.aos_sal_sche_pub.aos_sal_sche_pub;
import sal.synciface.imp.aos_sal_import_pub;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class aos_mkt_popadjs_init extends AbstractTask {

	private static final DistributeSessionlessCache cache = CacheFactory.getCommonCacheFactory()
			.getDistributeSessionlessCache("mkt_redis");
	public final static String SYSTEM = Cux_Common_Utl.SYSTEM; // ID-000000

	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {

		// 初始化数据
		CommData.init();
		aos_mkt_common_redis.init_redis("ppc");
		
		long is_oversea_flag = aos_sal_sche_pub.get_lookup_values("AOS_YES_NO", "Y");
		QFilter oversea_flag = new QFilter("aos_is_oversea_ou", "=", is_oversea_flag);
		QFilter oversea_flag2 = new QFilter("aos_isomvalid", "=", true);
		QFilter[] filters_ou = new QFilter[] { oversea_flag, oversea_flag2 };
		DynamicObjectCollection bd_country = QueryServiceHelper.query("bd_country", "id,number", filters_ou);
		for (DynamicObject ou : bd_country) {
			param.put("p_ou_code", ou.get("number"));
			executerun(param);
		}
	}

	public static void ManualitemClick(Map<String, Object> param) {
		executerun(param);
	}

	public static void executerun(Map<String, Object> param) {
		Object p_ou_code = param.get("p_ou_code");
		// 删除数据
		Calendar today = Calendar.getInstance();
		today.set(Calendar.HOUR_OF_DAY, 0);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.SECOND, 0);
		today.set(Calendar.MILLISECOND, 0);
		Date TodayTime = today.getTime();
		QFilter filter_id = new QFilter("aos_orgid.number", "=", p_ou_code);
		QFilter filter_date = new QFilter("aos_makedate", "=", TodayTime);
		QFilter[] filters_adj = new QFilter[] { filter_id, filter_date };
		DeleteServiceHelper.delete("aos_mkt_popular_adjs", filters_adj);
		DeleteServiceHelper.delete("aos_mkt_popadjusts_data", filters_adj);
		// 初始化数据
		long is_oversea_flag = aos_sal_sche_pub.get_lookup_values("AOS_YES_NO", "Y");
		QFilter oversea_flag = new QFilter("aos_is_oversea_ou", "=", is_oversea_flag);
		QFilter oversea_flag2 = new QFilter("aos_isomvalid", "=", true);
		QFilter qf_ou = new QFilter("number", "=", p_ou_code);// 海外公司
		QFilter[] filters_ou = new QFilter[] { oversea_flag, oversea_flag2, qf_ou };
		DynamicObjectCollection bd_country = QueryServiceHelper.query("bd_country", "id,number", filters_ou);
		for (DynamicObject ou : bd_country) {
			QFilter group_org = new QFilter("aos_org.number", "=", ou.get("number"));// 海外公司
			QFilter group_enable = new QFilter("enable", "=", 1);// 可用
			QFilter[] filters_group = new QFilter[] { group_org, group_enable };
			DynamicObjectCollection aos_salgroup = QueryServiceHelper.query("aos_salgroup", "id", filters_group);
			for (DynamicObject group : aos_salgroup) {
				long group_id = group.getLong("id");
				Map<String, Object> params = new HashMap<>();
				params.put("p_ou_code", p_ou_code);
				params.put("p_group_id", group_id);
				do_operate(params);
			}
			// 全部执行完后生成 出价调整推广
			Map<String, Object> adjs = new HashMap<>();
			adjs.put("p_ou_code", p_ou_code);
			aos_mkt_popadjp_init.executerun(adjs);
		}
	}
	
	private static BigDecimal GetHighStandard(Object fixValue, BigDecimal HIGH1, BigDecimal HIGH2, BigDecimal HIGH3) {
		if (fixValue == null || ((BigDecimal) fixValue).compareTo(BigDecimal.valueOf(200)) < 0)
			return HIGH1;
		else if (((BigDecimal) fixValue).compareTo(BigDecimal.valueOf(200)) >= 0
				&& ((BigDecimal) fixValue).compareTo(BigDecimal.valueOf(500)) < 0)
			return HIGH2;
		else
			return HIGH3;
	}

	@SuppressWarnings("deprecation")
	public static void do_operate(Map<String, Object> params) {
		SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");
		String p_ou_code = (String) params.get("p_ou_code");
		long p_group_id = (long) params.get("p_group_id");
		Object p_org_id = aos_sal_import_pub.get_import_id(p_ou_code, "bd_country");
		byte[] serialize_adprice = cache.getByteValue("mkt_adprice"); // 建议价格
		HashMap<String, Map<String, Object>> AdPrice = SerializationUtils.deserialize(serialize_adprice);
		byte[] serialize_skurptdetail = cache.getByteValue("mkt_skurptDetail"); // SKU报告1日
		HashMap<String, Map<String, Object>> SkuRptDetail = SerializationUtils.deserialize(serialize_skurptdetail);
		byte[] serialize_skurpt7 = cache.getByteValue("mkt_skurpt14Detail"); // SKU报告14日
		HashMap<String, Map<String, Map<String, Object>>> SkuRpt7Detail = SerializationUtils
				.deserialize(serialize_skurpt7);
		byte[] serialize_ppcyster7 = cache.getByteValue("mkt_ppcyster14"); // PPC14日
		HashMap<String, Map<String, Map<String, Object>>> PpcYster7 = SerializationUtils
				.deserialize(serialize_ppcyster7);
		byte[] serialize_poporgInfo = cache.getByteValue("mkt_poporginfo"); // 营销国别参数表
		HashMap<String, Map<String, Object>> PopOrgInfo = SerializationUtils.deserialize(serialize_poporgInfo);
		byte[] serialize_skurpt3avg = cache.getByteValue("mkt_skurpt3avg"); // SKU报表三日平均
		HashMap<String, Map<String, Object>> SkuRpt3Avg = SerializationUtils.deserialize(serialize_skurpt3avg);
		byte[] serialize_DailyPrice = cache.getByteValue("mkt_dailyprice"); // Amazon每日价格
		HashMap<String, Object> DailyPrice = SerializationUtils.deserialize(serialize_DailyPrice);
		byte[] serialize_adviceprice7 = cache.getByteValue("mkt_adviceprice7"); // AD报告7日
		HashMap<String, Map<String, Map<String, Object>>> AdvicePrice7Day = SerializationUtils
				.deserialize(serialize_adviceprice7);
		byte[] serialize_ppcYesterSerial = cache.getByteValue("mkt_ppcyestSerial"); // PPC昨日数据 系列
		HashMap<String, Map<String, Object>> PpcYesterSerial = SerializationUtils
				.deserialize(serialize_ppcYesterSerial);
		byte[] serialize_skurptdetailSerial = cache.getByteValue("mkt_skurptDetailSerial"); // SKU报告1日
		HashMap<String, Map<String, Object>> SkuRptDetailSerial = SerializationUtils
				.deserialize(serialize_skurptdetailSerial);
		byte[] serialize_skurpt = cache.getByteValue("mkt_skurpt"); // SKU报告7日
		HashMap<String, Map<String, Object>> SkuRpt = SerializationUtils.deserialize(serialize_skurpt);
		byte[] serialize_ppcInfo = cache.getByteValue("mkt_ppcinfo"); // PPC系列与组创建日期
		HashMap<String, Map<String, Object>> PPCInfo = SerializationUtils.deserialize(serialize_ppcInfo);
		// 获取缓存
		byte[] serialize_item = cache.getByteValue("item");
		HashMap<String, Map<String, Object>> item = SerializationUtils.deserialize(serialize_item);
		Map<String, Object> item_overseaqty_map = item.get("item_overseaqty");// 海外在库数量
		HashMap<String, Integer> OrderMonth = AosMktGenerate.GenerateOrderMonth(p_ou_code);// 半月销量

		BigDecimal SkuExptandard = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "EXPOSURE").get("aos_value");// SKU曝光标准
		BigDecimal PopOrgRoist = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "ROIST").get("aos_value");// 国别标准ROI
		BigDecimal EXPOSURE = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "EXPOSURE").get("aos_value");// 国别曝光标准
		BigDecimal HIGH1 = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "HIGH1").get("aos_value");// 最高标准1(定价<200)
		BigDecimal HIGH2 = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "HIGH2").get("aos_value");// 最高标准2(200<=定价<500)
		BigDecimal HIGH3 = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "HIGH3").get("aos_value");// 最高标准3(定价>=500)
		BigDecimal SkuCostStandard = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "SKUCOST").get("aos_value");// SKU花费标准
		BigDecimal avgSales7Std = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "7AVGSALES").get("aos_value");// 前7天日均销量标准

		Date SummerSpringEnd = MKTCom.Get_DateRange("aos_dateto", "SS", p_org_id);
		Date AutumnWinterEnd = MKTCom.Get_DateRange("aos_dateto", "AW", p_org_id);
		// 判断是否季末
		Date summerSpringSeasonEnd = DateUtils.setDays(DateUtils.addDays(SummerSpringEnd, -32), 1);
		Date autumnWinterSeasonEnd = DateUtils.setDays(DateUtils.addDays(AutumnWinterEnd, -32), 1);

		Calendar today = Calendar.getInstance();
		today.set(Calendar.HOUR_OF_DAY, 0);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.SECOND, 0);
		today.set(Calendar.MILLISECOND, 0);
		Date Today = today.getTime();
		int year = today.get(Calendar.YEAR) - 2000;
		int month = today.get(Calendar.MONTH) + 1;
		int day = today.get(Calendar.DAY_OF_MONTH);

		// AM平台活动
		HashMap<String, Object> Act = GenerateAct();

		QFilter Qf_Date = new QFilter("aos_date", "=", Today);
		QFilter Qf_Org = new QFilter("aos_orgid.number", "=", p_ou_code);
		QFilter Qf_type = new QFilter("aos_entryentity.aos_groupstatus", "=", "AVAILABLE");
		QFilter Qf_group = new QFilter("aos_entryentity.aos_sal_group", "=", p_group_id);
		QFilter Qf_roi = new QFilter("aos_entryentity.aos_roiflag", "=", false);

		QFilter[] filters = new QFilter[] { Qf_Date, Qf_Org, Qf_type, Qf_group, Qf_roi };
		String SelectField = "aos_billno,aos_orgid," + "aos_orgid.number aos_orgnumber,"
				+ "aos_poptype,aos_entryentity.aos_productno aos_productno,"
				+ "aos_entryentity.aos_seasonseting aos_seasonseting,"
				+ "aos_entryentity.aos_contryentrystatus aos_contryentrystatus,"
				+ "aos_entryentity.aos_cn_name aos_cn_name," + "aos_entryentity.aos_itemnumer aos_itemnumer,"
				+ "aos_entryentity.aos_avadays aos_avadays," + "aos_entryentity.aos_bid aos_bid,"
				+ "aos_entryentity.aos_lastpricedate aos_lastpricedate," + "aos_entryentity.aos_lastbid aos_lastbid,"
				+ "aos_entryentity.aos_groupdate aos_groupdate," + "aos_entryentity.aos_roi7days aos_roi7days,"
				+ "aos_entryentity.aos_itemid aos_itemid," + "aos_entryentity.aos_sal_group aos_sal_group,"
				+ "aos_entryentity.id aos_ppcentryid," + "id," + "aos_entryentity.aos_highvalue aos_highvalue,"
				+ "aos_entryentity.aos_basestitem aos_basestitem";
		DynamicObjectCollection aos_mkt_popular_ppcS = QueryServiceHelper.query("aos_mkt_popular_ppc", SelectField,
				filters, "aos_entryentity.aos_productno");
		int rows = aos_mkt_popular_ppcS.size();
		int count = 0;
		if (rows == 0)
			return;
		DynamicObject Head = aos_mkt_popular_ppcS.get(0);
		DynamicObject aos_mkt_popular_adjs = BusinessDataServiceHelper.newDynamicObject("aos_mkt_popular_adjs");
		aos_mkt_popular_adjs.set("aos_billno", Head.get("aos_billno"));
		aos_mkt_popular_adjs.set("aos_ppcid", Head.get("id"));
		aos_mkt_popular_adjs.set("aos_orgid", p_org_id);
		aos_mkt_popular_adjs.set("billstatus", "A");
		aos_mkt_popular_adjs.set("aos_status", "A");
		aos_mkt_popular_adjs.set("aos_makeby", SYSTEM);
		aos_mkt_popular_adjs.set("aos_makedate", Today);
		aos_mkt_popular_adjs.set("aos_groupid", p_group_id);
		OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_popular_adjs",
				new DynamicObject[] { aos_mkt_popular_adjs }, OperateOption.create());
		Object aos_sourceid = operationrst.getSuccessPkIds().get(0);

		DynamicObject aos_mkt_popadjusts_data = BusinessDataServiceHelper.newDynamicObject("aos_mkt_popadjusts_data");
		aos_mkt_popadjusts_data.set("aos_billno", Head.get("aos_billno"));
		aos_mkt_popadjusts_data.set("aos_orgid", p_org_id);
		aos_mkt_popadjusts_data.set("billstatus", "A");
		aos_mkt_popadjusts_data.set("aos_groupid", p_group_id);
		aos_mkt_popadjusts_data.set("aos_sourceid", aos_sourceid);
		aos_mkt_popadjusts_data.set("aos_makedate", Today);
		DynamicObjectCollection aos_detailentryS = aos_mkt_popadjusts_data
				.getDynamicObjectCollection("aos_detailentry");

		DynamicObject aos_sync_log = BusinessDataServiceHelper.newDynamicObject("aos_sync_log");
		aos_sync_log.set("aos_type_code", "MKT_出价调整(销售)");
		aos_sync_log.set("aos_groupid", p_ou_code.toString() + year + month + day);
		aos_sync_log.set("billstatus", "A");
		DynamicObjectCollection aos_sync_logS = aos_sync_log.getDynamicObjectCollection("aos_entryentity");

		for (DynamicObject aos_mkt_popular_ppc : aos_mkt_popular_ppcS) {
			count++;

			// TODO 全部跳过
			if ("A".equals("A"))
				continue;

			// 数据层
			long item_id = aos_mkt_popular_ppc.getLong("aos_itemid");
			String aos_itemnumer = aos_mkt_popular_ppc.getString("aos_itemnumer");
			String aos_orgnumber = aos_mkt_popular_ppc.getString("aos_orgnumber");
			int aos_avadays = aos_mkt_popular_ppc.getInt("aos_avadays");
			BigDecimal aos_bid = aos_mkt_popular_ppc.getBigDecimal("aos_bid");
			String aos_basestitem = aos_mkt_popular_ppc.getString("aos_basestitem");// 滞销类型

			String aos_seasonseting = aos_mkt_popular_ppc.getString("aos_seasonseting");
			String aos_seasonpro = "";
			if ("春季产品".equals(aos_seasonseting)) {
				aos_seasonpro = "SPRING";
			}
			switch (aos_seasonseting) {
			case "春季产品":
				aos_seasonpro = "SPRING";
				break;
			case "春夏产品":
				aos_seasonpro = "SPRING_SUMMER";
				break;
			case "夏季产品":
				aos_seasonpro = "SUMMER";
				break;
			case "春夏常规品":
				aos_seasonpro = "SPRING-SUMMER-CONVENTIONAL";
				break;
			case "秋冬产品":
				aos_seasonpro = "AUTUMN_WINTER";
				break;
			case "冬季产品":
				aos_seasonpro = "WINTER";
				break;
			}

			Map<String, Object> SkuRpt3AvgMap = SkuRpt3Avg.get(p_org_id + "~" + item_id);
			BigDecimal Spend3Avg = BigDecimal.ZERO;
			BigDecimal Impress3Avg = BigDecimal.ZERO;
			if (SkuRpt3AvgMap != null) {
				Spend3Avg = (BigDecimal) SkuRpt3AvgMap.get("aos_spend");
				Impress3Avg = (BigDecimal) SkuRpt3AvgMap.get("aos_impressions");
			}
			aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS,
					aos_itemnumer + "三天平均曝光=" + Impress3Avg + " 国别标准=" + EXPOSURE);
			Map<String, Object> SkuRptDetailMap = SkuRptDetail.get(p_org_id + "~" + item_id);// Sku报告
			if (SkuRptDetailMap == null)
				continue;
			String category = CommData.getItemCategoryName(item_id + "");
			if (category == null || "".equals(category))
				continue;
			/*Map<String, Object> SkuRptDetailSerialMap = SkuRptDetailSerial
					.get(p_org_id + "~" + aos_mkt_popular_ppc.get("aos_productno"));// Sku报告系列
			if (SkuRptDetailSerialMap == null)
				continue;*/
			Map<String, Object> SkuRptMap = SkuRpt.get(p_org_id + "~" + item_id);// Sku报告
			BigDecimal Roi7Days = BigDecimal.ZERO;// 7天ROI
			if (SkuRptMap != null && ((BigDecimal) SkuRptMap.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
				Roi7Days = ((BigDecimal) SkuRptMap.get("aos_total_sales"))
						.divide((BigDecimal) SkuRptMap.get("aos_spend"), 2, BigDecimal.ROUND_HALF_UP);
			}
			Map<String, Object> PPCInfo_Map = PPCInfo.get(p_org_id + "~" + item_id);
			Object aos_groupdate = null; // 首次建组日期
			if (PPCInfo_Map != null)
				aos_groupdate = PPCInfo_Map.get("aos_groupdate");
			if (aos_groupdate == null)
				aos_groupdate = Today;

			// PPC数据源 出价调整(销售)筛选逻辑
			Boolean IsUnsalable = true; // 滞销品不剔除
			// 对于滞销品
			if ("NORMAL".equals(aos_basestitem) || "HARD".equals(aos_basestitem)) {
				if ((aos_avadays < 90) && (Spend3Avg.compareTo(SkuCostStandard) > 0))
					// 1.可售低花费高 可售天数＜90天(在途+在库数量计算可售天数)，前3天日平均花费≥国别标准SKU花费标准;
					continue;// 直接剔除留给营销调整出价
				else if ((get_between_days(Today, (Date) aos_groupdate) > 7)
						&& (Spend3Avg.compareTo(SkuCostStandard) > 0) && (Roi7Days.compareTo(PopOrgRoist) < 0)
						&& (Impress3Avg.compareTo(SkuExptandard) > 0))
					// 2.ROI差花费高 首次建组>7天，前3天日平均花费≥SKU花费标准(取参数表<SKU花费标准>)，且
					// 7天ROI＜国别ROI标准，且前3平均曝光>国别标准(取参数表<曝光标准>);
					continue;// 直接剔除留给营销调整出价
				else if ("AUTUMN_WINTER".equals(aos_seasonpro))
					continue;// 秋冬产品 直接剔除留给营销调整出价
				else if (!Cux_Common_Utl.IsNull(Act.get(p_org_id + "~" + item_id)))
					continue;// 未来七天有活动
				else
					IsUnsalable = false; // 其余滞销类型不做任何判断必进出价调整(销售)
			}

			if (IsUnsalable) {
				// 1.3天平均曝光<国别标准
				if (Impress3Avg.compareTo(EXPOSURE) >= 0)
					continue;
				// 2.计算出价<出价最高标准
				Object FixValue = DailyPrice.get(p_org_id + "~" + item_id); // 定价 Amazon每日价格
				BigDecimal HighStandard = GetHighStandard(FixValue, HIGH1, HIGH2, HIGH3);// 最高标准 根据国别定价筛选
				aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS,
						aos_itemnumer + "计算出价=" + aos_bid + " 最高标准=" + HighStandard);
				if (aos_bid.compareTo(HighStandard) >= 0)
					continue;
				// 3.只筛选0销量 低销量
				float R = InStockAvailableDays.getOrgItemOnlineAvgQty(p_org_id.toString(), String.valueOf(item_id));
				int halfMonthTotalSales = (int) Cux_Common_Utl.nvl(OrderMonth.get(item_id + ""));
				Boolean RegularUn = MKTCom.Get_RegularUn((long) p_org_id, item_id, aos_orgnumber, aos_avadays, R,
						halfMonthTotalSales);
				aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "非0销量低销量 标记=" + RegularUn);
				if (RegularUn) {
					continue;
				}

				// 前7天日均销量 < 国别标准
				if (BigDecimal.valueOf(R).compareTo(avgSales7Std) >= 0) {
					MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "前7天日均销量=" + R + " 国别标准=" + avgSales7Std);
					continue;
				}
			}

			// 判断是或否最近推广调价日期<= 3天
			Date aos_lastpricedate = aos_mkt_popular_ppc.getDate("aos_lastpricedate");
			// if (aos_lastpricedate != null) {
			// int between_days = get_between_days(Today, aos_lastpricedate);
			// if (between_days <= 3) {
			// MKTCom.Put_SyncLog(aos_sync_logS,
			// aos_itemnumer + " 最近调价日期" + aos_lastpricedate + "最近调价日期小于3天");
			// continue;
			// }
			// }

			// 判断季节品是否季末有剩余
			Object item_overseaqty = item_overseaqty_map.get(p_org_id + "~" + item_id);
			if (item_overseaqty == null || item_overseaqty.equals("null"))
				item_overseaqty = 0;
			if ("SPRING".equals(aos_seasonpro) || "SPRING_SUMMER".equals(aos_seasonpro)
					|| "SUMMER".equals(aos_seasonpro) || "SPRING-SUMMER-CONVENTIONAL".equals(aos_seasonpro)) {
				if (Today.after(summerSpringSeasonEnd)) {
					// 季末 判断是否达标
					float SeasonRate = MKTCom.Get_SeasonRate((long) p_org_id, item_id, aos_seasonpro, item_overseaqty,
							month);
					if (!MKTCom.Is_SeasonRate(aos_seasonpro, month, SeasonRate)) {
						MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "// 季末 判断是否达标");
						continue;
					}
				}
			}

			if ("AUTUMN_WINTER".equals(aos_seasonpro) || "WINTER".equals(aos_seasonpro)) {
				// 判断是否季末
				if (Today.after(autumnWinterSeasonEnd)) {
					// 季末 判断是否达标
					float SeasonRate = MKTCom.Get_SeasonRate((long) p_org_id, item_id, aos_seasonpro, item_overseaqty,
							month);
					if (!MKTCom.Is_SeasonRate(aos_seasonpro, month, SeasonRate)) {
						continue;
					}
				}
			}

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
			BigDecimal aos_bid_suggest = BigDecimal.ZERO;
			BigDecimal aos_bid_rangestart = BigDecimal.ZERO;
			BigDecimal aos_bid_rangeend = BigDecimal.ZERO;
			Map<String, Object> AdPriceMap = AdPrice.get(p_org_id + "~" + aos_itemnumer);// 组
			if (AdPriceMap != null) {
				aos_bid_suggest = (BigDecimal) AdPriceMap.get("aos_bid_suggest");
				aos_bid_rangestart = (BigDecimal) AdPriceMap.get("aos_bid_rangestart");
				aos_bid_rangeend = (BigDecimal) AdPriceMap.get("aos_bid_rangeend");
			}

			// 开始行赋值
			DynamicObject aos_detailentry = aos_detailentryS.addNew();
			aos_detailentry.set("aos_basestitem", aos_basestitem);// 滞销类型
			aos_detailentry.set("aos_productno", aos_mkt_popular_ppc.get("aos_productno"));
			aos_detailentry.set("aos_itemnumer", aos_itemnumer);
			aos_detailentry.set("aos_categroy1", aos_category1);
			aos_detailentry.set("aos_categroy2", aos_category2);
			aos_detailentry.set("aos_categroy3", aos_category3);
			aos_detailentry.set("aos_avadays", aos_mkt_popular_ppc.get("aos_avadays"));
			aos_detailentry.set("aos_contryentrystatus", aos_mkt_popular_ppc.get("aos_contryentrystatus"));
			aos_detailentry.set("aos_seasonseting", aos_mkt_popular_ppc.get("aos_seasonseting"));
			aos_detailentry.set("aos_cn_name", aos_mkt_popular_ppc.get("aos_cn_name"));
			aos_detailentry.set("aos_highvalue", aos_mkt_popular_ppc.get("aos_highvalue"));

			Object aos_activity = Act.get(p_org_id + "~" + item_id);
			if (aos_activity != null)
				aos_detailentry.set("aos_activity", aos_activity);

			aos_detailentry.set("aos_calprice", aos_mkt_popular_ppc.get("aos_bid"));
			aos_detailentry.set("aos_adjprice", aos_mkt_popular_ppc.get("aos_bid"));
			aos_detailentry.set("aos_lastdate", aos_lastpricedate);
			aos_detailentry.set("aos_bidsuggest", aos_bid_suggest);
			aos_detailentry.set("aos_lastprice", aos_mkt_popular_ppc.get("aos_lastbid"));

			// 设置比例
			BigDecimal big_temp = BigDecimal.ZERO;
			// 计算出价
			BigDecimal big_calprice = aos_mkt_popular_ppc.getBigDecimal("aos_bid");
			if (aos_bid_suggest.compareTo(BigDecimal.ZERO) != 0) {
				big_temp = big_calprice.divide(aos_bid_suggest, 5, BigDecimal.ROUND_HALF_UP);
			}
			aos_detailentry.set("aos_ratio", big_temp);
			aos_detailentry.set("aos_bidrangestart", aos_bid_rangestart);
			aos_detailentry.set("aos_bidrangeend", aos_bid_rangeend);
			aos_detailentry.set("aos_roi", aos_mkt_popular_ppc.get("aos_roi7days"));
			aos_detailentry.set("aos_itemid", aos_mkt_popular_ppc.get("aos_itemid"));
			aos_detailentry.set("aos_ppcentryid", aos_mkt_popular_ppc.get("aos_ppcentryid"));
			aos_detailentry.set("aos_expouse", Impress3Avg);
			
			BigDecimal aos_spend = BigDecimal.ZERO;
			Object LastSpendObj = SkuRptDetailSerial.get(p_org_id + "~" + aos_mkt_popular_ppc.get("aos_productno"));// Sku报告1日数据
			if (!Cux_Common_Utl.IsNull(LastSpendObj)) {
				aos_spend = (BigDecimal) LastSpendObj;
			}
			
			// 系列花出率 = 前一天花出/预算
			BigDecimal aos_serialrate = BigDecimal.ZERO;
			Map<String, Object> PpcYester_Map = PpcYesterSerial
					.get(p_org_id + "~" + aos_mkt_popular_ppc.get("aos_productno"));// 默认昨日数据 昨日没有则为空
			if (PpcYester_Map != null) {
				BigDecimal aos_budget = (BigDecimal) PpcYester_Map.get("aos_budget");// 昨日出价
				if (aos_budget.compareTo(BigDecimal.ZERO) != 0)
					aos_serialrate = aos_spend.divide(aos_budget, 2, BigDecimal.ROUND_HALF_UP);
			}
			aos_detailentry.set("aos_costrate", aos_serialrate);
			BigDecimal aos_groupcost = (BigDecimal) SkuRptDetailMap.get("aos_spend");
			aos_detailentry.set("aos_spend", aos_groupcost);
		}

		// 查找15天前的 ppc平台数据
		List<QFilter> list_qfs = new ArrayList<>(10); // 查询条件
		QFilter qf_status = new QFilter("aos_entryentity.aos_groupstatus", "=", "AVAILABLE"); // 状态可用
		list_qfs.add(qf_status);
		Calendar cal = new GregorianCalendar();
		try {
			cal.setTime(DF.parse(DF.format(today.getTime())));
		} catch (ParseException e1) {
			e1.printStackTrace();
		}
		cal.add(Calendar.DATE, 1); // 待删除
		QFilter qf_date_max = new QFilter("aos_date", "<", cal.getTime()); // 制单日期小于今天
		list_qfs.add(qf_date_max);
		cal.add(Calendar.DATE, -15);
		QFilter qf_date_min = new QFilter("aos_date", ">=", cal.getTime()); // 制单日期大于等于15天前
		list_qfs.add(qf_date_min);
		QFilter qf_org = new QFilter("aos_orgid", "=", p_org_id); // 国别
		list_qfs.add(qf_org);
		QFilter[] qfs = list_qfs.toArray(new QFilter[list_qfs.size()]);
		// 查询字段
		StringJoiner str = new StringJoiner(",");
		str.add("substring(aos_date,1,10) as aos_date");
		str.add("aos_entryentity.aos_itemid as aos_itemid"); // 物料
		str.add("aos_entryentity.aos_bid as aos_ppcbid"); // 出价
		str.add("aos_entryentity.aos_topprice as aos_topprice"); // 置顶位置出价
		DynamicObjectCollection dyc_ppc = QueryServiceHelper.query("aos_mkt_popular_ppc", str.toString(), qfs);
		Map<String, BigDecimal[]> map_ppc = dyc_ppc.stream()
				.collect(Collectors.toMap(e -> e.getString("aos_date") + "?" + e.getString("aos_itemid"), e -> {
					BigDecimal[] big = new BigDecimal[2];
					big[0] = BigDecimal.ZERO;
					if (e.getBigDecimal("aos_ppcbid") != null)
						big[0] = e.getBigDecimal("aos_ppcbid");
					big[1] = BigDecimal.ZERO;
					if (e.getBigDecimal("aos_topprice") != null) {
						big[1] = e.getBigDecimal("aos_topprice");
					}
					return big;
				}));
		for (DynamicObject aos_detailentry : aos_detailentryS) {
			try {
				long item_id = aos_detailentry.getLong("aos_itemid");
				Map<String, Map<String, Object>> SkuRptMap7 = SkuRpt7Detail.get(p_org_id + "~" + item_id);// Sku报告
				if (SkuRptMap7 == null)
					continue;
				Map<String, Map<String, Object>> PpcYster7Map = PpcYster7.get(p_org_id + "~" + item_id);// PPC7日
				if (PpcYster7Map == null)
					continue;
				DynamicObjectCollection aos_groupentryS = aos_detailentry.getDynamicObjectCollection("aos_groupentry");
				// 循环ROI
				for (String DateStr : SkuRptMap7.keySet()) {
					Map<String, Object> SkuRptMap7D = SkuRptMap7.get(DateStr);
					BigDecimal aos_expouse = (BigDecimal) SkuRptMap7D.get("aos_impressions");
					BigDecimal aos_roi = (BigDecimal) SkuRptMap7D.get("aos_roi");
					BigDecimal aos_bid = (BigDecimal) SkuRptMap7D.get("aos_bid");
					BigDecimal aos_cost = (BigDecimal) SkuRptMap7D.get("aos_spend");
					DynamicObject aos_groupentry = aos_groupentryS.addNew();
					Date aos_date_d = DF.parse(DateStr);
					aos_groupentry.set("aos_date_g", aos_date_d);
					aos_groupentry.set("aos_exp_g", aos_expouse);
					aos_groupentry.set("aos_roi_g", aos_roi);
					aos_groupentry.set("aos_bid_g", aos_bid);
					aos_groupentry.set("aos_spend_g", aos_cost);
					BigDecimal aos_budget_g = BigDecimal.ZERO;
					Map<String, Object> PpcYster7MapD = PpcYster7Map.get(DateStr);
					if (PpcYster7MapD != null)
						aos_budget_g = (BigDecimal) PpcYster7MapD.get("aos_budget");
					aos_groupentry.set("aos_budget_g", aos_budget_g);// 预算
					String key = DF.format(aos_date_d) + "?" + item_id;
					if (map_ppc.containsKey(key)) {
						BigDecimal[] value = map_ppc.get(key);
						aos_groupentry.set("aos_ppcbid", value[0]);
						aos_groupentry.set("aos_topprice", value[1]);
					}
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		for (DynamicObject aos_detailentry : aos_detailentryS) {
			try {
				String aos_itemnumer = aos_detailentry.getString("aos_itemnumer");
				Map<String, Map<String, Object>> AdvicePrice7DayMap = AdvicePrice7Day
						.get(p_org_id + "~" + aos_itemnumer);
				if (AdvicePrice7DayMap == null)
					continue;
				DynamicObjectCollection aos_marketentryS = aos_detailentry
						.getDynamicObjectCollection("aos_marketentry");
				// 循环ROI
				for (String DateStr : AdvicePrice7DayMap.keySet()) {
					Date aos_date_d = DF.parse(DateStr);
					Map<String, Object> AdvicePrice7DayMapD = AdvicePrice7DayMap.get(DateStr);
					BigDecimal aos_marketprice = BigDecimal.ZERO;
					if (AdvicePrice7DayMapD != null)
						aos_marketprice = (BigDecimal) AdvicePrice7DayMapD.get("aos_bid_suggest");
					DynamicObject aos_marketentry = aos_marketentryS.addNew();
					aos_marketentry.set("aos_date_p", aos_date_d);
					aos_marketentry.set("aos_marketprice", aos_marketprice);
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_popadjusts_data",
				new DynamicObject[] { aos_mkt_popadjusts_data }, OperateOption.create());
		// 保存日志表
		OperationResult operationrstLog = OperationServiceHelper.executeOperate("save", "aos_sync_log",
				new DynamicObject[] { aos_sync_log }, OperateOption.create());
		if (operationrstLog.getValidateResult().getValidateErrors().size() != 0) {
		}

	
		
			
	}

	private static int get_between_days(Date end_day, Date from_day) {
		Calendar cal = Calendar.getInstance();
		cal.setTime(end_day);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		end_day = cal.getTime();
		cal.setTime(from_day);
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MILLISECOND, 0);
		from_day = cal.getTime();
		int days = (int) ((end_day.getTime() - from_day.getTime()) / (1000 * 3600 * 24));
		return days;
	}

	public static HashMap<String, Object> GenerateAct() {
		HashMap<String, Object> Act = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		Date date_from = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, 7);
		Date date_to = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);

		QFilter filter_date1 = new QFilter("aos_sal_actplanentity.aos_l_startdate", ">=", date_from_str)
				.and("aos_sal_actplanentity.aos_l_startdate", "<=", date_to_str);
		QFilter filter_date2 = new QFilter("aos_sal_actplanentity.aos_enddate", ">=", date_from_str)
				.and("aos_sal_actplanentity.aos_enddate", "<=", date_to_str);
		QFilter filter_date3 = new QFilter("aos_sal_actplanentity.aos_l_startdate", "<=", date_from_str)
				.and("aos_sal_actplanentity.aos_enddate", ">=", date_to_str);
		QFilter filter_date = filter_date1.or(filter_date2).or(filter_date3);
		QFilter filter_ama = new QFilter("aos_channel.number", "=", "AMAZON");
		// QFilter filter_type = new QFilter("aos_acttype.aos_assessment.number", "=",
		// "Y");
		QFilter[] filters = new QFilter[] { filter_date, filter_ama };
		String Select = "aos_nationality.id aos_orgid,aos_shop,aos_acttype,aos_sal_actplanentity.aos_itemnum.id aos_itemid,"
				+ "aos_acttype.number aos_acttypeNumber,aos_sal_actplanentity.aos_l_startdate aos_l_startdate,aos_sal_actplanentity.aos_enddate aos_enddate";
		DataSet aos_act_select_planS = QueryServiceHelper.queryDataSet("aos_mkt_popadjs_init.GenerateAct",
				"aos_act_select_plan", Select, filters, "aos_makedate");

		// 查询店铺活动类型参数
		String selectFields = "aos_org,aos_shop,aos_acttype";
		DataSet aos_sal_act_type_pS = QueryServiceHelper.queryDataSet("aos_mkt_popadjs_init.GenerateAct",
				"aos_sal_act_type_p", selectFields,
				new QFilter[] { new QFilter("aos_assessment.number", QCP.equals, "Y") }, null);
		aos_act_select_planS = aos_act_select_planS.join(aos_sal_act_type_pS, JoinType.INNER).on("aos_orgid", "aos_org")
				.on("aos_shop", "aos_shop").on("aos_acttype", "aos_acttype")
				.select("aos_orgid", "aos_itemid", "aos_acttypeNumber", "aos_l_startdate", "aos_enddate").finish();
		String[] GroupBy = new String[] { "aos_orgid", "aos_itemid", "aos_acttypeNumber", "aos_l_startdate",
				"aos_enddate" };
		aos_act_select_planS = aos_act_select_planS.groupBy(GroupBy).finish();
		SimpleDateFormat sim = new SimpleDateFormat("MM-dd");
		while (aos_act_select_planS.hasNext()) {
			Row aos_act_select_plan = aos_act_select_planS.next();
			StringBuilder sign = new StringBuilder();
			if (aos_act_select_plan.get("aos_acttypeNumber") != null)
				sign.append(aos_act_select_plan.getString("aos_acttypeNumber"));
			sign.append("(");
			if (aos_act_select_plan.get("aos_l_startdate") != null)
				sign.append(sim.format(aos_act_select_plan.getDate("aos_l_startdate")));
			sign.append("-");
			if (aos_act_select_plan.get("aos_enddate") != null)
				sign.append(sim.format(aos_act_select_plan.getDate("aos_enddate")));
			sign.append(")");
			Act.put(aos_act_select_plan.getString("aos_orgid") + "~" + aos_act_select_plan.getLong("aos_itemid"),
					sign.toString());
		}
		aos_act_select_planS.close();
		aos_act_select_planS.close();
		return Act;
	}
}