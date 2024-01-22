package mkt.progress.design;

import java.util.EventObject;
import java.util.HashMap;
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
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;

/**
 * @author aosom
 * @version Slogan品类-动态表单插件
 */
public class AosMktSloganForm extends AbstractFormPlugin {
    private static final String BTNOK = "btnok";
    private static final String AOS_CATEGORY1 = "aos_category1";
    private static final String AOS_CATEGORY2 = "aos_category2";
    private static final String AOS_ITEMNAME_CATEGORY1 = "aos_itemname_category1";
    private static final String AOS_ITEMNAME_CATEGORY3 = "aos_itemname_category3";
    private static final String AOS_CNAME = "aos_cname";

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
            Map<String, Object> para = this.getView().getFormShowParameter().getCustomParam("params");
            if (para.containsKey(AOS_CATEGORY1)) {
                EntryGrid aosEntryentity = this.getControl("aos_entryentity");
                int[] row = aosEntryentity.getSelectRows();
                if (row.length > 0) {
                    this.getView().getParentView().getModel().setValue("aos_category2",
                        this.getModel().getValue("aos_category", row[0]));
                    this.getView().getParentView().getModel().setValue("aos_category3", null);
                    this.getView().close();
                } else {
                    this.getView().showErrorNotification("请选择一行数据!");
                }
            } else if (para.containsKey(AOS_CATEGORY2)) {
                EntryGrid aosEntryentity = this.getControl("aos_entryentity");
                int[] row = aosEntryentity.getSelectRows();
                if (row.length > 0) {
                    this.getView().getParentView().getModel().setValue("aos_category3",
                        this.getModel().getValue("aos_category", row[0]));
                    this.getView().close();
                } else {
                    this.getView().showErrorNotification("请选择一行数据!");
                }
            }

            else if (para.containsKey(AOS_ITEMNAME_CATEGORY1)) {
                EntryGrid aosEntryentity = this.getControl("aos_entryentity");
                int[] row = aosEntryentity.getSelectRows();
                if (row.length > 0) {
                    this.getView().getParentView().getModel().setValue("aos_itemname",
                        this.getModel().getValue("aos_category", row[0]),
                        this.getView().getParentView().getModel().getEntryCurrentRowIndex("aos_entryentity"));
                    this.getView().close();
                } else {
                    this.getView().showErrorNotification("请选择一行数据!");
                }
            } else if (para.containsKey(AOS_CNAME)) {
                EntryGrid aosEntryentity = this.getControl("aos_entryentity");
                int[] row = aosEntryentity.getSelectRows();
                if (row.length > 0) {
                    this.getView().getParentView().getModel().setValue("aos_cname",
                        this.getModel().getValue("aos_category", row[0]));
                    this.getView().getParentView().getModel().setValue("aos_itemname",
                        this.getModel().getValue("aos_category", row[0]), 0);
                    this.getView().close();
                } else {
                    this.getView().showErrorNotification("请选择一行数据!");
                }
            }
        }
    }

    @Override
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        Map<String, Object> para = this.getView().getFormShowParameter().getCustomParam("params");
        if (para.containsKey(AOS_CATEGORY1) || para.containsKey(AOS_CATEGORY2)) {
            DynamicObjectCollection aosSyncOperateS = QueryServiceHelper.query("bd_materialgroup", "name,level",
                new QFilter("parent.name", QCP.in, para.values()).toArray());
            this.getModel().deleteEntryData("aos_entryentity");
            int i = 0;
            for (DynamicObject aosSyncOperate : aosSyncOperateS) {
                this.getModel().batchCreateNewEntryRow("aos_entryentity", 1);
                this.getModel().setValue("aos_category",
                    aosSyncOperate.getString("name").split(",")[aosSyncOperate.getInt("level") - 1], i);
                i++;
            }
        } else if (para.containsKey(AOS_ITEMNAME_CATEGORY1)) {
            String category = para.get("aos_itemname_category1") + "," + para.get("aos_itemname_category2");
            if (FndGlobal.IsNotNull(para.get(AOS_ITEMNAME_CATEGORY3))) {
                category += ("," + para.get("aos_itemname_category3"));
            }
            FndMsg.debug("category =" + category);
            DynamicObject[] aosSyncOperateS = BusinessDataServiceHelper.load("bd_materialgroupdetail", "material",
                new QFilter("group.name", QCP.equals, category).toArray());
            this.getModel().deleteEntryData("aos_entryentity");
            Map<String, Object> map = new HashMap<>(16);
            for (DynamicObject aosSyncOperate : aosSyncOperateS) {
                String name = aosSyncOperate.getDynamicObject("material").getString("name.en_US");
                if (FndGlobal.IsNull(name)) {
                    continue;
                }
                map.put(name, name);
            }
            int i = 0;
            for (String key : map.keySet()) {
                this.getModel().batchCreateNewEntryRow("aos_entryentity", 1);
                this.getModel().setValue("aos_category", key, i);
                i++;
            }
        } else if (para.containsKey(AOS_CNAME)) {
            String category = (String)para.get("aos_cname");
            FndMsg.debug("category:" + category);
            DynamicObjectCollection aosSyncOperateS = QueryServiceHelper.query("bd_materialgroupdetail",
                " material.name name", new QFilter("group.name", QCP.equals, category).toArray());
            this.getModel().deleteEntryData("aos_entryentity");
            List<String> list =
                aosSyncOperateS.stream().map(dy -> dy.getString("name")).distinct().collect(Collectors.toList());
            for (int i = 0; i < list.size(); i++) {
                this.getModel().batchCreateNewEntryRow("aos_entryentity", 1);
                this.getModel().setValue("aos_category", list.get(i), i);
            }
        }
    }
}