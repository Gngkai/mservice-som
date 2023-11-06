package mkt.progress.photo;

import java.util.EventObject;
import java.util.Map;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;

public class aos_mkt_phoson_form extends AbstractFormPlugin {

	public void afterCreateNewData(EventObject e) {
		super.afterCreateNewData(e);
		Map<String, Object> map = this.getView().getFormShowParameter().getCustomParam("params");
		QFilter filter_formid = new QFilter("aos_parentid", "=", map.get("aos_parentid"));
		QFilter filter_status = new QFilter("aos_status", "!=", "已完成");
		QFilter[] filters = new QFilter[] { filter_formid,filter_status };
		String selectFields = "aos_user,aos_status";
		DynamicObjectCollection aos_sync_operateS = QueryServiceHelper.query("aos_mkt_photoreq", selectFields,
				filters);
		this.getModel().deleteEntryData("aos_entryentity");
		int i = 0;
		for (DynamicObject aos_sync_operate : aos_sync_operateS) {
			this.getModel().batchCreateNewEntryRow("aos_entryentity", 1);
			this.getModel().setValue("aos_user", aos_sync_operate.get("aos_user"), i);
			this.getModel().setValue("aos_status", aos_sync_operate.get("aos_status"), i);
			i++;
		}
		
	}
}
