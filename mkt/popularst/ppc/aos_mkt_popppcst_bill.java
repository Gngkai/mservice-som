package mkt.popularst.ppc;

import java.util.EventObject;

import common.fnd.FndError;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.form.FormShowParameter;
import kd.bos.form.IPageCache;
import kd.bos.form.ShowType;

public class aos_mkt_popppcst_bill extends AbstractBillPlugIn {
	
	public void afterLoadData(EventObject e) {
		super.afterLoadData(e);
		try {
			initCommon();
		} catch (FndError fndError) {
			fndError.show(getView());
		} catch (Exception ex) {
			FndError.showex(getView(), ex);
		}
	}

	private void initCommon() {
		// 打开至界面容器中
		FormShowParameter parameter = new FormShowParameter();
		parameter.setFormId("aos_mkt_popst_form");
		parameter.getOpenStyle().setShowType(ShowType.InContainer);
		parameter.getOpenStyle().setTargetKey("aos_flexpanelap1"); // 设置父容器标识
		this.getView().showForm(parameter);
		// 在父界面缓存中设置子界面ID
		String sonPageId = parameter.getPageId();
		IPageCache iPageCache = this.getPageCache();
		iPageCache.put("sonPageId", sonPageId);
	}
	
}
