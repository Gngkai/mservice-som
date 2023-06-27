package mkt.base.fbaitem;

import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.logging.Log;
import kd.bos.logging.LogFactory;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.bos.threads.ThreadPools;
import sal.sche.aos_sal_sche_pub.aos_sal_sche_pvt;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author create by ?
 * @date 2022/9/19 17:26
 * @action
 */
public class SyncFbaItemTask extends AbstractTask {
    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        ThreadPools.executeOnce("FBA每日物料同步",new SyncRunnable());
    }
    static class SyncRunnable implements Runnable{
        Log log;    //服务器
        Logger utillog; //本地
        SyncRunnable(){
           log = LogFactory.getLog("SyncFbaItemTask");
           utillog = aos_sal_sche_pvt.get_logger("SyncFbaItemTask");
        }
        @Override
        public void run() {
            try {
                SyncDataToFabItem();
                log.info("同步数据完成");
                utillog.info("同步数据完成");
            }catch (Exception e){
                StringWriter writer = new StringWriter();
                e.printStackTrace(new PrintWriter(writer));
                log.info(writer.toString());
                utillog.info(writer.toString());
            }
        }
        public static void SyncDataToFabItem(){
            DeleteServiceHelper.delete("aos_base_fbaitem",null);
            StringJoiner selectFields = new StringJoiner(",");
            selectFields.add("aos_orgid");
            selectFields.add("aos_item_code aos_itemid");
            selectFields.add("aos_item_code.number number");
            selectFields.add("aos_shopsku");
            DynamicObjectCollection dyc_invPrice = QueryServiceHelper.query("aos_sync_invprice", selectFields.toString(), null);
            List<String> list_fields = aos_sal_sche_pvt.getDynamicObjectType(dyc_invPrice.getDynamicObjectType());
            list_fields = list_fields.stream().filter(field->!field.equals("number")).collect(Collectors.toList());
            List<DynamicObject> list_save = new ArrayList<>(5000);
            for (DynamicObject dy : dyc_invPrice) {
                if (!dy.getString("aos_shopsku").equals(dy.getString("number"))){
                    DynamicObject dy_new  = BusinessDataServiceHelper.newDynamicObject("aos_base_fbaitem");
                    for (String field : list_fields) {
                        dy_new.set(field,dy.get(field));
                    }
                    list_save.add(dy_new);
                    if (list_save.size()>4500) {
                        SaveServiceHelper.save(list_save.toArray(new DynamicObject[list_save.size()]));
                        list_save.clear();
                    }
                }
            }
            SaveServiceHelper.save(list_save.toArray(new DynamicObject[list_save.size()]));
        }
    }
}
