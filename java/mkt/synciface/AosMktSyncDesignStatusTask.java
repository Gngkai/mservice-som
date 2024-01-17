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
 * @since 2022/11/22 16:32
 * @version 拍照功能图制作状态下的设计需求表，修改状态为设计，并且回写完成日期-调度任务类
 */
public class AosMktSyncDesignStatusTask extends AbstractTask {

    /** 同步 **/
    public static void syncDesignreq() {
        // 查询设计需求表id
        DynamicObject[] dycDesign = queryDesignreqId();
        // 获取物料的产品号
        Map<String, String> mapItemToNo = queryProductNo(dycDesign);
        // 获取功能需求表中 第一行ZH不为空的产品号
        List<String> listPhotoNo = queryFuncreq(mapItemToNo);
        // 获取设计需求表的来源单 来源单据体id对来源单据
        Map<String, DynamicObject> mapSource = querySourceEntity(dycDesign);
        // 执行修改
        updateEntity(dycDesign, mapItemToNo, listPhotoNo, mapSource);
    }

    /** 查询 拍照功能图制作状态的设计需求表单据 **/
    private static DynamicObject[] queryDesignreqId() {
        QFilter filterStatus = new QFilter("aos_status", "=", "拍照功能图制作");
        QFilter filterSource = new QFilter("aos_sourcetype", "=", "PHOTO");
        StringJoiner select = new StringJoiner(",");
        select.add("id");
        select.add("billno");
        select.add("aos_funcpicdate");
        select.add("aos_status");
        select.add("aos_sourceid");
        select.add("aos_entryentity.aos_itemid");
        select.add("aos_requiredate");
        return BusinessDataServiceHelper.load("aos_mkt_designreq", select.toString(),
            new QFilter[] {filterStatus, filterSource});
    }

    /** 查询物料的产品号 **/
    private static Map<String, String> queryProductNo(DynamicObject[] dycDsign) {
        // 获取物料id
        List<Object> listItemid = Arrays.stream(dycDsign).map(
            dy -> dy.getDynamicObjectCollection("aos_entryentity").get(0).getDynamicObject("aos_itemid").getPkValue())
            .distinct().collect(Collectors.toList());
        // 查找物料的产品号
        QFilter filterId = new QFilter("id", QFilter.in, listItemid);
        return QueryServiceHelper.query("bd_material", "id,aos_productno", new QFilter[] {filterId}).stream().collect(
            Collectors.toMap(dy -> dy.getString("id"), dy -> dy.getString("aos_productno"), (key1, key2) -> key1));
    }

    /** 查询 产品号对应的功能图需求表 **/
    private static List<String> queryFuncreq(Map<String, String> mapItemToNo) {
        QFBuilder builder = new QFBuilder("aos_segment3", QFilter.in, mapItemToNo.values());
        builder.add("aos_entity1.aos_lan1", "=", "中文");
        StringJoiner str = new StringJoiner(",");
        str.add("id");
        str.add("aos_segment3");
        str.add("aos_entity1.aos_value1 aos_value1");
        str.add("aos_entity1.aos_content1 aos_content1");
        return QueryServiceHelper.query("aos_mkt_functreq", str.toString(), builder.toArray()).stream()
            .filter(dy -> (!Cux_Common_Utl.IsNull(dy.get("aos_value1")))
                || (!Cux_Common_Utl.IsNull(dy.get("aos_content1"))))
            .map(dy -> dy.getString("aos_segment3")).distinct().collect(Collectors.toList());
    }

    /** 查询 来源的拍照需求表 **/
    private static Map<String, DynamicObject> querySourceEntity(DynamicObject[] dycDesign) {
        // 获取来源单号
        Map<String, String> mapDesignIdToSourceId = Arrays.stream(dycDesign).collect(
            Collectors.toMap(dy -> dy.getString("id"), dy -> dy.getString("aos_sourceid"), (key1, key2) -> key1));
        // 查找来源单
        QFilter filterId = new QFilter("id", QFilter.in, mapDesignIdToSourceId.values());
        String select = "id,aos_funcpicdate";
        return Arrays.stream(BusinessDataServiceHelper.load("aos_mkt_photoreq", select, new QFilter[] {filterId}))
            .collect(Collectors.toMap(dy -> dy.getString("id"), dy -> dy, (key1, key2) -> key1));
    }

    /** 执行 修改状态、时间 **/
    private static void updateEntity(DynamicObject[] design, Map<String, String> mapItemToNo, List<String> listPhotoNo,
        Map<String, DynamicObject> mapSource) {
        // 需求表的修改单据
        List<DynamicObject> listDesignEntity = new ArrayList<>(5000);
        // 拍照的修改单据
        List<DynamicObject> listPhotoEntity = new ArrayList<>(5000);
        Date dateNow = new Date();
        for (DynamicObject dyDesign : design) {
            String itemId = dyDesign.getDynamicObjectCollection("aos_entryentity").get(0).getDynamicObject("aos_itemid")
                .getString("id");
            String sourceid = dyDesign.getString("aos_sourceid");
            // 获取产品号
            if (mapItemToNo.containsKey(itemId)) {
                String productNo = mapItemToNo.get(itemId);
                if (listPhotoNo.contains(productNo)) {
                    dyDesign.set("aos_status", "设计");
                    dyDesign.set("aos_funcpicdate", dateNow);
                    // 同时需要修改申请日期
                    dyDesign.set("aos_requiredate", dateNow);
                    FndHistory.Create(dyDesign, "系统提交，拍照功能图制作，修改状态为设计", "设计");
                    listDesignEntity.add(dyDesign);
                    if (mapSource.containsKey(sourceid)) {
                        DynamicObject dySource = mapSource.get(sourceid);
                        dySource.set("aos_funcpicdate", dateNow);
                        FndHistory.Create(dySource, "系统提交", "拍照功能图制作状态下的设计需求表改为设计，回写完成日期");
                        listPhotoEntity.add(dySource);
                        update(listPhotoEntity, false);
                    }
                    update(listPhotoEntity, false);
                }
            }
        }
        update(listDesignEntity, true);
        update(listPhotoEntity, true);
    }

    /** 执行修改 **/
    private static void update(List<DynamicObject> listDy, boolean end) {
        if (end) {
            int size = listDy.size();
            SaveServiceHelper.update(listDy.toArray(new DynamicObject[size]));
        } else {
            int count = 4500;
            if (listDy.size() > count) {
                OperateOption option = OperateOption.create();
                int size = listDy.size();
                SaveServiceHelper.update(listDy.toArray(new DynamicObject[size]), option);
                listDy.clear();
            }
        }
    }

    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        syncDesignreq();
    }
}
