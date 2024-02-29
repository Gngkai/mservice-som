package mkt.act.basedata.actType;

import kd.bos.bill.BillShowParameter;
import kd.bos.bill.OperationStatus;
import kd.bos.entity.datamodel.ListSelectedRow;
import kd.bos.form.ShowType;
import kd.bos.form.control.Control;
import kd.bos.form.events.*;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.list.BillList;
import kd.bos.orm.query.QFilter;

import java.util.EventObject;

/**
 * @author: GK
 * @create: 2024-02-29 10:34
 * @Description: 活动日志查询报表
 */
@SuppressWarnings("unused")
public class ActLogReport extends AbstractFormPlugin implements HyperLinkClickListener {
    @Override
    public void afterDoOperation(AfterDoOperationEventArgs e) {
        super.afterDoOperation(e);
        String operateKey = e.getOperateKey();
        if ("aos_query".equals(operateKey)) {
            queryData();
        }
    }

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        BillList billList = getControl("aos_billlistap");
        billList.addHyperClickListener(this::hyperLinkClick);
    }

    @Override
    public void initialize() {
        super.initialize();
        BillList billList = getControl("aos_billlistap");
        billList.setFilter(new QFilter("1","=","2"));
    }

    @Override
    public void hyperLinkClick(HyperLinkClickEvent event) {
        Control control = (Control) event.getSource();
        ListSelectedRow sele = ((BillList) control).getCurrentSelectedRowInfo();
        Object id = sele.getPrimaryKeyValue();
        BillShowParameter billShowParameter = new BillShowParameter();
        billShowParameter.setFormId("aos_sync_log");
        billShowParameter.setPkId(id);
        billShowParameter.getOpenStyle().setShowType(ShowType.Modal);
        billShowParameter.setStatus(OperationStatus.VIEW);
        control.getView().showForm(billShowParameter);

    }

    private void queryData(){
        QFilter qFilter;
        String type = getModel().getValue("aos_sel_type").toString();
        qFilter = new QFilter("aos_type_code","=",type);

        BillList billList = getControl("aos_billlistap");
        billList.setFilter(qFilter);
        getView().updateView();
    }
}
