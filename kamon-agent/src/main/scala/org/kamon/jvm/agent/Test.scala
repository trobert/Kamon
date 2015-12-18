package org.kamon.jvm.agent

import kamon.Kamon
import kamon.trace.Tracer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Test extends App {
    Kamon.start

//  val pool = TypePool.Default.ofClassPath()

//  val a = new ByteBuddy().subclass(pool.describe("scala.concurrent.impl.CallbackRunnable").resolve())
//    .implement(classOf[TraceContextAware])
//    .intercept(FieldAccessor.ofField("traceContext"))
//    .defineField("traceContext", classOf[TraceContext], PROTECTED)
//    .constructor(any()).intercept(SuperMethodCall.INSTANCE.andThen(to(ConstructorInterceptor)))
//    .method(named("run")).intercept(to(FutureInstrumentation))
//    .make()
//    .saveIn(new File("/home/diego/puto"))
//        .load(getClass.getClassLoader, ClassLoadingStrategy.Default.WRAPPER)
//    .getLoaded

  val (future, testTraceContext) = Tracer.withContext(Kamon.tracer.newContext("future-body")) {
    val future = Future("Hello Kamon!")
      // The TraceContext is expected to be available during all intermediate processing.
      .map(_.length)
      .flatMap(len ⇒ Future(len.toString))
      .map(s ⇒ Tracer.currentContext)

    (future, Tracer.currentContext)
  }

  future.map {
    ctxInFuture =>
      println(ctxInFuture == testTraceContext)
  }
}
