package leaderboard.search.beautyq.gen2.contract

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

type BeautyQPublicField = PublicFilterField[VariantSearchDocumentGen2]

object BeautyQPublicFilterRegistry {
  /** BeautyQ owns only this ordered public vocabulary: caller-facing names, typed field handles and
    * the one intentional Boolean narrowing. Generic declarations own shape decoding and registry
    * mechanics. */
  private val Fields = BeautyQSearchDeclarations.variants.Fields

  // Business-facing public filter policy: names intentionally differ from storage field ids.
  private def dynamicValueDeclarations[A](
    family: DynamicFieldFamily[VariantSearchDocumentGen2, A],
    prefix: String,
    ordering: Option[Ordering[A]],
  ): Vector[PublicFilterDeclaration[VariantSearchDocumentGen2]] =
    family.entries.map { case (code, field) =>
      ordering match {
        case Some(valueOrdering) => PublicFilterDeclaration.ordered(PublicFieldName(s"$prefix.$code"), field)(using valueOrdering)
        case None                => PublicFilterDeclaration.value(PublicFieldName(s"$prefix.$code"), field)
      }
    }

  // Business-facing dynamic inventory: stable attribute codes define public names and order.
  // One ordered declaration vector is the source for the public inventory, registry and decoder.
  private val declarations: Vector[PublicFilterDeclaration[VariantSearchDocumentGen2]] =
    Vector(
      PublicFilterDeclaration.value(PublicFieldName("service"), Fields.serviceCode),
      PublicFilterDeclaration.value(PublicFieldName("category"), Fields.categoryCode),
      PublicFilterDeclaration.intervalOverlap(PublicFieldName("price"), Fields.priceFrom, Fields.priceTo),
      PublicFilterDeclaration.ordered(PublicFieldName("durationMinutes"), Fields.durationMin),
      PublicFilterDeclaration.geoDistance(PublicFieldName("distanceMeters"), Fields.location),
    ) ++
      dynamicValueDeclarations(Fields.intAttributes, "attribute.int", Some(summon[Ordering[Int]])) ++
      dynamicValueDeclarations(Fields.decimalAttributes, "attribute.decimal", Some(summon[Ordering[BigDecimal]])) ++
      dynamicValueDeclarations(Fields.enumAttributes, "attribute.enum", None) ++
      Fields.booleanAttributes.entries.map { case (code, field) =>
        PublicFilterDeclaration.value(PublicFieldName(s"attribute.boolean.$code"), field)
          .withOperators(Vector(PublicOperator.Equal))
      }

  private val registry = PublicFilterRegistry.unsafeFrom(declarations)

  val fields: Vector[BeautyQPublicField] = registry.fields

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

  private def errors(error: PublicFilterError): NonEmptyErrors[PublicFilterError] =
    NonEmptyErrors.fromHead(error, Vector.empty)

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

  private val registry = PublicSortRegistry.unsafeFrom[BeautySortInput, PublicSortName, DecodedBeautySort, BeautySortError](specs, _.name, BeautySortError.UnknownPublicSort.apply)

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
  final case class QueryTooLong(maxCodePoints: Int, actualCodePoints: Int) extends BeautySearchRequestError
  final case class TooManyFilters(max: Int, actual: Int) extends BeautySearchRequestError
  final case class TooManyRequestedFacets(max: Int, actual: Int) extends BeautySearchRequestError
  final case class TooManySorts(max: Int, actual: Int) extends BeautySearchRequestError
  final case class PageSizeTooLarge(max: Int, actual: Int) extends BeautySearchRequestError
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
    val budgetErrors = Vector(
      request.query.flatMap { value =>
        val actual = value.codePointCount(0, value.length)
        Option.when(actual > BeautyQSearchRequestBudget.MaxQueryCodePoints)(
          BeautySearchRequestError.QueryTooLong(BeautyQSearchRequestBudget.MaxQueryCodePoints, actual)
        )
      },
      Option.when(request.filters.size > BeautyQSearchRequestBudget.MaxFilters)(
        BeautySearchRequestError.TooManyFilters(BeautyQSearchRequestBudget.MaxFilters, request.filters.size)
      ),
      Option.when(request.requestedFacets.size > BeautyQSearchRequestBudget.MaxRequestedFacets)(
        BeautySearchRequestError.TooManyRequestedFacets(BeautyQSearchRequestBudget.MaxRequestedFacets, request.requestedFacets.size)
      ),
      Option.when(request.sort.size > BeautyQSearchRequestBudget.MaxSorts)(
        BeautySearchRequestError.TooManySorts(BeautyQSearchRequestBudget.MaxSorts, request.sort.size)
      ),
      Option.when(request.page.size.value > BeautyQSearchRequestBudget.MaxPageSize)(
        BeautySearchRequestError.PageSizeTooLarge(BeautyQSearchRequestBudget.MaxPageSize, request.page.size.value)
      ),
    ).flatten
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
        Left(prependErrors(budgetErrors ++ filterErrors ++ sortErrors ++ duplicateSortErrors, requestFacetErrors, duplicateFacetErrors))

      case Right(resolvedFacets) =>
        val allErrors = budgetErrors ++ filterErrors ++ sortErrors ++ duplicateSortErrors ++ duplicateFacetErrors
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
