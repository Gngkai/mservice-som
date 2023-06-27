package mkt.progress.photo;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.EventObject;

import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndMsg;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.control.events.RowClickEventListener;
import kd.bos.form.events.HyperLinkClickArgs;
import kd.bos.form.events.HyperLinkClickEvent;
import kd.bos.form.events.HyperLinkClickListener;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;

public class aos_mkt_photolist_list extends AbstractListPlugin
		implements ItemClickListener, HyperLinkClickListener, RowClickEventListener {

	@Override
	public void setFilter(SetFilterEvent e) {
    }

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String itemKey = evt.getItemKey();
		try {
			if ("aos_select".equals(itemKey))
				aos_select();
		} catch (FndError error) {
			this.getView().showMessage(error.getErrorMessage());
		} catch (Exception e) {
			StringWriter writer = new StringWriter();
			e.printStackTrace(new PrintWriter(writer));
			this.getView().showMessage(writer.toString());
			e.printStackTrace();
		}
	}
	
	private void aos_select() {
		FndMsg.debug("into aos_select");
		
	}

	@Override
	public void registerListener(EventObject e) {
		super.registerListener(e);
	}

	@Override
	public void billListHyperLinkClick(HyperLinkClickArgs hyperLinkClickEvent) {
		String FieldName = hyperLinkClickEvent.getFieldName();
		hyperLinkClickEvent.setCancel(true);
		System.out.println("FieldName =" + FieldName);
		if ("aos_photourl".equals(FieldName)) {
			Object fid = getFocusRowPkId();
			System.out.println(fid);
			QFilter filter_billno = new QFilter("aos_sourceid", QCP.equals, fid);
			QFilter filter_type = new QFilter("aos_type", QCP.equals, "拍照").or("aos_type", QCP.equals, "");
			QFilter[] filters = new QFilter[] { filter_billno, filter_type };
			DynamicObject aos_mkt_photoreq = QueryServiceHelper.queryOne("aos_mkt_photoreq", "id", filters);
			if (aos_mkt_photoreq != null)
				Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_photoreq", aos_mkt_photoreq.get("id"));
		} else if ("aos_vediourl".equals(FieldName)) {
			Object fid = getFocusRowPkId();
			System.out.println(fid);
			QFilter filter_billno = new QFilter("aos_sourceid", QCP.equals, fid);
			QFilter filter_type = new QFilter("aos_type", QCP.equals, "视频").or("aos_type", QCP.equals, "");
			QFilter[] filters = new QFilter[] { filter_billno, filter_type };
			DynamicObject aos_mkt_photoreq = QueryServiceHelper.queryOne("aos_mkt_photoreq", "id", filters);
			if (aos_mkt_photoreq != null)
				Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_photoreq", aos_mkt_photoreq.get("id"));
		}
	}

	@Override
	public void hyperLinkClick(HyperLinkClickEvent arg0) {
		// TODO Auto-generated method stub

	}

}