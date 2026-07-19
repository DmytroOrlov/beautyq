package leaderboard.search.gen2.qdrant

import leaderboard.search.gen2.core.materialization.CanonicalFingerprint

enum QdrantEmbeddingPurpose {
  case Document
  case CandidateQuery
}

final class QdrantEmbeddingInput private (
  purpose: QdrantEmbeddingPurpose,
  subject: String,
  text: String,
  model: QdrantEmbeddingModelIdentity,
) {
  val purposeValue: QdrantEmbeddingPurpose = purpose
  val subjectValue: String = subject
  val textValue: String = text
  val modelValue: QdrantEmbeddingModelIdentity = model

  val fingerprint: String =
    CanonicalFingerprint.sha256HexTokens(
      Vector(
        CanonicalFingerprint.token("purpose", purpose match {
          case QdrantEmbeddingPurpose.Document      => "document"
          case QdrantEmbeddingPurpose.CandidateQuery => "candidate-query"
        }),
        CanonicalFingerprint.token("subject", subject),
        CanonicalFingerprint.token("text", text),
        CanonicalFingerprint.token("provider", model.provider),
        CanonicalFingerprint.token("model", model.model),
        CanonicalFingerprint.token("revision", model.revision),
        CanonicalFingerprint.token("dimension", model.dimension.toString),
        CanonicalFingerprint.token("text-format", model.textFormatVersion),
      )
    )
}

object QdrantEmbeddingInput {
  def from(
    purpose: QdrantEmbeddingPurpose,
    subject: String,
    text: String,
    model: QdrantEmbeddingModelIdentity,
  ): Either[QdrantEmbeddingError, QdrantEmbeddingInput] =
    if (text.trim.isEmpty) Left(QdrantEmbeddingError.BlankText(subject))
    else Right(new QdrantEmbeddingInput(purpose, subject, text, model))
}

sealed trait QdrantEmbeddingError
object QdrantEmbeddingError {
  final case class BlankText(subject: String) extends QdrantEmbeddingError
  final case class DimensionMismatch(expected: Int, actual: Int) extends QdrantEmbeddingError
  final case class NonFinite(index: Int, value: Double) extends QdrantEmbeddingError
  final case class InputMismatch(expected: String, actual: String) extends QdrantEmbeddingError
  final case class ModelMismatch(expected: QdrantEmbeddingModelIdentity, actual: QdrantEmbeddingModelIdentity) extends QdrantEmbeddingError
}

/** Validated output of an external embedding boundary. The input fingerprint is retained so a vector
  * cannot accidentally be paired with a different prepared document/query. */
final class QdrantEmbeddingResult private (
  val inputFingerprint: String,
  val model: QdrantEmbeddingModelIdentity,
  val values: Vector[Double],
)

object QdrantEmbeddingResult {
  def from(
    input: QdrantEmbeddingInput,
    values: Vector[Double],
  ): Either[QdrantEmbeddingError, QdrantEmbeddingResult] =
    if (input.textValue.trim.isEmpty) Left(QdrantEmbeddingError.BlankText(input.subjectValue))
    else if (values.length != input.modelValue.dimension) Left(QdrantEmbeddingError.DimensionMismatch(input.modelValue.dimension, values.length))
    else values.zipWithIndex.find { case (value, _) => !value.isFinite } match {
      case Some((value, index)) => Left(QdrantEmbeddingError.NonFinite(index, value))
      case None                 => Right(new QdrantEmbeddingResult(input.fingerprint, input.modelValue, values))
    }
}

sealed trait QdrantPointId
object QdrantPointId {
  final case class Uuid(value: String) extends QdrantPointId
  final case class UnsignedLong(value: BigInt) extends QdrantPointId

  def json(id: QdrantPointId): io.circe.Json = id match {
    case Uuid(value)        => io.circe.Json.fromString(value)
    case UnsignedLong(value) => io.circe.Json.fromBigInt(value)
  }

  def canonical(id: QdrantPointId): String = id match {
    case Uuid(value)         => s"uuid:$value"
    case UnsignedLong(value) => s"uint:$value"
  }

  def fromCanonical(field: leaderboard.search.gen2.contract.SearchField[?, ?], canonical: String): Either[QdrantPointIdError, QdrantPointId] = {
    val uuid = leaderboard.search.gen2.contract.SearchValueCodec.uuid.decodeCanonical(canonical).toOption
    if (uuid.nonEmpty) Right(Uuid(canonical))
    else field.kind match {
      case leaderboard.search.gen2.contract.SearchFieldKind.Integer | leaderboard.search.gen2.contract.SearchFieldKind.Long =>
        scala.util.Try(BigInt(canonical)).toOption match {
          case Some(value) if value >= 0 && value <= BigInt("18446744073709551615") => Right(UnsignedLong(value))
          case _ => Left(QdrantPointIdError.UnsupportedCanonicalValue(field.id, canonical))
        }
      case _ => Left(QdrantPointIdError.UnsupportedCanonicalValue(field.id, canonical))
    }
  }
}

sealed trait QdrantPointIdError
object QdrantPointIdError {
  final case class UnsupportedCanonicalValue(fieldId: leaderboard.search.gen2.contract.FieldId, value: String) extends QdrantPointIdError
}
