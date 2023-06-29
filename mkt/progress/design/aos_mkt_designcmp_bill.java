package mkt.progress.design;

import java.util.*;
import java.util.stream.Collectors;

import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndHistory;
import common.sal.util.SalUtil;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.dataentity.metadata.dynamicobject.DynamicObjectType;
import kd.bos.entity.EntityMetadataCache;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.ClientProperties;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.events.BeforeClosedEvent;
import kd.bos.form.events.HyperLinkClickEvent;
import kd.bos.form.events.HyperLinkClickListener;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.MKTCom;
import mkt.progress.ProgressUtil;

public class aos_mkt_designcmp_bill extends AbstractBillPlugIn implements ItemClickListener, HyperLinkClickListener {
	/** 系统管理员 **/
	public final static String system = Cux_Common_Utl.SYSTEM;

	@Override
	public void hyperLinkClick(HyperLinkClickEvent hyperLinkClickEvent) {
		int RowIndex = hyperLinkClickEvent.getRowIndex();
		String FieldName = hyperLinkClickEvent.getFieldName();
		if ("aos_segment3".equals(FieldName)) {
			Object aos_segment3 = this.getModel().getValue("aos_segment3", RowIndex);
			DynamicObject aos_mkt_functreq = QueryServiceHelper.queryOne("aos_mkt_functreq", "id",
					new QFilter[] { new QFilter("aos_segment3", QCP.equals, aos_segment3) });
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
		
		EntryGrid entryGrid = this.getControl("aos_subentryentity");
		entryGrid.addHyperClickListener(this);
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String Control = evt.getItemKey();
		try {
			if ("aos_submit".equals(Control))
				aos_submit(this.getModel().getDataEntity(true),"A");
			else if ("aos_back".equals(Control))
				aos_back();
			else if ("aos_open".equals(Control))
				aos_open();
		} catch (FndError fndMessage) {
			this.getView().showTipNotification(fndMessage.getErrorMessage());
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
		}
	}

	private void aos_open() {
		Object aos_sourceid= this.getModel().getValue("aos_sourceid");
		Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_designreq", aos_sourceid);
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

	/** 界面关闭事件 **/
	public void beforeClosed(BeforeClosedEvent e) {
		super.beforeClosed(e);
		e.setCheckDataChange(false);
	}

	/** 销售退回 **/
	private void aos_back() throws FndError {
		String MessageId ;
		String ErrorMessage = "";
		String Message = "设计完成表(国别)-销售退回";
		Object ReqFId = this.getModel().getDataEntity().getPkValue(); // 当前界面主键
		Object billno = this.getModel().getValue("billno");
		Object aos_requireby = this.getModel().getValue("aos_requireby");
		boolean reasonflag = false;
		// 校验 销售退回时，明细中必须有一行有退回原因；
		DynamicObjectCollection aos_entryentityS = this.getModel().getEntryEntity("aos_entryentity");

		for (DynamicObject aos_entryentity : aos_entryentityS) {
			String aos_reason = aos_entryentity.getString("aos_reason");
			if (!Cux_Common_Utl.IsNull(aos_reason))
				reasonflag = true;
		}

		if (!reasonflag) {
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "销售退回时，明细中必须有一行有退回原因!");
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}

		MessageId = aos_requireby + "";
		this.getModel().setValue("aos_status", "申请人");// 设置单据流程状态
		this.getModel().setValue("aos_user", aos_requireby);// 流转给编辑
		this.getView().invokeOperation("save");
		this.getView().invokeOperation("refresh");
		MKTCom.SendGlobalMessage(MessageId, "aos_mkt_designcmp", ReqFId + "", billno + "", Message);
	}

	/** 提交 **/
	public void aos_submit(DynamicObject dy_main,String type) throws FndError {
		SaveControl();// 先做数据校验判断是否可以提交
		String aos_status = dy_main.getString("aos_status");// 根据状态判断当前流程节点
		switch (aos_status) {
		case "申请人":
			SubmitForApply(dy_main); break;
		case "销售确认":
			SubmitForSale(dy_main);
			break;
		case "设计传图":
			SubmitForEditor(dy_main);
			break;
		}
		FndHistory.Create(dy_main,"提交",aos_status);
		SaveServiceHelper.save(new DynamicObject[]{dy_main});
		if (type.equals("A")){
//			this.getView().invokeOperation("save");
			this.getView().invokeOperation("refresh");
			StatusControl();// 提交完成后做新的界面状态控制
		}
	}

	/** 设计传图 **/
	private static void SubmitForEditor(DynamicObject dy_mian) throws FndError {
		// 数据层
		Object aos_orgid = dy_mian.getDynamicObject("aos_orgid");
		String aos_orgnumber = null;
		Object aos_language = dy_mian.getDynamicObjectCollection("aos_entryentity").get(0).get("aos_language");
		String aos_languagenumber = null;
		if (aos_orgid != null) {
			aos_orgnumber = ((DynamicObject) aos_orgid).getString("number");
		}
		if (aos_language != null)
			aos_languagenumber = (String) aos_language;
        
		int total = 0;
		DynamicObjectCollection aos_entryentityS = dy_mian.getDynamicObjectCollection("aos_entryentity");
		for (DynamicObject aos_entryentity : aos_entryentityS) {
			if (!aos_entryentity.getBoolean("aos_valid")) {
				continue;
			} else {
				total += aos_entryentity.getInt("aos_whited") + aos_entryentity.getInt("aos_whites")
						+ aos_entryentity.getInt("aos_backround") + aos_entryentity.getInt("aos_sizepic")
						+ aos_entryentity.getInt("aos_anew") + aos_entryentity.getInt("aos_alike")
						+ aos_entryentity.getInt("aos_trans") + aos_entryentity.getInt("aos_despic")
						+ aos_entryentity.getInt("aos_ps") + aos_entryentity.getInt("aos_fix");
			}
		}

		// 第一行功能图翻译语种为EN才走老逻辑 不然都生成确认单
		if ("EN".equals(aos_languagenumber)) {
			if ("US".equals(aos_orgnumber) || "CA".equals(aos_orgnumber) || "UK".equals(aos_orgnumber))
				GenerateListingSal(dy_mian);// 英语国别生成销售确认单
			else if (total == 0)
				GenerateListingLanguage(dy_mian);// 小语种国别生成listing优化需求-小语种
		} else {
			GenerateListingSal(dy_mian);// 生成销售确认单
		}

		dy_mian.set("aos_status", "结束");
		dy_mian.set("aos_user", system);
	}

	/** 创建Listing优化需求表小语种 **/
	private static void GenerateListingLanguage( DynamicObject dy_main) throws FndError {

		// 信息参数
		String MessageId ;
		String Message ;
		// 异常参数
		int ErrorCount = 0;
		String ErrorMessage = "";
		// 数据层
		DynamicObject AosDesigner =  dy_main.getDynamicObject("aos_designer");
		DynamicObject aos_orgid = dy_main.getDynamicObject("aos_orgid");
		String orgid = null;
		if (!Cux_Common_Utl.IsNull(aos_orgid))
		 orgid = aos_orgid.getString("id");
		Object AosDesignerId = AosDesigner.getPkValue();
		Object billno = dy_main.get("billno");
		Object ReqFId = dy_main.getPkValue(); // 当前界面主键
		Object aos_type = dy_main.get("aos_type");// 任务类型
		Object aos_source = dy_main.get("aos_source");// 任务来源
		Object aos_importance = dy_main.get("aos_importance");// 紧急程度
		Object aos_requireby = dy_main.getDynamicObject("aos_requireby");// 设计需求表申请人
		List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(((DynamicObject) aos_requireby).getPkValue());
		Object LastItemId = null;
		Object aos_demandate = new Date();
		if ("四者一致".equals(aos_type)) {
			aos_demandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 1);
		} else if ("老品优化".equals(aos_type))
			aos_demandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 3);
		// 校验
		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}
		//校验小语种是否重复
		DuplicateCheck(dy_main);

		//生成小语种
		DynamicObject aos_mkt_listing_min = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_min");
		aos_mkt_listing_min.set("aos_requireby", aos_requireby);
		aos_mkt_listing_min.set("aos_requiredate", new Date());
		aos_mkt_listing_min.set("aos_type", "功能图翻译");
		aos_mkt_listing_min.set("aos_source", aos_source);
		aos_mkt_listing_min.set("aos_importance", aos_importance);
		aos_mkt_listing_min.set("aos_designer", AosDesignerId);
		aos_mkt_listing_min.set("aos_orignbill", billno);
		aos_mkt_listing_min.set("aos_sourceid", ReqFId);
		aos_mkt_listing_min.set("aos_status", "编辑确认");
		aos_mkt_listing_min.set("aos_sourcetype", "CMP"); // 小语种来源类型为设计完成表
		aos_mkt_listing_min.set("aos_orgid", aos_orgid);
		aos_mkt_listing_min.set("aos_demandate", aos_demandate);

		if (MapList != null) {
			if (MapList.get(2) != null)
				aos_mkt_listing_min.set("aos_organization1", MapList.get(2).get("id"));
			if (MapList.get(3) != null)
				aos_mkt_listing_min.set("aos_organization2", MapList.get(3).get("id"));
		}

		DynamicObjectCollection mkt_listing_minS = aos_mkt_listing_min.getDynamicObjectCollection("aos_entryentity");

		DynamicObjectCollection aos_entryentityS = dy_main.getDynamicObjectCollection("aos_entryentity");

		// 循环所有行
		for (DynamicObject aos_entryentity : aos_entryentityS) {

			if (!aos_entryentity.getBoolean("aos_valid"))
				continue;

			DynamicObject mkt_listing_min = mkt_listing_minS.addNew();
			DynamicObject subentryentity = aos_entryentity.getDynamicObjectCollection("aos_subentryentity").get(0);
			LastItemId = aos_entryentity.get("aos_itemid.id");

			mkt_listing_min.set("aos_itemid", aos_entryentity.get("aos_itemid"));
			mkt_listing_min.set("aos_is_saleout",ProgressUtil.Is_saleout(LastItemId));
			mkt_listing_min.set("aos_require", aos_entryentity.get("aos_desreq"));

			// 附件
			DynamicObjectCollection aos_attribute = mkt_listing_min.getDynamicObjectCollection("aos_attribute");
			aos_attribute.clear();
			DynamicObjectCollection aos_attributefrom = aos_entryentity.getDynamicObjectCollection("aos_attribute");
			DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
			DynamicObject tempFile = null;
			for (DynamicObject d : aos_attributefrom) {
				tempFile = BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"), type);
				aos_attribute.addNew().set("fbasedataid", tempFile);
			}
			DynamicObjectCollection aos_subentryentityS = mkt_listing_min
					.getDynamicObjectCollection("aos_subentryentity");
			DynamicObject aos_subentryentity = aos_subentryentityS.addNew();
			aos_subentryentity.set("aos_segment3", subentryentity.get("aos_segment3"));
			aos_subentryentity.set("aos_broitem", subentryentity.get("aos_broitem"));
			aos_subentryentity.set("aos_itemname", subentryentity.get("aos_itemname"));
			aos_subentryentity.set("aos_orgtext", subentryentity.get("aos_orgtext"));
			aos_subentryentity.set("aos_reqinput", aos_entryentity.get("aos_desreq"));

			mkt_listing_min.set("aos_segment3_r", subentryentity.get("aos_segment3"));
			mkt_listing_min.set("aos_broitem_r", subentryentity.get("aos_broitem"));
			mkt_listing_min.set("aos_itemname_r", subentryentity.get("aos_itemname"));
			mkt_listing_min.set("aos_orgtext_r", ProgressUtil.getOrderOrg(aos_entryentity.getDynamicObject("aos_itemid").getPkValue()));

		}

		// 根据循环中最后一个物料去获取对应的 英语编辑师 小语种编辑师 LastItemId
		String category = MKTCom.getItemCateNameZH(LastItemId );
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
		if (aos_editor == 0) {
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, AosCategory1 + "," + AosCategory2 + "英语编辑不存在!");
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}
		long aos_oueditor = 0;
		if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("") && !AosCategory2.equals("")) {
			DynamicObject aos_mkt_progorguser = ProgressUtil.minListtFindEditorByType(orgid,AosCategory1,AosCategory2,aos_type.toString());
			if (aos_mkt_progorguser != null) {
				aos_oueditor = aos_mkt_progorguser.getLong("aos_user");
			}
		}
		
		/*FndMsg.debug("aos_oueditor ="+aos_oueditor);
		FndMsg.debug("aos_orgid ="+aos_orgid);*/
		
		if (aos_oueditor == 0) {
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, AosCategory1 + "," + AosCategory2 + "小语种编辑师不存在!");
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}
		aos_mkt_listing_min.set("aos_editormin", aos_oueditor);// 小语种编辑师
		aos_mkt_listing_min.set("aos_user", aos_oueditor);

		MessageId = aos_oueditor + "";
		Message = "Listing优化需求表小语种-设计完成表自动创建";
		OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_min",
				new DynamicObject[] { aos_mkt_listing_min }, OperateOption.create());
		if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
			MKTCom.SendGlobalMessage(MessageId, aos_mkt_listing_min + "", operationrst.getSuccessPkIds().get(0) + "",
					aos_mkt_listing_min.getString("billno"), Message);
			FndHistory.Create(dy_main, MessageId, "生成小语种");
			FndHistory.Create(aos_mkt_listing_min, aos_mkt_listing_min.getString("aos_status"),
					"Listing优化需求表小语种-设计完成表自动创建");
		}
	}

	/** 生成销售确认单 **/
	private static void GenerateListingSal(DynamicObject dy_mian) {
		// 信息处理
		String ErrorMessage = "";
		String MessageId = null;
		String Message = "";
		// 数据层
		Object aos_designer =  dy_mian.getDynamicObject("aos_designer").getPkValue();// 设计
		//编辑确认师傅
		Object billno = dy_mian.get("billno");
		Object ReqFId = dy_mian.getPkValue(); // 当前界面主键
		Object aos_orgid = dy_mian.getDynamicObject("aos_orgid");
		Object aos_type = dy_mian.get("aos_type");
		Object aos_orgnumber = ((DynamicObject) aos_orgid).getString("number");

		Object ItemId =  dy_mian.getDynamicObjectCollection("aos_entryentity").get(0).getDynamicObject("aos_itemid").getPkValue();
		String category = MKTCom.getItemCateNameZH(ItemId);
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
			QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
			QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
			QFilter filter_ou = new QFilter("aos_orgid.number", "=", aos_orgnumber);
			QFilter[] filters_category = new QFilter[] { filter_category1, filter_category2, filter_ou };
			String SelectStr = "aos_oueditor";
			DynamicObject aos_mkt_progorguser = QueryServiceHelper.queryOne("aos_mkt_progorguser", SelectStr,
					filters_category);
			if (aos_mkt_progorguser != null) {
				aos_oueditor = aos_mkt_progorguser.getLong("aos_oueditor");
			}
		}
		if (aos_oueditor == 0) {
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, AosCategory1 + "," + AosCategory2 + "国别编辑师不存在!");
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}

		DynamicObjectCollection aos_entryentityS = dy_mian.getDynamicObjectCollection("aos_entryentity");
		// 初始化
		DynamicObject aos_mkt_listing_sal = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_sal");

		aos_mkt_listing_sal.set("aos_requireby", aos_designer);
		aos_mkt_listing_sal.set("aos_designer", aos_designer);
		
		aos_mkt_listing_sal.set("aos_status", "销售确认");
		aos_mkt_listing_sal.set("aos_orgid", aos_orgid);
		aos_mkt_listing_sal.set("aos_orignbill", billno);
		aos_mkt_listing_sal.set("aos_sourceid", ReqFId);
		aos_mkt_listing_sal.set("aos_type", aos_type);
		aos_mkt_listing_sal.set("aos_requiredate", new Date());
		aos_mkt_listing_sal.set("aos_editor", aos_oueditor);
		aos_mkt_listing_sal.set("aos_sourcetype", "设计完成表");

		DynamicObjectCollection cmp_entryentityS = aos_mkt_listing_sal.getDynamicObjectCollection("aos_entryentity");
		for (DynamicObject aos_entryentity : aos_entryentityS) {
			if (!aos_entryentity.getBoolean("aos_valid"))
				continue;
			DynamicObject aos_subentryentity = aos_entryentity.getDynamicObjectCollection("aos_subentryentity").get(0);
			DynamicObject cmp_entryentity = cmp_entryentityS.addNew();
			cmp_entryentity.set("aos_itemid", aos_entryentity.get("aos_itemid"));
			cmp_entryentity.set("aos_segment3", aos_subentryentity.get("aos_segment3"));
			cmp_entryentity.set("aos_itemname", aos_subentryentity.get("aos_itemname"));
			cmp_entryentity.set("aos_broitem", aos_subentryentity.get("aos_broitem"));
			cmp_entryentity.set("aos_salestatus", "已确认");
			cmp_entryentity.set("aos_text", aos_entryentity.get("aos_designway"));
		}


		long aos_sale = 0;
		
		if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("")
				&& !AosCategory2.equals("")) {
			QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
			QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
			QFilter filter_ou = new QFilter("aos_orgid.number", "=", aos_orgnumber);
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
		Message = "Listing优化销售确认单-设计完成表国别自动创建";
		OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_sal",
				new DynamicObject[] { aos_mkt_listing_sal }, OperateOption.create());
		if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
			MKTCom.SendGlobalMessage(MessageId, aos_mkt_listing_sal + "", operationrst.getSuccessPkIds().get(0) + "",
					aos_mkt_listing_sal.getString("billno"), Message);
			FndHistory.Create(dy_mian, MessageId, "生成销售确认单");
			FndHistory.Create(aos_mkt_listing_sal, aos_mkt_listing_sal.getString("aos_status"),
					"Listing优化需求表小语种-设计完成表自动创建");
		}

	}

	/** 销售确认 **/
	private static void SubmitForSale(DynamicObject dy_main) throws FndError {
		// 数据层
		String MessageId = null;
		String Message = "设计完成表(国别)-销售确认";
		DynamicObject aos_designer = dy_main.getDynamicObject("aos_designer");
		Object ReqFId = dy_main.getPkValue(); // 当前界面主键
		Object billno = dy_main.get("billno");
		MessageId =  aos_designer.getPkValue() + "";
		Object aos_orgid = dy_main.getDynamicObject("aos_orgid");
		String aos_orgnumber = null;
		Object aos_language = dy_main.getDynamicObjectCollection("aos_entryentity").get(0).get("aos_language");
		String aos_languagenumber = null;
		if (aos_orgid != null)
			aos_orgnumber = ((DynamicObject) aos_orgid).getString("number");
		if (aos_language != null)
			aos_languagenumber = (String) aos_language;
		Boolean Valid = GetValid(dy_main);

		int func = 0;			//功能图全新+功能图类似
		int judgeType2 = 0;		//白底精修或 背景有值或是否3D建模=是
		DynamicObjectCollection aos_entryentityS = dy_main.getDynamicObjectCollection("aos_entryentity");
		for (DynamicObject aos_entryentity : aos_entryentityS) {
			if (aos_entryentity.getBoolean("aos_valid"))
				func += aos_entryentity.getInt("aos_funcpic") + aos_entryentity.getInt("aos_funclike");
			if (aos_entryentity.getBoolean("aos_3d"))
				judgeType2++;
			judgeType2 += aos_entryentity.getInt("aos_whited");	//白底精修
			judgeType2 += aos_entryentity.getInt("aos_backround");	//背景
		}

		if ("EN".equals(aos_languagenumber) && ("ES/IT/DE/FR".contains(aos_orgnumber))) {
			if (Valid)	//是否应用
				GenerateListingLanguage(dy_main);// 生成小语种
			if (func > 0) {	////功能图全新+功能图类似
				dy_main.set("aos_status", "设计传图");// 设置单据流程状态
				dy_main.set("aos_user", dy_main.get("aos_requireby"));// 流转给设计
			} else {
				dy_main.set("aos_status", "结束");// 设置单据流程状态
				dy_main.set("aos_user", system);// 流转给设计
			}
		}
		else if (("DE/FR/IT/ES".contains(aos_orgnumber)) && Cux_Common_Utl.IsNull(aos_languagenumber) && judgeType2>0) {
			dy_main.set("aos_status", "设计传图");// 设置单据流程状态
			dy_main.set("aos_user", dy_main.get("aos_requireby"));// 流转给设计
			MKTCom.SendGlobalMessage(MessageId, "aos_mkt_designcmp", ReqFId + "", billno + "", Message);
		}
		else {
			dy_main.set("aos_status", "结束");// 设置单据流程状态
			dy_main.set("aos_user", system);// 流转给设计
		}

	}

	private static Boolean GetValid(DynamicObject dy_main) {
		Boolean Valid = false;
		DynamicObjectCollection aos_entryentityS = dy_main.getDynamicObjectCollection("aos_entryentity");
		for (DynamicObject aos_entryentity : aos_entryentityS) {
			Boolean aos_valid = aos_entryentity.getBoolean("aos_valid");
			if (aos_valid) {
				Valid = true;
				break;
			}
		}
		return Valid;
	}

	/** 值校验 **/
	private static void SaveControl() throws FndError {
		int ErrorCount = 0;
		String ErrorMessage = "";
		// 数据层

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
		DynamicObject AosUser = (DynamicObject) this.getModel().getValue("aos_user");
		Object CurrentUserId = UserServiceHelper.getCurrentUserId();
		Object CurrentUserName = UserServiceHelper.getUserInfoByID((long) CurrentUserId).get("name");

		// 锁住需要控制的字段
		this.getView().setVisible(false, "aos_back");

		// 当前节点操作人不为当前用户 全锁
		if (!AosUser.getPkValue().toString().equals(CurrentUserId.toString())
				&& !"程震杰".equals(CurrentUserName.toString()) && !"陈聪".equals(CurrentUserName.toString())
				&& !"刘中怀".equals(CurrentUserName.toString())) {
			this.getView().setEnable(false, "titlepanel");// 标题面板
			this.getView().setEnable(false, "aos_contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "bar_save");
			this.getView().setVisible(false, "aos_submit");
		}
		// 状态控制
		Map<String, Object> map = new HashMap<>();
		if ("销售确认".equals(AosStatus)) {
			map.put(ClientProperties.Text, new LocaleString("销售确认"));
			this.getView().updateControlMetadata("aos_submit", map);
			this.getView().setVisible(true, "aos_submit");
			this.getView().setEnable(true, "aos_contentpanelflex");// 主界面面板
			this.getView().setVisible(true, "bar_save");
			this.getView().setVisible(true, "aos_back");
		} else if ("申请人".equals(AosStatus)) {
			map.put(ClientProperties.Text, new LocaleString("提交"));
			this.getView().updateControlMetadata("aos_submit", map);
			this.getView().setVisible(true, "aos_submit");
			this.getView().setEnable(true, "aos_contentpanelflex");// 主界面面板
			this.getView().setVisible(true, "bar_save");
			this.getView().setVisible(false, "aos_back");
		} else if ("结束".equals(AosStatus)) {
			this.getView().setVisible(false, "aos_submit");
			this.getView().setEnable(false, "aos_contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "bar_save");
		} else if ("设计传图".equals(AosStatus)) {
			this.getView().setVisible(true, "aos_submit");
			this.getView().setEnable(true, "aos_contentpanelflex");// 主界面面板
			this.getView().setVisible(true, "bar_save");
		}
	}

	/** 申请人提交 **/
	private static void SubmitForApply(DynamicObject dy_mian) {
		String MessageId = null;
		String Message = "设计完成表(国别)-销售确认";
		Object ReqFId = dy_mian.getPkValue(); // 当前界面主键
		Object billno = dy_mian.get("billno");
		Object aos_itemid = dy_mian.getDynamicObjectCollection("aos_entryentity").get(0).getDynamicObject("aos_itemid" ).getPkValue();
		Object aos_orgid =  dy_mian.getDynamicObject("aos_orgid").getPkValue();
		Object aos_sale = ProgressUtil.findUserByOrgCate(aos_orgid,aos_itemid,"aos_02hq");
		MessageId = ((DynamicObject) aos_sale).getPkValue() + "";
		dy_mian.set("aos_status", "销售确认");// 设置单据流程状态
		dy_mian.set("aos_user", aos_sale);// 流转给销售人员
		MKTCom.SendGlobalMessage(MessageId, "aos_mkt_designcmp", ReqFId + "", billno + "", Message);
	}

	/** 生成小语种文案的时候先校验是否存在，存在的情况下，先删除 **/
	private static void DuplicateCheck(DynamicObject dy_mian){
		DynamicObject dy_cmp = dy_mian;
		String billno = dy_cmp.getString("billno");
		if (billno == null)
			return;
		List<String> list_item = dy_cmp.getDynamicObjectCollection("aos_entryentity")
				.stream()
				.map(dy -> dy.getDynamicObject("aos_itemid").getString("id"))
				.distinct()
				.collect(Collectors.toList());
		//查找小语种是否存在
		QFilter filter_billno = new QFilter("aos_orignbill","=",billno);
		StringJoiner str = new StringJoiner(",");
		str.add("id");
		str.add("aos_entryentity.aos_itemid");
		List<String> list_delete = new ArrayList<>();
		DynamicObject[] dyc = BusinessDataServiceHelper.load("aos_mkt_listing_min", str.toString(), new QFilter[]{filter_billno});
		//List<String> id = Arrays.stream(dyc).map(dy -> dy.getString("id")).distinct().collect(Collectors.toList());

		//校验每单的物料是否在当前的提交单中都存在
		for (DynamicObject dy_min : dyc) {
			DynamicObjectCollection dyc_ent = dy_min.getDynamicObjectCollection("aos_entryentity");
			boolean reject = true;
			for (DynamicObject dy : dyc_ent) {
				String itemid = dy.getDynamicObject("aos_itemid").getString("id");
				if (!list_item.contains(itemid)){
					reject = false;
					break;
				}
			}
			if (reject && !list_delete.contains(dy_min.getString("id")))
				list_delete.add(dy_min.getString("id"));
		}
		QFilter filter_id = new QFilter("id",QFilter.in,list_delete);
		DeleteServiceHelper.delete("aos_mkt_listing_min",new QFilter[]{filter_id});
		//添加日志
		FndHistory.Create(dy_mian, "设计完成表生成小语种单据", "小语种存在重复单据:   "+list_delete.size());
	}
}