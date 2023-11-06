package mkt.synciface;

import common.Cux_Common_Utl;
import common.fnd.FndHistory;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import common.sal.util.QFBuilder;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author create by gk
 * @date 2022/11/22 16:32
 * @action  拍照功能图制作状态下的设计需求表，修改状态为设计，并且回写完成日期
 */
public class aos_mkt_sync_designreqStatus extends AbstractTask {
    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        SyncDesignreq(map);
    }
    /** 同步 **/
   public static void SyncDesignreq(Map<String,Object> map_parameter){
        //查询设计需求表id
        DynamicObject[] dyc_design = QueryDesignreqId();
       //获取物料的产品号
        Map<String, String> map_itemToNo = QueryProductNo(dyc_design);
        //获取功能需求表中 第一行ZH不为空的产品号
        List<String> list_photoNo = QueryFuncreq(map_itemToNo);
        //获取设计需求表的来源单 来源单据体id对来源单据
        Map<String, DynamicObject> map_Source = QuerySourceEntity(dyc_design);
        //执行修改
        UpdateEntity(dyc_design,map_itemToNo,list_photoNo,map_Source);
    }
    /** 查询 拍照功能图制作状态的设计需求表单据  **/
    private static DynamicObject[] QueryDesignreqId (){
        QFilter filter_status = new QFilter("aos_status","=","拍照功能图制作");
        QFilter filter_source = new QFilter("aos_sourcetype","=","PHOTO");
        StringJoiner select = new StringJoiner(",");
        select.add("id");
        select.add("billno");
        select.add("aos_funcpicdate");
        select.add("aos_status");
        select.add("aos_sourceid");
        select.add("aos_entryentity.aos_itemid");
        select.add("aos_requiredate");
        return BusinessDataServiceHelper.load("aos_mkt_designreq", select.toString(), new QFilter[]{filter_source, filter_status});
    }
    /** 查询物料的产品号 **/
    private static Map<String,String> QueryProductNo(DynamicObject [] dyc_dsign){
        //获取物料id
        List<Object> list_itemid = Arrays.stream(dyc_dsign)
                .map(dy -> dy.getDynamicObjectCollection("aos_entryentity").get(0).getDynamicObject("aos_itemid").getPkValue())
                .distinct()
                .collect(Collectors.toList());
        //查找物料的产品号
        QFilter filter_id = new QFilter("id",QFilter.in,list_itemid);
        String select = "id,aos_productno";
        return QueryServiceHelper.query("bd_material", select, new QFilter[]{filter_id})
                .stream()
                .collect(Collectors.toMap(
                        dy -> dy.getString("id"),
                        dy -> dy.getString("aos_productno"),
                        (key1, key2) -> key1));
    }
    /** 查询 产品号对应的功能图需求表 **/
    private static List<String> QueryFuncreq (Map<String, String> map_itemToNo){
        QFBuilder builder = new QFBuilder("aos_segment3",QFilter.in,map_itemToNo.values());
        builder.add("aos_entity1.aos_lan1","=","中文");
        StringJoiner str = new StringJoiner(",");
        str.add("id");
        str.add("aos_segment3");
        str.add("aos_entity1.aos_value1 aos_value1");
        str.add("aos_entity1.aos_content1 aos_content1");
        return QueryServiceHelper.query("aos_mkt_functreq", str.toString(), builder.toArray())
                .stream()
                .filter(dy -> (!Cux_Common_Utl.IsNull(dy.get("aos_value1"))) || (!Cux_Common_Utl.IsNull(dy.get("aos_content1"))))
                .map(dy -> dy.getString("aos_segment3"))
                .distinct()
                .collect(Collectors.toList());
    }
    /** 查询 来源的拍照需求表**/
    private static Map<String, DynamicObject> QuerySourceEntity (DynamicObject [] dyc_design){
        //获取来源单号
        Map<String, String> map_designIdToSourceId = Arrays.stream(dyc_design)
                .collect(Collectors.toMap(
                        dy -> dy.getString("id"),
                        dy -> dy.getString("aos_sourceid"),
                        (key1, key2) -> key1
                ));
        //查找来源单
        QFilter filter_id = new QFilter("id",QFilter.in,map_designIdToSourceId.values());
        String select = "id,aos_funcpicdate";
        Map<String, DynamicObject> map_photoidToEnt = Arrays.stream(BusinessDataServiceHelper.load("aos_mkt_photoreq", select, new QFilter[]{filter_id}))
                .collect(Collectors.toMap(
                        dy -> dy.getString("id"),
                        dy -> dy,
                        (key1, key2) -> key1));
        return  map_photoidToEnt;
    }
    /** 执行 修改状态、时间**/
    private static void UpdateEntity(DynamicObject [] design,Map<String,String> map_itemToNo,List<String> list_photoNo, Map<String, DynamicObject> map_Source ){
        List<DynamicObject> list_designEntity = new ArrayList<>(5000); //需求表的修改单据
        List<DynamicObject> list_photoEntity = new ArrayList<>(5000);   //拍照的修改单据
        Date date_now = new Date();
        for (DynamicObject dy_design : design) {
            //String id = dy_design.getString("id");
            String itemId = dy_design.getDynamicObjectCollection("aos_entryentity").get(0).getDynamicObject("aos_itemid").getString("id");
            String sourceid = dy_design.getString("aos_sourceid");
            //获取产品号
            if (map_itemToNo.containsKey(itemId)) {
                String productNo = map_itemToNo.get(itemId);
                if (list_photoNo.contains(productNo)){
                    dy_design.set("aos_status","设计");
                    dy_design.set("aos_funcpicdate",date_now);
                    dy_design.set("aos_requiredate",date_now);// 同时需要修改申请日期
                    FndHistory.Create(dy_design, "系统提交，拍照功能图制作，修改状态为设计", "设计");
                    list_designEntity.add(dy_design);
                    if (map_Source.containsKey(sourceid)){
                        DynamicObject dy_source = map_Source.get(sourceid);
                        dy_source.set("aos_funcpicdate",date_now);
                        FndHistory.Create(dy_source, "系统提交", "拍照功能图制作状态下的设计需求表改为设计，回写完成日期");
                        list_photoEntity.add(dy_source);
                        Update(list_photoEntity,false);
                    }
                    Update(list_designEntity,false);
                }
            }
        }
        Update(list_designEntity, true);
        Update(list_photoEntity,true);
    }
    /** 执行修改 **/
    private static void Update(List<DynamicObject> list_dy,boolean end){
        if (end)
            SaveServiceHelper.update(list_dy.toArray(new DynamicObject[list_dy.size()]));
        else{
            if (list_dy.size()>4500){
                OperateOption option = OperateOption.create();
                SaveServiceHelper.update(list_dy.toArray(new DynamicObject[list_dy.size()]), option);
                list_dy.clear();
            }
        }
    }
}
