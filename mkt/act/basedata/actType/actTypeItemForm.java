package mkt.act.basedata.actType;

import common.fnd.FndGlobal;
import common.sal.sys.basedata.dao.ItemCategoryDao;
import common.sal.sys.basedata.dao.impl.ItemCategoryDaoImpl;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.form.control.Button;
import kd.bos.form.control.Control;
import kd.bos.form.field.ComboEdit;
import kd.bos.form.field.ComboItem;
import kd.bos.form.plugin.AbstractFormPlugin;

import java.util.*;

/**
 * @author: GK
 * @create: 2023-09-20 15:05
 * @Description: 活动库物料 品名弹窗
 */
public class actTypeItemForm extends AbstractFormPlugin {
    @Override
    public void afterBindData(EventObject e) {
        super.afterBindData(e);
        init();
    }
    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        Button btu=this.getView().getControl("btnok");
        btu.addClickListener(this);
        this.addClickListeners("btnok");
    }
    @Override
    public void click(EventObject evt) {
        super.click(evt);
        Control control= (Control) evt.getSource();
        if (control.getKey().equals("btnok")){
            String value = this.getModel().getValue("aos_item").toString();
            this.getView().returnDataToParent(value);
            this.getView().close();
        }
    }

    private void init(){
        String entityId = this.getView().getParentView().getEntityId();
        if (FndGlobal.IsNotNull(entityId)){

            Object customParam = getView().getFormShowParameter().getCustomParam("value");
            List<String> cateids = Arrays.asList(customParam.toString());
            ItemCategoryDao itemCategoryDao = new ItemCategoryDaoImpl();
            DynamicObjectCollection itemInfoes = itemCategoryDao.queryItemByCate(cateids);

            List<ComboItem> comboItems=new ArrayList<>();
            ComboEdit comboEdit = this.getControl("aos_item");
            Set<String> itemNames = new HashSet<>(itemInfoes.size());
            for (DynamicObject dy : itemInfoes) {
                itemNames.add(dy.getString("name"));
            }
            for (String name : itemNames) {
                comboItems.add(new ComboItem(new LocaleString(name),name));
            }
            comboEdit.setComboItems(comboItems);
            comboEdit.setComboInputable(true);
        }
    }
}
