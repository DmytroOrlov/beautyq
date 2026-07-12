package leaderboard.model

final case class StableCodeValidationError(
  codeType: String,
  value: String,
) {
  def message: String =
    s"Invalid $codeType '$value': expected lowercase snake_case matching [a-z][a-z0-9]*(?:_[a-z0-9]+)*"
}

private[model] object StableCodeGrammar {
  private val pattern = "^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$".r

  def isValid(value: String): Boolean = pattern.matches(value)
}

/** Reusable implementation for one canonical lowercase-snake-case stable-code wrapper (e.g. a business
  * code such as `ServiceCode`/`CategoryCode`). A domain companion supplies only its public type name
  * plus total wrap/unwrap operations; grammar validation, canonical evidence, unsafe seed construction
  * and the `.value` extension are derived once here, for any module that depends on `leaderboard-core`
  * - not only the module that first needed this shape.
  *
  * Deliberately Circe-free: a JSON wire codec is a separate, per-module policy decision (some
  * consumers may want a different JSON representation, a different codec library entirely, or no wire
  * codec at all), so it is layered on top of this trait where Circe is actually a dependency, not
  * forced onto every consumer of this trait. See `beautyq-model`'s `StableCodeCirceSupport` for the
  * Circe adapter `ServiceCode`/`CategoryCode` actually use.
  *
  * The accepted grammar (lowercase snake_case) is fixed, not a type parameter: this trait serves any
  * domain that wants exactly `ServiceCode`/`CategoryCode`'s grammar. A domain needing a different
  * accepted grammar (e.g. digits-and-hyphens) still implements its own `CanonicalStringValue[A]`
  * instance directly - parameterizing the grammar here was considered and deliberately deferred rather
  * than folded into this move, since generalizing it is an independent design decision with its own
  * review, not a pure relocation.
  */
trait StableCodeCompanion[A] extends CanonicalStringValue[A] {
  protected def codeType: String
  protected def wrap(value: String): A
  protected def unwrap(value: A): String

  final def fromString(value: String): Either[StableCodeValidationError, A] =
    if (StableCodeGrammar.isValid(value)) Right(wrap(value))
    else Left(StableCodeValidationError(codeType, value))

  private[leaderboard] final def unsafeFromString(value: String): A =
    fromString(value) match {
      case Right(code) => code
      case Left(error) => throw new IllegalArgumentException(error.message)
    }

  extension (code: A) final def value: String = unwrap(code)

  final def decodeCanonical(value: String): Either[String, A] = fromString(value).left.map(_.message)
  final def encodeCanonical(value: A): String = unwrap(value)
}
