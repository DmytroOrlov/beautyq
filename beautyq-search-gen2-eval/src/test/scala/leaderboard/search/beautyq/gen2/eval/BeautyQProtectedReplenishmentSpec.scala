package leaderboard.search.beautyq.gen2.eval

import io.circe.parser.parse
import leaderboard.search.gen2.eval.EvaluationPartition
import org.scalatest.wordspec.AnyWordSpec

import scala.io.Source

final class BeautyQProtectedReplenishmentSpec extends AnyWordSpec {
  "the recovery replenished protected holdout" should {
    "migrate the disclosed exact-intent slice and preserve the rotation-7 protected shape" in {
      val visible = BeautyQEvaluationCorpus.loadCanonical().fold(error => fail(error.toString), identity)
      val protectedCorpus = decodeResource(ProtectedCorpusResource)
      val judgedDraft = readResource(JudgedDraftResource)
      val protectedRaw = readResource(ProtectedCorpusResource)
      val authorDraftRaw = readResource(AuthorDraftResource)
      val authorDraft = BeautyQProtectedAuthorDraft.decodeString(authorDraftRaw).fold(error => fail(error), identity)

      assert(protectedCorpus.cases.size == 24)
      assert(protectedCorpus.corpusId == "beautyq-protected-holdout-q2i-recovery-v8")
      assert(protectedCorpus.version == 8)
      assert(visible.cases.size == 172)
      assert(protectedCorpus.cases.forall(_.partition == EvaluationPartition.ProtectedHoldout))
      assert(protectedCorpus.cases.count(_.notes.contains("Selected from the independently frozen pre-disclosure reserve.")) == 0)
      assert(protectedCorpus.cases.count(_.notes.contains("Selected in stable order from the independently frozen second-cycle pre-disclosure reserve.")) == 0)
      assert(protectedCorpus.cases.count(_.notes.contains("Selected in stable coverage order from the independently frozen cycle-3 pre-disclosure reserve.")) == 0)
      assert(protectedCorpus.cases.count(_.notes.contains("Authored independently from the public BeautyQ typed vocabulary before the Q2 convergence disclosure.")) == 0)
      assert(protectedCorpus.cases.count(_.notes.contains("Selected as the first eligible non-consumed candidate in stable bucket order from the independently authored fresh catalog-bound recovery reserve.")) == 0)
      assert(protectedCorpus.cases.count(_.notes.contains("Selected as the first eligible non-consumed candidate in stable bucket order from the rotation-4 independently authored fresh catalog-bound recovery reserve.")) == 0)
      assert(protectedCorpus.cases.count(_.notes.contains("Authored independently from the canonical BeautyQ typed seed catalog before any disclosure inspection.")) == 8)
      assert(protectedCorpus.cases.groupMapReduce(_.slices.headOption.map(_.value).getOrElse("missing"))(_ => 1)(_ + _) == Map(
        "exact-intent" -> 8,
        "conversational" -> 6,
        "nearby" -> 4,
        "multilingual" -> 3,
        "broad-safe" -> 3,
      ))
      assert(protectedCorpus.cases.map(_.caseId).toSet.intersect(visible.cases.map(_.caseId).toSet).isEmpty)
      assert(protectedCorpus.cases.map(_.query).toSet.intersect(visible.cases.map(_.query).toSet).isEmpty)
      assert(!protectedCorpus.cases.exists(_.caseId.value.startsWith("q2i5_reserve_")))
      assert(!protectedCorpus.cases.exists(_.caseId.value.startsWith("q2i6_reserve_")))
      val disclosedIds = Vector(
        "q2i7_recovery_057", "q2i7_recovery_058", "q2i7_recovery_059", "q2i7_recovery_060",
        "q2i7_recovery_061", "q2i7_recovery_062", "q2i7_recovery_063", "q2i7_recovery_064",
      )
      val selectedIds = Vector(
        "q2i7_recovery_065", "q2i7_recovery_066", "q2i7_recovery_067", "q2i7_recovery_068",
        "q2i7_recovery_069", "q2i7_recovery_073", "q2i7_recovery_071", "q2i7_recovery_072",
      )
      assert(protectedCorpus.cases.takeRight(8).map(_.caseId.value) == selectedIds)
      assert(visible.cases.takeRight(8).map(_.caseId.value) == disclosedIds)
      assert(protectedCorpus.cases.forall(current => !disclosedIds.contains(current.caseId.value)))
      assert(visible.cases.forall(current => !selectedIds.contains(current.caseId.value)))
      assert(judgedDraft == protectedRaw)
      assert(authorDraft.schemaVersion == BeautyQProtectedAuthorDraft.CurrentSchemaVersion)
      assert(authorDraft.sourceRevision == "6652582525071773fbc14d02b0e081e58e8ef2ac")
      assert(authorDraft.authorPassId == "q2i7-recovery-rotation-7-fresh-reserve-author-v3")
      val protectedEvaluationCorpus = BeautyQProtectedEvaluationCorpus.fromJson(
        parse(protectedRaw).getOrElse(fail("invalid protected json")),
        visible,
        loadPolicy(),
      ).fold(error => fail(error.toString), identity)
      assert(BeautyQProtectedAuthorDraft.correspondsTo(authorDraft, protectedEvaluationCorpus).isRight)
      assert(authorDraftRaw != judgedDraft)
      Vector("judgments", "acceptableIds", "forbiddenIds", "neutralIds", "gradedGains", "resultId", "providerId", "serviceIntentId")
        .foreach(field => assert(!authorDraftRaw.contains(field), s"author draft leaked $field"))
      assert(
        protectedCorpus.corpusFingerprint.length == 64,
        s"recovery corpus fingerprint=${protectedCorpus.corpusFingerprint}",
      )
      assert(protectedCorpus.corpusFingerprint ==
        "ce020b49d28d4c1c45ee6abfc0959678a920e2fb6e2dc676e7b72525a272ac50")
      assert(loadPolicy().protectedAcceptancePolicyVersion == "beautyq-protected-acceptance-policy-v7")
    }
  }

  private def loadPolicy(): BeautyQProtectedAcceptancePolicy = {
    val policyRaw = readResource(PolicyResource)
    parse(policyRaw).flatMap(BeautyQProtectedAcceptancePolicy.fromJson) match {
      case Right(value) => value
      case Left(error) => fail(s"policy load failed: $error")
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
  private val AuthorDraftResource =
    "leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-author-draft-v1.json"
  private val PolicyResource =
    "leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-acceptance-policy-v1.json"
}
