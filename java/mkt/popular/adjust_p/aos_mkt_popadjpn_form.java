package mkt.popular.adjust_p;

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

public class aos_mkt_popadjpn_form extends AbstractFormPlugin implements ItemClickListener, RowClickEventListener {

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
				|| Control.equals("aos_subtract") || Control.equals("aos_set"))
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

	private void aos_confirm() {
		Object aos_ppcid = this.getView().getParentView().getModel().getValue("aos_ppcid");
		DynamicObject aos_mkt_popular_ppc = BusinessDataServiceHelper.loadSingle(aos_ppcid, "aos_mkt_popular_ppc");
		aos_mkt_popular_ppc.set("aos_adjustsp", true);
		OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_popular_ppc",
				new DynamicObject[] { aos_mkt_popular_ppc }, OperateOption.create());
		if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
		}

		Boolean aos_adjusts = aos_mkt_popular_ppc.getBoolean("aos_adjusts");
		if (aos_adjusts) {
			String p_ou_code = ((DynamicObject) this.getView().getParentView().getModel().getValue("aos_orgid"))
					.getString("number");
			// 开始生成 预算调整(推广)
			Map<String, Object> budget = new HashMap<>();
			budget.put("p_ou_code", p_ou_code);
			aos_mkt_popbudgetp_init.executerun(budget);
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
			BigDecimal aos_adjprice = (BigDecimal) this.getModel().getValue("aos_adjprice", i);
			long aos_entryid = (long) this.getModel().getValue("aos_entryid", i);
			long aos_ppcentryid = (long) this.getModel().getValue("aos_ppcentryid", i);
			Object aos_mark = this.getModel().getValue("aos_mark", i);
			if (aos_mark.toString().equals("true"))
				aos_mark = 1;
			else
				aos_mark = 0;
			Update(aos_entryid, aos_adjprice, aos_ppcentryid, aos_mark);
		}
		this.getView().showSuccessNotification("保存成功!");
	}

	private void Update(long aos_entryid, BigDecimal aos_adjprice, long aos_ppcentryid, Object aos_mark) {
		String sql = " UPDATE tk_aos_mkt_popadpdata_r r " + " SET fk_aos_adjprice = ? " + ",fk_aos_mark= ?"
				+ " WHERE 1=1 " + " and r.FEntryId = ?  ";
		Object[] params = { aos_adjprice, aos_mark, aos_entryid };
		DB.execute(DBRoute.of(DB_MKT), sql, params);

		SimpleDateFormat DF = new SimpleDateFormat("yyyy-MM-dd");
		String today = DF.format(new Date());
		sql = " UPDATE tk_aos_mkt_popular_ppc_r r " + " SET fk_aos_bid = ?," + " fk_aos_lastpricedate = '" + today
				+ "' " + " WHERE 1=1 " + " and r.FEntryId = ? " + "  and fk_aos_lastbid <> ?   ";
		Object[] params2 = { aos_adjprice, aos_ppcentryid, aos_adjprice };
		DB.execute(DBRoute.of(DB_MKT), sql, params2);

		sql = " UPDATE tk_aos_mkt_popular_ppc_r r " + " SET fk_aos_bid = ? " + " WHERE 1=1 " + " and r.FEntryId = ? "
				+ "  and fk_aos_lastbid = ?   ";
		Object[] params3 = { aos_adjprice, aos_ppcentryid, aos_adjprice };
		DB.execute(DBRoute.of(DB_MKT), sql, params3);
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
			BigDecimal aos_lastprice = (BigDecimal) this.getModel().getValue("aos_lastprice", SelectRows[i]);
			BigDecimal aos_highvalue = (BigDecimal) this.getModel().getValue("aos_highvalue", SelectRows[i]);
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
				result = aos_adjustrate;
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
			filter_roifrom = new QFilter("aos_detailentry.aos_grouproi", ">=", aos_roifrom);
		QFilter filter_roito = null;
		if (aos_roito != null && aos_roito.toString().length() > 0)
			filter_roito = new QFilter("aos_detailentry.aos_grouproi", "<=", aos_roito);
		QFilter filter_expfrom = null;
		if (aos_expfrom != null && aos_expfrom.toString().length() > 0)
			filter_expfrom = new QFilter("aos_detailentry.aos_avgexp", ">=", aos_expfrom);
		QFilter filter_expto = null;
		if (aos_expto != null && aos_expto.toString().length() > 0)
			filter_expto = new QFilter("aos_detailentry.aos_avgexp", "<=", aos_expto);
		QFilter filter_spendfrom = null;
		if (aos_spendfrom != null && aos_spendfrom.toString().length() > 0)
			filter_spendfrom = new QFilter("aos_detailentry.aos_groupcost", ">=", aos_spendfrom);
		QFilter filter_spendto = null;
		if (aos_spendto != null && aos_spendto.toString().length() > 0)
			filter_spendto = new QFilter("aos_detailentry.aos_groupcost", "<=", aos_spendto);

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
		String SelectField = "aos_detailentry.aos_poptype aos_poptype,"
				+ "aos_detailentry.aos_productno aos_productno, " + "aos_detailentry.aos_type aos_type,"
				+ "aos_detailentry.aos_season aos_season," + "aos_detailentry.aos_itemstatus aos_itemstatus,"
				+ "aos_detailentry.aos_itemname aos_itemname," + "aos_detailentry.aos_itemnumer aos_itemnumer,"
				+ "aos_detailentry.aos_avadays aos_avadays," + "aos_detailentry.aos_grouproi aos_grouproi,"
				+ "aos_detailentry.aos_avgexp aos_avgexp," + "aos_detailentry.aos_exprate aos_exprate,"
				+ "aos_detailentry.aos_serialrate aos_serialrate," + "aos_detailentry.aos_groupcost aos_groupcost,"
				+ "aos_detailentry.aos_lastprice aos_lastprice," + "aos_detailentry.aos_calprice aos_calprice,"
				+ "aos_detailentry.aos_adjprice aos_adjprice," + "aos_detailentry.aos_lastdate aos_lastdate,"
				+ "aos_detailentry.aos_sal_group aos_sal_group," + "aos_detailentry.aos_buybox aos_buybox,"
				+ "aos_detailentry.aos_itemid aos_itemid," + "aos_detailentry.aos_ppcentryid aos_ppcentryid,"
				+ "aos_detailentry.id aos_entryid, " + "aos_detailentry.aos_highvalue aos_highvalue,"
				+ "aos_detailentry.aos_bidsuggest aos_bidsuggest," + "aos_detailentry.aos_mark aos_mark,"
				+ "aos_detailentry.aos_activity aos_activity," + "aos_detailentry.aos_age aos_age,"
				+ "aos_detailentry.aos_rank aos_rank," + "aos_detailentry.aos_overseaqty aos_overseaqty,"
						+ "aos_detailentry.aos_is_saleout aos_is_saleout,"
						+ "aos_detailentry.aos_seasonattr aos_seasonattr,"
						+ "aos_detailentry.aos_sys aos_sys," +
				"aos_detailentry.aos_cpc aos_cpc," +
				"aos_detailentry.aos_ratio aos_ratio";
		DynamicObjectCollection aos_mkt_popadjustp_dataS = QueryServiceHelper.query("aos_mkt_popadjustp_data",
				SelectField, filters, "aos_detailentry.aos_productno");
		int size = aos_mkt_popadjustp_dataS.size();
		this.getModel().setValue("aos_count", size);
		int i = 0;
		for (DynamicObject aos_mkt_popadjustp_data : aos_mkt_popadjustp_dataS) {
			this.getModel().batchCreateNewEntryRow("aos_detailentry", 1);
			this.getModel().setValue("aos_poptype", aos_mkt_popadjustp_data.get("aos_poptype"), i);
			this.getModel().setValue("aos_productno", aos_mkt_popadjustp_data.get("aos_productno"), i);
			this.getModel().setValue("aos_type", aos_mkt_popadjustp_data.get("aos_type"), i);
			this.getModel().setValue("aos_season", aos_mkt_popadjustp_data.get("aos_season"), i);
			this.getModel().setValue("aos_itemstatus", aos_mkt_popadjustp_data.get("aos_itemstatus"), i);
			this.getModel().setValue("aos_itemname", aos_mkt_popadjustp_data.get("aos_itemname"), i);
			this.getModel().setValue("aos_itemnumer", aos_mkt_popadjustp_data.get("aos_itemnumer"), i);
			this.getModel().setValue("aos_avadays", aos_mkt_popadjustp_data.get("aos_avadays"), i);
			this.getModel().setValue("aos_grouproi", aos_mkt_popadjustp_data.get("aos_grouproi"), i);
			this.getModel().setValue("aos_avgexp", aos_mkt_popadjustp_data.get("aos_avgexp"), i);
			this.getModel().setValue("aos_exprate", aos_mkt_popadjustp_data.get("aos_exprate"), i);
			this.getModel().setValue("aos_serialrate", aos_mkt_popadjustp_data.get("aos_serialrate"), i);
			this.getModel().setValue("aos_groupcost", aos_mkt_popadjustp_data.get("aos_groupcost"), i);
			this.getModel().setValue("aos_lastprice", aos_mkt_popadjustp_data.get("aos_lastprice"), i);
			this.getModel().setValue("aos_calprice", aos_mkt_popadjustp_data.get("aos_calprice"), i);
			this.getModel().setValue("aos_adjprice", aos_mkt_popadjustp_data.get("aos_adjprice"), i);
			this.getModel().setValue("aos_lastdate", aos_mkt_popadjustp_data.get("aos_lastdate"), i);
			this.getModel().setValue("aos_sal_group", aos_mkt_popadjustp_data.get("aos_sal_group"), i);
			this.getModel().setValue("aos_buybox", aos_mkt_popadjustp_data.get("aos_buybox"), i);
			this.getModel().setValue("aos_itemid", aos_mkt_popadjustp_data.get("aos_itemid"), i);
			this.getModel().setValue("aos_ppcentryid", aos_mkt_popadjustp_data.get("aos_ppcentryid"), i);
			this.getModel().setValue("aos_entryid", aos_mkt_popadjustp_data.get("aos_entryid"), i);
			this.getModel().setValue("aos_highvalue", aos_mkt_popadjustp_data.get("aos_highvalue"), i);
			this.getModel().setValue("aos_bidsuggest", aos_mkt_popadjustp_data.get("aos_bidsuggest"), i);
			this.getModel().setValue("aos_mark", aos_mkt_popadjustp_data.get("aos_mark"), i);
			this.getModel().setValue("aos_activity", aos_mkt_popadjustp_data.get("aos_activity"), i);
			this.getModel().setValue("aos_age", aos_mkt_popadjustp_data.get("aos_age"), i);
			this.getModel().setValue("aos_rank", aos_mkt_popadjustp_data.get("aos_rank"), i);
			this.getModel().setValue("aos_overseaqty", aos_mkt_popadjustp_data.get("aos_overseaqty"), i);
			this.getModel().setValue("aos_is_saleout", aos_mkt_popadjustp_data.get("aos_is_saleout"), i);
			this.getModel().setValue("aos_seasonattr", aos_mkt_popadjustp_data.get("aos_seasonattr"), i);
			this.getModel().setValue("aos_sys", aos_mkt_popadjustp_data.get("aos_sys"), i);

			this.getModel().setValue("aos_cpc", aos_mkt_popadjustp_data.get("aos_cpc"), i);
			this.getModel().setValue("aos_ratio", aos_mkt_popadjustp_data.get("aos_ratio"), i);
			
			
			BigDecimal aos_lastprice = aos_mkt_popadjustp_data.getBigDecimal("aos_lastprice");
			BigDecimal aos_adjprice = aos_mkt_popadjustp_data.getBigDecimal("aos_adjprice");
			if (aos_lastprice.compareTo(BigDecimal.ZERO) != 0) {
				this.getModel().setValue("aos_rate",
						aos_adjprice.divide(aos_lastprice, 2, BigDecimal.ROUND_HALF_UP).subtract(BigDecimal.valueOf(1)),
						i);
			}
			i++;
		}
	}

	@Override
	public void propertyChanged(PropertyChangedArgs e) {
		String name = e.getProperty().getName();
		// 手动出价调整，修改调整标记
		if (name.equals("aos_adjprice")) {
			int rowIndex = e.getChangeSet()[0].getRowIndex();
			if (rowIndex >= 0) {
				Optional<String> op_mark = Optional
						.ofNullable(this.getModel().getValue("aos_mark", rowIndex).toString());
				if (!Boolean.valueOf(op_mark.orElse("false"))) {
					this.getModel().setValue("aos_mark", true, rowIndex);
				}
				BigDecimal aos_lastprice = (BigDecimal) this.getModel().getValue("aos_lastprice", rowIndex);
				BigDecimal aos_adjprice = (BigDecimal) this.getModel().getValue("aos_adjprice", rowIndex);

				BigDecimal aos_bidsuggest = (BigDecimal) this.getModel().getValue("aos_bidsuggest", rowIndex);



				if (aos_lastprice.compareTo(BigDecimal.ZERO) != 0) {
					this.getModel().setValue("aos_rate", aos_adjprice.divide(aos_lastprice, 2, BigDecimal.ROUND_HALF_UP)
							.subtract(BigDecimal.valueOf(1)), rowIndex);
				}
				if (aos_bidsuggest.compareTo(BigDecimal.ZERO) != 0) {
					this.getModel().setValue("aos_ratio", aos_adjprice.divide(aos_bidsuggest, 2, BigDecimal.ROUND_HALF_UP));
				}



			}
		}
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
		String SelectField = "aos_detailentry.aos_subentryentity1.aos_date_d2 aos_date_d2,"
				+ "aos_detailentry.aos_subentryentity1.aos_bid_suggest aos_bid_suggest,"
				+ "aos_detailentry.aos_subentryentity1.aos_bid_rangestart aos_bid_rangestart,"
				+ "aos_detailentry.aos_subentryentity1.aos_bid_rangeend aos_bid_rangeend";
		DynamicObjectCollection aos_mkt_popadjusts_dataS = QueryServiceHelper.query("aos_mkt_popadjustp_data",
				SelectField, filters, "aos_detailentry.aos_subentryentity1.aos_date_d2 desc");
		int i = 0;
		for (DynamicObject aos_mkt_popadjusts_data : aos_mkt_popadjusts_dataS) {
			this.getModel().batchCreateNewEntryRow("aos_marketentry", 1);
			this.getModel().setValue("aos_date_d2", aos_mkt_popadjusts_data.get("aos_date_d2"), i);
			this.getModel().setValue("aos_bid_suggest", aos_mkt_popadjusts_data.get("aos_bid_suggest"), i);
			this.getModel().setValue("aos_bid_rangestart", aos_mkt_popadjusts_data.get("aos_bid_rangestart"), i);
			this.getModel().setValue("aos_bid_rangeend", aos_mkt_popadjusts_data.get("aos_bid_rangeend"), i);
			i++;
		}
	}

	private void InitGroup(Object aos_entryid) {
		this.getModel().deleteEntryData("aos_groupentry");
		Object fid = this.getView().getParentView().getModel().getDataEntity().getPkValue();
		QFilter Qf_id = new QFilter("aos_sourceid", "=", fid);
		QFilter Qf_EntryID = new QFilter("aos_detailentry.id", "=", aos_entryid);
		QFilter[] filters = new QFilter[] { Qf_id, Qf_EntryID };

		String SelectField = "aos_detailentry.aos_subentryentity.aos_date_d aos_date_d,"
				+ "aos_detailentry.aos_subentryentity.aos_roi aos_roi,"
				+ "aos_detailentry.aos_subentryentity.aos_expouse aos_expouse,"
				+ "aos_detailentry.aos_subentryentity.aos_bid aos_bid,"
				+ "aos_detailentry.aos_subentryentity.aos_cost aos_cost,"
				+ "aos_detailentry.aos_subentryentity.aos_ppcbid as aos_ppcbid,"
				+ "aos_detailentry.aos_subentryentity.aos_topprice as aos_topprice";
		DynamicObjectCollection aos_mkt_popadjusts_dataS = QueryServiceHelper.query("aos_mkt_popadjustp_data",
				SelectField, filters, "aos_detailentry.aos_subentryentity.aos_date_d desc");
		int i = 0;
		for (DynamicObject aos_mkt_popadjusts_data : aos_mkt_popadjusts_dataS) {
			this.getModel().batchCreateNewEntryRow("aos_groupentry", 1);
			this.getModel().setValue("aos_date_d", aos_mkt_popadjusts_data.get("aos_date_d"), i);
			this.getModel().setValue("aos_roi", aos_mkt_popadjusts_data.get("aos_roi"), i);
			this.getModel().setValue("aos_expouse", aos_mkt_popadjusts_data.get("aos_expouse"), i);
			this.getModel().setValue("aos_bid", aos_mkt_popadjusts_data.get("aos_bid"), i);
			this.getModel().setValue("aos_cost", aos_mkt_popadjusts_data.get("aos_cost"), i);
			this.getModel().setValue("aos_ppcbid", aos_mkt_popadjusts_data.get("aos_ppcbid"), i);
			this.getModel().setValue("aos_topprice", aos_mkt_popadjusts_data.get("aos_topprice"), i);
			i++;
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
		} else {
			this.getView().setEnable(true, "aos_detailentry");
			this.getView().setEnable(true, "aos_apply");
			this.getView().setEnable(true, "aos_save");
			this.getView().setEnable(true, "aos_confirm");
		}
	}

	private static BigDecimal min(BigDecimal Value1, BigDecimal Value2) {
		if (Value1.compareTo(Value2) > -1)
			return Value2;
		else
			return Value1;
	}

}