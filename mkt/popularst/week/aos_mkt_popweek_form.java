package mkt.popularst.week;

import java.util.EventObject;

import common.fnd.FndMsg;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.IFormView;
import kd.bos.form.control.Control;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.plugin.AbstractFormPlugin;

public class aos_mkt_popweek_form extends AbstractFormPlugin {
	public void registerListener(EventObject e) {
		super.registerListener(e);
		this.addClickListeners("btnok");
	}

	/**
	 * 按钮点击事件
	 */
	public void click(EventObject evt) {
		super.click(evt);
		Control control = (Control) evt.getSource();
		String key = control.getKey();
		if (key.equals("btnok")) {
			// 确认按钮 同步数据并关闭
			statusChange();
			this.getView().close();
		}

	}

	/**
	 * 批量改变状态
	 */
	private void statusChange() {
		FndMsg.debug("==========into statusChange==========");
		Object aos_status = this.getModel().getValue("aos_status");
		IFormView view = this.getView().getParentView();
		int currentRow = view.getModel().getEntryCurrentRowIndex("aos_entryentity");
		DynamicObjectCollection aosSubEntryS = view.getModel().getEntryEntity("aos_subentryentity");
		for (DynamicObject aosSubEntry : aosSubEntryS) {
			aosSubEntry.set("aos_status", aos_status);
		}
		view.updateView();
		EntryGrid aos_entryentity = view.getControl("aos_entryentity");
		aos_entryentity.selectRows(currentRow);
	}

}
