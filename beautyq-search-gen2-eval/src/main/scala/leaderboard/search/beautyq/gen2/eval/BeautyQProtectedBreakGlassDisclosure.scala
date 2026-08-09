package leaderboard.search.beautyq.gen2.eval

import io.circe.Json
import leaderboard.search.gen2.eval.{EvaluationReport, EvaluationResultId, MetricValue, RankingJudgments}

final class BeautyQProtectedBreakGlassDisclosedCase private[eval] (
  val corpusCase: CorpusCase,
  val relevantSurface: String,
  val relevantMetric: String,
  val cutoff: Int,
  val topCutoffIds: Vector[EvaluationResultId],
) {
  private[eval] def toJson: Json = {
    def ids(values: Vector[EvaluationResultId]): Json =
      Json.fromValues(values.map(value => Json.fromString(value.value)))

    def judgments(value: RankingJudgments): Json = Json.obj(
      "acceptableIds" -> ids(value.acceptableIds),
      "forbiddenIds" -> ids(value.forbiddenIds),
      "neutralIds" -> ids(value.neutralIds),
      "gradedGains" -> Json.fromValues(value.gradedGains.map(gain => Json.obj(
        "id" -> Json.fromString(gain.id.value),
        "gain" -> Json.fromInt(gain.gain.value),
      ))),
    )

    Json.obj(
      "case" -> Json.obj(
        "id" -> Json.fromString(corpusCase.caseId.value),
        "partition" -> Json.fromString(corpusCase.partition.stableCode),
        "judgmentMode" -> Json.fromString(corpusCase.judgmentMode.stableCode),
        "query" -> Json.fromString(corpusCase.query),
        "language" -> Json.fromString(corpusCase.language),
        "slices" -> Json.fromValues(corpusCase.slices.map(value => Json.fromString(value.value))),
        "userIntent" -> Json.fromString(corpusCase.userIntent),
        "notes" -> Json.fromValues(corpusCase.notes.map(Json.fromString)),
        "judgments" -> Json.obj(
          "variants" -> judgments(corpusCase.variantJudgments),
          "providers" -> judgments(corpusCase.providerJudgments),
          "serviceIntents" -> judgments(corpusCase.serviceIntentJudgments),
        ),
      ),
      "diagnostic" -> Json.obj(
        "surface" -> Json.fromString(relevantSurface),
        "metric" -> Json.fromString(relevantMetric),
        "cutoff" -> Json.fromInt(cutoff),
        "topCutoffIds" -> ids(topCutoffIds),
      ),
    )
  }
}

final class BeautyQProtectedBreakGlassDisclosure private[eval] (
  val applicationRevision: String,
  val authorizationId: String,
  val failedCheckCode: String,
  val protectedCorpusFingerprint: String,
  val policyFingerprint: String,
  val disclosedCases: Vector[BeautyQProtectedBreakGlassDisclosedCase],
) {
  val schemaVersion: String = BeautyQProtectedBreakGlassDisclosure.CurrentSchemaVersion
  val applicationRevisionSource: String = "system-property"

  def toJson: Json = Json.obj(
    "schemaVersion" -> Json.fromString(schemaVersion),
    "applicationRevision" -> Json.fromString(applicationRevision),
    "applicationRevisionSource" -> Json.fromString(applicationRevisionSource),
    "authorizationId" -> Json.fromString(authorizationId),
    "failedCheckCode" -> Json.fromString(failedCheckCode),
    "protectedCorpusFingerprint" -> Json.fromString(protectedCorpusFingerprint),
    "policyFingerprint" -> Json.fromString(policyFingerprint),
    "disclosedCases" -> Json.fromValues(disclosedCases.map(_.toJson)),
  )
}

/**
  * Typed, check-scoped disclosure projection for the explicitly authorised
  * Q2 break-glass run. The ordinary protected report remains aggregate-only.
  *
  * For the authorised success/10 check, RankingEvaluator produces only binary
  * applicable values and the frozen minimum is exactly one. The aggregate
  * passes iff every applicable case succeeds, so all and only zero-success
  * cases are the unique case-level contributing set.
  */
object BeautyQProtectedBreakGlassDisclosure {
  val CurrentSchemaVersion = "beautyq-protected-break-glass-disclosure-v1"
  val AuthorizedCheckCode = "metric-protected-slice:exact-intent-variants/success/10"
  val AuthorizationId = "q2-break-glass-exact-intent-variants-success-10-v1"
  val Cycle2AuthorizationId = "q2-break-glass-exact-intent-variants-success-10-cycle-2-v1"
  val FullSliceCycle3AuthorizationId = "q2-break-glass-exact-intent-full-slice-cycle-3-v1"
  val ConvergenceAuthorizationId = "q2-break-glass-exact-intent-convergence-v1"
  val PostRecoveryAuthorizationId = "q2-break-glass-exact-intent-post-recovery-v1"
  val RecoveryRotation2AuthorizationId = "q2-break-glass-exact-intent-recovery-rotation-2-v1"
  val RecoveryRotation3AuthorizationId = "q2-break-glass-exact-intent-recovery-rotation-3-v1"
  val RecoveryRotation4AuthorizationId =
    "q2-break-glass-exact-intent-recovery-rotation-4-v1"
  val RecoveryRotation5AuthorizationId =
    "q2-break-glass-exact-intent-recovery-rotation-5-v1"
  val AuthorizedSliceId = "exact-intent"

  private[eval] sealed trait DisclosureScope
  private[eval] object DisclosureScope {
    case object MinimalContributors extends DisclosureScope
    final case class CompleteSlice(sliceId: String, expectedCount: Int) extends DisclosureScope
  }

  final class AuthorizationRecord private[eval] (
    val id: String,
    val applicationRevision: String,
    val protectedCorpusFingerprint: String,
    val policyFingerprint: String,
    val failedCheckCode: String,
    private[eval] val disclosureScope: DisclosureScope,
  )

  val FirstAuthorization: AuthorizationRecord = new AuthorizationRecord(
    AuthorizationId,
    "89811d5f2ad5327b24b2aac4641f4716d781000e",
    "825ca2862ad99b61002bcf04bfe000168eccc61760d9a1d091c0c4320afc9bb0",
    "0f86960495e64b430e2ba55eac012bf00a8e80f0eb8d1500c967d582cd673098",
    AuthorizedCheckCode,
    DisclosureScope.MinimalContributors,
  )

  val Cycle2Authorization: AuthorizationRecord = new AuthorizationRecord(
    Cycle2AuthorizationId,
    "655ebd9d21920d0b03c08df487acc8b4bd0db590",
    "7a654c7323f822f26bccc6d5ae7adde3faf4bfd790984ae63fb25c34978c9188",
    "905894412cb68ed8447ecc9c99ffe1ac9ee94e22001f6a8c206cdd81c9a165ce",
    AuthorizedCheckCode,
    DisclosureScope.MinimalContributors,
  )

  val FullSliceCycle3Authorization: AuthorizationRecord = new AuthorizationRecord(
    FullSliceCycle3AuthorizationId,
    "718660275e72b287c24aec494c174c3d3a55bef0",
    "23fe801f0b1c48b77847a94d0eb260ee5e2faac5018c67bab5debce382b5f2d0",
    "c80f15f5cb7beb8ad5f01bec82146c67e17d8a3bc0a31e4e88a49fd115e9b360",
    AuthorizedCheckCode,
    DisclosureScope.CompleteSlice(AuthorizedSliceId, expectedCount = 8),
  )

  val ConvergenceAuthorization: AuthorizationRecord = new AuthorizationRecord(
    ConvergenceAuthorizationId,
    "eeccefe8bde82a1ac93f426aa4e58cf936178640",
    "d72b29d6d9e13e21b34722fa4c8219975aae7613ba0c003326baefc5da056a6e",
    "6a8a4f68f69d85467db941284dfc5181f48bfb3f7d3d7631d87fd9c17261c622",
    AuthorizedCheckCode,
    DisclosureScope.CompleteSlice(AuthorizedSliceId, expectedCount = 8),
  )

  val PostRecoveryAuthorization: AuthorizationRecord = new AuthorizationRecord(
    PostRecoveryAuthorizationId,
    "54e488690124c69f87f82346de3a9e1e300db49c",
    "bd821a976622ad0157ec4c818f92187c60e08b2b5123f6306dea7878ef0aad17",
    "5422773856685e4ff781b04c39b266c6ac5de06c0fadfb63f8d4f6bea3929755",
    AuthorizedCheckCode,
    DisclosureScope.CompleteSlice(AuthorizedSliceId, expectedCount = 8),
  )

  val RecoveryRotation2Authorization: AuthorizationRecord = new AuthorizationRecord(
    RecoveryRotation2AuthorizationId,
    "440fdf2827a880ea02c36fb3044c18d1b1874c23",
    "87002a0e79984365320b40f31f8cf4c76c7a4757d86ab3a4e1674819514651af",
    "14781a4c2832374ee0f46540c8a532fc853d291841715ed816f12d0fa23c8045",
    AuthorizedCheckCode,
    DisclosureScope.CompleteSlice(AuthorizedSliceId, expectedCount = 8),
  )

  val RecoveryRotation3Authorization: AuthorizationRecord = new AuthorizationRecord(
    RecoveryRotation3AuthorizationId,
    "4e3f6aed518a3d86d8336e4b575edee7012832e9",
    "f531e287027595a602fe97f44cae7d7cfbe2759be8f1b18d594d21ecbdb83f6e",
    "c9c0677f25b45310376d0e2aa4c678a1e579a984f0dc8cd2e8ef0bba6990289b",
    AuthorizedCheckCode,
    DisclosureScope.CompleteSlice(AuthorizedSliceId, expectedCount = 8),
  )

  val RecoveryRotation4Authorization: AuthorizationRecord =
    new AuthorizationRecord(
      RecoveryRotation4AuthorizationId,
      "83caf9fb8bcde8da569cedd175a72be62855b8e7",
      "2cf8cf77085faf5ab84d2eafd8b68e0020f64bf89df794f0419d8ce55684ef2f",
      "30ab025069e175400dfdbd5f3c9e0a49dff23c1ac005537c3dc2114713c3fdb8",
      AuthorizedCheckCode,
      DisclosureScope.CompleteSlice(AuthorizedSliceId, expectedCount = 8),
    )

  val RecoveryRotation5Authorization: AuthorizationRecord =
    new AuthorizationRecord(
      RecoveryRotation5AuthorizationId,
      "020e2c4f3ab02b4ee735f42b35731e4e784a5f32",
      "3893ad32b8bb8dc4a77df85ae40dd0537a32090111ae2ee2528e42090f9c1874",
      "5a47394dcc6eb0e483c9998a9e481083522b3876c1ad8a084d683961a16d38b7",
      AuthorizedCheckCode,
      DisclosureScope.CompleteSlice(AuthorizedSliceId, expectedCount = 8),
    )

  val Authorizations: Vector[AuthorizationRecord] =
    Vector(FirstAuthorization, Cycle2Authorization, FullSliceCycle3Authorization, ConvergenceAuthorization, PostRecoveryAuthorization, RecoveryRotation2Authorization, RecoveryRotation3Authorization, RecoveryRotation4Authorization, RecoveryRotation5Authorization)

  def authorizationById(id: String): Option[AuthorizationRecord] =
    Authorizations.find(_.id == id)

  def derive(
    acceptance: BeautyQProtectedAcceptanceResult,
    report: EvaluationReport,
    corpus: BeautyQEvaluationCorpus,
    policy: BeautyQProtectedAcceptancePolicy,
    applicationRevision: String,
    authorizationId: String,
    failedCheckCode: String,
  ): Either[String, BeautyQProtectedBreakGlassDisclosure] = {
    authorizationById(authorizationId) match {
      case None => Left("invalid_authorization_id")
      case Some(authorization) => deriveAuthorized(
        acceptance,
        report,
        corpus,
        policy,
        applicationRevision,
        authorization,
        failedCheckCode,
      )
    }
  }

  private[eval] def deriveAuthorized(
    acceptance: BeautyQProtectedAcceptanceResult,
    report: EvaluationReport,
    corpus: BeautyQEvaluationCorpus,
    policy: BeautyQProtectedAcceptancePolicy,
    applicationRevision: String,
    authorization: AuthorizationRecord,
    failedCheckCode: String,
  ): Either[String, BeautyQProtectedBreakGlassDisclosure] = {
    val failedCodes = acceptance.checks.filterNot(_.passed).map(_.code)
    if (applicationRevision.isEmpty || applicationRevision.trim != applicationRevision || applicationRevision == "working-tree")
      Left("invalid_application_revision")
    else if (applicationRevision != authorization.applicationRevision)
      Left("break_glass_authorization_revision_mismatch")
    else if (corpus.corpusFingerprint != authorization.protectedCorpusFingerprint)
      Left("break_glass_authorization_corpus_mismatch")
    else if (policy.fingerprint != authorization.policyFingerprint)
      Left("break_glass_authorization_policy_mismatch")
    else if (failedCheckCode != authorization.failedCheckCode)
      Left("failed_check_not_authorized")
    else if (acceptance.passed || failedCodes != Vector(failedCheckCode))
      Left("break_glass_evidence_changed")
    else policy.requiredMetricMinimums.find(metricCode(_) == failedCheckCode) match {
      case None => Left("failed_check_not_declared_by_policy")
      case Some(requirement) if requirement.scope.metricId.value != "success" || requirement.minimum != BigDecimal(1).setScale(12) =>
        Left("minimal_disclosure_contract_missing")
      case Some(requirement) =>
        val sliceId = requirement.observationKey.stripPrefix("protected-slice:")
        if (sliceId == requirement.observationKey) Left("minimal_disclosure_contract_missing")
        else {
          val byId = corpus.cases.map(current => current.caseId.value -> current).toMap
          val observations = report.caseResults.flatMap { caseResult =>
            val inSlice = caseResult.slices.exists(_.exists(_.value == sliceId))
            if (!inSlice) Vector.empty
            else caseResult.surfaceResults.collect {
              case (surface, result) if surface == requirement.scope.surfaceId =>
                result.metricRows.collectFirst {
                  case row if row.cutoff == requirement.scope.cutoff =>
                    row.success match {
                      case actual: MetricValue.Applicable => Right((caseResult.caseId.value, actual.value, result.uniqueRanking.take(requirement.scope.cutoff.value)))
                      case _: MetricValue.NotApplicable => Left("minimal_disclosure_contract_missing")
                    }
                }.toRight("minimal_disclosure_contract_missing").flatten
            }
          }
          val decoded = observations.foldLeft[Either[String, Vector[(String, BigDecimal, Vector[EvaluationResultId])]]](Right(Vector.empty)) {
            case (acc, current) => acc.flatMap(done => current.map(done :+ _))
          }
          decoded.flatMap { values =>
            val binary = values.forall { case (_, value, _) => value == BigDecimal(0).setScale(12) || value == BigDecimal(1).setScale(12) }
            if (values.isEmpty || !binary) Left("minimal_disclosure_contract_missing")
            else {
              val selectedValues = authorization.disclosureScope match {
                case DisclosureScope.MinimalContributors =>
                  Right(values.filter { case (_, value, _) => value == BigDecimal(0).setScale(12) })
                case DisclosureScope.CompleteSlice(authorizedSlice, expectedCount) =>
                  if (sliceId != authorizedSlice) Left("break_glass_authorization_slice_mismatch")
                  else if (values.size != expectedCount) Left("break_glass_authorization_disclosed_count_mismatch")
                  else Right(values)
              }
              selectedValues.flatMap { selectedSource =>
                val selected = selectedSource.map { case (caseId, _, topCutoffIds) =>
                byId.get(caseId).map(current => new BeautyQProtectedBreakGlassDisclosedCase(
                  current,
                  requirement.scope.surfaceId.value,
                  requirement.scope.metricId.value,
                  requirement.scope.cutoff.value,
                  topCutoffIds,
                ))
                }
                if (selected.isEmpty || selected.exists(_.isEmpty)) Left("no_contributing_cases_for_failed_check")
                else Right(new BeautyQProtectedBreakGlassDisclosure(
                  applicationRevision,
                  authorization.id,
                  failedCheckCode,
                  corpus.corpusFingerprint,
                  policy.fingerprint,
                  selected.flatten,
                ))
              }
            }
          }
        }
    }
  }

  def metricCode(requirement: BeautyQProtectedMetricMinimum): String =
    s"metric-${requirement.observationKey}-${BeautyQProtectedAcceptanceGate.scopeKey(requirement.scope)}"
}
