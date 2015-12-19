package kamon.instrumentation.scala

import java.lang.instrument.Instrumentation

import com.esotericsoftware.reflectasm.FieldAccess
import kamon.instrumentation.InstrumentationUtils.withTransformer
import kamon.instrumentation.KamonInstrumentation
import kamon.trace.{TraceContext, TraceContextAware, Tracer}
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.description.NamedElement
import net.bytebuddy.description.modifier.Visibility._
import net.bytebuddy.implementation.MethodDelegation._
import net.bytebuddy.implementation.bind.annotation.{FieldValue, SuperCall, This}
import net.bytebuddy.implementation.{FieldAccessor, SuperMethodCall}
import net.bytebuddy.matcher.ElementMatchers._

class FutureInstrumentation extends KamonInstrumentation {

  import FutureInstrumentation._

  private val Runnable = {
    named[NamedElement]("scala.concurrent.impl.CallbackRunnable").or(
    named[NamedElement]("scala.concurrent.impl.Future.PromiseCompletingRunnable"))
  }

  override def register(instrumentation:Instrumentation): Unit = {
    new AgentBuilder.Default()
      .`type`(Runnable)
      .transform(futureTransformer)
      .installOn(instrumentation)
  }
}

object FutureInstrumentation {
  object ConstructorInterceptor {
    def init(@This runnable: TraceContextAware) = {
      val traceContext = FieldAccess.get(runnable.getClass)
      traceContext.set(runnable, "traceContext", TraceContextAware.default.traceContext)
    }
  }

  object FutureInterceptor {
    def run(@SuperCall r: Runnable, @FieldValue("traceContext") traceContext: TraceContext): Unit = {
      Tracer.withContext(traceContext) {
        r.run()
      }
    }
  }

  private def futureTransformer = withTransformer { (builder, typeDescription) =>
    builder.implement(classOf[TraceContextAware]).intercept(FieldAccessor.ofField("traceContext"))
      .defineField("traceContext", classOf[TraceContext], PROTECTED)
      .constructor(any()).intercept(SuperMethodCall.INSTANCE.andThen(to(ConstructorInterceptor)))
      .method(named("run")).intercept(to(FutureInterceptor))
  }
}
