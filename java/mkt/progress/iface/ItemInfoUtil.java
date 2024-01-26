package mkt.progress.iface;

import java.util.*;

import common.Cux_Common_Utl;
import common.fnd.FndGlobal;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;

/**
 * @author aosom
 * @version 物料信息
 */
public class ItemInfoUtil {
    /** 获取单条国别物料维度海外库存 **/
    public static int getItemOsQty(Object pOrgId, Object pItemId) {
        int qty = 0;
        QFilter filter = new QFilter("aos_ou", "=", pOrgId);
        QFilter filter1 = new QFilter("aos_item", "=", pItemId);
        QFilter[] filters = new QFilter[] {filter, filter1};
        DynamicObject dyn = QueryServiceHelper.queryOne("aos_sync_invou_value", "aos_instock_qty", filters);
        if (FndGlobal.IsNotNull(dyn)) {
            qty = dyn.getInt("aos_instock_qty");
        }
        return qty;
    }

    /** 获取单国别安全库存 **/
    public static int getSafeQty(Object pOrgId) {
        int qty = 0;
        QFilter filter = new QFilter("aos_org", "=", pOrgId);
        QFilter filter1 = new QFilter("aos_project.name", "=", "安全库存>");
        QFilter[] filters = new QFilter[] {filter, filter1};
        DynamicObject dyn = QueryServiceHelper.queryOne("aos_sal_quo_m_coe", "aos_value", filters);
        if (FndGlobal.IsNotNull(dyn)) {
            qty = dyn.getBigDecimal("aos_value").intValue();
        }
        return qty;
    }

    /** 根据单条物料获取最小出运日期 新品取最小出运日期 **/
    public static DynamicObjectCollection getShipDate(Object pItemId, Object aosStoContractno, String... select) {
        QFilter filter = new QFilter("aos_stockentry.aos_sto_artno", "=", pItemId);
        QFilter filterSuppierId = new QFilter("aos_stockentry.aos_sto_contractno", "=", aosStoContractno);
        QFilter[] filters = new QFilter[] {filter, filterSuppierId};
        StringJoiner str = new StringJoiner(",");
        str.add("aos_shipmentdate");
        for (String s : select) {
            str.add(s);
        }
        return QueryServiceHelper.query("aos_preparationnote", str.toString(), filters, "aos_shipmentdate asc", 1);
    }

    /** 根据供应商名称获取供应商id **/
    public static long queryVendorIdByName(Object item, String vendorName) {
        // 如果供应商不为空
        if (!Cux_Common_Utl.IsNull(vendorName)) {
            QFilter filterName = new QFilter("name", "=", vendorName);
            DynamicObject dy = QueryServiceHelper.queryOne("bd_supplier", "id", new QFilter[] {filterName});
            if (dy != null) {
                return dy.getLong("id");
            }
        }
        return queryVendorIdByItem(item);
    }

    public static long queryVendorIdByItem(Object item) {
        // 根据物料查找报价单中的供应商
        QFilter filter = new QFilter("aos_offerbillentry.aos_materiel", "=", item);
        String select = "aos_supplier";
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_offerbill", select, new QFilter[] {filter},
            "aos_offerbillentry.aos_updatetime desc", 1);
        if (dyc.size() > 0) {
            return dyc.get(0).getLong(select);
        } else {
            return -1;
        }
    }
}
