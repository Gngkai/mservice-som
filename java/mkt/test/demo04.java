package mkt.test;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.extplugin.sample.AbstractFormPlugin;
import kd.bos.form.field.LargeTextEdit;

//AbstractBillPlugIn 大文本字段
//public class demo04 extends AbstractFormPlugin {
public class demo04 extends AbstractBillPlugIn {

    private final static String KEY_HEADLARGETEXT = "aos_largetextfield1";

    private void demoFieldValue(){
        LargeTextEdit largeTextEdit = this.getView().getControl(KEY_HEADLARGETEXT);
        String tagPropName = largeTextEdit.getTagFieldKey();

        String largeText = (String) this.getModel().getValue(KEY_HEADLARGETEXT);
        String largeTextTag = (String) this.getModel().getValue(KEY_HEADLARGETEXT);

        this.getModel().setValue(KEY_HEADLARGETEXT, largeText);
        this.getModel().setValue(tagPropName, largeTextTag);
    }
}
