package mkt.popular.ppc;

import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.form.FieldTip;
import kd.bos.form.control.Control;
import kd.bos.form.plugin.AbstractFormPlugin;

public class aos_mkt_popppc_form extends AbstractFormPlugin {
	public void registerListener(EventObject e) {
		super.registerListener(e);
		this.addClickListeners("btnok");
	}
	public void click(EventObject evt) {
		super.click(evt);
		Control control = (Control) evt.getSource();
		String key = control.getKey();
		Map<String, Object> map = new HashMap<>();
		if (key.equals("btnok")) {
			if (this.getModel().getValue("aos_orgid") != null) {
				String aos_ou_code = ((DynamicObject) this.getModel().getValue("aos_orgid")).getString("number");
				map.put("aos_ou_code", aos_ou_code);
				this.getView().returnDataToParent(map);
				this.getView().close();
			} else {
				FieldTip remarks = new FieldTip(FieldTip.FieldTipsLevel.Info, FieldTip.FieldTipsTypes.others,
						"aos_orgid", "国别不能为空");
				this.getView().showFieldTip(remarks);
			}
		}
	}

}
