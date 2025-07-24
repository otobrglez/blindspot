package com.pinkstack.blindspot.config

import com.pinkstack.blindspot.config.Environment.Development
import enumeratum.*
import zio.*
import zio.Config.*
import zio.config.*
import zio.config.magnolia.*
import zio.http.URL

sealed trait Environment extends EnumEntry

object Environment extends Enum[Environment] with CirceEnum[Environment]:
  case object Test        extends Environment
  case object Production  extends Environment
  case object Development extends Environment
  val values: IndexedSeq[Environment] = findValues

type Port = Int
final case class AppConfig(
  @name("port")
  port: Port = 7779,
  @name("postgres_user")
  postgresUser: String,
  @name("postgres_password")
  postgresPassword: String,
  @name("postgres_host")
  postgresHost: String,
  @name("postgres_port")
  postgresPort: Int,
  @name("postgres_db")
  postgresDb: String,
  @name("blindspot_env")
  environment: Environment = Development
)

object AppConfig:
  private given Config[URL] = Config.string.mapOrFail: raw =>
    URL.decode(raw).left.map(err => zio.Config.Error.InvalidData(message = err.getMessage))

  private def configDefinition: Config[AppConfig] = deriveConfig[AppConfig]
  def config: ZIO[Any, Error, AppConfig]          = ZIO.config(configDefinition)
  def port: IO[Error, Port]                       = config.map(_.port)
  def portLayer: TaskLayer[Port]                  = ZLayer.fromZIO(port)
  def environment: ZIO[Any, Error, Environment]   = config.map(_.environment)

  