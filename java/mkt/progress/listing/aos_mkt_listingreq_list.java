package mkt.progress.listing;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import common.fnd.FndError;
import common.sal.util.SalUtil;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.entity.datamodel.ListSelectedRowCollection;
import kd.bos.form.*;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QFilter;
import mkt.progress.ProgressUtil;
import mkt.progress.iface.parainfo;

@SuppressWarnings("unchecked")
public class aos_mkt_listingreq_list extends AbstractListPlugin {

	public final static String aos_mkt_listing_req = "aos_mkt_listing_req";
	private final static String KEY_CATE = "cate";

	@Override
	public void setFilter(SetFilterEvent e) {
		List<QFilter> qFilters = e.getQFilters();
		parainfo.setRights(qFilters, this.getPageCache(), aos_mkt_listing_req);
		IPageCache pageCache = this.getPageCache();
		if (pageCache.get(KEY_CATE)!=null && !pageCache.get(KEY_CATE).equals("")){
			String items = pageCache.get(KEY_CATE);
			List<String> list_cate = Arrays.asList(items.split(","));
			if (list_cate.size()>0){
				QFilter filter_item = new QFilter("aos_entryentity.aos_category1",QFilter.in,list_cate);
				qFilters.add(filter_item);
			}
			pageCache.put(KEY_CATE,null);
		}
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String itemKey = evt.getItemKey();
		try {
			if ("aos_showclose".equals(itemKey))
				parainfo.showClose(this.getView());// 查询关闭流程
			else if (itemKey.equals("aos_submit")) {
				aos_submit();
			}
			else if ("aos_findcate".equals(itemKey)){
				findCate();
			}
		} catch (FndError error) {
			this.getView().showMessage(error.getErrorMessage());
		} catch (Exception e) {
			StringWriter writer = new StringWriter();
			e.printStackTrace(new PrintWriter(writer));
			this.getView().showMessage(writer.toString());
			e.printStackTrace();
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
	/** 品类筛选 **/
	private void findCate(){
		//创建弹出页面对象，FormShowParameter表示弹出页面为动态表单
		FormShowParameter ShowParameter = new FormShowParameter();
		//设置弹出页面的编码
		ShowParameter.setFormId("aos_mkt_reqcate");
		//设置弹出页面标题
		//设置页面关闭回调方法
		//CloseCallBack参数：回调插件，回调标识
		ShowParameter.setCloseCallBack(new CloseCallBack(this, "form"));
		//设置弹出页面打开方式，支持模态，新标签等
		ShowParameter.getOpenStyle().setShowType(ShowType.Modal);
		//弹出页面对象赋值给父页面
		this.getView().showForm(ShowParameter);
	}
	/**
	 * 页面关闭回调事件
	 * @param closedCallBackEvent   event
	 */
	@Override
	public void closedCallBack(ClosedCallBackEvent closedCallBackEvent) {
		super.closedCallBack(closedCallBackEvent);
		String actionId = closedCallBackEvent.getActionId();
		//判断标识是否匹配，并验证返回值不为空，不验证返回值可能会报空指针
		if (StringUtils.equals(actionId, "form") && null != closedCallBackEvent.getReturnData()) {
			//这里返回对象为Object，可强转成相应的其他类型，
			// 单条数据可用String类型传输，返回多条数据可放入map中，也可使用json等方式传输
			Map<String, List<String>> returnData = (Map<String, List<String>>) closedCallBackEvent.getReturnData();
			if (returnData.containsKey(KEY_CATE) && returnData.get(KEY_CATE)!=null){
				List<String> list_cate = returnData.get(KEY_CATE);
				String cate = String.join(",", list_cate);
				this.getView().getPageCache().put(KEY_CATE,cate);
			}
			this.getView().invokeOperation("refresh");
		}
	}
}
