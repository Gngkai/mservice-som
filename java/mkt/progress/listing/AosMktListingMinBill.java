package mkt.progress.listing;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

import common.CommonDataSomQuo;
import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndHistory;
import common.fnd.FndMsg;
import common.sal.util.SalUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.dataentity.metadata.dynamicobject.DynamicObjectType;
import kd.bos.entity.EntityMetadataCache;
import kd.bos.entity.datamodel.events.ImportDataEventArgs;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.ClientProperties;
import kd.bos.form.ConfirmCallBackListener;
import kd.bos.form.MessageBoxOptions;
import kd.bos.form.MessageBoxResult;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.events.*;
import kd.bos.form.operate.FormOperate;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.MktComUtil;
import mkt.common.MktS3Pic;
import mkt.common.otel.MmsOtelUtils;
import mkt.progress.ProgressUtil;
import mkt.progress.design.AosMktDesignReqBill;
import mkt.progress.iface.ItemInfoUtil;
import mkt.progress.parameter.errorlisting.ErrorListEntity;

/**
 * @author aosom
 * @version Listing优化需求表小语种-表单插件
 */
public class AosMktListingMinBill extends AbstractBillPlugIn implements ItemClickListener, HyperLinkClickListener {
    /**
     * 系统管理员
     **/
    public final static String SYSTEM = Cux_Common_Utl.SYSTEM;
    public final static int TWO = 2;
    public final static int THREE = 3;
    public final static int FOUR = 4;
    public final static String HOT = "HOT";
    private static final Tracer TRACER = MmsOtelUtils.getTracer(AosMktListingMinBill.class, RequestContext.get());

    /**
     * 设置改错任务清单
     **/
    public static void setErrorList(DynamicObject dyMain) {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            String status = dyMain.getString("aos_status");
            if (!sign.end.name.equals(status)) {
                return;
            }
            DynamicObject dyOrg = dyMain.getDynamicObject("aos_orgid");
            if (dyOrg == null) {
                return;
            }
            String aosType = dyMain.getString("aos_type");
            if (!ErrorListEntity.errorListType.contains(aosType)) {
                return;
            }
            String orgid = dyOrg.getString("id");
            String billno = dyMain.getString("billno");
            DynamicObjectCollection dycEnt = dyMain.getDynamicObjectCollection("aos_entryentity");
            for (DynamicObject dy : dycEnt) {
                DynamicObject dyItem = dy.getDynamicObject("aos_itemid");
                if (dyItem == null) {
                    continue;
                }
                ErrorListEntity errorListEntity = new ErrorListEntity(billno, aosType, orgid, dyItem.getString("id"));
                errorListEntity.save();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 小站海外编辑确认:功能图
     * 
     * @param dyMain 单据对象
     */
    private static void submitForOsSmall(DynamicObject dyMain) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            Object aosType = dyMain.get("aos_type");
            // 只有子表会进入 必为RO和PT
            Object aosOrgsmall = dyMain.get("aos_orgsmall");
            String aosOrgnumber = ((DynamicObject)aosOrgsmall).getString("number");
            if (sign.translate.name.equals(aosType)) {
                // 插入功能图翻译台账
                generateFuncSummary(aosOrgnumber, dyMain);
            }
            dyMain.set("aos_submitter", "B");
            dyMain.set("aos_status", "结束");
            dyMain.set("aos_user", SYSTEM);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 海外编辑确认:功能图
     **/
    private static void submitForOsFunc(DynamicObject dyMain) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            // 数据层
            Object aosOrgid = dyMain.get("aos_orgid");
            String aosOrgnumber = ((DynamicObject)aosOrgid).getString("number");
            Object aosType = dyMain.get("aos_type");
            DynamicObject aosOrgsmall = dyMain.getDynamicObject("aos_orgsmall");
            if (sign.it.name.equals(aosOrgnumber)) {
                // 同时生成小站海外编辑确认:功能图 的listing优化需求小语种
                generateOsSmall("RO", dyMain);
            }
            if (sign.translate.name.equals(aosType)) {
                // 插入功能图翻译台账
                generateFuncSummary(aosOrgnumber, dyMain);
            }

            boolean cond = (("IT".equals(aosOrgnumber) || "ES".equals(aosOrgnumber))
                && ("老品优化".equals(aosType) || "四者一致".equals(aosType)) && FndGlobal.IsNull(aosOrgsmall));
            if (cond) {
                // 生成Listing优化销售确认单
                if (sign.it.name.equals(aosOrgnumber)) {
                    dyMain.set("aos_orgsmall", FndGlobal.getBaseId("RO", "bd_country"));
                }
                if (sign.es.name.equals(aosOrgnumber)) {
                    dyMain.set("aos_orgsmall", FndGlobal.getBaseId("PT", "bd_country"));
                }
                generateListingSalSmall(dyMain);
            }
            // 设置功能图节点操作人为 人提交
            dyMain.set("aos_submitter", "B");
            dyMain.set("aos_status", "结束");
            dyMain.set("aos_user", SYSTEM);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 海外编辑确认
     **/
    private static void submitForOsEditor(DynamicObject dyMain) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            // 生成销售信息确认单
            generateListingSal(dyMain);
            dyMain.set("aos_status", "结束");
            dyMain.set("aos_user", SYSTEM);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 编辑确认状态下提交
     **/
    private static void submitForEditor(DynamicObject dyMain) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            FndError fndError = new FndError();
            // 数据层
            Object aosOrgid = dyMain.get("aos_orgid");
            String aosOrgnumber = ((DynamicObject)aosOrgid).getString("number");
            Object aosSourcetype = dyMain.get("aos_sourcetype");
            Object aosSourceid = dyMain.get("aos_sourceid");
            // 海外文字确认
            Object aosOsconfirmlov = dyMain.get("aos_osconfirmlov");
            // 海外功能图确认
            Object aosFunconfirm = dyMain.get("aos_funconfirm");
            Object listingStatus = null;
            Object listingUser = null;
            Object aosType = dyMain.get("aos_type");
            DynamicObject aosOseditorview = dyMain.getDynamicObject("aos_oseditor");
            // 获取海外编辑
            DynamicObject dyEntFirstRow = dyMain.getDynamicObjectCollection("aos_entryentity").get(0);
            Object itemId = dyEntFirstRow.get("aos_itemid");
            Object id = ((DynamicObject)itemId).getString("id");
            String category = MktComUtil.getItemCateNameZh(id);
            String[] categoryGroup = category.split(",");
            String aosCategory1 = null;
            String aosCategory2 = null;
            int categoryLength = categoryGroup.length;
            if (categoryLength > 0) {
                aosCategory1 = categoryGroup[0];
            }
            if (categoryLength > 1) {
                aosCategory2 = categoryGroup[1];
            }
            long aosOseditor = 0;
            if (Cux_Common_Utl.IsNull(aosOseditorview)) {
                if (FndGlobal.IsNotNull(aosCategory1) && FndGlobal.IsNotNull(aosCategory2)) {
                    QFilter filterCategory1 = new QFilter("aos_category1", "=", aosCategory1);
                    QFilter filterCategory2 = new QFilter("aos_category2", "=", aosCategory2);
                    QFilter filterOu = new QFilter("aos_orgid.number", "=", aosOrgnumber);
                    QFilter[] filtersCategory = new QFilter[] {filterCategory1, filterCategory2, filterOu};
                    String selectStr = "aos_oseditor";
                    DynamicObject aosMktProgorguser =
                        QueryServiceHelper.queryOne("aos_mkt_progorguser", selectStr, filtersCategory);
                    if (aosMktProgorguser != null) {
                        aosOseditor = aosMktProgorguser.getLong("aos_oseditor");
                    }
                }
                if (aosOseditor == 0) {
                    fndError.add(aosCategory1 + "," + aosCategory2 + "海外编辑师不存在!");
                }
                dyMain.set("aos_oseditor", aosOseditor);
            } else {
                aosOseditor = aosOseditorview.getLong("id");
            }
            // 校验
            if (fndError.getCount() > 0) {
                throw fndError;
            }
            if (sign.ved.name.equals(aosSourcetype)) {
                // 如果是视频类型 判断是否为最后一个完成
                dyMain.set("aos_status", "结束");
                dyMain.set("aos_user", SYSTEM);
                // 先执行保存操作
                SaveServiceHelper.save(new DynamicObject[] {dyMain});
                QFilter filterId = new QFilter("aos_sourceid", "=", aosSourceid);
                QFilter filterStatus = new QFilter("aos_status", "=", "编辑确认").or("aos_status", "=", "申请人");
                QFilter[] filters = new QFilter[] {filterId, filterStatus};
                DynamicObject aosMktListingMin = QueryServiceHelper.queryOne("aos_mkt_listing_min", "id", filters);
                // 全部已完成 修改主流程状态
                if (aosMktListingMin == null) {
                    filterId = new QFilter("id", "=", aosSourceid);
                    filters = new QFilter[] {filterId};
                    DynamicObject aosMktListingSon =
                        QueryServiceHelper.queryOne("aos_mkt_listing_son", "aos_sourceid", filters);
                    Object photoId = aosMktListingSon.get("aos_sourceid");
                    // 小语种来源子表的来源拍照需求表ID
                    AosMktListingSonBill.updatePhotoToCut(photoId);
                }
            } else if (sign.listing.name.equals(aosSourcetype) || sign.cmp.name.equals(aosSourcetype)
                || "DESIGN".equals(aosSourcetype)) {
                if (!sign.yes.name.equals(aosOsconfirmlov) && !sign.yes.name.equals(aosFunconfirm)) {
                    // 1.海外文字确认不为是 海外功能图确认也不为是
                    if (sign.translate.name.equals(aosType)) {
                        // 插入功能图翻译台账
                        generateFuncSummary(aosOrgnumber, dyMain);
                    }
                    listingStatus = "结束";
                    listingUser = SYSTEM;
                    // 功能图翻译类型 不需要生成
                    if (!sign.translate.name.equals(aosType)) {
                        // 同时生成销售信息确认单
                        generateListingSal(dyMain);
                    }
                    Object aosOrgsmall = dyMain.get("aos_orgsmall");
                    boolean cond = (("IT".equals(aosOrgnumber) || "ES".equals(aosOrgnumber))
                        && ("老品优化".equals(aosType) || "四者一致".equals(aosType)) && FndGlobal.IsNull(aosOrgsmall));
                    if (cond) {
                        // 生成Listing优化销售确认单
                        if (sign.it.name.equals(aosOrgnumber)) {
                            dyMain.set("aos_orgsmall", FndGlobal.getBaseId("RO", "bd_country"));
                        }
                        if (sign.es.name.equals(aosOrgnumber)) {
                            dyMain.set("aos_orgsmall", FndGlobal.getBaseId("PT", "bd_country"));
                        }
                        generateListingSalSmall(dyMain);
                    }
                } else if (sign.yes.name.equals(aosOsconfirmlov) && !sign.yes.name.equals(aosFunconfirm)) {
                    // 2.海外文字确认为是 海外功能图确认不为是
                    listingStatus = "海外编辑确认";
                    dyMain.set("aos_status", listingStatus);
                    dyMain.set("aos_ecdate", new Date());
                    return;// 不调整节点操作人 直接退出
                } else if (!sign.yes.name.equals(aosOsconfirmlov)) {
                    // 3.海外功能图确认=是，海外文字确认=否时，流程走到海外编辑确认功能图节点
                    listingStatus = "海外编辑确认:功能图";
                    listingUser = aosOseditor;
                    dyMain.set("aos_funcdate", new Date());
                    dyMain.set("aos_oseditor", aosOseditor);
                    if (sign.es.name.equals(aosOrgnumber)) {
                        generateOsSmall("PT", dyMain);
                    }
                }
                if (listingStatus == null || listingUser == null) {
                    throw new FndError("未获取到下一节点状态或操作人!");
                }
                // 回写设计需求表
                fillDesign(dyMain);
                dyMain.set("aos_status", listingStatus);
                dyMain.set("aos_user", listingUser);
                dyMain.set("aos_make", UserServiceHelper.getCurrentUserId());
                dyMain.set("aos_ecdate", new Date());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 来源类型=设计需求表时，编辑确认节点可编辑；提交后将值回写到设计需求表的功能图文案备注字段
     **/
    private static void fillDesign(DynamicObject dyMain) {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            String aosSourcetype = dyMain.getString("aos_sourcetype");
            if (sign.design.name.equals(aosSourcetype)) {
                String aosSourceid = dyMain.getString("aos_sourceid");
                // 设计需求表
                DynamicObject dyDesign = BusinessDataServiceHelper.loadSingle(aosSourceid, "aos_mkt_designreq");
                // 获取文中物料对应的行
                Map<String, DynamicObject> mapItemToRow =
                    dyMain.getDynamicObjectCollection("aos_entryentity").stream().collect(Collectors.toMap(
                        dy -> dy.getDynamicObject("aos_itemid").getString("id"), dy -> dy, (key1, key2) -> key1));
                DynamicObjectCollection dycDsign = dyDesign.getDynamicObjectCollection("aos_entryentity");
                for (DynamicObject dyRow : dycDsign) {
                    DynamicObject aosItemid = dyRow.getDynamicObject("aos_itemid");
                    if (aosItemid == null) {
                        continue;
                    }
                    String itemid = aosItemid.getString("id");
                    if (mapItemToRow.containsKey(itemid)) {
                        dyRow.set("aos_remakes", mapItemToRow.get(itemid).get("aos_remakes"));
                    }
                }
                SaveServiceHelper.update(new DynamicObject[] {dyDesign});
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 生成小站海外编辑确认:功能图 的listing优化需求小语种
     **/
    private static void generateOsSmall(String aosOrgnumber, DynamicObject dyMain) {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            Object aosOrgsmall = FndGlobal.get_import_id(aosOrgnumber, "bd_country");
            DynamicObject dyUser;
            Object aosOseditor = dyMain.get("aos_oseditor");
            if (aosOseditor instanceof String) {
                aosOseditor = dyMain.getString("aos_oseditor");
            } else if (aosOseditor instanceof Long) {
                aosOseditor = String.valueOf(aosOseditor);
            } else {
                aosOseditor = ((DynamicObject)aosOseditor).getString("id");
            }
            if (sign.ro.name.equalsIgnoreCase(aosOrgnumber)) {
                QFilter filter = new QFilter("number", "=", "024044");
                dyUser = QueryServiceHelper.queryOne("bos_user", "id,name,number", new QFilter[] {filter});
                if (dyUser != null) {
                    aosOseditor = dyUser.get("id");
                }
            } else if (sign.pt.name.equalsIgnoreCase(aosOrgnumber)) {
                QFilter filter = new QFilter("number", "=", "023186");
                dyUser = QueryServiceHelper.queryOne("bos_user", "id,name,number", new QFilter[] {filter});
                if (dyUser != null) {
                    aosOseditor = dyUser.get("id");
                }
            }
            DynamicObject aosMktListingMin = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_min");
            setListingMin(dyMain, aosOrgsmall, aosOseditor, aosMktListingMin);
            // BOTP
            aosMktListingMin.set("aos_sourcebilltype", "aos_mkt_listing_min");
            aosMktListingMin.set("aos_sourcebillno", dyMain.get("billno"));
            aosMktListingMin.set("aos_srcentrykey", "aos_entryentity");
            DynamicObjectCollection aosEntryentityS = dyMain.getDynamicObjectCollection("aos_entryentity");
            DynamicObjectCollection aosEntryentityNewS = aosMktListingMin.getDynamicObjectCollection("aos_entryentity");
            for (DynamicObject aosEntryentity : aosEntryentityS) {
                DynamicObject aosEntryentityNew = aosEntryentityNewS.addNew();
                aosEntryentityNew.set("aos_itemid", aosEntryentity.get("aos_itemid"));
                aosEntryentityNew.set("aos_is_saleout",
                    ProgressUtil.Is_saleout(aosEntryentity.getDynamicObject("aos_itemid").getPkValue()));
                aosEntryentityNew.set("aos_require", aosEntryentity.get("aos_require"));
                aosEntryentityNew.set("aos_case", aosEntryentity.get("aos_case"));
                aosEntryentityNew.set("aos_srcrowseq", aosEntryentity.get("SEQ"));
                DynamicObjectCollection aosAttribute = aosEntryentityNew.getDynamicObjectCollection("aos_attribute");
                aosAttribute.clear();
                DynamicObjectCollection aosAttributefrom = aosEntryentity.getDynamicObjectCollection("aos_attribute");
                DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
                DynamicObject tempFile;
                for (DynamicObject d : aosAttributefrom) {
                    tempFile = BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"), type);
                    aosAttribute.addNew().set("fbasedataid", tempFile);
                }
                setEntryNew(aosEntryentity, aosEntryentityNew);
            }
            OperationServiceHelper.executeOperate("save", "aos_mkt_listing_min", new DynamicObject[] {aosMktListingMin},
                OperateOption.create());
            // 修复关联关系
            try {
                ProgressUtil.botp("aos_mkt_listing_min", aosMktListingMin.get("id"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    public static void setEntryNew(DynamicObject aosEntryentity, DynamicObject aosEntryentityNew) {
        aosEntryentityNew.set("aos_write", aosEntryentity.get("aos_write"));
        aosEntryentityNew.set("aos_opt", aosEntryentity.get("aos_opt"));
        aosEntryentityNew.set("aos_pic", aosEntryentity.get("aos_pic"));
        aosEntryentityNew.set("aos_subtitle", aosEntryentity.get("aos_subtitle"));
        aosEntryentityNew.set("aos_title", aosEntryentity.get("aos_title"));
        aosEntryentityNew.set("aos_keyword", aosEntryentity.get("aos_keyword"));
        aosEntryentityNew.set("aos_other", aosEntryentity.get("aos_other"));
        aosEntryentityNew.set("aos_etc", aosEntryentity.get("aos_etc"));
        aosEntryentityNew.set("aos_segment3_r", aosEntryentity.get("aos_segment3_r"));
        aosEntryentityNew.set("aos_broitem_r", aosEntryentity.get("aos_broitem_r"));
        aosEntryentityNew.set("aos_itemname_r", aosEntryentity.get("aos_itemname_r"));
        aosEntryentityNew.set("aos_orgtext_r",
            ProgressUtil.getOrderOrg(aosEntryentity.getDynamicObject("aos_itemid").getPkValue()));
        DynamicObjectCollection aosSubentryentityNewS =
            aosEntryentityNew.getDynamicObjectCollection("aos_subentryentity");
        DynamicObject aosSubentryentity = aosEntryentity.getDynamicObjectCollection("aos_subentryentity").get(0);
        DynamicObject aosSubentryentityNew = aosSubentryentityNewS.addNew();
        aosSubentryentityNew.set("aos_segment3", aosSubentryentity.get("aos_segment3"));
        aosSubentryentityNew.set("aos_broitem", aosSubentryentity.get("aos_broitem"));
        aosSubentryentityNew.set("aos_itemname", aosSubentryentity.get("aos_itemname"));
        aosSubentryentityNew.set("aos_orgtext", aosSubentryentity.get("aos_orgtext"));
        aosSubentryentityNew.set("aos_reqinput", aosSubentryentity.get("aos_reqinput"));
        aosSubentryentityNew.set("aos_caseinput", aosSubentryentity.get("aos_caseinput"));
    }

    public static void setListingMin(DynamicObject dyMain, Object aosOrgsmall, Object aosOseditor,
        DynamicObject aosMktListingMin) {
        aosMktListingMin.set("billno", dyMain.get("billno"));
        aosMktListingMin.set("aos_user", aosOseditor);
        aosMktListingMin.set("aos_sourcetype", dyMain.get("aos_sourcetype"));
        aosMktListingMin.set("aos_status", "小站海外编辑确认:功能图");
        aosMktListingMin.set("aos_funcdate", new Date());
        aosMktListingMin.set("aos_requireby", dyMain.get("aos_requireby"));
        aosMktListingMin.set("aos_organization1", dyMain.get("aos_organization1"));
        aosMktListingMin.set("aos_organization2", dyMain.get("aos_organization2"));
        aosMktListingMin.set("aos_requiredate", dyMain.get("aos_requiredate"));
        aosMktListingMin.set("aos_demandate", dyMain.get("aos_demandate"));
        aosMktListingMin.set("aos_type", dyMain.get("aos_type"));
        aosMktListingMin.set("aos_source", dyMain.get("aos_source"));
        aosMktListingMin.set("aos_importance", dyMain.get("aos_importance"));
        aosMktListingMin.set("aos_designer", dyMain.get("aos_designer"));
        aosMktListingMin.set("aos_editor", dyMain.get("aos_editor"));
        aosMktListingMin.set("aos_editormin", dyMain.get("aos_editormin"));
        aosMktListingMin.set("aos_oseditor", dyMain.get("aos_oseditor"));
        aosMktListingMin.set("aos_orgid", dyMain.get("aos_orgid"));
        aosMktListingMin.set("aos_orgsmall", dyMain.get("aos_orgsmall"));
        aosMktListingMin.set("aos_osconfirmlov", dyMain.get("aos_osconfirmlov"));
        aosMktListingMin.set("aos_funconfirm", dyMain.get("aos_funconfirm"));
        aosMktListingMin.set("aos_orignbill", dyMain.get("aos_orignbill"));
        aosMktListingMin.set("aos_sourceid", dyMain.get("aos_sourceid"));
        aosMktListingMin.set("aos_orgsmall", aosOrgsmall);
    }

    /**
     * 生成功能图翻译任务台账数据表
     *
     **/
    private static void generateFuncSummary(String aosOrgnumber, DynamicObject dyMain) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            // 数据层
            Object billno = dyMain.get("billno");
            Object reqFid = dyMain.getPkValue();
            DynamicObjectCollection aosEntryentityS = dyMain.getDynamicObjectCollection("aos_entryentity");
            Object aosOrgid = FndGlobal.get_import_id(aosOrgnumber, "bd_country");
            for (DynamicObject aosEntryentity : aosEntryentityS) {
                DynamicObject aosMktFuncsumdata = BusinessDataServiceHelper.newDynamicObject("aos_mkt_funcsumdata");
                aosMktFuncsumdata.set("aos_orgid", aosOrgid);
                aosMktFuncsumdata.set("aos_itemid", aosEntryentity.get("aos_itemid"));
                aosMktFuncsumdata.set("aos_sourcebill", billno);
                aosMktFuncsumdata.set("aos_creationdate", new Date());
                aosMktFuncsumdata.set("aos_eng", "N");
                aosMktFuncsumdata.set("aos_sourceid", reqFid);
                OperationServiceHelper.executeOperate("save", "aos_mkt_funcsumdata",
                    new DynamicObject[] {aosMktFuncsumdata}, OperateOption.create());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 申请人提交
     **/
    private static void submitForApply(DynamicObject dyMain) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            // 异常参数
            FndError fndError = new FndError();
            // 数据层
            String messageId = null;
            String message = "Listing优化需求表小语种-编辑确认";
            Object aosEditormin = dyMain.get("aos_editormin");
            Object billno = dyMain.get("billno");
            Object reqFid = dyMain.getPkValue();
            DynamicObject aosItemid = null;
            Object aosOrignbill = dyMain.get("aos_orignbill");
            int size = dyMain.getDynamicObjectCollection("aos_entryentity").size();
            if (size > 0) {
                aosItemid = dyMain.getDynamicObjectCollection("aos_entryentity").get(0).getDynamicObject("aos_itemid");
            }
            Object aosOrgid = dyMain.get("aos_orgid");
            // 校验
            if (aosItemid == null) {
                fndError.add("物料必填!");
            }
            if (aosOrgid == null) {
                fndError.add("国别必填!");
            }
            if (fndError.getCount() > 0) {
                throw fndError;
            }
            if (aosEditormin != null) {
                messageId = String.valueOf(((DynamicObject)aosEditormin).getPkValue());
            }
            String type = "";
            if (dyMain.get(sign.type.name) != null) {
                type = dyMain.getString("aos_type");
            }
            // 任务类型为小语种或者功能图翻译，流转给小语种，其他类型流转给国别编辑
            if (aosEditormin == null && aosItemid != null && aosOrgid != null) {
                String category = MktComUtil.getItemCateNameZh(aosItemid.getPkValue());
                String[] categoryGroup = category.split(",");
                String aosCategory1 = null;
                String aosCategory2 = null;
                int categoryLength = categoryGroup.length;
                if (categoryLength > 0) {
                    aosCategory1 = categoryGroup[0];
                }
                if (categoryLength > 1) {
                    aosCategory2 = categoryGroup[1];
                }
                long aosOueditor = 0;
                if (FndGlobal.IsNotNull(aosCategory1) && FndGlobal.IsNotNull(aosCategory2)) {
                    Object orgid = ((DynamicObject)aosOrgid).getPkValue();
                    DynamicObject aosMktProgorguser =
                        ProgressUtil.minListtFindEditorByType(orgid, aosCategory1, aosCategory2, type);
                    if (aosMktProgorguser != null) {
                        aosOueditor = aosMktProgorguser.getLong("aos_user");
                    }
                }
                if (aosOueditor == 0) {
                    throw new FndError(aosCategory1 + "," + aosCategory2 + "小语种编辑师不存在!");
                }
                aosEditormin = aosOueditor;
                messageId = String.valueOf(aosEditormin);
                // 流转给小语种编辑师 或者编辑
                dyMain.set("aos_editormin", aosEditormin);
            }
            dyMain.set("aos_user", aosEditormin);
            dyMain.set("aos_status", "编辑确认");
            if (Cux_Common_Utl.IsNull(aosOrignbill)) {
                splitMinBySegment3(dyMain);
            }
            MktComUtil.sendGlobalMessage(messageId, "aos_mkt_listing_min", String.valueOf(reqFid),
                String.valueOf(billno), message);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 若为手工新增的单据 则根据产品号拆分单据
     **/
    private static void splitMinBySegment3(DynamicObject dyMain) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            // 开始汇总
            Map<String, List<DynamicObject>> segment3Map = new HashMap<>(16);
            List<DynamicObject> mapList;
            DynamicObjectCollection aosEntryentityS = dyMain.getDynamicObjectCollection("aos_entryentity");
            for (DynamicObject aosEntryentity : aosEntryentityS) {
                String aosSegment3R = aosEntryentity.getString("aos_segment3_r");
                mapList = segment3Map.get(aosSegment3R);
                if (mapList == null || mapList.size() == 0) {
                    mapList = new ArrayList<>();
                }
                mapList.add(aosEntryentity);
                segment3Map.put(aosSegment3R, mapList);
            }
            // 开始拆分
            int r = 1;
            aosEntryentityS.clear();
            for (String key : segment3Map.keySet()) {
                // 对于第一种产品号 保留于本单
                if (r == 1) {
                    mapList = segment3Map.get(key);
                    for (DynamicObject aosEntryentitylist : mapList) {
                        DynamicObject aosEntryentity = aosEntryentityS.addNew();
                        aosEntryentity.set("aos_itemid", aosEntryentitylist.get("aos_itemid"));
                        aosEntryentity.set("aos_require", aosEntryentitylist.get("aos_require"));
                        aosEntryentity.set("aos_case", aosEntryentitylist.get("aos_case"));
                        aosEntryentity.set("aos_write", aosEntryentitylist.get("aos_write"));
                        aosEntryentity.set("aos_opt", aosEntryentitylist.get("aos_opt"));
                        aosEntryentity.set("aos_pic", aosEntryentitylist.get("aos_pic"));
                        aosEntryentity.set("aos_subtitle", aosEntryentitylist.get("aos_subtitle"));
                        aosEntryentity.set("aos_title", aosEntryentitylist.get("aos_title"));
                        aosEntryentity.set("aos_keyword", aosEntryentitylist.get("aos_keyword"));
                        aosEntryentity.set("aos_other", aosEntryentitylist.get("aos_other"));
                        aosEntryentity.set("aos_etc", aosEntryentitylist.get("aos_etc"));
                        aosEntryentity.set("aos_segment3_r", aosEntryentitylist.get("aos_segment3_r"));
                        aosEntryentity.set("aos_broitem_r", aosEntryentitylist.get("aos_broitem_r"));
                        aosEntryentity.set("aos_itemname_r", aosEntryentitylist.get("aos_itemname_r"));
                        aosEntryentity.set("aos_orgtext_r", aosEntryentitylist.get("aos_orgtext_r"));
                        DynamicObjectCollection aosAttribute =
                            aosEntryentity.getDynamicObjectCollection("aos_attribute");
                        aosAttribute.clear();
                        DynamicObjectCollection aosAttributefrom =
                            aosEntryentitylist.getDynamicObjectCollection("aos_attribute");
                        DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
                        DynamicObject tempFile;
                        for (DynamicObject d : aosAttributefrom) {
                            tempFile =
                                BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"), type);
                            aosAttribute.addNew().set("fbasedataid", tempFile);
                        }
                        // 子单据体
                        DynamicObjectCollection aosSubentryentityListS =
                            aosEntryentitylist.getDynamicObjectCollection("aos_subentryentity");
                        DynamicObjectCollection aosSubentryentityS =
                            aosEntryentity.getDynamicObjectCollection("aos_subentryentity");
                        for (DynamicObject aosSubentryentityList : aosSubentryentityListS) {
                            DynamicObject aosSubentryentity = aosSubentryentityS.addNew();
                            aosSubentryentity.set("aos_segment3", aosSubentryentityList.get("aos_segment3"));
                            aosSubentryentity.set("aos_broitem", aosSubentryentityList.get("aos_broitem"));
                            aosSubentryentity.set("aos_itemname", aosSubentryentityList.get("aos_itemname"));
                            aosSubentryentity.set("aos_orgtext", aosSubentryentityList.get("aos_orgtext"));
                            aosSubentryentity.set("aos_reqinput", aosSubentryentityList.get("aos_reqinput"));
                            aosSubentryentity.set("aos_caseinput", aosSubentryentityList.get("aos_caseinput"));
                        }
                    }
                } else {
                    DynamicObject aosMktListingMinnew =
                        BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_min");
                    DynamicObjectCollection aosEntryentitynewS =
                        aosMktListingMinnew.getDynamicObjectCollection("aos_entryentity");
                    aosMktListingMinnew.set("aos_user", dyMain.get("aos_user"));
                    aosMktListingMinnew.set("aos_sourcetype", dyMain.get("aos_sourcetype"));
                    aosMktListingMinnew.set("aos_status", dyMain.get("aos_status"));
                    aosMktListingMinnew.set("aos_requireby", dyMain.get("aos_requireby"));
                    aosMktListingMinnew.set("aos_organization1", dyMain.get("aos_organization1"));
                    aosMktListingMinnew.set("aos_organization2", dyMain.get("aos_organization2"));
                    aosMktListingMinnew.set("aos_requiredate", dyMain.get("aos_requiredate"));
                    aosMktListingMinnew.set("aos_demandate", dyMain.get("aos_demandate"));
                    aosMktListingMinnew.set("aos_type", dyMain.get("aos_type"));
                    aosMktListingMinnew.set("aos_source", dyMain.get("aos_source"));
                    aosMktListingMinnew.set("aos_importance", dyMain.get("aos_importance"));
                    aosMktListingMinnew.set("aos_designer", dyMain.get("aos_designer"));
                    aosMktListingMinnew.set("aos_editor", dyMain.get("aos_editor"));
                    aosMktListingMinnew.set("aos_editormin", dyMain.get("aos_editormin"));
                    aosMktListingMinnew.set("aos_orgid", dyMain.get("aos_orgid"));
                    aosMktListingMinnew.set("aos_osconfirmlov", dyMain.get("aos_osconfirmlov"));
                    aosMktListingMinnew.set("aos_funconfirm", dyMain.get("aos_funconfirm"));
                    aosMktListingMinnew.set("aos_orignbill", dyMain.get("aos_orignbill"));
                    aosMktListingMinnew.set("aos_sourceid", dyMain.get("aos_sourceid"));
                    mapList = segment3Map.get(key);
                    for (DynamicObject aosEntryentitylist : mapList) {
                        DynamicObject aosEntryentitynew = aosEntryentitynewS.addNew();
                        aosEntryentitynew.set("aos_itemid", aosEntryentitylist.get("aos_itemid"));
                        aosEntryentitynew.set("aos_require", aosEntryentitylist.get("aos_require"));
                        aosEntryentitynew.set("aos_case", aosEntryentitylist.get("aos_case"));
                        aosEntryentitynew.set("aos_write", aosEntryentitylist.get("aos_write"));
                        aosEntryentitynew.set("aos_opt", aosEntryentitylist.get("aos_opt"));
                        aosEntryentitynew.set("aos_pic", aosEntryentitylist.get("aos_pic"));
                        aosEntryentitynew.set("aos_subtitle", aosEntryentitylist.get("aos_subtitle"));
                        aosEntryentitynew.set("aos_title", aosEntryentitylist.get("aos_title"));
                        aosEntryentitynew.set("aos_keyword", aosEntryentitylist.get("aos_keyword"));
                        aosEntryentitynew.set("aos_other", aosEntryentitylist.get("aos_other"));
                        aosEntryentitynew.set("aos_etc", aosEntryentitylist.get("aos_etc"));
                        aosEntryentitynew.set("aos_segment3_r", aosEntryentitylist.get("aos_segment3_r"));
                        aosEntryentitynew.set("aos_broitem_r", aosEntryentitylist.get("aos_broitem_r"));
                        aosEntryentitynew.set("aos_itemname_r", aosEntryentitylist.get("aos_itemname_r"));
                        aosEntryentitynew.set("aos_orgtext_r", aosEntryentitylist.get("aos_orgtext_r"));
                        DynamicObjectCollection aosAttribute =
                            aosEntryentitynew.getDynamicObjectCollection("aos_attribute");
                        aosAttribute.clear();
                        DynamicObjectCollection aosAttributefrom =
                            aosEntryentitylist.getDynamicObjectCollection("aos_attribute");
                        DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
                        DynamicObject tempFile;
                        for (DynamicObject d : aosAttributefrom) {
                            tempFile =
                                BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"), type);
                            aosAttribute.addNew().set("fbasedataid", tempFile);
                        }
                        // 子单据体
                        DynamicObjectCollection aosSubentryentityListS =
                            aosEntryentitylist.getDynamicObjectCollection("aos_subentryentity");
                        DynamicObjectCollection aosSubentryentityS =
                            aosEntryentitynew.getDynamicObjectCollection("aos_subentryentity");
                        for (DynamicObject aosSubentryentityList : aosSubentryentityListS) {
                            DynamicObject aosSubentryentity = aosSubentryentityS.addNew();
                            aosSubentryentity.set("aos_segment3", aosSubentryentityList.get("aos_segment3"));
                            aosSubentryentity.set("aos_broitem", aosSubentryentityList.get("aos_broitem"));
                            aosSubentryentity.set("aos_itemname", aosSubentryentityList.get("aos_itemname"));
                            aosSubentryentity.set("aos_orgtext", aosSubentryentityList.get("aos_orgtext"));
                            aosSubentryentity.set("aos_reqinput", aosSubentryentityList.get("aos_reqinput"));
                            aosSubentryentity.set("aos_caseinput", aosSubentryentityList.get("aos_caseinput"));
                        }
                    }
                    // 保存拆分单
                    OperationServiceHelper.executeOperate("save", "aos_mkt_listing_min",
                        new DynamicObject[] {aosMktListingMinnew}, OperateOption.create());
                }
                r++;
            }
            // 保存本单
            OperationServiceHelper.executeOperate("save", "aos_mkt_listing_min", new DynamicObject[] {dyMain},
                OperateOption.create());
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 如果是设计需求表类型 完成后生成一个 设计需求表 任务类型=翻译
     *
     **/
    public static String generateDesign(DynamicObject dyMain, String pAosItemid, String orgid) throws FndError {
        // 数据层
        Object billno = dyMain.get("billno");
        Object reqFid = dyMain.getPkValue();
        DynamicObject dyDesigner = dyMain.getDynamicObject("aos_designer");
        Object aosRequireby = dyMain.get("aos_requireby");
        Object aosRequirebyid = ((DynamicObject)aosRequireby).getPkValue();
        DynamicObject aosOrgid = dyMain.getDynamicObject("aos_orgid");
        if (!Cux_Common_Utl.IsNull(pAosItemid) && !Cux_Common_Utl.IsNull(orgid)) {
            // 来源类型为台账，且台账的国别不为空
            QFilter filterId = new QFilter("id", "=", orgid);
            aosOrgid = QueryServiceHelper.queryOne("bd_country", "id,number", new QFilter[] {filterId});
        }
        Object orgId = aosOrgid.get("id");
        DynamicObjectCollection aosEntryentityS = dyMain.getDynamicObjectCollection("aos_entryentity");
        Object lastItemId = null;
        // 初始化
        DynamicObject aosMktDesignreq = BusinessDataServiceHelper.newDynamicObject("aos_mkt_designreq");
        aosMktDesignreq.set("aos_requiredate", new Date());
        aosMktDesignreq.set("aos_type", "翻译");
        aosMktDesignreq.set("aos_orignbill", billno);
        aosMktDesignreq.set("aos_sourceid", reqFid);
        aosMktDesignreq.set("aos_status", "设计");
        // BOTP
        aosMktDesignreq.set("aos_sourcebilltype", "aos_mkt_listing_min");
        aosMktDesignreq.set("aos_sourcebillno", dyMain.get("billno"));
        aosMktDesignreq.set("aos_srcentrykey", "aos_entryentity");
        mkt.progress.design.AosMktDesignReqBill.setEntityValue(aosMktDesignreq);
        aosMktDesignreq.set("aos_requireby", aosRequireby);
        aosMktDesignreq.set("aos_sourcetype", "LISTING");
        aosMktDesignreq.set("aos_orgid", orgid);
        List<DynamicObject> mapList = Cux_Common_Utl.GetUserOrg(aosRequirebyid);
        if (mapList != null) {
            if (mapList.get(TWO) != null) {
                aosMktDesignreq.set("aos_organization1", mapList.get(TWO).get("id"));
            }
            if (mapList.get(THREE) != null) {
                aosMktDesignreq.set("aos_organization2", mapList.get(THREE).get("id"));
            }
        }
        // 循环货号生成
        DynamicObjectCollection desEntryentityS = aosMktDesignreq.getDynamicObjectCollection("aos_entryentity");
        for (DynamicObject aosEntryentity : aosEntryentityS) {
            Object aosItemid = aosEntryentity.get("aos_itemid.id");
            if (!Cux_Common_Utl.IsNull(pAosItemid) && !pAosItemid.equals(String.valueOf(aosItemid))) {
                continue; // 若为功能图需求任务台账调用 则只生成对应货号数据
            }
            DynamicObject desEntryentity = desEntryentityS.addNew();
            lastItemId = aosItemid;
            StringBuilder aosContrybrandStr = new StringBuilder();
            StringBuilder aosOrgtext = new StringBuilder();
            DynamicObject bdMaterial = BusinessDataServiceHelper.loadSingle(aosItemid, "bd_material");
            DynamicObjectCollection aosContryentryS = bdMaterial.getDynamicObjectCollection("aos_contryentry");
            // 获取所有国家品牌 字符串拼接 终止
            Set<String> setBra = new HashSet<>();
            for (DynamicObject aosContryentry : aosContryentryS) {
                // 物料国别
                DynamicObject aosNationality = aosContryentry.getDynamicObject("aos_nationality");
                // 物料国别编码
                String aosNationalitynumber = aosNationality.getString("number");
                if ("IE".equals(aosNationalitynumber)) {
                    continue;
                }
                Object itemOrgId = aosNationality.get("id");
                int osQty = ItemInfoUtil.getItemOsQty(itemOrgId, aosItemid);
                int onQty = CommonDataSomQuo.get_on_hand_qty(Long.parseLong(itemOrgId.toString()),
                    Long.parseLong(aosItemid.toString()));
                osQty += onQty;
                int safeQty = ItemInfoUtil.getSafeQty(itemOrgId);
                if ("C".equals(aosContryentry.getString("aos_contryentrystatus")) && osQty < safeQty) {
                    continue;
                }
                aosOrgtext.append(aosNationalitynumber).append(";");
                Object obj = aosContryentry.getDynamicObject("aos_contrybrand");
                if (obj == null) {
                    continue;
                }
                // 物料品牌编码
                String value = aosContryentry.getDynamicObject("aos_contrybrand").getString("number");
                if (value != null) {
                    setBra.add(value);
                }
                if (setBra.size() > 1) {
                    if (value != null && !aosContrybrandStr.toString().contains(value)) {
                        aosContrybrandStr.append(value).append(";");
                    }
                } else if (setBra.size() == 1) {
                    if (value != null) {
                        aosContrybrandStr = new StringBuilder(value);
                    }
                }
            }
            String itemNumber = bdMaterial.getString("number");
            // 图片字段
            String url = "https://clss.s3.amazonaws.com/" + itemNumber + ".jpg";
            String aosProductno = bdMaterial.getString("aos_productno");
            String aosItemname = bdMaterial.getString("name");
            // 获取同产品号物料
            QFilter filterProductno = new QFilter("aos_productno", QCP.equals, aosProductno);
            QFilter[] filters = new QFilter[] {filterProductno};
            String selectColumn = "number,aos_type";
            StringBuilder aosBroitem = new StringBuilder();
            DynamicObjectCollection bdMaterialS = QueryServiceHelper.query("bd_material", selectColumn, filters);
            for (DynamicObject bd : bdMaterialS) {
                if ("B".equals(bd.getString("aos_type"))) {
                    continue; // 配件不获取
                }
                String number = bd.getString("number");
                if (!itemNumber.equals(number)) {
                    aosBroitem.append(number).append(";");
                }
            }
            // 翻译类型的设计需求表需带出申请人要求
            desEntryentity.set("aos_desreq", aosEntryentity.get("aos_require"));
            desEntryentity.set("aos_itemid", aosItemid);
            desEntryentity.set("aos_is_saleout", ProgressUtil.Is_saleout(aosItemid));
            desEntryentity.set("aos_srcrowseq", aosEntryentity.get("SEQ"));
            desEntryentity.set("aos_remakes", aosEntryentity.get("aos_remakes"));
            DynamicObjectCollection aosSubentryentityS =
                desEntryentity.getDynamicObjectCollection("aos_subentryentity");
            DynamicObject aosSubentryentity = aosSubentryentityS.addNew();
            aosSubentryentity.set("aos_sub_item", aosItemid);
            aosSubentryentity.set("aos_segment3", aosProductno);
            aosSubentryentity.set("aos_itemname", aosItemname);
            aosSubentryentity.set("aos_brand", aosContrybrandStr.toString());
            aosSubentryentity.set("aos_pic", url);
            aosSubentryentity.set("aos_developer", bdMaterial.get("aos_developer"));
            aosSubentryentity.set("aos_seting1", bdMaterial.get("aos_seting_cn"));
            aosSubentryentity.set("aos_seting2", bdMaterial.get("aos_seting_en"));
            aosSubentryentity.set("aos_spec", bdMaterial.get("aos_specification_cn"));
            aosSubentryentity.set("aos_url", MktS3Pic.getItemPicture(itemNumber));
            aosSubentryentity.set("aos_broitem", aosBroitem.toString());
            aosSubentryentity.set("aos_orgtext", aosOrgtext.toString());
            StringJoiner productStyle = new StringJoiner(";");
            DynamicObjectCollection item = bdMaterial.getDynamicObjectCollection("aos_productstyle_new");
            if (item.size() != 0) {
                List<Object> id =
                    item.stream().map(e -> e.getDynamicObject("fbasedataid").getPkValue()).collect(Collectors.toList());
                for (Object a : id) {
                    DynamicObject dysty = QueryServiceHelper.queryOne("aos_product_style", "id,name",
                        new QFilter("id", QCP.equals, a).toArray());
                    String styname = dysty.getString("name");
                    productStyle.add(styname);
                }
                aosSubentryentity.set("aos_productstyle_new", productStyle.toString());
            }
            aosSubentryentity.set("aos_shootscenes", bdMaterial.getString("aos_shootscenes"));
        }
        // 产品类别
        String category = MktComUtil.getItemCateNameZh(lastItemId);
        String[] categoryGroup = category.split(",");
        String aosCategory1 = null;
        String aosCategory2 = null;
        int categoryLength = categoryGroup.length;
        if (categoryLength > 0) {
            aosCategory1 = categoryGroup[0];
        }
        if (categoryLength > 1) {
            aosCategory2 = categoryGroup[1];
        }
        String designerId = "";
        String messageId = null;
        DynamicObject aosMktProguser1 = QueryServiceHelper.queryOne("aos_mkt_proguser", "aos_designer",
            new QFilter("aos_category1", QCP.equals, aosCategory1).and("aos_category2", QCP.equals, aosCategory2)
                .toArray());
        if (FndGlobal.IsNotNull(aosMktProguser1)) {
            designerId = aosMktProguser1.getString("aos_designer");
        }
        if (dyDesigner != null && FndGlobal.IsNull(designerId)) {
            messageId = String.valueOf(dyDesigner.getPkValue());
            designerId = dyDesigner.getString("id");
        }
        aosMktDesignreq.set("aos_user", designerId);
        if (FndGlobal.IsNull(designerId)) {
            aosMktDesignreq.set("aos_user", aosRequirebyid);
        }
        aosMktDesignreq.set("aos_designer", designerId);
        // 根据大类中类获取对应营销人员
        if (FndGlobal.IsNotNull(aosCategory1) && FndGlobal.IsNotNull(aosCategory2)) {
            String type = "";
            if (dyMain.get(sign.type.name) != null && !"".equals(dyMain.getString(sign.type.name))) {
                type = dyMain.getString("aos_type");
            }
            String[] selectFields = new String[] {"aos_designeror", "aos_3d"};
            DynamicObject aosMktProguser =
                ProgressUtil.findDesignerByType(orgId, aosCategory1, aosCategory2, type, selectFields);
            if (aosMktProguser != null) {
                aosMktDesignreq.set("aos_dm", aosMktProguser.get("aos_designeror"));
                aosMktDesignreq.set("aos_3der", aosMktProguser.get("aos_3d"));
                aosMktDesignreq.set("aos_designer", aosMktProguser.get("aos_designer"));
                aosMktDesignreq.set("aos_user", aosMktProguser.get("aos_designer"));
                messageId = aosMktProguser.getString("aos_designer");
            }
        }
        AosMktDesignReqBill.createDesiginBeforeSave(aosMktDesignreq);
        OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_designreq",
            new DynamicObject[] {aosMktDesignreq}, OperateOption.create());
        if (operationrst.isSuccess()) {
            Object pk = operationrst.getSuccessPkIds().get(0);
            FndHistory.Create("aos_mkt_designreq", pk.toString(), "新建",
                "新建节点: " + aosMktDesignreq.getString("aos_status"));
        }
        // 修复关联关系
        try {
            ProgressUtil.botp("aos_mkt_designreq", aosMktDesignreq.get("id"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
            MktComUtil.sendGlobalMessage(messageId, "aos_mkt_designreq",
                String.valueOf(operationrst.getSuccessPkIds().get(0)), aosMktDesignreq.getString("billno"),
                "设计需求表-Listing优化需求表子表自动创建翻译类型");
            FndHistory.Create(aosMktDesignreq, aosMktDesignreq.getString("aos_status"), "设计需求表-Listing优化需求表子表自动创建翻译类型");
        }
        return aosMktDesignreq.getString("billno");
    }

    /**
     * 如果是Listing类型生成销售信息确认单
     **/
    private static void generateListingSalSmall(DynamicObject dyMain) throws FndError {
        // 信息处理
        String messageId;
        String message;
        // 数据层
        DynamicObject aosDesignerObj = dyMain.getDynamicObject("aos_designer");
        Object aosDesigner = null;
        if (!Cux_Common_Utl.IsNull(aosDesignerObj)) {
            aosDesigner = aosDesignerObj.get("id");
        }
        Object billno = dyMain.get("billno");
        Object reqFid = dyMain.getPkValue();
        // 小站国别
        Object aosOrgid = dyMain.get("aos_orgsmall");
        Object aosType = dyMain.get("aos_type");
        DynamicObject aosEditorminObj = dyMain.getDynamicObject("aos_editormin");
        Object aosEditorminid = null;
        if (!Cux_Common_Utl.IsNull(aosEditorminObj)) {
            aosEditorminid = aosEditorminObj.get("id");
        }
        // 设计
        // 编辑确认师
        Object aosMake = null;
        DynamicObject dyMake = dyMain.getDynamicObject("aos_make");
        if (dyMake != null) {
            aosMake = dyMake.getPkValue();
        }

        DynamicObjectCollection aosEntryentityS = dyMain.getDynamicObjectCollection("aos_entryentity");
        // 初始化
        DynamicObject aosMktListingSal = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_sal");
        aosMktListingSal.set("aos_requireby", aosMake);
        aosMktListingSal.set("aos_designer", aosDesigner);
        aosMktListingSal.set("aos_status", "销售确认");
        aosMktListingSal.set("aos_orgid", aosOrgid);
        aosMktListingSal.set("aos_orignbill", billno);
        aosMktListingSal.set("aos_sourceid", reqFid);
        aosMktListingSal.set("aos_type", aosType);
        aosMktListingSal.set("aos_requiredate", new Date());
        aosMktListingSal.set("aos_editor", aosEditorminid);
        aosMktListingSal.set("aos_sourcetype", "Listing优化需求表小语种");
        // BOTP
        aosMktListingSal.set("aos_sourcebilltype", "aos_mkt_listing_min");
        aosMktListingSal.set("aos_sourcebillno", dyMain.get("billno"));
        aosMktListingSal.set("aos_srcentrykey", "aos_entryentity");
        DynamicObjectCollection cmpEntryentityS = aosMktListingSal.getDynamicObjectCollection("aos_entryentity");
        for (DynamicObject aosEntryentity : aosEntryentityS) {
            DynamicObject aosSubentryentity = aosEntryentity.getDynamicObjectCollection("aos_subentryentity").get(0);
            DynamicObject cmpEntryentity = cmpEntryentityS.addNew();
            cmpEntryentity.set("aos_itemid", aosEntryentity.get("aos_itemid"));
            cmpEntryentity.set("aos_segment3", aosSubentryentity.get("aos_segment3"));
            cmpEntryentity.set("aos_itemname", aosSubentryentity.get("aos_itemname"));
            cmpEntryentity.set("aos_broitem", aosSubentryentity.get("aos_broitem"));
            cmpEntryentity.set("aos_salestatus", "已确认");
            cmpEntryentity.set("aos_text", aosEntryentity.get("aos_case"));
            cmpEntryentity.set("aos_srcrowseq", aosEntryentity.get("SEQ"));
        }
        Object itemId = aosEntryentityS.get(0).getDynamicObject("aos_itemid").getPkValue();
        String category = MktComUtil.getItemCateNameZh(itemId);
        String[] categoryGroup = category.split(",");
        String aosCategory1 = null;
        String aosCategory2 = null;
        int categoryLength = categoryGroup.length;
        if (categoryLength > 0) {
            aosCategory1 = categoryGroup[0];
        }
        if (categoryLength > 1) {
            aosCategory2 = categoryGroup[1];
        }
        long aosSale = 0;
        if (FndGlobal.IsNotNull(aosCategory1) && FndGlobal.IsNotNull(aosCategory2)) {
            QFilter filterCategory1 = new QFilter("aos_category1", "=", aosCategory1);
            QFilter filterCategory2 = new QFilter("aos_category2", "=", aosCategory2);
            Object id = "";
            if (aosOrgid != null) {
                try {
                    DynamicObject dyOrg = (DynamicObject)aosOrgid;
                    id = dyOrg.getString("id");
                } catch (Exception ex) {
                    id = String.valueOf((long)aosOrgid);
                }
            }
            QFilter filterOu = new QFilter("aos_orgid", "=", id);
            QFilter[] filtersCategory = new QFilter[] {filterCategory1, filterCategory2, filterOu};
            String selectStr = "aos_salehelper aos_salehelper";
            DynamicObject aosMktProgorguser =
                QueryServiceHelper.queryOne("aos_mkt_progorguser", selectStr, filtersCategory);
            if (aosMktProgorguser != null) {
                aosSale = aosMktProgorguser.getLong("aos_salehelper");
            }
        }
        if (aosSale == 0) {
            throw new FndError(aosCategory1 + "," + aosCategory2 + "国别销售不存在!");
        }
        aosMktListingSal.set("aos_sale", aosSale);
        aosMktListingSal.set("aos_user", aosSale);
        messageId = String.valueOf(aosSale);
        message = "Listing优化销售确认单-Listing优化销售确认表小语种自动创建";
        OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_sal",
            new DynamicObject[] {aosMktListingSal}, OperateOption.create());
        // 修复关联关系
        try {
            ProgressUtil.botp("aos_mkt_listing_sal", aosMktListingSal.get("id"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
            MktComUtil.sendGlobalMessage(messageId, String.valueOf(aosMktListingSal),
                String.valueOf(operationrst.getSuccessPkIds().get(0)), aosMktListingSal.getString("billno"), message);
        }
    }

    /**
     * 如果是Listing类型生成销售信息确认单
     **/
    private static void generateListingSal(DynamicObject dyMain) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            // 信息处理
            String messageId;
            String message;
            // 数据层
            DynamicObject aosDesignerObj = dyMain.getDynamicObject("aos_designer");
            Object aosDesigner = null;
            if (!Cux_Common_Utl.IsNull(aosDesignerObj)) {
                aosDesigner = aosDesignerObj.get("id");
            }
            Object billno = dyMain.get("billno");
            Object reqFid = dyMain.getPkValue();
            Object aosOrgid = dyMain.get("aos_orgid");
            Object aosType = dyMain.get("aos_type");
            DynamicObject aosEditorminObj = dyMain.getDynamicObject("aos_editormin");
            Object aosEditorminid = null;
            if (!Cux_Common_Utl.IsNull(aosEditorminObj)) {
                aosEditorminid = aosEditorminObj.get("id");
            }
            // 编辑确认师
            Object aosMake = null;
            DynamicObject dyMake = dyMain.getDynamicObject("aos_make");
            if (dyMake != null) {
                aosMake = dyMake.getPkValue();
            }
            DynamicObjectCollection aosEntryentityS = dyMain.getDynamicObjectCollection("aos_entryentity");
            // 初始化
            DynamicObject aosMktListingSal = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_sal");
            aosMktListingSal.set("aos_requireby", aosMake);
            aosMktListingSal.set("aos_designer", aosDesigner);
            aosMktListingSal.set("aos_status", "销售确认");
            aosMktListingSal.set("aos_orgid", aosOrgid);
            aosMktListingSal.set("aos_orignbill", billno);
            aosMktListingSal.set("aos_sourceid", reqFid);
            aosMktListingSal.set("aos_type", aosType);
            aosMktListingSal.set("aos_requiredate", new Date());
            aosMktListingSal.set("aos_editor", aosEditorminid);
            aosMktListingSal.set("aos_sourcetype", "Listing优化需求表小语种");
            // BOTP
            aosMktListingSal.set("aos_sourcebilltype", "aos_mkt_listing_min");
            aosMktListingSal.set("aos_sourcebillno", dyMain.get("billno"));
            aosMktListingSal.set("aos_srcentrykey", "aos_entryentity");
            DynamicObjectCollection cmpEntryentityS = aosMktListingSal.getDynamicObjectCollection("aos_entryentity");
            for (DynamicObject aosEntryentity : aosEntryentityS) {
                DynamicObject aosSubentryentity =
                    aosEntryentity.getDynamicObjectCollection("aos_subentryentity").get(0);
                DynamicObject cmpEntryentity = cmpEntryentityS.addNew();
                cmpEntryentity.set("aos_itemid", aosEntryentity.get("aos_itemid"));
                cmpEntryentity.set("aos_segment3", aosSubentryentity.get("aos_segment3"));
                cmpEntryentity.set("aos_itemname", aosSubentryentity.get("aos_itemname"));
                cmpEntryentity.set("aos_broitem", aosSubentryentity.get("aos_broitem"));
                cmpEntryentity.set("aos_salestatus", "已确认");
                cmpEntryentity.set("aos_text", aosEntryentity.get("aos_case"));
                cmpEntryentity.set("aos_srcrowseq", aosEntryentity.get("SEQ"));
            }
            Object itemId = aosEntryentityS.get(0).getDynamicObject("aos_itemid").getPkValue();
            String category = MktComUtil.getItemCateNameZh(itemId);
            String[] categoryGroup = category.split(",");
            String aosCategory1 = null;
            String aosCategory2 = null;
            int categoryLength = categoryGroup.length;
            if (categoryLength > 0) {
                aosCategory1 = categoryGroup[0];
            }
            if (categoryLength > 1) {
                aosCategory2 = categoryGroup[1];
            }
            long aosSale = 0;
            long aosSale1 = 0;
            if (FndGlobal.IsNotNull(aosCategory1) && FndGlobal.IsNotNull(aosCategory2)) {
                QFilter filterCategory1 = new QFilter("aos_category1", "=", aosCategory1);
                QFilter filterCategory2 = new QFilter("aos_category2", "=", aosCategory2);
                String id = "";
                if (aosOrgid != null) {
                    DynamicObject dyOrg = (DynamicObject)aosOrgid;
                    id = dyOrg.getString("id");
                }
                QFilter filterOu = new QFilter("aos_orgid", "=", id);
                QFilter[] filtersCategory = new QFilter[] {filterCategory1, filterCategory2, filterOu};
                String selectStr = "aos_salehelper aos_salehelper";
                DynamicObject aosMktProgorguser =
                    QueryServiceHelper.queryOne("aos_mkt_progorguser", selectStr, filtersCategory);
                if (aosMktProgorguser != null) {
                    aosSale = aosMktProgorguser.getLong("aos_salehelper");
                    aosSale1 = aosMktProgorguser.getLong("aos_salehelper");
                }
            }
            if (aosSale == 0) {
                throw new FndError(aosCategory1 + "," + aosCategory2 + "国别销售不存在!");
            }
            String sourceType = dyMain.getString("aos_sourcetype");
            if (sign.listing.name.equals(sourceType) && sign.old.name.equals(aosType)) {
                try {
                    aosSale = Long.parseLong(dyMain.getDynamicObject("aos_requireby").getString("id"));
                } catch (Exception ex) {
                    aosSale = dyMain.getLong("aos_requireby");
                }
                // 判断申请人是否在表中存在
                DynamicObject orgUser = null;
                if (aosOrgid != null) {
                    orgUser = QueryServiceHelper.queryOne("aos_mkt_progorguser", "id",
                        new QFilter("aos_salehelper", QCP.equals, aosSale)
                            .and("aos_orgid", QCP.equals, aosOrgid.toString()).toArray());
                }
                if (orgUser == null) {
                    aosSale = aosSale1;
                }
            }
            aosMktListingSal.set("aos_sale", aosSale);
            aosMktListingSal.set("aos_user", aosSale);
            messageId = String.valueOf(aosSale);
            message = "Listing优化销售确认单-Listing优化销售确认表小语种自动创建";
            OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_sal",
                new DynamicObject[] {aosMktListingSal}, OperateOption.create());
            // 修复关联关系
            try {
                ProgressUtil.botp("aos_mkt_listing_sal", aosMktListingSal.get("id"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                MktComUtil.sendGlobalMessage(messageId, String.valueOf(aosMktListingSal),
                    String.valueOf(operationrst.getSuccessPkIds().get(0)), aosMktListingSal.getString("billno"),
                    message);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 值校验
     **/
    private static void saveControl(DynamicObject dyMain) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            FndError fndError = new FndError();
            // 数据层
            Object aosOrgid = dyMain.get("aos_orgid");
            Object aosSourcetype = dyMain.get("aos_sourcetype");
            // 海外文字确认
            Object aosOsconfirmlov = dyMain.get("aos_osconfirmlov");
            // 海外功能图确认
            Object aosFunconfirm = dyMain.get("aos_funconfirm");
            Object aosType = dyMain.get("aos_type");
            String aosStatus = dyMain.getString("aos_status");
            DynamicObject aosOrganization2 = dyMain.getDynamicObject("aos_organization2");
            if (sign.yes.name.equals(aosOsconfirmlov) && sign.yes.name.equals(aosFunconfirm)) {
                fndError.add("文字确认与功能图确认不能同时为是!");
            }
            if (sign.listing.name.equals(aosSourcetype) && aosOrgid == null) {
                fndError.add("Listing类型国别字段必填!");
            }
            // AddByCzj 2023/01/09 禅道反馈7472
            if (FndGlobal.IsNotNull(aosOrganization2)
                && !sign.dept.name.equals(aosOrganization2.getString(sign.nameCode.name))
                && sign.translate.name.equals(aosType) && "申请人".equals(aosStatus)) {
                fndError.add("只允许编辑人员提功能图翻译流程!");
            }
            // 校验
            if (fndError.getCount() > 0) {
                throw fndError;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 物料改变和导入数据后的单据行赋值
     *
     */
    private static void entityRowSetValue(DynamicObject dyRow) {
        DynamicObject aosItemid = dyRow.getDynamicObject("aos_itemid");
        DynamicObject dyParent = (DynamicObject)dyRow.getParent();
        if (aosItemid == null) {
            dyRow.set("aos_segment3_r", null);
            dyRow.set("aos_broitem_r", null);
            dyRow.set("aos_itemname_r", null);
            dyRow.set("aos_orgtext_r", null);
            dyRow.set("aos_is_saleout", false);
            DynamicObjectCollection dycSub = dyRow.getDynamicObjectCollection("aos_subentryentity");
            if (dycSub.size() > 0) {
                DynamicObject dySub = dycSub.get(0);
                dySub.set("aos_segment3", null);
                dySub.set("aos_broitem", null);
                dySub.set("aos_itemname", null);
                dySub.set("aos_orgtext", null);
            }
        } else {
            Object itemId = aosItemid.getPkValue();
            String aosOrgtext = ProgressUtil.getOrderOrg(itemId);
            DynamicObject bdMaterial = BusinessDataServiceHelper.loadSingle(itemId, "bd_material");
            String itemNumber = bdMaterial.getString("number");
            String aosProductno = bdMaterial.getString("aos_productno");
            String aosItemname = bdMaterial.getString("name");
            dyRow.set("aos_is_saleout", ProgressUtil.Is_saleout(itemId));
            // 获取同产品号物料
            QFilter filterProductno = new QFilter("aos_productno", QCP.equals, aosProductno);
            QFilter[] filters = new QFilter[] {filterProductno};
            String selectColumn = "number,aos_type";
            StringBuilder aosBroitem = new StringBuilder();
            if (FndGlobal.IsNotNull(aosProductno)) {
                DynamicObjectCollection bdMaterialS = QueryServiceHelper.query("bd_material", selectColumn, filters);
                for (DynamicObject bd : bdMaterialS) {
                    if ("B".equals(bd.getString("aos_type"))) {
                        continue; // 配件不获取
                    }
                    String number = bd.getString("number");
                    if (!itemNumber.equals(number)) {
                        aosBroitem.append(number).append(";");
                    }
                }
            }
            // 获取英语编辑
            String category = MktComUtil.getItemCateNameZh(itemId);
            String[] categoryGroup = category.split(",");
            String aosCategory1 = null;
            String aosCategory2 = null;
            int categoryLength = categoryGroup.length;
            if (categoryLength > 0) {
                aosCategory1 = categoryGroup[0];
            }
            if (categoryLength > 1) {
                aosCategory2 = categoryGroup[1];
            }
            long aosEditor = 0;
            if (FndGlobal.IsNotNull(aosCategory1) && FndGlobal.IsNotNull(aosCategory2)) {
                QFilter filterCategory1 = new QFilter("aos_category1", "=", aosCategory1);
                QFilter filterCategory2 = new QFilter("aos_category2", "=", aosCategory2);
                QFilter[] filtersCategory = new QFilter[] {filterCategory1, filterCategory2};
                String selectStr = "aos_eng aos_editor";
                DynamicObject aosMktProguser =
                    QueryServiceHelper.queryOne("aos_mkt_proguser", selectStr, filtersCategory);
                if (aosMktProguser != null) {
                    aosEditor = aosMktProguser.getLong("aos_editor");
                }
            }

            long aosDesigner = 0;
            if (FndGlobal.IsNotNull(aosCategory1) && FndGlobal.IsNotNull(aosCategory2)) {
                String type = "";
                if (FndGlobal.IsNotNull(dyParent.get(sign.type.name))) {
                    type = dyParent.getString("aos_type");
                }
                String orgId = "";
                if (dyParent.get(sign.orgid.name) != null) {
                    orgId = dyParent.getDynamicObject("aos_orgid").getString("id");
                }
                String[] selectFields = new String[] {"aos_eng aos_editor"};
                DynamicObject aosMktProguser =
                    ProgressUtil.findDesignerByType(orgId, aosCategory1, aosCategory2, type, selectFields);
                if (aosMktProguser != null) {
                    aosDesigner = aosMktProguser.getLong("aos_designer");
                }
            }
            // 设计师
            if (dyParent.get(sign.designer.name) == null) {
                dyParent.set("aos_designer", aosDesigner);
            }
            // 英语编辑师
            if (dyParent.get(sign.editor.name) == null) {
                dyParent.set("aos_editor", aosEditor);
            }
            // 赋值物料相关
            dyRow.set("aos_segment3_r", aosProductno);
            dyRow.set("aos_itemname_r", aosItemname);
            dyRow.set("aos_broitem_r", aosBroitem.toString());
            dyRow.set("aos_orgtext_r", aosOrgtext);
            DynamicObjectCollection dycSub = dyRow.getDynamicObjectCollection("aos_subentryentity");
            if (dycSub.size() == 0) {
                dycSub.addNew();
            }
            DynamicObject dySubFirstRow = dycSub.get(0);
            dySubFirstRow.set("aos_segment3", aosProductno);
            dySubFirstRow.set("aos_itemname", aosItemname);
            dySubFirstRow.set("aos_broitem", aosBroitem.toString());
            dySubFirstRow.set("aos_orgtext", aosOrgtext);
        }
    }

    @Override
    public void hyperLinkClick(HyperLinkClickEvent hyperLinkClickEvent) {
        int rowIndex = hyperLinkClickEvent.getRowIndex();
        String fieldName = hyperLinkClickEvent.getFieldName();
        if (sign.segment3.name.equals(fieldName)) {
            Object aosSegment3R = this.getModel().getValue("aos_segment3_r", rowIndex);
            DynamicObject aosMktFunctreq = QueryServiceHelper.queryOne("aos_mkt_functreq", "id",
                new QFilter[] {new QFilter("aos_segment3", QCP.equals, aosSegment3R)});
            if (!Cux_Common_Utl.IsNull(aosMktFunctreq)) {
                Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_functreq", aosMktFunctreq.get("id"));
            } else {
                this.getView().showErrorNotification("功能图需求表信息不存在!");
            }
        }
    }

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        // 给工具栏加监听事件
        this.addItemClickListeners("aos_toolbarap");
        // 提交
        this.addItemClickListeners("aos_submit");
        // 编辑退回
        this.addItemClickListeners("aos_back");
        EntryGrid entryGrid = this.getControl("aos_entryentity");
        entryGrid.addHyperClickListener(this);
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        Span span = MmsOtelUtils.getCusMainSpan(TRACER, MmsOtelUtils.getMethodPath());
        super.itemClick(evt);
        String control = evt.getItemKey();
        try (Scope ignore = span.makeCurrent()) {
            if (sign.submit.name.equals(control)) {
                DynamicObject dyMain = this.getModel().getDataEntity(true);
                aosSubmit(dyMain, "A");
            } else if (sign.back.name.equals(control)) {
                aosBack();
            } else if (sign.history.name.equals(control)) {
                // 查看历史记录
                aosHistory();
            } else if (sign.open.name.equals(control)) {
                aosOpen();
            } else if (sign.close.name.equals(control)) {
                // 手工关闭
                aosClose();
            }
        } catch (FndError fndMessage) {
            this.getView().showTipNotification(fndMessage.getErrorMessage());
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    private void aosClose() {
        ConfirmCallBackListener confirmCallBackListener = new ConfirmCallBackListener("bar_cancel", this);
        // 设置页面确认框，参数为：标题，选项框类型，回调监听
        this.getView().showConfirm("您确认关闭此申请单吗？", MessageBoxOptions.YesNo, confirmCallBackListener);
    }

    @Override
    public void confirmCallBack(MessageBoxClosedEvent event) {
        super.confirmCallBack(event);
        String callBackId = event.getCallBackId();
        if (sign.cancel.name.equals(callBackId)) {
            if (event.getResult().equals(MessageBoxResult.Yes)) {
                this.getModel().setValue("aos_user", SYSTEM);
                this.getModel().setValue("aos_status", "结束");
                this.getView().invokeOperation("save");
                this.getView().invokeOperation("refresh");
                FndHistory.Create(this.getView(), "手工关闭", "手工关闭");
                setErrorList(this.getModel().getDataEntity(true));
                statusControl();

                Object aosSourcetype = this.getModel().getValue("aos_sourcetype");
                if (HOT.equals(aosSourcetype)) {
                    Object aosSourceid = this.getModel().getValue("aos_sourceid");
                    DynamicObject hotDyn = BusinessDataServiceHelper.loadSingle(aosSourceid, "aos_mkt_hot_point");
                    hotDyn.set("aos_status", "结束");
                    SaveServiceHelper.save(new DynamicObject[] {hotDyn});
                }
            }
        }
    }

    /**
     * 打开来源流程
     **/
    private void aosOpen() {
        Object aosSourceid = this.getModel().getValue("aos_sourceid");
        Object aosSourcetype = this.getModel().getValue("aos_sourcetype");
        if (sign.listing.name.equals(aosSourcetype)) {
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_listing_req", aosSourceid);
        } else if (sign.design.name.equals(aosSourcetype)) {
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_designreq", aosSourceid);
        } else if (sign.ved.name.equals(aosSourcetype)) {
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_photoreq", aosSourceid);
        } else if (sign.cmp.name.equals(aosSourcetype)) {
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_designcmp", aosSourceid);
        }
    }

    /**
     * 打开历史记录
     **/
    private void aosHistory() throws FndError {
        Cux_Common_Utl.OpenHistory(this.getView());
    }

    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        try {
            String name = e.getProperty().getName();
            if (sign.caseCode.name.equals(name) || sign.caseCodeInput.name.equals(name)
                || sign.require.name.equals(name) || sign.requireInput.name.equals(name)) {
                syncInput(name);
            } else if (sign.type.name.equals(name)) {
                aosTypeChanged();
            } else if (sign.itemid.name.equals(name)) {
                int index = e.getChangeSet()[0].getRowIndex();
                itemChanged(index);
            }
        } catch (FndError fndMessage) {
            this.getView().showTipNotification(fndMessage.getErrorMessage());
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
        }
    }

    @Override
    public void afterImportData(ImportDataEventArgs e) {
        DynamicObject mainDyEntity = this.getModel().getDataEntity(true);
        SaveServiceHelper.save(new DynamicObject[] {mainDyEntity});
        try {
            DynamicObjectCollection dycSub = mainDyEntity.getDynamicObjectCollection("aos_entryentity");
            for (DynamicObject dyRow : dycSub) {
                entityRowSetValue(dyRow);
            }
            SaveServiceHelper.save(new DynamicObject[] {mainDyEntity});
        } catch (Exception e1) {
            StringWriter sw = new StringWriter();
            e1.printStackTrace(new PrintWriter(sw));
            this.getView().showMessage(e1.getMessage());
            e1.printStackTrace();
        }
    }

    /**
     * 物料值改变
     **/
    private void itemChanged(int row) {
        if (row >= 0) {
            DynamicObject dyRow =
                this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity").get(row);
            entityRowSetValue(dyRow);
            this.getView().updateView("aos_entryentity", row);
        }
    }

    private void syncInput(String name) {
        int aosEntryentity = this.getModel().getEntryCurrentRowIndex("aos_entryentity");
        if (sign.caseCode.name.equals(name)) {
            this.getModel().setValue("aos_caseinput", this.getModel().getValue("aos_case"), 0);
        } else if (sign.caseCodeInput.name.equals(name)) {
            this.getModel().setValue("aos_case", this.getModel().getValue("aos_caseinput"), aosEntryentity);
        } else if (sign.require.name.equals(name)) {
            this.getModel().setValue("aos_reqinput", this.getModel().getValue("aos_require"), 0);
        } else if (sign.requireInput.name.equals(name)) {
            this.getModel().setValue("aos_require", this.getModel().getValue("aos_reqinput"), aosEntryentity);
        }
    }

    /**
     * 任务类型值改变事件
     **/
    private void aosTypeChanged() {
        Object aosType = this.getModel().getValue("aos_type");
        Object aosDemandate;
        if (sign.same.name.equals(aosType)) {
            aosDemandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 1);
            this.getModel().setValue("aos_demandate", aosDemandate);
        } else if (sign.old.name.equals(aosType)) {
            aosDemandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 3);
            this.getModel().setValue("aos_demandate", aosDemandate);
        }
    }

    /**
     * 编辑退回
     **/
    private void aosBack() {
        String messageId;
        String message = "Listing优化需求表小语种-编辑退回";
        Object reqFid = this.getModel().getDataEntity().getPkValue();
        Object billno = this.getModel().getValue("billno");
        Object aosRequireby = this.getModel().getValue("aos_requireby");
        messageId = String.valueOf(aosRequireby);
        this.getModel().setValue("aos_status", "申请人");
        this.getModel().setValue("aos_user", aosRequireby);
        this.getView().invokeOperation("save");
        this.getView().invokeOperation("refresh");
        MktComUtil.sendGlobalMessage(messageId, "aos_mkt_listing_son", String.valueOf(reqFid), String.valueOf(billno),
            message);
    }

    /**
     * 初始化事件
     **/
    @Override
    public void afterLoadData(EventObject e) {
        super.afterLoadData(e);
        statusControl();
    }

    /**
     * 新建事件
     **/
    @Override
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        statusControl();// 界面控制
        // 带出人员组织
        Object currentUserId = UserServiceHelper.getCurrentUserId();
        List<DynamicObject> mapList = Cux_Common_Utl.GetUserOrg(currentUserId);
        if (mapList != null) {
            if (mapList.size() >= THREE && mapList.get(TWO) != null) {
                this.getModel().setValue("aos_organization1", mapList.get(2).get("id"));
            }
            if (mapList.size() >= FOUR && mapList.get(THREE) != null) {
                this.getModel().setValue("aos_organization2", mapList.get(3).get("id"));
            }
        }
    }

    /**
     * 界面关闭事件
     **/
    @Override
    public void beforeClosed(BeforeClosedEvent e) {
        super.beforeClosed(e);
        e.setCheckDataChange(false);
    }

    /**
     * 操作之前事件
     **/
    @Override
    public void beforeDoOperation(BeforeDoOperationEventArgs args) {
        FormOperate formOperate = (FormOperate)args.getSource();
        String operatation = formOperate.getOperateKey();
        try {
            if (sign.save.name.equals(operatation)) {
                DynamicObject dyMain = this.getModel().getDataEntity(true);
                saveControl(dyMain);
            }
        } catch (FndError fndMessage) {
            this.getView().showTipNotification(fndMessage.getErrorMessage());
            args.setCancel(true);
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
            args.setCancel(true);
        }
    }

    @Override
    public void afterDoOperation(AfterDoOperationEventArgs args) {
        FormOperate formOperate = (FormOperate)args.getSource();
        String operatation = formOperate.getOperateKey();
        try {
            if (sign.save.name.equals(operatation)) {
                statusControl();
            }
        } catch (FndError fndMessage) {
            this.getView().showTipNotification(fndMessage.getErrorMessage());
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
        }
    }

    /**
     * 提交
     **/
    public void aosSubmit(DynamicObject dyMain, String type) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            saveControl(dyMain);
            String aosStatus = dyMain.getString("aos_status");
            switch (aosStatus) {
                case "编辑确认":
                    submitForEditor(dyMain);
                    break;
                case "海外编辑确认":
                    submitForOsEditor(dyMain);
                    break;
                case "申请人":
                    submitForApply(dyMain);
                    break;
                case "海外编辑确认:功能图":
                    submitForOsFunc(dyMain);
                    break;
                case "小站海外编辑确认:功能图":
                    submitForOsSmall(dyMain);
                    break;
                default:
                    break;
            }
            SaveServiceHelper.save(new DynamicObject[] {dyMain});
            setErrorList(dyMain);
            // 插入历史记录
            FndHistory.Create(dyMain, "提交", aosStatus);
            if (sign.A.name.equals(type)) {
                this.getView().invokeOperation("refresh");
                statusControl();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 全局状态控制
     **/
    private void statusControl() {
        // 数据层
        Object aosStatus = this.getModel().getValue("aos_status");
        Object aosUser = this.getModel().getValue("aos_user");
        String aosUserId;
        if (aosUser instanceof String) {
            aosUserId = (String)aosUser;
        } else if (aosUser instanceof Long) {
            aosUserId = String.valueOf(aosUser);
        } else {
            aosUserId = ((DynamicObject)aosUser).getString("id");
        }
        Object currentUserId = UserServiceHelper.getCurrentUserId();
        Object reqFid = this.getModel().getDataEntity().getPkValue();
        FndMsg.debug("into min StatusControl");
        // 锁住需要控制的字段
        this.getView().setVisible(false, "aos_back");
        this.getView().setVisible(true, "bar_save");

        // 当前节点操作人不为当前用户 全锁
        if (!aosUserId.equals(currentUserId.toString())) {
            // 主界面面板
            this.getView().setEnable(false, "aos_contentpanelflex");
            // 保存
            this.getView().setEnable(false, "bar_save");
            // 提交
            this.getView().setEnable(false, "aos_submit");
            // 引入数据
            this.getView().setEnable(false, "aos_import");
        }
        // 状态控制
        Map<String, Object> map = new HashMap<>(16);
        if (sign.editorConfirm.name.equals(aosStatus)) {
            map.put(ClientProperties.Text, new LocaleString("编辑确认"));
            this.getView().updateControlMetadata("aos_submit", map);
            this.getView().setVisible(true, "aos_submit");
            // 主界面面板
            this.getView().setEnable(true, "aos_contentpanelflex");
            this.getView().setVisible(true, "aos_back");
        } else if (sign.apply.name.equals(aosStatus)) {
            map.put(ClientProperties.Text, new LocaleString("提交"));
            this.getView().updateControlMetadata("aos_submit", map);
            this.getView().setVisible(false, "aos_submit");
            // 主界面面板
            this.getView().setEnable(true, "aos_contentpanelflex");
            this.getView().setVisible(false, "aos_back");
            if (reqFid != null && !sign.zero.name.equals(reqFid.toString())) {
                this.getView().setVisible(true, "aos_submit");
            }
        } else if (sign.osCofirm.name.equals(aosStatus)) {
            map.put(ClientProperties.Text, new LocaleString("海外编辑确认"));
            this.getView().updateControlMetadata("aos_submit", map);
            this.getView().setVisible(true, "aos_submit");
            this.getView().setEnable(true, "aos_contentpanelflex");
            this.getView().setVisible(false, "aos_back");
        } else if (sign.osCofirmPic.name.equals(aosStatus)) {
            map.put(ClientProperties.Text, new LocaleString(FndMsg.get("MKT_MINOSCONFIRM")));
            this.getView().updateControlMetadata("aos_submit", map);
            this.getView().setVisible(true, "aos_submit");
            this.getView().setEnable(true, "aos_contentpanelflex");
            this.getView().setVisible(false, "aos_back");
        } else if (sign.endName.name.equals(aosStatus)) {
            this.getView().setVisible(false, "aos_submit");
            this.getView().setEnable(false, "aos_contentpanelflex");
            this.getView().setVisible(false, "bar_save");
            this.getView().setVisible(false, "aos_import");
            this.getView().setVisible(false, "aos_refresh");
            this.getView().setVisible(false, "aos_close");
        }
    }

    /**
     * 标识枚举类
     */
    private enum sign {
        /**
         * 结束
         */
        end("结束"),
        /**
         * 编辑确认
         */
        editorConfirm("编辑确认"),
        /**
         * A
         */
        A("A"),
        /**
         * 老品优化
         */
        old("老品优化"),
        /**
         * save
         */
        save("save"),
        /**
         * 结束
         */
        endName("结束"),
        /**
         * 四者一致
         */
        same("四者一致"),
        /**
         * aos_case
         */
        caseCode("aos_case"),
        /**
         * aos_caseinput
         */
        caseCodeInput("aos_caseinput"),
        /**
         * aos_require
         */
        require("aos_require"),
        /**
         * aos_reqinput
         */
        requireInput("aos_reqinput"),
        /**
         * aos_submit
         */
        submit("aos_submit"),
        /**
         * aos_close
         */
        close("aos_close"),
        /**
         * aos_history
         */
        history("aos_history"),
        /**
         * aos_open
         */
        open("aos_open"),
        /**
         * aos_segment3_r
         */
        segment3("aos_segment3_r"),
        /**
         * 0
         */
        zero("0"),
        /**
         * aos_type
         */
        type("aos_type"),
        /**
         * aos_itemid
         */
        itemid("aos_itemid"),
        /**
         * IT
         */
        it("IT"),
        /**
         * RO
         */
        ro("RO"),
        /**
         * DESIGN
         */
        design("DESIGN"),
        /**
         * CMP
         */
        cmp("CMP"),
        /**
         * aos_orgid
         */
        orgid("aos_orgid"),
        /**
         * aos_designer
         */
        designer("aos_designer"),
        /**
         * 海外编辑确认:功能图
         */
        osCofirmPic("海外编辑确认:功能图"),
        /**
         * aos_editor
         */
        editor("aos_editor"),
        /**
         * 申请人
         */
        apply("申请人"),
        /**
         * ES
         */
        es("ES"),
        /**
         * PT
         */
        pt("PT"),
        /**
         * 是
         */
        yes("是"),
        /**
         * 内容运营部
         */
        dept("文案部"),
        /**
         * LISTING
         */
        listing("LISTING"),
        /**
         * 海外编辑确认
         */
        osCofirm("海外编辑确认"),
        /**
         * bar_cancel
         */
        cancel("bar_cancel"),
        /**
         * aos_back
         */
        back("aos_back"),
        /**
         * VED
         */
        ved("VED"),
        /**
         * name
         */
        nameCode("name"),
        /**
         * 功能图翻译
         */
        translate("功能图翻译");

        /**
         * 名称
         */
        private final String name;

        /**
         * 构造方法
         *
         * @param name 名称
         */
        sign(String name) {
            this.name = name;
        }
    }
}
