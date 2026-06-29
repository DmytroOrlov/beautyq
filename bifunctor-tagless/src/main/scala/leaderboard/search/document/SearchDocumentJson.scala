package leaderboard.search.document

import io.circe.syntax.*
import io.circe.{Json, JsonObject}
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.{SearchDocumentSpec, SearchField, SearchValue}

object SearchDocumentJson {
  def encodeValue(value: SearchValue): Json =
    value match {
      case SearchValue.Text(raw)     => Json.fromString(raw)
      case SearchValue.Keyword(raw)  => Json.fromString(raw)
      case SearchValue.Integer(raw)  => Json.fromInt(raw)
      case SearchValue.Decimal(raw)  => raw.asJson
      case SearchValue.Boolean(raw)  => Json.fromBoolean(raw)
      case SearchValue.GeoPoint(raw) => Json.obj("lat" -> raw.lat.asJson, "lon" -> raw.lon.asJson)
    }

  def sourceJson[A](
    documentSpec: SearchDocumentSpec[A],
    document: A,
  ): Json = {
    val fields = documentSpec.fields.flatMap { field =>
      field.extract(document).map(value => field.path -> value)
    }

    Json.fromJsonObject(
      fields.foldLeft(JsonObject.empty) {
        case (acc, (path, value)) =>
          mergeObjects(acc, propertyTree(path.split('.').toList, encodeValue(value)))
      }
    )
  }

  def payload[A](
    documentSpec: SearchDocumentSpec[A],
    fields: List[SearchField[A]],
    document: A,
  ): Either[QueryFailure, Map[String, Json]] =
    fields.foldRight[Either[QueryFailure, Unit]](Right(())) {
      (field, acc) =>
        for {
          _ <- acc
          _ <- documentSpec.fieldByPath(field.path)
        } yield ()
    }.map { _ =>
      fields.foldLeft(Map.empty[String, Json]) {
        case (acc, field) =>
          field.extract(document) match {
            case Some(value) => acc.updated(field.path, encodeValue(value))
            case None        => acc
          }
      }
    }

  private[search] def payloadByPaths[A](
    documentSpec: SearchDocumentSpec[A],
    fieldPaths: List[String],
    document: A,
  ): Either[QueryFailure, Map[String, Json]] =
    payloadFields(documentSpec, fieldPaths).flatMap(payload(documentSpec, _, document))

  private def payloadFields[A](
    documentSpec: SearchDocumentSpec[A],
    fieldPaths: List[String],
  ): Either[QueryFailure, List[SearchField[A]]] =
    fieldPaths.foldRight[Either[QueryFailure, List[SearchField[A]]]](Right(Nil)) {
      (path, acc) =>
        for {
          tail  <- acc
          field <- documentSpec.fieldByPath(path)
        } yield field :: tail
    }

  private def propertyTree(parts: List[String], value: Json): JsonObject =
    parts match {
      case head :: Nil  => JsonObject.singleton(head, value)
      case head :: tail => JsonObject.singleton(head, Json.fromJsonObject(propertyTree(tail, value)))
      case Nil          => JsonObject.empty
    }

  private def mergeObjects(left: JsonObject, right: JsonObject): JsonObject =
    right.toIterable.foldLeft(left) {
      case (acc, (key, value)) =>
        acc(key) match {
          case Some(existing) if existing.isObject && value.isObject =>
            (existing.asObject, value.asObject) match {
              case (Some(existingObject), Some(valueObject)) =>
                acc.add(key, Json.fromJsonObject(mergeObjects(existingObject, valueObject)))
              case _ =>
                acc.add(key, value)
            }
          case _ =>
            acc.add(key, value)
        }
    }
}

final case class SearchDocumentPayloadSpec[A](
  documentSpec: SearchDocumentSpec[A],
  fields: List[SearchField[A]],
) {
  def fieldPaths: List[String] = fields.map(_.path)

  def payload(document: A): Either[QueryFailure, Map[String, Json]] =
    SearchDocumentJson.payload(documentSpec, fields, document)
}
