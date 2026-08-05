package leaderboard.search.beautyq.gen2.eval

import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import leaderboard.search.gen2.eval.{EvaluationPartition, EvaluationSliceId}

import java.security.MessageDigest
import java.nio.charset.StandardCharsets

object BeautyQProtectedRecoveryReserve {
  type ResourceReader = String => Either[String, String]

  final class JudgedRecoveryReserve(
    val schemaVersion: String,
    val sourceRevision: String,
    val judgePassId: String,
    val corpus: BeautyQEvaluationCorpus,
  )

  final class AuthorReserve(
    val schemaVersion: String,
    val sourceRevision: String,
    val authorPassId: String,
    val cases: Vector[AuthorReserveCase],
  )

  final case class AuthorReserveCase(
    id: String,
    query: String,
    language: String,
    primarySlice: String,
    additionalSlices: Vector[String],
    userIntent: String,
    notes: Vector[String],
    coverageBucket: String,
  )

  final class SelectionAudit(
    val schemaVersion: String,
    val sourceRevision: String,
    val authorReservePassId: String,
    val judgeReservePassId: String,
    val auditPassId: String,
    val authorReserveSha256: String,
    val judgedReserveSha256: String,
    val candidateCount: Int,
    val orderedBuckets: Vector[String],
    val consumedCaseIds: Vector[String],
    val selectedCaseIds: Vector[String],
    val finalAuthorDraftSha256: String,
    val finalJudgedHoldoutSha256: String,
    val finalProtectedCorpusFingerprint: String,
    val finalProtectedPolicyFingerprint: String,
    val canonicalCatalogFingerprint: String,
  )

  private val AuthorReserveResource =
    "leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-recovery-author-reserve-v1.json"
  private val JudgedReserveResource =
    "leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-recovery-judged-reserve-v1.json"
  private val SelectionAuditResource =
    "leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-recovery-selection-audit-v2.json"
  private val VisibleCorpusResource =
    "leaderboard/search/beautyq/gen2/eval/beautyq_evaluation_corpus_v2.json"
  private val FinalAuthorDraftResource =
    "leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-author-draft-v1.json"
  private val FinalJudgedDraftResource =
    "leaderboard/search/beautyq/gen2/eval/protected/provenance/beautyq-protected-judged-draft-v1.json"
  private val FinalProtectedCorpusResource =
    "leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-holdout-v1.json"
  private val FinalProtectedPolicyResource =
    "leaderboard/search/beautyq/gen2/eval/protected/beautyq-protected-acceptance-policy-v1.json"

  private val RevisionPattern = "^[0-9a-f]{40}$".r
  private val DigestPattern = "^[0-9a-f]{64}$".r

  private val AuthorReserveRootFields = Set("schemaVersion", "sourceRevision", "authorPassId", "cases")
  private val AuthorReserveCaseFields = Set("id", "query", "language", "primarySlice", "additionalSlices", "userIntent", "notes", "coverageBucket")
  private val CurrentAuthorSchema = "beautyq-protected-recovery-author-reserve-v1"

  private val JudgedReserveRootFields = Set("schemaVersion", "sourceRevision", "judgePassId", "corpus")
  private val CurrentJudgedSchema = "beautyq-protected-recovery-judged-reserve-v1"

  private val SelectionAuditRootFields = Set(
    "schemaVersion", "sourceRevision", "authorReservePassId", "judgeReservePassId", "auditPassId",
    "authorReserveSha256", "judgedReserveSha256", "candidateCount", "orderedBuckets", "consumedCaseIds", "selectedCaseIds",
    "finalAuthorDraftSha256", "finalJudgedHoldoutSha256", "finalProtectedCorpusFingerprint",
    "finalProtectedPolicyFingerprint", "canonicalCatalogFingerprint",
  )
  private val CurrentAuditSchema = "beautyq-protected-recovery-selection-audit-v2"

  def load(readResource: ResourceReader): Either[String, BeautyQProtectedRecoveryReserve] = for {
    authorRaw <- readResource(AuthorReserveResource)
    judgedRaw <- readResource(JudgedReserveResource)
    auditRaw <- readResource(SelectionAuditResource)
    visibleRaw <- readResource(VisibleCorpusResource)
    author <- decodeAuthorReserve(authorRaw)
    judged <- decodeJudgedReserve(judgedRaw)
    audit <- decodeSelectionAudit(auditRaw)
    visible <- parse(visibleRaw).left.map(_ => "recovery_visible_corpus_invalid_json")
      .flatMap(BeautyQEvaluationCorpus.decodeFromJson)
      .left.map(_ => "recovery_visible_corpus_decode_failed")
    _ <- validateHashes(authorRaw, judgedRaw, audit)
    _ <- validateSourceRevisions(author, judged, audit)
    _ <- validatePassBinding(author, judged, audit)
    _ <- validatePassSeparation(author, judged, audit)
    _ <- validateInventories(author, judged.corpus, audit)
    _ <- validateBucketStructure(author)
    _ <- validateSelection(audit, author)
    _ <- validateConsumedSelection(audit)
    _ <- validatePositionalSelection(audit, author, visible)
    _ <- validateAuthorJudgeCorrespondence(author, judged.corpus)
    _ <- validateSelectedNotVisible(audit, author, visible)
    _ <- validateConsumedVisibleProof(author, judged.corpus, audit, visible)
    _ <- validateFinalBindings(readResource, audit, author, judged.corpus)
  } yield new BeautyQProtectedRecoveryReserve(author, judged, audit)

  private def validateSourceRevisions(author: AuthorReserve, judged: JudgedRecoveryReserve, audit: SelectionAudit): Either[String, Unit] = {
    if (author.sourceRevision != audit.sourceRevision) Left("recovery_author_revision_mismatch")
    else if (judged.sourceRevision != audit.sourceRevision) Left("recovery_judged_revision_mismatch")
    else Right(())
  }

  private def validatePassBinding(author: AuthorReserve, judged: JudgedRecoveryReserve, audit: SelectionAudit): Either[String, Unit] = {
    if (author.authorPassId != audit.authorReservePassId) Left("recovery_author_pass_binding_mismatch")
    else if (judged.judgePassId != audit.judgeReservePassId) Left("recovery_judge_pass_binding_mismatch")
    else Right(())
  }

  private def validatePassSeparation(author: AuthorReserve, judged: JudgedRecoveryReserve, audit: SelectionAudit): Either[String, Unit] = {
    val ids = Vector(author.authorPassId, judged.judgePassId, audit.auditPassId)
    if (ids.distinct.size != ids.size) Left("recovery_pass_ids_not_distinct")
    else Right(())
  }

  private def validateHashes(authorRaw: String, judgedRaw: String, audit: SelectionAudit): Either[String, Unit] = {
    val actualAuthorSha = sha256(authorRaw)
    val actualJudgedSha = sha256(judgedRaw)
    if (actualAuthorSha != audit.authorReserveSha256) Left("recovery_author_reserve_sha_mismatch")
    else if (actualJudgedSha != audit.judgedReserveSha256) Left("recovery_judged_reserve_sha_mismatch")
    else Right(())
  }

  private def validateInventories(author: AuthorReserve, judged: BeautyQEvaluationCorpus, audit: SelectionAudit): Either[String, Unit] = {
    if (author.cases.size != judged.cases.size) Left("recovery_inventory_size_mismatch")
    else if (author.cases.size != audit.candidateCount) Left("recovery_candidate_count_mismatch")
    else if (author.cases.map(_.id) != judged.cases.map(_.caseId.value)) Left("recovery_inventory_order_mismatch")
    else Right(())
  }

  private def validateBucketStructure(author: AuthorReserve): Either[String, Unit] = {
    val ids = author.cases.map(_.id)
    if (ids.distinct.size != ids.size) Left("recovery_duplicate_author_case_id")
    else {
      val declaredBuckets = author.cases.map(_.coverageBucket).distinct
      if (declaredBuckets.size != 8) Left("recovery_bucket_count_not_eight")
      else {
        val grouped = author.cases.groupBy(_.coverageBucket).view.mapValues(_.size).toMap
        val broken = grouped.find(_._2 != 4)
        broken match {
          case Some(_) => Left("recovery_bucket_candidate_count_not_four")
          case None => Right(())
        }
      }
    }
  }

  private def validateSelection(audit: SelectionAudit, author: AuthorReserve): Either[String, Unit] = {
    if (audit.consumedCaseIds.size != audit.orderedBuckets.size) Left("recovery_consumed_count_mismatch")
    else if (audit.consumedCaseIds.distinct.size != audit.consumedCaseIds.size) Left("recovery_consumed_duplicate_ids")
    else if (audit.selectedCaseIds.size != audit.orderedBuckets.size) Left("recovery_selected_count_mismatch")
    else if (audit.selectedCaseIds.distinct.size != audit.selectedCaseIds.size) Left("recovery_selected_duplicate_ids")
    else {
      val authorIds = author.cases.map(_.id).toSet
      val allConsumedExist = audit.consumedCaseIds.forall(authorIds.contains)
      if (!allConsumedExist) Left("recovery_consumed_case_not_found")
      else {
        val allSelectedExist = audit.selectedCaseIds.forall(authorIds.contains)
        if (!allSelectedExist) Left("recovery_selected_case_not_found")
        else Right(())
      }
    }
  }

  private def validateConsumedSelection(audit: SelectionAudit): Either[String, Unit] = {
    val consumedSet = audit.consumedCaseIds.toSet
    val selectedSet = audit.selectedCaseIds.toSet
    if (consumedSet.intersect(selectedSet).nonEmpty) Left("recovery_consumed_selected_overlap")
    else Right(())
  }

  private def validatePositionalSelection(audit: SelectionAudit, author: AuthorReserve, visible: BeautyQEvaluationCorpus): Either[String, Unit] = {
    val orderedBuckets = audit.orderedBuckets
    val consumedCaseIds = audit.consumedCaseIds
    val selectedCaseIds = audit.selectedCaseIds
    val bucketOrderDeclared = author.cases.map(_.coverageBucket).distinct
    if (orderedBuckets.size != 8) Left("recovery_ordered_bucket_count_not_eight")
    else if (orderedBuckets.distinct.size != orderedBuckets.size) Left("recovery_duplicate_ordered_bucket")
    else if (orderedBuckets != bucketOrderDeclared) Left("recovery_bucket_order_mismatch")
    else {
      val consumedPairs = orderedBuckets.zip(consumedCaseIds)
      val consumedBroken = consumedPairs.find { case (bucket, consumedId) =>
        val bucketCandidates = author.cases.filter(_.coverageBucket == bucket)
        val bucketHead = bucketCandidates.headOption.map(_.id)
        !(bucketHead.contains(consumedId) && bucketCandidates.exists(_.id == consumedId))
      }
      consumedBroken match {
        case Some((bucket, consumedId)) =>
          val bucketCandidates = author.cases.filter(_.coverageBucket == bucket)
          if (!bucketCandidates.exists(_.id == consumedId)) Left("recovery_consumed_case_not_in_paired_bucket")
          else Left("recovery_consumed_not_first_candidate")
        case None =>
          val consumedSet = consumedCaseIds.toSet
          val visibleNormalized = visible.cases.map(c => BeautyQEvaluationQueryIdentity.normalize(c.query)).toSet
          val selectedBroken = orderedBuckets.zip(selectedCaseIds).find { case (bucket, selectedId) =>
            val bucketCandidates = author.cases.filter(_.coverageBucket == bucket)
            val remainingCandidates = bucketCandidates.filter(c =>
              !consumedSet.contains(c.id) &&
              !visibleNormalized.contains(BeautyQEvaluationQueryIdentity.normalize(c.query))
            )
            val firstRemaining = remainingCandidates.headOption.map(_.id)
            !(firstRemaining.contains(selectedId) && bucketCandidates.exists(_.id == selectedId))
          }
          selectedBroken match {
            case Some((bucket, selectedId)) =>
              val bucketCandidates = author.cases.filter(_.coverageBucket == bucket)
              if (!bucketCandidates.exists(_.id == selectedId)) Left("recovery_selected_case_not_in_paired_bucket")
              else Left("recovery_selected_not_first_eligible_candidate")
            case None => Right(())
          }
      }
    }
  }

  private def validateAuthorJudgeCorrespondence(author: AuthorReserve, judged: BeautyQEvaluationCorpus): Either[String, Unit] = {
    val pairs = author.cases.zip(judged.cases)
    val mismatchCategory = pairs.collectFirst {
      case (ac, jc) if ac.id != jc.caseId.value => "case_id"
      case (ac, jc) if ac.query != jc.query => "query"
      case (ac, jc) if ac.language != jc.language => "language"
      case (ac, jc) if (ac.primarySlice +: ac.additionalSlices) != jc.slices.map(_.value) => "slice"
      case (ac, jc) if ac.userIntent != jc.userIntent => "user_intent"
      case (ac, jc) if ac.notes != jc.notes => "notes"
    }
    mismatchCategory match {
      case Some(category) => Left(s"recovery_author_judge_mismatch_${category}")
      case None => Right(())
    }
  }

  private def validateConsumedVisibleProof(
    author: AuthorReserve,
    judged: BeautyQEvaluationCorpus,
    audit: SelectionAudit,
    visible: BeautyQEvaluationCorpus,
  ): Either[String, Unit] = {
    val allReserveIds = author.cases.map(_.id).toSet
    val visibleReserveCases = visible.cases.filter(c => allReserveIds.contains(c.caseId.value))
    if (visibleReserveCases.map(_.caseId.value) != audit.consumedCaseIds)
      Left("recovery_consumed_visible_inventory_mismatch")
    else {
      val judgedById = judged.cases.map(c => c.caseId.value -> c).toMap
      val mismatches = visibleReserveCases.flatMap { vc =>
        val jc = judgedById(vc.caseId.value)
        if (vc.caseId != jc.caseId) Vector("id")
        else if (vc.judgmentMode != jc.judgmentMode) Vector("judgment_mode")
        else if (vc.query != jc.query) Vector("query")
        else if (vc.language != jc.language) Vector("language")
        else if (vc.userIntent != jc.userIntent) Vector("user_intent")
        else if (vc.variantJudgments != jc.variantJudgments) Vector("variant_judgments")
        else if (vc.providerJudgments != jc.providerJudgments) Vector("provider_judgments")
        else if (vc.serviceIntentJudgments != jc.serviceIntentJudgments) Vector("service_intent_judgments")
        else Vector.empty
      }
      mismatches.headOption match {
        case Some(category) => Left(s"recovery_consumed_visible_mismatch_${category}")
        case None =>
          if (!visibleReserveCases.forall(_.partition == EvaluationPartition.Regression))
            Left("recovery_consumed_visible_partition_not_regression")
          else {
            val sliceMismatch = visibleReserveCases.find { vc =>
              val jc = judgedById(vc.caseId.value)
              val expectedSlices = jc.slices :+ EvaluationSliceId.from("q2-post-recovery-migrated").getOrElse(
                throw new AssertionError("q2-post-recovery-migrated slice id is invalid")
              )
              vc.slices != expectedSlices
            }
            sliceMismatch match {
              case Some(_) => Left("recovery_consumed_visible_slice_mismatch")
              case None => Right(())
            }
          }
      }
    }
  }

  private def validateSelectedNotVisible(
    audit: SelectionAudit,
    author: AuthorReserve,
    visible: BeautyQEvaluationCorpus,
  ): Either[String, Unit] = {
    val visibleIds = visible.cases.map(_.caseId.value).toSet
    val selectedIds = audit.selectedCaseIds.toSet
    if (selectedIds.intersect(visibleIds).nonEmpty) Left("recovery_selected_id_already_visible")
    else {
      val visibleNormalized = visible.cases.map(c => BeautyQEvaluationQueryIdentity.normalize(c.query)).toSet
      val authorById = author.cases.map(c => c.id -> c).toMap
      val selectedQueryVisible = audit.selectedCaseIds.exists { sid =>
        val query = authorById(sid).query
        visibleNormalized.contains(BeautyQEvaluationQueryIdentity.normalize(query))
      }
      if (selectedQueryVisible) Left("recovery_selected_query_already_visible")
      else Right(())
    }
  }

  private def validateFinalBindings(
    readResource: ResourceReader,
    audit: SelectionAudit,
    authorReserve: AuthorReserve,
    judgedReserveCorpus: BeautyQEvaluationCorpus,
  ): Either[String, Unit] = for {
    authorDraftRaw <- readResource(FinalAuthorDraftResource)
    judgedDraftRaw <- readResource(FinalJudgedDraftResource)
    protectedCorpusRaw <- readResource(FinalProtectedCorpusResource)
    protectedPolicyRaw <- readResource(FinalProtectedPolicyResource)
    actualAuthorDraftSha = sha256(authorDraftRaw)
    _ <- Either.cond(actualAuthorDraftSha == audit.finalAuthorDraftSha256, (), "recovery_final_author_sha_mismatch")
    actualJudgedDraftSha = sha256(judgedDraftRaw)
    _ <- Either.cond(actualJudgedDraftSha == audit.finalJudgedHoldoutSha256, (), "recovery_final_judged_sha_mismatch")
    actualHoldoutSha = sha256(protectedCorpusRaw)
    _ <- Either.cond(actualHoldoutSha == audit.finalJudgedHoldoutSha256, (), "recovery_final_holdout_sha_mismatch")
    _ <- Either.cond(judgedDraftRaw == protectedCorpusRaw, (), "recovery_final_judged_holdout_byte_mismatch")
    _ <- validateDigestShape(audit.finalProtectedCorpusFingerprint, "corpus_fingerprint")
    _ <- validateDigestShape(audit.finalProtectedPolicyFingerprint, "policy_fingerprint")
    _ <- validateDigestShape(audit.canonicalCatalogFingerprint, "catalog_fingerprint")
    authorDraft <- BeautyQProtectedAuthorDraft.decodeString(authorDraftRaw)
    judgedDraft <- parse(judgedDraftRaw).left.map(_ => "recovery_final_judged_invalid_json")
      .flatMap(BeautyQEvaluationCorpus.decodeFromJson)
      .left.map(_ => "recovery_final_judged_decode_failed")
    protectedCorpus <- parse(protectedCorpusRaw).left.map(_ => "recovery_final_holdout_invalid_json")
      .flatMap(BeautyQEvaluationCorpus.decodeFromJson)
      .left.map(_ => "recovery_final_holdout_decode_failed")
    _ <- eitherCond(protectedCorpus.corpusFingerprint == audit.finalProtectedCorpusFingerprint,
      "recovery_final_corpus_fingerprint_mismatch")
    policyRaw <- parse(protectedPolicyRaw).left.map(_ => "recovery_final_policy_invalid_json")
      .flatMap(BeautyQProtectedAcceptancePolicy.fromJson)
      .left.map(_ => "recovery_final_policy_decode_failed")
    _ <- eitherCond(policyRaw.fingerprint == audit.finalProtectedPolicyFingerprint,
      "recovery_final_policy_fingerprint_mismatch")
    _ <- validateSelectedToFinalAuthor(authorDraft, authorReserve, audit)
    _ <- validateSelectedToFinalJudged(judgedDraft, protectedCorpus, authorReserve, audit, judgedReserveCorpus)
    _ <- validateFinalNoteProvenance(authorDraft, judgedDraft, audit)
  } yield ()

  private def eitherCond(test: Boolean, error: String): Either[String, Unit] =
    Either.cond(test, (), error)

  private def validateSelectedToFinalAuthor(
    finalAuthor: BeautyQProtectedAuthorDraft,
    authorReserve: AuthorReserve,
    audit: SelectionAudit,
  ): Either[String, Unit] = {
    val allReserveIds = authorReserve.cases.map(_.id).toSet
    val selectedIds = audit.selectedCaseIds
    val reserveFinalCases = finalAuthor.cases.filter(c => allReserveIds.contains(c.id))
    if (reserveFinalCases.map(_.id) != selectedIds) Left("recovery_selected_final_author_inventory_mismatch")
    else {
      val authorById = authorReserve.cases.map(c => c.id -> c).toMap
      val mismatchCategory = reserveFinalCases.zip(selectedIds).flatMap { case (fc, sid) =>
        val ac = authorById(sid)
        if (fc.id != ac.id) Some("id")
        else if (fc.query != ac.query) Some("query")
        else if (fc.language != ac.language) Some("language")
        else if (fc.primarySlice != ac.primarySlice) Some("primary_slice")
        else if (fc.additionalSlices != ac.additionalSlices) Some("additional_slices")
        else if (fc.userIntent != ac.userIntent) Some("user_intent")
        else None
      }.headOption
      mismatchCategory match {
        case None => Right(())
        case Some(category) => Left(s"recovery_selected_final_author_mismatch_${category}")
      }
    }
  }

  private def validateSelectedToFinalJudged(
    judgedDraft: BeautyQEvaluationCorpus,
    protectedCorpus: BeautyQEvaluationCorpus,
    authorReserve: AuthorReserve,
    audit: SelectionAudit,
    judgedReserveCorpus: BeautyQEvaluationCorpus,
  ): Either[String, Unit] = {
    val allReserveIds = authorReserve.cases.map(_.id).toSet
    val selectedIds = audit.selectedCaseIds
    val reserveCases = judgedDraft.cases.filter(c => allReserveIds.contains(c.caseId.value))
    if (reserveCases.map(_.caseId.value) != selectedIds) Left("recovery_selected_final_judged_inventory_mismatch")
    else {
    val judgedReserveById = judgedReserveCorpus.cases.map(c => c.caseId.value -> c).toMap
    val judgedById = judgedDraft.cases.map(c => c.caseId.value -> c).toMap
    val reserveJudgeMismatch = selectedIds.flatMap { sid =>
      val rjc = judgedReserveById(sid)
      val fjc = judgedById(sid)
      if (rjc.caseId != fjc.caseId) Some("id")
      else if (rjc.partition != fjc.partition) Some("partition")
      else if (rjc.judgmentMode != fjc.judgmentMode) Some("judgment_mode")
      else if (rjc.query != fjc.query) Some("query")
      else if (rjc.language != fjc.language) Some("language")
      else if (rjc.slices != fjc.slices) Some("slices")
      else if (rjc.userIntent != fjc.userIntent) Some("user_intent")
      else if (rjc.variantJudgments != fjc.variantJudgments) Some("variant_judgments")
      else if (rjc.providerJudgments != fjc.providerJudgments) Some("provider_judgments")
      else if (rjc.serviceIntentJudgments != fjc.serviceIntentJudgments) Some("service_intent_judgments")
      else None
    }.headOption
    reserveJudgeMismatch match {
      case Some(category) => Left(s"recovery_selected_judge_corpus_mismatch_${category}")
      case None =>
        val protectedById = protectedCorpus.cases.map(c => c.caseId.value -> c).toMap
        val mismatchCategory = selectedIds.flatMap { sid =>
          val jc = judgedById(sid)
          val pc = protectedById(sid)
          if (jc.caseId != pc.caseId) Some("id")
          else if (jc.partition != pc.partition) Some("partition")
          else if (jc.judgmentMode != pc.judgmentMode) Some("judgment_mode")
          else if (jc.query != pc.query) Some("query")
          else if (jc.language != pc.language) Some("language")
          else if (jc.slices != pc.slices) Some("slices")
          else if (jc.userIntent != pc.userIntent) Some("user_intent")
          else if (jc.notes != pc.notes) Some("notes")
          else if (jc.variantJudgments != pc.variantJudgments) Some("variant_judgments")
          else if (jc.providerJudgments != pc.providerJudgments) Some("provider_judgments")
          else if (jc.serviceIntentJudgments != pc.serviceIntentJudgments) Some("service_intent_judgments")
          else None
        }.headOption
        mismatchCategory match {
          case None => Right(())
          case Some(category) => Left(s"recovery_selected_final_judged_mismatch_${category}")
        }
    }
    }
  }

  private def validateFinalNoteProvenance(
    authorDraft: BeautyQProtectedAuthorDraft,
    judgedDraft: BeautyQEvaluationCorpus,
    audit: SelectionAudit,
  ): Either[String, Unit] = {
    val authorById = authorDraft.cases.map(c => c.id -> c).toMap
    val judgedById = judgedDraft.cases.map(c => c.caseId.value -> c).toMap
    val mismatched = audit.selectedCaseIds.exists { sid =>
      authorById(sid).notes != judgedById(sid).notes
    }
    Either.cond(!mismatched, (), "recovery_selected_final_note_provenance_mismatch")
  }

  private def validateDigestShape(value: String, label: String): Either[String, Unit] =
    Either.cond(DigestPattern.matches(value), (), s"recovery_${label}_shape_invalid")

  private def decodeAuthorReserve(raw: String): Either[String, AuthorReserve] =
    parse(raw).left.map(_ => "recovery_author_reserve_invalid_json").flatMap { json =>
      for {
        root <- json.asObject.toRight("recovery_author_reserve_expected_object")
        _ <- exactFields(root, AuthorReserveRootFields, "recovery_author_reserve")
        schema <- string(root, "schemaVersion")
        _ <- Either.cond(schema == CurrentAuthorSchema, (), "recovery_author_reserve_schema_mismatch")
        revision <- string(root, "sourceRevision")
        _ <- Either.cond(RevisionPattern.matches(revision), (), "recovery_author_reserve_revision_invalid")
        authorPassId <- string(root, "authorPassId")
        _ <- nonBlank(authorPassId, "recovery_author_reserve_pass_invalid")
        casesJson <- root("cases").flatMap(_.asArray).toRight("recovery_author_reserve_cases_invalid")
        _ <- Either.cond(casesJson.nonEmpty, (), "recovery_author_reserve_cases_empty")
        cases <- casesJson.zipWithIndex.foldLeft[Either[String, Vector[AuthorReserveCase]]](Right(Vector.empty)) {
          case (acc, (value, index)) => acc.flatMap(done => decodeAuthorCase(value, index).map(done :+ _))
        }
        _ <- Either.cond(cases.map(_.id).distinct.size == cases.size, (), "recovery_author_reserve_duplicate_case_id")
      } yield new AuthorReserve(schema, revision, authorPassId, cases)
    }

  private def decodeAuthorCase(json: Json, index: Int): Either[String, AuthorReserveCase] = for {
    obj <- json.asObject.toRight(s"recovery_author_reserve_case_${index}_invalid")
    _ <- exactFields(obj, AuthorReserveCaseFields, s"recovery_author_reserve_case_$index")
    id <- string(obj, "id")
    query <- string(obj, "query")
    language <- string(obj, "language")
    primarySlice <- string(obj, "primarySlice")
    _ <- nonBlank(id, s"recovery_author_reserve_case_${index}_id_invalid")
    _ <- nonBlank(query, s"recovery_author_reserve_case_${index}_query_invalid")
    _ <- nonBlank(primarySlice, s"recovery_author_reserve_case_${index}_slice_invalid")
    additionalSlices <- strings(obj, "additionalSlices")
    _ <- Either.cond(!additionalSlices.contains(primarySlice), (), s"recovery_author_reserve_case_${index}_duplicate_slice")
    userIntent <- string(obj, "userIntent")
    notes <- strings(obj, "notes")
    coverageBucket <- string(obj, "coverageBucket")
    _ <- nonBlank(coverageBucket, s"recovery_author_reserve_case_${index}_coverage_bucket_invalid")
  } yield AuthorReserveCase(id, query, language, primarySlice, additionalSlices, userIntent, notes, coverageBucket)

  private def decodeJudgedReserve(raw: String): Either[String, JudgedRecoveryReserve] =
    parse(raw).left.map(_ => "recovery_judged_reserve_invalid_json").flatMap { json =>
      for {
        root <- json.asObject.toRight("recovery_judged_reserve_expected_object")
        _ <- exactFields(root, JudgedReserveRootFields, "recovery_judged_reserve")
        schema <- string(root, "schemaVersion")
        _ <- Either.cond(schema == CurrentJudgedSchema, (), "recovery_judged_reserve_schema_mismatch")
        revision <- string(root, "sourceRevision")
        _ <- Either.cond(RevisionPattern.matches(revision), (), "recovery_judged_reserve_revision_invalid")
        judgePassId <- string(root, "judgePassId")
        _ <- nonBlank(judgePassId, "recovery_judged_reserve_pass_invalid")
        corpusJson <- root("corpus").toRight("recovery_judged_reserve_corpus_missing")
        corpus <- BeautyQEvaluationCorpus.decodeFromJson(corpusJson).left.map(_ => "recovery_judged_reserve_decode_failed")
      } yield new JudgedRecoveryReserve(schema, revision, judgePassId, corpus)
    }

  private def decodeSelectionAudit(raw: String): Either[String, SelectionAudit] =
    parse(raw).left.map(_ => "recovery_selection_audit_invalid_json").flatMap { json =>
      for {
        root <- json.asObject.toRight("recovery_selection_audit_expected_object")
        _ <- exactFields(root, SelectionAuditRootFields, "recovery_selection_audit")
        schema <- string(root, "schemaVersion")
        _ <- Either.cond(schema == CurrentAuditSchema, (), "recovery_selection_audit_schema_mismatch")
        revision <- string(root, "sourceRevision")
        _ <- Either.cond(RevisionPattern.matches(revision), (), "recovery_selection_audit_revision_invalid")
        authorReservePassId <- string(root, "authorReservePassId")
        judgeReservePassId <- string(root, "judgeReservePassId")
        auditPassId <- string(root, "auditPassId")
        _ <- nonBlank(authorReservePassId, "recovery_selection_audit_author_pass_invalid")
        _ <- nonBlank(judgeReservePassId, "recovery_selection_audit_judge_pass_invalid")
        _ <- nonBlank(auditPassId, "recovery_selection_audit_audit_pass_invalid")
        authorSha <- string(root, "authorReserveSha256")
        judgedSha <- string(root, "judgedReserveSha256")
        _ <- Either.cond(DigestPattern.matches(authorSha), (), "recovery_selection_audit_author_sha_invalid")
        _ <- Either.cond(DigestPattern.matches(judgedSha), (), "recovery_selection_audit_judged_sha_invalid")
        count <- int(root, "candidateCount")
        _ <- Either.cond(count > 0, (), "recovery_selection_audit_candidate_count_invalid")
        bucketsRaw <- root("orderedBuckets").flatMap(_.asArray).toRight("recovery_selection_audit_buckets_invalid")
        buckets <- parseStringArray(bucketsRaw, "recovery_selection_audit_bucket_element_invalid")
        _ <- Either.cond(buckets.nonEmpty, (), "recovery_selection_audit_buckets_empty")
        selectedRaw <- root("selectedCaseIds").flatMap(_.asArray).toRight("recovery_selection_audit_selected_invalid")
        selected <- parseStringArray(selectedRaw, "recovery_selection_audit_selected_element_invalid")
        _ <- Either.cond(selected.nonEmpty, (), "recovery_selection_audit_selected_empty")
        consumedRaw <- root("consumedCaseIds").flatMap(_.asArray).toRight("recovery_selection_audit_consumed_invalid")
        consumed <- parseStringArray(consumedRaw, "recovery_selection_audit_consumed_element_invalid")
        _ <- Either.cond(consumed.nonEmpty, (), "recovery_selection_audit_consumed_empty")
        finalAuthorSha <- string(root, "finalAuthorDraftSha256")
        _ <- Either.cond(DigestPattern.matches(finalAuthorSha), (), "recovery_selection_audit_final_author_sha_invalid")
        finalJudgedSha <- string(root, "finalJudgedHoldoutSha256")
        _ <- Either.cond(DigestPattern.matches(finalJudgedSha), (), "recovery_selection_audit_final_judged_sha_invalid")
        finalCorpusFp <- string(root, "finalProtectedCorpusFingerprint")
        _ <- Either.cond(DigestPattern.matches(finalCorpusFp), (), "recovery_selection_audit_final_corpus_fp_invalid")
        finalPolicyFp <- string(root, "finalProtectedPolicyFingerprint")
        _ <- Either.cond(DigestPattern.matches(finalPolicyFp), (), "recovery_selection_audit_final_policy_fp_invalid")
        catalogFp <- string(root, "canonicalCatalogFingerprint")
        _ <- Either.cond(DigestPattern.matches(catalogFp), (), "recovery_selection_audit_catalog_fp_invalid")
      } yield new SelectionAudit(
        schema, revision, authorReservePassId, judgeReservePassId, auditPassId,
        authorSha, judgedSha, count, buckets, consumed, selected,
        finalAuthorSha, finalJudgedSha, finalCorpusFp, finalPolicyFp, catalogFp,
      )
    }

  private def parseStringArray(values: Vector[Json], error: String): Either[String, Vector[String]] =
    values.foldLeft[Either[String, Vector[String]]](Right(Vector.empty)) {
      case (acc, value) => acc.flatMap(xs => value.asString.toRight(error).map(xs :+ _))
    }

  private def exactFields(obj: JsonObject, expected: Set[String], context: String): Either[String, Unit] =
    Either.cond(obj.keys.toSet == expected, (), s"${context}_fields_invalid")

  private def string(obj: JsonObject, name: String): Either[String, String] =
    obj(name).flatMap(_.asString).toRight(s"selection_audit_${name}_invalid")

  private def strings(obj: JsonObject, name: String): Either[String, Vector[String]] =
    obj(name).flatMap(_.asArray).toRight(s"${name}_invalid").flatMap { values =>
      values.foldLeft[Either[String, Vector[String]]](Right(Vector.empty)) {
        case (acc, value) => acc.flatMap(done => value.asString.toRight(s"${name}_element_invalid").map(done :+ _))
      }
    }

  private def int(obj: JsonObject, name: String): Either[String, Int] =
    obj(name).flatMap(_.asNumber).flatMap(_.toInt).toRight(s"selection_audit_${name}_invalid")

  private def nonBlank(value: String, error: String): Either[String, Unit] =
    Either.cond(value.nonEmpty && value.trim == value, (), error)

  private def sha256(raw: String): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.digest(raw.getBytes(StandardCharsets.UTF_8)).map(b => f"$b%02x").mkString
  }
}

final class BeautyQProtectedRecoveryReserve private[eval] (
  val authorReserve: BeautyQProtectedRecoveryReserve.AuthorReserve,
  val judgedReserve: BeautyQProtectedRecoveryReserve.JudgedRecoveryReserve,
  val selectionAudit: BeautyQProtectedRecoveryReserve.SelectionAudit,
)
