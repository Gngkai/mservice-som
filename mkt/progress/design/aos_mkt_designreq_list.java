package mkt.progress.design;

import java.util.*;
import java.util.stream.Collectors;

import com.alibaba.nacos.common.utils.Pair;
import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndHistory;
import common.sal.util.SalUtil;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.utils.StringUtils;
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
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.MKTCom;
import mkt.progress.ProgressUtil;
import mkt.progress.iface.parainfo;
import org.apache.commons.collections4.BidiMap;

public class aos_mkt_designreq_list extends AbstractListPlugin {

	public final static String aos_mkt_designreq = "aos_mkt_designreq";

	@Override
	public void setFilter(SetFilterEvent e) {
		List<QFilter> qFilters = e.getQFilters();
		// 判断是否不可见拍照功能图制作
		QFilter filter_code = new QFilter("aos_code", "=", "aos_mkt_design_photoStatus");
		QFilter filter_value = new QFilter("aos_value", "=", "1");
		boolean exists = QueryServiceHelper.exists("aos_sync_params", new QFilter[] { filter_code, filter_value });
		if (exists) {
			QFilter filter_status = new QFilter("aos_status", "!=", "拍照功能图制作");
			qFilters.add(filter_status);
		}
		long currUserId = RequestContext.get().getCurrUserId();
		// 是销售
		if (ProgressUtil.JudgeSaleUser(currUserId, ProgressUtil.Dapartment.Sale.getNumber()))
			parainfo.setRights(qFilters, this.getPageCache(), aos_mkt_designreq);
		// 不是销售
		else
			parainfo.setRightsForDesign(qFilters, this.getPageCache());
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String itemKey = evt.getItemKey();
		try {
			if ("aos_batchgive".equals(itemKey))
				aos_open();// 批量转办
			else if ("aos_submit".equals(itemKey)) {
				aos_submit();
			} else if ("aos_showclose".equals(itemKey)) {
				parainfo.showClose(this.getView());// 查询关闭流程
			}
		} catch (FndError fndMessage) {
			this.getView().showTipNotification(fndMessage.getErrorMessage());
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
		}
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
		List<Object> list = getSelectedRows().stream().map(row -> row.getPrimaryKeyValue()).distinct()
				.collect(Collectors.toList());
		Object CurrentUserId = UserServiceHelper.getCurrentUserId();
		Object CurrentUserName = UserServiceHelper.getUserInfoByID((long) CurrentUserId).get("name");

		QFilter filter = new QFilter("aos_user.id", QCP.equals, CurrentUserId);
		QFilter filter2 = new QFilter("aos_give", QCP.equals, true);// 判断是否有转办权限
		boolean exists = QueryServiceHelper.exists("aos_mkt_userights", new QFilter[] { filter, filter2 });

		for (int i = 0; i < list.size(); i++) {
			String id = list.get(i).toString();
			DynamicObject aos_mkt_designreq = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_designreq");
			String aos_userold = aos_mkt_designreq.getDynamicObject("aos_user").getPkValue().toString();
			String billno = aos_mkt_designreq.getString("billno");
			String aos_status = aos_mkt_designreq.getString("aos_status");

			if (!(CurrentUserId + "").equals(aos_userold) && !exists) {
				this.getView().showTipNotification(billno + "只允许转办自己的单据!");
				return;
			}

			aos_mkt_designreq.set("aos_user", aos_user);
			aos_mkt_designreq.set("aos_designby", aos_user);
			// 设计节点转办时 将设计调整为 转办操作人
			if ("设计".equals(aos_status)) {
				aos_mkt_designreq.set("aos_designer", aos_user);
			}

			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_designreq",
					new DynamicObject[] { aos_mkt_designreq }, OperateOption.create());
			MKTCom.SendGlobalMessage(((DynamicObject) aos_user).getPkValue() + "", aos_mkt_designreq + "",
					operationrst.getSuccessPkIds().get(0) + "", billno, CurrentUserName + "流程转办!");
			FndHistory fndHistory = new FndHistory();
			fndHistory.SetActionBy(CurrentUserId);
			fndHistory.SetFormId("aos_mkt_designreq");
			fndHistory.SetSourceId(id);
			fndHistory.SetActionCode("流程转办");// 操作动作
			fndHistory.SetDesc(CurrentUserName + "流程转办!"); // 操作备注
			Cux_Common_Utl.History(fndHistory);// 插入历史记录;
		}
		this.getView().showSuccessNotification("转办成功");
		this.getView().invokeOperation("refresh");// 刷新列表
	}

	@Override
	public void afterQueryOfExport(AfterQueryOfExportEvent e) {
		fillExportData(e.getQueryValues());
	}

	private static void fillExportData(DynamicObject[] dyc_export) {
		Map<String, List<String>> map_fill = new HashMap<>();
		map_fill.put("设计", Arrays.asList("aos_design_re", "aos_design_su"));
		map_fill.put("设计确认:翻译", Arrays.asList("aos_trans_re", "aos_trans_su"));
		map_fill.put("设计确认3D", Arrays.asList("aos_3d_re", "aos_3d_su"));

		for (DynamicObject dy : dyc_export) {
			String name = dy.getDynamicObjectType().getName();
			String id = dy.getString("id");
			Pair<BidiMap<String, Integer>, Map<String, DynamicObject>> pair = ProgressUtil.getOperateLog(name, id, "");
			BidiMap<String, Integer> map_opIndex = pair.getFirst();
			Map<String, DynamicObject> map_opDy = pair.getSecond();
			for (Map.Entry<String, List<String>> entry : map_fill.entrySet()) {
				if (map_opIndex.containsKey(entry.getKey())) {
					dy.set(entry.getValue().get(1), map_opDy.get(entry.getKey()).getDate("aos_actiondate"));
					// 如果没有 制作设计师，补上设计节点的提交人
					if (entry.getKey().equals("设计")) {
						if (Cux_Common_Utl.IsNull(dy.get("aos_designby"))) {
							DynamicObject dy_user = BusinessDataServiceHelper
									.loadSingle(map_opDy.get(entry.getKey()).getString("aos_actionby"), "bos_user");
							dy.set("aos_designby", dy_user);
						}
						if (Cux_Common_Utl.IsNull(dy.get("aos_design_date"))) {
							dy.set("aos_design_date", map_opDy.get(entry.getKey()).getDate("aos_actiondate"));
						}
					}
					// 前一个节点
					int index = map_opIndex.get(entry.getKey()) - 1; // 设计节点的前一个节点
					if (map_opIndex.containsValue(index)) {
						String key = map_opIndex.getKey(index);
						dy.set(entry.getValue().get(0), map_opDy.get(key).getDate("aos_actiondate"));
					}
					// 如果没有前一个节点，则把申请日期作为当前节点的收到日期（即上一个节点的提交日期）
					else {
						dy.set(entry.getValue().get(0), dy.getDate("aos_requiredate"));
					}
				}
			}
		}
	}
}