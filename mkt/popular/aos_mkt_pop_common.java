package mkt.popular;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;

// 推广通用工具类
public class aos_mkt_pop_common {

	// 判断今日是否执行亦或是复制前一日数据
	public static Boolean GetCopyFlag(String type, int week) {
		QFilter filter_type = new QFilter("aos_entryentity.aos_type", QCP.equals, type);
		QFilter[] filters_calendar = new QFilter[] { filter_type };
		DynamicObject aos_mkt_calendar = QueryServiceHelper.queryOne("aos_mkt_calendar",
				"aos_entryentity.aos_column" + week, filters_calendar);
		Boolean CopyFlag = aos_mkt_calendar.getBoolean("aos_entryentity.aos_column" + week);
		return CopyFlag;
	}

	// 根据当前整点判断执行欧洲亦或是美加的数据
	public static QFilter getEuOrUsQf(int hour) {
		DynamicObject dynamicObject = QueryServiceHelper.queryOne("aos_mkt_base_orgvalue", "aos_value",
				new QFilter("aos_type", QCP.equals, "TIME").toArray());
		int time = 16;
		if (dynamicObject != null)
			time = dynamicObject.getBigDecimal("aos_value").intValue();
		if (hour < time)
			return new QFilter("aos_is_north_america.number", QCP.not_equals, "Y");
		else
			return new QFilter("aos_is_north_america.number", QCP.equals, "Y");
	}
}
