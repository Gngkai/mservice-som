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

/**
 * @author aosom
 * @version 定时清理冗余数据-调度任务类
 */
public class AosMktSyncClearTask extends AbstractTask {
    @Override
    public void execute(RequestContext arg0, Map<String, Object> arg1) throws KDException {
        doOperate();
    }

    private void doOperate() {
        // 日期格式化
        SimpleDateFormat writeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        // 获取对应日期
        Calendar date = Calendar.getInstance();
        date.set(Calendar.HOUR_OF_DAY, 0);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);
        Date before = date.getTime();
        date.add(Calendar.DAY_OF_MONTH, -3);
        Date before3 = date.getTime();
        String dateBeforStr3 = writeFormat.format(before3);
        date.add(Calendar.DAY_OF_MONTH, -4);
        Date before7 = date.getTime();
        // 日志处理表 aos_sync_log
        QFilter filterDate = new QFilter("modifytime", "<=", dateBeforStr3);
        QFilter[] filters = new QFilter[] {filterDate};
        DeleteServiceHelper.delete("aos_sync_log", filters);
        // 关键词报告 aos_base_pointrpt
		filterDate = new QFilter("aos_date", "<=", before);
        filters = new QFilter[] {filterDate};
        DeleteServiceHelper.delete("aos_base_pointrpt", filters);
        // SkU推广报告 aos_base_skupoprpt
		filterDate = new QFilter("aos_date", "<=", before);
        filters = new QFilter[] {filterDate};
        DeleteServiceHelper.delete("aos_base_skupoprpt", filters);
        // 推广AD建议价格 aos_base_advrpt
		filterDate = new QFilter("aos_date", "<=", before);
        filters = new QFilter[] {filterDate};
        DeleteServiceHelper.delete("aos_base_advrpt", filters);
        // 推广 aos_mkt_pop_ppcst aos_mkt_ppcst_data
		filterDate = new QFilter("aos_date", "<=", before7);
        filters = new QFilter[] {filterDate};
        DeleteServiceHelper.delete("aos_mkt_pop_ppcst", filters);
        DeleteServiceHelper.delete("aos_mkt_ppcst_data", filters);
        // 活动选品查询 aos_mkt_actquery
        DeleteServiceHelper.delete("aos_mkt_actquery", null);
    }
}
