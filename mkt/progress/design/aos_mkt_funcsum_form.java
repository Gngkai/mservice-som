package mkt.progress.design;

import java.util.EventObject;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.servicehelper.QueryServiceHelper;

public class aos_mkt_funcsum_form extends AbstractFormPlugin {
	public void afterCreateNewData(EventObject e) {
		super.afterCreateNewData(e);
		InitData();
	}

	/** 初始化数据 **/
	private void InitData() {
		String SelectColumn = "id,aos_orgid,aos_itemid,aos_eng,aos_sourcebill,aos_frombill,aos_creationdate,aos_sourceid"
				+ ",aos_triggerdate";
		DynamicObjectCollection aos_mkt_funcsumdataS = QueryServiceHelper.query("aos_mkt_funcsumdata", SelectColumn,
				null);
		this.getModel().deleteEntryData("aos_entryentity");
		int i = 0;
		for (DynamicObject aos_mkt_funcsumdata : aos_mkt_funcsumdataS) {
			this.getModel().batchCreateNewEntryRow("aos_entryentity", 1);
			this.getModel().setValue("aos_orgid", aos_mkt_funcsumdata.get("aos_orgid"), i);
			this.getModel().setValue("aos_itemid", aos_mkt_funcsumdata.get("aos_itemid"), i);
			this.getModel().setValue("aos_eng", aos_mkt_funcsumdata.get("aos_eng"), i);
			this.getModel().setValue("aos_sourcebill", aos_mkt_funcsumdata.get("aos_sourcebill"), i);
			this.getModel().setValue("aos_frombill", aos_mkt_funcsumdata.get("aos_frombill"), i);
			this.getModel().setValue("aos_creationdate", aos_mkt_funcsumdata.get("aos_creationdate"), i);
			this.getModel().setValue("aos_triggerdate", aos_mkt_funcsumdata.get("aos_triggerdate"), i);
			this.getModel().setValue("aos_sourceid", aos_mkt_funcsumdata.get("aos_sourceid"), i);
			i++;
		}
	}
}
