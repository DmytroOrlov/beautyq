package leaderboard.search.eval

final case class M9OfflineEvalRealBackendSpikeArtifactInput(
  spikeResult: M9OfflineEvalRealBackendSpikeResult,
  markdownFilename: String,
)

final case class M9OfflineEvalRealBackendSpikeArtifactSummary(
  gateDecision: M9OfflineEvalRealBackendResourceGateDecision,
  backendKind: Option[M9OfflineEvalRealBackendKind],
  executionMode: M9OfflineEvalBackendExecutionMode,
  candidateSource: CandidateSource,
  resourceConfigPresent: Boolean,
  productionActivationNotApprovedConfirmed: Boolean,
  realBackendCallImplemented: Boolean,
  offlineEvalEvidenceOnly: Boolean,
  notProductionTelemetry: Boolean,
  notActivationApproval: Boolean,
) {
  def render: String =
    List(
      s"gate_status=${gateDecision.status.render}",
      s"backend_kind=${backendKind.fold("unknown")(_.render)}",
      s"execution_mode=${executionMode.render}",
      s"candidate_source=${candidateSource.render}",
      s"resource_config_present=$resourceConfigPresent",
      s"production_activation_not_approved_confirmed=$productionActivationNotApprovedConfirmed",
      s"real_backend_call_implemented=$realBackendCallImplemented",
      s"offline_eval_evidence_only=$offlineEvalEvidenceOnly",
      s"not_production_telemetry=$notProductionTelemetry",
      s"not_activation_approval=$notActivationApproval",
    ).mkString("; ")
}

final case class M9OfflineEvalRealBackendSpikeArtifact(
  savedReport: M9OfflineEvalSavedReport,
  markdownArtifact: M9OfflineEvalReportArtifact,
  summary: M9OfflineEvalRealBackendSpikeArtifactSummary,
  staticRunResult: M9OfflineEvalStaticRunResult,
)

object M9OfflineEvalRealBackendSpikeArtifactCapture {
  val ArtifactBoundaryNote: String =
    "Offline eval evidence only; not production telemetry; not production activation approval."

  val DefaultDisabledFilename: String = "m9-real-backend-gated-spike-default-disabled.md"
  val NotConfiguredFilename: String = "m9-real-backend-gated-spike-not-configured.md"
  val ConfiguredFilename: String = "m9-real-backend-gated-spike-configured.md"

  def capture(
    input: M9OfflineEvalRealBackendSpikeArtifactInput,
  ): Either[M9OfflineEvalRealBackendSpikeArtifactCaptureError, M9OfflineEvalRealBackendSpikeArtifact] = {
    val evidence = input.spikeResult.evidence
    val response = input.spikeResult.response

    val staticRunInput = response.toStaticRunInput
    val enrichedInput = staticRunInput.copy(
      notes = staticRunInput.notes :+ ArtifactBoundaryNote,
      warnings = staticRunInput.warnings ++ captureWarnings(evidence),
    )

    M9OfflineEvalStaticRunner.run(
      input = enrichedInput,
      config = M9OfflineEvalStaticRunnerConfig(markdownFilename = input.markdownFilename),
    ) match {
      case Right(staticRun) =>
        val summary = buildSummary(evidence)
        Right(M9OfflineEvalRealBackendSpikeArtifact(
          savedReport = staticRun.report,
          markdownArtifact = staticRun.markdownArtifact,
          summary = summary,
          staticRunResult = staticRun,
        ))
      case Left(M9OfflineEvalStaticRunError(reasons)) =>
        Left(M9OfflineEvalRealBackendSpikeArtifactCaptureError(reasons))
    }
  }

  def captureDefaultDisabled(
    spikeResult: M9OfflineEvalRealBackendSpikeResult,
  ): Either[M9OfflineEvalRealBackendSpikeArtifactCaptureError, M9OfflineEvalRealBackendSpikeArtifact] =
    capture(M9OfflineEvalRealBackendSpikeArtifactInput(
      spikeResult = spikeResult,
      markdownFilename = DefaultDisabledFilename,
    ))

  def captureNotConfigured(
    spikeResult: M9OfflineEvalRealBackendSpikeResult,
  ): Either[M9OfflineEvalRealBackendSpikeArtifactCaptureError, M9OfflineEvalRealBackendSpikeArtifact] =
    capture(M9OfflineEvalRealBackendSpikeArtifactInput(
      spikeResult = spikeResult,
      markdownFilename = NotConfiguredFilename,
    ))

  def captureConfigured(
    spikeResult: M9OfflineEvalRealBackendSpikeResult,
  ): Either[M9OfflineEvalRealBackendSpikeArtifactCaptureError, M9OfflineEvalRealBackendSpikeArtifact] =
    capture(M9OfflineEvalRealBackendSpikeArtifactInput(
      spikeResult = spikeResult,
      markdownFilename = ConfiguredFilename,
    ))

  private def buildSummary(evidence: M9OfflineEvalRealBackendSpikeEvidence): M9OfflineEvalRealBackendSpikeArtifactSummary =
    M9OfflineEvalRealBackendSpikeArtifactSummary(
      gateDecision = evidence.gateDecision,
      backendKind = evidence.backendKind,
      executionMode = evidence.executionMode,
      candidateSource = evidence.candidateSource,
      resourceConfigPresent = evidence.resourceConfigPresent,
      productionActivationNotApprovedConfirmed = evidence.productionActivationNotApprovedConfirmed,
      realBackendCallImplemented = evidence.realBackendCallImplemented,
      offlineEvalEvidenceOnly = true,
      notProductionTelemetry = true,
      notActivationApproval = true,
    )

  private def captureWarnings(evidence: M9OfflineEvalRealBackendSpikeEvidence): List[String] =
    List(
      s"artifact_capture_gate_status=${evidence.gateDecision.status.render}",
      s"artifact_capture_resource_config_present=${evidence.resourceConfigPresent}",
      s"artifact_capture_real_backend_call_implemented=${evidence.realBackendCallImplemented}",
      ArtifactBoundaryNote,
    )
}

final case class M9OfflineEvalRealBackendSpikeArtifactCaptureError(
  reasons: List[String],
)
