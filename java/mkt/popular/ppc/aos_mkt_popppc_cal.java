package mkt.popular.ppc;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import common.CommonDataSom;
import common.fnd.FndGlobal;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.time.DateUtils;

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
import kd.bos.servicehelper.operation.OperationServiceHelper;
import mkt.common.MKTCom;
import mkt.common.aos_mkt_common_redis;

public class aos_mkt_popppc_cal extends AbstractTask {

	private static final DistributeSessionlessCache cache = CacheFactory.getCommonCacheFactory()
			.getDistributeSessionlessCache("mkt_redis");

	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		// 多线程执行
		executerun();
	}

	public static void ManualitemClick(String aos_ou_code) {
		// 初始化数据
		CommonDataSom.init();
		aos_mkt_common_redis.init_redis("ppc");
		Map<String, Object> params = new HashMap<>();
		params.put("p_ou_code", aos_ou_code);
		do_operate(params);
	}

	private static void executerun() {
		// 初始化数据
		CommonDataSom.init();
		aos_mkt_common_redis.init_redis("ppc");

		Calendar Today = Calendar.getInstance();
		int hour = Today.get(Calendar.HOUR_OF_DAY);
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
		// 调用线程池
		QFilter oversea_flag = new QFilter("aos_is_oversea_ou.number", "=", "Y");// 海外公司
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

	@SuppressWarnings("deprecation")
	public static void do_operate(Map<String, Object> params) {
		try {
			// 获取缓存
			byte[] serialize_item = cache.getByteValue("item");
			HashMap<String, Map<String, Object>> item = SerializationUtils.deserialize(serialize_item);
			Map<String, Object> item_overseaqty_map = item.get("item_overseaqty");// 海外在库数量
			Map<String, Object> item_intransqty_map = item.get("item_intransqty");// 在途数量
			byte[] serialize_datepara = cache.getByteValue("datepara");
			HashMap<String, Map<String, Object>> datepara = SerializationUtils.deserialize(serialize_datepara);
			Map<String, Object> aos_shpday_map = datepara.get("aos_shp_day");// 备货天数
			Map<String, Object> aos_clearday_map = datepara.get("aos_clear_day");// 清关天数
			Map<String, Object> aos_freightday_map = datepara.get("aos_freight_day");// 海运天数
			byte[] serialize_ProductInfo = cache.getByteValue("mkt_productinfo");// Amazon店铺货号
			HashMap<String, String> mkt_productinfo = SerializationUtils.deserialize(serialize_ProductInfo);
			byte[] serialize_ppcInfo = cache.getByteValue("mkt_ppcinfo"); // PPC系列与组创建日期
			HashMap<String, Map<String, Object>> PPCInfo = SerializationUtils.deserialize(serialize_ppcInfo);
			byte[] serialize_ppcInfoSerial = cache.getByteValue("mkt_ppcinfoSerial"); // PPC系列与组创建日期
			HashMap<String, Map<String, Object>> PPCInfoSerial = SerializationUtils
					.deserialize(serialize_ppcInfoSerial);
			byte[] serialize_poporgInfo = cache.getByteValue("mkt_poporginfo"); // 营销国别参数表
			HashMap<String, Map<String, Object>> PopOrgInfo = SerializationUtils.deserialize(serialize_poporgInfo);
			byte[] serialize_skurpt14 = cache.getByteValue("mkt_skurpt14"); // SKU报告14日
			HashMap<String, Map<String, Object>> SkuRpt14 = SerializationUtils.deserialize(serialize_skurpt14);

			// 获取传入参数 国别
			Object p_ou_code = params.get("p_ou_code");
			Object p_org_id = FndGlobal.get_import_id(p_ou_code, "bd_country");

			// 获取当前日期
			Calendar date = Calendar.getInstance();
			date.set(Calendar.HOUR_OF_DAY, 0);
			date.set(Calendar.MINUTE, 0);
			date.set(Calendar.SECOND, 0);
			date.set(Calendar.MILLISECOND, 0);
			Date Today = date.getTime();

			SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");
			date.add(Calendar.DAY_OF_MONTH, -1);
			String Yester = DF.format(date.getTime());
			date.add(Calendar.DAY_OF_MONTH, -1);

			// 获取单号
			int month = date.get(Calendar.MONTH) + 1;
			int week = date.get(Calendar.DAY_OF_WEEK);

			// 获取春夏品 秋冬品开始结束日期 营销日期参数表
			Date SummerSpringStart = MKTCom.Get_DateRange("aos_datefrom", "SS", p_org_id);
			Date SummerSpringEnd = MKTCom.Get_DateRange("aos_dateto", "SS", p_org_id);
			Date AutumnWinterStart = MKTCom.Get_DateRange("aos_datefrom", "AW", p_org_id);
			Date AutumnWinterEnd = MKTCom.Get_DateRange("aos_dateto", "AW", p_org_id);
			// 判断是否季末
			Date summerSpringSeasonEnd = DateUtils.setDays(DateUtils.addDays(SummerSpringEnd, -32), 1);
			Date autumnWinterSeasonEnd = DateUtils.setDays(DateUtils.addDays(AutumnWinterEnd, -32), 1);
			Date HalloweenStart = MKTCom.Get_DateRange("aos_datefrom", "Halloween", p_org_id);// 万圣节
			Date HalloweenEnd = MKTCom.Get_DateRange("aos_dateto", "Halloween", p_org_id);// 万圣节
			Date ChristmasStart = MKTCom.Get_DateRange("aos_datefrom", "Christmas", p_org_id);// 圣诞节
			Date ChristmasEnd = MKTCom.Get_DateRange("aos_dateto", "Christmas", p_org_id);// 圣诞节
			Map<String, Integer> ProductNo_Map = new HashMap<>();
			// 获取营销国别参数
			BigDecimal QtyStandard = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "QTYSTANDARD").get("aos_value");// 国别海外库存标准
			Set<String> ActAmazonItem = GenActAmazonItem(p_org_id + "");

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
					+ "aos_contryentry.aos_firstindate aos_firstindate";
			DynamicObjectCollection bd_materialS = QueryServiceHelper.query("bd_material", SelectField, filters,
					"aos_productno");
			int rows = bd_materialS.size();
			int count = 0;
			Set<String> eliminateItemSet = getEliminateItem(p_org_id.toString());

			DynamicObject aos_sync_log = BusinessDataServiceHelper.newDynamicObject("aos_sync_log");
			aos_sync_log.set("aos_type_code", "MKT_PPC临时剔除计算");
			aos_sync_log.set("aos_groupid", p_ou_code.toString());
			aos_sync_log.set("billstatus", "A");
			DynamicObjectCollection aos_sync_logS = aos_sync_log.getDynamicObjectCollection("aos_entryentity");

			for (DynamicObject bd_material : bd_materialS) {
				count++;
				// 判断是否跳过
				long item_id = bd_material.getLong("id");
				long org_id = bd_material.getLong("aos_orgid");
				String aos_itemnumer = bd_material.getString("number");
				String aos_productno = bd_material.getString("aos_productno");

				if (eliminateItemSet.contains(String.valueOf(item_id)))
					continue;
				// 获取AMAZON店铺货号 如果没有则跳过
				String aos_shopsku = mkt_productinfo.get(org_id + "~" + item_id);
				if (aos_shopsku == null || aos_shopsku.equals("") || aos_shopsku.equals("null")) {
					continue;
				}
				Date aos_firstindate = bd_material.getDate("aos_firstindate");
				String aos_seasonpro = bd_material.getString("aos_seasonseting");
				Object aos_seasonid = bd_material.get("aos_seasonid");
				if (aos_seasonpro == null)
					aos_seasonpro = "";
				String aos_itemstatus = bd_material.getString("aos_contryentrystatus");
				String aos_festivalseting = bd_material.getString("aos_festivalseting");
				if (aos_festivalseting == null)
					aos_festivalseting = "";
				if (aos_seasonid == null) {
					continue;
				}
				Object item_overseaqty = item_overseaqty_map.get(org_id + "~" + item_id);
				if (item_overseaqty == null || item_overseaqty.equals("null"))
					item_overseaqty = 0;
				if ("C".equals(aos_itemstatus) && (int) item_overseaqty == 0)
					continue;// 终止状态且无海外库存 跳过
				Object item_intransqty = item_intransqty_map.get(org_id + "~" + item_id);
				if (item_intransqty == null || item_intransqty.equals("null"))
					item_intransqty = 0;
				// 产品号不存在 跳过
				if (aos_productno == null || aos_productno.equals("") || aos_productno.equals("null")) {
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
				String itemCategoryName = CommonDataSom.getItemCategoryName(String.valueOf(item_id));
				String aos_category2 = "";
				if (!"".equals(itemCategoryName) && null != itemCategoryName) {
					String[] split = itemCategoryName.split(",");
					if (split.length == 3) {
						aos_category2 = split[1];
					}
				}

				// 获取销售组别并赋值
				// 2.获取产品小类
				String itemCategoryId = CommonDataSom.getItemCategoryId(item_id + "");
				if (itemCategoryId == null || "".equals(itemCategoryId)) {
					continue;
				}
				// 3.根据小类获取组别
				String salOrg = CommonDataSom.getSalOrgV2(p_org_id + "", itemCategoryId); // 小类获取组别
				if (salOrg == null || "".equals(salOrg)) {
					continue;
				}

				// 初始化插入参数

				// 剔除海外库存
				if ((int) item_overseaqty <= QtyStandard.intValue() && !aos_itemstatus.equals("E")
						&& !aos_itemstatus.equals("A") && (int) item_intransqty == 0) {
					continue;
				}


				// 剔除过季品
				if ((aos_seasonpro != null)
						&& (aos_seasonpro.equals("SPRING") || aos_seasonpro.equals("SPRING_SUMMER")
						|| aos_seasonpro.equals("SUMMER"))
						&& (Today.before(SummerSpringStart) || Today.after(SummerSpringEnd))) {
					MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "春夏品剔除");
					continue;
				}
				if ((aos_seasonpro != null)
						&& (aos_seasonpro.equals("AUTUMN_WINTER") || aos_seasonpro.equals("WINTER"))
						&& (Today.before(AutumnWinterStart) || Today.after(AutumnWinterEnd))) {
					MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "秋冬品剔除");
					continue;
				}


				// 非节日品剔除预断货 节日品节日2天内剔除 其它节日装饰 圣诞装饰 这两个中类不做预断货剔除
				if ((aos_festivalseting.equals("") || aos_festivalseting.equals("null"))
						&& !"其它节日装饰".equals(aos_category2) && !"圣诞装饰".equals(aos_category2)) {

					Object org_id_o = Long.toString(org_id);
					int aos_shp_day = (int) aos_shpday_map.get(org_id_o);// 备货天数
					int aos_freight_day = (int) aos_clearday_map.get(org_id_o);// 海运天数
					int aos_clear_day = (int) aos_freightday_map.get(org_id_o);// 清关天数
					String orgid_str = Long.toString(org_id);
					String itemid_str = Long.toString(item_id);
					float aos_7days_sale = InStockAvailableDays.getOrgItemOnlineAvgQty(orgid_str, itemid_str);
					int availableDays = InStockAvailableDays.calInstockSalDays(orgid_str, itemid_str);

					// 季节品 季节品 季末达标 CONTINUE; 剔除
					boolean isSeasonEnd = false;
					if ("SPRING".equals(aos_seasonpro) || "SPRING_SUMMER".equals(aos_seasonpro)
							|| "SUMMER".equals(aos_seasonpro)) {
						if (Today.after(summerSpringSeasonEnd)) {
							// 季末 判断是否达标
							isSeasonEnd = true;
							float SeasonRate = MKTCom.Get_SeasonRate(org_id, item_id, aos_seasonpro, item_overseaqty,
									month);
							if (!MKTCom.Is_SeasonRate(aos_seasonpro, month, SeasonRate)) {
								continue;
							}
						}
					}
					if ("AUTUMN_WINTER".equals(aos_seasonpro) || "WINTER".equals(aos_seasonpro)) {
						// 判断是否季末
						if (Today.after(autumnWinterSeasonEnd)) {
							isSeasonEnd = true;
							// 季末 判断是否达标
							float SeasonRate = MKTCom.Get_SeasonRate(org_id, item_id, aos_seasonpro, item_overseaqty,
									month);
							if (!MKTCom.Is_SeasonRate(aos_seasonpro, month, SeasonRate)) {
								continue;
							}
						}
					}

					// (海外在库+在途数量)/7日均销量)<60 或 满足销售预断货逻辑 则为营销预断货逻辑 且 不能为季节品季末
					if (((((int) item_overseaqty + (int) item_intransqty) / aos_7days_sale < 60)
							|| (MKTCom.Is_PreSaleOut(org_id, item_id, (int) item_intransqty, aos_shp_day,
									aos_freight_day, aos_clear_day, availableDays)))
							&& !isSeasonEnd) {
						continue;
					}

				}
				else {
					if ((aos_festivalseting.equals("HALLOWEEN"))
							&& (!(Today.after(HalloweenStart) && Today.before(HalloweenEnd)))) {
						continue;
					}
					if ((aos_festivalseting.equals("CHRISTMAS"))
							&& (!(Today.after(ChristmasStart) && Today.before(ChristmasEnd)))) {
						continue;
					}
				}
				Map<String, Object> SkuRptMap14 = SkuRpt14.get(org_id + "~" + item_id);// Sku报告
				BigDecimal Roi14Days = BigDecimal.ZERO;// 14天ROI
				BigDecimal aos_spend14SKU = BigDecimal.ZERO; // 过去14天的花费

				if (SkuRptMap14 != null
						&& ((BigDecimal) SkuRptMap14.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0) {
					Roi14Days = ((BigDecimal) SkuRptMap14.get("aos_total_sales"))
							.divide((BigDecimal) SkuRptMap14.get("aos_spend"), 2, BigDecimal.ROUND_HALF_UP);
					aos_spend14SKU = (BigDecimal) SkuRptMap14.get("aos_spend");
				}

				if (Roi14Days.compareTo(BigDecimal.valueOf(3)) >= 0)
					MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "14日ROI " + Roi14Days + " 大于等于3");

				if (!Cux_Common_Utl.IsNull(aos_festivalseting))
					MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "节日属性不为空" + aos_festivalseting);

				if ("AUTUMN_WINTER".equals(aos_seasonpro) || "WINTER".equals(aos_seasonpro))
					MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "秋冬春夏品");

				if (aos_spend14SKU.compareTo(BigDecimal.valueOf(0)) <= 0)
					MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "14日花费 " + aos_spend14SKU + " 小于等于0");

				if ("其它节日装饰".equals(aos_category2) || "圣诞装饰".equals(aos_category2))
					MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "产品类别" + aos_category2);

				if (aos_firstindate == null || MKTCom.GetBetweenDays(Today, aos_firstindate) < 30)
					MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "首次入库日期" + Today);

				if (ActAmazonItem.contains(aos_itemnumer))
					MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumer + "有做活动");

				/** 2022-09-30 临时启用逻辑 **/
				/** 14日ROI小于3,节日属性为空,且不为秋冬品,且中类不为 其它节日装饰 圣诞装饰 ，30天内入库新品。 **/
				if (Roi14Days.compareTo(BigDecimal.valueOf(3)) < 0 && Cux_Common_Utl.IsNull(aos_festivalseting)
						&& !"AUTUMN_WINTER".equals(aos_seasonpro) && !"WINTER".equals(aos_seasonpro)
						&& aos_spend14SKU.compareTo(BigDecimal.valueOf(0)) > 0 && !"其它节日装饰".equals(aos_category2)
						&& !"圣诞装饰".equals(aos_category2) && !ActAmazonItem.contains(aos_itemnumer)
						&& ((aos_firstindate != null) && MKTCom.GetBetweenDays(Today, aos_firstindate) >= 30)) {

					DynamicObject aos_mkt_popppc_cal = BusinessDataServiceHelper.newDynamicObject("aos_mkt_popppc_cal");

					aos_mkt_popppc_cal.set("aos_orgid", p_org_id);
					aos_mkt_popppc_cal.set("aos_productno", aos_productno + "-AUTO");
					aos_mkt_popppc_cal.set("aos_group", aos_itemnumer);

					OperationResult operationrstLog = OperationServiceHelper.executeOperate("save",
							"aos_mkt_popppc_cal", new DynamicObject[] { aos_mkt_popppc_cal }, OperateOption.create());
					if (operationrstLog.getValidateResult().getValidateErrors().size() != 0) {
					}
					continue;
				}
			}

			// 保存日志表
			OperationResult operationrstLog = OperationServiceHelper.executeOperate("save", "aos_sync_log",
					new DynamicObject[] { aos_sync_log }, OperateOption.create());
			if (operationrstLog.getValidateResult().getValidateErrors().size() != 0) {
			}
		}

		catch (Exception ex) {

		}

	}

	public static Set<String> GenActAmazonItem(String aos_orgid) {
		String[] channelArr = { "AMAZON" };
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

		DataSet dataSet = QueryServiceHelper.queryDataSet("aos_mkt_popppc_init.queryApartFromAmzAndEbayItem",
				"aos_act_select_plan", "aos_sal_actplanentity.aos_itemnum.number aos_itennum, 1 as count",
				new QFilter[] { new QFilter("aos_nationality", QCP.equals, aos_orgid),
						new QFilter("aos_channel.number", QCP.in, channelArr),
						new QFilter("aos_actstatus", QCP.equals, "B"), // 已提报
						new QFilter("aos_sal_actplanentity.aos_l_actstatus", QCP.equals, "A"), // 正常
						filter_date },
				null);
		DataSet finish = dataSet.groupBy(new String[] { "aos_itennum" }).sum("count").finish();
		Set<String> itemSet = new HashSet<>();
		while (finish.hasNext()) {
			Row next = finish.next();
			String aos_itennum = next.getString("aos_itennum");
			Integer count = next.getInteger("count");
			if (count > 0) {
				itemSet.add(aos_itennum);
			}
		}
		dataSet.close();
		finish.close();
		return itemSet;
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
		aos_sal_act_type_pS.close();
		aos_act_select_planS.close();
		return Act;
	}

	private static Set<String> getEliminateItem(String aos_orgid) {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_dataeliminate", "aos_itemid",
				new QFilter[] { new QFilter("aos_orgid", QCP.equals, aos_orgid) });

		return list.stream().map(obj -> obj.getString("aos_itemid")).collect(Collectors.toSet());
	}

}
