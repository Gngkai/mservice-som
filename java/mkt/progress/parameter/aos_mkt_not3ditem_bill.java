package mkt.progress.parameter;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author Zd
 * @Date 2023/11/15 19:08
 * @Version 1.0
 */
public class aos_mkt_not3ditem_bill extends AbstractBillPlugIn {

    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        super.propertyChanged(e);
        String name = e.getProperty().getName();
        if("aos_item".equals(name)){
            DynamicObject skudy = (DynamicObject)e.getChangeSet()[0].getNewValue();
            String sku = skudy.getPkValue().toString();
            System.out.println("sku = " + sku);
            Map<String, String> category =  get_sku_category(sku);
            this.getModel().setValue("aos_cate1",category.get("aos_category_stat1"));
            this.getModel().setValue("aos_cate2",category.get("aos_category_stat2"));
            this.getModel().setValue("aos_cate3",category.get("aos_category_stat3"));
        }
    }

    public static Map<String,String> get_sku_category(String sku){
        QFilter qFilter_sku=new QFilter("material","=",sku);
        QFilter qFilter_jb=new QFilter("standard.number","=","JBFLBZ");
        DynamicObject dy_group= QueryServiceHelper.queryOne("bd_materialgroupdetail",
                "group.name as name,group.number as number",new QFilter[]{qFilter_sku,qFilter_jb});

        System.out.println("dy_group = " + dy_group);
        return   get_category(dy_group.get("number").toString(),dy_group.get("name").toString());
    }
    /**
     *获取大、中、小产品类别
     * @param group_3_number    小类编码
     * @param group_3_name  小类名称
     * @return
     */
    public static Map<String, String> get_category(String group_3_number,  String group_3_name){
        try {
            Map<String,String>category=new HashMap<>();
            String[]number= org.apache.commons.lang.StringUtils.split(group_3_number,",");
            String[]name= org.apache.commons.lang.StringUtils.split(group_3_name,",");
            category.put("aos_category_code1",number[0]);
            category.put("aos_category_stat1",name[0]);
            if (number.length>2&&name.length>1){
                category.put("aos_category_code2",number[1]);
                category.put("aos_category_stat2",name[1]);
            }
            if (number.length>2&&name.length>1) {
                category.put("aos_category_code3", number[2]);
                category.put("aos_category_stat3", name[2]);
            }
            System.out.println("category = " + category);
            return category;
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }
}
