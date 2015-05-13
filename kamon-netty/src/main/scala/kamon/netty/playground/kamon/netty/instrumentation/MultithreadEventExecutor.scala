package kamon.netty.playground.kamon.netty.instrumentation

import org.aspectj.lang.annotation.{Around, Aspect}

@Aspect
class MultithreadEventExecutor {

  @Around("execution(* )")
  def onNew(){}
}
