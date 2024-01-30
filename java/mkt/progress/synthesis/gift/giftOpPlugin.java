package mkt.progress.synthesis.gift;

import common.fnd.FndGlobal;
import common.sal.util.QFBuilder;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.plugin.AbstractOperationServicePlugIn;
import kd.bos.entity.plugin.PreparePropertysEventArgs;
import kd.bos.entity.plugin.args.BeforeOperationArgs;
import kd.bos.servicehelper.QueryServiceHelper;

import java.time.LocalDate;
import java.util.List;

/**
 * @author: GK
 * @create: 2024-01-25 16:56
 * @Description:
 */
public class giftOpPlugin extends AbstractOperationServicePlugIn {
    @Override
    public void onPreparePropertys(PreparePropertysEventArgs e) {
        super.onPreparePropertys(e);
        List<String> fieldKeys = e.getFieldKeys();
        fieldKeys.add("aos_org");
        fieldKeys.add("aos_item");
        fieldKeys.add("aos_channel");
        fieldKeys.add("aos_shop");
        fieldKeys.add("aos_order");
        fieldKeys.add("aos_feeno");
        fieldKeys.add("aos_address");
    }

    @Override
    public void beforeExecuteOperationTransaction(BeforeOperationArgs e) {
        super.beforeExecuteOperationTransaction(e);
        String operationKey = e.getOperationKey();
        if ("order_con".equals(operationKey)){
            setFeeInfo(e.getDataEntities());
        }
    }

    /**
     * 设置快递费订单信息
     */
    public static void setFeeInfo(DynamicObject [] EntryRows){
        //当前日期
        LocalDate toDay = LocalDate.now();
        for (DynamicObject mainEntry : EntryRows) {
            DynamicObject orgEntry = mainEntry.getDynamicObject("aos_org");
            if (orgEntry == null) {
                continue;
            }
            //收货地址
            String address = mainEntry.getString("aos_address");
            if (FndGlobal.IsNull(address)) {
                continue;
            }
            QFBuilder builder = new QFBuilder();
            for (DynamicObject row : mainEntry.getDynamicObjectCollection("entryentity")) {
                DynamicObject itemEntry = row.getDynamicObject("aos_item");
                if (itemEntry == null){
                    continue;
                }
                builder.add("aos_sku","=",itemEntry.getString("number"));
                DynamicObject shopEntry = row.getDynamicObject("aos_shop");
                if (shopEntry == null){
                    continue;
                }
                builder.add("aos_shop_name","=",shopEntry.getString("name"));
                builder.add("aos_order_time",">=",toDay.minusDays(2).toString());
                builder.add("aos_order_time","<=",toDay.plusDays(2).toString());
                DynamicObjectCollection results = QueryServiceHelper.query("aos_lms_exp_base_track",
                        "aos_track_no,aos_sync_billid,aos_receiver_address1", builder.toArray());
                for (DynamicObject result : results) {
                    String reAddress = result.getString("aos_receiver_address1");
                    if (address.contains(reAddress)){
                        row.set("aos_order",result.getString("aos_sync_billid"));
                        row.set("aos_feeno",result.getString("aos_track_no"));
                    }
                }

            }
        }
    }
}
