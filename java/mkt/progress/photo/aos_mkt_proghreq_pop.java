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
 * @author create by gk
 * @date 2022/11/21 10:20
 * @action  拍照需求表精确查找货号弹窗
 */
public class aos_mkt_proghreq_pop extends AbstractFormPlugin {
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
            DynamicObjectCollection dyc_item = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_item");
            List<String> list_item = dyc_item.stream()
                    .map(dy -> dy.getDynamicObject("fbasedataid").getString("id"))
                    .distinct()
                    .collect(Collectors.toList());
            Map<String,List<String>> map_re = new HashMap<>();
            map_re.put("item",list_item);
            this.getView().returnDataToParent(map_re);
            this.getView().close();
        }
    }

}
