package leaderboard.search.beautyq.gen2.eval

import io.circe.parser.parse
import leaderboard.search.gen2.eval.EvaluationPartition
import org.scalatest.wordspec.AnyWordSpec

import scala.io.Source

final class BeautyQProtectedReplenishmentSpec extends AnyWordSpec {
  "the recovery replenished protected holdout" should {
    "replace the defective convergence reserve exact-intent slice from a fresh catalog-bound recovery reserve and preserve the required shape" in {
      val visible = BeautyQEvaluationCorpus.loadCanonical().fold(error => fail(error.toString), identity)
      val protectedCorpus = decodeResource(ProtectedCorpusResource)
      val judgedDraft = readResource(JudgedDraftResource)
      val protectedRaw = readResource(ProtectedCorpusResource)
      val authorDraftRaw = readResource(AuthorDraftResource)
      val authorDraft = BeautyQProtectedAuthorDraft.decodeString(authorDraftRaw).fold(error => fail(error), identity)

      assert(protectedCorpus.cases.size == 24)
      assert(protectedCorpus.cases.forall(_.partition == EvaluationPartition.ProtectedHoldout))
      assert(protectedCorpus.cases.count(_.notes.contains("Selected from the independently frozen pre-disclosure reserve.")) == 0)
      assert(protectedCorpus.cases.count(_.notes.contains("Selected in stable order from the independently frozen second-cycle pre-disclosure reserve.")) == 0)
      assert(protectedCorpus.cases.count(_.notes.contains("Selected in stable coverage order from the independently frozen cycle-3 pre-disclosure reserve.")) == 0)
      assert(protectedCorpus.cases.count(_.notes.contains("Authored independently from the public BeautyQ typed vocabulary before the Q2 convergence disclosure.")) == 0)
      assert(protectedCorpus.cases.count(_.notes.contains("Selected in stable order from the independently authored catalog-bound recovery reserve.")) == 8)
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
      assert(protectedCorpus.cases.takeRight(8).map(_.caseId.value) == Vector(
        "q2i7_recovery_001", "q2i7_recovery_005", "q2i7_recovery_009", "q2i7_recovery_013",
        "q2i7_recovery_017", "q2i7_recovery_021", "q2i7_recovery_025", "q2i7_recovery_029",
      ))
      assert(visible.cases.takeRight(8).map(_.caseId.value) == Vector(
        "q2i5_reserve_001", "q2i5_reserve_004", "q2i5_reserve_007", "q2i5_reserve_010",
        "q2i5_reserve_013", "q2i5_reserve_016", "q2i5_reserve_019", "q2i5_reserve_022",
      ))
      assert(judgedDraft == protectedRaw)
      assert(authorDraft.schemaVersion == BeautyQProtectedAuthorDraft.CurrentSchemaVersion)
      assert(authorDraft.sourceRevision == "eeccefe8bde82a1ac93f426aa4e58cf936178640")
      assert(authorDraft.authorPassId == "q2-exact-intent-convergence-recovery-author-v1")
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
        protectedCorpus.corpusFingerprint == "bd821a976622ad0157ec4c818f92187c60e08b2b5123f6306dea7878ef0aad17",
        s"recovery corpus fingerprint=${protectedCorpus.corpusFingerprint}",
      )
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
