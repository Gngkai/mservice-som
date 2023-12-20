package mkt.act.basedata.actType;

import common.fnd.FndGlobal;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.formula.CalcExprParser;
import kd.bos.form.CloseCallBack;
import kd.bos.form.FormShowParameter;
import kd.bos.form.ShowType;
import kd.bos.form.control.Control;
import kd.bos.form.events.ClosedCallBackEvent;
import kd.bos.formula.FormulaEngine;

import java.util.EventObject;
import java.util.HashMap;
import java.util.Map;

/**
 * @author: GK
 * @create: 2023-11-27 17:10
 * @Description:
 */
public class actRuleForm extends AbstractBillPlugIn {
    public  static final String Form_key="aos_sal_shop_actprofit";  //界面标识
    public  static final String Key_return_k="re_v";    //回传公式标识
    public  static final String Key_return_v="re_k";    //回传公式名称

    public  static final String Key_entity="operate";   //父界面传值标识

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        this.addClickListeners(new String[]{"aos_rule"});
    }
    @Override
    public void click(EventObject evt) {
        super.click(evt);
        Control control= (Control) evt.getSource();
        String key= control.getKey();
        if (key.equals("aos_rule")) {
            showParameter(key,Form_key);
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
            DynamicObjectCollection dyc = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity");
            if (dyc.size()==0)
                return;

            showParameter.setCustomParam(Key_entity,"rule");

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

    @Override
    public void closedCallBack(ClosedCallBackEvent event) {
        super.closedCallBack(event);
        String actionId = event.getActionId();
        Object redata =  event.getReturnData();
        if (redata==null)
            return;
        if (actionId.equals("aos_rule")) {
            Map<String,String> returnData = (Map<String, String>) redata;
            String key = returnData.get(Key_return_k);
            String name = returnData.get(Key_return_v);
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
