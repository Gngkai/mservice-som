package mkt.progress.listing;

import java.util.*;
import java.util.stream.Collectors;

import com.alibaba.nacos.common.utils.Pair;
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
import kd.bos.form.IFormView;
import kd.bos.form.ShowType;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.AfterQueryOfExportEvent;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.MKTCom;
import mkt.progress.ProgressUtil;
import mkt.progress.iface.parainfo;
import org.apache.commons.collections4.BidiMap;

/**
 * @author aosom
 */
public class AosMktListingSonList extends AbstractListPlugin {

    public final static String BATCH_GIVE = "aos_batchgive";
    public final static String SUBMIT = "aos_submit";
    public final static String CLOSE = "aos_showclose";
    public final static String FORM = "form";

    private static void fillExportData(DynamicObject[] dycExport) {
        Map<String, List<String>> mapFill = new HashMap<>(16);
        mapFill.put("编辑确认", Arrays.asList("aos_design_re", "aos_design_su"));
        for (DynamicObject dy : dycExport) {
            String name = dy.getDynamicObjectType().getName();
            String id = dy.getString("id");
            Pair<BidiMap<String, Integer>, Map<String, DynamicObject>> pair = ProgressUtil.getOperateLog(name, id, "");
            BidiMap<String, Integer> mapOpIndex = pair.getFirst();
            Map<String, DynamicObject> mapOpDy = pair.getSecond();
            for (Map.Entry<String, List<String>> entry : mapFill.entrySet()) {
                if (mapOpIndex.containsKey(entry.getKey())) {
                    dy.set(entry.getValue().get(1), mapOpDy.get(entry.getKey()).get("aos_actiondate"));
                    if (Cux_Common_Utl.IsNull(dy.get("aos_make"))) {
                        DynamicObject dyUser = BusinessDataServiceHelper
                            .loadSingle(mapOpDy.get(entry.getKey()).get("aos_actionby"), "bos_user");
                        dy.set("aos_make", dyUser);
                    }
                    // 设计节点的前一个节点
                    int index = mapOpIndex.get(entry.getKey()) - 1;
                    if (mapOpIndex.containsValue(index)) {
                        String key = mapOpIndex.getKey(index);
                        dy.set(entry.getValue().get(0), mapOpDy.get(key).getDate("aos_actiondate"));
                    } else {
                        dy.set(entry.getValue().get(0), dy.getDate("aos_requiredate"));
                    }
                }
            }
            if (mapOpDy.containsKey("海外确认")) {
                dy.set("aos_over_su", mapOpDy.get("海外确认").getDate("aos_actiondate"));
            }
        }
    }

    @Override
    public void setFilter(SetFilterEvent e) {
        List<QFilter> qFilters = e.getQFilters();
        parainfo.setRights(qFilters, this.getPageCache(), "aos_editor");
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String itemKey = evt.getItemKey();
        try {
            if (BATCH_GIVE.equals(itemKey)) {
                // 批量转办
                aosOpen();
            } else if (SUBMIT.equals(itemKey)) {
                aosSubmit();
            } else if (CLOSE.equals(itemKey)) {
                // 查询关闭流程
                parainfo.showClose(this.getView());
            }
        } catch (FndError fndMessage) {
            this.getView().showTipNotification(fndMessage.getErrorMessage());
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
        }
    }

    /** 批量提交 **/
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
        for (Object o : list) {
            String id = o.toString();
            DynamicObject aosMktListingSon = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_listing_son");
            String aosUserold = aosMktListingSon.getDynamicObject("aos_user").getPkValue().toString();
            String billno = aosMktListingSon.getString("billno");
            if (!(String.valueOf(currentUserId)).equals(aosUserold)) {
                this.getView().showTipNotification(billno + "只允许转办自己的单据!");
                return;
            }
			aosMktListingSon.set("aos_user", aosUser);
            AosMktListingSonBill.setListSonUserOrganizate(aosMktListingSon);
            OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_son",
                new DynamicObject[] {aosMktListingSon}, OperateOption.create());
            MKTCom.SendGlobalMessage(String.valueOf(((DynamicObject)aosUser).getPkValue()), "aos_mkt_listing_son",
                String.valueOf(operationrst.getSuccessPkIds().get(0)), billno, currentUserName + "流程转办!");
            FndHistory fndHistory = new FndHistory();
            fndHistory.SetActionBy(currentUserId);
            fndHistory.SetFormId("aos_mkt_listing_son");
            fndHistory.SetSourceId(id);
            fndHistory.SetActionCode("流程转办");
            fndHistory.SetDesc(currentUserName + "流程转办!");
            Cux_Common_Utl.History(fndHistory);
        }
        this.getView().showSuccessNotification("转办成功");
        this.getView().invokeOperation("refresh");
    }

    @Override
    public void afterQueryOfExport(AfterQueryOfExportEvent e) {
        fillExportData(e.getQueryValues());
    }
}
