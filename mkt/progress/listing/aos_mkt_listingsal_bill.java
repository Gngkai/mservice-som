package mkt.progress.listing;

import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;

import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndHistory;
import common.sal.util.SalUtil;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.form.ClientProperties;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.events.BeforeClosedEvent;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.MKTCom;
import mkt.progress.ProgressUtil;

public class aos_mkt_listingsal_bill extends AbstractBillPlugIn implements ItemClickListener {

	/** 系统管理员 **/
	public final static String system = Cux_Common_Utl.SYSTEM;


	@Override
	public void registerListener(EventObject e) {
		super.registerListener(e);
		this.addItemClickListeners("aos_toolbarap");// 给工具栏加监听事件
		this.addItemClickListeners("aos_submit"); // 提交
		this.addItemClickListeners("aos_open"); // 打开来源流程
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String Control = evt.getItemKey();
		try {
			if ("aos_submit".equals(Control))
				aos_submit();
			else if ("aos_open".equals(Control))
				aos_open();
		} catch (FndError fndMessage) {
			this.getView().showTipNotification(fndMessage.getErrorMessage());
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
		}
	}

	private void aos_open() {
		Object aos_sourceid = this.getModel().getValue("aos_sourceid");
		Object aos_sourcetype = this.getModel().getValue("aos_sourcetype");
		if ("设计需求表".equals(aos_sourcetype))
			Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_designreq", aos_sourceid);
		else if ("Listing优化需求表子表".equals(aos_sourcetype))
			Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_listing_son", aos_sourceid);
		else if ("Listing优化需求表小语种".equals(aos_sourcetype))
			Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_listing_min", aos_sourceid);
		else if ("设计完成表".equals(aos_sourcetype))
			Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_designcmp", aos_sourceid);
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

	/** 提交 **/
	private void aos_submit() throws FndError {
		SaveControl();// 先做数据校验判断是否可以提交
		String aos_status = this.getModel().getValue("aos_status").toString();// 根据状态判断当前流程节点
		switch (aos_status) {
		case "申请人":
			SubmitReq();
			break;
		case "销售确认":
			SubmitForSal();
			break;
		}
		FndHistory.Create(this.getView(),"提交",aos_status);
		StatusControl();// 提交完成后做新的界面状态控制
	}

	/** 值校验 **/
	private void SaveControl() throws FndError {
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
		// 图片控制
		// InitPic();

		// 锁住需要控制的字段

		// 当前节点操作人不为当前用户 全锁
		if (!AosUser.getPkValue().toString().equals(CurrentUserId.toString())
				&& !"程震杰".equals(CurrentUserName.toString())
				&& !"刘中怀".equals(CurrentUserName.toString())
				&& !"陈聪".equals(CurrentUserName.toString())) {
			this.getView().setEnable(false, "titlepanel");// 标题面板
			this.getView().setEnable(false, "aos_contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "bar_save");
			this.getView().setVisible(false, "aos_submit");
		}
		// 状态控制
		if ("销售确认".equals(AosStatus)) {
			Map<String, Object> map = new HashMap<>();
			map.put(ClientProperties.Text, new LocaleString("销售确认"));
			this.getView().updateControlMetadata("aos_submit", map);
			this.getView().setVisible(true, "aos_submit");
			this.getView().setEnable(false, "aos_contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "bar_save");
		} else if ("结束".equals(AosStatus)) {
			this.getView().setVisible(false, "aos_submit");
			this.getView().setEnable(false, "aos_contentpanelflex");// 主界面面板
			this.getView().setVisible(false, "bar_save");
		}
	}

	/** 申请人状态下提交 **/
	private void SubmitReq() {
		// 异常参数
		int ErrorCount = 0;
		String ErrorMessage = "";
		// 数据层
		Object aos_designer = this.getModel().getValue("aos_designer");
		Object FId = this.getModel().getDataEntity().getPkValue(); // 当前界面主键
		Object AosBillno = this.getModel().getValue("billno");
		Object aos_itemid = ((DynamicObject)this.getModel().getValue("aos_itemid", 0)).getPkValue();
		Object aos_orgid = ((DynamicObject) this.getModel().getValue("aos_orgid")).getPkValue();
		Object aos_sale = ProgressUtil.findUserByOrgCate(aos_orgid,aos_itemid,"aos_salehelper");
		if (aos_designer == null) {
			ErrorCount++;
			ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "设计为空,流程无法流转!");
		}
		if (ErrorCount > 0) {
			FndError fndMessage = new FndError(ErrorMessage);
			throw fndMessage;
		}

		// 执行保存操作
		this.getModel().setValue("aos_status", "销售确认");// 设置单据流程状态
		this.getModel().setValue("aos_user", aos_sale);// 设置操作人为销售
		this.getView().invokeOperation("save");
		this.getView().invokeOperation("refresh");

		String MessageId = aos_sale + "";
		String Message = "设计需求表-Listing优化需求自动创建";
		MKTCom.SendGlobalMessage(MessageId, "aos_mkt_listing_sal", FId + "", AosBillno + "", Message);
	}

	/** 销售确认状态下提交 **/
	private void SubmitForSal() {
		// 异常参数
		// 数据层

		// 执行保存操作
		this.getModel().setValue("aos_status", "结束");// 设置单据流程状态
		this.getModel().setValue("aos_user", system);// 设置操作人为系统管理员
		this.getView().invokeOperation("save");
		this.getView().invokeOperation("refresh");

	}

}
