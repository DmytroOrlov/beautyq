package leaderboard.http.tapir

import leaderboard.model.CanonicalStringValue
import sttp.tapir.Schema

import scala.annotation.unused

object StableCodeTapirSupport {
  given canonicalStringSchema[A](using @unused value: CanonicalStringValue[A]): Schema[A] =
    Schema.string[A]
}
