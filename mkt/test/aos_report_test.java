package mkt.test;

import common.Cux_Common_Utl;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.datamodel.AbstractFormDataModel;
import kd.bos.entity.datamodel.TableValueSetter;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import kd.epm.eb.formplugin.AbstractListPlugin;
import kd.fi.bd.util.QFBuilder;
import java.util.*;
//
public class aos_report_test extends AbstractListPlugin {
    public void afterDoOperation(AfterDoOperationEventArgs eventArgs) {
        super.afterDoOperation(eventArgs);
        String operateKey = eventArgs.getOperateKey();
        if (operateKey.equals("aos_query")) {
            try {
                select();
                this.getView().updateView("aos_entryentity");
            }
            catch (Exception e) {
            }
        }
    }
    void select() {
        this.getModel().deleteEntryData("aos_entryentity");
        //原单查询字段
        List<String> list_fildes = Arrays.asList("billno", "aos_itemid","aos_itemname", "aos_newitem", "aos_newvendor", "aos_vendor",
                "aos_ponumber","aos_orgtext","aos_photoflag","aos_vedioflag","aos_type","aos_phstate","aos_samplestatus",
                "aos_sale","aos_returntimes","aos_user","aos_urgent","aos_shipdate","aos_arrivaldate","aos_overseasdate",
                "aos_sampledate","aos_installdate", "aos_whitedate","aos_actdate","aos_follower.name aos_follower","aos_developer","aos_whiteph",
                "aos_actph","aos_vedior");
        StringJoiner str = new StringJoiner(",");
        for (String filde : list_fildes) {
            str.add(filde);
        }

        //查询
        QFBuilder qfBuilder = getOperateFiltes();//查询的过滤约束条件
        DynamicObjectCollection dyc = QueryServiceHelper.query("aos_mkt_photoreq", str.toString(), qfBuilder.toArray()); //查询结果
        //DynamicObject[] dyc = BusinessDataServiceHelper.load("aos_mkt_photoreq", str.toString(), qfBuilder.toArray());


        /**
         * 1. dy遍历
         * 2.aos_entryentity 增行
         * 3.行赋值
         */
        //单据体中每行数据都是一个DynamicObject，多行数据集合为DynamicObjectCollection
        //DynamicObject dy = this.getModel().getDataEntity(true);
        DynamicObjectCollection entryentity = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity");
        //DynamicObject user = BusinessDataServiceHelper.loadSingle("currentUserId", "bos_user");

        //人员赋值问题： 1.查询name 对界面赋值。
        // 2.人员换成对应的基础资料，修改赋值
        // 2.1.BusinessDataServiceHelper.loadSingle(dy_temp.get("aos_developer"),"bos_user") 不推荐
        // 2.2.先this.getModel().createNewEntryRow("单据体标识" ); 再赋值
//        for (DynamicObject dy_row : dyc) {
//            int row = this.getModel().createNewEntryRow("aos_entryentity");
//            this.getModel().setValue("aos_developer",dy_row.get("aos_developer"),row);
//        }
        //  2.3 TableValueSetter 赋值
        List<String> list_fields = new ArrayList<>();
        list_fields.add("aos_developer");
        TableValueSetter setter = new TableValueSetter(list_fields.toArray(new String[0]));
        for (DynamicObject dy_row : dyc) {
            setter.addRow(dy_row.get("aos_developer"));
        }
        AbstractFormDataModel model= (AbstractFormDataModel) this.getModel();
        model.beginInit();
        model.batchCreateNewEntryRow("aos_entryentity",setter);
        model.endInit();



//          for (DynamicObject dy_temp : dyc) {
//            DynamicObject addNew = entryentity.addNew();
//            addNew.set("billno",dy_temp.get("billno"));//前个为本单，后个为原单
//            addNew.set("aos_itemid",dy_temp.get("aos_itemid"));
//            addNew.set("aos_itemname",dy_temp.get("aos_itemname"));
//            addNew.set("aos_newitem",dy_temp.get("aos_newitem"));
//            addNew.set("aos_newvendor",dy_temp.get("aos_newvendor"));
//            addNew.set("aos_vendor",dy_temp.get("aos_vendor"));
//            addNew.set("aos_ponumber",dy_temp.get("aos_ponumber"));
//            addNew.set("aos_orgtext",dy_temp.get("aos_orgtext"));
//            addNew.set("aos_photoflag",dy_temp.get("aos_photoflag"));
//            addNew.set("aos_vedioflag",dy_temp.get("aos_vedioflag"));
//            addNew.set("aos_type",dy_temp.get("aos_type"));
//            addNew.set("aos_phstate",dy_temp.get("aos_phstate"));
//            addNew.set("aos_samplestatus",dy_temp.get("aos_samplestatus"));
//            addNew.set("aos_sale",dy_temp.get("aos_sale"));
//            addNew.set("aos_returntimes",dy_temp.get("aos_returntimes"));
//            addNew.set("aos_user", dy_temp.get("aos_user"));
//            addNew.set("aos_urgent",dy_temp.get("aos_urgent"));
//            addNew.set("aos_shipdate",dy_temp.get("aos_shipdate"));
//            addNew.set("aos_arrivaldate",dy_temp.get("aos_arrivaldate"));
//            addNew.set("aos_overseasdate",dy_temp.get("aos_overseasdate"));
//            addNew.set("aos_sampledate",dy_temp.get("aos_sampledate"));
//            addNew.set("aos_installdate",dy_temp.get("aos_installdate"));
//            addNew.set("aos_whitedate",dy_temp.get("aos_whitedate"));
//            addNew.set("aos_actdate",dy_temp.get("aos_actdate"));
//            addNew.set("aos_follower",dy_temp.get("aos_follower"));
//            addNew.set("aos_developer",BusinessDataServiceHelper.loadSingle(dy_temp.get("aos_developer"),"bos_user"));
//            addNew.set("aos_whiteph",dy_temp.get("aos_whiteph"));
//            addNew.set("aos_actph",dy_temp.get("aos_actph"));
//            addNew.set("aos_vedior",dy_temp.get("aos_vedior"));
//        }
            //this.getView().invokeOperation("refresh");
        //DynamicObjectCollection entryRows = this.getModel().getEntryEntity("aos_entryentity");
        //获取当前页面的数据包 DynamicObject dataEntity = this.getModel().getDataEntity(true);
    }

    //查询过滤条件
    private QFBuilder getOperateFiltes(){
        QFBuilder qfBuilder = new QFBuilder();
        DynamicObject aos_materielfield = (DynamicObject) this.getModel().getValue("aos_huoid");//拿到的是一个DynamicObject对象（数据包）
//        DynamicObjectCollection aos_materielfield = (DynamicObjectCollection) this.getModel().getValue("aos_huoid");//拿到的是一个DynamicObject对象（数据包）
//        for(DynamicObject col : aos_materielfield){
//            DynamicObject baseDate = (DynamicObject) col.get("aos_huoid");
//        }

        if (!Cux_Common_Utl.IsNull(aos_materielfield)) {
            qfBuilder.add("aos_itemid", "=", aos_materielfield.get("id"));
            //qfBuilder.add("aos_itemid", "=", "baseDate");

        }

        Object aos_pname = this.getModel().getValue("aos_pname");
        if(!Cux_Common_Utl.IsNull(aos_pname)){
            qfBuilder.add("aos_itemname","=",aos_pname);
        }

        Object aos_photoplace = this.getModel().getValue("aos_photoplace");
        if(!Cux_Common_Utl.IsNull(aos_photoplace)){
            qfBuilder.add("aos_phstate","=",aos_photoplace);
        }
        return qfBuilder;
    }

}
