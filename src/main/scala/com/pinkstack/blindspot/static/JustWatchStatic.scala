package com.pinkstack.blindspot.static

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
      fullLocale     <- c.downField("full_locale").as[String].map(_.trim)
      iso31662       <- c.downField("iso_3166_2").as[String].map(_.trim)
      country        <- c.downField("country").as[String].map(_.trim)
      exposedUrlPart <- c.downField("exposed_url_part").as[String].map(_.trim).map(_.toUpperCase)
      currency       <- c.downField("currency").as[String].map(_.trim)
      timezone       <- c.downField("timezone").as[String].map(_.trim)
    yield Locale(fullLocale, iso31662, country, exposedUrlPart, currency, timezone)

  private def readLocalesState(): String =
    Using(Source.fromResource("static/locales_state.json"))(_.mkString)
      .getOrElse(throw new RuntimeException("Failed to read locales_state.json"))

  def localesState: Task[List[Locale]] = for
    raw     <- ZIO.attemptBlocking(readLocalesState())
    locales <- ZIO.fromEither(parse(raw)).flatMap(json => ZIO.fromEither(json.as[List[Locale]]))
  yield locales

  // Hardcoded. This is POC after-all.
  val supportedCountryCodes: Set[String] = Set(
    "AU",
    "CA",
    "DE",
    "DE",
    "ES",
    "FR",
    "GB",
    "HK",
    "IN",
    "IT",
    "JP",
    "NZ",
    "SI",
    "TR",
    "US"
  )

  // Hardcoded. This is POC. (using slug)
  val supportedPackages: Set[String] = Set(
    "amazon-prime-video",
    "apple-tv",
    "apple-tv-plus",
    "disney-plus",
    "google-play-movies",
    "hbo-max",
    "hulu",
    "mubi",
    "netflix",
    "paramount-pictures",
    "paramount-plus"
  )
