package mkt.popular;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;

/**
 * @author aosom
 * @version 推广通用工具类
 */
public class AosMktPopUtil {
    /**
     * 判断今日是否执行亦或是复制前一日数据
     * 
     * @param type 类型
     * @param week 周
     * @return 是否复制
     */
    public static boolean getCopyFlag(String type, int week) {
        DynamicObject aosMktCalendar = QueryServiceHelper.queryOne("aos_mkt_calendar",
            "aos_entryentity.aos_column" + week, new QFilter("aos_entryentity.aos_type", QCP.equals, type).toArray());
        return aosMktCalendar.getBoolean("aos_entryentity.aos_column" + week);
    }
    /**
     * 根据当前整点判断执行欧洲亦或是美加的数据
     * 
     * @param hour 小时
     * @return 查询条件
     */
    public static QFilter getEuOrUsQf(int hour) {
        DynamicObject dynamicObject = QueryServiceHelper.queryOne("aos_mkt_base_orgvalue", "aos_value",
            new QFilter("aos_type", QCP.equals, "TIME").toArray());
        int time = 16;
        if (dynamicObject != null) {
            time = dynamicObject.getBigDecimal("aos_value").intValue();
        }
        if (hour < time) {
            return new QFilter("aos_is_north_america.number", QCP.not_equals, "Y");
        } else {
            return new QFilter("aos_is_north_america.number", QCP.equals, "Y");
        }
    }
}
