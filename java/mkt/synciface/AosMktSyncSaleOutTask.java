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

/**
 * @author aosom
 * @version 营销工作流物料爆品刷新接口-调度任务类
 * @deprecated 未启用
 */
public class AosMktSyncSaleOutTask extends AbstractTask {
    public static void doOperate() {
        Set<String> invSaleOutSet = InvData.genInvSaleOut();
        // 查询所有未关闭的拍照需求表
        DynamicObject[] aosMktPhotoS = BusinessDataServiceHelper.load("aos_mkt_photoreq", "aos_itemid,aos_is_saleout",
            new QFilter("aos_status", QCP.not_in, new String[] {"已完成", "不需拍"}).toArray());
        for (DynamicObject aosMktPhoto : aosMktPhotoS) {
            DynamicObject aosItemid = aosMktPhoto.getDynamicObject("aos_itemid");
            aosMktPhoto.set("aos_is_saleout", invSaleOutSet.contains(aosItemid.getPkValue().toString()));
        }
        SaveServiceHelper.save(aosMktPhotoS);
        // 查询所有未关闭的设计需求表
        DynamicObject[] aosMktDesignReqS = BusinessDataServiceHelper.load("aos_mkt_designreq",
            "aos_entryentity.aos_itemid,aos_entryentity.aos_is_saleout,billno",
            new QFilter("aos_status", QCP.not_equals, "结束").toArray());
        for (DynamicObject aosMktDesignReq : aosMktDesignReqS) {
            DynamicObjectCollection aosEntryentityS = aosMktDesignReq.getDynamicObjectCollection("aos_entryentity");
            for (DynamicObject aosEntryentity : aosEntryentityS) {
                DynamicObject aosItemid = aosEntryentity.getDynamicObject("aos_itemid");
                if (FndGlobal.IsNull(aosItemid)) {
                    continue;
                }
                aosEntryentity.set("aos_is_saleout", invSaleOutSet.contains(aosItemid.getPkValue().toString()));
            }
        }
        SaveServiceHelper.save(aosMktDesignReqS);
        FndMsg.debug("=================查询所有未关闭的3D产品设计单====================");
        DynamicObject[] aosMkt3DesignS = BusinessDataServiceHelper.load("aos_mkt_3design",
            "aos_entryentity.aos_itemid,aos_entryentity.aos_is_saleout",
            new QFilter("aos_status", QCP.not_equals, "已完成").toArray());
        for (DynamicObject aosMkt3Design : aosMkt3DesignS) {
            DynamicObjectCollection aosEntryentityS = aosMkt3Design.getDynamicObjectCollection("aos_entryentity");
            for (DynamicObject aosEntryentity : aosEntryentityS) {
                DynamicObject aosItemid = aosEntryentity.getDynamicObject("aos_itemid");
                if (FndGlobal.IsNull(aosItemid)) {
                    continue;
                }
                aosEntryentity.set("aos_is_saleout", invSaleOutSet.contains(aosItemid.getPkValue().toString()));
            }
        }
        SaveServiceHelper.save(aosMkt3DesignS);
        // 查询所有未关闭的Listing优化需求表-文案
        DynamicObject[] aosMktListingSonS = BusinessDataServiceHelper.load("aos_mkt_listing_son",
            "aos_entryentity.aos_itemid,aos_entryentity.aos_is_saleout",
            new QFilter("aos_status", QCP.not_equals, "结束").toArray());
        for (DynamicObject aosMktListingSon : aosMktListingSonS) {
            DynamicObjectCollection aosEntryentityS = aosMktListingSon.getDynamicObjectCollection("aos_entryentity");
            for (DynamicObject aosEntryentity : aosEntryentityS) {
                DynamicObject aosItemid = aosEntryentity.getDynamicObject("aos_itemid");
                if (FndGlobal.IsNull(aosItemid)) {
                    continue;
                }
                aosEntryentity.set("aos_is_saleout", invSaleOutSet.contains(aosItemid.getPkValue().toString()));
            }
        }
        SaveServiceHelper.save(aosMktListingSonS);
        // 查询所有未关闭的Listing优化需求表-小语种文案
        DynamicObject[] aosMktListingMinS = BusinessDataServiceHelper.load("aos_mkt_listing_min",
            "aos_entryentity.aos_itemid,aos_entryentity.aos_is_saleout",
            new QFilter("aos_status", QCP.not_equals, "结束").toArray());
        for (DynamicObject aosMktListingMin : aosMktListingMinS) {
            DynamicObjectCollection aosEntryentityS = aosMktListingMin.getDynamicObjectCollection("aos_entryentity");
            for (DynamicObject aosEntryentity : aosEntryentityS) {
                DynamicObject aosItemid = aosEntryentity.getDynamicObject("aos_itemid");
                if (FndGlobal.IsNull(aosItemid)) {
                    continue;
                }
                aosEntryentity.set("aos_is_saleout", invSaleOutSet.contains(aosItemid.getPkValue().toString()));
            }
        }
        SaveServiceHelper.save(aosMktListingMinS);
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        doOperate();
    }
}
