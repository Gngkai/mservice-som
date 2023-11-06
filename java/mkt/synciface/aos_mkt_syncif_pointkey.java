package mkt.synciface;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

/**
 * 关键词建议价 每周一早3点执行 执行前清除数据
 * 
 * @author aosom
 *
 */
public class aos_mkt_syncif_pointkey extends AbstractTask {
	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		orgLoop(param);
	}

	public static void orgLoop(Map<String, Object> param) {
		DeleteServiceHelper.delete("aos_base_pointadv", null);
		QFilter oversea_flag = new QFilter("aos_is_oversea_ou.number", "=", "Y");// 海外公司
		QFilter oversea_flag2 = new QFilter("aos_isomvalid", "=", true);
		QFilter[] filters_ou = new QFilter[] { oversea_flag, oversea_flag2 };
		DynamicObjectCollection bd_country = QueryServiceHelper.query("bd_country", "id,number", filters_ou);
		for (DynamicObject ou : bd_country) {
			do_operate(param, ou.getString("number"));
		}
	}

	public static void do_operate(Map<String, Object> param, String ou) {
		Map<String, Object> map = GenerateCountry();
		Object p_org_id = map.get(ou);
		Calendar Today = Calendar.getInstance();
		Today.set(Calendar.HOUR_OF_DAY, 0);
		Today.set(Calendar.MINUTE, 0);
		Today.set(Calendar.SECOND, 0);
		Today.set(Calendar.MILLISECOND, 0);
		DynamicObject aos_base_advrpt = BusinessDataServiceHelper.newDynamicObject("aos_base_pointadv");
		aos_base_advrpt.set("billstatus", "A");
		aos_base_advrpt.set("aos_date", Today.getTime());
		aos_base_advrpt.set("aos_org", p_org_id);
		DynamicObjectCollection aos_entryentityS = aos_base_advrpt.getDynamicObjectCollection("entryentity");
		String org = getOrg(ou);
		DynamicObjectCollection rptS = QueryServiceHelper.query("aos_base_pointadv_dp",
				"aos_ad_name,aos_keyword,aos_match_type,aos_bid_suggest,aos_bid_rangestart,aos_bid_rangeend",
				new QFilter("aos_org", QCP.equals, org).toArray());
		for (DynamicObject rpt : rptS) {
			DynamicObject aos_entryentity = aos_entryentityS.addNew();
			aos_entryentity.set("aos_orgid", p_org_id);
			aos_entryentity.set("aos_ad_name", rpt.get("aos_ad_name"));
			aos_entryentity.set("aos_keyword", rpt.get("aos_keyword"));
			aos_entryentity.set("aos_match_type", rpt.get("aos_match_type"));
			aos_entryentity.set("aos_bid_rangeend", rpt.get("aos_bid_suggest"));
			aos_entryentity.set("aos_bid_rangestart", rpt.get("aos_bid_rangestart"));
			aos_entryentity.set("aos_bid_suggest", rpt.get("aos_bid_rangeend"));
		}
		SaveServiceHelper.save(new DynamicObject[] { aos_base_advrpt });
	}

	public static String getOrg(String ou) {
		String org = "";
		if ("US".equals(ou))
			org = "AOSOM LLC";
		else if ("CA".equals(ou))
			org = "AOSOM CANADA INC.";
		else if ("UK".equals(ou))
			org = "MH STAR UK LTD";
		else if ("DE".equals(ou))
			org = "MH HANDEL GMBH";
		else if ("FR".equals(ou))
			org = "MH FRANCE";
		else if ("IT".equals(ou))
			org = "AOSOM ITALY SRL";
		else if ("ES".equals(ou))
			org = "SPANISH AOSOM, S.L.";
		return org;
	}

	public static Map<String, Object> GenerateCountry() {
		Map<String, Object> map = new HashMap<>();
		DynamicObjectCollection dyns = QueryServiceHelper.query("bd_country", "id,number", null);
		for (DynamicObject d : dyns) {
			map.put(d.getString("number"), d.get("id"));
		}
		return map;
	}
}
