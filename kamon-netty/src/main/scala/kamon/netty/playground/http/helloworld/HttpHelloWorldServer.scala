package kamon.netty.playground.http.helloworld

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelOption
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.util.internal.logging.{InternalLoggerFactory, Slf4JLoggerFactory}
import kamon.Kamon
import kamon.netty.annotation.MetricName
import kamon.netty.playground.HttpHelloWorldServerInitializer

object HttpHelloWorldServer extends App {
  Kamon.start()
  val port = args.headOption.map(_.toInt).getOrElse(8080)
  InternalLoggerFactory.setDefaultFactory(new Slf4JLoggerFactory())
  val bossGroup, workerGroup = new NioEventLoopGroup(20)
  try {
    val b = new ServerBootstrap()
    b.option(ChannelOption.SO_BACKLOG, Int.box(1024))
      .group(bossGroup, workerGroup)
      .channel(classOf[NioServerSocketChannel])
      .childHandler(new HttpHelloWorldServerInitializer())

    val ch = b.bind(port).sync().channel()
    ch.closeFuture().sync()
  } finally {
    bossGroup.shutdownGracefully()
    workerGroup.shutdownGracefully()
    Kamon.shutdown()
  }
}
