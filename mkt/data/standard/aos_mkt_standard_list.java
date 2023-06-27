package mkt.data.standard;

import java.util.ArrayList;
import java.util.List;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;

public class aos_mkt_standard_list extends AbstractListPlugin {
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
}
