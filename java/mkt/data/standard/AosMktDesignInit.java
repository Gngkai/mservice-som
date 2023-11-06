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
 * 设计标准库初始化
 */
public class AosMktDesignInit extends AbstractTask {
    public static void run() {
        // 已存在的设计标准库
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
        List<DynamicObject> aosMktDesignS = new ArrayList<>();
        int i = 0;
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
            DynamicObject aosMktDesign = BusinessDataServiceHelper.newDynamicObject("aos_mkt_designstd");
            aosMktDesign.set("aos_category1_name", aosCategory1);
            aosMktDesign.set("aos_category2_name", aosCategory2);
            aosMktDesign.set("aos_category3_name", aosCategory3);
            aosMktDesign.set("aos_itemnamecn", itemName);
            aosMktDesign.set("aos_status", "待制作");
            aosMktDesign.set("aos_user", cateUser.get(userKey));
            aosMktDesignS.add(aosMktDesign);
            if (aosMktDesignS.size() >= 5000 || !groupS.hasNext()) {
                SaveServiceHelper.save(aosMktDesignS.toArray(new DynamicObject[0]));
                aosMktDesignS.clear();
            }
        }
        groupS.close();
    }

    /**
     * @return
     */
    private static Set<String> getStdCategory() {
        DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_designstd",
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
                        "aos_designer",
                null);
        for (DynamicObject cateUserDyn : cateUserDynS)
            cateUser.put(cateUserDyn.getString("aos_category1") + "~" +
                            cateUserDyn.getString("aos_category2"),
                    cateUserDyn.getString("aos_designer")
            );
        return cateUser;
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        run();
    }
}