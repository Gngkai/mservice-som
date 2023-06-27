package mkt.image.test;

import java.util.EventObject;

import common.fnd.AosomLog;
import common.fnd.AosomLogFactory;
import common.fnd.AosomLoggerImpl;
import common.fnd.FndMsg;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import mkt.popular.ppc.aos_mkt_popppc_init;

public class aos_mkt_bitest_form extends AbstractFormPlugin {

	public void registerListener(EventObject e) {
		super.registerListener(e);
		// 给工具栏加监听事件
		this.addItemClickListeners("aos_toolbarap");
		this.addItemClickListeners("aos_test"); // 提交
	}

	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String Control = evt.getItemKey();
		if (Control.equals("aos_test")) {
			aos_test();
		}
	}

	private void aos_test() {
		FndMsg.debug("=====into aos_test=====");
		AosomLog logger = AosomLog.init("aos_mkt_bitest_form");
		logger.fatal("test info");
	}

}
