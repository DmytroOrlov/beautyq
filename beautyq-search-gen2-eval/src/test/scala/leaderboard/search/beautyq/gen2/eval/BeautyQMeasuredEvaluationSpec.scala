package leaderboard.search.beautyq.gen2.eval

import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.beautyq.gen2.wiring.testkit.BeautyQOrchestrationTestKit
import leaderboard.search.gen2.eval.{EvaluationCaseId, EvaluationPartition, EvaluationResultId, RankingJudgments}
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
      assert(observed.scoreObservations.map(_.resultId) == response.hits.map(_.id))
      assert(observed.scoreObservations.map(_.score) == response.hits.map(_.score))
      assert(observed.scoreObservations.map(_.origin) == response.hits.map(_.origin.stableCode))
      assert(observed.scoreObservations.forall(_.query == current.query))
      assert(observed.scoreObservations.forall(_.supplementStatus == observed.signature.status))
      assert(observed.scoreObservations.forall(_.baselineIds == observed.signature.baselineIds))
      assert(observed.scoreObservations.forall(_.appendedIds == observed.signature.appendedIds))
    }

    "encode strict visible score evidence with protected redaction" in {
      val (corpus, current) = canonicalCase()
      val observed = measuredCase()
      val artifact = BeautyQScoreSeparationArtifact.encode(corpus, Vector(observed))
      assert(artifact.hcursor.get[String]("schemaVersion") == Right("beautyq-evaluation-score-separation-v1"))
      assert(artifact.hcursor.downField("protectedAggregate").focus.contains(io.circe.Json.Null))
      val visible = artifact.hcursor.downField("visibleCases").downArray
      assert(visible.get[String]("caseId") == Right(current.caseId.value))
      assert(visible.get[String]("query") == Right(current.query))
      val observations = visible.downField("observations").values.getOrElse(Vector.empty)
      assert(observations.nonEmpty)
      observations.foreach { value =>
        assert(value.hcursor.get[String]("resultId").isRight)
        assert(value.hcursor.get[String]("origin").isRight)
        assert(value.hcursor.get[String]("supplementStatus").isRight)
        assert(value.hcursor.get[Vector[String]]("baselineIds").isRight)
        assert(value.hcursor.get[Vector[String]]("appendedIds").isRight)
      }
      assert(artifact.hcursor.downField("supplementSeparation").get[Boolean]("separable").isRight)
    }

    "redact protected identities while retaining protected aggregate evidence" in {
      val (_, visibleSourceCase) = canonicalCase()
      val visibleContext = BeautyQOrchestrationTestKit.build(
        BeautyQOrchestrationTestKit.ineligible.request,
        baselineIds = Vector.empty,
        qdrantMode = BeautyQOrchestrationTestKit.QdrantMode.NoCall,
        membershipFailure = false,
        membershipIds = Vector.empty,
      )
      val visibleApplication = BeautyQSearchApplication.make(
        BeautyQOrchestrationTestKit.materialized,
        visibleContext.baselineService,
        visibleContext.embedding,
        visibleContext.qdrant,
      )
      val visibleResult = visibleApplication.execute(visibleContext.request) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected visible result, got $error")
      }
      val visibleResponse = BeautyQSearchResponseGen2Projector.projectWithoutStatus(visibleResult) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected visible response, got $error")
      }
      val visibleMeasured = BeautyQMeasuredCase.fromExecution(
        visibleSourceCase,
        visibleResult,
        visibleResponse,
        100L,
        "protected-redaction-visible",
      ) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected visible measured case, got $error")
      }

      val protectedContext = BeautyQOrchestrationTestKit.eligible(baselineIds = Vector.empty)
      val protectedApplication = BeautyQSearchApplication.make(
        BeautyQOrchestrationTestKit.materialized,
        protectedContext.baselineService,
        protectedContext.embedding,
        protectedContext.qdrant,
      )
      val protectedResult = protectedApplication.execute(protectedContext.request) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected protected result, got $error")
      }
      val protectedResponse = BeautyQSearchResponseGen2Projector.projectWithoutStatus(protectedResult) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected protected response, got $error")
      }
      val protectedHit = protectedResponse.hits match {
        case hit +: _ if hit.origin == BeautyQSearchHitOrigin.QdrantSupplement => hit
        case other => fail(s"expected one protected supplement hit, got ${other.size}")
      }
      val protectedResultId = EvaluationResultId.from(protectedHit.id) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected protected result identity, got $error")
      }
      val protectedJudgments = RankingJudgments.from(
        visibleSourceCase.variantJudgments.mode,
        acceptableIds = Vector.empty,
        forbiddenIds = Vector(protectedResultId),
        neutralIds = Vector.empty,
        gradedGains = Vector.empty,
      ) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected protected judgments, got $error")
      }
      val protectedCaseId = EvaluationCaseId.from("protected-redaction-sentinel-case") match {
        case Right(value) => value
        case Left(error)  => fail(s"expected protected case id, got $error")
      }
      val protectedCase = new CorpusCase(
        protectedCaseId,
        EvaluationPartition.ProtectedHoldout,
        visibleSourceCase.judgmentMode,
        "protected-redaction-sentinel-query",
        visibleSourceCase.language,
        visibleSourceCase.slices,
        visibleSourceCase.userIntent,
        Vector("protected note must not be emitted"),
        protectedJudgments,
        visibleSourceCase.providerJudgments,
        visibleSourceCase.serviceIntentJudgments,
      )
      val protectedMeasured = BeautyQMeasuredCase.fromExecution(
        protectedCase,
        protectedResult,
        protectedResponse,
        100L,
        "protected-redaction-protected",
      ) match {
        case Right(value) => value
        case Left(error)  => fail(s"expected protected measured case, got $error")
      }
      val syntheticCorpus = new BeautyQEvaluationCorpus(
        "beautyq-evaluation-corpus-v2",
        "protected-redaction-corpus",
        "test",
        2,
        BeautyQEvaluationCorpus.loadCanonical() match {
          case Right(value) => value.defaultUserLocation
          case Left(error)  => fail(s"expected canonical location, got $error")
        },
        Vector(visibleSourceCase, protectedCase),
        "protected-redaction-fingerprint",
      )
      val artifact = BeautyQScoreSeparationArtifact.encode(
        syntheticCorpus,
        Vector(visibleMeasured, protectedMeasured),
      )
      val visibleCases = artifact.hcursor.downField("visibleCases").values.getOrElse(Vector.empty)
      assert(visibleCases.exists(_.hcursor.get[String]("caseId") match {
        case Right(value) => value == visibleSourceCase.caseId.value
        case Left(_) => false
      }))
      assert(visibleCases.forall(_.hcursor.get[String]("caseId") match {
        case Right(value) => value != protectedCaseId.value
        case Left(_) => true
      }))
      val protectedAggregate = artifact.hcursor.downField("protectedAggregate").focus match {
        case Some(value) if !value.isNull => value
        case _ => fail("expected protected aggregate")
      }
      assert(protectedAggregate.hcursor.get[Int]("forbiddenCount") == Right(1))
      assert(protectedAggregate.hcursor.get[Int]("nonForbiddenCount") == Right(0))
      assert(protectedAggregate.hcursor.get[BigDecimal]("minimumForbidden") == Right(protectedHit.score))
      assert(protectedAggregate.hcursor.get[BigDecimal]("maximumForbidden") == Right(protectedHit.score))
      assert(protectedAggregate.hcursor.get[String]("caseId").isLeft)
      assert(protectedAggregate.hcursor.get[String]("query").isLeft)
      assert(protectedAggregate.hcursor.get[String]("resultId").isLeft)

      def strings(value: io.circe.Json): Vector[String] = value.fold(
        jsonNull = Vector.empty,
        jsonBoolean = _ => Vector.empty,
        jsonNumber = _ => Vector.empty,
        jsonString = text => Vector(text),
        jsonArray = values => values.toVector.flatMap(strings),
        jsonObject = values => values.values.toVector.flatMap(strings),
      )
      val encodedStrings = strings(protectedAggregate)
      assert(!encodedStrings.contains(protectedCaseId.value))
      assert(!encodedStrings.contains(protectedCase.query))
      assert(!encodedStrings.contains(protectedHit.id))
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

    "recursively exclude protected sentinel identities from all output forms" in {
      val result = syntheticProtectedResult()
      val allJson = Vector(
        "detailedJson" -> result.detailedJson,
        "protectedReportJson" -> result.protectedReportJson,
        "measurementJson" -> result.measurementJson,
        "scoreSeparationJson" -> result.scoreSeparationJson,
      )
      val sentinels = Vector(
        "synthetic-protected-sentinel-case",
        "synthetic-protected-sentinel-query",
        "synthetic-protected-sentinel-result",
        "synthetic-protected-sentinel-note",
      )
      allJson.foreach { case (name, json) =>
        val text = json.noSpaces
        sentinels.foreach(sentinel =>
          assert(!text.contains(sentinel), s"$name must not contain '$sentinel'")
        )
      }
    }

    "retain aggregate protected evidence in protected output" in {
      val result = syntheticProtectedResult()
      val struct = result.protectedReportJson.hcursor.downField("globalAggregates")
      assert(struct.get[Int]("structuralInvalidCount").isRight)
      assert(struct.get[Int]("duplicateIdentityCount").isRight)
      assert(struct.get[Int]("forbiddenHitCount").isRight)
      val observations = struct.downField("metricObservations").values
      assert(observations.nonEmpty)
      observations.foreach { values =>
        values.foreach { obs =>
          assert(obs.hcursor.get[String]("surface").isRight)
          assert(obs.hcursor.get[String]("metric").isRight)
          assert(obs.hcursor.get[Int]("cutoff").isRight)
          assert(obs.hcursor.get[BigDecimal]("average").isRight)
        }
      }
      val cases = result.protectedReportJson.hcursor.downField("caseResults").values
      cases.foreach { values =>
        values.foreach { cr =>
          assert(cr.hcursor.get[String]("caseId").isRight)
        }
      }
    }

    "prove protectedReportDigest equals digest of protectedReportJson" in {
      val result = syntheticProtectedResult()
      val expectedDigest = leaderboard.search.gen2.eval.EvaluationReportDigest.compute(result.protectedReportJson)
      assert(result.protectedReportDigest == expectedDigest)
    }

    "prove digest is deterministic" in {
      val result1 = syntheticProtectedResult()
      val result2 = syntheticProtectedResult()
      assert(result1.protectedReportDigest == result2.protectedReportDigest)
      assert(result1.reportDigest == result2.reportDigest)
    }

    "retain the supplied protected evaluation-policy version" in {
      val result = syntheticProtectedResult()
      assert(result.evaluationPolicyVersion == BeautyQEvaluationPolicy.CurrentVersion)
    }
  }

  private def canonicalCase(): (BeautyQEvaluationCorpus, CorpusCase) = {
    val corpus = BeautyQEvaluationCorpus.loadCanonical() match {
      case Right(value) => value
      case Left(error)  => fail(s"expected canonical corpus, got $error")
    }
    corpus.cases match {
      case current +: _ =>
        assert(corpus.cases.size == 108)
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

  private def syntheticProtectedResult(): BeautyQMeasuredEvaluationResult = {
    val caseId = leaderboard.search.gen2.eval.EvaluationCaseId.from("synthetic-protected-sentinel-case") match {
      case Right(value) => value
      case Left(error) => fail(error.toString)
    }
    val resultId = leaderboard.search.gen2.eval.EvaluationResultId.from("synthetic-protected-sentinel-result") match {
      case Right(value) => value
      case Left(error) => fail(error.toString)
    }
    val surface = BeautyQEvaluationPolicy.Variants
    val slice = leaderboard.search.gen2.eval.EvaluationSliceId.from("smoke") match {
      case Right(value) => value
      case Left(error) => fail(error.toString)
    }
    val judgments = leaderboard.search.gen2.eval.RankingJudgments.from(
      leaderboard.search.gen2.eval.JudgmentMode.Partial,
      Vector(resultId), Vector.empty, Vector.empty, Vector.empty,
    ) match {
      case Right(value) => value
      case Left(error) => fail(error.toString)
    }
    val ranking = leaderboard.search.gen2.eval.RankingEvaluationInput.from(
      caseId, leaderboard.search.gen2.eval.EvaluationPartition.ProtectedHoldout, surface,
      Vector(slice), judgments, Vector(resultId), BeautyQEvaluationPolicy.cutoffs,
    ) match {
      case Right(value) => leaderboard.search.gen2.eval.RankingEvaluator.evaluate(value)
      case Left(error) => fail(error.toString)
    }
    val provenance = Vector(
      "corpus-fingerprint" -> ("f" * 64),
      "evaluation-policy-version" -> BeautyQEvaluationPolicy.CurrentVersion,
      "metric-schema-version" -> leaderboard.search.gen2.eval.RankingEvaluator.MetricSchemaVersion,
    ).foldLeft[Vector[leaderboard.search.gen2.eval.ProvenanceComponent]](Vector.empty) { case (done, (idText, value)) =>
      val id = leaderboard.search.gen2.eval.EvaluationProvenanceId.from(idText) match {
        case Right(actual) => actual
        case Left(error) => fail(error.toString)
      }
      leaderboard.search.gen2.eval.ProvenanceComponent.from(id, value) match {
        case Right(actual) => done :+ actual
        case Left(error) => fail(error.toString)
      }
    }
    val reportInput = leaderboard.search.gen2.eval.EvaluationReportCaseInput.from(
      caseId, leaderboard.search.gen2.eval.EvaluationPartition.ProtectedHoldout,
      leaderboard.search.gen2.eval.JudgmentMode.Partial,
      Some(Vector(slice)), Vector(surface -> ranking),
    ) match {
      case Right(value) => value
      case Left(error) => fail(error.toString)
    }
    val report = leaderboard.search.gen2.eval.EvaluationReportBuilder.build(provenance, Vector(reportInput))
    val scoreSep = BeautyQSupplementScoreSeparation.from(Vector.empty)
    val checks = Vector(
      "required-full-search", "complete-warmup", "complete-measured-passes",
      "deterministic-measured-rankings", "no-request-degradation",
      "no-public-identity-duplicates", "no-forbidden-hits",
      "baseline-prefix-preserved", "baseline-owned-components-preserved",
      "append-budget-preserved",
    ).map(code => BeautyQEvaluationCorrectionCheck.create(code, true, "true", "true"))
    BeautyQMeasuredEvaluationResult.create(
      report,
      leaderboard.search.gen2.eval.EvaluationReport.encodeDetailed(report),
      leaderboard.search.gen2.eval.EvaluationReport.encodeProtected(report),
      leaderboard.search.gen2.eval.EvaluationReportDigest.compute(
        leaderboard.search.gen2.eval.EvaluationReport.encodeProtected(report),
      ),
      leaderboard.search.gen2.eval.EvaluationReportDigest.compute(
        leaderboard.search.gen2.eval.EvaluationReport.encodeDetailed(report),
      ),
      io.circe.Json.obj("test" -> io.circe.Json.fromString("measurement")),
      io.circe.Json.obj("test" -> io.circe.Json.fromString("score-separation")),
      BeautyQEvaluationCorrectionGateResult.create(checks, 1, 1, 3, 0, 0, 0, 0, 0, 0, scoreSep),
      1, 3, BeautyQEvaluationPolicy.CurrentVersion,
    )
  }
}
