package kamon.instrumentation

import java.lang.instrument.Instrumentation

import net.bytebuddy.agent.builder.AgentBuilder.Transformer
import net.bytebuddy.description.`type`.TypeDescription
import net.bytebuddy.dynamic.DynamicType.Builder

trait KamonInstrumentation {
  def register(instrumentation:Instrumentation):Unit
}

object InstrumentationUtils {
  @inline def withTransformer(f: => (Builder[_], TypeDescription) => Builder[_]): Transformer = new Transformer {
    override def transform(builder: Builder[_], typeDescription: TypeDescription): Builder[_] = {
      f.apply(builder, typeDescription)
    }
  }
}
