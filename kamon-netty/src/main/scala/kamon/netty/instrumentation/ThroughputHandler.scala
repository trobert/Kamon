package kamon.netty.instrumentation

import java.util.concurrent.Executors

import io.netty.handler.traffic.GlobalTrafficShapingHandler

class ThroughputHandler extends GlobalTrafficShapingHandler(Executors.newScheduledThreadPool(1), 0){

  def readBytes(): Long = trafficCounter.lastReadBytes()
  def readBytesTotal(): Long = trafficCounter.cumulativeReadBytes()

  def writtenBytes(): Long = trafficCounter.lastWrittenBytes()
  def writtenBytesTotal(): Long = trafficCounter.cumulativeWrittenBytes()
}

object ThroughputHandler extends ThroughputHandler


//http://www.javased.com/?source_dir=hotpotato/src/functionaltest/java/com/biasedbit/hotpotato/pipelining/Http11PipeliningTestServer.java"""