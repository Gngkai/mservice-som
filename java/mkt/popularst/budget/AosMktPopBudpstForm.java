package mkt.popularst.budget;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EventObject;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.db.DB;
import kd.bos.db.DBRoute;
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

/**
 * @author aosom
 * @version ST预算调整-动态表单插件
 */
public class AosMktPopBudpstForm extends AbstractFormPlugin implements ItemClickListener, RowClickEventListener {

    private static final String DB_MKT = "aos.mkt";
    private static final String AOS_SELECT = "aos_select";
    private static final String AOS_APPLY = "aos_apply";
    private static final String AOS_SAVE = "aos_save";
    private static final String AOS_CONFIRM = "aos_confirm";
    private static final String AOS_BUDGETENTRY = "aos_budgetentry";
    private static final String B = "B";

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        EntryGrid aosBudgetentry = this.getControl("aos_budgetentry");
        aosBudgetentry.addRowClickListener(this);
        // 给工具栏加监听事件
        this.addItemClickListeners("aos_toolbarap");
        this.addItemClickListeners("aos_select");
        this.addItemClickListeners("aos_apply");
        this.addItemClickListeners("aos_save");
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String control = evt.getItemKey();
        if (AOS_SELECT.equals(control)) {
            aosSelect();
        } else if (AOS_APPLY.equals(control)) {
            aosApply();
        } else if (AOS_SAVE.equals(control)) {
            aosSave();
        } else if (AOS_CONFIRM.equals(control)) {
            aosConfirm();
        }
    }

    private void aosConfirm() {
        Object aosPpcid = this.getView().getParentView().getModel().getValue("aos_ppcid");
        DynamicObject aosMktPopularPpc = BusinessDataServiceHelper.loadSingle(aosPpcid, "aos_mkt_pop_ppcst");
        aosMktPopularPpc.set("aos_budgetp", true);
        OperationServiceHelper.executeOperate("save", "aos_mkt_pop_ppcst", new DynamicObject[] {aosMktPopularPpc},
            OperateOption.create());
        this.getView().getParentView().getModel().setValue("aos_status", "B");
        this.getView().getParentView().invokeOperation("save");
        this.getView().showSuccessNotification("确认成功！");
        this.getView().setEnable(false, "aos_budgetentry");
        this.getView().setEnable(false, "aos_apply");
        this.getView().setEnable(false, "aos_save");
        this.getView().setEnable(false, "aos_confirm");
    }

    private void aosSave() {
        int rowCount = this.getModel().getEntryRowCount("aos_budgetentry");
        Object aosPpcid = this.getView().getParentView().getModel().getValue("aos_ppcid");
        for (int i = 0; i < rowCount; i++) {
            BigDecimal aosPopbudget = (BigDecimal)this.getModel().getValue("aos_popbudget", i);
            String aosSaldescription = (String)this.getModel().getValue("aos_saldescription", i);
            long aosEntryid = (long)this.getModel().getValue("aos_entryid", i);
            String aosSerialname = (String)this.getModel().getValue("aos_serialname", i);
            update(aosEntryid, aosPopbudget, aosSaldescription, aosSerialname, aosPpcid);
        }
        this.getView().showSuccessNotification("保存成功!");
    }

    private void update(long aosEntryid, BigDecimal aosAdjprice, String aosDescription, String aosSerialname,
        Object aosPpcid) {
        String sql = " UPDATE tk_aos_mkt_popbud_stdt_r r " + " SET fk_aos_popbudget = ?,"
            + " fk_aos_saldescription = ? " + " WHERE 1=1 " + " and r.FEntryId = ? ";
        Object[] params = {aosAdjprice, aosDescription, aosEntryid};
        DB.execute(DBRoute.of(DB_MKT), sql, params);

        sql = " UPDATE tk_aos_mkt_ppcst_data r " + " SET fk_aos_budget = ?" + " WHERE 1=1 "
            + " and r.fk_aos_productno = ? and r.aos_sourceid = ?  ";
        Object[] params2 = {aosAdjprice, aosSerialname, aosPpcid};
        DB.execute(DBRoute.of(DB_MKT), sql, params2);
    }

    private void aosApply() {
        Object aosAdjrate = this.getModel().getValue("aos_adjrate");
        if (aosAdjrate != null) {
            EntryGrid aosDetailentry = this.getControl("aos_budgetentry");
            int[] selectRows = aosDetailentry.getSelectRows();
            for (int selectRow : selectRows) {
                BigDecimal aosLastbudget = (BigDecimal)this.getModel().getValue("aos_lastbudget", selectRow);
                this.getModel().setValue("aos_popbudget", aosLastbudget.multiply((BigDecimal)aosAdjrate), selectRow);
            }
        }
        this.getView().showSuccessNotification("应用成功!");
    }

    private void aosSelect() {
        // 筛选前自动保存
        aosSave();
        Object aosChannelQ = this.getModel().getValue("aos_channel_q");
        Object aosPoptypeQ = this.getModel().getValue("aos_poptype_q");
        Object aosSeasonid = this.getModel().getValue("aos_seasonid");
        Object aosRoifrom = this.getModel().getValue("aos_roifrom");
        Object aosRoito = this.getModel().getValue("aos_roito");
        Object aosRatefrom = this.getModel().getValue("aos_ratefrom");
        Object aosRateto = this.getModel().getValue("aos_rateto");
        Object aosLastfrom = this.getModel().getValue("aos_lastfrom");
        Object aosLastto = this.getModel().getValue("aos_lastto");
        Object aosBudgetfrom = this.getModel().getValue("aos_budgetfrom");
        Object aosBudgetto = this.getModel().getValue("aos_budgetto");
        Object aosDescriptionQ = this.getModel().getValue("aos_description_q");
        QFilter filterChannel = null;
        QFilter filterPoptype = null;
        QFilter filterSeasonid = null;
        QFilter filterRoifrom = null;
        QFilter filterRoito = null;
        QFilter filterRatefrom = null;
        QFilter filterRateto = null;
        QFilter filterSpendfrom = null;
        QFilter filterSpendto = null;
        QFilter filterBudgetfrom = null;
        QFilter filterBudgetto = null;
        QFilter filterDesc = null;

        if (aosChannelQ != null && aosChannelQ.toString().length() > 0) {
            filterChannel = new QFilter("aos_budgetentry.aos_channel_r", "=", aosChannelQ);
        }
        if (aosPoptypeQ != null && aosPoptypeQ.toString().length() > 0) {
            filterPoptype =
                new QFilter("aos_budgetentry.aos_poptype_r", "=", ((DynamicObject)aosPoptypeQ).getPkValue());
        }
        if (aosSeasonid != null && aosSeasonid.toString().length() > 0) {
            filterSeasonid =
                new QFilter("aos_budgetentry.aos_season_r", "=", ((DynamicObject)aosSeasonid).getPkValue());
        }
        if (aosRoifrom != null && aosRoifrom.toString().length() > 0) {
            filterRoifrom = new QFilter("aos_budgetentry.aos_roi7days", ">=", aosRoifrom);
        }
        if (aosRoito != null && aosRoito.toString().length() > 0) {
            filterRoito = new QFilter("aos_budgetentry.aos_roi7days", "<=", aosRoito);
        }
        if (aosRatefrom != null && aosRatefrom.toString().length() > 0) {
            filterRatefrom = new QFilter("aos_budgetentry.aos_lastrate", ">=", aosRatefrom);
        }
        if (aosRateto != null && aosRateto.toString().length() > 0) {
            filterRateto = new QFilter("aos_budgetentry.aos_lastrate", "<=", aosRateto);
        }
        if (aosLastfrom != null && aosLastfrom.toString().length() > 0) {
            filterSpendfrom = new QFilter("aos_budgetentry.aos_lastspend_r", ">=", aosLastfrom);
        }
        if (aosLastto != null && aosLastto.toString().length() > 0) {
            filterSpendto = new QFilter("aos_budgetentry.aos_lastspend_r", "<=", aosLastto);
        }
        if (aosBudgetfrom != null && aosBudgetfrom.toString().length() > 0) {
            filterBudgetfrom = new QFilter("aos_budgetentry.aos_lastbudget", ">=", aosBudgetfrom);
        }
        if (aosBudgetto != null && aosBudgetto.toString().length() > 0) {
            filterBudgetto = new QFilter("aos_budgetentry.aos_lastbudget", "<=", aosBudgetto);
        }
        if (aosDescriptionQ != null && aosDescriptionQ.toString().length() > 0) {
            filterDesc = new QFilter("aos_budgetentry.aos_saldescription", "not like ", "%" + aosDescriptionQ + "%");
        }
        ArrayList<QFilter> arrQ = new ArrayList<>();
        arrQ.add(filterChannel);
        arrQ.add(filterPoptype);
        arrQ.add(filterSeasonid);
        arrQ.add(filterRoifrom);
        arrQ.add(filterRoito);
        arrQ.add(filterRatefrom);
        arrQ.add(filterRateto);
        arrQ.add(filterSpendfrom);
        arrQ.add(filterSpendto);
        arrQ.add(filterBudgetfrom);
        arrQ.add(filterBudgetto);
        arrQ.add(filterDesc);
        initData(arrQ);
    }

    @Override
    public void afterCreateNewData(EventObject e) {
        initData(null);
    }

    private void initData(ArrayList<QFilter> arrQ) {
        this.getModel().deleteEntryData("aos_budgetentry");
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
        String selectField = "aos_budgetentry.id aos_entryid," + "aos_budgetentry.aos_channel_r aos_channel_r,"
            + "aos_budgetentry.aos_poptype_r aos_poptype_r," + "aos_budgetentry.aos_serialname aos_serialname,"
            + "aos_budgetentry.aos_season_r aos_season_r," + "aos_budgetentry.aos_roi7days aos_roi7days,"
            + "aos_budgetentry.aos_roitype aos_roitype," + "aos_budgetentry.aos_skucount aos_skucount,"
            + "aos_budgetentry.aos_kwcount aos_kwcount," + "aos_budgetentry.aos_lastrate aos_lastrate,"
            + "aos_budgetentry.aos_lastspend_r aos_lastspend_r," + "aos_budgetentry.aos_lastbudget aos_lastbudget,"
            + "aos_budgetentry.aos_calbudget aos_calbudget," + "aos_budgetentry.aos_adjbudget aos_adjbudget,"
            + "aos_budgetentry.aos_saldescription aos_saldescription," + "aos_budgetentry.aos_popbudget aos_popbudget,"
            + "aos_budgetentry.aos_adjustrate aos_adjustrate";
        DynamicObjectCollection aosMktPopbudgetDataS =
            QueryServiceHelper.query("aos_mkt_popbudget_stdt", selectField, filters, "aos_budgetentry.aos_serialname");
        int i = 0;
        for (DynamicObject aosMktPopbudgetData : aosMktPopbudgetDataS) {
            this.getModel().batchCreateNewEntryRow("aos_budgetentry", 1);
            this.getModel().setValue("aos_entryid", aosMktPopbudgetData.get("aos_entryid"), i);
            this.getModel().setValue("aos_channel_r", aosMktPopbudgetData.get("aos_channel_r"), i);
            this.getModel().setValue("aos_poptype_r", aosMktPopbudgetData.get("aos_poptype_r"), i);
            this.getModel().setValue("aos_serialname", aosMktPopbudgetData.get("aos_serialname"), i);
            this.getModel().setValue("aos_season_r", aosMktPopbudgetData.get("aos_season_r"), i);
            this.getModel().setValue("aos_roi7days", aosMktPopbudgetData.get("aos_roi7days"), i);
            this.getModel().setValue("aos_roitype", aosMktPopbudgetData.get("aos_roitype"), i);
            this.getModel().setValue("aos_skucount", aosMktPopbudgetData.get("aos_skucount"), i);
            this.getModel().setValue("aos_kwcount", aosMktPopbudgetData.get("aos_kwcount"), i);
            this.getModel().setValue("aos_lastrate", aosMktPopbudgetData.get("aos_lastrate"), i);
            this.getModel().setValue("aos_lastspend_r", aosMktPopbudgetData.get("aos_lastspend_r"), i);
            this.getModel().setValue("aos_lastbudget", aosMktPopbudgetData.get("aos_lastbudget"), i);
            this.getModel().setValue("aos_calbudget", aosMktPopbudgetData.get("aos_calbudget"), i);
            this.getModel().setValue("aos_adjbudget", aosMktPopbudgetData.get("aos_adjbudget"), i);
            this.getModel().setValue("aos_saldescription", aosMktPopbudgetData.get("aos_saldescription"), i);
            this.getModel().setValue("aos_popbudget", aosMktPopbudgetData.get("aos_popbudget"), i);
            this.getModel().setValue("aos_adjustrate", aosMktPopbudgetData.get("aos_adjustrate"), i);
            i++;
        }
    }

    @Override
    public void entryRowClick(RowClickEvent evt) {
        Control source = (Control)evt.getSource();
        if (AOS_BUDGETENTRY.equals(source.getKey())) {
            int currentRowIndex = this.getModel().getEntryCurrentRowIndex("aos_budgetentry");
            Object aosEntryid = this.getModel().getValue("aos_entryid", currentRowIndex);
            initGroup(aosEntryid);
        }
    }

    private void initGroup(Object aosEntryid) {
        this.getModel().deleteEntryData("aos_detailentry");
        Object fid = this.getView().getParentView().getModel().getDataEntity().getPkValue();
        QFilter qfId = new QFilter("aos_sourceid", "=", fid);
        QFilter qfEntryId = new QFilter("aos_budgetentry.id", "=", aosEntryid);
        QFilter[] filters = new QFilter[] {qfId, qfEntryId};
        String selectField = "aos_budgetentry.aos_detailentry.aos_date_d aos_date_d,"
            + "aos_budgetentry.aos_detailentry.aos_roi_d aos_roi_d,"
            + "aos_budgetentry.aos_detailentry.aos_spend_d aos_spend_d,"
            + "aos_budgetentry.aos_detailentry.aos_spendrate_d aos_spendrate_d,"
            + "aos_budgetentry.aos_detailentry.aos_budget_d aos_budget_d,"
            + "aos_budgetentry.aos_detailentry.aos_skucount_d aos_skucount_d,"
            + "aos_budgetentry.aos_detailentry.aos_kwcount_d aos_kwcount_d";
        DynamicObjectCollection aosMktPopbudgetDataS = QueryServiceHelper.query("aos_mkt_popbudget_stdt", selectField,
            filters, " aos_budgetentry.aos_detailentry.aos_date_d desc");
        int i = 0;
        for (DynamicObject aosMktPopbudgetData : aosMktPopbudgetDataS) {
            this.getModel().batchCreateNewEntryRow("aos_detailentry", 1);
            this.getModel().setValue("aos_date_d", aosMktPopbudgetData.get("aos_date_d"), i);
            this.getModel().setValue("aos_roi_d", aosMktPopbudgetData.get("aos_roi_d"), i);
            this.getModel().setValue("aos_spend_d", aosMktPopbudgetData.get("aos_spend_d"), i);
            this.getModel().setValue("aos_spendrate_d", aosMktPopbudgetData.get("aos_spendrate_d"), i);
            this.getModel().setValue("aos_budget_d", aosMktPopbudgetData.get("aos_budget_d"), i);
            this.getModel().setValue("aos_skucount_d", aosMktPopbudgetData.get("aos_skucount_d"), i);
            this.getModel().setValue("aos_kwcount_d", aosMktPopbudgetData.get("aos_kwcount_d"), i);
            i++;
        }
    }

    @Override
    public void afterBindData(EventObject e) {
        String aosStatus = String.valueOf(this.getView().getParentView().getModel().getValue("aos_status"));
        if (B.equals(aosStatus)) {
            this.getView().setEnable(false, "aos_entryentity2");
            this.getView().setEnable(false, "aos_apply");
            this.getView().setEnable(false, "aos_save");
            this.getView().setEnable(false, "aos_confirm");
        }
        long user = UserServiceHelper.getCurrentUserId();
        DynamicObject aosMakeby = (DynamicObject)this.getView().getParentView().getModel().getValue("aos_makeby");
        long makebyId = (Long)aosMakeby.getPkValue();
        // 如果当前用户不为录入人则全锁定
        if (user != makebyId) {
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