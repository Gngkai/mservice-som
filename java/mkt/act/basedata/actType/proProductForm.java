package mkt.act.basedata.actType;

import kd.bos.bill.AbstractBillPlugIn;

import java.util.EventObject;

/**
 * @author: GK
 * @create: 2023-12-21 15:09
 * @Description:  周销售推荐产品清单
 */
public class proProductForm extends AbstractBillPlugIn {


    @Override
    public void afterBindData(EventObject e) {
        super.afterBindData(e);
        //初始话设置单据行上的物料状态 和 季节属性

    }

    private void init(){
        //设置组别


        Object aos_org = this.getModel().getValue("aos_org");
        if (aos_org == null )
            return;



    }
}
