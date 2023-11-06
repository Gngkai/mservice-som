package mkt.act.select;

import java.util.EventObject;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;

public class aos_mkt_actselect_bill extends AbstractBillPlugIn {

	public void propertyChanged(PropertyChangedArgs e) {
		//String name = e.getProperty().getName();
		
	}

	@Override
	public void afterLoadData(EventObject e) {
	}

	public void afterCreateNewData(EventObject e) {
	}

}