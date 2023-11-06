package mkt.test;

import kd.bos.filter.FilterColumn;
import kd.bos.form.events.FilterColumnSetFilterEvent;
import kd.bos.form.events.FilterContainerInitArgs;
import kd.bos.form.events.FilterContainerSearchClickArgs;
import kd.bos.form.events.SetFilterEvent;
import kd.bos.list.plugin.AbstractListPlugin;
import kd.bos.orm.query.QFilter;

public class demo8 extends AbstractListPlugin {
    public void setFilter(SetFilterEvent e) {
        //addCustomQFilter设置多个过滤条件
     //   e.addCustomQFilter(new QFilter("billno", "=", "002"));
    }

    /*
事件名称：filterContainerInit
对应监控监听：addFilterContainerInitListener
说明：过滤字段元数据初始化
方法：getFilterColumn  通过字段名获取过滤字段
addFilterColumn   添加过滤字段
     */
    public void filterContainerInit(FilterContainerInitArgs args){
        //FilterColumn filterColumn = new FilterColumn("字段");
       // args.addFilterColumn(filterColumn);
        args.addFilterColumn(new FilterColumn("aos_mulilangtextfield"));   //=============多语言文本字段未出现

        args.getFilterColumn("billstatus");

        FilterColumn filterColumn = args.getFilterColumn("billstatus");
        filterColumn.setDefaultValues("已提交");  //==========未显示

        super.filterContainerInit(args);
    }

    public void filterColumnSetFilter(SetFilterEvent args) {

        if ("basedatafield2.name".equals(args.getFieldName()))
            args.addCustomQFilter(new QFilter("name", QFilter.equals, "test123"));
        if ("basedatafield2.name".equals(args.getFieldName())) {
            FilterColumnSetFilterEvent args2 = (FilterColumnSetFilterEvent) args;
            args.addCustomQFilter(new QFilter("name", QFilter.equals, args2.getCommonFilterValue("orgfield1.number")));
        }
        super.filterColumnSetFilter(args);
    }
}
