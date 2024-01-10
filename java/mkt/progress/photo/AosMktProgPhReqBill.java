package mkt.progress.photo;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import common.Cux_Common_Utl;
import common.fnd.FndDate;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndHistory;
import common.fnd.FndMsg;
import common.fnd.FndWebHook;
import common.sal.util.SalUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.bill.BillShowParameter;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.ClientProperties;
import kd.bos.form.FormShowParameter;
import kd.bos.form.ShowType;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.control.Image;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.events.BeforeClosedEvent;
import kd.bos.form.events.BeforeDoOperationEventArgs;
import kd.bos.form.events.HyperLinkClickEvent;
import kd.bos.form.events.HyperLinkClickListener;
import kd.bos.form.operate.FormOperate;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import common.sal.util.QFBuilder;
import mkt.common.GlobalMessage;
import mkt.common.MKTCom;
import mkt.common.MKTS3PIC;
import mkt.common.otel.MmsOtelUtils;
import mkt.progress.ProgressUtil;
import mkt.progress.design.aos_mkt_designreq_bill;
import mkt.progress.design3d.aos_mkt_3design_bill;
import mkt.progress.iface.iteminfo;
import mkt.progress.listing.AosMktListingReqBill;
import mkt.progress.listing.aos_mkt_listingson_bill;

import static mkt.progress.ProgressUtil.Is_saleout;

/**
 * @author aosom
 */
public class AosMktProgPhReqBill extends AbstractBillPlugIn implements ItemClickListener, HyperLinkClickListener {
    /**
     * 拍照需求表标识
     **/
    public final static String AOS_MKT_PHOTOREQ = "aos_mkt_photoreq";
    /**
     * 状态
     **/
    public final static String AOS_STATUS = "aos_status";
    /**
     * 需求类型
     **/
    public final static String AOS_TYPE = "aos_type";
    /**
     * 是否拍照
     **/
    public final static String AOS_PHOTOFLAG = "aos_photoflag";
    /**
     * 是否拍展示视频
     **/
    public final static String AOS_VEDIOFLAG = "aos_vedioflag";
    /**
     * 不拍照原因
     **/
    public final static String AOS_REASON = "aos_reason";
    /**
     * 同货号
     **/
    public final static String AOS_SAMEITEMID = "aos_sameitemid";
    /**
     * 需求类型
     **/
    public final static String AOS_REQTYPE = "aos_reqtype";
    /**
     * 物料信息
     **/
    public final static String AOS_ITEMID = "aos_itemid";
    /**
     * 品名
     **/
    public final static String AOS_ITEMNAME = "aos_itemname";
    /**
     * 品牌
     **/
    public final static String AOS_CONTRYBRAND = "aos_contrybrand";
    /**
     * 是否新产品
     **/
    public final static String AOS_NEWITEM = "aos_newitem";
    /**
     * 是否新供应商
     **/
    public final static String AOS_NEWVENDOR = "aos_newvendor";
    /**
     * 合同号
     **/
    public final static String AOS_PONUMBER = "aos_ponumber";
    /**
     * 行号
     **/
    public final static String AOS_LINENUMBER = "aos_linenumber";
    /**
     * 产品规格
     **/
    public final static String AOS_SPECIFICATION = "aos_specification";
    /**
     * 产品属性1
     **/
    public final static String AOS_SETING1 = "aos_seting1";
    /**
     * 产品属性2
     **/
    public final static String AOS_SETING2 = "aos_seting2";
    /**
     * 产品卖点
     **/
    public final static String AOS_SELLINGPOINT = "aos_sellingpoint";
    /**
     * 供应商
     **/
    public final static String AOS_VENDOR = "aos_vendor";
    /**
     * 城市
     **/
    public final static String AOS_CITY = "aos_city";
    /**
     * 工厂联系人
     **/
    public final static String AOS_CONTACT = "aos_contact";
    /**
     * 地址
     **/
    public final static String AOS_ADDRESS = "aos_address";
    /**
     * 联系电话
     **/
    public final static String AOS_PHONE = "aos_phone";
    /**
     * 拍照地点
     **/
    public final static String AOS_PHSTATE = "aos_phstate";
    /**
     * 采购
     **/
    public final static String AOS_POER = "aos_poer";
    /**
     * 开发
     **/
    public final static String AOS_DEVELOPER = "aos_developer";
    /**
     * 跟单
     **/
    public final static String AOS_FOLLOWER = "aos_follower";
    /**
     * 单据编号
     **/
    public final static String BILLNO = "billno";
    /**
     * 出运日期
     **/
    public final static String AOS_SHIPDATE = "aos_shipdate";
    /**
     * 白底摄影师
     **/
    public final static String AOS_WHITEPH = "aos_whiteph";
    /**
     * 实景摄影师
     **/
    public final static String AOS_ACTPH = "aos_actph";
    /**
     * 摄像师
     **/
    public final static String AOS_VEDIOR = "aos_vedior";
    /**
     * 当前节点操作人
     **/
    public final static String AOS_USER = "aos_user";
    /**
     * 源单ID
     **/
    public final static String AOS_SOURCEID = "aos_sourceid";
    /**
     * 设计师
     **/
    public final static String AOS_DESIGNER = "aos_designer";
    /**
     * 2
     **/
    public final static int TWO = 2;
    /**
     * 3
     **/
    public final static int THREE = 3;
    /**
     * 到港日期
     **/
    public final static String AOS_ARRIVALDATE = "aos_arrivaldate";
    /**
     * 系统管理员
     **/
    public final static String SYSTEM = Cux_Common_Utl.SYSTEM;
    /**
     * 大类
     **/
    public final static String AOS_CATEGORY1 = "aos_category1";
    /**
     * 中类
     **/
    public final static String AOS_CATEGORY2 = "aos_category2";
    /**
     * 小类
     **/
    public final static String AOS_CATEGORY3 = "aos_category3";
    /**
     * 紧急程度
     **/
    public final static String AOS_URGENT = "aos_urgent";
    private static final Tracer TRACER = MmsOtelUtils.getTracer(AosMktProgPhReqBill.class, RequestContext.get());

    /**
     * 有进行中拍照需求表，不允许再新增拍照需求流程
     **/
    private static void newControl(DynamicObject dyMain) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            String fid = dyMain.getPkValue().toString();
            Object aosType = dyMain.get("aos_type");
            boolean aosSonflag = dyMain.getBoolean("aos_sonflag");
            DynamicObject aosItemid = dyMain.getDynamicObject("aos_itemid");
            // 本单不需要拍照的话则不做校验
            if (!dyMain.getBoolean(sign.photoFlag.name) || sign.video.name.equals(aosType)) {
                return;
            }
            // 子流程不做判断
            if (!aosSonflag && FndGlobal.IsNotNull(aosItemid)) {
                QFilter filterItemid = new QFilter("aos_itemid", "=", aosItemid.getPkValue().toString());
                QFilter filterStatus = new QFilter("aos_status", "!=", "已完成");
                QFilter filterStatus2 = new QFilter("aos_status", "!=", "不需拍");
                QFilter filterStatus3 = new QFilter("aos_type", "!=", "视频");
                QFilter filterSon = new QFilter("aos_sonflag", "=", false);
                QFilter filterId = new QFilter("id", "!=", fid);
                QFilter filterType = new QFilter("aos_photoflag", "=", true);
                QFilter[] filters = new QFilter[] {filterStatus3, filterItemid, filterStatus, filterId, filterType,
                    filterSon, filterStatus2};
                DynamicObject aosMktPhotoreq = QueryServiceHelper.queryOne("aos_mkt_photoreq", "billno", filters);
                if (aosMktPhotoreq != null) {
                    throw new FndError("有进行中拍照需求表" + aosMktPhotoreq.getString("billno") + "，不允许再新增拍照需求流程!");
                }
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    public static Map<String, String> getSkuCategory(String sku) {
        QFilter qFilterSku = new QFilter("material", "=", sku);
        QFilter qFilterJb = new QFilter("standard.number", "=", "JBFLBZ");
        DynamicObject dyGroup = QueryServiceHelper.queryOne("bd_materialgroupdetail",
            "group.name as name,group.number as number", new QFilter[] {qFilterSku, qFilterJb});
        return getCategory(dyGroup.get("number").toString(), dyGroup.get("name").toString());
    }

    /**
     * 获取大、中、小产品类别
     *
     * @return Map<String, String>
     */
    public static Map<String, String> getCategory(String group3Number, String group3Name) {
        try {
            Map<String, String> category = new HashMap<>(16);
            String[] number = org.apache.commons.lang.StringUtils.split(group3Number, ",");
            String[] name = org.apache.commons.lang.StringUtils.split(group3Name, ",");
            category.put("aos_category_code1", number[0]);
            category.put("aos_category_stat1", name[0]);
            int length = 2;
            if (number.length > length && name.length > 1) {
                category.put("aos_category_code2", number[1]);
                category.put("aos_category_stat2", name[1]);
            }
            if (number.length > length && name.length > 1) {
                category.put("aos_category_code3", number[2]);
                category.put("aos_category_stat3", name[2]);
            }
            return category;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 通用控制校验
     **/
    private static void saveControl(DynamicObject dyMain) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            FndError fndError = new FndError();
            // 数据层
            Object aosStatus = dyMain.get(AOS_STATUS);
            boolean aosPhotoFlag = dyMain.getBoolean(AOS_PHOTOFLAG);
            boolean aosVedioFlag = dyMain.getBoolean(AOS_VEDIOFLAG);
            Object aosReason = dyMain.get(AOS_REASON);
            Object aosReqtype = dyMain.get(AOS_REQTYPE);
            Object aosItemid = dyMain.get(AOS_ITEMID);
            Object aosPhstate = dyMain.get(AOS_PHSTATE);
            Object aosSameitemid = dyMain.get("aos_sameitemid");
            Object aosPoerO = dyMain.get(AOS_POER);
            Object aosDeveloperO = dyMain.get(AOS_DEVELOPER);
            Object aosFollowerO = dyMain.get(AOS_FOLLOWER);
            Object aosWhitephO = dyMain.get(AOS_WHITEPH);
            Object aosActphO = dyMain.get(AOS_ACTPH);
            Object aosVediorO = dyMain.get(AOS_VEDIOR);
            Object aosDesignerO = dyMain.get(AOS_DESIGNER);
            Object aos3dO = dyMain.get("aos_3d");
            Object aosVediotype = dyMain.get("aos_vediotype");
            boolean strightCut = false;
            Boolean aos3dflag = dyMain.getBoolean("aos_3dflag");
            Object aos3dReason = dyMain.get("aos_3d_reason");
            if (sign.shortCut.name.equals(aosVediotype) && aosVedioFlag) {
                strightCut = true;
            }
            // 校验 视频优化类型为剪辑时 只勾选是否拍展示视频
            boolean cod = (sign.shortCut.name.equals(aosVediotype) && (!aosVedioFlag || aosPhotoFlag));
            if (cod) {
                fndError.add("视频优化类型为剪辑时 只允许勾选是否拍展示视频!");
            }
            // 校验 新建时是否拍照、是否拍展示视频不能同时为false
            cod = ("新建".equals(aosStatus) && !aosPhotoFlag && !aosVedioFlag
                && (aosReason == null || "".equals(aosReason)));
            if (cod) {
                fndError.add("是否拍照与是否拍视频同时为否时不拍照原因必填!");
            }
            // 新增、开发/采购确认 是否拍照选择否时，不拍照原因必填
            cod = (("新建".equals(aosStatus) || "开发/采购确认".equals(aosStatus)) && !aosPhotoFlag
                && (FndGlobal.IsNull(aosReason)));
            if (cod) {
                fndError.add("是否拍照为否时不拍照原因必填!");
            }
            // 校验 新建时如果选择了拍照或者拍视频 拍照地点必填 非视频剪辑类型才做校验
            cod = ("开发/采购确认".equals(aosStatus) && (aosPhotoFlag || aosVedioFlag)
                && (aosPhstate == null || "".equals(aosPhstate)) && !strightCut);
            if (cod) {
                fndError.add("选择了拍照或者拍视频则拍照地点必填!");
            }
            // 校验 不拍照原因在是否拍照为否时必填
            cod = (!aosPhotoFlag && (aosReason == null || "".equals(aosReason)));
            if (cod) {
                fndError.add("不拍照原因在是否拍照为否时必填!");
            }
            // 校验 同货号在是否拍照为否不拍照原因=同XX货号时必填
            cod = (!aosPhotoFlag && (aosSameitemid == null && "同XX货号".equals(aosReason)));
            if (cod) {
                fndError.add("同货号在是否拍照为否,不拍照原因为同XX货号时必填!");
            }
            // 校验 需求类型在新建状态下必填
            cod = ("新建".equals(aosStatus) && (aosReqtype == null || "".equals(aosReqtype)));
            if (cod) {
                fndError.add("需求类型必填!");
            }
            // 校验 物料信息 新建状态下必填
            if (sign.newStatus.name.equals(aosStatus) && (aosItemid == null)) {
                fndError.add("物料信息必填!");
            }
            // 校验 转3D需填写原因
            if (aos3dflag && Cux_Common_Utl.IsNull(aos3dReason)) {
                fndError.add("转3D需填写原因!");
            }
            // 校验人员信息是否都存在
            if (Cux_Common_Utl.IsNull(aosPoerO)) {
                fndError.add("采购员必填!");
            }
            if (Cux_Common_Utl.IsNull(aosDeveloperO)) {
                fndError.add("开发员必填!");
            }
            if (Cux_Common_Utl.IsNull(aosFollowerO)) {
                fndError.add("跟单员必填!");
            }
            if (Cux_Common_Utl.IsNull(aosWhitephO)) {
                fndError.add("白底摄影师必填!");
            }
            if (Cux_Common_Utl.IsNull(aosActphO)) {
                fndError.add("实景摄影师必填!");
            }
            if (Cux_Common_Utl.IsNull(aosVediorO)) {
                fndError.add("摄像师必填!");
            }
            if (Cux_Common_Utl.IsNull(aos3dO)) {
                fndError.add("3D设计师必填!");
            }
            if (Cux_Common_Utl.IsNull(aosDesignerO)) {
                fndError.add("设计师必填!");
            }
            // 校验 跟单 行政组别
            try {
                if (dyMain.get(AOS_FOLLOWER) != null) {
                    DynamicObject aosFollower = dyMain.getDynamicObject(AOS_FOLLOWER);
                    List<DynamicObject> mapList = Cux_Common_Utl.GetUserOrg(aosFollower.getPkValue());
                    if (mapList != null) {
                        if (mapList.get(TWO) != null) {
                            mapList.get(TWO).get("id");
                        }
                        if (mapList.get(THREE) != null) {
                            mapList.get(THREE).get("id");
                        }
                    }
                }
            } catch (Exception ex) {
                fndError.add("跟单行政组织级别有误!");
            }
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
     * 新建状态下提交
     *
     * @param dyMain 单据对象
     **/
    public static void submitForNew(DynamicObject dyMain) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            // 异常参数
            FndError fndError = new FndError();
            // 数据层
            Object aosNewItem = dyMain.get(AOS_NEWITEM);
            Object aosPoer = dyMain.get(AOS_POER);
            Object aosDeveloper = dyMain.get(AOS_DEVELOPER);
            Object aosPhotoFlag = dyMain.get(AOS_PHOTOFLAG);
            Object aosVedioFlag = dyMain.get(AOS_VEDIOFLAG);
            Object aosSourceid = dyMain.get(AOS_SOURCEID);
            Object aosBillno = dyMain.get(BILLNO);
            // 当前界面主键
            Object fid = dyMain.get("id");
            Object aosVedioType = dyMain.get("aos_vediotype");
            boolean strightCut = false;
            Object aosPicdesc;
            Object aosVeddesc;
            DynamicObjectCollection aosEntryentityS = dyMain.getDynamicObjectCollection("aos_entryentity6");
            if (aosEntryentityS.size() > 0) {
                aosPicdesc = aosEntryentityS.get(0).get("aos_reqsupp");
            } else {
                aosPicdesc = dyMain.getDynamicObjectCollection("aos_entryentity").get(1).get("aos_picdesc");
            }
            DynamicObjectCollection aosEntryentity1S = dyMain.getDynamicObjectCollection("aos_entryentity1");
            if (!Cux_Common_Utl.IsNull(aosEntryentity1S)) {
                aosVeddesc = aosEntryentity1S.get(0).get("aos_veddesc");
            } else {
                aosVeddesc = dyMain.getDynamicObjectCollection("aos_entryentity1").get(1).get("aos_veddesc");
            }
            // 校验
            if ((Boolean)aosNewItem && aosDeveloper == null) {
                fndError.add("新产品开发为空,流程无法流转!");
            } else if (!(Boolean)aosNewItem && aosPoer == null) {
                fndError.add("非新产品采购为空,流程无法流转!");
            }
            // 校验 申请人需求
            if ((Boolean)aosPhotoFlag && Cux_Common_Utl.IsNull(aosPicdesc)) {
                fndError.add("新建状态下 若勾选拍照 则申请人拍照需求必填!");
            }
            if ((Boolean)aosVedioFlag && Cux_Common_Utl.IsNull(aosVeddesc)) {
                fndError.add("新建状态下 若勾选视频 则申请人视频需求必填!");
            }
            if (fndError.getCount() > 0) {
                throw fndError;
            }
            if (sign.shortCut.name.equals(aosVedioType) && (Boolean)aosVedioFlag) {
                strightCut = true;
            }
            // 回写拍照任务清单
            if (QueryServiceHelper.exists(sign.photoList.name, aosSourceid)) {
                DynamicObject aosMktPhotolist = BusinessDataServiceHelper.loadSingle(aosSourceid, "aos_mkt_photolist");
                if ((Boolean)aosPhotoFlag) {
                    aosMktPhotolist.set("aos_phstatus", "开发/采购确认");
                }
                if ((Boolean)aosVedioFlag) {
                    if (strightCut) {
                        aosMktPhotolist.set("aos_vedstatus", "视频拍摄");
                    } else {
                        aosMktPhotolist.set("aos_vedstatus", "开发/采购确认");
                    }
                }
                OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
                    new DynamicObject[] {aosMktPhotolist}, OperateOption.create());
                if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                    throw new FndError("拍照任务清单保存失败!");
                }
            }
            String messageId = null;
            String aosStatus;
            Object aosUser;
            String message = "拍照需求表-开发/采购确认";
            if ((Boolean)aosNewItem) {
                // 勾选新产品流转给开发
                aosUser = aosDeveloper;
                if (aosDeveloper != null) {
                    messageId = ((DynamicObject)aosDeveloper).getPkValue().toString();
                }
            } else {
                // 不勾新产品流转给采购
                aosUser = aosPoer;
                if (aosPoer != null) {
                    messageId = ((DynamicObject)aosPoer).getPkValue().toString();
                }
            }
            if (strightCut) {
                dyMain.set(AOS_VEDIOR, UserServiceHelper.getCurrentUserId());
                // 直接流转给摄像
                aosUser = UserServiceHelper.getCurrentUserId();
                messageId = String.valueOf(UserServiceHelper.getCurrentUserId());
                aosStatus = "视频剪辑";
                dyMain.set(AOS_TYPE, "视频");
                message = "拍照需求表-视频拍摄";
            } else {
                aosStatus = "开发/采购确认";
            }
            dyMain.set(AOS_USER, aosUser);
            // 设置单据流程状态
            dyMain.set(AOS_STATUS, aosStatus);
            OperationServiceHelper.executeOperate("save", AOS_MKT_PHOTOREQ, new DynamicObject[] {dyMain},
                OperateOption.create());
            // 发送消息
            MKTCom.SendGlobalMessage(messageId, AOS_MKT_PHOTOREQ, String.valueOf(fid), String.valueOf(aosBillno),
                message);
            GlobalMessage.SendMessage(aosBillno + "-拍照需求单据待处理", messageId);
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 开发/采购确认 状态下提交
     **/
    private static void submitForProductConfirm(DynamicObject dyMain) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            // 异常参数
            FndError fndError = new FndError();
            // 数据层
            Object aosSourceid = dyMain.get(AOS_SOURCEID);
            Object aosPhotoFlag = dyMain.get(AOS_PHOTOFLAG);
            Object aosVedioFlag = dyMain.get(AOS_VEDIOFLAG);
            Object aosFollower = dyMain.get(AOS_FOLLOWER);
            Object aosPoNumber = dyMain.get(AOS_PONUMBER);
            String aosCategory1 = dyMain.getString("aos_category1");
            String aosCategory2 = dyMain.getString("aos_category2");
            String aosCategory3 = dyMain.getString("aos_category3");
            String aosItemname = dyMain.getString("aos_itemname");
            DynamicObject aosItemid = dyMain.getDynamicObject("aos_itemid");
            // 子流程不做判断
            newControl(dyMain);
            DynamicObjectCollection dycPhoto = dyMain.getDynamicObjectCollection("aos_entryentity6");
            Object aosReqsupp;
            Object aosVeddesc;
            if (dycPhoto.size() > 0) {
                aosReqsupp = dyMain.getDynamicObjectCollection("aos_entryentity6").get(0).get("aos_devsupp");
            } else {
                aosReqsupp = dyMain.getDynamicObjectCollection("aos_entryentity").get(1).get("aos_picdesc");
            }
            aosVeddesc = dyMain.getDynamicObjectCollection("aos_entryentity1").get(1).get("aos_veddesc");
            // 校验
            if ((Boolean)aosPhotoFlag && Cux_Common_Utl.IsNull(aosReqsupp)) {
                fndError.add("若勾选拍照 则拍照需求必填!");
            }
            if ((Boolean)aosVedioFlag && Cux_Common_Utl.IsNull(aosVeddesc)) {
                fndError.add("若勾选视频 则视频需求必填!");
            }
            // 拍照地点
            Object aosPhstate = dyMain.get("aos_phstate");
            // 新产品
            Boolean aosNewitem = dyMain.getBoolean("aos_newitem");
            // 新供应商
            Boolean aosNewvendor = dyMain.getBoolean("aos_newvendor");
            // 无法建模的产品不允许改成工厂简拍
            if (sign.snapShot.name.equals(aosPhstate)) {
                boolean cond1 = QueryServiceHelper.exists("aos_sealsample",
                    new QFilter("aos_item.id", QCP.equals, aosItemid.getPkValue().toString())
                        .and("aos_contractnowb", QCP.equals, aosPoNumber).and("aos_model", QCP.equals, "否").toArray());
                boolean cond2 = QueryServiceHelper.exists("aos_sealsample",
                    new QFilter("aos_item.id", QCP.equals, aosItemid.getPkValue().toString())
                        .and("aos_contractnowb", QCP.equals, aosPoNumber).and("aos_model", QCP.equals, "").toArray());
                boolean cond3 = QueryServiceHelper.exists("aos_sealsample",
                    new QFilter("aos_item.id", QCP.equals, aosItemid.getPkValue().toString())
                        .and("aos_contractnowb", QCP.equals, aosPoNumber).toArray());
                boolean cond4 = QueryServiceHelper.exists("aos_mkt_3dselect",
                    new QFilter("aos_category1", QCP.equals, aosCategory1)
                        .and("aos_category2", QCP.equals, aosCategory2).and("aos_category3", QCP.equals, aosCategory3)
                        .and("aos_name", QCP.equals, aosItemname).toArray());
                boolean cond = (cond1 || cond2 || (!cond3 && !cond4));
                if (cond) {
                    fndError.add("无法建模的产品不允许改成工厂简拍!");
                }
            }
            // 拍照地点=工厂简拍，且新产品=是或新供应商=是，开发/采购节点提交时增加判断：合同号+SKU对应的封样流程大货样的产品整体图有图，否则提示“3D建模，必须有大货封样图片！”；
            // 24-1-2 gk 当是否拍照="否",是否拍展示视频="否"时,无需校验封祥单图片
            // 是否拍照
            boolean photoflag = dyMain.getBoolean("aos_photoflag");
            // 是否拍展示视频
            boolean vedioflag = dyMain.getBoolean("aos_vedioflag");
            if (photoflag || vedioflag) {
                boolean cond = (sign.snapShot.name.equals(aosPhstate) && (aosNewitem || aosNewvendor));
                if (cond) {
                    boolean isSealSample = QueryServiceHelper.exists("aos_sealsample",
                        new QFilter("aos_item.id", QCP.equals, aosItemid.getPkValue().toString())
                            .and("aos_contractnowb", QCP.equals, aosPoNumber).and("aos_islargeseal", QCP.equals, "是")
                            .and("aos_largetext", QCP.equals, "").toArray());
                    if (isSealSample) {
                        fndError.add("3D建模，必须有大货封样图片!");
                    }
                    isSealSample = QueryServiceHelper.exists("aos_sealsample",
                        new QFilter("aos_item.id", QCP.equals, aosItemid.getPkValue())
                            .and("aos_contractnowb", QCP.equals, aosPoNumber).and("aos_islargeseal", QCP.equals, "")
                            .and("aos_largetext", QCP.equals, "").toArray());
                    if (isSealSample) {
                        fndError.add("3D建模，必须有大货封样图片!");
                    }
                }
            }
            if (fndError.getCount() > 0) {
                throw fndError;
            }
            // 不拍照不拍视频 状态直接调整为不需拍 并进入不拍照任务清单
            if (!(Boolean)aosPhotoFlag && !(Boolean)aosVedioFlag) {
                // 生成不拍照任务清单
                aos_mkt_nophoto_bill.create_noPhotoEntity(dyMain);
            } else {
                // 校验跟单是否为空
                if (aosFollower == null) {
                    fndError.add("跟单为空,流程无法流转!");
                }
                // 校验采购订单号是否为空
                if (aosPoNumber == null || "".equals(aosPoNumber)) {
                    fndError.add("开发/采购确认节点,采购订单号不允许为空!");
                } else {
                    // 校验采购订单号、行号是否存在
                    QFilter filterBillno = new QFilter("billno", "=", aosPoNumber);
                    QFilter[] filters = new QFilter[] {filterBillno};
                    DynamicObject aosPurcontract = QueryServiceHelper.queryOne("aos_purcontract", "id", filters);
                    if (aosPurcontract == null) {
                        fndError.add("购销合同不存在!");
                    }
                }
                if (fndError.getCount() > 0) {
                    throw fndError;
                }
            }
            // 回写拍照任务清单
            if (QueryServiceHelper.exists(sign.photoList.name, aosSourceid)) {
                DynamicObject aosMktPhotolist = BusinessDataServiceHelper.loadSingle(aosSourceid, "aos_mkt_photolist");
                if (!(Boolean)aosPhotoFlag && !(Boolean)aosVedioFlag) {
                    aosMktPhotolist.set("aos_phstatus", "不需拍");
                    aosMktPhotolist.set("aos_vedstatus", "不需拍");
                } else {
                    aosMktPhotolist.set("aos_phstatus", "跟单提样");
                    aosMktPhotolist.set("aos_vedstatus", "跟单提样");
                }
                OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
                    new DynamicObject[] {aosMktPhotolist}, OperateOption.create());
                if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                    throw new FndError("拍照任务清单保存失败!");
                }
            }
            // 执行保存操作
            dyMain.set(AOS_STATUS, "跟单提样");
            if (!(Boolean)aosPhotoFlag && !(Boolean)aosVedioFlag) {
                // 拍照需求表状态调整为不需拍
                dyMain.set(AOS_STATUS, "不需拍");
                // 将流程人员设置为系统管理员
                dyMain.set(AOS_USER, SYSTEM);
            } else {
                // 流转给跟单
                dyMain.set(AOS_USER, aosFollower);
                // 样品入库通知单 在样品入库通知单中通知跟单
                generateRcv(dyMain);
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 白底拍摄 状态下提交
     **/
    private static void submitForWhite(DynamicObject dyMain) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            // 异常参数
            FndError fndError = new FndError();
            // 数据层
            Object aosSourceid = dyMain.get(AOS_SOURCEID);
            Object aosBillno = dyMain.get(BILLNO);
            Object aosActph = dyMain.get(AOS_ACTPH);
            Object aos3d = dyMain.get("aos_3d");
            // 当前界面主键
            Object fid = dyMain.getPkValue();
            boolean aos3dflag = dyMain.getBoolean("aos_3dflag");
            String messageId = null;
            String messageStr;
            String status;
            Date now = new Date();
            // 校验
            if (aosActph == null && !aos3dflag) {
                fndError.add("实景摄影师为空,视频流程无法流转!");
            }
            if (fndError.getCount() > 0) {
                throw fndError;
            }
            if (aos3dflag) {
                DynamicObject aosMktPhotoreq = BusinessDataServiceHelper.loadSingle(fid, "aos_mkt_photoreq");
                // 生成3D确认单
                aos_mkt_3design_bill.Generate3Design(aosMktPhotoreq);
                status = "3D建模";
                messageStr = "拍照需求表-3D建模";
                messageId = String.valueOf(((DynamicObject)aos3d).getPkValue());
            } else {
                // 回写拍照任务清单
                status = "实景拍摄";
                messageStr = "拍照需求表-实景拍摄";
                if (aosActph != null) {
                    messageId = String.valueOf(((DynamicObject)aosActph).getPkValue());
                }
            }
            if (QueryServiceHelper.exists(sign.photoList.name, aosSourceid)) {
                DynamicObject aosMktPhotolist = BusinessDataServiceHelper.loadSingle(aosSourceid, "aos_mkt_photolist");
                aosMktPhotolist.set("aos_phstatus", status);
                aosMktPhotolist.set("aos_whitedate", now);
                OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
                    new DynamicObject[] {aosMktPhotolist}, OperateOption.create());
                if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                    throw new FndError("拍照任务清单保存失败!");
                }
            }
            // 执行保存操作
            dyMain.set(AOS_STATUS, status);
            dyMain.set(AOS_USER, messageId);
            // 设置白底完成日期为当前日期
            dyMain.set("aos_whitedate", now);
            dyMain.set("aos_whiteph", RequestContext.get().getCurrUserId());
            DynamicObject query = QueryServiceHelper.queryOne(AOS_MKT_PHOTOREQ, "id",
                new QFilter("billno", QCP.equals, aosBillno).and("aos_type", QCP.equals, "视频").toArray());
            if (FndGlobal.IsNotNull(query)) {
                DynamicObject dyn = BusinessDataServiceHelper.loadSingle(query.get("id"), AOS_MKT_PHOTOREQ);
                // 设置白底完成日期为当前日期
                dyn.set("aos_whitedate", now);
                dyn.set("aos_whiteph", RequestContext.get().getCurrUserId());
                OperationServiceHelper.executeOperate("save", AOS_MKT_PHOTOREQ, new DynamicObject[] {dyn},
                    OperateOption.create());
            }
            // 发送消息
            MKTCom.SendGlobalMessage(messageId, AOS_MKT_PHOTOREQ, String.valueOf(fid), String.valueOf(aosBillno),
                messageStr);
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 实景拍摄 状态下提交
     **/
    private static void submitForAct(DynamicObject dyMain) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            // 异常参数
            // 数据层
            Object aosSourceid = dyMain.get(AOS_SOURCEID);
            Object aosBillno = dyMain.get(BILLNO);
            Object aosDeveloper = dyMain.get(AOS_DEVELOPER);
            DynamicObject aosItemid = dyMain.getDynamicObject(AOS_ITEMID);
            Object aosItemName = dyMain.get(AOS_ITEMNAME);
            Object fid = dyMain.getPkValue();
            Object aos3d = dyMain.get("aos_3d");
            String sku = dyMain.getDynamicObject("aos_itemid").getString("number");
            String poNumber = dyMain.getString("aos_ponumber");
            boolean aos3dflag = dyMain.getBoolean("aos_3dflag");
            String messageId;
            String messageStr;
            String status;
            Date now = new Date();
            // 是否生成抠图任务表
            boolean createPs = false;
            // 校验
            if (aos3dflag) {
                DynamicObject aosMktPhotoreq = BusinessDataServiceHelper.loadSingle(fid, "aos_mkt_photoreq");
                // 生成3D确认单
                aos_mkt_3design_bill.Generate3Design(aosMktPhotoreq);
                status = "3D建模";
                messageStr = "拍照需求表-3D建模";
                messageId = String.valueOf(((DynamicObject)aos3d).getPkValue());
                // 拍照地点
                String aosPhstate = dyMain.getString("aos_phstate");
                if (sign.goShot.name.equals(aosPhstate)) {
                    createPs = true;
                }
            } else {
                // 回写拍照任务清单
                status = "开发/采购确认图片";
                messageStr = "拍照需求表-开发/采购确认图片";
                messageId = String.valueOf(((DynamicObject)aosDeveloper).getPkValue());
                createPs = true;
            }
            if (createPs) {
                // 生成抠图任务清单
                DynamicObject aosMktPslist = BusinessDataServiceHelper.newDynamicObject("aos_mkt_pslist");
                aosMktPslist.set("billstatus", "A");
                aosMktPslist.set("aos_initdate", now);
                aosMktPslist.set("aos_reqno", aosBillno);
                aosMktPslist.set("aos_sourceid", fid);
                aosMktPslist.set(AOS_ITEMID, aosItemid);
                aosMktPslist.set(AOS_ITEMNAME, aosItemName);
                aosMktPslist.set(AOS_DESIGNER, ProgressUtil.findPSlistDesign(aosItemid.getPkValue()));
                // 出运日期 到港日期 入库日期
                Date shippingDate = ProgressUtil.getShippingDate(poNumber, sku);
                Date arrivalDate = ProgressUtil.getArrivalDate(poNumber, sku);
                Date rcvDate = ProgressUtil.getRcvDate(poNumber, sku);
                Date today = new Date();
                aosMktPslist.set(AOS_SHIPDATE, shippingDate);
                aosMktPslist.set(AOS_ARRIVALDATE, arrivalDate);
                aosMktPslist.set("aos_rcvdate", rcvDate);
                // 紧急状态
                String aosEmstatus = null;
                if (FndGlobal.IsNotNull(rcvDate) && rcvDate.compareTo(today) <= 0) {
                    aosEmstatus = "非常紧急";
                } else if (FndGlobal.IsNotNull(arrivalDate) && arrivalDate.compareTo(today) <= 0) {
                    aosEmstatus = "紧急";
                } else if (FndGlobal.IsNotNull(shippingDate)) {
                    int days = FndDate.GetBetweenDays(today, shippingDate);
                    aosEmstatus = "出运" + days + "天";
                }
                aosMktPslist.set("aos_emstatus", aosEmstatus);
                aosMktPslist.set("aos_status", "已完成");
                aosMktPslist.set("aos_startdate", new Date());
                aosMktPslist.set("aos_enddate", new Date());
                OperationResult operationrstps = OperationServiceHelper.executeOperate("save", "aos_mkt_pslist",
                    new DynamicObject[] {aosMktPslist}, OperateOption.create());
                if (operationrstps.getValidateResult().getValidateErrors().size() != 0) {
                    throw new FndError("抠图任务清单保存失败!");
                }
            }
            // 回写拍照任务清单
            if (QueryServiceHelper.exists(sign.photoList.name, aosSourceid)) {
                DynamicObject aosMktPhotolist = BusinessDataServiceHelper.loadSingle(aosSourceid, "aos_mkt_photolist");
                aosMktPhotolist.set("aos_phstatus", status);
                // 同步实景完成日期为当前日期
                aosMktPhotolist.set("aos_actdate", now);
                OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
                    new DynamicObject[] {aosMktPhotolist}, OperateOption.create());
                if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                    throw new FndError("拍照任务清单保存失败!");
                }
            }
            // 执行保存操作
            dyMain.set("aos_actph", RequestContext.get().getCurrUserId());
            dyMain.set(AOS_STATUS, status);
            dyMain.set(AOS_USER, messageId);
            dyMain.set("aos_actdate", now);
            DynamicObject query = QueryServiceHelper.queryOne(AOS_MKT_PHOTOREQ, "id",
                new QFilter("billno", QCP.equals, aosBillno).and("aos_type", QCP.equals, "视频").toArray());
            if (FndGlobal.IsNotNull(query)) {
                DynamicObject dyn = BusinessDataServiceHelper.loadSingle(query.get("id"), AOS_MKT_PHOTOREQ);
                // 设置实景完成日期为当前日期
                dyMain.set("aos_actdate", now);
                dyMain.set("aos_actph", RequestContext.get().getCurrUserId());
                OperationServiceHelper.executeOperate("save", AOS_MKT_PHOTOREQ, new DynamicObject[] {dyn},
                    OperateOption.create());
            }
            // 发送消息
            MKTCom.SendGlobalMessage(messageId, AOS_MKT_PHOTOREQ, String.valueOf(fid), String.valueOf(aosBillno),
                messageStr);
            GlobalMessage.SendMessage(aosBillno + "-拍照需求单据待处理", messageId);
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 拍照类型新品结束后 生成设计需求表
     *
     * @param dyn 单据对象
     **/
    public static void generateDesign(DynamicObject dyn) {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            // 数据层
            Object aosShipDate = dyn.get(AOS_SHIPDATE);
            Object aosBillno = dyn.get(BILLNO);
            // 判断是否已经生成了设计需求表
            QFBuilder builder = new QFBuilder("aos_orignbill", "=", aosBillno);
            DynamicObject exists = QueryServiceHelper.queryOne("aos_mkt_designreq", "id", builder.toArray());
            if (FndGlobal.IsNotNull(exists)) {
                return;
            }
            // 当前界面主键
            Object reqFid = dyn.getPkValue();
            Object aosItemId = dyn.get(AOS_ITEMID);
            Object aosRequireby = dyn.get("aos_requireby");
            DynamicObject aosDeveloper = dyn.getDynamicObject(AOS_DEVELOPER);
            Object aosDeveloperId = aosDeveloper.getPkValue();
            DynamicObject aosItemidObject = (DynamicObject)aosItemId;
            Object fid = aosItemidObject.getPkValue();
            String messageId = String.valueOf(aosDeveloperId);
            Object aosRequirebyid = ((DynamicObject)aosRequireby).getPkValue();
            boolean aos3dflag = dyn.getBoolean("aos_3dflag");
            String aosPhstate = dyn.getString("aos_phstate");
            // 判断对应抠图任务清单是否为已完成
            if (!aos3dflag && !aosPhstate.equals(sign.snapShot.name)) {
                QFilter filterStatus = new QFilter("aos_status", QCP.equals, "已完成");
                QFilter filterId = new QFilter("aos_sourceid", "=", reqFid);
                QFilter[] filtersPs = new QFilter[] {filterStatus, filterId};
                DynamicObject aosMktPslist = QueryServiceHelper.queryOne("aos_mkt_pslist", "id", filtersPs);
                // 如果不为已完成 则不生成设计需求表 等待抠图任务清单完成后再生成设计需求表
                if (aosMktPslist == null) {
                    return;
                }
            }
            // 初始化
            DynamicObject aosMktDesignreq = BusinessDataServiceHelper.newDynamicObject("aos_mkt_designreq");
            aosMktDesignreq.set("aos_requiredate", new Date());
            String category = (String)SalUtil.getCategoryByItemId(String.valueOf(fid)).get("name");
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
                QFilter[] filters = new QFilter[] {filterCategory1, filterCategory2};
                String selectStr = "aos_designer,aos_3d,aos_designeror";
                DynamicObject aosMktProguser = QueryServiceHelper.queryOne("aos_mkt_proguser", selectStr, filters);
                if (aosMktProguser != null) {
                    aosMktDesignreq.set("aos_user", aosMktProguser.get(AOS_DESIGNER));
                    aosMktDesignreq.set("aos_designer", aosMktProguser.get(AOS_DESIGNER));
                    aosMktDesignreq.set("aos_dm", aosMktProguser.get("aos_designeror"));
                    aosMktDesignreq.set("aos_3der", aosMktProguser.get("aos_3d"));
                }
            }
            // 是否新品
            if (dyn.getBoolean(sign.newItem.name)) {
                aosMktDesignreq.set("aos_type", "新品设计");
            } else {
                aosMktDesignreq.set("aos_type", "老品重拍");
            }
            aosMktDesignreq.set("aos_shipdate", aosShipDate);
            aosMktDesignreq.set("aos_orignbill", aosBillno);
            aosMktDesignreq.set("aos_sourceid", reqFid);
            // 状态先默认为 拍照功能图制作
            aosMktDesignreq.set("aos_status", "拍照功能图制作");
            aosMktDesignreq.set("aos_requireby", aosRequireby);
            aosMktDesignreq.set("aos_sourcetype", "PHOTO");
            // BOTP
            aosMktDesignreq.set("aos_sourcebilltype", AOS_MKT_PHOTOREQ);
            aosMktDesignreq.set("aos_sourcebillno", aosBillno);
            aosMktDesignreq.set("aos_srcentrykey", "aos_entryentity");
            mkt.progress.design.aos_mkt_designreq_bill.setEntityValue(aosMktDesignreq);
            List<DynamicObject> mapList = Cux_Common_Utl.GetUserOrg(aosRequirebyid);
            if (mapList != null) {
                if (mapList.get(TWO) != null) {
                    aosMktDesignreq.set("aos_organization1", mapList.get(TWO).get("id"));
                }
                if (mapList.get(THREE) != null) {
                    aosMktDesignreq.set("aos_organization2", mapList.get(THREE).get("id"));
                }
            }
            DynamicObjectCollection aosEntryentityS = aosMktDesignreq.getDynamicObjectCollection("aos_entryentity");
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
                int osQty = iteminfo.GetItemOsQty(orgId, fid);
                int safeQty = iteminfo.GetSafeQty(orgId);
                if ("C".equals(aosContryentry.getString("aos_contryentrystatus")) && osQty < safeQty) {
                    continue;
                }
                aosOrgtext.append(aosNationalitynumber).append(";");
                Object obj = aosContryentry.getDynamicObject("aos_contrybrand");
                if (obj == null) {
                    continue;
                }
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
            String url = "https://clss.s3.amazonaws.com/" + itemNumber + ".jpg";
            String aosProductno = bdMaterial.getString("aos_productno");
            String aosItemname = bdMaterial.getString("name");
            // 获取同产品号物料
            StringBuilder aosBroitem = new StringBuilder();
            DynamicObjectCollection bdMaterialS = QueryServiceHelper.query("bd_material", "id,number,aos_type",
                new QFilter("aos_productno", QCP.equals, aosProductno).and("aos_type", QCP.equals, "A").toArray());
            for (DynamicObject bd : bdMaterialS) {
                Object itemid = bdMaterial.get("id");
                if ("B".equals(bd.getString("aos_type"))) {
                    // 配件不获取
                    continue;
                }
                boolean exist = QueryServiceHelper.exists("bd_material", new QFilter("id", QCP.equals, itemid)
                    .and("aos_contryentry.aos_contryentrystatus", QCP.not_equals, "C"));
                if (!exist) {
                    // 全球终止不取
                    continue;
                }
                int osQty = AosMktListingReqBill.getOsQty(itemid);
                if (osQty < 10) {
                    continue;
                }
                String number = bd.getString("number");
                if (!itemNumber.equals(number)) {
                    aosBroitem.append(number).append(";");
                }
            }

            DynamicObject aosEntryentity = aosEntryentityS.addNew();
            aosEntryentity.set("aos_itemid", aosItemId);
            aosEntryentity.set("aos_is_saleout", ProgressUtil.Is_saleout(fid));
            aosEntryentity.set("aos_srcrowseq", 1);

            DynamicObjectCollection aosEntryentity6S = dyn.getDynamicObjectCollection("aos_entryentity6");
            for (DynamicObject aosEntryentity6 : aosEntryentity6S) {
                aosEntryentity.set("aos_desreq",
                    aosEntryentity6.getString("aos_reqsupp") + "/" + aosEntryentity6.getString("aos_devsupp"));
            }

            DynamicObjectCollection aosSubentryentityS =
                aosEntryentity.getDynamicObjectCollection("aos_subentryentity");

            DynamicObject aosSubentryentity = aosSubentryentityS.addNew();
            aosSubentryentity.set("aos_sub_item", aosItemId);
            aosSubentryentity.set("aos_segment3", aosProductno);
            aosSubentryentity.set("aos_itemname", aosItemname);
            aosSubentryentity.set("aos_brand", aosContrybrandStr.toString());
            aosSubentryentity.set("aos_pic", url);
            aosSubentryentity.set("aos_developer", bdMaterial.get("aos_developer"));
            aosSubentryentity.set("aos_seting1", bdMaterial.get("aos_seting_cn"));
            aosSubentryentity.set("aos_seting2", bdMaterial.get("aos_seting_en"));
            aosSubentryentity.set("aos_spec", bdMaterial.get("aos_specification_cn"));
            aosSubentryentity.set("aos_url", MKTS3PIC.GetItemPicture(itemNumber));
            aosSubentryentity.set("aos_broitem", aosBroitem);
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

            aos_mkt_designreq_bill.createDesiginBeforeSave(aosMktDesignreq);
            OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_designreq",
                new DynamicObject[] {aosMktDesignreq}, OperateOption.create());
            if (operationrst.isSuccess()) {
                String pK = operationrst.getSuccessPkIds().get(0).toString();
                FndHistory.Create("aos_mkt_designreq", pK, "新建", "新建节点： " + aosMktDesignreq.getString("aos_status"));
            }
            // 修复关联关系
            try {
                ProgressUtil.botp("aos_mkt_designreq", aosMktDesignreq.get("id"));
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                MKTCom.SendGlobalMessage(messageId, "aos_mkt_designreq",
                    String.valueOf(operationrst.getSuccessPkIds().get(0)), aosMktDesignreq.getString("billno"),
                    "设计需求表-拍照新品自动创建");
                FndHistory.Create(aosMktDesignreq, aosMktDesignreq.getString("aos_status"), "设计需求表-拍照新品自动创建");
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 开发/采购确认 创建跟单样品入库通知单
     **/
    public static void generateRcv(DynamicObject dyMain) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            // 数据层
            Object aosBillno = dyMain.get(BILLNO);
            Object reqFid = dyMain.getPkValue();
            Object aosItemId = dyMain.get(AOS_ITEMID);
            DynamicObject aosFollower = dyMain.getDynamicObject(AOS_FOLLOWER);
            Object aosFollowerId = aosFollower.getPkValue();
            Object aosVendor = dyMain.get("aos_vendor");
            DynamicObject aosItemidObject = (DynamicObject)aosItemId;
            Object fid = aosItemidObject.getPkValue();
            String messageId = String.valueOf(aosFollowerId);
            Object aosPhstate = dyMain.get("aos_phstate");
            DynamicObject bdMaterial = BusinessDataServiceHelper.loadSingle(fid, "bd_material", "aos_boxrate");
            DynamicObject aosMktRcv = BusinessDataServiceHelper.newDynamicObject("aos_mkt_rcv");
            // 校验
            try {
                List<DynamicObject> mapList = Cux_Common_Utl.GetUserOrg(aosFollowerId);
                if (mapList != null) {
                    if (mapList.get(TWO) != null) {
                        aosMktRcv.set("aos_organization1", mapList.get(TWO).get("id"));
                    }
                    if (mapList.get(THREE) != null) {
                        aosMktRcv.set("aos_organization2", mapList.get(THREE).get("id"));
                    }
                }
            } catch (Exception ex) {
                throw new FndError("跟单行政组织级别有误!");
            }
            // 校验生成的单据的唯一性
            judgeRcvRepeat(dyMain);
            // 初始化
            aosMktRcv.set("aos_user", aosFollowerId);
            aosMktRcv.set("aos_status", "新建");
            aosMktRcv.set("aos_vendor", aosVendor);
            aosMktRcv.set("aos_ponumber", dyMain.get("aos_ponumber"));
            aosMktRcv.set("aos_lineno", dyMain.get("aos_linenumber"));
            aosMktRcv.set("aos_itemid", aosItemId);
            aosMktRcv.set("aos_itemname", dyMain.get("aos_itemname"));
            aosMktRcv.set("aos_boxqty", bdMaterial.get("aos_boxrate"));
            aosMktRcv.set("aos_photoflag", dyMain.get(AOS_PHOTOFLAG));
            aosMktRcv.set("aos_phstate", aosPhstate);
            aosMktRcv.set("aos_photo", dyMain.get(AOS_PHOTOFLAG));
            aosMktRcv.set("aos_vedio", dyMain.get(AOS_VEDIOFLAG));
            aosMktRcv.set("aos_reason", dyMain.get(AOS_REASON));
            aosMktRcv.set("aos_sameitemid", dyMain.get(AOS_SAMEITEMID));
            aosMktRcv.set("aos_developer", dyMain.get(AOS_DEVELOPER));
            aosMktRcv.set("aos_earlydate", dyMain.get("aos_earlydate"));
            aosMktRcv.set("aos_shipdate", dyMain.get(AOS_SHIPDATE));
            aosMktRcv.set("aos_requireby", aosFollowerId);
            aosMktRcv.set("aos_requiredate", new Date());
            aosMktRcv.set("aos_orignbill", aosBillno);
            aosMktRcv.set("aos_sourceid", reqFid);
            aosMktRcv.set("aos_is_saleout", dyMain.get("aos_is_saleout"));
            aosMktRcv.set("aos_returnreason", dyMain.get("aos_phreason"));
            // BOTP
            aosMktRcv.set("aos_sourcebilltype", AOS_MKT_PHOTOREQ);
            aosMktRcv.set("aos_sourcebillno", dyMain.get("billno"));
            aosMktRcv.set("aos_srcentrykey", "aos_entryentity");
            // 新增质检完成日期、紧急程度字段
            QFilter qFilterContra = new QFilter("aos_insrecordentity.aos_insresultchk", "=", "A");
            QFilter qFilterLineno = new QFilter("aos_insrecordentity.aos_instypedetailchk", "=", "1");
            // 优化 过滤条件 增加合同号 行号
            QFilter qFilterPonumber =
                new QFilter("aos_insrecordentity.aos_contractnochk", "=", dyMain.get("aos_ponumber"));
            QFilter qFilterLinenumber =
                new QFilter("aos_insrecordentity.aos_lineno", "=", dyMain.get("aos_linenumber"));
            QFilter[] qFilters = {qFilterContra, qFilterLineno, qFilterPonumber, qFilterLinenumber};
            DynamicObject dyDate = QueryServiceHelper.queryOne("aos_qctasklist", "aos_quainscomdate", qFilters);
            if (dyDate != null) {
                aosMktRcv.set("aos_quainscomdate", dyDate.get("aos_quainscomdate"));
                // 当前日期-10(质检完成日期)，紧急程度刷新成紧急
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date dt = new Date();
                Calendar rightNow = Calendar.getInstance();
                rightNow.setTime(dt);
                // 日期减10天
                rightNow.add(Calendar.DAY_OF_YEAR, -10);
                Date dt1 = rightNow.getTime();
                String reStr1 = sdf.format(dt1);
                String reStr2 = sdf.format(dyDate.get("aos_quainscomdate"));
                if (reStr2.compareTo(reStr1) >= 0) {
                    aosMktRcv.set("aos_importance", "紧急");
                }
            }
            DynamicObjectCollection aosEntryentityS = aosMktRcv.getDynamicObjectCollection("aos_entryentity");
            DynamicObject aosEntryentity = aosEntryentityS.addNew();
            aosEntryentity.set("aos_srcrowseq", 1);
            if (sign.goShot.name.equals(aosPhstate) || sign.outSource.name.equals(aosPhstate)) {
                aosMktRcv.set("aos_protype", "退回");
                QFilter filterVendor = new QFilter("aos_vendor", "=", aosVendor);
                QFilter filterType = new QFilter("aos_protype", "=", "退回");
                QFilter[] filters = {filterVendor, filterType};
                DynamicObjectCollection aosMktRcvSameS = QueryServiceHelper.query("aos_mkt_rcv",
                    "aos_contact,aos_contactway,aos_returnadd", filters, "createtime desc");
                for (DynamicObject aosMktRcvSame : aosMktRcvSameS) {
                    aosMktRcv.set("aos_contact", aosMktRcvSame.get("aos_contact"));
                    aosMktRcv.set("aos_contactway", aosMktRcvSame.get("aos_contactway"));
                    aosMktRcv.set("aos_returnadd", aosMktRcvSame.get("aos_returnadd"));
                    break;
                }
            }
            // 给 拍照地点赋值
            if (sign.goShot.name.equals(aosPhstate)) {
                // 取拍照地点
                QFilter filterStatus = new QFilter("aos_address", "=", aosPhstate);
                QFilter filterType = new QFilter("aos_entryentity.aos_valid", "=", true);
                QFilter[] filters = {filterStatus, filterType};
                DynamicObjectCollection aosMktSampleaddress = QueryServiceHelper.query("aos_mkt_sampleaddress",
                    "aos_entryentity.aos_content aos_content", filters);
                if (aosMktSampleaddress.size() > 0) {
                    DynamicObject dy = aosMktSampleaddress.get(0);
                    aosMktRcv.set("aos_address", dy.getString("aos_content"));
                }
            }
            OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_rcv",
                new DynamicObject[] {aosMktRcv}, OperateOption.create());
            // 修复关联关系
            try {
                ProgressUtil.botp("aos_mkt_rcv", aosMktRcv.get("id"));
            } catch (Exception ex) {
                FndError.send(SalUtil.getExceptionStr(ex), FndWebHook.urlMms);
            }
            if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                MKTCom.SendGlobalMessage(messageId, "aos_mkt_rcv",
                    String.valueOf(operationrst.getSuccessPkIds().get(0)), aosMktRcv.getString("billno"),
                    "样品入库通知单-拍照流程自动创建");
            }
            dyMain.set("aos_rcvbill", aosMktRcv.getString("billno"));
            dyMain.set("aos_samplestatus", "新建");
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 生成拍照需求表校验唯一性
     **/
    private static void judgeRcvRepeat(DynamicObject dyMain) {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            Object bill = dyMain.get(BILLNO);
            DynamicObject aosItemid = dyMain.getDynamicObject("aos_itemid");
            if (aosItemid == null) {
                return;
            }
            Object item = aosItemid.getPkValue();
            QFilter filterBillno = new QFilter("aos_orignbill", "=", bill);
            QFilter filterItem = new QFilter("aos_itemid", "=", item);
            DeleteServiceHelper.delete("aos_mkt_rcv", new QFilter[] {filterBillno, filterItem});
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 视频更新拆分子流程
     *
     * @param userMap 人员
     **/
    private static void splitSon(String userMapKey, HashMap<String, String> userMap, DynamicObject pAosMktPhotoReq)
        throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            Object reqFid = pAosMktPhotoReq.getPkValue();
            DynamicObject aosMktPhotoReq = BusinessDataServiceHelper.newDynamicObject("aos_mkt_photoreq");
            Object aosItemid = pAosMktPhotoReq.getDynamicObject("aos_itemid").get("id");
            String aosOrgid = userMap.get(userMapKey);
            DynamicObject bdMaterial = QueryServiceHelper.queryOne("bd_material",
                "aos_contryentry.aos_firstindate aos_firstindate", new QFilter("id", QCP.equals, aosItemid)
                    .and("aos_contryentry.aos_nationality", QCP.equals, aosOrgid).toArray());
            Date aosFirstindate = bdMaterial.getDate("aos_firstindate");
            if (FndGlobal.IsNull(aosFirstindate)) {
                aosMktPhotoReq.set("aos_user", SYSTEM);
            } else {
                aosMktPhotoReq.set("aos_user", userMapKey);
            }
            aosMktPhotoReq.set("aos_salehelper", userMapKey);
            // 国别
            aosMktPhotoReq.set("aos_orgid", aosOrgid);
            // 国别首次入库日期
            aosMktPhotoReq.set("aos_firstindate", aosFirstindate);
            aosMktPhotoReq.set("billstatus", "A");
            aosMktPhotoReq.set("aos_requireby", pAosMktPhotoReq.get("aos_requireby"));
            aosMktPhotoReq.set("aos_requiredate", pAosMktPhotoReq.get("aos_requiredate"));
            aosMktPhotoReq.set("aos_shipdate", pAosMktPhotoReq.getDate("aos_shipdate"));
            aosMktPhotoReq.set(AOS_URGENT, pAosMktPhotoReq.get(AOS_URGENT));
            aosMktPhotoReq.set("aos_photoflag", pAosMktPhotoReq.get("aos_photoflag"));
            aosMktPhotoReq.set("aos_reason", pAosMktPhotoReq.get("aos_reason"));
            aosMktPhotoReq.set("aos_sameitemid", pAosMktPhotoReq.get("aos_sameitemid"));
            aosMktPhotoReq.set("aos_vedioflag", pAosMktPhotoReq.get("aos_vedioflag"));
            aosMktPhotoReq.set("aos_reqtype", pAosMktPhotoReq.get("aos_reqtype"));
            aosMktPhotoReq.set("aos_sourceid", pAosMktPhotoReq.get("aos_sourceid"));
            aosMktPhotoReq.set("aos_itemid", pAosMktPhotoReq.get("aos_itemid"));
            aosMktPhotoReq.set("aos_is_saleout", Is_saleout(aosItemid));
            aosMktPhotoReq.set("aos_itemname", pAosMktPhotoReq.get("aos_itemname"));
            aosMktPhotoReq.set("aos_contrybrand", pAosMktPhotoReq.get("aos_contrybrand"));
            aosMktPhotoReq.set("aos_newitem", pAosMktPhotoReq.get("aos_newitem"));
            aosMktPhotoReq.set("aos_newvendor", pAosMktPhotoReq.get("aos_newvendor"));
            aosMktPhotoReq.set("aos_ponumber", pAosMktPhotoReq.get("aos_ponumber"));
            aosMktPhotoReq.set("aos_linenumber", pAosMktPhotoReq.get("aos_linenumber"));
            aosMktPhotoReq.set("aos_earlydate", pAosMktPhotoReq.get("aos_earlydate"));
            aosMktPhotoReq.set("aos_checkdate", pAosMktPhotoReq.get("aos_checkdate"));
            aosMktPhotoReq.set("aos_specification", pAosMktPhotoReq.get("aos_specification"));
            aosMktPhotoReq.set("aos_seting1", pAosMktPhotoReq.get("aos_seting1"));
            aosMktPhotoReq.set("aos_seting2", pAosMktPhotoReq.get("aos_seting2"));
            aosMktPhotoReq.set("aos_sellingpoint", pAosMktPhotoReq.get("aos_sellingpoint"));
            aosMktPhotoReq.set("aos_vendor", pAosMktPhotoReq.get("aos_vendor"));
            aosMktPhotoReq.set("aos_city", pAosMktPhotoReq.get("aos_city"));
            aosMktPhotoReq.set("aos_contact", pAosMktPhotoReq.get("aos_contact"));
            aosMktPhotoReq.set("aos_address", pAosMktPhotoReq.get("aos_address"));
            aosMktPhotoReq.set("aos_phone", pAosMktPhotoReq.get("aos_phone"));
            aosMktPhotoReq.set("aos_phstate", pAosMktPhotoReq.get("aos_phstate"));
            aosMktPhotoReq.set("aos_rcvbill", pAosMktPhotoReq.get("aos_rcvbill"));
            aosMktPhotoReq.set("aos_sampledate", pAosMktPhotoReq.get("aos_sampledate"));
            aosMktPhotoReq.set("aos_installdate", pAosMktPhotoReq.get("aos_installdate"));
            aosMktPhotoReq.set("aos_poer", pAosMktPhotoReq.get("aos_poer"));
            aosMktPhotoReq.set("aos_developer", pAosMktPhotoReq.get("aos_developer"));
            aosMktPhotoReq.set("aos_follower", pAosMktPhotoReq.get("aos_follower"));
            aosMktPhotoReq.set("aos_whiteph", pAosMktPhotoReq.get("aos_whiteph"));
            aosMktPhotoReq.set("aos_actph", pAosMktPhotoReq.get("aos_actph"));
            aosMktPhotoReq.set("aos_vedior", pAosMktPhotoReq.get("aos_vedior"));
            aosMktPhotoReq.set("aos_3d", pAosMktPhotoReq.get("aos_3d"));
            aosMktPhotoReq.set("aos_whitedate", pAosMktPhotoReq.get("aos_whitedate"));
            aosMktPhotoReq.set("aos_actdate", pAosMktPhotoReq.get("aos_actdate"));
            aosMktPhotoReq.set("aos_picdate", pAosMktPhotoReq.get("aos_picdate"));
            aosMktPhotoReq.set("aos_funcpicdate", pAosMktPhotoReq.get("aos_funcpicdate"));
            aosMktPhotoReq.set("aos_vedio", pAosMktPhotoReq.get("aos_vedio"));
            aosMktPhotoReq.set("billno", pAosMktPhotoReq.get("billno"));
            aosMktPhotoReq.set("aos_type", "视频");
            aosMktPhotoReq.set("aos_designer", pAosMktPhotoReq.get("aos_designer"));
            aosMktPhotoReq.set("aos_sale", pAosMktPhotoReq.get("aos_sale"));
            aosMktPhotoReq.set("aos_status", "视频更新:多人会审");
            aosMktPhotoReq.set("aos_desc", pAosMktPhotoReq.get("aos_desc"));
            aosMktPhotoReq.set("aos_sonflag", true);
            aosMktPhotoReq.set("aos_parentid", reqFid);
            aosMktPhotoReq.set("aos_parentbill", pAosMktPhotoReq.get("billno"));
            aosMktPhotoReq.set("aos_vediotype", pAosMktPhotoReq.get("aos_vediotype"));
            aosMktPhotoReq.set("aos_organization1", pAosMktPhotoReq.get("aos_organization1"));
            aosMktPhotoReq.set("aos_organization2", pAosMktPhotoReq.get("aos_organization2"));
            aosMktPhotoReq.set("aos_vediotype", pAosMktPhotoReq.get("aos_vediotype"));
            aosMktPhotoReq.set("aos_orgtext", pAosMktPhotoReq.get("aos_orgtext"));
            aosMktPhotoReq.set("aos_samplestatus", pAosMktPhotoReq.get("aos_samplestatus"));
            // 新增质检完成日期
            QFilter qFilterContra = new QFilter("aos_insrecordentity.aos_insresultchk", "=", "A");
            QFilter qFilterLineno = new QFilter("aos_insrecordentity.aos_instypedetailchk", "=", "1");
            // 优化 过滤条件 增加合同号 行号
            QFilter qFilterPonumber =
                new QFilter("aos_insrecordentity.aos_contractnochk", "=", pAosMktPhotoReq.get("aos_ponumber"));
            QFilter qFilterLinenumber =
                new QFilter("aos_insrecordentity.aos_lineno", "=", pAosMktPhotoReq.get("aos_linenumber"));
            QFilter[] qFilters = {qFilterContra, qFilterLineno, qFilterPonumber, qFilterLinenumber};
            DynamicObject dyDate = QueryServiceHelper.queryOne("aos_qctasklist", "aos_quainscomdate", qFilters);
            if (dyDate != null) {
                aosMktPhotoReq.set("aos_quainscomdate", dyDate.get("aos_quainscomdate"));
            }
            // 照片需求单据体(新)
            DynamicObjectCollection aosEntryentity5S = aosMktPhotoReq.getDynamicObjectCollection("aos_entryentity5");
            DynamicObjectCollection aosEntryentity5OriS =
                pAosMktPhotoReq.getDynamicObjectCollection("aos_entryentity5");
            for (DynamicObject aosEntryentity5Ori : aosEntryentity5OriS) {
                DynamicObject aosEntryentity5 = aosEntryentity5S.addNew();
                aosEntryentity5.set("aos_reqfirst", aosEntryentity5Ori.get("aos_reqfirst"));
                aosEntryentity5.set("aos_reqother", aosEntryentity5Ori.get("aos_reqother"));
                aosEntryentity5.set("aos_detail", aosEntryentity5Ori.get("aos_detail"));
                aosEntryentity5.set("aos_scene1", aosEntryentity5Ori.get("aos_scene1"));
                aosEntryentity5.set("aos_object1", aosEntryentity5Ori.get("aos_object1"));
                aosEntryentity5.set("aos_scene2", aosEntryentity5Ori.get("aos_scene2"));
                aosEntryentity5.set("aos_object2", aosEntryentity5Ori.get("aos_object2"));
            }
            // 照片需求单据体(新2)
            DynamicObjectCollection aosEntryentity6S = aosMktPhotoReq.getDynamicObjectCollection("aos_entryentity6");
            DynamicObjectCollection aosEntryentity6OriS =
                pAosMktPhotoReq.getDynamicObjectCollection("aos_entryentity6");
            for (DynamicObject aosEntryentity6Ori : aosEntryentity6OriS) {
                DynamicObject aosEntryentity6 = aosEntryentity6S.addNew();
                aosEntryentity6.set("aos_reqsupp", aosEntryentity6Ori.get("aos_reqsupp"));
                aosEntryentity6.set("aos_devsupp", aosEntryentity6Ori.get("aos_devsupp"));
            }
            // 照片需求单据体
            DynamicObjectCollection aosEntryentityS = aosMktPhotoReq.getDynamicObjectCollection("aos_entryentity");
            DynamicObjectCollection aosEntryentityOriS = pAosMktPhotoReq.getDynamicObjectCollection("aos_entryentity");
            for (DynamicObject aosEntryentityOri : aosEntryentityOriS) {
                DynamicObject aosEntryentity = aosEntryentityS.addNew();
                aosEntryentity.set("aos_applyby", aosEntryentityOri.get("aos_applyby"));
                aosEntryentity.set("aos_picdesc", aosEntryentityOri.get("aos_picdesc"));
            }
            // 视频需求单据体
            DynamicObjectCollection aosEntryentity1S = aosMktPhotoReq.getDynamicObjectCollection("aos_entryentity1");
            DynamicObjectCollection aosEntryentityOri1S =
                pAosMktPhotoReq.getDynamicObjectCollection("aos_entryentity1");
            for (DynamicObject aosEntryentityOri1 : aosEntryentityOri1S) {
                DynamicObject aosEntryentity1 = aosEntryentity1S.addNew();
                aosEntryentity1.set("aos_applyby2", aosEntryentityOri1.get("aos_applyby2"));
                aosEntryentity1.set("aos_veddesc", aosEntryentityOri1.get("aos_veddesc"));
            }
            // 拍摄情况单据体
            DynamicObjectCollection aosEntryentity2S = aosMktPhotoReq.getDynamicObjectCollection("aos_entryentity2");
            DynamicObjectCollection aosEntryentityOri2S =
                pAosMktPhotoReq.getDynamicObjectCollection("aos_entryentity2");
            for (DynamicObject aosEntryentityOri2 : aosEntryentityOri2S) {
                DynamicObject aosEntryentity2 = aosEntryentity2S.addNew();
                aosEntryentity2.set("aos_phtype", aosEntryentityOri2.get("aos_phtype"));
                aosEntryentity2.set("aos_complete", aosEntryentityOri2.get("aos_complete"));
                aosEntryentity2.set("aos_completeqty", aosEntryentityOri2.get("aos_completeqty"));
            }
            // 流程退回原因单据体
            DynamicObjectCollection aosEntryentity3S = aosMktPhotoReq.getDynamicObjectCollection("aos_entryentity3");
            DynamicObjectCollection aosEntryentityOri3S =
                pAosMktPhotoReq.getDynamicObjectCollection("aos_entryentity3");
            for (DynamicObject aosEntryentityOri3 : aosEntryentityOri3S) {
                DynamicObject aosEntryentity3 = aosEntryentity3S.addNew();
                aosEntryentity3.set("aos_returnby", aosEntryentityOri3.get("aos_returnby"));
                aosEntryentity3.set("aos_return", aosEntryentityOri3.get("aos_return"));
                aosEntryentity3.set("aos_returnreason", aosEntryentityOri3.get("aos_returnreason"));
            }
            aosMktPhotoReq.set("aos_phreturn", pAosMktPhotoReq.get("aos_phreturn"));
            aosMktPhotoReq.set("aos_phreason", pAosMktPhotoReq.get("aos_phreason"));
            aosMktPhotoReq.set("aos_dereturn", pAosMktPhotoReq.get("aos_dereturn"));
            aosMktPhotoReq.set("aos_dereason", pAosMktPhotoReq.get("aos_dereason"));
            // 视频地址单据体
            DynamicObjectCollection aosEntryentity4S = aosMktPhotoReq.getDynamicObjectCollection("aos_entryentity4");
            DynamicObjectCollection aosEntryentityOri4S =
                pAosMktPhotoReq.getDynamicObjectCollection("aos_entryentity4");
            for (DynamicObject aosEntryentityOri4 : aosEntryentityOri4S) {
                DynamicObject aosEntryentity4 = aosEntryentity4S.addNew();
                aosEntryentity4.set("aos_orgshort", aosEntryentityOri4.get("aos_orgshort"));
                aosEntryentity4.set("aos_brand", aosEntryentityOri4.get("aos_brand"));
                aosEntryentity4.set("aos_s3address1", aosEntryentityOri4.get("aos_s3address1"));
                aosEntryentity4.set("aos_s3address2", aosEntryentityOri4.get("aos_s3address2"));
                aosEntryentity4.set("aos_editor", aosEntryentityOri4.get("aos_editor"));
                aosEntryentity4.set("aos_salerece_date", new Date());
                aosEntryentityOri4.set("aos_salerece_date", new Date());
            }
            OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq", new DynamicObject[] {aosMktPhotoReq},
                OperateOption.create());
            OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq", new DynamicObject[] {pAosMktPhotoReq},
                OperateOption.create());
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        // 给工具栏加监听事件
        this.addItemClickListeners("aos_toolbarap");
        // 提交
        this.addItemClickListeners("aos_submit");
        // 确认
        this.addItemClickListeners("aos_confirm");
        // 退回
        this.addItemClickListeners("aos_back");
        EntryGrid entryGrid = this.getControl("aos_entryentity5");
        entryGrid.addHyperClickListener(this);
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String control = evt.getItemKey();
        Span span = MmsOtelUtils.getCusMainSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            if (sign.submit.name.equals(control)) {
                DynamicObject dyMain = this.getModel().getDataEntity(true);
                // 提交
                aos_submit(dyMain, "A");
                // 当前界面主键
                Object reqFid = this.getModel().getDataEntity().getPkValue();
                DeleteServiceHelper.delete("aos_mkt_photoreq", new QFilter[] {new QFilter("aos_sonflag", "=", true),
                    new QFilter("aos_status", "=", "已完成"), new QFilter("id", "=", reqFid)});
            } else if (sign.confirm.name.equals(control)) {
                // 确认
                aos_confirm();
            } else if (sign.back.name.equals(control)) {
                // 退回
                aos_back();
            } else if (sign.history.name.equals(control)) {
                // 查看历史记录
                aos_history();
            } else if (sign.search.name.equals(control)) {
                // 查看子流程
                aosSearch();
            } else if (sign.showRcv.name.equals(control)) {
                // 查看入库单
                aos_showrcv();
            } else if (sign.photoUrl.name.equals(control)) {
                openPhotoUrl();
            } else if (sign.senceUrl.name.equals(control)) {
                openSenceUrl();
            }
        } catch (FndError fndError) {
            fndError.show(getView());
            MmsOtelUtils.setException(span, fndError);
        } catch (Exception ex) {
            FndError.showex(getView(), ex, FndWebHook.urlMms);
            MmsOtelUtils.setException(span, ex);
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    @Override
    public void afterLoadData(EventObject e) {
        super.afterLoadData(e);
        StatusControl();
        itemInit();
    }

    private void itemInit() {
        Object aosItemid = this.getModel().getValue(AOS_ITEMID);
        Object aosStatus = this.getModel().getValue("aos_status");
        if (aosItemid == null) {
            // 清空值
            this.getModel().setValue(AOS_ITEMNAME, null);
            this.getModel().setValue(AOS_CONTRYBRAND, null);
            this.getModel().setValue(AOS_SPECIFICATION, null);
            this.getModel().setValue(AOS_SETING1, null);
            this.getModel().setValue(AOS_SETING2, null);
            this.getModel().setValue(AOS_SELLINGPOINT, null);
            this.getModel().setValue(AOS_VENDOR, null);
            this.getModel().setValue(AOS_CITY, null);
            this.getModel().setValue(AOS_CONTACT, null);
            this.getModel().setValue(AOS_ADDRESS, null);
            this.getModel().setValue(AOS_PHONE, null);
            this.getModel().setValue(AOS_PHSTATE, null);
            this.getModel().setValue(AOS_DEVELOPER, null);
            this.getModel().setValue(AOS_POER, null);
            this.getModel().setValue(AOS_FOLLOWER, null);
            this.getModel().setValue("aos_orgtext", null);
            this.getModel().setValue("aos_sale", null);
            this.getModel().setValue("aos_vedior", null);
            this.getModel().setValue("aos_3d", null);
            this.getModel().setValue(AOS_SHIPDATE, null);
            this.getModel().setValue(AOS_URGENT, null);
            // 爆品
            this.getModel().setValue("aos_is_saleout", false);
            // 是否拍视频
            this.getModel().setValue("aos_vedioflag", false);
            // 清空图片
            Image image = this.getControl("aos_image");
            image.setUrl(null);
            this.getModel().setValue("aos_picturefield", null);
            // 清空品牌信息
            this.getModel().deleteEntryData("aos_entryentity4");
            // RGB颜色
            this.getModel().setValue("aos_rgb", null);
        } else {
            DynamicObject aosItemidObject = (DynamicObject)aosItemid;
            Object fid = aosItemidObject.getPkValue();
            StringBuilder aosContrybrandStr = new StringBuilder();
            StringBuilder aosOrgtext = new StringBuilder();
            DynamicObject bdMaterial = BusinessDataServiceHelper.loadSingle(fid, "bd_material");
            DynamicObjectCollection aosContryentryS = bdMaterial.getDynamicObjectCollection("aos_contryentry");
            String itemNumber = bdMaterial.getString("number");
            List<DynamicObject> listCountry = aosContryentryS.stream()
                .sorted(Comparator.comparingInt(dy -> dy.getDynamicObject("aos_nationality").getInt("aos_orderby")))
                .collect(Collectors.toList());
            // 产品类别
            String category = (String)SalUtil.getCategoryByItemId(String.valueOf(fid)).get("name");
            String[] categoryGroup = category.split(",");
            String aosCategory1 = null;
            String aosCategory2 = null;
            String aosCategory3 = null;
            int categoryLength = categoryGroup.length;
            if (categoryLength > 0) {
                aosCategory1 = categoryGroup[0];
            }
            if (categoryLength > 1) {
                aosCategory2 = categoryGroup[1];
            }
            if (categoryLength > TWO) {
                aosCategory3 = categoryGroup[2];
            }
            if (sign.newStatus.name.equals(aosStatus) || sign.vedPhoto.name.equals(aosStatus)) {
                this.getModel().deleteEntryData("aos_entryentity4");
            }
            int i = 1;
            // 获取所有国家品牌 字符串拼接终止
            for (DynamicObject aosContryentry : listCountry) {
                DynamicObject aosNationality = aosContryentry.getDynamicObject("aos_nationality");
                String aosNationalitynumber = aosNationality.getString("number");
                if ("IE".equals(aosNationalitynumber)) {
                    continue;
                }
                Object orgId = aosNationality.get("id");
                int osQty = iteminfo.GetItemOsQty(orgId, fid);
                int safeQty = iteminfo.GetSafeQty(orgId);
                if ("C".equals(aosNationality.getString("aos_contryentrystatus")) && osQty < safeQty) {
                    continue;
                }
                aosOrgtext.append(aosNationalitynumber).append(";");
                Object obj = aosContryentry.getDynamicObject("aos_contrybrand");
                if (obj == null) {
                    continue;
                }
                String value = aosContryentry.getDynamicObject("aos_contrybrand").getString("name");
                if ("新建".equals(aosStatus) || "视频拍摄".equals(aosStatus)) {
                    this.getModel().batchCreateNewEntryRow("aos_entryentity4", 1);
                    this.getModel().setValue("aos_orgshort", aosNationalitynumber, i - 1);
                    this.getModel().setValue("aos_brand", value, i - 1);
                    // 设置编辑人员
                    DynamicObject aosMktProgorguser = QueryServiceHelper.queryOne("aos_mkt_progorguser", "aos_oueditor",
                        new QFilter("aos_orgid.number", QCP.equals, aosNationalitynumber)
                            .and("aos_category1", QCP.equals, aosCategory1)
                            .and("aos_category2", QCP.equals, aosCategory2).toArray());
                    FndMsg.debug("aos_category1:" + aosCategory1);
                    FndMsg.debug("aos_category2:" + aosCategory2);
                    if (FndGlobal.IsNotNull(aosMktProgorguser)) {
                        FndMsg.debug("into not null prorguser");
                        this.getModel().setValue("aos_editor", aosMktProgorguser.get("aos_oueditor"), i - 1);
                    }
                    String first = itemNumber.substring(0, 1);
                    // 含品牌
                    if ("US".equals(aosNationalitynumber) || "CA".equals(aosNationalitynumber)
                        || "UK".equals(aosNationalitynumber))
                    // 非小语种
                    {
                        this.getModel().setValue("aos_s3address1", "https://uspm.aosomcdn.com/videos/en/" + first + "/"
                            + itemNumber + "/" + itemNumber + "-" + value + ".mp4", i - 1);
                    } else
                    // 小语种
                    {
                        this.getModel()
                            .setValue("aos_s3address1", "https://uspm.aosomcdn.com/videos/en/" + first + "/"
                                + itemNumber + "/" + itemNumber + "-" + value + "-" + aosNationalitynumber + ".mp4",
                                i - 1);
                    }
                    // 不含品牌
                    if ("US".equals(aosNationalitynumber) || "CA".equals(aosNationalitynumber)
                        || "UK".equals(aosNationalitynumber))
                    // 非小语种
                    {
                        this.getModel().setValue("aos_s3address2", "https://uspm.aosomcdn.com/videos/en/" + first + "/"
                            + itemNumber + "/" + itemNumber + ".mp4", i - 1);
                    } else
                    // 小语种
                    {
                        this.getModel().setValue("aos_s3address2", "https://uspm.aosomcdn.com/videos/en/" + first + "/"
                            + itemNumber + "/" + itemNumber + "-" + aosNationalitynumber + ".mp4", i - 1);
                    }
                    if ("US".equals(aosNationalitynumber) || "CA".equals(aosNationalitynumber)
                        || "UK".equals(aosNationalitynumber)) {
                        this.getModel().setValue("aos_filename", itemNumber + "-" + value, i - 1);
                    } else {
                        this.getModel().setValue("aos_filename", itemNumber + "-" + value + "-" + aosNationalitynumber,
                            i - 1);
                    }
                    // 设置slogan与品名
                    String sloganSelect = "";
                    if ("US,CA,UK".contains(aosNationalitynumber)) {
                        sloganSelect = "aos_itemnameen aos_name,aos_sloganen aos_slogan";
                    } else if ("DE".equals(aosNationalitynumber)) {
                        sloganSelect = "aos_itemnamede aos_name,aos_slogande aos_slogan";
                    } else if ("IT".equals(aosNationalitynumber)) {
                        sloganSelect = "aos_itemnameit aos_name,aos_sloganit aos_slogan";
                    } else if ("FR".equals(aosNationalitynumber)) {
                        sloganSelect = "aos_itemnamefr aos_name,aos_sloganfr aos_slogan";
                    } else if ("ES".equals(aosNationalitynumber)) {
                        sloganSelect = "aos_itemnamees aos_name,aos_sloganes aos_slogan";
                    }
                    StringBuilder aosName = new StringBuilder();
                    StringBuilder aosSlogan = new StringBuilder();
                    DynamicObjectCollection sloganS = QueryServiceHelper.query("aos_mkt_data_slogan", sloganSelect,
                        new QFilter("aos_category1", QCP.equals, aosCategory1)
                            .and("aos_category2", QCP.equals, aosCategory2)
                            .and("aos_category3", QCP.equals, aosCategory3)
                            .and("aos_itemnamecn", QCP.equals, bdMaterial.getString("name")).toArray());
                    for (DynamicObject slogan : sloganS) {
                        aosName.append(slogan.getString("aos_name")).append(";");
                        aosSlogan.append(slogan.getString("aos_slogan")).append(";");
                    }
                    this.getModel().setValue("aos_name", aosName.toString(), i - 1);
                    this.getModel().setValue("aos_slogan", aosSlogan.toString(), i - 1);
                }
                i++;
                if (!aosContrybrandStr.toString().contains(value)) {
                    aosContrybrandStr.append(value).append(";");
                }
            }
            // 图片字段
            QFilter filter = new QFilter("entryentity.aos_articlenumber", QCP.equals, aosItemidObject.getPkValue())
                .and("billstatus", QCP.in, new String[] {"C", "D"});
            DynamicObjectCollection query = QueryServiceHelper.query("aos_newarrangeorders", "entryentity.aos_picture",
                filter.toArray(), "aos_creattime desc");
            if (query != null && query.size() > 0) {
                Object o = query.get(0).get("entryentity.aos_picture");
                this.getModel().setValue("aos_picturefield", o);
            }
            // 赋值物料相关
            String aosItemName = bdMaterial.getString("name");
            this.getModel().setValue(AOS_ITEMNAME, aosItemName);
            this.getModel().setValue(AOS_CONTRYBRAND, aosContrybrandStr.toString());
            this.getModel().setValue("aos_orgtext", aosOrgtext.toString());
            this.getModel().setValue(AOS_SPECIFICATION, bdMaterial.get("aos_specification_cn"));
            this.getModel().setValue(AOS_SETING1, bdMaterial.get("aos_seting_cn"));
            this.getModel().setValue(AOS_SETING2, bdMaterial.get("aos_seting_en"));
            this.getModel().setValue(AOS_SELLINGPOINT, bdMaterial.get("aos_sellingpoint"));
            this.getModel().setValue(AOS_DEVELOPER, bdMaterial.get("aos_developer"));
            this.getModel().setValue(AOS_CATEGORY1, aosCategory1);
            this.getModel().setValue(AOS_CATEGORY2, aosCategory2);
            this.getModel().setValue(AOS_CATEGORY3, aosCategory3);
            // 摄影标准库字段
            this.getModel().deleteEntryData("aos_entryentity5");
            this.getModel().batchCreateNewEntryRow("aos_entryentity5", 1);
            this.getModel().setValue("aos_refer", "摄影标准库", 0);
            DynamicObject aosMktPhotoStd =
                QueryServiceHelper.queryOne("aos_mkt_photostd", "aos_firstpicture,aos_other,aos_require",
                    new QFilter("aos_category1_name", QCP.equals, aosCategory1)
                        .and("aos_category2_name", QCP.equals, aosCategory2)
                        .and("aos_category3_name", QCP.equals, aosCategory3)
                        .and("aos_itemnamecn", QCP.equals, aosItemName).toArray());
            if (FndGlobal.IsNotNull(aosMktPhotoStd)) {
                this.getModel().setValue("aos_reqfirst", aosMktPhotoStd.getString("aos_firstpicture"), 0);
                this.getModel().setValue("aos_reqother", aosMktPhotoStd.getString("aos_other"), 0);
                this.getModel().setValue("aos_detail", aosMktPhotoStd.getString("aos_require"), 0);
            }
            // 布景标准库字段
            DynamicObject aosMktViewStd = QueryServiceHelper.queryOne("aos_mkt_viewstd",
                "aos_scene1,aos_object1,aos_scene2,aos_object2,aos_itemnamecn,aos_descpic",
                new QFilter("aos_category1_name", QCP.equals, aosCategory1)
                    .and("aos_category2_name", QCP.equals, aosCategory2)
                    .and("aos_category3_name", QCP.equals, aosCategory3).and("aos_itemnamecn", QCP.equals, aosItemName)
                    .toArray());
            if (FndGlobal.IsNotNull(aosMktViewStd)) {
                this.getModel().setValue("aos_scene1", aosMktViewStd.getString("aos_scene1"), 0);
                this.getModel().setValue("aos_object1", aosMktViewStd.getString("aos_object1"), 0);
                this.getModel().setValue("aos_scene2", aosMktViewStd.getString("aos_scene2"), 0);
                this.getModel().setValue("aos_object2", aosMktViewStd.getString("aos_object2"), 0);
                this.getModel().setValue("aos_descpic", aosMktViewStd.getString("aos_descpic"), 0);
            }

            DynamicObject aosColor = bdMaterial.getDynamicObject("aos_color");
            if (FndGlobal.IsNotNull(aosColor)) {
                String aosRgb = aosColor.getString("aos_rgb");
                this.getModel().setValue("aos_rgb", aosRgb);
                this.getModel().setValue("aos_colors", aosColor.get("aos_colors"));
                this.getModel().setValue("aos_colorname", aosColor.get("name"));
                this.getModel().setValue("aos_colorex", aosColor);
                HashMap<String, Object> fieldMap = new HashMap<>(16);
                // 设置前景色
                fieldMap.put(ClientProperties.BackColor, aosRgb);
                // 同步指定元数据到控件
                this.getView().updateControlMetadata("aos_color", fieldMap);
            }
        }
    }

    @Override
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        initDefualt();
        StatusControl();
        this.getView().setVisible(false, "aos_submit");
    }

    @Override
    public void afterBindData(EventObject e) {
        super.afterBindData(e);
        // 设置白底和实景状态下的默认值
        setDefaulAtPhotoNode();
    }

    /**
     * 在白底和实景拍摄节点设置拍摄的完成数的默认值 白1，实2
     **/
    private void setDefaulAtPhotoNode() {
        Object aosStatus = this.getModel().getValue("aos_status");
        if (!Cux_Common_Utl.IsNull(aosStatus)) {
            if (StringUtils.equals(aosStatus.toString(), sign.white.name)
                || StringUtils.equals(aosStatus.toString(), sign.actual.name)) {
                int value = (int)this.getModel().getValue("aos_completeqty", 0);
                if (value == 0) {
                    this.getModel().setValue("aos_completeqty", 1, 0);
                }
                value = (int)this.getModel().getValue("aos_completeqty", 1);
                if (value == 0) {
                    this.getModel().setValue("aos_completeqty", 2, 1);
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
    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();
        if (sign.itemId.name.equals(name)) {
            AosItemChange();
        } else if (sign.phstate.name.equals(name)) {
            phstateChange();
        } else if (sign.vedioType.name.equals(name)) {
            vedioTypeChange();
        } else if (sign.poNumber.name.equals(name)) {
            poNumberChanged();
        } else if (sign.photoFlag.name.equals(name)) {
            photoChanged();
        }
    }

    /**
     * 是否拍照值改变事件
     */
    private void photoChanged() {
        Object aosStatus = this.getModel().getValue("aos_status");
        Boolean aosPhotoFlag = (Boolean)this.getModel().getValue("aos_photoflag");
        if (sign.newStatus.name.equals(aosStatus) || sign.poComfirm.name.equals(aosStatus)) {
            if (aosPhotoFlag) {
                this.getModel().setValue("aos_reason", null);
                this.getModel().setValue("aos_sameitemid", null);
                this.getView().setEnable(false, "aos_reason");
                this.getView().setEnable(false, "aos_sameitemid");
            } else {
                this.getView().setEnable(true, "aos_reason");
                this.getView().setEnable(true, "aos_sameitemid");
            }
        }
    }

    /**
     * 采购订单值改变时
     */
    private void poNumberChanged() {
        Object aosItemid = this.getModel().getValue(AOS_ITEMID);
        Object aosPonumber = this.getModel().getValue("aos_ponumber");
        if (FndGlobal.IsNotNull(aosPonumber) && FndGlobal.IsNotNull(aosItemid)) {
            DynamicObject aosItemidObject = (DynamicObject)aosItemid;
            Object fid = aosItemidObject.getPkValue();
            DynamicObject bdMaterial = BusinessDataServiceHelper.loadSingle(fid, "bd_material");
            String category = (String)SalUtil.getCategoryByItemId(String.valueOf(fid)).get("name");
            String[] categoryGroup = category.split(",");
            String aosCategory1 = null;
            String aosCategory2 = null;
            String aosCategory3 = null;
            int categoryLength = categoryGroup.length;
            if (categoryLength > 0) {
                aosCategory1 = categoryGroup[0];
            }
            if (categoryLength > 1) {
                aosCategory2 = categoryGroup[1];
            }
            if (categoryLength > TWO) {
                aosCategory3 = categoryGroup[2];
            }
            DynamicObject isSealSample = QueryServiceHelper.queryOne("aos_sealsample", "aos_model",
                new QFilter("aos_item.id", QCP.equals, fid).and("aos_contractnowb", QCP.equals, aosPonumber).toArray());
            if (FndGlobal.IsNotNull(isSealSample)) {
                String aosModel = isSealSample.getString("aos_model");
                FndMsg.debug("aos_model:" + aosModel);
                if (sign.yes.name.equals(aosModel)) {
                    this.getModel().setValue("aos_phstate", "工厂简拍");
                } else if (sign.no.name.equals(aosModel)) {
                    this.getModel().setValue("aos_phstate", null);
                } else if (FndGlobal.IsNull(aosModel)) {
                    boolean exists =
                        Judge3dSelect(aosCategory1, aosCategory2, aosCategory3, bdMaterial.getString("name"));
                    if (exists) {
                        this.getModel().setValue("aos_phstate", "工厂简拍");
                    } else {
                        this.getModel().setValue("aos_phstate", null);
                    }
                }
            } else {
                boolean exists = Judge3dSelect(aosCategory1, aosCategory2, aosCategory3, bdMaterial.getString("name"));
                if (exists) {
                    this.getModel().setValue("aos_phstate", "工厂简拍");
                } else {
                    this.getModel().setValue("aos_phstate", null);
                }
            }
        } else {
            this.getModel().setValue("aos_phstate", null);
        }
    }

    @Override
    public void beforeDoOperation(BeforeDoOperationEventArgs args) {
        FormOperate formOperate = (FormOperate)args.getSource();
        String operatation = formOperate.getOperateKey();
        try {
            if (sign.save.name.equals(operatation)) {
                newControl(this.getModel().getDataEntity(true));
                SyncPhotoList(); // 拍照任务清单同步
                phstateis();
            }
        } catch (FndError fndError) {
            fndError.show(getView());
            args.setCancel(true);
        } catch (Exception ex) {
            FndError.showex(getView(), ex);
            args.setCancel(true);
        }
    }

    @Override
    public void afterDoOperation(AfterDoOperationEventArgs args) {
        FormOperate formOperate = (FormOperate)args.getSource();
        String operatation = formOperate.getOperateKey();
        try {
            if (sign.save.name.equals(operatation)) {
                InitReq();
            }
            StatusControl();
        } catch (FndError fndError) {
            fndError.show(getView(), FndWebHook.urlMms);
        } catch (Exception ex) {
            FndError.showex(getView(), ex, FndWebHook.urlMms);
        }
    }

    private void vedioTypeChange() {
        Object aosVedioType = this.getModel().getValue("aos_vediotype");
        if (sign.shortCut.name.equals(aosVedioType)) {
            this.getModel().setValue("aos_photoflag", false);
            this.getModel().setValue("aos_vedioflag", true);
            this.getModel().setValue("aos_reason", "只拍展示视频");
        }
    }

    private void phstateChange() {
        Object aosPhstate = this.getModel().getValue("aos_phstate");
        if (sign.snapShot.name.equals(aosPhstate)) {
            this.getModel().setValue("aos_3dflag", true);
            this.getModel().setValue("aos_3d_reason", true);
        } else {
            this.getModel().setValue("aos_3dflag", false);
            this.getModel().setValue("aos_3d_reason", false);
        }
    }

    private void phstateis() throws FndError {
        Object state = this.getModel().getValue("aos_status");
        Boolean issys = (Boolean)this.getModel().getValue("aos_sys");
        DynamicObject item = (DynamicObject)this.getModel().getValue("aos_itemid");
        String ponumber = (String)this.getModel().getValue("aos_ponumber");
        String phsta = (String)this.getModel().getValue("aos_phstate");
        if (FndGlobal.IsNotNull(item)) {
            if (issys) {
                if ((sign.newStatus.name.equals(state) || sign.poComfirm.name.equals(state))) {
                    DynamicObjectCollection sealSample = QueryServiceHelper.query("aos_sealsample", "aos_model",
                        new QFilter("aos_item", QCP.equals, item.getPkValue())
                            .and("aos_contractnowb", QCP.equals, ponumber).toArray());
                    if (!QueryServiceHelper.exists(sign.not3d.name,
                        new QFilter("aos_item", QCP.equals, item.getPkValue()).toArray())) {
                        if (sealSample.size() > 0) {
                            for (DynamicObject dy : sealSample) {
                                if ("是".equals(dy.get("aos_model")) && !"工厂简拍".equals(phsta)) {
                                    throw new FndError("该产品可3D建模，不需实物拍摄！");
                                }
                            }
                        } else {
                            Map<String, String> category = getSkuCategory(String.valueOf(item.getPkValue()));
                            String category1 = category.get("aos_category_stat1");
                            String category2 = category.get("aos_category_stat2");
                            String category3 = category.get("aos_category_stat3");
                            if (QueryServiceHelper.exists(sign.select3d.name,
                                new QFilter("aos_category1", QCP.equals, category1)
                                    .and("aos_category2", QCP.equals, category2)
                                    .and("aos_category3", QCP.equals, category3)
                                    .and("aos_name", QCP.equals, item.getString("name")).toArray())
                                && !"工厂简拍".equals(phsta)) {
                                throw new FndError("该产品可3D建模，不需实物拍摄！");
                            }
                        }
                    }
                }
            }
        }
    }

    private void aosSearch() throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            Map<String, Object> map = new HashMap<>(16);
            FormShowParameter parameter = new FormShowParameter();
            parameter.getOpenStyle().setShowType(ShowType.Modal);
            parameter.setFormId("aos_mkt_phoson_form");
            map.put("aos_parentid", this.getView().getModel().getDataEntity().getPkValue());
            parameter.setCustomParam("params", map);
            this.getView().showForm(parameter);
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 跳转到 数据库-摄影标准库
     **/
    private void openPhotoUrl() {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            QFilter filterCate1 = new QFilter("aos_category1_name", "=", this.getModel().getValue("aos_category1"));
            QFilter filterCate2 = new QFilter("aos_category2_name", "=", this.getModel().getValue("aos_category2"));
            QFilter filterCate3 = new QFilter("aos_category3_name", "=", this.getModel().getValue("aos_category3"));
            QFilter filterName = new QFilter("aos_itemnamecn", "=", this.getModel().getValue("aos_itemname"));
            QFilter[] qfs = new QFilter[] {filterCate1, filterCate2, filterCate3, filterName};
            List<Object> listId = QueryServiceHelper.queryPrimaryKeys("aos_mkt_photostd", qfs, null, 1);
            if (listId.size() == 0) {
                this.getView().showMessage("数据库还未建立");
            } else {
                BillShowParameter parameter = new BillShowParameter();
                parameter.setFormId("aos_mkt_photostd");
                parameter.setPkId(listId.get(0));
                parameter.getOpenStyle().setShowType(ShowType.NewWindow);
                this.getView().showForm(parameter);
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 跳转到 布景标准库
     **/
    private void openSenceUrl() {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            QFilter filterCate1 = new QFilter("aos_category1_name", "=", this.getModel().getValue("aos_category1"));
            QFilter filterCate2 = new QFilter("aos_category2_name", "=", this.getModel().getValue("aos_category2"));
            QFilter filterCate3 = new QFilter("aos_category3_name", "=", this.getModel().getValue("aos_category3"));
            QFilter filterName = new QFilter("aos_itemnamecn", "=", this.getModel().getValue("aos_itemname"));
            QFilter[] qfs = new QFilter[] {filterCate1, filterCate2, filterCate3, filterName};
            List<Object> listId = QueryServiceHelper.queryPrimaryKeys("aos_mkt_viewstd", qfs, null, 1);
            if (listId.size() == 0) {
                this.getView().showMessage("数据库还未建立");
            } else {
                BillShowParameter parameter = new BillShowParameter();
                parameter.setFormId("aos_mkt_viewstd");
                parameter.setPkId(listId.get(0));
                parameter.getOpenStyle().setShowType(ShowType.NewWindow);
                this.getView().showForm(parameter);
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 新建设置默认值
     **/
    private void initDefualt() {
        try {
            long currentUserId = UserServiceHelper.getCurrentUserId();
            List<DynamicObject> mapList = Cux_Common_Utl.GetUserOrg(currentUserId);
            if (mapList != null) {
                if (mapList.get(TWO) != null) {
                    this.getModel().setValue("aos_organization1", mapList.get(TWO).get("id"));
                }
                if (mapList.get(THREE) != null) {
                    this.getModel().setValue("aos_organization2", mapList.get(3).get("id"));
                }
            }
        } catch (Exception ex) {
            FndError.showex(getView(), ex, FndWebHook.urlMms);
        }
    }

    /**
     * 拍照需求表同步拍照任务清单
     **/
    private void SyncPhotoList() throws FndError {
        String ErrorMessage = "";
        Object AosBillno = this.getModel().getValue(BILLNO);
        Object AosItemName = this.getModel().getValue(AOS_ITEMNAME);
        Object AosShipdate = this.getModel().getValue(AOS_SHIPDATE);
        Object AosDeveloper = this.getModel().getValue(AOS_DEVELOPER);
        Object AosPoer = this.getModel().getValue(AOS_POER);
        Object AosFollower = this.getModel().getValue(AOS_FOLLOWER);
        Object AosWhiteph = this.getModel().getValue(AOS_WHITEPH);
        Object AosActph = this.getModel().getValue(AOS_ACTPH);
        Object AosVedior = this.getModel().getValue(AOS_VEDIOR);
        Object AosStatus = this.getModel().getValue(AOS_STATUS);
        Object AosPhotoFlag = this.getModel().getValue(AOS_PHOTOFLAG);
        Object AosVedioFlag = this.getModel().getValue(AOS_VEDIOFLAG);
        Object AosItemid = this.getModel().getValue(AOS_ITEMID);
        Object AosPhstate = this.getModel().getValue(AOS_PHSTATE);
        Object aos_sourceid = this.getModel().getValue("aos_sourceid");
        Object aos_address = this.getModel().getValue("aos_address"); // 地址
        Object aos_orgtext = this.getModel().getValue("aos_orgtext"); // 下单国别
        Object aos_urgent = this.getModel().getValue("aos_urgent"); // 紧急提醒

        // 调整为新拍照界面
        String aos_picdesc = "", aos_picdesc1 = ""; // 照片需求
        DynamicObjectCollection dyc_photo =
            this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity6");
        if (dyc_photo.size() > 0) {
            aos_picdesc = dyc_photo.get(0).getString("aos_reqsupp");
            aos_picdesc1 = dyc_photo.get(0).getString("aos_devsupp");
        }

        // 如果新建状态下 单据编号为空 则需要生成对应的拍照任务清单 并回写单据编号
        if ("新建".equals(AosStatus) && (AosBillno == null || "".equals(AosBillno))) {
            DynamicObject aos_mkt_photolist = BusinessDataServiceHelper.newDynamicObject("aos_mkt_photolist");
            aos_mkt_photolist.set(AOS_ITEMID, AosItemid);
            aos_mkt_photolist.set(AOS_ITEMNAME, AosItemName);
            aos_mkt_photolist.set(AOS_PHOTOFLAG, AosPhotoFlag);
            aos_mkt_photolist.set(AOS_VEDIOFLAG, AosVedioFlag);
            aos_mkt_photolist.set(AOS_PHSTATE, AosPhstate);
            aos_mkt_photolist.set(AOS_SHIPDATE, AosShipdate);
            aos_mkt_photolist.set(AOS_DEVELOPER, AosDeveloper);
            aos_mkt_photolist.set(AOS_POER, AosPoer);
            aos_mkt_photolist.set(AOS_FOLLOWER, AosFollower);
            aos_mkt_photolist.set(AOS_WHITEPH, AosWhiteph);
            aos_mkt_photolist.set(AOS_ACTPH, AosActph);
            aos_mkt_photolist.set(AOS_VEDIOR, AosVedior);
            aos_mkt_photolist.set("aos_photourl", "拍照");
            aos_mkt_photolist.set("aos_vediourl", "视频");
            aos_mkt_photolist.set("aos_address", aos_address);
            aos_mkt_photolist.set("aos_orgtext", aos_orgtext);
            aos_mkt_photolist.set("aos_urgent", aos_urgent);
            aos_mkt_photolist.set("aos_picdesc", aos_picdesc);
            aos_mkt_photolist.set("aos_picdesc1", aos_picdesc1);

            if ((Boolean)AosPhotoFlag) {
                aos_mkt_photolist.set("aos_phstatus", "新建");
            }
            if ((Boolean)AosVedioFlag) {
                aos_mkt_photolist.set("aos_vedstatus", "新建");
            }
            aos_mkt_photolist.set("billstatus", "A");
            OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
                new DynamicObject[] {aos_mkt_photolist}, OperateOption.create());
            if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                throw new FndError("拍照任务清单保存失败!");
            } else {
                this.getModel().setValue(BILLNO, aos_mkt_photolist.get("billno"));
                this.getModel().setValue("aos_sourceid", operationrst.getSuccessPkIds().get(0));
                this.getView().setVisible(true, "aos_submit");
            }
        } else {
            // 其他情况下 都需要同步数据至拍照任务清单
            if (!QueryServiceHelper.exists("aos_mkt_photolist", aos_sourceid)) {
                return;
            }
            DynamicObject aos_mkt_photolist = BusinessDataServiceHelper.loadSingle(aos_sourceid, "aos_mkt_photolist");
            // 除状态外字段同步
            aos_mkt_photolist.set("aos_itemid", this.getModel().getValue(AOS_ITEMID));
            aos_mkt_photolist.set("aos_itemname", this.getModel().getValue(AOS_ITEMNAME));
            aos_mkt_photolist.set("aos_samplestatus", this.getModel().getValue("aos_samplestatus"));
            aos_mkt_photolist.set("aos_newitem", this.getModel().getValue(AOS_NEWITEM));
            aos_mkt_photolist.set("aos_newvendor", this.getModel().getValue(AOS_NEWVENDOR));
            aos_mkt_photolist.set("aos_photoflag", this.getModel().getValue(AOS_PHOTOFLAG));
            aos_mkt_photolist.set("aos_phstate", this.getModel().getValue(AOS_PHSTATE));
            aos_mkt_photolist.set("aos_shipdate", this.getModel().getValue(AOS_SHIPDATE));
            aos_mkt_photolist.set("aos_arrivaldate", this.getModel().getValue(AOS_ARRIVALDATE));
            aos_mkt_photolist.set("aos_developer", this.getModel().getValue(AOS_DEVELOPER));
            aos_mkt_photolist.set("aos_poer", this.getModel().getValue(AOS_POER));
            aos_mkt_photolist.set("aos_follower", this.getModel().getValue(AOS_FOLLOWER));
            aos_mkt_photolist.set("aos_whiteph", this.getModel().getValue(AOS_WHITEPH));
            aos_mkt_photolist.set("aos_actph", this.getModel().getValue(AOS_ACTPH));
            aos_mkt_photolist.set("aos_vedior", this.getModel().getValue(AOS_VEDIOR));
            aos_mkt_photolist.set("aos_sampledate", this.getModel().getValue("aos_sampledate"));
            aos_mkt_photolist.set("aos_installdate", this.getModel().getValue("aos_installdate"));
            aos_mkt_photolist.set("aos_whitedate", this.getModel().getValue("aos_whitedate"));
            aos_mkt_photolist.set("aos_actdate", this.getModel().getValue("aos_actdate"));
            aos_mkt_photolist.set("aos_picdate", this.getModel().getValue("aos_picdate"));
            aos_mkt_photolist.set("aos_funcpicdate", this.getModel().getValue("aos_funcpicdate"));
            aos_mkt_photolist.set("aos_vedio", this.getModel().getValue("aos_vedio"));

            OperationServiceHelper.executeOperate("save", "aos_mkt_photolist", new DynamicObject[] {aos_mkt_photolist},
                OperateOption.create());

        }

    }

    /**
     * 初始化图片
     **/
    private void InitPic() {
        // 数据层
        Object AosItemid = this.getModel().getValue(AOS_ITEMID);
        // 如果存在物料 设置图片
        if (AosItemid != null) {
            QFilter filter =
                new QFilter("entryentity.aos_articlenumber", QCP.equals, ((DynamicObject)AosItemid).getPkValue())
                    .and("billstatus", QCP.in, new String[] {"C", "D"});
            DynamicObjectCollection query = QueryServiceHelper.query("aos_newarrangeorders", "entryentity.aos_picture",
                filter.toArray(), "aos_creattime desc");
            if (query != null && query.size() > 0) {
                Object o = query.get(0).get("entryentity.aos_picture");
                this.getModel().setValue("aos_picturefield", o);
            }
        }

        // 对于流程图
        Object aos_type = this.getModel().getValue("aos_type");

        if (aos_type == null || "".equals(aos_type) || "null".equals(aos_type)) {
            this.getModel().setValue("aos_flowpic", MKTS3PIC.aos_flowpic);
        } else if ("拍照".equals(aos_type)) {
            this.getModel().setValue("aos_flowpic", MKTS3PIC.aos_flowpic);
        } else if ("视频".equals(aos_type)) {
            this.getModel().setValue("aos_flowpic", MKTS3PIC.aos_flowved);
        }

    }

    /**
     * 新建初始化
     **/
    private void InitReq() throws FndError {
        // 数据层
        Boolean AosInit = (Boolean)this.getModel().getValue("aos_init");

        // 数据赋值
        if (!AosInit) {
            // 照片需求(new1)
            this.getModel().deleteEntryData("aos_entryentity5");
            this.getModel().batchCreateNewEntryRow("aos_entryentity5", 1);
            // 照片需求(new2)
            this.getModel().deleteEntryData("aos_entryentity6");
            this.getModel().batchCreateNewEntryRow("aos_entryentity6", 1);
            // 照片需求
            this.getModel().deleteEntryData("aos_entryentity");
            this.getModel().batchCreateNewEntryRow("aos_entryentity", 2);
            this.getModel().setValue("aos_applyby", "申请人", 0);
            this.getModel().setValue("aos_applyby", "开发/采购", 1);
            // 视频需求
            this.getModel().deleteEntryData("aos_entryentity1");
            this.getModel().batchCreateNewEntryRow("aos_entryentity1", 2);
            this.getModel().setValue("aos_applyby2", "申请人", 0);
            this.getModel().setValue("aos_applyby2", "开发/采购", 1);
            // 拍摄情况
            this.getModel().deleteEntryData("aos_entryentity2");
            this.getModel().batchCreateNewEntryRow("aos_entryentity2", 2);
            this.getModel().setValue("aos_phtype", "白底", 0);
            this.getModel().setValue("aos_phtype", "实景", 1);
            // 流程退回原因
            this.getModel().deleteEntryData("aos_entryentity3");
            this.getModel().batchCreateNewEntryRow("aos_entryentity3", 2);
            this.getModel().setValue("aos_returnby", "摄影师", 0);
            this.getModel().setValue("aos_returnby", "开发", 1);
            // 初始化标记
            this.getModel().setValue("aos_init", true);
        }

    }

    /**
     * 物料值改变
     **/
    private void AosItemChange() {
        Object AosItemid = this.getModel().getValue(AOS_ITEMID);
        if (AosItemid == null) {
            // 清空值
            this.getModel().setValue(AOS_ITEMNAME, null);
            this.getModel().setValue(AOS_CONTRYBRAND, null);
            this.getModel().setValue(AOS_SPECIFICATION, null);
            this.getModel().setValue(AOS_SETING1, null);
            this.getModel().setValue(AOS_SETING2, null);
            this.getModel().setValue(AOS_SELLINGPOINT, null);
            this.getModel().setValue(AOS_VENDOR, null);
            this.getModel().setValue(AOS_CITY, null);
            this.getModel().setValue(AOS_CONTACT, null);
            this.getModel().setValue(AOS_ADDRESS, null);
            this.getModel().setValue(AOS_PHONE, null);
            this.getModel().setValue(AOS_PHSTATE, null);
            this.getModel().setValue(AOS_DEVELOPER, null);
            this.getModel().setValue(AOS_POER, null);
            this.getModel().setValue(AOS_FOLLOWER, null);
            this.getModel().setValue("aos_orgtext", null);
            this.getModel().setValue("aos_sale", null);
            this.getModel().setValue("aos_vedior", null);
            this.getModel().setValue("aos_3d", null);
            this.getModel().setValue(AOS_SHIPDATE, null);
            this.getModel().setValue(AOS_URGENT, null);
            this.getModel().setValue("aos_is_saleout", false); // 爆品
            this.getModel().setValue("aos_vedioflag", false); // 是否拍视频
            // 清空图片
            Image image = this.getControl("aos_image");
            image.setUrl(null);
            this.getModel().setValue("aos_picturefield", null);
            // 清空品牌信息
            this.getModel().deleteEntryData("aos_entryentity4");
            // RGB颜色
            this.getModel().setValue("aos_rgb", null);
        } else {
            DynamicObject AosItemidObject = (DynamicObject)AosItemid;
            Object fid = AosItemidObject.getPkValue();
            String aos_contrybrandStr = "";
            String aos_orgtext = "";
            DynamicObject bd_material = BusinessDataServiceHelper.loadSingle(fid, "bd_material");
            DynamicObjectCollection aosContryentryS = bd_material.getDynamicObjectCollection("aos_contryentry");
            String ItemNumber = bd_material.getString("number");
            List<DynamicObject> listCountry = aosContryentryS.stream()
                .sorted(Comparator.comparingInt(dy -> dy.getDynamicObject("aos_nationality").getInt("aos_orderby")))
                .collect(Collectors.toList());
            String category = (String)SalUtil.getCategoryByItemId(String.valueOf(fid)).get("name");
            String[] category_group = category.split(",");
            String AosCategory1 = null;
            String AosCategory2 = null;
            String AosCategory3 = null;
            int category_length = category_group.length;
            if (category_length > 0) {
                AosCategory1 = category_group[0];
            }
            if (category_length > 1) {
                AosCategory2 = category_group[1];
            }
            if (category_length > 2) {
                AosCategory3 = category_group[2];
            }
            this.getModel().deleteEntryData("aos_entryentity4");
            int i = 1;
            // 获取所有国家品牌 字符串拼接 终止
            for (DynamicObject aos_contryentry : listCountry) {
                DynamicObject aos_nationality = aos_contryentry.getDynamicObject("aos_nationality");
                String aos_nationalitynumber = aos_nationality.getString("number");
                if ("IE".equals(aos_nationalitynumber)) {
                    continue;
                }
                Object org_id = aos_nationality.get("id"); // ItemId
                int OsQty = iteminfo.GetItemOsQty(org_id, fid);
                int SafeQty = iteminfo.GetSafeQty(org_id);
                if ("C".equals(aos_contryentry.getString("aos_contryentrystatus")) && OsQty < SafeQty) {
                    continue;
                }
                aos_orgtext = aos_orgtext + aos_nationalitynumber + ";";

                Object obj = aos_contryentry.getDynamicObject("aos_contrybrand");
                if (obj == null) {
                    continue;
                }
                String value = aos_contryentry.getDynamicObject("aos_contrybrand").getString("name");
                String Str = "";

                if ("US".equals(aos_nationalitynumber) || "CA".equals(aos_nationalitynumber)
                    || "UK".equals(aos_nationalitynumber)) {
                    Str = "";
                } else {
                    Str = "-" + aos_nationalitynumber;
                }

                this.getModel().batchCreateNewEntryRow("aos_entryentity4", 1);
                this.getModel().setValue("aos_orgshort", aos_nationalitynumber, i - 1);
                this.getModel().setValue("aos_brand", value, i - 1);

                // 设置编辑人员
                DynamicObject aos_mkt_progorguser = QueryServiceHelper.queryOne("aos_mkt_progorguser", "aos_oueditor",
                    new QFilter("aos_orgid.number", QCP.equals, aos_nationalitynumber)
                        .and("aos_category1", QCP.equals, AosCategory1).and("aos_category2", QCP.equals, AosCategory2)
                        .toArray());
                if (FndGlobal.IsNotNull(aos_mkt_progorguser)) {
                    this.getModel().setValue("aos_editor", aos_mkt_progorguser.get("aos_oueditor"), i - 1);
                }

                String first = ItemNumber.substring(0, 1);

                // 含品牌
                if ("US".equals(aos_nationalitynumber) || "CA".equals(aos_nationalitynumber)
                    || "UK".equals(aos_nationalitynumber))
                // 非小语种
                {
                    this.getModel().setValue("aos_s3address1", "https://uspm.aosomcdn.com/videos/en/" + first + "/"
                        + ItemNumber + "/" + ItemNumber + "-" + value + ".mp4", i - 1);
                } else
                // 小语种
                {
                    this.getModel().setValue("aos_s3address1", "https://uspm.aosomcdn.com/videos/en/" + first + "/"
                        + ItemNumber + "/" + ItemNumber + "-" + value + "-" + aos_nationalitynumber + ".mp4", i - 1);
                }

                // 不含品牌
                if ("US".equals(aos_nationalitynumber) || "CA".equals(aos_nationalitynumber)
                    || "UK".equals(aos_nationalitynumber))
                // 非小语种
                {
                    this.getModel().setValue("aos_s3address2",
                        "https://uspm.aosomcdn.com/videos/en/" + first + "/" + ItemNumber + "/" + ItemNumber + ".mp4",
                        i - 1);
                } else
                // 小语种
                {
                    this.getModel().setValue("aos_s3address2", "https://uspm.aosomcdn.com/videos/en/" + first + "/"
                        + ItemNumber + "/" + ItemNumber + "-" + aos_nationalitynumber + ".mp4", i - 1);
                }

                if ("US".equals(aos_nationalitynumber) || "CA".equals(aos_nationalitynumber)
                    || "UK".equals(aos_nationalitynumber)) {
                    this.getModel().setValue("aos_filename", ItemNumber + "-" + value, i - 1);
                } else {
                    this.getModel().setValue("aos_filename", ItemNumber + "-" + value + "-" + aos_nationalitynumber,
                        i - 1);
                }
                i++;
                if (!aos_contrybrandStr.contains(value)) {
                    aos_contrybrandStr = aos_contrybrandStr + value + ";";
                }
            }

            // 图片字段
            QFilter filter = new QFilter("entryentity.aos_articlenumber", QCP.equals, AosItemidObject.getPkValue())
                .and("billstatus", QCP.in, new String[] {"C", "D"});
            DynamicObjectCollection query = QueryServiceHelper.query("aos_newarrangeorders", "entryentity.aos_picture",
                filter.toArray(), "aos_creattime desc");
            if (query != null && query.size() > 0) {
                Object o = query.get(0).get("entryentity.aos_picture");
                this.getModel().setValue("aos_picturefield", o);
            }

            // 赋值物料相关
            String aosItemName = bd_material.getString("name");
            this.getModel().setValue(AOS_ITEMNAME, aosItemName);
            this.getModel().setValue(AOS_CONTRYBRAND, aos_contrybrandStr);
            this.getModel().setValue("aos_orgtext", aos_orgtext);
            this.getModel().setValue(AOS_SPECIFICATION, bd_material.get("aos_specification_cn"));
            this.getModel().setValue(AOS_SETING1, bd_material.get("aos_seting_cn"));
            this.getModel().setValue(AOS_SETING2, bd_material.get("aos_seting_en"));
            this.getModel().setValue(AOS_SELLINGPOINT, bd_material.get("aos_sellingpoint"));
            this.getModel().setValue(AOS_DEVELOPER, bd_material.get("aos_developer"));
            this.getModel().setValue(AOS_CATEGORY1, AosCategory1);
            this.getModel().setValue(AOS_CATEGORY2, AosCategory2);
            this.getModel().setValue(AOS_CATEGORY3, AosCategory3);

            // 根据大类中类获取对应营销人员
            if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("") && !AosCategory2.equals("")) {
                QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
                QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
                QFilter[] filters = new QFilter[] {filter_category1, filter_category2};
                String SelectStr = "aos_actph,aos_whiteph,aos_designer,aos_salreq,aos_photoer,aos_3d";
                DynamicObject aos_mkt_proguser = QueryServiceHelper.queryOne("aos_mkt_proguser", SelectStr, filters);
                if (aos_mkt_proguser != null) {
                    this.getModel().setValue(AOS_ACTPH, aos_mkt_proguser.get(AOS_ACTPH));
                    this.getModel().setValue(AOS_WHITEPH, aos_mkt_proguser.get(AOS_WHITEPH));
                    this.getModel().setValue(AOS_DESIGNER, aos_mkt_proguser.get(AOS_DESIGNER));
                    this.getModel().setValue("aos_sale", aos_mkt_proguser.get("aos_salreq"));
                    this.getModel().setValue("aos_vedior", aos_mkt_proguser.get("aos_photoer"));
                    this.getModel().setValue("aos_3d", aos_mkt_proguser.get("aos_3d"));
                }
            }
            // 对于供应商
            long VendorId = Cux_Common_Utl.GetItemVendor(fid);
            if (VendorId != -1) {
                DynamicObject bd_supplier = BusinessDataServiceHelper.loadSingle(VendorId, "bd_supplier");
                // 赋值供应商相关
                this.getModel().setValue(AOS_VENDOR, bd_supplier.get("name"));
                this.getModel().setValue(AOS_POER, bd_supplier.get("aos_purer"));
                this.getModel().setValue(AOS_FOLLOWER, bd_supplier.get("aos_documentary"));
                // 供应商地址
                QFilter[] filterRelation = new QFilter("supplier", QCP.equals, VendorId).toArray();
                DynamicObject bd_address = BusinessDataServiceHelper.loadSingle("bd_address",
                    "admindivision,admindivision.parent,aos_contacts,aos_addfulladdress,aos_addphone", filterRelation);
                if (bd_address != null) {
                    QFilter[] filterid = new QFilter("id", QCP.equals, bd_address.get("admindivision")).toArray();
                    DynamicObject bd_admindivision =
                        BusinessDataServiceHelper.loadSingle("bd_admindivision", "parent.name", filterid);
                    if (bd_admindivision != null) {
                        this.getModel().setValue(AOS_CITY, bd_admindivision.get("parent.name"));
                    }
                    this.getModel().setValue(AOS_CONTACT, bd_address.get("aos_contacts"));
                    this.getModel().setValue(AOS_ADDRESS, bd_address.get("aos_addfulladdress"));
                    this.getModel().setValue(AOS_PHONE, bd_address.get("aos_addphone"));
                }
            }

            Object aos_ponumber = this.getModel().getValue("aos_ponumber");
            if (FndGlobal.IsNotNull(aos_ponumber)) {
                DynamicObject isSealSample = QueryServiceHelper.queryOne("aos_sealsample", "aos_model",
                    new QFilter("aos_item.id", QCP.equals, fid).and("aos_contractnowb", QCP.equals, aos_ponumber)
                        .toArray());//
                if (FndGlobal.IsNotNull(isSealSample)) {
                    String aos_model = isSealSample.getString("aos_model");
                    if ("是".equals(aos_model)) {
                        this.getModel().setValue("aos_phstate", "工厂简拍");
                    } else if ("否".equals(aos_model)) {
                        this.getModel().setValue("aos_phstate", null);
                    } else if (FndGlobal.IsNull(aos_model)) {
                        Boolean exists =
                            Judge3dSelect(AosCategory1, AosCategory2, AosCategory3, bd_material.getString("name"));
                        if (exists) {
                            this.getModel().setValue("aos_phstate", "工厂简拍");
                        } else {
                            this.getModel().setValue("aos_phstate", null);
                        }
                    }
                } else {
                    Boolean exists =
                        Judge3dSelect(AosCategory1, AosCategory2, AosCategory3, bd_material.getString("name"));
                    if (exists) {
                        this.getModel().setValue("aos_phstate", "工厂简拍");
                    } else {
                        this.getModel().setValue("aos_phstate", null);
                    }
                }
            }

            // 增加爆品字段
            Boolean aos_is_saleout = Is_saleout(fid);
            this.getModel().setValue("aos_is_saleout", aos_is_saleout);
            this.getModel().setValue("aos_vedioflag", aos_is_saleout);

            // 摄影标准库字段
            this.getModel().deleteEntryData("aos_entryentity5");
            this.getModel().batchCreateNewEntryRow("aos_entryentity5", 1);
            this.getModel().setValue("aos_refer", "摄影标准库", 0);
            DynamicObject aosMktPhotoStd =
                QueryServiceHelper.queryOne("aos_mkt_photostd", "aos_firstpicture,aos_other,aos_require",
                    new QFilter("aos_category1_name", QCP.equals, AosCategory1)
                        .and("aos_category2_name", QCP.equals, AosCategory2)
                        .and("aos_category3_name", QCP.equals, AosCategory3)
                        .and("aos_itemnamecn", QCP.equals, aosItemName).toArray());
            if (FndGlobal.IsNotNull(aosMktPhotoStd)) {
                this.getModel().setValue("aos_reqfirst", aosMktPhotoStd.getString("aos_firstpicture"), 0);
                this.getModel().setValue("aos_reqother", aosMktPhotoStd.getString("aos_other"), 0);
                this.getModel().setValue("aos_detail", aosMktPhotoStd.getString("aos_require"), 0);
            }

            // 布景标准库字段
            DynamicObject aosMktViewStd = QueryServiceHelper.queryOne("aos_mkt_viewstd",
                "aos_scene1,aos_object1,aos_scene2,aos_object2,aos_itemnamecn,aos_descpic",
                new QFilter("aos_category1_name", QCP.equals, AosCategory1)
                    .and("aos_category2_name", QCP.equals, AosCategory2)
                    .and("aos_category3_name", QCP.equals, AosCategory3).and("aos_itemnamecn", QCP.equals, aosItemName)
                    .toArray());
            if (FndGlobal.IsNotNull(aosMktViewStd)) {
                this.getModel().setValue("aos_scene1", aosMktViewStd.getString("aos_scene1"), 0);
                this.getModel().setValue("aos_object1", aosMktViewStd.getString("aos_object1"), 0);
                this.getModel().setValue("aos_scene2", aosMktViewStd.getString("aos_scene2"), 0);
                this.getModel().setValue("aos_object2", aosMktViewStd.getString("aos_object2"), 0);
                this.getModel().setValue("aos_descpic", aosMktViewStd.getString("aos_descpic"), 0);
            }

            DynamicObject aos_color = bd_material.getDynamicObject("aos_color");
            if (FndGlobal.IsNotNull(aos_color)) {
                String aos_rgb = aos_color.getString("aos_rgb");
                this.getModel().setValue("aos_rgb", aos_rgb);
                this.getModel().setValue("aos_colors", aos_color.get("aos_colors"));
                this.getModel().setValue("aos_colorname", aos_color.get("name"));
                this.getModel().setValue("aos_colorex", aos_color);

                HashMap<String, Object> fieldMap = new HashMap<>();
                // 设置前景色
                fieldMap.put(ClientProperties.BackColor, aos_rgb);
                // 同步指定元数据到控件
                this.getView().updateControlMetadata("aos_color", fieldMap);
            }
        }
    }

    /**
     * 全局状态控制
     **/
    private void StatusControl() {
        // 数据层
        Object AosStatus = this.getModel().getValue(AOS_STATUS);
        DynamicObject AosUser = (DynamicObject)this.getModel().getValue(AOS_USER);
        Object CurrentUserId = UserServiceHelper.getCurrentUserId();
        Object CurrentUserName = UserServiceHelper.getUserInfoByID((long)CurrentUserId).get("name");
        Object AosBillno = this.getModel().getValue(BILLNO);
        // 图片控制
        InitPic();

        // 锁住需要控制的字段
        this.getView().setEnable(false, AOS_PHOTOFLAG);
        this.getView().setEnable(false, AOS_REASON);
        this.getView().setEnable(false, AOS_SAMEITEMID);
        this.getView().setEnable(false, AOS_VEDIOFLAG);
        this.getView().setEnable(false, AOS_NEWITEM);
        this.getView().setEnable(false, AOS_NEWVENDOR);
        this.getView().setEnable(false, AOS_PONUMBER);
        this.getView().setEnable(false, AOS_LINENUMBER);
        this.getView().setEnable(false, AOS_PHSTATE);
        this.getView().setEnable(false, "aos_vediotype");

        this.getView().setEnable(false, "aos_phreason");
        this.getView().setEnable(false, "aos_dereason");

        // this.getView().setEnable(false, aos_poer);
        // this.getView().setEnable(false, aos_developer);
        // this.getView().setEnable(false, aos_follower);
        this.getView().setVisible(false, "aos_confirm");// 确认按钮
        this.getView().setVisible(false, "aos_back");// 退回按钮
        this.getView().setVisible(true, "aos_submit");
        this.getView().setVisible(true, "bar_new");
        this.getView().setVisible(false, "aos_3dflag");
        this.getView().setVisible(false, "aos_3d_reason");
        this.getView().setVisible(false, "aos_search");// 查看子流程
        this.getView().setVisible(true, "aos_flexpanelap11");
        this.getView().setVisible(true, "aos_flexpanelap12");
        this.getView().setVisible(true, "aos_flexpanelap13");
        this.getView().setVisible(true, "aos_flexpanelap14");
        this.getView().setVisible(true, "aos_flexpanelap18");
        this.getView().setVisible(true, "aos_flexpanelap15");
        this.getView().setVisible(true, "aos_flexpanelap20");

        this.getView().setVisible(true, "aos_flexpanelap14");
        this.getView().setVisible(false, "aos_3datta");

        // 当前节点操作人不为当前用户 全锁
        if (!AosUser.getPkValue().toString().equals(CurrentUserId.toString())) {
            this.getView().setEnable(false, "contentpanelflex");// 主界面面板
            this.getView().setEnable(false, "bar_save");
            this.getView().setEnable(false, "aos_submit");
            this.getView().setEnable(false, "aos_confirm");
        }

        // 状态控制
        Map<String, Object> map = new HashMap<>();
        if (AosBillno == null || AosBillno.equals("")) {
            this.getView().setVisible(false, "aos_submit");
            this.getView().setVisible(false, "aos_flexpanelap12");
            this.getView().setVisible(false, "aos_flexpanelap13");
            this.getView().setVisible(false, "aos_flexpanelap14");
            this.getView().setVisible(false, "aos_flexpanelap18");
            this.getView().setVisible(false, "aos_flexpanelap15");
            this.getView().setVisible(false, "aos_flexpanelap20");
        }
        if ("新建".equals(AosStatus) || "开发/采购确认".equals(AosStatus) || "跟单提样".equals(AosStatus)) {
            this.getView().setEnable(true, AOS_PHOTOFLAG);
            this.getView().setEnable(true, AOS_REASON);
            this.getView().setEnable(true, AOS_SAMEITEMID);
            this.getView().setEnable(true, AOS_VEDIOFLAG);
            this.getView().setEnable(true, AOS_PHSTATE);
        }
        if ("新建".equals(AosStatus)) {
            this.getView().setEnable(true, AOS_NEWITEM);
            this.getView().setEnable(true, AOS_NEWVENDOR);
            this.getView().setEnable(true, "aos_vediotype");
        } else if ("开发/采购确认".equals(AosStatus)) {
            this.getView().setEnable(true, AOS_PONUMBER);
            this.getView().setEnable(true, AOS_LINENUMBER);
            this.getView().setEnable(true, AOS_POER);
            this.getView().setEnable(true, AOS_DEVELOPER);
        } else if ("视频更新".equals(AosStatus)) {
            this.getView().setEnable(false, "contentpanelflex");// 主界面面板
            this.getView().setVisible(false, "aos_submit");// 提交
            this.getView().setVisible(false, "bar_save");// 保存
            this.getView().setVisible(true, "aos_search");// 查看子流程
        } else if ("跟单提样".equals(AosStatus)) {
            // 跟单提样状态下不可修改 等待入库单回传
            this.getView().setEnable(false, "contentpanelflex");// 主界面面板
            this.getView().setVisible(false, "aos_submit");// 提交
            this.getView().setVisible(false, "bar_save");// 保存
        } else if ("字幕翻译".equals(AosStatus)) {
            // 字幕翻译状态下不可修改 等待入库单回传
            this.getView().setEnable(false, "contentpanelflex");// 主界面面板
            this.getView().setVisible(false, "aos_submit");// 提交
            this.getView().setVisible(false, "bar_save");// 保存
        } else if ("3D建模".equals(AosStatus)) {
            // 3D建模状态下不可修改 等待3D设计表回传
            this.getView().setEnable(false, "contentpanelflex");// 主界面面板
            this.getView().setVisible(false, "aos_submit");// 提交
            this.getView().setVisible(false, "bar_save");// 保存
        } else if ("不需拍".equals(AosStatus)) {
            this.getView().setEnable(false, "contentpanelflex");// 主界面面板
            this.getView().setVisible(false, "aos_submit");// 提交
            this.getView().setVisible(false, "bar_save");// 保存
        } else if ("开发/采购确认图片".equals(AosStatus)) {
            this.getView().setEnable(true, "contentpanelflex");
            this.getView().setEnable(false, "aos_flexpanelap");
            this.getView().setEnable(false, "aos_flexpanelap1");
            this.getView().setEnable(false, "aos_flexpanelap8");
            this.getView().setEnable(false, "aos_flexpanelap11");
            this.getView().setEnable(false, "aos_flexpanelap12");
            this.getView().setEnable(false, "aos_flexpanelap13");
            this.getView().setEnable(false, "aos_flexpanelap18");
            this.getView().setEnable(false, "aos_flexpanelap15");
            this.getView().setEnable(false, "aos_flexpanelap20");
            this.getView().setVisible(false, "aos_submit");// 提交
            this.getView().setVisible(false, "bar_save");// 保存
            if (AosUser.getPkValue().toString().equals(CurrentUserId.toString())) {
                this.getView().setVisible(true, "aos_confirm");// 确认
                this.getView().setVisible(true, "aos_back");// 退回按钮
                this.getView().setEnable(true, "aos_dereason");// 开发退回原因
            }
        } else if ("已完成".equals(AosStatus)) {
            // this.getView().setEnable(false, "titlepanel");// 标题面板
            this.getView().setEnable(false, "contentpanelflex");// 主界面面板
            this.getView().setVisible(false, "bar_save");
            this.getView().setVisible(false, "aos_submit");
            this.getView().setVisible(false, "aos_confirm");
        } else if ("白底拍摄".equals(AosStatus)) {
            this.getView().setVisible(true, "aos_back");// 退回按钮
            this.getView().setEnable(true, "aos_phreason");// 摄影师退回原因
            this.getView().setVisible(true, "aos_3dflag");
            this.getView().setVisible(true, "aos_3d_reason");
            // 白底摄影师
            DynamicObject aos_whiteph = this.getModel().getDataEntity(true).getDynamicObject("aos_whiteph");
            if (aos_whiteph != null) {
                if (aos_whiteph.getLong("id") == RequestContext.get().getCurrUserId()) {
                    this.getView().setVisible(true, "aos_submit");
                    this.getView().setEnable(true, "aos_submit");
                }
            }
        } else if ("编辑确认".equals(AosStatus)) {
            map.put(ClientProperties.Text, new LocaleString("编辑确认"));
            this.getView().updateControlMetadata("aos_submit", map);
            this.getView().setVisible(true, "aos_submit");
            this.getView().setEnable(false, "contentpanelflex");// 主界面面板
            this.getView().setVisible(false, "bar_save");
        } else if ("开发确认:视频".equals(AosStatus)) {
            map.put(ClientProperties.Text, new LocaleString("开发确认:视频"));
            this.getView().updateControlMetadata("aos_submit", map);
            this.getView().setVisible(true, "aos_submit");
            this.getView().setVisible(false, "bar_save");
            this.getView().setVisible(true, "aos_back");// 退回按钮

            this.getView().setEnable(true, "aos_dereason");// 开发退回原因

            this.getView().setEnable(true, "aos_flexpanelap");
            this.getView().setEnable(true, "aos_flexpanelap1");
            this.getView().setEnable(true, "aos_flexpanelap11");
            this.getView().setEnable(true, "aos_flexpanelap12");
            this.getView().setEnable(true, "aos_flexpanelap13");
            this.getView().setEnable(true, "aos_flexpanelap18");
            this.getView().setEnable(true, "aos_flexpanelap20");
            this.getView().setEnable(true, "aos_flexpanelap15");
            this.getView().setEnable(true, "aos_flexpanelap141");

        } else if ("视频剪辑".equals(AosStatus)) {
            this.getView().setEnable(true, "aos_flexpanelap20");
            map.put(ClientProperties.Text, new LocaleString("提交"));
            this.getView().updateControlMetadata("aos_submit", map);
            // 是否3D、3D原因2个字段可编辑
            this.getView().setEnable(true, "aos_3dflag");
            this.getView().setEnable(true, "aos_3d_reason");
            this.getView().setVisible(true, "aos_3dflag");
            this.getView().setVisible(true, "aos_3d_reason");
            this.getView().setVisible(true, "aos_3datta");

        } else if ("实景拍摄".equals(AosStatus)) {
            map.put(ClientProperties.Text, new LocaleString("提交"));
            this.getView().updateControlMetadata("aos_submit", map);
            this.getView().setVisible(true, "aos_3dflag");
            this.getView().setVisible(true, "aos_3d_reason");
            // 实景摄影师
            DynamicObject aos_whiteph = this.getModel().getDataEntity(true).getDynamicObject("aos_actph");
            if (aos_whiteph != null) {
                if (aos_whiteph.getLong("id") == RequestContext.get().getCurrUserId()) {
                    this.getView().setVisible(true, "aos_submit");
                    this.getView().setEnable(true, "aos_submit");
                }
            }
        } else if ("视频拍摄".equals(AosStatus)) {
            // 摄影师
            DynamicObject aos_whiteph = this.getModel().getDataEntity(true).getDynamicObject("aos_vedior");
            if (aos_whiteph != null) {
                if (aos_whiteph.getLong("id") == RequestContext.get().getCurrUserId()) {
                    this.getView().setVisible(true, "aos_submit");
                    this.getView().setEnable(true, "aos_submit");
                    this.getView().setVisible(true, "bar_new");
                }
            }
        }

        if (FndGlobal.IsNotNull(this.getModel().getValue("aos_rgb"))) {
            String aos_rgb = this.getModel().getValue("aos_rgb").toString();
            DynamicObject aos_colorex = (DynamicObject)this.getModel().getValue("aos_colorex");

            this.getModel().setValue("aos_rgb", aos_rgb);
            this.getModel().setValue("aos_colors", aos_colorex.get("aos_colors"));
            this.getModel().setValue("aos_colorname", aos_colorex.get("name"));
            this.getModel().setValue("aos_colorex", aos_colorex);

            HashMap<String, Object> fieldMap = new HashMap<>();
            fieldMap.put(ClientProperties.BackColor, aos_rgb);
            this.getView().updateControlMetadata("aos_color", fieldMap);

        }

        Boolean aosPhotoFlag = (Boolean)this.getModel().getValue("aos_photoflag");
        if ("新建".equals(AosStatus) || "开发/采购确认".equals(AosStatus)) {
            if (aosPhotoFlag) {
                this.getModel().setValue("aos_reason", null);
                this.getModel().setValue("aos_sameitemid", null);
                this.getView().setEnable(false, "aos_reason");
                this.getView().setEnable(false, "aos_sameitemid");
            } else {
                this.getView().setEnable(true, "aos_reason");
                this.getView().setEnable(true, "aos_sameitemid");
            }
        }

        InitDifference();
    }

    /**
     * 拍照与视频界面显示做区分
     **/
    private void InitDifference() {
        Object aos_type = this.getModel().getValue("aos_type");
        if (Cux_Common_Utl.IsNull(aos_type)) {
            return;
        }

        // 视频需要隐藏字段
        if ("视频".equals(aos_type)) {
            this.getView().setVisible(true, AOS_PHOTOFLAG);
            this.getView().setVisible(true, AOS_VEDIOFLAG);
            this.getView().setVisible(false, AOS_REQTYPE);
            this.getView().setVisible(false, AOS_ARRIVALDATE);
            this.getView().setVisible(false, AOS_REASON);
            this.getView().setVisible(false, AOS_SAMEITEMID);
            this.getView().setVisible(false, AOS_CONTRYBRAND);
            this.getView().setVisible(false, AOS_NEWITEM);
            this.getView().setVisible(false, AOS_NEWVENDOR);
            this.getView().setVisible(false, AOS_PONUMBER);
            this.getView().setVisible(false, AOS_LINENUMBER);
            this.getView().setVisible(false, "aos_earlydate");
            this.getView().setVisible(false, "aos_checkdate");
            this.getView().setVisible(false, AOS_CATEGORY1);
            this.getView().setVisible(false, AOS_CATEGORY2);
            this.getView().setVisible(false, AOS_CATEGORY3);
            this.getView().setVisible(false, AOS_VENDOR);
            this.getView().setVisible(false, AOS_CITY);
            this.getView().setVisible(false, AOS_CONTACT);
            this.getView().setVisible(false, AOS_ADDRESS);
            this.getView().setVisible(false, AOS_PHONE);
            this.getView().setVisible(false, "aos_flexpanelap15");// 流程节点时间
            this.getView().setVisible(false, "aos_flexpanelap14");// 流程退回原因
            this.getView().setVisible(false, "aos_flexpanelap13");// 拍摄情况
            this.getView().setVisible(false, "aos_flexpanelap11");// 拍照需求
        } else if ("拍照".equals(aos_type)) {
            this.getView().setVisible(false, "aos_flexpanelap12");// 视频需求
            this.getView().setVisible(false, "aos_flexpanelap18");// 字幕需求
        }
    }

    /**
     * 打开历史记录
     **/
    private void aos_history() throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope scope = span.makeCurrent()) {
            Cux_Common_Utl.OpenHistory(this.getView());
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 退回
     **/
    private void aos_back() throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope scope = span.makeCurrent()) {
            // 先做数据校验判断是否可以确认
            saveControl(this.getModel().getDataEntity(true));
            String AosStatus = this.getModel().getValue(AOS_STATUS).toString();// 根据状态判断当前流程节点
            boolean is_return = false;
            switch (AosStatus) {
                case "白底拍摄":
                    BackForWhite();
                    is_return = true;
                    break;
                case "开发/采购确认图片":
                    BackForConfirm();
                    is_return = true;
                    break;
                case "开发确认:视频":
                    BackForConfirm();
                    is_return = true;
                    break;
            }
            if (is_return) {
                FndHistory.Create(this.getView(), "退回", AosStatus + " 退回");
                int returnTimes = (int)this.getModel().getValue("aos_returntimes") + 1;
                this.getModel().setValue("aos_returntimes", returnTimes);
                this.getView().invokeOperation("save");
                this.getView().invokeOperation("refresh");
            }
            // 确认完成后做新的界面状态控制
            StatusControl();
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 确认
     **/
    private void aos_confirm() throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope scope = span.makeCurrent()) {
            // 先做数据校验判断是否可以确认
            saveControl(this.getModel().getDataEntity(true));

            // 异常参数
            String ErrorMessage = "";

            // 数据层
            Object AosSourceid = this.getModel().getValue(AOS_SOURCEID);
            Object AosType = this.getModel().getValue("aos_type");

            // 回写拍照任务清单
            if (QueryServiceHelper.exists("aos_mkt_photolist", AosSourceid)) {
                DynamicObject aos_mkt_photolist =
                    BusinessDataServiceHelper.loadSingle(AosSourceid, "aos_mkt_photolist");
                if ("拍照".equals(AosType)) {
                    aos_mkt_photolist.set("aos_phstatus", "已完成");
                } else if ("视频".equals(AosType)) {
                    aos_mkt_photolist.set("aos_vedstatus", "已完成");
                }
                OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
                    new DynamicObject[] {aos_mkt_photolist}, OperateOption.create());
                if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                    ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "拍照任务清单保存失败!");
                    FndError fndMessage = new FndError(ErrorMessage);
                    throw fndMessage;
                }
            }
            // 如果是拍照类型的新品则生成 设计需求表
            if ("拍照".equals(AosType)) {
                generateDesign(this.getModel().getDataEntity(true));
            }
            FndHistory.Create(this.getView(), "确认", "确认");
            // 执行保存操作
            this.getModel().setValue(AOS_STATUS, "已完成");// 设置单据流程状态
            this.getModel().setValue(AOS_USER, SYSTEM);// 设置操作人为系统管理员
            this.getView().invokeOperation("save");
            this.getView().invokeOperation("refresh");

            // 确认完成后做新的界面状态控制
            StatusControl();
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 提交
     **/
    public void aos_submit(DynamicObject dy_main, String type) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope scope = span.makeCurrent()) {
            saveControl(dy_main);// 先做数据校验判断是否可以提交
            String AosStatus = dy_main.getString(AOS_STATUS);// 根据状态判断当前流程节点
            switch (AosStatus) {
                case "新建":
                    submitForNew(dy_main);
                    break;
                case "开发/采购确认":
                    submitForProductConfirm(dy_main);
                    break;
                case "白底拍摄":
                    submitForWhite(dy_main);
                    break;
                case "实景拍摄":
                    submitForAct(dy_main);
                    break;
                case "视频拍摄":
                    SubmitForVedio();
                    break;
                case "视频剪辑":
                    SubmitForCut();
                    break;
                case "编辑确认":
                    SubmitForEdit();
                    break;
                case "开发确认:视频":
                    SubmitForVc(dy_main, type);
                    break;
                case "视频更新":
                    aos_confirm();
                    break;
                case "视频更新:多人会审":
                    aos_sonsubmit();
                    break;
            }
            SaveServiceHelper.saveOperate("aos_mkt_photoreq", new DynamicObject[] {dy_main}, OperateOption.create());
            FndHistory.Create(dy_main, "提交", AosStatus);
            if (type.equals("A")) {
                this.getView().invokeOperation("refresh");
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
     * 视频更新 多人会审 提交
     **/
    private void aos_sonsubmit() {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope scope = span.makeCurrent()) {
            // 本单直接变为已完成
            this.getModel().setValue(AOS_USER, SYSTEM);
            this.getModel().setValue(AOS_STATUS, "已完成");// 设置单据流程状态

            // 获取当前单据国别
            Object aos_orgid = this.getModel().getValue("aos_orgid");
            if (FndGlobal.IsNotNull(aos_orgid)) {
                FndMsg.debug("org not null");
                String aosOrgNum = ((DynamicObject)aos_orgid).getString("number");
                FndMsg.debug("aosOrgNum:" + aosOrgNum);
                DynamicObjectCollection aos_entryentity4S = this.getModel().getEntryEntity("aos_entryentity4");
                for (DynamicObject aos_entryentity4 : aos_entryentity4S) {
                    FndMsg.debug("aos_orgshort:" + aos_entryentity4.getString("aos_orgshort"));
                    if (aos_entryentity4.getString("aos_orgshort").equals(aosOrgNum)) {
                        FndMsg.debug("INTO EQUAL");
                        aos_entryentity4.set("aos_salesub_date", new Date());
                    }
                }
            }
            this.getView().invokeOperation("save");
            this.getView().invokeOperation("refresh");

            // 判断是否为最后一个完成
            Object aos_parentid = this.getModel().getValue("aos_parentid");
            QFilter filter_id = new QFilter("aos_parentid", "=", aos_parentid);
            QFilter filter_status = new QFilter("aos_status", "=", "视频更新:多人会审");
            QFilter[] filters = new QFilter[] {filter_id, filter_status};
            DynamicObject aos_mkt_photoreq = QueryServiceHelper.queryOne("aos_mkt_photoreq", "id", filters);
            // 全部已完成 修改主流程状态
            if (aos_mkt_photoreq == null) {
                // 回写主流程
                aos_mkt_photoreq = BusinessDataServiceHelper.loadSingle(aos_parentid, "aos_mkt_photoreq");
                aos_mkt_photoreq.set("aos_status", "已完成");
                aos_mkt_photoreq.set("aos_user", SYSTEM);
                OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq",
                    new DynamicObject[] {aos_mkt_photoreq}, OperateOption.create());
                // 回写拍照任务清单
                if (QueryServiceHelper.exists("aos_mkt_photolist", aos_mkt_photoreq.get(AOS_SOURCEID))) {
                    DynamicObject aos_mkt_photolist =
                        BusinessDataServiceHelper.loadSingle(aos_mkt_photoreq.get(AOS_SOURCEID), "aos_mkt_photolist");
                    aos_mkt_photolist.set("aos_vedstatus", "已完成");
                    OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
                        new DynamicObject[] {aos_mkt_photolist}, OperateOption.create());
                }
            }

            // 仅更新提交人
            aos_mkt_photoreq = BusinessDataServiceHelper.loadSingle(aos_parentid, "aos_mkt_photoreq");
            if (FndGlobal.IsNotNull(aos_orgid)) {
                String aosOrgNum = ((DynamicObject)aos_orgid).getString("number");
                DynamicObjectCollection aos_entryentity4S =
                    aos_mkt_photoreq.getDynamicObjectCollection("aos_entryentity4");
                for (DynamicObject aos_entryentity4 : aos_entryentity4S) {
                    if (aos_entryentity4.getString("aos_orgshort").equals(aosOrgNum)) {
                        FndMsg.debug("INTO EQUAL2");
                        aos_entryentity4.set("aos_salesub_date", new Date());
                    }
                }
            }
            OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq", new DynamicObject[] {aos_mkt_photoreq},
                OperateOption.create());
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 视频拍摄 状态下提交
     **/
    private void SubmitForVedio() throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope scope = span.makeCurrent()) {
            // 异常参数
            int ErrorCount = 0;
            String ErrorMessage = "";
            // 数据层
            Object AosSourceid = this.getModel().getValue(AOS_SOURCEID);
            Object AosBillno = this.getModel().getValue(BILLNO);
            Object AosVedior = this.getModel().getValue(AOS_VEDIOR);
            Object ReqFId = this.getModel().getDataEntity().getPkValue(); // 当前界面主键
            Object aos_subtitle = this.getModel().getValue("aos_subtitle");
            Object aos_language = this.getModel().getValue("aos_language");
            Boolean StrightCut = false;
            String AosStatus = null;
            Object AosUser = null;
            String MessageId = null;

            // 根据国别大类中类取对应营销US编辑
            Object ItemId = ((DynamicObject)this.getModel().getValue("aos_itemid")).getPkValue();
            String category = (String)SalUtil.getCategoryByItemId(String.valueOf(ItemId)).get("name");
            String[] category_group = category.split(",");
            String AosCategory1 = null;
            String AosCategory2 = null;
            int category_length = category_group.length;
            if (category_length > 0) {
                AosCategory1 = category_group[0];
            }
            if (category_length > 1) {
                AosCategory2 = category_group[1];
            }
            long aos_editor = 0;
            if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("") && !AosCategory2.equals("")) {
                QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
                QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
                QFilter[] filters_category = new QFilter[] {filter_category1, filter_category2};
                String SelectStr = "aos_eng";
                DynamicObject aos_mkt_proguser =
                    QueryServiceHelper.queryOne("aos_mkt_proguser", SelectStr, filters_category);
                if (aos_mkt_proguser != null) {
                    aos_editor = aos_mkt_proguser.getLong("aos_eng");
                }
            }
            if (aos_editor == 0) {
                ErrorMessage = FndError.AddErrorMessage(ErrorMessage, AosCategory1 + "," + AosCategory2 + "品类英语编辑不存在!");
                FndError fndMessage = new FndError(ErrorMessage);
                throw fndMessage;
            }

            // 校验
            if ((Cux_Common_Utl.IsNull(aos_subtitle) && !Cux_Common_Utl.IsNull(aos_language))
                || (!Cux_Common_Utl.IsNull(aos_subtitle) && Cux_Common_Utl.IsNull(aos_language))) {
                ErrorCount++;
                ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "字幕需求必须同时填写 或 同时不填写!");
            }
            if (ErrorCount > 0) {
                FndError fndMessage = new FndError(ErrorMessage);
                throw fndMessage;
            }

            if (!Cux_Common_Utl.IsNull(aos_subtitle) && !Cux_Common_Utl.IsNull(aos_language)) {
                StrightCut = true;
            }

            if (StrightCut) {
                AosStatus = "字幕翻译";
                AosUser = aos_editor;
                MessageId = String.valueOf(aos_editor);
                GenerateListing();// 按照语言生成Listing优化需求子表&小语种
            } else {
                AosStatus = "视频剪辑";
                this.getModel().setValue(AOS_VEDIOR, UserServiceHelper.getCurrentUserId());
                AosUser = UserServiceHelper.getCurrentUserId();
                MessageId = String.valueOf(UserServiceHelper.getCurrentUserId());
            }

            // 回写拍照任务清单
            if (QueryServiceHelper.exists("aos_mkt_photolist", AosSourceid)) {
                DynamicObject aos_mkt_photolist =
                    BusinessDataServiceHelper.loadSingle(AosSourceid, "aos_mkt_photolist");
                aos_mkt_photolist.set("aos_vedstatus", AosStatus);
                OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
                    new DynamicObject[] {aos_mkt_photolist}, OperateOption.create());
                if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                    ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "拍照任务清单保存失败!");
                    FndError fndMessage = new FndError(ErrorMessage);
                    throw fndMessage;
                }
            }

            // 执行保存操作
            this.getModel().setValue(AOS_STATUS, AosStatus);// 设置单据流程状态
            this.getModel().setValue(AOS_USER, AosUser);// 流转给摄像师
            this.getModel().setValue("aos_vediodate", new Date());// 视频拍摄完成日期

            this.getView().invokeOperation("save");
            this.getView().invokeOperation("refresh");
            // 发送消息
            if (StrightCut) {
            } else {
                MKTCom.SendGlobalMessage(MessageId, AOS_MKT_PHOTOREQ, String.valueOf(ReqFId), String.valueOf(AosBillno),
                    "拍照需求表-视频剪辑");
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 生成Listing优化需求表子表 视频语言只能填英语
     **/
    private void GenerateListing() throws FndError {
        // 信息参数
        String MessageId = null;
        String Message = "";
        // 异常参数
        int ErrorCount = 0;
        String ErrorMessage = "";
        // 数据层
        long CurrentUserId = UserServiceHelper.getCurrentUserId();
        DynamicObject AosDesigner = (DynamicObject)this.getModel().getValue("aos_designer");
        Object AosDesignerId = AosDesigner.getPkValue();
        Object billno = this.getModel().getValue("billno");
        Object ReqFId = this.getModel().getDataEntity().getPkValue(); // 当前界面主键
        Object aos_reqtype = this.getModel().getValue("aos_reqtype");// 任务类型
        Object ItemId = ((DynamicObject)this.getModel().getValue("aos_itemid")).getPkValue();
        Object aos_requireby = CurrentUserId;// 申请人为拍照需求表当前操作人
        Object aos_subtitle = this.getModel().getValue("aos_subtitle");
        Object aos_demandate = new Date();
        Object aos_osconfirmlov = null;
        if ("四者一致".equals(AOS_TYPE)) {
            aos_demandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 1);
            aos_osconfirmlov = "否";
        } else if ("优化".equals(AOS_TYPE)) {
            aos_demandate = Cux_Common_Utl.add_NoWeekendDays(new Date(), 3);
        }

        // 拍照任务类型转化
        if ("新品".equals(aos_reqtype)) {
            aos_reqtype = "新品设计";
        } else if ("老品".equals(aos_reqtype)) {
            aos_reqtype = "老品优化";
        }
        List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(aos_requireby);
        String aos_orgtext = "";
        DynamicObject bd_material = BusinessDataServiceHelper.loadSingle(ItemId, "bd_material");
        String aos_productno = bd_material.getString("aos_productno");
        String aos_itemname = bd_material.getString("name");
        String item_number = bd_material.getString("number");
        DynamicObjectCollection aos_contryentryS = bd_material.getDynamicObjectCollection("aos_contryentry");
        // 获取所有国家品牌 字符串拼接 终止
        for (DynamicObject aos_contryentry : aos_contryentryS) {
            DynamicObject aos_nationality = aos_contryentry.getDynamicObject("aos_nationality");
            String aos_nationalitynumber = aos_nationality.getString("number");
            if ("IE".equals(aos_nationalitynumber)) {
                continue;
            }
            Object org_id = aos_nationality.get("id"); // ItemId
            int OsQty = iteminfo.GetItemOsQty(org_id, ItemId);
            int SafeQty = iteminfo.GetSafeQty(org_id);
            if ("C".equals(aos_contryentry.getString("aos_contryentrystatus")) && OsQty < SafeQty) {
                continue;
            }
            aos_orgtext = aos_orgtext + aos_nationalitynumber + ";";
        }
        // 获取同产品号物料
        QFilter filter_productno = new QFilter("aos_productno", QCP.equals, aos_productno);
        QFilter[] filters = new QFilter[] {filter_productno};
        String SelectColumn = "number,aos_type";
        String aos_broitem = "";
        DynamicObjectCollection bd_materialS = QueryServiceHelper.query("bd_material", SelectColumn, filters);
        for (DynamicObject bd : bd_materialS) {
            if ("B".equals(bd.getString("aos_type"))) {
                continue; // 配件不获取
            }
            String number = bd.getString("number");
            if (item_number.equals(number)) {
                continue;
            } else {
                aos_broitem = aos_broitem + number + ";";
            }
        }
        // 根据国别大类中类取对应营销US编辑
        String category = MKTCom.getItemCateNameZH(String.valueOf(ItemId));
        String[] category_group = category.split(",");
        String AosCategory1 = null;
        String AosCategory2 = null;
        int category_length = category_group.length;
        if (category_length > 0) {
            AosCategory1 = category_group[0];
        }
        if (category_length > 1) {
            AosCategory2 = category_group[1];
        }
        long aos_editor = 0;
        if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("") && !AosCategory2.equals("")) {
            DynamicObject dy_editor =
                ProgressUtil.findEditorByType(AosCategory1, AosCategory2, String.valueOf(aos_reqtype));
            if (dy_editor != null) {
                aos_editor = dy_editor.getLong("aos_user");
            }
        }
        if (aos_editor == 0) {
            ErrorCount++;
            ErrorMessage = FndError.AddErrorMessage(ErrorMessage, AosCategory1 + "," + AosCategory2 + "品类英语编辑不存在!");
        }

        // 校验
        if (ErrorCount > 0) {
            FndError fndMessage = new FndError(ErrorMessage);
            throw fndMessage;
        }

        // 创建Listing优化需求表子表
        DynamicObject aos_mkt_listing_son = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_son");
        aos_mkt_listing_son.set("aos_type", aos_reqtype);
        aos_mkt_listing_son.set("aos_designer", AosDesignerId);
        aos_mkt_listing_son.set("aos_editor", aos_editor);
        aos_mkt_listing_son.set("aos_user", aos_editor);
        aos_mkt_listing_son.set("aos_orignbill", billno);
        aos_mkt_listing_son.set("aos_sourceid", ReqFId);
        aos_mkt_listing_son.set("aos_status", "编辑确认");
        aos_mkt_listing_son.set("aos_sourcetype", "VED");
        aos_mkt_listing_son.set("aos_requireby", aos_requireby); // 申请人为当前操作人
        aos_mkt_listing_son.set("aos_requiredate", new Date());
        aos_mkt_listing_son.set("aos_ismall", true);
        aos_mkt_listing_son.set("aos_ismalllov", "是");
        aos_mkt_listing_son.set("aos_demandate", aos_demandate);
        aos_mkt_listing_son.set("aos_osconfirmlov", aos_osconfirmlov);
        // BOTP
        aos_mkt_listing_son.set("aos_sourcebilltype", AOS_MKT_PHOTOREQ);
        aos_mkt_listing_son.set("aos_sourcebillno", billno);
        aos_mkt_listing_son.set("aos_srcentrykey", "aos_entryentity");

        if (MapList != null) {
            if (MapList.get(2) != null) {
                aos_mkt_listing_son.set("aos_organization1", MapList.get(2).get("id"));
            }
            if (MapList.get(3) != null) {
                aos_mkt_listing_son.set("aos_organization2", MapList.get(3).get("id"));
            }
        }
        DynamicObjectCollection aos_entryentityS = aos_mkt_listing_son.getDynamicObjectCollection("aos_entryentity");
        DynamicObject aos_entryentity = aos_entryentityS.addNew();
        aos_entryentity.set("aos_itemid", ItemId);
        aos_entryentity.set("aos_is_saleout", ProgressUtil.Is_saleout(ItemId));
        aos_entryentity.set("aos_require", aos_subtitle);
        aos_entryentity.set("aos_srcrowseq", 1);
        // 物料相关信息
        DynamicObjectCollection aos_subentryentityS = aos_entryentity.getDynamicObjectCollection("aos_subentryentity");
        DynamicObject aos_subentryentity = aos_subentryentityS.addNew();
        aos_subentryentity.set("aos_segment3", aos_productno);
        aos_subentryentity.set("aos_broitem", aos_broitem);
        aos_subentryentity.set("aos_itemname", aos_itemname);
        aos_subentryentity.set("aos_orgtext", aos_orgtext);
        aos_subentryentity.set("aos_reqinput", aos_subtitle);

        aos_entryentity.set("aos_segment3_r", aos_productno);
        aos_entryentity.set("aos_broitem_r", aos_broitem);
        aos_entryentity.set("aos_itemname_r", aos_itemname);
        aos_entryentity.set("aos_orgtext_r", ProgressUtil.getOrderOrg(ItemId));

        // 消息推送
        MessageId = String.valueOf(aos_editor);
        Message = "Listing优化需求表子表-拍照需求表:视频-自动创建";
        aos_mkt_listingson_bill.setListSonUserOrganizate(aos_mkt_listing_son);
        OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_son",
            new DynamicObject[] {aos_mkt_listing_son}, OperateOption.create());

        // 修复关联关系
        try {
            ProgressUtil.botp("aos_mkt_listing_son", aos_mkt_listing_son.get("id"));
        } catch (Exception ex) {
            FndError.showex(getView(), ex, FndWebHook.urlMms);
        }

        if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
            MKTCom.SendGlobalMessage(MessageId, String.valueOf(aos_mkt_listing_son),
                String.valueOf(operationrst.getSuccessPkIds().get(0)), aos_mkt_listing_son.getString("billno"),
                Message);
        }
    }

    /**
     * 视频剪辑 状态下提交 直接到 开发确认：视频
     **/
    private void SubmitForCut() throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope scope = span.makeCurrent()) {
            this.getView().invokeOperation("save");
            // 异常参数
            String ErrorMessage = "";
            // 数据层
            DynamicObject AosDeveloper = (DynamicObject)this.getModel().getValue(AOS_DEVELOPER);
            if (AosDeveloper == null) {
                throw new FndError("开发不能为空");
            }

            Object aos_vediocate = this.getModel().getValue("aos_vediocate");
            if (FndGlobal.IsNull(aos_vediocate)) {
                throw new FndError("视频类型不能为空!");
            }

            String nextStatus = "开发确认:视频";
            String nextUser = "";
            if (AosDeveloper != null) {
                nextUser = AosDeveloper.getString("id");
            }
            // 3d = 是，3d原因必填
            if (Boolean.parseBoolean(getModel().getValue("aos_3dflag").toString())) {
                Object reason = getModel().getValue("aos_3d_reason");
                if (FndGlobal.IsNull(reason)) {
                    throw new FndError("是否3D为是时，3D原因不能为空!");
                }
                nextStatus = "3D建模";
                DynamicObject dy_3d = (DynamicObject)getModel().getValue("aos_3d");
                nextUser = "";
                if (dy_3d != null) {
                    nextUser = dy_3d.getString("id");
                }
                // 生成对应的 3D产品设计单
                aos_mkt_3design_bill.Generate3Design(getModel().getDataEntity(true));
            }

            Object AosSourceid = this.getModel().getValue(AOS_SOURCEID);
            Object AosBillno = this.getModel().getValue(BILLNO);
            Object ReqFId = this.getModel().getDataEntity().getPkValue(); // 当前界面主键
            // 回写拍照任务清单
            if (QueryServiceHelper.exists("aos_mkt_photolist", AosSourceid)) {
                DynamicObject aos_mkt_photolist =
                    BusinessDataServiceHelper.loadSingle(AosSourceid, "aos_mkt_photolist");
                aos_mkt_photolist.set("aos_vedstatus", nextStatus);
                OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
                    new DynamicObject[] {aos_mkt_photolist}, OperateOption.create());
                if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                    ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "拍照任务清单保存失败!");
                    FndError fndMessage = new FndError(ErrorMessage);
                    throw fndMessage;
                }
            }
            // 执行保存操作
            this.getModel().setValue(AOS_STATUS, nextStatus);// 设置单据流程状态
            this.getModel().setValue("aos_cutdate", new Date());// 视频剪辑完成日期

            String MessageId = null;
            // 流转给开发
            this.getModel().setValue(AOS_USER, nextUser);
            MessageId = String.valueOf(nextUser);
            // 发送消息
            MKTCom.SendGlobalMessage(MessageId, AOS_MKT_PHOTOREQ, String.valueOf(ReqFId), String.valueOf(AosBillno),
                "拍照需求表-" + nextStatus);
            GlobalMessage.SendMessage(AosBillno + "-拍照需求单据待处理", MessageId);
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 编辑确认 状态下提交
     **/
    private void SubmitForEdit() throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope scope = span.makeCurrent()) {
            // 异常参数
            String ErrorMessage = "";
            // 数据层
            Object AosSourceid = this.getModel().getValue(AOS_SOURCEID);
            Object AosBillno = this.getModel().getValue(BILLNO);
            Object AosDeveloper = this.getModel().getValue(AOS_DEVELOPER);
            Object ReqFId = this.getModel().getDataEntity().getPkValue(); // 当前界面主键
            // 校验
            // 回写拍照任务清单
            if (QueryServiceHelper.exists("aos_mkt_photolist", AosSourceid)) {
                DynamicObject aos_mkt_photolist =
                    BusinessDataServiceHelper.loadSingle(AosSourceid, "aos_mkt_photolist");
                aos_mkt_photolist.set("aos_vedstatus", "开发确认:视频");
                OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
                    new DynamicObject[] {aos_mkt_photolist}, OperateOption.create());
                if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                    ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "拍照任务清单保存失败!");
                    FndError fndMessage = new FndError(ErrorMessage);
                    throw fndMessage;
                }
            }

            // 执行保存操作
            this.getModel().setValue(AOS_STATUS, "开发确认:视频");// 设置单据流程状态
            String MessageId = null;
            this.getModel().setValue(AOS_USER, AosDeveloper);// 流转给开发
            MessageId = ((DynamicObject)AosDeveloper).getPkValue().toString();
            this.getView().invokeOperation("save");
            this.getView().invokeOperation("refresh");
            // 发送消息
            MKTCom.SendGlobalMessage(MessageId, AOS_MKT_PHOTOREQ, String.valueOf(ReqFId), String.valueOf(AosBillno),
                "拍照需求表-开发确认");
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 开发确认:视频 状态下提交
     **/
    private void SubmitForVc(DynamicObject dy_main, String type) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope scope = span.makeCurrent()) {
            // 异常参数
            String ErrorMessage = "";
            // 数据层
            Object AosSourceid = dy_main.get(AOS_SOURCEID);
            // 校验
            // 回写拍照任务清单
            if (QueryServiceHelper.exists("aos_mkt_photolist", AosSourceid)) {
                DynamicObject aos_mkt_photolist =
                    BusinessDataServiceHelper.loadSingle(AosSourceid, "aos_mkt_photolist");
                aos_mkt_photolist.set("aos_vedstatus", "视频更新");
                OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
                    new DynamicObject[] {aos_mkt_photolist}, OperateOption.create());
                if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                    ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "拍照任务清单保存失败!");
                    FndError fndMessage = new FndError(ErrorMessage);
                    throw fndMessage;
                }
            }

            /** 销售助理取值逻辑：按照拍照需求表 视频地址 中出现的 国别，和流程中SKU的大类+中类，在营销审批流-国别品类人员表中找到销售助理 **/
            Object AosItemId = dy_main.get(AOS_ITEMID);
            DynamicObject AosItemidObject = (DynamicObject)AosItemId;
            Object fid = AosItemidObject.getPkValue();
            // 产品类别
            String category = (String)SalUtil.getCategoryByItemId(String.valueOf(fid)).get("name");
            String[] category_group = category.split(",");
            String AosCategory1 = null;
            String AosCategory2 = null;
            int category_length = category_group.length;
            if (category_length > 0) {
                AosCategory1 = category_group[0];
            }
            if (category_length > 1) {
                AosCategory2 = category_group[1];
            }

            HashMap<String, String> userMap = new HashMap<>();

            // 视频地址单据体
            DynamicObjectCollection dy_spentry = dy_main.getDynamicObjectCollection("aos_entryentity4");
            for (DynamicObject dy : dy_spentry) {
                DynamicObject bd_country = QueryServiceHelper.queryOne("bd_country", "id,name,number",
                    new QFilter[] {new QFilter("number", "=", dy.getString("aos_orgshort"))});
                // 根据大类中类获取对应销售助理
                if (AosCategory1 != null & AosCategory2 != null && !AosCategory1.equals("")
                    && !AosCategory2.equals("")) {
                    QFilter filter_category1 = new QFilter("aos_category1", "=", AosCategory1);
                    QFilter filter_category2 = new QFilter("aos_category2", "=", AosCategory2);
                    QFilter filter_org = new QFilter("aos_orgid", "=", bd_country.get("id"));
                    QFilter[] filters_category = new QFilter[] {filter_category1, filter_category2, filter_org};
                    String SelectStr = "aos_salehelper.id aos_salehelper";
                    DynamicObjectCollection aos_mkt_progorguser =
                        QueryServiceHelper.query("aos_mkt_progorguser", SelectStr, filters_category);
                    if (aos_mkt_progorguser != null) {
                        for (DynamicObject user : aos_mkt_progorguser) {
                            userMap.put(user.getString("aos_salehelper"), bd_country.getString("id"));
                        }
                    } else {
                        throw new FndError(bd_country.getString("number") + " " + AosCategory1 + " " + AosCategory2
                            + " 未查询出销售助理，请确认销售助理是否存在");
                    }
                }
            }

            if (FndGlobal.IsNotNull(userMap) && userMap.size() >= 1) {
                for (String userMapKey : userMap.keySet()) {
                    splitSon(userMapKey, userMap, dy_main);
                }
            } else {
                throw new FndError("未查询出销售助理，请确认销售助理是否存在");
            }

            // 源单状态调整
            if (type.equals("A")) {
                if (FndGlobal.IsNotNull(userMap) && userMap.size() >= 1) {
                    this.getView().setVisible(false, "aos_submit");
                }
            }
            dy_main.set(AOS_STATUS, "视频更新");// 设置单据流程状态
            dy_main.set(AOS_USER, SYSTEM);// 设置单据节点为申请人
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 白底退回
     **/
    private void BackForWhite() throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope scope = span.makeCurrent()) {
            // 异常参数
            String ErrorMessage = "";
            // 数据层
            Object AosSourceid = this.getModel().getValue(AOS_SOURCEID);
            Object AosBillno = this.getModel().getValue(BILLNO);
            Object AosFollower = this.getModel().getValue(AOS_FOLLOWER);
            Object ReqFId = this.getModel().getDataEntity().getPkValue(); // 当前界面主键
            // 校验
            Object aosPhReason = this.getModel().getValue("aos_phreason");
            if (FndGlobal.IsNull(aosPhReason)) {
                throw new FndError("摄影师退回原因必填！");
            }
            // 回写拍照任务清单
            if (QueryServiceHelper.exists("aos_mkt_photolist", AosSourceid)) {
                DynamicObject aos_mkt_photolist =
                    BusinessDataServiceHelper.loadSingle(AosSourceid, "aos_mkt_photolist");
                aos_mkt_photolist.set("aos_vedstatus", "跟单提样");
                OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
                    new DynamicObject[] {aos_mkt_photolist}, OperateOption.create());
                if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                    ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "拍照任务清单保存失败!");
                    FndError fndMessage = new FndError(ErrorMessage);
                    throw fndMessage;
                }
            }

            // 退回时生成新的样品入库单
            generateRcv(this.getModel().getDataEntity(true));

            // 执行保存操作
            Object aos_return = 0;
            aos_return = this.getModel().getValue("aos_return", 0);
            if (aos_return == null) {
                aos_return = 1;
            } else {
                aos_return = (int)aos_return + 1;
            }
            this.getModel().setValue("aos_return", aos_return, 0);// 退回次数

            // 摄影师退回
            Object aos_phreturn = 0;
            aos_phreturn = this.getModel().getValue("aos_phreturn");
            if (aos_phreturn == null) {
                aos_phreturn = 1;
            } else {
                aos_phreturn = (int)aos_phreturn + 1;
            }
            this.getModel().setValue("aos_phreturn", aos_phreturn);

            this.getModel().setValue(AOS_STATUS, "跟单提样");// 设置单据流程状态
            String MessageId = null;
            this.getModel().setValue(AOS_USER, AosFollower);// 流转给跟单
            // 退回时去除备样安装完成日期
            this.getModel().setValue("aos_sampledate", null);
            this.getModel().setValue("aos_installdate", null);
            MessageId = ((DynamicObject)AosFollower).getPkValue().toString();
            this.getView().invokeOperation("save");
            this.getView().invokeOperation("refresh");
            // 发送消息
            MKTCom.SendGlobalMessage(MessageId, AOS_MKT_PHOTOREQ, String.valueOf(ReqFId), String.valueOf(AosBillno),
                "拍照需求表-白底退回");

        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 开发退回
     **/
    private void BackForConfirm() throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope scope = span.makeCurrent()) {
            // 异常参数
            String ErrorMessage = "";
            // 数据层
            Object AosSourceid = this.getModel().getValue(AOS_SOURCEID);
            Object AosBillno = this.getModel().getValue(BILLNO);
            Object AosActph = this.getModel().getValue(AOS_ACTPH);
            Object aos_3d = this.getModel().getValue("aos_3d");
            Object AosVedior = this.getModel().getValue(AOS_VEDIOR);
            Object AosType = this.getModel().getValue(AOS_TYPE).toString();
            Object ReqFId = this.getModel().getDataEntity().getPkValue(); // 当前界面主键
            Object aos_3dflag = this.getModel().getValue("aos_3dflag");
            Boolean Is3DFlag = false;
            Object aosDeReason = this.getModel().getValue("aos_dereason");

            if (FndGlobal.IsNull(aosDeReason)) {
                throw new FndError("开发退回原因必填！");
            }

            // 校验
            if ((boolean)aos_3dflag) {
                Is3DFlag = true;
            }
            if (Is3DFlag) {
                QFilter filter_id = new QFilter("aos_orignbill", QCP.equals, AosBillno);
                QFilter[] filters_3d = new QFilter[] {filter_id};
                DynamicObject aos_mkt_3design = QueryServiceHelper.queryOne("aos_mkt_3design", "id", filters_3d);
                if (FndGlobal.IsNotNull(aos_mkt_3design)) {
                    Object id = aos_mkt_3design.get("id");
                    aos_mkt_3design = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_3design");
                    aos_mkt_3design.set("aos_returnflag", true);
                    aos_mkt_3design.set("aos_user", aos_3d);
                    aos_mkt_3design.set("aos_status", "新建");
                    aos_mkt_3design.set("aos_returnreason", this.getModel().getValue("aos_dereason"));
                    OperationServiceHelper.executeOperate("save", "aos_mkt_3design",
                        new DynamicObject[] {aos_mkt_3design}, OperateOption.create());
                }
            }

            // 回写拍照任务清单
            if (QueryServiceHelper.exists("aos_mkt_photolist", AosSourceid)) {
                DynamicObject aos_mkt_photolist =
                    BusinessDataServiceHelper.loadSingle(AosSourceid, "aos_mkt_photolist");
                if ("拍照".equals(AosType) && !Is3DFlag) {
                    aos_mkt_photolist.set("aos_phstatus", "实景拍摄");
                } else if ("拍照".equals(AosType) && Is3DFlag) {
                    aos_mkt_photolist.set("aos_phstatus", "3D建模");
                } else if ("视频".equals(AosType)) {
                    aos_mkt_photolist.set("aos_vedstatus", "视频剪辑");
                }

                OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
                    new DynamicObject[] {aos_mkt_photolist}, OperateOption.create());
                if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                    ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "拍照任务清单保存失败!");
                    FndError fndMessage = new FndError(ErrorMessage);
                    throw fndMessage;
                }
            }

            // 执行保存操作
            Object aos_return = 0;
            Object aos_dereturn = 0;

            String MessageId = null;
            aos_return = this.getModel().getValue("aos_return", 1);
            if (aos_return == null) {
                aos_return = 1;
            } else {
                aos_return = (int)aos_return + 1;
            }
            this.getModel().setValue("aos_return", aos_return, 1);// 退回次数
            // 开发退回
            aos_dereturn = this.getModel().getValue("aos_dereturn", 1);
            if (aos_dereturn == null) {
                aos_dereturn = 1;
            } else {
                aos_dereturn = (int)aos_dereturn + 1;
            }
            this.getModel().setValue("aos_dereturn", aos_dereturn, 1);// 退回次数

            if ("拍照".equals(AosType) && !Is3DFlag) {
                this.getModel().setValue("aos_actdate", "");
                this.getModel().setValue(AOS_STATUS, "实景拍摄");// 设置单据流程状态
                this.getModel().setValue(AOS_USER, AosActph);// 流转给实景摄影师
                MessageId = ((DynamicObject)AosActph).getPkValue().toString();
            } else if ("拍照".equals(AosType) && Is3DFlag) {
                this.getModel().setValue(AOS_STATUS, "3D建模");// 设置单据流程状态
                this.getModel().setValue(AOS_USER, aos_3d);// 流转给3D设计师
                MessageId = ((DynamicObject)aos_3d).getPkValue().toString();
            } else if ("视频".equals(AosType)) {
                this.getModel().setValue(AOS_STATUS, "视频剪辑");// 设置单据流程状态
                this.getModel().setValue(AOS_USER, AosVedior);// 流转给摄像师
                MessageId = ((DynamicObject)AosVedior).getPkValue().toString();
            }

            // 退回时 删除对应抠图任务清单 数据
            QFilter filter_id = new QFilter("aos_sourceid", "=", ReqFId);
            QFilter[] filters_ps = new QFilter[] {filter_id};
            DeleteServiceHelper.delete("aos_mkt_pslist", filters_ps);

            this.getView().invokeOperation("save");
            this.getView().invokeOperation("refresh");
            // 发送消息
            MKTCom.SendGlobalMessage(MessageId, AOS_MKT_PHOTOREQ, String.valueOf(ReqFId), String.valueOf(AosBillno),
                "拍照需求表-开发退回");
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 查看入库单
     **/
    private void aos_showrcv() throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope scope = span.makeCurrent()) {
            // 数据层
            int ErrorCount = 0;
            String ErrorMessage = "";
            Object aos_rcvbill = this.getModel().getValue("aos_rcvbill");

            if (Cux_Common_Utl.IsNull(aos_rcvbill)) {
                ErrorCount++;
                ErrorMessage = FndError.AddErrorMessage(ErrorMessage, "入库单号不存在!");
            }

            if (ErrorCount > 0) {
                FndError fndMessage = new FndError(ErrorMessage);
                throw fndMessage;
            }

            QFilter filter_bill = new QFilter("billno", "=", aos_rcvbill);
            QFilter[] filters = new QFilter[] {filter_bill};
            DynamicObject aos_mkt_photoreq = QueryServiceHelper.queryOne("aos_mkt_rcv", "id", filters);
            if (Cux_Common_Utl.IsNull(aos_mkt_photoreq)) {
                FndError fndMessage = new FndError("未查询到入库单!");
                throw fndMessage;
            }
            Cux_Common_Utl.OpenSingleBill(this.getView(), "aos_mkt_rcv", aos_mkt_photoreq.get("id"));
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 判断是否拍摄视频
     *
     * @param cate1 大类
     * @param cate2 中类
     * @param cate3 小类
     * @param itemName 品名
     */
    public boolean JudgeTakeVideo(String cate1, String cate2, String cate3, String itemName) {
        QFilter filter_majorCate = new QFilter("aos_cate1", "=", cate1);
        QFilter filter_middleCate = new QFilter("aos_cate2", "=", cate2);
        QFilter filter_subCate = new QFilter("aos_cate3", "=", cate3);
        QFilter filter_itemName = new QFilter("aos_item", "=", itemName);
        QFilter[] qfs = new QFilter[] {filter_majorCate, filter_middleCate, filter_subCate, filter_itemName};
        return QueryServiceHelper.exists("aos_mkt_photo_cate", qfs);
    }

    /**
     * 在3D选品表存在，拍照地点默认=工厂简拍
     **/
    public boolean Judge3dSelect(String cate1, String cate2, String cate3, String itemName) {
        QFBuilder qfBuilder = new QFBuilder();
        qfBuilder.add("aos_category1", "=", cate1);
        qfBuilder.add("aos_category2", "=", cate2);
        qfBuilder.add("aos_category3", "=", cate3);
        qfBuilder.add("aos_name", "=", itemName);
        return QueryServiceHelper.exists("aos_mkt_3dselect", qfBuilder.toArray());
    }

    public boolean Judge3dPlan(Object itemID, String cate1, String cate2, String cate3, String aos_type) {
        QFBuilder qfBuilder = new QFBuilder();
        qfBuilder.add("aos_item", "=", itemID);
        qfBuilder.add("aos_cate1", "=", cate1);
        qfBuilder.add("aos_cate2", "=", cate2);
        qfBuilder.add("aos_cate3", "=", cate3);
        if (aos_type.equals("A")) {
            qfBuilder.add("aos_model", "=", "是");
        } else {
            qfBuilder.add("aos_model", "=", "否");
        }
        return QueryServiceHelper.exists("aos_mkt_3dplan", qfBuilder.toArray());
    }

    @Override
    public void hyperLinkClick(HyperLinkClickEvent hyperLinkClickEvent) {
        String FieldName = hyperLinkClickEvent.getFieldName();
        if ("aos_refer".equals(FieldName)) {
            openPhotoUrl();
        }
    }

    /**
     * 标识枚举类
     */
    private enum sign {
        /**
         * 拍照标记
         */
        photoFlag("aos_photoflag"),
        /**
         * 拍照清单
         */
        photoList("aos_mkt_photolist"),
        /**
         * 视频拍摄
         */
        vedPhoto("视频拍摄"),
        /**
         * 外包拍照
         */
        outSource("外包拍照"),
        /**
         * 提交按钮
         */
        submit("aos_submit"),
        /**
         * 确认按钮
         */
        confirm("aos_confirm"),
        /**
         * 退回按钮
         */
        back("aos_back"),
        /**
         * 查看历史记录按钮
         */
        history("aos_history"),
        /**
         * 白底拍摄
         */
        white("白底拍摄"),
        /**
         * 实景拍摄
         */
        actual("实景拍摄"),
        /**
         * 查看入库单
         */
        showRcv("aos_showrcv"),
        /**
         * 是
         */
        yes("是"),
        /**
         * 否
         */
        no("否"),
        /**
         * 保存
         */
        save("save"),
        /**
         * 采购合同号
         */
        poNumber("aos_ponumber"),
        /**
         * 开发/采购确认
         */
        poComfirm("开发/采购确认"),
        /**
         * 查看子流程
         */
        search("aos_search"),
        /**
         * 物料Id
         */
        itemId("aos_itemid"),
        /**
         * 拍照地点
         */
        phstate("aos_phstate"),
        /**
         * 拍照类型
         */
        vedioType("aos_vediotype"),
        /**
         * 无法3D建模清单
         */
        not3d("aos_mkt_not3ditem"),
        /**
         * 3d选品表
         */
        select3d("aos_mkt_3dselect"),
        /**
         * 来样拍照
         */
        goShot("来样拍照"),
        /**
         * 工厂简拍
         */
        snapShot("工厂简拍"),
        /**
         * 剪辑
         */
        shortCut("剪辑"),
        /**
         * 跳转到摄影标准库
         */
        photoUrl("aos_photourl"),
        /**
         * 跳转到布景标准库
         */
        senceUrl("aos_senceurl"),
        /**
         * 新建
         */
        newStatus("新建"),
        /**
         * 是否新品
         */
        newItem("aos_newitem"),
        /**
         * 视频
         */
        video("视频");

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