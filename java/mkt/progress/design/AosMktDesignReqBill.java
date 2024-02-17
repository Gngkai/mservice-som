package mkt.progress.design;

import java.util.*;
import java.util.stream.Collectors;

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
import kd.bos.dataentity.serialization.SerializationUtils;
import kd.bos.entity.EntityMetadataCache;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.*;
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
import mkt.common.MKTS3PIC;
import mkt.common.otel.MmsOtelUtils;
import mkt.progress.ProgressUtil;
import mkt.progress.design.aadd.AosMktAaddBill;
import mkt.progress.design3d.DesignSkuList;
import mkt.progress.design3d.AosMkt3DesignBill;
import mkt.progress.iface.ItemInfoUtil;
import mkt.progress.iface.ParaInfoUtil;
import mkt.progress.listing.AosMktListingSonBill;
import mkt.progress.listing.hotpoint.AosMktListingHotUtil;
import mkt.progress.parameter.errorlisting.ErrorListEntity;

/**
 * @author aosom
 * @version 设计需求表-表单插件
 */
public class AosMktDesignReqBill extends AbstractBillPlugIn implements ItemClickListener, HyperLinkClickListener {
    /** 设计需求表标识 **/
    public final static String AOS_MKT_DESIGNREQ = "aos_mkt_designreq";
    public final static String AOS_TYPE = "aos_type";
    public final static String AOS_ORGID = "aos_orgid";
    public final static String AOS_STATUS = "aos_status";
    public final static String AOS_SOURCETYPE = "aos_sourcetype";
    public final static String AOS_SEGMENT3 = "aos_segment3";
    public final static String AOS_SUBMIT = "aos_submit";
    public final static String AOS_RETURN = "aos_return";
    public final static String AOS_OPEN = "aos_open";
    public final static String AOS_CLOSE = "aos_close";
    public final static String AOS_OPENORIGN = "aos_openorign";
    public final static String TRANSLATE = "功能图翻译";
    public final static String AOS_ITEMID = "aos_itemid";
    public final static String DMCONFIRM = "组长确认";
    public final static String AOS_HISTORY = "aos_history";
    public final static String AOS_QUERYSAMPLE = "aos_querysample";
    public final static String AOS_URL = "aos_url";
    /** 系统管理员 **/
    public final static String SYSTEM = Cux_Common_Utl.SYSTEM;
    public final static String APPLY = "申请人";
    public final static String DESIGN = "设计";
    public final static String APPLYCONFIRM = "申请人确认";
    public final static String DESCONFIM3D = "设计确认3D";
    public final static String END = "结束";
    public final static String NEWDESIGN = "新品设计";
    public final static String OLD = "老品优化";
    public final static String SAME = "四者一致";
    public final static String MODEL = "3D建模";
    public final static String DESTRANS = "设计确认:翻译";
    public final static String A = "A";
    public final static String EN = "EN";
    public final static String US = "US";
    public final static String CA = "CA";
    public final static String UK = "UK";
    public final static String RO = "RO";
    public final static String PT = "PT";
    public final static String LISTING = "LISTING";

    public final static String ORG = "DE/FR/IT/ES/PT/RO";
    public final static int TWO = 2;
    public final static int THREE = 3;
    public final static int FOUR = 4;
    public final static int FIVE = 5;
    public final static String PHOTO = "PHOTO";
    private static final Tracer TRACER = MmsOtelUtils.getTracer(AosMktDesignReqBill.class, RequestContext.get());
    /**
     * 3d建模页面缓存标识
     */
    private final static String KEY_CREATEDESIGN = "CreateDesign";

    /** 值校验 **/
    private static void saveControl(DynamicObject dyMain) throws FndError {
        FndError fndError = new FndError();
        // 数据层
        Object aosStatus = dyMain.get("aos_status");
        DynamicObject dyFirstRow = dyMain.getDynamicObjectCollection("aos_entryentity").get(0);
        Object aosItemid = dyFirstRow.get("aos_itemid");
        Object aosDesreq = dyFirstRow.get("aos_desreq");
        Object aosType = dyMain.get("aos_type");
        // 校验 物料信息 新建状态下必填
        if (APPLY.equals(aosStatus)) {
            if (Cux_Common_Utl.IsNull(aosItemid)) {
                fndError.add("物料信息必填!");
            }
            if (Cux_Common_Utl.IsNull(aosDesreq)) {
                fndError.add("设计要求必填!");
            }
            if (Cux_Common_Utl.IsNull(aosType)) {
                fndError.add("任务类型必填!");
            }
        }
        if (fndError.getCount() > 0) {
            throw fndError;
        }
    }

    /**
     * 目前给当前操作人赋值
     *
     * @param dyMain 设计需求表
     */
    public static void setEntityValue(DynamicObject dyMain) {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            setUser(dyMain);
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

    /** 设置改错任务清单 **/
    public static void setErrorList(DynamicObject dyMain) {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            String aosStatus = dyMain.getString("aos_status");
            if (!END.equals(aosStatus)) {
                return;
            }
            String aosType = dyMain.getString("aos_type");
            if (!ErrorListEntity.errorListType.contains(aosType)) {
                return;
            }
            String billno = dyMain.getString("billno");
            DynamicObject aosOrgid = dyMain.getDynamicObject("aos_orgid");
            if (aosOrgid == null) {
                CountryDao countryDao = new CountryDaoImpl();
                DynamicObjectCollection dycEnt = dyMain.getDynamicObjectCollection("aos_entryentity");
                for (DynamicObject dy : dycEnt) {
                    DynamicObject aosItemid = dy.getDynamicObject("aos_itemid");
                    if (aosItemid == null) {
                        continue;
                    }
                    DynamicObject dySub = dy.getDynamicObjectCollection("aos_subentryentity").get(0);
                    String orgtext = dySub.getString("aos_orgtext");
                    if (Cux_Common_Utl.IsNull(orgtext)) {
                        continue;
                    }
                    String[] split = orgtext.split(";");
                    for (String org : split) {
                        String countryId = countryDao.getCountryID(org);
                        ErrorListEntity errorListEntity =
                            new ErrorListEntity(billno, aosType, countryId, aosItemid.getString("id"));
                        errorListEntity.save();
                    }
                }
            } else {
                String orgid = aosOrgid.getString("id");
                DynamicObjectCollection dycEnt = dyMain.getDynamicObjectCollection("aos_entryentity");
                for (DynamicObject dy : dycEnt) {
                    DynamicObject aosItemid = dy.getDynamicObject("aos_itemid");
                    if (aosItemid == null) {
                        continue;
                    }
                    ErrorListEntity errorListEntity =
                        new ErrorListEntity(billno, aosType, orgid, aosItemid.getString("id"));
                    errorListEntity.save();
                }
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /** 申请人确认状态下提交 **/
    private static void submitForNew(DynamicObject dyMain) {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            // 异常参数
            FndError fndError = new FndError();
            // 数据层
            Object fid = dyMain.getPkValue();
            Object aosBillno = dyMain.get("billno");
            Object aosDesigner = dyMain.get("aos_designer");
            if (aosDesigner == null) {
                fndError.add("设计为空,流程无法流转!");
            }
            if (fndError.getCount() > 0) {
                throw fndError;
            }
            // 执行保存操作
            dyMain.set("aos_status", "设计");
            // 设计接收日期
            dyMain.set("aos_receivedate", new Date());
            dyMain.set("aos_user", aosDesigner);
            String messageId = null;
            if (aosDesigner != null) {
                messageId = ((DynamicObject)aosDesigner).getPkValue().toString();
            }
            // 发送消息
            MKTCom.SendGlobalMessage(messageId, AOS_MKT_DESIGNREQ, String.valueOf(fid), String.valueOf(aosBillno),
                "设计需求表-设计节点");
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /** 设计状态下提交 **/
    private static void submitForDesign(DynamicObject dyMain) {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            FndError fndError = new FndError();
            // 信息参数
            String messageId = null;
            String message;
            // 数据层
            Object fid = dyMain.getPkValue();
            Object aosBillno = dyMain.get("billno");
            Object aosRequireby = dyMain.get("aos_requireby");
            Object aos3der = dyMain.get("aos_3der");
            Object aosDm = dyMain.get("aos_dm");
            String aosType = dyMain.getString("aos_type");
            // 判断行上是否存在3D建模 判断优化项是否存在数字
            boolean flag3D = false;
            String aosLanguageH = "";
            int total = 0;
            DynamicObjectCollection aosEntryentityS = dyMain.getDynamicObjectCollection("aos_entryentity");
            List<DynamicObject> list3D = new ArrayList<>();
            List<DynamicObject> listListing = new ArrayList<>();
            List<DynamicObject> listLanguageListing = new ArrayList<>();
            for (DynamicObject aosEntryentity : aosEntryentityS) {
                boolean aos3d = aosEntryentity.getBoolean("aos_3d");
                String aosLanguage = aosEntryentity.getString("aos_language");
                int aosWhited = aosEntryentity.getInt("aos_whited");
                int aosWhites = aosEntryentity.getInt("aos_whites");
                int aosBackround = aosEntryentity.getInt("aos_backround");
                int aosFuncpic = aosEntryentity.getInt("aos_funcpic");
                int aosFunclike = aosEntryentity.getInt("aos_funclike");
                int aosSizepic = aosEntryentity.getInt("aos_sizepic");
                int aosAnew = aosEntryentity.getInt("aos_anew");
                int aosAlike = aosEntryentity.getInt("aos_alike");
                int aosTrans = aosEntryentity.getInt("aos_trans");
                int aosDespic = aosEntryentity.getInt("aos_despic");
                int aosPs = aosEntryentity.getInt("aos_ps");
                int aosFix = aosEntryentity.getInt("aos_fix");
                int aosWhitepro = aosEntryentity.getInt("aos_whitepro");
                int aosProground = aosEntryentity.getInt("aos_proground");
                int aosDetailpic = aosEntryentity.getInt("aos_detailpic");
                // 优化项内数字
                int linetotal = aosWhited + aosWhites + aosBackround + aosFuncpic + aosFunclike + aosSizepic + aosAnew
                    + aosAlike + aosTrans + aosDespic + aosPs + aosFix + aosWhitepro + aosProground + aosDetailpic;
                total += linetotal;
                // 存在一个则确认为3D建模
                if (aos3d) {
                    flag3D = true;
                    list3D.add(aosEntryentity);
                }
                // 功能图翻译语种
                if (FndGlobal.IsNotNull(aosLanguage)) {
                    if ("EN".equals(aosLanguage) || "US".equals(aosLanguage) || "CA".equals(aosLanguage)
                        || "UK".equals(aosLanguage)) {
                        listListing.add(aosEntryentity);
                    } else if ("DE/FR/IT/ES/PT/RO".contains(aosLanguage)) {
                        listLanguageListing.add(aosEntryentity);
                    }
                    if (FndGlobal.IsNull(aosLanguageH)) {
                        aosLanguageH = aosLanguage;
                    } else if (!aosLanguageH.equals(aosLanguage)) {
                        fndError.add("同单非空功能图翻译语种翻译必须相同!");
                    }
                }
            }
            if (flag3D && aos3der == null) {
                fndError.add("3D设计师为空,流程无法流转!");
            } else if (!flag3D && aosRequireby == null && !NEWDESIGN.equals(aosType)) {
                fndError.add("申请人为空,流程无法流转!");
            } else if (!flag3D && aosDm == null && NEWDESIGN.equals(aosType)) {
                fndError.add("设计组长为空,流程无法流转!");
            }
            if (fndError.getCount() > 0) {
                throw fndError;
            }
            dyMain.set("aos_designby", RequestContext.get().getCurrUserId());
            dyMain.set("aos_design_date", new Date());

            boolean cond1 =
                ((!flag3D) && (FndGlobal.IsNull(aosLanguageH)) && ("翻译".equals(aosType) || "四者一致".equals(aosType)));
            boolean cond2 = ((!flag3D) && (FndGlobal.IsNull(aosLanguageH)) && total > 0);
            // 设置单据流程状态
            if (flag3D) {
                // 1.是否3D建模=是，走3D建模节点
                dyMain.set("aos_status", "3D建模");
                dyMain.set("aos_user", aos3der);
                if (aos3der != null) {
                    messageId = ((DynamicObject)aos3der).getPkValue().toString();
                }
                message = "设计需求表-3D建模";
                // 同时创建3D产品设计单
                AosMkt3DesignBill.generate3Design(list3D, dyMain);
            } else if (cond1) {
                // 2.是否3D建模=否，功能图翻译语种=空，且任务类型=翻译或者四者一致，流程到结束节点 并生成Listing优化销售确认单
                generateListingSal(dyMain, "A");
                dyMain.set("aos_status", "结束");
                dyMain.set("aos_user", SYSTEM);
                AosMktListingHotUtil.createHotFromDesign(dyMain);
                if (aosRequireby != null) {
                    messageId = ((DynamicObject)aosRequireby).getPkValue().toString();
                }
                message = "设计需求表-自动结束!";
            } else if (cond2) {
                // 3.优化项内有数字，是否3D建模=否，功能图翻译语种=空，到申请人确认节点
                if (NEWDESIGN.equals(aosType)) {
                    dyMain.set("aos_status", "组长确认");
                    dyMain.set("aos_user", aosDm);
                    if (aosDm != null) {
                        messageId = ((DynamicObject)aosDm).getPkValue().toString();
                    }
                    message = "设计需求表-组长确认!";
                } else if (OLD.equals(aosType)) {
                    // 老品优化
                    dyMain.set("aos_laststatus", dyMain.get("aos_status"));
                    dyMain.set("aos_lastuser", dyMain.get("aos_user"));
                    dyMain.set("aos_status", "申请人确认");
                    dyMain.set("aos_user", aosRequireby);
                    if (aosRequireby != null) {
                        messageId = ((DynamicObject)aosRequireby).getPkValue().toString();
                    }
                    message = "设计需求表-申请人确认!";
                } else {
                    // 其他结束
                    dyMain.set("aos_status", "结束");
                    dyMain.set("aos_user", SYSTEM);
                    AosMktListingHotUtil.createHotFromDesign(dyMain);
                    if (aosRequireby != null) {
                        messageId = ((DynamicObject)aosRequireby).getPkValue().toString();
                    }
                    message = "设计需求表-结束";
                }
            } else if ((EN.equals(aosLanguageH) || US.equals(aosLanguageH) || CA.equals(aosLanguageH)
                || UK.equals(aosLanguageH))) {
                // 4.功能图翻译语种=EN，走功能图翻译节点
                // 同时创建 Listing优化需求表子表
                long aosEditor = generateListing(dyMain, listListing);
                dyMain.set("aos_status", "功能图翻译");
                dyMain.set("aos_user", aosEditor);
                messageId = String.valueOf(aosEditor);
                message = "设计需求表-功能图翻译";
            } else if (ORG.contains(aosLanguageH) && total > 0) {
                // 同时触发生成listing优化需求表-小语种
                generateListingLanguage(dyMain, listLanguageListing);
                // 5.功能图翻译语种=DE/FR/IT/ES时，优化项内有数字的到申请人确认节点
                if (NEWDESIGN.equals(aosType)) {
                    dyMain.set("aos_status", "组长确认");
                    dyMain.set("aos_user", aosDm);
                    if (aosDm != null) {
                        messageId = ((DynamicObject)aosDm).getPkValue().toString();
                    }
                    message = "设计需求表-组长确认";
                } else if (OLD.equals(aosType)) {
                    dyMain.set("aos_laststatus", dyMain.get("aos_status"));
                    dyMain.set("aos_lastuser", dyMain.get("aos_user"));
                    dyMain.set("aos_status", "申请人确认");
                    dyMain.set("aos_user", aosRequireby);
                    if (aosRequireby != null) {
                        messageId = ((DynamicObject)aosRequireby).getPkValue().toString();
                    }
                    message = "设计需求表-申请人确认";
                } else {
                    dyMain.set("aos_status", "结束");
                    dyMain.set("aos_user", SYSTEM);
                    AosMktListingHotUtil.createHotFromDesign(dyMain);
                    if (aosRequireby != null) {
                        messageId = ((DynamicObject)aosRequireby).getPkValue().toString();
                    }
                    message = "设计需求表-结束";
                }
            } else if (ORG.contains(aosLanguageH) && total == 0) {
                // 6.功能图翻译语种=DE/FR/IT/ES时，优化项内没有数字的流程到结束节点
                // 同时触发生成listing优化需求表-小语种
                generateListingLanguage(dyMain, listLanguageListing);
                dyMain.set("aos_status", "结束");
                dyMain.set("aos_user", SYSTEM);
                AosMktListingHotUtil.createHotFromDesign(dyMain);
                if (aosRequireby != null) {
                    messageId = ((DynamicObject)aosRequireby).getPkValue().toString();
                }
                message = "设计需求表-自动结束!";
            } else {
                if (NEWDESIGN.equals(aosType)) {
                    dyMain.set("aos_status", "组长确认");
                    dyMain.set("aos_user", aosDm);
                    if (aosDm != null) {
                        messageId = ((DynamicObject)aosDm).getPkValue().toString();
                    }
                    message = "设计需求表-组长确认";
                } else if (OLD.equals(aosType)) {
                    dyMain.set("aos_laststatus", dyMain.get("aos_status"));
                    dyMain.set("aos_lastuser", dyMain.get("aos_user"));
                    dyMain.set("aos_status", "申请人确认");
                    dyMain.set("aos_user", aosRequireby);
                    if (aosRequireby != null) {
                        messageId = ((DynamicObject)aosRequireby).getPkValue().toString();
                    }
                    message = "设计需求表-申请人确认";
                } else {
                    dyMain.set("aos_status", "结束");
                    dyMain.set("aos_user", SYSTEM);
                    AosMktListingHotUtil.createHotFromDesign(dyMain);
                    if (aosRequireby != null) {
                        messageId = ((DynamicObject)aosRequireby).getPkValue().toString();
                    }
                    message = "设计需求表-结束";
                }
            }

            // 如果是拍照类型 回写
            String aosSourcetype = dyMain.getString("aos_sourcetype");
            if (PHOTO.equals(aosSourcetype)) {
                String oriBillNo = dyMain.getString("aos_orignbill");
                DynamicObject photo = BusinessDataServiceHelper.loadSingle("aos_mkt_photoreq",
                    new QFilter("aos_type", QCP.equals, "视频").and("billno", QCP.equals, oriBillNo).toArray());
                if (FndGlobal.IsNotNull(photo)) {
                    photo.set("aos_user", photo.get("aos_vedior"));
                    SaveServiceHelper.save(new DynamicObject[] {photo});
                }
            }

            // 发送消息
            MKTCom.SendGlobalMessage(messageId, AOS_MKT_DESIGNREQ, String.valueOf(fid), String.valueOf(aosBillno),
                message);
            if (APPLYCONFIRM.equals(dyMain.getString(AOS_STATUS))) {
                GlobalMessage.SendMessage(aosBillno + "-设计需求单据待申请人确认", messageId);
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /** 3D建模状态下提交 **/
    private static void submitFor3D(DynamicObject dyMain) {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            FndError fndError = new FndError();
            // 信息参数
            String messageId = null;
            String message;
            // 数据层
            Object fid = dyMain.getPkValue();
            Object aosBillno = dyMain.get("billno");
            // 先取制作设计师若为空，则取设计师
            Object aosDesigner = dyMain.get("aos_designby");
            if (aosDesigner == null) {
                aosDesigner = dyMain.get("aos_designer");
            }
            if (aosDesigner == null) {
                fndError.add("设计为空,流程无法流转!");
            }
            if (fndError.getCount() > 0) {
                throw fndError;
            }
            // 设置单据流程状态
            dyMain.set("aos_status", "设计确认3D");
            // 设计接收日期
            dyMain.set("aos_receivedate", new Date());
            dyMain.set("aos_user", aosDesigner);
            if (aosDesigner != null) {
                messageId = ((DynamicObject)aosDesigner).getPkValue().toString();
            }
            message = "设计需求表-设计确认3D";
            // 发送消息
            MKTCom.SendGlobalMessage(messageId, AOS_MKT_DESIGNREQ, String.valueOf(fid), String.valueOf(aosBillno),
                message);
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /** 设计确认3D状态下提交 **/
    private static void submitForConfirm(DynamicObject dyMain) {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            FndError fndError = new FndError();
            // 信息参数
            String messageId = null;
            String message;
            // 数据层
            Object fid = dyMain.getPkValue();
            Object aosBillno = dyMain.get("billno");
            Object aosRequireby = dyMain.get("aos_requireby");
            Object aosDm = dyMain.get("aos_dm");
            String aosType = dyMain.getString("aos_type");
            if (!NEWDESIGN.equals(aosType) && aosRequireby == null) {
                fndError.add("申请人为空,流程无法流转!");
            }
            if (NEWDESIGN.equals(aosDm) && aosRequireby == null) {
                fndError.add("设计组长为空,流程无法流转!");
            }

            if (fndError.getCount() > 0) {
                throw fndError;
            }
            // 设置单据流程状态
            if (NEWDESIGN.equals(aosType)) {
                dyMain.set("aos_status", "组长确认");
                dyMain.set("aos_user", aosDm);
                if (aosDm instanceof DynamicObject) {
                    messageId = ((DynamicObject)aosDm).getPkValue().toString();
                }
                message = "设计需求表-组长确认";
            } else if (OLD.equals(aosType)) {
                dyMain.set("aos_laststatus", dyMain.get("aos_status"));
                dyMain.set("aos_lastuser", dyMain.get("aos_user"));
                dyMain.set("aos_status", "申请人确认");
                dyMain.set("aos_user", aosRequireby);
                if (aosRequireby != null) {
                    messageId = ((DynamicObject)aosRequireby).getPkValue().toString();
                }
                message = "设计需求表-申请人确认";
            } else {
                // 老品重拍回写拍照
                if (PHOTO.equals(dyMain.getString(AOS_SOURCETYPE))) {
                    DynamicObject aosMktPhotoreq = BusinessDataServiceHelper.loadSingleFromCache("aos_mkt_photoreq",
                        new QFilter("billno", QCP.equals, dyMain.getString("aos_sourcebillno"))
                            .and("aos_type", QCP.equals, "视频").toArray());
                    if (FndGlobal.IsNotNull(aosMktPhotoreq)) {
                        aosMktPhotoreq.set("aos_user", aosMktPhotoreq.get("aos_vedior"));
                        OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq",
                            new DynamicObject[] {aosMktPhotoreq}, OperateOption.create());
                    }
                }
                dyMain.set("aos_status", "结束");
                dyMain.set("aos_user", SYSTEM);
                AosMktListingHotUtil.createHotFromDesign(dyMain);
                if (aosRequireby != null) {
                    messageId = ((DynamicObject)aosRequireby).getPkValue().toString();
                }
                message = "设计需求表-结束";
            }
            // 发送消息
            dyMain.set("aos_designby", RequestContext.get().getCurrUserId());
            dyMain.set("aos_design_date", new Date());
            MKTCom.SendGlobalMessage(messageId, AOS_MKT_DESIGNREQ, String.valueOf(fid), String.valueOf(aosBillno),
                message);
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /** 设计确认翻译状态下提交 **/
    private static void submitForTrans(DynamicObject dyMain) {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            //
            FndError fndError = new FndError();
            // 信息参数
            String messageId = null;
            String message;
            // 数据层
            Object fid = dyMain.getPkValue();
            Object aosBillno = dyMain.get("billno");
            Object aosRequireby = dyMain.get("aos_requireby");
            Object aosDm = dyMain.get("aos_dm");
            String aosType = dyMain.getString("aos_type");
            if (!NEWDESIGN.equals(aosType) && aosRequireby == null) {
                fndError.add("申请人为空,流程无法流转!");
            }
            if (NEWDESIGN.equals(aosType) && aosDm == null) {
                fndError.add("设计组长为空,流程无法流转!");
            }
            if (fndError.getCount() > 0) {
                throw fndError;
            }
            // 设置单据流程状态
            if (NEWDESIGN.equals(aosType)) {
                dyMain.set("aos_status", "组长确认");
                dyMain.set("aos_user", aosDm);
                if (aosDm instanceof DynamicObject) {
                    messageId = ((DynamicObject)aosDm).getPkValue().toString();
                }
                message = "设计需求表-组长确认";
            } else if (OLD.equals(aosType)) {
                dyMain.set("aos_laststatus", dyMain.get("aos_status"));
                dyMain.set("aos_lastuser", dyMain.get("aos_user"));
                dyMain.set("aos_status", "申请人确认");
                dyMain.set("aos_user", aosRequireby);
                if (aosRequireby != null) {
                    messageId = ((DynamicObject)aosRequireby).getPkValue().toString();
                }
                message = "设计需求表-申请人确认";
            } else {
                dyMain.set("aos_status", "结束");
                dyMain.set("aos_user", SYSTEM);
                AosMktListingHotUtil.createHotFromDesign(dyMain);
                if (aosRequireby != null) {
                    messageId = ((DynamicObject)aosRequireby).getPkValue().toString();
                }
                message = "设计需求表-结束";
            }
            dyMain.set("aos_designby", RequestContext.get().getCurrUserId());
            dyMain.set("aos_design_date", new Date());
            // 发送消息
            MKTCom.SendGlobalMessage(messageId, AOS_MKT_DESIGNREQ, String.valueOf(fid), String.valueOf(aosBillno),
                message);
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /** 组长确认 **/
    private static void submitForConfirmDm(DynamicObject dyMain) {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            FndError fndError = new FndError();
            // 信息参数
            String messageId = null;
            String message;
            // 数据层
            Object fid = dyMain.getPkValue();
            Object aosBillno = dyMain.get("billno");
            Object aosRequireby = dyMain.get("aos_requireby");
            if (aosRequireby == null) {
                fndError.add("申请人为空,流程无法流转!");
            }
            if (fndError.getCount() > 0) {
                throw fndError;
            }
            // 设置单据流程状态
            dyMain.set("aos_laststatus", dyMain.get("aos_status"));
            dyMain.set("aos_lastuser", dyMain.get("aos_user"));
            dyMain.set("aos_status", "申请人确认");
            dyMain.set("aos_user", aosRequireby);
            if (aosRequireby != null) {
                messageId = ((DynamicObject)aosRequireby).getPkValue().toString();
            }
            message = "设计需求表-申请人确认";
            // 发送消息
            MKTCom.SendGlobalMessage(messageId, AOS_MKT_DESIGNREQ, String.valueOf(fid), String.valueOf(aosBillno),
                message);
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /** 申请人确认 **/
    private static void submitForConfirmReq(DynamicObject dyMain, String type) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            Object aosOrgid = dyMain.get("aos_orgid");
            Object aosSourcetype = dyMain.get("aos_sourcetype");
            Object aosType = dyMain.get("aos_type");
            // A+生成逻辑
            if (NEWDESIGN.equals(aosType)) {
                DynamicObjectCollection aosEntryentityS = dyMain.getDynamicObjectCollection("aos_entryentity");
                for (DynamicObject aosEntryentity : aosEntryentityS) {
                    DynamicObject aosItemid = aosEntryentity.getDynamicObject("aos_itemid");
                    String aosProductno = aosItemid.getString("aos_productno");
                    boolean exists1 = QueryServiceHelper.exists("aos_mkt_addtrack",
                        new QFilter("aos_itemid.aos_productno", QCP.equals, aosProductno));
                    boolean exists2 = QueryServiceHelper.exists("aos_mkt_addtrack",
                        new QFilter("aos_itemid", QCP.equals, aosItemid.getPkValue()));
                    // A+跟踪表中不存在物料 存在产品号
                    if (exists1 && !exists2) {
                        // 循环
                        DynamicObjectCollection aosSubentryentityS =
                            aosEntryentity.getDynamicObjectCollection("aos_subentryentity");
                        DynamicObject aosSubentryentity = aosSubentryentityS.get(0);
                        String aosOrgtext = aosSubentryentity.getString("aos_orgtext");
                        if (Cux_Common_Utl.IsNull(aosOrgtext)) {
                            continue;
                        }
                        String[] aosOrgtextArray = aosOrgtext.split(";");
                        for (String org : aosOrgtextArray) {
                            // 判断国别类型为英语还是小语种
                            if ("US,CA,UK".contains(org)) {
                                // 判断同产品号是否有英语国别制作完成
                                boolean exists3 =
                                    QueryServiceHelper
                                        .exists("aos_mkt_addtrack",
                                            new QFilter("aos_itemid.aos_productno", QCP.equals, aosProductno)
                                                .and(new QFilter("aos_us", QCP.equals, true)
                                                    .or("aos_ca", QCP.equals, true).or("aos_uk", QCP.equals, true))
                                                .toArray());
                                // 存在英语国别制作完成 到05节点 不存在 到04节点
                                if (exists3) {
                                    AosMktAaddBill.generateAddFromDesign(aosItemid, org, "EN_05");
                                } else {
                                    AosMktAaddBill.generateAddFromDesign(aosItemid, org, "EN_04");
                                }
                            } else if ("DE,FR,IT,ES".contains(org)) {
                                // 判断同产品号是否有小语种国别制作完成
                                boolean exists3 =
                                    QueryServiceHelper.exists("aos_mkt_addtrack",
                                        new QFilter("aos_itemid.aos_productno", QCP.equals, aosProductno)
                                            .and(new QFilter("aos_de", QCP.equals, true).or("aos_fr", QCP.equals, true)
                                                .or("aos_it", QCP.equals, true).or("aos_es", QCP.equals, true))
                                            .toArray());
                                // 存在小语种国别制作完成 到04节点 不存在 到02节点
                                if (exists3) {
                                    AosMktAaddBill.generateAddFromDesign(aosItemid, org, "SM_04");
                                } else {
                                    AosMktAaddBill.generateAddFromDesign(aosItemid, org, "SM_02");
                                }
                            }
                        }
                    }
                }
            }

            if (aosOrgid != null && LISTING.equals(aosSourcetype)) {
                // 1.触发生成设计需求表的listing优化需求表上有国别，设计需求表结束后只触发生成本国的设计完成表-国别;
                generateDesignHasOu(dyMain);
                // 优化确认单
                generateListingSal(dyMain, "B");
            } else if (aosOrgid == null && LISTING.equals(aosSourcetype)) {
                // 2.触发生成设计需求表的listing优化需求表上无国别，设计需求表结束后触发生成下单国别的设计完成表-国别;
                generateDesignNotHasOu(dyMain);
                // 优化确认单
                generateListingSal(dyMain, "C");
            }
            if (A.equals(type)) {
                dyMain.set("aos_submitter", "person");
            } else {
                dyMain.set("aos_submitter", "system");
            }
            // 老品重拍回写拍照
            if (PHOTO.equals(dyMain.getString(AOS_SOURCETYPE))) {
                DynamicObject aosMktPhotoreq = BusinessDataServiceHelper.loadSingleFromCache("aos_mkt_photoreq",
                    new QFilter("billno", QCP.equals, dyMain.getString("aos_sourcebillno"))
                        .and("aos_type", QCP.equals, "视频").toArray());
                if (FndGlobal.IsNotNull(aosMktPhotoreq)) {
                    aosMktPhotoreq.set("aos_user", aosMktPhotoreq.get("aos_vedior"));
                    OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq",
                        new DynamicObject[] {aosMktPhotoreq}, OperateOption.create());
                }
            }

            // 执行保存操作
            dyMain.set("aos_status", "结束");
            dyMain.set("aos_user", SYSTEM);
            AosMktListingHotUtil.createHotFromDesign(dyMain);
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 创建Listing优化需求表子表
     *
     **/
    private static long generateListing(DynamicObject dyMain, List<DynamicObject> listingEn) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            FndError fndError = new FndError();
            // 信息参数
            boolean messageFlag = false;
            String messageId;
            String message;
            // 数据层
            DynamicObject aosDesigner = dyMain.getDynamicObject("aos_designer");
            Object aosDesignerId = aosDesigner.getPkValue();
            Object billno = dyMain.get("billno");
            Object reqFid = dyMain.getPkValue();
            // 任务类型
            Object aosType = dyMain.get("aos_type");
            // 任务来源
            Object aosSource = dyMain.get("aos_source");
            // 紧急程度
            Object aosImportance = dyMain.get("aos_importance");
            // 设计需求表申请人
            Object aosRequireby = dyMain.get("aos_requireby");
            Object aosDemandate = new Date();
            Object aosIsmalllov;
            Object aosOrgid = dyMain.get("aos_orgid");
            Object aosOrgnumber = null;
            Object aosOsconfirmlov = "否";
            if (aosOrgid != null) {
                aosOrgnumber = ((DynamicObject)aosOrgid).get("number");
            }
            if (US.equals(aosOrgnumber) || CA.equals(aosOrgnumber) || UK.equals(aosOrgnumber)) {
                aosIsmalllov = "否";
            } else {
                aosIsmalllov = "是";
            }
            if (SAME.equals(aosType)) {
                aosDemandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 1);
                aosIsmalllov = "是";
            } else if (OLD.equals(aosType)) {
                aosDemandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 3);
            }

            List<DynamicObject> mapList = Cux_Common_Utl.GetUserOrg(((DynamicObject)aosRequireby).getPkValue());
            long aosEditor = 0;
            // 校验
            if (listingEn.size() == 0) {
                fndError.add("EN功能图翻译行信息不存在!");
            }
            if (fndError.getCount() > 0) {
                throw fndError;
            }
            // 循环创建
            for (DynamicObject dyn3dR : listingEn) {
                DynamicObject dyn3dD = dyn3dR.getDynamicObjectCollection("aos_subentryentity").get(0);
                // 头信息
                // 根据国别大类中类取对应营销US编辑
                Object itemId = dyn3dR.getDynamicObject("aos_itemid").getPkValue();
                String category = MKTCom.getItemCateNameZH(itemId);
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
                aosEditor = 0;
                if (FndGlobal.IsNotNull(aosCategory1) && FndGlobal.IsNotNull(aosCategory2)) {
                    DynamicObject aosMktProguser =
                        ProgressUtil.findEditorByType(aosCategory1, aosCategory2, String.valueOf(aosType));
                    if (aosMktProguser != null) {
                        aosEditor = aosMktProguser.getLong("aos_user");
                    }
                }
                if (aosEditor == 0) {
                    fndError.add(aosCategory1 + "," + aosCategory2 + "品类英语编辑不存在!");
                    throw fndError;
                }
                // 根据国别编辑合并单据
                DynamicObject aosMktListingSon;
                QFilter filterEditor = new QFilter("aos_editor", "=", aosEditor);
                QFilter filterSourceid = new QFilter("aos_sourceid", "=", reqFid);
                QFilter[] filters = new QFilter[] {filterEditor, filterSourceid};
                DynamicObject aosMktListingSonq = QueryServiceHelper.queryOne("aos_mkt_listing_son", "id", filters);
                if (aosMktListingSonq == null) {
                    aosMktListingSon = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_son");
                    messageFlag = true;
                    aosMktListingSon.set("aos_type", aosType);
                    aosMktListingSon.set("aos_source", aosSource);
                    aosMktListingSon.set("aos_importance", aosImportance);
                    aosMktListingSon.set("aos_designer", aosDesignerId);
                    aosMktListingSon.set("aos_editor", aosEditor);
                    aosMktListingSon.set("aos_user", aosEditor);
                    aosMktListingSon.set("aos_orignbill", billno);
                    aosMktListingSon.set("aos_sourceid", reqFid);
                    aosMktListingSon.set("aos_status", "编辑确认");
                    aosMktListingSon.set("aos_sourcetype", "DESIGN");
                    aosMktListingSon.set("aos_demandate", aosDemandate);
                    aosMktListingSon.set("aos_ismalllov", aosIsmalllov);
                    aosMktListingSon.set("aos_osconfirmlov", aosOsconfirmlov);
                    // BOTP
                    aosMktListingSon.set("aos_sourcebilltype", AOS_MKT_DESIGNREQ);
                    aosMktListingSon.set("aos_sourcebillno", dyMain.get("billno"));
                    aosMktListingSon.set("aos_srcentrykey", "aos_entryentity");
                    if (!"EN".equals(dyn3dR.getString("aos_language"))) {
                        aosMktListingSon.set("aos_orgid", aosOrgid);
                    }
                } else {
                    aosMktListingSon =
                        BusinessDataServiceHelper.loadSingle(aosMktListingSonq.getLong("id"), "aos_mkt_listing_son");
                }
                // 设计需求表申请人
                aosMktListingSon.set("aos_requireby", aosRequireby);
                aosMktListingSon.set("aos_requiredate", new Date());
                if (mapList != null) {
                    if (mapList.get(2) != null) {
                        aosMktListingSon.set("aos_organization1", mapList.get(2).get("id"));
                    }
                    if (mapList.get(3) != null) {
                        aosMktListingSon.set("aos_organization2", mapList.get(3).get("id"));
                    }
                }
                // 明细
                DynamicObjectCollection aosEntryentityS =
                    aosMktListingSon.getDynamicObjectCollection("aos_entryentity");
                DynamicObject aosEntryentity = aosEntryentityS.addNew();
                aosEntryentity.set("aos_itemid", itemId);
                aosEntryentity.set("aos_is_saleout", ProgressUtil.Is_saleout(itemId));
                aosEntryentity.set("aos_require", dyn3dR.get("aos_desreq"));
                aosEntryentity.set("aos_srcrowseq", dyn3dR.get("SEQ"));
                // 功能图文案备注
                aosEntryentity.set("aos_remakes", dyn3dR.get("aos_remakes"));
                DynamicObjectCollection aosAttribute = aosEntryentity.getDynamicObjectCollection("aos_attribute");
                aosAttribute.clear();
                DynamicObjectCollection aosAttributefrom = dyn3dR.getDynamicObjectCollection("aos_attribute");
                DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
                DynamicObject tempFile;
                for (DynamicObject d : aosAttributefrom) {
                    tempFile = BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"), type);
                    aosAttribute.addNew().set("fbasedataid", tempFile);
                }
                // 物料相关信息
                DynamicObjectCollection aosSubentryentityS =
                    aosEntryentity.getDynamicObjectCollection("aos_subentryentity");
                DynamicObject aosSubentryentity = aosSubentryentityS.addNew();
                aosSubentryentity.set("aos_segment3", dyn3dD.get("aos_segment3"));
                aosSubentryentity.set("aos_broitem", dyn3dD.get("aos_broitem"));
                aosSubentryentity.set("aos_itemname", dyn3dD.get("aos_itemname"));
                aosSubentryentity.set("aos_orgtext", dyn3dD.get("aos_orgtext"));
                aosSubentryentity.set("aos_reqinput", dyn3dR.get("aos_desreq"));
                aosEntryentity.set("aos_segment3_r", dyn3dD.get("aos_segment3"));
                aosEntryentity.set("aos_broitem_r", dyn3dD.get("aos_broitem"));
                aosEntryentity.set("aos_itemname_r", dyn3dD.get("aos_itemname"));
                aosEntryentity.set("aos_orgtext_r", ProgressUtil.getOrderOrg(itemId));
                if (messageFlag) {
                    messageId = String.valueOf(aosDesignerId);
                    message = "Listing优化需求表子表-设计需求自动创建";
                    AosMktListingSonBill.setListSonUserOrganizate(dyMain);
                    OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_son",
                        new DynamicObject[] {aosMktListingSon}, OperateOption.create());
                    // 修复关联关系
                    try {
                        ProgressUtil.botp("aos_mkt_listing_son", aosMktListingSon.get("id"));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                        MKTCom.SendGlobalMessage(messageId, String.valueOf(aosMktListingSon),
                            String.valueOf(operationrst.getSuccessPkIds().get(0)), aosMktListingSon.getString("billno"),
                            message);
                        FndHistory.Create(dyMain, messageId, "生成文案", aosMktListingSon.getString("billno"));
                        FndHistory.Create(aosMktListingSon, "来源-设计需求表", dyMain.getString("billno"));
                    }
                }
            }
            return aosEditor;
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /** 创建Listing优化需求表小语种 **/
    private static void generateListingLanguage(DynamicObject dyMain, List<DynamicObject> listingLanguage)
        throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            FndError fndError = new FndError();
            // 信息参数
            String messageId;
            String message;
            // 数据层
            Object aosDesignerId = ParaInfoUtil.dynFormat(dyMain.get("aos_designer"));
            Object billno = dyMain.get("billno");
            Object reqFid = dyMain.getPkValue();
            // 任务类型
            Object aosType = dyMain.get("aos_type");
            // 任务来源
            Object aosSource = dyMain.get("aos_source");
            // 紧急程度
            Object aosImportance = dyMain.get("aos_importance");
            // 设计需求表申请人
            Object aosRequireby = ParaInfoUtil.dynFormat(dyMain.get("aos_requireby"));
            List<DynamicObject> mapList = Cux_Common_Utl.GetUserOrg(aosRequireby);
            Object lastItemId = null;
            Object lastOrgNumber = null;
            Object aosDemandate = new Date();
            if (SAME.equals(aosType)) {
                aosDemandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 1);
            } else if (OLD.equals(aosType)) {
                aosDemandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 3);
            }
            // 校验
            if (listingLanguage.size() == 0) {
                fndError.add("小语种功能图翻译行信息不存在!");
            }
            if (fndError.getCount() > 0) {
                throw fndError;
            }
            DynamicObject aosMktListingMin = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_min");
            aosMktListingMin.set("aos_requireby", aosRequireby);
            aosMktListingMin.set("aos_requiredate", new Date());
            aosMktListingMin.set("aos_type", "功能图翻译");
            aosMktListingMin.set("aos_source", aosSource);
            aosMktListingMin.set("aos_importance", aosImportance);
            aosMktListingMin.set("aos_designer", aosDesignerId);
            aosMktListingMin.set("aos_orignbill", billno);
            aosMktListingMin.set("aos_sourceid", reqFid);
            aosMktListingMin.set("aos_status", "编辑确认");
            aosMktListingMin.set("aos_sourcetype", "DESIGN");
            aosMktListingMin.set("aos_demandate", aosDemandate);
            aosMktListingMin.set("aos_osconfirmlov", "否");
            // BOTP
            aosMktListingMin.set("aos_sourcebilltype", AOS_MKT_DESIGNREQ);
            aosMktListingMin.set("aos_sourcebillno", dyMain.get("billno"));
            aosMktListingMin.set("aos_srcentrykey", "aos_entryentity");
            if (mapList != null) {
                if (mapList.get(TWO) != null) {
                    aosMktListingMin.set("aos_organization1", mapList.get(2).get("id"));
                }
                if (mapList.get(THREE) != null) {
                    aosMktListingMin.set("aos_organization2", mapList.get(3).get("id"));
                }
            }

            DynamicObjectCollection mktListingMinS = aosMktListingMin.getDynamicObjectCollection("aos_entryentity");
            // 循环所有行
            for (DynamicObject aosEntryentity : listingLanguage) {
                DynamicObject mktListingMin = mktListingMinS.addNew();
                DynamicObject subentryentity = aosEntryentity.getDynamicObjectCollection("aos_subentryentity").get(0);
                lastItemId = aosEntryentity.get("aos_itemid.id");
                lastOrgNumber = aosEntryentity.get("aos_language");
                mktListingMin.set("aos_itemid", aosEntryentity.get("aos_itemid"));
                mktListingMin.set("aos_is_saleout", ProgressUtil.Is_saleout(lastItemId));
                mktListingMin.set("aos_require", aosEntryentity.get("aos_desreq"));
                mktListingMin.set("aos_srcrowseq", aosEntryentity.get("SEQ"));
                // 功能图文案备注
                mktListingMin.set("aos_remakes", aosEntryentity.get("aos_remakes"));
                // 附件
                DynamicObjectCollection aosAttribute = mktListingMin.getDynamicObjectCollection("aos_attribute");
                aosAttribute.clear();
                DynamicObjectCollection aosAttributefrom = aosEntryentity.getDynamicObjectCollection("aos_attribute");
                DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
                DynamicObject tempFile;
                for (DynamicObject d : aosAttributefrom) {
                    tempFile = BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"), type);
                    aosAttribute.addNew().set("fbasedataid", tempFile);
                }
                DynamicObjectCollection aosSubentryentityS =
                    mktListingMin.getDynamicObjectCollection("aos_subentryentity");
                DynamicObject aosSubentryentity = aosSubentryentityS.addNew();
                aosSubentryentity.set("aos_segment3", subentryentity.get("aos_segment3"));
                aosSubentryentity.set("aos_broitem", subentryentity.get("aos_broitem"));
                aosSubentryentity.set("aos_itemname", subentryentity.get("aos_itemname"));
                aosSubentryentity.set("aos_orgtext", subentryentity.get("aos_orgtext"));
                aosSubentryentity.set("aos_reqinput", aosEntryentity.get("aos_desreq"));

                mktListingMin.set("aos_segment3_r", subentryentity.get("aos_segment3"));
                mktListingMin.set("aos_broitem_r", subentryentity.get("aos_broitem"));
                mktListingMin.set("aos_itemname_r", subentryentity.get("aos_itemname"));
                mktListingMin.set("aos_orgtext_r",
                    ProgressUtil.getOrderOrg(aosEntryentity.getDynamicObject("aos_itemid").getPkValue()));
            }
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
            if (aosEditor == 0) {
                fndError.add(aosCategory1 + "," + aosCategory2 + "英语编辑不存在!");
                throw fndError;
            }
            long aosOueditor = 0;
            Object orgid = FndGlobal.get_import_id(lastOrgNumber, "bd_country");
            if (FndGlobal.IsNotNull(aosCategory1) && FndGlobal.IsNotNull(aosCategory2)) {
                DynamicObject aosMktProgorguser =
                    ProgressUtil.minListtFindEditorByType(orgid, aosCategory1, aosCategory2, "功能图翻译");
                if (aosMktProgorguser != null) {
                    aosOueditor = aosMktProgorguser.getLong("aos_user");
                }
            }
            if (aosOueditor == 0) {
                fndError.add(aosCategory1 + "," + aosCategory2 + "小语种编辑师不存在!");
                throw fndError;
            }
            // 小语种编辑师
            aosMktListingMin.set("aos_editormin", aosOueditor);
            aosMktListingMin.set("aos_user", aosOueditor);
            aosMktListingMin.set("aos_editor", aosEditor);
            aosMktListingMin.set("aos_orgid", orgid);
            // 国别=RO或者PT时，流程直接到小站海外编辑确认:功能图节点
            if (!Cux_Common_Utl.IsNull(lastOrgNumber)) {
                boolean overseasCountry =
                    RO.equalsIgnoreCase(String.valueOf(lastOrgNumber)) || PT.equals(String.valueOf(lastOrgNumber));
                if (overseasCountry) {
                    aosMktListingMin.set("aos_status", "小站海外编辑确认:功能图");
                    aosMktListingMin.set("aos_funcdate", new Date());
                }

            }
            messageId = String.valueOf(aosOueditor);
            message = "Listing优化需求表小语种-设计需求自动创建";
            OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_min",
                new DynamicObject[] {aosMktListingMin}, OperateOption.create());
            // 修复关联关系
            ProgressUtil.botp("aos_mkt_listing_min", aosMktListingMin.get("id"));
            if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                MKTCom.SendGlobalMessage(messageId, String.valueOf(aosMktListingMin),
                    String.valueOf(operationrst.getSuccessPkIds().get(0)), aosMktListingMin.getString("billno"),
                    message);
                FndHistory.Create(dyMain, messageId, "生成小语种", aosMktListingMin.getString("billno"));
                FndHistory.Create(aosMktListingMin, "来源-设计需求表", dyMain.getString("billno"));
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }

    }

    /** 触发生成设计需求表的listing优化需求表上有国别,设计需求表结束后触发生成下单国别的设计完成表-国别 **/
    private static void generateDesignHasOu(DynamicObject dyMain) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            FndError fndError = new FndError();
            // 信息处理
            String messageId;
            String message;
            // 数据层
            // 设计
            Object aosDesigner = dyMain.getDynamicObject("aos_designer").getPkValue();
            // 制作设计师
            Object aosDesignby = dyMain.getDynamicObject("aos_designby").getPkValue();
            Object billno = dyMain.get("billno");
            Object reqFid = dyMain.getPkValue();
            // 任务类型
            Object aosType = dyMain.get("aos_type");
            // 任务来源
            Object aosSource = dyMain.get("aos_source");
            // 紧急程度
            Object aosImportance = dyMain.get("aos_importance");
            // 国别
            Object aosOrgid = ParaInfoUtil.dynFormat(dyMain.get("aos_orgid"));

            Boolean aos3d = null;
            // 循环创建
            DynamicObject aosMktDesigncmp = BusinessDataServiceHelper.newDynamicObject("aos_mkt_designcmp");
            aosMktDesigncmp.set("aos_requireby", aosDesignby);
            aosMktDesigncmp.set("aos_designer", aosDesigner);
            aosMktDesigncmp.set("aos_orgid", aosOrgid);
            aosMktDesigncmp.set("aos_orignbill", billno);
            aosMktDesigncmp.set("aos_sourceid", reqFid);
            aosMktDesigncmp.set("aos_type", aosType);
            aosMktDesigncmp.set("aos_source", aosSource);
            aosMktDesigncmp.set("aos_importance", aosImportance);
            aosMktDesigncmp.set("aos_requiredate", new Date());
            // BOTP
            aosMktDesigncmp.set("aos_sourcebilltype", AOS_MKT_DESIGNREQ);
            aosMktDesigncmp.set("aos_sourcebillno", dyMain.get("billno"));
            aosMktDesigncmp.set("aos_srcentrykey", "aos_entryentity");
            List<DynamicObject> mapList = Cux_Common_Utl.GetUserOrg(aosDesigner);
            if (mapList != null) {
                if (mapList.get(TWO) != null) {
                    aosMktDesigncmp.set("aos_organization1", mapList.get(TWO).get("id"));
                }
                if (mapList.get(THREE) != null) {
                    aosMktDesigncmp.set("aos_organization2", mapList.get(THREE).get("id"));
                }
            }
            DynamicObjectCollection aosEntryentityS = dyMain.getDynamicObjectCollection("aos_entryentity");
            DynamicObjectCollection cmpEntryentityS = aosMktDesigncmp.getDynamicObjectCollection("aos_entryentity");
            int total = 0;
            for (DynamicObject aosEntryentity : aosEntryentityS) {
                // 优化项无数字 直接跳过
                int aosWhited = aosEntryentity.getInt("aos_whited");
                int aosWhites = aosEntryentity.getInt("aos_whites");
                int aosBackround = aosEntryentity.getInt("aos_backround");
                int aosFuncpic = aosEntryentity.getInt("aos_funcpic");
                int aosFunclike = aosEntryentity.getInt("aos_funclike");
                int aosSizepic = aosEntryentity.getInt("aos_sizepic");
                int aosAnew = aosEntryentity.getInt("aos_anew");
                int aosAlike = aosEntryentity.getInt("aos_alike");
                int aosTrans = aosEntryentity.getInt("aos_trans");
                int aosDespic = aosEntryentity.getInt("aos_despic");
                int aosPs = aosEntryentity.getInt("aos_ps");
                int aosFix = aosEntryentity.getInt("aos_fix");
                int aosWhitepro = aosEntryentity.getInt("aos_whitepro");
                int aosProground = aosEntryentity.getInt("aos_proground");
                int aosDetailpic = aosEntryentity.getInt("aos_detailpic");
                // 优化项内数字
                int linetotal = aosWhited + aosWhites + aosBackround + aosFuncpic + aosFunclike + aosSizepic + aosAnew
                    + aosAlike + aosTrans + aosDespic + aosPs + aosFix + aosWhitepro + aosProground + aosDetailpic;
                if (linetotal == 0) {
                    continue;
                }
                total += aosWhited + aosBackround + aosFuncpic + aosFunclike;
                DynamicObject cmpEntryentity = cmpEntryentityS.addNew();
                cmpEntryentity.set("aos_itemid", aosEntryentity.get("aos_itemid"));
                cmpEntryentity.set("aos_3d", aosEntryentity.get("aos_3d"));
                cmpEntryentity.set("aos_desreq", aosEntryentity.get("aos_desreq"));
                cmpEntryentity.set("aos_designway", aosEntryentity.get("aos_designway"));
                cmpEntryentity.set("aos_srcrowseq", aosEntryentity.get("SEQ"));
                if (Cux_Common_Utl.IsNull(aos3d)) {
                    aos3d = aosEntryentity.getBoolean("aos_3d");
                }
                cmpEntryentity.set("aos_language", aosEntryentity.get("aos_language"));
                cmpEntryentity.set("aos_whited", aosEntryentity.get("aos_whited"));
                cmpEntryentity.set("aos_whites", aosEntryentity.get("aos_whites"));
                cmpEntryentity.set("aos_backround", aosEntryentity.get("aos_backround"));
                cmpEntryentity.set("aos_funcpic", aosEntryentity.get("aos_funcpic"));
                cmpEntryentity.set("aos_funclike", aosEntryentity.get("aos_funclike"));
                cmpEntryentity.set("aos_sizepic", aosEntryentity.get("aos_sizepic"));
                cmpEntryentity.set("aos_anew", aosEntryentity.get("aos_anew"));
                cmpEntryentity.set("aos_alike", aosEntryentity.get("aos_alike"));
                cmpEntryentity.set("aos_trans", aosEntryentity.get("aos_trans"));
                cmpEntryentity.set("aos_despic", aosEntryentity.get("aos_despic"));
                cmpEntryentity.set("aos_ps", aosEntryentity.get("aos_ps"));
                cmpEntryentity.set("aos_fix", aosEntryentity.get("aos_fix"));
                Object itemId = aosEntryentity.getDynamicObject("aos_itemid").getPkValue();
                String category = MKTCom.getItemCateNameZH(itemId);
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
                // 附件
                DynamicObjectCollection aosAttribute = cmpEntryentity.getDynamicObjectCollection("aos_attribute");
                aosAttribute.clear();
                DynamicObjectCollection aosAttributefrom = aosEntryentity.getDynamicObjectCollection("aos_attribute");
                DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
                DynamicObject tempFile;
                for (DynamicObject d : aosAttributefrom) {
                    tempFile = BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"), type);
                    aosAttribute.addNew().set("fbasedataid", tempFile);
                }
                // 子单据体
                DynamicObjectCollection aosSubentryentityS =
                    aosEntryentity.getDynamicObjectCollection("aos_subentryentity");
                DynamicObjectCollection cmpSubentryentityS =
                    cmpEntryentity.getDynamicObjectCollection("aos_subentryentity");
                for (DynamicObject aosSubentryentity : aosSubentryentityS) {
                    DynamicObject cmpSubentryentity = cmpSubentryentityS.addNew();
                    cmpSubentryentity.set("aos_broitem", aosSubentryentity.get("aos_broitem"));
                    cmpSubentryentity.set("aos_segment3", aosSubentryentity.get("aos_segment3"));
                    cmpSubentryentity.set("aos_itemname", aosSubentryentity.get("aos_itemname"));
                    cmpSubentryentity.set("aos_orgtext", aosSubentryentity.get("aos_orgtext"));
                    cmpSubentryentity.set("aos_brand", aosSubentryentity.get("aos_brand"));
                    cmpSubentryentity.set("aos_developer", aosSubentryentity.get("aos_developer"));
                    cmpSubentryentity.set("aos_url", aosSubentryentity.get("aos_url"));
                    cmpSubentryentity.set("aos_pic", aosSubentryentity.get("aos_pic"));
                    Object aosEditor = null;
                    if (FndGlobal.IsNotNull(aosCategory1) && FndGlobal.IsNotNull(aosCategory2)) {
                        QFilter filterCategory1 = new QFilter("aos_category1", "=", aosCategory1);
                        QFilter filterCategory2 = new QFilter("aos_category2", "=", aosCategory2);
                        QFilter filterOrg = new QFilter("aos_orgid", "=", aosOrgid);
                        QFilter[] filtersCategory = new QFilter[] {filterCategory1, filterCategory2, filterOrg};
                        String selectStr = "aos_oueditor";
                        DynamicObject aosMktProguser =
                            QueryServiceHelper.queryOne("aos_mkt_progorguser", selectStr, filtersCategory);
                        if (aosMktProguser != null) {
                            aosEditor = aosMktProguser.getLong("aos_oueditor");
                        }
                    }
                    cmpSubentryentity.set("aos_editor", aosEditor);
                }
            }
            // 推送给销售
            Object itemId = aosEntryentityS.get(0).getDynamicObject("aos_itemid").getPkValue();
            String category = MKTCom.getItemCateNameZH(itemId);
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
                QFilter filterOu = new QFilter("aos_orgid", "=", aosOrgid);
                QFilter[] filtersCategory = new QFilter[] {filterCategory1, filterCategory2, filterOu};
                String selectStr = "aos_02hq";
                DynamicObject aosMktProgorguser =
                    QueryServiceHelper.queryOne("aos_mkt_progorguser", selectStr, filtersCategory);
                if (aosMktProgorguser != null) {
                    aosSale = aosMktProgorguser.getLong(selectStr);
                }
            }
            if (aosSale == 0) {
                fndError.add(aosCategory1 + "," + aosCategory2 + "国别组长不存在!");
                throw fndError;
            }
            aosMktDesigncmp.set("aos_sale", aosSale);
            if (Cux_Common_Utl.IsNull(aos3d)) {
                aos3d = false;
            }
            // 节点判断
            if (total > 0 || Boolean.TRUE.equals(aos3d)) {
                aosMktDesigncmp.set("aos_status", "销售确认");
                aosMktDesigncmp.set("aos_user", aosSale);
                messageId = String.valueOf(aosSale);
                message = "设计完成表国别-设计需求表自动创建";
                OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_designcmp",
                    new DynamicObject[] {aosMktDesigncmp}, OperateOption.create());
                // 修复关联关系
                try {
                    ProgressUtil.botp("aos_mkt_designcmp", aosMktDesigncmp.get("id"));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

                if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                    MKTCom.SendGlobalMessage(messageId, String.valueOf(aosMktDesigncmp),
                        String.valueOf(operationrst.getSuccessPkIds().get(0)), aosMktDesigncmp.getString("billno"),
                        message);
                    FndHistory.Create(dyMain, messageId, "生成设计完成表", aosMktDesigncmp.getString("billno"));
                    FndHistory.Create(aosMktDesigncmp, "来源-设计需求表", dyMain.getString("billno"));
                }
            }

        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /** 触发生成设计需求表的listing优化需求表上无国别，设计需求表结束后触发生成下单国别的设计完成表-国别 **/
    private static void generateDesignNotHasOu(DynamicObject dyMain) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            FndError fndError = new FndError();
            // 信息处理
            String messageId;
            String message;
            // 数据层
            // 设计
            Object aosDesigner = dyMain.getDynamicObject("aos_designer").getPkValue();
            // 制作设计师
            Object aosDesignby = dyMain.getDynamicObject("aos_designby").getPkValue();
            Object billno = dyMain.get("billno");
            Object reqFid = dyMain.getPkValue();
            // 任务类型
            Object aosType = dyMain.get("aos_type");
            // 任务来源
            Object aosSource = dyMain.get("aos_source");
            // 紧急程度
            Object aosImportance = dyMain.get("aos_importance");
            DynamicObjectCollection aosEntryentityS = dyMain.getDynamicObjectCollection("aos_entryentity");
            Map<String, List<DynamicObject>> oumap = new HashMap<>(16);
            List<DynamicObject> mapList;
            List<DynamicObject> orgList = Cux_Common_Utl.GetUserOrg(aosDesigner);
            Boolean aos3d = null;
            int total = 0;
            // 循环国别分组
            for (DynamicObject aosEntryentity : aosEntryentityS) {
                // 优化项无数字 直接跳过
                int aosWhited = aosEntryentity.getInt("aos_whited");
                int aosWhites = aosEntryentity.getInt("aos_whites");
                int aosBackround = aosEntryentity.getInt("aos_backround");
                int aosFuncpic = aosEntryentity.getInt("aos_funcpic");
                int aosFunclike = aosEntryentity.getInt("aos_funclike");
                int aosSizepic = aosEntryentity.getInt("aos_sizepic");
                int aosAnew = aosEntryentity.getInt("aos_anew");
                int aosAlike = aosEntryentity.getInt("aos_alike");
                int aosTrans = aosEntryentity.getInt("aos_trans");
                int aosDespic = aosEntryentity.getInt("aos_despic");
                int aosPs = aosEntryentity.getInt("aos_ps");
                int aosFix = aosEntryentity.getInt("aos_fix");
                int aosWhitepro = aosEntryentity.getInt("aos_whitepro");
                int aosProground = aosEntryentity.getInt("aos_proground");
                int aosDetailpic = aosEntryentity.getInt("aos_detailpic");
                // 优化项内数字
                int linetotal = aosWhited + aosWhites + aosBackround + aosFuncpic + aosFunclike + aosSizepic + aosAnew
                    + aosAlike + aosTrans + aosDespic + aosPs + aosFix + aosWhitepro + aosProground + aosDetailpic;
                if (linetotal == 0) {
                    continue;
                }
                total += aosWhited + aosBackround + aosFuncpic + aosFunclike;
                DynamicObjectCollection aosSubentryentityS =
                    aosEntryentity.getDynamicObjectCollection("aos_subentryentity");
                DynamicObject aosSubentryentity = aosSubentryentityS.get(0);
                String aosOrgtext = aosSubentryentity.getString("aos_orgtext");
                if (Cux_Common_Utl.IsNull(aosOrgtext)) {
                    continue;
                }
                String[] aosOrgtextArray = aosOrgtext.split(";");
                for (String org : aosOrgtextArray) {
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
                if (Cux_Common_Utl.IsNull(orgId)) {
                    continue;
                }
                DynamicObject aosMktDesigncmp = BusinessDataServiceHelper.newDynamicObject("aos_mkt_designcmp");
                aosMktDesigncmp.set("aos_requireby", aosDesignby);
                aosMktDesigncmp.set("aos_designer", aosDesigner);
                aosMktDesigncmp.set("aos_status", "申请人");
                aosMktDesigncmp.set("aos_orgid", orgId);
                aosMktDesigncmp.set("aos_orignbill", billno);
                aosMktDesigncmp.set("aos_sourceid", reqFid);
                aosMktDesigncmp.set("aos_type", aosType);
                aosMktDesigncmp.set("aos_source", aosSource);
                aosMktDesigncmp.set("aos_importance", aosImportance);
                aosMktDesigncmp.set("aos_requiredate", new Date());
                // BOTP
                aosMktDesigncmp.set("aos_sourcebilltype", AOS_MKT_DESIGNREQ);
                aosMktDesigncmp.set("aos_sourcebillno", dyMain.get("billno"));
                aosMktDesigncmp.set("aos_srcentrykey", "aos_entryentity");
                if (orgList != null) {
                    if (orgList.get(2) != null) {
                        aosMktDesigncmp.set("aos_organization1", orgList.get(2).get("id"));
                    }
                    if (orgList.get(3) != null) {
                        aosMktDesigncmp.set("aos_organization2", orgList.get(3).get("id"));
                    }
                }
                DynamicObjectCollection cmpEntryentityS = aosMktDesigncmp.getDynamicObjectCollection("aos_entryentity");
                List<DynamicObject> entryList = oumap.get(ou);
                for (DynamicObject aosEntryentity : entryList) {
                    DynamicObject cmpEntryentity = cmpEntryentityS.addNew();
                    cmpEntryentity.set("aos_itemid", aosEntryentity.get("aos_itemid"));
                    cmpEntryentity.set("aos_3d", aosEntryentity.get("aos_3d"));
                    if (Cux_Common_Utl.IsNull(aos3d)) {
                        aos3d = aosEntryentity.getBoolean("aos_3d");
                    }
                    cmpEntryentity.set("aos_desreq", aosEntryentity.get("aos_desreq"));
                    cmpEntryentity.set("aos_language", aosEntryentity.get("aos_language"));
                    cmpEntryentity.set("aos_whited", aosEntryentity.get("aos_whited"));
                    cmpEntryentity.set("aos_whites", aosEntryentity.get("aos_whites"));
                    cmpEntryentity.set("aos_backround", aosEntryentity.get("aos_backround"));
                    cmpEntryentity.set("aos_funcpic", aosEntryentity.get("aos_funcpic"));
                    cmpEntryentity.set("aos_funclike", aosEntryentity.get("aos_funclike"));
                    cmpEntryentity.set("aos_sizepic", aosEntryentity.get("aos_sizepic"));
                    cmpEntryentity.set("aos_anew", aosEntryentity.get("aos_anew"));
                    cmpEntryentity.set("aos_alike", aosEntryentity.get("aos_alike"));
                    cmpEntryentity.set("aos_trans", aosEntryentity.get("aos_trans"));
                    cmpEntryentity.set("aos_despic", aosEntryentity.get("aos_despic"));
                    cmpEntryentity.set("aos_ps", aosEntryentity.get("aos_ps"));
                    cmpEntryentity.set("aos_fix", aosEntryentity.get("aos_fix"));
                    cmpEntryentity.set("aos_designway", aosEntryentity.get("aos_designway"));
                    cmpEntryentity.set("aos_srcrowseq", aosEntryentity.get("SEQ"));
                    Object itemId = aosEntryentity.getDynamicObject("aos_itemid").getPkValue();
                    String category = MKTCom.getItemCateNameZH(String.valueOf(itemId));
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
                    // 附件
                    DynamicObjectCollection aosAttribute = cmpEntryentity.getDynamicObjectCollection("aos_attribute");
                    aosAttribute.clear();
                    DynamicObjectCollection aosAttributefrom =
                        aosEntryentity.getDynamicObjectCollection("aos_attribute");
                    DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
                    DynamicObject tempFile;
                    for (DynamicObject d : aosAttributefrom) {
                        tempFile =
                            BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"), type);
                        aosAttribute.addNew().set("fbasedataid", tempFile);
                    }
                    // 子单据体
                    DynamicObjectCollection aosSubentryentityS =
                        aosEntryentity.getDynamicObjectCollection("aos_subentryentity");
                    DynamicObjectCollection cmpSubentryentityS =
                        cmpEntryentity.getDynamicObjectCollection("aos_subentryentity");
                    for (DynamicObject aosSubentryentity : aosSubentryentityS) {
                        DynamicObject cmpSubentryentity = cmpSubentryentityS.addNew();
                        cmpSubentryentity.set("aos_broitem", aosSubentryentity.get("aos_broitem"));
                        cmpSubentryentity.set("aos_segment3", aosSubentryentity.get("aos_segment3"));
                        cmpSubentryentity.set("aos_itemname", aosSubentryentity.get("aos_itemname"));
                        cmpSubentryentity.set("aos_orgtext", aosSubentryentity.get("aos_orgtext"));
                        cmpSubentryentity.set("aos_brand", aosSubentryentity.get("aos_brand"));
                        cmpSubentryentity.set("aos_developer", aosSubentryentity.get("aos_developer"));
                        cmpSubentryentity.set("aos_url", aosSubentryentity.get("aos_url"));
                        cmpSubentryentity.set("aos_pic", aosSubentryentity.get("aos_pic"));
                        Object aosEditor = null;
                        if (FndGlobal.IsNotNull(aosCategory1)) {
                            QFilter filterCategory1 = new QFilter("aos_category1", "=", aosCategory1);
                            QFilter filterCategory2 = new QFilter("aos_category2", "=", aosCategory2);
                            QFilter filterOrg = new QFilter("aos_orgid", "=", orgId);
                            QFilter[] filtersCategory = new QFilter[] {filterCategory1, filterCategory2, filterOrg};
                            String selectStr = "aos_oueditor";
                            DynamicObject aosMktProguser =
                                QueryServiceHelper.queryOne("aos_mkt_progorguser", selectStr, filtersCategory);
                            if (aosMktProguser != null) {
                                aosEditor = aosMktProguser.getLong("aos_oueditor");
                            }
                        }
                        cmpSubentryentity.set("aos_editor", aosEditor);
                    }
                }
                // 推送给销售
                Object itemId = aosEntryentityS.get(0).getDynamicObject("aos_itemid").getPkValue();
                String category = MKTCom.getItemCateNameZH(itemId);
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
                    QFilter filterOu = new QFilter("aos_orgid", "=", orgId);
                    QFilter[] filtersCategory = new QFilter[] {filterCategory1, filterCategory2, filterOu};
                    String selectStr = "aos_02hq";
                    DynamicObject aosMktProgorguser =
                        QueryServiceHelper.queryOne("aos_mkt_progorguser", selectStr, filtersCategory);
                    if (aosMktProgorguser != null) {
                        aosSale = aosMktProgorguser.getLong(selectStr);
                    }
                }
                if (aosSale == 0) {
                    fndError.add(aosCategory1 + "," + aosCategory2 + "国别组长不存在!");
                    throw fndError;
                }
                aosMktDesigncmp.set("aos_sale", aosSale);
                if (Cux_Common_Utl.IsNull(aos3d)) {
                    aos3d = false;
                }
                // 节点判断
                if (total > 0 || Boolean.TRUE.equals(aos3d)) {
                    aosMktDesigncmp.set("aos_status", "销售确认");
                    aosMktDesigncmp.set("aos_user", aosSale);
                    messageId = String.valueOf(aosSale);
                    message = "设计完成表国别-设计需求表自动创建";
                    OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_designcmp",
                        new DynamicObject[] {aosMktDesigncmp}, OperateOption.create());
                    // 修复关联关系
                    ProgressUtil.botp("aos_mkt_designcmp", aosMktDesigncmp.get("id"));
                    if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                        MKTCom.SendGlobalMessage(messageId, String.valueOf(aosMktDesigncmp),
                            String.valueOf(operationrst.getSuccessPkIds().get(0)), aosMktDesigncmp.getString("billno"),
                            message);
                        FndHistory.Create(dyMain, messageId, "生成设计完成表", aosMktDesigncmp.getString("billno"));
                        FndHistory.Create(aosMktDesigncmp, "来源-设计需求表", dyMain.getString("billno"));
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

    /** 是否3D建模=否，功能图翻译语种=空，且任务类型=翻译或者四者一致，流程到结束节点 并生成Listing优化销售确认单 **/
    private static void generateListingSal(DynamicObject dyMain, String type) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            // 信息处理
            String messageId;
            String message;
            // 数据层
            Object aosDesigner = dyMain.getDynamicObject("aos_designer").getPkValue();
            Object billno = dyMain.get("billno");
            Object reqFid = dyMain.getPkValue();
            Object aosType = dyMain.get("aos_type");
            Object aosOrgid = dyMain.get("aos_orgid");
            String aosOrgnumber = null;
            if (aosOrgid != null) {
                aosOrgnumber = ((DynamicObject)aosOrgid).getString("number");
            }
            DynamicObjectCollection aosEntryentityS = dyMain.getDynamicObjectCollection("aos_entryentity");
            Map<String, List<DynamicObject>> oumap = new HashMap<>(16);
            List<DynamicObject> mapList;
            // 循环国别分组
            for (DynamicObject aosEntryentity : aosEntryentityS) {
                int aosWhited = aosEntryentity.getInt("aos_whited");
                int aosWhites = aosEntryentity.getInt("aos_whites");
                int aosBackround = aosEntryentity.getInt("aos_backround");
                int aosFuncpic = aosEntryentity.getInt("aos_funcpic");
                int aosFunclike = aosEntryentity.getInt("aos_funclike");
                int aosSizepic = aosEntryentity.getInt("aos_sizepic");
                int aosAnew = aosEntryentity.getInt("aos_anew");
                int aosAlike = aosEntryentity.getInt("aos_alike");
                int aosTrans = aosEntryentity.getInt("aos_trans");
                int aosDespic = aosEntryentity.getInt("aos_despic");
                int aosPs = aosEntryentity.getInt("aos_ps");
                int aosFix = aosEntryentity.getInt("aos_fix");
                int aosWhitepro = aosEntryentity.getInt("aos_whitepro");
                int aosProground = aosEntryentity.getInt("aos_proground");
                int aosDetailpic = aosEntryentity.getInt("aos_detailpic");
                // 优化项内数字
                int linetotal = aosWhited + aosWhites + aosBackround + aosFuncpic + aosFunclike + aosSizepic + aosAnew
                    + aosAlike + aosTrans + aosDespic + aosPs + aosFix + aosWhitepro + aosProground + aosDetailpic;
                if (linetotal == 0) {
                    // 本条件需要优化项内有数字
                    continue;
                }
                int total = aosWhited + aosBackround + aosFuncpic + aosFunclike;
                boolean aos3d = aosEntryentity.getBoolean("aos_3d");
                DynamicObjectCollection aosSubentryentityS =
                    aosEntryentity.getDynamicObjectCollection("aos_subentryentity");
                DynamicObject aosSubentryentity = aosSubentryentityS.get(0);
                String aosOrgtext = aosSubentryentity.getString("aos_orgtext");
                String[] aosOrgtextArray = aosOrgtext.split(";");
                if ("A".equals(type)) {
                    for (String org : aosOrgtextArray) {
                        if (aosOrgid != null && !org.equals(aosOrgnumber)) {
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
                // 申请人提交，且头表有国别
                else if ("B".equals(type)) {
                    if (total == 0 && !aos3d) {
                        String orgNumber = dyMain.getDynamicObject("aos_orgid").getString("number");
                        List<DynamicObject> list = oumap.computeIfAbsent(orgNumber, key -> new ArrayList<>());
                        list.add(aosEntryentity);
                    }
                } else if ("C".equals(type)) {
                    if (total == 0 && !aos3d) {
                        for (String org : aosOrgtextArray) {
                            if (aosOrgid != null && !org.equals(aosOrgnumber)) {
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
                }
            }
            // 循环每个分组后的国家 创建一个头
            for (String ou : oumap.keySet()) {
                Object orgId = FndGlobal.get_import_id(ou, "bd_country");
                DynamicObject aosMktListingSal = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_sal");
                aosMktListingSal.set("aos_requireby", aosDesigner);
                aosMktListingSal.set("aos_designer", aosDesigner);
                aosMktListingSal.set("aos_status", "销售确认");
                aosMktListingSal.set("aos_orgid", orgId);
                aosMktListingSal.set("aos_orignbill", billno);
                aosMktListingSal.set("aos_sourceid", reqFid);
                aosMktListingSal.set("aos_type", aosType);
                aosMktListingSal.set("aos_sourcetype", "设计需求表");
                aosMktListingSal.set("aos_requiredate", new Date());
                // BOTP
                aosMktListingSal.set("aos_sourcebilltype", AOS_MKT_DESIGNREQ);
                aosMktListingSal.set("aos_sourcebillno", dyMain.get("billno"));
                aosMktListingSal.set("aos_srcentrykey", "aos_entryentity");
                DynamicObjectCollection cmpEntryentityS =
                    aosMktListingSal.getDynamicObjectCollection("aos_entryentity");
                List<DynamicObject> entryList = oumap.get(ou);
                long aosSale = 0;
                for (DynamicObject aosEntryentity : entryList) {
                    DynamicObject aosSubentryentity =
                        aosEntryentity.getDynamicObjectCollection("aos_subentryentity").get(0);
                    DynamicObject cmpEntryentity = cmpEntryentityS.addNew();
                    cmpEntryentity.set("aos_itemid", aosEntryentity.get("aos_itemid"));
                    cmpEntryentity.set("aos_segment3", aosSubentryentity.get("aos_segment3"));
                    cmpEntryentity.set("aos_itemname", aosSubentryentity.get("aos_itemname"));
                    cmpEntryentity.set("aos_broitem", aosSubentryentity.get("aos_broitem"));
                    cmpEntryentity.set("aos_salestatus", "已确认");
                    cmpEntryentity.set("aos_text", aosEntryentity.get("aos_desreq"));
                    cmpEntryentity.set("aos_srcrowseq", aosEntryentity.get("SEQ"));
                    Object itemId = aosEntryentity.getDynamicObject("aos_itemid").getPkValue();
                    String category = MKTCom.getItemCateNameZH(itemId);
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
                        Object aosEditor = null;
                        QFilter filterCategory1 = new QFilter("aos_category1", "=", aosCategory1);
                        QFilter filterCategory2 = new QFilter("aos_category2", "=", aosCategory2);
                        QFilter filterOrg = new QFilter("aos_orgid", "=", orgId);
                        QFilter[] filtersCategory = new QFilter[] {filterCategory1, filterCategory2, filterOrg};
                        String selectStr = "aos_oueditor";
                        DynamicObject aosMktProguser =
                            QueryServiceHelper.queryOne("aos_mkt_progorguser", selectStr, filtersCategory);
                        if (aosMktProguser != null) {
                            aosEditor = aosMktProguser.getLong("aos_oueditor");
                        }
                        aosMktListingSal.set("aos_editor", aosEditor);
                    }
                    aosSale = 0;
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
                    String listing = "LISTING";
                    String old = "老品优化";
                    if (listing.equals(sourceType) && old.equals(aosType)) {
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
                    aosMktListingSal.set("aos_user", aosSale);
                    aosMktListingSal.set("aos_sale", aosSale);
                }
                messageId = String.valueOf(aosSale);
                message = "Listing优化销售确认单-设计需求表自动创建";
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
                    FndHistory.Create(dyMain, messageId, "生成销售确认单", aosMktListingSal.getString("billno"));
                    FndHistory.Create(aosMktListingSal, "来源-设计需求表", dyMain.getString("billno"));
                }
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 创建设计需求表后，未保存前的，用于一些赋值
     */
    public static void createDesiginBeforeSave(DynamicObject designEntity) {
        // 查找已经生成3d建模的物料
        List<String> skuList = DesignSkuList.getSkuList();
        DynamicObjectCollection entityRows = designEntity.getDynamicObjectCollection("aos_entryentity");
        for (DynamicObject row : entityRows) {
            String itemid = row.getString("aos_itemid");
            row.set("aos_is_design", skuList.contains(itemid));
        }
    }

    @Override
    public void afterBindData(EventObject e) {
        super.afterBindData(e);
        // 设置缓存
        setPageCache();
    }

    @Override
    public void hyperLinkClick(HyperLinkClickEvent hyperLinkClickEvent) {
        int rowIndex = hyperLinkClickEvent.getRowIndex();
        String fieldName = hyperLinkClickEvent.getFieldName();
        if (AOS_SEGMENT3.equals(fieldName)) {
            Object aosSegment3 = this.getModel().getValue("aos_segment3", rowIndex);
            DynamicObject aosMktFunctreq = QueryServiceHelper.queryOne("aos_mkt_functreq", "id",
                new QFilter[] {new QFilter("aos_segment3", QCP.equals, aosSegment3)});
            if (!Cux_Common_Utl.IsNull(aosMktFunctreq)) {
                Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_functreq", aosMktFunctreq.get("id"));
            } else {
                this.getView().showErrorNotification("功能图需求表信息不存在!");
            }
        } else if (AOS_URL.equals(fieldName)) {
            String url = this.getModel().getValue("aos_url", rowIndex).toString();
            if (url != null) {
                this.getView().openUrl(url);
            }
        }
    }

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        this.addItemClickListeners("aos_toolbarap");
        this.addItemClickListeners("aos_submit");
        this.addItemClickListeners("aos_return");
        this.addItemClickListeners("aos_open");
        this.addItemClickListeners("aos_openorign");
        EntryGrid entryGrid = this.getControl("aos_subentryentity");
        entryGrid.addHyperClickListener(this);
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        Span span = MmsOtelUtils.getCusMainSpan(TRACER, MmsOtelUtils.getMethodPath());
        super.itemClick(evt);
        String control = evt.getItemKey();
        try (Scope ignore = span.makeCurrent()) {
            if (AOS_SUBMIT.equals(control)) {
                DynamicObject dyMain = this.getModel().getDataEntity(true);
                // 提交
                aosSubmit(dyMain, "A");
            } else if (AOS_RETURN.equals(control)) {
                aosReturn();
            } else if (AOS_OPEN.equals(control)) {
                aosOpen();
            } else if (AOS_OPENORIGN.equals(control)) {
                aosOpenorign();
            } else if (AOS_HISTORY.equals(control)) {
                aosHistory();// 查看历史记录
            } else if (AOS_CLOSE.equals(control)) {
                aosClose();// 手工关闭
            } else if (AOS_QUERYSAMPLE.equals(control)) {
                querySample();
            }
        } catch (FndError fndError) {
            this.getView().showTipNotification(fndError.getErrorMessage());
            MmsOtelUtils.setException(span, fndError);
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
            MmsOtelUtils.setException(span, ex);
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    private void querySample() throws FndError {
        try {
            // 封样单参数
            Object sampleId;
            // 当前行
            int currentRow = this.getModel().getEntryCurrentRowIndex("aos_entryentity");
            // 货号
            DynamicObject aosItemId = (DynamicObject)this.getModel().getValue("aos_itemid", currentRow);
            List<Object> primaryKeys = QueryServiceHelper.queryPrimaryKeys("aos_sealsample",
                new QFilter("aos_item.id", QCP.equals, aosItemId.getPkValue()).toArray(), "createtime desc", 1);
            if (FndGlobal.IsNotNull(primaryKeys) && primaryKeys.size() > 0) {
                sampleId = primaryKeys.get(0);
            } else {
                throw new FndError("未找到对应封样单!");
            }
            // 打开封样单
            FndGlobal.OpenBillById(this.getView(), "aos_sealsample", sampleId);
        } catch (FndError fndMessage) {
            this.getView().showTipNotification(fndMessage.getErrorMessage());
        } catch (Exception ex) {
            this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
        }
    }

    private void aosClose() {
        String keyCancel = "bar_cancel";
        ConfirmCallBackListener confirmCallBackListener = new ConfirmCallBackListener(keyCancel, this);
        // 设置页面确认框，参数为：标题，选项框类型，回调监听
        this.getView().showConfirm("您确认关闭此申请单吗？", MessageBoxOptions.YesNo, confirmCallBackListener);
    }

    @Override
    public void confirmCallBack(MessageBoxClosedEvent event) {
        super.confirmCallBack(event);
        String callBackId = event.getCallBackId();
        String keyCancel = "bar_cancel";
        if (keyCancel.equals(callBackId)) {
            if (event.getResult().equals(MessageBoxResult.Yes)) {
                this.getModel().setValue("aos_user", SYSTEM);
                this.getModel().setValue("aos_status", "结束");
                this.getView().invokeOperation("save");
                this.getView().invokeOperation("refresh");
                setErrorList(this.getModel().getDataEntity(true));
                FndHistory.Create(this.getView(), "手工关闭", "手工关闭");
            }
        }
    }

    /** 打开历史记录 **/
    private void aosHistory() throws FndError {
        Cux_Common_Utl.OpenHistory(this.getView());
    }

    private void aosOpenorign() {
        Object aosSourceid = this.getModel().getValue("aos_sourceid");
        Object aosSourcetype = this.getModel().getValue("aos_sourcetype");
        if (LISTING.equals(aosSourcetype)) {
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_listing_req", aosSourceid);
        } else if (PHOTO.equals(aosSourcetype)) {
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_photoreq", aosSourceid);
        }
    }

    private void aosOpen() {
        Object aosProductid = this.getModel().getValue("aos_productid");
        DynamicObject aosProdatachan = QueryServiceHelper.queryOne("aos_prodatachan", "id",
            new QFilter[] {new QFilter("id", QCP.equals, aosProductid)});
        if (!Cux_Common_Utl.IsNull(aosProdatachan)) {
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_prodatachan", aosProdatachan.get("id"));
        } else {
            this.getView().showErrorNotification("产品资料变跟单信息不存在!");
        }
    }

    /** 申请人确认退回 **/
    private void aosReturn() throws FndError {
        Object aosLaststatus = this.getModel().getValue("aos_laststatus");
        Object aosLastuser = this.getModel().getValue("aos_lastuser");
        Object aosReturnreason = this.getModel().getValue("aos_returnreason", 0);
        if (FndGlobal.IsNull(aosReturnreason)) {
            throw new FndError("退回时退回原因必填!");
        }
        if (!Cux_Common_Utl.IsNull(aosLaststatus) && !Cux_Common_Utl.IsNull(aosLastuser)) {
            this.getModel().setValue("aos_status", this.getModel().getValue("aos_laststatus"));
            this.getModel().setValue("aos_user", this.getModel().getValue("aos_lastuser"));
        } else {
            throw new FndError("前置节点不允许退回!");
        }
        this.getView().invokeOperation("save");
        this.getView().invokeOperation("refresh");
    }

    /** 初始化事件 **/
    @Override
    public void afterLoadData(EventObject e) {
        super.afterLoadData(e);
        setHeadTable();
        statusControl();
    }

    /** 新建事件 **/
    @Override
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        statusControl();
        initDefualt();
    }

    /** 界面关闭事件 **/
    @Override
    public void beforeClosed(BeforeClosedEvent e) {
        super.beforeClosed(e);
        e.setCheckDataChange(true);
    }

    /** 值改变事件 **/
    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();
        if (AOS_ITEMID.equals(name)) {
            aosItemChange();
        }
    }

    /** 全局状态控制 **/
    private void statusControl() {
        // 数据层
        Object aosStatus = this.getModel().getValue("aos_status");
        DynamicObject aosUser = (DynamicObject)this.getModel().getValue("aos_user");
        Object currentUserId = UserServiceHelper.getCurrentUserId();
        Object currentUserName = UserServiceHelper.getUserInfoByID((long)currentUserId).get("name");
        // 图片控制
        // InitPic();
        // 锁住需要控制的字段
        this.getView().setVisible(false, "aos_return");
        // 当前节点操作人不为当前用户 全锁
        if (!aosUser.getPkValue().toString().equals(currentUserId.toString())
            && !"龚凯".equals(currentUserName.toString()) && !"刘中怀".equals(currentUserName.toString())
            && !"程震杰".equals(currentUserName.toString()) && !"陈聪".equals(currentUserName.toString())
            && !"邹地".equals(currentUserName.toString())) {
            this.getView().setEnable(false, "contentpanelflex");
            this.getView().setVisible(false, "bar_save");
            this.getView().setVisible(false, "aos_submit");
            return;
        }
        // 状态控制
        if (APPLY.equals(aosStatus)) {
            this.getView().setVisible(true, "bar_save");
            this.getView().setVisible(true, "aos_submit");
        } else if (DESIGN.equals(aosStatus)) {
            this.getView().setVisible(true, "bar_save");
            this.getView().setVisible(true, "aos_submit");
        } else if (MODEL.equals(aosStatus)) {
            this.getView().setVisible(false, "bar_save");
            this.getView().setVisible(false, "aos_submit");
        } else if (DESCONFIM3D.equals(aosStatus)) {
            Map<String, Object> map = new HashMap<>(16);
            map.put(ClientProperties.Text, new LocaleString("设计确认"));
            this.getView().updateControlMetadata("aos_submit", map);
            this.getView().setVisible(true, "aos_submit");
            this.getView().setEnable(true, "contentpanelflex");
            this.getView().setVisible(true, "bar_save");
        } else if (TRANSLATE.equals(aosStatus)) {
            this.getView().setVisible(false, "aos_submit");
            this.getView().setEnable(false, "contentpanelflex");
            this.getView().setVisible(false, "bar_save");
        } else if (DESTRANS.equals(aosStatus)) {
            Map<String, Object> map = new HashMap<>(16);
            map.put(ClientProperties.Text, new LocaleString("设计确认"));
            this.getView().updateControlMetadata("aos_submit", map);
            this.getView().setVisible(true, "aos_submit");
            this.getView().setEnable(true, "contentpanelflex");
            this.getView().setVisible(false, "bar_save");
        } else if (DMCONFIRM.equals(aosStatus)) {
            Map<String, Object> map = new HashMap<>(16);
            map.put(ClientProperties.Text, new LocaleString("组长确认"));
            this.getView().updateControlMetadata("aos_submit", map);
            this.getView().setVisible(true, "aos_submit");
            this.getView().setEnable(false, "contentpanelflex");
            this.getView().setVisible(false, "bar_save");
        } else if (APPLYCONFIRM.equals(aosStatus)) {
            Map<String, Object> map = new HashMap<>(16);
            map.put(ClientProperties.Text, new LocaleString("申请人确认"));
            this.getView().updateControlMetadata("aos_submit", map);
            this.getView().setVisible(true, "aos_submit");
            this.getView().setVisible(false, "bar_save");
            this.getView().setVisible(true, "aos_return");
            this.getView().setEnable(false, "aos_flexpanelap1");
            this.getView().setEnable(false, "aos_flexpanelap3");
        } else if (END.equals(aosStatus)) {
            this.getView().setVisible(false, "aos_submit");
            this.getView().setVisible(false, "bar_save");
            this.getView().setVisible(false, "aos_close");
            this.getView().setVisible(false, "aos_refresh");
            this.getView().setEnable(false, "contentpanelflex");
        }
    }

    /** 新建设置默认值 **/
    private void initDefualt() {
        long currentUserId = UserServiceHelper.getCurrentUserId();
        List<DynamicObject> mapList = Cux_Common_Utl.GetUserOrg(currentUserId);
        if (mapList != null) {
            if (mapList.get(TWO) != null) {
                this.getModel().setValue("aos_organization1", mapList.get(2).get("id"));
            }
            if (mapList.get(THREE) != null) {
                this.getModel().setValue("aos_organization2", mapList.get(3).get("id"));
            }
        }
    }

    /** 物料值改变 **/
    private void aosItemChange() {
        int currentRowIndex = this.getModel().getEntryCurrentRowIndex("aos_entryentity");
        Object aosItemid = this.getModel().getValue("aos_itemid", currentRowIndex);
        if (aosItemid == null) {
            // 清空值
            this.getModel().setValue("aos_segment3", null, 0);
            this.getModel().setValue("aos_itemname", null, 0);
            this.getModel().setValue("aos_brand", null, 0);
            this.getModel().setValue("aos_developer", null, 0);
            this.getModel().setValue("aos_seting1", null, 0);
            this.getModel().setValue("aos_seting2", null, 0);
            this.getModel().setValue("aos_spec", null, 0);
            this.getModel().setValue("aos_url", null, 0);
            this.getModel().setValue("aos_pic", null, 0);
            this.getModel().setValue("aos_sellingpoint", null, 0);
            // 是否爆品
            this.getModel().setValue("aos_is_saleout", false, 0);
            // 生成3d
            this.getModel().setValue("aos_is_design", false, 0);
            this.getModel().setValue("aos_productstyle_new", null, 0);
            this.getModel().setValue("aos_shootscenes", null, 0);
        } else {
            DynamicObject aosItemidObject = (DynamicObject)aosItemid;
            Object fid = aosItemidObject.getPkValue();
            StringBuilder aosContrybrandStr = new StringBuilder();
            StringBuilder aosOrgtext = new StringBuilder();
            DynamicObject bdMaterial = BusinessDataServiceHelper.loadSingle(fid, "bd_material");
            DynamicObjectCollection aosContryentryS = bdMaterial.getDynamicObjectCollection("aos_contryentry");
            // 获取所有国家品牌 字符串拼接 终止
            Set<String> setBra = new HashSet<>();
            for (DynamicObject aosContryentry : aosContryentryS) {
                DynamicObject aosNationality = aosContryentry.getDynamicObject("aos_nationality");
                String aosNationalitynumber = aosNationality.getString("number");
                if ("IE".equals(aosNationalitynumber)) {
                    continue;
                }
                Object orgId = aosNationality.get("id");
                int osQty = ItemInfoUtil.getItemOsQty(orgId, fid);
                int safeQty = ItemInfoUtil.getSafeQty(orgId);
                // 安全库存 海外库存
                if ("C".equals(aosContryentry.getString("aos_contryentrystatus")) && osQty < safeQty) {
                    continue;
                }
                // 代卖、小于安全库存
                if ("F".equals(aosContryentry.getString("aos_contryentrystatus")) && osQty < safeQty) {
                    continue;
                }
                // 虚拟上架、小于安全库存
                if ("H".equals(aosContryentry.getString("aos_contryentrystatus")) && osQty < safeQty) {
                    continue;
                }
                aosOrgtext.append(aosNationalitynumber).append(";");
                Object obj = aosContryentry.getDynamicObject("aos_contrybrand");
                if (obj == null) {
                    continue;
                }
                String value =
                    aosNationalitynumber + "~" + aosContryentry.getDynamicObject("aos_contrybrand").getString("number");
                String bra = aosContryentry.getDynamicObject("aos_contrybrand").getString("number");
                if (bra != null) {
                    setBra.add(bra);
                }
                if (setBra.size() > 1) {
                    if (!aosContrybrandStr.toString().contains(value)) {
                        aosContrybrandStr.append(value).append(";");
                    }
                } else if (setBra.size() == 1) {
                    if (bra != null) {
                        aosContrybrandStr = new StringBuilder(bra);
                    }
                }
            }
            String itemNumber = bdMaterial.getString("number");
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
            this.getModel().setValue("aos_sub_item", fid, 0);
            this.getModel().setValue("aos_segment3", aosProductno, 0);
            this.getModel().setValue("aos_itemname", aosItemname, 0);
            this.getModel().setValue("aos_brand", aosContrybrandStr.toString(), 0);
            this.getModel().setValue("aos_pic", url, 0);
            // 开发
            this.getModel().setValue("aos_developer", bdMaterial.get("aos_developer"), 0);
            this.getModel().setValue("aos_seting1", bdMaterial.get("aos_seting_cn"), 0);
            this.getModel().setValue("aos_seting2", bdMaterial.get("aos_seting_en"), 0);
            this.getModel().setValue("aos_spec", bdMaterial.get("aos_specification_cn"), 0);
            this.getModel().setValue("aos_url", MKTS3PIC.GetItemPicture(itemNumber), 0);
            this.getModel().setValue("aos_broitem", aosBroitem, 0);
            this.getModel().setValue("aos_orgtext", aosOrgtext.toString(), 0);
            this.getModel().setValue("aos_sellingpoint", bdMaterial.get("aos_sellingpoint"), 0);
            this.getModel().setValue("aos_is_saleout", ProgressUtil.Is_saleout(fid), 0);
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
                this.getModel().setValue("aos_productstyle_new", productStyle.toString(), 0);
            }
            this.getModel().setValue("aos_shootscenes", bdMaterial.getString("aos_shootscenes"), 0);

            // 设置是否已经3d建模
            List<String> designItem = (List<String>)SerializationUtils
                .fromJsonStringToList(getPageCache().get(KEY_CREATEDESIGN), String.class);
            this.getModel().setValue("aos_is_design", designItem.contains(String.valueOf(fid)), 0);

            // 产品类别
            String category = MKTCom.getItemCateNameZH(fid);
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
            // 根据大类中类获取对应营销人员
            if (FndGlobal.IsNotNull(aosCategory1) && FndGlobal.IsNotNull(aosCategory2)) {
                String type = "";
                if (this.getModel().getValue(AOS_TYPE) != null) {
                    type = this.getModel().getValue("aos_type").toString();
                }
                Object orgid = null;
                if (this.getModel().getValue(AOS_ORGID) != null) {
                    orgid = this.getModel().getDataEntity(true).getDynamicObject("aos_orgid").getPkValue();
                }
                String[] fields = new String[] {"aos_designeror", "aos_3d", "aos_eng"};
                DynamicObject aosMktProguser =
                    ProgressUtil.findDesignerByType(orgid, aosCategory1, aosCategory2, type, fields);
                if (aosMktProguser != null) {
                    this.getModel().setValue("aos_designer", aosMktProguser.get("aos_designer"));
                    this.getModel().setValue("aos_dm", aosMktProguser.get("aos_designeror"));
                    this.getModel().setValue("aos_3der", aosMktProguser.get("aos_3d"));
                }
            }
        }
    }

    /** 提交 **/
    public void aosSubmit(DynamicObject dyMain, String type) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            // 先做数据校验判断是否可以提交
            saveControl(dyMain);
            // 根据状态判断当前流程节点
            String aosStatus = dyMain.getString("aos_status");
            switch (aosStatus) {
                case "申请人":
                    submitForNew(dyMain);
                    break;
                case "设计":
                    submitForDesign(dyMain);
                    break;
                case "3D建模":
                    submitFor3D(dyMain);
                    break;
                case "设计确认:翻译":
                    submitForTrans(dyMain);
                    break;
                case "设计确认3D":
                    submitForConfirm(dyMain);
                    break;
                case "组长确认":
                    submitForConfirmDm(dyMain);
                    break;
                case "申请人确认":
                    submitForConfirmReq(dyMain, type);
                    break;
                default:
                    break;
            }
            SaveServiceHelper.save(new DynamicObject[] {dyMain});
            setEntityValue(dyMain);
            FndHistory.Create(dyMain, "提交", aosStatus);
            // 触发提交后事件
            afterSubmit(dyMain, type);
            if (A.equals(type)) {
                this.getView().invokeOperation("refresh");
                statusControl();
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 提交后事件
     *
     * @param mainEntity 设计需求表
     * @param type 提交类型
     */
    public void afterSubmit(DynamicObject mainEntity, String type) {
        // 24-01-16 GK:判断提交后的状态是否为“申请人确认”且 任务类型 = 新品设计；如果满足则继续往下提交
        String status = mainEntity.getString("aos_status");
        String taskType = mainEntity.getString("aos_type");
        if (APPLYCONFIRM.equals(status) && NEWDESIGN.equals(taskType)) {
            // 重新查找单据，然后提交
            mainEntity = BusinessDataServiceHelper.loadSingle(mainEntity.getPkValue(), AOS_MKT_DESIGNREQ);
            aosSubmit(mainEntity, type);
        }
    }

    /** table设置 -物料 or -新建 **/
    private void setHeadTable() {
        String message = "新建";
        DynamicObjectCollection dycEnt =
            this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity");
        if (dycEnt.size() > 0) {
            if (dycEnt.get(0).get(AOS_ITEMID) != null) {
                message = dycEnt.get(0).getDynamicObject("aos_itemid").getString("number");
            }
        }
        LocaleString value = new LocaleString();
        value.setLocaleValue_zh_CN("设计需求表- " + message);
        this.getView().setFormTitle(value);
    }

    private void setPageCache() {
        IPageCache pageCache = getPageCache();
        // 缓存3d建模物料清单
        pageCache.put(KEY_CREATEDESIGN, SerializationUtils.toJsonString(DesignSkuList.getSkuList()));
    }
}
