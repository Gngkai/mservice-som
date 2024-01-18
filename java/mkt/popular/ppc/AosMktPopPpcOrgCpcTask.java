package mkt.popular.ppc;

import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.exception.KDException;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.schedule.executor.AbstractTask;
import kd.bos.servicehelper.BusinessDataServiceHelper;
import kd.bos.servicehelper.QueryServiceHelper;
import kd.bos.servicehelper.operation.DeleteServiceHelper;
import kd.bos.servicehelper.operation.SaveServiceHelper;
import org.apache.commons.lang3.time.DateFormatUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
/**
 * @author aosom
 * @version 国别品类CPC同步-调度任务类
 */
public class AosMktPopPpcOrgCpcTask extends AbstractTask {
    @Override
    public void execute(RequestContext requestContext, Map<String, Object> map) throws KDException {
        genOrgCpc();
    }
    private void genOrgCpc() {
        DynamicObjectCollection list = QueryServiceHelper.query("bd_country", "id aos_orgid",
            new QFilter[] {new QFilter("aos_isomvalid", QCP.equals, true)});
        // 过去7天
        Calendar instance = Calendar.getInstance();
        instance.add(Calendar.DAY_OF_MONTH, -7);
        String before7 = DateFormatUtils.format(instance.getTime(), "yyyy-MM-dd");
        for (DynamicObject orgObj : list) {
            String aosOrgid = orgObj.getString("aos_orgid");
            DeleteServiceHelper.delete("aos_mkt_orgcpc",
                new QFilter[] {new QFilter("aos_orgid", QCP.equals, aosOrgid)});
            String selectFileds =
                "aos_entryentity.aos_category1 aos_category1," + "aos_entryentity.aos_category2 aos_category2,"
                    + "aos_entryentity.aos_category3 aos_category3," + "aos_entryentity.aos_bid aos_bid";
            DataSet dataSet = QueryServiceHelper.queryDataSet(
                "genOrgCpc", "aos_mkt_popular_ppc", selectFileds, new QFilter[] {
                    new QFilter("aos_orgid", QCP.equals, aosOrgid), new QFilter("aos_date", QCP.large_than, before7)},
                null);
            dataSet = dataSet.groupBy(new String[] {"aos_category1", "aos_category2", "aos_category3"}).avg("aos_bid")
                .finish();
            List<DynamicObject> dynamicObjectList = new ArrayList<>();
            while (dataSet.hasNext()) {
                Row next = dataSet.next();
                String aosCategory1 = next.getString("aos_category1");
                String aosCategory2 = next.getString("aos_category2");
                String aosCategory3 = next.getString("aos_category3");
                BigDecimal aosBid = next.getBigDecimal("aos_bid");
                DynamicObject dynamicObject = BusinessDataServiceHelper.newDynamicObject("aos_mkt_orgcpc");
                dynamicObject.set("aos_orgid", aosOrgid);
                dynamicObject.set("aos_category1", aosCategory1);
                dynamicObject.set("aos_category2", aosCategory2);
                dynamicObject.set("aos_category3", aosCategory3);
                dynamicObject.set("aos_bid", aosBid);
                dynamicObject.set("billstatus", "A");
                dynamicObjectList.add(dynamicObject);
            }
            dataSet.close();
            SaveServiceHelper.save(dynamicObjectList.toArray(new DynamicObject[0]));
        }
    }
}
