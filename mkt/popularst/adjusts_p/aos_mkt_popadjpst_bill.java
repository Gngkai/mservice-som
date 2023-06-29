package mkt.popularst.adjusts_p;

import java.util.EventObject;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.form.FormShowParameter;
import kd.bos.form.IPageCache;
import kd.bos.form.ShowType;
import kd.bos.form.container.Tab;
import kd.bos.form.events.BeforeClosedEvent;

public class aos_mkt_popadjpst_bill extends AbstractBillPlugIn {

	public void afterLoadData(EventObject e) {
		// 默认页签
		Tab tab = this.getControl("aos_tabap");
		tab.activeTab("aos_detail");
		// 打开通用界面
		Open_Common(this);
	}

	private void Open_Common(AbstractBillPlugIn Obj) {
		FormShowParameter parameter = new FormShowParameter();
		parameter.setFormId("aos_mkt_pop_adjfmst");
		parameter.getOpenStyle().setShowType(ShowType.InContainer);
		parameter.getOpenStyle().setTargetKey("aos_detail");
		Obj.getView().showForm(parameter);
		String son_page_id = parameter.getPageId();
		IPageCache iPageCache = Obj.getPageCache();
		iPageCache.put("son_page_id", son_page_id);
	}
	
	public void beforeClosed(BeforeClosedEvent e) {
		super.beforeClosed(e);
		e.setCheckDataChange(false);
	}
	
}