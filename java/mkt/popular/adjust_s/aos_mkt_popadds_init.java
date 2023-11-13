package mkt.popular.adjust_s;

import com.alibaba.nacos.common.utils.Pair;
import common.CommonDataSom;
import common.Cux_Common_Utl;
import common.fnd.FndGlobal;
import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.cache.CacheFactory;
import kd.bos.cache.DistributeSessionlessCache;
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
import kd.bos.servicehelper.operation.OperationServiceHelper;
import mkt.common.MKTCom;
import mkt.common.aos_mkt_common_redis;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class aos_mkt_popadds_init extends AbstractTask {

	public final static String SYSTEM = Cux_Common_Utl.SYSTEM; // ID-000000
	private static final Log logger = LogFactory.getLog(aos_mkt_popadds_init.class);
	private static final DistributeSessionlessCache cache = CacheFactory.getCommonCacheFactory()
			.getDistributeSessionlessCache("mkt_redis");

	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {// 初始化数据
		CommonDataSom.init();
		aos_mkt_common_redis.init_redis("ppc");
		Run();
	}

	public static void Run() {
		Calendar Today = Calendar.getInstance();
		int hour = Today.get(Calendar.HOUR_OF_DAY);
		// OU循环
		QFilter qf_time = null;
		DynamicObject dynamicObject = QueryServiceHelper.queryOne("aos_mkt_base_orgvalue", "aos_value",
				new QFilter[] { new QFilter("aos_type", QCP.equals, "TIME") });
		int time = 16;
		if (dynamicObject != null) {
			time = dynamicObject.getBigDecimal("aos_value").intValue();
		}
		if (hour < time)
			qf_time = new QFilter("aos_is_north_america.number", QCP.not_equals, "Y");
		else
			qf_time = new QFilter("aos_is_north_america.number", QCP.equals, "Y");
		QFilter oversea_flag = new QFilter("aos_is_oversea_ou.number", "=", "Y");// 海外公司
		QFilter oversea_flag2 = new QFilter("aos_isomvalid", "=", true);
		QFilter[] filters_ou = new QFilter[] { oversea_flag, oversea_flag2, qf_time };
		DynamicObjectCollection bd_country = QueryServiceHelper.query("bd_country", "id,number", filters_ou);
		for (DynamicObject ou : bd_country) {
			String p_ou_code = ou.getString("number");
			Map<String, Object> params = new HashMap<>();
			params.put("p_ou_code", p_ou_code);
			executerun(params);
		}

	}

	public static void executerun(Map<String, Object> param) {
		Object p_ou_code = param.get("p_ou_code");
		// 删除数据
		Calendar today = Calendar.getInstance();
		today.add(Calendar.DAY_OF_MONTH, -1);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.SECOND, 0);
		today.set(Calendar.MILLISECOND, 0);
		today.set(Calendar.HOUR, 0);
		/*
		 * Date TodayTime = today.getTime(); QFilter filter_id = new
		 * QFilter("aos_orgid.number", "=", p_ou_code); QFilter filter_date = new
		 * QFilter("aos_makedate", "=", TodayTime); QFilter[] filters_adj = new
		 * QFilter[] { filter_id, filter_date };
		 * DeleteServiceHelper.delete("aos_mkt_popadds", filters_adj);
		 */

		HashMap<String, Object> Act = aos_mkt_popadjs_init.GenerateAct();
		QFilter group_org = new QFilter("aos_org.number", "=", p_ou_code);// 海外公司
		QFilter group_enable = new QFilter("enable", "=", 1);// 可用
		QFilter[] filters_group = new QFilter[] { group_org, group_enable };
		DynamicObjectCollection aos_salgroup = QueryServiceHelper.query("aos_salgroup", "id", filters_group);
		logger.info("aos_salgroup.size() = " + aos_salgroup.size());
		for (DynamicObject group : aos_salgroup) {
			Map<String, Object> params = new HashMap<>();
			params.put("p_ou_code", p_ou_code);
			params.put("p_group_id", group.getLong("id"));
			do_operate(params, Act);
		}
	}

	public static void do_operate(Map<String, Object> params, HashMap<String, Object> Act) {
		String p_ou_code = (String) params.get("p_ou_code");
		long p_group_id = (long) params.get("p_group_id");
		Object p_org_id = FndGlobal.get_import_id(p_ou_code, "bd_country");
		Boolean aos_is_north_america = QueryServiceHelper
				.queryOne("bd_country", "aos_is_north_america", new QFilter[] { new QFilter("id", "=", p_org_id) })
				.getBoolean("aos_is_north_america");

		Calendar today = Calendar.getInstance();
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.SECOND, 0);
		today.set(Calendar.MILLISECOND, 0);
		today.set(Calendar.HOUR_OF_DAY, 0);
		Date now = today.getTime();
		int month = today.get(Calendar.MONTH) + 1;
		today.add(Calendar.DAY_OF_MONTH, -1);
		Date Today = today.getTime();
		// 数据初始化
		HashMap<String, Object> LastBid = GenerateLastBid(p_ou_code, Today);

		byte[] serialize_poporgInfo = cache.getByteValue("mkt_poporginfo"); // 营销国别参数表
		HashMap<String, Map<String, Object>> PopOrgInfo = SerializationUtils.deserialize(serialize_poporgInfo);

		byte[] serialize_skurptdetail = cache.getByteValue("mkt_skurptDetail"); // SKU报告1日
		HashMap<String, Map<String, Object>> SkuRptDetail = SerializationUtils.deserialize(serialize_skurptdetail);
		byte[] serialize_skurpt3avg = cache.getByteValue("mkt_skurpt3avg"); // SKU报表三日平均
		HashMap<String, Map<String, Object>> SkuRpt3Avg = SerializationUtils.deserialize(serialize_skurpt3avg);
		// 获取缓存
		byte[] serialize_item = cache.getByteValue("item");
		HashMap<String, Map<String, Object>> item = SerializationUtils.deserialize(serialize_item);
		Map<String, Object> item_overseaqty_map = item.get("item_overseaqty");// 海外在库数量
		Map<String, Object> item_maxage_map = item.get("item_maxage");// 最大库龄
		HashMap<String, Integer> Order30 = GenerateOrder(p_org_id, 30);// 30日销量
		HashMap<String, Integer> Order7 = GenerateOrder(p_org_id, 7);// 7日销量

		HashMap<String, Map<String, Object>> ItemMap = GenerateItem(p_org_id);

		Date ChristmasStart = MKTCom.Get_DateRange("aos_datefrom", "CH-1", p_org_id); // 圣诞季初开始
		Date ChristmasEnd = MKTCom.Get_DateRange("aos_dateto", "CH-2", p_org_id);// 圣诞季中结束
		Date HA_E_1 = MKTCom.Get_DateRange("aos_datefrom", "HA-E-1", p_org_id);// 万圣欧洲季初开始
		Date HA_E_2 = MKTCom.Get_DateRange("aos_dateto", "HA-E-2", p_org_id);// 万圣欧洲季中结束
		Date HA_U_1 = MKTCom.Get_DateRange("aos_datefrom", "HA-U-1", p_org_id);// 万圣北美季初开始
		Date HA_U_2 = MKTCom.Get_DateRange("aos_dateto", "HA-U-2", p_org_id);// 万圣北美季中结束

		Date SummerSpringStart = MKTCom.Get_DateRange("aos_datefrom", "SS", p_org_id);
		Date SummerSpringEnd = MKTCom.Get_DateRange("aos_dateto", "SS", p_org_id);
		Date AutumnWinterStart = MKTCom.Get_DateRange("aos_datefrom", "AW", p_org_id);
		Date AutumnWinterEnd = MKTCom.Get_DateRange("aos_dateto", "AW", p_org_id);

		BigDecimal MULTI = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "MULTI").get("aos_value");

		Set<String> CategorySet = GenerateCategorySet(p_org_id);// 平台上架信息
		Set<String> Category = GenerateCategory(p_org_id);
		Set<String> StItem = GenerateStItem(p_org_id);
		Set<String> Season = GenerateSeaSet(p_org_id, Today);

		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String now_str = writeFormat.format(now);

		logger.info("now_str:" + now_str);
		logger.info("p_ou_code:" + p_ou_code);
		// 今天PPC单据
		QFilter Qf_Date = new QFilter("aos_date", ">=", now_str);
		QFilter Qf_Org = new QFilter("aos_orgid.number", "=", p_ou_code);
		QFilter[] filters = new QFilter[] { Qf_Date, Qf_Org };
		String SelectField = "id";
		DynamicObject aos_mkt_popular_ppcn = QueryServiceHelper.queryOne("aos_mkt_popular_ppc", SelectField, filters);
		if (aos_mkt_popular_ppcn == null)
			return;
		Object nowid = aos_mkt_popular_ppcn.get("id");

		Qf_Date = new QFilter("aos_date", "=", now_str);
		Qf_Org = new QFilter("aos_orgid.number", "=", p_ou_code);
		QFilter Qf_roi = new QFilter("aos_entryentity.aos_roiflag", "=", true).or("aos_entryentity.aos_groupstatus",
				"=", "PRO");
		QFilter Qf_group = new QFilter("aos_entryentity.aos_sal_group.id", "=", p_group_id);

		filters = new QFilter[] { Qf_Date, Qf_Org, Qf_group, Qf_roi };
		SelectField = "aos_billno,aos_orgid," + "aos_orgid.number aos_orgnumber,"
				+ "aos_poptype,aos_entryentity.aos_productno aos_productno,"
				+ "aos_entryentity.aos_seasonseting aos_seasonseting,"
				+ "aos_entryentity.aos_contryentrystatus aos_contryentrystatus,"
				+ "aos_entryentity.aos_cn_name aos_cn_name," + "aos_entryentity.aos_itemnumer aos_itemnumer,"
				+ "aos_entryentity.aos_avadays aos_avadays," + "aos_entryentity.aos_bid aos_bid,"
				+ "aos_entryentity.aos_lastpricedate aos_lastpricedate," + "aos_entryentity.aos_lastbid aos_lastbid,"
				+ "aos_entryentity.aos_groupdate aos_groupdate," + "aos_entryentity.aos_roi7days aos_roi7days,"
				+ "aos_entryentity.aos_itemid aos_itemid," + "aos_entryentity.aos_sal_group aos_sal_group,"
				+ "aos_entryentity.id aos_ppcentryid," + "id," + "aos_entryentity.aos_highvalue aos_highvalue,"
				+ "aos_entryentity.aos_basestitem aos_basestitem," + "aos_entryentity.aos_roiflag aos_roiflag,"
				+ "aos_entryentity.aos_groupstatus aos_groupstatus," + "aos_entryentity.aos_special aos_special," // 特殊广告
				+ "aos_entryentity.aos_eliminatedate aos_eliminatedate";
		DynamicObjectCollection aos_mkt_popular_ppcS = QueryServiceHelper.query("aos_mkt_popular_ppc", SelectField,
				filters, "aos_entryentity.aos_productno");
		int rows = aos_mkt_popular_ppcS.size();
		int count = 0;
		logger.info("rows =" + rows);
		if (rows == 0)
			return;

		DynamicObject Head = aos_mkt_popular_ppcS.get(0);
		// 销售加回
		DynamicObject aos_mkt_popadds = BusinessDataServiceHelper.newDynamicObject("aos_mkt_popadds");
		aos_mkt_popadds.set("aos_billno", Head.get("aos_billno"));
		aos_mkt_popadds.set("aos_ppcid", nowid);
		aos_mkt_popadds.set("aos_orgid", p_org_id);
		aos_mkt_popadds.set("billstatus", "A");
		aos_mkt_popadds.set("aos_status", "A");
		aos_mkt_popadds.set("aos_makeby", SYSTEM);
		aos_mkt_popadds.set("aos_makedate", now);
		aos_mkt_popadds.set("aos_groupid", p_group_id);

		OperationServiceHelper.executeOperate("save", "aos_mkt_popadds", new DynamicObject[] { aos_mkt_popadds },
				OperateOption.create());
		DynamicObjectCollection aos_entryentityS = aos_mkt_popadds.getDynamicObjectCollection("aos_entryentity");

		// 获取所有的物料编码
		List<String> list_itemNumber = aos_mkt_popular_ppcS.stream().map(dy -> dy.getString("aos_itemnumer"))
				.filter(number -> number != null).collect(Collectors.toList());
		// 分别从常规和排名获取改善措施
		Pair<Map<String, String>, Map<String, String>> pair_improve = queryImproveStep(p_org_id, list_itemNumber);

		for (DynamicObject aos_mkt_popular_ppc : aos_mkt_popular_ppcS) {
			count++;
			String aos_itemnumer = aos_mkt_popular_ppc.getString("aos_itemnumer");
			Boolean aos_roiflag = aos_mkt_popular_ppc.getBoolean("aos_roiflag");
			String aos_groupstatus = aos_mkt_popular_ppc.getString("aos_groupstatus");
			String aos_productno = aos_mkt_popular_ppc.getString("aos_productno");
			String aos_seasonseting = aos_mkt_popular_ppc.getString("aos_seasonseting");
			long item_id = aos_mkt_popular_ppc.getLong("aos_itemid");
			BigDecimal aos_roi = aos_mkt_popular_ppc.getBigDecimal("aos_roi7days");
			BigDecimal aos_bid = aos_mkt_popular_ppc.getBigDecimal("aos_bid");
			Date aos_lastpricedate = aos_mkt_popular_ppc.getDate("aos_lastpricedate");
			String aos_cn_name = aos_mkt_popular_ppc.getString("aos_cn_name");
			Object aos_special = aos_mkt_popular_ppc.get("aos_special"); // 特殊广告

			Map<String, Object> SkuRpt3AvgMap = SkuRpt3Avg.get(p_org_id + "~" + item_id);
			BigDecimal Impress3Avg = BigDecimal.ZERO;
			if (SkuRpt3AvgMap != null) {
				Impress3Avg = (BigDecimal) SkuRpt3AvgMap.get("aos_impressions");
			}

			String category = CommonDataSom.getItemCategoryName(item_id + "");
			if (category == null || "".equals(category))
				continue;
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

			Map<String, Object> SkuRptDetailMap = SkuRptDetail.get(p_org_id + "~" + item_id);// Sku报告
			BigDecimal aos_groupcost = BigDecimal.ZERO;
			if (SkuRptDetailMap != null)
				aos_groupcost = (BigDecimal) SkuRptDetailMap.get("aos_spend");

			Object item_overseaqty = item_overseaqty_map.get(p_org_id + "~" + item_id);
			if (item_overseaqty == null || item_overseaqty.equals("null"))
				item_overseaqty = 0;

			Object aos_activity = Act.get(p_org_id + "~" + item_id);

			// 七日销量
			float aos_7_sales = Order7.getOrDefault(item_id + "", 0);
			float aos_30_sales = Order30.getOrDefault(item_id + "", 0);
			Object aos_age = item_maxage_map.getOrDefault(p_org_id + "~" + item_id, 0);

			// 对于类型
			String aos_type = null;
			// 相关标记
			// 1.节日品标记
			Boolean ChrisFlag = false;
			if (now.after(ChristmasStart) && now.before(ChristmasEnd) || now == ChristmasStart || now == ChristmasEnd)
				ChrisFlag = true;
			Boolean HallowFlag = false;

			if (aos_is_north_america && HA_U_1 != null && HA_U_2 != null
					&& (now.after(HA_U_1) && now.before(HA_U_2) || now.equals(HA_U_1) || now.equals(HA_U_2))) {
				HallowFlag = true;
			} else if (!aos_is_north_america && HA_E_1 != null && HA_E_2 != null
					&& (now.after(HA_E_1) && now.before(HA_E_2) || now.equals(HA_E_1) || now.equals(HA_E_2))) {
				HallowFlag = true;
			}

			// 2.新品标记
			Boolean NewFlag = false;
			Date aos_firstindate = null;
			String aos_itemstatus = null;
			String aos_seasonpro = null;
			if (ItemMap.get(item_id + "") != null) {
				if (ItemMap.get(item_id + "").get("aos_firstindate") != null)
					aos_firstindate = (Date) ItemMap.get(item_id + "").get("aos_firstindate");
				if (ItemMap.get(item_id + "").get("aos_contryentrystatus") != null)
					aos_itemstatus = (String) ItemMap.get(item_id + "").get("aos_contryentrystatus");
				if (ItemMap.get(item_id + "").get("aos_seasonseting") != null)
					aos_seasonpro = (String) ItemMap.get(item_id + "").get("aos_seasonseting");
			}
			if (aos_firstindate == null || (("E".equals(aos_itemstatus) || "A".equals(aos_itemstatus))
					&& (aos_firstindate != null) && MKTCom.GetBetweenDays(Today, aos_firstindate) <= 30))
				NewFlag = true;
			// 3.品牌季节品当季标记
			// 春夏品
			Boolean SpringSummerFlag = false;
			if ((aos_seasonpro != null) && (aos_seasonpro.equals("SPRING") || aos_seasonpro.equals("SPRING_SUMMER")
					|| aos_seasonpro.equals("SUMMER")))
				SpringSummerFlag = true;
			// 秋冬品
			Boolean AutumnWinterFlag = false;
			if ((aos_seasonpro != null) && (aos_seasonpro.equals("AUTUMN_WINTER") || aos_seasonpro.equals("WINTER")))
				AutumnWinterFlag = true;
			Boolean SeasonFlag = false;
			if (!((aos_seasonpro != null)
					&& (aos_seasonpro.equals("SPRING") || aos_seasonpro.equals("SPRING_SUMMER")
							|| aos_seasonpro.equals("SUMMER"))
					&& (Today.before(SummerSpringStart) || Today.after(SummerSpringEnd)))) {
				SeasonFlag = true;
			}
			if (!((aos_seasonpro != null) && (aos_seasonpro.equals("AUTUMN_WINTER") || aos_seasonpro.equals("WINTER"))
					&& (Today.before(AutumnWinterStart) || Today.after(AutumnWinterEnd)))) {
				SeasonFlag = true;
			}
			// 4.季节品 分季标记
			Boolean RangeFlag = false;
			if (aos_seasonpro == null)
				aos_seasonpro = "";
			float SeasonRate = 0;
			if (SpringSummerFlag || AutumnWinterFlag)
				SeasonRate = MKTCom.Get_SeasonRate((long) p_org_id, item_id, aos_seasonpro, item_overseaqty, month);

			if (SpringSummerFlag && (month == 2 || month == 3 || month == 4))
				RangeFlag = true;
			else if (AutumnWinterFlag && (month == 8 || month == 9 || month == 10))
				RangeFlag = true;
			else if (SpringSummerFlag && (month == 5 || month == 6 || month == 7 || month == 8 || month == 9)
					&& MKTCom.Is_SeasonRate(aos_seasonpro, month, SeasonRate))
				RangeFlag = true;
			else if (AutumnWinterFlag && (month == 11 || month == 12 || month == 1 || month == 2 || month == 3)
					&& MKTCom.Is_SeasonRate(aos_seasonpro, month, SeasonRate))
				RangeFlag = true;

			// 开始类型判断
			if (("其它节日装饰".equals(aos_category2) && HallowFlag) || ("圣诞装饰".equals(aos_category2) && ChrisFlag))
				// 1.节日品
				aos_type = "FES";
			else if (NewFlag)
				// 2.新品大产品
				aos_type = "NEW";
			else if (CategorySet.contains(aos_category1 + "~" + aos_category2)
					&& (int) item_overseaqty > MULTI.intValue() && ((!SpringSummerFlag && !AutumnWinterFlag)
							|| (SpringSummerFlag && SeasonFlag) || (AutumnWinterFlag || SeasonFlag)))
				// 3.品牌产品
				aos_type = "BRA";
			else if ((int) item_overseaqty > MULTI.intValue() && RangeFlag)
				// 4.季节品
				aos_type = "SEA";
			else if (Category.contains(aos_category1 + "~" + aos_category2 + "~" + aos_category3))
				// 5.特殊品类
				aos_type = "CAT";
			else if (!Cux_Common_Utl.IsNull(aos_activity) && (aos_activity + "").contains("7DD"))
				// 6.活动产品
				aos_type = "ACT";
			else if (StItem.contains(item_id + ""))
				// 7.货多销少
				aos_type = "LES";
			else if (FndGlobal.IsNotNull(Season) && Season.contains(item_id + ""))
				// 8.季节品调价表-降价
				aos_type = "DIS";
			else
				// 9.其余类型不需要加回
				continue;

			// 对于需要进入销售加回表的数据
			DynamicObject aos_entryentity = aos_entryentityS.addNew();
			aos_entryentity.set("aos_productno", aos_productno);
			aos_entryentity.set("aos_itemnumer", aos_itemnumer);
			aos_entryentity.set("aos_categroy1", aos_category1);
			aos_entryentity.set("aos_categroy2", aos_category2);
			aos_entryentity.set("aos_categroy3", aos_category3);
			aos_entryentity.set("aos_roi", aos_roi);
			aos_entryentity.set("aos_expouse", Impress3Avg);
			aos_entryentity.set("aos_spend", aos_groupcost);
			aos_entryentity.set("aos_bid", aos_bid);
			aos_entryentity.set("aos_avadays", item_overseaqty);// 海外可用库存
			aos_entryentity.set("aos_lastdate", aos_lastpricedate);
			aos_entryentity.set("aos_7_sales", aos_7_sales);
			aos_entryentity.set("aos_30_sales", aos_30_sales);
			aos_entryentity.set("aos_age", aos_age);
			aos_entryentity.set("aos_itemname", aos_cn_name);
			aos_entryentity.set("aos_type", aos_type);
			aos_entryentity.set("aos_seasonseting", aos_seasonseting);// 季节属性
			aos_entryentity.set("aos_lastbid", LastBid.get(aos_itemnumer));// 剔除前一次出价
			aos_entryentity.set("aos_special", aos_special); // 特殊广告
			if (aos_activity != null)
				aos_entryentity.set("aos_activity", aos_activity);
			// 常规品滞销存在措施
			if (pair_improve.getFirst().containsKey(aos_itemnumer)) {
				aos_entryentity.set("aos_improvedesc", pair_improve.getFirst().get(aos_itemnumer));
			} else {
				// 排名改善存在措施
				if (pair_improve.getSecond().containsKey(aos_itemnumer))
					aos_entryentity.set("aos_improvedesc", pair_improve.getSecond().get(aos_itemnumer));
			}
			String aos_eliminatereason = "";
			if (aos_roiflag) {
				aos_eliminatereason = "ROI";
			} else {
				aos_eliminatereason = aos_groupstatus;
			}
			aos_entryentity.set("aos_eliminatereason", aos_eliminatereason);// 剔除原因
		}
		OperationServiceHelper.executeOperate("save", "aos_mkt_popadds", new DynamicObject[] { aos_mkt_popadds },
				OperateOption.create());
	}

	/**
	 * 获取改善措施
	 * 
	 * @param orgid       国别
	 * @param list_number 物料编码
	 * @return itemToImprove
	 */
	private static Pair<Map<String, String>, Map<String, String>> queryImproveStep(Object orgid,
			List<String> list_number) {
		// 获取常规品的改善措施
		LocalDate lastWeek = LocalDate.now().minusDays(8);
		QFilter filter_org = new QFilter("aos_orgid", QFilter.equals, orgid);
		QFilter filter_item = new QFilter("aos_entryentity.aos_itemid.number", QFilter.in, list_number);
		QFilter filter_date = new QFilter("aos_makedate", ">=", lastWeek.toString());
		QFilter filter_measure = new QFilter("aos_entryentity.aos_measure", "!=", "");
		QFilter[] qfs = new QFilter[] { filter_item, filter_date, filter_org, filter_measure };
		StringJoiner str = new StringJoiner(",");
		str.add("aos_entryentity.aos_itemid.number item");
		str.add("aos_entryentity.aos_measure aos_measure");
		Map<String, String> map_rountine = QueryServiceHelper.query("aos_sal_rpusdetail", str.toString(), qfs).stream()
				.collect(Collectors.toMap(dy -> dy.getString("item"), dy -> dy.getString("aos_measure"),
						(key1, key2) -> key2));

		filter_measure.__setProperty("aos_entryentity.aos_improvedesc");
		qfs = new QFilter[] { filter_org, filter_item, filter_date, filter_measure };
		str = new StringJoiner(",");
		str.add("aos_entryentity.aos_itemid.number item");
		str.add("aos_entryentity.aos_improvedesc aos_improvedesc");
		Map<String, String> map_rank = QueryServiceHelper.query("aos_sal_rankdetail", str.toString(), qfs).stream()
				.collect(Collectors.toMap(dy -> dy.getString("item"), dy -> dy.getString("aos_improvedesc"),
						(key1, key2) -> key2));
		Pair<Map<String, String>, Map<String, String>> with = Pair.with(map_rountine, map_rank);
		return with;
	}

	public static HashMap<String, Object> GenerateLastBid(String p_ou_code, Date today) {
		HashMap<String, Object> LastBid = new HashMap<>();
		QFilter filter_org = new QFilter("aos_orgid.number", "=", p_ou_code);
		QFilter filter_date = new QFilter("aos_date", "<=", today);
		QFilter filter_bid = new QFilter("aos_entryentity.aos_bid", QCP.not_equals, null);

		QFilter[] filters = new QFilter[] { filter_org, filter_date, filter_bid };
		String Select = "aos_entryentity.aos_itemnumer aos_itemnumer," + "aos_entryentity.aos_bid aos_bid, "
				+ "aos_date";
		DataSet aos_mkt_popular_ppcS = QueryServiceHelper.queryDataSet("GenerateLastBid" + p_ou_code,
				"aos_mkt_popular_ppc", Select, filters, null);
		String[] GroupBy = new String[] { "aos_itemnumer" };
		aos_mkt_popular_ppcS = aos_mkt_popular_ppcS.groupBy(GroupBy).maxP("aos_date", "aos_bid").finish();
		while (aos_mkt_popular_ppcS.hasNext()) {

			Row aos_mkt_popular_ppc = aos_mkt_popular_ppcS.next();

			LastBid.put(aos_mkt_popular_ppc.getString("aos_itemnumer"), aos_mkt_popular_ppc.getBigDecimal("aos_bid"));
		}
		aos_mkt_popular_ppcS.close();
		return LastBid;
	}

	/** 近30日销量 **/
	private static HashMap<String, Integer> GenerateOrder(Object p_org_id, int day) {
		HashMap<String, Integer> Order = new HashMap<>();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		Calendar today = Calendar.getInstance();
		today.set(Calendar.HOUR_OF_DAY, 0);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.SECOND, 0);
		today.set(Calendar.MILLISECOND, 0);
		today.add(Calendar.DAY_OF_MONTH, -day);
		QFilter filter_date = new QFilter("aos_order_date", QCP.large_equals, sdf.format(today.getTime()));
		QFilter filter_org = new QFilter("aos_org.id", QCP.equals, p_org_id);
		List<QFilter> orderFilter = SalUtil.getOrderFilter();
		orderFilter.add(filter_date);
		orderFilter.add(filter_org);
		QFilter[] filters = orderFilter.toArray(new QFilter[orderFilter.size()]);
		DataSet orderDataSet = QueryServiceHelper.queryDataSet("aos_mkt_popadds_init_GenerateOrderMonth",
				"aos_sync_om_order_r", "aos_org,aos_item_fid aos_itemid,aos_order_qty", filters, null);
		orderDataSet = orderDataSet.groupBy(new String[] { "aos_org", "aos_itemid" }).sum("aos_order_qty").finish();
		while (orderDataSet.hasNext()) {
			Row aos_sync_omonth_summary = orderDataSet.next();
			int aos_total_qty = aos_sync_omonth_summary.getInteger("aos_order_qty");
			String aos_itemid = aos_sync_omonth_summary.getString("aos_itemid");
			Order.put(aos_itemid, aos_total_qty);
		}
		orderDataSet.close();
		return Order;
	}

	private static Set<String> GenerateStItem(Object aos_orgid) {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_base_stitem", "aos_itemid",
				new QFilter[] { new QFilter("aos_orgid", QCP.equals, aos_orgid) });
		return list.stream().map(obj -> obj.getString("aos_itemid")).collect(Collectors.toSet());
	}

	private static Set<String> GenerateCategory(Object aos_orgid) {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_specategory",
				"aos_category1,aos_category2,aos_category3",
				new QFilter[] { new QFilter("aos_orgid", QCP.equals, aos_orgid) });
		return list.stream().map(obj -> obj.getString("aos_category1") + "~" + obj.getString("aos_category2") + "~"
				+ obj.getString("aos_category3")).collect(Collectors.toSet());
	}

	private static HashMap<String, Map<String, Object>> GenerateItem(Object p_org_id) {
		HashMap<String, Map<String, Object>> ItemMap = new HashMap<>();
		QFilter filter_ou = new QFilter("aos_contryentry.aos_nationality.id", "=", p_org_id);
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
				+ "aos_contryentry.aos_firstindate aos_firstindate";
		DynamicObjectCollection bd_materialS = QueryServiceHelper.query("bd_material", SelectField, filters,
				"aos_productno");
		for (DynamicObject bd_material : bd_materialS) {
			String aos_itemid = bd_material.getString("id");
			Map<String, Object> Info = ItemMap.get(aos_itemid);
			if (Info == null)
				Info = new HashMap<>();
			Info.put("aos_contryentrystatus", bd_material.get("aos_contryentrystatus"));
			Info.put("aos_firstindate", bd_material.get("aos_firstindate"));
			Info.put("aos_seasonseting", bd_material.get("aos_seasonseting"));
			ItemMap.put(aos_itemid, Info);
		}
		return ItemMap;
	}

	private static Set<String> GenerateCategorySet(Object p_org_id) {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_category", "aos_category1,aos_category2",
				new QFilter[] { new QFilter("aos_orgid", QCP.equals, p_org_id) });
		return list.stream().map(obj -> obj.getString("aos_category1") + "~" + obj.getString("aos_category2"))
				.collect(Collectors.toSet());
	}

	private static Set<String> GenerateSeaSet(Object p_org_id, Date today) {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_sal_quo_sa",
				"aos_entryentity.aos_sku.id aos_sku",
				new QFilter("aos_org", QCP.equals, p_org_id).and("aos_sche_date", QCP.large_than, today)
						.and("aos_entryentity.aos_adjtype", QCP.equals, "降价").toArray());
		return list.stream().map(obj -> obj.getString("aos_sku")).collect(Collectors.toSet());
	}

	public static HashMap<String, Object> GenerateYes(String aos_orgid, String aos_groupid) {
		HashMap<String, Object> Yester = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.add(Calendar.DAY_OF_MONTH, -4);
		Date date = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_str = writeFormat.format(date);
		QFilter filter_date = new QFilter("aos_makedate", ">=", date_str);
		QFilter filter_org = new QFilter("aos_orgid.id", "=", aos_orgid);
		QFilter filter_group = new QFilter("aos_groupid.id", "=", aos_groupid);
		QFilter filter_flag = new QFilter("aos_entryentity.aos_add", "=", true);
		QFilter[] filters = new QFilter[] { filter_date, filter_org, filter_group, filter_flag };
		String Select = "aos_entryentity.aos_itemnumer aos_itemnumer";
		DataSet aos_mkt_popaddsS = QueryServiceHelper.queryDataSet("aos_mkt_popadds.GenerateYes", "aos_mkt_popadds",
				Select, filters, "aos_makedate");

		while (aos_mkt_popaddsS.hasNext()) {
			Row aos_mkt_popadds = aos_mkt_popaddsS.next();
			Yester.put(aos_mkt_popadds.getString("aos_itemnumer"), aos_mkt_popadds.getString("aos_itemnumer"));
		}
		aos_mkt_popaddsS.close();
		return Yester;
	}

}