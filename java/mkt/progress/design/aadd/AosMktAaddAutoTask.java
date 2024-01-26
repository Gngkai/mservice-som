package mkt.progress.design.aadd;

import java.util.Date;
import java.util.Map;

import common.fnd.FndDate;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

/**
 * @author aosom
 * @version 高级A+自动提交-调度任务类
 */
public class AosMktAaddAutoTask extends AbstractTask {
    @Override
    public void execute(RequestContext arg0, Map<String, Object> arg1) throws KDException {
        doOperate();
    }

    private void doOperate() {
        Date today = new Date();
        DynamicObject[] aosMktAaddS = BusinessDataServiceHelper.load("aos_mkt_aadd",
            "aos_status,aos_osdate,aos_org,aos_oueditor,aos_design,aos_user,aos_auto",
            new QFilter("aos_status", QCP.equals, "海外确认").toArray());
        for (DynamicObject aosMktAadd : aosMktAaddS) {
            Date aosOsdate = aosMktAadd.getDate("aos_osdate");
            if (FndDate.add_NoWeekendDays(aosOsdate, 3).compareTo(today) > 0) {
                continue;
            }
            String aosOrg = aosMktAadd.getString("aos_org");
            // 小语种 自动 到文案确认
            if ("DE,FR,IT,ES".contains(aosOrg)) {
                aosMktAadd.set("aos_user", aosMktAadd.get("aos_oueditor"));
                aosMktAadd.set("aos_status", "文案确认");
            } else {
                aosMktAadd.set("aos_user", aosMktAadd.get("aos_design"));
                aosMktAadd.set("aos_status", "设计制作");
            }
            // 人为提交
            aosMktAadd.set("aos_auto", "SYSTEM");
        }
        SaveServiceHelper.save(aosMktAaddS);
    }
}