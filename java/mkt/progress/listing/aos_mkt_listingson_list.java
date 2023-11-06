package mkt.progress.listing;

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
import mkt.common.MKTCom;
import mkt.progress.ProgressUtil;
import mkt.progress.iface.parainfo;
import org.apache.commons.collections4.BidiMap;

public class aos_mkt_listingson_list extends AbstractListPlugin {

	@Override
	public void setFilter(SetFilterEvent e) {
		// IListView view = (IListView) this.getView();
		// String billFormId = view.getBillFormId();
		List<QFilter> qFilters = e.getQFilters();
		parainfo.setRights(qFilters, this.getPageCache(), "aos_editor");
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String itemKey = evt.getItemKey();
		try {
			if ("aos_batchgive".equals(itemKey))
				aos_open();// 批量转办
			else if ("aos_submit".equals(itemKey))
				aos_submit();
			else if ("aos_showclose".equals(itemKey))
				parainfo.showClose(this.getView());// 查询关闭流程
		} catch (FndError fndMessage) {
			this.getView().showTipNotification(fndMessage.getErrorMessage());
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
		}
	}

	/** 批量提交 **/
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

		for (int i = 0; i < list.size(); i++) {
			String id = list.get(i).toString();
			DynamicObject aos_mkt_listing_son = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_listing_son");
			String aos_userold = aos_mkt_listing_son.getDynamicObject("aos_user").getPkValue().toString();
			String billno = aos_mkt_listing_son.getString("billno");

			if (!(CurrentUserId + "").equals(aos_userold)) {
				this.getView().showTipNotification(billno + "只允许转办自己的单据!");
				return;
			}
			aos_mkt_listing_son.set("aos_user", aos_user);
			aos_mkt_listingson_bill.setListSonUserOrganizate(aos_mkt_listing_son);
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_son",
					new DynamicObject[] { aos_mkt_listing_son }, OperateOption.create());
			MKTCom.SendGlobalMessage(((DynamicObject) aos_user).getPkValue() + "", aos_mkt_listing_son + "",
					operationrst.getSuccessPkIds().get(0) + "", billno, CurrentUserName + "流程转办!");
			FndHistory fndHistory = new FndHistory();
			fndHistory.SetActionBy(CurrentUserId);
			fndHistory.SetFormId("aos_mkt_listing_son");
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
	private static void fillExportData(DynamicObject [] dyc_export){
		Map<String,List<String>> map_fill = new HashMap<>();
		map_fill.put("编辑确认", Arrays.asList("aos_design_re","aos_design_su"));

		for (DynamicObject dy : dyc_export) {
			String name = dy.getDynamicObjectType().getName();
			String id = dy.getString("id");
			Pair<BidiMap<String, Integer>, Map<String, DynamicObject>> pair = ProgressUtil.getOperateLog(name, id,"");
			BidiMap<String, Integer> map_opIndex = pair.getFirst();
			Map<String, DynamicObject> map_opDy = pair.getSecond();
			for (Map.Entry<String, List<String>> entry : map_fill.entrySet()) {
				if (map_opIndex.containsKey(entry.getKey())){
					dy.set(entry.getValue().get(1),map_opDy.get(entry.getKey()).get("aos_actiondate"));
					if (Cux_Common_Utl.IsNull(dy.get("aos_make"))){
						DynamicObject dy_user = BusinessDataServiceHelper.loadSingle(map_opDy.get(entry.getKey()).get("aos_actionby"), "bos_user");
						dy.set("aos_make",dy_user);
					}
					int index = map_opIndex.get(entry.getKey())-1;	//设计节点的前一个节点
					if (map_opIndex.containsValue(index)){
						String key = map_opIndex.getKey(index);
						dy.set(entry.getValue().get(0),map_opDy.get(key).getDate("aos_actiondate"));
					}
					else {
						dy.set(entry.getValue().get(0),dy.getDate("aos_requiredate"));
					}
				}
			}
			if (map_opDy.containsKey("海外确认"))
				dy.set("aos_over_su",map_opDy.get("海外确认").getDate("aos_actiondate"));
		}
	}
}
