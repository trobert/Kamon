package kamon.netty

import kamon.metric.instrument.{InstrumentFactory, Memory}
import kamon.metric.{EntityRecorderFactory, GenericEntityRecorder}
import kamon.netty.instrumentation.ThroughputHandler

class ThroughputMetrics(instrumentFactory: InstrumentFactory) extends GenericEntityRecorder(instrumentFactory) {

  gauge("read-bytes", Memory.Bytes, () ⇒ {
    println( ThroughputHandler.readBytes())

    ThroughputHandler.readBytes()
  })

  gauge("read-bytes-total", Memory.Bytes, () ⇒ {
    println(ThroughputHandler.readBytesTotal())

    ThroughputHandler.readBytesTotal()
  })

  gauge("written-bytes", Memory.Bytes, () ⇒ {
    println( ThroughputHandler.writtenBytes())

    ThroughputHandler.writtenBytes()
  })

  gauge("written-bytes-total", Memory.Bytes, () ⇒ {
    println( ThroughputHandler.writtenBytesTotal())
    ThroughputHandler.writtenBytesTotal()
  })

}

object ThroughputMetrics extends EntityRecorderFactory[ThroughputMetrics] {
  def category: String = "netty-throughput"
  def createRecorder(instrumentFactory: InstrumentFactory): ThroughputMetrics = new ThroughputMetrics(instrumentFactory)
}