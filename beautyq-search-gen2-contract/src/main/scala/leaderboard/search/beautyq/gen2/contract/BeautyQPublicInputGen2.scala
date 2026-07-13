package leaderboard.search.beautyq.gen2.contract

import leaderboard.model.AttributeDefinition
import leaderboard.search.gen2.contract.*

// Compatibility view for the original BeautyQ contract package; the actual operator vocabulary is
// generic and owned by search-gen2-contract.
type PublicOperator = leaderboard.search.gen2.contract.PublicOperator
object PublicOperator {
  export leaderboard.search.gen2.contract.PublicOperator.*
}

final case class BeautySortInput(name: PublicSortName, direction: SortDirection)

type BeautyPublicFilterClause = PublicFilterClause[VariantSearchDocumentGen2]
object BeautyPublicFilterClause {
  export PublicFilterClause.{Constraint, GeoRadius}
}

/** Trusted, server-assigned provenance over a decoded public clause. Construction is restricted to this
  * contract package - only [[BeautyQPublicFilterRegistry.decode]] may produce one - so nothing outside
  * the BeautyQ Gen2 contract boundary can forge `ParsedHard`/`ParsedSoft`/`SystemDefault` provenance for
  * a public filter by constructing this value directly. The public name is retained from the decoder's
  * declaration, never reconstructed by reverse-matching field handles. */
final class DecodedPublicFilter private[contract] (
  val clause: BeautyPublicFilterClause,
  val provenance: ConstraintProvenance,
  val publicName: PublicFieldName,
) {
  override def equals(other: Any): Boolean = other match {
    case value: DecodedPublicFilter => clause == value.clause && provenance == value.provenance
    case _ => false
  }

  override def hashCode(): Int = 31 * clause.hashCode() + provenance.hashCode()
  override def toString: String = s"DecodedPublicFilter($clause, $provenance)"
}

object DecodedPublicFilter {
  private[contract] def withName(name: PublicFieldName, clause: BeautyPublicFilterClause, provenance: ConstraintProvenance): DecodedPublicFilter =
    new DecodedPublicFilter(clause, provenance, name)

  def unapply(value: DecodedPublicFilter): Option[(BeautyPublicFilterClause, ConstraintProvenance)] =
    Some((value.clause, value.provenance))
}

// The unresolved/planned split is generic (PublicSortClause, mirroring PublicFilterClause); this
// alias keeps the original BeautyQ contract package's name for source readability without a second,
// independently defined sort algebra.
type DecodedBeautySort = PublicSortClause[VariantSearchDocumentGen2]
object DecodedBeautySort {
  export PublicSortClause.{Planned, GeoDistance}
}

sealed trait BeautySortError
object BeautySortError {
  final case class UnknownPublicSort(name: PublicSortName) extends BeautySortError
  final case class DuplicatePublicSort(name: PublicSortName) extends BeautySortError
}

final case class BeautyQPublicField(
  name: PublicFieldName,
  fieldHandles: Vector[SearchField[VariantSearchDocumentGen2, ?]],
  acceptedOperators: Vector[PublicOperator],
)

object BeautyQPublicFilterRegistry {
  /** BeautyQ policy is declared in the ordered `staticSpecs`/`dynamicSpecs` inventory below:
    * public names, field handles and intentionally accepted operators. The decoder and registry
    * after that inventory are reusable mechanics and should not be copied into a new domain. */
  private val Fields = BeautyQSearchDeclarations.variants.Fields

  private def publicOperators(field: SearchField[VariantSearchDocumentGen2, ?]): Vector[PublicOperator] =
    PublicOperator.fromFilterCapabilities(field.capabilities.filterOperators.toVector)

  private sealed trait Spec extends PublicInputSpec[PublicFilterInput, PublicFieldName, PublicOperator, BeautyPublicFilterClause, PublicFilterError] {
    def public: BeautyQPublicField

    def name: PublicFieldName = public.name
    def acceptedOperators: Vector[PublicOperator] = public.acceptedOperators
  }

  private final case class ValueSpec[A](override val name: PublicFieldName, field: SearchField[VariantSearchDocumentGen2, A], ordering: Option[Ordering[A]], operators: Option[Vector[PublicOperator]] = None) extends Spec {
    val public: BeautyQPublicField = BeautyQPublicField(name, Vector(field), operators.getOrElse(publicOperators(field)))

    def decode(input: PublicFilterInput): Either[NonEmptyErrors[PublicFilterError], BeautyPublicFilterClause] =
      input.operator match {
        case PublicOperator.Equal => scalar(input, "Scalar").flatMap(raw => decodeValue(input.field, field, raw)).map(value => BeautyPublicFilterClause.Constraint(PlannedConstraint.Terms(field, Set(value))))
        case PublicOperator.In => many(input).flatMap(values => decodeMany(input, field, values)).map(values => BeautyPublicFilterClause.Constraint(PlannedConstraint.Terms(field, values.toSet)))
        case operator => range(input, field, operator)
      }

    private def range(input: PublicFilterInput, target: SearchField[VariantSearchDocumentGen2, A], operator: PublicOperator): Either[NonEmptyErrors[PublicFilterError], BeautyPublicFilterClause] = {
      val decoded = input.value match {
        case PublicFilterValue.Scalar(raw) if operator != PublicOperator.Between =>
          decodeValue(input.field, target, raw).flatMap(value => singleBound(input.field, operator, value))
        case PublicFilterValue.BetweenBounds(lower, upper, lowerInclusive, upperInclusive) if operator == PublicOperator.Between =>
          for {
            low <- decodeValue(input.field, target, lower)
            high <- decodeValue(input.field, target, upper)
            bounds <- ordering match {
              case Some(valueOrdering) => boundsFor(input.field, lower, upper, low, high, lowerInclusive, upperInclusive)(using valueOrdering)
              case None => Left(errors(PublicFilterError.InvalidBetweenBounds(input.field, lower, upper, "field does not support ordered bounds")))
            }
          } yield bounds
        case _ => Left(errors(PublicFilterError.WrongPublicValueShape(input.field, operator, if (operator == PublicOperator.Between) "BetweenBounds" else "Scalar")))
      }
      decoded.map(bounds => BeautyPublicFilterClause.Constraint(PlannedConstraint.NumberRange(target, bounds)))
    }
  }

  private final case class PriceSpec(override val name: PublicFieldName, from: SearchField[VariantSearchDocumentGen2, BigDecimal], to: SearchField[VariantSearchDocumentGen2, BigDecimal]) extends Spec {
    val public: BeautyQPublicField = BeautyQPublicField(name, Vector(from, to), publicOperators(from))

    def decode(input: PublicFilterInput): Either[NonEmptyErrors[PublicFilterError], BeautyPublicFilterClause] = {
      val decoded: Either[NonEmptyErrors[PublicFilterError], RangeBounds[BigDecimal]] = input.value match {
        case PublicFilterValue.Scalar(raw) if input.operator != PublicOperator.Between =>
          decodeValue(input.field, from, raw).flatMap(value => singleBound(input.field, input.operator, value))
        case PublicFilterValue.BetweenBounds(lower, upper, lowerInclusive, upperInclusive) if input.operator == PublicOperator.Between =>
          for {
            low <- decodeValue(input.field, from, lower)
            high <- decodeValue(input.field, from, upper)
            bounds <- boundsFor(input.field, lower, upper, low, high, lowerInclusive, upperInclusive)
          } yield bounds
        case _ => Left(errors(PublicFilterError.WrongPublicValueShape(input.field, input.operator, if (input.operator == PublicOperator.Between) "BetweenBounds" else "Scalar")))
      }
      decoded.map(bounds => BeautyPublicFilterClause.Constraint(PlannedConstraint.IntervalOverlap(from, to, bounds)))
    }
  }

  private final case class DistanceSpec(override val name: PublicFieldName, field: SearchField[VariantSearchDocumentGen2, GeoPoint]) extends Spec {
    val public: BeautyQPublicField = BeautyQPublicField(name, Vector(field), publicOperators(field))

    def decode(input: PublicFilterInput): Either[NonEmptyErrors[PublicFilterError], BeautyPublicFilterClause] =
      scalar(input, "Scalar").flatMap { raw =>
        SearchValueCodec.bigDecimal.decodeCanonical(raw) match {
          case Left(error) => Left(errors(PublicFilterError.InvalidCanonicalValue(input.field, error.typeId, raw, error.message)))
          case Right(meters) if meters <= 0 => Left(errors(PublicFilterError.NonPositiveDistance(input.field, raw)))
          case Right(meters) => Right(BeautyPublicFilterClause.GeoRadius(field, Distance(meters)))
        }
      }
  }

  // Business-facing public filter policy: names intentionally differ from storage field ids.
  private val staticSpecs: Vector[Spec] = Vector(
    ValueSpec(PublicFieldName("service"), Fields.serviceCode, None),
    ValueSpec(PublicFieldName("category"), Fields.categoryCode, None),
    PriceSpec(PublicFieldName("price"), Fields.priceFrom, Fields.priceTo),
    ValueSpec(PublicFieldName("durationMinutes"), Fields.durationMin, Some(summon[Ordering[Int]])),
    DistanceSpec(PublicFieldName("distanceMeters"), Fields.location),
  )

  private def dynamicValueSpecs[A](
    definitions: Iterable[AttributeDefinition[?]],
    fields: Map[String, SearchField[VariantSearchDocumentGen2, A]],
    prefix: String,
    ordering: Option[Ordering[A]],
    operators: Option[Vector[PublicOperator]] = None,
  ): Vector[Spec] =
    definitions.toVector.flatMap(definition => fields.get(definition.code).map(field => ValueSpec(PublicFieldName(s"$prefix.${definition.code}"), field, ordering, operators)))

  // Business-facing dynamic inventory: stable attribute codes define public names and order.
  private def dynamicSpecs: Vector[Spec] =
    dynamicValueSpecs(AttributeDefinition.intDefinitions, Fields.intAttributesByCode, "attribute.int", Some(summon[Ordering[Int]])) ++
      dynamicValueSpecs(AttributeDefinition.bigDecimalDefinitions, Fields.decimalAttributesByCode, "attribute.decimal", Some(summon[Ordering[BigDecimal]])) ++
      dynamicValueSpecs(AttributeDefinition.enumDefinitions, Fields.enumAttributesByCode, "attribute.enum", None: Option[Ordering[String]]) ++
      dynamicValueSpecs(AttributeDefinition.booleanDefinitions, Fields.booleanAttributesByCode, "attribute.boolean", None: Option[Ordering[Boolean]], Some(Vector(PublicOperator.Equal)))

  // Keep the public inventory ordered: static names first, then the source AttributeDefinition inventories.
  private val specs: Vector[Spec] = staticSpecs ++ dynamicSpecs

  val fields: Vector[BeautyQPublicField] = specs.map(_.public)

  private val registry = PublicInputRegistry[PublicFilterInput, PublicFieldName, PublicOperator, BeautyPublicFilterClause, PublicFilterError](
    specs,
    _.field,
    _.operator,
    PublicFilterError.UnknownPublicField.apply,
    PublicFilterError.UnsupportedPublicOperator.apply,
  )

  def publicNameOf(filter: DecodedPublicFilter): PublicFieldName = filter.publicName

  def decode(input: PublicFilterInput): Either[NonEmptyErrors[PublicFilterError], DecodedPublicFilter] = {
    val result = registry.decodeNamed(input)
    result.flatMap { case (name, clause) =>
      input.presentationId match {
        case None => Right(DecodedPublicFilter.withName(name, clause, ConstraintProvenance.ExplicitUi))
        case Some(id) if id.value.trim.isEmpty => Left(errors(PublicFilterError.BlankFacetSelectionId(input.field)))
        case Some(id) => Right(DecodedPublicFilter.withName(name, clause, ConstraintProvenance.FacetSelection(id)))
      }
    }
  }

  private def scalar(input: PublicFilterInput, expected: String): Either[NonEmptyErrors[PublicFilterError], String] = input.value match {
    case PublicFilterValue.Scalar(value) => Right(value)
    case _ => Left(errors(PublicFilterError.WrongPublicValueShape(input.field, input.operator, expected)))
  }

  private def many(input: PublicFilterInput): Either[NonEmptyErrors[PublicFilterError], Vector[String]] = input.value match {
    case PublicFilterValue.Many(values) if values.nonEmpty => Right(values)
    case PublicFilterValue.Many(_) => Left(errors(PublicFilterError.EmptyPublicValues(input.field)))
    case _ => Left(errors(PublicFilterError.WrongPublicValueShape(input.field, input.operator, "Many")))
  }

  // Decodes every input value regardless of earlier failures, so a request with several invalid values
  // reports all of them (with their exact index) in one response instead of one-at-a-time round trips.
  // Canonical-duplicate detection only runs once every value has decoded successfully, and only then is
  // the final Vector/Set constructed - a decode failure must never be shadowed by a spurious duplicate
  // report over a partially-decoded result.
  private def decodeMany[A](input: PublicFilterInput, field: SearchField[VariantSearchDocumentGen2, A], values: Vector[String]): Either[NonEmptyErrors[PublicFilterError], Vector[A]] = {
    val (decodeErrors, decodedValues) =
      values.zipWithIndex.map { case (raw, index) =>
        field.codec.decodeCanonical(raw) match {
          case Right(value) => Right(value)
          case Left(error)  => Left(PublicFilterError.InvalidCanonicalValueAt(input.field, index, error.typeId, raw, error.message))
        }
      }.partitionMap(identity)

    NonEmptyErrors.fromVector(decodeErrors) match {
      case Some(errs) => Left(errs)
      case None =>
        val canonical = decodedValues.map(field.codec.encodeCanonical)
        canonical.find(value => canonical.count(_ == value) > 1) match {
          case Some(value) => Left(errors(PublicFilterError.DuplicateCanonicalTerm(input.field, value)))
          case None        => Right(decodedValues)
        }
    }
  }

  private def decodeValue[A](name: PublicFieldName, field: SearchField[VariantSearchDocumentGen2, A], raw: String): Either[NonEmptyErrors[PublicFilterError], A] =
    field.codec.decodeCanonical(raw) match {
      case Right(value) => Right(value)
      case Left(error)  => Left(errors(PublicFilterError.InvalidCanonicalValue(name, error.typeId, raw, error.message)))
    }

  // Total over all eight PublicOperator cases, but only the four legal single-bound operators produce a
  // bound: an operator that cannot legally reach this helper (Equal, In, Between, WithinDistance) still
  // produces a typed WrongPublicValueShape decoding error here rather than an invented equality range,
  // even though every current caller already excludes those operators before calling in.
  private def singleBound[A](name: PublicFieldName, operator: PublicOperator, value: A): Either[NonEmptyErrors[PublicFilterError], RangeBounds[A]] = operator match {
    case PublicOperator.GreaterThan        => Right(RangeBounds(Bound.Exclusive(value), Bound.Unbounded))
    case PublicOperator.GreaterThanOrEqual => Right(RangeBounds(Bound.Inclusive(value), Bound.Unbounded))
    case PublicOperator.LessThan           => Right(RangeBounds(Bound.Unbounded, Bound.Exclusive(value)))
    case PublicOperator.LessThanOrEqual    => Right(RangeBounds(Bound.Unbounded, Bound.Inclusive(value)))
    case other                             => Left(errors(PublicFilterError.WrongPublicValueShape(name, other, "Scalar")))
  }

  private def boundsFor[A: Ordering](name: PublicFieldName, lowerRaw: String, upperRaw: String, lower: A, upper: A, lowerInclusive: Boolean, upperInclusive: Boolean): Either[NonEmptyErrors[PublicFilterError], RangeBounds[A]] = {
    val ordering = summon[Ordering[A]]
    if (ordering.gt(lower, upper)) Left(errors(PublicFilterError.InvalidBetweenBounds(name, lowerRaw, upperRaw, "lower bound is greater than upper bound")))
    else if (ordering.equiv(lower, upper) && (!lowerInclusive || !upperInclusive)) Left(errors(PublicFilterError.InvalidBetweenBounds(name, lowerRaw, upperRaw, "equal endpoints require both bounds inclusive")))
    else Right(RangeBounds(if (lowerInclusive) Bound.Inclusive(lower) else Bound.Exclusive(lower), if (upperInclusive) Bound.Inclusive(upper) else Bound.Exclusive(upper)))
  }

  private def errors(error: PublicFilterError): NonEmptyErrors[PublicFilterError] = NonEmptyErrors.fromHead(error, Vector.empty)
}

object BeautyQPublicSortRegistry {
  /** Business-facing sort names and their typed field policy. Registry lookup is generic mechanics. */
  private val Fields = BeautyQSearchDeclarations.variants.Fields

  private final case class SortSpec(name: PublicSortName, decoder: BeautySortInput => Either[NonEmptyErrors[BeautySortError], DecodedBeautySort]) extends PublicSortSpec[BeautySortInput, PublicSortName, DecodedBeautySort, BeautySortError] {
    def decode(input: BeautySortInput): Either[NonEmptyErrors[BeautySortError], DecodedBeautySort] = decoder(input)
  }

  private val specs: Vector[SortSpec] = Vector(
    SortSpec(PublicSortName("price"), input => Right(DecodedBeautySort.Planned(PlannedSort.FieldValue(Fields.priceFrom, input.direction)))),
    SortSpec(PublicSortName("durationMinutes"), input => Right(DecodedBeautySort.Planned(PlannedSort.FieldValue(Fields.durationMin, input.direction)))),
    SortSpec(PublicSortName("distanceMeters"), input => Right(DecodedBeautySort.GeoDistance(Fields.location, input.direction))),
  )

  private val registry = PublicSortRegistry[BeautySortInput, PublicSortName, DecodedBeautySort, BeautySortError](specs, _.name, BeautySortError.UnknownPublicSort.apply)

  val names: Vector[PublicSortName] = registry.names

  // Pairs the decoded clause with its originating public name (BeautySortInput.name), the same fact
  // DecodedPublicFilter already preserves for filters, since it is otherwise lost once decoding produces
  // a bare DecodedBeautySort. Brick 4F's plan compiler needs the name to report exactly which public sort
  // is missing a location.
  def decode(input: BeautySortInput): Either[NonEmptyErrors[BeautySortError], DecodedBeautySortInput] =
    registry.decode(input).map(clause => DecodedBeautySortInput(input.name, clause))
}

/** A decoded public sort clause paired with its originating public name, mirroring [[DecodedPublicFilter]]'s
  * own name-preserving construction. */
final case class DecodedBeautySortInput(name: PublicSortName, clause: DecodedBeautySort)

sealed trait BeautySearchRequestError
object BeautySearchRequestError {
  final case class InvalidFilter(index: Int, error: PublicFilterError) extends BeautySearchRequestError
  final case class InvalidSort(index: Int, error: BeautySortError) extends BeautySearchRequestError
  final case class UnknownRequestedFacet(id: FacetId) extends BeautySearchRequestError
  final case class DuplicateRequestedFacet(id: FacetId) extends BeautySearchRequestError
}

final case class BeautySearchRequestGen2(
  query: Option[String],
  filters: Vector[PublicFilterInput],
  requestedFacets: Vector[FacetId],
  sort: Vector[BeautySortInput],
  page: PageRequest,
  userLocation: Option[GeoPoint],
)

/** The trusted, decoded request. Construction is restricted to this contract package - only
  * [[BeautySearchRequestGen2.validate]] may produce one - so nothing outside the BeautyQ Gen2 contract
  * boundary can forge a validated request (e.g. claiming decoded filters/sort without having actually
  * run them through the registries above). */
final case class ValidatedBeautySearchRequestGen2 private[contract] (
  query: Option[String],
  filters: Vector[DecodedPublicFilter],
  facets: Vector[FacetRequest[VariantSearchDocumentGen2]],
  sort: Vector[DecodedBeautySortInput],
  page: PageRequest,
  userLocation: Option[GeoPoint],
) {
  /** Public IDs are a derived trace/presentation view; compilation consumes the already-resolved
    * typed facet requests carried by this trusted boundary. */
  def requestedFacets: Vector[FacetId] = facets.map(_.id)
}

object BeautySearchRequestGen2 {
  def validate(request: BeautySearchRequestGen2): Either[NonEmptyErrors[BeautySearchRequestError], ValidatedBeautySearchRequestGen2] = {
    val filterResults = request.filters.map(BeautyQPublicFilterRegistry.decode)
    val filterErrors = filterResults.zipWithIndex.flatMap { case (result, index) => result match {
      case Left(errors) => errors.toVector.map(error => BeautySearchRequestError.InvalidFilter(index, error))
      case Right(_)     => Vector.empty
    }}
    val sortResults = request.sort.map(BeautyQPublicSortRegistry.decode)
    val sortErrors = sortResults.zipWithIndex.flatMap { case (result, index) => result match {
      case Left(errors) => errors.toVector.map(error => BeautySearchRequestError.InvalidSort(index, error))
      case Right(_)     => Vector.empty
    }}
    val duplicateFacetErrors = request.requestedFacets.groupBy(identity).collect { case (id, values) if values.size > 1 => id }.toVector.sortBy(_.value).map(BeautySearchRequestError.DuplicateRequestedFacet.apply)
    val duplicateSortErrors = request.sort.groupBy(_.name).collect { case (name, values) if values.size > 1 => name }.toVector.sortBy(_.value).map(BeautySortError.DuplicatePublicSort.apply).map(error => BeautySearchRequestError.InvalidSort(request.sort.indexWhere(_.name == error.name), error))
    val facetResolution = BeautyQSearchPlanPolicy.facetRegistry.resolve(request.requestedFacets)

    facetResolution match {
      case Left(facetErrors) =>
        val requestFacetErrors = facetErrors.map(error => BeautySearchRequestError.UnknownRequestedFacet(error.id))
        Left(prependErrors(filterErrors ++ sortErrors ++ duplicateSortErrors, requestFacetErrors, duplicateFacetErrors))

      case Right(resolvedFacets) =>
        val allErrors = filterErrors ++ sortErrors ++ duplicateSortErrors ++ duplicateFacetErrors
        NonEmptyErrors.fromVector(allErrors) match {
          case Some(errors) => Left(errors)
          case None =>
            val decodedFilters = filterResults.collect { case Right(value) => value }
            val decodedSort = sortResults.collect { case Right(value) => value }
            Right(ValidatedBeautySearchRequestGen2(request.query, decodedFilters, resolvedFacets, decodedSort, request.page, request.userLocation))
        }
    }
  }

  private def prependErrors(
    prefix: Vector[BeautySearchRequestError],
    middle: NonEmptyErrors[BeautySearchRequestError],
    suffix: Vector[BeautySearchRequestError],
  ): NonEmptyErrors[BeautySearchRequestError] =
    prefix match {
      case first +: rest => NonEmptyErrors.fromHead(first, rest ++ middle.toVector ++ suffix)
      case _             => NonEmptyErrors.fromHead(middle.head, middle.tail ++ suffix)
    }
}
