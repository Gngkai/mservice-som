package mkt.act.month;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.form.CloseCallBack;
import kd.bos.form.FormShowParameter;
import kd.bos.form.ShowType;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;

public class aos_mkt_actmonth_list extends AbstractListPlugin {
    @Override
    public void setFilter(SetFilterEvent e) {
    	// 营销人员国别权限控制
        try {
            long CurrentUserId = UserServiceHelper.getCurrentUserId();
            DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_userights",
					"entryentity.aos_orgid aos_orgid",
					new QFilter[] { new QFilter("aos_user", QCP.equals, CurrentUserId) });
			List<String> orgList = new ArrayList<>();
			for (DynamicObject obj : list) {
				orgList.add(obj.getString("aos_orgid"));
			}
			QFilter qFilter = new QFilter("aos_orgid", QCP.in, orgList);
            List<QFilter> qFilters = e.getQFilters();
            qFilters.add(qFilter);
        } catch (Exception ex) {
            this.getView().showErrorNotification("setFilter = " + ex.toString());
        }
    }
    
    @Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		if (StringUtils.equals("aos_init", evt.getItemKey())) {
			// 周期性活动初始化
			aos_init();
		} 
	}
    
	private void aos_init() {
		// TODO 周期性活动初始化
		FormShowParameter showParameter=new FormShowParameter();
		showParameter.setFormId("aos_mkt_act_cal_form");
		showParameter.getOpenStyle().setShowType(ShowType.Modal);
		showParameter.setCloseCallBack(new CloseCallBack(this,"form"));
		this.getView().showForm(showParameter);
	}
	
	
	@SuppressWarnings("unchecked")
	@Override
	public void closedCallBack(ClosedCallBackEvent closedCallBackEvent) {
		String actionId = closedCallBackEvent.getActionId();
		if (StringUtils.equals(actionId, "form")) {
			Object map =  closedCallBackEvent.getReturnData();
			Object aos_date =  ((Map<String, Object>) map).get("aos_date");
			try{
				aos_mkt_actmonth_init.do_operate(aos_date);
			}
			catch(Exception ex) {
				ex.printStackTrace();
			}
			this.getView().showSuccessNotification("已成功生成数据!");
			this.getView().invokeOperation("refresh");
		}
	}
    
}
