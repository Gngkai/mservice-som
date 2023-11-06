package mkt.progress.design.aadd;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndHistory;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.datamodel.ListSelectedRow;
import kd.bos.entity.datamodel.ListSelectedRowCollection;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.CloseCallBack;
import kd.bos.form.FormShowParameter;
import kd.bos.form.ShowType;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.list.IListView;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.MKTCom;
import mkt.progress.ProgressUtil;
import mkt.progress.iface.parainfo;

public class aos_mkt_aadd_list extends AbstractListPlugin {
	public final static String aos_mkt_listing_min = "aos_mkt_listing_min";

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String itemKey = evt.getItemKey();
		try {
			if ("aos_showclose".equals(itemKey))
				parainfo.showClose(this.getView());// 查询关闭流程
			else if ("aos_submit".equals(itemKey)) // 提交
				aos_submit();
			else if ("aos_batchgive".equals(itemKey))
				aos_open();// 批量转办
		} catch (FndError error) {
			this.getView().showMessage(error.getErrorMessage());
		} catch (Exception e) {
			StringWriter writer = new StringWriter();
			e.printStackTrace(new PrintWriter(writer));
			this.getView().showMessage(writer.toString());
			e.printStackTrace();
		}
	}

	private void aos_open() {
		FormShowParameter showParameter = new FormShowParameter();
		showParameter.setFormId("aos_mkt_progive");
		showParameter.getOpenStyle().setShowType(ShowType.Modal);
		showParameter.setCloseCallBack(new CloseCallBack(this, "give"));
		this.getView().showForm(showParameter);
	}

	@Override
	public void closedCallBack(ClosedCallBackEvent closedCallBackEvent) {
		super.closedCallBack(closedCallBackEvent);
		String actionId = closedCallBackEvent.getActionId();
		// 判断标识是否匹配，并验证返回值不为空，不验证返回值可能会报空指针
		 if (StringUtils.equals(actionId, "give")) {
			Object map = closedCallBackEvent.getReturnData();
			if (map == null)
				return;
			@SuppressWarnings("unchecked")
			Object aos_user = ((Map<String, Object>) map).get("aos_user");
			aos_batchgive(aos_user);
		}
	}

	private void aos_batchgive(Object aos_user) {
		List<Object> list = getSelectedRows().stream().map(row -> row.getPrimaryKeyValue()).distinct()
				.collect(Collectors.toList());
		Object CurrentUserId = UserServiceHelper.getCurrentUserId();
		Object CurrentUserName = UserServiceHelper.getUserInfoByID((long) CurrentUserId).get("name");

		QFilter filter = new QFilter("aos_user.id", QCP.equals, CurrentUserId);
		QFilter filter2 = new QFilter("aos_give", QCP.equals, true);// 判断是否有转办权限
		boolean exists = QueryServiceHelper.exists("aos_mkt_userights", new QFilter[] { filter, filter2 });


		for (int i = 0; i < list.size(); i++) {
			String id = list.get(i).toString();
			DynamicObject aos_mkt_photoreq = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_aadd");
			String aos_userold = aos_mkt_photoreq.getDynamicObject("aos_user").getPkValue().toString();
			String billno = aos_mkt_photoreq.getString("billno");

			if (!(CurrentUserId + "").equals(aos_userold) && !exists) {
				this.getView().showTipNotification(billno + "只允许转办自己的单据!");
				return;
			}
			aos_mkt_photoreq.set("aos_user", aos_user);
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_aadd",
					new DynamicObject[] { aos_mkt_photoreq }, OperateOption.create());
			MKTCom.SendGlobalMessage(((DynamicObject) aos_user).getPkValue() + "", aos_mkt_photoreq + "",
					operationrst.getSuccessPkIds().get(0) + "", billno, CurrentUserName + "流程转办!");
			FndHistory fndHistory = new FndHistory();
			fndHistory.SetActionBy(CurrentUserId);
			fndHistory.SetFormId("aos_mkt_aadd");
			fndHistory.SetSourceId(id);
			fndHistory.SetActionCode("流程转办");// 操作动作
			fndHistory.SetDesc(CurrentUserName + "流程转办!"); // 操作备注
			Cux_Common_Utl.History(fndHistory);// 插入历史记录;
		}

		this.getView().showSuccessNotification("转办成功");
		this.getView().invokeOperation("refresh");// 刷新列表
	}

	private void aos_submit() {

		ListSelectedRowCollection selectedRows = this.getSelectedRows();
		int size = selectedRows.size();
		IListView view = (IListView) this.getView();
		String billFormId = view.getBillFormId();
		Iterator<ListSelectedRow> iterator = selectedRows.iterator();
		StringBuilder builder = new StringBuilder("");

		if (size == 0) {
			this.getView().showTipNotification("请先选择提交数据");
		} else {
			String message = ProgressUtil.submitEntity(view, selectedRows);
			if (message != null && message.length() > 0) {
				message = "提交成功,部分无权限单据无法提交：  " + message;
			} else
				message = "提交成功";
			this.getView().updateView();
			if (builder.toString().length() > 0) {
				this.getView().showSuccessNotification(message + builder.toString());
			} else {
				this.getView().showSuccessNotification(message);
			}
		}
	}


	@Override
	public void setFilter(SetFilterEvent e) {
		List<QFilter> qFilters = e.getQFilters();
		parainfo.setRights(qFilters, this.getPageCache(), "aos_mkt_aadd");
	}
}
