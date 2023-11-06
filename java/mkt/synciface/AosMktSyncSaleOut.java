package mkt.synciface;

import common.fnd.FndGlobal;
import common.fnd.FndMsg;
import common.fnd.data.InvData;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.dataentity.entity.DynamicObjectCollection;

import java.util.Map;
import java.util.Set;

public class AosMktSyncSaleOut extends AbstractTask {
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        do_operate();
    }

    public static void do_operate() {
        FndMsg.debug("=================AosMktSyncSaleOut====================");
        Set<String> invSaleOutSet = InvData.genInvSaleOut();
        FndMsg.debug("invSaleOutSet.size:"+invSaleOutSet.size());
        FndMsg.debug("=================查询所有未关闭的拍照需求表====================");
        DynamicObject[] aosMktPhotoS = BusinessDataServiceHelper.load("aos_mkt_photoreq",
                "aos_itemid,aos_is_saleout",
                new QFilter("aos_status", QCP.not_in, new String[]{"已完成", "不需拍"})
                        .toArray());
        for (DynamicObject aosMktPhoto : aosMktPhotoS) {
            DynamicObject aos_itemid = aosMktPhoto.getDynamicObject("aos_itemid");
            if (invSaleOutSet.contains(aos_itemid.getPkValue().toString()))
                aosMktPhoto.set("aos_is_saleout", true);
            else
                aosMktPhoto.set("aos_is_saleout", false);
        }
        SaveServiceHelper.save(aosMktPhotoS);
        FndMsg.debug("=================查询所有未关闭的设计需求表====================");
        DynamicObject[] aosMktDesignReqS = BusinessDataServiceHelper.load("aos_mkt_designreq",
                "aos_entryentity.aos_itemid,aos_entryentity.aos_is_saleout,billno",
                new QFilter("aos_status", QCP.not_equals, "结束")
                        .toArray());
        for (DynamicObject aosMktDesignReq : aosMktDesignReqS) {
            DynamicObjectCollection aos_entryentityS = aosMktDesignReq.getDynamicObjectCollection("aos_entryentity");
            for (DynamicObject aos_entryentity : aos_entryentityS) {
                DynamicObject aos_itemid = aos_entryentity.getDynamicObject("aos_itemid");
                if (FndGlobal.IsNull(aos_itemid))
                    continue;
                if (invSaleOutSet.contains(aos_itemid.getPkValue().toString()))
                    aos_entryentity.set("aos_is_saleout", true);
                else
                    aos_entryentity.set("aos_is_saleout", false);
            }
        }
        SaveServiceHelper.save(aosMktDesignReqS);
        FndMsg.debug("=================查询所有未关闭的3D产品设计单====================");
        DynamicObject[] aosMkt3DesignS = BusinessDataServiceHelper.load("aos_mkt_3design",
                "aos_entryentity.aos_itemid,aos_entryentity.aos_is_saleout",
                new QFilter("aos_status", QCP.not_equals, "已完成")
                        .toArray());
        for (DynamicObject aosMkt3Design : aosMkt3DesignS) {
            DynamicObjectCollection aos_entryentityS = aosMkt3Design.getDynamicObjectCollection("aos_entryentity");
            for (DynamicObject aos_entryentity : aos_entryentityS) {
                DynamicObject aos_itemid = aos_entryentity.getDynamicObject("aos_itemid");
                if (FndGlobal.IsNull(aos_itemid))
                    continue;
                if (invSaleOutSet.contains(aos_itemid.getPkValue().toString()))
                    aos_entryentity.set("aos_is_saleout", true);
                else
                    aos_entryentity.set("aos_is_saleout", false);
            }
        }
        SaveServiceHelper.save(aosMkt3DesignS);
        FndMsg.debug("=================查询所有未关闭的Listing优化需求表-文案====================");
        DynamicObject[] aosMktListingSonS = BusinessDataServiceHelper.load("aos_mkt_listing_son",
                "aos_entryentity.aos_itemid,aos_entryentity.aos_is_saleout",
                new QFilter("aos_status", QCP.not_equals, "结束")
                        .toArray());
        for (DynamicObject aosMktListingSon : aosMktListingSonS) {
            DynamicObjectCollection aos_entryentityS = aosMktListingSon.getDynamicObjectCollection("aos_entryentity");
            for (DynamicObject aos_entryentity : aos_entryentityS) {
                DynamicObject aos_itemid = aos_entryentity.getDynamicObject("aos_itemid");
                if (FndGlobal.IsNull(aos_itemid))
                    continue;
                if (invSaleOutSet.contains(aos_itemid.getPkValue().toString()))
                    aos_entryentity.set("aos_is_saleout", true);
                else
                    aos_entryentity.set("aos_is_saleout", false);
            }
        }
        SaveServiceHelper.save(aosMktListingSonS);
        FndMsg.debug("=================查询所有未关闭的Listing优化需求表-小语种文案====================");
        DynamicObject[] aosMktListingMinS = BusinessDataServiceHelper.load("aos_mkt_listing_min",
                "aos_entryentity.aos_itemid,aos_entryentity.aos_is_saleout",
                new QFilter("aos_status", QCP.not_equals, "结束")
                        .toArray());
        for (DynamicObject aosMktListingMin : aosMktListingMinS) {
            DynamicObjectCollection aos_entryentityS = aosMktListingMin.getDynamicObjectCollection("aos_entryentity");

            for (DynamicObject aos_entryentity : aos_entryentityS) {
                DynamicObject aos_itemid = aos_entryentity.getDynamicObject("aos_itemid");
                if (FndGlobal.IsNull(aos_itemid))
                    continue;
                if (invSaleOutSet.contains(aos_itemid.getPkValue().toString()))
                    aos_entryentity.set("aos_is_saleout", true);
                else
                    aos_entryentity.set("aos_is_saleout", false);
            }
        }
        SaveServiceHelper.save(aosMktListingMinS);
    }
}
