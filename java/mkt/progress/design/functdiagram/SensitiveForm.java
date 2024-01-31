package mkt.progress.design.functdiagram;

import common.fnd.FndGlobal;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.ILocaleString;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.form.plugin.AbstractFormPlugin;

/**
 * @author create by gk
 * @since 2023/7/20 14:32
 * @version 敏感词参数表界面
 */
public class SensitiveForm extends AbstractFormPlugin {
    public final static String AOS_CATE = "aos_cate";

    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();
        if (AOS_CATE.equals(name)) {
            int rowIndex = e.getChangeSet()[0].getRowIndex();
            Object newValue = e.getChangeSet()[0].getNewValue();
            if (rowIndex >= 0) {
                if (FndGlobal.IsNotNull(newValue)) {
                    DynamicObject dyCate = (DynamicObject)newValue;
                    ILocaleString cateName = dyCate.getLocaleString("name");
                    String localeValueEn = cateName.getLocaleValue_en();
                    String[] split = localeValueEn.split(",");
                    int count = 3;
                    for (int i = 0; i < count; i++) {
                        if (split.length <= i) {
                            getModel().setValue("aos_cate" + (i + 1), "", rowIndex);
                        } else {
                            getModel().setValue("aos_cate" + (i + 1), split[i], rowIndex);
                        }
                    }
                } else {
                    getModel().setValue("aos_cate1", "", rowIndex);
                    getModel().setValue("aos_cate2", "", rowIndex);
                    getModel().setValue("aos_cate3", "", rowIndex);
                }
            }

        }
    }
}
