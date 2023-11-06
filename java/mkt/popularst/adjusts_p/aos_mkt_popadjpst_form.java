package mkt.popularst.adjusts_p;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;

import common.Cux_Common_Utl;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.db.DB;
import kd.bos.db.DBRoute;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
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
import mkt.popularst.budget_p.aos_mkt_popbudpst_init;

public class aos_mkt_popadjpst_form extends AbstractFormPlugin implements ItemClickListener, RowClickEventListener {

	private static final String DB_MKT = "aos.mkt";

	public void registerListener(EventObject e) {
		super.registerListener(e);

		EntryGrid aos_detailentry = this.getControl("aos_detailentry");
		aos_detailentry.addRowClickListener(this);
		// 给工具栏加监听事件
		this.addItemClickListeners("aos_toolbarap");
		this.addItemClickListeners("aos_select");
		this.addItemClickListeners("aos_apply");
		this.addItemClickListeners("aos_save");
		this.addItemClickListeners("aos_confirm");
		this.addItemClickListeners("aos_set");
	}

	public void itemClick(ItemClickEvent evt) {
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
	}

	private void aos_confirm() {
		Object aos_ppcid = this.getView().getParentView().getModel().getValue("aos_ppcid");
		DynamicObject aos_mkt_pop_ppcst = BusinessDataServiceHelper.loadSingle(aos_ppcid, "aos_mkt_pop_ppcst");
		aos_mkt_pop_ppcst.set("aos_adjustsp", true);
		OperationServiceHelper.executeOperate("save", "aos_mkt_pop_ppcst",
				new DynamicObject[] { aos_mkt_pop_ppcst }, OperateOption.create());
		Boolean aos_adjusts = aos_mkt_pop_ppcst.getBoolean("aos_adjusts");
		if (aos_adjusts) {
			String p_ou_code = ((DynamicObject) this.getView().getParentView().getModel().getValue("aos_orgid"))
					.getString("number");
			// 开始生成 预算调整(推广)
			Map<String, Object> budget = new HashMap<>();
			budget.put("p_ou_code", p_ou_code);
			aos_mkt_popbudpst_init.executerun(budget);
		}
		this.getView().getParentView().getModel().setValue("aos_status", "B");
		this.getView().getParentView().invokeOperation("save");
		this.getView().showSuccessNotification("确认成功！"); 
		this.getView().setEnable(false, "aos_detailentry");
		this.getView().setEnable(false, "aos_save");
		this.getView().setEnable(false, "aos_confirm");
		this.getView().setEnable(false, "aos_apply");
	}

	private void aos_save() {
		int RowCount = this.getModel().getEntryRowCount("aos_detailentry");
		for (int i = 0; i < RowCount; i++) {
			Object aos_adjprice = this.getModel().getValue("aos_adjprice", i);
			Object aos_entryid =  this.getModel().getValue("aos_entryid", i);
			Object aos_ppcentryid = this.getModel().getValue("aos_ppcentryid", i);
			Map<String, Object> Param = new HashMap<>();
			Param.put("aos_adjprice", aos_adjprice);
			Param.put("aos_entryid", aos_entryid);
			Param.put("aos_ppcentryid", aos_ppcentryid);
			Update(Param);
		}
		this.getView().showSuccessNotification("保存成功!");
	}

	private void Update(Map<String, Object> Param) {
		Object aos_adjprice = Param.get("aos_adjprice");
		Object aos_entryid = Param.get("aos_entryid");
		Object aos_ppcentryid = Param.get("aos_ppcentryid");
		String sql = " UPDATE tk_aos_mkt_pop_adjpstdt_r r " + " SET fk_aos_adjprice = ? " 
		+" WHERE 1=1 " + " and r.FEntryId = ?  ";
		Object[] params = { aos_adjprice,  aos_entryid };
		DB.execute(DBRoute.of(DB_MKT), sql, params);
		SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");
		String today = DF.format(new Date());
		sql = " UPDATE tk_aos_mkt_ppcst_data r " + " SET fk_aos_bid = ?," + " fk_aos_lastpricedate = '" + today
				+ "' " + " WHERE 1=1 " + " and r.fid = ? " + "  and fk_aos_lastbid <> ?   ";
		Object[] params2 = { aos_adjprice, aos_ppcentryid, aos_adjprice };
		DB.execute(DBRoute.of(DB_MKT), sql, params2);
	}

	@SuppressWarnings("deprecation")
	private void aos_apply(String sign) {
		BigDecimal aos_adjustrate = (BigDecimal) this.getModel().getValue("aos_adjustrate");
		if ("aos_divide".equals(sign) && (aos_adjustrate == null || aos_adjustrate.compareTo(BigDecimal.ZERO) == 0)) {
			this.getView().showTipNotification("除以时,调整比例不能为0");
			return;
		}
		EntryGrid aos_detailentry = this.getControl("aos_detailentry");
		int[] SelectRows = aos_detailentry.getSelectRows();
		int RowCount = SelectRows.length;
		if (RowCount == 0) {
			this.getView().showTipNotification("至少选择一行数据");
			return;
		}
		for (int i = 0; i < RowCount; i++) {
			BigDecimal aos_highvalue = (BigDecimal) this.getModel().getValue("aos_highvalue", SelectRows[i]);
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
			this.getModel().setValue("aos_adjprice", Cux_Common_Utl.Min(result, aos_highvalue), SelectRows[i]);
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
		Object aos_ctrfrom = this.getModel().getValue("aos_ctrfrom");
		Object aos_ctrto = this.getModel().getValue("aos_ctrto");

		QFilter filter_roifrom = null;
		if (aos_roifrom != null && aos_roifrom.toString().length() > 0)
			filter_roifrom = new QFilter("aos_detailentry.aos_roi", ">=", aos_roifrom);
		QFilter filter_roito = null;
		if (aos_roito != null && aos_roito.toString().length() > 0)
			filter_roito = new QFilter("aos_detailentry.aos_roi", "<=", aos_roito);
		QFilter filter_expfrom = null;
		if (aos_expfrom != null && aos_expfrom.toString().length() > 0)
			filter_expfrom = new QFilter("aos_detailentry.aos_exposure", ">=", aos_expfrom);
		QFilter filter_expto = null;
		if (aos_expto != null && aos_expto.toString().length() > 0)
			filter_expto = new QFilter("aos_detailentry.aos_exposure", "<=", aos_expto);
		QFilter filter_spendfrom = null;
		if (aos_spendfrom != null && aos_spendfrom.toString().length() > 0)
			filter_spendfrom = new QFilter("aos_detailentry.aos_cost", ">=", aos_spendfrom);
		QFilter filter_spendto = null;
		if (aos_spendto != null && aos_spendto.toString().length() > 0)
			filter_spendto = new QFilter("aos_detailentry.aos_cost", "<=", aos_spendto);
		QFilter filter_ctrfrom = null;
		if (aos_ctrfrom != null && aos_ctrfrom.toString().length() > 0)
			filter_ctrfrom = new QFilter("aos_detailentry.aos_ctr", ">=", aos_ctrfrom);
		QFilter filter_ctrto = null;
		if (aos_ctrto != null && aos_ctrto.toString().length() > 0)
			filter_ctrto = new QFilter("aos_detailentry.aos_ctr", "<=", aos_ctrto);

		ArrayList<QFilter> arr_q = new ArrayList<QFilter>();
		arr_q.add(filter_roifrom);
		arr_q.add(filter_roito);
		arr_q.add(filter_expfrom);
		arr_q.add(filter_expto);
		arr_q.add(filter_spendfrom);
		arr_q.add(filter_spendto);
		arr_q.add(filter_ctrfrom);
		arr_q.add(filter_ctrto);
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
		String SelectField = "aos_detailentry.aos_poptype aos_poptype,"
				+ "aos_detailentry.aos_productno aos_productno,"
				+ "aos_detailentry.aos_type aos_type,"
				+ "aos_detailentry.aos_season aos_season,"
				+ "aos_detailentry.aos_category1 aos_category1,"
				+ "aos_detailentry.aos_itemname aos_itemname,"
				+ "aos_detailentry.aos_itemnumer aos_itemnumer,"
				+ "aos_detailentry.aos_keyword aos_keyword,"
				+ "aos_detailentry.aos_matchtype aos_matchtype,"
				+ "aos_detailentry.aos_roi aos_roi,"
				+ "aos_detailentry.aos_exposure aos_exposure,"
				+ "aos_detailentry.aos_ctr aos_ctr,"
				+ "aos_detailentry.aos_serialrate aos_serialrate,"
				+ "aos_detailentry.aos_cost aos_cost,"
				+ "aos_detailentry.aos_lastprice aos_lastprice,"
				+ "aos_detailentry.aos_calprice aos_calprice,"
				+ "aos_detailentry.aos_adjprice aos_adjprice,"
				+ "aos_detailentry.aos_lastdate aos_lastdate,"
				+ "aos_detailentry.aos_bidsuggest aos_bidsuggest,"
				+ "aos_detailentry.aos_highvalue aos_highvalue,"
				+ "aos_detailentry.aos_entryid aos_ppcentryid,"
				+ "aos_detailentry.id aos_entryid";
		DynamicObjectCollection aos_mkt_popadjusts_dataS = QueryServiceHelper.query("aos_mkt_pop_adjpstdt", SelectField,
				filters, "aos_detailentry.aos_productno");
		int i = 0;
		for (DynamicObject aos_mkt_popadjusts_data : aos_mkt_popadjusts_dataS) {
			this.getModel().batchCreateNewEntryRow("aos_detailentry", 1);
			this.getModel().setValue("aos_poptype", aos_mkt_popadjusts_data.get("aos_poptype"), i);
			this.getModel().setValue("aos_productno", aos_mkt_popadjusts_data.get("aos_productno"), i);
			this.getModel().setValue("aos_type", aos_mkt_popadjusts_data.get("aos_type"), i);
			this.getModel().setValue("aos_season", aos_mkt_popadjusts_data.get("aos_season"), i);
			this.getModel().setValue("aos_category1", aos_mkt_popadjusts_data.get("aos_category1"), i);
			this.getModel().setValue("aos_itemname", aos_mkt_popadjusts_data.get("aos_itemname"), i);
			this.getModel().setValue("aos_itemnumer", aos_mkt_popadjusts_data.get("aos_itemnumer"), i);
			this.getModel().setValue("aos_keyword", aos_mkt_popadjusts_data.get("aos_keyword"), i);
			this.getModel().setValue("aos_matchtype", aos_mkt_popadjusts_data.get("aos_matchtype"), i);
			this.getModel().setValue("aos_roi", aos_mkt_popadjusts_data.get("aos_roi"), i);
			this.getModel().setValue("aos_exposure", aos_mkt_popadjusts_data.get("aos_exposure"), i);
			this.getModel().setValue("aos_ctr", aos_mkt_popadjusts_data.get("aos_ctr"), i);
			this.getModel().setValue("aos_serialrate", aos_mkt_popadjusts_data.get("aos_serialrate"), i);
			this.getModel().setValue("aos_cost", aos_mkt_popadjusts_data.get("aos_cost"), i);
			this.getModel().setValue("aos_lastprice", aos_mkt_popadjusts_data.get("aos_lastprice"), i);
			this.getModel().setValue("aos_calprice", aos_mkt_popadjusts_data.get("aos_calprice"), i);
			this.getModel().setValue("aos_adjprice", aos_mkt_popadjusts_data.get("aos_adjprice"), i);
			this.getModel().setValue("aos_lastdate", aos_mkt_popadjusts_data.get("aos_lastdate"), i);
			this.getModel().setValue("aos_bidsuggest", aos_mkt_popadjusts_data.get("aos_bidsuggest"), i);
			this.getModel().setValue("aos_highvalue", aos_mkt_popadjusts_data.get("aos_highvalue"), i);
			this.getModel().setValue("aos_ppcentryid", aos_mkt_popadjusts_data.get("aos_ppcentryid"), i);
			this.getModel().setValue("aos_entryid", aos_mkt_popadjusts_data.get("aos_entryid"), i);
			i++;
		}
	}

	public void entryRowClick(RowClickEvent evt) {
		Control source = (Control) evt.getSource();
		if (source.getKey().equals("aos_detailentry")) {
			int CurrentRowIndex = this.getModel().getEntryCurrentRowIndex("aos_detailentry");
			Object aos_entryid = this.getModel().getValue("aos_entryid", CurrentRowIndex);
			InitWord(aos_entryid);
			InitGroup(aos_entryid);
		}

	}

	private void InitGroup(Object aos_entryid) {
		this.getModel().deleteEntryData("aos_groupskuentry");
		Object fid = this.getView().getParentView().getModel().getDataEntity().getPkValue();
		QFilter Qf_id = new QFilter("aos_sourceid", "=", fid);
		QFilter Qf_EntryID = new QFilter("aos_detailentry.id", "=", aos_entryid);
		QFilter[] filters = new QFilter[] { Qf_id, Qf_EntryID };
		String SelectField = "aos_detailentry.aos_groupskuentry.aos_groupsku aos_groupsku,"
				+ "aos_detailentry.aos_groupskuentry.aos_groupitemname aos_groupitemname,"
				+ "aos_detailentry.aos_groupskuentry.aos_groupkwnum aos_groupkwnum,"
				+ "aos_detailentry.aos_groupskuentry.aos_grouproi aos_grouproi,"
				+ "aos_detailentry.aos_groupskuentry.aos_groupex aos_groupex,"
				+ "aos_detailentry.aos_groupskuentry.aos_groupcost aos_groupcost,"
				+ "aos_detailentry.aos_groupskuentry.aos_buybox aos_buybox";
		DynamicObjectCollection aos_mkt_popadjusts_dataS = QueryServiceHelper.query("aos_mkt_pop_adjpstdt", SelectField,
				filters, " aos_detailentry.aos_groupskuentry.aos_groupsku");
		int i = 0;
		for (DynamicObject aos_mkt_popadjusts_data : aos_mkt_popadjusts_dataS) {
			this.getModel().batchCreateNewEntryRow("aos_groupskuentry", 1);
			this.getModel().setValue("aos_groupsku", aos_mkt_popadjusts_data.get("aos_groupsku"), i);
			this.getModel().setValue("aos_groupitemname", aos_mkt_popadjusts_data.get("aos_groupitemname"), i);
			this.getModel().setValue("aos_groupkwnum", aos_mkt_popadjusts_data.get("aos_groupkwnum"), i);
			this.getModel().setValue("aos_grouproi", aos_mkt_popadjusts_data.get("aos_grouproi"), i);
			this.getModel().setValue("aos_groupex", aos_mkt_popadjusts_data.get("aos_groupex"), i);
			this.getModel().setValue("aos_groupcost", aos_mkt_popadjusts_data.get("aos_groupcost"), i);
			this.getModel().setValue("aos_buybox", aos_mkt_popadjusts_data.get("aos_buybox"), i);
			i++;
		}
	}

	private void InitWord(Object aos_entryid) {
		this.getModel().deleteEntryData("aos_wordentry");
		Object fid = this.getView().getParentView().getModel().getDataEntity().getPkValue();
		QFilter Qf_id = new QFilter("aos_sourceid", "=", fid);
		QFilter Qf_EntryID = new QFilter("aos_detailentry.id", "=", aos_entryid);
		QFilter[] filters = new QFilter[] { Qf_id, Qf_EntryID };
		String SelectField = "aos_detailentry.aos_wordentry.aos_worddate aos_worddate,"
				+ "aos_detailentry.aos_wordentry.aos_wordroi aos_wordroi,"
				+ "aos_detailentry.aos_wordentry.aos_wordex aos_wordex,"
				+ "aos_detailentry.aos_wordentry.aos_wordctr aos_wordctr,"
				+ "aos_detailentry.aos_wordentry.aos_wordbid aos_wordbid,"
				+ "aos_detailentry.aos_wordentry.aos_wordcost aos_wordcost,"
				+ "aos_detailentry.aos_wordentry.aos_wordsetbid aos_wordsetbid,"
				+ "aos_detailentry.aos_wordentry.aos_wordadprice aos_wordadprice";
		DynamicObjectCollection aos_mkt_popadjusts_dataS = QueryServiceHelper.query("aos_mkt_pop_adjpstdt", SelectField,
				filters, " aos_detailentry.aos_wordentry.aos_worddate desc");
		int i = 0;
		for (DynamicObject aos_mkt_popadjusts_data : aos_mkt_popadjusts_dataS) {
			this.getModel().batchCreateNewEntryRow("aos_wordentry", 1);
			this.getModel().setValue("aos_worddate", aos_mkt_popadjusts_data.get("aos_worddate"), i);
			this.getModel().setValue("aos_wordroi", aos_mkt_popadjusts_data.get("aos_wordroi"), i);
			this.getModel().setValue("aos_wordex", aos_mkt_popadjusts_data.get("aos_wordex"), i);
			this.getModel().setValue("aos_wordctr", aos_mkt_popadjusts_data.get("aos_wordctr"), i);
			this.getModel().setValue("aos_wordbid", aos_mkt_popadjusts_data.get("aos_wordbid"), i);
			this.getModel().setValue("aos_wordcost", aos_mkt_popadjusts_data.get("aos_wordcost"), i);
			this.getModel().setValue("aos_wordsetbid", aos_mkt_popadjusts_data.get("aos_wordsetbid"), i);
			this.getModel().setValue("aos_wordadprice", aos_mkt_popadjusts_data.get("aos_wordadprice"), i);
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

}