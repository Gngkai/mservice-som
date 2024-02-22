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
import mkt.common.MktComUtil;
import mkt.common.otel.MmsOtelUtils;
import mkt.progress.ProgressUtil;
import mkt.progress.parameter.errorlisting.ErrorListEntity;

/**
 * @author aosom
 * @version Listing优化需求表文案-表单插件
 */
public class AosMktListingSonBill extends AbstractBillPlugIn implements ItemClickListener, HyperLinkClickListener {
    /**
     * 系统管理员
     **/
    public final static String SYSTEM = Cux_Common_Utl.SYSTEM;
    public final static int TWO = 2;
    public final static int THREE = 3;
    public final static int FOUR = 4;
    public final static String HOT = "HOT";
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
            MktComUtil.sendGlobalMessage(aosVedior.getPkValue().toString(), String.valueOf(aosMktPhotoreq),
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
            ex.printStackTrace();
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
            ex.printStackTrace();
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
            ex.printStackTrace();
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
            ex.printStackTrace();
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
            MktComUtil.sendGlobalMessage(messageId, "aos_mkt_listing_son", String.valueOf(reqFid),
                String.valueOf(billno), message);
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
            if (Cux_Common_Utl.IsNull(dyMain.get(sign.osConfirm.name))) {
                fndError.add("海外文字确认必填!");
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
                generateListingLanguageCa(dyMain, listLanguageListingCa);
            }
            // 先执行保存操作
            dyMain.set("aos_make", RequestContext.get().getCurrUserId());
            dyMain.set("aos_status", "结束");
            dyMain.set("aos_user", SYSTEM);
            dyMain.set("aos_ecdate", new Date());
            if (FndGlobal.IsNotNull(aosOrgid)
                && sign.us.name.equals(((DynamicObject)aosOrgid).getString(sign.number.name))) {
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
                DynamicObject aosMktProgorguser = QueryServiceHelper.queryOne("aos_mkt_progorguser", "aos_oseditor",
                    (new QFilter("aos_orgid", "=", ((DynamicObject)aosOrgid).getPkValue().toString())
                        .and("aos_category1", QCP.equals, aosCategory1).and("aos_category2", QCP.equals, aosCategory2))
                        .toArray());
                if (aosMktProgorguser != null) {
                    long aosOseditor = aosMktProgorguser.getLong("aos_oseditor");
                    dyMain.set("aos_sendto", aosOseditor);
                    MktComUtil.sendGlobalMessage(String.valueOf(aosOseditor), "aos_mkt_listing_son",
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
                    mkt.progress.design.AosMktDesignReqBill.setEntityValue(aosMktDesignreq);
                    FndHistory.Create(aosMktDesignreq, "提交", aosStatus);
                    OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_designreq",
                        new DynamicObject[] {aosMktDesignreq}, OperateOption.create());
                    if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                        MktComUtil.sendGlobalMessage(messageId, "aos_mkt_designreq", String.valueOf(aosSourceid),
                            String.valueOf(aosOrignbill), "设计确认:翻译");
                    }
                }
            }
            // 如果是Listing类型生成销售信息确认单
            if (sign.listing.name.equals(aosSourcetype)) {
                generateListingSal(dyMain);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
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
        DynamicObjectCollection aosEntryentityS = dyMain.getDynamicObjectCollection("aos_entryentity");
        Map<String, List<DynamicObject>> oumap = new HashMap<>(16);
        List<DynamicObject> mapList;
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
                    mapList = new ArrayList<>();
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
                    String category = MktComUtil.getItemCateNameZh(String.valueOf(lastItemId));
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
                MktComUtil.sendGlobalMessage(messageId, String.valueOf(aosMktListingSal),
                    String.valueOf(operationrst.getSuccessPkIds().get(0)), aosMktListingSal.getString("billno"),
                    message);
                FndHistory.Create(aosMktListingSal, aosMktListingSal.getString("aos_status"),
                    "Listing优化销售确认单-Listing优化子表自动创建");
            }
        }
    }

    private static void generateListingLanguageCa(DynamicObject dyMain, List<DynamicObject> listingLanguage)
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
        Object orgId = null;
        if (FndGlobal.IsNotNull(orgidDyn)) {
            orgId = orgidDyn.get("id");
        }
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
        for (DynamicObject dynamicObject : listingLanguage) {
            DynamicObject aosSubentryentitySon = dynamicObject.getDynamicObjectCollection("aos_subentryentity").get(0);
            String aosOrgtext = aosSubentryentitySon.getString("aos_orgtext");
            String[] aosOrgtextArray = aosOrgtext.split(";");
            for (String org : aosOrgtextArray) {
                messageFlag = false;
                if (!"CA".equals(org)) {
                    continue;
                }
                // 如果头上国别为空 则国别为法国
                if (FndGlobal.IsNull(orgId)) {
                    orgId = FndGlobal.get_import_id("FR", "bd_country");
                }
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
                    aosMktListingMin.set("aos_demandate", aosDemandate);
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
                DynamicObject mktListingMin = mktListingMinS.addNew();
                DynamicObject subentryentity = dynamicObject.getDynamicObjectCollection("aos_subentryentity").get(0);
                lastItemId = dynamicObject.get("aos_itemid.id");
                mktListingMin.set("aos_itemid", dynamicObject.get("aos_itemid"));
                mktListingMin.set("aos_is_saleout", ProgressUtil.Is_saleout(lastItemId));
                mktListingMin.set("aos_require", dynamicObject.get("aos_require"));
                mktListingMin.set("aos_case", dynamicObject.get("aos_case"));
                // 附件
                DynamicObjectCollection aosAttributefrom = dynamicObject.getDynamicObjectCollection("aos_attribute");
                DynamicObjectCollection minEntityAttribute = mktListingMin.getDynamicObjectCollection("aos_attribute");
                DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
                for (DynamicObject attribute : aosAttributefrom) {
                    DynamicObject tempFile =
                        BusinessDataServiceHelper.loadSingle(attribute.getDynamicObject("fbasedataid").get("id"), type);
                    minEntityAttribute.addNew().set("fbasedataid", tempFile);
                }
                mktListingMin.set("aos_write", dynamicObject.get("aos_write"));
                mktListingMin.set("aos_opt", dynamicObject.get("aos_opt"));
                mktListingMin.set("aos_pic", dynamicObject.get("aos_pic"));
                mktListingMin.set("aos_subtitle", dynamicObject.get("aos_subtitle"));
                mktListingMin.set("aos_title", dynamicObject.get("aos_title"));
                mktListingMin.set("aos_keyword", dynamicObject.get("aos_keyword"));
                mktListingMin.set("aos_other", dynamicObject.get("aos_other"));
                mktListingMin.set("aos_etc", dynamicObject.get("aos_etc"));
                mktListingMin.set("aos_srcrowseq", dynamicObject.get("SEQ"));

                DynamicObjectCollection aosSubentryentityS =
                    mktListingMin.getDynamicObjectCollection("aos_subentryentity");
                DynamicObject aosSubentryentity = aosSubentryentityS.addNew();
                aosSubentryentity.set("aos_segment3", subentryentity.get("aos_segment3"));
                aosSubentryentity.set("aos_broitem", subentryentity.get("aos_broitem"));
                aosSubentryentity.set("aos_itemname", subentryentity.get("aos_itemname"));
                aosSubentryentity.set("aos_orgtext", subentryentity.get("aos_orgtext"));
                aosSubentryentity.set("aos_reqinput", dynamicObject.get("aos_require"));
                mktListingMin.set("aos_segment3_r", subentryentity.get("aos_segment3"));
                mktListingMin.set("aos_broitem_r", subentryentity.get("aos_broitem"));
                mktListingMin.set("aos_itemname_r", subentryentity.get("aos_itemname"));
                mktListingMin.set("aos_orgtext_r",
                    ProgressUtil.getOrderOrg(dynamicObject.getDynamicObject("aos_itemid").getPkValue()));
                // 根据循环中最后一个物料去获取对应的 英语编辑师 小语种编辑师 LastItemId
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
                aosMktListingMin.set("aos_editor", aosEditor);
                // 小语种编辑师
                aosMktListingMin.set("aos_editormin", aosOueditor);
                aosMktListingMin.set("aos_user", aosOueditor);
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
                    MktComUtil.sendGlobalMessage(messageId, "aos_mkt_listing_min",
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
        for (DynamicObject dynamicObject : listingLanguage) {
            DynamicObject aosSubentryentitySon = dynamicObject.getDynamicObjectCollection("aos_subentryentity").get(0);
            String aosOrgtext = aosSubentryentitySon.getString("aos_orgtext");
            String[] aosOrgtextArray = aosOrgtext.split(";");

            for (String org : aosOrgtextArray) {
                messageFlag = false;
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
                DynamicObject mktListingMin = mktListingMinS.addNew();
                DynamicObject subentryentity = dynamicObject.getDynamicObjectCollection("aos_subentryentity").get(0);
                lastItemId = dynamicObject.get("aos_itemid.id");
                mktListingMin.set("aos_itemid", dynamicObject.get("aos_itemid"));
                mktListingMin.set("aos_is_saleout", ProgressUtil.Is_saleout(lastItemId));
                mktListingMin.set("aos_require", dynamicObject.get("aos_require"));
                mktListingMin.set("aos_case", dynamicObject.get("aos_case"));
                // 附件
                DynamicObjectCollection aosAttributefrom = dynamicObject.getDynamicObjectCollection("aos_attribute");
                DynamicObjectCollection minEntityAttribute = mktListingMin.getDynamicObjectCollection("aos_attribute");
                DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
                for (DynamicObject attribute : aosAttributefrom) {
                    DynamicObject tempFile =
                        BusinessDataServiceHelper.loadSingle(attribute.getDynamicObject("fbasedataid").get("id"), type);
                    minEntityAttribute.addNew().set("fbasedataid", tempFile);
                }
                mktListingMin.set("aos_write", dynamicObject.get("aos_write"));
                mktListingMin.set("aos_opt", dynamicObject.get("aos_opt"));
                mktListingMin.set("aos_pic", dynamicObject.get("aos_pic"));
                mktListingMin.set("aos_subtitle", dynamicObject.get("aos_subtitle"));
                mktListingMin.set("aos_title", dynamicObject.get("aos_title"));
                mktListingMin.set("aos_keyword", dynamicObject.get("aos_keyword"));
                mktListingMin.set("aos_other", dynamicObject.get("aos_other"));
                mktListingMin.set("aos_etc", dynamicObject.get("aos_etc"));
                mktListingMin.set("aos_srcrowseq", dynamicObject.get("SEQ"));
                DynamicObjectCollection aosSubentryentityS =
                    mktListingMin.getDynamicObjectCollection("aos_subentryentity");
                DynamicObject aosSubentryentity = aosSubentryentityS.addNew();
                aosSubentryentity.set("aos_segment3", subentryentity.get("aos_segment3"));
                aosSubentryentity.set("aos_broitem", subentryentity.get("aos_broitem"));
                aosSubentryentity.set("aos_itemname", subentryentity.get("aos_itemname"));
                aosSubentryentity.set("aos_orgtext", subentryentity.get("aos_orgtext"));
                aosSubentryentity.set("aos_reqinput", dynamicObject.get("aos_require"));
                mktListingMin.set("aos_segment3_r", subentryentity.get("aos_segment3"));
                mktListingMin.set("aos_broitem_r", subentryentity.get("aos_broitem"));
                mktListingMin.set("aos_itemname_r", subentryentity.get("aos_itemname"));
                mktListingMin.set("aos_orgtext_r",
                    ProgressUtil.getOrderOrg(dynamicObject.getDynamicObject("aos_itemid").getPkValue()));
                // 根据循环中最后一个物料去获取对应的 英语编辑师 小语种编辑师 LastItemId
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
                aosMktListingMin.set("aos_editor", aosEditor);
                // 小语种编辑师
                aosMktListingMin.set("aos_editormin", aosOueditor);
                aosMktListingMin.set("aos_user", aosOueditor);
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
                    MktComUtil.sendGlobalMessage(messageId, "aos_mkt_listing_min",
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
    private static void itemChanged(@NotNull DynamicObject dyRow, @NotNull int row) {
        if (dyRow == null || row < 0) {
            return;
        }
        // 清除子单据信息
        DynamicObjectCollection aosSubentryentityS = dyRow.getDynamicObjectCollection("aos_subentryentity");
        DynamicObject aosSubentryentity;
        if (aosSubentryentityS.size() == 0) {
            aosSubentryentity = aosSubentryentityS.addNew();
        } else {
            aosSubentryentity = aosSubentryentityS.get(0);
        }
        if (dyRow.get(sign.item.name) == null) {
            dyRow.set("aos_segment3_r", "");
            dyRow.set("aos_broitem_r", "");
            dyRow.set("aos_itemname_r", "");
            dyRow.set("aos_orgtext_r", "");
            aosSubentryentity.set("aos_segment3", "");
            aosSubentryentity.set("aos_broitem", "");
            aosSubentryentity.set("aos_itemname", "");
            aosSubentryentity.set("aos_orgtext", "");
        } else {
            Object itemid = dyRow.getDynamicObject("aos_itemid").get("id");
            // 查找物料信息
            DynamicObject bdMaterial = BusinessDataServiceHelper.loadSingle(itemid, "bd_material");
            String aosProductno = bdMaterial.getString("aos_productno");
            String aosItemname = bdMaterial.getString("name");
            String itemNumber = bdMaterial.getString("number");
            String aosOrgtext = ProgressUtil.getOrderOrg(itemid);
            // 获取同产品号物料
            QFilter filterProductno = new QFilter("aos_productno", QCP.equals, aosProductno);
            QFilter[] filters = new QFilter[] {filterProductno};
            String selectColumn = "number,aos_type";
            StringBuilder aosBroitem = new StringBuilder();
            DynamicObjectCollection bdMaterialS = QueryServiceHelper.query("bd_material", selectColumn, filters);
            for (DynamicObject bd : bdMaterialS) {
                if ("B".equals(bd.getString("aos_type"))) {
                    continue;
                }
                String number = bd.getString("number");
                if (!itemNumber.equals(number)) {
                    aosBroitem.append(number).append(";");
                }
            }
            aosSubentryentity.set("aos_segment3", aosProductno);
            aosSubentryentity.set("aos_broitem", aosBroitem.toString());
            aosSubentryentity.set("aos_itemname", aosItemname);
            aosSubentryentity.set("aos_orgtext", aosOrgtext);
            dyRow.set("aos_segment3_r", aosProductno);
            dyRow.set("aos_broitem_r", aosBroitem.toString());
            dyRow.set("aos_itemname_r", aosItemname);
            dyRow.set("aos_orgtext_r", aosOrgtext);
        }
    }

    /**
     * 超链接
     */
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
        // 打开来源流程
        this.addItemClickListeners("aos_open");
        this.addItemClickListeners("aos_product");
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
                aosSubmit(this.getModel().getDataEntity(true), "A");
            } else if (sign.back.name.equals(control)) {
                aosBack();
            } else if (sign.history.name.equals(control)) {
                // 查看历史记录
                aosHistory();
            } else if (sign.open.name.equals(control)) {
                aosOpen();
            } else if (sign.product.name.equals(control)) {
                aosProduct();
            } else if (sign.close.name.equals(control)) {
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

    /**
     * 关闭按钮
     */
    private void aosClose() {
        ConfirmCallBackListener confirmCallBackListener = new ConfirmCallBackListener(sign.cancel.name, this);
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
                statusControl();
                setErrorList(this.getModel().getDataEntity(true));
                Object aosSourcetype = this.getModel().getValue("aos_sourcetype");
                if (HOT.equals(aosSourcetype)) {
                    Object aosSourceid = this.getModel().getValue("aos_sourceid");
                    DynamicObject hotDyn = BusinessDataServiceHelper.loadSingle(aosSourceid, "aos_mkt_hot_point");
                    hotDyn.set("aos_status", "二次确认");
                    hotDyn.set("aos_user", hotDyn.get("aos_deal"));
                    SaveServiceHelper.save(new DynamicObject[] {hotDyn});
                }
            }
        }
    }

    private void aosProduct() {
        Object aosProductid = this.getModel().getValue("aos_productid");
        DynamicObject aosProdatachan = QueryServiceHelper.queryOne("aos_prodatachan", "id",
            new QFilter[] {new QFilter("id", QCP.equals, aosProductid)});
        if (!Cux_Common_Utl.IsNull(aosProdatachan)) {
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_prodatachan", aosProdatachan.get("id"));
        } else {
            this.getView().showErrorNotification("产品资料变跟单信息不存在!");
        }
    }

    /**
     * 值改变事件
     **/
    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();
        if (sign.caseB.name.equals(name) || sign.caseInput.name.equals(name)) {
            syncInput(name);
        } else if (sign.type.name.equals(name)) {
            aosTypeChanged();
        } else if (sign.item.name.equals(name)) {
            int row = e.getChangeSet()[0].getRowIndex();
            if (row >= 0) {
                DynamicObject dyRow =
                    this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity").get(row);
                itemChanged(dyRow, row);
                this.getView().updateView("aos_entryentity", row);
            }
        }
    }

    /**
     * 任务类型值改变事件
     **/
    private void aosTypeChanged() {
        Object aosType = this.getModel().getValue("aos_type");
        Object aosDemandate = new Date();
        if (sign.same.name.equals(aosType)) {
            aosDemandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 1);
        } else if (sign.old.name.equals(aosType)) {
            aosDemandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 3);
        }
        this.getModel().setValue("aos_demandate", aosDemandate);
    }

    private void syncInput(String name) {
        int aosEntryentity = this.getModel().getEntryCurrentRowIndex("aos_entryentity");
        if (sign.caseB.name.equals(name)) {
            this.getModel().setValue("aos_caseinput", this.getModel().getValue("aos_case"), 0);
        } else if (sign.caseInput.name.equals(name)) {
            this.getModel().setValue("aos_case", this.getModel().getValue("aos_caseinput"), aosEntryentity);
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
        } else if (sign.productU.name.equals(aosSourcetype)) {
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_prodatachan", aosSourceid);
        }
    }

    /**
     * 初始化事件
     **/
    @Override
    public void afterLoadData(EventObject e) {
        super.afterLoadData(e);
        statusControl();
        // 获取当前操作人
        Object currentUserId = UserServiceHelper.getCurrentUserId();
        Object aosSendto = this.getModel().getValue("aos_sendto");
        Object aosSendate = this.getModel().getValue("aos_sendate");
        if (FndGlobal.IsNull(aosSendate) && FndGlobal.IsNotNull(aosSendto)
            && currentUserId.toString().equals(((DynamicObject)aosSendto).getPkValue().toString())) {
            this.getModel().setValue("aos_sendate", new Date());
            this.getView().invokeOperation("save");
            this.getView().invokeOperation("refresh");
        }
    }

    /**
     * 新建事件
     **/
    @Override
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        statusControl();// 界面控制
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
     * 提交
     **/
    public void aosSubmit(DynamicObject dyMain, String type) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            // 先做数据校验判断是否可以提交
            saveControl(dyMain);
            // 根据状态判断当前流程节点
            String aosStatus = dyMain.getString("aos_status");
            Object aosOsconfirmlov = dyMain.get("aos_osconfirmlov");
            switch (aosStatus) {
                case "编辑确认":
                    if (sign.yes.name.equals(aosOsconfirmlov)) {
                        submitToOsConfirm(dyMain);
                    } else {
                        submitForEditor(dyMain);
                    }
                    fillDesign(dyMain);
                    break;
                case "海外确认":
                    submitForEditor(dyMain);
                    break;
                case "申请人":
                    submitForApply(dyMain);
                    break;
                default:
                    break;
            }
            SaveServiceHelper.save(new DynamicObject[] {dyMain});
            setListSonUserOrganizate(dyMain);
            FndHistory.Create(dyMain, "提交", aosStatus);
            if (sign.A.name.equals(type)) {
                this.getView().updateView();
                statusControl();// 提交完成后做新的界面状态控制
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
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
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
            // 锁住需要控制的字段
            this.getView().setVisible(false, "aos_back");
            this.getView().setVisible(true, "bar_save");
            // 当前节点操作人不为当前用户 全锁
            if (!aosUserId.equals(currentUserId.toString())) {
                this.getView().setEnable(false, "aos_contentpanelflex");
                this.getView().setEnable(false, "bar_save");
                this.getView().setEnable(false, "aos_submit");
                this.getView().setEnable(false, "aos_close");
            }
            Map<String, Object> map = new HashMap<>(16);
            if (sign.editorConfirm.name.equals(aosStatus)) {
                map.put(ClientProperties.Text, new LocaleString("编辑确认"));
                this.getView().updateControlMetadata("aos_submit", map);
                this.getView().setVisible(true, "aos_submit");
                this.getView().setEnable(true, "aos_contentpanelflex");
            } else if (sign.osConfirmN.name.equals(aosStatus)) {
                map.put(ClientProperties.Text, new LocaleString("海外确认"));
                this.getView().updateControlMetadata("aos_submit", map);
                this.getView().setVisible(true, "aos_submit");
                this.getView().setEnable(true, "aos_contentpanelflex");
            } else if (sign.apply.name.equals(aosStatus)) {
                map.put(ClientProperties.Text, new LocaleString("提交"));
                this.getView().updateControlMetadata("aos_submit", map);
                this.getView().setVisible(true, "aos_submit");
                this.getView().setEnable(true, "aos_contentpanelflex");
            } else if (sign.end.name.equals(aosStatus)) {
                this.getView().setVisible(false, "aos_submit");
                this.getView().setEnable(false, "aos_contentpanelflex");
                this.getView().setVisible(false, "bar_save");
                this.getView().setVisible(false, "aos_close");
            }

            Object aosSourcetype = this.getModel().getValue("aos_sourcetype");
            if (HOT.equals(aosSourcetype)) {
                this.getView().setVisible(false, "aos_submit");
                this.getView().setVisible(true, "aos_close");
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 打开历史记录
     **/
    private void aosHistory() throws FndError {
        Cux_Common_Utl.OpenHistory(this.getView());
    }

    /**
     * 编辑退回
     **/
    private void aosBack() {
        String messageId;
        String message = "Listing优化需求表子表-编辑退回";
        Object reqFid = this.getModel().getDataEntity().getPkValue();
        Object billno = this.getModel().getValue("billno");
        Object aosRequireby = this.getModel().getValue("aos_requireby");
        messageId = String.valueOf(aosRequireby);
        this.getModel().setValue("aos_status", "申请人");
        this.getModel().setValue("aos_user", aosRequireby);
        setListSonUserOrganizate(this.getModel().getDataEntity(true));
        this.getView().invokeOperation("save");
        this.getView().invokeOperation("refresh");
        MktComUtil.sendGlobalMessage(messageId, "aos_mkt_listing_son", String.valueOf(reqFid), String.valueOf(billno),
            message);
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
         * 编辑确认
         */
        editorConfirm("编辑确认"),
        /**
         * 海外确认
         */
        osConfirmN("海外确认"),
        /**
         * aos_submit
         */
        submit("aos_submit"),
        /**
         * aos_itemid
         */
        item("aos_itemid"),
        /**
         * 申请人
         */
        apply("申请人"),
        /**
         * aos_product
         */
        product("aos_product"),
        /**
         * aos_close
         */
        close("aos_close"),
        /**
         * 是
         */
        yes("是"),
        /**
         * aos_case
         */
        caseB("aos_case"),
        /**
         * aos_caseinput
         */
        caseInput("aos_caseinput"),
        /**
         * 行产品号
         */
        segment3("aos_segment3_r"),
        /**
         * PRODUCT
         */
        productU("PRODUCT"),
        /**
         * 取消工具栏
         */
        cancel("bar_cancel"),
        /**
         * aos_type
         */
        type("aos_type"),
        /**
         * aos_open
         */
        open("aos_open"),
        /**
         * number
         */
        number("number"),
        /**
         * aos_back
         */
        back("aos_back"),
        /**
         * A
         */
        A("A"),
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
         * aos_history
         */
        history("aos_history"),
        /**
         * VED
         */
        ved("VED"),
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
