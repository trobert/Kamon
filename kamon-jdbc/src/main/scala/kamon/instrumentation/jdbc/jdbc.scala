package kamon.instrumentation

import net.bytebuddy.agent.builder.AgentBuilder.Transformer
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.dynamic.DynamicType.Builder
import net.bytebuddy.matcher.ElementMatcher.Junction
import net.bytebuddy.matcher.ElementMatchers._
import net.bytebuddy.pool.TypePool

package object jdbc {
  val typePool = TypePool.Default.ofClassPath()
  val NotDeclaredByObject: Junction[MethodDescription] = not(isDeclaredBy(classOf[Object]))
  val TakesArguments: Junction[MethodDescription] = not(takesArguments(0))

  @inline def withTransformer(f: ⇒ (Builder[_], TypeDescription) ⇒ Builder[_]): Transformer = new Transformer {
    override def transform(builder: Builder[_], typeDescription: TypeDescription): Builder[_] = {
      f.apply(builder, typeDescription)
    }
  }
}
