package mkt.popularst.ppc;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import common.CommonDataSom;
import common.StringComUtils;
import common.fnd.FndDate;
import common.fnd.FndGlobal;
import common.fnd.FndLog;
import common.fnd.FndMsg;
import common.Cux_Common_Utl;
import common.sal.util.InStockAvailableDays;
import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
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
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import mkt.common.AosMktGenerate;
import mkt.common.MKTCom;
import mkt.popular.aos_mkt_pop_common;
import mkt.popular.ppc.aos_mkt_popppc_init;
import mkt.popularst.add_s.aos_mkt_popadds_init;
import mkt.popularst.adjust_s.aos_mkt_pop_stadd_init;
import mkt.popularst.adjust_s.aos_mkt_popadjst_init;
import mkt.popularst.week.aos_mkt_popweek_init;

// 营销ST推广生成核心

/**
 * 2023.05.08 测试结果
 * 
 * 1.关键词任然存在null 需剔除(已解决) 2.关键词报告接口同步问题 数据量太大接口无法同步 3.因测试站数据问题 去除了预断货剔除与低毛利剔除
 * 需恢复
 * 
 * @author aosom
 *
 *         TODO
 */

public class aos_mkt_popppcst_init extends AbstractTask {
	private static final String KW = "KW";
	private static final String ST = "ST";
	private static final String AMAZON = "AMAZON";

	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		executerun();// EU
	}

	public static void executerun() {
		FndMsg.debug("=======开始推广ST生成核心=======");
		// 当前时间
		Calendar Today = Calendar.getInstance();
		// 当前工作日
		int week = Today.get(Calendar.DAY_OF_WEEK);
		// 判断今日是否执行
		Boolean CopyFlag = aos_mkt_pop_common.GetCopyFlag("PPC_ST", week);
		// 若今日需要执行则初始化通用计算数据
		if (CopyFlag)
			CommonDataSom.init();
		// 当前整点
		int p_hour = Today.get(Calendar.HOUR_OF_DAY);
		// 根据当前整点判断执行的是欧洲与美加
		QFilter qf_time = aos_mkt_pop_common.getEuOrUsQf(p_hour);
		// 获取所有当前整点应执行的销售国别
		DynamicObjectCollection bd_country = QueryServiceHelper.query("bd_country", "id,number",
				new QFilter("aos_is_oversea_ou.number", QCP.equals, "Y").and("aos_isomvalid", QCP.equals, true)
						.and(qf_time).toArray());
		for (DynamicObject ou : bd_country) {
			Map<String, Object> params = new HashMap<>();
			params.put("p_ou_code", ou.getString("number"));
			// 主方法
			do_operate(params);
		}
		// 生成销售手动调整ST
		FndMsg.debug("=======生成销售手动调整ST=======");
		aos_mkt_popadds_init.executerun(p_hour);
		// 生成销售周调整ST
		FndMsg.debug("=======生成销售周调整ST=======");
		aos_mkt_popweek_init.executerun(p_hour);
		// 生成ST销售加回
		FndMsg.debug("=======生成ST销售加回=======");
		aos_mkt_pop_stadd_init.stInit();
	}

	@SuppressWarnings("deprecation")
	public static void do_operate(Map<String, Object> params) {
		// 打印对象
		FndMsg fndmsg = FndMsg.init();
		fndmsg.setOn();
		fndmsg.print("========Into ST Init========");
		// 日期格式化
		SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");
		// 国别编码
		Object p_ou_code = params.get("p_ou_code");
		fndmsg.print("====" + p_ou_code + "====");
		// 国别id
		Object p_org_id = FndGlobal.get_import_id(p_ou_code, "bd_country");
		// 删除该国别今日已生成的数据
		Calendar date = Calendar.getInstance();
		date.set(Calendar.HOUR_OF_DAY, 0);
		date.set(Calendar.MINUTE, 0);
		date.set(Calendar.SECOND, 0);
		date.set(Calendar.MILLISECOND, 0);
		Date Today = date.getTime();
		// 删除ST主表数据
		DeleteServiceHelper.delete("aos_mkt_pop_ppcst",
				new QFilter("aos_orgid.number", QCP.equals, p_ou_code).and("aos_date", QCP.equals, Today).toArray());
		// 删除ST数据表数据
		DeleteServiceHelper.delete("aos_mkt_ppcst_data",
				new QFilter("aos_orgid.number", QCP.equals, p_ou_code).and("aos_date", QCP.equals, Today).toArray());
		// 获取单号
		int year = date.get(Calendar.YEAR) - 2000;
		int month = date.get(Calendar.MONTH) + 1;
		int day = date.get(Calendar.DAY_OF_MONTH);
		String aos_billno = ST + p_ou_code + year + month + day;
		// 推广类型
		Object aos_poptype = FndGlobal.get_import_id(ST, "aos_mkt_base_poptype");
		// 推广平台
		Object platform_id = FndGlobal.get_import_id(AMAZON, "aos_sal_channel");
		// 当前周
		int week = date.get(Calendar.DAY_OF_WEEK);
		// 如果不是执行周期中的日期则复制昨日数据
		Boolean CopyFlag = aos_mkt_pop_common.GetCopyFlag("PPC_ST", week);
		fndmsg.print("CopyFlag:" + CopyFlag);
		if (!CopyFlag) {
			copyLastDayData(p_org_id, aos_billno, aos_poptype, Today, platform_id);
			return;
		}

		// 获取前三日
		date.add(Calendar.DAY_OF_MONTH, -1);
		String Yester = DF.format(date.getTime());
		date.add(Calendar.DAY_OF_MONTH, -1);
		String Yester2 = DF.format(date.getTime());
		date.add(Calendar.DAY_OF_MONTH, -1);
		String Yester3 = DF.format(date.getTime());
		// 获取春夏品 秋冬品开始结束日期 营销日期参数表
		Map<String, String> ProductAvaMap = new HashMap<>();// 记录系列是否可用
		Map<String, Integer> ProductNoMap = new HashMap<>();// 记录系列可用组个数
		Date SummerSpringStart = MKTCom.Get_DateRange("aos_datefrom", "SS", p_org_id);
		Date SummerSpringEnd = MKTCom.Get_DateRange("aos_dateto", "SS", p_org_id);
		Date AutumnWinterStart = MKTCom.Get_DateRange("aos_datefrom", "AW", p_org_id);
		Date AutumnWinterEnd = MKTCom.Get_DateRange("aos_dateto", "AW", p_org_id);
		// 通用数据
		HashMap<String, String> ProductInfo = AosMktGenerate.GenerateShopSKU(p_ou_code);// 店铺货号
		List<Map<String, Object>> ItemQtyList = AosMktGenerate.GenerateItemQtyList(p_ou_code);
		Map<String, Object> ItemOverseaQtyMap = ItemQtyList.get(0);// 海外库存
		Map<String, Object> ItemIntransQtyMap = ItemQtyList.get(1);// 在途数量
		HashMap<String, Object> GroupDateMap = AosMktGenerate.GenerateLastGroupDate(p_ou_code);// 组创建日期
		HashMap<String, Object> KeyDateMap = AosMktGenerate.GenerateLastKeyDate(p_ou_code);// 词创建日期
		HashMap<String, Object> SerialDateMap = AosMktGenerate.GenerateLastSerialDate(p_ou_code);// 词创建日期
		HashMap<String, Map<String, Object>> KeyRpt = AosMktGenerate.GenerateKeyRpt(p_ou_code); // 关键词报告 非常重要！
		HashMap<String, Map<String, Object>> AdPriceKey = AosMktGenerate.GenerateAdvKeyPrice(p_ou_code);// 关键词建议价格
		HashMap<String, Object> DailyPrice = AosMktGenerate.GenerateDailyPrice(p_ou_code);// 亚马逊定价
		HashMap<String, Map<String, Object>> PopOrgInfo = AosMktGenerate.GeneratePopOrgInfo(p_ou_code);// 营销国别标准参数
		HashMap<String, Object> ItemAvaDays = AosMktGenerate.GenerateAvaDays(p_ou_code);// 可售天数
		HashMap<String, Map<String, Object>> PpcYester = AosMktGenerate.GeneratePpcYesterSt(p_ou_code);// 昨日数据
		HashMap<String, Map<String, Object>> KeyRpt7 = AosMktGenerate.GenerateSkuRpt7(p_ou_code);// 7日词报告
		HashMap<String, Map<String, Object>> SalSummary = AosMktGenerate.GenerateSalSummarySerial(p_ou_code);// 预测汇总表
		HashMap<String, Map<String, Object>> PpcYesterSerial = AosMktGenerate.GeneratePpcYesterSerial(p_ou_code);
		HashMap<String, Map<String, Object>> SkuRpt_Serial = AosMktGenerate.GenerateSkuRptSerial(p_ou_code);
		HashMap<String, Map<String, Object>> SkuRptDetailSerial = AosMktGenerate.GenerateSkuRptDetailSerial(p_ou_code);
		HashMap<String, Map<String, Map<String, Object>>> SkuRpt3Serial = AosMktGenerate
				.GenerateSkuRpt3SerialObject(p_ou_code);
		Map<String, BigDecimal> CostMap = aos_mkt_popppc_init.initItemCost();
		Map<String, BigDecimal> ShipFee = aos_mkt_popppc_init.GenerateShipFee();
		Map<String, BigDecimal> VatMap = aos_mkt_popppc_init.GenerateVat(p_org_id);
		Set<String> manualSet = GenerateManual(p_ou_code);
		Set<String> weekSet = GenerateWeek(p_ou_code);

		BigDecimal aos_vat_amount = VatMap.get("aos_vat_amount");
		BigDecimal aos_am_platform = VatMap.get("aos_am_platform");

		HashMap<String, Map<String, String>> itemPoint = GenerateItemPoint(p_org_id);
		// 全程天数信息
		List<Integer> MapList = AosMktGenerate.GenerateShpDay(p_ou_code);
		int aos_shp_day = MapList.get(0);// 备货天数
		int aos_freight_day = MapList.get(1);// 海运天数
		int aos_clear_day = MapList.get(2);// 清关天数
		// 营销国别参数
		BigDecimal HIGH1 = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "HIGH1").get("aos_value");// 最高标准1(定价<200)
		BigDecimal HIGH2 = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "HIGH2").get("aos_value");// 最高标准2(200<=定价<500)
		BigDecimal HIGH3 = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "HIGH3").get("aos_value");// 最高标准3(定价>=500)
		BigDecimal PopOrgFirstBid = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "FIRSTBID").get("aos_value");// 国别首次出价均价
		BigDecimal LOWESTBID = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "LOWESTBID").get("aos_value");
		BigDecimal PopOrgRoist = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "ROIST").get("aos_value");// 国别标准ROI
		BigDecimal WORST = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "WORST").get("aos_value");// 很差
		BigDecimal WORRY = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "WORRY").get("aos_value");// 差
		BigDecimal STANDARD = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "STANDARD").get("aos_value");// 标准
		BigDecimal WELL = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "WELL").get("aos_value");// 好
		BigDecimal EXCELLENT = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "EXCELLENT").get("aos_value");// 优
		BigDecimal PopOrgAMSaleRate = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "AMSALE_RATE").get("aos_value");// AM营收占比
		BigDecimal PopOrgAMAffRate = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "AMAFF_RATE").get("aos_value");// AM付费营收占比
		BigDecimal PointClick = (BigDecimal) PopOrgInfo.get(p_org_id + "~" + "POINT_CLICK").get("aos_value");// 推广词点击
		// 营销出价调整幅度参数表
		String SelectColumn = "aos_exposure,aos_exposurerate,aos_roi,aos_roitype,aos_rate,aos_level";
		DataSet aos_mkt_bsadjrateST = QueryServiceHelper.queryDataSet("aos_mkt_bsadjratest." + p_ou_code,
				"aos_mkt_bsadjratest", SelectColumn, null, "aos_level");
		// 营销预算调整系数表 到国别维度
		QFilter filter_org = new QFilter("aos_orgid", "=", p_org_id);
		QFilter[] filters_adjpara = new QFilter[] { filter_org };
		SelectColumn = "aos_ratefrom,aos_rateto,aos_roi,aos_adjratio";
		DataSet aos_mkt_bsadjparaS = QueryServiceHelper.queryDataSet("aos_mkt_popppc_init." + p_ou_code,
				"aos_mkt_bsadjpara", SelectColumn, filters_adjpara, null);
		// 查询国别物料
		QFilter filter_ou = new QFilter("aos_contryentry.aos_nationality.number", "=", p_ou_code);
		QFilter filter_type = new QFilter("aos_protype", "=", "N")/* .and("number", "=", "001-007") */;
		QFilter[] filters = new QFilter[] { filter_ou, filter_type };
		String SelectField = "id,aos_productno," + "number," + "name," + "aos_contryentry.aos_nationality.id aos_orgid,"
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
		// 初始化ST头信息
		DynamicObject aos_mkt_pop_ppcst = BusinessDataServiceHelper.newDynamicObject("aos_mkt_pop_ppcst");
		aos_mkt_pop_ppcst.set("aos_orgid", p_org_id);
		aos_mkt_pop_ppcst.set("billstatus", "A");
		aos_mkt_pop_ppcst.set("aos_billno", aos_billno);
		aos_mkt_pop_ppcst.set("aos_poptype", aos_poptype);
		aos_mkt_pop_ppcst.set("aos_date", Today);
		aos_mkt_pop_ppcst.set("aos_channel", platform_id);
		SaveServiceHelper.save(new DynamicObject[] { aos_mkt_pop_ppcst });

		DynamicObjectCollection aos_entryentityS = aos_mkt_pop_ppcst.getDynamicObjectCollection("aos_entryentity");
		// 初始化历史记录对象
//		FndLog log = FndLog.init("MKT_PPC_ST数据源初始化", p_ou_code.toString() + year + month + day);
		// 循环1:国别物料
		fndmsg.setOn();
		fndmsg.print("========开始 循环1:国别物料========");
		for (DynamicObject bd_material : bd_materialS) {
			count++;
			fndmsg.setOff();
			fndmsg.print(p_ou_code + "循环1" + "(" + count + "/" + rows + ")");
			long item_id = bd_material.getLong("id");
			long org_id = bd_material.getLong("aos_orgid");
			String orgid_str = Long.toString(org_id);
			String itemid_str = Long.toString(item_id);
			// 物料编码
			String aos_itemnumer = bd_material.getString("number");
			// 产品号
			String aos_productno = bd_material.getString("aos_productno");
			// 物料品名
			String aos_itemname = bd_material.getString("name");
			// 物料状态
			String aos_itemstatus = bd_material.getString("aos_contryentrystatus");
			// 节日属性
			String aos_festivalseting = bd_material.getString("aos_festivalseting");
			if (aos_festivalseting == null)
				aos_festivalseting = "";
			// 季节属性
			String aos_season = bd_material.getString("aos_season");
			String aos_seasonpro = bd_material.getString("aos_seasonseting");
			if (aos_seasonpro == null)
				aos_seasonpro = "";
			Object aos_seasonid = bd_material.get("aos_seasonid");
			if (aos_seasonid == null) {
//				log.add(aos_itemnumer + "季节属性不存在");
				continue;
			}
			// 海外库存
			Object item_overseaqty = ItemOverseaQtyMap.get(itemid_str);// 终止状态且无海外库存 跳过
			if (item_overseaqty == null || item_overseaqty.equals("null"))
				item_overseaqty = 0;
			if ("C".equals(aos_itemstatus) && (int) item_overseaqty == 0) {
//				log.add(aos_itemnumer + "终止状态且无海外库存");
				continue;
			}
			// 产品号
			Object item_intransqty = ItemIntransQtyMap.get(item_id + "");// 在途数量
			if (item_intransqty == null || item_intransqty.equals("null"))
				item_intransqty = 0;
			if (aos_productno == null || aos_productno.equals("") || aos_productno.equals("null")) {
//				log.add(aos_itemnumer + "产品号不存在");
				continue;
			}
			// 获取AMAZON店铺货号
			String aos_shopsku = ProductInfo.get(item_id + "");
			if (aos_shopsku == null || aos_shopsku.equals("") || aos_shopsku.equals("null")) {
//				log.add(aos_itemnumer + "AMAZON店铺货号不存在");
				continue;
			}
			// 可售库存天数7日
			if (ItemAvaDays.get(aos_itemnumer) == null) {
//				log.add(aos_itemnumer + "无可售库存天数");
				continue;
			}
			int aos_avadays = (int) ItemAvaDays.get(aos_itemnumer);
			// 产品类别
			String itemCategoryId = CommonDataSom.getItemCategoryId(item_id + "");
			if (itemCategoryId == null || "".equals(itemCategoryId)) {
//				log.add(aos_itemnumer + "产品类别不存在自动剔除");
				continue;
			}
			// 销售组别
			String salOrg = CommonDataSom.getSalOrgV2(p_org_id + "", itemCategoryId);
			if (salOrg == null || "".equals(salOrg)) {
//				log.add(aos_itemnumer + "组别不存在自动剔除");
				continue;
			}
			// TODO 物料词信息 无词信息 跳过
			Map<String, Object> KeyRptDetail = KeyRpt.get(aos_itemnumer);
			if (FndGlobal.IsNull(KeyRptDetail)) {
//				log.add(aos_itemnumer + "无词信息 跳过");
				continue;
			}

			// 大中小类
			String itemCategoryName = CommonDataSom.getItemCategoryName(String.valueOf(item_id));
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
			// 新系列新组逻辑
			int kw = 0;
			if (ProductNoMap.get(aos_productno + "-" + KW) != null)
				kw = ProductNoMap.get(aos_productno + "-" + KW);
			ProductNoMap.put(aos_productno + "-" + KW, kw);
			// 系列创建日期
			Object aos_makedate = null;
			if (SerialDateMap != null)
				aos_makedate = SerialDateMap.get(aos_productno + "-" + KW);
			if (aos_makedate == null)
				aos_makedate = Today;
			// 组创建日期
			Object aos_groupdate = null;
			if (GroupDateMap != null)
				aos_groupdate = GroupDateMap.get(aos_itemnumer);
			if (aos_groupdate == null)
				aos_groupdate = Today;
			// 给词信息添加 物料品类关键词信息
			Map<String, String> itemPointDetail = itemPoint
					.get(aos_category1 + "~" + aos_category2 + "~" + aos_category3 + "~" + aos_itemname);
			if (FndGlobal.IsNotNull(itemPointDetail) && !itemPointDetail.isEmpty()) {
				for (String key : itemPointDetail.keySet()) {
					KeyRptDetail.put(key + "~PHRASE", key + "~PHRASE");
					KeyRptDetail.put(key + "~EXACT", key + "~EXACT");
				}
			}

			// SKU剔除
			Map<String, Object> insert_map = new HashMap<>();// 初始化剔除类型组插入参数
			insert_map.put("aos_itemid", item_id);
			insert_map.put("aos_productno", aos_productno);
			insert_map.put("aos_itemnumer", aos_itemnumer);
			insert_map.put("aos_shopsku", aos_shopsku);
			insert_map.put("aos_makedate", aos_makedate);
			insert_map.put("aos_groupdate", aos_groupdate);
			insert_map.put("aos_eliminatedate", Today);
			insert_map.put("KeyRptDetail", KeyRptDetail);
			insert_map.put("KeyDateMap", KeyDateMap);
			insert_map.put("aos_itemname", aos_itemname);
			insert_map.put("aos_season", aos_season);
			insert_map.put("aos_avadays", aos_avadays);
			insert_map.put("aos_bid", LOWESTBID);// 最低出价
			insert_map.put("aos_sal_group", salOrg);// 销售组别
			insert_map.put("aos_category1", aos_category1);// 产品大类
			insert_map.put("aos_category2", aos_category2);// 产品中类
			insert_map.put("aos_category3", aos_category3);// 产品小类
			insert_map.put("aos_itemstatus", aos_itemstatus);
			insert_map.put("Today", Today);
			// .剔除无词组
			if (FndGlobal.IsNull(KeyRptDetail)) {
				insert_map.put("aos_reason", "无词组剔除");
				InsertData(aos_entryentityS, insert_map);
//				log.add(aos_itemnumer + "无词组剔除");
				continue;
			}

			// .剔除过季品
			if ((FndGlobal.IsNotNull(aos_seasonpro))
					&& (aos_seasonpro.equals("SPRING") || aos_seasonpro.equals("SPRING_SUMMER")
							|| aos_seasonpro.equals("SUMMER"))
					&& (Today.before(SummerSpringStart) || Today.after(SummerSpringEnd))) {
				insert_map.put("aos_reason", "过季品剔除");
				InsertData(aos_entryentityS, insert_map);
//				log.add(aos_itemnumer + "过季品剔除");
				continue;
			}
			if ((FndGlobal.IsNotNull(aos_seasonpro))
					&& (aos_seasonpro.equals("AUTUMN_WINTER") || aos_seasonpro.equals("WINTER"))
					&& (Today.before(AutumnWinterStart) || Today.after(AutumnWinterEnd))) {
				insert_map.put("aos_reason", "过季品剔除");
				InsertData(aos_entryentityS, insert_map);
//				log.add(aos_itemnumer + "过季品剔除");
				continue;
			}
			// .剔除预断货

			Map<String, Object> DayMap = new HashMap<>();
			int availableDays = InStockAvailableDays.calInstockSalDaysForDayMin(orgid_str, itemid_str, DayMap);// 可售天数
			if (FndGlobal.IsNull(aos_festivalseting)) {
				if (((availableDays < 30) && (MKTCom.Is_PreSaleOut(org_id, item_id, (int) item_intransqty, aos_shp_day,
						aos_freight_day, aos_clear_day, availableDays)))) {
					insert_map.put("aos_reason", "预断货剔除");
					InsertData(aos_entryentityS, insert_map);
//					log.add(aos_itemnumer + "预断货剔除");
					continue;
				}
			}

			// .剔除毛利<12%

//			Object FixValue = DailyPrice.getOrDefault(item_id+ "", BigDecimal.ZERO);
//			BigDecimal inventoryCost = CostMap.getOrDefault(org_id + "~" + item_id, BigDecimal.ZERO);
//			BigDecimal expressFee = ShipFee.getOrDefault(org_id + "~" + item_id, BigDecimal.ZERO);
//			BigDecimal Profitrate = SalUtil.calProfitrate((BigDecimal) FixValue, aos_vat_amount, aos_am_platform,
//					inventoryCost, expressFee);
//			
//			log.add(aos_itemnumer + " FixValue =" + FixValue + "\n" + " inventoryCost =" + inventoryCost + "\n"
//					+ " expressFee =" + expressFee + "\n" + " aos_vat_amount =" + aos_vat_amount + "\n"
//					+ " aos_am_platform =" + aos_am_platform + "\n");
//
//			log.add(aos_itemnumer + "(" + FixValue + " / (1 + " + aos_vat_amount + ") - " + inventoryCost + " - "
//					+ FixValue + " / (1 +  " + aos_vat_amount + ") * " + aos_am_platform + " - " + expressFee
//					+ " / (1 + " + aos_vat_amount + "+)) / (" + FixValue + " / (1 + " + aos_vat_amount + "+))");
//			
//			if (Profitrate.compareTo(BigDecimal.valueOf(0.12)) < 0) {
//				insert_map.put("aos_reason", "低毛利剔除");
//				InsertData(aos_entryentityS, insert_map);
//				log.add(aos_itemnumer + "低毛利剔除");
//				continue;
//			}

			// 循环关键词表
			for (String aos_targetcomb : KeyRptDetail.keySet()) {
				// 关键词
				String aos_keyword = aos_targetcomb.split("~")[0];
				if (Cux_Common_Utl.IsNull(aos_keyword))
					continue;
				// 匹配方式
				String aos_match_type = aos_targetcomb.split("~")[1];
				// 词创建日
				Object aos_keydate = null;
				if (KeyDateMap != null)
					aos_keydate = KeyDateMap.get(aos_itemnumer + "~" + aos_targetcomb);
				if (aos_keydate == null)
					aos_keydate = Today;
				// 词报告
				String key = aos_itemnumer + "~" + aos_targetcomb;
				Map<String, Object> KeyRptMap = KeyRpt7.get(key);
				// 无词报告信息剔除
				if (FndGlobal.IsNull(KeyRptMap)) {
					keyInsertData(aos_entryentityS, insert_map, aos_keyword, aos_match_type, aos_keydate);
					continue;
				}

				// .手动建组剔除
				if (manualSet.contains(key)) {
					insert_map.put("aos_reason", "手动建组剔除");
					InsertData(aos_entryentityS, insert_map);
//					log.add(key + "手动建组剔除");
					continue;
				}
				// .周调整剔除
				if (weekSet.contains(key)) {
					insert_map.put("aos_reason", "周调整剔除");
					InsertData(aos_entryentityS, insert_map);
//					log.add(key + "周调整剔除");
					continue;
				}

				// 7天词ROI
				BigDecimal Roi7Days = BigDecimal.ZERO;
				BigDecimal aos_impressions = BigDecimal.ZERO;
				BigDecimal aos_clicks = BigDecimal.ZERO;
				if (KeyRptMap != null && ((BigDecimal) KeyRptMap.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0)
					Roi7Days = ((BigDecimal) KeyRptMap.get("aos_sales")).divide((BigDecimal) KeyRptMap.get("aos_spend"),
							2, BigDecimal.ROUND_HALF_UP);
				if (KeyRptMap != null) {
					aos_impressions = (BigDecimal) KeyRptMap.get("aos_impressions");
					aos_clicks = (BigDecimal) KeyRptMap.get("aos_clicks");
				}
				// 周一触发 词剔除判断
				if (week == 2 && Roi7Days.compareTo(BigDecimal.valueOf(3.5)) < 0
						&& FndDate.GetBetweenDays(Today, (Date) aos_keydate) > 14
						&& aos_clicks.compareTo(PointClick) > 0) {
					insert_map.put("aos_reason", "差ROI剔除");
					keyInsertData(aos_entryentityS, insert_map, aos_keyword, aos_match_type, aos_keydate);
//					log.add(key + "差ROI剔除");
					continue;
				}
				// 赋值数据
				DynamicObject aos_entryentity = aos_entryentityS.addNew();
				aos_entryentity.set("aos_itemid", item_id);
				aos_entryentity.set("aos_productno", aos_productno + "-" + KW);
				aos_entryentity.set("aos_itemnumer", aos_itemnumer);
				aos_entryentity.set("aos_itemstatus", aos_itemstatus);
				aos_entryentity.set("aos_keyword", aos_keyword);
				aos_entryentity.set("aos_match_type", aos_match_type);
				aos_entryentity.set("aos_shopsku", aos_shopsku);
				aos_entryentity.set("aos_makedate", aos_makedate);
				aos_entryentity.set("aos_groupdate", aos_groupdate);
				aos_entryentity.set("aos_keydate", aos_keydate);
				aos_entryentity.set("aos_keystatus", "AVAILABLE");
				aos_entryentity.set("aos_groupstatus", "AVAILABLE");
				aos_entryentity.set("aos_serialstatus", "AVAILABLE");
				aos_entryentity.set("aos_itemname", aos_itemname);
				aos_entryentity.set("aos_season", aos_season);
				aos_entryentity.set("aos_avadays", aos_avadays);
				aos_entryentity.set("aos_roi7days", Roi7Days);
				aos_entryentity.set("aos_impressions", aos_impressions);
				aos_entryentity.set("aos_sal_group", salOrg);
				aos_entryentity.set("aos_category1", aos_category1);
				aos_entryentity.set("aos_category2", aos_category2);
				aos_entryentity.set("aos_category3", aos_category3);
				ProductAvaMap.put(aos_productno + "-" + KW, "AVAILABLE");// 存在一个词可用则系列可用
				ProductNoMap.put(aos_productno + "-" + KW, kw + 1);// 累计可用组个数
			}
		}
		// 循环2 重新循环设置系列状态 只要存在一个可用即为可用
		fndmsg.setOn();
		fndmsg.print("========开始 循环2========");
		int size = aos_entryentityS.size();
		count = 0;
		for (DynamicObject aos_entryentity : aos_entryentityS) {
			count++;
			fndmsg.setOff();
			fndmsg.print(p_ou_code + "循环2" + "(" + count + "/" + size + ")");
			String aos_productno = aos_entryentity.getString("aos_productno");
			if (aos_productno.equals("AVAILABLE"))
				continue; // 只判断不可用系列
			if (ProductAvaMap.get(aos_productno) != null)
				aos_entryentity.set("aos_serialstatus", "AVAILABLE");
		}
		// 循环3 计算出价
		fndmsg.setOn();
		fndmsg.print("========开始 循环3========");
		count = 0;
		Map<String, BigDecimal> ProductNoBid_Map = new HashMap<>();
		for (DynamicObject aos_entryentity : aos_entryentityS) {
			count++;
			fndmsg.setOff();
			fndmsg.print(p_ou_code + "循环3" + "(" + count + "/" + size + ")");
			String aos_groupstatus = aos_entryentity.getString("aos_groupstatus");
			if (!aos_groupstatus.equals("AVAILABLE"))
				continue; // 只判断可用组
			String aos_keystatus = aos_entryentity.getString("aos_keystatus");
			if (!aos_keystatus.equals("AVAILABLE"))
				continue; // 只判断可用词
			long item_id = aos_entryentity.getLong("aos_itemid");
			String aos_itemnumer = aos_entryentity.getString("aos_itemnumer");
			int aos_avadays = aos_entryentity.getInt("aos_avadays");
			String aos_productno = aos_entryentity.getString("aos_productno");
			String aos_keyword = aos_entryentity.getString("aos_keyword");
			String aos_match_type = aos_entryentity.getString("aos_match_type");
			BigDecimal aos_roi7days = aos_entryentity.getBigDecimal("aos_roi7days");
			BigDecimal aos_impressions = aos_entryentity.getBigDecimal("aos_impressions");
			String key = aos_itemnumer + "~" + aos_keyword + "~" + aos_match_type;

			Boolean IsNewGroupFlag = false;// =====是否新系列新组判断=====
			Date aos_groupdate = aos_entryentity.getDate("aos_groupdate");
			if (aos_groupdate.equals(Today))
				IsNewGroupFlag = true;

			BigDecimal HighValue = BigDecimal.ZERO;// 最高出价 = MIN(市场价，最高标准)
			BigDecimal AdviceValue = BigDecimal.ZERO;// 建议价 报告中获取
			BigDecimal MaxValue = BigDecimal.ZERO;// 市场最高价 报告中获取
			BigDecimal MarketValue = BigDecimal.ZERO;// 市场价 公式计算

			// =====计算市场价=====
			Map<String, Object> AdPriceMap = AdPriceKey.get(key);// 组
			if (AdPriceMap != null) {
				AdviceValue = (BigDecimal) AdPriceMap.get("aos_bid_suggest");
				MaxValue = (BigDecimal) AdPriceMap.get("aos_bid_rangeend");
			}
			Object FixValue = DailyPrice.get(item_id + ""); // 定价 Amazon每日价格
			BigDecimal HighStandard = GetHighStandard(FixValue, HIGH1, HIGH2, HIGH3);// 最高标准 根据国别定价筛选
			HighValue = BigDecimal.ZERO;// 最高出价 = MIN(市场价，最高标准)
			// 根据可售库存天数计算市场价
			if (aos_avadays < 90) {
				// 可售库存天数 < 90 市场价= min(建议价*0.7,最高标准)
				BigDecimal Value1 = AdviceValue.multiply(BigDecimal.valueOf(0.7));
				BigDecimal Value2 = HighStandard;
				MarketValue = Cux_Common_Utl.Min(Value1, Value2);
			} else if (aos_avadays >= 90 && aos_avadays <= 120) {
				// 可售库存天数 90-120 市场价=建议价
				MarketValue = AdviceValue;
			} else if (aos_avadays > 120 && aos_avadays <= 180) {
				// 可售库存天数120-180 市场价=max(市场最高价*0.7,建议价)
				BigDecimal Value1 = MaxValue.multiply(BigDecimal.valueOf(0.7));
				BigDecimal Value2 = AdviceValue;
				MarketValue = Cux_Common_Utl.Max(Value1, Value2);
			} else if (aos_avadays > 180) {
				// 可售库存天数>180 市场价=市场最高价
				MarketValue = MaxValue;
			}
			// =====End计算市场价=====
			HighValue = Cux_Common_Utl.Min(MarketValue, HighStandard);// 最高出价 = MIN(市场价，最高标准)
			BigDecimal aos_bid = BigDecimal.ZERO;// 出价
			Object aos_lastpricedate = Today;
			Object aos_lastbid = BigDecimal.ZERO;
			Map<String, Object> PpcYester_Map = PpcYester.get(aos_itemnumer + "~" + aos_keyword + "~" + aos_match_type);// 默认昨日数据
			if (PpcYester_Map != null) {
				aos_lastpricedate = PpcYester_Map.get("aos_lastpricedate");
				aos_lastbid = PpcYester_Map.get("aos_bid");// 昨日出价
				if (aos_lastpricedate == null)
					aos_lastpricedate = Today;
			}
			if (IsNewGroupFlag) {
				// 1.首次出价 新组或前一次为非可用组 首次出价规定：MIN(自动广告组市场价*0.7，国别均价)
				aos_bid = Cux_Common_Utl.Min(MarketValue.multiply(BigDecimal.valueOf(0.7)), PopOrgFirstBid);
				aos_lastpricedate = Today;// 判断为首次时需要调整出价日期
			} else {
				// 3.出价3天内不调整 今天与最近出价日期做判断 且 不为 首次建组日后7天内
				BigDecimal LastBid = (BigDecimal) aos_lastbid;// 上次出价 昨日出价缓存中获取
				Map<String, Object> GetAdjustRateMap = new HashMap<>();
				GetAdjustRateMap.put("aos_roi7days", aos_roi7days);// 七日词ROI
				GetAdjustRateMap.put("aos_impressions", aos_impressions);// 七日词曝光
				GetAdjustRateMap.put("PopOrgRoist", PopOrgRoist);
				GetAdjustRateMap.put("WORST", WORST);// 很差
				GetAdjustRateMap.put("WORRY", WORRY);// 差
				GetAdjustRateMap.put("STANDARD", STANDARD);// 标准
				GetAdjustRateMap.put("WELL", WELL);// 好
				GetAdjustRateMap.put("EXCELLENT", EXCELLENT);// 优
				Map<String, Object> AdjustRateMap = GetAdjustRate(GetAdjustRateMap, aos_mkt_bsadjrateST, aos_itemnumer);
				BigDecimal AdjustRate = (BigDecimal) AdjustRateMap.get("AdjustRate");// 调整幅度 出价调整幅度参数表中获取
				String rule = (String) AdjustRateMap.get("rule");
				int aos_level = (int) AdjustRateMap.get("aos_level");
//				log.add(aos_itemnumer + "优先级 =" + aos_level);
//				log.add(aos_itemnumer + "公式 =" + rule);
				aos_bid = LastBid.multiply(AdjustRate.add(BigDecimal.valueOf(1)));
			}
			aos_bid = Cux_Common_Utl.Min(aos_bid, HighValue);// 计算出价不能高于最高出价
			aos_bid = Cux_Common_Utl.Max(aos_bid, LOWESTBID);// 计算出价不能低于最低出价
			aos_entryentity.set("aos_bid", aos_bid);
			aos_entryentity.set("aos_bid_ori", aos_bid);
			aos_entryentity.set("aos_avadays", aos_avadays);
			aos_entryentity.set("aos_lastbid", aos_lastbid);
			aos_entryentity.set("aos_marketprice", MarketValue);// 市场价
			aos_entryentity.set("aos_highvalue", HighValue);// 最高价
			aos_entryentity.set("aos_fixvalue", FixValue);// 定价
			aos_entryentity.set("aos_lastpricedate", aos_lastpricedate);// 上次出价调整日期
			if (ProductNoBid_Map.get(aos_productno) != null)
				aos_bid = aos_bid.add(ProductNoBid_Map.get(aos_productno));
			ProductNoBid_Map.put(aos_productno, aos_bid);
		}
		count = 0;

		// 新品
		fndmsg.setOn();
		fndmsg.print("========开始 新品循环========");
		Map<String, BigDecimal> NewSerial_Map = new HashMap<>();
		for (DynamicObject aos_entryentity : aos_entryentityS) {
			count++;
			fndmsg.setOff();
			fndmsg.print(p_ou_code + "新品循环" + "(" + count + "/" + size + ")");
			Date aos_makedate = aos_entryentity.getDate("aos_makedate");
			Boolean IsNewSerialFlag = false;// =====是否新系列新组判断=====
			if (aos_makedate.equals(Today))
				IsNewSerialFlag = true;
			// 新系列预算计算 = MAX(广告系列中SKU预测营收*AM营收占比*AM付费营收占比/国别标准ROI，2*广告系列中SKU个数)
			String aos_productno = aos_entryentity.getString("aos_productno");
			if (!IsNewSerialFlag)
				continue; // 非新品 直接跳过 只需要合计新品
			Map<String, Object> Summary = SalSummary.get(aos_productno);
			if (Summary != null) {
				BigDecimal aos_sales = ((BigDecimal) SalSummary.get(aos_productno).get("aos_sales"))
						.divide(BigDecimal.valueOf(30), 2, BigDecimal.ROUND_HALF_UP);// 广告系列中SKU预测营收
				BigDecimal SerialTotalBid = ProductNoBid_Map.get(aos_productno);// 词出价合计
				BigDecimal budget1 = aos_sales.multiply(PopOrgAMSaleRate).multiply(PopOrgAMAffRate).divide(PopOrgRoist,
						2, BigDecimal.ROUND_HALF_UP);
				BigDecimal budget2 = SerialTotalBid.multiply(BigDecimal.valueOf(2));
				BigDecimal NewBudget = Cux_Common_Utl.Max(budget1, budget2);
				if (NewSerial_Map.get(aos_productno) != null)
					NewSerial_Map.put(aos_productno, NewBudget.add(NewSerial_Map.get(aos_productno)));
				else
					NewSerial_Map.put(aos_productno, NewBudget);
			}
		}

		// 循环4 计算预算 此时组系列词状态已定
		fndmsg.setOn();
		fndmsg.print("========开始 循环4========");
		count = 0;
		for (DynamicObject aos_entryentity : aos_entryentityS) {
			count++;
			fndmsg.setOff();
			fndmsg.print(p_ou_code + "循环4" + "(" + count + "/" + size + ")");
			String aos_serialstatus = aos_entryentity.getString("aos_serialstatus");
			if (!aos_serialstatus.equals("AVAILABLE"))
				continue;// 系列剔除不计算出价与预算
			String aos_productno = aos_entryentity.getString("aos_productno");
			String aos_itemnumer = aos_entryentity.getString("aos_itemnumer");
			Date aos_makedate = aos_entryentity.getDate("aos_makedate");
			Boolean IsNewSerialFlag = false;// =====是否新系列新组判断=====
			if (aos_makedate.equals(Today))
				IsNewSerialFlag = true;
			BigDecimal aos_budget = BigDecimal.ZERO;
			BigDecimal Roi7Days_Serial = BigDecimal.ZERO;// 7天ROI 系列维度
			Map<String, Object> PpcYesterSerial_Map = PpcYesterSerial.get(aos_productno);
			Map<String, Object> SkuRptMap_Serial = SkuRpt_Serial.get(aos_productno);// Sku报告
			if (SkuRptMap_Serial != null
					&& ((BigDecimal) SkuRptMap_Serial.get("aos_spend")).compareTo(BigDecimal.ZERO) != 0)
				Roi7Days_Serial = ((BigDecimal) SkuRptMap_Serial.get("aos_total_sales"))
						.divide((BigDecimal) SkuRptMap_Serial.get("aos_spend"), 2, BigDecimal.ROUND_HALF_UP);
			BigDecimal RoiLast1Days = BigDecimal.ZERO; // 昨天ROI
			BigDecimal RoiLast2Days = BigDecimal.ZERO; // 前天ROI
			BigDecimal RoiLast3Days = BigDecimal.ZERO; // 大前天ROI
			Map<String, Map<String, Object>> SkuRpt3SerialMap = SkuRpt3Serial.get(aos_productno);// Sku报告
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
			Map<String, Object> SkuRptDetailSerialMap = SkuRptDetailSerial.get(aos_productno);// Sku报告1日数据
			// 系列花出率 = 前一天花出/预算
			if (SkuRptDetailSerialMap != null && PpcYesterSerial_Map != null) {
				BigDecimal LastSpend = (BigDecimal) SkuRptDetailSerialMap.get("aos_spend");
				BigDecimal LastBudget = (BigDecimal) PpcYesterSerial_Map.get("aos_budget");// 昨日预算
//				log.add(aos_itemnumer + "预算3 昨日花出 =" + LastSpend);
//				log.add(aos_itemnumer + "预算3 昨日预算 =" + LastBudget);
				if (LastBudget.compareTo(BigDecimal.ZERO) != 0)
					CostRate = LastSpend.divide(LastBudget, 2, BigDecimal.ROUND_HALF_UP);
			}
			if (Cux_Common_Utl.GetBetweenDays(Today, (Date) aos_makedate) <= 3)
				// 全新系列3天内花出率最低为1
				CostRate = Cux_Common_Utl.Max(CostRate, BigDecimal.valueOf(1));

			if (IsNewSerialFlag) {
				// a.新系列 预算=新系列预算
				// 新系列预算计算 = MAX(广告系列中SKU预测营收*AM营收占比*AM付费营收占比/国别标准ROI，2*广告系列中SKU个数)
				Map<String, Object> Summary = SalSummary.get(aos_productno);//
				if (Summary != null) {
					BigDecimal aos_sales = ((BigDecimal) Summary.get("aos_sales")).divide(BigDecimal.valueOf(30), 2,
							BigDecimal.ROUND_HALF_UP);// 广告系列中SKU预测营收
					BigDecimal SerialTotalBid = ProductNoBid_Map.get(aos_productno);// 词出价合计
					BigDecimal budget1 = aos_sales.multiply(PopOrgAMSaleRate).multiply(PopOrgAMAffRate)
							.divide(PopOrgRoist, 2, BigDecimal.ROUND_HALF_UP);
					BigDecimal budget2 = SerialTotalBid.multiply(BigDecimal.valueOf(2));
					aos_budget = Cux_Common_Utl.Max(budget1, budget2);
				}
			} else if ((Roi7Days_Serial.compareTo(PopOrgRoist) < 0) && (((RoiLast1Days.compareTo(PopOrgRoist) >= 0)
					&& (RoiLast2Days.compareTo(PopOrgRoist) >= 0))
					|| ((RoiLast1Days.compareTo(PopOrgRoist) >= 0) && (RoiLast3Days.compareTo(PopOrgRoist) >= 0))
					|| ((RoiLast2Days.compareTo(PopOrgRoist) >= 0) && (RoiLast3Days.compareTo(PopOrgRoist) >= 0)))) {
				// b.当7天ROI＜国别标准时，前3天ROI中任意2天ROI≥国别标准ROI，则预算维持上次设置金额
				BigDecimal Lastbudget = BigDecimal.ZERO;
				if (PpcYesterSerial_Map != null)
					Lastbudget = (BigDecimal) PpcYesterSerial_Map.get("aos_budget");// 上次设置金额
				aos_budget = Lastbudget;
//				log.add(aos_itemnumer + "预算2 =" + aos_budget);
			} else {
				// c.计算预算
				BigDecimal Lastbudget = BigDecimal.ZERO;
				if (PpcYesterSerial_Map != null)
					Lastbudget = (BigDecimal) PpcYesterSerial_Map.get("aos_budget");// 昨日设置金额
				Map<String, Object> GetBudgetRateMap = new HashMap<>();
				String RoiType = GetRoiType(PopOrgRoist, WORST, WORRY, STANDARD, WELL, EXCELLENT, Roi7Days_Serial);
				GetBudgetRateMap.put("CostRate", CostRate);// 花出率
				GetBudgetRateMap.put("RoiType", RoiType);
				Map<String, Object> BudgetMap = GetBudgetRate(GetBudgetRateMap, aos_mkt_bsadjparaS);// 花出率获取对应预算调整系数
				BigDecimal BudgetRate = (BigDecimal) BudgetMap.get("BudgetRate");
				String rule = (String) BudgetMap.get("rule");
//				log.add(aos_itemnumer + "预算3 预算公式 =" + rule);
				BigDecimal NewSerial = BigDecimal.ZERO;
				BigDecimal aos_bid = BigDecimal.ZERO;
				if (ProductNoBid_Map.get(aos_productno) != null)
					aos_bid = ProductNoBid_Map.get(aos_productno);
				if (NewSerial_Map.get(aos_productno) != null)
					NewSerial = NewSerial_Map.get(aos_productno);
				BigDecimal budget1 = Lastbudget.multiply(BudgetRate).add(NewSerial);
				BigDecimal budget2 = aos_bid.multiply(BigDecimal.valueOf(20));
				aos_budget = Cux_Common_Utl.Max(budget1, budget2);

//				log.add(aos_itemnumer + "预算3 =" + aos_budget);
//				log.add(aos_itemnumer + "预算3 花出率 =" + CostRate);
//				log.add(aos_itemnumer + "预算3 7日Roi =" + Roi7Days_Serial);
//				log.add(aos_itemnumer + "预算3 上次预算 =" + Lastbudget);
//				log.add(aos_itemnumer + "预算3 花出率对应系数 =" + BudgetRate);
//				log.add(aos_itemnumer + "预算3 新系列 =" + NewSerial);
//				log.add(aos_itemnumer + "预算3 aos_bid =" + aos_bid);
//				log.add(aos_itemnumer + "预算3 budget1 =" + budget1);
//				log.add(aos_itemnumer + "预算3 budget2 =" + budget2);
			}

			aos_entryentity.set("aos_budget", Cux_Common_Utl.Max(aos_budget, BigDecimal.valueOf(2)));
			aos_entryentity.set("aos_budget_ori", Cux_Common_Utl.Max(aos_budget, BigDecimal.valueOf(2)));

		}
		aos_mkt_bsadjrateST.close();// 关闭Dataset
		aos_mkt_bsadjparaS.close();
		// 生成ST数据表
		genDataTableST(aos_entryentityS, aos_mkt_pop_ppcst.getPkValue(), p_org_id, Today, aos_billno);
		aos_entryentityS.clear();
		// 保存ST单据
		SaveServiceHelper.save(new DynamicObject[] { aos_mkt_pop_ppcst });
		// 保存log
//		log.finnalSave();
		// 开始生成 出价调整(销售)
		fndmsg.setOn();
		fndmsg.print("========开始生成 出价调整(销售)ST========");
		Map<String, Object> adjs = new HashMap<>();
		adjs.put("p_ou_code", p_ou_code);
		aos_mkt_popadjst_init.executerun(adjs);
	}

	/**
	 * 根据单据体对象生成数据
	 * 
	 * @param aos_entryentityS
	 * @param fid
	 * @param p_org_id
	 * @param today
	 * @param aos_billno
	 */
	private static void genDataTableST(DynamicObjectCollection aos_entryentityS, Object fid, Object p_org_id,
			Date today, String aos_billno) {
		int size = aos_entryentityS.size();
		int seq = 1;
		List<DynamicObject> dataS = new ArrayList<>();// 账单明细集合
		for (DynamicObject aos_entryentity : aos_entryentityS) {
			DynamicObject data = BusinessDataServiceHelper.newDynamicObject("aos_mkt_ppcst_data");
			data.set("aos_sourceid", fid);
			data.set("aos_orgid", p_org_id);
			data.set("aos_date", today);
			data.set("aos_productno", aos_entryentity.get("aos_productno"));
			data.set("aos_itemnumer", aos_entryentity.get("aos_itemnumer"));
			data.set("aos_shopsku", aos_entryentity.get("aos_shopsku"));
			data.set("aos_keyword", aos_entryentity.get("aos_keyword"));
			data.set("aos_match_type", aos_entryentity.get("aos_match_type"));
			data.set("aos_itemstatus", aos_entryentity.get("aos_itemstatus"));
			data.set("aos_type", aos_entryentity.get("aos_type"));
			data.set("aos_budget", aos_entryentity.get("aos_budget"));
			data.set("aos_bid", aos_entryentity.get("aos_bid"));
			data.set("aos_serialstatus", aos_entryentity.get("aos_serialstatus"));
			data.set("aos_groupstatus", aos_entryentity.get("aos_groupstatus"));
			data.set("aos_keystatus", aos_entryentity.get("aos_keystatus"));
			data.set("aos_valid_flag", aos_entryentity.get("aos_valid_flag"));
			data.set("aos_reason", aos_entryentity.get("aos_reason"));
			data.set("aos_makedate", aos_entryentity.get("aos_makedate"));
			data.set("aos_groupdate", aos_entryentity.get("aos_groupdate"));
			data.set("aos_keydate", aos_entryentity.get("aos_keydate"));
			data.set("aos_eliminatedate", aos_entryentity.get("aos_eliminatedate"));
			data.set("aos_lastpricedate", aos_entryentity.get("aos_lastpricedate"));
			data.set("aos_itemid", aos_entryentity.get("aos_itemid"));
			data.set("aos_itemname", aos_entryentity.get("aos_itemname"));
			data.set("aos_season", aos_entryentity.get("aos_season"));
			data.set("aos_sal_group", aos_entryentity.get("aos_sal_group"));
			data.set("aos_category1", aos_entryentity.get("aos_category1"));
			data.set("aos_category2", aos_entryentity.get("aos_category2"));
			data.set("aos_category3", aos_entryentity.get("aos_category3"));
			data.set("aos_budget_ori", aos_entryentity.get("aos_budget_ori"));
			data.set("aos_bid_ori", aos_entryentity.get("aos_bid_ori"));
			data.set("aos_avadays", aos_entryentity.get("aos_avadays"));
			data.set("aos_salemanual", aos_entryentity.get("aos_salemanual"));
			data.set("aos_roi7days", aos_entryentity.get("aos_roi7days"));
			data.set("aos_impressions", aos_entryentity.get("aos_impressions"));
			data.set("aos_lastbid", aos_entryentity.get("aos_lastbid"));
			data.set("aos_marketprice", aos_entryentity.get("aos_marketprice"));
			data.set("aos_highvalue", aos_entryentity.get("aos_highvalue"));
			data.set("aos_fixvalue", aos_entryentity.get("aos_fixvalue"));
			data.set("aos_billno", aos_billno);

			dataS.add(data);
			if (dataS.size() >= 5000 || seq == size) {
				DynamicObject[] dataSArray = dataS.toArray(new DynamicObject[0]);
				SaveServiceHelper.save(dataSArray);
				dataS.clear();
			}
			++seq;
		}
	}

	/**
	 * 赋值昨日数据
	 * 
	 * @param p_org_id
	 * @param aos_billno
	 * @param aos_poptype
	 * @param today
	 * @param platform_id
	 */
	private static void copyLastDayData(Object p_org_id, String aos_billno, Object aos_poptype, Date today,
			Object platform_id) {
		DynamicObject aos_mkt_pop_ppcst = BusinessDataServiceHelper.newDynamicObject("aos_mkt_pop_ppcst");
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
		long lastFid = QueryServiceHelper.queryOne("aos_mkt_pop_ppcst", "id", filters).getLong("id");
		DynamicObject aos_mkt_pop_ppclast = BusinessDataServiceHelper.loadSingle(lastFid, "aos_mkt_pop_ppcst");
		if (aos_mkt_pop_ppclast == null)
			return;// 没有昨日数据则直接退出
		aos_mkt_pop_ppcst.set("aos_orgid", p_org_id);
		aos_mkt_pop_ppcst.set("billstatus", "A");
		aos_mkt_pop_ppcst.set("aos_billno", aos_billno);
		aos_mkt_pop_ppcst.set("aos_poptype", aos_poptype);
		aos_mkt_pop_ppcst.set("aos_date", today);
		aos_mkt_pop_ppcst.set("aos_channel", platform_id);
		SaveServiceHelper.save(new DynamicObject[] { aos_mkt_pop_ppcst });
		Object fid = aos_mkt_pop_ppcst.getPkValue();
		// 查询所有昨日数据
		DynamicObjectCollection aosMktPpcStDataS = QueryServiceHelper.query("aos_mkt_ppcst_data",
				"aos_productno,aos_itemnumer,aos_shopsku,aos_keyword,"
						+ "aos_match_type,aos_itemstatus,aos_type,aos_budget,"
						+ "aos_bid,aos_serialstatus,aos_groupstatus,aos_keystatus,"
						+ "aos_valid_flag,aos_reason,aos_makedate,aos_groupdate,"
						+ "aos_keydate,aos_eliminatedate,aos_lastpricedate,aos_itemid,"
						+ "aos_itemname,aos_season,aos_sal_group,aos_category1,aos_category2,"
						+ "aos_category3,aos_budget_ori,aos_bid_ori,aos_avadays,aos_salemanual,"
						+ "aos_roi7days,aos_impressions,aos_lastbid,aos_marketprice," + "aos_highvalue,aos_fixvalue",
				new QFilter("aos_sourceid", QCP.equals, lastFid).toArray());
		int size = aosMktPpcStDataS.size();
		List<DynamicObject> dataS = new ArrayList<>();// 账单明细集合
		int seq = 1; // 序列号
		// 循环昨日数据生成本日数据
		for (DynamicObject aosMktPpcStData : aosMktPpcStDataS) {
			DynamicObject data = BusinessDataServiceHelper.newDynamicObject("aos_mkt_ppcst_data");
			data.set("aos_sourceid", fid);
			data.set("aos_orgid", p_org_id);
			data.set("aos_date", today);
			data.set("aos_productno", aosMktPpcStData.get("aos_productno"));
			data.set("aos_itemnumer", aosMktPpcStData.get("aos_itemnumer"));
			data.set("aos_shopsku", aosMktPpcStData.get("aos_shopsku"));
			data.set("aos_keyword", aosMktPpcStData.get("aos_keyword"));
			data.set("aos_match_type", aosMktPpcStData.get("aos_match_type"));
			data.set("aos_itemstatus", aosMktPpcStData.get("aos_itemstatus"));
			data.set("aos_type", aosMktPpcStData.get("aos_type"));
			data.set("aos_budget", aosMktPpcStData.get("aos_budget"));
			data.set("aos_bid", aosMktPpcStData.get("aos_bid"));
			data.set("aos_serialstatus", aosMktPpcStData.get("aos_serialstatus"));
			data.set("aos_groupstatus", aosMktPpcStData.get("aos_groupstatus"));
			data.set("aos_keystatus", aosMktPpcStData.get("aos_keystatus"));
			data.set("aos_valid_flag", aosMktPpcStData.get("aos_valid_flag"));
			data.set("aos_reason", aosMktPpcStData.get("aos_reason"));
			data.set("aos_makedate", aosMktPpcStData.get("aos_makedate"));
			data.set("aos_groupdate", aosMktPpcStData.get("aos_groupdate"));
			data.set("aos_keydate", aosMktPpcStData.get("aos_keydate"));
			data.set("aos_eliminatedate", aosMktPpcStData.get("aos_eliminatedate"));
			data.set("aos_lastpricedate", aosMktPpcStData.get("aos_lastpricedate"));
			data.set("aos_itemid", aosMktPpcStData.get("aos_itemid"));
			data.set("aos_itemname", aosMktPpcStData.get("aos_itemname"));
			data.set("aos_season", aosMktPpcStData.get("aos_season"));
			data.set("aos_sal_group", aosMktPpcStData.get("aos_sal_group"));
			data.set("aos_category1", aosMktPpcStData.get("aos_category1"));
			data.set("aos_category2", aosMktPpcStData.get("aos_category2"));
			data.set("aos_category3", aosMktPpcStData.get("aos_category3"));
			data.set("aos_budget_ori", aosMktPpcStData.get("aos_budget_ori"));
			data.set("aos_bid_ori", aosMktPpcStData.get("aos_bid_ori"));
			data.set("aos_avadays", aosMktPpcStData.get("aos_avadays"));
			data.set("aos_salemanual", aosMktPpcStData.get("aos_salemanual"));
			data.set("aos_roi7days", aosMktPpcStData.get("aos_roi7days"));
			data.set("aos_impressions", aosMktPpcStData.get("aos_impressions"));
			data.set("aos_lastbid", aosMktPpcStData.get("aos_lastbid"));
			data.set("aos_marketprice", aosMktPpcStData.get("aos_marketprice"));
			data.set("aos_highvalue", aosMktPpcStData.get("aos_highvalue"));
			data.set("aos_fixvalue", aosMktPpcStData.get("aos_fixvalue"));
			data.set("aos_billno", aos_billno);

			dataS.add(data);
			if (dataS.size() >= 5000 || seq == size) {
				DynamicObject[] dataSArray = dataS.toArray(new DynamicObject[0]);
				SaveServiceHelper.save(dataSArray);
				dataS.clear();
			}
			++seq;
		}

	}

	private static void keyInsertData(DynamicObjectCollection aos_entryentityS, Map<String, Object> insert_map,
			String aos_keyword, String aos_match_type, Object aos_keydate) {
		DynamicObject aos_entryentity = aos_entryentityS.addNew();
		Object item_id = insert_map.get("aos_itemid");
		Object aos_productno = insert_map.get("aos_productno");
		Object aos_itemnumer = insert_map.get("aos_itemnumer");
		Object aos_shopsku = insert_map.get("aos_shopsku");
		Object aos_makedate = insert_map.get("aos_makedate");
		Object aos_groupdate = insert_map.get("aos_groupdate");
		Object aos_eliminatedate = insert_map.get("aos_eliminatedate");
		Object aos_itemname = insert_map.get("aos_itemname");
		Object aos_season = insert_map.get("aos_season");
		Object aos_avadays = insert_map.get("aos_avadays");
		Object aos_bid = insert_map.get("aos_bid");
		Object aos_sal_group = insert_map.get("aos_sal_group");
		Object aos_category1 = insert_map.get("aos_category1");// 产品大类
		Object aos_category2 = insert_map.get("aos_category2");// 产品中类
		Object aos_category3 = insert_map.get("aos_category3");// 产品小类
		Object aos_itemstatus = insert_map.get("aos_itemstatus");
		aos_entryentity.set("aos_itemid", item_id);
		aos_entryentity.set("aos_productno", aos_productno + "-" + KW);
		aos_entryentity.set("aos_itemnumer", aos_itemnumer);
		aos_entryentity.set("aos_makedate", aos_makedate);
		aos_entryentity.set("aos_groupdate", aos_groupdate);
		aos_entryentity.set("aos_keydate", aos_keydate);
		aos_entryentity.set("aos_keystatus", "UNAVAILABLE");
		aos_entryentity.set("aos_groupstatus", "UNAVAILABLE");
		aos_entryentity.set("aos_serialstatus", "UNAVAILABLE");
		aos_entryentity.set("aos_keyword", aos_keyword);
		aos_entryentity.set("aos_match_type", aos_match_type);
		aos_entryentity.set("aos_shopsku", aos_shopsku);
		aos_entryentity.set("aos_eliminatedate", aos_eliminatedate);
		aos_entryentity.set("aos_itemname", aos_itemname);
		aos_entryentity.set("aos_season", aos_season);
		aos_entryentity.set("aos_avadays", aos_avadays);
		aos_entryentity.set("aos_bid", aos_bid);
		aos_entryentity.set("aos_bid_ori", aos_bid);
		aos_entryentity.set("aos_sal_group", aos_sal_group);
		aos_entryentity.set("aos_category1", aos_category1);
		aos_entryentity.set("aos_category2", aos_category2);
		aos_entryentity.set("aos_category3", aos_category3);
		aos_entryentity.set("aos_itemstatus", aos_itemstatus);
	}

	public static HashMap<String, Map<String, String>> GenerateItemPoint(Object p_org_id) {
		HashMap<String, Map<String, String>> itemPoint = new HashMap<>();
		// 品名关键词库
		DynamicObjectCollection aos_mkt_itempointS = QueryServiceHelper.query("aos_mkt_itempoint",
				"aos_category1," + "aos_category2,aos_category3," + "aos_itemname,aos_point",
				new QFilter("aos_orgid", QCP.equals, p_org_id).toArray());
		for (DynamicObject aos_mkt_itempoint : aos_mkt_itempointS) {
			String key = aos_mkt_itempoint.getString("aos_category1") + "~"
					+ aos_mkt_itempoint.getString("aos_category2") + "~" + aos_mkt_itempoint.getString("aos_category3")
					+ "~" + aos_mkt_itempoint.getString("aos_itemname");
			String point = aos_mkt_itempoint.getString("aos_point");
			Map<String, String> info = itemPoint.get(key);
			if (info == null)
				info = new HashMap<>();
			info.put(point, point);
			itemPoint.put(key, info);
		}
		// SKU关键词库
		DynamicObjectCollection aos_mkt_keywordS = QueryServiceHelper.query("aos_mkt_keyword",
				"aos_category1," + "aos_category2,aos_category3,"
						+ "aos_itemname,aos_entryentity.aos_mainvoc aos_point",
				new QFilter("aos_orgid", QCP.equals, p_org_id).toArray());
		for (DynamicObject aos_mkt_keyword : aos_mkt_keywordS) {
			String key = aos_mkt_keyword.getString("aos_category1") + "~" + aos_mkt_keyword.getString("aos_category2")
					+ "~" + aos_mkt_keyword.getString("aos_category3") + "~"
					+ aos_mkt_keyword.getString("aos_itemname");
			String point = aos_mkt_keyword.getString("aos_point");
			if (FndGlobal.IsNull(point))
				continue;
			Map<String, String> info = itemPoint.get(key);
			if (info == null)
				info = new HashMap<>();
			info.put(point, point);
			itemPoint.put(key, info);
		}
		return itemPoint;
	}

	private static Map<String, Object> GetAdjustRate(Map<String, Object> GetAdjustRateMap, DataSet aos_mkt_bsadjrateST,
			String aos_itemnumer) {
		Map<String, Object> AdjustRateMap = new HashMap<>();
		BigDecimal AdjustRate = BigDecimal.ZERO;

		Object aos_roi7days = GetAdjustRateMap.get("aos_roi7days");// 七日词ROI
		Object aos_impressions = GetAdjustRateMap.get("aos_impressions");// 七日词曝光
		Object PopOrgRoist = GetAdjustRateMap.get("PopOrgRoist");
		Object WORST = GetAdjustRateMap.get("WORST");// 很差
		Object WORRY = GetAdjustRateMap.get("WORRY");// 差
		Object STANDARD = GetAdjustRateMap.get("STANDARD");// 标准
		Object WELL = GetAdjustRateMap.get("WELL");// 好
		Object EXCELLENT = GetAdjustRateMap.get("EXCELLENT");// 优

		String[] OrderBy = { "aos_level" };
		DataSet aos_mkt_bsadjrateS = aos_mkt_bsadjrateST.copy().orderBy(OrderBy);
		String rule = "";
		int aos_level = 0;
		while (aos_mkt_bsadjrateS.hasNext()) {
			Row mkt_bsadjrate = aos_mkt_bsadjrateS.next();
			rule = "";
			String rule1 = "";
			String rule2 = "";
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

				rule1 = aos_roi7days + mkt_bsadjrate.getString("aos_roi") + RoiValue;
			}

			String aos_exposure = mkt_bsadjrate.getString("aos_exposure");
			if (aos_exposure != null && !aos_exposure.equals("") && !aos_exposure.equals("null"))
				if (rule1 != null && !rule1.equals(""))
					rule2 = "&&" + aos_impressions + aos_exposure + mkt_bsadjrate.getBigDecimal("aos_exposurerate");
				else
					rule2 = aos_impressions + aos_exposure + mkt_bsadjrate.getBigDecimal("aos_exposurerate");
			rule = rule1 + rule2;
			boolean condition = Cux_Common_Utl.GetResult(rule);
			if (condition) {
				AdjustRate = mkt_bsadjrate.getBigDecimal("aos_rate");
				aos_level = mkt_bsadjrate.getInteger("aos_level");
				break;
			}
		}
		aos_mkt_bsadjrateS.close();
		AdjustRateMap.put("AdjustRate", AdjustRate);
		AdjustRateMap.put("rule", rule);
		AdjustRateMap.put("aos_level", aos_level);
		return AdjustRateMap;
	}

	private static void InsertData(DynamicObjectCollection aos_entryentityS, Map<String, Object> insert_map) {
		Object item_id = insert_map.get("aos_itemid");
		Object aos_productno = insert_map.get("aos_productno");
		Object aos_itemnumer = insert_map.get("aos_itemnumer");
		Object aos_shopsku = insert_map.get("aos_shopsku");
		Object aos_makedate = insert_map.get("aos_makedate");
		Object aos_groupdate = insert_map.get("aos_groupdate");
		Object aos_eliminatedate = insert_map.get("aos_eliminatedate");
		Object aos_itemname = insert_map.get("aos_itemname");
		Object aos_season = insert_map.get("aos_season");
		Object aos_reason = insert_map.get("aos_reason");
		Object aos_avadays = insert_map.get("aos_avadays");
		Object aos_bid = insert_map.get("aos_bid");
		Object aos_sal_group = insert_map.get("aos_sal_group");
		Object aos_category1 = insert_map.get("aos_category1");// 产品大类
		Object aos_category2 = insert_map.get("aos_category2");// 产品中类
		Object aos_category3 = insert_map.get("aos_category3");// 产品小类
		Object aos_itemstatus = insert_map.get("aos_itemstatus");
		Object Today = insert_map.get("Today");

		@SuppressWarnings("unchecked")
		Map<String, Object> KeyRptDetail = (Map<String, Object>) insert_map.get("KeyRptDetail");
		@SuppressWarnings("unchecked")
		Map<String, Object> KeyDateMap = (Map<String, Object>) insert_map.get("KeyDateMap");

		for (String aos_targetcomb : KeyRptDetail.keySet()) {
			String aos_keyword = aos_targetcomb.split("~")[0];
			String aos_match_type = aos_targetcomb.split("~")[1];

			Object aos_keydate = null;
			if (KeyDateMap != null)
				aos_keydate = KeyDateMap.get(aos_itemnumer + "~" + aos_targetcomb);
			if (aos_keydate == null)
				aos_keydate = Today;// 新词

			DynamicObject aos_entryentity = aos_entryentityS.addNew();
			aos_entryentity.set("aos_itemid", item_id);
			aos_entryentity.set("aos_productno", aos_productno + "-" + KW);
			aos_entryentity.set("aos_itemnumer", aos_itemnumer);
			aos_entryentity.set("aos_makedate", aos_makedate);
			aos_entryentity.set("aos_groupdate", aos_groupdate);
			aos_entryentity.set("aos_keydate", aos_keydate);
			aos_entryentity.set("aos_keystatus", "UNAVAILABLE");
			aos_entryentity.set("aos_groupstatus", "UNAVAILABLE");
			aos_entryentity.set("aos_serialstatus", "UNAVAILABLE");
			aos_entryentity.set("aos_keyword", aos_keyword);
			aos_entryentity.set("aos_match_type", aos_match_type);
			aos_entryentity.set("aos_shopsku", aos_shopsku);
			aos_entryentity.set("aos_eliminatedate", aos_eliminatedate);
			aos_entryentity.set("aos_itemname", aos_itemname);
			aos_entryentity.set("aos_season", aos_season);
			aos_entryentity.set("aos_avadays", aos_avadays);
			aos_entryentity.set("aos_bid", aos_bid);
			aos_entryentity.set("aos_bid_ori", aos_bid);
			aos_entryentity.set("aos_sal_group", aos_sal_group);
			aos_entryentity.set("aos_category1", aos_category1);
			aos_entryentity.set("aos_category2", aos_category2);
			aos_entryentity.set("aos_category3", aos_category3);
			aos_entryentity.set("aos_itemstatus", aos_itemstatus);

			aos_entryentity.set("aos_reason", aos_reason);

		}
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

	private static Map<String, Object> GetBudgetRate(Map<String, Object> GetBudgetRateMap, DataSet aos_mkt_bsadjparaS) {
		Map<String, Object> BudgetMap = new HashMap<>();
		BigDecimal BudgetRate = BigDecimal.ZERO;
		String rule = "";
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
			// rule = CostRate + ">" + aos_ratefrom + "&&" + CostRate + "<=" + aos_rateto;
			// boolean condition = Cux_Common_Utl.GetResult(rule);
			if (condition) {
				BudgetRate = mkt_bsadjpara.getBigDecimal("aos_adjratio");
				break;
			}
		}
		mkt_bsadjparaS.close();
		BudgetMap.put("BudgetRate", BudgetRate);
		BudgetMap.put("rule", rule);
		return BudgetMap;
	}

	// 手动建组剔除
	private static Set<String> GenerateManual(Object p_ou_code) {
		// 第二部分 销售手动建组界面上周数据
		Calendar date = Calendar.getInstance();
		date.set(Calendar.HOUR_OF_DAY, 0);
		date.set(Calendar.MINUTE, 0);
		date.set(Calendar.SECOND, 0);
		date.set(Calendar.MILLISECOND, 0);
		int WeekOfYearLast = date.get(Calendar.WEEK_OF_YEAR) - 1;
		QFilter filter_week = new QFilter("aos_weekofyear", QCP.equals, WeekOfYearLast);
		QFilter filter_status = new QFilter("aos_entryentity.aos_subentryentity.aos_keystatus", QCP.equals, "OFF");// 删除
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter[] filters = new QFilter[] { filter_week, filter_org, filter_status };
		String SelectColumn = "aos_entryentity.aos_itemid.number aos_ad_name,"
				+ "aos_entryentity.aos_subentryentity.aos_keyword aos_keyword,"
				+ "aos_entryentity.aos_subentryentity.aos_keytype aos_keytype";
		DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_pop_addst", SelectColumn, filters);
		return list.stream().map(obj -> obj.getString("aos_ad_name") + "~" + obj.getString("aos_keyword") + "~"
				+ obj.getString("aos_keytype")).collect(Collectors.toSet());
	}

	// 周调整剔除
	private static Set<String> GenerateWeek(Object p_ou_code) {
		QFilter filter_org = new QFilter("aos_orgid.number", QCP.equals, p_ou_code);
		QFilter filter_type = new QFilter("aos_entryentity.aos_subentryentity.aos_valid", QCP.equals, true);
		QFilter[] filters = new QFilter[] { filter_org, filter_type };
		String SelectColumn = "aos_entryentity.aos_itemid.number aos_ad_name,"
				+ "aos_entryentity.aos_subentryentity.aos_keyword aos_keyword,"
				+ "aos_entryentity.aos_subentryentity.aos_match_type aos_match_type";
		DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_pop_weekst", SelectColumn, filters);
		return list.stream().map(obj -> obj.getString("aos_ad_name") + "~" + obj.getString("aos_keyword") + "~"
				+ obj.getString("aos_match_type")).collect(Collectors.toSet());
	}

}
