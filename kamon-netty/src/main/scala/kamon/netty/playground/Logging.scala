package kamon.netty.playground

import java.util.logging.Logger

trait Logging {
  protected lazy val logger = Logger.getLogger(getClass.getName)
}
