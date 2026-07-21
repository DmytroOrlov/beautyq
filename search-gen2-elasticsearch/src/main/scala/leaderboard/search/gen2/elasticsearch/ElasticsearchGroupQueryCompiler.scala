package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.contract.*

import io.circe.Json

/** Framework-owned exact composite traversal size. It is part of the search compiler protocol, not a
  * domain tuning knob. A protocol bump is required if this wire behavior changes. */
private[elasticsearch] val GroupCompositePageSize: Int = 500

sealed trait ElasticsearchGroupQueryCompileError

object ElasticsearchGroupQueryCompileError {
  final case class UnsupportedGroupShape(id: GroupId, message: String) extends ElasticsearchGroupQueryCompileError
}

/** One immutable group-query plan. It carries the typed declaration and the already compiled main
  * execution query; group traversal never reconstructs constraints or geo scoring. */
final class CompiledElasticsearchGroupQuery[Document, Id] private[elasticsearch] (
  val request: GroupRequest[Document, ?],
  val executionQuery: Json,
  val identityField: SearchField[Document, Id],
) {
  def body(afterKey: Option[Json]): Json = ElasticsearchGroupQueryCompiler.body(this, afterKey)
}

object ElasticsearchGroupQueryCompiler {
  def compile[Document, Id](
    prepared: PreparedElasticsearchSearchRequest[Document, Id],
    request: GroupRequest[Document, ?],
  ): Either[ElasticsearchGroupQueryCompileError, CompiledElasticsearchGroupQuery[Document, Id]] =
    GroupRequest.validate(request) match {
      case Left(errors) => Left(ElasticsearchGroupQueryCompileError.UnsupportedGroupShape(request.id, errors.toVector.mkString("; ")))
      case Right(valid) => Right(new CompiledElasticsearchGroupQuery(valid, prepared.executionQuery, prepared.identityField))
    }

  private[elasticsearch] def body[Document, Id](
    compiled: CompiledElasticsearchGroupQuery[Document, Id],
    afterKey: Option[Json],
  ): Json = {
    val request = compiled.request
    val keyField = request.keyField
    val composite =
      Vector(
        "size" -> Json.fromInt(GroupCompositePageSize),
        "sources" -> Json.arr(Json.obj("group_key" -> Json.obj("terms" -> Json.obj("field" -> Json.fromString(keyField.path.value), "order" -> Json.fromString("asc"))))),
      ) ++ afterKey.map(value => "after" -> value).toVector

    val subAggregations =
      Vector("representative" -> representativeAggregation(request.representative, compiled.identityField)) ++
        request.metrics.collect {
          case metric: GroupMetricRequest.MinGeoDistance[Document] => metric.id.value -> geoMetricAggregation(metric)
        }

    Json.obj(
      "size" -> Json.fromInt(0),
      "track_total_hits" -> Json.fromBoolean(false),
      "query" -> compiled.executionQuery,
      "aggs" -> Json.obj(
        "gen2_group" -> Json.obj(
          "composite" -> Json.obj(composite*),
          "aggs" -> Json.obj(subAggregations*),
        )
      ),
    )
  }

  private def representativeAggregation[Document, Id](
    request: RepresentativeRequest[Document],
    identityField: SearchField[Document, Id],
  ): Json = {
    val source = request match {
      case RepresentativeRequest.IdentityOnly() => Json.fromBoolean(false)
      case RepresentativeRequest.Fields(fields) =>
        Json.obj("includes" -> Json.fromValues(fields.map(field => Json.fromString(field.path.value))))
    }
    Json.obj(
      "top_hits" -> Json.obj(
        "size" -> Json.fromInt(1),
        "_source" -> source,
        "track_scores" -> Json.fromBoolean(true),
        "sort" -> Json.fromValues(
          Vector(
            Json.obj("_score" -> Json.obj("order" -> Json.fromString("desc"))),
            Json.obj(identityField.path.value -> Json.obj("order" -> Json.fromString("asc"))),
          )
        ),
      )
    )
  }

  private def geoMetricAggregation[Document](
    metric: GroupMetricRequest.MinGeoDistance[Document]
  ): Json =
    Json.obj(
      "top_hits" -> Json.obj(
        "size" -> Json.fromInt(1),
        "_source" -> Json.fromBoolean(false),
        "sort" -> Json.fromValues(
          Vector(
            Json.obj(
              "_geo_distance" -> Json.obj(
                metric.field.path.value -> Json.obj(
                  "lat" -> Json.fromBigDecimal(metric.origin.lat),
                  "lon" -> Json.fromBigDecimal(metric.origin.lon),
                ),
                "order" -> Json.fromString("asc"),
                "unit" -> Json.fromString("m"),
                "distance_type" -> Json.fromString("arc"),
              )
            ),
          )
        ),
      )
    )
}
