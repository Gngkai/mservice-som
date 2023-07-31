package mkt.act.query;

import cn.com.vastbase.core.Query;
import com.alibaba.fastjson.JSONObject;
import common.sal.sys.sync.service.ItemCacheService;
import common.sal.sys.sync.service.impl.ItemCacheServiceImpl;
import common.sal.util.InStockAvailableDays;
import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.cache.CacheFactory;
import kd.bos.cache.DistributeSessionlessCache;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.plugin.ImportLogger;
import kd.bos.exception.ErrorCode;
import kd.bos.exception.KDException;
import kd.bos.form.plugin.impt.BatchImportPlugin;
import kd.bos.form.plugin.impt.ImportBillData;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import kd.fi.bd.util.QFBuilder;
import mkt.common.MKTCom;
import org.apache.commons.lang3.time.DateUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class aos_mkt_act_query_imp extends BatchImportPlugin {
	Log log = LogFactory.getLog("aos_mkt_act_query_imp");
	private static final DistributeSessionlessCache cache = CacheFactory.getCommonCacheFactory()
			.getDistributeSessionlessCache("ComImp");

	private static final ItemCacheService itemCacheService = new ItemCacheServiceImpl();

	Map<String, String> ItemCategory = null;
	HashMap<String, Map<String, Object>> ItemInfo = null;
	HashMap<String, Map<String, Object>> ItemPrice = null;
	HashMap<String, Map<String, Object>> ItemPriceWeb = null;
	HashMap<String, Date> ItemArrival = null;
	Map<String, BigDecimal> ShipFee = null;
	Map<String, BigDecimal> FbaShipFee = null;
	HashMap<String, Map<String, Object>> item = null;
	HashMap<String, Integer> Order7Shop =  null;
	Map<String, BigDecimal> costMap = null;
	Map<String, BigDecimal> primeMap = null;
	Map<String, Integer> platQtyMap = null;
	Map<String, Integer> noPlatQtyMap = null;
	Map<String, BigDecimal> deStockQtyMap = null;
	Map<String,Integer> IeInstockQty = null;	//23-06-29 IE的海外库存直接国别物料的海外库存

	private synchronized void Before() {
		String currentUserId = UserServiceHelper.getCurrentUserId() + "";
		String ImpInitFlag = cache.get(currentUserId);
		System.out.println("currentUserId =" + currentUserId);
		System.out.println("ImpInitFlag =" + ImpInitFlag);
		if (ImpInitFlag == null || "".equals(ImpInitFlag) || "null".equals(ImpInitFlag) || ItemCategory == null) {
			System.out.println("======into first======");
			ItemCategory = GenerateCategory();
			ItemInfo = GenerateItemInfo();
			ItemPrice = GenerateItemPrice();
			ItemPriceWeb = GenerateItemPriceWeb();
			ItemArrival = queryExpectedArrivalDate();
			item = init_itemorg();
			ShipFee = GenerateShipFee();
			FbaShipFee = GenerateFbaShipFee();
			Order7Shop = GenerateOrder7Shop();
			costMap = initItemCost();
			platQtyMap = queryPlatFormQty();
			noPlatQtyMap = getNoPlatQty();
			primeMap = queryPrimeExpress();
			deStockQtyMap = calInventory();
			IeInstockQty = getIeInStockQty();
			cache.put(currentUserId, "Y", 3600);
			System.out.println("cache.get =" + cache.get(currentUserId));
		} else {
			System.out.println("======into remove======");
			cache.remove(currentUserId);
		}

	}

	private Map<String, BigDecimal> initItemCost() {

		DynamicObjectCollection list = QueryServiceHelper.query("aos_sync_invcost", "aos_orgid,aos_itemid,aos_item_cost", new QFilter[]{
				new QFilter("aos_isnew", QCP.equals, true)
		});
		return list.stream()
				.collect(Collectors.toMap(
						obj -> obj.getString("aos_orgid")+obj.getString("aos_itemid"),
						obj -> obj.getBigDecimal("aos_item_cost"),
						(k1, k2) -> k1));
	}

	// 平台仓数量
	private Map<String, Integer> queryPlatFormQty() {
		String algoKey = "queryPlatFormQty";
		String selectFields = "aos_subinv.aos_belongshop.number aos_belongshop," +
				"aos_item.number aos_itemnum," +
				"aos_available_qty";
		DataSet dataSet = QueryServiceHelper.queryDataSet(algoKey, "aos_sync_invsub_value", selectFields, new QFilter[]{
				new QFilter("aos_subinv.aos_belongshop", QCP.is_notnull, null)
		}, null);
		dataSet = dataSet.groupBy(new String[]{"aos_belongshop", "aos_itemnum"}).sum("aos_available_qty").finish();

		Map<String, Integer> result = new HashMap<>();
		while (dataSet.hasNext()) {
			Row next = dataSet.next();
			String aos_belongshop = next.getString("aos_belongshop");
			String aos_itemnum = next.getString("aos_itemnum");
			Integer aos_available_qty = next.getInteger("aos_available_qty");
			result.put(aos_belongshop+aos_itemnum, aos_available_qty);
		}

		selectFields = "aos_item.number aos_itemnum," +
				"aos_available_qty";
		DataSet dsvDataSet = QueryServiceHelper.queryDataSet(algoKey, "aos_sync_invsub_value", selectFields, new QFilter[]{
				new QFilter("aos_subinv.name", QCP.equals, "DSV")
		}, null);

		dsvDataSet = dsvDataSet.groupBy(new String[]{"aos_itemnum"}).sum("aos_available_qty").finish();

		while (dsvDataSet.hasNext()) {
			Row next = dsvDataSet.next();
			String aos_itemnum = next.getString("aos_itemnum");
			Integer aos_available_qty = next.getInteger("aos_available_qty");
			result.put("IT-eMag" + aos_itemnum, aos_available_qty);
			result.put("IT-eMAG.BG" + aos_itemnum, aos_available_qty);
			result.put("IT-emag.hu" + aos_itemnum, aos_available_qty);
			result.put("IT-Vivre.ro" + aos_itemnum, aos_available_qty);
		}
		return result;
	}

	@Override
	protected void beforeSave(List<ImportBillData> billdatas, ImportLogger logger) {
		Before();
		Calendar date = Calendar.getInstance();
		int month = date.get(Calendar.MONTH) + 1;// 当前月
		int row=0;
		try {
			for (ImportBillData data : billdatas) {
				row++;
				JSONObject object = data.getData();
				String item_number = ((JSONObject) object.get("aos_itemid")).getString("number");
				String item_id = ((JSONObject) object.get("aos_itemid")).getString("id");
				String org_number = ((JSONObject) object.get("aos_orgid")).getString("number");
				String shop = ((JSONObject) object.get("aos_shop")).getString("number");
				String orgid_str = "";
				String itemid_str = "";
				String key = org_number + "~" + item_number;
				System.out.println(key);
				// 类别
				String itemCategoryName = ItemCategory.get(item_number);
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
				// 大类
				object.put("aos_category1", aos_category1);
				// 中类
				object.put("aos_category2", aos_category2);
				// 小类
				object.put("aos_category3", aos_category3);
				// 供应链相关库存

				Map<String, Object> item_ouqty = item.get("item_ouqty");


				// 平台仓库数量
				Integer platQty = platQtyMap.getOrDefault(shop + item_number, 0);
				// 非平台仓数量
				Integer noPlatQty = noPlatQtyMap.getOrDefault(org_number + item_number, 0);
//			int selfQty = (int) item_ouqty.getOrDefault(key + "_NOPLT", 0);
				// 海外库存
				int overseaQty = platQty+noPlatQty;
				object.put("aos_subqty", platQty);
				object.put("aos_ownstorage", noPlatQty);// 自有仓库库存

				if (org_number.equals("IE"))
					overseaQty = IeInstockQty.getOrDefault(item_id,0);
				object.put("aos_osqty", overseaQty);

				Map<String, Object> item_inprocess = item.get("item_inprocess");
				object.put("aos_inprocessqty", item_inprocess.get(key));

				Map<String, Object> item_intransqty = item.get("item_intransqty");
				object.put("aos_intransqty", item_intransqty.get(key));


				// 物料信息
				Map<String, Object> ItemInfoD = ItemInfo.get(key);
				if (ItemInfoD != null) {
					object.put("aos_itemname", ItemInfoD.get("name"));
					object.put("aos_season", ItemInfoD.get("aos_season"));
					object.put("aos_itemstatus", ItemInfoD.get("aos_item_status"));
					object.put("aos_brand", ItemInfoD.get("aos_contrybrand"));
					object.put("aos_spec", ItemInfoD.get("aos_spec"));
					object.put("aos_firstdate", ItemInfoD.get("aos_firstindate"));
				// 爆品
				object.put("aos_is_saleout", ItemInfoD.get("aos_is_saleout"));

					orgid_str = ItemInfoD.get("org_id") + "";
					itemid_str = ItemInfoD.get("item_id") + "";

					float SeasonRate = MKTCom.Get_SeasonRate((long) ItemInfoD.get("org_id"),
							(long) ItemInfoD.get("item_id"), ItemInfoD.get("aos_season") + "", item_ouqty.getOrDefault(key + "_OS",0),
							month);
					BigDecimal SeasonRateB = new BigDecimal(SeasonRate).setScale(2, BigDecimal.ROUND_HALF_UP);
					object.put("aos_seasonrate", SeasonRateB);
					float aos_7days_sale = InStockAvailableDays.getOrgItemOnlineAvgQty(orgid_str, itemid_str);
					BigDecimal aos_7days_saleB = new BigDecimal(aos_7days_sale).setScale(2, BigDecimal.ROUND_HALF_UP);
					int availableDays = InStockAvailableDays.calInstockSalDays(orgid_str, itemid_str);
					object.put("aos_orgavg7", aos_7days_saleB);
					object.put("aos_avadays", availableDays);
					BigDecimal inventoryCost = costMap.getOrDefault(orgid_str + itemid_str, BigDecimal.ZERO);
					object.put("aos_cost", inventoryCost);
					BigDecimal primeExpress = primeMap.getOrDefault(orgid_str + itemid_str, BigDecimal.ZERO);
					object.put("aos_prime", primeExpress);

					object.put("aos_itemage", itemCacheService.getItemMinAge(Long.parseLong(orgid_str), Long.parseLong(itemid_str)));
					object.put("aos_completeqty", deStockQtyMap.getOrDefault(orgid_str + "~" + itemid_str, BigDecimal.ZERO).intValue());
				}
				// 原价现价
				Map<String, Object> ItemPriceD = ItemPrice.get(key);
				if (ItemPriceD != null) {
					object.put("aos_preprice", ItemPriceD.get("aos_regular_amount"));
					object.put("aos_nowprice", ItemPriceD.get("aos_currentprice"));
				}

				// 官网价格
				Map<String, Object> ItemPriceWebD = ItemPriceWeb.get(key);
				if (ItemPriceWebD != null) {
					object.put("aos_webprice", ItemPriceWebD.get("aos_regular_amount"));
				}

				// 预计入库日期
				object.put("aos_predate", ItemArrival.get(key));

				// 快递费
				object.put("aos_shipfee", ShipFee.get(key));

				// 获取店铺7天销量
				if (Order7Shop.get(key+"~"+shop) != null) {
					object.put("aos_decimalfield", BigDecimal.valueOf(Order7Shop.get(key+"~"+shop)/7) );
				}
				object.put("aos_fba", FbaShipFee.get(key));

			}
		}
		catch (Exception e){
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			log.error(sw.toString());
			throw new KDException(new ErrorCode("选品信息查询异常",row+"行  :"+e.getMessage()));
		}
	}

	private Map<String, Integer> getMaxAgeMap() {
		DynamicObjectCollection aos_sync_itemage = QueryServiceHelper.query("aos_sync_itemage", "aos_orgid.number aos_orgnum, aos_itemid.number aos_itemnum, aos_item_maxage", null);
		return aos_sync_itemage.stream().collect(Collectors.toMap(
				obj -> obj.getString("aos_orgnum") + obj.getString("aos_itemnum"),
				obj -> obj.getInt("aos_item_maxage"), (k1, k2) -> k1));
	}

	private HashMap<String, Map<String, Object>> GenerateItemPriceWeb() {
		QFilter filter_platform = new QFilter("aos_platformfid.number", "=", "WEB");
		QFilter filter_mainshop = new QFilter("aos_shopfid.aos_is_mainshop", "=", true);
		QFilter[] filters = new QFilter[] { filter_platform, filter_mainshop };
		HashMap<String, Map<String, Object>> ItemInfo = new HashMap<>();
		String SelectColumn = "aos_orgid.number ou_code," + "aos_item_code.number item_number," + "aos_regular_amount";
		DynamicObjectCollection aos_sync_invpriceS = QueryServiceHelper.query("aos_sync_invprice", SelectColumn,
				filters);
		for (DynamicObject aos_sync_invprice : aos_sync_invpriceS) {
			HashMap<String, Object> Info = new HashMap<>();
			Info.put("aos_regular_amount", aos_sync_invprice.get("aos_regular_amount"));// 成交价
			ItemInfo.put(aos_sync_invprice.getString("ou_code") + "~" + aos_sync_invprice.getString("item_number"),
					Info);
		}
		System.out.println("=====结束官网价格初始化=====");
		return ItemInfo;
	}

	private HashMap<String, Map<String, Object>> GenerateItemPrice() {
		QFilter filter_platform = new QFilter("aos_platformfid.number", "=", "AMAZON");
		QFilter filter_mainshop = new QFilter("aos_shopfid.aos_is_mainshop", "=", true);
		QFilter[] filters = new QFilter[] { filter_platform, filter_mainshop };
		HashMap<String, Map<String, Object>> ItemInfo = new HashMap<>();
		String SelectColumn = "aos_orgid.number ou_code," + "aos_item_code.number item_number," + "aos_regular_amount,"
				+ "aos_currentprice ";
		DynamicObjectCollection aos_sync_invpriceS = QueryServiceHelper.query("aos_sync_invprice", SelectColumn,
				filters);
		for (DynamicObject aos_sync_invprice : aos_sync_invpriceS) {
			HashMap<String, Object> Info = new HashMap<>();
			Info.put("aos_regular_amount", aos_sync_invprice.get("aos_regular_amount"));// 原价
			Info.put("aos_currentprice", aos_sync_invprice.get("aos_currentprice"));// 成交价
			ItemInfo.put(aos_sync_invprice.getString("ou_code") + "~" + aos_sync_invprice.getString("item_number"),
					Info);
		}
		System.out.println("=====结束每日价格初始化=====");
		return ItemInfo;
	}

	public static Map<String, BigDecimal> GenerateShipFee() {
		Map<String, BigDecimal> map = new HashMap<>();
		DynamicObjectCollection dyns = QueryServiceHelper.query("aos_quo_skuquery",
				"aos_orgid.number aos_orgid,aos_lowest_fee,aos_itemid.number aos_itemid", null);
		for (DynamicObject d : dyns) {
			BigDecimal aos_lowest_fee = d.getBigDecimal("aos_lowest_fee");
			String aos_item_code = d.getString("aos_itemid");
			String aos_ou_code = d.getString("aos_orgid");
			map.put(aos_ou_code + "~" + aos_item_code, aos_lowest_fee);
		}
		return map;
	}

	public static Map<String, BigDecimal> GenerateFbaShipFee() {
		Map<String, BigDecimal> map = new HashMap<>();
		DynamicObjectCollection dyns = QueryServiceHelper.query("aos_quo_skuquery",
				"aos_orgid.number aos_orgid,aos_fba_fee,aos_itemid.number aos_itemid", null);
		for (DynamicObject d : dyns) {
			BigDecimal aos_fba_fee = d.getBigDecimal("aos_fba_fee");
			String aos_item_code = d.getString("aos_itemid");
			String aos_ou_code = d.getString("aos_orgid");
			map.put(aos_ou_code + "~" + aos_item_code, aos_fba_fee);
		}
		return map;
	}

	private Map<String, String> GenerateCategory() {
		Map<String, String> categoryNameMap = null;
		if (categoryNameMap == null || categoryNameMap.size() == 0) {
			String selectFields = "material.number item_number,group.name categoryname";
			DynamicObjectCollection list = QueryServiceHelper.query("bd_materialgroupdetail", selectFields,
					new QFilter[] { new QFilter("standard.name", QCP.equals, "物料基本分类标准") });
			categoryNameMap = new HashMap<>();
			for (DynamicObject obj : list) {
				String item_number = obj.getString("item_number");
				String categoryName = obj.getString("categoryname");
				if (categoryName == null || "".equals(categoryName) || "null".equals(categoryName))
					continue;
				categoryNameMap.put(item_number, categoryName);
			}
		}
		System.out.println("=====结束物料类别初始化=====");
		return categoryNameMap;
	}

	private HashMap<String, Map<String, Object>> GenerateItemInfo() {
		HashMap<String, Map<String, Object>> ItemInfo = new HashMap<>();
		String SelectColumn = "id item_id," + "number," + "name," + "aos_contryentry.aos_nationality org_id,"
				+ "aos_contryentry.aos_nationality.number ou_code,"
				+ "aos_contryentry.aos_contryentrystatus aos_item_status,"
				+ "aos_contryentry.aos_seasonseting.name aos_season,"
				+ "aos_contryentry.aos_festivalseting.name aos_festivalseting,"
				+ "aos_contryentry.aos_contrybrand.number aos_contrybrand," + "aos_specification_cn aos_spec,"
				+ "aos_contryentry.aos_firstindate aos_firstindate,"
				+ "aos_contryentry.aos_is_saleout aos_is_saleout ";
		DynamicObjectCollection bd_materialS = QueryServiceHelper.query("bd_material", SelectColumn, null);
		for (DynamicObject bd_material : bd_materialS) {
			HashMap<String, Object> Info = new HashMap<>();
			Info.put("number", bd_material.get("number"));
			Info.put("name", bd_material.get("name"));
			String aos_item_status = bd_material.getString("aos_item_status");
			if (aos_item_status == null)
				continue;
			switch (aos_item_status) {
			case "A":
				aos_item_status = "新品首单";
				break;
			case "B":
				aos_item_status = "正常";
				break;
			case "C":
				aos_item_status = "终止";
				break;
			case "D":
				aos_item_status = "异常";
				break;
			case "E":
				aos_item_status = "入库新品";
				break;
			case "F":
				aos_item_status = "代卖";
				break;
			case "G":
				aos_item_status = "冻结";
				break;
			}
			Info.put("aos_item_status", aos_item_status);
			Info.put("org_id", bd_material.get("org_id"));
			Info.put("item_id", bd_material.get("item_id"));
			Info.put("aos_season", bd_material.get("aos_season"));
			Info.put("aos_festivalseting", bd_material.get("aos_festivalseting"));
			Info.put("aos_contrybrand", bd_material.get("aos_contrybrand"));
			Info.put("aos_spec", bd_material.get("aos_spec"));
			Info.put("aos_firstindate", bd_material.get("aos_firstindate"));

			Info.put("aos_is_saleout", bd_material.get("aos_is_saleout"));
			
			
			ItemInfo.put(bd_material.getString("ou_code") + "~" + bd_material.getString("number"), Info);
		}
		System.out.println("=====结束物料信息初始化=====");
		return ItemInfo;
	}

	private static HashMap<String, Map<String, Object>> init_itemorg() {
		HashMap<String, Map<String, Object>> item = new HashMap<>();
		List<Map<String, Object>> maps = init_item_qty();
		item.put("item_ouqty", maps.get(0));// 0 库龄 海外库存(废弃) 非平台仓数量
		item.put("item_inprocess", maps.get(1));// 1 国内在制
		item.put("item_complete", maps.get(2));// 2 完工数量
		item.put("item_intransqty", maps.get(3));// 3 在途数量
		System.out.println("=====结束物料库存初始化=====");
		return item;
	}

	public static List<Map<String, Object>> init_item_qty() {
		// Begin
		List<Map<String, Object>> MapList = new ArrayList<Map<String, Object>>();
		String SelectColumn = null;
		// 0.库龄
		Map<String, Object> item_ouqty = new HashMap<String, Object>();
		SelectColumn = "aos_ou.number org_number," + "aos_item.number item_number," + "aos_item_minage,"
				+ "aos_instock_qty," + "aos_noplatform_qty";
		DynamicObjectCollection aos_sync_invou_valueS = QueryServiceHelper.query("aos_sync_invou_value", SelectColumn,
				null);
		for (DynamicObject aos_sync_invou_value : aos_sync_invou_valueS) {
			String org_number = aos_sync_invou_value.getString("org_number");
			String item_number = aos_sync_invou_value.getString("item_number");
			String key = org_number + "~" + item_number;
			int aos_item_minage = aos_sync_invou_value.getInt("aos_item_minage");
			int aos_instock_qty = aos_sync_invou_value.getInt("aos_instock_qty");
			int aos_noplatform_qty = aos_sync_invou_value.getInt("aos_noplatform_qty");
			item_ouqty.put(key + "_AGE", aos_item_minage);
			item_ouqty.put(key + "_OS", aos_instock_qty);
			item_ouqty.put(key + "_NOPLT", aos_noplatform_qty);
		}

		// 1.国内在制
		Map<String, Object> item_inprocess = new HashMap<String, Object>();
		SelectColumn = "aos_orgid.number org_number,aos_itemid.number item_number,aos_inprocess_qty+0 aos_inprocess_qty";
		QFilter filter_qty = new QFilter("aos_inprocess_qty+0", ">", 0);
		QFilter[] filters = new QFilter[] { filter_qty };
		DataSet aos_sync_poS = QueryServiceHelper.queryDataSet("InvOu.aos_sync_poS2", "aos_sync_po", SelectColumn,
				filters, null);
		String GroupBy[] = new String[] { "org_number", "item_number" };
		aos_sync_poS = aos_sync_poS.groupBy(GroupBy).sum("aos_inprocess_qty").finish();
		while (aos_sync_poS.hasNext()) {
			Row aos_sync_po = aos_sync_poS.next();
			String aos_orgid = aos_sync_po.getString("org_number");
			String aos_itemid = aos_sync_po.getString("item_number");
			int aos_inprocess_qty = aos_sync_po.getInteger("aos_inprocess_qty");
			item_inprocess.put(aos_orgid + "~" + aos_itemid, aos_inprocess_qty);
		}
		aos_sync_poS.close();

		// 2.完工数量
		Map<String, Object> item_complete = new HashMap<>();
		SelectColumn = "aos_entryentity.aos_salecountry.number org_number,"
				+ "aos_entryentity.aos_materiel.number item_number,"
				+ "aos_entryentity.aos_completedqua aos_completedqua";
		filter_qty = new QFilter("aos_entryentity.aos_completedqua", ">", 0);
		filters = new QFilter[] { filter_qty };
		DataSet aos_purcontractS = QueryServiceHelper.queryDataSet("InvOu.aos_purcontract", "aos_purcontract",
				SelectColumn, filters, null);
		aos_purcontractS = aos_purcontractS.groupBy(GroupBy).sum("aos_completedqua").finish();
		while (aos_purcontractS.hasNext()) {
			Row aos_purcontract = aos_purcontractS.next();
			String aos_orgid = aos_purcontract.getString("org_number");
			String aos_itemid = aos_purcontract.getString("item_number");
			int aos_completedqua = aos_purcontract.getInteger("aos_completedqua");
			item_complete.put(aos_orgid + "~" + aos_itemid, aos_completedqua);
		}
		aos_purcontractS.close();

		// 3.在途数量
		Map<String, Object> item_intransqty = new HashMap<String, Object>();
		SelectColumn = "aos_orgid.number org_number,aos_itemid.number item_number,aos_ship_qty";
		filter_qty = new QFilter("aos_ship_qty", ">", 0);
		QFilter filter_flag = new QFilter("aos_os_flag", "=", "N");
		QFilter filter_status = new QFilter("aos_shp_status", "=", "SHIPPING").or("aos_shp_status", "=", "PACKING");
		filters = new QFilter[] { filter_qty, filter_flag, filter_status };
		DataSet aos_sync_shpS = QueryServiceHelper.queryDataSet("InvOu.aos_sync_shpS", "aos_sync_shp", SelectColumn,
				filters, null);
		aos_sync_shpS = aos_sync_shpS.groupBy(GroupBy).sum("aos_ship_qty").finish();
		while (aos_sync_shpS.hasNext()) {
			Row aos_sync_shp = aos_sync_shpS.next();
			String aos_orgid = aos_sync_shp.getString("org_number");
			String aos_itemid = aos_sync_shp.getString("item_number");
			int aos_ship_qty = aos_sync_shp.getInteger("aos_ship_qty");
			item_intransqty.put(aos_orgid + "~" + aos_itemid, aos_ship_qty);
		}
		aos_sync_shpS.close();

		MapList.add(item_ouqty);// 0 库龄 海外库存 平台仓数量
		MapList.add(item_inprocess);// 1 国内在制
		MapList.add(item_complete);// 2 完工数量
		MapList.add(item_intransqty);// 3 在途数量

		return MapList;
	}

	/**
	 *
	 * @return 当前国别下 所有国别物料的预计入库日期
	 */
	public static HashMap<String, Date> queryExpectedArrivalDate() {
		String[] status = {"SHIPPING", "PACKING"};
		DataSet actDateDataSet = QueryServiceHelper.queryDataSet("queryExpectedArrivalDate_actdate", "aos_sync_shp", "aos_orgid.number aos_orgnum,aos_itemid.number aos_itemnum,aos_act_date ect_arrival_date", new QFilter[]{
				new QFilter("aos_os_flag", QCP.equals, "N"),
				new QFilter("aos_shp_status", QCP.in, status),
				new QFilter("aos_act_date", QCP.is_notnull, null)
		}, null);

		DataSet estDateDataSet = QueryServiceHelper.queryDataSet("queryExpectedArrivalDate_actdate", "aos_sync_shp", "aos_orgid.number aos_orgnum,aos_itemid.number aos_itemnum,aos_est_date ect_arrival_date", new QFilter[]{
				new QFilter("aos_os_flag", QCP.equals, "N"),
				new QFilter("aos_shp_status", QCP.in, status),
				new QFilter("aos_act_date", QCP.is_null, null),
				new QFilter("aos_est_date", QCP.is_notnull, null)
		}, null);

		DataSet expectedArrivalDateDataSet = actDateDataSet.union(estDateDataSet);
		expectedArrivalDateDataSet = expectedArrivalDateDataSet.groupBy(new String[]{"aos_orgnum", "aos_itemnum"}).min("ect_arrival_date").finish();
		actDateDataSet.close();
		estDateDataSet.close();
		HashMap<String, Date> result = new HashMap<>();
		Calendar instance = Calendar.getInstance();
		instance.set(Calendar.HOUR_OF_DAY, 0);
		instance.set(Calendar.MINUTE, 0);
		instance.set(Calendar.SECOND, 0);
		Date currentDate = instance.getTime();
		while (expectedArrivalDateDataSet.hasNext()) {
			Row next = expectedArrivalDateDataSet.next();
			String aos_orgnum = next.getString("aos_orgnum");
			String aos_itemnum = next.getString("aos_itemnum");
			Date ect_arrival_date = next.getDate("ect_arrival_date");
			ect_arrival_date = DateUtils.addDays(ect_arrival_date, +7);// 预计入库日期
			ect_arrival_date = ect_arrival_date.before(currentDate) ? currentDate : ect_arrival_date;//iii.	如果上面计算的预计入库时间在今天以前的，修正到今天
			result.put(aos_orgnum+ "~" + aos_itemnum, ect_arrival_date);
		}
		return result;
	}


	
	public static HashMap<String, Integer> GenerateOrder7Shop() {
		HashMap<String, Integer> Order7Shop = new HashMap<>();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		Calendar today = Calendar.getInstance();
		today.set(Calendar.HOUR_OF_DAY, 0);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.SECOND, 0);
		today.set(Calendar.MILLISECOND, 0);
		today.add(Calendar.DAY_OF_MONTH, -7);
		QFilter filter_date = new QFilter("aos_order_date", QCP.large_equals, sdf.format(today.getTime()));
		List<QFilter> orderFilter = SalUtil.getOrderFilter();
		orderFilter.add(filter_date);
		QFilter[] filters = orderFilter.toArray(new QFilter[orderFilter.size()]);
		DataSet orderDataSet = QueryServiceHelper.queryDataSet("aos_mkt_act_query_imp.GenerateOrderMonth",
				"aos_sync_om_order_r", "aos_org.number aos_org,aos_item_fid.number aos_item_fid,aos_order_qty,aos_shopfid.number aos_shopfid", filters, null);
		orderDataSet = orderDataSet.groupBy(new String[] { "aos_org", "aos_item_fid","aos_shopfid" }).sum("aos_order_qty").finish();
		while (orderDataSet.hasNext()) {
			Row aos_sync_omonth_summary = orderDataSet.next();
			Order7Shop.put(aos_sync_omonth_summary.getString("aos_org") +"~"+
			aos_sync_omonth_summary.getString("aos_item_fid")
			+ "~" + aos_sync_omonth_summary.getString("aos_shopfid"),
					aos_sync_omonth_summary.getInteger("aos_order_qty"));
		}
		orderDataSet.close();
		System.out.println("====结束7日店铺订单销量====");
		return Order7Shop;
	}

	private static Map<String, Integer> getNoPlatQty() {
		String algoKey = "getNoPlatQty";
		String selectFields = "aos_ou.number aos_orgnum,aos_item.number aos_itemnum," +
				"aos_available_qty";
		DataSet dataSet = QueryServiceHelper.queryDataSet(algoKey, "aos_sync_invsub_value", selectFields, new QFilter[]{
				new QFilter("aos_subinv.aos_belongshop", QCP.is_null, null).and(new QFilter("aos_subinv.name", QCP.not_equals, "DSV"))
		}, null);
		dataSet = dataSet.groupBy(new String[]{"aos_orgnum","aos_itemnum"}).sum("aos_available_qty").finish();

		Map<String, Integer> result = new HashMap<>();
		while (dataSet.hasNext()) {
			Row next = dataSet.next();
			String aos_orgnum = next.getString("aos_orgnum");
			String aos_itemnum = next.getString("aos_itemnum");
			Integer aos_available_qty = next.getInteger("aos_available_qty");
			result.put(aos_orgnum + aos_itemnum, aos_available_qty);
		}
		return result;
	}

	private Map<String, BigDecimal> queryPrimeExpress() {
		String algoKey = this.getClass().getName() + "queryPrimeExpress";

		Map<String, BigDecimal> result = new HashMap<>();
		DataSet dataSet = QueryServiceHelper.queryDataSet(algoKey, "aos_expressresult", "aos_org,aos_mat,aos_rev_fee",
				new QFilter("aos_suittype.number", QCP.equals, "Prime").toArray(), null);
		dataSet = dataSet.groupBy(new String[]{"aos_org", "aos_mat"}).min("aos_rev_fee").finish();
		while (dataSet.hasNext()) {
			Row next = dataSet.next();
			String aos_orgid = next.getString("aos_org");
			String aos_itemid = next.getString("aos_mat");
			BigDecimal aos_rev_fee = next.getBigDecimal("aos_rev_fee");
			result.put(aos_orgid+aos_itemid, aos_rev_fee);
		}
		return result;
	}

	private static final String AOS_PREPARATIONNOTE = "aos_preparationnote";  //备货单
	private static final String AOS_PURCONTRACT = "aos_purcontract";  //购销合同
	/**
	 * 国内库存：合同行状态含有关闭：已接收数量+调整数量-备货单状态为已装箱或已出运的出运数量；合同行状态为打开；
	 * 已完工数量+调整数量-备货单状态为已装箱或已出运的出运数量
	 *
	 * @return
	 */
	public static Map<String, BigDecimal> calInventory() {
		DataSet dataSet = QueryServiceHelper.queryDataSet("aos_scm_fcorder_shipl_cal_getSupplier",
				AOS_PURCONTRACT, " aos_entryentity.aos_materiel aos_materiel , aos_entryentity.aos_salecountry  aos_salecountry , billno , " +
						"  aos_entryentity.aos_completedqua aos_completedqua ,  aos_entryentity.aos_receivedqua  aos_receivedqua  , " +
						"   aos_entryentity.aos_adjustqua  aos_adjustqua ,  aos_entryentity.aos_linenumber aos_linenumber     ",
				new QFilter[]{
						new QFilter("billstatus", QCP.equals, "C"),
						new QFilter("aos_entryentity.aos_linestatus", QCP.equals, "0"),
						//new QFilter("aos_entryentity.aos_salecountry", QCP.equals, aos_org)
				}, "aos_entryentity.aos_materiel asc ");


		DataSet dataSet1 = queryShipqtyForPurcontract1();

		DataSet oneDataSet = dataSet.leftJoin(dataSet1).on("billno", "aos_sto_contractno").on("aos_linenumber", "aos_sto_lineno").on("aos_materiel", "aos_sto_artno").select(
				new String[]{
						"aos_salecountry", "aos_materiel", "aos_completedqua", "aos_receivedqua", "aos_adjustqua"
				}, new String[]{
						" case when stockqty  is null  then 0 else stockqty  end  stockqty"
				}
		).finish().select(new String[]{
				"aos_salecountry", "aos_materiel", "aos_completedqua", "aos_receivedqua", "aos_adjustqua",
				" case when stockqty  is null  then 0 else stockqty  end  stockqty"
		}).select(new String[]{
				"aos_salecountry",
				"aos_materiel",
				"aos_completedqua - aos_receivedqua - stockqty as inStockQty  "
		});


		DataSet preDataSet2 = queryShipqtyForPurcontract2(); //备货单已出运
		DataSet preDataSet3 = queryShipqtyForPurcontract3(); //备货单已装箱非供应商仓库
		/**
		 * 在库数量:已接收数量+调整数量-备货单状态为“已出运”的出运数量-备货单状态为“已装箱”且备货单明细表中“货物地点”非“供应商仓库”的“出运数量” [合同号+行号对应]
		 */
		DataSet pur_dataSet = QueryServiceHelper.queryDataSet("aos_scm_fcorder_shipl_cal_getSupplier2",
				AOS_PURCONTRACT, " aos_entryentity.aos_materiel aos_materiel , aos_entryentity.aos_salecountry  aos_salecountry , billno , " +
						"  aos_entryentity.aos_receivedqua  aos_receivedqua  , " +
						"  aos_entryentity.aos_adjustqua aos_adjustqua , " +
						"  aos_entryentity.aos_linenumber  aos_linenumber   ",
				new QFilter[]{
						new QFilter("billstatus", QCP.equals, "C"),
						//new QFilter("aos_entryentity.aos_salecountry", QCP.equals, aos_org)
				}
				, "aos_entryentity.aos_materiel asc  ");


		DataSet preResultData = pur_dataSet.leftJoin(preDataSet2).on("billno", "aos_sto_contractno").on("aos_linenumber", "aos_sto_lineno").on("aos_materiel", "aos_sto_artno").select(
				new String[]{
						"aos_salecountry", "aos_materiel", "aos_receivedqua", "aos_adjustqua", "billno", "aos_linenumber"
				}, new String[]{
						" case when stockqty2  is null  then 0 else stockqty2  end  stockqty2"
				}
		).finish().select(new String[]{
				"aos_salecountry",
				"aos_materiel",
				"aos_receivedqua",
				"aos_adjustqua",
				"case when stockqty2  is null  then 0 else stockqty2  end  stockqty2",
				"billno",
				"aos_linenumber"
		});

		DataSet preResultData2 = preResultData.leftJoin(preDataSet3).on("billno", "aos_sto_contractno").on("aos_linenumber", "aos_sto_lineno").on("aos_materiel", "aos_sto_artno").select(
				new String[]{
						"aos_salecountry", "aos_materiel", "aos_receivedqua", "aos_adjustqua", "stockqty2"
				}, new String[]{
						" case when stockqty3  is null  then 0 else stockqty3  end  stockqty3"
				}
		).finish().select(new String[]{
				"aos_salecountry",
				"aos_materiel",
				"aos_receivedqua",
				"aos_adjustqua",
				"stockqty2",
				"case when stockqty3  is null  then 0 else stockqty3  end  stockqty3"
		});


		DataSet secondDataSet = preResultData2.select(new String[]{
				"aos_salecountry",
				"aos_materiel",
				"aos_receivedqua",
				"aos_adjustqua",
				"stockqty2",
				"stockqty3",
				"aos_receivedqua + aos_adjustqua - stockqty2 -  stockqty3  as inStockQty "
		}).filter("aos_receivedqua + aos_adjustqua - stockqty2 -  stockqty3  > 0 ").select(new String[]{
				"aos_materiel",
				"aos_salecountry",
				"aos_receivedqua + aos_adjustqua - stockqty2 -  stockqty3  as inStockQty"
		});


		DataSet sum2 = secondDataSet.select(new String[]{
				"aos_materiel",
				"aos_salecountry",
				"inStockQty"
		}).groupBy(new String[]{
				"aos_materiel",
				"aos_salecountry"
		}).sum("inStockQty", "inventoryQty").finish().select(new String[]{
				"aos_materiel",
				"aos_salecountry",
				"inventoryQty"
		}).filter("inventoryQty > 0 ");

		DataSet sum1 = oneDataSet.select(new String[]{
				"aos_materiel",
				"aos_salecountry",
				"inStockQty"
		}).groupBy(new String[]{
				"aos_materiel",
				"aos_salecountry"
		}).sum("inStockQty", "inventoryQty").finish().select(new String[]{
				"aos_materiel",
				"aos_salecountry",
				"inventoryQty"
		}).filter("inventoryQty > 0 ");

		Map<String, BigDecimal> result = new HashMap<>();
		while (sum1.hasNext()) {
			Row next = sum1.next();
			String key = next.getString("aos_salecountry") + "~" + next.getString("aos_materiel");
			BigDecimal inventoryQty = next.getBigDecimal("inventoryQty");
			if (result.containsKey(key)) {
				result.put(key, result.get(key).add(inventoryQty));
			} else {
				result.put(key, inventoryQty);
			}
		}
		while (sum2.hasNext()) {
			Row next = sum2.next();
			String key = next.getString("aos_salecountry") + "~" + next.getString("aos_materiel");
			BigDecimal inventoryQty = next.getBigDecimal("inventoryQty");
			if (result.containsKey(key)) {
				result.put(key, result.get(key).add(inventoryQty));
			} else {
				result.put(key, inventoryQty);
			}
		}


		dataSet.close();
		dataSet1.close();
		oneDataSet.close();
		preDataSet2.close();
		preDataSet3.close();
		pur_dataSet.close();
		preResultData.close();
		preResultData2.close();
		secondDataSet.close();
		sum1.close();
		sum2.close();
		return result;
	}


	/**
	 * 备货单为已装箱货物地点为供应商仓库
	 *
	 * @return
	 */
	public static DataSet queryShipqtyForPurcontract1() {
		String algoKey = "FunctionUtils.queryShipqtyForPurcontract1";
		QFilter[] filter = {
				new QFilter("aos_status", QFilter.equals, "D"),
				//new QFilter("aos_soldtocountry",QFilter.in, aos_org),
				new QFilter("aos_stockentry.aos_sto_loadlocat.number", QFilter.equals, "Virtual warehouse")
		};
		String cloum =
				"aos_stockentry.aos_sto_artno as aos_sto_artno ," +
						"aos_stockentry.aos_sto_contractno as aos_sto_contractno ," +
						"aos_stockentry.aos_sto_lineno as aos_sto_lineno ,"
						+ "aos_stockentry.aos_sto_shipmentcount as qty ";
		DataSet dataSet = QueryServiceHelper.queryDataSet(algoKey, AOS_PREPARATIONNOTE, cloum, filter, null)
				.groupBy(new String[]{"aos_sto_artno", "aos_sto_contractno", "aos_sto_lineno"})
				.sum("qty", "stockqty")
				.finish().filter("stockqty > 0 ");
		return dataSet;
	}


	/**
	 * 备货单为已出运
	 *
	 * @return
	 */
	public static DataSet queryShipqtyForPurcontract2() {
		String algoKey = "FunctionUtils.queryShipqtyForPurcontract2";
		QFilter[] filter = {
				new QFilter("aos_status", QFilter.equals, "E"),
				//new QFilter("aos_soldtocountry",QFilter.in, aos_org)
				//new QFilter("aos_stockentry.aos_sto_loadlocat.number",QFilter.not_equals, "Virtual warehouse")
		};
		String cloum =
				"aos_stockentry.aos_sto_artno as aos_sto_artno," +
						"aos_stockentry.aos_sto_contractno as aos_sto_contractno ," +
						"aos_stockentry.aos_sto_lineno as aos_sto_lineno ,"
						+ "aos_stockentry.aos_sto_shipmentcount as qty ";
		DataSet dataSet = QueryServiceHelper.queryDataSet(algoKey, AOS_PREPARATIONNOTE, cloum, filter, null)
				.groupBy(new String[]{"aos_sto_artno", "aos_sto_contractno", "aos_sto_lineno"})
				.sum("qty", "stockqty2")
				.finish().filter("stockqty2 > 0 ");
		return dataSet;
	}

	/**
	 * 备货单为已装箱货物地点为非供应商仓库
	 *
	 * @return
	 */
	public static DataSet queryShipqtyForPurcontract3() {
		String algoKey = "FunctionUtils.queryShipqtyForPurcontract3";
		QFilter[] filter = {
				new QFilter("aos_status", QFilter.equals, "D"),
				//new QFilter("aos_soldtocountry",QFilter.in, aos_org),
				new QFilter("aos_stockentry.aos_sto_loadlocat.number", QFilter.not_equals, "Virtual warehouse")
		};
		String cloum =
				"aos_stockentry.aos_sto_artno as aos_sto_artno," +
						"aos_stockentry.aos_sto_contractno as aos_sto_contractno ," +
						"aos_stockentry.aos_sto_lineno as aos_sto_lineno ,"
						+ "aos_stockentry.aos_sto_shipmentcount as qty ";
		DataSet dataSet = QueryServiceHelper.queryDataSet(algoKey, AOS_PREPARATIONNOTE, cloum, filter, null)
				.groupBy(new String[]{"aos_sto_artno", "aos_sto_contractno", "aos_sto_lineno"})
				.sum("qty", "stockqty3")
				.finish().filter("stockqty3 > 0 ");
		return dataSet;
	}

	/** 计算IE的海外库存 **/
	public static Map<String,Integer> getIeInStockQty(){
		Map<String ,Integer> result = new HashMap<>();
		QFBuilder builder = new QFBuilder("aos_ou.number","=","IE");
		builder.add("aos_item","!=","");
		builder.add("aos_instock_qty",">",0);
		DynamicObjectCollection dyc = QueryServiceHelper.query("aos_sync_invou_value", "aos_item,aos_instock_qty", builder.toArray());
		for (DynamicObject dy : dyc) {
			String item = dy.getString("aos_item");
			int qty = result.getOrDefault(item, 0);
			qty += dy.getInt("aos_instock_qty");
			result.put(item,qty);
		}
		return result;
	}

}