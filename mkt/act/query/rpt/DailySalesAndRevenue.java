package mkt.act.query.rpt;

import common.sal.permission.PermissionUtil;
import common.sal.sys.basedata.dao.ShopDao;
import common.sal.sys.basedata.dao.impl.ShopImpl;
import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.JoinType;
import kd.bos.algo.Row;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.datamodel.AbstractFormDataModel;
import kd.bos.entity.datamodel.TableValueSetter;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.field.BasedataEdit;
import kd.bos.form.field.events.BeforeF7SelectEvent;
import kd.bos.form.field.events.BeforeF7SelectListener;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.EventObject;
import java.util.List;

public class DailySalesAndRevenue extends AbstractFormPlugin implements BeforeF7SelectListener {
    private static final String BUTTON_INQUIRE = "aos_inquire";
    private static final String ENTRY_ENTITY = "aos_entryentity";

    private static final String QUERY_FIELD_ORG = "aos_orgid_h";
    private static final String QUERY_FIELD_PLATFORM = "aos_platformid_h";
    private static final String QUERY_FIELD_SHOP = "aos_shopid_h";
    private static final String QUERY_FIELD_START_DATE = "aos_startdate";
    private static final String QUERY_FIELD_END_DATE = "aos_enddate";

    private static final String BILL_REVIEW = "aos_sync_review";
    private static final String BILL_ORDER_LINE = "aos_sync_om_order_r";

    @Override
    public void registerListener(EventObject e) {
        BasedataEdit shopBaseData = this.getControl(QUERY_FIELD_SHOP);
        shopBaseData.addBeforeF7SelectListener(this);

        BasedataEdit orgBaseData = this.getControl(QUERY_FIELD_ORG);
        orgBaseData.addBeforeF7SelectListener(this);
    }

    @Override
    public void beforeF7Select(BeforeF7SelectEvent f7SelectEvent) {
        String name = f7SelectEvent.getProperty().getName();
        if (QUERY_FIELD_SHOP.equals(name)) {
            // 根据国别和渠道过滤店铺
            String orgId = getOrgId();
            String platFormId = getPlatFormId();
            f7SelectEvent.addCustomQFilter(new QFilter("aos_org", QCP.equals, orgId));
            f7SelectEvent.addCustomQFilter(new QFilter("aos_channel", QCP.equals, platFormId));
        }

        if (QUERY_FIELD_ORG.equals(name)) {
            QFilter idQFilter = PermissionUtil.getOrgQFilterForSale(UserServiceHelper.getCurrentUserId(), "id");
            f7SelectEvent.addCustomQFilter(idQFilter);
        }
    }

    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        String name = e.getProperty().getName();

        // 国别或店铺改变 清空店铺
        if (QUERY_FIELD_ORG.equals(name) || QUERY_FIELD_PLATFORM.equals(name)) {
            this.getModel().setValue(QUERY_FIELD_SHOP, null);
        }
    }

    @Override
    public void afterDoOperation(AfterDoOperationEventArgs args) {
        String operateKey = args.getOperateKey();
        if (BUTTON_INQUIRE.equals(operateKey)) {
            inquire();
        }
    }

    private void inquire() {
        Date endDate = (Date) this.getModel().getValue(QUERY_FIELD_END_DATE);
        Date startDate = (Date) this.getModel().getValue(QUERY_FIELD_START_DATE);
        if (endDate == null || startDate == null) {
            this.getView().showTipNotification("日期范围必填");
            return;
        }
        String aos_orgid = getOrgId();
        String aos_platformid = getPlatFormId();
        String aos_shopid = getShopId();

        int queryType = getQueryType(aos_orgid, aos_platformid, aos_shopid);

        // 界面赋值字段
        List<String> fieldList = new ArrayList<>();
        fieldList.add("aos_orgid");
        fieldList.add("aos_itemid");
        fieldList.add("aos_brandid");
        fieldList.add("aos_asin");
        fieldList.add("aos_stars");
        fieldList.add("aos_reviewnum");
        fieldList.add("aos_localdate");
        fieldList.add("aos_totalamt");
        fieldList.add("aos_orderqty");

        String selectFields = "aos_org aos_orgid," +
                "aos_item_fid aos_itemid," +
                "aos_item_fid.aos_contryentry.aos_contrybrand aos_brandid," +
                "aos_item_fid.aos_contryentry.aos_nationality aos_nationality," +
                "aos_totalamt," +
                "aos_order_qty aos_orderqty," +
                "to_char(aos_local_date, 'yyyy-mm-dd') aos_localdate";
        boolean sameDay = DateUtils.isSameDay(startDate, endDate);
        if (sameDay) {
            endDate = DateUtils.addDays(endDate, 1);
        }
        QFilter qFilter1 = new QFilter("aos_local_date", QCP.large_equals, DateFormatUtils.format(startDate, "yyyy-MM-dd"));
        qFilter1.and(new QFilter("aos_local_date", QCP.less_equals, DateFormatUtils.format(endDate, "yyyy-MM-dd")));
        // 分组字段
        String[] groupByField = {"aos_orgid", "aos_itemid", "aos_brandid","aos_localdate"};
        if (queryType == 2) {
            qFilter1.and(new QFilter("aos_org", QCP.equals, aos_orgid));
//            qFilter1.and(new QFilter("aos_item_fid.aos_contryentry.aos_nationality", QCP.equals, Long.parseLong(aos_orgid)));
        }

        if (3 == queryType) {
            qFilter1.and(new QFilter("aos_org", QCP.equals, aos_orgid));
//            qFilter1.and(new QFilter("aos_item_fid.aos_contryentry.aos_nationality", QCP.equals, Long.parseLong(aos_orgid)));
            qFilter1.and(new QFilter("aos_platformfid", QCP.equals, Long.parseLong(aos_platformid)));
            fieldList.add("aos_platformid");
            groupByField = new String[]{"aos_orgid", "aos_platformid", "aos_itemid", "aos_brandid", "aos_localdate"};
            selectFields = "aos_org aos_orgid," +
                    "aos_platformfid aos_platformid," +
                    "aos_item_fid aos_itemid," +
                    "aos_item_fid.aos_contryentry.aos_contrybrand aos_brandid," +
                    "aos_item_fid.aos_contryentry.aos_nationality aos_nationality," +
                    "aos_totalamt," +
                    "aos_order_qty aos_orderqty," +
                    "to_char(aos_local_date, 'yyyy-mm-dd') aos_localdate";
        }

        if (4 == queryType) {
            qFilter1.and(new QFilter("aos_org", QCP.equals, aos_orgid));
//            qFilter1.and(new QFilter("aos_item_fid.aos_contryentry.aos_nationality", QCP.equals, Long.parseLong(aos_orgid)));
            qFilter1.and(new QFilter("aos_platformfid", QCP.equals, Long.parseLong(aos_platformid)));
            qFilter1.and(new QFilter("aos_shopfid", QCP.equals, aos_shopid));
            fieldList.add("aos_platformid");
            fieldList.add("aos_shopid");
            groupByField = new String[]{"aos_orgid", "aos_platformid", "aos_shopid", "aos_itemid", "aos_brandid","aos_localdate"};

            selectFields = "aos_org aos_orgid," +
                    "aos_platformfid aos_platformid," +
                    "aos_shopfid aos_shopid," +
                    "aos_item_fid aos_itemid," +
                    "aos_item_fid.aos_contryentry.aos_contrybrand aos_brandid," +
                    "aos_item_fid.aos_contryentry.aos_nationality aos_nationality," +
                    "aos_totalamt," +
                    "aos_order_qty aos_orderqty," +
                    "to_char(aos_local_date, 'yyyy-mm-dd') aos_localdate";
        }
        DataSet dataSet = queryOrderBill(selectFields, qFilter1);
        if (!"".equals(aos_orgid)) {
            dataSet = dataSet.where("aos_nationality = " + aos_orgid);
        }

        dataSet = dataSet.groupBy(groupByField).sum("aos_totalamt").sum("aos_orderqty").finish().orderBy(new String[]{"aos_localdate desc"});


        String selectFields4 = "aos_orgid, aos_itemid, aos_asin,aos_review aos_reviewnum, aos_stars";
        QFilter qFilter4 = new QFilter("aos_orgid", QCP.is_notnull, null);
        if (queryType == 2) {
            qFilter4.and(new QFilter("aos_orgid", QCP.equals, aos_orgid));
        }
        DataSet dataSet4 = queryReview(selectFields4, qFilter4);
        dataSet = dataSet.join(dataSet4, JoinType.LEFT)
                .on("aos_orgid", "aos_orgid")
                .on("aos_itemid", "aos_itemid")
                .select(fieldList.toArray(new String[0]))
                .finish();
        TableValueSetter tableValueSetter = handleDataToSetter(dataSet, fieldList);
        setEntryEntity(tableValueSetter);
        dataSet.close();
        dataSet4.close();
    }

    private DataSet queryOrderBill(String selectFields, QFilter qFilter) {
        String algoKey = this.getClass().getName()+ "queryOrderBill";
        List<QFilter> orderFilter = SalUtil.getOrderFilter();   //订单通用过滤
        qFilter = qFilter.and(orderFilter.get(0));
        return QueryServiceHelper.queryDataSet(algoKey, BILL_ORDER_LINE, selectFields, qFilter.toArray(), null);
    }

    private DataSet queryReview(String selectFields, QFilter qFilter) {
        String algoKey = this.getClass().getName()+ "queryItem";
        return QueryServiceHelper.queryDataSet(algoKey, BILL_REVIEW, selectFields, qFilter.toArray(), null);
    }

    private TableValueSetter handleDataToSetter(DataSet dataSet, List<String> fieldList) {
        //查询所有的亚马逊主店铺
        ShopDao shopDao = new ShopImpl();
        List<String> list_amazonShop = shopDao.AmazonShop();
        TableValueSetter tableValueSetter = new TableValueSetter();
        int count = 0;
        while (dataSet.hasNext()) {
            Row next = dataSet.next();
            String aos_shopid = next.getString("aos_shopid");
            boolean whetherAmaozon = list_amazonShop.contains(aos_shopid);
            for (String field : fieldList) {
                if (  field.equals("aos_asin") && field.equals("aos_stars")){
                    if (whetherAmaozon)
                        tableValueSetter.set(field, next.get(field), count);
                    else
                        tableValueSetter.set(field, null, count);
                }
                else
                    tableValueSetter.set(field, next.get(field), count);
            }
            count++;
        }
        return tableValueSetter;
    }

    private void setEntryEntity(TableValueSetter setter) {
        AbstractFormDataModel model = (AbstractFormDataModel) this.getModel();
        model.deleteEntryData(ENTRY_ENTITY);
        //开启事务
        model.beginInit();
        model.batchCreateNewEntryRow(ENTRY_ENTITY, setter);
        //关闭事务
        model.endInit();
        this.getView().updateView(ENTRY_ENTITY);
    }


    private int getQueryType(String aos_orgid, String aos_platformid, String aos_shopid) {
        // 任何查询字段都没填
        if ("".equals(aos_orgid) && "".equals(aos_platformid) && "".equals(aos_shopid)) {
            return 1;
        }
        // 只填了国别
        else if (!"".equals(aos_orgid) && "".equals(aos_platformid) && "".equals(aos_shopid)) {
            return 2;
        }
        // 只填了国别和渠道
        else if (!"".equals(aos_orgid) && !"".equals(aos_platformid) && "".equals(aos_shopid)) {
            return 3;
        }
        // 国别渠道店铺都填
        else {
            return 4;
        }
    }

    private String getShopId() {
        return getBaseDataId(QUERY_FIELD_SHOP);
    }
    private String getPlatFormId() {
        return getBaseDataId(QUERY_FIELD_PLATFORM);
    }
    private String getOrgId() {
        return getBaseDataId(QUERY_FIELD_ORG);
    }
    private String getBaseDataId(String field) {
        DynamicObject object = (DynamicObject) this.getModel().getValue(field);
        return object == null ? "" : object.getString("id");
    }

}
