package leaderboard.search.eval

object M9OfflineEvalCombinedGatedSmokeEvidenceRenderer {

  val Heading: String = "# M9 Combined ES+Qdrant Gated Smoke Evidence Index"

  def render(input: M9OfflineEvalCombinedGatedSmokeEvidenceInput): String = {
    val builder = new StringBuilder

    line(builder, Heading)
    line(builder, "")
    line(builder, "This artifact is an offline eval reporting/index layer only.")
    line(builder, "It summarizes ES-only and Qdrant-only gated smoke evidence artifacts.")
    line(builder, "No real backend calls are represented; no backend execution branch is added.")
    line(builder, "")
    line(builder, M9OfflineEvalCombinedGatedSmokeEvidenceIndex.ArtifactBoundaryNote)
    line(builder, "")

    line(builder, "## Boundaries")
    line(builder, "")
    line(builder, "- offline_eval_evidence_only: true")
    line(builder, "- not_production_telemetry: true")
    line(builder, "- not_activation_approval: true")
    line(builder, "- real_backend_call_implemented: false")
    line(builder, "- production_activation_not_approved_confirmed: true")
    line(builder, "- no_route_or_plugin_or_di_or_http_source_change: true")
    line(builder, "- no_hybrid_or_fallback_or_fusion_or_reranking: true")
    line(builder, "")

    line(builder, "## ES-only gated smoke evidence")
    line(builder, "")
    renderEsEntry(builder, input.esResult)
    line(builder, "")

    line(builder, "## Qdrant-only gated smoke evidence")
    line(builder, "")
    renderQdrantEntry(builder, input.qdrantResult)
    line(builder, "")

    line(builder, "## Pointers to checked-in per-backend artifacts")
    line(builder, "")
    line(builder, s"- es_artifact_filename: ${input.esResult.artifact.markdownArtifact.filename}")
    line(builder, s"- qdrant_artifact_filename: ${input.qdrantResult.artifact.markdownArtifact.filename}")
    line(builder, "")

    line(builder, "## Notes")
    line(builder, "")
    line(builder, "- reporting/index layer only")
    line(builder, "- no real backend call")
    line(builder, "- no production telemetry")
    line(builder, "- no activation claim")
    line(builder, "- explicit not-implemented policy declared in Boundaries")

    builder.result()
  }

  private def renderEsEntry(
    builder: StringBuilder,
    result: M9OfflineEvalEsOnlyGatedSmokeResult,
  ): Unit = {
    line(builder, s"- artifact_filename: ${result.artifact.markdownArtifact.filename}")
    line(builder, s"- es_source: ${result.esSource.render}")
    line(builder, s"- es_execution_mode: ${result.esExecutionMode.render}")
    line(builder, s"- es_serving_mode: ${result.esServingMode.render}")
    line(builder, s"- gate_status: ${result.gateDecision.status.render}")
    line(builder, s"- es_resource_config_present: ${result.resourceConfigPresent}")
    line(builder, s"- real_backend_call_implemented: ${result.realBackendCallImplemented}")
    line(builder, s"- production_activation_not_approved_confirmed: ${result.productionActivationNotApprovedConfirmed}")
    line(builder, s"- offline_eval_evidence_only: ${result.offlineEvalEvidenceOnly}")
    line(builder, s"- not_production_telemetry: ${result.notProductionTelemetry}")
    line(builder, s"- not_activation_approval: ${result.notActivationApproval}")
    renderWarnings(builder, "es_warnings", result.artifact.savedReport.warnings)
    renderNotes(builder, "es_notes", result.artifact.savedReport.notes)
  }

  private def renderQdrantEntry(
    builder: StringBuilder,
    result: M9OfflineEvalQdrantOnlyGatedSmokeResult,
  ): Unit = {
    line(builder, s"- artifact_filename: ${result.artifact.markdownArtifact.filename}")
    line(builder, s"- qdrant_source: ${result.qdrantSource.render}")
    line(builder, s"- qdrant_execution_mode: ${result.qdrantExecutionMode.render}")
    line(builder, s"- qdrant_serving_mode: ${result.qdrantServingMode.render}")
    line(builder, s"- gate_status: ${result.gateDecision.status.render}")
    line(builder, s"- qdrant_resource_config_present: ${result.resourceConfigPresent}")
    line(builder, s"- real_backend_call_implemented: ${result.realBackendCallImplemented}")
    line(builder, s"- production_activation_not_approved_confirmed: ${result.productionActivationNotApprovedConfirmed}")
    line(builder, s"- offline_eval_evidence_only: ${result.offlineEvalEvidenceOnly}")
    line(builder, s"- not_production_telemetry: ${result.notProductionTelemetry}")
    line(builder, s"- not_activation_approval: ${result.notActivationApproval}")
    renderWarnings(builder, "qdrant_warnings", result.artifact.savedReport.warnings)
    renderNotes(builder, "qdrant_notes", result.artifact.savedReport.notes)
  }

  private def renderWarnings(
    builder: StringBuilder,
    label: String,
    warnings: List[String],
  ): Unit = {
    line(builder, s"- $label:")
    warnings match {
      case Nil => line(builder, "  - -")
      case _   => warnings.foreach(w => line(builder, s"  - $w"))
    }
  }

  private def renderNotes(
    builder: StringBuilder,
    label: String,
    notes: List[String],
  ): Unit = {
    line(builder, s"- $label:")
    notes match {
      case Nil => line(builder, "  - -")
      case _   => notes.foreach(n => line(builder, s"  - $n"))
    }
  }

  private def line(builder: StringBuilder, value: String): Unit = {
    builder.append(value)
    builder.append('\n')
  }
}
