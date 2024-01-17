package mkt.synciface;

import common.CommonDataSom;
import common.Cux_Common_Utl;
import common.fnd.FndHistory;
import common.scmQtyType;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.metadata.dynamicobject.DynamicObjectType;
import kd.bos.entity.EntityMetadataCache;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import mkt.common.MKTCom;
import mkt.progress.listing.AosMktListingReqBill;

import java.time.LocalDateTime;
import java.util.*;

/**
 * @author create by gk
 * @since 2022/12/26 16:07
 * @version 拍照需求表定时生成listing优化需求表-调度任务类
 */
public class AosMktCreateListingReqTask extends AbstractTask {
    public final static int TWO = 2;
    public final static int THREE = 3;
    public final static int FOUR = 4;

    /** 判断拍照需求表的状态 **/
    private static DynamicObject[] judgePhotoStatus() {
        QFilter filterPho = new QFilter("aos_photoflag", "=", "1");
        QFilter filterNew = new QFilter("aos_newitem", "=", "0");
        QFilter filterStatus = new QFilter("aos_status", "=", "已完成");
        QFilter filterReq = new QFilter("aos_req", "=", "0");
        // 不为手工关闭的拍照需求表
        QFilter filterManual = new QFilter("aos_manual_close", QCP.not_equals, true);
        QFilter[] qfs = new QFilter[] {filterPho, filterNew, filterStatus, filterReq, filterManual};
        return BusinessDataServiceHelper.load("aos_mkt_photoreq", "id,billno,aos_req", qfs);
    }

    /** 根据拍照需求表生成优化需求表 **/
    private static void createListReq(DynamicObject[] dycPhoto, DynamicObjectCollection aosSyncLogS) {
        for (DynamicObject dyPhoto : dycPhoto) {
            StringJoiner logRow = new StringJoiner(" ; ");
            if (judgeDesignReq(dyPhoto)) {
                // 查找该拍照需求表的详情
                DynamicObject dyPhototReq = BusinessDataServiceHelper.loadSingle(dyPhoto.get("id"), "aos_mkt_photoreq");
                logRow.add("billno: " + dyPhototReq.getString("billno"));
                if (dyPhototReq.get("aos_itemid") == null) {
                    logRow.add("物料为空剔除");
                    continue;
                }
                if (dyPhototReq.get("aos_ponumber") == null) {
                    logRow.add("合同号为空剔除");
                    continue;
                }
                String itemId = dyPhototReq.getDynamicObject("aos_itemid").getString("id");
                // 如果该物料已经生成过，则跳过，不生成
                String itemNumber = dyPhototReq.getDynamicObject("aos_itemid").getString("number");
                logRow.add("物料： " + itemNumber);
                // 合同号
                String aosPonumber = dyPhototReq.getString("aos_ponumber");
                logRow.add("合同号： " + aosPonumber);
                // 查找购销合同中的国别
                DynamicObjectCollection dycOrg = queryOrg(itemId, aosPonumber);
                // 记录已经创建的国别
                List<String> crateOrg = new ArrayList<>(dycOrg.size());
                for (DynamicObject dyPur : dycOrg) {
                    String aosOrg = dyPur.getString("aos_org");
                    logRow.add("国别： " + dyPur.get("orgNumber"));
                    if (crateOrg.contains(aosOrg)) {
                        continue;
                    }
                    if (queryNote(itemId, aosPonumber, aosOrg, logRow)) {
                        logRow.add("创建优化需求表");
                        createListReq(itemId, aosOrg, dyPhototReq, logRow);
                        crateOrg.add(aosOrg);
                    }
                }
                SaveServiceHelper.update(new DynamicObject[] {dyPhototReq});
            }
            if (logRow.toString().length() > 0) {
                MKTCom.Put_SyncLog(aosSyncLogS, logRow.toString());
            }
        }
    }

    /** 判断设计需求表是否结束 **/
    private static Boolean judgeDesignReq(DynamicObject dyPhoto) {
        QFilter filterBillno = new QFilter("aos_orignbill", "=", dyPhoto.getString("billno"));
        QFilter filterStatus = new QFilter("aos_status", "!=", "结束");
        return !QueryServiceHelper.exists("aos_mkt_designreq", new QFilter[] {filterBillno, filterStatus});
    }

    /** 查找备货单 合同的首批海外入库后，海外库存数量+5＜该合同的首批海外入库数量； **/
    private static boolean queryNote(Object itemid, Object poNumber, Object org, StringJoiner log) {
        QFilter filterItem = new QFilter("aos_stockentry.aos_sto_artno", "=", itemid);
        QFilter filterNo = new QFilter("aos_stockentry.aos_sto_contractno", "=", poNumber);
        QFilter filterOverSeas = new QFilter("aos_overseasflag", "=", "1");
        QFilter filterOrg = new QFilter("aos_soldtocountry", "=", org);
        QFilter[] qfs = new QFilter[] {filterItem, filterNo, filterOverSeas, filterOrg};
        StringJoiner str = new StringJoiner(",");
        str.add("aos_stockentry.aos_sto_shipmentcount aos_sto_shipmentcount");
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_preparationnote", str.toString(), qfs);
        // 首批海外入库数量
        int shipmentCount = 0;
        for (DynamicObject dy : dyc) {
            if (dy.get("aos_sto_shipmentcount") != null) {
                shipmentCount += dy.getInt("aos_sto_shipmentcount");
            }
        }
        log.add("海外入库： " + shipmentCount);
        ArrayList<String> arr = new ArrayList<>();
        arr.add(String.valueOf(itemid));
        Map<String, Integer> mapQty = CommonDataSom.getScmQty(scmQtyType.oversea, String.valueOf(org), arr);
        int overSeaQty = 5;
        overSeaQty += mapQty.getOrDefault(String.valueOf(itemid), 0);
        log.add("海外库存+5： " + overSeaQty);
        return overSeaQty < shipmentCount;
    }

    /** 根据合同号，查找国别 **/
    private static DynamicObjectCollection queryOrg(Object itemId, Object poNumber) {
        QFilter filterNo = new QFilter("billno", "=", poNumber);
        QFilter filterItem = new QFilter("aos_entryentity.aos_materiel", "=", itemId);
        QFilter[] qfs = new QFilter[] {filterNo, filterItem};
        String sel = "aos_entryentity.aos_salecountry aos_org,aos_entryentity.aos_salecountry.number orgNumber";
        return QueryServiceHelper.query("aos_purcontract", sel, qfs);
    }

    /** 生成优化需求表 **/
    private static void createListReq(Object item, Object orgId, DynamicObject dyPhoto, StringJoiner str) {
        // 获取物料
        DynamicObject dyMater = BusinessDataServiceHelper.loadSingle(item, "bd_material");
        DynamicObject dyDeveloper = dyMater.getDynamicObject("aos_developer");
        if (dyDeveloper == null) {
            str.add("开发为空无法生成");
            return;
        }
        DynamicObject dyReq = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_req");
        Calendar cal = Calendar.getInstance();
        dyReq.set("aos_requireby", dyDeveloper.getPkValue());
        dyReq.set("aos_user", dyDeveloper.getPkValue());
        dyReq.set("aos_status", "申请人");
        List<DynamicObject> mapList = Cux_Common_Utl.GetUserOrg(dyDeveloper.getPkValue());
        if (mapList != null) {
            if (mapList.size() >= THREE && mapList.get(TWO) != null) {
                dyReq.set("aos_organization1", mapList.get(2).get("id"));
            }
            if (mapList.size() >= FOUR && mapList.get(THREE) != null) {
                dyReq.set("aos_organization2", mapList.get(3).get("id"));
            }
        }
        // 根据开发获取组织
        String aosUnit = Cux_Common_Utl.GetUserOrgLong(dyDeveloper.getPkValue());
        dyReq.set("aos_unit", aosUnit);
        dyReq.set("aos_requiredate", cal.getTime());
        cal.add(Calendar.DATE, 1);
        dyReq.set("aos_demandate", cal.getTime());
        dyReq.set("aos_type", "四者一致");
        dyReq.set("aos_source", "资料变更");
        dyReq.set("aos_importance", "紧急");
        dyReq.set("aos_orgid", orgId);
        dyReq.set("aos_autoflag", true);
        // 明细行
        DynamicObjectCollection dycEntPhoto = dyPhoto.getDynamicObjectCollection("aos_entryentity");
        if (dycEntPhoto.size() < 1) {
            str.add("拍照明细行为空，无法生成");
            return;
        }
        DynamicObjectCollection dycEnt = dyReq.getDynamicObjectCollection("aos_entryentity");
        DynamicObject dyNewRow = dycEnt.addNew();
        dyNewRow.set("aos_itemid", item);
        dyNewRow.set("aos_requirepic", dycEntPhoto.get(1).get("aos_picdesc"));
        DynamicObjectCollection dyRowAttribute = dyNewRow.getDynamicObjectCollection("aos_attribute");
        DynamicObjectCollection aosAttributefrom = dycEntPhoto.get(1).getDynamicObjectCollection("aos_picattr");
        DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
        DynamicObject tempFile = null;
        for (DynamicObject d : aosAttributefrom) {
            tempFile = BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"), type);
            dyRowAttribute.addNew().set("fbasedataid", tempFile);
        }
        AosMktListingReqBill.setItemCate(dyReq);
        OperationResult result =
            SaveServiceHelper.saveOperate("aos_mkt_listing_req", new DynamicObject[] {dyReq}, OperateOption.create());
        if (result.getSuccessPkIds().size() > 0) {
            DynamicObject dy =
                BusinessDataServiceHelper.loadSingle(result.getSuccessPkIds().get(0), "aos_mkt_listing_req");
            FndHistory.Create(dy, "提交", "定时任务，拍照需求表生成优化需求表");
            new AosMktListingReqBill().aosSubmit(dy, "B");
        }
        dyPhoto.set("aos_req", true);
    }

    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        DynamicObject aosSyncLog = BusinessDataServiceHelper.newDynamicObject("aos_sync_log");
        aosSyncLog.set("aos_type_code", "拍照需求表定时生成listing优化需求表");
        aosSyncLog.set("aos_groupid", LocalDateTime.now());
        aosSyncLog.set("billstatus", "A");
        DynamicObjectCollection aosSyncLogS = aosSyncLog.getDynamicObjectCollection("aos_entryentity");
        // 拍照需求表
        DynamicObject[] dycPhotoReq = judgePhotoStatus();
        createListReq(dycPhotoReq, aosSyncLogS);
        SaveServiceHelper.save(new DynamicObject[] {aosSyncLog});
    }
}
