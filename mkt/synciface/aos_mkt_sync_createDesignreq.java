package mkt.synciface;

import common.Cux_Common_Utl;
import common.fnd.FndHistory;
import common.sal.util.SalUtil;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.exception.ErrorCode;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import mkt.common.MKTCom;
import mkt.common.MKTS3PIC;
import mkt.progress.ProgressUtil;
import mkt.progress.iface.iteminfo;
import sal.sche.aos_sal_sche_pub.aos_sal_sche_pvt;
import sal.sche.aos_sal_sche_pub.scmQtyType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author create by gk
 * @date 2022/12/5 14:36
 * @action  每周一定时自动生成综合帖任务的设计需求表；
 */
public class aos_mkt_sync_createDesignreq extends AbstractTask {
    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        DynamicObject aos_sync_log = BusinessDataServiceHelper.newDynamicObject("aos_sync_log");
        aos_sync_log.set("aos_type_code", "生成综合帖任务的设计需求表");
        aos_sync_log.set("aos_groupid", LocalDateTime.now());
        aos_sync_log.set("billstatus", "A");
        DynamicObjectCollection aos_sync_logS = aos_sync_log.getDynamicObjectCollection("aos_entryentity");
        Sync(aos_sync_logS);
        SaveServiceHelper.save(new DynamicObject[]{aos_sync_log});
    }
    public static void Sync(DynamicObjectCollection aos_sync_logS){
        DynamicObjectCollection dyc_shipItem = queryShipItem();
        List<String> createOrgItem = findCreateOrgItem();   //已经生成过的
        for (DynamicObject dy_item : dyc_shipItem) {
            StringJoiner logRow = new StringJoiner(" ; ");
            logRow.add(dy_item.getString("orgNumber"));
            logRow.add(dy_item.getString("itemNumber"));
            List<DynamicObject> list_sku = new ArrayList<>();
            if (JudgeItem(dy_item,list_sku,logRow) && JudgeDesign(dy_item,logRow)){
                logRow.add("生成单据");
//                createEntity(dy_item,list_sku);
                String key = dy_item.getString("aos_org")+"/"+dy_item.getString("aos_item");
                if (createOrgItem.contains(key)){
                    logRow.add("已经生成过了");
                    continue;
                }
                createMultipleEntity(dy_item);
            }
            MKTCom.Put_SyncLog(aos_sync_logS, logRow.toString() );
        }
    }
    /**①查询备货单中，过去7天内出运的SKU**/
    private static DynamicObjectCollection queryShipItem(){
        LocalDate now = LocalDate.now();
        QFilter filter_from = new QFilter("aos_shipmentdate",">=",now.minusDays(7).toString());
        QFilter filter_to = new QFilter("aos_shipmentdate","<",now.toString());
        QFilter [] qfs = new QFilter[]{filter_from,filter_to};
        StringJoiner str = new StringJoiner(",");
        str.add("aos_soldtocountry aos_org");
        str.add("aos_soldtocountry.number orgNumber");
        str.add("aos_stockentry.aos_sto_artno aos_item");
        str.add("aos_stockentry.aos_sto_artno.number itemNumber");
        return QueryServiceHelper.query("aos_preparationnote", str.toString(), qfs);
    }
    /** 判断对应产品号下某国别有2个以上SKU（剔除终止、异常；且供应链小于10） **/
    private static boolean JudgeItem(DynamicObject dy_item,List<DynamicObject> list_other,StringJoiner str){
        String aos_org = dy_item.getString("aos_org");
        String aos_item = dy_item.getString("aos_item");
        //查找产品号
        QFilter filter_id = new QFilter("id","=",aos_item);
        DynamicObject dy_no = QueryServiceHelper.queryOne("bd_material", "aos_productno", new QFilter[]{filter_id});
        if (dy_no==null || dy_no.get("aos_productno")==null){
            str.add("产品号为空剔除");
            return false;
        }
        String aos_productno = dy_no.getString("aos_productno");    //产品号
        QFilter filter_no = new QFilter("aos_productno","=",aos_productno);
        QFilter filter_org = new QFilter("aos_contryentry.aos_nationality","=",aos_org);
        List<String> list_status = Arrays.asList("C", "D");
        QFilter filter_status = new QFilter("aos_contryentry.aos_contryentrystatus",QFilter.not_in,list_status);
        //同一产品号的物料
        List<QFilter> materialFilter = SalUtil.get_MaterialFilter();
        materialFilter.add(filter_no);
        materialFilter.add(filter_status);
        materialFilter.add(filter_org);
        QFilter[] qfs = materialFilter.toArray(new QFilter[materialFilter.size()]);
        DynamicObjectCollection dyc_otherItem = QueryServiceHelper.query("bd_material", "id,number", qfs);
        if (dyc_otherItem.size()<2){
            str.add("同产品号物料小于2剔除");
            return false;
        }

        //查找供应链数量
        ArrayList <String> arr_itemid = (ArrayList<String>) dyc_otherItem.stream()
                .map(dy -> dy.getString("id")).distinct().collect(Collectors.toList());
        Map<String, Integer> map_domesticAndInPro = aos_sal_sche_pvt.getScmQty(scmQtyType.domesticAndInProcess, aos_org, arr_itemid);
        Map<String, Integer> map_oversea = aos_sal_sche_pvt.getScmQty(scmQtyType.oversea, aos_org, arr_itemid);
        Map<String, Integer> map_onhand = aos_sal_sche_pvt.getScmQty(scmQtyType.onHand, aos_org, arr_itemid);
        for (DynamicObject dy_productNo : dyc_otherItem) {
            String itemid = dy_productNo.getString("id");
            int scmQty = 0;
            scmQty += map_domesticAndInPro.getOrDefault(itemid,0);
            scmQty += map_oversea.getOrDefault(itemid,0);
            scmQty += map_onhand.getOrDefault(itemid,0);
            if (scmQty >= 10){
                list_other.add(dy_productNo);
            }
        }
        if (list_other.size()>2)
            return true;
        else
        {
            str.add("同产品号供应链数量合格物料小于2剔除");
            return false;
        }

    }
    /** SKU对设计需求表，存在已完成的流程**/
    private static boolean JudgeDesign(DynamicObject dy_item,StringJoiner str){
        String aos_item = dy_item.getString("aos_item");
        QFilter filter_item = new QFilter("aos_entryentity.aos_itemid","=",aos_item);
        QFilter filter_status = new QFilter("aos_status","=","结束");
        boolean exists = QueryServiceHelper.exists("aos_mkt_designreq", new QFilter[]{filter_item, filter_status});
        str.add("设计需求表存在： "+exists);
        return exists;
    }

    /** 生成设计需求表**/
    private static void createEntity(DynamicObject dy_item,List<DynamicObject> list_other){
        String aos_org = dy_item.getString("aos_org");
        String aos_item = dy_item.getString("aos_item");
        DynamicObject dy_designReq = BusinessDataServiceHelper.newDynamicObject("aos_mkt_designreq");
        Date date_now = new Date();
        dy_designReq.set("aos_requireby", Cux_Common_Utl.SYSTEM);
        dy_designReq.set("aos_status","设计");
        dy_designReq.set("aos_requiredate",date_now);   //申请日期
        dy_designReq.set("aos_orgid",aos_org);

        dy_designReq.set("aos_type","其他");
        dy_designReq.set("aos_source","综合帖");
        //设计师
        String category = MKTCom.getItemCateNameZH(aos_item );
        String[] category_group = category.split(",");
        String[] fields = new String[] { "aos_designeror", "aos_3d", "aos_eng" };
        DynamicObject dy_design = ProgressUtil.findDesignerByType(aos_org, category_group[0], category_group[1], "其他",fields);
        Object aos_design = dy_design.get("aos_designer");
        dy_designReq.set("aos_designer",aos_design);
        dy_designReq.set("aos_user",aos_design);
        dy_designReq.set("aos_dm", dy_design.get("aos_designeror"));
        dy_designReq.set("aos_3der", dy_design.get("aos_3d"));
        //组织
        List<DynamicObject> MapList = Cux_Common_Utl.GetUserOrg(aos_design);
        if (MapList != null) {
            if (MapList.get(2) != null)
                dy_designReq.set("aos_organization1", MapList.get(2).get("id"));
            if (MapList.get(3) != null)
                dy_designReq.set("aos_organization2", MapList.get(3).get("id"));
        }

        //明细行
        DynamicObjectCollection dyc_designEnt = dy_designReq.getDynamicObjectCollection("aos_entryentity");
        DynamicObject dy_newRow = dyc_designEnt.addNew();
        dy_newRow.set("aos_itemid",aos_item);
        dy_newRow.set("aos_is_saleout",ProgressUtil.Is_saleout(aos_item));
        dy_newRow.set("aos_desreq",fillDesreq(list_other));

        //子单据体
        DynamicObject dy_subRow = dy_newRow.getDynamicObjectCollection("aos_subentryentity").addNew();

        String aos_contrybrandStr = "";
        String aos_orgtext = "";
        DynamicObject bd_material = BusinessDataServiceHelper.loadSingle(aos_item, "bd_material");
        DynamicObjectCollection aos_contryentryS = bd_material.getDynamicObjectCollection("aos_contryentry");
        // 获取所有国家品牌 字符串拼接 终止
        Set<String> set_bra = new HashSet<>();
        for (DynamicObject aos_contryentry : aos_contryentryS) {
            DynamicObject aos_nationality = aos_contryentry.getDynamicObject("aos_nationality");
            String aos_nationalitynumber = aos_nationality.getString("number");
            if ("IE".equals(aos_nationalitynumber))
                continue;
            Object org_id = aos_nationality.get("id"); // ItemId
            int OsQty = iteminfo.GetItemOsQty(org_id, aos_item);
            int SafeQty = iteminfo.GetSafeQty(org_id);
            int onQty = sal.sche.aos_sal_sche_pub.aos_sal_sche_pvt
                    .get_on_hand_qty(Long.valueOf(org_id.toString()),Long.valueOf(aos_item));
            OsQty += onQty;
            // 安全库存 海外库存
            if ("C".equals(aos_contryentry.getString("aos_contryentrystatus")) && OsQty < SafeQty)
                continue;

            aos_orgtext = aos_orgtext + aos_nationalitynumber + ";";

            Object obj = aos_contryentry.getDynamicObject("aos_contrybrand");
            if (obj == null)
                continue;
            String value = aos_nationalitynumber + "~"
                    + aos_contryentry.getDynamicObject("aos_contrybrand").getString("number");
//            if (!aos_contrybrandStr.contains(value))
//                aos_contrybrandStr = aos_contrybrandStr + value + ";";

            String bra = aos_contryentry.getDynamicObject("aos_contrybrand").getString("number");
            if(bra != null)
                set_bra.add(bra);
            if(set_bra.size() > 1){
                if (!aos_contrybrandStr.contains(value))
                    aos_contrybrandStr = aos_contrybrandStr + value + ";";
            }
            else if(set_bra.size() == 1){
                aos_contrybrandStr = bra;
            }

        }
        String item_number = bd_material.getString("number");
        String url = "https://clss.s3.amazonaws.com/" + item_number + ".jpg";// 图片字段
        String aos_productno = bd_material.getString("aos_productno");
        String aos_itemname = bd_material.getString("name");
        // 获取同产品号物料
        QFilter filter_productno = new QFilter("aos_productno", QCP.equals, aos_productno);
        QFilter[] filters = new QFilter[] { filter_productno };
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
        dy_subRow.set("aos_sub_item", aos_item);
        dy_subRow.set("aos_segment3", aos_productno);
        dy_subRow.set("aos_itemname", aos_itemname);
        dy_subRow.set("aos_brand", aos_contrybrandStr);
        dy_subRow.set("aos_pic", url);
        dy_subRow.set("aos_developer", bd_material.get("aos_developer"));// 开发
        dy_subRow.set("aos_seting1", bd_material.get("aos_seting_cn"));
        dy_subRow.set("aos_seting2", bd_material.get("aos_seting_en"));
        dy_subRow.set("aos_spec", bd_material.get("aos_specification_cn"));
        dy_subRow.set("aos_url", MKTS3PIC.GetItemPicture(item_number));
        dy_subRow.set("aos_broitem", aos_broitem);
        dy_subRow.set("aos_orgtext", aos_orgtext);
        dy_subRow.set("aos_sellingpoint", bd_material.get("aos_sellingpoint"));
        StringJoiner productStyle = new StringJoiner(";");
        DynamicObjectCollection item = bd_material.getDynamicObjectCollection("aos_productstyle_new");
        if(item.size() != 0){
            List<Object> id = item.stream().map(e -> e.getDynamicObject("fbasedataid").getPkValue()).collect(Collectors.toList());
            for(Object a : id) {
                DynamicObject dysty = QueryServiceHelper.queryOne("aos_product_style","id,name",
                        new QFilter("id", QCP.equals,a).toArray());
                String styname = dysty.getString("name");
                productStyle.add(styname);
            }
            dy_subRow.set("aos_productstyle_new", productStyle.toString());
        }
        dy_subRow.set("aos_shootscenes", bd_material.getString("aos_shootscenes"));

        OperationResult result = SaveServiceHelper.saveOperate("aos_mkt_designreq", new DynamicObject[]{dy_designReq}, OperateOption.create());
        List<Object> successPkIds = result.getSuccessPkIds();
        if (successPkIds.size()>0){
            DynamicObject dy_temp = BusinessDataServiceHelper.loadSingle(successPkIds.get(0), "aos_mkt_designreq");
            FndHistory.Create(dy_temp, "提交", "自动帖生成");
            FndHistory.Create(dy_temp, "提交", "设计");
        }
    }
    /** 生成综合贴国别物料清单**/
    private static void createMultipleEntity(DynamicObject dy_item){
        List<String> createOrgItem = findCreateOrgItem();
        DynamicObject dy_multiple = BusinessDataServiceHelper.newDynamicObject("aos_mkt_multiple");
        String orgNumber = dy_item.getString("orgNumber");
        String itemNumber = dy_item.getString("itemNumber");
        dy_multiple.set("aos_org",dy_item.getString("aos_org"));
        dy_multiple.set("aos_sku",dy_item.getString("aos_item"));
        dy_multiple.set("billstatus","A");
        OperationResult result = SaveServiceHelper.saveOperate("aos_mkt_multiple", new DynamicObject[]{dy_multiple}, OperateOption.create());
        if (!result.isSuccess()){
            throw new KDException(new ErrorCode(" MMS综合贴清单保存失败： "+orgNumber+"  "+itemNumber,
                    result.getAllErrorOrValidateInfo().get(0).getMessage()));
        }
    }
    private static String fillDesreq(List<DynamicObject> list_other){
        StringBuilder builder = new StringBuilder();
        builder.append("综合帖需求，本国别下单SKU：");
        StringJoiner str = new StringJoiner("、");
        for (int i=0;i<=2;i++){
            str.add(list_other.get(i).getString("number"));
        }
        builder.append(str.toString());
        builder.append(";");
        return builder.toString();
    }
    /** 查找已经生成的综合贴的国别物料 **/
    private static List<String> findCreateOrgItem(){
        return QueryServiceHelper.query("aos_mkt_multiple", "aos_org,aos_sku", null)
                .stream()
                .map(dy -> dy.getString("aos_org") + "/" + dy.getString("aos_sku"))
                .distinct()
                .collect(Collectors.toList());
    }
}
