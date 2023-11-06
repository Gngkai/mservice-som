package mkt.common;

import kd.bos.form.events.BeforeClosedEvent;
import kd.bos.form.plugin.AbstractFormPlugin;

public class aos_picmiddle_form extends AbstractFormPlugin {
	@Override
	public void beforeClosed(BeforeClosedEvent e) {
		super.beforeClosed(e);
		String picturefield =(String)this.getModel().getValue("aos_picture");
		this.getView().returnDataToParent(picturefield);
	}
}