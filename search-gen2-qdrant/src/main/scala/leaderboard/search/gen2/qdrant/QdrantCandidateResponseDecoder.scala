package leaderboard.search.gen2.qdrant

import io.circe.{Json, JsonObject}
import leaderboard.search.gen2.contract.*

final case class QdrantCandidateHit[Id](id: Id, score: Double)

final case class QdrantCandidateDiagnostics(
  elapsedSeconds: Option[BigDecimal],
  rawCount: Int,
  uniqueCount: Int,
  duplicateCount: Int,
)

/** Candidate-only trusted result. Qdrant never owns public total, facets, groups or pagination. */
final class QdrantCandidateSearchResult[Id] private[qdrant] (
  val hits: Vector[QdrantCandidateHit[Id]],
  val diagnostics: QdrantCandidateDiagnostics,
)

sealed trait QdrantCandidateResponseError
object QdrantCandidateResponseError {
  final case class Malformed(message: String) extends QdrantCandidateResponseError
  final case class ExcessiveCount(maximum: Int, actual: Int) extends QdrantCandidateResponseError
  final case class InvalidPoint(index: Int, message: String) extends QdrantCandidateResponseError
  final case class InvalidId(index: Int, error: SearchValueDecodeError) extends QdrantCandidateResponseError
  final case class InvalidScore(index: Int, value: String) extends QdrantCandidateResponseError
}

object QdrantCandidateResponseDecoder {
  def decode[Document, Id](
    identityField: SearchField[Document, Id],
    request: QdrantCompiledCandidateRequest,
    raw: Json,
  ): Either[QdrantCandidateResponseError, QdrantCandidateSearchResult[Id]] =
    for {
      obj    <- raw.asObject.toRight(QdrantCandidateResponseError.Malformed("response must be an object"))
      status <- string(obj, "status")
      _      <- Either.cond(status == "ok", (), QdrantCandidateResponseError.Malformed(s"status must be 'ok', got '$status'"))
      time   <- optionalFiniteNumber(obj, "time")
      result <- obj("result").flatMap(_.asObject).toRight(QdrantCandidateResponseError.Malformed("result must be an object"))
      points <- result("points").flatMap(_.asArray).toRight(QdrantCandidateResponseError.Malformed("result.points must be an array"))
      _      <- Either.cond(points.length <= request.limit, (), QdrantCandidateResponseError.ExcessiveCount(request.limit, points.length))
      hits   <- decodePoints(identityField, points.toVector)
    } yield {
      val unique = hits.foldLeft(Vector.empty[QdrantCandidateHit[Id]]) { (acc, hit) =>
        if (acc.exists(_.id == hit.id)) acc else acc :+ hit
      }
      new QdrantCandidateSearchResult(
        unique,
        QdrantCandidateDiagnostics(time, hits.length, unique.length, hits.length - unique.length),
      )
    }

  private def decodePoints[Document, Id](
    identityField: SearchField[Document, Id],
    points: Vector[Json],
  ): Either[QdrantCandidateResponseError, Vector[QdrantCandidateHit[Id]]] =
    points.zipWithIndex.foldLeft[Either[QdrantCandidateResponseError, Vector[QdrantCandidateHit[Id]]]](Right(Vector.empty)) {
      case (acc, (point, index)) =>
        acc.flatMap { hits =>
          point.asObject.toRight(QdrantCandidateResponseError.InvalidPoint(index, "point must be an object")).flatMap { obj =>
            for {
              canonical <- pointIdCanonical(obj, index)
              id        <- identityField.codec.decodeCanonical(canonical).left.map(error => QdrantCandidateResponseError.InvalidId(index, error))
              score     <- score(obj, index)
            } yield hits :+ QdrantCandidateHit(id, score)
          }
        }
    }

  private def pointIdCanonical(obj: JsonObject, index: Int): Either[QdrantCandidateResponseError, String] =
    obj("id") match {
      case Some(value) =>
        value.asString.orElse(value.asNumber.flatMap(_.toBigInt).map(_.toString)).toRight(QdrantCandidateResponseError.InvalidPoint(index, "id must be a UUID string or unsigned integer"))
      case None => Left(QdrantCandidateResponseError.InvalidPoint(index, "missing id"))
    }

  private def score(obj: JsonObject, index: Int): Either[QdrantCandidateResponseError, Double] =
    obj("score").flatMap(_.asNumber.map(_.toDouble)) match {
      case Some(value) if value.isFinite => Right(value)
      case Some(value) => Left(QdrantCandidateResponseError.InvalidScore(index, value.toString))
      case None => Left(QdrantCandidateResponseError.InvalidPoint(index, "score must be a finite number"))
    }

  private def string(obj: JsonObject, name: String): Either[QdrantCandidateResponseError, String] =
    obj(name).flatMap(_.asString).toRight(QdrantCandidateResponseError.Malformed(s"$name must be a string"))

  private def optionalFiniteNumber(obj: JsonObject, name: String): Either[QdrantCandidateResponseError, Option[BigDecimal]] =
    obj(name) match {
      case None => Right(None)
      case Some(value) =>
        value.asNumber.flatMap(number => number.toBigDecimal) match {
          case Some(number) if number >= 0 => Right(Some(number))
          case _                           => Left(QdrantCandidateResponseError.Malformed(s"$name must be a non-negative number"))
        }
    }
}
