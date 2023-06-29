package mkt.popular.adjust_p;

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
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.control.events.ClickListener;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.popular.budget_p.aos_mkt_popbudgetp_init;

public class aos_mkt_popadjp_bill extends AbstractBillPlugIn implements ItemClickListener, ClickListener {

	private static final String DB_MKT = "aos.mkt";

	public void registerListener(EventObject e) {
		super.registerListener(e);
		// 给工具栏加监听事件
		this.addItemClickListeners("aos_toolbarap");
		this.addItemClickListeners("aos_confirm");
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String Control = evt.getItemKey();
		if (Control.equals("aos_confirm"))
			aos_confirm();
	}

	private void aos_confirm() {
		System.out.println("==== aos_confirm ====");
		Object aos_ppcid = this.getModel().getValue("aos_ppcid");
		DynamicObject aos_mkt_popular_ppc = BusinessDataServiceHelper.loadSingle(aos_ppcid, "aos_mkt_popular_ppc");
		aos_mkt_popular_ppc.set("aos_adjustsp", true);
		OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_popular_ppc",
				new DynamicObject[] { aos_mkt_popular_ppc }, OperateOption.create());
		if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
		}
		
		Boolean aos_adjusts = aos_mkt_popular_ppc.getBoolean("aos_adjusts");
		if (aos_adjusts) {
			String p_ou_code = ((DynamicObject) this.getModel().getValue("aos_orgid")).getString("number");
			// 开始生成 出价调整(销售)
			Map<String, Object> budget = new HashMap<>();
			budget.put("p_ou_code", p_ou_code);
			aos_mkt_popbudgetp_init.executerun(budget);
		}
		
		this.getModel().setValue("aos_status", "B");
		this.getView().invokeOperation("save");
		this.getView().setEnable(false, "aos_entryentity");
		this.getView().setEnable(false, "bar_save");
		this.getView().setEnable(false, "aos_confirm");
	}

	public void afterLoadData(EventObject e) {
		super.afterLoadData(e);
		StatusControl();
	}

	private void StatusControl() {
		long User = UserServiceHelper.getCurrentUserId();// 操作人员
		DynamicObject aos_makeby = (DynamicObject) this.getModel().getValue("aos_makeby");
		long makeby_id = (Long) aos_makeby.getPkValue();
		// 如果当前用户不为录入人则全锁定
		if (User != makeby_id) {
			this.getView().setEnable(false, "aos_entryentity");
			this.getView().setEnable(false, "bar_save");
			this.getView().setEnable(false, "aos_confirm");
		} else {
			this.getView().setEnable(true, "aos_entryentity");
			this.getView().setEnable(true, "bar_save");
			this.getView().setEnable(true, "aos_confirm");
		}

		String aos_status = this.getModel().getValue("aos_status") + "";
		if (aos_status.equals("B")) {
			this.getView().setEnable(false, "aos_entryentity");
			this.getView().setEnable(false, "bar_save");
			this.getView().setEnable(false, "aos_confirm");
		}
		
		DynamicObject ou_code = (DynamicObject) this.getModel().getValue("aos_orgid");
		this.getView().setFormTitle(new LocaleString("SP出价调整(推广)-" + (ou_code.get("name").toString().substring(0, 1))));
		
	}

	public void afterDoOperation(AfterDoOperationEventArgs args) {
		super.afterDoOperation(args);
		String key = args.getOperateKey();
		if ("save".equals(key)) {
			aos_save();
		}
	}

	private void aos_save() {
		int RowCount = this.getModel().getEntryRowCount("aos_entryentity");
		for (int i = 0; i < RowCount; i++) {
			BigDecimal aos_adjprice = (BigDecimal) this.getModel().getValue("aos_adjprice", i);
			long aos_ppcentryid = (long) this.getModel().getValue("aos_ppcentryid", i);
			Update(aos_adjprice, aos_ppcentryid);
		}
	}

	private void Update(BigDecimal aos_adjprice, long aos_ppcentryid) {
		String sql = " UPDATE tk_aos_mkt_popular_ppc_r r " + " SET fk_aos_bid = ?" + " WHERE 1=1 "
				+ " and r.FEntryId = ?  ";
		Object[] params = { aos_adjprice, aos_ppcentryid };
		DB.execute(DBRoute.of(DB_MKT), sql, params);
	}

}