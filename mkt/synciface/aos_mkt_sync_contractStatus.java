package mkt.synciface;

import common.Cux_Common_Utl;
import common.sal.util.SalUtil;
import common.sal.util.SaveUtils;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.fi.bd.util.QFBuilder;
import mkt.common.MKTCom;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * @author create by gk
 * @date 2022/12/28 16:10
 * @action  设计需求表同步合同状态
 */
public class aos_mkt_sync_contractStatus extends AbstractTask {
    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        DynamicObject[] designReqs = findDesignReq();
        List<DynamicObject> list_update = new ArrayList<>(5000);
        for (DynamicObject dy_designReq : designReqs) {
            if (dy_designReq!=null && dy_designReq.get("aos_sourceid")!=null){
                //合同被取消
                if (JudgeContractStatus(dy_designReq.get("aos_sourceid"))){
                    dy_designReq.set("aos_contract","B");
                    list_update.add(dy_designReq);
                    SaveUtils.UpdateEntity(list_update,false);
                }
            }
        }
        SaveUtils.UpdateEntity(list_update,true);
    }
    /** 查找来自拍照需求表的设计需求表
     * @return**/
    private static DynamicObject[] findDesignReq(){
        QFilter filter_type = new QFilter("aos_sourcetype","=","PHOTO");
        QFilter filter_status = new QFilter("aos_status","!=","结束");
        QFilter filter_contract = new QFilter("aos_contract","!=","B");
        QFilter [] qfs = new QFilter[]{filter_contract,filter_status,filter_type};
        return BusinessDataServiceHelper.load("aos_mkt_designreq", "id,aos_sourceid,aos_contract", qfs, null);
    }
    /** 判断合同是否取消 **/
    private static boolean JudgeContractStatus(Object sourceID){
        QFilter filter_id = new QFilter("id","=",sourceID);
        StringJoiner str = new StringJoiner(",");
        str.add("aos_ponumber");
        str.add("aos_itemid");
        DynamicObject dy = QueryServiceHelper.queryOne("aos_mkt_photoreq", str.toString(), new QFilter[]{filter_id});
        if (dy==null || dy.get("aos_ponumber")==null || dy.get("aos_itemid")==null)
            return false;
        QFBuilder builder = new QFBuilder();
        builder.add("billno","=",dy.get("aos_ponumber"));
        builder.add("aos_entryentity.aos_materiel","=",dy.get("aos_itemid"));
        str = new StringJoiner(",");
        str.add("aos_entryentity.aos_contractnum aos_contractnum");
        str.add("aos_entryentity.aos_cancelqua aos_cancelqua");
        DynamicObjectCollection dyc_pur = QueryServiceHelper.query("aos_purcontract", str.toString(), builder.toArray());
        for (DynamicObject dy_pur : dyc_pur) {
            int contractQty = 0,cancelQty = 0;
            if (!Cux_Common_Utl.IsNull( dy_pur.get("aos_contractnum")))
                contractQty = dy_pur.getInt("aos_contractnum");
            if (!Cux_Common_Utl.IsNull(dy_pur.get("aos_cancelqua"))) {
                cancelQty = dy_pur.getInt("aos_cancelqua");
            }
            if (contractQty>cancelQty)
                return false;
        }
        return true;
    }
}
