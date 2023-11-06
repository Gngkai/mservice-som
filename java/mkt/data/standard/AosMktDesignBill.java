package mkt.data.standard;

import common.fnd.FndGlobal;
import common.fnd.FndMsg;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.form.ConfirmCallBackListener;
import kd.bos.form.MessageBoxOptions;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.events.BeforeDoOperationEventArgs;
import kd.bos.form.events.MessageBoxClosedEvent;
import kd.bos.form.field.BasedataEdit;
import kd.bos.form.operate.FormOperate;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;

import java.util.EventObject;

public class AosMktDesignBill extends AbstractBillPlugIn implements ItemClickListener {
    @Override
    public void registerListener(EventObject e) {
        try {
            this.addItemClickListeners("tbmain");// 给工具栏加监听事件
        } catch (Exception ex) {
            this.getView().showErrorNotification("registerListener = " + ex);
        }
    }

    private void aos_manuopen(DynamicObject dy_main, String type) {
        {
            dy_main.set("aos_status", "待优化");
            SaveServiceHelper.saveOperate("aos_mkt_designstd",
                    new DynamicObject[]{dy_main}, OperateOption.create());
            if (type.equals("A")) {
                this.getView().invokeOperation("refresh");
                this.getView().showSuccessNotification("手动开启成功");
            }
        }
    }

    /**
     * 手工关闭
     *
     * @param dy_main
     * @param type
     */
    private void aos_manuclose(DynamicObject dy_main, String type) {
        dy_main.set("aos_status", "手动关闭");
        SaveServiceHelper.saveOperate("aos_mkt_designstd",
                new DynamicObject[]{dy_main}, OperateOption.create());
        if (type.equals("A")) {
            this.getView().invokeOperation("refresh");
            this.getView().showSuccessNotification("手动关闭成功");
        }
    }

    private void aos_audit(DynamicObject dy_main, String type) {
        dy_main.set("aos_status", "已完成");
        dy_main.set("aos_confirmor", UserServiceHelper.getCurrentUserId());
        SaveServiceHelper.saveOperate("aos_mkt_designstd",
                new DynamicObject[]{dy_main}, OperateOption.create());
        if (type.equals("A")) {
            this.getView().invokeOperation("refresh");
            this.getView().showSuccessNotification("审核成功");
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
                photoObj.set("aos_status", "待优化");
                SaveServiceHelper.save(new DynamicObject[]{photoObj});
            }
            if (videoObj != null) {
                videoObj.set("billstatus", "D");
                videoObj.set("aos_status", "待优化");
                SaveServiceHelper.save(new DynamicObject[]{videoObj});
            }
            if (viewObj != null) {
                viewObj.set("billstatus", "D");
                viewObj.set("aos_status", "待优化");
                SaveServiceHelper.save(new DynamicObject[]{viewObj});
            }
        }
    }

    /**
     * 提交按钮
     *
     * @param dy_main
     * @param type
     */
    private void aos_submit(DynamicObject dy_main, String type) {
        dy_main.set("aos_status", "待确认");
        SaveServiceHelper.saveOperate("aos_mkt_designstd",
                new DynamicObject[]{dy_main}, OperateOption.create());
        if (type.equals("A")) {
            this.getView().invokeOperation("refresh");
            this.getView().showSuccessNotification("提交成功");
        }
    }

    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String Control = evt.getItemKey();
        if ("aos_submit".equals(Control)) {
            DynamicObject dy_main = this.getModel().getDataEntity(true);
            aos_submit(dy_main, "A");// 提交
        } else if ("aos_audit".equals(Control)) {
            DynamicObject dy_main = this.getModel().getDataEntity(true);
            aos_audit(dy_main, "A");// 审核
        } else if ("aos_manuclose".equals(Control)) {
            DynamicObject dy_main = this.getModel().getDataEntity(true);
            aos_manuclose(dy_main, "A");// 手工关闭
        } else if ("aos_manuopen".equals(Control)) {
            DynamicObject dy_main = this.getModel().getDataEntity(true);
            aos_manuopen(dy_main, "A");// 手工开启
        }
    }

    public void beforeDoOperation(BeforeDoOperationEventArgs args) {
        super.beforeDoOperation(args);
        FormOperate formOperate = (FormOperate) args.getSource();
        String Operatation = formOperate.getOperateKey();
        if ("save".equals(Operatation)) {
            FndMsg.debug("=====save Operation=====");
            // 校验
            DynamicObject aosMktStandard = QueryServiceHelper.queryOne("aos_mkt_designstd", "id",
                    new QFilter("aos_category1_name", QCP.equals, this.getModel().getValue("aos_category1_name"))
                            .and("aos_category2_name", QCP.equals, this.getModel().getValue("aos_category2_name"))
                            .and("aos_category3_name", QCP.equals, this.getModel().getValue("aos_category3_name"))
                            .and("aos_itemnamecn", QCP.equals, this.getModel().getValue("aos_itemnamecn"))
                            .and("aos_detail", QCP.equals, this.getModel().getValue("aos_detail"))
                            .and("id", QCP.not_equals, this.getModel().getDataEntity().getPkValue().toString())
                            .toArray());
            if (FndGlobal.IsNotNull(aosMktStandard)) {
                this.getView().showErrorNotification("已存在相同的产品类别和品名");
                args.setCancel(true);
            }
        }
    }
}
