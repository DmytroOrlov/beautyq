package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.contract.*

import io.circe.{Json, JsonObject}

/** The compiled Elasticsearch mapping body (the `properties` object and its nested field definitions) for
  * one [[ElasticsearchIndexPolicy]]'s bound declaration. Carries no index-creation setting (shards,
  * replicas, aliases, credentials, lifecycle); those remain later transport/lifecycle concerns. `json` is
  * exposed read-only for that future transport layer to embed in an actual create-index request body. */
final case class ElasticsearchMapping(json: Json)

final case class ElasticsearchMappingError(conflict: ElasticsearchPathConflict)

/** Pure, declaration-driven Elasticsearch mapping compilation: every stored field and its dotted path
  * comes from [[SearchDocumentDeclaration.allFields]], in declaration order - never a second, parallel
  * field inventory. Analyzer choice for a `Text` field comes only from the bound
  * [[ElasticsearchIndexPolicy]]'s own `textFields` assignment, never derived from `FieldSemantic`, path,
  * field name or enum inventory.
  */
object ElasticsearchMappingCompiler {

  def compile[Document, Id](policy: ElasticsearchIndexPolicy[Document, Id]): Either[ElasticsearchMappingError, ElasticsearchMapping] = {
    val entries = policy.declaration.allFields.map(field => (field.id, field.path, fieldMapping(field, policy)))

    ElasticsearchDottedPathTree.build(entries, wrapChild, unwrapChild) match {
      case Left(conflict)      => Left(ElasticsearchMappingError(conflict))
      case Right(propertiesObj) => Right(ElasticsearchMapping(Json.obj("properties" -> Json.fromJsonObject(propertiesObj))))
    }
  }

  private def wrapChild(child: JsonObject): Json = Json.obj("properties" -> Json.fromJsonObject(child))

  private def unwrapChild(value: Json): Option[JsonObject] =
    value.asObject.flatMap(_.apply("properties")).flatMap(_.asObject)

  private def fieldMapping[Document, Id](field: SearchField[Document, ?], policy: ElasticsearchIndexPolicy[Document, Id]): Json =
    field.kind match {
      case SearchFieldKind.Keyword => Json.obj("type" -> Json.fromString("keyword"))
      case SearchFieldKind.Text =>
        // Only searchable Text fields are ever assigned an analyzer (ElasticsearchIndexPolicy's own
        // validation requires exactly one for each); a non-searchable Text field simply carries no
        // "analyzer" key, letting Elasticsearch apply its own default rather than this module inventing
        // one from field semantic/name/path.
        policy.analyzerOf(field) match {
          case Some(analyzer) => Json.obj("type" -> Json.fromString("text"), "analyzer" -> Json.fromString(analyzer.value))
          case None            => Json.obj("type" -> Json.fromString("text"))
        }
      case SearchFieldKind.Integer  => Json.obj("type" -> Json.fromString("integer"))
      case SearchFieldKind.Long     => Json.obj("type" -> Json.fromString("long"))
      case SearchFieldKind.Decimal  => Json.obj("type" -> Json.fromString("double"))
      case SearchFieldKind.Boolean  => Json.obj("type" -> Json.fromString("boolean"))
      case SearchFieldKind.DateTime => Json.obj("type" -> Json.fromString("date"), "format" -> Json.fromString("strict_date_optional_time"))
      case SearchFieldKind.GeoPoint => Json.obj("type" -> Json.fromString("geo_point"))
    }
}
