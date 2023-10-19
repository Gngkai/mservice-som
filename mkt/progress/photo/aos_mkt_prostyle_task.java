package mkt.progress.photo;

import common.fnd.FndGlobal;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import java.util.*;
import java.util.stream.Collectors;

public class aos_mkt_prostyle_task extends AbstractTask {
    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        System.out.println("开始");
        //拍照需求表
        DynamicObject[] progphreq = BusinessDataServiceHelper.load("aos_mkt_photoreq",
                "id,aos_itemid,aos_productstyle_new,aos_shootscenes,aos_entryentity5",null);  // new QFilter("billno",QCP.equals,"PZXQB202309261033").toArray())

        for(DynamicObject dy : progphreq) {
            DynamicObject sku = (DynamicObject) dy.get("aos_itemid");  //拍照需求表sku
            DynamicObject item = BusinessDataServiceHelper.loadSingle(
                    "bd_material",
                    "aos_productstyle_new,aos_shootscenes",
                    new QFilter("number", QCP.equals, sku.get("number")).toArray());

            if (FndGlobal.IsNotNull(item)) {
                DynamicObjectCollection productStyle = item.getDynamicObjectCollection("aos_productstyle_new");
                StringJoiner aaa = new StringJoiner(";");
                if (productStyle.size() != 0) {
                    List<Object> id = productStyle.stream().map(e -> e.getDynamicObject("fbasedataid").getPkValue()).collect(Collectors.toList());
                    for(Object a : id) {
                        DynamicObject dysty = QueryServiceHelper.queryOne("aos_product_style","id,name",
                                new QFilter("id", QCP.equals,a).toArray());
                        String styname = dysty.getString("name");
                        aaa.add(styname);
                    }
                }
                DynamicObjectCollection entryentity5 = dy.getDynamicObjectCollection("aos_entryentity5");
                if (entryentity5.size() > 0) {
                    for (DynamicObject dypr : entryentity5) {
                        System.out.println("aaa.toString( = " + aaa.toString() + "  sku.get(\"number\") = " + sku.get("number"));
                        dypr.set("aos_productstyle_new",aaa.toString());
                        dypr.set("aos_shootscenes",item.getString("aos_shootscenes")); //item.getString("aos_shootscenes")
                    }
                }
            }
        }
        SaveServiceHelper.update(progphreq);
        System.out.println("拍照需求表结束");

        //设计需求表
        DynamicObject[] designreq = BusinessDataServiceHelper.load("aos_mkt_designreq",
                "id,aos_sub_item,aos_productstyle_new,aos_shootscenes,aos_entryentity",null);
        for(DynamicObject dy : designreq){
            DynamicObjectCollection entrytity1 = dy.getDynamicObjectCollection("aos_entryentity"); //货号信息
            for(DynamicObject dy1 : entrytity1){
                DynamicObjectCollection entrytity = dy1.getDynamicObjectCollection("aos_subentryentity");
                if(entrytity == null) continue;
                if(entrytity.getRowCount() > 0){
                    for(DynamicObject dyc :entrytity){
                        if(dyc == null) continue;
                        DynamicObject sku = dyc.getDynamicObject("aos_sub_item");
                        if(sku == null) continue;
                        DynamicObject item = BusinessDataServiceHelper.loadSingle("bd_material",
                                "aos_productstyle_new,aos_shootscenes",
                                new QFilter("number", QCP.equals,sku.get("number")).toArray());
                        if(FndGlobal.IsNotNull(item)){
                            DynamicObjectCollection bbb = item.getDynamicObjectCollection("aos_productstyle_new");
                            StringJoiner aaa = new StringJoiner(";");
                            if(bbb.size() != 0){
                                List<Object> id = bbb.stream().map(e -> e.getDynamicObject("fbasedataid").getPkValue()).collect(Collectors.toList());
                                for(Object a : id) {
                                    DynamicObject dysty = QueryServiceHelper.queryOne("aos_product_style","id,name",
                                            new QFilter("id", QCP.equals,a).toArray());
                                    String styname = dysty.getString("name");
                                    aaa.add(styname);
                                }
                                dyc.set("aos_productstyle_new",aaa.toString());
                                System.out.println("aaa.toString() = " + aaa.toString() + " sku = " + sku);
                            }
                            dyc.set("aos_shootscenes", item.getString("aos_shootscenes"));
                        }
                        SaveServiceHelper.save(new DynamicObject[]{dy});
                    }
                }
            }
        }
        SaveServiceHelper.update(designreq);
        System.out.println("结束");
    }
}
