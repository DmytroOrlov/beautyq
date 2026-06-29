package leaderboard.search

import io.circe.syntax.*
import io.circe.{Json, JsonObject}
import distage.{Injector, ModuleDef}
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.document.{VariantSearchDocument, VariantSearchDocumentSnapshotProvider}
import leaderboard.search.dsl.{EmbeddingSpec, SearchDocumentSpec, SearchGeoPoint, SearchField, SearchFieldKind, SearchValue, VectorDistance, VectorSearchSpec}
import leaderboard.search.embedding.EmbeddingClient
import leaderboard.search.hybrid.{
  BeautyQNonProductionHybridRunnerManualAdapterInputs,
  BeautyQNonProductionHybridRunnerManualHandle,
}
import leaderboard.search.lexical.{LexicalDocumentBackend, LexicalDocumentHit}
import leaderboard.search.qdrant.{
  QdrantCollectionCompatibilityExpectation,
  QdrantCollectionInfoClient,
  QdrantCollectionReadinessConfig,
  QdrantPointUpsertClient,
  QdrantSearchClient,
  QdrantSearchHit,
}
import leaderboard.search.semantic.SemanticDocumentLookup
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe, ZIO}

import java.util.UUID

final class BeautyQNonProductionHybridRunnerManualAdapterInputsModuleSpec extends AnyWordSpec {

  "BeautyQNonProductionHybridRunnerManualAdapterInputs Distage module" should {

    "module materialization is side-effect-free" in {
      val probe = buildConstructionProbe

      assert(probe.inputs.isInstanceOf[BeautyQNonProductionHybridRunnerManualAdapterInputs])
      assert(probe.handle.isInstanceOf[BeautyQNonProductionHybridRunnerManualHandle])
    }

    "materialized handle indexSnapshot is explicit" in {
      val expectedVariantId = UUID.fromString("00000000-0000-0000-0000-000000000101")
      val doc = moduleTestDocumentWithId(expectedVariantId)

      val probe = buildIndexSnapshotProbe(doc)

      val result = runIO(probe.handle.indexSnapshot())

      assert(result.indexedVariantIds == List(expectedVariantId))
    }

    "materialized handle run is explicit and does not index" in {
      val expectedVariantId = UUID.fromString("00000000-0000-0000-0000-000000000101")
      val semanticDoc = moduleTestDocumentWithId(expectedVariantId)

      val probe = buildRunProbe(semanticDoc)

      val result = runIO(probe.handle.run(input, intent))

      assert(result.response.variantCarousel.map(_.variantId) == List(expectedVariantId))
      assert(result.diagnostics.lexicalHitCount == 0)
      assert(result.diagnostics.semanticHitCount == 1)
      assert(result.diagnostics.distinctVariantIdCount == 1)
    }

    "materialized handle run propagates missing lookup document failure" in {
      val missingId = UUID.fromString("00000000-0000-0000-0000-000000000303")

      val probe = buildMissingLookupProbe(missingId)

      val result = runIO(probe.handle.run(input, intent).either)

      assert(result.isLeft)
      val error = result.swap.getOrElse(fail("expected QueryFailure"))
      assert(error.message.contains("Missing VariantSearchDocument"))
      assert(error.message.contains(missingId.toString))
    }
  }

  // --- Construction probe (side-effect-free) ---

  private def buildConstructionProbe: AdapterInputsProbe = {
    val module = new ModuleDef {
      make[LexicalDocumentBackend[IO, MasterServiceOfferVariantId]].from {
        new FailIfCalledLexicalBackend
      }

      make[QdrantCollectionReadinessConfig].from { () => testReadinessConfig }

      make[QdrantCollectionInfoClient].from {
        new FailIfCalledCollectionInfoClient
      }

      make[VariantSearchDocumentSnapshotProvider[IO]].from {
        new FailIfCalledSnapshotProvider
      }

      make[QdrantPointUpsertClient].from {
        new FailIfCalledUpsertClient
      }

      make[EmbeddingClient].from {
        new FailIfCalledEmbeddingClient
      }

      make[QdrantSearchClient].from {
        new FailIfCalledQdrantSearchClient
      }

      make[SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument]].from {
        new FailIfCalledDocumentLookup
      }

      make[SearchDocumentSpec[VariantSearchDocument]].from { () => testDocumentSpec }

      make[EmbeddingSpec[VariantSearchDocument]].from { () => testEmbeddingSpec }

      make[BeautyQNonProductionHybridRunnerManualAdapterInputs].from {
        (
          lexicalBackend: LexicalDocumentBackend[IO, MasterServiceOfferVariantId],
          readinessConfig: QdrantCollectionReadinessConfig,
          collectionInfoClient: QdrantCollectionInfoClient,
          snapshotProvider: VariantSearchDocumentSnapshotProvider[IO],
          pointUpsertClient: QdrantPointUpsertClient,
          embeddingClient: EmbeddingClient,
          qdrantSearchClient: QdrantSearchClient,
          documentLookup: SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument],
          documentSpec: SearchDocumentSpec[VariantSearchDocument],
          embeddingSpec: EmbeddingSpec[VariantSearchDocument],
        ) =>
          BeautyQNonProductionHybridRunnerManualAdapterInputs(
            lexicalBackend = lexicalBackend,
            readinessConfig = readinessConfig,
            collectionInfoClient = collectionInfoClient,
            snapshotProvider = snapshotProvider,
            pointUpsertClient = pointUpsertClient,
            embeddingClient = embeddingClient,
            qdrantSearchClient = qdrantSearchClient,
            documentLookup = documentLookup,
            documentSpec = documentSpec,
            embeddingSpec = embeddingSpec,
          )
      }

      make[BeautyQNonProductionHybridRunnerManualHandle].from {
        (inputs: BeautyQNonProductionHybridRunnerManualAdapterInputs) =>
          inputs.buildHandle()
      }

      make[AdapterInputsProbe].from {
        (
          inputs: BeautyQNonProductionHybridRunnerManualAdapterInputs,
          handle: BeautyQNonProductionHybridRunnerManualHandle,
        ) =>
          AdapterInputsProbe(inputs, handle)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[AdapterInputsProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[AdapterInputsProbe]
  }

  // --- indexSnapshot probe ---

  private def buildIndexSnapshotProbe(doc: VariantSearchDocument): AdapterInputsProbe = {
    val module = new ModuleDef {
      make[LexicalDocumentBackend[IO, MasterServiceOfferVariantId]].from {
        new FailIfCalledLexicalBackend
      }

      make[QdrantCollectionReadinessConfig].from { () => testReadinessConfig }

      make[QdrantCollectionInfoClient].from {
        new ConstCollectionInfoClient(Right(collectionInfoJson()))
      }

      make[VariantSearchDocumentSnapshotProvider[IO]].from {
        new ScriptedSnapshotProvider(List(doc))
      }

      make[QdrantPointUpsertClient].from {
        new ExpectingUpsertClient(
          testReadinessConfig.collectionName,
          doc.variantId,
        )
      }

      make[EmbeddingClient].from {
        new ExpectingEmbeddingClient(doc.allText)
      }

      make[QdrantSearchClient].from {
        new FailIfCalledQdrantSearchClient
      }

      make[SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument]].from {
        new FailIfCalledDocumentLookup
      }

      make[SearchDocumentSpec[VariantSearchDocument]].from { () => testDocumentSpec }

      make[EmbeddingSpec[VariantSearchDocument]].from { () => testEmbeddingSpec }

      make[BeautyQNonProductionHybridRunnerManualAdapterInputs].from {
        (
          lexicalBackend: LexicalDocumentBackend[IO, MasterServiceOfferVariantId],
          readinessConfig: QdrantCollectionReadinessConfig,
          collectionInfoClient: QdrantCollectionInfoClient,
          snapshotProvider: VariantSearchDocumentSnapshotProvider[IO],
          pointUpsertClient: QdrantPointUpsertClient,
          embeddingClient: EmbeddingClient,
          qdrantSearchClient: QdrantSearchClient,
          documentLookup: SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument],
          documentSpec: SearchDocumentSpec[VariantSearchDocument],
          embeddingSpec: EmbeddingSpec[VariantSearchDocument],
        ) =>
          BeautyQNonProductionHybridRunnerManualAdapterInputs(
            lexicalBackend = lexicalBackend,
            readinessConfig = readinessConfig,
            collectionInfoClient = collectionInfoClient,
            snapshotProvider = snapshotProvider,
            pointUpsertClient = pointUpsertClient,
            embeddingClient = embeddingClient,
            qdrantSearchClient = qdrantSearchClient,
            documentLookup = documentLookup,
            documentSpec = documentSpec,
            embeddingSpec = embeddingSpec,
          )
      }

      make[BeautyQNonProductionHybridRunnerManualHandle].from {
        (inputs: BeautyQNonProductionHybridRunnerManualAdapterInputs) =>
          inputs.buildHandle()
      }

      make[AdapterInputsProbe].from {
        (
          inputs: BeautyQNonProductionHybridRunnerManualAdapterInputs,
          handle: BeautyQNonProductionHybridRunnerManualHandle,
        ) =>
          AdapterInputsProbe(inputs, handle)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[AdapterInputsProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[AdapterInputsProbe]
  }

  // --- run probe (explicit, no implicit indexing) ---

  private def buildRunProbe(semanticDoc: VariantSearchDocument): AdapterInputsProbe = {
    val module = new ModuleDef {
      make[LexicalDocumentBackend[IO, MasterServiceOfferVariantId]].from {
        new ScriptedLexicalBackend(Nil)
      }

      make[QdrantCollectionReadinessConfig].from { () => testReadinessConfig }

      make[QdrantCollectionInfoClient].from {
        new FailIfCalledCollectionInfoClient
      }

      make[VariantSearchDocumentSnapshotProvider[IO]].from {
        new FailIfCalledSnapshotProvider
      }

      make[QdrantPointUpsertClient].from {
        new FailIfCalledUpsertClient
      }

      make[EmbeddingClient].from {
        new ExpectingEmbeddingClient(input.query)
      }

      make[QdrantSearchClient].from {
        new ExpectingQdrantSearchClient(
          testReadinessConfig.vectorSearchSpec.collectionName,
          semanticDoc.variantId,
          0.92,
        )
      }

      make[SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument]].from {
        new ScriptedDocumentLookup(Map(semanticDoc.variantId -> semanticDoc))
      }

      make[SearchDocumentSpec[VariantSearchDocument]].from { () => testDocumentSpec }

      make[EmbeddingSpec[VariantSearchDocument]].from { () => testEmbeddingSpec }

      make[BeautyQNonProductionHybridRunnerManualAdapterInputs].from {
        (
          lexicalBackend: LexicalDocumentBackend[IO, MasterServiceOfferVariantId],
          readinessConfig: QdrantCollectionReadinessConfig,
          collectionInfoClient: QdrantCollectionInfoClient,
          snapshotProvider: VariantSearchDocumentSnapshotProvider[IO],
          pointUpsertClient: QdrantPointUpsertClient,
          embeddingClient: EmbeddingClient,
          qdrantSearchClient: QdrantSearchClient,
          documentLookup: SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument],
          documentSpec: SearchDocumentSpec[VariantSearchDocument],
          embeddingSpec: EmbeddingSpec[VariantSearchDocument],
        ) =>
          BeautyQNonProductionHybridRunnerManualAdapterInputs(
            lexicalBackend = lexicalBackend,
            readinessConfig = readinessConfig,
            collectionInfoClient = collectionInfoClient,
            snapshotProvider = snapshotProvider,
            pointUpsertClient = pointUpsertClient,
            embeddingClient = embeddingClient,
            qdrantSearchClient = qdrantSearchClient,
            documentLookup = documentLookup,
            documentSpec = documentSpec,
            embeddingSpec = embeddingSpec,
          )
      }

      make[BeautyQNonProductionHybridRunnerManualHandle].from {
        (inputs: BeautyQNonProductionHybridRunnerManualAdapterInputs) =>
          inputs.buildHandle()
      }

      make[AdapterInputsProbe].from {
        (
          inputs: BeautyQNonProductionHybridRunnerManualAdapterInputs,
          handle: BeautyQNonProductionHybridRunnerManualHandle,
        ) =>
          AdapterInputsProbe(inputs, handle)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[AdapterInputsProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[AdapterInputsProbe]
  }

  // --- missing lookup probe (run propagates missing document failure) ---

  private def buildMissingLookupProbe(missingVariantId: UUID): AdapterInputsProbe = {
    val module = new ModuleDef {
      make[LexicalDocumentBackend[IO, MasterServiceOfferVariantId]].from {
        new ScriptedLexicalBackend(Nil)
      }

      make[QdrantCollectionReadinessConfig].from { () => testReadinessConfig }

      make[QdrantCollectionInfoClient].from {
        new FailIfCalledCollectionInfoClient
      }

      make[VariantSearchDocumentSnapshotProvider[IO]].from {
        new FailIfCalledSnapshotProvider
      }

      make[QdrantPointUpsertClient].from {
        new FailIfCalledUpsertClient
      }

      make[EmbeddingClient].from {
        new ExpectingEmbeddingClient(input.query)
      }

      make[QdrantSearchClient].from {
        new ExpectingQdrantSearchClient(
          testReadinessConfig.vectorSearchSpec.collectionName,
          missingVariantId,
          0.92,
        )
      }

      make[SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument]].from {
        new ScriptedDocumentLookup(Map.empty)
      }

      make[SearchDocumentSpec[VariantSearchDocument]].from { () => testDocumentSpec }

      make[EmbeddingSpec[VariantSearchDocument]].from { () => testEmbeddingSpec }

      make[BeautyQNonProductionHybridRunnerManualAdapterInputs].from {
        (
          lexicalBackend: LexicalDocumentBackend[IO, MasterServiceOfferVariantId],
          readinessConfig: QdrantCollectionReadinessConfig,
          collectionInfoClient: QdrantCollectionInfoClient,
          snapshotProvider: VariantSearchDocumentSnapshotProvider[IO],
          pointUpsertClient: QdrantPointUpsertClient,
          embeddingClient: EmbeddingClient,
          qdrantSearchClient: QdrantSearchClient,
          documentLookup: SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument],
          documentSpec: SearchDocumentSpec[VariantSearchDocument],
          embeddingSpec: EmbeddingSpec[VariantSearchDocument],
        ) =>
          BeautyQNonProductionHybridRunnerManualAdapterInputs(
            lexicalBackend = lexicalBackend,
            readinessConfig = readinessConfig,
            collectionInfoClient = collectionInfoClient,
            snapshotProvider = snapshotProvider,
            pointUpsertClient = pointUpsertClient,
            embeddingClient = embeddingClient,
            qdrantSearchClient = qdrantSearchClient,
            documentLookup = documentLookup,
            documentSpec = documentSpec,
            embeddingSpec = embeddingSpec,
          )
      }

      make[BeautyQNonProductionHybridRunnerManualHandle].from {
        (inputs: BeautyQNonProductionHybridRunnerManualAdapterInputs) =>
          inputs.buildHandle()
      }

      make[AdapterInputsProbe].from {
        (
          inputs: BeautyQNonProductionHybridRunnerManualAdapterInputs,
          handle: BeautyQNonProductionHybridRunnerManualHandle,
        ) =>
          AdapterInputsProbe(inputs, handle)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[AdapterInputsProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[AdapterInputsProbe]
  }

  // --- Fail-if-called collaborators ---

  private final class FailIfCalledCollectionInfoClient extends QdrantCollectionInfoClient {
    override def collectionInfo(path: String): IO[QueryFailure, Json] =
      ZIO.fail(QueryFailure.OperationFailure("fail-if-called", "collectionInfo must not be called"))
  }

  private final class FailIfCalledSnapshotProvider extends VariantSearchDocumentSnapshotProvider[IO] {
    override def loadSnapshot(): IO[QueryFailure, List[VariantSearchDocument]] =
      ZIO.fail(QueryFailure.OperationFailure("fail-if-called", "loadSnapshot must not be called"))
  }

  private final class FailIfCalledUpsertClient extends QdrantPointUpsertClient {
    override def upsertPoint(path: String, json: Json): IO[QueryFailure, Json] =
      ZIO.fail(QueryFailure.OperationFailure("fail-if-called", "upsertPoint must not be called"))
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

  // --- Scripted collaborators ---

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

  private final class ScriptedSnapshotProvider(
    result: List[VariantSearchDocument],
  ) extends VariantSearchDocumentSnapshotProvider[IO] {
    override def loadSnapshot(): IO[QueryFailure, List[VariantSearchDocument]] =
      ZIO.succeed(result)
  }

  // --- Expecting collaborators ---

  private final class ExpectingEmbeddingClient(expectedText: String) extends EmbeddingClient {
    override def embed(text: String): IO[QueryFailure, Vector[Double]] =
      ZIO.fromEither(
        if (text == expectedText) Right(Vector(0.1, 0.2, 0.3))
        else Left(QueryFailure.OperationFailure("expecting-embed", s"unexpected text: '$text', expected: '$expectedText'"))
      )
  }

  private final class ExpectingUpsertClient(
    expectedCollectionName: String,
    expectedVariantId: UUID,
  ) extends QdrantPointUpsertClient {
    override def upsertPoint(path: String, json: Json): IO[QueryFailure, Json] =
      ZIO.fromEither {
        val expectedPath = s"/collections/${expectedCollectionName}/points?wait=true"
        if (path != expectedPath) {
          Left(QueryFailure.OperationFailure("expecting-upsert", s"unexpected path: '$path', expected: '$expectedPath'"))
        } else {
          val variantIdInPayload = json.hcursor
            .downField("points")
            .downArray
            .downField("payload")
            .downField("variantId")
            .as[String]
            .toOption
          if (variantIdInPayload.contains(expectedVariantId.toString)) {
            Right(Json.fromString("ok"))
          } else {
            Left(QueryFailure.OperationFailure("expecting-upsert", s"variantId not found in payload, expected: '$expectedVariantId'"))
          }
        }
      }
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

  // --- Const collaborators ---

  private final class ConstCollectionInfoClient(result: Either[QueryFailure, Json]) extends QdrantCollectionInfoClient {
    override def collectionInfo(path: String): IO[QueryFailure, Json] =
      ZIO.fromEither(result)
  }

  // --- Probe root ---

  private final case class AdapterInputsProbe(
    inputs: BeautyQNonProductionHybridRunnerManualAdapterInputs,
    handle: BeautyQNonProductionHybridRunnerManualHandle,
  )

  // --- Helpers ---

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

  private val testEmbeddingSpec: leaderboard.search.dsl.EmbeddingSpec[VariantSearchDocument] =
    leaderboard.search.dsl.EmbeddingSpec(
      vectorName = "variant-embedding",
      modelName = "llama-cpp-embedding",
      dimension = 1024,
      distance = VectorDistance.Cosine,
      sourceTextFields = List(leaderboard.search.document.BeautyQVariantSearchDocumentSchema.Fields.allText),
    )

  private val testDocumentSpec: leaderboard.search.dsl.SearchDocumentSpec[VariantSearchDocument] =
    leaderboard.search.dsl.SearchDocumentSpec(
      indexName = "variants",
      id = _.variantId.toString,
      fields = List(
        SearchField[VariantSearchDocument]("variantId", SearchFieldKind.Keyword, d => Some(SearchValue.Keyword(d.variantId.toString))),
        SearchField[VariantSearchDocument]("allText", SearchFieldKind.Text, d => Some(SearchValue.Text(d.allText))),
        SearchField[VariantSearchDocument]("serviceName", SearchFieldKind.Text, d => Some(SearchValue.Text(d.serviceName))),
        SearchField[VariantSearchDocument]("categoryName", SearchFieldKind.Text, d => Some(SearchValue.Text(d.categoryName))),
        SearchField[VariantSearchDocument]("masterName", SearchFieldKind.Text, d => Some(SearchValue.Text(d.masterName))),
        SearchField[VariantSearchDocument]("locationName", SearchFieldKind.Text, d => Some(SearchValue.Text(d.locationName))),
        SearchField[VariantSearchDocument]("address", SearchFieldKind.Text, d => Some(SearchValue.Text(d.address))),
        SearchField[VariantSearchDocument]("serviceText", SearchFieldKind.Text, d => Some(SearchValue.Text(d.serviceText))),
        SearchField[VariantSearchDocument]("attributeText", SearchFieldKind.Text, d => Some(SearchValue.Text(d.attributeText))),
        SearchField[VariantSearchDocument]("providerText", SearchFieldKind.Text, d => Some(SearchValue.Text(d.providerText))),
        SearchField[VariantSearchDocument]("locationText", SearchFieldKind.Text, d => Some(SearchValue.Text(d.locationText))),
      ),
    )

  private def moduleTestDocumentWithId(docVariantId: UUID): VariantSearchDocument =
    VariantSearchDocument(
      variantId = docVariantId,
      masterServiceOfferId = makeVariantId(100),
      masterLocationId = makeVariantId(200),
      masterId = makeVariantId(1000),
      serviceId = makeVariantId(400),
      categoryId = makeVariantId(500),
      serviceName = "Test Service",
      categoryName = "Test Category",
      masterName = "Test Master",
      locationName = "Test Location",
      address = "Test Address",
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
      allText = "test service test category test master test location",
      serviceText = "test service test category",
      attributeText = "coverage gel with removal",
      providerText = "test master test location",
      locationText = "test location test address test category",
    )

  private def makeVariantId(value: Int): MasterServiceOfferVariantId =
    UUID.fromString(f"00000000-0000-0000-0000-$value%012d")

  private val input = UserSearchInput(query = "test query", userLat = None, userLon = None, limit = 10)
  private val intent = ParsedSearchIntent(
    originalQuery = input.query,
    normalizedTokens = List("test", "query"),
    explicitConstraints = Nil,
    softBoosts = Nil,
    remainingText = input.query,
  )

  private def runIO[E, A](effect: IO[E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
