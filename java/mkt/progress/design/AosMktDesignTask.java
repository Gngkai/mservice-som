package mkt.progress.design;

import java.time.LocalDate;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import common.fnd.FndLog;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.OperationServiceHelper;
import mkt.progress.listing.AosMktListingMinBill;

/**
 * @author aosom
 * @version 功能图翻译台账生成 设计需求表 定时任务
 */
public class AosMktDesignTask extends AbstractTask {
    public static void doOperate() {
        Set<String> orignItem = generateOrignItem();
        Set<String> noPhotoItem = generateNoPhotoItem();
        Set<String> endDesignItem = generateEndDesignItem();
        QFilter filter = new QFilter("aos_eng", QCP.equals, "N");
        QFilter[] filters = new QFilter[] {filter};
        DynamicObjectCollection aosMktFuncsumdataS =
            QueryServiceHelper.query("aos_mkt_funcsumdata", "id,aos_itemid,aos_sourceid,aos_orgid", filters);
        FndLog log = FndLog.init("功能图翻译台账生成", LocalDate.now().toString());
        for (DynamicObject aosMktFuncsumdata : aosMktFuncsumdataS) {
            String aosItemid = aosMktFuncsumdata.getString("aos_itemid");
            if (orignItem.contains(aosItemid) || noPhotoItem.contains(aosItemid) || endDesignItem.contains(aosItemid)) {
                // 货号维度生成设计需求表
                boolean exists = QueryServiceHelper.exists("aos_mkt_listing_min",
                    new QFilter[] {new QFilter("id", "=", aosMktFuncsumdata.get("aos_sourceid"))});
                if (!exists) {
                    log.add(aosMktFuncsumdata.getString("id") + "  源单不存在");
                    continue;
                }
                DynamicObject aosMktListingMin =
                    BusinessDataServiceHelper.loadSingle(aosMktFuncsumdata.get("aos_sourceid"), "aos_mkt_listing_min");
                String aosFrombill = AosMktListingMinBill.generateDesign(aosMktListingMin, aosItemid,
                    aosMktFuncsumdata.getString("aos_orgid"));
                // 回写功能图翻译台账数据表
                DynamicObject aosMktFuncsumdataSingle =
                    BusinessDataServiceHelper.loadSingle(aosMktFuncsumdata.get("id"), "aos_mkt_funcsumdata");
                aosMktFuncsumdataSingle.set("aos_eng", "Y");
                aosMktFuncsumdataSingle.set("aos_frombill", aosFrombill);
                aosMktFuncsumdataSingle.set("aos_triggerdate", new Date());
                OperationServiceHelper.executeOperate("save", "aos_mkt_funcsumdata",
                    new DynamicObject[] {aosMktFuncsumdataSingle}, OperateOption.create());
            }
        }
        log.finnalSave();
    }

    /** 设计需求表 期初已完成货号 **/
    private static Set<String> generateOrignItem() {
        DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_designitem", "aos_itemid", null);
        return list.stream().map(obj -> obj.getString("aos_itemid")).collect(Collectors.toSet());
    }

    /** 不拍照任务清单货号 **/
    private static Set<String> generateNoPhotoItem() {
        DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_nophotolist", "aos_itemid", null);
        return list.stream().map(obj -> obj.getString("aos_itemid")).collect(Collectors.toSet());
    }

    /** 已结束的设计需求表 **/
    private static Set<String> generateEndDesignItem() {
        DynamicObjectCollection list = QueryServiceHelper.query("aos_mkt_designreq",
            "aos_entryentity.aos_itemid aos_itemid", new QFilter[] {new QFilter("aos_status", QCP.equals, "结束")});
        return list.stream().map(obj -> obj.getString("aos_itemid")).collect(Collectors.toSet());
    }

    @Override
    public void execute(RequestContext ctx, Map<String, Object> param) throws KDException {
        doOperate();
    }

}