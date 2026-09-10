package leaderboard.search.beautyq.gen2.eval

import io.circe.Json
import io.circe.parser.parse
import leaderboard.search.gen2.eval.{EvaluationPartition, EvaluationSliceId}

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

sealed trait BeautyQProtectedEvaluationCorpusError
object BeautyQProtectedEvaluationCorpusError {
  final case class InvalidInput(reason: String) extends BeautyQProtectedEvaluationCorpusError
  case object EmptyProtectedCorpus extends BeautyQProtectedEvaluationCorpusError
  case object ContainsNonProtectedCase extends BeautyQProtectedEvaluationCorpusError
  case object OverlapsVisibleCorpus extends BeautyQProtectedEvaluationCorpusError
  case object CaseCountMismatch extends BeautyQProtectedEvaluationCorpusError
  final case class MissingRequiredSlice(sliceId: String) extends BeautyQProtectedEvaluationCorpusError
  final case class SliceCountTooSmall(sliceId: String) extends BeautyQProtectedEvaluationCorpusError
}

final class BeautyQProtectedEvaluationCorpus private[eval] (
  val corpus: BeautyQEvaluationCorpus,
  val orderedRequiredSliceCounts: Vector[(EvaluationSliceId, Int)],
) {
  val caseCount: Int = corpus.cases.size
}

object BeautyQProtectedEvaluationCorpus {
  def fromJson(
    json: Json,
    visibleCorpus: BeautyQEvaluationCorpus,
    policy: BeautyQProtectedAcceptancePolicy,
  ): Either[BeautyQProtectedEvaluationCorpusError, BeautyQProtectedEvaluationCorpus] =
    BeautyQEvaluationCorpus.decodeFromJson(json) match {
      case Left(_) => Left(BeautyQProtectedEvaluationCorpusError.InvalidInput("protected corpus does not match the strict canonical corpus schema"))
      case Right(corpus) => validate(corpus, visibleCorpus, policy)
    }

  def load(
    path: Path,
    visibleCorpus: BeautyQEvaluationCorpus,
    policy: BeautyQProtectedAcceptancePolicy,
  ): Either[BeautyQProtectedEvaluationCorpusError, BeautyQProtectedEvaluationCorpus] =
    if (!Files.isRegularFile(path)) Left(BeautyQProtectedEvaluationCorpusError.InvalidInput("protected corpus file is not present"))
    else {
      try parse(Files.readString(path, StandardCharsets.UTF_8)) match {
        case Left(_) => Left(BeautyQProtectedEvaluationCorpusError.InvalidInput("protected corpus is not valid JSON"))
        case Right(json) => fromJson(json, visibleCorpus, policy)
      } catch {
        case _: java.io.IOException => Left(BeautyQProtectedEvaluationCorpusError.InvalidInput("protected corpus cannot be read"))
      }
    }

  private def validate(
    corpus: BeautyQEvaluationCorpus,
    visibleCorpus: BeautyQEvaluationCorpus,
    policy: BeautyQProtectedAcceptancePolicy,
  ): Either[BeautyQProtectedEvaluationCorpusError, BeautyQProtectedEvaluationCorpus] = {
    if (corpus.cases.isEmpty) Left(BeautyQProtectedEvaluationCorpusError.EmptyProtectedCorpus)
    else if (corpus.cases.exists(_.partition != EvaluationPartition.ProtectedHoldout)) Left(BeautyQProtectedEvaluationCorpusError.ContainsNonProtectedCase)
    else if (corpus.cases.map(_.caseId).distinct.size != corpus.cases.size) Left(BeautyQProtectedEvaluationCorpusError.InvalidInput("protected case IDs are not unique"))
    else if (corpus.cases.exists(current => visibleCorpus.cases.exists(_.caseId == current.caseId))) Left(BeautyQProtectedEvaluationCorpusError.OverlapsVisibleCorpus)
    else if (corpus.cases.size != policy.expectedCaseCount) Left(BeautyQProtectedEvaluationCorpusError.CaseCountMismatch)
    else {
      val counts = policy.requiredSliceMinimums.map { requirement =>
        val count = corpus.cases.count(_.slices.contains(requirement.sliceId))
        requirement.sliceId -> count
      }
      counts.collectFirst {
        case (sliceId, count) if !corpus.cases.exists(_.slices.contains(sliceId)) => BeautyQProtectedEvaluationCorpusError.MissingRequiredSlice(sliceId.value)
        case (sliceId, count) if count < policy.requiredSliceMinimums.find(_.sliceId == sliceId).map(_.minimumCaseCount).getOrElse(Int.MaxValue) => BeautyQProtectedEvaluationCorpusError.SliceCountTooSmall(sliceId.value)
      } match {
        case Some(error) => Left(error)
        case None => Right(new BeautyQProtectedEvaluationCorpus(corpus, counts))
      }
    }
  }
}
