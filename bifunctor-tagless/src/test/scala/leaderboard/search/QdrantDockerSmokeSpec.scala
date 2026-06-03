package leaderboard.search

import distage.{DIKey, Mode}
import izumi.distage.model.definition.Activation
import leaderboard.{LeaderboardTest, ProdTest}
import leaderboard.config.QdrantPortCfg
import leaderboard.search.dsl.{EmbeddingSpec, VectorDistance, VectorSearchSpec}
import leaderboard.search.qdrant.QdrantJsonInterpreter
import java.util.UUID

final class QdrantDockerSmokeSpec extends LeaderboardTest with ProdTest {
  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Mode -> Mode.Test),
    memoizationRoots = super.config.memoizationRoots + DIKey[QdrantPortCfg],
  )

  "Qdrant Docker smoke" should {
    "create and delete a collection" in {
      (
        portCfg: QdrantPortCfg,
      ) =>
        val client = new QdrantTestClient(portCfg.host, portCfg.port)
        val spec = VectorSearchSpec(
          collectionName = "placeholder",
          vectorName = "variant-embedding",
          topK = 3,
          scoreThreshold = None,
        )
        val embeddingSpec = EmbeddingSpec[Any](
          vectorName = "variant-embedding",
          modelName = "smoke-test",
          dimension = 3,
          distance = VectorDistance.Cosine,
          sourceTextFieldPaths = Nil,
        )
        val collectionName = s"smoke_${UUID.randomUUID().toString.replace('-', '_')}"
        val collectionJson = QdrantJsonInterpreter.createCollectionJson(spec.copy(collectionName = collectionName), embeddingSpec)
        for {
          _ <- client.createCollection(s"/collections/$collectionName", collectionJson)
          _ <- client.deleteCollection(s"/collections/$collectionName")
        } yield ()
    }
  }
}
