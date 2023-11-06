package mkt.data.standard;

import common.fnd.FndGlobal;
import common.fnd.FndMsg;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
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

import java.util.*;
import java.util.stream.Collectors;

/**
 * 摄影标准库初始化
 */
public class AosMktPhotoInit extends AbstractTask {
    public static void run() {
        // 已存在的摄影标准库
        Set<String> stdCategory = getStdCategory();
        // 国别品类人员表 国别编辑
        HashMap<String, String> cateUser = getCateUser();
        // 循环物料分类
        DataSet groupS = QueryServiceHelper.queryDataSet("AosMktStdInit.run",
                "bd_materialgroupdetail", "group.name category,material.name item_name",
                new QFilter("group.level", QCP.equals, 3)
                        .and("material.aos_protype", QCP.equals, "N")
                        .toArray(), null);
        groupS = groupS.distinct();
        List<DynamicObject> aosMktPhotoS = new ArrayList<>();
        int i = 0;
        String ip = "https://clsv.s3.amazonaws.com/";
        while (groupS.hasNext()) {
            Row group = groupS.next();
            String category = group.getString("category");
            String itemName = group.getString("item_name");
            String aosCategory1 = category.split(",")[0];
            String aosCategory2 = category.split(",")[1];
            String aosCategory3 = category.split(",")[2];
            String userKey = aosCategory1 + "~" + aosCategory2;
            FndMsg.debug("大类:" + aosCategory1 +
                    ",中类:" + aosCategory2 +
                    ",小类:" + aosCategory3 + ",品名:" + itemName);
            if (FndGlobal.IsNotNull(stdCategory) && stdCategory.contains(aosCategory1 + "~" +
                    aosCategory2 + "~" + aosCategory3 + "~" + itemName))
                continue;
            DynamicObject aosMktPhoto = BusinessDataServiceHelper.newDynamicObject("aos_mkt_photostd");

            aosMktPhoto.set("aos_category1_name", aosCategory1);
            aosMktPhoto.set("aos_category2_name", aosCategory2);
            aosMktPhoto.set("aos_category3_name", aosCategory3);
            aosMktPhoto.set("aos_itemnamecn", itemName);
            aosMktPhoto.set("aos_status", "待制作");
            aosMktPhoto.set("aos_user", cateUser.get(userKey));

            // https://clsv.s3.amazonaws.com/居家系列/书房&办公家具/白板&展示板/A型板/白底/整体角度示意图/正面.jpg
            // 加密示例 加密(居家系列) + @符号 + 加密(居家系列/书房&办公家具/白板&展示板/A型板/白底/整体角度示意图/正面.jpg)
//        String prefix = MKTS3PIC.decrypt(category[0]) + "@";
            String add = aosCategory1 +"/" + aosCategory2 + "/" + aosCategory3 + "/" + itemName + "/" ;

            aosMktPhoto.set("aos_anglesignal1", ip  + add+"白底/整体角度示意图/正面.jpg");
            aosMktPhoto.set("aos_anglesignal2", "/root/Pictures/空.jpg");
            aosMktPhoto.set("aos_anglesignal3", ip  + add+"白底/整体角度示意图/30度中机位右.jpg");
            aosMktPhoto.set("aos_anglesignal4", ip  + add+"白底/整体角度示意图/30度高机位.jpg");
            aosMktPhoto.set("aos_anglesignal5", ip  + add+"白底/整体角度示意图/90度中机位侧面.jpg");
            aosMktPhoto.set("aos_anglesignal6", ip  + add+"白底/整体角度示意图/背30度中机位.jpg");
            aosMktPhoto.set("aos_anglesignal7", ip  + add+"白底/整体角度示意图/特殊角度/形态1.jpg");
            aosMktPhoto.set("aos_anglesignal8", ip  + add+"白底/整体角度示意图/特殊角度/形态2.jpg");
            aosMktPhoto.set("aos_anglesignal9", ip  + add+"白底/整体角度示意图/特殊角度/形态3.jpg");

            aosMktPhoto.set("aos_replace1", ip  + add+"白底/单件/配件/示意图1.jpg");
            aosMktPhoto.set("aos_replace2", ip  + add+"白底/单件/配件/示意图2.jpg");
            aosMktPhoto.set("aos_replace3", ip  + add+"白底/单件/配件/示意图3.jpg");
            aosMktPhoto.set("aos_replace4", ip  + add+"白底/单件/配件/示意图4.jpg");
            aosMktPhoto.set("aos_replace5", ip  + add+"白底/单件/配件/示意图5.jpg");
            aosMktPhoto.set("aos_replace6", ip  + add+"白底/单件/配件/示意图6.jpg");

            aosMktPhoto.set("aos_detail1", ip  + add+"实景/细节图/细节1.jpg");
            aosMktPhoto.set("aos_detail2", ip  + add+"实景/细节图/细节2.jpg");
            aosMktPhoto.set("aos_detail3", ip  + add+"实景/细节图/细节3.jpg");
            aosMktPhoto.set("aos_detail4", ip  + add+"实景/细节图/细节4.jpg");
            aosMktPhoto.set("aos_detail5", ip  + add+"实景/细节图/细节5.jpg");
            aosMktPhoto.set("aos_detail6", ip  + add+"实景/细节图/细节6.jpg");
            aosMktPhoto.set("aos_detail7", ip  + add+"实景/细节图/细节7.jpg");
            aosMktPhoto.set("aos_detail8", ip  + add+"实景/细节图/细节8.jpg");
            aosMktPhoto.set("aos_detail9", ip  + add+"实景/细节图/细节9.jpg");

            aosMktPhoto.set("aos_refer1", ip  + add+"实景/参考图/参考1.jpg");
            aosMktPhoto.set("aos_refer2", ip  + add+"实景/参考图/参考2.jpg");
            aosMktPhoto.set("aos_refer3", ip  + add+"实景/参考图/参考3.jpg");
            aosMktPhoto.set("aos_refer4", ip  + add+"实景/参考图/参考4.jpg");
            aosMktPhoto.set("aos_refer5", ip  + add+"实景/参考图/参考5.jpg");
            aosMktPhoto.set("aos_refer6", ip  + add+"实景/参考图/参考6.jpg");
            aosMktPhoto.set("aos_refer7", ip  + add+"实景/参考图/参考7.jpg");
            aosMktPhoto.set("aos_refer8", ip  + add+"实景/参考图/参考8.jpg");
            aosMktPhoto.set("aos_refer9", ip  + add+"实景/参考图/参考9.jpg");


            
            aosMktPhotoS.add(aosMktPhoto);
            if (aosMktPhotoS.size() >= 5000 || !groupS.hasNext()) {
                SaveServiceHelper.save(aosMktPhotoS.toArray(new DynamicObject[0]));
                aosMktPhotoS.clear();
            }
        }
        groupS.close();
    }

    /**
     * @return
     */
    private static Set<String> getStdCategory() {
        DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_photostd",
                "aos_category1_name," +
                        "aos_category2_name," +
                        "aos_category3_name," +
                        "aos_itemnamecn",
                null);
        return list.stream().map(obj -> obj.getString("aos_category1_name")
                + "~" + obj.getString("aos_category2_name") + "~" + obj.getString("aos_category3_name")
                + "~" + obj.getString("aos_itemnamecn")).collect(Collectors.toSet());
    }

    /**
     * 品类人员对应表 设计
     */
    private static HashMap<String, String> getCateUser() {
        HashMap<String, String> cateUser = new HashMap<>();
        DynamicObjectCollection cateUserDynS = QueryServiceHelper.query("aos_mkt_proguser",
                "aos_category1," +
                        "aos_category2," +
                        "aos_actph",
                null);
        for (DynamicObject cateUserDyn : cateUserDynS)
            cateUser.put(cateUserDyn.getString("aos_category1") + "~" +
                            cateUserDyn.getString("aos_category2"),
                    cateUserDyn.getString("aos_actph")
            );
        return cateUser;
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        run();
    }
}