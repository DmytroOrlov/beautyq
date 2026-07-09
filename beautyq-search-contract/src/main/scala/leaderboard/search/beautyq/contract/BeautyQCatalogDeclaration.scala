package leaderboard.search.beautyq.contract

/** Compatibility entry point for the BeautyQ catalog topology. The actual
  * business-facing declaration lives alongside the query declaration in
  * [[BeautyQSearchDeclarations]].
  */
object BeautyQCatalogDeclaration {
  val declaration = BeautyQSearchDeclarations.catalog
}
