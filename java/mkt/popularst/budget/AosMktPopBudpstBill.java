package mkt.popularst.budget;

import java.util.EventObject;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.form.FormShowParameter;
import kd.bos.form.IPageCache;
import kd.bos.form.ShowType;
import kd.bos.form.container.Tab;
import kd.bos.form.events.BeforeClosedEvent;

/**
 * @author aosom
 * @version ST预算调整-表单插件
 */
public class AosMktPopBudpstBill extends AbstractBillPlugIn {
    @Override
    public void afterLoadData(EventObject e) {
        // 默认页签
        Tab tab = this.getControl("aos_tabap");
        tab.activeTab("aos_detail");
        // 打开通用界面
		openCommon(this);
    }

    private void openCommon(AbstractBillPlugIn obj) {
        FormShowParameter parameter = new FormShowParameter();
        parameter.setFormId("aos_mkt_pop_budgetfmst");
        parameter.getOpenStyle().setShowType(ShowType.InContainer);
        parameter.getOpenStyle().setTargetKey("aos_detail");
		obj.getView().showForm(parameter);
        String sonPageId = parameter.getPageId();
        IPageCache iPageCache = obj.getPageCache();
        iPageCache.put("son_page_id", sonPageId);
    }

    @Override
	public void beforeClosed(BeforeClosedEvent e) {
        super.beforeClosed(e);
        e.setCheckDataChange(false);
    }

}