package leaderboard.search.elasticsearch

import io.circe.{Json, JsonObject}
import leaderboard.search.dsl.{BeautySearchSpec, SearchField, SearchFieldKind}
import leaderboard.search.document.VariantSearchDocument

object ElasticsearchMappingInterpreter {
  def mapping(spec: BeautySearchSpec): Json =
    Json.obj(
      "settings" -> Json.obj(
        "index" -> Json.obj(
          "number_of_shards" -> Json.fromInt(1),
          "number_of_replicas" -> Json.fromInt(0),
        )
      ),
      "mappings" -> Json.obj(
        "properties" -> Json.fromJsonObject(properties(spec.variantDocument.fields))
      ),
    )

  private def properties(fields: List[SearchField[VariantSearchDocument]]): JsonObject =
    fields.foldLeft(JsonObject.empty) { (acc, field) =>
      mergeObjects(acc, propertyTree(field.path.split('.').toList, fieldMapping(field)))
    }

  private def fieldMapping(field: SearchField[VariantSearchDocument]): Json = {
    val base = field.kind match {
      case SearchFieldKind.Text =>
        Json.obj("type" -> Json.fromString("text"))
      case SearchFieldKind.Keyword =>
        Json.obj("type" -> Json.fromString("keyword"))
      case SearchFieldKind.Integer =>
        Json.obj("type" -> Json.fromString("integer"))
      case SearchFieldKind.Decimal =>
        Json.obj("type" -> Json.fromString("double"))
      case SearchFieldKind.Boolean =>
        Json.obj("type" -> Json.fromString("boolean"))
      case SearchFieldKind.GeoPoint =>
        Json.obj("type" -> Json.fromString("geo_point"))
    }

    field.analyzer match {
      case Some(analyzer) =>
        base.deepMerge(Json.obj("analyzer" -> Json.fromString(analyzer)))
      case None =>
        base
    }
  }

  private def propertyTree(parts: List[String], value: Json): JsonObject =
    parts match {
      case head :: Nil =>
        JsonObject.singleton(head, value)
      case head :: tail =>
        JsonObject.singleton(head, Json.obj("properties" -> Json.fromJsonObject(propertyTree(tail, value))))
      case Nil =>
        JsonObject.empty
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
