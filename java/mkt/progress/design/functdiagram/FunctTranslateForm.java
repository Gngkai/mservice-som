package mkt.progress.design.functdiagram;

import common.Cux_Common_Utl;
import common.fnd.FndGlobal;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.form.FormShowParameter;
import kd.bos.form.control.Control;
import kd.bos.form.field.ComboEdit;
import kd.bos.form.field.ComboItem;
import kd.bos.form.plugin.AbstractFormPlugin;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author create by ?
 */
public class FunctTranslateForm extends AbstractFormPlugin {
    private static final String BTNOK = "btnok";
    private static final String AOS_AADD_MODEL = "aos_aadd_model";

    @Override
    public void afterBindData(EventObject e) {
        super.afterBindData(e);
        init();
    }

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
            Map<String, Object> mapReturn = new HashMap<>(16);
            // 翻译源
            String aosSource = (String)this.getModel().getValue("aos_source");
            mapReturn.put("source", aosSource);
            // 翻译端
            String aosTerminal = (String)this.getModel().getValue("aos_terminal");
            List<String> listTerminal =
                Arrays.stream(aosTerminal.split(",")).filter(value -> !Cux_Common_Utl.IsNull(value))
                    .filter(value -> !value.equals(aosSource)).collect(Collectors.toList());
            mapReturn.put("terminal", listTerminal);
            // 图片页
            String aosTable = (String)this.getModel().getValue("aos_table");
            List<String> listTable = Arrays.stream(aosTable.split(",")).filter(value -> !Cux_Common_Utl.IsNull(value))
                .collect(Collectors.toList());
            mapReturn.put("img", listTable);
            // 回传
            this.getView().returnDataToParent(mapReturn);
            this.getView().close();
        }
    }

    private void init() {
        ComboEdit comboEdit = this.getControl("aos_terminal");
        List<ComboItem> data = new ArrayList<>();
        FormShowParameter formShowParameter = this.getView().getFormShowParameter();
        List<String> userLan = formShowParameter.getCustomParam("userLan");
        for (String lan : userLan) {
            data.add(new ComboItem(new LocaleString(lan), lan));
        }
        comboEdit.setComboItems(data);

        String entityId = getView().getParentView().getEntityId();
        // 高级A+模块 设置模块信息
        if (AOS_AADD_MODEL.equals(entityId)) {
            comboEdit = this.getControl("aos_table");
            DynamicObjectCollection tabEntityRows =
                this.getView().getParentView().getModel().getDataEntity(true).getDynamicObjectCollection("aos_ent_tab");
            data = new ArrayList<>(tabEntityRows.size());
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
}
