package kamon.agent

import kamon.Pepe
import kamon.trace.{ Tracer, TraceContext, TraceContextAware }
import kamon.util.instrumentation.KamonInstrumentation
import kamon.util.{ initializer, Mixin, MixinTraceContext }
import net.bytebuddy.agent.builder.AgentBuilder.Transformer
import net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith

class MixinInstrumentation extends KamonInstrumentation {

  override def getType = isAnnotatedWith(classOf[Mixin])

  override def getTransformer(): Transformer = withTransformer((builder, _) ⇒ builder)

  //@MixinTraceContext(Array("kamon.agent.Pepa")) //
  @Mixin(Array("kamon.agent.Pepa"))
  class InjectTraceContext extends TraceContextAware {

    @transient
    var traceContext: TraceContext = _

    @initializer
    private def init(): Unit = {
      traceContext = Tracer.currentContext
    }
  }
}
//-javaagent:/home/diego/gitHub/Kamon/kamon-agent/target/scala-2.11/kamon-agent_2.11-0.5.3-SNAPSHOT.jar

object M extends App {
  println(new Pepa().isInstanceOf[TraceContextAware])
  //  PepePonpin.b
  //  val pool = TypePool.Default.ofClassPath()
  //  new PepePonpin
  //  val a = new ByteBuddy().subclass(pool.describe("scala.concurrent.impl.CallbackRunnable").resolve(),ClassFileLocator.ForClassLoader.ofClassPath())
  //       .implement(classOf[TraceContextAware]).intercept(FieldAccessor.ofField("traceContext"))
  //      .defineField("traceContext", classOf[TraceContext], FINAL | PRIVATE | TRANSIENT)
  //      .method(named("run")).intercept(to(FutureInterceptor)).
  //      })    .make()
  //      .saveIn(new File("/home/diego/puto12"))
  //
}