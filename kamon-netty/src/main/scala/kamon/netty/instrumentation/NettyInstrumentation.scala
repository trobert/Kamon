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

import java.util.concurrent.TimeUnit

import io.netty.channel.nio.NioEventLoop
import io.netty.channel.{MultithreadEventLoopGroup, EventLoopGroup}
import io.netty.util.concurrent.MultithreadEventExecutorGroup
import kamon.Kamon
import kamon.metric.Entity
import kamon.netty.EventLoopMetrics
import kamon.util.Latency
import org.aspectj.lang.reflect.MethodSignature
import org.aspectj.lang.{JoinPoint, ProceedingJoinPoint}
import org.aspectj.lang.annotation._

@Aspect
class NettyInstrumentation {

  @After("execution(io.netty.channel.SingleThreadEventLoop.new(..)) && this(eventLoop)")
  def onNewEventLoop(eventLoop:EventLoopWithMetrics):Unit ={
    val eventLoopEntity = Entity(eventLoop.getClass.getSimpleName, EventLoopMetrics.category)
    val eventLoopRecorder = Kamon.metrics.entity(EventLoopMetrics, eventLoopEntity)
    println("neweventLoop: " + eventLoop.hashCode() + "thread: " + Thread.currentThread().getName)

    eventLoop.entity = eventLoopEntity
    eventLoop.recorder = Some(eventLoopRecorder)
  }

  @AfterReturning("execution(* io.netty.channel.SingleThreadEventLoop+.register(*, *)) && this(eventLoop)")
  def onRegister(eventLoop:EventLoopWithMetrics): Unit = {
      println("register: " + eventLoop.hashCode() + "thread: " + Thread.currentThread().getName)
      eventLoop.recorder.foreach(recorder => recorder.totalChannelsRegistered.increment())
  }

  @AfterReturning("execution(* io.netty.util.concurrent.SingleThreadEventExecutor+.cancel(..)) && this(eventLoop)")
  def onCancelTasks(eventLoop:EventLoopWithMetrics): Unit = {
    println("cancel: " + eventLoop.hashCode() + "thread: " + Thread.currentThread().getName)
    eventLoop.recorder.foreach(recorder => recorder.totalChannelsRegistered.decrement())
  }

  @Around("execution(* io.netty.util.concurrent.SingleThreadEventExecutor+.runAllTasks(..)) && this(eventLoop)")
  def onRunAllTasks(pjp:ProceedingJoinPoint, eventLoop:EventLoopWithMetrics): Any = {
    println("runAllTasks: " + eventLoop.hashCode() + "thread: " + Thread.currentThread().getName)
    eventLoop.recorder.map(recorder => Latency.measure(recorder.loopExecutionTime)(pjp.proceed())).getOrElse(pjp.proceed())
  }


  @After("execution(* io.netty.bootstrap.ServerBootstrap.group(..)) && args(bossGroup, workerGroup)")
  def onNewServerBootstrap(jp:JoinPoint,bossGroup:NamedEventLoopGroup, workerGroup:NamedEventLoopGroup):Unit ={
    if(bossGroup == workerGroup) {
      bossGroup.name = "boss-group"
      workerGroup.name = "boss-group"
      println(s" instance ${bossGroup.name} and threads ${bossGroup.asInstanceOf[MultithreadEventExecutorGroup].executorCount()}" + "thread: " + Thread.currentThread().getName)
    }else{
      bossGroup.name = "boss-group"
      workerGroup.name = "worker-group"
      println(s" instance ${bossGroup.name} and threads ${bossGroup.asInstanceOf[MultithreadEventExecutorGroup].executorCount()}" + "thread: " + Thread.currentThread().getName)
      println(s" instance ${workerGroup.name} and threads ${workerGroup.asInstanceOf[MultithreadEventExecutorGroup].executorCount()}" + "thread: " + Thread.currentThread().getName)
    }
    println(jp.getSignature.asInstanceOf[MethodSignature].getMethod.getParameterAnnotations)
  }

  @After("execution(io.netty.util.concurrent.MultithreadEventExecutorGroup.new(..)) && args(nThreads, *, *) && this(multiThreadEventExecutor)")
  def onNewMultithreadEventExecutorGroup(multiThreadEventExecutor:MultithreadEventExecutorGroup, nThreads:Int):Unit ={
    println(s" instance ${multiThreadEventExecutor.asInstanceOf[NamedEventLoopGroup].name} and threads $nThreads" + "thread: " + Thread.currentThread().getName)
  }

  @Pointcut("execution(* io.netty.channel.nio.NioEventLoop.select(*))")
  def point():Unit = {}

  var start = 0L;

//  @Before("point()")
//  def bla():Unit = {
//      start = System.currentTimeMillis()
//      println("NioEventLoop.select(*)")
//  }

  @Around("call(int java.nio.channels.Selector.select(..)) && args(timeout) && cflow(execution(* io.netty.channel.nio.NioEventLoop.select(*))) && !within(kamon.netty.instrumentation.NettyInstrumentation)")
  def bl2a(pjs:ProceedingJoinPoint, timeout:Long):Any = {
        val beforeSelect = System.nanoTime()
        val o  =pjs.proceed()
    val l: Long = System.nanoTime() - beforeSelect
    val selectTime= TimeUnit.MILLISECONDS.convert(l,TimeUnit.NANOSECONDS);

    println(s"select time $selectTime timeout $timeout")
    if(selectTime < timeout){
          println("Selector.select "  + "hgjghjghjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjjg")
        }
       o
  }
}

trait EventLoopWithMetrics {
  var entity: Entity = _
  var recorder: Option[EventLoopMetrics] = None
}

trait NamedEventLoopGroup {
  var name:String = _
}

@Aspect
class MetricsIntoSingleEventLoopMixin {

  @DeclareMixin("io.netty.channel.EventLoopGroup+")
  def mixinEventLoopGroupWithNamedEventLoopGroup: NamedEventLoopGroup = new NamedEventLoopGroup {}

  @DeclareMixin("io.netty.channel.EventLoop+")
  def mixinEventLoopWithMetricsTEventLoop: EventLoopWithMetrics = new EventLoopWithMetrics {}
}
