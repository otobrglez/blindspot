package com.pinkstack.blindspot

import com.pinkstack.blindspot.Model.{CountryCode, PackageSlug}
import zio.http.Request

private val defaultPageSize = 20

final case class GridParams(
  itemIDs: List[String] = List.empty,
  countries: List[CountryCode] = List.empty,
  packages: List[PackageSlug] = List.empty,
  query: Option[String] = None,
  kind: List[Model.ItemKind] = List.empty,
  page: Int = 1,
  pageSize: Int = defaultPageSize
)

object GridParams:
  private def optionalStringToList(op: Option[String]): List[String] =
    op.fold(List.empty[String])(_.split("-").toList)

  def fromRequest(request: Request): Either[Throwable, GridParams] = for
    query    <- Right(request.queryOrElse[Option[String]]("query", None).filter(_.nonEmpty))
    kind      = optionalStringToList(request.queryOrElse[Option[String]]("kind", None)).map(Model.ItemKind.withName)
    itemIDs   = optionalStringToList(request.queryOrElse[Option[String]]("itemIDs", None))
    countries = optionalStringToList(request.queryOrElse[Option[String]]("countries", None))
    packages  = optionalStringToList(request.queryOrElse[Option[String]]("packages", None))
    page      = request.queryOrElse[Option[Int]]("page", None).getOrElse(1)
    pageSize  = request.queryOrElse[Option[Int]]("pageSize", None).getOrElse(defaultPageSize)
  yield GridParams(itemIDs, countries, packages, query, kind, page, pageSize)
