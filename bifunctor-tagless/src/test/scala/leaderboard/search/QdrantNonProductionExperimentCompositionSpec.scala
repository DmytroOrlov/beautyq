package leaderboard.search

import io.circe.Json
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
import zio.{IO, Ref, Runtime, Unsafe, ZIO}

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
      val snapshotCallsRef = runUio(Ref.make(0))
      val upsertCallsRef = runUio(Ref.make(List.empty[(String, UUID)]))
      val composition = buildComposition(
        snapshotProvider = new FakeSnapshotProvider(Right(List(document)), snapshotCallsRef),
        documentUpsert = new RecordingDocumentUpsert(upsertCallsRef),
      )

      val result = run(composition.indexSnapshot())

      assert(runUio(snapshotCallsRef.get) == 1)
      assert(runUio(upsertCallsRef.get) == List(testReadinessConfig.collectionName -> document.variantId))
      assert(result.totalDocumentsLoaded == 1)
      assert(result.totalDocumentsIndexed == 1)
      assert(result.indexedVariantIds == List(document.variantId))
    }

    "guard failure prevents snapshot load and upserts" in {
      val snapshotCallsRef = runUio(Ref.make(0))
      val upsertCallsRef = runUio(Ref.make(List.empty[(String, UUID)]))
      val composition = buildComposition(
        compatibilityGuard = guard(new ConstQdrantCollectionInfoClient(Right(collectionInfoJson(dimension = 768)))),
        snapshotProvider = new FakeSnapshotProvider(Right(List(variantDocument(1))), snapshotCallsRef),
        documentUpsert = new RecordingDocumentUpsert(upsertCallsRef),
      )

      val error = runFail(composition.indexSnapshot())

      error match {
        case QueryFailure.OperationFailure("qdrant-collection-compatibility", message) =>
          assert(message.contains("DimensionMismatch(expected=1024, observed=768)"))
        case other =>
          fail(s"Expected qdrant-collection-compatibility failure, got $other")
      }
      assert(runUio(snapshotCallsRef.get) == 0)
      assert(runUio(upsertCallsRef.get).isEmpty)
    }

    "semantic backend uses readinessConfig vectorSearchSpec collectionName in Qdrant search path" in {
      val pathRef = runUio(Ref.make(Option.empty[String]))
      val composition = buildComposition(
        semanticCandidateSearch = new QdrantSemanticCandidateSearch(
          new ConstEmbeddingClient(Vector(0.1, 0.2, 0.3)),
          new RecordingQdrantSearchClient(pathRef),
        )
      )

      val result = run(composition.semanticBackend.candidates(
        UserSearchInput(query = "semantic query", userLat = None, userLon = None),
        ParsedSearchIntent("semantic query", List("semantic", "query"), Nil, Nil, "ignored residual"),
      ))

      assert(result.isEmpty)
      assert(runUio(pathRef.get).contains(s"/collections/${testReadinessConfig.vectorSearchSpec.collectionName}/points/search"))
      assert(testReadinessConfig.vectorSearchSpec.collectionName == testReadinessConfig.collectionName)
    }

    "build and fake paths do not require real Qdrant or llama calls" in {
      val snapshotCallsRef = runUio(Ref.make(0))
      val upsertCallsRef = runUio(Ref.make(List.empty[(String, UUID)]))
      val searchPathRef = runUio(Ref.make(Option.empty[String]))
      val composition = buildComposition(
        snapshotProvider = new FakeSnapshotProvider(Right(Nil), snapshotCallsRef),
        documentUpsert = new RecordingDocumentUpsert(upsertCallsRef),
        semanticCandidateSearch = new QdrantSemanticCandidateSearch(
          new ConstEmbeddingClient(Vector(0.1)),
          new RecordingQdrantSearchClient(searchPathRef),
        ),
      )

      val indexingResult = run(composition.indexSnapshot())
      val searchResult = run(composition.semanticBackend.candidates(
        UserSearchInput(query = "fake-only", userLat = None, userLon = None),
        ParsedSearchIntent("fake-only", List("fake-only"), Nil, Nil, "fake-only"),
      ))

      assert(indexingResult.totalDocumentsLoaded == 0)
      assert(searchResult.isEmpty)
      assert(runUio(snapshotCallsRef.get) == 1)
      assert(runUio(upsertCallsRef.get).isEmpty)
      assert(runUio(searchPathRef.get).contains(s"/collections/${testReadinessConfig.collectionName}/points/search"))
    }
  }

  private def buildComposition(
    readinessConfig: QdrantCollectionReadinessConfig = testReadinessConfig,
    compatibilityGuard: QdrantCollectionCompatibilityGuard = compatibleGuard,
    snapshotProvider: VariantSearchDocumentSnapshotProvider[IO] = new FakeSnapshotProvider(Right(Nil), runUio(Ref.make(0))),
    documentUpsert: QdrantVariantDocumentUpsert = new RecordingDocumentUpsert(runUio(Ref.make(List.empty[(String, UUID)]))),
    semanticCandidateSearch: QdrantSemanticCandidateSearch = new QdrantSemanticCandidateSearch(
      new ConstEmbeddingClient(Vector(0.1)),
      new RecordingQdrantSearchClient(runUio(Ref.make(Option.empty[String]))),
    ),
  ): QdrantNonProductionExperimentComposition =
    QdrantNonProductionExperimentComposition.build(
      readinessConfig = readinessConfig,
      compatibilityGuard = compatibilityGuard,
      snapshotProvider = snapshotProvider,
      documentUpsert = documentUpsert,
      semanticCandidateSearch = semanticCandidateSearch,
    )

  private final class FakeSnapshotProvider(
    result: Either[QueryFailure, List[VariantSearchDocument]],
    callsRef: Ref[Int],
  ) extends VariantSearchDocumentSnapshotProvider[IO] {
    override def loadSnapshot(): IO[QueryFailure, List[VariantSearchDocument]] =
      callsRef.update(_ + 1) *> ZIO.fromEither(result)
  }

  private final class RecordingDocumentUpsert(callsRef: Ref[List[(String, UUID)]]) extends QdrantVariantDocumentUpsert {
    override def upsertDocument(collectionName: String, document: VariantSearchDocument): IO[QueryFailure, Json] =
      callsRef.update(_ :+ (collectionName -> document.variantId)).as(Json.obj())
  }

  private final class ConstEmbeddingClient(vector: Vector[Double]) extends EmbeddingClient {
    override def embed(text: String): IO[QueryFailure, Vector[Double]] =
      ZIO.succeed(vector)
  }

  private final class RecordingQdrantSearchClient(pathRef: Ref[Option[String]]) extends QdrantSearchClient {
    override def search(path: String, json: Json): IO[QueryFailure, List[QdrantSearchHit]] =
      pathRef.set(Some(path)).as(Nil)
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

  private def runUio[A](effect: UIO[A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private def runFail[A](effect: IO[QueryFailure, A]): QueryFailure =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect.either).getOrThrowFiberFailure().swap.getOrElse(sys.error("expected failure"))
    }

  private type UIO[A] = zio.UIO[A]
}
