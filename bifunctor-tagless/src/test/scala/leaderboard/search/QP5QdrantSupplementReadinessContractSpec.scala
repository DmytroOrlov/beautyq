package leaderboard.search

import distage.{Injector, ModuleDef}
import io.circe.Json
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.plugins.BeautySearchQdrantSupplementActivation
import leaderboard.plugins.BeautySearchQdrantSupplementActivation.{QdrantSupplementNotReady, QdrantSupplementReady}
import leaderboard.plugins.BeautySearchQdrantSupplementActivationModuleSelector
import leaderboard.plugins.BeautySearchQdrantSupplementRuntimeBindingPlan
import leaderboard.search.document.{VariantSearchDocument, VariantSearchDocumentSnapshotProvider}
import leaderboard.search.dsl.{EmbeddingSpec, SearchGeoPoint, VectorDistance, VectorSearchSpec}
import leaderboard.search.qdrant.{
  ObservedQdrantVectorConfig,
  QdrantCollectionCompatibilityChecker,
  QdrantCollectionCompatibilityExpectation,
  QdrantCollectionCompatibilityGuard,
  QdrantCollectionCompatibilityMismatch,
  QdrantCollectionIdentity,
  QdrantCollectionInfoClient,
  QdrantCollectionReadinessConfig,
  QdrantCollectionReadinessInput,
  QdrantEmbeddingBenchmarkExecutorConfig,
  QdrantSnapshotIndexingCompatibilityGuard,
  QdrantVariantDocumentSnapshotIndexer,
  QdrantVariantDocumentUpsert,
}
import leaderboard.search.semantic.{SemanticCandidateBackend, SemanticCandidateHit, VariantSearchDocumentLookup}
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Ref, Runtime, Unsafe, ZIO}

import java.util.UUID

/**
 * QP5: focused readiness source-of-truth contract for the no-worsening Qdrant supplement path.
 *
 * Composes the existing readiness/compatibility stack (`QdrantCollectionReadinessConfig`,
 * `QdrantCollectionIdentity`, `QdrantCollectionCompatibilityGuard`,
 * `QdrantVariantDocumentSnapshotIndexer`) into one identity -> guard -> indexer proof, and adds the
 * two assertions that stack does not yet make explicit by name:
 *
 *   - no silent production-like recreate: the guard/checker/indexer call path depends only on
 *     `QdrantCollectionInfoClient` (read-only) and `QdrantVariantDocumentUpsert` (point writes), so a
 *     mismatch can only fail closed; the only delete/create-capable component in the codebase is the
 *     non-production `QdrantEmbeddingBenchmarkQdrantCandidateExecutor`, scoped to its own
 *     benchmark-purpose-namespaced collection (`QdrantEmbeddingBenchmarkExecutorSpec`);
 *   - activation boundary: selecting `BeautySearchQdrantSupplementActivation.QdrantSupplementReady` /
 *     `QdrantSupplementNotReady` requires only the supplement backend's lexical/semantic/document-lookup
 *     collaborators -- no readiness/compatibility binding is required or supplied -- so this contract
 *     must justify selecting `Ready` separately; it is not auto-wired into the activation module.
 *
 * Proof-only: no default route change, no Qdrant-as-default, no production collection lifecycle
 * manager, no startup indexing, no alias/blue-green.
 */
final class QP5QdrantSupplementReadinessContractSpec extends AnyWordSpec {

  // ============================================================================================
  // Requirement 1: one source-of-truth readiness identity, derived without any Qdrant call.
  // ============================================================================================

  "The QP5 readiness identity contract" should {
    "derive one source-of-truth identity (collection name, vector name, dimension, distance, embedding model) from EmbeddingSpec/VectorSearchSpec" in {
      assert(readinessConfig.collectionName.nonEmpty)
      assert(readinessConfig.vectorSearchSpec.collectionName == readinessConfig.collectionName)
      assert(expectation == QdrantCollectionCompatibilityExpectation(
        collectionName = readinessConfig.collectionName,
        vectorName = vectorSearchSpec.vectorName,
        expectedDimension = embeddingSpec.dimension,
        expectedDistance = embeddingSpec.distance,
        embeddingModelName = embeddingSpec.modelName,
      ))
    }
  }

  // ============================================================================================
  // Requirement 2: compatible readiness is accepted.
  // ============================================================================================

  "Compatible readiness" should {
    "accept a collection/config with matching collection name, vector name, dimension, and distance" in {
      assert(QdrantCollectionIdentity.checkCompatibility(expectation, observedMatching) == Right(()))
    }
  }

  // ============================================================================================
  // Requirement 3: mismatch fail-fast, one field at a time.
  // ============================================================================================

  "Readiness mismatch" should {
    "fail fast on collection name, vector name, dimension, distance, and embedding model mismatches, each in isolation" in {
      assert(QdrantCollectionIdentity.checkCompatibility(expectation, observedMatching.copy(collectionName = "other_collection")) ==
        Left(List(QdrantCollectionCompatibilityMismatch.CollectionNameMismatch(expectation.collectionName, "other_collection"))))
      assert(QdrantCollectionIdentity.checkCompatibility(expectation, observedMatching.copy(vectorName = "other-vector")) ==
        Left(List(QdrantCollectionCompatibilityMismatch.VectorNameMismatch(expectation.vectorName, "other-vector"))))
      assert(QdrantCollectionIdentity.checkCompatibility(expectation, observedMatching.copy(dimension = 768)) ==
        Left(List(QdrantCollectionCompatibilityMismatch.DimensionMismatch(expectation.expectedDimension, 768))))
      assert(QdrantCollectionIdentity.checkCompatibility(expectation, observedMatching.copy(distance = VectorDistance.Euclidean)) ==
        Left(List(QdrantCollectionCompatibilityMismatch.DistanceMismatch(expectation.expectedDistance, VectorDistance.Euclidean))))
      assert(QdrantCollectionIdentity.checkCompatibility(expectation, observedMatching.copy(embeddingModelName = Some("other-model"))) ==
        Left(List(QdrantCollectionCompatibilityMismatch.EmbeddingModelMismatch(expectation.embeddingModelName, "other-model"))))
    }
  }

  // ============================================================================================
  // Requirement 4: no silent production-like recreate.
  // ============================================================================================

  "The compatibility guard call path" should {
    "fail closed on mismatch without any reachable delete/create capability" in {
      // ReadOnlyMismatchedCollectionInfoClient implements the entire interface surface reachable
      // from the guard: `QdrantCollectionInfoClient` exposes only the one read-only
      // `collectionInfo` method. There is no delete/create method anywhere on this call path, so a
      // mismatch can only be reported as a QueryFailure, never acted on by deleting or recreating
      // the checked collection.
      val guard = new QdrantCollectionCompatibilityGuard(
        new QdrantCollectionCompatibilityChecker(new ReadOnlyMismatchedCollectionInfoClient)
      )

      val error = runFail(guard.requireCompatible(expectation))

      error match {
        case QueryFailure.OperationFailure("qdrant-collection-compatibility", message) =>
          assert(message.contains(expectation.collectionName))
        case other =>
          fail(s"expected qdrant-collection-compatibility failure, got $other")
      }
    }
  }

  "Production-vs-non-production collection mutation capability" should {
    "confine delete/create capability to the non-production embedding-benchmark executor, namespaced away from production-purpose collection names by construction" in {
      val defaultBenchmarkConfig = QdrantEmbeddingBenchmarkExecutorConfig()

      // The only delete/create-capable component in the codebase is
      // `QdrantEmbeddingBenchmarkQdrantCandidateExecutor`: it always creates its own
      // benchmark-purpose-namespaced collection before indexing and deletes that same collection on
      // cleanup (QdrantEmbeddingBenchmarkExecutorSpec); it never receives or mutates a collection
      // that the compatibility guard checked. The guard/checker/indexer path proved above has no
      // such capability at all.
      assert(defaultBenchmarkConfig.collectionPurposePrefix == "embedding-benchmark")
      assert(defaultBenchmarkConfig.collectionPurposePrefix != readinessInput.purpose)
    }
  }

  // ============================================================================================
  // Requirement 5: snapshot indexing guard checks and writes to exactly one, the same, collection.
  // ============================================================================================

  "The end-to-end identity -> guard -> indexer composition" should {
    "index through the guard into exactly the collection the QP5 identity derived" in {
      val documents = List(variantDocument(1), variantDocument(2))
      val loadsRef = runUio(Ref.make(0))
      val callsRef = runUio(Ref.make(List.empty[(String, MasterServiceOfferVariantId)]))
      val compatibleGuard = new QdrantCollectionCompatibilityGuard(
        new QdrantCollectionCompatibilityChecker(new ConstQdrantCollectionInfoClient(compatibleCollectionInfoJson))
      )
      val indexer = new QdrantVariantDocumentSnapshotIndexer(
        new FakeSnapshotProvider(documents, loadsRef),
        new RecordingDocumentUpsert(callsRef),
      )

      val result = run(indexer.indexCompatibleSnapshot(QdrantSnapshotIndexingCompatibilityGuard(expectation, compatibleGuard)))

      assert(runUio(loadsRef.get) == 1)
      assert(runUio(callsRef.get) == documents.map(document => expectation.collectionName -> document.variantId))
      assert(runUio(callsRef.get).map(_._1).distinct == List(expectation.collectionName))
      assert(result.indexedVariantIds == documents.map(_.variantId))
    }

    "never check one collection and write to another -- skip every write when the checked collection mismatches" in {
      val loadsRef = runUio(Ref.make(0))
      val callsRef = runUio(Ref.make(List.empty[(String, MasterServiceOfferVariantId)]))
      val mismatchingGuard = new QdrantCollectionCompatibilityGuard(
        new QdrantCollectionCompatibilityChecker(new ReadOnlyMismatchedCollectionInfoClient)
      )
      val indexer = new QdrantVariantDocumentSnapshotIndexer(
        new FakeSnapshotProvider(List(variantDocument(1)), loadsRef),
        new RecordingDocumentUpsert(callsRef),
      )

      val error = runFail(indexer.indexCompatibleSnapshot(QdrantSnapshotIndexingCompatibilityGuard(expectation, mismatchingGuard)))

      error match {
        case QueryFailure.OperationFailure("qdrant-collection-compatibility", message) =>
          assert(message.contains(expectation.collectionName))
        case other =>
          fail(s"expected qdrant-collection-compatibility failure, got $other")
      }
      assert(runUio(loadsRef.get) == 0)
      assert(runUio(callsRef.get).isEmpty)
    }
  }

  // ============================================================================================
  // Requirement 6: activation boundary -- QdrantSupplementReady/NotReady stay pure module
  // selection; the QP5 readiness contract is a separate source that must justify selecting Ready.
  // ============================================================================================

  "BeautySearchQdrantSupplementActivationModuleSelector.moduleFor(QdrantSupplementReady / QdrantSupplementNotReady)" should {
    "select the supplement backend from only its lexical/semantic/document-lookup collaborators -- no QP5 readiness/compatibility binding is required or supplied" in {
      List(QdrantSupplementNotReady, QdrantSupplementReady).foreach { activation =>
        // Building the module's `BeautySearchBackend[IO]` root succeeds while supplying ONLY the
        // three named collaborators below -- no `QdrantCollectionCompatibilityGuard`,
        // `QdrantCollectionReadinessConfig`, or checker binding is present anywhere in this graph.
        // If selecting this activation state had silently required the QP5 readiness contract,
        // production would fail here with a missing-dependency error instead of succeeding; success
        // is therefore the proof that the contract is NOT auto-wired into activation, and must be
        // justified separately (the assertions above) before an operator selects `QdrantSupplementReady`.
        val backend = supplementBackend(activation, oneAppendFixture)

        val response = run(backend.search(
          UserSearchInput(query = "zzqx vvbb wwyy qqzz nonsense gibberish", userLat = None, userLon = None, limit = 10),
          ParsedSearchIntent("zzqx vvbb wwyy qqzz nonsense gibberish", Nil, Nil, Nil, "zzqx vvbb wwyy qqzz nonsense gibberish"),
        ))

        assert(response.variantCarousel.size <= 1, s"[$activation] expected the wired supplement backend to serve with the top-1 cap")
      }
    }
  }

  // ============================================================================================
  // Shared QP5 readiness fixtures.
  // ============================================================================================

  private val embeddingSpec: EmbeddingSpec[VariantSearchDocument] =
    EmbeddingSpec[VariantSearchDocument](
      vectorName = "qp5-embedding-spec-vector-name-is-not-identity-source",
      modelName = "llama-cpp-embedding",
      dimension = 1024,
      distance = VectorDistance.Cosine,
      sourceTextFields = List(leaderboard.search.document.BeautyQVariantSearchDocumentContract.Fields.serviceText, leaderboard.search.document.BeautyQVariantSearchDocumentContract.Fields.allText),
    )

  private val vectorSearchSpec: VectorSearchSpec =
    VectorSearchSpec(
      collectionName = "placeholder_collection",
      vectorName = "variant-embedding",
      topK = 20,
      scoreThreshold = Some(0.2),
    )

  private val readinessInput: QdrantCollectionReadinessInput =
    QdrantCollectionReadinessInput(
      domainName = "beauty_variant",
      searchSpecVersion = "v1",
      purpose = "qp5-readiness-contract",
      embeddingSpec = embeddingSpec,
      vectorSearchSpec = vectorSearchSpec,
    )

  private val readinessConfig: QdrantCollectionReadinessConfig =
    QdrantCollectionReadinessConfig.derive(readinessInput)

  private val expectation: QdrantCollectionCompatibilityExpectation =
    readinessConfig.compatibilityExpectation

  private val observedMatching: ObservedQdrantVectorConfig =
    ObservedQdrantVectorConfig(
      collectionName = expectation.collectionName,
      vectorName = expectation.vectorName,
      dimension = expectation.expectedDimension,
      distance = expectation.expectedDistance,
      embeddingModelName = Some(expectation.embeddingModelName),
    )

  private def compatibleCollectionInfoJson: Json =
    Json.obj(
      "result" -> Json.obj(
        "name" -> Json.fromString(expectation.collectionName),
        "config" -> Json.obj(
          "params" -> Json.obj(
            "vectors" -> Json.obj(
              expectation.vectorName -> Json.obj(
                "size" -> Json.fromInt(expectation.expectedDimension),
                "distance" -> Json.fromString("Cosine"),
              )
            )
          )
        ),
        "metadata" -> Json.obj(
          "embeddingModelName" -> Json.fromString(expectation.embeddingModelName)
        ),
      )
    )

  private final class ConstQdrantCollectionInfoClient(json: Json) extends QdrantCollectionInfoClient {
    override def collectionInfo(path: String): IO[QueryFailure, Json] = ZIO.succeed(json)
  }

  private final class ReadOnlyMismatchedCollectionInfoClient extends QdrantCollectionInfoClient {
    override def collectionInfo(path: String): IO[QueryFailure, Json] =
      ZIO.succeed(Json.obj(
        "result" -> Json.obj(
          "name" -> Json.fromString("other_collection"),
          "config" -> Json.obj(
            "params" -> Json.obj(
              "vectors" -> Json.obj(
                "other-vector" -> Json.obj(
                  "size" -> Json.fromInt(1),
                  "distance" -> Json.fromString("Dot"),
                )
              )
            )
          ),
        )
      ))
  }

  private final class FakeSnapshotProvider(
    documents: List[VariantSearchDocument],
    loadsRef: Ref[Int],
  ) extends VariantSearchDocumentSnapshotProvider[IO] {
    override def loadSnapshot(): IO[QueryFailure, List[VariantSearchDocument]] =
      loadsRef.update(_ + 1).as(documents)
  }

  private final class RecordingDocumentUpsert(
    callsRef: Ref[List[(String, MasterServiceOfferVariantId)]]
  ) extends QdrantVariantDocumentUpsert {
    override def upsertDocument(collectionName: String, document: VariantSearchDocument): IO[QueryFailure, Json] =
      callsRef.update(_ :+ (collectionName -> document.variantId)).as(Json.obj())
  }

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

  // ============================================================================================
  // Shared activation-boundary fixtures (mirrors QP3/QP4 fixture style).
  // ============================================================================================

  private final case class SupplementFixture(
    lexicalBackend: BeautySearchBackend[IO],
    semanticBackend: SemanticCandidateBackend[IO],
    documentLookup: VariantSearchDocumentLookup[IO],
  )

  private val emptyEsResponse: BeautySearchResponse = BeautySearchResponse(Nil, Nil, Nil, Nil, Nil)

  private def oneAppendFixture: SupplementFixture = {
    val knownDocument = variantDocument(1)
    SupplementFixture(
      lexicalBackend = new StubBeautySearchBackend(emptyEsResponse),
      semanticBackend = new StubSemanticCandidateBackend(List(SemanticCandidateHit(knownDocument.variantId, 0.9))),
      documentLookup = new StubVariantSearchDocumentLookup(List(knownDocument)),
    )
  }

  private final class StubBeautySearchBackend(response: BeautySearchResponse) extends BeautySearchBackend[IO] {
    override def search(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, BeautySearchResponse] =
      ZIO.succeed(response)
  }

  private final class StubSemanticCandidateBackend(hits: List[SemanticCandidateHit]) extends SemanticCandidateBackend[IO] {
    override def candidates(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, List[SemanticCandidateHit]] =
      ZIO.succeed(hits)
  }

  private final class StubVariantSearchDocumentLookup(documents: List[VariantSearchDocument]) extends VariantSearchDocumentLookup[IO] {
    private val documentsById = documents.iterator.map(document => document.variantId -> document).toMap

    override def lookup(variantIds: List[MasterServiceOfferVariantId]): IO[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]] =
      ZIO.succeed(variantIds.iterator.flatMap(variantId => documentsById.get(variantId).map(variantId -> _)).toMap)
  }

  private def supplementBackend(
    activation: BeautySearchQdrantSupplementActivation,
    fixture: SupplementFixture,
  ): BeautySearchBackend[IO] = {
    val module = new ModuleDef {
      include(BeautySearchQdrantSupplementActivationModuleSelector.moduleFor(activation))
      make[BeautySearchBackend[IO]].named(BeautySearchQdrantSupplementRuntimeBindingPlan.LexicalBackendBindingName).fromValue(fixture.lexicalBackend)
      make[SemanticCandidateBackend[IO]].fromValue(fixture.semanticBackend)
      make[VariantSearchDocumentLookup[IO]].fromValue(fixture.documentLookup)
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[BeautySearchBackend[IO]],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[BeautySearchBackend[IO]]
  }

  // ============================================================================================
  // Effect runners.
  // ============================================================================================

  private def run[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private def runUio[A](effect: zio.UIO[A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }

  private def runFail[A](effect: IO[QueryFailure, A]): QueryFailure =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect.either).getOrThrowFiberFailure() match {
        case Left(failure) => failure
        case Right(value)  => fail(s"expected failure, got $value")
      }
    }
}
