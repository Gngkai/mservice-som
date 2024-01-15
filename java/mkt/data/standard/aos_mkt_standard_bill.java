package mkt.data.standard;

import java.util.*;

import common.fnd.FndGlobal;
import common.fnd.FndMsg;
import kd.bos.dataentity.OperateOption;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.form.control.events.*;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.events.BeforeDoOperationEventArgs;
import kd.bos.form.events.MessageBoxClosedEvent;
import kd.bos.form.operate.FormOperate;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import mkt.data.com.AosMktDataComAudit;
import org.apache.commons.lang3.StringUtils;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.field.BasedataEdit;
import kd.bos.form.field.ComboEdit;
import kd.bos.form.field.ComboItem;
import kd.bos.form.field.events.BeforeF7SelectEvent;
import kd.bos.form.field.events.BeforeF7SelectListener;
import kd.bos.list.ListShowParameter;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;

/**
 * @author aosom
 */
public class aos_mkt_standard_bill extends AbstractBillPlugIn
    implements ItemClickListener, RowClickEventListener, BeforeF7SelectListener, CellClickListener {

    public final static String DB_MKT = "aos.mkt";

    @Override
    public void registerListener(EventObject e) {
        try {
            // 给工具栏加监听事件
            this.addItemClickListeners("tbmain");
            // 根据人员过滤国别
            BasedataEdit aosOrgid = this.getControl("aos_orgid");
            aosOrgid.addBeforeF7SelectListener(this);
            EntryGrid aosSalesentity = this.getControl("aos_salesentity");
            aosSalesentity.addCellClickListener(this);
        } catch (Exception ex) {
            this.getView().showErrorNotification("registerListener = " + ex);
        }
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        super.itemClick(evt);
        String control = evt.getItemKey();
        DynamicObject dyMain = this.getModel().getDataEntity(true);
        if ("aos_submit".equals(control)) {
            // 提交
            aos_submit(dyMain, "A");
        } else if ("aos_audit".equals(control)) {
            // 审核
            aos_audit(dyMain, "A");
        } else if ("aos_manuclose".equals(control)) {
            // 手工关闭
            aos_manuclose(dyMain, "A");
        } else if ("aos_manuopen".equals(control)) {
            // 手工开启
            aos_manuopen(dyMain, "A");
        }
    }

    private void aos_manuopen(DynamicObject dy_main, String type) {
        {
            dy_main.set("aos_status", "待优化");
            SaveServiceHelper.saveOperate("aos_mkt_standard", new DynamicObject[] {dy_main}, OperateOption.create());
            if (type.equals("A")) {
                this.getView().invokeOperation("refresh");
                this.getView().showSuccessNotification("手动开启成功");
            }
        }
    }

    /**
     * 手工关闭
     *
     * @param dy_main
     * @param type
     */
    private void aos_manuclose(DynamicObject dy_main, String type) {
        dy_main.set("aos_status", "手动关闭");
        SaveServiceHelper.saveOperate("aos_mkt_standard", new DynamicObject[] {dy_main}, OperateOption.create());
        if ("A".equals(type)) {
            this.getView().invokeOperation("refresh");
            this.getView().showSuccessNotification("手动关闭成功");
        }
    }

    private void aos_audit(DynamicObject dy_main, String type) {
        dy_main.set("aos_status", "已完成");
        dy_main.set("aos_confirmor", UserServiceHelper.getCurrentUserId());
        SaveServiceHelper.saveOperate("aos_mkt_standard", new DynamicObject[] {dy_main}, OperateOption.create());
        if ("A".equals(type)) {
            this.getView().invokeOperation("refresh");
            Map<String, Object> params = new HashMap<>(16);
            params.put("bill_type", "aos_mkt_standard");
            params.put("id", dy_main.getPkValue().toString());
            AosMktDataComAudit.audit(this, params);
        }
    }

    /**
     * 提交按钮
     *
     * @param dy_main
     * @param type
     */
    private void aos_submit(DynamicObject dy_main, String type) {
        dy_main.set("aos_status", "待确认");
        SaveServiceHelper.saveOperate("aos_mkt_standard", new DynamicObject[] {dy_main}, OperateOption.create());
        if ("A".equals(type)) {
            this.getView().invokeOperation("refresh");
            this.getView().showSuccessNotification("提交成功");
        }
    }

    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();
        if ("aos_category1".equals(name) || "aos_category2".equals(name) || "aos_category3".equals(name)) {
            init_category();
            syncCategory();
        }
    }

    /**
     * 同步产品类别
     */
    private void syncCategory() {
        this.getModel().setValue("aos_category1_name", this.getModel().getValue("aos_category1"));
        this.getModel().setValue("aos_category2_name", this.getModel().getValue("aos_category2"));
        this.getModel().setValue("aos_category3_name", this.getModel().getValue("aos_category3"));
    }

    @Override
    public void afterLoadData(EventObject e) {
        init_category();
        syncCategory();
    }

    public void afterCreateNewData(EventObject e) {
        init_category();
        init_org();
    }

    /**
     * 初始化国别
     */
    private void init_org() {
        long CurrentUserId = UserServiceHelper.getCurrentUserId();
        DynamicObject aos_mkt_useright = QueryServiceHelper.queryOne("aos_mkt_userights",
            "entryentity.aos_orgid aos_orgid", new QFilter[] {new QFilter("aos_user", QCP.equals, CurrentUserId)});
        long aos_orgid = aos_mkt_useright.getLong("aos_orgid");
        this.getModel().setValue("aos_orgid", aos_orgid);
    }

    /**
     * 初始化类别
     */
    private void init_category() {
        Object aos_category1 = this.getModel().getValue("aos_category1");
        Object aos_category2 = this.getModel().getValue("aos_category2");
        ComboEdit comboEdit = this.getControl("aos_category1");
        ComboEdit comboEdit2 = this.getControl("aos_category2");
        ComboEdit comboEdit3 = this.getControl("aos_category3");
        List<ComboItem> data = new ArrayList<>();
        List<ComboItem> data2 = new ArrayList<>();
        List<ComboItem> data3 = new ArrayList<>();
        // 大类
        QFilter filter_level = new QFilter("level", "=", "1");
        QFilter filter_divide = new QFilter("name", "!=", "待分类");
        QFilter[] filters_group = new QFilter[] {filter_level, filter_divide};
        String select_group = "name,level";
        DataSet bd_materialgroupS =
            QueryServiceHelper.queryDataSet(this.getClass().getName() + "." + "bd_materialgroupS", "bd_materialgroup",
                select_group, filters_group, null);
        while (bd_materialgroupS.hasNext()) {
            Row bd_materialgroup = bd_materialgroupS.next();
            // 获取数据
            String category_name = bd_materialgroup.getString("name");
            data.add(new ComboItem(new LocaleString(category_name), category_name));
        }
        bd_materialgroupS.close();
        comboEdit.setComboItems(data);

        if (aos_category1 == null) {
            comboEdit2.setComboItems(null);
            comboEdit3.setComboItems(null);
        } else {
            filter_level = new QFilter("level", "=", "2");
            filter_divide = new QFilter("parent.name", "=", aos_category1);
            filters_group = new QFilter[] {filter_level, filter_divide};
            select_group = "name,level";
            DataSet bd_materialgroup2S =
                QueryServiceHelper.queryDataSet(this.getClass().getName() + "." + "bd_materialgroup2S",
                    "bd_materialgroup", select_group, filters_group, null);
            while (bd_materialgroup2S.hasNext()) {
                Row bd_materialgroup2 = bd_materialgroup2S.next();
                // 获取数据
                String category_name;
                String[] names = bd_materialgroup2.getString("name").split(",");
                if (names.length > 1) {
                    category_name = names[1];
                } else {
                    continue;
                }
                data2.add(new ComboItem(new LocaleString(category_name), category_name));
            }
            bd_materialgroup2S.close();
            comboEdit2.setComboItems(data2);

            if (aos_category2 == null) {
                comboEdit3.setComboItems(null);
            } else {
                filter_level = new QFilter("level", "=", "3");
                filter_divide = new QFilter("parent.name", "=", aos_category1 + "," + aos_category2);
                filters_group = new QFilter[] {filter_level, filter_divide};
                select_group = "name,level";

                DataSet bd_materialgroup3S =
                    QueryServiceHelper.queryDataSet(this.getClass().getName() + "." + "bd_materialgroup3S",
                        "bd_materialgroup", select_group, filters_group, null);
                while (bd_materialgroup3S.hasNext()) {
                    Row bd_materialgroup3 = bd_materialgroup3S.next();
                    // 获取数据
                    String category_name;
                    String[] names = bd_materialgroup3.getString("name").split(",");
                    if (names.length > 2) {
                        category_name = names[2];
                    } else {
                        continue;
                    }
                    data3.add(new ComboItem(new LocaleString(category_name), category_name));
                }
                bd_materialgroup3S.close();
                comboEdit3.setComboItems(data3);
            }
        }
    }

    @Override
    public void beforeF7Select(BeforeF7SelectEvent beforeF7SelectEvent) {
        // 国别权限控制
        try {
            String name = beforeF7SelectEvent.getProperty().getName();
            // 获取当前人员id
            long CurrentUserId = UserServiceHelper.getCurrentUserId();
            if (StringUtils.equals(name, "aos_orgid")) {
                DynamicObjectCollection list =
                    QueryServiceHelper.query("aos_mkt_userights", "entryentity.aos_orgid aos_orgid",
                        new QFilter[] {new QFilter("aos_user", QCP.equals, CurrentUserId)});
                List<String> orgList = new ArrayList<>();
                for (DynamicObject obj : list) {
                    orgList.add(obj.getString("aos_orgid"));
                }
                QFilter qFilter = new QFilter("id", QCP.in, orgList);
                ListShowParameter showParameter = (ListShowParameter)beforeF7SelectEvent.getFormShowParameter();
                showParameter.getListFilterParameter().getQFilters().add(qFilter);
            }
        } catch (Exception ex) {
            this.getView().showErrorNotification("beforeF7Select = " + ex);
        }
    }

    @Override
    public void cellClick(CellClickEvent arg0) {
        String name = arg0.getFieldKey();
        int row = arg0.getRow();
        if (name.equals("aos_featuresl") && row != -1
            && this.getModel().getValue("aos_featuresl", row).toString().equals(""))
            this.getModel().setValue("aos_featuresl", "-", row);
    }

    @Override
    public void cellDoubleClick(CellClickEvent arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void beforeDoOperation(BeforeDoOperationEventArgs args) {
        super.beforeDoOperation(args);
        FormOperate formOperate = (FormOperate)args.getSource();
        String Operatation = formOperate.getOperateKey();
        if ("save".equals(Operatation)) {
            FndMsg.debug("=====save Operation=====");
            Object aos_orgid = this.getModel().getValue("aos_orgid");
            if (FndGlobal.IsNull(aos_orgid)) {
                this.getView().showErrorNotification("国别不能为空");
                args.setCancel(true);
                return;
            }
            // 校验
            DynamicObject aosMktStandard = QueryServiceHelper.queryOne("aos_mkt_standard", "id",
                new QFilter("aos_category1", QCP.equals, this.getModel().getValue("aos_category1"))
                    .and("aos_category2", QCP.equals, this.getModel().getValue("aos_category2"))
                    .and("aos_category3", QCP.equals, this.getModel().getValue("aos_category3"))
                    .and("aos_itemnamecn", QCP.equals, this.getModel().getValue("aos_itemnamecn"))
                    .and("aos_orgid", QCP.equals, ((DynamicObject)aos_orgid).getPkValue())
                    .and("id", QCP.not_equals, this.getModel().getDataEntity().getPkValue().toString()).toArray());
            if (FndGlobal.IsNotNull(aosMktStandard)) {
                this.getView().showErrorNotification("已存在相同的产品类别和品名");
                args.setCancel(true);
            }
        }
    }

    @Override
    public void afterDoOperation(AfterDoOperationEventArgs args) {
        String operateKey = args.getOperateKey();
        if ("aos_manuclose".equals(operateKey) || "aos_manuopen".equals(operateKey)) {
            OperationResult operationResult = args.getOperationResult();
            operationResult.setShowMessage(false);
            this.getView().showTipNotification(operationResult.getMessage());
        }
    }

    @Override
    public void confirmCallBack(MessageBoxClosedEvent messageBoxClosedEvent) {
        // 回调标识
        String callBackId = messageBoxClosedEvent.getCallBackId();
        // 确认结果
        String resultValue = messageBoxClosedEvent.getResultValue();
        // 过滤条件
        String aos_category1_name = this.getModel().getValue("aos_category1_name").toString();
        String aos_category2_name = this.getModel().getValue("aos_category2_name").toString();
        String aos_category3_name = this.getModel().getValue("aos_category3_name").toString();
        String aos_itemnamecn = this.getModel().getValue("aos_itemnamecn").toString();
        String aos_detail = this.getModel().getValue("aos_detail").toString();
        QFilter[] qFilters = new QFilter[] {new QFilter("aos_category1_name", QCP.equals, aos_category1_name),
            new QFilter("aos_category2_name", QCP.equals, aos_category2_name),
            new QFilter("aos_category3_name", QCP.equals, aos_category3_name),
            new QFilter("aos_itemnamecn", QCP.equals, aos_itemnamecn),
            new QFilter("aos_detail", QCP.equals, aos_detail),};
        if ("Yes".equals(resultValue) && "audit".equals(callBackId)) {
            // 如果为是 更新相同品类的摄影 摄像 布景标准库 的进度为待优化
            DynamicObject photoObj =
                BusinessDataServiceHelper.loadSingle("aos_mkt_designstd", "billstatus,aos_status", qFilters);
            if (photoObj != null) {
                photoObj.set("billstatus", "D");
                photoObj.set("aos_status", "待优化");
                SaveServiceHelper.save(new DynamicObject[] {photoObj});
            }
        }
    }
}