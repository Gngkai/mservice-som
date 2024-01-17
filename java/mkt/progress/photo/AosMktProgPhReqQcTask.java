package mkt.progress.photo;

import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import common.sal.util.QFBuilder;
import java.util.Map;

/**
 * @author aosom
 * @version 拍照需求表质检任务时间刷新-调度任务类
 */
public class AosMktProgPhReqQcTask extends AbstractTask {
    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        QFBuilder builderPro = new QFBuilder();
        builderPro.add("aos_status", QCP.not_equals, "已完成");
        builderPro.add("aos_quainscomdate", "=", null);
        builderPro.add("aos_ponumber", QCP.not_equals, "");
        builderPro.add("aos_linenumber", QCP.not_equals, "");
        // 需要修改的拍照需求表
        DynamicObject[] dyPro = BusinessDataServiceHelper.load("aos_mkt_photoreq",
            "id,aos_quainscomdate,aos_ponumber,aos_linenumber", builderPro.toArray());
        for (DynamicObject dy : dyPro) {
            QFilter qFilter = new QFilter("aos_insrecordentity.aos_contractnochk", "=", dy.get("aos_ponumber"));
            QFilter qFilter1 = new QFilter("aos_insrecordentity.aos_lineno", "=", dy.get("aos_linenumber"));
            QFilter qFilter2 = new QFilter("aos_insrecordentity.aos_insresultchk", "=", "A");
            QFilter qFilter3 = new QFilter("aos_insrecordentity.aos_instypedetailchk", "=", "1");
            QFilter[] qFilterRcv = {qFilter, qFilter1, qFilter2, qFilter3};
            // 质检任务单
            DynamicObject dyQct =
                BusinessDataServiceHelper.loadSingle("aos_qctasklist", "aos_quainscomdate", qFilterRcv);
            if (dyQct != null) {
                dy.set("aos_quainscomdate", dyQct.get("aos_quainscomdate"));
                SaveServiceHelper.save(new DynamicObject[] {dy});
            }
        }
        SaveServiceHelper.update(dyPro);
    }
}
