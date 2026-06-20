package leaderboard.search

import leaderboard.search.eval.{
  CandidateSource,
  CatalogSnapshotId,
  EvalDatasetId,
  ExperimentId,
  M9OfflineEvalBackendExecutionMode,
  M9OfflineEvalBackendExecutionPlan,
  M9OfflineEvalBackendRunRequest,
  M9OfflineEvalDataset,
  M9OfflineEvalDatasetQuery,
  M9OfflineEvalExpectedResult,
  M9OfflineEvalRealBackendKind,
  M9OfflineEvalRealBackendRequestIdPolicy,
  M9OfflineEvalRealBackendResourceConfig,
  M9OfflineEvalRealBackendResourceGate,
  M9OfflineEvalRealBackendResourceGateStatus,
  M9OfflineEvalRealBackendSpikeAdapter,
  M9OfflineEvalRealBackendSpikeArtifactCapture,
  M9OfflineEvalRealBackendSpikeArtifactInput,
  M9OfflineEvalRealBackendSpikeInput,
  M9OfflineEvalReportRenderer,
  MetricWindow,
  OfflineEvalMetricName,
  QueryClass,
  RequestId,
  RoutingPolicyId,
  ServingMode,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M9OfflineEvalRealBackendSpikeArtifactCaptureSpec extends AnyWordSpec {

  "M9OfflineEvalRealBackendSpikeArtifactCapture" should {

    "render a default-disabled artifact without real backend calls" in {
      val spikeResult = runSpike(
        input = esInput(resourceConfig = None),
        gate = M9OfflineEvalRealBackendResourceGate.DefaultDisabled,
      )
      val artifact = M9OfflineEvalRealBackendSpikeArtifactCapture.captureDefaultDisabled(spikeResult)

      artifact match {
        case Right(value) =>
          assert(value.summary.gateDecision.status == M9OfflineEvalRealBackendResourceGateStatus.Disabled)
          assert(!value.summary.realBackendCallImplemented)
          assert(value.summary.offlineEvalEvidenceOnly)
          assert(value.summary.notProductionTelemetry)
          assert(value.summary.notActivationApproval)
          assert(value.savedReport.rows.forall(_.regressionStatus == "failed"))
          assert(value.savedReport.rows.flatMap(_.warnings).exists(_.contains("disabled")))
          assert(value.savedReport.warnings.exists(_.contains("artifact_capture_gate_status=disabled")))
          assert(value.markdownArtifact.filename == M9OfflineEvalRealBackendSpikeArtifactCapture.DefaultDisabledFilename)
        case Left(error) =>
          fail(s"expected default-disabled artifact, got $error")
      }
    }

    "render a not-configured artifact when enabled without resource config" in {
      val spikeResult = runSpike(
        input = esInput(resourceConfig = None),
        gate = M9OfflineEvalRealBackendResourceGate(runRealBackendOfflineEval = true),
      )
      val artifact = M9OfflineEvalRealBackendSpikeArtifactCapture.captureNotConfigured(spikeResult)

      artifact match {
        case Right(value) =>
          assert(value.summary.gateDecision.status == M9OfflineEvalRealBackendResourceGateStatus.AllowedWithoutResourceConfig)
          assert(!value.summary.gateDecision.permitsRealBackendCall)
          assert(!value.summary.resourceConfigPresent)
          assert(!value.summary.realBackendCallImplemented)
          assert(value.savedReport.rows.forall(_.regressionStatus == "failed"))
          assert(value.savedReport.rows.flatMap(_.warnings).exists(_.contains("not configured")))
          assert(value.savedReport.warnings.exists(_.contains("artifact_capture_resource_config_present=false")))
          assert(value.markdownArtifact.filename == M9OfflineEvalRealBackendSpikeArtifactCapture.NotConfiguredFilename)
        case Left(error) =>
          fail(s"expected not-configured artifact, got $error")
      }
    }

    "render a configured artifact when resource config is present" in {
      val spikeResult = runSpike(
        input = esInput(resourceConfig = Some(resourceConfig("es-resource"))),
        gate = M9OfflineEvalRealBackendResourceGate(runRealBackendOfflineEval = true),
      )
      val artifact = M9OfflineEvalRealBackendSpikeArtifactCapture.captureConfigured(spikeResult)

      artifact match {
        case Right(value) =>
          assert(value.summary.gateDecision.status == M9OfflineEvalRealBackendResourceGateStatus.AllowedForConfiguredResource)
          assert(value.summary.gateDecision.permitsRealBackendCall)
          assert(value.summary.resourceConfigPresent)
          assert(!value.summary.realBackendCallImplemented)
          assert(value.savedReport.rows.forall(_.regressionStatus == "failed"))
          assert(value.savedReport.rows.flatMap(_.warnings).exists(_.contains("configured")))
          assert(value.savedReport.warnings.exists(_.contains("artifact_capture_resource_config_present=true")))
          assert(value.markdownArtifact.filename == M9OfflineEvalRealBackendSpikeArtifactCapture.ConfiguredFilename)
        case Left(error) =>
          fail(s"expected configured artifact, got $error")
      }
    }

    "record gate decision in summary and warnings" in {
      val disabledResult = runSpike(
        input = esInput(resourceConfig = None),
        gate = M9OfflineEvalRealBackendResourceGate.DefaultDisabled,
      )
      val enabledNoConfigResult = runSpike(
        input = esInput(resourceConfig = None),
        gate = M9OfflineEvalRealBackendResourceGate(runRealBackendOfflineEval = true),
      )

      M9OfflineEvalRealBackendSpikeArtifactCapture.captureDefaultDisabled(disabledResult) match {
        case Right(value) =>
          assert(value.summary.gateDecision.status == M9OfflineEvalRealBackendResourceGateStatus.Disabled)
          assert(value.summary.gateDecision.reasons.exists(_.contains("RUN_REAL_BACKEND_OFFLINE_EVAL=1")))
          assert(value.savedReport.warnings.exists(_.contains("gate_status=disabled")))
        case Left(error) =>
          fail(s"expected disabled artifact summary, got $error")
      }

      M9OfflineEvalRealBackendSpikeArtifactCapture.captureNotConfigured(enabledNoConfigResult) match {
        case Right(value) =>
          assert(value.summary.gateDecision.status == M9OfflineEvalRealBackendResourceGateStatus.AllowedWithoutResourceConfig)
          assert(value.savedReport.warnings.exists(_.contains("gate_status=allowed_without_resource_config")))
        case Left(error) =>
          fail(s"expected not-configured artifact summary, got $error")
      }
    }

    "record resource config presence and absence" in {
      val noConfigResult = runSpike(
        input = esInput(resourceConfig = None),
        gate = M9OfflineEvalRealBackendResourceGate(runRealBackendOfflineEval = true),
      )
      val withConfigResult = runSpike(
        input = esInput(resourceConfig = Some(resourceConfig("es-resource"))),
        gate = M9OfflineEvalRealBackendResourceGate(runRealBackendOfflineEval = true),
      )

      M9OfflineEvalRealBackendSpikeArtifactCapture.captureNotConfigured(noConfigResult) match {
        case Right(value) =>
          assert(!value.summary.resourceConfigPresent)
          assert(value.savedReport.warnings.exists(_.contains("resource_config_present=false")))
        case Left(error) =>
          fail(s"expected no-config artifact, got $error")
      }

      M9OfflineEvalRealBackendSpikeArtifactCapture.captureConfigured(withConfigResult) match {
        case Right(value) =>
          assert(value.summary.resourceConfigPresent)
          assert(value.savedReport.warnings.exists(_.contains("resource_config_present=true")))
        case Left(error) =>
          fail(s"expected with-config artifact, got $error")
      }
    }

    "record production non-approval in summary, notes, and warnings" in {
      val spikeResult = runSpike(
        input = esInput(resourceConfig = None),
        gate = M9OfflineEvalRealBackendResourceGate.DefaultDisabled,
      )
      val artifact = M9OfflineEvalRealBackendSpikeArtifactCapture.captureDefaultDisabled(spikeResult)

      artifact match {
        case Right(value) =>
          assert(value.summary.productionActivationNotApprovedConfirmed)
          assert(value.summary.notActivationApproval)
          assert(value.summary.notProductionTelemetry)
          assert(value.summary.offlineEvalEvidenceOnly)
          assert(value.savedReport.notes.exists(_.contains("not production telemetry")))
          assert(value.savedReport.notes.exists(_.contains("not production activation approval")))
          assert(value.savedReport.warnings.exists(_.contains("production_activation_not_approved_confirmed=true")))
          assert(value.markdownArtifact.contents.contains("Offline eval evidence only; not production telemetry; not production activation approval."))
          assert(value.markdownArtifact.contents.contains("production_activation_not_approved"))
        case Left(error) =>
          fail(s"expected production non-approval recorded, got $error")
      }
    }

    "preserve ES source and mode attribution" in {
      val spikeResult = runSpike(
        input = esInput(resourceConfig = None),
        gate = M9OfflineEvalRealBackendResourceGate.DefaultDisabled,
      )
      val artifact = M9OfflineEvalRealBackendSpikeArtifactCapture.captureDefaultDisabled(spikeResult)

      artifact match {
        case Right(value) =>
          assert(value.summary.backendKind == Some(M9OfflineEvalRealBackendKind.Es))
          assert(value.summary.executionMode == M9OfflineEvalBackendExecutionMode.EsOnlyOffline)
          assert(value.summary.candidateSource == CandidateSource.Es)
          assert(value.savedReport.metadata.servingMode == ServingMode.EsOnly)
          assert(value.savedReport.metadata.candidateSource == CandidateSource.Es)
          assert(value.savedReport.rows.forall(_.servingMode == ServingMode.EsOnly))
          assert(value.savedReport.rows.forall(_.candidateSource == CandidateSource.Es))
        case Left(error) =>
          fail(s"expected ES attribution preserved, got $error")
      }
    }

    "preserve Qdrant source and mode attribution" in {
      val spikeResult = runSpike(
        input = qdrantInput(resourceConfig = None),
        gate = M9OfflineEvalRealBackendResourceGate.DefaultDisabled,
      )
      val artifact = M9OfflineEvalRealBackendSpikeArtifactCapture.captureDefaultDisabled(spikeResult)

      artifact match {
        case Right(value) =>
          assert(value.summary.backendKind == Some(M9OfflineEvalRealBackendKind.Qdrant))
          assert(value.summary.executionMode == M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline)
          assert(value.summary.candidateSource == CandidateSource.Qdrant)
          assert(value.savedReport.metadata.servingMode == ServingMode.QdrantOnly)
          assert(value.savedReport.metadata.candidateSource == CandidateSource.Qdrant)
          assert(value.savedReport.rows.forall(_.servingMode == ServingMode.QdrantOnly))
          assert(value.savedReport.rows.forall(_.candidateSource == CandidateSource.Qdrant))
        case Left(error) =>
          fail(s"expected Qdrant attribution preserved, got $error")
      }
    }

    "represent warnings and failures as data, not exceptions" in {
      val spikeResult = runSpike(
        input = esInput(resourceConfig = None),
        gate = M9OfflineEvalRealBackendResourceGate.DefaultDisabled,
      )
      val artifact = M9OfflineEvalRealBackendSpikeArtifactCapture.captureDefaultDisabled(spikeResult)

      artifact match {
        case Right(value) =>
          assert(value.savedReport.rows.forall(_.regressionStatus == "failed"))
          assert(value.savedReport.rows.flatMap(_.metrics).exists(_.name == OfflineEvalMetricName.FailureCount))
          assert(value.savedReport.rows.flatMap(_.warnings).nonEmpty)
          assert(value.savedReport.warnings.nonEmpty)
          assert(value.savedReport.aggregateMetrics.exists(_.name == OfflineEvalMetricName.QualityGateDecision))
          assert(value.savedReport.aggregateMetrics.exists(_.value.render == "not_evaluated_not_for_activation"))
        case Left(error) =>
          fail(s"expected failure data, got $error")
      }
    }

    "produce a byte-for-byte stable artifact for default-disabled ES spike" in {
      val spikeResult = runSpike(
        input = esInput(resourceConfig = None),
        gate = M9OfflineEvalRealBackendResourceGate.DefaultDisabled,
      )
      val artifact = M9OfflineEvalRealBackendSpikeArtifactCapture.captureDefaultDisabled(spikeResult)

      artifact match {
        case Right(value) =>
          val rendered = value.markdownArtifact.contents
          val renderedAgain = M9OfflineEvalReportRenderer.renderMarkdown(value.savedReport)

          assert(rendered == renderedAgain)
          assert(rendered == checkedInDefaultDisabledArtifact)
        case Left(error) =>
          fail(s"expected stable artifact, got $error")
      }
    }

    "produce a byte-for-byte stable artifact for not-configured ES spike" in {
      val spikeResult = runSpike(
        input = esInput(resourceConfig = None),
        gate = M9OfflineEvalRealBackendResourceGate(runRealBackendOfflineEval = true),
      )
      val artifact = M9OfflineEvalRealBackendSpikeArtifactCapture.captureNotConfigured(spikeResult)

      artifact match {
        case Right(value) =>
          val rendered = value.markdownArtifact.contents
          val renderedAgain = M9OfflineEvalReportRenderer.renderMarkdown(value.savedReport)

          assert(rendered == renderedAgain)
          assert(rendered == checkedInNotConfiguredArtifact)
        case Left(error) =>
          fail(s"expected stable artifact, got $error")
      }
    }

    "feed the existing static runner output path" in {
      val spikeResult = runSpike(
        input = esInput(resourceConfig = None),
        gate = M9OfflineEvalRealBackendResourceGate.DefaultDisabled,
      )
      val artifact = M9OfflineEvalRealBackendSpikeArtifactCapture.captureDefaultDisabled(spikeResult)

      artifact match {
        case Right(value) =>
          val staticResult = value.staticRunResult
          assert(staticResult.report == value.savedReport)
          assert(staticResult.markdownArtifact == value.markdownArtifact)
          assert(staticResult.qualityGateSummary.totalRows == value.savedReport.rows.size)
          assert(staticResult.qualityGateSummary.failedRows == value.savedReport.rows.size)
          assert(staticResult.qualityGateSummary.qualityGateDecision == "not_evaluated_not_for_activation")
        case Left(error) =>
          fail(s"expected static runner output, got $error")
      }
    }

    "preserve dataset id, catalog snapshot id, and experiment id" in {
      val spikeResult = runSpike(
        input = esInput(resourceConfig = None),
        gate = M9OfflineEvalRealBackendResourceGate.DefaultDisabled,
      )
      val artifact = M9OfflineEvalRealBackendSpikeArtifactCapture.captureDefaultDisabled(spikeResult)

      artifact match {
        case Right(value) =>
          assert(value.savedReport.evalDatasetId == EvalDatasetId("beautyq-m9-real-backend-spike-v1"))
          assert(value.savedReport.catalogSnapshotId == CatalogSnapshotId("seed-resource-catalog"))
          assert(value.savedReport.metadata.experimentId == ExperimentId("m9-real-backend-spike-spec"))
          assert(value.savedReport.metadata.evalDatasetId == EvalDatasetId("beautyq-m9-real-backend-spike-v1"))
          assert(value.savedReport.metadata.catalogSnapshotId == CatalogSnapshotId("seed-resource-catalog"))
        case Left(error) =>
          fail(s"expected preserved ids, got $error")
      }
    }

    "not require real backend calls and avoid route/plugin/DI/http surfaces" in {
      val spikeResult = runSpike(
        input = esInput(resourceConfig = None),
        gate = M9OfflineEvalRealBackendResourceGate.DefaultDisabled,
      )
      val artifact = M9OfflineEvalRealBackendSpikeArtifactCapture.captureDefaultDisabled(spikeResult)

      artifact match {
        case Right(value) =>
          assert(!value.summary.realBackendCallImplemented)
          assert(value.summary.offlineEvalEvidenceOnly)
          assert(!value.markdownArtifact.contents.toLowerCase.contains("elasticsearch query executed"))
          assert(!value.markdownArtifact.contents.toLowerCase.contains("qdrant query executed"))
          assert(!value.markdownArtifact.contents.toLowerCase.contains("hybrid execution"))

          val inputFields = value.staticRunResult.productElementNames.toSet
          val forbiddenTerms = List("route", "plugin", "distage", "module", "http", "docker", "client")
          assert(!inputFields.exists(field => forbiddenTerms.exists(term => field.toLowerCase.contains(term))))
        case Left(error) =>
          fail(s"expected no-backend-call artifact, got $error")
      }
    }

    "capture a denied artifact when required inputs are missing" in {
      val invalidInput = esInput(resourceConfig = None).copy(
        backendKind = None,
        candidateSource = None,
        queryClass = None,
      )
      val spikeResult = runSpike(
        input = invalidInput,
        gate = M9OfflineEvalRealBackendResourceGate(runRealBackendOfflineEval = true),
      )
      val artifact = M9OfflineEvalRealBackendSpikeArtifactCapture.capture(
        M9OfflineEvalRealBackendSpikeArtifactInput(
          spikeResult = spikeResult,
          markdownFilename = "m9-real-backend-gated-spike-denied.md",
        )
      )

      artifact match {
        case Right(deniedArtifact) =>
          assert(deniedArtifact.summary.gateDecision.status == M9OfflineEvalRealBackendResourceGateStatus.Denied)
          assert(deniedArtifact.savedReport.rows.forall(_.regressionStatus == "failed"))
          assert(deniedArtifact.savedReport.rows.flatMap(_.warnings).exists(_.contains("denied")))
          assert(deniedArtifact.savedReport.warnings.exists(_.contains("artifact_capture_gate_status=denied")))
        case Left(captureError) =>
          fail(s"expected denied artifact, got $captureError")
      }
    }
  }

  private def runSpike(
    input: M9OfflineEvalRealBackendSpikeInput,
    gate: M9OfflineEvalRealBackendResourceGate,
  ): leaderboard.search.eval.M9OfflineEvalRealBackendSpikeResult = {
    val adapter = M9OfflineEvalRealBackendSpikeAdapter(input, gate)
    adapter.runWithEvidence(input.request)
  }

  private def esInput(
    resourceConfig: Option[M9OfflineEvalRealBackendResourceConfig]
  ): M9OfflineEvalRealBackendSpikeInput =
    spikeInput(
      request = esRequest,
      backendKind = M9OfflineEvalRealBackendKind.Es,
      candidateSource = CandidateSource.Es,
      resourceConfig = resourceConfig,
    )

  private def qdrantInput(
    resourceConfig: Option[M9OfflineEvalRealBackendResourceConfig]
  ): M9OfflineEvalRealBackendSpikeInput =
    spikeInput(
      request = qdrantRequest,
      backendKind = M9OfflineEvalRealBackendKind.Qdrant,
      candidateSource = CandidateSource.Qdrant,
      resourceConfig = resourceConfig,
    )

  private def spikeInput(
    request: M9OfflineEvalBackendRunRequest,
    backendKind: M9OfflineEvalRealBackendKind,
    candidateSource: CandidateSource,
    resourceConfig: Option[M9OfflineEvalRealBackendResourceConfig],
  ): M9OfflineEvalRealBackendSpikeInput =
    M9OfflineEvalRealBackendSpikeInput(
      request = request,
      backendKind = Some(backendKind),
      candidateSource = Some(candidateSource),
      requestIdPolicy = Some(M9OfflineEvalRealBackendRequestIdPolicy.UsePlanRequestId),
      queryClass = Some(QueryClass.SemanticDescriptive),
      topK = Some(10),
      timeoutBudgetMillis = Some(5000),
      resourceConfig = resourceConfig,
      productionActivationNotApprovedConfirmed = true,
    )

  private def resourceConfig(name: String): M9OfflineEvalRealBackendResourceConfig =
    M9OfflineEvalRealBackendResourceConfig(
      resourceName = name,
      connectionTarget = s"$name-local-resource",
      resourceIdentity = Some(s"$name-identity"),
      connectionTimeoutMillis = 5000,
    )

  private def esRequest: M9OfflineEvalBackendRunRequest =
    M9OfflineEvalBackendRunRequest(esPlan, dataset)

  private def qdrantRequest: M9OfflineEvalBackendRunRequest =
    M9OfflineEvalBackendRunRequest(qdrantPlan, dataset)

  private def esPlan: M9OfflineEvalBackendExecutionPlan =
    plan(
      executionMode = M9OfflineEvalBackendExecutionMode.EsOnlyOffline,
      routingPolicyId = RoutingPolicyId("m9-real-backend-spike-es-v1"),
    )

  private def qdrantPlan: M9OfflineEvalBackendExecutionPlan =
    plan(
      executionMode = M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline,
      routingPolicyId = RoutingPolicyId("m9-real-backend-spike-qdrant-v1"),
    )

  private def plan(
    executionMode: M9OfflineEvalBackendExecutionMode,
    routingPolicyId: RoutingPolicyId,
  ): M9OfflineEvalBackendExecutionPlan =
    M9OfflineEvalBackendExecutionPlan(
      executionMode = executionMode,
      evalDatasetId = EvalDatasetId("beautyq-m9-real-backend-spike-v1"),
      catalogSnapshotId = CatalogSnapshotId("seed-resource-catalog"),
      experimentId = ExperimentId("m9-real-backend-spike-spec"),
      routingPolicyId = routingPolicyId,
      metricWindow = MetricWindow("offline-spike"),
      requestId = Some(RequestId("request-m9-real-backend-spike-001")),
      qualityGateDecision = "not_evaluated_not_for_activation",
      generatedAt = "2026-06-20T12:00:00Z",
      notes = List("m9-real-backend-spike-spec"),
      warnings = List("resource-gated offline eval only; production activation not approved"),
    )

  private def dataset: M9OfflineEvalDataset =
    M9OfflineEvalDataset(
      evalDatasetId = EvalDatasetId("beautyq-m9-real-backend-spike-v1"),
      catalogSnapshotId = CatalogSnapshotId("seed-resource-catalog"),
      queries = List(
        datasetQuery("q_spike_exact_001", QueryClass.ExactProductNameBrand),
        datasetQuery("q_spike_semantic_001", QueryClass.SemanticDescriptive),
      ),
    )

  private def datasetQuery(
    queryId: String,
    queryClass: QueryClass,
  ): M9OfflineEvalDatasetQuery =
    M9OfflineEvalDatasetQuery(
      queryId = queryId,
      rawQueryText = s"raw $queryId",
      normalizedQueryText = Some(s"raw $queryId"),
      queryClass = queryClass,
      filters = Nil,
      categories = Nil,
      expectedResults = List(M9OfflineEvalExpectedResult(resultId = s"expected-$queryId", note = None)),
      expectedNotes = Nil,
      negativeOutOfCatalog = false,
    )

  private def checkedInDefaultDisabledArtifact: String =
    readCheckedInResource("/leaderboard/search/eval/m9-real-backend-gated-spike-default-disabled-report.md")

  private def checkedInNotConfiguredArtifact: String =
    readCheckedInResource("/leaderboard/search/eval/m9-real-backend-gated-spike-not-configured-report.md")

  private def readCheckedInResource(path: String): String = {
    val stream = Option(getClass.getResourceAsStream(path))

    stream match {
      case Some(value) =>
        Using.resource(Source.fromInputStream(value, StandardCharsets.UTF_8.name()))(_.mkString)
      case None =>
        fail(s"missing checked-in resource artifact $path")
    }
  }
}
