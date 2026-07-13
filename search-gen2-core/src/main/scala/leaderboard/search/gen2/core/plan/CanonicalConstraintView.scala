package leaderboard.search.gen2.core.plan

import leaderboard.search.gen2.contract.*

/** The typed semantic slot a constraint occupies for Brick 4F precedence and deduplication: two
  * constraints compete only when they resolve to the same slot. Constraint kind is part of the slot -
  * a `Terms` and a `NumberRange` constraint over the same [[FieldId]] are different slots - and slot
  * identity uses [[FieldId]] only, never `SearchField` equality, field path, capabilities, semantics
  * or extractor identity.
  */
sealed trait ConstraintSlot

object ConstraintSlot {
  final case class Terms(fieldId: FieldId) extends ConstraintSlot
  final case class NumberRange(fieldId: FieldId) extends ConstraintSlot
  final case class IntervalOverlap(fromFieldId: FieldId, toFieldId: FieldId) extends ConstraintSlot
  final case class GeoDistanceFilter(fieldId: FieldId) extends ConstraintSlot
}

/** The single, reusable conversion from a typed [[PlannedConstraint]] to its value-only
  * [[CanonicalConstraint]] form, and from that canonical form to the [[ConstraintSlot]] it occupies.
  * [[CanonicalPlanView]] delegates its own constraint (and geo/distance) conversion here rather than
  * keeping a second implementation, so PlanIdentity's canonical values and Brick 4F's precedence/
  * equivalence decisions can never drift apart into two representations of the same constraint.
  */
object CanonicalConstraintView {

  def apply[Document](constraint: PlannedConstraint[Document]): CanonicalConstraint =
    constraint match {
      case PlannedConstraint.Terms(field, values) =>
        CanonicalConstraint.Terms(field.id, values.iterator.map(field.codec.encodeCanonical).toVector.distinct.sorted)
      case PlannedConstraint.NumberRange(field, range) =>
        CanonicalConstraint.NumberRange(field.id, bounds(field, range))
      case PlannedConstraint.IntervalOverlap(from, to, range) =>
        CanonicalConstraint.IntervalOverlap(from.id, to.id, bounds(from, range))
      case PlannedConstraint.GeoDistanceFilter(field, origin, radius) =>
        CanonicalConstraint.GeoDistanceFilter(field.id, geoPoint(origin), distance(radius))
    }

  def slot(constraint: CanonicalConstraint): ConstraintSlot =
    constraint match {
      case CanonicalConstraint.Terms(fieldId, _) => ConstraintSlot.Terms(fieldId)
      case CanonicalConstraint.NumberRange(fieldId, _) => ConstraintSlot.NumberRange(fieldId)
      case CanonicalConstraint.IntervalOverlap(fromFieldId, toFieldId, _) => ConstraintSlot.IntervalOverlap(fromFieldId, toFieldId)
      case CanonicalConstraint.GeoDistanceFilter(fieldId, _, _) => ConstraintSlot.GeoDistanceFilter(fieldId)
    }

  private def bound[A](field: SearchField[?, A], value: Bound[A]): CanonicalBound =
    value match {
      case Bound.Unbounded        => CanonicalBound.Unbounded
      case Bound.Inclusive(item) => CanonicalBound.Inclusive(field.codec.encodeCanonical(item))
      case Bound.Exclusive(item) => CanonicalBound.Exclusive(field.codec.encodeCanonical(item))
    }

  private def bounds[A](field: SearchField[?, A], value: RangeBounds[A]): CanonicalRangeBounds =
    CanonicalRangeBounds(bound(field, value.lower), bound(field, value.upper))

  private[plan] def geoPoint(value: GeoPoint): CanonicalGeoPoint =
    CanonicalGeoPoint(SearchValueCodec.geoPoint.encodeCanonical(value))

  private[plan] def distance(value: Distance): CanonicalDistance =
    CanonicalDistance(SearchValueCodec.bigDecimal.encodeCanonical(value.meters))
}
