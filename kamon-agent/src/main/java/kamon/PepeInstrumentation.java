package kamon;

import kamon.trace.TraceContext;
import kamon.trace.TraceContextAware;
import kamon.trace.Tracer;
import kamon.util.initializer;
import kamon.util.Mixin;
import kamon.util.instrumentation.KamonInstrumentation;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * Created by diego on 12/28/15.
 */
public class PepeInstrumentation extends KamonInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getType() {
        return ElementMatchers.isAnnotatedWith(Mixin.class);
    }

    @Mixin({"kamon.agent.Pepa"})
    public static class InjectTraceContext2 implements TraceContextAware {
        private transient TraceContext traceContext;
        private String bla = "asdfasfasfafsfasd";
        private String rePuto;

        @initializer
        private void init()  {
            this.rePuto = "superputo";
            this.traceContext = Tracer.currentContext();
            this.bla ="bla";
        }

        @Override
        public TraceContext traceContext() {
            return traceContext;
        }
    }
}
