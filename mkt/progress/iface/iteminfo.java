package mkt.progress.iface;

import java.util.*;

import common.Cux_Common_Utl;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;

/**
 * 物料信息
 * 
 * @author aosom
 *
 */
public class iteminfo {

	/** 获取单条国别物料维度海外库存 **/
	public static int GetItemOsQty(Object p_org_id, Object p_item_id) {
		int qty = 0;
		QFilter filter = new QFilter("aos_ou", "=", p_org_id);
		QFilter filter1 = new QFilter("aos_item", "=", p_item_id);
		QFilter[] filters = new QFilter[] { filter, filter1 };
		DynamicObject Dyn = QueryServiceHelper.queryOne("aos_sync_invou_value", "aos_instock_qty", filters);
		if (Dyn == null)
			qty = 0;
		else
			qty = Dyn.getInt("aos_instock_qty");
		return qty;
	}

	/** 获取单国别安全库存 **/
	public static int GetSafeQty(Object p_org_id) {
		int qty = 0;
		QFilter filter = new QFilter("aos_org", "=", p_org_id);
		QFilter filter1 = new QFilter("aos_project.name", "=", "安全库存>");
		QFilter[] filters = new QFilter[] { filter, filter1 };
		DynamicObject Dyn = QueryServiceHelper.queryOne("aos_sal_quo_m_coe", "aos_value", filters);
		if (Dyn == null)
			qty = 0;
		else
			qty = Dyn.getBigDecimal("aos_value").intValue();
		return qty;
	}

	/** 根据单条物料获取最小出运日期 新品取最小出运日期 **/
	public static DynamicObjectCollection GetShipDate(Object p_item_id,Object aos_sto_contractno,String...select) {
		QFilter filter = new QFilter("aos_stockentry.aos_sto_artno", "=", p_item_id);
		QFilter filter_suppierId = new QFilter("aos_stockentry.aos_sto_contractno","=",aos_sto_contractno);
		QFilter[] filters = new QFilter[] { filter ,filter_suppierId};
		StringJoiner str = new StringJoiner(",");
		str.add("aos_shipmentdate");
		for (String s : select) {
			str.add(s);
		}
		return QueryServiceHelper.query("aos_preparationnote", str.toString(), filters, "aos_shipmentdate asc", 1);


	}
	/** 根据供应商名称获取供应商id **/
	public static long QueryVendorIdByName (Object item,String vendorName){
		//如果供应商不为空
		if (!Cux_Common_Utl.IsNull(vendorName)) {
			QFilter filter_name = new QFilter("name","=",vendorName);
			DynamicObject dy = QueryServiceHelper.queryOne("bd_supplier", "id", new QFilter[]{filter_name});
			if (dy!=null)
				return dy.getLong("id");
		}
		return QueryVendorIdByItem(item);
	}

	public static long QueryVendorIdByItem (Object item){
		//根据物料查找报价单中的供应商
		QFilter filter = new QFilter("aos_offerbillentry.aos_materiel","=",item);
		String select = "aos_supplier";
		DynamicObjectCollection dyc = QueryServiceHelper.query("aos_offerbill", select, new QFilter[]{filter}, "aos_offerbillentry.aos_updatetime desc", 1);
		if (dyc.size()>0)
			return dyc.get(0).getLong(select);
		else
			return -1;
	}
}
