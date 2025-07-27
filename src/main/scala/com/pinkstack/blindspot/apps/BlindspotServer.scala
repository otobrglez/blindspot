package com.pinkstack.blindspot.apps

import com.pinkstack.blindspot.{Assistant, AssistantMemory, Grid, GridParams, Model}
import com.pinkstack.blindspot.ZIOOps.logExecution
import com.pinkstack.blindspot.db.DB
import io.circe.*
import io.circe.syntax.*
import zio.*
import zio.ZIO.{logError, logInfo}
import zio.http.*
import zio.http.Header.AccessControlAllowOrigin
import zio.http.Middleware.{cors, CorsConfig}
import zio.stream.ZStream
import com.pinkstack.blindspot.clients.OpanAI

object BlindspotServer:
  def myStream = ZStream
    .from(List(1, 2, 3, 4, 5, 6, 7, 8, 9, 10))
    .schedule(Schedule.spaced(2.seconds))
    .map(i => s"Number is ${i}. ")
    .map(s => ServerSentEvent(s))

  Route

  private val assistantRoutes: Routes[DB & AssistantMemory & OpanAI, Nothing] = Routes(
    Method.GET / "assistant" -> handler { (request: Request) =>
      for
        openAI <- ZIO.service[OpanAI]
        memory <- ZIO.service[AssistantMemory]
        grid   <- ZIO.fromEither(GridParams.fromRequest(request)).flatMap(Grid.fromParams).orDie
      yield Response
        .fromServerSentEvents(Assistant.sseStreamFromRequest(openAI, memory, grid, request))
        .addHeader(Header.CacheControl.NoCache)
    }
  )

  private val routes: Routes[DB & OpanAI, Nothing] = Routes(
    Method.GET / "health"      -> handler(Response.ok),
    Method.GET / "grid"        -> handler { (req: Request) =>
      ZIO
        .fromEither(GridParams.fromRequest(req))
        .flatMap(Grid.fromParams)
        .map(rows => Response.json(rows.asJson.noSpaces))
    },
    Method.GET / "test-stream" -> handler {
      Response
        .fromServerSentEvents(myStream)
        .addHeader(Header.CacheControl.NoCache)
    },
    Method.GET / "countries"   -> handler {
      val countries = Model.supportedCountries.toList.sortBy(country => (-country.priority, country.name))
      Response.json(countries.asJson.noSpaces)
    },
    Method.GET / "packages"    -> handler {
      val packages = Model.supportedPackages.toList.sortBy(pkg => (-pkg.priority, pkg.name))
      Response.json(packages.asJson.noSpaces)
    },
    Method.GET / "config"      -> handler {
      val config = Json.obj(
        "packages"  -> Model.supportedPackages.toList.sortBy(pkg => -pkg.priority -> pkg.name).asJson,
        "countries" -> Model.supportedCountries.toList.sortBy(country => -country.priority -> country.name).asJson,
        "platforms" -> Model.supportedPlatforms.toList.sortBy(p => -p.priority -> p.name).asJson
      )
      Response.json(config.asJson.noSpaces)
    }
  ).handleError(th =>
    Response
      .json(Json.obj("error" -> Json.fromString(th.getMessage)).noSpaces)
      .copy(status = Status.InternalServerError)
  )

  private val corsConfig: CorsConfig = CorsConfig(
    allowedOrigin = _ => Some(AccessControlAllowOrigin.All),
    allowedMethods = Header.AccessControlAllowMethods.All
  )

  def run = {
    for
      // scope  <- ZIO.service[Scope]
      _   <- ZIO.unit
      port = 7779
      _   <- DB.migrate.logExecution("Migration")
      _   <- logInfo(s"Booting server on port ${port}")
      _   <-
        Server
          .serve(routes = (routes ++ assistantRoutes) @@ cors(corsConfig) @@ Middleware.debug)
          .provide(Server.defaultWithPort(port), DB.transactorLayer, OpanAI.liveWithAsyncClient, AssistantMemory.live)
      _   <- ZIO.never
    yield ()
  }.tapError(th => zio.Console.printLine(s"🔥 Crashed: ${th.getMessage} @ ${th.printStackTrace()}"))
