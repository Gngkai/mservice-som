package mkt.common;

import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;

import kd.bos.form.FieldTip;
import kd.bos.form.control.Control;
import kd.bos.form.plugin.AbstractFormPlugin;

public class aos_mkt_progive_form extends AbstractFormPlugin {
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
			if (this.getModel().getValue("aos_user") != null) {
				Object aos_user = this.getModel().getValue("aos_user");
				map.put("aos_user", aos_user);
				this.getView().returnDataToParent(map);
				this.getView().close();
			} else {
				FieldTip remarks = new FieldTip(FieldTip.FieldTipsLevel.Info, FieldTip.FieldTipsTypes.others,
						"aos_user", "转办接收人不能为空");
				this.getView().showFieldTip(remarks);
			}
		}
	}
}
