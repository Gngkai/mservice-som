package mkt.act.query;

import common.sal.permission.PermissionUtil;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.user.UserServiceHelper;

public class aos_mkt_act_overseaconfirm_list extends AbstractListPlugin {

    @Override
    public void setFilter(SetFilterEvent e) {
        long currentUserId = UserServiceHelper.getCurrentUserId();
        QFilter qFilter = PermissionUtil.getOrgQFilterForSale(currentUserId, "aos_orgid");
        e.addCustomQFilter(qFilter);
    }
}
