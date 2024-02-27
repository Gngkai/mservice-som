package mkt.synciface;

import common.fnd.FndDate;
import common.fnd.FndError;
import common.fnd.FndMsg;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

/**
 * @author aosom
 * @since 2024/2/27 15:22
 */
public class AosMktSyncList extends AbstractListPlugin {
    public final static String AOS_REDATE_ORDER = "aos_redate_order";
    public final static String AOS_REDATE_QUO = "aos_redate_quo";
    public final static String AOS_REDATE_TRAN = "aos_redate_tran";
    public final static String AOS_REDATE_COST = "aos_redate_cost";

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String itemKey = evt.getItemKey();
        try {
            if (AOS_REDATE_ORDER.equals(itemKey)) {
                refreshDateOrder();
            } else if (AOS_REDATE_QUO.equals(itemKey)) {
                refreshDateQuote();
            } else if (AOS_REDATE_TRAN.equals(itemKey)) {
                refreshDateTran();
            } else if (AOS_REDATE_COST.equals(itemKey)) {
                refreshDateCost();
            }
        } catch (FndError error) {
            this.getView().showMessage(error.getErrorMessage());
        } catch (Exception e) {
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            this.getView().showMessage(writer.toString());
            e.printStackTrace();
        }
    }

    private void refreshDateCost() {
        FndMsg.debug("=========into refreshDateCost=========");
        Date yesterday = FndDate.add_days(FndDate.zero(new Date()), -1);
        DynamicObject[] costS = BusinessDataServiceHelper.load("aos_sync_invcost", "aos_creation_date",
            new QFilter("aos_creation_date", QCP.large_equals, yesterday).toArray());
        for (DynamicObject cost : costS) {
            cost.set("aos_creation_date", new Date());
        }
        SaveServiceHelper.save(costS);
    }

    private void refreshDateTran() {
        FndMsg.debug("=========into refreshDateTran=========");
        Date yesterday = FndDate.add_days(FndDate.zero(new Date()), -1);
        DynamicObject[] tranS = BusinessDataServiceHelper.load("aos_sync_inv_trans", "aos_trans_date",
            new QFilter("aos_trans_date", QCP.large_equals, yesterday).toArray());
        for (DynamicObject tran : tranS) {
            tran.set("aos_trans_date", new Date());
        }
        SaveServiceHelper.save(tranS);
    }

    private void refreshDateQuote() {
        FndMsg.debug("=========into refreshDateQuote=========");
        Date yesterday = FndDate.add_days(FndDate.zero(new Date()), -1);
        DynamicObject[] quoS = BusinessDataServiceHelper.load("aos_sal_quoteattr", "aos_quo_date",
            new QFilter("aos_quo_date", QCP.large_equals, yesterday).toArray());
        for (DynamicObject quo : quoS) {
            quo.set("aos_quo_date", new Date());
        }
        SaveServiceHelper.save(quoS);
    }

    /**
     * 更新日期
     */
    private void refreshDateOrder() {
        FndMsg.debug("=========into refreshDateOrder=========");
        Date yesterday = FndDate.add_days(FndDate.zero(new Date()), -1);
        DynamicObject[] orderS = BusinessDataServiceHelper.load("aos_sync_om_order_r", "aos_local_date,aos_order_date",
            new QFilter("aos_local_date", QCP.large_equals, yesterday).toArray());
        for (DynamicObject order : orderS) {
            order.set("aos_local_date", new Date());
            order.set("aos_order_date", new Date());
        }
        SaveServiceHelper.save(orderS);

    }
}
