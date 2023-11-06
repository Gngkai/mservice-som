package mkt.data.standard;

import common.fnd.FndGlobal;
import common.fnd.FndMsg;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.events.BeforeDoOperationEventArgs;
import kd.bos.form.operate.FormOperate;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;

import java.util.EventObject;

/**
 * 布景标准库
 */
public class AosMktViewBill  extends AbstractBillPlugIn implements ItemClickListener {
    @Override
    public void registerListener(EventObject e) {
        try {
            this.addItemClickListeners("tbmain");// 给工具栏加监听事件
        } catch (Exception ex) {
            this.getView().showErrorNotification("registerListener = " + ex);
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

    private void aos_manuopen(DynamicObject dy_main, String type) {
        {
            dy_main.set("aos_status", "待优化");
            SaveServiceHelper.saveOperate("aos_mkt_viewstd",
                    new DynamicObject[]{dy_main}, OperateOption.create());
            if (type.equals("A")) {
                this.getView().invokeOperation("refresh");
                this.getView().showSuccessNotification("手动开启成功");
            }
        }
    }
    private void aos_manuclose(DynamicObject dy_main, String type) {
        dy_main.set("aos_status", "手动关闭");
        SaveServiceHelper.saveOperate("aos_mkt_viewstd",
                new DynamicObject[]{dy_main}, OperateOption.create());
        if (type.equals("A")) {
            this.getView().invokeOperation("refresh");
            this.getView().showSuccessNotification("手动关闭成功");
        }
    }

    private void aos_audit(DynamicObject dy_main, String type) {
        dy_main.set("aos_status", "已完成");
        dy_main.set("aos_confirmor", UserServiceHelper.getCurrentUserId());
        SaveServiceHelper.saveOperate("aos_mkt_viewstd",
                new DynamicObject[]{dy_main}, OperateOption.create());
        if (type.equals("A")) {
            this.getView().invokeOperation("refresh");
            this.getView().showSuccessNotification("审核成功");
//            this.getView().showConfirm("是否调整摄影、摄像、布景标准库?", MessageBoxOptions.YesNo, new ConfirmCallBackListener("audit", this));
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
        SaveServiceHelper.saveOperate("aos_mkt_viewstd",
                new DynamicObject[]{dy_main}, OperateOption.create());
        if (type.equals("A")) {
            this.getView().invokeOperation("refresh");
            this.getView().showSuccessNotification("提交成功");
        }
    }

    public void beforeDoOperation(BeforeDoOperationEventArgs args) {
        super.beforeDoOperation(args);
        FormOperate formOperate = (FormOperate) args.getSource();
        String Operatation = formOperate.getOperateKey();
        if ("save".equals(Operatation)) {
            FndMsg.debug("=====save Operation=====");
            // 校验
            DynamicObject aos_mkt_photostd = QueryServiceHelper.queryOne("aos_mkt_viewstd", "id",
                    new QFilter("aos_category1_name", QCP.equals, this.getModel().getValue("aos_category1_name"))
                            .and("aos_category2_name", QCP.equals, this.getModel().getValue("aos_category2_name"))
                            .and("aos_category3_name", QCP.equals, this.getModel().getValue("aos_category3_name"))
                            .and("aos_itemnamecn", QCP.equals, this.getModel().getValue("aos_itemnamecn"))
                            .and("aos_detail", QCP.equals, this.getModel().getValue("aos_detail"))
                            .and("id", QCP.not_equals, this.getModel().getDataEntity().getPkValue().toString())
                            .toArray());
            if (FndGlobal.IsNotNull(aos_mkt_photostd)) {
                this.getView().showErrorNotification("已存在相同的产品类别和品名");
                args.setCancel(true);
            }
        }
    }
}