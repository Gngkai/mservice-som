package mkt.progress.design.functDiagram;

import kd.bos.form.control.Control;
import kd.bos.form.plugin.AbstractFormPlugin;

import java.util.*;

/**
 * @author create by gk
 * @date 2023/4/24 13:47
 * @action
 */
public class functDiagramExchangeForm extends AbstractFormPlugin {
    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        this.addClickListeners("btnok");
    }
    @Override
    public void click(EventObject evt) {
        super.click(evt);
        Control source = (Control) evt.getSource();
        String key = source.getKey();
        if (key.equals("btnok")){
            Map<String,Object> map_return = new HashMap<>();
            Object aos_source = this.getModel().getValue("aos_source");
            Object aos_end = this.getModel().getValue("aos_end");
            map_return.put("source",aos_source);
            map_return.put("end",aos_end);
            this.getView().returnDataToParent(map_return);
            this.getView().close();
        }
    }
}
