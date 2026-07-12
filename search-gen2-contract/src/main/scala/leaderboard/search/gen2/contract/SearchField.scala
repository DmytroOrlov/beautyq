package leaderboard.search.gen2.contract

import leaderboard.model.{CanonicalStringValue, UuidBackedId}

import java.time.Instant
import scala.annotation.unused

enum SearchFieldKind {
  case Keyword
  case Text
  case Integer
  case Long
  case Decimal
  case Boolean
  case DateTime
  case GeoPoint
}

enum FilterOperator {
  case Equal
  case In
  case Range
  case GeoDistance
}

enum FacetMode {
  case Terms
  case Range
}

enum SortMode {
  case Value
  case Distance
}

enum GroupMode {
  case Terms
}

/** Tautological default [[SearchFieldKind]] evidence for a direct field's value type: only for value
  * types with exactly one unambiguous backend-neutral kind. Deliberately has no `String` instance -
  * keyword versus text is business policy for a raw `String` field and must stay an explicit author
  * choice (`.keyword`/`.text`), never guessed.
  */
trait DefaultSearchFieldKind[A] {
  def kind: SearchFieldKind
}

object DefaultSearchFieldKind {
  given DefaultSearchFieldKind[Int] with {
    def kind: SearchFieldKind = SearchFieldKind.Integer
  }

  given DefaultSearchFieldKind[Long] with {
    def kind: SearchFieldKind = SearchFieldKind.Long
  }

  given DefaultSearchFieldKind[BigDecimal] with {
    def kind: SearchFieldKind = SearchFieldKind.Decimal
  }

  given DefaultSearchFieldKind[Boolean] with {
    def kind: SearchFieldKind = SearchFieldKind.Boolean
  }

  given DefaultSearchFieldKind[Instant] with {
    def kind: SearchFieldKind = SearchFieldKind.DateTime
  }

  given DefaultSearchFieldKind[GeoPoint] with {
    def kind: SearchFieldKind = SearchFieldKind.GeoPoint
  }

  // The `using` evidence is an intentional coherence gate, not a future placeholder: it is what
  // restricts this given to exactly the types that actually have a UuidBackedId/CanonicalStringValue
  // instance, so it must stay even though `kind` itself does not need the evidence's value.
  given uuidBacked[A](using @unused id: UuidBackedId[A]): DefaultSearchFieldKind[A] =
    new DefaultSearchFieldKind[A] {
      def kind: SearchFieldKind = SearchFieldKind.Keyword
    }

  given canonicalString[A](using @unused value: CanonicalStringValue[A]): DefaultSearchFieldKind[A] =
    new DefaultSearchFieldKind[A] {
      def kind: SearchFieldKind = SearchFieldKind.Keyword
    }
}

final case class FieldCapabilities(
  searchable: Boolean = false,
  filterOperators: Set[FilterOperator] = Set.empty,
  facetModes: Set[FacetMode] = Set.empty,
  sortModes: Set[SortMode] = Set.empty,
  groupModes: Set[GroupMode] = Set.empty,
  payloadEligible: Boolean = false,
)

sealed trait FieldExtraction[Document, Value] {
  def extract(document: Document): Option[Value]
  def required: Boolean
}

object FieldExtraction {
  final case class Required[Document, Value] private[contract] (get: Document => Value) extends FieldExtraction[Document, Value] {
    def extract(document: Document): Option[Value] = Some(get(document))
    def required: Boolean = true
  }

  final case class Optional[Document, Value] private[contract] (get: Document => Option[Value]) extends FieldExtraction[Document, Value] {
    def extract(document: Document): Option[Value] = get(document)
    def required: Boolean = false
  }
}

/** An immutable typed field handle. Capability methods are additive copies and never throw; final
  * compatibility validation happens when the owning document declaration is built.
  */
final case class SearchField[Document, Value] private[contract] (
  id: FieldId,
  path: FieldPath,
  kind: SearchFieldKind,
  extraction: FieldExtraction[Document, Value],
  codec: SearchValueCodec[Value],
  semantic: Option[FieldSemantic],
  capabilities: FieldCapabilities,
) {
  def extract(document: Document): Option[Value] = extraction.extract(document)
  def required: Boolean = extraction.required

  def withSemantic(value: String): SearchField[Document, Value] =
    copy(semantic = Some(FieldSemantic(value)))

  def searchable: SearchField[Document, Value] =
    copy(capabilities = capabilities.copy(searchable = true))

  def filterable(first: FilterOperator, rest: FilterOperator*): SearchField[Document, Value] =
    copy(capabilities = capabilities.copy(filterOperators = capabilities.filterOperators ++ (first +: rest)))

  def facetable(first: FacetMode, rest: FacetMode*): SearchField[Document, Value] =
    copy(capabilities = capabilities.copy(facetModes = capabilities.facetModes ++ (first +: rest)))

  def sortable(first: SortMode, rest: SortMode*): SearchField[Document, Value] =
    copy(capabilities = capabilities.copy(sortModes = capabilities.sortModes ++ (first +: rest)))

  def groupable(first: GroupMode, rest: GroupMode*): SearchField[Document, Value] =
    copy(capabilities = capabilities.copy(groupModes = capabilities.groupModes ++ (first +: rest)))

  def payloadEligible: SearchField[Document, Value] =
    copy(capabilities = capabilities.copy(payloadEligible = true))
}

/** Constructor-stage type binding a field's identity/path/extraction/codec to one of the exact
  * backend-neutral kinds. `keyword` accepts any codec-mapped logical type (including future wrapper
  * types); the other kind methods require their exact Scala value type.
  */
final class SearchFieldKindBuilder[Document, Value] private[contract] (
  id: FieldId,
  path: FieldPath,
  extraction: FieldExtraction[Document, Value],
  codec: SearchValueCodec[Value],
) {
  private def build(kind: SearchFieldKind): SearchField[Document, Value] =
    SearchField(id, path, kind, extraction, codec, None, FieldCapabilities())

  def keyword: SearchField[Document, Value] = build(SearchFieldKind.Keyword)

  def text(using Value =:= String): SearchField[Document, Value] = build(SearchFieldKind.Text)

  def integer(using Value =:= Int): SearchField[Document, Value] = build(SearchFieldKind.Integer)

  def long(using Value =:= Long): SearchField[Document, Value] = build(SearchFieldKind.Long)

  def decimal(using Value =:= BigDecimal): SearchField[Document, Value] = build(SearchFieldKind.Decimal)

  def boolean(using Value =:= Boolean): SearchField[Document, Value] = build(SearchFieldKind.Boolean)

  def dateTime(using Value =:= Instant): SearchField[Document, Value] = build(SearchFieldKind.DateTime)

  def geoPoint(using Value =:= GeoPoint): SearchField[Document, Value] = build(SearchFieldKind.GeoPoint)
}

private[contract] object SearchFieldKindBuilder {

  // Scala 3 forbids constructing a private-constructor class (or case class) directly inside an
  // inline method body, since the constructor reference would need to be preserved at foreign inline
  // call sites. `field` is inline (to forward its selector to the compile-time path macro), so it
  // reaches both private constructors - FieldExtraction.Required's and SearchFieldKindBuilder's -
  // through this ordinary, non-inline factory instead.
  def forRequired[Document, Value](
    id: FieldId,
    path: FieldPath,
    get: Document => Value,
    codec: SearchValueCodec[Value],
  ): SearchFieldKindBuilder[Document, Value] =
    new SearchFieldKindBuilder[Document, Value](id, path, FieldExtraction.Required(get), codec)
}

/** Declares a direct document field: `id` is explicit and independent from the derived path, `selector`
  * must be a direct field selection (`_.fieldName`, checked at compile time by [[SearchFieldDerivation]]),
  * and extraction is required.
  */
inline def field[Document, Value](
  inline id: String,
  inline selector: Document => Value,
)(using codec: SearchValueCodec[Value]): SearchFieldKindBuilder[Document, Value] =
  SearchFieldKindBuilder.forRequired[Document, Value](
    FieldId(id),
    FieldPath(SearchFieldDerivation.path[Document, Value](selector)),
    selector,
    codec,
  )

/** Derives the stable path of one direct stored field selector for reusable declaration builders
  * outside `search-gen2-contract`. The caller supplies only `_.fieldName`; the same compile-time
  * restrictions as [[field]] reject nested selectors, methods and expressions.
  */
inline def directFieldPath[Document, Value](inline selector: Document => Value): String =
  SearchFieldDerivation.path[Document, Value](selector)

/** Declares a computed/dynamic document field with an explicit path: the only Commit 2 API for nested,
  * computed, map-backed, or dynamic fields such as `enumAttributes.color`. Extraction is optional.
  */
def computedField[Document, Value](
  id: String,
  path: String,
)(
  extract: Document => Option[Value]
)(using codec: SearchValueCodec[Value]): SearchFieldKindBuilder[Document, Value] =
  new SearchFieldKindBuilder[Document, Value](
    FieldId(id),
    FieldPath(path),
    FieldExtraction.Optional(extract),
    codec,
  )
