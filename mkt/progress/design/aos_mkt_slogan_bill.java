package mkt.progress.design;

import java.util.ArrayList;
import java.util.Date;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndMsg;
import common.sal.util.SalUtil;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.control.Control;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.Image;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.events.BeforeDoOperationEventArgs;
import kd.bos.form.field.TextEdit;
import kd.bos.form.operate.FormOperate;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.MKTCom;

public class aos_mkt_slogan_bill extends AbstractBillPlugIn {

	String SYSTEM = Cux_Common_Utl.SYSTEM;

	@Override
	public void click(EventObject evt) {
		Control sourceControl = (Control) evt.getSource();
		String keyName = sourceControl.getKey();
		if ("aos_category2".equals(keyName)) {
			Map<String, Object> params = new HashMap<>();
			params.put("aos_category1", this.getModel().getValue("aos_category1"));
			FndGlobal.OpenForm(this, "aos_mkt_slogan_form", params);
		} else if ("aos_category3".equals(keyName)) {
			Map<String, Object> params = new HashMap<>();
			params.put("aos_category2",
					this.getModel().getValue("aos_category1") + "," + this.getModel().getValue("aos_category2"));
			FndGlobal.OpenForm(this, "aos_mkt_slogan_form", params);
		} else if ("aos_cname".equals(keyName)) {
			Map<String, Object> params = new HashMap<>();
			Object aos_category1 = this.getModel().getValue("aos_category1");
			Object aos_category2 = this.getModel().getValue("aos_category2");
			Object aos_category3 = this.getModel().getValue("aos_category3");
			String group = aos_category1 + "," + aos_category2 + "," + aos_category3;
			params.put("aos_cname", group);
			FndGlobal.OpenForm(this, "aos_mkt_slogan_form", params);
		} else if ("aos_itemname".equals(keyName)) {
			int row = this.getModel().getEntryCurrentRowIndex("aos_entryentity");
			Object aos_type = this.getModel().getValue("aos_type");
			FndMsg.debug("row:" + row + "\n" + "aos_type:" + aos_type);
			if (row == 1) {
				Map<String, Object> params = new HashMap<>();
				params.put("aos_itemname_category1", this.getModel().getValue("aos_category1"));
				params.put("aos_itemname_category2", this.getModel().getValue("aos_category2"));
				params.put("aos_itemname_category3", this.getModel().getValue("aos_category3"));
				params.put("EN", "en_US");// 需要获取英文品名
				FndGlobal.OpenForm(this, "aos_mkt_slogan_form", params);
			}
		}
	}

	@Override
	public void registerListener(EventObject e) {
		super.registerListener(e);

		TextEdit aos_category2 = this.getControl("aos_category2");
		aos_category2.addClickListener(this);
		aos_category2.addItemClickListener(this);

		TextEdit aos_category3 = this.getControl("aos_category3");
		aos_category3.addClickListener(this);
		aos_category3.addItemClickListener(this);

		TextEdit aos_cname = this.getControl("aos_cname");
		aos_cname.addClickListener(this);
		aos_cname.addItemClickListener(this);

		EntryGrid aos_entryentity = this.getControl("aos_entryentity");
		aos_entryentity.addClickListener(this);
		aos_entryentity.addItemClickListener(this);

		TextEdit aos_itemname = this.getControl("aos_itemname");
		aos_itemname.addClickListener(this);
		aos_itemname.addItemClickListener(this);
	}

	/** 操作之前事件 **/
	public void beforeDoOperation(BeforeDoOperationEventArgs args) {
		FormOperate formOperate = (FormOperate) args.getSource();
		String Operatation = formOperate.getOperateKey();
		try {
			if ("save".equals(Operatation)) {
				SaveControl(); // 保存校验
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
			if ("save".equals(Operatation)) {
				args.getOperationResult().setShowMessage(false);
			}
		} catch (Exception ex) {
		}
	}

	/** 初始化事件 **/
	public void afterLoadData(EventObject e) {
		super.afterLoadData(e);
		StatusControl();
		aos_egsku_change();
	}

	/** 新建事件 **/
	public void afterCreateNewData(EventObject e) {
		super.afterCreateNewData(e);
		InitDefualt();
		aos_egsku_change();

		Map<String, Object> params = this.getView().getFormShowParameter().getCustomParam("params");
		if (FndGlobal.IsNotNull(params) && params.keySet().contains("TYPE")) {

			this.getModel().setValue("aos_category1", params.get("aos_category1"));
			this.getModel().setValue("aos_category2", params.get("aos_category2"));
			this.getModel().setValue("aos_category3", params.get("aos_category3"));
			this.getModel().setValue("aos_cname", params.get("aos_itemnamecn"));
			this.getModel().setValue("aos_itemname", params.get("aos_itemnamecn"), 0);
			this.getModel().setValue("aos_detail", params.get("aos_detail"));
			this.getModel().setValue("aos_type", "优化");

			String group = params.get("aos_category1") + "," + params.get("aos_category2") + ","
					+ params.get("aos_category3");
			DynamicObject bd_materialgroupd = QueryServiceHelper.queryOne("bd_materialgroupdetail",
					"material.id itemid,material.number item_number", new QFilter("group.name", QCP.equals, group)
							.and("material.name", QCP.equals, params.get("aos_itemnamecn")).toArray());
			if (FndGlobal.IsNotNull(bd_materialgroupd)) {
				this.getModel().setValue("aos_itemid", bd_materialgroupd.getLong("itemid"));

				String url = "https://cls3.s3.amazonaws.com/" + bd_materialgroupd.getString("item_number") + "/1-1.jpg";
				Image image = this.getControl("aos_image");
				image.setUrl(url);
				this.getModel().setValue("aos_picture", url);
			}

		}
	}

	/** 值改变事件 **/
	public void propertyChanged(PropertyChangedArgs e) {
		try {
			String name = e.getProperty().getName();
			if (name.equals("aos_type") || name.equals("aos_lang")) {
				Object aos_status = this.getModel().getValue("aos_status"); // aos_status 流程节点: 申请 翻译 设计 结束
				Object aos_type = this.getModel().getValue("aos_type");// 优化 新增
				if (aos_status != null && aos_type != null) {
					StatusControl();
				}
				if (name.equals("aos_lang")) {
					Object aos_lang = this.getModel().getValue("aos_lang");
					if ("EN".equals(aos_lang)) {
						this.getModel().setValue("aos_osconfirm", false);
						this.getView().setEnable(false, "aos_osconfirm");
					} else {
						this.getModel().setValue("aos_osconfirm", true);
						this.getView().setEnable(true, "aos_osconfirm");
					}
				}
			} else if ("aos_category1".equals(name)) {
				Object aos_category1 = this.getModel().getValue("aos_category1");
				if (FndGlobal.IsNull(aos_category1)) {
					this.getModel().setValue("aos_category2", null);
					this.getModel().setValue("aos_category3", null);
					this.getModel().setValue("aos_cname", null);
				}
			} else if ("aos_category2".equals(name)) {
				Object aos_category2 = this.getModel().getValue("aos_category2");
				if (FndGlobal.IsNull(aos_category2)) {
					this.getModel().setValue("aos_cname", null);
					this.getModel().setValue("aos_category3", null);
				}
			} else if ("aos_category3".equals(name)) {
				Object aos_category3 = this.getModel().getValue("aos_category3");
				if (FndGlobal.IsNull(aos_category3)) {
					this.getModel().setValue("aos_cname", null);
				}
			} else if ("aos_cname".equals(name)) {
				Object aos_category1 = this.getModel().getValue("aos_category1");
				Object aos_category2 = this.getModel().getValue("aos_category2");
				Object aos_category3 = this.getModel().getValue("aos_category3");
				Object aos_cname = this.getModel().getValue("aos_cname");
				// 赋值参考SKU
				if (FndGlobal.IsNotNull(aos_category1) && FndGlobal.IsNotNull(aos_category2)
						&& FndGlobal.IsNotNull(aos_category3) && FndGlobal.IsNotNull(aos_cname)) {
					String group = aos_category1 + "," + aos_category2 + "," + aos_category3;
					DynamicObject bd_materialgroupd = QueryServiceHelper.queryOne("bd_materialgroupdetail",
							"material.id itemid", new QFilter("group.name", QCP.equals, group)
									.and("material.name", QCP.equals, aos_cname).toArray());
					if (FndGlobal.IsNotNull(bd_materialgroupd)) {
						this.getModel().setValue("aos_itemid", bd_materialgroupd.getLong("itemid"));
					}
				}
				if (FndGlobal.IsNull(aos_cname)) {
					this.getModel().setValue("aos_itemid", null);
				}
			} else if (name.equals("aos_itemid")) {
				aos_egsku_change();
			} else if (name.equals("aos_slogan")) {
				int row = this.getModel().getEntryCurrentRowIndex("aos_entryentity");
				Object aos_slogan = this.getModel().getValue("aos_slogan", row);
				if (FndGlobal.IsNotNull(aos_slogan))
					this.getModel().setValue("aos_width", aos_slogan.toString().length() + "/50", row);
				else
					this.getModel().setValue("aos_width", "0/50", row);
			}
		} catch (Exception ex) {
			this.getView().showErrorNotification("propertyChanged = " + ex.toString());
		}
	}

	private void aos_egsku_change() {
		Object aos_egsku = this.getModel().getValue("aos_itemid");
		if (aos_egsku == null) {
			this.getView().setVisible(true, "aos_picture");
			this.getView().setVisible(false, "aos_image");
		} else {
			this.getView().setVisible(false, "aos_picture");
			this.getView().setVisible(true, "aos_image");
			String item_number = ((DynamicObject) aos_egsku).getString("number");
			String itemid = ((DynamicObject) aos_egsku).getString("id");
			String name = ((DynamicObject) aos_egsku).getString("name");

			String url = "https://cls3.s3.amazonaws.com/" + item_number + "/1-1.jpg";
			Image image = this.getControl("aos_image");
			image.setUrl(url);
			this.getModel().setValue("aos_picture", url);

			String category = (String) SalUtil.getCategoryByItemId(itemid).get("name");
			String[] category_group = category.split(",");
			String AosCategory1 = null;
			String AosCategory2 = null;
			String AosCategory3 = null;
			int category_length = category_group.length;
			if (category_length > 0)
				AosCategory1 = category_group[0];
			if (category_length > 1)
				AosCategory2 = category_group[1];
			if (category_length > 2)
				AosCategory3 = category_group[2];

			this.getModel().setValue("aos_cname", name);
			this.getModel().setValue("aos_category3", AosCategory3);
			this.getModel().setValue("aos_category2", AosCategory2);
			this.getModel().setValue("aos_category1", AosCategory1);
		}
	}

	/** 新建设置默认值 **/
	private void InitDefualt() {
		this.getModel().deleteEntryData("aos_entryentity");
		this.getModel().batchCreateNewEntryRow("aos_entryentity", 6);
		this.getModel().setValue("aos_langr", "CN", 0);
		this.getModel().setValue("aos_langr", "EN", 1);
		this.getModel().setValue("aos_langr", "DE", 2);
		this.getModel().setValue("aos_langr", "FR", 3);
		this.getModel().setValue("aos_langr", "IT", 4);
		this.getModel().setValue("aos_langr", "ES", 5);
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String Control = evt.getItemKey();
		try {
			if ("aos_submit".equals(Control)) {
				aos_submit();// 提交
			} else if ("aos_auto".equals(Control)) {
				aos_auto();
			}
		} catch (FndError FndError) {
			this.getView().showTipNotification(FndError.getErrorMessage());
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
		}
	}

	/**
	 * 手工关闭
	 */
	private void aos_auto() throws FndError {
		FndError fndError = new FndError();

		Boolean aos_main = (Boolean) this.getModel().getValue("aos_main");
		Object ReqFId = this.getModel().getDataEntity().getPkValue();// 当前单据主键
		Object aos_sourceid = this.getModel().getValue("aos_sourceid");
		String aos_category1 = (String) this.getModel().getValue("aos_category1");// 大类
		String aos_category2 = (String) this.getModel().getValue("aos_category2");// 中类

		if (!aos_main) {
			Object aos_designer = this.getModel().getValue("aos_designer");
			if (FndGlobal.IsNull(aos_designer)) {
				DynamicObject aos_mkt_proguser = QueryServiceHelper.queryOne("aos_mkt_proguser", "aos_designer",
						new QFilter("aos_category1", "=", aos_category1).and("aos_category2", "=", aos_category2)
								.toArray());
				if (FndGlobal.IsNull(aos_mkt_proguser) || FndGlobal.IsNull(aos_mkt_proguser.get("aos_designer"))) {
					fndError.add("品类设计师不存在!");
					throw fndError;
				}
				aos_designer = aos_mkt_proguser.getLong("aos_designer");// 品类设计师
			}
			DynamicObject aos_mkt_slogan = BusinessDataServiceHelper.loadSingle(aos_sourceid, "aos_mkt_slogan");
			Boolean exist = QueryServiceHelper.exists("aos_mkt_slogan",
					new QFilter("aos_sourceid", QCP.equals, aos_sourceid).and("id", QCP.not_equals, ReqFId)
							.and("aos_status", QCP.in, new String[] { "翻译", "海外翻译" }).toArray());
			if (!exist) {
				aos_mkt_slogan.set("aos_designer", aos_designer);
				aos_mkt_slogan.set("aos_user", aos_designer);
				aos_mkt_slogan.set("aos_status", "设计");
			}
			SaveServiceHelper.saveOperate("aos_mkt_slogan", new DynamicObject[] { aos_mkt_slogan },
					OperateOption.create());
		}

		this.getModel().setValue("aos_status", "结束");
		this.getModel().setValue("aos_user", SYSTEM);
		this.getView().invokeOperation("save");
		this.getView().invokeOperation("refresh");
		this.getView().showSuccessNotification("手工关闭成功!");
	}

	/** 提交 **/
	public void aos_submit() throws FndError {
		this.getView().invokeOperation("save");
		SaveControl();// 先做数据校验判断是否可以提交
		String aos_status = this.getModel().getValue("aos_status").toString();// 根据状态判断当前流程节点
		switch (aos_status) {
		case "申请":
			SubmitAppl();
			break;
		case "翻译":
			SubmitTrans();
			break;
		case "设计":
			SubmitDesign();
			break;
		case "海外翻译":
			SubmitOSTrans();
			break;
		}
		this.getView().invokeOperation("save");
		this.getView().invokeOperation("refresh");

		if ("申请".equals(aos_status)) {
			Map<String, Object> params = this.getView().getFormShowParameter().getCustomParam("params");
			if (FndGlobal.IsNotNull(params) && params.keySet().contains("TYPE")) {
				this.getView().close();
			}
		}
		StatusControl();// 提交完成后做新的界面状态控制
	}

	/**
	 * 海外翻译
	 */
	private void SubmitOSTrans() throws FndError {
		FndError fndError = new FndError();
		Object aos_lang = this.getModel().getValue("aos_lang");// 语言
		Object messageId;
		Object ReqFId = this.getModel().getDataEntity().getPkValue();
		Object billno = this.getModel().getValue("billno");

		// 校验对应小语种slogan与品名是否填写
		DynamicObjectCollection aos_entryentityS = this.getModel().getEntryEntity("aos_entryentity");
		for (DynamicObject aos_entryentity : aos_entryentityS) {
			String aos_langr = aos_entryentity.getString("aos_langr");
			String aos_itemname = aos_entryentity.getString("aos_itemname");
			String aos_slogan = aos_entryentity.getString("aos_slogan");
			if (aos_langr.equals(aos_lang)) {
				if (FndGlobal.IsNull(aos_itemname) || FndGlobal.IsNull(aos_slogan)) {
					fndError.add("Slogan与品名必填!");
				}
			}
		}
	
		Object aos_oueditor = this.getModel().getValue("aos_oueditor") ;
		if (FndGlobal.IsNull(aos_oueditor))
			this.getModel().setValue("aos_user", SYSTEM);
		else 
			this.getModel().setValue("aos_user", this.getModel().getValue("aos_oueditor"));
		this.getModel().setValue("aos_status", "翻译");
		this.getModel().setValue("aos_osubmit", "人为提交");
//		MKTCom.SendGlobalMessage(messageId + "", "aos_mkt_slogan", ReqFId + "", billno + "", "Slogan-翻译");
	}

	private void SubmitDesign() {
		Object aos_type = this.getModel().getValue("aos_type");
		Object ReqFId = this.getModel().getDataEntity().getPkValue();
		Object billno = this.getModel().getValue("billno");
		this.getModel().setValue("aos_status", "结束");
		DynamicObject aos_requireby = (DynamicObject) this.getModel().getValue("aos_requireby");
		this.getModel().setValue("aos_user", SYSTEM);
		if ("新增".equals(aos_type)) {
			newSlogan();
		} else if ("优化".equals(aos_type)) {
			updateSlogan();
		}
		MKTCom.SendGlobalMessage(aos_requireby.getPkValue() + "", "aos_mkt_listing_min", ReqFId + "", billno + "",
				"Slogan-结束");
	}

	private void updateSlogan() {
		// 大类
		Object aos_category1 = this.getModel().getValue("aos_category1");
		// 中类
		Object aos_category2 = this.getModel().getValue("aos_category2");
		// 小类
		Object aos_category3 = this.getModel().getValue("aos_category3");
		Object aos_detail = this.getModel().getValue("aos_detail");
		DynamicObjectCollection aos_entryentityS = this.getModel().getEntryEntity("aos_entryentity");
		String aos_cname = aos_entryentityS.get(0).getString("aos_itemname");
		DynamicObject aos_mkt_data_slogan = QueryServiceHelper.queryOne("aos_mkt_data_slogan", "id",
				new QFilter("aos_category1", QCP.equals, aos_category1).and("aos_category2", QCP.equals, aos_category2)
						.and("aos_category3", QCP.equals, aos_category3).and("aos_itemnamecn", QCP.equals, aos_cname)
						.and("aos_detail", QCP.equals, aos_detail).toArray());
		DynamicObject slogan = BusinessDataServiceHelper.loadSingle(aos_mkt_data_slogan.get("id"),
				"aos_mkt_data_slogan");

		String aos_itemnamecn = aos_entryentityS.get(0).getString("aos_itemname");
		if (FndGlobal.IsNotNull(aos_itemnamecn))
			slogan.set("aos_itemnamecn", aos_itemnamecn);

		String aos_slogancn = aos_entryentityS.get(0).getString("aos_slogan");
		if (FndGlobal.IsNotNull(aos_slogancn))
			slogan.set("aos_slogancn", aos_slogancn);

		String aos_itemnameen = aos_entryentityS.get(1).getString("aos_itemname");
		if (FndGlobal.IsNotNull(aos_itemnameen))
			slogan.set("aos_itemnameen", aos_itemnameen);

		String aos_sloganen = aos_entryentityS.get(1).getString("aos_slogan");
		if (FndGlobal.IsNotNull(aos_sloganen))
			slogan.set("aos_sloganen", aos_sloganen);

		String aos_itemnamede = aos_entryentityS.get(2).getString("aos_itemname");
		if (FndGlobal.IsNotNull(aos_itemnamede))
			slogan.set("aos_itemnamede", aos_itemnamede);

		String aos_slogande = aos_entryentityS.get(2).getString("aos_slogan");
		if (FndGlobal.IsNotNull(aos_slogande))
			slogan.set("aos_slogande", aos_slogande);

		String aos_itemnamefr = aos_entryentityS.get(3).getString("aos_itemname");
		if (FndGlobal.IsNotNull(aos_itemnamefr))
			slogan.set("aos_itemnamefr", aos_itemnamefr);

		String aos_sloganfr = aos_entryentityS.get(3).getString("aos_slogan");
		if (FndGlobal.IsNotNull(aos_sloganfr))
			slogan.set("aos_sloganfr", aos_sloganfr);

		String aos_itemnameit = aos_entryentityS.get(4).getString("aos_itemname");
		if (FndGlobal.IsNotNull(aos_itemnameit))
			slogan.set("aos_itemnameit", aos_itemnameit);

		String aos_sloganit = aos_entryentityS.get(4).getString("aos_slogan");
		if (FndGlobal.IsNotNull(aos_sloganit))
			slogan.set("aos_sloganit", aos_sloganit);

		String aos_itemnamees = aos_entryentityS.get(5).getString("aos_itemname");
		if (FndGlobal.IsNotNull(aos_itemnamees))
			slogan.set("aos_itemnamees", aos_itemnamees);

		String aos_sloganes = aos_entryentityS.get(5).getString("aos_slogan");
		if (FndGlobal.IsNotNull(aos_sloganes))
			slogan.set("aos_sloganes", aos_sloganes);

		SaveServiceHelper.saveOperate("aos_mkt_data_slogan", new DynamicObject[] { slogan }, OperateOption.create());
	}

	private void newSlogan() throws FndError {
		String aos_category1 = (String) this.getModel().getValue("aos_category1");// 大类
		String aos_category2 = (String) this.getModel().getValue("aos_category2");// 中类
		Object aos_detail = this.getModel().getValue("aos_detail");
		DynamicObjectCollection aos_entryentityS = this.getModel().getEntryEntity("aos_entryentity");
		DynamicObject aos_mkt_data_slogan = BusinessDataServiceHelper.newDynamicObject("aos_mkt_data_slogan"); // 品名Slogan库
		aos_mkt_data_slogan.set("aos_category1", aos_category1);
		aos_mkt_data_slogan.set("aos_category2", aos_category2);
		aos_mkt_data_slogan.set("aos_category3", this.getModel().getValue("aos_category3"));
		aos_mkt_data_slogan.set("aos_detail", aos_detail);
		aos_mkt_data_slogan.set("aos_category3", this.getModel().getValue("aos_category3"));
		long aos_eng = 0;
		QFilter filter_category1 = new QFilter("aos_category1", "=", aos_category1);
		QFilter filter_category2 = new QFilter("aos_category2", "=", aos_category2);
		QFilter[] filters_category = new QFilter[] { filter_category1, filter_category2 };
		DynamicObject aos_mkt_proguser = QueryServiceHelper.queryOne("aos_mkt_proguser", "aos_eng", filters_category);
		if (aos_mkt_proguser != null) {
			aos_eng = aos_mkt_proguser.getLong("aos_eng");
		}
		if (aos_eng == 0) {
			FndError fndMessage = new FndError("英语编辑不存在!");
			throw fndMessage;
		}
		aos_mkt_data_slogan.set("aos_user", aos_eng);
		aos_mkt_data_slogan.set("aos_itemid", this.getModel().getValue("aos_itemid"));
		aos_mkt_data_slogan.set("aos_itemnamecn", aos_entryentityS.get(0).get("aos_itemname"));
		aos_mkt_data_slogan.set("aos_slogancn", aos_entryentityS.get(0).get("aos_slogan"));
		aos_mkt_data_slogan.set("aos_itemnameen", aos_entryentityS.get(1).get("aos_itemname"));
		aos_mkt_data_slogan.set("aos_sloganen", aos_entryentityS.get(1).get("aos_slogan"));
		aos_mkt_data_slogan.set("aos_itemnamede", aos_entryentityS.get(2).get("aos_itemname"));
		aos_mkt_data_slogan.set("aos_slogande", aos_entryentityS.get(2).get("aos_slogan"));
		aos_mkt_data_slogan.set("aos_itemnamefr", aos_entryentityS.get(3).get("aos_itemname"));
		aos_mkt_data_slogan.set("aos_sloganfr", aos_entryentityS.get(3).get("aos_slogan"));
		aos_mkt_data_slogan.set("aos_itemnameit", aos_entryentityS.get(4).get("aos_itemname"));
		aos_mkt_data_slogan.set("aos_sloganit", aos_entryentityS.get(4).get("aos_slogan"));
		aos_mkt_data_slogan.set("aos_itemnamees", aos_entryentityS.get(5).get("aos_itemname"));
		aos_mkt_data_slogan.set("aos_sloganes", aos_entryentityS.get(5).get("aos_slogan"));
		SaveServiceHelper.saveOperate("aos_mkt_data_slogan", new DynamicObject[] { aos_mkt_data_slogan },
				OperateOption.create());
	}

	/**
	 * 翻译节点提交
	 * 
	 * @throws FndError
	 */
	private void SubmitTrans() throws FndError {
		FndError fndError = new FndError();
		Object ReqFId = this.getModel().getDataEntity().getPkValue();
		String aos_category1 = (String) this.getModel().getValue("aos_category1");// 大类
		String aos_category2 = (String) this.getModel().getValue("aos_category2");// 中类
		Object billno = this.getModel().getValue("billno");
		Boolean aos_main = (Boolean) this.getModel().getValue("aos_main");
		Object aos_sourceid = this.getModel().getValue("aos_sourceid");
		Object aos_lang = this.getModel().getValue("aos_lang");
		Object aos_designer = this.getModel().getValue("aos_designer");
		Object messageId;
		// 校验对应小语种slogan与品名是否填写
		DynamicObjectCollection aos_entryentityS_O = this.getModel().getEntryEntity("aos_entryentity");
		for (DynamicObject aos_entryentity : aos_entryentityS_O) {
			String aos_langr = aos_entryentity.getString("aos_langr");
			String aos_itemname = aos_entryentity.getString("aos_itemname");
			String aos_slogan = aos_entryentity.getString("aos_slogan");
			if (aos_langr.equals(aos_lang)) {
				if (FndGlobal.IsNull(aos_itemname) || FndGlobal.IsNull(aos_slogan)) {
					fndError.add("Slogan与品名必填!");
					throw fndError;
				}
			}
		}
		if (FndGlobal.IsNull(aos_designer)) {
			DynamicObject aos_mkt_proguser = QueryServiceHelper.queryOne("aos_mkt_proguser", "aos_designer",
					new QFilter("aos_category1", "=", aos_category1).and("aos_category2", "=", aos_category2)
							.toArray());
			if (FndGlobal.IsNull(aos_mkt_proguser) || FndGlobal.IsNull(aos_mkt_proguser.get("aos_designer"))) {
				fndError.add("品类设计师不存在!");
				throw fndError;
			}
			aos_designer = aos_mkt_proguser.getLong("aos_designer");// 品类设计师
			messageId = aos_designer;
		} else {
			messageId = ((DynamicObject) aos_designer).getPkValue();
		}

		if (aos_main) {
			this.getModel().setValue("aos_designer", aos_designer);
			this.getModel().setValue("aos_user", aos_designer);
			this.getModel().setValue("aos_status", "设计");
			MKTCom.SendGlobalMessage(messageId + "", "aos_mkt_slogan", ReqFId + "", billno + "", "Slogan-设计");
		} else {
			this.getModel().setValue("aos_user", SYSTEM);
			this.getModel().setValue("aos_status", "结束");
			DynamicObject aos_mkt_slogan = BusinessDataServiceHelper.loadSingle(aos_sourceid, "aos_mkt_slogan");
			DynamicObjectCollection aos_entryentityS = aos_mkt_slogan.getDynamicObjectCollection("aos_entryentity");
			for (DynamicObject aos_entryentity : aos_entryentityS) {
				String aos_langr = aos_entryentity.getString("aos_langr");
				if (!aos_langr.equals(aos_lang))
					continue;
				aos_entryentity.set("aos_itemname", this.getModel().getValue("aos_itemname", 2));
				aos_entryentity.set("aos_slogan", this.getModel().getValue("aos_slogan", 2));
				aos_entryentity.set("aos_width", this.getModel().getValue("aos_width", 2));
			}
			Boolean exist = QueryServiceHelper.exists("aos_mkt_slogan",
					new QFilter("aos_sourceid", QCP.equals, aos_sourceid).and("id", QCP.not_equals, ReqFId)
							.and("aos_status", QCP.in, new String[] { "翻译", "海外翻译" }).toArray());
			if (!exist) {
				aos_mkt_slogan.set("aos_designer", aos_designer);
				aos_mkt_slogan.set("aos_user", aos_designer);
				aos_mkt_slogan.set("aos_status", "设计");
			}
			SaveServiceHelper.saveOperate("aos_mkt_slogan", new DynamicObject[] { aos_mkt_slogan },
					OperateOption.create());
		}
	}

	private void SubmitAppl() throws FndError {
		FndError fndError = new FndError();
		// 校验是否已存在当前任务
		Object aos_type = this.getModel().getValue("aos_type");// 类型
		Object aos_lang = this.getModel().getValue("aos_lang");// 语言
		Object aos_category1 = this.getModel().getValue("aos_category1");// 大类
		Object aos_category2 = this.getModel().getValue("aos_category2");// 中类
		Object aos_category3 = this.getModel().getValue("aos_category3");// 小类
		Object aos_detail = this.getModel().getValue("aos_detail");// 属性细分
		Object ReqFId = this.getModel().getDataEntity().getPkValue();// 当前单据主键
		Object aos_cname = this.getModel().getValue("aos_cname");// CN品名
		Boolean aos_osconfirm = (Boolean) this.getModel().getValue("aos_osconfirm");// 是否海外确认
		Object aos_itemid = this.getModel().getValue("aos_itemid");// 参考SKU
		Object billno = this.getModel().getValue("billno");// 单据编号
		Object messageId;
		Object aos_designer = this.getModel().getValue("aos_designer");
		DynamicObject aos_mkt_progorguserA = QueryServiceHelper.queryOne("aos_mkt_progorguser", "aos_oueditor",
				new QFilter("aos_category1", "=", aos_category1).and("aos_category2", "=", aos_category2)
						.and("aos_orgid.number", "=", aos_lang).toArray());
		if (FndGlobal.IsNull(aos_mkt_progorguserA) || FndGlobal.IsNull(aos_mkt_progorguserA.get("aos_oueditor"))) {
			fndError.add("国别编辑不存在!");
		}
		this.getModel().setValue("aos_oueditor", aos_mkt_progorguserA.get("aos_oueditor"));
		
		if ("新增".equals(aos_type)) {
			DynamicObject exist = QueryServiceHelper.queryOne("aos_mkt_slogan", "billno",
					new QFilter("aos_category1", QCP.equals, aos_category1)
							.and("aos_category2", QCP.equals, aos_category2)
							.and("aos_category3", QCP.equals, aos_category3).and("aos_cname", QCP.equals, aos_cname)
							.and("aos_detail", QCP.equals, aos_detail).and("aos_status", QCP.not_equals, "结束")
							.and("id", QCP.not_equals, ReqFId).toArray());
			if (FndGlobal.IsNotNull(exist)) {
				fndError.add("已存在任务" + exist.getString("billno") + "!");
			}
		} else if ("优化".equals(aos_type)) {
			DynamicObject exist = QueryServiceHelper.queryOne("aos_mkt_slogan", "billno",
					new QFilter("aos_category1", QCP.equals, aos_category1)
							.and("aos_category2", QCP.equals, aos_category2)
							.and("aos_category3", QCP.equals, aos_category3).and("aos_cname", QCP.equals, aos_cname)
							.and("aos_detail", QCP.equals, aos_detail).and("aos_lang", QCP.equals, aos_lang)
							.and("aos_status", QCP.not_equals, "结束").and("id", QCP.not_equals, ReqFId).toArray());
			if (FndGlobal.IsNotNull(exist)) {
				fndError.add("已存在任务" + exist.getString("billno") + "!");
			}
		}
		if (fndError.getCount() > 0) {
			throw fndError;
		}

		// 判断是否需要拆分单据
		if ("CN".equals(aos_lang)) {
			ArrayList<String> orgS = new ArrayList<>();
			orgS.add("DE");
			orgS.add("FR");
			orgS.add("IT");
			orgS.add("ES");
			for (String org : orgS) {
				DynamicObject aos_mkt_slogan = BusinessDataServiceHelper.newDynamicObject("aos_mkt_slogan");
				aos_mkt_slogan.set("aos_requireby", this.getModel().getValue("aos_requireby"));
				aos_mkt_slogan.set("aos_requiredate", this.getModel().getValue("aos_requiredate"));
				aos_mkt_slogan.set("aos_type", aos_type);
				aos_mkt_slogan.set("aos_lang", org);
				aos_mkt_slogan.set("aos_category1", aos_category1);
				aos_mkt_slogan.set("aos_category2", aos_category2);
				aos_mkt_slogan.set("aos_category3", aos_category3);
				aos_mkt_slogan.set("aos_cname", aos_cname);
				aos_mkt_slogan.set("aos_detail", aos_detail);
				aos_mkt_slogan.set("aos_itemid", aos_itemid);
				aos_mkt_slogan.set("aos_osconfirm", aos_osconfirm);
				aos_mkt_slogan.set("aos_main", false);
				aos_mkt_slogan.set("aos_sourcebillno", billno);
				aos_mkt_slogan.set("aos_sourceid", ReqFId);
				if (aos_osconfirm) {
					DynamicObject aos_mkt_progorguser = QueryServiceHelper.queryOne("aos_mkt_progorguser",
							"aos_oseditor",
							new QFilter("aos_category1", "=", aos_category1).and("aos_category2", "=", aos_category2)
									.and("aos_orgid.number", "=", org).toArray());
					if (FndGlobal.IsNull(aos_mkt_progorguser)
							|| FndGlobal.IsNull(aos_mkt_progorguser.get("aos_oseditor"))) {
						fndError.add("小语种海外编辑不存在!");
						throw fndError;
					}
					long aos_oseditor = aos_mkt_progorguser.getLong("aos_oseditor");
					messageId = aos_oseditor;
					aos_mkt_slogan.set("aos_oseditor", aos_oseditor);
					aos_mkt_slogan.set("aos_user", aos_oseditor);
					aos_mkt_slogan.set("aos_status", "海外翻译");
					aos_mkt_slogan.set("aos_osdate", new Date());
				} else {
					DynamicObject aos_mkt_progorguser = QueryServiceHelper.queryOne("aos_mkt_progorguser",
							"aos_oueditor",
							new QFilter("aos_category1", "=", aos_category1).and("aos_category2", "=", aos_category2)
									.and("aos_orgid.number", "=", org).toArray());
					if (FndGlobal.IsNull(aos_mkt_progorguser)
							|| FndGlobal.IsNull(aos_mkt_progorguser.get("aos_oueditor"))) {
						fndError.add("小语种国别编辑不存在!");
						throw fndError;
					}
					long aos_oueditor = aos_mkt_progorguser.getLong("aos_oueditor");
					messageId = aos_oueditor;
					aos_mkt_slogan.set("aos_oseditor", aos_oueditor);
					aos_mkt_slogan.set("aos_user", aos_oueditor);
					aos_mkt_slogan.set("aos_status", "翻译");
				}
				DynamicObjectCollection aos_entryentityS = aos_mkt_slogan.getDynamicObjectCollection("aos_entryentity");
				DynamicObject aos_entryentity = aos_entryentityS.addNew();
				aos_entryentity.set("aos_langr", "CN");
				aos_entryentity.set("aos_itemname", this.getModel().getValue("aos_itemname", 0));
				aos_entryentity.set("aos_slogan", this.getModel().getValue("aos_slogan", 0));
				aos_entryentity.set("aos_width", this.getModel().getValue("aos_width", 0));
				aos_entryentity = aos_entryentityS.addNew();
				aos_entryentity.set("aos_langr", "EN");
				aos_entryentity.set("aos_itemname", this.getModel().getValue("aos_itemname", 1));
				aos_entryentity.set("aos_slogan", this.getModel().getValue("aos_slogan", 1));
				aos_entryentity.set("aos_width", this.getModel().getValue("aos_width", 1));

				DynamicObjectCollection aos_entryentityOS = this.getModel().getEntryEntity("aos_entryentity");
				for (DynamicObject aos_entryentityO : aos_entryentityOS) {
					if (org.equals(aos_entryentityO.getString("aos_langr"))) {
						aos_entryentity = aos_entryentityS.addNew();
						aos_entryentity.set("aos_langr", org);
						aos_entryentity.set("aos_itemname", aos_entryentityO.get("aos_itemname"));
						aos_entryentity.set("aos_slogan", aos_entryentityO.get("aos_slogan"));
					}
				}
				OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_slogan",
						new DynamicObject[] { aos_mkt_slogan }, OperateOption.create());
				MKTCom.SendGlobalMessage(messageId + "", "aos_mkt_slogan", operationrst.getSuccessPkIds().get(0) + "",
						aos_mkt_slogan.getString("billno"), "Slogan-小语种翻译");
			}
			this.getModel().setValue("aos_status", "翻译");
			this.getModel().setValue("aos_user", SYSTEM);
		} else if ("EN".equals(aos_lang)) {
			if (FndGlobal.IsNull(aos_designer)) {
				DynamicObject aos_mkt_proguser = QueryServiceHelper.queryOne("aos_mkt_proguser", "aos_designer",
						new QFilter("aos_category1", "=", aos_category1).and("aos_category2", "=", aos_category2)
								.toArray());
				if (FndGlobal.IsNull(aos_mkt_proguser) || FndGlobal.IsNull(aos_mkt_proguser.get("aos_designer"))) {
					fndError.add("品类设计师不存在!");
					throw fndError;
				}
				aos_designer = aos_mkt_proguser.getLong("aos_designer");// 品类设计师
				messageId = aos_designer;
			} else {
				messageId = ((DynamicObject) aos_designer).getPkValue();
			}
			this.getModel().setValue("aos_designer", aos_designer);
			this.getModel().setValue("aos_user", aos_designer);
			this.getModel().setValue("aos_status", "设计");
		} else if ("DE/FR/IT/ES".contains(aos_lang + "")) {
			if (aos_osconfirm) {
				DynamicObject aos_mkt_progorguser = QueryServiceHelper.queryOne("aos_mkt_progorguser", "aos_oseditor",
						new QFilter("aos_category1", "=", aos_category1).and("aos_category2", "=", aos_category2)
								.and("aos_orgid.number", "=", aos_lang).toArray());
				if (FndGlobal.IsNull(aos_mkt_progorguser)
						|| FndGlobal.IsNull(aos_mkt_progorguser.get("aos_oseditor"))) {
					fndError.add("小语种海外编辑不存在!");
					throw fndError;
				}
				long aos_oseditor = aos_mkt_progorguser.getLong("aos_oseditor");
				messageId = aos_oseditor;
				this.getModel().setValue("aos_oseditor", aos_oseditor);
				this.getModel().setValue("aos_user", aos_oseditor);
				this.getModel().setValue("aos_status", "海外翻译");
				this.getModel().setValue("aos_osdate", new Date());
			} else {
				DynamicObject aos_mkt_progorguser = QueryServiceHelper.queryOne("aos_mkt_progorguser", "aos_oueditor",
						new QFilter("aos_category1", "=", aos_category1).and("aos_category2", "=", aos_category2)
								.and("aos_orgid.number", "=", aos_lang).toArray());
				if (FndGlobal.IsNull(aos_mkt_progorguser)
						|| FndGlobal.IsNull(aos_mkt_progorguser.get("aos_oueditor"))) {
					fndError.add("小语种国别编辑不存在!");
					throw fndError;
				}
				long aos_oueditor = aos_mkt_progorguser.getLong("aos_oueditor");
				messageId = aos_oueditor;
				this.getModel().setValue("aos_oueditor", aos_oueditor);
				this.getModel().setValue("aos_user", aos_oueditor);
				this.getModel().setValue("aos_status", "翻译");
			}
		}

	}

	/**
	 * 状态控制
	 */
	private void StatusControl() {
		Object aos_status = this.getModel().getValue("aos_status"); // aos_status 流程节点: 申请 翻译 设计 结束
		Object AosUser = this.getModel().getValue("aos_user"); // AosUser 当前节点操作人
		Object aos_type = this.getModel().getValue("aos_type");// 优化 新增
		Object aos_lang = this.getModel().getValue("aos_lang");// 语言
		String AosUserId = null;
		if (AosUser instanceof String)
			AosUserId = (String) AosUser;
		else if (AosUser instanceof Long)
			AosUserId = AosUser + "";
		else
			AosUserId = ((DynamicObject) AosUser).getString("id"); // AosUserId 当前节点操作人 String类型
		Object CurrentUserId = UserServiceHelper.getCurrentUserId();
		Object CurrentUserName = UserServiceHelper.getUserInfoByID((long) CurrentUserId).get("name");

		// 当前节点操作人不为当前用户 全锁
//		if (!AosUserId.equals(CurrentUserId.toString()) && !"程震杰".equals(CurrentUserName.toString())
//				&& !"陈聪".equals(CurrentUserName.toString()) && !"刘中怀".equals(CurrentUserName.toString())) {
//			this.getView().setEnable(false, "aos_flexpanelap");// 标题面板
//			this.getView().setEnable(false, "aos_entryentity");// 主界面面板
//			this.getView().setVisible(false, "bar_save");
//			this.getView().setVisible(false, "aos_submit");
//		}

		// 按钮状态控制
		if ("结束".equals(aos_status)) {
			this.getView().setVisible(false, "aos_submit");
			this.getView().setVisible(false, "bar_save");
			this.getView().setEnable(false, "aos_flexpanelap");
			this.getView().setEnable(false, "aos_entryentity");
		} else if ("翻译".equals(aos_status)) {
			this.getView().setVisible(true, "aos_auto");
		} else {
			this.getView().setVisible(false, "aos_auto");
		}

		// 字段状态控制
		this.getView().setEnable(false, 0, "aos_itemname");
		if ("申请".equals(aos_status)) {
			if ("新增".equals(aos_type)) {
				this.getView().setEnable(true, 0, "aos_slogan");
				this.getView().setEnable(true, 1, "aos_itemname");
				this.getView().setEnable(true, 1, "aos_slogan");
				this.getView().setEnable(false, "aos_lang");
				this.getModel().setValue("aos_lang", "CN");
				this.getView().setEnable(false, 2, "aos_itemname");
				this.getView().setEnable(false, 2, "aos_slogan");
				this.getView().setEnable(false, 3, "aos_itemname");
				this.getView().setEnable(false, 3, "aos_slogan");
				this.getView().setEnable(false, 4, "aos_itemname");
				this.getView().setEnable(false, 4, "aos_slogan");
				this.getView().setEnable(false, 5, "aos_itemname");
				this.getView().setEnable(false, 5, "aos_slogan");
			} else if ("优化".equals(aos_type)) {
				this.getView().setEnable(true, "aos_lang");
				if ("CN".equals(aos_lang)) {
					this.getView().setEnable(true, 0, "aos_slogan");
					this.getView().setEnable(true, 1, "aos_itemname");
					this.getView().setEnable(true, 1, "aos_slogan");
					this.getView().setEnable(false, 2, "aos_itemname");
					this.getView().setEnable(false, 2, "aos_slogan");
					this.getView().setEnable(false, 3, "aos_itemname");
					this.getView().setEnable(false, 3, "aos_slogan");
					this.getView().setEnable(false, 4, "aos_itemname");
					this.getView().setEnable(false, 4, "aos_slogan");
					this.getView().setEnable(false, 5, "aos_itemname");
					this.getView().setEnable(false, 5, "aos_slogan");
				} else if ("EN".equals(aos_lang)) {
					this.getView().setEnable(false, 0, "aos_slogan");
					this.getView().setEnable(true, 1, "aos_itemname");
					this.getView().setEnable(true, 1, "aos_slogan");
					this.getView().setEnable(false, 2, "aos_itemname");
					this.getView().setEnable(false, 2, "aos_slogan");
					this.getView().setEnable(false, 3, "aos_itemname");
					this.getView().setEnable(false, 3, "aos_slogan");
					this.getView().setEnable(false, 4, "aos_itemname");
					this.getView().setEnable(false, 4, "aos_slogan");
					this.getView().setEnable(false, 5, "aos_itemname");
					this.getView().setEnable(false, 5, "aos_slogan");
				} else if ("DE".equals(aos_lang)) {
					this.getView().setEnable(false, 0, "aos_slogan");
					this.getView().setEnable(false, 1, "aos_itemname");
					this.getView().setEnable(false, 1, "aos_slogan");
					this.getView().setEnable(true, 2, "aos_itemname");
					this.getView().setEnable(true, 2, "aos_slogan");
					this.getView().setEnable(false, 3, "aos_itemname");
					this.getView().setEnable(false, 3, "aos_slogan");
					this.getView().setEnable(false, 4, "aos_itemname");
					this.getView().setEnable(false, 4, "aos_slogan");
					this.getView().setEnable(false, 5, "aos_itemname");
					this.getView().setEnable(false, 5, "aos_slogan");
				} else if ("FR".equals(aos_lang)) {
					this.getView().setEnable(false, 0, "aos_slogan");
					this.getView().setEnable(false, 1, "aos_itemname");
					this.getView().setEnable(false, 1, "aos_slogan");
					this.getView().setEnable(false, 2, "aos_itemname");
					this.getView().setEnable(false, 2, "aos_slogan");
					this.getView().setEnable(true, 3, "aos_itemname");
					this.getView().setEnable(true, 3, "aos_slogan");
					this.getView().setEnable(false, 4, "aos_itemname");
					this.getView().setEnable(false, 4, "aos_slogan");
					this.getView().setEnable(false, 5, "aos_itemname");
					this.getView().setEnable(false, 5, "aos_slogan");
				} else if ("IT".equals(aos_lang)) {
					this.getView().setEnable(false, 0, "aos_slogan");
					this.getView().setEnable(false, 1, "aos_itemname");
					this.getView().setEnable(false, 1, "aos_slogan");
					this.getView().setEnable(false, 2, "aos_itemname");
					this.getView().setEnable(false, 2, "aos_slogan");
					this.getView().setEnable(false, 3, "aos_itemname");
					this.getView().setEnable(false, 3, "aos_slogan");
					this.getView().setEnable(true, 4, "aos_itemname");
					this.getView().setEnable(true, 4, "aos_slogan");
					this.getView().setEnable(false, 5, "aos_itemname");
					this.getView().setEnable(false, 5, "aos_slogan");
				} else if ("ES".equals(aos_lang)) {
					this.getView().setEnable(false, 0, "aos_slogan");
					this.getView().setEnable(false, 1, "aos_itemname");
					this.getView().setEnable(false, 1, "aos_slogan");
					this.getView().setEnable(false, 2, "aos_itemname");
					this.getView().setEnable(false, 2, "aos_slogan");
					this.getView().setEnable(false, 3, "aos_itemname");
					this.getView().setEnable(false, 3, "aos_slogan");
					this.getView().setEnable(false, 4, "aos_itemname");
					this.getView().setEnable(false, 4, "aos_slogan");
					this.getView().setEnable(true, 5, "aos_itemname");
					this.getView().setEnable(true, 5, "aos_slogan");
				}
			}
		} else if ("海外翻译".equals(aos_status) || "翻译".equals(aos_status)) {
			this.getView().setEnable(false, "aos_flexpanelap1");
			this.getView().setEnable(false, 0, "aos_slogan");
			this.getView().setEnable(false, 1, "aos_itemname");
			this.getView().setEnable(false, 1, "aos_slogan");
			if ("优化".equals(aos_type)) {
				if ("DE".equals(aos_lang)) {
					this.getView().setEnable(true, 2, "aos_itemname");
					this.getView().setEnable(true, 2, "aos_slogan");
					this.getView().setEnable(false, 3, "aos_itemname");
					this.getView().setEnable(false, 3, "aos_slogan");
					this.getView().setEnable(false, 4, "aos_itemname");
					this.getView().setEnable(false, 4, "aos_slogan");
					this.getView().setEnable(false, 5, "aos_itemname");
					this.getView().setEnable(false, 5, "aos_slogan");
				} else if ("FR".equals(aos_lang)) {
					this.getView().setEnable(false, 2, "aos_itemname");
					this.getView().setEnable(false, 2, "aos_slogan");
					this.getView().setEnable(true, 3, "aos_itemname");
					this.getView().setEnable(true, 3, "aos_slogan");
					this.getView().setEnable(false, 4, "aos_itemname");
					this.getView().setEnable(false, 4, "aos_slogan");
					this.getView().setEnable(false, 5, "aos_itemname");
					this.getView().setEnable(false, 5, "aos_slogan");
				} else if ("IT".equals(aos_lang)) {
					this.getView().setEnable(false, 2, "aos_itemname");
					this.getView().setEnable(false, 2, "aos_slogan");
					this.getView().setEnable(false, 3, "aos_itemname");
					this.getView().setEnable(false, 3, "aos_slogan");
					this.getView().setEnable(true, 4, "aos_itemname");
					this.getView().setEnable(true, 4, "aos_slogan");
					this.getView().setEnable(false, 5, "aos_itemname");
					this.getView().setEnable(false, 5, "aos_slogan");
				} else if ("ES".equals(aos_lang)) {
					this.getView().setEnable(false, 2, "aos_itemname");
					this.getView().setEnable(false, 2, "aos_slogan");
					this.getView().setEnable(false, 3, "aos_itemname");
					this.getView().setEnable(false, 3, "aos_slogan");
					this.getView().setEnable(false, 4, "aos_itemname");
					this.getView().setEnable(false, 4, "aos_slogan");
					this.getView().setEnable(true, 5, "aos_itemname");
					this.getView().setEnable(true, 5, "aos_slogan");
				}
			} else if ("新增".equals(aos_type)) {
				this.getView().setEnable(true, 2, "aos_itemname");
				this.getView().setEnable(true, 2, "aos_slogan");
			}
		} else if ("设计".equals(aos_status)) {
			this.getView().setEnable(false, "aos_flexpanelap1");
			this.getView().setEnable(false, "aos_entryentity");
		}
	}

	/** 值校验 **/
	private void SaveControl() throws FndError {
		FndError fndError = new FndError();
		// 数据层
		// 流程节点
		Object aos_status = this.getModel().getValue("aos_status");
		// 类型
		Object aos_type = this.getModel().getValue("aos_type");
		// 语言
		Object aos_lang = this.getModel().getValue("aos_lang");
		// 大类
		Object aos_category1 = this.getModel().getValue("aos_category1");
		// 中类
		Object aos_category2 = this.getModel().getValue("aos_category2");
		// 小类
		Object aos_category3 = this.getModel().getValue("aos_category3");
		// 参考SKU
		Object aos_itemid = this.getModel().getValue("aos_itemid");
		// 属性细分
		Object aos_detail = this.getModel().getValue("aos_detail");

		Object aos_cname = this.getModel().getValue("aos_cname");

		DynamicObjectCollection aos_entryentityS = this.getModel().getEntryEntity("aos_entryentity");
		// 新建状态下校验必填
		if ("申请".equals(aos_status)) {
			if (FndGlobal.IsNull(aos_type)) {
				fndError.add("类型必填!");
			}
			if (FndGlobal.IsNull(aos_lang)) {
				fndError.add("语言必填!");
			}
			if (FndGlobal.IsNull(aos_category1)) {
				fndError.add("大类必填!");
			}
			if (FndGlobal.IsNull(aos_category2)) {
				fndError.add("中类必填!");
			}
			if (FndGlobal.IsNull(aos_category3)) {
				fndError.add("小类必填!");
			}
			if ("新增".equals(aos_type) && FndGlobal.IsNull(aos_itemid)) {
				fndError.add("新增类型下参考SKU必填!");
			}
			if (aos_lang != null) {
				if ("新增".equals(aos_type) && !aos_lang.equals("CN") && !aos_lang.equals("EN")) {
					fndError.add("类型为新增时，只能选择EN或者CN");
				}
			}

			for (DynamicObject aos_entryentity : aos_entryentityS) {
				String aos_langr = aos_entryentity.getString("aos_langr");
				String aos_itemname = aos_entryentity.getString("aos_itemname");
				String aos_slogan = aos_entryentity.getString("aos_slogan");

				if ("CN".equals(aos_langr)) {
					if (FndGlobal.IsNull(aos_itemname) && "新增".equals(aos_type)) {
						fndError.add("CN品名必填!");
					}
					if (FndGlobal.IsNull(aos_slogan) && "新增".equals(aos_type)) {
						fndError.add("新增类型下CN-slogan必填!");
					}
				}

				if ("EN".equals(aos_langr)) {
					if (FndGlobal.IsNull(aos_itemname) && "新增".equals(aos_type)) {
						fndError.add("新增类型下EN品名必填!");
					}
					if (FndGlobal.IsNull(aos_slogan) && "新增".equals(aos_type)) {
						fndError.add("新增类型下EN-slogan必填!");
					}
				}

				if ("DE".equals(aos_langr) && "DE".equals(aos_lang)) {
					if (FndGlobal.IsNull(aos_itemname)) {
						fndError.add("优化类型下DE品名必填!");
					}
				}

				if ("FR".equals(aos_langr) && "FR".equals(aos_lang)) {
					if (FndGlobal.IsNull(aos_itemname)) {
						fndError.add("优化类型下FR品名必填!");
					}
				}

				if ("IT".equals(aos_langr) && "IT".equals(aos_lang)) {
					if (FndGlobal.IsNull(aos_itemname)) {
						fndError.add("优化类型下IT品名必填!");
					}
				}

				if ("ES".equals(aos_langr) && "ES".equals(aos_lang)) {
					if (FndGlobal.IsNull(aos_itemname)) {
						fndError.add("优化类型下ES品名必填!");
					}
				}
			}

			if ("新增".equals(aos_type)) {
				// 校验唯一性
				Boolean exists = QueryServiceHelper.exists("aos_mkt_data_slogan",
						new QFilter("aos_category1", QCP.equals, aos_category1)
								.and("aos_category2", QCP.equals, aos_category2)
								.and("aos_category3", QCP.equals, aos_category3)
								.and("aos_itemnamecn", QCP.equals, aos_cname).and("aos_detail", QCP.equals, aos_detail)
								.toArray());
				if (exists) {
					fndError.add("品名Slogan已存在!");
				}
			}

			else if ("优化".equals(aos_type)) {
				// 校验唯一性
				Boolean exists = QueryServiceHelper.exists("aos_mkt_data_slogan",
						new QFilter("aos_category1", QCP.equals, aos_category1)
								.and("aos_category2", QCP.equals, aos_category2)
								.and("aos_category3", QCP.equals, aos_category3)
								.and("aos_itemnamecn", QCP.equals, aos_cname).and("aos_detail", QCP.equals, aos_detail)
								.toArray());
				if (!exists) {
					fndError.add("品名Slogan不存在!");
				}
			}

		}

		if (fndError.getCount() > 0) {
			throw fndError;
		}
	}

}
