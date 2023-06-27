package mkt.act.coupon.list;

import common.sal.permission.PermissionUtil;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.user.UserServiceHelper;

/**
 * @author: lch
 * @createDate: 2022/11/11
 * @description:
 * @updateRemark:
 */
public class CouponCancelListPlugin extends AbstractListPlugin {

    @Override
    public void setFilter(SetFilterEvent e) {
        QFilter aos_makeyby = PermissionUtil.getOrgQFilterForSale(UserServiceHelper.getCurrentUserId(), "aos_makeyby");
        e.addCustomQFilter(aos_makeyby);
    }

    @Override
    public void afterDoOperation(AfterDoOperationEventArgs afterDoOperationEventArgs) {

    }
}
