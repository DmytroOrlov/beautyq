package leaderboard.search.beautyq.gen2.eval

import io.circe.parser.parse
import leaderboard.search.gen2.eval.EvaluationPartition
import org.scalatest.wordspec.AnyWordSpec

import scala.io.Source

final class BeautyQProtectedReplenishmentSpec extends AnyWordSpec {
  "the recovery replenished protected holdout" should {
    "migrate the disclosed exact-intent slice and preserve the rotation-8 protected shape" in {
      val visible = BeautyQEvaluationCorpus.loadCanonical().fold(error => fail(error.toString), identity)
      val protectedCorpus = decodeResource(ProtectedCorpusResource)
      val judgedDraft = readResource(JudgedDraftResource)
      val protectedRaw = readResource(ProtectedCorpusResource)
      val authorDraftRaw = readResource(AuthorDraftResource)
      val authorDraft = BeautyQProtectedAuthorDraft.decodeString(authorDraftRaw).fold(error => fail(error), identity)

      assert(protectedCorpus.cases.size == 24)
      assert(protectedCorpus.corpusId == "beautyq-protected-holdout-q2i-recovery-v9")
      assert(protectedCorpus.version == 9)
      assert(visible.cases.size == 180)
      assert(protectedCorpus.cases.forall(_.partition == EvaluationPartition.ProtectedHoldout))
      assert(protectedCorpus.cases.count(_.notes.contains("Selected from the independently frozen pre-disclosure reserve.")) == 0)
      assert(protectedCorpus.cases.count(_.notes.contains("Selected in stable order from the independently frozen second-cycle pre-disclosure reserve.")) == 0)
      assert(protectedCorpus.cases.count(_.notes.contains("Selected in stable coverage order from the independently frozen cycle-3 pre-disclosure reserve.")) == 0)
      assert(protectedCorpus.cases.count(_.notes.contains("Authored independently from the public BeautyQ typed vocabulary before the Q2 convergence disclosure.")) == 0)
      assert(protectedCorpus.cases.count(_.notes.contains("Selected as the first eligible non-consumed candidate in stable bucket order from the independently authored fresh catalog-bound recovery reserve.")) == 0)
      assert(protectedCorpus.cases.count(_.notes.contains("Selected as the first eligible non-consumed candidate in stable bucket order from the rotation-4 independently authored fresh catalog-bound recovery reserve.")) == 0)
      assert(protectedCorpus.cases.count(_.notes.contains("Authored independently from the canonical BeautyQ typed seed catalog for the rotation-8 fresh author pass before any disclosure inspection.")) == 8)
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
        "q2i7_recovery_065", "q2i7_recovery_066", "q2i7_recovery_067", "q2i7_recovery_068",
        "q2i7_recovery_069", "q2i7_recovery_071", "q2i7_recovery_072", "q2i7_recovery_073",
      )
      val selectedIds = Vector(
        "q2i8_recovery_001", "q2i8_recovery_002", "q2i8_recovery_003", "q2i8_recovery_004",
        "q2i8_recovery_005", "q2i8_recovery_006", "q2i8_recovery_007", "q2i8_recovery_008",
      )
      assert(protectedCorpus.cases.takeRight(8).map(_.caseId.value) == selectedIds)
      assert(visible.cases.takeRight(8).map(_.caseId.value) == disclosedIds)
      assert(protectedCorpus.cases.forall(current => !disclosedIds.contains(current.caseId.value)))
      assert(visible.cases.forall(current => !selectedIds.contains(current.caseId.value)))
      assert(judgedDraft == protectedRaw)
      assert(authorDraft.schemaVersion == BeautyQProtectedAuthorDraft.CurrentSchemaVersion)
      assert(authorDraft.authorPassId == "q2i8-recovery-rotation-8-fresh-reserve-author-v1")
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
        "c86815df77acecb6ea7797e7717f5acbc39d23e12495526c3e82df1742daff97")
      assert(loadPolicy().protectedAcceptancePolicyVersion == "beautyq-protected-acceptance-policy-v8")
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
