package mkt.test;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.entity.botp.plugin.args.AfterConvertEventArgs;

import java.math.BigDecimal;
import java.util.EventObject;

public class demo11 extends AbstractBillPlugIn {
    public void afterCreateNewData(EventObject e) {
        Integer a = (Integer) this.getModel().getValue("aos_integerfield");
        Integer b = (Integer) this.getModel().getValue("aos_integerfield3");
        if (a > 0 && b > 0){
            int r = (int)(a + b);
            this.getModel().setValue("aos_integerfield2", r);
        }
        else System.out.println("输入数字格式有误");
    }
}
