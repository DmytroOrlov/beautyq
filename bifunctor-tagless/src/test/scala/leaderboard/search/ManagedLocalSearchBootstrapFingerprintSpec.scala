package leaderboard.search

import com.typesafe.config.ConfigFactory
import io.circe.Json
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

    "treat Qdrant collection info without managed fingerprint metadata as not reusable" in {
      val catalog = BeautySearchReadyCatalogDocuments("managed-local-fingerprint-spec", documents)
      val fingerprint = BeautyQManagedLocalSearchBootstrapFingerprint.build(baseSpec, catalog, vectorSpec, embeddingSpec, endpoint)

      assert(!BeautyQManagedLocalSearchBootstrap.qdrantCollectionInfoReusable(qdrantInfo(pointsCount = Some(documents.size)), documents.size, fingerprint))
    }

    "treat Qdrant collection info with mismatched managed fingerprint metadata as not reusable" in {
      val catalog = BeautySearchReadyCatalogDocuments("managed-local-fingerprint-spec", documents)
      val fingerprint = BeautyQManagedLocalSearchBootstrapFingerprint.build(baseSpec, catalog, vectorSpec, embeddingSpec, endpoint)

      assert(!BeautyQManagedLocalSearchBootstrap.qdrantCollectionInfoReusable(
        qdrantInfo(pointsCount = Some(documents.size), managedFingerprint = Some("other-fingerprint")),
        documents.size,
        fingerprint,
      ))
    }

    "treat Qdrant collection info with matching managed fingerprint metadata and enough points as reusable" in {
      val catalog = BeautySearchReadyCatalogDocuments("managed-local-fingerprint-spec", documents)
      val fingerprint = BeautyQManagedLocalSearchBootstrapFingerprint.build(baseSpec, catalog, vectorSpec, embeddingSpec, endpoint)

      assert(BeautyQManagedLocalSearchBootstrap.qdrantCollectionInfoReusable(
        qdrantInfo(pointsCount = Some(documents.size), managedFingerprint = Some(fingerprint.value)),
        documents.size,
        fingerprint,
      ))
    }

    "treat Qdrant collection info with matching managed fingerprint metadata but too few points as not reusable" in {
      val catalog = BeautySearchReadyCatalogDocuments("managed-local-fingerprint-spec", documents)
      val fingerprint = BeautyQManagedLocalSearchBootstrapFingerprint.build(baseSpec, catalog, vectorSpec, embeddingSpec, endpoint)

      assert(!BeautyQManagedLocalSearchBootstrap.qdrantCollectionInfoReusable(
        qdrantInfo(pointsCount = Some(documents.size - 1), managedFingerprint = Some(fingerprint.value)),
        documents.size,
        fingerprint,
      ))
    }

    "treat Qdrant collection info with matching managed fingerprint metadata but no supported count field as not reusable" in {
      val catalog = BeautySearchReadyCatalogDocuments("managed-local-fingerprint-spec", documents)
      val fingerprint = BeautyQManagedLocalSearchBootstrapFingerprint.build(baseSpec, catalog, vectorSpec, embeddingSpec, endpoint)

      assert(!BeautyQManagedLocalSearchBootstrap.qdrantCollectionInfoReusable(
        qdrantInfo(managedFingerprint = Some(fingerprint.value)),
        documents.size,
        fingerprint,
      ))
    }

    "treat Qdrant collection info with matching managed fingerprint metadata and enough result.indexed_vectors_count as reusable" in {
      val catalog = BeautySearchReadyCatalogDocuments("managed-local-fingerprint-spec", documents)
      val fingerprint = BeautyQManagedLocalSearchBootstrapFingerprint.build(baseSpec, catalog, vectorSpec, embeddingSpec, endpoint)

      assert(BeautyQManagedLocalSearchBootstrap.qdrantCollectionInfoReusable(
        qdrantInfo(indexedVectorsCount = Some(documents.size), managedFingerprint = Some(fingerprint.value)),
        documents.size,
        fingerprint,
      ))
    }

    "treat Qdrant collection info with matching managed fingerprint metadata and enough top-level points_count as reusable" in {
      val catalog = BeautySearchReadyCatalogDocuments("managed-local-fingerprint-spec", documents)
      val fingerprint = BeautyQManagedLocalSearchBootstrapFingerprint.build(baseSpec, catalog, vectorSpec, embeddingSpec, endpoint)

      assert(BeautyQManagedLocalSearchBootstrap.qdrantCollectionInfoReusable(
        qdrantInfo(topLevelPointsCount = Some(documents.size), managedFingerprint = Some(fingerprint.value)),
        documents.size,
        fingerprint,
      ))
    }

    "treat Qdrant collection info with matching managed fingerprint metadata and enough top-level indexed_vectors_count as reusable" in {
      val catalog = BeautySearchReadyCatalogDocuments("managed-local-fingerprint-spec", documents)
      val fingerprint = BeautyQManagedLocalSearchBootstrapFingerprint.build(baseSpec, catalog, vectorSpec, embeddingSpec, endpoint)

      assert(BeautyQManagedLocalSearchBootstrap.qdrantCollectionInfoReusable(
        qdrantInfo(topLevelIndexedVectorsCount = Some(documents.size), managedFingerprint = Some(fingerprint.value)),
        documents.size,
        fingerprint,
      ))
    }
  }

  private def qdrantInfo(
    pointsCount: Option[Int] = None,
    indexedVectorsCount: Option[Int] = None,
    topLevelPointsCount: Option[Int] = None,
    topLevelIndexedVectorsCount: Option[Int] = None,
    managedFingerprint: Option[String] = None,
  ): Json = {
    val resultCountFields = List(
      pointsCount.map("points_count" -> Json.fromInt(_)),
      indexedVectorsCount.map("indexed_vectors_count" -> Json.fromInt(_)),
    ).flatten

    val topLevelCountFields = List(
      topLevelPointsCount.map("points_count" -> Json.fromInt(_)),
      topLevelIndexedVectorsCount.map("indexed_vectors_count" -> Json.fromInt(_)),
    ).flatten

    val metadataField = managedFingerprint.map { value =>
      "metadata" -> Json.obj(
        BeautyQManagedLocalSearchBootstrapFingerprint.MetadataKey -> Json.fromString(value),
        BeautyQManagedLocalSearchBootstrapFingerprint.MetadataVersionKey -> Json.fromString(
          BeautyQManagedLocalSearchBootstrapFingerprint.Version
        ),
      )
    }

    Json.obj("result" -> Json.obj((resultCountFields ++ metadataField.toList): _*))
      .deepMerge(Json.obj(topLevelCountFields: _*))
  }
}
