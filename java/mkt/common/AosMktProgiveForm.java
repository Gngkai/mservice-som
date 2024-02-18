package mkt.common;

import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;
import kd.bos.form.FieldTip;
import kd.bos.form.control.Control;
import kd.bos.form.plugin.AbstractFormPlugin;

/**
 * @author aosom
 * @version 转办-动态表单插件
 */
public class AosMktProgiveForm extends AbstractFormPlugin {
    private static final String BTNOK = "btnok";
    private static final String AOS_USER = "aos_user";
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
        Map<String, Object> map = new HashMap<>(16);
        if (BTNOK.equals(key)) {
            if (this.getModel().getValue(AOS_USER) != null) {
                Object aosUser = this.getModel().getValue("aos_user");
                map.put("aos_user", aosUser);
                this.getView().returnDataToParent(map);
                this.getView().close();
            } else {
                FieldTip remarks =
                    new FieldTip(FieldTip.FieldTipsLevel.Info, FieldTip.FieldTipsTypes.others, "aos_user", "转办接收人不能为空");
                this.getView().showFieldTip(remarks);
            }
        }
    }
}
