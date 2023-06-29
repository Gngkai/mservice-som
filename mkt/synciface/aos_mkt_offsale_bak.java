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
import kd.fi.bd.util.QFBuilder;
import mkt.common.MKTCom;
import net.sf.cglib.core.Local;

/** 缺货天数结存接口 **/
public class aos_mkt_offsale_bak extends AbstractTask {
	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		do_operate(param);
	}

	public static void do_operate(Map<String, Object> param) {
		LocalDate localDate = LocalDate.now();
		//结存的天数
		String saleBackDay = Cux_Common_Utl.GeneratePara("MMS_offSaleBackDay");
		if (!Cux_Common_Utl.IsNull(saleBackDay)) {
			localDate = LocalDate.parse(saleBackDay);
		}

		// 删除今日数据
		QFBuilder builder = new QFBuilder();
		builder.add("aos_date",">=",localDate.toString());
		builder.add("aos_date","<",localDate.plusDays(1));
		DeleteServiceHelper.delete("aos_sync_offsale_bak", builder.toArray());

		//获取全是新品的物料
		List<String> list_newItem = getNewStatusItem();

		// 循环国别物料库存
		builder.clear();
		builder.add("aos_date","=",localDate.toString());
		DynamicObjectCollection aos_sync_invou_valueS = QueryServiceHelper.query("aos_sync_itemqty_forday",
				"aos_ou aos_orgid,aos_item aos_itemid,aos_qty", builder.toArray());
		Map<String, BigDecimal> SaveQtyMap = GenerateSaveQty();

		DynamicObject aos_sync_offsale_bak = BusinessDataServiceHelper.newDynamicObject("aos_sync_offsale_bak");
		Date createDate = Date.from(localDate.atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
		aos_sync_offsale_bak.set("aos_date", createDate);
		aos_sync_offsale_bak.set("billstatus","A");
		DynamicObjectCollection aos_entryentityS = aos_sync_offsale_bak.getDynamicObjectCollection("aos_entryentity");

		for (DynamicObject aos_sync_invou_value : aos_sync_invou_valueS) {
			String aos_orgid = aos_sync_invou_value.getString("aos_orgid");
			String aos_itemid = aos_sync_invou_value.getString("aos_itemid");
			int aos_instock_qty = aos_sync_invou_value.getInt("aos_qty");
			int SaveQty = SaveQtyMap.get(aos_orgid).intValue();
			DynamicObject aos_entryentity = aos_entryentityS.addNew();
			aos_entryentity.set("aos_orgid", aos_orgid);
			aos_entryentity.set("aos_itemid", aos_itemid);
			aos_entryentity.set("aos_osqty", aos_instock_qty);
			aos_entryentity.set("aos_createdate",createDate);
			if (list_newItem.contains(aos_orgid+"/"+aos_itemid)){
				aos_entryentity.set("aos_flag", false);
			}
			else {
				if (aos_instock_qty <= SaveQty)
					aos_entryentity.set("aos_flag", true);
				else
					aos_entryentity.set("aos_flag", false);
			}
		}
		OperationServiceHelper.executeOperate("save", "aos_sync_offsale_bak",
				new DynamicObject[] { aos_sync_offsale_bak }, OperateOption.create());
	}

	private static Map<String, BigDecimal> GenerateSaveQty() {
		HashMap<String, BigDecimal> SaveQtyMap = new HashMap<>();
		QFilter filter_type = new QFilter("aos_project.name", "=", "安全库存>");
		QFilter[] filters = new QFilter[] { filter_type };
		DynamicObjectCollection aos_sal_quo_m_coeS = QueryServiceHelper.query("aos_sal_quo_m_coe",
				"aos_org.id aos_orgid, aos_value", filters);
		for (DynamicObject aos_sal_quo_m_coe : aos_sal_quo_m_coeS)
			SaveQtyMap.put(aos_sal_quo_m_coe.getString("aos_orgid"), aos_sal_quo_m_coe.getBigDecimal("aos_value"));
		return SaveQtyMap;
	}

	/**
	 * 获取全是新品的物料信息
	 */
	private static List<String> getNewStatusItem(){
		QFilter filter = new QFilter("aos_contryentry.aos_contryentrystatus","=","A");
		ItemDao itemDao = new ItemDaoImpl();
		StringJoiner str = new StringJoiner(",");
		str.add("id");
		str.add("aos_contryentry.aos_nationality aos_nationality");
		return itemDao.listItemObj(str.toString(), filter, null)
				.stream()
				.map(dy -> dy.getString("aos_nationality") + "/" + dy.getString("id"))
				.distinct()
				.collect(Collectors.toList());
	}
}