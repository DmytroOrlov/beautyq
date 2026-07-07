package leaderboard.search

import distage.{DIKey, Mode}
import io.circe.Json
import izumi.distage.model.definition.Activation
import leaderboard.{LeaderboardTest, ProdTest}
import leaderboard.config.QdrantPortCfg
import leaderboard.search.dsl.{EmbeddingSpec, VectorDistance, VectorSearchSpec}
import leaderboard.search.qdrant.QdrantClient
import leaderboard.search.qdrant.QdrantJsonInterpreter
import java.util.UUID
import zio.ZIO

final class QdrantDockerSmokeSpec extends LeaderboardTest with ProdTest {
  override def config = {
    val base = super.config
    base.copy(
      activation = base.activation ++ Activation(Mode -> Mode.Test),
      memoizationRoots = base.memoizationRoots + DIKey[QdrantPortCfg],
    )
  }

  "Qdrant Docker smoke" should {
    "upsert and search a vector" in {
      (
        portCfg: QdrantPortCfg,
      ) =>
        val client = new QdrantClient(portCfg.host, portCfg.port)
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
          sourceTextFields = Nil,
        )
        val collectionName = s"smoke_${UUID.randomUUID().toString.replace('-', '_')}"
        val collectionPath = s"/collections/$collectionName"
        val vector = List(0.12, 0.34, 0.56)
        val pointId = UUID.randomUUID().toString
        val payloadId = s"payload_${UUID.randomUUID().toString.replace('-', '_')}"
        val collectionJson = QdrantJsonInterpreter.createCollectionJson(spec.copy(collectionName = collectionName), embeddingSpec)
        val upsertJson = QdrantJsonInterpreter.upsertPointJson(pointId, spec.vectorName, vector, Map("payloadId" -> Json.fromString(payloadId)))
        val searchJson = Json.obj(
          "vector" -> Json.obj(
            "name" -> Json.fromString("variant-embedding"),
            "vector" -> Json.arr(vector.map(Json.fromDoubleOrNull): _*),
          ),
          "limit" -> Json.fromInt(spec.topK),
          "with_payload" -> Json.True,
        )

        for {
          _ <- client.createCollection(collectionPath, collectionJson)
          _ <- client.upsertPoint(s"$collectionPath/points?wait=true", upsertJson)
          hits <- client.search(s"$collectionPath/points/search", searchJson)
          _ <- ZIO.succeed {
            assert(hits.nonEmpty, "expected at least one search hit")
            assert(hits.head.id == pointId, s"unexpected top hit id: ${hits.head.id}")
            assert(hits.head.payload.apply("payloadId").flatMap(_.asString).contains(payloadId), s"unexpected payload: ${hits.head.payload}")
          }
          _ <- client.deleteCollection(collectionPath)
        } yield ()
    }
  }
}
