package leaderboard.search

import distage.{Injector, ModuleDef}
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.config.QdrantPortCfg
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.document.{VariantSearchDocument, VariantSearchDocumentSnapshotProvider}
import leaderboard.search.dsl.{EmbeddingSpec, SearchDocumentSpec, SearchField, SearchFieldKind, SearchValue, VectorDistance, VectorSearchSpec}
import leaderboard.search.embedding.{EmbeddingClient, LlamaCppEmbeddingClient, LlamaCppEmbeddingClientConfig}
import leaderboard.search.hybrid._
import leaderboard.search.lexical.{LexicalDocumentBackend, LexicalDocumentHit}
import leaderboard.search.qdrant.{
  QdrantClient,
  QdrantClientCollectionInfoAdapter,
  QdrantClientPointUpsertAdapter,
  QdrantClientSearchAdapter,
  QdrantCollectionCompatibilityExpectation,
  QdrantCollectionReadinessConfig,
}
import leaderboard.search.semantic.SemanticDocumentLookup
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, ZIO}

final class BeautyQNonProductionHybridRunnerRealClientInputsModuleSpec extends AnyWordSpec {

  "BeautyQNonProductionHybridRunnerRealClientInputs Distage module" should {

    "module materialization is side-effect-free" in {
      val probe = buildConstructionProbe

      assert(probe.realInputs.isInstanceOf[BeautyQNonProductionHybridRunnerRealClientInputs])
      assert(probe.qdrantInputs.isInstanceOf[BeautyQNonProductionHybridRunnerQdrantClientInputs])
      assert(probe.handle.isInstanceOf[BeautyQNonProductionHybridRunnerManualHandle])

      assert(probe.qdrantInputs.qdrantClient.isInstanceOf[QdrantClient])
      assert(probe.qdrantInputs.embeddingClient.isInstanceOf[LlamaCppEmbeddingClient])
    }

    "materialized adapter inputs use Qdrant adapters" in {
      val probe = buildAdapterProbe

      assert(probe.adapterInputs.isInstanceOf[BeautyQNonProductionHybridRunnerManualAdapterInputs])
      assert(probe.adapterInputs.collectionInfoClient.isInstanceOf[QdrantClientCollectionInfoAdapter])
      assert(probe.adapterInputs.pointUpsertClient.isInstanceOf[QdrantClientPointUpsertAdapter])
      assert(probe.adapterInputs.qdrantSearchClient.isInstanceOf[QdrantClientSearchAdapter])
    }

    "module materialization preserves explicit config/dependency fields" in {
      val probe = buildConfigProbe

      assert(probe.realInputs.qdrantPortCfg eq testQdrantPortCfg)
      assert(probe.realInputs.llamaConfig eq testLlamaConfig)
      assert(probe.realInputs.readinessConfig eq testReadinessConfig)
      assert(probe.realInputs.documentSpec eq testDocumentSpec)
      assert(probe.realInputs.embeddingSpec eq testEmbeddingSpec)
      assert(probe.realInputs.lexicalBackend eq failIfCalledLexicalBackend)
      assert(probe.realInputs.snapshotProvider eq failIfCalledSnapshotProvider)
      assert(probe.realInputs.documentLookup eq failIfCalledDocumentLookup)
    }
  }

  // --- Construction probe (side-effect-free) ---

  private def buildConstructionProbe: RealClientInputsProbe = {
    val module = new ModuleDef {
      make[LexicalDocumentBackend[IO, MasterServiceOfferVariantId]].from {
        failIfCalledLexicalBackend
      }

      make[QdrantCollectionReadinessConfig].from { () => testReadinessConfig }

      make[QdrantPortCfg].from { () => testQdrantPortCfg }

      make[LlamaCppEmbeddingClientConfig].from { () => testLlamaConfig }

      make[VariantSearchDocumentSnapshotProvider[IO]].from {
        failIfCalledSnapshotProvider
      }

      make[SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument]].from {
        failIfCalledDocumentLookup
      }

      make[SearchDocumentSpec[VariantSearchDocument]].from { () => testDocumentSpec }

      make[EmbeddingSpec[VariantSearchDocument]].from { () => testEmbeddingSpec }

      make[BeautyQNonProductionHybridRunnerRealClientInputs].from {
        (
          lexicalBackend: LexicalDocumentBackend[IO, MasterServiceOfferVariantId],
          readinessConfig: QdrantCollectionReadinessConfig,
          qdrantPortCfg: QdrantPortCfg,
          llamaConfig: LlamaCppEmbeddingClientConfig,
          snapshotProvider: VariantSearchDocumentSnapshotProvider[IO],
          documentLookup: SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument],
          documentSpec: SearchDocumentSpec[VariantSearchDocument],
          embeddingSpec: EmbeddingSpec[VariantSearchDocument],
        ) =>
          BeautyQNonProductionHybridRunnerRealClientInputs(
            lexicalBackend = lexicalBackend,
            readinessConfig = readinessConfig,
            qdrantPortCfg = qdrantPortCfg,
            llamaConfig = llamaConfig,
            snapshotProvider = snapshotProvider,
            documentLookup = documentLookup,
            documentSpec = documentSpec,
            embeddingSpec = embeddingSpec,
          )
      }

      make[BeautyQNonProductionHybridRunnerQdrantClientInputs].from {
        (realInputs: BeautyQNonProductionHybridRunnerRealClientInputs) =>
          realInputs.toQdrantClientInputs()
      }

      make[BeautyQNonProductionHybridRunnerManualHandle].from {
        (realInputs: BeautyQNonProductionHybridRunnerRealClientInputs) =>
          realInputs.buildHandle()
      }

      make[RealClientInputsProbe].from {
        (
          realInputs: BeautyQNonProductionHybridRunnerRealClientInputs,
          qdrantInputs: BeautyQNonProductionHybridRunnerQdrantClientInputs,
          handle: BeautyQNonProductionHybridRunnerManualHandle,
        ) =>
          RealClientInputsProbe(realInputs, qdrantInputs, handle)
      }
    }

    Injector().produce(
      bindings = module,
      roots = Roots.target[RealClientInputsProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet().get[RealClientInputsProbe]
  }

  // --- Adapter probe ---

  private def buildAdapterProbe: AdapterInputsProbe = {
    val module = new ModuleDef {
      make[LexicalDocumentBackend[IO, MasterServiceOfferVariantId]].from {
        failIfCalledLexicalBackend
      }

      make[QdrantCollectionReadinessConfig].from { () => testReadinessConfig }

      make[QdrantPortCfg].from { () => testQdrantPortCfg }

      make[LlamaCppEmbeddingClientConfig].from { () => testLlamaConfig }

      make[VariantSearchDocumentSnapshotProvider[IO]].from {
        failIfCalledSnapshotProvider
      }

      make[SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument]].from {
        failIfCalledDocumentLookup
      }

      make[SearchDocumentSpec[VariantSearchDocument]].from { () => testDocumentSpec }

      make[EmbeddingSpec[VariantSearchDocument]].from { () => testEmbeddingSpec }

      make[BeautyQNonProductionHybridRunnerRealClientInputs].from {
        (
          lexicalBackend: LexicalDocumentBackend[IO, MasterServiceOfferVariantId],
          readinessConfig: QdrantCollectionReadinessConfig,
          qdrantPortCfg: QdrantPortCfg,
          llamaConfig: LlamaCppEmbeddingClientConfig,
          snapshotProvider: VariantSearchDocumentSnapshotProvider[IO],
          documentLookup: SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument],
          documentSpec: SearchDocumentSpec[VariantSearchDocument],
          embeddingSpec: EmbeddingSpec[VariantSearchDocument],
        ) =>
          BeautyQNonProductionHybridRunnerRealClientInputs(
            lexicalBackend = lexicalBackend,
            readinessConfig = readinessConfig,
            qdrantPortCfg = qdrantPortCfg,
            llamaConfig = llamaConfig,
            snapshotProvider = snapshotProvider,
            documentLookup = documentLookup,
            documentSpec = documentSpec,
            embeddingSpec = embeddingSpec,
          )
      }

      make[BeautyQNonProductionHybridRunnerManualAdapterInputs].from {
        (realInputs: BeautyQNonProductionHybridRunnerRealClientInputs) =>
          realInputs.toAdapterInputs()
      }

      make[AdapterInputsProbe].from {
        (adapterInputs: BeautyQNonProductionHybridRunnerManualAdapterInputs) =>
          AdapterInputsProbe(adapterInputs)
      }
    }

    Injector().produce(
      bindings = module,
      roots = Roots.target[AdapterInputsProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet().get[AdapterInputsProbe]
  }

  // --- Config probe (preserves references) ---

  private def buildConfigProbe: ConfigProbe = {
    val module = new ModuleDef {
      make[LexicalDocumentBackend[IO, MasterServiceOfferVariantId]].from {
        failIfCalledLexicalBackend
      }

      make[QdrantCollectionReadinessConfig].from { () => testReadinessConfig }

      make[QdrantPortCfg].from { () => testQdrantPortCfg }

      make[LlamaCppEmbeddingClientConfig].from { () => testLlamaConfig }

      make[VariantSearchDocumentSnapshotProvider[IO]].from {
        failIfCalledSnapshotProvider
      }

      make[SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument]].from {
        failIfCalledDocumentLookup
      }

      make[SearchDocumentSpec[VariantSearchDocument]].from { () => testDocumentSpec }

      make[EmbeddingSpec[VariantSearchDocument]].from { () => testEmbeddingSpec }

      make[BeautyQNonProductionHybridRunnerRealClientInputs].from {
        (
          lexicalBackend: LexicalDocumentBackend[IO, MasterServiceOfferVariantId],
          readinessConfig: QdrantCollectionReadinessConfig,
          qdrantPortCfg: QdrantPortCfg,
          llamaConfig: LlamaCppEmbeddingClientConfig,
          snapshotProvider: VariantSearchDocumentSnapshotProvider[IO],
          documentLookup: SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument],
          documentSpec: SearchDocumentSpec[VariantSearchDocument],
          embeddingSpec: EmbeddingSpec[VariantSearchDocument],
        ) =>
          BeautyQNonProductionHybridRunnerRealClientInputs(
            lexicalBackend = lexicalBackend,
            readinessConfig = readinessConfig,
            qdrantPortCfg = qdrantPortCfg,
            llamaConfig = llamaConfig,
            snapshotProvider = snapshotProvider,
            documentLookup = documentLookup,
            documentSpec = documentSpec,
            embeddingSpec = embeddingSpec,
          )
      }

      make[ConfigProbe].from {
        (
          lexicalBackend: LexicalDocumentBackend[IO, MasterServiceOfferVariantId],
          readinessConfig: QdrantCollectionReadinessConfig,
          qdrantPortCfg: QdrantPortCfg,
          llamaConfig: LlamaCppEmbeddingClientConfig,
          snapshotProvider: VariantSearchDocumentSnapshotProvider[IO],
          documentLookup: SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument],
          documentSpec: SearchDocumentSpec[VariantSearchDocument],
          embeddingSpec: EmbeddingSpec[VariantSearchDocument],
          realInputs: BeautyQNonProductionHybridRunnerRealClientInputs,
        ) =>
          ConfigProbe(
            realInputs,
            lexicalBackend,
            readinessConfig,
            qdrantPortCfg,
            llamaConfig,
            snapshotProvider,
            documentLookup,
            documentSpec,
            embeddingSpec,
          )
      }
    }

    Injector().produce(
      bindings = module,
      roots = Roots.target[ConfigProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet().get[ConfigProbe]
  }

  // --- Fail-if-called collaborators ---

  private val failIfCalledLexicalBackend = new FailIfCalledLexicalBackend

  private val failIfCalledSnapshotProvider = new FailIfCalledSnapshotProvider

  private val failIfCalledDocumentLookup = new FailIfCalledDocumentLookup

  private final class FailIfCalledLexicalBackend extends LexicalDocumentBackend[IO, MasterServiceOfferVariantId] {
    override def documentHits(
      input: UserSearchInput,
      intent: ParsedSearchIntent,
    ): IO[QueryFailure, List[LexicalDocumentHit[MasterServiceOfferVariantId]]] =
      ZIO.fail(QueryFailure.domain("FailIfCalledLexicalBackend.documentHits was unexpectedly called"))
  }

  private final class FailIfCalledSnapshotProvider extends VariantSearchDocumentSnapshotProvider[IO] {
    override def loadSnapshot(): IO[QueryFailure, List[VariantSearchDocument]] =
      ZIO.fail(QueryFailure.OperationFailure("fail-if-called", "loadSnapshot must not be called"))
  }

  private final class FailIfCalledDocumentLookup extends SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument] {
    override def lookup(ids: List[MasterServiceOfferVariantId]): IO[QueryFailure, Map[MasterServiceOfferVariantId, VariantSearchDocument]] =
      ZIO.fail(QueryFailure.domain("FailIfCalledDocumentLookup.lookup was unexpectedly called"))
  }

  // --- Probe roots ---

  private final case class RealClientInputsProbe(
    realInputs: BeautyQNonProductionHybridRunnerRealClientInputs,
    qdrantInputs: BeautyQNonProductionHybridRunnerQdrantClientInputs,
    handle: BeautyQNonProductionHybridRunnerManualHandle,
  )

  private final case class AdapterInputsProbe(
    adapterInputs: BeautyQNonProductionHybridRunnerManualAdapterInputs,
  )

  private final case class ConfigProbe(
    realInputs: BeautyQNonProductionHybridRunnerRealClientInputs,
    lexicalBackend: LexicalDocumentBackend[IO, MasterServiceOfferVariantId],
    readinessConfig: QdrantCollectionReadinessConfig,
    qdrantPortCfg: QdrantPortCfg,
    llamaConfig: LlamaCppEmbeddingClientConfig,
    snapshotProvider: VariantSearchDocumentSnapshotProvider[IO],
    documentLookup: SemanticDocumentLookup[IO, MasterServiceOfferVariantId, VariantSearchDocument],
    documentSpec: SearchDocumentSpec[VariantSearchDocument],
    embeddingSpec: EmbeddingSpec[VariantSearchDocument],
  )

  // --- Helpers ---

  private val testQdrantPortCfg = QdrantPortCfg("127.0.0.1", 1)

  private val testLlamaConfig = LlamaCppEmbeddingClientConfig("http://127.0.0.1:9999", "/nonexistent")

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

  private val testEmbeddingSpec: EmbeddingSpec[VariantSearchDocument] =
    EmbeddingSpec(
      vectorName = "variant-embedding",
      modelName = "llama-cpp-embedding",
      dimension = 1024,
      distance = VectorDistance.Cosine,
      sourceTextFields = List(leaderboard.search.document.BeautyQVariantSearchDocumentSchema.Fields.allText),
    )

  private val testDocumentSpec: SearchDocumentSpec[VariantSearchDocument] =
    SearchDocumentSpec(
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
}
