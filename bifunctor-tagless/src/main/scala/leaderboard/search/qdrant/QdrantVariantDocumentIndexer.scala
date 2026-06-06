package leaderboard.search.qdrant

import io.circe.Json
import leaderboard.model.QueryFailure
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.{EmbeddingSpec, SearchDocumentSpec}
import leaderboard.search.embedding.EmbeddingClient
import zio.IO

trait QdrantVariantDocumentUpsert {
  def upsertDocument(collectionName: String, document: VariantSearchDocument): IO[QueryFailure, Json]
}

final class QdrantVariantDocumentIndexer(
  embeddingClient: EmbeddingClient,
  upsertClient: QdrantPointUpsertClient,
  documentSpec: SearchDocumentSpec[VariantSearchDocument],
  embeddingSpec: EmbeddingSpec[VariantSearchDocument],
) extends QdrantVariantDocumentUpsert {
  private val delegate =
    new QdrantSearchDocumentIndexer[VariantSearchDocument](
      embeddingClient,
      upsertClient,
      documentSpec,
      embeddingSpec,
      QdrantVariantDocumentPointBuilder,
    )

  def upsertDocument(collectionName: String, document: VariantSearchDocument): IO[QueryFailure, Json] =
    delegate.upsertDocument(collectionName, document)
}
