package mkt.popularst.adjusts_p;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import common.sal.util.SalUtil;
import kd.bos.cache.CacheFactory;
import kd.bos.cache.DistributeSessionlessCache;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.db.DB;
import kd.bos.db.DBRoute;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.exception.KDException;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import mkt.common.AosMktGenerate;
import mkt.common.MKTCom;
import sal.synciface.imp.aos_sal_import_pub;

public class aos_mkt_popadjpst_init extends AbstractTask {
	private static final String DB_MKT = "aos.mkt";
	private static Log logger = LogFactory.getLog(aos_mkt_popadjpst_init.class);

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
			String p_ou_code = (String) param.get("p_ou_code");
			Object p_org_id = aos_sal_import_pub.get_import_id(p_ou_code, "bd_country");
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
			QFilter filter_org = new QFilter("aos_orgid.number", "=", p_ou_code);
			QFilter filter_date = new QFilter("aos_makedate", "=", Today);
			QFilter[] filters_adj = new QFilter[] { filter_org, filter_date };
			DeleteServiceHelper.delete("aos_mkt_pop_adjpst", filters_adj);
			DeleteServiceHelper.delete("aos_mkt_pop_adjpstdt", filters_adj);
			// 初始化数据
			HashMap<String, Map<String, Object>> PopOrgInfo = AosMktGenerate.GeneratePopOrgInfo(p_ou_code);// 营销国别标准参数
			HashMap<String, Map<String, Object>> SkuRpt3Avg = AosMktGenerate.GenerateSkuRpt3Group(p_ou_code);// 3日组报告
			HashMap<String, Map<String, Object>> SkuRptGroup = AosMktGenerate.GenerateSkuRpt7Group(p_ou_code);// 7日组报告
			HashMap<String, Map<String, Object>> SkuRpt = AosMktGenerate.GenerateSkuRpt7(p_ou_code);
			HashMap<String, Map<String, Object>> SkuRpt1 = AosMktGenerate.GenerateKeyRpt1(p_ou_code);//词昨日报告
			HashMap<String, Map<String, Map<String, Object>>> KeyRpt14 = AosMktGenerate.GenerateSkuRpt14(p_ou_code);//词每日报告
			HashMap<String, Map<String, Object>> PpcYester = AosMktGenerate.GeneratePpcYesterSt(p_ou_code);// 昨日数据
			HashMap<String, Object> DailyPrice = AosMktGenerate.GenerateDailyPrice(p_ou_code);// 亚马逊定价
			HashMap<String, Object> PpcTodayGroup = AosMktGenerate.GeneratePpcTodayStGroup(p_ou_code);// 今日数据源组关键词个数
			HashMap<String, Map<String, Object>> AdPriceKey = AosMktGenerate.GenerateAdvKeyPrice(p_ou_code);// 关键词建议价格
			HashMap<String, Map<String,  Object>> AdPriceKey14 =  AosMktGenerate.GenerateAdvKeyPrice14(p_ou_code);
			
			// 营销国别参数
			BigDecimal HIGH1 = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "HIGH1").get("aos_value");// 最高标准1(定价<200)
			BigDecimal HIGH2 = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "HIGH2").get("aos_value");// 最高标准2(200<=定价<500)
			BigDecimal HIGH3 = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "HIGH3").get("aos_value");// 最高标准3(定价>=500)
			BigDecimal PopOrgRoist = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "ROIST").get("aos_value");// 国别标准ROI
			BigDecimal SkuCostStandard = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "SKUCOST").get("aos_value");// SKU花费标准
			BigDecimal SkuExptandard = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "EXPOSURE").get("aos_value");// SKU曝光标准
			BigDecimal SKUEXP = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "SKUEXP").get("aos_value");// SKU曝光点击

//			QFilter Qf_Date = new QFilter("aos_date", "=", Today);
//			QFilter Qf_Org = new QFilter("aos_orgid.number", "=", p_ou_code);
//			QFilter Qf_type = new QFilter("aos_entryentity.aos_keystatus", "=", "AVAILABLE");
//			QFilter[] filters = new QFilter[] { Qf_Date, Qf_Org, Qf_type };

//			String SelectField = "id,aos_billno,aos_entryentity.aos_productno aos_productno,"
//					+ "aos_entryentity.aos_season aos_season," + "aos_entryentity.aos_category1 aos_category1,"
//					+ "aos_entryentity.aos_itemname aos_itemname," + "aos_entryentity.aos_itemnumer aos_itemnumer,"
//					+ "aos_entryentity.aos_keyword aos_keyword," + "aos_entryentity.aos_match_type aos_match_type,"
//					+ "aos_entryentity.aos_roi7days aos_roi7days," + "aos_entryentity.aos_impressions aos_impressions,"
//					+ "aos_entryentity.aos_lastbid aos_lastbid," + "aos_entryentity.aos_highvalue aos_highvalue,"
//					+ "aos_entryentity.aos_lastpricedate aos_lastpricedate,"
//					+ "aos_entryentity.aos_groupdate aos_groupdate," + "aos_entryentity.aos_itemid aos_itemid,"
//					+ "aos_entryentity.aos_lastbid aos_lastprice," + "aos_entryentity.aos_avadays aos_avadays,"
//					+ "aos_entryentity.id aos_entryid," + "aos_entryentity.aos_bid aos_bid";
//			DynamicObjectCollection aos_mkt_pop_ppcstS = QueryServiceHelper.query("aos_mkt_pop_ppcst", SelectField,
//					filters, "aos_entryentity.aos_productno");
			
			QFilter Qf_Date = new QFilter("aos_date", "=", Today);
			QFilter Qf_Org = new QFilter("aos_orgid.number", "=", p_ou_code);
			QFilter Qf_type = new QFilter("aos_keystatus", "=", "AVAILABLE");
			QFilter[] filters = new QFilter[] { Qf_Date, Qf_Org, Qf_type };
			
			String SelectField = "aos_sourceid id,aos_billno,aos_productno,"
					+ "aos_season,aos_category1,aos_itemname,aos_itemnumer,"
					+ "aos_keyword,aos_match_type,aos_roi7days,aos_impressions,"
					+ "aos_lastbid,aos_highvalue,aos_lastpricedate,aos_groupdate,aos_itemid,"
					+ "aos_lastbid aos_lastprice,aos_avadays,"
					+ "id aos_entryid," + "aos_bid";
			DynamicObjectCollection aos_mkt_pop_ppcstS = QueryServiceHelper.query("aos_mkt_ppcst_data", SelectField,
					filters, "aos_entryentity.aos_productno");
			
			int rows = aos_mkt_pop_ppcstS.size();
			int count = 0;
			DynamicObject Head = aos_mkt_pop_ppcstS.get(0);
			String aos_billno = Head.getString("aos_billno");
			DynamicObject aos_mkt_pop_adjpst = BusinessDataServiceHelper.newDynamicObject("aos_mkt_pop_adjpst");
			aos_mkt_pop_adjpst.set("aos_billno", aos_billno);
			aos_mkt_pop_adjpst.set("aos_orgid", p_org_id);
			aos_mkt_pop_adjpst.set("billstatus", "A");
			aos_mkt_pop_adjpst.set("aos_status", "A");
			aos_mkt_pop_adjpst.set("aos_makeby", SYSTEM);
			aos_mkt_pop_adjpst.set("aos_makedate", Today);
			aos_mkt_pop_adjpst.set("aos_ppcid", Head.get("id"));

			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_pop_adjpst",
					new DynamicObject[] { aos_mkt_pop_adjpst }, OperateOption.create());
			Object aos_sourceid = operationrst.getSuccessPkIds().get(0);

			DynamicObject aos_mkt_pop_adjpstdt = BusinessDataServiceHelper.newDynamicObject("aos_mkt_pop_adjpstdt");
			aos_mkt_pop_adjpstdt.set("aos_billno", aos_billno);
			aos_mkt_pop_adjpstdt.set("aos_orgid", p_org_id);
			aos_mkt_pop_adjpstdt.set("billstatus", "A");
			aos_mkt_pop_adjpstdt.set("aos_sourceid", aos_sourceid);
			aos_mkt_pop_adjpstdt.set("aos_makedate", Today);
			// 获取 出价调整(销售)相同单号表中的物料 这些物料需要做排除 出价调整(销售)先生成 出价调整(推广)后生成
			Map<String, Object> AdjustPMap = GenerateAdjustPMap(aos_billno);

			DynamicObjectCollection aos_detailentryS = aos_mkt_pop_adjpstdt
					.getDynamicObjectCollection("aos_detailentry");
			DynamicObject aos_sync_log = BusinessDataServiceHelper.newDynamicObject("aos_sync_log");
			aos_sync_log.set("aos_type_code", "MKT_ST出价调整(推广)");
			aos_sync_log.set("aos_groupid", p_ou_code.toString() + year + month + day);
			aos_sync_log.set("billstatus", "A");
			DynamicObjectCollection aos_sync_logS = aos_sync_log.getDynamicObjectCollection("aos_entryentity");

			for (DynamicObject aos_mkt_pop_ppcst : aos_mkt_pop_ppcstS) {
				count++;
				long item_id = aos_mkt_pop_ppcst.getLong("aos_itemid");
				String aos_itemnumer = aos_mkt_pop_ppcst.getString("aos_itemnumer");
				String aos_productno = aos_mkt_pop_ppcst.getString("aos_productno");
				String aos_season = aos_mkt_pop_ppcst.getString("aos_season");
				String aos_category1 = aos_mkt_pop_ppcst.getString("aos_category1");
				String aos_itemname = aos_mkt_pop_ppcst.getString("aos_itemname");
				String aos_keyword = aos_mkt_pop_ppcst.getString("aos_keyword");
				String aos_matchtype = aos_mkt_pop_ppcst.getString("aos_match_type");
				BigDecimal aos_lastprice = aos_mkt_pop_ppcst.getBigDecimal("aos_lastprice");
				Date aos_lastpricedate = aos_mkt_pop_ppcst.getDate("aos_lastpricedate");
				BigDecimal aos_highvalue = aos_mkt_pop_ppcst.getBigDecimal("aos_highvalue");
				Date aos_groupdate = aos_mkt_pop_ppcst.getDate("aos_groupdate");
				int aos_avadays = aos_mkt_pop_ppcst.getInt("aos_avadays");
				long aos_entryid = aos_mkt_pop_ppcst.getLong("aos_entryid");
				BigDecimal aos_bid = aos_mkt_pop_ppcst.getBigDecimal("aos_bid");

				if (AdjustPMap.get(item_id + "") != null) {
					MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "ST出价调整(销售)中存在跳过");
					continue;// 出价调整(销售)中如果存在则 出价调整(推广)不生成
				}

				DynamicObject aos_detailentry = aos_detailentryS.addNew();
				aos_detailentry.set("aos_poptype", "ST");
				aos_detailentry.set("aos_productno", aos_productno);
				aos_detailentry.set("aos_season", aos_season);
				aos_detailentry.set("aos_category1", aos_category1);
				aos_detailentry.set("aos_itemname", aos_itemname);
				aos_detailentry.set("aos_itemnumer", aos_itemnumer);
				aos_detailentry.set("aos_keyword", aos_keyword);
				aos_detailentry.set("aos_matchtype", aos_matchtype);
				aos_detailentry.set("aos_lastprice", aos_lastprice);
				aos_detailentry.set("aos_lastdate", aos_lastpricedate);
				aos_detailentry.set("aos_highvalue", aos_highvalue);
				aos_detailentry.set("aos_groupdate", aos_groupdate);
				aos_detailentry.set("aos_avadays", aos_avadays);
				aos_detailentry.set("aos_itemid", item_id);
				aos_detailentry.set("aos_entryid", aos_entryid);
				aos_detailentry.set("aos_calprice", aos_bid);
				aos_detailentry.set("aos_adjprice", aos_bid);
				
				String key = aos_itemnumer + "~" + aos_keyword + "~" + aos_matchtype;

				
				// 建议价 
				Map<String, Object> AdPriceMap = AdPriceKey.get(key);// 组
				BigDecimal aos_bidsuggest = BigDecimal.ZERO;
				if (AdPriceMap != null) 
					aos_bidsuggest = (BigDecimal) AdPriceMap.get("aos_bid_suggest");
				aos_detailentry.set("aos_bidsuggest", aos_bidsuggest);
				
				// 7日组维度词曝光点击率
				Map<String, Object> SkuRptMap = SkuRpt.get(key);
				BigDecimal aos_click = BigDecimal.ZERO;// 点击
				BigDecimal aos_impressions = BigDecimal.ZERO;// 曝光
				BigDecimal aos_spend = BigDecimal.ZERO;// 投入
				BigDecimal aos_ctr = BigDecimal.ZERO;// 曝光点击率
				BigDecimal aos_sales = BigDecimal.ZERO;//营收
				BigDecimal aos_roi = BigDecimal.ZERO;//ROI
				if (SkuRptMap != null) {
					aos_click = (BigDecimal) SkuRptMap.get("aos_clicks");
					aos_impressions = (BigDecimal) SkuRptMap.get("aos_impressions");// 曝光
					aos_spend = (BigDecimal) SkuRptMap.get("aos_spend");// 花费
					aos_sales= (BigDecimal) SkuRptMap.get("aos_sales");// 营收
				}
				if (aos_impressions.compareTo(BigDecimal.ZERO) != 0 && aos_click != null)
					aos_ctr = aos_click.divide(aos_impressions, 6, BigDecimal.ROUND_HALF_UP);
				if (aos_spend.compareTo(BigDecimal.ZERO) != 0 && aos_sales != null)
					aos_roi = aos_sales.divide(aos_spend, 6, BigDecimal.ROUND_HALF_UP);
				aos_detailentry.set("aos_exposure", aos_impressions);
				aos_detailentry.set("aos_ctr", aos_ctr);
				aos_detailentry.set("aos_cost", aos_spend);
				aos_detailentry.set("aos_roi", aos_roi);
				
				// 系列花出率  ST数据源前一天花出/预算
				BigDecimal aos_serialrate = BigDecimal.ZERO;
				BigDecimal aos_budgetlast = BigDecimal.ZERO;//昨日预算
				BigDecimal aos_spendlast = BigDecimal.ZERO;// 前一天花出
				Map<String, Object> SkuRpt1Map = SkuRpt1.get(key);
				if (SkuRpt1Map != null && SkuRpt1Map.get("aos_spend") != null) 
					aos_spendlast = (BigDecimal) SkuRpt1Map.get("aos_spend");
				Map<String, Object> PpcYester_Map = PpcYester
						.get(key);
				if (PpcYester_Map != null && PpcYester_Map.get("aos_budget") != null) 
					aos_budgetlast = (BigDecimal) PpcYester_Map.get("aos_budget");//昨日预算
				if (PpcYester_Map != null) 
					if (aos_budgetlast.compareTo(BigDecimal.ZERO) != 0)
						aos_serialrate = aos_spendlast.divide(aos_budgetlast, 2, BigDecimal.ROUND_HALF_UP);
				
				aos_detailentry.set("aos_serialrate", aos_serialrate);
			}

			for (DynamicObject aos_detailentry : aos_detailentryS) {
				String aos_itemnumer = aos_detailentry.getString("aos_itemnumer");
				int aos_avadays = aos_detailentry.getInt("aos_avadays");
				long item_id = aos_detailentry.getLong("aos_itemid");
				BigDecimal aos_bid = aos_detailentry.getBigDecimal("aos_calprice");
				BigDecimal aos_exprate = aos_detailentry.getBigDecimal("aos_ctr");
				Date aos_groupdate = aos_detailentry.getDate("aos_groupdate");
				Map<String, Object> SkuRpt3AvgMap = SkuRpt3Avg.get(aos_itemnumer);
				BigDecimal Spend3Avg = BigDecimal.ZERO;
				BigDecimal Impress3Avg = BigDecimal.ZERO;
				if (SkuRpt3AvgMap != null) {
					Spend3Avg = (BigDecimal) SkuRpt3AvgMap.get("aos_spend");
					Impress3Avg = (BigDecimal) SkuRpt3AvgMap.get("aos_impressions");
				}
				Map<String, Object> SkuRptMap = SkuRptGroup.get(aos_itemnumer);// Sku报告
				BigDecimal Roi7Days = BigDecimal.ZERO;// 7天ROI
				if (SkuRptMap != null && ((BigDecimal) SkuRptMap.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
					Roi7Days = ((BigDecimal) SkuRptMap.get("aos_sales")).divide((BigDecimal) SkuRptMap.get("aos_spend"),
							2, BigDecimal.ROUND_HALF_UP);
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
				// 对于类型进行计算
				if ((aos_avadays < 90) && (Spend3Avg.compareTo(SkuCostStandard) > 0))
					// 1.可售低花费高 可售天数＜90天(在途+在库数量计算可售天数)，前3天日平均花费≥国别标准SKU花费标准;
					aos_detailentry.set("aos_type", "AVALOW");
				else if ((get_between_days(Today, aos_groupdate) > 7) && (Spend3Avg.compareTo(SkuCostStandard) > 0)
						&& (Roi7Days.compareTo(PopOrgRoist) < 0) && (Impress3Avg.compareTo(SkuExptandard) > 0))
					// 2.ROI差花费高 首次建组>7天，前3天日平均花费≥SKU花费标准(取参数表<SKU花费标准>)，且
					// 7天ROI＜国别ROI标准，且前3平均曝光>国别标准(取参数表<曝光标准>);
					aos_detailentry.set("aos_type", "ROILOW");
				else if ((aos_avadays > 120) && (Impress3Avg.compareTo(SkuExptandard) < 0)
						&& (aos_bid.compareTo(HighStandard) < 0))
					// 3.曝光低可售高 可售天数＞120天(在途+在库数量计算)，前3平均曝光＜国别标准(取参数表<曝光标准>)*50%，出价<出价最高标准(根据定价分别
					// 取参数表<出价最高标准>);
					aos_detailentry.set("aos_type", "EXPLOW");
				else if ((Roi7Days.compareTo(PopOrgRoist) > 0)
						&& (Impress3Avg.compareTo(SkuExptandard.multiply(BigDecimal.valueOf(0.5))) > 0)
						&& (Spend3Avg.compareTo(SkuCostStandard.multiply(BigDecimal.valueOf(0.2))) < 0)
						&& (aos_exprate.compareTo(SKUEXP) < 0))
					// 4.ROI好花费低
					// 7天ROI＞标准，前3曝光＞国别标准(取参数表<曝光标准>)，前3天日平均花费＜国别标准*20%，曝光点击率＜国别标准(取参数表<曝光点击>);
					aos_detailentry.set("aos_type", "ROIHIGH");
				else
					// 5.其他
					aos_detailentry.set("aos_type", "OTHER");
			}
			
			
			// 查找15天前的 ppc平台数据
			List<QFilter> list_qfs = new ArrayList<>(10); // 查询条件
			QFilter qf_status = new QFilter("aos_entryentity.aos_keystatus", "=", "AVAILABLE"); // 状态可用
			list_qfs.add(qf_status);
			Calendar cal = new GregorianCalendar();
			try {
				cal.setTime(DF.parse(DF.format(today.getTime())));
			} catch (ParseException e1) {
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
//			str.add("substring(aos_date,1,10) as aos_date");
//			str.add("aos_entryentity.aos_bid as aos_ppcbid"); // 出价
//			str.add("aos_entryentity.aos_itemnumer as aos_itemnumer"); // 组名
//			str.add("aos_entryentity.aos_keyword as aos_keyword"); // 关键词
//			str.add("aos_entryentity.aos_match_type as aos_match_type"); // 匹配类型
			
			str.add("substring(aos_date,1,10) as aos_date");
			str.add("aos_bid as aos_ppcbid"); // 出价
			str.add("aos_itemnumer as aos_itemnumer"); // 组名
			str.add("aos_keyword as aos_keyword"); // 关键词
			str.add("aos_match_type as aos_match_type"); // 匹配类型
			
			
//			DynamicObjectCollection dyc_ppc = QueryServiceHelper.query("aos_mkt_pop_ppcst", str.toString(), qfs);
			DynamicObjectCollection dyc_ppc = QueryServiceHelper.query("aos_mkt_ppcst_data", str.toString(), qfs);
			
			Map<String, BigDecimal[]> map_ppc = dyc_ppc.stream()
					.collect(Collectors.toMap(e -> e.getString("aos_date") + "?" + e.getString("aos_itemnumer")+"~"
							+ e.getString("aos_keyword") +"~"+e.getString("aos_match_type"), e -> {
						BigDecimal[] big = new BigDecimal[2];
						big[0] = BigDecimal.ZERO;
						if (e.getBigDecimal("aos_ppcbid") != null)
							big[0] = e.getBigDecimal("aos_ppcbid");
						return big;
					}));
			
			// 构造子单据体词数据15天
			for (DynamicObject aos_detailentry : aos_detailentryS) {
				String aos_itemnumer = aos_detailentry.getString("aos_itemnumer");
				String aos_keyword = aos_detailentry.getString("aos_keyword");
				String aos_matchtype = aos_detailentry.getString("aos_matchtype");
				String key = aos_itemnumer+"~"+aos_keyword+"~"+aos_matchtype;
				Map<String, Map<String, Object>> SkuRptMap14 = KeyRpt14.get(key);
				if (SkuRptMap14 == null)
					continue;
				

				DynamicObjectCollection aos_wordentryS = aos_detailentry
						.getDynamicObjectCollection("aos_wordentry");
				
				for (String DateStr : SkuRptMap14.keySet()) {
					Map<String, Object> AdPriceKey14D = AdPriceKey14.get(DateStr);
					BigDecimal aos_wordadprice = BigDecimal.ZERO;
					if (AdPriceKey14D != null)
						aos_wordadprice = (BigDecimal) AdPriceKey14D.get("aos_bid_suggest");
					Map<String, Object> SkuRptMap14D = SkuRptMap14.get(DateStr);
					BigDecimal aos_expouse = (BigDecimal) SkuRptMap14D.get("aos_impressions");
					BigDecimal aos_roi = (BigDecimal) SkuRptMap14D.get("aos_roi");
					BigDecimal aos_bid = (BigDecimal) SkuRptMap14D.get("aos_bid");
					BigDecimal aos_cost = (BigDecimal) SkuRptMap14D.get("aos_spend");
					BigDecimal aos_click = (BigDecimal) SkuRptMap14D.get("aos_clicks");
					BigDecimal aos_wordctr = BigDecimal.ZERO;
					if (aos_expouse.compareTo(BigDecimal.ZERO) != 0 && aos_click != null)
						aos_wordctr = aos_click.divide(aos_expouse, 6, BigDecimal.ROUND_HALF_UP);
					DynamicObject aos_wordentry = aos_wordentryS.addNew();
					Date aos_date_d = DF.parse(DateStr);
					aos_wordentry.set("aos_worddate", aos_date_d);
					aos_wordentry.set("aos_wordroi", aos_roi);
					aos_wordentry.set("aos_wordex", aos_expouse);
					aos_wordentry.set("aos_wordctr", aos_wordctr);
					aos_wordentry.set("aos_wordbid", aos_bid);
					aos_wordentry.set("aos_wordcost", aos_cost);
					aos_wordentry.set("aos_wordadprice", aos_wordadprice);
					
					String key2 = DF.format(aos_date_d) + "?" + key;
					if (map_ppc.containsKey(key2)) {
						BigDecimal[] value = map_ppc.get(key2);
						aos_wordentry.set("aos_wordsetbid", value[0]);
					}
				}
			}
			
			// 构造子单据体 组数据
			for (DynamicObject aos_detailentry : aos_detailentryS) {
				String aos_itemnumer = aos_detailentry.getString("aos_itemnumer");
				String aos_groupitemname = aos_detailentry.getString("aos_itemname");
				Map<String, Object> SkuRptMap = SkuRptGroup.get(aos_itemnumer);// 7日关键词报告词数据
				BigDecimal Roi7Days = BigDecimal.ZERO;
				BigDecimal aos_spend = BigDecimal.ZERO;
				BigDecimal aos_sales = BigDecimal.ZERO;
				BigDecimal aos_impressions = BigDecimal.ZERO;
				if (SkuRptMap != null ) {
					aos_spend = (BigDecimal) SkuRptMap.get("aos_spend");// 投入
					aos_sales = (BigDecimal) SkuRptMap.get("aos_sales");// 营收
					aos_impressions = (BigDecimal) SkuRptMap.get("aos_impressions");//曝光
					if (aos_sales != null && ((BigDecimal) SkuRptMap.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0  ) {
						Roi7Days = ((BigDecimal) SkuRptMap.get("aos_sales")).divide((BigDecimal) SkuRptMap.get("aos_spend"),
								2, BigDecimal.ROUND_HALF_UP);
					}
				}
				DynamicObjectCollection aos_groupskuentryS = aos_detailentry
						.getDynamicObjectCollection("aos_groupskuentry");
				DynamicObject aos_groupskuentry = aos_groupskuentryS.addNew();
				aos_groupskuentry.set("aos_groupsku", aos_itemnumer);
				aos_groupskuentry.set("aos_groupitemname", aos_groupitemname);
				aos_groupskuentry.set("aos_groupkwnum", PpcTodayGroup.get(aos_itemnumer));
				aos_groupskuentry.set("aos_grouproi", Roi7Days);
				aos_groupskuentry.set("aos_groupex", aos_impressions);
				aos_groupskuentry.set("aos_groupcost", aos_spend);
				aos_groupskuentry.set("aos_buybox", null);
			}
			// 保存正式表
			OperationResult operationrst1 = OperationServiceHelper.executeOperate("save", "aos_mkt_pop_adjpstdt",
					new DynamicObject[] { aos_mkt_pop_adjpstdt }, OperateOption.create());
			if (operationrst1.getValidateResult().getValidateErrors().size() != 0) {
			}
			Object fid = operationrst1.getSuccessPkIds().get(0);
			String sql = " DELETE FROM tk_aos_mkt_pop_adjpstdt_r r WHERE 1=1 "
					+ " and r.FId = ?  and r.fk_aos_type = '' ";
			Object[] params = { fid };
			DB.execute(DBRoute.of(DB_MKT), sql, params);

			// 保存日志表
			OperationResult operationrstLog = OperationServiceHelper.executeOperate("save", "aos_sync_log",
					new DynamicObject[] { aos_sync_log }, OperateOption.create());
			if (operationrstLog.getValidateResult().getValidateErrors().size() != 0) {
			}
		} catch (Exception e) {
			String message = e.toString();
			String exceptionStr = SalUtil.getExceptionStr(e);
			String messageStr = message + "\r\n" + exceptionStr;
			logger.error(messageStr);
		} finally {
			try {
				setCache();
			} catch (Exception e) {
				String message = e.toString();
				String exceptionStr = SalUtil.getExceptionStr(e);
				String messageStr = message + "\r\n" + exceptionStr;
				logger.error(messageStr);
			}
		}
	}

	private static Map<String, Object> GenerateAdjustPMap(String aos_billno) {
		Map<String, Object> AdjustPMap = new HashMap<>();
		QFilter qf_billno = new QFilter("aos_billno", "=", aos_billno);
		QFilter[] filters = new QFilter[] { qf_billno };
		String SelectField = "aos_detailentry.aos_itemid aos_itemid";
		DynamicObjectCollection aos_mkt_popadjusts_dataS = QueryServiceHelper.query("aos_mkt_pop_adjstdt", SelectField,
				filters);
		for (DynamicObject aos_mkt_popadjusts_data : aos_mkt_popadjusts_dataS) {
			String aos_itemid = aos_mkt_popadjusts_data.get("aos_itemid") + "";
			AdjustPMap.put(aos_itemid, aos_itemid);
		}
		return AdjustPMap;
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
				// 发送邮件
//				String head = "ST初始化数据完成 (欧洲)";
//				sal.sche.aos_sal_sche_pub.aos_sal_sche_pvt.WarinCore(head, head, "B");
				// 缓存清空
				cache.remove("saveOrgs");
			} else {
				cache.put("saveOrgs", String.valueOf(saveOrgs));
			}
		}
		// 下午 美加
		if (type == 1) {
			if (saveOrgs >= 2) {
				// 发送邮件
//				String head = "ST初始化数据完成 (美加)";
//				sal.sche.aos_sal_sche_pub.aos_sal_sche_pvt.WarinCore(head, head, "B");
				// 缓存清空
				cache.remove("saveOrgs");
			} else {
				cache.put("saveOrgs", String.valueOf(saveOrgs));
			}
		}
	}
}