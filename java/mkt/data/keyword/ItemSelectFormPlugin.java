package mkt.data.keyword;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.control.Control;
import kd.bos.form.plugin.AbstractFormPlugin;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

/**
 * 选择物料
 */
public class ItemSelectFormPlugin extends AbstractFormPlugin {
    private final static String KEY_OK = "btnok";
    private final static String KEY_CANCEL = "btncancel";

    @Override
    public void registerListener(EventObject e) {
        //页面确认按钮和取消按钮添加监听
        this.addClickListeners(KEY_OK, KEY_CANCEL);
    }

    @Override
    public void click(EventObject evt) {
        //获取被点击的控件对象
        Control source = (Control) evt.getSource();
        if (StringUtils.equals(source.getKey(), KEY_OK)) {
            // 获取物料
            List<String> itemIdList = new ArrayList<>();
            DynamicObjectCollection aos_itemids = (DynamicObjectCollection) this.getModel().getValue("aos_itemids");
            if (aos_itemids != null && aos_itemids.size() > 0) {
                for (DynamicObject obj:aos_itemids) {
                    DynamicObject aos_itemid = obj.getDynamicObject("fbasedataid");
                    itemIdList.add(aos_itemid.getString("id"));
                }
            }
            if (itemIdList.size() == 0) {
                this.getView().showTipNotification("请选择SKU!");
                return;
            }
            this.getView().returnDataToParent(itemIdList);
            this.getView().close();
        } else if (StringUtils.equals(source.getKey(), KEY_CANCEL)) {
            //被点击控件为取消则设置返回值为空并关闭页面（在页面关闭回调方法中必须验证返回值不为空，否则会报空指针）
            this.getView().returnDataToParent(null);
            this.getView().close();
        }
    }
}
