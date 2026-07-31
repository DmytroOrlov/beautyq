package leaderboard.search.beautyq.gen2.eval

import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.beautyq.gen2.wiring.testkit.BeautyQOrchestrationTestKit
import leaderboard.search.gen2.eval.EvaluationPartition
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQMeasuredEvaluationSpec extends AnyWordSpec {
  "BeautyQ measured evaluation" should {
    "build the canonical request from the corpus owner" in {
      val (corpus, current) = canonicalCase()
      val request = BeautyQEvaluationRequestFactory.fromCase(corpus, current) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected canonical request, got $error")
      }

      assert(request.query.contains(current.query))
      assert(request.filters.isEmpty)
      assert(request.requestedFacets.isEmpty)
      assert(request.sort.isEmpty)
      assert(request.page.cursor.isEmpty)
      assert(request.page.size.value == 20)
      request.userLocation match {
        case Some(location) =>
          assert(location.lat == BigDecimal(corpus.defaultUserLocation.lat.toString))
          assert(location.lon == BigDecimal(corpus.defaultUserLocation.lon.toString))
        case None => fail("expected canonical default location")
      }
    }

    "adapt projector-owned identities in the declared surface order" in {
      val (_, current) = canonicalCase()
      val context = BeautyQOrchestrationTestKit.eligible()
      val application = BeautyQSearchApplication.make(
        BeautyQOrchestrationTestKit.materialized,
        context.baselineService,
        context.embedding,
        context.qdrant,
      )
      val result = application.execute(context.request) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected application result, got $error")
      }
      val response = BeautyQSearchResponseGen2Projector.projectWithoutStatus(result) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected response, got $error")
      }
      val observed = BeautyQMeasuredCase.fromExecution(current, result, response, 100L, "measured[1]") match {
        case Right(value) => value
        case Left(error)  => fail(s"expected measured case, got $error")
      }

      assert(observed.reportInput.orderedSurfaceResults.map(_._1) == BeautyQEvaluationPolicy.activeSurfaces)
      assert(observed.signature.variants == response.hits.map(_.id))
      assert(observed.signature.providers == response.providerCarousel.map(_.masterLocationId))
      assert(observed.signature.serviceIntents == response.serviceIntentCarousel.map(_.serviceId))
      assert(observed.reportInput.caseId == current.caseId)
      assert(observed.reportInput.partition == current.partition)
      assert(observed.reportInput.slices.contains(current.slices))
    }

    "calculate exact nearest-rank latency summaries" in {
      val odd = BeautyQLatencySummary.from(Vector(50L, 10L, 30L, 20L, 40L)) match {
        case Right(value) => value
        case Left(error)  => fail(error)
      }
      assert(odd.sampleCount == 5)
      assert(odd.minimum == 10L)
      assert(odd.p50 == 30L)
      assert(odd.p95 == 50L)
      assert(odd.maximum == 50L)

      val even = BeautyQLatencySummary.from(Vector(40L, 10L, 30L, 20L)) match {
        case Right(value) => value
        case Left(error)  => fail(error)
      }
      assert(even.p50 == 20L)
      assert(even.p95 == 40L)

      val three = BeautyQLatencySummary.from(Vector(3L, 1L, 2L)) match {
        case Right(value) => value
        case Left(error)  => fail(error)
      }
      assert(three.sampleCount == 3)
      assert(three.p50 == 2L)
      assert(three.p95 == 3L)
    }

    "reject a changed measured ranking deterministically" in {
      val observed = measuredCase()
      val changedSignature = observed.signature.copy(variants = observed.signature.variants :+ "changed-id")
      val changed = BeautyQMeasuredCase.syntheticForGate(observed, signature = Some(changedSignature))
      BeautyQMeasuredEvaluation.validateMeasuredDeterminism(
        Vector(observed),
        Vector(Vector(observed), Vector(changed), Vector(observed)),
      ) match {
        case Left(BeautyQEvaluationExecutionError.NonDeterministicObservation(
              caseId, _, "variants", 1, 2, expected, actual,
            )) =>
          assert(caseId == observed.corpusCase.caseId.value)
          assert(expected == observed.signature.variants)
          assert(actual == changedSignature.variants)
        case other => fail(s"expected typed nondeterminism, got $other")
      }
    }

    "keep derived construction boundaries closed" in {
      assertDoesNotCompile(
        """new leaderboard.search.beautyq.gen2.eval.BeautyQLatencySummary(1, 1L, 1L, 1L, 1L)"""
      )
    }
  }

  private def canonicalCase(): (BeautyQEvaluationCorpus, CorpusCase) = {
    val corpus = BeautyQEvaluationCorpus.loadCanonical() match {
      case Right(value) => value
      case Left(error)  => fail(s"expected canonical corpus, got $error")
    }
    corpus.cases match {
      case current +: _ =>
        assert(corpus.cases.size == 89)
        assert(corpus.cases.forall(_.partition == EvaluationPartition.Regression))
        (corpus, current)
      case _ => fail("expected non-empty corpus")
    }
  }

  private def measuredCase(): BeautyQMeasuredCase = {
    val (_, current) = canonicalCase()
    val context = BeautyQOrchestrationTestKit.eligible()
    val application = BeautyQSearchApplication.make(
      BeautyQOrchestrationTestKit.materialized,
      context.baselineService,
      context.embedding,
      context.qdrant,
    )
    val result = application.execute(context.request) match {
      case Right(value) => value
      case Left(error)  => fail(s"expected application result, got $error")
    }
    val response = BeautyQSearchResponseGen2Projector.projectWithoutStatus(result) match {
      case Right(value) => value
      case Left(error)  => fail(s"expected response, got $error")
    }
    BeautyQMeasuredCase.fromExecution(current, result, response, 100L, "measured[1]") match {
      case Right(value) => value
      case Left(error)  => fail(s"expected measured case, got $error")
    }
  }
}
