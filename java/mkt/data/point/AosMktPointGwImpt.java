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
 * @author aosom
 * @version 官网导入插件
 */
public class AosMktPointGwImpt extends BatchImportPlugin {
    private static Map<String, String> getAllOrgId() {
        DynamicObjectCollection list = QueryServiceHelper.query("bd_country", "id ,number", null);
        return list.stream().collect(Collectors.toMap(obj -> obj.getString("number"), obj -> obj.getString("id"), (k1, k2) -> k1));
    }

    @Override
    protected void beforeSave(List<ImportBillData> billdatas, ImportLogger logger) {
        Map<String, String> allOrgIdMap = getAllOrgId();
        for (ImportBillData data : billdatas) {
            JSONObject object = data.getData();
            JSONObject aosOrgObject = object.getJSONObject("aos_orgid");
            String aosOrgnum = aosOrgObject.getString("number");
            String aosOrgid = allOrgIdMap.getOrDefault(aosOrgnum, "0");
            String aosDetail = object.getString("aos_detail");
            String aosCategory1 = object.getString("aos_category1");
            String aosCategory2 = object.getString("aos_category2");
            String aosCategory3 = object.getString("aos_category3");
            String aosItemnamecn = object.getString("aos_itemnamecn");
            if (aosDetail == null) {
                object.put("aos_detail", "");
                aosDetail = "";
            }
            object.put("aos_category1_name", aosCategory1);
            object.put("aos_category2_name", aosCategory2);
            object.put("aos_category3_name", aosCategory3);
            String selectFields1 = "aos_linentity_gw.aos_mainvoc_gw," +
                    "aos_linentity_gw.aos_type_gw," +
                    "aos_linentity_gw.aos_desc_gw," +
                    "aos_linentity_gw.aos_relate_gw," +
                    "aos_linentity_gw.aos_rate_gw," +
                    "aos_linentity_gw.aos_counts_gw," +
                    "aos_linentity_gw.aos_source_gw," +
                    "aos_linentity_gw.aos_adress_gw";;
            DynamicObject object1 = BusinessDataServiceHelper.loadSingle("aos_mkt_point", selectFields1, new QFilter[]{
                    new QFilter("aos_orgid", QCP.equals, aosOrgid),
                    new QFilter("aos_category1", QCP.equals, aosCategory1),
                    new QFilter("aos_category2", QCP.equals, aosCategory2),
                    new QFilter("aos_category3", QCP.equals, aosCategory3),
                    new QFilter("aos_itemnamecn", QCP.equals, aosItemnamecn),
                    new QFilter("aos_detail", QCP.equals, aosDetail)
            });
            if (object1 == null)
            {
                continue;
            }
            JSONArray aosLinentityGw = object.getJSONArray("aos_linentity_gw");
            DynamicObjectCollection aosLinentityGw1 = object1.getDynamicObjectCollection("aos_linentity_gw");
            aosLinentityGw1.clear();
            for (Object v : aosLinentityGw) {
                JSONObject webLineData = (JSONObject) v;
                Object aosMainvocGw = webLineData.get("aos_mainvoc_gw");
                Object aosTypeGw = webLineData.get("aos_type_gw");
                Object aosDescGw = webLineData.get("aos_desc_gw");
                Object aosRelateGw = webLineData.get("aos_relate_gw");
                Object aosRateGw = webLineData.get("aos_rate_gw");
                Object aosCountsGw = webLineData.get("aos_counts_gw");
                Object aosSourceGw = webLineData.get("aos_source_gw");
                Object aosAdressGw = webLineData.get("aos_adress_gw");
                DynamicObject object2 = aosLinentityGw1.addNew();
                object2.set("aos_mainvoc_gw", aosMainvocGw);
                object2.set("aos_type_gw", aosTypeGw);
                object2.set("aos_desc_gw", aosDescGw);
                object2.set("aos_relate_gw", aosRelateGw);
                object2.set("aos_rate_gw", aosRateGw);
                object2.set("aos_counts_gw", aosCountsGw);
                object2.set("aos_source_gw", aosSourceGw);
                object2.set("aos_adress_gw", aosAdressGw);
            }
            AosMktPointBillOld.setCate(object1);
            SaveServiceHelper.save(new DynamicObject[]{object1});
            aosLinentityGw.clear();
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
