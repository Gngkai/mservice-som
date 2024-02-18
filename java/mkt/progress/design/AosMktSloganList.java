package mkt.progress.design;

import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndHistory;
import common.sal.util.SalUtil;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.datamodel.ListSelectedRow;
import kd.bos.entity.datamodel.ListSelectedRowCollection;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.CloseCallBack;
import kd.bos.form.FormShowParameter;
import kd.bos.form.ShowType;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.list.IListView;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.MktComUtil;
import mkt.progress.ProgressUtil;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * @author aosom
 * @version 品名&Slogan维护表-列表插件
 */
public class AosMktSloganList extends AbstractListPlugin {
    private static final String AOS_BATCHGIVE = "aos_batchgive";
    private static final String AOS_SUBMIT = "aos_submit";
    private static final String FORM = "form";

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String itemKey = evt.getItemKey();
        try {
            if (AOS_BATCHGIVE.equals(itemKey)) {
                // 批量转办
                aosOpen();
            } else if (AOS_SUBMIT.equals(itemKey)) {
                // 提交
                aosSubmit();
            }
        } catch (FndError fndMessage) {
            this.getView().showTipNotification(fndMessage.getErrorMessage());
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
        }
    }

    private void aosSubmit() {
        {
            try {
                ListSelectedRowCollection selectedRows = this.getSelectedRows();
                int size = selectedRows.size();
                IListView view = (IListView)this.getView();
                String billFormId = view.getBillFormId();
                Iterator<ListSelectedRow> iterator = selectedRows.iterator();
                StringBuilder builder = new StringBuilder();
                StringJoiner str = new StringJoiner(";");
                while (iterator.hasNext()) {
                    ListSelectedRow next = iterator.next();
                    QFilter filterId = new QFilter("id", "=", next.getPrimaryKeyValue());
                    DynamicObject dy = QueryServiceHelper.queryOne(billFormId, "aos_status", new QFilter[] {filterId});
                    boolean reject = true;
                    if (dy != null) {
                        if ("翻译".equals(dy.getString("aos_status"))) {
                            reject = false;
                        }
                    }
                    if (reject) {
                        str.add(next.getBillNo());
                        iterator.remove();
                    }
                }
                if (str.toString().length() > 0) {
                    builder.append("  以下流程不在翻译节点，不能批量提交:");
                    builder.append(str);
                }
                if (size == 0) {
                    this.getView().showTipNotification("请先选择提交数据");
                } else {
                    String message = ProgressUtil.submitEntity(view, selectedRows);
                    if (message != null && message.length() > 0) {
                        message = "提交成功,部分无权限单据无法提交：  " + message;
                    } else {
                        message = "提交成功";
                    }
                    this.getView().updateView();
                    if (builder.toString().length() > 0) {
                        this.getView().showSuccessNotification(message + builder);
                    } else {
                        this.getView().showSuccessNotification(message);
                    }
                }
            } catch (FndError fndMessage) {
                this.getView().showTipNotification(fndMessage.getErrorMessage());
            } catch (Exception ex) {
                this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
            }
        }
    }

    /**
     * 弹出转办界面
     */
    private void aosOpen() {
        FormShowParameter showParameter = new FormShowParameter();
        showParameter.setFormId("aos_mkt_progive");
        showParameter.getOpenStyle().setShowType(ShowType.Modal);
        showParameter.setCloseCallBack(new CloseCallBack(this, "form"));
        this.getView().showForm(showParameter);
    }

    /** 回调事件 **/
    @Override
    public void closedCallBack(ClosedCallBackEvent closedCallBackEvent) {
        String actionId = closedCallBackEvent.getActionId();
        if (StringUtils.equals(actionId, FORM)) {
            Object map = closedCallBackEvent.getReturnData();
            if (map == null) {
                return;
            }
            @SuppressWarnings("unchecked")
            Object aosUser = ((Map<String, Object>)map).get("aos_user");
            aosBatchgive(aosUser);
        }
    }

    private void aosBatchgive(Object aosUser) {
        List<Object> list =
            getSelectedRows().stream().map(ListSelectedRow::getPrimaryKeyValue).distinct().collect(Collectors.toList());
        Object currentUserId = UserServiceHelper.getCurrentUserId();
        Object currentUserName = UserServiceHelper.getUserInfoByID((long)currentUserId).get("name");
        QFilter filter = new QFilter("aos_user.id", QCP.equals, currentUserId);
        // 判断是否有转办权限
        QFilter filter2 = new QFilter("aos_give", QCP.equals, true);
        boolean exists = QueryServiceHelper.exists("aos_mkt_userights", new QFilter[] {filter, filter2});
        for (Object o : list) {
            String id = o.toString();
            DynamicObject aosMktSlogan = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_slogan");
            String aosUserold = aosMktSlogan.getDynamicObject("aos_user").getPkValue().toString();
            String billno = aosMktSlogan.getString("billno");
            String aosStatus = aosMktSlogan.getString("aos_status");
            if (!(String.valueOf(currentUserId)).equals(aosUserold) && !exists) {
                this.getView().showTipNotification(billno + "只允许转办自己的单据!");
                return;
            }
            if (!"翻译,设计,海外翻译".contains(aosStatus)) {
                this.getView().showTipNotification(billno + "只允许转办翻译,设计,海外翻译的单据!");
                return;
            }
            aosMktSlogan.set("aos_user", aosUser);
            OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_slogan",
                new DynamicObject[] {aosMktSlogan}, OperateOption.create());
            MktComUtil.sendGlobalMessage(String.valueOf(((DynamicObject)aosUser).getPkValue()),
                String.valueOf(aosMktSlogan), String.valueOf(operationrst.getSuccessPkIds().get(0)), billno,
                currentUserName + "流程转办!");
            FndHistory fndHistory = new FndHistory();
            fndHistory.SetActionBy(currentUserId);
            fndHistory.SetFormId("aos_mkt_slogan");
            fndHistory.SetSourceId(id);
            // 操作动作
            fndHistory.SetActionCode("流程转办");
            // 操作备注
            fndHistory.SetDesc(currentUserName + "流程转办!");
            // 插入历史记录;
            Cux_Common_Utl.History(fndHistory);
        }
        this.getView().showSuccessNotification("转办成功");
        // 刷新列表
        this.getView().invokeOperation("refresh");
    }

}
