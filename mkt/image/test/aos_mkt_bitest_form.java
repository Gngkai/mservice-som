package mkt.image.test;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EventObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import common.fnd.AosomLog;
import common.fnd.FndMsg;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.plugin.AbstractFormPlugin;
import mkt.synciface.*;

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
		aos_mkt_syncif_sku.executerun();
	}

	public static Object aos_junit(Object obj) {
		FndMsg.debug("obj:"+obj);
		return obj;
	}

	public void error(String var, Object... var2) {
		String aString = MessageFormat.format(var, var2);
		FndMsg.debug("aString:" + aString);
	}

	public static long getSecondsDifference(Date startDate, Date endDate) {
		long startMillis = startDate.getTime();
		long endMillis = endDate.getTime();
		long diffMillis = endMillis - startMillis;
		return diffMillis / 1000; // 将毫秒转换为秒
	}

	private static void sortMapValue() {
		Map<String, String> map = new HashMap<>();
		map.put("a", "2");
		map.put("c", "5");
		map.put("d", "6");
		map.put("b", "1");
		List<Map.Entry<String, String>> lstEntry = new ArrayList<>(map.entrySet());
		Collections.sort(lstEntry, ((o1, o2) -> {
			return o1.getValue().compareTo(o2.getValue());
		}));
		
		lstEntry.forEach(o -> {
		});
	}
}
