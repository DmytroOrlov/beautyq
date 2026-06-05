package leaderboard.search.qdrant

import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.dsl.VectorSearchSpec
import leaderboard.search.embedding.EmbeddingClient
import zio.{IO, ZIO}

import java.util.UUID
import scala.util.Try

final class QdrantSemanticCandidateSearch(
  embeddingClient: EmbeddingClient,
  qdrantSearchClient: QdrantSearchClient,
) {
  def search(queryText: String, spec: VectorSearchSpec): IO[QueryFailure, List[QdrantCandidateHit]] =
    for {
      embeddingVector <- embeddingClient.embed(queryText)
      searchJson = QdrantJsonInterpreter.searchRequestJson(spec, embeddingVector.toList)
      hits <- qdrantSearchClient.search(s"/collections/${spec.collectionName}/points/search", searchJson)
      candidates <- ZIO.foreach(hits)(toCandidateHit)
    } yield candidates

  private def toCandidateHit(hit: QdrantSearchHit): IO[QueryFailure, QdrantCandidateHit] =
    for {
      variantIdText <- ZIO
        .fromOption(hit.payload("variantId").flatMap(_.asString))
        .orElseFail(QueryFailure.operation("decode-qdrant-candidate-hit", s"Missing payload variantId for Qdrant hit ${hit.id}"))
      variantId <- parseVariantId(variantIdText)
    } yield QdrantCandidateHit(variantId = variantId, score = hit.score)

  private def parseVariantId(value: String): IO[QueryFailure, MasterServiceOfferVariantId] =
    ZIO
      .fromTry(Try(UUID.fromString(value)))
      .mapError(error => QueryFailure.operation("decode-qdrant-candidate-hit", s"Invalid payload variantId $value: ${error.getMessage}"))
}
