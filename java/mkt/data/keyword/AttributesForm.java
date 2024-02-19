package mkt.data.keyword;

import kd.bos.dataentity.entity.LocaleString;
import kd.bos.form.control.Control;
import kd.bos.form.field.ComboEdit;
import kd.bos.form.field.ComboItem;
import kd.bos.form.plugin.AbstractFormPlugin;

import java.util.EventObject;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author GK
 * @since 2024-02-01 14:03
 * @version 属性细分选择框
 */
public class AttributesForm extends AbstractFormPlugin {
    private static final String DETAIL_LIST = "detailList";
    private static final String DETAIL_KEY = "aos_detail";
    private static final String BTNOK = "btnok";

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
        Control control = (Control)evt.getSource();
        String key = control.getKey();
        if (BTNOK.equals(key)) {
            btnok();
        }
    }

    /**
     * 确认按钮
     */
    private void btnok() {
        String value = (String)getModel().getValue(DETAIL_KEY);
        getView().returnDataToParent(value);
        this.getView().close();
    }

    private void init() {
        Map<String, Object> params = getView().getFormShowParameter().getCustomParam("params");
        @SuppressWarnings("unchecked")
        List<String> datailList = (List<String>)params.get(DETAIL_LIST);
        // 设置过滤
        ComboEdit comboEdit = this.getControl(DETAIL_KEY);
        comboEdit.setComboInputable(false);
        // 设置下拉框的值
        List<ComboItem> data = new LinkedList<>();
        for (String value : datailList) {
            LocaleString locale = new LocaleString();
            locale.setLocaleValue_zh_CN(value);
            data.add(new ComboItem(locale, value));
        }
        comboEdit.setComboItems(data);
    }
}
