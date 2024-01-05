package mkt.synciface;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import common.Cux_Common_Utl;
import common.fnd.FndGlobal;
import common.fnd.FndMsg;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.db.DB;
import kd.bos.db.DBRoute;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import mkt.progress.listing.AosMktListingReqBill;

/** 工作流定时信息同步 **/
public class aos_mkt_item_sync extends AbstractTask {

	public final static String EIPDB = Cux_Common_Utl.EIPDB;

	@Override
	public void execute(RequestContext arg0, Map<String, Object> arg1) throws KDException {
		do_operate();
	}

	public static void do_operate() {
		FndMsg.debug("===============物料数据初始化===============");
		HashMap<String, Map<String, String>> itemMap = generateItem();
		FndMsg.debug("===============文案数据初始化===============");
		HashMap<String, String> son = generateSon();
		FndMsg.debug("===============设计需求数据初始化===============");
		HashMap<String, String> design = generateDesign();
		// 1.拍照需求表
		DynamicObject[] aos_mkt_photoreqS = BusinessDataServiceHelper.load("aos_mkt_photoreq", "id",
				new QFilter("aos_status", QCP.not_equals, "已完成").toArray());
		for (DynamicObject aos_mkt_photoreqID : aos_mkt_photoreqS) {
			Object fid = aos_mkt_photoreqID.get("id");
			DynamicObject aos_mkt_photoreq = BusinessDataServiceHelper.loadSingleFromCache(fid, "aos_mkt_photoreq");
			String item = aos_mkt_photoreq.getDynamicObject("aos_itemid").getString("number");
			if (FndGlobal.IsNull(itemMap.get(item))) {
				continue;
			}
			aos_mkt_photoreq.set("aos_itemname", itemMap.get(item).get("name"));
			aos_mkt_photoreq.set("aos_seting1", itemMap.get(item).get("aos_seting_cn"));
			aos_mkt_photoreq.set("aos_specification", itemMap.get(item).get("aos_specification_cn"));
			aos_mkt_photoreq.set("aos_sellingpoint", itemMap.get(item).get("aos_sellingpoint"));
			SaveServiceHelper.saveOperate("aos_mkt_photoreq", new DynamicObject[] { aos_mkt_photoreq },
					OperateOption.create());
		}
		// 2.设计需求表
		DynamicObject[] aos_mkt_designreqS = BusinessDataServiceHelper.load("aos_mkt_designreq", "id",
				new QFilter("aos_status", QCP.not_equals, "结束").toArray());
		for (DynamicObject aos_mkt_designreqID : aos_mkt_designreqS) {
			Object fid = aos_mkt_designreqID.get("id");
			DynamicObject aos_mkt_designreq = BusinessDataServiceHelper.loadSingleFromCache(fid, "aos_mkt_designreq");
			DynamicObjectCollection aos_entryentityS = aos_mkt_designreq.getDynamicObjectCollection("aos_entryentity");
			for (DynamicObject aos_entryentity : aos_entryentityS) {
				DynamicObjectCollection aos_subentryentityS = aos_entryentity
						.getDynamicObjectCollection("aos_subentryentity");
				for (DynamicObject aos_subentryentity : aos_subentryentityS) {
					DynamicObject aos_sub_item = aos_subentryentity.getDynamicObject("aos_sub_item");
					if (FndGlobal.IsNull(aos_sub_item))
						continue;
					String item = aos_sub_item.getString("number");
					if (FndGlobal.IsNull(itemMap.get(item)))
						continue;
					aos_subentryentity.set("aos_itemname", itemMap.get(item).get("name"));
					aos_subentryentity.set("aos_seting1", itemMap.get(item).get("aos_seting_cn"));
					aos_subentryentity.set("aos_spec", itemMap.get(item).get("aos_specification_cn"));
					aos_subentryentity.set("aos_sellingpoint", itemMap.get(item).get("aos_sellingpoint"));
				}
			}
			SaveServiceHelper.saveOperate("aos_mkt_designreq", new DynamicObject[] { aos_mkt_designreq },
					OperateOption.create());
		}

		// 3.listing优化需求表
		DynamicObject[] aos_mkt_listing_reqS = BusinessDataServiceHelper.load("aos_mkt_listing_req", "id",
				new QFilter("aos_status", QCP.equals, "已完成")
						.and("aos_requiredate", QCP.large_equals, LocalDate.now().minusDays(30).toString()).toArray());
		for (DynamicObject aos_mkt_listing_reqID : aos_mkt_listing_reqS) {
			String fid = aos_mkt_listing_reqID.getString("id");
			DynamicObject aos_mkt_listing_req = BusinessDataServiceHelper
					.loadSingleFromCache(aos_mkt_listing_reqID.get("id"), "aos_mkt_listing_req");

			if (FndGlobal.IsNull(aos_mkt_listing_req))
				continue;
			FndMsg.debug(fid);

			DynamicObjectCollection aos_entryentityS = aos_mkt_listing_req
					.getDynamicObjectCollection("aos_entryentity");
			for (DynamicObject aos_entryentity : aos_entryentityS) {
				// 英语文案完成情况
				aos_entryentity.set("aos_enstatus", son.get(fid));
				// DE文案翻译情况
				aos_entryentity.set("aos_destatus", getMin("DE", fid));
				// FR文案翻译情况
				aos_entryentity.set("aos_frstatus", getMin("FR", fid));
				// IT文案翻译情况
				aos_entryentity.set("aos_itstatus", getMin("IT", fid));
				// ES文案翻译情况
				aos_entryentity.set("aos_esstatus", getMin("ES", fid));
				// 品类设计完成情况
				aos_entryentity.set("aos_signstatus", design.get(fid));
				// DE图片翻译情况
				aos_entryentity.set("aos_depic", getPic("DE", fid));
				// FR图片翻译情况
				aos_entryentity.set("aos_frpic", getPic("FR", fid));
				// IT图片翻译情况
				aos_entryentity.set("aos_itpic", getPic("IT", fid));
				// ES图片翻译情况
				aos_entryentity.set("aos_espic", getPic("ES", fid));
			}
			AosMktListingReqBill.setItemCate(aos_mkt_listing_req);
			SaveServiceHelper.saveOperate("aos_mkt_listing_req", new DynamicObject[] { aos_mkt_listing_req },
					OperateOption.create());
		}
	}

	private static Object getMin(String org, String fid) {
		Object status = null;
		String sql = " select taml.fk_aos_status from  tk_aos_mkt_listing_min taml,"
				+ " tk_aos_mkt_listing_son tamls  ,tk_aos_mkt_listing_req tamlq," + EIPDB + ".T_BD_Country tbc"
				+ " where 1=1 " + " and tamls.fid = taml.fk_aos_sourceid " + " and tamlq.fid = tamls.fk_aos_sourceid "
				+ " and tbc.fid = taml.fk_aos_orgid " + " and tbc.fnumber = ? " + " and tamlq.fid = ? ";
		Object[] params = { org, fid };
		DataSet ds = DB.queryDataSet("getPic" + org, DBRoute.of("aos.mkt"), sql, params);
		while (ds.hasNext()) {
			Row row = ds.next();
			status = row.get(0);
		}
		return status;
	}

	/**
	 * 获取图片翻译情况
	 * 
	 * @param org
	 * @param fid
	 * @return
	 */
	private static Object getPic(String org, Object fid) {
		Object status = null;
		String sql = " select tamd.fk_aos_status from tk_aos_mkt_designreq tamd," + " tk_aos_mkt_listing_min taml,"
				+ " tk_aos_mkt_designcmp tamdc," + " tk_aos_mkt_designreq tamd2," + " tk_aos_mkt_listing_req tamlq,"
				+ EIPDB + ".T_BD_Country tbc" + " where 1=1 " + " and taml.fid = tamd.fk_aos_sourceid "
				+ " and tamdc.fid = taml.fk_aos_sourceid " + " and tamd2.fid = tamdc.fk_aos_sourceid "
				+ " and tamlq.fid = tamd2.fk_aos_sourceid " + " and tbc.fid = tamdc.fk_aos_orgid "
				+ " and tbc.fnumber = ? " + " and tamlq.fid = ? ";
		Object[] params = { org, fid };
		DataSet ds = DB.queryDataSet("getPic" + org, DBRoute.of("aos.mkt"), sql, params);
		while (ds.hasNext()) {
			Row row = ds.next();
			status = row.get(0);
		}
		return status;
	}

	/**
	 * 设计需求初始化
	 * 
	 * @return
	 */
	private static HashMap<String, String> generateDesign() {
		HashMap<String, String> design = new HashMap<>();
		DynamicObjectCollection aos_mkt_designreqS = QueryServiceHelper.query("aos_mkt_designreq",
				"aos_sourceid,aos_status", null);
		for (DynamicObject aos_mkt_designreq : aos_mkt_designreqS) {
			design.put(aos_mkt_designreq.getString("aos_sourceid"), aos_mkt_designreq.getString("aos_status"));
		}
		return design;
	}

	/**
	 * 文案数据初始化
	 * 
	 * @return
	 */
	private static HashMap<String, String> generateSon() {
		HashMap<String, String> son = new HashMap<>();
		DynamicObjectCollection aos_mkt_listing_sonS = QueryServiceHelper.query("aos_mkt_listing_son",
				"aos_sourceid,aos_status", null);
		for (DynamicObject aos_mkt_listing_son : aos_mkt_listing_sonS) {
			son.put(aos_mkt_listing_son.getString("aos_sourceid"), aos_mkt_listing_son.getString("aos_status"));
		}
		return son;
	}

	/**
	 * 物料数据初始化
	 * 
	 * @return
	 */
	public static HashMap<String, Map<String, String>> generateItem() {
		HashMap<String, Map<String, String>> item = new HashMap<>();
		String SelectColumn = "number,name,aos_seting_cn,aos_specification_cn,aos_sellingpoint";
		DataSet bd_materialS = QueryServiceHelper.queryDataSet("aos_mkt_item_sync.generateItem", "bd_material",
				SelectColumn, new QFilter("aos_protype", QCP.equals, "N").toArray(), null);
		while (bd_materialS.hasNext()) {
			Row bd_material = bd_materialS.next();
			HashMap<String, String> Info = new HashMap<>();
			Info.put("name", bd_material.getString("name"));
			Info.put("aos_seting_cn", bd_material.getString("aos_seting_cn"));
			Info.put("aos_specification_cn", bd_material.getString("aos_specification_cn"));
			Info.put("aos_sellingpoint", bd_material.getString("aos_sellingpoint"));
			item.put(bd_material.getString("number"), Info);
		}
		bd_materialS.close();
		return item;
	}

}