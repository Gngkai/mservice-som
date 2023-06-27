package mkt.progress.design;

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

public class aos_mkt_designcmp_auto extends AbstractTask {

	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		do_operate(param);
	}

	public static void do_operate(Map<String, Object> param) {
		DynamicObjectCollection aosMktDesignCmpS = QueryServiceHelper.query("aos_mkt_designcmp", "id",
				new QFilter("aos_status", QCP.in, new String[] { "销售确认", "设计传图" }).toArray());

		for (DynamicObject aosMktDesignCmp : aosMktDesignCmpS) {
			DynamicObject aos_mkt_designcmp = BusinessDataServiceHelper.loadSingle(aosMktDesignCmp.get("id"),
					"aos_mkt_designcmp");
			new aos_mkt_designcmp_bill().aos_submit(aos_mkt_designcmp, "B");
		}

	}
}