package mkt.popular.adjust_p;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.lang3.SerializationUtils;

import common.Cux_Common_Utl;
import common.fnd.FndDate;
import common.fnd.FndGlobal;
import common.sal.util.SalUtil;
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
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import mkt.common.MKTCom;
import sal.synciface.imp.aos_sal_import_pub;

public class aos_mkt_popadjp_init extends AbstractTask {
	private static Log logger = LogFactory.getLog(aos_mkt_popadjp_init.class);

	private static final DistributeSessionlessCache cache = CacheFactory.getCommonCacheFactory()
			.getDistributeSessionlessCache("mkt_redis");

	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
	}

	public final static String SYSTEM = get_system_id(); // ID-000000

	public static String get_system_id() {
		QFilter qf_user = new QFilter("number", "=", "system");
		DynamicObject dy_user = QueryServiceHelper.queryOne("bos_user", "id", new QFilter[] { qf_user });
		String system_id = dy_user.get("id").toString();
		return system_id;
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

	@SuppressWarnings("deprecation")
	public static void executerun(Map<String, Object> param) {
		try {
			SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");
			Object p_ou_code = param.get("p_ou_code");
			// 删除数据
			Calendar today = Calendar.getInstance();
			today.set(Calendar.HOUR_OF_DAY, 0);
			today.set(Calendar.MINUTE, 0);
			today.set(Calendar.SECOND, 0);
			today.set(Calendar.MILLISECOND, 0);
			Date Today = today.getTime();
			int year = today.get(Calendar.YEAR) - 2000;
			int month = today.get(Calendar.MONTH) + 1;
			int day = today.get(Calendar.DAY_OF_MONTH);
			QFilter filter_id = new QFilter("aos_orgid.number", "=", p_ou_code);
			QFilter filter_date = new QFilter("aos_makedate", "=", Today);
			QFilter[] filters_adj = new QFilter[] { filter_id, filter_date };
			DeleteServiceHelper.delete("aos_mkt_popular_adjpn", filters_adj);
			DeleteServiceHelper.delete("aos_mkt_popadjustp_data", filters_adj);
			System.out.println("=====into 出价调整(推广)=====" + p_ou_code);
			Object p_org_id = aos_sal_import_pub.get_import_id(p_ou_code, "bd_country");

			byte[] serialize_skurpt14 = cache.getByteValue("mkt_skurpt14Detail_total"); // SKU报告14日
			HashMap<String, Map<String, Map<String, Object>>> SkuRpt14Detail = SerializationUtils
					.deserialize(serialize_skurpt14);
			
			
			
			byte[] serialize_skurptdetail = cache.getByteValue("mkt_skurptDetail"); // SKU报告1日
			HashMap<String, Map<String, Object>> SkuRptDetail = SerializationUtils.deserialize(serialize_skurptdetail);
			byte[] serialize_skurptdetailSerial = cache.getByteValue("mkt_skurptDetailSerial"); // SKU报告1日
			HashMap<String, Map<String, Object>> SkuRptDetailSerial = SerializationUtils
					.deserialize(serialize_skurptdetailSerial);
			byte[] serialize_ppcYesterSerial = cache.getByteValue("mkt_ppcyestSerial"); // PPC昨日数据 系列
			HashMap<String, Map<String, Object>> PpcYesterSerial = SerializationUtils
					.deserialize(serialize_ppcYesterSerial);
			byte[] serialize_adviceprice3 = cache.getByteValue("mkt_adviceprice3"); // 建议价格近三天
			HashMap<String, Map<String, Map<String, Object>>> AdvicePrice3Day = SerializationUtils
					.deserialize(serialize_adviceprice3);
			byte[] serialize_adprice = cache.getByteValue("mkt_adprice"); // 建议价格
			HashMap<String, Map<String, Object>> AdPrice = SerializationUtils.deserialize(serialize_adprice);

			byte[] serialize_ppcInfo = cache.getByteValue("mkt_ppcinfo"); // PPC系列与组创建日期
			HashMap<String, Map<String, Object>> PPCInfo = SerializationUtils.deserialize(serialize_ppcInfo);
			byte[] serialize_skurpt3avg = cache.getByteValue("mkt_skurpt3avg"); // SKU报表三日平均
			HashMap<String, Map<String, Object>> SkuRpt3Avg = SerializationUtils.deserialize(serialize_skurpt3avg);
			byte[] serialize_poporgInfo = cache.getByteValue("mkt_poporginfo"); // 营销国别参数表
			HashMap<String, Map<String, Object>> PopOrgInfo = SerializationUtils.deserialize(serialize_poporgInfo);
			byte[] serialize_skurpt = cache.getByteValue("mkt_skurpt"); // SKU报告7日
			HashMap<String, Map<String, Object>> SkuRpt = SerializationUtils.deserialize(serialize_skurpt);
			byte[] serialize_DailyPrice = cache.getByteValue("mkt_dailyprice"); // Amazon每日价格
			HashMap<String, Object> DailyPrice = SerializationUtils.deserialize(serialize_DailyPrice);
			BigDecimal SkuCostStandard = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "SKUCOST").get("aos_value");// SKU花费标准
			BigDecimal SkuExptandard = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "EXPOSURE").get("aos_value");// SKU曝光标准
			BigDecimal PopOrgRoist = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "ROIST").get("aos_value");// 国别标准ROI
			BigDecimal SKUEXP = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "SKUEXP").get("aos_value");// SKU曝光点击
			BigDecimal HIGH1 = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "HIGH1").get("aos_value");// 最高标准1(定价<200)
			BigDecimal HIGH2 = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "HIGH2").get("aos_value");// 最高标准2(200<=定价<500)
			BigDecimal HIGH3 = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "HIGH3").get("aos_value");// 最高标准3(定价>=500)

			HashMap<String, Object> Act = GenerateAct();
			HashMap<String, Object> Rank = GenerateRank(p_org_id);
			Map<String, Object> cateSeason = getCateSeason(p_ou_code); // aos_mkt_cateseason

			QFilter Qf_Date = new QFilter("aos_date", "=", Today);
			QFilter Qf_Org = new QFilter("aos_orgid.number", "=", p_ou_code);
			QFilter Qf_type = new QFilter("aos_entryentity.aos_groupstatus", "=", "AVAILABLE");
			QFilter Qf_roi = new QFilter("aos_entryentity.aos_roiflag", "=", false);
			QFilter[] filters = new QFilter[] { Qf_Date, Qf_Org, Qf_type, Qf_roi };
			String SelectField = "aos_billno,aos_orgid,aos_poptype,aos_entryentity.aos_productno aos_productno,"
					+ "aos_entryentity.aos_seasonseting aos_seasonseting,"
					+ "aos_entryentity.aos_contryentrystatus aos_contryentrystatus,"
					+ "aos_entryentity.aos_cn_name aos_cn_name," + "aos_entryentity.aos_itemnumer aos_itemnumer,"
					+ "aos_entryentity.aos_avadays aos_avadays," + "aos_entryentity.aos_bid aos_bid,"
					+ "aos_entryentity.aos_lastpricedate aos_lastpricedate,"
					+ "aos_entryentity.aos_lastbid aos_lastbid," + "aos_entryentity.aos_groupdate aos_groupdate,"
					+ "aos_entryentity.aos_roi7days aos_roi7days," + "aos_entryentity.aos_itemid aos_itemid,"
					+ "aos_entryentity.aos_sal_group aos_sal_group," + "aos_entryentity.id aos_ppcentryid," + "id,"
					+ "aos_entryentity.aos_highvalue aos_highvalue," + "aos_entryentity.aos_basestitem aos_basestitem,"
					+ "aos_entryentity.aos_age aos_age," + "aos_entryentity.aos_overseaqty aos_overseaqty,"
					+ "aos_entryentity.aos_is_saleout aos_is_saleout,"
					+ "aos_entryentity.aos_category1 aos_category1,aos_entryentity.aos_category2 aos_category2";
			DynamicObjectCollection aos_mkt_popular_ppcS = QueryServiceHelper.query("aos_mkt_popular_ppc", SelectField,
					filters, "aos_entryentity.aos_productno");
			int rows = aos_mkt_popular_ppcS.size();
			int count = 0;
			System.out.println("rows =" + rows);
			DynamicObject Head = aos_mkt_popular_ppcS.get(0);
			DynamicObject aos_mkt_popular_adjpn = BusinessDataServiceHelper.newDynamicObject("aos_mkt_popular_adjpn");
			aos_mkt_popular_adjpn.set("aos_billno", Head.get("aos_billno"));
			aos_mkt_popular_adjpn.set("aos_orgid", p_org_id);
			aos_mkt_popular_adjpn.set("billstatus", "A");
			aos_mkt_popular_adjpn.set("aos_status", "A");
			aos_mkt_popular_adjpn.set("aos_makeby", SYSTEM);
			aos_mkt_popular_adjpn.set("aos_makedate", Today);
			aos_mkt_popular_adjpn.set("aos_ppcid", Head.get("id"));

			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_popular_adjpn",
					new DynamicObject[] { aos_mkt_popular_adjpn }, OperateOption.create());
			Object aos_sourceid = operationrst.getSuccessPkIds().get(0);

			DynamicObject aos_mkt_popadjustp_data = BusinessDataServiceHelper
					.newDynamicObject("aos_mkt_popadjustp_data");
			aos_mkt_popadjustp_data.set("aos_billno", Head.get("aos_billno"));
			aos_mkt_popadjustp_data.set("aos_orgid", p_org_id);
			aos_mkt_popadjustp_data.set("billstatus", "A");
			aos_mkt_popadjustp_data.set("aos_sourceid", aos_sourceid);
			aos_mkt_popadjustp_data.set("aos_makedate", Today);

			// 获取 出价调整(销售)相同单号表中的物料 这些物料需要做排除 出价调整(销售)先生成 出价调整(推广)后生成
			QFilter qf_billno = new QFilter("aos_billno", "=", Head.get("aos_billno"));
			filters = new QFilter[] { qf_billno };
			SelectField = "aos_detailentry.aos_itemid aos_itemid";
			DynamicObjectCollection aos_mkt_popadjusts_dataS = QueryServiceHelper.query("aos_mkt_popadjusts_data",
					SelectField, filters);
			Map<String, Object> AdjustPMap = new HashMap<>();
			for (DynamicObject aos_mkt_popadjusts_data : aos_mkt_popadjusts_dataS) {
				String aos_itemid = aos_mkt_popadjusts_data.get("aos_itemid") + "";
				AdjustPMap.put(aos_itemid, aos_itemid);
			}

			DynamicObjectCollection aos_detailentryS = aos_mkt_popadjustp_data
					.getDynamicObjectCollection("aos_detailentry");

			DynamicObject aos_sync_log = BusinessDataServiceHelper.newDynamicObject("aos_sync_log");
			aos_sync_log.set("aos_type_code", "MKT_出价调整(推广)");
			aos_sync_log.set("aos_groupid", p_ou_code.toString() + year + month + day);
			aos_sync_log.set("billstatus", "A");
			DynamicObjectCollection aos_sync_logS = aos_sync_log.getDynamicObjectCollection("aos_entryentity");

			for (DynamicObject aos_mkt_popular_ppc : aos_mkt_popular_ppcS) {
				count++;
				System.out.println(p_ou_code + "进度" + "(" + count + "/" + rows + ")");
				long item_id = aos_mkt_popular_ppc.getLong("aos_itemid");
				String aos_itemnumer = aos_mkt_popular_ppc.getString("aos_itemnumer");

				if (AdjustPMap.get(item_id + "") != null) {
					aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "出价调整(销售)中存在跳过");
					continue;// 出价调整(销售)中如果存在则 出价调整(推广)不生成
				}

				Map<String, Object> SkuRptDetailMap = SkuRptDetail.get(p_org_id + "~" + item_id);// Sku报告
				BigDecimal aos_avgexp = BigDecimal.ZERO;
				BigDecimal aos_clicks = BigDecimal.ZERO;
				BigDecimal aos_groupcost = BigDecimal.ZERO;

				if (SkuRptDetailMap != null) {
					aos_avgexp = (BigDecimal) SkuRptDetailMap.get("aos_impressions");
					aos_clicks = (BigDecimal) SkuRptDetailMap.get("aos_clicks");
					aos_groupcost = (BigDecimal) SkuRptDetailMap.get("aos_spend");
				}

				BigDecimal aos_exprate = BigDecimal.ZERO;
				if (aos_avgexp.compareTo(BigDecimal.ZERO) != 0)
					aos_exprate = aos_clicks.divide(aos_avgexp, 6, BigDecimal.ROUND_HALF_UP);

				int aos_avadays = aos_mkt_popular_ppc.getInt("aos_avadays");
				BigDecimal aos_bid = aos_mkt_popular_ppc.getBigDecimal("aos_bid");
				String aos_basestitem = aos_mkt_popular_ppc.getString("aos_basestitem");
				// 对于类型进行计算
				Map<String, Object> PPCInfo_Map = PPCInfo.get(p_org_id + "~" + item_id);
				Object aos_groupdate = null; // 首次建组日期
				if (PPCInfo_Map != null)
					aos_groupdate = PPCInfo_Map.get("aos_groupdate");
				if (aos_groupdate == null)
					aos_groupdate = Today;

				Map<String, Object> SkuRpt3AvgMap = SkuRpt3Avg.get(p_org_id + "~" + item_id);
				BigDecimal Spend3Avg = BigDecimal.ZERO;
				BigDecimal Impress3Avg = BigDecimal.ZERO;
				if (SkuRpt3AvgMap != null) {
					Spend3Avg = (BigDecimal) SkuRpt3AvgMap.get("aos_spend");
					Impress3Avg = (BigDecimal) SkuRpt3AvgMap.get("aos_impressions");
				}
				Map<String, Object> SkuRptMap = SkuRpt.get(p_org_id + "~" + item_id);// Sku报告
				BigDecimal Roi7Days = BigDecimal.ZERO;// 7天ROI
				if (SkuRptMap != null && ((BigDecimal) SkuRptMap.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
					Roi7Days = ((BigDecimal) SkuRptMap.get("aos_total_sales"))
							.divide((BigDecimal) SkuRptMap.get("aos_spend"), 2, BigDecimal.ROUND_HALF_UP);
				}
				Object FixValue = DailyPrice.get(p_org_id + "~" + item_id); // 定价 Amazon每日价格
				BigDecimal HighStandard = GetHighStandard(FixValue, HIGH1, HIGH2, HIGH3);// 最高标准 根据国别定价筛选

				String aos_seasonseting = aos_mkt_popular_ppc.getString("aos_seasonseting");

				if ("HARD".equals(aos_basestitem))
					;// 严重滞销
				else if ("NORMAL".equals(aos_basestitem))
					;// 一般滞销
				else if ((aos_avadays < 90) && (Spend3Avg.compareTo(SkuCostStandard) > 0))
					// 1.可售低花费高 可售天数＜90天(在途+在库数量计算可售天数)，前3天日平均花费≥国别标准SKU花费标准;
					;
				else if ((get_between_days(Today, (Date) aos_groupdate) > 7)
						&& (Spend3Avg.multiply(BigDecimal.valueOf(3))
								.compareTo(SkuCostStandard.multiply(BigDecimal.valueOf(3))) > 0)
						&& (Roi7Days.compareTo(PopOrgRoist) < 0) && !"春夏产品".equals(aos_seasonseting)
						&& !"春夏常规品".equals(aos_seasonseting))
					// 2.ROI差花费高 首次建组>7天，前3天日每日花费≥SKU花费标准(取参数表<SKU花费标准>)，且
					// 7天ROI＜国别ROI标准;
					// 保留
					;
				else if ((get_between_days(Today, (Date) aos_groupdate) > 7)
						&& (Spend3Avg.multiply(BigDecimal.valueOf(3))
								.compareTo(SkuCostStandard.multiply(BigDecimal.valueOf(3))) > 0)
						&& (Roi7Days.compareTo(BigDecimal.valueOf(5)) < 0)
						&& ("春夏产品".equals(aos_seasonseting) || "春夏常规品".equals(aos_seasonseting)))
					// 保留
					;
				else if ((aos_avadays > 150)
						&& (Impress3Avg.compareTo(SkuExptandard.multiply(BigDecimal.valueOf(0.5))) < 0)
						&& (aos_bid.compareTo(HighStandard.multiply(BigDecimal.valueOf(0.5))) < 0))
					// 3.曝光低可售高
					// 可售天数＞150天(在途+在库数量计算)，前3平均曝光＜国别标准(取参数表<曝光标准>)*50%，出价<出价最高标准*50%(根据定价分别
					// 取参数表<出价最高标准>);
					;
				else if ((Roi7Days.compareTo(PopOrgRoist) > 0)
						&& (Impress3Avg.multiply(BigDecimal.valueOf(3)).compareTo(
								SkuExptandard.multiply(BigDecimal.valueOf(3)).multiply(BigDecimal.valueOf(0.5))) > 0)
						&& (Spend3Avg.multiply(BigDecimal.valueOf(3)).compareTo(
								SkuCostStandard.multiply(BigDecimal.valueOf(3)).multiply(BigDecimal.valueOf(0.2))) < 0)
						&& (aos_exprate.compareTo(SKUEXP) < 0))
					// 4.ROI好花费低
					// 7天ROI＞标准，前3曝光＞国别标准(取参数表<曝光标准>)，前3天日平均花费＜国别标准*20%，曝光点击率＜国别标准(取参数表<曝光点击>);
					// 保留
					;
				else
					// 5.其他默认为其他类型
					;

				DynamicObject aos_detailentry = aos_detailentryS.addNew();
				aos_detailentry.set("aos_poptype", Head.get("aos_poptype"));
				aos_detailentry.set("aos_productno", aos_mkt_popular_ppc.get("aos_productno"));
				aos_detailentry.set("aos_season", aos_seasonseting);
				aos_detailentry.set("aos_itemstatus", aos_mkt_popular_ppc.get("aos_contryentrystatus"));
				aos_detailentry.set("aos_itemname", aos_mkt_popular_ppc.get("aos_cn_name"));
				aos_detailentry.set("aos_itemnumer", aos_mkt_popular_ppc.get("aos_itemnumer"));
				aos_detailentry.set("aos_avadays", aos_mkt_popular_ppc.getInt("aos_avadays"));
				aos_detailentry.set("aos_calprice", aos_mkt_popular_ppc.get("aos_bid"));
				aos_detailentry.set("aos_adjprice", aos_mkt_popular_ppc.get("aos_bid"));
				aos_detailentry.set("aos_lastdate", aos_mkt_popular_ppc.get("aos_lastpricedate"));
				aos_detailentry.set("aos_lastprice", aos_mkt_popular_ppc.get("aos_lastbid"));
				aos_detailentry.set("aos_grouproi", aos_mkt_popular_ppc.get("aos_roi7days"));
				aos_detailentry.set("aos_itemid", item_id);
				aos_detailentry.set("aos_sal_group", aos_mkt_popular_ppc.get("aos_sal_group"));
				aos_detailentry.set("aos_ppcentryid", aos_mkt_popular_ppc.get("aos_ppcentryid"));
				aos_detailentry.set("aos_highvalue", aos_mkt_popular_ppc.get("aos_highvalue"));// 最高价
				aos_detailentry.set("aos_basestitem", aos_mkt_popular_ppc.get("aos_basestitem"));// 滞销类型
				aos_detailentry.set("aos_age", aos_mkt_popular_ppc.get("aos_age"));// 最大库龄
				aos_detailentry.set("aos_is_saleout", aos_mkt_popular_ppc.get("aos_is_saleout"));
				aos_detailentry.set("aos_overseaqty", aos_mkt_popular_ppc.get("aos_overseaqty"));

				String aos_category1 = aos_mkt_popular_ppc.getString("aos_category1");
				String aos_category2 = aos_mkt_popular_ppc.getString("aos_category2");
				Object aos_seasonattr = cateSeason.getOrDefault(aos_category1 + "~" + aos_category2, null);
				aos_detailentry.set("aos_seasonattr", aos_seasonattr);

				if (Rank != null)
					aos_detailentry.set("aos_rank", Rank.get(item_id + ""));// AM搜索排名

				Object aos_activity = Act.get(p_org_id + "~" + item_id);
				if (aos_activity != null)
					aos_detailentry.set("aos_activity", aos_activity);

				BigDecimal aos_bid_suggest = BigDecimal.ZERO;
				Map<String, Object> AdPriceMap = AdPrice.get(p_org_id + "~" + aos_itemnumer);// 组
				if (AdPriceMap != null) {
					aos_bid_suggest = (BigDecimal) AdPriceMap.get("aos_bid_suggest");
				}
				aos_detailentry.set("aos_bidsuggest", aos_bid_suggest);// 建议价

				if (SkuRpt3AvgMap != null)
					Impress3Avg = (BigDecimal) SkuRpt3AvgMap.get("aos_impressions");
				aos_detailentry.set("aos_avgexp", Impress3Avg);

				aos_detailentry.set("aos_exprate", aos_exprate);
				aos_detailentry.set("aos_groupcost", aos_groupcost);

				BigDecimal aos_spend = BigDecimal.ZERO;
				Object LastSpendObj = SkuRptDetailSerial.get(p_org_id + "~" + aos_mkt_popular_ppc.get("aos_productno"));// Sku报告1日数据
				if (!Cux_Common_Utl.IsNull(LastSpendObj)) {
					aos_spend = (BigDecimal) LastSpendObj;
				}

				BigDecimal aos_serialrate = BigDecimal.ZERO;

				// 系列花出率 = 前一天花出/预算
				Map<String, Object> PpcYester_Map = PpcYesterSerial
						.get(p_org_id + "~" + aos_mkt_popular_ppc.get("aos_productno"));// 默认昨日数据 昨日没有则为空
				if (PpcYester_Map != null) {
					BigDecimal aos_budget = (BigDecimal) PpcYester_Map.get("aos_budget");// 昨日出价
					if (aos_budget.compareTo(BigDecimal.ZERO) != 0)
						aos_serialrate = aos_spend.divide(aos_budget, 2, BigDecimal.ROUND_HALF_UP);
				}
				aos_detailentry.set("aos_serialrate", aos_serialrate);

				// 系统建议策略 aos_sys
				BigDecimal aos_lastprice = aos_detailentry.getBigDecimal("aos_lastprice");
				BigDecimal aos_calprice = aos_detailentry.getBigDecimal("aos_calprice");

				if (aos_lastprice.compareTo(BigDecimal.ZERO) != 0 && aos_calprice.compareTo(aos_lastprice) > 0)
					aos_detailentry.set("aos_sys", "提出价");
				if (aos_calprice.compareTo(aos_lastprice) < 0) {
					aos_detailentry.set("aos_sys", "降出价");
				}

			}

			for (DynamicObject aos_detailentry : aos_detailentryS) {
				try {
					String aos_itemnumer = aos_detailentry.getString("aos_itemnumer");
					Map<String, Map<String, Object>> AdvicePrice3DayMap = AdvicePrice3Day
							.get(p_org_id + "~" + aos_itemnumer);// Sku报告
					if (AdvicePrice3DayMap == null)
						continue;

					DynamicObjectCollection aos_subentryentityS = aos_detailentry
							.getDynamicObjectCollection("aos_subentryentity1");
					// 循环ROI
					for (String DateStr : AdvicePrice3DayMap.keySet()) {
						Map<String, Object> AdvicePrice3DayMapD = AdvicePrice3DayMap.get(DateStr);
						BigDecimal aos_bid_suggest = (BigDecimal) AdvicePrice3DayMapD.get("aos_bid_suggest");
						BigDecimal aos_bid_rangestart = (BigDecimal) AdvicePrice3DayMapD.get("aos_bid_rangestart");
						BigDecimal aos_bid_rangeend = (BigDecimal) AdvicePrice3DayMapD.get("aos_bid_rangeend");
						DynamicObject aos_subentryentity = aos_subentryentityS.addNew();
						Date aos_date_d2 = DF.parse(DateStr);
						aos_subentryentity.set("aos_date_d2", aos_date_d2);
						aos_subentryentity.set("aos_bid_suggest", aos_bid_suggest);
						aos_subentryentity.set("aos_bid_rangestart", aos_bid_rangestart);
						aos_subentryentity.set("aos_bid_rangeend", aos_bid_rangeend);
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}

			// 组数据( 15天 ) 循环 单据体循环

			// 查找15天前的 ppc平台数据
			List<QFilter> list_qfs = new ArrayList<>(10); // 查询条件
			QFilter qf_status = new QFilter("aos_entryentity.aos_groupstatus", "=", "AVAILABLE"); // 状态可用
			list_qfs.add(qf_status);
			Calendar cal = new GregorianCalendar();
			cal.setTime(DF.parse(DF.format(today.getTime())));
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
					Map<String, Map<String, Object>> SkuRptMap14 = SkuRpt14Detail.get(p_org_id + "~" + item_id);// Sku报告
					if (SkuRptMap14 == null)
						continue;
					DynamicObjectCollection aos_subentryentityS = aos_detailentry
							.getDynamicObjectCollection("aos_subentryentity");
					// 循环ROI
					for (String DateStr : SkuRptMap14.keySet()) {
						Map<String, Object> SkuRptMap14D = SkuRptMap14.get(DateStr);
						BigDecimal aos_expouse = (BigDecimal) SkuRptMap14D.get("aos_impressions");
						BigDecimal aos_roi = (BigDecimal) SkuRptMap14D.get("aos_roi");
						BigDecimal aos_bid = (BigDecimal) SkuRptMap14D.get("aos_bid");
						BigDecimal aos_cost = (BigDecimal) SkuRptMap14D.get("aos_spend");
						DynamicObject aos_subentryentity = aos_subentryentityS.addNew();
						Date aos_date_d = DF.parse(DateStr);
						aos_subentryentity.set("aos_date_d", aos_date_d);
						aos_subentryentity.set("aos_expouse", aos_expouse);
						aos_subentryentity.set("aos_roi", aos_roi);
						aos_subentryentity.set("aos_bid", aos_bid);
						aos_subentryentity.set("aos_cost", aos_cost);

						String key = DF.format(aos_date_d) + "?" + item_id;
						if (map_ppc.containsKey(key)) {
							BigDecimal[] value = map_ppc.get(key);
							aos_subentryentity.set("aos_ppcbid", value[0]);
							aos_subentryentity.set("aos_topprice", value[1]);
						}
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}

			for (DynamicObject aos_detailentry : aos_detailentryS) {
				String aos_itemnumer = aos_detailentry.getString("aos_itemnumer");
				int aos_avadays = aos_detailentry.getInt("aos_avadays");
				long item_id = aos_detailentry.getLong("aos_itemid");
				BigDecimal aos_bid = aos_detailentry.getBigDecimal("aos_calprice");
				BigDecimal aos_exprate = aos_detailentry.getBigDecimal("aos_exprate");

				String aos_seasonseting = aos_detailentry.getString("aos_season");

				// 对于类型进行计算
				Map<String, Object> PPCInfo_Map = PPCInfo.get(p_org_id + "~" + item_id);
				Object aos_groupdate = null; // 首次建组日期
				if (PPCInfo_Map != null)
					aos_groupdate = PPCInfo_Map.get("aos_groupdate");
				if (aos_groupdate == null)
					aos_groupdate = Today;
				Map<String, Object> SkuRpt3AvgMap = SkuRpt3Avg.get(p_org_id + "~" + item_id);
				BigDecimal Spend3Avg = BigDecimal.ZERO;
				BigDecimal Impress3Avg = BigDecimal.ZERO;
				if (SkuRpt3AvgMap != null) {
					Spend3Avg = (BigDecimal) SkuRpt3AvgMap.get("aos_spend");
					Impress3Avg = (BigDecimal) SkuRpt3AvgMap.get("aos_impressions");
				}
				Map<String, Object> SkuRptMap = SkuRpt.get(p_org_id + "~" + item_id);// Sku报告
				BigDecimal Roi7Days = BigDecimal.ZERO;// 7天ROI
				if (SkuRptMap != null && ((BigDecimal) SkuRptMap.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
					Roi7Days = ((BigDecimal) SkuRptMap.get("aos_total_sales"))
							.divide((BigDecimal) SkuRptMap.get("aos_spend"), 2, BigDecimal.ROUND_HALF_UP);
				}
				Object FixValue = DailyPrice.get(p_org_id + "~" + item_id); // 定价 Amazon每日价格
				BigDecimal HighStandard = GetHighStandard(FixValue, HIGH1, HIGH2, HIGH3);// 最高标准 根据国别定价筛选

				MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "前3天日平均花费 =" + Spend3Avg);
				MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "SKU花费标准=" + SkuCostStandard);
				MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "7天ROI=" + Roi7Days);
				MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "国别ROI标准=" + PopOrgRoist);
				MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "可售库存天数=" + aos_avadays);
				MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "出价=" + aos_bid);
				MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "出价最高标准=" + HighStandard);
				MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "前3天日平均花费=" + Impress3Avg);
				MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "国别标准SKU花费标准=" + SkuExptandard);
				MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "曝光点击率=" + aos_exprate);
				MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "曝光点击国别标准=" + SKUEXP);

				if ((get_between_days(Today, (Date) aos_groupdate) > 7)
						&& (Spend3Avg.multiply(BigDecimal.valueOf(3))
								.compareTo(SkuCostStandard.multiply(BigDecimal.valueOf(3))) > 0)
						&& (Roi7Days.compareTo(PopOrgRoist) < 0) && !"春夏产品".equals(aos_seasonseting)
						&& !"春夏常规品".equals(aos_seasonseting))
					// 1.ROI差花费高 首次建组>7天，前3天日平均花费≥SKU花费标准(取参数表<SKU花费标准>)，且
					// 7天ROI＜国别ROI标准;
					aos_detailentry.set("aos_type", "ROILOW");
				else if ((get_between_days(Today, (Date) aos_groupdate) > 7)
						&& (Spend3Avg.multiply(BigDecimal.valueOf(3))
								.compareTo(SkuCostStandard.multiply(BigDecimal.valueOf(3))) > 0)
						&& (Roi7Days.compareTo(BigDecimal.valueOf(5)) < 0)
						&& ("春夏产品".equals(aos_seasonseting) || "春夏常规品".equals(aos_seasonseting)))
					// 保留
					aos_detailentry.set("aos_type", "ROILOW");
				else if ((Roi7Days.compareTo(PopOrgRoist) > 0)
						&& (Impress3Avg.multiply(BigDecimal.valueOf(3)).compareTo(
								SkuExptandard.multiply(BigDecimal.valueOf(3)).multiply(BigDecimal.valueOf(0.5))) > 0)
						&& (Spend3Avg.multiply(BigDecimal.valueOf(3)).compareTo(
								SkuCostStandard.multiply(BigDecimal.valueOf(3)).multiply(BigDecimal.valueOf(0.2))) < 0)
						&& (aos_exprate.compareTo(SKUEXP) < 0))
					// 2.ROI好花费低
					// 7天ROI＞标准，前3曝光＞国别标准(取参数表<曝光标准>)，前3天日平均花费＜国别标准*20%，曝光点击率＜国别标准(取参数表<曝光点击>);
					aos_detailentry.set("aos_type", "ROIHIGH");
				else
					aos_detailentry.set("aos_type", "OTHER");

			}

			// 保存正式表
			OperationServiceHelper.executeOperate("save", "aos_mkt_popadjustp_data",
					new DynamicObject[] { aos_mkt_popadjustp_data }, OperateOption.create());

			// 保存日志表
			OperationServiceHelper.executeOperate("save", "aos_sync_log", new DynamicObject[] { aos_sync_log },
					OperateOption.create());

		} catch (Exception e) {
			String message = e.toString();
			String exceptionStr = SalUtil.getExceptionStr(e);
			String messageStr = message + "\r\n" + exceptionStr;
			System.out.println(messageStr);
			logger.error(messageStr);
		} finally {
			try {
				setCache();
			} catch (Exception e) {
				String message = e.toString();
				String exceptionStr = SalUtil.getExceptionStr(e);
				String messageStr = message + "\r\n" + exceptionStr;
				System.out.println(messageStr);
				logger.error(messageStr);
			}
		}
	}

	private static Map<String, Object> getCateSeason(Object p_ou_code) {
		HashMap<String, Object> cateSeason = new HashMap<>();
		DynamicObjectCollection aos_mkt_cateseasonS = QueryServiceHelper.query("aos_mkt_cateseason",
				"aos_category1," + "aos_category2,aos_festname",
				new QFilter("aos_startdate", QCP.large_equals, FndDate.add_days(new Date(), 7))
						.and("aos_endate", QCP.less_equals, FndDate.add_days(new Date(), 7))
						.and("aos_orgid.number", QCP.equals, p_ou_code).toArray());
		for (DynamicObject aos_mkt_cateseason : aos_mkt_cateseasonS) {
			String aos_category1 = aos_mkt_cateseason.getString("aos_category1");
			String aos_category2 = aos_mkt_cateseason.getString("aos_category2");
			String aos_festname = aos_mkt_cateseason.getString("aos_festname");
			Object lastObject = cateSeason.get("aos_category1" + "~" + "aos_category2");
			if (FndGlobal.IsNull(lastObject))
				cateSeason.put(aos_category1 + "~" + aos_category2, aos_festname);
			else
				cateSeason.put(aos_category1 + "~" + aos_category2, lastObject + "/" + aos_festname);
		}
		return cateSeason;
	}

	private static HashMap<String, Object> GenerateRank(Object p_org_id) {
		HashMap<String, Object> Rank = new HashMap<>();
		QFilter filter_org = new QFilter("aos_orgid", "=", p_org_id);
		QFilter[] filters = new QFilter[] { filter_org };
		DynamicObjectCollection aos_base_rankS = QueryServiceHelper.query("aos_base_rank", "aos_itemid,aos_rank",
				filters);
		for (DynamicObject aos_base_rank : aos_base_rankS) {
			Rank.put(aos_base_rank.getString("aos_itemid"), aos_base_rank.get("aos_rank"));
		}
		return Rank;
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
		aos_sal_act_type_pS.close();
		System.out.println("====AM平台活动====");
		return Act;
	}

	/**
	 * 在缓存里面添加完成国别数量，当所有国别都完成了以后，发送邮件
	 * 
	 * @throws ParseException
	 */
	synchronized public static void setCache() throws ParseException {
		// 判断是否完成
		Optional<String> op = Optional.ofNullable(cache.get("saveOrgs"));
		// 保存的国家数量
		String orgs = op.orElse("0");
		int saveOrgs = Integer.valueOf(orgs);
		saveOrgs++;
		// 获取当前时间，判断是早上还是下午，早上是欧洲，下午是美加
		Date date_now = new Date();
		SimpleDateFormat sim = new SimpleDateFormat("yyyy-MM-dd");
		Calendar cal = new GregorianCalendar();
		cal.setTime(sim.parse(sim.format(date_now)));
		cal.add(Calendar.HOUR, 16); // 4点前都认为是早生成的
		int type = date_now.compareTo(cal.getTime());

		// 早上 欧洲
		if (type <= 0) {
			if (saveOrgs >= 5) {
				// 缓存清空
				cache.remove("saveOrgs");
			} else {
				cache.put("saveOrgs", String.valueOf(saveOrgs));
			}
		}
		// 下午 美加
		if (type == 1) {
			if (saveOrgs >= 2) {
				// 缓存清空
				cache.remove("saveOrgs");
			} else {
				cache.put("saveOrgs", String.valueOf(saveOrgs));
			}
		}
	}
}