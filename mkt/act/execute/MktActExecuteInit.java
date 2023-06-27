package mkt.act.execute;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;

import common.Cux_Common_Utl;
import common.fnd.FndMsg;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import sal.quote.CommData;

/**
 * 抓客执行表初始化
 * 
 * @author aosom
 *
 */
public class MktActExecuteInit extends AbstractTask {
	public final static String SYSTEM = Cux_Common_Utl.SYSTEM; // ID-000000

	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		do_operate();
	}

	public static void do_operate() {
		DynamicObjectCollection bd_country = QueryServiceHelper.query("bd_country", "id,number",
				new QFilter("aos_is_oversea_ou.number", QCP.equals, "Y").and("aos_isomvalid", "=", true).toArray());
		for (DynamicObject ou : bd_country) {
			QFilter group_org = new QFilter("aos_org.number", "=", ou.get("number"));// 海外公司
			QFilter group_enable = new QFilter("enable", "=", 1);// 可用
			QFilter[] filters_group = new QFilter[] { group_org, group_enable };
			DynamicObjectCollection aos_salgroup = QueryServiceHelper.query("aos_salgroup", "id", filters_group);
			for (DynamicObject group : aos_salgroup) {
				long group_id = group.getLong("id");
				init(ou.get("id"), group_id);
			}
		}
	}

	private static void init(Object org_id, long group_id) {
		Calendar today = Calendar.getInstance();
		today.set(Calendar.HOUR_OF_DAY, 0);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.SECOND, 0);
		today.set(Calendar.MILLISECOND, 0);
		Date Today = today.getTime();
		// 新建抓客执行表头
		DynamicObject aos_mkt_trackerexe = BusinessDataServiceHelper.newDynamicObject("aos_mkt_trackerexe");
		aos_mkt_trackerexe.set("aos_orgid", org_id);
		aos_mkt_trackerexe.set("aos_salgroup", group_id);
		aos_mkt_trackerexe.set("aos_creationdate", new Date());
		aos_mkt_trackerexe.set("aos_status", "新建");
		aos_mkt_trackerexe.set("aos_makeby", SYSTEM);
		DynamicObjectCollection aos_entryentityS = aos_mkt_trackerexe.getDynamicObjectCollection("aos_entryentity");
		// 查询活动与选品表
		String selectStr = "aos_nationality.id orgid,aos_channel,aos_shop,billno,aos_sal_actplanentity.aos_itemnum.id aos_itemnum,"
				+ "aos_sal_actplanentity.aos_l_startdate aos_l_startdate, aos_sal_actplanentity.aos_enddate aos_enddate,"
				+ "aos_sal_actplanentity.aos_price aos_price,aos_sal_actplanentity.aos_actprice aos_actprice,"
				+ "aos_sal_actplanentity.aos_actqty aos_actqty,"
				+ "aos_sal_actplanentity.aos_category_stat1 aos_category_stat1,"
				+ "aos_sal_actplanentity.aos_category_stat2 aos_category_stat2,"
				+ "aos_sal_actplanentity.aos_itemnum.name aos_itemname";
		DynamicObjectCollection aosActSelectPlanS = QueryServiceHelper.query("aos_act_select_plan", selectStr,
				new QFilter("aos_acttype.number", QCP.equals, "Tracker/Vipon").and("aos_actstatus", QCP.equals, "B")
						.and("aos_startdate", QCP.equals, Today).toArray());
		FndMsg.debug("size =" + aosActSelectPlanS.size());
		for (DynamicObject aosActSelectPlan : aosActSelectPlanS) {
			String item_id = aosActSelectPlan.getString("aos_itemnum");
			// 类别
			String itemCategoryId = CommData.getItemCategoryId(item_id);
			if (itemCategoryId == null || "".equals(itemCategoryId))
				continue;
			// 小类获取组别
			String salOrg = CommData.getSalOrgV2(org_id + "", itemCategoryId);
			if (salOrg == null || "".equals(salOrg))
				continue;
			// 循环非当前销售组别
			if (!salOrg.equals(group_id + ""))
				continue;
			DynamicObject aos_entryentity = aos_entryentityS.addNew();
			aos_entryentity.set("aos_channel", aosActSelectPlan.get("aos_channel"));
			aos_entryentity.set("aos_shop", aosActSelectPlan.get("aos_shop"));
			aos_entryentity.set("aos_itemid", item_id);
			aos_entryentity.set("aos_startdate", aosActSelectPlan.get("aos_l_startdate"));
			aos_entryentity.set("aos_enddate", aosActSelectPlan.get("aos_enddate"));
			aos_entryentity.set("aos_orignprz", aosActSelectPlan.get("aos_price"));
			aos_entryentity.set("aos_actprz", aosActSelectPlan.get("aos_actprice"));
			aos_entryentity.set("aos_qty", aosActSelectPlan.get("aos_actqty"));
			aos_entryentity.set("aos_category1", aosActSelectPlan.get("aos_category_stat1"));
			aos_entryentity.set("aos_category2", aosActSelectPlan.get("aos_category_stat2"));
			aos_entryentity.set("aos_actno", aosActSelectPlan.get("billno"));
			aos_entryentity.set("aos_way", "管控");
		}

		if (aosActSelectPlanS.size() > 0)
			OperationServiceHelper.executeOperate("save", "aos_mkt_trackerexe",
					new DynamicObject[] { aos_mkt_trackerexe }, OperateOption.create());
	}
}