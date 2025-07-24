package com.pinkstack.blindspot

import com.pinkstack.blindspot.Model.{CountryCode, ItemKind, PackageSlug}
import com.pinkstack.blindspot.db.DB
import io.circe.Json
import io.circe.syntax.*
import io.circe.{Encoder, Json}
import io.circe.generic.semiauto.*
import doobie.*
import doobie.implicits.*
import doobie.postgres.circe.jsonb.implicits.*
import zio.RIO

object Grid:
  final case class Row(
    id: String,
    title: String,
    kind: Model.ItemKind,
    releaseYear: Int,
    imdbVotes: Option[Int],
    imdbScore: Option[Double],
    tmdbPopularity: Option[Double],
    tmdbScore: Option[Double],
    tomatoMeter: Option[Int],
    providerTags: Set[String],
    rank: Double
  )

  object Row:
    given encoder: Encoder[Row] = deriveEncoder[Row]

  private object queries:
    given setGet: Get[Set[String]]         = Get[Json].map(_.asArray.fold(Set.empty[String])(_.flatMap(_.asString).toSet))
    // given setPut: Put[Set[String]]         = Put[Json].contramap(_.asJson)
    given itemKindGet: Get[Model.ItemKind] = Get[String].map(_.toLowerCase match
      case "movie" => Model.ItemKind.Movie; case "show" => Model.ItemKind.Show
    )
    given itemKindPut: Put[Model.ItemKind] = Put[String].contramap(_.toString.toUpperCase)

    def gridQuery(
      query: Option[String] = None,
      kind: List[ItemKind] = List.empty,
      countries: List[CountryCode] = List.empty,
      packages: List[PackageSlug] = List.empty,
      page: Int = 1,
      pageSize: Int = 100
    ): Fragment =
      val rankFiled    = query.fold(fr"1.0")(_ => fr"ts_rank(i.title_vec, query)")
      val searchVector = query.fold(fr"")(q => fr", to_tsquery('english', $q) as query")

      val init =
        sql"""
             SELECT
              i.id,
              i.title, i.kind, i.original_release_year, i.imdb_votes, i.imdb_score, i.tmdb_popularity,
              i.tmdb_score, i.tomato_meter, i.provider_tags,
              $rankFiled as rank
             FROM
              items i
              $searchVector
             """

      val where = (query, kind, countries, packages) match
        case (Some(_), Nil, _, _)   => fr" WHERE title_vec @@ query"
        case (Some(_), kinds, _, _) =>
          fr" WHERE title_vec @@ query " ++
            fr"AND i.kind IN (${kinds.map(_.entryName.toUpperCase).mkString(",")})"
        case _                      =>
          fr" WHERE original_release_year=date_part('year', now()) AND tomato_meter IS NOT NULL "

      val ordering = (query, kind, countries, packages) match
        case (Some(_), _, _, _) => fr" ORDER BY rank DESC"
        case (None, _, _, _)    => fr" ORDER BY tomato_meter DESC, original_release_year DESC"

      val pagination = fr" LIMIT $pageSize OFFSET ${(page - 1) * pageSize}"
      init ++ where ++ ordering ++ pagination

    def showQuery(
      query: Option[String] = None,
      kind: List[ItemKind] = List.empty,
      countries: List[CountryCode] = List.empty,
      packages: List[PackageSlug] = List.empty,
      page: Int = 1,
      pageSize: Int = 100
    ) = gridQuery(
      query,
      kind,
      countries,
      packages,
      page,
      pageSize
    ).queryWithLabel[Row]("show-query")

  def showFor(
    query: Option[String] = None,
    kind: List[ItemKind] = List.empty,
    countries: List[CountryCode] = List.empty,
    packages: List[PackageSlug] = List.empty,
    page: Int = 1,
    pageSize: Int = 20
  ): RIO[DB, List[Row]] =
    DB.transact(queries.showQuery(query, kind, countries, packages, page, pageSize).to[List])
