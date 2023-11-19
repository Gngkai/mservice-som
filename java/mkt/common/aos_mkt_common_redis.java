package mkt.common;

import common.fnd.FndGlobal;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.cache.CacheFactory;
import kd.bos.cache.DistributeSessionlessCache;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import org.apache.commons.lang3.SerializationUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

public class aos_mkt_common_redis {
	private static final DistributeSessionlessCache cache = CacheFactory.getCommonCacheFactory()
			.getDistributeSessionlessCache("mkt_redis");

	public static void init_redis(String type) {
		// if (redis_notneed()) return;
		if (type.equals("act")) {
			init_itemorg();// 国别物料维度
			init_datepara();// 物流周期表
		}
		if (type.equals("ppc")) {
			init_itemorg();// 国别物料维度
			init_datepara();// 物流周期表
			init_productid();// 店铺货号关联关系
			init_ppcdate();// ppc数据源最小系列与组日期
			init_poporg();// 营销国别参数
			init_salsummary();// 销售汇总表当月数据
			init_ppcyester();// ppc数据源昨日数据
			init_itemavadays();// 库存可售天数
			init_dailyprice();// 每日价格
			init_skurpt();// SKU推广报告7日汇总
			init_skurpt3();// SKU推广报告3日汇总
			init_skurpt14();// SKU推广报告14日汇总
			init_skurpt14Serial();// SKU推广报告14日系列
			init_ppcyester14();// PPC14日
			init_advpirce();// 建议价格
			init_skurptDetail();// SKU推广报告1日
			init_skurptSerial();// SKU推广报告7日系列
			init_skurpt3Serial();// SKU推广报告3日系列
			init_salsummaryserial();// 预测汇总表系列
			init_basestitem();// 国别物料滞销类型
			// 出价调整(销售)
			init_skurpt3avg();// 前三日平均
			init_skurpt7Detail();// SKU推广报告7日
			init_ppcyester7();// ppc数据源前7日数据
			init_advpirce7Day();// 建议价格前7日数据
			// 出价调整(预算)
			init_skurpt14Detail();// SKU推广报告14日
			init_advpirce3Day();// 建议价格近三天
			init_ppcyesterSerial();// PPC昨日 系列维度
			init_skurptDetailSerial();// SKU推广报告1日系列
			init_ppcdateSerial();

			init_skurptSerial14();
			init_skurpt14Detail_total();
		}
		if (type.equals("ppcadj")) {
			init_poporg();// 营销国别参数
			init_skurptDetail();// SKU推广报告1日
			init_ppcyester();// ppc数据源昨日数据
			init_advpirce3Day();// 建议价格近三天
			init_ppcdate();// ppc数据源最小系列与组日期
			init_skurpt3avg();// 前三日平均
			init_skurpt();// SKU推广报告7日汇总
			init_dailyprice();// 每日价格
			init_skurptDetailSerial();// SKU推广报告1日系列
			init_skurpt14Detail();// SKU推广报告14日
			init_ppcyester14();// PPC14日
		}
		if (type.equals("ppcadj_sal")) {
			init_skurpt3avg();// 前三日平均
			init_poporg();// 营销国别参数
			init_advpirce();// 建议价格
			init_skurptDetail();// SKU推广报告1日
			init_skurpt7Detail();// SKU推广报告7日
			init_ppcyester7();// ppc数据源前7日数据
			init_dailyprice();// 每日价格
			init_ppcyesterSerial();// PPC昨日 系列维度
			init_skurpt14Detail();// SKU推广报告14日
			init_ppcyester14();// PPC14日
		}
		if (type.equals("ppcbudget_p")) {
			init_skurptSerial();// SKU推广报告7日系列
			init_poporg();// 营销国别参数
			init_skurptDetailSerial();// SKU推广报告1日系列
			init_ppcyesterSerial();// PPC昨日数据系列维度
			init_ppcyesterSerial7();// ppc数据源前7日数据 系列维度
			init_skurpt7Serial();// SKU推广报告7日系列
		}
	}

	private static void init_skurpt7Serial() {
		HashMap<String, Map<String, Map<String, Object>>> SkuRpt = new HashMap<>();
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
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", date_to_str);
		QFilter filter_match = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_match };
		String SelectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku.aos_productno||'-AUTO' aos_productno,"
				+ "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_total_sales aos_total_sales,"
				+ "aos_entryentity.aos_date_l aos_date_l";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet(
				"aos_mkt_common_redis" + "." + "init_skurpt7Serial", "aos_base_skupoprpt", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_orgid", "aos_productno", "aos_date_l" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).sum("aos_total_sales").sum("aos_spend").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			Map<String, Object> Detail = new HashMap<>();
			Detail.put("aos_spend", aos_base_skupoprpt.get("aos_spend"));
			BigDecimal aos_roi = BigDecimal.ZERO;
			if (aos_base_skupoprpt.get("aos_spend") != null
					&& ((BigDecimal) aos_base_skupoprpt.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
				aos_roi = ((BigDecimal) aos_base_skupoprpt.get("aos_total_sales"))
						.divide((BigDecimal) aos_base_skupoprpt.get("aos_spend"), 6, BigDecimal.ROUND_HALF_UP);
			}
			Detail.put("aos_roi", aos_roi);
			Date aos_date_l = aos_base_skupoprpt.getDate("aos_date_l");
			String aos_date_str = DF.format(aos_date_l);
			Map<String, Map<String, Object>> Info = SkuRpt
					.get(aos_base_skupoprpt.getLong("aos_orgid") + "~" + aos_base_skupoprpt.getString("aos_productno"));
			if (Info == null)
				Info = new HashMap<>();
			Info.put(aos_date_str, Detail);
			SkuRpt.put(aos_base_skupoprpt.getLong("aos_orgid") + "~" + aos_base_skupoprpt.getString("aos_productno"),
					Info);
		}
		aos_base_skupoprptS.close();
		byte[] serialize_skurpt = SerializationUtils.serialize(SkuRpt);
		cache.put("mkt_skurpt7Serial", serialize_skurpt, 3600);
	}

	private static void init_ppcyesterSerial7() {
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
		QFilter filter_date_from = new QFilter("aos_date", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_date", "<=", date_to_str);
		QFilter Qf_type = new QFilter("aos_entryentity.aos_groupstatus", "=", "AVAILABLE");
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, Qf_type };
		String SelectColumn = "aos_orgid,aos_date," + "aos_entryentity.aos_budget aos_budget,"
				+ "aos_entryentity.aos_productno aos_productno,1 aos_skucount";
		DataSet aos_mkt_popular_ppcS = QueryServiceHelper.queryDataSet(
				"aos_mkt_common_redis" + "." + "init_ppcyesterSerial7", "aos_mkt_popular_ppc", SelectColumn, filters,
				null);
		String[] GroupBy = new String[] { "aos_orgid", "aos_date", "aos_productno", "aos_budget" };
		aos_mkt_popular_ppcS = aos_mkt_popular_ppcS.groupBy(GroupBy).sum("aos_skucount").finish();
		while (aos_mkt_popular_ppcS.hasNext()) {
			Row aos_mkt_popular_ppc = aos_mkt_popular_ppcS.next();
			Map<String, Object> Detail = new HashMap<>();
			Detail.put("aos_budget", aos_mkt_popular_ppc.get("aos_budget"));
			Detail.put("aos_skucount", aos_mkt_popular_ppc.get("aos_skucount"));
			Date aos_date = aos_mkt_popular_ppc.getDate("aos_date");
			String aos_date_str = DF.format(aos_date);

			Map<String, Map<String, Object>> Info = PpcYster7.get(
					aos_mkt_popular_ppc.getLong("aos_orgid") + "~" + aos_mkt_popular_ppc.getString("aos_productno"));
			if (Info == null)
				Info = new HashMap<>();

			Info.put(aos_date_str, Detail);
			PpcYster7.put(
					aos_mkt_popular_ppc.getLong("aos_orgid") + "~" + aos_mkt_popular_ppc.getString("aos_productno"),
					Info);
		}
		aos_mkt_popular_ppcS.close();
		byte[] serialize_ppcyster7 = SerializationUtils.serialize(PpcYster7);
		cache.put("mkt_ppcyster7Serial", serialize_ppcyster7, 3600);
	}

	private static void init_ppcyesterSerial() {
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
		QFilter Qf_type = new QFilter("aos_entryentity.aos_groupstatus", "=", "AVAILABLE");
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, Qf_type };
		String SelectColumn = "aos_orgid,aos_entryentity.aos_productno aos_productno,"
				+ "aos_entryentity.aos_budget aos_budget";
		DataSet aos_mkt_popular_ppcS = QueryServiceHelper.queryDataSet(
				"aos_mkt_common_redis" + "." + "init_ppcyesterSerial", "aos_mkt_popular_ppc", SelectColumn, filters,
				null);
		String[] GroupBy = new String[] { "aos_orgid", "aos_productno", "aos_budget" };
		aos_mkt_popular_ppcS = aos_mkt_popular_ppcS.groupBy(GroupBy).finish();
		while (aos_mkt_popular_ppcS.hasNext()) {
			Row aos_mkt_popular_ppc = aos_mkt_popular_ppcS.next();
			HashMap<String, Object> Info = new HashMap<>();
			Info.put("aos_budget", aos_mkt_popular_ppc.get("aos_budget"));// 上次预算
			PpcYester.put(
					aos_mkt_popular_ppc.getLong("aos_orgid") + "~" + aos_mkt_popular_ppc.getString("aos_productno"),
					Info);
		}
		aos_mkt_popular_ppcS.close();
		byte[] serialize_ppcYester = SerializationUtils.serialize(PpcYester);
		cache.put("mkt_ppcyestSerial", serialize_ppcYester, 3600);

	}

	private static void init_skurpt14Serial() {
		HashMap<String, Map<String, Object>> SkuRpt = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date aos_date = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -2);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -14);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		String aos_date_str = writeFormat.format(aos_date);
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", aos_date_str);
		QFilter filter_match = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_match };
		String SelectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku.aos_productno||'-AUTO' aos_productno,"
				+ "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_total_sales aos_total_sales,"
				+ "aos_entryentity.aos_impressions aos_impressions";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet(
				"aos_mkt_common_redis" + "." + "init_skurpt14Serial", "aos_base_skupoprpt", SelectColumn, filters,
				null);
		String[] GroupBy = new String[] { "aos_orgid", "aos_productno" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).sum("aos_spend").sum("aos_total_sales")
				.sum("aos_impressions").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			HashMap<String, Object> Info = new HashMap<>();
			Info.put("aos_spend", aos_base_skupoprpt.get("aos_spend"));
			Info.put("aos_total_sales", aos_base_skupoprpt.get("aos_total_sales"));
			Info.put("aos_impressions", aos_base_skupoprpt.get("aos_impressions"));
			SkuRpt.put(aos_base_skupoprpt.getLong("aos_orgid") + "~" + aos_base_skupoprpt.getString("aos_productno"),
					Info);
		}
		aos_base_skupoprptS.close();
		byte[] serialize_skurpt = SerializationUtils.serialize(SkuRpt);
		cache.put("mkt_skurpt14Serial", serialize_skurpt, 3600);
	}

	private static void init_salsummaryserial() {

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
		QFilter filter_type = new QFilter("aos_rsche_no", "=", "SKU调整表");
		QFilter filter_date_from = new QFilter("aos_month", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_month", "<", date_to_str);
		QFilter[] filters = new QFilter[] { filter_type, filter_date_from, filter_date_to };
		String SelectColumn = "aos_org aos_orgid," + "aos_sku.aos_productno||'-AUTO' aos_productno,"
				+ "aos_price * aos_sche_qty aos_sales," + "aos_sche_qty,createtime";
		DataSet aos_sal_sche_summaryS = QueryServiceHelper.queryDataSet(
				"aos_mkt_common_redis" + "." + "init_salsummaryserial", "aos_sal_sche_summary", SelectColumn, filters,
				null);
		String[] GroupBy = new String[] { "aos_orgid", "aos_productno" };
		aos_sal_sche_summaryS = aos_sal_sche_summaryS.groupBy(GroupBy).maxP("createtime", "aos_sales")
				.maxP("createtime", "aos_sche_qty").finish();
		while (aos_sal_sche_summaryS.hasNext()) {
			Row aos_sal_sche_summary = aos_sal_sche_summaryS.next();
			HashMap<String, Object> Info = new HashMap<>();
			Info.put("aos_sales", aos_sal_sche_summary.get("aos_sales"));
			Info.put("aos_sche_qty", aos_sal_sche_summary.get("aos_sche_qty"));
			SalSummary.put(aos_sal_sche_summary.get("aos_orgid") + "~" + aos_sal_sche_summary.get("aos_productno"),
					Info);
		}
		aos_sal_sche_summaryS.close();
		byte[] serialize_SalSummary = SerializationUtils.serialize(SalSummary);
		cache.put("mkt_salsummarySerial", serialize_SalSummary, 3600);
	}

	private static void init_skurptDetailSerial() {
		HashMap<String, Object> SkuRptDetail = new HashMap<>();
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
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<=", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", date_to_str);
		QFilter filter_match = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_match };
		String SelectColumn = "aos_orgid," + "aos_entryentity.aos_cam_name aos_productno,"
				+ "aos_entryentity.aos_spend aos_spend";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet(
				"aos_mkt_common_redis" + "." + "init_skurptDetail", "aos_base_skupoprpt", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_orgid", "aos_productno" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).sum("aos_spend").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			SkuRptDetail.put(
					aos_base_skupoprpt.getLong("aos_orgid") + "~" + aos_base_skupoprpt.getString("aos_productno"),
					aos_base_skupoprpt.getBigDecimal("aos_spend"));
		}
		aos_base_skupoprptS.close();
		byte[] serialize_skurptdetail = SerializationUtils.serialize(SkuRptDetail);
		cache.put("mkt_skurptDetailSerial", serialize_skurptdetail, 3600);
	}

	private static void init_skurpt3Serial() {
		HashMap<String, Map<String, Map<String, Object>>> SkuRpt = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);

		Date aos_date = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -2);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -3);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		String aos_datestr = writeFormat.format(aos_date);
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", aos_datestr);
		QFilter filter_match = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_match };
		String SelectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku.aos_productno||'-AUTO' aos_productno,"
				+ "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_total_sales aos_total_sales,"
				+ "aos_entryentity.aos_impressions aos_impressions," + "aos_entryentity.aos_date_l aos_date_l,"
				+ "aos_entryentity.aos_clicks aos_clicks";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet(
				"aos_mkt_common_redis" + "." + "init_skurpt14Detail", "aos_base_skupoprpt", SelectColumn, filters,
				null);
		String[] GroupBy = new String[] { "aos_orgid", "aos_productno", "aos_date_l" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).sum("aos_clicks").sum("aos_impressions")
				.sum("aos_spend").sum("aos_total_sales").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			Map<String, Object> Detail = new HashMap<>();
			Detail.put("aos_spend", aos_base_skupoprpt.get("aos_spend"));
			Detail.put("aos_total_sales", aos_base_skupoprpt.get("aos_total_sales"));
			Detail.put("aos_impressions", aos_base_skupoprpt.get("aos_impressions"));
			BigDecimal aos_roi = BigDecimal.ZERO;
			if (aos_base_skupoprpt.get("aos_spend") != null
					&& ((BigDecimal) aos_base_skupoprpt.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
				aos_roi = ((BigDecimal) aos_base_skupoprpt.get("aos_total_sales"))
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

			Map<String, Map<String, Object>> Info = SkuRpt
					.get(aos_base_skupoprpt.getLong("aos_orgid") + "~" + aos_base_skupoprpt.getString("aos_productno"));
			if (Info == null)
				Info = new HashMap<>();

			Info.put(aos_date_str, Detail);
			SkuRpt.put(aos_base_skupoprpt.getLong("aos_orgid") + "~" + aos_base_skupoprpt.getString("aos_productno"),
					Info);
		}
		aos_base_skupoprptS.close();
		byte[] serialize_skurpt = SerializationUtils.serialize(SkuRpt);
		cache.put("mkt_skurpt3Serial", serialize_skurpt, 3600);
	}

	private static void init_skurptSerial() {
		HashMap<String, Map<String, Object>> SkuRpt = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);

		Date aos_date = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -0);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -7);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		String aos_date_str = writeFormat.format(aos_date);
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", aos_date_str);
		QFilter filter_match = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_match };
		String SelectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku.aos_productno||'-AUTO' aos_productno,"
				+ "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_total_sales aos_total_sales,"
				+ "aos_entryentity.aos_clicks aos_clicks,aos_entryentity.aos_total_order aos_total_order";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_skurpt",
				"aos_base_skupoprpt", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_orgid", "aos_productno" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).sum("aos_spend").sum("aos_total_sales")
				.sum("aos_clicks").sum("aos_total_order").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			HashMap<String, Object> Info = new HashMap<>();
			Info.put("aos_spend", aos_base_skupoprpt.get("aos_spend"));
			Info.put("aos_total_sales", aos_base_skupoprpt.get("aos_total_sales"));

			Info.put("aos_clicks", aos_base_skupoprpt.get("aos_clicks"));
			Info.put("aos_total_order", aos_base_skupoprpt.get("aos_total_order"));

			SkuRpt.put(aos_base_skupoprpt.getLong("aos_orgid") + "~" + aos_base_skupoprpt.getString("aos_productno"),
					Info);
		}
		aos_base_skupoprptS.close();
		byte[] serialize_skurpt = SerializationUtils.serialize(SkuRpt);
		cache.put("mkt_skurptSerial7", serialize_skurpt, 3600);
	}

	private static void init_skurptSerial14() {
		HashMap<String, Map<String, Object>> SkuRpt = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date aos_date = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -2);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -14);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		String aos_date_str = writeFormat.format(aos_date);
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", aos_date_str);
		QFilter filter_match = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_match };
		String SelectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku.aos_productno||'-AUTO' aos_productno,"
				+ "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_total_sales aos_total_sales,"
				+ "aos_entryentity.aos_clicks aos_clicks,aos_entryentity.aos_total_order aos_total_order";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_skurpt",
				"aos_base_skupoprpt", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_orgid", "aos_productno" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).sum("aos_spend").sum("aos_total_sales")
				.sum("aos_clicks").sum("aos_total_order").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			HashMap<String, Object> Info = new HashMap<>();
			Info.put("aos_spend", aos_base_skupoprpt.get("aos_spend"));
			Info.put("aos_total_sales", aos_base_skupoprpt.get("aos_total_sales"));

			Info.put("aos_clicks", aos_base_skupoprpt.get("aos_clicks"));
			Info.put("aos_total_order", aos_base_skupoprpt.get("aos_total_order"));

			SkuRpt.put(aos_base_skupoprpt.getLong("aos_orgid") + "~" + aos_base_skupoprpt.getString("aos_productno"),
					Info);
		}
		aos_base_skupoprptS.close();
		byte[] serialize_skurpt = SerializationUtils.serialize(SkuRpt);
		cache.put("mkt_skurptSerial14", serialize_skurpt, 3600);
	}

	private static void init_ppcyester7() {
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
		QFilter filter_date_from = new QFilter("aos_date", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_date", "<=", date_to_str);
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to };
		String SelectColumn = "aos_orgid,aos_date," + "aos_entryentity.aos_itemid aos_itemid,"
				+ "aos_entryentity.aos_marketprice aos_marketprice," + "aos_entryentity.aos_budget aos_budget";
		DataSet aos_mkt_popular_ppcS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_ppcyester7",
				"aos_mkt_popular_ppc", SelectColumn, filters, null);
		while (aos_mkt_popular_ppcS.hasNext()) {
			Row aos_mkt_popular_ppc = aos_mkt_popular_ppcS.next();
			Map<String, Object> Detail = new HashMap<>();
			Detail.put("aos_marketprice", aos_mkt_popular_ppc.get("aos_marketprice"));
			Detail.put("aos_budget", aos_mkt_popular_ppc.get("aos_budget"));
			Date aos_date = aos_mkt_popular_ppc.getDate("aos_date");
			String aos_date_str = DF.format(aos_date);

			Map<String, Map<String, Object>> Info = PpcYster7
					.get(aos_mkt_popular_ppc.getLong("aos_orgid") + "~" + aos_mkt_popular_ppc.getLong("aos_itemid"));
			if (Info == null)
				Info = new HashMap<>();

			Info.put(aos_date_str, Detail);
			PpcYster7.put(aos_mkt_popular_ppc.getLong("aos_orgid") + "~" + aos_mkt_popular_ppc.getLong("aos_itemid"),
					Info);
		}
		aos_mkt_popular_ppcS.close();
		byte[] serialize_ppcyster7 = SerializationUtils.serialize(PpcYster7);
		cache.put("mkt_ppcyster7", serialize_ppcyster7, 3600);
	}

	private static void init_ppcyester14() {
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
		QFilter filter_date_from = new QFilter("aos_date", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_date", "<=", date_to_str);
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to };
		String SelectColumn = "aos_orgid,aos_date," + "aos_entryentity.aos_itemid aos_itemid,"
				+ "aos_entryentity.aos_marketprice aos_marketprice," + "aos_entryentity.aos_budget aos_budget";
		DataSet aos_mkt_popular_ppcS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_ppcyester7",
				"aos_mkt_popular_ppc", SelectColumn, filters, null);
		while (aos_mkt_popular_ppcS.hasNext()) {
			Row aos_mkt_popular_ppc = aos_mkt_popular_ppcS.next();
			Map<String, Object> Detail = new HashMap<>();
			Detail.put("aos_marketprice", aos_mkt_popular_ppc.get("aos_marketprice"));
			Detail.put("aos_budget", aos_mkt_popular_ppc.get("aos_budget"));
			Date aos_date = aos_mkt_popular_ppc.getDate("aos_date");
			String aos_date_str = DF.format(aos_date);

			Map<String, Map<String, Object>> Info = PpcYster7
					.get(aos_mkt_popular_ppc.getLong("aos_orgid") + "~" + aos_mkt_popular_ppc.getLong("aos_itemid"));
			if (Info == null)
				Info = new HashMap<>();

			Info.put(aos_date_str, Detail);
			PpcYster7.put(aos_mkt_popular_ppc.getLong("aos_orgid") + "~" + aos_mkt_popular_ppc.getLong("aos_itemid"),
					Info);
		}
		aos_mkt_popular_ppcS.close();
		byte[] serialize_ppcyster7 = SerializationUtils.serialize(PpcYster7);
		cache.put("mkt_ppcyster14", serialize_ppcyster7, 3600);
	}

	private static void init_skurpt7Detail() {
		HashMap<String, Map<String, Map<String, Object>>> SkuRpt7 = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date aos_date = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -2);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -7);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		String aos_datestr = writeFormat.format(aos_date);
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", aos_datestr);
		QFilter filter_match = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_match };
		String SelectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku aos_itemid,"
				+ "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_total_sales aos_total_sales,"
				+ "aos_entryentity.aos_impressions aos_impressions," + "aos_entryentity.aos_date_l aos_date_l,"
				+ "aos_entryentity.aos_clicks aos_clicks";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet(
				"aos_mkt_common_redis" + "." + "init_skurpt14Detail", "aos_base_skupoprpt", SelectColumn, filters,
				null);

		String[] GroupBy = new String[] { "aos_orgid", "aos_itemid", "aos_date_l" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).sum("aos_spend").sum("aos_total_sales")
				.sum("aos_impressions").sum("aos_clicks").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			Map<String, Object> Detail = new HashMap<>();
			Detail.put("aos_spend", aos_base_skupoprpt.get("aos_spend"));
			Detail.put("aos_total_sales", aos_base_skupoprpt.get("aos_total_sales"));
			Detail.put("aos_impressions", aos_base_skupoprpt.get("aos_impressions"));

			BigDecimal aos_roi = BigDecimal.ZERO;
			if (aos_base_skupoprpt.get("aos_spend") != null
					&& ((BigDecimal) aos_base_skupoprpt.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
				aos_roi = ((BigDecimal) aos_base_skupoprpt.get("aos_total_sales"))
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
			Map<String, Map<String, Object>> Info = SkuRpt7
					.get(aos_base_skupoprpt.getLong("aos_orgid") + "~" + aos_base_skupoprpt.getLong("aos_itemid"));
			if (Info == null)
				Info = new HashMap<>();
			Info.put(aos_date_str, Detail);
			SkuRpt7.put(aos_base_skupoprpt.getLong("aos_orgid") + "~" + aos_base_skupoprpt.getLong("aos_itemid"), Info);
		}
		aos_base_skupoprptS.close();
		byte[] serialize_skurpt7 = SerializationUtils.serialize(SkuRpt7);
		cache.put("mkt_skurpt7Detail", serialize_skurpt7, 3600);
	}

	private static void init_skurpt3avg() {
		HashMap<String, Map<String, Object>> SkuRpt3Avg = new HashMap<>();
		HashMap<String, Integer> qtyMap = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date aos_date = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -2);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -3);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		String aos_date_str = writeFormat.format(aos_date);
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", aos_date_str);
		QFilter filter_match = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_match};

		DataSet skuRptSetS =QueryServiceHelper.queryDataSet("aos_mkt_common_redis.qty",
				"aos_base_skupoprpt","aos_orgid,aos_entryentity.aos_ad_sku aos_itemid," +
						"aos_entryentity.aos_shopsku aos_shopsku",filters,null);
		skuRptSetS = skuRptSetS.distinct();
		while (skuRptSetS.hasNext()) {
			Row skuRptSet = skuRptSetS.next();
			String key = skuRptSet.getString("aos_orgid")+"~"+skuRptSet.getString("aos_itemid") ;
			if (FndGlobal.IsNull(qtyMap.get(key)))
				qtyMap.put(key,1);
			else
				qtyMap.put(key,qtyMap.get(key)+1);
		}
		skuRptSetS.close();

//		QFilter filter_equal =
//				new QFilter("aos_entryentity.aos_ad_name", QCP.equals, "aos_entryentity.aos_shopsku");
		String SelectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku aos_itemid,"
				+ "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_impressions aos_impressions";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_skurpt3avg",
				"aos_base_skupoprpt", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_orgid", "aos_itemid" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).avg("aos_spend").avg("aos_impressions").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			Map<String, Object> Info = new HashMap<>();
			String key = aos_base_skupoprpt.getLong("aos_orgid") + "~" + aos_base_skupoprpt.getLong("aos_itemid");
			Info.put("aos_spend", aos_base_skupoprpt.get("aos_spend"));
			BigDecimal aos_impressions = aos_base_skupoprpt.getBigDecimal("aos_impressions")
					.multiply(BigDecimal.valueOf(qtyMap.getOrDefault(key,1)));
			Info.put("aos_impressions", aos_impressions);
			SkuRpt3Avg.put(key,
					Info);
		}
		aos_base_skupoprptS.close();
		byte[] serialize_skurpt3avg = SerializationUtils.serialize(SkuRpt3Avg);
		cache.put("mkt_skurpt3avg", serialize_skurpt3avg, 3600);
	}

	private static void init_advpirce3Day() {
		HashMap<String, Map<String, Map<String, Object>>> AdvicePrice3Day = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -2);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		QFilter filter_date_from = new QFilter("aos_date", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_date", "<=", date_to_str);
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to };
		String SelectColumn = "aos_entryentity.aos_orgid aos_orgid," + "aos_entryentity.aos_ad_name aos_ad_name,"
				+ "aos_entryentity.aos_bid_suggest aos_bid_suggest,"
				+ "aos_entryentity.aos_bid_rangestart aos_bid_rangestart,"
				+ "aos_entryentity.aos_bid_rangeend aos_bid_rangeend," + "aos_date";
		DataSet aos_base_advrptS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_advpirce3Day",
				"aos_base_advrpt", SelectColumn, filters, null);
		while (aos_base_advrptS.hasNext()) {
			Row aos_base_advrpt = aos_base_advrptS.next();
			Map<String, Object> Detail = new HashMap<>();
			Detail.put("aos_bid_suggest", aos_base_advrpt.get("aos_bid_suggest"));
			Detail.put("aos_bid_rangestart", aos_base_advrpt.get("aos_bid_rangestart"));
			Detail.put("aos_bid_rangeend", aos_base_advrpt.get("aos_bid_rangeend"));
			Date aos_date = aos_base_advrpt.getDate("aos_date");
			String aos_date_str = DF.format(aos_date);
			Map<String, Map<String, Object>> Info = AdvicePrice3Day
					.get(aos_base_advrpt.getLong("aos_orgid") + "~" + aos_base_advrpt.getString("aos_ad_name"));
			if (Info == null)
				Info = new HashMap<>();
			Info.put(aos_date_str, Detail);
			AdvicePrice3Day.put(aos_base_advrpt.getLong("aos_orgid") + "~" + aos_base_advrpt.getString("aos_ad_name"),
					Info);
		}
		aos_base_advrptS.close();
		byte[] serialize_adviceprice3 = SerializationUtils.serialize(AdvicePrice3Day);
		cache.put("mkt_adviceprice3", serialize_adviceprice3, 3600);
	}

	private static void init_advpirce7Day() {
		HashMap<String, Map<String, Map<String, Object>>> AdvicePrice7Day = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -6);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		QFilter filter_date_from = new QFilter("aos_date", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_date", "<=", date_to_str);
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to };
		String SelectColumn = "aos_entryentity.aos_orgid aos_orgid," + "aos_entryentity.aos_ad_name aos_ad_name,"
				+ "aos_entryentity.aos_bid_suggest aos_bid_suggest,"
				+ "aos_entryentity.aos_bid_rangestart aos_bid_rangestart,"
				+ "aos_entryentity.aos_bid_rangeend aos_bid_rangeend," + "aos_date";
		DataSet aos_base_advrptS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_advpirce7Day",
				"aos_base_advrpt", SelectColumn, filters, null);
		while (aos_base_advrptS.hasNext()) {
			Row aos_base_advrpt = aos_base_advrptS.next();
			Map<String, Object> Detail = new HashMap<>();
			Detail.put("aos_bid_suggest", aos_base_advrpt.get("aos_bid_suggest"));
			Detail.put("aos_bid_rangestart", aos_base_advrpt.get("aos_bid_rangestart"));
			Detail.put("aos_bid_rangeend", aos_base_advrpt.get("aos_bid_rangeend"));
			Date aos_date = aos_base_advrpt.getDate("aos_date");
			String aos_date_str = DF.format(aos_date);
			Map<String, Map<String, Object>> Info = AdvicePrice7Day
					.get(aos_base_advrpt.getLong("aos_orgid") + "~" + aos_base_advrpt.getString("aos_ad_name"));
			if (Info == null)
				Info = new HashMap<>();
			Info.put(aos_date_str, Detail);
			AdvicePrice7Day.put(aos_base_advrpt.getLong("aos_orgid") + "~" + aos_base_advrpt.getString("aos_ad_name"),
					Info);
		}
		aos_base_advrptS.close();
		byte[] serialize_adviceprice7 = SerializationUtils.serialize(AdvicePrice7Day);
		cache.put("mkt_adviceprice7", serialize_adviceprice7, 3600);
	}

	private static void init_skurptDetail() {
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
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<=", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", date_to_str);
		QFilter filter_match = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_match };
		String SelectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku aos_itemid,"
				+ "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_impressions aos_impressions,"
				+ "aos_entryentity.aos_clicks aos_clicks";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet(
				"aos_mkt_common_redis" + "." + "init_skurptDetail", "aos_base_skupoprpt", SelectColumn, filters, null);

		String[] GroupBy = new String[] { "aos_orgid", "aos_itemid" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).sum("aos_spend").sum("aos_impressions")
				.sum("aos_clicks").finish();

		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			Map<String, Object> Info = new HashMap<>();
			Info.put("aos_spend", aos_base_skupoprpt.get("aos_spend"));
			Info.put("aos_impressions", aos_base_skupoprpt.get("aos_impressions"));
			Info.put("aos_clicks", aos_base_skupoprpt.get("aos_clicks"));
			SkuRptDetail.put(aos_base_skupoprpt.getLong("aos_orgid") + "~" + aos_base_skupoprpt.getLong("aos_itemid"),
					Info);
		}
		aos_base_skupoprptS.close();
		byte[] serialize_skurptdetail = SerializationUtils.serialize(SkuRptDetail);
		cache.put("mkt_skurptDetail", serialize_skurptdetail, 3600);
	}

	private static void init_advpirce() {
		HashMap<String, Map<String, Object>> AdPrice = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date aos_date = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String aos_date_str = writeFormat.format(aos_date);
		QFilter filter_date = new QFilter("aos_date", "=", aos_date_str);
		QFilter[] filters = new QFilter[] { filter_date };
		String SelectColumn = "aos_entryentity.aos_orgid aos_orgid," + "aos_entryentity.aos_ad_name aos_ad_name,"
				+ "aos_entryentity.aos_bid_suggest aos_bid_suggest,"
				+ "aos_entryentity.aos_bid_rangestart aos_bid_rangestart,"
				+ "aos_entryentity.aos_bid_rangeend aos_bid_rangeend";
		DataSet aos_base_advrptS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_advpirce",
				"aos_base_advrpt", SelectColumn, filters, null);
		while (aos_base_advrptS.hasNext()) {
			Row aos_base_advrpt = aos_base_advrptS.next();
			Map<String, Object> Info = new HashMap<>();
			Info.put("aos_bid_suggest", aos_base_advrpt.get("aos_bid_suggest"));
			Info.put("aos_bid_rangestart", aos_base_advrpt.get("aos_bid_rangestart"));
			Info.put("aos_bid_rangeend", aos_base_advrpt.get("aos_bid_rangeend"));
			AdPrice.put(aos_base_advrpt.getLong("aos_orgid") + "~" + aos_base_advrpt.getString("aos_ad_name"), Info);
		}
		aos_base_advrptS.close();
		byte[] serialize_adprice = SerializationUtils.serialize(AdPrice);
		cache.put("mkt_adprice", serialize_adprice, 3600);
	}

	private static void init_basestitem() {
		HashMap<String, Object> BaseStItem = new HashMap<>();
		String SelectColumn = "aos_orgid," + "aos_itemid," + "aos_type";
		DataSet aos_base_stiteS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_basestitem",
				"aos_base_stitem", SelectColumn, null, null);
		while (aos_base_stiteS.hasNext()) {
			Row aos_base_stite = aos_base_stiteS.next();
			BaseStItem.put(aos_base_stite.getLong("aos_orgid") + "~" + aos_base_stite.getLong("aos_itemid"),
					aos_base_stite.getString("aos_type"));
		}
		aos_base_stiteS.close();
		byte[] serialize_BaseStItem = SerializationUtils.serialize(BaseStItem);
		cache.put("mkt_basestitem", serialize_BaseStItem, 3600);
	}

	private static void init_skurpt14Detail() {
		HashMap<String, Map<String, Map<String, Object>>> SkuRpt = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);

		Date aos_date = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -2);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -14);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		String aos_datestr = writeFormat.format(aos_date);
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", aos_datestr);
		QFilter filter_match = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_match };
		String SelectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku aos_itemid,"
				+ "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_total_sales aos_total_sales,"
				+ "aos_entryentity.aos_impressions aos_impressions," + "aos_entryentity.aos_date_l aos_date_l,"
				+ "aos_entryentity.aos_clicks aos_clicks";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet(
				"aos_mkt_common_redis" + "." + "init_skurpt14Detail", "aos_base_skupoprpt", SelectColumn, filters,
				null);
		String[] GroupBy = new String[] { "aos_orgid", "aos_itemid", "aos_date_l" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).sum("aos_spend").sum("aos_total_sales")
				.sum("aos_impressions").sum("aos_clicks").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			Map<String, Object> Detail = new HashMap<>();
			Detail.put("aos_spend", aos_base_skupoprpt.get("aos_spend"));
			Detail.put("aos_total_sales", aos_base_skupoprpt.get("aos_total_sales"));
			Detail.put("aos_impressions", aos_base_skupoprpt.get("aos_impressions"));
			BigDecimal aos_roi = BigDecimal.ZERO;
			if (aos_base_skupoprpt.get("aos_spend") != null
					&& ((BigDecimal) aos_base_skupoprpt.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
				aos_roi = ((BigDecimal) aos_base_skupoprpt.get("aos_total_sales"))
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
			Map<String, Map<String, Object>> Info = SkuRpt
					.get(aos_base_skupoprpt.getLong("aos_orgid") + "~" + aos_base_skupoprpt.getLong("aos_itemid"));
			if (Info == null)
				Info = new HashMap<>();
			Info.put(aos_date_str, Detail);
			SkuRpt.put(aos_base_skupoprpt.getLong("aos_orgid") + "~" + aos_base_skupoprpt.getLong("aos_itemid"), Info);
		}
		aos_base_skupoprptS.close();
		byte[] serialize_skurpt = SerializationUtils.serialize(SkuRpt);
		cache.put("mkt_skurpt14Detail", serialize_skurpt, 3600);
	}
	
	private static void init_skurpt14Detail_total() {
		HashMap<String, Map<String, Map<String, Object>>> SkuRpt = new HashMap<>();
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
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<=", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", date_to_str);
		QFilter filter_match = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_match };
		String SelectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku aos_itemid,"
				+ "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_total_sales aos_total_sales,"
				+ "aos_entryentity.aos_impressions aos_impressions," + "aos_entryentity.aos_date_l aos_date_l,"
				+ "aos_entryentity.aos_clicks aos_clicks";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet(
				"aos_mkt_common_redis" + "." + "init_skurpt14Detail", "aos_base_skupoprpt", SelectColumn, filters,
				null);
		String[] GroupBy = new String[] { "aos_orgid", "aos_itemid", "aos_date_l" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).sum("aos_spend").sum("aos_total_sales")
				.sum("aos_impressions").sum("aos_clicks").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			Map<String, Object> Detail = new HashMap<>();
			Detail.put("aos_spend", aos_base_skupoprpt.get("aos_spend"));
			Detail.put("aos_total_sales", aos_base_skupoprpt.get("aos_total_sales"));
			Detail.put("aos_impressions", aos_base_skupoprpt.get("aos_impressions"));
			BigDecimal aos_roi = BigDecimal.ZERO;
			if (aos_base_skupoprpt.get("aos_spend") != null
					&& ((BigDecimal) aos_base_skupoprpt.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
				aos_roi = ((BigDecimal) aos_base_skupoprpt.get("aos_total_sales"))
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
			Map<String, Map<String, Object>> Info = SkuRpt
					.get(aos_base_skupoprpt.getLong("aos_orgid") + "~" + aos_base_skupoprpt.getLong("aos_itemid"));
			if (Info == null)
				Info = new HashMap<>();
			Info.put(aos_date_str, Detail);
			SkuRpt.put(aos_base_skupoprpt.getLong("aos_orgid") + "~" + aos_base_skupoprpt.getLong("aos_itemid"), Info);
		}
		aos_base_skupoprptS.close();
		byte[] serialize_skurpt = SerializationUtils.serialize(SkuRpt);
		cache.put("mkt_skurpt14Detail_total", serialize_skurpt, 3600);
	}

	private static void init_skurpt() {
		HashMap<String, Map<String, Object>> SkuRpt = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date aos_date = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -0);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -7);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		String aos_date_str = writeFormat.format(aos_date);
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", aos_date_str);
		QFilter filter_match = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_match };
		String SelectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku aos_itemid,"
				+ "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_total_sales aos_total_sales,"
				+ "aos_entryentity.aos_impressions aos_impressions," + "aos_entryentity.aos_clicks aos_clicks,"
				+ "aos_entryentity.aos_total_order aos_total_order";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_skurpt",
				"aos_base_skupoprpt", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_orgid", "aos_itemid" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).sum("aos_spend").sum("aos_total_sales")
				.sum("aos_impressions").sum("aos_clicks").sum("aos_total_order").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			HashMap<String, Object> Info = new HashMap<>();
			Info.put("aos_spend", aos_base_skupoprpt.get("aos_spend"));
			Info.put("aos_total_sales", aos_base_skupoprpt.get("aos_total_sales"));
			Info.put("aos_impressions", aos_base_skupoprpt.get("aos_impressions"));
			Info.put("aos_clicks", aos_base_skupoprpt.get("aos_clicks"));
			Info.put("aos_total_order", aos_base_skupoprpt.get("aos_total_order"));
			SkuRpt.put(aos_base_skupoprpt.getLong("aos_orgid") + "~" + aos_base_skupoprpt.getLong("aos_itemid"), Info);
		}
		aos_base_skupoprptS.close();
		byte[] serialize_skurpt = SerializationUtils.serialize(SkuRpt);
		cache.put("mkt_skurpt", serialize_skurpt, 3600);
	}

	private static void init_skurpt3() {
		HashMap<String, Map<String, Object>> SkuRpt = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date aos_date = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -0);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -3);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		String aos_date_str = writeFormat.format(aos_date);
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", aos_date_str);
		QFilter filter_match = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_match };
		String SelectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku aos_itemid,"
				+ "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_total_sales aos_total_sales,"
				+ "aos_entryentity.aos_impressions aos_impressions," + "aos_entryentity.aos_clicks aos_clicks";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_skurpt",
				"aos_base_skupoprpt", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_orgid", "aos_itemid" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).sum("aos_spend").sum("aos_total_sales")
				.sum("aos_impressions").sum("aos_clicks").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			HashMap<String, Object> Info = new HashMap<>();
			Info.put("aos_spend", aos_base_skupoprpt.get("aos_spend"));
			Info.put("aos_total_sales", aos_base_skupoprpt.get("aos_total_sales"));
			Info.put("aos_impressions", aos_base_skupoprpt.get("aos_impressions"));
			Info.put("aos_clicks", aos_base_skupoprpt.get("aos_clicks"));
			SkuRpt.put(aos_base_skupoprpt.getLong("aos_orgid") + "~" + aos_base_skupoprpt.getLong("aos_itemid"), Info);
		}
		aos_base_skupoprptS.close();
		byte[] serialize_skurpt = SerializationUtils.serialize(SkuRpt);
		cache.put("mkt_skurpt3", serialize_skurpt, 3600);
	}

	private static void init_skurpt14() {
		HashMap<String, Map<String, Object>> SkuRpt = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date aos_date = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -2);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -14);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		String aos_date_str = writeFormat.format(aos_date);
		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", aos_date_str);
		QFilter filter_match = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");

		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_match };
		String SelectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku aos_itemid,"
				+ "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_total_sales aos_total_sales,"
				+ "aos_entryentity.aos_clicks aos_clicks," + "aos_entryentity.aos_impressions aos_impressions,"
				+ "case when aos_entryentity.aos_impressions > 0 " + "then 1 "
				+ "when aos_entryentity.aos_impressions <= 0 " + "then 0 " + "end as aos_impcount,"
				+ "1 as aos_online ";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_skurpt14",
				"aos_base_skupoprpt", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_orgid", "aos_itemid" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).sum("aos_spend").sum("aos_total_sales")
				.sum("aos_clicks").sum("aos_impressions").sum("aos_impcount").sum("aos_online").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			HashMap<String, Object> Info = new HashMap<>();
			Info.put("aos_spend", aos_base_skupoprpt.get("aos_spend"));
			Info.put("aos_total_sales", aos_base_skupoprpt.get("aos_total_sales"));
			Info.put("aos_impressions", aos_base_skupoprpt.get("aos_impressions"));
			Info.put("aos_impcount", aos_base_skupoprpt.get("aos_impcount"));
			Info.put("aos_online", aos_base_skupoprpt.get("aos_online"));

			Info.put("aos_clicks", aos_base_skupoprpt.get("aos_clicks"));

			SkuRpt.put(aos_base_skupoprpt.getLong("aos_orgid") + "~" + aos_base_skupoprpt.getLong("aos_itemid"), Info);
		}
		aos_base_skupoprptS.close();
		byte[] serialize_skurpt = SerializationUtils.serialize(SkuRpt);
		cache.put("mkt_skurpt14", serialize_skurpt, 3600);
	}

	private static void init_dailyprice() {
		HashMap<String, Object> DailyPrice = new HashMap<>();
		String SelectColumn = "aos_orgid," + "aos_item_code aos_itemid," + "aos_currentprice";
		QFilter filter_match = new QFilter("aos_platformfid.number", "=", "AMAZON");
		QFilter[] filters = new QFilter[] { filter_match };
		DynamicObjectCollection aos_sync_invpriceS = QueryServiceHelper.query("aos_sync_invprice", SelectColumn,
				filters);
		for (DynamicObject aos_sync_invprice : aos_sync_invpriceS) {
			DailyPrice.put(aos_sync_invprice.getLong("aos_orgid") + "~" + aos_sync_invprice.getLong("aos_itemid"),
					aos_sync_invprice.get("aos_currentprice"));
		}
		byte[] serialize_DailyPrice = SerializationUtils.serialize(DailyPrice);
		cache.put("mkt_dailyprice", serialize_DailyPrice, 3600);
	}

	private static void init_itemavadays() {
		HashMap<String, Object> ItemAvaDays = new HashMap<>();
		String SelectColumn = "aos_ou_code," + "aos_item_code," + "aos_7avadays";
		DynamicObjectCollection aos_sal_dw_invavadayS = QueryServiceHelper.query("aos_sal_dw_invavadays", SelectColumn,
				null);
		for (DynamicObject aos_sal_dw_invavaday : aos_sal_dw_invavadayS) {
			ItemAvaDays.put(aos_sal_dw_invavaday.get("aos_ou_code") + "~" + aos_sal_dw_invavaday.get("aos_item_code"),
					aos_sal_dw_invavaday.get("aos_7avadays"));
		}
		byte[] serialize_ItemAvaDays = SerializationUtils.serialize(ItemAvaDays);
		cache.put("mkt_itemavadays", serialize_ItemAvaDays, 3600);
	}

	private static void init_ppcyester() {
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
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to };
		String SelectColumn = "aos_orgid," + "aos_entryentity.aos_itemid aos_itemid,"
				+ "aos_entryentity.aos_groupstatus aos_groupstatus," + "aos_entryentity.aos_budget aos_budget,"
				+ "aos_entryentity.aos_bid aos_bid," + "aos_entryentity.aos_lastpricedate aos_lastpricedate";
		DataSet aos_mkt_popular_ppcS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_ppcyester",
				"aos_mkt_popular_ppc", SelectColumn, filters, null);
		while (aos_mkt_popular_ppcS.hasNext()) {
			Row aos_mkt_popular_ppc = aos_mkt_popular_ppcS.next();
			HashMap<String, Object> Info = new HashMap<>();
			Info.put("aos_budget", aos_mkt_popular_ppc.get("aos_budget"));// 上次预算
			Info.put("aos_bid", aos_mkt_popular_ppc.get("aos_bid"));// 上次出价
			Info.put("aos_groupstatus", aos_mkt_popular_ppc.get("aos_groupstatus"));
			Info.put("aos_lastpricedate", aos_mkt_popular_ppc.get("aos_lastpricedate"));
			PpcYester.put(aos_mkt_popular_ppc.getLong("aos_orgid") + "~" + aos_mkt_popular_ppc.getLong("aos_itemid"),
					Info);
		}
		aos_mkt_popular_ppcS.close();
		byte[] serialize_ppcYester = SerializationUtils.serialize(PpcYester);
		cache.put("mkt_ppcyest", serialize_ppcYester, 3600);
	}

	private static void init_salsummary() {
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
		QFilter filter_type = new QFilter("aos_rsche_no", "=", "SKU调整表");
		QFilter filter_date_from = new QFilter("aos_month", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_month", "<", date_to_str);
		QFilter[] filters = new QFilter[] { filter_type, filter_date_from, filter_date_to };
		String SelectColumn = "aos_org aos_orgid," + "aos_sku aos_itemid," + "aos_price * aos_sche_qty aos_sales,"
				+ "aos_sche_qty";
		DynamicObjectCollection aos_sal_sche_summaryS = QueryServiceHelper.query("aos_sal_sche_summary", SelectColumn,
				filters, "createtime");
		for (DynamicObject aos_sal_sche_summary : aos_sal_sche_summaryS) {
			HashMap<String, Object> Info = new HashMap<>();
			Info.put("aos_sales", aos_sal_sche_summary.get("aos_sales"));
			Info.put("aos_sche_qty", aos_sal_sche_summary.get("aos_sche_qty"));
			SalSummary.put(aos_sal_sche_summary.get("aos_orgid") + "~" + aos_sal_sche_summary.get("aos_itemid"), Info);
		}
		byte[] serialize_SalSummary = SerializationUtils.serialize(SalSummary);
		cache.put("mkt_salsummary", serialize_SalSummary, 3600);
	}

	private static void init_poporg() {
		HashMap<String, Map<String, Object>> PopOrgInfo = new HashMap<>();
		String SelectColumn = "aos_orgid," + "aos_type," + "aos_value,aos_condition1";
		DataSet aos_mkt_base_orgvalueS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_poporg",
				"aos_mkt_base_orgvalue", SelectColumn, null, null);
		while (aos_mkt_base_orgvalueS.hasNext()) {
			Row aos_mkt_base_orgvalue = aos_mkt_base_orgvalueS.next();
			HashMap<String, Object> Info = new HashMap<>();
			Info.put("aos_value", aos_mkt_base_orgvalue.get("aos_value"));
			Info.put("aos_condition1", aos_mkt_base_orgvalue.get("aos_condition1"));
			PopOrgInfo.put(aos_mkt_base_orgvalue.getLong("aos_orgid") + "~" + aos_mkt_base_orgvalue.get("aos_type"),
					Info);
		}
		aos_mkt_base_orgvalueS.close();
		byte[] serialize_poporgInfo = SerializationUtils.serialize(PopOrgInfo);
		cache.put("mkt_poporginfo", serialize_poporgInfo, 3600);
	}

	private static void init_ppcdate() {
		HashMap<String, Map<String, Object>> PPCInfo = new HashMap<>();
		String SelectColumn = "aos_orgid," + "aos_entryentity.aos_itemid aos_itemid,"
				+ "aos_entryentity.aos_groupdate aos_groupdate";
		DataSet aos_mkt_popular_ppcS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_ppcdate",
				"aos_mkt_popular_ppc", SelectColumn, null, null);
		String[] GroupBy = new String[] { "aos_orgid", "aos_itemid" };
		aos_mkt_popular_ppcS = aos_mkt_popular_ppcS.groupBy(GroupBy).min("aos_groupdate").finish();
		while (aos_mkt_popular_ppcS.hasNext()) {
			Row aos_mkt_popular_ppc = aos_mkt_popular_ppcS.next();
			HashMap<String, Object> Info = new HashMap<>();
			Info.put("aos_groupdate", aos_mkt_popular_ppc.get("aos_groupdate"));
			PPCInfo.put(aos_mkt_popular_ppc.getLong("aos_orgid") + "~" + aos_mkt_popular_ppc.getLong("aos_itemid"),
					Info);
		}
		aos_mkt_popular_ppcS.close();
		byte[] serialize_ppcInfo = SerializationUtils.serialize(PPCInfo);
		cache.put("mkt_ppcinfo", serialize_ppcInfo, 3600);
	}

	private static void init_ppcdateSerial() {
		HashMap<String, Map<String, Object>> PPCInfo = new HashMap<>();
		String SelectColumn = "aos_orgid," + "aos_entryentity.aos_makedate aos_makedate,"
				+ "aos_entryentity.aos_productno aos_productno";
		DataSet aos_mkt_popular_ppcS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_ppcdate",
				"aos_mkt_popular_ppc", SelectColumn, null, null);
		String[] GroupBy = new String[] { "aos_orgid", "aos_productno" };
		aos_mkt_popular_ppcS = aos_mkt_popular_ppcS.groupBy(GroupBy).min("aos_makedate").finish();
		while (aos_mkt_popular_ppcS.hasNext()) {
			Row aos_mkt_popular_ppc = aos_mkt_popular_ppcS.next();
			HashMap<String, Object> Info = new HashMap<>();
			Info.put("aos_makedate", aos_mkt_popular_ppc.get("aos_makedate"));
			PPCInfo.put(aos_mkt_popular_ppc.getLong("aos_orgid") + "~" + aos_mkt_popular_ppc.getString("aos_productno"),
					Info);
		}
		aos_mkt_popular_ppcS.close();
		byte[] serialize_ppcInfo = SerializationUtils.serialize(PPCInfo);
		cache.put("mkt_ppcinfoSerial", serialize_ppcInfo, 3600);
	}

	private static void init_productid() {
		HashMap<String, String> ProductInfo = new HashMap<>();
		QFilter filter_ama = new QFilter("aos_platformfid.number", "=", "AMAZON");
		QFilter filter_mainshop = new QFilter("aos_shopfid.aos_is_mainshop", "=", true);
		QFilter[] filters = new QFilter[] { filter_ama, filter_mainshop };
		String SelectColumn = "aos_orgid aos_orgid," + "aos_item_code aos_itemid," + "aos_shopsku aos_productid";
		DynamicObjectCollection bd_materialS = QueryServiceHelper.query("aos_sync_invprice", SelectColumn, filters);
		for (DynamicObject bd_material : bd_materialS) {
			ProductInfo.put(bd_material.getLong("aos_orgid") + "~" + bd_material.getLong("aos_itemid"),
					bd_material.getString("aos_productid"));
		}
		byte[] serialize_ProductInfo = SerializationUtils.serialize(ProductInfo);
		cache.put("mkt_productinfo", serialize_ProductInfo, 3600);
	}

	private static void init_datepara() {
		HashMap<String, Map<String, Object>> datepara = new HashMap<>();
		datepara.put("aos_shp_day", init_aos_shp_day().get(0));
		datepara.put("aos_clear_day", init_aos_shp_day().get(1));
		datepara.put("aos_freight_day", init_aos_shp_day().get(2));
		byte[] serialize_datepara = SerializationUtils.serialize(datepara);
		cache.put("datepara", serialize_datepara, 3600);
	}

	private static List<Map<String, Object>> init_aos_shp_day() {
		List<Map<String, Object>> MapList = new ArrayList<Map<String, Object>>();
		Map<String, Object> aos_shpday_map = new HashMap<String, Object>();
		Map<String, Object> aos_clearday_map = new HashMap<String, Object>();
		Map<String, Object> aos_freightday_map = new HashMap<String, Object>();
		DynamicObjectCollection aos_scm_fcorder_paraS = QueryServiceHelper.query("aos_scm_fcorder_para",
				"aos_org.id aos_orgid,aos_shp_day,aos_clear_day,aos_freight_day", null);
		for (DynamicObject aos_scm_fcorder_para : aos_scm_fcorder_paraS) {
			String aos_orgid = aos_scm_fcorder_para.getString("aos_orgid");
			int aos_shp_day = aos_scm_fcorder_para.getInt("aos_shp_day");
			int aos_clear_day = aos_scm_fcorder_para.getInt("aos_clear_day");
			int aos_freight_day = aos_scm_fcorder_para.getInt("aos_freight_day");
			aos_shpday_map.put(aos_orgid, aos_shp_day);
			aos_clearday_map.put(aos_orgid, aos_clear_day);
			aos_freightday_map.put(aos_orgid, aos_freight_day);
		}
		MapList.add(aos_shpday_map);
		MapList.add(aos_clearday_map);
		MapList.add(aos_freightday_map);
		return MapList;
	}

	private static void init_itemorg() {
		HashMap<String, Map<String, Object>> item = new HashMap<>();
		item.put("item_maxage", init_item_maxage().get(0));// 最大库龄
		item.put("item_overseaqty", init_item_qty().get(0));// 海外库存数量
		item.put("item_intransqty", init_item_qty().get(1));// 在途数量
		byte[] serialize_item = SerializationUtils.serialize(item);
		cache.put("item", serialize_item, 3600);
	}

	private static List<Map<String, Object>> init_item_qty() {
		List<Map<String, Object>> MapList = new ArrayList<Map<String, Object>>();
		Map<String, Object> item_overseaqty = new HashMap<String, Object>();
		Map<String, Object> item_intransqty = new HashMap<String, Object>();
		DynamicObjectCollection aos_sync_invou_valueS = QueryServiceHelper.query("aos_sync_invou_value",
				"aos_ou.id aos_orgid,aos_item.id aos_itemid,(aos_noplatform_qty+aos_fba_qty) as aos_instock_qty,aos_intrans_qty",
				null);
		for (DynamicObject aos_sync_invou_value : aos_sync_invou_valueS) {
			String aos_orgid = aos_sync_invou_value.getString("aos_orgid");
			String aos_itemid = aos_sync_invou_value.getString("aos_itemid");
			int aos_instock_qty = aos_sync_invou_value.getInt("aos_instock_qty");
			int aos_intrans_qty = aos_sync_invou_value.getInt("aos_intrans_qty");
			item_overseaqty.put(aos_orgid + "~" + aos_itemid, aos_instock_qty);
			item_intransqty.put(aos_orgid + "~" + aos_itemid, aos_intrans_qty);
		}
		MapList.add(item_overseaqty);// 0 海外库存数量
		MapList.add(item_intransqty);// 1 在途数量
		return MapList;
	}

	private static List<Map<String, Object>> init_item_maxage() {
		List<Map<String, Object>> MapList = new ArrayList<Map<String, Object>>();
		Map<String, Object> item_maxage = new HashMap<String, Object>();
		DynamicObjectCollection aos_sync_itemageS = QueryServiceHelper.query("aos_sync_itemage",
				"aos_orgid.id aos_orgid,aos_itemid.id aos_itemid,aos_item_maxage", null);
		for (DynamicObject aos_sync_itemage : aos_sync_itemageS) {
			String aos_orgid = aos_sync_itemage.getString("aos_orgid");
			String aos_itemid = aos_sync_itemage.getString("aos_itemid");
			int aos_item_maxage = aos_sync_itemage.getInt("aos_item_maxage");
			item_maxage.put(aos_orgid + "~" + aos_itemid, aos_item_maxage);
		}
		MapList.add(item_maxage);// 0 最大库龄
		return MapList;
	}

}
