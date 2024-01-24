package mkt.popularst.sale;

import common.Cux_Common_Utl;
import common.sal.permission.PermissionUtil;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.datamodel.ListSelectedRow;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * @author aosom
 * @version ST加回-列表插件
 */
public class AosMktPopStaddList extends AbstractListPlugin {
    private final static String ENTITY = "aos_mkt_pop_stadd";
    private final static String SYSTEM = Cux_Common_Utl.SYSTEM;
    private final static String AOS_CLAIM = "aos_claim";
    private final static String AOS_NOCLAIM = "aos_noclaim";
    private final static String AOS_INIT = "aos_init";

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String itemKey = evt.getItemKey();
        if (AOS_CLAIM.equals(itemKey)) {
            List<ListSelectedRow> list = getSelectedRows();
            long user = RequestContext.get().getCurrUserId();
            claim(list, user);
        } else if (AOS_NOCLAIM.equals(itemKey)) {
            List<ListSelectedRow> list = getSelectedRows();
            long user = RequestContext.get().getCurrUserId();
            concelClaim(list, user);
        } else if (AOS_INIT.equals(itemKey)) {
            AosMktPopStaddTask.stInit();
        }
    }

    private void claim(List<ListSelectedRow> list, Long people) {
        try {
            int count = 0;
            StringBuilder error = new StringBuilder();
            List<DynamicObject> dys = new ArrayList<>();
            for (ListSelectedRow r : list) {
                Long l = Long.valueOf(r.toString());
                DynamicObject dy = BusinessDataServiceHelper.loadSingle(l, ENTITY, "aos_makeby,billno");
                if (!SYSTEM.equals(String.valueOf(dy.get("aos_makeby.masterid")))) {
                    count++;
                    error.append(r.getBillNo()).append(";");
                }
                dy.set("aos_makeby", people);
                dys.add(dy);
            }
            if (count > 0) {
                this.getView().showTipNotification("单据" + error + "已被认领");
            } else {
                for (DynamicObject d : dys) {
                    OperationServiceHelper.executeOperate("save", ENTITY, new DynamicObject[] {d},
                        OperateOption.create());
                }
                this.getView().showMessage("认领成功");
                reload();
            }
        } catch (Exception e) {
            this.getView().showMessage(e.getMessage());
        }
    }

    private void concelClaim(List<ListSelectedRow> list, long people) {
        try {
            int count = 0;
            int count1 = 0;
            int count2 = 0;
            StringBuilder error = new StringBuilder();
            StringBuilder error1 = new StringBuilder();
            StringBuilder error2 = new StringBuilder();
            List<DynamicObject> dys = new ArrayList<>();
            for (ListSelectedRow r : list) {
                Long l = Long.valueOf(r.toString());
                DynamicObject dy = BusinessDataServiceHelper.loadSingle(l, ENTITY, "aos_makeby,billno");
                if (!String.valueOf(people).equals(String.valueOf(dy.get("aos_makeby.masterid")))
                    & !SYSTEM.equals(String.valueOf(dy.get("aos_makeby.masterid")))) {
                    count++;
                    error.append("单据").append(r.getBillNo()).append("非本人认领，无法取消;");
                }
                if (SYSTEM.equals(String.valueOf(dy.get("aos_makeby.masterid")))) {
                    count1++;
                    error1.append("单据").append(r.getBillNo()).append("还未被认领，无法取消;");
                }
                if (String.valueOf(people).equals(String.valueOf(dy.get("aos_makeby.masterid")))
                    & !("A").equals(r.getBillStatus())) {
                    count2++;
                    error2.append("单据").append(r.getBillNo()).append("非新建状态，无法取消认领");
                }
                dy.set("aos_makeby", SYSTEM);
                dys.add(dy);
            }
            if (count > 0 || count1 > 0 || count2 > 0) {
                this.getView().showTipNotification(error + error1.toString() + error2);
            } else {
                for (DynamicObject d : dys) {
                    OperationServiceHelper.executeOperate("save", ENTITY, new DynamicObject[] {d},
                        OperateOption.create());
                }
                this.getView().showMessage("取消认领成功");
                reload();
            }
        } catch (Exception e) {
            this.getView().showMessage(e.getMessage());
        }
    }

    @Override
    public void setFilter(SetFilterEvent e) {
        // 销售人员国别维护表
        long currentUserId = UserServiceHelper.getCurrentUserId();
        QFilter qFilter = PermissionUtil.getOrgGroupQFilterForSale(currentUserId, "aos_orgid", "aos_groupid");
        e.addCustomQFilter(qFilter);
    }

}
