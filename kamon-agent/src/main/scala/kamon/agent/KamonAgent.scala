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

package kamon.agent

import java.lang.instrument.Instrumentation

object KamonAgent  {

  /**
   * JVM hook to statically load the javaagent at startup.
   *
   * After the Java Virtual Machine (JVM) has initialized, the premain method
   * will be called. Then the real application main method will be called.
   *
   * @param args
   * @param instrumentation
   * @throws Exception
   */
  @throws(classOf[Exception])
  def premain(args:String, instrumentation:Instrumentation):Unit = {
    println(s"premain method invoked with args: $args and inst: $instrumentation")
    InstrumentationLoader.load(instrumentation)

//      new AgentBuilder.Default()
//      .withListener(new AgentBuilder.Listener() {
//        override def onError(typeName: String, throwable: Throwable): Unit = {
//          System.out.println("Error - " + typeName+", "+ throwable.getMessage());
//        }
//
//        override def onTransformation(typeDescription: TypeDescription, dynamicType: DynamicType): Unit = {
//          System.out.println("Transformed - " + typeDescription+", type = "+dynamicType);
//        }
//
//        override def onComplete(typeName: String): Unit = {
////          System.out.println("Completed - " + typeName);
//        }
//
//        override def onIgnored(typeDescription: TypeDescription): Unit = {
////          System.out.println("Ignored - " + typeDescription);
//        }
//      })
//      .`type`(named[NamedElement]("scala.concurrent.impl.CallbackRunnable").or(named[NamedElement]("scala.concurrent.impl.Future.PromiseCompletingRunnable")))
//      .transform(FutureInstrumentation())
//      .installOn(inst)
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
