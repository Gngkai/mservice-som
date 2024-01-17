package mkt.synciface;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import common.Cux_Common_Utl;
import common.sal.sys.basedata.dao.ItemDao;
import common.sal.sys.basedata.dao.impl.ItemDaoImpl;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import common.sal.util.QFBuilder;

/**
 * @author aosom
 * @version 缺货天数结存-调度任务类
 */
public class AosMktOffSaleTask extends AbstractTask {
    public static void doOperate() {
        LocalDate localDate = LocalDate.now();
        // 结存的天数
        String saleBackDay = Cux_Common_Utl.GeneratePara("MMS_offSaleBackDay");
        if (!Cux_Common_Utl.IsNull(saleBackDay)) {
            localDate = LocalDate.parse(saleBackDay);
        }
        // 删除今日数据
        QFBuilder builder = new QFBuilder();
        builder.add("aos_date", ">=", localDate.toString());
        builder.add("aos_date", "<", localDate.plusDays(1));
        DeleteServiceHelper.delete("aos_sync_offsale_bak", builder.toArray());
        // 获取全是新品的物料
        List<String> listNewItem = getNewStatusItem();
        // 循环国别物料库存
        builder.clear();
        builder.add("aos_date", "=", localDate.toString());
        DynamicObjectCollection aosSyncInvouValueS = QueryServiceHelper.query("aos_sync_itemqty_forday",
            "aos_ou aos_orgid,aos_item aos_itemid,aos_qty", builder.toArray());
        Map<String, BigDecimal> saveQtyMap = generateSaveQty();
        DynamicObject aosSyncOffsaleBak = BusinessDataServiceHelper.newDynamicObject("aos_sync_offsale_bak");
        Date createDate = Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
        aosSyncOffsaleBak.set("aos_date", createDate);
        aosSyncOffsaleBak.set("billstatus", "A");
        DynamicObjectCollection aosEntryentityS = aosSyncOffsaleBak.getDynamicObjectCollection("aos_entryentity");
        for (DynamicObject aosSyncInvouValue : aosSyncInvouValueS) {
            String aosOrgid = aosSyncInvouValue.getString("aos_orgid");
            String aosItemid = aosSyncInvouValue.getString("aos_itemid");
            int aosInstockQty = aosSyncInvouValue.getInt("aos_qty");
            int saveQty = saveQtyMap.get(aosOrgid).intValue();
            DynamicObject aosEntryentity = aosEntryentityS.addNew();
            aosEntryentity.set("aos_orgid", aosOrgid);
            aosEntryentity.set("aos_itemid", aosItemid);
            aosEntryentity.set("aos_osqty", aosInstockQty);
            aosEntryentity.set("aos_createdate", createDate);
            if (listNewItem.contains(aosOrgid + "/" + aosItemid)) {
                aosEntryentity.set("aos_flag", false);
            } else {
                if (aosInstockQty <= saveQty) {
                    aosEntryentity.set("aos_flag", true);
                } else {
                    aosEntryentity.set("aos_flag", false);
                }
            }
        }
        OperationServiceHelper.executeOperate("save", "aos_sync_offsale_bak", new DynamicObject[] {aosSyncOffsaleBak},
            OperateOption.create());
    }

    private static Map<String, BigDecimal> generateSaveQty() {
        HashMap<String, BigDecimal> saveQtyMap = new HashMap<>(16);
        QFilter filterType = new QFilter("aos_project.name", "=", "安全库存>");
        QFilter[] filters = new QFilter[] {filterType};
        DynamicObjectCollection aosSalQuoCoeS =
            QueryServiceHelper.query("aos_sal_quo_m_coe", "aos_org.id aos_orgid, aos_value", filters);
        for (DynamicObject aosSalQuoCoe : aosSalQuoCoeS) {
            saveQtyMap.put(aosSalQuoCoe.getString("aos_orgid"), aosSalQuoCoe.getBigDecimal("aos_value"));
        }
        return saveQtyMap;
    }

    /**
     * 获取全是新品的物料信息
     */
    private static List<String> getNewStatusItem() {
        QFilter filter = new QFilter("aos_contryentry.aos_contryentrystatus", "=", "A");
        ItemDao itemDao = new ItemDaoImpl();
        StringJoiner str = new StringJoiner(",");
        str.add("id");
        str.add("aos_contryentry.aos_nationality aos_nationality");
        return itemDao.listItemObj(str.toString(), filter, null).stream()
            .map(dy -> dy.getString("aos_nationality") + "/" + dy.getString("id")).distinct()
            .collect(Collectors.toList());
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        doOperate();
    }
}