package mkt.progress.listing;

import java.util.*;
import java.util.stream.Collectors;
import com.sun.istack.NotNull;
import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndHistory;
import common.sal.sys.basedata.dao.CountryDao;
import common.sal.sys.basedata.dao.impl.CountryDaoImpl;
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
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.ClientProperties;
import kd.bos.form.ConfirmCallBackListener;
import kd.bos.form.MessageBoxOptions;
import kd.bos.form.MessageBoxResult;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.events.BeforeClosedEvent;
import kd.bos.form.events.HyperLinkClickEvent;
import kd.bos.form.events.HyperLinkClickListener;
import kd.bos.form.events.MessageBoxClosedEvent;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.GlobalMessage;
import mkt.common.MKTCom;
import mkt.common.otel.MmsOtelUtils;
import mkt.progress.ProgressUtil;
import mkt.progress.parameter.errorListing.ErrorListEntity;

/**
 * @author aosom
 */
public class AosMktListingSonBill extends AbstractBillPlugIn implements ItemClickListener, HyperLinkClickListener {
    /**
     * 系统管理员
     **/
    public final static String SYSTEM = Cux_Common_Utl.SYSTEM;
    public final static int TWO = 2;
    public final static int THREE = 3;
    public final static int FOUR = 4;
    public final static int FIVE = 5;
    private static final Tracer TRACER = MmsOtelUtils.getTracer(AosMktListingSonBill.class, RequestContext.get());

    /**
     * 回写拍照需求表状态至 视频剪辑
     **/
    public static void updatePhotoToCut(Object aosSourceid) throws FndError {
        // 异常参数
        // 回写拍照需求表
        DynamicObject aosMktPhotoreq = BusinessDataServiceHelper.loadSingle(aosSourceid, "aos_mkt_photoreq");
        String aosStatus = aosMktPhotoreq.getString("aos_status");
        // 摄像师
        DynamicObject aosVedior = aosMktPhotoreq.getDynamicObject("aos_vedior");
        Object aosPhotoList = aosMktPhotoreq.get("aos_sourceid");
        aosMktPhotoreq.set("aos_status", "视频剪辑");
        aosMktPhotoreq.set("aos_user", aosVedior);
        // 回写拍照任务清单
        if (QueryServiceHelper.exists(sign.photoList.name, aosPhotoList)) {
            DynamicObject aosMktPhotolist = BusinessDataServiceHelper.loadSingle(aosPhotoList, "aos_mkt_photolist");
            aosMktPhotolist.set("aos_vedstatus", "视频剪辑");
            OperationServiceHelper.executeOperate("save", "aos_mkt_photolist", new DynamicObject[] {aosMktPhotolist},
                OperateOption.create());
        }
        FndHistory.Create(aosMktPhotoreq, "提交(文案回写)，下节点：视频剪辑", aosStatus);
        OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq",
            new DynamicObject[] {aosMktPhotoreq}, OperateOption.create());
        if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
            MKTCom.SendGlobalMessage(aosVedior.getPkValue().toString(), String.valueOf(aosMktPhotoreq),
                String.valueOf(operationrst.getSuccessPkIds().get(0)), aosMktPhotoreq.getString("billno"),
                "拍照需求表-视频剪辑");
        }
    }

    /**
     * 设置操作人的组织
     **/
    public static void setListSonUserOrganizate(DynamicObject dyMain) {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            // 设置操作人
            setUser(dyMain);
            // 添加数据到 改错任务清单
            setErrorList(dyMain);
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    private static void setUser(DynamicObject dyMain) {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            Object aosUser = dyMain.get("aos_user");
            Object userId;
            if (aosUser instanceof Long) {
                userId = aosUser;
            } else if (aosUser instanceof DynamicObject) {
                userId = ((DynamicObject)aosUser).get("id");
            } else if (aosUser instanceof String) {
                userId = aosUser;
            } else {
                return;
            }
            List<DynamicObject> mapList = Cux_Common_Utl.GetUserOrg(userId);
            if (mapList != null) {
                if (mapList.size() >= THREE && mapList.get(TWO) != null) {
                    dyMain.set("aos_userorganizat1", mapList.get(2).get("id"));
                }
                if (mapList.size() >= FOUR && mapList.get(THREE) != null) {
                    dyMain.set("aos_userorganizat2", mapList.get(3).get("id"));
                }
                if (mapList.size() >= FIVE && mapList.get(FOUR) != null) {
                    dyMain.set("aos_userorganizat3", mapList.get(4).get("id"));
                }
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    private static void setErrorList(DynamicObject dyMain) {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            String aosStatus = dyMain.getString("aos_status");
            if (!sign.end.name.equals(aosStatus)) {
                return;
            }
            String aosType = dyMain.getString("aos_type");
            if (!ErrorListEntity.errorListType.contains(aosType)) {
                return;
            }
            List<String> errorCountries = ErrorListEntity.errorCountries;
            DynamicObject dyOrg = dyMain.getDynamicObject("aos_orgid");
            String billno = dyMain.getString("billno");
            // 国别为空
            if (dyOrg == null) {
                DynamicObjectCollection dycEnt = dyMain.getDynamicObjectCollection("aos_entryentity");
                CountryDao countryDao = new CountryDaoImpl();
                for (DynamicObject dy : dycEnt) {
                    if (dy.get("aos_itemid") == null) {
                        continue;
                    }
                    String itemid = dy.getDynamicObject("aos_itemid").getString("id");
                    String orgtextR = dy.getString("aos_orgtext_r");
                    if (Cux_Common_Utl.IsNull(orgtextR)) {
                        continue;
                    }
                    String[] split = orgtextR.split(";");
                    for (String org : split) {
                        if (errorCountries.contains(org)) {
                            String orgid = countryDao.getCountryID(org);
                            ErrorListEntity errorListEntity = new ErrorListEntity(billno, aosType, orgid, itemid);
                            errorListEntity.save();
                        }
                    }
                }
            } else {
                String orgid = dyOrg.getString("id");
                String orgNumber = dyOrg.getString("number");
                if (errorCountries.contains(orgNumber)) {
                    DynamicObjectCollection dycEnt = dyMain.getDynamicObjectCollection("aos_entryentity");
                    for (DynamicObject dy : dycEnt) {
                        if (dy.get("aos_itemid") == null) {
                            continue;
                        }
                        String itemid = dy.getDynamicObject("aos_itemid").getString("id");
                        ErrorListEntity errorListEntity = new ErrorListEntity(billno, aosType, orgid, itemid);
                        errorListEntity.save();
                    }
                }
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    private static void submitToOsConfirm(DynamicObject dyMain) {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            long currentUserId = UserServiceHelper.getCurrentUserId();
            dyMain.set("aos_make", currentUserId);
            dyMain.set("aos_status", "海外确认");
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
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
                        DynamicObject dySonRow = mapItemToRow.get(itemid);
                        dyRow.set("aos_remakes", dySonRow.get("aos_remakes"));
                    }
                }
                SaveServiceHelper.update(new DynamicObject[] {dyDesign});
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 申请人提交
     **/
    private static void submitForApply(DynamicObject dyMain) {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            String messageId;
            String message = "Listing优化需求表子表-编辑确认";
            Object aosEditor = dyMain.get("aos_editor");
            // 当前界面主键
            Object reqFid = dyMain.getPkValue();
            Object billno = dyMain.get("billno");
            messageId = String.valueOf(aosEditor);
            // 设置单据流程状态
            dyMain.set("aos_status", "编辑确认");
            // 流转给编辑
            dyMain.set("aos_user", aosEditor);
            MKTCom.SendGlobalMessage(messageId, "aos_mkt_listing_son", String.valueOf(reqFid), String.valueOf(billno),
                message);
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
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
            if (Cux_Common_Utl.IsNull(dyMain.get(sign.osConfirm.name))) {
                fndError.add("海外文字确认必填!");
            }
            // 校验
            if (fndError.getCount() > 0) {
                throw fndError;
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
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
            Object aosDesigner = dyMain.get("aos_designer");
            Object aosOrignbill = dyMain.get("aos_orignbill");
            Object aosSourceid = dyMain.get("aos_sourceid");
            Object aosSourcetype = dyMain.get("aos_sourcetype");
            boolean aosIsmall = false;
            Object aosIsmalllov = dyMain.get("aos_ismalllov");
            Object aosOrgid = dyMain.get("aos_orgid");
            if (FndGlobal.IsNotNull(aosOrgid)) {
                aosOrgid = dyMain.getDynamicObject("aos_orgid");
            }
            // 任务类型
            Object aosType = dyMain.get("aos_type");
            if (sign.yes.name.equals(aosIsmalllov)) {
                aosIsmall = true;
            }
            // 需要生成小语种的行
            List<DynamicObject> listLanguageListing = new ArrayList<>();
            // 需要生成小语种的行
            List<DynamicObject> listLanguageListingCa = new ArrayList<>();
            // 校验
            DynamicObjectCollection aosEntryentityS = dyMain.getDynamicObjectCollection("aos_entryentity");
            Object lastItemId = 0;
            for (DynamicObject aosEntryentity : aosEntryentityS) {
                // 获取优化项 合计
                int aosWrite = aosEntryentity.getInt("aos_write");
                int aosOpt = aosEntryentity.getInt("aos_opt");
                int aosPic = aosEntryentity.getInt("aos_pic");
                int aosSubtitle = aosEntryentity.getInt("aos_subtitle");
                int aosTitle = aosEntryentity.getInt("aos_title");
                int aosKeyword = aosEntryentity.getInt("aos_keyword");
                int aosOther = aosEntryentity.getInt("aos_other");
                int aosEtc = aosEntryentity.getInt("aos_etc");
                int total = aosWrite + aosOpt + aosPic + aosSubtitle + aosTitle + aosKeyword + aosOther + aosEtc;
                if (total == 0) {
                    continue;// 优化项无数字 跳过
                }
                if ("0".equals(lastItemId.toString())) {
                    lastItemId = aosEntryentity.getDynamicObject("aos_itemid").getPkValue();
                }
                Object aosCase = aosEntryentity.get("aos_case");
                if (Cux_Common_Utl.IsNull(aosCase) && !"DESIGN".equals(aosSourcetype)) {
                    fndError.add("优化方案不允许为空!");
                }
                DynamicObject aosSubentryentity =
                    aosEntryentity.getDynamicObjectCollection("aos_subentryentity").get(0);
                String aosOrgtext = aosSubentryentity.getString("aos_orgtext");
                if (aosOrgtext.contains("ES") || aosOrgtext.contains("IT") || aosOrgtext.contains("FR")
                    || aosOrgtext.contains("DE")) {
                    listLanguageListing.add(aosEntryentity);
                }
                if (aosOrgtext.contains("CA")) {
                    listLanguageListingCa.add(aosEntryentity);
                }
            }
            if (Cux_Common_Utl.IsNull(aosDesigner)) {
                fndError.add("设计为空,流程无法流转!");
            }
            if (fndError.getCount() > 0) {
                throw fndError;
            }
            if (aosIsmall && listLanguageListing.size() > 0 && !sign.design.name.equals(aosSourcetype))
            // 是否抄送小语种 按照下单国别分组生成listing小语种
            {
                generateListingLanguage(dyMain, listLanguageListing);
            } else if (sign.ved.name.equals(aosSourcetype) && aosIsmall && listLanguageListing.size() == 0)
            // 视频类型 抄送小语种 但是物料中下单国别没有小语种 直接回写视频状态 至视频剪辑
            {
                updatePhotoToCut(aosSourceid);
            }
            if (sign.same.name.equals(aosType) && listLanguageListingCa.size() > 0) {
                generateListingLanguageCA(dyMain, listLanguageListingCa);
            }
            // 先执行保存操作
            dyMain.set("aos_make", RequestContext.get().getCurrUserId());
            dyMain.set("aos_status", "结束");
            dyMain.set("aos_user", SYSTEM);
            dyMain.set("aos_ecdate", new Date());
            if (FndGlobal.IsNotNull(aosOrgid) && sign.us.name.equals(((DynamicObject)aosOrgid).getString("number"))) {
                String category = MKTCom.getItemCateNameZH(lastItemId);
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
                DynamicObject aosMktProgorguser = QueryServiceHelper.queryOne("aos_mkt_progorguser", "aos_oseditor",
                    (new QFilter("aos_orgid", "=", ((DynamicObject)aosOrgid).getPkValue().toString())
                        .and("aos_category1", QCP.equals, aosCategory1).and("aos_category2", QCP.equals, aosCategory2))
                        .toArray());
                if (aosMktProgorguser != null) {
                    long aosOseditor = aosMktProgorguser.getLong("aos_oseditor");
                    dyMain.set("aos_sendto", aosOseditor);
                    MKTCom.SendGlobalMessage(String.valueOf(aosOseditor), "aos_mkt_listing_son",
                        dyMain.getPkValue().toString(), dyMain.getString("billno"), "Listing优化需求表-文案处理完成!");
                    GlobalMessage.SendMessage(dyMain.getString("billno") + "-Listing优化需求表-文案处理完成!",
                        String.valueOf(aosOseditor));
                }
            }
            SaveServiceHelper.save(new DynamicObject[] {dyMain});
            // 设计类型 判断本单下Listing需求子表是否全部已完成 非设计需求表过来的子表不触发此逻辑
            if (sign.design.name.equals(aosSourcetype)) {
                QFilter filterBill = new QFilter("aos_sourceid", "=", aosSourceid);
                QFilter filterStatus = new QFilter("aos_status", "!=", "结束");
                QFilter[] filters = new QFilter[] {filterBill, filterStatus};
                DynamicObject aosMktListingSon =
                    QueryServiceHelper.queryOne("aos_mkt_listing_son", "count(0)", filters);
                if (aosMktListingSon == null || aosMktListingSon.getInt(0) == 0) {
                    DynamicObject aosMktDesignreq =
                        BusinessDataServiceHelper.loadSingle(aosSourceid, "aos_mkt_designreq");
                    String aosStatus = aosMktDesignreq.getString("aos_status");
                    String messageId = ProgressUtil.getSubmitUser(aosSourceid, "aos_mkt_designreq", "设计");
                    if (Cux_Common_Utl.IsNull(messageId)) {
                        messageId = ((DynamicObject)aosDesigner).getPkValue().toString();
                    }
                    aosMktDesignreq.set("aos_user", messageId);
                    aosMktDesignreq.set("aos_status", "设计确认:翻译");
                    aosMktDesignreq.set("aos_receivedate", new Date());
                    mkt.progress.design.aos_mkt_designreq_bill.setEntityValue(aosMktDesignreq);
                    FndHistory.Create(aosMktDesignreq, "提交", aosStatus);
                    OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_designreq",
                        new DynamicObject[] {aosMktDesignreq}, OperateOption.create());
                    if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                        MKTCom.SendGlobalMessage(messageId, "aos_mkt_designreq", String.valueOf(aosSourceid),
                            String.valueOf(aosOrignbill), "设计确认:翻译");
                    }
                }
            }
            // 如果是Listing类型生成销售信息确认单
            if (sign.listing.name.equals(aosSourcetype)) {
                generateListingSal(dyMain);
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 生成销售信息确认单
     * 
     * @param dyMain 单据对象
     */
    private static void generateListingSal(DynamicObject dyMain) throws FndError {
        // 信息处理
        String messageId;
        String message;
        // 数据层
        Object aosDesigner = dyMain.getDynamicObject("aos_designer").getPkValue();
        Object billno = dyMain.get("billno");
        Object reqFid = dyMain.getPkValue();
        Object aosType = dyMain.get("aos_type");
        Object aosEditor = dyMain.get("aos_editor");
        Object aosOrgid = dyMain.get("aos_orgid");
        // 子表与小语种生成时 申请人为编辑
        Object aosMake = ((DynamicObject)aosEditor).getPkValue();
        if (dyMain.get(sign.make.name) != null) {
            aosMake = dyMain.get("aos_make");
        }
        DynamicObjectCollection aosEntryentityS = dyMain.getDynamicObjectCollection("aos_entryentity");
        Map<String, List<DynamicObject>> oumap = new HashMap<>(16);
        List<DynamicObject> mapList = new ArrayList<DynamicObject>();
        // 循环国别分组
        for (DynamicObject aosEntryentity : aosEntryentityS) {
            // 获取优化项 合计
            int aosWrite = aosEntryentity.getInt("aos_write");
            int aosOpt = aosEntryentity.getInt("aos_opt");
            int aosPic = aosEntryentity.getInt("aos_pic");
            int aosSubtitle = aosEntryentity.getInt("aos_subtitle");
            int aosTitle = aosEntryentity.getInt("aos_title");
            int aosKeyword = aosEntryentity.getInt("aos_keyword");
            int aosOther = aosEntryentity.getInt("aos_other");
            int aosEtc = aosEntryentity.getInt("aos_etc");
            int total = aosWrite + aosOpt + aosPic + aosSubtitle + aosTitle + aosKeyword + aosOther + aosEtc;
            if (total == 0) {
                continue;// 优化项无数字 跳过
            }
            DynamicObjectCollection aosSubentryentityS =
                aosEntryentity.getDynamicObjectCollection("aos_subentryentity");
            DynamicObject aosSubentryentity = aosSubentryentityS.get(0);
            String aosOrgtext = aosSubentryentity.getString("aos_orgtext");
            String[] aosOrgtextArray = aosOrgtext.split(";");
            for (String org : aosOrgtextArray) {
                if (!("US".equals(org) || "CA".equals(org) || "UK".equals(org))) {
                    continue;
                }
                if (aosOrgid != null && !(((DynamicObject)aosOrgid).getString("number")).equals(org)) {
                    continue;
                }
                mapList = oumap.get(org);
                if (mapList == null || mapList.size() == 0) {
                    mapList = new ArrayList<DynamicObject>();
                }
                mapList.add(aosEntryentity);
                oumap.put(org, mapList);
            }
        }
        // 循环每个分组后的国家 创建一个头
        for (String ou : oumap.keySet()) {
            Object orgId = FndGlobal.get_import_id(ou, "bd_country");
            DynamicObject aosMktListingSal = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_sal");
            aosMktListingSal.set("aos_requireby", UserServiceHelper.getCurrentUserId());
            aosMktListingSal.set("aos_designer", aosDesigner);
            aosMktListingSal.set("aos_status", "销售确认");
            aosMktListingSal.set("aos_orgid", orgId);
            aosMktListingSal.set("aos_orignbill", billno);
            aosMktListingSal.set("aos_sourceid", reqFid);
            aosMktListingSal.set("aos_type", aosType);
            aosMktListingSal.set("aos_requiredate", new Date());
            aosMktListingSal.set("aos_editor", aosEditor);
            aosMktListingSal.set("aos_sourcetype", "Listing优化需求表子表");
            // BOTP
            aosMktListingSal.set("aos_sourcebilltype", "aos_mkt_listing_son");
            aosMktListingSal.set("aos_sourcebillno", dyMain.get("billno"));
            aosMktListingSal.set("aos_srcentrykey", "aos_entryentity");
            DynamicObjectCollection cmpEntryentityS = aosMktListingSal.getDynamicObjectCollection("aos_entryentity");
            List<DynamicObject> entryList = oumap.get(ou);
            if (entryList.size() == 0) {
                continue;
            }
            Object lastItemId = 0;
            long aosSale = 0;
            long aosSale1 = 0;
            for (DynamicObject aosEntryentity : entryList) {
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
                if ("0".equals(lastItemId.toString())) {
                    lastItemId = aosEntryentity.getDynamicObject("aos_itemid").getPkValue();
                    String category = MKTCom.getItemCateNameZH(String.valueOf(lastItemId));
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
                    if (FndGlobal.IsNotNull(aosCategory1) && FndGlobal.IsNotNull(aosCategory2)) {
                        QFilter filterCategory1 = new QFilter("aos_category1", "=", aosCategory1);
                        QFilter filterCategory2 = new QFilter("aos_category2", "=", aosCategory2);
                        QFilter filterOu = new QFilter("aos_orgid", "=", orgId);
                        QFilter[] filtersCategory = new QFilter[] {filterCategory1, filterCategory2, filterOu};
                        String selectStr = "aos_salehelper aos_salehelper";
                        DynamicObject aosMktProgorguser =
                            QueryServiceHelper.queryOne("aos_mkt_progorguser", selectStr, filtersCategory);
                        if (aosMktProgorguser != null) {
                            aosSale = aosMktProgorguser.getLong("aos_salehelper");
                            aosSale1 = aosMktProgorguser.getLong("aos_salehelper");
                        }
                    }
                }
            }
            String sourceType = dyMain.getString("aos_sourcetype");
            if ("LISTING".equals(sourceType) && "老品优化".equals(aosType)) {
                try {
                    aosSale = Long.parseLong(dyMain.getDynamicObject("aos_requireby").getString("id"));
                } catch (Exception ex) {
                    aosSale = dyMain.getLong("aos_requireby");
                }
                // 判断申请人是否在表中存在
                DynamicObject orgUser = QueryServiceHelper.queryOne("aos_mkt_progorguser", "id",
                    new QFilter("aos_salehelper", QCP.equals, aosSale).and("aos_orgid", QCP.equals, orgId.toString())
                        .toArray());
                if (orgUser == null) {
                    aosSale = aosSale1;
                }
            }
            aosMktListingSal.set("aos_sale", aosSale);
            aosMktListingSal.set("aos_user", aosSale);
            messageId = String.valueOf(aosSale);
            message = "Listing优化销售确认单-Listing优化子表自动创建";
            OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_sal",
                new DynamicObject[] {aosMktListingSal}, OperateOption.create());
            // 修复关联关系
            try {
                ProgressUtil.botp("aos_mkt_listing_sal", aosMktListingSal.get("id"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                MKTCom.SendGlobalMessage(messageId, String.valueOf(aosMktListingSal),
                    String.valueOf(operationrst.getSuccessPkIds().get(0)), aosMktListingSal.getString("billno"),
                    message);
                FndHistory.Create(aosMktListingSal, aosMktListingSal.getString("aos_status"),
                    "Listing优化销售确认单-Listing优化子表自动创建");
            }
        }
    }

    private static void generateListingLanguageCA(DynamicObject dyMain, List<DynamicObject> listingLanguage)
        throws FndError {
        // 信息参数
        String messageId;
        String message;
        // 异常参数
        FndError fndError = new FndError();
        // 数据层
        DynamicObject aosDesigner = dyMain.getDynamicObject("aos_designer");
        Object aosDesignerId = aosDesigner.getPkValue();
        Object billno = dyMain.get("billno");
        Object reqFid = dyMain.getPkValue();
        Object aosType = dyMain.get("aos_type");
        Object aosSource = dyMain.get("aos_source");
        Object aosImportance = dyMain.get("aos_importance");
        Object aosRequireby = dyMain.get("aos_requireby");
        Object aosSourcetype = dyMain.get("aos_sourcetype");
        DynamicObject orgidDyn = dyMain.getDynamicObject("aos_orgid");
        Object orgId;
        if (!Cux_Common_Utl.IsNull(orgidDyn)) {
            orgId = orgidDyn.get("id");
        }
        Object aosOrgid = dyMain.get("aos_orgid");
        List<DynamicObject> mapList = Cux_Common_Utl.GetUserOrg(((DynamicObject)aosRequireby).getPkValue());
        Object lastItemId;
        boolean messageFlag;
        Object aos_demandate = new Date();
        if ("四者一致".equals(aosType)) {
            aos_demandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 1);
        } else if ("老品优化".equals(aosType)) {
            aos_demandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 3);
        }
        // 校验
        if (listingLanguage.size() == 0) {
            fndError.add("小语种功能图翻译行信息不存在!");
        }
        if (fndError.getCount() > 0) {
            throw fndError;
        }
        // 循环所有行
        for (int i = 0; i < listingLanguage.size(); i++) {
            DynamicObject aosSubentryentitySon =
                listingLanguage.get(i).getDynamicObjectCollection("aos_subentryentity").get(0);
            String aosOrgtext = aosSubentryentitySon.getString("aos_orgtext");
            String[] aosOrgtextArray = aosOrgtext.split(";");
            for (int o = 0; o < aosOrgtextArray.length; o++) {
                messageFlag = false;
                String org = aosOrgtextArray[o];
                if (!"CA".equals(org)) {
                    continue;
                }
                // 如果头上国别为空 则国别为法国
                orgId = FndGlobal.get_import_id("FR", "bd_country");
                QFilter filterOrg = new QFilter("aos_orgid.id", "=", orgId);
                QFilter filterSourceid = new QFilter("aos_sourceid", "=", reqFid);
                QFilter filterCafr = new QFilter("aos_cafr", "=", true);
                QFilter[] filters = new QFilter[] {filterOrg, filterSourceid, filterCafr};
                DynamicObject aosMktListingMin = QueryServiceHelper.queryOne("aos_mkt_listing_min", "id", filters);
                if (aosMktListingMin == null) {
                    aosMktListingMin = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_min");
                    aosMktListingMin.set("aos_requireby", aosRequireby);
                    aosMktListingMin.set("aos_requiredate", new Date());
                    aosMktListingMin.set("aos_type", aosType);
                    aosMktListingMin.set("aos_source", aosSource);
                    aosMktListingMin.set("aos_importance", aosImportance);
                    aosMktListingMin.set("aos_designer", aosDesignerId);
                    aosMktListingMin.set("aos_orignbill", billno);
                    aosMktListingMin.set("aos_sourceid", reqFid);
                    aosMktListingMin.set("aos_status", "编辑确认");
                    aosMktListingMin.set("aos_sourcetype", aosSourcetype);
                    aosMktListingMin.set("aos_orgid", orgId);
                    aosMktListingMin.set("aos_demandate", aos_demandate);
                    aosMktListingMin.set("aos_cafr", true);
                    // BOTP
                    aosMktListingMin.set("aos_sourcebilltype", "aos_mkt_listing_son");
                    aosMktListingMin.set("aos_sourcebillno", dyMain.get("billno"));
                    aosMktListingMin.set("aos_srcentrykey", "aos_entryentity");

                    if (mapList != null) {
                        if (mapList.get(2) != null) {
                            aosMktListingMin.set("aos_organization1", mapList.get(2).get("id"));
                        }
                        if (mapList.get(3) != null) {
                            aosMktListingMin.set("aos_organization2", mapList.get(3).get("id"));
                        }
                    }
                    messageFlag = true;
                } else {
                    long fid = aosMktListingMin.getLong("id");
                    aosMktListingMin = BusinessDataServiceHelper.loadSingle(fid, "aos_mkt_listing_min");
                }
                DynamicObjectCollection mktListingMinS = aosMktListingMin.getDynamicObjectCollection("aos_entryentity");
                DynamicObject aosEntryentity = listingLanguage.get(i);
                DynamicObject mktListingMin = mktListingMinS.addNew();
                DynamicObject subentryentity = aosEntryentity.getDynamicObjectCollection("aos_subentryentity").get(0);
                lastItemId = aosEntryentity.get("aos_itemid.id");
                mktListingMin.set("aos_itemid", aosEntryentity.get("aos_itemid"));
                mktListingMin.set("aos_is_saleout", ProgressUtil.Is_saleout(lastItemId));
                mktListingMin.set("aos_require", aosEntryentity.get("aos_require"));
                mktListingMin.set("aos_case", aosEntryentity.get("aos_case"));
                // 附件
                DynamicObjectCollection aosAttributefrom = aosEntryentity.getDynamicObjectCollection("aos_attribute");
                DynamicObjectCollection minEntityAttribute = mktListingMin.getDynamicObjectCollection("aos_attribute");
                DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
                for (DynamicObject attribute : aosAttributefrom) {
                    DynamicObject tempFile =
                        BusinessDataServiceHelper.loadSingle(attribute.getDynamicObject("fbasedataid").get("id"), type);
                    minEntityAttribute.addNew().set("fbasedataid", tempFile);
                }
                mktListingMin.set("aos_write", aosEntryentity.get("aos_write"));
                mktListingMin.set("aos_opt", aosEntryentity.get("aos_opt"));
                mktListingMin.set("aos_pic", aosEntryentity.get("aos_pic"));
                mktListingMin.set("aos_subtitle", aosEntryentity.get("aos_subtitle"));
                mktListingMin.set("aos_title", aosEntryentity.get("aos_title"));
                mktListingMin.set("aos_keyword", aosEntryentity.get("aos_keyword"));
                mktListingMin.set("aos_other", aosEntryentity.get("aos_other"));
                mktListingMin.set("aos_etc", aosEntryentity.get("aos_etc"));
                mktListingMin.set("aos_srcrowseq", aosEntryentity.get("SEQ"));

                DynamicObjectCollection aos_subentryentityS =
                    mktListingMin.getDynamicObjectCollection("aos_subentryentity");
                DynamicObject aos_subentryentity = aos_subentryentityS.addNew();
                aos_subentryentity.set("aos_segment3", subentryentity.get("aos_segment3"));
                aos_subentryentity.set("aos_broitem", subentryentity.get("aos_broitem"));
                aos_subentryentity.set("aos_itemname", subentryentity.get("aos_itemname"));
                aos_subentryentity.set("aos_orgtext", subentryentity.get("aos_orgtext"));
                aos_subentryentity.set("aos_reqinput", aosEntryentity.get("aos_require"));

                mktListingMin.set("aos_segment3_r", subentryentity.get("aos_segment3"));
                mktListingMin.set("aos_broitem_r", subentryentity.get("aos_broitem"));
                mktListingMin.set("aos_itemname_r", subentryentity.get("aos_itemname"));
                mktListingMin.set("aos_orgtext_r",
                    ProgressUtil.getOrderOrg(aosEntryentity.getDynamicObject("aos_itemid").getPkValue()));

                // 根据循环中最后一个物料去获取对应的 英语编辑师 小语种编辑师 LastItemId
                String category = MKTCom.getItemCateNameZH(lastItemId);
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
                long aosEditor = RequestContext.get().getCurrUserId();
                if (aosEditor == 0) {
                    throw new FndError(aosCategory1 + "," + aosCategory2 + "英语编辑不存在!");
                }
                long aosOueditor = 0;
                if (FndGlobal.IsNotNull(aosCategory1) && FndGlobal.IsNotNull(aosCategory2)) {
                    DynamicObject aosMktProgorguser =
                        ProgressUtil.minListtFindEditorByType(orgId, aosCategory1, aosCategory2, aosType.toString());
                    if (aosMktProgorguser != null) {
                        aosOueditor = aosMktProgorguser.getLong("aos_user");
                    }
                }
                if (aosOueditor == 0) {
                    throw new FndError(aosCategory1 + "," + aosCategory2 + "小语种编辑师不存在!");
                }
                // 英语编辑师
                mktListingMin.set("aos_editor", aosEditor);
                // 小语种编辑师
                mktListingMin.set("aos_editormin", aosOueditor);
                mktListingMin.set("aos_user", aosOueditor);
                messageId = String.valueOf(aosOueditor);
                message = "Listing优化需求表小语种-Listing优化需求子表自动创建";
                OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_min",
                    new DynamicObject[] {mktListingMin}, OperateOption.create());
                // 修复关联关系
                try {
                    ProgressUtil.botp("aos_mkt_listing_min", mktListingMin.get("id"));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                if (operationrst.getValidateResult().getValidateErrors().size() != 0 && messageFlag) {
                    MKTCom.SendGlobalMessage(messageId, "aos_mkt_listing_min",
                        String.valueOf(operationrst.getSuccessPkIds().get(0)), mktListingMin.getString("billno"),
                        message);
                    mktListingMin = BusinessDataServiceHelper.loadSingle(operationrst.getSuccessPkIds().get(0),
                        "aos_mkt_listing_min");
                    ProgressUtil.botp("aos_mkt_listing_min", mktListingMin.get("id"));
                    FndHistory.Create(mktListingMin, mktListingMin.getString("aos_status"),
                        "Listing优化需求表小语种-Listing优化需求文案自动创建");
                }
            }
        }
    }

    /**
     * 子表抄送小语种
     **/
    private static void generateListingLanguage(DynamicObject dyMain, List<DynamicObject> listingLanguage)
        throws FndError {
        // 信息参数
        String messageId;
        String message;
        // 异常参数
        FndError fndError = new FndError();
        // 数据层
        DynamicObject aosDesigner = dyMain.getDynamicObject("aos_designer");
        Object aosDesignerId = aosDesigner.getPkValue();
        Object billno = dyMain.get("billno");
        // 当前界面主键
        Object reqFid = dyMain.getPkValue();
        // 任务类型
        Object aosType = dyMain.get("aos_type");
        // 任务来源
        Object aosSource = dyMain.get("aos_source");
        // 紧急程度
        Object aosImportance = dyMain.get("aos_importance");
        // 设计需求表申请人
        Object aosRequireby = dyMain.get("aos_requireby");
        Object aosSourcetype = dyMain.get("aos_sourcetype");
        DynamicObject orgidDyn = dyMain.getDynamicObject("aos_orgid");
        Object orgId = null;
        if (!Cux_Common_Utl.IsNull(orgidDyn)) {
            orgId = orgidDyn.get("id");
        }
        Object aosOrgid = dyMain.get("aos_orgid");
        List<DynamicObject> mapList = Cux_Common_Utl.GetUserOrg(((DynamicObject)aosRequireby).getPkValue());
        Object lastItemId;
        boolean messageFlag;
        Object aosDemandate = new Date();
        if (sign.same.name.equals(aosType)) {
            aosDemandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 1);
        } else if (sign.old.name.equals(aosType)) {
            aosDemandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 3);
        }
        // 校验
        if (listingLanguage.size() == 0) {
            fndError.add("小语种功能图翻译行信息不存在!");
        }
        if (fndError.getCount() > 0) {
            throw fndError;
        }
        // 循环所有行
        for (int i = 0; i < listingLanguage.size(); i++) {
            DynamicObject aosSubentryentitySon =
                listingLanguage.get(i).getDynamicObjectCollection("aos_subentryentity").get(0);
            String aosOrgtext = aosSubentryentitySon.getString("aos_orgtext");
            String[] aosOrgtextArray = aosOrgtext.split(";");

            for (int o = 0; o < aosOrgtextArray.length; o++) {
                messageFlag = false;
                String org = aosOrgtextArray[o];
                if ("US".equals(org) || "CA".equals(org) || "UK".equals(org)) {
                    continue;
                }
                if (aosOrgid == null)
                // 如果头上国别为空 则国别为下单国别
                {
                    orgId = FndGlobal.get_import_id(org, "bd_country");
                } else if (!((DynamicObject)aosOrgid).getString("number").equals(org))
                // 如果头上国别不为空 则只生成该国别数据
                {
                    continue;
                }
                QFilter filterOrg = new QFilter("aos_orgid.id", "=", orgId);
                QFilter filterSourceid = new QFilter("aos_sourceid", "=", reqFid);
                QFilter[] filters = new QFilter[] {filterSourceid, filterOrg};
                DynamicObject aosMktListingMin = QueryServiceHelper.queryOne("aos_mkt_listing_min", "id", filters);

                if (aosMktListingMin == null) {
                    aosMktListingMin = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_min");
                    aosMktListingMin.set("aos_requireby", aosRequireby);
                    aosMktListingMin.set("aos_requiredate", new Date());
                    aosMktListingMin.set("aos_type", aosType);
                    aosMktListingMin.set("aos_source", aosSource);
                    aosMktListingMin.set("aos_importance", aosImportance);
                    aosMktListingMin.set("aos_designer", aosDesignerId);
                    aosMktListingMin.set("aos_orignbill", billno);
                    aosMktListingMin.set("aos_sourceid", reqFid);
                    aosMktListingMin.set("aos_status", "编辑确认");
                    aosMktListingMin.set("aos_sourcetype", aosSourcetype);
                    aosMktListingMin.set("aos_orgid", orgId);
                    aosMktListingMin.set("aos_demandate", aosDemandate);
                    // BOTP
                    aosMktListingMin.set("aos_sourcebilltype", "aos_mkt_listing_son");
                    aosMktListingMin.set("aos_sourcebillno", dyMain.get("billno"));
                    aosMktListingMin.set("aos_srcentrykey", "aos_entryentity");
                    if (mapList != null) {
                        if (mapList.get(2) != null) {
                            aosMktListingMin.set("aos_organization1", mapList.get(2).get("id"));
                        }
                        if (mapList.get(3) != null) {
                            aosMktListingMin.set("aos_organization2", mapList.get(3).get("id"));
                        }
                    }
                    messageFlag = true;
                } else {
                    long fid = aosMktListingMin.getLong("id");
                    aosMktListingMin = BusinessDataServiceHelper.loadSingle(fid, "aos_mkt_listing_min");
                }

                DynamicObjectCollection mktListingMinS = aosMktListingMin.getDynamicObjectCollection("aos_entryentity");
                DynamicObject aosEntryentity = listingLanguage.get(i);
                DynamicObject mktListingMin = mktListingMinS.addNew();
                DynamicObject subentryentity = aosEntryentity.getDynamicObjectCollection("aos_subentryentity").get(0);
                lastItemId = aosEntryentity.get("aos_itemid.id");
                mktListingMin.set("aos_itemid", aosEntryentity.get("aos_itemid"));
                mktListingMin.set("aos_is_saleout", ProgressUtil.Is_saleout(lastItemId));
                mktListingMin.set("aos_require", aosEntryentity.get("aos_require"));
                mktListingMin.set("aos_case", aosEntryentity.get("aos_case"));

                // 附件
                DynamicObjectCollection aosAttributefrom = aosEntryentity.getDynamicObjectCollection("aos_attribute");
                DynamicObjectCollection minEntityAttribute = mktListingMin.getDynamicObjectCollection("aos_attribute");
                DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
                for (DynamicObject attribute : aosAttributefrom) {
                    DynamicObject tempFile =
                        BusinessDataServiceHelper.loadSingle(attribute.getDynamicObject("fbasedataid").get("id"), type);
                    minEntityAttribute.addNew().set("fbasedataid", tempFile);
                }
                mktListingMin.set("aos_write", aosEntryentity.get("aos_write"));
                mktListingMin.set("aos_opt", aosEntryentity.get("aos_opt"));
                mktListingMin.set("aos_pic", aosEntryentity.get("aos_pic"));
                mktListingMin.set("aos_subtitle", aosEntryentity.get("aos_subtitle"));
                mktListingMin.set("aos_title", aosEntryentity.get("aos_title"));
                mktListingMin.set("aos_keyword", aosEntryentity.get("aos_keyword"));
                mktListingMin.set("aos_other", aosEntryentity.get("aos_other"));
                mktListingMin.set("aos_etc", aosEntryentity.get("aos_etc"));
                mktListingMin.set("aos_srcrowseq", aosEntryentity.get("SEQ"));
                DynamicObjectCollection aosSubentryentityS =
                    mktListingMin.getDynamicObjectCollection("aos_subentryentity");
                DynamicObject aosSubentryentity = aosSubentryentityS.addNew();
                aosSubentryentity.set("aos_segment3", subentryentity.get("aos_segment3"));
                aosSubentryentity.set("aos_broitem", subentryentity.get("aos_broitem"));
                aosSubentryentity.set("aos_itemname", subentryentity.get("aos_itemname"));
                aosSubentryentity.set("aos_orgtext", subentryentity.get("aos_orgtext"));
                aosSubentryentity.set("aos_reqinput", aosEntryentity.get("aos_require"));
                mktListingMin.set("aos_segment3_r", subentryentity.get("aos_segment3"));
                mktListingMin.set("aos_broitem_r", subentryentity.get("aos_broitem"));
                mktListingMin.set("aos_itemname_r", subentryentity.get("aos_itemname"));
                mktListingMin.set("aos_orgtext_r",
                    ProgressUtil.getOrderOrg(aosEntryentity.getDynamicObject("aos_itemid").getPkValue()));
                // 根据循环中最后一个物料去获取对应的 英语编辑师 小语种编辑师 LastItemId
                String category = MKTCom.getItemCateNameZH(lastItemId);
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
                long aosEditor = RequestContext.get().getCurrUserId();
                if (aosEditor == 0) {
                    throw new FndError(aosCategory1 + "," + aosCategory2 + "英语编辑不存在!");
                }
                long aosOueditor = 0;
                if (FndGlobal.IsNotNull(aosCategory1) && FndGlobal.IsNotNull(aosCategory2)) {
                    DynamicObject aosMktProgorguser =
                        ProgressUtil.minListtFindEditorByType(orgId, aosCategory1, aosCategory2, aosType.toString());
                    if (aosMktProgorguser != null) {
                        aosOueditor = aosMktProgorguser.getLong("aos_user");
                    }
                }
                if (aosOueditor == 0) {
                    throw new FndError(aosCategory1 + "," + aosCategory2 + "小语种编辑师不存在!");
                }
                // 英语编辑师
                mktListingMin.set("aos_editor", aosEditor);
                // 小语种编辑师
                mktListingMin.set("aos_editormin", aosOueditor);
                mktListingMin.set("aos_user", aosOueditor);
                messageId = String.valueOf(aosOueditor);
                message = "Listing优化需求表小语种-Listing优化需求子表自动创建";
                OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_min",
                    new DynamicObject[] {aosMktListingMin}, OperateOption.create());
                // 修复关联关系
                try {
                    ProgressUtil.botp("aos_mkt_listing_min", aosMktListingMin.get("id"));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                if (operationrst.getValidateResult().getValidateErrors().size() != 0 && messageFlag) {
                    MKTCom.SendGlobalMessage(messageId, "aos_mkt_listing_min",
                        String.valueOf(operationrst.getSuccessPkIds().get(0)), aosMktListingMin.getString("billno"),
                        message);
                    aosMktListingMin = BusinessDataServiceHelper.loadSingle(operationrst.getSuccessPkIds().get(0),
                        "aos_mkt_listing_min");
                    ProgressUtil.botp("aos_mkt_listing_min", aosMktListingMin.get("id"));
                    FndHistory.Create(aosMktListingMin, aosMktListingMin.getString("aos_status"),
                        "Listing优化需求表小语种-Listing优化需求文案自动创建");
                }
            }
        }
    }

    /**
     * 物料改变时，带出相关信息
     *
     * @param dyRow 对应的行数据
     * @param row 改变的行数
     */
    private static void ItemChanged(@NotNull DynamicObject dyRow, @NotNull int row) {
        if (dyRow == null || row < 0)
        {
            return;
        }
        // 清除子单据信息
        DynamicObjectCollection aos_subentryentityS = dyRow.getDynamicObjectCollection("aos_subentryentity");
        DynamicObject aos_subentryentity;
        if (aos_subentryentityS.size() == 0)
        {
            aos_subentryentity = aos_subentryentityS.addNew();
        }
        else
        {
            aos_subentryentity = aos_subentryentityS.get(0);
        }
        if (dyRow.get("aos_itemid") == null) {
            dyRow.set("aos_segment3_r", "");
            dyRow.set("aos_broitem_r", "");
            dyRow.set("aos_itemname_r", "");
            dyRow.set("aos_orgtext_r", "");
            aos_subentryentity.set("aos_segment3", "");
            aos_subentryentity.set("aos_broitem", "");
            aos_subentryentity.set("aos_itemname", "");
            aos_subentryentity.set("aos_orgtext", "");
        } else {
            Object itemid = dyRow.getDynamicObject("aos_itemid").get("id");
            // 查找物料信息
            DynamicObject bd_material = BusinessDataServiceHelper.loadSingle(itemid, "bd_material");
            String aos_productno = bd_material.getString("aos_productno");
            String aos_itemname = bd_material.getString("name");
            String item_number = bd_material.getString("number");
            String aos_orgtext = ProgressUtil.getOrderOrg(itemid);

            // 获取同产品号物料
            QFilter filter_productno = new QFilter("aos_productno", QCP.equals, aos_productno);
            QFilter[] filters = new QFilter[] {filter_productno};
            String SelectColumn = "number,aos_type";
            String aos_broitem = "";
            DynamicObjectCollection bd_materialS = QueryServiceHelper.query("bd_material", SelectColumn, filters);
            for (DynamicObject bd : bd_materialS) {
                if ("B".equals(bd.getString("aos_type")))
                    continue; // 配件不获取
                String number = bd.getString("number");
                if (item_number.equals(number))
                    continue;
                else
                    aos_broitem = aos_broitem + number + ";";
            }

            aos_subentryentity.set("aos_segment3", aos_productno);
            aos_subentryentity.set("aos_broitem", aos_broitem);
            aos_subentryentity.set("aos_itemname", aos_itemname);
            aos_subentryentity.set("aos_orgtext", aos_orgtext);
            dyRow.set("aos_segment3_r", aos_productno);
            dyRow.set("aos_broitem_r", aos_broitem);
            dyRow.set("aos_itemname_r", aos_itemname);
            dyRow.set("aos_orgtext_r", aos_orgtext);
        }
    }

    @Override
    public void hyperLinkClick(HyperLinkClickEvent hyperLinkClickEvent) {
        int RowIndex = hyperLinkClickEvent.getRowIndex();
        String FieldName = hyperLinkClickEvent.getFieldName();
        if ("aos_segment3_r".equals(FieldName)) {
            Object aos_segment3_r = this.getModel().getValue("aos_segment3_r", RowIndex);
            DynamicObject aos_mkt_functreq = QueryServiceHelper.queryOne("aos_mkt_functreq", "id",
                new QFilter[] {new QFilter("aos_segment3", QCP.equals, aos_segment3_r)});
            if (!Cux_Common_Utl.IsNull(aos_mkt_functreq))
                Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_functreq", aos_mkt_functreq.get("id"));
            else
                this.getView().showErrorNotification("功能图需求表信息不存在!");
        }
    }

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        this.addItemClickListeners("aos_toolbarap");// 给工具栏加监听事件
        this.addItemClickListeners("aos_submit"); // 提交
        this.addItemClickListeners("aos_back"); // 编辑退回
        this.addItemClickListeners("aos_open"); // 打开来源流程
        this.addItemClickListeners("aos_product");
        EntryGrid entryGrid = this.getControl("aos_entryentity");
        entryGrid.addHyperClickListener(this);
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        Span span = MmsOtelUtils.getCusMainSpan(TRACER, MmsOtelUtils.getMethodPath());
        super.itemClick(evt);
        String Control = evt.getItemKey();
        try (Scope scope = span.makeCurrent()) {
            if ("aos_submit".equals(Control)) {
                DynamicObject dy_main = this.getModel().getDataEntity(true);
                aos_submit(dy_main, "A");
            } else if ("aos_back".equals(Control))
                aos_back();
            else if ("aos_history".equals(Control))
                aos_history();// 查看历史记录
            else if ("aos_open".equals(Control))
                aos_open();
            else if ("aos_product".equals(Control))
                aos_product();
            else if ("aos_close".equals(Control))
                aos_close();
        } catch (FndError fndMessage) {
            this.getView().showTipNotification(fndMessage.getErrorMessage());
            MmsOtelUtils.setException(span, fndMessage);
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
            MmsOtelUtils.setException(span, ex);
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    private void aos_close() {
        String KEY_CANCEL = "bar_cancel";
        ConfirmCallBackListener confirmCallBackListener = new ConfirmCallBackListener(KEY_CANCEL, this);
        // 设置页面确认框，参数为：标题，选项框类型，回调监听
        this.getView().showConfirm("您确认关闭此申请单吗？", MessageBoxOptions.YesNo, confirmCallBackListener);
    }

    @Override
    public void confirmCallBack(MessageBoxClosedEvent event) {
        super.confirmCallBack(event);
        String callBackId = event.getCallBackId();
        if (callBackId.equals("bar_cancel")) {
            if (event.getResult().equals(MessageBoxResult.Yes)) {
                this.getModel().setValue("aos_user", SYSTEM);
                this.getModel().setValue("aos_status", "结束");
                this.getView().invokeOperation("save");
                this.getView().invokeOperation("refresh");
                FndHistory.Create(this.getView(), "手工关闭", "手工关闭");
                StatusControl();
                setErrorList(this.getModel().getDataEntity(true));
            }
        }
    }

    private void aos_product() {
        Object aos_productid = this.getModel().getValue("aos_productid");
        DynamicObject aos_prodatachan = QueryServiceHelper.queryOne("aos_prodatachan", "id",
            new QFilter[] {new QFilter("id", QCP.equals, aos_productid)});
        if (!Cux_Common_Utl.IsNull(aos_prodatachan))
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_prodatachan", aos_prodatachan.get("id"));
        else
            this.getView().showErrorNotification("产品资料变跟单信息不存在!");
    }

    /**
     * 值改变事件
     **/
    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();
        if (name.equals("aos_case") || name.equals("aos_caseinput"))
            SyncInput(name);
        else if (name.equals("aos_type")) {
            AosTypeChanged();
        } else if (name.equals("aos_itemid")) {
            int row = e.getChangeSet()[0].getRowIndex();
            if (row >= 0) {
                DynamicObject dy_row =
                    this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity").get(row);
                ItemChanged(dy_row, row);
                this.getView().updateView("aos_entryentity", row);
            }
        }
    }

    /**
     * 任务类型值改变事件
     **/
    private void AosTypeChanged() {
        Object aos_type = this.getModel().getValue("aos_type");
        Object aos_demandate = new Date();
        if ("四者一致".equals(aos_type)) {
            aos_demandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 1);
        } else if ("老品优化".equals(aos_type))
            aos_demandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 3);
        this.getModel().setValue("aos_demandate", aos_demandate);
    }

    private void SyncInput(String name) {
        int aos_entryentity = this.getModel().getEntryCurrentRowIndex("aos_entryentity");
        if (name.equals("aos_case"))
            this.getModel().setValue("aos_caseinput", this.getModel().getValue("aos_case"), 0);
        else if (name.equals("aos_caseinput"))
            this.getModel().setValue("aos_case", this.getModel().getValue("aos_caseinput"), aos_entryentity);
    }

    /**
     * 打开来源流程
     **/
    private void aos_open() {
        Object aos_sourceid = this.getModel().getValue("aos_sourceid");
        Object aos_sourcetype = this.getModel().getValue("aos_sourcetype");
        if ("LISTING".equals(aos_sourcetype))
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_listing_req", aos_sourceid);
        else if ("DESIGN".equals(aos_sourcetype))
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_designreq", aos_sourceid);
        else if ("VED".equals(aos_sourcetype))
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_photoreq", aos_sourceid);
        else if ("PRODUCT".equals(aos_sourcetype))
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_prodatachan", aos_sourceid);
    }

    /**
     * 初始化事件
     **/
    public void afterLoadData(EventObject e) {
        super.afterLoadData(e);
        StatusControl();
        // 获取当前操作人
        Object CurrentUserId = UserServiceHelper.getCurrentUserId();
        Object aos_sendto = this.getModel().getValue("aos_sendto");
        Object aos_sendate = this.getModel().getValue("aos_sendate");

        if (FndGlobal.IsNull(aos_sendate) && FndGlobal.IsNotNull(aos_sendto)
            && CurrentUserId.toString().equals(((DynamicObject)aos_sendto).getPkValue().toString())) {
            this.getModel().setValue("aos_sendate", new Date());
            this.getView().invokeOperation("save");
            this.getView().invokeOperation("refresh");
        }
    }

    /**
     * 新建事件
     **/
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        StatusControl();// 界面控制
    }

    /**
     * 界面关闭事件
     **/
    public void beforeClosed(BeforeClosedEvent e) {
        super.beforeClosed(e);
        e.setCheckDataChange(false);
    }

    /**
     * 提交
     **/
    public void aos_submit(DynamicObject dy_main, String type) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope scope = span.makeCurrent()) {
            saveControl(dy_main);// 先做数据校验判断是否可以提交
            String aos_status = dy_main.getString("aos_status");// 根据状态判断当前流程节点
            Object aos_osconfirmlov = dy_main.get("aos_osconfirmlov");
            switch (aos_status) {
                case "编辑确认":
                    if ("是".equals(aos_osconfirmlov))
                        submitToOsConfirm(dy_main);
                    else
                        submitForEditor(dy_main);
                    fillDesign(dy_main);
                    break;
                case "海外确认":
                    submitForEditor(dy_main);
                    break;
                case "申请人":
                    submitForApply(dy_main);
                    break;
                default:
                    break;
            }
            SaveServiceHelper.save(new DynamicObject[] {dy_main});
            setListSonUserOrganizate(dy_main);
            FndHistory.Create(dy_main, "提交", aos_status);
            if (type.equals("A")) {
                this.getView().updateView();
                StatusControl();// 提交完成后做新的界面状态控制
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 全局状态控制
     **/
    private void StatusControl() {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope scope = span.makeCurrent()) {
            // 数据层
            Object AosStatus = this.getModel().getValue("aos_status");
            Object AosUser = this.getModel().getValue("aos_user");
            String AosUserId = null;
            if (AosUser instanceof String)
                AosUserId = (String)AosUser;
            else if (AosUser instanceof Long)
                AosUserId = String.valueOf(AosUser);
            else
                AosUserId = ((DynamicObject)AosUser).getString("id");
            Object CurrentUserId = UserServiceHelper.getCurrentUserId();
            // Object CurrentUserName = UserServiceHelper.getUserInfoByID((long)
            // CurrentUserId).get("name");
            // 图片控制
            // InitPic();
            // 锁住需要控制的字段
            this.getView().setVisible(false, "aos_back");
            this.getView().setVisible(true, "bar_save");
            // 当前节点操作人不为当前用户 全锁
            if (!AosUserId.equals(CurrentUserId.toString())) {
                this.getView().setEnable(false, "aos_contentpanelflex");// 主界面面板
                this.getView().setEnable(false, "bar_save");
                this.getView().setEnable(false, "aos_submit");
                this.getView().setEnable(false, "aos_close");
            }
            // 状态控制
            Map<String, Object> map = new HashMap<>();
            if ("编辑确认".equals(AosStatus)) {
                map.put(ClientProperties.Text, new LocaleString("编辑确认"));
                this.getView().updateControlMetadata("aos_submit", map);
                this.getView().setVisible(true, "aos_submit");
                this.getView().setEnable(true, "aos_contentpanelflex");// 主界面面板
            } else if ("海外确认".equals(AosStatus)) {
                map.put(ClientProperties.Text, new LocaleString("海外确认"));
                this.getView().updateControlMetadata("aos_submit", map);
                this.getView().setVisible(true, "aos_submit");
                this.getView().setEnable(true, "aos_contentpanelflex");// 主界面面板
            } else if ("申请人".equals(AosStatus)) {
                map.put(ClientProperties.Text, new LocaleString("提交"));
                this.getView().updateControlMetadata("aos_submit", map);
                this.getView().setVisible(true, "aos_submit");
                this.getView().setEnable(true, "aos_contentpanelflex");// 主界面面板
            } else if ("结束".equals(AosStatus)) {
                this.getView().setVisible(false, "aos_submit");
                this.getView().setEnable(false, "aos_contentpanelflex");// 主界面面板
                this.getView().setVisible(false, "bar_save");
                this.getView().setVisible(false, "aos_close");
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 打开历史记录
     **/
    private void aos_history() throws FndError {
        Cux_Common_Utl.OpenHistory(this.getView());
    }

    /**
     * 编辑退回
     **/
    private void aos_back() {
        String MessageId = null;
        String Message = "Listing优化需求表子表-编辑退回";
        Object ReqFId = this.getModel().getDataEntity().getPkValue(); // 当前界面主键
        Object billno = this.getModel().getValue("billno");
        Object aos_requireby = this.getModel().getValue("aos_requireby");
        MessageId = String.valueOf(aos_requireby);
        this.getModel().setValue("aos_status", "申请人");// 设置单据流程状态
        this.getModel().setValue("aos_user", aos_requireby);// 流转给编辑
        setListSonUserOrganizate(this.getModel().getDataEntity(true));
        this.getView().invokeOperation("save");
        this.getView().invokeOperation("refresh");
        MKTCom.SendGlobalMessage(MessageId, "aos_mkt_listing_son", String.valueOf(ReqFId), String.valueOf(billno),
            Message);
        FndHistory.Create(this.getView(), "编辑退回", "编辑退回");
    }

    /**
     * 标识枚举类
     */
    private enum sign {
        /**
         * aos_mkt_photolist
         */
        photoList("aos_mkt_photolist"),
        /**
         * DESIGN
         */
        design("DESIGN"),
        /**
         * aos_make
         */
        make("aos_make"),
        /**
         * LISTING
         */
        listing("LISTING"),
        /**
         * 四者一致
         */
        same("四者一致"),
        /**
         * 老品优化
         */
        old("老品优化"),
        /**
         * VED
         */
        ved("VED"),
        /**
         * 是
         */
        yes("是"),
        /**
         * US
         */
        us("US"),
        /**
         * aos_osconfirmlov
         */
        osConfirm("aos_osconfirmlov"),
        /**
         * 结束
         */
        end("结束");

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
