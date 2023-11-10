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

public class aos_mkt_aaddmodel_show extends AbstractFormPlugin {

	public void registerListener(EventObject e) {
		super.registerListener(e);
		this.addClickListeners("btnok");
	}

	public void click(EventObject evt) {
		super.click(evt);
		Control control = (Control) evt.getSource();
		String key = control.getKey();
		if (key.equals("btnok")) {
			String aos_button = (String) this.getModel().getValue("aos_button");
			String aos_name = (String) this.getModel().getValue("aos_name");
			this.getView().getParentView().getModel().setValue("aos_tab", aos_name,Integer.valueOf(aos_button));
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
		DynamicObjectCollection tabEntityRows = parentView.getModel().getDataEntity(true).getDynamicObjectCollection("aos_ent_tab");
		for (int i = 0; i < tabEntityRows.size(); i++) {
			DynamicObject row = tabEntityRows.get(i);
			LocaleString locale = new LocaleString();
			String tabName =  row.getString("aos_tab");
			if (FndGlobal.IsNotNull(tabName)){
				tabName = tabName+" ( 页签 "+ (i+1) +" )";
			}
			else {
				tabName = "页签 "+ (i+1) +"";
			}
			locale.setLocaleValue_zh_CN(tabName);
			data.add(new ComboItem(locale,String.valueOf(i)));
		}
		comboEdit.setComboItems(data);
	}
}