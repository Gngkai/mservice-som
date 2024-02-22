package mkt.progress.listing.hotpoint;

import common.fnd.FndError;
import common.fnd.FndGlobal;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.datamodel.ListSelectedRowCollection;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * @author aosom
 * @since 2024/2/22 10:36
 * @version 爆品质量打分列表插件
 */
public class AosMktListingHotList extends AbstractListPlugin {
    public final static String AOS_BATCH = "aos_batch";

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String itemKey = evt.getItemKey();
        try {
            if (AOS_BATCH.equals(itemKey)) {
                aosBatch();
            }
        } catch (FndError error) {
            this.getView().showErrorNotification(error.getErrorMessage());
        }
    }

    /**
     * 批量修改
     */
    private void aosBatch() {
        String aosOrgCmp = "";
        String aosTypeCmp = "";
        ListSelectedRowCollection selectedRows = this.getSelectedRows();
        DynamicObjectCollection hotPointS =
            QueryServiceHelper.query("aos_mkt_hot_point", "aos_entryentity.aos_orgid aos_orgid,aos_type",
                new QFilter("id", QCP.in, selectedRows.getPrimaryKeyValues()).toArray());
        for (DynamicObject hotPoint : hotPointS) {
            String aosOrgId = hotPoint.getString("aos_orgid");
            String aosType = hotPoint.getString("aos_type");
            if (FndGlobal.IsNull(aosOrgCmp)) {
                aosOrgCmp = aosOrgId;
            }
            if (FndGlobal.IsNull(aosTypeCmp)) {
                aosTypeCmp = aosType;
            }
            if (!aosOrgCmp.equals(aosOrgId)) {
                throw new FndError("请勾选相同国别单据!");
            }
            if (!aosTypeCmp.equals(aosType)) {
                throw new FndError("请勾选相同类型单据!");
            }
        }
        Map<String, Object> params = new HashMap<>(16);
        params.put("aos_type", aosTypeCmp);
        params.put("ids", selectedRows.getPrimaryKeyValues());
        FndGlobal.OpenForm(this, "aos_mkt_hot_point_form", params);
    }
}
