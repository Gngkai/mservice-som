package mkt.popularst.promot;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import mkt.popularst.budget.AosMktPopBudpstTask;

/**
 * @author aosom
 * @version ST出价调整推广-动态表单插件
 */
public class AosMktPopAdjpstForm extends AbstractFormPlugin implements ItemClickListener, RowClickEventListener {
    private static final String DB_MKT = "aos.mkt";
    private static final String AOS_SELECT = "aos_select";
    private static final String AOS_MULTIPY = "aos_multipy";
    private static final String AOS_DIVIDE = "aos_divide";
    private static final String AOS_ADD = "aos_add";
    private static final String AOS_SUBTRACT = "aos_subtract";
    private static final String AOS_SET = "aos_set";
    private static final String AOS_SAVE = "aos_save";
    private static final String AOS_CONFIRM = "aos_confirm";
    private static final String AOS_DETAILENTRY = "aos_detailentry";
    private static final String AOS_MANUALPRZ = "aos_manualprz";
    private static final String Y = "Y";
    private static final String B = "B";

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
        this.addItemClickListeners("aos_set");
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String control = evt.getItemKey();
        if (AOS_SELECT.equals(control)) {
            aosSelect();
        } else if (AOS_MULTIPY.equals(control) || AOS_DIVIDE.equals(control) || AOS_ADD.equals(control)
            || AOS_SUBTRACT.equals(control) || AOS_SET.equals(control)) {
            IPageCache iPageCache = this.getView().getPageCache();
            iPageCache.put("UpdateFlag", "Y");
            aosApply(control);
            iPageCache.put("UpdateFlag", "N");
        } else if (control.equals(AOS_SAVE)) {
            aosSave();
        } else if (control.equals(AOS_CONFIRM)) {
            aosConfirm();
        }
    }

    private void aosConfirm() {
        Object aosPpcid = this.getView().getParentView().getModel().getValue("aos_ppcid");
        DynamicObject aosMktPopPpcst = BusinessDataServiceHelper.loadSingle(aosPpcid, "aos_mkt_pop_ppcst");
        aosMktPopPpcst.set("aos_adjustsp", true);
        OperationServiceHelper.executeOperate("save", "aos_mkt_pop_ppcst", new DynamicObject[] {aosMktPopPpcst},
            OperateOption.create());
        boolean aosAdjusts = aosMktPopPpcst.getBoolean("aos_adjusts");
        if (aosAdjusts) {
            String pOuCode =
                ((DynamicObject)this.getView().getParentView().getModel().getValue("aos_orgid")).getString("number");
            // 开始生成 预算调整(推广)
            Map<String, Object> budget = new HashMap<>(16);
            budget.put("p_ou_code", pOuCode);
            AosMktPopBudpstTask.executerun(budget);
        }
        this.getView().getParentView().getModel().setValue("aos_status", "B");
        this.getView().getParentView().invokeOperation("save");
        this.getView().showSuccessNotification("确认成功！");
        this.getView().setEnable(false, "aos_detailentry");
        this.getView().setEnable(false, "aos_save");
        this.getView().setEnable(false, "aos_confirm");
        this.getView().setEnable(false, "aos_apply");
    }

    private void aosSave() {
        int rowCount = this.getModel().getEntryRowCount("aos_detailentry");
        for (int i = 0; i < rowCount; i++) {
            Object aosAdjprice = this.getModel().getValue("aos_adjprice", i);
            Object aosEntryid = this.getModel().getValue("aos_entryid", i);
            Object aosPpcentryid = this.getModel().getValue("aos_ppcentryid", i);
            Map<String, Object> param = new HashMap<>(16);
            param.put("aos_adjprice", aosAdjprice);
            param.put("aos_entryid", aosEntryid);
            param.put("aos_ppcentryid", aosPpcentryid);
            update(param);
        }
        this.getView().showSuccessNotification("保存成功!");
    }

    private void update(Map<String, Object> param) {
        Object aosAdjprice = param.get("aos_adjprice");
        Object aosEntryid = param.get("aos_entryid");
        Object aosPpcentryid = param.get("aos_ppcentryid");
        String sql = " UPDATE tk_aos_mkt_pop_adjpstdt_r r " + " SET fk_aos_adjprice = ? " + " WHERE 1=1 "
            + " and r.FEntryId = ?  ";
        Object[] params = {aosAdjprice, aosEntryid};
        DB.execute(DBRoute.of(DB_MKT), sql, params);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String today = df.format(new Date());
        sql = " UPDATE tk_aos_mkt_ppcst_data r " + " SET fk_aos_bid = ?," + " fk_aos_lastpricedate = '" + today + "' "
            + " WHERE 1=1 " + " and r.fid = ? " + "  and fk_aos_lastbid <> ?   ";
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
                case "aos_set":
                    result = aosLastprice;
                    break;
                default:
                    break;
            }
            this.getModel().setValue("aos_adjprice", Cux_Common_Utl.Min(result, aosHighvalue), selectRow);
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
        Object aosCtrfrom = this.getModel().getValue("aos_ctrfrom");
        Object aosCtrto = this.getModel().getValue("aos_ctrto");
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
            filterExpfrom = new QFilter("aos_detailentry.aos_exposure", ">=", aosExpfrom);
        }

        QFilter filterExpto = null;
        if (aosExpto != null && aosExpto.toString().length() > 0) {
            filterExpto = new QFilter("aos_detailentry.aos_exposure", "<=", aosExpto);
        }
        QFilter filterSpendfrom = null;
        if (aosSpendfrom != null && aosSpendfrom.toString().length() > 0) {
            filterSpendfrom = new QFilter("aos_detailentry.aos_cost", ">=", aosSpendfrom);
        }
        QFilter filterSpendto = null;
        if (aosSpendto != null && aosSpendto.toString().length() > 0) {
            filterSpendto = new QFilter("aos_detailentry.aos_cost", "<=", aosSpendto);
        }
        QFilter filterCtrfrom = null;
        if (aosCtrfrom != null && aosCtrfrom.toString().length() > 0) {
            filterCtrfrom = new QFilter("aos_detailentry.aos_ctr", ">=", aosCtrfrom);
        }
        QFilter filterCtrto = null;
        if (aosCtrto != null && aosCtrto.toString().length() > 0) {
            filterCtrto = new QFilter("aos_detailentry.aos_ctr", "<=", aosCtrto);
        }
        ArrayList<QFilter> arrQ = new ArrayList<>();
        arrQ.add(filterRoifrom);
        arrQ.add(filterRoito);
        arrQ.add(filterExpfrom);
        arrQ.add(filterExpto);
        arrQ.add(filterSpendfrom);
        arrQ.add(filterSpendto);
        arrQ.add(filterCtrfrom);
        arrQ.add(filterCtrto);
        initData(arrQ);
    }

    @Override
    public void afterCreateNewData(EventObject e) {
        IPageCache iPageCache = this.getView().getPageCache();
        iPageCache.put("UpdateFlag", "N");
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
        String selectField = "aos_detailentry.aos_poptype aos_poptype," + "aos_detailentry.aos_productno aos_productno,"
            + "aos_detailentry.aos_type aos_type," + "aos_detailentry.aos_season aos_season,"
            + "aos_detailentry.aos_category1 aos_category1," + "aos_detailentry.aos_itemname aos_itemname,"
            + "aos_detailentry.aos_itemnumer aos_itemnumer," + "aos_detailentry.aos_keyword aos_keyword,"
            + "aos_detailentry.aos_matchtype aos_matchtype," + "aos_detailentry.aos_roi aos_roi,"
            + "aos_detailentry.aos_exposure aos_exposure," + "aos_detailentry.aos_ctr aos_ctr,"
            + "aos_detailentry.aos_serialrate aos_serialrate," + "aos_detailentry.aos_cost aos_cost,"
            + "aos_detailentry.aos_lastprice aos_lastprice," + "aos_detailentry.aos_calprice aos_calprice,"
            + "aos_detailentry.aos_adjprice aos_adjprice," + "aos_detailentry.aos_lastdate aos_lastdate,"
            + "aos_detailentry.aos_bidsuggest aos_bidsuggest," + "aos_detailentry.aos_highvalue aos_highvalue,"
            + "aos_detailentry.aos_entryid aos_ppcentryid," + "aos_detailentry.id aos_entryid";
        DynamicObjectCollection aosMktPopadjustsDataS =
            QueryServiceHelper.query("aos_mkt_pop_adjpstdt", selectField, filters, "aos_detailentry.aos_productno");
        int i = 0;
        for (DynamicObject aosMktPopadjustsData : aosMktPopadjustsDataS) {
            this.getModel().batchCreateNewEntryRow("aos_detailentry", 1);
            this.getModel().setValue("aos_poptype", aosMktPopadjustsData.get("aos_poptype"), i);
            this.getModel().setValue("aos_productno", aosMktPopadjustsData.get("aos_productno"), i);
            this.getModel().setValue("aos_type", aosMktPopadjustsData.get("aos_type"), i);
            this.getModel().setValue("aos_season", aosMktPopadjustsData.get("aos_season"), i);
            this.getModel().setValue("aos_category1", aosMktPopadjustsData.get("aos_category1"), i);
            this.getModel().setValue("aos_itemname", aosMktPopadjustsData.get("aos_itemname"), i);
            this.getModel().setValue("aos_itemnumer", aosMktPopadjustsData.get("aos_itemnumer"), i);
            this.getModel().setValue("aos_keyword", aosMktPopadjustsData.get("aos_keyword"), i);
            this.getModel().setValue("aos_matchtype", aosMktPopadjustsData.get("aos_matchtype"), i);
            this.getModel().setValue("aos_roi", aosMktPopadjustsData.get("aos_roi"), i);
            this.getModel().setValue("aos_exposure", aosMktPopadjustsData.get("aos_exposure"), i);
            this.getModel().setValue("aos_ctr", aosMktPopadjustsData.get("aos_ctr"), i);
            this.getModel().setValue("aos_serialrate", aosMktPopadjustsData.get("aos_serialrate"), i);
            this.getModel().setValue("aos_cost", aosMktPopadjustsData.get("aos_cost"), i);
            this.getModel().setValue("aos_lastprice", aosMktPopadjustsData.get("aos_lastprice"), i);
            this.getModel().setValue("aos_calprice", aosMktPopadjustsData.get("aos_calprice"), i);
            this.getModel().setValue("aos_adjprice", aosMktPopadjustsData.get("aos_adjprice"), i);
            this.getModel().setValue("aos_lastdate", aosMktPopadjustsData.get("aos_lastdate"), i);
            this.getModel().setValue("aos_bidsuggest", aosMktPopadjustsData.get("aos_bidsuggest"), i);
            this.getModel().setValue("aos_highvalue", aosMktPopadjustsData.get("aos_highvalue"), i);
            this.getModel().setValue("aos_ppcentryid", aosMktPopadjustsData.get("aos_ppcentryid"), i);
            this.getModel().setValue("aos_entryid", aosMktPopadjustsData.get("aos_entryid"), i);
            i++;
        }
    }

    @Override
    public void entryRowClick(RowClickEvent evt) {
        Control source = (Control)evt.getSource();
        if (AOS_DETAILENTRY.equals(source.getKey())) {
            int currentRowIndex = this.getModel().getEntryCurrentRowIndex("aos_detailentry");
            Object aosEntryid = this.getModel().getValue("aos_entryid", currentRowIndex);
            initWord(aosEntryid);
            initGroup(aosEntryid);
        }

    }

    private void initGroup(Object aosEntryid) {
        this.getModel().deleteEntryData("aos_groupskuentry");
        Object fid = this.getView().getParentView().getModel().getDataEntity().getPkValue();
        QFilter qfId = new QFilter("aos_sourceid", "=", fid);
        QFilter qfEntryId = new QFilter("aos_detailentry.id", "=", aosEntryid);
        QFilter[] filters = new QFilter[] {qfId, qfEntryId};
        String selectField = "aos_detailentry.aos_groupskuentry.aos_groupsku aos_groupsku,"
            + "aos_detailentry.aos_groupskuentry.aos_groupitemname aos_groupitemname,"
            + "aos_detailentry.aos_groupskuentry.aos_groupkwnum aos_groupkwnum,"
            + "aos_detailentry.aos_groupskuentry.aos_grouproi aos_grouproi,"
            + "aos_detailentry.aos_groupskuentry.aos_groupex aos_groupex,"
            + "aos_detailentry.aos_groupskuentry.aos_groupcost aos_groupcost,"
            + "aos_detailentry.aos_groupskuentry.aos_buybox aos_buybox";
        DynamicObjectCollection aosMktPopadjustsDataS = QueryServiceHelper.query("aos_mkt_pop_adjpstdt", selectField,
            filters, " aos_detailentry.aos_groupskuentry.aos_groupsku");
        int i = 0;
        for (DynamicObject aosMktPopadjustsData : aosMktPopadjustsDataS) {
            this.getModel().batchCreateNewEntryRow("aos_groupskuentry", 1);
            this.getModel().setValue("aos_groupsku", aosMktPopadjustsData.get("aos_groupsku"), i);
            this.getModel().setValue("aos_groupitemname", aosMktPopadjustsData.get("aos_groupitemname"), i);
            this.getModel().setValue("aos_groupkwnum", aosMktPopadjustsData.get("aos_groupkwnum"), i);
            this.getModel().setValue("aos_grouproi", aosMktPopadjustsData.get("aos_grouproi"), i);
            this.getModel().setValue("aos_groupex", aosMktPopadjustsData.get("aos_groupex"), i);
            this.getModel().setValue("aos_groupcost", aosMktPopadjustsData.get("aos_groupcost"), i);
            this.getModel().setValue("aos_buybox", aosMktPopadjustsData.get("aos_buybox"), i);
            i++;
        }
    }

    private void initWord(Object aosEntryid) {
        this.getModel().deleteEntryData("aos_wordentry");
        Object fid = this.getView().getParentView().getModel().getDataEntity().getPkValue();
        QFilter qfId = new QFilter("aos_sourceid", "=", fid);
        QFilter qfEntryId = new QFilter("aos_detailentry.id", "=", aosEntryid);
        QFilter[] filters = new QFilter[] {qfId, qfEntryId};
        String selectField = "aos_detailentry.aos_wordentry.aos_worddate aos_worddate,"
            + "aos_detailentry.aos_wordentry.aos_wordroi aos_wordroi,"
            + "aos_detailentry.aos_wordentry.aos_wordex aos_wordex,"
            + "aos_detailentry.aos_wordentry.aos_wordctr aos_wordctr,"
            + "aos_detailentry.aos_wordentry.aos_wordbid aos_wordbid,"
            + "aos_detailentry.aos_wordentry.aos_wordcost aos_wordcost,"
            + "aos_detailentry.aos_wordentry.aos_wordsetbid aos_wordsetbid,"
            + "aos_detailentry.aos_wordentry.aos_wordadprice aos_wordadprice";
        DynamicObjectCollection aosMktPopadjustsDataS = QueryServiceHelper.query("aos_mkt_pop_adjpstdt", selectField,
            filters, " aos_detailentry.aos_wordentry.aos_worddate desc");
        int i = 0;
        for (DynamicObject aosMktPopadjustsData : aosMktPopadjustsDataS) {
            this.getModel().batchCreateNewEntryRow("aos_wordentry", 1);
            this.getModel().setValue("aos_worddate", aosMktPopadjustsData.get("aos_worddate"), i);
            this.getModel().setValue("aos_wordroi", aosMktPopadjustsData.get("aos_wordroi"), i);
            this.getModel().setValue("aos_wordex", aosMktPopadjustsData.get("aos_wordex"), i);
            this.getModel().setValue("aos_wordctr", aosMktPopadjustsData.get("aos_wordctr"), i);
            this.getModel().setValue("aos_wordbid", aosMktPopadjustsData.get("aos_wordbid"), i);
            this.getModel().setValue("aos_wordcost", aosMktPopadjustsData.get("aos_wordcost"), i);
            this.getModel().setValue("aos_wordsetbid", aosMktPopadjustsData.get("aos_wordsetbid"), i);
            this.getModel().setValue("aos_wordadprice", aosMktPopadjustsData.get("aos_wordadprice"), i);
            i++;
        }
    }

    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();
        IPageCache iPageCache = this.getView().getPageCache();
        String updateFlag = iPageCache.get("UpdateFlag");
        if (AOS_MANUALPRZ.equals(name) && Y.equals(updateFlag)) {
            int currentRowIndex = this.getModel().getEntryCurrentRowIndex("aos_detailentry");
            int currentRowIndexG = e.getChangeSet()[0].getRowIndex();
            DynamicObject aosItemnumerD = (DynamicObject)this.getModel().getValue("aos_itemid", currentRowIndex);
            String aosItemnumer = aosItemnumerD.getString("number");
            Object aosGroupentryid = this.getModel().getValue("aos_groupentryid", currentRowIndex);
            Object aosKeyword = this.getModel().getValue("aos_keyword", currentRowIndexG);
            Object aosMatchType = this.getModel().getValue("aos_match_type", currentRowIndexG);
            Object aosManualprz = this.getModel().getValue("aos_manualprz", currentRowIndexG);
            Map<String, Object> param = new HashMap<>(16);
            param.put("aos_itemnumer", aosItemnumer);
            param.put("aos_groupentryid", aosGroupentryid);
            param.put("aos_keyword", aosKeyword);
            param.put("aos_match_type", aosMatchType);
            param.put("aos_manualprz", aosManualprz);
            update(param);
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