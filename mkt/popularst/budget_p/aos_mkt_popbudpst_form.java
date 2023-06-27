package mkt.popularst.budget_p;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EventObject;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.db.DB;
import kd.bos.db.DBRoute;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.control.Control;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.control.events.RowClickEvent;
import kd.bos.form.control.events.RowClickEventListener;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;

public class aos_mkt_popbudpst_form extends AbstractFormPlugin implements ItemClickListener, RowClickEventListener {

	private static final String DB_MKT = "aos.mkt";

	public void registerListener(EventObject e) {
		super.registerListener(e);

		EntryGrid aos_budgetentry = this.getControl("aos_budgetentry");
		aos_budgetentry.addRowClickListener(this);
		// 给工具栏加监听事件
		this.addItemClickListeners("aos_toolbarap");
		this.addItemClickListeners("aos_select");
		this.addItemClickListeners("aos_apply");
		this.addItemClickListeners("aos_save");
	}

	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String Control = evt.getItemKey();
		if (Control.equals("aos_select"))
			aos_select();
		else if (Control.equals("aos_apply")) {
			aos_apply();
		} else if (Control.equals("aos_save")) {
			aos_save();
		} else if (Control.equals("aos_confirm")) {
			aos_confirm();
		}

	}

	private void aos_confirm() {
		Object aos_ppcid = this.getView().getParentView().getModel().getValue("aos_ppcid");
		DynamicObject aos_mkt_popular_ppc = BusinessDataServiceHelper.loadSingle(aos_ppcid, "aos_mkt_pop_ppcst");
		aos_mkt_popular_ppc.set("aos_budgetp", true);
		OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_pop_ppcst",
				new DynamicObject[] { aos_mkt_popular_ppc }, OperateOption.create());
		if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
		}
		this.getView().getParentView().getModel().setValue("aos_status", "B");
		this.getView().getParentView().invokeOperation("save");
		this.getView().showSuccessNotification("确认成功！");
		this.getView().setEnable(false, "aos_budgetentry");
		this.getView().setEnable(false, "aos_apply");
		this.getView().setEnable(false, "aos_save");
		this.getView().setEnable(false, "aos_confirm");
	}

	private void aos_save() {
		int RowCount = this.getModel().getEntryRowCount("aos_budgetentry");
		Object aos_ppcid = this.getView().getParentView().getModel().getValue("aos_ppcid");
		for (int i = 0; i < RowCount; i++) {
			
			BigDecimal aos_popbudget = (BigDecimal) this.getModel().getValue("aos_popbudget", i);
			String aos_saldescription = (String) this.getModel().getValue("aos_saldescription", i);
			long aos_entryid = (long) this.getModel().getValue("aos_entryid", i);
			String aos_serialname = (String) this.getModel().getValue("aos_serialname", i);
			
			Update(aos_entryid, aos_popbudget, aos_saldescription, aos_serialname, aos_ppcid);
		}
		this.getView().showSuccessNotification("保存成功!");
	}

	private void Update(long aos_entryid, BigDecimal aos_adjprice, String aos_description, String aos_serialname,
			Object aos_ppcid) {
		String sql = " UPDATE tk_aos_mkt_popbud_stdt_r r " + " SET fk_aos_popbudget = ?," + " fk_aos_saldescription = ? "
				+ " WHERE 1=1 " + " and r.FEntryId = ? ";
		Object[] params = { aos_adjprice, aos_description, aos_entryid };
		DB.execute(DBRoute.of(DB_MKT), sql, params);

		sql = " UPDATE tk_aos_mkt_pop_ppcst_r r " + " SET fk_aos_budget = ?" + " WHERE 1=1 "
				+ " and r.fk_aos_productno = ? and r.fid = ?  ";
		Object[] params2 = { aos_adjprice, aos_serialname, aos_ppcid };
		DB.execute(DBRoute.of(DB_MKT), sql, params2);
	}

	private void aos_apply() {
		Object aos_adjrate = this.getModel().getValue("aos_adjrate");
		if (aos_adjrate != null) {
			EntryGrid aos_detailentry = this.getControl("aos_budgetentry");
			int[] SelectRows = aos_detailentry.getSelectRows();
			int RowCount = SelectRows.length;
			for (int i = 0; i < RowCount; i++) {
				BigDecimal aos_lastbudget = (BigDecimal) this.getModel().getValue("aos_lastbudget", SelectRows[i]);
				this.getModel().setValue("aos_popbudget", aos_lastbudget.multiply((BigDecimal) aos_adjrate), SelectRows[i]);
			}
		}
		this.getView().showSuccessNotification("应用成功!");
	}

	private void aos_select() {
		aos_save();//筛选前自动保存
		
		Object aos_channel_q = this.getModel().getValue("aos_channel_q");
		Object aos_poptype_q = this.getModel().getValue("aos_poptype_q");
		Object aos_seasonid = this.getModel().getValue("aos_seasonid");
		Object aos_roifrom = this.getModel().getValue("aos_roifrom");
		Object aos_roito = this.getModel().getValue("aos_roito");
		Object aos_ratefrom = this.getModel().getValue("aos_ratefrom");
		Object aos_rateto = this.getModel().getValue("aos_rateto");
		Object aos_lastfrom = this.getModel().getValue("aos_lastfrom");
		Object aos_lastto = this.getModel().getValue("aos_lastto");
		Object aos_budgetfrom = this.getModel().getValue("aos_budgetfrom");
		Object aos_budgetto = this.getModel().getValue("aos_budgetto");
		Object aos_description_q = this.getModel().getValue("aos_description_q");

		QFilter filter_channel = null;
		QFilter filter_poptype = null;
		QFilter filter_seasonid = null;
		QFilter filter_roifrom = null;
		QFilter filter_roito = null;
		QFilter filter_ratefrom = null;
		QFilter filter_rateto = null;
		QFilter filter_spendfrom = null;
		QFilter filter_spendto = null;
		QFilter filter_budgetfrom = null;
		QFilter filter_budgetto = null;
		QFilter filter_desc = null;

		if (aos_channel_q != null && aos_channel_q.toString().length() > 0)
			filter_channel = new QFilter("aos_budgetentry.aos_channel_r", "=", aos_channel_q);
		if (aos_poptype_q != null && aos_poptype_q.toString().length() > 0)
			filter_poptype = new QFilter("aos_budgetentry.aos_poptype_r", "=",
					((DynamicObject) aos_poptype_q).getPkValue());
		if (aos_seasonid != null && aos_seasonid.toString().length() > 0)
			filter_seasonid = new QFilter("aos_budgetentry.aos_season_r", "=",
					((DynamicObject) aos_seasonid).getPkValue());
		if (aos_roifrom != null && aos_roifrom.toString().length() > 0)
			filter_roifrom = new QFilter("aos_budgetentry.aos_roi7days", ">=", aos_roifrom);
		if (aos_roito != null && aos_roito.toString().length() > 0)
			filter_roito = new QFilter("aos_budgetentry.aos_roi7days", "<=", aos_roito);
		if (aos_ratefrom != null && aos_ratefrom.toString().length() > 0)
			filter_ratefrom = new QFilter("aos_budgetentry.aos_lastrate", ">=", aos_ratefrom);
		if (aos_rateto != null && aos_rateto.toString().length() > 0)
			filter_rateto = new QFilter("aos_budgetentry.aos_lastrate", "<=", aos_rateto);
		if (aos_lastfrom != null && aos_lastfrom.toString().length() > 0)
			filter_spendfrom = new QFilter("aos_budgetentry.aos_lastspend_r", ">=", aos_lastfrom);
		if (aos_lastto != null && aos_lastto.toString().length() > 0)
			filter_spendto = new QFilter("aos_budgetentry.aos_lastspend_r", "<=", aos_lastto);
		if (aos_budgetfrom != null && aos_budgetfrom.toString().length() > 0)
			filter_budgetfrom = new QFilter("aos_budgetentry.aos_lastbudget", ">=", aos_budgetfrom);
		if (aos_budgetto != null && aos_budgetto.toString().length() > 0)
			filter_budgetto = new QFilter("aos_budgetentry.aos_lastbudget", "<=", aos_budgetto);
		if (aos_description_q != null && aos_description_q.toString().length() > 0)
			filter_desc = new QFilter("aos_budgetentry.aos_saldescription", "not like ", "%" + aos_description_q + "%");

		ArrayList<QFilter> arr_q = new ArrayList<QFilter>();
		arr_q.add(filter_channel);
		arr_q.add(filter_poptype);
		arr_q.add(filter_seasonid);
		arr_q.add(filter_roifrom);
		arr_q.add(filter_roito);
		arr_q.add(filter_ratefrom);
		arr_q.add(filter_rateto);
		arr_q.add(filter_spendfrom);
		arr_q.add(filter_spendto);
		arr_q.add(filter_budgetfrom);
		arr_q.add(filter_budgetto);
		arr_q.add(filter_desc);
		System.out.println(arr_q);
		InitData(arr_q);
	}

	public void afterCreateNewData(EventObject e) {
		InitData(null);// 初始化数据
	}

	private void InitData(ArrayList<QFilter> arr_q) {
		this.getModel().deleteEntryData("aos_budgetentry");
		Object fid = this.getView().getParentView().getModel().getDataEntity().getPkValue();
		QFilter Qf_id = new QFilter("aos_sourceid", "=", fid);
		QFilter[] filters = null;
		if (arr_q == null)
			filters = new QFilter[] { Qf_id };
		else {
			arr_q.add(Qf_id);
			filters = arr_q.toArray(new QFilter[arr_q.size()]);
		}
		String SelectField = "aos_budgetentry.id aos_entryid,"
				+ "aos_budgetentry.aos_channel_r aos_channel_r,"
				+ "aos_budgetentry.aos_poptype_r aos_poptype_r,"
				+ "aos_budgetentry.aos_serialname aos_serialname,"
				+ "aos_budgetentry.aos_season_r aos_season_r,"
				+ "aos_budgetentry.aos_roi7days aos_roi7days,"
				+ "aos_budgetentry.aos_roitype aos_roitype,"
				+ "aos_budgetentry.aos_skucount aos_skucount,"
				+ "aos_budgetentry.aos_kwcount aos_kwcount,"
				+ "aos_budgetentry.aos_lastrate aos_lastrate,"
				+ "aos_budgetentry.aos_lastspend_r aos_lastspend_r,"
				+ "aos_budgetentry.aos_lastbudget aos_lastbudget,"
				+ "aos_budgetentry.aos_calbudget aos_calbudget,"
				+ "aos_budgetentry.aos_adjbudget aos_adjbudget,"
				+ "aos_budgetentry.aos_saldescription aos_saldescription,"
				+ "aos_budgetentry.aos_popbudget aos_popbudget,"
				+ "aos_budgetentry.aos_adjustrate aos_adjustrate";
		DynamicObjectCollection aos_mkt_popbudget_dataS = QueryServiceHelper.query("aos_mkt_popbudget_stdt",
				SelectField, filters, "aos_budgetentry.aos_serialname");
		int i = 0;
		for (DynamicObject aos_mkt_popbudget_data : aos_mkt_popbudget_dataS) {
			this.getModel().batchCreateNewEntryRow("aos_budgetentry", 1);
			this.getModel().setValue("aos_entryid", aos_mkt_popbudget_data.get("aos_entryid"), i);
			this.getModel().setValue("aos_channel_r", aos_mkt_popbudget_data.get("aos_channel_r"), i);
			this.getModel().setValue("aos_poptype_r", aos_mkt_popbudget_data.get("aos_poptype_r"), i);
			this.getModel().setValue("aos_serialname", aos_mkt_popbudget_data.get("aos_serialname"), i);
			this.getModel().setValue("aos_season_r", aos_mkt_popbudget_data.get("aos_season_r"), i);
			this.getModel().setValue("aos_roi7days", aos_mkt_popbudget_data.get("aos_roi7days"), i);
			this.getModel().setValue("aos_roitype", aos_mkt_popbudget_data.get("aos_roitype"), i);
			this.getModel().setValue("aos_skucount", aos_mkt_popbudget_data.get("aos_skucount"), i);
			this.getModel().setValue("aos_kwcount", aos_mkt_popbudget_data.get("aos_kwcount"), i);
			this.getModel().setValue("aos_lastrate", aos_mkt_popbudget_data.get("aos_lastrate"), i);
			this.getModel().setValue("aos_lastspend_r", aos_mkt_popbudget_data.get("aos_lastspend_r"), i);
			this.getModel().setValue("aos_lastbudget", aos_mkt_popbudget_data.get("aos_lastbudget"), i);
			this.getModel().setValue("aos_calbudget", aos_mkt_popbudget_data.get("aos_calbudget"), i);
			this.getModel().setValue("aos_adjbudget", aos_mkt_popbudget_data.get("aos_adjbudget"), i);
			this.getModel().setValue("aos_saldescription", aos_mkt_popbudget_data.get("aos_saldescription"), i);
			this.getModel().setValue("aos_popbudget", aos_mkt_popbudget_data.get("aos_popbudget"), i);
			this.getModel().setValue("aos_adjustrate", aos_mkt_popbudget_data.get("aos_adjustrate"), i);
			i++;
		}

	
	}

	

	public void entryRowClick(RowClickEvent evt) {
		Control source = (Control) evt.getSource();
		if (source.getKey().equals("aos_budgetentry")) {
			int CurrentRowIndex = this.getModel().getEntryCurrentRowIndex("aos_budgetentry");
			Object aos_entryid = this.getModel().getValue("aos_entryid", CurrentRowIndex);
			InitGroup(aos_entryid);
		}
	}

	private void InitGroup(Object aos_entryid) {
		System.out.println("=== InitGroup ===");
		this.getModel().deleteEntryData("aos_detailentry");
		Object fid = this.getView().getParentView().getModel().getDataEntity().getPkValue();
		QFilter Qf_id = new QFilter("aos_sourceid", "=", fid);
		QFilter Qf_EntryID = new QFilter("aos_budgetentry.id", "=", aos_entryid);
		QFilter[] filters = new QFilter[] { Qf_id, Qf_EntryID };
		String SelectField = "aos_budgetentry.aos_detailentry.aos_date_d aos_date_d,"
				+ "aos_budgetentry.aos_detailentry.aos_roi_d aos_roi_d,"
				+ "aos_budgetentry.aos_detailentry.aos_spend_d aos_spend_d,"
				+ "aos_budgetentry.aos_detailentry.aos_spendrate_d aos_spendrate_d,"
				+ "aos_budgetentry.aos_detailentry.aos_budget_d aos_budget_d,"
				+ "aos_budgetentry.aos_detailentry.aos_skucount_d aos_skucount_d,"
				+ "aos_budgetentry.aos_detailentry.aos_kwcount_d aos_kwcount_d";
		DynamicObjectCollection aos_mkt_popbudget_dataS = QueryServiceHelper.query("aos_mkt_popbudget_stdt",
				SelectField, filters," aos_budgetentry.aos_detailentry.aos_date_d desc");
		int i = 0;
		for (DynamicObject aos_mkt_popbudget_data : aos_mkt_popbudget_dataS) {
			this.getModel().batchCreateNewEntryRow("aos_detailentry", 1);
			this.getModel().setValue("aos_date_d", aos_mkt_popbudget_data.get("aos_date_d"), i);
			this.getModel().setValue("aos_roi_d", aos_mkt_popbudget_data.get("aos_roi_d"), i);
			this.getModel().setValue("aos_spend_d", aos_mkt_popbudget_data.get("aos_spend_d"), i);
			this.getModel().setValue("aos_spendrate_d", aos_mkt_popbudget_data.get("aos_spendrate_d"), i);
			this.getModel().setValue("aos_budget_d", aos_mkt_popbudget_data.get("aos_budget_d"), i);
			this.getModel().setValue("aos_skucount_d", aos_mkt_popbudget_data.get("aos_skucount_d"), i);
			this.getModel().setValue("aos_kwcount_d", aos_mkt_popbudget_data.get("aos_kwcount_d"), i);
			i++;
		}
	}

	public void afterBindData(EventObject e) {
		super.afterBindData(e);
		String aos_status = this.getView().getParentView().getModel().getValue("aos_status") + "";
		if (aos_status.equals("B")) {
			this.getView().setEnable(false, "aos_entryentity2");
			this.getView().setEnable(false, "aos_apply");
			this.getView().setEnable(false, "aos_save");
			this.getView().setEnable(false, "aos_confirm");
		}
		long User = UserServiceHelper.getCurrentUserId();// 操作人员
		DynamicObject aos_makeby = (DynamicObject) this.getView().getParentView().getModel().getValue("aos_makeby");
		long makeby_id = (Long) aos_makeby.getPkValue();
		// 如果当前用户不为录入人则全锁定
		if (User != makeby_id) {
			this.getView().setEnable(false, "aos_entryentity2");
			this.getView().setEnable(false, "aos_apply");
			this.getView().setEnable(false, "aos_save");
			this.getView().setEnable(false, "aos_confirm");
		} else {
			this.getView().setEnable(true, "aos_entryentity2");
			this.getView().setEnable(true, "aos_apply");
			this.getView().setEnable(true, "aos_save");
			this.getView().setEnable(true, "aos_confirm");
		}
	}

}