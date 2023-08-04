package mkt.progress.design3d;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

import com.alibaba.nacos.common.utils.Pair;
import common.Cux_Common_Utl;
import common.fnd.FndError;
import common.fnd.FndGlobal;
import common.fnd.FndHistory;
import common.sal.util.SalUtil;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.datamodel.ListSelectedRow;
import kd.bos.entity.datamodel.ListSelectedRowCollection;
import kd.bos.entity.datamodel.events.PackageDataEvent;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.CloseCallBack;
import kd.bos.form.FormShowParameter;
import kd.bos.form.IFormView;
import kd.bos.form.ShowType;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.AfterQueryOfExportEvent;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.form.operatecol.OperationColItem;
import kd.bos.list.column.ListOperationColumnDesc;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import kd.fi.bd.util.QFBuilder;
import mkt.progress.ProgressUtil;
import mkt.progress.iface.parainfo;
import org.apache.commons.collections4.BidiMap;

public class aos_mkt_3design_list extends AbstractListPlugin {

	public final static String aos_mkt_3design = "aos_mkt_3design";

	@Override
	public void packageData(PackageDataEvent e) {
		if (e.getSource() instanceof ListOperationColumnDesc) {
			List<OperationColItem> operationColItems = (List<OperationColItem>) e.getFormatValue();
			for (OperationColItem operationColItem : operationColItems) {
				LocaleString name = new LocaleString();
				String billno = e.getRowData().getString("billno");
				// 设置操作列名称为单据编号
				if (FndGlobal.IsNull(billno))
					name.setLocaleValue(" ");
				else
					name.setLocaleValue(billno);
				operationColItem.setOperationName(name);
				// 判断货号合同是否已关闭
				String aos_source = e.getRowData().getString("aos_source");
				String aos_orignbill = e.getRowData().getString("aos_orignbill");
				if ("拍照需求表".equals(aos_source)) {
					DynamicObject aos_mkt_photoreq = QueryServiceHelper.queryOne("aos_mkt_photoreq", "aos_ponumber",
							new QFilter("billno", QCP.equals, aos_orignbill).toArray());
					if (FndGlobal.IsNotNull(aos_mkt_photoreq)
							&& FndGlobal.IsNotNull(aos_mkt_photoreq.getString("aos_ponumber"))) {
						Boolean exist = QueryServiceHelper.exists("aos_purcontract",
								new QFilter("billno", QCP.equals, aos_mkt_photoreq.getString("aos_ponumber"))
										.and("billstatus", QCP.equals, "F").toArray());
						if (exist)
							operationColItem.setForeColor("red");
					}
				}
			}
		}
		super.packageData(e);
	}

	@Override
	public void setFilter(SetFilterEvent e) {
		List<QFilter> qFilters = e.getQFilters();
		//判断大货封样是否可见
		QFBuilder builder = new QFBuilder();
		builder.add("aos_code", "=", "aos_mkt_3designStatus");
		builder.add("aos_value", "=", "1");
		boolean exists = QueryServiceHelper.exists("aos_sync_params", builder.toArray());
		if (exists){
			QFilter filter = new QFilter("aos_status","!=","大货样封样");
			qFilters.add(filter);
		}
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
			else if ("aos_querysample".equals(itemKey))
				querySample();// 查看封样图片
		} catch (FndError error) {
			this.getView().showMessage(error.getErrorMessage());
		} catch (Exception e) {
			StringWriter writer = new StringWriter();
			e.printStackTrace(new PrintWriter(writer));
			this.getView().showMessage(writer.toString());
			e.printStackTrace();
		}
	}
	/**
	 * 查看封样图片
	 */
	private void querySample() throws FndError {
		try {
			ListSelectedRowCollection selectedRows = this.getSelectedRows();
			int size = selectedRows.size();
			if (size != 1) {
				this.getView().showTipNotification("请先选择单条数据查询!");
			} else {
				aos_mkt_3design_bill.openSample(this.getView(), selectedRows.get(0).getPrimaryKeyValue());
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
		List<ListSelectedRow> list = getSelectedRows();
		Object CurrentUserId = UserServiceHelper.getCurrentUserId();
		Object CurrentUserName = UserServiceHelper.getUserInfoByID((long) CurrentUserId).get("name");

		List<Object> list_pks = list.stream().map(row -> row.getPrimaryKeyValue()).distinct()
				.collect(Collectors.toList());
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

	private static void fillExportData(DynamicObject[] dyc_export) {
		for (DynamicObject dy : dyc_export) {
			String name = dy.getDynamicObjectType().getName();
			String id = dy.getString("id");
			Pair<BidiMap<String, Integer>, Map<String, Date>> pair = ProgressUtil.getOperateLog(name, id);
			Map<String, Date> map_opDate = pair.getSecond();
			if (map_opDate.containsKey("新建")) {
				dy.set("aos_enddate", map_opDate.get("新建"));
			}
		}
	}

}
