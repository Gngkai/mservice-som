package mkt.popular.promot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.util.*;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.db.DB;
import kd.bos.db.DBRoute;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
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
import mkt.common.otel.MmsOtelUtils;
import mkt.popular.budget_p.aos_mkt_popbudgetp_form;
import mkt.popular.budget_p.aos_mkt_popbudgetp_init;

/**
 * @author aosom
 */
public class AosMktPopAdjpForm extends AbstractFormPlugin implements ItemClickListener, RowClickEventListener {
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
    private static final String AOS_ADJPRICE = "aos_adjprice";
    private static final String FALSE = "false";
    private static final String AOS_DETAILENTRY = "aos_detailentry";
    private static final String B = "B";

    private static final Tracer TRACER = MmsOtelUtils.getTracer(aos_mkt_popbudgetp_form.class, RequestContext.get());

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
        Span span = MmsOtelUtils.getCusMainSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            String control = evt.getItemKey();
            if (AOS_SELECT.equals(control)) {
                aosSelect();
            } else if (AOS_MULTIPY.equals(control) || AOS_DIVIDE.equals(control) || AOS_ADD.equals(control)
                || AOS_SUBTRACT.equals(control) || AOS_SET.equals(control)) {
                aosApply(control);
            } else if (control.equals(AOS_SAVE)) {
                aosSave();
            } else if (control.equals(AOS_CONFIRM)) {
                aosConfirm();
            } else if (control.equals(AOS_ADJUST)) {
                aosAdjust();
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    private void aosConfirm() {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            Object aosPpcid = this.getView().getParentView().getModel().getValue("aos_ppcid");
            DynamicObject aosMktPopularPpc = BusinessDataServiceHelper.loadSingle(aosPpcid, "aos_mkt_popular_ppc");
            aosMktPopularPpc.set("aos_adjustsp", true);
            OperationServiceHelper.executeOperate("save", "aos_mkt_popular_ppc", new DynamicObject[] {aosMktPopularPpc},
                OperateOption.create());
            boolean aosAdjusts = aosMktPopularPpc.getBoolean("aos_adjusts");
            if (aosAdjusts) {
                String pOuCode = ((DynamicObject)this.getView().getParentView().getModel().getValue("aos_orgid"))
                    .getString("number");
                // 开始生成 预算调整(推广)
                Map<String, Object> budget = new HashMap<>(16);
                budget.put("p_ou_code", pOuCode);
                aos_mkt_popbudgetp_init.executerun(budget);
            }
            this.getView().getParentView().getModel().setValue("aos_status", "B");
            this.getView().getParentView().invokeOperation("save");
            this.getView().showSuccessNotification("确认成功！");
            this.getView().setEnable(false, "aos_detailentry");
            this.getView().setEnable(false, "aos_save");
            this.getView().setEnable(false, "aos_confirm");
            this.getView().setEnable(false, "aos_apply");
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    private void aosSave() {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            int rowCount = this.getModel().getEntryRowCount("aos_detailentry");
            for (int i = 0; i < rowCount; i++) {
                BigDecimal aosAdjprice = (BigDecimal)this.getModel().getValue("aos_adjprice", i);
                long aosEntryid = (long)this.getModel().getValue("aos_entryid", i);
                long aosPpcentryid = (long)this.getModel().getValue("aos_ppcentryid", i);
                Object aosMark = this.getModel().getValue("aos_mark", i);
                if ("true".equals(aosMark.toString())) {
                    aosMark = 1;
                } else {
                    aosMark = 0;
                }
                update(aosEntryid, aosAdjprice, aosPpcentryid, aosMark);
            }
            this.getView().showSuccessNotification("保存成功!");
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    private void update(long aosEntryid, BigDecimal aosAdjprice, long aosPpcentryid, Object aosMark) {
        String sql = " UPDATE tk_aos_mkt_popadpdata_r r " + " SET fk_aos_adjprice = ? " + ",fk_aos_mark= ?"
            + " WHERE 1=1 " + " and r.FEntryId = ?  ";
        Object[] params = {aosAdjprice, aosMark, aosEntryid};
        DB.execute(DBRoute.of(DB_MKT), sql, params);
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        String today = df.format(new Date());
        sql = " UPDATE tk_aos_mkt_popular_ppc_r r " + " SET fk_aos_bid = ?," + " fk_aos_lastpricedate = '" + today
            + "' " + " WHERE 1=1 " + " and r.FEntryId = ? " + "  and fk_aos_lastbid <> ?   ";
        Object[] params2 = {aosAdjprice, aosPpcentryid, aosAdjprice};
        DB.execute(DBRoute.of(DB_MKT), sql, params2);
        sql = " UPDATE tk_aos_mkt_popular_ppc_r r " + " SET fk_aos_bid = ? " + " WHERE 1=1 " + " and r.FEntryId = ? "
            + "  and fk_aos_lastbid = ?   ";
        Object[] params3 = {aosAdjprice, aosPpcentryid, aosAdjprice};
        DB.execute(DBRoute.of(DB_MKT), sql, params3);
    }

    /**
     * 建议价*调整比例 应用至销售出价 字段
     */
    private void aosAdjust() {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
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
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    private void aosApply(String sign) {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
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
                BigDecimal aosLastprice = (BigDecimal)this.getModel().getValue("aos_lastprice", selectRow);
                BigDecimal aosHighvalue = (BigDecimal)this.getModel().getValue("aos_highvalue", selectRow);
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
                        result = aosAdjustrate;
                        break;
                    default:
                        break;
                }
                this.getModel().setValue("aos_adjprice", min(result, aosHighvalue), selectRow);
            }
            this.getView().showSuccessNotification("应用成功!");
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    private void aosSelect() {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
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
                filterRoifrom = new QFilter("aos_detailentry.aos_grouproi", ">=", aosRoifrom);
            }
            QFilter filterRoito = null;
            if (aosRoito != null && aosRoito.toString().length() > 0) {
                filterRoito = new QFilter("aos_detailentry.aos_grouproi", "<=", aosRoito);
            }
            QFilter filterExpfrom = null;
            if (aosExpfrom != null && aosExpfrom.toString().length() > 0) {
                filterExpfrom = new QFilter("aos_detailentry.aos_avgexp", ">=", aosExpfrom);
            }
            QFilter filterExpto = null;
            if (aosExpto != null && aosExpto.toString().length() > 0) {
                filterExpto = new QFilter("aos_detailentry.aos_avgexp", "<=", aosExpto);
            }
            QFilter filterSpendfrom = null;
            if (aosSpendfrom != null && aosSpendfrom.toString().length() > 0) {
                filterSpendfrom = new QFilter("aos_detailentry.aos_groupcost", ">=", aosSpendfrom);
            }
            QFilter filterSpendto = null;
            if (aosSpendto != null && aosSpendto.toString().length() > 0) {
                filterSpendto = new QFilter("aos_detailentry.aos_groupcost", "<=", aosSpendto);
            }
            ArrayList<QFilter> arrQ = new ArrayList<>();
            arrQ.add(filterRoifrom);
            arrQ.add(filterRoito);
            arrQ.add(filterExpfrom);
            arrQ.add(filterExpto);
            arrQ.add(filterSpendfrom);
            arrQ.add(filterSpendto);
            initData(arrQ);
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    @Override
    public void afterCreateNewData(EventObject e) {
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
            "aos_detailentry.aos_poptype aos_poptype," + "aos_detailentry.aos_productno aos_productno, "
                + "aos_detailentry.aos_type aos_type," + "aos_detailentry.aos_season aos_season,"
                + "aos_detailentry.aos_itemstatus aos_itemstatus," + "aos_detailentry.aos_itemname aos_itemname,"
                + "aos_detailentry.aos_itemnumer aos_itemnumer," + "aos_detailentry.aos_avadays aos_avadays,"
                + "aos_detailentry.aos_grouproi aos_grouproi," + "aos_detailentry.aos_avgexp aos_avgexp,"
                + "aos_detailentry.aos_exprate aos_exprate," + "aos_detailentry.aos_serialrate aos_serialrate,"
                + "aos_detailentry.aos_groupcost aos_groupcost," + "aos_detailentry.aos_lastprice aos_lastprice,"
                + "aos_detailentry.aos_calprice aos_calprice," + "aos_detailentry.aos_adjprice aos_adjprice,"
                + "aos_detailentry.aos_lastdate aos_lastdate," + "aos_detailentry.aos_sal_group aos_sal_group,"
                + "aos_detailentry.aos_buybox aos_buybox," + "aos_detailentry.aos_itemid aos_itemid,"
                + "aos_detailentry.aos_ppcentryid aos_ppcentryid," + "aos_detailentry.id aos_entryid, "
                + "aos_detailentry.aos_highvalue aos_highvalue," + "aos_detailentry.aos_bidsuggest aos_bidsuggest,"
                + "aos_detailentry.aos_mark aos_mark," + "aos_detailentry.aos_activity aos_activity,"
                + "aos_detailentry.aos_age aos_age," + "aos_detailentry.aos_rank aos_rank,"
                + "aos_detailentry.aos_overseaqty aos_overseaqty," + "aos_detailentry.aos_is_saleout aos_is_saleout,"
                + "aos_detailentry.aos_seasonattr aos_seasonattr," + "aos_detailentry.aos_sys aos_sys,"
                + "aos_detailentry.aos_cpc aos_cpc," + "aos_detailentry.aos_ratio aos_ratio,"
                + "aos_detailentry.aos_offline aos_offline," + "aos_detailentry.aos_special aos_special";
        DynamicObjectCollection aosMktPopadjustpDataS =
            QueryServiceHelper.query("aos_mkt_popadjustp_data", selectField, filters, "aos_detailentry.aos_productno");
        int size = aosMktPopadjustpDataS.size();
        this.getModel().setValue("aos_count", size);
        int i = 0;
        for (DynamicObject aosMktPopadjustpData : aosMktPopadjustpDataS) {
            this.getModel().batchCreateNewEntryRow("aos_detailentry", 1);
            this.getModel().setValue("aos_poptype", aosMktPopadjustpData.get("aos_poptype"), i);
            this.getModel().setValue("aos_productno", aosMktPopadjustpData.get("aos_productno"), i);
            this.getModel().setValue("aos_type", aosMktPopadjustpData.get("aos_type"), i);
            this.getModel().setValue("aos_season", aosMktPopadjustpData.get("aos_season"), i);
            this.getModel().setValue("aos_itemstatus", aosMktPopadjustpData.get("aos_itemstatus"), i);
            this.getModel().setValue("aos_itemname", aosMktPopadjustpData.get("aos_itemname"), i);
            this.getModel().setValue("aos_itemnumer", aosMktPopadjustpData.get("aos_itemnumer"), i);
            this.getModel().setValue("aos_avadays", aosMktPopadjustpData.get("aos_avadays"), i);
            this.getModel().setValue("aos_grouproi", aosMktPopadjustpData.get("aos_grouproi"), i);
            this.getModel().setValue("aos_avgexp", aosMktPopadjustpData.get("aos_avgexp"), i);
            this.getModel().setValue("aos_exprate", aosMktPopadjustpData.get("aos_exprate"), i);
            this.getModel().setValue("aos_serialrate", aosMktPopadjustpData.get("aos_serialrate"), i);
            this.getModel().setValue("aos_groupcost", aosMktPopadjustpData.get("aos_groupcost"), i);
            this.getModel().setValue("aos_lastprice", aosMktPopadjustpData.get("aos_lastprice"), i);
            this.getModel().setValue("aos_calprice", aosMktPopadjustpData.get("aos_calprice"), i);
            this.getModel().setValue("aos_adjprice", aosMktPopadjustpData.get("aos_adjprice"), i);
            this.getModel().setValue("aos_lastdate", aosMktPopadjustpData.get("aos_lastdate"), i);
            this.getModel().setValue("aos_sal_group", aosMktPopadjustpData.get("aos_sal_group"), i);
            this.getModel().setValue("aos_buybox", aosMktPopadjustpData.get("aos_buybox"), i);
            this.getModel().setValue("aos_itemid", aosMktPopadjustpData.get("aos_itemid"), i);
            this.getModel().setValue("aos_ppcentryid", aosMktPopadjustpData.get("aos_ppcentryid"), i);
            this.getModel().setValue("aos_entryid", aosMktPopadjustpData.get("aos_entryid"), i);
            this.getModel().setValue("aos_highvalue", aosMktPopadjustpData.get("aos_highvalue"), i);
            this.getModel().setValue("aos_bidsuggest", aosMktPopadjustpData.get("aos_bidsuggest"), i);
            this.getModel().setValue("aos_mark", aosMktPopadjustpData.get("aos_mark"), i);
            this.getModel().setValue("aos_activity", aosMktPopadjustpData.get("aos_activity"), i);
            this.getModel().setValue("aos_age", aosMktPopadjustpData.get("aos_age"), i);
            this.getModel().setValue("aos_rank", aosMktPopadjustpData.get("aos_rank"), i);
            this.getModel().setValue("aos_overseaqty", aosMktPopadjustpData.get("aos_overseaqty"), i);
            this.getModel().setValue("aos_is_saleout", aosMktPopadjustpData.get("aos_is_saleout"), i);
            this.getModel().setValue("aos_seasonattr", aosMktPopadjustpData.get("aos_seasonattr"), i);
            this.getModel().setValue("aos_sys", aosMktPopadjustpData.get("aos_sys"), i);
            this.getModel().setValue("aos_cpc", aosMktPopadjustpData.get("aos_cpc"), i);
            this.getModel().setValue("aos_ratio", aosMktPopadjustpData.get("aos_ratio"), i);
            this.getModel().setValue("aos_offline", aosMktPopadjustpData.get("aos_offline"), i);
            this.getModel().setValue("aos_special", aosMktPopadjustpData.get("aos_special"), i);
            BigDecimal aosLastprice = aosMktPopadjustpData.getBigDecimal("aos_lastprice");
            BigDecimal aosAdjprice = aosMktPopadjustpData.getBigDecimal("aos_adjprice");
            if (aosLastprice.compareTo(BigDecimal.ZERO) != 0) {
                this.getModel().setValue("aos_rate",
                    aosAdjprice.divide(aosLastprice, 2, RoundingMode.HALF_UP).subtract(BigDecimal.valueOf(1)), i);
            }
            i++;
        }
    }

    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();
        // 手动出价调整，修改调整标记
        if (AOS_ADJPRICE.equals(name)) {
            int rowIndex = e.getChangeSet()[0].getRowIndex();
            if (rowIndex >= 0) {
                Optional<String> opMark =
                    Optional.ofNullable(this.getModel().getValue("aos_mark", rowIndex).toString());
                if (!Boolean.parseBoolean(opMark.orElse(FALSE))) {
                    this.getModel().setValue("aos_mark", true, rowIndex);
                }
                BigDecimal aosLastprice = (BigDecimal)this.getModel().getValue("aos_lastprice", rowIndex);
                BigDecimal aosAdjprice = (BigDecimal)this.getModel().getValue("aos_adjprice", rowIndex);
                BigDecimal aosBidsuggest = (BigDecimal)this.getModel().getValue("aos_bidsuggest", rowIndex);
                if (aosLastprice.compareTo(BigDecimal.ZERO) != 0) {
                    this.getModel().setValue("aos_rate",
                        aosAdjprice.divide(aosLastprice, 2, RoundingMode.HALF_UP).subtract(BigDecimal.valueOf(1)),
                        rowIndex);
                }
                if (aosBidsuggest.compareTo(BigDecimal.ZERO) != 0) {
                    this.getModel().setValue("aos_ratio", aosAdjprice.divide(aosBidsuggest, 2, RoundingMode.HALF_UP));
                }
            }
        }
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
        String selectField = "aos_detailentry.aos_subentryentity1.aos_date_d2 aos_date_d2,"
            + "aos_detailentry.aos_subentryentity1.aos_bid_suggest aos_bid_suggest,"
            + "aos_detailentry.aos_subentryentity1.aos_bid_rangestart aos_bid_rangestart,"
            + "aos_detailentry.aos_subentryentity1.aos_bid_rangeend aos_bid_rangeend";
        DynamicObjectCollection aosMktPopadjustsDataS = QueryServiceHelper.query("aos_mkt_popadjustp_data", selectField,
            filters, "aos_detailentry.aos_subentryentity1.aos_date_d2 desc");
        int i = 0;
        for (DynamicObject aosMktPopadjustsData : aosMktPopadjustsDataS) {
            this.getModel().batchCreateNewEntryRow("aos_marketentry", 1);
            this.getModel().setValue("aos_date_d2", aosMktPopadjustsData.get("aos_date_d2"), i);
            this.getModel().setValue("aos_bid_suggest", aosMktPopadjustsData.get("aos_bid_suggest"), i);
            this.getModel().setValue("aos_bid_rangestart", aosMktPopadjustsData.get("aos_bid_rangestart"), i);
            this.getModel().setValue("aos_bid_rangeend", aosMktPopadjustsData.get("aos_bid_rangeend"), i);
            i++;
        }
    }

    private void initGroup(Object aosEntryid) {
        this.getModel().deleteEntryData("aos_groupentry");
        Object fid = this.getView().getParentView().getModel().getDataEntity().getPkValue();
        QFilter qfId = new QFilter("aos_sourceid", "=", fid);
        QFilter qfEntryId = new QFilter("aos_detailentry.id", "=", aosEntryid);
        QFilter[] filters = new QFilter[] {qfId, qfEntryId};
        String selectField = "aos_detailentry.aos_subentryentity.aos_date_d aos_date_d,"
            + "aos_detailentry.aos_subentryentity.aos_roi aos_roi,"
            + "aos_detailentry.aos_subentryentity.aos_expouse aos_expouse,"
            + "aos_detailentry.aos_subentryentity.aos_bid aos_bid,"
            + "aos_detailentry.aos_subentryentity.aos_cost aos_cost,"
            + "aos_detailentry.aos_subentryentity.aos_ppcbid as aos_ppcbid,"
            + "aos_detailentry.aos_subentryentity.aos_topprice as aos_topprice";
        DynamicObjectCollection aosMktPopadjustsDataS = QueryServiceHelper.query("aos_mkt_popadjustp_data", selectField,
            filters, "aos_detailentry.aos_subentryentity.aos_date_d desc");
        int i = 0;
        for (DynamicObject aosMktPopadjustsData : aosMktPopadjustsDataS) {
            this.getModel().batchCreateNewEntryRow("aos_groupentry", 1);
            this.getModel().setValue("aos_date_d", aosMktPopadjustsData.get("aos_date_d"), i);
            this.getModel().setValue("aos_roi", aosMktPopadjustsData.get("aos_roi"), i);
            this.getModel().setValue("aos_expouse", aosMktPopadjustsData.get("aos_expouse"), i);
            this.getModel().setValue("aos_bid", aosMktPopadjustsData.get("aos_bid"), i);
            this.getModel().setValue("aos_cost", aosMktPopadjustsData.get("aos_cost"), i);
            this.getModel().setValue("aos_ppcbid", aosMktPopadjustsData.get("aos_ppcbid"), i);
            this.getModel().setValue("aos_topprice", aosMktPopadjustsData.get("aos_topprice"), i);
            i++;
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
        } else {
            this.getView().setEnable(true, "aos_detailentry");
            this.getView().setEnable(true, "aos_apply");
            this.getView().setEnable(true, "aos_save");
            this.getView().setEnable(true, "aos_confirm");
        }
    }
}