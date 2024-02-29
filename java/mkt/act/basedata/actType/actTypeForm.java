package mkt.act.basedata.actType;

import common.fnd.FndGlobal;
import common.sal.util.QFBuilder;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.datamodel.IDataModel;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.entity.formula.CalcExprParser;
import kd.bos.form.CloseCallBack;
import kd.bos.form.FormShowParameter;
import kd.bos.form.ShowType;
import kd.bos.form.control.Control;
import kd.bos.form.events.BeforeDoOperationEventArgs;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.form.field.BasedataEdit;
import kd.bos.form.field.events.BeforeF7SelectEvent;
import kd.bos.form.field.events.BeforeF7SelectListener;
import kd.bos.form.operate.FormOperate;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.formula.FormulaEngine;
import kd.bos.list.ListShowParameter;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;

import java.util.*;

/**
 * @author create by gk
 * @date 2023/8/17 16:02
 * @action 活动库界面插件
 */
@SuppressWarnings("unchecked")
public class actTypeForm extends AbstractBillPlugIn implements BeforeF7SelectListener {

    public  static final String Form_key="aos_sal_shop_actprofit";  //界面标识
    public  static final String Key_return_k="re_v";    //回传公式标识
    public  static final String Key_return_v="re_k";    //回传公式名称

    public  static final String Key_entity="operate";   //父界面传值标识
    public  static final List<String> ASSESS_LIST = Arrays.asList("平台活动","线下活动","站外Deal");

    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        super.propertyChanged(e);
        String name = e.getProperty().getName();
        //活动分类修改
        if ("aos_sort".equals(name)){
            sortChange(e.getChangeSet()[0].getNewValue());
        }
    }

    private void sortChange(Object newValue){
        if (FndGlobal.IsNull(newValue)) {
            getModel().setItemValueByNumber("aos_assessment","N");
        }
        else {
            String newValueStr = String.valueOf(newValue);
            if (ASSESS_LIST.contains(newValueStr)) {
                getModel().setItemValueByNumber("aos_assessment","Y");
            }
            else {
                getModel().setItemValueByNumber("aos_assessment","N");
            }
        }
    }

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        BasedataEdit control = this.getControl("aos_shop");
        control.addBeforeF7SelectListener(this);

        control = this.getControl("aos_re_shop");
        control.addBeforeF7SelectListener(this);

        control = this.getControl("aos_re_act");
        control.addBeforeF7SelectListener(this);

        this.addClickListeners("aos_rule","aos_priceformula","aos_name","aos_rule_value");

    }

    @Override
    public void beforeF7Select(BeforeF7SelectEvent beforeF7SelectEvent) {
        ListShowParameter showParameter = (ListShowParameter) beforeF7SelectEvent.getFormShowParameter();
        String F7name = beforeF7SelectEvent.getProperty().getName();
        switch (F7name) {
            case "aos_shop": {
                List<QFilter> filters = new ArrayList<>();
                if (getModel().getValue("aos_org") != null) {
                    DynamicObject org = (DynamicObject) getModel().getValue("aos_org");
                    QFilter filter = new QFilter("aos_org", "=", org.getString("id"));
                    filters.add(filter);
                }
                if (getModel().getValue("aos_channel") != null) {
                    DynamicObject channel = (DynamicObject) getModel().getValue("aos_channel");
                    QFilter filter = new QFilter("aos_channel", "=", channel.getString("id"));
                    filters.add(filter);
                }
                showParameter.getListFilterParameter().setQFilters(filters);
                break;
            }
            case "aos_re_shop": {
                List<QFilter> filters = new ArrayList<>();
                if (getModel().getValue("aos_org") != null) {
                    DynamicObject org = (DynamicObject) getModel().getValue("aos_org");
                    QFilter filter = new QFilter("aos_org", "=", org.getString("id"));
                    filters.add(filter);
                }
                int index = this.getModel().getEntryCurrentRowIndex("aos_entryentity3");
                if (getModel().getValue("aos_re_channel", index) != null) {
                    DynamicObject channel = (DynamicObject) getModel().getValue("aos_re_channel", index);
                    QFilter filter = new QFilter("aos_channel", "=", channel.getString("id"));
                    filters.add(filter);
                }
                showParameter.getListFilterParameter().setQFilters(filters);
                break;
            }
            case "aos_re_act": {
                List<QFilter> filters = new ArrayList<>();
                if (getModel().getValue("aos_org") != null) {
                    DynamicObject org = (DynamicObject) getModel().getValue("aos_org");
                    QFilter filter = new QFilter("aos_org", "=", org.getString("id"));
                    filters.add(filter);
                }
                int index = this.getModel().getEntryCurrentRowIndex("aos_entryentity3");
                if (getModel().getValue("aos_re_channel", index) != null) {
                    DynamicObject channel = (DynamicObject) getModel().getValue("aos_re_channel", index);
                    QFilter filter = new QFilter("aos_channel", "=", channel.getString("id"));
                    filters.add(filter);
                }
                if (getModel().getValue("aos_re_shop", index) != null) {
                    DynamicObject shop = (DynamicObject) getModel().getValue("aos_re_shop", index);
                    QFilter filter = new QFilter("aos_shop", "=", shop.getString("id"));
                    filters.add(filter);
                }
                DynamicObjectCollection dyc = QueryServiceHelper.query("aos_sal_act_type_p", "aos_acttype", filters.toArray(new QFilter[0]));
                List<String> ids = new ArrayList<>(dyc.size());
                for (DynamicObject dy : dyc) {
                    ids.add(dy.getString("aos_acttype"));
                }
                filters.clear();
                filters.add(new QFilter("id", QFilter.in, ids));
                showParameter.getListFilterParameter().setQFilters(filters);
                break;
            }
        }
    }

    @Override
    public void click(EventObject evt) {
        super.click(evt);
        Control control= (Control) evt.getSource();
        String key= control.getKey();
        switch (key) {
            case "aos_rule":
                showParameter(key, Form_key, this);
                break;
            case "aos_priceformula":
                showParameter(key, Form_key, this);
                break;
            case "aos_name":
                showParameter(key, "aos_act_type_cate", this);
                break;
            case "aos_rule_value":
                showParameter(key, "aos_act_type_se", this);
                break;
        }

    }

    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        super.propertyChanged(e);
        String name = e.getProperty().getName();
        if ("aos_project".equals(name)){
            int rowIndex = e.getChangeSet()[0].getRowIndex();
            setDay(rowIndex);
        }
    }

    private void setDay(int rowIndex){
        String value = getModel().getValue("aos_project", rowIndex).toString();
        if (FndGlobal.IsNull(value)){
            getModel().setValue("aos_def_day",0,rowIndex);
        }
        //国别日均
        else if ("countyAverage".equals(value)){
            getModel().setValue("aos_def_day",7,rowIndex);
        }
        else if ("historicalSales".equals(value)){
            getModel().setValue("aos_def_day",7,rowIndex);
        }
        else {
            getModel().setValue("aos_def_day",0,rowIndex);
        }
    }

    @Override
    public void closedCallBack(ClosedCallBackEvent event) {
        super.closedCallBack(event);
        String actionId = event.getActionId();
        Object redata =  event.getReturnData();
        if (redata==null)
            return;
        switch (actionId) {
            case "aos_rule": {
                Map<String, String> returnData = (Map<String, String>) redata;
                String key = returnData.get(Key_return_k);
                String name = returnData.get(Key_return_v);
                if (FndGlobal.IsNotNull(key)) {
                    try {
                        parseFormula(key);
                        getModel().setValue("aos_rule", name);
                        getModel().setValue("aos_rule_v", key);
                    } catch (Exception e) {
                        e.printStackTrace();
                        getView().showTipNotification("表达式输入有误");
                    }
                } else {
                    getModel().setValue("aos_rule", "");
                    getModel().setValue("aos_rule_v", "");
                }
                break;
            }
            case "aos_priceformula": {
                Map<String, String> returnData = (Map<String, String>) redata;
                String value = returnData.get(Key_return_k);
                if (FndGlobal.IsNotNull(value)) {
                    try {
                        FormulaEngine.parseFormula(value);
                        getModel().setValue("aos_priceformula", returnData.get(Key_return_v));
                        getModel().setValue("aos_priceformula_v", value);
                    } catch (Exception e) {
                        getView().showTipNotification("表达式输入有误");
                    }
                } else {
                    getModel().setValue("aos_priceformula", "");
                    getModel().setValue("aos_priceformula_v", "");
                }

                break;
            }
            case "aos_name":
                int index = this.getModel().getEntryCurrentRowIndex("aos_entryentity1");
                this.getModel().setValue("aos_name", redata.toString(), index);
                break;
        }
    }

    @Override
    public void beforeDoOperation(BeforeDoOperationEventArgs args) {
        super.beforeDoOperation(args);
        FormOperate formOperate = (FormOperate) args.getSource();
        if (formOperate.getOperateKey().equals("orgrule")){
            String message = syncOrgRule("1");
            if (FndGlobal.IsNotNull(message)){
                args.setCancel(true);
                getView().showTipNotification(message);
            }
        }
        else  if (formOperate.getOperateKey().equals("orgrulepr")){
            String message = syncOrgRule("2");
            if (FndGlobal.IsNotNull(message)){
                args.setCancel(true);
                getView().showTipNotification(message);
            }
        }
    }

    /**
     * 弹窗规则编辑框
     * @param type  类型
     */
    public static void showParameter(String type, String formKey, AbstractFormPlugin formPlugin){
        FormShowParameter showParameter = new FormShowParameter();
        IDataModel model = formPlugin.getView().getModel();
        //活动规则
        switch (type) {
            case "aos_rule":
                //获取活动规则数据
                DynamicObjectCollection dyc = model.getDataEntity(true).getDynamicObjectCollection("aos_entryentity2");
                if (dyc.size() == 0)
                    return;

                showParameter.setCustomParam(Key_entity, "rule");

                break;
            //活动价
            case "aos_priceformula":
                showParameter.setCustomParam(Key_entity, "price");
                break;
            case "aos_name":
                int index = model.getEntryCurrentRowIndex("aos_entryentity1");
                Object aos_cate = model.getValue("aos_cate", index);
                if (aos_cate == null) {
                    return;
                }
                String cateId = ((DynamicObject) aos_cate).getString("id");
                showParameter.setCustomParam("value", cateId);
                break;
        }

        //标识
        showParameter.setFormId(formKey);
        //类型
        showParameter.getOpenStyle().setShowType(ShowType.Modal);
        //回调函数
        showParameter.setCloseCallBack(new CloseCallBack(formPlugin,type));
        //弹窗
        formPlugin.getView().showForm(showParameter);
    }

    public static void parseFormula (String formula){
        String[] values = CalcExprParser.getExprVariables(formula);
        Map<String,Object> parameters = new HashMap<>();
        for (String value : values) {
            parameters.put(value,true);
        }
        FormulaEngine.execExcelFormula(formula,parameters);
    }


    /**
     * 同步国别规则
     * @param type  1:对应 规则集 ；2:对应 优先级
     * @return  成功和失败信息
     */
    private String syncOrgRule (String type){
        Object aos_org = this.getModel().getValue("aos_org");
        if (aos_org==null){
           return "请先选择国别";
        }
        String orgId = ((DynamicObject) aos_org).getString("id");
        //查找对应的国别规则
        QFBuilder qfBuilder = new QFBuilder();
        qfBuilder.add("aos_org","=",orgId);
        qfBuilder.add("enable","=",1);
        StringJoiner str = new StringJoiner(",");
        str.add("aos_entryentity.aos_project");
        str.add("aos_entryentity.aos_condite");
        str.add("aos_entryentity.aos_rule_value");
        str.add("aos_entryentity.aos_rule_day");
        str.add("aos_entryentity.seq");
        str.add("aos_rule");
        str.add("aos_rule_v");
        str.add("aos_entryentity4.aos_pr_project");
        str.add("aos_entryentity4.aos_pr_way");
        str.add("aos_entryentity4.aos_pr_weight");
        str.add("aos_entryentity4.aos_pr_day");
        str.add("aos_entryentity4.seq");

        //国别规则单据
        DynamicObject orgRuleEntity = BusinessDataServiceHelper.loadSingle("aos_sal_act_rule", str.toString(), qfBuilder.toArray());
        if (orgRuleEntity==null){
            return "未找到对应的国别规则";
        }

        if ("1".equals(type)){
            this.getModel().setValue("aos_rule",orgRuleEntity.get("aos_rule"));
            this.getModel().setValue("aos_rule_v",orgRuleEntity.get("aos_rule_v"));
            DynamicObjectCollection ruleRows = orgRuleEntity.getDynamicObjectCollection("aos_entryentity");
            DynamicObjectCollection theFormRuleRows = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity2");
            theFormRuleRows.removeIf(row->true);
            for (DynamicObject row : ruleRows) {
                DynamicObject addNewRow = theFormRuleRows.addNew();
                addNewRow.set("seq",row.get("seq"));
                addNewRow.set("aos_project",row.get("aos_project"));
                addNewRow.set("aos_condite",row.get("aos_condite"));
                addNewRow.set("aos_rule_value",row.get("aos_rule_value"));
                addNewRow.set("aos_rule_day",row.get("aos_rule_day"));
            }
            getView().updateView("aos_entryentity2");
        }
        else {
            DynamicObjectCollection deWeightRows = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity4");
            deWeightRows.removeIf(row->true);
            for (DynamicObject row : orgRuleEntity.getDynamicObjectCollection("aos_entryentity4")) {
                //将国别规则的去重规则同步到单据
                DynamicObject addNewRow = deWeightRows.addNew();
                addNewRow.set("seq",row.get("seq"));
                addNewRow.set("aos_pr_project",row.get("aos_pr_project"));
                addNewRow.set("aos_pr_way",row.get("aos_pr_way"));
                addNewRow.set("aos_pr_weight",row.get("aos_pr_weight"));
                addNewRow.set("aos_pr_day",row.get("aos_pr_day"));
            }
            getView().updateView("aos_entryentity4");
        }

        return null;
    }
}
