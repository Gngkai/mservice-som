package mkt.progress.design;

import java.util.Date;
import java.util.Map;

import common.fnd.FndDate;
import common.fnd.FndGlobal;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

/**
 * @author aosom
 * @version Slogan自动提交-调度任务类
 */
public class AosMktSloganAutoTask extends AbstractTask {
    @Override
    public void execute(RequestContext arg0, Map<String, Object> arg1) throws KDException {
        doOperate();
    }

    private void doOperate() {
        DynamicObjectCollection aosMktSloganS = QueryServiceHelper.query("aos_mkt_slogan", "id,aos_osdate",
            new QFilter("aos_status", QCP.equals, "海外翻译").toArray());
        for (DynamicObject aosMktSlogan : aosMktSloganS) {
            Date aosOsdate = aosMktSlogan.getDate("aos_osdate");
            if (FndDate.add_NoWeekendDays(aosOsdate, 3).compareTo(new Date()) < 0) {
                DynamicObject aosMktSloganO =
                    BusinessDataServiceHelper.loadSingle(aosMktSlogan.getString("id"), "aos_mkt_slogan");
                String aosCategory1 = aosMktSloganO.getString("aos_category1");
                String aosCategory2 = aosMktSloganO.getString("aos_category2");
                String aosLang = aosMktSloganO.getString("aos_lang");
                DynamicObject aosMktProgorguser = QueryServiceHelper.queryOne("aos_mkt_progorguser", "aos_oueditor",
                    new QFilter("aos_category1", "=", aosCategory1).and("aos_category2", "=", aosCategory2)
                        .and("aos_orgid.number", "=", aosLang).toArray());
                if (FndGlobal.IsNull(aosMktProgorguser) || FndGlobal.IsNull(aosMktProgorguser.get("aos_oueditor"))) {
                    continue;
                }
                aosMktSloganO.set("aos_status", "翻译");
                aosMktSloganO.set("aos_osubmit", "系统提交");
                aosMktSloganO.set("aos_user", aosMktProgorguser.get("aos_oueditor"));
                SaveServiceHelper.saveOperate("aos_mkt_slogan", new DynamicObject[] {aosMktSloganO},
                    OperateOption.create());
            }
        }
    }
}
