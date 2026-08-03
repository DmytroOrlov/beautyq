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

  def derive(
    acceptance: BeautyQProtectedAcceptanceResult,
    report: EvaluationReport,
    corpus: BeautyQEvaluationCorpus,
    policy: BeautyQProtectedAcceptancePolicy,
    applicationRevision: String,
    authorizationId: String,
    failedCheckCode: String,
  ): Either[String, BeautyQProtectedBreakGlassDisclosure] = {
    val failedCodes = acceptance.checks.filterNot(_.passed).map(_.code)
    if (applicationRevision.isEmpty || applicationRevision.trim != applicationRevision || applicationRevision == "working-tree")
      Left("invalid_application_revision")
    else if (authorizationId != AuthorizationId)
      Left("invalid_authorization_id")
    else if (failedCheckCode != AuthorizedCheckCode)
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
              val selected = values.collect { case (caseId, value, topCutoffIds) if value == BigDecimal(0).setScale(12) =>
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
                authorizationId,
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

  def metricCode(requirement: BeautyQProtectedMetricMinimum): String =
    s"metric-${requirement.observationKey}-${BeautyQProtectedAcceptanceGate.scopeKey(requirement.scope)}"
}
