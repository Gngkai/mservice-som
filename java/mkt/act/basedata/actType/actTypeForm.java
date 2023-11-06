package mkt.act.basedata.actType;

import common.fnd.FndGlobal;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.formula.CalcExprParser;
import kd.bos.form.CloseCallBack;
import kd.bos.form.FormShowParameter;
import kd.bos.form.ShowType;
import kd.bos.form.control.Control;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.form.field.BasedataEdit;
import kd.bos.form.field.events.BeforeF7SelectEvent;
import kd.bos.form.field.events.BeforeF7SelectListener;
import kd.bos.formula.FormulaEngine;
import kd.bos.list.ListShowParameter;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import common.sal.util.QFBuilder;
import sal.act.ActShopProfit.aos_sal_ShopActProfit_form;

import java.util.*;

/**
 * @author create by gk
 * @date 2023/8/17 16:02
 * @action 活动库界面插件
 */
public class actTypeForm extends AbstractBillPlugIn implements BeforeF7SelectListener {
    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        BasedataEdit control = this.getControl("aos_shop");
        control.addBeforeF7SelectListener(this);

        control = this.getControl("aos_re_shop");
        control.addBeforeF7SelectListener(this);

        control = this.getControl("aos_re_act");
        control.addBeforeF7SelectListener(this);

        this.addClickListeners(new String[]{"aos_rule","aos_priceformula","aos_name"});

    }

    @Override
    public void beforeF7Select(BeforeF7SelectEvent beforeF7SelectEvent) {
        ListShowParameter showParameter = (ListShowParameter) beforeF7SelectEvent.getFormShowParameter();
        String F7name = beforeF7SelectEvent.getProperty().getName();
        if (F7name.equals("aos_shop")){
            List<QFilter> filters = new ArrayList<>();
            if (getModel().getValue("aos_org")!=null){
                DynamicObject org = (DynamicObject) getModel().getValue("aos_org");
                QFilter filter = new QFilter("aos_org","=",org.getString("id"));
                filters.add(filter);
            }
            if (getModel().getValue("aos_channel")!=null){
                DynamicObject channel = (DynamicObject) getModel().getValue("aos_channel");
                QFilter filter = new QFilter("aos_channel","=",channel.getString("id"));
                filters.add(filter);
            }
            showParameter.getListFilterParameter().setQFilters(filters);
        }
        else if (F7name.equals("aos_re_shop")){
            List<QFilter> filters = new ArrayList<>();
            if (getModel().getValue("aos_org")!=null){
                DynamicObject org = (DynamicObject) getModel().getValue("aos_org");
                QFilter filter = new QFilter("aos_org","=",org.getString("id"));
                filters.add(filter);
            }
            int index = this.getModel().getEntryCurrentRowIndex("aos_entryentity3");
            if (getModel().getValue("aos_re_channel",index)!=null){
                DynamicObject channel = (DynamicObject) getModel().getValue("aos_re_channel",index);
                QFilter filter = new QFilter("aos_channel","=",channel.getString("id"));
                filters.add(filter);
            }
            showParameter.getListFilterParameter().setQFilters(filters);
        }
        else if (F7name.equals("aos_re_act")){
            List<QFilter> filters = new ArrayList<>();
            if (getModel().getValue("aos_org")!=null){
                DynamicObject org = (DynamicObject) getModel().getValue("aos_org");
                QFilter filter = new QFilter("aos_org","=",org.getString("id"));
                filters.add(filter);
            }
            int index = this.getModel().getEntryCurrentRowIndex("aos_entryentity3");
            if (getModel().getValue("aos_re_channel",index)!=null){
                DynamicObject channel = (DynamicObject) getModel().getValue("aos_re_channel",index);
                QFilter filter = new QFilter("aos_channel","=",channel.getString("id"));
                filters.add(filter);
            }
            if (getModel().getValue("aos_re_shop",index)!=null){
                DynamicObject shop = (DynamicObject) getModel().getValue("aos_re_shop",index);
                QFilter filter = new QFilter("aos_shop","=",shop.getString("id"));
                filters.add(filter);
            }
            DynamicObjectCollection dyc = QueryServiceHelper.query("aos_sal_act_type_p", "aos_acttype", filters.toArray(new QFilter[0]));
            List<String> ids = new ArrayList<>(dyc.size());
            for (DynamicObject dy : dyc) {
                ids.add(dy.getString("aos_acttype"));
            }
            filters.clear();
            filters.add(new QFilter("id",QFilter.in,ids));
            showParameter.getListFilterParameter().setQFilters(filters);
        }
    }

    @Override
    public void click(EventObject evt) {
        super.click(evt);
        Control control= (Control) evt.getSource();
        String key= control.getKey();
        if (key.equals("aos_rule")) {
          showParameter(key,aos_sal_ShopActProfit_form.Form_key);
        }
        else if (key.equals("aos_priceformula")){
            showParameter(key,aos_sal_ShopActProfit_form.Form_key);
        }
        else if (key.equals("aos_name")){
            showParameter(key,"aos_act_type_cate");
        }
    }

    @Override
    public void closedCallBack(ClosedCallBackEvent event) {
        super.closedCallBack(event);
        String actionId = event.getActionId();
        Object redata =  event.getReturnData();
        if (redata==null)
            return;
        if (actionId.equals("aos_rule")) {
            Map<String,String> returnData = (Map<String, String>) redata;
            String key = returnData.get(aos_sal_ShopActProfit_form.Key_return_k);
            String name = returnData.get(aos_sal_ShopActProfit_form.Key_return_v);
            if (FndGlobal.IsNotNull(key)){
                try {
                    parseFormula(key);
                    getModel().setValue("aos_rule",name);
                    getModel().setValue("aos_rule_v",key);
                }
                catch (Exception e){
                    e.printStackTrace();
                    getView().showTipNotification("表达式输入有误");
                }
            }
            else{
                getModel().setValue("aos_rule","");
                getModel().setValue("aos_rule_v","");
            }
        }
        else if (actionId.equals("aos_priceformula")){
            Map<String,String> returnData = (Map<String, String>) redata;
            String value = returnData.get(aos_sal_ShopActProfit_form.Key_return_k);
            if (FndGlobal.IsNotNull(value)){
                try {
                    FormulaEngine.parseFormula(value);
                    getModel().setValue("aos_priceformula",returnData.get(aos_sal_ShopActProfit_form.Key_return_v));
                    getModel().setValue("aos_priceformula_v",value);
                }
                catch (Exception e){
                    getView().showTipNotification("表达式输入有误");
                }
            }
            else{
                getModel().setValue("aos_priceformula","");
                getModel().setValue("aos_priceformula_v","");
            }

        }
        else if (actionId.equals("aos_name")){
            int index = this.getModel().getEntryCurrentRowIndex("aos_entryentity1");
            this.getModel().setValue("aos_name",redata.toString(),index);
        }
    }

    /**
     * 弹窗规则编辑框
     * @param type
     */
    private void showParameter(String type,String formKey){
        FormShowParameter showParameter=new FormShowParameter();
        //活动规则
        if (type.equals("aos_rule")){
            //获取活动规则数据
            DynamicObjectCollection dyc = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity2");
            if (dyc.size()==0)
                return;
            List<String> values = new ArrayList<>(dyc.size());
            for (DynamicObject dy : dyc) {
                String seq = dy.getString("seq");
                values.add(seq);
            }
            showParameter.setCustomParam(aos_sal_ShopActProfit_form.Key_entity,"rule");
            showParameter.setCustomParam("value",values);
        }
        //活动价
        else if (type.equals("aos_priceformula")){
            showParameter.setCustomParam(aos_sal_ShopActProfit_form.Key_entity,"price");
        }
        else if (type.equals("aos_name")){
            int index = this.getModel().getEntryCurrentRowIndex("aos_entryentity1");
            Object aos_cate = this.getModel().getValue("aos_cate", index);
            if (aos_cate==null) {
                return;
            }
            String cateId = ((DynamicObject) aos_cate).getString("id");
            showParameter.setCustomParam("value",cateId);
        }

        //标识
        showParameter.setFormId(formKey);
        //类型
        showParameter.getOpenStyle().setShowType(ShowType.Modal);
        //回调函数
        showParameter.setCloseCallBack(new CloseCallBack(this,type));
        //弹窗
        this.getView().showForm(showParameter);
    }

    private void parseFormula (String formula){
        String[] values = CalcExprParser.getExprVariables(formula);
        Map<String,Object> parameters = new HashMap<>();
        for (String value : values) {
            parameters.put(value,true);
        }
        FormulaEngine.execExcelFormula(formula,parameters);
    }
}
