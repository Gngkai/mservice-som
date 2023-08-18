package mkt.act.basedata.actType;

import common.fnd.FndGlobal;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
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
import sal.act.ActShopProfit.aos_sal_ShopActProfit_form;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import java.util.Map;

/**
 * @author create by gk
 * @date 2023/8/17 16:02
 * @action 活动库界面插件
 */
public class actTypeForm extends AbstractBillPlugIn implements BeforeF7SelectListener {
    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        BasedataEdit aos_shop = this.getControl("aos_shop");
        aos_shop.addBeforeF7SelectListener(this);
        this.addClickListeners(new String[]{"aos_rule","aos_priceformula"});

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
    }

    @Override
    public void click(EventObject evt) {
        super.click(evt);
        Control control= (Control) evt.getSource();
        String key= control.getKey();
        if (key.equals("aos_rule")) {
          showParameter(key);
        }
        else if (key.equals("aos_priceformula")){
            showParameter(key);
        }

    }

    @Override
    public void closedCallBack(ClosedCallBackEvent event) {
        super.closedCallBack(event);
        String actionId = event.getActionId();
        Map<String,String> returnData = (Map<String, String>) event.getReturnData();
        if (returnData==null)
            return;
        if (actionId.equals("aos_rule")) {
            String value = returnData.get(aos_sal_ShopActProfit_form.Key_return_k);
            if (FndGlobal.IsNotNull(value)){
                try {
                    FormulaEngine.parseFormula(value);
                    getModel().setValue("aos_rule",value);
                }
                catch (Exception e){
                    getView().showTipNotification("表达式输入有误");
                }
            }
            else{
                getModel().setValue("aos_rule","");
            }
        }
        else if (actionId.equals("aos_priceformula")){
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
    }

    /**
     * 弹窗规则编辑框
     * @param type
     */
    private void showParameter(String type){
        FormShowParameter showParameter=new FormShowParameter();
        //活动规则
        if (type.equals("aos_rule")){
            //获取活动规则数据
            DynamicObjectCollection dyc = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity2");
            if (dyc.size()==0)
                return;
            List<String> values = new ArrayList<>(dyc.size());
            for (DynamicObject dy : dyc) {
                values.add("项目"+dy.getString("seq"));
            }
            showParameter.setCustomParam(aos_sal_ShopActProfit_form.Key_entity,"rule");
            showParameter.setCustomParam("value",values);
        }
        //活动价
        else if (type.equals("aos_priceformula")){
            showParameter.setCustomParam(aos_sal_ShopActProfit_form.Key_entity,"price");
        }

        //标识
        showParameter.setFormId(aos_sal_ShopActProfit_form.Form_key);
        //类型
        showParameter.getOpenStyle().setShowType(ShowType.Modal);
        //回调函数
        showParameter.setCloseCallBack(new CloseCallBack(this,type));
        //弹窗
        this.getView().showForm(showParameter);
    }

}
