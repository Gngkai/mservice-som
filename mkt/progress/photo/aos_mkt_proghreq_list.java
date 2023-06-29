package mkt.progress.photo;

import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.sal.util.SalUtil;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.datamodel.ListSelectedRow;
import kd.bos.entity.datamodel.ListSelectedRowCollection;
import kd.bos.entity.datamodel.events.PackageDataEvent;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.*;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.form.operatecol.OperationColItem;
import kd.bos.list.IListView;
import kd.bos.list.column.ListOperationColumnDesc;
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

import java.util.*;
import java.util.stream.Collectors;

import common.Cux_Common_Utl;
import common.fnd.FndHistory;

/**
 * @author create by gk
 * @date 2022/11/14 18:57
 * @action 拍照需求表 列表插件 aos_mkt_photoreq
 */
@SuppressWarnings("unchecked")
public class aos_mkt_proghreq_list extends AbstractListPlugin {
	private static final String KEY_Item = "item";

	@Override
	public void packageData(PackageDataEvent e) {
		if (e.getSource() instanceof ListOperationColumnDesc) {
			List<OperationColItem> operationColItems = (List<OperationColItem>) e.getFormatValue();
			for (OperationColItem operationColItem : operationColItems) {
				LocaleString name = new LocaleString();
				String billno = e.getRowData().getString("billno");
				String aos_ponumber = e.getRowData().getString("aos_ponumber");
				// 设置操作列名称为单据编号
				if (FndGlobal.IsNull(billno))
					name.setLocaleValue(" ");
				else
					name.setLocaleValue(billno);
				operationColItem.setOperationName(name);
				// 判断货号合同是否已关闭
				if (FndGlobal.IsNotNull(aos_ponumber)) {
					Boolean exist = QueryServiceHelper.exists("aos_purcontract",
							new QFilter("billno", QCP.equals, aos_ponumber).and("billstatus", QCP.equals, "F")
									.toArray());
					if (exist)
						operationColItem.setForeColor("red");
				}
			}
		}
		super.packageData(e);
	}

	@Override
	public void setFilter(SetFilterEvent e) {
		List<QFilter> qFilters = e.getQFilters();
		// 非布景师
		if (!ProgressUtil.JudeMaster()) {
			String[] ViewPermiss = new String[] { "aos_whiteph", "aos_actph", "aos_vedior", };
			parainfo.setRights(qFilters, this.getPageCache(), "aos_mkt_photoreq", ViewPermiss);
		}
		IPageCache pageCache = this.getPageCache();
		if (pageCache.get(KEY_Item) != null && !pageCache.get(KEY_Item).equals("")) {
			String items = pageCache.get(KEY_Item);
			List<String> list_item = Arrays.asList(items.split(","));
			if (list_item.size() > 0) {
				QFilter filter_item = new QFilter("aos_itemid.id", QFilter.in, list_item);
				qFilters.add(filter_item);
			}
			pageCache.put(KEY_Item, null);
		}
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String itemKey = evt.getItemKey();
		if ("aos_find".equals(itemKey))
			exactQueryItem();
		else if ("aos_showclose".equals(itemKey))
			parainfo.showClose(this.getView());// 查询关闭流程
		else if ("aos_batchgive".equals(itemKey))
			aos_open();// 批量转办
		else if ("aos_submit".equals(itemKey)) // 提交
			aos_submit();
	}

	private void aos_submit() {
		try {
			ListSelectedRowCollection selectedRows = this.getSelectedRows();
			int size = selectedRows.size();
			IListView view = (IListView) this.getView();
			String billFormId = view.getBillFormId();
			Iterator<ListSelectedRow> iterator = selectedRows.iterator();
			StringBuilder builder = new StringBuilder("");
			StringJoiner str = new StringJoiner(";");
			while (iterator.hasNext()) {
				ListSelectedRow next = iterator.next();
				QFilter filter_id = new QFilter("id", "=", next.getPrimaryKeyValue());
				DynamicObject dy = QueryServiceHelper.queryOne(billFormId, "aos_status", new QFilter[] { filter_id });
				boolean reject = true;
				if (dy != null) {
					if (dy.getString("aos_status").equals("白底拍摄")) {
						reject = false;
					} else if (dy.getString("aos_status").equals("实景拍摄")) {
						reject = false;
					}
				}
				if (reject) {
					str.add(next.getBillNo());
					iterator.remove();
				}
			}
			if (str.toString().length() > 0) {
				builder.append("  以下流程不在白底拍照或实景拍照节点，不能批量提交:");
				builder.append(str.toString());
			}

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
		showParameter.setCloseCallBack(new CloseCallBack(this, "give"));
		this.getView().showForm(showParameter);
	}

	/** 精确查询物料 **/
	private void exactQueryItem() {
		// 创建弹出页面对象，FormShowParameter表示弹出页面为动态表单
		FormShowParameter ShowParameter = new FormShowParameter();
		// 设置弹出页面的编码
		ShowParameter.setFormId("aos_mkt_phreq_form");
		// 设置弹出页面标题
//        ShowParameter.setCaption("您的请假天数大于3天，请填写工作交接安排说明");
		// 设置页面关闭回调方法
		// CloseCallBack参数：回调插件，回调标识
		ShowParameter.setCloseCallBack(new CloseCallBack(this, "form"));
		// 设置弹出页面打开方式，支持模态，新标签等
		ShowParameter.getOpenStyle().setShowType(ShowType.Modal);
		// 弹出页面对象赋值给父页面
		this.getView().showForm(ShowParameter);
	}

	/**
	 * 页面关闭回调事件
	 * 
	 * @param closedCallBackEvent event
	 */
	@Override
	public void closedCallBack(ClosedCallBackEvent closedCallBackEvent) {
		super.closedCallBack(closedCallBackEvent);
		String actionId = closedCallBackEvent.getActionId();
		// 判断标识是否匹配，并验证返回值不为空，不验证返回值可能会报空指针
		if (StringUtils.equals(actionId, "form") && null != closedCallBackEvent.getReturnData()) {
			// 这里返回对象为Object，可强转成相应的其他类型，
			// 单条数据可用String类型传输，返回多条数据可放入map中，也可使用json等方式传输
			Map<String, List<String>> returnData = (Map<String, List<String>>) closedCallBackEvent.getReturnData();
			if (returnData.containsKey(KEY_Item) && returnData.get(KEY_Item) != null) {
				List<String> list_item = returnData.get("item");
				String items = String.join(",", list_item);
				this.getView().getPageCache().put(KEY_Item, items);
			}
			this.getView().invokeOperation("refresh");
		} else if (StringUtils.equals(actionId, "give")) {
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
			DynamicObject aos_mkt_photoreq = BusinessDataServiceHelper.loadSingle(id, "aos_mkt_photoreq");
			String aos_userold = aos_mkt_photoreq.getDynamicObject("aos_user").getPkValue().toString();
			String billno = aos_mkt_photoreq.getString("billno");
			System.out.println("aos_userold =" + aos_userold);
			System.out.println("CurrentUserId =" + CurrentUserId);

			if (!(CurrentUserId + "").equals(aos_userold)) {
				this.getView().showTipNotification(billno + "只允许转办自己的单据!");
				return;
			}
			aos_mkt_photoreq.set("aos_user", aos_user);
			OperationResult operationrst = OperationServiceHelper.executeOperate("save", "aos_mkt_photoreq",
					new DynamicObject[] { aos_mkt_photoreq }, OperateOption.create());
			MKTCom.SendGlobalMessage(((DynamicObject) aos_user).getPkValue() + "", aos_mkt_photoreq + "",
					operationrst.getSuccessPkIds().get(0) + "", billno, CurrentUserName + "流程转办!");
			FndHistory fndHistory = new FndHistory();
			fndHistory.SetActionBy(CurrentUserId);
			fndHistory.SetFormId("aos_mkt_photoreq");
			fndHistory.SetSourceId(id);
			fndHistory.SetActionCode("流程转办");// 操作动作
			fndHistory.SetDesc(CurrentUserName + "流程转办!"); // 操作备注
			Cux_Common_Utl.History(fndHistory);// 插入历史记录;
		}

		this.getView().showSuccessNotification("转办成功");
		this.getView().invokeOperation("refresh");// 刷新列表
	}

}
