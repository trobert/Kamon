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

package kamon.instrumentation.jdbc

import java.lang.instrument.Instrumentation
import java.util.concurrent.Callable

import kamon.util.instrumentation.KamonInstrumentation
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.implementation.FieldAccessor
import net.bytebuddy.implementation.MethodDelegation._
import net.bytebuddy.implementation.bind.annotation._
import net.bytebuddy.matcher.ElementMatcher.Junction
import net.bytebuddy.matcher.ElementMatchers._

class PreparedStatementInstrumentation extends KamonInstrumentation {

  private val PreparedStatement: Junction[TypeDescription] = isSubTypeOf(typePool.describe("java.sql.PreparedStatement").resolve())

  override def register(instrumentation: Instrumentation): Unit = {
    new AgentBuilder.Default()
      .`type`(PreparedStatement, is(ClassLoader.getSystemClassLoader))
      .transform(preparedStatementTransformer)
      .installOn(instrumentation)
  }

  def preparedStatementTransformer = withTransformer { (builder, typeDescription) ⇒
    builder
      .implement(classOf[PreparedStatementExtension]).intercept(FieldAccessor.ofBeanProperty())
      .defineField("sql", classOf[String])
      .method(named("execute")).intercept(to(PreparedStatementInterceptor).filter(NotDeclaredByObject))
      .method(named("executeUpdate")).intercept(to(PreparedStatementInterceptor).filter(NotDeclaredByObject))
      .method(named("executeQuery")).intercept(to(PreparedStatementInterceptor).filter(NotDeclaredByObject))
  }
}

object PreparedStatementInterceptor {
  @RuntimeType
  def execute(@SuperCall callable: Callable[_], @This ps: PreparedStatementExtension): Unit = {
    SqlProcessor.processStatement(callable, ps.getSql)
  }
}

trait PreparedStatementExtension {
  def getSql: String
  def setSql(sql: String): Unit
}