package mkt.popularst.week;

import java.util.EventObject;

import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.sal.util.SalUtil;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.form.container.Tab;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.RowClickEventListener;
import kd.bos.servicehelper.user.UserServiceHelper;

/**
 * @author aosom
 * @version 销售周调整-表单插件
 */
public class AosMktPopWeekBill extends AbstractBillPlugIn implements RowClickEventListener {
    private static final String AOS_VALID = "aos_valid";
    private static final String AOS_CONFIRM = "aos_confirm";
    private static final String AOS_BATCH = "aos_batch";
    private static final String B = "B";

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        // 给单据体加监听
        EntryGrid aosSubentryentity = this.getControl("aos_subentryentity");
        aosSubentryentity.addRowClickListener(this);
        // 给工具栏加监听事件
        this.addItemClickListeners("aos_toolbarap");
        // 提交
        this.addItemClickListeners("aos_confirm");
        this.addItemClickListeners("aos_batch");
    }

    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();
        if (AOS_VALID.equals(name)) {
            aosValid();
        }
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String control = evt.getItemKey();
        try {
            if (AOS_CONFIRM.equals(control)) {
                // 提交
                aosConfirm();
            } else if (AOS_BATCH.equals(control)) {
                // 批量修改状态
                aosBatch();
            }
        } catch (FndError fndError) {
            this.getView().showTipNotification(fndError.getErrorMessage());
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
        }
    }

    /**
     * 批量修改状态
     */
    private void aosBatch() {
        FndGlobal.OpenForm(this, "aos_mkt_week_show", null);
    }

    private void aosConfirm() {
        this.getModel().setValue("aos_statush", "B");
        this.getView().invokeOperation("save");
        this.getView().invokeOperation("refresh");
        statusControl();
    }

    private void statusControl() {
        Object aosStatus = this.getModel().getValue("aos_statush");
        if (B.equals(aosStatus)) {
            this.getView().setEnable(false, "contentpanelflex");
            this.getView().setEnable(false, "aos_confirm");
            this.getView().setEnable(false, "bar_save");
        }
        long user = UserServiceHelper.getCurrentUserId();
        DynamicObject aosMakeby = (DynamicObject)this.getModel().getValue("aos_makeby");
        long makebyId = (Long)aosMakeby.getPkValue();
        // 如果当前用户不为录入人则全锁定
        if (user != makebyId) {
            this.getView().setEnable(false, "contentpanelflex");
            this.getView().setEnable(false, "aos_confirm");
            this.getView().setEnable(false, "bar_save");
        } else {
            this.getView().setEnable(true, "contentpanelflex");
            this.getView().setEnable(true, "aos_confirm");
            this.getView().setEnable(true, "bar_save");
        }
    }

    private void aosValid() {
        int currentRowIndex = this.getModel().getEntryCurrentRowIndex("aos_subentryentity");
        boolean aosValid = (boolean)this.getModel().getValue("aos_valid", currentRowIndex);
        if (aosValid) {
            this.getModel().setValue("aos_status", "paused", currentRowIndex);
        } else {
            this.getModel().setValue("aos_status", "enabled", currentRowIndex);
        }
    }

    @Override
    public void afterLoadData(EventObject e) {
        // 默认选中详细信息页签
        Tab tab = this.getControl("aos_tabap");
        tab.activeTab("aos_tabpageap1");
        statusControl();
    }

}