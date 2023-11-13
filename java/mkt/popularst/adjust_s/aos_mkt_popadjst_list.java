package mkt.popularst.adjust_s;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import common.CommonDataSomQuo;
import common.Cux_Common_Utl;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.datamodel.ListSelectedRow;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.MKTCom;

public class aos_mkt_popadjst_list extends AbstractListPlugin {

	private final static String KEY_AOS_ASSIGN = "aos_assign_list";
	private final static String KEY_CANCEL_AOS_ASSIGN = "aos_cancel_assign_list";
	public final static String SYSTEM = Cux_Common_Utl.SYSTEM; // ID-000000
	private final static String BillID = "aos_mkt_pop_adjst";

	@Override
	public void setFilter(SetFilterEvent e) {
		long currentUserId = UserServiceHelper.getCurrentUserId();
		// 1.判断该人员是否销售人员 (品类人员对应表)
		QFilter salespersonOrg = MKTCom.querySalespersonOrg(currentUserId, "aos_orgid", "aos_groupid");
		// 2.判断该人员是否为审核人员
		Set<String> salesAuditorSet = CommonDataSomQuo.querySalesAuditors(currentUserId);
		List<Object> salesAuditorList = Arrays.asList(salesAuditorSet.toArray());
		List<QFilter> qFilters = e.getQFilters();
		qFilters.add(new QFilter("aos_orgid", QCP.in, salesAuditorList).or(salespersonOrg));
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		if (StringUtils.equals(KEY_AOS_ASSIGN, evt.getItemKey())) {
			// 列表认领按钮逻辑
			KEY_AOS_ASSIGN();
		} else if (StringUtils.equals(KEY_CANCEL_AOS_ASSIGN, evt.getItemKey())) {
			// 列表取消认领按钮逻辑
			KEY_CANCEL_AOS_ASSIGN();
		}
	}

	private void KEY_AOS_ASSIGN() {
		try {
			// 获取选中的行id
			List<ListSelectedRow> list = getSelectedRows();
			long User_id = UserServiceHelper.getCurrentUserId();// 操作人员
			// 判断用户是否是营销推广人员
			QFilter filter_user = new QFilter("user", "=", User_id);
			QFilter filter_roleNmae = new QFilter("role.name", "=", "营销推广人员");
			// 存在即为营销推广人员，否则为不是
			boolean exists = QueryServiceHelper.exists("perm_userrole", new QFilter[] { filter_user, filter_roleNmae });

			int error_count = 0;
			String error_message = "";
			for (int i = 0; i < list.size(); i++) {
				String para = list.get(i).toString();

				DynamicObject bill = BusinessDataServiceHelper.loadSingle(para, BillID);
				DynamicObject aos_makeby = (DynamicObject) bill.get("aos_makeby");

				long makeby_id = Long.valueOf(aos_makeby.getPkValue().toString());
				String Status = bill.get("billstatus").toString();
				String billno = bill.get("aos_billno").toString();

				if (!Status.equals("A")) {
					error_count++;
					error_message += billno + " 无法认领非新建状态的单据!";
				} else if (makeby_id == User_id) {
					error_count++;
					error_message += billno + " 你已认领本单!";
				} else if (!SYSTEM.equals("" + makeby_id) && !exists) {
					error_count++;
					error_message += billno + " 只能认领系统创建的单据!";
				} else {
					bill.set("aos_makeby", User_id);
					OperationResult operationrst = OperationServiceHelper.executeOperate("save", BillID,
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

	private void KEY_CANCEL_AOS_ASSIGN() {
		try {
			List<ListSelectedRow> list = getSelectedRows();
			long User_id = UserServiceHelper.getCurrentUserId();// 操作人员
			int error_count = 0;
			String error_message = "";

			for (int i = 0; i < list.size(); i++) {
				String para = list.get(i).toString();

				DynamicObject bill = BusinessDataServiceHelper.loadSingle(para, BillID);
				DynamicObject aos_makeby = (DynamicObject) bill.get("aos_makeby");
				long makeby_id = Long.valueOf(aos_makeby.getPkValue().toString());
				String Status = bill.get("billstatus").toString();
				String billno = bill.get("aos_billno").toString();

				if (!Status.equals("A")) {
					error_count++;
					error_message += billno + " 无法认领非新建状态的单据!";
				} else if (SYSTEM.equals("" + makeby_id)) {
					error_count++;
					error_message += billno + " 本单还未被认领,无法取消认领!";
				} else if (makeby_id != User_id) {
					error_count++;
					error_message += billno + " 只能取消认领自己认领的单据!";
				} else {
					bill.set("aos_makeby", SYSTEM);
					OperationResult operationrst = OperationServiceHelper.executeOperate("save", BillID,
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
			this.getView().showSuccessNotification("取消认领成功!");
		} catch (Exception e) {
			this.getView().showErrorNotification("取消认领失败");
			e.printStackTrace();
		}
	}

}