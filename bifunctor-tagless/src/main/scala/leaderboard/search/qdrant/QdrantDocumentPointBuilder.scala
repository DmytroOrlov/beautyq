package leaderboard.search.qdrant

import io.circe.Json
import leaderboard.model.QueryFailure
import leaderboard.search.document.{SearchDocumentJson, SearchDocumentPayloadSpec}
import leaderboard.search.dsl.SearchField

trait QdrantDocumentPointBuilder[A] {
  def qdrantPointId(document: A): Either[QueryFailure, QdrantPointId]

  def payload(document: A): Map[String, Json]

  final def validatedUpsertPointJson(document: A, vectorName: String, vector: List[Double]): Either[QueryFailure, Json] =
    qdrantPointId(document).map { pointId =>
      QdrantJsonInterpreter.upsertPointJson(pointId, vectorName, vector, payload(document))
    }
}

object QdrantDocumentPointBuilder {
  def fromPayloadSpec[A](
    payloadSpec: SearchDocumentPayloadSpec[A],
  ): QdrantDocumentPointBuilder[A] =
    new SchemaPayloadPointBuilder(payloadSpec, validatedPayloadFields(payloadSpec))

  private final class SchemaPayloadPointBuilder[A](
    payloadSpec: SearchDocumentPayloadSpec[A],
    payloadFields: List[SearchField[A]],
  ) extends QdrantDocumentPointBuilder[A] {
    override def qdrantPointId(document: A): Either[QueryFailure, QdrantPointId] =
      QdrantPointId.fromUuidString(payloadSpec.documentSpec.id(document))

    override def payload(document: A): Map[String, Json] =
      payloadFields.foldLeft(Map.empty[String, Json]) {
        case (acc, field) =>
          field.extract(document) match {
            case Some(value) => acc.updated(field.path, SearchDocumentJson.encodeValue(value))
            case None        => acc
          }
      }
  }

  private def validatedPayloadFields[A](
    payloadSpec: SearchDocumentPayloadSpec[A]
  ): List[SearchField[A]] =
    SearchDocumentJson.payloadFields(payloadSpec.documentSpec, payloadSpec.fieldPaths) match {
      case Right(fields) =>
        fields
      case Left(failure) =>
        throw new IllegalArgumentException(failure.message)
    }
}
