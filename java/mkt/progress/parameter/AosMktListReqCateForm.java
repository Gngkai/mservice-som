package mkt.progress.parameter;

import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.control.Control;
import kd.bos.form.plugin.AbstractFormPlugin;

import java.util.EventObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author create by gk
 * @version 优化需求表品类选择-动态表单插件
 */
public class AosMktListReqCateForm extends AbstractFormPlugin {
    public final static String BTNOK = "btnok";

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        this.addClickListeners("btnok");
    }

    @Override
    public void click(EventObject evt) {
        super.click(evt);
        Control source = (Control)evt.getSource();
        String key = source.getKey();
        if (BTNOK.equals(key)) {
            DynamicObjectCollection dycItem =
                this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_cate");
            List<String> listCate = dycItem.stream()
                .map(dy -> dy.getDynamicObject("fbasedataid").getLocaleString("name").getLocaleValue_zh_CN()).distinct()
                .collect(Collectors.toList());
            Map<String, List<String>> mapRe = new HashMap<>(16);
            mapRe.put("cate", listCate);
            this.getView().returnDataToParent(mapRe);
            this.getView().close();
        }
    }
}
