package mkt.synciface;

import common.Cux_Common_Utl;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.operate.result.IOperateInfo;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.exception.ErrorCode;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.StringJoiner;

/**
 * @author create by gk
 * @since 2023/1/9 10:34
 * @version 活动月计划，根据周期活动表中的数据生成活动计划和选品表-调度任务类
 */
public class AosMktSyncCreateActTask extends AbstractTask {
    public final static String AOS_USER = "aos_user";

    public static void doOperate(Object aosDate) {
        // 获取当天为周几
        Calendar today = Calendar.getInstance();
        // 计算28天后数据
        today.add(Calendar.DATE, 28);
        if (aosDate != null) {
            today.setTime((Date)aosDate);
        }
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        int week = today.get(Calendar.DAY_OF_WEEK);
        if (week == 0) {
            week = 7;
        } else {
            week = week - 1;
        }
        int day = today.get(Calendar.DAY_OF_MONTH);
        Date todayD = today.getTime();
        // 周期表 周类型 计划提报日为当前周几 月由于写法复杂 在循环中跳过
        QFilter filterDay =
            new QFilter("aos_periodtype", "=", "W").and("aos_plandays", "=", week).or("aos_periodtype", "=", "M");
        // 查询周期活动列表
        QFilter[] filters = new QFilter[] {filterDay};
        String selectField = "aos_orgid,aos_platformfid,aos_shopfid,aos_activity_name,aos_periodtype,aos_plandays,"
            + "aos_earlydays,aos_period,aos_orgid.number,aos_shopfid.aos_short_code,aos_activity_name.aos_short_code";
        DynamicObject[] aosMktActperiodS = BusinessDataServiceHelper.load("aos_mkt_actperiod", selectField, filters);
        for (DynamicObject aosMktActperiod : aosMktActperiodS) {
            boolean jump = true;
            // 周期
            String aosPeriodtype = aosMktActperiod.get("aos_periodtype").toString();
            // 计划提报日期
            String aosPlandays = aosMktActperiod.get("aos_plandays").toString();
            if ("M".equals(aosPeriodtype)) {
                String[] aosPlandayS = aosPlandays.split("&");
                for (String aosPlanday : aosPlandayS) {
                    if (aosPlanday.equals(String.valueOf(day))) {
                        jump = false;
                        break;
                    }
                }
            } else if ("W".equals(aosPeriodtype)) {
                // 周类型直接为true
                jump = false;
            }
            if (jump) {
                continue;
            }
            Object aosOrgid = aosMktActperiod.get("aos_orgid");
            Object aosPlatformid = aosMktActperiod.get("aos_platformfid");
            Object aosShopid = aosMktActperiod.get("aos_shopfid");
            Object aosActivityName = aosMktActperiod.get("aos_activity_name");
            int aosPeriod = (int)aosMktActperiod.get("aos_period");
            int aosEarlydays = (int)aosMktActperiod.get("aos_earlydays");
            Date aosDatefrom = addDays(todayD, aosEarlydays);
            Date aosDateto = addDays(aosDatefrom, aosPeriod - 1);
            DynamicObject aosActSelectPlan = BusinessDataServiceHelper.newDynamicObject("aos_act_select_plan");
            aosActSelectPlan.set("aos_nationality", aosOrgid);
            aosActSelectPlan.set("aos_channel", aosPlatformid);
            aosActSelectPlan.set("aos_shop", aosShopid);
            aosActSelectPlan.set("aos_acttype", aosActivityName);
            aosActSelectPlan.set("aos_startdate", aosDatefrom);
            aosActSelectPlan.set("aos_enddate1", aosDateto);
            aosActSelectPlan.set("billstatus", "A");
            // 默认 新建
            aosActSelectPlan.set("aos_actstatus", "A");
            aosActSelectPlan.set("aos_makedate", new Date());
            aosActSelectPlan.set("aos_makeby", getUser(aosOrgid));
            OperationResult result = SaveServiceHelper.saveOperate("aos_act_select_plan",
                new DynamicObject[] {aosActSelectPlan}, OperateOption.create());
            if (!result.isSuccess()) {
                StringJoiner str = new StringJoiner(";");
                for (IOperateInfo iOperateInfo : result.getAllErrorOrValidateInfo()) {
                    str.add(iOperateInfo.getMessage());
                }
                throw new KDException(new ErrorCode("aos_mkt_sync_createAct", str.toString()));
            }
        }
    }

    public static Date addDays(Date date, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }

    public static Object getUser(Object orgid) {
        QFilter filterOrg = new QFilter("aos_org", "=", orgid);
        DynamicObject dy = QueryServiceHelper.queryOne("aos_mkt_month_user", "aos_user", new QFilter[] {filterOrg});
        if (dy != null && !Cux_Common_Utl.IsNull(dy.get(AOS_USER))) {
            return dy.get("aos_user");
        }
        return Cux_Common_Utl.SYSTEM;
    }

    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        doOperate(null);
    }
}
