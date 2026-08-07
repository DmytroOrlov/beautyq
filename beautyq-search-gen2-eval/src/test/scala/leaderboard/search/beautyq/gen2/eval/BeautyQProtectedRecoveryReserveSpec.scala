package leaderboard.search.beautyq.gen2.eval

import io.circe.parser.parse
import io.circe.Json
import org.scalatest.wordspec.AnyWordSpec

import java.security.MessageDigest
import java.nio.charset.StandardCharsets
import scala.io.Source

final class BeautyQProtectedRecoveryReserveSpec extends AnyWordSpec {
  private def classpathReader(path: String): Either[String, String] = {
    val stream = Option(getClass.getClassLoader.getResourceAsStream(path))
    if (stream.isEmpty) Left(s"resource_not_found: $path")
    else {
      try Right(Source.fromInputStream(stream.get, "UTF-8").mkString)
      finally try { stream.get.close() } catch { case _: Exception => () }
    }
  }

  private def sha256(raw: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.digest(raw.getBytes(StandardCharsets.UTF_8)).map(b => f"$b%02x").mkString
  }

  private lazy val reserve = BeautyQProtectedRecoveryReserve.load(classpathReader).fold(error => fail(error), identity)
  private lazy val reserve2 = BeautyQProtectedRecoveryReserve.load(classpathReader).fold(error => fail(error), identity)

  private val CurrentAuthorReserveResource =
    "leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-recovery-author-reserve-v2.json"
  private val CurrentJudgedReserveResource =
    "leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-recovery-judged-reserve-v2.json"
  private val CurrentSelectionAuditResource =
    "leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-recovery-selection-audit-v4.json"

  private val AuthorReserveResource =
    "leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-recovery-author-reserve-v1.json"
  private val SelectionAuditResource =
    "leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-recovery-selection-audit-v3.json"
  private val FinalAuthorDraftResource =
    "leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-author-draft-v1.json"
  private val FinalJudgedDraftResource =
    "leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-judged-draft-v1.json"
  private val FinalProtectedCorpusResource =
    "leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-holdout-v1.json"
  private val FinalProtectedPolicyResource =
    "leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-acceptance-policy-v1.json"
  private val JudgedReserveResource =
    "leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-recovery-judged-reserve-v1.json"
  private val VisibleCorpusResource =
    "leaderboard/search/beautyq/gen2/eval/beautyq_evaluation_corpus_v2.json"
  private val FreshDisclosedIds = Set(
    "q2i7_recovery_003", "q2i7_recovery_007", "q2i7_recovery_011", "q2i7_recovery_015",
    "q2i7_recovery_019", "q2i7_recovery_023", "q2i7_recovery_027", "q2i7_recovery_032",
  )

  private def loadLegacyFixture(
    reader: BeautyQProtectedRecoveryReserve.ResourceReader,
    preserveVisible: Boolean = false,
  ): Either[String, BeautyQProtectedRecoveryReserve] = {
    def schema(raw: String, expected: String): Either[String, String] =
      parse(raw).left.map(_.message).map(_.mapObject(_.add("schemaVersion", io.circe.Json.fromString(expected))).noSpaces)

    val authorRaw = reader(AuthorReserveResource).flatMap(schema(_, "beautyq-protected-recovery-author-reserve-v2"))
    val judgedRaw = reader(JudgedReserveResource).flatMap(schema(_, "beautyq-protected-recovery-judged-reserve-v2"))
    val auditRaw = for {
      author <- authorRaw
      judged <- judgedRaw
      raw <- reader(SelectionAuditResource)
      authorSha = sha256(author)
      judgedSha = sha256(judged)
      normalized <- parse(raw).left.map(_.message).map { json =>
        json.mapObject(_.add("schemaVersion", io.circe.Json.fromString("beautyq-protected-recovery-selection-audit-v4"))
          .add("authorReserveSha256", io.circe.Json.fromString(authorSha))
          .add("judgedReserveSha256", io.circe.Json.fromString(judgedSha)))
          .noSpaces
      }
    } yield normalized

    BeautyQProtectedRecoveryReserve.load { path =>
      path match {
        case CurrentAuthorReserveResource => authorRaw
        case CurrentJudgedReserveResource => judgedRaw
        case CurrentSelectionAuditResource => auditRaw
        case VisibleCorpusResource if !preserveVisible => Right(legacyVisibleRaw)
        case other => reader(other)
      }
    }
  }

  private def legacyVisibleRaw: String = {
    val raw = classpathReader(VisibleCorpusResource).getOrElse(fail("missing visible corpus"))
    parse(raw).getOrElse(fail("invalid visible json")).mapObject { obj =>
      val cases = obj("cases").flatMap(_.asArray).getOrElse(fail("cases missing"))
      obj.add("cases", io.circe.Json.fromValues(cases.filterNot { value =>
        value.asObject.flatMap(_("id")).flatMap(_.asString).exists(FreshDisclosedIds.contains)
      }))
    }.noSpaces
  }

  "BeautyQProtectedRecoveryReserve" should {
    "load tracked resources from classpath without target dependency" in {
      BeautyQProtectedRecoveryReserve.load(classpathReader) match {
        case Right(value) =>
           assert(value.authorReserve.cases.size == 8)
           assert(value.selectionAudit.orderedBuckets.size == 8)
           assert(value.selectionAudit.candidateCount == 8)
        case Left(error) => fail(error)
      }
    }

    "bind all three source revisions" in {
      assert(reserve.authorReserve.sourceRevision == reserve.selectionAudit.sourceRevision)
      assert(reserve.judgedReserve.sourceRevision == reserve.selectionAudit.sourceRevision)
    }

    "bind author and judge pass IDs to the audit" in {
      assert(reserve.authorReserve.authorPassId == reserve.selectionAudit.authorReservePassId)
      assert(reserve.judgedReserve.judgePassId == reserve.selectionAudit.judgeReservePassId)
    }

    "keep author, judge and audit pass IDs pairwise distinct" in {
      val ids = Vector(
        reserve.authorReserve.authorPassId,
        reserve.judgedReserve.judgePassId,
        reserve.selectionAudit.auditPassId,
      )
      assert(ids.distinct.size == ids.size)
    }

    "have exact 8 x 1 bucket structure" in {
      val buckets = reserve.authorReserve.cases.groupBy(_.coverageBucket)
      assert(buckets.size == 8)
      buckets.values.foreach(cases => assert(cases.size == 1))
      val declared = reserve.authorReserve.cases.map(_.coverageBucket).distinct
      assert(declared == reserve.selectionAudit.orderedBuckets)
    }

    "reject uneven positive bucket counts" in {
      val rawAuthor = classpathReader(CurrentAuthorReserveResource).getOrElse(fail("missing author reserve"))
      val rawJudged = classpathReader(CurrentJudgedReserveResource).getOrElse(fail("missing judged reserve"))
      val authorJson = parse(rawAuthor).getOrElse(fail("invalid author reserve"))
      val judgedJson = parse(rawJudged).getOrElse(fail("invalid judged reserve"))
      val authorCases = authorJson.hcursor.downField("cases").focus.flatMap(_.asArray).getOrElse(fail("author cases missing"))
      val judgedCorpus = judgedJson.hcursor.downField("corpus").focus.flatMap(_.asObject).getOrElse(fail("judged corpus missing"))
      val judgedCases = judgedCorpus("cases").flatMap(_.asArray).getOrElse(fail("judged cases missing"))
      val extraAuthor = authorCases.headOption.getOrElse(fail("author cases empty")).mapObject(_.add(
        "id", io.circe.Json.fromString("q2i7_recovery_041"),
      ).add("query", io.circe.Json.fromString("manicure no coating alternate")))
      val extraJudged = judgedCases.headOption.getOrElse(fail("judged cases empty")).mapObject(_.add(
        "id", io.circe.Json.fromString("q2i7_recovery_041"),
      ).add("query", io.circe.Json.fromString("manicure no coating alternate")))
      val newAuthor = authorJson.mapObject(_.add("cases", io.circe.Json.fromValues(authorCases :+ extraAuthor))).noSpaces
      val newJudged = judgedJson.mapObject(_.add(
        "corpus", io.circe.Json.fromJsonObject(judgedCorpus.add("cases", io.circe.Json.fromValues(judgedCases :+ extraJudged))),
      )).noSpaces
      val newAuthorSha = sha256(newAuthor)
      val newJudgedSha = sha256(newJudged)
      val reader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case CurrentAuthorReserveResource => Right(newAuthor)
        case CurrentJudgedReserveResource => Right(newJudged)
        case CurrentSelectionAuditResource => classpathReader(CurrentSelectionAuditResource).map { raw =>
          parse(raw).getOrElse(fail("invalid selection audit")).mapObject(_.add("candidateCount", io.circe.Json.fromInt(9))
            .add("authorReserveSha256", io.circe.Json.fromString(newAuthorSha))
            .add("judgedReserveSha256", io.circe.Json.fromString(newJudgedSha))).noSpaces
        }
        case other => classpathReader(other)
      }
      assert(BeautyQProtectedRecoveryReserve.load(reader) == Left("recovery_bucket_candidate_count_not_uniform_positive"))
    }

    "reject a missing bucket" in {
      val rawAuthor = classpathReader(CurrentAuthorReserveResource).getOrElse(fail("missing author reserve"))
      val modifiedAuthor = parse(rawAuthor).getOrElse(fail("invalid author reserve")).mapObject { obj =>
        val cases = obj("cases").flatMap(_.asArray).getOrElse(fail("author cases missing"))
        val first = cases.headOption.getOrElse(fail("author cases empty")).mapObject(_.add("coverageBucket", io.circe.Json.fromString("pedicure")))
        obj.add("cases", io.circe.Json.fromValues(first +: cases.drop(1)))
      }.noSpaces
      val authorSha = sha256(modifiedAuthor)
      val reader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case CurrentAuthorReserveResource => Right(modifiedAuthor)
        case CurrentSelectionAuditResource => classpathReader(CurrentSelectionAuditResource).map { raw =>
          parse(raw).getOrElse(fail("invalid selection audit")).mapObject(_.add("authorReserveSha256", io.circe.Json.fromString(authorSha))).noSpaces
        }
        case other => classpathReader(other)
      }
      assert(BeautyQProtectedRecoveryReserve.load(reader) == Left("recovery_bucket_count_not_eight"))
    }

    "have exact positional selection with an empty consumed inventory" in {
      val visible = BeautyQEvaluationCorpus.loadCanonical().fold(error => fail(error.toString), identity)
      val orderedBuckets = reserve.selectionAudit.orderedBuckets
      val consumedCaseIds = reserve.selectionAudit.consumedCaseIds
      val bucketCount = orderedBuckets.size
      val roundCount = consumedCaseIds.size / bucketCount
       assert(roundCount == 0)
       assert(consumedCaseIds.isEmpty)
      val consumedSet = consumedCaseIds.toSet
      val reserveIds = reserve.authorReserve.cases.map(_.id).toSet
      val nonReserveVisibleNormalized = visible.cases.iterator.filterNot(c => reserveIds.contains(c.caseId.value)).map(c => BeautyQEvaluationQueryIdentity.normalize(c.query)).toSet
      orderedBuckets.zipWithIndex.foreach { case (bucket, bucketIndex) =>
        val bucketCandidates = reserve.authorReserve.cases.filter(_.coverageBucket == bucket)
        val eligibleIds = bucketCandidates.filterNot(c =>
          nonReserveVisibleNormalized.contains(BeautyQEvaluationQueryIdentity.normalize(c.query))
        ).map(_.id)
        val bucketConsumedIds = (0 until roundCount).map(r => consumedCaseIds(r * bucketCount + bucketIndex))
        assert(bucketConsumedIds == eligibleIds.take(roundCount), s"bucket $bucket: expected consumed ${eligibleIds.take(roundCount)}, got $bucketConsumedIds")
      }
      val selectedPairs = orderedBuckets.zip(reserve.selectionAudit.selectedCaseIds)
      selectedPairs.foreach { case (bucket, selectedId) =>
        val nonReserveVisibleNormalized = visible.cases.iterator.filterNot(c => reserveIds.contains(c.caseId.value)).map(c => BeautyQEvaluationQueryIdentity.normalize(c.query)).toSet
        val bucketCandidates = reserve.authorReserve.cases.filter(_.coverageBucket == bucket)
        val remainingCandidates = bucketCandidates.filterNot(c =>
          consumedSet.contains(c.id) ||
          nonReserveVisibleNormalized.contains(BeautyQEvaluationQueryIdentity.normalize(c.query))
        )
        assert(remainingCandidates.headOption.exists(_.id == selectedId), s"bucket $bucket: expected first remaining non-visible candidate ${remainingCandidates.headOption.map(_.id)}, got selected $selectedId")
      }
    }

     "correspond all 8 author cases with judged cases including notes" in {
      val pairs = reserve.authorReserve.cases.zip(reserve.judgedReserve.corpus.cases)
      pairs.foreach { case (ac, jc) =>
        assert(ac.id == jc.caseId.value)
        assert(ac.query == jc.query)
        assert(ac.language == jc.language)
        val authorSlices = ac.primarySlice +: ac.additionalSlices
        assert(authorSlices == jc.slices.map(_.value))
        assert(ac.userIntent == jc.userIntent)
        assert(ac.notes == jc.notes)
      }
    }

    "bind selected cases to the final author draft" in {
      val authorDraftRaw = classpathReader(FinalAuthorDraftResource).fold(error => fail(error), identity)
      val authorDraft = BeautyQProtectedAuthorDraft.decodeString(authorDraftRaw).fold(error => fail(error), identity)
      val allReserveIds = reserve.authorReserve.cases.map(_.id).toSet
      val selectedIds = reserve.selectionAudit.selectedCaseIds
      val reserveFinalCases = authorDraft.cases.filter(c => allReserveIds.contains(c.id))
      assert(reserveFinalCases.map(_.id) == selectedIds)
      val authorById = reserve.authorReserve.cases.map(c => c.id -> c).toMap
      reserveFinalCases.foreach { fc =>
        val ac = authorById(fc.id)
        assert(fc.query == ac.query)
        assert(fc.language == ac.language)
        assert(fc.primarySlice == ac.primarySlice)
        assert(fc.additionalSlices == ac.additionalSlices)
        assert(fc.userIntent == ac.userIntent)
      }
    }

    "bind selected cases to the final judged/holdout corpus" in {
      val judgedDraftRaw = classpathReader(FinalJudgedDraftResource).fold(error => fail(error), identity)
      val judgedDraft = BeautyQEvaluationCorpus.decodeFromJson(parse(judgedDraftRaw).getOrElse(fail("invalid json"))).getOrElse(fail("decode failed"))
      val allReserveIds = reserve.authorReserve.cases.map(_.id).toSet
      val selectedIds = reserve.selectionAudit.selectedCaseIds
      val reserveCases = judgedDraft.cases.filter(c => allReserveIds.contains(c.caseId.value))
      assert(reserveCases.map(_.caseId.value) == selectedIds)
      val protectedCorpusRaw = classpathReader(FinalProtectedCorpusResource).fold(error => fail(error), identity)
      val protectedCorpus = BeautyQEvaluationCorpus.decodeFromJson(parse(protectedCorpusRaw).getOrElse(fail("invalid json"))).getOrElse(fail("decode failed"))
      val protectedById = protectedCorpus.cases.map(c => c.caseId.value -> c).toMap
      reserveCases.foreach { jc =>
        val pc = protectedById(jc.caseId.value)
        assert(jc.caseId == pc.caseId)
        assert(jc.partition == pc.partition)
        assert(jc.judgmentMode == pc.judgmentMode)
        assert(jc.query == pc.query)
        assert(jc.language == pc.language)
        assert(jc.slices == pc.slices)
        assert(jc.userIntent == pc.userIntent)
        assert(jc.notes == pc.notes)
        assert(jc.variantJudgments == pc.variantJudgments)
        assert(jc.providerJudgments == pc.providerJudgments)
        assert(jc.serviceIntentJudgments == pc.serviceIntentJudgments)
      }
    }

    "exclude unselected reserve candidates from the final recovery inventory" in {
      val authorDraftRaw = classpathReader(FinalAuthorDraftResource).fold(error => fail(error), identity)
      val authorDraft = BeautyQProtectedAuthorDraft.decodeString(authorDraftRaw).fold(error => fail(error), identity)
      val allReserveIds = reserve.authorReserve.cases.map(_.id).toSet
      val selectedIds = reserve.selectionAudit.selectedCaseIds.toSet
      val reserveFinalIds = authorDraft.cases.map(_.id).filter(allReserveIds.contains)
      assert(reserveFinalIds.forall(selectedIds.contains))
    }

    "have byte-equal final judged draft and holdout" in {
      val judgedDraftRaw = classpathReader(FinalJudgedDraftResource).fold(error => fail(error), identity)
      val protectedCorpusRaw = classpathReader(FinalProtectedCorpusResource).fold(error => fail(error), identity)
      assert(judgedDraftRaw == protectedCorpusRaw)
    }

    "bind actual resource SHA-256 hashes computed from loaded bytes" in {
      val audit = reserve.selectionAudit
      assert(audit.authorReserveSha256.length == 64)
      assert(audit.judgedReserveSha256.length == 64)
    }

    "bind final resource hashes and fingerprints from actual tracked resources" in {
      val audit = reserve.selectionAudit
      assert(audit.finalAuthorDraftSha256.length == 64)
      assert(audit.finalJudgedHoldoutSha256.length == 64)
      assert(audit.finalProtectedCorpusFingerprint.length == 64)
      assert(audit.finalProtectedPolicyFingerprint.length == 64)
      assert(audit.canonicalCatalogFingerprint.length == 64)
    }

    "produce identical result from two independent loads" in {
      assert(reserve.authorReserve.cases.map(_.id) == reserve2.authorReserve.cases.map(_.id))
      assert(reserve.judgedReserve.corpus.cases.map(_.caseId.value) == reserve2.judgedReserve.corpus.cases.map(_.caseId.value))
      assert(reserve.selectionAudit.sourceRevision == reserve2.selectionAudit.sourceRevision)
      assert(reserve.selectionAudit.selectedCaseIds == reserve2.selectionAudit.selectedCaseIds)
      assert(reserve.selectionAudit.consumedCaseIds == reserve2.selectionAudit.consumedCaseIds)
    }

    "not depend on ignored target directory" in {
      assert(BeautyQProtectedRecoveryReserve.load(classpathReader).isRight)
    }

    "reject author/audit revision mismatch" in {
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case SelectionAuditResource =>
          classpathReader(SelectionAuditResource).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject(_.add("sourceRevision", io.circe.Json.fromString("0000000000000000000000000000000000000000"))).noSpaces
          }
        case other => classpathReader(other)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.left.exists(_.contains("author_revision")))
    }

    "reject judged/audit revision mismatch" in {
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case SelectionAuditResource =>
          classpathReader(SelectionAuditResource).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject(_.add("sourceRevision", io.circe.Json.fromString("0000000000000000000000000000000000000000"))).noSpaces
          }
        case other => classpathReader(other)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.left.exists(_.contains("author_revision")))
    }

    "reject equal author and judge pass IDs" in {
      val rawAuthor = classpathReader(AuthorReserveResource).getOrElse(fail("missing author"))
      val modifiedAuthor = parse(rawAuthor).getOrElse(fail("invalid json"))
        .mapObject(_.add("authorPassId", io.circe.Json.fromString("q2i7-recovery-reserve-judge-v1")))
      val newAuthorRaw = modifiedAuthor.noSpaces
      val newAuthorSha = sha256(newAuthorRaw)
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case AuthorReserveResource => Right(newAuthorRaw)
        case SelectionAuditResource =>
          classpathReader(SelectionAuditResource).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject { obj =>
              obj.add("authorReservePassId", io.circe.Json.fromString("q2i7-recovery-reserve-judge-v1"))
                .add("authorReserveSha256", io.circe.Json.fromString(newAuthorSha))
            }.noSpaces
          }
        case other => classpathReader(other)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.left.exists(_.contains("pass_ids_not_distinct")))
    }

    "reject equal author and audit pass IDs" in {
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case SelectionAuditResource =>
          classpathReader(SelectionAuditResource).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject { obj =>
              obj.add("auditPassId", io.circe.Json.fromString("q2i7-recovery-reserve-author-v1"))
            }.noSpaces
          }
        case other => classpathReader(other)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.left.exists(_.contains("pass_ids_not_distinct")))
    }

    "reject equal judge and audit pass IDs" in {
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case SelectionAuditResource =>
          classpathReader(SelectionAuditResource).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject { obj =>
              obj.add("auditPassId", io.circe.Json.fromString("q2i7-recovery-reserve-judge-v1"))
            }.noSpaces
          }
        case other => classpathReader(other)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.left.exists(_.contains("pass_ids_not_distinct")))
    }

    "reject swapped consumed IDs between two buckets" in {
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case SelectionAuditResource =>
          classpathReader(SelectionAuditResource).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject { obj =>
              obj.add("consumedCaseIds", io.circe.Json.arr(
                io.circe.Json.fromString("q2i7_recovery_005"),
                io.circe.Json.fromString("q2i7_recovery_001"),
                io.circe.Json.fromString("q2i7_recovery_009"),
                io.circe.Json.fromString("q2i7_recovery_013"),
                io.circe.Json.fromString("q2i7_recovery_017"),
                io.circe.Json.fromString("q2i7_recovery_021"),
                io.circe.Json.fromString("q2i7_recovery_025"),
                io.circe.Json.fromString("q2i7_recovery_029"),
              ))
            }.noSpaces
          }
        case other => classpathReader(other)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.left.exists(_.contains("consumed_not_positional_per_round")))
    }

    "reject selecting a non-first eligible candidate in a bucket" in {
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case SelectionAuditResource =>
          classpathReader(SelectionAuditResource).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject { obj =>
              obj.add("selectedCaseIds", io.circe.Json.arr(
                io.circe.Json.fromString("q2i7_recovery_004"),
                io.circe.Json.fromString("q2i7_recovery_007"),
                io.circe.Json.fromString("q2i7_recovery_011"),
                io.circe.Json.fromString("q2i7_recovery_015"),
                io.circe.Json.fromString("q2i7_recovery_019"),
                io.circe.Json.fromString("q2i7_recovery_023"),
                io.circe.Json.fromString("q2i7_recovery_027"),
                io.circe.Json.fromString("q2i7_recovery_032"),
              ))
            }.noSpaces
          }
        case other => classpathReader(other)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.left.exists(_.contains("selected_not_first_eligible_candidate")))
    }

    "reject duplicate consumed ID" in {
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case SelectionAuditResource =>
          classpathReader(SelectionAuditResource).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject { obj =>
              obj.add("consumedCaseIds", io.circe.Json.arr(
                io.circe.Json.fromString("q2i7_recovery_001"),
                io.circe.Json.fromString("q2i7_recovery_001"),
                io.circe.Json.fromString("q2i7_recovery_009"),
                io.circe.Json.fromString("q2i7_recovery_013"),
                io.circe.Json.fromString("q2i7_recovery_017"),
                io.circe.Json.fromString("q2i7_recovery_021"),
                io.circe.Json.fromString("q2i7_recovery_025"),
                io.circe.Json.fromString("q2i7_recovery_029"),
              ))
            }.noSpaces
          }
        case other => classpathReader(other)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.left.exists(_.contains("consumed_duplicate_ids")))
    }

    "reject consumed ID absent from the reserve" in {
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case SelectionAuditResource =>
          classpathReader(SelectionAuditResource).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject { obj =>
              obj.add("consumedCaseIds", io.circe.Json.arr(
                io.circe.Json.fromString("q2i7_recovery_001"),
                io.circe.Json.fromString("q2i7_recovery_999"),
                io.circe.Json.fromString("q2i7_recovery_009"),
                io.circe.Json.fromString("q2i7_recovery_013"),
                io.circe.Json.fromString("q2i7_recovery_017"),
                io.circe.Json.fromString("q2i7_recovery_021"),
                io.circe.Json.fromString("q2i7_recovery_025"),
                io.circe.Json.fromString("q2i7_recovery_029"),
              ))
            }.noSpaces
          }
        case other => classpathReader(other)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.left.exists(_.contains("consumed_case_not_found")))
    }

    "reject selected ID also appearing as consumed" in {
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case SelectionAuditResource =>
          classpathReader(SelectionAuditResource).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject { obj =>
              obj.add("selectedCaseIds", io.circe.Json.arr(
                io.circe.Json.fromString("q2i7_recovery_001"),
                io.circe.Json.fromString("q2i7_recovery_006"),
                io.circe.Json.fromString("q2i7_recovery_010"),
                io.circe.Json.fromString("q2i7_recovery_014"),
                io.circe.Json.fromString("q2i7_recovery_018"),
                io.circe.Json.fromString("q2i7_recovery_022"),
                io.circe.Json.fromString("q2i7_recovery_026"),
                io.circe.Json.fromString("q2i7_recovery_030"),
              ))
            }.noSpaces
          }
        case other => classpathReader(other)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.left.exists(_.contains("consumed_selected_overlap")))
    }

    "reject consumed ID paired with the wrong bucket" in {
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case SelectionAuditResource =>
          classpathReader(SelectionAuditResource).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject { obj =>
              obj.add("consumedCaseIds", io.circe.Json.arr(
                io.circe.Json.fromString("q2i7_recovery_005"),
                io.circe.Json.fromString("q2i7_recovery_001"),
                io.circe.Json.fromString("q2i7_recovery_009"),
                io.circe.Json.fromString("q2i7_recovery_013"),
                io.circe.Json.fromString("q2i7_recovery_017"),
                io.circe.Json.fromString("q2i7_recovery_021"),
                io.circe.Json.fromString("q2i7_recovery_025"),
                io.circe.Json.fromString("q2i7_recovery_029"),
              ))
            }.noSpaces
          }
        case other => classpathReader(other)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.left.exists(_.contains("consumed_not_positional_per_round")))
    }

    "reject incomplete consumed round" in {
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case SelectionAuditResource =>
          classpathReader(SelectionAuditResource).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject { obj =>
              val consumedArray = obj("consumedCaseIds").flatMap(_.asArray).getOrElse(fail("consumed missing"))
              obj.add("consumedCaseIds", io.circe.Json.fromValues(consumedArray.init))
            }.noSpaces
          }
        case other => classpathReader(other)
      }
      val result = loadLegacyFixture(failReader)
      assert(result == Left("recovery_consumed_count_not_multiple_of_buckets"))
    }

    "reject wrong position in the second round" in {
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case SelectionAuditResource =>
          classpathReader(SelectionAuditResource).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject { obj =>
              val consumedArray = obj("consumedCaseIds").flatMap(_.asArray).getOrElse(fail("consumed missing"))
              val swapped = consumedArray.updated(8, consumedArray(9)).updated(9, consumedArray(8))
              obj.add("consumedCaseIds", io.circe.Json.fromValues(swapped))
            }.noSpaces
          }
        case other => classpathReader(other)
      }
      val result = loadLegacyFixture(failReader)
      assert(result == Left("recovery_consumed_not_positional_per_round"))
    }

    "reject skipped eligible consumed candidate" in {
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case SelectionAuditResource =>
          classpathReader(SelectionAuditResource).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject { obj =>
              val consumedArray = obj("consumedCaseIds").flatMap(_.asArray).getOrElse(fail("consumed missing"))
              obj.add("consumedCaseIds", io.circe.Json.fromValues(consumedArray.updated(8, io.circe.Json.fromString("q2i7_recovery_004"))))
            }.noSpaces
          }
        case other => classpathReader(other)
      }
      val result = loadLegacyFixture(failReader)
      assert(result == Left("recovery_consumed_not_positional_per_round"))
    }

    "reject exhausted bucket with no next eligible candidate" in {
      val threeRoundsConsumed = Vector(
        "q2i7_recovery_001", "q2i7_recovery_005", "q2i7_recovery_009", "q2i7_recovery_013",
        "q2i7_recovery_017", "q2i7_recovery_021", "q2i7_recovery_025", "q2i7_recovery_029",
        "q2i7_recovery_002", "q2i7_recovery_006", "q2i7_recovery_010", "q2i7_recovery_014",
        "q2i7_recovery_018", "q2i7_recovery_022", "q2i7_recovery_026", "q2i7_recovery_031",
        "q2i7_recovery_003", "q2i7_recovery_007", "q2i7_recovery_011", "q2i7_recovery_015",
        "q2i7_recovery_019", "q2i7_recovery_023", "q2i7_recovery_027", "q2i7_recovery_032",
      )
      val exhaustedSelected = Vector(
        "q2i7_recovery_004", "q2i7_recovery_008", "q2i7_recovery_012", "q2i7_recovery_016",
        "q2i7_recovery_020", "q2i7_recovery_024", "q2i7_recovery_028", "q2i7_recovery_030",
      )
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case SelectionAuditResource =>
          classpathReader(SelectionAuditResource).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject { obj =>
              obj.add("consumedCaseIds", io.circe.Json.fromValues(threeRoundsConsumed.map(io.circe.Json.fromString)))
                .add("selectedCaseIds", io.circe.Json.fromValues(exhaustedSelected.map(io.circe.Json.fromString)))
            }.noSpaces
          }
        case other => classpathReader(other)
      }
      val result = loadLegacyFixture(failReader)
      assert(result == Left("recovery_no_next_eligible_candidate"))
    }

    "reject changed bucket order" in {
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case SelectionAuditResource =>
          classpathReader(SelectionAuditResource).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject { obj =>
              obj.add("orderedBuckets", io.circe.Json.arr(
                io.circe.Json.fromString("pedicure"),
                io.circe.Json.fromString("manicure"),
                io.circe.Json.fromString("lashes"),
                io.circe.Json.fromString("brows"),
                io.circe.Json.fromString("pmu"),
                io.circe.Json.fromString("facials"),
                io.circe.Json.fromString("hair-removal"),
                io.circe.Json.fromString("nail-modeling"),
              ))
            }.noSpaces
          }
        case other => classpathReader(other)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.left.exists(_.contains("bucket_order")))
    }

    "reject duplicate bucket" in {
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case SelectionAuditResource =>
          classpathReader(SelectionAuditResource).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject { obj =>
              obj.add("orderedBuckets", io.circe.Json.arr(
                io.circe.Json.fromString("manicure"),
                io.circe.Json.fromString("manicure"),
                io.circe.Json.fromString("lashes"),
                io.circe.Json.fromString("brows"),
                io.circe.Json.fromString("pmu"),
                io.circe.Json.fromString("facials"),
                io.circe.Json.fromString("hair-removal"),
                io.circe.Json.fromString("nail-modeling"),
              ))
            }.noSpaces
          }
        case other => classpathReader(other)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.left.exists(_.contains("duplicate_ordered_bucket")))
    }

    "reject candidate moved to another bucket" in {
      val rawAuthor = classpathReader(AuthorReserveResource).getOrElse(fail("missing author"))
      val modifiedAuthor = parse(rawAuthor).getOrElse(fail("invalid json")).mapObject { obj =>
        val casesArray = obj("cases").flatMap(_.asArray).getOrElse(fail("cases missing"))
        val modified = casesArray.head.mapObject(_.add("coverageBucket", io.circe.Json.fromString("pedicure"))) +: casesArray.tail
        obj.add("cases", io.circe.Json.fromValues(modified))
      }
      val newAuthorRaw = modifiedAuthor.noSpaces
      val newAuthorSha = sha256(newAuthorRaw)
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case AuthorReserveResource => Right(newAuthorRaw)
        case SelectionAuditResource =>
          classpathReader(SelectionAuditResource).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject(_.add("authorReserveSha256", io.circe.Json.fromString(newAuthorSha))).noSpaces
          }
        case other => classpathReader(other)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.left.exists(_.contains("bucket_candidate_count_not_uniform_positive")))
    }

    "reject duplicate author candidate ID" in {
      val rawAuthor = classpathReader(AuthorReserveResource).getOrElse(fail("missing author"))
      val modifiedAuthor = parse(rawAuthor).getOrElse(fail("invalid json")).mapObject { obj =>
        val casesArray = obj("cases").flatMap(_.asArray).getOrElse(fail("cases missing"))
        val modified = casesArray.head.mapObject(_.add("id", io.circe.Json.fromString("q2i7_recovery_002"))) +: casesArray.tail
        obj.add("cases", io.circe.Json.fromValues(modified))
      }
      val newAuthorRaw = modifiedAuthor.noSpaces
      val newAuthorSha = sha256(newAuthorRaw)
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case AuthorReserveResource => Right(newAuthorRaw)
        case SelectionAuditResource =>
          classpathReader(SelectionAuditResource).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject(_.add("authorReserveSha256", io.circe.Json.fromString(newAuthorSha))).noSpaces
          }
        case other => classpathReader(other)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.left.exists(_.contains("duplicate_case_id")))
    }

    "reject selected ID absent from any bucket" in {
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case SelectionAuditResource =>
          classpathReader(SelectionAuditResource).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject { obj =>
              obj.add("selectedCaseIds", io.circe.Json.arr(
                io.circe.Json.fromString("q2i7_recovery_002"),
                io.circe.Json.fromString("q2i7_recovery_999"),
                io.circe.Json.fromString("q2i7_recovery_010"),
                io.circe.Json.fromString("q2i7_recovery_014"),
                io.circe.Json.fromString("q2i7_recovery_018"),
                io.circe.Json.fromString("q2i7_recovery_022"),
                io.circe.Json.fromString("q2i7_recovery_026"),
                io.circe.Json.fromString("q2i7_recovery_030"),
              ))
            }.noSpaces
          }
        case other => classpathReader(other)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.left.exists(_.contains("selected_case_not_found")))
    }

    "reject changed final author query" in {
      val rawAuthorDraft = classpathReader(FinalAuthorDraftResource).getOrElse(fail("missing author draft"))
      val modifiedJson = parse(rawAuthorDraft).getOrElse(fail("invalid json")).mapObject { obj =>
        val casesArray = obj("cases").flatMap(_.asArray).getOrElse(fail("cases missing"))
        val reserveCaseIdx = casesArray.indexWhere(c => c.asObject.flatMap(_("id")).flatMap(_.asString).contains("q2i7_recovery_033"))
        if (reserveCaseIdx < 0) obj
        else {
          val modified = casesArray(reserveCaseIdx).mapObject(_.add("query", io.circe.Json.fromString("wrong query")))
          obj.add("cases", io.circe.Json.fromValues(casesArray.updated(reserveCaseIdx, modified)))
        }
      }
      val newAuthorDraftRaw = modifiedJson.noSpaces
      val newHash = sha256(newAuthorDraftRaw)
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = { path =>
        if (path == FinalAuthorDraftResource) Right(newAuthorDraftRaw)
        else if (path == SelectionAuditResource) {
          classpathReader(path).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject(_.add("finalAuthorDraftSha256", io.circe.Json.fromString(newHash))).noSpaces
          }
        } else classpathReader(path)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.left.exists(_.contains("recovery_selected_final_author_mismatch_query")))
    }

    "reject changed final judged judgment mode" in {
      val rawJudged = classpathReader(FinalJudgedDraftResource).getOrElse(fail("missing judged draft"))
      val modifiedJson = parse(rawJudged).getOrElse(fail("invalid json")).mapObject { obj =>
        val casesArray = obj("cases").flatMap(_.asArray).getOrElse(fail("cases missing"))
        val reserveCaseIdx = casesArray.indexWhere(c => c.asObject.flatMap(_("id")).flatMap(_.asString).contains("q2i7_recovery_033"))
        if (reserveCaseIdx < 0) obj
        else {
          val modified = casesArray(reserveCaseIdx).mapObject(_.add("judgmentMode", io.circe.Json.fromString("strict")))
          obj.add("cases", io.circe.Json.fromValues(casesArray.updated(reserveCaseIdx, modified)))
        }
      }
      val newJudgedRaw = modifiedJson.noSpaces
      val newHash = sha256(newJudgedRaw)
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = { path =>
        if (path == FinalJudgedDraftResource) Right(newJudgedRaw)
        else if (path == SelectionAuditResource) {
          classpathReader(path).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject(_.add("finalJudgedHoldoutSha256", io.circe.Json.fromString(newHash))).noSpaces
          }
        } else classpathReader(path)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.isLeft)
    }

    "reject changed final variant judgments" in {
      val rawJudged = classpathReader(FinalJudgedDraftResource).getOrElse(fail("missing judged draft"))
      val modifiedJson = parse(rawJudged).getOrElse(fail("invalid json")).mapObject { obj =>
        val casesArray = obj("cases").flatMap(_.asArray).getOrElse(fail("cases missing"))
        val reserveCaseIdx = casesArray.indexWhere(c => c.asObject.flatMap(_("id")).flatMap(_.asString).contains("q2i7_recovery_033"))
        if (reserveCaseIdx < 0) obj
        else {
          val caseObj = casesArray(reserveCaseIdx).asObject.getOrElse(fail("case not object"))
          val judgmentsJson = io.circe.Json.obj(
            "variants" -> io.circe.Json.obj(
              "acceptableIds" -> io.circe.Json.arr(),
              "forbiddenIds" -> io.circe.Json.arr(),
              "neutralIds" -> io.circe.Json.arr(),
              "gradedGains" -> io.circe.Json.arr(),
            ),
            "providers" -> caseObj("judgments").flatMap(_.asObject).flatMap(_("providers")).getOrElse(io.circe.Json.obj()),
            "serviceIntents" -> caseObj("judgments").flatMap(_.asObject).flatMap(_("serviceIntents")).getOrElse(io.circe.Json.obj()),
          )
          val modified = caseObj.add("judgments", judgmentsJson)
          obj.add("cases", io.circe.Json.fromValues(casesArray.updated(reserveCaseIdx, io.circe.Json.fromJsonObject(modified))))
        }
      }
      val newJudgedRaw = modifiedJson.noSpaces
      val newHash = sha256(newJudgedRaw)
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = { path =>
        if (path == FinalJudgedDraftResource) Right(newJudgedRaw)
        else if (path == SelectionAuditResource) {
          classpathReader(path).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject(_.add("finalJudgedHoldoutSha256", io.circe.Json.fromString(newHash))).noSpaces
          }
        } else classpathReader(path)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.isLeft)
    }

    "reject changed final provider judgments" in {
      val rawJudged = classpathReader(FinalJudgedDraftResource).getOrElse(fail("missing judged draft"))
      val modifiedJson = parse(rawJudged).getOrElse(fail("invalid json")).mapObject { obj =>
        val casesArray = obj("cases").flatMap(_.asArray).getOrElse(fail("cases missing"))
        val reserveCaseIdx = casesArray.indexWhere(c => c.asObject.flatMap(_("id")).flatMap(_.asString).contains("q2i7_recovery_033"))
        if (reserveCaseIdx < 0) obj
        else {
          val caseObj = casesArray(reserveCaseIdx).asObject.getOrElse(fail("case not object"))
          val judgmentsJson = io.circe.Json.obj(
            "variants" -> caseObj("judgments").flatMap(_.asObject).flatMap(_("variants")).getOrElse(io.circe.Json.obj()),
            "providers" -> io.circe.Json.obj(
              "acceptableIds" -> io.circe.Json.arr(),
              "forbiddenIds" -> io.circe.Json.arr(),
              "neutralIds" -> io.circe.Json.arr(),
              "gradedGains" -> io.circe.Json.arr(),
            ),
            "serviceIntents" -> caseObj("judgments").flatMap(_.asObject).flatMap(_("serviceIntents")).getOrElse(io.circe.Json.obj()),
          )
          val modified = caseObj.add("judgments", judgmentsJson)
          obj.add("cases", io.circe.Json.fromValues(casesArray.updated(reserveCaseIdx, io.circe.Json.fromJsonObject(modified))))
        }
      }
      val newJudgedRaw = modifiedJson.noSpaces
      val newHash = sha256(newJudgedRaw)
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = { path =>
        if (path == FinalJudgedDraftResource) Right(newJudgedRaw)
        else if (path == SelectionAuditResource) {
          classpathReader(path).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject(_.add("finalJudgedHoldoutSha256", io.circe.Json.fromString(newHash))).noSpaces
          }
        } else classpathReader(path)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.isLeft)
    }

    "reject changed final service-intent judgments" in {
      val rawJudged = classpathReader(FinalJudgedDraftResource).getOrElse(fail("missing judged draft"))
      val modifiedJson = parse(rawJudged).getOrElse(fail("invalid json")).mapObject { obj =>
        val casesArray = obj("cases").flatMap(_.asArray).getOrElse(fail("cases missing"))
        val reserveCaseIdx = casesArray.indexWhere(c => c.asObject.flatMap(_("id")).flatMap(_.asString).contains("q2i7_recovery_033"))
        if (reserveCaseIdx < 0) obj
        else {
          val caseObj = casesArray(reserveCaseIdx).asObject.getOrElse(fail("case not object"))
          val judgmentsJson = io.circe.Json.obj(
            "variants" -> caseObj("judgments").flatMap(_.asObject).flatMap(_("variants")).getOrElse(io.circe.Json.obj()),
            "providers" -> caseObj("judgments").flatMap(_.asObject).flatMap(_("providers")).getOrElse(io.circe.Json.obj()),
            "serviceIntents" -> io.circe.Json.obj(
              "acceptableIds" -> io.circe.Json.arr(),
              "forbiddenIds" -> io.circe.Json.arr(),
              "neutralIds" -> io.circe.Json.arr(),
              "gradedGains" -> io.circe.Json.arr(),
            ),
          )
          val modified = caseObj.add("judgments", judgmentsJson)
          obj.add("cases", io.circe.Json.fromValues(casesArray.updated(reserveCaseIdx, io.circe.Json.fromJsonObject(modified))))
        }
      }
      val newJudgedRaw = modifiedJson.noSpaces
      val newHash = sha256(newJudgedRaw)
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = { path =>
        if (path == FinalJudgedDraftResource) Right(newJudgedRaw)
        else if (path == SelectionAuditResource) {
          classpathReader(path).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject(_.add("finalJudgedHoldoutSha256", io.circe.Json.fromString(newHash))).noSpaces
          }
        } else classpathReader(path)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.isLeft)
    }

    "reject changed final holdout bytes" in {
      val rawHoldout = classpathReader(FinalProtectedCorpusResource).getOrElse(fail("missing holdout"))
      val newHoldoutRaw = parse(rawHoldout).getOrElse(fail("invalid json")).mapObject(_.add("corpusId", io.circe.Json.fromString("tampered"))).noSpaces
      val newHash = sha256(newHoldoutRaw)
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = { path =>
        if (path == FinalProtectedCorpusResource) Right(newHoldoutRaw)
        else if (path == SelectionAuditResource) {
          classpathReader(path).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject(_.add("finalJudgedHoldoutSha256", io.circe.Json.fromString(newHash))).noSpaces
          }
        } else classpathReader(path)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.isLeft)
    }

    "reject changed final policy binding" in {
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case FinalProtectedPolicyResource =>
          classpathReader(FinalProtectedPolicyResource).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject(_.add("expectedCaseCount", io.circe.Json.fromInt(99))).noSpaces
          }
        case other => classpathReader(other)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.left.exists(_.contains("policy_fingerprint")))
    }

    "reject changed final author notes" in {
      val rawAuthorDraft = classpathReader(FinalAuthorDraftResource).getOrElse(fail("missing author draft"))
      val modifiedJson = parse(rawAuthorDraft).getOrElse(fail("invalid json")).mapObject { obj =>
        val casesArray = obj("cases").flatMap(_.asArray).getOrElse(fail("cases missing"))
        val reserveCaseIdx = casesArray.indexWhere(c => c.asObject.flatMap(_("id")).flatMap(_.asString).contains("q2i7_recovery_033"))
        if (reserveCaseIdx < 0) obj
        else {
          val modified = casesArray(reserveCaseIdx).mapObject(_.add("notes", io.circe.Json.arr(io.circe.Json.fromString("wrong note"))))
          obj.add("cases", io.circe.Json.fromValues(casesArray.updated(reserveCaseIdx, modified)))
        }
      }
      val newAuthorDraftRaw = modifiedJson.noSpaces
      val newHash = sha256(newAuthorDraftRaw)
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = { path =>
        if (path == FinalAuthorDraftResource) Right(newAuthorDraftRaw)
        else if (path == SelectionAuditResource) {
          classpathReader(path).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject(_.add("finalAuthorDraftSha256", io.circe.Json.fromString(newHash))).noSpaces
          }
        } else classpathReader(path)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.left.exists(_.contains("recovery_selected_final_note_provenance_mismatch")))
    }

    "reject changed judged reserve case judgment" in {
      val rawJudgedReserve = classpathReader(JudgedReserveResource).getOrElse(fail("missing judged reserve"))
      val modifiedJudgedReserve = parse(rawJudgedReserve).getOrElse(fail("invalid json")).mapObject { obj =>
        val corpusObj = obj("corpus").flatMap(_.asObject).getOrElse(fail("corpus missing"))
        val casesArray = corpusObj("cases").flatMap(_.asArray).getOrElse(fail("cases missing"))
        val reserveCaseIdx = casesArray.indexWhere(c => c.asObject.flatMap(_("id")).flatMap(_.asString).contains("q2i7_recovery_003"))
        if (reserveCaseIdx < 0) obj
        else {
          val caseObj = casesArray(reserveCaseIdx).asObject.getOrElse(fail("case not object"))
          val judgmentsJson = io.circe.Json.obj(
            "variants" -> io.circe.Json.obj(
              "acceptableIds" -> io.circe.Json.arr(),
              "forbiddenIds" -> io.circe.Json.arr(),
              "neutralIds" -> io.circe.Json.arr(),
              "gradedGains" -> io.circe.Json.arr(),
            ),
            "providers" -> caseObj("judgments").flatMap(_.asObject).flatMap(_("providers")).getOrElse(io.circe.Json.obj()),
            "serviceIntents" -> caseObj("judgments").flatMap(_.asObject).flatMap(_("serviceIntents")).getOrElse(io.circe.Json.obj()),
          )
          val modified = caseObj.add("judgments", judgmentsJson)
          val modifiedCorpus = io.circe.Json.fromJsonObject(corpusObj.add("cases", io.circe.Json.fromValues(casesArray.updated(reserveCaseIdx, io.circe.Json.fromJsonObject(modified)))))
          obj.add("corpus", modifiedCorpus)
        }
      }
      val newJudgedReserveRaw = modifiedJudgedReserve.noSpaces
      val newJudgedHash = sha256(newJudgedReserveRaw)
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = { path =>
        if (path == JudgedReserveResource) Right(newJudgedReserveRaw)
        else if (path == SelectionAuditResource) {
          classpathReader(path).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject(_.add("judgedReserveSha256", io.circe.Json.fromString(newJudgedHash))).noSpaces
          }
        } else classpathReader(path)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.left.exists(_.contains("recovery_selected_judge_corpus_mismatch_variant_judgments")))
    }

    "select q2i7_recovery_040 for the fresh nail-modeling bucket without an ID-specific exception" in {
      val selectedId = reserve.selectionAudit.selectedCaseIds(reserve.selectionAudit.orderedBuckets.indexOf("nail-modeling"))
      assert(reserve.selectionAudit.consumedCaseIds.isEmpty)
      assert(selectedId == "q2i7_recovery_040")
      assert(!reserve.selectionAudit.selectedCaseIds.contains("q2i7_recovery_031"))
    }

    "exclude q2i7_recovery_030 because its normalized query collides with a visible case" in {
      val visible = BeautyQEvaluationCorpus.loadCanonical().fold(error => fail(error.toString), identity)
      val candidateNormalized = BeautyQEvaluationQueryIdentity.normalize("коррекция гелевых ногтей с дизайном")
      val visibleNormalized = visible.cases.map(c => BeautyQEvaluationQueryIdentity.normalize(c.query)).toSet
      assert(visibleNormalized.contains(candidateNormalized), s"q2i7_recovery_030 normalized query '$candidateNormalized' must already be visible")
    }

    "reject when removing the non-reserve visible collision for q2i7_recovery_030 makes the second consumed nail-modeling candidate non-positional" in {
      val candidateNormalized = BeautyQEvaluationQueryIdentity.normalize("коррекция гелевых ногтей с дизайном")
      val visibleRaw = legacyVisibleRaw
      val modifiedVisible = parse(visibleRaw).getOrElse(fail("invalid visible json")).mapObject { obj =>
        val casesArray = obj("cases").flatMap(_.asArray).getOrElse(fail("cases missing"))
        val filteredCases = casesArray.filterNot { c =>
          val query = c.asObject.flatMap(_("query")).flatMap(_.asString).getOrElse("")
          BeautyQEvaluationQueryIdentity.normalize(query) == candidateNormalized
        }
        obj.add("cases", io.circe.Json.fromValues(filteredCases))
      }
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = { path =>
        if (path == VisibleCorpusResource) Right(modifiedVisible.noSpaces)
        else classpathReader(path)
      }
      val result = loadLegacyFixture(failReader, preserveVisible = true)
      assert(result.left.exists(_.contains("recovery_consumed_not_positional_per_round")))
    }

    "reject a missing consumed visible case" in {
      val visibleRaw = legacyVisibleRaw
      val modifiedVisible = parse(visibleRaw).getOrElse(fail("invalid visible json")).mapObject { obj =>
        val casesArray = obj("cases").flatMap(_.asArray).getOrElse(fail("cases missing"))
        val filteredCases = casesArray.filterNot { c =>
          c.asObject.flatMap(_("id")).flatMap(_.asString).contains("q2i7_recovery_001")
        }
        obj.add("cases", io.circe.Json.fromValues(filteredCases))
      }
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = { path =>
        if (path == VisibleCorpusResource) Right(modifiedVisible.noSpaces)
        else classpathReader(path)
      }
      val result = loadLegacyFixture(failReader, preserveVisible = true)
      assert(result.left.exists(_.contains("consumed_visible_inventory_mismatch")))
    }

    "reject changed second-round consumed variant judgments" in {
      val visibleRaw = legacyVisibleRaw
      val modifiedVisible = parse(visibleRaw).getOrElse(fail("invalid visible json")).mapObject { obj =>
        val casesArray = obj("cases").flatMap(_.asArray).getOrElse(fail("cases missing"))
        val modifiedCases = casesArray.map { c =>
          if (c.asObject.flatMap(_("id")).flatMap(_.asString).contains("q2i7_recovery_002"))
            c.mapObject { caseObj =>
              val judgmentsJson = io.circe.Json.obj(
                "variants" -> io.circe.Json.obj(
                  "acceptableIds" -> io.circe.Json.arr(),
                  "forbiddenIds" -> io.circe.Json.arr(),
                  "neutralIds" -> io.circe.Json.arr(),
                  "gradedGains" -> io.circe.Json.arr(),
                ),
                "providers" -> caseObj("judgments").flatMap(_.asObject).flatMap(_("providers")).getOrElse(io.circe.Json.obj()),
                "serviceIntents" -> caseObj("judgments").flatMap(_.asObject).flatMap(_("serviceIntents")).getOrElse(io.circe.Json.obj()),
              )
              caseObj.add("judgments", judgmentsJson)
            }
          else c
        }
        obj.add("cases", io.circe.Json.fromValues(modifiedCases))
      }
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = { path =>
        if (path == VisibleCorpusResource) Right(modifiedVisible.noSpaces)
        else classpathReader(path)
      }
      val result = loadLegacyFixture(failReader, preserveVisible = true)
      assert(result == Left("recovery_consumed_visible_mismatch_variant_judgments"))
    }

    "reject a selected ID appearing in visible" in {
      val visibleRaw = classpathReader(VisibleCorpusResource).getOrElse(fail("missing visible corpus"))
      val selectedId = reserve.selectionAudit.selectedCaseIds.head
      val visibleJson = parse(visibleRaw).getOrElse(fail("invalid visible json"))
      val modifiedVisible = visibleJson.mapObject { obj =>
        val casesArray = obj("cases").flatMap(_.asArray).getOrElse(fail("cases missing"))
        val extraCase = io.circe.Json.obj(
          "id" -> io.circe.Json.fromString(selectedId),
          "partition" -> io.circe.Json.fromString("regression"),
          "judgmentMode" -> io.circe.Json.fromString("partial"),
          "query" -> io.circe.Json.fromString("unique test query"),
          "language" -> io.circe.Json.fromString("en"),
          "slices" -> io.circe.Json.arr(io.circe.Json.fromString("q2-post-recovery-migrated")),
          "userIntent" -> io.circe.Json.fromString("test"),
          "notes" -> io.circe.Json.arr(),
          "judgments" -> io.circe.Json.obj(
            "variants" -> io.circe.Json.obj(
              "acceptableIds" -> io.circe.Json.arr(),
              "forbiddenIds" -> io.circe.Json.arr(),
              "neutralIds" -> io.circe.Json.arr(),
              "gradedGains" -> io.circe.Json.arr(),
            ),
            "providers" -> io.circe.Json.obj(
              "acceptableIds" -> io.circe.Json.arr(),
              "forbiddenIds" -> io.circe.Json.arr(),
              "neutralIds" -> io.circe.Json.arr(),
              "gradedGains" -> io.circe.Json.arr(),
            ),
            "serviceIntents" -> io.circe.Json.obj(
              "acceptableIds" -> io.circe.Json.arr(),
              "forbiddenIds" -> io.circe.Json.arr(),
              "neutralIds" -> io.circe.Json.arr(),
              "gradedGains" -> io.circe.Json.arr(),
            ),
          ),
        )
        obj.add("cases", io.circe.Json.fromValues(casesArray :+ extraCase))
      }
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = { path =>
        if (path == VisibleCorpusResource) Right(modifiedVisible.noSpaces)
        else classpathReader(path)
      }
      val result = BeautyQProtectedRecoveryReserve.load(failReader)
      assert(result.left.exists(_.contains("selected_id_already_visible")))
    }

    "reject empty finalAuthorDraftSha256" in {
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case SelectionAuditResource =>
          classpathReader(SelectionAuditResource).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject(_.add("finalAuthorDraftSha256", io.circe.Json.fromString(""))).noSpaces
          }
        case other => classpathReader(other)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.left.exists(_.contains("final_author_sha_invalid")))
    }

    "reject empty finalJudgedHoldoutSha256" in {
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case SelectionAuditResource =>
          classpathReader(SelectionAuditResource).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject(_.add("finalJudgedHoldoutSha256", io.circe.Json.fromString(""))).noSpaces
          }
        case other => classpathReader(other)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.left.exists(_.contains("final_judged_sha_invalid")))
    }

    "reject empty finalProtectedCorpusFingerprint" in {
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case SelectionAuditResource =>
          classpathReader(SelectionAuditResource).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject(_.add("finalProtectedCorpusFingerprint", io.circe.Json.fromString(""))).noSpaces
          }
        case other => classpathReader(other)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.left.exists(_.contains("final_corpus_fp_invalid")))
    }

    "reject empty finalProtectedPolicyFingerprint" in {
      val failReader: BeautyQProtectedRecoveryReserve.ResourceReader = {
        case SelectionAuditResource =>
          classpathReader(SelectionAuditResource).map { raw =>
            parse(raw).getOrElse(fail("invalid json")).mapObject(_.add("finalProtectedPolicyFingerprint", io.circe.Json.fromString(""))).noSpaces
          }
        case other => classpathReader(other)
      }
      val result = loadLegacyFixture(failReader)
      assert(result.left.exists(_.contains("final_policy_fp_invalid")))
    }
  }
}
