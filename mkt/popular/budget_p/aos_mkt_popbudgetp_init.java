package mkt.popular.budget_p;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.SerializationUtils;

import common.Cux_Common_Utl;
import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
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
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.threads.ThreadPools;
import mkt.common.aos_mkt_common_redis;
import sal.quote.CommData;
import sal.synciface.imp.aos_sal_import_pub;

public class aos_mkt_popbudgetp_init extends AbstractTask {
	private static final DistributeSessionlessCache cache = CacheFactory.getCommonCacheFactory()
			.getDistributeSessionlessCache("mkt_redis");
	private static Log logger = LogFactory.getLog(aos_mkt_popbudgetp_init.class);
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
			logger.info("MKT_PPC预算调整_" + p_ou_code);
			Map<String, Object> params = new HashMap<>();
			params.put("p_ou_code", ou.getString("number"));
			MktPopBudPRunnable mktPopBudPRunnable = new MktPopBudPRunnable(params);
			ThreadPools.executeOnce("MKT_PPC预算调整_" + p_ou_code, mktPopBudPRunnable);
		}
	}

	static class MktPopBudPRunnable implements Runnable {
		private Map<String, Object> params = new HashMap<>();

		public MktPopBudPRunnable(Map<String, Object> param) {
			this.params = param;
		}

		@Override
		public void run() {
			try {
				CommData.init();
				aos_mkt_common_redis.init_redis("ppcbudget_p");
				do_operate(params);
			} catch (Exception e) {
				String message = e.toString();
				String exceptionStr = SalUtil.getExceptionStr(e);
				String messageStr = message + "\r\n" + exceptionStr;
				System.out.println(messageStr);
				logger.error(messageStr);
			}
		}
	}

	@SuppressWarnings("deprecation")
	public static void do_operate(Map<String, Object> params) {
		System.out.println("===== into aos_mkt_popbudgetp_init =====");
		logger.info("===== into aos_mkt_popbudgetp_init =====");
		
		SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");
		String p_ou_code = (String) params.get("p_ou_code");
		Object p_org_id = aos_sal_import_pub.get_import_id(p_ou_code, "bd_country");
		byte[] serialize_skurptSerial7 = cache.getByteValue("mkt_skurptSerial7"); // SKU报告系列7日
		HashMap<String, Map<String, Object>> SkuRpt_Serial = SerializationUtils.deserialize(serialize_skurptSerial7);
		byte[] serialize_poporgInfo = cache.getByteValue("mkt_poporginfo"); // 营销国别参数表
		HashMap<String, Map<String, Object>> PopOrgInfo = SerializationUtils.deserialize(serialize_poporgInfo);
		byte[] serialize_skurptdetailSerial = cache.getByteValue("mkt_skurptDetailSerial"); // SKU报告系列1日
		HashMap<String, Map<String, Object>> SkuRptDetailSerial = SerializationUtils
				.deserialize(serialize_skurptdetailSerial);
		byte[] serialize_ppcYesterSerial = cache.getByteValue("mkt_ppcyestSerial"); // PPC昨日数据
		HashMap<String, Map<String, Object>> PpcYesterSerial = SerializationUtils
				.deserialize(serialize_ppcYesterSerial);
		byte[] serialize_ppcyster7Serial = cache.getByteValue("mkt_ppcyster7Serial"); // PPC7日
		HashMap<String, Map<String, Map<String, Object>>> PpcYsterSerial7 = SerializationUtils
				.deserialize(serialize_ppcyster7Serial);
		byte[] serialize_skurpt7Serial = cache.getByteValue("mkt_skurpt7Serial"); // SKU报告系列近3日
		HashMap<String, Map<String, Map<String, Object>>> SkuRpt7Serial = SerializationUtils
				.deserialize(serialize_skurpt7Serial);
		logger.info("===== end redis =====");
		BigDecimal PopOrgRoist = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "ROIST").get("aos_value");// 国别标准ROI
		BigDecimal WORST = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "WORST").get("aos_value");// 很差
		BigDecimal WORRY = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "WORRY").get("aos_value");// 差
		BigDecimal STANDARD = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "STANDARD").get("aos_value");// 标准
		BigDecimal WELL = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "WELL").get("aos_value");// 好
		BigDecimal EXCELLENT = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "EXCELLENT").get("aos_value");// 优
		Calendar today = Calendar.getInstance();
		today.set(Calendar.HOUR_OF_DAY, 0);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.SECOND, 0);
		today.set(Calendar.MILLISECOND, 0);
		Date Today = today.getTime();
		QFilter filter_id = new QFilter("aos_orgid", "=", p_org_id);
		QFilter filter_date = new QFilter("aos_makedate", "=", Today);
		QFilter[] filters_adj = new QFilter[] { filter_id, filter_date };
		DeleteServiceHelper.delete("aos_mkt_pop_budgetp", filters_adj);
		DeleteServiceHelper.delete("aos_mkt_popbudget_data", filters_adj);
		HashMap<String, BigDecimal> AdjsMap = InitAdjsMap(p_ou_code,Today);
		QFilter Qf_Date = new QFilter("aos_date", "=", Today);
		QFilter Qf_Org = new QFilter("aos_orgid.number", "=", p_ou_code);
		QFilter Qf_type = new QFilter("aos_entryentity.aos_groupstatus", "=", "AVAILABLE");
		
		//设置出价调整日期Start
		QFilter[] filters = new QFilter[] { Qf_Date, Qf_Org};
		String Select = "id";
		DynamicObject ppc = QueryServiceHelper.queryOne("aos_mkt_popular_ppc", Select, filters);
		long fid = ppc.getLong("id");
		ppc = BusinessDataServiceHelper.loadSingle(fid, "aos_mkt_popular_ppc");
		DynamicObjectCollection aos_bid_diff = ppc.getDynamicObjectCollection("aos_entryentity");
		for (DynamicObject d : aos_bid_diff) {
			BigDecimal aos_bid = d.getBigDecimal("aos_bid");
			BigDecimal aos_bid_ori = d.getBigDecimal("aos_bid_ori");
			if (aos_bid.compareTo(aos_bid_ori) != 0)
				d.set("aos_lastpricedate", Today);
		}
		OperationResult operationrst1 = OperationServiceHelper.executeOperate("save", "aos_mkt_popular_ppc",
				new DynamicObject[] { ppc }, OperateOption.create());
		if (operationrst1.getValidateResult().getValidateErrors().size() != 0) {
		}
		//设置出价调整日期 End
		filters = new QFilter[] { Qf_Date, Qf_Org, Qf_type };
		String SelectField = "1 aos_skucount,aos_billno,aos_orgid,aos_channel,aos_poptype,"
				+ "aos_orgid.number aos_orgnumber," + "aos_entryentity.aos_productno aos_productno,"
				+ "aos_entryentity.aos_seasonseting aos_seasonseting," + "aos_entryentity.aos_budget aos_budget,"
						+ "id, case when aos_entryentity.aos_salemanual = 'Y' then 1 "
						+ "  when aos_entryentity.aos_salemanual = 'N' then 0 end as aos_salemanual";
		DataSet aos_mkt_popular_ppcS = QueryServiceHelper.queryDataSet("aos_mkt_popbudgetp_init" + "." + p_ou_code,
				"aos_mkt_popular_ppc", SelectField, filters, null);
		String[] GroupBy = new String[] { "aos_orgid", "aos_productno", "aos_seasonseting", "aos_billno", "aos_channel",
				"aos_poptype", "aos_orgnumber", "aos_budget","id" };
		aos_mkt_popular_ppcS = aos_mkt_popular_ppcS.groupBy(GroupBy).sum("aos_skucount").max("aos_salemanual").finish();
		int count = 0;
		DynamicObject aos_mkt_popbudget_data = BusinessDataServiceHelper.newDynamicObject("aos_mkt_popbudget_data");
		DynamicObjectCollection aos_entryentityS = aos_mkt_popbudget_data.getDynamicObjectCollection("aos_entryentity");
		while (aos_mkt_popular_ppcS.hasNext()) {
			Row aos_mkt_popular_ppc = aos_mkt_popular_ppcS.next();
			count++;
			if (count == 1) {
				System.out.println(p_ou_code + count);
				// 初始化 预算调整(推广)
				DynamicObject aos_mkt_pop_budgetp = BusinessDataServiceHelper.newDynamicObject("aos_mkt_pop_budgetp");
				aos_mkt_pop_budgetp.set("aos_billno", aos_mkt_popular_ppc.get("aos_billno"));
				aos_mkt_pop_budgetp.set("aos_orgid", p_org_id);
				aos_mkt_pop_budgetp.set("billstatus", "A");
				aos_mkt_pop_budgetp.set("aos_status", "A");
				aos_mkt_pop_budgetp.set("aos_ppcid", aos_mkt_popular_ppc.get("id"));
				aos_mkt_pop_budgetp.set("aos_makeby", SYSTEM);
				aos_mkt_pop_budgetp.set("aos_makedate", Today);
				OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_pop_budgetp",
						new DynamicObject[] { aos_mkt_pop_budgetp }, OperateOption.create());
				Object aos_sourceid = operationrst.getSuccessPkIds().get(0);
				// 初始化 预算调整(推广)数据表
				aos_mkt_popbudget_data.set("aos_billno", aos_mkt_popular_ppc.get("aos_billno"));
				aos_mkt_popbudget_data.set("billno", aos_mkt_popular_ppc.get("aos_billno"));
				aos_mkt_popbudget_data.set("aos_orgid", p_org_id);
				aos_mkt_popbudget_data.set("billstatus", "A");
				aos_mkt_popbudget_data.set("aos_sourceid", aos_sourceid);
				aos_mkt_popbudget_data.set("aos_makedate", Today);
			}
			Object aos_channel = aos_mkt_popular_ppc.get("aos_channel");
			Object aos_poptype = aos_mkt_popular_ppc.get("aos_poptype");
			String aos_productno = aos_mkt_popular_ppc.getString("aos_productno");
			Object aos_seasonseting = aos_mkt_popular_ppc.get("aos_seasonseting");
			Object aos_skucount = aos_mkt_popular_ppc.get("aos_skucount");
			Object aos_salemanual = aos_mkt_popular_ppc.get("aos_salemanual"); 
			
			BigDecimal aos_budget = aos_mkt_popular_ppc.getBigDecimal("aos_budget");
			Object aos_seasonsetingid = aos_sal_import_pub.get_import_id2(aos_seasonseting, "aos_scm_seasonatt");
			BigDecimal Roi7Days_Serial = BigDecimal.ZERO;// 7天ROI 系列维度
			Map<String, Object> SkuRptMap_Serial = SkuRpt_Serial.get(p_org_id + "~" + aos_productno);// Sku报告
			Map<String, Object> PpcYesterSerialMap = PpcYesterSerial.get(p_org_id + "~" + aos_productno);// 默认昨日数据
			if (SkuRptMap_Serial != null
					&& ((BigDecimal) SkuRptMap_Serial.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0)
				Roi7Days_Serial = ((BigDecimal) SkuRptMap_Serial.get("aos_total_sales"))
						.divide((BigDecimal) SkuRptMap_Serial.get("aos_spend"), 6, BigDecimal.ROUND_HALF_UP);
			String aos_roitype = null;
			if (Roi7Days_Serial.compareTo(BigDecimal.ZERO) != 0)
				aos_roitype = GetRoiType(PopOrgRoist, WORST, WORRY, STANDARD, WELL, EXCELLENT, Roi7Days_Serial);
			BigDecimal CostRate = BigDecimal.ZERO; // 花出率 =报告中花费/预算
			BigDecimal LastSpend = BigDecimal.ZERO;
			BigDecimal LastBudget = BigDecimal.ZERO;
			
			
			Object LastSpendObj = SkuRptDetailSerial.get(p_org_id + "~" + aos_mkt_popular_ppc.get("aos_productno"));// Sku报告1日数据
			if (!Cux_Common_Utl.IsNull(LastSpendObj)) {
				LastSpend = (BigDecimal) LastSpendObj;
			}
			
			if ( PpcYesterSerialMap != null) {
				LastBudget = (BigDecimal) PpcYesterSerialMap.get("aos_budget");// 昨日预算
				if (LastBudget.compareTo(BigDecimal.ZERO) != 0)
					CostRate = LastSpend.divide(LastBudget, 2, BigDecimal.ROUND_HALF_UP);
			}
			DynamicObject aos_entryentity = aos_entryentityS.addNew();
			aos_entryentity.set("aos_channel_r", aos_channel);
			aos_entryentity.set("aos_poptype_r", aos_poptype);
			aos_entryentity.set("aos_serialname", aos_productno);
			aos_entryentity.set("aos_season_r", aos_seasonsetingid);
			aos_entryentity.set("aos_roi7days", Roi7Days_Serial);
			aos_entryentity.set("aos_roitype", aos_roitype);
			aos_entryentity.set("aos_skucount", aos_skucount);
			aos_entryentity.set("aos_lastrate", CostRate);
			aos_entryentity.set("aos_lastspend_r", LastSpend);
			aos_entryentity.set("aos_lastbudget", LastBudget);
			aos_entryentity.set("aos_calbudget", aos_budget);
			if ((int)aos_salemanual == 1) 
				aos_entryentity.set("aos_saldescription", "销售手调");
			// 系列获取 出价调整 金额
			BigDecimal AdjsBid = BigDecimal.ZERO;
			if  (AdjsMap.get(aos_productno)!= null)
				AdjsBid = AdjsMap.get(aos_productno);
			aos_entryentity.set("aos_adjbudget", AdjsBid);
			aos_entryentity.set("aos_popbudget", max(aos_budget,AdjsBid));
		}

		aos_mkt_popular_ppcS.close();
		// 子表初始化
		for (DynamicObject aos_entryentity : aos_entryentityS) {
			try {
				String aos_serialname = aos_entryentity.getString("aos_serialname");
				Map<String, Map<String, Object>> PpcYster7SerialMap = PpcYsterSerial7
						.get(p_org_id + "~" + aos_serialname);// PPC7日
				Map<String, Map<String, Object>> SkuRpt7SerialMap = SkuRpt7Serial.get(p_org_id + "~" + aos_serialname);// PPC7日
				if (PpcYster7SerialMap == null)
					continue;
				DynamicObjectCollection aos_subentryentityS = aos_entryentity
						.getDynamicObjectCollection("aos_subentryentity");
				Map<String, Object> SkuRpt7SerialMapD = new HashMap<>();
				// 循环ROI
				for (String DateStr : PpcYster7SerialMap.keySet()) {
					Date aos_date_d = DF.parse(DateStr);
					Map<String, Object> PpcYster7MapD = PpcYster7SerialMap.get(DateStr);
					BigDecimal aos_budget = BigDecimal.ZERO;
					Object aos_skucount = null;
					if (PpcYster7MapD != null) {
						aos_budget = (BigDecimal) PpcYster7MapD.get("aos_budget");
						aos_skucount = PpcYster7MapD.get("aos_skucount");
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
					aos_subentryentity.set("aos_budget_d", aos_budget);
					aos_subentryentity.set("aos_skucount_d", aos_skucount);
					aos_subentryentity.set("aos_roi_d", aos_roi_d);
					aos_subentryentity.set("aos_spend_d", aos_spend_d);
					aos_subentryentity.set("aos_spendrate_d", aos_spendrate_d);
				}
			} catch (Exception ex) {
				String message = ex.toString();
				String exceptionStr = SalUtil.getExceptionStr(ex);
				String messageStr = message + "\r\n" + exceptionStr;
				System.out.println(messageStr);
				logger.error(messageStr);
			}
		}

		OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_popbudget_data",
				new DynamicObject[] { aos_mkt_popbudget_data }, OperateOption.create());
		if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
		}
	}

	private static HashMap<String, BigDecimal> InitAdjsMap(String p_ou_code, Date today) {
		HashMap<String,BigDecimal> InitAdjs = new HashMap<>();
		String SelectColumn = "aos_entryentity.aos_bid * 20 aos_bid,"
				+ "aos_entryentity.aos_productno aos_productno";
		QFilter Qf_Date = new QFilter("aos_date", "=", today);
		QFilter filter_bill = new QFilter("aos_orgid.number", "=", p_ou_code);
		QFilter[] filters = new QFilter[] { Qf_Date, filter_bill };
		DataSet aos_mkt_popular_ppcS = QueryServiceHelper.queryDataSet("aos_mkt_popbudgetp_init" + "." + "InitAdjsMap",
				"aos_mkt_popular_ppc", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_productno" };
		aos_mkt_popular_ppcS = aos_mkt_popular_ppcS.groupBy(GroupBy).sum("aos_bid").finish();
		while (aos_mkt_popular_ppcS.hasNext()) {
			Row aos_mkt_popular_ppc = aos_mkt_popular_ppcS.next();
			InitAdjs.put(aos_mkt_popular_ppc.getString("aos_productno"),
					aos_mkt_popular_ppc.getBigDecimal("aos_bid"));
		}
		aos_mkt_popular_ppcS.close();
		return InitAdjs;
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
	
	private static BigDecimal max(BigDecimal Value1, BigDecimal Value2) {
		if (Value1.compareTo(Value2) > -1)
			return Value1;
		else
			return Value2;
	}
}