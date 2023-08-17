package mkt.act.basedata.actType;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.form.field.BasedataEdit;
import kd.bos.form.field.events.BeforeF7SelectEvent;
import kd.bos.form.field.events.BeforeF7SelectListener;
import kd.bos.list.ListShowParameter;
import kd.bos.orm.query.QFilter;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

/**
 * @author create by gk
 * @date 2023/8/17 16:02
 * @action 活动库界面插件
 */
public class actTypeForm extends AbstractBillPlugIn implements BeforeF7SelectListener {
    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        BasedataEdit aos_shop = this.getControl("aos_shop");
        aos_shop.addBeforeF7SelectListener(this);
    }

    @Override
    public void beforeF7Select(BeforeF7SelectEvent beforeF7SelectEvent) {
        ListShowParameter showParameter = (ListShowParameter) beforeF7SelectEvent.getFormShowParameter();
        String F7name = beforeF7SelectEvent.getProperty().getName();
        if (F7name.equals("aos_shop")){
            List<QFilter> filters = new ArrayList<>();
            if (getModel().getValue("aos_org")!=null){
                DynamicObject org = (DynamicObject) getModel().getValue("aos_org");
                QFilter filter = new QFilter("aos_org","=",org.getString("id"));
                filters.add(filter);
            }
            if (getModel().getValue("aos_channel")!=null){
                DynamicObject channel = (DynamicObject) getModel().getValue("aos_channel");
                QFilter filter = new QFilter("aos_channel","=",channel.getString("id"));
                filters.add(filter);
            }
            showParameter.getListFilterParameter().setQFilters(filters);
        }
    }
}
