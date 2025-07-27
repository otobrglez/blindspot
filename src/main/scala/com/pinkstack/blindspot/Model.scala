package com.pinkstack.blindspot

import enumeratum.*
import io.circe.*
import io.circe.generic.semiauto.*

object Model:
  sealed trait ItemKind extends EnumEntry

  object ItemKind extends Enum[ItemKind] with CirceEnum[ItemKind]:
    case object Movie extends ItemKind
    case object Show  extends ItemKind
    val values: IndexedSeq[ItemKind] = findValues

  type CountryCode = String
  type PackageSlug = String
  type Priority    = Double

  final case class Country(name: String, value: CountryCode, priority: Priority)
  object Country:
    given encoder: Encoder[Country] = deriveEncoder[Country]

  final case class Package(name: String, value: PackageSlug, priority: Priority)
  object Package:
    given encoder: Encoder[Package] = deriveEncoder[Package]

  final case class Platform(name: String, value: Set[PackageSlug], priority: Priority)
  object Platform:
    given encoder: Encoder[Platform] = deriveEncoder[Platform]

  val supportedCountries: Set[Country] = Set(
    Country("United States", "US", 0.9),
    Country("United Kingdom", "GB", 0.8), // GB or UK?
    Country("Australia", "AU", 0.0),
    Country("Canada", "CA", 0.0),
    Country("Germany", "DE", 0.7),
    Country("Spain", "ES", 0.6),
    Country("France", "FR", 0.0),
    Country("Hong Kong", "HK", 0.0),
    Country("India", "IN", 0.5),
    Country("Italy", "IT", 0.0),
    Country("Japan", "JP", 0.4),
    Country("New Zealand", "NZ", 0.0),
    Country("Slovenia", "SI", 0.3),
    Country("Turkey", "TR", 0.0)
  )

  val supportedPackages: Set[Package] = Set(
    Package("Amazon Prime Video", "amazon-prime-video", 0.5),
    Package("Apple TV", "apple-tv", 0.4),
    Package("Apple TV+", "apple-tv-plus", 0.3),
    Package("Disney+", "disney-plus", 0.9),
    Package("HULU", "hulu", 0.8),
    Package("Google Play Movies", "google-play-movies", 0.0),
    Package("HBO Max", "hbo-max", 0.0),
    // Package("MUBI", "mubi", 0.0),
    Package("Netflix", "netflix", 0.8),
    Package("Paramount Pictures", "paramount-pictures", 0.5),
    Package("Paramount+", "paramount-plus", 0.0)
  )

  val supportedPlatforms: Set[Platform] = Set(
    Platform("Amazon Prime Video", Set("amazon-prime-video"), 0.5),
    Platform("Apple TV", Set("apple-tv", "apple-tv-plus"), 0.4),
    Platform("Disney+", Set("disney-plus"), 0.9),
    Platform("HULU", Set("hulu"), 0.8),
    Platform("Google Play Movies", Set("google-play-movies"), 0.0),
    Platform("HBO Max", Set("hbo-max"), 0.0),
    // Platform("MUBI", Set("mubi"), 0.0),
    Platform("Netflix", Set("netflix"), 0.8),
    Platform("Paramount Pictures", Set("paramount-pictures", "paramount-plus"), 0.5)
  )
