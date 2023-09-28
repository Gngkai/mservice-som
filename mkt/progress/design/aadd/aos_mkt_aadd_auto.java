package mkt.progress.design.aadd;

import java.util.Date;
import java.util.Map;

import common.fnd.FndDate;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

public class aos_mkt_aadd_auto extends AbstractTask {

	@Override
	public void execute(RequestContext arg0, Map<String, Object> arg1) throws KDException {
		do_operate();
	}

	private void do_operate() {
		Date today = new Date();
		DynamicObject[] aosMktAaddS = BusinessDataServiceHelper.load("aos_mkt_aadd",
				"aos_status,aos_osdate,aos_org,aos_oueditor,aos_design",
				new QFilter("aos_status", QCP.equals, "海外确认").toArray());
		for (DynamicObject aosMktAadd : aosMktAaddS) {
			Date aos_osdate = aosMktAadd.getDate("aos_osdate");
			if (FndDate.add_NoWeekendDays(aos_osdate, 3).compareTo(today) < 0)
				continue;
			String aos_org = aosMktAadd.getString("aos_org");
			// 小语种 自动 到文案确认
			if ("DE,FR,IT,ES".contains(aos_org.toString())) {
				aosMktAadd.set("aos_user", aosMktAadd.get("aos_oueditor"));
				aosMktAadd.set("aos_status", "文案确认");
			} else {
				aosMktAadd.set("aos_user", aosMktAadd.get("aos_design"));
				aosMktAadd.set("aos_status", "设计制作");
			}
			aosMktAadd.set("aos_auto", "SYSTEM");// 人为提交
		}
		SaveServiceHelper.save(aosMktAaddS);
	}
}