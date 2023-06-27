package mkt.popularst.week;

import java.util.EventObject;

import common.fnd.FndError;
import common.sal.util.SalUtil;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.form.container.Tab;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.RowClickEventListener;
import kd.bos.servicehelper.user.UserServiceHelper;

public class aos_mkt_popweek_bill extends AbstractBillPlugIn implements RowClickEventListener {

	public void registerListener(EventObject e) {
		super.registerListener(e);
		// 给单据体加监听
		EntryGrid aos_subentryentity = this.getControl("aos_subentryentity");
		aos_subentryentity.addRowClickListener(this);
		this.addItemClickListeners("aos_toolbarap");// 给工具栏加监听事件
		this.addItemClickListeners("aos_confirm"); // 提交
	}

	public void propertyChanged(PropertyChangedArgs e) {
		String name = e.getProperty().getName();
		if (name.equals("aos_valid"))
			aos_valid();
	}
	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String Control = evt.getItemKey();
		try {
			if ("aos_confirm".equals(Control))
				aos_confirm();// 提交
		} catch (FndError FndError) {
			this.getView().showTipNotification(FndError.getErrorMessage());
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
		}
	}
	
	private void aos_confirm() {
		this.getModel().setValue("aos_statush", "B");
		this.getView().invokeOperation("save");
		this.getView().invokeOperation("refresh");
		
		StatusControl();
	}
	
	private void StatusControl() {
		Object aos_status = this.getModel().getValue("aos_statush");
		if ("B".equals(aos_status)) {
			this.getView().setEnable(false, "contentpanelflex");
			this.getView().setEnable(false, "aos_confirm");
			this.getView().setEnable(false, "bar_save");
		}
		
		long User = UserServiceHelper.getCurrentUserId();// 操作人员
		DynamicObject aos_makeby = (DynamicObject) this.getModel().getValue("aos_makeby");
		long makeby_id = (Long) aos_makeby.getPkValue();
		// 如果当前用户不为录入人则全锁定
		if (User != makeby_id) {
			this.getView().setEnable(false, "contentpanelflex");
			this.getView().setEnable(false, "aos_confirm");
			this.getView().setEnable(false, "bar_save");
		} else {
			this.getView().setEnable(true, "contentpanelflex");
			this.getView().setEnable(true, "aos_confirm");
			this.getView().setEnable(true, "bar_save");
		}
		
	}

	private void aos_valid() {
		System.out.println("=== aos_valid ===");
		int CurrentRowIndex = this.getModel().getEntryCurrentRowIndex("aos_subentryentity");
		boolean aos_valid = (boolean) this.getModel().getValue("aos_valid", CurrentRowIndex);
		System.out.println("CurrentRowIndex ="+CurrentRowIndex);
		System.out.println("aos_valid ="+aos_valid);
		if (aos_valid)
			this.getModel().setValue("aos_status", "paused", CurrentRowIndex);
		else
			this.getModel().setValue("aos_status", "enabled", CurrentRowIndex);
	}

	public void afterLoadData(EventObject e) {
		// 默认选中详细信息页签
		Tab tab = this.getControl("aos_tabap");
		tab.activeTab("aos_tabpageap1");
		StatusControl();
	}

}