package mkt.popularst.sale;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;

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
import mkt.common.MktComUtil;
import mkt.popularst.budget.AosMktPopBudpstTask;

/**
 * @author aosom
 * @version ST出价调整销售-动态表单插件
 */
public class AosMktPopAdjstForm extends AbstractFormPlugin implements ItemClickListener, RowClickEventListener {
    private static final String DB_MKT = "aos.mkt";
    private static final String AOS_SELECT = "aos_select";
    private static final String AOS_MULTIPY = "aos_multipy";
    private static final String AOS_DIVIDE = "aos_divide";
    private static final String AOS_ADD = "aos_add";
    private static final String AOS_SUBTRACT = "aos_subtract";
    private static final String AOS_SET = "aos_set";
    private static final String AOS_SAVE = "aos_save";
    private static final String AOS_CONFIRM = "aos_confirm";
    private static final String AOS_ADJUST = "aos_adjust";
    private static final String AOS_VALID_FLAG = "aos_valid_flag";
    private static final String AOS_DETAILENTRY = "aos_detailentry";
    private static final String AOS_GROUPENTRY = "aos_groupentry";
    private static final String Y = "Y";
    private static final String AOS_MANUALPRZ = "aos_manualprz";
    private static final String B = "B";

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        EntryGrid aosDetailentry = this.getControl("aos_detailentry");
        aosDetailentry.addRowClickListener(this);
        EntryGrid aosGroupentry = this.getControl("aos_groupentry");
        aosGroupentry.addRowClickListener(this);
        // 给工具栏加监听事件
        this.addItemClickListeners("aos_toolbarap");
        this.addItemClickListeners("aos_select");
        this.addItemClickListeners("aos_apply");
        this.addItemClickListeners("aos_save");
        this.addItemClickListeners("aos_confirm");
        this.addItemClickListeners("aos_set");
        this.addItemClickListeners("aos_adjust");
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        try {
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
            } else if (control.equals(AOS_ADJUST)) {
                adjustPrice();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void aosConfirm() {
        Object aosPpcid = this.getView().getParentView().getModel().getValue("aos_ppcid");
        QFilter qfId = new QFilter("aos_ppcid", "=", aosPpcid);
        QFilter[] filters = new QFilter[] {qfId};
        String selectField = "id";
        DynamicObjectCollection aosMktPopularAdjsS =
            QueryServiceHelper.query("aos_mkt_pop_adjst", selectField, filters);
        int total = aosMktPopularAdjsS.size();
        QFilter qfStatus = new QFilter("aos_status", "=", "B");
        filters = new QFilter[] {qfId, qfStatus};
        aosMktPopularAdjsS = QueryServiceHelper.query("aos_mkt_pop_adjst", selectField, filters);
        int confirm = aosMktPopularAdjsS.size();
        if (total == confirm + 1) {
            DynamicObject aosMktPopularPpc = BusinessDataServiceHelper.loadSingle(aosPpcid, "aos_mkt_pop_ppcst");
            aosMktPopularPpc.set("aos_adjusts", true);
            OperationServiceHelper.executeOperate("save", "aos_mkt_pop_ppcst", new DynamicObject[] {aosMktPopularPpc},
                OperateOption.create());
            boolean aosAdjustsp = aosMktPopularPpc.getBoolean("aos_adjustsp");
            if (aosAdjustsp) {
                String pOuCode = ((DynamicObject)this.getView().getParentView().getModel().getValue("aos_orgid"))
                    .getString("number");
                // 开始生成 预算调整(推广)
                Map<String, Object> budget = new HashMap<>(16);
                budget.put("p_ou_code", pOuCode);
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

    private void aosSave() {
        int currentRowIndex = this.getModel().getEntryCurrentRowIndex("aos_detailentry");
        DynamicObject aosItemnumerD = (DynamicObject)this.getModel().getValue("aos_itemid", currentRowIndex);
        String aosItemnumer = aosItemnumerD.getString("number");
        DynamicObjectCollection aosGroupentryS = this.getModel().getEntryEntity("aos_groupentry");
        for (DynamicObject aosGroupentry : aosGroupentryS) {
            long aosGroupentryid = aosGroupentry.getLong("aos_groupentryid");
            String aosKeyword = aosGroupentry.getString("aos_keyword");
            String aosMatchType = aosGroupentry.getString("aos_match_type");
            BigDecimal aosManualprz = aosGroupentry.getBigDecimal("aos_manualprz");
            // 剔词
            boolean aosValidFlag = aosGroupentry.getBoolean("aos_valid_flag");
            Map<String, Object> param = new HashMap<>(16);
            param.put("aos_itemnumer", aosItemnumer);
            param.put("aos_groupentryid", aosGroupentryid);
            param.put("aos_keyword", aosKeyword);
            param.put("aos_match_type", aosMatchType);
            param.put("aos_manualprz", aosManualprz);
            if (aosValidFlag) {
                param.put("aos_valid_flag", aosValidFlag);
            }
            update(param);
        }
        this.getView().showSuccessNotification("保存成功!");
    }

    private void update(Map<String, Object> param) {
        Object aosItemnumer = param.get("aos_itemnumer");
        Object aosGroupentryid = param.get("aos_groupentryid");
        Object aosKeyword = param.get("aos_keyword");
        Object aosMatchType = param.get("aos_match_type");
        Object aosManualprz = param.get("aos_manualprz");
        String sql;
        if (param.containsKey(AOS_VALID_FLAG)) {
            sql = " UPDATE tk_aos_mktpop_adjstdt_d d " + " SET fk_aos_manualprz = ?, " + " fk_aos_valid_flag = 1 "
                + " WHERE 1=1 and d.FDetailId = ?  ";
        } else {
            sql = " UPDATE tk_aos_mktpop_adjstdt_d d " + " SET fk_aos_manualprz = ?, " + " fk_aos_valid_flag = 0 "
                + " WHERE 1=1 and d.FDetailId = ?  ";
        }
        Object[] params = {aosManualprz, aosGroupentryid};
        DB.execute(DBRoute.of(DB_MKT), sql, params);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String today = df.format(new Date());
        if (param.containsKey(AOS_VALID_FLAG)) {
            sql = " UPDATE tk_aos_mkt_pop_ppcst_r r " + " SET fk_aos_bid = ? , "
                + "fk_aos_valid_flag = 1,fk_aos_groupstatus = 'UNAVAILABLE', " + " fk_aos_salemanual = 1 , "
                + " fk_aos_lastpricedate =  '" + today + "'  " + " WHERE 1=1 " + " and r.fk_aos_itemnumer = ? "
                + " and r.fk_aos_keyword = ? " + " and r.fk_aos_match_type = ? " + " and r.fk_aos_lastbid <> ?   ";
        } else {
            sql = " UPDATE tk_aos_mkt_pop_ppcst_r r " + " SET fk_aos_bid = ? , " + " fk_aos_salemanual = 1 , "
                + " fk_aos_lastpricedate =  '" + today + "'  " + " WHERE 1=1 " + " and r.fk_aos_itemnumer = ? "
                + " and r.fk_aos_keyword = ? " + " and r.fk_aos_match_type = ? " + " and r.fk_aos_lastbid <> ?   ";
        }
        Object[] params2 = {aosManualprz, aosItemnumer, aosKeyword, aosMatchType, aosManualprz};
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
        EntryGrid aosGroupentry = this.getControl("aos_groupentry");
        int[] selectRows = aosGroupentry.getSelectRows();
        int rowCount = selectRows.length;
        if (rowCount == 0) {
            this.getView().showTipNotification("至少选择一行数据");
            return;
        }
        for (int selectRow : selectRows) {
            BigDecimal aosHighvalue = (BigDecimal)this.getModel().getValue("aos_highprz", selectRow);
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
            this.getModel().setValue("aos_manualprz", Cux_Common_Utl.Min(result, aosHighvalue), selectRow);
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
        IPageCache iPageCache = this.getView().getPageCache();
        iPageCache.put("UpdateFlag", "N");
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
        String selectField = "aos_detailentry.aos_itemid aos_itemid," + "aos_detailentry.aos_cn_name aos_cn_name,"
            + "aos_detailentry.aos_seasonseting aos_seasonseting," + "aos_detailentry.aos_categroy1 aos_categroy1,"
            + "aos_detailentry.aos_categroy2 aos_categroy2," + "aos_detailentry.aos_categroy3 aos_categroy3,"
            + "aos_detailentry.aos_avadays aos_avadays," + "aos_detailentry.aos_count aos_count,"
            + "aos_detailentry.aos_click aos_click," + "aos_detailentry.aos_spend aos_spend,"
            + "aos_detailentry.aos_roi aos_roi," + "aos_detailentry.aos_expouse aos_expouse,"
            + "aos_detailentry.id EntryID";
        DynamicObjectCollection aosMktPopadjustsDataS =
            QueryServiceHelper.query("aos_mkt_pop_adjstdt", selectField, filters, "aos_detailentry.aos_itemid");
        int i = 0;
        for (DynamicObject aosMktPopadjustsData : aosMktPopadjustsDataS) {
            this.getModel().batchCreateNewEntryRow("aos_detailentry", 1);
            this.getModel().setValue("aos_itemid", aosMktPopadjustsData.get("aos_itemid"), i);
            this.getModel().setValue("aos_cn_name", aosMktPopadjustsData.get("aos_cn_name"), i);
            this.getModel().setValue("aos_seasonseting", aosMktPopadjustsData.get("aos_seasonseting"), i);
            this.getModel().setValue("aos_categroy1", aosMktPopadjustsData.get("aos_categroy1"), i);
            this.getModel().setValue("aos_categroy2", aosMktPopadjustsData.get("aos_categroy2"), i);
            this.getModel().setValue("aos_categroy3", aosMktPopadjustsData.get("aos_categroy3"), i);
            this.getModel().setValue("aos_avadays", aosMktPopadjustsData.get("aos_avadays"), i);
            this.getModel().setValue("aos_count", aosMktPopadjustsData.get("aos_count"), i);
            this.getModel().setValue("aos_click", aosMktPopadjustsData.get("aos_click"), i);
            this.getModel().setValue("aos_spend", aosMktPopadjustsData.get("aos_spend"), i);
            this.getModel().setValue("aos_roi", aosMktPopadjustsData.get("aos_roi"), i);
            this.getModel().setValue("aos_expouse", aosMktPopadjustsData.get("aos_expouse"), i);
            this.getModel().setValue("aos_entryid", aosMktPopadjustsData.get("EntryID"), i);
            i++;
        }
    }

    @Override
    public void entryRowClick(RowClickEvent evt) {
        Control source = (Control)evt.getSource();
        if (AOS_DETAILENTRY.equals(source.getKey())) {
            IPageCache iPageCache = this.getView().getPageCache();
            iPageCache.put("UpdateFlag", "N");
            int currentRowIndex = this.getModel().getEntryCurrentRowIndex("aos_detailentry");
            Object aosEntryid = this.getModel().getValue("aos_entryid", currentRowIndex);
            initGroup(aosEntryid);
        } else if (AOS_GROUPENTRY.equals(source.getKey())) {
            IPageCache iPageCache = this.getView().getPageCache();
            iPageCache.put("UpdateFlag", "Y");
        }
    }

    private void initGroup(Object aosEntryid) {
        this.getModel().deleteEntryData("aos_groupentry");
        Object fid = this.getView().getParentView().getModel().getDataEntity().getPkValue();
        QFilter qfId = new QFilter("aos_sourceid", "=", fid);
        QFilter qfEntryId = new QFilter("aos_detailentry.id", "=", aosEntryid);
        QFilter[] filters = new QFilter[] {qfId, qfEntryId};
        String selectField = "aos_detailentry.aos_groupentry.aos_keyword aos_keyword,"
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
        DynamicObjectCollection aosMktPopadjustsDataS = QueryServiceHelper.query("aos_mkt_pop_adjstdt", selectField,
            filters, " aos_detailentry.aos_groupentry.aos_keyword");
        int i = 0;
        for (DynamicObject aosMktPopadjustsData : aosMktPopadjustsDataS) {
            this.getModel().batchCreateNewEntryRow("aos_groupentry", 1);
            this.getModel().setValue("aos_keyword", aosMktPopadjustsData.get("aos_keyword"), i);
            this.getModel().setValue("aos_click_r", aosMktPopadjustsData.get("aos_click_r"), i);
            this.getModel().setValue("aos_spend_r", aosMktPopadjustsData.get("aos_spend_r"), i);
            this.getModel().setValue("aos_roi_r", aosMktPopadjustsData.get("aos_roi_r"), i);
            this.getModel().setValue("aos_expouse_r", aosMktPopadjustsData.get("aos_expouse_r"), i);
            this.getModel().setValue("aos_ctr", aosMktPopadjustsData.get("aos_ctr"), i);
            this.getModel().setValue("aos_match_type", aosMktPopadjustsData.get("aos_match_type"), i);
            this.getModel().setValue("aos_lastprice", aosMktPopadjustsData.get("aos_lastprice"), i);
            this.getModel().setValue("aos_calprice", aosMktPopadjustsData.get("aos_calprice"), i);
            this.getModel().setValue("aos_manualprz", aosMktPopadjustsData.get("aos_manualprz"), i);
            this.getModel().setValue("aos_valid_flag", aosMktPopadjustsData.get("aos_valid_flag"), i);
            this.getModel().setValue("aos_adviceprz", aosMktPopadjustsData.get("aos_adviceprz"), i);
            this.getModel().setValue("aos_highprz", aosMktPopadjustsData.get("aos_highprz"), i);
            this.getModel().setValue("aos_lowprz", aosMktPopadjustsData.get("aos_lowprz"), i);
            this.getModel().setValue("aos_groupentryid", aosMktPopadjustsData.get("aos_groupentryid"), i);
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
        e.setCheckDataChange(false);
    }

    @Override
    public void afterBindData(EventObject e) {
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

    public void adjustPrice() {
        Object aosAdjustrate = this.getModel().getValue("aos_adjustrate");
        if (aosAdjustrate != null) {
            EntryGrid aosDetailentry = this.getControl("aos_groupentry");
            int[] selectRows = aosDetailentry.getSelectRows();
            for (int selectRow : selectRows) {
                BigDecimal aosAdjprice = (BigDecimal)Optional
                    .ofNullable(this.getModel().getValue("aos_adviceprz", selectRow)).orElse(BigDecimal.ZERO);
                BigDecimal aosHighvalue = (BigDecimal)Optional
                    .ofNullable(this.getModel().getValue("aos_highprz", selectRow)).orElse(BigDecimal.ZERO);
                this.getModel().setValue("aos_manualprz",
                    MktComUtil.min(aosAdjprice.multiply((BigDecimal)aosAdjustrate).setScale(2, RoundingMode.HALF_UP),
                        aosHighvalue),
                    selectRow);
            }
        }
        aosSave();
        this.getView().showSuccessNotification("建议价应用成功!");
    }
}