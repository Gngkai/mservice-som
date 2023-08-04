package mkt.data.point;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.form.field.FieldEdit;
import kd.bos.lang.Lang;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;

import java.util.*;

public class aos_mkt_point_list extends AbstractListPlugin {
    @Override
    public void setFilter(SetFilterEvent e) {
        try {
            long CurrentUserId = UserServiceHelper.getCurrentUserId();
            DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_userights",
					"entryentity.aos_orgid aos_orgid",
					new QFilter[] { new QFilter("aos_user", QCP.equals, CurrentUserId) });
			List<String> orgList = new ArrayList<>();
			for (DynamicObject obj : list) {
				orgList.add(obj.getString("aos_orgid"));
			}
			QFilter qFilter = new QFilter("aos_orgid", QCP.in, orgList);
            List<QFilter> qFilters = e.getQFilters();
            qFilters.add(qFilter);
        } catch (Exception ex) {
            this.getView().showErrorNotification("setFilter = " + ex.toString());
        }
    }

    @Override
    public void afterBindData(EventObject e) {
        super.afterBindData(e);
        Locale locale = Lang.get().getLocale();
        List<String> list;
        if (locale.getLanguage().equals("zh")){
            list = Arrays.asList("aos_en_category1","aos_en_category2","aos_en_category3");
        }
        else {
            list = Arrays.asList("aos_itemnamecn_s","aos_category1_name", "aos_category2_name", "aos_category3_name");
        }
        this.getView().setVisible(false,list.toArray(new String[0]));
    }
}
