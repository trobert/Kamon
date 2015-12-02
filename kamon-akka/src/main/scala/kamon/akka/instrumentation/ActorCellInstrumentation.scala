/*
 * =========================================================================================
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

package akka.kamon.instrumentation

import java.io.ObjectStreamException

import akka.actor._
import akka.dispatch.{ Envelope, MessageDispatcher }
import akka.routing.{ RoutedActorRef, RoutedActorCell }
import kamon.Kamon
import kamon.akka.{ AkkaExtension, ActorMetrics, RouterMetrics }
import kamon.metric.{ Entity, MetricsModule }
import kamon.trace._
import kamon.util.NanoTimestamp
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

import scala.collection.immutable

case class EnvelopeContext(nanoTime: NanoTimestamp, context: TraceContext, router: Option[RouterMetrics])

object EnvelopeContext {
  val Empty = EnvelopeContext(new NanoTimestamp(0L), EmptyTraceContext, None)
}

trait InstrumentedEnvelope {
  def envelopeContext(): EnvelopeContext
  def setEnvelopeContext(envelopeContext: EnvelopeContext): Unit
}

object InstrumentedEnvelope {
  def apply(): InstrumentedEnvelope = new InstrumentedEnvelope {
    var envelopeContext: EnvelopeContext = _

    def setEnvelopeContext(envelopeContext: EnvelopeContext): Unit =
      this.envelopeContext = envelopeContext
  }
}

trait ActorInstrumentation {
  def captureEnvelopeContext(): EnvelopeContext
  def processMessage(pjp: ProceedingJoinPoint, envelopeContext: EnvelopeContext): AnyRef
  def processFailure(failure: Throwable): Unit
  def cleanup(): Unit
}

object CellInstrumentation {
  case class CellInfo(entity: Entity, isRouter: Boolean, isRoutee: Boolean, isTracked: Boolean)

  private def cellName(system: ActorSystem, ref: ActorRef): String =
    system.name + "/" + ref.path.elements.mkString("/")

  private def cellInfoFor(cell: Cell, system: ActorSystem, ref: ActorRef, parent: ActorRef): CellInfo = {
    val pathString = ref.path.elements.mkString("/")
    val isRootSupervisor = pathString.length == 0 || pathString == "user" || pathString == "system"

    val isRouter = cell.isInstanceOf[RoutedActorCell]
    val isRoutee = parent.isInstanceOf[RoutedActorRef]
    val tags = if (isRoutee) {
      Map("router" -> parent.path.elements.mkString("/"))
    } else Map.empty[String, String]

    val category = if (isRouter) RouterMetrics.category else ActorMetrics.category
    val entity = Entity(cellName(system, ref), category, tags)
    val isTracked = !isRootSupervisor && Kamon.metrics.shouldTrack(entity)

    CellInfo(entity, isRouter, isRoutee, isTracked)
  }

  def createActorInstrumentation(cell: Cell, system: ActorSystem, ref: ActorRef, parent: ActorRef): ActorInstrumentation = {
    import kamon.akka.TraceContextPropagationSettings._
    val cellInfo = cellInfoFor(cell, system, ref, parent)
    def actorMetrics = Kamon.metrics.entity(ActorMetrics, cellInfo.entity)

    if (cellInfo.isRouter)
      NoOpInstrumentation
    else {

      AkkaExtension.traceContextPropagation match {
        case Off if cellInfo.isTracked                 ⇒ new ActorMetricsInstrumentation(cellInfo.entity, actorMetrics)
        case Off                                       ⇒ NoOpInstrumentation
        case MonitoredActorsOnly if cellInfo.isTracked ⇒ new FullInstrumentation(cellInfo.entity, actorMetrics)
        case MonitoredActorsOnly                       ⇒ NoOpInstrumentation
        case Always if cellInfo.isTracked              ⇒ new FullInstrumentation(cellInfo.entity, actorMetrics)
        case Always                                    ⇒ ContextPropagationInstrumentation
      }
    }
  }
}

object NoOpInstrumentation extends ActorInstrumentation {
  def captureEnvelopeContext(): EnvelopeContext =
    EnvelopeContext.Empty

  def processMessage(pjp: ProceedingJoinPoint, envelopeContext: EnvelopeContext): AnyRef =
    pjp.proceed()

  def cleanup(): Unit = {}

  def processFailure(failure: Throwable): Unit = {}
}

object ContextPropagationInstrumentation extends ActorInstrumentation {
  def captureEnvelopeContext(): EnvelopeContext =
    EnvelopeContext(new NanoTimestamp(0L), Tracer.currentContext, None)

  def processMessage(pjp: ProceedingJoinPoint, envelopeContext: EnvelopeContext): AnyRef =
    Tracer.withContext(envelopeContext.context)(pjp.proceed())

  def cleanup(): Unit = {}

  def processFailure(failure: Throwable): Unit = {}
}

class ActorMetricsInstrumentation(entity: Entity, actorMetrics: ActorMetrics) extends ActorInstrumentation {
  def captureEnvelopeContext(): EnvelopeContext = {
    actorMetrics.mailboxSize.increment()
    EnvelopeContext(NanoTimestamp.now, EmptyTraceContext, None)
  }

  def processMessage(pjp: ProceedingJoinPoint, envelopeContext: EnvelopeContext): AnyRef = {
    val timestampBeforeProcessing = NanoTimestamp.now

    try {
      pjp.proceed()
    } finally {
      val timestampAfterProcessing = NanoTimestamp.now

      val timeInMailbox = timestampBeforeProcessing - envelopeContext.nanoTime
      val processingTime = timestampAfterProcessing - timestampBeforeProcessing

      actorMetrics.processingTime.record(processingTime.nanos)
      actorMetrics.timeInMailbox.record(timeInMailbox.nanos)
      actorMetrics.mailboxSize.decrement()

      envelopeContext.router.map { routerMetrics ⇒
        routerMetrics.processingTime.record(processingTime.nanos)
        routerMetrics.timeInMailbox.record(timeInMailbox.nanos)
      }

    }
  }

  def cleanup(): Unit =
    Kamon.metrics.removeEntity(entity)

  def processFailure(failure: Throwable): Unit =
    actorMetrics.errors.increment()
}

class FullInstrumentation(entity: Entity, actorMetrics: ActorMetrics) extends ActorMetricsInstrumentation(entity, actorMetrics) {
  override def captureEnvelopeContext(): EnvelopeContext = {
    println("CAPTURED: " + Tracer.currentContext)
    /*println((new Throwable).getStackTraceString)*/
    actorMetrics.mailboxSize.increment()
    EnvelopeContext(NanoTimestamp.now, Tracer.currentContext, None)
  }

  override def processMessage(pjp: ProceedingJoinPoint, envelopeContext: EnvelopeContext): AnyRef = {
    println("Processing with: " + envelopeContext.context)
    Tracer.withContext(envelopeContext.context) {
      super.processMessage(pjp, envelopeContext)
    }
  }
}

trait RouterInstrumentation {
  def routeeAdded(): Unit
  def routeeRemoved(): Unit
}

class RouterMetricsInstrumentation(routerMetrics: RouterMetrics) {
  private val _metricsOpt = Some(routerMetrics)

  def captureEnvelopeContext(): EnvelopeContext = {
    EnvelopeContext(NanoTimestamp.now, EmptyTraceContext, _metricsOpt)
  }

  def processMessage(pjp: ProceedingJoinPoint, envelopeContext: EnvelopeContext): AnyRef = {
    val timestampBeforeProcessing = NanoTimestamp.now

    try {
      pjp.proceed()
    } finally {
      val timestampAfterProcessing = NanoTimestamp.now

      val timeInMailbox = timestampBeforeProcessing - envelopeContext.nanoTime
      val processingTime = timestampAfterProcessing - timestampBeforeProcessing

      routerMetrics.processingTime.record(processingTime.nanos)
      routerMetrics.timeInMailbox.record(timeInMailbox.nanos)

    }
  }
}

@Aspect
class ActorCellInstrumentation {

  def actorInstrumentation(cell: Cell): ActorInstrumentation =
    cell.asInstanceOf[ActorInstrumentationAware].actorInstrumentation

  @Pointcut("execution(akka.actor.ActorCell.new(..)) && this(cell) && args(system, ref, *, *, parent)")
  def actorCellCreation(cell: Cell, system: ActorSystem, ref: ActorRef, parent: InternalActorRef): Unit = {}

  @Pointcut("execution(akka.actor.UnstartedCell.new(..)) && this(cell) && args(system, ref, *, parent)")
  def repointableActorRefCreation(cell: Cell, system: ActorSystem, ref: ActorRef, parent: InternalActorRef): Unit = {}

  @After("actorCellCreation(cell, system, ref, parent)")
  def afterCreation(cell: Cell, system: ActorSystem, ref: ActorRef, parent: ActorRef): Unit = {
    cell.asInstanceOf[ActorInstrumentationAware].setActorInstrumentation(
      CellInstrumentation.createActorInstrumentation(cell, system, ref, parent))
  }

  @After("repointableActorRefCreation(cell, system, ref, parent)")
  def afterCreation2(cell: Cell, system: ActorSystem, ref: ActorRef, parent: ActorRef): Unit = {
    cell.asInstanceOf[ActorInstrumentationAware].setActorInstrumentation(
      CellInstrumentation.createActorInstrumentation(cell, system, ref, parent))
  }

  @Pointcut("execution(* akka.actor.ActorCell.invoke(*)) && this(cell) && args(envelope)")
  def invokingActorBehaviourAtActorCell(cell: ActorCell, envelope: Envelope) = {}

  @Around("invokingActorBehaviourAtActorCell(cell, envelope)")
  def aroundBehaviourInvoke(pjp: ProceedingJoinPoint, cell: ActorCell, envelope: Envelope): Any = {
    actorInstrumentation(cell).processMessage(pjp, envelope.asInstanceOf[InstrumentedEnvelope].envelopeContext())
  }

  /**
   *
   *
   */

  @Pointcut("execution(* akka.actor.ActorCell.sendMessage(*)) && this(cell) && args(envelope)")
  def sendMessageInActorCell(cell: Cell, envelope: Envelope): Unit = {}

  @Pointcut("execution(* akka.actor.UnstartedCell.sendMessage(*)) && this(cell) && args(envelope)")
  def sendMessageInActorCell2(cell: Cell, envelope: Envelope): Unit = {}

  @Before("sendMessageInActorCell(cell, envelope)")
  def afterSendMessageInActorCell(cell: Cell, envelope: Envelope): Unit = {
    envelope.asInstanceOf[InstrumentedEnvelope].setEnvelopeContext(
      actorInstrumentation(cell).captureEnvelopeContext())
  }

  @Before("sendMessageInActorCell2(cell, envelope)")
  def afterSendMessageInActorCell2(cell: Cell, envelope: Envelope): Unit = {
    println("REPOINTABLE ABOUT TO SEND: " + envelope + " ==== " + Tracer.currentContext)

    envelope.asInstanceOf[InstrumentedEnvelope].setEnvelopeContext(
      actorInstrumentation(cell).captureEnvelopeContext())
  }

  /*  @Before("sendMessageInActorCell(cell, envelope)")
  def afterSendMessageInActorCell2(cell: ActorCell, envelope: Envelope): Unit = {
    envelope.asInstanceOf[InstrumentedEnvelope].setEnvelopeContext(
      actorInstrumentation(cell).captureEnvelopeContext())
  }*/

  /**
   *
   * @param cell
   */

  @Pointcut("execution(* akka.actor.ActorCell.stop()) && this(cell)")
  def actorStop(cell: ActorCell): Unit = {}

  @After("actorStop(cell)")
  def afterStop(cell: ActorCell): Unit = {
    actorInstrumentation(cell).cleanup()
    // TODO: Capture Stop of RoutedActorCell

    /*val cellMetrics = cell.asInstanceOf[ActorCellMetrics]
    cellMetrics.unsubscribe()

    // The Stop can't be captured from the RoutedActorCell so we need to put this piece of cleanup here.
    if (cell.isInstanceOf[RoutedActorCell]) {
      val routedCellMetrics = cell.asInstanceOf[RoutedActorCellMetrics]
      routedCellMetrics.unsubscribe()
    }*/
  }

  @Pointcut("execution(* akka.actor.ActorCell.handleInvokeFailure(..)) && this(cell) && args(childrenNotToSuspend, failure)")
  def actorInvokeFailure(cell: ActorCell, childrenNotToSuspend: immutable.Iterable[ActorRef], failure: Throwable): Unit = {}

  @Before("actorInvokeFailure(cell, childrenNotToSuspend, failure)")
  def beforeInvokeFailure(cell: ActorCell, childrenNotToSuspend: immutable.Iterable[ActorRef], failure: Throwable): Unit = {
    actorInstrumentation(cell).processFailure(failure)
    /*val cellMetrics = cell.asInstanceOf[ActorCellMetrics]
    cellMetrics.recorder.foreach { am ⇒
      am.errors.increment()
    }

    // In case that this actor is behind a router, count the errors for the router as well.
    val envelope = cell.currentMessage.asInstanceOf[RouterAwareEnvelope]
    if (envelope ne null) {
      // The ActorCell.handleInvokeFailure(..) method is also called when a failure occurs
      // while processing a system message, in which case ActorCell.currentMessage is always
      // null.
      envelope.routerMetricsRecorder.foreach { rm ⇒
        rm.errors.increment()
      }
    }*/
  }
}

@Aspect
class RoutedActorCellInstrumentation {

  @Pointcut("execution(akka.routing.RoutedActorCell.new(..)) && this(cell) && args(system, ref, props, dispatcher, routeeProps, supervisor)")
  def routedActorCellCreation(cell: RoutedActorCell, system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, routeeProps: Props, supervisor: ActorRef): Unit = {}

  @After("routedActorCellCreation(cell, system, ref, props, dispatcher, routeeProps, supervisor)")
  def afterRoutedActorCellCreation(cell: RoutedActorCell, system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, routeeProps: Props, supervisor: ActorRef): Unit = {
    /*    val routerEntity = Entity(system.name + "/" + ref.path.elements.mkString("/"), RouterMetrics.category)

    if (Kamon.metrics.shouldTrack(routerEntity)) {
      val cellMetrics = cell.asInstanceOf[RoutedActorCellMetrics]

      cellMetrics.metrics = Kamon.metrics
      cellMetrics.routerEntity = routerEntity
      cellMetrics.routerRecorder = Some(Kamon.metrics.entity(RouterMetrics, routerEntity))
    }*/
  }

  @Pointcut("execution(* akka.routing.RoutedActorCell.sendMessage(*)) && this(cell) && args(envelope)")
  def sendMessageInRouterActorCell(cell: RoutedActorCell, envelope: Envelope) = {}

  @Around("sendMessageInRouterActorCell(cell, envelope)")
  def aroundSendMessageInRouterActorCell(pjp: ProceedingJoinPoint, cell: RoutedActorCell, envelope: Envelope): Any = {
    /*    val cellMetrics = cell.asInstanceOf[RoutedActorCellMetrics]
    val timestampBeforeProcessing = System.nanoTime()
    val contextAndTimestamp = envelope.asInstanceOf[TimestampedTraceContextAware]

    try {
      Tracer.withContext(contextAndTimestamp.traceContext) {

        // The router metrics recorder will only be picked up if the message is sent from a tracked router.
        RouterAwareEnvelope.dynamicRouterMetricsRecorder.withValue(cellMetrics.routerRecorder) {
          pjp.proceed()
        }
      }
    } finally {
      cellMetrics.routerRecorder.foreach { routerRecorder ⇒
        routerRecorder.routingTime.record(System.nanoTime() - timestampBeforeProcessing)
      }
    }*/
  }
}

trait WithMetricModule {
  var metrics: MetricsModule = _
}

trait ActorCellMetrics extends WithMetricModule {

  var entity: Entity = _
  var recorder: Option[ActorMetrics] = None

  def unsubscribe() = {
    recorder.foreach { _ ⇒
      metrics.removeEntity(entity)
    }
  }
}

trait ActorInstrumentationAware {
  def actorInstrumentation: ActorInstrumentation
  def setActorInstrumentation(ai: ActorInstrumentation): Unit
}

object ActorInstrumentationAware {
  def apply(): ActorInstrumentationAware = new ActorInstrumentationAware {
    private var _ai: ActorInstrumentation = _

    def setActorInstrumentation(ai: ActorInstrumentation): Unit = _ai = ai
    def actorInstrumentation: ActorInstrumentation = _ai
  }
}

trait RoutedActorCellMetrics extends WithMetricModule {
  var routerEntity: Entity = _
  var routerRecorder: Option[RouterMetrics] = None

  def unsubscribe() = {
    routerRecorder.foreach { _ ⇒
      metrics.removeEntity(routerEntity)
    }
  }
}

trait RouterAwareEnvelope {
  def routerMetricsRecorder: Option[RouterMetrics]
}

object RouterAwareEnvelope {
  import scala.util.DynamicVariable
  private[kamon] val dynamicRouterMetricsRecorder = new DynamicVariable[Option[RouterMetrics]](None)

  def default: RouterAwareEnvelope = new RouterAwareEnvelope {
    val routerMetricsRecorder: Option[RouterMetrics] = dynamicRouterMetricsRecorder.value
  }
}

@Aspect
class MetricsIntoActorCellsMixin {

  @DeclareMixin("akka.actor.ActorCell")
  def mixinActorCellMetricsToActorCell: ActorInstrumentationAware = ActorInstrumentationAware()

  @DeclareMixin("akka.actor.UnstartedCell")
  def mixinActorCellMetricsToUnstartedActorCell: ActorInstrumentationAware = ActorInstrumentationAware()

  @DeclareMixin("akka.routing.RoutedActorCell")
  def mixinActorCellMetricsToRoutedActorCell: RoutedActorCellMetrics = new RoutedActorCellMetrics {}

}

@Aspect
class TraceContextIntoEnvelopeMixin {

  @DeclareMixin("akka.dispatch.Envelope")
  def mixinInstrumentationToEnvelope: InstrumentedEnvelope = InstrumentedEnvelope()

  /*  @DeclareMixin("akka.dispatch.Envelope")
  def mixinTraceContextAwareToEnvelope: TimestampedTraceContextAware = TimestampedTraceContextAware.default*/

  /*  @DeclareMixin("akka.dispatch.Envelope")
  def mixinRouterAwareToEnvelope: RouterAwareEnvelope = RouterAwareEnvelope.default*/

  // TODO: THIS PART IS IMPORTANT!!!!
  /*  @Pointcut("execution(akka.dispatch.Envelope.new(..)) && this(ctx)")
  def envelopeCreation(ctx: InstrumentedEnvelope): Unit = {}

  @After("envelopeCreation(ctx)")
  def afterEnvelopeCreation(ctx: InstrumentedEnvelope): Unit = {
    println(s"${Thread.currentThread().getName} Created ENVELOPE: [${ctx.hashCode()}] $ctx   $ctx => ${ctx.asInstanceOf[InstrumentedEnvelope].envelopeContext()}")
    // Necessary to force the initialization of ContextAware at the moment of creation.
    /*ctx.traceContext
    ctx.routerMetricsRecorder*/
  }*/
}