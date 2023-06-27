package mkt.data.point;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.plugin.ImportLogger;
import kd.bos.form.field.ComboItem;
import kd.bos.form.plugin.impt.BatchImportPlugin;
import kd.bos.form.plugin.impt.ImportBillData;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 官网导入插件
 */
public class aos_mkt_point_importgw extends BatchImportPlugin {
    private static Map<String, String> getAllOrgId() {
        DynamicObjectCollection list = QueryServiceHelper.query("bd_country", "id ,number", null);
        return list.stream().collect(Collectors.toMap(obj -> obj.getString("number"), obj -> obj.getString("id"), (k1, k2) -> k1));
    }

    @Override
    protected void beforeSave(List<ImportBillData> billdatas, ImportLogger logger) {
        Map<String, String> allOrgIdMap = getAllOrgId();
        for (ImportBillData data : billdatas) {
            JSONObject object = data.getData();
            JSONObject aos_orgObject = object.getJSONObject("aos_orgid");
            String aos_orgnum = aos_orgObject.getString("number");
            String aos_orgid = allOrgIdMap.getOrDefault(aos_orgnum, "0");

            String aos_detail = object.getString("aos_detail");
            String aos_category1 = object.getString("aos_category1");
            String aos_category2 = object.getString("aos_category2");
            String aos_category3 = object.getString("aos_category3");
            String aos_itemnamecn = object.getString("aos_itemnamecn");
            if (aos_detail == null) {
                object.put("aos_detail", "");
                aos_detail = "";
            }

            object.put("aos_category1_name", aos_category1);
            object.put("aos_category2_name", aos_category2);
            object.put("aos_category3_name", aos_category3);

            String selectFields1 = "aos_linentity_gw.aos_mainvoc_gw," +
                    "aos_linentity_gw.aos_type_gw," +
                    "aos_linentity_gw.aos_desc_gw," +
                    "aos_linentity_gw.aos_relate_gw," +
                    "aos_linentity_gw.aos_rate_gw," +
                    "aos_linentity_gw.aos_counts_gw," +
                    "aos_linentity_gw.aos_source_gw," +
                    "aos_linentity_gw.aos_adress_gw";;
            DynamicObject object1 = BusinessDataServiceHelper.loadSingle("aos_mkt_point", selectFields1, new QFilter[]{
                    new QFilter("aos_orgid", QCP.equals, aos_orgid),
                    new QFilter("aos_category1", QCP.equals, aos_category1),
                    new QFilter("aos_category2", QCP.equals, aos_category2),
                    new QFilter("aos_category3", QCP.equals, aos_category3),
                    new QFilter("aos_itemnamecn", QCP.equals, aos_itemnamecn),
                    new QFilter("aos_detail", QCP.equals, aos_detail)
            });
            if (object1 == null) continue;
            JSONArray aos_linentity_gw = object.getJSONArray("aos_linentity_gw");
            DynamicObjectCollection aos_linentity_gw1 = object1.getDynamicObjectCollection("aos_linentity_gw");
            aos_linentity_gw1.clear();

            for (Object v : aos_linentity_gw) {
                JSONObject webLineData = (JSONObject) v;
                Object aos_mainvoc_gw = webLineData.get("aos_mainvoc_gw");
                Object aos_type_gw = webLineData.get("aos_type_gw");
                Object aos_desc_gw = webLineData.get("aos_desc_gw");
                Object aos_relate_gw = webLineData.get("aos_relate_gw");
                Object aos_rate_gw = webLineData.get("aos_rate_gw");
                Object aos_counts_gw = webLineData.get("aos_counts_gw");
                Object aos_source_gw = webLineData.get("aos_source_gw");
                Object aos_adress_gw = webLineData.get("aos_adress_gw");

                DynamicObject object2 = aos_linentity_gw1.addNew();
                object2.set("aos_mainvoc_gw", aos_mainvoc_gw);
                object2.set("aos_type_gw", aos_type_gw);
                object2.set("aos_desc_gw", aos_desc_gw);
                object2.set("aos_relate_gw", aos_relate_gw);
                object2.set("aos_rate_gw", aos_rate_gw);
                object2.set("aos_counts_gw", aos_counts_gw);
                object2.set("aos_source_gw", aos_source_gw);
                object2.set("aos_adress_gw", aos_adress_gw);
            }
            aos_mkt_point_bill.setCate(object1);
            SaveServiceHelper.save(new DynamicObject[]{object1});

            aos_linentity_gw.clear();
        }
    }



    @Override
    public List<ComboItem> getOverrideFieldsConfig() {
        List<ComboItem> overrideFieldsConfig = super.getOverrideFieldsConfig();
        List<ComboItem> comboItems = new ArrayList<>(6);
        for (int i = 0; i < overrideFieldsConfig.size(); i++) {
            ComboItem comboItem = overrideFieldsConfig.get(i);
            String value = comboItem.getValue();
            if ("aos_orgid".equals(value) || "aos_itemnamecn".equals(value)
                    || "aos_category1".equals(value) || "aos_category2".equals(value)
                    || "aos_category3".equals(value) || "aos_detail".equals(value)) {
                comboItems.add(comboItem);
            }
        }
        return comboItems;
    }
}
