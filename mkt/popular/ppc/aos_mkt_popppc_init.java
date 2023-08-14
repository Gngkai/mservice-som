package mkt.popular.ppc;

import common.StringComUtils;
import common.fnd.AosomLog;
import common.fnd.FndDate;
import common.fnd.FndGlobal;
import common.fnd.FndLog;
import common.fnd.FndWebHook;
import common.Cux_Common_Utl;
import common.sal.util.InStockAvailableDays;
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
import kd.bos.db.DB;
import kd.bos.db.DBRoute;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.exception.KDException;
import kd.bos.orm.ORM;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.fi.bd.util.QFBuilder;
import mkt.common.MKTCom;
import mkt.common.aos_mkt_common_redis;
import mkt.popular.aos_mkt_pop_common;
import mkt.popular.adjust_s.aos_mkt_popadds_init;
import mkt.popular.adjust_s.aos_mkt_popadjs_init;
import mkt.progress.iface.iteminfo;

import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import sal.quote.CommData;
import sal.sche.aos_sal_sche_pub.aos_sal_sche_pub;
import sal.synciface.imp.aos_sal_import_pub;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public class aos_mkt_popppc_init extends AbstractTask {

	private static final DistributeSessionlessCache cache = CacheFactory.getCommonCacheFactory()
			.getDistributeSessionlessCache("mkt_redis");
	private static final String DB_MKT = "aos.mkt";
	private static AosomLog logger = AosomLog.init("aos_mkt_popppc_init");
	static {
		logger.setService("aos.mms");
		logger.setDomain("mms.popular");
	}
	public static final String urlSP = "https://open.feishu.cn/open-apis/bot/v2/hook/242e7160-8309-4312-8586-5fe58482d8c5";

	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		executerun();
		FndWebHook.send(urlSP, "PPC推广SP初始化已成功生成!");
	}

	public static void ManualitemClick(String aos_ou_code) {
		// 删除数据
		Calendar today = Calendar.getInstance();
		today.set(Calendar.HOUR_OF_DAY, 0);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.SECOND, 0);
		today.set(Calendar.MILLISECOND, 0);
		Date Today = today.getTime();
		QFilter filter_id = new QFilter("aos_orgid.number", "=", aos_ou_code);
		QFilter filter_date = new QFilter("aos_date", "=", Today);
		QFilter[] filters_adj = new QFilter[] { filter_id, filter_date };
		DeleteServiceHelper.delete("aos_mkt_popular_ppc", filters_adj);
		// 初始化数据
		CommData.init();
		aos_mkt_common_redis.init_redis("ppc");
		Map<String, Object> params = new HashMap<>();
		params.put("p_ou_code", aos_ou_code);
		do_operate(params);
	}

	private static void executerun() {
		// 初始化数据
		CommData.init();
		aos_mkt_common_redis.init_redis("ppc");

		Calendar Today = Calendar.getInstance();
		int hour = Today.get(Calendar.HOUR_OF_DAY);
		int week = Today.get(Calendar.DAY_OF_WEEK);
		System.out.println("hour =" + hour);
		long is_oversea_flag = aos_sal_sche_pub.get_lookup_values("AOS_YES_NO", "Y");
		QFilter qf_time = null;

		DynamicObject dynamicObject = QueryServiceHelper.queryOne("aos_mkt_base_orgvalue", "aos_value",
				new QFilter[] { new QFilter("aos_type", QCP.equals, "TIME") });
		int time = 16;
		if (dynamicObject != null) {
			time = dynamicObject.getBigDecimal("aos_value").intValue();
		}
		if (hour < time)
			qf_time = new QFilter("aos_is_north_america", QCP.not_equals, is_oversea_flag);
		else
			qf_time = new QFilter("aos_is_north_america", QCP.equals, is_oversea_flag);
		QFilter oversea_flag = new QFilter("aos_is_oversea_ou", "=", is_oversea_flag);// 海外公司
		QFilter oversea_flag2 = new QFilter("aos_isomvalid", "=", true);
		QFilter[] filters_ou = new QFilter[] { oversea_flag, oversea_flag2, qf_time };
		DynamicObjectCollection bd_country = QueryServiceHelper.query("bd_country", "id,number", filters_ou);
		for (DynamicObject ou : bd_country) {
			String p_ou_code = ou.getString("number");
			logger.info("MKT_PPC操作平台数据源  " + p_ou_code);
			Map<String, Object> params = new HashMap<>();
			params.put("p_ou_code", p_ou_code);
			do_operate(params);
		}

		// 生成销售加回数据
		Boolean CopyFlag = aos_mkt_pop_common.GetCopyFlag("PPC_ADD", week);
		if (CopyFlag)
			aos_mkt_popadds_init.Run();
	}

	static class MktPopPpcRunnable implements Runnable {
		private Map<String, Object> params = new HashMap<>();

		public MktPopPpcRunnable(Map<String, Object> param) {
			this.params = param;
		}

		@Override
		public void run() {
			try {
				do_operate(params);
			} catch (Exception e) {
			}
		}
	}

	public static void do_operate(Map<String, Object> params) {
		Object p_ou_code = params.get("p_ou_code");
		try {
			System.out.println("===== into aos_mkt_popppc_init =====");
			// 获取缓存
			byte[] serialize_item = cache.getByteValue("item");
			HashMap<String, Map<String, Object>> item = SerializationUtils.deserialize(serialize_item);
			Map<String, Object> item_overseaqty_map = item.get("item_overseaqty");// 海外在库数量
			Map<String, Object> item_intransqty_map = item.get("item_intransqty");// 在途数量
			Map<String, Object> item_maxage_map = item.get("item_maxage");// 在途数量
			byte[] serialize_datepara = cache.getByteValue("datepara");
			HashMap<String, Map<String, Object>> datepara = SerializationUtils.deserialize(serialize_datepara);
			Map<String, Object> aos_shpday_map = datepara.get("aos_shp_day");// 备货天数
			Map<String, Object> aos_clearday_map = datepara.get("aos_clear_day");// 清关天数
			Map<String, Object> aos_freightday_map = datepara.get("aos_freight_day");// 海运天数

			// byte[] serialize_ProductInfo = cache.getByteValue("mkt_productinfo");//
			// Amazon店铺货号
			// HashMap<String, String> mkt_productinfo =
			// SerializationUtils.deserialize(serialize_ProductInfo);

			byte[] serialize_ppcInfo = cache.getByteValue("mkt_ppcinfo"); // PPC系列与组创建日期
			HashMap<String, Map<String, Object>> PPCInfo = SerializationUtils.deserialize(serialize_ppcInfo);
			byte[] serialize_ppcInfoSerial = cache.getByteValue("mkt_ppcinfoSerial"); // PPC系列与组创建日期
			HashMap<String, Map<String, Object>> PPCInfoSerial = SerializationUtils
					.deserialize(serialize_ppcInfoSerial);
			byte[] serialize_poporgInfo = cache.getByteValue("mkt_poporginfo"); // 营销国别参数表
			HashMap<String, Map<String, Object>> PopOrgInfo = SerializationUtils.deserialize(serialize_poporgInfo);
			byte[] serialize_SalSummary = cache.getByteValue("mkt_salsummary"); // 预测汇总表
			HashMap<String, Map<String, Object>> SalSummary = SerializationUtils.deserialize(serialize_SalSummary);
			byte[] serialize_ppcYester = cache.getByteValue("mkt_ppcyest"); // PPC昨日数据
			HashMap<String, Map<String, Object>> PpcYester = SerializationUtils.deserialize(serialize_ppcYester);
			byte[] serialize_ppcyestSerial = cache.getByteValue("mkt_ppcyestSerial"); // PPC昨日数据系列
			HashMap<String, Map<String, Object>> PpcYesterSerial = SerializationUtils
					.deserialize(serialize_ppcyestSerial);
			byte[] serialize_DailyPrice = cache.getByteValue("mkt_dailyprice"); // Amazon每日价格
			HashMap<String, Object> DailyPrice = SerializationUtils.deserialize(serialize_DailyPrice);
			byte[] serialize_skurpt = cache.getByteValue("mkt_skurpt"); // SKU报告7日
			HashMap<String, Map<String, Object>> SkuRpt = SerializationUtils.deserialize(serialize_skurpt);
			byte[] serialize_skurpt3 = cache.getByteValue("mkt_skurpt3"); // SKU报告3日
			HashMap<String, Map<String, Object>> SkuRpt3 = SerializationUtils.deserialize(serialize_skurpt3);
			byte[] serialize_skurpt14 = cache.getByteValue("mkt_skurpt14"); // SKU报告14日
			HashMap<String, Map<String, Object>> SkuRpt14 = SerializationUtils.deserialize(serialize_skurpt14);
			byte[] serialize_adprice = cache.getByteValue("mkt_adprice"); // 建议价格
			HashMap<String, Map<String, Object>> AdPrice = SerializationUtils.deserialize(serialize_adprice);
			byte[] serialize_skurptSerial7 = cache.getByteValue("mkt_skurptSerial7"); // SKU报告系列7日
			HashMap<String, Map<String, Object>> SkuRpt_Serial = SerializationUtils
					.deserialize(serialize_skurptSerial7);
			byte[] serialize_skurpt3Serial = cache.getByteValue("mkt_skurpt3Serial"); // SKU报告系列近3日
			HashMap<String, Map<String, Map<String, Object>>> SkuRpt3Serial = SerializationUtils
					.deserialize(serialize_skurpt3Serial);
			byte[] serialize_skurptdetailSerial = cache.getByteValue("mkt_skurptDetailSerial"); // SKU报告系列1日
			HashMap<String, BigDecimal> SkuRptDetailSerial = SerializationUtils
					.deserialize(serialize_skurptdetailSerial);
			byte[] serialize_SalSummarySerial = cache.getByteValue("mkt_salsummarySerial"); // 预测汇总表
			HashMap<String, Map<String, Object>> SalSummaryMap = SerializationUtils
					.deserialize(serialize_SalSummarySerial);
			byte[] serialize_skurpt3avg = cache.getByteValue("mkt_skurpt3avg"); // SKU报表三日平均
			HashMap<String, Map<String, Object>> SkuRpt3Avg = SerializationUtils.deserialize(serialize_skurpt3avg);
			byte[] serialize_BaseStItem = cache.getByteValue("mkt_basestitem"); // 国别货号滞销类型
			HashMap<String, Object> BaseStItem = SerializationUtils.deserialize(serialize_BaseStItem);
			byte[] serialize_SkuRptDetail = cache.getByteValue("mkt_skurptDetail"); // 国别货号滞销类型
			HashMap<String, Map<String, Object>> SkuRptDetail = SerializationUtils.deserialize(serialize_SkuRptDetail);
			byte[] mkt_itemavadays = cache.getByteValue("mkt_itemavadays"); // 物料可售库存
			HashMap<String, Integer> map_itemavadays = SerializationUtils.deserialize(mkt_itemavadays);

			// 获取传入参数 国别
			Object p_org_id = aos_sal_import_pub.get_import_id(p_ou_code, "bd_country");
			int SafeQty = iteminfo.GetSafeQty(p_org_id);// 国别安全库存

			// 获取当前日期
			Calendar date = Calendar.getInstance();
			date.set(Calendar.HOUR_OF_DAY, 0);
			date.set(Calendar.MINUTE, 0);
			date.set(Calendar.SECOND, 0);
			date.set(Calendar.MILLISECOND, 0);
			Date Today = date.getTime();
			// 获取单号
			int year = date.get(Calendar.YEAR) - 2000;
			int month = date.get(Calendar.MONTH) + 1;
			int day = date.get(Calendar.DAY_OF_MONTH);
			int week = date.get(Calendar.DAY_OF_WEEK);
			System.out.println("week =" + week);

			SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");
			date.add(Calendar.DAY_OF_MONTH, -1);
			String Yester = DF.format(date.getTime());
			date.add(Calendar.DAY_OF_MONTH, -1);
			String Yester2 = DF.format(date.getTime());
			date.add(Calendar.DAY_OF_MONTH, -1);
			String Yester3 = DF.format(date.getTime());
			String aos_billno = "SP" + p_ou_code + year + month + day;
			// 如果是 2467 则赋值昨日数据 直接退出
			Boolean CopyFlag = aos_mkt_pop_common.GetCopyFlag("PPC_SP", week);
			if (!CopyFlag) {
				DynamicObject aos_mkt_popular_ppc = BusinessDataServiceHelper.newDynamicObject("aos_mkt_popular_ppc");
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
				QFilter filter_org = new QFilter("aos_orgid", "=", p_org_id);
				QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_org };
				long fid = QueryServiceHelper.queryOne("aos_mkt_popular_ppc", "id", filters).getLong("id");
				DynamicObject aos_mkt_pop_ppclast = BusinessDataServiceHelper.loadSingle(fid, "aos_mkt_popular_ppc");
				aos_mkt_popular_ppc.set("aos_orgid", p_org_id);
				aos_mkt_popular_ppc.set("billstatus", "A");
				aos_mkt_popular_ppc.set("aos_status", "A");
				aos_mkt_popular_ppc.set("aos_adjusts", true);
				aos_mkt_popular_ppc.set("aos_adjustsp", true);
				aos_mkt_popular_ppc.set("aos_budgetp", true);
				aos_mkt_popular_ppc.set("aos_billno", aos_billno);
				Object aos_poptype = aos_sal_import_pub.get_import_id("SP", "aos_mkt_base_poptype");
				aos_mkt_popular_ppc.set("aos_poptype", aos_poptype);
				aos_mkt_popular_ppc.set("aos_date", Today);
				Object platform_id = aos_sal_import_pub.get_import_id("AMAZON", "aos_sal_channel");
				aos_mkt_popular_ppc.set("aos_channel", platform_id);
				DynamicObjectCollection aos_entryentityS = aos_mkt_popular_ppc
						.getDynamicObjectCollection("aos_entryentity");
				DynamicObjectCollection aos_entryentityLastS = aos_mkt_pop_ppclast
						.getDynamicObjectCollection("aos_entryentity");
				aos_entryentityS.clear();
				for (int i = 0; i < aos_entryentityLastS.size(); i++) {
					DynamicObject dyn = aos_entryentityS.addNew();
					DynamicObject temp = aos_entryentityLastS.get(i);
					StringComUtils.setValue(temp, dyn);
				}
				OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_popular_ppc",
						new DynamicObject[] { aos_mkt_popular_ppc }, OperateOption.create());
				if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
				}
				return;
			}
			// 获取春夏品 秋冬品开始结束日期 营销日期参数表
			Date SummerSpringStart = MKTCom.Get_DateRange("aos_datefrom", "SS", p_org_id);
			Date SummerSpringEnd = MKTCom.Get_DateRange("aos_dateto", "SS", p_org_id);
			Date AutumnWinterStart = MKTCom.Get_DateRange("aos_datefrom", "AW", p_org_id);
			Date AutumnWinterEnd = MKTCom.Get_DateRange("aos_dateto", "AW", p_org_id);
			Date SummerSpringUnStart = MKTCom.Get_DateRange("aos_datefrom", "SSUN", p_org_id);// 春夏品不剔除日期
			Date AutumnWinterUnStart = MKTCom.Get_DateRange("aos_datefrom", "AWUN", p_org_id);// 秋冬品不剔除日期
			Date SummerSpringUnEnd = MKTCom.Get_DateRange("aos_dateto", "SSUN", p_org_id);// 春夏品不剔除日期
			// 判断是否季末
			Date summerSpringSeasonEnd = DateUtils.setDays(DateUtils.addDays(SummerSpringEnd, -32), 1);
			Date AutumnWinterUnEnd = MKTCom.Get_DateRange("aos_dateto", "AWUN", p_org_id);// 秋冬品不剔除日期
			Date autumnWinterSeasonEnd = DateUtils.setDays(DateUtils.addDays(AutumnWinterEnd, -32), 1);
			Date HalloweenStart = MKTCom.Get_DateRange("aos_datefrom", "Halloween", p_org_id);// 万圣节
			Date HalloweenEnd = MKTCom.Get_DateRange("aos_dateto", "Halloween", p_org_id);// 万圣节
			Date ChristmasStart = MKTCom.Get_DateRange("aos_datefrom", "Christmas", p_org_id);// 圣诞节
			Date ChristmasEnd = MKTCom.Get_DateRange("aos_dateto", "Christmas", p_org_id);// 圣诞节
			Map<String, Integer> ProductNo_Map = new HashMap<>();
			Map<String, BigDecimal> ProductNoBid_Map = new HashMap<>();
			// 获取营销国别参数
			BigDecimal PopOrgAMSaleRate = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "AMSALE_RATE").get("aos_value");// AM营收占比
			BigDecimal PopOrgAMAffRate = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "AMAFF_RATE").get("aos_value");// AM付费营收占比
			BigDecimal PopOrgRoist = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "ROIST").get("aos_value");// 国别标准ROI
			BigDecimal WORST = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "WORST").get("aos_value");// 很差
			BigDecimal WORRY = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "WORRY").get("aos_value");// 差
			BigDecimal STANDARD = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "STANDARD").get("aos_value");// 标准
			BigDecimal WELL = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "WELL").get("aos_value");// 好
			BigDecimal EXCELLENT = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "EXCELLENT").get("aos_value");// 优
			BigDecimal PopOrgFirstBid = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "FIRSTBID").get("aos_value");// 国别首次出价均价
			BigDecimal HIGH1 = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "HIGH1").get("aos_value");// 最高标准1(定价<200)
			BigDecimal HIGH2 = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "HIGH2").get("aos_value");// 最高标准2(200<=定价<500)
			BigDecimal HIGH3 = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "HIGH3").get("aos_value");// 最高标准3(定价>=500)
			BigDecimal EXPOSURE = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "EXPOSURE").get("aos_value");// 国别曝光标准
			BigDecimal RoiStandard = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "ROISTANDARD").get("aos_value");// 转化率国别标准
			BigDecimal StandardFix = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "STANDARDFIX").get("aos_value");// 国别标准均价
			BigDecimal QtyStandard = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "QTYSTANDARD").get("aos_value");// 国别海外库存标准
			BigDecimal LOWESTBID = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "LOWESTBID").get("aos_value");
			BigDecimal CLICK = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "CLICK").get("aos_value");// 点击国别标准
			Map<String, BigDecimal> orgCpcMap = queryOrgCpc(p_org_id.toString());
			HashMap<String, Object> Act = GenerateAct();
			Map<String, BigDecimal> VatMap = GenerateVat(p_org_id);
			Map<String, BigDecimal> CostMap = initItemCost();
			Map<String, BigDecimal> ShipFee = GenerateShipFee();
			BigDecimal aos_vat_amount = VatMap.get("aos_vat_amount");
			BigDecimal aos_am_platform = VatMap.get("aos_am_platform");
			Set<String> OnlineSkuSet = GenerateOnlineSkuSet(p_org_id);// 平台上架信息
			Set<String> Deseaon = GenerateDeseaon();
			Set<String> mustSet = GenerateMustSet(p_org_id);
			// 获取昨天的所有差ROI剔除数据
			DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_popular_ppc",
					"aos_entryentity.aos_itemnumer aos_itemnumer",
					new QFilter[] { new QFilter("aos_date", QCP.like, Yester + "%"),
							new QFilter("aos_orgid.number", QCP.equals, p_ou_code),
							new QFilter("aos_entryentity.aos_roiflag", QCP.equals, true), });
			Set<String> roiItemSet = new HashSet<>();
			for (DynamicObject obj : list) {
				roiItemSet.add(obj.getString("aos_itemnumer"));
			}
			// 获取昨天的所有定价毛利剔除数据
			DynamicObjectCollection list_pro = QueryServiceHelper.query("aos_mkt_popular_ppc",
					"aos_entryentity.aos_itemnumer aos_itemnumer",
					new QFilter[] { new QFilter("aos_date", QCP.like, Yester + "%"),
							new QFilter("aos_orgid.number", QCP.equals, p_ou_code),
							new QFilter("aos_entryentity.aos_groupstatus", QCP.equals, "PRO"), });
			Set<String> proItemSet = new HashSet<>();
			for (DynamicObject obj : list_pro) {
				proItemSet.add(obj.getString("aos_itemnumer"));
			}

			// 获取昨天的所有可用组
			DynamicObjectCollection lastAva = QueryServiceHelper.query("aos_mkt_popular_ppc",
					"aos_entryentity.aos_itemnumer aos_itemnumer",
					new QFilter[] { new QFilter("aos_date", QCP.like, Yester + "%"),
							new QFilter("aos_orgid.number", QCP.equals, p_ou_code),
							new QFilter("aos_entryentity.aos_groupstatus", QCP.equals, "AVAILABLE"), });
			Set<String> lastAvaSet = new HashSet<>();
			for (DynamicObject obj : lastAva) {
				lastAvaSet.add(obj.getString("aos_itemnumer"));
			}

			// 获取营销国别滞销货号 春夏滞销
			DynamicObjectCollection baseItem = QueryServiceHelper.query("aos_base_stitem",
					"aos_itemid.number aos_itemnumer",
					new QFilter("aos_orgid", QCP.equals, p_org_id).and("aos_type", QCP.equals, "SPRING").toArray());
			Set<String> baseItemSet = new HashSet<>();
			for (DynamicObject obj : baseItem) {
				baseItemSet.add(obj.getString("aos_itemnumer"));
			}

			// 最近一次上线日期
			Map<String, Date> onlineDate = new HashMap<>();
			DynamicObjectCollection dyns = QueryServiceHelper.query("aos_mkt_sp_date",
					"aos_entryentity.aos_itemstr aos_itemstr," + "aos_entryentity.aos_online aos_online",
					new QFilter[] { new QFilter("aos_orgid", QCP.equals, p_org_id) });
			for (DynamicObject d : dyns) {
				String aos_itemstr = d.getString("aos_itemstr");
				Date aos_online = d.getDate("aos_online");
				onlineDate.put(aos_itemstr, aos_online);
			}

			// 获取项鑫报告数据
			DynamicObjectCollection list_rpt = QueryServiceHelper.query("aos_mkt_tmp", "aos_group",
					new QFilter[] { new QFilter("aos_orgid.number", QCP.equals, p_ou_code) });
			Set<String> rptSet = new HashSet<>();
			for (DynamicObject obj : list_rpt) {
				rptSet.add(obj.getString("aos_group"));
			}
			// 营销出价调整幅度参数表
			String SelectColumn = "aos_roi," + "aos_roitype," + "aos_exposure," + "aos_exposurerate," + "aos_avadays,"
					+ "aos_avadaysvalue," + "aos_costrate," + "aos_costratevalue," + "aos_rate,aos_level,"
					+ "aos_roi3,aos_roitype3";
			DataSet aos_mkt_bsadjrateS = QueryServiceHelper.queryDataSet("aos_mkt_popppc_init." + p_ou_code,
					"aos_mkt_bsadjrate", SelectColumn, null, "aos_level");
			// 营销预算调整系数表 到国别维度
			QFilter filter_org = new QFilter("aos_orgid", "=", p_org_id);
			QFilter[] filters_adjpara = new QFilter[] { filter_org };
			SelectColumn = "aos_ratefrom,aos_rateto,aos_roi,aos_adjratio";
			DataSet aos_mkt_bsadjparaS = QueryServiceHelper.queryDataSet("aos_mkt_popppc_init." + p_ou_code,
					"aos_mkt_bsadjpara", SelectColumn, filters_adjpara, null);
			// 每日价格
			HashMap<String, String> mkt_productinfo = generateProductInfo();
			// 循环国别物料
			QFilter filter_ou = new QFilter("aos_contryentry.aos_nationality.number", "=", p_ou_code);
			QFilter filter_type = new QFilter("aos_protype", "=", "N");
			QFilter[] filters = new QFilter[] { filter_ou, filter_type };
			String SelectField = "id,aos_productno," + "number," + "aos_cn_name,"
					+ "aos_contryentry.aos_nationality.id aos_orgid,"
					+ "aos_contryentry.aos_nationality.number aos_orgnumber,"
					+ "aos_contryentry.aos_seasonseting.number aos_seasonseting,"
					+ "aos_contryentry.aos_seasonseting.name aos_season,"
					+ "aos_contryentry.aos_seasonseting.id aos_seasonid,"
					+ "aos_contryentry.aos_contryentrystatus aos_contryentrystatus,"
					+ "aos_contryentry.aos_festivalseting.number aos_festivalseting,"
					+ "aos_contryentry.aos_firstindate aos_firstindate,"
					+ "aos_contryentry.aos_is_saleout aos_is_saleout," + "aos_isrepliceold," + "aos_oldnumber,"
					+ "aos_contryentry.aos_onshelfreplace aos_onshelfreplace";
			DynamicObjectCollection bd_materialS = QueryServiceHelper.query("bd_material", SelectField, filters,
					"aos_productno");
			int rows = bd_materialS.size();
			int count = 0;
			System.out.println("rows =" + rows);
			DynamicObject aos_mkt_popular_ppc = BusinessDataServiceHelper.newDynamicObject("aos_mkt_popular_ppc");
			aos_mkt_popular_ppc.set("aos_orgid", p_org_id);
			aos_mkt_popular_ppc.set("billstatus", "A");
			aos_mkt_popular_ppc.set("aos_status", "A");
			aos_mkt_popular_ppc.set("aos_billno", aos_billno);
			Object aos_poptype = aos_sal_import_pub.get_import_id("SP", "aos_mkt_base_poptype");
			aos_mkt_popular_ppc.set("aos_poptype", aos_poptype);
			aos_mkt_popular_ppc.set("aos_date", Today);
			Object platform_id = aos_sal_import_pub.get_import_id("AMAZON", "aos_sal_channel");
			aos_mkt_popular_ppc.set("aos_channel", platform_id);
			DynamicObjectCollection aos_entryentityS = aos_mkt_popular_ppc
					.getDynamicObjectCollection("aos_entryentity");
			Set<String> eliminateItemSet = getEliminateItem(p_org_id.toString());
			// 初始化历史记录
			FndLog log = FndLog.init("MKT_PPC数据源初始化", p_ou_code.toString() + year + month + day);

			Map<String, String> roiMap = new HashMap<>();
			// 获取7天内的入库的物料的最新时间，并且根据时间对物料分组
			Map<String, List<String>> map_wareHoseDateToItem = Query7daysWareHouseItem(p_org_id, Today);
			// 判断7天内有入库的物料，入库前30天是否断货
			Map<String, Boolean> map_itemToOutage = new HashMap<>(); // 记录物料的是否存在不断货，存在为true,全断货为false
			// 具体判断断货
			for (Map.Entry<String, List<String>> entry : map_wareHoseDateToItem.entrySet()) {
				// 先全设置物料为断货
				for (String itemID : entry.getValue()) {
					if (!map_itemToOutage.containsKey(itemID))
						map_itemToOutage.put(itemID, false);
				}
				// 判断是否存在不断货
				Judge30DaysStock(p_org_id, entry.getKey(), entry.getValue(), map_itemToOutage);
			}

			// 获取物料前一天的特殊行
			Map<String, String> map_itemToAdd = QuerySaleAdd(p_org_id);
			// 获取固定剔除剔除物料
			List<String> list_itemRejectIds = getRejectItem(p_org_id);

			for (DynamicObject bd_material : bd_materialS) {
				count++;
				System.out.println(p_ou_code + "循环1" + "(" + count + "/" + rows + ")");
				// 判断是否跳过
				long item_id = bd_material.getLong("id");
				long org_id = bd_material.getLong("aos_orgid");
				String orgid_str = Long.toString(org_id);
				String itemid_str = Long.toString(item_id);
				String aos_itemnumer = bd_material.getString("number");
				String aos_productno = bd_material.getString("aos_productno");
				Object org_id_o = Long.toString(org_id);
				int aos_shp_day = (int) aos_shpday_map.get(org_id_o);// 备货天数
				int aos_freight_day = (int) aos_clearday_map.get(org_id_o);// 海运天数
				int aos_clear_day = (int) aos_freightday_map.get(org_id_o);// 清关天数
				Boolean aos_is_saleout = bd_material.getBoolean("aos_is_saleout");

				if (eliminateItemSet.contains(String.valueOf(item_id))) {
					log.add(aos_itemnumer + "营销已剔除!");
					continue;
				}

				if (!OnlineSkuSet.contains(String.valueOf(item_id))) {
					log.add(aos_itemnumer + "平台上架信息不存在!");
					continue;
				}

				String BseStItem = null;
				if (BaseStItem.get(org_id + "~" + item_id) != null)
					BseStItem = BaseStItem.get(org_id + "~" + item_id) + "";
				// 获取AMAZON店铺货号 如果没有则跳过

				// 获取老货号
				Boolean aosIsRepliceOld = bd_material.getBoolean("aos_isrepliceold");
				String aosOldNumber = bd_material.getString("aos_oldnumber");
				Boolean aosOnShelfReplace = bd_material.getBoolean("aos_onshelfreplace");

				String aos_shopsku = "";
				if (aosIsRepliceOld && FndGlobal.IsNotNull(aosOldNumber) && aosOnShelfReplace)
					aos_shopsku = mkt_productinfo.get(p_ou_code + "~" + aosOldNumber);
				else
					aos_shopsku = mkt_productinfo.get(p_ou_code + "~" + aos_itemnumer);

				if (FndGlobal.IsNull(aos_shopsku)) {
					log.add(aos_itemnumer + "AMAZON店铺货号不存在");
					continue;
				}
				Date aos_firstindate = bd_material.getDate("aos_firstindate");
				String aos_seasonpro = bd_material.getString("aos_seasonseting");
				String aos_cn_name = bd_material.getString("aos_cn_name");
				String aos_season = bd_material.getString("aos_season");
				Object aos_seasonid = bd_material.get("aos_seasonid");
				if (aos_seasonpro == null)
					aos_seasonpro = "";
				String aos_itemstatus = bd_material.getString("aos_contryentrystatus");
				String aos_festivalseting = bd_material.getString("aos_festivalseting");
				if (aos_festivalseting == null)
					aos_festivalseting = "";
				if (aos_seasonid == null) {
					log.add(aos_itemnumer + "季节属性不存在");
					continue;
				}

				Object aos_age = item_maxage_map.getOrDefault(org_id + "~" + item_id, 0);
				Object item_overseaqty = item_overseaqty_map.getOrDefault(org_id + "~" + item_id, 0);
				if ("C".equals(aos_itemstatus) && (int) item_overseaqty == 0)
					continue;// 终止状态且无海外库存 跳过
				Object item_intransqty = item_intransqty_map.getOrDefault(org_id + "~" + item_id, 0);

				// 产品号不存在 跳过
				if (Cux_Common_Utl.IsNull(aos_productno)) {
					log.add(aos_itemnumer + "产品号不存在");
					continue;
				}

				int auto = 0;
				if (ProductNo_Map.get(aos_productno + "-AUTO") != null)
					auto = ProductNo_Map.get(aos_productno + "-AUTO");
				ProductNo_Map.put(aos_productno + "-AUTO", auto);

				// =====是否新系列新组判断=====
				Map<String, Object> PPCInfo_Map = PPCInfo.get(org_id + "~" + item_id);
				Map<String, Object> PPCInfoSerial_Map = PPCInfoSerial.get(org_id + "~" + aos_productno + "-AUTO");
				Object aos_makedate = null;
				Object aos_groupdate = null;
				if (PPCInfo_Map != null)
					aos_groupdate = PPCInfo_Map.get("aos_groupdate");
				if (PPCInfoSerial_Map != null)
					aos_makedate = PPCInfoSerial_Map.get("aos_makedate");
				if (aos_makedate == null)
					aos_makedate = Today;// 新系列
				if (aos_groupdate == null)
					aos_groupdate = Today;// 新组
				// =====结束是否新系列新组判断=====
				String itemCategoryName = CommData.getItemCategoryName(String.valueOf(item_id));
				String aos_category1 = "";
				String aos_category2 = "";
				String aos_category3 = "";
				if (!"".equals(itemCategoryName) && null != itemCategoryName) {
					String[] split = itemCategoryName.split(",");
					if (split.length == 3) {
						aos_category1 = split[0];
						aos_category2 = split[1];
						aos_category3 = split[2];
					}
				}

				// 获取销售组别并赋值
				// 2.获取产品小类
				String itemCategoryId = CommData.getItemCategoryId(item_id + "");
				if (itemCategoryId == null || "".equals(itemCategoryId)) {
					log.add(aos_itemnumer + "产品类别不存在自动剔除");
					continue;
				}
				// 3.根据小类获取组别
				String salOrg = CommData.getSalOrgV2(p_org_id + "", itemCategoryId); // 小类获取组别
				if (salOrg == null || "".equals(salOrg)) {
					log.add(aos_itemnumer + "组别不存在自动剔除");
					continue;
				}

				Map<String, Object> SkuRptMap14 = SkuRpt14.get(org_id + "~" + item_id);// Sku报告

				BigDecimal Roi14Days = BigDecimal.ZERO;// 14天ROI
				BigDecimal Spend14Days = BigDecimal.ZERO;// 14天花费
				BigDecimal LastSpend = BigDecimal.ZERO;// 昨日花费
				BigDecimal aos_clicks14Days = BigDecimal.ZERO;// 14天点击
				int aos_online = 0;

				if (SkuRptMap14 != null
						&& ((BigDecimal) SkuRptMap14.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
					Spend14Days = (BigDecimal) SkuRptMap14.get("aos_spend");
					Roi14Days = ((BigDecimal) SkuRptMap14.get("aos_total_sales"))
							.divide((BigDecimal) SkuRptMap14.get("aos_spend"), 2, BigDecimal.ROUND_HALF_UP);
					aos_clicks14Days = (BigDecimal) SkuRptMap14.get("aos_clicks");
					aos_online = (int) SkuRptMap14.get("aos_online");
				}

				Map<String, Object> SkuRptDetailMap = SkuRptDetail.get(p_org_id + "~" + item_id);// Sku报告1日数据
				if (!Cux_Common_Utl.IsNull(SkuRptDetailMap)) {
					LastSpend = (BigDecimal) SkuRptDetailMap.get("aos_spend");
				}

				// =====开始定价毛利剔除判断=====
				Object FixValue = DailyPrice.getOrDefault(org_id + "~" + item_id, BigDecimal.ZERO);
				BigDecimal inventoryCost = CostMap.getOrDefault(org_id + "~" + item_id, BigDecimal.ZERO);
				BigDecimal expressFee = ShipFee.getOrDefault(org_id + "~" + item_id, BigDecimal.ZERO);
				BigDecimal Profitrate = SalUtil.calProfitrate((BigDecimal) FixValue, aos_vat_amount, aos_am_platform,
						inventoryCost, expressFee);

				log.add(aos_itemnumer + " FixValue =" + FixValue + "\n" + " inventoryCost =" + inventoryCost + "\n"
						+ " expressFee =" + expressFee + "\n" + " aos_vat_amount =" + aos_vat_amount + "\n"
						+ " aos_am_platform =" + aos_am_platform + "\n");

				log.add(aos_itemnumer + "(" + FixValue + " / (1 + " + aos_vat_amount + ") - " + inventoryCost + " - "
						+ FixValue + " / (1 +  " + aos_vat_amount + ") * " + aos_am_platform + " - " + expressFee
						+ " / (1 + " + aos_vat_amount + "+)) / (" + FixValue + " / (1 + " + aos_vat_amount + "+))");

				// 上次出价
				Object aos_lastbid = BigDecimal.ZERO;
				Map<String, Object> PpcYester_Map = PpcYester.get(p_org_id + "~" + item_id);// 默认昨日数据
				if (PpcYester_Map != null) {
					aos_lastbid = PpcYester_Map.get("aos_bid");// 昨日出价
				}

				// 7日ROI
				Map<String, Object> SkuRptMap = SkuRpt.get(org_id + "~" + item_id);// Sku报告7日
				BigDecimal Roi7Days = BigDecimal.ZERO;// 7天ROI
				BigDecimal Spend7Days = BigDecimal.ZERO;// 7天ROI
				if (SkuRptMap != null && ((BigDecimal) SkuRptMap.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
					Spend7Days = (BigDecimal) SkuRptMap.get("aos_spend");
					Roi7Days = ((BigDecimal) SkuRptMap.get("aos_total_sales")).divide(Spend7Days, 2,
							BigDecimal.ROUND_HALF_UP);
				}

				Map<String, Object> DayMap = new HashMap<>();
				int availableDays = InStockAvailableDays.calInstockSalDaysForDayMin(orgid_str, itemid_str, DayMap);// 可售天数

				String saleAdd = "false";
				if (map_itemToAdd.containsKey(aos_itemnumer))
					saleAdd = map_itemToAdd.get(aos_itemnumer);

				Object aos_activity = Act.get(p_org_id + "~" + item_id);
				Boolean actFlag = false;
				if (aos_activity != null && aos_activity.toString().contains("7DD")) {
					actFlag = true;// 7DD活动不参与剔除
				}

				// 初始化插入参数
				Map<String, Object> insert_map = new HashMap<>();
				insert_map.put("item_id", item_id);
				insert_map.put("aos_productno", aos_productno);
				insert_map.put("aos_itemnumer", aos_itemnumer);
				insert_map.put("date", Today);
				insert_map.put("aos_shopsku", aos_shopsku);
				insert_map.put("aos_seasonseting", aos_season);
				insert_map.put("aos_contryentrystatus", aos_itemstatus);
				insert_map.put("aos_cn_name", aos_cn_name);
				insert_map.put("aos_makedate", aos_makedate);
				insert_map.put("aos_groupdate", aos_groupdate);
				insert_map.put("aos_bid", LOWESTBID);// 最低出价
				insert_map.put("aos_basestitem", BseStItem);
				insert_map.put("aos_category1", aos_category1);
				insert_map.put("aos_category2", aos_category2);
				insert_map.put("aos_category3", aos_category3);
				insert_map.put("aos_sal_group", salOrg);
				insert_map.put("aos_roi14days", Roi14Days);
				insert_map.put("aos_lastspend", LastSpend);
				insert_map.put("aos_profit", Profitrate);
				insert_map.put("aos_age", aos_age);
				insert_map.put("aos_lastbid", aos_lastbid);
				insert_map.put("aos_roi7days", Roi7Days);
				insert_map.put("aos_avadays", availableDays);
				insert_map.put("aos_overseaqty", item_overseaqty);
				insert_map.put("aos_online", onlineDate.get(aos_itemnumer));
				insert_map.put("aos_is_saleout", aos_is_saleout);

				insert_map.put("aos_special", saleAdd);
				insert_map.put("aos_firstindate", aos_firstindate);

				// 剔除海外库存
				if ((int) item_overseaqty <= QtyStandard.intValue() && !aos_itemstatus.equals("E")
						&& !aos_itemstatus.equals("A") && (int) item_intransqty == 0) {
					log.add(aos_itemnumer + "海外库存剔除 国别库存标准 =" + item_overseaqty + "<=" + QtyStandard);
					log.add(aos_itemnumer + "海外库存剔除 在途 =" + item_intransqty);
					InsertData(aos_entryentityS, insert_map, "LOWQTY", roiMap);
					continue;
				}
				// 低库存剔除
				if ((int) item_overseaqty < SafeQty) {
					log.add(aos_itemnumer + "低库存剔除 海外库存小于安全库存 =" + item_overseaqty + "<" + SafeQty);
					InsertData(aos_entryentityS, insert_map, "LOWQTY", roiMap);
					continue;
				}
				// 固定sku剔除
				if (list_itemRejectIds.contains(String.valueOf(item_id))) {
					log.add(aos_itemnumer + "  固定剔除物料");
					InsertData(aos_entryentityS, insert_map, "ROI", roiMap);
					continue;
				}
				// 判断是否是必推货号
				Boolean mustFlag = true;
				if (mustSet.contains(itemid_str))
					mustFlag = false;
				if (mustFlag) {
					if ((int) item_overseaqty < 10
							&& ("其它节日装饰".equals(aos_category2) || "圣诞装饰".equals(aos_category2))) {
						log.add(aos_itemnumer + "节日品 低库存小于10剔除  =" + item_overseaqty);
						InsertData(aos_entryentityS, insert_map, "LOWQTY", roiMap);
						continue;
					}
					// 其它节日装饰 中类 万圣 过季品剔除
					if ("其它节日装饰".equals(aos_category2)) {
						log.add(aos_itemnumer + "其他节日装饰 过季品剔除");
						InsertData(aos_entryentityS, insert_map, "SEASON", roiMap);
						continue;
					}

					if ("圣诞装饰".equals(aos_category2)) {
						log.add(aos_itemnumer + "圣诞装饰 过季品剔除");
						InsertData(aos_entryentityS, insert_map, "SEASON", roiMap);
						continue;
					}

					// 非节日品剔除预断货 节日品节日2天内剔除 圣诞装饰 这两个中类不做预断货剔除 销量为3日均与7日均的最小值
					if ((aos_festivalseting.equals("") || aos_festivalseting.equals("null"))
							&& !"圣诞装饰".equals(aos_category2)) {

						// 剔除过季品
						if ((aos_seasonpro != null)
								&& (aos_seasonpro.equals("SPRING") || aos_seasonpro.equals("SPRING_SUMMER")
										|| aos_seasonpro.equals("SUMMER"))
								&& (Today.before(SummerSpringStart) || Today.after(SummerSpringEnd))) {
							log.add(aos_itemnumer + "过季品剔除");
							InsertData(aos_entryentityS, insert_map, "SEASON", roiMap);
							continue;
						} else {
							if (Deseaon.contains(aos_category1 + "~" + aos_category2 + "~" + aos_category3)) {
								log.add(aos_itemnumer + "不按季节属性推广品类");
								InsertData(aos_entryentityS, insert_map, "DESEASON", roiMap);
								continue;
							}
						}

						if ((aos_seasonpro != null)
								&& (aos_seasonpro.equals("AUTUMN_WINTER") || aos_seasonpro.equals("WINTER"))
								&& (Today.before(AutumnWinterStart) || Today.after(AutumnWinterEnd))) {
							InsertData(aos_entryentityS, insert_map, "SEASON", roiMap);
							log.add(aos_itemnumer + "过季品剔除");
							continue;
						}

						log.add(aos_itemnumer + "非节日品预断货参数数据" + "item_intransqty =" + item_intransqty
								+ "item_overseaqty =" + item_overseaqty + " 可售库存天数 =" + availableDays + " 3日销量 ="
								+ DayMap.get("for3day") + " 7日销量 =" + DayMap.get("for7day"));

						// 季节品 季节品 季末达标 CONTINUE; 剔除
						boolean isSeasonEnd = false;
						if ("SPRING".equals(aos_seasonpro) || "SPRING_SUMMER".equals(aos_seasonpro)
								|| "SUMMER".equals(aos_seasonpro)) {
							if (Today.after(summerSpringSeasonEnd)) {
								// 季末 判断是否达标
								isSeasonEnd = true;
								float SeasonRate = MKTCom.Get_SeasonRate(org_id, item_id, aos_seasonpro,
										item_overseaqty, month);
								if (!MKTCom.Is_SeasonRate(aos_seasonpro, month, SeasonRate)) {
									InsertData(aos_entryentityS, insert_map, "OFFSALE", roiMap);
									continue;
								}
							}
						}
						if ("AUTUMN_WINTER".equals(aos_seasonpro) || "WINTER".equals(aos_seasonpro)) {
							// 判断是否季末
							if (Today.after(autumnWinterSeasonEnd)) {
								isSeasonEnd = true;
								// 季末 判断是否达标
								float SeasonRate = MKTCom.Get_SeasonRate(org_id, item_id, aos_seasonpro,
										item_overseaqty, month);
								if (!MKTCom.Is_SeasonRate(aos_seasonpro, month, SeasonRate)) {
									InsertData(aos_entryentityS, insert_map, "OFFSALE", roiMap);
									continue;
								}
							}
						}
						// (海外在库+在途数量)/7日均销量)<30 或 满足销售预断货逻辑 则为营销预断货逻辑 且 不能为季节品季末 且可售天数小于45
						int itemavadays = map_itemavadays.getOrDefault(p_ou_code + "~" + aos_itemnumer, 0);
						if (((availableDays < 30) || ((MKTCom.Is_PreSaleOut(org_id, item_id, (int) item_intransqty,
								aos_shp_day, aos_freight_day, aos_clear_day, availableDays)) && itemavadays < 45))
								&& !isSeasonEnd) {
							InsertData(aos_entryentityS, insert_map, "OFFSALE", roiMap);
							continue;
						}
					} else {
						if ((aos_festivalseting.equals("HALLOWEEN") || "其它节日装饰".equals(aos_category2))
								&& (!(Today.after(HalloweenStart) && Today.before(HalloweenEnd)))) {
							InsertData(aos_entryentityS, insert_map, "SEASON", roiMap);
							log.add(aos_itemnumer + "节日品剔除");
							continue;
						}
						if ((aos_festivalseting.equals("CHRISTMAS") || "圣诞装饰".equals(aos_category2))
								&& (!(Today.after(ChristmasStart) && Today.before(ChristmasEnd)))) {
							InsertData(aos_entryentityS, insert_map, "SEASON", roiMap);
							log.add(aos_itemnumer + "节日品剔除");
							continue;
						}
					}

					// 周24持续剔除
					if ((week == Calendar.THURSDAY || week == Calendar.TUESDAY) && roiItemSet.contains(aos_itemnumer)) {
						InsertData(aos_entryentityS, insert_map, "ROI", roiMap);
						log.add(aos_itemnumer + "差ROI自动剔除");
						continue;
					}
					// 周24持续剔除
					if ((week == Calendar.THURSDAY || week == Calendar.TUESDAY) && proItemSet.contains(aos_itemnumer)) {
						InsertData(aos_entryentityS, insert_map, "PRO", roiMap);
						log.add(aos_itemnumer + "定价低毛利自动剔除");
						continue;
					}

					// =====各类标记=====
					// 新品
					Boolean NewFlag = false;
					if (aos_firstindate == null || ((aos_itemstatus.equals("E") || aos_itemstatus.equals("A"))
							&& (aos_firstindate != null) && MKTCom.GetBetweenDays(Today, aos_firstindate) <= 14))
						NewFlag = true;
					// 春夏品
					Boolean SpringSummerFlag = false;
					if ((aos_seasonpro != null)
							&& (aos_seasonpro.equals("SPRING") || aos_seasonpro.equals("SPRING_SUMMER")
									|| aos_seasonpro.equals("SUMMER")
									|| aos_seasonpro.equals("SPRING-SUMMER-CONVENTIONAL"))
							&& (Today.after(SummerSpringUnStart) && Today.before(SummerSpringUnEnd)))
						SpringSummerFlag = true;
					// 秋冬品
					Boolean AutumnWinterFlag = false;
					if ((aos_seasonpro != null)
							&& (aos_seasonpro.equals("AUTUMN_WINTER") || aos_seasonpro.equals("WINTER"))
							&& (Today.after(AutumnWinterUnStart) && Today.before(AutumnWinterUnEnd)))
						AutumnWinterFlag = true;
					// 14日在线

					Boolean Online14Days = false;
					if (aos_online >= 10 && MKTCom.GetBetweenDays(Today, (Date) aos_groupdate) > 14)
						Online14Days = true;
					// 7日在线
					Boolean Online7Days = false;
					if (aos_online >= 5 && MKTCom.GetBetweenDays(Today, (Date) aos_groupdate) > 7)
						Online7Days = true;
					// =====开始ROI剔除判断=====
					Boolean ROIFlag = false;
					String RoiType = null;
					// a.14 日在线 14天ROI＜4；且7天<8(不包括新品、季节品)
					if (Online14Days && Roi14Days.compareTo(BigDecimal.valueOf(4)) < 0
							&& Roi7Days.compareTo(BigDecimal.valueOf(8)) < 0 && !NewFlag && !SpringSummerFlag
							&& !AutumnWinterFlag) {
						ROIFlag = true;
						RoiType = "a";
					}
					// b.7日在线 秋冬品7日ROI<3； 所有秋冬
					if (Online7Days && AutumnWinterFlag && Roi7Days.compareTo(BigDecimal.valueOf(3)) < 0) {
						ROIFlag = true;
						RoiType = "b";
					}
					// c.在线7天，不到14天，点击＞国别标准(US300,其它150)，ROI＜4(不包括季节品)
					if (Online7Days && !Online14Days && Roi14Days.compareTo(BigDecimal.valueOf(4)) < 0
							&& aos_clicks14Days.compareTo(CLICK) > 0 && !SpringSummerFlag && !AutumnWinterFlag) {
						ROIFlag = true;
						RoiType = "c";
					}
					// d.历史剔除 持续剔除 项鑫报告中不存在才处理
					if (roiItemSet.contains(aos_itemnumer) && !rptSet.contains(aos_itemnumer)) {
						ROIFlag = true;
						RoiType = "d";
					}
					// d1.春夏滞销差ROI剔除
					if (baseItemSet.contains(aos_itemnumer) && Roi14Days.compareTo(BigDecimal.valueOf(2.5)) < 0
							&& Spend14Days.compareTo(BigDecimal.valueOf(10)) > 0) {
						ROIFlag = true;
						RoiType = "d1";
					}
					// ==以下为ROI加回 ==//
					// d2.爆品加回
					if (FndGlobal.IsNotNull(aos_is_saleout) && aos_is_saleout) {
						ROIFlag = false;
						RoiType = "d2";
					}
					// e.秋冬品历史剔除 7月ROI> 8 加回
					if (AutumnWinterFlag && roiItemSet.contains(aos_itemnumer)
							&& Roi7Days.compareTo(BigDecimal.valueOf(8)) > 0) {
						ROIFlag = false;
						RoiType = "e";
					}
					// f.过去14日ROI大于7 则加回
					if (Roi14Days.compareTo(BigDecimal.valueOf(7)) > 0 && roiItemSet.contains(aos_itemnumer)) {
						ROIFlag = false;
						RoiType = "f";
					}
					// g.最近一次入库(七天内)前30天全断货不剔除
					if (map_itemToOutage.containsKey(itemid_str)) {
						ROIFlag = false;
						RoiType = "g";
					}
					// h.新品14天内不剔除；
					if (NewFlag) {
						ROIFlag = false;
						RoiType = "h";
					}
					// i.圣诞装饰默认差ROI不剔除
					if ("圣诞装饰".equals(aos_category2)) {
						ROIFlag = false;
						RoiType = "i";
					}
					// j.7DD不剔除
					if (actFlag) {
						ROIFlag = false;
						RoiType = "ji";
					}
					// k.
					if ("true".equals(saleAdd)) {
						ROIFlag = false;
						RoiType = "k";
					}

					// 周2周4不剔除
					if (week == Calendar.TUESDAY && week == Calendar.THURSDAY) {
						ROIFlag = false;
					}

					if (ROIFlag) {
						InsertData(aos_entryentityS, insert_map, "ROI", roiMap);
						log.add(aos_itemnumer + " " + RoiType + " 差ROI自动剔除");
						continue;
					}

					// 低定价毛利剔除
					Boolean ProFlag = false;
					// a.历史中未剔除 且定价毛利＜8%；；
					if (!proItemSet.contains(aos_itemnumer) && Profitrate.compareTo(BigDecimal.valueOf(0.08)) < 0) {
						ProFlag = true;
					}
					// b.历史剔除 持续剔除 项鑫报告中不存在才处理
					if (proItemSet.contains(aos_itemnumer) && !rptSet.contains(aos_itemnumer)) {
						ProFlag = true;
					}
					// c.历史中剔除 前14天ROI>7 或等于0 且定价毛利>=12% 加回
					if (proItemSet.contains(aos_itemnumer)
							&& (Roi14Days.compareTo(BigDecimal.valueOf(7)) > 0
									|| Roi7Days.compareTo(BigDecimal.ZERO) == 0)
							&& Profitrate.compareTo(BigDecimal.valueOf(0.12)) >= 0) {
						ProFlag = false;
					}
					// e.秋冬品不剔除；
					if (AutumnWinterFlag)
						ProFlag = false;
					// f.新品不剔除
					if (NewFlag)
						ProFlag = false;
					// g.节日品不剔除
					if ("圣诞装饰".equals(aos_category2)) {
						ProFlag = false;
					}
					// h.7DD不剔除
					if (actFlag) {
						ProFlag = false;
					}

					// k.周135 正常剔除 销售加回不剔除
					if (week != Calendar.TUESDAY && week != Calendar.THURSDAY && "true".equals(saleAdd)) {
						ProFlag = false;
					}

					// k.周24剔除135部分
					if (week == Calendar.TUESDAY && week == Calendar.THURSDAY) {
						ProFlag = false;
					}

					// 2023.08.07 不做低毛利剔除
					ProFlag = false;

					// 特殊广告不进低毛利剔除
					if (ProFlag && !"true".equals(saleAdd)) {
						InsertData(aos_entryentityS, insert_map, "PRO", roiMap);
						log.add(aos_itemnumer + "定价低毛利自动剔除");
						continue;
					}
				} else {
					// 必推货号 判断预断货
					if ((aos_festivalseting.equals("") || aos_festivalseting.equals("null"))
							&& !"圣诞装饰".equals(aos_category2)) {
						// (海外在库+在途数量)/7日均销量)<30 或 满足销售预断货逻辑 则为营销预断货逻辑 且 不能为季节品季末 且可售天数小于45
						int itemavadays = map_itemavadays.getOrDefault(p_ou_code + "~" + aos_itemnumer, 0);
						if (((availableDays < 30) || ((MKTCom.Is_PreSaleOut(org_id, item_id, (int) item_intransqty,
								aos_shp_day, aos_freight_day, aos_clear_day, availableDays)) && itemavadays < 45))) {
							InsertData(aos_entryentityS, insert_map, "OFFSALE", roiMap);
							continue;
						}
					}
				}

				// 赋值数据
				DynamicObject aos_entryentity = aos_entryentityS.addNew();
				aos_entryentity.set("aos_itemid", item_id);
				aos_entryentity.set("aos_productno", aos_productno + "-AUTO");
				aos_entryentity.set("aos_itemnumer", aos_itemnumer);
				aos_entryentity.set("aos_groupstatus", "AVAILABLE");
				aos_entryentity.set("aos_serialstatus", "UNAVAILABLE");
				aos_entryentity.set("aos_shopsku", aos_shopsku);
				aos_entryentity.set("aos_seasonseting", aos_season);
				aos_entryentity.set("aos_contryentrystatus", aos_itemstatus);
				aos_entryentity.set("aos_cn_name", aos_cn_name);
				aos_entryentity.set("aos_sal_group", salOrg);
				aos_entryentity.set("aos_makedate", aos_makedate);
				aos_entryentity.set("aos_groupdate", aos_groupdate);
				aos_entryentity.set("aos_basestitem", BseStItem);
				aos_entryentity.set("aos_category1", aos_category1);
				aos_entryentity.set("aos_category2", aos_category2);
				aos_entryentity.set("aos_category3", aos_category3);
				aos_entryentity.set("aos_profit", Profitrate);
				aos_entryentity.set("aos_roi14days", Roi14Days);
				aos_entryentity.set("aos_lastspend", LastSpend);
				aos_entryentity.set("aos_age", aos_age);
				aos_entryentity.set("aos_avadays", availableDays);
				aos_entryentity.set("aos_special", saleAdd);
				aos_entryentity.set("aos_overseaqty", item_overseaqty);
				aos_entryentity.set("aos_online", onlineDate.get(aos_itemnumer));
				aos_entryentity.set("aos_is_saleout", aos_is_saleout);
				aos_entryentity.set("aos_firstindate", aos_firstindate);

				if (FndGlobal.IsNotNull(onlineDate.get(aos_itemnumer))
						&& MKTCom.GetBetweenDays(Today, (Date) onlineDate.get(aos_itemnumer)) < 14)
					aos_entryentity.set("aos_offline", "Y");
				else
					aos_entryentity.set("aos_offline", "N");

				if (aos_activity != null)
					aos_entryentity.set("aos_activity", aos_activity);

				ProductNo_Map.put(aos_productno + "-AUTO", auto + 1);
				roiMap.put(aos_productno + "-AUTO", "N");
			}

			int size = aos_entryentityS.size();
			count = 0;
			// 新品
			Map<String, BigDecimal> NewSerial_Map = new HashMap<>();
			for (DynamicObject aos_entryentity : aos_entryentityS) {
				count++;
				System.out.println(p_ou_code + "循环2" + "(" + count + "/" + size + ")");
				// 新系列预算计算 = MAX(广告系列中SKU预测营收*AM营收占比*AM付费营收占比/国别标准ROI，2*广告系列中SKU个数)
				long org_id = (long) p_org_id;
				long item_id = aos_entryentity.getLong("aos_itemid");
				Map<String, Object> PPCInfo_Map = PPCInfo.get(org_id + "~" + item_id);
				if (PPCInfo_Map != null)
					continue; // 非新品 直接跳过 只需要合计新品
				String aos_productno = aos_entryentity.getString("aos_productno");
				Map<String, Object> Summary = SalSummary.get(org_id + "~" + item_id);
				if (Summary != null) {
					BigDecimal aos_sales = ((BigDecimal) SalSummary.get(org_id + "~" + item_id).get("aos_sales"))
							.divide(BigDecimal.valueOf(30), 2, BigDecimal.ROUND_HALF_UP);// 广告系列中SKU预测营收
					int aos_sche_qty = ProductNo_Map.get(aos_productno);// 广告系列中可用SKU个数
					BigDecimal budget1 = aos_sales.multiply(PopOrgAMSaleRate).multiply(PopOrgAMAffRate)
							.divide(PopOrgRoist, 2, BigDecimal.ROUND_HALF_UP);
					BigDecimal budget2 = BigDecimal.valueOf(aos_sche_qty * 2);
					BigDecimal NewBudget = max(budget1, budget2);
					if (NewSerial_Map.get(aos_productno + "-AUTO") != null)
						NewSerial_Map.put(aos_productno + "-AUTO",
								NewBudget.add(NewSerial_Map.get(aos_productno + "-AUTO")));
					else
						NewSerial_Map.put(aos_productno, NewBudget);
				}
			}

			count = 0;
			// 循环计算 预算
			for (DynamicObject aos_entryentity : aos_entryentityS) {
				count++;
				System.out.println(p_ou_code + "循环3" + "(" + count + "/" + size + ")");
				String aos_groupstatus = aos_entryentity.getString("aos_groupstatus");
				if (!aos_groupstatus.equals("AVAILABLE"))
					continue;// 剔除不计算出价与预算
				long org_id = (long) p_org_id;
				long item_id = aos_entryentity.getLong("aos_itemid");
				String aos_productno = aos_entryentity.getString("aos_productno");
				String aos_itemnumer = aos_entryentity.getString("aos_itemnumer");
				// =====是否新系列新组判断=====
				Boolean IsNewGroupFlag = false;

				Map<String, Object> PPCInfo_Map = PPCInfo.get(org_id + "~" + item_id);
				Map<String, Object> PPCInfoSerial_Map = PPCInfoSerial.get(org_id + "~" + aos_productno);
				Object aos_makedate = null;
				Object aos_groupdate = null;
				if (PPCInfo_Map != null)
					aos_groupdate = PPCInfo_Map.get("aos_groupdate");
				if (PPCInfoSerial_Map != null)
					aos_makedate = PPCInfoSerial_Map.get("aos_makedate");
				if (aos_makedate == null)
					aos_makedate = Today;// 新系列
				if (aos_groupdate == null) {
					aos_groupdate = Today;// 新组
					IsNewGroupFlag = true;
				}

				/*
				 * Map<String, Object> PPCInfo_Map = PPCInfo.get(org_id + "~" + item_id); Object
				 * aos_makedate = null; Object aos_groupdate = null; if (PPCInfo_Map != null) {
				 * aos_makedate = PPCInfo_Map.get("aos_makedate"); aos_groupdate =
				 * PPCInfo_Map.get("aos_groupdate"); } if (aos_makedate == null) { // 新系列
				 * aos_makedate = Today; } if (aos_groupdate == null) { // 新组 aos_groupdate =
				 * Today; }
				 */
				// =====结束是否新系列新组判断=====

				// =====Start 出价&预算 参数=====
				Map<String, Object> SkuRptMap = SkuRpt.get(org_id + "~" + item_id);// Sku报告7日
				Map<String, Object> SkuRptMap3 = SkuRpt3.get(org_id + "~" + item_id);// Sku报告3日
				Map<String, Object> PpcYester_Map = PpcYester.get(p_org_id + "~" + item_id);// 默认昨日数据
				Map<String, Object> PpcYesterSerial_Map = PpcYesterSerial.get(p_org_id + "~" + aos_productno);

				BigDecimal Roi7Days = BigDecimal.ZERO;// 7天ROI
				if (SkuRptMap != null && ((BigDecimal) SkuRptMap.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0)
					Roi7Days = ((BigDecimal) SkuRptMap.get("aos_total_sales"))
							.divide((BigDecimal) SkuRptMap.get("aos_spend"), 2, BigDecimal.ROUND_HALF_UP);
				BigDecimal Roi3Days = BigDecimal.ZERO;// 3天ROI
				if (SkuRptMap3 != null && ((BigDecimal) SkuRptMap3.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0)
					Roi3Days = ((BigDecimal) SkuRptMap3.get("aos_total_sales"))
							.divide((BigDecimal) SkuRptMap3.get("aos_spend"), 2, BigDecimal.ROUND_HALF_UP);

				BigDecimal CostRate = BigDecimal.ZERO; // 花出率 =报告中花费/预算
				// 系列花出率 = 前一天花出/预算
				Object LastSpendObj = SkuRptDetailSerial.get(p_org_id + "~" + aos_productno);// Sku报告1日数据
				BigDecimal LastSpend = BigDecimal.ZERO;
				if (!Cux_Common_Utl.IsNull(LastSpendObj)) {
					LastSpend = (BigDecimal) LastSpendObj;
				}
				if (PpcYesterSerial_Map != null) {
					BigDecimal LastBudget = (BigDecimal) PpcYesterSerial_Map.get("aos_budget");// 昨日预算
					if (LastBudget.compareTo(BigDecimal.ZERO) != 0)
						CostRate = LastSpend.divide(LastBudget, 2, BigDecimal.ROUND_HALF_UP);
				}
				if (get_between_days(Today, (Date) aos_makedate) <= 3)
					// 全新系列3天内花出率最低为1
					CostRate = max(CostRate, BigDecimal.valueOf(1));

				/*
				 * BigDecimal ExpouseRate = BigDecimal.ZERO; // 曝光 if (SkuRptDetailMap != null)
				 * ExpouseRate = (BigDecimal) SkuRptDetailMap.get("aos_impressions");
				 */

				// =====计算出价=====
				BigDecimal aos_bid = BigDecimal.ZERO;// 出价
				Object aos_lastpricedate = Today;
				Object aos_lastbid = BigDecimal.ZERO;
				if (PpcYester_Map != null) {
					aos_lastpricedate = PpcYester_Map.get("aos_lastpricedate");// 最近出价调整日期 初始化默认为昨日日期 后面再重新赋值
					aos_entryentity.set("aos_lastpricedate", aos_lastpricedate);
					aos_lastbid = PpcYester_Map.get("aos_bid");// 昨日出价
					if (aos_lastpricedate == null)
						aos_lastpricedate = Today;
				}

				Map<String, Object> SkuRpt3AvgMap = SkuRpt3Avg.get(p_org_id + "~" + item_id);
				BigDecimal Impress3Avg = BigDecimal.ZERO;
				if (SkuRpt3AvgMap != null)
					Impress3Avg = (BigDecimal) SkuRpt3AvgMap.get("aos_impressions");

				BigDecimal HighValue = BigDecimal.ZERO;// 最高出价 = MIN(市场价，最高标准)
				BigDecimal AdviceValue = BigDecimal.ZERO;// 建议价 报告中获取
				BigDecimal MaxValue = BigDecimal.ZERO;// 市场最高价 报告中获取
				BigDecimal MarketValue = BigDecimal.ZERO;// 市场价 公式计算
				// =====计算市场价=====
				Map<String, Object> AdPriceMap = AdPrice.get(org_id + "~" + aos_itemnumer);// 组
				if (AdPriceMap != null) {
					AdviceValue = (BigDecimal) AdPriceMap.get("aos_bid_suggest");
					MaxValue = (BigDecimal) AdPriceMap.get("aos_bid_rangeend");
				}

				// 如果建议价为空 或为0 取国别cpc
				if (AdviceValue == null || AdviceValue.compareTo(BigDecimal.ZERO) == 0) {
					String aos_category1 = aos_entryentity.getString("aos_category1");
					String aos_category2 = aos_entryentity.getString("aos_category2");
					String aos_category3 = aos_entryentity.getString("aos_category3");
					AdviceValue = orgCpcMap.getOrDefault(aos_category1 + aos_category2 + aos_category3,
							BigDecimal.ZERO);
				}
				Object FixValue = DailyPrice.get(org_id + "~" + item_id); // 定价 Amazon每日价格
				BigDecimal HighStandard = GetHighStandard(FixValue, HIGH1, HIGH2, HIGH3);// 最高标准 根据国别定价筛选
				HighValue = BigDecimal.ZERO;// 最高出价 = MIN(市场价，最高标准)

				int AvaDays = aos_entryentity.getInt("aos_avadays");
				// 根据可售库存天数计算市场价
				if (AvaDays < 90) {
					// 可售库存天数 < 90 市场价= min(建议价*0.7,最高标准)
					BigDecimal Value1 = AdviceValue.multiply(BigDecimal.valueOf(0.7));
					BigDecimal Value2 = HighStandard;
					MarketValue = min(Value1, Value2);
				} else if (AvaDays >= 90 && AvaDays <= 120) {
					// 可售库存天数 90-120 市场价=建议价
					MarketValue = AdviceValue;
				} else if (AvaDays > 120 && AvaDays <= 180) {
					// 可售库存天数120-180 市场价=max(市场最高价*0.7,建议价)
					BigDecimal Value1 = MaxValue.multiply(BigDecimal.valueOf(0.7));
					BigDecimal Value2 = AdviceValue;
					MarketValue = max(Value1, Value2);
				} else if (AvaDays > 180) {
					// 可售库存天数>180 市场价=市场最高价
					MarketValue = MaxValue;
				}
				// =====End计算市场价=====

				if (IsNewGroupFlag) {
					// 1.首次出价 新组
					aos_bid = PopOrgFirstBid;
					aos_lastpricedate = Today;// 判断为首次时需要调整出价日期
					aos_entryentity.set("aos_lastpricedate", aos_lastpricedate);
					log.add(aos_itemnumer + "出价1 =" + aos_bid);
				} else if (PpcYester_Map != null
						&& !PpcYester_Map.get("aos_groupstatus").toString().equals("AVAILABLE")) {
					// 1.1.首次出价/2 前一次为空 或 前一次不可用
					aos_bid = PopOrgFirstBid.divide(BigDecimal.valueOf(2), 2, BigDecimal.ROUND_HALF_UP);
					aos_lastpricedate = Today;// 判断为首次时需要调整出价日期
					aos_entryentity.set("aos_lastpricedate", aos_lastpricedate);
					log.add(aos_itemnumer + "出价1.1 =" + aos_bid);
				} else if ((get_between_days(Today, (Date) aos_lastpricedate) >= 3)
						&& (get_between_days(Today, (Date) aos_groupdate) > 7)) {
					// 3.出价3天内不调整 今天与最近出价日期做判断 且 不为 首次建组日后7天内
					BigDecimal LastBid = (BigDecimal) aos_lastbid;// 上次出价 昨日出价缓存中获取
					Map<String, Object> GetAdjustRateMap = new HashMap<>();
					GetAdjustRateMap.put("Roi3Days", Roi3Days);
					GetAdjustRateMap.put("Roi7Days", Roi7Days);
					GetAdjustRateMap.put("ExpouseRate", Impress3Avg);
					GetAdjustRateMap.put("AvaDays", AvaDays);
					GetAdjustRateMap.put("CostRate", CostRate);
					GetAdjustRateMap.put("PopOrgRoist", PopOrgRoist);
					GetAdjustRateMap.put("WORST", WORST);// 很差
					GetAdjustRateMap.put("WORRY", WORRY);// 差
					GetAdjustRateMap.put("STANDARD", STANDARD);// 标准
					GetAdjustRateMap.put("WELL", WELL);// 好
					GetAdjustRateMap.put("EXCELLENT", EXCELLENT);// 优
					GetAdjustRateMap.put("EXPOSURE", EXPOSURE);// 国别曝光标准
					Map<String, Object> AdjustRateMap = GetAdjustRate(GetAdjustRateMap, aos_mkt_bsadjrateS, log,
							aos_itemnumer);
					BigDecimal AdjustRate = (BigDecimal) AdjustRateMap.get("AdjustRate");// 调整幅度 出价调整幅度参数表中获取
					int aos_level = (int) AdjustRateMap.get("aos_level");

					log.add(aos_itemnumer + "调整幅度 =" + AdjustRate);
					log.add(aos_itemnumer + "优先级 =" + aos_level);
					log.add(aos_itemnumer + "上次出价 =" + LastBid);
					// 计算出价逻辑 本次出价=上次出价*(1+调整幅度) 如果优先级为1则本次出价=MAX(/*建议价*0.7*/国别首次出价均价,上次出价*(1+调整幅度))
					if (aos_level != 1)
						aos_bid = LastBid.multiply(AdjustRate.add(BigDecimal.valueOf(1)));
					else
						aos_bid = max(PopOrgFirstBid.multiply(BigDecimal.valueOf(0.5)),
								LastBid.multiply(AdjustRate.add(BigDecimal.valueOf(1.15))));
					log.add(aos_itemnumer + "出价4 =" + aos_bid);
				} else {
					// 4.其他情况下不调整出价与出价日期 为昨日信息
					aos_bid = (BigDecimal) aos_lastbid;// 本次出价=上次出价
					log.add(aos_itemnumer + "出价5 =" + aos_bid);
				}
				// =====End计算出价=====
				log.add(aos_itemnumer + "花出率 =" + CostRate);
				log.add(aos_itemnumer + "曝光 =" + Impress3Avg);
				log.add(aos_itemnumer + "7日ROI =" + Roi7Days);
				log.add(aos_itemnumer + "计算出价与最高价取最小值 " + aos_bid + "&" + HighValue);

				// 2.1 计算最高出价
				if (AvaDays < 120) {
					HighValue = min(max(AdviceValue, aos_bid), HighStandard);
				} else {
					HighValue = min(max(MaxValue, aos_bid), HighStandard);
				}
				aos_bid = min(aos_bid, HighValue);// 计算出价不能超过最高出价 第一次计算出价

				// 新品第二次出价 移动至最高出价计算完后判断
				if (get_between_days(Today, (Date) aos_groupdate) <= 3) {
					// 2.今天为第二次出价
					// 2.2 根据市场价计算出价 = min(市场价*1.1，最高出价*0.5)
					BigDecimal Value1 = MarketValue.multiply(BigDecimal.valueOf(1.1));
					BigDecimal Value2 = HighValue.multiply(BigDecimal.valueOf(0.5));
					aos_bid = min(Value1, Value2);
					aos_lastpricedate = Today;// 需要调整出价日期
					aos_entryentity.set("aos_lastpricedate", aos_lastpricedate);
					log.add(aos_itemnumer + "出价3 =" + aos_bid);
				}
				aos_bid = min(aos_bid, HighValue);// 计算出价不能超过最高出价

				// 如果此时出价为0则默认为初始价
				if (aos_bid.compareTo(BigDecimal.ZERO) == 0)
					aos_bid = PopOrgFirstBid.multiply(BigDecimal.valueOf(0.5));
				// 出价保留两位
				aos_bid = aos_bid.setScale(2, BigDecimal.ROUND_HALF_UP);
				aos_entryentity.set("aos_lastbid", aos_lastbid);
				aos_entryentity.set("aos_bid", aos_bid);

				try {
					if (aos_bid.compareTo((BigDecimal) aos_lastbid) != 0)
						aos_entryentity.set("aos_lastpricedate", new Date());
				} catch (Exception ex) {
				}

				aos_entryentity.set("aos_bid_ori", aos_bid);
				aos_entryentity.set("aos_roi7days", Roi7Days);// 7日ROI
				aos_entryentity.set("aos_marketprice", MarketValue);// 市场价
				aos_entryentity.set("aos_highvalue", HighValue);// 最高价
				aos_entryentity.set("aos_fixvalue", FixValue);// 定价
				if (ProductNoBid_Map.get(aos_productno) != null)
					aos_bid = aos_bid.add(ProductNoBid_Map.get(aos_productno));
				ProductNoBid_Map.put(aos_productno, aos_bid);
			}

			// =====计算 系列预算=====
			for (DynamicObject aos_entryentity : aos_entryentityS) {
				String aos_groupstatus = aos_entryentity.getString("aos_groupstatus");
				if (!aos_groupstatus.equals("AVAILABLE"))
					continue;// 剔除不计算出价与预算
				BigDecimal aos_budget = BigDecimal.ZERO;// 预算 分新系列
				long org_id = (long) p_org_id;
				long item_id = aos_entryentity.getLong("aos_itemid");
				String aos_productno = aos_entryentity.getString("aos_productno");
				String aos_itemnumer = aos_entryentity.getString("aos_itemnumer");
				// =====是否新系列新组判断=====
				Boolean IsNewSerialFlag = false;
				Map<String, Object> PPCInfo_Map = PPCInfo.get(org_id + "~" + item_id);
				Map<String, Object> PPCInfoSerial_Map = PPCInfoSerial.get(org_id + "~" + aos_productno);
				Object aos_makedate = null;
				Object aos_groupdate = null;
				if (PPCInfo_Map != null)
					aos_groupdate = PPCInfo_Map.get("aos_groupdate");
				if (PPCInfoSerial_Map != null)
					aos_makedate = PPCInfoSerial_Map.get("aos_makedate");
				if (aos_makedate == null) {
					aos_makedate = Today;// 新系列
					IsNewSerialFlag = true;
				}
				if (aos_groupdate == null)
					aos_groupdate = Today;// 新组

				BigDecimal Roi7Days_Serial = BigDecimal.ZERO;// 7天ROI 系列维度
				Map<String, Object> PpcYesterSerial_Map = PpcYesterSerial.get(p_org_id + "~" + aos_productno);
				Map<String, Object> SkuRptMap_Serial = SkuRpt_Serial.get(org_id + "~" + aos_productno);// Sku报告
				if (SkuRptMap_Serial != null
						&& ((BigDecimal) SkuRptMap_Serial.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0)
					Roi7Days_Serial = ((BigDecimal) SkuRptMap_Serial.get("aos_total_sales"))
							.divide((BigDecimal) SkuRptMap_Serial.get("aos_spend"), 2, BigDecimal.ROUND_HALF_UP);
				BigDecimal RoiLast1Days = BigDecimal.ZERO; // 昨天ROI
				BigDecimal RoiLast2Days = BigDecimal.ZERO; // 前天ROI
				BigDecimal RoiLast3Days = BigDecimal.ZERO; // 大前天ROI
				Map<String, Map<String, Object>> SkuRpt3SerialMap = SkuRpt3Serial.get(p_org_id + "~" + aos_productno);// Sku报告
				if (SkuRpt3SerialMap != null) {
					Map<String, Object> RoiLast1DaysMap = SkuRpt3SerialMap.get(Yester);
					Map<String, Object> RoiLast2DaysMap = SkuRpt3SerialMap.get(Yester2);
					Map<String, Object> RoiLast3DaysMap = SkuRpt3SerialMap.get(Yester3);
					if (RoiLast1DaysMap != null
							&& ((BigDecimal) RoiLast1DaysMap.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0)
						RoiLast1Days = ((BigDecimal) RoiLast1DaysMap.get("aos_total_sales"))
								.divide((BigDecimal) RoiLast1DaysMap.get("aos_spend"), 2, BigDecimal.ROUND_HALF_UP);
					if (RoiLast2DaysMap != null
							&& ((BigDecimal) RoiLast2DaysMap.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0)
						RoiLast2Days = ((BigDecimal) RoiLast2DaysMap.get("aos_total_sales"))
								.divide((BigDecimal) RoiLast2DaysMap.get("aos_spend"), 2, BigDecimal.ROUND_HALF_UP);
					if (RoiLast3DaysMap != null
							&& ((BigDecimal) RoiLast3DaysMap.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0)
						RoiLast3Days = ((BigDecimal) RoiLast3DaysMap.get("aos_total_sales"))
								.divide((BigDecimal) RoiLast3DaysMap.get("aos_spend"), 2, BigDecimal.ROUND_HALF_UP);
				}
				BigDecimal CostRate = BigDecimal.ZERO; // 花出率 =报告中花费/预算

				// 系列花出率 = 前一天花出/预算
				Object LastSpendObj = SkuRptDetailSerial.get(p_org_id + "~" + aos_productno);// Sku报告1日数据
				BigDecimal LastSpend = BigDecimal.ZERO;
				if (!Cux_Common_Utl.IsNull(LastSpendObj)) {
					LastSpend = (BigDecimal) LastSpendObj;
				}
				if (PpcYesterSerial_Map != null) {
					BigDecimal LastBudget = (BigDecimal) PpcYesterSerial_Map.get("aos_budget");// 昨日预算
					log.add(aos_itemnumer + "预算3 昨日花出 =" + LastSpend);
					log.add(aos_itemnumer + "预算3 昨日预算 =" + LastBudget);
					if (LastBudget.compareTo(BigDecimal.ZERO) != 0)
						CostRate = LastSpend.divide(LastBudget, 2, BigDecimal.ROUND_HALF_UP);
				}
				if (get_between_days(Today, (Date) aos_makedate) <= 3)
					// 全新系列3天内花出率最低为1
					CostRate = max(CostRate, BigDecimal.valueOf(1));
				if (IsNewSerialFlag) {
					// a.新系列 预算=新系列预算
					BigDecimal NewBudget = BigDecimal.ZERO;// 新系列预算
					// 新系列预算计算 = MAX(广告系列中SKU预测营收*AM营收占比*AM付费营收占比/国别标准ROI，2*广告系列中SKU个数)
					Map<String, Object> Summary = SalSummaryMap.get(org_id + "~" + aos_productno);//
					if (Summary != null) {
						BigDecimal aos_sales = ((BigDecimal) Summary.get("aos_sales")).divide(BigDecimal.valueOf(30), 2,
								BigDecimal.ROUND_HALF_UP);// 广告系列中SKU预测营收
						int aos_sche_qty = ProductNo_Map.get(aos_productno);// 广告系列中可用SKU个数
						BigDecimal budget1 = aos_sales.multiply(PopOrgAMSaleRate).multiply(PopOrgAMAffRate)
								.divide(PopOrgRoist, 2, BigDecimal.ROUND_HALF_UP);
						BigDecimal budget2 = BigDecimal.valueOf(aos_sche_qty * 2);
						NewBudget = max(budget1, budget2);
						log.add(aos_itemnumer + "新系列budget1 =" + budget1);
						log.add(aos_itemnumer + "新系列budget2 =" + budget2);
						log.add(aos_itemnumer + "新系列NewBudget =" + NewBudget);
						log.add(aos_itemnumer + "新系列aos_sales =" + aos_sales);
						log.add(aos_itemnumer + "新系列PopOrgAMSaleRate =" + PopOrgAMSaleRate);
						log.add(aos_itemnumer + "新系列PopOrgAMAffRate =" + PopOrgAMAffRate);
						log.add(aos_itemnumer + "新系列PopOrgRoist =" + PopOrgRoist);
						log.add(aos_itemnumer + "新系列aos_sche_qty =" + aos_sche_qty);
					}
					aos_budget = NewBudget;
					log.add(aos_itemnumer + "预算1 =" + aos_budget);
				} else if ((Roi7Days_Serial.compareTo(PopOrgRoist) < 0)
						&& (((RoiLast1Days.compareTo(PopOrgRoist) >= 0) && (RoiLast2Days.compareTo(PopOrgRoist) >= 0))
								|| ((RoiLast1Days.compareTo(PopOrgRoist) >= 0)
										&& (RoiLast3Days.compareTo(PopOrgRoist) >= 0))
								|| ((RoiLast2Days.compareTo(PopOrgRoist) >= 0)
										&& (RoiLast3Days.compareTo(PopOrgRoist) >= 0)))) {
					// b.当7天ROI＜国别标准时，前3天ROI中任意2天ROI≥国别标准ROI，则预算维持上次设置金额
					BigDecimal Lastbudget = BigDecimal.ZERO;
					if (PpcYesterSerial_Map != null)
						Lastbudget = (BigDecimal) PpcYesterSerial_Map.get("aos_budget");// 上次设置金额
					aos_budget = Lastbudget;
					log.add(aos_itemnumer + "预算2 =" + aos_budget);
				} else {
					// c.计算预算
					BigDecimal Lastbudget = BigDecimal.ZERO;
					if (PpcYesterSerial_Map != null)
						Lastbudget = (BigDecimal) PpcYesterSerial_Map.get("aos_budget");// 昨日设置金额
					Map<String, Object> GetBudgetRateMap = new HashMap<>();
					String RoiType = GetRoiType(PopOrgRoist, WORST, WORRY, STANDARD, WELL, EXCELLENT, Roi7Days_Serial);
					GetBudgetRateMap.put("CostRate", CostRate);// 花出率
					GetBudgetRateMap.put("RoiType", RoiType);
					BigDecimal BudgetRate = GetBudgetRate(GetBudgetRateMap, aos_mkt_bsadjparaS);// 花出率获取对应预算调整系数
					BigDecimal NewSerial = BigDecimal.ZERO;
					BigDecimal aos_bid = BigDecimal.ZERO;
					if (ProductNoBid_Map.get(aos_productno) != null)
						aos_bid = ProductNoBid_Map.get(aos_productno);
					if (NewSerial_Map.get(aos_productno) != null)
						NewSerial = NewSerial_Map.get(aos_productno);
					BigDecimal budget1 = Lastbudget.multiply(BudgetRate).add(NewSerial);
					BigDecimal budget2 = aos_bid.multiply(BigDecimal.valueOf(20));
					aos_budget = max(budget1, budget2);

					log.add(aos_itemnumer + "预算3 =" + aos_budget);
					log.add(aos_itemnumer + "预算3 花出率 =" + CostRate);
					log.add(aos_itemnumer + "预算3 7日Roi =" + Roi7Days_Serial);
					log.add(aos_itemnumer + "预算3 上次预算 =" + Lastbudget);
					log.add(aos_itemnumer + "预算3 花出率对应系数 =" + BudgetRate);
					log.add(aos_itemnumer + "预算3 新系列 =" + NewSerial);
					log.add(aos_itemnumer + "预算3 aos_bid =" + aos_bid);
					log.add(aos_itemnumer + "预算3 budget1 =" + budget1);
					log.add(aos_itemnumer + "预算3 budget2 =" + budget2);

				}
				aos_entryentity.set("aos_budget", max(aos_budget, BigDecimal.valueOf(2)));
				aos_entryentity.set("aos_budget_ori", max(aos_budget, BigDecimal.valueOf(2)));
				// =====End计算预算=====
			}

			aos_mkt_bsadjrateS.close();// 关闭Dataset
			aos_mkt_bsadjparaS.close();
			// 保存正式表
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_popular_ppc",
					new DynamicObject[] { aos_mkt_popular_ppc }, OperateOption.create());
			if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
			}

			Object fid = operationrst.getSuccessPkIds().get(0);

			// 重新剔除后 对于置顶位置出价

			for (DynamicObject aos_entryentity : aos_entryentityS) {
				String aos_groupstatus = aos_entryentity.getString("aos_groupstatus");
				if (!aos_groupstatus.equals("AVAILABLE")) {
					aos_entryentity.set("aos_budget", 0);
					aos_entryentity.set("aos_bid", 0);
					aos_entryentity.set("aos_budget_ori", 0);
					aos_entryentity.set("aos_bid_ori", 0);
					continue;
				}
				long org_id = (long) p_org_id;
				long item_id = aos_entryentity.getLong("aos_itemid");
				String aos_productno = aos_entryentity.getString("aos_productno");
				// 出价逻辑结束后 对于置顶位置出价
				BigDecimal aos_topprice = BigDecimal.ZERO;
				Map<String, Object> SkuRptMap14 = SkuRpt14.get(org_id + "~" + item_id);// Sku报告
				BigDecimal Roi14Days = BigDecimal.ZERO;// 系列SKU平均店铺14天转化率
				if (SkuRptMap14 != null && ((BigDecimal) SkuRptMap14.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0)
					Roi14Days = ((BigDecimal) SkuRptMap14.get("aos_total_sales"))
							.divide((BigDecimal) SkuRptMap14.get("aos_spend"), 2, BigDecimal.ROUND_HALF_UP);
				int aos_sche_qty = ProductNo_Map.get(aos_productno);// 广告系列中可用SKU个数 用于计算平均
				BigDecimal SerialAvaFix = GetSerialAvaFix(aos_productno, fid).divide(BigDecimal.valueOf(aos_sche_qty),
						2, BigDecimal.ROUND_HALF_UP);

				log.add("置顶位置出价 " + "Roi14Days =" + Roi14Days);
				log.add("置顶位置出价 " + "RoiStandard =" + RoiStandard);
				log.add("置顶位置出价 " + "SerialAvaFix =" + SerialAvaFix);
				log.add("置顶位置出价 " + "StandardFix =" + StandardFix);
				log.add("置顶位置出价 " + "aos_sche_qty =" + aos_sche_qty);

				if (Roi14Days.compareTo(RoiStandard) > 0 && SerialAvaFix.compareTo(StandardFix) > 0)
					aos_topprice = BigDecimal.valueOf(0.2);

				// 7天订单转化率
				Map<String, Object> SkuRptMap = SkuRpt.get(org_id + "~" + item_id);// Sku报告
				BigDecimal Roi7Days = BigDecimal.ZERO;// 7天ROI
				if (SkuRptMap != null && ((BigDecimal) SkuRptMap.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0)
					Roi7Days = ((BigDecimal) SkuRptMap.get("aos_total_sales"))
							.divide((BigDecimal) SkuRptMap.get("aos_spend"), 2, BigDecimal.ROUND_HALF_UP);

				Map<String, Object> SkuRpt3AvgMap = SkuRpt3Avg.get(p_org_id + "~" + item_id);
				// 曝光3天平均
				BigDecimal Impress3Avg = BigDecimal.ZERO;
				if (SkuRpt3AvgMap != null)
					Impress3Avg = (BigDecimal) SkuRpt3AvgMap.get("aos_impressions");

				// 曝光点击率7天
				BigDecimal aos_exprate = BigDecimal.ZERO;
				BigDecimal aos_avgexp = BigDecimal.ZERO;
				BigDecimal aos_clicks = BigDecimal.ZERO;
				if (SkuRptMap != null) {
					aos_avgexp = (BigDecimal) SkuRptMap.get("aos_impressions");
					aos_clicks = (BigDecimal) SkuRptMap.get("aos_clicks");
					if (aos_avgexp.compareTo(BigDecimal.ZERO) != 0)
						aos_exprate = aos_clicks.divide(aos_avgexp, 6, BigDecimal.ROUND_HALF_UP);
				}

				// 定价
				BigDecimal aos_fixvalue = BigDecimal.ZERO;
				if (aos_entryentity.getBigDecimal("aos_fixvalue") != null) {
					aos_fixvalue = aos_entryentity.getBigDecimal("aos_fixvalue");
				}
				// 季节属性
				String aos_season = aos_entryentity.getString("aos_seasonseting");
				String seasonpro = "";
				// 判断是否为春夏品
				if ("夏季产品".equals(aos_season) || "春夏产品".equals(aos_season) || "春季产品".equals(aos_season)) {
					seasonpro = "春夏品";
				} else if ("常规产品".equals(aos_season) || "春夏常规品".equals(aos_season)) {
					seasonpro = "常规品";
				}

				// 春夏品，订单转化率>2%，且定价>国别均价；top of search加50%; //如果和另外两条逻辑重复，取大值加；
				if ("春夏品".equals(seasonpro) && Roi7Days.compareTo(BigDecimal.valueOf(0.02)) > 0
						&& aos_fixvalue.compareTo(StandardFix) > 0) {
					if (aos_topprice.compareTo(BigDecimal.valueOf(0.5)) < 0) {
						aos_topprice = BigDecimal.valueOf(0.5);
					}
				}

				// 曝光>标准*3, CTR<0.2%；top of search常规品+20%；春夏+50%；
				if (Impress3Avg.compareTo(EXPOSURE.multiply(BigDecimal.valueOf(3))) > 0
						&& aos_exprate.compareTo(BigDecimal.valueOf(0.002)) > 0) {
					if ("常规品".equals(seasonpro)) {
						if (aos_topprice.compareTo(BigDecimal.valueOf(0.2)) < 0) {
							aos_topprice = BigDecimal.valueOf(0.2);
						}
					} else if ("春夏品".equals(seasonpro)) {
						if (aos_topprice.compareTo(BigDecimal.valueOf(0.5)) < 0) {
							aos_topprice = BigDecimal.valueOf(0.5);
						}
					}
				}
				aos_entryentity.set("aos_topprice", aos_topprice);
				log.add("置顶位置出价 " + "aos_topprice =" + aos_topprice);
			}

			Map<String, BigDecimal> availableBudgetMap = new HashMap<>();
			Map<String, BigDecimal> preDayExposureMap = getPreDayExposure(p_org_id.toString());
			for (DynamicObject aos_entryentity : aos_entryentityS) {
				// 获取前一日曝光
				String aos_itemnumer = aos_entryentity.getString("aos_itemnumer");
				BigDecimal preDayExposure = preDayExposureMap.getOrDefault(aos_itemnumer, BigDecimal.ZERO);
				aos_entryentity.set("aos_predayexposure", preDayExposure);
				// 系列
				String aos_productno = aos_entryentity.getString("aos_productno");
				// 组状态
				String aos_groupstatus = aos_entryentity.getString("aos_groupstatus");
				BigDecimal aos_budget = aos_entryentity.getBigDecimal("aos_budget");
				if ("AVAILABLE".equals(aos_groupstatus)) {
					availableBudgetMap.put(aos_productno, aos_budget);
				}
			}

			// 删除日期参数表
			DeleteServiceHelper.delete("aos_mkt_sp_date", new QFilter("aos_orgid", "=", p_org_id).toArray());
			DynamicObject aos_mkt_sp_date = BusinessDataServiceHelper.newDynamicObject("aos_mkt_sp_date");
			aos_mkt_sp_date.set("aos_orgid", p_org_id);
			aos_mkt_sp_date.set("aos_date", Today);
			DynamicObjectCollection aos_mkt_sp_dateS = aos_mkt_sp_date.getDynamicObjectCollection("aos_entryentity");
			// 循环设置 最终系列状态 与不可用系列预算
			for (DynamicObject aos_entryentity : aos_entryentityS) {
				String aos_productno = aos_entryentity.getString("aos_productno");
				String aos_itemnumer = aos_entryentity.getString("aos_itemnumer");
				DynamicObject spDateLine = aos_mkt_sp_dateS.addNew();
				if (availableBudgetMap.containsKey(aos_productno)) {
					aos_entryentity.set("aos_serialstatus", "AVAILABLE");
					aos_entryentity.set("aos_budget", availableBudgetMap.get(aos_productno));
					aos_entryentity.set("aos_budget_ori", availableBudgetMap.get(aos_productno));
					if (!lastAvaSet.contains(aos_itemnumer)) {
						spDateLine.set("aos_itemstr", aos_itemnumer);
						spDateLine.set("aos_online", Today);
					} else {
						spDateLine.set("aos_itemstr", aos_itemnumer);
						spDateLine.set("aos_online", aos_entryentity.get("aos_online"));
					}
				} else {
					aos_entryentity.set("aos_budget", 1);
					aos_entryentity.set("aos_budget_ori", 1);
					spDateLine.set("aos_itemstr", aos_itemnumer);
					spDateLine.set("aos_online", aos_entryentity.get("aos_online"));
				}
			}
			// 保存日期参数表
			OperationServiceHelper.executeOperate("save", "aos_mkt_sp_date", new DynamicObject[] { aos_mkt_sp_date },
					OperateOption.create());

			// 保存正式表
			operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_popular_ppc",
					new DynamicObject[] { aos_mkt_popular_ppc }, OperateOption.create());
			if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
			}

			// 保存日志表
			log.finnalSave();

			// 开始生成 出价调整(销售)
			Map<String, Object> adjs = new HashMap<>();
			adjs.put("p_ou_code", p_ou_code);
			aos_mkt_popadjs_init.executerun(adjs);
		} catch (Exception e) {
			logger.error(p_ou_code + ":PPC推广SP初始化失败!", e);
		}
	}

	private static HashMap<String, String> generateProductInfo() {
		HashMap<String, String> ProductInfo = new HashMap<>();
		QFilter filter_ama = new QFilter("aos_platformfid.number", "=", "AMAZON");
		QFilter filter_mainshop = new QFilter("aos_shopfid.aos_is_mainshop", "=", true);
		QFilter[] filters = new QFilter[] { filter_ama, filter_mainshop };
		String SelectColumn = "aos_orgid.number aos_orgid," + "aos_item_code.number aos_itemid,"
				+ "aos_shopsku aos_productid";
		DynamicObjectCollection bd_materialS = QueryServiceHelper.query("aos_sync_invprice", SelectColumn, filters);
		for (DynamicObject bd_material : bd_materialS) {
			ProductInfo.put(bd_material.getString("aos_orgid") + "~" + bd_material.getString("aos_itemid"),
					bd_material.getString("aos_productid"));
		}
		return ProductInfo;
	}

	/** 平台上架信息 **/
	private static Set<String> GenerateOnlineSkuSet(Object p_org_id) {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_sync_inv_ctl", "aos_item",
				new QFilter("aos_ou", QCP.equals, p_org_id)
						.and("createtime", QCP.less_than, FndDate.add_days(new Date(), -2)).toArray());
		return list.stream().map(obj -> obj.getString("aos_item")).collect(Collectors.toSet());
	}

	/** 不按季节属性推广品类 **/
	private static Set<String> GenerateDeseaon() {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_deseason",
				"aos_category1,aos_category2,aos_category3", new QFilter[] {});
		return list.stream().map(obj -> obj.getString("aos_category1") + "~" + obj.getString("aos_category2") + "~"
				+ obj.getString("aos_category3")).collect(Collectors.toSet());
	}

	// 营销必推货号
	private static Set<String> GenerateMustSet(Object p_org_id) {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_mktitem", "aos_itemid",
				new QFilter("aos_orgid", QCP.equals, p_org_id).toArray());
		return list.stream().map(obj -> obj.getString("aos_itemid")).collect(Collectors.toSet());
	}

	public static HashMap<String, BigDecimal> GenerateVat(Object p_org_id) {
		HashMap<String, BigDecimal> VatMap = new HashMap<>();
		QFilter filter_org = new QFilter("aos_org.id", "=", p_org_id);
		QFilter[] filters = new QFilter[] { filter_org };
		DynamicObject aos_sal_org_cal_p = QueryServiceHelper.queryOne("aos_sal_org_cal_p",
				"aos_org.number aos_orgnum,aos_vat_amount,aos_am_platform", filters);
		VatMap.put("aos_vat_amount", aos_sal_org_cal_p.getBigDecimal("aos_vat_amount"));
		VatMap.put("aos_am_platform", aos_sal_org_cal_p.getBigDecimal("aos_am_platform"));
		return VatMap;
	}

	private static BigDecimal GetSerialAvaFix(String aos_productno, Object fid) {
		DataSet Productno = null;
		BigDecimal SerialAvaFix = BigDecimal.ZERO;
		try {
			String algoKey = "aos_mkt_popular_ppc.IsExists." + aos_productno;
			String sql = " select sum(r.fk_aos_fixvalue) from tk_aos_mkt_popular_ppc_r r" + " where 1=1 "
					+ " and r.fk_aos_productno = ? " + " and r.fk_aos_groupstatus IN ('AVAILABLE','SAL_ADD') "
					+ " and r.fid = ? ";
			Object[] params = { aos_productno, fid };
			Productno = DB.queryDataSet(algoKey, DBRoute.of(DB_MKT), sql, params);
			while (Productno.hasNext()) {
				Row row = Productno.next();
				SerialAvaFix = row.getBigDecimal(0);
			}
		} catch (Exception Ex) {
			Ex.printStackTrace();
		} finally {
			if (Productno != null) {
				Productno.close();
			}
		}
		return SerialAvaFix;
	}

	private static Map<String, Object> GetAdjustRate(Map<String, Object> GetAdjustRateMap, DataSet aos_mkt_bsadjrateS,
			FndLog aos_sync_log, String aos_itemnumer) {
		Map<String, Object> AdjustRateMap = new HashMap<>();
		BigDecimal AdjustRate = BigDecimal.ZERO;
		Object Roi3Days = GetAdjustRateMap.get("Roi3Days");
		Object Roi7Days = GetAdjustRateMap.get("Roi7Days");
		Object ExpouseRate = GetAdjustRateMap.get("ExpouseRate");
		Object AvaDays = GetAdjustRateMap.get("AvaDays");
		Object CostRate = GetAdjustRateMap.get("CostRate");
		Object PopOrgRoist = GetAdjustRateMap.get("PopOrgRoist");// Roi标准
		Object WORST = GetAdjustRateMap.get("WORST");
		Object WORRY = GetAdjustRateMap.get("WORRY");
		Object STANDARD = GetAdjustRateMap.get("STANDARD");
		Object WELL = GetAdjustRateMap.get("WELL");
		Object EXCELLENT = GetAdjustRateMap.get("EXCELLENT");
		Object EXPOSURE = GetAdjustRateMap.get("EXPOSURE");
		String[] OrderBy = { "aos_level" };
		DataSet mkt_bsadjrateS = aos_mkt_bsadjrateS.copy().orderBy(OrderBy);
		String rule = "";
		int aos_level = 0;
		while (mkt_bsadjrateS.hasNext()) {
			Row mkt_bsadjrate = mkt_bsadjrateS.next();
			rule = "";
			String rule1 = "";
			String rule2 = "";
			String rule5 = "";
			String aos_roitype = mkt_bsadjrate.getString("aos_roitype");

			if (aos_roitype != null && !aos_roitype.equals("") && !aos_roitype.equals("null")) {
				BigDecimal Value = BigDecimal.ZERO;
				switch (aos_roitype) {
				case "WORST":
					Value = (BigDecimal) WORST;
					break;
				case "WORRY":
					Value = (BigDecimal) WORRY;
					break;
				case "STANDARD":
					Value = (BigDecimal) STANDARD;
					break;
				case "WELL":
					Value = (BigDecimal) WELL;
					break;
				case "EXCELLENT":
					Value = (BigDecimal) EXCELLENT;
					break;
				}
				BigDecimal RoiValue = Value.add((BigDecimal) PopOrgRoist);// ROI类型 要转化为对应的值
				rule1 = Roi7Days + mkt_bsadjrate.getString("aos_roi") + RoiValue;
			}

			String aos_exposure = mkt_bsadjrate.getString("aos_exposure");
			if (aos_exposure != null && !aos_exposure.equals("") && !aos_exposure.equals("null"))
				if (rule1 != null && !rule1.equals(""))
					rule2 = "&&" + ExpouseRate + aos_exposure
							+ mkt_bsadjrate.getBigDecimal("aos_exposurerate").multiply((BigDecimal) EXPOSURE);
				else
					rule2 = ExpouseRate + aos_exposure
							+ mkt_bsadjrate.getBigDecimal("aos_exposurerate").multiply((BigDecimal) EXPOSURE);

			/*
			 * String aos_avadays = mkt_bsadjrate.getString("aos_avadays"); if (aos_avadays
			 * != null && !aos_avadays.equals("") && !aos_avadays.equals("null")) rule3 =
			 * "&&" + AvaDays + aos_avadays +
			 * mkt_bsadjrate.getBigDecimal("aos_avadaysvalue");
			 * 
			 * String aos_costrate = mkt_bsadjrate.getString("aos_costrate"); if
			 * (aos_costrate != null && !aos_costrate.equals("") &&
			 * !aos_costrate.equals("null")) rule4 = "&&" + CostRate + aos_costrate +
			 * mkt_bsadjrate.getBigDecimal("aos_costratevalue");
			 */

			String aos_roitype3 = mkt_bsadjrate.getString("aos_roitype3");
			if (aos_roitype3 != null && !aos_roitype3.equals("") && !aos_roitype3.equals("null")) {
				BigDecimal Value = BigDecimal.ZERO;
				switch (aos_roitype3) {
				case "WORST":
					Value = (BigDecimal) WORST;
					break;
				case "WORRY":
					Value = (BigDecimal) WORRY;
					break;
				case "STANDARD":
					Value = (BigDecimal) STANDARD;
					break;
				case "WELL":
					Value = (BigDecimal) WELL;
					break;
				case "EXCELLENT":
					Value = (BigDecimal) EXCELLENT;
					break;
				}
				BigDecimal RoiValue = Value.add((BigDecimal) PopOrgRoist);// ROI类型 要转化为对应的值
				rule5 = "&&" + Roi3Days + mkt_bsadjrate.getString("aos_roi3") + RoiValue;
			}

			rule = rule1 + rule2 + rule5;
			boolean condition = getResult(rule);

			aos_sync_log.add(aos_itemnumer + "rule =" + rule);
			aos_sync_log.add(aos_itemnumer + "rule5 =" + rule5);
			aos_sync_log.add(aos_itemnumer + "condition =" + condition);

			if (condition) {
				AdjustRate = mkt_bsadjrate.getBigDecimal("aos_rate");
				aos_level = mkt_bsadjrate.getInteger("aos_level");
				break;
			}
		}
		mkt_bsadjrateS.close();
		AdjustRateMap.put("AdjustRate", AdjustRate);
		AdjustRateMap.put("rule", rule);
		AdjustRateMap.put("aos_level", aos_level);
		return AdjustRateMap;
	}

	private static boolean getResult(String rule) {
		boolean result = false;
		try {
			ScriptEngineManager scriptEngineManager = new ScriptEngineManager();
			ScriptEngine scriptEngine = scriptEngineManager.getEngineByName("nashorn");
			result = Boolean.parseBoolean(String.valueOf(scriptEngine.eval(rule)));
		} catch (Exception e) {
			e.getMessage();
		}
		return result;
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

	private static BigDecimal GetBudgetRate(Map<String, Object> GetBudgetRateMap, DataSet aos_mkt_bsadjparaS) {
		BigDecimal BudgetRate = BigDecimal.ZERO;
		Object RoiType = GetBudgetRateMap.get("RoiType");
		BigDecimal CostRate = (BigDecimal) GetBudgetRateMap.get("CostRate");
		DataSet mkt_bsadjparaS = aos_mkt_bsadjparaS.copy();
		while (mkt_bsadjparaS.hasNext()) {
			Row mkt_bsadjpara = mkt_bsadjparaS.next();
			String aos_roi = mkt_bsadjpara.getString("aos_roi");
			BigDecimal aos_ratefrom = mkt_bsadjpara.getBigDecimal("aos_ratefrom");
			BigDecimal aos_rateto = mkt_bsadjpara.getBigDecimal("aos_rateto");
			if (!aos_roi.equals(RoiType.toString()))
				continue;
			boolean condition = false;
			if (CostRate.compareTo(aos_ratefrom) >= 0 && CostRate.compareTo(aos_rateto) <= 0)
				condition = true;
			// String rule = CostRate + ">" + aos_ratefrom + "&&" + CostRate + "<=" +
			// aos_rateto;
			// condition = getResult(rule);
			if (condition) {
				BudgetRate = mkt_bsadjpara.getBigDecimal("aos_adjratio");
				break;
			}
		}
		mkt_bsadjparaS.close();
		return BudgetRate;
	}

	private static BigDecimal max(BigDecimal Value1, BigDecimal Value2) {
		if (Value1.compareTo(Value2) > -1)
			return Value1;
		else
			return Value2;
	}

	private static BigDecimal min(BigDecimal Value1, BigDecimal Value2) {
		if (Value1.compareTo(Value2) > -1)
			return Value2;
		else
			return Value1;
	}

	private static void InsertData(DynamicObjectCollection aos_entryentityS, Map<String, Object> insert_map,
			String Type, Map<String, String> roiMap) {
		DynamicObject aos_entryentity = aos_entryentityS.addNew();
		Object item_id = insert_map.get("item_id");
		Object aos_productno = insert_map.get("aos_productno");
		Object aos_itemnumer = insert_map.get("aos_itemnumer");
		Object date = insert_map.get("date");
		Object aos_shopsku = insert_map.get("aos_shopsku");
		Object aos_makedate = insert_map.get("aos_makedate");
		Object aos_groupdate = insert_map.get("aos_groupdate");
		Object aos_budget = insert_map.get("aos_budget");
		Object aos_bid = insert_map.get("aos_bid");
		Object aos_basestitem = insert_map.get("aos_basestitem");
		Object aos_sal_group = insert_map.get("aos_sal_group");
		Object aos_tmp = insert_map.get("aos_tmp");
		Object aos_profit = insert_map.get("aos_profit");
		Object aos_age = insert_map.get("aos_age");
		Object aos_roi7days = insert_map.get("aos_roi7days");
		Object aos_lastpricedate = insert_map.get("aos_lastpricedate");
		Object aos_cn_name = insert_map.get("aos_cn_name");
		Object aos_contryentrystatus = insert_map.get("aos_contryentrystatus");
		Object aos_seasonseting = insert_map.get("aos_seasonseting");
		Object aos_lastbid = insert_map.get("aos_lastbid");
		Object aos_category1 = insert_map.get("aos_category1");
		Object aos_category2 = insert_map.get("aos_category2");
		Object aos_category3 = insert_map.get("aos_category3");
		Object aos_roi14days = insert_map.get("aos_roi14days");
		Object aos_lastspend = insert_map.get("aos_lastspend");
		Object aos_avadays = insert_map.get("aos_avadays");
		Object aos_special = insert_map.get("aos_special");
		Object aos_overseaqty = insert_map.get("aos_overseaqty");
		Object aos_online = insert_map.get("aos_online");
		Object aos_is_saleout = insert_map.get("aos_is_saleout");
		Object aos_firstindate = insert_map.get("aos_firstindate");

		if (Type.equals("ROI") || Type.equals("PRO"))
			aos_special = false;

		aos_entryentity.set("aos_itemid", item_id);
		aos_entryentity.set("aos_productno", aos_productno + "-AUTO");
		aos_entryentity.set("aos_itemnumer", aos_itemnumer);
		aos_entryentity.set("aos_makedate", aos_makedate);
		aos_entryentity.set("aos_groupdate", aos_groupdate);
		aos_entryentity.set("aos_eliminatedate", date);
		aos_entryentity.set("aos_groupstatus", Type);
		aos_entryentity.set("aos_serialstatus", "UNAVAILABLE");
		aos_entryentity.set("aos_shopsku", aos_shopsku);
		aos_entryentity.set("aos_budget", aos_budget);
		aos_entryentity.set("aos_bid", aos_bid);
		aos_entryentity.set("aos_lastpricedate", aos_lastpricedate);
		aos_entryentity.set("aos_avadays", aos_avadays);
		aos_entryentity.set("aos_seasonseting", aos_seasonseting);
		aos_entryentity.set("aos_contryentrystatus", aos_contryentrystatus);
		aos_entryentity.set("aos_cn_name", aos_cn_name);
		aos_entryentity.set("aos_lastbid", aos_lastbid);
		aos_entryentity.set("aos_basestitem", aos_basestitem);
		aos_entryentity.set("aos_category1", aos_category1);
		aos_entryentity.set("aos_category2", aos_category2);
		aos_entryentity.set("aos_category3", aos_category3);
		aos_entryentity.set("aos_sal_group", aos_sal_group);
		aos_entryentity.set("aos_tmp", aos_tmp);
		aos_entryentity.set("aos_roi14days", aos_roi14days);
		aos_entryentity.set("aos_lastspend", aos_lastspend);
		aos_entryentity.set("aos_profit", aos_profit);
		aos_entryentity.set("aos_age", aos_age);
		aos_entryentity.set("aos_roi7days", aos_roi7days);
		aos_entryentity.set("aos_avadays", aos_avadays);
		aos_entryentity.set("aos_special", aos_special);
		aos_entryentity.set("aos_overseaqty", aos_overseaqty);
		aos_entryentity.set("aos_online", aos_online);
		aos_entryentity.set("aos_is_saleout", aos_is_saleout);
		aos_entryentity.set("aos_firstindate", aos_firstindate);

		if (FndGlobal.IsNotNull(aos_online) && MKTCom.GetBetweenDays(new Date(), (Date) aos_online) < 14)
			aos_entryentity.set("aos_offline", "Y");
		else
			aos_entryentity.set("aos_offline", "N");

		if ("ROI".equals(Type))
			aos_entryentity.set("aos_roiflag", true);

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

	/*
	 * public static Set<String> GenActAmazonItem(String aos_orgid) { String[]
	 * channelArr = { "AMAZON" }; Calendar calendar = Calendar.getInstance();
	 * calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0);
	 * calendar.set(Calendar.MILLISECOND, 0); calendar.set(Calendar.HOUR_OF_DAY, 0);
	 * Date date_from = calendar.getTime(); calendar.add(Calendar.DAY_OF_MONTH, 7);
	 * Date date_to = calendar.getTime(); SimpleDateFormat writeFormat = new
	 * SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化 String
	 * date_from_str = writeFormat.format(date_from); String date_to_str =
	 * writeFormat.format(date_to); QFilter filter_date1 = new
	 * QFilter("aos_sal_actplanentity.aos_l_startdate", ">=", date_from_str)
	 * .and("aos_sal_actplanentity.aos_l_startdate", "<=", date_to_str); QFilter
	 * filter_date2 = new QFilter("aos_sal_actplanentity.aos_enddate", ">=",
	 * date_from_str) .and("aos_sal_actplanentity.aos_enddate", "<=", date_to_str);
	 * QFilter filter_date3 = new QFilter("aos_sal_actplanentity.aos_l_startdate",
	 * "<=", date_from_str) .and("aos_sal_actplanentity.aos_enddate", ">=",
	 * date_to_str); QFilter filter_date =
	 * filter_date1.or(filter_date2).or(filter_date3);
	 * 
	 * DataSet dataSet = QueryServiceHelper.queryDataSet(
	 * "aos_mkt_popppc_init.queryApartFromAmzAndEbayItem", "aos_act_select_plan",
	 * "aos_sal_actplanentity.aos_itemnum.number aos_itennum, 1 as count", new
	 * QFilter[] { new QFilter("aos_nationality", QCP.equals, aos_orgid), new
	 * QFilter("aos_channel.number", QCP.in, channelArr), new
	 * QFilter("aos_actstatus", QCP.equals, "B"), // 已提报 new
	 * QFilter("aos_sal_actplanentity.aos_l_actstatus", QCP.equals, "A"), // 正常
	 * filter_date }, null); DataSet finish = dataSet.groupBy(new String[] {
	 * "aos_itennum" }).sum("count").finish(); dataSet.close(); Set<String> itemSet
	 * = new HashSet<>(); while (finish.hasNext()) { Row next = finish.next();
	 * String aos_itennum = next.getString("aos_itennum"); Integer count =
	 * next.getInteger("count"); if (count > 0) { itemSet.add(aos_itennum); } }
	 * return itemSet; }
	 */

	private static Map<String, BigDecimal> queryOrgCpc(String aos_orgid) {
		String selectFileds = "aos_category1," + "aos_category2," + "aos_category3," + "aos_bid";
		DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_orgcpc", selectFileds,
				new QFilter[] { new QFilter("aos_orgid", QCP.equals, aos_orgid) });
		return list.stream().collect(Collectors.toMap(
				obj -> obj.getString("aos_category1") + obj.getString("aos_category2") + obj.getString("aos_category3"),
				obj -> obj.getBigDecimal("aos_bid"), (k1, k2) -> k1));

	}

	public static Map<String, BigDecimal> GenerateShipFee() {
		Map<String, BigDecimal> map = new HashMap<>();
		DynamicObjectCollection dyns = QueryServiceHelper.query("aos_quo_skuquery",
				"aos_orgid.id aos_orgid,aos_lowest_fee,aos_itemid.id aos_itemid", null);
		for (DynamicObject d : dyns) {
			BigDecimal aos_lowest_fee = d.getBigDecimal("aos_lowest_fee");
			String aos_item_code = d.getString("aos_itemid");
			String aos_ou_code = d.getString("aos_orgid");
			map.put(aos_ou_code + "~" + aos_item_code, aos_lowest_fee);
		}
		return map;
	}

	public static Map<String, BigDecimal> initItemCost() {
		Map<String, BigDecimal> map = new HashMap<>();
		DynamicObjectCollection dyns = QueryServiceHelper.query("aos_sync_invcost",
				"aos_orgid.id aos_orgid," + "aos_itemid.id aos_itemid," + "aos_item_cost",
				new QFilter[] { new QFilter("aos_isnew", QCP.equals, true) });
		for (DynamicObject d : dyns) {
			BigDecimal aos_item_cost = d.getBigDecimal("aos_item_cost");
			String aos_orgid = d.getString("aos_orgid");
			String aos_itemid = d.getString("aos_itemid");
			map.put(aos_orgid + "~" + aos_itemid, aos_item_cost);
		}
		return map;
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
		System.out.println("====AM平台活动====");
		return Act;
	}

	// 获取前一天曝光
	private static Map<String, BigDecimal> getPreDayExposure(String aos_orgid) {
		Calendar instance = Calendar.getInstance();
		String aos_date = DateFormatUtils.format(instance.getTime(), "yyyy-MM-dd");
		instance.add(Calendar.DAY_OF_MONTH, -1);
		String aos_date1 = DateFormatUtils.format(instance.getTime(), "yyyy-MM-dd");
		QFilter qFilter = new QFilter("aos_orgid", QCP.equals, aos_orgid);
		qFilter.and(new QFilter("aos_date", QCP.like, aos_date + "%"));
		qFilter.and(new QFilter("aos_entryentity.aos_date_l", QCP.like, aos_date1 + "%"));
		String selectFields = "aos_entryentity.aos_ad_name aos_ad_name,"
				+ "aos_entryentity.aos_impressions aos_impressions";
		DynamicObjectCollection aos_base_skupoprpt = QueryServiceHelper.query("aos_base_skupoprpt", selectFields,
				qFilter.toArray());
		return aos_base_skupoprpt.stream().collect(Collectors.toMap(obj -> obj.getString("aos_ad_name"),
				obj -> obj.getBigDecimal("aos_impressions"), (k1, k2) -> k1));
	}

	// 营销剔除
	private static Set<String> getEliminateItem(String aos_orgid) {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_dataeliminate", "aos_itemid",
				new QFilter[] { new QFilter("aos_orgid", QCP.equals, aos_orgid) });
		return list.stream().map(obj -> obj.getString("aos_itemid")).collect(Collectors.toSet());
	}

	/**
	 * 获取物料事物处理表中 获取国别物料维度的最大入库日期
	 * 
	 * @param orgid         国别主键
	 * @param wareHouseDate 入库时间
	 * @return 物料的入库时间，同一入库时间下的所有物料
	 */
	private static Map<String, List<String>> Query7daysWareHouseItem(Object orgid, Date wareHouseDate) {
		SimpleDateFormat sim_day = new SimpleDateFormat("yyyy-MM-dd");
		LocalDate local_wareHouse = wareHouseDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
		local_wareHouse = local_wareHouse.minusDays(6);
		QFilter filter_org = new QFilter("aos_ou", "=", orgid);
		QFilter filter_date = new QFilter("aos_trans_date", ">=", local_wareHouse.toString());
		String algo = "mkt.popular.ppc.aos_mkt_popppc_init.Query7daysWareHouseItem";
		DataSet ds = QueryServiceHelper.queryDataSet(algo, "aos_sync_inv_trans", "aos_item,aos_trans_date",
				new QFilter[] { filter_org, filter_date }, null);
		ds = ds.groupBy(new String[] { "aos_item" }).max("aos_trans_date").finish();
		// 物料和其最新入库时间
		DynamicObjectCollection dyc_itemAndDate = ORM.create().toPlainDynamicObjectCollection(ds);
		ds.close();
		// 记录每个的入库时间
		// Map<String, String> map_itemToDate = dyc_itemAndDate.stream()
		// .filter(dy -> dy.getString("aos_item") != null)
		// .filter(dy -> dy.getDate("aos_trans_date") != null)
		// .collect(Collectors.toMap(
		// dy -> dy.getString("aos_item"),
		// dy -> sim_day.format(dy.getDate("aos_trans_date")),
		// (key1, key2) -> key1));
		// 根据入库时间，将物料进行分组
		Map<String, List<String>> map_dateToItem = dyc_itemAndDate.stream()
				.collect(Collectors.groupingBy(dy -> sim_day.format(dy.getDate("aos_trans_date")),
						Collectors.mapping(dy -> dy.getString("aos_item"), Collectors.toList())));
		return map_dateToItem;
	}

	/**
	 * 判断入库前30是否全断货
	 * 
	 * @param orgid            orgid
	 * @param wareHouseDate    入库时间
	 * @param list_item        物料id
	 * @param map_itemToOutage 记录物料的断货情况，存在不断货为true，全断货为false
	 */
	private static void Judge30DaysStock(Object orgid, String wareHouseDate, List<String> list_item,
			Map<String, Boolean> map_itemToOutage) {
		LocalDate local_warehouseDate = LocalDate.parse(wareHouseDate);
		// 判断入库的前30天，是否存在不断货
		QFilter filter_org = new QFilter("aos_entryentity.aos_orgid", "=", orgid);
		QFilter filter_to = new QFilter("aos_date", "<=", local_warehouseDate.toString());
		QFilter filter_from = new QFilter("aos_date", ">=", local_warehouseDate.minusDays(30).toString());
		QFilter filter_item = new QFilter("aos_entryentity.aos_itemid", QFilter.in, list_item);
		QFilter filter_noShortage = new QFilter("aos_entryentity.aos_flag", "!=", true); // 不缺货
		QFilter[] qfs = new QFilter[] { filter_org, filter_item, filter_to, filter_from, filter_noShortage };
		List<String> list_noShortage = QueryServiceHelper
				.query("aos_sync_offsale_bak", "aos_entryentity.aos_itemid aos_itemid", qfs).stream()
				.map(dy -> dy.getString("aos_itemid")).filter(itemId -> itemId != null).distinct()
				.collect(Collectors.toList());
		// 将存在不断货的信息修改为true
		for (String noShortageItem : list_noShortage) {
			map_itemToOutage.put(noShortageItem, true);
		}
	}

	/** 获取前7天的销售加回数据 **/
	private static Map<String, String> QuerySaleAdd(Object orgid) {
		QFilter filter_org = new QFilter("aos_orgid", "=", orgid);
		LocalDate now = LocalDate.now();
		QFilter qFilter_dateMin = new QFilter("aos_date", ">=", now.minusDays(1).toString());
		QFilter qFilter_dateMax = new QFilter("aos_date", "<", now.toString());
		QFilter[] qfs = new QFilter[] { filter_org, qFilter_dateMin, qFilter_dateMax };
		StringJoiner str = new StringJoiner(",");
		str.add("aos_entryentity.aos_itemnumer aos_itemnumer");
		str.add("aos_entryentity.aos_special aos_special");
		return QueryServiceHelper.query("aos_mkt_popular_ppc", str.toString(), qfs).stream()
				.collect(Collectors.toMap(dy -> dy.getString("aos_itemnumer"), dy -> dy.getString("aos_special")));
	}

	/** 获取固定剔除物料 **/
	private static List<String> getRejectItem(Object orgid) {
		QFBuilder builder = new QFBuilder();
		builder.add("aos_ou", "=", orgid);
		builder.add("aos_type", "=", "sp");
		builder.add("aos_item", "!=", "");
		return QueryServiceHelper.query("aos_mkt_reject", "aos_item", builder.toArray()).stream()
				.map(dy -> dy.getString("aos_item")).distinct().collect(Collectors.toList());
	}
}
