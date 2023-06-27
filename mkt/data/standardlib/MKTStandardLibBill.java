package mkt.data.standardlib;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.ConfirmCallBackListener;
import kd.bos.form.MessageBoxOptions;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.events.MessageBoxClosedEvent;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;


/**
 * 营销标准库界面插件
 */
public class MKTStandardLibBill extends AbstractBillPlugIn {


    @Override
    public void afterDoOperation(AfterDoOperationEventArgs args) {
        String operateKey = args.getOperateKey();
        if ("aos_manuclose".equals(operateKey) || "aos_manuopen".equals(operateKey)) {
            OperationResult operationResult = args.getOperationResult();
            operationResult.setShowMessage(false);
            this.getView().showTipNotification(operationResult.getMessage());
        }

        // 审核通过 确认是否调整摄影，摄像，布景等标准库
        if("audit".equals(operateKey)) {
            this.getView().showConfirm("是否调整摄影、摄像、布景标准库?", MessageBoxOptions.YesNo, new ConfirmCallBackListener("audit", this));
        }
    }

    @Override
    public void confirmCallBack(MessageBoxClosedEvent messageBoxClosedEvent) {
        // 回调标识
        String callBackId = messageBoxClosedEvent.getCallBackId();
        // 确认结果
        String resultValue = messageBoxClosedEvent.getResultValue();
        // 过滤条件
        String aos_category1_name = this.getModel().getValue("aos_category1_name").toString();
        String aos_category2_name = this.getModel().getValue("aos_category2_name").toString();
        String aos_category3_name = this.getModel().getValue("aos_category3_name").toString();
        String aos_itemnamecn = this.getModel().getValue("aos_itemnamecn").toString();
        QFilter[] qFilters = new QFilter[]{
                new QFilter("aos_category1_name", QCP.equals, aos_category1_name),
                new QFilter("aos_category2_name", QCP.equals, aos_category2_name),
                new QFilter("aos_category3_name", QCP.equals, aos_category3_name),
                new QFilter("aos_itemnamecn", QCP.equals, aos_itemnamecn),
        };

        if ("Yes".equals(resultValue) && "audit".equals(callBackId)) {
            // 如果为是 更新相同品类的摄影 摄像 布景标准库 的进度为待优化
            DynamicObject photoObj = BusinessDataServiceHelper.loadSingle("aos_mkt_photostd", "billstatus", qFilters);
            DynamicObject videoObj = BusinessDataServiceHelper.loadSingle("aos_mkt_videostd", "billstatus", qFilters);
            DynamicObject viewObj = BusinessDataServiceHelper.loadSingle("aos_mkt_viewstd", "billstatus", qFilters);
            if (photoObj != null) {
                photoObj.set("billstatus", "D");
                SaveServiceHelper.save(new DynamicObject[]{photoObj});
            }
            if (videoObj != null) {
                videoObj.set("billstatus", "D");
                SaveServiceHelper.save(new DynamicObject[]{videoObj});
            }
            if (viewObj != null) {
                viewObj.set("billstatus", "D");
                SaveServiceHelper.save(new DynamicObject[]{viewObj});
            }
        }
    }
}
