package kamon.agent

import kamon.trace.{TraceContextAware, TraceContext}

import scala.concurrent.{OnCompleteRunnable, ExecutionContext}
import scala.util.Try
import scala.util.control.NonFatal

private class CallbackRunnable[T](val executor: ExecutionContext, val onComplete: Try[T] => Any) extends  Runnable with OnCompleteRunnable {
  // must be filled in before running it
  val traceContext:TraceContext = TraceContextAware.default.traceContext

  var value: Try[T] = null

  override def run() = {
    require(value ne null) // must set value to non-null before running!
    try onComplete(value) catch { case NonFatal(e) => executor reportFailure e }
  }

  def executeWithValue(v: Try[T]): Unit = {
    require(value eq null) // can't complete it twice
    value = v
    // Note that we cannot prepare the ExecutionContext at this point, since we might
    // already be running on a different thread!
    try executor.execute(this) catch { case NonFatal(t) => executor reportFailure t }
  }
}