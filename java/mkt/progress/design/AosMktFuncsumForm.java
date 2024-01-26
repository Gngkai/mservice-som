package mkt.progress.design;

import java.util.EventObject;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.servicehelper.QueryServiceHelper;

/**
 * @author aosom
 * @version 功能图翻译任务台账-动态表单插件
 */
public class AosMktFuncsumForm extends AbstractFormPlugin {
    @Override
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        initData();
    }

    /** 初始化数据 **/
    private void initData() {
        String selectColumn =
            "id,aos_orgid,aos_itemid,aos_eng,aos_sourcebill,aos_frombill,aos_creationdate,aos_sourceid"
                + ",aos_triggerdate";
        DynamicObjectCollection aosMktFuncsumdataS =
            QueryServiceHelper.query("aos_mkt_funcsumdata", selectColumn, null);
        this.getModel().deleteEntryData("aos_entryentity");
        int i = 0;
        for (DynamicObject aosMktFuncsumdata : aosMktFuncsumdataS) {
            this.getModel().batchCreateNewEntryRow("aos_entryentity", 1);
            this.getModel().setValue("aos_orgid", aosMktFuncsumdata.get("aos_orgid"), i);
            this.getModel().setValue("aos_itemid", aosMktFuncsumdata.get("aos_itemid"), i);
            this.getModel().setValue("aos_eng", aosMktFuncsumdata.get("aos_eng"), i);
            this.getModel().setValue("aos_sourcebill", aosMktFuncsumdata.get("aos_sourcebill"), i);
            this.getModel().setValue("aos_frombill", aosMktFuncsumdata.get("aos_frombill"), i);
            this.getModel().setValue("aos_creationdate", aosMktFuncsumdata.get("aos_creationdate"), i);
            this.getModel().setValue("aos_triggerdate", aosMktFuncsumdata.get("aos_triggerdate"), i);
            this.getModel().setValue("aos_sourceid", aosMktFuncsumdata.get("aos_sourceid"), i);
            i++;
        }
    }
}
