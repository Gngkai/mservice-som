package mkt.act.query;

import common.sal.permission.PermissionUtil;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;

public class aos_mkt_act_quert_list extends AbstractListPlugin {
	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		if (StringUtils.equals("aos_baritemap", evt.getItemKey())) {
			// 删除本操作人所有数据
			String currentUserId = UserServiceHelper.getCurrentUserId() + "";
			QFilter filter_id = new QFilter("creator", "=", currentUserId);
			QFilter[] filters_st = new QFilter[] { filter_id };
			DeleteServiceHelper.delete("aos_mkt_actquery", filters_st);
		}
	}


	@Override
	public void setFilter(SetFilterEvent e) {
		long currentUserId = UserServiceHelper.getCurrentUserId();
		QFilter qFilter = PermissionUtil.getOrgQFilterForSale(currentUserId, "aos_orgid");
		e.addCustomQFilter(qFilter);
	}
}
