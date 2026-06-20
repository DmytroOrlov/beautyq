package leaderboard.search

import distage.{DIKey, Mode}
import izumi.distage.model.definition.Activation
import leaderboard.{LeaderboardTest, ProdTest}
import leaderboard.config.QdrantPortCfg
import leaderboard.search.dsl.{EmbeddingSpec, VectorDistance, VectorSearchSpec}
import leaderboard.search.qdrant.{
  QdrantClient,
  QdrantClientCollectionInfoAdapter,
  QdrantCollectionCompatibilityChecker,
  QdrantCollectionCompatibilityMismatch,
  QdrantCollectionIdentity,
  QdrantJsonInterpreter,
}
import java.util.UUID
import zio.ZIO

final class QdrantCollectionCompatibilityIntegrationSpec extends LeaderboardTest with ProdTest {
  override def config = {
    val base = super.config
    base.copy(
      activation = base.activation ++ Activation(Mode -> Mode.Test),
      memoizationRoots = base.memoizationRoots + DIKey[QdrantPortCfg],
    )
  }

  "Qdrant collection compatibility integration" should {
    "check a real collection-info response" in {
      (
        portCfg: QdrantPortCfg,
      ) =>
        val qdrantClient = new QdrantClient(portCfg.host, portCfg.port)
        val collectionName = s"collection_compat_${UUID.randomUUID().toString.replace('-', '_')}"
        val collectionPath = s"/collections/$collectionName"
        val vectorSearchSpec = VectorSearchSpec(
          collectionName = collectionName,
          vectorName = "compat-vector",
          topK = 10,
          scoreThreshold = None,
        )
        val embeddingSpec = EmbeddingSpec[Any](
          vectorName = vectorSearchSpec.vectorName,
          modelName = "compat-model",
          dimension = 3,
          distance = VectorDistance.Cosine,
          sourceTextFieldPaths = Nil,
        )
        val expectation = QdrantCollectionIdentity.compatibilityExpectation(embeddingSpec, vectorSearchSpec)
        val checker = new QdrantCollectionCompatibilityChecker(new QdrantClientCollectionInfoAdapter(qdrantClient))

        (for {
          _ <- qdrantClient.createCollection(
            collectionPath,
            QdrantJsonInterpreter.createCollectionJson(vectorSearchSpec, embeddingSpec),
          )
          matchingResult <- checker.check(expectation)
          dimensionMismatchResult <- checker.check(expectation.copy(expectedDimension = embeddingSpec.dimension + 1))
          _ <- ZIO.succeed {
            assert(matchingResult == Right(()))
            assert(dimensionMismatchResult == Left(List(
              QdrantCollectionCompatibilityMismatch.DimensionMismatch(embeddingSpec.dimension + 1, embeddingSpec.dimension)
            )))
          }
        } yield ()).ensuring(qdrantClient.deleteCollection(collectionPath).either.unit)
    }
  }
}
