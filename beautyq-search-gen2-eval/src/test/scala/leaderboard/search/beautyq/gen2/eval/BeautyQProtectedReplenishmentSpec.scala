package leaderboard.search.beautyq.gen2.eval

import io.circe.parser.parse
import leaderboard.search.gen2.eval.EvaluationPartition
import org.scalatest.wordspec.AnyWordSpec

import scala.io.Source

final class BeautyQProtectedReplenishmentSpec extends AnyWordSpec {
  "the replenished protected holdout" should {
    "replace every disclosed case from the frozen reserve and preserve the required shape" in {
      val visible = BeautyQEvaluationCorpus.loadCanonical().fold(error => fail(error.toString), identity)
      val protectedCorpus = decodeResource(ProtectedCorpusResource)
      val judgedDraft = readResource(JudgedDraftResource)
      val protectedRaw = readResource(ProtectedCorpusResource)

      assert(protectedCorpus.cases.size == 24)
      assert(protectedCorpus.cases.forall(_.partition == EvaluationPartition.ProtectedHoldout))
      assert(protectedCorpus.cases.count(_.notes.contains("Selected from the independently frozen pre-disclosure reserve.")) == 7)
      assert(protectedCorpus.cases.groupMapReduce(_.slices.headOption.map(_.value).getOrElse("missing"))(_ => 1)(_ + _) == Map(
        "exact-intent" -> 8,
        "conversational" -> 6,
        "nearby" -> 4,
        "multilingual" -> 3,
        "broad-safe" -> 3,
      ))
      assert(protectedCorpus.cases.map(_.caseId).toSet.intersect(visible.cases.map(_.caseId).toSet).isEmpty)
      assert(protectedCorpus.cases.map(_.query).toSet.intersect(visible.cases.map(_.query).toSet).isEmpty)
      assert(judgedDraft == protectedRaw)
      assert(
        protectedCorpus.corpusFingerprint == "7a654c7323f822f26bccc6d5ae7adde3faf4bfd790984ae63fb25c34978c9188",
        s"replenished corpus fingerprint=${protectedCorpus.corpusFingerprint}",
      )
    }
  }

  private def decodeResource(path: String): BeautyQEvaluationCorpus =
    parse(readResource(path)).left.map(_.message).flatMap(BeautyQEvaluationCorpus.decodeFromJson) match {
      case Right(value) => value
      case Left(error)  => fail(s"protected replenishment resource failed to decode: $error")
    }

  private def readResource(path: String): String = {
    val stream = Option(getClass.getClassLoader.getResourceAsStream(path)).getOrElse(fail(s"missing resource: $path"))
    try Source.fromInputStream(stream, "UTF-8").mkString
    finally stream.close()
  }

  private val ProtectedCorpusResource =
    "leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-holdout-v1.json"
  private val JudgedDraftResource =
    "leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-judged-draft-v1.json"
}
