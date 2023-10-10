package mkt.progress.iface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import common.Cux_Common_Utl;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.form.ClientProperties;
import kd.bos.form.IFormView;
import kd.bos.form.IPageCache;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;

/**
 * 参数信息
 * 
 * @author aosom
 *
 */
public class parainfo {

	/**
	 * 处理营销工作流通用列表查询权限
	 * 
	 * @param iPageCache
	 **/
	public static void setRights(List<QFilter> qFilters, IPageCache iPageCache, String type, String... OtherFilter) {
		long currentUserId = UserServiceHelper.getCurrentUserId();

		QFilter filter = new QFilter("aos_user.id", QCP.equals, currentUserId);
		QFilter filter2 = new QFilter("aos_process", QCP.equals, true);// 判断是否为工作流管理员
		boolean exists = QueryServiceHelper.exists("aos_mkt_userights", new QFilter[] { filter, filter2 });
		// 1.工作流管理员不做任何人员相关权限控制
		if (!exists) {
			// 2.只能看到申请人为自己或当前节点操作人为自己的单据 或流经自己的单据

			// 获取历史记录中 流经自己的单据
			List<Long> list = GetHisList(type, currentUserId);

			QFilter filter_user = new QFilter("aos_user", QCP.equals, currentUserId)
					.or("aos_requireby", QCP.equals, currentUserId).or("id", QCP.in, list);
			for (String otherUser : OtherFilter) {
				filter_user = filter_user.or(otherUser, "=", currentUserId);
			}
			qFilters.add(filter_user);
		}
		// 3.若未点击查看关闭流程(界面缓存参数为空) 则排除已关闭的流程
		if ("true".equals(iPageCache.get("p_close_flag")))
			qFilters.add(new QFilter("aos_status", QCP.equals, "已完成").or("aos_status", QCP.equals, "结束"));
		else if ("false".equals(iPageCache.get("p_close_flag")))
			qFilters.add(new QFilter("aos_status", QCP.not_equals, "已完成").and("aos_status", QCP.not_equals, "结束"));
		else
			qFilters.add(new QFilter("aos_status", QCP.not_equals, "已完成").and("aos_status", QCP.not_equals, "结束"));
	}

	private static List<Long> GetHisList(String type, long currentUserId) {
		List<Long> list = new ArrayList<Long>();
		QFilter filter_type = new QFilter("aos_formid", QCP.equals, type);
		QFilter filter_by = new QFilter("aos_actionby", QCP.equals, currentUserId);
		DynamicObjectCollection aos_sync_operateS = QueryServiceHelper.query("aos_sync_operate", "aos_sourceid",
				new QFilter[] { filter_type, filter_by });
		for (DynamicObject aos_sync_operate : aos_sync_operateS) {
			list.add(aos_sync_operate.getLong("aos_sourceid"));
		}
		return list;
	}

	/**
	 * 处理营销工作流通用列表查询权限
	 *
	 * @param iPageCache
	 **/
	public static void setPhotoRights(List<QFilter> qFilters, IPageCache iPageCache, String... OtherFilter) {
		long currentUserId = UserServiceHelper.getCurrentUserId();
		QFilter filter = new QFilter("aos_user.id", QCP.equals, currentUserId);
		QFilter filter2 = new QFilter("aos_process", QCP.equals, true);// 判断是否为工作流管理员
		boolean exists = QueryServiceHelper.exists("aos_mkt_userights", new QFilter[] { filter, filter2 });
		// 1.工作流管理员不做任何人员相关权限控制
		if (!exists) {
			// 2.只能看到申请人为自己或当前节点操作人为自己的单据
			QFilter filter_user = new QFilter("aos_requireby", QCP.equals, currentUserId);
			for (String otherUser : OtherFilter) {
				filter_user = filter_user.or(otherUser, "=", currentUserId);
			}
			qFilters.add(filter_user);
		}
	}

	/** 处理营销工作流设计专用列表查询权限 到小组维度 **/
	public static void setRightsForDesign(List<QFilter> qFilters, IPageCache iPageCache) {
		/*
		 * long currentUserId = UserServiceHelper.getCurrentUserId(); QFilter filter =
		 * new QFilter("aos_user.id", QCP.equals, currentUserId); QFilter filter2 = new
		 * QFilter("aos_process", QCP.equals, true);// 判断是否为工作流管理员 boolean exists =
		 * QueryServiceHelper.exists("aos_mkt_userights", new QFilter[] { filter,
		 * filter2 }); // 1.工作流管理员不做任何人员相关权限控制 if (!exists) { //
		 * 2.只能看到申请人为自己或当前节点操作人为自己的单据 若存在小组权限 则能看到小组权限维度下的数据 filter = new
		 * QFilter("aos_user.id", QCP.equals, currentUserId); filter2 = new
		 * QFilter("aos_entryentity.aos_type", QCP.equals, "DESIGN");// 判断是否为设计组别类型
		 * DynamicObjectCollection aos_mkt_userightS =
		 * QueryServiceHelper.query("aos_mkt_userights",
		 * "aos_entryentity.aos_dept aos_dept", new QFilter[] { filter, filter2 });
		 * List<Long> deptList = new ArrayList<>(); for (DynamicObject aos_mkt_useright
		 * : aos_mkt_userightS) { deptList.add(aos_mkt_useright.getLong("aos_dept")); }
		 * QFilter filter_dpt = new QFilter("entryentity.dpt", QCP.in, deptList);
		 * DynamicObjectCollection bos_userS = QueryServiceHelper.query("bos_user",
		 * "id", new QFilter[] { filter_dpt }); List<Long> userList = new ArrayList<>();
		 * for (DynamicObject bos_user : bos_userS) {
		 * userList.add(bos_user.getLong("id")); } qFilters.add( new QFilter("aos_user",
		 * QCP.equals, currentUserId).or("aos_requireby", QCP.equals, currentUserId)
		 * .or("aos_user", QCP.in, userList).or("aos_requireby", QCP.in, userList)); }
		 */

		// 3.若未点击查看关闭流程(界面缓存参数为空) 则排除已关闭的流程
		if ("true".equals(iPageCache.get("p_close_flag")))
			qFilters.add(new QFilter("aos_status", QCP.equals, "已完成").or("aos_status", QCP.equals, "结束"));
		else
			qFilters.add(new QFilter("aos_status", QCP.not_equals, "已完成").and("aos_status", QCP.not_equals, "结束"));
	}

	public static void showClose(IFormView iFormView) {
		IPageCache iPageCache = iFormView.getPageCache();
		String p_close_flag = iPageCache.get("p_close_flag");
		Map<String, Object> map = new HashMap<>();

		if (Cux_Common_Utl.IsNull(p_close_flag)) {
			iPageCache.put("p_close_flag", "true");
			map.put(ClientProperties.Text, new LocaleString("查询未关闭流程"));
			iFormView.updateControlMetadata("aos_showclose", map);
		} else if ("false".equals(p_close_flag)) {
			iPageCache.put("p_close_flag", "true");
			map.put(ClientProperties.Text, new LocaleString("查询未关闭流程"));
			iFormView.updateControlMetadata("aos_showclose", map);
		} else if ("true".equals(p_close_flag)) {
			iPageCache.put("p_close_flag", "false");
			map.put(ClientProperties.Text, new LocaleString("查询关闭流程"));
			iFormView.updateControlMetadata("aos_showclose", map);
		}
		iFormView.invokeOperation("refresh");
	}

	/** 对象类型转化 **/
	public static Object dynFormat(Object value) {
		if (Cux_Common_Utl.IsNull(value))
			return value;
		else if (value instanceof String)
			value = (String) value;
		else if (value instanceof Long)
			value = value + "";
		else
			value = ((DynamicObject) value).getString("id");
		return value;
	}

}
