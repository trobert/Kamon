/*
 * =========================================================================================
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

package kamon.netty.instrumentation

import kamon.Kamon
import kamon.metric.Entity
import kamon.netty.EventLoopMetrics
import kamon.util.Latency
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation._

@Aspect
class NettyInstrumentation {

  @After("execution(io.netty.channel.EventLoop+.new(..)) && this(eventLoop)")
  def onNewEventLoop(eventLoop:EventLoopWithMetrics):Unit ={
    val eventLoopEntity = Entity(eventLoop.getClass.getSimpleName, EventLoopMetrics.category)
    val eventLoopRecorder = Kamon.metrics.entity(EventLoopMetrics, eventLoopEntity)

    eventLoop.entity = eventLoopEntity
    eventLoop.recorder = Some(eventLoopRecorder)
  }

  @AfterReturning("execution(* io.netty.channel.EventLoop+.register(*, *)) && this(eventLoop)")
  def onRegister(eventLoop:EventLoopWithMetrics): Unit = {
      eventLoop.recorder.foreach(recorder => recorder.totalChannelsRegistered.increment())
  }

  @AfterReturning("execution(* io.netty.util.concurrent.SingleThreadEventExecutor+.cancel(..)) && this(eventLoop)")
  def onCancelTasks(eventLoop:EventLoopWithMetrics): Unit = {
    eventLoop.recorder.foreach(recorder => recorder.totalChannelsRegistered.decrement())
  }

  @Around("execution(* io.netty.util.concurrent.SingleThreadEventExecutor+.runAllTasks(..)) && this(eventLoop)")
  def onRunAllTasks(pjp:ProceedingJoinPoint, eventLoop:EventLoopWithMetrics): Any = {
    eventLoop.recorder.map(recorder => Latency.measure(recorder.loopExecutionTime)(pjp.proceed())).getOrElse(pjp.proceed())
  }
}

trait EventLoopWithMetrics {
  var entity: Entity = _
  var recorder: Option[EventLoopMetrics] = None
}

@Aspect
class MetricsIntoSingleEventLoopMixin {

  @DeclareMixin("io.netty.channel.EventLoop+")
  def mixinEventLoopWithMetricsTEventLoop: EventLoopWithMetrics = new EventLoopWithMetrics {}
}

