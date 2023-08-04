package mkt.progress.design.aadd;

import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;

import kd.bos.dataentity.entity.LocaleString;
import kd.bos.form.ClientProperties;
import kd.bos.form.control.Control;
import kd.bos.form.plugin.AbstractFormPlugin;

public class aos_mkt_aaddmodel_show extends AbstractFormPlugin {

	public void registerListener(EventObject e) {
		super.registerListener(e);
		this.addClickListeners("btnok");
	}

	public void click(EventObject evt) {
		super.click(evt);
		Control control = (Control) evt.getSource();
		String key = control.getKey();
		if (key.equals("btnok")) {
			String aos_button = (String) this.getModel().getValue("aos_button");
			String aos_name = (String) this.getModel().getValue("aos_name");
			this.getView().getParentView().getModel().setValue("aos_textfield"+aos_button, aos_name);
			this.getView().getParentView().invokeOperation("save");
			Map<String, Object> map = new HashMap<>();
			map.put(ClientProperties.Text, new LocaleString(aos_name));
			this.getView().getParentView().updateControlMetadata("aos_button"+aos_button, map);
			this.getView().close();
		}
	}
}