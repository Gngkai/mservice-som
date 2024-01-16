package mkt.progress.photo;

import java.util.List;

import common.fnd.FndError;
import common.sal.util.SalUtil;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.datamodel.ListSelectedRow;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;

/**
 * @author aosom 不拍照任务清单-列表插件
 */
public class AosMktNoPhotoList extends AbstractListPlugin {
    public final static String AOS_SUBMIT = "aos_submit";
    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        try {
            if (StringUtils.equals(AOS_SUBMIT, evt.getItemKey())) {
                // 批量提交
                aosSubmit();
            }
        } catch (FndError fndMessage) {
            this.getView().showTipNotification(fndMessage.getErrorMessage());
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
        }
    }
    private void aosSubmit() throws FndError {
        List<ListSelectedRow> list = getSelectedRows();
        String errorMessage = "";
        for (ListSelectedRow listSelectedRow : list) {
            String id = listSelectedRow.toString();
            DynamicObject aosMktNophotolist = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_nophotolist");
            String aosReqno = aosMktNophotolist.getString("aos_reqno");
            String aosStatus = aosMktNophotolist.getString("aos_status");
            if (!"新建".equals(aosStatus)) {
                errorMessage += aosReqno + "新建状态下才允许提交!";
                this.getView().showTipNotification(errorMessage);
                // 刷新列表
                this.getView().invokeOperation("refresh");
                return;
            }
            aosMktNophotolist.set("aos_status", "已完成");
            OperationServiceHelper.executeOperate("save", "aos_mkt_nophotolist",
                new DynamicObject[] {aosMktNophotolist}, OperateOption.create());
        }
        this.getView().showSuccessNotification("提交成功");
        // 刷新列表
        this.getView().invokeOperation("refresh");
    }
}