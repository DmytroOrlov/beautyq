package leaderboard.search.eval

/** Pure deterministic markdown renderer for the real-resource smoke evidence SCHEMA artifact.
  *
  * This is a reporting/rendering layer over `M9BeautyQSearchEvalRealResourceSmokeEvidenceArtifact`. It
  * is a total pure function of the artifact value: it never runs Elasticsearch or Qdrant, never creates
  * an ES or Qdrant client, never calls production `/beauty-search`, and never touches a route, plugin,
  * DI, or HTTP source. It renders blocked/skip evidence for blocked prerequisites and pending-execution
  * evidence for complete prerequisites; it never claims quality green, production readiness, route
  * activation, serving approval, or Qdrant production activation, and it preserves that default
  * `/beauty-search` remains ES-backed and that no hybrid serving, fallback, score fusion, reranking,
  * production telemetry, or route switch is implemented.
  */
object M9BeautyQSearchEvalRealResourceSmokeEvidenceRenderer {

  val ArtifactId: String = "m9-beautyq-real-resource-smoke-evidence"

  val ArtifactVersion: String = "v1"

  val Heading: String = "# M9 BeautyQ Real-Resource Smoke Evidence (Schema Artifact)"

  /** Checked-in example artifact for the default/no-config audit: every target blocked/skip. */
  val DefaultExampleFilename: String =
    "m9-beautyq-real-resource-smoke-evidence-default.md"

  def render(artifact: M9BeautyQSearchEvalRealResourceSmokeEvidenceArtifact): String = {
    val builder = new StringBuilder

    line(builder, Heading)
    line(builder, "")
    line(builder, "This artifact is a saved-evidence schema rendering layer only.")
    line(builder, "It records the shape a future offline, resource-gated smoke run would fill.")
    line(builder, "No real ES or Qdrant call is executed, implemented, or required.")
    line(builder, "")

    line(builder, "## Artifact identity")
    line(builder, "")
    line(builder, s"- artifact_id: $ArtifactId")
    line(builder, s"- artifact_version: $ArtifactVersion")
    line(builder, s"- saved_evidence_schema_only: ${artifact.savedEvidenceSchemaOnly}")
    line(builder, s"- real_backend_call_execution_part_of_this_schema_task: ${artifact.realBackendCallExecutionPartOfThisSchemaTask}")
    line(builder, "")

    line(builder, "## Checkpoint decision")
    line(builder, "")
    line(builder, s"- required_checkpoint_decision: ${artifact.checkpointDecision.render}")
    line(builder, s"- eligible_for_resource_gated_smoke: ${artifact.checkpointDecision.eligibleForResourceGatedSmoke}")
    line(builder, "")

    line(builder, "## Static scorecard readiness")
    line(builder, "")
    line(builder, s"- static_scorecard_ready: ${artifact.staticScorecardReady}")
    line(builder, s"- static_scorecard_verdict: ${M9BeautyQSearchEvalStaticScorecard.VerdictDatasetStaticRowsReady}")
    line(builder, "")

    line(builder, "## ES-only evidence")
    line(builder, "")
    renderEsOnly(builder, artifact.esOnly)
    line(builder, "")

    line(builder, "## Qdrant-only evidence")
    line(builder, "")
    renderQdrantOnly(builder, artifact.qdrantOnly)
    line(builder, "")

    line(builder, "## Combined ES/Qdrant comparison evidence")
    line(builder, "")
    renderComparison(builder, artifact.esQdrantComparison)
    line(builder, "")

    line(builder, "## Standing production boundaries")
    line(builder, "")
    line(builder, s"- default_beauty_search_es_backed: ${artifact.defaultBeautySearchEsBacked}")
    line(builder, s"- production_activation_approved: ${artifact.productionActivationApproved}")
    line(builder, s"- qdrant_production_activation_approved: ${artifact.qdrantProductionActivationApproved}")
    line(builder, s"- quality_green_claimed: ${artifact.qualityGreenClaimed}")
    line(builder, s"- production_readiness_claimed: ${artifact.productionReadinessClaimed}")
    line(builder, s"- route_activation_claimed: ${artifact.routeActivationClaimed}")
    line(builder, s"- serving_approval_claimed: ${artifact.servingApprovalClaimed}")
    line(builder, s"- es_client_created: ${artifact.esClientCreated}")
    line(builder, s"- qdrant_client_created: ${artifact.qdrantClientCreated}")
    line(builder, s"- production_beauty_search_called: ${artifact.productionBeautySearchCalled}")
    line(builder, s"- route_plugin_di_http_involved: ${artifact.routePluginDiHttpInvolved}")
    line(builder, s"- hybrid_fallback_fusion_reranking_telemetry_route_switch_implemented: ${artifact.hybridFallbackFusionRerankingTelemetryRouteSwitchImplemented}")

    builder.result()
  }

  private def renderEsOnly(
    builder: StringBuilder,
    evidence: M9BeautyQSearchEvalRealResourceEsOnlySmokeEvidence,
  ): Unit = {
    renderCommon(builder, evidence)
    line(builder, s"- candidate_source: ${evidence.candidateSource.render}")
    line(builder, s"- serving_mode: ${evidence.servingMode.render}")
  }

  private def renderQdrantOnly(
    builder: StringBuilder,
    evidence: M9BeautyQSearchEvalRealResourceQdrantOnlySmokeEvidence,
  ): Unit = {
    renderCommon(builder, evidence)
    line(builder, s"- candidate_source: ${evidence.candidateSource.render}")
    line(builder, s"- serving_mode: ${evidence.servingMode.render}")
  }

  private def renderComparison(
    builder: StringBuilder,
    evidence: M9BeautyQSearchEvalRealResourceComparisonSmokeEvidence,
  ): Unit = {
    renderCommon(builder, evidence)
    line(builder, s"- es_candidate_source: ${evidence.esCandidateSource.render}")
    line(builder, s"- qdrant_candidate_source: ${evidence.qdrantCandidateSource.render}")
    line(builder, s"- es_execution_mode: ${evidence.esExecutionMode.render}")
    line(builder, s"- qdrant_execution_mode: ${evidence.qdrantExecutionMode.render}")
    line(builder, "- comparison_dimensions:")
    val shape = evidence.comparisonShape
    renderDimension(builder, "es_candidate_ids", shape.esCandidateIds)
    renderDimension(builder, "qdrant_candidate_ids", shape.qdrantCandidateIds)
    renderDimension(builder, "overlap_candidate_ids", shape.overlapCandidateIds)
    renderDimension(builder, "miss_candidate_ids", shape.missCandidateIds)
    renderDimension(builder, "unexpected_candidate_ids", shape.unexpectedCandidateIds)
    line(builder, s"  - missing_lookup_rate: ${renderRate(shape.missingLookupRate)}")
    renderDimension(builder, "comparison_notes", shape.comparisonNotes)
  }

  private def renderCommon(
    builder: StringBuilder,
    evidence: M9BeautyQSearchEvalRealResourceSmokeEvidence,
  ): Unit = {
    line(builder, s"- evidence_kind: ${evidence.artifactKind}")
    line(builder, s"- evidence_state: ${evidence.state.render}")
    line(builder, s"- blocked_or_skipped: ${evidence.blockedOrSkipped}")
    line(builder, s"- prerequisites_status: ${evidence.prerequisitesStatus.render}")
    line(builder, s"- prerequisites_complete: ${evidence.prerequisitesComplete}")
    line(builder, s"- required_checkpoint_decision: ${evidence.requiredCheckpointDecision.render}")
    line(builder, "- backend_modes:")
    listOrDash(builder, "  ", evidence.backendModes.map(_.render))
    line(builder, "- selected_query_anchors:")
    evidence.selectedQueryAnchors match {
      case Nil => line(builder, "  - -")
      case anchors =>
        anchors.foreach(a => line(builder, s"  - ${a.queryId}: ${a.queryClass.render}"))
    }
    line(builder, "- expected_evidence_files_or_sections:")
    listOrDash(builder, "  ", evidence.expectedEvidenceFilesOrSections)
    line(builder, "- skip_or_block_reasons:")
    listOrDash(builder, "  ", evidence.skipOrBlockReasons.map(_.render))
    line(builder, "- standing_boundaries:")
    listOrDash(builder, "  ", evidence.standingBoundaries.map(_.render))
    line(builder, "- validation_summary:")
    listOrDash(builder, "  ", evidence.validationSummary)
  }

  private def renderDimension(builder: StringBuilder, label: String, values: List[String]): Unit = {
    line(builder, s"  - $label:")
    listOrDash(builder, "    ", values)
  }

  private def renderRate(rate: Option[BigDecimal]): String =
    rate match {
      case Some(value) => value.toString
      case None        => "-"
    }

  private def listOrDash(builder: StringBuilder, indent: String, values: List[String]): Unit =
    values match {
      case Nil => line(builder, s"$indent- -")
      case _   => values.foreach(v => line(builder, s"$indent- $v"))
    }

  private def line(builder: StringBuilder, value: String): Unit = {
    builder.append(value)
    builder.append('\n')
  }
}
