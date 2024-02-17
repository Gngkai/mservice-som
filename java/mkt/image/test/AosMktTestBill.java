package mkt.image.test;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.form.control.events.ItemClickEvent;

/**
 * @author aosom
 * @since 2024/2/1 9:35
 */
public class AosMktTestBill extends AbstractBillPlugIn {

    public final static String AOS_CAL = "aos_cal";

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String control = evt.getItemKey();

        if (AOS_CAL.equals(control)) {

        }

    }

}
