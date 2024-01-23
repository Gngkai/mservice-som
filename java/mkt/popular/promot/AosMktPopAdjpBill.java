package mkt.popular.promot;

import java.math.BigDecimal;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.db.DB;
import kd.bos.db.DBRoute;
import kd.bos.form.control.events.ClickListener;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.popular.budget.AosMktPopBudgetpTask;

/**
 * @author aosom
 * @version 出价调整推广-表单插件
 * @deprecated
 */
public class AosMktPopAdjpBill extends AbstractBillPlugIn implements ItemClickListener, ClickListener {
    private static final String DB_MKT = "aos.mkt";
    private static final String AOS_CONFIRM = "aos_confirm";
    private static final String B = "B";
    private static final String SAVE = "save";

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        // 给工具栏加监听事件
        this.addItemClickListeners("aos_toolbarap");
        this.addItemClickListeners("aos_confirm");
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String control = evt.getItemKey();
        if (AOS_CONFIRM.equals(control)) {
            aosConfirm();
        }
    }

    private void aosConfirm() {
        Object aosPpcid = this.getModel().getValue("aos_ppcid");
        DynamicObject aosMktPopularPpc = BusinessDataServiceHelper.loadSingle(aosPpcid, "aos_mkt_popular_ppc");
        aosMktPopularPpc.set("aos_adjustsp", true);
        OperationServiceHelper.executeOperate("save", "aos_mkt_popular_ppc", new DynamicObject[] {aosMktPopularPpc},
            OperateOption.create());
        boolean aosAdjusts = aosMktPopularPpc.getBoolean("aos_adjusts");
        if (aosAdjusts) {
            String pOuCode = ((DynamicObject)this.getModel().getValue("aos_orgid")).getString("number");
            // 开始生成 出价调整(销售)
            Map<String, Object> budget = new HashMap<>(16);
            budget.put("p_ou_code", pOuCode);
            AosMktPopBudgetpTask.executerun(budget);
        }
        this.getModel().setValue("aos_status", "B");
        this.getView().invokeOperation("save");
        this.getView().setEnable(false, "aos_entryentity");
        this.getView().setEnable(false, "bar_save");
        this.getView().setEnable(false, "aos_confirm");
    }

    @Override
    public void afterLoadData(EventObject e) {
        super.afterLoadData(e);
        statusControl();
    }

    private void statusControl() {
        // 操作人员
        long user = UserServiceHelper.getCurrentUserId();
        DynamicObject aosMakeby = (DynamicObject)this.getModel().getValue("aos_makeby");
        long makebyId = (Long)aosMakeby.getPkValue();
        // 如果当前用户不为录入人则全锁定
        if (user != makebyId) {
            this.getView().setEnable(false, "aos_entryentity");
            this.getView().setEnable(false, "bar_save");
            this.getView().setEnable(false, "aos_confirm");
        } else {
            this.getView().setEnable(true, "aos_entryentity");
            this.getView().setEnable(true, "bar_save");
            this.getView().setEnable(true, "aos_confirm");
        }
        String aosStatus = String.valueOf(this.getModel().getValue("aos_status"));
        if (B.equals(aosStatus)) {
            this.getView().setEnable(false, "aos_entryentity");
            this.getView().setEnable(false, "bar_save");
            this.getView().setEnable(false, "aos_confirm");
        }
        DynamicObject ouCode = (DynamicObject)this.getModel().getValue("aos_orgid");
        this.getView().setFormTitle(new LocaleString("SP出价调整(推广)-" + (ouCode.get("name").toString().charAt(0))));
    }

    @Override
    public void afterDoOperation(AfterDoOperationEventArgs args) {
        super.afterDoOperation(args);
        String key = args.getOperateKey();
        if (SAVE.equals(key)) {
            aosSave();
        }
    }

    private void aosSave() {
        int rowCount = this.getModel().getEntryRowCount("aos_entryentity");
        for (int i = 0; i < rowCount; i++) {
            BigDecimal aosAdjprice = (BigDecimal)this.getModel().getValue("aos_adjprice", i);
            long aosPpcentryid = (long)this.getModel().getValue("aos_ppcentryid", i);
            update(aosAdjprice, aosPpcentryid);
        }
    }

    private void update(BigDecimal aosAdjprice, long aosPpcentryid) {
        String sql =
            " UPDATE tk_aos_mkt_popular_ppc_r r " + " SET fk_aos_bid = ?" + " WHERE 1=1 " + " and r.FEntryId = ?  ";
        Object[] params = {aosAdjprice, aosPpcentryid};
        DB.execute(DBRoute.of(DB_MKT), sql, params);
    }

}