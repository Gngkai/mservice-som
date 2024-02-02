package mkt.act.query.rpt;

import common.sal.util.QFBuilder;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.plugin.AbstractFormPlugin;


import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * @author: GK
 * @create: 2024-02-01 17:43
 * @Description:  国别节日&渠道大促活动日历 报表
 */
public class PromoteCalendarRpt extends AbstractFormPlugin {
    @Override
    public void afterDoOperation(AfterDoOperationEventArgs eventArgs) {
        super.afterDoOperation(eventArgs);
        String operateKey = eventArgs.getOperateKey();
        if ("aos_query".equals(operateKey)) {
            queryData();
        }
    }

    /**
     * 查询数据
     */
    public void queryData(){
        List<QFBuilder> filter = getFilter();

    }

    /**
     * 获取过滤条件
     * @return  过滤条件
     */
    private List<QFBuilder> getFilter(){
        List<QFBuilder> result = new ArrayList<>(2);
        QFBuilder orgFilterArray = new QFBuilder(),channelFilterArray = new QFBuilder();
        Object org = getModel().getValue("aos_sel_org");
        if (org!=null){
            String orgId = ((DynamicObject)org).getString("id");
            orgFilterArray.add("aos_orgid","=",orgId);
            channelFilterArray.add("aos_org","=",orgId);
        }

        Date yearDate = (Date) getModel().getValue("aos_sel_year");
        Date monthDate = (Date) getModel().getValue("aos_sel_month");
        Calendar yearCal = Calendar.getInstance(),monthCal = Calendar.getInstance();
        yearCal.setTime(yearDate);
        monthCal.setTime(monthDate);
        yearCal.set(Calendar.MONTH,monthCal.get(Calendar.MONTH));
        yearCal.set(Calendar.DAY_OF_MONTH,1);

        orgFilterArray.add("aos_date",">=",yearCal.getTime());
        channelFilterArray.add("aos_start",">=",yearCal.getTime());

        yearCal.add(Calendar.MONTH,1);
        orgFilterArray.add("aos_date","<",yearCal.getTime());
        channelFilterArray.add("aos_start","<",yearCal.getTime());

        result.add(orgFilterArray);
        result.add(channelFilterArray);
        return result;
    }

}
