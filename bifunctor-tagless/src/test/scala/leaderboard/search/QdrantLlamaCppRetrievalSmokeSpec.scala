package leaderboard.search

import distage.{DIKey, Mode}
import io.circe.Json
import izumi.distage.model.definition.Activation
import leaderboard.{LeaderboardTest, ProdTest}
import leaderboard.config.QdrantPortCfg
import leaderboard.search.qdrant.QdrantJsonInterpreter
import leaderboard.search.qdrant.QdrantClient
import java.util.UUID
import zio.ZIO

final class QdrantLlamaCppRetrievalSmokeSpec extends LeaderboardTest with ProdTest {
  override def config = {
    val base = super.config
    base.copy(
      activation = base.activation ++ Activation(Mode -> Mode.Test),
      memoizationRoots = base.memoizationRoots + DIKey[QdrantPortCfg],
    )
  }

  "Qdrant + llama.cpp retrieval smoke" should {
    "retrieve the face-care doc for a face treatment query" in {
      (
        portCfg: QdrantPortCfg,
      ) =>
        val embeddingConfig = sys.env.get("LLAMA_CPP_EMBEDDING_URL")
          .map(LlamaCppEmbeddingTestConfig.withBaseUrl)
          .getOrElse(LlamaCppEmbeddingTestConfig.default)
        val qdrantClient = new QdrantClient(portCfg.host, portCfg.port)
        val embeddingClient = LlamaCppEmbeddingTestConfig.client(embeddingConfig)
        val probeResult = try {
          unsafeRun(embeddingClient.embed("qdrant llama retrieval smoke probe").either)
        } catch {
          case _: Exception => Left(leaderboard.model.QueryFailure.operation("llama-probe", "endpoint unavailable"))
        }
        probeResult match {
          case Right(vector) if vector.nonEmpty =>
            val collectionName = s"llama_cpp_retrieval_${UUID.randomUUID().toString.replace('-', '_')}"
            val collectionPath = s"/collections/$collectionName"
            val vectorName = "llama-cpp-embedding"
            val firstDoc = ("550e8400-e29b-41d4-a716-446655440001", "face-care", "facial care skin hydration anti aging")
            val remainingDocs = List(
              ("550e8400-e29b-41d4-a716-446655440002", "nails", "manicure gel polish nails"),
              ("550e8400-e29b-41d4-a716-446655440003", "lashes", "eyelash extension 2d lashes"),
            )

            unsafeRun(
              (
                for {
                firstVector <- embeddingClient.embed(firstDoc._3)
                spec = QdrantRetrievalSmokeSpec.collectionSpec(collectionName, vectorName)
                embeddingSpec = QdrantRetrievalSmokeSpec.embeddingSpec(firstVector.length)
                collectionJson <- ZIO.succeed(QdrantJsonInterpreter.createCollectionJson(spec, embeddingSpec))
                _ <- qdrantClient.createCollection(collectionPath, collectionJson)
                _ <- qdrantClient.upsertPoint(
                  s"$collectionPath/points?wait=true",
                  QdrantJsonInterpreter.upsertPointJson(
                    firstDoc._1,
                    vectorName,
                    firstVector.toList,
                    Map("id" -> Json.fromString(firstDoc._2)),
                  ),
                )
                _ <- ZIO.foreachDiscard(remainingDocs) { case (pointId, payloadId, text) =>
                  for {
                    vector <- embeddingClient.embed(text)
                    payload = Map("id" -> Json.fromString(payloadId))
                    pointJson = QdrantJsonInterpreter.upsertPointJson(pointId, vectorName, vector.toList, payload)
                    _ <- qdrantClient.upsertPoint(s"$collectionPath/points?wait=true", pointJson)
                  } yield ()
                }
                queryVector <- embeddingClient.embed("skin treatment for face")
                hits <- qdrantClient.search(
                  s"$collectionPath/points/search",
                  QdrantRetrievalSmokeSpec.searchJson(vectorName, queryVector.toList),
                )
                _ <- ZIO.succeed {
                  hits.headOption match {
                    case Some(hit) =>
                      assert(hit.payload.apply("id").flatMap(_.asString).contains("face-care"), s"unexpected top payload: ${hit.payload}")
                    case None =>
                      fail("expected at least one search hit")
                  }
                }
              } yield ()
              ).ensuring(qdrantClient.deleteCollection(collectionPath).either.unit)
            )
          case _ =>
            cancel(s"llama.cpp embedding endpoint ${embeddingConfig.baseUrl} is unavailable; canceling Qdrant+llama retrieval smoke")
        }
    }
  }

  private def unsafeRun[A](effect: zio.IO[leaderboard.model.QueryFailure, A]): A =
    zio.Unsafe.unsafe { implicit unsafe =>
      zio.Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}

private object QdrantRetrievalSmokeSpec {
  def embeddingSpec(dimension: Int): leaderboard.search.dsl.EmbeddingSpec[Any] =
    leaderboard.search.dsl.EmbeddingSpec[Any](
      vectorName = "llama-cpp-embedding",
      modelName = "local-llama-cpp-embedding",
      dimension = dimension,
      distance = leaderboard.search.dsl.VectorDistance.Cosine,
      sourceTextFieldPaths = Nil,
    )

  def collectionSpec(collectionName: String, vectorName: String): leaderboard.search.dsl.VectorSearchSpec =
    leaderboard.search.dsl.VectorSearchSpec(
      collectionName = collectionName,
      vectorName = vectorName,
      topK = 3,
      scoreThreshold = None,
    )

  def searchJson(vectorName: String, vector: List[Double]): Json =
    Json.obj(
      "vector" -> Json.obj(
        "name" -> Json.fromString(vectorName),
        "vector" -> Json.arr(vector.map(Json.fromDoubleOrNull): _*),
      ),
      "limit" -> Json.fromInt(3),
      "with_payload" -> Json.True,
    )
}
