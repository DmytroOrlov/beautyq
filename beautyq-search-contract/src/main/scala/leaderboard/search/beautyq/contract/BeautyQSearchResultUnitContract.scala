package leaderboard.search.beautyq.contract

/** Pure descriptor for the BeautyQ variant result unit, the value
  * `SearchDomainSpec.DocumentSection[Document, ResultUnit]` requires as its
  * `resultUnit` field. Kept as literal constants rather than importing
  * [[leaderboard.search.document.BeautyQVariantSearchDocumentContract]] or
  * [[leaderboard.search.dsl.BeautyQSearchPresentation]] here; tests verify
  * these literals against those contract owners instead.
  */
final case class BeautyQSearchResultUnit(
  id: String,
  label: String,
  documentIndexName: String,
  carouselLimitName: String,
)

object BeautyQSearchResultUnitContract {
  val variant: BeautyQSearchResultUnit =
    BeautyQSearchResultUnit(
      id                = "variant",
      label             = "BeautyQ variant result",
      documentIndexName = "beautyq_variant_v1",
      carouselLimitName = "variantSize",
    )
}
