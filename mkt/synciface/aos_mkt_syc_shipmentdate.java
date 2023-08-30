package mkt.synciface;

import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import mkt.common.MKTCom;
import mkt.progress.ProgressUtil;
import mkt.progress.iface.iteminfo;

import java.time.LocalDateTime;
import java.util.*;

import common.fnd.FndGlobal;

/**
 * @author create by gk
 * @date 2022/11/17 11:13
 * @action 同步出运日期(主要是未结束的拍照需求表和设计需求表)
 */
public class aos_mkt_syc_shipmentdate extends AbstractTask {
	@Override
	public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
		SyncPhotoReq();
		SyncFirstDate();
	}

	private void SyncFirstDate() {
		// 刷新多人会审日期与操作人
		DynamicObject[] aosMktPhotoReqS = BusinessDataServiceHelper.load("aos_mkt_photoreq",
				"aos_firstindate,aos_salehelper,aos_orgid,aos_itemid",
				new QFilter("aos_status", QCP.equals, "视频更新:多人会审").and("aos_sonflag", QCP.equals, true)
						.and("aos_firstindate", QCP.equals, null).toArray());
		for (DynamicObject aosMktPhotoReq : aosMktPhotoReqS) {
			String aos_orgid = aosMktPhotoReq.getString("aos_orgid");
			String aos_itemid = aosMktPhotoReq.getString("aos_itemid");
			DynamicObject bd_material = QueryServiceHelper.queryOne("bd_material",
					"aos_contryentry.aos_firstindate aos_firstindate", new QFilter("id", QCP.equals, aos_itemid)
							.and("aos_contryentry.aos_nationality", QCP.equals, aos_orgid).toArray());
			Date aos_firstindate = bd_material.getDate("aos_firstindate");
			Object aos_salehelper = aosMktPhotoReq.get("aos_salehelper");
			if (FndGlobal.IsNotNull(aosMktPhotoReqS) && FndGlobal.IsNotNull(aos_salehelper)) {
				aosMktPhotoReq.set("aos_user", aos_salehelper);
				aosMktPhotoReq.set("aos_firstindate", aos_firstindate);
			}
		}
		SaveServiceHelper.update(aosMktPhotoReqS);
	}

	/** 同步拍照需求表 **/
	public static void SyncPhotoReq() {
		DynamicObject aos_sync_log = BusinessDataServiceHelper.newDynamicObject("aos_sync_log");
		aos_sync_log.set("aos_type_code", "同步出运日期");
		aos_sync_log.set("aos_groupid", LocalDateTime.now());
		aos_sync_log.set("billstatus", "A");
		DynamicObjectCollection aos_sync_logS = aos_sync_log.getDynamicObjectCollection("aos_entryentity");

		QFilter filter_status = new QFilter("aos_status", "!=", "已完成");
		StringJoiner str = new StringJoiner(",");
		str.add("id");
		str.add("billno");
		str.add("aos_itemid");
		str.add("aos_requiredate"); // 申请日期
		str.add("aos_ponumber"); // 合同号
		str.add("aos_newitem"); // 新品
		str.add("aos_newvendor"); // 新供应商
		str.add("aos_shipdate"); // 出运日期
		str.add("aos_urgent"); // 紧急提醒
		str.add("aos_overseasdate");// 最早入库日期
		DynamicObject[] dyc_req = BusinessDataServiceHelper.load("aos_mkt_photoreq", str.toString(),
				new QFilter[] { filter_status });
		// 获取出运日期
		List<DynamicObject> list_save = new ArrayList<>(5000);
		for (DynamicObject dy_req : dyc_req) {
			StringJoiner logRow = new StringJoiner(";");
			logRow.add(dy_req.getString("billno"));
			logRow.add(dy_req.getBoolean("aos_newitem") + "  " + dy_req.getBoolean("aos_newvendor") + "   ");

			// 出运日期
			Date aos_shipdate = dy_req.getDate("aos_shipdate");
			// 判断是否为全球新品
			Boolean overSeaNew = true;
			if (dy_req.get("aos_itemid") != null) {
				String itemId = dy_req.getDynamicObject("aos_itemid").getString("id");
				overSeaNew = QueryServiceHelper.exists("bd_material",
						new QFilter("aos_contryentry.aos_contryentrystatus", QCP.not_equals, "A")
								.and("id", QCP.equals, itemId).toArray());
			}

			if (dy_req.getBoolean("aos_newitem") || dy_req.getBoolean("aos_newvendor")
					|| (FndGlobal.IsNull(aos_shipdate) && !overSeaNew)) {
				if (dy_req.get("aos_itemid") != null) {
					String item = dy_req.getDynamicObject("aos_itemid").getString("id");
					logRow.add(dy_req.getDynamicObject("aos_itemid").getString("number") + "   ");
					Object aos_ponumber = dy_req.get("aos_ponumber");
					logRow.add(aos_ponumber + " ");
					Date shipDate = null, overseasdate = null;
					DynamicObjectCollection dyc = iteminfo.GetShipDate(item, aos_ponumber,
							new String[] { "aos_overseasdate" });
					if (dyc.size() > 0) {
						shipDate = dyc.get(0).getDate("aos_shipmentdate");
						overseasdate = dyc.get(0).getDate("aos_overseasdate");
					}

					logRow.add("shipDate: " + shipDate + "   ");
					logRow.add("overSeasDate: " + overseasdate + "   ");
					dy_req.set("aos_shipdate", shipDate);
					dy_req.set("aos_overseasdate", overseasdate);
					dy_req.set("aos_urgent", ProgressUtil.JudgeUrgency(new Date(), shipDate));
					list_save.add(dy_req);
					// 同步样品入库通知单
					if (list_save.size() > 4500) {
						SaveServiceHelper.update(list_save.toArray(new DynamicObject[list_save.size()]));
						list_save.clear();
					}
					SyncRcv(dy_req.getString("id"), shipDate);
					SyncDesignreq(dy_req.getString("id"), shipDate);
					SyncPhotoList(dy_req.getString("billno"), shipDate);
				}
			}
			MKTCom.Put_SyncLog(aos_sync_logS, logRow.toString());
		}
		SaveServiceHelper.save(new DynamicObject[] { aos_sync_log });
		SaveServiceHelper.update(list_save.toArray(new DynamicObject[list_save.size()]));
		list_save.clear();
	}

	/** 同步样品入库单 **/
	private static void SyncRcv(String fid, Date shipDate) {
		QFilter filter_Id = new QFilter("aos_sourceid", "=", fid);
		StringJoiner str = new StringJoiner(",");
		str.add("id");
		str.add("aos_shipdate");
		DynamicObject[] dyc = BusinessDataServiceHelper.load("aos_mkt_rcv", str.toString(),
				new QFilter[] { filter_Id });
		for (DynamicObject dy : dyc) {
			dy.set("aos_shipdate", shipDate);
		}
		SaveServiceHelper.update(dyc);
	}

	/** 同步设计需求表 **/
	public static void SyncDesignreq(String fid, Date shipDate) {
		QFilter filter_source = new QFilter("aos_sourceid", "=", fid);
		StringJoiner str = new StringJoiner(",");
		str.add("id");
		str.add("aos_shipdate");
		str.add("aos_entryentity.Seq");
		str.add("aos_entryentity.aos_itemid");
		DynamicObject[] dyc_design = BusinessDataServiceHelper.load("aos_mkt_designreq", str.toString(),
				new QFilter[] { filter_source });
		List<DynamicObject> list_save = new ArrayList<>(5000);
		for (DynamicObject dy : dyc_design) {
			if (dy.getDynamicObjectCollection("aos_entryentity").size() > 0) {
				Object item = dy.getDynamicObjectCollection("aos_entryentity").get(0).get("aos_itemid");
				if (item != null) {
					dy.set("aos_shipdate", shipDate);
					list_save.add(dy);
					if (list_save.size() > 4500) {
						SaveServiceHelper.update(list_save.toArray(new DynamicObject[list_save.size()]));
						list_save.clear();

					}
				}
			}
		}
		SaveServiceHelper.update(list_save.toArray(new DynamicObject[list_save.size()]));
		list_save.clear();
	}

	/** 同步拍照任务清单 **/
	public static void SyncPhotoList(String billno, Date shipDate) {
		QFilter filter_bill = new QFilter("billno", "=", billno);
		StringJoiner str = new StringJoiner(",");
		str.add("id");
		str.add("aos_shipdate");
		DynamicObject[] dyc = BusinessDataServiceHelper.load("aos_mkt_photolist", str.toString(),
				new QFilter[] { filter_bill });
		for (DynamicObject dy : dyc) {
			dy.set("aos_shipdate", shipDate);
		}
		SaveServiceHelper.update(dyc);
	}
}
