package mkt.test;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

import java.util.Map;
import java.util.StringJoiner;

import static kd.bos.servicehelper.BusinessDataServiceHelper.load;

public class Demo1 extends AbstractTask {
    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        //数据库增、删、改、查
        //查

        QFilter filter_billno = new QFilter("billno","=",003);
        StringJoiner str = new StringJoiner(",");
        str.add("billno");
        str.add("creator");
        str.add("aos_decimalfield");
        str.add("aos_textareafield");
        //str.add("aos_entryentity.aos_enable");
        str.add("aos_entryentity.aos_basedatafield.name");
        DynamicObject dy = QueryServiceHelper.queryOne("aos_mkt_test_work",str.toString(),new QFilter[]{filter_billno});
        //第二种查询
        DynamicObject[] dy_01 = load("aos_mkt_test_work",str.toString(),new QFilter[]{filter_billno});
        for(DynamicObject dy_ : dy_01){
        }


        //改
        for(DynamicObject testWork : dy_01){
            testWork.set("aos_decimalfield",999);
        }
        SaveServiceHelper.update(dy_01);
    }
}
