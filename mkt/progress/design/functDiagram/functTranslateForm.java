package mkt.progress.design.functDiagram;

import common.Cux_Common_Utl;
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
 * @date 2023/4/21 13:41
 * @action
 */
public class functTranslateForm extends AbstractFormPlugin {
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
        Control source = (Control) evt.getSource();
        String key = source.getKey();
        if (key.equals("btnok")){
            Map<String,Object> map_return = new HashMap<>();
            //翻译源
            String aos_source = (String) this.getModel().getValue("aos_source");
            map_return.put("source",aos_source);
            //翻译端
            String aos_terminal = (String) this.getModel().getValue("aos_terminal");
            List<String> list_terminal = Arrays.stream(aos_terminal.split(","))
                    .filter(value -> !Cux_Common_Utl.IsNull(value))
                    .filter(value -> !value.equals(aos_source))
                    .collect(Collectors.toList());
            map_return.put("terminal",list_terminal);
            //图片页
            String aos_table = (String) this.getModel().getValue("aos_table");
            List<String> list_table = Arrays.stream(aos_table.split(","))
                    .filter(value -> !Cux_Common_Utl.IsNull(value))
                    .collect(Collectors.toList());
            map_return.put("img",list_table);
            //回传
            this.getView().returnDataToParent(map_return);
            this.getView().close();
        }
    }
    private void init(){
        ComboEdit comboEdit = this.getControl("aos_terminal");
        List<ComboItem> data = new ArrayList<>();
        FormShowParameter formShowParameter = this.getView().getFormShowParameter();
        List<String> userLan = formShowParameter.getCustomParam("userLan");
        for (String lan : userLan) {
            data.add(new ComboItem(new LocaleString(lan),lan));
        }
        comboEdit.setComboItems(data);
    }
}
