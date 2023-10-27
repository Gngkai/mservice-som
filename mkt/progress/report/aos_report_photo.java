package mkt.progress.report;

import common.Cux_Common_Utl;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.epm.eb.formplugin.AbstractListPlugin;
import common.sal.util.QFBuilder;
import java.util.*;

/**
 * @Author Zd
 * @Date 2023/4/1 13:34
 * @Action 拍照需求流程报表
 */

public class aos_report_photo extends AbstractListPlugin {
    public void afterDoOperation(AfterDoOperationEventArgs eventArgs) {
        super.afterDoOperation(eventArgs);
        String operateKey = eventArgs.getOperateKey();
        if (operateKey.equals("aos_query")) {
            try {
                select();
                this.getView().updateView("aos_entryentity");
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    void select() {
        this.getModel().deleteEntryData("aos_entryentity");
        List<String> list_fildes = Arrays.asList("billno", "aos_itemid","aos_itemname", "aos_newitem", "aos_newvendor", "aos_vendor",
                "aos_ponumber","aos_orgtext","aos_photoflag","aos_vedioflag","aos_type","aos_phstate","aos_samplestatus",
                "billstatus","aos_returntimes","aos_user","aos_urgent","aos_shipdate","aos_arrivaldate","aos_overseasdate",
                "aos_sampledate","aos_installdate", "aos_whitedate","aos_actdate","aos_follower","aos_developer","aos_whiteph",
                "aos_actph","aos_vedior");
        StringJoiner str = new StringJoiner(",");
        for (String filde : list_fildes) {
            str.add(filde);
        }

        //查询
        QFBuilder qfBuilder = getOperateFiltes();
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_photoreq", str.toString(), qfBuilder.toArray());
        DynamicObjectCollection entryentity = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity");

        for (DynamicObject dy_row : dyc) {
            int row = this.getModel().createNewEntryRow("aos_entryentity");
            this.getModel().setValue("billno",dy_row.get("billno"),row);
            this.getModel().setValue("aos_itemid",dy_row.get("aos_itemid"),row);
            this.getModel().setValue("aos_itemname",dy_row.get("aos_itemname"),row);
            this.getModel().setValue("aos_newitem",dy_row.get("aos_newitem"),row);
            this.getModel().setValue("aos_newvendor",dy_row.get("aos_newvendor"),row);
            this.getModel().setValue("aos_vendor",dy_row.get("aos_vendor"),row);
            this.getModel().setValue("aos_ponumber",dy_row.get("aos_ponumber"),row);
            this.getModel().setValue("aos_orgtext",dy_row.get("aos_orgtext"),row);
            this.getModel().setValue("aos_photoflag",dy_row.get("aos_photoflag"),row);
            this.getModel().setValue("aos_vedioflag",dy_row.get("aos_vedioflag"),row);
            this.getModel().setValue("aos_type",dy_row.get("aos_type"),row);
            this.getModel().setValue("aos_phstate",dy_row.get("aos_phstate"),row);
            this.getModel().setValue("aos_samplestatus",dy_row.get("aos_samplestatus"),row);
            this.getModel().setValue("aos_billstatus",dy_row.get("billstatus"),row);
            this.getModel().setValue("aos_returntimes",dy_row.get("aos_returntimes"),row);
            this.getModel().setValue("aos_user",dy_row.get("aos_user"),row);
            this.getModel().setValue("aos_urgent",dy_row.get("aos_urgent"),row);
            this.getModel().setValue("aos_shipdate",dy_row.get("aos_shipdate"),row);
            this.getModel().setValue("aos_arrivaldate",dy_row.get("aos_arrivaldate"),row);
            this.getModel().setValue("aos_overseasdate",dy_row.get("aos_overseasdate"),row);
            this.getModel().setValue("aos_sampledate",dy_row.get("aos_sampledate"),row);
            this.getModel().setValue("aos_installdate",dy_row.get("aos_installdate"),row);
            this.getModel().setValue("aos_whitedate",dy_row.get("aos_whitedate"),row);
            this.getModel().setValue("aos_actdate",dy_row.get("aos_actdate"),row);
            this.getModel().setValue("aos_follower",dy_row.get("aos_follower"),row);
            this.getModel().setValue("aos_developer",dy_row.get("aos_developer"),row);
            this.getModel().setValue("aos_whiteph",dy_row.get("aos_whiteph"),row);
            this.getModel().setValue("aos_actph",dy_row.get("aos_actph"),row);
            this.getModel().setValue("aos_vedior",dy_row.get("aos_vedior"),row);
        }
    }

        private QFBuilder getOperateFiltes(){
            QFBuilder qfBuilder = new QFBuilder();
            DynamicObject aos_materielfield = (DynamicObject) this.getModel().getValue("aos_huoid");
            if (!Cux_Common_Utl.IsNull(aos_materielfield)) {
                qfBuilder.add("aos_itemid", "=", aos_materielfield.get("id"));
            }

            Object aos_pname = this.getModel().getValue("aos_pname");
            if(!Cux_Common_Utl.IsNull(aos_pname)){
                qfBuilder.add("aos_itemname","=",aos_pname);
            }

            Object aos_photoplace = this.getModel().getValue("aos_photoplace");
            if(!Cux_Common_Utl.IsNull(aos_photoplace)){
                qfBuilder.add("aos_phstate","=",aos_photoplace);
            }
            return qfBuilder;
        }

    }
