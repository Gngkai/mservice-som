package mkt.common.otel;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import kd.bos.context.RequestContext;
import kd.bos.form.control.events.ItemClickEvent;
import kd.bos.form.plugin.AbstractFormPlugin;

import java.util.EventObject;

/**
 * Author:aosom
 * Date:2023/12/6 13:07
 */
public class MmsOtelTemp extends AbstractFormPlugin {
    private static final Tracer tracer = MmsOtelUtils.getTracer(mkt.image.test.aos_mkt_bitest_form.class, RequestContext.get());

    public void registerListener(EventObject e) {
        super.registerListener(e);
        // 给工具栏加监听事件
        this.addItemClickListeners("aos_toolbarap");
        this.addItemClickListeners("aos_test"); // 提交
    }

    public void itemClick(ItemClickEvent evt) {
        Span span = MmsOtelUtils.getCusMainSpan(tracer, MmsOtelUtils.getMethodPath());
        try (Scope scope = span.makeCurrent()) {
            String Control = evt.getItemKey();
            if (Control.equals("aos_test")) {
                aos_test();
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    private void aos_test() {
        Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
        try (Scope scope = span.makeCurrent()) {
            subTest();
            span.addEvent("主测试");
            span.setStatus(StatusCode.OK);
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    private void subTest() {
        Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
        try (Scope scope = span.makeCurrent()) {
            detailTest();
            span.addEvent("子测试");
            span.setStatus(StatusCode.OK);
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    private void detailTest() {
        Span span = MmsOtelUtils.getCusSubSpan(tracer, MmsOtelUtils.getMethodPath());
        try (Scope scope = span.makeCurrent()) {
            span.addEvent("明细测试");
            span.setStatus(StatusCode.OK);
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

}