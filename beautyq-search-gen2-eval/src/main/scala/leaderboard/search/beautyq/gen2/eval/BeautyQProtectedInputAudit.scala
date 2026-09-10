package leaderboard.search.beautyq.gen2.eval

import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import leaderboard.search.gen2.eval.{EvaluationPartition, EvaluationSliceId}

sealed trait BeautyQProtectedInputAuditError { def stableCode: String }

object BeautyQProtectedInputAuditError {
  case object InvalidDocument extends BeautyQProtectedInputAuditError { val stableCode = "invalid_document" }
  case object InvalidAuthoringMethod extends BeautyQProtectedInputAuditError { val stableCode = "invalid_authoring_method" }
  case object InvalidPassIdentity extends BeautyQProtectedInputAuditError { val stableCode = "invalid_pass_identity" }
  case object DuplicatePassIdentity extends BeautyQProtectedInputAuditError { val stableCode = "duplicate_pass_identity" }
  case object InvalidFileHash extends BeautyQProtectedInputAuditError { val stableCode = "invalid_file_hash" }
  case object InvalidDraftHash extends BeautyQProtectedInputAuditError { val stableCode = "invalid_draft_hash" }
  case object InvalidFingerprint extends BeautyQProtectedInputAuditError { val stableCode = "invalid_fingerprint" }
  case object InvalidCatalogFingerprint extends BeautyQProtectedInputAuditError { val stableCode = "invalid_catalog_fingerprint" }
  case object InvalidCaseCount extends BeautyQProtectedInputAuditError { val stableCode = "invalid_case_count" }
  case object ExactIntentInventoryMismatch extends BeautyQProtectedInputAuditError { val stableCode = "exact_intent_inventory_mismatch" }
  case object InvalidSliceInventory extends BeautyQProtectedInputAuditError { val stableCode = "invalid_slice_inventory" }
  case object VisibleLeakageDetected extends BeautyQProtectedInputAuditError { val stableCode = "visible_leakage_detected" }
  case object ProtectedLeakageDetected extends BeautyQProtectedInputAuditError { val stableCode = "protected_leakage_detected" }
  case object CatalogValidationFailed extends BeautyQProtectedInputAuditError { val stableCode = "catalog_validation_failed" }
  case object ExactIntentValidationFailed extends BeautyQProtectedInputAuditError { val stableCode = "exact_intent_validation_failed" }
  case object ValidationFailed extends BeautyQProtectedInputAuditError { val stableCode = "validation_failed" }
  case object InputsNotFrozen extends BeautyQProtectedInputAuditError { val stableCode = "inputs_not_frozen" }
}

final class BeautyQProtectedInputAudit private (
  val schemaVersion: String,
  val authoringMethod: String,
  val authorPassId: String,
  val judgePassId: String,
  val auditPassId: String,
  val protectedCorpusSha256: String,
  val protectedPolicySha256: String,
  val authorDraftSha256: String,
  val judgedDraftSha256: String,
  val protectedCorpusFingerprint: String,
  val protectedPolicyFingerprint: String,
  val canonicalSourceFingerprint: String,
  val protectedCaseCount: Int,
  val orderedRequiredSliceCounts: Vector[(EvaluationSliceId, Int)],
  val exactVisibleQueryDuplicateCount: Int,
  val normalizedVisibleQueryDuplicateCount: Int,
  val visibleCaseIdOverlapCount: Int,
  val internalExactQueryDuplicateCount: Int,
  val internalNormalizedQueryDuplicateCount: Int,
  val exactIntentCaseCount: Int,
  val exactIntentWithoutAcceptableVariantCount: Int,
  val invalidVariantJudgmentIdentityCount: Int,
  val invalidProviderJudgmentIdentityCount: Int,
  val invalidServiceIntentJudgmentIdentityCount: Int,
  val strictCorpusValidationPassed: Boolean,
  val strictPolicyValidationPassed: Boolean,
  val partitionValidationPassed: Boolean,
  val sliceValidationPassed: Boolean,
  val frozen: Boolean,
) {
  def toJson: Json = BeautyQProtectedInputAudit.encode(this)

  override def equals(other: Any): Boolean = other match {
    case value: BeautyQProtectedInputAudit =>
      toJson == value.toJson
    case _ => false
  }

  override def hashCode(): Int = toJson.hashCode
}

object BeautyQProtectedInputAudit {
  val CurrentSchemaVersion: String = "beautyq-protected-input-audit-v2"
  val CurrentAuthoringMethod: String = "model-assisted-separated-passes-v1"

  private val DigestPattern = "^[0-9a-f]{64}$".r
  private val RootFields = Set(
    "schemaVersion", "authoringMethod", "authorPassId", "judgePassId", "auditPassId",
    "protectedCorpusSha256", "protectedPolicySha256", "authorDraftSha256", "judgedDraftSha256",
    "protectedCorpusFingerprint", "protectedPolicyFingerprint", "canonicalSourceFingerprint",
    "protectedCaseCount", "orderedRequiredSliceCounts", "exactVisibleQueryDuplicateCount",
    "normalizedVisibleQueryDuplicateCount", "visibleCaseIdOverlapCount", "internalExactQueryDuplicateCount",
    "internalNormalizedQueryDuplicateCount", "exactIntentCaseCount", "exactIntentWithoutAcceptableVariantCount",
    "invalidVariantJudgmentIdentityCount", "invalidProviderJudgmentIdentityCount",
    "invalidServiceIntentJudgmentIdentityCount", "strictCorpusValidationPassed", "strictPolicyValidationPassed",
    "partitionValidationPassed", "sliceValidationPassed", "frozen",
  )
  private val SliceFields = Set("sliceId", "caseCount")

  def create(
    authoringMethod: String,
    authorPassId: String,
    judgePassId: String,
    auditPassId: String,
    protectedCorpusSha256: String,
    protectedPolicySha256: String,
    authorDraftSha256: String,
    judgedDraftSha256: String,
    protectedCorpus: BeautyQProtectedEvaluationCorpus,
    protectedPolicy: BeautyQProtectedAcceptancePolicy,
    canonicalSourceFingerprint: String,
    exactVisibleQueryDuplicateCount: Int,
    normalizedVisibleQueryDuplicateCount: Int,
    visibleCaseIdOverlapCount: Int,
    internalExactQueryDuplicateCount: Int,
    internalNormalizedQueryDuplicateCount: Int,
    exactIntentCaseCount: Int,
    exactIntentWithoutAcceptableVariantCount: Int,
    invalidVariantJudgmentIdentityCount: Int,
    invalidProviderJudgmentIdentityCount: Int,
    invalidServiceIntentJudgmentIdentityCount: Int,
  ): Either[BeautyQProtectedInputAuditError, BeautyQProtectedInputAudit] = {
    val corpusMatchesPolicy =
      protectedCorpus.corpusFingerprint == protectedPolicy.expectedCorpusFingerprint &&
        protectedCorpus.caseCount == protectedPolicy.expectedCaseCount &&
        protectedCorpus.orderedRequiredSliceCounts.map(_._1) == protectedPolicy.requiredSliceMinimums.map(_.sliceId) &&
        protectedCorpus.orderedRequiredSliceCounts.zip(protectedPolicy.requiredSliceMinimums).forall {
          case ((sliceId, count), minimum) => sliceId == minimum.sliceId && count >= minimum.minimumCaseCount
        }
    createValidated(
      CurrentSchemaVersion, authoringMethod, authorPassId, judgePassId, auditPassId,
      protectedCorpusSha256, protectedPolicySha256, authorDraftSha256, judgedDraftSha256,
      protectedCorpus.corpusFingerprint, protectedPolicy.fingerprint, canonicalSourceFingerprint,
      protectedCorpus.caseCount, protectedCorpus.orderedRequiredSliceCounts,
      exactVisibleQueryDuplicateCount, normalizedVisibleQueryDuplicateCount, visibleCaseIdOverlapCount,
      internalExactQueryDuplicateCount, internalNormalizedQueryDuplicateCount,
      exactIntentCaseCount, exactIntentWithoutAcceptableVariantCount,
      invalidVariantJudgmentIdentityCount, invalidProviderJudgmentIdentityCount,
      invalidServiceIntentJudgmentIdentityCount,
      strictCorpusValidationPassed = true, strictPolicyValidationPassed = true,
      partitionValidationPassed = protectedCorpus.corpus.cases.forall(_.partition == EvaluationPartition.ProtectedHoldout),
      sliceValidationPassed = corpusMatchesPolicy, frozen = true, corpusMatchesPolicy,
    )
  }

  def decode(json: Json): Either[BeautyQProtectedInputAuditError, BeautyQProtectedInputAudit] = for {
    root <- json.asObject.toRight(BeautyQProtectedInputAuditError.InvalidDocument)
    _ <- Either.cond(root.keys.toSet == RootFields, (), BeautyQProtectedInputAuditError.InvalidDocument)
    schemaVersion <- string(root, "schemaVersion")
    authoringMethod <- string(root, "authoringMethod")
    authorPassId <- string(root, "authorPassId")
    judgePassId <- string(root, "judgePassId")
    auditPassId <- string(root, "auditPassId")
    protectedCorpusSha256 <- string(root, "protectedCorpusSha256")
    protectedPolicySha256 <- string(root, "protectedPolicySha256")
    authorDraftSha256 <- string(root, "authorDraftSha256")
    judgedDraftSha256 <- string(root, "judgedDraftSha256")
    protectedCorpusFingerprint <- string(root, "protectedCorpusFingerprint")
    protectedPolicyFingerprint <- string(root, "protectedPolicyFingerprint")
    canonicalSourceFingerprint <- string(root, "canonicalSourceFingerprint")
    protectedCaseCount <- int(root, "protectedCaseCount")
    orderedRequiredSliceCounts <- sliceCounts(root)
    exactVisibleQueryDuplicateCount <- int(root, "exactVisibleQueryDuplicateCount")
    normalizedVisibleQueryDuplicateCount <- int(root, "normalizedVisibleQueryDuplicateCount")
    visibleCaseIdOverlapCount <- int(root, "visibleCaseIdOverlapCount")
    internalExactQueryDuplicateCount <- int(root, "internalExactQueryDuplicateCount")
    internalNormalizedQueryDuplicateCount <- int(root, "internalNormalizedQueryDuplicateCount")
    exactIntentCaseCount <- int(root, "exactIntentCaseCount")
    exactIntentWithoutAcceptableVariantCount <- int(root, "exactIntentWithoutAcceptableVariantCount")
    invalidVariantJudgmentIdentityCount <- int(root, "invalidVariantJudgmentIdentityCount")
    invalidProviderJudgmentIdentityCount <- int(root, "invalidProviderJudgmentIdentityCount")
    invalidServiceIntentJudgmentIdentityCount <- int(root, "invalidServiceIntentJudgmentIdentityCount")
    strictCorpusValidationPassed <- boolean(root, "strictCorpusValidationPassed")
    strictPolicyValidationPassed <- boolean(root, "strictPolicyValidationPassed")
    partitionValidationPassed <- boolean(root, "partitionValidationPassed")
    sliceValidationPassed <- boolean(root, "sliceValidationPassed")
    frozen <- boolean(root, "frozen")
    value <- createValidated(
      schemaVersion, authoringMethod, authorPassId, judgePassId, auditPassId,
      protectedCorpusSha256, protectedPolicySha256, authorDraftSha256, judgedDraftSha256,
      protectedCorpusFingerprint, protectedPolicyFingerprint, canonicalSourceFingerprint,
      protectedCaseCount, orderedRequiredSliceCounts,
      exactVisibleQueryDuplicateCount, normalizedVisibleQueryDuplicateCount, visibleCaseIdOverlapCount,
      internalExactQueryDuplicateCount, internalNormalizedQueryDuplicateCount,
      exactIntentCaseCount, exactIntentWithoutAcceptableVariantCount,
      invalidVariantJudgmentIdentityCount, invalidProviderJudgmentIdentityCount,
      invalidServiceIntentJudgmentIdentityCount, strictCorpusValidationPassed, strictPolicyValidationPassed,
      partitionValidationPassed, sliceValidationPassed, frozen, corpusMatchesPolicy = true,
    )
  } yield value

  def decodeString(raw: String): Either[BeautyQProtectedInputAuditError, BeautyQProtectedInputAudit] =
    parse(raw).left.map(_ => BeautyQProtectedInputAuditError.InvalidDocument).flatMap(decode)

  private def createValidated(
    schemaVersion: String,
    authoringMethod: String,
    authorPassId: String,
    judgePassId: String,
    auditPassId: String,
    protectedCorpusSha256: String,
    protectedPolicySha256: String,
    authorDraftSha256: String,
    judgedDraftSha256: String,
    protectedCorpusFingerprint: String,
    protectedPolicyFingerprint: String,
    canonicalSourceFingerprint: String,
    protectedCaseCount: Int,
    orderedRequiredSliceCounts: Vector[(EvaluationSliceId, Int)],
    exactVisibleQueryDuplicateCount: Int,
    normalizedVisibleQueryDuplicateCount: Int,
    visibleCaseIdOverlapCount: Int,
    internalExactQueryDuplicateCount: Int,
    internalNormalizedQueryDuplicateCount: Int,
    exactIntentCaseCount: Int,
    exactIntentWithoutAcceptableVariantCount: Int,
    invalidVariantJudgmentIdentityCount: Int,
    invalidProviderJudgmentIdentityCount: Int,
    invalidServiceIntentJudgmentIdentityCount: Int,
    strictCorpusValidationPassed: Boolean,
    strictPolicyValidationPassed: Boolean,
    partitionValidationPassed: Boolean,
    sliceValidationPassed: Boolean,
    frozen: Boolean,
    corpusMatchesPolicy: Boolean,
  ): Either[BeautyQProtectedInputAuditError, BeautyQProtectedInputAudit] = {
    val passIds = Vector(authorPassId, judgePassId, auditPassId)
    if (schemaVersion != CurrentSchemaVersion) Left(BeautyQProtectedInputAuditError.InvalidDocument)
    else if (authoringMethod != CurrentAuthoringMethod) Left(BeautyQProtectedInputAuditError.InvalidAuthoringMethod)
    else if (passIds.exists(value => value.isEmpty || value.trim != value)) Left(BeautyQProtectedInputAuditError.InvalidPassIdentity)
    else if (passIds.distinct.size != passIds.size) Left(BeautyQProtectedInputAuditError.DuplicatePassIdentity)
    else if (!DigestPattern.matches(protectedCorpusSha256) || !DigestPattern.matches(protectedPolicySha256)) Left(BeautyQProtectedInputAuditError.InvalidFileHash)
    else if (!DigestPattern.matches(authorDraftSha256) || !DigestPattern.matches(judgedDraftSha256)) Left(BeautyQProtectedInputAuditError.InvalidDraftHash)
    else if (!DigestPattern.matches(protectedCorpusFingerprint) || !DigestPattern.matches(protectedPolicyFingerprint)) Left(BeautyQProtectedInputAuditError.InvalidFingerprint)
    else if (!DigestPattern.matches(canonicalSourceFingerprint)) Left(BeautyQProtectedInputAuditError.InvalidCatalogFingerprint)
    else if (protectedCaseCount <= 0 || exactIntentCaseCount <= 0 || orderedRequiredSliceCounts.exists(_._2 <= 0)) Left(BeautyQProtectedInputAuditError.InvalidCaseCount)
    else if (orderedRequiredSliceCounts.isEmpty || orderedRequiredSliceCounts.map(_._1).distinct.size != orderedRequiredSliceCounts.size) Left(BeautyQProtectedInputAuditError.InvalidSliceInventory)
    else {
      val exactIntentCounts = orderedRequiredSliceCounts.collect { case (sliceId, count) if sliceId.value == "exact-intent" => count }
      if (exactIntentCounts match {
        case Vector(count) => count != exactIntentCaseCount || exactIntentCaseCount > protectedCaseCount
        case _ => true
      }) Left(BeautyQProtectedInputAuditError.ExactIntentInventoryMismatch)
      else validateRemaining(
        schemaVersion, authoringMethod, authorPassId, judgePassId, auditPassId,
        protectedCorpusSha256, protectedPolicySha256, authorDraftSha256, judgedDraftSha256,
        protectedCorpusFingerprint, protectedPolicyFingerprint, canonicalSourceFingerprint,
        protectedCaseCount, orderedRequiredSliceCounts,
        exactVisibleQueryDuplicateCount, normalizedVisibleQueryDuplicateCount, visibleCaseIdOverlapCount,
        internalExactQueryDuplicateCount, internalNormalizedQueryDuplicateCount,
        exactIntentCaseCount, exactIntentWithoutAcceptableVariantCount,
        invalidVariantJudgmentIdentityCount, invalidProviderJudgmentIdentityCount,
        invalidServiceIntentJudgmentIdentityCount, strictCorpusValidationPassed, strictPolicyValidationPassed,
        partitionValidationPassed, sliceValidationPassed, frozen, corpusMatchesPolicy,
      )
    }
  }

  private def validateRemaining(
    schemaVersion: String,
    authoringMethod: String,
    authorPassId: String,
    judgePassId: String,
    auditPassId: String,
    protectedCorpusSha256: String,
    protectedPolicySha256: String,
    authorDraftSha256: String,
    judgedDraftSha256: String,
    protectedCorpusFingerprint: String,
    protectedPolicyFingerprint: String,
    canonicalSourceFingerprint: String,
    protectedCaseCount: Int,
    orderedRequiredSliceCounts: Vector[(EvaluationSliceId, Int)],
    exactVisibleQueryDuplicateCount: Int,
    normalizedVisibleQueryDuplicateCount: Int,
    visibleCaseIdOverlapCount: Int,
    internalExactQueryDuplicateCount: Int,
    internalNormalizedQueryDuplicateCount: Int,
    exactIntentCaseCount: Int,
    exactIntentWithoutAcceptableVariantCount: Int,
    invalidVariantJudgmentIdentityCount: Int,
    invalidProviderJudgmentIdentityCount: Int,
    invalidServiceIntentJudgmentIdentityCount: Int,
    strictCorpusValidationPassed: Boolean,
    strictPolicyValidationPassed: Boolean,
    partitionValidationPassed: Boolean,
    sliceValidationPassed: Boolean,
    frozen: Boolean,
    corpusMatchesPolicy: Boolean,
  ): Either[BeautyQProtectedInputAuditError, BeautyQProtectedInputAudit] = {
    if (exactVisibleQueryDuplicateCount != 0 || normalizedVisibleQueryDuplicateCount != 0 || visibleCaseIdOverlapCount != 0) Left(BeautyQProtectedInputAuditError.VisibleLeakageDetected)
    else if (internalExactQueryDuplicateCount != 0 || internalNormalizedQueryDuplicateCount != 0) Left(BeautyQProtectedInputAuditError.ProtectedLeakageDetected)
    else if (invalidVariantJudgmentIdentityCount != 0 || invalidProviderJudgmentIdentityCount != 0 || invalidServiceIntentJudgmentIdentityCount != 0) Left(BeautyQProtectedInputAuditError.CatalogValidationFailed)
    else if (exactIntentWithoutAcceptableVariantCount != 0) Left(BeautyQProtectedInputAuditError.ExactIntentValidationFailed)
    else if (!strictCorpusValidationPassed || !strictPolicyValidationPassed || !partitionValidationPassed || !sliceValidationPassed || !corpusMatchesPolicy) Left(BeautyQProtectedInputAuditError.ValidationFailed)
    else if (!frozen) Left(BeautyQProtectedInputAuditError.InputsNotFrozen)
    else Right(new BeautyQProtectedInputAudit(
      schemaVersion, authoringMethod, authorPassId, judgePassId, auditPassId,
      protectedCorpusSha256, protectedPolicySha256, authorDraftSha256, judgedDraftSha256,
      protectedCorpusFingerprint, protectedPolicyFingerprint, canonicalSourceFingerprint,
      protectedCaseCount, orderedRequiredSliceCounts,
      exactVisibleQueryDuplicateCount, normalizedVisibleQueryDuplicateCount, visibleCaseIdOverlapCount,
      internalExactQueryDuplicateCount, internalNormalizedQueryDuplicateCount,
      exactIntentCaseCount, exactIntentWithoutAcceptableVariantCount,
      invalidVariantJudgmentIdentityCount, invalidProviderJudgmentIdentityCount,
      invalidServiceIntentJudgmentIdentityCount, strictCorpusValidationPassed, strictPolicyValidationPassed,
      partitionValidationPassed, sliceValidationPassed, frozen,
    ))
  }

  private def encode(value: BeautyQProtectedInputAudit): Json = Json.obj(
    "schemaVersion" -> Json.fromString(value.schemaVersion),
    "authoringMethod" -> Json.fromString(value.authoringMethod),
    "authorPassId" -> Json.fromString(value.authorPassId),
    "judgePassId" -> Json.fromString(value.judgePassId),
    "auditPassId" -> Json.fromString(value.auditPassId),
    "protectedCorpusSha256" -> Json.fromString(value.protectedCorpusSha256),
    "protectedPolicySha256" -> Json.fromString(value.protectedPolicySha256),
    "authorDraftSha256" -> Json.fromString(value.authorDraftSha256),
    "judgedDraftSha256" -> Json.fromString(value.judgedDraftSha256),
    "protectedCorpusFingerprint" -> Json.fromString(value.protectedCorpusFingerprint),
    "protectedPolicyFingerprint" -> Json.fromString(value.protectedPolicyFingerprint),
    "canonicalSourceFingerprint" -> Json.fromString(value.canonicalSourceFingerprint),
    "protectedCaseCount" -> Json.fromInt(value.protectedCaseCount),
    "orderedRequiredSliceCounts" -> Json.fromValues(value.orderedRequiredSliceCounts.map { case (sliceId, count) =>
      Json.obj("sliceId" -> Json.fromString(sliceId.value), "caseCount" -> Json.fromInt(count))
    }),
    "exactVisibleQueryDuplicateCount" -> Json.fromInt(value.exactVisibleQueryDuplicateCount),
    "normalizedVisibleQueryDuplicateCount" -> Json.fromInt(value.normalizedVisibleQueryDuplicateCount),
    "visibleCaseIdOverlapCount" -> Json.fromInt(value.visibleCaseIdOverlapCount),
    "internalExactQueryDuplicateCount" -> Json.fromInt(value.internalExactQueryDuplicateCount),
    "internalNormalizedQueryDuplicateCount" -> Json.fromInt(value.internalNormalizedQueryDuplicateCount),
    "exactIntentCaseCount" -> Json.fromInt(value.exactIntentCaseCount),
    "exactIntentWithoutAcceptableVariantCount" -> Json.fromInt(value.exactIntentWithoutAcceptableVariantCount),
    "invalidVariantJudgmentIdentityCount" -> Json.fromInt(value.invalidVariantJudgmentIdentityCount),
    "invalidProviderJudgmentIdentityCount" -> Json.fromInt(value.invalidProviderJudgmentIdentityCount),
    "invalidServiceIntentJudgmentIdentityCount" -> Json.fromInt(value.invalidServiceIntentJudgmentIdentityCount),
    "strictCorpusValidationPassed" -> Json.fromBoolean(value.strictCorpusValidationPassed),
    "strictPolicyValidationPassed" -> Json.fromBoolean(value.strictPolicyValidationPassed),
    "partitionValidationPassed" -> Json.fromBoolean(value.partitionValidationPassed),
    "sliceValidationPassed" -> Json.fromBoolean(value.sliceValidationPassed),
    "frozen" -> Json.fromBoolean(value.frozen),
  )

  private def sliceCounts(root: JsonObject): Either[BeautyQProtectedInputAuditError, Vector[(EvaluationSliceId, Int)]] =
    root("orderedRequiredSliceCounts").flatMap(_.asArray).toRight(BeautyQProtectedInputAuditError.InvalidDocument).flatMap { values =>
      values.foldLeft[Either[BeautyQProtectedInputAuditError, Vector[(EvaluationSliceId, Int)]]](Right(Vector.empty)) {
        case (acc, json) => acc.flatMap { current =>
          for {
            obj <- json.asObject.toRight(BeautyQProtectedInputAuditError.InvalidDocument)
            _ <- Either.cond(obj.keys.toSet == SliceFields, (), BeautyQProtectedInputAuditError.InvalidDocument)
            rawId <- string(obj, "sliceId")
            sliceId <- EvaluationSliceId.from(rawId).left.map(_ => BeautyQProtectedInputAuditError.InvalidDocument)
            count <- int(obj, "caseCount")
          } yield current :+ (sliceId -> count)
        }
      }
    }

  private def string(root: JsonObject, field: String): Either[BeautyQProtectedInputAuditError, String] =
    root(field).flatMap(_.asString).toRight(BeautyQProtectedInputAuditError.InvalidDocument)

  private def int(root: JsonObject, field: String): Either[BeautyQProtectedInputAuditError, Int] =
    root(field).flatMap(_.asNumber).flatMap(_.toInt).toRight(BeautyQProtectedInputAuditError.InvalidDocument)

  private def boolean(root: JsonObject, field: String): Either[BeautyQProtectedInputAuditError, Boolean] =
    root(field).flatMap(_.asBoolean).toRight(BeautyQProtectedInputAuditError.InvalidDocument)
}
