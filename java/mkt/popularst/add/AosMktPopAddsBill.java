package mkt.popularst.add;

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

/**
 * @author aosom
 * @version ST销售加回-表单插件
 */
public class AosMktPopAddsBill extends AbstractBillPlugIn implements RowClickEventListener {
    private static final String AOS_CONFIRM = "aos_confirm";
    private static final String AOS_ENTRYENTITY = "aos_entryentity";
    private static final String B = "B";

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        // 给单据体加监听
        EntryGrid aosEntryentity = this.getControl("aos_entryentity");
        aosEntryentity.addRowClickListener(this);
        // 给工具栏加监听事件
        this.addItemClickListeners("aos_toolbarap");
        // 提交
        this.addItemClickListeners("aos_confirm");
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String control = evt.getItemKey();
        try {
            if (AOS_CONFIRM.equals(control)) {
                // 提交
                aosConfirm();
            }
        } catch (FndError fndError) {
            this.getView().showTipNotification(fndError.getErrorMessage());
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
        }
    }

    private void aosConfirm() {
        this.getModel().setValue("aos_status", "B");
        this.getView().invokeOperation("save");
        this.getView().invokeOperation("refresh");
        statusControl();
    }

    @Override
    public void entryRowClick(RowClickEvent evt) {
        Control source = (Control)evt.getSource();
        if (AOS_ENTRYENTITY.equals(source.getKey())) {
            int currentRowIndex = this.getModel().getEntryCurrentRowIndex("aos_entryentity");
            itemRowClick(currentRowIndex);
        }
    }

    private void itemRowClick(int currentRowIndex) {
        DynamicObject aosItem = (DynamicObject)this.getModel().getValue("aos_itemid", currentRowIndex);
        // ======点击物料设置图片====== //
        String itemNumber = aosItem.getString("number");
        String url = "https://clss.s3.amazonaws.com/" + itemNumber + ".jpg";
        Image image = this.getControl("aos_image");
        image.setUrl(url);
    }

    @Override
    public void afterLoadData(EventObject e) {
        // 默认选中详细信息页签
        Tab tab = this.getControl("aos_tabap");
        tab.activeTab("aos_tabpageap1");
        // 默认选中第一行
        Object aosItemid = this.getModel().getValue("aos_itemid", 0);
        if (aosItemid == null) {
            return;
        }
        DynamicObject aosItem = (DynamicObject)this.getModel().getValue("aos_itemid", 0);
        String itemNumber = aosItem.getString("number");
        String url = "https://clss.s3.amazonaws.com/" + itemNumber + ".jpg";
        Image image = this.getControl("aos_image");
        image.setUrl(url);
        statusControl();
    }

    private void statusControl() {
        Object aosStatus = this.getModel().getValue("aos_status");
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
}
