package mkt.popularst.add_s;

import java.util.EventObject;

import common.fnd.FndError;
import common.sal.util.SalUtil;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.form.container.Tab;
import kd.bos.form.control.Control;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.Image;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.RowClickEvent;
import kd.bos.form.control.events.RowClickEventListener;
import kd.bos.servicehelper.user.UserServiceHelper;

public class aos_mkt_popadds_bill extends AbstractBillPlugIn implements RowClickEventListener {

	public void registerListener(EventObject e) {
		super.registerListener(e);
		// 给单据体加监听
		EntryGrid aos_entryentity = this.getControl("aos_entryentity");
		aos_entryentity.addRowClickListener(this);
		this.addItemClickListeners("aos_toolbarap");// 给工具栏加监听事件
		this.addItemClickListeners("aos_confirm"); // 提交
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
		this.getModel().setValue("aos_status", "B");
		this.getView().invokeOperation("save");
		this.getView().invokeOperation("refresh");
		
		StatusControl();
	}

	public void entryRowClick(RowClickEvent evt) {
		Control source = (Control) evt.getSource();
		if (source.getKey().equals("aos_entryentity")) {
			int CurrentRowIndex = this.getModel().getEntryCurrentRowIndex("aos_entryentity");
			ItemRowClick(CurrentRowIndex);
		}
	}

	private void ItemRowClick(int currentRowIndex) {
		DynamicObject aos_item = (DynamicObject) this.getModel().getValue("aos_itemid", currentRowIndex);
		// ======点击物料设置图片====== //
		String item_number = aos_item.getString("number");
		String url = "https://clss.s3.amazonaws.com/" + item_number + ".jpg";
		Image image = this.getControl("aos_image");
		image.setUrl(url);
	}

	public void afterLoadData(EventObject e) {
		// 默认选中详细信息页签
		Tab tab = this.getControl("aos_tabap");
		tab.activeTab("aos_tabpageap1");
		// 默认选中第一行
		Object aos_itemid = this.getModel().getValue("aos_itemid", 0);
		if (aos_itemid == null)
			return;
		DynamicObject aos_item = (DynamicObject) this.getModel().getValue("aos_itemid", 0);
		String item_number = aos_item.getString("number");
		String url = "https://clss.s3.amazonaws.com/" + item_number + ".jpg";
		Image image = this.getControl("aos_image");
		image.setUrl(url);
		StatusControl();
	}

	private void StatusControl() {
		Object aos_status = this.getModel().getValue("aos_status");
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

}
