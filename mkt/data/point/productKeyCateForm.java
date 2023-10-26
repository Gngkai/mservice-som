package mkt.data.point;

import common.sal.sys.basedata.dao.ItemCategoryDao;
import common.sal.sys.basedata.dao.ItemDao;
import common.sal.sys.basedata.dao.impl.ItemCategoryDaoImpl;
import common.sal.sys.basedata.dao.impl.ItemDaoImpl;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.datamodel.*;
import kd.bos.form.FormShowParameter;
import kd.bos.form.control.Control;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.plugin.AbstractFormPlugin;

import java.util.*;

/**
 * @author: GK
 * @create: 2023-10-25 16:36
 * @Description: 品名关键词库选择品类
 */
public class productKeyCateForm extends AbstractFormPlugin {
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


    private void  init(){

        String entityId = this.getView().getParentView().getEntityId();
        if (entityId.equals("aos_mkt_point_from")){
            FormShowParameter formShowParameter = this.getView().getFormShowParameter();
            if (formShowParameter.getCustomParam("org")!=null) {
                String org =  formShowParameter.getCustomParam("org");
                ItemDao itemDao = new ItemDaoImpl();
                DynamicObjectCollection dyc  = itemDao.listItemObj(Long.parseLong(org));
                List<String> ids = new ArrayList<>(dyc.size());
                for (DynamicObject dy : dyc) {
                    ids.add(dy.getString("id"));
                }

                ItemCategoryDao categoryDao = new ItemCategoryDaoImpl();
                Map<String, DynamicObject> dyc_cate = categoryDao.getItemCategoryByItemId("material.name mname,group,group.number gnumber", ids);
                List<String> list_repeatValue = new ArrayList<>(dyc_cate.size());

                TableValueSetter setter=new TableValueSetter();
                setter.addField("aos_item");
                setter.addField("aos_cate");
                for (Map.Entry<String, DynamicObject> entry : dyc_cate.entrySet()) {
                    if (entry.getValue().getString("gnumber").equals("waitgroup"))
                       continue;
                    DynamicObject dy_value = entry.getValue();
                    String value = dy_value.getString("mname")+"/"+dy_value.getString("group");
                    if (list_repeatValue.contains(value))
                        continue;
                    list_repeatValue.add(value);

                   setter.addRow(dy_value.getString("mname"),dy_value.getString("group"));
                }

                AbstractFormDataModel model= (AbstractFormDataModel) this.getModel();
                model.beginInit();
                model.batchCreateNewEntryRow("aos_ent",setter);
                model.endInit();
                this.getView().updateView("aos_ent");
            }
        }
    }

    @Override
    public void click(EventObject evt) {
        super.click(evt);
        Control source = (Control) evt.getSource();
        String key = source.getKey();
        if (key.equals("btnok")){
            EntryGrid aos_ent = this.getControl("aos_ent");
            int[] selectRows = aos_ent.getSelectRows();
            List<DynamicObject> returnData = new ArrayList<>(selectRows.length);
            DynamicObjectCollection dyc = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_ent");
            for (int selectRow : selectRows) {
                returnData.add(dyc.get(selectRow));
            }
            this.getView().returnDataToParent(returnData);
            this.getView().close();
        }
    }
}
