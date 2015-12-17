/* ===================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */

package org.kamon.jvm.agent

import java.io.File
import java.lang.instrument.Instrumentation
import java.lang.reflect.{Field, Method}
import java.util.concurrent.Callable

import kamon.trace.TraceContextAware.DefaultTraceContextAware
import kamon.trace.{TraceContext, TraceContextAware, Tracer}
import net.bytebuddy.ByteBuddy
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.agent.builder.AgentBuilder.Transformer
import net.bytebuddy.description.NamedElement
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.modifier.Visibility
import net.bytebuddy.dynamic.DynamicType.Builder
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy
import net.bytebuddy.dynamic.{DynamicType, ClassFileLocator, TargetType}
import net.bytebuddy.implementation.MethodDelegation._
import net.bytebuddy.implementation.{SuperMethodCall, MethodDelegation, FixedValue}
import net.bytebuddy.implementation.bind.annotation._
import net.bytebuddy.matcher.ElementMatchers
import net.bytebuddy.matcher.ElementMatchers._
import net.bytebuddy.pool.TypePool

object KamonAgent  {

  /**
   * JVM hook to statically load the javaagent at startup.
   *
   * After the Java Virtual Machine (JVM) has initialized, the premain method
   * will be called. Then the real application main method will be called.
   *
   * @param args
   * @param inst
   * @throws Exception
   */
  @throws(classOf[Exception])
  def premain(args:String, inst:Instrumentation):Unit = {
    println(s"premain method invoked with args: $args and inst: $inst")
//    val pool = TypePool.Default.ofClassPath()
//
//    new ByteBuddy().subclass(pool.describe("sc  ala.concurrent.impl.CallbackRunnable").resolve())
//      .implement(classOf[TraceContextAware])
//      .defineField("traceContext", classOf[TraceContextAware], Visibility.PRIVATE)
//      .constructor(any()).intercept(SuperMethodCall.INSTANCE.andThen(to(ConstructorInterceptor)))
//      .method(named("run"))
//      .intercept(to(FutureInstrumentation))
//      .make()
//        .saveIn(new File("/home/diego/puto"))
//      .load(getClass.getClassLoader, ClassLoadingStrategy.Default.WRAPPER)
//      .getLoaded



      new AgentBuilder.Default()
      .withListener(new AgentBuilder.Listener() {
        override def onError(typeName: String, throwable: Throwable): Unit = {
          System.out.println("Error - " + typeName+", "+ throwable.getMessage());
        }

        override def onTransformation(typeDescription: TypeDescription, dynamicType: DynamicType): Unit = {
          System.out.println("Transformed - " + typeDescription+", type = "+dynamicType);
        }

        override def onComplete(typeName: String): Unit = {
//          System.out.println("Completed - " + typeName);
        }

        override def onIgnored(typeDescription: TypeDescription): Unit = {
//          System.out.println("Ignored - " + typeDescription);
        }
      })
      .`type`(named[NamedElement]("scala.concurrent.impl.CallbackRunnable").or(named[NamedElement]("scala.concurrent.impl.Future.PromiseCompletingRunnable")))
      .transform(new Transformer {
        override def transform(builder: Builder[_], typeDescription: TypeDescription): Builder[_] = {
          builder
            .implement(classOf[TraceContextAware])
            .defineField("traceContext", classOf[TraceContextAware], Visibility.PRIVATE)
            .constructor(any()).intercept(SuperMethodCall.INSTANCE.andThen(to(ConstructorInterceptor)))
            .method(named("run"))
            .intercept(to(FutureInstrumentation))
        }
      }).installOn(inst)
  }

  object ConstructorInterceptor {
    def init(@This obj: TraceContextAware) ={
      val traceContext: Field = obj.getClass.getDeclaredField("traceContext")
      traceContext.setAccessible(true)
      traceContext.set(obj, TraceContextAware.default)
    }
  }

  object FutureInstrumentation {
    @RuntimeType
    def run(@This runnable: TraceContextAware, @SuperCall r: Runnable): Unit = {
      Tracer.withContext(runnable.traceContext) {
        r.run()
      }
    }
  }




    /**
   * JVM hook to dynamically load javaagent at runtime.
   *
   * The agent class may have an agentmain method for use when the agent is
   * started after VM startup.
   *
   * @param args
   * @param inst
   * @throws Exception
   */
  @throws(classOf[Exception])
  def agentmain(args:String, inst:Instrumentation ):Unit = {
    println(s"agentmain method invoked with args: $args and inst: $inst")
  }

  /**
   * Programmatic hook to dynamically load javaagent at runtime.
   */
  def initialize():Unit = {
//    if (instrumentation == null) {
      //KamonJavaAgentLoader.loadAgent();
//    }
  }

}
