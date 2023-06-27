package mkt.progress.photo;

import java.util.EventObject;
import java.util.List;
import java.util.Map;

import com.alibaba.fastjson.JSONObject;

import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.form.control.Control;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

public class aos_mkt_rcv_track extends AbstractFormPlugin {

	public void registerListener(EventObject e) {
		super.registerListener(e);
		this.addClickListeners("btnok");
	}

	public void click(EventObject evt) {
		super.click(evt);
		Control control = (Control) evt.getSource();
		String key = control.getKey();
		if (key.equals("btnok")) {
			Map<String, Object> para = this.getView().getFormShowParameter().getCustomParam("params");
			List<Object> list = (List<Object>) para.get("list");
			Object aos_trackno = this.getModel().getValue("aos_trackno");
			for (int i = 0; i < list.size(); i++) {
				JSONObject id = (JSONObject) list.get(i);
				DynamicObject aos_mkt_rcv = BusinessDataServiceHelper.loadSingle(id.get("pkv"), "aos_mkt_rcv");
				aos_mkt_rcv.set("aos_trackno", aos_trackno);
				SaveServiceHelper.saveOperate("aos_mkt_rcv", new DynamicObject[] { aos_mkt_rcv },
						OperateOption.create());
			}
			
			this.getView().close();
		}
	}

}