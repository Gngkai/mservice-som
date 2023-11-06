package mkt.popularst.adjust_s;

import common.Cux_Common_Utl;
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
import mkt.common.MKTCom;

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
 * @date 2023/1/12 16:58
 * @action st加回调度任务
 */
public class aos_mkt_pop_stadd_init extends AbstractTask {
    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        stInit();
    }
    public static void stInit(){
        //查找ppc操作平台的数据
        LocalDate now = LocalDate.now();
        QFilter filter_code = new QFilter("aos_code","=","aos_mkt_ppcst_init");
        QFilter filter_value = new QFilter("aos_value","!=","");
        DynamicObject dy_value = QueryServiceHelper.queryOne("aos_sync_params", "aos_value", new QFilter[]{filter_code, filter_value});
        if (dy_value!=null){
            now = LocalDate.parse(dy_value.getString("aos_value"));
        }
        QFilter filter_s = new QFilter("aos_date",">=",now.toString());
        QFilter filter_org = new QFilter("aos_orgid","!=","");
        QFilter [] qfs = new QFilter[]{filter_org,filter_s  };
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_pop_ppcst", "id,aos_billno,aos_orgid,aos_orgid.number orgNumber", qfs);
        for (DynamicObject dy : dyc) {
            CreateStAdd stAdd = new CreateStAdd(dy);
            ThreadPools.executeOnce("",stAdd);
        }
    }
    static class CreateStAdd implements Runnable{
        Object ppcStId,org;
        String orgNumber,billno;
        CreateStAdd(DynamicObject dy){
            this.ppcStId = dy.get("id");
            this.org = dy.get("aos_orgid");
            orgNumber = dy.getString("orgNumber");
            billno = dy.getString("aos_billno");
        }
        @Override
        public void run() {
            try {
                //查找国别下的所有组别
                QFilter qf_group = null;
                QFilter qf_the_org = new QFilter("aos_org","=",org);
                QFilter qf_enable = new QFilter("enable","=","1");
                QFilter[] qfs = new QFilter[]{qf_group,qf_the_org,qf_enable};
                DynamicObjectCollection dyc_group = QueryServiceHelper.query("aos_salgroup","id,name",qfs);
                for (DynamicObject dy_group : dyc_group) {
                    String bill = orgNumber+dy_group.getString("name")+billno;
                    do_operate(bill,dy_group);
                }
            }catch (KDException e){
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                Log log = LogFactory.getLog("aos_mkt_pop_stadd_init");
                log.error(sw.toString());
                e.printStackTrace();
                throw e;
            }
        }
        public  void do_operate(String bill,DynamicObject dy_goup){
            DynamicObject aos_sync_log = BusinessDataServiceHelper.newDynamicObject("aos_sync_log");
            aos_sync_log.set("aos_type_code", "st销售加回"+bill);
            aos_sync_log.set("aos_groupid", LocalDateTime.now());
            aos_sync_log.set("billstatus", "A");
            DynamicObjectCollection aos_sync_logS = aos_sync_log.getDynamicObjectCollection("aos_entryentity");
            DynamicObject dy_stAdd = BusinessDataServiceHelper.newDynamicObject("aos_mkt_pop_stadd");
            dy_stAdd.set("aos_orgid",org);
            dy_stAdd.set("billno",bill);
            dy_stAdd.set("aos_groupid",dy_goup.get("id"));
            dy_stAdd.set("aos_makeby", Cux_Common_Utl.SYSTEM);
            dy_stAdd.set("aos_makedate",new Date());
            dy_stAdd.set("billstatus","A");
            Map<String, List<DynamicObject>> map_ppc = queryPPC(ppcStId,dy_goup.get("id"));
            MKTCom.Put_SyncLog(aos_sync_logS, "ppc数据： "+map_ppc.size());

            Map<String, DynamicObject> map_point = queryPointPt(org);
            MKTCom.Put_SyncLog(aos_sync_logS,"关键词报告数据：  "+map_point.size());

            DynamicObjectCollection dyc_stEnt = dy_stAdd.getDynamicObjectCollection("entryentity");
            for (Map.Entry<String, List<DynamicObject>> entry : map_ppc.entrySet()) {
                String[] split = entry.getKey().split("/");
                DynamicObject dy_entNewRow = dyc_stEnt.addNew();
                dy_entNewRow.set("aos_productno",split[0]);
                dy_entNewRow.set("aos_itemnumer",split[1]);
                DynamicObject dy_ppc = entry.getValue().get(0);

                dy_entNewRow.set("aos_category1",dy_ppc.get("aos_category1"));
                dy_entNewRow.set("aos_category2",dy_ppc.get("aos_category2"));
                dy_entNewRow.set("aos_category3",dy_ppc.get("aos_category3"));
                dy_entNewRow.set("aos_avadays",dy_ppc.get("aos_avadays"));
                dy_entNewRow.set("aos_eliminate",dy_ppc.get("aos_eliminatedate"));
                SetSubEntValue(dy_entNewRow,entry.getValue(),map_point);
            }
            if (dyc_stEnt.size()>0)
                SaveServiceHelper.save(new DynamicObject[]{dy_stAdd});
            SaveServiceHelper.save(new DynamicObject[]{aos_sync_log});
        }
    }
    /** 查找ppc数据 **/
    public static Map<String, List<DynamicObject>> queryPPC(Object ppcStId,Object groupName){
        QFilter filter_id = new QFilter("id","=",ppcStId);
//        QFilter filter_group = new QFilter("aos_entryentity.aos_sal_group","=",groupName);
//        QFilter filter_reason = new QFilter("aos_entryentity.aos_reason","=","ROI");
        QFilter filter_group = new QFilter("aos_sal_group","=",groupName);
        QFilter filter_reason = new QFilter("aos_reason","=","ROI");
        
        QFilter [] qfs = new QFilter[]{filter_id,filter_group,filter_reason};
        StringJoiner str = new StringJoiner(",");
//        str.add("aos_entryentity.aos_productno aos_productno");
//        str.add("aos_entryentity.aos_itemnumer aos_itemnumer");
//        str.add("aos_entryentity.aos_category1 aos_category1");
//        str.add("aos_entryentity.aos_category2 aos_category2");
//        str.add("aos_entryentity.aos_category3 aos_category3");
//        str.add("aos_entryentity.aos_avadays aos_avadays");
//        str.add("aos_entryentity.aos_eliminatedate aos_eliminatedate");
//        str.add("aos_entryentity.aos_keyword aos_keyword");
//        str.add("aos_entryentity.aos_match_type aos_match_type");

        str.add("aos_productno");
        str.add("aos_itemnumer");
        str.add("aos_category1");
        str.add("aos_category2");
        str.add("aos_category3");
        str.add("aos_avadays");
        str.add("aos_eliminatedate");
        str.add("aos_keyword");
        str.add("aos_match_type");
//        return QueryServiceHelper.query("aos_mkt_pop_ppcst", str.toString(), qfs)
//                .stream()
//                .collect(Collectors.groupingBy(dy -> dy.getString("aos_productno") + "/" +dy.getString("aos_itemnumer")));
        
        return QueryServiceHelper.query("aos_mkt_ppcst_data", str.toString(), qfs)
               .stream()
               .collect(Collectors.groupingBy(dy -> dy.getString("aos_productno") + "/" +dy.getString("aos_itemnumer")));
    }
    /** 查找关键词报告中的数据 **/
    public static Map<String, DynamicObject> queryPointPt(Object orgid){
        LocalDate now = LocalDate.now();
        QFilter filter_day = new QFilter("aos_date",">=",now.toString());
        QFilter filter_org = new QFilter("aos_orgid","=",orgid);
        QFilter filter_row = new QFilter("aos_entryentity.aos_date_l",">=",now.minusDays(30).toString());
        QFilter [] qfs = new QFilter[]{filter_day,filter_row,filter_org};
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
        ds = ds
                .groupBy(new String[]{"aos_cam_name","aos_ad_name","aos_match_type","aos_search_term"})
                .sum("aos_impressions")
                .sum("aos_clicks")
                .sum("aos_spend")
                .sum("aos_roi")
                .finish();
        DynamicObjectCollection dyc = ORM.create().toPlainDynamicObjectCollection(ds);
        ds.close();
        return dyc.stream().collect(Collectors
                .toMap(
                        dy -> dy.getString("aos_cam_name") + "/" + dy.getString("aos_ad_name") + "/"
                                + dy.getString("aos_match_type") + "/" + dy.getString("aos_search_term"),
                        dy -> dy,
                        (key1, key2) -> key1
                ));
    }
    /** 子单据体填充 **/
    public static void SetSubEntValue(DynamicObject dy_entRow,List<DynamicObject> list_ppc,Map<String,DynamicObject> map_point){
        DynamicObjectCollection dyc_subEnt = dy_entRow.getDynamicObjectCollection("aos_subentryentity");
        for (DynamicObject dy_ppc : list_ppc) {
            StringJoiner key = new StringJoiner("/");
            key.add(dy_entRow.getString("aos_productno"));
            key.add(dy_entRow.getString("aos_itemnumer"));
            DynamicObject dy_subRow = dyc_subEnt.addNew();
            dy_subRow.set("aos_keyword",dy_ppc.get("aos_keyword"));
            key.add(dy_ppc.getString("aos_keyword"));
            dy_subRow.set("aos_match_type",dy_ppc.get("aos_match_type"));
            key.add(dy_ppc.getString("aos_match_type"));
            if (map_point.containsKey(key.toString())){
                DynamicObject dy_point = map_point.get(key.toString());
                dy_subRow.set("aos_clicks",dy_point.get("aos_clicks"));
                dy_subRow.set("aos_spend",dy_point.get("aos_spend"));
                dy_subRow.set("aos_impressions",dy_point.get("aos_impressions"));
                dy_subRow.set("aos_roi_r",dy_point.get("aos_roi"));
            }
        }
    }
}
