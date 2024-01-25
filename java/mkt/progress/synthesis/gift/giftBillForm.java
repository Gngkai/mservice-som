package mkt.progress.synthesis.gift;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.plugin.AbstractFormPlugin;

import java.util.EventObject;

/**
 * @author: GK
 * @create: 2024-01-24 17:38
 * @Description:赠品流程界面插件
 */
@SuppressWarnings("unused")
public class giftBillForm extends AbstractFormPlugin {
    @Override
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        setUserNerve();
    }

    /**
     * 新建时，设置组织
     */
    private void setUserNerve(){
        DynamicObject creatorEntry = (DynamicObject) getModel().getValue("creator");
        DynamicObjectCollection sectorEntryRows = creatorEntry.getDynamicObjectCollection("entryentity");
        if (sectorEntryRows.size()>0){
            DynamicObject dpt = sectorEntryRows.get(0).getDynamicObject("dpt");
            this.getModel().setValue("aos_nerve",dpt.getPkValue());
        }
    }

}
