package mkt.act.month;

import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;
import kd.bos.form.control.Control;
import kd.bos.form.plugin.AbstractFormPlugin;

public class aos_mkt_actmonth_form extends AbstractFormPlugin {
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
			Object data_sche = this.getModel().getValue("aos_date");
			map.put("aos_date", data_sche);
			this.getView().returnDataToParent(map);
			this.getView().close();
		}
	}
}
