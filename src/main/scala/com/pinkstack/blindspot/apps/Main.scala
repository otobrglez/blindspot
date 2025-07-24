package com.pinkstack.blindspot.apps

import zio.*
import zio.Runtime.{removeDefaultLoggers, setConfigProvider}
import zio.cli.*
import zio.cli.HelpDoc.Empty
import zio.logging.backend.SLF4J

object Main extends ZIOCliDefault:
  override val bootstrap: ZLayer[ZIOAppArgs, Any, Any] =
    setConfigProvider(ConfigProvider.envProvider) >>> removeDefaultLoggers >>> SLF4J.slf4j

  // Options
  private val port: Options[BigInt]             = Options.integer("port").withDefault(BigInt(7779))
  private val postgresUser: Options[String]     = Options.text("postgres-user").withDefault("postgres")
  private val postgresPassword: Options[String] = Options.text("postgres-password")
  private val postgresHost: Options[String]     = Options.text("postgres-host").withDefault("localhost")
  private val postgresPort: Options[BigInt]     = Options.integer("postgres-port").withDefault(BigInt(5432))
  private val postgresDb: Options[String]       = Options.text("postgres-db").withDefault("blindspot")
  private val blindspotEnv: Options[String]     = Options.text("blindspot-env").withDefault("Production")

  private val dbOptions = postgresUser ++ postgresPassword ++ postgresHost ++ postgresPort ++ postgresDb

  // Commands
  private val mainCommand = Command("blindspot").withHelp("Blindspot suite")

  private val refreshJustWatch =
    Command("refresh-just-watch", options = dbOptions ++ blindspotEnv).withHelp("Refresh JustWatch")

  private val command = mainCommand.subcommands(refreshJustWatch)

  val cliApp: CliApp[ZIOAppArgs & Scope, Throwable, Unit] =
    CliApp.make(
      name = "blindspot",
      version = "x",
      summary = HelpDoc.Span.empty,
      footer = Empty,
      command = command
    ) {
      case (
            postgresUser: String,
            postgresPassword: String,
            postgresHost: String,
            postgresPort: BigInt,
            postgresDb: String,
            blindspotEnv: String
          ) =>
        RefreshJustWatch.run.withConfigProvider(
          ConfigProvider.fromMap(
            Map(
              "postgres_user"     -> postgresUser,
              "postgres_password" -> postgresPassword,
              "postgres_host"     -> postgresHost,
              "postgres_port"     -> postgresPort.toString,
              "postgres_db"       -> postgresDb,
              "blindspot_env"     -> blindspotEnv
            )
          )
        )
    }
