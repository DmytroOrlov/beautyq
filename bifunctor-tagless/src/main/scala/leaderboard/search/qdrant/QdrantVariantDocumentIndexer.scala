package leaderboard.search.qdrant

import io.circe.Json
import leaderboard.model.QueryFailure
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.{EmbeddingSpec, SearchDocumentSpec}
import leaderboard.search.embedding.EmbeddingClient
import leaderboard.search.interpreter.SearchEmbeddingTextExtractor
import zio.{IO, ZIO}

trait QdrantVariantDocumentUpsert {
  def upsertDocument(collectionName: String, document: VariantSearchDocument): IO[QueryFailure, Json]
}

final class QdrantVariantDocumentIndexer(
  embeddingClient: EmbeddingClient,
  upsertClient: QdrantPointUpsertClient,
  documentSpec: SearchDocumentSpec[VariantSearchDocument],
  embeddingSpec: EmbeddingSpec[VariantSearchDocument],
) extends QdrantVariantDocumentUpsert {
  def upsertDocument(collectionName: String, document: VariantSearchDocument): IO[QueryFailure, Json] =
    for {
      text <- ZIO.succeed(SearchEmbeddingTextExtractor.extract(documentSpec, embeddingSpec, document))
      vector <- embeddingClient.embed(text)
      json = QdrantVariantDocumentPointBuilder.upsertPointJson(document, embeddingSpec.vectorName, vector.toList)
      response <- upsertClient.upsertPoint(s"/collections/$collectionName/points?wait=true", json)
    } yield response
}
