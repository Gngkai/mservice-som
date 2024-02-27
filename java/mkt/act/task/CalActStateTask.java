package mkt.act.task;

import common.sal.util.DateUtil;
import common.sal.util.QFBuilder;
import common.sal.util.SaveUtils;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;

import java.util.*;

/**
 * @author: GK
 * @create: 2024-02-27 16:11
 * @Description:  活动计划 定时任务 计算活动状态
 */
public class CalActStateTask extends AbstractTask {
    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
       setActPlanState();
    }

    /**
     * 获取需要进行状态判断的 活动计划
     */
    private void setActPlanState(){
        QFBuilder qfBuilder = new QFBuilder();
        qfBuilder.add("aos_actstatus", QFilter.in, Arrays.asList("A","B","F"));
        qfBuilder.add("aos_startdate","!=","");
        qfBuilder.add("aos_enddate1","!=","");
        StringJoiner str = new StringJoiner(",");
        str.add("id");
        str.add("billno");
        str.add("aos_startdate");
        str.add("aos_enddate1");
        str.add("aos_actstatus");
        DynamicObject[] actPlanArrays = BusinessDataServiceHelper.load("aos_act_select_plan", str.toString(), qfBuilder.toArray());
        //今天的零时零分零秒
        Calendar toDayCal = DateUtil.todayCalendar();
        Calendar startDateCal = Calendar.getInstance(),endDateCal = Calendar.getInstance();
        List<DynamicObject> updateList = new ArrayList<>();
        for (DynamicObject actPlanEntry : actPlanArrays) {
            Date startDate = actPlanEntry.getDate("aos_startdate");
            startDateCal.setTime(startDate);
            DateUtil.setTodayCalendar(startDateCal);

            Date endDate = actPlanEntry.getDate("aos_enddate1");
            endDateCal.setTime(endDate);
            DateUtil.setTodayCalendar(endDateCal);

            String actStatus = actPlanEntry.getString("aos_actstatus");
            //新建 ,判断其是不是应该变更为进行中
            if ("A".equals(actStatus) || "B".equals(actStatus)){
                boolean actStart = (startDateCal.compareTo(toDayCal) < 1) && (endDateCal.compareTo(toDayCal) > -1);
                if (actStart){
                    actPlanEntry.set("aos_actstatus","F");
                    updateList.add(actPlanEntry);
                }
            }
            //已提报
            else {
                boolean actEnd = toDayCal.compareTo(endDateCal) > 0;
                if (actEnd){
                    actPlanEntry.set("aos_actstatus","D");
                    updateList.add(actPlanEntry);
                }
            }
            SaveUtils.UpdateEntity(updateList,false);
        }
        SaveUtils.UpdateEntity(updateList,true);
    }

}
