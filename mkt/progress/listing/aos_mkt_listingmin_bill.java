package mkt.progress.listing;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndHistory;
import common.fnd.FndMsg;
import common.sal.util.SalUtil;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.dataentity.metadata.dynamicobject.DynamicObjectType;
import kd.bos.entity.EntityMetadataCache;
import kd.bos.entity.datamodel.events.ImportDataEventArgs;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.ClientProperties;
import kd.bos.form.ConfirmCallBackListener;
import kd.bos.form.MessageBoxOptions;
import kd.bos.form.MessageBoxResult;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.events.*;
import kd.bos.form.operate.FormOperate;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.MKTCom;
import mkt.common.MKTS3PIC;
import mkt.progress.ProgressUtil;
import mkt.progress.design.aos_mkt_designreq_bill;
import mkt.progress.iface.iteminfo;
import mkt.progress.parameter.errorListing.ErrorListEntity;
import sal.synciface.imp.aos_sal_import_pub;

public class aos_mkt_listingmin_bill extends AbstractBillPlugIn implements ItemClickListener, HyperLinkClickListener {
	/** 系统管理员 **/
	public final static String system = Cux_Common_Utl.SYSTEM;

	@Override
	public void hyperLinkClick(HyperLinkClickEvent hyperLinkClickEvent) {
		int RowIndex = hyperLinkClickEvent.getRowIndex();
		String FieldName = hyperLinkClickEvent.getFieldName();
		if ("aos_segment3_r".equals(FieldName)) {
			Object aos_segment3_r = this.getModel().getValue("aos_segment3_r", RowIndex);
			DynamicObject aos_mkt_functreq = QueryServiceHelper.queryOne("aos_mkt_functreq", "id",
					new QFilter[] { new QFilter("aos_segment3", QCP.equals, aos_segment3_r) });
			if (!Cux_Common_Utl.IsNull(aos_mkt_functreq))
				Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_functreq", aos_mkt_functreq.get("id"));
			else
				this.getView().showErrorNotification("功能图需求表信息不存在!");
		}
	}

	@Override
	public void registerListener(EventObject e) {
		super.registerListener(e);
		this.addItemClickListeners("aos_toolbarap");// 给工具栏加监听事件
		this.addItemClickListeners("aos_submit"); // 提交
		this.addItemClickListeners("aos_back"); // 编辑退回
		EntryGrid entryGrid = this.getControl("aos_entryentity");
		entryGrid.addHyperClickListener(this);
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String Control = evt.getItemKey();
		try {
			if ("aos_submit".equals(Control)) {
				DynamicObject dy_main = this.getModel().getDataEntity(true);
				aos_submit(dy_main, "A");
			} else if ("aos_back".equals(Control))
				aos_back();
			else if ("aos_history".equals(Control))
				aos_history();// 查看历史记录
			else if ("aos_open".equals(Control))
				aos_open();
			else if ("aos_close".equals(Control))
				aos_close();// 手工关闭
		} catch (FndError fndMessage) {
			this.getView().showTipNotification(fndMessage.getErrorMessage());
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
		}
	}

	private void aos_close() {
		String KEY_CANCEL = "bar_cancel";
		ConfirmCallBackListener confirmCallBackListener = new ConfirmCallBackListener(KEY_CANCEL, this);
		// 设置页面确认框，参数为：标题，选项框类型，回调监听
		this.getView().showConfirm("您确认关闭此申请单吗？", MessageBoxOptions.YesNo, confirmCallBackListener);
	}

	@Override
	public void confirmCallBack(MessageBoxClosedEvent event) {
		super.confirmCallBack(event);
		String callBackId = event.getCallBackId();
		if (callBackId.equals("bar_cancel")) {
			if (event.getResult().equals(MessageBoxResult.Yes)) {
				this.getModel().setValue("aos_user", system);
				this.getModel().setValue("aos_status", "结束");
				this.getView().invokeOperation("save");
				this.getView().invokeOperation("refresh");
				FndHistory.Create(this.getView(), "手工关闭", "手工关闭");
				setErrorList(this.getModel().getDataEntity(true));
				StatusControl();
			}
		}
	}

	/** 打开来源流程 **/
	private void aos_open() {
		Object aos_sourceid = this.getModel().getValue("aos_sourceid");
		Object aos_sourcetype = this.getModel().getValue("aos_sourcetype");
		if ("LISTING".equals(aos_sourcetype))
			Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_listing_req", aos_sourceid);
		else if ("DESIGN".equals(aos_sourcetype))
			Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_designreq", aos_sourceid);
		else if ("VED".equals(aos_sourcetype))
			Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_photoreq", aos_sourceid);
		else if ("CMP".equals(aos_sourcetype))
			Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_designcmp", aos_sourceid);
	}

	/** 打开历史记录 **/
	private void aos_history() throws FndError {
		Cux_Common_Utl.OpenHistory(this.getView());
	}

	@Override
	public void propertyChanged(PropertyChangedArgs e) {
		try {
			String name = e.getProperty().getName();
			if (name.equals("aos_case") || name.equals("aos_caseinput") || name.equals("aos_require")
					|| name.equals("aos_reqinput"))
				SyncInput(name);
			else if (name.equals("aos_type")) {
				AosTypeChanged();
			} else if (name.equals("aos_itemid")) {
				int index = e.getChangeSet()[0].getRowIndex();
				ItemChanged(index);
			}
		} catch (FndError fndMessage) {
			this.getView().showTipNotification(fndMessage.getErrorMessage());
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
		}
	}

	@Override
	public void afterImportData(ImportDataEventArgs e) {
		DynamicObject mainDyEntity = this.getModel().getDataEntity(true);
		SaveServiceHelper.save(new DynamicObject[] { mainDyEntity });
		try {
			DynamicObjectCollection dyc_sub = mainDyEntity.getDynamicObjectCollection("aos_entryentity");
			for (DynamicObject dy_row : dyc_sub) {
				EntityRowSetValue(dy_row);
			}
			SaveServiceHelper.save(new DynamicObject[] { mainDyEntity });
		} catch (Exception e1) {
			StringWriter sw = new StringWriter();
			e1.printStackTrace(new PrintWriter(sw));
			this.getView().showMessage(e1.getMessage());
			e1.printStackTrace();
		}
	}

	/** 物料值改变 **/
	private void ItemChanged(int row) {
		if (row < 0)
			return;
		else {
			DynamicObject dy_row = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity")
					.get(row);
			EntityRowSetValue(dy_row);
			this.getView().updateView("aos_entryentity", row);
		}
	}

	private void SyncInput(String name) {
		int aos_entryentity = this.getModel().getEntryCurrentRowIndex("aos_entryentity");
		if (name.equals("aos_case"))
			this.getModel().setValue("aos_caseinput", this.getModel().getValue("aos_case"), 0);
		else if (name.equals("aos_caseinput"))
			this.getModel().setValue("aos_case", this.getModel().getValue("aos_caseinput"), aos_entryentity);
		else if (name.equals("aos_require"))
			this.getModel().setValue("aos_reqinput", this.getModel().getValue("aos_require"), 0);
		else if (name.equals("aos_reqinput"))
			this.getModel().setValue("aos_require", this.getModel().getValue("aos_reqinput"), aos_entryentity);
	}

	/** 任务类型值改变事件 **/
	private void AosTypeChanged() {
		Object aos_type = this.getModel().getValue("aos_type");
		Object aos_demandate = new Date();
		if ("四者一致".equals(aos_type)) {
			aos_demandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 1);
			this.getModel().setValue("aos_demandate", aos_demandate);
		} else if ("老品优化".equals(aos_type)) {
			aos_demandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 3);
			this.getModel().setValue("aos_demandate", aos_demandate);
		}
	}

	/** 编辑退回 **/
	private void aos_back() {
		String MessageId = null;
		String Message = "Listing优化需求表小语种-编辑退回";
		Object ReqFId = this.getModel().getDataEntity().getPkValue(); // 当前界面主键
		Object billno = this.getModel().getValue("billno");
		Object aos_requireby = this.getModel().getValue("aos_requireby");
		MessageId = aos_requireby + "";
		this.getModel().setValue("aos_status", "申请人");// 设置单据流程状态
		this.getModel().setValue("aos_user", aos_requireby);// 流转给编辑
		this.getView().invokeOperation("save");
		this.getView().invokeOperation("refresh");
		MKTCom.SendGlobalMessage(MessageId, "aos_mkt_listing_son", ReqFId + "", billno + "", Message);
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
		// 带出人员组织
		Object CurrentUserId = UserServiceHelper.getCurrentUserId();
		List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(CurrentUserId);
		if (MapList != null) {
			if (MapList.size() >= 3 && MapList.get(2) != null)
				this.getModel().setValue("aos_organization1", MapList.get(2).get("id"));
			if (MapList.size() >= 4 && MapList.get(3) != null)
				this.getModel().setValue("aos_organization2", MapList.get(3).get("id"));
		}
	}

	/** 界面关闭事件 **/
	public void beforeClosed(BeforeClosedEvent e) {
		super.beforeClosed(e);
		e.setCheckDataChange(false);
	}

	/** 操作之前事件 **/
	public void beforeDoOperation(BeforeDoOperationEventArgs args) {
		FormOperate formOperate = (FormOperate) args.getSource();
		String Operatation = formOperate.getOperateKey();
		try {
			if ("save".equals(Operatation)) {
				DynamicObject dy_main = this.getModel().getDataEntity(true);
				SaveControl(dy_main); // 保存校验
			}
		} catch (FndError fndMessage) {
			this.getView().showTipNotification(fndMessage.getErrorMessage());
			args.setCancel(true);
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
			args.setCancel(true);
		}
	}

	public void afterDoOperation(AfterDoOperationEventArgs args) {
		FormOperate formOperate = (FormOperate) args.getSource();
		String Operatation = formOperate.getOperateKey();
		try {
			if ("save".equals(Operatation))
				StatusControl();
		} catch (FndError fndMessage) {
			this.getView().showTipNotification(fndMessage.getErrorMessage());
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
		}
	}

	/** 提交 **/
	public void aos_submit(DynamicObject dy_main, String type) throws FndError {
		SaveControl(dy_main);// 先做数据校验判断是否可以提交
		String aos_status = dy_main.getString("aos_status");// 根据状态判断当前流程节点
		switch (aos_status) {
		case "编辑确认":
			SubmitForEditor(dy_main);
			break;
		case "海外编辑确认":
			SubmitForOsEditor(dy_main);
			break;
		case "申请人":
			SubmitForApply(dy_main);
			break;
		case "海外编辑确认:功能图":
			SubmitForOsFunc(dy_main);
			break;
		case "小站海外编辑确认:功能图":
			SubmitForOsSmall(dy_main);
			break;
		}
		SaveServiceHelper.save(new DynamicObject[] { dy_main });
		setErrorList(dy_main);
		// 插入历史记录
		FndHistory.Create(dy_main, "提交", aos_status);
		if (type.equals("A")) {
			this.getView().invokeOperation("refresh");
			StatusControl();// 提交完成后做新的界面状态控制
		}
	}

	/** 设置改错任务清单 **/
	public static void setErrorList(DynamicObject dy_main){
		String status = dy_main.getString("aos_status");
		if (!status.equals("结束")) {
			return;
		}
		DynamicObject dy_org = dy_main.getDynamicObject("aos_orgid");
		if (dy_org==null) {
			return;
		}
		String aos_type = dy_main.getString("aos_type");
		if (!ErrorListEntity.errorListType.contains(aos_type))
			return;
		String orgid = dy_org.getString("id");
		String billno = dy_main.getString("billno");
		DynamicObjectCollection dyc_ent = dy_main.getDynamicObjectCollection("aos_entryentity");
		for (DynamicObject dy : dyc_ent) {
			DynamicObject dy_item = dy.getDynamicObject("aos_itemid");
			if (dy_item==null) {
				continue;
			}
			ErrorListEntity errorListEntity = new ErrorListEntity(billno,aos_type,orgid,dy_item.getString("id"));
			errorListEntity.save();
		}
	}

	// 小站海外编辑确认:功能图
	private static void SubmitForOsSmall(DynamicObject dy_main) throws FndError {
		Object aos_type = dy_main.get("aos_type");

		// 只有子表会进入 必为RO和PT
		Object aos_orgsmall = dy_main.get("aos_orgsmall");
		String aos_orgnumber = ((DynamicObject) aos_orgsmall).getString("number");

		if ("功能图翻译".equals(aos_type)) {
			GenerateFuncSummary(aos_orgnumber, dy_main);// 插入功能图翻译台账
		}
		dy_main.set("aos_submitter", "B");
		dy_main.set("aos_status", "结束");
		dy_main.set("aos_user", system);
	}

	/** 海外编辑确认:功能图 **/
	private static void SubmitForOsFunc(DynamicObject dy_main) throws FndError {
		// 异常参数
		int ErrorCount = 0;
		String ErrorMessage = "";
		// 数据层
		Object aos_orgid = dy_main.get("aos_orgid");
		String aos_orgnumber = ((DynamicObject) aos_orgid).getString("number");
		Object aos_type = dy_main.get("aos_type");

		// 校验
		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}

		if ("IT".equals(aos_orgnumber)) {
			GenerateOsSmall("RO", dy_main);// 同时生成小站海外编辑确认:功能图 的listing优化需求小语种
		}

		if ("功能图翻译".equals(aos_type)) {
			GenerateFuncSummary(aos_orgnumber, dy_main);// 插入功能图翻译台账
		}
		// 设置功能图节点操作人为 人提交
		dy_main.set("aos_submitter", "B");
		dy_main.set("aos_status", "结束");
		dy_main.set("aos_user", system);
	}

	/** 海外编辑确认 **/
	private static void SubmitForOsEditor(DynamicObject dy_main) throws FndError {
		// 异常参数
		int ErrorCount = 0;
		String ErrorMessage = "";
		// 数据层

		// 校验
		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}
		GenerateListingSal(dy_main);// 生成销售信息确认单

		dy_main.set("aos_status", "结束");
		dy_main.set("aos_user", system);
	}

	/** 编辑确认状态下提交 **/
	private static void SubmitForEditor(DynamicObject dy_main) throws FndError {
		// 异常参数
		int ErrorCount = 0;
		String ErrorMessage = "";
		// 数据层
		Object aos_orgid = dy_main.get("aos_orgid");
		String aos_orgnumber = ((DynamicObject) aos_orgid).getString("number");
		Object aos_sourcetype = dy_main.get("aos_sourcetype");
		Object aos_sourceid = dy_main.get("aos_sourceid");
		Object aos_osconfirmlov = dy_main.get("aos_osconfirmlov");// 海外文字确认
		Object aos_funconfirm = dy_main.get("aos_funconfirm");// 海外功能图确认
		Object ListingStatus = null;
		Object ListingUser = null;
		Object aos_type = dy_main.get("aos_type");
		DynamicObject aos_oseditorview = dy_main.getDynamicObject("aos_oseditor");

		// 获取海外编辑
		DynamicObject dy_entFirstRow = dy_main.getDynamicObjectCollection("aos_entryentity").get(0);
		Object itemId = dy_entFirstRow.get("aos_itemid");
		Object id = ((DynamicObject) itemId).getString("id");

		String category = MKTCom.getItemCateNameZH(id);
		String[] category_group = category.split(",");
		String AosCategory1 = null;
		String AosCategory2 = null;
		int category_length = category_group.length;
		if (category_length > 0)
			AosCategory1 = category_group[0];
		if (category_length > 1)
			AosCategory2 = category_group[1];
		long aos_oseditor = 0;
		if (Cux_Common_Utl.IsNull(aos_oseditorview)) {
			if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("") && !AosCategory2.equals("")) {
				QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
				QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
				QFilter filter_ou = new QFilter("aos_orgid.number", "=", aos_orgnumber);
				QFilter[] filters_category = new QFilter[] { filter_category1, filter_category2, filter_ou };
				String SelectStr = "aos_oseditor";
				DynamicObject aos_mkt_progorguser = QueryServiceHelper.queryOne("aos_mkt_progorguser", SelectStr,
						filters_category);
				if (aos_mkt_progorguser != null) {
					aos_oseditor = aos_mkt_progorguser.getLong("aos_oseditor");
				}
			}
			if (aos_oseditor == 0) {
				ErrorCount++;
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, AosCategory1 + "," + AosCategory2 + "海外编辑师不存在!");
			}
			dy_main.set("aos_oseditor", aos_oseditor);
		} else {
			aos_oseditor = aos_oseditorview.getLong("id");
		}

		// 校验
		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}

		if ("VED".equals(aos_sourcetype)) {
			// 如果是视频类型 判断是否为最后一个完成
			dy_main.set("aos_status", "结束");
			dy_main.set("aos_user", system);
			// 先执行保存操作
			SaveServiceHelper.save(new DynamicObject[] { dy_main });
			QFilter filter_id = new QFilter("aos_sourceid", "=", aos_sourceid);
			QFilter filter_status = new QFilter("aos_status", "=", "编辑确认").or("aos_status", "=", "申请人");
			QFilter[] filters = new QFilter[] { filter_id, filter_status };
			DynamicObject aos_mkt_listing_min = QueryServiceHelper.queryOne("aos_mkt_listing_min", "id", filters);
			// 全部已完成 修改主流程状态
			if (aos_mkt_listing_min == null) {
				filter_id = new QFilter("id", "=", aos_sourceid);
				filters = new QFilter[] { filter_id };
				DynamicObject aos_mkt_listing_son = QueryServiceHelper.queryOne("aos_mkt_listing_son", "aos_sourceid",
						filters);
				Object PhotoId = aos_mkt_listing_son.get("aos_sourceid");
				aos_mkt_listingson_bill.UpdatePhotoToCut(PhotoId);// 小语种来源子表的来源拍照需求表ID
			}
		}
		/*
		 * else if ("DESIGN".equals(aos_sourcetype)) { // 如果是设计需求表 或 设计完成表 类型 完成后生成一个
		 * 设计需求表 任务类型=翻译 // 先执行保存操作 dy_main.set("aos_status", "结束");// 设置单据流程状态
		 * dy_main.set("aos_user", system);// 设置操作人为系统管理员 GenerateDesign(dy_main,
		 * null,null); }
		 */
		else if ("LISTING".equals(aos_sourcetype) || "CMP".equals(aos_sourcetype) || "DESIGN".equals(aos_sourcetype)) {
			if (!"是".equals(aos_osconfirmlov) && !"是".equals(aos_funconfirm)) {
				// 1.海外文字确认不为是 海外功能图确认也不为是
				if ("功能图翻译".equals(aos_type)) {
					GenerateFuncSummary(aos_orgnumber, dy_main);// 插入功能图翻译台账
				}
				ListingStatus = "结束";
				ListingUser = system;
				// 功能图翻译类型 不需要生成
				if (!"功能图翻译".equals(aos_type))
					GenerateListingSal(dy_main);// 同时生成销售信息确认单
			} else if ("是".equals(aos_osconfirmlov) && !"是".equals(aos_funconfirm)) {
				// 2.海外文字确认为是 海外功能图确认不为是
				ListingStatus = "海外编辑确认";
				dy_main.set("aos_status", ListingStatus);
				return;// 不调整节点操作人 直接退出
			} else if (!"是".equals(aos_osconfirmlov) && "是".equals(aos_funconfirm)) {
				// 3.海外功能图确认=是，海外文字确认=否时，流程走到海外编辑确认功能图节点
				ListingStatus = "海外编辑确认:功能图";
				ListingUser = aos_oseditor;
				dy_main.set("aos_funcdate", new Date());
				dy_main.set("aos_oseditor", aos_oseditor);
				if ("ES".equals(aos_orgnumber)) {
					GenerateOsSmall("PT", dy_main);
				}
			}
			if (ListingStatus == null || ListingUser == null) {
				FndError fndMessage = new FndError("未获取到下一节点状态或操作人!");
				throw fndMessage;
			}
			// 回写设计需求表
			fillDesign(dy_main);

			dy_main.set("aos_status", ListingStatus);
			dy_main.set("aos_user", ListingUser);
			dy_main.set("aos_make", UserServiceHelper.getCurrentUserId());
			dy_main.set("aos_ecdate", new Date());
		}
		// 结束所有
	}

	/** 来源类型=设计需求表时，编辑确认节点可编辑；提交后将值回写到设计需求表的功能图文案备注字段 **/
	private static void fillDesign(DynamicObject dy_main) {
		String aos_sourcetype = dy_main.getString("aos_sourcetype");
		if (aos_sourcetype.equals("DESIGN")) {
			String aos_sourceid = dy_main.getString("aos_sourceid");
			DynamicObject dy_design = BusinessDataServiceHelper.loadSingle(aos_sourceid, "aos_mkt_designreq");// 设计需求表
			// 获取文中物料对应的行
			Map<String, DynamicObject> map_itemToRow = dy_main.getDynamicObjectCollection("aos_entryentity").stream()
					.collect(Collectors.toMap(dy -> dy.getDynamicObject("aos_itemid").getString("id"), dy -> dy,
							(key1, key2) -> key1));
			DynamicObjectCollection dyc_dsign = dy_design.getDynamicObjectCollection("aos_entryentity");
			for (DynamicObject dy_row : dyc_dsign) {
				DynamicObject aos_itemid = dy_row.getDynamicObject("aos_itemid");
				if (aos_itemid == null)
					continue;
				String itemid = aos_itemid.getString("id");
				if (map_itemToRow.containsKey(itemid)) {
					DynamicObject dy_sonRow = map_itemToRow.get(itemid);
					dy_row.set("aos_remakes", dy_sonRow.get("aos_remakes"));

				}
			}
			SaveServiceHelper.update(new DynamicObject[] { dy_design });
		}
	}

	/** 生成小站海外编辑确认:功能图 的listing优化需求小语种 **/
	private static void GenerateOsSmall(String aos_orgnumber, DynamicObject dy_main) {
		Object aos_orgsmall = aos_sal_import_pub.get_import_id(aos_orgnumber, "bd_country");
		DynamicObject dy_user = null;
		Object aos_oseditor = dy_main.get("aos_oseditor");

		if (aos_oseditor instanceof String)
			aos_oseditor = (String) aos_oseditor;
		else if (aos_oseditor instanceof Long)
			aos_oseditor = aos_oseditor + "";
		else
			aos_oseditor = ((DynamicObject) aos_oseditor).getString("id");

		if (aos_orgnumber.equalsIgnoreCase("RO")) {
			QFilter filter = new QFilter("number", "=", "024044");
			dy_user = QueryServiceHelper.queryOne("bos_user", "id,name,number", new QFilter[] { filter });
			if (dy_user != null)
				aos_oseditor = dy_user.get("id");
		} else if (aos_orgnumber.equalsIgnoreCase("PT")) {
			QFilter filter = new QFilter("number", "=", "023186");
			dy_user = QueryServiceHelper.queryOne("bos_user", "id,name,number", new QFilter[] { filter });
			if (dy_user != null)
				aos_oseditor = dy_user.get("id");
		}

		DynamicObject aos_mkt_listing_min = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_min");
		aos_mkt_listing_min.set("billno", dy_main.get("billno"));
		aos_mkt_listing_min.set("aos_user", aos_oseditor);
		aos_mkt_listing_min.set("aos_sourcetype", dy_main.get("aos_sourcetype"));
		aos_mkt_listing_min.set("aos_status", "小站海外编辑确认:功能图");
		aos_mkt_listing_min.set("aos_funcdate", new Date());
		aos_mkt_listing_min.set("aos_requireby", dy_main.get("aos_requireby"));
		aos_mkt_listing_min.set("aos_organization1", dy_main.get("aos_organization1"));
		aos_mkt_listing_min.set("aos_organization2", dy_main.get("aos_organization2"));
		aos_mkt_listing_min.set("aos_requiredate", dy_main.get("aos_requiredate"));
		aos_mkt_listing_min.set("aos_demandate", dy_main.get("aos_demandate"));
		aos_mkt_listing_min.set("aos_type", dy_main.get("aos_type"));
		aos_mkt_listing_min.set("aos_source", dy_main.get("aos_source"));
		aos_mkt_listing_min.set("aos_importance", dy_main.get("aos_importance"));
		aos_mkt_listing_min.set("aos_designer", dy_main.get("aos_designer"));
		aos_mkt_listing_min.set("aos_editor", dy_main.get("aos_editor"));
		aos_mkt_listing_min.set("aos_editormin", dy_main.get("aos_editormin"));
		aos_mkt_listing_min.set("aos_oseditor", dy_main.get("aos_oseditor"));
		aos_mkt_listing_min.set("aos_orgid", dy_main.get("aos_orgid"));
		aos_mkt_listing_min.set("aos_orgsmall", dy_main.get("aos_orgsmall"));
		aos_mkt_listing_min.set("aos_osconfirmlov", dy_main.get("aos_osconfirmlov"));
		aos_mkt_listing_min.set("aos_funconfirm", dy_main.get("aos_funconfirm"));
		aos_mkt_listing_min.set("aos_orignbill", dy_main.get("aos_orignbill"));
		aos_mkt_listing_min.set("aos_sourceid", dy_main.get("aos_sourceid"));
		aos_mkt_listing_min.set("aos_orgsmall", aos_orgsmall);

		// BOTP
		aos_mkt_listing_min.set("aos_sourcebilltype", "aos_mkt_listing_min");
		aos_mkt_listing_min.set("aos_sourcebillno", dy_main.get("billno"));
		aos_mkt_listing_min.set("aos_srcentrykey", "aos_entryentity");

		DynamicObjectCollection aos_entryentityS = dy_main.getDynamicObjectCollection("aos_entryentity");

		DynamicObjectCollection aos_entryentityNewS = aos_mkt_listing_min.getDynamicObjectCollection("aos_entryentity");

		for (DynamicObject aos_entryentity : aos_entryentityS) {
			DynamicObject aos_entryentityNew = aos_entryentityNewS.addNew();
			aos_entryentityNew.set("aos_itemid", aos_entryentity.get("aos_itemid"));
			aos_entryentityNew.set("aos_is_saleout",
					ProgressUtil.Is_saleout(aos_entryentity.getDynamicObject("aos_itemid").getPkValue()));
			aos_entryentityNew.set("aos_require", aos_entryentity.get("aos_require"));
			aos_entryentityNew.set("aos_case", aos_entryentity.get("aos_case"));
			aos_entryentityNew.set("aos_srcrowseq", aos_entryentity.get("SEQ"));

			DynamicObjectCollection aos_attribute = aos_entryentityNew.getDynamicObjectCollection("aos_attribute");
			aos_attribute.clear();
			DynamicObjectCollection aos_attributefrom = aos_entryentity.getDynamicObjectCollection("aos_attribute");
			DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
			DynamicObject tempFile = null;
			for (DynamicObject d : aos_attributefrom) {
				tempFile = BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"), type);
				aos_attribute.addNew().set("fbasedataid", tempFile);
			}

			aos_entryentityNew.set("aos_write", aos_entryentity.get("aos_write"));
			aos_entryentityNew.set("aos_opt", aos_entryentity.get("aos_opt"));
			aos_entryentityNew.set("aos_pic", aos_entryentity.get("aos_pic"));
			aos_entryentityNew.set("aos_subtitle", aos_entryentity.get("aos_subtitle"));
			aos_entryentityNew.set("aos_title", aos_entryentity.get("aos_title"));
			aos_entryentityNew.set("aos_keyword", aos_entryentity.get("aos_keyword"));
			aos_entryentityNew.set("aos_other", aos_entryentity.get("aos_other"));
			aos_entryentityNew.set("aos_etc", aos_entryentity.get("aos_etc"));
			aos_entryentityNew.set("aos_segment3_r", aos_entryentity.get("aos_segment3_r"));
			aos_entryentityNew.set("aos_broitem_r", aos_entryentity.get("aos_broitem_r"));
			aos_entryentityNew.set("aos_itemname_r", aos_entryentity.get("aos_itemname_r"));
			aos_entryentityNew.set("aos_orgtext_r",
					ProgressUtil.getOrderOrg(aos_entryentity.getDynamicObject("aos_itemid").getPkValue()));

			DynamicObjectCollection aos_subentryentityNewS = aos_entryentityNew
					.getDynamicObjectCollection("aos_subentryentity");
			DynamicObject aos_subentryentity = aos_entryentity.getDynamicObjectCollection("aos_subentryentity").get(0);
			DynamicObject aos_subentryentityNew = aos_subentryentityNewS.addNew();
			aos_subentryentityNew.set("aos_segment3", aos_subentryentity.get("aos_segment3"));
			aos_subentryentityNew.set("aos_broitem", aos_subentryentity.get("aos_broitem"));
			aos_subentryentityNew.set("aos_itemname", aos_subentryentity.get("aos_itemname"));
			aos_subentryentityNew.set("aos_orgtext", aos_subentryentity.get("aos_orgtext"));
			aos_subentryentityNew.set("aos_reqinput", aos_subentryentity.get("aos_reqinput"));
			aos_subentryentityNew.set("aos_caseinput", aos_subentryentity.get("aos_caseinput"));

		}

		OperationServiceHelper.executeOperate("save", "aos_mkt_listing_min",
				new DynamicObject[] { aos_mkt_listing_min }, OperateOption.create());
		// 修复关联关系
		try {
			ProgressUtil.botp("aos_mkt_listing_min", aos_mkt_listing_min.get("id"));
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * 生成功能图翻译任务台账数据表
	 * 
	 * @param aos_orgnumber
	 **/
	private static void GenerateFuncSummary(String aos_orgnumber, DynamicObject dy_main) throws FndError {
		// 异常参数
		int ErrorCount = 0;
		String ErrorMessage = "";
		// 数据层
		Object billno = dy_main.get("billno");
		Object aos_orgid = dy_main.get("aos_orgid");
		Object ReqFId = dy_main.getPkValue(); // 当前界面主键
		DynamicObjectCollection aos_entryentityS = dy_main.getDynamicObjectCollection("aos_entryentity");
		aos_orgid = aos_sal_import_pub.get_import_id(aos_orgnumber, "bd_country");

		// 校验
		if ("A".equals("B")) {
			ErrorCount++;
			ErrorMessage += "小语种编辑师不存在!";
		}
		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}

		for (DynamicObject aos_entryentity : aos_entryentityS) {
			DynamicObject aos_mkt_funcsumdata = BusinessDataServiceHelper.newDynamicObject("aos_mkt_funcsumdata");
			aos_mkt_funcsumdata.set("aos_orgid", aos_orgid);
			aos_mkt_funcsumdata.set("aos_itemid", aos_entryentity.get("aos_itemid"));
			aos_mkt_funcsumdata.set("aos_sourcebill", billno);
			aos_mkt_funcsumdata.set("aos_creationdate", new Date());
			aos_mkt_funcsumdata.set("aos_eng", "N");
			aos_mkt_funcsumdata.set("aos_sourceid", ReqFId);

			OperationServiceHelper.executeOperate("save", "aos_mkt_funcsumdata",
					new DynamicObject[] { aos_mkt_funcsumdata }, OperateOption.create());
		}
	}

	/** 申请人提交 **/
	private static void SubmitForApply(DynamicObject dy_main) throws FndError {
		// 异常参数
		int ErrorCount = 0;
		String ErrorMessage = "";
		// 数据层
		String MessageId = null;
		String Message = "Listing优化需求表小语种-编辑确认";
		Object aos_editormin = dy_main.get("aos_editormin");
		Object billno = dy_main.get("billno");
		Object ReqFId = dy_main.getPkValue(); // 当前界面主键
		Object aos_itemid = null;
		Object aos_orignbill = dy_main.get("aos_orignbill");
		if (dy_main.getDynamicObjectCollection("aos_entryentity").size() > 0)
			aos_itemid = dy_main.getDynamicObjectCollection("aos_entryentity").get(0).getDynamicObject("aos_itemid");
		Object aos_orgid = dy_main.get("aos_orgid");

		// 校验
		if (aos_itemid == null) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "物料必填!");
		}
		if (aos_orgid == null) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "国别必填!");
		}

		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}

		if (aos_editormin != null)
			MessageId = ((DynamicObject) aos_editormin).getPkValue() + "";

		String type = ""; // 任务类型
		if (dy_main.get("aos_type") != null)
			type = dy_main.getString("aos_type");
		// 任务类型为小语种或者功能图翻译，流转给小语种，其他类型流转给国别编辑
		if (aos_editormin == null && aos_itemid != null && aos_orgid != null) {
			String category = MKTCom.getItemCateNameZH(((DynamicObject) aos_itemid).getPkValue());
			String[] category_group = category.split(",");
			String AosCategory1 = null;
			String AosCategory2 = null;
			int category_length = category_group.length;
			if (category_length > 0)
				AosCategory1 = category_group[0];
			if (category_length > 1)
				AosCategory2 = category_group[1];
			long aos_oueditor = 0;
			if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("") && !AosCategory2.equals("")) {
				Object orgid = ((DynamicObject) aos_orgid).getPkValue();
				DynamicObject aos_mkt_progorguser = ProgressUtil.minListtFindEditorByType(orgid, AosCategory1,
						AosCategory2, type);
				if (aos_mkt_progorguser != null) {
					aos_oueditor = aos_mkt_progorguser.getLong("aos_user");
				}
			}
			if (aos_oueditor == 0) {
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, AosCategory1 + "," + AosCategory2 + "小语种编辑师不存在!");
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}
			aos_editormin = aos_oueditor;
			MessageId = aos_editormin + "";
			dy_main.set("aos_editormin", aos_editormin);// 流转给小语种编辑师 或者编辑
		}
		dy_main.set("aos_user", aos_editormin);
		dy_main.set("aos_status", "编辑确认");// 设置单据流程状态
		if (Cux_Common_Utl.IsNull(aos_orignbill))
			SplitMinBySegment3(dy_main);
		MKTCom.SendGlobalMessage(MessageId, "aos_mkt_listing_min", ReqFId + "", billno + "", Message);
	}

	/** 若为手工新增的单据 则根据产品号拆分单据 **/
	private static void SplitMinBySegment3(DynamicObject dy_main) throws FndError {
		DynamicObject aos_mkt_listing_min = dy_main;
		// 开始汇总
		Map<String, List<DynamicObject>> Segment3Map = new HashMap<>();
		List<DynamicObject> MapList = new ArrayList<DynamicObject>();
		DynamicObjectCollection aos_entryentityS = aos_mkt_listing_min.getDynamicObjectCollection("aos_entryentity");
		for (DynamicObject aos_entryentity : aos_entryentityS) {
			String aos_segment3_r = aos_entryentity.getString("aos_segment3_r");
			MapList = Segment3Map.get(aos_segment3_r);
			if (MapList == null || MapList.size() == 0) {
				MapList = new ArrayList<DynamicObject>();
			}
			MapList.add(aos_entryentity);
			Segment3Map.put(aos_segment3_r, MapList);
		}

		// 开始拆分
		int r = 1;
		aos_entryentityS.clear();
		for (String key : Segment3Map.keySet()) {
			// 对于第一种产品号 保留于本单
			if (r == 1) {
				MapList = Segment3Map.get(key);
				for (int i = 0; i < MapList.size(); i++) {
					DynamicObject aos_entryentitylist = MapList.get(i);
					DynamicObject aos_entryentity = aos_entryentityS.addNew();
					aos_entryentity.set("aos_itemid", aos_entryentitylist.get("aos_itemid"));
					aos_entryentity.set("aos_require", aos_entryentitylist.get("aos_require"));
					aos_entryentity.set("aos_case", aos_entryentitylist.get("aos_case"));
					aos_entryentity.set("aos_write", aos_entryentitylist.get("aos_write"));
					aos_entryentity.set("aos_opt", aos_entryentitylist.get("aos_opt"));
					aos_entryentity.set("aos_pic", aos_entryentitylist.get("aos_pic"));
					aos_entryentity.set("aos_subtitle", aos_entryentitylist.get("aos_subtitle"));
					aos_entryentity.set("aos_title", aos_entryentitylist.get("aos_title"));
					aos_entryentity.set("aos_keyword", aos_entryentitylist.get("aos_keyword"));
					aos_entryentity.set("aos_other", aos_entryentitylist.get("aos_other"));
					aos_entryentity.set("aos_etc", aos_entryentitylist.get("aos_etc"));
					aos_entryentity.set("aos_segment3_r", aos_entryentitylist.get("aos_segment3_r"));
					aos_entryentity.set("aos_broitem_r", aos_entryentitylist.get("aos_broitem_r"));
					aos_entryentity.set("aos_itemname_r", aos_entryentitylist.get("aos_itemname_r"));
					aos_entryentity.set("aos_orgtext_r", aos_entryentitylist.get("aos_orgtext_r"));
					DynamicObjectCollection aos_attribute = aos_entryentity.getDynamicObjectCollection("aos_attribute");
					aos_attribute.clear();
					DynamicObjectCollection aos_attributefrom = aos_entryentitylist
							.getDynamicObjectCollection("aos_attribute");
					DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
					DynamicObject tempFile = null;
					for (DynamicObject d : aos_attributefrom) {
						tempFile = BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"),
								type);
						aos_attribute.addNew().set("fbasedataid", tempFile);
					}

					// 子单据体
					DynamicObjectCollection aos_subentryentityListS = aos_entryentitylist
							.getDynamicObjectCollection("aos_subentryentity");
					DynamicObjectCollection aos_subentryentityS = aos_entryentity
							.getDynamicObjectCollection("aos_subentryentity");
					for (DynamicObject aos_subentryentityList : aos_subentryentityListS) {
						DynamicObject aos_subentryentity = aos_subentryentityS.addNew();
						aos_subentryentity.set("aos_segment3", aos_subentryentityList.get("aos_segment3"));
						aos_subentryentity.set("aos_broitem", aos_subentryentityList.get("aos_broitem"));
						aos_subentryentity.set("aos_itemname", aos_subentryentityList.get("aos_itemname"));
						aos_subentryentity.set("aos_orgtext", aos_subentryentityList.get("aos_orgtext"));
						aos_subentryentity.set("aos_reqinput", aos_subentryentityList.get("aos_reqinput"));
						aos_subentryentity.set("aos_caseinput", aos_subentryentityList.get("aos_caseinput"));
					}
				}

			} else // 对于非第一种产品号 生成新的 小语种
			{
				DynamicObject aos_mkt_listing_minnew = BusinessDataServiceHelper
						.newDynamicObject("aos_mkt_listing_min");
				DynamicObjectCollection aos_entryentitynewS = aos_mkt_listing_minnew
						.getDynamicObjectCollection("aos_entryentity");
				aos_mkt_listing_minnew.set("aos_user", aos_mkt_listing_min.get("aos_user"));
				aos_mkt_listing_minnew.set("aos_sourcetype", aos_mkt_listing_min.get("aos_sourcetype"));
				aos_mkt_listing_minnew.set("aos_status", aos_mkt_listing_min.get("aos_status"));
				aos_mkt_listing_minnew.set("aos_requireby", aos_mkt_listing_min.get("aos_requireby"));
				aos_mkt_listing_minnew.set("aos_organization1", aos_mkt_listing_min.get("aos_organization1"));
				aos_mkt_listing_minnew.set("aos_organization2", aos_mkt_listing_min.get("aos_organization2"));
				aos_mkt_listing_minnew.set("aos_requiredate", aos_mkt_listing_min.get("aos_requiredate"));
				aos_mkt_listing_minnew.set("aos_demandate", aos_mkt_listing_min.get("aos_demandate"));
				aos_mkt_listing_minnew.set("aos_type", aos_mkt_listing_min.get("aos_type"));
				aos_mkt_listing_minnew.set("aos_source", aos_mkt_listing_min.get("aos_source"));
				aos_mkt_listing_minnew.set("aos_importance", aos_mkt_listing_min.get("aos_importance"));
				aos_mkt_listing_minnew.set("aos_designer", aos_mkt_listing_min.get("aos_designer"));
				aos_mkt_listing_minnew.set("aos_editor", aos_mkt_listing_min.get("aos_editor"));
				aos_mkt_listing_minnew.set("aos_editormin", aos_mkt_listing_min.get("aos_editormin"));
				aos_mkt_listing_minnew.set("aos_orgid", aos_mkt_listing_min.get("aos_orgid"));
				aos_mkt_listing_minnew.set("aos_osconfirmlov", aos_mkt_listing_min.get("aos_osconfirmlov"));
				aos_mkt_listing_minnew.set("aos_funconfirm", aos_mkt_listing_min.get("aos_funconfirm"));
				aos_mkt_listing_minnew.set("aos_orignbill", aos_mkt_listing_min.get("aos_orignbill"));
				aos_mkt_listing_minnew.set("aos_sourceid", aos_mkt_listing_min.get("aos_sourceid"));

				MapList = Segment3Map.get(key);
				for (int i = 0; i < MapList.size(); i++) {
					DynamicObject aos_entryentitylist = MapList.get(i);
					DynamicObject aos_entryentitynew = aos_entryentitynewS.addNew();
					aos_entryentitynew.set("aos_itemid", aos_entryentitylist.get("aos_itemid"));
					aos_entryentitynew.set("aos_require", aos_entryentitylist.get("aos_require"));
					aos_entryentitynew.set("aos_case", aos_entryentitylist.get("aos_case"));
					aos_entryentitynew.set("aos_write", aos_entryentitylist.get("aos_write"));
					aos_entryentitynew.set("aos_opt", aos_entryentitylist.get("aos_opt"));
					aos_entryentitynew.set("aos_pic", aos_entryentitylist.get("aos_pic"));
					aos_entryentitynew.set("aos_subtitle", aos_entryentitylist.get("aos_subtitle"));
					aos_entryentitynew.set("aos_title", aos_entryentitylist.get("aos_title"));
					aos_entryentitynew.set("aos_keyword", aos_entryentitylist.get("aos_keyword"));
					aos_entryentitynew.set("aos_other", aos_entryentitylist.get("aos_other"));
					aos_entryentitynew.set("aos_etc", aos_entryentitylist.get("aos_etc"));
					aos_entryentitynew.set("aos_segment3_r", aos_entryentitylist.get("aos_segment3_r"));
					aos_entryentitynew.set("aos_broitem_r", aos_entryentitylist.get("aos_broitem_r"));
					aos_entryentitynew.set("aos_itemname_r", aos_entryentitylist.get("aos_itemname_r"));
					aos_entryentitynew.set("aos_orgtext_r", aos_entryentitylist.get("aos_orgtext_r"));
					DynamicObjectCollection aos_attribute = aos_entryentitynew
							.getDynamicObjectCollection("aos_attribute");
					aos_attribute.clear();
					DynamicObjectCollection aos_attributefrom = aos_entryentitylist
							.getDynamicObjectCollection("aos_attribute");
					DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
					DynamicObject tempFile = null;
					for (DynamicObject d : aos_attributefrom) {
						tempFile = BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"),
								type);
						aos_attribute.addNew().set("fbasedataid", tempFile);
					}

					// 子单据体
					DynamicObjectCollection aos_subentryentityListS = aos_entryentitylist
							.getDynamicObjectCollection("aos_subentryentity");
					DynamicObjectCollection aos_subentryentityS = aos_entryentitynew
							.getDynamicObjectCollection("aos_subentryentity");
					for (DynamicObject aos_subentryentityList : aos_subentryentityListS) {
						DynamicObject aos_subentryentity = aos_subentryentityS.addNew();
						aos_subentryentity.set("aos_segment3", aos_subentryentityList.get("aos_segment3"));
						aos_subentryentity.set("aos_broitem", aos_subentryentityList.get("aos_broitem"));
						aos_subentryentity.set("aos_itemname", aos_subentryentityList.get("aos_itemname"));
						aos_subentryentity.set("aos_orgtext", aos_subentryentityList.get("aos_orgtext"));
						aos_subentryentity.set("aos_reqinput", aos_subentryentityList.get("aos_reqinput"));
						aos_subentryentity.set("aos_caseinput", aos_subentryentityList.get("aos_caseinput"));
					}
				}
				// 保存拆分单
				OperationServiceHelper.executeOperate("save", "aos_mkt_listing_min",
						new DynamicObject[] { aos_mkt_listing_minnew }, OperateOption.create());
			}
			r++;
		}
		// 保存本单
		OperationServiceHelper.executeOperate("save", "aos_mkt_listing_min",
				new DynamicObject[] { aos_mkt_listing_min }, OperateOption.create());
	}

	/**
	 * 如果是设计需求表类型 完成后生成一个 设计需求表 任务类型=翻译
	 * 
	 * @param p_aos_itemid
	 * @return
	 **/
	public static String GenerateDesign(DynamicObject dy_main, String p_aos_itemid, String orgid) throws FndError {
		// 数据层
		Object billno = dy_main.get("billno");
		Object ReqFId = dy_main.getPkValue(); // 当前界面主键
		DynamicObject dy_designer = dy_main.getDynamicObject("aos_designer");

		Object aos_requireby = dy_main.get("aos_requireby");
		Object aos_requirebyid = ((DynamicObject) aos_requireby).getPkValue();
		DynamicObject aos_orgid = dy_main.getDynamicObject("aos_orgid");
		if (!Cux_Common_Utl.IsNull(p_aos_itemid) && !Cux_Common_Utl.IsNull(orgid)) {
			// 来源类型为台账，且台账的国别不为空
			QFilter filter_id = new QFilter("id", "=", orgid);
			aos_orgid = QueryServiceHelper.queryOne("bd_country", "id,number", new QFilter[] { filter_id });
		}
		Object aos_orgnumber = aos_orgid.get("number");
		Object orgId = aos_orgid.get("id");
		DynamicObjectCollection aos_entryentityS = dy_main.getDynamicObjectCollection("aos_entryentity");

		String designerId = "";
		Object LastItemId = null;
		String MessageId = null;
		designerId = ProgressUtil.QueryEdit(orgId);
		if (dy_designer != null && FndGlobal.IsNull(designerId)) {
			MessageId = dy_designer.getPkValue() + "";
			designerId = dy_designer.getString("id");
		}

		// 初始化
		DynamicObject aos_mkt_designreq = BusinessDataServiceHelper.newDynamicObject("aos_mkt_designreq");
		aos_mkt_designreq.set("aos_requiredate", new Date());
		aos_mkt_designreq.set("aos_type", "翻译");
		aos_mkt_designreq.set("aos_orignbill", billno);
		aos_mkt_designreq.set("aos_sourceid", ReqFId);
		aos_mkt_designreq.set("aos_status", "设计");
		aos_mkt_designreq.set("aos_user", designerId);

		// BOTP
		aos_mkt_designreq.set("aos_sourcebilltype", "aos_mkt_listing_min");
		aos_mkt_designreq.set("aos_sourcebillno", dy_main.get("billno"));
		aos_mkt_designreq.set("aos_srcentrykey", "aos_entryentity");

		mkt.progress.design.aos_mkt_designreq_bill.setEntityValue(aos_mkt_designreq);
		if (designerId == null || designerId.equals("")) {
			aos_mkt_designreq.set("aos_user", aos_requirebyid);
		}
		aos_mkt_designreq.set("aos_requireby", aos_requireby);
		aos_mkt_designreq.set("aos_sourcetype", "LISTING");
		aos_mkt_designreq.set("aos_orgid", orgid);
		aos_mkt_designreq.set("aos_designer", designerId);

		List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(aos_requirebyid);
		if (MapList != null) {
			if (MapList.get(2) != null)
				aos_mkt_designreq.set("aos_organization1", MapList.get(2).get("id"));
			if (MapList.get(3) != null)
				aos_mkt_designreq.set("aos_organization2", MapList.get(3).get("id"));
		}

		// 循环货号生成
		DynamicObjectCollection des_entryentityS = aos_mkt_designreq.getDynamicObjectCollection("aos_entryentity");

		for (DynamicObject aos_entryentity : aos_entryentityS) {
			Object aos_itemid = aos_entryentity.get("aos_itemid.id");

			if (!Cux_Common_Utl.IsNull(p_aos_itemid) && !p_aos_itemid.equals(aos_itemid + ""))
				continue; // 若为功能图需求任务台账调用 则只生成对应货号数据

			DynamicObject des_entryentity = des_entryentityS.addNew();
			LastItemId = aos_itemid;
			System.out.println("aos_itemid =" + aos_itemid);
			String aos_contrybrandStr = "";
			String aos_orgtext = "";
			DynamicObject bd_material = BusinessDataServiceHelper.loadSingle(aos_itemid, "bd_material");
			DynamicObjectCollection aos_contryentryS = bd_material.getDynamicObjectCollection("aos_contryentry");
			// 获取所有国家品牌 字符串拼接 终止
			Set<String> set_bra = new HashSet<>();
			for (DynamicObject aos_contryentry : aos_contryentryS) {
				DynamicObject aos_nationality = aos_contryentry.getDynamicObject("aos_nationality");// 物料国别
				String aos_nationalitynumber = aos_nationality.getString("number");// 物料国别编码
				if ("IE".equals(aos_nationalitynumber))
					continue;
				Object org_id = aos_nationality.get("id"); // ItemId
				int OsQty = iteminfo.GetItemOsQty(org_id, aos_itemid);
				int onQty = sal.sche.aos_sal_sche_pub.aos_sal_sche_pvt.get_on_hand_qty(Long.valueOf(org_id.toString()),
						Long.valueOf(aos_itemid.toString()));
				OsQty += onQty;
				int SafeQty = iteminfo.GetSafeQty(org_id);
				if ("C".equals(aos_contryentry.getString("aos_contryentrystatus")) && OsQty < SafeQty)
					continue;
				aos_orgtext = aos_orgtext + aos_nationalitynumber + ";";

				Object obj = aos_contryentry.getDynamicObject("aos_contrybrand");
				if (obj == null)
					continue;
				String value = aos_contryentry.getDynamicObject("aos_contrybrand").getString("number");// 物料品牌编码
//				if (!aos_contrybrandStr.contains(value))
//					aos_contrybrandStr = aos_contrybrandStr + value + ";";

				if (value != null)
					set_bra.add(value);
				if (set_bra.size() > 1) {
					if (!aos_contrybrandStr.contains(value))
						aos_contrybrandStr = aos_contrybrandStr + value + ";";
				} else if (set_bra.size() == 1) {
					aos_contrybrandStr = value;
				}
			}

			String item_number = bd_material.getString("number");
			String url = "https://clss.s3.amazonaws.com/" + item_number + ".jpg";// 图片字段
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

			// 翻译类型的设计需求表需带出申请人要求
			des_entryentity.set("aos_desreq",aos_entryentity.get("aos_require") );
			des_entryentity.set("aos_itemid", aos_itemid);
			des_entryentity.set("aos_is_saleout", ProgressUtil.Is_saleout(aos_itemid));
			des_entryentity.set("aos_srcrowseq", aos_entryentity.get("SEQ"));
			des_entryentity.set("aos_remakes", aos_entryentity.get("aos_remakes"));  

			DynamicObjectCollection aos_subentryentityS = des_entryentity
					.getDynamicObjectCollection("aos_subentryentity");
			DynamicObject aos_subentryentity = aos_subentryentityS.addNew();
			aos_subentryentity.set("aos_sub_item", aos_itemid);
			aos_subentryentity.set("aos_segment3", aos_productno);
			aos_subentryentity.set("aos_itemname", aos_itemname);
			aos_subentryentity.set("aos_brand", aos_contrybrandStr);
			aos_subentryentity.set("aos_pic", url);
			aos_subentryentity.set("aos_developer", bd_material.get("aos_developer"));// 开发
			aos_subentryentity.set("aos_seting1", bd_material.get("aos_seting_cn"));
			aos_subentryentity.set("aos_seting2", bd_material.get("aos_seting_en"));
			aos_subentryentity.set("aos_spec", bd_material.get("aos_specification_cn"));
			aos_subentryentity.set("aos_url", MKTS3PIC.GetItemPicture(item_number));
			aos_subentryentity.set("aos_broitem", aos_broitem);
			aos_subentryentity.set("aos_orgtext", aos_orgtext);
		}

		// 产品类别
		String category = MKTCom.getItemCateNameZH(LastItemId);
		String[] category_group = category.split(",");
		String AosCategory1 = null;
		String AosCategory2 = null;
		int category_length = category_group.length;
		if (category_length > 0)
			AosCategory1 = category_group[0];
		if (category_length > 1)
			AosCategory2 = category_group[1];
		// 根据大类中类获取对应营销人员
		if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("") && !AosCategory2.equals("")) {
			String type = "";
			if (dy_main.get("aos_type") != null && !dy_main.getString("aos_type").equals(""))
				type = dy_main.getString("aos_type");
			String[] selectFields = new String[] { "aos_designeror", "aos_3d" };
			DynamicObject aos_mkt_proguser = ProgressUtil.findDesignerByType(orgId, AosCategory1, AosCategory2, type,
					selectFields);
			if (aos_mkt_proguser != null) {
				aos_mkt_designreq.set("aos_dm", aos_mkt_proguser.get("aos_designeror"));
				aos_mkt_designreq.set("aos_3der", aos_mkt_proguser.get("aos_3d"));
			}
		}

		// 翻译类型设计需求表 按照 营销国别参数表 图片翻译 获取申请人
		QFilter filter_org = new QFilter("aos_orgid.number", "=", aos_orgnumber);
		QFilter filter_type = new QFilter("aos_type", "=", "图片翻译");
		QFilter[] filters = new QFilter[] { filter_org, filter_type };
		String SelectStr = "aos_condition1";
		DynamicObject aos_mkt_base_orgvalue = QueryServiceHelper.queryOne("aos_mkt_base_orgvalue", SelectStr, filters);
		if (aos_mkt_base_orgvalue != null) {
			Object aos_condition1 = aos_mkt_base_orgvalue.get("aos_condition1");
			QFilter filter_name = new QFilter("name", "=", aos_condition1);
			filters = new QFilter[] { filter_name };
			DynamicObject bos_user = QueryServiceHelper.queryOne("bos_user", "id", filters);
			if (bos_user != null) {
				aos_mkt_designreq.set("aos_designer", bos_user.get("id"));
				aos_mkt_designreq.set("aos_user", bos_user.get("id"));
				MessageId = bos_user.getString("id");
			}
		}
		aos_mkt_designreq_bill.createDesiginBeforeSave(aos_mkt_designreq);
		OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_designreq",
				new DynamicObject[] { aos_mkt_designreq }, OperateOption.create());
		if (operationrst.isSuccess()) {
			Object pk = operationrst.getSuccessPkIds().get(0);
			FndHistory.Create("aos_mkt_designreq", pk.toString(), "新建",
					"新建节点: " + aos_mkt_designreq.getString("aos_status"));
		}

		// 修复关联关系
		try {
			ProgressUtil.botp("aos_mkt_designreq", aos_mkt_designreq.get("id"));
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
			MKTCom.SendGlobalMessage(MessageId, "aos_mkt_designreq", operationrst.getSuccessPkIds().get(0) + "",
					aos_mkt_designreq.getString("billno"), "设计需求表-Listing优化需求表子表自动创建翻译类型");
			FndHistory.Create(aos_mkt_designreq, aos_mkt_designreq.getString("aos_status"),
					"设计需求表-Listing优化需求表子表自动创建翻译类型");
		}
		return aos_mkt_designreq.getString("billno");
	}

	/** 如果是Listing类型生成销售信息确认单 **/
	private static void GenerateListingSal(DynamicObject dy_main) throws FndError {
		// 信息处理
		String ErrorMessage = "";
		String MessageId = null;
		String Message = "";
		// 数据层
		DynamicObject aos_designerObj = dy_main.getDynamicObject("aos_designer");// 设计
		Object aos_designer = null;
		if (!Cux_Common_Utl.IsNull(aos_designerObj))
			aos_designer = aos_designerObj.get("id");
		Object billno = dy_main.get("billno");
		Object ReqFId = dy_main.getPkValue(); // 当前界面主键
		Object aos_orgid = dy_main.get("aos_orgid");
		Object aos_type = dy_main.get("aos_type");
		DynamicObject aos_editorminObj = dy_main.getDynamicObject("aos_editormin");
		Object aos_editorminid = null;
		if (!Cux_Common_Utl.IsNull(aos_editorminObj))
			aos_editorminid = aos_editorminObj.get("id");
		;// 设计
			// 编辑确认师
		Object aos_make = null;
		DynamicObject dy_make = dy_main.getDynamicObject("aos_make");
		if (dy_make != null)
			aos_make = dy_make.getPkValue();

		DynamicObjectCollection aos_entryentityS = dy_main.getDynamicObjectCollection("aos_entryentity");
		// 初始化
		DynamicObject aos_mkt_listing_sal = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_sal");
		aos_mkt_listing_sal.set("aos_requireby", aos_make);
		aos_mkt_listing_sal.set("aos_designer", aos_designer);
		aos_mkt_listing_sal.set("aos_status", "销售确认");
		aos_mkt_listing_sal.set("aos_orgid", aos_orgid);
		aos_mkt_listing_sal.set("aos_orignbill", billno);
		aos_mkt_listing_sal.set("aos_sourceid", ReqFId);
		aos_mkt_listing_sal.set("aos_type", aos_type);
		aos_mkt_listing_sal.set("aos_requiredate", new Date());
		aos_mkt_listing_sal.set("aos_editor", aos_editorminid);
		aos_mkt_listing_sal.set("aos_sourcetype", "Listing优化需求表小语种");
		// BOTP
		aos_mkt_listing_sal.set("aos_sourcebilltype", "aos_mkt_listing_min");
		aos_mkt_listing_sal.set("aos_sourcebillno", dy_main.get("billno"));
		aos_mkt_listing_sal.set("aos_srcentrykey", "aos_entryentity");

		DynamicObjectCollection cmp_entryentityS = aos_mkt_listing_sal.getDynamicObjectCollection("aos_entryentity");
		for (DynamicObject aos_entryentity : aos_entryentityS) {
			DynamicObject aos_subentryentity = aos_entryentity.getDynamicObjectCollection("aos_subentryentity").get(0);
			DynamicObject cmp_entryentity = cmp_entryentityS.addNew();
			cmp_entryentity.set("aos_itemid", aos_entryentity.get("aos_itemid"));
			cmp_entryentity.set("aos_segment3", aos_subentryentity.get("aos_segment3"));
			cmp_entryentity.set("aos_itemname", aos_subentryentity.get("aos_itemname"));
			cmp_entryentity.set("aos_broitem", aos_subentryentity.get("aos_broitem"));
			cmp_entryentity.set("aos_salestatus", "已确认");
			cmp_entryentity.set("aos_text", aos_entryentity.get("aos_case"));
			cmp_entryentity.set("aos_srcrowseq", aos_entryentity.get("SEQ"));
		}

		Object ItemId = aos_entryentityS.get(0).getDynamicObject("aos_itemid").getPkValue();
		String category = MKTCom.getItemCateNameZH(ItemId);
		String[] category_group = category.split(",");
		String AosCategory1 = null;
		String AosCategory2 = null;
		int category_length = category_group.length;
		if (category_length > 0)
			AosCategory1 = category_group[0];
		if (category_length > 1)
			AosCategory2 = category_group[1];
		long aos_sale = 0;
		if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("") && !AosCategory2.equals("")) {
			QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
			QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
			String id = "";
			if (aos_orgid != null) {
				DynamicObject dy_org = (DynamicObject) aos_orgid;
				id = dy_org.getString("id");
			}

			QFilter filter_ou = new QFilter("aos_orgid", "=", id);
			QFilter[] filters_category = new QFilter[] { filter_category1, filter_category2, filter_ou };
			String SelectStr = "aos_salehelper aos_salehelper";
			DynamicObject aos_mkt_progorguser = QueryServiceHelper.queryOne("aos_mkt_progorguser", SelectStr,
					filters_category);
			if (aos_mkt_progorguser != null) {
				aos_sale = aos_mkt_progorguser.getLong("aos_salehelper");
			}
		}

		if (aos_sale == 0) {
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, AosCategory1 + "," + AosCategory2 + "国别销售不存在!");
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}

		aos_mkt_listing_sal.set("aos_sale", aos_sale);
		aos_mkt_listing_sal.set("aos_user", aos_sale);
		MessageId = aos_sale + "";
		Message = "Listing优化销售确认单-Listing优化销售确认表小语种自动创建";
		OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_sal",
				new DynamicObject[] { aos_mkt_listing_sal }, OperateOption.create());

		// 修复关联关系
		try {
			ProgressUtil.botp("aos_mkt_listing_sal", aos_mkt_listing_sal.get("id"));
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
			MKTCom.SendGlobalMessage(MessageId, aos_mkt_listing_sal + "", operationrst.getSuccessPkIds().get(0) + "",
					aos_mkt_listing_sal.getString("billno"), Message);
		}

	}

	/** 值校验 **/
	private static void SaveControl(DynamicObject dy_main) throws FndError {
		int ErrorCount = 0;
		String ErrorMessage = "";
		// 数据层
		Object aos_orgid = dy_main.get("aos_orgid");
		Object aos_sourcetype = dy_main.get("aos_sourcetype");
		Object aos_osconfirmlov = dy_main.get("aos_osconfirmlov");// 海外文字确认
		Object aos_funconfirm = dy_main.get("aos_funconfirm");// 海外功能图确认
		Object aos_type = dy_main.get("aos_type"); // 任务类型
		String aos_status = dy_main.getString("aos_status"); // 流程节点

		DynamicObject aos_organization2 = dy_main.getDynamicObject("aos_organization2");

		if ("是".equals(aos_osconfirmlov) && "是".equals(aos_funconfirm)) {
			ErrorCount++;
			ErrorMessage += "文字确认与功能图确认不能同时为是!";
		}

		if ("LISTING".equals(aos_sourcetype) && aos_orgid == null) {
			ErrorCount++;
			ErrorMessage += "Listing类型国别字段必填!";
		}

		// AddByCzj 2023/01/09 禅道反馈7472
		if (FndGlobal.IsNotNull(aos_organization2) && !"体验&文案部".equals(aos_organization2.getString("name"))
				&& "功能图翻译".equals(aos_type) && aos_status.equals("申请人")) {
			ErrorCount++;
			ErrorMessage += "只允许编辑人员提功能图翻译流程!";
		}

		// 校验
		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}
	}

	/** 全局状态控制 **/
	private void StatusControl() {
		// 数据层
		Object AosStatus = this.getModel().getValue("aos_status");
		Object AosUser = this.getModel().getValue("aos_user");
		String AosUserId = null;
		if (AosUser instanceof String)
			AosUserId = (String) AosUser;
		else if (AosUser instanceof Long)
			AosUserId = AosUser + "";
		else
			AosUserId = ((DynamicObject) AosUser).getString("id");
		Object CurrentUserId = UserServiceHelper.getCurrentUserId();
		//Object CurrentUserName = UserServiceHelper.getUserInfoByID((long) CurrentUserId).get("name");
		Object ReqFId = this.getModel().getDataEntity().getPkValue(); // 当前界面主键

		FndMsg.debug("into min StatusControl");
		
		// 锁住需要控制的字段
		this.getView().setVisible(false, "aos_back");
		this.getView().setVisible(true, "bar_save");

		// 当前节点操作人不为当前用户 全锁
		if (!AosUserId.equals(CurrentUserId.toString())) {
			this.getView().setEnable(false, "aos_contentpanelflex");// 主界面面板
			this.getView().setEnable(false, "bar_save");// 保存
			this.getView().setEnable(false, "aos_submit");// 提交
			this.getView().setEnable(false, "aos_import");// 引入数据
		}
		// 状态控制
		Map<String, Object> map = new HashMap<>();
		if ("编辑确认".equals(AosStatus)) {
			map.put(ClientProperties.Text, new LocaleString("编辑确认"));
			this.getView().updateControlMetadata("aos_submit", map);
			this.getView().setVisible(true, "aos_submit");
			this.getView().setEnable(true, "aos_contentpanelflex");// 主界面面板
			this.getView().setVisible(true, "aos_back");
		} else if ("申请人".equals(AosStatus)) {
			map.put(ClientProperties.Text, new LocaleString("提交"));
			this.getView().updateControlMetadata("aos_submit", map);
			this.getView().setVisible(false, "aos_submit");
			this.getView().setEnable(true, "aos_contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "aos_back");
			if (ReqFId != null && !"0".equals(ReqFId.toString()))
				this.getView().setVisible(true, "aos_submit");
		} else if ("海外编辑确认".equals(AosStatus)) {
			map.put(ClientProperties.Text, new LocaleString("海外编辑确认"));
			this.getView().updateControlMetadata("aos_submit", map);
			this.getView().setVisible(true, "aos_submit");
			this.getView().setEnable(true, "aos_contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "aos_back");
		} else if ("海外编辑确认:功能图".equals(AosStatus)) {
			map.put(ClientProperties.Text, new LocaleString(FndMsg.get("MKT_MINOSCONFIRM")));
			this.getView().updateControlMetadata("aos_submit", map);
			this.getView().setVisible(true, "aos_submit");
			this.getView().setEnable(true, "aos_contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "aos_back");
		} else if ("结束".equals(AosStatus)) {
			this.getView().setVisible(false, "aos_submit");
			this.getView().setEnable(false, "aos_contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "bar_save");

			this.getView().setVisible(false, "aos_import");// 引入数据
			this.getView().setVisible(false, "aos_refresh");
			this.getView().setVisible(false, "aos_close");
		}
	}

	/**
	 * 物料改变和导入数据后的单据行赋值
	 * 
	 * @param dy_row
	 */
	private static void EntityRowSetValue(DynamicObject dy_row) {
		DynamicObject aos_itemid = dy_row.getDynamicObject("aos_itemid");
		DynamicObject dy_parent = (DynamicObject) dy_row.getParent();
		if (aos_itemid == null) {
			dy_row.set("aos_segment3_r", null);
			dy_row.set("aos_broitem_r", null);
			dy_row.set("aos_itemname_r", null);
			dy_row.set("aos_orgtext_r", null);
			dy_row.set("aos_is_saleout", false);
			DynamicObjectCollection dyc_sub = dy_row.getDynamicObjectCollection("aos_subentryentity");
			if (dyc_sub.size() > 0) {
				DynamicObject dy_sub = dyc_sub.get(0);
				dy_sub.set("aos_segment3", null);
				dy_sub.set("aos_broitem", null);
				dy_sub.set("aos_itemname", null);
				dy_sub.set("aos_orgtext", null);

			}

		} else {
			DynamicObject AosItemidObject = aos_itemid;
			Object ItemId = AosItemidObject.getPkValue();
			String aos_orgtext = ProgressUtil.getOrderOrg(ItemId);
			DynamicObject bd_material = BusinessDataServiceHelper.loadSingle(ItemId, "bd_material");
			String item_number = bd_material.getString("number");
			String aos_productno = bd_material.getString("aos_productno");
			String aos_itemname = bd_material.getString("name");
			dy_row.set("aos_is_saleout", ProgressUtil.Is_saleout(ItemId));
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

			// 获取英语编辑
			String category = MKTCom.getItemCateNameZH(ItemId);
			String[] category_group = category.split(",");
			String AosCategory1 = null;
			String AosCategory2 = null;
			int category_length = category_group.length;
			if (category_length > 0)
				AosCategory1 = category_group[0];
			if (category_length > 1)
				AosCategory2 = category_group[1];

			long aos_editor = 0;
			if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("") && !AosCategory2.equals("")) {
				QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
				QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
				QFilter[] filters_category = new QFilter[] { filter_category1, filter_category2 };
				String SelectStr = "aos_eng aos_editor";
				DynamicObject aos_mkt_proguser = QueryServiceHelper.queryOne("aos_mkt_proguser", SelectStr,
						filters_category);
				if (aos_mkt_proguser != null) {
					aos_editor = aos_mkt_proguser.getLong("aos_editor");
				}
			}

			long aos_designer = 0;
			if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("") && !AosCategory2.equals("")) {
				String type = "";
				if (dy_parent.get("aos_type") != null && !dy_parent.getString("aos_type").equals(""))
					type = dy_parent.getString("aos_type");
				String orgId = "";
				if (dy_parent.get("aos_orgid") != null)
					orgId = dy_parent.getDynamicObject("aos_orgid").getString("id");
				String[] selectFields = new String[] { "aos_eng aos_editor" };
				DynamicObject aos_mkt_proguser = ProgressUtil.findDesignerByType(orgId, AosCategory1, AosCategory2,
						type, selectFields);
				if (aos_mkt_proguser != null) {
					aos_designer = aos_mkt_proguser.getLong("aos_designer");
				}
			}

			// 设计师
			if (dy_parent.get("aos_designer") == null)
				dy_parent.set("aos_designer", aos_designer);
			// 英语编辑师
			if (dy_parent.get("aos_editor") == null)
				dy_parent.set("aos_editor", aos_editor);

			// 赋值物料相关
			dy_row.set("aos_segment3_r", aos_productno);
			dy_row.set("aos_itemname_r", aos_itemname);
			dy_row.set("aos_broitem_r", aos_broitem);
			dy_row.set("aos_orgtext_r", aos_orgtext);

			DynamicObjectCollection dyc_sub = dy_row.getDynamicObjectCollection("aos_subentryentity");
			if (dyc_sub.size() == 0)
				dyc_sub.addNew();
			DynamicObject dy_subFirstRow = dyc_sub.get(0);
			dy_subFirstRow.set("aos_segment3", aos_productno);
			dy_subFirstRow.set("aos_itemname", aos_itemname);
			dy_subFirstRow.set("aos_broitem", aos_broitem);
			dy_subFirstRow.set("aos_orgtext", aos_orgtext);
		}
	}
}
