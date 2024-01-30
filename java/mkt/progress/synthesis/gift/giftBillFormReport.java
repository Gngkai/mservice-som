package mkt.progress.synthesis.gift;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.metadata.IDataEntityProperty;
import kd.bos.dataentity.metadata.dynamicobject.DynamicObjectType;
import kd.bos.entity.datamodel.AbstractFormDataModel;
import kd.bos.entity.datamodel.TableValueSetter;
import kd.bos.entity.property.EntryProp;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author: GK
 * @create: 2024-01-26 10:33
 * @Description: 赠品流程报表
 */
public class giftBillFormReport extends AbstractFormPlugin {
    @Override
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        query();
    }

    @Override
    public void afterDoOperation(AfterDoOperationEventArgs eventArgs) {
        super.afterDoOperation(eventArgs);
        String operateKey = eventArgs.getOperateKey();
        try {
            if (operateKey.equals("aos_query")){
                query();
                this.getView().updateView("aos_entryentity");
            }
        }
        catch (Exception e){
            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            this.getView().showMessage(writer.toString());
            e.printStackTrace();
        }
    }
    private void query(){
        this.getModel().deleteEntryData("aos_entryentity");
       // QFilter[] filterArray = getFilterArray();
        DynamicObjectType type = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity").getDynamicObjectType();
        List<String> list_fields = getDynamicObjectType(type);
        DynamicObject dy_act = BusinessDataServiceHelper.newDynamicObject("aos_mkt_gift");
        Map<String,String> map_fieldsPath = new HashMap<>();
        readFieldFullPath(dy_act.getDynamicObjectType(),list_fields,map_fieldsPath);
        StringJoiner selectFields = new StringJoiner(",");
        for (Map.Entry<String, String> entry : map_fieldsPath.entrySet()) {
            selectFields.add(entry.getValue()+" "+entry.getKey());
        }
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_gift", selectFields.toString(), null);
        TableValueSetter setter=new TableValueSetter();
        for (String field : list_fields) {
            setter.addField(field);
        }
        for (DynamicObject object : dyc) {
            List<Object> list_value = new ArrayList<>(list_fields.size());
            for (String field : list_fields) {
                list_value.add(object.get(field));
            }
            Object[] rowValue = list_value.toArray(new Object[list_value.size()]);
            setter.addRow(rowValue);
        }
        AbstractFormDataModel model= (AbstractFormDataModel) this.getModel();
        model.beginInit();
        model.batchCreateNewEntryRow("aos_entryentity",setter);
        model.endInit();
    }
    /**
     * 读取字段标识
     *
     * @param objectType
     * @return
     */
    public static List<String> getDynamicObjectType(DynamicObjectType objectType) {
        return objectType.getProperties().stream().filter(pro -> !pro.getPropertyType().getName().equals("long"))
                .map(property -> property.getName()).filter(name -> !name.equals("id"))
                .filter(name -> !name.equals("seq")).collect(Collectors.toList());
    }
    /**
     * 查找标识在单据体中的全路径
     *
     * @param dType              单据体
     * @param list_queryFields   查找字段
     * @param map_fullPathFields 字段全路径
     * @param addEntityname      方法内部设置字段
     */
    public static void readFieldFullPath(DynamicObjectType dType, List<String> list_queryFields,
                                         Map<String, String> map_fullPathFields, String... addEntityname) {
        // 获取数据包对应的实体模型

        for (IDataEntityProperty property : dType.getProperties()) {
            if (property instanceof EntryProp) {
                // 集合属性，关联子实体，值是数据包集合
                DynamicObjectType propertyType = ((EntryProp) property).getDynamicCollectionItemPropertyType();
                StringJoiner str = new StringJoiner(".");
                if (addEntityname.length > 0)
                    str.add(addEntityname[0]);
                str.add(property.getName());
                readFieldFullPath(propertyType, list_queryFields, map_fullPathFields, str.toString());
            } else {
                if (list_queryFields.contains(property.getName())) {
                    if (addEntityname.length > 0) {
                        map_fullPathFields.put(property.getName(), addEntityname[0] + "." + property.getName());
                    } else
                        map_fullPathFields.put(property.getName(), property.getName());
                }
            }
        }
    }
}
