package mkt.test;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import java.util.EventObject;
import java.util.StringJoiner;
import static kd.bos.servicehelper.BusinessDataServiceHelper.*;

public class zsgc extends AbstractBillPlugIn {
    @Override
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        //第一种
        QFilter filter_billno = new QFilter("billno","=",003);
        StringJoiner str = new StringJoiner(",");
        str.add("billno");
        str.add("creator");
        str.add("aos_decimalfield");
        str.add("aos_textareafield");
        //str.add("aos_entryentity.aos_enable");
        str.add("aos_entryentity.aos_basedatafield.name");
        DynamicObject dy = QueryServiceHelper.queryOne("aos_mkt_test_work",str.toString(),new QFilter[]{filter_billno});
        System.out.println("dy = " + dy);
        //第二种查询
        DynamicObject[] dy_01 = load("aos_mkt_test_work",str.toString(),new QFilter[]{filter_billno});
        for(DynamicObject dy_ : dy_01){
            System.out.println("dy_01 = " + dy_);
        }


        //改
        for(DynamicObject testWork : dy_01){
            testWork.set("aos_decimalfield",999);
        }
        SaveServiceHelper.update(dy_01);

        //删
        QFilter filter_billno1 = new QFilter("aos_integerfield1","=","110");
       // filter_billno.__setProperty("aos_integerfield1");
        DeleteServiceHelper.delete("aos_mkt_test_work",new QFilter[]{filter_billno1});

        QFilter filter_billno2 = new QFilter("aos_integerfield1","=","2323");
        DeleteServiceHelper.delete("aos_mkt_test_work",new QFilter[]{filter_billno2});

        //增
        DynamicObject dy_test = BusinessDataServiceHelper.newDynamicObject("aos_mkt_test_work");
        dy_test.set("aos_textfield1","好好");
        SaveServiceHelper.save(new DynamicObject[]{dy_test});

        DynamicObject dy_test01 = BusinessDataServiceHelper.loadSingle("aos_mkt_test_work",new QFilter[]{filter_billno});
        dy_test01.set("aos_textfield1","好好");
        SaveServiceHelper.save(new DynamicObject[]{dy_test01});


    }


}
