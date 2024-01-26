package mkt.progress.parameter;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * @author aosom
 * @version 无法3D建模清单-表单插件
 */
public class AosMktNot3dItemBill extends AbstractBillPlugIn {
    public final static int TWO = 2;
    public final static String AOS_ITEM = "aos_item";

    public static Map<String, String> getSkuCategory(String sku) {
        QFilter qFilterSku = new QFilter("material", "=", sku);
        QFilter qFilterJb = new QFilter("standard.number", "=", "JBFLBZ");
        DynamicObject dyGroup = QueryServiceHelper.queryOne("bd_materialgroupdetail",
            "group.name as name,group.number as number", new QFilter[] {qFilterSku, qFilterJb});
        return getCategory(dyGroup.get("number").toString(), dyGroup.get("name").toString());
    }

    /**
     * 获取大、中、小产品类别
     *
     * @param group3Number 小类编码
     * @param group3Name 小类名称
     */
    public static Map<String, String> getCategory(String group3Number, String group3Name) {
        try {
            Map<String, String> category = new HashMap<>(16);
            String[] number = org.apache.commons.lang.StringUtils.split(group3Number, ",");
            String[] name = org.apache.commons.lang.StringUtils.split(group3Name, ",");
            category.put("aos_category_code1", number[0]);
            category.put("aos_category_stat1", name[0]);
            if (number.length > TWO && name.length > 1) {
                category.put("aos_category_code2", number[1]);
                category.put("aos_category_stat2", name[1]);
            }
            if (number.length > TWO && name.length > 1) {
                category.put("aos_category_code3", number[2]);
                category.put("aos_category_stat3", name[2]);
            }
            System.out.println("category = " + category);
            return category;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        super.propertyChanged(e);
        String name = e.getProperty().getName();
        if (AOS_ITEM.equals(name)) {
            DynamicObject skudy = (DynamicObject)e.getChangeSet()[0].getNewValue();
            String sku = skudy.getPkValue().toString();
            System.out.println("sku = " + sku);
            Map<String, String> category = getSkuCategory(sku);
            this.getModel().setValue("aos_cate1", category.get("aos_category_stat1"));
            this.getModel().setValue("aos_cate2", category.get("aos_category_stat2"));
            this.getModel().setValue("aos_cate3", category.get("aos_category_stat3"));
        }
    }
}
