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
 * @author aosom
 */
public class MmsOtelTemp extends AbstractFormPlugin {
    private static final Tracer TRACER = MmsOtelUtils.getTracer(MmsOtelTemp.class, RequestContext.get());

    @Override
    public void registerListener(EventObject e) {
        super.registerListener(e);
        // 给工具栏加监听事件
        this.addItemClickListeners("aos_toolbarap");
        this.addItemClickListeners("aos_test"); // 提交
    }

    @Override
    public void itemClick(ItemClickEvent evt) {
        Span span = MmsOtelUtils.getCusMainSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignored = span.makeCurrent()) {
            String control = evt.getItemKey();
            if ("aos_test".equals(control)) {
                aosTest();
            }
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

    private void aosTest() {
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignored = span.makeCurrent()) {
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
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignored = span.makeCurrent()) {
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
        Span span = MmsOtelUtils.getCusSubSpan(TRACER, MmsOtelUtils.getMethodPath());
        try (Scope ignored = span.makeCurrent()) {
            span.addEvent("明细测试");
            span.setStatus(StatusCode.OK);
        } catch (Exception ex) {
            MmsOtelUtils.setException(span, ex);
        } finally {
            MmsOtelUtils.spanClose(span);
        }
    }

}