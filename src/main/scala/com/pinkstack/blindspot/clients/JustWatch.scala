package com.pinkstack.blindspot.clients

import com.pinkstack.blindspot.clients.JsoupOps.given
import com.pinkstack.blindspot.clients.JustWatch.Item
import io.circe.Decoder.decodeList
import io.circe.{Decoder, Json}
import zio.ZIO.{logError, logInfo}
import zio.http.*
import zio.stream.ZStream
import zio.{RIO, RLayer, Scope, ZIO, ZLayer}
import zio.durationInt

final class JustWatch private (val client: Client) extends GraphQLClient:
  import JustWatch.Package

  private given Decoder[List[Package]] = _.downFields("data", "packages").as(using decodeList[Package])

  def getPackages(country: String): RIO[Scope, List[Package]] = queryAs(
    """query GetPackages($country: Country!) {
      |    packages(
      |        country: $country
      |        platform: WEB
      |        includeAddons: true
      |    ) {
      |        clearName
      |        slug
      |        technicalName
      |    }
      |}""".stripMargin,
    "country" -> Json.fromString(country)
  )

  private given itemsDecoder: Decoder[List[Item]] =
    _.downFields("data", "popularTitles", "edges").as(using decodeList[Item])

  val perItems: Int           = 100
  val defaultLanguage: String = "en"
  def getPopularTitles(
    country: String,
    language: String = defaultLanguage,
    offset: Int = 0,
    first: Int = perItems,
    objectType: String = "MOVIE",            // "SHOW"
    popularTitlesSortBy: String = "TRENDING" // "POPULAR"
  ): RIO[Scope, List[Item]] = queryAs[List[Item]](
    """query GetPopularTitles(
      |    $country: Country!,
      |    $language: Language!,
      |    $popularTitlesFilter: TitleFilter,
      |    $popularTitlesSortBy: PopularTitlesSorting! = POPULAR,
      |    $first: Int! = 70,
      |    $offset: Int = 0,
      |    $sortRandomSeed: Int! = 0) {
      |    popularTitles(
      |        country: $country
      |        filter: $popularTitlesFilter
      |        first: $first
      |        sortBy: $popularTitlesSortBy
      |        sortRandomSeed: $sortRandomSeed
      |        offset: $offset
      |        # after: $after
      |    ) {
      |        edges { node { ...PopularTitleGraphql } }
      |        totalCount
      |    }
      |}
      |
      |fragment PopularTitleGraphql on MovieOrShow {
      |    id
      |    objectId
      |    objectType
      |    offers(country: $country, platform: WEB) {
      |        monetizationType
      |        availableToTime
      |        availableFromTime
      |        package {
      |            id
      |            clearName
      |            technicalName
      |            slug
      |        }
      |    }
      |    content(country: $country, language: $language) {
      |        title
      |        originalReleaseYear
      |        shortDescription
      |        scoring {
      |         imdbVotes
      |         imdbScore
      |         tmdbPopularity
      |         tmdbScore
      |         tomatoMeter
      |         jwRating
      |        }
      |    }
      |}""".stripMargin,
    "first"               -> Json.fromInt(first),
    "offset"              -> Json.fromInt(offset),
    "language"            -> Json.fromString(language),
    "country"             -> Json.fromString(country),
    "popularTitlesSortBy" -> Json.fromString(popularTitlesSortBy),
    "popularTitlesFilter" -> Json.obj("objectTypes" -> Json.fromString(objectType))
  ).catchSome { case e: Throwable =>
    logError(s"Error getting popular titles for $country: ${e.getMessage}").as(List.empty[Item])
  }

  def collectItems(
    country: String,
    maxItems: Int = 1000
  ): ZStream[Scope, Throwable, List[(String, Item)]] = for
    objectType <- ZStream.fromIterable(Seq("MOVIE", "SHOW"))
    popularity <- ZStream.fromIterable(Seq("TRENDING", "POPULAR"))
    offset     <- ZStream.iterate(0)(_ + perItems).takeWhile(_ <= maxItems)
    items      <-
      ZStream.fromZIO(
        getPopularTitles(country, offset = offset, objectType = objectType, popularTitlesSortBy = popularity).timed
          .tap((duration, items) =>
            logInfo(
              s"Collected for $country, offset: $offset, objectType: $objectType, " +
                s"popularity: $popularity, size: ${items.size} in ${duration.toMillis}ms"
            )
          )
          .map(_._2)
          .retry(zio.Schedule.exponential(2.second) && zio.Schedule.recurs(4))
      )
  yield items.map(country -> _)

object JustWatch:
  final case class Package(cleanName: String, technicalName: String, slug: String)

  object Package:
    given Decoder[Package] = c =>
      for
        cleanName     <- c.downField("clearName").as[String].map(_.trim)
        technicalName <- c.downField("technicalName").as[String].map(_.trim)
        slug          <- c.downField("slug").as[String].map(_.trim)
      // id            <- c.downField("id").as[String]
      yield Package(cleanName, technicalName, slug)

  final case class Item(
    id: String,
    title: String,
    originalReleaseYear: Int,
    shortDescription: String,
    kind: String,
    imdbVotes: Option[Int],
    imdbScore: Option[Double],
    tmdbPopularity: Option[Double],
    tmdbScore: Option[Double],
    tomatoMeter: Option[Int],
    providerTags: Set[String]
  )

  object Item:
    given Decoder[Item] = c =>
      for
        id                  <- c.downFields("node", "id").as[String]
        title               <- c.downFields("node", "content", "title").as[String]
        originalReleaseYear <- c.downFields("node", "content", "originalReleaseYear").as[Int]
        shortDescription    <- c.downFields("node", "content", "shortDescription").as[String]
        kind                <- c.downFields("node", "objectType").as[String]
        imdbVotes           <- c.downFields("node", "content", "scoring", "imdbVotes").as[Option[Int]]
        imdbScore           <- c.downFields("node", "content", "scoring", "imdbScore").as[Option[Double]]
        tmdbPopularity      <- c.downFields("node", "content", "scoring", "tmdbPopularity").as[Option[Double]]
        tmdbScore           <- c.downFields("node", "content", "scoring", "tmdbScore").as[Option[Double]]
        tomatoMeter         <- c.downFields("node", "content", "scoring", "tomatoMeter").as[Option[Int]]

        providerTags <-
          c.downFields("node", "offers")
            .as[List[Json]]
            .map(_.flatMap(_.hcursor.downFields("package", "slug").as[String].toOption.toList).toSet)
      yield Item(
        id,
        title,
        originalReleaseYear,
        shortDescription,
        kind,
        imdbVotes,
        imdbScore,
        tmdbPopularity,
        tmdbScore,
        tomatoMeter,
        providerTags
      )

  private val followRedirects =
    ZClientAspect.followRedirects(3)((resp, message) => logInfo(s"WOT $message").as(resp))

  def live: RLayer[Client, JustWatch] = ZLayer.fromZIO:
    for
      graphQLEndpoint <- ZIO.fromEither(URL.decode("https://apis.justwatch.com/graphql"))
      client          <- ZIO.serviceWith[Client](_.url(graphQLEndpoint) @@ followRedirects)
    yield JustWatch(client)
