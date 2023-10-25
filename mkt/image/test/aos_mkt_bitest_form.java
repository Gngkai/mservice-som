package mkt.image.test;

import java.util.EventObject;
import common.fnd.FndMsg;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import mkt.synciface.*;
import sal.synciface.imp.aos_sal_import_invprice;

public class aos_mkt_bitest_form extends AbstractFormPlugin {

	/*private static AosomLog logger = AosomLog.init("aos_mkt_bitest_form");

	static {
		logger.setService("aos.mms");
		logger.setDomain("mms.act");
		logger.setFile("aos_mkt_bitest_form");
	}*/

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
		aos_sal_import_invprice.do_operate(null);
//		AosMktSyncSaleOut.do_operate();
	}

	public static Object aos_junit(Object obj) {
		FndMsg.debug("obj:" + obj);
		return obj;
	}

	private long n = 0;

	public long add(long x) {
		n = n + x;
		return n;
	}

	public long sub(long x) {
		n = n - x;
		return n;
	}

}
