package mkt.act.execute;

import java.util.List;

import common.Cux_Common_Utl;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.datamodel.ListSelectedRow;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;

public class MktActExecuteList extends AbstractListPlugin {
	public final static String SYSTEM = Cux_Common_Utl.SYSTEM; // ID-000000

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		if (StringUtils.equals("aos_assign", evt.getItemKey())) {
			aos_assign();
		}
	}

	/**
	 * 认领按钮逻辑
	 */
	private void aos_assign() {
		// 列表认领按钮逻辑
		try {
			// 获取选中的行id
			List<ListSelectedRow> list = getSelectedRows();
			long User_id = UserServiceHelper.getCurrentUserId();// 操作人员
			int error_count = 0;
			String error_message = "";
			for (int i = 0; i < list.size(); i++) {
				String para = list.get(i).toString();
				DynamicObject bill = BusinessDataServiceHelper.loadSingle(para, "aos_mkt_trackerexe");
				DynamicObject aos_makeby = (DynamicObject) bill.get("aos_makeby");
				long makeby_id = Long.valueOf(aos_makeby.getPkValue().toString());
				String Status = bill.get("aos_status").toString();
				String billno = bill.get("billno").toString();

				if (!Status.equals("新建")) {
					error_count++;
					error_message += billno + " 无法认领非新建状态的单据!";
				} else if (makeby_id == User_id) {
					error_count++;
					error_message += billno + " 你已认领本单!";
				} else if (!SYSTEM.equals("" + makeby_id)) {
					error_count++;
					error_message += billno + " 只能认领系统创建的单据!";
				} else {
					bill.set("aos_makeby", User_id);
					OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_trackerexe",
							new DynamicObject[] { bill }, OperateOption.create());
					if (operationrst.getValidateResult().getValidateErrors().size() != 0) {
						error_count++;
						error_message += billno + " 保存失败!";
					}
				}
			}

			// 重新加载界面
			reload();
			// 校验报错
			if (error_count > 0) {
				this.getView().showTipNotification(error_message);
				return;
			}
			// 弹出提示框
			this.getView().showSuccessNotification("认领成功!");
		} catch (Exception e) {
			this.getView().showErrorNotification("认领失败");
			e.printStackTrace();
		}
	}
}
