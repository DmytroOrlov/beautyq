package leaderboard.search

import com.typesafe.config.ConfigFactory
import leaderboard.plugins.BeautySearchLocalQdrantSupplementLauncherModule
import leaderboard.search.document.{BeautySearchCatalogSnapshot, BeautySearchReadyCatalogDocuments, VariantSearchDocumentBuilder}
import leaderboard.search.dsl.VectorSearchSpec
import leaderboard.search.embedding.LlamaCppEmbeddingClientConfig
import leaderboard.search.startup.{BeautyQManagedLocalSearchBootstrap, BeautyQManagedLocalSearchBootstrapFingerprint}
import leaderboard.seed.BeautyQSeedLoader
import org.scalatest.wordspec.AnyWordSpec

final class ManagedLocalSearchBootstrapFingerprintSpec extends AnyWordSpec {
  private val baseSpec = leaderboard.search.dsl.BeautySearchSpecV1.spec
  private val vectorSpec = BeautySearchLocalQdrantSupplementLauncherModule.VectorSpec
  private val embeddingSpec = BeautyQManagedLocalSearchBootstrap.embeddingSpec(
    vectorSpec,
    BeautyQManagedLocalSearchBootstrap.ExpectedVectorDimension,
  )
  private val endpoint = {
    val config = ConfigFactory.load("common-reference.conf").resolve().getConfig("llama-cpp-embedding")
    LlamaCppEmbeddingClientConfig(
      baseUrl = config.getString("baseUrl"),
      endpointPath = config.getString("endpointPath"),
    ).baseUrl
  }

  private val seed = new BeautyQSeedLoader.ResourceLoader().load() match {
    case Right(value) => value
    case Left(error)  => throw new RuntimeException(error.message)
  }

  private val documents = VariantSearchDocumentBuilder.build(BeautySearchCatalogSnapshot.fromSeedData(seed)) match {
    case Right(value) => value
    case Left(error)  => throw new RuntimeException(error.message)
  }

  "BeautyQManagedLocalSearchBootstrapFingerprint" should {
    "be deterministic for the same catalog, search, vector, and embedding inputs" in {
      val catalog = BeautySearchReadyCatalogDocuments("managed-local-fingerprint-spec", documents)
      val first = BeautyQManagedLocalSearchBootstrapFingerprint.build(baseSpec, catalog, vectorSpec, embeddingSpec, endpoint)
      val second = BeautyQManagedLocalSearchBootstrapFingerprint.build(baseSpec, catalog, vectorSpec, embeddingSpec, endpoint)

      assert(first.value == second.value)
      assert(first.inputs == second.inputs)
    }

    "change when a search document content field changes" in {
      val catalog = BeautySearchReadyCatalogDocuments("managed-local-fingerprint-spec", documents)
      val changedCatalog = BeautySearchReadyCatalogDocuments(
        "managed-local-fingerprint-spec",
        documents match {
          case head :: tail => head.copy(serviceText = s"${head.serviceText} changed") :: tail
          case Nil => fail("expected seed documents to be non-empty")
        },
      )

      val original = BeautyQManagedLocalSearchBootstrapFingerprint.build(baseSpec, catalog, vectorSpec, embeddingSpec, endpoint)
      val changed = BeautyQManagedLocalSearchBootstrapFingerprint.build(baseSpec, changedCatalog, vectorSpec, embeddingSpec, endpoint)

      assert(original.value != changed.value)
    }

    "change when the Qdrant vector spec changes" in {
      val catalog = BeautySearchReadyCatalogDocuments("managed-local-fingerprint-spec", documents)
      val changedVectorSpec = VectorSearchSpec(
        collectionName = vectorSpec.collectionName,
        vectorName = s"${vectorSpec.vectorName}-changed",
        topK = vectorSpec.topK,
        scoreThreshold = vectorSpec.scoreThreshold,
      )
      val changedEmbeddingSpec = BeautyQManagedLocalSearchBootstrap.embeddingSpec(
        changedVectorSpec,
        BeautyQManagedLocalSearchBootstrap.ExpectedVectorDimension,
      )

      val original = BeautyQManagedLocalSearchBootstrapFingerprint.build(baseSpec, catalog, vectorSpec, embeddingSpec, endpoint)
      val changed = BeautyQManagedLocalSearchBootstrapFingerprint.build(baseSpec, catalog, changedVectorSpec, changedEmbeddingSpec, endpoint)

      assert(original.value != changed.value)
    }
  }
}
