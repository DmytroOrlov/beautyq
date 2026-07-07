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
  M9OfflineEvalRealBackendSpikeInput,
  M9OfflineEvalStaticRunner,
  M9OfflineEvalStaticRunnerConfig,
  MetricWindow,
  OfflineEvalMetricName,
  QueryClass,
  RequestId,
  RoutingPolicyId,
  ServingMode,
}
import org.scalatest.wordspec.AnyWordSpec

final class M9OfflineEvalRealBackendResourceGateSpec extends AnyWordSpec {

  "M9OfflineEvalRealBackendResourceGate" should {

    "be disabled by default" in {
      val decision = M9OfflineEvalRealBackendResourceGate.DefaultDisabled.decide(esInput(None))

      assert(!decision.allowsAdapterRun)
      assert(!decision.permitsRealBackendCall)
      assert(decision.status == M9OfflineEvalRealBackendResourceGateStatus.Disabled)
      assert(decision.reasons == List("RUN_REAL_BACKEND_OFFLINE_EVAL=1 is required for real backend offline eval"))
    }

    "require the explicit enablement flag" in {
      assert(M9OfflineEvalRealBackendResourceGate.fromEnv(Map.empty).runRealBackendOfflineEval == false)
      assert(M9OfflineEvalRealBackendResourceGate.fromEnv(Map("RUN_REAL_BACKEND_OFFLINE_EVAL" -> "0")).runRealBackendOfflineEval == false)
      assert(M9OfflineEvalRealBackendResourceGate.fromEnv(Map("RUN_REAL_BACKEND_OFFLINE_EVAL" -> "1")).runRealBackendOfflineEval)
    }

    "deny missing required inputs before any backend resource call" in {
      val enabledGate = M9OfflineEvalRealBackendResourceGate(runRealBackendOfflineEval = true)
      val missing = esInput(None).copy(
        request = esRequest.copy(plan = esPlan.copy(
          evalDatasetId = EvalDatasetId(""),
          catalogSnapshotId = CatalogSnapshotId(" "),
          experimentId = ExperimentId(""),
          requestId = None,
        )),
        backendKind = None,
        candidateSource = None,
        requestIdPolicy = None,
        queryClass = None,
        topK = Some(0),
        timeoutBudgetMillis = Some(0),
      )

      val decision = enabledGate.decide(missing)

      assert(decision.status == M9OfflineEvalRealBackendResourceGateStatus.Denied)
      assert(decision.reasons.contains("eval dataset id is required"))
      assert(decision.reasons.contains("catalog snapshot id is required"))
      assert(decision.reasons.contains("experiment id is required"))
      assert(decision.reasons.contains("request id policy or explicit request id is required"))
      assert(decision.reasons.contains("backend kind is required"))
      assert(decision.reasons.contains("candidate source is required"))
      assert(decision.reasons.contains("query class is required"))
      assert(decision.reasons.contains("top-k must be positive"))
      assert(decision.reasons.contains("timeout budget must be positive"))
      assert(decision.warnings == List("real backend offline eval denied before any backend resource call"))
    }

    "require explicit production activation non-approval confirmation" in {
      val enabledGate = M9OfflineEvalRealBackendResourceGate(runRealBackendOfflineEval = true)
      val decision = enabledGate.decide(esInput(None).copy(productionActivationNotApprovedConfirmed = false))

      assert(decision.status == M9OfflineEvalRealBackendResourceGateStatus.Denied)
      assert(decision.reasons == List("explicit production-activation non-approval confirmation is required"))
    }

    "avoid production route, plugin, DI, transport, Docker, metrics, and backend-call surfaces" in {
      val input = esInput(resourceConfig = Some(resourceConfig("es-resource")))
      val adapter = M9OfflineEvalRealBackendSpikeAdapter(input, M9OfflineEvalRealBackendResourceGate.DefaultDisabled)
      val fields =
        input.productElementNames.toList ++
          input.request.productElementNames.toList ++
          input.request.plan.productElementNames.toList ++
          resourceConfig("es-resource").productElementNames.toList ++
          adapter.productElementNames.toList
      val forbiddenTerms = List("route", "plugin", "distage", "module", "http", "docker", "client", "metrics")

      assert(!fields.exists(field => forbiddenTerms.exists(term => field.toLowerCase.contains(term))))
      assert(!fields.exists(_.toLowerCase.contains("beautysearch")))
      assert(adapter.executionMode == M9OfflineEvalBackendExecutionMode.EsOnlyOffline)
    }
  }

  "M9OfflineEvalRealBackendSpikeAdapter" should {

    "return denied decisions as failure data, not thrown exceptions" in {
      val adapter = M9OfflineEvalRealBackendSpikeAdapter(
        input = esInput(None).copy(queryClass = None),
        gate = M9OfflineEvalRealBackendResourceGate(runRealBackendOfflineEval = true),
      )
      val result = adapter.runWithEvidence(esRequest)

      assert(result.evidence.gateDecision.status == M9OfflineEvalRealBackendResourceGateStatus.Denied)
      assert(result.response.queryResults.map(_.failure.map(_.message)) == List(
        Some("real backend offline eval denied: query class is required"),
        Some("real backend offline eval denied: query class is required"),
      ))
      assert(result.response.rows.flatMap(_.metrics.map(_.name)) == List(
        OfflineEvalMetricName.FailureCount,
        OfflineEvalMetricName.FailureCount,
      ))
    }

    "return not-configured data when enabled inputs have no resource config" in {
      val adapter = M9OfflineEvalRealBackendSpikeAdapter(
        input = esInput(None),
        gate = M9OfflineEvalRealBackendResourceGate(runRealBackendOfflineEval = true),
      )
      val result = adapter.runWithEvidence(esRequest)

      assert(result.evidence.gateDecision.status == M9OfflineEvalRealBackendResourceGateStatus.AllowedWithoutResourceConfig)
      assert(!result.evidence.gateDecision.permitsRealBackendCall)
      assert(!result.evidence.realBackendCallImplemented)
      assert(result.response.queryResults.map(_.candidateSource) == List(CandidateSource.Es, CandidateSource.Es))
      assert(result.response.queryResults.map(_.servingMode) == List(ServingMode.EsOnly, ServingMode.EsOnly))
      assert(result.response.queryResults.forall(_.candidates.isEmpty))
      assert(result.response.queryResults.forall(_.failure.exists(
        _.message == "es offline adapter resource config is not configured; no real backend call attempted"
      )))
    }

    "preserve ES source and mode when an ES resource config is present" in {
      val adapter = M9OfflineEvalRealBackendSpikeAdapter(
        input = esInput(resourceConfig = Some(resourceConfig("es-resource"))),
        gate = M9OfflineEvalRealBackendResourceGate(runRealBackendOfflineEval = true),
      )
      val result = adapter.runWithEvidence(esRequest)

      assert(result.evidence.gateDecision.status == M9OfflineEvalRealBackendResourceGateStatus.AllowedForConfiguredResource)
      assert(result.evidence.gateDecision.permitsRealBackendCall)
      assert(!result.evidence.realBackendCallImplemented)
      assert(result.response.metadata.servingMode == ServingMode.EsOnly)
      assert(result.response.metadata.candidateSource == CandidateSource.Es)
      assert(result.response.queryResults.map(_.candidateSource) == List(CandidateSource.Es, CandidateSource.Es))
      assert(result.response.queryResults.forall(_.failure.exists(
        _.message == "es offline adapter resource is configured, but real backend client calls are not implemented in this spike"
      )))
    }

    "preserve Qdrant source and mode when a Qdrant resource config is present" in {
      val adapter = M9OfflineEvalRealBackendSpikeAdapter(
        input = qdrantInput(resourceConfig = Some(resourceConfig("qdrant-resource"))),
        gate = M9OfflineEvalRealBackendResourceGate(runRealBackendOfflineEval = true),
      )
      val result = adapter.runWithEvidence(qdrantRequest)

      assert(result.evidence.gateDecision.status == M9OfflineEvalRealBackendResourceGateStatus.AllowedForConfiguredResource)
      assert(result.evidence.gateDecision.permitsRealBackendCall)
      assert(!result.evidence.realBackendCallImplemented)
      assert(result.response.metadata.servingMode == ServingMode.QdrantOnly)
      assert(result.response.metadata.candidateSource == CandidateSource.Qdrant)
      assert(result.response.queryResults.map(_.candidateSource) == List(CandidateSource.Qdrant, CandidateSource.Qdrant))
      assert(result.response.queryResults.forall(_.failure.exists(
        _.message == "qdrant offline adapter resource is configured, but real backend client calls are not implemented in this spike"
      )))
    }

    "feed M9OfflineEvalStaticRunner as failure rows when allowed without resource config" in {
      val adapter = M9OfflineEvalRealBackendSpikeAdapter(
        input = esInput(None),
        gate = M9OfflineEvalRealBackendResourceGate(runRealBackendOfflineEval = true),
      )
      val result = adapter.runWithEvidence(esRequest)
      val staticResult = M9OfflineEvalStaticRunner.run(
        input = result.response.toStaticRunInput,
        config = M9OfflineEvalStaticRunnerConfig(markdownFilename = "m9-real-backend-resource-gate.md"),
      )

      staticResult match {
        case Right(staticRun) =>
          assert(staticRun.report.metadata.servingMode == ServingMode.EsOnly)
          assert(staticRun.report.metadata.candidateSource == CandidateSource.Es)
          assert(staticRun.report.rows.forall(_.regressionStatus == "failed"))
          assert(staticRun.report.rows.flatMap(_.warnings).exists(_.contains("not configured")))
          assert(staticRun.report.warnings.exists(_.contains("resource_config_present=false")))
        case Left(error) =>
          fail(s"expected static runner to accept resource-gated failure rows, got $error")
      }
    }
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
      routingPolicyId = RoutingPolicyId("m9-real-backend-resource-gate-es-v1"),
    )

  private def qdrantPlan: M9OfflineEvalBackendExecutionPlan =
    plan(
      executionMode = M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline,
      routingPolicyId = RoutingPolicyId("m9-real-backend-resource-gate-qdrant-v1"),
    )

  private def plan(
    executionMode: M9OfflineEvalBackendExecutionMode,
    routingPolicyId: RoutingPolicyId,
  ): M9OfflineEvalBackendExecutionPlan =
    M9OfflineEvalBackendExecutionPlan(
      executionMode = executionMode,
      evalDatasetId = EvalDatasetId("beautyq-m9-real-backend-resource-gate-v1"),
      catalogSnapshotId = CatalogSnapshotId("seed-resource-catalog"),
      experimentId = ExperimentId("m9-real-backend-resource-gate-spec"),
      routingPolicyId = routingPolicyId,
      metricWindow = MetricWindow("offline-resource-gate"),
      requestId = Some(RequestId("request-m9-real-backend-resource-gate-001")),
      qualityGateDecision = "not_evaluated_not_for_activation",
      generatedAt = "2026-06-20T10:15:30Z",
      notes = List("m9-real-backend-resource-gate-spec"),
      warnings = List("resource-gated offline eval only; production activation not approved"),
    )

  private def dataset: M9OfflineEvalDataset =
    M9OfflineEvalDataset(
      evalDatasetId = EvalDatasetId("beautyq-m9-real-backend-resource-gate-v1"),
      catalogSnapshotId = CatalogSnapshotId("seed-resource-catalog"),
      queries = List(
        datasetQuery("q_gate_exact_001", QueryClass.ExactProductNameBrand),
        datasetQuery("q_gate_semantic_001", QueryClass.SemanticDescriptive),
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
}
