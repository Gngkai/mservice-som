package mkt.progress.synthesis.gift;

import common.sal.util.QFBuilder;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * @author: GK
 * @create: 2024-01-26 11:07
 * @Description:
 */
@SuppressWarnings("unused")
public class gifrTask extends AbstractTask {
    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        //查找订单好为空的数据
        QFBuilder builder = new QFBuilder();
        builder.add("entryentity.aos_feeno","=","");
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_gift", "id", builder.toArray());
        List<String> list = new ArrayList<>(dyc.size());
        for (DynamicObject dy : dyc) {
            list.add(dy.getString("id"));
        }
        StringJoiner str = new StringJoiner(",");
        str.add("aos_org");
        str.add("aos_address");
        str.add("entryentity.aos_item");
        str.add("entryentity.aos_channel");
        str.add("entryentity.aos_shop");
        str.add("entryentity.aos_order");
        str.add("entryentity.aos_feeno");
        DynamicObject[] load = BusinessDataServiceHelper.load("aos_mkt_gift", str.toString(), new QFilter[]{new QFilter("id", "in", list.toArray())});
        giftOpPlugin.setFeeInfo(load);
        SaveServiceHelper.save(load);
    }
}
