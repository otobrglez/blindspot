package com.pinkstack.blindspot.clients

import org.jsoup.*
import org.jsoup.nodes.{Document, Element}
import zio.http.{Charsets, Response, URL}
import zio.{Task, ZIO}

import scala.jdk.CollectionConverters.*

object JsoupOps:
  extension (r: Response)
    def bodyAsDocument(baseUri: URL): Task[Document] =
      r.body.asString(Charsets.Utf8).flatMap { body =>
        ZIO.attempt(
          Jsoup.parse(body, baseUri.toString)
        )
      }

  extension (doc: Document)
    def selectZIO(query: String): Task[Vector[Element]] =
      ZIO.attempt(doc.select(query).iterator().asScala.toVector)

    def selectAndMap[Out](query: String)(f: Element => Out): Task[Vector[Out]] =
      selectZIO(query).map(_.map(f))

    def selectAndMapZIO[Out](query: String)(f: Element => Task[Out]): Task[Vector[Out]] =
      selectZIO(query).flatMap(elements => ZIO.foreach(elements)(f))

    def findZIO(query: String): Task[Option[Element]] =
      selectZIO(query).map(_.headOption)

    def findOrDieWith[E <: Exception](query: String, err: E): Task[Element] =
      findZIO(query).flatMap: first =>
        ZIO.fromOption(first).orElseFail(err)

    def findOrDie(query: String): Task[Element] =
      findOrDieWith(query, new RuntimeException(s"Could not find any element with CSS query: \"$query\""))
