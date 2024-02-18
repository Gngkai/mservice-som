package mkt.common;

import kd.bos.form.events.BeforeClosedEvent;
import kd.bos.form.plugin.AbstractFormPlugin;

/**
 * @author aosom
 * @version 上传图片-动态表单插件
 */
public class AosPicMiddleForm extends AbstractFormPlugin {
	@Override
	public void beforeClosed(BeforeClosedEvent e) {
		super.beforeClosed(e);
		String picturefield =(String)this.getModel().getValue("aos_picture");
		this.getView().returnDataToParent(picturefield);
	}
}