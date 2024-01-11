package mkt.data.keyword;

import common.fnd.FndGlobal;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.control.Control;
import kd.bos.form.field.MulBasedataEdit;
import kd.bos.form.field.events.BeforeF7SelectEvent;
import kd.bos.form.field.events.BeforeF7SelectListener;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.list.ListShowParameter;
import kd.bos.orm.query.QFilter;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 选择物料
 */
public class ItemSelectFormPlugin extends AbstractFormPlugin implements BeforeF7SelectListener {
    private final static String KEY_OK = "btnok";
    private final static String KEY_CANCEL = "btncancel";

    @Override
    public void registerListener(EventObject e) {
        //页面确认按钮和取消按钮添加监听
        this.addClickListeners(KEY_OK, KEY_CANCEL);
        MulBasedataEdit mEdit = this.getView().getControl("aos_itemids");
        mEdit.addBeforeF7SelectListener(this);
    }

    @Override
    public void click(EventObject evt) {
        //获取被点击的控件对象
        Control source = (Control) evt.getSource();
        if (StringUtils.equals(source.getKey(), KEY_OK)) {
            // 获取物料
            List<String> itemIdList = new ArrayList<>();
            DynamicObjectCollection aos_itemids = (DynamicObjectCollection) this.getModel().getValue("aos_itemids");
            for (DynamicObject obj:aos_itemids) {
                DynamicObject aos_itemid = obj.getDynamicObject("fbasedataid");
                itemIdList.add(aos_itemid.getString("id"));
            }
            String tabValue = getModel().getValue("aos_tab").toString();
            List<String> tabList = Arrays.stream(tabValue.split(",")).filter(FndGlobal::IsNotNull).collect(Collectors.toList());

            Map<String,List<String>> resultMap = new HashMap<>(2);
            resultMap.put("itemIdList",itemIdList);
            resultMap.put("tabList",tabList);
            this.getView().returnDataToParent(resultMap);
            this.getView().close();
        } else if (StringUtils.equals(source.getKey(), KEY_CANCEL)) {
            //被点击控件为取消则设置返回值为空并关闭页面（在页面关闭回调方法中必须验证返回值不为空，否则会报空指针）
            this.getView().returnDataToParent(null);
            this.getView().close();
        }
    }

    @Override
    public void beforeF7Select(BeforeF7SelectEvent event) {
        String name = event.getProperty().getName();
        if ("aos_itemids".equals(name)) {
            String entityId = getView().getParentView().getEntityId();
            if ("aos_mkt_keyword".equals(entityId)){
                Object orgEntity = getView().getParentView().getModel().getValue("aos_orgid");
                if (orgEntity != null) {
                    String orgId = ((DynamicObject) orgEntity).getString("id");
                    QFilter filter = new QFilter("aos_contryentry.aos_nationality","=",orgId);
                    event.getCustomQFilters().add(filter);
                }
            }
        }
    }
}
