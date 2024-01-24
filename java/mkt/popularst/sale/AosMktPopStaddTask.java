package mkt.popularst.sale;

import common.Cux_Common_Utl;
import common.fnd.FndLog;
import kd.bos.algo.DataSet;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import kd.bos.orm.ORM;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.threads.ThreadPools;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * @author create by gk
 * @version ST加回-调度任务类
 */
public class AosMktPopStaddTask extends AbstractTask {
    public static void stInit() {
        // 查找ppc操作平台的数据
        LocalDate now = LocalDate.now();
        QFilter filterCode = new QFilter("aos_code", "=", "aos_mkt_ppcst_init");
        QFilter filterValue = new QFilter("aos_value", "!=", "");
        DynamicObject dyValue =
            QueryServiceHelper.queryOne("aos_sync_params", "aos_value", new QFilter[] {filterCode, filterValue});
        if (dyValue != null) {
            now = LocalDate.parse(dyValue.getString("aos_value"));
        }
        QFilter filterS = new QFilter("aos_date", ">=", now.toString());
        QFilter filterOrg = new QFilter("aos_orgid", "!=", "");
        QFilter[] qfs = new QFilter[] {filterOrg, filterS};
        DynamicObjectCollection dyc =
            QueryServiceHelper.query("aos_mkt_pop_ppcst", "id,aos_billno,aos_orgid,aos_orgid.number orgNumber", qfs);
        for (DynamicObject dy : dyc) {
            CreateStAdd stAdd = new CreateStAdd(dy);
            ThreadPools.executeOnce("", stAdd);
        }
    }

    /** 查找ppc数据 **/
    public static Map<String, List<DynamicObject>> queryPpc(Object ppcStId, Object groupName) {
        QFilter filterId = new QFilter("id", "=", ppcStId);
        QFilter filterGroup = new QFilter("aos_sal_group", "=", groupName);
        QFilter filterReason = new QFilter("aos_reason", "=", "ROI");
        QFilter[] qfs = new QFilter[] {filterId, filterGroup, filterReason};
        StringJoiner str = new StringJoiner(",");
        str.add("aos_productno");
        str.add("aos_itemnumer");
        str.add("aos_category1");
        str.add("aos_category2");
        str.add("aos_category3");
        str.add("aos_avadays");
        str.add("aos_eliminatedate");
        str.add("aos_keyword");
        str.add("aos_match_type");
        return QueryServiceHelper.query("aos_mkt_ppcst_data", str.toString(), qfs).stream()
            .collect(Collectors.groupingBy(dy -> dy.getString("aos_productno") + "/" + dy.getString("aos_itemnumer")));
    }

    /** 查找关键词报告中的数据 **/
    public static Map<String, DynamicObject> queryPointPt(Object orgid) {
        LocalDate now = LocalDate.now();
        QFilter filterDay = new QFilter("aos_date", ">=", now.toString());
        QFilter filterOrg = new QFilter("aos_orgid", "=", orgid);
        QFilter filterRow = new QFilter("aos_entryentity.aos_date_l", ">=", now.minusDays(30).toString());
        QFilter[] qfs = new QFilter[] {filterDay, filterRow, filterOrg};
        StringJoiner str = new StringJoiner(",");
        str.add("aos_entryentity.aos_cam_name aos_cam_name");
        str.add("aos_entryentity.aos_ad_name aos_ad_name");
        str.add("aos_entryentity.aos_match_type aos_match_type");
        str.add("aos_entryentity.aos_search_term aos_search_term");
        str.add("aos_entryentity.aos_impressions aos_impressions");
        str.add("aos_entryentity.aos_clicks aos_clicks");
        str.add("aos_entryentity.aos_spend aos_spend");
        str.add("aos_entryentity.aos_sales/aos_entryentity.aos_spend aos_roi");
        QueryServiceHelper.query("aos_base_pointrpt", str.toString(), qfs);
        String algo = "mkt.popularst.adjust_s.aos_mkt_pop_stadd_init.queryPointPt";
        DataSet ds = QueryServiceHelper.queryDataSet(algo, "aos_base_pointrpt", str.toString(), qfs, null);
        ds = ds.groupBy(new String[] {"aos_cam_name", "aos_ad_name", "aos_match_type", "aos_search_term"})
            .sum("aos_impressions").sum("aos_clicks").sum("aos_spend").sum("aos_roi").finish();
        DynamicObjectCollection dyc = ORM.create().toPlainDynamicObjectCollection(ds);
        ds.close();
        return dyc.stream()
            .collect(Collectors.toMap(
                dy -> dy.getString("aos_cam_name") + "/" + dy.getString("aos_ad_name") + "/"
                    + dy.getString("aos_match_type") + "/" + dy.getString("aos_search_term"),
                dy -> dy, (key1, key2) -> key1));
    }

    /** 子单据体填充 **/
    public static void setSubEntValue(DynamicObject dyEntRow, List<DynamicObject> listPpc,
        Map<String, DynamicObject> mapPoint) {
        DynamicObjectCollection dycSubEnt = dyEntRow.getDynamicObjectCollection("aos_subentryentity");
        for (DynamicObject dyPpc : listPpc) {
            StringJoiner key = new StringJoiner("/");
            key.add(dyEntRow.getString("aos_productno"));
            key.add(dyEntRow.getString("aos_itemnumer"));
            DynamicObject dySubRow = dycSubEnt.addNew();
            dySubRow.set("aos_keyword", dyPpc.get("aos_keyword"));
            key.add(dyPpc.getString("aos_keyword"));
            dySubRow.set("aos_match_type", dyPpc.get("aos_match_type"));
            key.add(dyPpc.getString("aos_match_type"));
            if (mapPoint.containsKey(key.toString())) {
                DynamicObject dyPoint = mapPoint.get(key.toString());
                dySubRow.set("aos_clicks", dyPoint.get("aos_clicks"));
                dySubRow.set("aos_spend", dyPoint.get("aos_spend"));
                dySubRow.set("aos_impressions", dyPoint.get("aos_impressions"));
                dySubRow.set("aos_roi_r", dyPoint.get("aos_roi"));
            }
        }
    }

    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        stInit();
    }

    static class CreateStAdd implements Runnable {
        Object ppcStId, org;
        String orgNumber, billno;

        CreateStAdd(DynamicObject dy) {
            this.ppcStId = dy.get("id");
            this.org = dy.get("aos_orgid");
            orgNumber = dy.getString("orgNumber");
            billno = dy.getString("aos_billno");
        }

        @Override
        public void run() {
            try {
                // 查找国别下的所有组别
                QFilter qfTheOrg = new QFilter("aos_org", "=", org);
                QFilter qfEnable = new QFilter("enable", "=", "1");
                QFilter[] qfs = new QFilter[] {qfTheOrg, qfEnable};
                DynamicObjectCollection dycGroup = QueryServiceHelper.query("aos_salgroup", "id,name", qfs);
                for (DynamicObject dyGroup : dycGroup) {
                    String bill = orgNumber + dyGroup.getString("name") + billno;
                    doOperate(bill, dyGroup);
                }
            } catch (KDException e) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                Log log = LogFactory.getLog("aos_mkt_pop_stadd_init");
                log.error(sw.toString());
                e.printStackTrace();
                throw e;
            }
        }

        public void doOperate(String bill, DynamicObject dy_goup) {
            FndLog log = FndLog.init("st销售加回" + bill, String.valueOf(LocalDateTime.now()));
            DynamicObject dyStAdd = BusinessDataServiceHelper.newDynamicObject("aos_mkt_pop_stadd");
            dyStAdd.set("aos_orgid", org);
            dyStAdd.set("billno", bill);
            dyStAdd.set("aos_groupid", dy_goup.get("id"));
            dyStAdd.set("aos_makeby", Cux_Common_Utl.SYSTEM);
            dyStAdd.set("aos_makedate", new Date());
            dyStAdd.set("billstatus", "A");
            Map<String, List<DynamicObject>> mapPpc = queryPpc(ppcStId, dy_goup.get("id"));
            log.add("ppc数据： " + mapPpc.size());
            Map<String, DynamicObject> mapPoint = queryPointPt(org);
            log.add("关键词报告数据：  " + mapPoint.size());
            DynamicObjectCollection dycStEnt = dyStAdd.getDynamicObjectCollection("entryentity");
            for (Map.Entry<String, List<DynamicObject>> entry : mapPpc.entrySet()) {
                String[] split = entry.getKey().split("/");
                DynamicObject dyEntNewRow = dycStEnt.addNew();
                dyEntNewRow.set("aos_productno", split[0]);
                dyEntNewRow.set("aos_itemnumer", split[1]);
                DynamicObject dyPpc = entry.getValue().get(0);
                dyEntNewRow.set("aos_category1", dyPpc.get("aos_category1"));
                dyEntNewRow.set("aos_category2", dyPpc.get("aos_category2"));
                dyEntNewRow.set("aos_category3", dyPpc.get("aos_category3"));
                dyEntNewRow.set("aos_avadays", dyPpc.get("aos_avadays"));
                dyEntNewRow.set("aos_eliminate", dyPpc.get("aos_eliminatedate"));
                setSubEntValue(dyEntNewRow, entry.getValue(), mapPoint);
            }
            if (dycStEnt.size() > 0) {
                SaveServiceHelper.save(new DynamicObject[] {dyStAdd});
            }
            log.finnalSave();
        }
    }
}
