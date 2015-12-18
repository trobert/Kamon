package kamon.scala.instrumentation

import java.lang.instrument.Instrumentation

import com.esotericsoftware.reflectasm.FieldAccess
import kamon.trace.{TraceContext, TraceContextAware, Tracer}
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.agent.builder.AgentBuilder.Transformer
import net.bytebuddy.description.NamedElement
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.modifier.Visibility._
import net.bytebuddy.dynamic.DynamicType.Builder
import net.bytebuddy.implementation.MethodDelegation._
import net.bytebuddy.implementation.bind.annotation.{SuperCall, This}
import net.bytebuddy.implementation.{FieldAccessor, SuperMethodCall}
import net.bytebuddy.matcher.ElementMatchers._

object FutureInstrumentation {

  object ConstructorInterceptor {
    def init(@This runnable: TraceContextAware) = {
      val traceContext = FieldAccess.get(runnable.getClass)
      traceContext.set(runnable, "traceContext", TraceContextAware.default.traceContext)
    }
  }

  object FutureInterceptor {
    def run(@This runnable: TraceContextAware, @SuperCall r: Runnable): Unit = {
      Tracer.withContext(runnable.traceContext) {
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

  def apply(instrumentation:Instrumentation): Unit = new AgentBuilder.Default()
    .`type`(named[NamedElement]("scala.concurrent.impl.CallbackRunnable").or(named[NamedElement]("scala.concurrent.impl.Future.PromiseCompletingRunnable")))
    .transform(futureTransformer)
    .installOn(instrumentation) 

  private def withTransformer(f: => (Builder[_], TypeDescription) => Builder[_]): Transformer = new Transformer {
    override def transform(builder: Builder[_], typeDescription: TypeDescription): Builder[_] = {
      f.apply(builder,typeDescription)
    }
  }
}
