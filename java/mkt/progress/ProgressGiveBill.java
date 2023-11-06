package mkt.progress;

import common.fnd.FndGlobal;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.form.events.BeforeDoOperationEventArgs;
import kd.bos.form.operate.FormOperate;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;

public class ProgressGiveBill extends AbstractBillPlugIn {

	/** 操作之前事件 **/
	public void beforeDoOperation(BeforeDoOperationEventArgs args) {
		FormOperate formOperate = (FormOperate) args.getSource();
		String Operatation = formOperate.getOperateKey();
		try {
			if ("save".equals(Operatation)) {
				Object aos_user = this.getModel().getValue("aos_user");
				if (FndGlobal.IsNotNull(aos_user)) {
					DynamicObject bos_user = QueryServiceHelper.queryOne("bos_user", "id",
							new QFilter("name", QCP.equals, aos_user).toArray());
					if (FndGlobal.IsNull(bos_user)) {
						this.getView().showErrorNotification("人员信息不存在!");
						throw new Exception();
					}
				}
			}
		} catch (Exception ex) {
			args.setCancel(true);
		}
	}
}
