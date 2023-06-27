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

public class aos_sync_operate_form extends AbstractFormPlugin {
	static Log log = LogFactory.getLog("mkt.common.aos_sync_operate_form");
	public void afterCreateNewData(EventObject e) {
		super.afterCreateNewData(e);
		Map<String, Object> map = this.getView().getFormShowParameter().getCustomParam("params");
		String aos_formid = String.valueOf(map.get("aos_formid"));
		String aos_sourceid = String.valueOf(map.get("aos_sourceid"));
		log.info("form : {},source : {}",aos_formid,aos_sourceid);
		QFilter filter_formid = new QFilter("aos_formid", "=", aos_formid);
		log.info("q1: {}",filter_formid.toString());
		QFilter filter_sourceid = new QFilter("aos_sourceid", "=", aos_sourceid);
		log.info("q2: {}",filter_sourceid.toString());
		QFilter[] filters = new QFilter[] { filter_formid,filter_sourceid };
		String selectFields = "aos_sourceid,aos_formid,aos_actionseq,aos_action,aos_actionby,aos_actiondate,aos_desc";
		DynamicObjectCollection aos_sync_operateS = QueryServiceHelper.query("aos_sync_operate", selectFields, filters);
		this.getModel().deleteEntryData("aos_entryentity");
		int i = 0;
		for (DynamicObject aos_sync_operate : aos_sync_operateS) {
			if (aos_sync_operate.getString("aos_sourceid").equals(aos_sourceid)) {
				this.getModel().batchCreateNewEntryRow("aos_entryentity", 1);
				this.getModel().setValue("aos_sourceid", aos_sync_operate.get("aos_sourceid"), i);
				this.getModel().setValue("aos_formid", aos_sync_operate.get("aos_formid"), i);
				this.getModel().setValue("aos_actionseq", aos_sync_operate.get("aos_actionseq"), i);
				this.getModel().setValue("aos_action", aos_sync_operate.get("aos_action"), i);
				this.getModel().setValue("aos_actionby", aos_sync_operate.get("aos_actionby"), i);
				this.getModel().setValue("aos_actiondate", aos_sync_operate.get("aos_actiondate"), i);
				this.getModel().setValue("aos_desc", aos_sync_operate.get("aos_desc"), i);
				i++;
			}
		}
	}

}
