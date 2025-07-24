package com.pinkstack.blindspot.apps

import com.pinkstack.blindspot.clients.JustWatch
import com.pinkstack.blindspot.db.{Countries, DB, Items, Packages}
import com.pinkstack.blindspot.static.JustWatchStatic
import com.pinkstack.blindspot.static.JustWatchStatic.Locale
import zio.*
import zio.ZIO.logInfo
import zio.http.Client
import zio.stream.ZStream
import com.pinkstack.blindspot.ZIOOps.{logExecution, given}

object RefreshJustWatch:

  private def syncCountries: RIO[DB, Unit] =
    ZStream
      .fromZIO(JustWatchStatic.localesState)
      .flatMap(ZStream.fromIterable)
      .filter(l => JustWatchStatic.supportedCountryCodes.contains(l.exposedUrlPart))
      .runForeach(Countries.syncFromLocale)

  private def syncPackages: RIO[Scope & DB & JustWatch, Unit] = for
    jw <- ZIO.service[JustWatch]
    _  <- ZStream
            .fromZIO(Countries.all())
            .flatMap(ZStream.fromIterable)
            .filter(c => JustWatchStatic.supportedCountryCodes.contains(c.countryCode))
            .mapZIOParUnordered(3) { country =>
              for
                apiPackages        <- jw.getPackages(country.countryCode)
                filteredApiPackages = apiPackages.filter(pkg => JustWatchStatic.supportedPackages.contains(pkg.slug))

                // Get existing packages from a database
                dbPackages <- Packages.allCountryPackages(country.countryCode)

                // Create sets for comparison
                apiSlugs = filteredApiPackages.map(_.slug).toSet
                dbSlugs  = dbPackages.map(_.slug).toSet

                // Determine operations needed
                toInsertUpdate = filteredApiPackages.filter(pkg => apiSlugs.contains(pkg.slug))
                toDelete       = dbSlugs -- apiSlugs

                _ <-
                  logInfo(
                    s"Country ${country.countryCode}: ${toInsertUpdate.size} to insert/update, " +
                      s"${toDelete.size} to delete"
                  )

                _ <- ZIO.foreachDiscard(toInsertUpdate)(Packages.syncFromCountryPackage(country, _))
                _ <- ZIO.foreachDiscard(toDelete)(Packages.deleteCountryPackage(country.countryCode, _))
              yield ()
            }
            .runDrain
  yield ()

  private def syncCountry(country: String, maxPageItems: Int) =
    ZStream
      .serviceWithStream[JustWatch](_.collectItems(country, maxPageItems))
      .flatMap(ZStream.fromIterable)
      .grouped(100)
      .runForeach(items => Items.syncItemsBatch(items.toList))

  // TODO: add country code to tag so that it is easier to grok
  private def syncCountryItems(maxPageItems: Int) =
    ZStream
      .fromZIO(Countries.all())
      .flatMap(ZStream.fromIterable)
      .flatMapPar(3)(country => ZStream.serviceWithStream[JustWatch](_.collectItems(country.countryCode, maxPageItems)))
      .flatMap(ZStream.fromIterable)
      .grouped(200)
      .runForeach(items => Items.syncItemsBatch(items.toList))

  private def program = for
    _ <- logInfo(s"Refreshing data.")
    _ <- DB.migrate.logExecution("Migration")
    _ <- syncCountries.logExecution("Sync countries")
    _ <- syncPackages.logExecution("Sync packages")

    // _ <- syncCountry("SI", maxPageItems = 200).logExecution("Sync country")

    _ <- syncCountryItems(maxPageItems = 1500).logExecution("Sync country items")
  yield ()

  def run = program
    .provide(
      Scope.default,
      Client.default,
      JustWatch.live,
      DB.transactorLayer
    )
    .tapError(th => zio.Console.printLine(s"🔥 Crashed: ${th.getMessage} @ ${th.printStackTrace()}"))
