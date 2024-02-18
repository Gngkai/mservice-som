package mkt.progress.design;

import java.util.*;
import java.util.stream.Collectors;

import com.alibaba.nacos.common.utils.Pair;
import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndHistory;
import common.sal.util.SalUtil;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.datamodel.ListSelectedRow;
import kd.bos.entity.datamodel.ListSelectedRowCollection;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.CloseCallBack;
import kd.bos.form.FormShowParameter;
import kd.bos.form.IFormView;
import kd.bos.form.ShowType;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.AfterQueryOfExportEvent;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.MktComUtil;
import mkt.progress.ProgressUtil;
import mkt.progress.iface.ParaInfoUtil;
import org.apache.commons.collections4.BidiMap;

/**
 * @author aosom
 */
public class AosMktDesignReqList extends AbstractListPlugin {
    public final static String AOS_MKT_DESIGNREQ = "aos_mkt_designreq";
    public final static String AOS_BATCHGIVE = "aos_batchgive";
    public final static String AOS_SUBMIT = "aos_submit";
    public final static String AOS_SHOWCLOSE = "aos_showclose";
    public final static String FORM = "form";

    private static void fillExportData(DynamicObject[] dycExport) {
        Map<String, List<String>> mapFill = new HashMap<>(16);
        mapFill.put("设计", Arrays.asList("aos_design_re", "aos_design_su"));
        mapFill.put("设计确认:翻译", Arrays.asList("aos_trans_re", "aos_trans_su"));
        mapFill.put("设计确认3D", Arrays.asList("aos_3d_re", "aos_3d_su"));
        for (DynamicObject dy : dycExport) {
            String name = dy.getDynamicObjectType().getName();
            String id = dy.getString("id");
            Pair<BidiMap<String, Integer>, Map<String, DynamicObject>> pair = ProgressUtil.getOperateLog(name, id, "");
            BidiMap<String, Integer> mapOpIndex = pair.getFirst();
            Map<String, DynamicObject> mapOpDy = pair.getSecond();
            for (Map.Entry<String, List<String>> entry : mapFill.entrySet()) {
                if (mapOpIndex.containsKey(entry.getKey())) {
                    dy.set(entry.getValue().get(1), mapOpDy.get(entry.getKey()).getDate("aos_actiondate"));
                    // 如果没有 制作设计师，补上设计节点的提交人
                    if ("设计".equals(entry.getKey())) {
                        if (Cux_Common_Utl.IsNull(dy.get("aos_designby"))) {
                            DynamicObject dyUser = BusinessDataServiceHelper
                                .loadSingle(mapOpDy.get(entry.getKey()).getString("aos_actionby"), "bos_user");
                            dy.set("aos_designby", dyUser);
                        }
                        if (Cux_Common_Utl.IsNull(dy.get("aos_design_date"))) {
                            dy.set("aos_design_date", mapOpDy.get(entry.getKey()).getDate("aos_actiondate"));
                        }
                    }
                    // 前一个节点
                    // 设计节点的前一个节点
                    int index = mapOpIndex.get(entry.getKey()) - 1;
                    if (mapOpIndex.containsValue(index)) {
                        String key = mapOpIndex.getKey(index);
                        dy.set(entry.getValue().get(0), mapOpDy.get(key).getDate("aos_actiondate"));
                    }
                    // 如果没有前一个节点，则把申请日期作为当前节点的收到日期（即上一个节点的提交日期）
                    else {
                        dy.set(entry.getValue().get(0), dy.getDate("aos_requiredate"));
                    }
                }
            }
        }
    }

    @Override
    public void setFilter(SetFilterEvent e) {
        List<QFilter> qFilters = e.getQFilters();
        // 判断是否不可见拍照功能图制作
        QFilter filterCode = new QFilter("aos_code", "=", "aos_mkt_design_photoStatus");
        QFilter filterValue = new QFilter("aos_value", "=", "1");
        boolean exists = QueryServiceHelper.exists("aos_sync_params", new QFilter[] {filterCode, filterValue});
        if (exists) {
            QFilter filterStatus = new QFilter("aos_status", "!=", "拍照功能图制作");
            qFilters.add(filterStatus);
        }
        long currUserId = RequestContext.get().getCurrUserId();
        // 是销售
        if (ProgressUtil.JudgeSaleUser(currUserId, ProgressUtil.Dapartment.Sale.getNumber())) {
            ParaInfoUtil.setRights(qFilters, this.getPageCache(), AOS_MKT_DESIGNREQ);
        }
        // 不是销售
        else {
            ParaInfoUtil.setRightsForDesign(qFilters, this.getPageCache());
        }
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String itemKey = evt.getItemKey();
        try {
            if (AOS_BATCHGIVE.equals(itemKey)) {
                aosOpen();// 批量转办
            } else if (AOS_SUBMIT.equals(itemKey)) {
                {
                    aosSubmit();
                }
            } else if (AOS_SHOWCLOSE.equals(itemKey)) {
                // 查询关闭流程
                ParaInfoUtil.showClose(this.getView());
            }
        } catch (FndError fndMessage) {
            this.getView().showTipNotification(fndMessage.getErrorMessage());
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
        }
    }

    private void aosSubmit() {
        try {
            ListSelectedRowCollection selectedRows = this.getSelectedRows();
            int size = selectedRows.size();
            if (size == 0) {
                this.getView().showTipNotification("请先选择提交数据");
            } else {
                IFormView view = this.getView();
                String message = ProgressUtil.submitEntity(view, selectedRows);
                if (message != null && message.length() > 0) {
                    message = "提交成功,部分无权限单据无法提交：  " + message;
                } else {
                    message = "提交成功";
                }
                this.getView().updateView();
                this.getView().showSuccessNotification(message);
            }
        } catch (FndError fndMessage) {
            this.getView().showTipNotification(fndMessage.getErrorMessage());
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
        }
    }

    /** 弹出转办框 **/
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
            DynamicObject aosMktDesignreq = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_designreq");
            String aosUserold = aosMktDesignreq.getDynamicObject("aos_user").getPkValue().toString();
            String billno = aosMktDesignreq.getString("billno");
            String aosStatus = aosMktDesignreq.getString("aos_status");
            if (!(String.valueOf(currentUserId)).equals(aosUserold) && !exists) {
                this.getView().showTipNotification(billno + "只允许转办自己的单据!");
                return;
            }
            aosMktDesignreq.set("aos_user", aosUser);
            // 设计节点转办时 将设计调整为 转办操作人
            if ("设计".equals(aosStatus)) {
                aosMktDesignreq.set("aos_designer", aosUser);
            }
            OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_designreq",
                new DynamicObject[] {aosMktDesignreq}, OperateOption.create());
            MktComUtil.sendGlobalMessage(String.valueOf(((DynamicObject)aosUser).getPkValue()),
                String.valueOf(aosMktDesignreq), String.valueOf(operationrst.getSuccessPkIds().get(0)), billno,
                currentUserName + "流程转办!");
            FndHistory fndHistory = new FndHistory();
            fndHistory.SetActionBy(currentUserId);
            fndHistory.SetFormId("aos_mkt_designreq");
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

    @Override
    public void afterQueryOfExport(AfterQueryOfExportEvent e) {
        fillExportData(e.getQueryValues());
    }
}