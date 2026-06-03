package leaderboard.search

import distage.{DIKey, Mode}
import io.circe.Json
import izumi.distage.model.definition.Activation
import leaderboard.{LeaderboardTest, ProdTest}
import leaderboard.config.QdrantPortCfg
import leaderboard.search.qdrant.QdrantJsonInterpreter
import java.util.UUID
import zio.ZIO

final class QdrantLlamaCppRetrievalSmokeSpec extends LeaderboardTest with ProdTest {
  override def config = super.config.copy(
    activation = super.config.activation ++ Activation(Mode -> Mode.Test),
    memoizationRoots = super.config.memoizationRoots + DIKey[QdrantPortCfg],
  )

  "Qdrant + llama.cpp retrieval smoke" should {
    "retrieve the face-care doc for a face treatment query" in {
      (
        portCfg: QdrantPortCfg,
      ) =>
        sys.env.get("LLAMA_CPP_EMBEDDING_URL") match {
          case None =>
            cancel("Set LLAMA_CPP_EMBEDDING_URL=http://localhost:8081 to run the llama.cpp retrieval smoke test")
          case Some(url) if url != "http://localhost:8081" =>
            cancel("Set LLAMA_CPP_EMBEDDING_URL=http://localhost:8081 to run the llama.cpp retrieval smoke test")
          case Some(url) =>
            val qdrantClient = new QdrantClient(portCfg.host, portCfg.port)
            val embeddingClient = new LlamaCppEmbeddingClient(LlamaCppEmbeddingClientConfig(baseUrl = url))
            val collectionName = s"llama_cpp_retrieval_${UUID.randomUUID().toString.replace('-', '_')}"
            val collectionPath = s"/collections/$collectionName"
            val vectorName = "llama-cpp-embedding"
            val docs = List(
              "face-care" -> "facial care skin hydration anti aging",
              "nails" -> "manicure gel polish nails",
              "lashes" -> "eyelash extension 2d lashes",
            )

            unsafeRun(
              (
                for {
                firstVector <- embeddingClient.embed(docs.head._2)
                spec = QdrantRetrievalSmokeSpec.collectionSpec(collectionName, vectorName)
                embeddingSpec = QdrantRetrievalSmokeSpec.embeddingSpec(firstVector.length)
                collectionJson <- ZIO.succeed(QdrantJsonInterpreter.createCollectionJson(spec, embeddingSpec))
                _ <- qdrantClient.createCollection(collectionPath, collectionJson)
                _ <- qdrantClient.upsertPoint(
                  s"$collectionPath/points?wait=true",
                  QdrantJsonInterpreter.upsertPointJson(
                    docs.head._1,
                    vectorName,
                    firstVector.toList,
                    Map("id" -> Json.fromString(docs.head._1)),
                  ),
                )
                _ <- ZIO.foreachDiscard(docs.tail) { case (id, text) =>
                  for {
                    vector <- embeddingClient.embed(text)
                    payload = Map("id" -> Json.fromString(id))
                    pointJson = QdrantJsonInterpreter.upsertPointJson(id, vectorName, vector.toList, payload)
                    _ <- qdrantClient.upsertPoint(s"$collectionPath/points?wait=true", pointJson)
                  } yield ()
                }
                queryVector <- embeddingClient.embed("skin treatment for face")
                hits <- qdrantClient.search(
                  s"$collectionPath/points/search",
                  QdrantRetrievalSmokeSpec.searchJson(vectorName, queryVector.toList),
                )
                _ <- ZIO.succeed {
                  assert(hits.nonEmpty, "expected at least one search hit")
                  assert(hits.head.payload.apply("id").flatMap(_.asString).contains("face-care"), s"unexpected top payload: ${hits.head.payload}")
                }
              } yield ()
              ).ensuring(qdrantClient.deleteCollection(collectionPath).either.unit)
            )
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
