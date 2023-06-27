package mkt.synciface;

import common.Cux_Common_Utl;
import common.fnd.FndError;
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
 * @date 2023/1/9 10:34
 * @action  活动月计划，根据周期活动表中的数据生成活动计划和选品表
 */
public class aos_mkt_sync_createAct extends AbstractTask {
    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        do_operate(null);
    }
    public static void do_operate(Object aos_date)  {
        // 获取当天为周几
        Calendar Today = Calendar.getInstance();
        Today.add(Calendar.DATE, 28); // 计算28天后数据
        if (aos_date != null)
            Today.setTime((Date) aos_date);
        Today.set(Calendar.HOUR_OF_DAY, 0);
        Today.set(Calendar.MINUTE, 0);
        Today.set(Calendar.SECOND, 0);
        int week = Today.get(Calendar.DAY_OF_WEEK);
        if (week == 0)
            week = 7;
        else
            week = week - 1;

        int day = Today.get(Calendar.DAY_OF_MONTH);
        Date today = Today.getTime();
        System.out.println(today);

        System.out.println("今天计算为周" + week + ";当月的第" + day + "天");
        // 周期表 周类型 计划提报日为当前周几 月由于写法复杂 在循环中跳过
        QFilter filter_day = new QFilter("aos_periodtype", "=", "W").and("aos_plandays", "=", week).or("aos_periodtype",
                "=", "M");
        // 查询周期活动列表
        QFilter[] filters = new QFilter[] { filter_day };
        String SelectField = "aos_orgid,aos_platformfid,aos_shopfid,aos_activity_name,aos_periodtype,aos_plandays,"
                + "aos_earlydays,aos_period,aos_orgid.number,aos_shopfid.aos_short_code,aos_activity_name.aos_short_code";
//        DynamicObjectCollection aos_mkt_actperiodS = QueryServiceHelper.query("aos_mkt_actperiod", SelectField, filters);
        DynamicObject[] aos_mkt_actperiodS = BusinessDataServiceHelper.load("aos_mkt_actperiod", SelectField, filters);

        int rows = aos_mkt_actperiodS.length;
        int count = 0;
        for (DynamicObject aos_mkt_actperiod : aos_mkt_actperiodS) {
            count++;
            System.out.println("进度" + "(" + count + "/" + rows + ")");
            boolean jump = true;
            String aos_periodtype = aos_mkt_actperiod.get("aos_periodtype").toString(); //周期
            String aos_plandays = aos_mkt_actperiod.get("aos_plandays").toString();     //计划提报日期
            if (aos_periodtype.equals("M")) {
                String[] aos_plandayS = aos_plandays.split("&");
                for (String aos_planday : aos_plandayS) {
                    if (aos_planday.equals(day + "")) {
                        jump = false;
                        break;
                    }
                }
            }
            else if (aos_periodtype.equals("W")) {
                jump = false;// 周类型直接为true
            }
            if (jump){
                System.out.println("   剔除      ");
                continue;
            }

            Object aos_orgid = aos_mkt_actperiod.get("aos_orgid");
            Object aos_platformid = aos_mkt_actperiod.get("aos_platformfid");
            Object aos_shopid = aos_mkt_actperiod.get("aos_shopfid");
            Object aos_activity_name = aos_mkt_actperiod.get("aos_activity_name");
            int aos_period = (int) aos_mkt_actperiod.get("aos_period");
            int aos_earlydays = (int) aos_mkt_actperiod.get("aos_earlydays");
            Date aos_datefrom = add_days(today, aos_earlydays);
            Date aos_dateto = add_days(aos_datefrom, aos_period - 1);
            System.out.println("aos_datefrom =" + aos_datefrom);
            DynamicObject aos_act_select_plan = BusinessDataServiceHelper.newDynamicObject("aos_act_select_plan");
            aos_act_select_plan.set("aos_nationality", aos_orgid);
            aos_act_select_plan.set("aos_channel", aos_platformid);
            aos_act_select_plan.set("aos_shop", aos_shopid);
            aos_act_select_plan.set("aos_acttype", aos_activity_name);
            aos_act_select_plan.set("aos_startdate", aos_datefrom);
            aos_act_select_plan.set("aos_enddate1", aos_dateto);
            aos_act_select_plan.set("billstatus", "A");
            aos_act_select_plan.set("aos_actstatus", "A");// 默认 新建
            aos_act_select_plan.set("aos_makedate",new Date());
            aos_act_select_plan.set("aos_makeby", getUser(aos_orgid));

//            SaveServiceHelper.save(new DynamicObject[]{aos_act_select_plan});

            OperationResult result = SaveServiceHelper.saveOperate("aos_act_select_plan", new DynamicObject[]{aos_act_select_plan}, OperateOption.create());
            if (!result.isSuccess()){
                StringJoiner str = new StringJoiner(";");
                for (IOperateInfo iOperateInfo : result.getAllErrorOrValidateInfo()) {
                    str.add(iOperateInfo.getMessage());
                }
                throw new KDException(new ErrorCode("aos_mkt_sync_createAct",str.toString()));
            }
        }
    }

    public static Date add_days(Date date, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.DAY_OF_MONTH, days);// 原为DAY
        return calendar.getTime();
    }
    public static Object getUser(Object orgid){
        QFilter filter_org = new QFilter("aos_org","=",orgid);
        DynamicObject dy = QueryServiceHelper.queryOne("aos_mkt_month_user", "aos_user", new QFilter[]{filter_org});
        if (dy!=null && !Cux_Common_Utl.IsNull(dy.get("aos_user")))
            return dy.get("aos_user");
        return Cux_Common_Utl.SYSTEM;
    }
}
