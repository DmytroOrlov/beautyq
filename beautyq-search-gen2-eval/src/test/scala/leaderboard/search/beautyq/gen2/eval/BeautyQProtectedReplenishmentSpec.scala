package leaderboard.search.beautyq.gen2.eval

import io.circe.parser.parse
import leaderboard.search.gen2.eval.EvaluationPartition
import org.scalatest.wordspec.AnyWordSpec

import scala.io.Source

final class BeautyQProtectedReplenishmentSpec extends AnyWordSpec {
  "the replenished protected holdout" should {
    "replace the complete disclosed exact-intent slice from the frozen cycle-3 reserve and preserve the required shape" in {
      val visible = BeautyQEvaluationCorpus.loadCanonical().fold(error => fail(error.toString), identity)
      val protectedCorpus = decodeResource(ProtectedCorpusResource)
      val judgedDraft = readResource(JudgedDraftResource)
      val protectedRaw = readResource(ProtectedCorpusResource)

      assert(protectedCorpus.cases.size == 24)
      assert(protectedCorpus.cases.forall(_.partition == EvaluationPartition.ProtectedHoldout))
      assert(protectedCorpus.cases.count(_.notes.contains("Selected from the independently frozen pre-disclosure reserve.")) == 0)
      assert(protectedCorpus.cases.count(_.notes.contains("Selected in stable order from the independently frozen second-cycle pre-disclosure reserve.")) == 0)
      assert(protectedCorpus.cases.count(_.notes.contains("Selected in stable coverage order from the independently frozen cycle-3 pre-disclosure reserve.")) == 8)
      assert(protectedCorpus.cases.groupMapReduce(_.slices.headOption.map(_.value).getOrElse("missing"))(_ => 1)(_ + _) == Map(
        "exact-intent" -> 8,
        "conversational" -> 6,
        "nearby" -> 4,
        "multilingual" -> 3,
        "broad-safe" -> 3,
      ))
      assert(protectedCorpus.cases.map(_.caseId).toSet.intersect(visible.cases.map(_.caseId).toSet).isEmpty)
      assert(protectedCorpus.cases.map(_.query).toSet.intersect(visible.cases.map(_.query).toSet).isEmpty)
      assert(!protectedCorpus.cases.exists(_.caseId.value.startsWith("q2i4_case_")))
      assert(protectedCorpus.cases.takeRight(8).map(_.caseId.value) == Vector(
        "q2i5_reserve_001", "q2i5_reserve_004", "q2i5_reserve_007", "q2i5_reserve_010",
        "q2i5_reserve_013", "q2i5_reserve_016", "q2i5_reserve_019", "q2i5_reserve_022",
      ))
      assert(visible.cases.takeRight(8).map(_.caseId.value) == Vector(
        "q2i3_case_001", "q2i2_case_005", "q2i3_case_005", "q2i3_case_007",
        "q2i4_case_001", "q2i4_case_002", "q2i4_case_003", "q2i4_case_004",
      ))
      assert(judgedDraft == protectedRaw)
      assert(
        protectedCorpus.corpusFingerprint == "d72b29d6d9e13e21b34722fa4c8219975aae7613ba0c003326baefc5da056a6e",
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
