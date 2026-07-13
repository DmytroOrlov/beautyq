package leaderboard.search.gen2.contract

/** Backend-neutral public filter vocabulary. Public names are API policy and intentionally do not
  * come from a SearchField path. */
final case class PublicFieldName(value: String)

enum PublicOperator {
  case Equal, In, GreaterThan, GreaterThanOrEqual, LessThan, LessThanOrEqual, Between, WithinDistance
}

object PublicOperator {
  /** Mechanical projection from backend-neutral field capability to the public scalar operators. A
    * domain may still choose a narrower public policy explicitly; the default covers the common
    * one-to-one declaration used by the Gen2 request DSL. */
  def fromFilterCapabilities(capabilities: Iterable[FilterOperator]): Vector[PublicOperator] = {
    val available = capabilities.toSet
    Vector(
      if (available.contains(FilterOperator.Equal)) Vector(Equal) else Vector.empty,
      if (available.contains(FilterOperator.In)) Vector(In) else Vector.empty,
      if (available.contains(FilterOperator.Range)) Vector(GreaterThan, GreaterThanOrEqual, LessThan, LessThanOrEqual, Between) else Vector.empty,
      if (available.contains(FilterOperator.GeoDistance)) Vector(WithinDistance) else Vector.empty,
    ).flatten
  }
}

sealed trait PublicFilterValue
object PublicFilterValue {
  final case class Scalar(value: String) extends PublicFilterValue
  final case class Many(values: Vector[String]) extends PublicFilterValue
  final case class BetweenBounds(lower: String, upper: String, lowerInclusive: Boolean, upperInclusive: Boolean) extends PublicFilterValue
}

final case class PublicFilterInput(
  field: PublicFieldName,
  operator: PublicOperator,
  value: PublicFilterValue,
  presentationId: Option[FacetSelectionId],
)

final case class PublicSortName(value: String)

sealed trait PublicFilterError
object PublicFilterError {
  final case class UnknownPublicField(name: PublicFieldName) extends PublicFilterError
  final case class UnsupportedPublicOperator(name: PublicFieldName, operator: PublicOperator, accepted: Vector[PublicOperator]) extends PublicFilterError
  final case class WrongPublicValueShape(name: PublicFieldName, operator: PublicOperator, expected: String) extends PublicFilterError
  final case class EmptyPublicValues(name: PublicFieldName) extends PublicFilterError
  final case class BlankFacetSelectionId(name: PublicFieldName) extends PublicFilterError
  final case class InvalidCanonicalValue(name: PublicFieldName, typeId: SearchValueTypeId, raw: String, message: String) extends PublicFilterError
  final case class InvalidCanonicalValueAt(name: PublicFieldName, index: Int, typeId: SearchValueTypeId, raw: String, message: String) extends PublicFilterError
  final case class InvalidBetweenBounds(name: PublicFieldName, lower: String, upper: String, message: String) extends PublicFilterError
  final case class NonPositiveDistance(name: PublicFieldName, meters: String) extends PublicFilterError
  final case class DuplicateCanonicalTerm(name: PublicFieldName, canonical: String) extends PublicFilterError
}

/** The generic typed result of public filter decoding. A domain may alias this to its document type
  * or wrap it with additional domain response policy. */
sealed trait PublicFilterClause[Document]
object PublicFilterClause {
  final case class Constraint[Document](value: PlannedConstraint[Document]) extends PublicFilterClause[Document]
  final case class GeoRadius[Document](field: SearchField[Document, GeoPoint], radius: Distance) extends PublicFilterClause[Document]
}
