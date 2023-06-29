package mkt.act.rule.dotd;

import com.google.common.collect.ImmutableMap;
import common.Cux_Common_Utl;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.dataentity.OperateOption;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.entity.operate.result.IOperateInfo;
import kd.bos.entity.operate.result.OperationResult;
import kd.bos.exception.ErrorCode;
import kd.bos.exception.KDException;
import kd.bos.form.ClientProperties;
import kd.bos.form.container.Tab;
import kd.bos.form.control.EntryData;
import kd.bos.form.control.EntryGrid;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import sal.act.ActShopProfit.aos_sal_act_from;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * lch
 * 2022-6-23
 */
public class ActivityRuleBillPlugin extends AbstractBillPlugIn {

    @Override
    public void afterDoOperation(AfterDoOperationEventArgs args) {
        String operateKey = args.getOperateKey();
        boolean success = args.getOperationResult().isSuccess();
        if ("submit".equals(operateKey) && success) {
            writeBack();
        }
    }
    // 回写状态到活动计划和选品
    private void writeBack() {
        EntryGrid entryGrid = this.getControl("aos_entryentity");
        EntryData entryData = entryGrid.getEntryData();
        DynamicObject[] dataEntitys = entryData.getDataEntitys();
        long aos_source = (long) this.getModel().getValue("aos_source");
        //活动计划和选品表
        DynamicObject dy_actPlan = BusinessDataServiceHelper.loadSingle(aos_source, "aos_act_select_plan");
        Map<String, DynamicObject> map_actPlanRow = dy_actPlan.getDynamicObjectCollection("aos_sal_actplanentity")
                .stream()
                .collect(Collectors.toMap(
                        dy_row -> dy_row.getString("id"),
                        dy_row -> dy_row
                ));
        for (DynamicObject obj : dataEntitys) {
            String actPlanRowId = obj.getString("aos_orilineid");
            if (map_actPlanRow.containsKey(actPlanRowId)) {
                map_actPlanRow.get(actPlanRowId).set("aos_actprice",obj.get("aos_actprice"));
                map_actPlanRow.get(actPlanRowId).set("aos_profit",obj.get("aos_actprofitrate"));
                map_actPlanRow.get(actPlanRowId).set("aos_discountdegree",obj.get("aos_discountdegree"));
                String aos_cancel = obj.getString("aos_cancel");
                if ("N".equals(aos_cancel)) {
                    map_actPlanRow.get(actPlanRowId).set("aos_l_actstatus","B");
                }
            }
        }
        // 查询所有相同原单的销售确认单是否审核完毕
        DataSet dataSet = QueryServiceHelper.queryDataSet("ActivityRuleBillPlugin_writeBack", "aos_mkt_activityrule", "aos_source,case when billstatus = 'C' then 1 else 0 end as aos_confirm", new QFilter[]{
                new QFilter("aos_source", QCP.equals, aos_source)
        }, null);
        dataSet = dataSet.groupBy(new String[]{"aos_source"}).min("aos_confirm").finish();
        while (dataSet.hasNext()) {
            Row next = dataSet.next();
            Integer aos_confirm = next.getInteger("aos_confirm");
            if (aos_confirm != null && aos_confirm == 1) {
                // 全部销售确认完毕
                dy_actPlan.set("aos_confirm", true);
            }
        }
        OperationResult result = SaveServiceHelper.saveOperate("aos_act_select_plan", new DynamicObject[]{dy_actPlan}, OperateOption.create());
        if (result.isSuccess()){
            this.getView().showTipNotification("回写状态成功");
        }
        else{
            StringJoiner str = new StringJoiner(" ,");
            str.add("活动计划和选品表保存失败");
            for (IOperateInfo info : result.getAllErrorOrValidateInfo()) {
                str.add(info.getMessage());
            }
            throw new KDException(new ErrorCode("活动计划和选品表保存失败",str.toString()));
        }
    }

    @Override
    public void afterBindData(EventObject eventObject) {
        // 冻结产品信息
        EntryGrid entry = getView().getControl("aos_entryentity");
        Optional.ofNullable(entry).ifPresent(e -> e.setColumnProperty("aos_productinfo", ClientProperties.IsFixed, true));
        // 默认选中详细信息页签
        Tab tab = this.getControl("aos_tabap");
        tab.activeTab("aos_tabpageap1");
    }

    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();
        if (name.equals("aos_discountdegree")) {
            calActPriceAndProfit(e.getChangeSet()[0].getRowIndex());
        }
        else  if (name.equals("aos_actnum")){
            int rowIndex = e.getChangeSet()[0].getRowIndex();
            Object newValue = e.getChangeSet()[0].getNewValue();
            Object oldValue = e.getChangeSet()[0].getOldValue();
            setActQty(rowIndex,newValue,oldValue);
        }
    }
    /** 根据活动折扣计算活动价 **/
    public void calActPriceAndProfit(int row)  {
        if (row<0)
            return;
        String orgId = ((DynamicObject) this.getModel().getValue("aos_orgid")).getString("id");
        String shopId = ((DynamicObject) this.getModel().getValue("aos_shopid")).getString("id");
        LocalDate startdate = ((Date) this.getModel().getValue("aos_startdate")).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate enddate = ((Date) this.getModel().getValue("aos_enddate")).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

        DynamicObject dy_row = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_entryentity").get(row);
        String itemId = dy_row.getDynamicObject("aos_itemid").getString("id");
        BigDecimal bd_amPrice = dy_row.getBigDecimal("aos_currentprice");
        //则扣力度
        BigDecimal bd_discountdegree = dy_row.getBigDecimal("aos_discountdegree");
        //计算活动价格
        BigDecimal bd_actPrice = (BigDecimal.ONE.subtract(bd_discountdegree)).multiply(bd_amPrice).setScale(3,BigDecimal.ROUND_HALF_UP);
        //计算毛利率
        BigDecimal[] bd_ItemPrice = {bd_amPrice,bd_actPrice};
        Map<String,String> map_itemToDate = new HashMap<>();
        map_itemToDate.put(itemId,startdate.toString()+"/"+enddate.toString());
        Map<String, BigDecimal[]> map_itemToPrice = ImmutableMap.of(itemId, bd_ItemPrice);
        Map<String, Map<String, BigDecimal>> map_profit = aos_sal_act_from.get_formula(orgId, shopId, map_itemToDate, map_itemToPrice);
        BigDecimal bd_profit = BigDecimal.ZERO;
        if (map_profit.containsKey(itemId))
            bd_profit = map_profit.get(itemId).getOrDefault("value",BigDecimal.ZERO);
        this.getModel().setValue("aos_actprofitrate",bd_profit,row);
        this.getModel().setValue("aos_actprice",bd_actPrice,row);
    }

    /** 活动数量校验 **/
    public void setActQty(int row,Object newValue,Object oldValue){
        if (row<0)
            return;
        int newV = 0;
        if (!Cux_Common_Utl.IsNull(newValue))
            newV = (Integer) newValue;
        int oldV = 0;
        if (!Cux_Common_Utl.IsNull(oldValue))
            oldV = (Integer) oldValue;
        if (newV < oldV){
            this.getModel().setValue("aos_actnum",oldV,row);
            this.getView().showTipNotification("活动数量不能比之前小");
        }
    }
}
