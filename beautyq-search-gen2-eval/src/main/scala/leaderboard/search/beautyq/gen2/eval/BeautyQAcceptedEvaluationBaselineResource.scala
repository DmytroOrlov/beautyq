package leaderboard.search.beautyq.gen2.eval

import leaderboard.search.gen2.eval.{AcceptedBaselineCodec, AcceptedBaselineDecodeError, AcceptedEvaluationBaseline}

import java.nio.ByteBuffer
import java.nio.charset.{CharacterCodingException, CodingErrorAction, StandardCharsets}
import scala.util.Using

sealed trait BeautyQAcceptedEvaluationBaselineResourceError {
  def code: String
}
object BeautyQAcceptedEvaluationBaselineResourceError {
  case object Missing extends BeautyQAcceptedEvaluationBaselineResourceError { val code = "canonical_resource_missing" }
  case object ReadFailure extends BeautyQAcceptedEvaluationBaselineResourceError { val code = "canonical_resource_read_failed" }
  case object InvalidUtf8 extends BeautyQAcceptedEvaluationBaselineResourceError { val code = "canonical_resource_invalid_utf8" }
  case object InvalidJson extends BeautyQAcceptedEvaluationBaselineResourceError { val code = "canonical_resource_invalid_json" }
  final case class CodecFailure(category: String) extends BeautyQAcceptedEvaluationBaselineResourceError { val code = "canonical_resource_codec_failed" }
}

object BeautyQAcceptedEvaluationBaselineResource {
  val ResourcePath = "leaderboard/search/beautyq/gen2/eval/beautyq_accepted_evaluation_baseline_v1.json"

  def loadCanonical(): Either[BeautyQAcceptedEvaluationBaselineResourceError, AcceptedEvaluationBaseline] =
    loadCanonical(resolveClassLoader(None))

  def loadCanonical(classLoader: ClassLoader): Either[BeautyQAcceptedEvaluationBaselineResourceError, AcceptedEvaluationBaseline] = {
    val resolvedLoader = resolveClassLoader(Option(classLoader))
    Option(resolvedLoader.getResourceAsStream(ResourcePath)) match {
      case None => Left(BeautyQAcceptedEvaluationBaselineResourceError.Missing)
      case Some(stream) =>
        Using.Manager { use =>
          val input = use(stream)
          input.readAllBytes()
        }.toEither match {
          case Left(_) => Left(BeautyQAcceptedEvaluationBaselineResourceError.ReadFailure)
          case Right(bytes) => decode(bytes)
        }
    }
  }

  private[eval] def resolveClassLoader(explicit: Option[ClassLoader]): ClassLoader =
    explicit
      .orElse(Option(Thread.currentThread().getContextClassLoader))
      .orElse(Option(definingClassLoader))
      .getOrElse(ClassLoader.getSystemClassLoader)

  private def definingClassLoader: ClassLoader = {
    val own = getClass.getClassLoader
    own match {
      case loader: ClassLoader => loader
      case null => ClassLoader.getSystemClassLoader
    }
  }

  private def decode(bytes: Array[Byte]): Either[BeautyQAcceptedEvaluationBaselineResourceError, AcceptedEvaluationBaseline] = {
    val decoder = StandardCharsets.UTF_8.newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT)
    val text = try decoder.decode(ByteBuffer.wrap(bytes)).toString
    catch { case _: CharacterCodingException => return Left(BeautyQAcceptedEvaluationBaselineResourceError.InvalidUtf8) }
    io.circe.parser.parse(text) match {
      case Left(_) => Left(BeautyQAcceptedEvaluationBaselineResourceError.InvalidJson)
      case Right(json) => AcceptedBaselineCodec.decode(json).left.map(error => BeautyQAcceptedEvaluationBaselineResourceError.CodecFailure(decodeCode(error)))
    }
  }

  private def decodeCode(error: AcceptedBaselineDecodeError): String = error match {
    case _: AcceptedBaselineDecodeError.ExpectedObject => "expected_object"
    case _: AcceptedBaselineDecodeError.ExpectedArray => "expected_array"
    case _: AcceptedBaselineDecodeError.UnexpectedFields => "unexpected_fields"
    case _: AcceptedBaselineDecodeError.MissingOrInvalidField => "missing_or_invalid_field"
    case _: AcceptedBaselineDecodeError.UnsupportedSchemaVersion => "unsupported_schema"
    case _: AcceptedBaselineDecodeError.InvalidFingerprintDigest => "invalid_fingerprint"
    case _: AcceptedBaselineDecodeError.EmptyVersionString => "empty_version"
    case _: AcceptedBaselineDecodeError.WhitespaceVersionString => "whitespace_version"
    case _: AcceptedBaselineDecodeError.DuplicateProvenanceId => "duplicate_provenance"
    case _: AcceptedBaselineDecodeError.DuplicateObservationKey => "duplicate_observation"
    case _: AcceptedBaselineDecodeError.MalformedMetricValue => "malformed_metric"
    case _: AcceptedBaselineDecodeError.NegativeCount => "negative_count"
    case _: AcceptedBaselineDecodeError.InvalidScale => "invalid_scale"
    case _: AcceptedBaselineDecodeError.ParseError => "parse_error"
  }
}
