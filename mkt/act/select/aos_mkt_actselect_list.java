package mkt.act.select;

import kd.bos.dataentity.utils.StringUtils;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.list.plugin.AbstractListPlugin;

public class aos_mkt_actselect_list extends AbstractListPlugin {
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
		System.out.println("===into 周期性活动初始化===");
		aos_mkt_actselect_init.ManualitemClick();
		this.getView().invokeOperation("refresh");
		this.getView().showSuccessNotification("已手工提交周期性活动初始化,请等待,务重复提交!");
	}
    
}