package mkt.progress.photo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import common.Cux_Common_Utl;
import common.fnd.FndGlobal;
import common.fnd.FndHistory;
import common.fnd.FndReturn;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import mkt.progress.listing.AosMktListingReqBill;

/**
 * @author aosom 拍照清单同步
 */
public class AosMktProgPhReqSyncTask extends AbstractTask {
    /** 系统管理员 **/
    public final static String SYSTEM = Cux_Common_Utl.SYSTEM;

    public static void doOperate() {
        /* 将SCM生成的拍照需求表同步至拍照任务清单 **/
        photoSync();
        /* 将SCM生成的Listing优化需求表同步至拍照任务清单 **/
        listingSync();
        /* 合同取消自动关闭 **/
        closePoSync();
    }

    /**
     * 合同取消自动关闭
     */
    private static void closePoSync() {
        List<String> aosPurContractS =
            QueryServiceHelper.query("aos_purcontract", "billno", new QFilter("billstatus", QCP.equals, "F").toArray())
                .stream().map(dy -> dy.getString("billno")).distinct().collect(Collectors.toList());
        DynamicObject[] aosMktPhotoReqS = BusinessDataServiceHelper.load("aos_mkt_photoreq",
            "aos_user,aos_status,aos_ponumber,aos_type,id,aos_photoflag,aos_vedioflag,"
                + "aos_itemid,aos_itemname,billno,aos_sameitemid,aos_reason,aos_developer,aos_poer,"
                + "aos_shipdate,aos_specification,aos_seting1",
            new QFilter("aos_status", QCP.not_equals, "已完成").and("aos_ponumber", QCP.in, aosPurContractS).toArray());
        for (DynamicObject aosMktPhotoReq : aosMktPhotoReqS) {
            aosMktPhotoReq.set("aos_user", SYSTEM);
            aosMktPhotoReq.set("aos_status", "不需拍");
            aosMktPhotoReq.set("aos_photoflag", false);
            aosMktPhotoReq.set("aos_vedioflag", false);
            if ("拍照".equals(aosMktPhotoReq.getString("aos_type"))) {
                // 数据进入不拍照任务清单
                AosMktNoPhotoUtil.createNoPhotoEntity(aosMktPhotoReq);
                // 将3D调整为已完成
                DynamicObject aosMkt3design = BusinessDataServiceHelper.loadSingle("aos_mkt_3design",
                    new QFilter("aos_sourceid", QCP.equals, aosMktPhotoReq.getPkValue().toString()).toArray());
                if (FndGlobal.IsNotNull(aosMkt3design)) {
                    aosMkt3design.set("aos_status", "已完成");
                    aosMkt3design.set("aos_user", SYSTEM);
                    SaveServiceHelper.save(new DynamicObject[] {aosMkt3design});
                }
                // 将样品入库单调整为已完成
                DynamicObject aosMktRcv = BusinessDataServiceHelper.loadSingle("aos_mkt_rcv",
                    new QFilter("aos_sourceid", QCP.equals, aosMktPhotoReq.getPkValue().toString()).toArray());
                if (FndGlobal.IsNotNull(aosMktRcv)) {
                    aosMktRcv.set("aos_status", "已完成");
                    aosMktRcv.set("aos_user", SYSTEM);
                    SaveServiceHelper.save(new DynamicObject[] {aosMktRcv});
                }
                // 将拍照清单同步
                DynamicObject aosMktPhotolist = BusinessDataServiceHelper.loadSingle("aos_mkt_photolist",
                    new QFilter("billno", QCP.equals, aosMktPhotoReq.getString("billno")).toArray());
                if (FndGlobal.IsNotNull(aosMktPhotolist)) {
                    aosMktPhotolist.set("aos_phstatus", "已完成");
                    SaveServiceHelper.save(new DynamicObject[] {aosMktPhotolist});
                }
            }
        }
        SaveServiceHelper.save(aosMktPhotoReqS);
    }

    private static void listingSync() {
        // 异常参数
        FndReturn ret = new FndReturn();
        QFilter filter = new QFilter("aos_autoflag", QCP.equals, true).and("aos_status", QCP.equals, "申请人");
        QFilter[] filters = new QFilter[] {filter};
        DynamicObjectCollection aosMktListingReqS = QueryServiceHelper.query("aos_mkt_listing_req", "id", filters);
        for (DynamicObject aosMktListingReq : aosMktListingReqS) {
            DynamicObject dyn = BusinessDataServiceHelper.loadSingle(aosMktListingReq.get("id"), "aos_mkt_listing_req");
            DynamicObjectCollection aosEntryentityS = dyn.getDynamicObjectCollection("aos_entryentity");
            List<DynamicObject> listRequire = new ArrayList<>();
            List<DynamicObject> listRequirePic = new ArrayList<>();
            for (DynamicObject aosEntryentity : aosEntryentityS) {
                // 文案
                Object aosRequire = aosEntryentity.get("aos_require");
                // 图片
                Object aosRequirepic = aosEntryentity.get("aos_requirepic");
                if (!Cux_Common_Utl.IsNull(aosRequire)) {
                    listRequire.add(aosEntryentity);
                }
                if (!Cux_Common_Utl.IsNull(aosRequirepic)) {
                    listRequirePic.add(aosEntryentity);
                }
            }
            if (listRequire.size() > 0) {
                AosMktListingReqBill.generateListingSon(listRequire, ret, dyn);
                if (ret.GetErrorCount() > 0) {
                    continue;
                }
            }
            if (listRequirePic.size() > 0) {
                AosMktListingReqBill.generateDesignReq(listRequirePic, ret, dyn);
                if (ret.GetErrorCount() > 0) {
                    continue;
                }
            }
            dyn.set("aos_status", "已完成");
            dyn.set("aos_user", SYSTEM);
            AosMktListingReqBill.setItemCate(dyn);
            OperationServiceHelper.executeOperate("save", "aos_mkt_listing_req", new DynamicObject[] {dyn},
                OperateOption.create());
        }
    }

    private static void photoSync() {
        QFilter filterExists = new QFilter("billno", QCP.equals, null).or("billno", QCP.equals, "");
        QFilter[] filters = new QFilter[] {filterExists};
        String selectField = "id,billno,aos_itemid,aos_itemname,aos_photoflag,aos_vedioflag,aos_phstate,"
            + "aos_shipdate,aos_developer,aos_poer,aos_follower,aos_whiteph,aos_actph,aos_vedior,"
            + "aos_newitem,aos_newvendor,aos_address,aos_orgtext,aos_urgent";
        DynamicObjectCollection aosMktPhotoreqS = QueryServiceHelper.query("aos_mkt_photoreq", selectField, filters);
        for (DynamicObject aosMktPhotoreq : aosMktPhotoreqS) {
            DynamicObject aosMktPhotolist = BusinessDataServiceHelper.newDynamicObject("aos_mkt_photolist");
            aosMktPhotolist.set("aos_itemid", aosMktPhotoreq.get("aos_itemid"));
            aosMktPhotolist.set("aos_itemname", aosMktPhotoreq.get("aos_itemname"));
            aosMktPhotolist.set("aos_photoflag", aosMktPhotoreq.get("aos_photoflag"));
            aosMktPhotolist.set("aos_vedioflag", aosMktPhotoreq.get("aos_vedioflag"));
            aosMktPhotolist.set("aos_phstate", aosMktPhotoreq.get("aos_phstate"));
            aosMktPhotolist.set("aos_shipdate", aosMktPhotoreq.get("aos_shipdate"));
            aosMktPhotolist.set("aos_developer", aosMktPhotoreq.get("aos_developer"));
            aosMktPhotolist.set("aos_poer", aosMktPhotoreq.get("aos_poer"));
            aosMktPhotolist.set("aos_follower", aosMktPhotoreq.get("aos_follower"));
            aosMktPhotolist.set("aos_whiteph", aosMktPhotoreq.get("aos_whiteph"));
            aosMktPhotolist.set("aos_actph", aosMktPhotoreq.get("aos_actph"));
            aosMktPhotolist.set("aos_vedior", aosMktPhotoreq.get("aos_vedior"));
            aosMktPhotolist.set("aos_newitem", aosMktPhotoreq.get("aos_newitem"));
            aosMktPhotolist.set("aos_newvendor", aosMktPhotoreq.get("aos_newvendor"));
            aosMktPhotolist.set("aos_photourl", "拍照");
            aosMktPhotolist.set("aos_vediourl", "视频");
            // 地址
            aosMktPhotolist.set("aos_address", aosMktPhotoreq.get("aos_address"));
            // 下单国别
            aosMktPhotolist.set("aos_orgtext", aosMktPhotoreq.get("aos_orgtext"));
            // 紧急提醒
            aosMktPhotolist.set("aos_urgent", aosMktPhotoreq.get("aos_urgent"));
            // 照片需求
            String aosPicdesc = "", aosPicdesc1 = "";
            QFilter filterId = new QFilter("id", "=", aosMktPhotoreq.get("id"));
            DynamicObjectCollection dycPhoto = QueryServiceHelper.query("aos_mkt_photoreq",
                "aos_entryentity.aos_picdesc aos_picdesc", new QFilter[] {filterId});
            if (dycPhoto.size() > 0) {
                if (dycPhoto.get(0).getString("aos_picdesc") != null) {
                    aosPicdesc = dycPhoto.get(0).getString("aos_picdesc");
                }
                if (dycPhoto.size() > 1) {
                    if (dycPhoto.get(1).getString("aos_picdesc") != null) {
                        aosPicdesc1 = dycPhoto.get(0).getString("aos_picdesc");
                    }
                }
            }
            aosMktPhotolist.set("aos_picdesc", aosPicdesc);
            aosMktPhotolist.set("aos_picdesc1", aosPicdesc1);
            if (aosMktPhotoreq.getBoolean("aos_photoflag")) {
                aosMktPhotolist.set("aos_phstatus", "新建");
            }
            if (aosMktPhotoreq.getBoolean("aos_vedioflag")) {
                aosMktPhotolist.set("aos_vedstatus", "新建");
            }
            aosMktPhotolist.set("billstatus", "A");
            OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photolist",
                new DynamicObject[] {aosMktPhotolist}, OperateOption.create());
            DynamicObject dyn = BusinessDataServiceHelper.loadSingle(aosMktPhotoreq.get("id"), "aos_mkt_photoreq");
            dyn.set("billno", aosMktPhotolist.get("billno"));
            dyn.set("aos_user", dyn.get("aos_requireby"));
            dyn.set("aos_sourceid", operationrst.getSuccessPkIds().get(0));
            dyn.set("aos_init", true);
            DynamicObjectCollection aosEntryentityS = dyn.getDynamicObjectCollection("aos_entryentity");
            aosEntryentityS.clear();
            DynamicObject aosEntryentity = aosEntryentityS.addNew();
            aosEntryentity.set("aos_applyby", "申请人");
            aosEntryentity.set("aos_picdesc", "见开发采购需求");
            aosEntryentity = aosEntryentityS.addNew();
            aosEntryentity.set("aos_applyby", "开发/采购");
            aosEntryentity.set("aos_picdesc", "见开发采购需求");
            DynamicObjectCollection aosEntryentity1S = dyn.getDynamicObjectCollection("aos_entryentity1");
            aosEntryentity1S.clear();
            DynamicObject aosEntryentity1 = aosEntryentity1S.addNew();
            aosEntryentity1.set("aos_applyby2", "申请人");
            aosEntryentity1.set("aos_veddesc", "见开发采购需求");
            aosEntryentity1 = aosEntryentity1S.addNew();
            aosEntryentity1.set("aos_applyby2", "开发/采购");
            aosEntryentity1.set("aos_veddesc", "见开发采购需求");
            DynamicObjectCollection aosEntryentity2S = dyn.getDynamicObjectCollection("aos_entryentity2");
            aosEntryentity2S.clear();
            DynamicObject aosEntryentity2 = aosEntryentity2S.addNew();
            aosEntryentity2.set("aos_phtype", "白底");
            aosEntryentity2 = aosEntryentity2S.addNew();
            aosEntryentity2.set("aos_phtype", "实景");
            DynamicObjectCollection aosEntryentity3S = dyn.getDynamicObjectCollection("aos_entryentity3");
            aosEntryentity3S.clear();
            DynamicObject aosEntryentity3 = aosEntryentity3S.addNew();
            aosEntryentity3.set("aos_returnby", "摄影师");
            aosEntryentity3 = aosEntryentity3S.addNew();
            aosEntryentity3.set("aos_returnby", "开发");
            OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq", new DynamicObject[] {dyn},
                OperateOption.create());
            AosMktProgPhReqBill.submitForNew(dyn);
            FndHistory.Create(dyn, "提交", "新建");
        }
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        doOperate();
    }
}