package kamon.netty

import kamon.metric.{ EntityRecorderFactory, GenericEntityRecorder }
import kamon.metric.instrument.{ Time, InstrumentFactory }

class EventLoopMetrics(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {
  val totalChannelsRegistered = minMaxCounter("total-channels-registered")
  val loopExecutionTime = histogram("loop-execution-time", Time.Nanoseconds)
}

object EventLoopMetrics extends EntityRecorderFactory[EventLoopMetrics] {
  def category: String = "netty-event-loop"
  def createRecorder(instrumentFactory: InstrumentFactory): EventLoopMetrics = new EventLoopMetrics(instrumentFactory)
}