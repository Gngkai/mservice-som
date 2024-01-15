package mkt.data.com;

import common.fnd.FndMsg;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.form.control.Control;
import kd.bos.form.field.ComboEdit;
import kd.bos.form.field.ComboItem;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;

import java.util.*;

/**
 * @author aosom
 */
public class AosMktDataComForm extends AbstractFormPlugin {
    private static final String BILL_TYPE = "bill_type";
    private static final String AOS_BILL = "aos_bill";
    private static final Map<String, String> OPTIONS = new LinkedHashMap<>();
    static {
        OPTIONS.put("aos_mkt_standard", "国别文案标准库");
        OPTIONS.put("aos_mkt_designstd", "设计标准库");
        OPTIONS.put("aos_mkt_photostd", "布景标准库");
        OPTIONS.put("aos_mkt_viewstd", "摄影标准库");
        OPTIONS.put("aos_mkt_videostd", "摄像标准库");
    }

    @Override
    public void afterCreateNewData(EventObject e) {
        Map<String, Object> params = this.getView().getFormShowParameter().getCustomParam("params");
        String billType = (String)params.get(BILL_TYPE);
        // 设置过滤
        ComboEdit comboEdit = this.getControl(AOS_BILL);
        comboEdit.setComboInputable(false);
        // 设置下拉框的值
        List<ComboItem> data = new LinkedList<>();
        for (Map.Entry<String, String> entry : OPTIONS.entrySet()) {
            if (!entry.getKey().equals(billType)) {
                LocaleString locale = new LocaleString();
                locale.setLocaleValue_zh_CN(entry.getValue());
                data.add(new ComboItem(locale, entry.getKey()));
            }
        }
        comboEdit.setComboItems(data);
    }

    public void registerListener(EventObject e) {
        super.registerListener(e);
        this.addClickListeners("btnok");
    }

    public void click(EventObject evt) {
        super.click(evt);
        Control control = (Control)evt.getSource();
        String key = control.getKey();
        if ("btnok".equals(key)) {
            btnok();
        }
    }

    /**
     * 确认按钮
     */
    private void btnok() {
        FndMsg.debug("========into btnok========");
        Map<String, Object> params = this.getView().getFormShowParameter().getCustomParam("params");
        String id = (String)params.get("id");
        String billType = (String)params.get(BILL_TYPE);
        DynamicObject bill = BusinessDataServiceHelper.loadSingle(id, billType);
        String aosCategory1Name = bill.getString("aos_category1_name");
        String aosCategory2Name = bill.getString("aos_category2_name");
        String aosCategory3Name = bill.getString("aos_category3_name");
        String aosItemnamecn = bill.getString("aos_itemnamecn");
        String aosDetail = bill.getString("aos_detail");
        QFilter[] qFilters = new QFilter[] {new QFilter("aos_category1_name", QCP.equals, aosCategory1Name),
            new QFilter("aos_category2_name", QCP.equals, aosCategory2Name),
            new QFilter("aos_category3_name", QCP.equals, aosCategory3Name),
            new QFilter("aos_itemnamecn", QCP.equals, aosItemnamecn),
            new QFilter("aos_detail", QCP.equals, aosDetail),};
        for (Map.Entry<String, String> entry : OPTIONS.entrySet()) {
            if (!entry.getKey().equals(billType)) {
                DynamicObject object =
                    BusinessDataServiceHelper.loadSingle(entry.getKey(), "billstatus,aos_status", qFilters);
                if (object != null) {
                    object.set("billstatus", "D");
                    object.set("aos_status", "待优化");
                    SaveServiceHelper.save(new DynamicObject[] {object});
                }
            }
        }
        this.getView().close();
    }
}
