package leaderboard.search.eval

final case class M9OfflineEvalCombinedGatedSmokeEvidenceInput(
  esResult: M9OfflineEvalEsOnlyGatedSmokeResult,
  qdrantResult: M9OfflineEvalQdrantOnlyGatedSmokeResult,
)

final case class M9OfflineEvalCombinedGatedSmokeEvidenceSummary(
  esArtifactFilename: String,
  qdrantArtifactFilename: String,
  esSource: CandidateSource,
  esServingMode: ServingMode,
  qdrantSource: CandidateSource,
  qdrantServingMode: ServingMode,
  esGateDecision: M9OfflineEvalRealBackendResourceGateDecision,
  qdrantGateDecision: M9OfflineEvalRealBackendResourceGateDecision,
  esResourceConfigPresent: Boolean,
  qdrantResourceConfigPresent: Boolean,
  productionActivationNotApprovedConfirmed: Boolean,
  realBackendCallImplemented: Boolean,
  offlineEvalEvidenceOnly: Boolean,
  notProductionTelemetry: Boolean,
  notActivationApproval: Boolean,
)

final case class M9OfflineEvalCombinedGatedSmokeEvidenceIndex(
  markdownArtifact: M9OfflineEvalReportArtifact,
  summary: M9OfflineEvalCombinedGatedSmokeEvidenceSummary,
)

object M9OfflineEvalCombinedGatedSmokeEvidenceIndex {

  val ArtifactBoundaryNote: String =
    "Offline eval evidence only; not production telemetry; not production activation approval."

  val EsOnlyGatedSmokeNotConfiguredFilename: String =
    M9OfflineEvalEsOnlyGatedSmokeEvidence.NotConfiguredFilename
  val QdrantOnlyGatedSmokeNotConfiguredFilename: String =
    M9OfflineEvalQdrantOnlyGatedSmokeEvidence.NotConfiguredFilename

  val EsOnlyGatedSmokeDefaultDisabledFilename: String =
    M9OfflineEvalEsOnlyGatedSmokeEvidence.DefaultDisabledFilename
  val QdrantOnlyGatedSmokeDefaultDisabledFilename: String =
    M9OfflineEvalQdrantOnlyGatedSmokeEvidence.DefaultDisabledFilename

  def build(
    input: M9OfflineEvalCombinedGatedSmokeEvidenceInput,
    markdownFilename: String,
  ): M9OfflineEvalCombinedGatedSmokeEvidenceIndex = {
    val rendered = M9OfflineEvalCombinedGatedSmokeEvidenceRenderer.render(input)
    val summary = M9OfflineEvalCombinedGatedSmokeEvidenceSummary(
      esArtifactFilename = input.esResult.artifact.markdownArtifact.filename,
      qdrantArtifactFilename = input.qdrantResult.artifact.markdownArtifact.filename,
      esSource = input.esResult.esSource,
      esServingMode = input.esResult.esServingMode,
      qdrantSource = input.qdrantResult.qdrantSource,
      qdrantServingMode = input.qdrantResult.qdrantServingMode,
      esGateDecision = input.esResult.gateDecision,
      qdrantGateDecision = input.qdrantResult.gateDecision,
      esResourceConfigPresent = input.esResult.resourceConfigPresent,
      qdrantResourceConfigPresent = input.qdrantResult.resourceConfigPresent,
      productionActivationNotApprovedConfirmed =
        input.esResult.productionActivationNotApprovedConfirmed &&
          input.qdrantResult.productionActivationNotApprovedConfirmed,
      realBackendCallImplemented =
        input.esResult.realBackendCallImplemented ||
          input.qdrantResult.realBackendCallImplemented,
      offlineEvalEvidenceOnly =
        input.esResult.offlineEvalEvidenceOnly && input.qdrantResult.offlineEvalEvidenceOnly,
      notProductionTelemetry =
        input.esResult.notProductionTelemetry && input.qdrantResult.notProductionTelemetry,
      notActivationApproval =
        input.esResult.notActivationApproval && input.qdrantResult.notActivationApproval,
    )
    val artifact = M9OfflineEvalReportArtifact(
      filename = markdownFilename,
      contentType = "text/markdown; charset=utf-8",
      contents = rendered,
    )
    M9OfflineEvalCombinedGatedSmokeEvidenceIndex(
      markdownArtifact = artifact,
      summary = summary,
    )
  }

  def fromNotConfiguredInputs(
    esInput: M9OfflineEvalEsOnlyGatedSmokeInput,
    qdrantInput: M9OfflineEvalQdrantOnlyGatedSmokeInput,
  ): Either[M9OfflineEvalRealBackendSpikeArtifactCaptureError, M9OfflineEvalCombinedGatedSmokeEvidenceIndex] =
    for {
      es <- M9OfflineEvalEsOnlyGatedSmokeEvidence.captureNotConfigured(esInput)
      qdrant <- M9OfflineEvalQdrantOnlyGatedSmokeEvidence.captureNotConfigured(qdrantInput)
    } yield build(
      input = M9OfflineEvalCombinedGatedSmokeEvidenceInput(esResult = es, qdrantResult = qdrant),
      markdownFilename = EsOnlyQdrantOnlyNotConfiguredIndexFilename,
    )

  def fromDefaultDisabledInputs(
    esInput: M9OfflineEvalEsOnlyGatedSmokeInput,
    qdrantInput: M9OfflineEvalQdrantOnlyGatedSmokeInput,
  ): Either[M9OfflineEvalRealBackendSpikeArtifactCaptureError, M9OfflineEvalCombinedGatedSmokeEvidenceIndex] =
    for {
      es <- M9OfflineEvalEsOnlyGatedSmokeEvidence.captureDefaultDisabled(esInput)
      qdrant <- M9OfflineEvalQdrantOnlyGatedSmokeEvidence.captureDefaultDisabled(qdrantInput)
    } yield build(
      input = M9OfflineEvalCombinedGatedSmokeEvidenceInput(esResult = es, qdrantResult = qdrant),
      markdownFilename = EsOnlyQdrantOnlyDefaultDisabledIndexFilename,
    )

  val EsOnlyQdrantOnlyNotConfiguredIndexFilename: String =
    "m9-combined-gated-smoke-evidence-index.md"
  val EsOnlyQdrantOnlyDefaultDisabledIndexFilename: String =
    "m9-combined-gated-smoke-evidence-index-default-disabled.md"
}
