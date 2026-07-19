package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.contract.*

import io.circe.{Json, JsonObject}

enum ElasticsearchGroupPrecision {
  case Exact
}

sealed trait ElasticsearchGroupValueAccessError

object ElasticsearchGroupValueAccessError {
  final case class FieldNotProjected(fieldId: FieldId) extends ElasticsearchGroupValueAccessError
  final case class MissingRequiredField(fieldId: FieldId) extends ElasticsearchGroupValueAccessError
  final case class WrongFieldHandle(fieldId: FieldId) extends ElasticsearchGroupValueAccessError
}

final case class ElasticsearchGroupMetricValue private[elasticsearch] (
  id: GroupMetricId,
  bestScore: Option[BigDecimal],
  minGeoDistanceMeters: Option[BigDecimal],
)

final class ElasticsearchGroupRepresentative[Document, Id] private[elasticsearch] (
  val id: Id,
  val source: JsonObject,
  val score: BigDecimal,
  private val projected: Vector[(SearchField[Document, ?], Option[Any])],
) {
  def optionalValue[A](field: SearchField[Document, A]): Either[ElasticsearchGroupValueAccessError, Option[A]] =
    projected.find(_._1 eq field) match {
      case None => Left(ElasticsearchGroupValueAccessError.FieldNotProjected(field.id))
      case Some((_, value)) => Right(value.map(_.asInstanceOf[A]))
    }

  def requiredValue[A](field: SearchField[Document, A]): Either[ElasticsearchGroupValueAccessError, A] =
    optionalValue(field).flatMap(_.toRight(ElasticsearchGroupValueAccessError.MissingRequiredField(field.id)))
}

final class ElasticsearchGroupBucket[Document, Id] private[elasticsearch] (
  private val keyField: SearchField[Document, ?],
  private val rawKey: Any,
  val canonicalKey: String,
  val matchingDocumentCount: Long,
  val representative: ElasticsearchGroupRepresentative[Document, Id],
  val metrics: Vector[ElasticsearchGroupMetricValue],
  private[elasticsearch] val encounterOrdinal: Long,
) {
  def key[A](field: SearchField[Document, A]): Either[ElasticsearchGroupValueAccessError, A] =
    if (keyField eq field) Right(rawKey.asInstanceOf[A])
    else Left(ElasticsearchGroupValueAccessError.WrongFieldHandle(field.id))

  def bestScore(metricId: GroupMetricId): Option[BigDecimal] =
    metrics.find(_.id == metricId).flatMap(_.bestScore)

  def minGeoDistanceMeters(metricId: GroupMetricId): Option[BigDecimal] =
    metrics.find(_.id == metricId).flatMap(_.minGeoDistanceMeters)
}

final class ElasticsearchGroupResult[Document, Id] private[elasticsearch] (
  val id: GroupId,
  private[elasticsearch] val keyField: SearchField[Document, ?],
  val buckets: Vector[ElasticsearchGroupBucket[Document, Id]],
  val precision: ElasticsearchGroupPrecision,
  val diagnostics: Vector[ElasticsearchResponseDiagnostics],
) {
  def typedFor[A](groupId: GroupId, field: SearchField[Document, A]): Option[ElasticsearchGroupResult[Document, Id]] =
    if (groupId == id && (field eq keyField)) Some(this) else None
}

private[elasticsearch] final case class DecodedElasticsearchGroupPage[Document, Id](
  buckets: Vector[ElasticsearchGroupBucket[Document, Id]],
  afterKey: Option[Json],
  diagnostics: ElasticsearchResponseDiagnostics,
)

sealed trait ElasticsearchGroupResponseError

object ElasticsearchGroupResponseError {
  final case class Malformed(groupId: GroupId, page: Int, message: String) extends ElasticsearchGroupResponseError
  final case class Partial(groupId: GroupId, page: Int, diagnostics: ElasticsearchResponseDiagnostics) extends ElasticsearchGroupResponseError
  final case class DuplicateKey(groupId: GroupId, page: Int, canonicalKey: String) extends ElasticsearchGroupResponseError
  final case class NonAdvancingAfterKey(groupId: GroupId, page: Int) extends ElasticsearchGroupResponseError
  final case class UnsupportedValue(groupId: GroupId, page: Int, message: String) extends ElasticsearchGroupResponseError
}

type ElasticsearchGroupResponseErrors = NonEmptyErrors[ElasticsearchGroupResponseError]

object ElasticsearchGroupResponseDecoder {
  def decode[Document, Id](
    compiled: CompiledElasticsearchGroupQuery[Document, Id],
    page: Int,
    response: Json,
    previousAfterKey: Option[Json],
    encounterOffset: Long,
  ): Either[ElasticsearchGroupResponseErrors, DecodedElasticsearchGroupPage[Document, Id]] =
    decodeOne(compiled, page, response, previousAfterKey, encounterOffset)
      .left
      .map(error => NonEmptyErrors.fromHead(error, Vector.empty))

  private def decodeOne[Document, Id](
    compiled: CompiledElasticsearchGroupQuery[Document, Id],
    page: Int,
    response: Json,
    previousAfterKey: Option[Json],
    encounterOffset: Long,
  ): Either[ElasticsearchGroupResponseError, DecodedElasticsearchGroupPage[Document, Id]] = {
    val groupId = compiled.request.id
    for {
      obj <- response.asObject.toRight(malformed(groupId, page, "expected top-level JSON object"))
      diagnostics <- decodeDiagnostics(groupId, page, obj)
      _ <- Either.cond(!diagnostics.timedOut && diagnostics.shardsFailed == 0, (), ElasticsearchGroupResponseError.Partial(groupId, page, diagnostics))
      aggs <- obj("aggregations").orElse(obj("aggs")).flatMap(_.asObject).toRight(malformed(groupId, page, "missing or invalid aggregations"))
      groupAgg <- aggs("gen2_group").flatMap(_.asObject).toRight(malformed(groupId, page, "missing gen2_group aggregation"))
      bucketsJson <- groupAgg("buckets").flatMap(_.asArray).map(_.toVector).toRight(malformed(groupId, page, "missing or invalid buckets"))
      buckets <- sequence(bucketsJson.zipWithIndex.map { case (json, index) => decodeBucket(compiled, groupId, page, json, index, encounterOffset + index) })
      afterKey <- decodeAfterKey(groupId, page, groupAgg)
      _ <- previousAfterKey match {
             case Some(previous) if afterKey.contains(previous) => Left(ElasticsearchGroupResponseError.NonAdvancingAfterKey(groupId, page))
             case _ => Right(())
           }
    } yield DecodedElasticsearchGroupPage(buckets, afterKey, diagnostics)
  }

  private def decodeBucket[Document, Id](
    compiled: CompiledElasticsearchGroupQuery[Document, Id],
    groupId: GroupId,
    page: Int,
    json: Json,
    bucketIndex: Int,
    encounterOrdinal: Long,
  ): Either[ElasticsearchGroupResponseError, ElasticsearchGroupBucket[Document, Id]] =
    for {
      bucket <- json.asObject.toRight(malformed(groupId, page, "bucket[" + bucketIndex + "] must be an object"))
      keyObj <- bucket("key").flatMap(_.asObject).toRight(malformed(groupId, page, "bucket[" + bucketIndex + "].key must be an object"))
      keyJson <- keyObj("group_key").toRight(malformed(groupId, page, "bucket[" + bucketIndex + "].key.group_key is missing"))
      canonical <- canonicalKey(keyJson, compiled.request.keyField.kind).toRight(malformed(groupId, page, "bucket[" + bucketIndex + "] has an invalid group key"))
      key <- compiled.request.keyField.codec.decodeCanonical(canonical).left.map(error => unsupported(groupId, page, "group key '" + canonical + "' failed to decode: " + error.message))
      count <- bucket("doc_count").flatMap(_.asNumber).flatMap(_.toLong).toRight(malformed(groupId, page, "bucket[" + bucketIndex + "].doc_count is invalid"))
      _ <- Either.cond(count >= 0L, (), malformed(groupId, page, "bucket[" + bucketIndex + "].doc_count is negative"))
      representative <- decodeRepresentative(compiled, groupId, page, bucket, bucketIndex)
      metrics <- decodeMetrics(compiled, groupId, page, bucket, representative.score)
    } yield new ElasticsearchGroupBucket(compiled.request.keyField, key, canonical, count, representative, metrics, encounterOrdinal)

  private def decodeRepresentative[Document, Id](
    compiled: CompiledElasticsearchGroupQuery[Document, Id],
    groupId: GroupId,
    page: Int,
    bucket: JsonObject,
    bucketIndex: Int,
  ): Either[ElasticsearchGroupResponseError, ElasticsearchGroupRepresentative[Document, Id]] =
    for {
      aggregations <- bucket("representative").flatMap(_.asObject).toRight(malformed(groupId, page, "bucket[" + bucketIndex + "] representative is missing"))
      hit <- aggregations("hits").flatMap(_.asObject).flatMap(_("hits")).flatMap(_.asArray).flatMap(_.headOption).flatMap(_.asObject).toRight(malformed(groupId, page, "bucket[" + bucketIndex + "] representative hit is missing"))
      idCanonical <- hit("_id").flatMap(_.asString).toRight(malformed(groupId, page, "bucket[" + bucketIndex + "] representative _id is invalid"))
      id <- compiled.identityField.codec.decodeCanonical(idCanonical).left.map(error => unsupported(groupId, page, "representative id failed to decode: " + error.message))
      score <- hit("_score").flatMap(_.asNumber).flatMap(_.toBigDecimal).toRight(malformed(groupId, page, "bucket[" + bucketIndex + "] representative score is invalid"))
      source <- decodeSource(compiled.request.representative, groupId, page, hit, bucketIndex)
      projected <- decodeProjected(compiled.request.representative, source, groupId, page, bucketIndex)
    } yield new ElasticsearchGroupRepresentative(id, source.getOrElse(JsonObject.empty), score, projected)

  private def decodeSource[Document](
    request: RepresentativeRequest[Document],
    groupId: GroupId,
    page: Int,
    hit: JsonObject,
    bucketIndex: Int,
  ): Either[ElasticsearchGroupResponseError, Option[JsonObject]] =
    request match {
      case RepresentativeRequest.IdentityOnly() => Right(None)
      case RepresentativeRequest.Fields(_) =>
        hit("_source").flatMap(_.asObject).toRight(malformed(groupId, page, "bucket[" + bucketIndex + "] representative _source must be an object")).map(Some(_))
    }

  private def decodeProjected[Document](
    request: RepresentativeRequest[Document],
    source: Option[JsonObject],
    groupId: GroupId,
    page: Int,
    bucketIndex: Int,
  ): Either[ElasticsearchGroupResponseError, Vector[(SearchField[Document, ?], Option[Any])]] =
    request match {
      case RepresentativeRequest.IdentityOnly() => Right(Vector.empty)
      case RepresentativeRequest.Fields(fields) =>
        val sourceObject = source.getOrElse(JsonObject.empty)
        sequence(fields.map { field =>
          jsonAtPath(sourceObject, field.path.value) match {
            case None if field.required => Left(malformed(groupId, page, "bucket[" + bucketIndex + "] required representative field '" + field.path.value + "' is missing"))
            case None => Right(field -> None)
            case Some(value) =>
              canonicalFromJson(field.kind, value)
                .toRight(malformed(groupId, page, "bucket[" + bucketIndex + "] representative field '" + field.path.value + "' has an invalid JSON value"))
                .flatMap { canonical =>
                  field.codec.decodeCanonical(canonical)
                    .left.map(error => unsupported(groupId, page, "representative field '" + field.path.value + "' failed to decode: " + error.message))
                    .map(decoded => field -> Some(decoded))
                }
          }
        })
    }

  private def decodeMetrics[Document, Id](
    compiled: CompiledElasticsearchGroupQuery[Document, Id],
    groupId: GroupId,
    page: Int,
    bucket: JsonObject,
    representativeScore: BigDecimal,
  ): Either[ElasticsearchGroupResponseError, Vector[ElasticsearchGroupMetricValue]] =
    sequence(compiled.request.metrics.map {
      case metric: GroupMetricRequest.BestScore[Document] =>
        Right(ElasticsearchGroupMetricValue(metric.id, Some(representativeScore), None))
      case metric: GroupMetricRequest.MinGeoDistance[Document] =>
        for {
          metricAgg <- bucket(metric.id.value).flatMap(_.asObject).toRight(malformed(groupId, page, "metric '" + metric.id.value + "' is missing"))
          hit <- metricAgg("hits").flatMap(_.asObject).flatMap(_("hits")).flatMap(_.asArray).flatMap(_.headOption).flatMap(_.asObject).toRight(malformed(groupId, page, "metric '" + metric.id.value + "' hit is missing"))
          distance <- hit("sort").flatMap(_.asArray).flatMap(_.headOption).flatMap(_.asNumber).flatMap(_.toBigDecimal).toRight(malformed(groupId, page, "metric '" + metric.id.value + "' distance is invalid"))
        } yield ElasticsearchGroupMetricValue(metric.id, None, Some(distance))
    })

  private def decodeDiagnostics[Document](
    groupId: GroupId,
    page: Int,
    obj: JsonObject,
  ): Either[ElasticsearchGroupResponseError, ElasticsearchResponseDiagnostics] =
    for {
      timedOut <- obj("timed_out").flatMap(_.asBoolean).toRight(malformed(groupId, page, "timed_out is missing or invalid"))
      shards <- obj("_shards").flatMap(_.asObject).toRight(malformed(groupId, page, "_shards is missing or invalid"))
      total <- shards("total").flatMap(_.asNumber).flatMap(_.toInt).toRight(malformed(groupId, page, "_shards.total is invalid"))
      successful <- shards("successful").flatMap(_.asNumber).flatMap(_.toInt).toRight(malformed(groupId, page, "_shards.successful is invalid"))
      failed <- shards("failed").flatMap(_.asNumber).flatMap(_.toInt).toRight(malformed(groupId, page, "_shards.failed is invalid"))
      _ <- Either.cond(total >= 0 && successful >= 0 && failed >= 0 && successful <= total && failed <= total, (), malformed(groupId, page, "_shards counts are inconsistent"))
    } yield ElasticsearchResponseDiagnostics(timedOut, total, successful, failed)

  private def decodeAfterKey[Document](
    groupId: GroupId,
    page: Int,
    groupAgg: JsonObject,
  ): Either[ElasticsearchGroupResponseError, Option[Json]] =
    groupAgg("after_key") match {
      case None => Right(None)
      case Some(value) => value.asObject.toRight(malformed(groupId, page, "after_key must be an object")).map(json => Some(Json.fromJsonObject(json)))
    }

  private def canonicalKey(value: Json, kind: SearchFieldKind): Option[String] =
    kind match {
      case SearchFieldKind.Keyword | SearchFieldKind.Text | SearchFieldKind.DateTime => value.asString
      case SearchFieldKind.Integer | SearchFieldKind.Long | SearchFieldKind.Decimal => value.asNumber.map(_.toString)
      case SearchFieldKind.Boolean => value.asBoolean.map(_.toString)
      case SearchFieldKind.GeoPoint => None
    }

  private def canonicalFromJson(kind: SearchFieldKind, value: Json): Option[String] =
    canonicalKey(value, kind).orElse {
      kind match {
        case SearchFieldKind.GeoPoint =>
          for {
            obj <- value.asObject
            lat <- obj("lat").flatMap(_.asNumber).flatMap(_.toBigDecimal)
            lon <- obj("lon").flatMap(_.asNumber).flatMap(_.toBigDecimal)
          } yield s"$lat,$lon"
        case _ => None
      }
    }

  private def jsonAtPath(source: JsonObject, path: String): Option[Json] =
    path.split("\\.").toVector.foldLeft[Option[Json]](Some(Json.fromJsonObject(source))) {
      case (Some(current), segment) => current.asObject.flatMap(_(segment))
      case (None, _) => None
    }

  private def sequence[A](values: Vector[Either[ElasticsearchGroupResponseError, A]]): Either[ElasticsearchGroupResponseError, Vector[A]] =
    values.foldLeft[Either[ElasticsearchGroupResponseError, Vector[A]]](Right(Vector.empty)) { (acc, next) =>
      acc.flatMap(done => next.map(done :+ _))
    }

  private def malformed(groupId: GroupId, page: Int, message: String): ElasticsearchGroupResponseError =
    ElasticsearchGroupResponseError.Malformed(groupId, page, message)

  private def unsupported(groupId: GroupId, page: Int, message: String): ElasticsearchGroupResponseError =
    ElasticsearchGroupResponseError.UnsupportedValue(groupId, page, message)
}
