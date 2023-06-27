package mkt.synciface;

import common.Cux_Common_Utl;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * @author create by gk
 * @date 2022/11/28 16:52
 * @action  将每日价格中存在，平台上架信息中不存在的数据同步到每日价格中
 * 以 国别，货号，店铺货号，渠道为amazon 为唯一区分点
 */
public class aos_mkt_invCtlInfo extends AbstractTask {
    private static final String aos_sync_inv_ctl = "aos_sync_inv_ctl";
    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        syncInfo();
    }
    /** 同步信息 **/
    private static void syncInfo(){
        List<String> lits_plat = QueryPlatformInfo();
        DynamicObjectCollection dyc_InvPrice = QueryInvPriceInfo();
        List<DynamicObject> list_save = new ArrayList<>(5000);
        for (DynamicObject dy_price : dyc_InvPrice) {
            if (Existence(lits_plat,dy_price)){
                CreateInvEntity(list_save,dy_price);
            }
        }
        mkt.common.MKTCom.EntitySave(list_save,true);
    }
    /** 获取平台上架信息中的所有数据 **/
    private static List<String> QueryPlatformInfo(){
        StringJoiner str = new StringJoiner(",");
        str.add("aos_ou");
        str.add("aos_item");
        str.add("aos_productid");
        return QueryServiceHelper.query(aos_sync_inv_ctl, str.toString(), null)
                .stream()
                .map(dy -> dy.getString("aos_ou") + "/" + dy.getString("aos_item") + "/" + dy.getString("aos_productid"))
                .distinct()
                .collect(Collectors.toList());
    }
    /**获取每日价格的所有信息 **/
    private static DynamicObjectCollection QueryInvPriceInfo(){
        StringJoiner str = new StringJoiner(",");
        str.add("aos_orgid"); //国别
        str.add("aos_orgid.number orgNumber");
        str.add("aos_platformfid");    //平台
        str.add("aos_platformfid.number platNumber");
        str.add("aos_shopfid");         //店铺
        str.add("aos_shopfid.number shopNumber");
        str.add("aos_item_code");      //物料
        str.add("aos_item_code.number itemNumber");
        str.add("aos_shopsku");     //店铺货号
        //获取亚马逊平台id
        QFilter filter_number = new QFilter("number","=","AMAZON");
        List<Object> list_channelId = QueryServiceHelper.queryPrimaryKeys("aos_sal_channel", new QFilter[]{filter_number}, null, 1);
        QFilter filter_channel = new QFilter("aos_platformfid","=",list_channelId.get(0));
        return QueryServiceHelper.query("aos_sync_invprice", str.toString(), new QFilter[]{filter_channel});
    }
    /**判断平台信息中是否存在 **/
    private static boolean Existence(List<String> lits_plat,DynamicObject dy_price){
        String aos_orgid = dy_price.getString("aos_orgid");
        if (Cux_Common_Utl.IsNull(aos_orgid))
            return false;
        String aos_item_code = dy_price.getString("aos_item_code");
        if (Cux_Common_Utl.IsNull(aos_item_code))
            return false;
        String aos_shopsku = dy_price.getString("aos_shopsku");
        StringJoiner str = new StringJoiner("/");
        str.add(aos_orgid);
        str.add(aos_item_code);
        str.add(aos_shopsku);
        return !lits_plat.contains(str.toString());
    }
    /**创建单据 **/
    private static void CreateInvEntity(List<DynamicObject> list_save,DynamicObject dy_price){
        DynamicObject dy_inv = BusinessDataServiceHelper.newDynamicObject(aos_sync_inv_ctl);
        dy_inv.set("aos_ou",dy_price.get("aos_orgid"));
        dy_inv.set("aos_ou_code",dy_price.get("orgNumber"));
        dy_inv.set("aos_platform_g",dy_price.get("aos_platformfid"));
        dy_inv.set("aos_platform",dy_price.get("platNumber"));
        dy_inv.set("aos_shop",dy_price.get("aos_shopfid"));
        dy_inv.set("aos_shopname",dy_price.get("shopNumber"));
        dy_inv.set("aos_item",dy_price.get("aos_item_code"));
        dy_inv.set("aos_item_code",dy_price.get("itemNumber"));
        dy_inv.set("aos_productid",dy_price.get("aos_shopsku"));
        dy_inv.set("billstatus","C");
        list_save.add(dy_inv);
        mkt.common.MKTCom.EntitySave(list_save,false);
    }
}
