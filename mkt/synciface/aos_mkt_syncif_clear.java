package mkt.synciface;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import kd.bos.context.RequestContext;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.operation.DeleteServiceHelper;

/** 定时清理冗余数据 **/
public class aos_mkt_syncif_clear extends AbstractTask {

	@Override
	public void execute(RequestContext arg0, Map<String, Object> arg1) throws KDException {
		do_operate();
	}

	private void do_operate() {
		// 日期格式化
		SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
		// 获取对应日期
		Calendar date = Calendar.getInstance();
		date.set(Calendar.HOUR_OF_DAY, 0);
		date.set(Calendar.MINUTE, 0);
		date.set(Calendar.SECOND, 0);
		date.set(Calendar.MILLISECOND, 0);
		Calendar today = date;
		Date Before = today.getTime();
		today.add(Calendar.DAY_OF_MONTH, -3);
		Date Before3 = today.getTime();
		String DateBeforStr3 = writeFormat.format(Before3);
		today.add(Calendar.DAY_OF_MONTH, -4);
		Date Before7 = today.getTime();
		String DateBeforStr7 = writeFormat.format(Before7);

		/** 日志处理表 aos_sync_log **/
		QFilter filter_date = new QFilter("modifytime", "<=", DateBeforStr3);
		QFilter[] filters = new QFilter[] { filter_date };
		DeleteServiceHelper.delete("aos_sync_log", filters);

		/** 关键词报告 aos_base_pointrpt **/
		filter_date = new QFilter("aos_date", "<=", Before);
		filters = new QFilter[] { filter_date };
		DeleteServiceHelper.delete("aos_base_pointrpt", filters);

		/** 关键词建议价报告 aos_base_pointadv 调整为自己清理**/ 
		//filter_date = new QFilter("aos_date", "<=", DateBeforStr7);
		//filters = new QFilter[] { filter_date };
		//DeleteServiceHelper.delete("aos_base_pointadv", filters);

		/** SkU推广报告 **/
		filter_date = new QFilter("aos_date", "<=", Before);
		filters = new QFilter[] { filter_date };
		DeleteServiceHelper.delete("aos_base_skupoprpt", filters);

		/** 推广AD建议价格 **/
		filter_date = new QFilter("aos_date", "<=", Before);
		filters = new QFilter[] { filter_date };
		DeleteServiceHelper.delete("aos_base_advrpt", filters);

		/** ST推广 **/
		filter_date = new QFilter("aos_date", "<=", Before7);
		filters = new QFilter[] { filter_date };
		DeleteServiceHelper.delete("aos_mkt_pop_ppcst", filters);

		/** 活动选品查询 **/
		DeleteServiceHelper.delete("aos_mkt_actquery",null);
	}

}
