/* =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.instrumentation.scala

import java.lang.instrument.Instrumentation

import kamon.instrumentation.InstrumentationUtils.withTransformer
import kamon.trace.{TraceContext, TraceContextAware, Tracer}
import kamon.util.instrumentation.KamonInstrumentation
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

  def futureTransformer = withTransformer { (builder, typeDescription) ⇒
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

