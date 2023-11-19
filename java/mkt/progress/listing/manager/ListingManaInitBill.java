package mkt.progress.listing.manager;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;

/**
 * @author aosom
 * 数字资产管理表单插件
 */
public class ListingManaInitBill extends AbstractBillPlugIn {
    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();
        if (name.contains("aos_point")) {
            int total = 0;
            for (int i = 1; i <= 12; i++) {
                total = total + (Integer) this.getModel().getValue("aos_point" + i, 0);
            }
            this.getModel().setValue("aos_total", total, 0);
        }
    }
}
