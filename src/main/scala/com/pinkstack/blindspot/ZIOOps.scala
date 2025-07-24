package com.pinkstack.blindspot

import zio.ZIO
import zio.ZIO.logInfo

object ZIOOps:
  extension [R, E, A](zio: ZIO[R, E, A])
    def logExecution(section: String): ZIO[R, E, A] = for
      _      <- logInfo(s"Starting ${section}")
      result <- zio.timed.tap { case (duration, _) => logInfo(s"Finished ${section} in ${duration.toMillis}ms") }
    yield result._2
