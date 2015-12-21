package kamon.instrumentation.scala

import java.lang.instrument.Instrumentation

import com.esotericsoftware.reflectasm.FieldAccess
import kamon.instrumentation.InstrumentationUtils.withTransformer
import kamon.instrumentation.KamonInstrumentation
import kamon.trace.{TraceContext, TraceContextAware, Tracer}
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.asm.ClassVisitorWrapper
import net.bytebuddy.description.NamedElement
import net.bytebuddy.implementation.FieldAccessor
import net.bytebuddy.implementation.MethodDelegation._
import net.bytebuddy.implementation.bind.annotation.{SuperCall, This}
import net.bytebuddy.jar.asm.Opcodes.{ACC_FINAL => FINAL, ACC_PRIVATE => PRIVATE, ACC_TRANSIENT => TRANSIENT}
import net.bytebuddy.jar.asm.commons.AdviceAdapter
import net.bytebuddy.jar.asm.{ClassReader, ClassVisitor, MethodVisitor, Opcodes}
import net.bytebuddy.matcher.ElementMatchers._

class FutureInstrumentation extends KamonInstrumentation {

  import FutureInstrumentation._

  private val Runnable = {
    named[NamedElement]("scala.concurrent.impl.CallbackRunnable").or(
    named[NamedElement]("scala.concurrent.impl.Future.PromiseCompletingRunnable"))
  }

  override def register(instrumentation:Instrumentation): Unit = {
    new AgentBuilder.Default()
//        .withTypeStrategy(AgentBuilder.TypeStrategy.Default.REDEFINE)
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
    def run(@This runnable: TraceContextAware, @SuperCall r: Runnable): Unit = {
      Tracer.withContext(runnable.traceContext) {
        r.run()
      }
    }
  }

  private def futureTransformer = withTransformer { (builder, typeDescription) =>
    builder.implement(classOf[TraceContextAware]).intercept(FieldAccessor.ofField("traceContext"))
      .defineField("traceContext", classOf[TraceContext], FINAL | PRIVATE | TRANSIENT)
      .method(named("run")).intercept(to(FutureInterceptor))
      .classVisitor(new ClassVisitorWrapper() {
        override def wrap(classVisitor: ClassVisitor): ClassVisitor = new ReturnVisitor(classVisitor)

        override def mergeWriter(flags: Int): Int = flags

        override def mergeReader(flags: Int): Int = flags | ClassReader.EXPAND_FRAMES
      })
  }
}

class ReturnVisitor(cv:ClassVisitor) extends ClassVisitor(Opcodes.ASM5, cv) {
  override def visitMethod(access: Int, name: String, desc: String, signature: String, exceptions: Array[String]): MethodVisitor = {
    val mv = super.visitMethod(access, name, desc, signature, exceptions)
    if (!name.startsWith("<init>")) {
      return mv
    }
    if ((Opcodes.ACC_PUBLIC & access) == 0) {
      // skipping non public methods
      return mv
    }
    new MethodReturnAdapter(mv,access,name,desc)
  }
}

class MethodReturnAdapter(mv:MethodVisitor,access:Int, name:String,desc:String) extends AdviceAdapter(Opcodes.ASM5,mv,access,name,desc) {
  override def onMethodExit(opcode: Int): Unit = {
    if (opcode != Opcodes.ATHROW) {
      mv.visitVarInsn(Opcodes.ALOAD, 0)
      mv.visitFieldInsn(Opcodes.GETSTATIC, "kamon/trace/TraceContextAware$", "MODULE$", "Lkamon/trace/TraceContextAware$;")
      mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "kamon/trace/TraceContextAware$", "default", "()Lkamon/trace/TraceContextAware;", false)
      mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "kamon/trace/TraceContextAware", "traceContext", "()Lkamon/trace/TraceContext;", true)
      mv.visitFieldInsn(Opcodes.PUTFIELD, "scala/concurrent/impl/CallbackRunnable", "traceContext", "Lkamon/trace/TraceContext;")
    }
  }
}


//object ConstructorInstrumentation {
//  val traceContextAware:Field  = Class.forName("kamon.trace.TraceContextAware$").getDeclaredField("MODULE$")
//  val defaultMethod:Method =  Class.forName("kamon.trace.TraceContextAware$").getMethod("default")
//  val traceContextMethod:Method =  Class.forName("kamon.trace.TraceContextAware").getMethod("traceContext")
//
//  def apply() = new Implementation {
//    override def prepare(instrumentedType: InstrumentedType): InstrumentedType = instrumentedType
//
//    override def appender(implementationTarget: Target): ByteCodeAppender = new ByteCodeAppender {
//      def apply(methodVisitor: MethodVisitor, implementationContext: Context, instrumentedMethod: MethodDescription): Size = {
//        val size = new Compound(
//          MethodVariableAccess.REFERENCE.loadOffset(0),
//          MethodVariableAccess.REFERENCE.loadOffset(1),
//          FieldAccess2.forField(implementationTarget.getTypeDescription.getDeclaredFields.get(0)).putter(),
//          MethodVariableAccess.REFERENCE.loadOffset(0),
//          MethodVariableAccess.REFERENCE.loadOffset(2),
//          FieldAccess2.forField(implementationTarget.getTypeDescription.getDeclaredFields.get(1)).putter(),
//          MethodVariableAccess.REFERENCE.loadOffset(0),
//          MethodInvocation.invoke(new TypeDescription.ForLoadedType(classOf[Object]).getDeclaredMethods.filter(isConstructor[MethodDescription].and(takesArguments(0))).getOnly),
//          MethodVariableAccess.REFERENCE.loadOffset(0),
//
//          FieldAccess2.forField(new FieldDescription.ForLoadedField(traceContextAware)).getter(),
//          MethodInvocation.invoke(new ForLoadedMethod(defaultMethod)),
//          MethodInvocation.invoke(new ForLoadedMethod(traceContextMethod)),
//          FieldAccess2.forField(implementationTarget.getTypeDescription.getDeclaredFields.get(3)).putter(),
//          MethodVariableAccess.REFERENCE.loadOffset(0),
//          NullConstant.INSTANCE,
//          FieldAccess2.forField(implementationTarget.getTypeDescription.getDeclaredFields.get(2)).putter(),
//          MethodReturn.VOID
//        ).apply(methodVisitor, implementationContext)
//        new Size(size.getMaximalSize, instrumentedMethod.getStackSize)
//      }
//    }
//  }
//}

