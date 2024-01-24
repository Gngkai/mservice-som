package mkt.popularst.adjust_s;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

import common.Cux_Common_Utl;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.db.DB;
import kd.bos.db.DBRoute;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.IPageCache;
import kd.bos.form.control.Control;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.control.events.RowClickEvent;
import kd.bos.form.control.events.RowClickEventListener;
import kd.bos.form.events.BeforeClosedEvent;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.MKTCom;
import mkt.popularst.budget.AosMktPopBudpstTask;

public class aos_mkt_popadjst_form extends AbstractFormPlugin implements ItemClickListener, RowClickEventListener {

	private static final String DB_MKT = "aos.mkt";

	public void registerListener(EventObject e) {
		super.registerListener(e);

		EntryGrid aos_detailentry = this.getControl("aos_detailentry");
		aos_detailentry.addRowClickListener(this);
		EntryGrid aos_groupentry = this.getControl("aos_groupentry");
		aos_groupentry.addRowClickListener(this);

		// 给工具栏加监听事件
		this.addItemClickListeners("aos_toolbarap");
		this.addItemClickListeners("aos_select");
		this.addItemClickListeners("aos_apply");
		this.addItemClickListeners("aos_save");
		this.addItemClickListeners("aos_confirm");
		this.addItemClickListeners("aos_set");
		this.addItemClickListeners("aos_adjust");
	}

	public void itemClick(ItemClickEvent evt) {
		try {
			super.itemClick(evt);
			String Control = evt.getItemKey();
			if (Control.equals("aos_select"))
				aos_select();
			else if (Control.equals("aos_multipy") || Control.equals("aos_divide") || Control.equals("aos_add")
					|| Control.equals("aos_subtract") || Control.equals("aos_set")) {
				IPageCache iPageCache = this.getView().getPageCache();
				iPageCache.put("UpdateFlag", "Y");
				aos_apply(Control);
				iPageCache.put("UpdateFlag", "N");
			} else if (Control.equals("aos_save"))
				aos_save();
			else if (Control.equals("aos_confirm"))
				aos_confirm();
			else if (Control.equals("aos_adjust"))
				adjustPrice();

		}catch (Exception e){
			e.printStackTrace();
		}
	}

	private void aos_confirm() {
		Object aos_ppcid = this.getView().getParentView().getModel().getValue("aos_ppcid");
		QFilter Qf_id = new QFilter("aos_ppcid", "=", aos_ppcid);
		QFilter[] filters = new QFilter[] { Qf_id };
		String SelectField = "id";
		DynamicObjectCollection aos_mkt_popular_adjsS = QueryServiceHelper.query("aos_mkt_pop_adjst", SelectField,
				filters);
		int total = aos_mkt_popular_adjsS.size();
		QFilter Qf_status = new QFilter("aos_status", "=", "B");
		filters = new QFilter[] { Qf_id, Qf_status };
		aos_mkt_popular_adjsS = QueryServiceHelper.query("aos_mkt_pop_adjst", SelectField, filters);
		int confirm = aos_mkt_popular_adjsS.size();
		if (total == confirm + 1) {
			DynamicObject aos_mkt_popular_ppc = BusinessDataServiceHelper.loadSingle(aos_ppcid, "aos_mkt_pop_ppcst");
			aos_mkt_popular_ppc.set("aos_adjusts", true);
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_pop_ppcst",
					new DynamicObject[] { aos_mkt_popular_ppc }, OperateOption.create());
			if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
			}
			Boolean aos_adjustsp = aos_mkt_popular_ppc.getBoolean("aos_adjustsp");
			if (aos_adjustsp) {
				String p_ou_code = ((DynamicObject) this.getView().getParentView().getModel().getValue("aos_orgid"))
						.getString("number");
				// 开始生成 预算调整(推广)
				Map<String, Object> budget = new HashMap<>();
				budget.put("p_ou_code", p_ou_code);
				AosMktPopBudpstTask.executerun(budget);
			}
		}
		this.getView().getParentView().getModel().setValue("aos_status", "B");
		this.getView().getParentView().invokeOperation("save");
		this.getView().showSuccessNotification("确认成功！");
		this.getView().setEnable(false, "aos_detailentry");
		this.getView().setEnable(false, "aos_apply");
		this.getView().setEnable(false, "aos_save");
		this.getView().setEnable(false, "aos_confirm");
	}

	private void aos_save() {
		int CurrentRowIndex = this.getModel().getEntryCurrentRowIndex("aos_detailentry");
		DynamicObject aos_itemnumerD = (DynamicObject) this.getModel().getValue("aos_itemid", CurrentRowIndex);
		String aos_itemnumer = aos_itemnumerD.getString("number");
		DynamicObjectCollection aos_groupentryS = this.getModel().getEntryEntity("aos_groupentry");
		for (DynamicObject aos_groupentry : aos_groupentryS) {
			long aos_groupentryid = aos_groupentry.getLong("aos_groupentryid");
			String aos_keyword = aos_groupentry.getString("aos_keyword");
			String aos_match_type = aos_groupentry.getString("aos_match_type");
			BigDecimal aos_manualprz = aos_groupentry.getBigDecimal("aos_manualprz");
			boolean aos_valid_flag = aos_groupentry.getBoolean("aos_valid_flag"); // 剔词
			Map<String, Object> Param = new HashMap<>();
			Param.put("aos_itemnumer", aos_itemnumer);
			Param.put("aos_groupentryid", aos_groupentryid);
			Param.put("aos_keyword", aos_keyword);
			Param.put("aos_match_type", aos_match_type);
			Param.put("aos_manualprz", aos_manualprz);
			if (aos_valid_flag)
				Param.put("aos_valid_flag", aos_valid_flag);
			Update(Param);
		}
		this.getView().showSuccessNotification("保存成功!");
	}

	private void Update(Map<String, Object> Param) {
		Object aos_itemnumer = Param.get("aos_itemnumer");
		Object aos_groupentryid = Param.get("aos_groupentryid");
		Object aos_keyword = Param.get("aos_keyword");
		Object aos_match_type = Param.get("aos_match_type");
		Object aos_manualprz = Param.get("aos_manualprz");

		String sql = null;

		if (Param.containsKey("aos_valid_flag")) {
			sql = " UPDATE tk_aos_mktpop_adjstdt_d d " + " SET fk_aos_manualprz = ?, " + " fk_aos_valid_flag = 1 "
					+ " WHERE 1=1 and d.FDetailId = ?  ";
		} else {
			sql = " UPDATE tk_aos_mktpop_adjstdt_d d " + " SET fk_aos_manualprz = ?, " + " fk_aos_valid_flag = 0 "
					+ " WHERE 1=1 and d.FDetailId = ?  ";
		}
		
		Object[] params = { aos_manualprz, aos_groupentryid };
		DB.execute(DBRoute.of(DB_MKT), sql, params);
		SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");
		String today = DF.format(new Date());
		if (Param.containsKey("aos_valid_flag")) {
			sql = " UPDATE tk_aos_mkt_pop_ppcst_r r " + " SET fk_aos_bid = ? , "
					+ "fk_aos_valid_flag = 1,fk_aos_groupstatus = 'UNAVAILABLE', " + " fk_aos_salemanual = 1 , "
					+ " fk_aos_lastpricedate =  '" + today + "'  " + " WHERE 1=1 " + " and r.fk_aos_itemnumer = ? "
					+ " and r.fk_aos_keyword = ? " + " and r.fk_aos_match_type = ? " + " and r.fk_aos_lastbid <> ?   ";
		} else {
			sql = " UPDATE tk_aos_mkt_pop_ppcst_r r " + " SET fk_aos_bid = ? , " + " fk_aos_salemanual = 1 , "
					+ " fk_aos_lastpricedate =  '" + today + "'  " + " WHERE 1=1 " + " and r.fk_aos_itemnumer = ? "
					+ " and r.fk_aos_keyword = ? " + " and r.fk_aos_match_type = ? " + " and r.fk_aos_lastbid <> ?   ";
		}
		Object[] params2 = { aos_manualprz, aos_itemnumer, aos_keyword, aos_match_type, aos_manualprz };
		DB.execute(DBRoute.of(DB_MKT), sql, params2);
	}

	@SuppressWarnings("deprecation")
	private void aos_apply(String sign) {
		BigDecimal aos_adjustrate = (BigDecimal) this.getModel().getValue("aos_adjustrate");
		if ("aos_divide".equals(sign) && (aos_adjustrate == null || aos_adjustrate.compareTo(BigDecimal.ZERO) == 0)) {
			this.getView().showTipNotification("除以时,调整比例不能为0");
			return;
		}
		EntryGrid aos_groupentry = this.getControl("aos_groupentry");
		int[] SelectRows = aos_groupentry.getSelectRows();
		int RowCount = SelectRows.length;
		if (RowCount == 0) {
			this.getView().showTipNotification("至少选择一行数据");
			return;
		}
		for (int i = 0; i < RowCount; i++) {
			BigDecimal aos_highvalue = (BigDecimal) this.getModel().getValue("aos_highprz", SelectRows[i]);
			BigDecimal aos_lastprice = (BigDecimal) this.getModel().getValue("aos_lastprice", SelectRows[i]);
			BigDecimal result = BigDecimal.ZERO;
			switch (sign) {
			case "aos_multipy":
				result = aos_lastprice.multiply(aos_adjustrate);
				break;
			case "aos_divide":
				result = aos_lastprice.divide(aos_adjustrate, 2, BigDecimal.ROUND_HALF_UP);
				break;
			case "aos_add":
				result = aos_lastprice.add(aos_adjustrate);
				break;
			case "aos_subtract":
				result = aos_lastprice.subtract(aos_adjustrate);
				break;
			case "aos_set":
				result = aos_lastprice;
				break;
			}

			this.getModel().setValue("aos_manualprz", Cux_Common_Utl.Min(result, aos_highvalue), SelectRows[i]);
		}
		this.getView().showSuccessNotification("应用成功!");
	}

	private void aos_select() {
		// 筛选之前自动保存
		aos_save();

		Object aos_roifrom = this.getModel().getValue("aos_roifrom");
		Object aos_roito = this.getModel().getValue("aos_roito");
		Object aos_expfrom = this.getModel().getValue("aos_expfrom");
		Object aos_expto = this.getModel().getValue("aos_expto");
		Object aos_spendfrom = this.getModel().getValue("aos_spendfrom");
		Object aos_spendto = this.getModel().getValue("aos_spendto");

		QFilter filter_roifrom = null;
		if (aos_roifrom != null && aos_roifrom.toString().length() > 0)
			filter_roifrom = new QFilter("aos_detailentry.aos_roi", ">=", aos_roifrom);
		QFilter filter_roito = null;
		if (aos_roito != null && aos_roito.toString().length() > 0)
			filter_roito = new QFilter("aos_detailentry.aos_roi", "<=", aos_roito);
		QFilter filter_expfrom = null;
		if (aos_expfrom != null && aos_expfrom.toString().length() > 0)
			filter_expfrom = new QFilter("aos_detailentry.aos_expouse", ">=", aos_expfrom);
		QFilter filter_expto = null;
		if (aos_expto != null && aos_expto.toString().length() > 0)
			filter_expto = new QFilter("aos_detailentry.aos_expouse", "<=", aos_expto);
		QFilter filter_spendfrom = null;
		if (aos_spendfrom != null && aos_spendfrom.toString().length() > 0)
			filter_spendfrom = new QFilter("aos_detailentry.aos_spend", ">=", aos_spendfrom);
		QFilter filter_spendto = null;
		if (aos_spendto != null && aos_spendto.toString().length() > 0)
			filter_spendto = new QFilter("aos_detailentry.aos_spend", "<=", aos_spendto);

		ArrayList<QFilter> arr_q = new ArrayList<QFilter>();
		arr_q.add(filter_roifrom);
		arr_q.add(filter_roito);
		arr_q.add(filter_expfrom);
		arr_q.add(filter_expto);
		arr_q.add(filter_spendfrom);
		arr_q.add(filter_spendto);
		InitData(arr_q);
	}

	public void afterCreateNewData(EventObject e) {
		IPageCache iPageCache = this.getView().getPageCache();
		iPageCache.put("UpdateFlag", "N");
		InitData(null);// 初始化数据
	}

	private void InitData(ArrayList<QFilter> arr_q) {
		this.getModel().deleteEntryData("aos_detailentry");
		Object fid = this.getView().getParentView().getModel().getDataEntity().getPkValue();
		QFilter Qf_id = new QFilter("aos_sourceid", "=", fid);
		QFilter[] filters = null;
		if (arr_q == null)
			filters = new QFilter[] { Qf_id };
		else {
			arr_q.add(Qf_id);
			filters = arr_q.toArray(new QFilter[arr_q.size()]);
		}
		String SelectField = "aos_detailentry.aos_itemid aos_itemid," + "aos_detailentry.aos_cn_name aos_cn_name,"
				+ "aos_detailentry.aos_seasonseting aos_seasonseting," + "aos_detailentry.aos_categroy1 aos_categroy1,"
				+ "aos_detailentry.aos_categroy2 aos_categroy2," + "aos_detailentry.aos_categroy3 aos_categroy3,"
				+ "aos_detailentry.aos_avadays aos_avadays," + "aos_detailentry.aos_count aos_count,"
				+ "aos_detailentry.aos_click aos_click," + "aos_detailentry.aos_spend aos_spend,"
				+ "aos_detailentry.aos_roi aos_roi," + "aos_detailentry.aos_expouse aos_expouse,"
				+ "aos_detailentry.id EntryID";
		DynamicObjectCollection aos_mkt_popadjusts_dataS = QueryServiceHelper.query("aos_mkt_pop_adjstdt", SelectField,
				filters, "aos_detailentry.aos_itemid");
		int i = 0;
		for (DynamicObject aos_mkt_popadjusts_data : aos_mkt_popadjusts_dataS) {
			this.getModel().batchCreateNewEntryRow("aos_detailentry", 1);
			this.getModel().setValue("aos_itemid", aos_mkt_popadjusts_data.get("aos_itemid"), i);
			this.getModel().setValue("aos_cn_name", aos_mkt_popadjusts_data.get("aos_cn_name"), i);
			this.getModel().setValue("aos_seasonseting", aos_mkt_popadjusts_data.get("aos_seasonseting"), i);
			this.getModel().setValue("aos_categroy1", aos_mkt_popadjusts_data.get("aos_categroy1"), i);
			this.getModel().setValue("aos_categroy2", aos_mkt_popadjusts_data.get("aos_categroy2"), i);
			this.getModel().setValue("aos_categroy3", aos_mkt_popadjusts_data.get("aos_categroy3"), i);
			this.getModel().setValue("aos_avadays", aos_mkt_popadjusts_data.get("aos_avadays"), i);
			this.getModel().setValue("aos_count", aos_mkt_popadjusts_data.get("aos_count"), i);
			this.getModel().setValue("aos_click", aos_mkt_popadjusts_data.get("aos_click"), i);
			this.getModel().setValue("aos_spend", aos_mkt_popadjusts_data.get("aos_spend"), i);
			this.getModel().setValue("aos_roi", aos_mkt_popadjusts_data.get("aos_roi"), i);
			this.getModel().setValue("aos_expouse", aos_mkt_popadjusts_data.get("aos_expouse"), i);
			this.getModel().setValue("aos_entryid", aos_mkt_popadjusts_data.get("EntryID"), i);
			i++;
		}
	}

	public void entryRowClick(RowClickEvent evt) {
		Control source = (Control) evt.getSource();
		if (source.getKey().equals("aos_detailentry")) {
			IPageCache iPageCache = this.getView().getPageCache();
			iPageCache.put("UpdateFlag", "N");
			int CurrentRowIndex = this.getModel().getEntryCurrentRowIndex("aos_detailentry");
			Object aos_entryid = this.getModel().getValue("aos_entryid", CurrentRowIndex);
			InitGroup(aos_entryid);
		} else if (source.getKey().equals("aos_groupentry")) {
			IPageCache iPageCache = this.getView().getPageCache();
			iPageCache.put("UpdateFlag", "Y");
		}

	}

	private void InitGroup(Object aos_entryid) {
		this.getModel().deleteEntryData("aos_groupentry");
		Object fid = this.getView().getParentView().getModel().getDataEntity().getPkValue();
		QFilter Qf_id = new QFilter("aos_sourceid", "=", fid);
		QFilter Qf_EntryID = new QFilter("aos_detailentry.id", "=", aos_entryid);
		QFilter[] filters = new QFilter[] { Qf_id, Qf_EntryID };
		String SelectField = "aos_detailentry.aos_groupentry.aos_keyword aos_keyword,"
				+ "aos_detailentry.aos_groupentry.aos_click_r aos_click_r,"
				+ "aos_detailentry.aos_groupentry.aos_spend_r aos_spend_r,"
				+ "aos_detailentry.aos_groupentry.aos_roi_r aos_roi_r,"
				+ "aos_detailentry.aos_groupentry.aos_expouse_r aos_expouse_r,"
				+ "aos_detailentry.aos_groupentry.aos_ctr aos_ctr,"
				+ "aos_detailentry.aos_groupentry.aos_match_type aos_match_type,"
				+ "aos_detailentry.aos_groupentry.aos_lastprice aos_lastprice,"
				+ "aos_detailentry.aos_groupentry.aos_calprice aos_calprice,"
				+ "aos_detailentry.aos_groupentry.aos_manualprz aos_manualprz,"
				+ "aos_detailentry.aos_groupentry.aos_valid_flag aos_valid_flag,"
				+ "aos_detailentry.aos_groupentry.aos_adviceprz aos_adviceprz,"
				+ "aos_detailentry.aos_groupentry.aos_highprz aos_highprz,"
				+ "aos_detailentry.aos_groupentry.aos_lowprz aos_lowprz,"
				+ "aos_detailentry.aos_groupentry.id aos_groupentryid";
		DynamicObjectCollection aos_mkt_popadjusts_dataS = QueryServiceHelper.query("aos_mkt_pop_adjstdt", SelectField,
				filters, " aos_detailentry.aos_groupentry.aos_keyword");
		int i = 0;
		for (DynamicObject aos_mkt_popadjusts_data : aos_mkt_popadjusts_dataS) {
			this.getModel().batchCreateNewEntryRow("aos_groupentry", 1);
			this.getModel().setValue("aos_keyword", aos_mkt_popadjusts_data.get("aos_keyword"), i);
			this.getModel().setValue("aos_click_r", aos_mkt_popadjusts_data.get("aos_click_r"), i);
			this.getModel().setValue("aos_spend_r", aos_mkt_popadjusts_data.get("aos_spend_r"), i);
			this.getModel().setValue("aos_roi_r", aos_mkt_popadjusts_data.get("aos_roi_r"), i);
			this.getModel().setValue("aos_expouse_r", aos_mkt_popadjusts_data.get("aos_expouse_r"), i);
			this.getModel().setValue("aos_ctr", aos_mkt_popadjusts_data.get("aos_ctr"), i);
			this.getModel().setValue("aos_match_type", aos_mkt_popadjusts_data.get("aos_match_type"), i);
			this.getModel().setValue("aos_lastprice", aos_mkt_popadjusts_data.get("aos_lastprice"), i);
			this.getModel().setValue("aos_calprice", aos_mkt_popadjusts_data.get("aos_calprice"), i);
			this.getModel().setValue("aos_manualprz", aos_mkt_popadjusts_data.get("aos_manualprz"), i);
			this.getModel().setValue("aos_valid_flag", aos_mkt_popadjusts_data.get("aos_valid_flag"), i);
			this.getModel().setValue("aos_adviceprz", aos_mkt_popadjusts_data.get("aos_adviceprz"), i);
			this.getModel().setValue("aos_highprz", aos_mkt_popadjusts_data.get("aos_highprz"), i);
			this.getModel().setValue("aos_lowprz", aos_mkt_popadjusts_data.get("aos_lowprz"), i);
			this.getModel().setValue("aos_groupentryid", aos_mkt_popadjusts_data.get("aos_groupentryid"), i);
			i++;
		}
	}

	public void propertyChanged(PropertyChangedArgs e) {
		String name = e.getProperty().getName();
		IPageCache iPageCache = this.getView().getPageCache();
		String UpdateFlag = iPageCache.get("UpdateFlag");
		if ("aos_manualprz".equals(name) && "Y".equals(UpdateFlag)) {
			int CurrentRowIndex = this.getModel().getEntryCurrentRowIndex("aos_detailentry");
			int CurrentRowIndexG = e.getChangeSet()[0].getRowIndex();
			DynamicObject aos_itemnumerD = (DynamicObject) this.getModel().getValue("aos_itemid", CurrentRowIndex);
			String aos_itemnumer = aos_itemnumerD.getString("number");
			Object aos_groupentryid = this.getModel().getValue("aos_groupentryid", CurrentRowIndex);
			Object aos_keyword = this.getModel().getValue("aos_keyword", CurrentRowIndexG);
			Object aos_match_type = this.getModel().getValue("aos_match_type", CurrentRowIndexG);
			Object aos_manualprz = this.getModel().getValue("aos_manualprz", CurrentRowIndexG);
			Map<String, Object> Param = new HashMap<>();
			Param.put("aos_itemnumer", aos_itemnumer);
			Param.put("aos_groupentryid", aos_groupentryid);
			Param.put("aos_keyword", aos_keyword);
			Param.put("aos_match_type", aos_match_type);
			Param.put("aos_manualprz", aos_manualprz);
			Update(Param);
		}
	}

	public void beforeClosed(BeforeClosedEvent e) {
		super.beforeClosed(e);
		e.setCheckDataChange(false);
	}

	public void afterBindData(EventObject e) {
		super.afterBindData(e);
		String aos_status = this.getView().getParentView().getModel().getValue("aos_status") + "";
		if (aos_status.equals("B")) {
			this.getView().setEnable(false, "aos_detailentry");
			this.getView().setEnable(false, "aos_apply");
			this.getView().setEnable(false, "aos_save");
			this.getView().setEnable(false, "aos_confirm");
		}

		long User = UserServiceHelper.getCurrentUserId();// 操作人员
		DynamicObject aos_makeby = (DynamicObject) this.getView().getParentView().getModel().getValue("aos_makeby");
		long makeby_id = (Long) aos_makeby.getPkValue();
		// 如果当前用户不为录入人则全锁定
		if (User != makeby_id) {
			this.getView().setEnable(false, "aos_detailentry");
			this.getView().setEnable(false, "aos_apply");
			this.getView().setEnable(false, "aos_save");
			this.getView().setEnable(false, "aos_confirm");
			this.getView().setEnable(false, "aos_adjust");
		} else {
			this.getView().setEnable(true, "aos_detailentry");
			this.getView().setEnable(true, "aos_apply");
			this.getView().setEnable(true, "aos_save");
			this.getView().setEnable(true, "aos_confirm");
			this.getView().setEnable(true, "aos_adjust");
		}

		// 自动勾选所有行
		// EntryGrid aos_detailentry = this.getControl("aos_detailentry");
		EntryGrid entryGrid = this.getControl("aos_detailentry");
		DynamicObjectCollection dyc_img = this.getModel().getDataEntity(true)
				.getDynamicObjectCollection("aos_detailentry");
		int rows = dyc_img.size();
		int[] selectRow = new int[rows];
		for (int i = 0; i < rows; i++)
			selectRow[i] = i;
		entryGrid.selectRows(selectRow, 0);
	}

	/** 建议价调整 **/
	@SuppressWarnings("deprecation")
	public void adjustPrice(){
		Object aos_adjustrate = this.getModel().getValue("aos_adjustrate");
		if (aos_adjustrate != null) {
			EntryGrid aos_detailentry = this.getControl("aos_groupentry");
			int[] SelectRows = aos_detailentry.getSelectRows();
			int RowCount = SelectRows.length;
			for (int i = 0; i < RowCount; i++) {
				BigDecimal aos_adjprice = (BigDecimal) Optional.ofNullable(this.getModel().getValue("aos_adviceprz",SelectRows[i])).orElse(BigDecimal.ZERO);
				BigDecimal aos_highvalue = (BigDecimal) Optional.ofNullable(this.getModel().getValue("aos_highprz",SelectRows[i])).orElse(BigDecimal.ZERO);
				this.getModel().setValue("aos_manualprz",
						MKTCom.min(aos_adjprice.multiply((BigDecimal) aos_adjustrate).setScale(2,BigDecimal.ROUND_HALF_UP),aos_highvalue),SelectRows[i]);
			}
		}
		aos_save();
		this.getView().showSuccessNotification("建议价应用成功!");
	}
}