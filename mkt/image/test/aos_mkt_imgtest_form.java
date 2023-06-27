package mkt.image.test;

import java.util.EventObject;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.servicehelper.BusinessDataServiceHelper;

public class aos_mkt_imgtest_form extends AbstractFormPlugin{

	public void registerListener(EventObject e) {
		super.registerListener(e);
		// 给工具栏加监听事件
		this.addItemClickListeners("aos_toolbarap");
		this.addItemClickListeners("aos_query"); // 提交
	}
	
	public void itemClick(ItemClickEvent evt) {
		super.itemClick(evt);
		String Control = evt.getItemKey();
		
		System.out.println("Control ="+Control);
		
		if (Control.equals("aos_query")) {
			aos_query();
		}
	}

	private void aos_query() {
		System.out.println("into aos_query");
		
		Object aos_itemid = this.getModel().getValue("aos_itemid");
		
		if (aos_itemid == null)
		 return;
		
		DynamicObject aos_item = (DynamicObject) aos_itemid;
		long id = aos_item.getLong("id");
		
		
		DynamicObject bd_material=  BusinessDataServiceHelper.loadSingle(id, "bd_material");
		Object aos_itemname = bd_material.get("name");
		Object aos_seting_cn = bd_material.get("aos_seting_cn");
		
		this.getModel().deleteEntryData("aos_entryentity");
		
		/*DynamicObjectCollection aos_entryentityS = this.getView().getModel().getEntryEntity("aos_entryentity");
		aos_entryentityS.addNew();*/
		this.getModel().batchCreateNewEntryRow("aos_entryentity", 1);
		
		
		this.getModel().setValue("aos_item", id,0);
		this.getModel().setValue("aos_itemname", aos_itemname,0);
		this.getModel().setValue("aos_spec", aos_seting_cn,0);

		
		
		
	}
	
}
