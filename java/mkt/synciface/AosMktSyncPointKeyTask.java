package mkt.synciface;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import common.fnd.FndDate;
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

/**
 * @author aosom
 * @version 关键词建议价同步-调度任务类
 */
public class AosMktSyncPointKeyTask extends AbstractTask {
    public static void orgLoop() {
        DeleteServiceHelper.delete("aos_base_pointadv", null);
        // 海外公司
        QFilter overseaFlag = new QFilter("aos_is_oversea_ou.number", "=", "Y");
        QFilter overseaFlag2 = new QFilter("aos_isomvalid", "=", true);
        QFilter[] filtersOu = new QFilter[] {overseaFlag, overseaFlag2};
        DynamicObjectCollection bdCountry = QueryServiceHelper.query("bd_country", "id,number", filtersOu);
        for (DynamicObject ou : bdCountry) {
            doOperate(ou.getString("number"));
        }
    }

    public static void doOperate(String ou) {
        Map<String, Object> map = generateCountry();
        Object pOrgId = map.get(ou);
        Date today = FndDate.zero(new Date());
        DynamicObject aosBaseAdvrpt = BusinessDataServiceHelper.newDynamicObject("aos_base_pointadv");
        aosBaseAdvrpt.set("billstatus", "A");
        aosBaseAdvrpt.set("aos_date", today);
        aosBaseAdvrpt.set("aos_org", pOrgId);
        DynamicObjectCollection aosEntryentityS = aosBaseAdvrpt.getDynamicObjectCollection("entryentity");
        String org = getOrg(ou);
        DynamicObjectCollection rptS = QueryServiceHelper.query("aos_base_pointadv_dp",
            "aos_ad_name,aos_keyword,aos_match_type,aos_bid_suggest,aos_bid_rangestart,aos_bid_rangeend",
            new QFilter("aos_org", QCP.equals, org).toArray());
        for (DynamicObject rpt : rptS) {
            DynamicObject aosEntryentity = aosEntryentityS.addNew();
            aosEntryentity.set("aos_orgid", pOrgId);
            aosEntryentity.set("aos_ad_name", rpt.get("aos_ad_name"));
            aosEntryentity.set("aos_keyword", rpt.get("aos_keyword"));
            aosEntryentity.set("aos_match_type", rpt.get("aos_match_type"));
            aosEntryentity.set("aos_bid_rangeend", rpt.get("aos_bid_suggest"));
            aosEntryentity.set("aos_bid_rangestart", rpt.get("aos_bid_rangestart"));
            aosEntryentity.set("aos_bid_suggest", rpt.get("aos_bid_rangeend"));
        }
        SaveServiceHelper.save(new DynamicObject[] {aosBaseAdvrpt});
    }

    public static String getOrg(String ou) {
        HashMap<String, String> orgMap = new HashMap<>(16);
        orgMap.put("US", "AOSOM LLC");
        orgMap.put("CA", "AOSOM CANADA INC.");
        orgMap.put("UK", "MH STAR UK LTD");
        orgMap.put("DE", "MH HANDEL GMBH");
        orgMap.put("FR", "MH FRANCE");
        orgMap.put("IT", "AOSOM ITALY SRL");
        orgMap.put("ES", "SPANISH AOSOM, S.L.");
        return orgMap.get(ou);
    }

    public static Map<String, Object> generateCountry() {
        Map<String, Object> map = new HashMap<>();
        DynamicObjectCollection dyns = QueryServiceHelper.query("bd_country", "id,number", null);
        for (DynamicObject d : dyns) {
            map.put(d.getString("number"), d.get("id"));
        }
        return map;
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        orgLoop();
    }
}
