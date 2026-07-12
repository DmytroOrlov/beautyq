package leaderboard.search.gen2.contract

enum FacetCountingPolicy {
  case AllAppliedHardFilters
}

enum TermsFacetOrder {
  case CountDescThenKeyAsc
  case KeyAsc
}

/** Explicit numeric/interval facet bucket. The bounds every case derives are fixed policy - inclusive
  * lower, exclusive upper, optionally upper-unbounded - so the generic contract states adjacency once
  * instead of trusting each domain to assemble an arbitrary [[RangeBounds]] correctly. Bucket sequencing
  * (adjacency, ascending order) is domain/request policy: [[SearchValueCodec]] carries no ordering
  * relation, so this layer cannot and does not verify it.
  */
sealed trait FacetBucket[A] {
  def id: FacetBucketId
  def bounds: RangeBounds[A]
}

object FacetBucket {
  final case class HalfOpen[A](
    id: FacetBucketId,
    min: A,
    max: A,
  ) extends FacetBucket[A] {
    def bounds: RangeBounds[A] = RangeBounds(Bound.Inclusive(min), Bound.Exclusive(max))
  }

  final case class UpperUnbounded[A](
    id: FacetBucketId,
    min: A,
  ) extends FacetBucket[A] {
    def bounds: RangeBounds[A] = RangeBounds(Bound.Inclusive(min), Bound.Unbounded)
  }
}

/** A backend-independent facet request over one or two typed fields of one document. Plan-time only,
  * construction unchecked - matches [[PlannedConstraint]]'s own separation of construction from
  * [[FacetRequest.validate]].
  */
sealed trait FacetRequest[Document] {
  def id: FacetId
  def countingPolicy: FacetCountingPolicy
}

object FacetRequest {
  final case class Terms[Document, A](
    id: FacetId,
    field: SearchField[Document, A],
    size: FacetSize,
    order: TermsFacetOrder,
    countingPolicy: FacetCountingPolicy,
  ) extends FacetRequest[Document]

  final case class NumberRange[Document, A](
    id: FacetId,
    field: SearchField[Document, A],
    buckets: Vector[FacetBucket[A]],
    countingPolicy: FacetCountingPolicy,
  ) extends FacetRequest[Document]

  /** Mirrors [[PlannedConstraint.IntervalOverlap]]: a price-style `[from, to]` interval bucketed by
    * overlap, never a range facet over `from` alone. Reuses [[FacetMode.Range]] on both fields - facet
    * bucketing is a facet-request concern, not a new field capability.
    */
  final case class IntervalOverlap[Document, A](
    id: FacetId,
    from: SearchField[Document, A],
    to: SearchField[Document, A],
    buckets: Vector[FacetBucket[A]],
    countingPolicy: FacetCountingPolicy,
  ) extends FacetRequest[Document]

  /** Checks `request`'s field(s) against their own declared [[FieldCapabilities]] and its bucket vector
    * for non-emptiness, ID uniqueness and upper-unbounded placement. Returns `request` unchanged on
    * success, matching [[PlannedConstraint.validate]]'s idiom.
    */
  def validate[Document](request: FacetRequest[Document]): Either[NonEmptyErrors[FacetRequestError], FacetRequest[Document]] =
    NonEmptyErrors.fromVector(violations(request)) match {
      case Some(errors) => Left(errors)
      case None         => Right(request)
    }

  // Deterministic order within one request: capability check(s) first (from, then to, for
  // IntervalOverlap), then cross-field codec-identity check, then bucket-vector checks in bucket
  // declaration order - never reordered by which check happens to fail.
  private def violations[Document](request: FacetRequest[Document]): Vector[FacetRequestError] =
    request match {
      case terms: Terms[Document, ?] =>
        requireFacetMode(terms.id, terms.field, FacetMode.Terms)

      case numberRange: NumberRange[Document, ?] =>
        requireFacetMode(numberRange.id, numberRange.field, FacetMode.Range) ++
          bucketViolations(numberRange.id, numberRange.buckets)

      case intervalOverlap: IntervalOverlap[Document, ?] =>
        requireFacetMode(intervalOverlap.id, intervalOverlap.from, FacetMode.Range) ++
          requireFacetMode(intervalOverlap.id, intervalOverlap.to, FacetMode.Range) ++
          requireMatchingTypeId(intervalOverlap.id, intervalOverlap.from, intervalOverlap.to) ++
          bucketViolations(intervalOverlap.id, intervalOverlap.buckets)
    }

  private def requireFacetMode[Document, A](
    facetId: FacetId,
    field: SearchField[Document, A],
    required: FacetMode,
  ): Vector[FacetRequestError] =
    if (field.capabilities.facetModes.contains(required)) Vector.empty
    else Vector(FacetRequestError.UnsupportedFacetMode(facetId, field.id, field.kind, required))

  private def requireMatchingTypeId[Document, A](
    facetId: FacetId,
    from: SearchField[Document, A],
    to: SearchField[Document, A],
  ): Vector[FacetRequestError] =
    if (from.codec.typeId == to.codec.typeId) Vector.empty
    else Vector(FacetRequestError.MismatchedIntervalFacetFields(facetId, from.id, to.id, from.codec.typeId, to.codec.typeId))

  // Empty buckets is a single whole-vector check. Otherwise, one left-to-right scan reports each
  // duplicated bucket ID exactly once (at the position its first repeat is seen) and every
  // out-of-position UpperUnbounded bucket, interleaved in bucket declaration order.
  private def bucketViolations[A](facetId: FacetId, buckets: Vector[FacetBucket[A]]): Vector[FacetRequestError] =
    if (buckets.isEmpty) Vector(FacetRequestError.EmptyFacetBuckets(facetId))
    else {
      val lastIndex = buckets.length - 1

      val (_, _, errors) =
        buckets.zipWithIndex.foldLeft((Set.empty[FacetBucketId], Set.empty[FacetBucketId], Vector.empty[FacetRequestError])) {
          case ((seen, reported, errors), (bucket, index)) =>
            val duplicateError =
              if (seen.contains(bucket.id) && !reported.contains(bucket.id))
                Vector(FacetRequestError.DuplicateFacetBucketId(facetId, bucket.id))
              else
                Vector.empty

            val positionError =
              bucket match {
                case _: FacetBucket.UpperUnbounded[A] if index != lastIndex =>
                  Vector(FacetRequestError.UpperUnboundedBucketMustBeLast(facetId, bucket.id))
                case _ =>
                  Vector.empty
              }

            (
              seen + bucket.id,
              if (duplicateError.nonEmpty) reported + bucket.id else reported,
              errors ++ duplicateError ++ positionError,
            )
        }

      errors
    }
}

sealed trait FacetRequestError

object FacetRequestError {
  final case class UnsupportedFacetMode(
    facetId: FacetId,
    fieldId: FieldId,
    kind: SearchFieldKind,
    required: FacetMode,
  ) extends FacetRequestError

  final case class EmptyFacetBuckets(
    facetId: FacetId
  ) extends FacetRequestError

  final case class DuplicateFacetBucketId(
    facetId: FacetId,
    bucketId: FacetBucketId,
  ) extends FacetRequestError

  final case class UpperUnboundedBucketMustBeLast(
    facetId: FacetId,
    bucketId: FacetBucketId,
  ) extends FacetRequestError

  final case class MismatchedIntervalFacetFields(
    facetId: FacetId,
    fromFieldId: FieldId,
    toFieldId: FieldId,
    fromTypeId: SearchValueTypeId,
    toTypeId: SearchValueTypeId,
  ) extends FacetRequestError
}
