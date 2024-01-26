package mkt.progress.parameter.errorlisting;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * @author create by gk
 * @version 改错任务清单
 */
public class ErrorListEntity {
    private static final String ENTITYNAME = "aos_mkt_listing_err";
    public static List<String> errorCountries = Arrays.asList("CA", "US", "UK");
    public static List<String> errorListType = Collections.singletonList("四者一致");
    private final String orgid;
    private final String itemid;
    private final String type;
    private final String billno;

    public ErrorListEntity(String billno, String type, String orgid, String itemid) {
        this.orgid = orgid;
        this.itemid = itemid;
        this.type = type;
        this.billno = billno;
    }

    public void save() {
        DynamicObject dy = BusinessDataServiceHelper.newDynamicObject(ENTITYNAME);
        dy.set("aos_date", new Date());
        dy.set("aos_org", orgid);
        dy.set("aos_item", itemid);
        dy.set("aos_type", type);
        dy.set("billno", billno);
        dy.set("billstatus", "A");
        SaveServiceHelper.save(new DynamicObject[] {dy});
    }
}
