package mkt.progress.design3d;

import common.sal.sys.basedata.dao.ItemCategoryDao;
import common.sal.sys.basedata.dao.impl.ItemCategoryDaoImpl;
import common.sal.util.SaveUtils;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.metadata.dynamicobject.DynamicObjectType;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.fi.bd.util.QFBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author: GK
 * @create: 2023-09-11 17:19
 * @Description: 3D建模sku清单 bean
 */
public class DesignSkuList {
    public static void createEntity(DynamicObject designEntity){
        DynamicObjectCollection entityRows = designEntity.getDynamicObjectCollection("aos_entryentity");
        //获取已经存在的sku
        List<String> skuList = getSkuList();
        //记录需要创建的sku
        List<String> createList = new ArrayList<>(entityRows.size());
        for (DynamicObject row : entityRows) {
            DynamicObject item = row.getDynamicObject("aos_itemid");
            if (item ==null){
                continue;
            }
            if (skuList.contains(item.getString("id"))) {
                continue;
            }
            createList.add(item.getString("id"));
        }
        //获取品名
        ItemCategoryDao categoryDao = new ItemCategoryDaoImpl();
        Map<String, String> cateInfo = categoryDao.getItemCateName(createList).getA();
        List<DynamicObject> list_save = new ArrayList<>(5000);
        DynamicObjectType designType = BusinessDataServiceHelper.newDynamicObject("aos_mkt_3ditem").getDynamicObjectType();
        for (String itemid : createList) {
            DynamicObject dy = new DynamicObject(designType);
            dy.set("aos_item",itemid);
            dy.set("billstatus","A");
            if (cateInfo.containsKey(itemid)) {
                String cate = cateInfo.get(itemid);
                String[] split = cate.split(",");
                if (split.length>0){
                    dy.set("aos_cate1",split[0]);
                }
                if (split.length>1){
                    dy.set("aos_cate2",split[1]);
                }
                if (split.length>2){
                    dy.set("aos_cate3",split[2]);
                }
            }
            list_save.add(dy);
            SaveUtils.SaveEntity("aos_mkt_3ditem",list_save,false);
        }
        SaveUtils.SaveEntity("aos_mkt_3ditem",list_save,true);

    }


    public static List<String> getSkuList(){
        QFBuilder builder = new QFBuilder();
        builder.add("aos_item","!=","");
        DynamicObjectCollection result = QueryServiceHelper.query("aos_mkt_3ditem", "aos_item", builder.toArray());
        List<String> itemids = new ArrayList<>(result.size());
        for (DynamicObject row : result) {
            itemids.add(row.getString("aos_item"));
        }
        return itemids;
    }


}
