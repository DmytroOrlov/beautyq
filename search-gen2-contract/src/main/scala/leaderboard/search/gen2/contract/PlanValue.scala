package leaderboard.search.gen2.contract

/** Stable contract identities selected by a domain: never normalized, lowercased, or derived from a
  * field path. A domain author picks these the same deliberate way it picks a [[FieldId]].
  */
final case class FacetId(value: String)
final case class FacetBucketId(value: String)
final case class FacetSelectionId(value: String)

final case class GroupId(value: String)
final case class GroupMetricId(value: String)

/** Explicit domain version mixed into the derived plan-contract fingerprint. The version is a
  * deliberate compatibility choice; the fingerprint mechanics derive the remaining value from the
  * executable document declaration. */
final case class PlanContractVersion(value: String)

/** A typed compatibility component contributed by a backend/compiler boundary. Components are sorted
  * by ID by the generic fingerprint owner, so their declaration order is not an accidental identity. */
final case class PlanContractContributionId(value: String)
final case class PlanContractContributionVersion(value: String)
/** One version per typed contribution ID. The fingerprint owner canonicalizes the map by ID. */
type PlanContractContributions = Map[PlanContractContributionId, PlanContractContributionVersion]

sealed trait PlanValueError

object PlanValueError {
  final case class InvalidPageSize(value: Int) extends PlanValueError
  final case class InvalidFacetSize(value: Int) extends PlanValueError
  final case class InvalidGroupSize(value: Int) extends PlanValueError
}

/** A positive page size. The generic contract defines no maximum; a public/domain ceiling is Brick 4D
  * policy, not a kernel constant.
  */
final case class PageSize private (value: Int)

object PageSize {
  def from(value: Int): Either[PlanValueError, PageSize] =
    if (value > 0) Right(new PageSize(value)) else Left(PlanValueError.InvalidPageSize(value))
}

final case class FacetSize private (value: Int)

object FacetSize {
  def from(value: Int): Either[PlanValueError, FacetSize] =
    if (value > 0) Right(new FacetSize(value)) else Left(PlanValueError.InvalidFacetSize(value))

  /** For static domain-policy declarations only, where an invalid literal is a broken source invariant
    * to fail loudly on, never a runtime input case. Domain files must call this rather than hand-roll
    * their own `from(...).getOrElse(throw ...)`.
    */
  def unsafeFrom(value: Int): FacetSize =
    from(value).getOrElse(throw new IllegalStateException(s"invalid source FacetSize literal '$value'"))
}

final case class GroupSize private (value: Int)

object GroupSize {
  def from(value: Int): Either[PlanValueError, GroupSize] =
    if (value > 0) Right(new GroupSize(value)) else Left(PlanValueError.InvalidGroupSize(value))
}

/** Opaque carriage for one backend cursor token. This value is deliberately untrusted: transport code
  * may carry it through request validation, while `search-gen2-core` validates its envelope and plan
  * identity before any backend consumes it. It performs no parsing, encoding or identity validation. */
opaque type SearchCursor = String

object SearchCursor {
  extension (cursor: SearchCursor) def opaqueValue: String = cursor

  /** Wrap a transport token without claiming that it is valid for any plan. */
  def fromTransport(value: String): SearchCursor = value

  /** Internal envelope construction; issued values still require plan validation by the envelope. */
  private[gen2] def fromOpaque(value: String): SearchCursor = value
}

final case class PageRequest(
  cursor: Option[SearchCursor],
  size: PageSize,
) {
  def withoutCursor: PageRequest = copy(cursor = None)
}
