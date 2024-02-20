package mkt.data.standardlib;

import kd.bos.algo.DataSet;
import kd.bos.algo.JoinType;
import kd.bos.algo.Row;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.OrmLocaleValue;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 同步标准库 lch 2022-05-03
 */
public class SyncStandardLib extends AbstractTask {
    public final static String AOS_MKT_POINT = "aos_mkt_point";
    /**
     *
     */
    public static void syncCategoryInfo() {
        // 国别信息
        DynamicObjectCollection orgList = QueryServiceHelper.query("bd_country", "id",
            new QFilter[] {new QFilter("aos_is_oversea_ou.number", QCP.equals, "Y")});
        DynamicObject[] bdMaterial = BusinessDataServiceHelper.load("bd_material", "name", null);
        // 英文品名
        Map<String, String> itemnameenMap = new HashMap<>(16);
        for (DynamicObject obj : bdMaterial) {
            OrmLocaleValue aosItemname = (OrmLocaleValue)obj.get("name");
            String localeValueZhCn = aosItemname.getLocaleValue_zh_CN();
            String localeValueEn = aosItemname.getLocaleValue_en();
            itemnameenMap.put(localeValueZhCn, localeValueEn);
        }
        // 查询物料分类中的品类+品名信息
        DataSet categoryDataSet = QueryServiceHelper.queryDataSet("SyncStandardLib_category", "bd_materialgroupdetail",
            "group.name aos_category3_name,group.parent aos_category2,material.name aos_itemnamecn", null, null);
        categoryDataSet = categoryDataSet.distinct();
        String selectFields = "aos_category2," + "aos_group_edit,aos_edit_leader,aos_editor,"
            + "aos_group_design,aos_design_leader,aos_designer," + "aos_group_photo,aos_photo_leader,aos_photographer,"
            + "aos_group_video,aos_video_leader,aos_videographer," + "aos_group_view,aos_view_leader,aos_setdesigner";
        DataSet groupInfoDataSet =
            QueryServiceHelper.queryDataSet("SyncStandardLib_groupinfo", "aos_mkt_groupinfo", selectFields, null, null);
        categoryDataSet = categoryDataSet.join(groupInfoDataSet, JoinType.INNER).on("aos_category2", "aos_category2")
            .select("aos_category3_name", "aos_itemnamecn", "aos_group_edit", "aos_edit_leader", "aos_editor",
                "aos_group_design", "aos_design_leader", "aos_designer", "aos_group_photo", "aos_photo_leader",
                "aos_photographer", "aos_group_video", "aos_video_leader", "aos_videographer", "aos_group_view",
                "aos_view_leader", "aos_setdesigner")
            .finish();

        List<DynamicObject> articalStdObjList = new ArrayList<>();
        List<DynamicObject> pointStdObjList = new ArrayList<>();
        List<DynamicObject> designStdObjList = new ArrayList<>();
        List<DynamicObject> photoStdObjList = new ArrayList<>();
        List<DynamicObject> videoStdObjList = new ArrayList<>();
        List<DynamicObject> viewStdObjList = new ArrayList<>();
        // 查询营销组别信息
        while (categoryDataSet.hasNext()) {
            Row next = categoryDataSet.next();

            // 小类
            String aosCategory3Name = next.getString("aos_category3_name");
            String[] split = aosCategory3Name.split(",");
            if (split.length < 2) {
                continue;
            }
            // 品名
            String aosItemnamecn = next.getString("aos_itemnamecn");
            String aosItemnameen = itemnameenMap.get(aosItemnamecn);

            // 编辑组
            long aosGroupEdit = next.getLong("aos_group_edit");
            // 编辑组长
            long aosEditLeader = next.getLong("aos_edit_leader");
            // 编辑师
            long aosEditor = next.getLong("aos_editor");
            if (aosGroupEdit != 0 && aosEditLeader != 0 && aosEditor != 0) {
                for (DynamicObject orgObj : orgList) {
                    String orgId = orgObj.getString("id");
                    DynamicObject dynamicObject = setStandardBillArticleOrPoint("aos_mkt_standard", orgId, split,
                        aosItemnamecn, aosItemnameen, aosGroupEdit, aosEditor, aosEditLeader);
                    if (dynamicObject != null) {
                        articalStdObjList.add(dynamicObject);
                    }
                    DynamicObject dynamicObject1 = setStandardBillArticleOrPoint("aos_mkt_point", orgId, split,
                        aosItemnamecn, aosItemnameen, aosGroupEdit, aosEditor, aosEditLeader);
                    if (dynamicObject1 != null) {
                        pointStdObjList.add(dynamicObject1);
                    }
                }

            }
            // 设计组
            long aosGroupDesign = next.getLong("aos_group_design");
            // 设计组长
            long aosDesignLeader = next.getLong("aos_design_leader");
            // 设计师
            long aosDesigner = next.getLong("aos_designer");
            if (aosGroupDesign != 0 && aosDesignLeader != 0 && aosDesigner != 0) {
                DynamicObject dynamicObject = setStandardBillData("aos_mkt_designstd", split, aosItemnamecn,
                    aosGroupDesign, aosDesigner, aosDesignLeader);
                if (dynamicObject != null) {
                    designStdObjList.add(dynamicObject);
                }
            }
            // 摄影组
            long aosGroupPhoto = next.getLong("aos_group_photo");
            // 摄影组长
            long aosPhotoLeader = next.getLong("aos_photo_leader");
            // 摄影师
            long aosPhotographer = next.getLong("aos_photographer");
            if (split.length > 2) {
                DynamicObject dynamicObject1 =
                    setPhotoStd(split, aosItemnamecn, aosGroupPhoto, aosPhotoLeader, aosPhotographer);
                if (dynamicObject1 != null) {
                    photoStdObjList.add(dynamicObject1);
                }
            }
            // 摄像组
            long aosGroupVideo = next.getLong("aos_group_video");
            // 摄像组长
            long aosVideoLeader = next.getLong("aos_video_leader");
            // 摄像师
            long aosVideographer = next.getLong("aos_videographer");
            if (aosGroupVideo != 0 && aosVideoLeader != 0 && aosVideographer != 0) {
                DynamicObject dynamicObject = setStandardBillData("aos_mkt_videostd", split, aosItemnamecn,
                    aosGroupVideo, aosVideographer, aosVideoLeader);
                if (dynamicObject != null) {
                    videoStdObjList.add(dynamicObject);
                }
            }
            // 布景组
            long aosGroupView = next.getLong("aos_group_view");
            // 布景组长
            long aosViewLeader = next.getLong("aos_view_leader");
            // 布景师
            long aosSetdesigner = next.getLong("aos_setdesigner");
            if (aosGroupView != 0 && aosViewLeader != 0 && aosSetdesigner != 0) {
                DynamicObject dynamicObject = setStandardBillData("aos_mkt_viewstd", split, aosItemnamecn,
                    aosGroupView, aosSetdesigner, aosViewLeader);
                if (dynamicObject != null) {
                    viewStdObjList.add(dynamicObject);
                }
            }
        }
        categoryDataSet.close();
        if (articalStdObjList.size() > 0) {
            // 文案库
            SaveServiceHelper.saveOperate("aos_mkt_standard", articalStdObjList.toArray(new DynamicObject[0]),
                OperateOption.create());
        }
        if (pointStdObjList.size() > 0) {
            // 关键词
            SaveServiceHelper.saveOperate("aos_mkt_point", pointStdObjList.toArray(new DynamicObject[0]),
                OperateOption.create());
        }

        if (designStdObjList.size() > 0) {
            // 设计
            SaveServiceHelper.saveOperate("aos_mkt_designstd", designStdObjList.toArray(new DynamicObject[0]),
                OperateOption.create());
        }
        if (photoStdObjList.size() > 0) {
            // 摄影
            SaveServiceHelper.saveOperate("aos_mkt_photostd", photoStdObjList.toArray(new DynamicObject[0]),
                OperateOption.create());
        }
        if (videoStdObjList.size() > 0) {
            // 摄像
            SaveServiceHelper.saveOperate("aos_mkt_videostd", videoStdObjList.toArray(new DynamicObject[0]),
                OperateOption.create());
        }

        if (viewStdObjList.size() > 0) {
            // 布景
            SaveServiceHelper.saveOperate("aos_mkt_viewstd", viewStdObjList.toArray(new DynamicObject[0]),
                OperateOption.create());
        }

    }

    private static DynamicObject setStandardBillData(String bill, String[] category, String aosItemnamecn,
        long groupId, long aosOperater, long aosAuditor) {
        boolean exists = QueryServiceHelper.exists(bill,
            new QFilter[] {new QFilter("aos_category1_name", QCP.equals, category[0]),
                new QFilter("aos_category2_name", QCP.equals, category[1]),
                new QFilter("aos_category3_name", QCP.equals, category[2]),
                new QFilter("aos_itemnamecn", QCP.equals, aosItemnamecn)});
        if (exists)
        {
            return null;
        }
        DynamicObject dynamicObject = BusinessDataServiceHelper.newDynamicObject(bill);
        dynamicObject.set("aos_groupid", groupId);
        dynamicObject.set("aos_category1_name", category[0]);
        dynamicObject.set("aos_category2_name", category[1]);
        dynamicObject.set("aos_category3_name", category[2]);
        dynamicObject.set("aos_itemnamecn", aosItemnamecn);
        dynamicObject.set("aos_user", aosOperater);
        dynamicObject.set("aos_confirmor", aosAuditor);
        dynamicObject.set("billstatus", "A");
        return dynamicObject;
    }

    private static DynamicObject setStandardBillArticleOrPoint(String bill, String orgId, String[] category,
        String aosItemnamecn, String aosItemnameen, long groupId, long aosOperater, long aosAuditor) {
        boolean exists = QueryServiceHelper.exists(bill,
            new QFilter[] {new QFilter("aos_category1_name", QCP.equals, category[0]),
                new QFilter("aos_category2_name", QCP.equals, category[1]),
                new QFilter("aos_category3_name", QCP.equals, category[2]),
                new QFilter("aos_itemnamecn", QCP.equals, aosItemnamecn)});
        if (exists)
        {
            return null;
        }
        DynamicObject dynamicObject = BusinessDataServiceHelper.newDynamicObject(bill);
        dynamicObject.set("aos_orgid", orgId);
        if (!AOS_MKT_POINT.equals(bill)) {
            dynamicObject.set("aos_groupid", groupId);
            dynamicObject.set("aos_confirmor", aosAuditor);
        }
        if (AOS_MKT_POINT.equals(bill)) {
            dynamicObject.set("aos_itemnamecn_s", aosItemnamecn);
        }

        dynamicObject.set("aos_category1_name", category[0]);
        dynamicObject.set("aos_category2_name", category[1]);
        dynamicObject.set("aos_category3_name", category[2]);
        dynamicObject.set("aos_category1", category[0]);
        dynamicObject.set("aos_category2", category[1]);
        dynamicObject.set("aos_category3", category[2]);
        dynamicObject.set("aos_itemnamecn", aosItemnamecn);
        dynamicObject.set("aos_itemnameen", aosItemnameen);
        dynamicObject.set("aos_user", aosOperater);

        dynamicObject.set("billstatus", "A");
        return dynamicObject;
    }

    private static DynamicObject setPhotoStd(String[] category, String aosItemnamecn, long groupId, long aosOperater,
        long aosAuditor) {
        boolean exists = QueryServiceHelper.exists("aos_mkt_photostd",
            new QFilter[] {new QFilter("aos_category1_name", QCP.equals, category[0]),
                new QFilter("aos_category2_name", QCP.equals, category[1]),
                new QFilter("aos_category3_name", QCP.equals, category[2]),
                new QFilter("aos_itemnamecn", QCP.equals, aosItemnamecn)});
        if (exists)
        {
            return null;
        }
        DynamicObject dynamicObject = BusinessDataServiceHelper.newDynamicObject("aos_mkt_photostd");
        dynamicObject.set("aos_groupid", groupId);
        dynamicObject.set("aos_category1_name", category[0]);
        dynamicObject.set("aos_category2_name", category[1]);
        dynamicObject.set("aos_category3_name", category[2]);
        dynamicObject.set("aos_itemnamecn", aosItemnamecn);
        dynamicObject.set("aos_user", aosOperater);
        dynamicObject.set("aos_confirmor", aosAuditor);
        dynamicObject.set("billstatus", "A");
        String ip = "https://clsv.s3.amazonaws.com/";
        // https://clsv.s3.amazonaws.com/居家系列/书房&办公家具/白板&展示板/A型板/白底/整体角度示意图/正面.jpg
        // 加密示例 加密(居家系列) + @符号 + 加密(居家系列/书房&办公家具/白板&展示板/A型板/白底/整体角度示意图/正面.jpg)
        // String prefix = MKTS3PIC.decrypt(category[0]) + "@";
        String add = category[0] + "/" + category[1] + "/" + category[2] + "/" + aosItemnamecn + "/";

        dynamicObject.set("aos_anglesignal1", ip + add + "白底/整体角度示意图/正面.jpg");
        dynamicObject.set("aos_anglesignal2", "/root/Pictures/空.jpg");
        dynamicObject.set("aos_anglesignal3", ip + add + "白底/整体角度示意图/30度中机位右.jpg");
        dynamicObject.set("aos_anglesignal4", ip + add + "白底/整体角度示意图/30度高机位.jpg");
        dynamicObject.set("aos_anglesignal5", ip + add + "白底/整体角度示意图/90度中机位侧面.jpg");
        dynamicObject.set("aos_anglesignal6", ip + add + "白底/整体角度示意图/背30度中机位.jpg");
        dynamicObject.set("aos_anglesignal7", ip + add + "白底/整体角度示意图/特殊角度/形态1.jpg");
        dynamicObject.set("aos_anglesignal8", ip + add + "白底/整体角度示意图/特殊角度/形态2.jpg");
        dynamicObject.set("aos_anglesignal9", ip + add + "白底/整体角度示意图/特殊角度/形态3.jpg");

        dynamicObject.set("aos_replace1", ip + add + "白底/单件/配件/示意图1.jpg");
        dynamicObject.set("aos_replace2", ip + add + "白底/单件/配件/示意图2.jpg");
        dynamicObject.set("aos_replace3", ip + add + "白底/单件/配件/示意图3.jpg");
        dynamicObject.set("aos_replace4", ip + add + "白底/单件/配件/示意图4.jpg");
        dynamicObject.set("aos_replace5", ip + add + "白底/单件/配件/示意图5.jpg");
        dynamicObject.set("aos_replace6", ip + add + "白底/单件/配件/示意图6.jpg");

        dynamicObject.set("aos_detail1", ip + add + "实景/细节图/细节1.jpg");
        dynamicObject.set("aos_detail2", ip + add + "实景/细节图/细节2.jpg");
        dynamicObject.set("aos_detail3", ip + add + "实景/细节图/细节3.jpg");
        dynamicObject.set("aos_detail4", ip + add + "实景/细节图/细节4.jpg");
        dynamicObject.set("aos_detail5", ip + add + "实景/细节图/细节5.jpg");
        dynamicObject.set("aos_detail6", ip + add + "实景/细节图/细节6.jpg");
        dynamicObject.set("aos_detail7", ip + add + "实景/细节图/细节7.jpg");
        dynamicObject.set("aos_detail8", ip + add + "实景/细节图/细节8.jpg");
        dynamicObject.set("aos_detail9", ip + add + "实景/细节图/细节9.jpg");

        dynamicObject.set("aos_refer1", ip + add + "实景/参考图/参考1.jpg");
        dynamicObject.set("aos_refer2", ip + add + "实景/参考图/参考2.jpg");
        dynamicObject.set("aos_refer3", ip + add + "实景/参考图/参考3.jpg");
        dynamicObject.set("aos_refer4", ip + add + "实景/参考图/参考4.jpg");
        dynamicObject.set("aos_refer5", ip + add + "实景/参考图/参考5.jpg");
        dynamicObject.set("aos_refer6", ip + add + "实景/参考图/参考6.jpg");
        dynamicObject.set("aos_refer7", ip + add + "实景/参考图/参考7.jpg");
        dynamicObject.set("aos_refer8", ip + add + "实景/参考图/参考8.jpg");
        dynamicObject.set("aos_refer9", ip + add + "实景/参考图/参考9.jpg");
        return dynamicObject;

    }

    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        syncCategoryInfo();
    }
}
