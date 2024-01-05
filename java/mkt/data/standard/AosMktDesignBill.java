package mkt.data.standard;

import common.fnd.FndGlobal;
import common.fnd.FndMsg;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.form.ConfirmCallBackListener;
import kd.bos.form.MessageBoxOptions;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.events.BeforeDoOperationEventArgs;
import kd.bos.form.events.MessageBoxClosedEvent;
import kd.bos.form.operate.FormOperate;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.otel.MmsOtelUtils;

import java.util.EventObject;

/**
 * @author aosom
 */
public class AosMktDesignBill extends AbstractBillPlugIn implements ItemClickListener {
    private static final Tracer TRACER = MmsOtelUtils.getTracer(AosMktDesignBill.class, RequestContext.get());

    @Override
    public void registerListener(EventObject e) {
        try {
            this.addItemClickListeners("tbmain");// 给工具栏加监听事件
        } catch (Exception ex) {
            this.getView().showErrorNotification("registerListener = " + ex);
        }
    }

    private void aosManuopen(DynamicObject dyMain) {
        {
            dyMain.set("aos_status", "待优化");
            SaveServiceHelper.saveOperate("aos_mkt_designstd", new DynamicObject[] {dyMain}, OperateOption.create());
            this.getView().invokeOperation("refresh");
            this.getView().showSuccessNotification("手动开启成功");
        }
    }

    /**
     * 手工关闭
     *
     */
    private void aosManuclose(DynamicObject dyMain) {
        dyMain.set("aos_status", "手动关闭");
        SaveServiceHelper.saveOperate("aos_mkt_designstd", new DynamicObject[] {dyMain}, OperateOption.create());
        this.getView().invokeOperation("refresh");
        this.getView().showSuccessNotification("手动关闭成功");
    }

    private void aosAudit(DynamicObject dyMain) {
        dyMain.set("aos_status", "已完成");
        dyMain.set("aos_confirmor", UserServiceHelper.getCurrentUserId());
        SaveServiceHelper.saveOperate("aos_mkt_designstd", new DynamicObject[] {dyMain}, OperateOption.create());
        this.getView().invokeOperation("refresh");
        this.getView().showSuccessNotification("审核成功");
        this.getView().showConfirm("是否调整摄影、摄像、布景标准库?", MessageBoxOptions.YesNo,
            new ConfirmCallBackListener("audit", this));

    }

    @Override
    public void confirmCallBack(MessageBoxClosedEvent messageBoxClosedEvent) {
        FndMsg.debug("=====confirmCallBack=====");
        // 回调标识
        String callBackId = messageBoxClosedEvent.getCallBackId();
        // 确认结果
        String resultValue = messageBoxClosedEvent.getResultValue();
        // 过滤条件
        String aosCategory1Name = this.getModel().getValue("aos_category1_name").toString();
        String aosCategory2Name = this.getModel().getValue("aos_category2_name").toString();
        String aosCategory3Name = this.getModel().getValue("aos_category3_name").toString();
        String aosItemnamecn = this.getModel().getValue("aos_itemnamecn").toString();
        String aosDetail = this.getModel().getValue("aos_detail").toString();

        QFilter[] qFilters = new QFilter[] {new QFilter("aos_category1_name", QCP.equals, aosCategory1Name),
            new QFilter("aos_category2_name", QCP.equals, aosCategory2Name),
            new QFilter("aos_category3_name", QCP.equals, aosCategory3Name),
            new QFilter("aos_itemnamecn", QCP.equals, aosItemnamecn),
            new QFilter("aos_detail", QCP.equals, aosDetail),};

        if ("Yes".equals(resultValue) && "audit".equals(callBackId)) {
            // 如果为是 更新相同品类的摄影 摄像 布景标准库 的进度为待优化
            DynamicObject photoObj =
                BusinessDataServiceHelper.loadSingle("aos_mkt_photostd", "billstatus,aos_status", qFilters);
            DynamicObject videoObj =
                BusinessDataServiceHelper.loadSingle("aos_mkt_videostd", "billstatus,aos_status", qFilters);
            DynamicObject viewObj =
                BusinessDataServiceHelper.loadSingle("aos_mkt_viewstd", "billstatus,aos_status", qFilters);
            if (photoObj != null) {
                FndMsg.debug("更新拍照需求表");
                photoObj.set("billstatus", "D");
                photoObj.set("aos_status", "待优化");
                SaveServiceHelper.save(new DynamicObject[] {photoObj});
            }
            if (videoObj != null) {
                FndMsg.debug("更新摄像需求表");
                videoObj.set("billstatus", "D");
                videoObj.set("aos_status", "待优化");
                SaveServiceHelper.save(new DynamicObject[] {videoObj});
            }
            if (viewObj != null) {
                FndMsg.debug("更新布景需求表");
                viewObj.set("billstatus", "D");
                viewObj.set("aos_status", "待优化");
                SaveServiceHelper.save(new DynamicObject[] {viewObj});
            }
        }
    }

    /**
     * 提交按钮
     *
     */
    private void aosSubmit(DynamicObject dyMain) {
        dyMain.set("aos_status", "待确认");
        SaveServiceHelper.saveOperate("aos_mkt_designstd", new DynamicObject[] {dyMain}, OperateOption.create());
        this.getView().invokeOperation("refresh");
        this.getView().showSuccessNotification("提交成功");

    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        Span span = MmsOtelUtils.getCusMainSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignored = span.makeCurrent()) {
            super.itemClick(evt);
            String control = evt.getItemKey();
            DynamicObject dyMain = this.getModel().getDataEntity(true);
            if (sign.submit.name.equals(control)) {
                aosSubmit(dyMain);
            } else if (sign.audit.name.equals(control)) {
                aosAudit(dyMain);
            } else if (sign.manuclose.name.equals(control)) {
                aosManuclose(dyMain);
            } else if (sign.manuopen.name.equals(control)) {
                aosManuopen(dyMain);
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    @Override
    public void beforeDoOperation(BeforeDoOperationEventArgs args) {
        super.beforeDoOperation(args);
        FormOperate formOperate = (FormOperate)args.getSource();
        String operatation = formOperate.getOperateKey();
        if (sign.save.name.equals(operatation)) {
            FndMsg.debug("=====save Operation=====");
            // 校验
            DynamicObject aosMktStandard = QueryServiceHelper.queryOne("aos_mkt_designstd", "id",
                new QFilter("aos_category1_name", QCP.equals, this.getModel().getValue("aos_category1_name"))
                    .and("aos_category2_name", QCP.equals, this.getModel().getValue("aos_category2_name"))
                    .and("aos_category3_name", QCP.equals, this.getModel().getValue("aos_category3_name"))
                    .and("aos_itemnamecn", QCP.equals, this.getModel().getValue("aos_itemnamecn"))
                    .and("aos_detail", QCP.equals, this.getModel().getValue("aos_detail"))
                    .and("id", QCP.not_equals, this.getModel().getDataEntity().getPkValue().toString()).toArray());
            if (FndGlobal.IsNotNull(aosMktStandard)) {
                this.getView().showErrorNotification("已存在相同的产品类别和品名");
                args.setCancel(true);
            }
        }
    }

    /**
     * 按钮枚举类
     */
    public enum sign {
        /**
         * 保存操作
         */
        save("save"),
        /**
         * 提交按钮
         */
        submit("aos_submit"),

        /**
         * 审核按钮
         */
        audit("aos_audit"),
        /**
         * 手工关闭
         */
        manuclose("aos_manuclose"),
        /**
         * 手工打开
         */
        manuopen("aos_manuopen");

        /**
         * 名称
         */
        private final String name;

        /**
         * 构造方法
         * 
         * @param name 名称
         */
        sign(String name) {
            this.name = name;
        }
    }
}
