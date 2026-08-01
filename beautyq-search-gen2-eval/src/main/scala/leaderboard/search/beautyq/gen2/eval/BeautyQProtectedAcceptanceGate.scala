package leaderboard.search.beautyq.gen2.eval

import io.circe.Json
import leaderboard.search.gen2.eval.{AggregateSection, MetricKeyScope}

final class BeautyQProtectedAcceptanceCheck private (
  val code: String,
  val passed: Boolean,
  val observed: String,
  val expected: String,
) {
  def toJson: Json = Json.obj(
    "code" -> Json.fromString(code),
    "passed" -> Json.fromBoolean(passed),
    "observed" -> Json.fromString(observed),
    "expected" -> Json.fromString(expected),
  )
}
object BeautyQProtectedAcceptanceCheck {
  private[eval] def create(code: String, passed: Boolean, observed: String, expected: String): BeautyQProtectedAcceptanceCheck =
    new BeautyQProtectedAcceptanceCheck(code, passed, observed, expected)
}

final class BeautyQProtectedAcceptanceResult private[BeautyQProtectedAcceptanceResult] (
  val schemaVersion: String,
  val passed: Boolean,
  val evaluationPolicyVersion: String,
  val protectedAcceptancePolicyVersion: String,
  val policyFingerprint: String,
  val protectedCorpusFingerprint: String,
  val protectedCaseCount: Int,
  val protectedReportDigest: String,
  val checks: Vector[BeautyQProtectedAcceptanceCheck],
) {
  def toJson: Json = Json.obj(
    "schemaVersion" -> Json.fromString(schemaVersion),
    "passed" -> Json.fromBoolean(passed),
    "evaluationPolicyVersion" -> Json.fromString(evaluationPolicyVersion),
    "protectedAcceptancePolicyVersion" -> Json.fromString(protectedAcceptancePolicyVersion),
    "policyFingerprint" -> Json.fromString(policyFingerprint),
    "protectedCorpusFingerprint" -> Json.fromString(protectedCorpusFingerprint),
    "protectedCaseCount" -> Json.fromInt(protectedCaseCount),
    "protectedReportDigest" -> Json.fromString(protectedReportDigest),
    "checks" -> Json.fromValues(checks.map(_.toJson)),
  )
}
object BeautyQProtectedAcceptanceResult {
  val CurrentSchemaVersion = "beautyq-protected-acceptance-gate-v1"
  private[eval] def create(
    passed: Boolean,
    evaluationPolicyVersion: String,
    protectedAcceptancePolicyVersion: String,
    policyFingerprint: String,
    protectedCorpusFingerprint: String,
    protectedCaseCount: Int,
    protectedReportDigest: String,
    checks: Vector[BeautyQProtectedAcceptanceCheck],
  ): BeautyQProtectedAcceptanceResult =
    new BeautyQProtectedAcceptanceResult(
      CurrentSchemaVersion,
      passed,
      evaluationPolicyVersion,
      protectedAcceptancePolicyVersion,
      policyFingerprint,
      protectedCorpusFingerprint,
      protectedCaseCount,
      protectedReportDigest,
      checks,
    )
}

object BeautyQProtectedAcceptanceGate {
  def evaluate(
    visible: BeautyQMeasuredEvaluationResult,
    protectedRun: BeautyQMeasuredEvaluationResult,
    protectedCorpus: BeautyQProtectedEvaluationCorpus,
    policy: BeautyQProtectedAcceptancePolicy,
  ): BeautyQProtectedAcceptanceResult = {
    def check(code: String, passed: Boolean, observed: String, expected: String): BeautyQProtectedAcceptanceCheck =
      BeautyQProtectedAcceptanceCheck.create(code, passed, observed, expected)

    val visibleQualityGreen = visible.correctionGate.passed &&
      visible.correctionGate.forbiddenHitCount == 0 &&
      visible.correctionGate.supplementScoreSeparation.forbiddenCount == 0
    val visibleChecks = Vector(
      check("visible-correction-gate", visible.correctionGate.passed, visible.correctionGate.passed.toString, "true"),
      check("visible-forbidden-hits", visible.correctionGate.forbiddenHitCount == 0, visible.correctionGate.forbiddenHitCount.toString, "0"),
      check("visible-quality-threshold", visible.correctionGate.supplementScoreSeparation.forbiddenCount == 0, actualThresholdStatus(visible.correctionGate.supplementScoreSeparation), "not_required"),
    )
    val protectedChecks = Vector(
      check("protected-hard-no-harm", protectedRun.correctionGate.passed, protectedRun.correctionGate.passed.toString, "true"),
      check("protected-required-full-search", protectedRun.correctionGate.checks.find(_.stableCode == "required-full-search").exists(_.passed), "typed-correction-gate", "required/full_search"),
      check("protected-complete-warmup", protectedRun.correctionGate.checks.find(_.stableCode == "complete-warmup").exists(_.passed), "typed-correction-gate", "true"),
      check("protected-complete-measured", protectedRun.correctionGate.checks.find(_.stableCode == "complete-measured-passes").exists(_.passed), "typed-correction-gate", "true"),
      check("protected-deterministic", protectedRun.correctionGate.checks.find(_.stableCode == "deterministic-measured-rankings").exists(_.passed), "typed-correction-gate", "true"),
      check("protected-no-degradation", protectedRun.correctionGate.checks.find(_.stableCode == "no-request-degradation").exists(_.passed), "typed-correction-gate", "0"),
      check("protected-no-duplicates", protectedRun.correctionGate.checks.find(_.stableCode == "no-public-identity-duplicates").exists(_.passed), "typed-correction-gate", "0"),
      check("protected-no-forbidden", protectedRun.correctionGate.checks.find(_.stableCode == "no-forbidden-hits").exists(_.passed), "typed-correction-gate", "0"),
      check("protected-prefix", protectedRun.correctionGate.checks.find(_.stableCode == "baseline-prefix-preserved").exists(_.passed), "typed-correction-gate", "0"),
      check("protected-owned-components", protectedRun.correctionGate.checks.find(_.stableCode == "baseline-owned-components-preserved").exists(_.passed), "typed-correction-gate", "0"),
      check("protected-append-budget", protectedRun.correctionGate.checks.find(_.stableCode == "append-budget-preserved").exists(_.passed), "typed-correction-gate", "0"),
    )
    val inventoryChecks = Vector(
      check("protected-corpus-fingerprint", protectedCorpus.corpusFingerprint == policy.expectedCorpusFingerprint, protectedCorpus.corpusFingerprint, policy.expectedCorpusFingerprint),
      check("protected-case-count", protectedCorpus.caseCount == policy.expectedCaseCount, protectedCorpus.caseCount.toString, policy.expectedCaseCount.toString),
    ) ++ policy.requiredSliceMinimums.zip(protectedCorpus.orderedRequiredSliceCounts).map { case (requirement, (sliceId, count)) =>
      check(
        s"slice-count-${sliceId.value}",
        requirement.sliceId == sliceId && count >= requirement.minimumCaseCount,
        count.toString,
        s">=${requirement.minimumCaseCount}",
      )
    }
    val identityChecks = Vector(
      check("evaluation-policy-version", protectedRun.evaluationPolicyVersion == policy.evaluationPolicyVersion, protectedRun.evaluationPolicyVersion, policy.evaluationPolicyVersion),
    )
    val metricChecks = orderedMetricChecks(protectedRun.report, policy)
    val checks = visibleChecks ++ protectedChecks ++ inventoryChecks ++ identityChecks ++ metricChecks
    BeautyQProtectedAcceptanceResult.create(
      visibleQualityGreen && checks.forall(_.passed),
      protectedRun.evaluationPolicyVersion,
      policy.protectedAcceptancePolicyVersion,
      policy.fingerprint,
      protectedCorpus.corpusFingerprint,
      protectedCorpus.caseCount,
      protectedRun.protectedReportDigest,
      checks,
    )
  }

  private[eval] def orderedPolicyAggregates(
    report: leaderboard.search.gen2.eval.EvaluationReport,
    policy: BeautyQProtectedAcceptancePolicy,
  ): Either[String, Vector[(String, AggregateSection)]] = {
    val orderedKeys = policy.requiredMetricMinimums.map(_.observationKey).distinct
    orderedKeys.foldLeft[Either[String, Vector[(String, AggregateSection)]]](Right(Vector.empty)) {
      case (acc, key) => acc.flatMap { done =>
        sectionsForKey(report, key) match {
          case Vector(section) => Right(done :+ (key -> section))
          case Vector() => Left(s"required observation key $key is missing")
          case _ => Left(s"required observation key $key is duplicated")
        }
      }
    }
  }

  private def orderedMetricChecks(
    report: leaderboard.search.gen2.eval.EvaluationReport,
    policy: BeautyQProtectedAcceptancePolicy,
  ): Vector[BeautyQProtectedAcceptanceCheck] = policy.requiredMetricMinimums.map { requirement =>
    val code = s"metric-${requirement.observationKey}-${scopeKey(requirement.scope)}"
        sectionsForKey(report, requirement.observationKey) match {
          case sections if sections.isEmpty => BeautyQProtectedAcceptanceCheck.create(code, false, "missing-observation-key", requirement.minimum.toString)
          case sections if sections.size > 1 => BeautyQProtectedAcceptanceCheck.create(code, false, "duplicate-observation-key", requirement.minimum.toString)
          case Vector(section) =>
            section.metricObservations.filter(_.scope == requirement.scope) match {
              case observations if observations.isEmpty => BeautyQProtectedAcceptanceCheck.create(code, false, "missing-scope", requirement.minimum.toString)
              case observations if observations.size > 1 => BeautyQProtectedAcceptanceCheck.create(code, false, "duplicate-scope", requirement.minimum.toString)
              case Vector(observation) if observation.applicableCount == 0 => BeautyQProtectedAcceptanceCheck.create(code, false, "not-applicable", requirement.minimum.toString)
              case Vector(observation) => BeautyQProtectedAcceptanceCheck.create(code, observation.average >= requirement.minimum, observation.average.toString, s">=${requirement.minimum}")
              case _ => BeautyQProtectedAcceptanceCheck.create(code, false, "duplicate-scope", requirement.minimum.toString)
            }
          case _ => BeautyQProtectedAcceptanceCheck.create(code, false, "duplicate-observation-key", requirement.minimum.toString)
        }
    }

  private def sectionsForKey(report: leaderboard.search.gen2.eval.EvaluationReport, key: String): Vector[AggregateSection] = key match {
    case "protected-global" => Vector(report.globalAggregates)
    case value if value.startsWith("protected-partition:") => report.partitionAggregates.collect { case (partition, section) if partition.stableCode == value.stripPrefix("protected-partition:") => section }
    case value if value.startsWith("protected-surface:") => report.surfaceAggregates.collect { case (surface, section) if surface.value == value.stripPrefix("protected-surface:") => section }
    case value if value.startsWith("protected-slice:") => report.sliceAggregates.collect { case (slice, section) if slice.value == value.stripPrefix("protected-slice:") => section }
    case _ => Vector.empty
  }

  private[eval] def scopeKey(scope: MetricKeyScope): String = s"${scope.surfaceId.value}/${scope.metricId.value}/${scope.cutoff.value}"

  def actualThresholdStatus(separation: BeautyQSupplementScoreSeparation): String =
    if (separation.forbiddenCount == 0) "not_required"
    else if (separation.strictlySeparable) "candidate_available"
    else "explicit_policy_decision_required"
}
