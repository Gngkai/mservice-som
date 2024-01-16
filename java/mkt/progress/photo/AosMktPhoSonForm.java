package mkt.progress.photo;

import java.util.EventObject;
import java.util.Map;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;

/**
 * @author aosom 拍照子流程弹框-动态表单插件
 */
public class AosMktPhoSonForm extends AbstractFormPlugin {
    @Override
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        Map<String, Object> map = this.getView().getFormShowParameter().getCustomParam("params");
        QFilter filterFormid = new QFilter("aos_parentid", "=", map.get("aos_parentid"));
        QFilter filterStatus = new QFilter("aos_status", "!=", "已完成");
        QFilter[] filters = new QFilter[] {filterFormid, filterStatus};
        String selectFields = "aos_user,aos_status";
        DynamicObjectCollection aosSyncOperateS = QueryServiceHelper.query("aos_mkt_photoreq", selectFields, filters);
        this.getModel().deleteEntryData("aos_entryentity");
        int i = 0;
        for (DynamicObject aosSyncOperate : aosSyncOperateS) {
            this.getModel().batchCreateNewEntryRow("aos_entryentity", 1);
            this.getModel().setValue("aos_user", aosSyncOperate.get("aos_user"), i);
            this.getModel().setValue("aos_status", aosSyncOperate.get("aos_status"), i);
            i++;
        }
    }
}
