package mkt.data.keyword;

import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.util.ExceptionUtils;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * lch
 * 2022-4-15
 */
public class ItemKeywordListPlugin extends AbstractTask {
    private static final Log logger = LogFactory.getLog(ItemKeywordListPlugin.class);
    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        // 同步SKU关键词库
        syncItemKeyword();
        // 同步关键词库
        syncItemList();
    }

    private Map<String, String> getAllItemCategory() {
        QFilter qFilter = new QFilter("standard.number", QCP.equals, "JBFLBZ");
        String selectFields = "material,group.name categoryname";
        DynamicObjectCollection list = QueryServiceHelper.query("bd_materialgroupdetail", selectFields, qFilter.toArray());
        return list.stream().collect(Collectors.toMap(
                obj -> obj.getString("material"),
                obj -> obj.getString("categoryname"),
                (k1, k2) -> k1));
    }
    private void syncItemKeyword() {
        Map<String, String> allItemCategory = getAllItemCategory();
        String selectFields = "aos_contryentry.aos_nationality aos_orgid," +
                "id aos_itemid," +
                "name aos_itemname";
        DynamicObjectCollection list = QueryServiceHelper.query("bd_material", selectFields, new QFilter[]{
                new QFilter("aos_contryentry.aos_nationality", QCP.is_notnull, null),
                new QFilter("aos_contryentry.aos_contryentrystatus", QCP.equals, "A")
        });

        // 查询关键词库中已有的数据
        DynamicObjectCollection aos_mkt_keyword = QueryServiceHelper.query("aos_mkt_keyword", "aos_orgid,aos_itemid", null);
        Set<String> keywordExists = new HashSet<>();
        for (DynamicObject obj:aos_mkt_keyword) {
            String aos_orgid = obj.getString("aos_orgid");
            String aos_itemid = obj.getString("aos_itemid");
            keywordExists.add(aos_orgid + "~" + aos_itemid);
        }

        for (DynamicObject obj:list) {
            String aos_orgid = obj.getString("aos_orgid");
            String aos_itemid = obj.getString("aos_itemid");
            String aos_itemname = obj.getString("aos_itemname");
            String aos_category1 = "";
            String aos_category2 = "";
            String aos_category3 = "";

            // 如果SKU关键词库中已存在
            if (keywordExists.contains(aos_orgid + "~" + aos_itemid)) continue;

            try {
                String aos_category = allItemCategory.get(aos_itemid);
                if (aos_category == null) continue;
                String[] categoryArr = aos_category.split(",");
                if (categoryArr.length > 0) {
                    aos_category1 =  categoryArr[0];
                }
                if (categoryArr.length > 1) {
                    aos_category2 =  categoryArr[1];
                }
                if (categoryArr.length > 2) {
                    aos_category3 =  categoryArr[2];
                }

                // 新建一单
                DynamicObject itemKeywordObj = BusinessDataServiceHelper.newDynamicObject("aos_mkt_keyword");
                itemKeywordObj.set("billstatus", "A");
                itemKeywordObj.set("aos_orgid", aos_orgid);
                itemKeywordObj.set("aos_itemid", aos_itemid);
                itemKeywordObj.set("aos_itemname", aos_itemname);
                itemKeywordObj.set("aos_category1", aos_category1);
                itemKeywordObj.set("aos_category2", aos_category2);
                itemKeywordObj.set("aos_category3", aos_category3);
                SaveServiceHelper.save(new DynamicObject[]{itemKeywordObj});
            } catch (Exception e) {
                e.printStackTrace();
                logger.error("mkt.data.keyword.ItemKeywordListPlugin.syncItemKeyword:" + ExceptionUtils.getExceptionStackTraceMessage(e)
                        + "\r\n 物料:" + aos_itemid
                        + "\r\n 国别:" + aos_orgid);
            }
        }
    }

    /**
     * 同步关键词库SKU清单
     */
    private void syncItemList() {
        // 1.查询关键词库中已存在的SKU清单信息
        DynamicObjectCollection aos_mkt_point = QueryServiceHelper.query("aos_mkt_point", "aos_orgid aos_orgid,aos_itementity.aos_itemid aos_itemid", null);
        Set<String> pointItemSet = new HashSet<>();
        for (DynamicObject obj:aos_mkt_point) {
            String aos_orgid = obj.getString("aos_orgid");
            String aos_itemid = obj.getString("aos_itemid");
            pointItemSet.add(aos_orgid + "~" + aos_itemid);
        }
        // 2.查询所有的关键词库中的信息
        Calendar instance = Calendar.getInstance();
        String selectFields = "aos_orgid,aos_category1,aos_category2,aos_category3,aos_itemnamecn,aos_itementity.aos_itemid,aos_itementity.aos_productnum,aos_itementity.aos_picture1,aos_itementity.aos_synctime";
        DynamicObject[] aos_mkt_points = BusinessDataServiceHelper.load("aos_mkt_point", selectFields, null);
        for (DynamicObject objPoints:aos_mkt_points) {
            // 根据国别+品类+品名获取SKU
            DynamicObject aos_orgid = objPoints.getDynamicObject("aos_orgid");
            String aos_category1 = objPoints.getString("aos_category1");
            String aos_category2 = objPoints.getString("aos_category2");
            String aos_category3 = objPoints.getString("aos_category3");
            String aos_itemname = objPoints.getString("aos_itemnamecn");

            // 查询SKU关键词库中的品类+品名符合的SKU
            DynamicObjectCollection itemList = QueryServiceHelper.query("aos_mkt_keyword", "aos_itemid,aos_itemid.number aos_itemnum,aos_itemid.aos_productno", new QFilter[]{
                    new QFilter("aos_orgid", QCP.equals, aos_orgid.getString("id")),
                    new QFilter("aos_category1", QCP.equals, aos_category1),
                    new QFilter("aos_category2", QCP.equals, aos_category2),
                    new QFilter("aos_category3", QCP.equals, aos_category3),
                    new QFilter("aos_itemname", QCP.equals, aos_itemname),
            });
            DynamicObjectCollection aos_itementity = objPoints.getDynamicObjectCollection("aos_itementity");
            for (DynamicObject obj:itemList) {
                String aos_itemid = obj.getString("aos_itemid");

                if (pointItemSet.contains(aos_orgid.getString("id") + "~" + aos_itemid)) continue;// 如果关键词SKU清单中已存在 不新增

                String aos_productno = obj.getString("aos_itemid.aos_productno");
                String aos_itemnum = obj.getString("aos_itemnum");

                DynamicObject dynamicObject = aos_itementity.addNew();
                dynamicObject.set("aos_itemid", aos_itemid);
                dynamicObject.set("aos_productnum", aos_productno);
                dynamicObject.set("aos_picture1", "https://cls3.s3.amazonaws.com/" + aos_itemnum + "/1-1.jpg");
                dynamicObject.set("aos_synctime", instance.getTime());
            }
            SaveServiceHelper.save(new DynamicObject[]{objPoints});
        }
    }

}
