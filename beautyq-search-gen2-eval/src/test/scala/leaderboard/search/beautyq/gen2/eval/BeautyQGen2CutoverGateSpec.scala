package leaderboard.search.beautyq.gen2.eval

import leaderboard.search.beautyq.gen2.wiring.{BeautyQServingMode, BeautyQSupplementPolicy}
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQGen2CutoverGateSpec extends AnyWordSpec {

  "BeautyQCutoverGate" should {
    "accept the four fixed observations when supplementing improves one query without harm" in {
      val result = BeautyQCutoverGate.evaluate(BeautyQServingMode.FullSearch, healthy)

      assert(result.passed)
      assert(result.metrics == BeautyQCutoverMetrics(4, 1, 3, 0, 1, 0, 0, 0, 0, 0))
      assert(result.readinessObserved == BeautyQServingMode.FullSearch.modeCode)
      assert(result.readinessExpected == BeautyQServingMode.FullSearch.modeCode)
      assert(result.checks.exists(check => check.id == "full-search-readiness" && check.passed))
      assert(BeautyQCutoverQueryFixture.required == Vector(
        BeautyQCutoverQueryFixture.QBroad006ReadyAppendProbe,
        BeautyQCutoverQueryFixture.ManicureRealRouteProbe,
        BeautyQCutoverQueryFixture.QBroad001WidenedProbe,
        BeautyQCutoverQueryFixture.QBroad003WidenedProbe,
      ))
      assert(BeautyQCutoverQueryFixture.required.map(value => value.stableId -> value.query) == Vector(
        "q_broad_006_ready_append_probe" -> "beauty near Wandsbek Markt",
        "manicure_real_route_probe" -> "маникюр",
        "q_broad_001_widened_probe" -> "салон красоты wandsbek ногти",
        "q_broad_003_widened_probe" -> "что-то для лица рядом",
      ))
      assert(BeautyQCutoverQueryFixture.required.forall(_.appendBudget == BeautyQSupplementPolicy.appendOnly.maxAppended))
    }

    "expose the canonical evaluation request through the fixture" in {
      val fixture = BeautyQCutoverQueryFixture.QBroad006ReadyAppendProbe
      val request = fixture.request
      assert(request.query.contains(fixture.query))
      assert(request.filters.isEmpty)
      assert(request.requestedFacets.isEmpty)
      assert(request.sort.isEmpty)
      assert(request.page.cursor.isEmpty)
      assert(request.page.size == BeautyQCutoverQueryFixture.pageSize)
      assert(request.userLocation.isEmpty)

      val nearUserRequest = BeautyQCutoverQueryFixture.QBroad003WidenedProbe.request
      assert(nearUserRequest.userLocation.nonEmpty)
    }

    "require a single canonical page size, not enum.values" in {
      assert(BeautyQCutoverQueryFixture.pageSize.value == 20)
      assert(BeautyQCutoverQueryFixture.required.length == 4)
    }

    "keep the observation factory bound to a typed fixture" in {
      assertDoesNotCompile(
        """{
          |  val evidence: BeautyQNoHarmSupplementEvidence.SupplementEvidence = ???
          |  BeautyQCutoverQueryObservation.fromEvidence("raw-id", evidence, 1)
          |}""".stripMargin
      )
    }

    "fail closed when serving mode is BaselineOnly" in {
      val result = BeautyQCutoverGate.evaluate(BeautyQServingMode.BaselineOnly, healthy)
      assert(!result.passed)
      val check = result.checks.find(_.id == "full-search-readiness").getOrElse(fail("expected full-search-readiness check"))
      assert(!check.passed)
      assert(check.observed == BeautyQServingMode.BaselineOnly.modeCode)
    }

    "reject the matrix when no query improves" in {
      val result = BeautyQCutoverGate.evaluate(BeautyQServingMode.FullSearch, healthy.map {
        case observation if observation.fixture == BeautyQCutoverQueryFixture.QBroad001WidenedProbe => observation.syntheticCopy(resultIds = observation.baselineIds, supplementOnlyIds = Vector.empty)
        case observation => observation
      })

      assert(!result.passed)
      assert(result.checks.exists(check => check.id == "improvement-observed" && !check.passed))
    }

    "reject lost baseline IDs, order changes, owned-component changes and budget violations" in {
      val lost = healthy.map {
        case observation if observation.fixture == BeautyQCutoverQueryFixture.QBroad001WidenedProbe => observation.syntheticCopy(resultIds = Vector("supplement-only"), supplementOnlyIds = Vector("supplement-only"))
        case observation => observation
      }
      val lostResult = BeautyQCutoverGate.evaluate(BeautyQServingMode.FullSearch, lost)
      assert(lostResult.metrics.lostBaselineIds == 1)
      assert(!lostResult.passed)

      val reordered = healthy.map {
        case observation if observation.fixture == BeautyQCutoverQueryFixture.ManicureRealRouteProbe => observation.syntheticCopy(baselineIds = Vector("a", "b"), resultIds = Vector("b", "a"))
        case observation => observation
      }
      val reorderedResult = BeautyQCutoverGate.evaluate(BeautyQServingMode.FullSearch, reordered)
      assert(reorderedResult.metrics.prefixOrderRegressions == 1)
      assert(!reorderedResult.passed)

      val ownedChanged = healthy.map {
        case observation if observation.fixture == BeautyQCutoverQueryFixture.QBroad003WidenedProbe => observation.syntheticCopy(baselineOwnedComponentsPreserved = false)
        case observation => observation
      }
      val ownedResult = BeautyQCutoverGate.evaluate(BeautyQServingMode.FullSearch, ownedChanged)
      assert(ownedResult.metrics.baselineOwnedComponentChanges == 1)
      assert(!ownedResult.passed)

      val budgetExceeded = healthy.map {
        case observation if observation.fixture == BeautyQCutoverQueryFixture.QBroad001WidenedProbe => observation.syntheticCopy(resultIds = Vector("c", "s1", "s2"), supplementOnlyIds = Vector("s1", "s2"))
        case observation => observation
      }
      val budgetResult = BeautyQCutoverGate.evaluate(BeautyQServingMode.FullSearch, budgetExceeded)
      assert(budgetResult.metrics.appendBudgetViolations == 1)
      assert(!budgetResult.passed)

      val duplicate = healthy.map {
        case observation if observation.fixture == BeautyQCutoverQueryFixture.QBroad001WidenedProbe => observation.syntheticCopy(resultIds = Vector("c", "c", "s"), supplementOnlyIds = Vector("s"))
        case observation => observation
      }
      val duplicateResult = BeautyQCutoverGate.evaluate(BeautyQServingMode.FullSearch, duplicate)
      assert(duplicateResult.metrics.duplicateBaselineIds == 1)
      assert(!duplicateResult.passed)
    }

    "reject an incomplete or reordered fixed query matrix" in {
      val missing = BeautyQCutoverGate.evaluate(BeautyQServingMode.FullSearch, healthy.dropRight(1))
      assert(!missing.passed)
      assert(missing.metrics.testedQueries == 3)

      val reordered = BeautyQCutoverGate.evaluate(BeautyQServingMode.FullSearch, healthy.reverse)
      assert(!reordered.passed)
      assert(reordered.checks.exists(check => check.id == "fixed-query-matrix" && !check.passed))
    }

    "reject a duplicated typed fixture" in {
      val duplicated = Vector(
        healthyObservation(BeautyQCutoverQueryFixture.QBroad006ReadyAppendProbe),
        healthyObservation(BeautyQCutoverQueryFixture.ManicureRealRouteProbe),
        healthyObservation(BeautyQCutoverQueryFixture.ManicureRealRouteProbe),
        healthyObservation(BeautyQCutoverQueryFixture.QBroad003WidenedProbe),
      )
      val result = BeautyQCutoverGate.evaluate(BeautyQServingMode.FullSearch, duplicated)
      assert(!result.passed)
      assert(result.checks.exists(check => check.id == "fixed-query-matrix" && !check.passed))
    }

    "expose readiness observation through the JSON encoder" in {
      val passed = BeautyQCutoverGate.evaluate(BeautyQServingMode.FullSearch, healthy)
      val json = passed.toJson
      val readinessJson = json.hcursor.downField("readiness")
      assert(readinessJson.get[String]("observed").toOption.contains(BeautyQServingMode.FullSearch.modeCode))
      assert(readinessJson.get[String]("expected").toOption.contains(BeautyQServingMode.FullSearch.modeCode))
      json.hcursor.downField("checks").values match {
        case Some(checks) =>
          val matched = checks.filter(v => v.hcursor.get[String]("id") == Right("full-search-readiness"))
          assert(matched.size == 1, s"expected exactly one full-search-readiness check, got ${matched.size}")
          val check = matched.head
          assert(check.hcursor.get[Boolean]("passed") == Right(true))
          assert(check.hcursor.get[String]("observed") == Right(BeautyQServingMode.FullSearch.modeCode))
          assert(check.hcursor.get[String]("expected") == Right(BeautyQServingMode.FullSearch.modeCode))
        case None => fail("expected checks array")
      }
    }
  }

  private val healthy: Vector[BeautyQCutoverQueryObservation] = Vector(
    BeautyQCutoverQueryObservation.synthetic(BeautyQCutoverQueryFixture.QBroad006ReadyAppendProbe, Vector("a"), Vector("a"), Vector.empty, true),
    BeautyQCutoverQueryObservation.synthetic(BeautyQCutoverQueryFixture.ManicureRealRouteProbe, Vector("b"), Vector("b"), Vector.empty, true),
    BeautyQCutoverQueryObservation.synthetic(BeautyQCutoverQueryFixture.QBroad001WidenedProbe, Vector("c"), Vector("c", "s"), Vector("s"), true),
    BeautyQCutoverQueryObservation.synthetic(BeautyQCutoverQueryFixture.QBroad003WidenedProbe, Vector("d"), Vector("d"), Vector.empty, true),
  )

  private def healthyObservation(fixture: BeautyQCutoverQueryFixture): BeautyQCutoverQueryObservation =
    healthy.find(_.fixture == fixture).getOrElse(fail(s"missing healthy fixture ${fixture.stableId}"))
}
