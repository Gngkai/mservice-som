package mkt.progress.design3d;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

import com.alibaba.nacos.common.utils.Pair;
import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndHistory;
import common.sal.util.SalUtil;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.datamodel.ListSelectedRow;
import kd.bos.entity.datamodel.ListSelectedRowCollection;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.CloseCallBack;
import kd.bos.form.FormShowParameter;
import kd.bos.form.IFormView;
import kd.bos.form.ShowType;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.AfterQueryOfExportEvent;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.progress.ProgressUtil;
import mkt.progress.iface.parainfo;
import org.apache.commons.collections4.BidiMap;

public class aos_mkt_3design_list extends AbstractListPlugin {

	public final static String aos_mkt_3design = "aos_mkt_3design";

	@Override
	public void setFilter(SetFilterEvent e) {
		List<QFilter> qFilters = e.getQFilters();
		parainfo.setRights(qFilters, this.getPageCache(), aos_mkt_3design);
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String itemKey = evt.getItemKey();
		try {
			if (StringUtils.equals("aos_batchgive", itemKey))
				aos_open();// 批量转办
			else if (itemKey.equals("aos_submit")) {
				aos_submit();
			} else if ("aos_showclose".equals(itemKey))
				parainfo.showClose(this.getView());// 查询关闭流程
		} catch (FndError error) {
			this.getView().showMessage(error.getErrorMessage());
		} catch (Exception e) {
			StringWriter writer = new StringWriter();
			e.printStackTrace(new PrintWriter(writer));
			this.getView().showMessage(writer.toString());
			e.printStackTrace();
		}
	}

	/** 弹出转办框 **/
	private void aos_open() {
		FormShowParameter showParameter = new FormShowParameter();
		showParameter.setFormId("aos_mkt_progive");
		showParameter.getOpenStyle().setShowType(ShowType.Modal);
		showParameter.setCloseCallBack(new CloseCallBack(this, "form"));
		this.getView().showForm(showParameter);
	}

	/** 回调事件 **/
	public void closedCallBack(ClosedCallBackEvent closedCallBackEvent) {
		String actionId = closedCallBackEvent.getActionId();
		if (StringUtils.equals(actionId, "form")) {
			Object map = closedCallBackEvent.getReturnData();
			if (map == null)
				return;
			@SuppressWarnings("unchecked")
			Object aos_user = ((Map<String, Object>) map).get("aos_user");
			aos_batchgive(aos_user);
		}
	}

	private void aos_batchgive(Object aos_user) {
		List<ListSelectedRow> list = getSelectedRows();
		Object CurrentUserId = UserServiceHelper.getCurrentUserId();
		Object CurrentUserName = UserServiceHelper.getUserInfoByID((long) CurrentUserId).get("name");

		List<Object> list_pks = list.stream().map(row -> row.getPrimaryKeyValue()).distinct().collect(Collectors.toList());
		for (Object id : list_pks) {
			DynamicObject aos_mkt_3design = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_3design");
			String aos_userold = aos_mkt_3design.getDynamicObject("aos_user").getPkValue().toString();
			String billno = aos_mkt_3design.getString("billno");
			System.out.println("aos_userold =" + aos_userold);
			System.out.println("CurrentUserId =" + CurrentUserId);

			if (!(CurrentUserId + "").equals(aos_userold)) {
				this.getView().showTipNotification(billno + "只允许转办自己的单据!");
				return;
			}
			aos_mkt_3design.set("aos_user", aos_user);
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_3design",
					new DynamicObject[] { aos_mkt_3design }, OperateOption.create());
			SalUtil.setMessage(((DynamicObject) aos_user).getPkValue() + "", aos_mkt_3design + "",
					operationrst.getSuccessPkIds().get(0) + "", billno, CurrentUserName + "流程转办!");
			FndHistory fndHistory = new FndHistory();
			fndHistory.SetActionBy(CurrentUserId);
			fndHistory.SetFormId("aos_mkt_3design");
			fndHistory.SetSourceId(id);
			fndHistory.SetActionCode("流程转办");// 操作动作
			fndHistory.SetDesc(CurrentUserName + "流程转办!"); // 操作备注
			Cux_Common_Utl.History(fndHistory);// 插入历史记录;
		}

		this.getView().showSuccessNotification("转办成功");
		this.getView().invokeOperation("refresh");// 刷新列表

	}

	private void aos_submit() {
		try {
			ListSelectedRowCollection selectedRows = this.getSelectedRows();
			int size = selectedRows.size();
			if (size == 0) {
				this.getView().showTipNotification("请先选择提交数据");
			} else {
				IFormView view = this.getView();
				String message = ProgressUtil.submitEntity(view, selectedRows);
				if (message != null && message.length() > 0) {
					message = "提交成功,部分无权限单据无法提交：  " + message;
				} else
					message = "提交成功";
				this.getView().updateView();
				this.getView().showSuccessNotification(message);
			}
		} catch (FndError fndMessage) {
			this.getView().showTipNotification(fndMessage.getErrorMessage());
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
		}
	}

	@Override
	public void afterQueryOfExport(AfterQueryOfExportEvent e) {
		fillExportData(e.getQueryValues());
	}
	private static void fillExportData(DynamicObject [] dyc_export){
		for (DynamicObject dy : dyc_export) {
			String name = dy.getDynamicObjectType().getName();
			String id = dy.getString("id");
			Pair<BidiMap<String, Integer>, Map<String, Date>> pair = ProgressUtil.getOperateLog(name, id);
			Map<String, Date> map_opDate = pair.getSecond();
			if (map_opDate.containsKey("新建")){
				dy.set("aos_enddate",map_opDate.get("新建"));
			}
		}
	}


}
