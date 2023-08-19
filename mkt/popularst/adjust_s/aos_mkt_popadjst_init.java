package mkt.popularst.adjust_s;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.time.DateUtils;

import common.Cux_Common_Utl;
import common.sal.util.InStockAvailableDays;
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
import kd.bos.threads.ThreadPool;
import kd.bos.threads.ThreadPools;
import mkt.common.AosMktGenerate;
import mkt.common.MKTCom;
import mkt.popularst.adjusts_p.aos_mkt_popadjpst_init;
import sal.sche.aos_sal_sche_pub.aos_sal_sche_pub;
import sal.synciface.imp.aos_sal_import_pub;

public class aos_mkt_popadjst_init extends AbstractTask {

	private static final DistributeSessionlessCache cache = CacheFactory.getCommonCacheFactory()
			.getDistributeSessionlessCache("mkt_redis");
	private static Log logger = LogFactory.getLog(aos_mkt_popadjst_init.class);
	public final static String SYSTEM = Cux_Common_Utl.SYSTEM; // ID-000000

	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		executerun(param);// 调度任务 触发多线程
	}

	public static void ManualitemClick(Map<String, Object> param) {
		executerun(param);// 手工点击 触发多线程
	}

	public static void executerun(Map<String, Object> param) {
		// 获取传入国别参数
		Object p_ou_code = param.get("p_ou_code");
		System.out.println("=====into 出价调整销售ST=====");
		System.out.println("p_ou_code =" + p_ou_code);
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
		DeleteServiceHelper.delete("aos_mkt_pop_adjst", filters_adj);// 删除出价调整销售
		DeleteServiceHelper.delete("aos_mkt_pop_adjstdt", filters_adj);// 删除出价调整销售数据表
		cache.put(p_ou_code + "_GroupSizeCountST", 0 + "", 3600);// 放置国别组缓存
		// 循环销售可用海外公司
		long is_oversea_flag = aos_sal_sche_pub.get_lookup_values("AOS_YES_NO", "Y");
		QFilter oversea_flag = new QFilter("aos_is_oversea_ou", "=", is_oversea_flag);
		QFilter oversea_flag2 = new QFilter("aos_isomvalid", "=", true);
		QFilter qf_ou = new QFilter("number", "=", p_ou_code);// 海外公司
		QFilter[] filters_ou = new QFilter[] { oversea_flag, oversea_flag2, qf_ou };
		DynamicObjectCollection bd_country = QueryServiceHelper.query("bd_country", "id,number", filters_ou);
		// 调用线程池
		ThreadPool threadPool = ThreadPools.newFixedThreadPool("aos_mkt_popadjst_init." + p_ou_code, 4);// 调用线程池
		for (DynamicObject ou : bd_country) {
			// 获取国别可用销售组个数
			QFilter group_org = new QFilter("aos_org.number", "=", ou.get("number"));// 海外公司
			QFilter group_enable = new QFilter("enable", "=", 1);// 可用
			QFilter[] filters_group = new QFilter[] { group_org, group_enable };
			DynamicObjectCollection aos_salgroup = QueryServiceHelper.query("aos_salgroup", "id", filters_group);
			cache.put(p_ou_code + "_GroupSizeST", aos_salgroup.size() + "", 3600);
			System.out.println(p_ou_code + "_GroupSizeST =" + aos_salgroup.size());
			for (DynamicObject group : aos_salgroup) {
				long group_id = group.getLong("id");
				Map<String, Object> params = new HashMap<>();
				params.put("p_ou_code", p_ou_code);
				params.put("p_group_id", group_id);
				do_operate(params);
			}
		}
		threadPool.close();
	}

	static class MktPopAdjSTRunnable implements Runnable {
		private Map<String, Object> params = new HashMap<>();

		public MktPopAdjSTRunnable(Map<String, Object> param) {
			this.params = param;
		}

		@Override
		public void run() {
			try {
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
		// 获取传入参数
		String p_ou_code = (String) params.get("p_ou_code");
		long p_group_id = (long) params.get("p_group_id");
		Object p_org_id = aos_sal_import_pub.get_import_id(p_ou_code, "bd_country");
		Calendar today = Calendar.getInstance();
		today.set(Calendar.HOUR_OF_DAY, 0);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.SECOND, 0);
		today.set(Calendar.MILLISECOND, 0);
		Date Today = today.getTime();
		int year = today.get(Calendar.YEAR) - 2000;
		int month = today.get(Calendar.MONTH) + 1;
		int day = today.get(Calendar.DAY_OF_MONTH);
		// 初始化数据
		HashMap<String, Map<String, Object>> SkuRpt3Avg = AosMktGenerate.GenerateSkuRpt3Group(p_ou_code);// 3日组报告
		HashMap<String, Map<String, Object>> SkuRpt = AosMktGenerate.GenerateSkuRpt7Group(p_ou_code);// 7日组报告
		HashMap<String, Map<String, Object>> KeyDetailToday = AosMktGenerate.GenerateKeyDetailToday(p_ou_code,
				p_group_id);// 今日可用词
		HashMap<String, Integer> OrderMonth = AosMktGenerate.GenerateOrderMonth(p_ou_code);// 半月销量
		HashMap<String, Map<String, Object>> KeyRpt7Map = AosMktGenerate.GenerateKeyRpt7(p_ou_code);
		HashMap<String, Map<String, Object>> PpcYester = AosMktGenerate.GeneratePpcYesterSt(p_ou_code);// 昨日数据
		HashMap<String, Map<String, Object>> PpcToday = AosMktGenerate.GeneratePpcTodaySt(p_ou_code);// 昨日数据
		HashMap<String, Map<String, Object>> AdPriceKey = AosMktGenerate.GenerateAdvKeyPrice(p_ou_code);// 关键词建议价格
		

		List<Map<String, Object>> ItemQtyList = AosMktGenerate.GenerateItemQtyList(p_ou_code);
		Map<String, Object> ItemOverseaQtyMap = ItemQtyList.get(0);// 海外库存
		HashMap<String, Map<String, Object>> PopOrgInfo = AosMktGenerate.GeneratePopOrgInfo(p_ou_code);// 营销国别标准参数
		BigDecimal EXPOSURE = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "EXPOSURE").get("aos_value");// 国别曝光标准BigDecimal
		BigDecimal avgSales7Std = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "7AVGSALES").get("aos_value");// 前7天日均销量标准

		Date SummerSpringEnd = MKTCom.Get_DateRange("aos_dateto", "SS", p_org_id);
		Date AutumnWinterEnd = MKTCom.Get_DateRange("aos_dateto", "AW", p_org_id);
		// 判断是否季末
		Date summerSpringSeasonEnd = DateUtils.setDays(DateUtils.addDays(SummerSpringEnd, -32), 1);
		Date autumnWinterSeasonEnd = DateUtils.setDays(DateUtils.addDays(AutumnWinterEnd, -32), 1);

		QFilter Qf_Date = new QFilter("aos_date", "=", Today);
		QFilter Qf_Org = new QFilter("aos_orgid.number", "=", p_ou_code);
//		QFilter Qf_type = new QFilter("aos_entryentity.aos_keystatus", "=", "AVAILABLE");
//		QFilter Qf_group = new QFilter("aos_entryentity.aos_sal_group", "=", p_group_id);
		QFilter Qf_type = new QFilter("aos_keystatus", "=", "AVAILABLE");
		QFilter Qf_group = new QFilter("aos_sal_group", "=", p_group_id);

		QFilter[] filters = new QFilter[] { Qf_Date, Qf_Org, Qf_type, Qf_group };
//		String SelectField = "id,aos_billno," + "aos_entryentity.aos_itemnumer aos_itemnumer,"
//				+ "aos_entryentity.aos_itemname aos_itemname," + "aos_entryentity.aos_season aos_season,"
//				+ "aos_entryentity.aos_category1 aos_category1," + "aos_entryentity.aos_category2 aos_category2,"
//				+ "aos_entryentity.aos_category3 aos_category3," + "aos_entryentity.aos_avadays aos_avadays,"
//				+ "aos_entryentity.aos_itemid aos_itemid," + "1 aos_count";
//		DataSet aos_mkt_pop_ppcstS = QueryServiceHelper.queryDataSet("aos_mkt_popadjst_init.do_operate" + p_ou_code,
//				"aos_mkt_pop_ppcst", SelectField, filters, null);
		String SelectField = "aos_sourceid id,aos_billno," + "aos_itemnumer,"
				+ "aos_itemname,aos_season,aos_category1,aos_category2,aos_category3," 
				+ "aos_avadays,aos_itemid,1 aos_count";
		DataSet aos_mkt_pop_ppcstS = QueryServiceHelper.queryDataSet("aos_mkt_popadjst_init.do_operate" + p_ou_code,
				"aos_mkt_ppcst_data", SelectField, filters, null);

		String[] GroupBy = new String[] { "id", "aos_billno", "aos_itemnumer", "aos_itemname", "aos_season",
				"aos_category1", "aos_category2", "aos_category3", "aos_avadays", "aos_itemid" };
		aos_mkt_pop_ppcstS = aos_mkt_pop_ppcstS.groupBy(GroupBy).sum("aos_count").finish();

		int row = 0;
		// 初始化日志
		DynamicObject aos_sync_log = BusinessDataServiceHelper.newDynamicObject("aos_sync_log");
		aos_sync_log.set("aos_type_code", "MKT_ST出价调整(销售)");
		aos_sync_log.set("aos_groupid", p_ou_code.toString() + year + month + day);
		aos_sync_log.set("billstatus", "A");
		DynamicObjectCollection aos_sync_logS = aos_sync_log.getDynamicObjectCollection("aos_entryentity");

		DynamicObject aos_mkt_pop_adjstdt = null;
		DynamicObjectCollection aos_detailentryS = null;

		while (aos_mkt_pop_ppcstS.hasNext()) {
			Row aos_mkt_pop_ppcst = aos_mkt_pop_ppcstS.next();
			row++;
			// 首次循环时新建头
			if (row == 1) {
				DynamicObject aos_mkt_pop_adjst = BusinessDataServiceHelper.newDynamicObject("aos_mkt_pop_adjst");
				aos_mkt_pop_adjst.set("aos_billno", aos_mkt_pop_ppcst.get("aos_billno"));
				aos_mkt_pop_adjst.set("aos_ppcid", aos_mkt_pop_ppcst.get("id"));
				aos_mkt_pop_adjst.set("aos_orgid", p_org_id);
				aos_mkt_pop_adjst.set("billstatus", "A");
				aos_mkt_pop_adjst.set("aos_status", "A");
				aos_mkt_pop_adjst.set("aos_makeby", SYSTEM);
				aos_mkt_pop_adjst.set("aos_makedate", Today);
				aos_mkt_pop_adjst.set("aos_groupid", p_group_id);
				OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_pop_adjst",
						new DynamicObject[] { aos_mkt_pop_adjst }, OperateOption.create());
				Object aos_sourceid = operationrst.getSuccessPkIds().get(0);

				aos_mkt_pop_adjstdt = BusinessDataServiceHelper.newDynamicObject("aos_mkt_pop_adjstdt");
				aos_mkt_pop_adjstdt.set("aos_billno", aos_mkt_pop_ppcst.get("aos_billno"));
				aos_mkt_pop_adjstdt.set("aos_orgid", p_org_id);
				aos_mkt_pop_adjstdt.set("billstatus", "A");
				aos_mkt_pop_adjstdt.set("aos_groupid", p_group_id);
				aos_mkt_pop_adjstdt.set("aos_sourceid", aos_sourceid);
				aos_mkt_pop_adjstdt.set("aos_makedate", Today);
				aos_detailentryS = aos_mkt_pop_adjstdt.getDynamicObjectCollection("aos_detailentry");
			}
			
			
			// 全部跳过 不生成数据
			if ("A".equals("A"))
							continue;

			// 插入ST出价调整(销售)数据
			long item_id = aos_mkt_pop_ppcst.getLong("aos_itemid");
			String aos_itemnumer = aos_mkt_pop_ppcst.getString("aos_itemnumer");
			String aos_orgnumber = p_ou_code;
			int aos_avadays = aos_mkt_pop_ppcst.getInteger("aos_avadays");

			Map<String, Object> SkuRpt3AvgMap = SkuRpt3Avg.get(aos_itemnumer);
			BigDecimal Impress3Avg = BigDecimal.ZERO;
			if (SkuRpt3AvgMap != null) {
				Impress3Avg = (BigDecimal) SkuRpt3AvgMap.get("aos_impressions");
			}
			MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "三天平均曝光=" + Impress3Avg + " 国别标准=" + EXPOSURE);

			// PPC数据源 出价调整(销售)筛选逻辑
			// 3天平均曝光<国别标准
			if (Impress3Avg.compareTo(EXPOSURE) >= 0)
				continue;
			// 只筛选0销量 低销量
			int halfMonthTotalSales = (int) Cux_Common_Utl.nvl(OrderMonth.get(item_id + ""));
			float R = InStockAvailableDays.getOrgItemOnlineAvgQty(p_org_id.toString(), String.valueOf(item_id));
			Boolean RegularUn = MKTCom.Get_RegularUn((long) p_org_id, item_id, aos_orgnumber, aos_avadays, R,
					halfMonthTotalSales);
			MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "非0销量低销量 标记=" + RegularUn);
			if (RegularUn) {
				continue;
			}
			// 前7天日均销量 < 国别标准
			if (BigDecimal.valueOf(R).compareTo(avgSales7Std) >= 0) {
				MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "前7天日均销量=" + R + " 国别标准=" + avgSales7Std);
				continue;
			}
			// 判断季节品是否季末有剩余
			String aos_season = aos_mkt_pop_ppcst.getString("aos_season");
			String aos_seasonpro = "";
			if ("春季产品".equals(aos_season)) {
				aos_seasonpro = "SPRING";
			}
			switch (aos_season) {
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
			// 海外库存
			Object item_overseaqty = ItemOverseaQtyMap.get(item_id + "");
			if (item_overseaqty == null || item_overseaqty.equals("null"))
				item_overseaqty = 0;
			if ("SPRING".equals(aos_seasonpro) || "SPRING_SUMMER".equals(aos_seasonpro)
					|| "SUMMER".equals(aos_seasonpro) || "SPRING-SUMMER-CONVENTIONAL".equals(aos_seasonpro)) {
				if (Today.after(summerSpringSeasonEnd)) {
					// 季末 判断是否达标
					float SeasonRate = MKTCom.Get_SeasonRate((long) p_org_id, item_id, aos_seasonpro, item_overseaqty,
							month);
					if (!MKTCom.Is_SeasonRate(aos_seasonpro, month, SeasonRate)) {
						MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "季末达标");
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

			// 开始行赋值
			DynamicObject aos_detailentry = aos_detailentryS.addNew();
			aos_detailentry.set("aos_itemid", aos_mkt_pop_ppcst.get("aos_itemid"));
			aos_detailentry.set("aos_cn_name", aos_mkt_pop_ppcst.get("aos_itemname"));
			aos_detailentry.set("aos_seasonseting", aos_mkt_pop_ppcst.get("aos_season"));
			aos_detailentry.set("aos_categroy1", aos_mkt_pop_ppcst.get("aos_category1"));
			aos_detailentry.set("aos_categroy2", aos_mkt_pop_ppcst.get("aos_category2"));
			aos_detailentry.set("aos_categroy3", aos_mkt_pop_ppcst.get("aos_category3"));
			aos_detailentry.set("aos_avadays", aos_mkt_pop_ppcst.get("aos_avadays"));
			aos_detailentry.set("aos_count", aos_mkt_pop_ppcst.get("aos_count"));// 关键词个数

			// 7日组报告字段
			Map<String, Object> SkuRptMap = SkuRpt.get(aos_itemnumer);
			BigDecimal Roi7Days = BigDecimal.ZERO;// 7天ROI
			BigDecimal aos_click = BigDecimal.ZERO;// 点击
			BigDecimal aos_impressions = BigDecimal.ZERO;// 曝光
			BigDecimal aos_sales = BigDecimal.ZERO;// 营收
			BigDecimal aos_spend = BigDecimal.ZERO;// 花费
			if (SkuRptMap != null) {
				aos_click = (BigDecimal) SkuRptMap.get("aos_clicks");
				aos_impressions = (BigDecimal) SkuRptMap.get("aos_impressions");// 曝光
				aos_sales = (BigDecimal) SkuRptMap.get("aos_sales");// 营收
				aos_spend = (BigDecimal) SkuRptMap.get("aos_spend");// 花费
			}
			if (SkuRptMap != null && (aos_spend).compareTo(BigDecimal.ZERO) != 0)
				Roi7Days = aos_sales.divide(aos_spend, 2, BigDecimal.ROUND_HALF_UP);
			aos_detailentry.set("aos_roi", Roi7Days);
			aos_detailentry.set("aos_click", aos_click);
			aos_detailentry.set("aos_spend", aos_spend);
			aos_detailentry.set("aos_expouse", aos_impressions);

			// 关键词循环 最后反写关键词个数
			DynamicObjectCollection aos_groupentryS = aos_detailentry.getDynamicObjectCollection("aos_groupentry");
			Map<String, Object> KeyRptDetail = KeyDetailToday.get(aos_itemnumer);// 关键词报告明细
			for (String aos_targetcomb : KeyRptDetail.keySet()) {
				String aos_keyword = aos_targetcomb.split("~")[0];
				String aos_match_type = aos_targetcomb.split("~")[1];
				DynamicObject aos_groupentry = aos_groupentryS.addNew();
				aos_groupentry.set("aos_keyword", aos_keyword);
				aos_groupentry.set("aos_match_type", aos_match_type);

				// 获取7日关键词报告数据
				String key = aos_itemnumer + "~" + aos_targetcomb;
				if (KeyRpt7Map.get(key) != null) {
					Map<String, Object> KeyRpt7MapD = KeyRpt7Map.get(key);
					if (KeyRpt7MapD != null) {
						BigDecimal aos_click_r = (BigDecimal) KeyRpt7MapD.get("aos_clicks");
						BigDecimal aos_spend_r = (BigDecimal) KeyRpt7MapD.get("aos_spend");
						BigDecimal aos_roi_r = (BigDecimal) KeyRpt7MapD.get("aos_roi");
						BigDecimal aos_expouse_r = (BigDecimal) KeyRpt7MapD.get("aos_impressions");
						aos_groupentry.set("aos_click_r", aos_click_r);
						aos_groupentry.set("aos_spend_r", aos_spend_r);
						aos_groupentry.set("aos_roi_r", aos_roi_r);
						aos_groupentry.set("aos_expouse_r", aos_expouse_r);
						if (aos_click_r != null && aos_expouse_r.compareTo(BigDecimal.ZERO) != 0) {
							BigDecimal aos_ctr_r = aos_click_r.divide(aos_expouse_r, 2, BigDecimal.ROUND_HALF_UP);
							aos_groupentry.set("aos_ctr", aos_ctr_r);
						}
					}
				}

				// 获取昨日词维度ST数据源
				Map<String, Object> PpcYester_Map = PpcYester.get(key);
				if (PpcYester_Map != null) {
					Object aos_lastbid = PpcYester_Map.get("aos_bid");// 昨日出价
					aos_groupentry.set("aos_lastprice", aos_lastbid);
				}
				// 获取今日词维度ST数据源
				Map<String, Object> PpcToday_Map = PpcToday.get(key);
				if (PpcToday_Map != null) {
					Object aos_bid = PpcToday_Map.get("aos_bid");// 本次出价
					Object aos_keystatus = PpcToday_Map.get("aos_keystatus");//词状态
					aos_groupentry.set("aos_calprice", aos_bid);
					aos_groupentry.set("aos_manualprz", aos_bid);
					if (!"AVAILABLE".equals(aos_keystatus))
						aos_groupentry.set("aos_valid_flag", true);
				}

				Map<String, Object> AdPriceMap = AdPriceKey.get(key);// 组
				BigDecimal aos_adviceprz = BigDecimal.ZERO;
				BigDecimal aos_highprz = BigDecimal.ZERO;
				BigDecimal aos_lowprz = BigDecimal.ZERO;
				if (AdPriceMap != null) {
					aos_adviceprz = (BigDecimal) AdPriceMap.get("aos_bid_suggest");
					aos_highprz = (BigDecimal) AdPriceMap.get("aos_bid_rangeend");
					aos_lowprz = (BigDecimal) AdPriceMap.get("aos_bid_rangestart");
				}
				aos_groupentry.set("aos_adviceprz", aos_adviceprz);
				aos_groupentry.set("aos_highprz", aos_highprz);
				aos_groupentry.set("aos_lowprz", aos_lowprz);
			}
		}
		aos_mkt_pop_ppcstS.close();

		if (aos_mkt_pop_adjstdt != null)
			OperationServiceHelper.executeOperate("save", "aos_mkt_pop_adjstdt",
					new DynamicObject[] { aos_mkt_pop_adjstdt }, OperateOption.create()); // 保存ST出价调整(销售)数据表
		OperationServiceHelper.executeOperate("save", "aos_sync_log", new DynamicObject[] { aos_sync_log },
				OperateOption.create()); // 保存日志表
		int GroupSizeCount = GroupSizeCountAdd(p_ou_code);
		int GroupSize = Integer.parseInt(cache.get(p_ou_code + "_GroupSizeST"));
		// 本国家 出价调整(销售)已全部执行完毕 后 执行 出价调整(推广)
		System.out.println("GroupSizeCountST =" + GroupSizeCount);
		System.out.println("GroupSizeST =" + GroupSize);
		if (GroupSizeCount == GroupSize) {
			Map<String, Object> adjs = new HashMap<>();
			adjs.put("p_ou_code", p_ou_code);
			aos_mkt_popadjpst_init.executerun(adjs);
		}

	}

	private synchronized static int  GroupSizeCountAdd(String p_ou_code) {
		int GroupSizeCount = Integer.parseInt(cache.get(p_ou_code + "_GroupSizeCountST")) + 1;
		cache.put(p_ou_code + "_GroupSizeCountST", GroupSizeCount + "");
		return GroupSizeCount;
	}
	
	
	
	
}