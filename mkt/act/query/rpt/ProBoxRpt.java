package mkt.act.query.rpt;

import common.sal.permission.PermissionUtil;
import common.sal.util.SalUtil;
import kd.bos.algo.DataSet;
import kd.bos.algo.JoinType;
import kd.bos.algo.Row;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.datamodel.AbstractFormDataModel;
import kd.bos.entity.datamodel.TableValueSetter;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.field.events.BeforeF7SelectEvent;
import kd.bos.form.field.events.BeforeF7SelectListener;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.user.UserServiceHelper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ProBoxRpt extends AbstractFormPlugin implements BeforeF7SelectListener {
    private static final String BUTTON_INQUIRE = "aos_inquire";
    private static final String ENTRY_ENTITY = "aos_entryentity";
    private static final String QUERY_FIELD_ORG = "aos_orgid_h";
    private static final String BILL_ITEM = "bd_material";
    @Override
    public void beforeF7Select(BeforeF7SelectEvent f7SelectEvent) {
        String name = f7SelectEvent.getProperty().getName();
        if (QUERY_FIELD_ORG.equals(name)) {
            QFilter idQFilter = PermissionUtil.getOrgQFilterForSale(UserServiceHelper.getCurrentUserId(), "id");
            f7SelectEvent.addCustomQFilter(idQFilter);
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
        String orgId = getOrgId();
        QFilter qFilter = new QFilter("aos_contryentry.aos_nationality", QCP.is_notnull, null);
        if (!"".equals(orgId)) {
            qFilter.and(new QFilter("aos_contryentry.aos_nationality", QCP.equals, orgId));//国别
        }
        qFilter.and(new QFilter("aos_protype", QCP.equals, "N"));//正常产品
        qFilter.and(new QFilter("aos_type", QCP.equals, "A"));//类别为“产品”
        qFilter.and(new QFilter("aos_iscomb", QCP.equals, "0"));//不为组合产品
        qFilter.and(new QFilter("aos_boxentry.aos_length", QCP.is_notnull, null));
        qFilter.and(new QFilter("aos_boxentry.aos_width", QCP.is_notnull, null));
        qFilter.and(new QFilter("aos_boxentry.aos_height", QCP.is_notnull, null));
        String algoKey = this.getClass().getName() + "inquire";
        String selectFields = "aos_contryentry.aos_nationality aos_orgid," +
                "id aos_itemid," +
                "aos_boxentry.seq aos_seq," +
                "aos_boxentry.aos_qty aos_boxqty," +
                "aos_boxentry.aos_length aos_length," +
                "aos_boxentry.aos_width aos_width," +
                "aos_boxentry.aos_height aos_height," +
                "aos_boxentry.aos_sizeunit.number aos_sizeunit," +
                "aos_boxentry.aos_volumeunit.number aos_volumeunit," +
                "aos_boxentry.aos_weightunit.number aos_weightunit," +
                "aos_boxentry.aos_grossweight aos_grossweight," +
                "aos_boxentry.aos_netweights aos_netweights," +
                "aos_boxentry.aos_volume aos_volume";
        DataSet dataSet = QueryServiceHelper.queryDataSet(algoKey, BILL_ITEM, selectFields, qFilter.toArray(), "aos_contryentry.aos_nationality,id");
        DataSet dataSet1 = QueryServiceHelper.queryDataSet(algoKey, BILL_ITEM, "id aos_itemid , aos_boxentry.id aos_boxentryid,1 as aos_count", null, null);
        dataSet1 = dataSet1.groupBy(new String[]{"aos_itemid"}).sum("aos_count").finish();
        dataSet = dataSet
                .join(dataSet1, JoinType.INNER)
                .on("aos_itemid", "aos_itemid")
                .select("aos_orgid","aos_itemid", "aos_seq","aos_boxqty","aos_length", "aos_width", "aos_height", "aos_grossweight", "aos_netweights", "aos_sizeunit", "aos_volumeunit", "aos_weightunit","aos_volume","aos_count")
                .finish();
        dataSet = dataSet.where("aos_orgid is not null and aos_orgid != 0");
        TableValueSetter tableValueSetter = handleDataToSetter(dataSet);
        setEntryEntity(tableValueSetter);
        dataSet1.close();
    }


    private TableValueSetter handleDataToSetter(DataSet dataSet) {
        String aos_sizeunit = getUnit("aos_sizeunit");
        String aos_weightunit = getUnit("aos_weightunit");
        String aos_volumeunit = getUnit("aos_volumeunit");
        TableValueSetter tableValueSetter = new TableValueSetter();
        int count = 0;
        while (dataSet.hasNext()) {
            Row next = dataSet.next();

            // 单位换算
            String aos_sizeunit1 = next.getString("aos_sizeunit");
            String aos_weightunit1 = next.getString("aos_weightunit");
            String aos_volumeunit1 = next.getString("aos_volumeunit");

            BigDecimal aos_length = BigDecimal.ZERO;
            BigDecimal aos_width = BigDecimal.ZERO;
            BigDecimal aos_height = BigDecimal.ZERO;
            BigDecimal aos_volume = BigDecimal.ZERO;
            BigDecimal aos_grossweight = BigDecimal.ZERO;
            BigDecimal aos_netweights = BigDecimal.ZERO;
            try {
                aos_length = SalUtil.unitConversion(aos_sizeunit1, next.getBigDecimal("aos_length"), aos_sizeunit);
                aos_width  = SalUtil.unitConversion(aos_sizeunit1, next.getBigDecimal("aos_width"), aos_sizeunit);
                aos_height  = SalUtil.unitConversion(aos_sizeunit1, next.getBigDecimal("aos_height"), aos_sizeunit);

                aos_volume  = SalUtil.unitConversion(aos_volumeunit1, next.getBigDecimal("aos_volume"), aos_volumeunit);

                aos_grossweight  = SalUtil.unitConversion(aos_weightunit1, next.getBigDecimal("aos_grossweight"), aos_weightunit);
                aos_netweights  = SalUtil.unitConversion(aos_weightunit1, next.getBigDecimal("aos_netweights"), aos_weightunit);
            } catch (Exception e) {}

            List<BigDecimal> sortList = new ArrayList<>(3);
            sortList.add(aos_length);
            sortList.add(aos_width);
            sortList.add(aos_height);

            sortList = sortList.stream().sorted((n1, n2) -> n2.compareTo(n1)).collect(Collectors.toList());

            tableValueSetter.set("aos_orgid", next.get("aos_orgid"), count);
            tableValueSetter.set("aos_itemid", next.get("aos_itemid"), count);
            tableValueSetter.set("aos_boxqty", next.get("aos_boxqty"), count);
            tableValueSetter.set("aos_boxnum", next.get("aos_seq")+"/"+next.get("aos_count"), count);
            tableValueSetter.set("aos_grossweight", aos_grossweight, count);
            tableValueSetter.set("aos_netweights", aos_netweights, count);

            tableValueSetter.set("aos_longestedge", sortList.get(0), count);
            tableValueSetter.set("aos_seclongestedge", sortList.get(1), count);
            tableValueSetter.set("aos_shortestedge", sortList.get(2), count);

            tableValueSetter.set("aos_volume", aos_volume, count);
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
    private String getOrgId() {
        DynamicObject object = (DynamicObject) this.getModel().getValue(ProBoxRpt.QUERY_FIELD_ORG);
        return object == null ? "" : object.getString("id");
    }

    private String getUnit(String field) {
        return SalUtil.obj2String(this.getModel().getValue(field));
    }

}
