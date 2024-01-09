package mkt.data.keyword;

import common.sal.sys.basedata.dao.ItemDao;
import common.sal.sys.basedata.dao.impl.ItemDaoImpl;
import common.sal.util.SalUtil;
import common.sal.util.SaveUtils;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import common.sal.util.QFBuilder;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author  lch
 * 2022-4-15
 * 同步关键词库SKU清单
 */

public class ItemKeywordListPlugin extends AbstractTask {
    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        // 同步SKU关键词库
        syncItemKeyword();
        // 同步关键词库
        syncItemList();
        //sku关键词状态
        syncItemStatus();
    }

    private static Map<String, String> getAllItemCategory() {
        QFilter qFilter = new QFilter("standard.number", QCP.equals, "JBFLBZ");
        String selectFields = "material,group.name categoryname";
        DynamicObjectCollection list = QueryServiceHelper.query("bd_materialgroupdetail", selectFields, qFilter.toArray());
        return list.stream().collect(Collectors.toMap(
                obj -> obj.getString("material"),
                obj -> obj.getString("categoryname"),
                (k1, k2) -> k1));
    }
    public static void syncItemKeyword() {
        Map<String, String> allItemCategory = getAllItemCategory();
        String selectFields = "aos_contryentry.aos_nationality aos_orgid," +
                "id aos_itemid," +
                "name aos_itemname";
        List<QFilter> materialFilter = SalUtil.get_MaterialFilter();
        QFilter filterDate = new QFilter("aos_contryentry.aos_firstshipment",QFilter.is_notnull,null);
        QFilter filterOrg = new QFilter("aos_contryentry.aos_nationality","!=", "");
        materialFilter.add(filterDate);
        materialFilter.add(filterOrg);

        DynamicObjectCollection list = QueryServiceHelper.query("bd_material", selectFields, materialFilter.toArray(new QFilter[0]));

        // 查询关键词库中已有的数据
        DynamicObjectCollection keywordRows = QueryServiceHelper.query("aos_mkt_keyword", "aos_orgid,aos_itemid", null);
        Set<String> keywordExists = new HashSet<>();
        for (DynamicObject obj:keywordRows) {
            String orgid = obj.getString("aos_orgid");
            String itemid = obj.getString("aos_itemid");
            keywordExists.add(orgid + "~" + itemid);
        }

        List<DynamicObject> saveList = new ArrayList<>(5000);
        for (DynamicObject obj:list) {
            String orgid = obj.getString("aos_orgid");
            String itemid = obj.getString("aos_itemid");
            // 如果SKU关键词库中已存在
            if (keywordExists.contains(orgid + "~" + itemid))
            {
                continue;
            }


            String itemname = obj.getString("aos_itemname");
            String category1 = "";
            String category2 = "";
            String category3 = "";


            String category = allItemCategory.get(itemid);
            if (category == null) {
                continue;
            }

            String[] categoryArr = category.split(",");
            if (categoryArr.length > 0) {
                category1 =  categoryArr[0];
            }
            if (categoryArr.length > 1) {
                category2 =  categoryArr[1];
            }
            if (categoryArr.length > 2) {
                category3 =  categoryArr[2];
            }

            // 新建一单
            DynamicObject itemKeywordObj = BusinessDataServiceHelper.newDynamicObject("aos_mkt_keyword");
            itemKeywordObj.set("billstatus", "A");
            itemKeywordObj.set("aos_orgid", orgid);
            itemKeywordObj.set("aos_itemid", itemid);
            itemKeywordObj.set("aos_itemname", itemname);
            itemKeywordObj.set("aos_category1", category1);
            itemKeywordObj.set("aos_category2", category2);
            itemKeywordObj.set("aos_category3", category3);
            saveList.add(itemKeywordObj);
            SaveUtils.SaveEntity("aos_mkt_keyword",saveList,false);
        }
        SaveUtils.SaveEntity("aos_mkt_keyword",saveList,true);
    }

    /**
     * 同步关键词库SKU清单
     */
    private void syncItemList() {
        // 1.查询关键词库中已存在的SKU清单信息
        DynamicObjectCollection pointRows = QueryServiceHelper.query("aos_mkt_point", "aos_orgid aos_orgid,aos_itementity.aos_itemid aos_itemid", null);
        Set<String> pointItemSet = new HashSet<>();
        for (DynamicObject obj:pointRows) {
            String orgid = obj.getString("aos_orgid");
            String itemid = obj.getString("aos_itemid");
            pointItemSet.add(orgid + "~" + itemid);
        }
        // 2.查询所有的关键词库中的信息
        String selectFields = "aos_orgid,aos_category1,aos_category2,aos_category3,aos_itemnamecn,aos_itementity.aos_itemid,aos_itementity.aos_productnum,aos_itementity.aos_picture1,aos_itementity.aos_synctime";
        DynamicObject[] pointArray = BusinessDataServiceHelper.load("aos_mkt_point", selectFields, null);
        List<DynamicObject> saveList = new ArrayList<>(5000);
        for (DynamicObject objPoints:pointArray) {
            // 根据国别+品类+品名获取SKU
            DynamicObject orgEntry = objPoints.getDynamicObject("aos_orgid");
            if (orgEntry==null) {
                continue;
            }
            String category1 = objPoints.getString("aos_category1");
            String category2 = objPoints.getString("aos_category2");
            String category3 = objPoints.getString("aos_category3");
            String itemname = objPoints.getString("aos_itemnamecn");

            // 查询SKU关键词库中的品类+品名符合的SKU
            DynamicObjectCollection itemList = QueryServiceHelper.query("aos_mkt_keyword", "aos_orgid,aos_itemid,aos_itemid.number aos_itemnum,aos_itemid.aos_productno", new QFilter[]{
                    new QFilter("aos_orgid", QCP.equals, orgEntry.getString("id")),
                    new QFilter("aos_category1", QCP.equals, category1),
                    new QFilter("aos_category2", QCP.equals, category2),
                    new QFilter("aos_category3", QCP.equals, category3),
                    new QFilter("aos_itemname", QCP.equals, itemname),
            });
            DynamicObjectCollection itemEntity = objPoints.getDynamicObjectCollection("aos_itementity");
            for (DynamicObject obj:itemList) {
                String itemid = obj.getString("aos_itemid");
                // 如果关键词SKU清单中已存在 不新增
                if (pointItemSet.contains(orgEntry.getString("id") + "~" + itemid)) {
                    continue;
                }

                String itemnum = obj.getString("aos_itemnum");

                DynamicObject dynamicObject = itemEntity.addNew();
                dynamicObject.set("aos_itemid",itemid);
                dynamicObject.set("aos_picture1", "https://cls3.s3.amazonaws.com/" + itemnum + "/1-1.jpg");
            }
            saveList.add(objPoints);
            SaveUtils.SaveEntity("aos_mkt_point",saveList,false);
        }
        SaveUtils.SaveEntity("aos_mkt_point",saveList,true);
    }

    /**
     * 同步关键词中的是否新品
     */
    private void syncItemStatus(){
        //获取所有新品的物料
        ItemDao itemDao = new ItemDaoImpl();
        QFilter filter = new QFilter("aos_contryentry.aos_contryentrystatus","=","E");
        String selectFields = "id,aos_contryentry.aos_nationality aos_nationality";
        DynamicObjectCollection itemEntity = itemDao.listItemObj(selectFields, filter, null);
        List<String> newItem = itemEntity.stream().map(dy -> dy.getString("id") + "/" + dy.getString("aos_nationality"))
                .distinct()
                .collect(Collectors.toList());

        QFBuilder builder = new QFBuilder();
        builder.add("aos_orgid",QFilter.is_notnull,null);
        builder.add("aos_itemid",QFilter.is_notnull,null);
        DynamicObject[] keywordArray = BusinessDataServiceHelper.load("aos_mkt_keyword", "aos_orgid,aos_itemid,aos_new", builder.toArray());
        List<DynamicObject> updateEntity = new ArrayList<>(5000);
        for (DynamicObject keyword : keywordArray) {
            //国别 或者 物料为空 跳过
            if (keyword.getDynamicObject("aos_orgid")==null || keyword.getDynamicObject("aos_itemid")==null){
                continue;
            }
            String orgid = keyword.getDynamicObject("aos_orgid").getString("id");
            String itemId = keyword.getDynamicObject("aos_itemid").getString("id");
            String key = itemId+"/"+orgid;
            if (newItem.contains(key) && !keyword.getBoolean("aos_new")){
                    keyword.set("aos_new",true);
                    updateEntity.add(keyword);
            }
            else if (!newItem.contains(key) && keyword.getBoolean("aos_new")){
                keyword.set("aos_new",false);
                updateEntity.add(keyword);
            }
            SaveUtils.UpdateEntity(updateEntity,false);
        }
        SaveUtils.UpdateEntity(updateEntity,true);
    }

}
