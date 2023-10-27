package mkt.progress.photo;

import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import common.sal.util.QFBuilder;
import java.util.Map;

/**
 * @Author Zd
 * @Date 2023/4/25 10:02
 * @Version 1.0
 */
public class aos_mkt_proghreq_task extends AbstractTask {

	@Override
	public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
		QFBuilder builder_pro = new QFBuilder();
		builder_pro.add("aos_status", QCP.not_equals, "已完成");
		builder_pro.add("aos_quainscomdate", "=", null);
		builder_pro.add("aos_ponumber", QCP.not_equals, "");
		builder_pro.add("aos_linenumber", QCP.not_equals, "");
		DynamicObject[] dy_pro = BusinessDataServiceHelper.load("aos_mkt_photoreq",
				"id,aos_quainscomdate,aos_ponumber,aos_linenumber", builder_pro.toArray());// 需要修改的拍照需求表

		for (DynamicObject dy_ : dy_pro) {
			QFilter qFilter = new QFilter("aos_insrecordentity.aos_contractnochk", "=", dy_.get("aos_ponumber"));
			QFilter qFilter1 = new QFilter("aos_insrecordentity.aos_lineno", "=", dy_.get("aos_linenumber"));
			QFilter qFilter2 = new QFilter("aos_insrecordentity.aos_insresultchk", "=", "A");
			QFilter qFilter3 = new QFilter("aos_insrecordentity.aos_instypedetailchk", "=", "1");
			QFilter[] qFilter_rcv = { qFilter, qFilter1, qFilter2, qFilter3 };
			DynamicObject dy_qct = BusinessDataServiceHelper.loadSingle("aos_qctasklist", "aos_quainscomdate",
					qFilter_rcv);// 质检任务单
			if (dy_qct != null) {
				dy_.set("aos_quainscomdate", dy_qct.get("aos_quainscomdate"));
				// SaveServiceHelper.update(dy_);
				SaveServiceHelper.save(new DynamicObject[] { dy_ });
			}
		}
		SaveServiceHelper.update(dy_pro);
	}
}
