package leaderboard.search.qdrant

import io.circe.Json
import leaderboard.model.QueryFailure

import java.util.UUID

sealed trait QdrantPointId extends Product with Serializable {
  def asJson: Json
}

object QdrantPointId {
  final case class Uuid(value: UUID) extends QdrantPointId {
    override def asJson: Json =
      Json.fromString(value.toString)
  }

  final case class UnsignedLong(value: Long) extends QdrantPointId {
    override def asJson: Json =
      Json.fromLong(value)
  }

  def fromUuidString(value: String): Either[QueryFailure, QdrantPointId] =
    try Right(Uuid(UUID.fromString(value)))
    catch {
      case _: IllegalArgumentException =>
        Left(QueryFailure.operation("qdrant-point-id", s"Qdrant point id must be a UUID string or unsigned integer, got '$value'"))
    }

  def fromUnsignedLong(value: Long): Either[QueryFailure, QdrantPointId] =
    Either.cond(
      value >= 0L,
      UnsignedLong(value),
      QueryFailure.operation("qdrant-point-id", s"Qdrant unsigned integer point id must be non-negative, got $value"),
    )
}
