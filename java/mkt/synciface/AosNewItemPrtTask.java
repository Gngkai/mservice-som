package mkt.synciface;

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

import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author aosom
 * @since 2024/1/4 14:31
 * @version 每日入库新品报表-调度任务类
 */
public class AosNewItemPrtTask extends AbstractTask {
    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        process();
    }
    private void process() {
        DynamicObjectCollection itemS = QueryServiceHelper.query("bd_material",
            "id itemid," + "aos_contryentry.aos_nationality orgid," + "aos_contryentry.aos_contrybrand brandid,"
                + "aos_contryentry.aos_firstindate aos_firstindate",
            new QFilter("aos_type", QCP.equals, "A").and("aos_contryentry.aos_firstindate", QCP.not_equals, null)
                .toArray());
        Set<String> prtS = QueryServiceHelper.query("aos_newitem_prt", "aos_orgid,aos_itemid", null).stream()
            .map(obj -> obj.getString("aos_orgid") + "~" + obj.getString("aos_itemid")).collect(Collectors.toSet());
        for (DynamicObject item : itemS) {
            DynamicObject newItem = BusinessDataServiceHelper.newDynamicObject("aos_newitem_prt");
            String orgid = item.getString("orgid");
            String itemid = item.getString("itemid");
            String key = orgid + "~" + itemid;
            if (prtS.contains(key)) {
                continue;
            }
            newItem.set("aos_orgid", orgid);
            newItem.set("aos_itemid", itemid);
            newItem.set("aos_brandid", item.getString("brandid"));
            newItem.set("aos_rcv_date", item.get("aos_firstindate"));
            newItem.set("createtime", new Date());
            SaveServiceHelper.save(new DynamicObject[] {newItem});
        }
    }
}
