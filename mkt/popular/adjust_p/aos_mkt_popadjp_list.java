package mkt.popular.adjust_p;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import mkt.common.aos_mkt_common_redis;
import sal.quote.CommData;

public class aos_mkt_popadjp_list extends AbstractListPlugin {

	private final static String KEY_AOS_ASSIGN = "aos_assign_list";
	private final static String KEY_CANCEL_AOS_ASSIGN = "aos_cancel_assign_list";
	public final static String SYSTEM = get_system_id(); // ID-000000
	
	public static String get_system_id() {
		QFilter qf_user = new QFilter("number", "=", "system");
		DynamicObject dy_user = QueryServiceHelper.queryOne("bos_user", "id", new QFilter[] { qf_user });
		String system_id = dy_user.get("id").toString();
		return system_id;
	}
	
    @Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		if (StringUtils.equals("aos_init", evt.getItemKey())) {
			// PPC数据源初始化
			aos_init();
		} 
		else if (StringUtils.equals(KEY_AOS_ASSIGN, evt.getItemKey())) {
			// 列表认领按钮逻辑
			KEY_AOS_ASSIGN();
		} else if (StringUtils.equals(KEY_CANCEL_AOS_ASSIGN, evt.getItemKey())) {
			// 列表取消认领按钮逻辑
			KEY_CANCEL_AOS_ASSIGN();
		}
	}
	private void aos_init() {
		// TODO PPC数据源初始化
		System.out.println("===into 出价调整(推广) 初始化===");
		CommData.init();
		aos_mkt_common_redis.init_redis("ppc");
		Map<String, Object> params = new HashMap<>();
		params.put("p_ou_code", "UK");
		aos_mkt_popadjp_init.executerun(params);
		this.getView().invokeOperation("refresh");
		this.getView().showSuccessNotification("已手工提交出价调整(推广)初始化,请等待,务重复提交!");
	}
	
	private void KEY_AOS_ASSIGN() {
		// TODO 列表认领按钮逻辑
		try {
			// 获取选中的行id
			List<ListSelectedRow> list = getSelectedRows();
			long User_id = UserServiceHelper.getCurrentUserId();// 操作人员
			int error_count = 0;
			String error_message = "";
			for (int i = 0; i < list.size(); i++) {
				String para = list.get(i).toString();
				DynamicObject bill = BusinessDataServiceHelper.loadSingle(para, "aos_mkt_popular_adjp");
				DynamicObject aos_makeby = (DynamicObject) bill.get("aos_makeby");
				long makeby_id = Long.valueOf(aos_makeby.getPkValue().toString());
				String Status = bill.get("billstatus").toString();
				String billno = bill.get("billno").toString();
				if (!Status.equals("A")) {
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
					OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_popular_adjp",
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
		// TODO 列表取消认领按钮逻辑
		try {
			List<ListSelectedRow> list = getSelectedRows();
			long User_id = UserServiceHelper.getCurrentUserId();// 操作人员
			int error_count = 0;
			String error_message = "";

			for (int i = 0; i < list.size(); i++) {
				String para = list.get(i).toString();

				DynamicObject bill = BusinessDataServiceHelper.loadSingle(para, "aos_mkt_popular_adjp");
				DynamicObject aos_makeby = (DynamicObject) bill.get("aos_makeby");
				long makeby_id = Long.valueOf(aos_makeby.getPkValue().toString());
				String Status = bill.get("billstatus").toString();
				String billno = bill.get("billno").toString();

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
					OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_popular_adjp",
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