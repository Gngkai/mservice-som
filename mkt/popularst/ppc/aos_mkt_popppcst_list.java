package mkt.popularst.ppc;

import common.fnd.FndMsg;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.list.plugin.AbstractListPlugin;

public class aos_mkt_popppcst_list extends AbstractListPlugin {
	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		if (StringUtils.equals("aos_manual", evt.getItemKey())) 
			aos_manual();// 手工初始化
	}

	private void aos_manual() {
		FndMsg.debug("123");
		aos_mkt_popppcst_init.executerun();
		this.getView().invokeOperation("refresh");
	}

}