package leaderboard.search.beautyq.contract

import leaderboard.search.contract.{SearchField, SearchFieldKind, SearchFieldName}
import leaderboard.search.document.BeautyQVariantSearchDocumentContract
import leaderboard.search.dsl

/** Derives the generic document field list `DocumentSection` requires from
  * the existing BeautyQ document contract. BeautyQ text fields map to
  * generic `Text`, not `SemanticText`: `BeautySearchSpecV1.spec` declares no
  * embedding spec, so there is no source-owned semantic/vector field yet.
  * Facetable keyword/boolean fields map to generic `Facet`, the aggregatable
  * facet-field descriptor; facetable integer/decimal fields map to generic
  * `Range`, since BeautyQ's range constraints/facet handling are
  * source-confirmed for numeric range fields - generic fields carry no
  * separate facetable/range flags to preserve instead.
  */
object BeautyQSearchDocumentFieldContract {
  val fields: List[SearchField] =
    BeautyQVariantSearchDocumentContract.documentSpec.fields.map(toGenericField)

  def toGenericField(field: dsl.SearchField[?]): SearchField =
    SearchField(
      name = SearchFieldName(field.path),
      kind = toGenericKind(field),
    )

  def toGenericKind(field: dsl.SearchField[?]): SearchFieldKind =
    field.kind match {
      case dsl.SearchFieldKind.Text =>
        SearchFieldKind.Text
      case dsl.SearchFieldKind.Keyword if field.facetable =>
        SearchFieldKind.Facet
      case dsl.SearchFieldKind.Keyword =>
        SearchFieldKind.Keyword
      case dsl.SearchFieldKind.Boolean if field.facetable =>
        SearchFieldKind.Facet
      case dsl.SearchFieldKind.Boolean =>
        SearchFieldKind.Keyword
      case dsl.SearchFieldKind.Integer if field.facetable =>
        SearchFieldKind.Range
      case dsl.SearchFieldKind.Integer =>
        SearchFieldKind.Numeric
      case dsl.SearchFieldKind.Decimal if field.facetable =>
        SearchFieldKind.Range
      case dsl.SearchFieldKind.Decimal =>
        SearchFieldKind.Numeric
      case dsl.SearchFieldKind.GeoPoint =>
        SearchFieldKind.Geo
    }
}
