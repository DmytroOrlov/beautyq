package leaderboard.search.startup

import io.circe.syntax.*
import io.circe.{Json, JsonObject}
import leaderboard.search.document.{BeautyQVariantSearchDocumentContract, BeautySearchReadyCatalogDocuments, VariantSearchDocument}
import leaderboard.search.dsl.*
import leaderboard.search.elasticsearch.ElasticsearchMappingInterpreter

final case class BeautyQManagedLocalSearchBootstrapFingerprint(
  value: String,
  inputs: Json,
) {
  def asQdrantCollectionMetadata: JsonObject =
    JsonObject(
      BeautyQManagedLocalSearchBootstrapFingerprint.MetadataKey -> Json.fromString(value),
      BeautyQManagedLocalSearchBootstrapFingerprint.MetadataVersionKey -> Json.fromString(BeautyQManagedLocalSearchBootstrapFingerprint.Version),
    )
}

object BeautyQManagedLocalSearchBootstrapFingerprint {
  val Version: String = "beautyq-managed-local-search-bootstrap-fingerprint-v2"
  val MetadataKey: String = "managedBootstrapFingerprint"
  val MetadataVersionKey: String = "managedBootstrapFingerprintVersion"

  def build(
    spec: BeautySearchSpec,
    catalog: BeautySearchReadyCatalogDocuments,
    vectorSearchSpec: VectorSearchSpec,
    embeddingSpec: EmbeddingSpec[VariantSearchDocument],
    embeddingEndpoint: String,
  ): BeautyQManagedLocalSearchBootstrapFingerprint = {
    // Source-of-truth runtime/schema spec: BeautyQ payload spec plus the managed bootstrap's embedding
    // and vector configuration. The document body and runtime/schema sections are derived generically
    // from this spec; only backend-specific and external inputs are layered in explicitly below.
    val runtimeSpec =
      spec
        .runtimeSpec(
          Map(BeautySearchSpecV1.QdrantPayloadSpecName -> BeautyQVariantSearchDocumentContract.qdrantPayloadSpec)
        )
        .copy(
          embeddingSpec = Some(embeddingSpec),
          vectorSearchSpec = Some(vectorSearchSpec),
        )

    val inputs = SearchRuntimeFingerprint.inputs(
      fingerprintVersion = Version,
      catalogSource = catalog.source,
      documents = catalog.documents,
      runtimeSpec = runtimeSpec,
      extraSections = JsonObject(
        // Managed bootstrap reuses both the ES baseline and the Qdrant resources, so the ES mapping is an
        // explicit layered input here even though it is not part of the generic runtime spec.
        "elasticsearch" -> Json.obj(
          "indexName" -> spec.variantDocument.indexName.asJson,
          "mapping" -> SearchRuntimeFingerprint.canonicalJson(ElasticsearchMappingInterpreter.mapping(spec.variantDocument)),
        ),
        // The embedding endpoint is an external managed-bootstrap input, not a schema/runtime property.
        "managedBootstrap" -> Json.obj(
          "embeddingEndpoint" -> Json.fromString(embeddingEndpoint)
        ),
      ),
    )

    BeautyQManagedLocalSearchBootstrapFingerprint(
      value = SearchRuntimeFingerprint.value(inputs),
      inputs = inputs,
    )
  }

  def decodeCollectionMetadataValue(json: Json): Option[String] =
    leaderboard.search.qdrant.QdrantCollectionInfoDecoder.metadataValue(json, MetadataKey)
      .flatMap(_.asString)
}
