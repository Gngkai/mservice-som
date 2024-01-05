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
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/**
 * Date:2024/1/4 14:31
 * 
 * @author aosom
 */
public class AosNewItemPrtInit extends AbstractTask {
    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        process();
    }

    private void process() {
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        String dateStr = writeFormat.format(today.getTime());
        DeleteServiceHelper.delete("aos_newitem_prt", new QFilter("createtime", QCP.large_equals, dateStr).toArray());
        DynamicObjectCollection itemS = QueryServiceHelper.query("bd_material",
            "id itemid," + "aos_contryentry.aos_nationality orgid," + "aos_contryentry.aos_contrybrand brandid,"
                + "aos_contryentry.aos_firstindate aos_firstindate",
            new QFilter("aos_type", QCP.equals, "A").and("aos_contryentry.aos_firstindate", QCP.large_equals, dateStr)
                .toArray());
        for (DynamicObject item : itemS) {
            DynamicObject newItem = BusinessDataServiceHelper.newDynamicObject("aos_newitem_prt");
            newItem.set("aos_orgid", item.getString("orgid"));
            newItem.set("aos_itemid", item.getString("itemid"));
            newItem.set("aos_brandid", item.getString("brandid"));
            newItem.set("aos_rcv_date", item.get("aos_firstindate"));
            newItem.set("createtime", new Date());
            SaveServiceHelper.save(new DynamicObject[] {newItem});
        }
    }
}
