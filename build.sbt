import Dependencies.*
import com.typesafe.sbt.packager.docker.{Cmd, DockerPermissionStrategy}
import sbtassembly.AssemblyKeys.assembly
import sbtassembly.{MergeStrategy, PathList}
val scala3Version = "3.7.1"

ThisBuild / dynverVTagPrefix  := false
ThisBuild / dynverSeparator   := "-"
ThisBuild / scalaVersion      := scala3Version
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision
ThisBuild / resolvers ++= Dependencies.projectResolvers

lazy val root = project
  .enablePlugins(BuildInfoPlugin, JavaAgent, JavaAppPackaging, LauncherJarPlugin, DockerPlugin)
  .in(file("."))
  .settings(
    buildInfoKeys    := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion),
    buildInfoPackage := "com.pinkstack.blindspot.info"
  )
  .settings(
    name         := "blindspot",
    scalaVersion := scala3Version,
    libraryDependencies ++= {
      zio ++ json ++ db ++ jsoup ++ enumeratum ++ openai
      // zio ++ db ++ scheduler ++
      //  json ++ jwt ++ jsoup ++ ical4j ++ enumeratum ++ playwright
    },
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-feature",
      "-unchecked",
      "-explain",
      "-Yretain-trees",
      "-Xmax-inlines:100",
      "-Ximplicit-search-limit:150000",
      "-language:implicitConversions",
      "-Wunused:all"
    )
  )
  .settings(javaAgents += "io.sentry" % "sentry-opentelemetry-agent" % Versions.sentryAgent)
  .settings(
    assembly / mainClass             := Some("com.pinkstack.blindspot.apps.Main"),
    assembly / assemblyJarName       := "blindspot.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("module-info.class")                        => MergeStrategy.discard
      case PathList("META-INF", "jpms.args")                    => MergeStrategy.discard
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.first
      case PathList("deriving.conf")                            => MergeStrategy.last
      case PathList(ps @ _*) if ps.last endsWith ".class"       => MergeStrategy.last
      case x                                                    => val old = (assembly / assemblyMergeStrategy).value; old(x)
    }
  )
  .settings(
    dockerExposedPorts ++= Seq(7779),
    dockerExposedUdpPorts    := Seq.empty[Int],
    dockerUsername           := Some("otobrglez"),
    dockerUpdateLatest       := true,
    dockerRepository         := Some("registry.ogrodje.si"),
    dockerBaseImage          := "azul/zulu-openjdk:24-jre-headless-latest",
    Docker / daemonUserUid   := None,
    Docker / daemonUser      := "root",
    dockerPermissionStrategy := DockerPermissionStrategy.None,
    packageName              := "blindspot",
    // dockerAliases ++= Seq(
    //   // dockerAlias.value.withTag(Option("stable")),
    //   dockerAlias.value.withRegistryHost(Option("registry.ogrodje.si"))
    // ),
    dockerCommands           := dockerCommands.value.flatMap {
      case cmd @ Cmd("WORKDIR", _) =>
        List(
          Cmd("LABEL", "maintainer=\"Oto Brglez <otobrglez@gmail.com>\""),
          Cmd("LABEL", "org.opencontainers.image.url=https://github.com/otobrglez/blindspot"),
          Cmd("LABEL", "org.opencontainers.image.source=https://github.com/ogrodje/blindspot"),
          Cmd("ENV", "PORT=7779"),
          Cmd("ENV", s"BLINDSPOT_VERSION=${version.value}"),
          cmd
        )
      case other                   => List(other)
    },
    dockerBuildCommand := {
      val arch = sys.props("os.arch")
      if (arch != "amd64" && !arch.contains("x86")) {
        // use buildx with platform to build supported amd64 images on other CPU architectures
        // this may require that you have first run 'docker buildx create' to set docker buildx up
        dockerExecCommand.value ++ Seq(
          "buildx",
          "build",
          "--platform=linux/amd64",
          "--load") ++ dockerBuildOptions.value :+ "."
      } else dockerBuildCommand.value
    }
  )

addCommandAlias("fmt", ";scalafmtAll;scalafmtSbt")
addCommandAlias("fix", ";scalafixAll")
