package mkt.image.test;

import java.util.EventObject;

import common.fnd.FndError;
import common.fnd.FndHistory;
import common.fnd.FndMsg;
import common.sal.util.SalUtil;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;

public class aos_mkt_test_bill extends AbstractBillPlugIn implements ItemClickListener {
	
	/** 添加监听 **/
	@Override
	public void registerListener(EventObject e) {
		super.registerListener(e);
		this.addItemClickListeners("aos_toolbarap");// 给工具栏加监听事件
		this.addItemClickListeners("aos_history"); 
		this.addItemClickListeners("aos_control"); 
	}
	
	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String Control = evt.getItemKey();
		try {
			if ("aos_history".equals(Control))
				aos_history();
			else if  ("aos_control".equals(Control))
				aos_control();
		} catch (FndError fndMessage) {
		} catch (Exception ex) {
			this.getView().showErrorNotification(SalUtil.getExceptionStr(ex));
		}
	}

	private void aos_control() {
		DynamicObject dyn = this.getModel().getDataEntity(true);
		FndHistory.Create(dyn, "dyn对象插入", "通过对象插入历史记录 -操作按钮");
		
		//String MKT_TEST = FndMsg.get("MKT_TEST");
		String MKT_TEST = FndMsg.getByPara("MKT_TEST", new String[] {"20DSA001" ,"1","20"});
		
		FndHistory.Create(this.getView(), "界面对象插入", "通过界面对象插入历史记录" + MKT_TEST);
		
		
	}

	private void aos_history() {
		FndHistory.OpenHistory(this.getView());
	}
	
	
	
	
}