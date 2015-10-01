/* ===================================================
 * Copyright © 2015 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */

//jmh:run -i 5 -wi 5 -f1 -t1 .*Tracer.* -prof stack

package test

import java.util.concurrent.TimeUnit

import kamon.Kamon
import kamon.trace.{ TraceContext, Tracer }
import org.openjdk.jmh.annotations._
import org.openjdk.jmh.annotations.TearDown

import scala.annotation.tailrec

@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS) //@Fork(3)
@State(Scope.Thread)
class TracerBench {

  @volatile var i = 0;

  def factorial(number: Int): Int = {
    @tailrec
    def factorialWithAccumulator(accumulator: Int, number: Int): Int = {
      if (number == 1) accumulator
      else factorialWithAccumulator(accumulator * number, number - 1)
    }
    factorialWithAccumulator(1, number)
  }

  var currentContext: TraceContext = _

  @Setup
  def setup: Unit = {
    Kamon.start()
    currentContext = Kamon.tracer.newContext("bench-context")
  }

  @TearDown
  def tearDown = {
    Kamon.shutdown()
  }

  @Benchmark
  def traceRecorderSpeedBaseline(): Long = {
    Tracer.withContext(currentContext)(0)
  }

  @Benchmark
  def traceNewRecorderSpeed(): Long = {
    i += 1
    Tracer.withNewContext(s"Trace-$i", autoFinish = true)(factorial(10))
  }

  @Benchmark
  def traceRecorderSpeed(): Long = {
    Tracer.withContext(currentContext)(factorial(10))
  }
}

