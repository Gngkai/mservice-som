package mkt.progress.photo;

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
import kd.fi.bd.util.QFBuilder;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.StringJoiner;

/**
 * @Author Zd
 * @Date 2023/4/10 16:31
 * 定时任务增加质检完成日期
 */
public class aos_mkt_rcv_task extends AbstractTask {
    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        QFBuilder builder_rcv = new QFBuilder();
        builder_rcv.add("aos_status", QCP.not_equals,"已完成");
        builder_rcv.add("aos_quainscomdate", "=",null);
        builder_rcv.add("aos_ponumber",QCP.not_equals,"");
        builder_rcv.add("aos_lineno",QCP.not_equals,"");
        DynamicObject[] dy_rcv = BusinessDataServiceHelper.load("aos_mkt_rcv",
                "id,aos_quainscomdate,aos_importance,aos_ponumber,aos_lineno",builder_rcv.toArray());//需要修改的样品入库通知单

        for(DynamicObject dy_ : dy_rcv) {
            QFilter qFilter = new QFilter("aos_insrecordentity.aos_contractnochk", "=", dy_.get("aos_ponumber"));
            QFilter qFilter1 = new QFilter("aos_insrecordentity.aos_lineno", "=", dy_.get("aos_lineno"));
            QFilter qFilter2 = new QFilter("aos_insrecordentity.aos_insresultchk","=","A");
            QFilter qFilter3 = new QFilter("aos_insrecordentity.aos_instypedetailchk","=","1");
            QFilter[] qFilter_rcv = {qFilter, qFilter1,qFilter2,qFilter3};
            DynamicObject dy_qct = QueryServiceHelper.queryOne("aos_qctasklist", "aos_quainscomdate", qFilter_rcv);//质检任务单
            if(dy_qct != null) {
                dy_.set("aos_quainscomdate", dy_qct.get("aos_quainscomdate"));
                SaveServiceHelper.update(dy_rcv);
                //当前日期-10(质检完成日期)，紧急程度刷新成紧急
                if (dy_.get("aos_quainscomdate") != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    Date dt = new Date();
                    Calendar rightNow = Calendar.getInstance();
                    rightNow.setTime(dt);
                    rightNow.add(Calendar.DAY_OF_YEAR, -10);
                    Date dt1 = rightNow.getTime();
                    String reStr1 = sdf.format(dt1);
                    //if (((String) dy_.get("aos_quainscomdate")).compareTo(reStr1) >= 0)
                    String reStr2 = sdf.format(dy_.get("aos_quainscomdate"));
                    if (reStr2.compareTo(reStr1) >= 0)
                        dy_.set("aos_importance", "紧急");
                }
             }
        }
        SaveServiceHelper.update(dy_rcv);


        QFBuilder builder_pro = new QFBuilder();
        builder_pro.add("aos_status", QCP.not_equals,"已完成");
        builder_pro.add("aos_quainscomdate", "=",null);
        builder_pro.add("aos_ponumber",QCP.not_equals,"");
        builder_pro.add("aos_linenumber",QCP.not_equals,"");
        DynamicObject[] dy_pro = BusinessDataServiceHelper.load("aos_mkt_photoreq",
                "id,aos_quainscomdate,aos_ponumber,aos_linenumber",builder_pro.toArray());//需要修改的拍照需求表

        for(DynamicObject dy_ : dy_pro) {
            QFilter qFilter = new QFilter("aos_insrecordentity.aos_contractnochk", "=", dy_.get("aos_ponumber"));
            QFilter qFilter1 = new QFilter("aos_insrecordentity.aos_lineno", "=", dy_.get("aos_linenumber"));
            QFilter qFilter2 = new QFilter("aos_insrecordentity.aos_insresultchk","=","A");
            QFilter qFilter3 = new QFilter("aos_insrecordentity.aos_instypedetailchk","=","1");
            QFilter[] qFilter_rcv = {qFilter, qFilter1,qFilter2,qFilter3};
            DynamicObject dy_qct = BusinessDataServiceHelper.loadSingle("aos_qctasklist", "aos_quainscomdate", qFilter_rcv);//质检任务单
            System.out.println("dy_qct = " + dy_qct);
            if(dy_qct != null) {
                dy_.set("aos_quainscomdate", dy_qct.get("aos_quainscomdate"));
                //SaveServiceHelper.update(dy_);
                System.out.println("aos_quainscomdate = " + dy_qct.get("aos_quainscomdate"));
                SaveServiceHelper.save(new DynamicObject[]{dy_});
            }
        }
        SaveServiceHelper.update(dy_pro);

        // 新品确认单增加订购会日期
        for(DynamicObject dy_confirm : BusinessDataServiceHelper.load("aos_sal_dis_confirm","aos_dis_date,aos_dis_code",
                new QFilter[]{new QFilter("aos_dis_date","=",null),
                        new QFilter("aos_dis_code", "!=","")})){
            DynamicObject dy_plan = QueryServiceHelper.queryOne("aos_sal_dis_plan","aos_dis_date",
                    new QFilter[]{new QFilter("aos_dis_name","=",dy_confirm.getString("aos_dis_code"))});

            if(dy_plan != null){
                dy_confirm.set("aos_dis_date",dy_plan.get("aos_dis_date"));
            }
            SaveServiceHelper.save(new DynamicObject[]{dy_confirm});
        }

    }
}
