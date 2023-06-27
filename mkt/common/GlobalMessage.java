package mkt.common;

import kd.bos.bill.BillShowParameter;
import kd.bos.bill.OperationStatus;
import kd.bos.form.ShowType;
import kd.bos.notification.AbstractNotificationClick;
import kd.bos.notification.events.ButtonClickEventArgs;

import java.util.Map;

/**
 * @author create by gk
 * @date 2022/11/5 13:28
 * @action  全部消息提醒的弹框点击事件
 */
public class GlobalMessage extends AbstractNotificationClick {
    @Override
    public void buttonClick(ButtonClickEventArgs eventArgs) {
        String buttonKey = eventArgs.getButtonKey();
        if (buttonKey.equals("aos_detail")){
            Map<String, Object> params = this.getNotificationFormInfo().getNotification().getParams();
            if (params.containsKey("name")&& params.containsKey("id")){
                BillShowParameter showParameter = new BillShowParameter();
                showParameter.setFormId(params.get("name").toString());
                showParameter.setPkId(params.get("id"));
                showParameter.getOpenStyle().setShowType(ShowType.NewWindow);
                showParameter.setStatus(OperationStatus.VIEW);
                this.getFormView().showForm(showParameter);
            }
            else{
                this.getFormView().showMessage("单据不存在，无法打开界面。");
            }
        }
    }
}
