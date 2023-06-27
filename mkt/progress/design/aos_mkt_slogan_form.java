package mkt.progress.design;

import java.util.EventObject;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import common.fnd.FndGlobal;
import common.fnd.FndMsg;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.control.Control;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;

public class aos_mkt_slogan_form extends AbstractFormPlugin {

	public void registerListener(EventObject e) {
		super.registerListener(e);
		this.addClickListeners("btnok");
	}

	public void click(EventObject evt) {
		super.click(evt);
		Control control = (Control) evt.getSource();
		String key = control.getKey();
		if (key.equals("btnok")) {
			Map<String, Object> para = this.getView().getFormShowParameter().getCustomParam("params");
			if (para.keySet().contains("aos_category1")) {
				EntryGrid aos_entryentity = this.getControl("aos_entryentity");
				int[] row = aos_entryentity.getSelectRows();
				if (row.length > 0) {
					this.getView().getParentView().getModel().setValue("aos_category2",
							this.getModel().getValue("aos_category", row[0]));
					this.getView().getParentView().getModel().setValue("aos_category3", null);
					this.getView().close();
				} else {
					this.getView().showErrorNotification("请选择一行数据!");
				}
			} else if (para.keySet().contains("aos_category2")) {
				EntryGrid aos_entryentity = this.getControl("aos_entryentity");
				int[] row = aos_entryentity.getSelectRows();
				if (row.length > 0) {
					this.getView().getParentView().getModel().setValue("aos_category3",
							this.getModel().getValue("aos_category", row[0]));
					this.getView().close();
				} else {
					this.getView().showErrorNotification("请选择一行数据!");
				}
			}

			else if (para.keySet().contains("aos_itemname_category1")) {
				EntryGrid aos_entryentity = this.getControl("aos_entryentity");
				int[] row = aos_entryentity.getSelectRows();
				if (row.length > 0) {
					this.getView().getParentView().getModel().setValue("aos_itemname",
							this.getModel().getValue("aos_category", row[0]),
							this.getView().getParentView().getModel().getEntryCurrentRowIndex("aos_entryentity"));
					this.getView().close();
				} else {
					this.getView().showErrorNotification("请选择一行数据!");
				}
			}
			else if (para.keySet().contains("aos_cname")) {
				EntryGrid aos_entryentity = this.getControl("aos_entryentity");
				int[] row = aos_entryentity.getSelectRows();
				if (row.length > 0) {
					this.getView().getParentView().getModel().setValue("aos_cname",
							this.getModel().getValue("aos_category", row[0]));
					this.getView().getParentView().getModel().setValue("aos_itemname",
							this.getModel().getValue("aos_category", row[0]),0);
					this.getView().close();
				} else {
					this.getView().showErrorNotification("请选择一行数据!");
				}
			}
		}
	}

	public void afterCreateNewData(EventObject e) {
		super.afterCreateNewData(e);
		Map<String, Object> para = this.getView().getFormShowParameter().getCustomParam("params");
		if (para.keySet().contains("aos_category1") || para.keySet().contains("aos_category2")) {
			DynamicObjectCollection aos_sync_operateS = QueryServiceHelper.query("bd_materialgroup", "name,level",
					new QFilter("parent.name", QCP.in, para.values()).toArray());
			this.getModel().deleteEntryData("aos_entryentity");
			int i = 0;
			for (DynamicObject aos_sync_operate : aos_sync_operateS) {
				this.getModel().batchCreateNewEntryRow("aos_entryentity", 1);
				this.getModel().setValue("aos_category",
						aos_sync_operate.getString("name").split(",")[aos_sync_operate.getInt("level") - 1], i);
				i++;
			}
		} else if (para.keySet().contains("aos_itemname_category1")) {
			String category = (String) para.get("aos_itemname_category1") + ","
					+ (String) para.get("aos_itemname_category2");
			if (FndGlobal.IsNotNull(para.get("aos_itemname_category3"))) {
				category += ("," + para.get("aos_itemname_category3"));
			}
			FndMsg.debug("category =" + category);
			DynamicObjectCollection aos_sync_operateS = QueryServiceHelper.query("bd_materialgroupdetail",
					" material.name name", new QFilter("group.name", QCP.equals, category).toArray());
			this.getModel().deleteEntryData("aos_entryentity");

			List<String> list = aos_sync_operateS.stream().map(dy -> dy.getString("name")).distinct()
					.collect(Collectors.toList());
			for (int i = 0; i < list.size(); i++) {
				this.getModel().batchCreateNewEntryRow("aos_entryentity", 1);
				this.getModel().setValue("aos_category", list.get(i), i);
			}
		} else if (para.keySet().contains("aos_cname"))  {
			String category =(String) para.get("aos_cname");
			FndMsg.debug("category:"+category);
			
			DynamicObjectCollection aos_sync_operateS = QueryServiceHelper.query("bd_materialgroupdetail",
					" material.name name", new QFilter("group.name", QCP.equals, category).toArray());
			this.getModel().deleteEntryData("aos_entryentity");

			List<String> list = aos_sync_operateS.stream().map(dy -> dy.getString("name")).distinct()
					.collect(Collectors.toList());
			for (int i = 0; i < list.size(); i++) {
				this.getModel().batchCreateNewEntryRow("aos_entryentity", 1);
				this.getModel().setValue("aos_category", list.get(i), i);
			}
		}
	}
}