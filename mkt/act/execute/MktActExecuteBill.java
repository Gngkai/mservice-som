package mkt.act.execute;

import java.util.Date;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.utils.StringUtils;
import kd.bos.form.control.events.ItemClickEvent;

public class MktActExecuteBill extends AbstractBillPlugIn {
	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		if (StringUtils.equals("aos_submit", evt.getItemKey())) {
			aos_submit();
		}
	}

	private void aos_submit() {
		this.getModel().setValue("aos_status", "已提交");
		this.getModel().setValue("aos_submitdate", new Date());
		this.getView().invokeOperation("save");
		this.getView().invokeOperation("refresh");
	}
}
