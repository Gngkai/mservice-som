package mkt.test;

import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.entity.operate.result.IOperateInfo;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.field.ComboEdit;
import kd.bos.form.field.ComboItem;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import kd.tmc.creditm.business.service.usecredit.BaseUseCreditService;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import java.util.StringJoiner;

public class demo10 extends AbstractBillPlugIn {
    public void afterCreateNewData(EventObject e) {
        //查
        /**第一种简单查值 **/
        QFilter filter_billno = new QFilter("billno","=","009");
        StringJoiner str = new StringJoiner(",");
        str.add("billno");//基本信息单据编号
        str.add("aos_integerfield1");//基本信息结果字段
        str.add("aos_entryentity.aos_basedatafield");//单据体中的基础资料字段
        //str.add("aos_entryentity.aos_basedatafield.number");//单据体中的基础资料的编码字段
        DynamicObject dy = QueryServiceHelper.queryOne("aos_mkt_test_work", str.toString(), new QFilter[]{filter_billno});
        System.out.println("dy = " + dy);
        /** 第二种查询方法 **/
        DynamicObject[] dyc_tsetWork = BusinessDataServiceHelper.load("aos_mkt_test_work", str.toString(), new QFilter[]{filter_billno});
        for (DynamicObject testWork : dyc_tsetWork) {
            System.out.println("testWork = " + testWork);
        }
        
        /** 改 **/
        for (DynamicObject testWork : dyc_tsetWork) {
            testWork.set("aos_integerfield1",120);
        }
        SaveServiceHelper.update(dyc_tsetWork);//保存

        /** 删 **/
        filter_billno.__setValue("002");
        DeleteServiceHelper.delete("aos_mkt_test_work",new QFilter[]{filter_billno});


        /** 增加 **/
        DynamicObject dy_test = BusinessDataServiceHelper.newDynamicObject("aos_mkt_test_work");
        dy_test.set("aos_integerfield1","1000");
//        SaveServiceHelper.save(new DynamicObject[]{dy_test});
        OperationResult result = SaveServiceHelper.saveOperate("aos_mkt_test_work", new DynamicObject[]{dy_test}, OperateOption.create());
        System.out.println("result.isSuccess() = " + result.isSuccess());
        /** 打印保存失败信息 **/
        if (!result.isSuccess()) {
            for (IOperateInfo info : result.getAllErrorOrValidateInfo()) {
                System.out.println("info.getMessage() = " + info.getMessage());
            }
        }


    }

    @Override
    public void afterBindData(EventObject e) {
        super.afterBindData(e);
        //适用于单据体各字段
/*
        if (getModel().getEntryEntity("aos_combofield").size() < 1) {
            for (int i = 1; i < 3; i++) {
                int rowIndex = this.getModel().createNewEntryRow("aos_combofield");
                getModel().setValue("aos_combofield", 1, rowIndex);
            }
            DynamicObjectCollection collection;
            collection = getModel().getEntryEntity("aos_combofield");
            getModel().updateEntryCache(collection);
        }
         */

        List<ComboItem> list;
        //复选框字段控件编程模型ComboEdit
        ComboEdit comboEidt = this.getControl("aos_combofield");
        ComboItem citem;
        LocaleString caption;
        list = new ArrayList<>(3);
        for(int i = 0; i < 3; ++i){
            citem = new ComboItem();
            caption = new LocaleString(String.valueOf(i));
            citem.setCaption(caption);
            citem.setValue(String.valueOf(i));
            list.add(citem);
        }
        comboEidt.setComboItems(list);
        // this.getModel().setValue("aos_combofield","1");//默认值
    }
}