package leaderboard.search.document

import leaderboard.model.QueryFailure

final case class BeautySearchReadyCatalogDocuments(
  source: String,
  documents: List[VariantSearchDocument],
)

object BeautySearchReadyCatalogDocuments {
  def from(source: String, documents: List[VariantSearchDocument]): Either[QueryFailure, BeautySearchReadyCatalogDocuments] =
    if (source.trim.isEmpty) {
      Left(QueryFailure.domain("catalog document source is empty"))
    } else if (documents.isEmpty) {
      Left(QueryFailure.domain(s"$source catalog documents are empty"))
    } else {
      Right(BeautySearchReadyCatalogDocuments(source, documents))
    }
}
