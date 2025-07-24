package com.pinkstack.blindspot.db

import com.pinkstack.blindspot.config.AppConfig
import org.flywaydb.core.Flyway
import zio.*
import zio.ZIO.logInfo
import zio.interop.catz.*
import doobie.*
import doobie.implicits.*
import io.circe.Json
import zio.http.URL
import io.circe.parser.parse
import org.flywaydb.core.api.output.MigrateResult

final class DB private (private val transactor: Transactor[Task]):
  def call: Transactor[Task] = transactor

object DBOps:
  given urlMeta: Meta[URL] = Meta[String].imap(raw => URL.decode(raw).toTry.get)(_.toString)
  given json: Meta[Json]   = Meta[String].tiemap(r => parse(r).left.map(_.getMessage))(_.noSpaces)

object DB:

  private def flyway: Task[Flyway] = for
    db              <- AppConfig.config.map(_.postgresDb)
    host            <- AppConfig.config.map(_.postgresHost)
    port            <- AppConfig.config.map(_.postgresPort)
    user            <- AppConfig.config.map(_.postgresUser)
    password        <- AppConfig.config.map(_.postgresPassword)
    configuredFlyway =
      Flyway
        .configure()
        .locations("migrations")
        .dataSource(s"jdbc:postgresql://$host:$port/$db", user, password)
        .load()
  yield configuredFlyway

  def migrate: Task[MigrateResult] = for
    _      <- logInfo("Migrating,...")
    result <- flyway.map(_.migrate())
  yield result

  def transact[Out](in: ConnectionIO[Out]): RIO[DB, Out] = for
    db  <- ZIO.service[DB]
    out <- in.transact(db.call)
  yield out

  def transactorLayer: ZLayer[Any, Config.Error, DB] = ZLayer.fromZIO:
    for
      db       <- AppConfig.config.map(_.postgresDb)
      host     <- AppConfig.config.map(_.postgresHost)
      port     <- AppConfig.config.map(_.postgresPort)
      user     <- AppConfig.config.map(_.postgresUser)
      password <- AppConfig.config.map(_.postgresPassword)

      tx = Transactor.fromDriverManager[Task](
             driver = "org.postgresql.Driver",
             url = s"jdbc:postgresql://$host:$port/$db",
             user = user,
             password = password,
             logHandler = None
           )
    yield DB(tx)
