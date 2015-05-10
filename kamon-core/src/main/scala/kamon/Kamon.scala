/* =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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
package kamon

import _root_.akka.actor
import _root_.akka.actor._
import com.typesafe.config.{ ConfigFactory, Config }
import kamon.metric._
import kamon.trace.{ TracerModuleImpl, TracerModule }

object Kamon {
  trait Extension extends actor.Extension

  private case class KamonCoreComponents(metrics: MetricsModule, tracer: TracerModule)

  @volatile private var _system: ActorSystem = _
  @volatile private var _coreComponents: Option[KamonCoreComponents] = None

  def start(config: Config): Unit = synchronized {
    def resolveInternalConfig: Config = {
      val internalConfig = config.getConfig("kamon.internal-config")

      config
        .withoutPath("akka")
        .withoutPath("spray")
        .withFallback(internalConfig)
    }

    if (_coreComponents.isEmpty) {
      val metrics = MetricsModuleImpl(config)
      val tracer = TracerModuleImpl(metrics, config)

      _coreComponents = Some(KamonCoreComponents(metrics, tracer))
      _system = ActorSystem("kamon", resolveInternalConfig)

      metrics.start(_system)
      tracer.start(_system)
      _system.registerExtension(ModuleLoader)

    } else sys.error("Kamon has already been started.")
  }

  def start(): Unit =
    start(ConfigFactory.load)

  def shutdown(): Unit = {
    _coreComponents = None
    _system.shutdown()
    _system = null
  }

  def metrics: MetricsModule =
    ifStarted(_.metrics)

  def tracer: TracerModule =
    ifStarted(_.tracer)

  def apply[T <: Kamon.Extension](key: ExtensionId[T]): T =
    ifStarted { _ ⇒
      if (_system ne null)
        key(_system)
      else
        sys.error("Cannot retrieve extensions while Kamon is being initialized.")
    }

  def extension[T <: Kamon.Extension](key: ExtensionId[T]): T =
    apply(key)

  private def ifStarted[T](thunk: KamonCoreComponents ⇒ T): T =
    _coreComponents.map(thunk(_)) getOrElse (sys.error("Kamon has not been started yet."))

}

trait ModuleSupervisorExtension  extends Kamon.Extension {
  def createModule(name: String, props: Props): Future[ActorRef]
}

class ModuleSupervisorExtensionImpl(system: ExtendedActorSystem) extends  ModuleSupervisorExtension {
  private val supervisor = system.actorOf(Props[ModuleSupervisor], "kamon")

  def createModule(name: String, props: Props): Future[ActorRef] = {
    val modulePromise = Promise[ActorRef]()
    supervisor ! CreateModule(name, props, modulePromise)
    modulePromise.future
  }
}

class ModuleSupervisor extends Actor with ActorLogging {

  def receive = {
    case CreateModule(name, props, childPromise) => createChildModule(name, props, childPromise)
  }

  def createChildModule(name: String, props: Props, childPromise: Promise[ActorRef]): Unit = {
    context.child(name).map { alreadyAvailableModule =>
      log.warning("Received a request to create module [{}] but the module is already available, returning the existent one.")
      childPromise.complete(Success(alreadyAvailableModule))

    } getOrElse {
      childPromise.complete(Success(context.actorOf(props, name)))
    }
  }
}

object ModuleSupervisor extends ExtensionId[ModuleSupervisorExtension] with ExtensionIdProvider {
  def lookup(): ExtensionId[_ <: actor.Extension] = ModuleSupervisor
  def createExtension(system: ExtendedActorSystem): ModuleSupervisorExtension = new ModuleSupervisorExtensionImpl(system)

  case class CreateModule(name: String, props: Props, childPromise: Promise[ActorRef])
}
