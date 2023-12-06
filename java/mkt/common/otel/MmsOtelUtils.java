package mkt.common.otel;

import common.otel.OtelConnUtils;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import kd.bos.context.RequestContext;

/**
 * Author:aosom
 * Date:2023/12/6 8:51
 */
public class MmsOtelUtils extends OtelConnUtils {

    public static Tracer getTracer(Class cls, RequestContext rc) {
        return OtelConnUtils.getTracer("MMS-CORE", cls, rc);
    }

    public static Tracer getTracer(String version, Class cls, RequestContext rc) {
        return OtelConnUtils.getTracer("MMS-CORE", version, cls, rc);
    }

    public static Span getCusMainSpan(Tracer tracer, String path) {
        return tracer.spanBuilder(path).startSpan();
    }

    public static Span getCusSubSpan(Tracer tracer, String path) {
        return tracer.spanBuilder(path).setParent(Context.current()).startSpan();
    }
}
