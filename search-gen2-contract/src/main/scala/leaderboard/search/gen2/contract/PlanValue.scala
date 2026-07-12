package leaderboard.search.gen2.contract

/** Stable contract identities selected by a domain: never normalized, lowercased, or derived from a
  * field path. A domain author picks these the same deliberate way it picks a [[FieldId]].
  */
final case class FacetId(value: String)
final case class FacetBucketId(value: String)
final case class FacetSelectionId(value: String)

final case class GroupId(value: String)
final case class GroupMetricId(value: String)

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
}

final case class GroupSize private (value: Int)

object GroupSize {
  def from(value: Int): Either[PlanValueError, GroupSize] =
    if (value > 0) Right(new GroupSize(value)) else Left(PlanValueError.InvalidGroupSize(value))
}

/** Opaque carriage for one backend cursor token: no parsing, no encoding, no plan-hash, no identity
  * validation. `search-gen2-core`'s Brick 4C owns the actual cursor envelope/codec and is the only
  * intended caller of [[SearchCursor.fromOpaque]] (hence `private[gen2]`, not `private[contract]`);
  * every other caller may only carry a cursor through unopened or read it back via
  * [[SearchCursor.opaqueValue]] for transport.
  */
opaque type SearchCursor = String

object SearchCursor {
  extension (cursor: SearchCursor) def opaqueValue: String = cursor

  private[gen2] def fromOpaque(value: String): SearchCursor = value
}

final case class PageRequest(
  cursor: Option[SearchCursor],
  size: PageSize,
) {
  def withoutCursor: PageRequest = copy(cursor = None)
}
