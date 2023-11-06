package mkt.progress.parameter.errorListing;

import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * @author create by gk
 * @date 2023/6/20 14:26
 * @action  改错任务清单
 */
public class ErrorListEntity {
    public static  List<String> errorCountries = Arrays.asList("CA", "US", "UK");
    public static  List<String> errorListType = Arrays.asList("四者一致");
    private static String entityName = "aos_mkt_listing_err";
    private String orgid,itemid,type,billno;
    public ErrorListEntity(String billno, String type, String orgid, String itemid){
        this.orgid = orgid;
        this.itemid = itemid;
        this.type = type;
        this.billno = billno;
    }
    public void save (){
        DynamicObject dy = BusinessDataServiceHelper.newDynamicObject(entityName);
        dy.set("aos_date",new Date());
        dy.set("aos_org",orgid);
        dy.set("aos_item",itemid);
        dy.set("aos_type",type);
        dy.set("billno",billno);
        dy.set("billstatus","A");
        SaveServiceHelper.save(new DynamicObject[]{dy});
    }
}
