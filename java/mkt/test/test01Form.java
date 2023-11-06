package mkt.test;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.entity.datamodel.IDataModel;
import kd.bos.form.IFormView;
import kd.bos.form.control.events.ItemClickEvent;

import java.util.EventObject;

/**
 * @author create by ?
 * @date 2023/3/22 16:12
 * @action
 */
public class test01Form extends AbstractBillPlugIn {
    @Override
    public void afterBindData(EventObject e) {
        super.afterBindData(e);
        this.getModel().setValue("aos_textfield","a");
        this.getModel().getValue("");
    }

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        this.addItemClickListeners("aos_baritemap");
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        if (evt.getItemKey().equals("")){}
    }
}
