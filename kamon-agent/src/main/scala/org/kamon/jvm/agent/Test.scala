package org.kamon.jvm.agent

import java.io.File

import kamon.Kamon
import kamon.trace.{TraceContextAware, Tracer}
import net.bytebuddy.ByteBuddy
import net.bytebuddy.description.modifier.Visibility
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy
import net.bytebuddy.implementation.MethodDelegation._
import net.bytebuddy.implementation.SuperMethodCall
import net.bytebuddy.matcher.ElementMatchers._
import net.bytebuddy.pool.TypePool
import org.kamon.jvm.agent.KamonAgent.{ConstructorInterceptor, FutureInstrumentation}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object Test extends App {
    Kamon.start

//  val pool = TypePool.Default.ofClassPath()
//
//  val a = new ByteBuddy().subclass(pool.describe("scala.concurrent.impl.CallbackRunnable").resolve())
//    .implement(classOf[TraceContextAware])
//    .defineField("traceContext", classOf[TraceContextAware], Visibility.PRIVATE)
//    .constructor(any()).intercept(SuperMethodCall.INSTANCE.andThen(to(ConstructorInterceptor)))
//    .method(named("run"))
//    .intercept(to(FutureInstrumentation))
//    .make()
//    .saveIn(new File("/home/diego/puto"))
//        .load(getClass.getClassLoader, ClassLoadingStrategy.Default.WRAPPER)
//    .getLoaded

  Tracer.withContext(Kamon.tracer.newContext("future-body")) {
    val future = Future("Hello Kamon!")
      // The TraceContext is expected to be available during all intermediate processing.
      .map(_.length)
      .flatMap(len ⇒ Future(len.toString))
      .map(s ⇒ Tracer.currentContext)
  }
  println("afasdasdlfasldfhasjdahdkjfhajsshfkjfkajsf")

}
