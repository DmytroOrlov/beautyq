package leaderboard.search

import io.circe.syntax.*
import io.circe.{Json, JsonObject}
import leaderboard.model.QueryFailure
import leaderboard.search.document.{VariantSearchDocument, VariantSearchDocumentSnapshotProvider}
import leaderboard.search.dsl.{BeautySearchSpecV1, SearchGeoPoint, VectorDistance, VectorSearchSpec}
import leaderboard.search.embedding.EmbeddingClient
import leaderboard.search.parser.BeautySearchIntentParser
import leaderboard.search.qdrant.{
  QdrantCollectionCompatibilityChecker,
  QdrantCollectionCompatibilityExpectation,
  QdrantCollectionCompatibilityGuard,
  QdrantCollectionInfoClient,
  QdrantCollectionReadinessConfig,
  QdrantNonProductionExperimentComposition,
  QdrantNonProductionHybridExperiment,
  QdrantSearchClient,
  QdrantSearchHit,
  QdrantSemanticCandidateSearch,
  QdrantVariantDocumentUpsert,
}
import leaderboard.search.routing.{SearchBackendRoute, SearchBackendRouter, SearchRoutingMetadata, SearchRoutingSignal}
import leaderboard.search.semantic.VariantSearchDocumentLookup
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Ref, Runtime, Unsafe, ZIO}

import java.util.UUID

final class QdrantNonProductionHybridExperimentSpec extends AnyWordSpec {
  "QdrantNonProductionHybridExperiment" should {
    "build an experiment whose service uses composition.semanticBackend" in {
      val document = variantDocument(1)
      val qdrantPathRef = runUio(Ref.make(Option.empty[String]))
      val composition = buildComposition(
        semanticCandidateSearch = new QdrantSemanticCandidateSearch(
          new ConstEmbeddingClient(Vector(0.1, 0.2)),
          new RecordingQdrantSearchClient(qdrantPathRef, Right(List(searchHit(document.variantId, 0.91)))),
        leaderboard.search.document.BeautyQVariantSearchDocumentSchema.Fields.variantId,
        )
      )
      val experiment = buildExperiment(composition, document)

      val response = run(experiment.search(
        semanticInput,
        SearchRoutingMetadata(signal = Some(SearchRoutingSignal.BroadSemanticCandidate)),
      ))

      assert(response.variantCarousel.map(_.variantId) == List(document.variantId))
      assert(response.variantCarousel.map(_.score) == List(0.91))
      assert(runUio(qdrantPathRef.get).contains(s"/collections/${testReadinessConfig.collectionName}/points/search"))
    }

    "indexSnapshot delegates to composition.indexSnapshot and guarded snapshot indexing" in {
      val document = variantDocument(2)
      val snapshotCallsRef = runUio(Ref.make(0))
      val upsertCallsRef = runUio(Ref.make(List.empty[(String, UUID)]))
      val composition = buildComposition(
        snapshotProvider = new FakeSnapshotProvider(Right(List(document)), snapshotCallsRef),
        documentUpsert = new RecordingDocumentUpsert(upsertCallsRef),
      )
      val experiment = buildExperiment(composition, document)

      val result = run(experiment.indexSnapshot())

      assert(runUio(snapshotCallsRef.get) == 1)
      assert(runUio(upsertCallsRef.get) == List(testReadinessConfig.collectionName -> document.variantId))
      assert(result.totalDocumentsLoaded == 1)
      assert(result.totalDocumentsIndexed == 1)
      assert(result.indexedVariantIds == List(document.variantId))
    }

    "diagnose returns QdrantCandidateRoute diagnostics for explicit BroadSemanticCandidate metadata" in {
      val document = variantDocument(3)
      val experiment = buildExperiment(buildComposition(), document)

      val diagnostics = experiment.diagnose(
        semanticInput,
        SearchRoutingMetadata(signal = Some(SearchRoutingSignal.BroadSemanticCandidate)),
      )

      assert(diagnostics.route == SearchBackendRoute.QdrantCandidateRoute)
      assert(diagnostics.routingSignal.contains(SearchRoutingSignal.BroadSemanticCandidate))
      assert(!diagnostics.usesLexicalBackend)
      assert(diagnostics.usesSemanticBackend)
      assert(!diagnostics.fallbackRequested)
      assert(!diagnostics.fallbackImplemented)
    }

    "diagnose does not call lexical backend, semantic backend, or document lookup" in {
      val document = variantDocument(4)
      val qdrantPathRef = runUio(Ref.make(Option.empty[String]))
      val lookupCallsRef = runUio(Ref.make(0))
      val experiment = buildExperiment(
        composition = buildComposition(
          semanticCandidateSearch = new QdrantSemanticCandidateSearch(
            new ThrowingEmbeddingClient,
            new RecordingQdrantSearchClient(qdrantPathRef, Right(Nil)),
          leaderboard.search.document.BeautyQVariantSearchDocumentSchema.Fields.variantId,
          )
        ),
        lexicalBackend = new ThrowingBeautySearchBackend,
        documentLookup = new CountingVariantSearchDocumentLookup(List(document), lookupCallsRef),
      )

      val diagnostics = experiment.diagnose(
        semanticInput,
        SearchRoutingMetadata(signal = Some(SearchRoutingSignal.BroadSemanticCandidate)),
      )

      assert(diagnostics.route == SearchBackendRoute.QdrantCandidateRoute)
      assert(runUio(qdrantPathRef.get).isEmpty)
      assert(runUio(lookupCallsRef.get) == 0)
    }

    "search with explicit BroadSemanticCandidate metadata uses the semantic backend path" in {
      val document = variantDocument(5)
      val lexicalCallsRef = runUio(Ref.make(0))
      val lookupCallsRef = runUio(Ref.make(0))
      val qdrantPathRef = runUio(Ref.make(Option.empty[String]))
      val experiment = buildExperiment(
        composition = buildComposition(
          semanticCandidateSearch = new QdrantSemanticCandidateSearch(
            new ConstEmbeddingClient(Vector(0.3, 0.4)),
            new RecordingQdrantSearchClient(qdrantPathRef, Right(List(searchHit(document.variantId, 0.87)))),
          leaderboard.search.document.BeautyQVariantSearchDocumentSchema.Fields.variantId,
          )
        ),
        lexicalBackend = new CountingBeautySearchBackend(emptyResponse, lexicalCallsRef),
        documentLookup = new CountingVariantSearchDocumentLookup(List(document), lookupCallsRef),
      )

      val response = run(experiment.search(
        semanticInput,
        SearchRoutingMetadata(signal = Some(SearchRoutingSignal.BroadSemanticCandidate)),
      ))

      assert(runUio(lexicalCallsRef.get) == 0)
      assert(runUio(lookupCallsRef.get) == 1)
      assert(response.variantCarousel.map(_.variantId) == List(document.variantId))
      assert(runUio(qdrantPathRef.get).nonEmpty)
    }

    "semantic Qdrant search path uses composition readiness vectorSearchSpec collectionName" in {
      val document = variantDocument(6)
      val qdrantPathRef = runUio(Ref.make(Option.empty[String]))
      val readinessConfig = testReadinessConfig.copy(
        vectorSearchSpec = testReadinessConfig.vectorSearchSpec.copy(collectionName = "non_prod_hybrid_readiness_collection"),
        collectionName = "non_prod_hybrid_readiness_collection",
        compatibilityExpectation = expectation.copy(collectionName = "non_prod_hybrid_readiness_collection"),
      )
      val composition = buildComposition(
        readinessConfig = readinessConfig,
        compatibilityGuard = guard(new ConstQdrantCollectionInfoClient(Right(collectionInfoJson(
          observedCollectionName = "non_prod_hybrid_readiness_collection"
        )))),
        semanticCandidateSearch = new QdrantSemanticCandidateSearch(
          new ConstEmbeddingClient(Vector(0.5, 0.6)),
          new RecordingQdrantSearchClient(qdrantPathRef, Right(List(searchHit(document.variantId, 0.79)))),
        leaderboard.search.document.BeautyQVariantSearchDocumentSchema.Fields.variantId,
        ),
      )
      val experiment = buildExperiment(composition, document)

      run(experiment.search(
        semanticInput,
        SearchRoutingMetadata(signal = Some(SearchRoutingSignal.BroadSemanticCandidate)),
      ))

      assert(runUio(qdrantPathRef.get).contains("/collections/non_prod_hybrid_readiness_collection/points/search"))
    }

    "keep metadata explicit and do not route default metadata to Qdrant unless the router does so" in {
      val document = variantDocument(7)
      val lexicalCallsRef = runUio(Ref.make(0))
      val lookupCallsRef = runUio(Ref.make(0))
      val qdrantPathRef = runUio(Ref.make(Option.empty[String]))
      val lexicalResponse = emptyResponse.copy(facets = List(BeautySearchFacet("lexical-default", Nil)))
      val experiment = buildExperiment(
        composition = buildComposition(
          semanticCandidateSearch = new QdrantSemanticCandidateSearch(
            new ConstEmbeddingClient(Vector(0.7, 0.8)),
            new RecordingQdrantSearchClient(qdrantPathRef, Right(List(searchHit(document.variantId, 0.75)))),
          leaderboard.search.document.BeautyQVariantSearchDocumentSchema.Fields.variantId,
          )
        ),
        lexicalBackend = new CountingBeautySearchBackend(lexicalResponse, lexicalCallsRef),
        documentLookup = new CountingVariantSearchDocumentLookup(List(document), lookupCallsRef),
      )

      val response = run(experiment.search(semanticInput, SearchRoutingMetadata()))

      assert(response.facets.map(_.fieldPath) == List("lexical-default"))
      assert(runUio(lexicalCallsRef.get) == 1)
      assert(runUio(lookupCallsRef.get) == 0)
      assert(runUio(qdrantPathRef.get).isEmpty)
    }

    "use fake-only Qdrant and embedding clients" in {
      val document = variantDocument(8)
      val embeddingCallsRef = runUio(Ref.make(0))
      val qdrantPathRef = runUio(Ref.make(Option.empty[String]))
      val composition = buildComposition(
        semanticCandidateSearch = new QdrantSemanticCandidateSearch(
          new CountingEmbeddingClient(Vector(0.9, 1.0), embeddingCallsRef),
          new RecordingQdrantSearchClient(qdrantPathRef, Right(List(searchHit(document.variantId, 0.72)))),
        leaderboard.search.document.BeautyQVariantSearchDocumentSchema.Fields.variantId,
        )
      )
      val experiment = buildExperiment(composition, document)

      run(experiment.search(
        semanticInput,
        SearchRoutingMetadata(signal = Some(SearchRoutingSignal.BroadSemanticCandidate)),
      ))

      assert(runUio(embeddingCallsRef.get) == 1)
      assert(runUio(qdrantPathRef.get).contains(s"/collections/${testReadinessConfig.collectionName}/points/search"))
    }
  }

  private def buildExperiment(
    composition: QdrantNonProductionExperimentComposition,
    document: VariantSearchDocument,
  ): QdrantNonProductionHybridExperiment =
    buildExperiment(
      composition = composition,
      lexicalBackend = new ThrowingBeautySearchBackend,
      documentLookup = new CountingVariantSearchDocumentLookup(List(document), runUio(Ref.make(0))),
    )

  private def buildExperiment(
    composition: QdrantNonProductionExperimentComposition,
    lexicalBackend: BeautySearchBackend[IO],
    documentLookup: VariantSearchDocumentLookup[IO],
  ): QdrantNonProductionHybridExperiment =
    QdrantNonProductionHybridExperiment.build(
      composition = composition,
      parser = parser,
      spec = BeautySearchSpecV1.spec,
      lexicalBackend = lexicalBackend,
      router = SearchBackendRouter.default,
      documentLookup = documentLookup,
    )

  private def buildComposition(
    readinessConfig: QdrantCollectionReadinessConfig = testReadinessConfig,
    compatibilityGuard: QdrantCollectionCompatibilityGuard = compatibleGuard,
    snapshotProvider: VariantSearchDocumentSnapshotProvider[IO] = new FakeSnapshotProvider(Right(Nil), runUio(Ref.make(0))),
    documentUpsert: QdrantVariantDocumentUpsert = new RecordingDocumentUpsert(runUio(Ref.make(List.empty[(String, UUID)]))),
    semanticCandidateSearch: QdrantSemanticCandidateSearch = new QdrantSemanticCandidateSearch(
      new ConstEmbeddingClient(Vector(0.1)),
      new RecordingQdrantSearchClient(runUio(Ref.make(Option.empty[String])), Right(Nil)),
    leaderboard.search.document.BeautyQVariantSearchDocumentSchema.Fields.variantId,
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

  private final class CountingEmbeddingClient(vector: Vector[Double], callsRef: Ref[Int]) extends EmbeddingClient {
    override def embed(text: String): IO[QueryFailure, Vector[Double]] =
      callsRef.update(_ + 1).as(vector)
  }

  private final class ThrowingEmbeddingClient extends EmbeddingClient {
    override def embed(text: String): IO[QueryFailure, Vector[Double]] =
      ZIO.dieMessage(s"Embedding client must not be called for $text")
  }

  private final class RecordingQdrantSearchClient(
    pathRef: Ref[Option[String]],
    result: Either[QueryFailure, List[QdrantSearchHit]],
  ) extends QdrantSearchClient {
    override def search(path: String, json: Json): IO[QueryFailure, List[QdrantSearchHit]] =
      pathRef.set(Some(path)) *> ZIO.fromEither(result)
  }

  private final class CountingBeautySearchBackend(response: BeautySearchResponse, callsRef: Ref[Int]) extends BeautySearchBackend[IO] {
    override def search(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, BeautySearchResponse] =
      callsRef.update(_ + 1).as(response)
  }

  private final class ThrowingBeautySearchBackend extends BeautySearchBackend[IO] {
    override def search(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, BeautySearchResponse] =
      ZIO.dieMessage(s"Lexical backend must not be called for ${input.query}: $intent")
  }

  private final class CountingVariantSearchDocumentLookup(
    documents: List[VariantSearchDocument],
    callsRef: Ref[Int],
  ) extends VariantSearchDocumentLookup[IO] {
    private val documentsById = documents.iterator.map(document => document.variantId -> document).toMap

    override def lookup(variantIds: List[UUID]): IO[QueryFailure, Map[UUID, VariantSearchDocument]] =
      callsRef.update(_ + 1).as {
        variantIds.iterator.flatMap(variantId => documentsById.get(variantId).map(document => variantId -> document)).toMap
      }
  }

  private def compatibleGuard: QdrantCollectionCompatibilityGuard =
    guard(new ConstQdrantCollectionInfoClient(Right(collectionInfoJson())))

  private def guard(client: QdrantCollectionInfoClient): QdrantCollectionCompatibilityGuard =
    new QdrantCollectionCompatibilityGuard(new QdrantCollectionCompatibilityChecker(client))

  private final class ConstQdrantCollectionInfoClient(result: Either[QueryFailure, Json]) extends QdrantCollectionInfoClient {
    override def collectionInfo(path: String): IO[QueryFailure, Json] =
      ZIO.fromEither(result)
  }

  private val parser = new BeautySearchIntentParser(BeautySearchSpecV1.spec)

  private val semanticInput: UserSearchInput =
    UserSearchInput(query = "synthetic semantic boundary probe", userLat = None, userLon = None, limit = 10)

  private val expectation: QdrantCollectionCompatibilityExpectation =
    QdrantCollectionCompatibilityExpectation(
      collectionName = "beauty_variant_v1_non_production_hybrid_embedding_1024_cosine",
      vectorName = "variant-embedding",
      expectedDimension = 1024,
      expectedDistance = VectorDistance.Cosine,
      embeddingModelName = "fake-embedding",
    )

  private val testReadinessConfig: QdrantCollectionReadinessConfig = {
    val spec = VectorSearchSpec(
      collectionName = expectation.collectionName,
      vectorName = expectation.vectorName,
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

  private def searchHit(variantId: UUID, score: Double): QdrantSearchHit =
    QdrantSearchHit(
      id = variantId.toString,
      payload = JsonObject.fromMap(Map("variantId" -> Json.fromString(variantId.toString))),
      score = score,
    )

  private def emptyResponse: BeautySearchResponse =
    BeautySearchResponse(
      variantCarousel = Nil,
      providerCarousel = Nil,
      serviceIntentCarousel = Nil,
      facets = Nil,
      inferredFilters = Nil,
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

  private type UIO[A] = zio.UIO[A]
}
