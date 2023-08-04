package mkt.progress.design.functDiagram;

import common.fnd.FndGlobal;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.ILocaleString;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.form.plugin.AbstractFormPlugin;

/**
 * @author create by gk
 * @date 2023/7/20 14:32
 * @action 敏感词参数表界面
 */
public class sensitiveForm extends AbstractFormPlugin {
    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();
        if (name.equals("aos_cate")){
            int rowIndex = e.getChangeSet()[0].getRowIndex();
            Object newValue = e.getChangeSet()[0].getNewValue();
            if (rowIndex>=0 ){
                if (FndGlobal.IsNotNull(newValue)){
                    DynamicObject dy_cate = (DynamicObject) newValue;
                    ILocaleString cateName = dy_cate.getLocaleString("name");
                    String localeValue_en = cateName.getLocaleValue_en();
                    String[] split = localeValue_en.split(",");
                    for (int i = 0; i < 3; i++) {
                        if (split.length<=i){
                            getModel().setValue("aos_cate"+(i+1),"",rowIndex);
                        }
                        else {
                            getModel().setValue("aos_cate"+(i+1),split[i],rowIndex);
                        }
                    }
                }
                else{
                    getModel().setValue("aos_cate1","",rowIndex);
                    getModel().setValue("aos_cate2","",rowIndex);
                    getModel().setValue("aos_cate3","",rowIndex);
                }
            }

        }
    }
}
