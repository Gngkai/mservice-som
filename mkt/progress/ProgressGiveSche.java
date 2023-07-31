package mkt.progress;

import java.util.Date;
import java.util.Map;

import common.fnd.FndMsg;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

/**
 * 流程代理
 * 
 * @author aosom
 *
 */
public class ProgressGiveSche extends AbstractTask {

	@Override
	public void execute(RequestContext arg0, Map<String, Object> arg1) throws KDException {
		main();
	}

	public static void main() {
		FndMsg.debug("into main");
		Date today = new Date();
		DynamicObject[] aosMktGiveS = BusinessDataServiceHelper.load("aos_mkt_give",
				"aos_agent,aos_user,aos_type,aos_total,aos_status", new QFilter("aos_startdate", QCP.less_equals, today)
						.and("aos_endate", QCP.large_equals, today).toArray());
		FndMsg.debug(aosMktGiveS.length);
		for (DynamicObject aosMktGive : aosMktGiveS) {
			try {
				Object aosAgent = aosMktGive.get("aos_agent");
				String aosUser = aosMktGive.getString("aos_user");
				String aosType = aosMktGive.getString("aos_type");
				Boolean aosTotal = aosMktGive.getBoolean("aos_total");
				FndMsg.debug(aosTotal);
				if (aosTotal) {
					FndMsg.debug("into aosTotal");
					DynamicObjectCollection aosMktProgressS = QueryServiceHelper.query("aos_mkt_progress", "number",
							null);
					
					
					for (DynamicObject aosMktProgress : aosMktProgressS) {
						FndMsg.debug(aosMktProgress.getString("number"));
						DynamicObject[] aosTypeS = BusinessDataServiceHelper.load(aosMktProgress.getString("number"),
								"aos_user", new QFilter("aos_user.name", QCP.equals, aosUser).toArray());
						for (DynamicObject aosTypeSingle : aosTypeS) {
							FndMsg.debug(aosAgent);
							aosTypeSingle.set("aos_user", aosAgent);
						}
						SaveServiceHelper.save(aosTypeS);
					}
				} else {
					DynamicObject[] aosTypeS = BusinessDataServiceHelper.load(aosType, "aos_user",
							new QFilter("aos_user.name", QCP.equals, aosUser).toArray());
					for (DynamicObject aosTypeSingle : aosTypeS) {
						aosTypeSingle.set("aos_user", aosAgent);
					}
					SaveServiceHelper.save(aosTypeS);
				}
				aosMktGive.set("aos_status", "B");
			} catch (Exception ex) {
				ex.printStackTrace();
				continue;
			}
		}
		SaveServiceHelper.save(aosMktGiveS);
		
		aosMktGiveS = BusinessDataServiceHelper.load("aos_mkt_give",
				"aos_status", new QFilter("aos_endate", QCP.less_than, today).toArray());
		for (DynamicObject aosMktGive : aosMktGiveS) {
			aosMktGive.set("aos_status", "C");
		}
		SaveServiceHelper.save(aosMktGiveS);
		

		aosMktGiveS = BusinessDataServiceHelper.load("aos_mkt_give",
				"aos_status", new QFilter("aos_startdate", QCP.large_than, today).toArray());
		for (DynamicObject aosMktGive : aosMktGiveS) {
			aosMktGive.set("aos_status", "A");
		}
		SaveServiceHelper.save(aosMktGiveS);
		
	}

}
