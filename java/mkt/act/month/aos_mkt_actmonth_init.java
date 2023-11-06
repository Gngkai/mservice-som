package mkt.act.month;

import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import common.Cux_Common_Utl;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

public class aos_mkt_actmonth_init extends AbstractTask {

	public final static String SYSTEM = Cux_Common_Utl.SYSTEM; // ID-000000

	@Override
	public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
		do_operate(null);
	}

	public static void do_operate(Object aos_date) {
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
		int year = Today.get(Calendar.YEAR);
		int month = Today.get(Calendar.MONTH) + 1;
		Date today = Today.getTime();
		// 删除那天生成的数据
		QFilter filter_id = new QFilter("aos_plandate", "=", today);
		QFilter[] filters_del = new QFilter[] { filter_id };
		DeleteServiceHelper.delete("aos_mkt_actmonth", filters_del);

		// 周期表 周类型 计划提报日为当前周几 月由于写法复杂 在循环中跳过
		QFilter filter_day = new QFilter("aos_periodtype", "=", "W").and("aos_plandays", "=", week).or("aos_periodtype",
				"=", "M");
		// 查询周期活动列表
		QFilter[] filters = new QFilter[] { filter_day };
		String SelectField = "aos_orgid,aos_platformfid,aos_shopfid,aos_activity_name,aos_periodtype,aos_plandays,"
				+ "aos_earlydays,aos_period,aos_orgid.number,aos_shopfid.aos_short_code,aos_activity_name.aos_short_code";
		DynamicObjectCollection aos_mkt_actperiodS = QueryServiceHelper.query("aos_mkt_actperiod", SelectField,
				filters);
		int rows = aos_mkt_actperiodS.size();
		int count = 0;
		for (DynamicObject aos_mkt_actperiod : aos_mkt_actperiodS) {
			Boolean jump = true;
			String aos_periodtype = aos_mkt_actperiod.get("aos_periodtype").toString();
			String aos_plandays = aos_mkt_actperiod.get("aos_plandays").toString();
			if (aos_periodtype.equals("M")) {
				String[] aos_plandayS = aos_plandays.split("&");
				int length = aos_plandayS.length;
				for (int i = 0; i < length; i++) {
					if (aos_plandayS[i].equals(day + "")) {
						jump = false;
					}
				}
			} else if (aos_periodtype.equals("W")) {
				jump = false;// 周类型直接为true
			}
			if (jump)
				continue;
			count++;
			Object aos_orgid = aos_mkt_actperiod.get("aos_orgid");
			Object aos_platformid = aos_mkt_actperiod.get("aos_platformfid");
			Object aos_shopid = aos_mkt_actperiod.get("aos_shopfid");
			Object aos_activity_name = aos_mkt_actperiod.get("aos_activity_name");
			int aos_period = (int) aos_mkt_actperiod.get("aos_period");
			int aos_earlydays = (int) aos_mkt_actperiod.get("aos_earlydays");
			Date aos_datefrom = add_days(today, aos_earlydays);
			Date aos_dateto = add_days(aos_datefrom, aos_period - 1);
			DynamicObject aos_mkt_actmonth = BusinessDataServiceHelper.newDynamicObject("aos_mkt_actmonth");
			aos_mkt_actmonth.set("aos_orgid", aos_orgid);
			aos_mkt_actmonth.set("aos_platformid", aos_platformid);
			aos_mkt_actmonth.set("aos_shopid", aos_shopid);
			aos_mkt_actmonth.set("aos_activity_name", aos_activity_name);
			aos_mkt_actmonth.set("aos_period", aos_period);
			aos_mkt_actmonth.set("aos_datefrom", aos_datefrom);
			aos_mkt_actmonth.set("aos_dateto", aos_dateto);
			aos_mkt_actmonth.set("billstatus", "A");
			aos_mkt_actmonth.set("aos_activity_type", "P");// 默认 平台活动
			aos_mkt_actmonth.set("aos_status", "新建");// 默认 新建
			aos_mkt_actmonth.set("aos_plandate", today);// 默认 今日
			aos_mkt_actmonth.set("modifier", SYSTEM);// 默认 系统管理员
			aos_mkt_actmonth.set("creator", SYSTEM);// 默认 系统管理员

			//
			String yearstr = (year + "").substring(2);

			String aos_orgnumber = aos_mkt_actperiod.getString("aos_orgid.number");
			String aos_shopnumber = aos_mkt_actperiod.getString("aos_shopfid.aos_short_code");
			String aos_activity_name_str = aos_mkt_actperiod.getString("aos_activity_name.aos_short_code");
			String aos_force = aos_orgnumber + "-" + aos_shopnumber + "-" + aos_activity_name_str;
			String billno = aos_force + "-" + yearstr + String.format("%02d", month) + "-"
					+ Cux_Common_Utl.GetBaseBillNo(aos_force);
			aos_mkt_actmonth.set("aos_billno", billno);
			SaveServiceHelper.save(new DynamicObject[] { aos_mkt_actmonth });
		}
	}

	public static Date add_days(Date date, int days) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.add(Calendar.DAY_OF_MONTH, days);// 原为DAY
		Date day = calendar.getTime();
		return day;
	}

}
