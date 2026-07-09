package leaderboard.search.document

import leaderboard.search.beautyq.contract.BeautyQSearchDeclarations

/** Technical facade for the business-facing [[BeautyQSearchDeclarations]]
  * document branch. It owns no field, document, payload, or query policy.
  */
object BeautyQVariantSearchDocumentContract {
  val Fields = BeautyQSearchDeclarations.VariantDocument.Fields
  lazy val documentSpec = BeautyQSearchDeclarations.VariantDocument.documentSpec
  lazy val qdrantPayloadSpec = BeautyQSearchDeclarations.VariantDocument.qdrantPayloadSpec
  lazy val querySchema = BeautyQSearchDeclarations.VariantDocument.querySchema
}
