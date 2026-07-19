package leaderboard.search.gen2.core.hydration

import java.time.Instant
import leaderboard.search.gen2.contract.*

enum PlannedConstraintViolationReason {
  case MissingValue
  case TermsMismatch
  case RangeMismatch
  case IntervalOverlapMismatch
  case GeoDistanceMismatch
}

final case class PlannedConstraintViolation(
  constraintIndex: Int,
  fieldId: FieldId,
  reason: PlannedConstraintViolationReason,
)

/** Replays the already-bound hard constraints as a post-hydration integrity assertion. This is not
  * a second capability validator and it never changes candidate order or silently drops a miss. */
object PlannedConstraintAssertion {
  def assertAll[Document](
    document: Document,
    constraints: Vector[PlannedConstraint[Document]],
  ): Either[Vector[PlannedConstraintViolation], Unit] = {
    val violations = constraints.zipWithIndex.flatMap { case (constraint, index) =>
      constraint match {
        case PlannedConstraint.Terms(field, values) =>
          field.extract(document) match {
            case None => Vector(PlannedConstraintViolation(index, field.id, PlannedConstraintViolationReason.MissingValue))
            case Some(value) if values.contains(value) => Vector.empty
            case Some(_) => Vector(PlannedConstraintViolation(index, field.id, PlannedConstraintViolationReason.TermsMismatch))
          }

        case PlannedConstraint.NumberRange(field, bounds) =>
          field.extract(document) match {
            case None => Vector(PlannedConstraintViolation(index, field.id, PlannedConstraintViolationReason.MissingValue))
            case Some(value) if satisfiesRange(field, value, bounds) => Vector.empty
            case Some(_) => Vector(PlannedConstraintViolation(index, field.id, PlannedConstraintViolationReason.RangeMismatch))
          }

        case PlannedConstraint.IntervalOverlap(from, to, bounds) =>
          (from.extract(document), to.extract(document)) match {
            case (Some(fromValue), Some(toValue)) if overlaps(from, to, fromValue, toValue, bounds) => Vector.empty
            case (None, _) => Vector(PlannedConstraintViolation(index, from.id, PlannedConstraintViolationReason.MissingValue))
            case (_, None) => Vector(PlannedConstraintViolation(index, to.id, PlannedConstraintViolationReason.MissingValue))
            case (Some(_), Some(_)) => Vector(PlannedConstraintViolation(index, from.id, PlannedConstraintViolationReason.IntervalOverlapMismatch))
          }

        case PlannedConstraint.GeoDistanceFilter(field, origin, radius) =>
          field.extract(document) match {
            case None => Vector(PlannedConstraintViolation(index, field.id, PlannedConstraintViolationReason.MissingValue))
            case Some(point) if haversineMeters(origin, point) <= radius.meters.toDouble => Vector.empty
            case Some(_) => Vector(PlannedConstraintViolation(index, field.id, PlannedConstraintViolationReason.GeoDistanceMismatch))
          }
      }
    }

    if (violations.isEmpty) Right(()) else Left(violations)
  }

  private def satisfiesRange[Document, A](
    field: SearchField[Document, A],
    value: A,
    bounds: RangeBounds[A],
  ): Boolean =
    lowerSatisfied(field, value, bounds.lower) && upperSatisfied(field, value, bounds.upper)

  private def lowerSatisfied[Document, A](field: SearchField[Document, A], value: A, bound: Bound[A]): Boolean = bound match {
    case Bound.Unbounded        => true
    case Bound.Inclusive(lower) => compare(field, value, lower) >= 0
    case Bound.Exclusive(lower) => compare(field, value, lower) > 0
  }

  private def upperSatisfied[Document, A](field: SearchField[Document, A], value: A, bound: Bound[A]): Boolean = bound match {
    case Bound.Unbounded       => true
    case Bound.Inclusive(upper) => compare(field, value, upper) <= 0
    case Bound.Exclusive(upper) => compare(field, value, upper) < 0
  }

  private def overlaps[Document, A](
    from: SearchField[Document, A],
    to: SearchField[Document, A],
    fromValue: A,
    toValue: A,
    bounds: RangeBounds[A],
  ): Boolean =
    lowerSatisfied(to, toValue, bounds.lower) && upperSatisfied(from, fromValue, bounds.upper)

  private def compare[Document, A](field: SearchField[Document, A], left: A, right: A): Int =
    val leftCanonical = field.codec.encodeCanonical(left)
    val rightCanonical = field.codec.encodeCanonical(right)
    // The declaration builder makes these codec/kind pairs exact. Parsing their own canonical
    // output therefore gives a typed numeric/temporal comparison without a second runtime policy.
    field.kind match {
      case SearchFieldKind.Integer  => leftCanonical.toInt.compare(rightCanonical.toInt)
      case SearchFieldKind.Long     => leftCanonical.toLong.compare(rightCanonical.toLong)
      case SearchFieldKind.Decimal  => BigDecimal(leftCanonical).compare(BigDecimal(rightCanonical))
      case SearchFieldKind.DateTime => Instant.parse(leftCanonical).compareTo(Instant.parse(rightCanonical))
      case _                        => leftCanonical.compareTo(rightCanonical)
    }

  private def haversineMeters(left: GeoPoint, right: GeoPoint): Double = {
    val earthRadiusMeters = 6371008.8
    val lat1 = math.toRadians(left.lat.toDouble)
    val lat2 = math.toRadians(right.lat.toDouble)
    val dLat = lat2 - lat1
    val dLon = math.toRadians(right.lon.toDouble - left.lon.toDouble)
    val a = math.sin(dLat / 2) * math.sin(dLat / 2) + math.cos(lat1) * math.cos(lat2) * math.sin(dLon / 2) * math.sin(dLon / 2)
    earthRadiusMeters * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
  }
}
