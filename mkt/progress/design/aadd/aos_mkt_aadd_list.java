package mkt.progress.design.aadd;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;

import common.fnd.FndError;
import kd.bos.entity.datamodel.ListSelectedRow;
import kd.bos.entity.datamodel.ListSelectedRowCollection;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.list.IListView;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QFilter;
import mkt.progress.ProgressUtil;
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
			else if ("aos_submit".equals(itemKey)) // 提交
				aos_submit();
		} catch (FndError error) {
			this.getView().showMessage(error.getErrorMessage());
		} catch (Exception e) {
			StringWriter writer = new StringWriter();
			e.printStackTrace(new PrintWriter(writer));
			this.getView().showMessage(writer.toString());
			e.printStackTrace();
		}
	}

	private void aos_submit() {

		ListSelectedRowCollection selectedRows = this.getSelectedRows();
		int size = selectedRows.size();
		IListView view = (IListView) this.getView();
		String billFormId = view.getBillFormId();
		Iterator<ListSelectedRow> iterator = selectedRows.iterator();
		StringBuilder builder = new StringBuilder("");

		if (size == 0) {
			this.getView().showTipNotification("请先选择提交数据");
		} else {
			String message = ProgressUtil.submitEntity(view, selectedRows);
			if (message != null && message.length() > 0) {
				message = "提交成功,部分无权限单据无法提交：  " + message;
			} else
				message = "提交成功";
			this.getView().updateView();
			if (builder.toString().length() > 0) {
				this.getView().showSuccessNotification(message + builder.toString());
			} else {
				this.getView().showSuccessNotification(message);
			}
		}
	}


	@Override
	public void setFilter(SetFilterEvent e) {
		List<QFilter> qFilters = e.getQFilters();
		parainfo.setRights(qFilters, this.getPageCache(), "aos_mkt_aadd");
	}
}
