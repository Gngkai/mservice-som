package mkt.progress.design;

import java.util.Map;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;

/**
 * @author aosom
 * @version 设计完成表自动提交-调度任务类
 */
public class AosMktDesignCmpTask extends AbstractTask {

    public static void doOperate() {
        DynamicObjectCollection aosMktDesignCmpS = QueryServiceHelper.query("aos_mkt_designcmp", "id",
            new QFilter("aos_status", QCP.in, new String[] {"销售确认", "设计传图"}).toArray());
        for (DynamicObject aosMktDesignCmp : aosMktDesignCmpS) {
            DynamicObject single = BusinessDataServiceHelper.loadSingle(aosMktDesignCmp.get("id"), "aos_mkt_designcmp");
            new AosMktDesignCmpBill().aosSubmit(single, "B");
        }
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        doOperate();
    }
}