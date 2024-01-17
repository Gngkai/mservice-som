package mkt.progress.photo;

import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.control.Control;
import kd.bos.form.plugin.AbstractFormPlugin;

import java.util.EventObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author aosom
 * @version 拍照需求货号查询-动态表单插件
 */
public class AosMktProgPhReqForm extends AbstractFormPlugin {

    public final static String BTNOK = "btnok";

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        this.addClickListeners(BTNOK);
    }

    @Override
    public void click(EventObject evt) {
        super.click(evt);
        Control source = (Control)evt.getSource();
        String key = source.getKey();
        if (BTNOK.equals(key)) {
            DynamicObjectCollection dycItem =
                this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_item");
            List<String> listItem = dycItem.stream().map(dy -> dy.getDynamicObject("fbasedataid").getString("id"))
                .distinct().collect(Collectors.toList());
            Map<String, List<String>> mapRe = new HashMap<>(16);
            mapRe.put("item", listItem);
            this.getView().returnDataToParent(mapRe);
            this.getView().close();
        }
    }
}
