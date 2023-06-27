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
import mkt.common.MKTS3PIC;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 同步标准库
 * lch
 * 2022-05-03
 */
public class SyncStandardLib extends AbstractTask {

    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        syncCategoryInfo();
    }

    /**
     *
     */
    public static void syncCategoryInfo() {
        // 国别信息
        DynamicObjectCollection orgList = QueryServiceHelper.query("bd_country", "id", new QFilter[]{new QFilter("aos_is_oversea_ou.number", QCP.equals, "Y")});
        DynamicObject[] bd_material = BusinessDataServiceHelper.load("bd_material", "name", null);
        // 英文品名
        Map<String, String> itemnameenMap = new HashMap<>();
        for (DynamicObject obj:bd_material) {
            OrmLocaleValue aos_itemname = (OrmLocaleValue) obj.get("name");
            String localeValue_zh_cn = aos_itemname.getLocaleValue_zh_CN();
            String localeValue_en = aos_itemname.getLocaleValue_en();
            itemnameenMap.put(localeValue_zh_cn, localeValue_en);
        }
        // 查询物料分类中的品类+品名信息
        DataSet categoryDataSet = QueryServiceHelper.queryDataSet("SyncStandardLib_category", "bd_materialgroupdetail", "group.name aos_category3_name,group.parent aos_category2,material.name aos_itemnamecn", null, null);
        categoryDataSet = categoryDataSet.distinct();

        String selectFields = "aos_category2," +
                "aos_group_edit,aos_edit_leader,aos_editor," +
                "aos_group_design,aos_design_leader,aos_designer," +
                "aos_group_photo,aos_photo_leader,aos_photographer," +
                "aos_group_video,aos_video_leader,aos_videographer," +
                "aos_group_view,aos_view_leader,aos_setdesigner";
        DataSet groupInfoDataSet = QueryServiceHelper.queryDataSet("SyncStandardLib_groupinfo", "aos_mkt_groupinfo", selectFields, null, null);
        categoryDataSet = categoryDataSet.join(groupInfoDataSet, JoinType.INNER).on("aos_category2", "aos_category2").select("aos_category3_name", "aos_itemnamecn",
                "aos_group_edit", "aos_edit_leader", "aos_editor",
                "aos_group_design", "aos_design_leader", "aos_designer",
                "aos_group_photo", "aos_photo_leader", "aos_photographer",
                "aos_group_video", "aos_video_leader", "aos_videographer",
                "aos_group_view", "aos_view_leader", "aos_setdesigner").finish();

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
            String aos_category3_name = next.getString("aos_category3_name");
            String[] split = aos_category3_name.split(",");
            if (split.length < 2) continue;
            // 品名
            String aos_itemnamecn = next.getString("aos_itemnamecn");
            String aos_itemnameen = itemnameenMap.get(aos_itemnamecn);

            // 编辑组
            long aos_group_edit = next.getLong("aos_group_edit");
            // 编辑组长
            long aos_edit_leader = next.getLong("aos_edit_leader");
            // 编辑师
            long aos_editor = next.getLong("aos_editor");
            if (aos_group_edit != 0 && aos_edit_leader != 0 && aos_editor != 0) {
                for (DynamicObject orgObj:orgList) {
                    String orgId = orgObj.getString("id");
                    DynamicObject dynamicObject = setStandardBillArticleOrPoint("aos_mkt_standard", orgId,split, aos_itemnamecn, aos_itemnameen,aos_group_edit, aos_editor, aos_edit_leader);
                    if (dynamicObject != null) {
                        articalStdObjList.add(dynamicObject);
                    }

                    DynamicObject dynamicObject1 = setStandardBillArticleOrPoint("aos_mkt_point", orgId,split, aos_itemnamecn, aos_itemnameen,aos_group_edit, aos_editor, aos_edit_leader);
                    if (dynamicObject1 != null) {
                        pointStdObjList.add(dynamicObject1);
                    }
                }

            }
            // 设计组
            long aos_group_design = next.getLong("aos_group_design");
            // 设计组长
            long aos_design_leader = next.getLong("aos_design_leader");
            // 设计师
            long aos_designer = next.getLong("aos_designer");
            if (aos_group_design != 0 && aos_design_leader != 0 && aos_designer != 0) {
                DynamicObject dynamicObject = setStandardBillData("aos_mkt_designstd", split, aos_itemnamecn, aos_group_design, aos_designer, aos_design_leader);
                if (dynamicObject != null) {
                    designStdObjList.add(dynamicObject);
                }
            }

            // 摄影组
            long aos_group_photo = next.getLong("aos_group_photo");
            // 摄影组长
            long aos_photo_leader = next.getLong("aos_photo_leader");
            // 摄影师
            long aos_photographer = next.getLong("aos_photographer");
            if (split.length > 2) {
                DynamicObject dynamicObject1 = setPhotoStd(split, aos_itemnamecn, aos_group_photo, aos_photo_leader, aos_photographer);
                if (dynamicObject1 != null ) {
                    photoStdObjList.add(dynamicObject1);
                }
            }

            // 摄像组
            long aos_group_video = next.getLong("aos_group_video");
            // 摄像组长
            long aos_video_leader = next.getLong("aos_video_leader");
            // 摄像师
            long aos_videographer = next.getLong("aos_videographer");
            if (aos_group_video != 0 && aos_video_leader != 0 && aos_videographer != 0) {
                DynamicObject dynamicObject = setStandardBillData("aos_mkt_videostd", split, aos_itemnamecn, aos_group_video, aos_videographer, aos_video_leader);
                if (dynamicObject != null) {
                    videoStdObjList.add(dynamicObject);
                }
            }

            // 布景组
            long aos_group_view = next.getLong("aos_group_view");
            // 布景组长
            long aos_view_leader = next.getLong("aos_view_leader");
            // 布景师
            long aos_setdesigner = next.getLong("aos_setdesigner");
            if (aos_group_view != 0 && aos_view_leader != 0 && aos_setdesigner != 0) {
                DynamicObject dynamicObject = setStandardBillData("aos_mkt_viewstd", split, aos_itemnamecn, aos_group_view, aos_setdesigner, aos_view_leader);
                if (dynamicObject != null) {
                    viewStdObjList.add(dynamicObject);
                }
            }
        }
        if (articalStdObjList.size() > 0) {
            // 文案库
            SaveServiceHelper.saveOperate("aos_mkt_standard", articalStdObjList.toArray(new DynamicObject[0]), OperateOption.create());
        }
        if (pointStdObjList.size() > 0) {
            // 关键词
            SaveServiceHelper.saveOperate("aos_mkt_point", pointStdObjList.toArray(new DynamicObject[0]), OperateOption.create());
        }

        if (designStdObjList.size() > 0) {
            // 设计
            SaveServiceHelper.saveOperate("aos_mkt_designstd", designStdObjList.toArray(new DynamicObject[0]), OperateOption.create());
        }
        if (photoStdObjList.size() > 0) {
            // 摄影
            SaveServiceHelper.saveOperate("aos_mkt_photostd", photoStdObjList.toArray(new DynamicObject[0]), OperateOption.create());
        }
        if (videoStdObjList.size() > 0) {
            // 摄像
            SaveServiceHelper.saveOperate("aos_mkt_videostd", videoStdObjList.toArray(new DynamicObject[0]), OperateOption.create());
        }

        if (viewStdObjList.size() > 0) {
            // 布景
            SaveServiceHelper.saveOperate("aos_mkt_viewstd", viewStdObjList.toArray(new DynamicObject[0]), OperateOption.create());
        }

    }


    private static DynamicObject setStandardBillData(String bill, String[] category, String aos_itemnamecn, long groupId, long aos_operater, long aos_auditor) {
        boolean exists = QueryServiceHelper.exists(bill, new QFilter[]{
                new QFilter("aos_category1_name", QCP.equals, category[0]),
                new QFilter("aos_category2_name", QCP.equals, category[1]),
                new QFilter("aos_category3_name", QCP.equals, category[2]),
                new QFilter("aos_itemnamecn", QCP.equals, aos_itemnamecn)
        });
        if (exists) return null;
        DynamicObject dynamicObject = BusinessDataServiceHelper.newDynamicObject(bill);
        dynamicObject.set("aos_groupid", groupId);
        dynamicObject.set("aos_category1_name", category[0]);
        dynamicObject.set("aos_category2_name", category[1]);
        dynamicObject.set("aos_category3_name", category[2]);
        dynamicObject.set("aos_itemnamecn", aos_itemnamecn);
        dynamicObject.set("aos_user", aos_operater);
        dynamicObject.set("aos_confirmor", aos_auditor);
        dynamicObject.set("billstatus", "A");
        return dynamicObject;
    }


    private static DynamicObject setStandardBillArticleOrPoint(String bill, String orgId,String[] category, String aos_itemnamecn, String aos_itemnameen,long groupId, long aos_operater, long aos_auditor) {
        boolean exists = QueryServiceHelper.exists(bill, new QFilter[]{
                new QFilter("aos_category1_name", QCP.equals, category[0]),
                new QFilter("aos_category2_name", QCP.equals, category[1]),
                new QFilter("aos_category3_name", QCP.equals, category[2]),
                new QFilter("aos_itemnamecn", QCP.equals, aos_itemnamecn)
        });
        if (exists) return null;
        DynamicObject dynamicObject = BusinessDataServiceHelper.newDynamicObject(bill);
        dynamicObject.set("aos_orgid", orgId);
        dynamicObject.set("aos_groupid", groupId);
        dynamicObject.set("aos_category1_name", category[0]);
        dynamicObject.set("aos_category2_name", category[1]);
        dynamicObject.set("aos_category3_name", category[2]);
        dynamicObject.set("aos_category1", category[0]);
        dynamicObject.set("aos_category2", category[1]);
        dynamicObject.set("aos_category3", category[2]);
        dynamicObject.set("aos_itemnamecn", aos_itemnamecn);
        dynamicObject.set("aos_itemnameen", aos_itemnameen);
        dynamicObject.set("aos_user", aos_operater);
        dynamicObject.set("aos_confirmor", aos_auditor);
        dynamicObject.set("billstatus", "A");
        return dynamicObject;
    }


    private static DynamicObject setPhotoStd(String[] category, String aos_itemnamecn, long groupId, long aos_operater, long aos_auditor) {
        boolean exists = QueryServiceHelper.exists("aos_mkt_photostd", new QFilter[]{
                new QFilter("aos_category1_name", QCP.equals, category[0]),
                new QFilter("aos_category2_name", QCP.equals, category[1]),
                new QFilter("aos_category3_name", QCP.equals, category[2]),
                new QFilter("aos_itemnamecn", QCP.equals, aos_itemnamecn)
        });
        if (exists) return null;
        DynamicObject dynamicObject = BusinessDataServiceHelper.newDynamicObject("aos_mkt_photostd");
        dynamicObject.set("aos_groupid", groupId);
        dynamicObject.set("aos_category1_name", category[0]);
        dynamicObject.set("aos_category2_name", category[1]);
        dynamicObject.set("aos_category3_name", category[2]);
        dynamicObject.set("aos_itemnamecn", aos_itemnamecn);
        dynamicObject.set("aos_user", aos_operater);
        dynamicObject.set("aos_confirmor", aos_auditor);
        dynamicObject.set("billstatus", "A");
        String ip = "https://clsv.s3.amazonaws.com/";
        // https://clsv.s3.amazonaws.com/居家系列/书房&办公家具/白板&展示板/A型板/白底/整体角度示意图/正面.jpg
        // 加密示例 加密(居家系列) + @符号 + 加密(居家系列/书房&办公家具/白板&展示板/A型板/白底/整体角度示意图/正面.jpg) 
//        String prefix = MKTS3PIC.decrypt(category[0]) + "@";
        String add = category[0] +"/" + category[1] + "/" + category[2] + "/" + aos_itemnamecn + "/" ;
        
        dynamicObject.set("aos_anglesignal1", ip  + add+"白底/整体角度示意图/正面.jpg");
        dynamicObject.set("aos_anglesignal2", "/root/Pictures/空.jpg");
        dynamicObject.set("aos_anglesignal3", ip  + add+"白底/整体角度示意图/30度中机位右.jpg");
        dynamicObject.set("aos_anglesignal4", ip  + add+"白底/整体角度示意图/30度高机位.jpg");
        dynamicObject.set("aos_anglesignal5", ip  + add+"白底/整体角度示意图/90度中机位侧面.jpg");
        dynamicObject.set("aos_anglesignal6", ip  + add+"白底/整体角度示意图/背30度中机位.jpg");
        dynamicObject.set("aos_anglesignal7", ip  + add+"白底/整体角度示意图/特殊角度/形态1.jpg");
        dynamicObject.set("aos_anglesignal8", ip  + add+"白底/整体角度示意图/特殊角度/形态2.jpg");
        dynamicObject.set("aos_anglesignal9", ip  + add+"白底/整体角度示意图/特殊角度/形态3.jpg");

        dynamicObject.set("aos_replace1", ip  + add+"白底/单件/配件/示意图1.jpg");
        dynamicObject.set("aos_replace2", ip  + add+"白底/单件/配件/示意图2.jpg");
        dynamicObject.set("aos_replace3", ip  + add+"白底/单件/配件/示意图3.jpg");
        dynamicObject.set("aos_replace4", ip  + add+"白底/单件/配件/示意图4.jpg");
        dynamicObject.set("aos_replace5", ip  + add+"白底/单件/配件/示意图5.jpg");
        dynamicObject.set("aos_replace6", ip  + add+"白底/单件/配件/示意图6.jpg");

        dynamicObject.set("aos_detail1", ip  + add+"实景/细节图/细节1.jpg");
        dynamicObject.set("aos_detail2", ip  + add+"实景/细节图/细节2.jpg");
        dynamicObject.set("aos_detail3", ip  + add+"实景/细节图/细节3.jpg");
        dynamicObject.set("aos_detail4", ip  + add+"实景/细节图/细节4.jpg");
        dynamicObject.set("aos_detail5", ip  + add+"实景/细节图/细节5.jpg");
        dynamicObject.set("aos_detail6", ip  + add+"实景/细节图/细节6.jpg");
        dynamicObject.set("aos_detail7", ip  + add+"实景/细节图/细节7.jpg");
        dynamicObject.set("aos_detail8", ip  + add+"实景/细节图/细节8.jpg");
        dynamicObject.set("aos_detail9", ip  + add+"实景/细节图/细节9.jpg");

        dynamicObject.set("aos_refer1", ip  + add+"实景/参考图/参考1.jpg");
        dynamicObject.set("aos_refer2", ip  + add+"实景/参考图/参考2.jpg");
        dynamicObject.set("aos_refer3", ip  + add+"实景/参考图/参考3.jpg");
        dynamicObject.set("aos_refer4", ip  + add+"实景/参考图/参考4.jpg");
        dynamicObject.set("aos_refer5", ip  + add+"实景/参考图/参考5.jpg");
        dynamicObject.set("aos_refer6", ip  + add+"实景/参考图/参考6.jpg");
        dynamicObject.set("aos_refer7", ip  + add+"实景/参考图/参考7.jpg");
        dynamicObject.set("aos_refer8", ip  + add+"实景/参考图/参考8.jpg");
        dynamicObject.set("aos_refer9", ip  + add+"实景/参考图/参考9.jpg");
        return dynamicObject;

    }

    public static boolean urlIsReach(String url) {
        if (url==null) {
            return false;
        }
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.connect();
            int responseCode = connection.getResponseCode();
            System.out.println("responseCode = " + responseCode);
            if (HttpURLConnection.HTTP_OK==connection.getResponseCode()) {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }
}
