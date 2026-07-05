package leaderboard.search.eval

final case class M9OfflineEvalEsOnlyGatedSmokeInput(
  resourceConfig: Option[M9OfflineEvalRealBackendResourceConfig],
  gate: M9OfflineEvalRealBackendResourceGate,
)

final case class M9OfflineEvalEsOnlyGatedSmokeResult(
  artifact: M9OfflineEvalRealBackendSpikeArtifact,
  gateDecision: M9OfflineEvalRealBackendResourceGateDecision,
  esSource: CandidateSource,
  esExecutionMode: M9OfflineEvalBackendExecutionMode,
  esServingMode: ServingMode,
  resourceConfigPresent: Boolean,
  productionActivationNotApprovedConfirmed: Boolean,
  realBackendCallImplemented: Boolean,
  offlineEvalEvidenceOnly: Boolean,
  notProductionTelemetry: Boolean,
  notActivationApproval: Boolean,
)

object M9OfflineEvalEsOnlyGatedSmokeEvidence {

  val ArtifactBoundaryNote: String =
    "Offline eval evidence only; not production telemetry; not production activation approval."

  val NotConfiguredFilename: String = "m9-es-only-gated-smoke-not-configured-report.md"
  val DefaultDisabledFilename: String = "m9-es-only-gated-smoke-default-disabled-report.md"

  val EsCandidateSource: CandidateSource = CandidateSource.Es
  val EsExecutionMode: M9OfflineEvalBackendExecutionMode = M9OfflineEvalBackendExecutionMode.EsOnlyOffline
  val EsServingMode: ServingMode = ServingMode.EsOnly

  def captureDefaultDisabled(
    input: M9OfflineEvalEsOnlyGatedSmokeInput,
  ): Either[M9OfflineEvalRealBackendSpikeArtifactCaptureError, M9OfflineEvalEsOnlyGatedSmokeResult] = {
    val spikeResult = runEsSpike(input, M9OfflineEvalRealBackendResourceGate.DefaultDisabled)
    M9OfflineEvalRealBackendSpikeArtifactCapture.capture(
      M9OfflineEvalRealBackendSpikeArtifactInput(
        spikeResult = spikeResult,
        markdownFilename = DefaultDisabledFilename,
      )
    ).map(buildResult)
  }

  def captureNotConfigured(
    input: M9OfflineEvalEsOnlyGatedSmokeInput,
  ): Either[M9OfflineEvalRealBackendSpikeArtifactCaptureError, M9OfflineEvalEsOnlyGatedSmokeResult] = {
    val spikeResult = runEsSpike(input, input.gate)
    M9OfflineEvalRealBackendSpikeArtifactCapture.capture(
      M9OfflineEvalRealBackendSpikeArtifactInput(
        spikeResult = spikeResult,
        markdownFilename = NotConfiguredFilename,
      )
    ).map(buildResult)
  }

  def capture(
    input: M9OfflineEvalEsOnlyGatedSmokeInput,
    markdownFilename: String,
  ): Either[M9OfflineEvalRealBackendSpikeArtifactCaptureError, M9OfflineEvalEsOnlyGatedSmokeResult] = {
    val spikeResult = runEsSpike(input, input.gate)
    M9OfflineEvalRealBackendSpikeArtifactCapture.capture(
      M9OfflineEvalRealBackendSpikeArtifactInput(
        spikeResult = spikeResult,
        markdownFilename = markdownFilename,
      )
    ).map(buildResult)
  }

  private def runEsSpike(
    input: M9OfflineEvalEsOnlyGatedSmokeInput,
    gate: M9OfflineEvalRealBackendResourceGate,
  ): M9OfflineEvalRealBackendSpikeResult = {
    val spikeInput = M9OfflineEvalRealBackendSpikeInput(
      request = esRequest,
      backendKind = Some(M9OfflineEvalRealBackendKind.Es),
      candidateSource = Some(CandidateSource.Es),
      requestIdPolicy = Some(M9OfflineEvalRealBackendRequestIdPolicy.UsePlanRequestId),
      queryClass = Some(QueryClass.SemanticDescriptive),
      topK = Some(10),
      timeoutBudgetMillis = Some(5000),
      resourceConfig = input.resourceConfig,
      productionActivationNotApprovedConfirmed = true,
    )
    val adapter = M9OfflineEvalRealBackendSpikeAdapter(spikeInput, gate)
    adapter.runWithEvidence(spikeInput.request)
  }

  private def buildResult(artifact: M9OfflineEvalRealBackendSpikeArtifact): M9OfflineEvalEsOnlyGatedSmokeResult =
    M9OfflineEvalEsOnlyGatedSmokeResult(
      artifact = artifact,
      gateDecision = artifact.summary.gateDecision,
      esSource = CandidateSource.Es,
      esExecutionMode = M9OfflineEvalBackendExecutionMode.EsOnlyOffline,
      esServingMode = ServingMode.EsOnly,
      resourceConfigPresent = artifact.summary.resourceConfigPresent,
      productionActivationNotApprovedConfirmed = artifact.summary.productionActivationNotApprovedConfirmed,
      realBackendCallImplemented = artifact.summary.realBackendCallImplemented,
      offlineEvalEvidenceOnly = artifact.summary.offlineEvalEvidenceOnly,
      notProductionTelemetry = artifact.summary.notProductionTelemetry,
      notActivationApproval = artifact.summary.notActivationApproval,
    )

  private def esRequest: M9OfflineEvalBackendRunRequest =
    M9OfflineEvalBackendRunRequest(esPlan, esDataset)

  private def esPlan: M9OfflineEvalBackendExecutionPlan =
    M9OfflineEvalBackendExecutionPlan(
      executionMode = M9OfflineEvalBackendExecutionMode.EsOnlyOffline,
      evalDatasetId = EvalDatasetId("beautyq-m9-es-only-gated-smoke-v1"),
      catalogSnapshotId = CatalogSnapshotId("seed-resource-catalog"),
      experimentId = ExperimentId("m9-es-only-gated-smoke-spec"),
      routingPolicyId = RoutingPolicyId("m9-es-only-gated-smoke-es-v1"),
      metricWindow = MetricWindow("offline-es-smoke"),
      requestId = Some(RequestId("request-m9-es-only-gated-smoke-001")),
      qualityGateDecision = "not_evaluated_not_for_activation",
      generatedAt = "2026-06-20T15:00:00Z",
      notes = List("m9-es-only-gated-smoke-spec"),
      warnings = List("ES-only gated smoke offline eval only; production activation not approved"),
    )

  private def esDataset: M9OfflineEvalDataset =
    M9OfflineEvalDataset(
      evalDatasetId = EvalDatasetId("beautyq-m9-es-only-gated-smoke-v1"),
      catalogSnapshotId = CatalogSnapshotId("seed-resource-catalog"),
      queries = List(
        datasetQuery("q_es_smoke_exact_001", QueryClass.ExactProductNameBrand),
        datasetQuery("q_es_smoke_semantic_001", QueryClass.SemanticDescriptive),
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
