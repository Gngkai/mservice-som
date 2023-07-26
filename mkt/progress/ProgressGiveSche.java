package mkt.progress;

import java.util.Date;
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

	private void main() {
		Date today = new Date();
		DynamicObjectCollection aosMktGiveS = QueryServiceHelper.query("aos_mkt_give", "aos_agent,aos_user,aos_type",
				new QFilter("aos_startdate", QCP.less_equals, today).and("aos_endate", QCP.large_equals, today)
						.toArray());
		for (DynamicObject aosMktGive : aosMktGiveS) {
			try {
				long aosAgent = aosMktGive.getLong("aos_agent");
				long aosUser = aosMktGive.getLong("aos_user");
				String aosType = aosMktGive.getString("aos_type");
				DynamicObject[] aosTypeS = BusinessDataServiceHelper.load(aosType, "aos_user",
						new QFilter("aos_user", QCP.equals, aosUser).toArray());
				for (DynamicObject aosTypeSingle : aosTypeS) {
					aosTypeSingle.set("aos_user", aosAgent);
				}
				SaveServiceHelper.save(aosTypeS);
			} catch (Exception ex) {
				continue;
			}
		}
	}

}
