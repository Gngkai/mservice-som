package mkt.data.point;

import common.fnd.FndGlobal;
import common.sal.util.SalUtil;
import common.sal.util.SaveUtils;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.IFormView;
import kd.bos.form.control.Control;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;

import java.util.*;

/**
 * @author create by gk
 * @date 2023/7/25 20:06
 * @action 子表转入
 */
public class wordEditForm extends AbstractFormPlugin {
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
            String formId = this.getView().getParentView().getFormShowParameter().getFormId();
            if (formId.equals("aos_mkt_point")) {
                //判断是修改的数据还是新增的
                Map<String,String> map_addRow = new HashMap<>();
                DynamicObjectCollection dyc_parentData = getView().getParentView().getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity");
                Map<String,DynamicObject> map_parentData= new HashMap<>(dyc_parentData.size());
                for (DynamicObject row : dyc_parentData) {
                    if (FndGlobal.IsNotNull(row.get("aos_root"))){
                        map_parentData.put(row.getString("aos_root"),row);
                    }
                }

                for (int i = 1; i < 4; i++) {
                    DynamicObjectCollection dyc_row = getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity" + i);
                    for (DynamicObject row : dyc_row) {
                        if (FndGlobal.IsNotNull(row.get("aos_root"+i))){
                            DynamicObject parentRow;
                            //不是新增 && 父界面有这个数据
                            if (row.getBoolean("aos_add"+i)  &&  map_parentData.containsKey(row.getString("aos_root"+i))) {
                                 parentRow = map_parentData.get(row.getString("aos_root" + i));
                            }
                            //新增
                            else {
                                String value = row.getString("aos_root_attr" + i) + "/" + row.getString("aos_root_value" + i);
                                map_addRow.put(row.getString("aos_root"+i),value);
                                parentRow = dyc_parentData.addNew();
                                parentRow.set("aos_root",row.getString("aos_root"+i));
                            }
                            parentRow.set("aos_root_attr",row.get("aos_root_attr"+i));
                            parentRow.set("aos_root_value",row.get("aos_root_value"+i));

                        }
                    }
                }
                //新增的数据回写到词根表
                DynamicObject dy_org = (DynamicObject) getView().getParentView().getModel().getValue("aos_orgid");
                if (dy_org!=null){
                    QFilter filter_org = new QFilter("aos_org","=",dy_org.getPkValue());
                    List<DynamicObject> saveEntity = new ArrayList<>(),updateEntity = new ArrayList<>();
                    for (Map.Entry<String, String> entry : map_addRow.entrySet()) {
                        String[] split = entry.getValue().split("/");
                        QFilter filter_root = new QFilter("aos_root","=",entry.getKey());
                        //存在
                        if (QueryServiceHelper.exists("aos_mkt_root",new QFilter[]{filter_root,filter_org})) {
                            DynamicObject dy = BusinessDataServiceHelper.loadSingle("aos_mkt_root", new QFilter[]{filter_org, filter_root});
                            dy.set("aos_attribute",split[0]);
                            dy.set("aos_value",split[1]);
                            updateEntity.add(dy);
                        }
                        else {
                            DynamicObject dy = BusinessDataServiceHelper.newDynamicObject("aos_mkt_root");
                            dy.set("aos_org",dy_org.getPkValue());
                            dy.set("aos_root",entry.getKey());
                            dy.set("aos_attribute",split[0]);
                            dy.set("aos_value",split[1]);
                            saveEntity.add(dy);
                        }
                    }
                    SaveUtils.SaveEntity("aos_mkt_root",saveEntity,true);
                    SaveUtils.UpdateEntity(updateEntity,true);
                }
            }
            this.getView().close();
        }
    }

    private void init(){
        IFormView parentView = this.getView().getParentView();
        String formId = parentView.getFormShowParameter().getFormId();
        if (formId.equals("aos_mkt_point")){
            DynamicObjectCollection parentRows = parentView.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity");
            DynamicObjectCollection entity1 = this.getView().getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity1");
            DynamicObjectCollection entity2 = this.getView().getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity2");
            DynamicObjectCollection entity3 = this.getView().getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity3");
            for (DynamicObject parentRow : parentRows) {
                String root = parentRow.getString("aos_root");
                if (FndGlobal.IsNull(root))
                    continue;
                String[] split = root.split(" ");
                int rootSize = split.length;
                if (rootSize>3)
                    rootSize = 3;
                DynamicObject newRow;
                if (rootSize == 1)
                    newRow = entity1.addNew();
                else if (rootSize == 2)
                    newRow = entity2.addNew();
                else
                    newRow = entity3.addNew();
                newRow.set("aos_root"+rootSize,parentRow.get("aos_root"));
                newRow.set("aos_root_attr"+rootSize,parentRow.get("aos_root_attr"));
                newRow.set("aos_word_fre"+rootSize,parentRow.get("aos_word_fre"));
                newRow.set("aos_root_value"+rootSize,parentRow.get("aos_root_value"));
                newRow.set("aos_total"+rootSize,parentRow.get("aos_total"));
                newRow.set("aos_add"+rootSize,true);
            }
            this.getView().updateView("aos_entryentity1");
            this.getView().updateView("aos_entryentity2");
            this.getView().updateView("aos_entryentity3");
        }
    }
}
