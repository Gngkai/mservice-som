package mkt.image.test;

import java.text.SimpleDateFormat;
import java.util.*;

import common.fnd.FndMsg;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import mkt.progress.design.aadd.aos_mkt_aadd_init;
import mkt.progress.listing.manager.ListingManaCateSync;
import mkt.progress.listing.manager.ListingManaClSync;
import mkt.progress.listing.manager.ListingManaInitTask;
import mkt.progress.listing.manager.ListingManaKeyWordSync;

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
		ListingManaInitTask.process();
	}


	private static void init_skurpt3avg() {
		HashMap<String, Map<String, Object>> SkuRpt3Avg = new HashMap<>();
		Calendar calendar = Calendar.getInstance();
		calendar.set(Calendar.HOUR_OF_DAY, 0);
		calendar.set(Calendar.MINUTE, 0);
		calendar.set(Calendar.SECOND, 0);
		calendar.set(Calendar.MILLISECOND, 0);
		Date aos_date = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -2);
		Date date_to = calendar.getTime();
		calendar.add(Calendar.DAY_OF_MONTH, -3);
		Date date_from = calendar.getTime();
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);// 日期格式化
		String date_from_str = writeFormat.format(date_from);
		String date_to_str = writeFormat.format(date_to);
		String aos_date_str = writeFormat.format(aos_date);

		FndMsg.debug("date_to_str:" + date_to_str);
		FndMsg.debug("date_from_str:" + date_from_str);

		QFilter filter_date_from = new QFilter("aos_entryentity.aos_date_l", ">=", date_from_str);
		QFilter filter_date_to = new QFilter("aos_entryentity.aos_date_l", "<", date_to_str);
		QFilter filter_date = new QFilter("aos_date", "=", aos_date_str);
		QFilter filter_match = new QFilter("aos_entryentity.aos_cam_name", "like", "%-AUTO%");
		QFilter[] filters = new QFilter[] { filter_date_from, filter_date_to, filter_date, filter_match };
		String SelectColumn = "aos_orgid," + "aos_entryentity.aos_ad_sku aos_itemid,"
				+ "aos_entryentity.aos_spend aos_spend," + "aos_entryentity.aos_impressions aos_impressions";
		DataSet aos_base_skupoprptS = QueryServiceHelper.queryDataSet("aos_mkt_common_redis" + "." + "init_skurpt3avg",
				"aos_base_skupoprpt", SelectColumn, filters, null);
		String[] GroupBy = new String[] { "aos_orgid", "aos_itemid" };
		aos_base_skupoprptS = aos_base_skupoprptS.groupBy(GroupBy).avg("aos_spend").avg("aos_impressions").finish();
		while (aos_base_skupoprptS.hasNext()) {
			Row aos_base_skupoprpt = aos_base_skupoprptS.next();
			Map<String, Object> Info = new HashMap<>();
			Info.put("aos_spend", aos_base_skupoprpt.get("aos_spend"));
			Info.put("aos_impressions", aos_base_skupoprpt.get("aos_impressions"));
			SkuRpt3Avg.put(aos_base_skupoprpt.getLong("aos_orgid") + "~" + aos_base_skupoprpt.getLong("aos_itemid"),
					Info);

			FndMsg.debug(aos_base_skupoprpt.get("aos_spend"));
			FndMsg.debug(aos_base_skupoprpt.get("aos_impressions"));

		}
		aos_base_skupoprptS.close();
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
