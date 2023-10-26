package mkt.progress.design.aadd;

import common.fnd.FndMsg;
import common.fnd.data.InvData;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.form.events.HyperLinkClickArgs;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.orm.query.QFilter;

public class AosMktAAddTrackList extends AbstractListPlugin {
    @Override
    public void billListHyperLinkClick(HyperLinkClickArgs hyperLinkClickEvent) {
        String FieldName = hyperLinkClickEvent.getFieldName();
        hyperLinkClickEvent.setCancel(true);
        DynamicObject aos_mkt_addtrack = QueryServiceHelper.queryOne("aos_mkt_addtrack", "aos_itemid.id",
                new QFilter("id", QCP.equals, this.getFocusRowPkId().toString()).toArray());
        String itemId = aos_mkt_addtrack.getString(0);
        if ("aos_us".equals(FieldName)) {
            String url = "https://www.amazon.com/dp/" + InvData.getInvAsin("US", itemId);
            FndMsg.debug("url:" + url);
            this.getView().openUrl(url);
        } else if ("aos_ca".equals(FieldName)) {
            String url = "https://www.amazon.ca/dp/" + InvData.getInvAsin("CA", itemId);
            FndMsg.debug("url:" + url);
            this.getView().openUrl(url);
        } else if ("aos_uk".equals(FieldName)) {
            String url = "https://www.amazon.co.uk/dp/" + InvData.getInvAsin("UK", itemId);
            FndMsg.debug("url:" + url);
            this.getView().openUrl(url);
        } else if ("aos_de".equals(FieldName)) {
            String url = "https://www.amazon.de/dp/" + InvData.getInvAsin("DE", itemId);
            FndMsg.debug("url:" + url);
            this.getView().openUrl(url);
        } else if ("aos_fr".equals(FieldName)) {
            String url = "https://www.amazon.fr/dp/" + InvData.getInvAsin("FR", itemId);
            FndMsg.debug("url:" + url);
            this.getView().openUrl(url);
        } else if ("aos_it".equals(FieldName)) {
            String url = "https://www.amazon.it/dp/" + InvData.getInvAsin("IT", itemId);
            FndMsg.debug("url:" + url);
            this.getView().openUrl(url);
        } else if ("aos_es".equals(FieldName)) {
            String url = "https://www.amazon.es/dp/" + InvData.getInvAsin("ES", itemId);
            FndMsg.debug("url:" + url);
            this.getView().openUrl(url);
        }
    }
}
