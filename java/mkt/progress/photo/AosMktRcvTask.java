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

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;

/**
 * @author aosom
 * @version 定时任务增加质检完成日期-调度任务类
 */
public class AosMktRcvTask extends AbstractTask {
    public final static String AOS_SAL_DIS_CONFIRM = "aos_sal_dis_confirm";

    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        QFBuilder builderRcv = new QFBuilder();
        builderRcv.add("aos_status", QCP.not_equals, "已完成");
        builderRcv.add("aos_quainscomdate", "=", null);
        builderRcv.add("aos_ponumber", QCP.not_equals, "");
        builderRcv.add("aos_lineno", QCP.not_equals, "");
        // 需要修改的样品入库通知单
        DynamicObject[] dyRcvS = BusinessDataServiceHelper.load("aos_mkt_rcv",
            "id,aos_quainscomdate,aos_importance,aos_ponumber,aos_lineno", builderRcv.toArray());
        for (DynamicObject dyRcv : dyRcvS) {
            QFilter qFilter = new QFilter("aos_insrecordentity.aos_contractnochk", "=", dyRcv.get("aos_ponumber"));
            QFilter qFilter1 = new QFilter("aos_insrecordentity.aos_lineno", "=", dyRcv.get("aos_lineno"));
            QFilter qFilter2 = new QFilter("aos_insrecordentity.aos_insresultchk", "=", "A");
            QFilter qFilter3 = new QFilter("aos_insrecordentity.aos_instypedetailchk", "=", "1");
            QFilter[] qFilterRcv = {qFilter, qFilter1, qFilter2, qFilter3};
            // 质检任务单
            DynamicObject dyQct = QueryServiceHelper.queryOne("aos_qctasklist", "aos_quainscomdate", qFilterRcv);
            if (dyQct != null) {
                dyRcv.set("aos_quainscomdate", dyQct.get("aos_quainscomdate"));
                SaveServiceHelper.update(dyRcvS);
                // 当前日期-10(质检完成日期)，紧急程度刷新成紧急
                if (dyRcv.get("aos_quainscomdate") != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    Date dt = new Date();
                    Calendar rightNow = Calendar.getInstance();
                    rightNow.setTime(dt);
                    rightNow.add(Calendar.DAY_OF_YEAR, -10);
                    Date dt1 = rightNow.getTime();
                    String reStr1 = sdf.format(dt1);
                    // if (((String) dy_.get("aos_quainscomdate")).compareTo(reStr1) >= 0)
                    String reStr2 = sdf.format(dyRcv.get("aos_quainscomdate"));
                    if (reStr2.compareTo(reStr1) >= 0) {
                        dyRcv.set("aos_importance", "紧急");
                    }
                }
            }
        }
        SaveServiceHelper.update(dyRcvS);
        QFBuilder builderPro = new QFBuilder();
        builderPro.add("aos_status", QCP.not_equals, "已完成");
        builderPro.add("aos_quainscomdate", "=", null);
        builderPro.add("aos_ponumber", QCP.not_equals, "");
        builderPro.add("aos_linenumber", QCP.not_equals, "");
        // 需要修改的拍照需求表
        DynamicObject[] dyProS = BusinessDataServiceHelper.load("aos_mkt_photoreq",
            "id,aos_quainscomdate,aos_ponumber,aos_linenumber", builderPro.toArray());
        for (DynamicObject dyPro : dyProS) {
            QFilter qFilter = new QFilter("aos_insrecordentity.aos_contractnochk", "=", dyPro.get("aos_ponumber"));
            QFilter qFilter1 = new QFilter("aos_insrecordentity.aos_lineno", "=", dyPro.get("aos_linenumber"));
            QFilter qFilter2 = new QFilter("aos_insrecordentity.aos_insresultchk", "=", "A");
            QFilter qFilter3 = new QFilter("aos_insrecordentity.aos_instypedetailchk", "=", "1");
            QFilter[] qFilterRcv = {qFilter, qFilter1, qFilter2, qFilter3};
            // 质检任务单
            DynamicObject dyQct =
                BusinessDataServiceHelper.loadSingle("aos_qctasklist", "aos_quainscomdate", qFilterRcv);
            if (dyQct != null) {
                dyPro.set("aos_quainscomdate", dyQct.get("aos_quainscomdate"));
                SaveServiceHelper.save(new DynamicObject[] {dyPro});
            }
        }
        SaveServiceHelper.update(dyProS);
        // 新品确认单增加订购会日期
        for (DynamicObject dyConfirm : BusinessDataServiceHelper.load(AOS_SAL_DIS_CONFIRM, "aos_dis_date,aos_dis_code",
            new QFilter[] {new QFilter("aos_dis_date", "=", null), new QFilter("aos_dis_code", "!=", "")})) {
            DynamicObject dyPlan = QueryServiceHelper.queryOne("aos_sal_dis_plan", "aos_dis_date",
                new QFilter[] {new QFilter("aos_dis_name", "=", dyConfirm.getString("aos_dis_code"))});
            if (dyPlan != null) {
                dyConfirm.set("aos_dis_date", dyPlan.get("aos_dis_date"));
            }
            SaveServiceHelper.save(new DynamicObject[] {dyConfirm});
        }
    }
}
