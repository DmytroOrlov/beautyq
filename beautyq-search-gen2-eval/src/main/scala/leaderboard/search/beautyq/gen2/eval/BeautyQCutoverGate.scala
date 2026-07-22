package leaderboard.search.beautyq.gen2.eval

import io.circe.Json
import leaderboard.search.beautyq.gen2.contract.BeautySearchRequestGen2
import leaderboard.search.beautyq.gen2.wiring.{BeautyQServingMode, BeautyQSupplementPolicy, BeautyQSupplementReadinessPolicy}
import leaderboard.search.gen2.contract.{GeoPoint, PageRequest, PageSize}

/** The closed, source-confirmed query inventory used by the pre-cutover gate.
  * Query text and stable identity belong to the same typed fixture; the
  * append budget is derived from BeautyQ's executable supplement policy and
  * the executable native Gen2 request is derived from the same fixture so
  * the runner never repeats query strings, page sizes or filter sets. */
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

  /** One canonical evaluation request: the fixture's own query, no filters, no
    * facets, no explicit sort, the fixture-owned optional user location,
    * first page with no cursor and the canonical page size. The runner obtains
    * its request from this fixture rather than accepting separately supplied
    * query or location values. */
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
  /** Validated once at class load so neither the fixture nor the runner ever
    * rebuilds the literal `20` or reruns `PageSize.from(20)`. */
  val pageSize: PageSize = PageSize.from(20).getOrElse(throw new AssertionError("expected canonical cutover page size"))

  /** Explicit active business order; this is not enum inventory order. */
  val required: Vector[BeautyQCutoverQueryFixture] = Vector(
    BeautyQCutoverQueryFixture.QBroad006ReadyAppendProbe,
    BeautyQCutoverQueryFixture.ManicureRealRouteProbe,
    BeautyQCutoverQueryFixture.QBroad001WidenedProbe,
    BeautyQCutoverQueryFixture.QBroad003WidenedProbe,
  )
}

/** One immutable observation produced by an evaluation runner.  It contains
  * only facts observed for one fixed query; the gate derives all counts and
  * classifications from these facts. */
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

  /** The production evaluation path supplies this observation from one
    * compiler-owned result; no response IDs or plan fingerprint are rebuilt
    * by the cutover runner. */
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
  /** These are the only observations sufficient to make the pre-cutover
    * decision.  A different or incomplete query set cannot pass by accident. */
  val requiredFixtures: Vector[BeautyQCutoverQueryFixture] = BeautyQCutoverQueryFixture.required

  /** The authoritative gate entry point. Both readiness and observations are
    * required: there is no public overload that evaluates observations without
    * readiness, so the only evidence path emits a `full-search-readiness`
    * check derived from the same readiness the live application used. */
  def evaluate(
    readiness: BeautyQSupplementReadinessPolicy.Result,
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
    val (readinessCheck, readinessObserved, readinessExpected) = readinessCheckValues(readiness)
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

  private def readinessCheckValues(
    readiness: BeautyQSupplementReadinessPolicy.Result,
  ): (BeautyQCutoverCheck, String, String) = {
    val fullSearchCode = BeautyQServingMode.FullSearch.modeCode
    val (passed, observed): (Boolean, String) = readiness match {
      case serving: BeautyQSupplementReadinessPolicy.Serving =>
        if (serving.mode == BeautyQServingMode.FullSearch) (true, fullSearchCode)
        else (false, serving.mode.modeCode)
      case notServing: BeautyQSupplementReadinessPolicy.NotServing =>
        (false, s"not-serving:${notServing.unavailableRequired.map(_.stableId).sorted.mkString(",")}")
    }
    (BeautyQCutoverCheck("full-search-readiness", passed, observed, fullSearchCode), observed, fullSearchCode)
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
