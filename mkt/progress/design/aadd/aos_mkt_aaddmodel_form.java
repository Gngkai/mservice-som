package mkt.progress.design.aadd;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.HashMap;
import java.util.List;

import common.fnd.FndGlobal;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.serialization.SerializationUtils;
import kd.bos.form.ClientProperties;
import kd.bos.form.IFormView;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;

public class aos_mkt_aaddmodel_form extends AbstractFormPlugin {

	public void afterCreateNewData(EventObject e) {
		super.afterCreateNewData(e);
		InitData();
	}

	@Override
	public void afterBindData(EventObject e) {
		super.afterBindData(e);
	}

	private void InitData() {
		IFormView parentView = this.getView().getParentView();
		Object fid = parentView.getModel().getDataEntity().getPkValue();
		String button = parentView.getPageCache().get("button");
		DynamicObjectCollection aos_aadd_model_detailS = QueryServiceHelper.query("aos_aadd_model_detail",
				"aos_cate1,aos_cate2," + "aos_cn,aos_usca,aos_uk,aos_de,aos_fr,aos_it,aos_es,aos_seq",
				new QFilter("aos_sourceid", QCP.equals, fid.toString()).and("aos_button", QCP.equals, Integer.parseInt(button))
						.toArray(),"aos_seq asc");
		this.getModel().deleteEntryData("aos_entryentity");
		int i = 0;
		for (DynamicObject aos_aadd_model_detail : aos_aadd_model_detailS) {
			this.getModel().batchCreateNewEntryRow("aos_entryentity", 1);
			this.getModel().setValue("aos_cate1", aos_aadd_model_detail.get("aos_cate1"), i);
			this.getModel().setValue("aos_cate2", aos_aadd_model_detail.get("aos_cate2"), i);
			this.getModel().setValue("aos_cn", aos_aadd_model_detail.get("aos_cn"), i);
			this.getModel().setValue("aos_usca", aos_aadd_model_detail.get("aos_usca"), i);
			this.getModel().setValue("aos_uk", aos_aadd_model_detail.get("aos_uk"), i);
			this.getModel().setValue("aos_de", aos_aadd_model_detail.get("aos_de"), i);
			this.getModel().setValue("aos_fr", aos_aadd_model_detail.get("aos_fr"), i);
			this.getModel().setValue("aos_it", aos_aadd_model_detail.get("aos_it"), i);
			this.getModel().setValue("aos_es", aos_aadd_model_detail.get("aos_es"), i);
			this.getModel().setValue("aos_seq", aos_aadd_model_detail.get("aos_seq"), i);
			i++;
		}

		//控制语言
		String lan = parentView.getPageCache().get(aos_mkt_aaddmodel_bill.KEY_USER);
		List<String> users= (List<String>) SerializationUtils.fromJsonStringToList(lan,String.class);
		for (String userLan : users) {
			String field = aos_mkt_aaddmodel_bill.judgeLan(userLan);
			if (FndGlobal.IsNotNull(field)) {
				this.getModel().setValue(field+"_h",true);
			}
		}
	}

}
