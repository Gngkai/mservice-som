package mkt.data.slogan;

import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import kd.bos.algo.DataSet;
import kd.bos.algo.Row;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.LocaleString;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.form.control.events.RowClickEventListener;
import kd.bos.form.field.ComboEdit;
import kd.bos.form.field.ComboItem;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import mkt.common.otel.MmsOtelUtils;


public class aos_mkt_slogan_bill extends AbstractBillPlugIn implements RowClickEventListener {

    public final static String DB_MKT = "aos.mkt";// 供应链库
    private static final Tracer tracer = MmsOtelUtils.getTracer(aos_mkt_slogan_bill.class, RequestContext.get());

    @Override
    public void registerListener(EventObject e) {
    }

    public void propertyChanged(PropertyChangedArgs e) {
        Span span = MmsOtelUtils.getCusMainSpan(tracer, MmsOtelUtils.getMethodPath());
        try (Scope scope = span.makeCurrent()) {
            String name = e.getProperty().getName();
            if (name.equals("aos_category1") || name.equals("aos_category2") || name.equals("aos_category3")) {
                init_category();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }


    @Override
    public void afterLoadData(EventObject e) {
        init_category();
    }


    public void afterCreateNewData(EventObject e) {
        init_category();
    }

    /*
     * 初始化类别
     */
    private void init_category() {
        Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
        try (Scope scope = span.makeCurrent()) {
            Object aos_category1 = this.getModel().getValue("aos_category1");
            Object aos_category2 = this.getModel().getValue("aos_category2");
            ComboEdit comboEdit = this.getControl("aos_category1");
            ComboEdit comboEdit2 = this.getControl("aos_category2");
            ComboEdit comboEdit3 = this.getControl("aos_category3");
            List<ComboItem> data = new ArrayList<>();
            List<ComboItem> data2 = new ArrayList<>();
            List<ComboItem> data3 = new ArrayList<>();
            // 大类
            QFilter filter_level = new QFilter("level", "=", "1");
            QFilter filter_divide = new QFilter("name", "!=", "待分类");
            QFilter[] filters_group = new QFilter[]{filter_level, filter_divide};
            String select_group = "name,level";
            DataSet bd_materialgroupS = QueryServiceHelper.queryDataSet(
                    this.getClass().getName() + "." + "bd_materialgroupS", "bd_materialgroup", select_group, filters_group,
                    null);
            while (bd_materialgroupS.hasNext()) {
                Row bd_materialgroup = bd_materialgroupS.next();
                // 获取数据
                String category_name = bd_materialgroup.getString("name");
                data.add(new ComboItem(new LocaleString(category_name), category_name));
            }
            bd_materialgroupS.close();
            comboEdit.setComboItems(data);

            if (aos_category1 == null) {
                comboEdit2.setComboItems(null);
                comboEdit3.setComboItems(null);
            } else {
                filter_level = new QFilter("level", "=", "2");
                filter_divide = new QFilter("parent.name", "=", aos_category1);
                filters_group = new QFilter[]{filter_level, filter_divide};
                select_group = "name,level";
                DataSet bd_materialgroup2S = QueryServiceHelper.queryDataSet(
                        this.getClass().getName() + "." + "bd_materialgroup2S", "bd_materialgroup", select_group,
                        filters_group, null);
                while (bd_materialgroup2S.hasNext()) {
                    Row bd_materialgroup2 = bd_materialgroup2S.next();
                    // 获取数据
                    String category_name;
                    String[] names = bd_materialgroup2.getString("name").split(",");
                    if (names.length > 1) {
                        category_name = names[1];
                    } else {
                        continue;
                    }
                    data2.add(new ComboItem(new LocaleString(category_name), category_name));
                }
                bd_materialgroup2S.close();
                comboEdit2.setComboItems(data2);

                if (aos_category2 == null) {
                    comboEdit3.setComboItems(null);
                } else {
                    filter_level = new QFilter("level", "=", "3");
                    filter_divide = new QFilter("parent.name", "=", aos_category1 + "," + aos_category2);
                    filters_group = new QFilter[]{filter_level, filter_divide};
                    select_group = "name,level";

                    DataSet bd_materialgroup3S = QueryServiceHelper.queryDataSet(
                            this.getClass().getName() + "." + "bd_materialgroup3S", "bd_materialgroup", select_group,
                            filters_group, null);
                    while (bd_materialgroup3S.hasNext()) {
                        Row bd_materialgroup3 = bd_materialgroup3S.next();
                        // 获取数据
                        String category_name;
                        String[] names = bd_materialgroup3.getString("name").split(",");
                        if (names.length > 2) {
                            category_name = names[2];
                        } else {
                            continue;
                        }
                        data3.add(new ComboItem(new LocaleString(category_name), category_name));
                    }
                    bd_materialgroup3S.close();
                    comboEdit3.setComboItems(data3);
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }


}