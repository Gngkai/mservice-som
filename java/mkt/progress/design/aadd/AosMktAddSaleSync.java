package mkt.progress.design.aadd;

import common.Cux_Common_Utl;
import common.fnd.FndGlobal;
import common.fnd.FndMsg;
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
import java.util.HashMap;
import java.util.Map;

/**
 * @author aosom
 * @since 2023/12/7 8:06
 * @version 每日十点定时刷新存在国别首次入库日期的销售上线单据
 */
public class AosMktAddSaleSync extends AbstractTask {
    public final static String SYSTEM = Cux_Common_Utl.SYSTEM;

    public static Map<String, Date> getFirstDate() {
        Map<String, Date> firstDateMap = new HashMap<>(16);
        DynamicObjectCollection bdMaterialS = QueryServiceHelper.query("bd_material",
            "id,aos_contryentry.aos_nationality.number aos_orgnum," + "aos_contryentry.aos_firstindate aos_firstindate",
            new QFilter("aos_contryentry.aos_contryentrystatus", QCP.not_equals, "F")
                .and("aos_contryentry.aos_contryentrystatus", QCP.not_equals, "H").toArray());
        for (DynamicObject bdMaterial : bdMaterialS) {
            String key = bdMaterial.getString("aos_orgnum") + "~" + bdMaterial.getString("id");
            Date aosFirstindate = bdMaterial.getDate("aos_firstindate");
            if (FndGlobal.IsNotNull(aosFirstindate)) {
                firstDateMap.put(key, aosFirstindate);
            }
        }
        return firstDateMap;
    }

    public static void process() {
        Map<String, Date> firstDateMap = getFirstDate();
        DynamicObject[] aosMktAaddS = BusinessDataServiceHelper.load("aos_mkt_aadd",
            "aos_org,aos_firstindate,aos_user," + "aos_sale,aos_entryentity.aos_itemid,aos_salerece_date",
            new QFilter("aos_status", QCP.equals, "销售上线").and("aos_user", QCP.equals, SYSTEM).toArray(), null);
        FndMsg.debug("size:" + aosMktAaddS.length);

        for (DynamicObject aosMktAadd : aosMktAaddS) {
            DynamicObjectCollection aosEntryEntity = aosMktAadd.getDynamicObjectCollection("aos_entryentity");

            String key = aosMktAadd.getString("aos_org") + "~"
                + aosEntryEntity.get(0).getDynamicObject("aos_itemid").getPkValue().toString();
            FndMsg.debug("key:" + key);
            if (firstDateMap.containsKey(key)) {
                FndMsg.debug("=====into contains=====");
                aosMktAadd.set("aos_user", aosMktAadd.get("aos_sale"));
                aosMktAadd.set("aos_firstindate", firstDateMap.get(key));
                aosMktAadd.set("aos_salerece_date", new Date());
            }
        }
        SaveServiceHelper.save(aosMktAaddS);
    }

    @Override
    public void execute(RequestContext arg0, Map<String, Object> arg1) throws KDException {
        process();
    }
}
