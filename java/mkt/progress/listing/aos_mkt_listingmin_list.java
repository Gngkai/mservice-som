package mkt.progress.listing;

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
import kd.bos.form.*;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.AfterQueryOfExportEvent;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.form.events.MessageBoxClosedEvent;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.list.IListView;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import mkt.common.MKTCom;
import mkt.progress.ProgressUtil;
import mkt.progress.iface.parainfo;
import org.apache.commons.collections4.BidiMap;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author create by gk
 * @date 2022/10/21 16:02
 * @action Listing优化需求表-小语种文案 列表
 */
public class aos_mkt_listingmin_list extends AbstractListPlugin {
	public final static String aos_mkt_listing_min = "aos_mkt_listing_min";
	@Override
	public void setFilter(SetFilterEvent e) {
		List<QFilter> qFilters = e.getQFilters();
		String[] otherUser = new String[] { "aos_editor", "aos_editormin" };
		parainfo.setRights(qFilters, this.getPageCache(), aos_mkt_listing_min, otherUser);
	}
	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String itemKey = evt.getItemKey();
		try {
			if ("aos_submit".equals(itemKey))
				aos_submit();
			else if ("aos_batchgive".equals(itemKey))
				aos_open();// 批量转办
			else if ("aos_showclose".equals(itemKey))
				parainfo.showClose(this.getView());// 查询关闭流程
			else if ("aos_close".equals(itemKey))
				aos_close();	//批量关闭
		} catch (FndError error) {
			this.getView().showMessage(error.getErrorMessage());
		} catch (Exception e) {
			StringWriter writer = new StringWriter();
			e.printStackTrace(new PrintWriter(writer));
			this.getView().showMessage(writer.toString());
			e.printStackTrace();
		}
	}

	private void aos_close() {
		String KEY_CANCEL = "bar_cancel";
		ConfirmCallBackListener confirmCallBackListener = new ConfirmCallBackListener(KEY_CANCEL, this);
		//设置页面确认框，参数为：标题，选项框类型，回调监听
		this.getView().showConfirm("您确认关闭此申请单吗？", MessageBoxOptions.YesNo, confirmCallBackListener);
	}
	@Override
	public void confirmCallBack(MessageBoxClosedEvent event) {
		super.confirmCallBack(event);
		String callBackId = event.getCallBackId();
		if (callBackId.equals("bar_cancel")) {
			List<DynamicObject> list_close = new ArrayList<>();
			if (event.getResult().equals(MessageBoxResult.Yes)) {
				IListView view = (IListView) this.getView();
				String billFormId = view.getBillFormId();
				ListSelectedRowCollection selectedRows = this.getSelectedRows();
				String current = String.valueOf(UserServiceHelper.getCurrentUserId());
				StringJoiner str_noUser = new StringJoiner(" , ");	//无权关闭单据编码
				for (ListSelectedRow row : selectedRows) {
					DynamicObject dy = BusinessDataServiceHelper.loadSingle(row.getPrimaryKeyValue(), billFormId);
					String user = dy.getDynamicObject("aos_user").getString("id");
					//关闭人是操作人
					if (user.equals(current)){
						dy.set("aos_user", Cux_Common_Utl.SYSTEM);
						dy.set("aos_status", "结束");
						aos_mkt_listingmin_bill.setErrorList(dy);
						list_close.add(dy);
						FndHistory.Create(dy, "手工关闭", "手工关闭");
					}
					else{
						str_noUser.add(row.getBillNo());
					}
				}
				String messgae = "批量关闭完成";
				if(str_noUser.toString().length()>0){
					messgae = messgae+"，以下单据不是操作人，无权关闭:  "+str_noUser.toString();
				}
				if (list_close.size()>0){
					SaveServiceHelper.update(list_close.toArray(new DynamicObject[list_close.size()]));
				}
				this.getView().updateView();
				this.getView().showMessage(messgae);
			}
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
		List<Object> list = getSelectedRows().stream().map(row->row.getPrimaryKeyValue()).distinct().collect(Collectors.toList());
		Object CurrentUserId = UserServiceHelper.getCurrentUserId();
		Object CurrentUserName = UserServiceHelper.getUserInfoByID((long) CurrentUserId).get("name");
		for (int i = 0; i < list.size(); i++) {
			String id = list.get(i).toString();
			DynamicObject aos_mkt_listing_min = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_listing_min");
			String aos_userold = aos_mkt_listing_min.getDynamicObject("aos_user").getPkValue().toString();
			String billno = aos_mkt_listing_min.getString("billno");

			if (!(CurrentUserId + "").equals(aos_userold)) {
				this.getView().showTipNotification(billno + "只允许转办自己的单据!");
				return;
			}
			aos_mkt_listing_min.set("aos_user", aos_user);
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_listing_min",
					new DynamicObject[] { aos_mkt_listing_min }, OperateOption.create());
			MKTCom.SendGlobalMessage(((DynamicObject) aos_user).getPkValue() + "", aos_mkt_listing_min + "",
					operationrst.getSuccessPkIds().get(0) + "", billno, CurrentUserName + "流程转办!");
			FndHistory fndHistory = new FndHistory();
			fndHistory.SetActionBy(CurrentUserId);
			fndHistory.SetFormId("aos_mkt_listing_min");
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
		Map<String,String> map_other = new HashMap<>();
		map_other.put("海外编辑确认","aos_over_su");
		map_other.put("海外编辑确认:功能图","aos_overfun_su");
		//引出模板的所有字段
		List<String> list_fields = dyc_export[0].getDynamicObjectType()
				.getProperties().stream().map(pro -> pro.getName()).distinct().collect(Collectors.toList());

		for (DynamicObject dy : dyc_export) {
			String name = dy.getDynamicObjectType().getName();
			String id = dy.getString("id");
			Pair<BidiMap<String, Integer>, Map<String, DynamicObject>> pair = ProgressUtil.getOperateLog(name, id, "");
			BidiMap<String, Integer> map_opIndex = pair.getFirst();
			Map<String, DynamicObject> map_opDate = pair.getSecond();
			//编辑节点
			for (Map.Entry<String, List<String>> entry : map_fill.entrySet()) {
				if (map_opIndex.containsKey(entry.getKey())){	//存在操作节点信息
					int index = map_opIndex.get(entry.getKey())-1;	//设计节点的前一个节点
					if (list_fields.contains(entry.getValue().get(1))){
						dy.set(entry.getValue().get(1),map_opDate.get(entry.getKey()).getDate("aos_actiondate"));
						//如果编辑确认师为空，赋上编辑确认师的值
						if(list_fields.contains("aos_make") && dy.get("aos_make")==null){
							DynamicObject dy_user = BusinessDataServiceHelper.loadSingle(map_opDate.get(entry.getKey()).getString("aos_actionby"),"bos_user");
							dy.set("aos_make",dy_user);
						}

					}

					if (map_opIndex.containsValue(index)){
						String key = map_opIndex.getKey(index);
						if (list_fields.contains(entry.getValue().get(0)))
						dy.set(entry.getValue().get(0),map_opDate.get(key).getDate("aos_actiondate"));
					}
				}
			}
			//其他节点
			for (Map.Entry<String, String> entry : map_other.entrySet()) {
				if (map_opDate.containsKey(entry.getKey()))
					if (list_fields.contains(entry.getValue()))
						dy.set(entry.getValue(),map_opDate.get(entry.getKey()).getDate("aos_actiondate"));
			}
			//如果单据手动关闭
			if (map_opIndex.containsKey("手工关闭")){
				if(list_fields.contains("aos_make")){
					dy.set("billstatus","手动关闭");
				}
			}
		}
	}
}
