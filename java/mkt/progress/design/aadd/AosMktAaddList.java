package mkt.progress.design.aadd;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndHistory;
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
import kd.bos.form.events.SetFilterEvent;
import kd.bos.list.IListView;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.MKTCom;
import mkt.progress.ProgressUtil;
import mkt.progress.iface.ParaInfoUtil;

/**
 * @author aosom
 * @version 高级A+需求单-列表插件
 */
public class AosMktAaddList extends AbstractListPlugin {
    public final static String AOS_MKT_LISTING_MIN = "aos_mkt_listing_min";
    public final static String AOS_SHOWCLOSE = "aos_showclose";
    public final static String AOS_SUBMIT = "aos_submit";
    public final static String AOS_BATCHGIVE = "aos_batchgive";
    public final static String GIVE = "give";

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String itemKey = evt.getItemKey();
        try {
            if (AOS_SHOWCLOSE.equals(itemKey)) {
                // 查询关闭流程
                ParaInfoUtil.showClose(this.getView());
            } else if (AOS_SUBMIT.equals(itemKey)) {
                // 提交
                aosSubmit();
            } else if (AOS_BATCHGIVE.equals(itemKey)) {
                // 批量转办
                aosOpen();
            }
        } catch (FndError error) {
            this.getView().showMessage(error.getErrorMessage());
        } catch (Exception e) {
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            this.getView().showMessage(writer.toString());
            e.printStackTrace();
        }
    }

    private void aosOpen() {
        FormShowParameter showParameter = new FormShowParameter();
        showParameter.setFormId("aos_mkt_progive");
        showParameter.getOpenStyle().setShowType(ShowType.Modal);
        showParameter.setCloseCallBack(new CloseCallBack(this, "give"));
        this.getView().showForm(showParameter);
    }

    @Override
    public void closedCallBack(ClosedCallBackEvent closedCallBackEvent) {
        super.closedCallBack(closedCallBackEvent);
        String actionId = closedCallBackEvent.getActionId();
        // 判断标识是否匹配，并验证返回值不为空，不验证返回值可能会报空指针
        if (StringUtils.equals(actionId, GIVE)) {
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
            DynamicObject aosMktPhotoreq = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_aadd");
            String aosUserold = aosMktPhotoreq.getDynamicObject("aos_user").getPkValue().toString();
            String billno = aosMktPhotoreq.getString("billno");
            if (!(String.valueOf(currentUserId)).equals(aosUserold) && !exists) {
                this.getView().showTipNotification(billno + "只允许转办自己的单据!");
                return;
            }
            aosMktPhotoreq.set("aos_user", aosUser);
            OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_aadd",
                new DynamicObject[] {aosMktPhotoreq}, OperateOption.create());
            MKTCom.SendGlobalMessage(String.valueOf(((DynamicObject)aosUser).getPkValue()),
                String.valueOf(aosMktPhotoreq), String.valueOf(operationrst.getSuccessPkIds().get(0)), billno,
                currentUserName + "流程转办!");
            FndHistory fndHistory = new FndHistory();
            fndHistory.SetActionBy(currentUserId);
            fndHistory.SetFormId("aos_mkt_aadd");
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

    private void aosSubmit() {
        ListSelectedRowCollection selectedRows = this.getSelectedRows();
        int size = selectedRows.size();
        IListView view = (IListView)this.getView();
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
            this.getView().showSuccessNotification(message);
        }
    }

    @Override
    public void setFilter(SetFilterEvent e) {
        List<QFilter> qFilters = e.getQFilters();
        ParaInfoUtil.setRights(qFilters, this.getPageCache(), "aos_mkt_aadd");
    }
}
