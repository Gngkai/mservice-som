package mkt.common;

import java.util.EventObject;
import java.util.StringJoiner;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.form.control.Control;
import kd.bos.form.control.events.ClickListener;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.control.events.ItemClickListener;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.fi.bd.util.QFBuilder;
import mkt.synciface.aos_mkt_item_sync;

public class aos_czj_test_bill extends AbstractBillPlugIn implements ItemClickListener, ClickListener {

	public void registerListener(EventObject e) {
		super.registerListener(e);
		// 给工具栏加监听事件
		this.addItemClickListeners("aos_toolbarap");
		this.addItemClickListeners("aos_test");
	}

	@Override
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String Control = evt.getItemKey();
		if (Control.equals("aos_test"))
			aos_test();
	}

	private void aos_test() {
		StringJoiner message = new StringJoiner(";");
		//设置设计需求表
		QFBuilder qfBuilder = new QFBuilder();
		qfBuilder.add("aos_status","!=","结束");
		StringJoiner str = new StringJoiner(",");
		str.add("id");
		str.add("aos_user");
		str.add("aos_userorganizat1");
		str.add("aos_userorganizat2");
		str.add("aos_userorganizat3");
		DynamicObject[] aos_mkt_designreqs = BusinessDataServiceHelper.load("aos_mkt_designreq", str.toString(), qfBuilder.toArray());
		for (DynamicObject dy : aos_mkt_designreqs) {
			mkt.progress.design.aos_mkt_designreq_bill.setEntityValue(dy);
		}
		SaveServiceHelper.update(aos_mkt_designreqs);
		message.add("设计需求表修改完成");
		//文案
		DynamicObject[] aos_mkt_listing_sons = BusinessDataServiceHelper.load("aos_mkt_listing_son", str.toString(), qfBuilder.toArray());
		for (DynamicObject dy : aos_mkt_listing_sons) {
			mkt.progress.listing.aos_mkt_listingson_bill.setListSonUserOrganizate(dy);
		}
		SaveServiceHelper.update(aos_mkt_listing_sons);
		message.add("文案修改完成");
		this.getView().showMessage(message.toString());
	}

	public void beforePropertyChanged(PropertyChangedArgs e) {
		String name = e.getProperty().getName();
		System.out.println("name =" + name);
		if (name.equals("aos_test_item")) {
			System.out.println("====into before aos_test_item====");
			System.out.println(this.getModel().getValue(name));
		}
	}

	public void propertyChanged(PropertyChangedArgs e) {
		String name = e.getProperty().getName();
		System.out.println("name =" + name);
		if (name.equals("aos_test_item")) {
			System.out.println("====into aos_test_item====");
			System.out.println(this.getModel().getValue(name));
		}
	}

}