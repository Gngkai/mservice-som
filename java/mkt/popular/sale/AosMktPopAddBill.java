package mkt.popular.sale;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EventObject;

import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.sal.util.SalUtil;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.db.DB;
import kd.bos.db.DBRoute;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.events.BeforeClosedEvent;

/**
 * @author aosom
 * @version 销售加回-表单插件
 */
public class AosMktPopAddBill extends AbstractBillPlugIn implements ItemClickListener {
    private static final String DB_MKT = "aos.mkt";
    private static final String AOS_SUBMIT = "aos_submit";

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        // 给工具栏加监听事件
        this.addItemClickListeners("aos_toolbarap");
        // 提交
        this.addItemClickListeners("aos_submit");
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String control = evt.getItemKey();
        try {
            if (AOS_SUBMIT.equals(control)) {
                // 提交
                aosSubmit();
            }
        } catch (FndError fndError) {
            this.getView().showTipNotification(fndError.getErrorMessage());
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
        }
    }

    private void aosSubmit() throws FndError {
        saveControl();
        syncPpc();
        this.getModel().setValue("aos_status", "B");
        this.getView().invokeOperation("save");
    }

    private void syncPpc() throws FndError {
        DynamicObjectCollection aosEntryentityS = this.getModel().getEntryEntity("aos_entryentity");
        Object aosPpcid = this.getModel().getValue("aos_ppcid");
        for (DynamicObject aosEntryentity : aosEntryentityS) {
            if (aosEntryentity.getBoolean("aos_add")) {
                Object aosLastbid = aosEntryentity.get("aos_lastbid");
                String aosItemnumer = aosEntryentity.getString("aos_itemnumer");
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
                String today = df.format(new Date());
                String sql = " UPDATE tk_aos_mkt_popular_ppc_r r " + " SET fk_aos_bid = ? , "
                    + " fk_aos_salemanual = 1 ," + " fk_aos_add = 1 ," + " fk_aos_groupstatus = 'AVAILABLE', "
                    + " fk_aos_serialstatus = 'AVAILABLE', " + " fk_aos_roiflag = 0," + " fk_aos_special = 1,  "
                    + " fk_aos_lastpricedate =  '" + today + "'  " + " WHERE 1=1 " + " and r.fid = ? "
                    + " and fk_aos_itemnumer = ?  ";
                Object[] params2 = {aosLastbid, aosPpcid, aosItemnumer};
                DB.execute(DBRoute.of(DB_MKT), sql, params2);
            }
        }
    }

    private void saveControl() throws FndError {
        DynamicObjectCollection aosEntryentityS = this.getModel().getEntryEntity("aos_entryentity");
        for (DynamicObject aosEntryentity : aosEntryentityS) {
            if (aosEntryentity.getBoolean("aos_add") && Cux_Common_Utl.IsNull(aosEntryentity.get("aos_reason"))) {
                String aosItemnumer = aosEntryentity.getString("aos_itemnumer");
                throw new FndError(aosItemnumer + "加回原因必填!");
            }
        }
    }

    @Override
    public void beforeClosed(BeforeClosedEvent e) {
        super.beforeClosed(e);
        e.setCheckDataChange(false);
    }
}