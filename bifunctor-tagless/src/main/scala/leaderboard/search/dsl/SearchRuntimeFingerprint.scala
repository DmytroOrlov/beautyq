package leaderboard.search.dsl

import io.circe.syntax.*
import io.circe.{Json, JsonObject}
import leaderboard.search.document.{SearchDocumentJson, SearchDocumentPayloadSpec}

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** Generic, schema/runtime-derived fingerprint inputs for a managed search bootstrap.
  *
  * The catalog documents are serialized through [[SearchDocumentJson.sourceJson]] from the
  * [[SearchDocumentSpec]] fields, and the runtime/schema sections are derived from a
  * [[SearchRuntimeSpec]] (document fields, query schema, request/facet/carousel config, payload field
  * paths, embedding source field paths, and vector config). Nothing here is specific to any concrete
  * document domain; backend-specific or externally-supplied inputs (e.g. an Elasticsearch mapping or an
  * embedding endpoint label) are layered in through `extraSections`.
  *
  * The string renderers below render typed metadata (`SearchFieldKind`, `SearchFieldSemantic`,
  * `VectorDistance`, facet modes/range buckets, request/ranking/vector/embedding config). They are not a
  * document-field serialization; the document body is always derived generically from the document spec.
  */
object SearchRuntimeFingerprint {

  /** Build deterministic, canonical fingerprint inputs from a runtime spec, a catalog source label, the
    * catalog documents, and any extra/backend sections. Documents are sorted by `documentSpec.id` so the
    * input order does not affect the result.
    */
  def inputs[A](
    fingerprintVersion: String,
    catalogSource: String,
    documents: List[A],
    runtimeSpec: SearchRuntimeSpec[A],
    extraSections: JsonObject,
  ): Json = {
    val base = JsonObject(
      "fingerprintVersion" -> Json.fromString(fingerprintVersion),
      "catalog" -> catalogJson(catalogSource, documents, runtimeSpec.documentSpec),
      "runtime" -> runtimeJson(runtimeSpec),
    )
    val combined = extraSections.toIterable.foldLeft(base) {
      case (acc, (key, value)) => acc.add(key, value)
    }
    canonicalJson(Json.fromJsonObject(combined))
  }

  /** Stable SHA-256 hash over the canonical JSON inputs. */
  def value(inputs: Json): String =
    sha256Hex(inputs.noSpaces)

  /** Recursively sort object keys so the JSON is deterministic regardless of construction order. */
  def canonicalJson(json: Json): Json =
    json.arrayOrObject(
      json,
      values => Json.arr(values.map(canonicalJson): _*),
      obj =>
        Json.fromJsonObject(JsonObject.fromIterable(obj.toIterable.toList.sortBy(_._1).map {
          case (key, value) => key -> canonicalJson(value)
        })),
    )

  private def catalogJson[A](
    source: String,
    documents: List[A],
    documentSpec: SearchDocumentSpec[A],
  ): Json =
    Json.obj(
      "source" -> Json.fromString(source),
      "documents" -> Json.arr(
        documents.sortBy(documentSpec.id).map(document => SearchDocumentJson.sourceJson(documentSpec, document)): _*
      ),
    )

  private def runtimeJson[A](runtimeSpec: SearchRuntimeSpec[A]): Json =
    Json.obj(
      "document" -> Json.obj(
        "indexName" -> Json.fromString(runtimeSpec.documentSpec.indexName),
        "fields" -> Json.arr(runtimeSpec.documentSpec.fields.map(fieldJson): _*),
      ),
      "querySchema" -> querySchemaJson(runtimeSpec.querySchema),
      "request" -> requestJson(runtimeSpec.requestSpec),
      "facets" -> facetSpecJson(runtimeSpec.facetSpec),
      "carousel" -> carouselJson(runtimeSpec.carouselSpec),
      "payloads" -> payloadsJson(runtimeSpec.payloadSpecs),
      "embedding" -> runtimeSpec.embeddingSpec.map(embeddingJson).asJson,
      "vector" -> runtimeSpec.vectorSearchSpec.map(vectorJson).asJson,
    )

  private def fieldJson[A](field: SearchField[A]): Json =
    Json.obj(
      "path" -> Json.fromString(field.path),
      "kind" -> Json.fromString(renderFieldKind(field.kind)),
      "semantic" -> field.semantic.map(renderSemantic).asJson,
      "searchable" -> Json.fromBoolean(field.searchable),
      "filterable" -> Json.fromBoolean(field.filterable),
      "facetable" -> Json.fromBoolean(field.facetable),
      "sortable" -> Json.fromBoolean(field.sortable),
      "boost" -> field.boost.asJson,
      "analyzer" -> field.analyzer.asJson,
    )

  /** Static schema-owned query field mappings. Dynamic query functions (enum/boolean/int/decimal
    * attribute lookups) are not introspectable and are already represented through document field
    * semantics, facets, and payload fields, so they are deliberately not rendered here.
    */
  private def querySchemaJson[A](schema: SearchQuerySchema[A]): Json =
    Json.obj(
      "serviceName" -> Json.fromString(schema.serviceName.path),
      "categoryName" -> Json.fromString(schema.categoryName.path),
      "priceFrom" -> Json.fromString(schema.priceFrom.path),
      "durationMin" -> Json.fromString(schema.durationMin.path),
      "location" -> Json.fromString(schema.location.path),
    )

  private def requestJson(request: SearchRequestSpec): Json =
    Json.obj(
      "hitWindowSize" -> request.hitWindowSize.asJson,
      "textOperator" -> Json.fromString(request.textOperator.value),
      "aggregationSize" -> request.aggregationSize.asJson,
      "geoDistanceScale" -> Json.fromString(request.geoDistanceScale),
      "geoDistanceOffset" -> Json.fromString(request.geoDistanceOffset),
      "geoDistanceDecay" -> request.geoDistanceDecay.asJson,
    )

  private def facetSpecJson[A](facetSpec: FacetSpec[A]): Json =
    Json.obj(
      "enabled" -> Json.fromBoolean(facetSpec.enabled),
      "inferredFilterDominanceThreshold" -> facetSpec.inferredFilterDominanceThreshold.asJson,
      "inferredFilterMinCount" -> facetSpec.inferredFilterMinCount.asJson,
      "fields" -> Json.arr(facetSpec.fields.map(facetFieldJson): _*),
    )

  private def facetFieldJson[A](facetField: FacetField[A]): Json =
    Json.obj(
      "path" -> Json.fromString(facetField.path),
      "mode" -> renderFacetMode(facetField.mode),
      "limit" -> facetField.limit.asJson,
      "inferable" -> Json.fromBoolean(facetField.inferable),
    )

  private def renderFacetMode(mode: FacetFieldMode): Json =
    mode match {
      case FacetFieldMode.Terms =>
        Json.obj("type" -> Json.fromString("terms"))
      case FacetFieldMode.Ranges(buckets) =>
        Json.obj(
          "type" -> Json.fromString("ranges"),
          "buckets" -> Json.arr(buckets.map(renderRangeBucket): _*),
        )
    }

  private def renderRangeBucket(bucket: FacetRangeBucket): Json =
    Json.obj(
      "key" -> Json.fromString(bucket.key),
      "min" -> bucket.min.asJson,
      "max" -> bucket.max.asJson,
    )

  private def carouselJson[A](carousel: CarouselSpec[A]): Json =
    Json.obj(
      "variantSize" -> carousel.variantSize.asJson,
      "providerSize" -> carousel.providerSize.asJson,
      "serviceIntentSize" -> carousel.serviceIntentSize.asJson,
      "providerGroupField" -> Json.fromString(carousel.providerGroupField.path),
      "serviceIntentGroupField" -> Json.fromString(carousel.serviceIntentGroupField.path),
      "ranking" -> rankingJson(carousel.ranking),
    )

  private def rankingJson(ranking: RankingSpec): Json =
    Json.obj(
      "textScoreWeight" -> ranking.textScoreWeight.asJson,
      "serviceBoostWeight" -> ranking.serviceBoostWeight.asJson,
      "attributeBoostWeight" -> ranking.attributeBoostWeight.asJson,
      "providerDistanceWeight" -> ranking.providerDistanceWeight.asJson,
      "providerMatchingVariantCountWeight" -> ranking.providerMatchingVariantCountWeight.asJson,
    )

  private def payloadsJson[A](payloadSpecs: Map[String, SearchDocumentPayloadSpec[A]]): Json =
    Json.fromJsonObject(
      JsonObject.fromIterable(
        payloadSpecs.toList.sortBy(_._1).map {
          case (name, payloadSpec) =>
            name -> Json.arr(payloadSpec.fieldPaths.map(Json.fromString): _*)
        }
      )
    )

  private def embeddingJson[A](embeddingSpec: EmbeddingSpec[A]): Json =
    Json.obj(
      "vectorName" -> Json.fromString(embeddingSpec.vectorName),
      "modelName" -> Json.fromString(embeddingSpec.modelName),
      "dimension" -> embeddingSpec.dimension.asJson,
      "distance" -> Json.fromString(renderDistance(embeddingSpec.distance)),
      "sourceTextFieldPaths" -> Json.arr(embeddingSpec.sourceTextFieldPaths.map(Json.fromString): _*),
    )

  private def vectorJson(vectorSearchSpec: VectorSearchSpec): Json =
    Json.obj(
      "collectionName" -> Json.fromString(vectorSearchSpec.collectionName),
      "vectorName" -> Json.fromString(vectorSearchSpec.vectorName),
      "topK" -> vectorSearchSpec.topK.asJson,
      "scoreThreshold" -> vectorSearchSpec.scoreThreshold.asJson,
    )

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
