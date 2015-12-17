name := "kamon-agent"
 
version := "1.0"
 
scalaVersion := "2.11.7"

libraryDependencies += "net.bytebuddy" % "byte-buddy" % "0.7.7"

libraryDependencies += "io.kamon" % "kamon-core_2.11" % "0.5.2"

packageOptions in (Compile, packageBin) +=
  Package.ManifestAttributes("Premain-Class" -> "org.kamon.jvm.agent.KamonAgent",
                             "Agent-Class" -> "org.kamon.jvm.agent.KamonAgent",
                             "Can-Redefine-Classes" -> "true",
                             "Can-Set-Native-Method-Prefix" -> "true",
                             "Can-Retransform-Classes" -> "true")