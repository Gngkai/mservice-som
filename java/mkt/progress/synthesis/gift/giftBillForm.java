package mkt.progress.synthesis.gift;

import common.fnd.FndGlobal;
import common.sal.sys.sync.dao.ItemCostDao;
import common.sal.sys.sync.dao.impl.ItemCostDaoImpl;
import common.sal.util.QFBuilder;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.form.field.BasedataEdit;
import kd.bos.form.field.events.BeforeF7SelectEvent;
import kd.bos.form.field.events.BeforeF7SelectListener;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.workflow.WorkflowServiceHelper;
import kd.bos.workflow.api.BizProcessStatus;

import java.math.BigDecimal;
import java.util.EventObject;
import java.util.List;
import java.util.Map;

/**
 * @author: GK
 * @create: 2024-01-24 17:38
 * @Description:赠品流程界面插件
 */
@SuppressWarnings("unused")
public class giftBillForm extends AbstractFormPlugin implements BeforeF7SelectListener {
    @Override
    public void afterCreateNewData(EventObject e) {
        super.afterCreateNewData(e);
        setUserNerve();
    }

    @Override
    public void afterBindData(EventObject e) {
        super.afterBindData(e);
        stateControl();
    }

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        BasedataEdit basedataEdit = this.getControl("aos_item");
        basedataEdit.addBeforeF7SelectListener(this);
        basedataEdit = this.getControl("aos_shop");
        basedataEdit.addBeforeF7SelectListener(this);
    }

    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        super.propertyChanged(e);
        String name = e.getProperty().getName();
        int rowIndex = e.getChangeSet()[0].getRowIndex();
        //物料修改
        if ("aos_item".equals(name))
        {
            if (rowIndex>=0){
                setItemCost(rowIndex);
            }
        }
        else if ("aos_order".equals(name)){
            if (rowIndex>=0){
                setFeeOrder(rowIndex);
            }
        }
    }

    @Override
    public void beforeF7Select(BeforeF7SelectEvent event) {
        String name = event.getProperty().getName();
        if ("aos_item".equals(name)) {
            Object org = this.getModel().getValue("aos_org");
            if (org!=null){
                DynamicObject dy = (DynamicObject) org;
                List<QFilter> customQFilters = event.getCustomQFilters();
                QFilter filter = new QFilter("aos_contryentry.aos_nationality","=",dy.getPkValue());
                customQFilters.add(filter);
            }
        }
        else if ("aos_shop".equals(name)){
            Object org = this.getModel().getValue("aos_org");
            Object channel = this.getModel().getValue("aos_channel");
            List<QFilter> customQFilters = event.getCustomQFilters();
            if (org!=null  ){
                DynamicObject orgEntry = (DynamicObject) org;
                QFilter orgFilter = new QFilter("aos_org", "=", orgEntry.getPkValue());
                customQFilters.add(orgFilter);

            }
            if (channel!=null){
                DynamicObject channelEntry = (DynamicObject) channel;
                QFilter channelFilter = new QFilter("aos_channel", "=", channelEntry.getPkValue());
                customQFilters.add(channelFilter);
            }
        }
    }

    /**
     * 新建时，设置组织
     */
    private void setUserNerve(){
        DynamicObject creatorEntry = (DynamicObject) getModel().getValue("creator");
        DynamicObjectCollection sectorEntryRows = creatorEntry.getDynamicObjectCollection("entryentity");
        if (sectorEntryRows.size()>0){
            DynamicObject dpt = sectorEntryRows.get(0).getDynamicObject("dpt");
            this.getModel().setValue("aos_nerve",dpt.getPkValue());
        }
    }

    private void stateControl(){
        getView().setEnable(false,-1,"aos_order");
        getView().setVisible(false,"aos_ordercon");
        //判断单据是否在工作流中，且在订单录入节点
        String billId = getModel().getValue("id").toString();
        if (WorkflowServiceHelper.inProcess(billId)) {
            Map<String, List<BizProcessStatus>> map = WorkflowServiceHelper.getBizProcessStatus(new String[]{billId});
            List<BizProcessStatus> processStatus = map.get(billId);
            if ("订单录入".equals(processStatus.get(0).getCurrentNodeName())) {
                getView().setEnable(true,-1,"aos_order");
                getView().setVisible(true,"aos_ordercon");
            }
        }
    }

    /**
     * 物料修改，带出费用
     */
    private void setItemCost(int rowIndex){
        Object item = getModel().getValue("aos_item", rowIndex);
        Object org = getModel().getValue("aos_org");
        if ((org == null) || (item == null)){
            getModel().setValue("aos_cost",0,rowIndex);
        }
        else {
            ItemCostDao itemCostDao = new ItemCostDaoImpl();
            String orgId = ((DynamicObject) org).getString("id");
            String itemId = ((DynamicObject) getModel().getValue("aos_item", rowIndex)).getString("id");
            //最新成本
            BigDecimal latestCost = itemCostDao.getItemLatestCost(Long.parseLong(orgId), Long.parseLong(itemId));
            //快递费
            QFBuilder qfBuilder = new QFBuilder();
            qfBuilder.add("aos_orgid","=",orgId);
            qfBuilder.add("aos_itemid","=",itemId);
            qfBuilder.add("aos_suittype.number","=","Aosom");
            qfBuilder.add("aos_dflag","=","Y");
            DynamicObject feeResult = QueryServiceHelper.queryOne("aos_courier_inquiry", "aos_rev_fee", qfBuilder.toArray());
            BigDecimal fee = BigDecimal.ZERO;
            if (feeResult!=null) {
                fee = feeResult.getBigDecimal("aos_rev_fee");
            }
            latestCost = latestCost.add(fee);
            //获取数量
            int  qty = (int) getModel().getValue("aos_qty", rowIndex);
            latestCost = latestCost.multiply(new BigDecimal(qty));
            this.getModel().setValue("aos_cost",latestCost,rowIndex);
        }
    }

    /**
     * 设置快递单号
     * @param rowIndex
     */
    private void setFeeOrder(int rowIndex){
        Object order = getModel().getValue("aos_order", rowIndex);
        if (FndGlobal.IsNull(order)){
            getModel().setValue("aos_feeno","",rowIndex);
        }
        else {
            QFBuilder builder = new QFBuilder();
            builder.add("aos_sync_billid","=",order.toString());
            DynamicObject results = QueryServiceHelper.queryOne("aos_lms_exp_base_track", "aos_track_no", builder.toArray());
            if (results!=null){
                getModel().setValue("aos_feeno",results.getString("aos_track_no"),rowIndex);
            }
        }
    }
}
