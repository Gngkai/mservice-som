package mkt.synciface;

import common.CommonMktListing;
import common.fnd.FndGlobal;
import common.fnd.FndMsg;
import common.sal.util.QFBuilder;
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
import mkt.progress.ProgressUtil;

import java.util.*;

/**
 * @author create by gk
 * @date 2023/7/18 17:20
 * @action 3d产品设计单同步数据（出运日期，质检完成日期，大货样封样状态）
 */
public class aos_mkt_sync_3dsign extends AbstractTask {
    public static void process2() {
        // 初始化物料数据
        HashMap<String, HashMap<String, String>> itemMap = new HashMap<>();
        DynamicObjectCollection itemS = QueryServiceHelper.query("bd_material",
                "id," +
                        "aos_developer.name aos_developer," +
                        "name," +
                        "aos_specification_cn," +
                        "aos_seting_cn," +
                        "aos_sellingpoint",
                new QFilter("aos_protype", QCP.equals, "N"
                ).toArray());

        FndMsg.debug("size1:" + itemS.size());
        for (DynamicObject item : itemS) {
            String itemId = item.getString("id");
            String developer = item.getString("aos_developer");
            String name = item.getString("name");
            String specification = item.getString("aos_specification_cn");
            String seting = item.getString("aos_seting_cn");
            String sellPoint = item.getString("aos_sellingpoint");
            HashMap<String, String> data = itemMap.getOrDefault(itemId, new HashMap<>());
            data.put("aos_developer", developer);// 现开发人
            data.put("name", name);// 产品品名
            data.put("aos_specification_cn", specification);// 产品规格
            data.put("aos_seting_cn", seting);// 产品属性
            data.put("aos_sellingpoint", sellPoint);// 产品卖点
            itemMap.put(itemId, data);
        }

        DynamicObject[] designS =
                BusinessDataServiceHelper
                        .load("aos_mkt_3design",
                                "aos_entryentity.aos_itemid," +
                                        "aos_entryentity.aos_is_saleout," +
                                        "aos_entryentity.aos_itemname," +
                                        "aos_entryentity.aos_orgtext," +
                                        "aos_entryentity.aos_specification," +
                                        "aos_entryentity.aos_seting1," +
                                        "aos_entryentity.aos_sellingpoint", new QFilter("aos_status",
                                        QCP.not_equals,
                                        "已完成").toArray());
        FndMsg.debug("size2:" + designS.length);
        List<DynamicObject> updateEntity = new ArrayList<>(designS.length);
        int i = 0;
        for (DynamicObject design : designS) {
            FndMsg.debug("i:" + i);
            DynamicObjectCollection entryS = design.getDynamicObjectCollection("aos_entryentity");
            for (DynamicObject entry : entryS) {
                String itemId = entry.getDynamicObject("aos_itemid").getString("id");
                HashMap<String, String> data = itemMap.get(itemId);
                if (FndGlobal.IsNotNull(data)) {
                    entry.set("aos_itemname", itemMap.get("name"));
                    entry.set("aos_specification", itemMap.get("aos_specification_cn"));
                    entry.set("aos_seting1", itemMap.get("aos_seting_cn"));
                    entry.set("aos_sellingpoint", itemMap.get("aos_sellingpoint"));
                    entry.set("aos_is_saleout", ProgressUtil.Is_saleout(itemId));
                    entry.set("aos_orgtext", CommonMktListing.getOrderOrg(itemId));
                }
            }
            updateEntity.add(design);
            i++;
        }
        DynamicObject[] array = updateEntity.toArray(new DynamicObject[0]);
        SaveServiceHelper.update(array);
    }

    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        //查找大货封样的3d产品设计单
        process1();
        // 刷新物料信息
        process2();
    }

    private void process1() {
        QFBuilder builder = new QFBuilder();
        builder.add("aos_status", "=", "大货样封样");
        StringJoiner select = new StringJoiner(","), photoSelect = new StringJoiner(",");
        select.add("id");
        select.add("aos_status");
        select.add("aos_orignbill");
        select.add("aos_quainscomdate");
        select.add("aos_shipdate");
        select.add("aos_user");
        select.add("aos_3der");

        photoSelect.add("aos_shipdate");
        photoSelect.add("aos_quainscomdate");
        photoSelect.add("aos_itemid");
        photoSelect.add("aos_ponumber");

        DynamicObject[] designs = BusinessDataServiceHelper.load("aos_mkt_3design", select.toString(), builder.toArray());
        //记录修改的单据
        List<DynamicObject> updateEntity = new ArrayList<>(designs.length);
        for (DynamicObject design : designs) {
            String photoBill = design.getString("aos_orignbill");
            //拍照需求表相关的信息
            builder.clear();
            builder.add("billno", "=", photoBill);
            DynamicObject dy_photo = QueryServiceHelper.queryOne("aos_mkt_photoreq", photoSelect.toString(), builder.toArray());
            if (dy_photo == null) {
                continue;
            }
            boolean invalidDate = FndGlobal.IsNull(dy_photo.get("aos_shipdate")) && FndGlobal.IsNull(dy_photo.get("aos_quainscomdate"));
            //出运日期和质检日期都无效
            if (invalidDate) {
                //查找封样单信息
                builder.clear();
                builder.add("aos_item", "=", dy_photo.get("aos_itemid"));
                builder.add("aos_contractnowb", "=", dy_photo.get("aos_ponumber"));
                builder.add("aos_largegood", QFilter.is_notnull, null);
                boolean sealsample = QueryServiceHelper.exists("aos_sealsample", builder.toArray());
                if (!sealsample) {
                    continue;
                }
            } else {
                design.set("aos_shipdate", dy_photo.get("aos_shipdate"));
                design.set("aos_quainscomdate", dy_photo.get("aos_quainscomdate"));
            }
            design.set("aos_status", "新建");
            design.set("aos_user", design.get("aos_3der"));
            updateEntity.add(design);
        }
        DynamicObject[] array = updateEntity.toArray(new DynamicObject[0]);
        SaveServiceHelper.update(array);
    }
}
