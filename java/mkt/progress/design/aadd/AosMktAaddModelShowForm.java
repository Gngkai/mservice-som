package mkt.progress.design.aadd;

import java.util.*;

import common.fnd.FndGlobal;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.form.IFormView;
import kd.bos.form.control.Control;
import kd.bos.form.field.ComboEdit;
import kd.bos.form.field.ComboItem;
import kd.bos.form.plugin.AbstractFormPlugin;

/**
 * @author aosom
 * @version 高级A+模板弹框-动态表单插件
 */
public class AosMktAaddModelShowForm extends AbstractFormPlugin {
    public final static String BTNOK = "btnok";

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
        if (BTNOK.equals(key)) {
            String aosButton = (String)this.getModel().getValue("aos_button");
            String aosName = (String)this.getModel().getValue("aos_name");
            this.getView().getParentView().getModel().setValue("aos_tab", aosName, Integer.parseInt(aosButton));
            this.getView().close();
        }
    }

    @Override
    public void afterBindData(EventObject e) {
        super.afterBindData(e);
        // 设置下拉框可修改
        ComboEdit comboEdit = this.getControl("aos_button");
        comboEdit.setComboInputable(false);
        // 设置下拉框的值
        List<ComboItem> data = new ArrayList<>();
        IFormView parentView = this.getView().getParentView();
        DynamicObjectCollection tabEntityRows =
            parentView.getModel().getDataEntity(true).getDynamicObjectCollection("aos_ent_tab");
        for (int i = 0; i < tabEntityRows.size(); i++) {
            DynamicObject row = tabEntityRows.get(i);
            LocaleString locale = new LocaleString();
            String tabName = row.getString("aos_tab");
            if (FndGlobal.IsNotNull(tabName)) {
                tabName = tabName + " ( 页签 " + (i + 1) + " )";
            } else {
                tabName = "页签 " + (i + 1);
            }
            locale.setLocaleValue_zh_CN(tabName);
            data.add(new ComboItem(locale, String.valueOf(i)));
        }
        comboEdit.setComboItems(data);
    }
}