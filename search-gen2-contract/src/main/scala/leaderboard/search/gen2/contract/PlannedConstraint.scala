package leaderboard.search.gen2.contract

/** Explicit numeric/temporal bound, never inferred from an operator string at this layer. Public
  * request operators (`gt`/`gte`/`lt`/`lte`/`between`) map to these cases during request decoding
  * (Brick 4D+); this contract only carries the already-resolved bound.
  */
sealed trait Bound[+A]

object Bound {
  case object Unbounded extends Bound[Nothing]
  final case class Inclusive[A](value: A) extends Bound[A]
  final case class Exclusive[A](value: A) extends Bound[A]
}

final case class RangeBounds[A](lower: Bound[A], upper: Bound[A])

/** A radius in meters. A plain case class - not a value class - to match every other small domain
  * value in this contract (`FieldId`, `FieldPath`, `GeoPoint`); ordering/positivity is a request-
  * decoding concern (Brick 4D+), not this contract type.
  */
final case class Distance(meters: BigDecimal)

/** A hard, backend-independent constraint over one or two typed fields of one document. Plan-time only:
  * carries no backend JSON, analyzer, or query-DSL concern. Construction is unchecked (plain case
  * classes, matching `SearchField`'s own separation of construction from validation); call
  * [[PlannedConstraint.validate]] to check a constraint against its field(s)' declared capabilities.
  */
sealed trait PlannedConstraint[Document]

object PlannedConstraint {
  final case class Terms[Document, A](
    field: SearchField[Document, A],
    values: Set[A],
  ) extends PlannedConstraint[Document]

  final case class NumberRange[Document, A](
    field: SearchField[Document, A],
    bounds: RangeBounds[A],
  ) extends PlannedConstraint[Document]

  /** An offer/availability interval `[from, to]` matches a requested interval by overlap, never by a
    * range over `from` alone; overlap is determined by the request interval against the document's `[from, to]` range, never by matching `from` alone. `from` and `to`
    * share one value type `A` by construction; [[validate]] additionally requires them to share one
    * logical codec (`codec.typeId`), since two `SearchField[Document, A]` handles for the same Scala
    * type `A` are not guaranteed to share the same [[SearchValueCodec]] instance.
    */
  final case class IntervalOverlap[Document, A](
    from: SearchField[Document, A],
    to: SearchField[Document, A],
    bounds: RangeBounds[A],
  ) extends PlannedConstraint[Document]

  final case class GeoDistanceFilter[Document](
    field: SearchField[Document, GeoPoint],
    origin: GeoPoint,
    radius: Distance,
  ) extends PlannedConstraint[Document]

  /** Checks `constraint`'s field(s) against their own declared [[FieldCapabilities]] and, for
    * [[IntervalOverlap]], against each other's logical codec identity. Returns `constraint` unchanged
    * on success, matching [[SearchDocumentDeclaration.validate]]'s validate-returns-the-value idiom.
    */
  def validate[Document](constraint: PlannedConstraint[Document]): Either[NonEmptyErrors[PlanConstraintError], PlannedConstraint[Document]] =
    NonEmptyErrors.fromVector(violations(constraint)) match {
      case Some(errors) => Left(errors)
      case None         => Right(constraint)
    }

  // Ordered accepted-operator requirements, reused both to decide acceptance and to populate
  // PlanConstraintError.UnsupportedFilterOperator.accepted deterministically - the error payload is
  // never rebuilt from a Set, so its rendering (see PlannedAlgebraTrace) never depends on Set
  // iteration order.
  private val TermsOperators =
    Vector(
      FilterOperator.Equal,
      FilterOperator.In,
    )

  private val RangeOperators =
    Vector(
      FilterOperator.Range,
    )

  private val GeoDistanceOperators =
    Vector(
      FilterOperator.GeoDistance,
    )

  // Deterministic order within one constraint: for IntervalOverlap, the `from` field's capability
  // check, then the `to` field's capability check, then the cross-field codec-identity check - never
  // reordered by which check happens to fail.
  private def violations[Document](constraint: PlannedConstraint[Document]): Vector[PlanConstraintError] =
    constraint match {
      case Terms(field, _) =>
        requireAnyFilterOperator(field, TermsOperators)

      case NumberRange(field, _) =>
        requireAnyFilterOperator(field, RangeOperators)

      case IntervalOverlap(from, to, _) =>
        requireAnyFilterOperator(from, RangeOperators) ++
          requireAnyFilterOperator(to, RangeOperators) ++
          requireMatchingTypeId(from, to)

      case GeoDistanceFilter(field, _, _) =>
        requireAnyFilterOperator(field, GeoDistanceOperators)
    }

  private def requireAnyFilterOperator[Document](
    field: SearchField[Document, ?],
    accepted: Vector[FilterOperator],
  ): Vector[PlanConstraintError] =
    if (field.capabilities.filterOperators.exists(accepted.contains)) Vector.empty
    else Vector(PlanConstraintError.UnsupportedFilterOperator(field.id, field.kind, accepted))

  private def requireMatchingTypeId[Document, A](
    from: SearchField[Document, A],
    to: SearchField[Document, A],
  ): Vector[PlanConstraintError] =
    if (from.codec.typeId == to.codec.typeId) Vector.empty
    else Vector(PlanConstraintError.MismatchedIntervalFields(from.id, to.id, from.codec.typeId, to.codec.typeId))
}

/** A soft relevance-scoring input over one typed field. Unlike [[PlannedConstraint]]/[[PlannedSort]],
  * no [[FieldCapabilities]] flag governs scoring signals, so no `validate` function exists here: the
  * field's own value type (`SearchField[Document, GeoPoint]`) is the only requirement, and that is
  * already enforced by the type system at construction, not by a runtime capability check.
  */
sealed trait PlannedSignal[Document]

object PlannedSignal {
  final case class GeoProximitySignal[Document](
    field: SearchField[Document, GeoPoint],
    origin: GeoPoint,
  ) extends PlannedSignal[Document]
}

enum SortDirection {
  case Asc
  case Desc
}

sealed trait PlannedSort[Document]

object PlannedSort {
  final case class FieldValue[Document, A](
    field: SearchField[Document, A],
    direction: SortDirection,
  ) extends PlannedSort[Document]

  final case class GeoDistance[Document](
    field: SearchField[Document, GeoPoint],
    origin: GeoPoint,
    direction: SortDirection,
  ) extends PlannedSort[Document]

  /** Checks `sort`'s field against its own declared [[FieldCapabilities]]. Returns `sort` unchanged on
    * success, matching [[PlannedConstraint.validate]]'s idiom.
    */
  def validate[Document](sort: PlannedSort[Document]): Either[NonEmptyErrors[PlanConstraintError], PlannedSort[Document]] =
    NonEmptyErrors.fromVector(violations(sort)) match {
      case Some(errors) => Left(errors)
      case None         => Right(sort)
    }

  private def violations[Document](sort: PlannedSort[Document]): Vector[PlanConstraintError] =
    sort match {
      case FieldValue(field, _)    => requireSortMode(field, SortMode.Value)
      case GeoDistance(field, _, _) => requireSortMode(field, SortMode.Distance)
    }

  private def requireSortMode[Document](
    field: SearchField[Document, ?],
    required: SortMode,
  ): Vector[PlanConstraintError] =
    if (field.capabilities.sortModes.contains(required)) Vector.empty
    else Vector(PlanConstraintError.UnsupportedSortMode(field.id, field.kind, required))
}

sealed trait PlanConstraintError

object PlanConstraintError {
  final case class UnsupportedFilterOperator(
    fieldId: FieldId,
    kind: SearchFieldKind,
    accepted: Vector[FilterOperator],
  ) extends PlanConstraintError

  final case class MismatchedIntervalFields(
    fromFieldId: FieldId,
    toFieldId: FieldId,
    fromTypeId: SearchValueTypeId,
    toTypeId: SearchValueTypeId,
  ) extends PlanConstraintError

  final case class UnsupportedSortMode(
    fieldId: FieldId,
    kind: SearchFieldKind,
    required: SortMode,
  ) extends PlanConstraintError
}
