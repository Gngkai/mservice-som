package mkt.progress.design.aadd;

import common.Cux_Common_Utl;
import common.fnd.FndGlobal;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Author:aosom
 * Date:2023/12/7 8:06
 * 每日十点定时刷新存在国别首次入库日期的销售上线单据
 */
public class AosMktAddSaleSync extends AbstractTask {
    public final static String system = Cux_Common_Utl.SYSTEM;

    public static Map<String, Date> getFirstDate() {
        Map<String, Date> firstDateMap = new HashMap<>();
        DynamicObjectCollection bdMaterialS = QueryServiceHelper.query("bd_material",
                "id,aos_nationality.number aos_orgnum," +
                        "aos_contryentry.aos_firstindate aos_firstindate",
                new QFilter("aos_contryentry.aos_contryentrystatus", QCP.not_equals, "F")
                        .and("aos_contryentry.aos_contryentrystatus", QCP.not_equals, "H")
                        .toArray());
        for (DynamicObject bdMaterial : bdMaterialS) {
            String key = bdMaterial.getString("aos_orgnum") + "~"
                    + bdMaterial.getString("id");
            Date aos_firstindate = bdMaterial.getDate("aos_firstindate");
            if (FndGlobal.IsNotNull(aos_firstindate)) {
                firstDateMap.put(key, aos_firstindate);
            }
        }
        return firstDateMap;
    }

    @Override
    public void execute(RequestContext arg0, Map<String, Object> arg1) throws KDException {
        process();
    }

    private void process() {
        Map<String, Date> firstDateMap = getFirstDate();
        DynamicObject[] aosMktAaddS = BusinessDataServiceHelper.load("aos_mkt_aadd",
                "aos_org,aos_entryentity.aos_itemid aos_itemid,aos_firstindate,aos_user," +
                        "aos_sale",
                new QFilter("aos_status", QCP.equals, "销售上线")
                        .and("aos_user", QCP.equals, system).toArray(), null);
        for (DynamicObject aosMktAadd : aosMktAaddS) {
            String key = aosMktAadd.getString("aos_org") +
                    "~" + aosMktAadd.getString("aos_user");
            if (firstDateMap.containsKey(key)) {
                aosMktAadd.set("aos_user",aosMktAadd.get("aos_sale"));
                aosMktAadd.set("aos_firstindate", firstDateMap.get(key));
                aosMktAadd.set("aos_salerece_date", new Date());
            }
        }
    }
}
