package kamon.instrumentation.scala

import java.lang.instrument.Instrumentation

import kamon.instrumentation.InstrumentationUtils.withTransformer
import kamon.instrumentation.KamonInstrumentation
import kamon.trace.{TraceContext, TraceContextAware, Tracer}
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.description.NamedElement
import net.bytebuddy.implementation.FieldAccessor
import net.bytebuddy.implementation.MethodDelegation._
import net.bytebuddy.implementation.bind.annotation.{SuperCall, This}
import net.bytebuddy.jar.asm.Opcodes.{ACC_FINAL => FINAL, ACC_PRIVATE => PRIVATE, ACC_TRANSIENT => TRANSIENT}
import net.bytebuddy.matcher.ElementMatchers._

class FutureInstrumentation extends KamonInstrumentation {

  private val Runnable = {
    named[NamedElement]("scala.concurrent.impl.CallbackRunnable").or(
    named[NamedElement]("scala.concurrent.impl.Future$PromiseCompletingRunnable"))
  }

  override def register(instrumentation: Instrumentation): Unit = {
    new AgentBuilder.Default()
      .`type`(Runnable)
      .transform(futureTransformer)
      .installOn(instrumentation)
  }

  def futureTransformer = withTransformer { (builder, typeDescription) =>
    builder.implement(classOf[TraceContextAware]).intercept(FieldAccessor.ofField("traceContext"))
      .defineField("traceContext", classOf[TraceContext], FINAL | PRIVATE | TRANSIENT)
      .method(named("run")).intercept(to(FutureInterceptor))
      .classVisitor(RunnableVisitor(typeDescription))
  }

  object FutureInterceptor {
    def run(@This runnable: TraceContextAware, @SuperCall r: Runnable): Unit = {
      Tracer.withContext(runnable.traceContext) {
        r.run()
      }
    }
  }
}



