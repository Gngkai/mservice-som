package mkt.progress.design;

import java.util.Date;
import java.util.Map;

import common.fnd.FndDate;
import common.fnd.FndGlobal;
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
import kd.bos.servicehelper.operation.SaveServiceHelper;

public class aos_mkt_slogan_auto extends AbstractTask {
	@Override
	public void execute(RequestContext arg0, Map<String, Object> arg1) throws KDException {
		do_operate();
	}

	private void do_operate() {
		DynamicObjectCollection aos_mkt_sloganS = QueryServiceHelper.query("aos_mkt_slogan", "id,aos_osdate",
				new QFilter("aos_status", QCP.equals, "海外翻译").toArray());
		for (DynamicObject aos_mkt_slogan : aos_mkt_sloganS) {
			Date aos_osdate = aos_mkt_slogan.getDate("aos_osdate");
			if (FndDate.add_NoWeekendDays(aos_osdate, 3).compareTo(new Date()) < 0) {
				DynamicObject aos_mkt_sloganO = BusinessDataServiceHelper.loadSingle(aos_mkt_slogan.getString("id"),
						"aos_mkt_slogan");
				String aos_category1 = aos_mkt_sloganO.getString("aos_category1");
				String aos_category2 = aos_mkt_sloganO.getString("aos_category2");
				String aos_lang = aos_mkt_sloganO.getString("aos_lang");

				DynamicObject aos_mkt_progorguser = QueryServiceHelper.queryOne("aos_mkt_progorguser", "aos_oueditor",
						new QFilter("aos_category1", "=", aos_category1).and("aos_category2", "=", aos_category2)
								.and("aos_orgid.number", "=", aos_lang).toArray());
				if (FndGlobal.IsNull(aos_mkt_progorguser)
						|| FndGlobal.IsNull(aos_mkt_progorguser.get("aos_oueditor"))) {
					continue;
				}
				aos_mkt_sloganO.set("aos_status", "翻译");
				aos_mkt_sloganO.set("aos_user", aos_mkt_progorguser.get("aos_oueditor"));
				SaveServiceHelper.saveOperate("aos_mkt_slogan", new DynamicObject[] { aos_mkt_sloganO },
						OperateOption.create());
			}
		}
	}
}
