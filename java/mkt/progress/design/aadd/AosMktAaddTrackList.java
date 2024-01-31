package mkt.progress.design.aadd;

import common.fnd.FndMsg;
import common.fnd.data.InvData;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.form.events.HyperLinkClickArgs;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.orm.query.QFilter;

/**
 * @author aosom
 * @version 高级A+进度表-列表插件
 */
public class AosMktAaddTrackList extends AbstractListPlugin {
    /**
     * US111
     */
    public final static String AOS_US = "aos_us";
    public final static String AOS_CA = "aos_ca";
    public final static String AOS_UK = "aos_uk";
    public final static String AOS_DE = "aos_de";
    public final static String AOS_FR = "aos_fr";
    public final static String AOS_IT = "aos_it";
    public final static String AOS_ES = "aos_es";

    @Override
    public void billListHyperLinkClick(HyperLinkClickArgs hyperLinkClickEvent) {
        String fieldName = hyperLinkClickEvent.getFieldName();
        hyperLinkClickEvent.setCancel(true);
        DynamicObject aosMktAddtrack = QueryServiceHelper.queryOne("aos_mkt_addtrack", "aos_itemid.id",
            new QFilter("id", QCP.equals, this.getFocusRowPkId().toString()).toArray());
        String itemId = aosMktAddtrack.getString(0);
        if (AOS_US.equals(fieldName)) {
            String url = "https://www.amazon.com/dp/" + InvData.getInvAsin("US", itemId);
            FndMsg.debug("url:" + url);
            this.getView().openUrl(url);
        } else if (AOS_CA.equals(fieldName)) {
            String url = "https://www.amazon.ca/dp/" + InvData.getInvAsin("CA", itemId);
            FndMsg.debug("url:" + url);
            this.getView().openUrl(url);
        } else if (AOS_UK.equals(fieldName)) {
            String url = "https://www.amazon.co.uk/dp/" + InvData.getInvAsin("UK", itemId);
            FndMsg.debug("url:" + url);
            this.getView().openUrl(url);
        } else if (AOS_DE.equals(fieldName)) {
            String url = "https://www.amazon.de/dp/" + InvData.getInvAsin("DE", itemId);
            FndMsg.debug("url:" + url);
            this.getView().openUrl(url);
        } else if (AOS_FR.equals(fieldName)) {
            String url = "https://www.amazon.fr/dp/" + InvData.getInvAsin("FR", itemId);
            FndMsg.debug("url:" + url);
            this.getView().openUrl(url);
        } else if (AOS_IT.equals(fieldName)) {
            String url = "https://www.amazon.it/dp/" + InvData.getInvAsin("IT", itemId);
            FndMsg.debug("url:" + url);
            this.getView().openUrl(url);
        } else if (AOS_ES.equals(fieldName)) {
            String url = "https://www.amazon.es/dp/" + InvData.getInvAsin("ES", itemId);
            FndMsg.debug("url:" + url);
            this.getView().openUrl(url);
        }
    }
}
