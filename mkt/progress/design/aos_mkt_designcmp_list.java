package mkt.progress.design;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import common.fnd.FndError;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QFilter;
import mkt.progress.iface.parainfo;

public class aos_mkt_designcmp_list extends AbstractListPlugin {
	@Override
	public void setFilter(SetFilterEvent e) {
		List<QFilter> qFilters = e.getQFilters();
		parainfo.setRightsForDesign(qFilters,this.getPageCache());
	}

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
}
