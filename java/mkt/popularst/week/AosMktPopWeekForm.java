package mkt.popularst.week;

import java.util.EventObject;
import common.fnd.FndMsg;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.IFormView;
import kd.bos.form.control.Control;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.plugin.AbstractFormPlugin;
/**
 * @author aosom
 * @version 销售周调整-动态表单插件
 */
public class AosMktPopWeekForm extends AbstractFormPlugin {
    private final static String BTNOK = "btnok";

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        this.addClickListeners("btnok");
    }

    /**
     * 按钮点击事件
     */
    @Override
    public void click(EventObject evt) {
        super.click(evt);
        Control control = (Control)evt.getSource();
        String key = control.getKey();
        if (BTNOK.equals(key)) {
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
        Object aosStatus = this.getModel().getValue("aos_status");
        IFormView view = this.getView().getParentView();
        int currentRow = view.getModel().getEntryCurrentRowIndex("aos_entryentity");
        DynamicObjectCollection aosSubEntryS = view.getModel().getEntryEntity("aos_subentryentity");
        for (DynamicObject aosSubEntry : aosSubEntryS) {
            aosSubEntry.set("aos_status", aosStatus);
        }
        view.updateView();
        EntryGrid aosEntryentity = view.getControl("aos_entryentity");
        aosEntryentity.selectRows(currentRow);
    }
}