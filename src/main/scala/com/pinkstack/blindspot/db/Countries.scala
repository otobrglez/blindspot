package com.pinkstack.blindspot.db

import com.pinkstack.blindspot.clients.JustWatch
import com.pinkstack.blindspot.static.JustWatchStatic.Locale
import doobie.*
import doobie.implicits.*
import doobie.postgres.circe.jsonb.implicits.*
import io.circe.Json
import io.circe.syntax.*
import zio.RIO

object Countries:
  final case class Country(countryCode: String, country: String, currency: String)

  def syncFromLocale(locale: Locale): RIO[DB, (String, String)] = DB.transact:
    sql"""INSERT INTO countries (country_code, country, currency, created_at, updated_at) VALUES (
         ${locale.exposedUrlPart.toUpperCase}, ${locale.country}, ${locale.currency}, now(), now()
       ) ON CONFLICT (country_code) DO UPDATE SET
        country_code = ${locale.exposedUrlPart.toUpperCase},
        country = ${locale.country},
        currency = ${locale.currency},
        updated_at = now()
       RETURNING
        CASE WHEN xmax = 0 THEN 'INSERTED' ELSE 'UPDATED' END as operation,
        country_code""".queryWithLabel[(String, String)]("upsert-countries").unique

  def all(): RIO[DB, List[Country]] = DB.transact:
    sql"SELECT country_code, country, currency FROM countries"
      .queryWithLabel[Country]("all-countries")
      .to[List]

object Packages:
  final case class Package(
    id: String,
    countryCode: String,
    clearName: String,
    technicalName: String,
    slug: String
  )

  def syncFromCountryPackage(
    country: Countries.Country,
    pkg: JustWatch.Package
  ): RIO[DB, (String, String)] = DB.transact:
    sql"""INSERT INTO packages (country_code, clear_name, technical_name, slug, created_at, updated_at) VALUES 
         (${country.countryCode}, ${pkg.cleanName}, ${pkg.technicalName}, ${pkg.slug}, now(), now())
         ON CONFLICT (country_code, slug) DO UPDATE SET
          updated_at = now()
         RETURNING
          CASE WHEN xmax = 0 THEN 'INSERTED' ELSE 'UPDATED' END as operation,
          slug""".queryWithLabel[(String, String)]("upsert-packages").unique

  def allCountryPackages(country: String): RIO[DB, List[Package]] = DB.transact:
    sql"""SELECT id, country_code, clear_name, technical_name, slug, created_at, updated_at
          FROM packages
          WHERE country_code = $country
          ORDER BY slug """.queryWithLabel[Package]("all-country-packages").to[List]

  def deleteCountryPackage(country: String, slug: String): RIO[DB, Int] = DB.transact:
    sql"""DELETE FROM packages WHERE country_code = ${country} AND slug = ${slug}""".update.run

object Items:
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
    // countryCode: String,
    // slug: String,
    // packageId: String,
    // monetizationType: String,
    // availableToTime: String,
    // availableFromTime: String
  )

  given setGet: Get[Set[String]] = Get[Json].map(_.asArray.fold(Set.empty[String])(_.flatMap(_.asString).toSet))
  given setPut: Put[Set[String]] = Put[Json].contramap(_.asJson)

  def syncItemsBatch(items: List[(String, JustWatch.Item)]): RIO[DB, List[(String, String)]] =
    val sql =
      """INSERT INTO
         items (
          id, title, original_release_year, short_description, kind,
          imdb_votes, imdb_score,
          tmdb_popularity, tmdb_score, tomato_meter, provider_tags, 
          created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
          ON CONFLICT (id) DO UPDATE SET
           title = EXCLUDED.title,
           original_release_year = EXCLUDED.original_release_year,
           short_description = EXCLUDED.short_description,
           kind = EXCLUDED.kind,
           imdb_votes = EXCLUDED.imdb_votes,
           imdb_score = EXCLUDED.imdb_score,
           tmdb_popularity = EXCLUDED.tmdb_popularity,
           tmdb_score = EXCLUDED.tmdb_score,
           tomato_meter = EXCLUDED.tomato_meter,
           provider_tags = COALESCE((
              SELECT jsonb_agg(DISTINCT elem)
              FROM (
                SELECT jsonb_array_elements_text('[]'::jsonb || items.provider_tags) AS elem
                UNION
                SELECT jsonb_array_elements_text('[]'::jsonb || EXCLUDED.provider_tags) AS elem
              ) AS union_of_elements
              WHERE elem IS NOT NULL
           ), '[]'::jsonb),
           updated_at = now()
          RETURNING
            CASE WHEN xmax = 0 THEN 'INSERTED' ELSE 'UPDATED' END as operation, id"""

    DB.transact(
      Update[JustWatch.Item](sql)
        .updateManyWithGeneratedKeys[(String, String)]("operation", "id")(items.map { (country, item) =>
          item.copy(providerTags = item.providerTags.map(t => s"$country:$t"))
        })
        .compile
        .toList
    )
