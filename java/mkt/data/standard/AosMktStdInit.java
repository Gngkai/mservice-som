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

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 国别文案标准库初始化
 */
public class AosMktStdInit extends AbstractTask {
    public static void run() {
        // 已存在的国别文案标准库
        Set<String> stdCategory = getStdCategory();
        // 国别品类人员表 国别编辑
        HashMap<String, String> orgUser = getOrgUser();
        // 循环物料分类
        DataSet groupS = QueryServiceHelper.queryDataSet("AosMktStdInit.run",
                "bd_materialgroupdetail", "group.name category,material.name item_name," +
                        "material.aos_cn_name aos_cn_name," +
                        "material.aos_contryentry.aos_nationality.number org_number," +
                        "material.aos_contryentry.aos_nationality.id org_id",
                new QFilter("group.level", QCP.equals, 3)
                        .and("material.aos_protype", QCP.equals, "N")
                        .and("material.aos_contryentry.aos_nationality.number", QCP.not_equals, null)
                        .toArray(), null);
        groupS = groupS.distinct();
        List<DynamicObject> aosMktStdS = new ArrayList<>();
        while (groupS.hasNext()) {
            Row group = groupS.next();
            String orgId = group.getString("org_id");
            String orgNum = group.getString("org_number");
            String category = group.getString("category");
            String itemName = group.getString("item_name");
            String aosCategory1 = category.split(",")[0];
            String aosCategory2 = category.split(",")[1];
            String aosCategory3 = category.split(",")[2];
            String userKey = orgNum + "~" + aosCategory1 + "~" + aosCategory2;
//                FndMsg.debug("国别:" + orgNum + ",大类:" + aosCategory1 +
//                        ",中类:" + aosCategory2 +
//                        ",小类:" + aosCategory3 + ",品名:" + itemName);
            if (FndGlobal.IsNotNull(stdCategory) && stdCategory.contains(orgNum + "~"
                    + aosCategory1 + "~" + aosCategory2 + "~" + aosCategory3 + "~" + itemName))
                continue;
            DynamicObject aosMktStd = BusinessDataServiceHelper.newDynamicObject("aos_mkt_standard");
            aosMktStd.set("aos_orgid", orgId);
            aosMktStd.set("aos_category1", aosCategory1);
            aosMktStd.set("aos_category2", aosCategory2);
            aosMktStd.set("aos_category3", aosCategory3);
            aosMktStd.set("aos_category1_name", aosCategory1);
            aosMktStd.set("aos_category2_name", aosCategory2);
            aosMktStd.set("aos_category3_name", aosCategory3);
            aosMktStd.set("aos_itemnamecn", itemName);
            aosMktStd.set("aos_status", "待制作");
            aosMktStd.set("aos_itemnameen", group.getString("aos_cn_name"));
            aosMktStd.set("aos_user", orgUser.get(userKey));

            aosMktStdS.add(aosMktStd);
            if (aosMktStdS.size() >= 5000 || !groupS.hasNext()) {
                SaveServiceHelper.save(aosMktStdS.toArray(new DynamicObject[0]));
                aosMktStdS.clear();
            }
        }
        groupS.close();
    }

    /**
     * @return
     */
    private static Set<String> getStdCategory() {
        DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_standard",
                "aos_orgid.number orgNumber," +
                        "aos_category1," +
                        "aos_category2," +
                        "aos_category3," +
                        "aos_itemnamecn",
                null);
        return list.stream().map(obj -> obj.getString("orgNumber") + "~" + obj.getString("aos_category1")
                + "~" + obj.getString("aos_category2") + "~" + obj.getString("aos_category3")
                + "~" + obj.getString("aos_itemnamecn")).collect(Collectors.toSet());
    }

    /**
     * 国别品类人员表 国别编辑
     * @return
     */
    private static HashMap<String, String> getOrgUser() {
        HashMap<String, String> orgUser = new HashMap<>();
        DynamicObjectCollection orgUserDynS = QueryServiceHelper.query("aos_mkt_progorguser",
                "aos_orgid.number org_number," +
                        "aos_category1," +
                        "aos_category2," +
                        "aos_oueditor",
                null);
        for (DynamicObject orgUserDyn : orgUserDynS)
            orgUser.put(orgUserDyn.getString("org_number") + "~" +
                            orgUserDyn.getString("aos_category1") + "~" +
                            orgUserDyn.getString("aos_category2"),
                    orgUserDyn.getString("aos_oueditor")
            );
        return orgUser;
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        run();
    }
}
