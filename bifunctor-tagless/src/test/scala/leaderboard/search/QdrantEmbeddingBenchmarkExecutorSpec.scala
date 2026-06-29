package leaderboard.search

import io.circe.{Json, JsonObject}
import io.circe.syntax.*
import leaderboard.model.QueryFailure
import leaderboard.search.document.{InMemoryVariantSearchDocumentSnapshotProvider, VariantSearchDocument, VariantSearchDocumentSnapshotProvider}
import leaderboard.search.dsl.SearchGeoPoint
import leaderboard.search.embedding.EmbeddingClient
import leaderboard.search.eval.{
  BeautySearchEvalQuery,
  EvalCarouselWeights,
  EvalProviderExpectation,
  EvalScoring,
  EvalServiceExpectation,
  EvalVariantExpectation,
}
import leaderboard.search.qdrant.{
  QdrantCollectionCompatibilityChecker,
  QdrantCollectionCompatibilityGuard,
  QdrantCollectionInfoClient,
  QdrantCollectionReadinessConfig,
  QdrantEmbeddingBenchmarkCandidate,
  QdrantEmbeddingBenchmarkCollectionClient,
  QdrantEmbeddingBenchmarkCompositionFactory,
  QdrantEmbeddingBenchmarkEmbeddingClientFactory,
  QdrantEmbeddingBenchmarkExecutorConfig,
  QdrantEmbeddingBenchmarkQdrantCandidateExecutor,
  QdrantNonProductionExperimentComposition,
  QdrantSearchClient,
  QdrantSearchHit,
  QdrantSemanticCandidateSearch,
  QdrantVariantDocumentUpsert,
}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Ref, Runtime, Unsafe, ZIO}

import java.util.UUID

final class QdrantEmbeddingBenchmarkExecutorSpec extends AnyWordSpec {
  "QdrantEmbeddingBenchmarkQdrantCandidateExecutor" should {
    "derive different candidate collection names for different models and dimensions" in {
      val config = QdrantEmbeddingBenchmarkExecutorConfig(collectionRunId = "test-run")
      val small = candidate("small", "Qwen3-Embedding-0.6B", 1024)
      val large = candidate("large", "Qwen3-Embedding-4B", 2560)

      val smallConfig = readinessConfig(small, config)
      val largeConfig = readinessConfig(large, config)

      assert(smallConfig.collectionName != largeConfig.collectionName)
      assert(smallConfig.collectionName.contains("qwen3_embedding_0_6b"))
      assert(largeConfig.collectionName.contains("qwen3_embedding_4b"))
      assert(smallConfig.compatibilityExpectation.expectedDimension == 1024)
      assert(largeConfig.compatibilityExpectation.expectedDimension == 2560)
    }

    "create a collection, index the fixed snapshot through the guard, run queries, and cleanup" in {
      val firstDocument = variantDocument(1)
      val secondDocument = variantDocument(2)
      val documents = List(firstDocument, secondDocument)
      val collectionCallsRef = runUio(Ref.make(List.empty[String]))
      val deletesRef = runUio(Ref.make(List.empty[String]))
      val upsertCallsRef = runUio(Ref.make(List.empty[(String, UUID)]))
      val searchQueriesRef = runUio(Ref.make(List.empty[String]))
      val benchmarkCandidate = candidate("candidate-a", "Qwen3-Embedding-0.6B", 1024)
      val config = QdrantEmbeddingBenchmarkExecutorConfig(collectionRunId = "executor-spec", topK = 5)
      val expectedReadinessConfig = readinessConfig(benchmarkCandidate, config)
      val executor = new QdrantEmbeddingBenchmarkQdrantCandidateExecutor(
        snapshotProvider = new InMemoryVariantSearchDocumentSnapshotProvider[IO](documents),
        collectionClient = new RecordingCollectionClient(collectionCallsRef, deletesRef),
        embeddingClientFactory = new ConstEmbeddingClientFactory(new ConstEmbeddingClient(Vector(0.1, 0.2, 0.3))),
        compositionFactory = new FakeCompositionFactory(
          hits = List(
            QdrantSearchHit(
              id = "point-1",
              payload = JsonObject.fromMap(Map("variantId" -> Json.fromString(firstDocument.variantId.toString))),
              score = 0.92,
            ),
            QdrantSearchHit(
              id = "point-2",
              payload = JsonObject.fromMap(Map("variantId" -> Json.fromString(secondDocument.variantId.toString))),
              score = 0.81,
            ),
          ),
          upsertCallsRef = upsertCallsRef,
          searchQueriesRef = searchQueriesRef,
        ),
        config = config,
      )

      val result = run(executor.runCandidate(benchmarkCandidate, List(evalQuery("q_benchmark_001", "маникюр"))))

      assert(runUio(collectionCallsRef.get) == List(s"/collections/${expectedReadinessConfig.collectionName}"))
      assert(runUio(deletesRef.get) == List(s"/collections/${expectedReadinessConfig.collectionName}"))
      assert(runUio(upsertCallsRef.get) == documents.map(document => expectedReadinessConfig.collectionName -> document.variantId))
      assert(runUio(searchQueriesRef.get) == List("маникюр"))
      assert(result.map(_.candidateId) == List(benchmarkCandidate.candidateId))
      assert(result.map(_.queryId) == List("q_benchmark_001"))
      assert(result.head.topVariantIds == List(firstDocument.variantId, secondDocument.variantId))
      assert(result.head.topProviderIds == List(firstDocument.masterLocationId, secondDocument.masterLocationId))
      assert(result.head.topServiceIds == List(firstDocument.serviceId, secondDocument.serviceId))
      assert(result.head.scores == List(0.92, 0.81))
      assert(result.head.queryLatencyMs.nonEmpty)
    }
  }

  private def readinessConfig(
    candidate: QdrantEmbeddingBenchmarkCandidate,
    config: QdrantEmbeddingBenchmarkExecutorConfig,
  ): QdrantCollectionReadinessConfig = {
    val embeddingSpec = QdrantEmbeddingBenchmarkQdrantCandidateExecutor.embeddingSpec(candidate, config)
    QdrantEmbeddingBenchmarkQdrantCandidateExecutor.readinessConfig(candidate, config, embeddingSpec)
  }

  private final class RecordingCollectionClient(
    createsRef: Ref[List[String]],
    deletesRef: Ref[List[String]],
  ) extends QdrantEmbeddingBenchmarkCollectionClient {
    override def createCollection(path: String, json: Json): IO[QueryFailure, Json] =
      createsRef.update(_ :+ path).as(Json.obj())

    override def deleteCollection(path: String): IO[QueryFailure, Unit] =
      deletesRef.update(_ :+ path)
  }

  private final class ConstEmbeddingClientFactory(embeddingClient: EmbeddingClient) extends QdrantEmbeddingBenchmarkEmbeddingClientFactory {
    override def clientFor(candidate: QdrantEmbeddingBenchmarkCandidate): IO[QueryFailure, EmbeddingClient] =
      ZIO.succeed(embeddingClient)
  }

  private final class FakeCompositionFactory(
    hits: List[QdrantSearchHit],
    upsertCallsRef: Ref[List[(String, UUID)]],
    searchQueriesRef: Ref[List[String]],
  ) extends QdrantEmbeddingBenchmarkCompositionFactory {
    override def build(
      readinessConfig: QdrantCollectionReadinessConfig,
      embeddingClient: EmbeddingClient,
      snapshotProvider: VariantSearchDocumentSnapshotProvider[IO],
      embeddingSpec: leaderboard.search.dsl.EmbeddingSpec[VariantSearchDocument],
    ): IO[QueryFailure, QdrantNonProductionExperimentComposition] = {
      val guard = new QdrantCollectionCompatibilityGuard(
        new QdrantCollectionCompatibilityChecker(new MatchingCollectionInfoClient(readinessConfig))
      )
      val semanticSearch = new QdrantSemanticCandidateSearch(
        new RecordingEmbeddingClient(embeddingClient, searchQueriesRef),
        new ConstQdrantSearchClient(hits),
      leaderboard.search.document.BeautyQVariantSearchDocumentSchema.Fields.variantId,
      )
      ZIO.succeed(QdrantNonProductionExperimentComposition.build(
        readinessConfig = readinessConfig,
        compatibilityGuard = guard,
        snapshotProvider = snapshotProvider,
        documentUpsert = new RecordingDocumentUpsert(upsertCallsRef),
        semanticCandidateSearch = semanticSearch,
      ))
    }
  }

  private final class MatchingCollectionInfoClient(readinessConfig: QdrantCollectionReadinessConfig) extends QdrantCollectionInfoClient {
    override def collectionInfo(path: String): IO[QueryFailure, Json] =
      ZIO.succeed(Json.obj(
        "result" -> Json.obj(
          "name" -> readinessConfig.collectionName.asJson,
          "config" -> Json.obj(
            "params" -> Json.obj(
              "vectors" -> Json.obj(
                readinessConfig.compatibilityExpectation.vectorName -> Json.obj(
                  "size" -> readinessConfig.compatibilityExpectation.expectedDimension.asJson,
                  "distance" -> "Cosine".asJson,
                )
              )
            )
          ),
        )
      ))
  }

  private final class RecordingDocumentUpsert(callsRef: Ref[List[(String, UUID)]]) extends QdrantVariantDocumentUpsert {
    override def upsertDocument(collectionName: String, document: VariantSearchDocument): IO[QueryFailure, Json] =
      callsRef.update(_ :+ (collectionName -> document.variantId)).as(Json.obj())
  }

  private final class RecordingEmbeddingClient(delegate: EmbeddingClient, queriesRef: Ref[List[String]]) extends EmbeddingClient {
    override def embed(text: String): IO[QueryFailure, Vector[Double]] =
      queriesRef.update(_ :+ text) *> delegate.embed(text)
  }

  private final class ConstEmbeddingClient(vector: Vector[Double]) extends EmbeddingClient {
    override def embed(text: String): IO[QueryFailure, Vector[Double]] =
      ZIO.succeed(vector)
  }

  private final class ConstQdrantSearchClient(hits: List[QdrantSearchHit]) extends QdrantSearchClient {
    override def search(path: String, json: Json): IO[QueryFailure, List[QdrantSearchHit]] =
      ZIO.succeed(hits)
  }

  private def candidate(candidateId: String, modelName: String, dimension: Int): QdrantEmbeddingBenchmarkCandidate =
    QdrantEmbeddingBenchmarkCandidate(
      candidateId = candidateId,
      modelName = modelName,
      endpointLabel = "http://localhost:8081",
      vectorDimension = dimension,
    )

  private def evalQuery(id: String, query: String): BeautySearchEvalQuery =
    BeautySearchEvalQuery(
      id = id,
      query = query,
      language = "ru",
      queryTypes = Nil,
      expectedVariantCarousel = EvalVariantExpectation(),
      expectedProviderCarousel = EvalProviderExpectation(),
      expectedServiceIntentCarousel = EvalServiceExpectation(),
      scoring = EvalScoring(
        variantCarousel = EvalCarouselWeights(),
        providerCarousel = EvalCarouselWeights(),
        serviceIntentCarousel = EvalCarouselWeights(),
      ),
    )

  private def variantDocument(index: Int): VariantSearchDocument =
    VariantSearchDocument(
      variantId = uuid(index, 1),
      masterServiceOfferId = uuid(index, 2),
      masterLocationId = uuid(index, 3),
      masterId = uuid(index, 4),
      serviceId = uuid(index, 5),
      categoryId = uuid(index, 6),
      serviceName = s"Service $index",
      categoryName = "Category",
      masterName = s"Master $index",
      locationName = s"Location $index",
      address = s"Address $index",
      location = SearchGeoPoint(lat = BigDecimal("52.5200"), lon = BigDecimal("13.4050")),
      lat = BigDecimal("52.5200"),
      lon = BigDecimal("13.4050"),
      priceFrom = BigDecimal("25.00"),
      priceTo = BigDecimal("40.00"),
      durationMin = 45,
      enumAttributes = Map.empty,
      booleanAttributes = Map.empty,
      intAttributes = Map.empty,
      bigDecimalAttributes = Map.empty,
      allText = s"Service $index Category",
      serviceText = s"Service $index",
      attributeText = "",
      providerText = s"Master $index Location $index",
      locationText = s"Location $index Address $index",
    )

  private def uuid(index: Int, offset: Int): UUID =
    UUID.fromString(f"00000000-0000-0000-0000-${index * 100 + offset}%012d")

  private def run[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private def runUio[A](effect: UIO[A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private type UIO[A] = zio.UIO[A]
}
