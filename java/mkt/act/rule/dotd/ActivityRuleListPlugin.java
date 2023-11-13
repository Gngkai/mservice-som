package mkt.act.rule.dotd;

import common.CommonDataSomDis;
import common.sal.permission.PermissionUtil;
import kd.bos.entity.datamodel.ListSelectedRow;
import kd.bos.form.events.BeforeDoOperationEventArgs;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.form.operate.FormOperate;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.user.UserServiceHelper;

import java.util.List;

/**
 * lch
 * 2022-6-24
 */
public class ActivityRuleListPlugin extends AbstractListPlugin {

    @Override
    public void setFilter(SetFilterEvent e) {
        QFilter qFilter = PermissionUtil.getOrgGroupQFilterForSale(UserServiceHelper.getCurrentUserId(), "aos_orgid", "aos_groupid");
        e.addCustomQFilter(qFilter);
    }

    @Override
    public void beforeDoOperation(BeforeDoOperationEventArgs args) {
        List<ListSelectedRow> list = getSelectedRows();
        FormOperate formOperate = (FormOperate) args.getSource();
        String operateKey = formOperate.getOperateKey();
        if ("aos_claim".equals(operateKey)) {
            // 认领
            CommonDataSomDis.claim(this, list, "aos_mkt_activityrule", "aos_makeby");
            this.reload();
        }

        if ("aos_unclaim".equals(operateKey)) {
            // 取消认领
            CommonDataSomDis.unClaim(this, list, "aos_mkt_activityrule", "aos_makeby");
            this.reload();
        }
    }
}
