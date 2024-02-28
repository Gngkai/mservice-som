package mkt.act.basedata.actType;

import common.fnd.FndGlobal;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.entity.datamodel.IDataModel;
import kd.bos.form.IPageCache;
import kd.bos.form.control.Button;
import kd.bos.form.control.Control;
import kd.bos.form.field.ComboEdit;
import kd.bos.form.field.ComboItem;
import kd.bos.servicehelper.QueryServiceHelper;

import java.util.*;

/**
 * @author: GK
 * @create: 2024-02-28 13:56
 * @Description:    活动库 值弹窗 aos_act_type_se
 */
@SuppressWarnings("unused")
public class ActTypeSelVueForm extends AbstractBillPlugIn {
    private static final List<String> selValueList = Arrays.asList("festive","brands");
    private static final String CACHE_KEY = "cacheKey";
    private static final String CACHE_ROW = "cacheRow";
    @Override
    public void afterBindData(EventObject e) {
        super.afterBindData(e);
        init();
    }
    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        Button btu=this.getView().getControl("btnok");
        btu.addClickListener(this);
    }
    @Override
    public void click(EventObject evt) {
        super.click(evt);
        Control control= (Control) evt.getSource();
        if (control.getKey().equals("btnok")){
            String type = getView().getPageCache().get(CACHE_KEY);
            int parentRow = Integer.parseInt(getView().getPageCache().get(CACHE_ROW));

            String returnValue;
            if (FndGlobal.IsNotNull(type) && "2".equals(type)){
                String value = getModel().getValue("aos_value2").toString();
                if (FndGlobal.IsNotNull(value)){
                    StringJoiner str = new StringJoiner(",");
                    for (String splValue : value.split(",")) {
                        if (FndGlobal.IsNotNull(splValue)) {
                            str.add(splValue);
                        }
                    }
                    returnValue = str.toString();
                }
                else {
                    returnValue = "";
                }
            }
            else {
               returnValue = getModel().getValue("aos_value1").toString();
            }

            getView().getParentView().getModel().setValue("aos_rule_value",returnValue,parentRow);
            this.getView().close();
        }
    }

    private void init(){
        IDataModel parentModel = getView().getParentView().getModel();
        int rowIndex = parentModel.getEntryCurrentRowIndex("aos_project");
        String value = parentModel.getValue("aos_project", rowIndex).toString();
        IPageCache pageCache = getView().getPageCache();
        pageCache.put(CACHE_ROW,String.valueOf(rowIndex));

        //节日或者品牌
        if (FndGlobal.IsNotNull(value) && selValueList.contains(value)){
            this.getView().setVisible(false,"aos_value1");
            ComboEdit comboEdit = this.getControl("aos_value2");
            List<ComboItem> data = new ArrayList<>();
            //节日属性
            if ("festive".equals(value)){
                DynamicObjectCollection festRulst = QueryServiceHelper.query("aos_scm_fest_attr", "name", null);
                for (DynamicObject row : festRulst) {
                    String name = row.getString("name");
                    if (FndGlobal.IsNotNull(name)) {
                        data.add(new ComboItem(new LocaleString(name), name));
                    }
                }
            }
            //品牌
            else {
                DynamicObjectCollection festRulst = QueryServiceHelper.query("mdr_item_brand", "name", null);
                for (DynamicObject row : festRulst) {
                    String name = row.getString("name");
                    if (FndGlobal.IsNotNull(name)) {
                        data.add(new ComboItem(new LocaleString(name), name));
                    }
                }

            }
            //缓存中记录类型是 下拉选择框
            pageCache.put(CACHE_KEY,"2");
            comboEdit.setComboItems(data);
        }
        else {
            this.getView().setVisible(false,"aos_value2");
            //缓存中记录类型是 文本框
            pageCache.put(CACHE_KEY,"1");
        }
    }
}
