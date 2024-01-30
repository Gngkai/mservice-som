package mkt.act.statement.calendar;

import common.fnd.FndGlobal;
import common.sal.util.QFBuilder;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.dataentity.entity.DynamicObjectCollection;
import kd.bos.form.events.AfterDoOperationEventArgs;
import kd.bos.form.plugin.AbstractFormPlugin;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * @author: GK
 * @create: 2024-01-30 10:30
 * @Description:活动日历报表
 */
@SuppressWarnings("unused")
public class EventsCalendarReport extends AbstractFormPlugin {
    @Override
    public void afterDoOperation(AfterDoOperationEventArgs eventArgs) {
        super.afterDoOperation(eventArgs);
        String operateKey = eventArgs.getOperateKey();
        System.out.println("operateKey = " + operateKey);
        if ("aos_query".equals(operateKey)){
            query();
        }
    }

    public void query(){
        getFilter();
    }

    /**
     * 获取过滤条件
     */
    public QFBuilder getFilter(){
        QFBuilder builder = new QFBuilder();

        //国家
        DynamicObjectCollection filterInfoRows = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_sel_org");
        List<String> countryList = new ArrayList<>();
        if (filterInfoRows.size()>0) {
            countryList = filterInfoRows
                    .stream()
                    .map(row -> row.getDynamicObject("fbasedataid").getString("id"))
                    .collect(Collectors.toList());

            builder.add("aos_nationality", QFilter.in, countryList);
        }

        //渠道
        filterInfoRows = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_sel_channel");
        if (filterInfoRows.size()>0) {
            List<String> idList = filterInfoRows
                    .stream()
                    .map(row -> row.getDynamicObject("fbasedataid").getString("id"))
                    .collect(Collectors.toList());
            builder.add("aos_channel", QFilter.in, idList);
        }

        //大类
        filterInfoRows = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_sel_cate");
        if (filterInfoRows.size()>0) {
            List<String> cateList = filterInfoRows
                    .stream()
                    .map(row -> row.getDynamicObject("fbasedataid").getLocaleString("name").getLocaleValue_zh_CN())
                    .collect(Collectors.toList());
            builder.add("aos_sal_actplanentity.aos_category_stat1", QFilter.in, cateList);
        }

        //品名
        filterInfoRows = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_sel_name");
        if (filterInfoRows.size()>0) {
            List<String> nameList = filterInfoRows
                    .stream()
                    .map(row -> row.getDynamicObject("fbasedataid").getString("name"))
                    .collect(Collectors.toList());
            builder.add("aos_sal_actplanentity.aos_itemnum.name", QFilter.in, nameList);
        }

        //sku
        filterInfoRows = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_sel_sku");
        if (filterInfoRows.size()>0) {
            List<String> skuList = filterInfoRows
                    .stream()
                    .map(row -> row.getDynamicObject("fbasedataid").getString("id"))
                    .collect(Collectors.toList());
            builder.add("aos_sal_actplanentity.aos_itemnum", QFilter.in, skuList);
        }

        //爆品
        Object hot = getModel().getValue("aos_sel_hot");
        if (FndGlobal.IsNotNull(hot)) {
            builder.add("aos_sal_actplanentity.aos_is_saleout", "=", hot);
        }

        //季节属性
        filterInfoRows = this.getModel().getDataEntity(true).getDynamicObjectCollection("aos_sel_season");
        if (filterInfoRows.size()>0) {
            List<String> seasonList = filterInfoRows
                    .stream()
                    .map(row -> row.getDynamicObject("fbasedataid").getString("number"))
                    .collect(Collectors.toList());
            builder.add("aos_sal_actplanentity.aos_season", QFilter.in, seasonList);
        }

        //滞销类型
        Object unsalable = getModel().getValue("aos_sel_type");
        List<String> unsalItemList = new ArrayList<>();
        if (FndGlobal.IsNotNull(unsalable)) {
            List<String> collect = Arrays.stream(String.valueOf(unsalable).split(","))
                    .filter(FndGlobal::IsNotNull)
                    .collect(Collectors.toList());
            //获取滞销货号
            QFBuilder unsalableBuilder = new QFBuilder();
            if (countryList.size()>0){
                unsalableBuilder.add("aos_orgid",QFilter.in,countryList);
            }
            unsalableBuilder.add("aos_type",QFilter.in,collect);
            DynamicObjectCollection unsaleRults = QueryServiceHelper.query("aos_base_stitem", "aos_orgid,aos_itemid", unsalableBuilder.toArray());
            unsalItemList = new ArrayList<>(unsaleRults.size());
            for (DynamicObject unsaleRult : unsaleRults) {
                StringJoiner str = new StringJoiner("/");
                str.add(unsaleRult.getString("aos_orgid"));
                str.add(unsaleRult.getString("aos_itemid"));
                unsalItemList.add(str.toString());
            }
        }

        //开始日期 的开始日期
        Object startDate = getModel().getValue("aos_sel_start_s");
        if (FndGlobal.IsNotNull(startDate)) {
            builder.add("aos_sal_actplanentity.aos_l_startdate", ">=", startDate);
        }

        //开始日期 的结束日期
        Object endDate = getModel().getValue("aos_sel_start_e");
        if (FndGlobal.IsNotNull(endDate)) {
            builder.add("aos_sal_actplanentity.aos_l_startdate", "<=", endDate);
        }

        //结束日期 的开始日期
        startDate = getModel().getValue("aos_sel_end_s");
        if (FndGlobal.IsNotNull(startDate)) {
            builder.add("aos_sal_actplanentity.aos_enddate", ">=", startDate);
        }
        //结束日期 的结束日期
        endDate = getModel().getValue("aos_sel_end_e");
        if (FndGlobal.IsNotNull(endDate)) {
            builder.add("aos_sal_actplanentity.aos_enddate", "<=", endDate);
        }

        return builder;
    }
}
