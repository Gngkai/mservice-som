package mkt.synciface;

import common.CommonDataSom;
import common.Cux_Common_Utl;
import common.fnd.FndHistory;
import common.scmQtyType;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
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
import mkt.progress.listing.aos_mkt_listingreq_bill;

import java.time.LocalDateTime;
import java.util.*;

/**
 * @author create by gk
 * @date 2022/12/26 16:07
 * @action  拍照需求表定时生成listing优化需求表
 */
public class aos_mkt_create_listingReq extends AbstractTask {
    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        DynamicObject aos_sync_log = BusinessDataServiceHelper.newDynamicObject("aos_sync_log");
        aos_sync_log.set("aos_type_code", "拍照需求表定时生成listing优化需求表");
        aos_sync_log.set("aos_groupid", LocalDateTime.now());
        aos_sync_log.set("billstatus", "A");
        DynamicObjectCollection aos_sync_logS = aos_sync_log.getDynamicObjectCollection("aos_entryentity");
        //拍照需求表
        DynamicObject[] dyc_photoReq = JudgePhotoStatus();
        createListReq(dyc_photoReq,aos_sync_logS);
        SaveServiceHelper.save(new DynamicObject[]{aos_sync_log});
    }
    /** 判断拍照需求表的状态 **/
    private static DynamicObject[] JudgePhotoStatus(){
        QFilter filter_pho = new QFilter("aos_photoflag","=","1");
        QFilter filter_new = new QFilter("aos_newitem","=","0");
        QFilter filter_status = new QFilter("aos_status","=","已完成");
        QFilter filter_req = new QFilter("aos_req","=","0");
        // 不为手工关闭的拍照需求表
        QFilter filter_manual = new QFilter("aos_manual_close", QCP.not_equals,true);
        QFilter [] qfs = new QFilter[]{filter_pho,filter_new,filter_status,filter_req,filter_manual};
        DynamicObject[] dy_photo = BusinessDataServiceHelper.load("aos_mkt_photoreq", "id,billno,aos_req", qfs);
        return dy_photo;
    }
    /** 根据拍照需求表生成优化需求表 **/
    private static void createListReq(DynamicObject [] dyc_photo,DynamicObjectCollection aos_sync_logS){
        for (DynamicObject dy_photo : dyc_photo) {
            StringJoiner logRow = new StringJoiner(" ; ");
            if (JudgeDesignReq(dy_photo)){
                //查找该拍照需求表的详情
                DynamicObject dy_phototReq = BusinessDataServiceHelper.loadSingle(dy_photo.get("id"), "aos_mkt_photoreq");
                logRow.add("billno: "+dy_phototReq.getString("billno"));
                if (dy_phototReq.get("aos_itemid")==null  ){
                    logRow.add("物料为空剔除");
                    continue;
                }
                if (dy_phototReq.get("aos_ponumber")==null){
                    logRow.add("合同号为空剔除");
                    continue;
                }

                String itemID = dy_phototReq.getDynamicObject("aos_itemid").getString("id");
                //如果该物料已经生成过，则跳过，不生成

                String itemNumber = dy_phototReq.getDynamicObject("aos_itemid").getString("number");
                logRow.add("物料： "+itemNumber);
                String aos_ponumber = dy_phototReq.getString("aos_ponumber");    //合同号
                logRow.add("合同号： "+aos_ponumber);
                //查找购销合同中的国别
                DynamicObjectCollection dyc_org = QueryOrg(itemID, aos_ponumber);
                List<String> crateOrg= new ArrayList<>(dyc_org.size());  //记录已经创建的国别
                for (DynamicObject dy_pur : dyc_org) {
                    String aos_org = dy_pur.getString("aos_org");
                    logRow.add("国别： "+dy_pur.get("orgNumber"));
                    if (crateOrg.contains(aos_org)){
                        continue;
                    }
                    if (QueryNote(itemID,aos_ponumber,aos_org,logRow)){
                        logRow.add("创建优化需求表");
                        createListReq(itemID,aos_org,dy_phototReq,logRow);
                        crateOrg.add(aos_org);
                    }
                }
                SaveServiceHelper.update(new DynamicObject[]{dy_phototReq});
            }
            if (logRow.toString().length()>0) {
                MKTCom.Put_SyncLog(aos_sync_logS, logRow.toString());
            }
        }
    }
    /** 判断设计需求表是否结束**/
    private static Boolean JudgeDesignReq(DynamicObject dy_photo){
        QFilter filter_billno = new QFilter("aos_orignbill","=",dy_photo.getString("billno"));
        QFilter filter_status = new QFilter("aos_status","!=","结束");
        return !QueryServiceHelper.exists("aos_mkt_designreq", new QFilter[]{filter_billno, filter_status});
    }



    /** 查找备货单  合同的首批海外入库后，海外库存数量+5＜该合同的首批海外入库数量；**/
    private static boolean QueryNote (Object itemid,Object poNumber,Object org,StringJoiner log){
        QFilter filter_item = new QFilter("aos_stockentry.aos_sto_artno","=",itemid);
        QFilter filter_no = new QFilter("aos_stockentry.aos_sto_contractno","=",poNumber);
        QFilter filter_overSeas = new QFilter("aos_overseasflag","=","1");
        QFilter filter_org = new QFilter("aos_soldtocountry","=",org);
        QFilter [] qfs = new QFilter[]{filter_item,filter_no,filter_overSeas,filter_org};
        StringJoiner str = new StringJoiner(",");
        str.add("aos_stockentry.aos_sto_shipmentcount aos_sto_shipmentcount");
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_preparationnote", str.toString(), qfs);
        int shipmentCount = 0; //首批海外入库数量
        for (DynamicObject dy : dyc) {
            if (dy.get("aos_sto_shipmentcount")!=null)
                shipmentCount += dy.getInt("aos_sto_shipmentcount");
        }
        log.add("海外入库： "+shipmentCount);
        ArrayList<String> arr = new ArrayList<>();
        arr.add(String.valueOf(itemid));
        Map<String, Integer> map_qty = CommonDataSom.getScmQty(scmQtyType.oversea, String.valueOf(org), arr);
        int overSeaQty = 5;
        overSeaQty += map_qty.getOrDefault(String.valueOf(itemid),0);
        log.add("海外库存+5： "+overSeaQty);
        return overSeaQty < shipmentCount;
    }
    /** 根据合同号，查找国别**/
    private static DynamicObjectCollection QueryOrg (Object itemID,Object pnoumber){
        QFilter filter_no = new QFilter("billno","=",pnoumber);
        QFilter filter_item = new QFilter("aos_entryentity.aos_materiel","=",itemID);
        QFilter [] qfs = new QFilter[]{filter_no,filter_item};
        String sel = "aos_entryentity.aos_salecountry aos_org,aos_entryentity.aos_salecountry.number orgNumber";
        return QueryServiceHelper.query("aos_purcontract", sel, qfs);
    }
    /** 生成优化需求表**/
    private static void createListReq(Object item,Object orgID,DynamicObject dy_photo,StringJoiner str){
        //获取物料
        DynamicObject dy_mater = BusinessDataServiceHelper.loadSingle(item, "bd_material");
        DynamicObject dy_developer = dy_mater.getDynamicObject("aos_developer");    //开发
        if (dy_developer==null){
            str.add("开发为空无法生成");
            return;
        }
        DynamicObject dy_req = BusinessDataServiceHelper.newDynamicObject("aos_mkt_listing_req");
        Calendar cal = Calendar.getInstance();
        dy_req.set("aos_requireby",dy_developer.getPkValue());
        dy_req.set("aos_user",dy_developer.getPkValue());
        dy_req.set("aos_status","申请人");
        List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(dy_developer.getPkValue());
        if (MapList != null) {
            if (MapList.size() >= 3 && MapList.get(2) != null)
                dy_req.set("aos_organization1", MapList.get(2).get("id"));
            if (MapList.size() >= 4 && MapList.get(3) != null)
               dy_req.set("aos_organization2", MapList.get(3).get("id"));
        }
        // 根据开发获取组织
        String aos_unit = Cux_Common_Utl.GetUserOrgLong(dy_developer.getPkValue());
        dy_req.set("aos_unit", aos_unit);
        dy_req.set("aos_requiredate",cal.getTime());
        cal.add(Calendar.DATE,1);
        dy_req.set("aos_demandate",cal.getTime());
        dy_req.set("aos_type","四者一致");
        dy_req.set("aos_source","资料变更");
        dy_req.set("aos_importance","紧急");
        dy_req.set("aos_orgid",orgID);
        dy_req.set("aos_autoflag",true);

        //明细行
        DynamicObjectCollection dyc_entPhoto = dy_photo.getDynamicObjectCollection("aos_entryentity");
        if (dyc_entPhoto.size()<1) {
            str.add("拍照明细行为空，无法生成");
            return;
        }
        DynamicObjectCollection dyc_ent = dy_req.getDynamicObjectCollection("aos_entryentity");
        DynamicObject dy_newRow = dyc_ent.addNew();
        dy_newRow.set("aos_itemid",item);
        dy_newRow.set("aos_requirepic",dyc_entPhoto.get(1).get("aos_picdesc"));
        DynamicObjectCollection dy_rowAttribute = dy_newRow.getDynamicObjectCollection("aos_attribute");
        DynamicObjectCollection aos_attributefrom = dyc_entPhoto.get(1).getDynamicObjectCollection("aos_picattr");
        DynamicObjectType type = EntityMetadataCache.getDataEntityType("bd_attachment");
        DynamicObject tempFile = null;
        for (DynamicObject d : aos_attributefrom) {
            tempFile = BusinessDataServiceHelper.loadSingle(d.getDynamicObject("fbasedataid").get("id"), type);
            dy_rowAttribute.addNew().set("fbasedataid", tempFile);
        }
        aos_mkt_listingreq_bill.setItemCate(dy_req);
        OperationResult result = SaveServiceHelper.saveOperate("aos_mkt_listing_req", new DynamicObject[]{dy_req}, OperateOption.create());
        if (result.getSuccessPkIds().size()>0){
            DynamicObject dy = BusinessDataServiceHelper.loadSingle(result.getSuccessPkIds().get(0), "aos_mkt_listing_req");
            FndHistory.Create(dy, "提交", "定时任务，拍照需求表生成优化需求表");
            new aos_mkt_listingreq_bill().aos_submit(dy,"B");
        }
        dy_photo.set("aos_req",true);
    }
}
