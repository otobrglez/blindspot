package com.pinkstack.blindspot.static

import com.pinkstack.blindspot.Model
import io.circe.*
import io.circe.parser.parse
import zio.{Task, ZIO}

import scala.io.Source
import scala.util.Using

object JustWatchStatic:
  final case class Locale(
    fullLocale: String,
    iso31662: String,
    country: String,
    exposedUrlPart: String,
    currency: String,
    timezone: String
  )
  private given decoder: Decoder[Locale] = (c: HCursor) =>
    for
      fullLocale           <- c.downField("full_locale").as[String].map(_.trim)
      iso31662             <- c.downField("iso_3166_2").as[String].map(_.trim)
      country              <- c.downField("country").as[String].map(_.trim)
      exposedUrlPart       <- c.downField("exposed_url_part").as[String].map(_.trim).map(_.toUpperCase)
      // This is hack
      exposedUrlPartPatched = if exposedUrlPart == "uk" then "gb" else exposedUrlPart
      currency             <- c.downField("currency").as[String].map(_.trim)
      timezone             <- c.downField("timezone").as[String].map(_.trim)
    yield Locale(fullLocale, iso31662, country, exposedUrlPart = exposedUrlPartPatched, currency, timezone)

  private def readLocalesState(): String =
    Using(Source.fromResource("static/locales_state.json"))(_.mkString)
      .getOrElse(throw new RuntimeException("Failed to read locales_state.json"))

  def localesState: Task[List[Locale]] = for
    raw     <- ZIO.attemptBlocking(readLocalesState())
    locales <- ZIO.fromEither(parse(raw)).flatMap(json => ZIO.fromEither(json.as[List[Locale]]))
  yield locales

  val supportedCountryCodes: Set[String] = Model.supportedCountries.map(_.value)
  val supportedPackages: Set[String]     = Model.supportedPackages.map(_.value)
