package com.pinkstack.blindspot.apps

import com.pinkstack.blindspot.{Grid, Model}
import com.pinkstack.blindspot.ZIOOps.logExecution
import com.pinkstack.blindspot.db.DB
import io.circe.*
import io.circe.syntax.*
import zio.*
import zio.ZIO.{logError, logInfo}
import zio.http.*
import zio.http.Header.AccessControlAllowOrigin
import zio.http.Middleware.{cors, CorsConfig}

object BlindspotServer:

  private def optionalStringToList(op: Option[String]): List[String] =
    op.fold(List.empty[String])(_.split(",").toList)

  private val routes: Routes[DB, Nothing] = Routes(
    Method.GET / "grid"      -> handler { (req: Request) =>
      Grid
        .showFor(
          query = req.queryOrElse[Option[String]]("query", None),
          kind = optionalStringToList(req.queryOrElse[Option[String]]("kind", None)).map(Model.ItemKind.withName),
          page = req.queryOrElse[Option[Int]]("page", None).getOrElse(1),
          pageSize = req.queryOrElse[Option[Int]]("pageSize", None).getOrElse(100)
        )
        .map(rows => Response.json(rows.asJson.noSpaces))
    },
    Method.GET / "countries" -> handler {
      val countries = Model.supportedCountries.toList.sortBy(country => (-country.priority, country.name))
      Response.json(countries.asJson.noSpaces)
    },
    Method.GET / "packages"  -> handler {
      val packages = Model.supportedPackages.toList.sortBy(pkg => (-pkg.priority, pkg.name))
      Response.json(packages.asJson.noSpaces)
    },
    Method.GET / "config"    -> handler {
      val config = Json.obj(
        "packages"  -> Model.supportedPackages.toList.sortBy(pkg => (-pkg.priority, pkg.name)).asJson,
        "countries" -> Model.supportedCountries.toList.sortBy(country => (-country.priority, country.name)).asJson
      )
      Response.json(config.asJson.noSpaces)
    }
  ).handleErrorZIO(th =>
    logError(s"Served crash of ${th.getMessage}").as {
      Response
        .json(Json.obj("error" -> Json.fromString(th.getMessage)).noSpaces)
        .copy(status = Status.InternalServerError)
    }
  )

  private val corsConfig: CorsConfig = CorsConfig(
    allowedOrigin = _ => Some(AccessControlAllowOrigin.All),
    allowedMethods = Header.AccessControlAllowMethods.All
  )

  def run = for
    // scope  <- ZIO.service[Scope]
    _   <- ZIO.unit
    port = 7779
    _   <- DB.migrate.logExecution("Migration")
    _   <- logInfo(s"Booting server on port ${port}")
    _   <- Server
             .serve(routes = routes @@ cors(corsConfig) @@ Middleware.debug)
             .provide(Server.defaultWithPort(port), DB.transactorLayer)
    _   <- ZIO.never
  yield ()
