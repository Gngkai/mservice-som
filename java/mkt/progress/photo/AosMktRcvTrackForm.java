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

/**
 * @author aosom 批量填写快递单号弹框-动态表单插件
 */
public class AosMktRcvTrackForm extends AbstractFormPlugin {
    public final static String BTNOK = "btnok";

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        this.addClickListeners("btnok");
    }

    @Override
    public void click(EventObject evt) {
        super.click(evt);
        Control control = (Control)evt.getSource();
        String key = control.getKey();
        if (BTNOK.equals(key)) {
            Map<String, Object> para = this.getView().getFormShowParameter().getCustomParam("params");
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>)para.get("list");
            Object aosTrackno = this.getModel().getValue("aos_trackno");
            for (Object o : list) {
                JSONObject id = (JSONObject)o;
                DynamicObject aosMktRcv = BusinessDataServiceHelper.loadSingle(id.get("pkv"), "aos_mkt_rcv");
                aosMktRcv.set("aos_trackno", aosTrackno);
                SaveServiceHelper.saveOperate("aos_mkt_rcv", new DynamicObject[] {aosMktRcv}, OperateOption.create());
            }
            this.getView().close();
        }
    }
}