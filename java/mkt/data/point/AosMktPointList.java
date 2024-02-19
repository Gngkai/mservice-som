package mkt.data.point;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.lang.Lang;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;

import java.util.*;

/**
 * @author aosom
 * @version 品名关键词库列表插件
 */
public class AosMktPointList extends AbstractListPlugin {
    public final static String ZH = "zh";
    @Override
    public void setFilter(SetFilterEvent e) {
        try {
            long currentUserId = UserServiceHelper.getCurrentUserId();
            DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_userights",
                "entryentity.aos_orgid aos_orgid", new QFilter[] {new QFilter("aos_user", QCP.equals, currentUserId)});
            List<String> orgList = new ArrayList<>();
            for (DynamicObject obj : list) {
                orgList.add(obj.getString("aos_orgid"));
            }
            QFilter qFilter = new QFilter("aos_orgid", QCP.in, orgList);
            List<QFilter> qFilters = e.getQFilters();
            qFilters.add(qFilter);
        } catch (Exception ex) {
            this.getView().showErrorNotification("setFilter = " + ex);
        }
    }

    @Override
    public void afterBindData(EventObject e) {
        super.afterBindData(e);
        Locale locale = Lang.get().getLocale();
        List<String> list;
        if (ZH.equals(locale.getLanguage())) {
            list = Arrays.asList("aos_en_category1", "aos_en_category2", "aos_en_category3");
        } else {
            list = Arrays.asList("aos_itemnamecn_s", "aos_category1_name", "aos_category2_name", "aos_category3_name");
        }
        this.getView().setVisible(false, list.toArray(new String[0]));
    }
}
