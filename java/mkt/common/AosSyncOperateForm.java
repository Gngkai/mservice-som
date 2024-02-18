package mkt.common;

import java.util.EventObject;
import java.util.Map;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;

/**
 * @author aosom
 * @version 操作记录表(查看)-表单插件
 */
public class AosSyncOperateForm extends AbstractFormPlugin {
    static Log log = LogFactory.getLog("mkt.common.aos_sync_operate_form");

    @Override
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        Map<String, Object> map = this.getView().getFormShowParameter().getCustomParam("params");
        String aosFormid = String.valueOf(map.get("aos_formid"));
        String aosSourceid = String.valueOf(map.get("aos_sourceid"));
        log.info("form : {},source : {}", aosFormid, aosSourceid);
        QFilter filterFormid = new QFilter("aos_formid", "=", aosFormid);
        log.info("q1: {}", filterFormid.toString());
        QFilter filterSourceid = new QFilter("aos_sourceid", "=", aosSourceid);
        log.info("q2: {}", filterSourceid.toString());
        QFilter[] filters = new QFilter[] {filterFormid, filterSourceid};
        String selectFields = "aos_sourceid,aos_formid,aos_actionseq,aos_action,aos_actionby,aos_actiondate,aos_desc";
        DynamicObjectCollection aosSyncOperateS = QueryServiceHelper.query("aos_sync_operate", selectFields, filters);
        this.getModel().deleteEntryData("aos_entryentity");
        int i = 0;
        for (DynamicObject aosSyncOperate : aosSyncOperateS) {
            if (aosSyncOperate.getString("aos_sourceid").equals(aosSourceid)) {
                this.getModel().batchCreateNewEntryRow("aos_entryentity", 1);
                this.getModel().setValue("aos_sourceid", aosSyncOperate.get("aos_sourceid"), i);
                this.getModel().setValue("aos_formid", aosSyncOperate.get("aos_formid"), i);
                this.getModel().setValue("aos_actionseq", aosSyncOperate.get("aos_actionseq"), i);
                this.getModel().setValue("aos_action", aosSyncOperate.get("aos_action"), i);
                this.getModel().setValue("aos_actionby", aosSyncOperate.get("aos_actionby"), i);
                this.getModel().setValue("aos_actiondate", aosSyncOperate.get("aos_actiondate"), i);
                this.getModel().setValue("aos_desc", aosSyncOperate.get("aos_desc"), i);
                i++;
            }
        }
    }
}
