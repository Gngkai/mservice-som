package mkt.progress.design.aadd;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import common.fnd.FndError;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QFilter;
import mkt.progress.iface.parainfo;

public class aos_mkt_aadd_list extends AbstractListPlugin {
	public final static String aos_mkt_listing_min = "aos_mkt_listing_min";

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String itemKey = evt.getItemKey();
		try {
			if ("aos_showclose".equals(itemKey))
				parainfo.showClose(this.getView());// 查询关闭流程
		} catch (FndError error) {
			this.getView().showMessage(error.getErrorMessage());
		} catch (Exception e) {
			StringWriter writer = new StringWriter();
			e.printStackTrace(new PrintWriter(writer));
			this.getView().showMessage(writer.toString());
			e.printStackTrace();
		}
	}
	
	
	@Override
	public void setFilter(SetFilterEvent e) {
		List<QFilter> qFilters = e.getQFilters();
		parainfo.setRights(qFilters, this.getPageCache(), "aos_mkt_aadd");
	}
}
