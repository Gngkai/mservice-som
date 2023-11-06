package mkt.progress.photo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import common.Cux_Common_Utl;
import common.fnd.FndGlobal;
import common.fnd.FndHistory;
import common.fnd.FndReturn;
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
import kd.bos.servicehelper.operation.SaveServiceHelper;
import mkt.progress.listing.aos_mkt_listingreq_bill;

public class aos_mkt_progphreq_sync extends AbstractTask {
	/** 系统管理员 **/
	public final static String system = Cux_Common_Utl.SYSTEM;

	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		do_operate(param);
	}

	public static void do_operate(Map<String, Object> param) {
		/** 将SCM生成的拍照需求表同步至拍照任务清单 **/
		PhotoSync();
		/** 将SCM生成的Listing优化需求表同步至拍照任务清单 **/
		ListingSync();
		/** 合同取消自动关闭 **/
		closePoSync();
	}

	/**
	 * 合同取消自动关闭
	 */
	private static void closePoSync() {
		List<String> aosPurContractS = QueryServiceHelper
				.query("aos_purcontract", "billno", new QFilter("billstatus", QCP.equals, "F").toArray()).stream()
				.map(dy -> dy.getString("billno")).distinct().collect(Collectors.toList());

		DynamicObject[] aosMktPhotoReqS = BusinessDataServiceHelper.load("aos_mkt_photoreq",
				"aos_user,aos_status,aos_ponumber,aos_type,id,aos_photoflag,aos_vedioflag,"
						+ "aos_itemid,aos_itemname,billno,aos_sameitemid,aos_reason,aos_developer,aos_poer,"
						+ "aos_shipdate,aos_specification,aos_seting1",
				new QFilter("aos_status", QCP.not_equals, "已完成").and("aos_ponumber", QCP.in, aosPurContractS)
						.toArray());
		for (DynamicObject aosMktPhotoReq : aosMktPhotoReqS) {
			aosMktPhotoReq.set("aos_user", system);
			aosMktPhotoReq.set("aos_status", "不需拍");
			aosMktPhotoReq.set("aos_photoflag", false);
			aosMktPhotoReq.set("aos_vedioflag", false);
			if ("拍照".equals(aosMktPhotoReq.getString("aos_type"))) {
				// 数据进入不拍照任务清单
				aos_mkt_nophoto_bill.create_noPhotoEntity(aosMktPhotoReq);
				// 将3D调整为已完成
				DynamicObject aos_mkt_3design = BusinessDataServiceHelper.loadSingle("aos_mkt_3design",
						new QFilter("aos_sourceid", QCP.equals, aosMktPhotoReq.getPkValue().toString()).toArray());
				if (FndGlobal.IsNotNull(aos_mkt_3design)) {
					aos_mkt_3design.set("aos_status", "已完成");
					aos_mkt_3design.set("aos_user", system);
					SaveServiceHelper.save(new DynamicObject[] { aos_mkt_3design });
				}
				// 将样品入库单调整为已完成
				DynamicObject aos_mkt_rcv = BusinessDataServiceHelper.loadSingle("aos_mkt_rcv",
						new QFilter("aos_sourceid", QCP.equals, aosMktPhotoReq.getPkValue().toString()).toArray());
				;
				if (FndGlobal.IsNotNull(aos_mkt_rcv)) {
					aos_mkt_rcv.set("aos_status", "已完成");
					aos_mkt_rcv.set("aos_user", system);
					SaveServiceHelper.save(new DynamicObject[] { aos_mkt_rcv });
				}
				// 将拍照清单同步
				DynamicObject aos_mkt_photolist = BusinessDataServiceHelper.loadSingle("aos_mkt_photolist",
						new QFilter("billno", QCP.equals, aosMktPhotoReq.getString("billno")).toArray());
				if (FndGlobal.IsNotNull(aos_mkt_photolist)) {
					aos_mkt_photolist.set("aos_phstatus", "已完成");
					SaveServiceHelper.save(new DynamicObject[] { aos_mkt_photolist });
				}

			}
		}
		SaveServiceHelper.save(aosMktPhotoReqS);
	}

	private static void ListingSync() {
		// 异常参数
		FndReturn Retrun = new FndReturn();

		QFilter filter = new QFilter("aos_autoflag", QCP.equals, true).and("aos_status", QCP.equals, "申请人");
		QFilter[] filters = new QFilter[] { filter };
		String SelectField = "id";
		DynamicObjectCollection aos_mkt_listing_reqS = QueryServiceHelper.query("aos_mkt_listing_req", SelectField,
				filters);
		for (DynamicObject aos_mkt_listing_req : aos_mkt_listing_reqS) {
			DynamicObject dyn = BusinessDataServiceHelper.loadSingle(aos_mkt_listing_req.get("id"),
					"aos_mkt_listing_req");

			DynamicObjectCollection aos_entryentityS = dyn.getDynamicObjectCollection("aos_entryentity");
			List<DynamicObject> ListRequire = new ArrayList<DynamicObject>();
			List<DynamicObject> ListRequirePic = new ArrayList<DynamicObject>();
			for (DynamicObject aos_entryentity : aos_entryentityS) {
				Object aos_require = aos_entryentity.get("aos_require");// 文案
				Object aos_requirepic = aos_entryentity.get("aos_requirepic"); // 图片
				if (!Cux_Common_Utl.IsNull(aos_require))
					ListRequire.add(aos_entryentity);
				if (!Cux_Common_Utl.IsNull(aos_requirepic))
					ListRequirePic.add(aos_entryentity);
			}


			if (ListRequire != null && ListRequire.size() > 0) {
				aos_mkt_listingreq_bill.GenerateListingSon(ListRequire, Retrun, dyn);
				if (Retrun.GetErrorCount() > 0) {
					continue;
				}
			}

			if (ListRequirePic != null && ListRequirePic.size() > 0) {
				aos_mkt_listingreq_bill.GenerateDesignReq(ListRequirePic, Retrun, dyn);
				if (Retrun.GetErrorCount() > 0) {
					continue;
				}
			}

			dyn.set("aos_status", "已完成");
			dyn.set("aos_user", system);
			aos_mkt_listingreq_bill.setItemCate(dyn);
			OperationServiceHelper.executeOperate("save", "aos_mkt_listing_req", new DynamicObject[] { dyn },
					OperateOption.create());

		}
	}

	private static void PhotoSync() {
		QFilter filter_exists = new QFilter("billno", QCP.equals, null).or("billno", QCP.equals, "");
		QFilter[] filters = new QFilter[] { filter_exists };
		String SelectField = "id,billno,aos_itemid,aos_itemname,aos_photoflag,aos_vedioflag,aos_phstate,"
				+ "aos_shipdate,aos_developer,aos_poer,aos_follower,aos_whiteph,aos_actph,aos_vedior,"
				+ "aos_newitem,aos_newvendor,aos_address,aos_orgtext,aos_urgent";
		DynamicObjectCollection aos_mkt_photoreqS = QueryServiceHelper.query("aos_mkt_photoreq", SelectField, filters);
		for (DynamicObject aos_mkt_photoreq : aos_mkt_photoreqS) {
			DynamicObject aos_mkt_photolist = BusinessDataServiceHelper.newDynamicObject("aos_mkt_photolist");
			aos_mkt_photolist.set("aos_itemid", aos_mkt_photoreq.get("aos_itemid"));
			aos_mkt_photolist.set("aos_itemname", aos_mkt_photoreq.get("aos_itemname"));
			aos_mkt_photolist.set("aos_photoflag", aos_mkt_photoreq.get("aos_photoflag"));
			aos_mkt_photolist.set("aos_vedioflag", aos_mkt_photoreq.get("aos_vedioflag"));
			aos_mkt_photolist.set("aos_phstate", aos_mkt_photoreq.get("aos_phstate"));
			aos_mkt_photolist.set("aos_shipdate", aos_mkt_photoreq.get("aos_shipdate"));
			aos_mkt_photolist.set("aos_developer", aos_mkt_photoreq.get("aos_developer"));
			aos_mkt_photolist.set("aos_poer", aos_mkt_photoreq.get("aos_poer"));
			aos_mkt_photolist.set("aos_follower", aos_mkt_photoreq.get("aos_follower"));
			aos_mkt_photolist.set("aos_whiteph", aos_mkt_photoreq.get("aos_whiteph"));
			aos_mkt_photolist.set("aos_actph", aos_mkt_photoreq.get("aos_actph"));
			aos_mkt_photolist.set("aos_vedior", aos_mkt_photoreq.get("aos_vedior"));
			aos_mkt_photolist.set("aos_newitem", aos_mkt_photoreq.get("aos_newitem"));
			aos_mkt_photolist.set("aos_newvendor", aos_mkt_photoreq.get("aos_newvendor"));
			aos_mkt_photolist.set("aos_photourl", "拍照");
			aos_mkt_photolist.set("aos_vediourl", "视频");
			aos_mkt_photolist.set("aos_address", aos_mkt_photoreq.get("aos_address")); // 地址
			aos_mkt_photolist.set("aos_orgtext", aos_mkt_photoreq.get("aos_orgtext")); // 下单国别
			aos_mkt_photolist.set("aos_urgent", aos_mkt_photoreq.get("aos_urgent")); // 紧急提醒

			String aos_picdesc = "", aos_picdesc1 = ""; // 照片需求
			QFilter filter_id = new QFilter("id", "=", aos_mkt_photoreq.get("id"));
			DynamicObjectCollection dyc_photo = QueryServiceHelper.query("aos_mkt_photoreq",
					"aos_entryentity.aos_picdesc aos_picdesc", new QFilter[] { filter_id });
			if (dyc_photo.size() > 0) {
				if (dyc_photo.get(0).getString("aos_picdesc") != null) {
					aos_picdesc = dyc_photo.get(0).getString("aos_picdesc");
				}
				if (dyc_photo.size() > 1) {
					if (dyc_photo.get(1).getString("aos_picdesc") != null) {
						aos_picdesc1 = dyc_photo.get(0).getString("aos_picdesc");
					}
				}
			}
			aos_mkt_photolist.set("aos_picdesc", aos_picdesc);
			aos_mkt_photolist.set("aos_picdesc1", aos_picdesc1);

			if (aos_mkt_photoreq.getBoolean("aos_photoflag"))
				aos_mkt_photolist.set("aos_phstatus", "新建");
			if (aos_mkt_photoreq.getBoolean("aos_vedioflag"))
				aos_mkt_photolist.set("aos_vedstatus", "新建");
			aos_mkt_photolist.set("billstatus", "A");
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
					new DynamicObject[] { aos_mkt_photolist }, OperateOption.create());
			if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
			} else {
				DynamicObject dyn = BusinessDataServiceHelper.loadSingle(aos_mkt_photoreq.get("id"),
						"aos_mkt_photoreq");
				dyn.set("billno", aos_mkt_photolist.get("billno"));
				dyn.set("aos_user", dyn.get("aos_requireby"));
				dyn.set("aos_sourceid", operationrst.getSuccessPkIds().get(0));
				dyn.set("aos_init", true);

				DynamicObjectCollection aos_entryentityS = dyn.getDynamicObjectCollection("aos_entryentity");
				DynamicObject aos_entryentity = aos_entryentityS.addNew();
				aos_entryentity.set("aos_applyby", "申请人");
				aos_entryentity.set("aos_picdesc", "见开发采购需求");
				aos_entryentity = aos_entryentityS.addNew();
				aos_entryentity.set("aos_applyby", "开发/采购");

				DynamicObjectCollection aos_entryentity1S = dyn.getDynamicObjectCollection("aos_entryentity1");
				DynamicObject aos_entryentity1 = aos_entryentity1S.addNew();
				aos_entryentity1.set("aos_applyby2", "申请人");
				aos_entryentity1.set("aos_veddesc", "见开发采购需求");
				aos_entryentity1 = aos_entryentity1S.addNew();
				aos_entryentity1.set("aos_applyby2", "开发/采购");

				DynamicObjectCollection aos_entryentity2S = dyn.getDynamicObjectCollection("aos_entryentity2");
				DynamicObject aos_entryentity2 = aos_entryentity2S.addNew();
				aos_entryentity2.set("aos_phtype", "白底");
				aos_entryentity2 = aos_entryentity2S.addNew();
				aos_entryentity2.set("aos_phtype", "实景");

				DynamicObjectCollection aos_entryentity3S = dyn.getDynamicObjectCollection("aos_entryentity3");
				DynamicObject aos_entryentity3 = aos_entryentity3S.addNew();
				aos_entryentity3.set("aos_returnby", "摄影师");
				aos_entryentity3 = aos_entryentity3S.addNew();
				aos_entryentity3.set("aos_returnby", "开发");

				OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq", new DynamicObject[] { dyn },
						OperateOption.create());

				aos_mkt_progphreq_bill.SubmitForNew(dyn);
				FndHistory.Create(dyn, "提交", "新建");
			}
		}

	}
}