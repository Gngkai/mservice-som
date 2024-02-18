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
 * @since 2022/11/28 16:52
 * @version 将每日价格中存在，平台上架信息中不存在的数据同步到每日价格中 以 国别，货号，店铺货号，渠道为amazon 为唯一区分点-调度任务类
 */
public class AosMktInvCtlInfoTask extends AbstractTask {
    private static final String AOS_SYNC_INV_CTL = "aos_sync_inv_ctl";

    /** 同步信息 **/
    private static void syncInfo() {
        List<String> litsPlat = queryPlatformInfo();
        DynamicObjectCollection dycInvPrice = queryInvPriceInfo();
        List<DynamicObject> listSave = new ArrayList<>(5000);
        for (DynamicObject dyPrice : dycInvPrice) {
            if (existence(litsPlat, dyPrice)) {
                createInvEntity(listSave, dyPrice);
            }
        }
        mkt.common.MktComUtil.entitySave(listSave, true);
    }

    /** 获取平台上架信息中的所有数据 **/
    private static List<String> queryPlatformInfo() {
        StringJoiner str = new StringJoiner(",");
        str.add("aos_ou");
        str.add("aos_item");
        str.add("aos_productid");
        return QueryServiceHelper.query(AOS_SYNC_INV_CTL, str.toString(), null).stream()
            .map(dy -> dy.getString("aos_ou") + "/" + dy.getString("aos_item") + "/" + dy.getString("aos_productid"))
            .distinct().collect(Collectors.toList());
    }

    /** 获取每日价格的所有信息 **/
    private static DynamicObjectCollection queryInvPriceInfo() {
        StringJoiner str = new StringJoiner(",");
        // 国别
        str.add("aos_orgid");
        str.add("aos_orgid.number orgNumber");
        // 平台
        str.add("aos_platformfid");
        str.add("aos_platformfid.number platNumber");
        // 店铺
        str.add("aos_shopfid");
        str.add("aos_shopfid.number shopNumber");
        // 物料
        str.add("aos_item_code");
        str.add("aos_item_code.number itemNumber");
        // 店铺货号
        str.add("aos_shopsku");
        // 获取亚马逊平台id
        QFilter filterNumber = new QFilter("number", "=", "AMAZON");
        List<Object> listChannelId =
            QueryServiceHelper.queryPrimaryKeys("aos_sal_channel", new QFilter[] {filterNumber}, null, 1);
        QFilter filterChannel = new QFilter("aos_platformfid", "=", listChannelId.get(0));
        return QueryServiceHelper.query("aos_sync_invprice", str.toString(), new QFilter[] {filterChannel});
    }

    /** 判断平台信息中是否存在 **/
    private static boolean existence(List<String> litsPlat, DynamicObject dyPrice) {
        String aosOrgid = dyPrice.getString("aos_orgid");
        if (Cux_Common_Utl.IsNull(aosOrgid)) {
            return false;
        }
        String aosItemCode = dyPrice.getString("aos_item_code");
        if (Cux_Common_Utl.IsNull(aosItemCode)) {
            return false;
        }
        String aosShopsku = dyPrice.getString("aos_shopsku");
        StringJoiner str = new StringJoiner("/");
        str.add(aosOrgid);
        str.add(aosItemCode);
        str.add(aosShopsku);
        return !litsPlat.contains(str.toString());
    }

    /** 创建单据 **/
    private static void createInvEntity(List<DynamicObject> listSave, DynamicObject dyPrice) {
        DynamicObject dyInv = BusinessDataServiceHelper.newDynamicObject(AOS_SYNC_INV_CTL);
        dyInv.set("aos_ou", dyPrice.get("aos_orgid"));
        dyInv.set("aos_ou_code", dyPrice.get("orgNumber"));
        dyInv.set("aos_platform_g", dyPrice.get("aos_platformfid"));
        dyInv.set("aos_platform", dyPrice.get("platNumber"));
        dyInv.set("aos_shop", dyPrice.get("aos_shopfid"));
        dyInv.set("aos_shopname", dyPrice.get("shopNumber"));
        dyInv.set("aos_item", dyPrice.get("aos_item_code"));
        dyInv.set("aos_item_code", dyPrice.get("itemNumber"));
        dyInv.set("aos_productid", dyPrice.get("aos_shopsku"));
        dyInv.set("billstatus", "C");
        listSave.add(dyInv);
        mkt.common.MktComUtil.entitySave(listSave, false);
    }

    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        syncInfo();
    }
}
