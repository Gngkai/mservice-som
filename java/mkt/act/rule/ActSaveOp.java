package mkt.act.rule;

import common.fnd.FndGlobal;
import common.sal.sys.sync.service.ItemCacheService;
import common.sal.sys.sync.service.impl.ItemCacheServiceImpl;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.plugin.AbstractOperationServicePlugIn;
import kd.bos.entity.plugin.PreparePropertysEventArgs;
import kd.bos.entity.plugin.args.BeforeOperationArgs;

import java.util.List;

/**
 * @author: GK
 * @create: 2023-12-28 15:53
 * @Description:
 */
public class ActSaveOp extends AbstractOperationServicePlugIn {
    @Override
    public void onPreparePropertys(PreparePropertysEventArgs e) {
        super.onPreparePropertys(e);
        List<String> fieldKeys = e.getFieldKeys();
        fieldKeys.add("aos_nationality");
        fieldKeys.add("aos_itemnum");
        fieldKeys.add("aos_sal_day");
        fieldKeys.add("aos_instock_qty");
        fieldKeys.add("aos_ship_qty");
        fieldKeys.add("aos_store_date");
    }

    @Override
    public void beforeExecuteOperationTransaction(BeforeOperationArgs e) {
        super.beforeExecuteOperationTransaction(e);
        DynamicObject[] dataEntities = e.getDataEntities();
        //可售天数，可售数量，在途数量

        //预计入库日期

        ItemCacheService itemCacheService = new ItemCacheServiceImpl();
        for (DynamicObject dataEntity : dataEntities) {
            DynamicObject aos_nationality = dataEntity.getDynamicObject("aos_nationality");
            if (aos_nationality == null)
                continue;
            String orgid = aos_nationality.getString("id");
            String orgNum = aos_nationality.getString("number");
            for (DynamicObject row : dataEntity.getDynamicObjectCollection("aos_sal_actplanentity")) {
                DynamicObject aos_itemnum = row.getDynamicObject("aos_itemnum");
                if (aos_itemnum==null)
                    continue;

                String itemID = aos_itemnum.getString("id");
                String itemNum = aos_itemnum.getString("number");
                //可售天数，
                if (FndGlobal.IsNull(row.get("aos_sal_day")) || row.getInt("aos_sal_day")==0){
                    row.set("aos_sal_day",itemCacheService.getItemSaleDays(orgNum, itemNum));
                }
                //可售数量
                if (FndGlobal.IsNull(row.get("aos_instock_qty")) || row.getInt("aos_instock_qty")==0){
                    row.set("aos_instock_qty",itemCacheService.getItemOverseaStock(Long.parseLong(orgid), Long.parseLong(itemID)));
                }
                //在途数量
                if (FndGlobal.IsNull(row.get("aos_ship_qty")) || row.getInt("aos_ship_qty")==0){
                    row.set("aos_ship_qty",itemCacheService.getItemOnhandQty(orgid, itemID));
                }
                //预计入库日期
                if (FndGlobal.IsNull(row.get("aos_store_date"))){
                    row.set("aos_store_date",itemCacheService.getItemExpectDate(orgid,itemID));
                }
            }
        }
    }
}
