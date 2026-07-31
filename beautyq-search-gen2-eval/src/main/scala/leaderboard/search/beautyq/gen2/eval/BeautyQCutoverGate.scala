package leaderboard.search.beautyq.gen2.eval

import io.circe.Json
import leaderboard.search.beautyq.gen2.contract.BeautySearchRequestGen2
import leaderboard.search.beautyq.gen2.wiring.{BeautyQServingMode, BeautyQSupplementPolicy}
import leaderboard.search.gen2.contract.{GeoPoint, PageRequest, PageSize}

enum BeautyQCutoverQueryFixture(
  val stableId: String,
  val query: String,
  val userLocation: Option[GeoPoint],
) {
  case QBroad006ReadyAppendProbe
      extends BeautyQCutoverQueryFixture(
        "q_broad_006_ready_append_probe",
        "beauty near Wandsbek Markt",
        None,
      )
  case ManicureRealRouteProbe
      extends BeautyQCutoverQueryFixture(
        "manicure_real_route_probe",
        "маникюр",
        None,
      )
  case QBroad001WidenedProbe
      extends BeautyQCutoverQueryFixture(
        "q_broad_001_widened_probe",
        "салон красоты wandsbek ногти",
        None,
      )
  case QBroad003WidenedProbe
      extends BeautyQCutoverQueryFixture(
        "q_broad_003_widened_probe",
        "что-то для лица рядом",
        Some(GeoPoint(BigDecimal("52.5"), BigDecimal("13.4"))),
      )

  def appendBudget: Int = BeautyQSupplementPolicy.appendOnly.maxAppended

  def request: BeautySearchRequestGen2 =
    BeautySearchRequestGen2(
      query = Some(query),
      filters = Vector.empty,
      requestedFacets = Vector.empty,
      sort = Vector.empty,
      page = PageRequest(cursor = None, size = BeautyQCutoverQueryFixture.pageSize),
      userLocation = userLocation,
    )
}

object BeautyQCutoverQueryFixture {
  val pageSize: PageSize = PageSize.from(20).getOrElse(throw new AssertionError("expected canonical cutover page size"))

  val required: Vector[BeautyQCutoverQueryFixture] = Vector(
    BeautyQCutoverQueryFixture.QBroad006ReadyAppendProbe,
    BeautyQCutoverQueryFixture.ManicureRealRouteProbe,
    BeautyQCutoverQueryFixture.QBroad001WidenedProbe,
    BeautyQCutoverQueryFixture.QBroad003WidenedProbe,
  )
}

final class BeautyQCutoverQueryObservation private[eval] (
  val fixture: BeautyQCutoverQueryFixture,
  val baselineIds: Vector[String],
  val resultIds: Vector[String],
  val supplementOnlyIds: Vector[String],
  val baselineOwnedComponentsPreserved: Boolean,
) {
  def queryId: String = fixture.stableId
  def appendBudget: Int = fixture.appendBudget

  private[eval] def syntheticCopy(
    baselineIds: Vector[String] = baselineIds,
    resultIds: Vector[String] = resultIds,
    supplementOnlyIds: Vector[String] = supplementOnlyIds,
    baselineOwnedComponentsPreserved: Boolean = baselineOwnedComponentsPreserved,
  ): BeautyQCutoverQueryObservation =
    new BeautyQCutoverQueryObservation(
      fixture,
      baselineIds,
      resultIds,
      supplementOnlyIds,
      baselineOwnedComponentsPreserved,
    )
}

object BeautyQCutoverQueryObservation {
  private[eval] def synthetic(
    fixture: BeautyQCutoverQueryFixture,
    baselineIds: Vector[String],
    resultIds: Vector[String],
    supplementOnlyIds: Vector[String],
    baselineOwnedComponentsPreserved: Boolean,
  ): BeautyQCutoverQueryObservation =
    new BeautyQCutoverQueryObservation(
      fixture,
      baselineIds,
      resultIds,
      supplementOnlyIds,
      baselineOwnedComponentsPreserved,
    )

  def fromEvidence(
    fixture: BeautyQCutoverQueryFixture,
    evidence: BeautyQNoHarmSupplementEvidence.SupplementEvidence,
  ): BeautyQCutoverQueryObservation =
    new BeautyQCutoverQueryObservation(
      fixture = fixture,
      baselineIds = evidence.baselineIds.map(_.value.toString),
      resultIds = evidence.resultIds,
      supplementOnlyIds = evidence.appendedIds.map(_.value.toString),
      baselineOwnedComponentsPreserved = evidence.baselineOwnedComponentsPreserved,
    )
}

final case class BeautyQCutoverMetrics(
  testedQueries: Int,
  improvedQueries: Int,
  unchangedQueries: Int,
  worsenedQueries: Int,
  totalSupplementOnlyAppends: Int,
  duplicateBaselineIds: Int,
  lostBaselineIds: Int,
  prefixOrderRegressions: Int,
  baselineOwnedComponentChanges: Int,
  appendBudgetViolations: Int,
)

final case class BeautyQCutoverCheck(id: String, passed: Boolean, observed: String, expected: String)

final class BeautyQCutoverGateResult private[BeautyQCutoverGateResult] (
  val metrics: BeautyQCutoverMetrics,
  val checks: Vector[BeautyQCutoverCheck],
  val readinessObserved: String,
  val readinessExpected: String,
) {
  def passed: Boolean = checks.forall(_.passed)

  def toJson: Json =
    Json.obj(
      "passed" -> Json.fromBoolean(passed),
      "readiness" -> Json.obj(
        "observed" -> Json.fromString(readinessObserved),
        "expected" -> Json.fromString(readinessExpected),
      ),
      "metrics" -> Json.obj(
        "testedQueries" -> Json.fromInt(metrics.testedQueries),
        "improvedQueries" -> Json.fromInt(metrics.improvedQueries),
        "unchangedQueries" -> Json.fromInt(metrics.unchangedQueries),
        "worsenedQueries" -> Json.fromInt(metrics.worsenedQueries),
        "totalSupplementOnlyAppends" -> Json.fromInt(metrics.totalSupplementOnlyAppends),
        "duplicateBaselineIds" -> Json.fromInt(metrics.duplicateBaselineIds),
        "lostBaselineIds" -> Json.fromInt(metrics.lostBaselineIds),
        "prefixOrderRegressions" -> Json.fromInt(metrics.prefixOrderRegressions),
        "baselineOwnedComponentChanges" -> Json.fromInt(metrics.baselineOwnedComponentChanges),
        "appendBudgetViolations" -> Json.fromInt(metrics.appendBudgetViolations),
      ),
      "checks" -> Json.fromValues(checks.map { check =>
        Json.obj(
          "id" -> Json.fromString(check.id),
          "passed" -> Json.fromBoolean(check.passed),
          "observed" -> Json.fromString(check.observed),
          "expected" -> Json.fromString(check.expected),
        )
      }),
    )
}

object BeautyQCutoverGateResult {
  private[eval] def from(
    metrics: BeautyQCutoverMetrics,
    checks: Vector[BeautyQCutoverCheck],
    readinessObserved: String,
    readinessExpected: String,
  ): BeautyQCutoverGateResult =
    new BeautyQCutoverGateResult(metrics, checks, readinessObserved, readinessExpected)
}

object BeautyQCutoverGate {
  val requiredFixtures: Vector[BeautyQCutoverQueryFixture] = BeautyQCutoverQueryFixture.required

  def evaluate(
    servingMode: BeautyQServingMode,
    observations: Vector[BeautyQCutoverQueryObservation],
  ): BeautyQCutoverGateResult = {
    val derived = observations.map(derive)
    val metrics = BeautyQCutoverMetrics(
      testedQueries = observations.size,
      improvedQueries = derived.count(_.improved),
      unchangedQueries = derived.count(value => !value.improved && !value.worsened),
      worsenedQueries = derived.count(_.worsened),
      totalSupplementOnlyAppends = observations.map(_.supplementOnlyIds.size).sum,
      duplicateBaselineIds = derived.map(_.duplicateBaselineIds).sum,
      lostBaselineIds = derived.map(_.lostBaselineIds).sum,
      prefixOrderRegressions = derived.count(_.prefixOrderRegression),
      baselineOwnedComponentChanges = derived.count(_.ownedComponentChanged),
      appendBudgetViolations = derived.map(_.appendBudgetViolations).sum,
    )
    val fixtures = observations.map(_.fixture)
    val fullSearchCode = BeautyQServingMode.FullSearch.modeCode
    val observedServing = servingMode.modeCode
    val (readinessCheck, readinessObserved, readinessExpected) =
      if (servingMode == BeautyQServingMode.FullSearch)
        (BeautyQCutoverCheck("full-search-readiness", passed = true, observedServing, fullSearchCode), observedServing, fullSearchCode)
      else
        (BeautyQCutoverCheck("full-search-readiness", passed = false, observedServing, fullSearchCode), observedServing, fullSearchCode)

    val checks = Vector(
      BeautyQCutoverCheck(
        "fixed-query-matrix",
        fixtures == requiredFixtures,
        fixtures.map(_.stableId).mkString(","),
        requiredFixtures.map(_.stableId).mkString(","),
      ),
      readinessCheck,
      BeautyQCutoverCheck("improvement-observed", metrics.improvedQueries > 0, metrics.improvedQueries.toString, "> 0"),
      BeautyQCutoverCheck("no-worsening", metrics.worsenedQueries == 0, metrics.worsenedQueries.toString, "0"),
      BeautyQCutoverCheck("baseline-preserved", metrics.lostBaselineIds == 0, metrics.lostBaselineIds.toString, "0 lost baseline IDs"),
      BeautyQCutoverCheck("baseline-order", metrics.prefixOrderRegressions == 0, metrics.prefixOrderRegressions.toString, "0 prefix order regressions"),
      BeautyQCutoverCheck("baseline-owned-components", metrics.baselineOwnedComponentChanges == 0, metrics.baselineOwnedComponentChanges.toString, "0 changes"),
      BeautyQCutoverCheck("append-budget", metrics.appendBudgetViolations == 0, metrics.appendBudgetViolations.toString, "0 violations"),
      BeautyQCutoverCheck("baseline-duplicates", metrics.duplicateBaselineIds == 0, metrics.duplicateBaselineIds.toString, "0 duplicates"),
    )
    val enriched = BeautyQCutoverGateResult.from(metrics, checks, readinessObserved, readinessExpected)
    enriched
  }

  private final case class Derived(
    improved: Boolean,
    worsened: Boolean,
    duplicateBaselineIds: Int,
    lostBaselineIds: Int,
    prefixOrderRegression: Boolean,
    ownedComponentChanged: Boolean,
    appendBudgetViolations: Int,
  )

  private def derive(observation: BeautyQCutoverQueryObservation): Derived = {
    val duplicateBaselineIds =
      (observation.resultIds.size - observation.resultIds.distinct.size) +
        observation.supplementOnlyIds.count(observation.baselineIds.contains)
    val lostBaselineIds = observation.baselineIds.distinct.count(id => !observation.resultIds.contains(id))
    val returnedBaselinePrefix = observation.resultIds.filter(observation.baselineIds.contains)
    val prefixOrderRegression = returnedBaselinePrefix != observation.baselineIds.distinct
    val ownedComponentChanged = !observation.baselineOwnedComponentsPreserved
    val appendBudgetViolations = if (observation.appendBudget >= 0 && observation.supplementOnlyIds.size <= observation.appendBudget) 0 else 1
    val worsened = duplicateBaselineIds > 0 || lostBaselineIds > 0 || prefixOrderRegression || ownedComponentChanged || appendBudgetViolations > 0
    Derived(
      improved = !worsened && observation.supplementOnlyIds.nonEmpty,
      worsened = worsened,
      duplicateBaselineIds = duplicateBaselineIds,
      lostBaselineIds = lostBaselineIds,
      prefixOrderRegression = prefixOrderRegression,
      ownedComponentChanged = ownedComponentChanged,
      appendBudgetViolations = appendBudgetViolations,
    )
  }
}
