package mkt.common;

import common.CommonDataSomQuo;
import common.fnd.FndGlobal;
import common.sal.sys.sync.service.ItemCacheService;
import common.sal.sys.sync.service.impl.ItemCacheServiceImpl;
import kd.bos.algo.DataSet;
import kd.bos.algo.JoinDataSet;
import kd.bos.algo.Row;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.MainEntityType;
import kd.bos.entity.ValueMapItem;
import kd.bos.entity.property.ComboProp;
import kd.bos.notification.IconType;
import kd.bos.notification.NotificationBody;
import kd.bos.notification.NotificationFormInfo;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.MetadataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.notification.NotificationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class MKTCom {
	/**
	 * 单据保存
	 * 
	 * @param list_save
	 * @param end       为true 时表示最后一次保存不考虑长度，否则考虑长度
	 */
	public static void EntitySave(List<DynamicObject> list_save, boolean end) {
		if (end) {
			SaveServiceHelper.save(list_save.toArray(new DynamicObject[list_save.size()]));
			list_save.clear();
		} else {
			if (list_save.size() > 4500) {
				SaveServiceHelper.save(list_save.toArray(new DynamicObject[list_save.size()]));
				list_save.clear();
			}
		}
	}

	/**
	 * 单据修改
	 * 
	 * @param list_save
	 * @param end       为true 时表示最后一次保存不考虑长度，否则考虑长度
	 */
	public static void EntityUpdate(List<DynamicObject> list_save, boolean end) {
		if (end) {
			SaveServiceHelper.update(list_save.toArray(new DynamicObject[list_save.size()]));
			list_save.clear();
		} else {
			if (list_save.size() > 4500) {
				SaveServiceHelper.save(list_save.toArray(new DynamicObject[list_save.size()]));
				list_save.clear();
			}
		}
	}

	public static BigDecimal min(BigDecimal Value1, BigDecimal Value2) {
		if (Value1.compareTo(Value2) > -1)
			return Value2;
		else
			return Value1;
	}

	/** 获取权限用户 */
	public static List<String> getPrivilegedUser() {
		QFilter filter_status = new QFilter("aos_process", QCP.equals, true);
		return QueryServiceHelper.query("aos_mkt_userights", "aos_user", new QFilter[] { filter_status }).stream()
				.map(dy -> dy.getString("aos_user")).collect(Collectors.toList());
	}

	/**
	 * 获取 已提报的未来60天的活动数量
	 * 
	 * @param org_id  国别
	 * @param item_id 物料
	 * @return
	 */
	public static int Get_Act60PreQty(long org_id, long item_id) {
		// TODO 获取已提报的未来60天的活动数量
		int Act60PreQty = 0;
		try {
			Calendar calendar = Calendar.getInstance();
			Date date_from = calendar.getTime();
			calendar.set(Calendar.DATE, calendar.get(Calendar.DATE) + 60);
			Date date_to = calendar.getTime();
			SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
			String date_from_str = writeFormat.format(date_from);
			String date_to_str = writeFormat.format(date_to);
			QFilter filter_ou = new QFilter("aos_nationality", "=", org_id);
			QFilter filter_status = new QFilter("aos_actstatus", "=", "B");// 已提报
			QFilter filter_statusl = new QFilter("aos_sal_actplanentity.aos_l_actstatus", "=", "A");// 活动状态为正常
			QFilter filter_item = new QFilter("aos_sal_actplanentity.aos_itemnum", "=", item_id);
			QFilter filter_date = new QFilter("aos_sal_actplanentity.aos_enddate", ">=", date_from_str)
					.and("aos_sal_actplanentity.aos_enddate", "<=", date_to_str);
			filter_date = filter_date.or("aos_startdate", ">=", date_from_str).and("aos_startdate", "<=", date_to_str);
			QFilter[] filters = new QFilter[] { filter_ou, filter_item, filter_date, filter_status, filter_statusl };
			String SelectColumn = "aos_sal_actplanentity.aos_actqty aos_actqty,aos_acttype,aos_nationality,aos_channel,aos_shop";
			DataSet aos_act_select_planS = QueryServiceHelper.queryDataSet("Get_Act60PreQty." + org_id,
					"aos_act_select_plan", SelectColumn, filters, null);
			QFilter filter_type = new QFilter("aos_assessment.number", "=", "Y");
			filters = new QFilter[] { filter_type };
			DataSet aos_sal_act_type_pS = QueryServiceHelper.queryDataSet("aos_sal_act_type_p." + org_id,
					"aos_sal_act_type_p", "aos_org,aos_channel,aos_shop,aos_acttype", filters, null);
			JoinDataSet join = aos_act_select_planS.join(aos_sal_act_type_pS);
			String[] join_select = new String[] { "aos_actqty" };
			aos_act_select_planS = join.on("aos_nationality", "aos_org").on("aos_channel", "aos_channel")
					.on("aos_shop", "aos_shop").on("aos_acttype", "aos_acttype").select(join_select).finish();// 供应商再产品信息表中有多个
			aos_act_select_planS = aos_act_select_planS.groupBy(new String[] { "aos_actqty" }).sum("aos_actqty")
					.finish();
			// aos_act_select_planS.leftJoin(aos_sal_act_type_pS).on(arg0, arg1);
			while (aos_act_select_planS.hasNext()) {
				Row aos_act_select_plan = aos_act_select_planS.next();
				Act60PreQty = aos_act_select_plan.getInteger("aos_actqty");
			}
			aos_act_select_planS.close();
			aos_sal_act_type_pS.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		return Act60PreQty;

	}

	/**
	 *
	 * @param currentUserId 当前人员
	 * @param orgField      国别字段
	 * @param groupField    组别字段
	 * @return QFilter
	 */
	public static QFilter querySalespersonOrg(long currentUserId, String orgField, String groupField) {
		DynamicObjectCollection list = QueryServiceHelper.query("aos_sal_porggroup", "aos_org,aos_categorygroup",
				new QFilter[] { new QFilter("aos_personal", QCP.equals, currentUserId) });
		QFilter qFilter = null;
		for (DynamicObject obj : list) {
			String aos_categorygroup = obj.getString("aos_categorygroup");
			if (StringUtils.equals(aos_categorygroup, "0") || aos_categorygroup == null)
				continue;
			QFilter permissionFilter = new QFilter(orgField, QCP.equals, obj.getString("aos_org"))
					.and(new QFilter(groupField, QCP.equals, aos_categorygroup));
			if (qFilter == null) {
				qFilter = permissionFilter;
			} else {
				qFilter.or(permissionFilter);
			}
		}
		return qFilter;
	}

	/**
	 * 获取 活动数量占比
	 * 
	 * @param aos_itemstatus     物料状态
	 * @param aos_seasonpro      季节属性
	 * @param aos_festivalseting 节日属性
	 * @return
	 */
	public static BigDecimal Get_ActQtyRate(String aos_itemstatus, String aos_seasonpro, String aos_festivalseting) {
		try
		{
		// TODO 获取活动数量占比
		BigDecimal ActQtyRate = null;
		// 终止品80%节日品50%新品30%季节品30%正常产品30%
		if (aos_itemstatus.equals("C"))
			ActQtyRate = BigDecimal.valueOf(0.8);// 终止品
		else if (aos_festivalseting != null && !aos_festivalseting.equals("null") && !aos_festivalseting.equals("")
				&& !aos_festivalseting.equals("0"))
			ActQtyRate = BigDecimal.valueOf(0.5);// 节日品
		else if (aos_itemstatus.equals("E"))
			ActQtyRate = BigDecimal.valueOf(0.3);// 新品
		else if (aos_seasonpro.equals("AUTUMN_WINTER") || aos_seasonpro.equals("SPRING")
				|| aos_seasonpro.equals("SPRING_SUMMER") || aos_seasonpro.equals("SUMMER")
				|| aos_seasonpro.equals("WINTER"))
			ActQtyRate = BigDecimal.valueOf(0.3);// 季节品
		else if (aos_itemstatus.equals("B"))
			ActQtyRate = BigDecimal.valueOf(0.3);// 正常产品
		else
			ActQtyRate = BigDecimal.ONE;
		return ActQtyRate;
		}
		catch (Exception ex) {
			return null;
		}
	}

	public static float Get_SeasonRate(long org_id, long item_id, String aos_season, Object item_overseaqty,
			int month) {
		// TODO 季节品累计完成率
		String aos_seasonpro = null;
		if (aos_season.equals("AUTUMN_WINTER") || aos_season.equals("WINTER"))
			aos_seasonpro = "AUTUMN_WINTER_PRO";
		else if (aos_season.equals("SPRING") || aos_season.equals("SPRING_SUMMER") || aos_season.equals("SUMMER"))
			aos_seasonpro = "SPRING_SUMMER_PRO";
		float cumulativeCompletionRate = 0;
		String orgid_str = Long.toString(org_id);
		String itemid_str = Long.toString(item_id);
		int seasonTotalForecastSales = CommonDataSomQuo.calSeasonForecastNum(orgid_str, itemid_str, aos_seasonpro);// 季节总预测销量
		int earlySeasonToMakeDayForecastNum = CommonDataSomQuo.calEarlySeasonToMakeDayForecastNum(orgid_str, itemid_str,
				aos_seasonpro);// Σ季初至制表日预测量
		if (seasonTotalForecastSales == 0 || earlySeasonToMakeDayForecastNum == 0)
			return cumulativeCompletionRate;
		float seasonalRequirementsProgress = (float) earlySeasonToMakeDayForecastNum / seasonTotalForecastSales;// 季节要求进度
		int transit_num = CommonDataSomQuo.getTransitNum(orgid_str, itemid_str);// 在途数量
		int actualSales = CommonDataSomQuo.calSeasonalProductsActualSales(orgid_str, itemid_str, aos_seasonpro);// 实际销量
		int seasonalTotalSupplyQty = actualSales + transit_num + (int) item_overseaqty; // 季节总供应量
		if ((StringUtils.equals(aos_seasonpro, "SPRING_SUMMER_PRO") && month <= Calendar.JUNE)
				|| (StringUtils.equals(aos_seasonpro, "AUTUMN_WINTER_PRO") && month >= Calendar.AUGUST)) {
			// a. 春夏品： 销售开始时间为1月1日，6月1日以后国内在制+国内在库的数量不计算入总供应
			// b. 秋冬品： 销售开始时间为8月1日，1月1日以后国内在制+国内在库的数量不计算入总供应
			int in_process_qty = CommonDataSomQuo.get_in_process_qty(org_id, item_id);// 国内在制
			int domestic_qty = CommonDataSomQuo.get_domestic_qty(org_id, item_id);// 国内在库
			seasonalTotalSupplyQty = seasonalTotalSupplyQty + in_process_qty + domestic_qty;
		}
		if (seasonalTotalSupplyQty == 0)
			return cumulativeCompletionRate; // 如果实际销量或者季节总供应量为0 下一次
		// 5.2季节实际进度=季节实际总销量/季节总供应数量
		// 季节总供应数量 = 实际销量 + 在途数量 + 海外库存 + 国内在制 + 国内在库
		float seasonActualProgress = (float) actualSales / seasonalTotalSupplyQty;// 季节实际进度
		cumulativeCompletionRate = seasonActualProgress / seasonalRequirementsProgress;// 累计完成率
		return cumulativeCompletionRate;
	}

	public static boolean Is_SeasonRate(String aos_seasonpro, int month, float seasonRate) {
		// TODO 判断季节品累计完成率
		boolean Is_SeasonRate = false;

		// 对于秋冬产品
		if ((aos_seasonpro.equals("AUTUMN_WINTER") || aos_seasonpro.equals("WINTER"))
				&& (month == 8 || month == 9 || month == 10) && (seasonRate <= 0.6))
			Is_SeasonRate = true;
		else if ((aos_seasonpro.equals("AUTUMN_WINTER") || aos_seasonpro.equals("WINTER"))
				&& (month == 11 || month == 12 || month == 1) && (seasonRate <= 0.75))
			Is_SeasonRate = true;
		else if ((aos_seasonpro.equals("AUTUMN_WINTER") || aos_seasonpro.equals("WINTER")) && (month == 2 || month == 3)
				&& (seasonRate <= 0.9))
			Is_SeasonRate = true;

		// 对于春夏产品
		else if ((aos_seasonpro.equals("SPRING") || aos_seasonpro.equals("SPRING_SUMMER")
				|| aos_seasonpro.equals("SUMMER"))
				&& (month == 0 || month == 1 || month == 2 || month == 3 || month == 4) && (seasonRate <= 0.6))
			Is_SeasonRate = true;
		else if ((aos_seasonpro.equals("SPRING") || aos_seasonpro.equals("SPRING_SUMMER")
				|| aos_seasonpro.equals("SUMMER")) && (month == 5 || month == 6 || month == 7) && (seasonRate <= 0.75))
			Is_SeasonRate = true;
		else if ((aos_seasonpro.equals("SPRING") || aos_seasonpro.equals("SPRING_SUMMER")
				|| aos_seasonpro.equals("SUMMER")) && (month == 8 || month == 9) && (seasonRate <= 0.9))
			Is_SeasonRate = true;

		return Is_SeasonRate;
	}

	/**
	 * 判断预断货逻辑
	 * 
	 * @param org_id          国别
	 * @param item_id         物料
	 * @param item_intransqty
	 * @param aos_clear_day
	 * @param aos_freight_day
	 * @param aos_shp_day
	 * @return
	 */
	public static boolean Is_PreSaleOut(long org_id, long item_id, int item_intransqty, int aos_shp_day,
			int aos_freight_day, int aos_clear_day, int availableDays) {
		//首先判断物料是否为季节品，如果不是季节品，调用销售的判断逻辑，否则还用之前的逻辑
		ItemCacheService cacheService = new ItemCacheServiceImpl();
		String seasonProNum = cacheService.getSeasonProNum(org_id, item_id);
		//不为季节品
		if (FndGlobal.IsNull(seasonProNum)) {
			return cacheService.getIsPreStockOut(String.valueOf(org_id), String.valueOf(item_id));
		}
		// TODO 判断预断货逻辑
		boolean PreSaleOut = false;
		String orgid_str = Long.toString(org_id);
		String itemid_str = Long.toString(item_id);
		int safetyStockDays = Get_safetyStockDays(org_id);// 安全库存天数
		int intoTheWarehouseDays = Get_intoTheWarehouseDays(orgid_str, itemid_str);// 离进仓天数
		if ((item_intransqty == 0
				&& (availableDays - aos_shp_day - aos_clear_day - aos_freight_day - safetyStockDays) <= 0) // a) 海外在途 =
																											// 0：库存可售天数-备货天数-海运清关周期-安全库存天数(15天)≤0
				|| (item_intransqty > 0 && (availableDays - intoTheWarehouseDays - safetyStockDays) <= 0)) {
			PreSaleOut = true;
		}



		return PreSaleOut;
	}

	public static int Get_intoTheWarehouseDays(String orgid_str, String itemid_str) {
		int intoTheWarehouseDays = 0;
		try {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			Map<String, String> estimatedStorageDateAndTransitNum = CommonDataSomQuo.calEstimatedStorageDate(orgid_str,
					itemid_str);
			if (estimatedStorageDateAndTransitNum != null) {
				String est_storage_date = estimatedStorageDateAndTransitNum.get("est_storage_date");// 预计入库日期
				// 计算离进仓天数 预计入库日期-制单日期
				Calendar instance = Calendar.getInstance();
				instance.setTime(sdf.parse(sdf.format(instance.getTime())));
				long makeDateTimeInMillis = instance.getTimeInMillis();// 制单日期
				instance.setTime(sdf.parse(est_storage_date));
				long estimatedStorageDate = instance.getTimeInMillis();// 预计入库日期
				intoTheWarehouseDays = (int) ((estimatedStorageDate - makeDateTimeInMillis) / (24 * 60 * 60 * 1000));// 离进仓天数
			}
		} catch (Exception ex) {
		}
		return Math.max(intoTheWarehouseDays, 0);
	}

	/**
	 * 获取安全库存天数
	 * 
	 * @param org_id
	 * @return
	 */
	public static int Get_safetyStockDays(long org_id) {
		// TODO 获取安全库存天数
		int safetyStockDays = 0;
		QFilter filter_ou = new QFilter("aos_org", "=", org_id);
		QFilter[] filters = new QFilter[] { filter_ou };
		DynamicObject aos_sal_quo_m_coe = QueryServiceHelper.queryOne("aos_sal_quo_m_coe", "aos_value", filters);
		if (aos_sal_quo_m_coe != null) {
			safetyStockDays = aos_sal_quo_m_coe.getInt("aos_value");
		}
		return safetyStockDays;
	}

	/*
	 * 滞销逻辑
	 */
	public static Boolean Get_RegularUn(long org_id_l, long item_id_l, String aos_orgnumber, int availableDays, float R,
			int halfMonthTotalSales) {
		Boolean RegularUn = false;
		String orgid_str = Long.toString(org_id_l);
		// 获取对比参数
		float zeroSalesHalfMonthSalesStandard = CommonDataSomQuo.getOrgStandard(orgid_str, "零销量品(半月销量=)");// 零销量排半月销量 国别标准
		float lowSalesHalfMonthSalesStandard = CommonDataSomQuo.getOrgStandard(orgid_str, "低销量品(半月销量<)");// 低销量 半月销量 国别标准
		float lowTurnaroundAvailableDaysInStockStandard = CommonDataSomQuo.getOrgStandard(orgid_str, "低周转品(库存可售天数≥)");// 低销量库存可售天数
		if (halfMonthTotalSales == zeroSalesHalfMonthSalesStandard)
			RegularUn = false;// "零销量品"
		else if (halfMonthTotalSales > zeroSalesHalfMonthSalesStandard
				&& halfMonthTotalSales < lowSalesHalfMonthSalesStandard
				&& availableDays >= lowTurnaroundAvailableDaysInStockStandard)
			RegularUn = false;// 低销量低周转品
		else if (availableDays >= 120) {
			// 高销量低周转 判断调整为: 可售天数>=120且 US/UK 7天日均<6，其他国家7天日均<3
			if (("US".equals(aos_orgnumber) || "UK".equals(aos_orgnumber)))
				if (R < 6)
					RegularUn = false;
				else
					RegularUn = !(R < 3);
		} else
			RegularUn = true;// 其他情况下都跳过
		return RegularUn;
	}

	/**
	 *
	 * @param aos_orgnumber
	 * @param availableDays 可售天数
	 * @param R             7天日均
	 * @return
	 */
	public static String Get_RegularUn(String aos_orgnumber, int availableDays, float R) {
		String us_type = "";
		// 可售天数标准 可售天数(7天日均) > 90(法国60)
		boolean availbaleDayStand = ("FR".equals(aos_orgnumber) && availableDays > 60 && availableDays < 90)
				|| (!"FR".equals(aos_orgnumber) && availableDays > 90);

		// 不是爆品,7天日均小于等于0.42 且可售天数(7天日均) > 90(法国60) 则为 "低动销"
		if (R <= 0.42 && availbaleDayStand) {
			us_type = "低动销";
		}
		// 低周转:可售天数(7天日均)>=120天(法国90天), 且0.43<日均<6(US,UK)其他3
		else if ((("FR".equals(aos_orgnumber) && availableDays >= 90)
				|| (!"FR".equals(aos_orgnumber) && availableDays >= 120))) {
			if ("US".equals(aos_orgnumber) || "UK".equals(aos_orgnumber)) {
				if (R > 0.43 && R < 6) {
					us_type = "低周转";
				}
			} else {
				if (R < 3) {
					us_type = "低周转";
				}
			}
		}
		return us_type;
	}

	// 判断季节品阶段
	// 332
	public static String seasonalProStage(String seasonattr) {
		String result = "";
		Calendar instance = Calendar.getInstance();
		int month = instance.get(Calendar.MONTH);
		if ("AUTUMN_WINTER".equals(seasonattr) || "WINTER".equals(seasonattr)) {
			if (month == Calendar.AUGUST || month == Calendar.SEPTEMBER || month == Calendar.OCTOBER) {
				result = "季初";
			} else if (month == Calendar.DECEMBER || month == Calendar.NOVEMBER || month == Calendar.JANUARY) {
				result = "季中";
			} else if (month == Calendar.FEBRUARY || month == Calendar.MARCH) {
				result = "季末";
			}
		} else if ("SPRING".equals(seasonattr) || "SPRING_SUMMER".equals(seasonattr) || "SUMMER".equals(seasonattr)) {
			if (month == Calendar.JANUARY || month == Calendar.FEBRUARY || month == Calendar.MARCH) {
				result = "季初";
			} else if (month == Calendar.APRIL || month == Calendar.MAY || month == Calendar.JUNE) {
				result = "季中";
			} else if (month == Calendar.JULY || month == Calendar.AUGUST) {
				result = "季末";
			}
		}
		return result;
	}

	public static int calInstockSalDays(String itemid, float R, String seasonalProduct, String org_id,
			String category_id) {
		if (R == 0)
			return 999;
		// 非平台可用量
		int nonPlatQty = CommonDataSomQuo.getNonPlatQty(org_id, itemid);
		Map<Integer, Float> coefficientMap = null;
		if (coefficientMap == null) {
			// 如果产品月度系数还为空 则取常规产品月度系数
			coefficientMap = CommonDataSomQuo.getCategoryC(org_id, category_id);
		}
		if (coefficientMap == null) {
			// 如果产品月度系数还为空 则每个月的系数设为1
			coefficientMap = new HashMap<>();
			for (int i = 0; i < 12; i++) {
				coefficientMap.put(i, 1f);
			}
		}
		Calendar instance = Calendar.getInstance();
		instance.add(Calendar.DAY_OF_MONTH, +1);
		int currentMonth = instance.get(Calendar.MONTH);// 当前月份
		int currentYear = instance.get(Calendar.YEAR);// 当前月份
		BigDecimal currentMonthSeasonalCoefficient = BigDecimal.valueOf(coefficientMap.get(currentMonth));// 当月季节系数
		if (currentMonthSeasonalCoefficient.floatValue() == 0)
			return 999;

		BigDecimal x = BigDecimal.valueOf(R);// 日均销量
		BigDecimal inStockNumTemp = BigDecimal.valueOf(nonPlatQty);
		int count = 0;
		while (inStockNumTemp.floatValue() > 0) {// 剩余库存大于0 且要大于日均销量
			// 获取月份 月份的季度系数
			int month = instance.get(Calendar.MONTH);
			int year = instance.get(Calendar.YEAR);
			int day = instance.get(Calendar.DAY_OF_MONTH);
			if (("SPRING_SUMMER_PRO".equals(seasonalProduct)
					&& ((month == Calendar.AUGUST && day > 16) || month > Calendar.AUGUST))
					|| ("AUTUMN_WINTER_PRO".equals(seasonalProduct) && ((month == Calendar.MARCH && day > 16)
							|| (month > Calendar.MARCH && month < Calendar.AUGUST)))) {
				++count;
				instance.add(Calendar.DAY_OF_MONTH, +1);
				continue;
			}

			if (month != currentMonth || year != currentYear) {
				BigDecimal seasonalCoefficient = BigDecimal.valueOf(coefficientMap.get(month));
				x = BigDecimal.valueOf(R).multiply(seasonalCoefficient).divide(currentMonthSeasonalCoefficient, 10,
						BigDecimal.ROUND_HALF_UP);
			}
			// 日均销量
			inStockNumTemp = inStockNumTemp.subtract(x);
			++count;
			instance.add(Calendar.DAY_OF_MONTH, +1);
		}
		return count;
	}

	// 获取季节范围
	public static Date Get_DateRange(String context, String type, Object p_org_id) {
		Date date = null;
		QFilter filter_type = new QFilter("aos_type", "=", type);
		QFilter filter_org = new QFilter("aos_orgid", "=", p_org_id);
		QFilter[] filters = new QFilter[] { filter_type, filter_org };
		DynamicObject aos_mkt_base_season = QueryServiceHelper.queryOne("aos_mkt_base_date", context, filters);
		if (aos_mkt_base_season != null) {
			date = aos_mkt_base_season.getDate(context);
		}
		return date;
	}

	/**
	 * 获取国别标准
	 * 
	 * @param p_ou_code
	 * @return
	 */
	public static int GetStandardOrgQty(Object p_ou_code) {
		// TODO Auto-generated method stub
		int StandardOrgQty = 0;
		QFilter filter_org = new QFilter("aos_org.number", "=", p_ou_code);
		QFilter filter_type = new QFilter("aos_project.number", "=", "00000006");// 调价库存标准类型
		QFilter[] filters = new QFilter[] { filter_org, filter_type };
		String SelectField = "aos_value";
		DynamicObject aos_sal_quo_m_coe = QueryServiceHelper.queryOne("aos_sal_quo_m_coe", SelectField, filters);
		if (aos_sal_quo_m_coe != null) {
			StandardOrgQty = aos_sal_quo_m_coe.getBigDecimal("aos_value").intValue();
		}
		return StandardOrgQty;
	}

	/**
	 * 获取间隔日期
	 * 
	 * @param end_day
	 * @param from_day
	 * @return
	 */
	public static int GetBetweenDays(Date end_day, Date from_day) {
		int days = (int) ((end_day.getTime() - from_day.getTime()) / (1000 * 3600 * 24));
		return days;
	}

	public static DynamicObjectCollection Put_SyncLog(DynamicObjectCollection aos_sync_logS, String info) {
		DynamicObject aos_sync_log = aos_sync_logS.addNew();
		aos_sync_log.set("aos_content", info);
		return aos_sync_logS;
	}

	/**
	 * 发送全局消息通知
	 * 
	 * @param receiver   接收人
	 * @param entityName 实体标识
	 * @param billPkId   单据主键
	 * @param title      标题
	 * @param content    文本内容
	 */
	public static void SendGlobalMessage(String receiver, String entityName, String billPkId, String title,
			String content) {

		NotificationBody notificationBody = new NotificationBody();
		notificationBody.setAppId("aos");
		notificationBody.setTitle(title);
		notificationBody.setContent(content);
		notificationBody.setNotificationId(UUID.randomUUID().toString());
		notificationBody.setIconType(IconType.Info.toString());

		// 忽略按钮平台自动添加
		NotificationBody.ButtonInfo detailButton = new NotificationBody.ButtonInfo();
		detailButton.setKey("aos_detail");
		detailButton.setText("查看详情");
		notificationBody.addButtonInfo(detailButton);

		// 消息窗口按钮点击事件处理的类
		notificationBody.setClickClassName("mkt.common.GlobalMessage");

		// 自定义参数 (用于点击事件)
		Map<String, Object> params = new HashMap<>();
		params.put("name", entityName);
		params.put("id", billPkId);
		notificationBody.setParams(params);

		// 创建消息通知界面参数
		NotificationFormInfo notificationFormInfo = new NotificationFormInfo();
		notificationFormInfo.setNotification(notificationBody);

		// 调用发送全局消息帮助服务方法
		NotificationServiceHelper.sendNotification(Arrays.asList(String.valueOf(receiver)), notificationFormInfo);
	}

	/** 获取物料的中文分类名称 **/
	public static String getItemCateNameZH(Object sku_id) {
		QFilter filter_sku = new QFilter("material.id", QCP.equals, sku_id);
		QFilter filter_stand = new QFilter("standard.number", QCP.equals, "JBFLBZ");
		QFilter qFilter_group = new QFilter("group.number", "!=", "waitgroup");
		DynamicObject[] itemCate = BusinessDataServiceHelper.load("bd_materialgroupdetail", "group",
				new QFilter[] { filter_sku, filter_stand, qFilter_group }, null, 1);
		if (itemCate.length > 0) {
			String cateName = itemCate[0].getDynamicObject("group").getLocaleString("name").getLocaleValue_zh_CN();
			String[] split = cateName.split(",");
			if (split.length >= 3) {
				if (!split[1].equals("组合货号"))
					return cateName;
			}
		}

		// 获取关联的sku
		QFilter filter_item = new QFilter("id", "=", sku_id);
		QFilter filter_cate = new QFilter("aos_contryentry.aos_submaterialentry.aos_submaterial", "!=", "");
		StringJoiner str = new StringJoiner(",");
		str.add("number");
		str.add("aos_contryentry.aos_submaterialentry.aos_submaterial aos_submaterial");
		List<String> list_otherSku = QueryServiceHelper
				.query("bd_material", str.toString(), new QFilter[] { filter_cate, filter_item }).stream()
				.map(dy -> dy.getString("aos_submaterial")).distinct().collect(Collectors.toList());
		filter_sku = new QFilter("material.id", QCP.in, list_otherSku);
		itemCate = BusinessDataServiceHelper.load("bd_materialgroupdetail", "group",
				new QFilter[] { filter_sku, filter_stand, qFilter_group }, null, 1);
		if (itemCate.length > 0) {
			for (DynamicObject dy : itemCate) {
				String cateName = dy.getDynamicObject("group").getLocaleString("name").getLocaleValue_zh_CN();
				String[] split = cateName.split(",");
				if (split.length >= 3) {
					if (!split[1].equals("组合货号"))
						return cateName;
				}
			}
		}
		return "";
	}
	/**
	 *
	 * @param billName 单据标识
	 * @param comboName 下拉框标识
	 * @return K 下拉值 V下拉标题
	 */
	public static Map<String, String> getComboMap(String billName, String comboName){
		MainEntityType type = MetadataServiceHelper.getDataEntityType(billName);    //界面标识
		ComboProp combo = (ComboProp) type.findProperty(comboName);    //下拉框标识
		List<ValueMapItem> items = combo.getComboItems();
		Map<String, String> result = new HashMap<>();
		for (int i=0;i<items.size();i++){
			String title = items.get(i).getName().getLocaleValue();
			String value = items.get(i).getValue();
			result.put(value, title);
		}
		return result;
	}
}
