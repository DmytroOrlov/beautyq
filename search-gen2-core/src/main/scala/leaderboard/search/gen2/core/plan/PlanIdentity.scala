package leaderboard.search.gen2.core.plan

import leaderboard.search.gen2.contract.*
import leaderboard.search.gen2.core.materialization.CanonicalFingerprint

/** Separately supplied fingerprint of the declared field/document contract. Brick 4C does not
  * compute this value; field paths, types and mappings are represented by the caller-supplied
  * contract fingerprint rather than duplicated in PlanIdentity.
  */
final case class ContractFingerprint(value: String)

/** A trusted value wrapper. Brick 4D/4F supplies the already normalized residual query text; this
  * type deliberately performs no trimming, case folding, tokenization or other normalization.
  */
final case class NormalizedQueryText(value: String)

sealed trait CanonicalBound

object CanonicalBound {
  case object Unbounded extends CanonicalBound
  final case class Inclusive(value: String) extends CanonicalBound
  final case class Exclusive(value: String) extends CanonicalBound
}

final case class CanonicalRangeBounds(
  lower: CanonicalBound,
  upper: CanonicalBound,
)

final case class CanonicalGeoPoint(value: String)
final case class CanonicalDistance(meters: String)

sealed trait CanonicalConstraint

object CanonicalConstraint {
  final case class Terms(fieldId: FieldId, values: Vector[String]) extends CanonicalConstraint
  final case class NumberRange(fieldId: FieldId, bounds: CanonicalRangeBounds) extends CanonicalConstraint
  final case class IntervalOverlap(fromFieldId: FieldId, toFieldId: FieldId, bounds: CanonicalRangeBounds) extends CanonicalConstraint
  final case class GeoDistanceFilter(fieldId: FieldId, origin: CanonicalGeoPoint, radius: CanonicalDistance) extends CanonicalConstraint
}

sealed trait CanonicalSignal

object CanonicalSignal {
  final case class GeoProximity(fieldId: FieldId, origin: CanonicalGeoPoint) extends CanonicalSignal
}

sealed trait CanonicalSort

object CanonicalSort {
  final case class FieldValue(fieldId: FieldId, direction: SortDirection) extends CanonicalSort
  final case class GeoDistance(fieldId: FieldId, origin: CanonicalGeoPoint, direction: SortDirection) extends CanonicalSort
}

sealed trait CanonicalFacetBucket {
  def id: FacetBucketId
}

object CanonicalFacetBucket {
  final case class HalfOpen(id: FacetBucketId, min: String, max: String) extends CanonicalFacetBucket
  final case class UpperUnbounded(id: FacetBucketId, min: String) extends CanonicalFacetBucket
}

sealed trait CanonicalFacetRequest {
  def id: FacetId
}

object CanonicalFacetRequest {
  final case class Terms(
    id: FacetId,
    fieldId: FieldId,
    size: Int,
    order: TermsFacetOrder,
    countingPolicy: FacetCountingPolicy,
  ) extends CanonicalFacetRequest

  final case class NumberRange(
    id: FacetId,
    fieldId: FieldId,
    buckets: Vector[CanonicalFacetBucket],
    countingPolicy: FacetCountingPolicy,
  ) extends CanonicalFacetRequest

  final case class IntervalOverlap(
    id: FacetId,
    fromFieldId: FieldId,
    toFieldId: FieldId,
    buckets: Vector[CanonicalFacetBucket],
    countingPolicy: FacetCountingPolicy,
  ) extends CanonicalFacetRequest
}

sealed trait CanonicalRepresentativeRequest

object CanonicalRepresentativeRequest {
  case object IdentityOnly extends CanonicalRepresentativeRequest
  final case class Fields(fieldIds: Vector[FieldId]) extends CanonicalRepresentativeRequest
}

sealed trait CanonicalGroupMetric {
  def id: GroupMetricId
}

object CanonicalGroupMetric {
  final case class BestScore(id: GroupMetricId) extends CanonicalGroupMetric
  final case class MinGeoDistance(id: GroupMetricId, fieldId: FieldId, origin: CanonicalGeoPoint) extends CanonicalGroupMetric
}

sealed trait CanonicalGroupOrder

object CanonicalGroupOrder {
  final case class Metric(metricId: GroupMetricId, direction: SortDirection) extends CanonicalGroupOrder
  final case class MatchingDocumentCount(direction: SortDirection) extends CanonicalGroupOrder
  final case class Key(direction: SortDirection) extends CanonicalGroupOrder
}

final case class CanonicalGroupRequest(
  id: GroupId,
  keyFieldId: FieldId,
  size: Int,
  representative: CanonicalRepresentativeRequest,
  metrics: Vector[CanonicalGroupMetric],
  order: Vector[CanonicalGroupOrder],
  precision: GroupPrecisionPolicy,
)

final case class PlanIdentity(
  contractFingerprint: ContractFingerprint,
  normalizedQuery: Option[NormalizedQueryText],
  hardConstraints: Vector[CanonicalConstraint],
  softSignals: Vector[CanonicalSignal],
  sort: Vector[CanonicalSort],
  facets: Vector[CanonicalFacetRequest],
  groups: Vector[CanonicalGroupRequest],
  pageSize: Int,
)

trait CanonicalPlanView[Document] {
  def identityOf(plan: SearchPlan[Document]): PlanIdentity
}

object CanonicalPlanView {
  def apply[Document](contractFingerprint: ContractFingerprint): CanonicalPlanView[Document] =
    new CanonicalPlanView[Document] {
      def identityOf(plan: SearchPlan[Document]): PlanIdentity = {
        val cursorFree = plan.withoutCursor
        PlanIdentity(
          contractFingerprint = contractFingerprint,
          normalizedQuery = cursorFree.residualText.map(NormalizedQueryText.apply),
          hardConstraints = cursorFree.hardConstraints.map(CanonicalPlanView.constraint),
          softSignals = cursorFree.softSignals.map(CanonicalPlanView.signal),
          sort = cursorFree.sort.map(CanonicalPlanView.sort),
          facets = cursorFree.facets.map(CanonicalPlanView.facet),
          groups = cursorFree.groups.map(CanonicalPlanView.group),
          pageSize = cursorFree.page.size.value,
        )
      }
    }

  private def bound[A](field: SearchField[?, A], value: Bound[A]): CanonicalBound =
    value match {
      case Bound.Unbounded        => CanonicalBound.Unbounded
      case Bound.Inclusive(item) => CanonicalBound.Inclusive(field.codec.encodeCanonical(item))
      case Bound.Exclusive(item) => CanonicalBound.Exclusive(field.codec.encodeCanonical(item))
    }

  private def bounds[A](field: SearchField[?, A], value: RangeBounds[A]): CanonicalRangeBounds =
    CanonicalRangeBounds(bound(field, value.lower), bound(field, value.upper))

  private def geoPoint(value: GeoPoint): CanonicalGeoPoint =
    CanonicalGeoPoint(SearchValueCodec.geoPoint.encodeCanonical(value))

  private def distance(value: Distance): CanonicalDistance =
    CanonicalDistance(SearchValueCodec.bigDecimal.encodeCanonical(value.meters))

  private def constraint[Document](value: PlannedConstraint[Document]): CanonicalConstraint =
    value match {
      case PlannedConstraint.Terms(field, values) =>
        CanonicalConstraint.Terms(field.id, values.iterator.map(field.codec.encodeCanonical).toVector.distinct.sorted)
      case PlannedConstraint.NumberRange(field, range) =>
        CanonicalConstraint.NumberRange(field.id, bounds(field, range))
      case PlannedConstraint.IntervalOverlap(from, to, range) =>
        CanonicalConstraint.IntervalOverlap(from.id, to.id, bounds(from, range))
      case PlannedConstraint.GeoDistanceFilter(field, origin, radius) =>
        CanonicalConstraint.GeoDistanceFilter(field.id, geoPoint(origin), distance(radius))
    }

  private def signal[Document](value: PlannedSignal[Document]): CanonicalSignal =
    value match {
      case PlannedSignal.GeoProximitySignal(field, origin) => CanonicalSignal.GeoProximity(field.id, geoPoint(origin))
    }

  private def sort[Document](value: PlannedSort[Document]): CanonicalSort =
    value match {
      case PlannedSort.FieldValue(field, direction) => CanonicalSort.FieldValue(field.id, direction)
      case PlannedSort.GeoDistance(field, origin, direction) => CanonicalSort.GeoDistance(field.id, geoPoint(origin), direction)
    }

  private def bucket[A](field: SearchField[?, A], value: FacetBucket[A]): CanonicalFacetBucket =
    value match {
      case FacetBucket.HalfOpen(id, min, max) =>
        CanonicalFacetBucket.HalfOpen(id, field.codec.encodeCanonical(min), field.codec.encodeCanonical(max))
      case FacetBucket.UpperUnbounded(id, min) =>
        CanonicalFacetBucket.UpperUnbounded(id, field.codec.encodeCanonical(min))
    }

  private def facet[Document](value: FacetRequest[Document]): CanonicalFacetRequest =
    value match {
      case terms: FacetRequest.Terms[Document, ?] =>
        CanonicalFacetRequest.Terms(terms.id, terms.field.id, terms.size.value, terms.order, terms.countingPolicy)
      case numberRange: FacetRequest.NumberRange[Document, ?] =>
        CanonicalFacetRequest.NumberRange(numberRange.id, numberRange.field.id, numberRange.buckets.map(bucket(numberRange.field, _)), numberRange.countingPolicy)
      case interval: FacetRequest.IntervalOverlap[Document, ?] =>
        CanonicalFacetRequest.IntervalOverlap(interval.id, interval.from.id, interval.to.id, interval.buckets.map(bucket(interval.from, _)), interval.countingPolicy)
    }

  private def representative[Document](value: RepresentativeRequest[Document]): CanonicalRepresentativeRequest =
    value match {
      case RepresentativeRequest.IdentityOnly() => CanonicalRepresentativeRequest.IdentityOnly
      case RepresentativeRequest.Fields(fields)  => CanonicalRepresentativeRequest.Fields(fields.map(_.id))
    }

  private def metric[Document](value: GroupMetricRequest[Document]): CanonicalGroupMetric =
    value match {
      case GroupMetricRequest.BestScore(id) => CanonicalGroupMetric.BestScore(id)
      case GroupMetricRequest.MinGeoDistance(id, field, origin) => CanonicalGroupMetric.MinGeoDistance(id, field.id, geoPoint(origin))
    }

  private def order(value: GroupOrder): CanonicalGroupOrder =
    value match {
      case GroupOrder.Metric(metricId, direction) => CanonicalGroupOrder.Metric(metricId, direction)
      case GroupOrder.MatchingDocumentCount(direction) => CanonicalGroupOrder.MatchingDocumentCount(direction)
      case GroupOrder.Key(direction) => CanonicalGroupOrder.Key(direction)
    }

  private def group[Document](value: GroupRequest[Document, ?]): CanonicalGroupRequest =
    CanonicalGroupRequest(
      id = value.id,
      keyFieldId = value.keyField.id,
      size = value.size.value,
      representative = representative(value.representative),
      metrics = value.metrics.map(metric),
      order = value.order.map(order),
      precision = value.precision,
    )
}

object PlanIdentityCompiler {
  def compile[Document](
    plan: SearchPlan[Document],
    view: CanonicalPlanView[Document],
  ): Either[NonEmptyErrors[SearchPlanError], PlanIdentity] =
    SearchPlan.validate(plan.withoutCursor).map(valid => view.identityOf(valid.withoutCursor))
}

final case class PlanIdentityHash private (value: String)

object PlanIdentityHash {
  def compute(identity: PlanIdentity): PlanIdentityHash =
    PlanIdentityHash(CanonicalFingerprint.sha256HexTokens(PlanIdentityCanonicalEncoding.tokens(identity)))

  private[plan] def parse(value: String): Option[PlanIdentityHash] =
    if (value.matches("[0-9a-f]{64}")) Some(PlanIdentityHash(value)) else None
}
