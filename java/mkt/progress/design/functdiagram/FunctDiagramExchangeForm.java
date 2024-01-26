package mkt.progress.design.functdiagram;

import kd.bos.form.control.Control;
import kd.bos.form.plugin.AbstractFormPlugin;

import java.util.*;

/**
 * @author create by gk
 */
public class FunctDiagramExchangeForm extends AbstractFormPlugin {
    public final static String BTNOK = "btnok";
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
        if (BTNOK.equals(key)){
            Map<String,Object> mapReturn = new HashMap<>(16);
            Object aosSource = this.getModel().getValue("aos_source");
            Object aosEnd = this.getModel().getValue("aos_end");
            mapReturn.put("source",aosSource);
            mapReturn.put("end",aosEnd);
            this.getView().returnDataToParent(mapReturn);
            this.getView().close();
        }
    }
}
