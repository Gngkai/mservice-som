package mkt.progress.design3d;

import java.util.Date;
import java.util.EventObject;
import java.util.List;

import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndHistory;
import common.sal.util.SalUtil;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.metadata.dynamicobject.DynamicObjectType;
import kd.bos.entity.EntityMetadataCache;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.IFormView;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import common.sal.util.QFBuilder;
import mkt.common.MKTCom;
import mkt.progress.ProgressUtil;
import mkt.progress.iface.iteminfo;
import mkt.progress.photo.aos_mkt_progphreq_bill;
import mkt.progress.photo.aos_mkt_rcv_bill;

public class aos_mkt_3design_bill extends AbstractBillPlugIn implements ItemClickListener {

	/** 系统管理员 **/
	public final static String system = Cux_Common_Utl.SYSTEM;

	@Override
	public void registerListener(EventObject e) {
		super.registerListener(e);
		this.addItemClickListeners("aos_toolbarap");// 给工具栏加监听事件
		this.addItemClickListeners("aos_submit"); // 提交
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String Control = evt.getItemKey();
		try {
			if ("aos_submit".equals(Control)) {
				DynamicObject dy_main = this.getModel().getDataEntity(true);
				aos_submit(dy_main, "A");// 提交
			} else if ("aos_history".equals(Control))
				aos_history();// 查看历史记录
			else if ("aos_querysample".equals(Control)) {
				openSample(this.getView(), this.getModel().getDataEntity(true).getPkValue());
			}
		} catch (FndError fndMessage) {
			this.getView().showTipNotification(fndMessage.getErrorMessage());
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
		}
	}

	public static void openSample(IFormView iFormView, Object id) {
		QFBuilder builder = new QFBuilder();
		builder.add("id", "=", id);
		builder.add("aos_source", "=", "拍照需求表");
		DynamicObject dy = QueryServiceHelper.queryOne("aos_mkt_3design", "aos_orignbill", builder.toArray());
		if (dy == null) {
			throw new FndError("拍照需求表不存在");
		}
		builder.clear();
		builder.add("billno", "=", dy.get("aos_orignbill"));
		dy = QueryServiceHelper.queryOne("aos_mkt_photoreq", "aos_itemid,aos_ponumber", builder.toArray());
		if (dy == null) {
			throw new FndError("拍照需求表不存在");
		}
		String aos_itemid = dy.getString("aos_itemid");
		String ponumber = dy.getString("aos_ponumber");
		aos_mkt_rcv_bill.openSample(iFormView, aos_itemid, ponumber);
	}

	/* 打开历史记录 **/
	private void aos_history() throws FndError {
		Cux_Common_Utl.OpenHistory(this.getView());
	}

	/** 初始化事件 **/
	public void afterLoadData(EventObject e) {
		super.afterLoadData(e);
		StatusControl();
	}

	/** 新建事件 **/
	public void afterCreateNewData(EventObject e) {
		super.afterCreateNewData(e);
		StatusControl();// 界面控制
	}

	/** 全局状态控制 **/
	private void StatusControl() {
		// 数据层
		Object AosStatus = this.getModel().getValue("aos_status");
		DynamicObject AosUser = (DynamicObject) this.getModel().getValue("aos_user");
		Object CurrentUserId = UserServiceHelper.getCurrentUserId();
		Object CurrentUserName = UserServiceHelper.getUserInfoByID((long) CurrentUserId).get("name");

		// 图片控制

		// 锁住需要控制的字段

		// 当前节点操作人不为当前用户 全锁
		if (!AosUser.getPkValue().toString().equals(CurrentUserId.toString())
				&& !"刘中怀".equals(CurrentUserName.toString()) && !"程震杰".equals(CurrentUserName.toString())
				&& !"陈聪".equals(CurrentUserName.toString())) {
//			this.getView().setEnable(false, "titlepanel");// 标题面板
			this.getView().setEnable(false, "aos_contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "bar_save");
			this.getView().setVisible(false, "aos_submit");
		}

		// 状态控制
		else if ("新建".equals(AosStatus)) {
			this.getView().setVisible(true, "bar_save");
			this.getView().setVisible(true, "aos_submit");
		} else if ("已完成".equals(AosStatus)) {
			this.getView().setVisible(false, "bar_save");
			this.getView().setVisible(false, "aos_submit");
			this.getView().setEnable(false, "aos_contentpanelflex");// 主界面面板
		}

	}

	/** 值校验 **/
	private static void SaveControl(DynamicObject dy_main) throws FndError {
		int ErrorCount = 0;
		String ErrorMessage = "";
		// 数据层

		// 校验
		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}

	}

	/** 提交 **/
	public void aos_submit(DynamicObject dy_main, String type) throws FndError {
		SaveControl(dy_main);// 先做数据校验判断是否可以提交
		String aos_status = dy_main.getString("aos_status");// 根据状态判断当前流程节点
		switch (aos_status) {
		case "新建":
			SubmitForNew(dy_main);
			DesignSkuList.createEntity(dy_main);
			break;
		}
		FndHistory.Create(dy_main, "提交", aos_status);
		SaveServiceHelper.save(new DynamicObject[] { dy_main });
		if (type.equals("A")) {
			this.getView().invokeOperation("refresh");
			StatusControl();// 提交完成后做新的界面状态控制
		}

	}

	/** 新建状态下提交 **/
	private static void SubmitForNew(DynamicObject dy_main) {
		// 异常参数
		int ErrorCount = 0;
		String ErrorMessage = "";

		// 数据层
		Object pkValue = dy_main.getPkValue();
		Object aos_designer = dy_main.get("aos_designer");
		Object aos_orignbill = dy_main.get("aos_orignbill");
		Object aos_sourceid = dy_main.get("aos_sourceid");
		Object aos_source = dy_main.get("aos_source");
		String MessageId = null;// 发送消息

		if (aos_designer == null) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "设计为空,流程无法流转!");
		}
		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}

		// 执行保存操作
		dy_main.set("aos_status", "已完成");// 设置单据流程状态
		dy_main.set("aos_user", system);// 设置操作人系统管理员

		SaveServiceHelper.save(new DynamicObject[] { dy_main });

		// 如果是设计需求表来的
		if ("设计需求表".equals(aos_source)) {
			// 判断本单下3D产品设计单是否全为已完成
			QFilter filter_bill = new QFilter("aos_orignbill", "=", aos_orignbill);
			QFilter filter_status = new QFilter("aos_status", "!=", "已完成");
			QFilter filter_id = new QFilter("id", "!=", pkValue);
			QFilter[] filters = new QFilter[] { filter_bill, filter_status, filter_id };
			DynamicObject aos_mkt_3design = QueryServiceHelper.queryOne("aos_mkt_3design", "count(0)", filters);
			int count = aos_mkt_3design.getInt(0);
			if (count == 0) {
				String designer = "";
				if ((aos_designer instanceof String) || (aos_designer instanceof Long))
					designer = String.valueOf(aos_designer);
				else
					designer = ((DynamicObject) aos_designer).getPkValue().toString();
				MessageId = designer;
				DynamicObject aos_mkt_designreq = BusinessDataServiceHelper.loadSingle(aos_sourceid,
						"aos_mkt_designreq");
				String aos_status = aos_mkt_designreq.getString("aos_status");
				aos_mkt_designreq.set("aos_user", MessageId);
				aos_mkt_designreq.set("aos_status", "设计确认3D");
				mkt.progress.design.aos_mkt_designreq_bill.setEntityValue(aos_mkt_designreq);
				FndHistory.Create(aos_mkt_designreq, "提交", aos_status);
				OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_designreq",
						new DynamicObject[] { aos_mkt_designreq }, OperateOption.create());
				if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
					MKTCom.SendGlobalMessage(MessageId, "aos_mkt_designreq", aos_sourceid + "", aos_orignbill + "",
							"设计确认3D");
				}
			}
		} else if ("拍照需求表".equals(aos_source)) {
			DynamicObject aos_mkt_photoreq = BusinessDataServiceHelper.loadSingle(aos_sourceid, "aos_mkt_photoreq");
			String aos_status = aos_mkt_photoreq.getString("aos_status");

			String aos_phstate = aos_mkt_photoreq.getString("aos_phstate");
			if ("工厂简拍".equals(aos_phstate)) {

				aos_mkt_progphreq_bill.GenerateDesign(aos_mkt_photoreq);

				aos_mkt_photoreq.set("aos_status", "已完成");
				aos_mkt_photoreq.set("aos_user", system);
				// 回写拍照任务清单
				FndHistory.Create(aos_mkt_photoreq, "提交(3d回写),工厂简拍结束", aos_status);
				OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq",
						new DynamicObject[] { aos_mkt_photoreq }, OperateOption.create());
				if (QueryServiceHelper.exists("aos_mkt_photolist", aos_mkt_photoreq.get("aos_sourceid"))) {
					DynamicObject aos_mkt_photolist = BusinessDataServiceHelper
							.loadSingle(aos_mkt_photoreq.get("aos_sourceid"), "aos_mkt_photolist");
					aos_mkt_photolist.set("aos_vedstatus", "已完成");
					OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
							new DynamicObject[] { aos_mkt_photolist }, OperateOption.create());
				}

			} else {
				aos_mkt_photoreq.set("aos_status", "开发/采购确认图片");
				Boolean aos_newitem = aos_mkt_photoreq.getBoolean("aos_newitem");
				Object aos_developer = aos_mkt_photoreq.getDynamicObject("aos_developer").getPkValue();
				Object aos_poer = aos_mkt_photoreq.getDynamicObject("aos_poer").getPkValue();
				if (aos_newitem) {
					aos_mkt_photoreq.set("aos_user", aos_developer);
					MessageId = aos_developer.toString();
				} else {
					aos_mkt_photoreq.set("aos_user", aos_poer);
					MessageId = aos_poer.toString();
				}
				FndHistory.Create(aos_mkt_photoreq, "提交(3d回写),下节点：开发/采购", aos_status);
				OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq",
						new DynamicObject[] { aos_mkt_photoreq }, OperateOption.create());

				// 回写拍照任务清单
				if (QueryServiceHelper.exists("aos_mkt_photolist", aos_mkt_photoreq.get("aos_sourceid"))) {
					DynamicObject aos_mkt_photolist = BusinessDataServiceHelper
							.loadSingle(aos_mkt_photoreq.get("aos_sourceid"), "aos_mkt_photolist");
					aos_mkt_photolist.set("aos_vedstatus", "开发/采购确认图片");
					OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
							new DynamicObject[] { aos_mkt_photolist }, OperateOption.create());
				}
				MKTCom.SendGlobalMessage(MessageId, "aos_mkt_photoreq", aos_sourceid + "", aos_orignbill + "",
						"开发/采购确认图片");

			}
		}

	}

	/**
	 * 设计需求表创建3D产品设计单
	 * 
	 * @param dy_mian 设计需求表单据
	 **/
	public static void Generate3Design(List<DynamicObject> list3d, DynamicObject dy_mian) throws FndError {
		// 信息参数
		String MessageId = null;
		String Message = "";
		// 异常参数
		int ErrorCount = 0;
		String ErrorMessage = "";
		// 数据层
		Object AosDesignerId = dy_mian.get("aos_designby");
		List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(AosDesignerId);
		Object aos_shipdate = dy_mian.get("aos_shipdate");
		Object billno = dy_mian.get("billno");
		Object ReqFId = dy_mian.getPkValue(); // 当前界面主键
		DynamicObject Aos3Der = dy_mian.getDynamicObject("aos_3der");
		Object Aos3DerID = Aos3Der.getPkValue();
		Object aos_type = dy_mian.get("aos_type");
		String aos_type3d = null;
		if ("新品设计".equals(aos_type))
			aos_type3d = "新建";
		else if ("老品优化".equals(aos_type))
			aos_type3d = "优化";

		if (list3d.size() == 0) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "3D行信息不存在!");
		}
		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}
		// 创建3D产品设计单头
		DynamicObject aos_mkt_3design = BusinessDataServiceHelper.newDynamicObject("aos_mkt_3design");
		// 头信息
		aos_mkt_3design.set("aos_requireby", AosDesignerId);
		aos_mkt_3design.set("aos_requiredate", new Date());
		aos_mkt_3design.set("aos_shipdate", aos_shipdate);
		aos_mkt_3design.set("aos_orignbill", billno);
		aos_mkt_3design.set("aos_sourceid", ReqFId);
		aos_mkt_3design.set("aos_3der", Aos3DerID);
		aos_mkt_3design.set("aos_user", Aos3DerID);
		aos_mkt_3design.set("aos_status", "新建");
		aos_mkt_3design.set("aos_designer", AosDesignerId);
		aos_mkt_3design.set("aos_type", aos_type3d);
		aos_mkt_3design.set("aos_source", "设计需求表");
		aos_mkt_3design.set("aos_source1", dy_mian.getString("aos_source"));

		// BOTP
		aos_mkt_3design.set("aos_sourcebilltype", "aos_mkt_designreq");
		aos_mkt_3design.set("aos_sourcebillno", billno);
		aos_mkt_3design.set("aos_srcentrykey", "aos_entryentity");

		//是否已经生成3d
		List<String> skuList = DesignSkuList.getSkuList();
		DynamicObjectCollection aos_entryentityS = aos_mkt_3design.getDynamicObjectCollection("aos_entryentity");
		for (int i = 0; i < list3d.size(); i++) {
			DynamicObject dyn3d_r = list3d.get(i);
			DynamicObject dyn3d_d = dyn3d_r.getDynamicObjectCollection("aos_subentryentity").get(0);
			if (i == 0) {
				aos_mkt_3design.set("aos_3dreq", dyn3d_r.get("aos_3dreq"));

				// 3d需求附件
				DynamicObjectCollection aos_attribute = aos_mkt_3design.getDynamicObjectCollection("aos_3datta");
				DynamicObjectCollection aos_attributefrom = dyn3d_r.getDynamicObjectCollection("aos_3datta");
				DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
				DynamicObject tempFile = null;
				for (DynamicObject d : aos_attributefrom) {
					tempFile = BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"), type);
					aos_attribute.addNew().set("fbasedataid", tempFile);
				}

				aos_mkt_3design.set("aos_productno", dyn3d_d.get("aos_segment3"));
			}
			if (MapList != null) {
				if (MapList.get(2) != null)
					aos_mkt_3design.set("aos_organization1", MapList.get(2).get("id"));
				if (MapList.get(3) != null)
					aos_mkt_3design.set("aos_organization2", MapList.get(3).get("id"));
			}
			// 产品信息
			DynamicObject aos_entryentity = aos_entryentityS.addNew();
			aos_entryentity.set("aos_itemid", dyn3d_r.get("aos_itemid"));
			aos_entryentity.set("aos_is_design",skuList.contains(dyn3d_r.getDynamicObject("aos_itemid").getString("id")));
			aos_entryentity.set("aos_is_saleout",
					ProgressUtil.Is_saleout(dyn3d_r.getDynamicObject("aos_itemid").getPkValue()));
			aos_entryentity.set("aos_itemname", dyn3d_d.get("aos_itemname"));
			aos_entryentity.set("aos_orgtext", dyn3d_d.get("aos_orgtext"));
			aos_entryentity.set("aos_specification", dyn3d_d.get("aos_spec"));
			aos_entryentity.set("aos_seting1", dyn3d_d.get("aos_seting1"));
			aos_entryentity.set("aos_seting2", dyn3d_d.get("aos_seting2"));
			aos_entryentity.set("aos_sellingpoint", dyn3d_d.get("aos_sellingpoint"));
			aos_entryentity.set("aos_srcrowseq", dyn3d_r.get("SEQ"));
		}

		MessageId = Aos3DerID + "";
		Message = "3D产品设计单-设计需求自动创建";
		OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_3design",
				new DynamicObject[] { aos_mkt_3design }, OperateOption.create());
		if (operationrst.isSuccess()) {
			String pk = operationrst.getSuccessPkIds().get(0).toString();
			FndHistory.Create("aos_mkt_3design", pk, "新建", "新建节点： " + aos_mkt_3design.getString("aos_status"));
		}

		// 修复关联关系
		try {
			ProgressUtil.botp("aos_mkt_3design", aos_mkt_3design.get("id"));
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
			MKTCom.SendGlobalMessage(MessageId, aos_mkt_3design + "", operationrst.getSuccessPkIds().get(0) + "",
					aos_mkt_3design.getString("billno"), Message);
		}
	}

	/** 拍照需求表类型创建3D **/
	public static void Generate3Design(DynamicObject aos_mkt_photoreq) throws FndError {
		// 信息参数
		String MessageId = null;
		String Message = "";
		// 异常参数
		int ErrorCount = 0;
		String ErrorMessage = "";
		// 数据层
		Object AosDesignerId = aos_mkt_photoreq.getDynamicObject("aos_designer").getPkValue();
		List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(AosDesignerId);
		Object aos_shipdate = aos_mkt_photoreq.get("aos_shipdate");
		Object billno = aos_mkt_photoreq.get("billno");
		Object ReqFId = aos_mkt_photoreq.get("id"); // 主键
		Object Aos3DerID = aos_mkt_photoreq.getDynamicObject("aos_3d").getPkValue();
		Object Item_id = aos_mkt_photoreq.getDynamicObject("aos_itemid").getPkValue();

		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}

		// 创建一条3D产品设计单
		DynamicObject aos_mkt_3design = BusinessDataServiceHelper.newDynamicObject("aos_mkt_3design");
		// 头信息
		aos_mkt_3design.set("aos_requireby", AosDesignerId);
		aos_mkt_3design.set("aos_requiredate", new Date());
		aos_mkt_3design.set("aos_shipdate", aos_shipdate);
		aos_mkt_3design.set("aos_orignbill", billno);
		aos_mkt_3design.set("aos_sourceid", ReqFId);
		aos_mkt_3design.set("aos_3der", Aos3DerID);
		aos_mkt_3design.set("aos_user", Aos3DerID);
		aos_mkt_3design.set("aos_status", "新建");
		aos_mkt_3design.set("aos_type", "新建");// 3D类型 此方法默认新建
		aos_mkt_3design.set("aos_designer", AosDesignerId);
		aos_mkt_3design.set("aos_source", "拍照需求表");

		aos_mkt_3design.set("aos_quainscomdate",aos_mkt_photoreq.get("aos_quainscomdate"));

		//230718 gk:(新产品=是，或新供应商=是)且工厂简拍 生成3D产品设计单，3D产品设计单到大货样封样节点
		boolean newitem = aos_mkt_photoreq.getBoolean("aos_newitem");
		boolean newvendor = aos_mkt_photoreq.getBoolean("aos_newvendor");
		boolean phstate = aos_mkt_photoreq.getString("aos_phstate").equals("工厂简拍");
		if ((newitem || newvendor) && phstate){
			boolean existShipdate = FndGlobal.IsNull(aos_mkt_photoreq.get("aos_shipdate"));
			boolean existQuainscome = FndGlobal.IsNull(aos_mkt_photoreq.get("aos_quainscomdate"));
			//出运日期和质检完成日期都为空
			if (existQuainscome && existShipdate){
				aos_mkt_3design.set("aos_status","大货样封样");
				aos_mkt_3design.set("aos_user",system);
			}
		}

		if (MapList != null) {
			if (MapList.get(2) != null)
				aos_mkt_3design.set("aos_organization1", MapList.get(2).get("id"));
			if (MapList.get(3) != null)
				aos_mkt_3design.set("aos_organization2", MapList.get(3).get("id"));
		}

		// 产品信息
		String aos_orgtext = "";
		DynamicObject bd_material = BusinessDataServiceHelper.loadSingle(Item_id, "bd_material");
		DynamicObjectCollection aos_contryentryS = bd_material.getDynamicObjectCollection("aos_contryentry");
		for (DynamicObject aos_contryentry : aos_contryentryS) {
			DynamicObject aos_nationality = aos_contryentry.getDynamicObject("aos_nationality");
			String aos_nationalitynumber = aos_nationality.getString("number");
			if ("IE".equals(aos_nationalitynumber))
				continue;
			Object org_id = aos_nationality.get("id"); // ItemId
			int OsQty = iteminfo.GetItemOsQty(org_id, Item_id);
			int SafeQty = iteminfo.GetSafeQty(org_id);
			if ("C".equals(aos_contryentry.getString("aos_contryentrystatus")) && OsQty < SafeQty)
				continue;
			aos_orgtext = aos_orgtext + aos_nationalitynumber + ";";
		}

		String item_number = bd_material.getString("number");
		String aos_productno = bd_material.getString("aos_productno");
		String aos_itemname = bd_material.getString("name");
		// 获取同产品号物料
		QFilter filter_productno = new QFilter("aos_productno", QCP.equals, aos_productno);
		QFilter[] filters = new QFilter[] { filter_productno };
		String SelectColumn = "number,aos_type";
		String aos_broitem = "";
		DynamicObjectCollection bd_materialS = QueryServiceHelper.query("bd_material", SelectColumn, filters);
		for (DynamicObject bd : bd_materialS) {
			if ("B".equals(bd.getString("aos_type")))
				continue; // 配件不获取
			String number = bd.getString("number");
			if (item_number.equals(number))
				continue;
			else
				aos_broitem = aos_broitem + number + ";";
		}

		List<String> skuList = DesignSkuList.getSkuList();
		// 产品信息
		DynamicObjectCollection aos_entryentityS = aos_mkt_3design.getDynamicObjectCollection("aos_entryentity");
		DynamicObject aos_entryentity = aos_entryentityS.addNew();
		aos_entryentity.set("aos_itemid", Item_id);
		aos_entryentity.set("aos_is_design",skuList.contains(String.valueOf(Item_id)));
		aos_entryentity.set("aos_is_saleout", ProgressUtil.Is_saleout(Item_id));
		aos_mkt_3design.set("aos_productno", aos_productno);
		aos_entryentity.set("aos_itemname", aos_itemname);
		aos_entryentity.set("aos_orgtext", aos_orgtext);
		aos_entryentity.set("aos_specification", aos_mkt_photoreq.get("aos_specification"));
		aos_entryentity.set("aos_seting1", aos_mkt_photoreq.get("aos_seting1"));
		aos_entryentity.set("aos_seting2", aos_mkt_photoreq.get("aos_seting2"));
		aos_entryentity.set("aos_sellingpoint", aos_mkt_photoreq.get("aos_sellingpoint"));

		// 拍照需求单据
		DynamicObjectCollection dyc_phptoDemand = aos_mkt_photoreq.getDynamicObjectCollection("aos_entryentity");
		// 存在申请人需求
		if (dyc_phptoDemand.size() > 0) {
			DynamicObject dy_reqEnt = dyc_phptoDemand.get(0);
			// 申请人需求
			aos_mkt_3design.set("aos_req", dy_reqEnt.get("aos_picdesc"));
			DynamicObjectCollection dyc_attr = dy_reqEnt.getDynamicObjectCollection("aos_picattr");
			DynamicObjectCollection dyc_reqAtt = aos_mkt_3design.getDynamicObjectCollection("aos_reqatt");
			for (DynamicObject dy_attr : dyc_attr) {
				dyc_reqAtt.addNew().set("fbasedataid", dy_attr);
			}
		}
		// 存在开发需求
		if (dyc_phptoDemand.size() > 1) {
			DynamicObject dy_deveEnt = dyc_phptoDemand.get(1);
			// 开发需求
			aos_mkt_3design.set("aos_deve", dy_deveEnt.get("aos_picdesc"));
			DynamicObjectCollection dyc_attr = dy_deveEnt.getDynamicObjectCollection("aos_picattr");
			DynamicObjectCollection dyc_deveAtt = aos_mkt_3design.getDynamicObjectCollection("aos_deveatt");
			for (DynamicObject dy_attr : dyc_attr) {
				dyc_deveAtt.addNew().set("fbasedataid", dy_attr);
			}
		}

		DynamicObjectCollection aos_entryentity6S = aos_mkt_photoreq.getDynamicObjectCollection("aos_entryentity6");
		if (aos_entryentity6S.size() > 0) {
			DynamicObject aos_entryentity6 = aos_entryentity6S.get(0);
			// 申请人需求
			aos_mkt_3design.set("aos_req", aos_entryentity6.get("aos_reqsupp"));
			DynamicObjectCollection dyc_attr = aos_entryentity6.getDynamicObjectCollection("aos_attach1");
			DynamicObjectCollection dyc_reqAtt = aos_mkt_3design.getDynamicObjectCollection("aos_reqatt");
			for (DynamicObject dy_attr : dyc_attr) {
				dyc_reqAtt.addNew().set("fbasedataid", dy_attr);
			}
			// 开发需求
			aos_mkt_3design.set("aos_deve", aos_entryentity6.get("aos_devsupp"));
			DynamicObjectCollection dyc_attr2 = aos_entryentity6.getDynamicObjectCollection("aos_attach2");
			DynamicObjectCollection dyc_deveAtt = aos_mkt_3design.getDynamicObjectCollection("aos_deveatt");
			for (DynamicObject dy_attr2 : dyc_attr2) {
				dyc_deveAtt.addNew().set("fbasedataid", dy_attr2);
			}
		}

		MessageId = Aos3DerID + "";
		Message = "3D产品设计单-设计需求自动创建";
		OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_3design",
				new DynamicObject[] { aos_mkt_3design }, OperateOption.create());
		if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
			MKTCom.SendGlobalMessage(MessageId, aos_mkt_3design + "", operationrst.getSuccessPkIds().get(0) + "",
					aos_mkt_3design.getString("billno"), Message);
		}
	}

}
