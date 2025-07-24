package com.pinkstack.blindspot.clients

import io.circe.{Decoder, Json}
import zio.*
import zio.http.*

trait GraphQLClient:
  protected def client: Client
  private type Query = String

  protected def query(query: Query, variables: (String, Json)*): RIO[Scope, Json] = for
    _      <- ZIO.unit
    payload =
      Json
        .obj("query" -> Json.fromString(query), "variables" -> Json.fromFields(variables))
        .noSpaces

    request =
      Request(
        method = Method.POST,
        body = Body.fromString(text = payload),
        headers = Headers("Content-Type" -> "application/json", "Accept" -> "application/json")
      )

    response <- client.request(request)
    body     <- response.body.asString(Charsets.Utf8)
    json     <- ZIO.fromEither(io.circe.parser.parse(body))
  yield json

  protected def queryAs[Out](queryStr: Query, variables: (String, Json)*)(using
    decoder: Decoder[Out]
  ): RIO[Scope, Out] =
    query(queryStr, variables*).flatMap(json => ZIO.fromEither(json.as[Out]))
