package leaderboard.model

/** Dependency-free abstraction shared by every domain wrapper that validates and normalizes a single
  * canonical `String` representation, regardless of domain. Has no Circe, search, or database
  * dependency, and no normalization behavior of its own - `decodeCanonical`/`encodeCanonical` are
  * exactly the wrapper's own accepted-grammar validation and its exact stored value.
  *
  * Lives in `leaderboard-core` (not any one domain's model module) so a future domain's own model
  * module can depend on it directly, without depending on an unrelated domain's model internals.
  */
trait CanonicalStringValue[A] {
  def decodeCanonical(value: String): Either[String, A]
  def encodeCanonical(value: A): String
}
