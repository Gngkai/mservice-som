package mkt.act.select;

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
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.threads.ThreadPools;
import mkt.common.AosMktGenerate;
import mkt.common.MKTCom;
import mkt.common.aos_mkt_common_redis;
import org.apache.commons.lang3.SerializationUtils;
import sal.quote.CommData;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public class aos_mkt_actselect_init extends AbstractTask {

	private static final DistributeSessionlessCache cache = CacheFactory.getCommonCacheFactory()
			.getDistributeSessionlessCache("mkt_redis");

	private static Log logger = LogFactory.getLog(aos_mkt_actselect_init.class);

	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		// 多线程执行
		executerun();
	}

	public static void ManualitemClick() {
		executerun();
	}

	private static void executerun() {
		// 初始化数据
		CommData.init();
		aos_mkt_common_redis.init_redis("act");
		// 调用线程池
//		long is_oversea_flag = aos_sal_sche_pub.get_lookup_values("AOS_YES_NO", "Y");
		QFilter oversea_flag = new QFilter("aos_isomvalid", "=", "1").or("number", QCP.equals, "IE");// 是否销售国别为是
		QFilter[] filters_ou = new QFilter[] { oversea_flag };
		DynamicObjectCollection bd_country = QueryServiceHelper.query("bd_country", "id,number", filters_ou);
		for (DynamicObject ou : bd_country) {
			String p_ou_code = ou.getString("number");
			Map<String, Object> params = new HashMap<>();
			params.put("p_ou_code", p_ou_code);
			MktActSelectRunnable mktActSelectRunnable = new MktActSelectRunnable(params);
			ThreadPools.executeOnce("MKT_活动选品初始化_" + p_ou_code, mktActSelectRunnable);
		}
	}

	static class MktActSelectRunnable implements Runnable {

		private Map<String, Object> params = new HashMap<>();

		public MktActSelectRunnable(Map<String, Object> param) {
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
				logger.error(messageStr);
				e.printStackTrace();
			}
		}
	}

	public static void do_operate(Map<String, Object> params) {
		byte[] serialize_item = cache.getByteValue("item");
		HashMap<String, Map<String, Object>> item = SerializationUtils.deserialize(serialize_item);
		Map<String, Object> item_maxage_map = item.get("item_maxage");
		Map<String, Object> item_overseaqty_map = item.get("item_overseaqty");
		Map<String, Object> item_intransqty_map = item.get("item_intransqty");
		byte[] serialize_datepara = cache.getByteValue("datepara");
		HashMap<String, Map<String, Object>> datepara = SerializationUtils.deserialize(serialize_datepara);
		Map<String, Object> aos_shpday_map = datepara.get("aos_shp_day");
		Map<String, Object> aos_clearday_map = datepara.get("aos_clear_day");
		Map<String, Object> aos_freightday_map = datepara.get("aos_freight_day");
		Calendar date = Calendar.getInstance();
		int year = date.get(Calendar.YEAR);// 当前年
		int month = date.get(Calendar.MONTH) + 1;// 当前月
		int day = date.get(Calendar.DAY_OF_MONTH);// 当前日
		Object p_ou_code = params.get("p_ou_code");
		// 获取当前国别下所有活动物料已提报次数
		Map<String, Integer> alreadyActivityTimes = getAlreadyActivityTimes(p_ou_code);
		HashMap<String, Integer> Order7Days = AosMktGenerate.GenerateOrder7Days(p_ou_code);// 7天销量
		QFilter filter_ou = new QFilter("aos_contryentry.aos_nationality.number", "=", p_ou_code);
		QFilter filter_itemtype = new QFilter("aos_protype", "=", "N");
		QFilter[] filters = new QFilter[] { filter_ou, filter_itemtype };
		String SelectField = "id," + "number," + "aos_cn_name," + "aos_contryentry.aos_nationality.id aos_orgid,"
				+ "aos_contryentry.aos_nationality.number aos_orgnumber,"
				+ "aos_contryentry.aos_seasonseting.number aos_seasonseting,"
				+ "aos_contryentry.aos_contryentrystatus aos_contryentrystatus,"
				+ "aos_contryentry.aos_festivalseting aos_festivalseting,"
				+ "aos_contryentry.aos_is_saleout aos_is_saleout";

		DynamicObjectCollection bd_materialS = QueryServiceHelper.query("bd_material", SelectField, filters);
		int rows = bd_materialS.size();
		int count = 0;
		System.out.println("rows =" + rows);
		// 清除同维度
		QFilter filter_type = new QFilter("aos_type_code", "=", "MKT_活动选品初始化");
		QFilter filter_group = new QFilter("aos_groupid", "=", p_ou_code.toString() + year + month + day);
		filters = new QFilter[] { filter_type, filter_group };
		DeleteServiceHelper.delete("aos_sync_log", filters);
		QFilter filter_bill = new QFilter("billno", "=", p_ou_code.toString() + year + month + day);
		filters = new QFilter[] { filter_bill };
		DeleteServiceHelper.delete("aos_mkt_actselect", filters);
		// 初始化保存对象
		DynamicObject aos_mkt_actselect = BusinessDataServiceHelper.newDynamicObject("aos_mkt_actselect");
		aos_mkt_actselect.set("billno", p_ou_code.toString() + year + month + day);
		aos_mkt_actselect.set("billstatus", "A");
		DynamicObjectCollection aos_entryentityS = aos_mkt_actselect.getDynamicObjectCollection("aos_entryentity");
		DynamicObject aos_sync_log = BusinessDataServiceHelper.newDynamicObject("aos_sync_log");
		aos_sync_log.set("aos_type_code", "MKT_活动选品初始化");
		aos_sync_log.set("aos_groupid", p_ou_code.toString() + year + month + day);
		aos_sync_log.set("billstatus", "A");
		DynamicObjectCollection aos_sync_logS = aos_sync_log.getDynamicObjectCollection("aos_entryentity");
		List<String> list_unSaleItemid = getNewestUnsaleDy(String.valueOf(p_ou_code));
		for (DynamicObject bd_material : bd_materialS) {
			count++;
			System.out.println(p_ou_code + "进度" + "(" + count + "/" + rows + ")");
			// 判断是否跳过

			long l1 = System.currentTimeMillis();
			long item_id = bd_material.getLong("id");
			long org_id = bd_material.getLong("aos_orgid");
			String aos_itemnumber = bd_material.getString("number");
			String aos_seasonpro = bd_material.getString("aos_seasonseting");
			String aos_itemstatus = bd_material.getString("aos_contryentrystatus");
			String aos_festivalseting = bd_material.getString("aos_festivalseting");
			String aos_orgnumber = bd_material.getString("aos_orgnumber");
			String aos_itemtype = null;
			boolean saleout = bd_material.getBoolean("aos_is_saleout"); // 是否爆品

			// 产品状态 季节属性
			if (aos_itemstatus == null || aos_itemstatus.equals("null") || aos_seasonpro == null
					|| aos_seasonpro.equals("null")) {
				aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "季节属性为空");
				continue;
			}
			// 最大库龄≥15天
			boolean flag1 = false;// 是否剔除
			Object item_maxage = item_maxage_map.get(org_id + "~" + item_id);
			if (item_maxage == null || item_maxage.equals("null") || (int) item_maxage < 15) {
				flag1 = true;
			}
			// 海外库存>30
			boolean flag2 = false;// 是否剔除
			Object item_overseaqty = item_overseaqty_map.get(org_id + "~" + item_id);
			if (item_overseaqty == null || item_overseaqty.equals("null")) {
				item_overseaqty = 0;
			}
			if (item_overseaqty == null || item_overseaqty.equals("null") || (int) item_overseaqty <= 30) {
				flag2 = true;
			}
			// 平台仓库数量可售天数
			boolean flag3 = false;// 是否剔除
			int availableDaysByPlatQty = InStockAvailableDays.calAvailableDaysByPlatQty(String.valueOf(org_id),
					String.valueOf(item_id));
			if (availableDaysByPlatQty < 120) {
				flag3 = true;
			}
			// (最大库龄 < 15 || 海外库存 <= 30) && 平台仓可售天数 < 120
			if ((flag1 || flag2) && flag3) {
				if (flag1) {
					MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "最大库龄<15天");
				}
				if (flag2) {
					MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "海外库存<30");
				}
				MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "平台仓数量可售天数:" + availableDaysByPlatQty + " < 120");
				continue;
			}

			// 可提报活动数量 > 5 (当前可售库存数量+在途)*活动数量占比-已提报的未来60天的活动数量
			Object item_intransqty = item_intransqty_map.get(org_id + "~" + item_id);
			if (item_intransqty == null || item_intransqty.equals("null"))
				item_intransqty = 0;
			// 活动数量占比
			BigDecimal ActQtyRate = MKTCom.Get_ActQtyRate(aos_itemstatus, aos_seasonpro, aos_festivalseting);
			if (ActQtyRate == null) {
				aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "活动数量占比为空");
				continue;
			}
			// 已提报未来60天活动数量
			int Act60PreQty = MKTCom.Get_Act60PreQty(org_id, item_id);
			BigDecimal AvaQty = new BigDecimal((int) item_overseaqty + (int) item_intransqty);
			// 可提报活动数量
			aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + ActQtyRate + "活动数量占比");
			aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + item_overseaqty + "海外库存");
			aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + item_intransqty + "在途数量");
			aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + Act60PreQty + "已提报未来60天活动数量");
			int aos_qty = ActQtyRate.multiply(AvaQty).intValue() - Act60PreQty;
			aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + aos_qty + "可提报活动数量");
			if (aos_qty <= 5) {
				aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "可提报活动数量<=5");
				// continue;
			}

			// 日均销量与可售库存天数参数申明
			String aos_seasonprostr = null;
			if (aos_seasonpro.equals("AUTUMN_WINTER") || aos_seasonpro.equals("WINTER"))
				aos_seasonprostr = "AUTUMN_WINTER_PRO";
			else if (aos_seasonpro.equals("SPRING") || aos_seasonpro.equals("SPRING_SUMMER")
					|| aos_seasonpro.equals("SUMMER"))
				aos_seasonprostr = "SPRING_SUMMER_PRO";

			// 4. 如果是春夏品:当前日期大于8/31直接剔除,如果为秋冬品，当前日期大于3月31日小于7月1日直接剔除
			if ("SPRING_SUMMER_PRO".equals(aos_seasonprostr)) {
				if (month - 1 >= Calendar.SEPTEMBER) {
					continue;
				}
			} else if ("AUTUMN_WINTER_PRO".equals(aos_seasonprostr)) {
				if (month - 1 >= Calendar.APRIL && month - 1 < Calendar.JULY) {
					continue;
				}
			}

			long l2 = System.currentTimeMillis();
			System.out.println("耗时:" + (l2 - l1) + "ms");
			String orgid_str = Long.toString(org_id);
			String itemid_str = Long.toString(item_id);

			// 库存可售天数 非平台仓库用量
			int availableDays = InStockAvailableDays.calInstockSalDays(orgid_str, itemid_str);
			// 7天日均销量dd
			float R = InStockAvailableDays.getOrgItemOnlineAvgQty(orgid_str, itemid_str);

			// 7天销量
			int day7Sales = (int) Cux_Common_Utl.nvl(Order7Days.get(String.valueOf(item_id)));
			String aos_typedetail = "";// 滞销类型 低动销 低周转

			// 针对爆品 季节 节日 常规 进行筛选 不满足条件的直接跳过
			float SeasonRate = 0;
			Boolean issaleout = false;
			if (saleout) {
				// 爆品中的常规品
				if (aos_seasonpro.equals("REGULAR") || aos_seasonpro.equals("SPRING-SUMMER-CONVENTIONAL")) {
					if (availableDays <= 90)
						issaleout = false;
				}
				// 爆品中的季节品
				if (aos_seasonpro.equals("AUTUMN_WINTER") || aos_seasonpro.equals("WINTER")
						|| aos_seasonpro.equals("SPRING") || aos_seasonpro.equals("SPRING_SUMMER")
						|| aos_seasonpro.equals("SUMMER")) {
					// 判断季节品累计完成率是否满足条件
					SeasonRate = MKTCom.Get_SeasonRate(org_id, item_id, aos_seasonpro, item_overseaqty, month);
					if (SeasonRate == 0) {
						aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "季节品累计完成率为空");
						issaleout = false;
					}
					if (!MKTCom.Is_SeasonRate(aos_seasonpro, month, SeasonRate)) {
						aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "季节品累计完成率不满足条件");
						issaleout = false;
					}
				}
				issaleout = true;
				aos_itemtype = "G";
				aos_typedetail = "爆品";
			}

			// 1.0季节品 累计完成率

			if (!issaleout) {
				if (aos_seasonpro.equals("AUTUMN_WINTER") || aos_seasonpro.equals("WINTER")
						|| aos_seasonpro.equals("SPRING") || aos_seasonpro.equals("SPRING_SUMMER")
						|| aos_seasonpro.equals("SUMMER")) {
					// 判断季节品累计完成率是否满足条件
					SeasonRate = MKTCom.Get_SeasonRate(org_id, item_id, aos_seasonpro, item_overseaqty, month);
					if (SeasonRate == 0) {
						aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "季节品累计完成率为空");
						continue;
					}
					if (MKTCom.Is_SeasonRate(aos_seasonpro, month, SeasonRate))
						aos_itemtype = "S";
					else {
						aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "季节品累计完成率不满足条件");
						continue;
					}
					// 季节品预断货 跳过
					// 海运备货清关
					Object org_id_o = Long.toString(org_id);
					int aos_shp_day = (int) aos_shpday_map.get(org_id_o);// 备货天数
					int aos_freight_day = (int) aos_clearday_map.get(org_id_o);// 海运天数
					int aos_clear_day = (int) aos_freightday_map.get(org_id_o);// 清关天数
					if (MKTCom.Is_PreSaleOut(org_id, item_id, (int) item_intransqty, aos_shp_day, aos_freight_day,
							aos_clear_day, availableDays)) {
						aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "季节品未预断货");
						continue;
					}

					// 判断季节类型
					aos_typedetail = MKTCom.seasonalProStage(aos_seasonpro);
				}
				// 2.0常规品 滞销
				else if (aos_seasonpro.equals("REGULAR") || aos_seasonpro.equals("SPRING-SUMMER-CONVENTIONAL")) {
					aos_typedetail = MKTCom.Get_RegularUn(aos_orgnumber, availableDays, R);
					if ("".equals(aos_typedetail)) {// 如果为空则表示不为滞销品
						aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "常规品未滞销");
						continue;
					}
					aos_itemtype = "D";

					if ("低周转".equals(aos_typedetail)) {
						if ("US".equals(aos_orgnumber) || "UK".equals(aos_orgnumber)) {
							if (R > 3) {
								aos_typedetail = "低周转(日均>标准)";
							} else {
								aos_typedetail = "低周转(日均<=标准)";
							}
						} else {
							if (R > 1.5) {
								aos_typedetail = "低周转(日均>标准)";
							} else {
								aos_typedetail = "低周转(日均<=标准)";
							}
						}
					}
				}
				// 3.0其他情况都跳过
				else {
					aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "其他情况都跳过");
					continue;
				}
			}

			// 获取数据
			String aos_sku = bd_material.getString("number");
			String aos_itemname = bd_material.getString("aos_cn_name");
			String category = (String) SalUtil.getCategoryByItemId(item_id + "").get("name");
			String[] category_group = category.split(",");
			String aos_category1 = "";
			String aos_category2 = "";
			// 处理数据
			int category_length = category_group.length;
			if (category_length > 0)
				aos_category1 = category_group[0];
			if (category_length > 1)
				aos_category2 = category_group[1];

			if (aos_category2.equals("圣诞装饰") || aos_category2.equals("其它节日装饰"))
				continue;
			switch (aos_itemstatus) {
			case "A":
				aos_itemstatus = "新品首单";
				break;
			case "B":
				aos_itemstatus = "正常";
				break;
			case "C":
				aos_itemstatus = "终止";
				break;
			case "D":
				aos_itemstatus = "异常";
				break;
			case "E":
				aos_itemstatus = "入库新品";
				break;
			}

			// 赋值数据
			DynamicObject aos_entryentity = aos_entryentityS.addNew();
			if (list_unSaleItemid.contains(String.valueOf(item_id))) {
				aos_entryentity.set("aos_weekunsale", "Y");
			}
			aos_entryentity.set("aos_orgid", aos_orgnumber);
			aos_entryentity.set("aos_sku", aos_sku);
			aos_entryentity.set("aos_itemname", aos_itemname);
			aos_entryentity.set("aos_seasonpro", aos_seasonpro);
			aos_entryentity.set("aos_itemstatus", aos_itemstatus);
			aos_entryentity.set("aos_category1", aos_category1);
			aos_entryentity.set("aos_category2", aos_category2);
			aos_entryentity.set("aos_overseaqty", item_overseaqty);
			aos_entryentity.set("aos_qty", aos_qty);
			aos_entryentity.set("aos_typedetail", aos_typedetail);// 类型细分
			aos_entryentity.set("aos_itemtype", aos_itemtype);
			aos_entryentity.set("aos_salesqty", BigDecimal.valueOf(R));
			aos_entryentity.set("aos_last7sales", day7Sales);
			aos_entryentity.set("aos_avadays", availableDays);// 非平台仓库可售天数
			aos_entryentity.set("aos_seasonrate", SeasonRate);
			aos_entryentity.set("aos_times", alreadyActivityTimes.getOrDefault(String.valueOf(item_id), 0));
			aos_entryentity.set("aos_platfqty",
					InStockAvailableDays.getPlatQty(String.valueOf(org_id), String.valueOf(item_id)));
			aos_entryentity.set("aos_platdays", availableDaysByPlatQty);// 平台仓库可售天数
			aos_entryentity.set("aos_itemmaxage", item_maxage);// 平台仓库可售天数
			aos_entryentity.set("aos_platavgqty",
					InStockAvailableDays.getPlatAvgQty(String.valueOf(org_id), String.valueOf(item_id)));// 平台仓库可售天数
			aos_entryentity.set("aos_is_saleout", saleout); // 是否爆品

			long l3 = System.currentTimeMillis();
			System.out.println("耗时:" + (l3 - l2) + "ms");
		}

		// 并上营销国别滞销货号中未预断货部分
		DynamicObjectCollection aos_base_stitemS = QueryServiceHelper.query("aos_base_stitem", "aos_itemid,aos_orgid",
				new QFilter("aos_orgid.number", QCP.equals, p_ou_code).toArray());
		for (DynamicObject aos_base_stitem : aos_base_stitemS) {
			long aos_itemid = aos_base_stitem.getLong("aos_itemid");
			
			DynamicObject bd_material = QueryServiceHelper.queryOne("bd_material",
					"id," + "number," + "aos_cn_name," + "aos_contryentry.aos_nationality.id aos_orgid,"
							+ "aos_contryentry.aos_nationality.number aos_orgnumber,"
							+ "aos_contryentry.aos_seasonseting.number aos_seasonseting,"
							+ "aos_contryentry.aos_contryentrystatus aos_contryentrystatus,"
							+ "aos_contryentry.aos_festivalseting aos_festivalseting,"
							+ "aos_contryentry.aos_is_saleout aos_is_saleout",
					new QFilter("id", "=", aos_itemid).toArray());

			long item_id = bd_material.getLong("id");
			long org_id = bd_material.getLong("aos_orgid");
			String aos_itemnumber = bd_material.getString("number");
			String aos_seasonpro = bd_material.getString("aos_seasonseting");
			String aos_itemstatus = bd_material.getString("aos_contryentrystatus");
			String aos_festivalseting = bd_material.getString("aos_festivalseting");
			String aos_orgnumber = bd_material.getString("aos_orgnumber");
			String aos_itemtype = null;
			boolean saleout = bd_material.getBoolean("aos_is_saleout"); // 是否爆品

			// 产品状态 季节属性
			if (aos_itemstatus == null || aos_itemstatus.equals("null") || aos_seasonpro == null
					|| aos_seasonpro.equals("null")) {
				aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "季节属性为空");
				continue;
			}
			// 最大库龄≥15天
			Object item_maxage = item_maxage_map.get(org_id + "~" + item_id);
			// 海外库存>30
			Object item_overseaqty = item_overseaqty_map.get(org_id + "~" + item_id);
			if (item_overseaqty == null || item_overseaqty.equals("null")) {
				item_overseaqty = 0;
			}
			// 平台仓库数量可售天数
			int availableDaysByPlatQty = InStockAvailableDays.calAvailableDaysByPlatQty(String.valueOf(org_id),
					String.valueOf(item_id));
			// 可提报活动数量 > 5 (当前可售库存数量+在途)*活动数量占比-已提报的未来60天的活动数量
			Object item_intransqty = item_intransqty_map.get(org_id + "~" + item_id);
			if (item_intransqty == null || item_intransqty.equals("null"))
				item_intransqty = 0;
			// 活动数量占比
			BigDecimal ActQtyRate = MKTCom.Get_ActQtyRate(aos_itemstatus, aos_seasonpro, aos_festivalseting);
			if (ActQtyRate == null) {
				aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "活动数量占比为空");
				continue;
			}
			// 已提报未来60天活动数量
			int Act60PreQty = MKTCom.Get_Act60PreQty(org_id, item_id);
			BigDecimal AvaQty = new BigDecimal((int) item_overseaqty + (int) item_intransqty);
			// 可提报活动数量
			aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + ActQtyRate + "活动数量占比");
			aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + item_overseaqty + "海外库存");
			aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + item_intransqty + "在途数量");
			aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + Act60PreQty + "已提报未来60天活动数量");
			int aos_qty = ActQtyRate.multiply(AvaQty).intValue() - Act60PreQty;
			aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + aos_qty + "可提报活动数量");
			if (aos_qty <= 5) {
				aos_sync_logS = MKTCom.Put_SyncLog(aos_sync_logS, aos_itemnumber + "可提报活动数量<=5");
				// continue;
			}

			String orgid_str = Long.toString(org_id);
			String itemid_str = Long.toString(item_id);

			// 库存可售天数 非平台仓库用量
			int availableDays = InStockAvailableDays.calInstockSalDays(orgid_str, itemid_str);
			// 7天日均销量dd
			float R = InStockAvailableDays.getOrgItemOnlineAvgQty(orgid_str, itemid_str);
			// 7天销量
			int day7Sales = (int) Cux_Common_Utl.nvl(Order7Days.get(String.valueOf(item_id)));

			// 获取数据
			String aos_itemname = bd_material.getString("aos_cn_name");
			String category = (String) SalUtil.getCategoryByItemId(item_id + "").get("name");
			String[] category_group = category.split(",");
			String aos_category1 = "";
			String aos_category2 = "";
			// 处理数据
			int category_length = category_group.length;
			if (category_length > 0)
				aos_category1 = category_group[0];
			if (category_length > 1)
				aos_category2 = category_group[1];

			if (aos_category2.equals("圣诞装饰") || aos_category2.equals("其它节日装饰"))
				continue;
			switch (aos_itemstatus) {
			case "A":
				aos_itemstatus = "新品首单";
				break;
			case "B":
				aos_itemstatus = "正常";
				break;
			case "C":
				aos_itemstatus = "终止";
				break;
			case "D":
				aos_itemstatus = "异常";
				break;
			case "E":
				aos_itemstatus = "入库新品";
				break;
			}

			Object org_id_o = Long.toString(org_id);
			int aos_shp_day = (int) aos_shpday_map.get(org_id_o);// 备货天数
			int aos_freight_day = (int) aos_clearday_map.get(org_id_o);// 海运天数
			int aos_clear_day = (int) aos_freightday_map.get(org_id_o);// 清关天数
			if (MKTCom.Is_PreSaleOut(org_id, item_id, (int) item_intransqty, aos_shp_day, aos_freight_day,
					aos_clear_day, availableDays)) {
				continue;
			}

			DynamicObject aos_entryentity = aos_entryentityS.addNew();
			if (list_unSaleItemid.contains(String.valueOf(aos_itemid))) {
				aos_entryentity.set("aos_weekunsale", "Y");
			}
			aos_entryentity.set("aos_orgid", aos_orgnumber);
			aos_entryentity.set("aos_sku", bd_material.getPkValue());
			aos_entryentity.set("aos_itemname", aos_itemname);
			aos_entryentity.set("aos_seasonpro", aos_seasonpro);
			aos_entryentity.set("aos_itemstatus", aos_itemstatus);
			aos_entryentity.set("aos_category1", aos_category1);
			aos_entryentity.set("aos_category2", aos_category2);
			aos_entryentity.set("aos_overseaqty", item_overseaqty);
			aos_entryentity.set("aos_qty", aos_qty);
			aos_entryentity.set("aos_typedetail", "常规滞销品(月更新)");// 类型细分
			aos_entryentity.set("aos_itemtype", aos_itemtype);
			aos_entryentity.set("aos_salesqty", BigDecimal.valueOf(R));
			aos_entryentity.set("aos_last7sales", day7Sales);
			aos_entryentity.set("aos_avadays", availableDays);// 非平台仓库可售天数
			aos_entryentity.set("aos_seasonrate", 0);
			aos_entryentity.set("aos_times", alreadyActivityTimes.getOrDefault(String.valueOf(item_id), 0));
			aos_entryentity.set("aos_platfqty",
					InStockAvailableDays.getPlatQty(String.valueOf(org_id), String.valueOf(item_id)));
			aos_entryentity.set("aos_platdays", availableDaysByPlatQty);// 平台仓库可售天数
			aos_entryentity.set("aos_itemmaxage", item_maxage);// 平台仓库可售天数
			aos_entryentity.set("aos_platavgqty",
					InStockAvailableDays.getPlatAvgQty(String.valueOf(org_id), String.valueOf(item_id)));// 平台仓库可售天数
			aos_entryentity.set("aos_is_saleout", saleout); // 是否爆品
		}

		// 保存正式表
		OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_actselect",
				new DynamicObject[] { aos_mkt_actselect }, OperateOption.create());
		if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
		}

		// 保存日志表
		OperationResult operationrstLog = OperationServiceHelper.executeOperate("save", "aos_sync_log",
				new DynamicObject[] { aos_sync_log }, OperateOption.create());
		if (operationrstLog.getValidateResult().getValidateErrors().size() != 0) {
		}
	}

	/**
	 * 获取已提报活动次数 查询活动计划和选品表 （1） 单据状态为已提报 （2） 明细行状态为正常 （3） 开始日期或结束日期在未来60天内 （4）
	 * 按国别物料分组计算次数
	 * 
	 * @param ouCode 国别编码
	 * @Return 该国别下的物料及其次数
	 */
	private static Map<String, Integer> getAlreadyActivityTimes(Object ouCode) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		Calendar instance = Calendar.getInstance();
		instance.set(Calendar.HOUR_OF_DAY, 0);
		instance.set(Calendar.MINUTE, 0);
		instance.set(Calendar.SECOND, 0);
		instance.set(Calendar.MILLISECOND, 0);
		String start = sdf.format(instance.getTime());
		instance.add(Calendar.DAY_OF_MONTH, +60);
		String end = sdf.format(instance.getTime());

		// 开始日期或结束日期在未来60天内
		QFilter qFilter = new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.large_equals, start)
				.and(new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.less_equals, end));
		qFilter.or(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.large_equals, start)
				.and(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.less_equals, end)));
		qFilter.or(new QFilter("aos_sal_actplanentity.aos_l_startdate", QCP.less_equals, start)
				.and(new QFilter("aos_sal_actplanentity.aos_enddate", QCP.large_equals, end)));
		// 字段
		String selectFields = "aos_sal_actplanentity.aos_itemnum aos_itemid";
		DataSet dataSet = QueryServiceHelper.queryDataSet("aos_mkt_actselect_init_getAlreadyActivityTimes",
				"aos_act_select_plan", selectFields,
				new QFilter[] { new QFilter("aos_nationality.number", QCP.equals, ouCode), // 国别
						new QFilter("aos_actstatus", QCP.equals, "B"), // 单据状态为已提报
						new QFilter("aos_sal_actplanentity.aos_l_actstatus", QCP.equals, "A"), // 明细行状态为正常
						qFilter// 开始日期或结束日期在未来60天内
				}, null);

		dataSet = dataSet.groupBy(new String[] { "aos_itemid" }).count().finish();
		Map<String, Integer> result = new HashMap<>();
		while (dataSet.hasNext()) {
			Row next = dataSet.next();
			String aos_itemid = next.getString(0);
			Integer aos_times = next.getInteger(1);
			result.put(aos_itemid, aos_times);
		}
		return result;
	}

	/**
	 * 获取最新一期的海外滞销表中的数据
	 * 
	 * @param orgNumber 国别编码
	 * @return itemNumber
	 */
	private static List<String> getNewestUnsaleDy(String orgNumber) {
		// 获取最新一期的时间
		DynamicObject dy_date = QueryServiceHelper.queryOne("aos_sal_quo_ur", "max(aos_makedate) as aos_makedate",
				null);
		if (dy_date == null || dy_date.get("aos_makedate") == null)
			return new ArrayList<>();
		else {
			LocalDate localDate = dy_date.getDate("aos_makedate").toInstant().atZone(ZoneId.systemDefault())
					.toLocalDate();
			QFilter filter_date_s = new QFilter("aos_makedate", ">=", localDate.toString());
			QFilter filter_date_e = new QFilter("aos_makedate", "<", localDate.plusDays(1).toString());
			QFilter filter_org = new QFilter("aos_org.number", "=", orgNumber);
			QFilter[] qfs = new QFilter[] { filter_date_s, filter_date_e, filter_org };
			return QueryServiceHelper.query("aos_sal_quo_ur", "aos_entryentity.aos_sku aos_sku", qfs).stream()
					.map(dy -> dy.getString("aos_sku")).distinct().collect(Collectors.toList());
		}
	}

}
