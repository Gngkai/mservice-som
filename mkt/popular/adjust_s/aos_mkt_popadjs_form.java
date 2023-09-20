package mkt.popular.adjust_s;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.db.DB;
import kd.bos.db.DBRoute;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.entity.report.CellStyle;
import kd.bos.form.control.AbstractGrid;
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
import mkt.popular.budget_p.aos_mkt_popbudgetp_init;

public class aos_mkt_popadjs_form extends AbstractFormPlugin implements ItemClickListener, RowClickEventListener {

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
	}

	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String Control = evt.getItemKey();
		if (Control.equals("aos_select"))
			aos_select();
		else if (Control.equals("aos_multipy") || Control.equals("aos_divide") || Control.equals("aos_add")
				|| Control.equals("aos_subtract"))
			aos_apply(Control);
		else if (Control.equals("aos_save"))
			aos_save();
		else if (Control.equals("aos_confirm"))
			aos_confirm();
		// 建议价调整
		else if (Control.equals("aos_adjust")) {
			aos_dujust();
		}

	}

	/**
	 * 建议价*调整比例 应用至销售出价 字段
	 */
	private void aos_dujust() {
		Object aos_adjustrate = this.getModel().getValue("aos_adjustrate");
		if (aos_adjustrate != null) {
			EntryGrid aos_detailentry = this.getControl("aos_detailentry");
			int[] SelectRows = aos_detailentry.getSelectRows();
			int RowCount = SelectRows.length;
			for (int i = 0; i < RowCount; i++) {
				BigDecimal aos_adjprice = (BigDecimal) this.getModel().getValue("aos_bidsuggest", SelectRows[i]);
				BigDecimal aos_highvalue = (BigDecimal) this.getModel().getValue("aos_highvalue", SelectRows[i]);
				this.getModel().setValue("aos_adjprice",
						min(aos_adjprice.multiply((BigDecimal) aos_adjustrate), aos_highvalue), SelectRows[i]);
			}
		}
		this.getView().showSuccessNotification("建议价应用成功!");
	}

	private void aos_confirm() {
		Object aos_ppcid = this.getView().getParentView().getModel().getValue("aos_ppcid");
		QFilter Qf_id = new QFilter("aos_ppcid", "=", aos_ppcid);
		QFilter[] filters = new QFilter[] { Qf_id };
		String SelectField = "id";
		DynamicObjectCollection aos_mkt_popular_adjsS = QueryServiceHelper.query("aos_mkt_popular_adjs", SelectField,
				filters);
		int total = aos_mkt_popular_adjsS.size();
		QFilter Qf_status = new QFilter("aos_status", "=", "B");
		filters = new QFilter[] { Qf_id, Qf_status };
		aos_mkt_popular_adjsS = QueryServiceHelper.query("aos_mkt_popular_adjs", SelectField, filters);
		int confirm = aos_mkt_popular_adjsS.size();
		if (total == confirm + 1) {
			DynamicObject aos_mkt_popular_ppc = BusinessDataServiceHelper.loadSingle(aos_ppcid, "aos_mkt_popular_ppc");
			aos_mkt_popular_ppc.set("aos_adjusts", true);
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_popular_ppc",
					new DynamicObject[] { aos_mkt_popular_ppc }, OperateOption.create());
			if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
			}
			Boolean aos_adjustsp = aos_mkt_popular_ppc.getBoolean("aos_adjustsp");
			if (aos_adjustsp) {
				String p_ou_code = ((DynamicObject) this.getView().getParentView().getModel().getValue("aos_orgid"))
						.getString("number");
				// 开始生成 出价调整(销售)
				Map<String, Object> budget = new HashMap<>();
				budget.put("p_ou_code", p_ou_code);
				aos_mkt_popbudgetp_init.executerun(budget);
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
		int RowCount = this.getModel().getEntryRowCount("aos_detailentry");
		for (int i = 0; i < RowCount; i++) {
			BigDecimal aos_adjprice = (BigDecimal) this.getModel().getValue("aos_adjprice", i);
			String aos_description = (String) this.getModel().getValue("aos_description", i);
			long aos_entryid = (long) this.getModel().getValue("aos_entryid", i);
			long aos_ppcentryid = (long) this.getModel().getValue("aos_ppcentryid", i);
			Object aos_mark = this.getModel().getValue("aos_mark", i);
			if (aos_mark.toString().equals("true"))
				aos_mark = 1;
			else
				aos_mark = 0;
			Update(aos_entryid, aos_adjprice, aos_description, aos_ppcentryid, aos_mark);
		}
		this.getView().showSuccessNotification("保存成功!");
	}

	private void Update(long aos_entryid, BigDecimal aos_adjprice, String aos_description, long aos_ppcentryid,
			Object aos_mark) {
		String sql = " UPDATE tk_aos_mkt_adjsdata_r r " + " SET fk_aos_adjprice = ?," + " fk_aos_description = ?, "
				+ "fk_aos_mark= ?" + " WHERE 1=1 " + " and r.FEntryId = ?  ";
		Object[] params = { aos_adjprice, aos_description, aos_mark, aos_entryid };
		DB.execute(DBRoute.of(DB_MKT), sql, params);

		SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");
		String today = DF.format(new Date());
		sql = " UPDATE tk_aos_mkt_popular_ppc_r r " + " SET fk_aos_bid = ? , " + " fk_aos_salemanual = 1 , "
				+ " fk_aos_lastpricedate =  '" + today + "'  " + " WHERE 1=1 " + " and r.FEntryId = ? "
				+ " and fk_aos_lastbid <> ?  ";
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
			}
			this.getModel().setValue("aos_adjprice", min(result, aos_highvalue), SelectRows[i]);
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
		String SelectField = "aos_detailentry.aos_productno aos_productno,"
				+ "aos_detailentry.aos_itemnumer aos_itemnumer," + "aos_detailentry.aos_categroy1 aos_categroy1,"
				+ "aos_detailentry.aos_categroy2 aos_categroy2," + "aos_detailentry.aos_categroy3 aos_categroy3,"
				+ "aos_detailentry.aos_avadays aos_avadays,"
				+ "aos_detailentry.aos_contryentrystatus aos_contryentrystatus,"
				+ "aos_detailentry.aos_highvalue aos_highvalue," + "aos_detailentry.aos_seasonseting aos_seasonseting,"
				+ "aos_detailentry.aos_cn_name aos_cn_name," + "aos_detailentry.aos_activity aos_activity,"
				+ "aos_detailentry.aos_roi aos_roi," + "aos_detailentry.aos_expouse aos_expouse,"
				+ "aos_detailentry.aos_costrate aos_costrate," + "aos_detailentry.aos_spend aos_spend,"
				+ "aos_detailentry.aos_calprice aos_calprice," + "aos_detailentry.aos_adjprice aos_adjprice,"
				+ "aos_detailentry.aos_description aos_description," + "aos_detailentry.aos_bidsuggest aos_bidsuggest,"
				+ "aos_detailentry.aos_bidrangestart aos_bidrangestart,"
				+ "aos_detailentry.aos_lastprice aos_lastprice," + "aos_detailentry.aos_bidrangeend aos_bidrangeend,"
				+ "aos_detailentry.aos_lastdate aos_lastdate," + "aos_detailentry.id EntryID,"
				+ "aos_detailentry.aos_ppcentryid aos_ppcentryid,aos_detailentry.aos_ratio as aos_ratio,"
				+ "aos_detailentry.aos_mark as aos_mark," + "aos_detailentry.aos_basestitem aos_basestitem";
		DynamicObjectCollection aos_mkt_popadjusts_dataS = QueryServiceHelper.query("aos_mkt_popadjusts_data",
				SelectField, filters, "aos_detailentry.aos_productno");
		int i = 0;
		for (DynamicObject aos_mkt_popadjusts_data : aos_mkt_popadjusts_dataS) {
			this.getModel().batchCreateNewEntryRow("aos_detailentry", 1);
			this.getModel().setValue("aos_productno", aos_mkt_popadjusts_data.get("aos_productno"), i);
			this.getModel().setValue("aos_itemnumer", aos_mkt_popadjusts_data.get("aos_itemnumer"), i);
			this.getModel().setValue("aos_categroy1", aos_mkt_popadjusts_data.get("aos_categroy1"), i);
			this.getModel().setValue("aos_categroy2", aos_mkt_popadjusts_data.get("aos_categroy2"), i);
			this.getModel().setValue("aos_categroy3", aos_mkt_popadjusts_data.get("aos_categroy3"), i);
			this.getModel().setValue("aos_roi", aos_mkt_popadjusts_data.get("aos_roi"), i);
			this.getModel().setValue("aos_expouse", aos_mkt_popadjusts_data.get("aos_expouse"), i);
			this.getModel().setValue("aos_costrate", aos_mkt_popadjusts_data.get("aos_costrate"), i);
			this.getModel().setValue("aos_spend", aos_mkt_popadjusts_data.get("aos_spend"), i);
			this.getModel().setValue("aos_calprice", aos_mkt_popadjusts_data.get("aos_calprice"), i);
			this.getModel().setValue("aos_adjprice", aos_mkt_popadjusts_data.get("aos_adjprice"), i);
			this.getModel().setValue("aos_description", aos_mkt_popadjusts_data.get("aos_description"), i);
			this.getModel().setValue("aos_bidsuggest", aos_mkt_popadjusts_data.get("aos_bidsuggest"), i);
			this.getModel().setValue("aos_bidrangestart", aos_mkt_popadjusts_data.get("aos_bidrangestart"), i);
			this.getModel().setValue("aos_bidrangeend", aos_mkt_popadjusts_data.get("aos_bidrangeend"), i);
			this.getModel().setValue("aos_lastdate", aos_mkt_popadjusts_data.get("aos_lastdate"), i);
			this.getModel().setValue("aos_entryid", aos_mkt_popadjusts_data.get("EntryID"), i);
			this.getModel().setValue("aos_ppcentryid", aos_mkt_popadjusts_data.get("aos_ppcentryid"), i);
			this.getModel().setValue("aos_ratio", aos_mkt_popadjusts_data.getBigDecimal("aos_ratio"), i);
			this.getModel().setValue("aos_lastprice", aos_mkt_popadjusts_data.getBigDecimal("aos_lastprice"), i);

			this.getModel().setValue("aos_avadays", aos_mkt_popadjusts_data.get("aos_avadays"), i);
			this.getModel().setValue("aos_contryentrystatus", aos_mkt_popadjusts_data.get("aos_contryentrystatus"), i);
			this.getModel().setValue("aos_seasonseting", aos_mkt_popadjusts_data.get("aos_seasonseting"), i);
			this.getModel().setValue("aos_cn_name", aos_mkt_popadjusts_data.get("aos_cn_name"), i);
			this.getModel().setValue("aos_activity", aos_mkt_popadjusts_data.get("aos_activity"), i);
			this.getModel().setValue("aos_highvalue", aos_mkt_popadjusts_data.get("aos_highvalue"), i);
			this.getModel().setValue("aos_mark", aos_mkt_popadjusts_data.get("aos_mark"), i);
			this.getModel().setValue("aos_basestitem", aos_mkt_popadjusts_data.get("aos_basestitem"), i);
			i++;
		}
		generate_line_color();
	}

	public void entryRowClick(RowClickEvent evt) {
		Control source = (Control) evt.getSource();
		if (source.getKey().equals("aos_detailentry")) {
			int CurrentRowIndex = this.getModel().getEntryCurrentRowIndex("aos_detailentry");
			Object aos_entryid = this.getModel().getValue("aos_entryid", CurrentRowIndex);
			InitGroup(aos_entryid);
			InitMarket(aos_entryid);
		}
	}

	private void InitMarket(Object aos_entryid) {
		this.getModel().deleteEntryData("aos_marketentry");
		Object fid = this.getView().getParentView().getModel().getDataEntity().getPkValue();
		QFilter Qf_id = new QFilter("aos_sourceid", "=", fid);
		QFilter Qf_EntryID = new QFilter("aos_detailentry.id", "=", aos_entryid);
		QFilter[] filters = new QFilter[] { Qf_id, Qf_EntryID };
		String SelectField = "aos_detailentry.aos_marketentry.aos_date_p aos_date_p,"
				+ "aos_detailentry.aos_marketentry.aos_marketprice aos_marketprice";
		DynamicObjectCollection aos_mkt_popadjusts_dataS = QueryServiceHelper.query("aos_mkt_popadjusts_data",
				SelectField, filters, " aos_detailentry.aos_marketentry.aos_date_p desc");
		int i = 0;
		for (DynamicObject aos_mkt_popadjusts_data : aos_mkt_popadjusts_dataS) {
			this.getModel().batchCreateNewEntryRow("aos_marketentry", 1);
			this.getModel().setValue("aos_date_p", aos_mkt_popadjusts_data.get("aos_date_p"), i);
			this.getModel().setValue("aos_marketprice", aos_mkt_popadjusts_data.get("aos_marketprice"), i);
			i++;
		}
	}

	private void InitGroup(Object aos_entryid) {
		this.getModel().deleteEntryData("aos_groupentry");
		Object fid = this.getView().getParentView().getModel().getDataEntity().getPkValue();
		QFilter Qf_id = new QFilter("aos_sourceid", "=", fid);
		QFilter Qf_EntryID = new QFilter("aos_detailentry.id", "=", aos_entryid);
		QFilter[] filters = new QFilter[] { Qf_id, Qf_EntryID };

		String SelectField = "aos_detailentry.aos_groupentry.aos_date_g aos_date_g,"
				+ "aos_detailentry.aos_groupentry.aos_roi_g aos_roi_g,"
				+ "aos_detailentry.aos_groupentry.aos_exp_g aos_exp_g,"
				+ "aos_detailentry.aos_groupentry.aos_bid_g aos_bid_g,"
				+ "aos_detailentry.aos_groupentry.aos_spend_g aos_spend_g,"
				+ "aos_detailentry.aos_groupentry.aos_budget_g aos_budget_g,"
				+ "aos_detailentry.aos_groupentry.aos_ppcbid aos_ppcbid,"
				+ "aos_detailentry.aos_groupentry.aos_topprice aos_topprice";
		DynamicObjectCollection aos_mkt_popadjusts_dataS = QueryServiceHelper.query("aos_mkt_popadjusts_data",
				SelectField, filters, " aos_detailentry.aos_groupentry.aos_date_g desc");
		int i = 0;
		for (DynamicObject aos_mkt_popadjusts_data : aos_mkt_popadjusts_dataS) {
			this.getModel().batchCreateNewEntryRow("aos_groupentry", 1);
			this.getModel().setValue("aos_date_g", aos_mkt_popadjusts_data.get("aos_date_g"), i);
			this.getModel().setValue("aos_roi_g", aos_mkt_popadjusts_data.get("aos_roi_g"), i);
			this.getModel().setValue("aos_exp_g", aos_mkt_popadjusts_data.get("aos_exp_g"), i);
			this.getModel().setValue("aos_bid_g", aos_mkt_popadjusts_data.get("aos_bid_g"), i);
			this.getModel().setValue("aos_spend_g", aos_mkt_popadjusts_data.get("aos_spend_g"), i);
			this.getModel().setValue("aos_budget_g", aos_mkt_popadjusts_data.get("aos_budget_g"), i);

			this.getModel().setValue("aos_ppcbid", aos_mkt_popadjusts_data.getBigDecimal("aos_ppcbid"), i);
			this.getModel().setValue("aos_topprice", aos_mkt_popadjusts_data.getBigDecimal("aos_topprice"), i);
			i++;
		}
	}

	@SuppressWarnings("deprecation")
	private void generate_line_color() {
		int rowCount = this.getModel().getEntryRowCount("aos_detailentry");
		for (int i = 0; i < rowCount; i++) {
			Object aos_adjprice = this.getModel().getValue("aos_adjprice", i);
			Object aos_calprice = this.getModel().getValue("aos_calprice", i);
			if (aos_adjprice != null && aos_calprice != null) {
				BigDecimal aos_adjprice_b = (BigDecimal) aos_adjprice;
				BigDecimal aos_calprice_b = (BigDecimal) aos_calprice;
				if (aos_calprice_b.compareTo(BigDecimal.ZERO) != 0) {
					if (aos_adjprice_b.divide(aos_calprice_b, 2, BigDecimal.ROUND_HALF_UP)
							.subtract(BigDecimal.valueOf(1)).abs().compareTo(BigDecimal.valueOf(0.2)) > 0) {
						AbstractGrid grid = this.getView().getControl("aos_detailentry");
						List<CellStyle> cslist = set_Forecolor("aos_description", i, "#fb2323");
						grid.setCellStyle(cslist);
					}
				}
			}
		}
	}

	public static List<CellStyle> set_Forecolor(String control, int row, String cololr) {
		if (cololr == null || cololr.equals(""))
			cololr = "#fbddaf";
		CellStyle cs = new CellStyle();
		cs.setForeColor(cololr);
		cs.setFieldKey(control);// 列标识
		cs.setRow(row);// 行索引
		List<CellStyle> csList = new ArrayList<>();
		csList.add(cs);
		return csList;
	}

	public void propertyChanged(PropertyChangedArgs e) {
		String name = e.getProperty().getName();
		if (name.equals("aos_calprice") || name.equals("aos_adjprice"))
			aos_color_change();
		// 手动出价调整，修改调整标记
		if (name.equals("aos_adjprice")) {
			int rowIndex = e.getChangeSet()[0].getRowIndex();
			if (rowIndex >= 0) {
				Optional<String> op_mark = Optional
						.ofNullable(this.getModel().getValue("aos_mark", rowIndex).toString());
				if (!Boolean.valueOf(op_mark.orElse("false"))) {
					this.getModel().setValue("aos_mark", true, rowIndex);
				}
			}
		}
	}

	@SuppressWarnings("deprecation")
	private void aos_color_change() {
		int i = this.getModel().getEntryCurrentRowIndex("aos_detailentry");
		Object aos_adjprice = this.getModel().getValue("aos_adjprice", i);
		Object aos_calprice = this.getModel().getValue("aos_calprice", i);
		if (aos_adjprice != null && aos_calprice != null) {
			BigDecimal aos_adjprice_b = (BigDecimal) aos_adjprice;
			BigDecimal aos_calprice_b = (BigDecimal) aos_calprice;
			if (aos_calprice_b.compareTo(BigDecimal.ZERO) != 0) {
				if (aos_adjprice_b.divide(aos_calprice_b, 2, BigDecimal.ROUND_HALF_UP).subtract(BigDecimal.valueOf(1))
						.abs().compareTo(BigDecimal.valueOf(0.2)) > 0) {
					AbstractGrid grid = this.getView().getControl("aos_detailentry");
					List<CellStyle> cslist = set_Forecolor("aos_description", i, "#fb2323");
					grid.setCellStyle(cslist);
				}
			}
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

	private static BigDecimal min(BigDecimal Value1, BigDecimal Value2) {
		if (Value1.compareTo(Value2) > -1)
			return Value2;
		else
			return Value1;
	}

}