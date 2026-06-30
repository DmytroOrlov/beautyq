package leaderboard.search.qdrant

import io.circe.Json
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.{EmbeddingSpec, SearchDocumentSpec}
import leaderboard.search.embedding.EmbeddingClient
import leaderboard.search.interpreter.SearchEmbeddingTextExtractor
import zio.{IO, ZIO}

final class QdrantSearchDocumentIndexer[A](
  embeddingClient: EmbeddingClient,
  upsertClient: QdrantPointUpsertClient,
  documentSpec: SearchDocumentSpec[A],
  embeddingSpec: EmbeddingSpec[A],
  pointBuilder: QdrantDocumentPointBuilder[A],
) {
  def upsertDocument(collectionName: String, document: A): IO[QueryFailure, Json] =
    for {
      pointId <- ZIO.fromEither(pointBuilder.qdrantPointId(document))
      text <- ZIO.succeed(SearchEmbeddingTextExtractor.extract(documentSpec, embeddingSpec, document))
      vector <- embeddingClient.embed(text)
      json = QdrantJsonInterpreter.upsertPointJson(pointId, embeddingSpec.vectorName, vector.toList, pointBuilder.payload(document))
      response <- upsertClient.upsertPoint(s"/collections/$collectionName/points?wait=true", json)
    } yield response
}
