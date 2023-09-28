package mkt.progress.listing;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.*;

import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndHistory;
import common.fnd.FndMsg;
import common.fnd.FndReturn;
import common.fnd.FndWebHook;
import common.sal.sys.basedata.dao.ItemCategoryDao;
import common.sal.sys.basedata.dao.impl.ItemCategoryDaoImpl;
import common.sal.util.SalUtil;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.metadata.dynamicobject.DynamicObjectType;
import kd.bos.entity.EntityMetadataCache;
import kd.bos.entity.datamodel.events.ImportDataEventArgs;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.entity.property.EntryProp;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.control.events.RowClickEventListener;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.events.BeforeClosedEvent;
import kd.bos.form.events.BeforeDoOperationEventArgs;
import kd.bos.form.events.HyperLinkClickEvent;
import kd.bos.form.events.HyperLinkClickListener;
import kd.bos.form.operate.FormOperate;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
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
import mkt.progress.iface.iteminfo;

public class aos_mkt_listingreq_bill extends AbstractBillPlugIn
		implements ItemClickListener, HyperLinkClickListener, RowClickEventListener {
	private final static Log log = LogFactory.getLog("ProductAdjustPriceBill");
	/** 系统管理员 **/
	public final static String system = Cux_Common_Utl.SYSTEM;

	@Override
	public void hyperLinkClick(HyperLinkClickEvent hyperLinkClickEvent) {
		int RowIndex = hyperLinkClickEvent.getRowIndex();
		String FieldName = hyperLinkClickEvent.getFieldName();
		if ("aos_likeno".equals(FieldName)) {
			Object aos_likeid = this.getModel().getValue("aos_likeid", RowIndex);
			Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_listing_req", aos_likeid);
		}
	}

	@Override
	public void registerListener(EventObject e) {
		super.registerListener(e);
		this.addItemClickListeners("aos_toolbarap");// 给工具栏加监听事件
		this.addItemClickListeners("aos_submit"); // 提交
		this.addItemClickListeners("aos_open"); // 打开产品资料变跟单

		EntryGrid entryGrid = this.getControl("aos_subentryentity");
		entryGrid.addRowClickListener(this);
		entryGrid.addHyperClickListener(this);
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
			else if ("aos_open".equals(Control))
				aos_open();// 打开产品资料变跟单
		} catch (FndError fndError) {
			fndError.show(getView(), FndWebHook.urlMms);
		} catch (Exception ex) {
			FndError.showex(getView(), ex, FndWebHook.urlMms);
		}
	}

	private void aos_open() {
		Object aos_productid = this.getModel().getValue("aos_productid");
		DynamicObject aos_prodatachan = QueryServiceHelper.queryOne("aos_prodatachan", "id",
				new QFilter[] { new QFilter("id", QCP.equals, aos_productid) });
		if (!Cux_Common_Utl.IsNull(aos_prodatachan))
			Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_prodatachan", aos_prodatachan.get("id"));
		else
			this.getView().showErrorNotification("产品资料变跟单信息不存在!");
	}

	@Override
	public void afterImportData(ImportDataEventArgs e) {
		DynamicObject mainDyEntity = this.getModel().getDataEntity(true);
		DynamicObject aos_requireby = mainDyEntity.getDynamicObject("aos_requireby");
		if (aos_requireby != null) {
			mainDyEntity.set("aos_orgid", ProgressUtil.getOrgByOrganizate(aos_requireby.getPkValue()));
		}
		// SaveServiceHelper.save(new DynamicObject[] { mainDyEntity });
		try {
			Object pkValue = mainDyEntity.getPkValue();
			DynamicObjectCollection dyc_sub = mainDyEntity.getDynamicObjectCollection("aos_entryentity");
			for (DynamicObject dy_row : dyc_sub) {
				EntityRowSetValue(dy_row, pkValue);
			}
			SaveServiceHelper.save(new DynamicObject[] { mainDyEntity });
		} catch (FndError fndMessage) {
			e.setCancel(true);
			e.setCancelMessages(0, 0, Arrays.asList(fndMessage.getErrorMessage()));
			fndMessage.printStackTrace();
		} catch (Exception e1) {
			e.setCancel(true);
			StringWriter sw = new StringWriter();
			e1.printStackTrace(new PrintWriter(sw));
			String write = sw.toString();
			if (write.length() > 2000) {
				write = write.substring(0, 1999);
			}
			e.setCancelMessages(0, 0, Arrays.asList(e1.getMessage()));
			log.error("listing需求优化子表导入异常  " + write.toString());
			e1.printStackTrace();
		}
	}

	/** 打开历史记录 **/
	private void aos_history() throws FndError {
		Cux_Common_Utl.OpenHistory(this.getView());
	}

	/** 初始化事件 **/
	public void afterLoadData(EventObject e) {
		super.afterLoadData(e);
		StatusControl();
	}

	public void afterDoOperation(AfterDoOperationEventArgs args) {
		FormOperate formOperate = (FormOperate) args.getSource();
		String Operatation = formOperate.getOperateKey();
		try {
			if ("save".equals(Operatation)) {
				StatusControl();
				this.getView().invokeOperation("refresh");
			}
		} catch (FndError fndMessage) {
			this.getView().showTipNotification(fndMessage.getErrorMessage());
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
		}
	}

	/** 保存之前 **/
	public void beforeDoOperation(BeforeDoOperationEventArgs args) {
		FormOperate formOperate = (FormOperate) args.getSource();
		String Operatation = formOperate.getOperateKey();
		try {
			if ("save".equals(Operatation)) {
				DynamicObject dy_main = this.getModel().getDataEntity(true);
				SaveControl(dy_main); // 保存校验
				setItemCate(dy_main);
			}
		} catch (FndError fndMessage) {
			this.getView().showTipNotification(fndMessage.getErrorMessage());
			args.setCancel(true);
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
			args.setCancel(true);
		}
	}

	/** 新建事件 **/
	public void afterCreateNewData(EventObject e) {
		super.afterCreateNewData(e);
		StatusControl();
		InitDefualt();
		this.getView().setVisible(false, "aos_submit");
	}

	/** 界面关闭事件 **/
	public void beforeClosed(BeforeClosedEvent e) {
		super.beforeClosed(e);
		e.setCheckDataChange(false);
	}

	/** 值改变事件 **/
	public void propertyChanged(PropertyChangedArgs e) {
		String name = e.getProperty().getName();
		try {
			if (name.equals("aos_itemid")) {
				int rowIndex = e.getChangeSet()[0].getRowIndex();
				AosItemChange(rowIndex);
			} else if (name.equals("aos_type"))
				AosTypeChange();
		} catch (FndError fndMessage) {
			this.getView().showTipNotification(fndMessage.getErrorMessage());
			if (name.equals("aos_itemid")) {
				this.getModel().setValue("aos_itemid", null, e.getChangeSet()[0].getRowIndex());
			}
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
		}

	}

	/** 任务类型改变 **/
	private void AosTypeChange() {
		Object aos_type = this.getModel().getValue("aos_type");
		Object aos_requiredate = this.getModel().getValue("aos_requiredate");
		Object aos_demandate = new Date();
		if ("四者一致".equals(aos_type)) {
			aos_demandate = Cux_Common_Utl.add_NoWeekendDays((Date) aos_requiredate, 1);
		} else if ("老品优化".equals(aos_type))
			aos_demandate = Cux_Common_Utl.add_NoWeekendDays((Date) aos_requiredate, 3);
		this.getModel().setValue("aos_demandate", aos_demandate);
	}

	/** 新建设置默认值 **/
	private void InitDefualt() {
		long CurrentUserId = UserServiceHelper.getCurrentUserId();
		List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(CurrentUserId);
		if (MapList != null) {
			if (MapList.size() >= 3 && MapList.get(2) != null)
				this.getModel().setValue("aos_organization1", MapList.get(2).get("id"));
			if (MapList.size() >= 4 && MapList.get(3) != null)
				this.getModel().setValue("aos_organization2", MapList.get(3).get("id"));
		}

		// 根据组织获取国别
		String aos_unit = Cux_Common_Utl.GetUserOrgLong(CurrentUserId);
		this.getModel().setValue("aos_unit", aos_unit);
		if (aos_unit.contains("美国")) {
			aos_unit("US");
		} else if (aos_unit.contains("加拿大")) {
			aos_unit("CA");
		} else if (aos_unit.contains("英国")) {
			aos_unit("UK");
		} else if (aos_unit.contains("英国销售部")) {
			aos_unit("UK");
		} else if (aos_unit.contains("德国")) {
			aos_unit("DE");
		} else if (aos_unit.contains("法国")) {
			aos_unit("FR");
		} else if (aos_unit.contains("意大利")) {
			aos_unit("IT");
		} else if (aos_unit.contains("西班牙")) {
			aos_unit("ES");
		} else if (aos_unit.contains("AOSOM LLC")) {
			aos_unit("US");
		} else if (aos_unit.contains("AOSOM CANADA INC")) {
			aos_unit("CA");
		} else if (aos_unit.contains("MH STAR UK LTD")) {
			aos_unit("UK");
		} else if (aos_unit.contains("MH HANDEL GMBH")) {
			aos_unit("DE");
		} else if (aos_unit.contains("MH FRANCE")) {
			aos_unit("FR");
		} else if (aos_unit.contains("AOSOM ITALY SRL")) {
			aos_unit("IT");
		} else if (aos_unit.contains("SPANISH AOSOM, S.L.")) {
			aos_unit("ES");
		}
	}

	private void aos_unit(String value) {
		QFilter filter_unit = new QFilter("number", "=", value);
		QFilter[] filters = new QFilter[] { filter_unit };
		String SelectColumn = "id,number";
		DynamicObject bd_country = QueryServiceHelper.queryOne("bd_country", SelectColumn, filters);
		if (bd_country != null)
			this.getModel().setValue("aos_orgid", bd_country.get("id"));
	}

	/** 物料值改变 **/
	private void AosItemChange(int row) throws FndError {
		if (row < 0)
			return;
		else {
			Object pkValue = this.getModel().getDataEntity(true).getPkValue();
			DynamicObject dy_row = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity")
					.get(row);
			EntityRowSetValue(dy_row, pkValue);
			this.getView().updateView("aos_entryentity", row);
		}
	}

	/** 提交 **/
	public void aos_submit(DynamicObject dy_main, String type) throws FndError {
		SaveControl(dy_main);// 先做数据校验判断是否可以提交
		String aos_status = dy_main.getString("aos_status");// 根据状态判断当前流程节点
		switch (aos_status) {
		case "申请人":
			SubmitForNew(dy_main);
			break;
		}
		setItemCate(dy_main);
		// 保存
		SaveServiceHelper.save(new DynamicObject[] { dy_main });
		FndHistory.Create(dy_main, "提交", aos_status);
		// 界面提交，提交后眼状态控制
		if (type.equals("A")) {
			this.getView().invokeOperation("refresh");
			StatusControl();// 提交完成后做新的界面状态控制
		}
	}

	/** 值校验 **/
	private static void SaveControl(DynamicObject dy_main) throws FndError {
		int ErrorCount = 0;
		String ErrorMessage = "";
		// 数据层
		Object aos_type = dy_main.get("aos_type");

		// 校验
		if (Cux_Common_Utl.IsNull(aos_type)) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "任务类型必填!");
		}

		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}
	}

	/** 全局状态控制 **/
	private void StatusControl() {
		// 数据层
		Object AosStatus = this.getModel().getValue("aos_status");
		DynamicObject AosUser = (DynamicObject) this.getModel().getValue("aos_user");
		Object CurrentUserId = UserServiceHelper.getCurrentUserId();
		Object CurrentUserName = UserServiceHelper.getUserInfoByID((long) CurrentUserId).get("name");

		// 图片控制
		// InitPic();

		// 锁住需要控制的字段

		// 当前节点操作人不为当前用户 全锁
		if (!AosUser.getPkValue().toString().equals(CurrentUserId.toString())
				&& !"程震杰".equals(CurrentUserName.toString()) && !"陈聪".equals(CurrentUserName.toString())) {
			this.getView().setEnable(false, "titlepanel");// 标题面板
			this.getView().setEnable(false, "aos_contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "bar_save");
			this.getView().setVisible(false, "aos_submit");
		}
		// 状态控制
		if ("申请人".equals(AosStatus)) {
			this.getView().setVisible(true, "aos_submit");
			this.getView().setEnable(true, "aos_contentpanelflex");// 主界面面板
			this.getView().setVisible(true, "bar_save");
		} else if ("已完成".equals(AosStatus)) {
			this.getView().setVisible(false, "aos_submit");
			this.getView().setEnable(false, "aos_contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "bar_save");

			this.getView().setVisible(false, "aos_import");
			this.getView().setVisible(false, "aos_refresh");

		}
	}

	/** 编辑确认状态下提交 **/
	private static void SubmitForNew(DynamicObject dy_main) throws FndError {
		// 异常参数
		FndReturn Retrun = new FndReturn();
		// 数据层

		// 校验
		if (Retrun.GetErrorCount() > 0) {
			throw new FndError(Retrun);
		}

		// 循环每一行
		DynamicObjectCollection aos_entryentityS = dy_main.getDynamicObjectCollection("aos_entryentity");
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
			GenerateListingSon(ListRequire, Retrun, dy_main);
			if (Retrun.GetErrorCount() > 0) {
				Retrun.AddErrorMessage("文案需求生成Listing优化子表失败!");
				throw new FndError(Retrun);
			}
		}

		if (ListRequirePic != null && ListRequirePic.size() > 0) {
			GenerateDesignReq(ListRequirePic, Retrun, dy_main);
			if (Retrun.GetErrorCount() > 0) {
				Retrun.AddErrorMessage("图片需求生成设计需求表失败!");
				throw new FndError(Retrun);
			}
		}
		dy_main.set("aos_status", "已完成"); // 设置单据流程状态
		dy_main.set("aos_user", system); // 设置操作人为系统管理员
	}

	/**
	 * 文案需求生成Listing优化子表
	 * 
	 * @param Retrun
	 * @param dy_main
	 **/
	public static void GenerateListingSon(List<DynamicObject> listRequire, FndReturn Retrun, DynamicObject dy_main)
			throws FndError {
		// 异常处理
		Retrun.clear();
		// 信息处理
		Boolean MessageFlag = false;
		String MessageId = null;
		String Message = "";
		// 数据层
		Object aos_designer = dy_main.get("aos_designer");
		Object billno = dy_main.get("billno");
		Object ReqFId = dy_main.get("id"); // 当前界面主键
		Object aos_type = dy_main.get("aos_type");// 任务类型
		Object aos_source = dy_main.get("aos_source");// 任务来源
		Object aos_importance = dy_main.get("aos_importance");// 紧急程度
		Object aos_editor = dy_main.get("aos_editor");
		Object aos_demandate = dy_main.get("aos_demandate");
		Object aos_ismalllov = "否";
		Object aos_orgid = dy_main.get("aos_orgid");
		Object aos_requireby = dy_main.get("aos_requireby");
		Object aos_productbill = dy_main.get("aos_productbill");
		Object aos_productid = dy_main.get("aos_productid");

		Object aos_orgnumber = null;
		DynamicObject aos_organization1 = dy_main.getDynamicObject("aos_organization1");
		String aos_organization1Str = aos_organization1.getString("number");
		Object aos_osconfirmlov = null;

		if (aos_orgid != null) {
			aos_orgnumber = ((DynamicObject) aos_orgid).get("number");
			if ("US".equals(aos_orgnumber) || "CA".equals(aos_orgnumber) || "UK".equals(aos_orgnumber))
				aos_ismalllov = "否";
			else
				aos_ismalllov = "是";
		} else {
			if ("老品优化".equals(aos_type)) {
				aos_ismalllov = null;
			} else
				aos_ismalllov = "是";
		}

		if ("体验&文案部".equals(aos_organization1Str))
			aos_ismalllov = null;// 若为编辑体验&文案部 默认为空
		if ("四者一致".equals(aos_type)) {
			aos_ismalllov = "是";
			aos_osconfirmlov = "否";
		}

		// 循环创建
		for (int i = 0; i < listRequire.size(); i++) {
			DynamicObject dyn3d_r = listRequire.get(i);
			// 头信息
			// 根据国别大类中类取对应营销US编辑
			Object ItemId = dyn3d_r.getDynamicObject("aos_itemid").getPkValue();
			String category = MKTCom.getItemCateNameZH(ItemId);
			String[] category_group = category.split(",");
			String AosCategory1 = null;
			String AosCategory2 = null;
			int category_length = category_group.length;
			if (category_length > 0)
				AosCategory1 = category_group[0];
			if (category_length > 1)
				AosCategory2 = category_group[1];
			long aos_editordefualt = 0;
			long aos_designerdefualt = 0;
			if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("") && !AosCategory2.equals("")) {
				String type = "";
				if (dy_main.get("aos_type") != null && !dy_main.getString("aos_type").equals(""))
					type = dy_main.getString("aos_type");
				String orgId = "";
				if (dy_main.get("aos_orgid") != null)
					orgId = dy_main.getDynamicObject("aos_orgid").getString("id");
				String[] selectFields = new String[] { "aos_eng aos_editor" };
				DynamicObject aos_mkt_designer = ProgressUtil.findDesignerByType(orgId, AosCategory1, AosCategory2,
						type, selectFields);
				DynamicObject dy_editor = ProgressUtil.findEditorByType(AosCategory1, AosCategory2, type);
				if (aos_mkt_designer != null) {
					aos_designerdefualt = aos_mkt_designer.getLong("aos_designer");
				}
				if (dy_editor != null)
					aos_editordefualt = dy_editor.getLong("aos_user");
			}

			if (aos_editordefualt == 0 && aos_editor == null) {
				Retrun.AddErrorCount();
				Retrun.AddErrorMessage("英语品类编辑师不存在!");
				return;
			}

			if (aos_designerdefualt == 0 && aos_designer == null) {
				Retrun.AddErrorCount();
				Retrun.AddErrorMessage("默认品类设计师不存在!");
				return;
			}

			if (aos_editor != null)
				aos_editordefualt = (long) ((DynamicObject) aos_editor).getPkValue();

			if (aos_designer != null)
				aos_designerdefualt = (long) ((DynamicObject) aos_designer).getPkValue();

			// 根据国别编辑产品号 合并单据
			DynamicObject aos_mkt_listing_son = null;
			QFilter filter_editor = new QFilter("aos_editor", "=", aos_editordefualt);
			QFilter filter_sourceid = new QFilter("aos_sourceid", "=", ReqFId);
			String aos_segment3 = dyn3d_r.getString("aos_segment3");
			QFilter filter_ment = new QFilter("aos_entryentity.aos_segment3_r", "=", aos_segment3);
			QFilter[] filters = new QFilter[] { filter_editor, filter_sourceid, filter_ment };
			DynamicObject aos_mkt_listing_sonq = QueryServiceHelper.queryOne("aos_mkt_listing_son", "id", filters);
			if (aos_mkt_listing_sonq == null) {
				aos_mkt_listing_son = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_son");
				MessageFlag = true;
				aos_mkt_listing_son.set("aos_requireby", aos_requireby);
				aos_mkt_listing_son.set("aos_requiredate", new Date());
				aos_mkt_listing_son.set("aos_type", aos_type);
				aos_mkt_listing_son.set("aos_source", aos_source);
				aos_mkt_listing_son.set("aos_importance", aos_importance);
				aos_mkt_listing_son.set("aos_designer", aos_designerdefualt);
				aos_mkt_listing_son.set("aos_editor", aos_editordefualt);
				aos_mkt_listing_son.set("aos_user", aos_editordefualt);
				aos_mkt_listing_son.set("aos_orignbill", billno);
				aos_mkt_listing_son.set("aos_sourceid", ReqFId);
				aos_mkt_listing_son.set("aos_status", "编辑确认");
				aos_mkt_listing_son.set("aos_sourcetype", "LISTING");
				aos_mkt_listing_son.set("aos_demandate", aos_demandate);// 要求完成日期
				aos_mkt_listing_son.set("aos_ismalllov", aos_ismalllov);
				aos_mkt_listing_son.set("aos_orgid", aos_orgid);
				aos_mkt_listing_son.set("aos_osconfirmlov", aos_osconfirmlov);
				aos_mkt_listing_son.set("aos_productbill", aos_productbill);
				aos_mkt_listing_son.set("aos_productid", aos_productid);
				// BOTP
				aos_mkt_listing_son.set("aos_sourcebilltype", "aos_mkt_listing_req");
				aos_mkt_listing_son.set("aos_sourcebillno", dy_main.get("billno"));
				aos_mkt_listing_son.set("aos_srcentrykey", "aos_entryentity");

				List<DynamicObject> MapList = Cux_Common_Utl
						.GetUserOrg(dy_main.getDynamicObject("aos_requireby").getPkValue());
				if (MapList != null) {
					if (MapList.size() >= 3 && MapList.get(2) != null)
						aos_mkt_listing_son.set("aos_organization1", MapList.get(2).get("id"));
					if (MapList.size() >= 4 && MapList.get(3) != null)
						aos_mkt_listing_son.set("aos_organization2", MapList.get(3).get("id"));
				}
			} else {
				aos_mkt_listing_son = BusinessDataServiceHelper.loadSingle(aos_mkt_listing_sonq.getLong("id"),
						"aos_mkt_listing_son");
			}

			// 明细
			DynamicObjectCollection aos_entryentityS = aos_mkt_listing_son
					.getDynamicObjectCollection("aos_entryentity");
			DynamicObject aos_entryentity = aos_entryentityS.addNew();
			aos_entryentity.set("aos_itemid", ItemId);
			aos_entryentity.set("aos_is_saleout", ProgressUtil.Is_saleout(ItemId));
			aos_entryentity.set("aos_require", dyn3d_r.get("aos_require"));
			aos_entryentity.set("aos_srcrowseq", dyn3d_r.get("SEQ"));

			DynamicObjectCollection aos_attribute = aos_entryentity.getDynamicObjectCollection("aos_attribute");
			aos_attribute.clear();
			DynamicObjectCollection aos_attributefrom = dyn3d_r.getDynamicObjectCollection("aos_attribute");
			DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
			DynamicObject tempFile = null;
			for (DynamicObject d : aos_attributefrom) {
				tempFile = BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"), type);
				aos_attribute.addNew().set("fbasedataid", tempFile);
			}

			// 物料相关信息
			DynamicObjectCollection aos_subentryentityS = aos_entryentity
					.getDynamicObjectCollection("aos_subentryentity");
			DynamicObject aos_subentryentity = aos_subentryentityS.addNew();
			aos_subentryentity.set("aos_segment3", dyn3d_r.get("aos_segment3"));
			aos_subentryentity.set("aos_broitem", dyn3d_r.get("aos_broitem"));
			aos_subentryentity.set("aos_itemname", dyn3d_r.get("aos_itemname"));
			aos_subentryentity.set("aos_orgtext", dyn3d_r.get("aos_orgtext"));
			aos_subentryentity.set("aos_reqinput", dyn3d_r.get("aos_require"));

			aos_entryentity.set("aos_segment3_r", dyn3d_r.get("aos_segment3"));
			aos_entryentity.set("aos_broitem_r", dyn3d_r.get("aos_broitem"));
			aos_entryentity.set("aos_itemname_r", dyn3d_r.get("aos_itemname"));
			aos_entryentity.set("aos_orgtext_r", ProgressUtil.getOrderOrg(ItemId));
			aos_mkt_listingson_bill.setListSonUserOrganizate(aos_mkt_listing_son);
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_son",
					new DynamicObject[] { aos_mkt_listing_son }, OperateOption.create());

			FndHistory.Create("aos_mkt_listing_son", operationrst.getSuccessPkIds().get(0).toString(), "优化需求生成文案",
					"到编辑确认节点");

			// 修复关联关系
			try {
				ProgressUtil.botp("aos_mkt_listing_son", aos_mkt_listing_son.get("id"));
			} catch (Exception ex) {
				ex.printStackTrace();
			}

			if (MessageFlag) {
				MessageId = aos_editordefualt + "";
				Message = "Listing优化需求表子表-Listing优化需求自动创建";
				if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
					MKTCom.SendGlobalMessage(MessageId, aos_mkt_listing_son + "",
							operationrst.getSuccessPkIds().get(0) + "", aos_mkt_listing_son.getString("billno"),
							Message);
				}
			}
		}
	}

	/**
	 * 图片需求生成设计需求表
	 * 
	 * @param listRequirePic
	 * @param dy_main
	 **/
	public static void GenerateDesignReq(List<DynamicObject> listRequirePic, FndReturn Retrun, DynamicObject dy_main) {

		// 异常处理
		Retrun.clear();
		// 信息处理
		Boolean MessageFlag = false;
		String MessageId = null;
		String Message = "";
		// 数据层
		Object aos_designer = dy_main.get("aos_designer");// 设计
		Object aos_editor = dy_main.get("aos_editor");// 编辑
		Object billno = dy_main.get("billno");
		Object ReqFId = dy_main.get("id"); // 当前界面主键

		Object aos_source = dy_main.get("aos_source");// 任务来源
		if (dy_main.getBoolean("aos_autoflag") && FndGlobal.IsNull(dy_main.get("aos_productbill")))
			aos_source = "老品重拍-传图";

		Object aos_importance = dy_main.get("aos_importance");// 紧急程度
		Object aos_orgid = dy_main.get("aos_orgid"); // 国别
		Object aos_requireby = dy_main.get("aos_requireby"); // 申请人

		Object aos_productbill = dy_main.get("aos_productbill");
		Object aos_productid = dy_main.get("aos_productid");

		// 循环创建
		for (int i = 0; i < listRequirePic.size(); i++) {
			DynamicObject dyn3d_r = listRequirePic.get(i);
			String aos_segment3 = dyn3d_r.getString("aos_segment3");// 产品号
			// 根据国别大类中类取对应营销US编辑
			Object ItemId = dyn3d_r.getDynamicObject("aos_itemid").getPkValue();
			String category = MKTCom.getItemCateNameZH(ItemId);
			String[] category_group = category.split(",");
			String AosCategory1 = null;
			String AosCategory2 = null;
			int category_length = category_group.length;
			if (category_length > 0)
				AosCategory1 = category_group[0];
			if (category_length > 1)
				AosCategory2 = category_group[1];
			long aos_editordefualt = 0;
			long aos_designerdefualt = 0;
			long aos_designeror = 0;
			long aos_3d = 0;
			if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("") && !AosCategory2.equals("")) {
				String type = "";
				if (dy_main.get("aos_type") != null && !dy_main.getString("aos_type").equals(""))
					type = dy_main.getString("aos_type");
				String orgId = "";
				if (dy_main.get("aos_orgid") != null)
					orgId = dy_main.getDynamicObject("aos_orgid").getString("id");
				String[] selectFields = new String[] { "aos_eng aos_editor", "aos_designeror", "aos_3d" };
				DynamicObject aos_mkt_proguser = ProgressUtil.findDesignerByType(orgId, AosCategory1, AosCategory2,
						type, selectFields);
				if (aos_mkt_proguser != null) {
					aos_editordefualt = aos_mkt_proguser.getLong("aos_editor");
					aos_designerdefualt = aos_mkt_proguser.getLong("aos_designer");
					aos_designeror = aos_mkt_proguser.getLong("aos_designeror");
					aos_3d = aos_mkt_proguser.getLong("aos_3d");
				}
			}

			if (aos_editordefualt == 0 && aos_editor == null) {
				Retrun.AddErrorCount();
				Retrun.AddErrorMessage("英语品类编辑师不存在!");
				return;
			}

			if (aos_designerdefualt == 0 && aos_designer == null) {
				Retrun.AddErrorCount();
				Retrun.AddErrorMessage("默认品类设计师不存在!");
				return;
			}

			if (aos_editor != null)
				aos_editordefualt = (long) ((DynamicObject) aos_editor).getPkValue();

			if (aos_designer != null)
				aos_designerdefualt = (long) ((DynamicObject) aos_designer).getPkValue();

			// 根据国别编辑合并单据
			DynamicObject aos_mkt_designreq = null;
			QFilter filter_segment3 = new QFilter("aos_groupseg", "=", aos_segment3);
			QFilter filter_sourceid = new QFilter("aos_sourceid", "=", ReqFId);
			QFilter[] filters = new QFilter[] { filter_segment3, filter_sourceid };
			DynamicObject aos_mkt_designreqq = QueryServiceHelper.queryOne("aos_mkt_designreq", "id", filters);
			if (aos_mkt_designreqq == null) {
				aos_mkt_designreq = BusinessDataServiceHelper.newDynamicObject("aos_mkt_designreq");
				MessageFlag = true;
				aos_mkt_designreq.set("aos_groupseg", aos_segment3);
				aos_mkt_designreq.set("aos_requiredate", new Date());
				aos_mkt_designreq.set("aos_type", dy_main.get("aos_type"));
				aos_mkt_designreq.set("aos_shipdate", null);
				aos_mkt_designreq.set("aos_orignbill", billno);
				aos_mkt_designreq.set("aos_sourceid", ReqFId);
				aos_mkt_designreq.set("aos_status", "设计");
				aos_mkt_designreq.set("aos_user", aos_designerdefualt);
				aos_mkt_designreq.set("aos_source", aos_source);
				aos_mkt_designreq.set("aos_importance", aos_importance);
				aos_mkt_designreq.set("aos_designer", aos_designerdefualt);
				aos_mkt_designreq.set("aos_dm", aos_designeror);
				aos_mkt_designreq.set("aos_3der", aos_3d);
				aos_mkt_designreq.set("aos_orgid", aos_orgid);
				aos_mkt_designreq.set("aos_sourcetype", "LISTING");
				aos_mkt_designreq.set("aos_requireby", aos_requireby);

				aos_mkt_designreq.set("aos_productbill", aos_productbill);
				aos_mkt_designreq.set("aos_productid", aos_productid);

				// BOTP
				aos_mkt_designreq.set("aos_sourcebilltype", "aos_mkt_listing_req");
				aos_mkt_designreq.set("aos_sourcebillno", dy_main.get("billno"));
				aos_mkt_designreq.set("aos_srcentrykey", "aos_entryentity");

				List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(((DynamicObject) aos_requireby).getPkValue());
				if (MapList != null) {
					if (MapList.size() >= 3 && MapList.get(2) != null)
						aos_mkt_designreq.set("aos_organization1", MapList.get(2).get("id"));
					if (MapList.size() >= 4 && MapList.get(3) != null)
						aos_mkt_designreq.set("aos_organization2", MapList.get(3).get("id"));
				}
			} else {
				aos_mkt_designreq = BusinessDataServiceHelper.loadSingle(aos_mkt_designreqq.getLong("id"),
						"aos_mkt_designreq");
			}

			DynamicObjectCollection aos_entryentityS = aos_mkt_designreq.getDynamicObjectCollection("aos_entryentity");
			String aos_contrybrandStr = "";
			String aos_orgtext = "";
			DynamicObject bd_material = BusinessDataServiceHelper.loadSingle(ItemId, "bd_material");
			DynamicObjectCollection aos_contryentryS = bd_material.getDynamicObjectCollection("aos_contryentry");
			// 获取所有国家品牌 字符串拼接 终止
			Set<String> set_bra = new HashSet<>();
			for (DynamicObject aos_contryentry : aos_contryentryS) {
				DynamicObject aos_nationality = aos_contryentry.getDynamicObject("aos_nationality");
				String aos_nationalitynumber = aos_nationality.getString("number");
				if ("IE".equals(aos_nationalitynumber))
					continue;

				Object org_id = aos_nationality.get("id"); // ItemId
				int OsQty = iteminfo.GetItemOsQty(org_id, ItemId);
				int SafeQty = iteminfo.GetSafeQty(org_id);
				// 安全库存 海外库存
				if ("C".equals(aos_contryentry.getString("aos_contryentrystatus")) && OsQty < SafeQty)
					continue;
				aos_orgtext = aos_orgtext + aos_nationalitynumber + ";";

				Object obj = aos_contryentry.getDynamicObject("aos_contrybrand");
				if (obj == null)
					continue;
				String value = aos_nationalitynumber + "~"
						+ aos_contryentry.getDynamicObject("aos_contrybrand").getString("number");
//				if (!aos_contrybrandStr.contains(value))
//					aos_contrybrandStr = aos_contrybrandStr + value + ";";

				String bra = aos_contryentry.getDynamicObject("aos_contrybrand").getString("number");
				if (bra != null)
					set_bra.add(bra);
				if (set_bra.size() > 1) {
					if (!aos_contrybrandStr.contains(value))
						aos_contrybrandStr = aos_contrybrandStr + value + ";";
				} else if (set_bra.size() == 1) {
					aos_contrybrandStr = bra;
				}
			}
			String item_number = bd_material.getString("number");
			String url = "https://clss.s3.amazonaws.com/" + item_number + ".jpg";// 图片字段
			String aos_productno = bd_material.getString("aos_productno");
			String aos_itemname = bd_material.getString("name");
			// 获取同产品号物料
			QFilter filter_productno = new QFilter("aos_productno", QCP.equals, aos_productno);
			filters = new QFilter[] { filter_productno };
			String aos_broitem = "";
			if (!Cux_Common_Utl.IsNull(aos_productno)) {
				DynamicObjectCollection bd_materialS = QueryServiceHelper.query("bd_material",  "id,number,aos_type",
						new QFilter("aos_productno", QCP.equals, aos_productno).and("aos_type", QCP.equals, "A")
								.toArray());
				for (DynamicObject bd : bd_materialS) {
					Object itemid = bd_material.get("id");
					if ("B".equals(bd.getString("aos_type")))
						continue; // 配件不获取
					
					Boolean exist = QueryServiceHelper.exists("bd_material", new QFilter("id", QCP.equals, itemid)
							.and("aos_contryentry.aos_contryentrystatus", QCP.not_equals, "C"));
					if (!exist)
						continue;
					// 全球终止不取
					int osQty = getOsQty(itemid);
					if (osQty < 10)
						continue;
					String number = bd.getString("number");
					if (item_number.equals(number))
						continue;
					else
						aos_broitem = aos_broitem + number + ";";
				}
			}

			DynamicObject aos_entryentity = aos_entryentityS.addNew();
			aos_entryentity.set("aos_itemid", ItemId);
			aos_entryentity.set("aos_is_saleout", ProgressUtil.Is_saleout(ItemId));
			aos_entryentity.set("aos_desreq", dyn3d_r.get("aos_requirepic"));
			aos_entryentity.set("aos_language", dyn3d_r.get("aos_language"));
			aos_entryentity.set("aos_3d", dyn3d_r.get("aos_3d"));
			aos_entryentity.set("aos_srcrowseq", dyn3d_r.get("SEQ"));

			DynamicObjectCollection aos_attribute = aos_entryentity.getDynamicObjectCollection("aos_attribute");
			aos_attribute.clear();
			DynamicObjectCollection aos_attributefrom = dyn3d_r.getDynamicObjectCollection("aos_attribute");
			DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
			DynamicObject tempFile = null;
			for (DynamicObject d : aos_attributefrom) {
				tempFile = BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"), type);
				aos_attribute.addNew().set("fbasedataid", tempFile);
			}

			DynamicObjectCollection aos_subentryentityS = aos_entryentity
					.getDynamicObjectCollection("aos_subentryentity");
			DynamicObject aos_subentryentity = aos_subentryentityS.addNew();
			aos_subentryentity.set("aos_sub_item", ItemId);
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

			mkt.progress.design.aos_mkt_designreq_bill.setEntityValue(aos_mkt_designreq);
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_designreq",
					new DynamicObject[] { aos_mkt_designreq }, OperateOption.create());

			FndHistory.Create("aos_mkt_designreq", operationrst.getSuccessPkIds().get(0).toString(), "优化需求生成设计完成",
					"到设计节点");

			// 修复关联关系
			try {
				ProgressUtil.botp("aos_mkt_designreq", aos_mkt_designreq.get("id"));
			} catch (Exception ex) {
				ex.printStackTrace();
			}

			if (MessageFlag) {
				MessageId = aos_designerdefualt + "";
				Message = "设计需求表-Listing优化需求自动创建";
				if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
					MKTCom.SendGlobalMessage(MessageId, aos_mkt_designreq + "",
							operationrst.getSuccessPkIds().get(0) + "", aos_mkt_designreq.getString("billno"), Message);
					FndHistory.Create(aos_mkt_designreq, aos_mkt_designreq.getString("aos_status"),
							"设计需求表-Listing优化需求自动创建");
				}
			}
		}
	}

	/**
	 * 物料改变和导入数据后的单据行赋值
	 * 
	 * @param dy_row
	 * @param FId
	 */
	private static void EntityRowSetValue(DynamicObject dy_row, Object FId) throws FndError {
		DynamicObject aos_itemid = dy_row.getDynamicObject("aos_itemid");
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		calendar.add(Calendar.DAY_OF_MONTH, -90);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		if (aos_itemid == null) {
			// 清空
			dy_row.set("aos_segment3", null);
			dy_row.set("aos_broitem", null);
			dy_row.set("aos_itemname", null);
			dy_row.set("aos_orgtext", null);

			// 删除子单据体
			DynamicObjectCollection dyc_rowSubEntity = dy_row.getDynamicObjectCollection("aos_subentryentity");
			Iterator<DynamicObject> iterator = dyc_rowSubEntity.iterator();
			while (iterator.hasNext()) {
				iterator.next();
				iterator.remove();
			}
		} else {
			DynamicObject AosItemidObject = aos_itemid;
			Object ItemId = AosItemidObject.getPkValue();
			String aos_orgtext = "";
			DynamicObject bd_material = BusinessDataServiceHelper.loadSingle(ItemId, "bd_material");
			String item_number = bd_material.getString("number");
			String aos_productno = bd_material.getString("aos_productno");
			String aos_itemname = bd_material.getString("name");
			Boolean aos_iscomb = bd_material.getBoolean("aos_iscomb");

			DynamicObjectCollection aos_contryentryS = bd_material.getDynamicObjectCollection("aos_contryentry");
			// 字符串拼接
			for (DynamicObject aos_contryentry : aos_contryentryS) {
				DynamicObject aos_nationality = aos_contryentry.getDynamicObject("aos_nationality");
				String aos_nationalitynumber = aos_nationality.getString("number");
				if ("IE".equals(aos_nationalitynumber))
					continue;
				Object org_id = aos_nationality.get("id"); // ItemId
				int OsQty = iteminfo.GetItemOsQty(org_id, ItemId);
				int SafeQty = iteminfo.GetSafeQty(org_id);
				// 终止、小于安全库存
				if ("C".equals(aos_contryentry.getString("aos_contryentrystatus")) && OsQty < SafeQty)
					continue;
				// 代卖、小于安全库存
				if ("F".equals(aos_contryentry.getString("aos_contryentrystatus")) && OsQty < SafeQty)
					continue;
				// 虚拟上架、小于安全库存
				if ("H".equals(aos_contryentry.getString("aos_contryentrystatus")) && OsQty < SafeQty)
					continue;
				aos_orgtext = aos_orgtext + aos_nationalitynumber + ";";
			}

			// 提示：所有国别已终止，且无库存，不建议优化listing
			if (FndGlobal.IsNull(aos_orgtext) && !aos_iscomb) {
				throw new FndError("所有国别已终止，且无库存，不建议优化listing!");
			}

			// 获取同产品号物料
			String aos_broitem = "";
			if (!Cux_Common_Utl.IsNull(aos_productno)) {
				DynamicObjectCollection bd_materialS = QueryServiceHelper.query("bd_material", "id,number,aos_type",
						new QFilter("aos_productno", QCP.equals, aos_productno).and("aos_type", QCP.equals, "A")
								.toArray());
				for (DynamicObject bd : bd_materialS) {
					Object itemid = bd.getString("id");
					if ("B".equals(bd.getString("aos_type")))
						continue; // 配件不获取
					DynamicObject one = QueryServiceHelper.queryOne("bd_material", "id", new QFilter("number", "=", bd.getString("number"))
							.and("aos_contryentry.aos_contryentrystatus","!=", "C").toArray());
					FndMsg.debug(FndGlobal.IsNull(one));
					if (FndGlobal.IsNull(one))
						continue;// 全球终止不取
					int osQty = getOsQty(itemid);
					FndMsg.debug(FndGlobal.IsNull(osQty));
					if (osQty < 10)
						continue;
					String number = bd.getString("number");
					FndMsg.debug(FndGlobal.IsNull(number));
					if (item_number.equals(number))
						continue;
					else
						aos_broitem = aos_broitem + number + ";";
				}
			}
			
			// 赋值物料相关
			dy_row.set("aos_segment3", aos_productno);
			dy_row.set("aos_itemname", aos_itemname);
			dy_row.set("aos_broitem", aos_broitem);
			dy_row.set("aos_orgtext", aos_orgtext);

			// 对近期流程进行赋值
			QFilter filter_item = new QFilter("aos_entryentity.aos_itemid", QCP.equals, ItemId);
			QFilter filter_fid = null;
			if (FId != null)
				filter_fid = new QFilter("id", QCP.not_equals, FId);
			QFilter filter_date = new QFilter("aos_requiredate", QCP.large_equals, date_from_str);
			QFilter[] filters = new QFilter[] { filter_item, filter_fid, filter_date };
			DynamicObjectCollection aos_mkt_listing_reqS = QueryServiceHelper.query("aos_mkt_listing_req",
					"id,billno," + "aos_entryentity.aos_require aos_require,"
							+ "aos_entryentity.aos_requirepic aos_requirepic," + "aos_requireby,aos_requiredate",
					filters);
			// 删除子单据体
			DynamicObjectCollection dyc_rowSubEntity = dy_row.getDynamicObjectCollection("aos_subentryentity");
			Iterator<DynamicObject> iterator = dyc_rowSubEntity.iterator();
			while (iterator.hasNext()) {
				iterator.next();
				iterator.remove();
			}

			int i = 0;
			for (DynamicObject aos_mkt_listing_req : aos_mkt_listing_reqS) {
				aos_mkt_listing_req.get("billno");
				DynamicObject dy_subNew = dyc_rowSubEntity.addNew();
				dy_subNew.set("SEQ", i);
				dy_subNew.set("aos_likeno", aos_mkt_listing_req.get("billno"));
				dy_subNew.set("aos_requirelast", aos_mkt_listing_req.get("aos_require"));
				dy_subNew.set("aos_requirepiclast", aos_mkt_listing_req.get("aos_requirepic"));
				dy_subNew.set("aos_likeid", aos_mkt_listing_req.get("id"));
				dy_subNew.set("aos_lastrequire", aos_mkt_listing_req.get("aos_requireby"));
				dy_subNew.set("aos_lastdate", aos_mkt_listing_req.get("aos_requiredate"));
				i++;
			}
		}
	}

	/**
	 * 获取物料全球海外库存
	 * 
	 * @param itemid
	 * @return
	 */
	public static int getOsQty(Object itemid) {
		int aos_instock_qty = 0;
		DynamicObjectCollection aos_sync_invou_valueS = QueryServiceHelper.query("aos_sync_invou_value",
				"aos_instock_qty", new QFilter("aos_item", QCP.equals, itemid).toArray());
		for (DynamicObject aos_sync_invou_value : aos_sync_invou_valueS) {
			aos_instock_qty += aos_sync_invou_value.getInt("aos_instock_qty");
		}
		return aos_instock_qty;
	}

	/** 创建优化需求表后给物料的大类赋值 **/
	public static void setItemCate(DynamicObject dy_main) {
		DynamicObjectCollection dyc_entity = dy_main.getDynamicObjectCollection("aos_entryentity");
		ItemCategoryDao itemCategoryDao = new ItemCategoryDaoImpl();
		for (DynamicObject dy_ent : dyc_entity) {
			Object aos_item = dy_ent.get("aos_itemid");
			Object itemid;
			if (aos_item instanceof Long) {
				itemid = aos_item;
			} else if (aos_item instanceof DynamicObject) {
				itemid = ((DynamicObject) aos_item).get("id");
			} else if (aos_item instanceof EntryProp) {
				itemid = ((DynamicObject) aos_item).get("id");
			} else if (aos_item instanceof String) {
				itemid = aos_item;
			} else
				return;
			String cateNameZH = itemCategoryDao.getItemCateNameZH(itemid);
			if (Cux_Common_Utl.IsNull(cateNameZH)) {
				continue;
			}
			String[] split = cateNameZH.split(",");
			dy_ent.set("aos_category1", split[0]);
		}
	}
}