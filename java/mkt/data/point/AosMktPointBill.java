package mkt.data.point;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import kd.bos.bill.AbstractBillPlugIn;
import kd.bos.context.RequestContext;
import kd.bos.dataentity.entity.DynamicObject;
import kd.bos.entity.datamodel.events.PropertyChangedArgs;
import kd.bos.orm.query.QCP;
import kd.bos.orm.query.QFilter;
import kd.bos.servicehelper.QueryServiceHelper;
import mkt.common.otel.MmsOtelUtils;

/**
 * @author aosom
 * @version 关键词库-表单插件
 */
public class AosMktPointBill extends AbstractBillPlugIn {
    public final static String AOS_CATEGORY1 = "aos_category1";
    public final static String AOS_CATEGORY2 = "aos_category2";
    private static final Tracer TRACER = MmsOtelUtils.getTracer(AosMktPointBill.class, RequestContext.get());

    public static String obj2String(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    @Override
    public void propertyChanged(PropertyChangedArgs e) {
        Span span = MmsOtelUtils.getCusMainSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            String name = e.getProperty().getName();
            if (AOS_CATEGORY2.equals(name) || AOS_CATEGORY1.equals(name)) {
                autoSetCategoryInfo();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    private void autoSetCategoryInfo() {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            String aosCategory1 = obj2String(this.getModel().getValue("aos_category1"));
            String aosCategory2 = obj2String(this.getModel().getValue("aos_category2"));
            // 产品中类改变自动带出编辑
            DynamicObject editor = getEditorInfo2(aosCategory1, aosCategory2);
            if (editor != null) {
                this.getModel().setValue("aos_user", editor.get("aos_eng"));
            } else {
                this.getModel().setValue("aos_user", null);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    /**
     * 根据大类中类获取编辑助理信息
     *
     * @param aosCategory1 大类
     * @param aosCategory2 小类
     * @return 编辑助理
     */
    private DynamicObject getEditorInfo2(String aosCategory1, String aosCategory2) {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignore = span.makeCurrent()) {
            return QueryServiceHelper.queryOne("aos_mkt_proguser", "aos_eng",
                new QFilter[] {new QFilter("aos_category1", QCP.equals, aosCategory1)
                    .and(new QFilter("aos_category2", QCP.equals, aosCategory2))});
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }
}
