package mkt.progress.photo;

import java.util.*;

import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndHistory;
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
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.exception.KDException;
import kd.bos.form.IFormView;
import kd.bos.form.container.Container;
import kd.bos.form.control.Control;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.events.BeforeClosedEvent;
import kd.bos.form.field.ComboEdit;
import kd.bos.form.field.ComboItem;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import common.sal.util.QFBuilder;
import mkt.common.MKTCom;
import mkt.common.otel.MmsOtelUtils;
import mkt.progress.ProgressUtil;
import mkt.progress.design3d.aos_mkt_3design_bill;
import mkt.progress.iface.iteminfo;

/**
 * @author aosom
 * @version 样品入库通知单-表单插件
 */
public class AosMktRcvBill extends AbstractBillPlugIn implements ItemClickListener {
    public final static String SYSTEM = Cux_Common_Utl.SYSTEM;
    public final static String AOS_MKT_RCV = "aos_mkt_rcv";
    public final static int TWO = 2;
    public final static String AOS_SEALSAMPLE = "aos_sealsample";
    private static final Tracer TRACER = MmsOtelUtils.getTracer(AosMktRcvBill.class, RequestContext.get());

    /** 退回 **/
    private static void aosReturn(DynamicObject dyMain) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            // 退回原因
            Object aosReturnreason = dyMain.get("aos_returnreason");
            if (Cux_Common_Utl.IsNull(aosReturnreason)) {
                throw new FndError("退回原因未填");
            }
            Object aosOrignbill = dyMain.get("aos_orignbill");
            if (Cux_Common_Utl.IsNull(aosOrignbill)) {
                QFilter filter = new QFilter("billno", "=", aosOrignbill);
                DynamicObject dy =
                    QueryServiceHelper.queryOne("aos_mkt_photoreq", "aos_follower", new QFilter[] {filter});
                if (dy != null) {
                    dyMain.set("aos_status", "退回");
                    dyMain.set("aos_user", dy.get("aos_follower"));
                    return;
                }
            }
            Object aosVendor = dyMain.get("aos_vendor");
            DynamicObject dyItem = dyMain.getDynamicObject("aos_itemid");
            if (Cux_Common_Utl.IsNull(aosVendor)) {
                throw new FndError("供应商为空，无法获取跟单");
            }
            if (dyItem == null) {
                throw new FndError("物料为空，获取数据异常");
            }
            // 供应商id
            long verder = iteminfo.QueryVendorIdByName(dyItem.getPkValue(), aosVendor.toString());
            // 获取跟单
            QFilter filterId = new QFilter("id", "=", verder);
            DynamicObject dy = QueryServiceHelper.queryOne("bd_supplier", "aos_documentary", new QFilter[] {filterId});
            if (dy == null) {
                throw new FndError("获取跟单失败");
            }
            dyMain.set("aos_status", "退回");
            dyMain.set("aos_user", dy.get("aos_documentary"));
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 查看封样图片
     *
     * @param iFormView view
     * @param fid pk
     */
    public static void querySample(IFormView iFormView, Object fid) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            // 根据传入的样品入库通知单主键获取 样品入库通知单对象
            boolean exists = QueryServiceHelper.exists(AOS_MKT_RCV, fid);
            if (!exists) {
                throw new FndError("封样单不存在");
            }
            DynamicObject aosMktRcv = BusinessDataServiceHelper.loadSingle(fid, AOS_MKT_RCV);
            // 合同号
            String aosPoNumber = aosMktRcv.getString("aos_ponumber");
            // 货号
            DynamicObject aosItemId = aosMktRcv.getDynamicObject("aos_itemid");
            openSample(iFormView, aosItemId.getPkValue(), aosPoNumber);
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    public static void openSample(IFormView iFormView, Object itemid, Object poNumber) {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            // 封样单参数
            Object sampleId;
            // 查询是否存在对应封样单
            DynamicObject aosSealSample = QueryServiceHelper.queryOne(AOS_SEALSAMPLE, "id",
                new QFilter("aos_item", QCP.equals, itemid).and("aos_contractnowb", QCP.equals, poNumber).toArray());
            if (FndGlobal.IsNull(aosSealSample)) {
                List<Object> primaryKeys = QueryServiceHelper.queryPrimaryKeys(AOS_SEALSAMPLE,
                    new QFilter("aos_item", QCP.equals, itemid).toArray(), "createtime desc", 1);
                if (FndGlobal.IsNotNull(primaryKeys) && primaryKeys.size() > 0) {
                    sampleId = primaryKeys.get(0);
                } else {
                    throw new FndError("未找到对应封样单!");
                }
            } else {
                sampleId = aosSealSample.get("id");
            }
            // 打开封样单
            FndGlobal.OpenBillById(iFormView, AOS_SEALSAMPLE, sampleId);
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /** 通用控制校验 **/
    private static void saveControl(DynamicObject dyMain) throws FndError {
        FndError fndError = new FndError();
        // 数据层
        long currentUserId = UserServiceHelper.getCurrentUserId();
        DynamicObject aosUser = dyMain.getDynamicObject("aos_user");
        long aosUserLong = (long)aosUser.getPkValue();
        String aosStatus = dyMain.getString("aos_status");
        Object aosPhotoflag = dyMain.get("aos_photoflag");
        // 样品处理方式
        Object aosProtype = dyMain.get("aos_protype");
        Object aosContact = dyMain.get("aos_contact");
        Object aosContactway = dyMain.get("aos_contactway");
        Object aosReturnadd = dyMain.get("aos_returnadd");
        Object aosPhoto = dyMain.get("aos_photo");
        Object aosReason = dyMain.get("aos_reason");
        Object aosAddress = dyMain.get("aos_address");
        // 拍照地点
        Object aosPhstate = dyMain.get("aos_phstate");
        Object aosSameitemid = dyMain.get("aos_sameitemid");
        Boolean aosVedio = dyMain.getBoolean("aos_vedio");
        Object aosAttach = dyMain.get("aos_attach");
        // ===== 2022/10/12 王丽娜新增校验 =====
        if (sign.newStatus.name.equals(aosStatus) && Cux_Common_Utl.IsNull(aosPhstate)) {
            fndError.add("新建状态下拍照地点必填!");
        }
        // 新建节点，当拍照地点=外包拍照时，样品接收地址必填；确保不会只有外包拍摄地址而无外包简称
        if (sign.newStatus.name.equals(aosStatus) && sign.outSource.name.equals(aosPhstate)
            && Cux_Common_Utl.IsNull(aosAddress)) {
            fndError.add("新建状态下拍照地点=外包拍照时，样品接收地址必填!");
        }
        // ==== 2022/11/24 拍照地方=工厂简拍/工厂自拍时，样品接收地址/样品处理方式不需必填
        if (!(Boolean)aosPhotoflag && Cux_Common_Utl.IsNull(aosReason)) {
            fndError.add("是否拍照为否，不拍照原因必填!");
        }
        if (sign.sameItem.name.equals(aosReason) && Cux_Common_Utl.IsNull(aosSameitemid)) {
            fndError.add("同XX货号，同货号必填!");
        }
        // ===== 原校验 =====
        if ((Boolean)aosPhotoflag && Cux_Common_Utl.IsNull(aosAddress) && !judgePhotoLocat(aosPhstate)) {
            fndError.add("需要拍照时样品接收地址必填!");
        }
        boolean cond = ("退回".equals(aosProtype) && (Cux_Common_Utl.IsNull(aosContact)
            || Cux_Common_Utl.IsNull(aosContactway) || Cux_Common_Utl.IsNull(aosReturnadd)));
        if (cond) {
            if ((Boolean)aosPhotoflag || aosVedio) {
                fndError.add("退回类型退回信息必填!");
            }
        }
        if (!(Boolean)aosPhotoflag && Cux_Common_Utl.IsNull(aosReason)) {
            fndError.add("拍照为勾选时不拍照原因必填!");
        }
        if (!(Boolean)aosPhoto && Cux_Common_Utl.IsNull(aosReason)) {
            fndError.add("拍照为勾选时不拍照原因必填!");
        }
        if (aosUserLong != currentUserId) {
            fndError.add("只允许流程节点操作人点击提交!");
        }
        if (sign.compete.name.equals(aosStatus)) {
            fndError.add("已完成状态不允许点击提交!");
        }
        if (sign.simple.name.equals(aosPhstate) && FndGlobal.IsNull(aosAttach)) {
            fndError.add("拍照地点=工厂简拍时，建模资料字段必填!");
        }
        cond = ((sign.newStatus.name.equals(aosStatus) || sign.prepara.name.equals(aosStatus))
            && sign.simple.name.equals(aosPhstate));
        if (cond) {
            DynamicObject aosItemid = dyMain.getDynamicObject("aos_itemid");
            String aosItemname = dyMain.getString("aos_itemname");
            String aosPonumber = dyMain.getString("aos_ponumber");
            String category = (String)SalUtil.getCategoryByItemId(aosItemid.getPkValue().toString()).get("name");
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
            boolean cond1 = QueryServiceHelper.exists("aos_sealsample",
                new QFilter("aos_item.id", QCP.equals, aosItemid.getPkValue().toString())
                    .and("aos_contractnowb", QCP.equals, aosPonumber).and("aos_model", QCP.equals, "否").toArray());
            boolean cond2 = QueryServiceHelper.exists("aos_sealsample",
                new QFilter("aos_item.id", QCP.equals, aosItemid.getPkValue().toString())
                    .and("aos_contractnowb", QCP.equals, aosPonumber).and("aos_model", QCP.equals, "").toArray());
            boolean cond3 = QueryServiceHelper.exists("aos_sealsample",
                new QFilter("aos_item.id", QCP.equals, aosItemid.getPkValue().toString())
                    .and("aos_contractnowb", QCP.equals, aosPonumber).toArray());
            boolean cond4 = QueryServiceHelper.exists("aos_mkt_3dselect",
                new QFilter("aos_category1", QCP.equals, aosCategory1).and("aos_category2", QCP.equals, aosCategory2)
                    .and("aos_category3", QCP.equals, aosCategory3).and("aos_name", QCP.equals, aosItemname).toArray());
            cond = (cond1 || cond2 || (!cond3 && !cond4));
            if (cond) {
                fndError.add("无法建模的产品不允许改成工厂简拍!");
            }
        }
        if (fndError.getCount() > 0) {
            throw fndError;
        }
    }

    public static void syncPhotoReq(DynamicObject dyMain, Boolean newflag) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            Object aosSourceid = dyMain.get("aos_sourceid");
            String aosStatus = dyMain.getString("aos_status");
            Object aosPhotoflag = dyMain.get("aos_photoflag");
            Object aosReason = dyMain.get("aos_reason");
            Object aosSameitemid = dyMain.get("aos_sameitemid");
            // 拍照地点
            Object aosPhstate = dyMain.get("aos_phstate");
            boolean aosVedio = dyMain.getBoolean("aos_vedio");
            Object aosDesc = dyMain.get("aos_desc");
            Object aosComment = dyMain.get("aos_comment");
            if (aosSourceid == null) {
                return;
            }
            // 同步拍照需求表
            DynamicObject aosMktPhotoreq = BusinessDataServiceHelper.loadSingle(aosSourceid, "aos_mkt_photoreq");
            Object aosSourceidlist = aosMktPhotoreq.get("aos_sourceid");
            aosMktPhotoreq.set("aos_samplestatus", aosStatus);
            aosMktPhotoreq.set("aos_desc", aosDesc);
            aosMktPhotoreq.set("aos_phstate", aosPhstate);
            // 同步拍照任务清单
            DynamicObject aosMktPhotolist;
            boolean photoListSave = true;
            try {
                aosMktPhotolist = BusinessDataServiceHelper.loadSingle(aosSourceidlist, "aos_mkt_photolist");
            } catch (KDException e) {
                aosMktPhotolist = BusinessDataServiceHelper.newDynamicObject("aos_mkt_photolist");
                photoListSave = false;
            }

            aosMktPhotolist.set("aos_samplestatus", aosStatus);
            aosMktPhotolist.set("aos_desc", aosDesc);
            if (sign.inStock.name.equals(aosStatus)) {
                aosMktPhotoreq.set("aos_sampledate", new Date());
                // 样品备注
                aosMktPhotoreq.set("aos_comment", aosComment);
                aosMktPhotolist.set("aos_sampledate", new Date());
            }
            if (sign.compete.name.equals(aosStatus)) {
                aosMktPhotoreq.set("aos_installdate", new Date());
                aosMktPhotolist.set("aos_installdate", new Date());
            }
            if (newflag) {
                aosMktPhotoreq.set("aos_photoflag", aosPhotoflag);
                aosMktPhotoreq.set("aos_reason", aosReason);
                aosMktPhotoreq.set("aos_sameitemid", aosSameitemid);
                if (!(boolean)aosPhotoflag && !aosVedio) {
                    String phStatus = aosMktPhotoreq.getString("aos_status");
                    // 生成不需要拍
                    AosMktNoPhotoUtil.createNoPhotoEntity(aosMktPhotoreq);
                    aosMktPhotoreq.set("aos_status", "不需拍");
                    aosMktPhotoreq.set("aos_user", SYSTEM);
                    aosMktPhotolist.set("aos_phstatus", "不需拍");
                    FndHistory.Create(aosMktPhotoreq, "提交(样品入库回写),下节点：不需拍", phStatus);
                }
            }
            OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq", new DynamicObject[] {aosMktPhotoreq},
                OperateOption.create());
            if (photoListSave) {
                OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
                    new DynamicObject[] {aosMktPhotolist}, OperateOption.create());
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    public static void updatePhotoReq(Object sourceid) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            FndError fndError = new FndError();
            DynamicObject aosMktPhotoreq = BusinessDataServiceHelper.loadSingle(sourceid, "aos_mkt_photoreq");
            // 初始状态
            String aosStatusS = aosMktPhotoreq.getString("aos_status");
            // 消息参数
            String messageId = null;
            String messageStr = null;
            // 数据层
            Object aosPhotoFlag = aosMktPhotoreq.get("aos_photoflag");
            Object aosVedioFlag = aosMktPhotoreq.get("aos_vedioflag");
            Object aosSourceid = aosMktPhotoreq.get("aos_sourceid");
            Object aosWhiteph = aosMktPhotoreq.get("aos_whiteph");
            Object aosActph = aosMktPhotoreq.get("aos_actph");
            Object aosVedior = aosMktPhotoreq.get("aos_vedior");
            Object aosBillno = aosMktPhotoreq.get("billno");
            Object reqFid = aosMktPhotoreq.get("id");
            Object reqFidSplit = null;
            Object aos3d = aosMktPhotoreq.get("aos_3d");
            String aosPhstate = aosMktPhotoreq.getString("aos_phstate");
            boolean need3dFlag = false;
            boolean skipWhite = false;
            if (sign.simple.name.equals(aosPhstate)) {
                need3dFlag = true;
            }
            if (sign.goFactory.name.equals(aosPhstate) || sign.outSource.name.equals(aosPhstate)
                || sign.factoryOwn.name.equals(aosPhstate)) {
                skipWhite = true;
            }
            // 校验
            // 校验白底摄影师是否为空
            if ((boolean)aosPhotoFlag && aosWhiteph == null) {
                fndError.add("白底摄影师为空,拍照流程无法流转!");
            }

            if ((boolean)aosVedioFlag && aosVedior == null) {
                fndError.add("摄像师为空,视频流程无法流转!");
            }
            if (fndError.getCount() > 0) {
                throw fndError;
            }
            // 根据是否拍照是否拍视频分为两张单据
            if ((boolean)aosPhotoFlag && (boolean)aosVedioFlag) {
                // 需要拍照与视频
                // 裂项复制的字段
                DynamicObject aosMktPhotoReqCopy = BusinessDataServiceHelper.newDynamicObject("aos_mkt_photoreq");
                aosMktPhotoReqCopy.set("billstatus", "A");
                aosMktPhotoReqCopy.set("aos_requireby", aosMktPhotoreq.get("aos_requireby"));
                aosMktPhotoReqCopy.set("aos_requiredate", aosMktPhotoreq.get("aos_requiredate"));
                aosMktPhotoReqCopy.set("aos_shipdate", aosMktPhotoreq.getDate("aos_shipdate"));
                aosMktPhotoReqCopy.set("aos_urgent", aosMktPhotoreq.get("aos_urgent"));
                aosMktPhotoReqCopy.set("aos_photoflag", aosMktPhotoreq.get("aos_photoflag"));
                aosMktPhotoReqCopy.set("aos_reason", aosMktPhotoreq.get("aos_reason"));
                aosMktPhotoReqCopy.set("aos_sameitemid", aosMktPhotoreq.get("aos_sameitemid"));
                aosMktPhotoReqCopy.set("aos_vedioflag", aosMktPhotoreq.get("aos_vedioflag"));
                aosMktPhotoReqCopy.set("aos_reqtype", aosMktPhotoreq.get("aos_reqtype"));
                aosMktPhotoReqCopy.set("aos_sourceid", aosMktPhotoreq.get("aos_sourceid"));
                aosMktPhotoReqCopy.set("aos_itemid", aosMktPhotoreq.get("aos_itemid"));
                aosMktPhotoReqCopy.set("aos_is_saleout",
                    ProgressUtil.Is_saleout(aosMktPhotoreq.getDynamicObject("aos_itemid").getPkValue()));
                aosMktPhotoReqCopy.set("aos_itemname", aosMktPhotoreq.get("aos_itemname"));
                aosMktPhotoReqCopy.set("aos_contrybrand", aosMktPhotoreq.get("aos_contrybrand"));
                aosMktPhotoReqCopy.set("aos_newitem", aosMktPhotoreq.get("aos_newitem"));
                aosMktPhotoReqCopy.set("aos_newvendor", aosMktPhotoreq.get("aos_newvendor"));
                aosMktPhotoReqCopy.set("aos_ponumber", aosMktPhotoreq.get("aos_ponumber"));
                aosMktPhotoReqCopy.set("aos_linenumber", aosMktPhotoreq.get("aos_linenumber"));
                aosMktPhotoReqCopy.set("aos_earlydate", aosMktPhotoreq.get("aos_earlydate"));
                aosMktPhotoReqCopy.set("aos_checkdate", aosMktPhotoreq.get("aos_checkdate"));
                aosMktPhotoReqCopy.set("aos_specification", aosMktPhotoreq.get("aos_specification"));
                aosMktPhotoReqCopy.set("aos_seting1", aosMktPhotoreq.get("aos_seting1"));
                aosMktPhotoReqCopy.set("aos_seting2", aosMktPhotoreq.get("aos_seting2"));
                aosMktPhotoReqCopy.set("aos_sellingpoint", aosMktPhotoreq.get("aos_sellingpoint"));
                aosMktPhotoReqCopy.set("aos_vendor", aosMktPhotoreq.get("aos_vendor"));
                aosMktPhotoReqCopy.set("aos_city", aosMktPhotoreq.get("aos_city"));
                aosMktPhotoReqCopy.set("aos_contact", aosMktPhotoreq.get("aos_contact"));
                aosMktPhotoReqCopy.set("aos_address", aosMktPhotoreq.get("aos_address"));
                aosMktPhotoReqCopy.set("aos_phone", aosMktPhotoreq.get("aos_phone"));
                aosMktPhotoReqCopy.set("aos_phstate", aosMktPhotoreq.get("aos_phstate"));
                aosMktPhotoReqCopy.set("aos_rcvbill", aosMktPhotoreq.get("aos_rcvbill"));
                aosMktPhotoReqCopy.set("aos_sampledate", aosMktPhotoreq.get("aos_sampledate"));
                aosMktPhotoReqCopy.set("aos_installdate", aosMktPhotoreq.get("aos_installdate"));
                aosMktPhotoReqCopy.set("aos_poer", aosMktPhotoreq.get("aos_poer"));
                aosMktPhotoReqCopy.set("aos_developer", aosMktPhotoreq.get("aos_developer"));
                aosMktPhotoReqCopy.set("aos_follower", aosMktPhotoreq.get("aos_follower"));
                aosMktPhotoReqCopy.set("aos_whiteph", aosMktPhotoreq.get("aos_whiteph"));
                aosMktPhotoReqCopy.set("aos_actph", aosMktPhotoreq.get("aos_actph"));
                aosMktPhotoReqCopy.set("aos_vedior", aosMktPhotoreq.get("aos_vedior"));
                aosMktPhotoReqCopy.set("aos_3d", aosMktPhotoreq.get("aos_3d"));
                aosMktPhotoReqCopy.set("aos_whitedate", aosMktPhotoreq.get("aos_whitedate"));
                aosMktPhotoReqCopy.set("aos_actdate", aosMktPhotoreq.get("aos_actdate"));
                aosMktPhotoReqCopy.set("aos_picdate", aosMktPhotoreq.get("aos_picdate"));
                aosMktPhotoReqCopy.set("aos_funcpicdate", aosMktPhotoreq.get("aos_funcpicdate"));
                aosMktPhotoReqCopy.set("aos_vedio", aosMktPhotoreq.get("aos_vedio"));
                aosMktPhotoReqCopy.set("billno", aosMktPhotoreq.get("billno"));
                if (sign.simple.name.equals(aosPhstate)) {
                    aosMktPhotoReqCopy.set("aos_user", SYSTEM);
                } else {
                    aosMktPhotoReqCopy.set("aos_user", aosMktPhotoreq.get("aos_vedior"));
                }
                aosMktPhotoReqCopy.set("aos_type", "视频");
                aosMktPhotoReqCopy.set("aos_designer", aosMktPhotoreq.get("aos_designer"));
                aosMktPhotoReqCopy.set("aos_sale", aosMktPhotoreq.get("aos_sale"));
                aosMktPhotoReqCopy.set("aos_vediotype", aosMktPhotoreq.get("aos_vediotype"));
                aosMktPhotoReqCopy.set("aos_organization1", aosMktPhotoreq.get("aos_organization1"));
                aosMktPhotoReqCopy.set("aos_organization2", aosMktPhotoreq.get("aos_organization2"));
                aosMktPhotoReqCopy.set("aos_vediotype", aosMktPhotoreq.get("aos_vediotype"));
                aosMktPhotoReqCopy.set("aos_orgtext", aosMktPhotoreq.get("aos_orgtext"));
                aosMktPhotoReqCopy.set("aos_samplestatus", aosMktPhotoreq.get("aos_samplestatus"));
                aosMktPhotoReqCopy.set("aos_3dflag", aosMktPhotoreq.get("aos_3dflag"));
                aosMktPhotoReqCopy.set("aos_3d_reason", aosMktPhotoreq.get("aos_3d_reason"));
                aosMktPhotoReqCopy.set("aos_desc", aosMktPhotoreq.get("aos_desc"));
                // 带出字幕需求
                aosMktPhotoReqCopy.set("aos_subtitle", aosMktPhotoreq.get("aos_subtitle"));
                aosMktPhotoReqCopy.set("aos_language", aosMktPhotoreq.get("aos_language"));
                // 新增质检完成日期
                QFilter qFilterContra = new QFilter("aos_insrecordentity.aos_insresultchk", "=", "A");
                QFilter qFilterLineno = new QFilter("aos_insrecordentity.aos_instypedetailchk", "=", "1");
                QFilter qFilterPonumber =
                    new QFilter("aos_insrecordentity.aos_contractnochk", "=", aosMktPhotoreq.get("aos_ponumber"));
                QFilter qFilterLinenumber =
                    new QFilter("aos_insrecordentity.aos_lineno", "=", aosMktPhotoreq.get("aos_linenumber"));
                QFilter[] qFilters = {qFilterContra, qFilterLineno, qFilterPonumber, qFilterLinenumber};
                DynamicObject dyDate = QueryServiceHelper.queryOne("aos_qctasklist", "aos_quainscomdate", qFilters);
                if (dyDate != null) {
                    aosMktPhotoReqCopy.set("aos_quainscomdate", dyDate.get("aos_quainscomdate"));
                }
                // 照片需求单据体
                DynamicObjectCollection aosEntryentityS =
                    aosMktPhotoReqCopy.getDynamicObjectCollection("aos_entryentity");
                DynamicObjectCollection aosEntryentityOriS =
                    aosMktPhotoreq.getDynamicObjectCollection("aos_entryentity");
                for (DynamicObject aosEntryentityOri : aosEntryentityOriS) {
                    DynamicObject aosEntryentity = aosEntryentityS.addNew();
                    aosEntryentity.set("aos_applyby", aosEntryentityOri.get("aos_applyby"));
                    aosEntryentity.set("aos_picdesc", aosEntryentityOri.get("aos_picdesc"));
                }
                // 照片需求单据体(新)
                DynamicObjectCollection aosEntryentity5S =
                    aosMktPhotoReqCopy.getDynamicObjectCollection("aos_entryentity5");
                DynamicObjectCollection aosEntryentity5OriS =
                    aosMktPhotoreq.getDynamicObjectCollection("aos_entryentity5");
                for (DynamicObject aosEntryentity5Ori : aosEntryentity5OriS) {
                    DynamicObject aosEntryentity5 = aosEntryentity5S.addNew();
                    aosEntryentity5.set("aos_reqfirst", aosEntryentity5Ori.get("aos_reqfirst"));
                    aosEntryentity5.set("aos_reqother", aosEntryentity5Ori.get("aos_reqother"));
                    aosEntryentity5.set("aos_detail", aosEntryentity5Ori.get("aos_detail"));
                    aosEntryentity5.set("aos_scene1", aosEntryentity5Ori.get("aos_scene1"));
                    aosEntryentity5.set("aos_object1", aosEntryentity5Ori.get("aos_object1"));
                    aosEntryentity5.set("aos_scene2", aosEntryentity5Ori.get("aos_scene2"));
                    aosEntryentity5.set("aos_object2", aosEntryentity5Ori.get("aos_object2"));
                    aosEntryentity5.set("aos_productstyle_new", aosEntryentity5Ori.get("aos_productstyle_new"));
                    aosEntryentity5.set("aos_shootscenes", aosEntryentity5Ori.get("aos_shootscenes"));
                }
                // 照片需求单据体(新2)
                DynamicObjectCollection aosEntryentity6S =
                    aosMktPhotoReqCopy.getDynamicObjectCollection("aos_entryentity6");
                DynamicObjectCollection aosEntryentity6OriS =
                    aosMktPhotoreq.getDynamicObjectCollection("aos_entryentity6");
                for (DynamicObject aosEntryentity6Ori : aosEntryentity6OriS) {
                    DynamicObject aosEntryentity6 = aosEntryentity6S.addNew();
                    aosEntryentity6.set("aos_reqsupp", aosEntryentity6Ori.get("aos_reqsupp"));
                    aosEntryentity6.set("aos_devsupp", aosEntryentity6Ori.get("aos_devsupp"));
                }
                // 视频需求单据体
                DynamicObjectCollection aosEntryentity1S =
                    aosMktPhotoReqCopy.getDynamicObjectCollection("aos_entryentity1");
                DynamicObjectCollection aosEntryentityOri1S =
                    aosMktPhotoreq.getDynamicObjectCollection("aos_entryentity1");
                for (DynamicObject aosEntryentityOri1 : aosEntryentityOri1S) {
                    DynamicObject aosEntryentity1 = aosEntryentity1S.addNew();
                    aosEntryentity1.set("aos_applyby2", aosEntryentityOri1.get("aos_applyby2"));
                    aosEntryentity1.set("aos_veddesc", aosEntryentityOri1.get("aos_veddesc"));
                }
                // 拍摄情况单据体
                DynamicObjectCollection aosEntryentity2S =
                    aosMktPhotoReqCopy.getDynamicObjectCollection("aos_entryentity2");
                DynamicObjectCollection aosEntryentityOri2S =
                    aosMktPhotoreq.getDynamicObjectCollection("aos_entryentity2");
                for (DynamicObject aosEntryentityOri2 : aosEntryentityOri2S) {
                    DynamicObject aosEntryentity2 = aosEntryentity2S.addNew();
                    aosEntryentity2.set("aos_phtype", aosEntryentityOri2.get("aos_phtype"));
                    aosEntryentity2.set("aos_complete", aosEntryentityOri2.get("aos_complete"));
                    aosEntryentity2.set("aos_completeqty", aosEntryentityOri2.get("aos_completeqty"));
                }
                // 流程退回原因单据体
                DynamicObjectCollection aosEntryentity3S =
                    aosMktPhotoReqCopy.getDynamicObjectCollection("aos_entryentity3");
                DynamicObjectCollection aosEntryentityOri3S =
                    aosMktPhotoreq.getDynamicObjectCollection("aos_entryentity3");
                for (DynamicObject aosEntryentityOri3 : aosEntryentityOri3S) {
                    DynamicObject aosEntryentity3 = aosEntryentity3S.addNew();
                    aosEntryentity3.set("aos_returnby", aosEntryentityOri3.get("aos_returnby"));
                    aosEntryentity3.set("aos_return", aosEntryentityOri3.get("aos_return"));
                    aosEntryentity3.set("aos_returnreason", aosEntryentityOri3.get("aos_returnreason"));
                }
                // 视频地址单据体
                DynamicObjectCollection aosEntryentity4S =
                    aosMktPhotoReqCopy.getDynamicObjectCollection("aos_entryentity4");
                DynamicObjectCollection aosEntryentityOri4S =
                    aosMktPhotoreq.getDynamicObjectCollection("aos_entryentity4");
                for (DynamicObject aosEntryentityOri4 : aosEntryentityOri4S) {
                    DynamicObject aosEntryentity4 = aosEntryentity4S.addNew();
                    aosEntryentity4.set("aos_orgshort", aosEntryentityOri4.get("aos_orgshort"));
                    aosEntryentity4.set("aos_brand", aosEntryentityOri4.get("aos_brand"));
                    aosEntryentity4.set("aos_s3address1", aosEntryentityOri4.get("aos_s3address1"));
                    aosEntryentity4.set("aos_s3address2", aosEntryentityOri4.get("aos_s3address2"));
                }
                // 本节点中改变的字段
                aosMktPhotoReqCopy.set("aos_type", "视频");
                aosMktPhotoReqCopy.set("aos_status", "视频拍摄");
                OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq",
                    new DynamicObject[] {aosMktPhotoReqCopy}, OperateOption.create());
                if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                    throw new FndError("裂项提交失败!");
                }
                aosMktPhotoreq.set("aos_type", "拍照");
                // 判断是否需要3D建模
                if (need3dFlag) {
                    aosMktPhotoreq.set("aos_status", "3D建模");
                    // 流转给3D摄影师
                    aosMktPhotoreq.set("aos_user", aos3d);
                    messageId = ((DynamicObject)aos3d).getPkValue().toString();
                } else {
                    // 跳过白底
                    if (skipWhite) {
                        aosMktPhotoreq.set("aos_status", "实景拍摄");
                        // 流转给实景摄影师
                        aosMktPhotoreq.set("aos_user", aosActph);
                        messageId = ((DynamicObject)aosActph).getPkValue().toString();
                    } else {
                        aosMktPhotoreq.set("aos_status", "白底拍摄");
                        // 流转给白底摄影师
                        aosMktPhotoreq.set("aos_user", aosWhiteph);
                        if (aosWhiteph != null) {
                            messageId = ((DynamicObject)aosWhiteph).getPkValue().toString();
                        }
                    }
                }
                reqFidSplit = operationrst.getSuccessPkIds().get(0);
            } else if ((boolean)aosPhotoFlag) {
                // 只需要拍照
                aosMktPhotoreq.set("aos_type", "拍照");
                if (need3dFlag) {
                    aosMktPhotoreq.set("aos_status", "3D建模");
                    // 流转给3D摄影师
                    aosMktPhotoreq.set("aos_user", aos3d);
                    messageId = ((DynamicObject)aos3d).getPkValue().toString();
                } else {
                    // 跳过白底
                    if (skipWhite) {
                        aosMktPhotoreq.set("aos_status", "实景拍摄");
                        // 流转给实景摄影师
                        aosMktPhotoreq.set("aos_user", aosActph);
                        messageId = ((DynamicObject)aosActph).getPkValue().toString();
                    } else {
                        aosMktPhotoreq.set("aos_status", "白底拍摄");
                        // 流转给白底摄影师
                        aosMktPhotoreq.set("aos_user", aosWhiteph);
                        if (aosWhiteph != null) {
                            messageId = ((DynamicObject)aosWhiteph).getPkValue().toString();
                        }
                    }
                }
                messageStr = "拍照需求表-白底拍摄";
            } else if ((boolean)aosVedioFlag) {
                // 只需要视频
                aosMktPhotoreq.set("aos_type", "视频");
                aosMktPhotoreq.set("aos_status", "视频拍摄");
                // 流转给摄像师
                aosMktPhotoreq.set("aos_user", aosVedior);
                if (aosVedior != null) {
                    messageId = ((DynamicObject)aosVedior).getPkValue().toString();
                }
                messageStr = "拍照需求表-视频拍摄";
            }
            String aosStatusE = aosMktPhotoreq.getString("aos_status");
            if (!aosStatusS.equals(aosStatusE)) {
                FndHistory.Create(aosMktPhotoreq, "提交(样品入库提交),下节点：" + aosStatusE, aosStatusS);
            }
            // 源单保存
            OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq",
                new DynamicObject[] {aosMktPhotoreq}, OperateOption.create());
            if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                throw new FndError("源单保存失败!");
            }
            // 回写拍照任务清单
            boolean photoListSave = true;
            DynamicObject aosMktPhotolist;
            try {
                aosMktPhotolist = BusinessDataServiceHelper.loadSingle(aosSourceid, "aos_mkt_photolist");
            } catch (KDException ex) {
                aosMktPhotolist = BusinessDataServiceHelper.newDynamicObject("aos_mkt_photolist");
                photoListSave = false;
            }

            if ((boolean)aosPhotoFlag && (boolean)aosVedioFlag) {
                if (need3dFlag) {
                    aosMktPhotolist.set("aos_phstatus", "3D建模");
                } else {
                    // 跳过白底
                    if (skipWhite) {
                        aosMktPhotoreq.set("aos_status", "实景拍摄");
                    } else {
                        aosMktPhotolist.set("aos_phstatus", "白底拍摄");
                    }
                }
                aosMktPhotolist.set("aos_vedstatus", "视频拍摄");
            } else if ((boolean)aosPhotoFlag) {
                if (need3dFlag) {
                    aosMktPhotolist.set("aos_phstatus", "3D建模");
                } else {
                    // 跳过白底
                    if (skipWhite) {
                        aosMktPhotoreq.set("aos_status", "实景拍摄");
                    } else {
                        aosMktPhotolist.set("aos_phstatus", "白底拍摄");
                    }
                }
            } else if ((boolean)aosVedioFlag) {
                aosMktPhotolist.set("aos_vedstatus", "视频拍摄");
            }
            if (photoListSave) {
                OperationResult operationrst1 = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
                    new DynamicObject[] {aosMktPhotolist}, OperateOption.create());
                if (operationrst1.getValidateResult().getValidateErrors().size() != 0) {
                    throw new FndError("拍照任务清单保存失败!");
                }
            }
            if (need3dFlag) {
                // 生成3D确认单
                aos_mkt_3design_bill.Generate3Design(aosMktPhotoreq);
            }
            // 发送消息
            if ((boolean)aosPhotoFlag && (boolean)aosVedioFlag) {
                MKTCom.SendGlobalMessage(messageId, "aos_mkt_photoreq", String.valueOf(reqFid),
                    String.valueOf(aosBillno), "拍照需求表-白底拍摄");
                MKTCom.SendGlobalMessage(messageId, "aos_mkt_photoreq", String.valueOf(reqFidSplit),
                    String.valueOf(aosBillno), "拍照需求表-视频拍摄");
            } else {
                MKTCom.SendGlobalMessage(messageId, "aos_mkt_photoreq", String.valueOf(reqFid),
                    String.valueOf(aosBillno), messageStr);
            }

        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /** 判断拍照地点 **/
    private static boolean judgePhotoLocat(Object locat) {
        return String.valueOf(locat).equals(sign.simple.name) || String.valueOf(locat).equals(sign.factoryOwn.name);
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        Span span = MmsOtelUtils.getCusMainSpan(TRACER, MmsOtelUtils.getMethodPath());
        String control = evt.getItemKey();
        try (Scope ignore = span.makeCurrent()) {
            if (sign.submit.name.equals(control)) {
                // 提交
                aosSubmit(this.getModel().getDataEntity(true), "A");
            } else if (sign.history.name.equals(control)) {
                // 查看历史记录
                aosHistory();
            } else if (sign.returnBack.name.equals(control)) {
                aosReturn(this.getModel().getDataEntity(true));
                this.getView().invokeOperation("save");
                this.getView().invokeOperation("refresh");
            } else if (sign.querySample.name.equals(control)) {
                // 查看封样单
                querySample(this.getView(), this.getView().getModel().getDataEntity().getPkValue());
            }
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

    /** 打开历史记录 **/
    private void aosHistory() throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            FndHistory.OpenHistory(this.getView());
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    public void aosSubmit(DynamicObject dyMain, String type) throws FndError {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            // 先做数据校验判断是否可以提交
            saveControl(dyMain);
            long aosRequireby = (long)(dyMain.getDynamicObject("aos_requireby")).getPkValue();
            String aosStatus = dyMain.get("aos_status").toString();
            Object aosPhotoflag = dyMain.get("aos_photoflag");
            Object aosPhstate = dyMain.get("aos_phstate");
            boolean aosVedio = dyMain.getBoolean("aos_vedio");
            boolean updatePhotoReqFlag = false;
            Object aosSourceid = dyMain.get("aos_sourceid");
            // 样品间管理人员
            String sampleManager = ProgressUtil.findUserByNumber("021903");
            boolean newflag = false;
            switch (aosStatus) {
                case "新建":
                case "退回":
                    if (!(boolean)aosPhotoflag && !aosVedio) {
                        dyMain.set("aos_status", "已完成");
                        dyMain.set("aos_user", sampleManager);
                        updatePhotoReqFlag = true;
                    } else {
                        if (sign.goSample.name.equals(aosPhstate)) {
                            dyMain.set("aos_user", sampleManager);
                        } else if (sign.outSource.name.equals(aosPhstate)) {
                            dyMain.set("aos_user", aosRequireby);
                        }
                        dyMain.set("aos_status", "备样中");
                    }
                    newflag = true;
                    break;
                case "备样中":
                    boolean cond = (aosPhstate != null
                        && ((String.valueOf(aosPhstate)).contains("工厂") || "外包拍照".equals(aosPhstate)));
                    if (sign.goSample.name.equals(aosPhstate)) {
                        dyMain.set("aos_status", "已入库");
                        dyMain.set("aos_user", sampleManager);
                        updatePhoto(dyMain, sampleManager);
                    } else if (cond) {
                        dyMain.set("aos_status", "已完成");
                        dyMain.set("aos_user", sampleManager);
                        updatePhotoReqFlag = true;
                    }
                    dyMain.set("aos_waredate", new Date());
                    break;
                case "已寄样":
                    if (sign.outSource.name.equals(aosPhstate)) {
                        dyMain.set("aos_user", aosRequireby);
                    } else if (sign.goSample.name.equals(aosPhstate)) {
                        dyMain.set("aos_user", sampleManager);
                    }
                    dyMain.set("aos_status", "已入库");
                    break;
                case "已入库":
                    dyMain.set("aos_status", "已完成");
                    dyMain.set("aos_user", sampleManager);
                    updatePhotoReqFlag = true;
                    dyMain.set("aos_completdate", new Date());
                    break;
                case "已安装":
                    dyMain.set("aos_status", "已完成");
                    dyMain.set("aos_user", sampleManager);
                    updatePhotoReqFlag = true;
                    break;
                default:
                    break;
            }
            // 同步拍照需求表
            syncPhotoReq(dyMain, newflag);
            // 完成后裂项
            if (updatePhotoReqFlag) {
                dyMain.set("aos_completedate", new Date());
                updatePhotoReq(aosSourceid);
            }
            // 插入历史记录
            FndHistory.Create(dyMain, "提交", aosStatus);
            SaveServiceHelper.save(new DynamicObject[] {dyMain});
            if (sign.A.name.equals(type)) {
                this.getView().invokeOperation("refresh");
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /** 创建3D产品设计单 **/
    @Override
    public void afterLoadData(EventObject e) {
        super.afterLoadData(e);
        statusControl();
        generateLov();
        Boolean aosPhotoFlag = (Boolean)this.getModel().getValue("aos_photoflag");
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

    @Override
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        statusControl();
        generateLov();
    }

    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();
        if (sign.proType.name.equals(name) && sign.returnB.name.equals(this.getModel().getValue(name).toString())) {
            aosProTypeChange();
        } else if (sign.photoFlag.name.equals(name)) {
            aosPhotoFlagChange();
        } else if (sign.phstate.name.equals(name)) {
            aosPhstateChange();
        } else if (sign.address.name.equals(name)) {
            aosAddressChange();
        }
    }

    /**
     * 样品接收地址值改变事件
     */
    private void aosAddressChange() {
        QFilter filterType =
            new QFilter("aos_entryentity.aos_content", QCP.equals, this.getModel().getValue("aos_address"));
        DynamicObject aosMktSampleaddress = QueryServiceHelper.queryOne("aos_mkt_sampleaddress",
            "aos_entryentity.aos_desc aos_desc", filterType.toArray());
        if (FndGlobal.IsNotNull(aosMktSampleaddress)) {
            this.getModel().setValue("aos_desc", aosMktSampleaddress.get("aos_desc"));
        }
    }

    private void aosPhstateChange() {
        generateLov();
    }

    /** 是否拍照值改变 **/
    private void aosPhotoFlagChange() {
        Boolean aosPhotoFlag = (Boolean)this.getModel().getValue("aos_photoflag");
        this.getModel().setValue("aos_photo", aosPhotoFlag);
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

    @Override
    public void beforeClosed(BeforeClosedEvent e) {
        super.beforeClosed(e);
        e.setCheckDataChange(false);
    }

    private void aosProTypeChange() {
        Object aosVendor = this.getModel().getValue("aos_vendor");
        QFilter filterVendor = new QFilter("aos_vendor", "=", aosVendor);
        QFilter filterType = new QFilter("aos_protype", "=", "退回");
        QFilter[] filters = {filterVendor, filterType};
        DynamicObjectCollection aosMktRcvS = QueryServiceHelper.query("aos_mkt_rcv",
            "aos_contact,aos_contactway,aos_returnadd", filters, "createtime desc");
        for (DynamicObject aosMktRcv : aosMktRcvS) {
            this.getModel().setValue("aos_contact", aosMktRcv.get("aos_contact"));
            this.getModel().setValue("aos_contactway", aosMktRcv.get("aos_contactway"));
            this.getModel().setValue("aos_returnadd", aosMktRcv.get("aos_returnadd"));
            return;
        }
    }

    private void generateLov() {
        Object aosPhstate = this.getModel().getValue("aos_phstate");
        // 根据拍照地点 动态设置下拉框
        ComboEdit comboEdit = this.getControl("aos_address");
        // 设置下拉框的值
        List<ComboItem> data = new ArrayList<>();
        QFilter filterStatus = new QFilter("aos_address", "=", aosPhstate);
        QFilter filterType = new QFilter("aos_entryentity.aos_valid", "=", true);
        QFilter[] filters = {filterStatus, filterType};
        DynamicObjectCollection aosMktSampleaddress =
            QueryServiceHelper.query("aos_mkt_sampleaddress", "aos_entryentity.aos_content aos_content", filters);
        for (DynamicObject d : aosMktSampleaddress) {
            String aosContent = d.getString("aos_content");
            data.add(new ComboItem(new LocaleString(aosContent), aosContent));
        }
        comboEdit.setComboItems(data);
    }

    /** 全局状态控制 **/
    private void statusControl() {
        // 数据层
        String aosStatus = this.getModel().getValue("aos_status").toString();
        DynamicObject aosUser = (DynamicObject)this.getModel().getValue("aos_user");
        Object currentUserId = UserServiceHelper.getCurrentUserId();
        Object currentUserName = UserServiceHelper.getUserInfoByID((long)currentUserId).get("name");
        // 当前节点操作人不为当前用户 全锁
        if (!aosUser.getPkValue().toString().equals(currentUserId.toString())
            && !"刘中怀".equals(currentUserName.toString()) && !"营销测试账号".equals(currentUserName.toString())
            && !"程震杰".equals(currentUserName.toString()) && !"陈聪".equals(currentUserName.toString())
            && !"杨晶晶".equals(currentUserName.toString())) {
            this.getView().setEnable(false, "titlepanel");
            this.getView().setEnable(false, "contentpanelflex");
            this.getView().setVisible(false, "bar_save");
        }
        // 状态控制
        if (!sign.newStatus.name.equals(aosStatus) && !sign.inStock.name.equals(aosStatus)
            && !sign.returnB.name.equals(aosStatus)) {
            List<String> listNoEnable = Arrays.asList("aos_returnreason", "aos_issuedate", "aos_express_info");
            setContinEnable("aos_contentpanelflex", listNoEnable);
        } else if (sign.inStock.name.equals(aosStatus)) {
            List<String> listNoEnable =
                Arrays.asList("aos_returnreason", "aos_issuedate", "aos_express_info", "aos_phstate", "aos_address");
            setContinEnable("aos_contentpanelflex", listNoEnable);
        } else {
            this.getView().setEnable(true, "contentpanelflex");
        }
        if (sign.compete.name.equals(aosStatus)) {
            this.getView().setVisible(true, "bar_save");
            this.getView().setEnable(true, "bar_save");
            this.getView().setVisible(false, "aos_submit");
        }
        if (sign.inStock.name.equals(aosStatus)) {
            this.getView().setVisible(true, "aos_return");
            this.getView().setEnable(true, "aos_returnreason");
        } else if (sign.prepara.name.equals(aosStatus)) {
            this.getView().setVisible(true, "aos_return");
            this.getView().setEnable(true, "aos_returnreason");
        } else {
            this.getView().setVisible(false, "aos_return");
        }
        if (!sign.newStatus.name.equals(aosStatus) && !sign.returnB.name.equals(aosStatus)) {
            // 样品备注
            this.getView().setEnable(false, "aos_comment");
        }
    }

    public void setContinEnable(String name, List<String> listNoEableField) {
        String panelName = name;
        if (sign.contentFlex.name.equals(panelName)) {
            panelName = "aos_flexpanelap1";
        }
        Container flexPanel = this.getView().getControl(panelName);
        String[] keys = flexPanel.getItems().stream().map(Control::getKey)
            .filter(key -> !listNoEableField.contains(key)).toArray(String[]::new);
        this.getView().setEnable(false, keys);
    }

    /** 拍照地点=来样拍照，状态=备样中,提交时,更新对应拍照需求表的当前节点操作人 **/
    private void updatePhoto(DynamicObject dyMain, Object user) {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            QFBuilder builder = new QFBuilder();
            builder.add("billno", "=", dyMain.get("aos_orignbill"));
            DynamicObject[] dycPhoto =
                BusinessDataServiceHelper.load("aos_mkt_photoreq", "id,aos_user", builder.toArray());
            if (dycPhoto.length > 0) {
                dycPhoto[0].set("aos_user", user);
                SaveServiceHelper.update(dycPhoto);
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
            throw ex;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 标识枚举类
     */
    private enum sign {
        /**
         * 新建
         */
        newStatus("新建"),
        /**
         * 拍照地点
         */
        phstate("aos_phstate"),
        /**
         * 退回
         */
        returnB("退回"),
        /**
         * 表单面板
         */
        contentFlex("aos_contentpanelflex"),
        /**
         * 拍照标记
         */
        photoFlag("aos_photoflag"),
        /**
         * 外包拍照
         */
        outSource("外包拍照"),
        /**
         * 样品处理方式标识
         */
        proType("aos_protype"),
        /**
         * 来样拍照
         */
        goSample("来样拍照"),
        /**
         * 去工厂拍
         */
        goFactory("去工厂拍"),
        /**
         * 工厂自拍
         */
        factoryOwn("工厂自拍"),
        /**
         * A
         */
        A("A"),
        /**
         * 已入库
         */
        inStock("已入库"),
        /**
         * 备样中
         */
        prepara("备样中"),
        /**
         * 工厂简拍
         */
        simple("工厂简拍"),
        /**
         * 已完成
         */
        compete("已完成"),
        /**
         * 提交按钮
         */
        submit("aos_submit"),
        /**
         * 拍照地点
         */
        address("aos_address"),
        /**
         * 查看历史记录
         */
        history("aos_history"),
        /**
         * 退回
         */
        returnBack("aos_return"),
        /**
         * 查看样品入库单
         */
        querySample("aos_querysample"),
        /**
         * 同XX货号
         */
        sameItem("同XX货号");

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