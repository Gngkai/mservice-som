package mkt.data.point;

import common.fnd.FndGlobal;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.FormShowParameter;
import kd.bos.form.control.Control;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.drp.pos.common.util.StringJoin;


import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

/**
 * @author: GK
 * @create: 2023-10-25 14:18
 * @Description:
 */
public class productKeyCopyForm extends AbstractFormPlugin  {
    @Override
    public void afterBindData(EventObject e) {
        super.afterBindData(e);
    }

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        this.addClickListeners(new String[]{"aos_rule","aos_priceformula","aos_name"});

    }

    @Override
    public void click(EventObject evt) {
        super.click(evt);
        Control control= (Control) evt.getSource();
        String key= control.getKey();
        if (key.equals("aos_name")){
            FormShowParameter showParameter = FndGlobal.CraeteForm(this, "aos_mkt_point_cate", "copyTo", null);
            showParameter.setCustomParam("org","1000002");
            showParameter.setCaption("选择品类");
            getView().showForm(showParameter);
        }
    }

    @Override
    public void closedCallBack(ClosedCallBackEvent event) {
        super.closedCallBack(event);
        String actionId = event.getActionId();
        Object redata =  event.getReturnData();
        if (redata==null)
            return;
        if (actionId.equals("copyTo")){
            List<DynamicObject> data = (List<DynamicObject>) redata;
            DynamicObjectCollection aos_ent = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_ent");
            for (DynamicObject row : data) {
                DynamicObject aos_item = row.getDynamicObject("aos_item");
                if (aos_item == null)
                    continue;
                DynamicObject aos_cate = row.getDynamicObject("aos_cate");
                if (aos_cate==null) {
                    continue;
                }
                DynamicObject newRow = aos_ent.addNew();
                newRow.set("aos_item",aos_item.getLocaleString("name").getLocaleValue_zh_CN());
                newRow.set("aos_cate",aos_cate.getLocaleString("name").getLocaleValue_zh_CN());
            }
            getView().updateView("aos_ent");
        }
    }
}
