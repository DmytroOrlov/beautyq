package leaderboard.search.gen2.qdrant

import io.circe.{Json, JsonObject}
import leaderboard.search.gen2.contract.*

final case class QdrantEncodedField(
  id: FieldId,
  path: FieldPath,
  kind: SearchFieldKind,
  canonical: String,
  json: Json,
)

sealed trait QdrantPayloadError
object QdrantPayloadError {
  final case class ValueEncoding(fieldId: FieldId, error: SearchValueDecodeError) extends QdrantPayloadError
  final case class PathCollision(path: FieldPath) extends QdrantPayloadError
  final case class RequiredValueMissing(fieldId: FieldId, path: FieldPath) extends QdrantPayloadError
}

/** One scalar conversion path for Qdrant payloads and filter values. */
object QdrantScalarCompiler {
  def toJson(kind: SearchFieldKind, canonical: String): Either[SearchValueDecodeError, Json] = kind match {
    case SearchFieldKind.Keyword | SearchFieldKind.Text => Right(Json.fromString(canonical))
    case SearchFieldKind.Integer => SearchValueCodec.int.decodeCanonical(canonical).map(Json.fromInt)
    case SearchFieldKind.Long    => SearchValueCodec.long.decodeCanonical(canonical).map(Json.fromLong)
    case SearchFieldKind.Decimal => SearchValueCodec.bigDecimal.decodeCanonical(canonical).map(Json.fromBigDecimal)
    case SearchFieldKind.Boolean => SearchValueCodec.boolean.decodeCanonical(canonical).map(Json.fromBoolean)
    case SearchFieldKind.DateTime => SearchValueCodec.instant.decodeCanonical(canonical).map(_ => Json.fromString(canonical))
    case SearchFieldKind.GeoPoint =>
      SearchValueCodec.geoPoint.decodeCanonical(canonical).map(point => Json.obj(
        "lat" -> Json.fromBigDecimal(point.lat),
        "lon" -> Json.fromBigDecimal(point.lon),
      ))
  }
}

/** Encodes declaration-owned values into nested Qdrant payload JSON. The field codec is the only
  * logical value authority; backend kind conversion happens once through [[QdrantScalarCompiler]]. */
object QdrantPayloadCompiler {
  def compile[Document, Id](
    policy: QdrantPolicy[Document, Id],
    document: Document,
  ): Either[QdrantPayloadError, Json] =
    policy.payloadFields.foldLeft[Either[QdrantPayloadError, JsonObject]](Right(JsonObject.empty)) {
      case (acc, field) =>
        acc.flatMap { current =>
          extractCanonical(field, document).flatMap {
            case Some(extracted) => insert(current, extracted.path.value.split("\\.", -1).toVector, extracted.json, extracted.path)
            case None if field.required => Left(QdrantPayloadError.RequiredValueMissing(field.id, field.path))
            case None => Right(current)
          }
        }
    }.map(Json.fromJsonObject)

  def encodeCanonical[Document](
    field: SearchField[Document, ?],
    canonical: String,
  ): Either[QdrantPayloadError, Json] =
    QdrantScalarCompiler.toJson(field.kind, canonical).left.map(QdrantPayloadError.ValueEncoding(field.id, _))

  private def extractCanonical[Document, Value](
    field: SearchField[Document, Value],
    document: Document,
  ): Either[QdrantPayloadError, Option[QdrantEncodedField]] =
    field.extract(document) match {
      case None => Right(None)
      case Some(value) =>
        val canonical = field.codec.encodeCanonical(value)
        QdrantScalarCompiler.toJson(field.kind, canonical) match {
          case Right(valueJson) => Right(Some(QdrantEncodedField(field.id, field.path, field.kind, canonical, valueJson)))
          case Left(error)      => Left(QdrantPayloadError.ValueEncoding(field.id, error))
        }
    }

  private def insert(
    current: JsonObject,
    segments: Vector[String],
    value: Json,
    path: FieldPath,
  ): Either[QdrantPayloadError, JsonObject] = segments.toList match {
    case Nil => Right(current)
    case head :: tail =>
      if (tail.isEmpty) {
        current(head) match {
          case Some(existing) if existing != value => Left(QdrantPayloadError.PathCollision(path))
          case _                                  => Right(current.add(head, value))
        }
      } else {
        current(head) match {
          case Some(existing) =>
            existing.asObject match {
              case Some(existingObject) => insert(existingObject, tail.toVector, value, path).map(updated => current.add(head, Json.fromJsonObject(updated)))
              case None                 => Left(QdrantPayloadError.PathCollision(path))
            }
          case None =>
            insert(JsonObject.empty, tail.toVector, value, path).map(updated => current.add(head, Json.fromJsonObject(updated)))
          }
      }
  }
}
