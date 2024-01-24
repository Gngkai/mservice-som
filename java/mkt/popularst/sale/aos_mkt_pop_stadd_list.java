package mkt.popularst.sale;

import common.Cux_Common_Utl;
import common.sal.permission.PermissionUtil;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.datamodel.ListSelectedRow;
import kd.bos.entity.operate.result.OperationResult;
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
 * @author create by gk
 * @date 2023/1/12 16:56
 * @action  st加回列表
 */
public class aos_mkt_pop_stadd_list extends AbstractListPlugin {
    String SYSTEM = Cux_Common_Utl.SYSTEM;
    private final static String entity="aos_mkt_pop_stadd";

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String itemKey = evt.getItemKey();
        if (itemKey.equals("aos_claim")){
            List<ListSelectedRow> list= getSelectedRows();
            long user= RequestContext.get().getCurrUserId();
            Claim(list,user);
        }
        else if (itemKey.equals("aos_noclaim")){
            List<ListSelectedRow>list=getSelectedRows();
            long user=RequestContext.get().getCurrUserId();
            Concel_claim(list,user);
        }
        else if (itemKey.equals("aos_init")){
            aos_mkt_pop_stadd_init.stInit();
        }
    }
    //确认认领逻辑
    private void Claim(List<ListSelectedRow> list, Long people) {
        try {
            int count = 0;
            String error = "";
            List<DynamicObject> dys = new ArrayList<>();
            for (ListSelectedRow r : list) {
                Long l = Long.valueOf(r.toString());
                DynamicObject dy = BusinessDataServiceHelper.loadSingle(l, entity, "aos_makeby,billno");
                if (!SYSTEM.equals(String.valueOf(dy.get("aos_makeby.masterid")))) {
                    count++;
                    error += r.getBillNo() + ";";
                }
                dy.set("aos_makeby", people);
                dys.add(dy);
            }
            if (count > 0) {
                this.getView().showTipNotification("单据" + error + "已被认领");
            } else {
                for (DynamicObject d : dys) {
                    OperationResult operationrst = OperationServiceHelper.executeOperate("save", entity, new DynamicObject[]{d}, OperateOption.create());
                }
                this.getView().showMessage("认领成功");
                reload();
            }
        }catch (Exception e){
            this.getView().showMessage(e.getMessage());
        }
    }
    //取消认领逻辑
    private void Concel_claim(List<ListSelectedRow> list, long people) {
        try {
            int count = 0;
            int count1 = 0;
            int count2 = 0;
            String error = "";
            String error1 = "";
            String error2 = "";
            List<DynamicObject> dys = new ArrayList<>();
            for (ListSelectedRow r : list) {
                Long l = Long.valueOf(r.toString());
                DynamicObject dy = BusinessDataServiceHelper.loadSingle(l, entity, "aos_makeby,billno");
                if (!String.valueOf(people).equals(String.valueOf(dy.get("aos_makeby.masterid"))) & !SYSTEM.equals(String.valueOf(dy.get("aos_makeby.masterid")))) {
                    count++;
                    error += "单据" + r.getBillNo() + "非本人认领，无法取消;";
                }
                if (SYSTEM.equals(String.valueOf(dy.get("aos_makeby.masterid")))) {
                    count1++;
                    error1 += "单据" + r.getBillNo() + "还未被认领，无法取消;";
                }
                if (String.valueOf(people).equals(String.valueOf(dy.get("aos_makeby.masterid"))) & !("A").equals(r.getBillStatus())) {
                    count2++;
                    error2 += "单据" + r.getBillNo() + "非新建状态，无法取消认领";
                }
                dy.set("aos_makeby", SYSTEM);
                dys.add(dy);
            }
            if (count > 0 || count1 > 0 || count2 > 0) {
                this.getView().showTipNotification(error + error1 + error2);
            } else {
                for (DynamicObject d : dys) {
                    OperationResult operationrst = OperationServiceHelper.executeOperate("save", entity, new DynamicObject[]{d}, OperateOption.create());
                }
                this.getView().showMessage("取消认领成功");
                reload();
            }
        }catch (Exception e){
            this.getView().showMessage(e.getMessage());
        }
    }

    @Override
    public void setFilter(SetFilterEvent e) {
        //销售人员国别维护表
        long currentUserId = UserServiceHelper.getCurrentUserId();
        QFilter qFilter = PermissionUtil.getOrgGroupQFilterForSale(currentUserId, "aos_orgid", "aos_groupid");
        e.addCustomQFilter(qFilter);
    }

}
