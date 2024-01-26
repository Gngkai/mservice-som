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
 * @version 参数信息工具类
 *
 */
public class ParaInfoUtil {
    public final static String TRUE = "true";
    public final static String FALSE = "false";
    public final static String ALL = "all";
    public final static String P_CLOSE_FLAG = "p_close_flag";

    /**
     * 处理营销工作流通用列表查询权限
     **/
    public static void setRights(List<QFilter> qFilters, IPageCache iPageCache, String type, String... otherFilter) {
        long currentUserId = UserServiceHelper.getCurrentUserId();
        QFilter filter = new QFilter("aos_user.id", QCP.equals, currentUserId);
        // 判断是否为工作流管理员
        QFilter filter2 = new QFilter("aos_process", QCP.equals, true);
        boolean exists = QueryServiceHelper.exists("aos_mkt_userights", new QFilter[] {filter, filter2});
        // 1.工作流管理员不做任何人员相关权限控制
        if (!exists) {
            // 2.只能看到申请人为自己或当前节点操作人为自己的单据 或流经自己的单据
            // 获取历史记录中 流经自己的单据
            List<Long> list = getHisList(type, currentUserId);
            QFilter filterUser = new QFilter("aos_user", QCP.equals, currentUserId)
                .or("aos_requireby", QCP.equals, currentUserId).or("id", QCP.in, list);
            for (String otherUser : otherFilter) {
                filterUser = filterUser.or(otherUser, "=", currentUserId);
            }
            qFilters.add(filterUser);
        }
        // 3.若未点击查看关闭流程(界面缓存参数为空) 则排除已关闭的流程
        if (TRUE.equals(iPageCache.get(P_CLOSE_FLAG))) {
            qFilters.add(new QFilter("aos_status", QCP.equals, "已完成").or("aos_status", QCP.equals, "结束"));
        } else if (FALSE.equals(iPageCache.get(P_CLOSE_FLAG))) {
            if (!exists) {
                qFilters.add(new QFilter("aos_user.number", QCP.not_equals, "SYSTEM"));
            }
            qFilters.add(new QFilter("aos_status", QCP.not_equals, "已完成").and("aos_status", QCP.not_equals, "结束"));
        } else if (ALL.equals(iPageCache.get(P_CLOSE_FLAG))) {
            if (!exists) {
                qFilters.add(new QFilter("aos_user.number", QCP.not_equals, "SYSTEM"));
            }
        } else {
            if (!exists) {
                qFilters.add(new QFilter("aos_user.number", QCP.not_equals, "SYSTEM"));
            }
            qFilters.add(new QFilter("aos_status", QCP.not_equals, "已完成").and("aos_status", QCP.not_equals, "结束"));
        }
    }

    private static List<Long> getHisList(String type, long currentUserId) {
        List<Long> list = new ArrayList<>();
        QFilter filterType = new QFilter("aos_formid", QCP.equals, type);
        QFilter filterBy = new QFilter("aos_actionby", QCP.equals, currentUserId);
        DynamicObjectCollection aosSyncOperateS =
            QueryServiceHelper.query("aos_sync_operate", "aos_sourceid", new QFilter[] {filterType, filterBy});
        for (DynamicObject aosSyncOperate : aosSyncOperateS) {
            list.add(aosSyncOperate.getLong("aos_sourceid"));
        }
        return list;
    }

    /**
     * 处理营销工作流通用列表查询权限
     **/
    public static void setPhotoRights(List<QFilter> qFilters, IPageCache iPageCache, String... otherFilter) {
        long currentUserId = UserServiceHelper.getCurrentUserId();
        QFilter filter = new QFilter("aos_user.id", QCP.equals, currentUserId);
        // 判断是否为工作流管理员
        QFilter filter2 = new QFilter("aos_process", QCP.equals, true);
        boolean exists = QueryServiceHelper.exists("aos_mkt_userights", new QFilter[] {filter, filter2});
        // 1.工作流管理员不做任何人员相关权限控制
        if (!exists) {
            // 2.只能看到申请人为自己或当前节点操作人为自己的单据
            QFilter filterUser = new QFilter("aos_requireby", QCP.equals, currentUserId);
            for (String otherUser : otherFilter) {
                filterUser = filterUser.or(otherUser, "=", currentUserId);
            }
            qFilters.add(filterUser);
        }
    }

    /** 处理营销工作流设计专用列表查询权限 到小组维度 **/
    public static void setRightsForDesign(List<QFilter> qFilters, IPageCache iPageCache) {
        // 3.若未点击查看关闭流程(界面缓存参数为空) 则排除已关闭的流程
        if (TRUE.equals(iPageCache.get(P_CLOSE_FLAG))) {
            qFilters.add(new QFilter("aos_status", QCP.equals, "已完成").or("aos_status", QCP.equals, "结束"));
        } else {
            qFilters.add(new QFilter("aos_status", QCP.not_equals, "已完成").and("aos_status", QCP.not_equals, "结束"));
        }
    }

    public static void showClose(IFormView iFormView) {
        IPageCache iPageCache = iFormView.getPageCache();
        String pCloseFlag = iPageCache.get("p_close_flag");
        Map<String, Object> map = new HashMap<>(16);
        if (Cux_Common_Utl.IsNull(pCloseFlag)) {
            iPageCache.put("p_close_flag", "true");
            map.put(ClientProperties.Text, new LocaleString("查询未关闭流程"));
            iFormView.updateControlMetadata("aos_showclose", map);
        } else if (FALSE.equals(pCloseFlag)) {
            iPageCache.put("p_close_flag", "true");
            map.put(ClientProperties.Text, new LocaleString("查询未关闭流程"));
            iFormView.updateControlMetadata("aos_showclose", map);
        } else if (TRUE.equals(pCloseFlag)) {
            iPageCache.put("p_close_flag", "false");
            map.put(ClientProperties.Text, new LocaleString("查询关闭流程"));
            iFormView.updateControlMetadata("aos_showclose", map);
        }
        iFormView.invokeOperation("refresh");
    }

    /** 对象类型转化 **/
    public static Object dynFormat(Object value) {
        if (Cux_Common_Utl.IsNull(value)) {
            return value;
        } else if (value instanceof Long) {
            value = String.valueOf(value);
        } else {
            value = ((DynamicObject)value).getString("id");
        }
        return value;
    }
}
