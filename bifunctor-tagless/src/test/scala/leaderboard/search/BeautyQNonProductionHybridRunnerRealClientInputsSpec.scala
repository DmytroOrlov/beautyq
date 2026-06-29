package leaderboard.search

import leaderboard.config.QdrantPortCfg
import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.document.{VariantSearchDocument, VariantSearchDocumentSnapshotProvider}
import leaderboard.search.dsl.{SearchField, SearchFieldKind, SearchValue, VectorDistance, VectorSearchSpec}
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

final class BeautyQNonProductionHybridRunnerRealClientInputsSpec extends AnyWordSpec {

  "BeautyQNonProductionHybridRunnerRealClientInputs" should {

    "real client input boundary is side-effect-free" in {
      val inputs = BeautyQNonProductionHybridRunnerRealClientInputs(
        lexicalBackend = new FailIfCalledLexicalBackend,
        readinessConfig = testReadinessConfig,
        qdrantPortCfg = QdrantPortCfg("127.0.0.1", 1),
        llamaConfig = LlamaCppEmbeddingClientConfig("http://127.0.0.1:9999", "/nonexistent"),
        snapshotProvider = new FailIfCalledSnapshotProvider,
        documentLookup = new FailIfCalledDocumentLookup,
        documentSpec = testDocumentSpec,
        embeddingSpec = testEmbeddingSpec,
      )

      val qdrantInputs = inputs.toQdrantClientInputs()
      assert(qdrantInputs.isInstanceOf[BeautyQNonProductionHybridRunnerQdrantClientInputs])

      val adapterInputs = inputs.toAdapterInputs()
      assert(adapterInputs.isInstanceOf[BeautyQNonProductionHybridRunnerManualAdapterInputs])

      val manualInputs = inputs.toManualInputs()
      assert(manualInputs.isInstanceOf[BeautyQNonProductionHybridRunnerManualInputs])

      val handle = inputs.buildHandle()
      assert(handle.isInstanceOf[BeautyQNonProductionHybridRunnerManualHandle])
    }

    "toQdrantClientInputs creates QdrantClientInputs with real client classes" in {
      val inputs = BeautyQNonProductionHybridRunnerRealClientInputs(
        lexicalBackend = new FailIfCalledLexicalBackend,
        readinessConfig = testReadinessConfig,
        qdrantPortCfg = QdrantPortCfg("10.0.0.1", 6333),
        llamaConfig = LlamaCppEmbeddingClientConfig("http://10.0.0.2:8081", "/v1/embeddings"),
        snapshotProvider = new FailIfCalledSnapshotProvider,
        documentLookup = new FailIfCalledDocumentLookup,
        documentSpec = testDocumentSpec,
        embeddingSpec = testEmbeddingSpec,
      )

      val qdrantInputs = inputs.toQdrantClientInputs()

      assert(qdrantInputs.qdrantClient.isInstanceOf[QdrantClient])
      assert(qdrantInputs.embeddingClient.isInstanceOf[LlamaCppEmbeddingClient])
      assert(qdrantInputs.lexicalBackend eq inputs.lexicalBackend)
      assert(qdrantInputs.snapshotProvider eq inputs.snapshotProvider)
      assert(qdrantInputs.documentLookup eq inputs.documentLookup)
      assert(qdrantInputs.documentSpec eq inputs.documentSpec)
      assert(qdrantInputs.embeddingSpec eq inputs.embeddingSpec)
      assert(qdrantInputs.readinessConfig eq inputs.readinessConfig)
    }

    "toAdapterInputs uses Qdrant adapter wrappers" in {
      val inputs = BeautyQNonProductionHybridRunnerRealClientInputs(
        lexicalBackend = new FailIfCalledLexicalBackend,
        readinessConfig = testReadinessConfig,
        qdrantPortCfg = QdrantPortCfg("127.0.0.1", 1),
        llamaConfig = LlamaCppEmbeddingClientConfig("http://127.0.0.1:9999", "/nonexistent"),
        snapshotProvider = new FailIfCalledSnapshotProvider,
        documentLookup = new FailIfCalledDocumentLookup,
        documentSpec = testDocumentSpec,
        embeddingSpec = testEmbeddingSpec,
      )

      val adapterInputs = inputs.toAdapterInputs()

      assert(adapterInputs.collectionInfoClient.isInstanceOf[QdrantClientCollectionInfoAdapter])
      assert(adapterInputs.pointUpsertClient.isInstanceOf[QdrantClientPointUpsertAdapter])
      assert(adapterInputs.qdrantSearchClient.isInstanceOf[QdrantClientSearchAdapter])
    }

    "buildHandle creates manual handle without network calls" in {
      val inputs = BeautyQNonProductionHybridRunnerRealClientInputs(
        lexicalBackend = new FailIfCalledLexicalBackend,
        readinessConfig = testReadinessConfig,
        qdrantPortCfg = QdrantPortCfg("127.0.0.1", 1),
        llamaConfig = LlamaCppEmbeddingClientConfig("http://127.0.0.1:9999", "/nonexistent"),
        snapshotProvider = new FailIfCalledSnapshotProvider,
        documentLookup = new FailIfCalledDocumentLookup,
        documentSpec = testDocumentSpec,
        embeddingSpec = testEmbeddingSpec,
      )

      val handle = inputs.buildHandle()
      assert(handle.isInstanceOf[BeautyQNonProductionHybridRunnerManualHandle])
    }
  }

  // --- Fail-if-called collaborators ---

  private final class FailIfCalledSnapshotProvider extends VariantSearchDocumentSnapshotProvider[IO] {
    override def loadSnapshot(): IO[QueryFailure, List[VariantSearchDocument]] =
      ZIO.fail(QueryFailure.OperationFailure("fail-if-called", "loadSnapshot must not be called"))
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

  // --- Helpers ---

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
}
