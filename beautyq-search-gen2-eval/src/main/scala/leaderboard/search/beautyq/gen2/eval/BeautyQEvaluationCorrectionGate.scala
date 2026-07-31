package leaderboard.search.beautyq.gen2.eval

import io.circe.Json
import leaderboard.search.beautyq.gen2.wiring.{BeautyQServingMode, SupplementStartupPolicy}

final class BeautyQSupplementScoreSeparation private[eval] (
  val forbiddenCount: Int,
  val nonForbiddenCount: Int,
  val minimumForbidden: Option[BigDecimal],
  val maximumForbidden: Option[BigDecimal],
  val minimumNonForbidden: Option[BigDecimal],
  val maximumNonForbidden: Option[BigDecimal],
  val candidateThreshold: Option[BigDecimal],
) {
  def strictlySeparable: Boolean = candidateThreshold.nonEmpty

  def toJson: Json = Json.obj(
    "forbiddenCount" -> Json.fromInt(forbiddenCount),
    "nonForbiddenCount" -> Json.fromInt(nonForbiddenCount),
    "minimumForbidden" -> minimumForbidden.fold(Json.Null)(Json.fromBigDecimal),
    "maximumForbidden" -> maximumForbidden.fold(Json.Null)(Json.fromBigDecimal),
    "minimumNonForbidden" -> minimumNonForbidden.fold(Json.Null)(Json.fromBigDecimal),
    "maximumNonForbidden" -> maximumNonForbidden.fold(Json.Null)(Json.fromBigDecimal),
    "strictlySeparable" -> Json.fromBoolean(strictlySeparable),
    "candidateThreshold" -> candidateThreshold.fold(Json.Null)(Json.fromBigDecimal),
  )
}
object BeautyQSupplementScoreSeparation {
  private val NonForbiddenJudgments = Set("acceptable", "neutral", "unjudged")

  private[eval] def from(values: Vector[BeautyQSupplementScoreObservation]): BeautyQSupplementScoreSeparation = {
    val supplement = values.filter(_.origin == "qdrant_supplement")
    val forbidden = supplement.filter(_.judgment == "forbidden").map(_.score)
    val nonForbidden = supplement.filter(value => NonForbiddenJudgments.contains(value.judgment)).map(_.score)
    val maximumForbidden = forbidden.maxOption
    val minimumNonForbidden = nonForbidden.minOption
    val threshold = for {
      maximum <- maximumForbidden
      minimum <- minimumNonForbidden
      if maximum < minimum
    } yield (maximum + minimum) / BigDecimal(2)
    new BeautyQSupplementScoreSeparation(
      forbidden.size,
      nonForbidden.size,
      forbidden.minOption,
      maximumForbidden,
      minimumNonForbidden,
      nonForbidden.maxOption,
      threshold,
    )
  }
}

final class BeautyQEvaluationCorrectionCheck private[BeautyQEvaluationCorrectionCheck] (
  val stableCode: String,
  val passed: Boolean,
  val observed: String,
  val expected: String,
)
object BeautyQEvaluationCorrectionCheck {
  private[eval] def create(
    stableCode: String,
    passed: Boolean,
    observed: String,
    expected: String,
  ): BeautyQEvaluationCorrectionCheck =
    new BeautyQEvaluationCorrectionCheck(stableCode, passed, observed, expected)
}

final class BeautyQEvaluationCorrectionGateResult private[BeautyQEvaluationCorrectionGateResult] (
  val checks: Vector[BeautyQEvaluationCorrectionCheck],
  val caseCount: Int,
  val warmupExecutions: Int,
  val measuredExecutions: Int,
  val duplicateIdentityCount: Int,
  val forbiddenHitCount: Int,
  val degradedRequestCount: Int,
  val baselinePrefixViolationCount: Int,
  val baselineOwnedComponentViolationCount: Int,
  val appendBudgetViolationCount: Int,
  val supplementScoreSeparation: BeautyQSupplementScoreSeparation,
) {
  def passed: Boolean = checks.forall(_.passed)

  def toJson: Json = Json.obj(
    "schemaVersion" -> Json.fromString("beautyq-evaluation-correction-gate-v1"),
    "passed" -> Json.fromBoolean(passed),
    "checks" -> Json.fromValues(checks.map { check => Json.obj(
      "code" -> Json.fromString(check.stableCode),
      "passed" -> Json.fromBoolean(check.passed),
      "observed" -> Json.fromString(check.observed),
      "expected" -> Json.fromString(check.expected),
    ) }),
    "observations" -> Json.obj(
      "caseCount" -> Json.fromInt(caseCount),
      "warmupExecutions" -> Json.fromInt(warmupExecutions),
      "measuredExecutions" -> Json.fromInt(measuredExecutions),
      "duplicateIdentityCount" -> Json.fromInt(duplicateIdentityCount),
      "forbiddenHitCount" -> Json.fromInt(forbiddenHitCount),
      "degradedRequestCount" -> Json.fromInt(degradedRequestCount),
      "baselinePrefixViolationCount" -> Json.fromInt(baselinePrefixViolationCount),
      "baselineOwnedComponentViolationCount" -> Json.fromInt(baselineOwnedComponentViolationCount),
      "appendBudgetViolationCount" -> Json.fromInt(appendBudgetViolationCount),
    ),
    "qualityThresholdStatus" -> Json.fromString(
      if (supplementScoreSeparation.forbiddenCount == 0) "not_required"
      else if (supplementScoreSeparation.strictlySeparable) "candidate_available"
      else "explicit_policy_decision_required"
    ),
    "supplementScoreSeparation" -> supplementScoreSeparation.toJson,
    "protectedHoldoutStatus" -> Json.fromString("not_configured"),
    "acceptedBaselineStatus" -> Json.fromString("not_generated"),
  )
}
object BeautyQEvaluationCorrectionGateResult {
  private[eval] def create(
    checks: Vector[BeautyQEvaluationCorrectionCheck],
    caseCount: Int,
    warmupExecutions: Int,
    measuredExecutions: Int,
    duplicateIdentityCount: Int,
    forbiddenHitCount: Int,
    degradedRequestCount: Int,
    baselinePrefixViolationCount: Int,
    baselineOwnedComponentViolationCount: Int,
    appendBudgetViolationCount: Int,
    supplementScoreSeparation: BeautyQSupplementScoreSeparation,
  ): BeautyQEvaluationCorrectionGateResult =
    new BeautyQEvaluationCorrectionGateResult(
      checks,
      caseCount,
      warmupExecutions,
      measuredExecutions,
      duplicateIdentityCount,
      forbiddenHitCount,
      degradedRequestCount,
      baselinePrefixViolationCount,
      baselineOwnedComponentViolationCount,
      appendBudgetViolationCount,
      supplementScoreSeparation,
    )
}

object BeautyQEvaluationCorrectionGate {
  private[eval] def evaluate(
    policy: SupplementStartupPolicy,
    servingMode: BeautyQServingMode,
    caseCount: Int,
    warmup: Vector[BeautyQMeasuredCase],
    measured: Vector[Vector[BeautyQMeasuredCase]],
    deterministic: Boolean,
  ): BeautyQEvaluationCorrectionGateResult = {
    val measuredFlat = measured.flatten
    val expectedWarmup = caseCount * BeautyQEvaluationPolicy.warmupPasses
    val expectedMeasured = caseCount * BeautyQEvaluationPolicy.measuredPasses
    val duplicateCount = measuredFlat.map(_.duplicateIdentityCount).sum
    val forbiddenCount = measuredFlat.map(_.forbiddenHitCount).sum
    val degradedCount = measuredFlat.count(_.requestDegraded)
    val prefixViolations = measuredFlat.count(value => !value.baselinePrefixPreserved)
    val ownedViolations = measuredFlat.count(value => !value.baselineOwnedComponentsPreserved)
    val budgetViolations = measuredFlat.count(value => !value.appendBudgetPreserved)
    val scoreSeparation = BeautyQSupplementScoreSeparation.from(measuredFlat.flatMap(_.scoreObservations))
    val requiredFullSearch =
      policy == SupplementStartupPolicy.Required && servingMode == BeautyQServingMode.FullSearch

    def check(code: String, passed: Boolean, observed: String, expected: String) =
      BeautyQEvaluationCorrectionCheck.create(code, passed, observed, expected)

    val checks = Vector(
      check("required-full-search", requiredFullSearch, s"${policy.stableCode}/${servingMode.modeCode}", "required/full_search"),
      check("complete-warmup", warmup.size == expectedWarmup, warmup.size.toString, expectedWarmup.toString),
      check("complete-measured-passes", measuredFlat.size == expectedMeasured && measured.size == BeautyQEvaluationPolicy.measuredPasses, measuredFlat.size.toString, expectedMeasured.toString),
      check("deterministic-measured-rankings", deterministic, deterministic.toString, "true"),
      check("no-request-degradation", degradedCount == 0, degradedCount.toString, "0"),
      check("no-public-identity-duplicates", duplicateCount == 0, duplicateCount.toString, "0"),
      check("no-forbidden-hits", forbiddenCount == 0, forbiddenCount.toString, "0"),
      check("baseline-prefix-preserved", prefixViolations == 0, prefixViolations.toString, "0"),
      check("baseline-owned-components-preserved", ownedViolations == 0, ownedViolations.toString, "0"),
      check("append-budget-preserved", budgetViolations == 0, budgetViolations.toString, "0"),
    )
    BeautyQEvaluationCorrectionGateResult.create(
      checks,
      caseCount,
      warmup.size,
      measuredFlat.size,
      duplicateCount,
      forbiddenCount,
      degradedCount,
      prefixViolations,
      ownedViolations,
      budgetViolations,
      scoreSeparation,
    )
  }
}
