package leaderboard.search.eval

final case class M9OfflineEvalQdrantOnlyGatedSmokeInput(
  resourceConfig: Option[M9OfflineEvalRealBackendResourceConfig],
  gate: M9OfflineEvalRealBackendResourceGate,
)

final case class M9OfflineEvalQdrantOnlyGatedSmokeResult(
  artifact: M9OfflineEvalRealBackendSpikeArtifact,
  gateDecision: M9OfflineEvalRealBackendResourceGateDecision,
  qdrantSource: CandidateSource,
  qdrantExecutionMode: M9OfflineEvalBackendExecutionMode,
  qdrantServingMode: ServingMode,
  resourceConfigPresent: Boolean,
  productionActivationNotApprovedConfirmed: Boolean,
  realBackendCallImplemented: Boolean,
  offlineEvalEvidenceOnly: Boolean,
  notProductionTelemetry: Boolean,
  notActivationApproval: Boolean,
)

object M9OfflineEvalQdrantOnlyGatedSmokeEvidence {

  val ArtifactBoundaryNote: String =
    "Offline eval evidence only; not production telemetry; not production activation approval."

  val NotConfiguredFilename: String = "m9-qdrant-only-gated-smoke-not-configured-report.md"
  val DefaultDisabledFilename: String = "m9-qdrant-only-gated-smoke-default-disabled-report.md"

  val QdrantCandidateSource: CandidateSource = CandidateSource.Qdrant
  val QdrantExecutionMode: M9OfflineEvalBackendExecutionMode = M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline
  val QdrantServingMode: ServingMode = ServingMode.QdrantOnly

  val EmbeddingVectorPrerequisiteWarning: String =
    "qdrant-only smoke requires embedding model and vector config; embedding/vector prerequisites are not configured"

  def captureDefaultDisabled(
    input: M9OfflineEvalQdrantOnlyGatedSmokeInput,
  ): Either[M9OfflineEvalRealBackendSpikeArtifactCaptureError, M9OfflineEvalQdrantOnlyGatedSmokeResult] = {
    val spikeResult = runQdrantSpike(input, M9OfflineEvalRealBackendResourceGate.DefaultDisabled)
    M9OfflineEvalRealBackendSpikeArtifactCapture.capture(
      M9OfflineEvalRealBackendSpikeArtifactInput(
        spikeResult = spikeResult,
        markdownFilename = DefaultDisabledFilename,
      )
    ).map(buildResult)
  }

  def captureNotConfigured(
    input: M9OfflineEvalQdrantOnlyGatedSmokeInput,
  ): Either[M9OfflineEvalRealBackendSpikeArtifactCaptureError, M9OfflineEvalQdrantOnlyGatedSmokeResult] = {
    val spikeResult = runQdrantSpike(input, input.gate)
    M9OfflineEvalRealBackendSpikeArtifactCapture.capture(
      M9OfflineEvalRealBackendSpikeArtifactInput(
        spikeResult = spikeResult,
        markdownFilename = NotConfiguredFilename,
      )
    ).map(buildResult)
  }

  def capture(
    input: M9OfflineEvalQdrantOnlyGatedSmokeInput,
    markdownFilename: String,
  ): Either[M9OfflineEvalRealBackendSpikeArtifactCaptureError, M9OfflineEvalQdrantOnlyGatedSmokeResult] = {
    val spikeResult = runQdrantSpike(input, input.gate)
    M9OfflineEvalRealBackendSpikeArtifactCapture.capture(
      M9OfflineEvalRealBackendSpikeArtifactInput(
        spikeResult = spikeResult,
        markdownFilename = markdownFilename,
      )
    ).map(buildResult)
  }

  private def runQdrantSpike(
    input: M9OfflineEvalQdrantOnlyGatedSmokeInput,
    gate: M9OfflineEvalRealBackendResourceGate,
  ): M9OfflineEvalRealBackendSpikeResult = {
    val spikeInput = M9OfflineEvalRealBackendSpikeInput(
      request = qdrantRequest,
      backendKind = Some(M9OfflineEvalRealBackendKind.Qdrant),
      candidateSource = Some(CandidateSource.Qdrant),
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

  private def buildResult(artifact: M9OfflineEvalRealBackendSpikeArtifact): M9OfflineEvalQdrantOnlyGatedSmokeResult =
    M9OfflineEvalQdrantOnlyGatedSmokeResult(
      artifact = artifact,
      gateDecision = artifact.summary.gateDecision,
      qdrantSource = CandidateSource.Qdrant,
      qdrantExecutionMode = M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline,
      qdrantServingMode = ServingMode.QdrantOnly,
      resourceConfigPresent = artifact.summary.resourceConfigPresent,
      productionActivationNotApprovedConfirmed = artifact.summary.productionActivationNotApprovedConfirmed,
      realBackendCallImplemented = artifact.summary.realBackendCallImplemented,
      offlineEvalEvidenceOnly = artifact.summary.offlineEvalEvidenceOnly,
      notProductionTelemetry = artifact.summary.notProductionTelemetry,
      notActivationApproval = artifact.summary.notActivationApproval,
    )

  private def qdrantRequest: M9OfflineEvalBackendRunRequest =
    M9OfflineEvalBackendRunRequest(qdrantPlan, qdrantDataset)

  private def qdrantPlan: M9OfflineEvalBackendExecutionPlan =
    M9OfflineEvalBackendExecutionPlan(
      executionMode = M9OfflineEvalBackendExecutionMode.QdrantOnlyOffline,
      evalDatasetId = EvalDatasetId("beautyq-m9-qdrant-only-gated-smoke-v1"),
      catalogSnapshotId = CatalogSnapshotId("seed-resource-catalog"),
      experimentId = ExperimentId("m9-qdrant-only-gated-smoke-spec"),
      routingPolicyId = RoutingPolicyId("m9-qdrant-only-gated-smoke-qdrant-v1"),
      metricWindow = MetricWindow("offline-qdrant-smoke"),
      requestId = Some(RequestId("request-m9-qdrant-only-gated-smoke-001")),
      qualityGateDecision = "not_evaluated_not_for_activation",
      generatedAt = "2026-06-20T15:00:00Z",
      notes = List("m9-qdrant-only-gated-smoke-spec"),
      warnings = List(
        "Qdrant-only gated smoke offline eval only; production activation not approved",
        EmbeddingVectorPrerequisiteWarning,
      ),
    )

  private def qdrantDataset: M9OfflineEvalDataset =
    M9OfflineEvalDataset(
      evalDatasetId = EvalDatasetId("beautyq-m9-qdrant-only-gated-smoke-v1"),
      catalogSnapshotId = CatalogSnapshotId("seed-resource-catalog"),
      queries = List(
        datasetQuery("q_qdrant_smoke_semantic_001", QueryClass.SemanticDescriptive),
        datasetQuery("q_qdrant_smoke_broad_001", QueryClass.BroadDiscovery),
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
