package mkt.progress.design.aadd;

import java.util.ArrayList;
import java.util.Date;
import java.util.EventObject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndHistory;
import common.fnd.FndMsg;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.form.ClientProperties;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.Image;
import kd.bos.form.control.Label;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.BeforeDoOperationEventArgs;
import kd.bos.form.events.HyperLinkClickEvent;
import kd.bos.form.events.HyperLinkClickListener;
import kd.bos.form.field.ComboEdit;
import kd.bos.form.field.ComboItem;
import kd.bos.form.operate.FormOperate;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.MKTCom;
import mkt.progress.iface.iteminfo;

public class aos_mkt_aadd_bill extends AbstractBillPlugIn implements HyperLinkClickListener {

	public final static String system = Cux_Common_Utl.SYSTEM;

	@Override
	public void registerListener(EventObject e) {
		super.registerListener(e);
		EntryGrid entryGrid = this.getControl("aos_entryentity");
		entryGrid.addHyperClickListener(this);
	}

	@Override
	public void hyperLinkClick(HyperLinkClickEvent hyperLinkClickEvent) {
		int RowIndex = hyperLinkClickEvent.getRowIndex();
		String FieldName = hyperLinkClickEvent.getFieldName();
		FndMsg.debug("FieldName " + FieldName);
		if ("aos_url".equals(FieldName))
			getView().openUrl(this.getModel().getValue(FieldName, RowIndex).toString());
	}

	public void propertyChanged(PropertyChangedArgs e) {
		String name = e.getProperty().getName();
		FndMsg.debug("name =" + name);
		if ("aos_type".equals(name))
			aosTypeChange();// 任务类型
		else if ("aos_itemid".equals(name))
			aosItemIdChange();
	}

	public void afterCreateNewData(EventObject e) {
		super.afterCreateNewData(e);
		statusControl();
		init();
	}

	/** 初始化事件 **/
	public void afterLoadData(EventObject e) {
		super.afterLoadData(e);
		statusControl();
		aosItemIdChange();
	}

	public void beforeDoOperation(BeforeDoOperationEventArgs args) {
		FormOperate formOperate = (FormOperate) args.getSource();
		String Operatation = formOperate.getOperateKey();
		try {
			if ("save".equals(Operatation)) {
				saveControl();
			}
			statusControl();
		} catch (FndError fndError) {
			fndError.show(getView());
			args.setCancel(true);
		} catch (Exception ex) {
			FndError.showex(getView(), ex);
			args.setCancel(true);
		}
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String Control = evt.getItemKey();
		try {
			if ("aos_submit".equals(Control))
				aosSubmit();
			else if ("aos_history".equals(Control))
				aos_history();// 查看历史记录
		} catch (FndError fndError) {
			fndError.show(getView());
		} catch (Exception ex) {
			FndError.showex(getView(), ex);
		}
	}

	/**
	 * 查看历史记录
	 */
	private void aos_history() {
		FndHistory.OpenHistory(this.getView());
	}

	/**
	 * 提交按钮逻辑
	 */
	private void aosSubmit() throws FndError {
		FndMsg.debug("=====Into aosSubmit=====");
		String aos_status = this.getModel().getValue("aos_status").toString();// 根据状态判断当前流程节点
		switch (aos_status) {
		case "新建":
			submitForNew();
			break;
		case "文案确认":
			submitForEdit();
			break;
		case "海外确认":
			submitForOs();
			break;
		case "设计制作":
			submitForDesign();
			break;
		case "新品对比模块录入":
			submitForModel();
			break;
		case "销售上线":
			submitForSale();
			break;
		}
		FndHistory.Create(this.getView(), "提交", aos_status);
		this.getView().invokeOperation("save");
		this.getView().invokeOperation("refresh");
		statusControl();
	}

	/**
	 * 销售上线提交
	 * 
	 * @throws FndError
	 */
	private void submitForSale() throws FndError {
		FndMsg.debug("=====Into submitForSale=====");
		String ReqFId = this.getModel().getDataEntity().getPkValue().toString();
		String aosBillNo = this.getModel().getValue("aos_billno").toString();
		Object aos_requireby = getModel().getValue("aos_requireby");
		this.getModel().setValue("aos_status", "已完成");
		this.getModel().setValue("aos_user", system);
		// 发送消息
		String messageId = aos_requireby.toString();
		MKTCom.SendGlobalMessage(messageId, "aos_mkt_aadd", ReqFId, aosBillNo, "高级A+需求单-已完成");
	}

	/**
	 * 新品选品模块录入提交
	 * 
	 * @throws FndError
	 */
	private void submitForModel() throws FndError {
		FndMsg.debug("=====Into submitForModel=====");
		String ReqFId = this.getModel().getDataEntity().getPkValue().toString();
		String aosBillNo = this.getModel().getValue("aos_billno").toString();
		Object aos_user = getModel().getValue("aos_sale");
		this.getModel().setValue("aos_status", "销售上线");
		this.getModel().setValue("aos_user", aos_user);
		// 发送消息
		String messageId = aos_user.toString();
		MKTCom.SendGlobalMessage(messageId, "aos_mkt_aadd", ReqFId, aosBillNo, "高级A+需求单-销售上线");
	}

	/**
	 * 设计制作提交
	 * 
	 * @throws FndError
	 */
	private void submitForDesign() throws FndError {
		FndMsg.debug("=====Into submitForDesign=====");
		Object aos_type = this.getModel().getValue("aos_type");
		String ReqFId = this.getModel().getDataEntity().getPkValue().toString();
		String aosBillNo = this.getModel().getValue("aos_billno").toString();
		Object aosItemId = this.getModel().getValue("aos_itemid", 0);
		Object aos_productno = ((DynamicObject) aosItemId).get("aos_productno");
		Object aos_org = this.getModel().getValue("aos_org");

		if ("新款".equals(aos_type)) {
			// 触发生成同款单据
			// 1.按流程中的SKU，生成其他英语国别(US/CA/UK)的同款高级A+需求表
			FndMsg.debug("=======1=======");
			Set<String> ouSet = getItemOrg(((DynamicObject) aosItemId).getPkValue());
			if (ouSet.size() > 0) {
				Iterator<String> iter = ouSet.iterator();
				while (iter.hasNext()) {
					String ou = iter.next();
					if ("US,UK,CA".contains(ou) && !aos_org.equals(ou)) {
						genSameAadd(ReqFId, ou, 0, "NORMAL", ((DynamicObject) aosItemId).getPkValue());
					}
				}
			}
			// 2.按流程中的SKU，找到对应产品号下的其他SKU，生成多张英语国别(US/CA/UK)同款高级A+需求表，流程到04节点
			FndMsg.debug("=======2=======");
			DynamicObjectCollection sameSegmentS = QueryServiceHelper.query("bd_material", "id",
					new QFilter("aos_productno", QCP.equals, aos_productno)
							.and("id", QCP.not_equals, ((DynamicObject) aosItemId).getPkValue()).toArray());
			for (DynamicObject sameSegment : sameSegmentS) {
				Set<String> segmentSet = getItemOrg(sameSegment.get("id"));
				if (segmentSet.size() > 0) {
					Iterator<String> iterSeg = segmentSet.iterator();
					while (iterSeg.hasNext()) {
						String ou = iterSeg.next();
						if ("US,UK,CA".contains(ou) && !aos_org.equals(ou)) {
							genSameAadd(ReqFId, ou, 0, "NORMAL", sameSegment.get("id"));
						}
					}
				}
			}

			// 3.统计流程中的SKU，对应产品号下的所有SKU，汇总出所有小语种下单国别
			FndMsg.debug("=======3=======");
			Map<String, Integer> ouCount = new HashMap<>();
			for (DynamicObject sameSegment : sameSegmentS) {
				Set<String> segmentSet = getItemOrg(sameSegment.get("id"));
				if (segmentSet.size() > 0) {
					Iterator<String> iterSeg = segmentSet.iterator();
					while (iterSeg.hasNext()) {
						String ou = iterSeg.next();
						if ("IT,ES,DE,FR".contains(ou) && !aos_org.equals(ou)) {
							if (FndGlobal.IsNull(ouCount.get(ou)))
								ouCount.put(ou, 0);
							else
								ouCount.put(ou, ouCount.get(ou) + 1);
							genSameAadd(ReqFId, ou, ouCount.get(ou), "MIN", sameSegment.get("id"));
						}
					}
				}
			}
		}

		// 判断高级A+进度表是否存在
		if ("US,UK,CA".contains(aos_org.toString())) {
			DynamicObject aos_mkt_addtrack = BusinessDataServiceHelper.loadSingleFromCache("aos_mkt_addtrack",
					new QFilter("aos_itemid", QCP.equals, ((DynamicObject) aosItemId).getPkValue()).toArray());
			if (FndGlobal.IsNotNull(aos_mkt_addtrack)) {
				aos_mkt_addtrack.set("aos_" + aos_org, true);
			} else {
				aos_mkt_addtrack = BusinessDataServiceHelper.newDynamicObject("aos_mkt_addtrack");
				aos_mkt_addtrack.set("aos_" + aos_org, true);
				aos_mkt_addtrack.set("aos_itemid", aosItemId);
			}
			OperationServiceHelper.executeOperate("save", "aos_mkt_addtrack", new DynamicObject[] { aos_mkt_addtrack },
					OperateOption.create());
		}

		Object aos_user = getModel().getValue("aos_monitor");
		this.getModel().setValue("aos_status", "新品对比模块录入");
		this.getModel().setValue("aos_user", aos_user);
		// 发送消息
		String messageId = aos_user.toString();
		MKTCom.SendGlobalMessage(messageId, "aos_mkt_aadd", ReqFId, aosBillNo, "高级A+需求单-新品对比模块录入");

		// 如果当前为小语种同步主单 则需要同步修改其他子单
		Boolean aos_min = (Boolean) this.getModel().getValue("aos_min");
		Object aos_sourceid = this.getModel().getValue("aos_sourceid");
		if (aos_min) {
			DynamicObjectCollection aosMktAaddS = QueryServiceHelper.query("aos_mkt_aadd", "id",
					new QFilter("aos_son", QCP.equals, true).and("aos_sourceid", QCP.equals, aos_sourceid).toArray());
			for (DynamicObject aosMktAadd : aosMktAaddS) {
				DynamicObject son = BusinessDataServiceHelper.loadSingle(aosMktAadd.get("id"), "aos_mkt_aadd");
				son.set("aos_user", son.get("aos_design"));
				son.set("aos_status", "设计制作");
			}
		}
	}

	/**
	 * 海外确认提交
	 * 
	 * @throws FndError
	 */
	private void submitForOs() throws FndError {
		FndMsg.debug("=====Into submitForOs=====");
		// 此时设计师肯定存在
		Object aos_user = this.getModel().getValue("aos_design");
		this.getModel().setValue("aos_user", aos_user);
		this.getModel().setValue("aos_status", "设计制作");
		// 发送消息
		String messageId = aos_user.toString();
		String ReqFId = this.getModel().getDataEntity().getPkValue().toString();
		String aosBillNo = this.getModel().getValue("aos_billno").toString();
		MKTCom.SendGlobalMessage(messageId, "aos_mkt_aadd", ReqFId, aosBillNo, "高级A+需求单-设计制作");
	}

	/**
	 * 文案确认节点提交
	 */
	private void submitForEdit() throws FndError {
		FndMsg.debug("=====Into submitForEdit=====");
		FndError fndError = new FndError();
		Object aosItemId = this.getModel().getValue("aos_itemid", 0);
		Object aos_org = this.getModel().getValue("aos_org");
		// 校验是否海外确认必填
		Object aos_osconfirm = this.getModel().getValue("aos_osconfirm");
		if (FndGlobal.IsNull(aos_osconfirm)) {
			fndError.add("是否海外确认必填!");
			throw fndError;
		}

		// 判断高级A+进度表是否存在
		if ("DE,FR,IT,ES".contains(aos_org.toString())) {
			DynamicObject aos_mkt_addtrack = BusinessDataServiceHelper.loadSingleFromCache("aos_mkt_addtrack",
					new QFilter("aos_itemid", QCP.equals, ((DynamicObject) aosItemId).getPkValue()).toArray());
			if (FndGlobal.IsNotNull(aos_mkt_addtrack)) {
				aos_mkt_addtrack.set("aos_" + aos_org, true);
			} else {
				aos_mkt_addtrack = BusinessDataServiceHelper.newDynamicObject("aos_mkt_addtrack");
				aos_mkt_addtrack.set("aos_" + aos_org, true);
				aos_mkt_addtrack.set("aos_itemid", aosItemId);
			}
			OperationServiceHelper.executeOperate("save", "aos_mkt_addtrack", new DynamicObject[] { aos_mkt_addtrack },
					OperateOption.create());
		}

		// 根据是否海外确认分类
		if ("是".equals(aos_osconfirm)) {
			Object aos_user = UserServiceHelper.getCurrentUserId();
			this.getModel().setValue("aos_status", "海外确认");
			this.getModel().setValue("aos_user", aos_user);
			// 发送消息
			String messageId = aos_user.toString();
			String ReqFId = this.getModel().getDataEntity().getPkValue().toString();
			String aosBillNo = this.getModel().getValue("aos_billno").toString();
			MKTCom.SendGlobalMessage(messageId, "aos_mkt_aadd", ReqFId, aosBillNo, "高级A+需求单-海外确认");
		} else if ("否".equals(aos_osconfirm)) {
			Object aos_user = getModel().getValue("aos_design");
			this.getModel().setValue("aos_status", "设计制作");
			this.getModel().setValue("aos_user", aos_user);
			// 发送消息
			String messageId = aos_user.toString();
			String ReqFId = this.getModel().getDataEntity().getPkValue().toString();
			String aosBillNo = this.getModel().getValue("aos_billno").toString();
			MKTCom.SendGlobalMessage(messageId, "aos_mkt_aadd", ReqFId, aosBillNo, "高级A+需求单-设计制作");
		}
	}

	/**
	 * 新建节点提交
	 */
	private void submitForNew() throws FndError {
		FndMsg.debug("=====Into submitForNew=====");
		FndError fndError = new FndError();
		// 校验是否至少存在一行SKU
		Object aosItemId = this.getModel().getValue("aos_itemid", 0);
		if (FndGlobal.IsNull(aosItemId)) {
			fndError.add("至少填写一行SKU数据!");
			throw fndError;
		}
		// 校验&获取国别编辑
		Object itemId = ((DynamicObject) aosItemId).getPkValue();// 物料ID
		String category = MKTCom.getItemCateNameZH(itemId);
		String[] category_group = category.split(",");
		String AosCategory1 = null;// 大类
		String AosCategory2 = null;// 中类
		int category_length = category_group.length;
		if (category_length > 0)
			AosCategory1 = category_group[0];
		if (category_length > 1)
			AosCategory2 = category_group[1];
		Object aos_org = this.getModel().getValue("aos_org");// 国别
		// 根据国别+大类+小类 从营销国别品类人员表中取国别
		DynamicObject aosMktProgOrgUser = QueryServiceHelper.queryOne("aos_mkt_progorguser",
				"aos_oueditor,aos_02hq,aos_salehelper",
				new QFilter("aos_orgid.number", QCP.equals, aos_org).and("aos_category1", QCP.equals, AosCategory1)
						.and("aos_category2", QCP.equals, AosCategory2).toArray());
		if (FndGlobal.IsNull(aosMktProgOrgUser) || FndGlobal.IsNull(aosMktProgOrgUser.get("aos_oueditor"))) {
			fndError.add(aos_org + "~" + AosCategory1 + "~" + AosCategory2 + ",国别编辑不存在!");
			throw fndError;
		}
		// 带出组长
		if (FndGlobal.IsNull(aosMktProgOrgUser) || FndGlobal.IsNull(aosMktProgOrgUser.get("aos_02hq"))) {
			fndError.add(aos_org + "~" + AosCategory1 + "~" + AosCategory2 + ",组长不存在!");
			throw fndError;
		}
		// 带出销售助理
		if (FndGlobal.IsNull(aosMktProgOrgUser) || FndGlobal.IsNull(aosMktProgOrgUser.get("aos_salehelper"))) {
			fndError.add(aos_org + "~" + AosCategory1 + "~" + AosCategory2 + ",销售助理不存在!");
			throw fndError;
		}

		// 若未填写设计师则按照逻辑带出设计师
		Object aos_design = this.getModel().getValue("aos_design");// 表头设计师
		Object aos_type = this.getModel().getValue("aos_type");// 任务类型
		if (FndGlobal.IsNull(aos_design)) {
			if ("新款".equals(aos_type)) {
				DynamicObject aosMktProgUser = QueryServiceHelper.queryOne("aos_mkt_proguser", "aos_designer",
						new QFilter("aos_category1", QCP.equals, AosCategory1)
								.and("aos_category2", QCP.equals, AosCategory2).toArray());
				if (FndGlobal.IsNull(aosMktProgUser) || FndGlobal.IsNull(aosMktProgUser.get("aos_designer"))) {
					fndError.add(AosCategory1 + "~" + AosCategory2 + ",品类设计不存在!");
					throw fndError;
				}
				this.getModel().setValue("aos_design", aosMktProgUser.get("aos_designer"));
			} else {
				DynamicObject aosMktProgUser = QueryServiceHelper.queryOne("aos_mkt_proguser", "aos_designassit",
						new QFilter("aos_category1", QCP.equals, AosCategory1)
								.and("aos_category2", QCP.equals, AosCategory2).toArray());
				if (FndGlobal.IsNull(aosMktProgUser) || FndGlobal.IsNull(aosMktProgUser.get("aos_designassit"))) {
					fndError.add(AosCategory1 + "~" + AosCategory2 + ",初级品类不存在!");
					throw fndError;
				}
				this.getModel().setValue("aos_design", aosMktProgUser.get("aos_designassit"));
			}
		}
		Object aos_user = aosMktProgOrgUser.get("aos_oueditor");
		this.getModel().setValue("aos_user", aos_user);
		this.getModel().setValue("aos_oueditor", aos_user);
		this.getModel().setValue("aos_status", "文案确认");
		Object aos_02hq = aosMktProgOrgUser.get("aos_02hq");
		Object aos_salehelper = aosMktProgOrgUser.get("aos_salehelper");
		this.getModel().setValue("aos_monitor", aos_02hq);
		this.getModel().setValue("aos_sale", aos_salehelper);

		// 发送消息
		String messageId = aos_user.toString();
		String ReqFId = this.getModel().getDataEntity().getPkValue().toString();
		String aosBillNo = this.getModel().getValue("aos_billno").toString();
		MKTCom.SendGlobalMessage(messageId, "aos_mkt_aadd", ReqFId, aosBillNo, "高级A+需求单-文案确认");
	}

	/**
	 * 物料值改变事件
	 */
	private void aosItemIdChange() {
		FndMsg.debug("=====aosItemIdChange=====");
		int currentRow = 0;
		Object aosItemid = this.getModel().getValue("aos_itemid", currentRow);
		Label aos_attr1 = this.getView().getControl("aos_attr1");
		Label aos_spec = this.getView().getControl("aos_spec");
		Label aos_brand = this.getView().getControl("aos_brand");
		Object aos_org = this.getModel().getValue("aos_org");
		Image aos_imageap = this.getControl("aos_imageap");

		if (FndGlobal.IsNull(aosItemid)) {
			aos_attr1.setText(null);
			aos_spec.setText(null);
			aos_brand.setText(null);
			this.getModel().setValue("aos_url", null);
			aos_imageap.setUrl(null);
		} else {
			DynamicObject aosItemidObject = (DynamicObject) aosItemid;
			DynamicObject bd_material = BusinessDataServiceHelper.loadSingle(aosItemidObject.getPkValue(),
					"bd_material");
			// 明细面板
			// \\192.168.70.62\Marketing service\外部文件\销售\A+上架资料\设计高级版A+\大类\品名-产品号
			String category = MKTCom.getItemCateNameZH(aosItemidObject.getPkValue());
			String[] category_group = category.split(",");
			String AosCategory1 = null;
			if (category_group.length > 0)
				AosCategory1 = category_group[0];
			AosCategory1 = AosCategory1.replace("运动&娱乐&汽配", "运动");
			AosCategory1 = AosCategory1.replace("庭院&花园", "庭院");
			AosCategory1 = AosCategory1.replace("居家系列", "居家");
			AosCategory1 = AosCategory1.replace("宠物用品", "宠物");
			AosCategory1 = AosCategory1.replace("藤编产品", "藤编");
			AosCategory1 = AosCategory1.replace("婴儿产品&玩具及游戏", "婴童");
			String aos_url = "\\\\192.168.70.62\\Marketing service\\外部文件\\销售\\A+上架资料\\设计高级版A+";
			aos_url = aos_url + "\\" + AosCategory1 + "\\" + bd_material.getString("name") + "-"
					+ bd_material.getString("aos_productno");
			this.getModel().setValue("aos_url", aos_url);
			// 货号信息面板
			aos_imageap.setUrl("https://cls3.s3.amazonaws.com/" + bd_material.getString("number") + "/1-1.jpg");
			aos_attr1.setText("产品属性1\n" + bd_material.getString("aos_seting_cn"));
			aos_spec.setText("产品规格\n" + bd_material.getString("aos_specification_cn"));
			if (FndGlobal.IsNotNull(aos_org)) {
				DynamicObjectCollection aos_contryentryS = bd_material.getDynamicObjectCollection("aos_contryentry");
				for (DynamicObject aos_contryentry : aos_contryentryS) {
					DynamicObject aos_nationality = aos_contryentry.getDynamicObject("aos_nationality");
					String aos_nationalitynumber = aos_nationality.getString("number");
					if (!aos_org.equals(aos_nationalitynumber))
						continue;
					aos_brand.setText("品牌\n" + aos_contryentry.getDynamicObject("aos_contrybrand").getString("number"));
				}
			}
		}
	}

	/**
	 * 任务类型值改变事件
	 */
	private void aosTypeChange() {
		Object aos_type = getModel().getValue("aos_type");
		ComboEdit aos_org = getControl("aos_org");
		List<ComboItem> data = new ArrayList<>();
		if ("新款".equals(aos_type)) {
			data.add(new ComboItem(new LocaleString("US"), "US"));
			data.add(new ComboItem(new LocaleString("UK"), "UK"));
		} else {
			data.add(new ComboItem(new LocaleString("US"), "US"));
			data.add(new ComboItem(new LocaleString("CA"), "CA"));
			data.add(new ComboItem(new LocaleString("UK"), "UK"));
			data.add(new ComboItem(new LocaleString("DE"), "DE"));
			data.add(new ComboItem(new LocaleString("FR"), "FR"));
			data.add(new ComboItem(new LocaleString("IT"), "IT"));
			data.add(new ComboItem(new LocaleString("ES"), "ES"));
		}
		aos_org.setComboItems(data);
	}

	/**
	 * 初始化数据
	 */
	private void init() {
		try {
			// 带出人员部门
			long CurrentUserId = UserServiceHelper.getCurrentUserId();
			List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(CurrentUserId);
			if (MapList != null) {
				if (MapList.get(2) != null)
					this.getModel().setValue("aos_organization1", MapList.get(2).get("id"));
			}
			// 设置国别初始下拉为空
			ComboEdit aos_org = getControl("aos_org");
			aos_org.setComboItems(null);
		} catch (Exception ex) {
			FndError.showex(getView(), ex);
		}
	}

	/**
	 * 保存校验
	 * 
	 * @throws FndError
	 */
	private void saveControl() throws FndError {
		FndError fndError = new FndError();
		Object aos_type = this.getModel().getValue("aos_type");
		Object aos_org = this.getModel().getValue("aos_org");
		Object aos_status = this.getModel().getValue("aos_status");
		// 对于新建节点
		if ("新建".equals(aos_status)) {
			if (FndGlobal.IsNull(aos_type)) {
				fndError.addCount();
				fndError.add("任务类型必填!");
			}
			if (FndGlobal.IsNull(aos_org)) {
				fndError.addCount();
				fndError.add("国别必填!");
			}
			// 校验是否至少存在一行SKU
			Object aosItemId = this.getModel().getValue("aos_itemid", 0);
			if (FndGlobal.IsNull(aosItemId)) {
				fndError.add("至少填写一行SKU数据!");
				throw fndError;
			}
		}
		if (fndError.getCount() > 0) {
			throw fndError;
		}
	}

	/**
	 * 状态控制
	 * 
	 * @throws FndError
	 */
	private void statusControl() throws FndError {
		// 数据层
		Object aosStatus = this.getModel().getValue("aos_status");
		DynamicObject aosUser = (DynamicObject) this.getModel().getValue("aos_user");
		Object currentUserId = UserServiceHelper.getCurrentUserId();
		// 权限控制
		if (!aosUser.getPkValue().toString().equals(currentUserId.toString())) {
			this.getView().setEnable(false, "titlepanel");// 标题面板
			this.getView().setEnable(false, "aos_contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "bar_save");// 保存
			this.getView().setVisible(false, "aos_submit");// 提交
		}
		// 状态控制
		if ("新建".equals(aosStatus)) {
			this.getView().setEnable(false, "aos_osconfirm");// 是否海外确认
			EntryGrid entryGrid = this.getControl("aos_entryentity");
			entryGrid.setColumnProperty("aos_aadd", ClientProperties.Lock, true);
			entryGrid.setColumnProperty("aos_new", ClientProperties.Lock, true);
			entryGrid.setColumnProperty("aos_same", ClientProperties.Lock, true);
			entryGrid.setColumnProperty("aos_trans", ClientProperties.Lock, true);
		} else if ("文案确认".equals(aosStatus)) {
			this.getView().setEnable(true, "aos_osconfirm");// 是否海外确认
			this.getView().setEnable(false, "aos_type");// 任务类型
			this.getView().setEnable(false, "aos_org");// 国别
			this.getView().setEnable(false, "aos_design");// 设计师
			this.getView().setEnable(false, "aos_sale");// 销售
			this.getView().setEnable(false, "aos_oueditor");// 国别编辑
			this.getView().setEnable(false, 0, "aos_new");// 新款
			this.getView().setEnable(false, 0, "aos_same");// 同款
			this.getView().setEnable(false, 0, "aos_trans");// 翻译
			this.getView().setEnable(false, 0, "aos_itemid");// 货号
		} else if ("海外确认".equals(aosStatus)) {
			this.getView().setEnable(false, "aos_osconfirm");// 是否海外确认
			this.getView().setEnable(false, "aos_type");// 任务类型
			this.getView().setEnable(false, "aos_org");// 国别
			this.getView().setEnable(false, "aos_design");// 设计师
			this.getView().setEnable(false, "aos_sale");// 销售
			this.getView().setEnable(false, "aos_oueditor");// 国别编辑
			this.getView().setEnable(false, 0, "aos_aadd");// 高级A+文案
			this.getView().setEnable(false, 0, "aos_new");// 新款
			this.getView().setEnable(false, 0, "aos_same");// 同款
			this.getView().setEnable(false, 0, "aos_trans");// 翻译
			this.getView().setEnable(false, 0, "aos_itemid");// 货号
		} else if ("设计制作".equals(aosStatus)) {
			this.getView().setEnable(false, "aos_osconfirm");// 是否海外确认
			this.getView().setEnable(false, "aos_type");// 任务类型
			this.getView().setEnable(false, "aos_org");// 国别
			this.getView().setEnable(false, "aos_design");// 设计师
			this.getView().setEnable(false, "aos_sale");// 销售
			this.getView().setEnable(false, "aos_oueditor");// 国别编辑
			this.getView().setEnable(false, 0, "aos_aadd");// 高级A+文案
			this.getView().setEnable(true, 0, "aos_new");// 新款
			this.getView().setEnable(true, 0, "aos_same");// 同款
			this.getView().setEnable(true, 0, "aos_trans");// 翻译
			this.getView().setEnable(false, 0, "aos_itemid");// 货号
		} else if ("新品对比模块录入".equals(aosStatus)) {
			this.getView().setVisible(false, "bar_save");// 保存
			this.getView().setEnable(false, "aos_osconfirm");// 是否海外确认
			this.getView().setEnable(false, "aos_type");// 任务类型
			this.getView().setEnable(false, "aos_org");// 国别
			this.getView().setEnable(false, "aos_design");// 设计师
			this.getView().setEnable(false, "aos_sale");// 销售
			this.getView().setEnable(false, "aos_oueditor");// 国别编辑
			this.getView().setEnable(false, 0, "aos_aadd");// 高级A+文案
			this.getView().setEnable(false, 0, "aos_new");// 新款
			this.getView().setEnable(false, 0, "aos_same");// 同款
			this.getView().setEnable(false, 0, "aos_trans");// 翻译
			this.getView().setEnable(false, 0, "aos_itemid");// 货号
		} else if ("销售上线".equals(aosStatus)) {
			this.getView().setVisible(false, "bar_save");// 保存
			this.getView().setEnable(false, "aos_osconfirm");// 是否海外确认
			this.getView().setEnable(false, "aos_type");// 任务类型
			this.getView().setEnable(false, "aos_org");// 国别
			this.getView().setEnable(false, "aos_design");// 设计师
			this.getView().setEnable(false, "aos_sale");// 销售
			this.getView().setEnable(false, "aos_oueditor");// 国别编辑
			this.getView().setEnable(false, 0, "aos_aadd");// 高级A+文案
			this.getView().setEnable(false, 0, "aos_new");// 新款
			this.getView().setEnable(false, 0, "aos_same");// 同款
			this.getView().setEnable(false, 0, "aos_trans");// 翻译
			this.getView().setEnable(false, 0, "aos_itemid");// 货号
		}
	}

	/**
	 * 获取下单国别
	 * 
	 * @param aosItemId
	 * @return
	 */
	private Set<String> getItemOrg(Object aosItemId) {
		Set<String> ouSet = new HashSet<>();
		DynamicObject bd_material = BusinessDataServiceHelper.loadSingle(aosItemId, "bd_material");
		DynamicObjectCollection aos_contryentryS = bd_material.getDynamicObjectCollection("aos_contryentry");
		// 获取所有国家品牌 字符串拼接 终止
		for (DynamicObject aos_contryentry : aos_contryentryS) {
			DynamicObject aos_nationality = aos_contryentry.getDynamicObject("aos_nationality");// 物料国别
			String aos_nationalitynumber = aos_nationality.getString("number");// 物料国别编码
			if ("IE".equals(aos_nationalitynumber))
				continue;
			Object org_id = aos_nationality.get("id"); // ItemId
			int OsQty = iteminfo.GetItemOsQty(org_id, aosItemId);
			int onQty = get_on_hand_qty(Long.valueOf(org_id.toString()), Long.valueOf(aosItemId.toString()));
			OsQty += onQty;
			int SafeQty = iteminfo.GetSafeQty(org_id);
			if ("C".equals(aos_contryentry.getString("aos_contryentrystatus")) && OsQty < SafeQty)
				continue;
			ouSet.add(aos_nationalitynumber);
		}
		return ouSet;
	}

	/**
	 * 获取在途数量
	 * 
	 * @param fk_aos_org_fid
	 * @param fk_aos_item_fid
	 * @return
	 */
	private int get_on_hand_qty(long fk_aos_org_fid, long fk_aos_item_fid) {
		try {
			QFilter filter_item = new QFilter("aos_itemid", "=", fk_aos_item_fid);
			QFilter filter_ou = new QFilter("aos_orgid", "=", fk_aos_org_fid);
			QFilter filter_qty = new QFilter("aos_ship_qty", ">", 0);
			QFilter filter_flag = new QFilter("aos_os_flag", "=", "N");
			QFilter filter_status = new QFilter("aos_shp_status", "=", "PACKING");
			filter_status = filter_status.or("aos_shp_status", "=", "SHIPPING");
			QFilter[] filters = new QFilter[] { filter_item, filter_ou, filter_qty, filter_flag, filter_status };
			DynamicObjectCollection aos_sync_shp = QueryServiceHelper.query("aos_sync_shp", "aos_ship_qty", filters);
			int aos_ship_qty = 0;
			for (DynamicObject d : aos_sync_shp) {
				aos_ship_qty += d.getInt("aos_ship_qty");
			}
			return aos_ship_qty;
		} catch (Exception Ex) {
			throw Ex;
		}
	}

	/**
	 * 生成同款高级A+需求单
	 * 
	 * @param reqFId
	 * @param ou
	 * @param count  用于判断是否小语种第一个货号
	 * @param type
	 * @param itemId
	 */
	private void genSameAadd(String reqFId, String ou, int count, String type, Object itemId) throws FndError {
		FndError fndError = new FndError();

		FndMsg.debug("ou =" + ou);
		FndMsg.debug("count =" + count);
		FndMsg.debug("type =" + type);

		DynamicObject sourceBill = BusinessDataServiceHelper.loadSingle(reqFId, "aos_mkt_aadd");
		DynamicObject aosMktAadd = BusinessDataServiceHelper.newDynamicObject("aos_mkt_aadd");
		aosMktAadd.set("aos_requireby", sourceBill.get("aos_requireby"));
		aosMktAadd.set("aos_organization1", sourceBill.get("aos_organization1"));
		aosMktAadd.set("aos_requiredate", sourceBill.get("aos_requiredate"));
		aosMktAadd.set("aos_type", "同款");
		aosMktAadd.set("aos_org", ou);
		aosMktAadd.set("aos_design", sourceBill.get("aos_design"));
		aosMktAadd.set("aos_osconfirm", sourceBill.get("aos_osconfirm"));
		aosMktAadd.set("aos_oueditor", sourceBill.get("aos_oueditor"));
		aosMktAadd.set("aos_monitor", sourceBill.get("aos_monitor"));
		aosMktAadd.set("aos_sale", sourceBill.get("aos_sale"));
		aosMktAadd.set("aos_sourceid", sourceBill.get("id"));
		aosMktAadd.set("aos_sourcebillno", sourceBill.get("aos_billno"));

		DynamicObjectCollection sourceEntryS = sourceBill.getDynamicObjectCollection("aos_entryentity");
		DynamicObject sourceEntry = sourceEntryS.get(0);
		DynamicObjectCollection aos_entryentityS = aosMktAadd.getDynamicObjectCollection("aos_entryentity");
		DynamicObject aos_entryentity = aos_entryentityS.addNew();

		aos_entryentity.set("aos_itemid", itemId);
		aos_entryentity.set("aos_aadd", sourceEntry.get("aos_aadd"));
		aos_entryentity.set("aos_new", sourceEntry.get("aos_new"));
		aos_entryentity.set("aos_same", sourceEntry.get("aos_same"));
		aos_entryentity.set("aos_trans", sourceEntry.get("aos_trans"));
		aos_entryentity.set("aos_url", sourceEntry.get("aos_url"));

		if (count == 0) {
			if ("MIN".equals(type)) {
				aosMktAadd.set("aos_min", true);// 小语种同步主单
				aosMktAadd.set("aos_status", "文案确认");
				// 小语种国别的品类编辑
				String category = MKTCom.getItemCateNameZH(itemId);
				String[] category_group = category.split(",");
				String AosCategory1 = null;// 大类
				String AosCategory2 = null;// 中类
				int category_length = category_group.length;
				if (category_length > 0)
					AosCategory1 = category_group[0];
				if (category_length > 1)
					AosCategory2 = category_group[1];
				// 根据国别+大类+小类 从营销国别品类人员表中取国别
				DynamicObject aosMktProgOrgUser = QueryServiceHelper.queryOne("aos_mkt_progorguser", "aos_oueditor",
						new QFilter("aos_orgid.number", QCP.equals, ou).and("aos_category1", QCP.equals, AosCategory1)
								.and("aos_category2", QCP.equals, AosCategory2).toArray());
				if (FndGlobal.IsNull(aosMktProgOrgUser) || FndGlobal.IsNull(aosMktProgOrgUser.get("aos_oueditor"))) {
					fndError.add(ou + "~" + AosCategory1 + "~" + AosCategory2 + ",国别编辑不存在!");
					DeleteServiceHelper.delete("aos_mkt_aadd",
							new QFilter("aos_sourceid", "=", sourceBill.get("id")).toArray());
					throw fndError;
				}
				Object aos_user = aosMktProgOrgUser.get("aos_oueditor");
				aosMktAadd.set("aos_user", aos_user);
				aosMktAadd.set("aos_oueditor", aos_user);
			} else if ("NORMAL".equals(type)) {
				aosMktAadd.set("aos_status", "设计制作");
				aosMktAadd.set("aos_user", sourceBill.get("aos_design"));
			}
		} else {
			if ("MIN".equals(type)) {
				aosMktAadd.set("aos_son", true);// 小语种同步子单
				aosMktAadd.set("aos_status", "小语种同步");
				aosMktAadd.set("aos_user", system);
			}
		}

		OperationServiceHelper.executeOperate("save", "aos_mkt_aadd", new DynamicObject[] { aosMktAadd },
				OperateOption.create());
	}

	public static void generateAddFromDesign(DynamicObject aos_itemid, String ou, String type) throws FndError {
		FndError fndError = new FndError();
		// 申请人
		DynamicObject aos_mkt_aadd = QueryServiceHelper.queryOne("aos_mkt_aadd", "aos_requireby,aos_organization1",
				new QFilter("aos_itemid.aos_productno", QCP.equals, aos_itemid.getString("aos_productno")).toArray());

		DynamicObject aosMktAadd = BusinessDataServiceHelper.newDynamicObject("aos_mkt_aadd");
		aosMktAadd.set("aos_requireby", aos_mkt_aadd.get("aos_requireby"));
		aosMktAadd.set("aos_organization1", aos_mkt_aadd.get("aos_organization1"));
		aosMktAadd.set("aos_requiredate", new Date());
		aosMktAadd.set("aos_type", "国别新品");
		aosMktAadd.set("aos_org", ou);
		aosMktAadd.set("aos_osconfirm", "是");

		String category = MKTCom.getItemCateNameZH(aos_itemid.getPkValue());
		String[] category_group = category.split(",");
		String AosCategory1 = null;// 大类
		String AosCategory2 = null;// 中类
		int category_length = category_group.length;
		if (category_length > 0)
			AosCategory1 = category_group[0];
		if (category_length > 1)
			AosCategory2 = category_group[1];
		DynamicObject aosMktProgUser = QueryServiceHelper.queryOne("aos_mkt_proguser", "aos_designassit",
				new QFilter("aos_category1", QCP.equals, AosCategory1).and("aos_category2", QCP.equals, AosCategory2)
						.toArray());
		if (FndGlobal.IsNull(aosMktProgUser) || FndGlobal.IsNull(aosMktProgUser.get("aos_designassit"))) {
			fndError.add(AosCategory1 + "~" + AosCategory2 + ",初级品类不存在!");
			throw fndError;
		}
		DynamicObject aosMktProgOrgUser = QueryServiceHelper.queryOne("aos_mkt_progorguser",
				"aos_oueditor,aos_02hq,aos_salehelper",
				new QFilter("aos_orgid.number", QCP.equals, ou).and("aos_category1", QCP.equals, AosCategory1)
						.and("aos_category2", QCP.equals, AosCategory2).toArray());
		if (FndGlobal.IsNull(aosMktProgOrgUser) || FndGlobal.IsNull(aosMktProgOrgUser.get("aos_oueditor"))) {
			fndError.add(ou + "~" + AosCategory1 + "~" + AosCategory2 + ",国别编辑不存在!");
			throw fndError;
		}
		aosMktAadd.set("aos_design", aosMktProgUser.get("aos_designassit"));
		aosMktAadd.set("aos_oueditor", aosMktProgOrgUser.get("aos_oueditor"));
		aosMktAadd.set("aos_monitor", aosMktProgOrgUser.get("aos_02hq"));
		aosMktAadd.set("aos_sale", aosMktProgOrgUser.get("aos_salehelper"));
		if ("EN_05".equals(type)) {
			aosMktAadd.set("aos_user", aosMktProgOrgUser.get("aos_02hq"));
			aosMktAadd.set("aos_status", "新品对比模块录入");
			// 将对应A+需求表对应国别勾选
			DynamicObject aos_mkt_addtrack = BusinessDataServiceHelper.loadSingleFromCache("aos_mkt_addtrack",
					new QFilter("aos_itemid", QCP.equals, aos_itemid.getPkValue()).toArray());
			if (FndGlobal.IsNotNull(aos_mkt_addtrack)) {
				aos_mkt_addtrack.set("aos_" + ou, true);
			} else {
				aos_mkt_addtrack = BusinessDataServiceHelper.newDynamicObject("aos_mkt_addtrack");
				aos_mkt_addtrack.set("aos_" + ou, true);
				aos_mkt_addtrack.set("aos_itemid", ou);
			}
			OperationServiceHelper.executeOperate("save", "aos_mkt_addtrack", new DynamicObject[] { aos_mkt_addtrack },
					OperateOption.create());
		} else if ("EN_04".equals(type)) {
			aosMktAadd.set("aos_user", aosMktProgUser.get("aos_designassit"));
			aosMktAadd.set("aos_status", "设计制作");
		} else if ("SM_04".equals(type)) {
			aosMktAadd.set("aos_user", aosMktProgUser.get("aos_designassit"));
			aosMktAadd.set("aos_status", "设计制作");
		} else if ("SM_02".equals(type)) {
			aosMktAadd.set("aos_user", aosMktProgOrgUser.get("aos_oueditor"));
			aosMktAadd.set("aos_status", "国别编辑");
		}

		DynamicObjectCollection aos_entryentityS = aosMktAadd.getDynamicObjectCollection("aos_entryentity");
		DynamicObject aos_entryentity = aos_entryentityS.addNew();
		aos_entryentity.set("aos_itemid", aos_itemid);

		OperationServiceHelper.executeOperate("save", "aos_mkt_aadd", new DynamicObject[] { aosMktAadd },
				OperateOption.create());
	}

}