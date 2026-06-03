package leaderboard.search.elasticsearch

import io.circe.syntax.*
import io.circe.{Json, JsonObject}
import leaderboard.search.dsl.{BeautySearchSpec, SearchValue}
import leaderboard.search.document.VariantSearchDocument

object ElasticsearchIngestionInterpreter {
  def bulkPayload(
    spec: BeautySearchSpec,
    documents: List[VariantSearchDocument],
  ): String =
    documents.map { document =>
      val action = Json.obj(
        "index" -> Json.obj(
          "_index" -> Json.fromString(spec.variantDocument.indexName),
          "_id" -> Json.fromString(spec.variantDocument.id(document)),
        )
      ).noSpaces
      val source = sourceJson(spec, document).noSpaces
      s"$action\n$source"
    }.mkString("\n", "\n", "\n")

  def sourceJson(
    spec: BeautySearchSpec,
    document: VariantSearchDocument,
  ): Json = {
    val fields = spec.variantDocument.fields.flatMap { field =>
      field.extract(document).map(value => field.path -> value)
    }

    Json.fromJsonObject(
      fields.foldLeft(JsonObject.empty) {
        case (acc, (path, value)) =>
          mergeObjects(acc, propertyTree(path.split('.').toList, encodeValue(value)))
      }
    )
  }

  private def encodeValue(value: SearchValue): Json =
    value match {
      case SearchValue.Text(raw) => Json.fromString(raw)
      case SearchValue.Keyword(raw) => Json.fromString(raw)
      case SearchValue.Integer(raw) => Json.fromInt(raw)
      case SearchValue.Decimal(raw) => raw.asJson
      case SearchValue.Boolean(raw) => Json.fromBoolean(raw)
      case SearchValue.GeoPoint(raw) => Json.obj("lat" -> raw.lat.asJson, "lon" -> raw.lon.asJson)
    }

  private def propertyTree(parts: List[String], value: Json): JsonObject =
    parts match {
      case head :: Nil => JsonObject.singleton(head, value)
      case head :: tail => JsonObject.singleton(head, Json.fromJsonObject(propertyTree(tail, value)))
      case Nil => JsonObject.empty
    }

  private def mergeObjects(left: JsonObject, right: JsonObject): JsonObject =
    right.toIterable.foldLeft(left) {
      case (acc, (key, value)) =>
        acc(key) match {
          case Some(existing) if existing.isObject && value.isObject =>
            acc.add(key, Json.fromJsonObject(mergeObjects(existing.asObject.get, value.asObject.get)))
          case _ =>
            acc.add(key, value)
        }
    }
}
