package mkt.popularst.budget_p;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import common.CommonDataSom;
import common.Cux_Common_Utl;
import common.fnd.FndGlobal;
import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
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
import kd.bos.threads.ThreadPools;
import mkt.common.AosMktGenerate;

public class aos_mkt_popbudpst_init extends AbstractTask {
	private static Log logger = LogFactory.getLog(aos_mkt_popbudpst_init.class);
	public final static String SYSTEM = get_system_id(); // ID-000000

	public static String get_system_id() {
		QFilter qf_user = new QFilter("number", "=", "system");
		DynamicObject dy_user = QueryServiceHelper.queryOne("bos_user", "id", new QFilter[] { qf_user });
		String system_id = dy_user.get("id").toString();
		return system_id;
	}

	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		// 多线程执行
		executerun(param);
	}

	public static void ManualitemClick(Map<String, Object> param) {
		executerun(param);
	}

	public static void executerun(Map<String, Object> param) {
		// 初始化数据
		Object p_ou_code = param.get("p_ou_code");
		QFilter oversea_flag = new QFilter("aos_is_oversea_ou.number", "=", "Y");// 海外公司
		QFilter Qf_Org = new QFilter("number", "=", p_ou_code);
		QFilter[] filters_ou = new QFilter[] { oversea_flag, Qf_Org };
		DynamicObjectCollection bd_country = QueryServiceHelper.query("bd_country", "id,number", filters_ou);
		for (DynamicObject ou : bd_country) {
			logger.info("MKT_PPC预算调整ST_" + p_ou_code);
			Map<String, Object> params = new HashMap<>();
			params.put("p_ou_code", ou.getString("number"));
			MktPopBudPSTRunnable mktPopBudPSTRunnable = new MktPopBudPSTRunnable(params);
			ThreadPools.executeOnce("MKT_PPC预算调整ST_" + p_ou_code, mktPopBudPSTRunnable);
		}
	}

	static class MktPopBudPSTRunnable implements Runnable {
		private Map<String, Object> params = new HashMap<>();

		public MktPopBudPSTRunnable(Map<String, Object> param) {
			this.params = param;
		}

		@Override
		public void run() {
			try {
				CommonDataSom.init();
				do_operate(params);
			} catch (Exception e) {
				String message = e.toString();
				String exceptionStr = SalUtil.getExceptionStr(e);
				String messageStr = message + "\r\n" + exceptionStr;
				logger.error(messageStr);
			}
		}
	}

	@SuppressWarnings("deprecation")
	public static void do_operate(Map<String, Object> params) {
		Cux_Common_Utl.Log(logger, "===== into aos_mkt_popbudgetp_init =====");
		SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");
		String p_ou_code = (String) params.get("p_ou_code");
		Object p_org_id = FndGlobal.get_import_id(p_ou_code, "bd_country");
		// 删除数据
		Calendar today = Calendar.getInstance();
		today.set(Calendar.HOUR_OF_DAY, 0);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.SECOND, 0);
		today.set(Calendar.MILLISECOND, 0);
		Date Today = today.getTime();
		QFilter filter_id = new QFilter("aos_orgid", "=", p_org_id);
		QFilter filter_date = new QFilter("aos_makedate", "=", Today);
		QFilter[] filters_adj = new QFilter[] { filter_id, filter_date };
		DeleteServiceHelper.delete("aos_mkt_pop_budgetst", filters_adj);
		DeleteServiceHelper.delete("aos_mkt_popbudget_stdt", filters_adj);
		// 初始化数据
		HashMap<String, Map<String, Object>> PopOrgInfo = AosMktGenerate.GeneratePopOrgInfo(p_ou_code);// 营销国别标准参数
		HashMap<String, Map<String, Object>> SkuRpt7Serial = AosMktGenerate.GenerateSkuRptSerial(p_ou_code);// 7日关键词报告系列
		HashMap<String, Object> PpcTodaySerial = AosMktGenerate.GeneratePpcTodayStSerial(p_ou_code);// 今日系列维度关键词个数
		HashMap<String, Object> PpcYesterSerial = AosMktGenerate.GeneratePpcYeStSerial(p_ou_code);// ST数据源昨日系列预算
		HashMap<String, Map<String, Object>> SkuRptDetailSerial = AosMktGenerate.GenerateSkuRptDetailSerial(p_ou_code);
		HashMap<String, Object> PpcTodaySerialGroup = AosMktGenerate.GeneratePpcTodayStSerialGroup(p_ou_code);
		HashMap<String, BigDecimal> AdjsMap = InitAdjsMap(p_ou_code, Today);
		HashMap<String, Map<String, Map<String, Object>>> SkuRpt7SerialD =AosMktGenerate.GenerateSkuRpt7Serial(p_ou_code);
		HashMap<String, Map<String, Map<String, Object>>> PpcYsterSerial7 = AosMktGenerate.GenerateYesterSTSerial7D(p_ou_code);
		HashMap<String, Map<String,  Object>> PpcYsterSerialG = AosMktGenerate.GenerateYesterSTSerial7G(p_ou_code);
			
		BigDecimal PopOrgRoist = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "ROIST").get("aos_value");// 国别标准ROI
		BigDecimal WORST = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "WORST").get("aos_value");// 很差
		BigDecimal WORRY = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "WORRY").get("aos_value");// 差
		BigDecimal STANDARD = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "STANDARD").get("aos_value");// 标准
		BigDecimal WELL = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "WELL").get("aos_value");// 好
		BigDecimal EXCELLENT = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "EXCELLENT").get("aos_value");// 优
		QFilter Qf_Date = new QFilter("aos_date", "=", Today);
		QFilter Qf_Org = new QFilter("aos_orgid.number", "=", p_ou_code);
		// 设置出价调整日期Start
		QFilter[] filters = new QFilter[] { Qf_Date, Qf_Org };
		String Select = "id";
		DynamicObject ppc = QueryServiceHelper.queryOne("aos_mkt_pop_ppcst", Select, filters);
		long fid = ppc.getLong("id");
		ppc = BusinessDataServiceHelper.loadSingle(fid, "aos_mkt_pop_ppcst");
		DynamicObjectCollection aos_bid_diff = ppc.getDynamicObjectCollection("aos_entryentity");
		for (DynamicObject d : aos_bid_diff) {
			BigDecimal aos_bid = d.getBigDecimal("aos_bid");
			BigDecimal aos_bid_ori = d.getBigDecimal("aos_bid_ori");
			if (aos_bid.compareTo(aos_bid_ori) != 0)
				d.set("aos_lastpricedate", Today);
		}
		OperationServiceHelper.executeOperate("save", "aos_mkt_pop_ppcst", new DynamicObject[] { ppc },
				OperateOption.create());
		// 开始循环今日ST数据源数据
		filters = new QFilter[] { Qf_Date, Qf_Org };
//		String SelectField = "id," + "aos_billno," + "aos_channel," + "aos_entryentity.aos_productno aos_productno,"
//				+ "aos_entryentity.aos_season aos_season," + "aos_entryentity.aos_budget aos_budget,"
//				+ " case when aos_entryentity.aos_salemanual = 'Y' then 1 "
//				+ " when aos_entryentity.aos_salemanual = 'N' then 0 end as aos_salemanual ";
//		DataSet aos_mkt_popular_ppcS = QueryServiceHelper.queryDataSet("aos_mkt_popbudpst_init" + "." + p_ou_code,
//				"aos_mkt_pop_ppcst", SelectField, filters, null);
		
		String SelectField = "aos_sourceid id,aos_billno,aos_channel,aos_productno,"
				+ "aos_season,aos_budget,"
				+ " case when aos_salemanual = 'Y' then 1 "
				+ " when aos_salemanual = 'N' then 0 end as aos_salemanual ";
		DataSet aos_mkt_popular_ppcS = QueryServiceHelper.queryDataSet("aos_mkt_popbudpst_init" + "." + p_ou_code,
				"aos_mkt_ppcst_data", SelectField, filters, null);
		
		String[] GroupBy = new String[] { "id", "aos_billno", "aos_channel", "aos_productno", "aos_season",
				"aos_budget" };
		aos_mkt_popular_ppcS = aos_mkt_popular_ppcS.groupBy(GroupBy).max("aos_salemanual").finish();
		int count = 0;
		DynamicObject aos_mkt_popbudget_data = BusinessDataServiceHelper.newDynamicObject("aos_mkt_popbudget_stdt");
		DynamicObjectCollection aos_entryentityS = aos_mkt_popbudget_data.getDynamicObjectCollection("aos_budgetentry");
		while (aos_mkt_popular_ppcS.hasNext()) {
			Row aos_mkt_popular_ppc = aos_mkt_popular_ppcS.next();
			count++;
			if (count == 1) {
				// 初始化 预算调整(推广)
				DynamicObject aos_mkt_pop_budgetp = BusinessDataServiceHelper.newDynamicObject("aos_mkt_pop_budgetst");
				aos_mkt_pop_budgetp.set("aos_billno", aos_mkt_popular_ppc.get("aos_billno"));
				aos_mkt_pop_budgetp.set("aos_orgid", p_org_id);
				aos_mkt_pop_budgetp.set("billstatus", "A");
				aos_mkt_pop_budgetp.set("aos_status", "A");
				aos_mkt_pop_budgetp.set("aos_ppcid", aos_mkt_popular_ppc.get("id"));
				aos_mkt_pop_budgetp.set("aos_makeby", SYSTEM);
				aos_mkt_pop_budgetp.set("aos_makedate", Today);
				OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_pop_budgetst",
						new DynamicObject[] { aos_mkt_pop_budgetp }, OperateOption.create());
				Object aos_sourceid = operationrst.getSuccessPkIds().get(0);
				// 初始化 预算调整(推广)数据表
				aos_mkt_popbudget_data.set("aos_billno", aos_mkt_popular_ppc.get("aos_billno"));
				aos_mkt_popbudget_data.set("billno", aos_mkt_popular_ppc.get("aos_billno"));
				aos_mkt_popbudget_data.set("aos_orgid", p_org_id);
				aos_mkt_popbudget_data.set("billstatus", "A");
				aos_mkt_popbudget_data.set("aos_billstatus", "A");
				aos_mkt_popbudget_data.set("aos_sourceid", aos_sourceid);
				aos_mkt_popbudget_data.set("aos_makedate", Today);
			}
			Object aos_channel = aos_mkt_popular_ppc.get("aos_channel");
			String aos_productno = aos_mkt_popular_ppc.getString("aos_productno");
			Object aos_seasonseting = aos_mkt_popular_ppc.get("aos_season");
			BigDecimal aos_budget = aos_mkt_popular_ppc.getBigDecimal("aos_budget");

			DynamicObject aos_entryentity = aos_entryentityS.addNew();
			aos_entryentity.set("aos_channel_r", aos_channel);
			aos_entryentity.set("aos_poptype_r", "ST");
			aos_entryentity.set("aos_serialname", aos_productno);
			aos_entryentity.set("aos_season_r", aos_seasonseting);
			aos_entryentity.set("aos_skucount", PpcTodaySerialGroup.get(aos_productno));
			aos_entryentity.set("aos_calbudget", aos_budget);

			// 系列获取 出价调整 金额
			BigDecimal AdjsBid = BigDecimal.ZERO;
			if (AdjsMap.get(aos_productno) != null)
				AdjsBid = AdjsMap.get(aos_productno);
			aos_entryentity.set("aos_adjbudget", AdjsBid);
			aos_entryentity.set("aos_popbudget", Cux_Common_Utl.Max(aos_budget, AdjsBid));

			int aos_salemanual = aos_mkt_popular_ppc.getInteger("aos_salemanual");
			if (aos_salemanual == 1)
				aos_entryentity.set("aos_saldescription", "销售手调");

			Map<String, Object> SkuRpt7SerialMap = SkuRpt7Serial.get(aos_productno);// 关键词报告
			BigDecimal aos_roi7days = BigDecimal.ZERO;// 7天ROI系列
			BigDecimal aos_sales = BigDecimal.ZERO;
			BigDecimal aos_spend = BigDecimal.ZERO;
			if (SkuRpt7SerialMap != null) {
				aos_sales = (BigDecimal) SkuRpt7SerialMap.get("aos_total_sales");
				aos_spend = (BigDecimal) SkuRpt7SerialMap.get("aos_spend");
				if (aos_sales != null && aos_spend.compareTo(BigDecimal.ZERO) != 0)
					aos_roi7days = (aos_sales.divide(aos_spend, 2, BigDecimal.ROUND_HALF_UP));
			}
			String aos_roitype = GetRoiType(PopOrgRoist, WORST, WORRY, STANDARD, WELL, EXCELLENT, aos_roi7days);
			aos_entryentity.set("aos_roi7days", aos_roi7days);
			aos_entryentity.set("aos_roitype", aos_roitype);
			aos_entryentity.set("aos_kwcount", PpcTodaySerial.get(aos_productno));

			Map<String, Object> SkuRptDetailSerialMap = SkuRptDetailSerial.get(aos_productno);// Sku报告1日数据
			BigDecimal CostRate = BigDecimal.ZERO; // 花出率 =报告中花费/预算
			BigDecimal LastSpend = BigDecimal.ZERO;
			BigDecimal LastBudget = BigDecimal.ZERO;
			if (PpcYesterSerial.get(aos_productno) != null)
				LastBudget = (BigDecimal) PpcYesterSerial.get(aos_productno);
			// 系列花出率 = 前一天花出/预算
			if (SkuRptDetailSerialMap != null) {
				LastSpend = (BigDecimal) SkuRptDetailSerialMap.get("aos_spend");
				if (LastBudget.compareTo(BigDecimal.ZERO) != 0)
					CostRate = LastSpend.divide(LastBudget, 2, BigDecimal.ROUND_HALF_UP);
			}
			aos_entryentity.set("aos_lastrate", CostRate);
			aos_entryentity.set("aos_lastspend_r", LastSpend);
			aos_entryentity.set("aos_lastbudget", LastBudget);
		}

		// 子表初始化
		for (DynamicObject aos_entryentity : aos_entryentityS) {
			try {
				String aos_serialname = aos_entryentity.getString("aos_serialname");
				Map<String, Map<String, Object>> PpcYster7SerialMap = PpcYsterSerial7
						.get(aos_serialname);// PPC7日

				Map<String, Object> PpcYster7SerialGMap = PpcYsterSerialG
						.get(aos_serialname);// PPC7日
				
				
				Map<String, Map<String, Object>> SkuRpt7SerialMap = SkuRpt7SerialD.get(aos_serialname);// PPC7日
				if (PpcYster7SerialMap == null)
					continue;
				if (PpcYster7SerialGMap == null)
					continue;
				
				
				
				DynamicObjectCollection aos_subentryentityS = aos_entryentity
						.getDynamicObjectCollection("aos_detailentry");
				Map<String, Object> SkuRpt7SerialMapD = new HashMap<>();
				// 循环ROI
				for (String DateStr : PpcYster7SerialMap.keySet()) {
					Date aos_date_d = DF.parse(DateStr);
					Map<String, Object> PpcYster7MapD = PpcYster7SerialMap.get(DateStr);
					Object aos_skucount = PpcYster7SerialGMap.get(DateStr);
					
					BigDecimal aos_budget = BigDecimal.ZERO;
					Object aos_keycount = null;
					
					if (PpcYster7MapD != null) {
						aos_budget = (BigDecimal) PpcYster7MapD.get("aos_budget");
						aos_keycount = PpcYster7MapD.get("aos_keycount");
					}
					
					BigDecimal aos_roi_d = BigDecimal.ZERO;
					BigDecimal aos_spend_d = BigDecimal.ZERO;
					BigDecimal aos_spendrate_d = BigDecimal.ZERO;
					if (SkuRpt7SerialMap != null)
						SkuRpt7SerialMapD = SkuRpt7SerialMap.get(DateStr);
					if (SkuRpt7SerialMapD != null) {
						aos_roi_d = (BigDecimal) SkuRpt7SerialMapD.get("aos_roi");
						aos_spend_d = (BigDecimal) SkuRpt7SerialMapD.get("aos_spend");
						if (aos_spend_d != null && aos_budget.compareTo(BigDecimal.ZERO) != 0)
							aos_spendrate_d = aos_spend_d.divide(aos_budget, 2, BigDecimal.ROUND_HALF_UP);
					}
					DynamicObject aos_subentryentity = aos_subentryentityS.addNew();
					aos_subentryentity.set("aos_date_d", aos_date_d);
					aos_subentryentity.set("aos_roi_d", aos_roi_d);
					aos_subentryentity.set("aos_spend_d", aos_spend_d);
					aos_subentryentity.set("aos_spendrate_d", aos_spendrate_d);
					aos_subentryentity.set("aos_budget_d", aos_budget);
					aos_subentryentity.set("aos_kwcount_d", aos_keycount);
					aos_subentryentity.set("aos_skucount_d", aos_skucount);
				}
			} catch (Exception ex) {
				ex.printStackTrace();
				String message = ex.toString();
				String exceptionStr = SalUtil.getExceptionStr(ex);
				String messageStr = message + "\r\n" + exceptionStr;
				logger.error(messageStr);
			}
		}

		OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_popbudget_stdt",
				new DynamicObject[] { aos_mkt_popbudget_data }, OperateOption.create());
		if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
		}
		aos_mkt_popular_ppcS.close();
	}

	private static String GetRoiType(BigDecimal popOrgRoist, BigDecimal wORST, BigDecimal wORRY, BigDecimal sTANDARD,
			BigDecimal wELL, BigDecimal eXCELLENT, BigDecimal roi7Days_Serial) {
		String RoiType = null;
		if (roi7Days_Serial.compareTo(popOrgRoist.add(wORST)) < 0)
			RoiType = "WORST";
		else if (roi7Days_Serial.compareTo(popOrgRoist.add(wORRY)) >= 0
				&& roi7Days_Serial.compareTo(popOrgRoist.add(sTANDARD)) < 0)
			RoiType = "WORRY";
		else if (roi7Days_Serial.compareTo(popOrgRoist.add(sTANDARD)) >= 0
				&& roi7Days_Serial.compareTo(popOrgRoist.add(wELL)) < 0)
			RoiType = "STANDARD";
		else if (roi7Days_Serial.compareTo(popOrgRoist.add(wELL)) >= 0
				&& roi7Days_Serial.compareTo(popOrgRoist.add(eXCELLENT)) < 0)
			RoiType = "WELL";
		else if (roi7Days_Serial.compareTo(popOrgRoist.add(eXCELLENT)) >= 0)
			RoiType = "EXCELLENT";
		return RoiType;
	}

	private static HashMap<String, BigDecimal> InitAdjsMap(String p_ou_code, Date today) {
		HashMap<String, BigDecimal> InitAdjs = new HashMap<>();
//		String SelectColumn = "aos_entryentity.aos_bid * 2 aos_bid," + "aos_entryentity.aos_productno aos_productno";
		
		String SelectColumn = "aos_bid * 2 aos_bid," + "aos_productno";
		
		
		QFilter Qf_Date = new QFilter("aos_date", "=", today);
		QFilter filter_bill = new QFilter("aos_orgid.number", "=", p_ou_code);
		QFilter[] filters = new QFilter[] { Qf_Date, filter_bill };
		DataSet aos_mkt_popular_ppcS = QueryServiceHelper.queryDataSet("aos_mkt_popbudpst_init" + "." + "InitAdjsMap",
				"aos_mkt_pop_ppcst", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_productno" };
		aos_mkt_popular_ppcS = aos_mkt_popular_ppcS.groupBy(GroupBy).sum("aos_bid").finish();
		while (aos_mkt_popular_ppcS.hasNext()) {
			Row aos_mkt_popular_ppc = aos_mkt_popular_ppcS.next();
			InitAdjs.put(aos_mkt_popular_ppc.getString("aos_productno"), aos_mkt_popular_ppc.getBigDecimal("aos_bid"));
		}
		aos_mkt_popular_ppcS.close();
		return InitAdjs;
	}

}