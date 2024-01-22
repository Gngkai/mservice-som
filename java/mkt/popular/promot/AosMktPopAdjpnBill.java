package mkt.popular.promot;

import java.util.EventObject;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.form.FormShowParameter;
import kd.bos.form.IPageCache;
import kd.bos.form.ShowType;
import kd.bos.form.container.Tab;
import kd.bos.form.events.BeforeClosedEvent;

/**
 * @author aosom
 * @version 出价调整推广-表单插件新
 */
public class AosMktPopAdjpnBill extends AbstractBillPlugIn {

    @Override
    public void afterLoadData(EventObject e) {
        // 默认页签
        Tab tab = this.getControl("aos_tabap");
        tab.activeTab("aos_detail");
        // 打开通用界面
		openCommon(this);
        DynamicObject ouCode = (DynamicObject)this.getModel().getValue("aos_orgid");
        this.getView().setFormTitle(new LocaleString("SP出价调整(推广)-" + (ouCode.get("name").toString().charAt(0))));
    }

    private void openCommon(AbstractBillPlugIn obj) {
        FormShowParameter parameter = new FormShowParameter();
        parameter.setFormId("aos_mkt_pop_adjpform");
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
