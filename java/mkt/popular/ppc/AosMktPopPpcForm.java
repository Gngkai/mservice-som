package mkt.popular.ppc;

import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.form.FieldTip;
import kd.bos.form.control.Control;
import kd.bos.form.plugin.AbstractFormPlugin;

/**
 * @author aosom
 * @deprecated
 */
public class AosMktPopPpcForm extends AbstractFormPlugin {
    private static final String BTNOK = "btnok";
    private static final String AOS_ORGID = "aos_orgid";

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
            if (this.getModel().getValue(AOS_ORGID) != null) {
                String aosOuCode = ((DynamicObject)this.getModel().getValue("aos_orgid")).getString("number");
                map.put("aos_ou_code", aosOuCode);
                this.getView().returnDataToParent(map);
                this.getView().close();
            } else {
                FieldTip remarks =
                    new FieldTip(FieldTip.FieldTipsLevel.Info, FieldTip.FieldTipsTypes.others, "aos_orgid", "国别不能为空");
                this.getView().showFieldTip(remarks);
            }
        }
    }
}
