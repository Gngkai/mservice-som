package mkt.popular.sale;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;

import common.fnd.FndGlobal;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.db.DB;
import kd.bos.db.DBRoute;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
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
import mkt.popular.budget.AosMktPopBudgetpTask;

/**
 * @author aosom
 * @version 出价调整销售-动态表单插件
 */
public class AosMktPopAdjsForm extends AbstractFormPlugin implements ItemClickListener, RowClickEventListener {
    private static final String DB_MKT = "aos.mkt";
    private static final String AOS_SELECT = "aos_select";
    private static final String AOS_MULTIPY = "aos_multipy";
    private static final String AOS_DIVIDE = "aos_divide";
    private static final String AOS_ADD = "aos_add";
    private static final String AOS_SUBTRACT = "aos_subtract";
    private static final String AOS_SAVE = "aos_save";
    private static final String AOS_CONFIRM = "aos_confirm";
    private static final String AOS_ADJUST = "aos_adjust";
    private static final String AOS_DETAILENTRY = "aos_detailentry";
    private static final String AOS_CALPRICE = "aos_calprice";
    private static final String AOS_ADJPRICE = "aos_adjprice";
    private static final String FALSE = "false";
    private static final String B = "B";
    private static final int TWO = 2;

    public static List<CellStyle> setForecolor(String control, int row, String cololr) {
        if (FndGlobal.IsNull(cololr)) {
            cololr = "#fbddaf";
        }
        CellStyle cs = new CellStyle();
        cs.setForeColor(cololr);
        // 列标识
        cs.setFieldKey(control);
        // 行索引
        cs.setRow(row);
        List<CellStyle> csList = new ArrayList<>();
        csList.add(cs);
        return csList;
    }

    private static BigDecimal min(BigDecimal value1, BigDecimal value2) {
        if (value1.compareTo(value2) > -1) {
            return value2;
        } else {
            return value1;
        }
    }

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        EntryGrid aosDetailentry = this.getControl("aos_detailentry");
        aosDetailentry.addRowClickListener(this);
        // 给工具栏加监听事件
        this.addItemClickListeners("aos_toolbarap");
        this.addItemClickListeners("aos_select");
        this.addItemClickListeners("aos_apply");
        this.addItemClickListeners("aos_save");
        this.addItemClickListeners("aos_confirm");
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String control = evt.getItemKey();
        if (AOS_SELECT.equals(control)) {
            aosSelect();
        } else if (AOS_MULTIPY.equals(control) || AOS_DIVIDE.equals(control) || AOS_ADD.equals(control)
            || AOS_SUBTRACT.equals(control)) {
            aosApply(control);
        } else if (AOS_SAVE.equals(control)) {
            aosSave();
        } else if (AOS_CONFIRM.equals(control)) {
            aosConfirm();
        } else if (AOS_ADJUST.equals(control)) {
            aosAdjust();
        }
    }

    /**
     * 建议价*调整比例 应用至销售出价 字段
     */
    private void aosAdjust() {
        Object aosAdjustrate = this.getModel().getValue("aos_adjustrate");
        if (aosAdjustrate != null) {
            EntryGrid aosDetailentry = this.getControl("aos_detailentry");
            int[] selectRows = aosDetailentry.getSelectRows();
            for (int selectRow : selectRows) {
                BigDecimal aosAdjprice = (BigDecimal)this.getModel().getValue("aos_bidsuggest", selectRow);
                BigDecimal aosHighvalue = (BigDecimal)this.getModel().getValue("aos_highvalue", selectRow);
                this.getModel().setValue("aos_adjprice",
                    min(aosAdjprice.multiply((BigDecimal)aosAdjustrate), aosHighvalue), selectRow);
            }
        }
        this.getView().showSuccessNotification("建议价应用成功!");
    }

    private void aosConfirm() {
        Object aosPpcid = this.getView().getParentView().getModel().getValue("aos_ppcid");
        QFilter qfId = new QFilter("aos_ppcid", "=", aosPpcid);
        QFilter[] filters = new QFilter[] {qfId};
        String selectField = "id";
        DynamicObjectCollection aosMktPopularAdjsS =
            QueryServiceHelper.query("aos_mkt_popular_adjs", selectField, filters);
        int total = aosMktPopularAdjsS.size();
        QFilter qfStatus = new QFilter("aos_status", "=", "B");
        filters = new QFilter[] {qfId, qfStatus};
        aosMktPopularAdjsS = QueryServiceHelper.query("aos_mkt_popular_adjs", selectField, filters);
        int confirm = aosMktPopularAdjsS.size();
        if (total == confirm + 1) {
            DynamicObject aosMktPopularPpc = BusinessDataServiceHelper.loadSingle(aosPpcid, "aos_mkt_popular_ppc");
            aosMktPopularPpc.set("aos_adjusts", true);
            OperationServiceHelper.executeOperate("save", "aos_mkt_popular_ppc", new DynamicObject[] {aosMktPopularPpc},
                OperateOption.create());
            boolean aosAdjustsp = aosMktPopularPpc.getBoolean("aos_adjustsp");
            if (aosAdjustsp) {
                String pOuCode = ((DynamicObject)this.getView().getParentView().getModel().getValue("aos_orgid"))
                    .getString("number");
                // 开始生成 出价调整(销售)
                Map<String, Object> budget = new HashMap<>(16);
                budget.put("p_ou_code", pOuCode);
                AosMktPopBudgetpTask.executerun(budget);
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

    private void aosSave() {
        int rowCount = this.getModel().getEntryRowCount("aos_detailentry");
        for (int i = 0; i < rowCount; i++) {
            BigDecimal aosAdjprice = (BigDecimal)this.getModel().getValue("aos_adjprice", i);
            String aosDescription = (String)this.getModel().getValue("aos_description", i);
            long aosEntryid = (long)this.getModel().getValue("aos_entryid", i);
            long aosPpcentryid = (long)this.getModel().getValue("aos_ppcentryid", i);
            Object aosMark = this.getModel().getValue("aos_mark", i);
            if ("true".equals(aosMark.toString())) {
                aosMark = 1;
            } else {
                aosMark = 0;
            }
            update(aosEntryid, aosAdjprice, aosDescription, aosPpcentryid, aosMark);
        }
        this.getView().showSuccessNotification("保存成功!");
    }

    private void update(long aosEntryid, BigDecimal aosAdjprice, String aosDescription, long aosPpcentryid,
        Object aosMark) {
        String sql = " UPDATE tk_aos_mkt_adjsdata_r r " + " SET fk_aos_adjprice = ?," + " fk_aos_description = ?, "
            + "fk_aos_mark= ?" + " WHERE 1=1 " + " and r.FEntryId = ?  ";
        Object[] params = {aosAdjprice, aosDescription, aosMark, aosEntryid};
        DB.execute(DBRoute.of(DB_MKT), sql, params);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String today = df.format(new Date());
        sql = " UPDATE tk_aos_mkt_popular_ppc_r r " + " SET fk_aos_bid = ? , " + " fk_aos_salemanual = 1 , "
            + " fk_aos_lastpricedate =  '" + today + "'  " + " WHERE 1=1 " + " and r.FEntryId = ? "
            + " and fk_aos_lastbid <> ?  ";
        Object[] params2 = {aosAdjprice, aosPpcentryid, aosAdjprice};
        DB.execute(DBRoute.of(DB_MKT), sql, params2);
    }

    private void aosApply(String sign) {
        BigDecimal aosAdjustrate = (BigDecimal)this.getModel().getValue("aos_adjustrate");
        boolean cond =
            ("aos_divide".equals(sign) && (aosAdjustrate == null || aosAdjustrate.compareTo(BigDecimal.ZERO) == 0));
        if (cond) {
            this.getView().showTipNotification("除以时,调整比例不能为0");
            return;
        }
        EntryGrid aosDetailentry = this.getControl("aos_detailentry");
        int[] selectRows = aosDetailentry.getSelectRows();
        int rowCount = selectRows.length;
        if (rowCount == 0) {
            this.getView().showTipNotification("至少选择一行数据");
            return;
        }
        for (int selectRow : selectRows) {
            BigDecimal aosHighvalue = (BigDecimal)this.getModel().getValue("aos_highvalue", selectRow);
            BigDecimal aosLastprice = (BigDecimal)this.getModel().getValue("aos_lastprice", selectRow);
            BigDecimal result = BigDecimal.ZERO;
            switch (sign) {
                case "aos_multipy":
                    result = aosLastprice.multiply(aosAdjustrate);
                    break;
                case "aos_divide":
                    result = aosLastprice.divide(aosAdjustrate, 2, RoundingMode.HALF_UP);
                    break;
                case "aos_add":
                    result = aosLastprice.add(aosAdjustrate);
                    break;
                case "aos_subtract":
                    result = aosLastprice.subtract(aosAdjustrate);
                    break;
                default:
                    break;
            }
            this.getModel().setValue("aos_adjprice", min(result, aosHighvalue), selectRow);
        }
        this.getView().showSuccessNotification("应用成功!");
    }

    private void aosSelect() {
        // 筛选之前自动保存
        aosSave();
        Object aosRoifrom = this.getModel().getValue("aos_roifrom");
        Object aosRoito = this.getModel().getValue("aos_roito");
        Object aosExpfrom = this.getModel().getValue("aos_expfrom");
        Object aosExpto = this.getModel().getValue("aos_expto");
        Object aosSpendfrom = this.getModel().getValue("aos_spendfrom");
        Object aosSpendto = this.getModel().getValue("aos_spendto");
        QFilter filterRoifrom = null;
        if (aosRoifrom != null && aosRoifrom.toString().length() > 0) {
            filterRoifrom = new QFilter("aos_detailentry.aos_roi", ">=", aosRoifrom);
        }
        QFilter filterRoito = null;
        if (aosRoito != null && aosRoito.toString().length() > 0) {
            filterRoito = new QFilter("aos_detailentry.aos_roi", "<=", aosRoito);
        }
        QFilter filterExpfrom = null;
        if (aosExpfrom != null && aosExpfrom.toString().length() > 0) {
            filterExpfrom = new QFilter("aos_detailentry.aos_expouse", ">=", aosExpfrom);
        }
        QFilter filterExpto = null;
        if (aosExpto != null && aosExpto.toString().length() > 0) {
            filterExpto = new QFilter("aos_detailentry.aos_expouse", "<=", aosExpto);
        }
        QFilter filterSpendfrom = null;
        if (aosSpendfrom != null && aosSpendfrom.toString().length() > 0) {
            filterSpendfrom = new QFilter("aos_detailentry.aos_spend", ">=", aosSpendfrom);
        }
        QFilter filterSpendto = null;
        if (aosSpendto != null && aosSpendto.toString().length() > 0) {
            filterSpendto = new QFilter("aos_detailentry.aos_spend", "<=", aosSpendto);
        }
        ArrayList<QFilter> arrQ = new ArrayList<>();
        arrQ.add(filterRoifrom);
        arrQ.add(filterRoito);
        arrQ.add(filterExpfrom);
        arrQ.add(filterExpto);
        arrQ.add(filterSpendfrom);
        arrQ.add(filterSpendto);
        initData(arrQ);
    }

    @Override
    public void afterCreateNewData(EventObject e) {
        // 初始化数据
        initData(null);
    }

    private void initData(ArrayList<QFilter> arrQ) {
        this.getModel().deleteEntryData("aos_detailentry");
        Object fid = this.getView().getParentView().getModel().getDataEntity().getPkValue();
        QFilter qfId = new QFilter("aos_sourceid", "=", fid);
        QFilter[] filters;
        if (arrQ == null) {
            filters = new QFilter[] {qfId};
        } else {
            arrQ.add(qfId);
            int size = arrQ.size();
            filters = arrQ.toArray(new QFilter[size]);
        }
        String selectField =
            "aos_detailentry.aos_productno aos_productno," + "aos_detailentry.aos_itemnumer aos_itemnumer,"
                + "aos_detailentry.aos_categroy1 aos_categroy1," + "aos_detailentry.aos_categroy2 aos_categroy2,"
                + "aos_detailentry.aos_categroy3 aos_categroy3," + "aos_detailentry.aos_avadays aos_avadays,"
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
        DynamicObjectCollection aosMktPopadjustsDataS =
            QueryServiceHelper.query("aos_mkt_popadjusts_data", selectField, filters, "aos_detailentry.aos_productno");
        int i = 0;
        for (DynamicObject aosMktPopadjustsData : aosMktPopadjustsDataS) {
            this.getModel().batchCreateNewEntryRow("aos_detailentry", 1);
            this.getModel().setValue("aos_productno", aosMktPopadjustsData.get("aos_productno"), i);
            this.getModel().setValue("aos_itemnumer", aosMktPopadjustsData.get("aos_itemnumer"), i);
            this.getModel().setValue("aos_categroy1", aosMktPopadjustsData.get("aos_categroy1"), i);
            this.getModel().setValue("aos_categroy2", aosMktPopadjustsData.get("aos_categroy2"), i);
            this.getModel().setValue("aos_categroy3", aosMktPopadjustsData.get("aos_categroy3"), i);
            this.getModel().setValue("aos_roi", aosMktPopadjustsData.get("aos_roi"), i);
            this.getModel().setValue("aos_expouse", aosMktPopadjustsData.get("aos_expouse"), i);
            this.getModel().setValue("aos_costrate", aosMktPopadjustsData.get("aos_costrate"), i);
            this.getModel().setValue("aos_spend", aosMktPopadjustsData.get("aos_spend"), i);
            this.getModel().setValue("aos_calprice", aosMktPopadjustsData.get("aos_calprice"), i);
            this.getModel().setValue("aos_adjprice", aosMktPopadjustsData.get("aos_adjprice"), i);
            this.getModel().setValue("aos_description", aosMktPopadjustsData.get("aos_description"), i);
            this.getModel().setValue("aos_bidsuggest", aosMktPopadjustsData.get("aos_bidsuggest"), i);
            this.getModel().setValue("aos_bidrangestart", aosMktPopadjustsData.get("aos_bidrangestart"), i);
            this.getModel().setValue("aos_bidrangeend", aosMktPopadjustsData.get("aos_bidrangeend"), i);
            this.getModel().setValue("aos_lastdate", aosMktPopadjustsData.get("aos_lastdate"), i);
            this.getModel().setValue("aos_entryid", aosMktPopadjustsData.get("EntryID"), i);
            this.getModel().setValue("aos_ppcentryid", aosMktPopadjustsData.get("aos_ppcentryid"), i);
            this.getModel().setValue("aos_ratio", aosMktPopadjustsData.getBigDecimal("aos_ratio"), i);
            this.getModel().setValue("aos_lastprice", aosMktPopadjustsData.getBigDecimal("aos_lastprice"), i);
            this.getModel().setValue("aos_avadays", aosMktPopadjustsData.get("aos_avadays"), i);
            this.getModel().setValue("aos_contryentrystatus", aosMktPopadjustsData.get("aos_contryentrystatus"), i);
            this.getModel().setValue("aos_seasonseting", aosMktPopadjustsData.get("aos_seasonseting"), i);
            this.getModel().setValue("aos_cn_name", aosMktPopadjustsData.get("aos_cn_name"), i);
            this.getModel().setValue("aos_activity", aosMktPopadjustsData.get("aos_activity"), i);
            this.getModel().setValue("aos_highvalue", aosMktPopadjustsData.get("aos_highvalue"), i);
            this.getModel().setValue("aos_mark", aosMktPopadjustsData.get("aos_mark"), i);
            this.getModel().setValue("aos_basestitem", aosMktPopadjustsData.get("aos_basestitem"), i);
            i++;
        }
        generateLineColor();
    }

    @Override
    public void entryRowClick(RowClickEvent evt) {
        Control source = (Control)evt.getSource();
        if (AOS_DETAILENTRY.equals(source.getKey())) {
            int currentRowIndex = this.getModel().getEntryCurrentRowIndex("aos_detailentry");
            Object aosEntryid = this.getModel().getValue("aos_entryid", currentRowIndex);
            initGroup(aosEntryid);
            initMarket(aosEntryid);
        }
    }

    private void initMarket(Object aosEntryid) {
        this.getModel().deleteEntryData("aos_marketentry");
        Object fid = this.getView().getParentView().getModel().getDataEntity().getPkValue();
        QFilter qfId = new QFilter("aos_sourceid", "=", fid);
        QFilter qfEntryId = new QFilter("aos_detailentry.id", "=", aosEntryid);
        QFilter[] filters = new QFilter[] {qfId, qfEntryId};
        String selectField = "aos_detailentry.aos_marketentry.aos_date_p aos_date_p,"
            + "aos_detailentry.aos_marketentry.aos_marketprice aos_marketprice";
        DynamicObjectCollection aosMktPopadjustsDataS = QueryServiceHelper.query("aos_mkt_popadjusts_data", selectField,
            filters, " aos_detailentry.aos_marketentry.aos_date_p desc");
        int i = 0;
        for (DynamicObject aosMktPopadjustsData : aosMktPopadjustsDataS) {
            this.getModel().batchCreateNewEntryRow("aos_marketentry", 1);
            this.getModel().setValue("aos_date_p", aosMktPopadjustsData.get("aos_date_p"), i);
            this.getModel().setValue("aos_marketprice", aosMktPopadjustsData.get("aos_marketprice"), i);
            i++;
        }
    }

    private void initGroup(Object aosEntryid) {
        this.getModel().deleteEntryData("aos_groupentry");
        Object fid = this.getView().getParentView().getModel().getDataEntity().getPkValue();
        QFilter qfId = new QFilter("aos_sourceid", "=", fid);
        QFilter qfEntryId = new QFilter("aos_detailentry.id", "=", aosEntryid);
        QFilter[] filters = new QFilter[] {qfId, qfEntryId};
        String selectField = "aos_detailentry.aos_groupentry.aos_date_g aos_date_g,"
            + "aos_detailentry.aos_groupentry.aos_roi_g aos_roi_g,"
            + "aos_detailentry.aos_groupentry.aos_exp_g aos_exp_g,"
            + "aos_detailentry.aos_groupentry.aos_bid_g aos_bid_g,"
            + "aos_detailentry.aos_groupentry.aos_spend_g aos_spend_g,"
            + "aos_detailentry.aos_groupentry.aos_budget_g aos_budget_g,"
            + "aos_detailentry.aos_groupentry.aos_ppcbid aos_ppcbid,"
            + "aos_detailentry.aos_groupentry.aos_topprice aos_topprice";
        DynamicObjectCollection aosMktPopadjustsDataS = QueryServiceHelper.query("aos_mkt_popadjusts_data", selectField,
            filters, " aos_detailentry.aos_groupentry.aos_date_g desc");
        int i = 0;
        for (DynamicObject aosMktPopadjustsData : aosMktPopadjustsDataS) {
            this.getModel().batchCreateNewEntryRow("aos_groupentry", 1);
            this.getModel().setValue("aos_date_g", aosMktPopadjustsData.get("aos_date_g"), i);
            this.getModel().setValue("aos_roi_g", aosMktPopadjustsData.get("aos_roi_g"), i);
            this.getModel().setValue("aos_exp_g", aosMktPopadjustsData.get("aos_exp_g"), i);
            this.getModel().setValue("aos_bid_g", aosMktPopadjustsData.get("aos_bid_g"), i);
            this.getModel().setValue("aos_spend_g", aosMktPopadjustsData.get("aos_spend_g"), i);
            this.getModel().setValue("aos_budget_g", aosMktPopadjustsData.get("aos_budget_g"), i);
            this.getModel().setValue("aos_ppcbid", aosMktPopadjustsData.getBigDecimal("aos_ppcbid"), i);
            this.getModel().setValue("aos_topprice", aosMktPopadjustsData.getBigDecimal("aos_topprice"), i);
            i++;
        }
    }

    private void generateLineColor() {
        int rowCount = this.getModel().getEntryRowCount("aos_detailentry");
        for (int i = 0; i < rowCount; i++) {
            Object aosAdjprice = this.getModel().getValue("aos_adjprice", i);
            Object aosCalprice = this.getModel().getValue("aos_calprice", i);
            if (aosAdjprice != null && aosCalprice != null) {
                BigDecimal aosAdjpriceB = (BigDecimal)aosAdjprice;
                BigDecimal aosCalpriceB = (BigDecimal)aosCalprice;
                if (aosCalpriceB.compareTo(BigDecimal.ZERO) != 0) {
                    if (aosAdjpriceB.divide(aosCalpriceB, 2, RoundingMode.HALF_UP).subtract(BigDecimal.valueOf(1)).abs()
                        .compareTo(BigDecimal.valueOf(0.2)) > 0) {
                        AbstractGrid grid = this.getView().getControl("aos_detailentry");
                        List<CellStyle> cslist = setForecolor("aos_description", i, "#fb2323");
                        grid.setCellStyle(cslist);
                    }
                }
            }
        }
    }

    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();
        if (AOS_CALPRICE.equals(name) || AOS_ADJPRICE.equals(name)) {
            aosColorChange();
        }
        // 手动出价调整，修改调整标记
        if (AOS_ADJPRICE.equals(name)) {
            int rowIndex = e.getChangeSet()[0].getRowIndex();
            if (rowIndex >= 0) {
                Optional<String> opMark =
                    Optional.ofNullable(this.getModel().getValue("aos_mark", rowIndex).toString());
                if (!Boolean.parseBoolean(opMark.orElse(FALSE))) {
                    this.getModel().setValue("aos_mark", true, rowIndex);
                }
            }
        }
    }

    private void aosColorChange() {
        int i = this.getModel().getEntryCurrentRowIndex("aos_detailentry");
        Object aosAdjprice = this.getModel().getValue("aos_adjprice", i);
        Object aosCalprice = this.getModel().getValue("aos_calprice", i);
        if (aosAdjprice != null && aosCalprice != null) {
            BigDecimal aosAdjpriceB = (BigDecimal)aosAdjprice;
            BigDecimal aosCalpriceB = (BigDecimal)aosCalprice;
            if (aosCalpriceB.compareTo(BigDecimal.ZERO) != 0) {
                if (aosAdjpriceB.divide(aosCalpriceB, TWO, RoundingMode.HALF_UP).subtract(BigDecimal.valueOf(1)).abs()
                    .compareTo(BigDecimal.valueOf(0.2)) > 0) {
                    AbstractGrid grid = this.getView().getControl("aos_detailentry");
                    List<CellStyle> cslist = setForecolor("aos_description", i, "#fb2323");
                    grid.setCellStyle(cslist);
                }
            }
        }
    }

    @Override
    public void beforeClosed(BeforeClosedEvent e) {
        super.beforeClosed(e);
        e.setCheckDataChange(false);
    }

    @Override
    public void afterBindData(EventObject e) {
        super.afterBindData(e);
        String aosStatus = String.valueOf(this.getView().getParentView().getModel().getValue("aos_status"));
        if (B.equals(aosStatus)) {
            this.getView().setEnable(false, "aos_detailentry");
            this.getView().setEnable(false, "aos_apply");
            this.getView().setEnable(false, "aos_save");
            this.getView().setEnable(false, "aos_confirm");
        }
        // 操作人员
        long user = UserServiceHelper.getCurrentUserId();
        DynamicObject aosMakeby = (DynamicObject)this.getView().getParentView().getModel().getValue("aos_makeby");
        long makebyId = (Long)aosMakeby.getPkValue();
        // 如果当前用户不为录入人则全锁定
        if (user != makebyId) {
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
        EntryGrid entryGrid = this.getControl("aos_detailentry");
        DynamicObjectCollection dycImg =
            this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_detailentry");
        int rows = dycImg.size();
        int[] selectRow = new int[rows];
        for (int i = 0; i < rows; i++) {
            selectRow[i] = i;
        }
        entryGrid.selectRows(selectRow, 0);
    }
}