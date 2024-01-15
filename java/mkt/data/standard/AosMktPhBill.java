package mkt.data.standard;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.data.com.AosMktDataComAudit;

import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;

/**
 * @author aosom
 */
public class AosMktPhBill extends AbstractBillPlugIn {
    @Override
    public void registerListener(EventObject e) {
        try {
            this.addItemClickListeners("tbmain");
        } catch (Exception ex) {
            this.getView().showErrorNotification("registerListener = " + ex);
        }
    }

    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String control = evt.getItemKey();
        if ("aos_audit".equals(control)) {
            aos_audit(this.getModel().getDataEntity(true), "A");// 审核
        }
    }

    private void aos_audit(DynamicObject dy_main, String type) {
        dy_main.set("aos_status", "已完成");
        dy_main.set("aos_confirmor", UserServiceHelper.getCurrentUserId());
        SaveServiceHelper.saveOperate("aos_mkt_videostd",
                new DynamicObject[]{dy_main}, OperateOption.create());
        if (type.equals("A")) {
            this.getView().invokeOperation("refresh");
            Map<String, Object> params = new HashMap<>(16);
            params.put("bill_type", "aos_mkt_videostd");
            params.put("id", dy_main.getPkValue().toString());
            AosMktDataComAudit.audit(this, params);
        }
    }
}
