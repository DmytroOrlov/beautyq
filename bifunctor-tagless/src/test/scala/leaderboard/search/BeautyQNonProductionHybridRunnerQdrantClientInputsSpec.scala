package leaderboard.search

import leaderboard.model.MasterServiceOfferVariantId
import leaderboard.model.QueryFailure
import leaderboard.search.document.{VariantSearchDocument, VariantSearchDocumentSnapshotProvider}
import leaderboard.search.dsl.{SearchField, SearchFieldKind, SearchValue, VectorDistance, VectorSearchSpec}
import leaderboard.search.embedding.EmbeddingClient
import leaderboard.search.hybrid.{
  BeautyQNonProductionHybridRunnerManualAdapterInputs,
  BeautyQNonProductionHybridRunnerManualHandle,
  BeautyQNonProductionHybridRunnerManualInputs,
  BeautyQNonProductionHybridRunnerQdrantClientInputs,
}
import leaderboard.search.lexical.{LexicalDocumentBackend, LexicalDocumentHit}
import leaderboard.search.qdrant.{
  QdrantClientCollectionInfoAdapter,
  QdrantClientPointUpsertAdapter,
  QdrantClientSearchAdapter,
  QdrantClient,
  QdrantCollectionCompatibilityExpectation,
  QdrantCollectionReadinessConfig,
}
import leaderboard.search.semantic.SemanticDocumentLookup
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, ZIO}

final class BeautyQNonProductionHybridRunnerQdrantClientInputsSpec extends AnyWordSpec {

  "BeautyQNonProductionHybridRunnerQdrantClientInputs" should {

    "qdrant client input boundary is side-effect-free" in {
      val qdrantClient = new QdrantClient("127.0.0.1", 1)

      val inputs = BeautyQNonProductionHybridRunnerQdrantClientInputs(
        lexicalBackend = new FailIfCalledLexicalBackend,
        readinessConfig = testReadinessConfig,
        qdrantClient = qdrantClient,
        snapshotProvider = new FailIfCalledSnapshotProvider,
        embeddingClient = new FailIfCalledEmbeddingClient,
        documentLookup = new FailIfCalledDocumentLookup,
        documentSpec = testDocumentSpec,
        embeddingSpec = testEmbeddingSpec,
      )

      val adapterInputs = inputs.toAdapterInputs()
      assert(adapterInputs.isInstanceOf[BeautyQNonProductionHybridRunnerManualAdapterInputs])

      val manualInputs = inputs.toManualInputs()
      assert(manualInputs.isInstanceOf[BeautyQNonProductionHybridRunnerManualInputs])

      val handle = inputs.buildHandle()
      assert(handle.isInstanceOf[BeautyQNonProductionHybridRunnerManualHandle])
    }

    "toAdapterInputs uses QdrantClient adapter wrappers" in {
      val qdrantClient = new QdrantClient("127.0.0.1", 1)

      val inputs = BeautyQNonProductionHybridRunnerQdrantClientInputs(
        lexicalBackend = new FailIfCalledLexicalBackend,
        readinessConfig = testReadinessConfig,
        qdrantClient = qdrantClient,
        snapshotProvider = new FailIfCalledSnapshotProvider,
        embeddingClient = new FailIfCalledEmbeddingClient,
        documentLookup = new FailIfCalledDocumentLookup,
        documentSpec = testDocumentSpec,
        embeddingSpec = testEmbeddingSpec,
      )

      val adapterInputs = inputs.toAdapterInputs()

      assert(adapterInputs.collectionInfoClient.isInstanceOf[QdrantClientCollectionInfoAdapter])
      assert(adapterInputs.pointUpsertClient.isInstanceOf[QdrantClientPointUpsertAdapter])
      assert(adapterInputs.qdrantSearchClient.isInstanceOf[QdrantClientSearchAdapter])

      assert(adapterInputs.lexicalBackend eq inputs.lexicalBackend)
      assert(adapterInputs.snapshotProvider eq inputs.snapshotProvider)
      assert(adapterInputs.embeddingClient eq inputs.embeddingClient)
      assert(adapterInputs.documentLookup eq inputs.documentLookup)
      assert(adapterInputs.documentSpec eq inputs.documentSpec)
      assert(adapterInputs.embeddingSpec eq inputs.embeddingSpec)
      assert(adapterInputs.readinessConfig eq inputs.readinessConfig)
    }

    "buildHandle returns manual handle without HTTP calls" in {
      val qdrantClient = new QdrantClient("127.0.0.1", 1)

      val inputs = BeautyQNonProductionHybridRunnerQdrantClientInputs(
        lexicalBackend = new FailIfCalledLexicalBackend,
        readinessConfig = testReadinessConfig,
        qdrantClient = qdrantClient,
        snapshotProvider = new FailIfCalledSnapshotProvider,
        embeddingClient = new FailIfCalledEmbeddingClient,
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

  private final class FailIfCalledEmbeddingClient extends EmbeddingClient {
    override def embed(text: String): IO[QueryFailure, Vector[Double]] =
      ZIO.fail(QueryFailure.OperationFailure("fail-if-called", "embed must not be called"))
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
