package leaderboard.search.startup

import io.circe.syntax.*
import io.circe.{Json, JsonObject}
import leaderboard.search.document.{BeautySearchReadyCatalogDocuments, VariantSearchDocument}
import leaderboard.search.dsl.*
import leaderboard.search.elasticsearch.ElasticsearchMappingInterpreter

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

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
  val Version: String = "beautyq-managed-local-search-bootstrap-fingerprint-v1"
  val VariantSearchDocumentSchemaVersion: String = "VariantSearchDocument:v1"
  val MetadataKey: String = "managedBootstrapFingerprint"
  val MetadataVersionKey: String = "managedBootstrapFingerprintVersion"

  def build(
    spec: BeautySearchSpec,
    catalog: BeautySearchReadyCatalogDocuments,
    vectorSearchSpec: VectorSearchSpec,
    embeddingSpec: EmbeddingSpec[VariantSearchDocument],
    embeddingEndpoint: String,
  ): BeautyQManagedLocalSearchBootstrapFingerprint = {
    val inputs = canonicalJson(
      Json.obj(
        "fingerprintVersion" -> Version.asJson,
        "catalog" -> Json.obj(
          "source" -> catalog.source.asJson,
          "documents" -> Json.arr(catalog.documents.sortBy(_.variantId.toString).map(documentJson): _*),
        ),
        "searchDocument" -> Json.obj(
          "schemaVersion" -> VariantSearchDocumentSchemaVersion.asJson,
          "indexName" -> spec.variantDocument.indexName.asJson,
          "fields" -> Json.arr(spec.variantDocument.fields.map(fieldJson): _*),
        ),
        "elasticsearch" -> Json.obj(
          "indexName" -> spec.variantDocument.indexName.asJson,
          "mapping" -> canonicalJson(ElasticsearchMappingInterpreter.mapping(spec)),
        ),
        "qdrant" -> Json.obj(
          "collectionName" -> vectorSearchSpec.collectionName.asJson,
          "vectorName" -> vectorSearchSpec.vectorName.asJson,
          "dimension" -> embeddingSpec.dimension.asJson,
          "distance" -> renderDistance(embeddingSpec.distance).asJson,
        ),
        "embedding" -> Json.obj(
          "modelName" -> embeddingSpec.modelName.asJson,
          "endpointLabel" -> embeddingEndpoint.asJson,
          "sourceTextFieldPaths" -> Json.arr(embeddingSpec.sourceTextFieldPaths.map(Json.fromString): _*),
        ),
      )
    )

    BeautyQManagedLocalSearchBootstrapFingerprint(
      value = sha256Hex(inputs.noSpaces),
      inputs = inputs,
    )
  }

  def decodeCollectionMetadataValue(json: Json): Option[String] =
    leaderboard.search.qdrant.QdrantCollectionInfoDecoder.metadata(json)
      .flatMap(_.apply(MetadataKey))
      .flatMap(_.asString)

  private def canonicalJson(json: Json): Json =
    json.arrayOrObject(
      json,
      values => Json.arr(values.map(canonicalJson): _*),
      obj => Json.fromJsonObject(JsonObject.fromIterable(obj.toIterable.toList.sortBy(_._1).map {
        case (key, value) => key -> canonicalJson(value)
      })),
    )

  private def documentJson(document: VariantSearchDocument): Json =
    Json.obj(
      "variantId" -> document.variantId.toString.asJson,
      "masterServiceOfferId" -> document.masterServiceOfferId.toString.asJson,
      "masterLocationId" -> document.masterLocationId.toString.asJson,
      "masterId" -> document.masterId.toString.asJson,
      "serviceId" -> document.serviceId.toString.asJson,
      "categoryId" -> document.categoryId.toString.asJson,
      "serviceName" -> document.serviceName.asJson,
      "categoryName" -> document.categoryName.asJson,
      "masterName" -> document.masterName.asJson,
      "locationName" -> document.locationName.asJson,
      "address" -> document.address.asJson,
      "location" -> Json.obj(
        "lat" -> document.location.lat.toString.asJson,
        "lon" -> document.location.lon.toString.asJson,
      ),
      "lat" -> document.lat.toString.asJson,
      "lon" -> document.lon.toString.asJson,
      "priceFrom" -> document.priceFrom.toString.asJson,
      "priceTo" -> document.priceTo.toString.asJson,
      "durationMin" -> document.durationMin.asJson,
      "enumAttributes" -> stringMapJson(document.enumAttributes),
      "booleanAttributes" -> Json.fromJsonObject(JsonObject.fromIterable(document.booleanAttributes.toList.sortBy(_._1).map {
        case (key, value) => key -> value.asJson
      })),
      "intAttributes" -> Json.fromJsonObject(JsonObject.fromIterable(document.intAttributes.toList.sortBy(_._1).map {
        case (key, value) => key -> value.asJson
      })),
      "bigDecimalAttributes" -> Json.fromJsonObject(JsonObject.fromIterable(document.bigDecimalAttributes.toList.sortBy(_._1).map {
        case (key, value) => key -> value.toString.asJson
      })),
      "allText" -> document.allText.asJson,
      "serviceText" -> document.serviceText.asJson,
      "attributeText" -> document.attributeText.asJson,
      "providerText" -> document.providerText.asJson,
      "locationText" -> document.locationText.asJson,
    )

  private def fieldJson(field: SearchField[VariantSearchDocument]): Json =
    Json.obj(
      "path" -> field.path.asJson,
      "kind" -> renderFieldKind(field.kind).asJson,
      "semantic" -> field.semantic.map(renderSemantic).asJson,
      "searchable" -> field.searchable.asJson,
      "filterable" -> field.filterable.asJson,
      "facetable" -> field.facetable.asJson,
      "sortable" -> field.sortable.asJson,
      "boost" -> field.boost.asJson,
      "analyzer" -> field.analyzer.asJson,
    )

  private def stringMapJson(values: Map[String, String]): Json =
    Json.fromJsonObject(JsonObject.fromIterable(values.toList.sortBy(_._1).map {
      case (key, value) => key -> value.asJson
    }))

  private def renderFieldKind(kind: SearchFieldKind): String =
    kind match {
      case SearchFieldKind.Text => "text"
      case SearchFieldKind.Keyword => "keyword"
      case SearchFieldKind.Integer => "integer"
      case SearchFieldKind.Decimal => "decimal"
      case SearchFieldKind.Boolean => "boolean"
      case SearchFieldKind.GeoPoint => "geo_point"
    }

  private def renderSemantic(semantic: SearchFieldSemantic): String =
    semantic match {
      case SearchFieldSemantic.VariantId => "variantId"
      case SearchFieldSemantic.MasterServiceOfferId => "masterServiceOfferId"
      case SearchFieldSemantic.MasterLocationId => "masterLocationId"
      case SearchFieldSemantic.MasterId => "masterId"
      case SearchFieldSemantic.ServiceId => "serviceId"
      case SearchFieldSemantic.ServiceName => "serviceName"
      case SearchFieldSemantic.CategoryId => "categoryId"
      case SearchFieldSemantic.CategoryName => "categoryName"
      case SearchFieldSemantic.PriceFrom => "priceFrom"
      case SearchFieldSemantic.PriceTo => "priceTo"
      case SearchFieldSemantic.DurationMin => "durationMin"
      case SearchFieldSemantic.Location => "location"
      case SearchFieldSemantic.AllText => "allText"
      case SearchFieldSemantic.ServiceText => "serviceText"
      case SearchFieldSemantic.AttributeText => "attributeText"
      case SearchFieldSemantic.ProviderText => "providerText"
      case SearchFieldSemantic.LocationText => "locationText"
      case SearchFieldSemantic.EnumAttribute(attributeCode) => s"enumAttributes.$attributeCode"
      case SearchFieldSemantic.BooleanAttribute(attributeCode) => s"booleanAttributes.$attributeCode"
      case SearchFieldSemantic.IntAttribute(attributeCode) => s"intAttributes.$attributeCode"
      case SearchFieldSemantic.DecimalAttribute(attributeCode) => s"bigDecimalAttributes.$attributeCode"
    }

  private def renderDistance(distance: VectorDistance): String =
    distance match {
      case VectorDistance.Cosine => "cosine"
      case VectorDistance.Dot => "dot"
      case VectorDistance.Euclidean => "euclidean"
    }

  private def sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256")
      .digest(value.getBytes(StandardCharsets.UTF_8))
      .map(byte => f"${byte & 0xff}%02x")
      .mkString
}
