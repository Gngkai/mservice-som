package mkt.progress.photo;

import java.util.List;

import common.fnd.FndError;
import common.sal.util.SalUtil;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.datamodel.ListSelectedRow;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;

public class aos_mkt_nophoto_list extends AbstractListPlugin {
	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		try {
			if (StringUtils.equals("aos_submit", evt.getItemKey()))
				aos_submit();// 批量提交
		} catch (FndError fndMessage) {
			this.getView().showTipNotification(fndMessage.getErrorMessage());
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
		}
	}

	private void aos_submit() throws FndError {
		List<ListSelectedRow> list = getSelectedRows();
		String error_message = "";
		for (int i = 0; i < list.size(); i++) {
			String id = list.get(i).toString();
			DynamicObject aos_mkt_nophotolist = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_nophotolist");
			String aos_reqno = aos_mkt_nophotolist.getString("aos_reqno");
			String aos_status = aos_mkt_nophotolist.getString("aos_status");
			if (!"新建".equals(aos_status)) {
				error_message += aos_reqno + "新建状态下才允许提交!";
				this.getView().showTipNotification(error_message);
				this.getView().invokeOperation("refresh");// 刷新列表
				return;
			}
			aos_mkt_nophotolist.set("aos_status", "已完成");
			OperationServiceHelper.executeOperate("save", "aos_mkt_nophotolist",
					new DynamicObject[] { aos_mkt_nophotolist }, OperateOption.create());
		}
		this.getView().showSuccessNotification("提交成功");
		this.getView().invokeOperation("refresh");// 刷新列表
	}
}