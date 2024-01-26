package mkt.synciface;

import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import mkt.common.MKTCom;
import mkt.progress.ProgressUtil;
import mkt.progress.iface.ItemInfoUtil;

import java.time.LocalDateTime;
import java.util.*;

import common.Cux_Common_Utl;
import common.fnd.FndGlobal;
import common.sal.util.SalUtil;

/**
 * @author create by gk
 * @since 2022/11/17 11:13
 * @version 同步出运日期(主要是未结束的拍照需求表和设计需求表)-调度任务类
 */
public class AosMktSyncShipmentTask extends AbstractTask {

    public final static String SYSTEM = Cux_Common_Utl.SYSTEM;

    public static void syncFirstDate() {
        // 刷新多人会审日期与操作人
        DynamicObject[] aosMktPhotoReqS = BusinessDataServiceHelper.load("aos_mkt_photoreq",
            "aos_firstindate,aos_salehelper,aos_orgid,aos_itemid,aos_user",
            new QFilter("aos_status", QCP.equals, "视频更新:多人会审").and("aos_sonflag", QCP.equals, true)
                .and("aos_firstindate", QCP.equals, null).toArray());
        for (DynamicObject aosMktPhotoReq : aosMktPhotoReqS) {
            Object org = aosMktPhotoReq.get("aos_orgid");
            if (FndGlobal.IsNull(org)) {
                continue;
            }
            String aosOrgid = aosMktPhotoReq.getDynamicObject("aos_orgid").getPkValue().toString();
            String aosItemid = aosMktPhotoReq.getDynamicObject("aos_itemid").getPkValue().toString();
            DynamicObject bdMaterial = QueryServiceHelper.queryOne("bd_material",
                "id,aos_contryentry.aos_firstindate aos_firstindate", new QFilter("id", QCP.equals, aosItemid)
                    .and("aos_contryentry.aos_nationality.id", QCP.equals, aosOrgid).toArray());
            if (FndGlobal.IsNotNull(bdMaterial)) {
                String category = (String)SalUtil.getCategoryByItemId(aosItemid).get("name");
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
                Object aosSalehelper = null;
                if (FndGlobal.IsNotNull(aosCategory1) && FndGlobal.IsNotNull(aosCategory2)) {
                    QFilter filterCategory1 = new QFilter("aos_category1", "=", aosCategory1);
                    QFilter filterCategory2 = new QFilter("aos_category2", "=", aosCategory2);
                    QFilter filterOrg = new QFilter("aos_orgid", "=", aosOrgid);
                    QFilter[] filtersCategory = new QFilter[] {filterCategory1, filterCategory2, filterOrg};
                    String selectStr = "aos_salehelper.id aos_salehelper";
                    DynamicObject aosMktProgorguser =
                        QueryServiceHelper.queryOne("aos_mkt_progorguser", selectStr, filtersCategory);
                    if (aosMktProgorguser != null) {
                        aosSalehelper = aosMktProgorguser.getString("aos_salehelper");
                    }
                }
                Date aosFirstindate = bdMaterial.getDate("aos_firstindate");
                if (FndGlobal.IsNull(aosFirstindate)) {
                    continue;
                }
                if (FndGlobal.IsNotNull(bdMaterial) && FndGlobal.IsNotNull(aosSalehelper)) {
                    aosMktPhotoReq.set("aos_user", aosSalehelper);
                    aosMktPhotoReq.set("aos_firstindate", aosFirstindate);
                } else {
                    aosMktPhotoReq.set("aos_user", SYSTEM);
                    aosMktPhotoReq.set("aos_firstindate", null);
                }
            }
        }
        SaveServiceHelper.update(aosMktPhotoReqS);
    }

    /** 同步拍照需求表 **/
    public static void syncPhotoReq() {
        DynamicObject aosSyncLog = BusinessDataServiceHelper.newDynamicObject("aos_sync_log");
        aosSyncLog.set("aos_type_code", "同步出运日期");
        aosSyncLog.set("aos_groupid", LocalDateTime.now());
        aosSyncLog.set("billstatus", "A");
        DynamicObjectCollection aosSyncLogS = aosSyncLog.getDynamicObjectCollection("aos_entryentity");
        QFilter filterStatus = new QFilter("aos_status", "!=", "已完成");
        StringJoiner str = new StringJoiner(",");
        str.add("id");
        str.add("billno");
        str.add("aos_itemid");
        // 申请日期
        str.add("aos_requiredate");
        // 合同号
        str.add("aos_ponumber");
        // 新品
        str.add("aos_newitem");
        // 新供应商
        str.add("aos_newvendor");
        // 出运日期
        str.add("aos_shipdate");
        // 紧急提醒
        str.add("aos_urgent");
        // 最早入库日期
        str.add("aos_overseasdate");
        DynamicObject[] dycReq =
            BusinessDataServiceHelper.load("aos_mkt_photoreq", str.toString(), new QFilter[] {filterStatus});
        // 获取出运日期
        List<DynamicObject> listSave = new ArrayList<>(5000);
        for (DynamicObject dyReq : dycReq) {
            StringJoiner logRow = new StringJoiner(";");
            logRow.add(dyReq.getString("billno"));
            logRow.add(dyReq.getBoolean("aos_newitem") + "  " + dyReq.getBoolean("aos_newvendor") + "   ");
            // 出运日期
            Date aosShipdate = dyReq.getDate("aos_shipdate");
            // 判断是否为全球新品
            boolean overSeaNew = true;
            if (dyReq.get("aos_itemid") != null) {
                String itemId = dyReq.getDynamicObject("aos_itemid").getString("id");
                overSeaNew = QueryServiceHelper.exists("bd_material",
                    new QFilter("aos_contryentry.aos_contryentrystatus", QCP.not_equals, "A")
                        .and("id", QCP.equals, itemId).toArray());
            }
            boolean cond = (dyReq.getBoolean("aos_newitem") || dyReq.getBoolean("aos_newvendor")
                || (FndGlobal.IsNull(aosShipdate) && !overSeaNew));
            if (cond) {
                if (dyReq.get("aos_itemid") != null) {
                    String item = dyReq.getDynamicObject("aos_itemid").getString("id");
                    logRow.add(dyReq.getDynamicObject("aos_itemid").getString("number") + "   ");
                    Object aosPonumber = dyReq.get("aos_ponumber");
                    logRow.add(aosPonumber + " ");
                    Date shipDate = null, overseasdate = null;
                    DynamicObjectCollection dyc = ItemInfoUtil.getShipDate(item, aosPonumber, "aos_overseasdate");
                    if (dyc.size() > 0) {
                        shipDate = dyc.get(0).getDate("aos_shipmentdate");
                        overseasdate = dyc.get(0).getDate("aos_overseasdate");
                    }
                    logRow.add("shipDate: " + shipDate + "   ");
                    logRow.add("overSeasDate: " + overseasdate + "   ");
                    dyReq.set("aos_shipdate", shipDate);
                    dyReq.set("aos_overseasdate", overseasdate);
                    dyReq.set("aos_urgent", ProgressUtil.JudgeUrgency(new Date(), shipDate));
                    listSave.add(dyReq);
                    // 同步样品入库通知单
                    if (listSave.size() > 4500) {
                        int size = listSave.size();
                        SaveServiceHelper.update(listSave.toArray(new DynamicObject[size]));
                        listSave.clear();
                    }
                    syncRcv(dyReq.getString("id"), shipDate);
                    syncDesignreq(dyReq.getString("id"), shipDate);
                    syncPhotoList(dyReq.getString("billno"), shipDate);
                }
            }
            MKTCom.Put_SyncLog(aosSyncLogS, logRow.toString());
        }
        SaveServiceHelper.save(new DynamicObject[] {aosSyncLog});
        int size = listSave.size();
        SaveServiceHelper.update(listSave.toArray(new DynamicObject[size]));
        listSave.clear();
    }

    /** 同步样品入库单 **/
    private static void syncRcv(String fid, Date shipDate) {
        QFilter filterId = new QFilter("aos_sourceid", "=", fid);
        StringJoiner str = new StringJoiner(",");
        str.add("id");
        str.add("aos_shipdate");
        DynamicObject[] dyc = BusinessDataServiceHelper.load("aos_mkt_rcv", str.toString(), new QFilter[] {filterId});
        for (DynamicObject dy : dyc) {
            dy.set("aos_shipdate", shipDate);
        }
        SaveServiceHelper.update(dyc);
    }

    /** 同步设计需求表 **/
    public static void syncDesignreq(String fid, Date shipDate) {
        QFilter filterSource = new QFilter("aos_sourceid", "=", fid);
        StringJoiner str = new StringJoiner(",");
        str.add("id");
        str.add("aos_shipdate");
        str.add("aos_entryentity.Seq");
        str.add("aos_entryentity.aos_itemid");
        DynamicObject[] dycDesign =
            BusinessDataServiceHelper.load("aos_mkt_designreq", str.toString(), new QFilter[] {filterSource});
        List<DynamicObject> listSave = new ArrayList<>(5000);
        for (DynamicObject dy : dycDesign) {
            if (dy.getDynamicObjectCollection("aos_entryentity").size() > 0) {
                Object item = dy.getDynamicObjectCollection("aos_entryentity").get(0).get("aos_itemid");
                if (item != null) {
                    dy.set("aos_shipdate", shipDate);
                    listSave.add(dy);
                    if (listSave.size() > 4500) {
                        int size = listSave.size();
                        SaveServiceHelper.update(listSave.toArray(new DynamicObject[size]));
                        listSave.clear();
                    }
                }
            }
        }
        int size = listSave.size();
        SaveServiceHelper.update(listSave.toArray(new DynamicObject[size]));
        listSave.clear();
    }

    /** 同步拍照任务清单 **/
    public static void syncPhotoList(String billno, Date shipDate) {
        QFilter filterBill = new QFilter("billno", "=", billno);
        StringJoiner str = new StringJoiner(",");
        str.add("id");
        str.add("aos_shipdate");
        DynamicObject[] dyc =
            BusinessDataServiceHelper.load("aos_mkt_photolist", str.toString(), new QFilter[] {filterBill});
        for (DynamicObject dy : dyc) {
            dy.set("aos_shipdate", shipDate);
        }
        SaveServiceHelper.update(dyc);
    }

    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        syncPhotoReq();
        syncFirstDate();
    }
}
