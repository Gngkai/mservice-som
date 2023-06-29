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
 * @date 2023/2/27 9:29
 * @action  优化需求表平类弹窗
 */
public class aos_mkt_listReqCateForm extends AbstractFormPlugin {
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
            DynamicObjectCollection dyc_item = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_cate");
            List<String> list_cate = dyc_item.stream()
                    .map(dy -> dy.getDynamicObject("fbasedataid").getLocaleString("name").getLocaleValue_zh_CN())
                    .distinct()
                    .collect(Collectors.toList());
            Map<String,List<String>> map_re = new HashMap<>();
            map_re.put("cate",list_cate);
            this.getView().returnDataToParent(map_re);
            this.getView().close();
        }
    }
}
