package mkt.progress.report;

import common.Cux_Common_Utl;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.datamodel.AbstractFormDataModel;
import kd.bos.entity.datamodel.TableValueSetter;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import common.sal.util.QFBuilder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;


/**
 * @author create by gk
 * @date 2023/2/16 9:46
 * @action  营销操作日志报表
 */
public class aos_mkt_operate_rpt extends AbstractFormPlugin {
    @Override
    public void afterDoOperation(AfterDoOperationEventArgs eventArgs) {
        super.afterDoOperation(eventArgs);
        String operateKey = eventArgs.getOperateKey();
        if (operateKey.equals("aos_query")){
            try {
                query();
                this.getView().updateView("aos_entryentity");
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }
    private void query(){
        this.getModel().deleteEntryData("aos_entryentity");
        List<String> list_fildes = Arrays.asList("aos_sourceid", "aos_action", "aos_actionby", "aos_actiondate", "aos_desc");
        StringJoiner selectFildes = new StringJoiner(",");
        for (String filde : list_fildes) {
           selectFildes.add(filde);
        }
        QFBuilder qfBuilder = getOperateFiltes();
        //查询操作日期
        DynamicObjectCollection dyc_operate = QueryServiceHelper.query("aos_sync_operate", selectFildes.toString(),qfBuilder.toArray());
        List<String> list_sourceid = dyc_operate.stream()
                .map(dy -> dy.getString("aos_sourceid"))
                .distinct()
                .collect(Collectors.toList());

        //查找源单单号
        Object aos_type = this.getModel().getValue("aos_type");
        qfBuilder.clear();
        qfBuilder.add("id",QFilter.in,list_sourceid);
        Object aos_no = this.getModel().getValue("aos_no");
        if (!Cux_Common_Utl.IsNull(aos_no))
            qfBuilder.add("billno","=",aos_no);
        Map<String, String> map_sourceBillno = QueryServiceHelper.query(aos_type.toString(), "id,billno", qfBuilder.toArray())
                .stream()
                .collect(Collectors.toMap(
                        dy -> dy.getString("id"),
                        dy -> dy.getString("billno"),
                        (key1, key2) -> key1
                ));

        TableValueSetter setter=new TableValueSetter();
        for (String field : list_fildes) {
            if (!field.equals("aos_sourceid"))
            setter.addField(field);
        }
        setter.addField("aos_billno");
        List<String> fields = setter.getFields();
        for (DynamicObject dy : dyc_operate) {
            if (!Cux_Common_Utl.IsNull(dy.get("aos_sourceid"))){
                String aos_sourceid = dy.getString("aos_sourceid");
                if (map_sourceBillno.containsKey(aos_sourceid)){
                    List<Object> list_value = new ArrayList<>();
                    for (String field : fields) {
                        if (!field.equals("aos_billno"))
                            list_value.add(dy.get(field));
                    }
                    list_value.add(map_sourceBillno.get(aos_sourceid));
                    Object[] rowValue = list_value.toArray(new Object[0]);
                    setter.addRow(rowValue);
                }
            }
        }
        AbstractFormDataModel model= (AbstractFormDataModel) this.getModel();
        model.beginInit();
        model.batchCreateNewEntryRow("aos_entryentity",setter);
        model.endInit();

    }
    private QFBuilder getOperateFiltes(){
        QFBuilder qfBuilder = new QFBuilder();
        Object aos_type = this.getModel().getValue("aos_type");
        if (!Cux_Common_Utl.IsNull(aos_type)){
            qfBuilder.add("aos_formid","=",aos_type);
        }
        Object aos_start = this.getModel().getValue("aos_start");
        if (!Cux_Common_Utl.IsNull(aos_start)){
            LocalDate date = ((Date) aos_start).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            qfBuilder.add("aos_actiondate",">=",date.toString());
        }
        Object aos_end = this.getModel().getValue("aos_end");
        if (!Cux_Common_Utl.IsNull(aos_end)){
            LocalDate date = ((Date) aos_end).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            qfBuilder.add("aos_actiondate","<=",date.toString());
        }
        return qfBuilder;
    }
}
