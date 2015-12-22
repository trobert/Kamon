package kamon.instrumentation

import net.bytebuddy.agent.builder.AgentBuilder.Transformer
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.dynamic.DynamicType.Builder

object InstrumentationUtils {
  @inline def withTransformer(f: ⇒ (Builder[_], TypeDescription) ⇒ Builder[_]): Transformer = new Transformer {
    override def transform(builder: Builder[_], typeDescription: TypeDescription): Builder[_] = {
      f.apply(builder, typeDescription)
    }
  }
}
