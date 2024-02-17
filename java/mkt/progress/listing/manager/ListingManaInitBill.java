package mkt.progress.listing.manager;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import java.util.EventObject;

/**
 * @author aosom 数字资产管理表单插件
 */
public class ListingManaInitBill extends AbstractBillPlugIn {
    public final static String AOS_POINT = "aos_point";

    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();
        if (name.contains(AOS_POINT)) {
            pointChanged();
        }
    }

    private void pointChanged() {
        int total = 0;
        int twelve = 12;
        for (int i = 1; i <= twelve; i++) {
            total = total + (Integer)this.getModel().getValue("aos_point" + i, 0);
        }
        this.getModel().setValue("aos_total", total, 0);
    }

    /**
     * 初始化事件
     **/
    @Override
    public void afterLoadData(EventObject e) {
        super.afterLoadData(e);
        statusControl();
        pointChanged();
    }

    /**
     * 通用状态控制
     */
    private void statusControl() {
        DynamicObject aosOrgDyn = (DynamicObject)this.getModel().getValue("aos_orgid");
        String aosOrgNum = aosOrgDyn.getString("number");

        this.getView().setVisible(false, "aos_us_sale");
        this.getView().setVisible(false, "aos_ca_sale");
        this.getView().setVisible(false, "aos_uk_sale");
        this.getView().setVisible(false, "aos_de_sale");
        this.getView().setVisible(false, "aos_fr_sale");
        this.getView().setVisible(false, "aos_it_sale");
        this.getView().setVisible(false, "aos_es_sale");

        this.getView().setVisible(true, "aos_" + aosOrgNum.toLowerCase() + "_sale");
    }

}
