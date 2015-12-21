package kamon.agent

import kamon.Kamon
import net.bytebuddy.jar.asm.Opcodes.{ACC_FINAL => FINAL, ACC_PRIVATE => PRIVATE, ACC_TRANSIENT => TRANSIENT}


//import kamon.instrumentation.scala.FutureInstrumentation.ConstructorInterceptor
import kamon.trace.Tracer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object Test extends App {
    Kamon.start

//  val pool = TypePool.Default.ofClassPath()

//  val a = new ByteBuddy().rebase(pool.describe("scala.concurrent.impl.CallbackRunnable").resolve(),ClassFileLocator.ForClassLoader.ofClassPath())
//     .implement(classOf[TraceContextAware]).intercept(FieldAccessor.ofField("traceContext"))
//    .defineField("traceContext", classOf[TraceContext], FINAL | PRIVATE | TRANSIENT)
//    .method(named("run")).intercept(to(FutureInterceptor))
//    .classVisitor(new ClassVisitorWrapper() {
//      override def wrap(classVisitor: ClassVisitor): ClassVisitor =  new ReturnVisitor(classVisitor)
//      override def mergeWriter(flags: Int): Int =   flags
//      override def mergeReader(flags: Int): Int = flags | ClassReader.EXPAND_FRAMES
//    })    .make()
//    .saveIn(new File("/home/diego/puto11"))
//        .load(getClass.getClassLoader, ClassLoadingStrategy.Default.WRAPPER)
//    .getLoaded
//
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

//class ClassSuperVisitor() extends ClassVisitorWrapper {
//  override def mergeWriter(flags: Int): Int = ???
//
//  override def wrap(classVisitor: ClassVisitor): = ???
//
//  override def mergeReader(flags: Int): Int = ???
//}
