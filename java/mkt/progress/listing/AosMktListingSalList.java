package mkt.progress.listing;

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
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.CloseCallBack;
import kd.bos.form.FormShowParameter;
import kd.bos.form.ShowType;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.MKTCom;
import mkt.progress.iface.parainfo;

/**
 * @author aosom
 */
public class AosMktListingSalList extends AbstractListPlugin {
    public final static String AOS_MKT_LISTING_SAL = "aos_mkt_listing_sal";
    public final static String SHOWCLOSE = "aos_showclose";
    public final static String BATCHGIVE = "aos_batchgive";
    public final static String FORM = "form";

    @Override
    public void setFilter(SetFilterEvent e) {
        List<QFilter> qFilters = e.getQFilters();
        parainfo.setRights(qFilters, this.getPageCache(), AOS_MKT_LISTING_SAL);
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String itemKey = evt.getItemKey();
        try {
            if (SHOWCLOSE.equals(itemKey)) {
                // 查询关闭流程
                parainfo.showClose(this.getView());
            } else if (BATCHGIVE.equals(itemKey)) {
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
            DynamicObject aosMktListingSal = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_listing_sal");
            String aosUserold = aosMktListingSal.getDynamicObject("aos_user").getPkValue().toString();
            String billno = aosMktListingSal.getString("billno");
            if (!(String.valueOf(currentUserId)).equals(aosUserold)) {
                this.getView().showTipNotification(billno + "只允许转办自己的单据!");
                return;
            }
            aosMktListingSal.set("aos_user", aosUser);
            OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_sal",
                new DynamicObject[] {aosMktListingSal}, OperateOption.create());
            MKTCom.SendGlobalMessage(String.valueOf(((DynamicObject)aosUser).getPkValue()),
                String.valueOf(aosMktListingSal), String.valueOf(operationrst.getSuccessPkIds().get(0)), billno,
                currentUserName + "流程转办!");
            FndHistory fndHistory = new FndHistory();
            fndHistory.SetActionBy(currentUserId);
            fndHistory.SetFormId("aos_mkt_listing_sal");
            fndHistory.SetSourceId(id);
            fndHistory.SetActionCode("流程转办");
            fndHistory.SetDesc(currentUserName + "流程转办!");
            Cux_Common_Utl.History(fndHistory);
        }
        this.getView().showSuccessNotification("转办成功");
        this.getView().invokeOperation("refresh");
    }
}