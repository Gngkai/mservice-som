package mkt.popular.adjust_s;

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

public class aos_mkt_popadds_bill extends AbstractBillPlugIn   implements ItemClickListener  {

	private static final String DB_MKT = "aos.mkt";
	
	public void registerListener(EventObject e) {
		super.registerListener(e);
		this.addItemClickListeners("aos_toolbarap");// 给工具栏加监听事件
		this.addItemClickListeners("aos_submit"); // 提交
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String Control = evt.getItemKey();
		try {
			if ("aos_submit".equals(Control))
				aos_submit();// 提交
		} catch (FndError FndError) {
			this.getView().showTipNotification(FndError.getErrorMessage());
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
		}
	}

	private void aos_submit() throws FndError {
		SaveControl(); 
		SyncPPC();
		this.getModel().setValue("aos_status","B");
		this.getView().invokeOperation("save");
	}

	private void SyncPPC() throws FndError {
		DynamicObjectCollection aos_entryentityS = this.getModel().getEntryEntity("aos_entryentity");
		Object aos_ppcid = this.getModel().getValue("aos_ppcid");
		for (DynamicObject aos_entryentity : aos_entryentityS) {
			if (aos_entryentity.getBoolean("aos_add")) {
				Object aos_lastbid = aos_entryentity.get("aos_lastbid");
				String aos_itemnumer = aos_entryentity.getString("aos_itemnumer");
				SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");
				String today = DF.format(new Date());
				String sql = " UPDATE tk_aos_mkt_popular_ppc_r r " + " SET fk_aos_bid = ? , "
						+ " fk_aos_salemanual = 1 ," + " fk_aos_add = 1 ," + " fk_aos_groupstatus = 'AVAILABLE', "
						+ " fk_aos_serialstatus = 'AVAILABLE', "
						+ " fk_aos_roiflag = 0,"
						+ " fk_aos_special = 1,  " + " fk_aos_lastpricedate =  '" + today + "'  "
						+ " WHERE 1=1 " + " and r.fid = ? " + " and fk_aos_itemnumer = ?  ";
				Object[] params2 = { aos_lastbid, aos_ppcid, aos_itemnumer };
				DB.execute(DBRoute.of(DB_MKT), sql, params2);
			}
		}
	}

	private void SaveControl() throws FndError {
		String ErrorMessage = "";
		DynamicObjectCollection aos_entryentityS = this.getModel().getEntryEntity("aos_entryentity");
		for (DynamicObject aos_entryentity : aos_entryentityS) {
			if (aos_entryentity.getBoolean("aos_add") && Cux_Common_Utl.IsNull(aos_entryentity.get("aos_reason"))) {
				String aos_itemnumer = aos_entryentity.getString("aos_itemnumer");
				ErrorMessage = FndError.AddErrorMessage(ErrorMessage, aos_itemnumer + "加回原因必填!");
				FndError fndMessage = new FndError(ErrorMessage);
				throw fndMessage;
			}
		}
	}

	public void beforeClosed(BeforeClosedEvent e) {
		super.beforeClosed(e);
		e.setCheckDataChange(false);
	}

}