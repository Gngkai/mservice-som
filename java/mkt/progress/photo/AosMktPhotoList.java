package mkt.progress.photo;

import java.util.EventObject;

import common.Cux_Common_Utl;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.control.events.RowClickEventListener;
import kd.bos.form.events.HyperLinkClickArgs;
import kd.bos.form.events.HyperLinkClickEvent;
import kd.bos.form.events.HyperLinkClickListener;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;

/**
 * @author aosom 拍照任务清单-列表插件
 */
public class AosMktPhotoList extends AbstractListPlugin
    implements ItemClickListener, HyperLinkClickListener, RowClickEventListener {

    public final static String AOS_PHOTOURL = "aos_photourl";
    public final static String AOS_VEDIOURL = "aos_vediourl";

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
    }

    @Override
    public void billListHyperLinkClick(HyperLinkClickArgs hyperLinkClickEvent) {
        String fieldName = hyperLinkClickEvent.getFieldName();
        hyperLinkClickEvent.setCancel(true);
        if (AOS_PHOTOURL.equals(fieldName)) {
            Object fid = getFocusRowPkId();
            QFilter filterBillno = new QFilter("aos_sourceid", QCP.equals, fid);
            QFilter filterType = new QFilter("aos_type", QCP.equals, "拍照").or("aos_type", QCP.equals, "");
            QFilter[] filters = new QFilter[] {filterBillno, filterType};
            DynamicObject aosMktPhotoreq = QueryServiceHelper.queryOne("aos_mkt_photoreq", "id", filters);
            if (aosMktPhotoreq != null) {
                Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_photoreq", aosMktPhotoreq.get("id"));
            }
        } else if (AOS_VEDIOURL.equals(fieldName)) {
            Object fid = getFocusRowPkId();
            QFilter filterBillno = new QFilter("aos_sourceid", QCP.equals, fid);
            QFilter filterType = new QFilter("aos_type", QCP.equals, "视频").or("aos_type", QCP.equals, "");
            QFilter[] filters = new QFilter[] {filterBillno, filterType};
            DynamicObject aosMktPhotoreq = QueryServiceHelper.queryOne("aos_mkt_photoreq", "id", filters);
            if (aosMktPhotoreq != null) {
                Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_photoreq", aosMktPhotoreq.get("id"));
            }
        }
    }

    @Override
    public void hyperLinkClick(HyperLinkClickEvent arg0) {
        // TODO Auto-generated method stub
    }
}