package kamon.agent

import java.lang.instrument.Instrumentation

import com.typesafe.config.ConfigFactory
import kamon.instrumentation.KamonInstrumentation
import scala.collection.JavaConverters._

object InstrumentationLoader {
  private val factory = ConfigFactory.load()
  private val config = factory.getConfig("kamon.agent")

  def load(instrumentation:Instrumentation) :Unit ={
    val instrumentationClass = (clazz:String)  => {
      Class.forName(clazz).newInstance().asInstanceOf[KamonInstrumentation]
    }

    config.getStringList("instrumentations").asScala.foreach { clazz =>
      instrumentationClass(clazz).register(instrumentation)
      println(s"$clazz registered.")
    }
  }
}
