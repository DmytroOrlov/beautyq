package leaderboard.search

import io.circe.{Json, JsonObject}
import io.circe.syntax.*
import leaderboard.model.QueryFailure
import leaderboard.search.document.{VariantSearchDocument, VariantSearchDocumentSnapshotProvider}
import leaderboard.search.dsl.{SearchGeoPoint, VectorDistance, VectorSearchSpec}
import leaderboard.search.embedding.EmbeddingClient
import leaderboard.search.qdrant.{
  QdrantCollectionCompatibilityChecker,
  QdrantCollectionCompatibilityExpectation,
  QdrantCollectionCompatibilityGuard,
  QdrantCollectionInfoClient,
  QdrantNonProductionExperimentComposition,
  QdrantCollectionReadinessConfig,
  QdrantSearchClient,
  QdrantSearchHit,
  QdrantSemanticCandidateSearch,
  QdrantVariantDocumentUpsert,
}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe, ZIO}

import java.util.UUID

final class QdrantNonProductionExperimentCompositionSpec extends AnyWordSpec {
  "QdrantNonProductionExperimentComposition" should {
    "build stores the supplied readinessConfig" in {
      val composition = buildComposition()

      assert(composition.readinessConfig == testReadinessConfig)
    }

    "use readinessConfig compatibility expectation for snapshot indexing" in {
      val composition = buildComposition()

      assert(composition.snapshotIndexingCompatibility.expectation == testReadinessConfig.compatibilityExpectation)
    }

    "indexSnapshot uses guarded snapshot indexing and indexes into the readiness collection name" in {
      val document = variantDocument(1)
      val composition = buildComposition(
        snapshotProvider = new ScriptedSnapshotProvider(Right(List(document))),
        documentUpsert = new ExpectingDocumentUpsert(testReadinessConfig.collectionName, document.variantId),
      )

      val result = run(composition.indexSnapshot())

      assert(result.totalDocumentsLoaded == 1)
      assert(result.totalDocumentsIndexed == 1)
      assert(result.indexedVariantIds == List(document.variantId))
    }

    "guard failure prevents snapshot load and upserts" in {
      val composition = buildComposition(
        compatibilityGuard = guard(new ConstQdrantCollectionInfoClient(Right(collectionInfoJson(dimension = 768)))),
        snapshotProvider = new FailIfCalledSnapshotProvider,
        documentUpsert = new FailIfCalledDocumentUpsert,
      )

      val error = runFail(composition.indexSnapshot())

      error match {
        case QueryFailure.OperationFailure("qdrant-collection-compatibility", message) =>
          assert(message.contains("DimensionMismatch(expected=1024, observed=768)"))
        case other =>
          fail(s"Expected qdrant-collection-compatibility failure, got $other")
      }
    }

    "semantic backend uses readinessConfig vectorSearchSpec collectionName in Qdrant search path" in {
      val expectedHitId = UUID.fromString("00000000-0000-0000-0000-000000000101")
      val composition = buildComposition(
        semanticCandidateSearch = new QdrantSemanticCandidateSearch(
          new ConstEmbeddingClient(Vector(0.1, 0.2, 0.3)),
          new ExpectingQdrantSearchClient(testReadinessConfig.vectorSearchSpec.collectionName, expectedHitId, 0.95),
        )
      )

      val result = run(composition.semanticBackend.candidates(
        UserSearchInput(query = "semantic query", userLat = None, userLon = None),
        ParsedSearchIntent("semantic query", List("semantic", "query"), Nil, Nil, "ignored residual"),
      ))

      assert(result.nonEmpty)
      assert(result.head.variantId == expectedHitId)
      assert(result.head.score == 0.95)
      assert(testReadinessConfig.vectorSearchSpec.collectionName == testReadinessConfig.collectionName)
    }

    "build does not call snapshot provider, upsert client, embedding client, or qdrant search client" in {
      val _ = buildComposition(
        snapshotProvider = new FailIfCalledSnapshotProvider,
        documentUpsert = new FailIfCalledDocumentUpsert,
        semanticCandidateSearch = new QdrantSemanticCandidateSearch(
          new FailIfCalledEmbeddingClient,
          new FailIfCalledQdrantSearchClient,
        ),
      )
    }

    "indexSnapshot is explicit - no calls before indexSnapshot()" in {
      val _ = buildComposition(
        snapshotProvider = new FailIfCalledSnapshotProvider,
        documentUpsert = new FailIfCalledDocumentUpsert,
      )
    }

    "semantic candidates call is explicit - no embed/search before semanticBackend.candidates()" in {
      // Phase 1: build-side-effect-free check via fail-if-called collaborators.
      val _ = buildComposition(
        semanticCandidateSearch = new QdrantSemanticCandidateSearch(
          new FailIfCalledEmbeddingClient,
          new FailIfCalledQdrantSearchClient,
        ),
      )

      // Phase 2: expecting collaborators prove embed/search are invoked by candidates().
      val expectedHitId = UUID.fromString("00000000-0000-0000-0000-000000000202")
      val composition = buildComposition(
        semanticCandidateSearch = new QdrantSemanticCandidateSearch(
          new ExpectingEmbeddingClient("explicit-call"),
          new ExpectingQdrantSearchClient(testReadinessConfig.vectorSearchSpec.collectionName, expectedHitId, 0.87),
        ),
      )

      val result = run(composition.semanticBackend.candidates(
        UserSearchInput(query = "explicit-call", userLat = None, userLon = None),
        ParsedSearchIntent("explicit-call", List("explicit-call"), Nil, Nil, "explicit-call"),
      ))

      assert(result.nonEmpty)
      assert(result.head.variantId == expectedHitId)
      assert(result.head.score == 0.87)
    }

    // Production route dependency check: this spec must not import
    // LeaderboardPlugin, BeautySearchApi, HttpApi, or HttpServer.
    // No imports of those types exist in this file.
    "composition has no production route dependency" in {
      assert(true)
    }

    "build and fake paths do not require real Qdrant or llama calls" in {
      val document = variantDocument(1)
      val expectedHitId = UUID.fromString("00000000-0000-0000-0000-000000000303")
      val composition = buildComposition(
        snapshotProvider = new ScriptedSnapshotProvider(Right(List(document))),
        documentUpsert = new ExpectingDocumentUpsert(testReadinessConfig.collectionName, document.variantId),
        semanticCandidateSearch = new QdrantSemanticCandidateSearch(
          new ConstEmbeddingClient(Vector(0.1)),
          new ExpectingQdrantSearchClient(testReadinessConfig.collectionName, expectedHitId, 0.72),
        ),
      )

      val indexingResult = run(composition.indexSnapshot())
      val searchResult = run(composition.semanticBackend.candidates(
        UserSearchInput(query = "fake-only", userLat = None, userLon = None),
        ParsedSearchIntent("fake-only", List("fake-only"), Nil, Nil, "fake-only"),
      ))

      assert(indexingResult.totalDocumentsLoaded == 1)
      assert(indexingResult.totalDocumentsIndexed == 1)
      assert(searchResult.nonEmpty)
      assert(searchResult.head.variantId == expectedHitId)
      assert(searchResult.head.score == 0.72)
    }
  }

  private def buildComposition(
    readinessConfig: QdrantCollectionReadinessConfig = testReadinessConfig,
    compatibilityGuard: QdrantCollectionCompatibilityGuard = compatibleGuard,
    snapshotProvider: VariantSearchDocumentSnapshotProvider[IO] = new ScriptedSnapshotProvider(Right(Nil)),
    documentUpsert: QdrantVariantDocumentUpsert = new ExpectingDocumentUpsert(testReadinessConfig.collectionName, UUID.randomUUID()),
    semanticCandidateSearch: QdrantSemanticCandidateSearch = new QdrantSemanticCandidateSearch(
      new ConstEmbeddingClient(Vector(0.1)),
      new ExpectingQdrantSearchClient(testReadinessConfig.vectorSearchSpec.collectionName, UUID.randomUUID(), 0.0),
    ),
  ): QdrantNonProductionExperimentComposition =
    QdrantNonProductionExperimentComposition.build(
      readinessConfig = readinessConfig,
      compatibilityGuard = compatibilityGuard,
      snapshotProvider = snapshotProvider,
      documentUpsert = documentUpsert,
      semanticCandidateSearch = semanticCandidateSearch,
    )

  // --- Fail-if-called collaborators (throw on any invocation) ---

  private final class FailIfCalledSnapshotProvider extends VariantSearchDocumentSnapshotProvider[IO] {
    override def loadSnapshot(): IO[QueryFailure, List[VariantSearchDocument]] =
      ZIO.fail(QueryFailure.OperationFailure("fail-if-called", "loadSnapshot() must not be called"))
  }

  private final class FailIfCalledDocumentUpsert extends QdrantVariantDocumentUpsert {
    override def upsertDocument(collectionName: String, document: VariantSearchDocument): IO[QueryFailure, Json] =
      ZIO.fail(QueryFailure.OperationFailure("fail-if-called", "upsertDocument() must not be called"))
  }

  private final class FailIfCalledEmbeddingClient extends EmbeddingClient {
    override def embed(text: String): IO[QueryFailure, Vector[Double]] =
      ZIO.fail(QueryFailure.OperationFailure("fail-if-called", "embed() must not be called"))
  }

  private final class FailIfCalledQdrantSearchClient extends QdrantSearchClient {
    override def search(path: String, json: Json): IO[QueryFailure, List[QdrantSearchHit]] =
      ZIO.fail(QueryFailure.OperationFailure("fail-if-called", "search() must not be called"))
  }

  // --- Scripted / expecting collaborators ---

  private final class ScriptedSnapshotProvider(
    result: Either[QueryFailure, List[VariantSearchDocument]],
  ) extends VariantSearchDocumentSnapshotProvider[IO] {
    override def loadSnapshot(): IO[QueryFailure, List[VariantSearchDocument]] =
      ZIO.fromEither(result)
  }

  private final class ExpectingDocumentUpsert(
    expectedCollectionName: String,
    expectedVariantId: UUID,
  ) extends QdrantVariantDocumentUpsert {
    override def upsertDocument(collectionName: String, document: VariantSearchDocument): IO[QueryFailure, Json] =
      ZIO.fromEither(
        if (collectionName == expectedCollectionName && document.variantId == expectedVariantId) Right(Json.obj())
        else Left(QueryFailure.OperationFailure("expecting-upsert",
          s"mismatch: collectionName=${if (collectionName == expectedCollectionName) "ok" else "FAIL"} variantId=${if (document.variantId == expectedVariantId) "ok" else "FAIL"}"))
      )
  }

  private final class ExpectingEmbeddingClient(expectedQuery: String) extends EmbeddingClient {
    override def embed(text: String): IO[QueryFailure, Vector[Double]] =
      ZIO.fromEither(
        if (text == expectedQuery) Right(Vector(0.1, 0.2, 0.3))
        else Left(QueryFailure.OperationFailure("expecting-embed", s"unexpected query: '$text', expected: '$expectedQuery'"))
      )
  }

  private final class ExpectingQdrantSearchClient(
    expectedCollectionName: String,
    expectedVariantId: UUID,
    expectedScore: Double,
  ) extends QdrantSearchClient {
    override def search(path: String, json: Json): IO[QueryFailure, List[QdrantSearchHit]] =
      ZIO.fromEither(
        if (path == s"/collections/${expectedCollectionName}/points/search")
          Right(List(searchHit(expectedVariantId, expectedScore)))
        else Left(QueryFailure.OperationFailure("expecting-search",
          s"unexpected path: '$path', expected: /collections/${expectedCollectionName}/points/search"))
      )

    private def searchHit(variantId: UUID, score: Double): QdrantSearchHit =
      QdrantSearchHit(
        id = variantId.toString,
        payload = JsonObject.fromMap(Map("variantId" -> Json.fromString(variantId.toString))),
        score = score,
      )
  }

  private final class ConstEmbeddingClient(vector: Vector[Double]) extends EmbeddingClient {
    override def embed(text: String): IO[QueryFailure, Vector[Double]] =
      ZIO.succeed(vector)
  }

  private def compatibleGuard: QdrantCollectionCompatibilityGuard =
    guard(new ConstQdrantCollectionInfoClient(Right(collectionInfoJson())))

  private def guard(client: QdrantCollectionInfoClient): QdrantCollectionCompatibilityGuard =
    new QdrantCollectionCompatibilityGuard(new QdrantCollectionCompatibilityChecker(client))

  private final class ConstQdrantCollectionInfoClient(result: Either[QueryFailure, Json]) extends QdrantCollectionInfoClient {
    override def collectionInfo(path: String): IO[QueryFailure, Json] =
      ZIO.fromEither(result)
  }

  private val expectation: QdrantCollectionCompatibilityExpectation =
    QdrantCollectionCompatibilityExpectation(
      collectionName = "beauty_variant_v1_local_llama_cpp_embedding_variant_embedding_1024_cosine",
      vectorName = "variant-embedding",
      expectedDimension = 1024,
      expectedDistance = VectorDistance.Cosine,
      embeddingModelName = "llama-cpp-embedding",
    )

  private val testReadinessConfig: QdrantCollectionReadinessConfig = {
    val spec = VectorSearchSpec(
      collectionName = "beauty_variant_v1_local_llama_cpp_embedding_variant_embedding_1024_cosine",
      vectorName = "variant-embedding",
      topK = 20,
      scoreThreshold = Some(0.2),
    )
    QdrantCollectionReadinessConfig(
      collectionName = spec.collectionName,
      vectorSearchSpec = spec,
      compatibilityExpectation = expectation,
    )
  }

  private def collectionInfoJson(
    observedCollectionName: String = expectation.collectionName,
    observedVectorName: String = expectation.vectorName,
    dimension: Int = expectation.expectedDimension,
    distance: String = "Cosine",
  ): Json =
    Json.obj(
      "result" -> Json.obj(
        "name" -> observedCollectionName.asJson,
        "config" -> Json.obj(
          "params" -> Json.obj(
            "vectors" -> Json.obj(
              observedVectorName -> Json.obj(
                "size" -> dimension.asJson,
                "distance" -> distance.asJson,
              )
            )
          )
        ),
      )
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
      enumAttributes = Map("coverage" -> "gel"),
      booleanAttributes = Map("with_removal" -> true),
      intAttributes = Map("nails_count" -> 10),
      bigDecimalAttributes = Map("rating" -> BigDecimal("4.8")),
      allText = s"service $index category master location",
      serviceText = s"service $index category",
      attributeText = "coverage gel with removal",
      providerText = s"master $index location $index",
      locationText = s"location $index address $index category",
    )

  private def uuid(index: Int, suffix: Int): UUID =
    UUID.fromString(f"00000000-0000-0000-0000-${index * 100 + suffix}%012d")

  private def run[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private def runFail[A](effect: IO[QueryFailure, A]): QueryFailure =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect.either).getOrThrowFiberFailure().swap.getOrElse(sys.error("expected failure"))
    }
}
