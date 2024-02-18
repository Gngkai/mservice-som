package mkt.progress.listing;

import com.alibaba.nacos.common.utils.Pair;
import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndHistory;
import common.sal.util.SalUtil;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.metadata.IMetadata;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.datamodel.ListSelectedRow;
import kd.bos.entity.datamodel.ListSelectedRowCollection;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.*;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.AfterQueryOfExportEvent;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.form.events.MessageBoxClosedEvent;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.list.IListView;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.MktComUtil;
import mkt.progress.ProgressUtil;
import mkt.progress.iface.ParaInfoUtil;
import org.apache.commons.collections4.BidiMap;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author create by gk
 * @version Listing优化需求表小语种-列表插件
 */
public class AosMktListingMinList extends AbstractListPlugin {
    public final static String AOS_MKT_LISTING_MIN = "aos_mkt_listing_min";
    public final static String AOS_SUBMIT = "aos_submit";
    public final static String AOS_BATCHGIVE = "aos_batchgive";
    public final static String AOS_SHOWCLOSE = "aos_showclose";
    public final static String AOS_CLOSE = "aos_close";
    public final static String BAR_CANCEL = "bar_cancel";
    public final static String FORM = "form";

    private static void fillExportData(DynamicObject[] dycExport) {
        Map<String, List<String>> mapFill = new HashMap<>(16);
        mapFill.put("编辑确认", Arrays.asList("aos_design_re", "aos_design_su"));
        Map<String, String> mapOther = new HashMap<>(16);
        mapOther.put("海外编辑确认", "aos_over_su");
        mapOther.put("海外编辑确认:功能图", "aos_overfun_su");
        // 引出模板的所有字段
        List<String> listFields = dycExport[0].getDynamicObjectType().getProperties().stream().map(IMetadata::getName)
            .distinct().collect(Collectors.toList());

        for (DynamicObject dy : dycExport) {
            String name = dy.getDynamicObjectType().getName();
            String id = dy.getString("id");
            Pair<BidiMap<String, Integer>, Map<String, DynamicObject>> pair = ProgressUtil.getOperateLog(name, id, "");
            BidiMap<String, Integer> mapOpIndex = pair.getFirst();
            Map<String, DynamicObject> mapOpDate = pair.getSecond();
            // 编辑节点
            for (Map.Entry<String, List<String>> entry : mapFill.entrySet()) {
                if (mapOpIndex.containsKey(entry.getKey())) {
                    // 设计节点的前一个节点
                    int index = mapOpIndex.get(entry.getKey()) - 1;
                    if (listFields.contains(entry.getValue().get(1))) {
                        dy.set(entry.getValue().get(1), mapOpDate.get(entry.getKey()).getDate("aos_actiondate"));
                        // 如果编辑确认师为空，赋上编辑确认师的值
                        if (listFields.contains("aos_make") && dy.get("aos_make") == null) {
                            DynamicObject dyUser = BusinessDataServiceHelper
                                .loadSingle(mapOpDate.get(entry.getKey()).getString("aos_actionby"), "bos_user");
                            dy.set("aos_make", dyUser);
                        }
                    }
                    if (mapOpIndex.containsValue(index)) {
                        String key = mapOpIndex.getKey(index);
                        if (listFields.contains(entry.getValue().get(0))) {
                            dy.set(entry.getValue().get(0), mapOpDate.get(key).getDate("aos_actiondate"));
                        }
                    }
                }
            }
            // 其他节点
            for (Map.Entry<String, String> entry : mapOther.entrySet()) {
                if (mapOpDate.containsKey(entry.getKey())) {
                    if (listFields.contains(entry.getValue())) {
                        dy.set(entry.getValue(), mapOpDate.get(entry.getKey()).getDate("aos_actiondate"));
                    }
                }
            }
            // 如果单据手动关闭
            if (mapOpIndex.containsKey("手工关闭")) {
                if (listFields.contains("aos_make")) {
                    dy.set("billstatus", "手动关闭");
                }
            }
        }
    }

    @Override
    public void setFilter(SetFilterEvent e) {
        List<QFilter> qFilters = e.getQFilters();
        String[] otherUser = new String[] {"aos_editor", "aos_editormin"};
        ParaInfoUtil.setRights(qFilters, this.getPageCache(), AOS_MKT_LISTING_MIN, otherUser);
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String itemKey = evt.getItemKey();
        try {
            if (AOS_SUBMIT.equals(itemKey)) {
                aosSubmit();
            } else if (AOS_BATCHGIVE.equals(itemKey)) {
                // 批量转办
                aosOpen();
            } else if (AOS_SHOWCLOSE.equals(itemKey)) {
                // 查询关闭流程
                ParaInfoUtil.showClose(this.getView());
            } else if (AOS_CLOSE.equals(itemKey)) {
                // 批量关闭
                aosClose();
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

    private void aosClose() {
        String keyCancel = "bar_cancel";
        ConfirmCallBackListener confirmCallBackListener = new ConfirmCallBackListener(keyCancel, this);
        // 设置页面确认框，参数为：标题，选项框类型，回调监听
        this.getView().showConfirm("您确认关闭此申请单吗？", MessageBoxOptions.YesNo, confirmCallBackListener);
    }

    @Override
    public void confirmCallBack(MessageBoxClosedEvent event) {
        super.confirmCallBack(event);
        String callBackId = event.getCallBackId();
        if (BAR_CANCEL.equals(callBackId)) {
            List<DynamicObject> listClose = new ArrayList<>();
            if (event.getResult().equals(MessageBoxResult.Yes)) {
                IListView view = (IListView)this.getView();
                String billFormId = view.getBillFormId();
                ListSelectedRowCollection selectedRows = this.getSelectedRows();
                String current = String.valueOf(UserServiceHelper.getCurrentUserId());
                // 无权关闭单据编码
                StringJoiner strNoUser = new StringJoiner(" , ");
                for (ListSelectedRow row : selectedRows) {
                    DynamicObject dy = BusinessDataServiceHelper.loadSingle(row.getPrimaryKeyValue(), billFormId);
                    String user = dy.getDynamicObject("aos_user").getString("id");
                    // 关闭人是操作人
                    if (user.equals(current)) {
                        dy.set("aos_user", Cux_Common_Utl.SYSTEM);
                        dy.set("aos_status", "结束");
                        AosMktListingMinBill.setErrorList(dy);
                        listClose.add(dy);
                        FndHistory.Create(dy, "手工关闭", "手工关闭");
                    } else {
                        strNoUser.add(row.getBillNo());
                    }
                }
                String message = "批量关闭完成";
                if (strNoUser.toString().length() > 0) {
                    message = message + "，以下单据不是操作人，无权关闭:  " + strNoUser;
                }
                if (listClose.size() > 0) {
                    int size = listClose.size();
                    SaveServiceHelper.update(listClose.toArray(new DynamicObject[size]));
                }
                this.getView().updateView();
                this.getView().showMessage(message);
            }
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
            DynamicObject aosMktListingMin = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_listing_min");
            String aosUserold = aosMktListingMin.getDynamicObject("aos_user").getPkValue().toString();
            String billno = aosMktListingMin.getString("billno");
            if (!(String.valueOf(currentUserId)).equals(aosUserold)) {
                this.getView().showTipNotification(billno + "只允许转办自己的单据!");
                return;
            }
            aosMktListingMin.set("aos_user", aosUser);
            OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_min",
                new DynamicObject[] {aosMktListingMin}, OperateOption.create());
            MktComUtil.sendGlobalMessage(String.valueOf(((DynamicObject)aosUser).getPkValue()),
                String.valueOf(aosMktListingMin), String.valueOf(operationrst.getSuccessPkIds().get(0)), billno,
                currentUserName + "流程转办!");
            FndHistory fndHistory = new FndHistory();
            fndHistory.SetActionBy(currentUserId);
            fndHistory.SetFormId("aos_mkt_listing_min");
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
