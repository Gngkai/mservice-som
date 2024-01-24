package mkt.popularst.sale;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.plugin.AbstractOperationServicePlugIn;
import kd.bos.entity.plugin.PreparePropertysEventArgs;
import kd.bos.entity.plugin.args.AfterOperationArgs;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.StringJoiner;

/**
 * @author aosom
 * @version ST加回-操作插件
 */
public class AosMktPopAdjstAddOp extends AbstractOperationServicePlugIn {
    private static final String SUBMIT = "submit";

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
        if (SUBMIT.equals(operationKey)) {
            DynamicObject[] dataEntities = e.getDataEntities();
            for (DynamicObject dyDate : dataEntities) {
                String[] split = dyDate.getString("billno").split("组");
                if (split.length <= 1) {
                    continue;
                }
                // 销售加回的数据
                List<String> listAddRow = new LinkedList<>();
                DynamicObjectCollection dycEnt = dyDate.getDynamicObjectCollection("entryentity");
                for (DynamicObject dyEnt : dycEnt) {
                    // 加回
                    if (!dyEnt.getBoolean("aos_add")) {
                        continue;
                    }
                    DynamicObjectCollection dycSubEnt = dyEnt.getDynamicObjectCollection("aos_subentryentity");
                    for (DynamicObject dySub : dycSubEnt) {
                        // 应用
                        if (!"Y".equals(dySub.getString("aos_apply"))) {
                            continue;
                        }
                        StringJoiner str = new StringJoiner("/");
                        str.add(dyEnt.getString("aos_productno"));
                        str.add(dyEnt.getString("aos_itemnumer"));
                        str.add(dySub.getString("aos_keyword"));
                        str.add(dySub.getString("aos_match_type"));
                        listAddRow.add(str.toString());
                    }
                }
                updatePpc(dyDate.getDynamicObject("aos_orgid").getPkValue(), split[1], listAddRow);
            }
        }
    }

    public void updatePpc(Object org, String billlno, List<String> listRow) {
        if (listRow.size() == 0) {
            return;
        }
        QFilter filter = new QFilter("aos_billno", "=", billlno);
        StringJoiner str = new StringJoiner(",");
        str.add("id");
        str.add("aos_billno");
        str.add("aos_productno");
        str.add("aos_itemnumer");
        str.add("aos_keyword");
        str.add("aos_match_type");
        str.add("aos_groupstatus");
        str.add("aos_bid");
        DynamicObject[] dycPpc =
            BusinessDataServiceHelper.load("aos_mkt_ppcst_data", str.toString(), new QFilter[] {filter});
        if (dycPpc.length == 0) {
            return;
        }
        // 查找出价
        QFilter filterOrg = new QFilter("aos_orgid", "=", org);
        QFilter filterType = new QFilter("aos_type", "=", "FIRSTBID");
        DynamicObject dy =
            QueryServiceHelper.queryOne("aos_mkt_base_orgvalue", "aos_value", new QFilter[] {filterOrg, filterType});
        BigDecimal bidPrice = BigDecimal.ZERO;
        if (dy != null) {
            bidPrice = dy.getBigDecimal("aos_value");
        }
        bidPrice = bidPrice.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        ArrayList<String> arrRow = new ArrayList<>(listRow);
        for (DynamicObject dyPpc : dycPpc) {
            DynamicObjectCollection dycEnt = dyPpc.getDynamicObjectCollection("aos_entryentity");
            for (DynamicObject dyEnt : dycEnt) {
                StringJoiner key = new StringJoiner("/");
                key.add(dyEnt.getString("aos_productno"));
                key.add(dyEnt.getString("aos_itemnumer"));
                key.add(dyEnt.getString("aos_keyword"));
                key.add(dyEnt.getString("aos_match_type"));
                if (arrRow.contains(key.toString())) {
                    dyEnt.set("aos_groupstatus", "AVAILABLE");
                    dyEnt.set("aos_bid", bidPrice);
                }
            }
            SaveServiceHelper.update(new DynamicObject[] {dyPpc});
        }
    }
}
