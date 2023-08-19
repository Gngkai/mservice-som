package mkt.popularst.adjust_s;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.plugin.AbstractOperationServicePlugIn;
import kd.bos.entity.plugin.PreparePropertysEventArgs;
import kd.bos.entity.plugin.args.AfterOperationArgs;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.StringJoiner;

/**
 * @author create by ?
 * @date 2023/1/16 16:41
 * @action
 */
public class aos_mkt_staddOperate extends AbstractOperationServicePlugIn {
    @Override
    public void onPreparePropertys(PreparePropertysEventArgs e) {
        List<String> fieldKeys = e.getFieldKeys();
        fieldKeys.add("billno");
        fieldKeys.add("aos_orgid");
        fieldKeys.add("aos_groupid");
        fieldKeys.add("aos_productno");
        fieldKeys.add("aos_itemnumer");
        fieldKeys.add("aos_add");
        fieldKeys.add("aos_keyword");
        fieldKeys.add("aos_match_type");
        fieldKeys.add("aos_apply");
    }
    @Override
    public void afterExecuteOperationTransaction(AfterOperationArgs e) {
        String operationKey = e.getOperationKey();
        if (operationKey.equals("submit")) {
            DynamicObject[] dataEntities = e.getDataEntities();
            for (DynamicObject dy_date : dataEntities) {
                String[] split = dy_date.getString("billno").split("组");
                if (split.length<=1) {
                    continue;
                }
                List<String> list_addRow = new LinkedList<>();   //销售加回的数据
                DynamicObjectCollection dyc_ent = dy_date.getDynamicObjectCollection("entryentity");
                for (DynamicObject dy_ent : dyc_ent) {
                    //加回
                    if (!dy_ent.getBoolean("aos_add"))
                        continue;
                    DynamicObjectCollection dyc_subEnt = dy_ent.getDynamicObjectCollection("aos_subentryentity");
                    for (DynamicObject dy_sub : dyc_subEnt) {
                        //应用
                        if (!dy_sub.getString("aos_apply").equals("Y")) {
                            continue;
                        }
                        StringJoiner str = new StringJoiner("/");
                        str.add(dy_ent.getString("aos_productno"));
                        str.add(dy_ent.getString("aos_itemnumer"));
                        str.add(dy_sub.getString("aos_keyword"));
                        str.add(dy_sub.getString("aos_match_type"));
                        list_addRow.add(str.toString());
                    }
                }
                updatePPC(dy_date.getDynamicObject("aos_orgid").getPkValue(),split[1],list_addRow);
            }
        }
    }
    public void updatePPC(Object org,String billlno,List<String> list_row){
        if (list_row.size()==0) {
            return;
        }
        QFilter filter = new QFilter("aos_billno","=",billlno);
        StringJoiner str = new StringJoiner(",");
//        str.add("id");
//        str.add("aos_billno");
//        str.add("aos_entryentity.aos_productno");
//        str.add("aos_entryentity.aos_itemnumer");
//        str.add("aos_entryentity.aos_keyword");
//        str.add("aos_entryentity.aos_match_type");
//        str.add("aos_entryentity.aos_groupstatus");
//        str.add("aos_entryentity.aos_bid");
        
        str.add("id");
        str.add("aos_billno");
        str.add("aos_productno");
        str.add("aos_itemnumer");
        str.add("aos_keyword");
        str.add("aos_match_type");
        str.add("aos_groupstatus");
        str.add("aos_bid");
        
//      DynamicObject[] dyc_ppc = BusinessDataServiceHelper.load("aos_mkt_pop_ppcst", str.toString(), new QFilter[]{filter});
        DynamicObject[] dyc_ppc = BusinessDataServiceHelper.load("aos_mkt_ppcst_data", str.toString(), new QFilter[]{filter});
        if (dyc_ppc.length==0)
            return;
        //查找出价
        QFilter filter_org = new QFilter("aos_orgid","=",org);
        QFilter filter_type = new QFilter("aos_type","=","FIRSTBID");
        DynamicObject dy = QueryServiceHelper.queryOne("aos_mkt_base_orgvalue", "aos_value", new QFilter[]{filter_org, filter_type});
        BigDecimal bid_price = BigDecimal.ZERO;
        if (dy!=null) {
            bid_price = dy.getBigDecimal("aos_value");
        }
        bid_price = bid_price.divide(BigDecimal.valueOf(2));
        ArrayList<String> arr_row = new ArrayList<>(list_row);
        for (DynamicObject dy_ppc : dyc_ppc) {
            DynamicObjectCollection dyc_ent = dy_ppc.getDynamicObjectCollection("aos_entryentity");
            for (DynamicObject dy_ent : dyc_ent) {
                StringJoiner key = new StringJoiner("/");
                key.add(dy_ent.getString("aos_productno"));
                key.add(dy_ent.getString("aos_itemnumer"));
                key.add(dy_ent.getString("aos_keyword"));
                key.add(dy_ent.getString("aos_match_type"));
                if (arr_row.contains(key.toString())){
                    dy_ent.set("aos_groupstatus","AVAILABLE");
                    dy_ent.set("aos_bid",bid_price);
                }
            }
            SaveServiceHelper.update(new DynamicObject[]{dy_ppc});
        }
    }
}
