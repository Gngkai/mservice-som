package mkt.popular.promot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import common.CommonDataSom;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.datamodel.ListSelectedRow;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.AosMktCacheUtil;

/**
 * @author aosom
 * @version 出价调整推广-列表插件新
 */
public class AosMktPopAdjpnList extends AbstractListPlugin {
    public final static String SYSTEM = getSystemId();
    private final static String KEY_AOS_ASSIGN = "aos_assign_list";
    private final static String KEY_CANCEL_AOS_ASSIGN = "aos_cancel_assign_list";
    private final static String AOS_INIT = "aos_init";

    public static String getSystemId() {
        QFilter qfUser = new QFilter("number", "=", "system");
        DynamicObject dyUser = QueryServiceHelper.queryOne("bos_user", "id", new QFilter[] {qfUser});
        return dyUser.get("id").toString();
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        if (StringUtils.equals(AOS_INIT, evt.getItemKey())) {
            // PPC数据源初始化
            aosInit();
        } else if (StringUtils.equals(KEY_AOS_ASSIGN, evt.getItemKey())) {
            // 列表认领按钮逻辑
            keyAssign();
        } else if (StringUtils.equals(KEY_CANCEL_AOS_ASSIGN, evt.getItemKey())) {
            // 列表取消认领按钮逻辑
            keyCancelAssign();
        }
    }

    private void aosInit() {
        // PPC数据源初始化
        CommonDataSom.init();
        AosMktCacheUtil.initRedis("ppc");
        Map<String, Object> params = new HashMap<>(16);
        params.put("p_ou_code", "UK");
        AosMktPopAdjpTask.executerun(params);
        this.getView().invokeOperation("refresh");
        this.getView().showSuccessNotification("已手工提交出价调整(推广)初始化,请等待,务重复提交!");
    }

    private void keyAssign() {
        // 列表认领按钮逻辑
        try {
            // 获取选中的行id
            List<ListSelectedRow> list = getSelectedRows();
            long userId = UserServiceHelper.getCurrentUserId();
            int errorCount = 0;
            StringBuilder errorMessage = new StringBuilder();
            for (ListSelectedRow listSelectedRow : list) {
                String para = listSelectedRow.toString();
                DynamicObject bill = BusinessDataServiceHelper.loadSingle(para, "aos_mkt_popular_adjpn");
                DynamicObject aosMakeby = (DynamicObject)bill.get("aos_makeby");
                long makebyId = Long.parseLong(aosMakeby.getPkValue().toString());
                String status = bill.get("billstatus").toString();
                String billno = bill.get("billno").toString();
                if (!"A".equals(status)) {
                    errorCount++;
                    errorMessage.append(billno).append(" 无法认领非新建状态的单据!");
                } else if (makebyId == userId) {
                    errorCount++;
                    errorMessage.append(billno).append(" 你已认领本单!");
                } else if (!SYSTEM.equals(String.valueOf(makebyId))) {
                    errorCount++;
                    errorMessage.append(billno).append(" 只能认领系统创建的单据!");
                } else {
                    bill.set("aos_makeby", userId);
                    OperationResult operationrst = OperationServiceHelper.executeOperate("save",
                        "aos_mkt_popular_adjpn", new DynamicObject[] {bill}, OperateOption.create());
                    if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                        errorCount++;
                        errorMessage.append(billno).append(" 保存失败!");
                    }
                }
            }
            // 重新加载界面
            reload();
            // 校验报错
            if (errorCount > 0) {
                this.getView().showTipNotification(String.valueOf(errorMessage));
                return;
            }
            // 弹出提示框
            this.getView().showSuccessNotification("认领成功!");
        } catch (Exception e) {
            this.getView().showErrorNotification("认领失败");
            e.printStackTrace();
        }
    }

    private void keyCancelAssign() {
        // 列表取消认领按钮逻辑
        try {
            List<ListSelectedRow> list = getSelectedRows();
            // 操作人员
            long userId = UserServiceHelper.getCurrentUserId();
            int errorCount = 0;
            StringBuilder errorMessage = new StringBuilder();
            for (ListSelectedRow listSelectedRow : list) {
                String para = listSelectedRow.toString();
                DynamicObject bill = BusinessDataServiceHelper.loadSingle(para, "aos_mkt_popular_adjpn");
                DynamicObject aosMakeby = (DynamicObject)bill.get("aos_makeby");
                long makebyId = Long.parseLong(aosMakeby.getPkValue().toString());
                String status = bill.get("billstatus").toString();
                String billno = bill.get("billno").toString();
                if (!"A".equals(status)) {
                    errorCount++;
                    errorMessage.append(billno).append(" 无法认领非新建状态的单据!");
                } else if (SYSTEM.equals(String.valueOf(makebyId))) {
                    errorCount++;
                    errorMessage.append(billno).append(" 本单还未被认领,无法取消认领!");
                } else if (makebyId != userId) {
                    errorCount++;
                    errorMessage.append(billno).append(" 只能取消认领自己认领的单据!");
                } else {
                    bill.set("aos_makeby", SYSTEM);
                    OperationResult operationrst = OperationServiceHelper.executeOperate("save",
                        "aos_mkt_popular_adjpn", new DynamicObject[] {bill}, OperateOption.create());
                    if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
                        errorCount++;
                        errorMessage.append(billno).append(" 保存失败!");
                    }
                }
            }

            // 重新加载界面
            reload();
            // 校验报错
            if (errorCount > 0) {
                this.getView().showTipNotification(errorMessage.toString());
                return;
            }
            // 弹出提示框
            this.getView().showSuccessNotification("取消认领成功!");
        } catch (Exception e) {
            this.getView().showErrorNotification("取消认领失败");
            e.printStackTrace();
        }
    }

}