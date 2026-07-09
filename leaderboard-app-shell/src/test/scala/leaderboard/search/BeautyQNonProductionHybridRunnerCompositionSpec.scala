package leaderboard.search

import io.circe.syntax.*
import io.circe.{Json, JsonObject}
import leaderboard.model.{MasterId, MasterLocationId, MasterServiceOfferId, MasterServiceOfferVariantId, QueryFailure, ServiceId}
import leaderboard.model.Category.CategoryId
import leaderboard.search.document.{VariantSearchDocument, VariantSearchDocumentSnapshotProvider}
import leaderboard.search.dsl.{SearchGeoPoint, VectorDistance, VectorSearchSpec}
import leaderboard.search.embedding.EmbeddingClient
import leaderboard.search.hybrid.{BeautyQNonProductionHybridExperimentRunner, BeautyQNonProductionHybridRunnerComposition}
import leaderboard.search.lexical.{LexicalDocumentBackend, LexicalDocumentHit}
import leaderboard.search.qdrant.{
  QdrantCollectionCompatibilityChecker,
  QdrantCollectionCompatibilityExpectation,
  QdrantCollectionCompatibilityGuard,
  QdrantCollectionInfoClient,
  QdrantCollectionReadinessConfig,
  QdrantNonProductionExperimentComposition,
  QdrantSearchClient,
  QdrantSearchHit,
  QdrantSemanticCandidateSearch,
  QdrantVariantDocumentUpsert,
}
import leaderboard.search.semantic.SemanticDocumentLookup
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe, ZIO}

import java.util.UUID

final class BeautyQNonProductionHybridRunnerCompositionSpec extends AnyWordSpec {

  "BeautyQNonProductionHybridRunnerComposition" should {

    "composition helper is side-effect-free" in {
      val runner: BeautyQNonProductionHybridExperimentRunner[IO] =
        BeautyQNonProductionHybridRunnerComposition.fromQdrantComposition(
          lexicalBackend = new FailIfCalledLexicalBackend,
          qdrantComposition = buildCompositionWith(
            semanticCandidateSearch = new QdrantSemanticCandidateSearch(
              new FailIfCalledEmbeddingClient,
              new FailIfCalledQdrantSearchClient,
            leaderboard.search.document.BeautyQVariantSearchDocumentContract.Fields.variantId,
            ),
          ),
          documentLookup = new FailIfCalledDocumentLookup,
        )

      assert(runner.isInstanceOf[BeautyQNonProductionHybridExperimentRunner[IO]])
    }

    "composed runner uses qdrant semantic backend" in {
      val expectedHitId = MasterServiceOfferVariantId(UUID.fromString("00000000-0000-0000-0000-000000000101"))
      val semanticDoc = variantDocumentWithId(1, expectedHitId)

      val composition = buildCompositionWith(
        semanticCandidateSearch = new QdrantSemanticCandidateSearch(
          new ExpectingEmbeddingClient(input.query),
          new ExpectingQdrantSearchClient(
            testReadinessConfig.vectorSearchSpec.collectionName,
            expectedHitId,
            0.92,
          ),
        leaderboard.search.document.BeautyQVariantSearchDocumentContract.Fields.variantId,
        ),
      )

      val runner = BeautyQNonProductionHybridRunnerComposition.fromQdrantComposition(
        lexicalBackend = new ScriptedLexicalBackend(Nil),
        qdrantComposition = composition,
        documentLookup = new ScriptedDocumentLookup(Map(expectedHitId -> semanticDoc)),
      )

      val result = run(runner.run(input, intent))

      assert(result.response.variantCarousel.map(_.variantId) == List(expectedHitId))
      assert(result.diagnostics.lexicalHitCount == 0)
      assert(result.diagnostics.semanticHitCount == 1)
      assert(result.diagnostics.distinctVariantIdCount == 1)
    }

    "composed runner preserves lexical + semantic overlap behavior" in {
      val lexicalOnlyId = MasterServiceOfferVariantId(UUID.fromString("00000000-0000-0000-0000-000000000201"))
      val overlapId = MasterServiceOfferVariantId(UUID.fromString("00000000-0000-0000-0000-000000000202"))
      val semanticOnlyId = MasterServiceOfferVariantId(UUID.fromString("00000000-0000-0000-0000-000000000203"))

      val docA = variantDocumentWithId(2, lexicalOnlyId)
      val docB = variantDocumentWithId(3, overlapId)
      val docC = variantDocumentWithId(4, semanticOnlyId)

      val composition = buildCompositionWith(
        semanticCandidateSearch = new QdrantSemanticCandidateSearch(
          new ConstEmbeddingClient(Vector(0.1, 0.2, 0.3)),
          new ScriptedQdrantSearchClient(
            List(
              searchHit(overlapId, 0.88),
              searchHit(semanticOnlyId, 0.76),
            ),
          ),
        leaderboard.search.document.BeautyQVariantSearchDocumentContract.Fields.variantId,
        ),
      )

      val runner = BeautyQNonProductionHybridRunnerComposition.fromQdrantComposition(
        lexicalBackend = new ScriptedLexicalBackend(
          List(
            LexicalDocumentHit(lexicalOnlyId, 5.0),
            LexicalDocumentHit(overlapId, 3.0),
          ),
        ),
        qdrantComposition = composition,
        documentLookup = new ScriptedDocumentLookup(Map(
          lexicalOnlyId -> docA,
          overlapId -> docB,
          semanticOnlyId -> docC,
        )),
      )

      val result = run(runner.run(input, intent))

      val variantIds = result.response.variantCarousel.map(_.variantId)
      assert(variantIds == List(lexicalOnlyId, overlapId, semanticOnlyId))
      assert(result.diagnostics.distinctVariantIdCount == 3)
    }

    "composed runner propagates missing lookup document failure" in {
      val missingId = MasterServiceOfferVariantId(UUID.fromString("00000000-0000-0000-0000-000000000303"))

      val composition = buildCompositionWith(
        semanticCandidateSearch = new QdrantSemanticCandidateSearch(
          new ConstEmbeddingClient(Vector(0.5)),
          new ExpectingQdrantSearchClient(
            testReadinessConfig.vectorSearchSpec.collectionName,
            missingId,
            0.75,
          ),
        leaderboard.search.document.BeautyQVariantSearchDocumentContract.Fields.variantId,
        ),
      )

      val runner = BeautyQNonProductionHybridRunnerComposition.fromQdrantComposition(
        lexicalBackend = new ScriptedLexicalBackend(Nil),
        qdrantComposition = composition,
        documentLookup = new ScriptedDocumentLookup(Map.empty),
      )

      val error = run(runner.run(input, intent).either).swap.getOrElse(fail("expected QueryFailure"))

      assert(error.message.contains("Missing VariantSearchDocument"))
      assert(error.message.contains(missingId.toString))
    }
  }

  // --- Composition builder ---

  private def buildCompositionWith(
    readinessConfig: QdrantCollectionReadinessConfig = testReadinessConfig,
    compatibilityGuard: QdrantCollectionCompatibilityGuard = compatibleGuard,
    snapshotProvider: VariantSearchDocumentSnapshotProvider[IO] = new FailIfCalledSnapshotProvider,
    documentUpsert: QdrantVariantDocumentUpsert = new FailIfCalledDocumentUpsert,
    semanticCandidateSearch: QdrantSemanticCandidateSearch,
  ): QdrantNonProductionExperimentComposition =
    QdrantNonProductionExperimentComposition.build(
      readinessConfig = readinessConfig,
      compatibilityGuard = compatibilityGuard,
      snapshotProvider = snapshotProvider,
      documentUpsert = documentUpsert,
      semanticCandidateSearch = semanticCandidateSearch,
    )

  // --- Fail-if-called collaborators ---

  private final class FailIfCalledSnapshotProvider extends VariantSearchDocumentSnapshotProvider[IO] {
    override def loadSnapshot(): IO[QueryFailure, List[VariantSearchDocument]] =
      ZIO.fail(QueryFailure.OperationFailure("fail-if-called", "loadSnapshot must not be called"))
  }

  private final class FailIfCalledDocumentUpsert extends QdrantVariantDocumentUpsert {
    override def upsertDocument(collectionName: String, document: VariantSearchDocument): IO[QueryFailure, Json] =
      ZIO.fail(QueryFailure.OperationFailure("fail-if-called", "upsertDocument must not be called"))
  }

  private final class FailIfCalledEmbeddingClient extends EmbeddingClient {
    override def embed(text: String): IO[QueryFailure, Vector[Double]] =
      ZIO.fail(QueryFailure.OperationFailure("fail-if-called", "embed must not be called"))
  }

  private final class FailIfCalledQdrantSearchClient extends QdrantSearchClient {
    override def search(path: String, json: Json): IO[QueryFailure, List[QdrantSearchHit]] =
      ZIO.fail(QueryFailure.OperationFailure("fail-if-called", "search must not be called"))
  }

  private final class FailIfCalledLexicalBackend extends LexicalDocumentBackend[IO, MasterServiceOfferVariantId] {
    override def documentHits(
      input: UserSearchInput,
      intent: ParsedSearchIntent,
    ): IO[QueryFailure, List[LexicalDocumentHit[MasterServiceOfferVariantId]]] =
      ZIO.fail(QueryFailure.domain("FailIfCalledLexicalBackend.documentHits was unexpectedly called"))
  }

  private final class FailIfCalledDocumentLookup extends SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument] {
    override def lookup(ids: List[MasterServiceOfferVariantId]): IO[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]] =
      ZIO.fail(QueryFailure.domain("FailIfCalledDocumentLookup.lookup was unexpectedly called"))
  }

  // --- Scripted / expecting collaborators ---

  private final class ScriptedLexicalBackend(
    result: List[LexicalDocumentHit[MasterServiceOfferVariantId]],
  ) extends LexicalDocumentBackend[IO, MasterServiceOfferVariantId] {
    override def documentHits(
      input: UserSearchInput,
      intent: ParsedSearchIntent,
    ): IO[QueryFailure, List[LexicalDocumentHit[MasterServiceOfferVariantId]]] =
      ZIO.succeed(result)
  }

  private final class ScriptedDocumentLookup(
    result: Map[MasterServiceOfferVariantId, VariantSearchDocument],
  ) extends SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument] {
    override def lookup(ids: List[MasterServiceOfferVariantId]): IO[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]] =
      ZIO.succeed(result)
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
    expectedVariantId: MasterServiceOfferVariantId,
    expectedScore: Double,
  ) extends QdrantSearchClient {
    override def search(path: String, json: Json): IO[QueryFailure, List[QdrantSearchHit]] =
      ZIO.fromEither(
        if (path == s"/collections/${expectedCollectionName}/points/search")
          Right(List(searchHit(expectedVariantId, expectedScore)))
        else Left(QueryFailure.OperationFailure("expecting-search",
          s"unexpected path: '$path', expected: /collections/${expectedCollectionName}/points/search"))
      )

    private def searchHit(variantId: MasterServiceOfferVariantId, score: Double): QdrantSearchHit =
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

  private final class ScriptedQdrantSearchClient(
    result: List[QdrantSearchHit],
  ) extends QdrantSearchClient {
    override def search(path: String, json: Json): IO[QueryFailure, List[QdrantSearchHit]] =
      ZIO.succeed(result)
  }

  private def searchHit(variantId: MasterServiceOfferVariantId, score: Double): QdrantSearchHit =
    QdrantSearchHit(
      id = variantId.toString,
      payload = JsonObject.fromMap(Map("variantId" -> Json.fromString(variantId.toString))),
      score = score,
    )

  // --- Helpers ---

  private def compatibleGuard: QdrantCollectionCompatibilityGuard =
    guard(new ConstQdrantCollectionInfoClient(Right(collectionInfoJson())))

  private def guard(client: QdrantCollectionInfoClient): QdrantCollectionCompatibilityGuard =
    new QdrantCollectionCompatibilityGuard(new QdrantCollectionCompatibilityChecker(client))

  private final class ConstQdrantCollectionInfoClient(result: Either[QueryFailure, Json]) extends QdrantCollectionInfoClient {
    override def collectionInfo(path: String): IO[QueryFailure, Json] =
      ZIO.fromEither(result)
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

  private def variantDocumentWithId(index: Int, hitVariantId: MasterServiceOfferVariantId): VariantSearchDocument =
    VariantSearchDocument(
      variantId = hitVariantId,
      masterServiceOfferId = MasterServiceOfferId(indexUuid(index + 100)),
      masterLocationId = MasterLocationId(indexUuid(index + 200)),
      masterId = MasterId(indexUuid(index + 1000)),
      serviceId = ServiceId(indexUuid(index + 400)),
      categoryId = CategoryId(indexUuid(index + 500)),
      serviceName = s"Service $index",
      categoryName = s"Category $index",
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

  private def indexUuid(value: Int): UUID =
    UUID.fromString(f"00000000-0000-0000-0000-$value%012d")

  private val input = UserSearchInput(query = "composition test", userLat = None, userLon = None, limit = 10)
  private val intent = ParsedSearchIntent(
    originalQuery = input.query,
    normalizedTokens = List("composition", "test"),
    explicitConstraints = Nil,
    softBoosts = Nil,
    remainingText = input.query,
  )

  private def run[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}


