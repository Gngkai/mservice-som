package mkt.synciface;

import common.Cux_Common_Utl;
import common.sal.util.SaveUtils;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import common.sal.util.QFBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * @author create by gk
 * @since 2022/12/28 16:10
 * @version 判断合同状态的定时任务-调度任务类
 */
public class AosMktSyncContractStatusTask extends AbstractTask {
    public final static String AOS_PONUMBER = "aos_ponumber";
    public final static String AOS_ITEMID = "aos_itemid";

    /**
     * 查找来自拍照需求表的设计需求表
     **/
    private static DynamicObject[] findDesignReq() {
        QFilter filterType = new QFilter("aos_sourcetype", "=", "PHOTO");
        QFilter filterStatus = new QFilter("aos_status", "!=", "结束");
        QFilter filterContract = new QFilter("aos_contract", "!=", "B");
        QFilter[] qfs = new QFilter[] {filterContract, filterStatus, filterType};
        return BusinessDataServiceHelper.load("aos_mkt_designreq", "id,aos_sourceid,aos_contract", qfs, null);
    }

    /** 判断合同是否取消 **/
    private static boolean judgeContractStatus(Object sourceId) {
        QFilter filterId = new QFilter("id", "=", sourceId);
        StringJoiner str = new StringJoiner(",");
        str.add("aos_ponumber");
        str.add("aos_itemid");
        DynamicObject dy = QueryServiceHelper.queryOne("aos_mkt_photoreq", str.toString(), new QFilter[] {filterId});
        if (dy == null || dy.get(AOS_PONUMBER) == null || dy.get(AOS_ITEMID) == null) {
            return false;
        }
        QFBuilder builder = new QFBuilder();
        builder.add("billno", "=", dy.get("aos_ponumber"));
        builder.add("aos_entryentity.aos_materiel", "=", dy.get("aos_itemid"));
        str = new StringJoiner(",");
        str.add("aos_entryentity.aos_contractnum aos_contractnum");
        str.add("aos_entryentity.aos_cancelqua aos_cancelqua");
        DynamicObjectCollection dycPur = QueryServiceHelper.query("aos_purcontract", str.toString(), builder.toArray());
        for (DynamicObject dyPur : dycPur) {
            int contractQty = 0, cancelQty = 0;
            if (!Cux_Common_Utl.IsNull(dyPur.get("aos_contractnum"))) {
                contractQty = dyPur.getInt("aos_contractnum");
            }
            if (!Cux_Common_Utl.IsNull(dyPur.get("aos_cancelqua"))) {
                cancelQty = dyPur.getInt("aos_cancelqua");
            }
            if (contractQty > cancelQty) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        DynamicObject[] designReqs = findDesignReq();
        List<DynamicObject> listUpdate = new ArrayList<>(5000);
        for (DynamicObject dyDesignReq : designReqs) {
            if (dyDesignReq != null && dyDesignReq.get("aos_sourceid") != null) {
                // 合同被取消
                if (judgeContractStatus(dyDesignReq.get("aos_sourceid"))) {
                    dyDesignReq.set("aos_contract", "B");
                    listUpdate.add(dyDesignReq);
                    SaveUtils.UpdateEntity(listUpdate, false);
                }
            }
        }
        SaveUtils.UpdateEntity(listUpdate, true);
    }
}
