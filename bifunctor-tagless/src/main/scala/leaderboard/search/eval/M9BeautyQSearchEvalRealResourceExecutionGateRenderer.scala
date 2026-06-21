package leaderboard.search.eval

/** Pure deterministic markdown renderer for the three real-resource execution gate DESIGN decisions.
  *
  * This is a reporting/rendering layer over the accepted ES-only, Qdrant-only, and combined ES/Qdrant
  * execution gate design contracts. It is a total pure function of the gate results: it never runs
  * Elasticsearch or Qdrant, never creates an ES or Qdrant client, never calls production
  * `/beauty-search`, and never touches a route, plugin, DI, or HTTP source. It renders the blocked/skip
  * state when prerequisites are incomplete and the pending-explicit-execution-task state when they are
  * complete; it never renders a success/quality-green/production-readiness/route-activation/serving-approval
  * state. Reaching pending-explicit-execution-task is design-state only, never approval to run: a future
  * execution still requires a separate explicit operator-approved task and saved evidence artifact
  * capture via the accepted schema/renderer. It preserves that default `/beauty-search` remains
  * ES-backed, that production activation and Qdrant production activation remain not approved, and that
  * no hybrid serving, fallback, score fusion, reranking, production telemetry, or route switch is
  * implemented.
  */
object M9BeautyQSearchEvalRealResourceExecutionGateRenderer {

  val ArtifactId: String = "m9-beautyq-real-resource-execution-gate"

  val ArtifactVersion: String = "v1"

  val Heading: String = "# M9 BeautyQ Real-Resource Execution Gate Decisions (Design Artifact)"

  /** Checked-in example artifact for the default/no-config gates: every gate blocked/skip. */
  val DefaultExampleFilename: String =
    "m9-beautyq-real-resource-execution-gate-default.md"

  /** A renderable bundle of the three accepted execution gate design results. */
  final case class M9BeautyQSearchEvalRealResourceExecutionGateArtifact(
    esOnly: M9BeautyQSearchEvalEsOnlyExecutionGateResult,
    qdrantOnly: M9BeautyQSearchEvalQdrantOnlyExecutionGateResult,
    combined: M9BeautyQSearchEvalCombinedExecutionGateResult,
  )

  /** Default/no-config artifact: all three gates blocked/skip (no resource config, no operator
    * approval). This is the checked-in example shape.
    */
  val DefaultArtifact: M9BeautyQSearchEvalRealResourceExecutionGateArtifact =
    M9BeautyQSearchEvalRealResourceExecutionGateArtifact(
      esOnly = M9BeautyQSearchEvalEsOnlyExecutionGateDesign.DefaultResult,
      qdrantOnly = M9BeautyQSearchEvalQdrantOnlyExecutionGateDesign.DefaultResult,
      combined = M9BeautyQSearchEvalCombinedExecutionGateDesign.DefaultResult,
    )

  /** Non-default showcase artifact: each gate is shown against the per-backend config that lets it reach
    * pending-explicit-execution-task. This is a synthetic design fixture (each gate uses its own
    * matching checkpoint); it renders pending design states only, never success.
    */
  val PendingArtifact: M9BeautyQSearchEvalRealResourceExecutionGateArtifact =
    M9BeautyQSearchEvalRealResourceExecutionGateArtifact(
      esOnly = M9BeautyQSearchEvalEsOnlyExecutionGateDesign.gateForEsOnlyConfig,
      qdrantOnly = M9BeautyQSearchEvalQdrantOnlyExecutionGateDesign.gateForQdrantOnlyConfig,
      combined = M9BeautyQSearchEvalCombinedExecutionGateDesign.gateForCombinedConfig,
    )

  def render(artifact: M9BeautyQSearchEvalRealResourceExecutionGateArtifact): String = {
    val builder = new StringBuilder

    line(builder, Heading)
    line(builder, "")
    line(builder, "This artifact is a design/planning rendering layer over the three real-resource execution gate decisions.")
    line(builder, "It renders blocked/skip or pending-explicit-execution-task design states only.")
    line(builder, "No real ES or Qdrant call is executed, implemented, or required.")
    line(builder, "")

    line(builder, "## Artifact identity")
    line(builder, "")
    line(builder, s"- artifact_id: $ArtifactId")
    line(builder, s"- artifact_version: $ArtifactVersion")
    line(builder, "")

    line(builder, "## Runbook consistency")
    line(builder, "")
    line(builder, s"- runbook_path: ${M9BeautyQSearchEvalRealResourceRunbookConsistency.RunbookPath}")
    line(builder, s"- schema_evidence_mode_count: ${M9BeautyQSearchEvalRealResourceRunbookConsistency.SchemaEvidenceModeCount}")
    line(builder, s"- mode_token_count_matches_schema: ${M9BeautyQSearchEvalRealResourceRunbookConsistency.RequiredEvidenceModeTokens.length == M9BeautyQSearchEvalRealResourceRunbookConsistency.SchemaEvidenceModeCount}")
    line(builder, s"- default_artifact_renders_blocked_skip_only: ${M9BeautyQSearchEvalRealResourceRunbookConsistency.DefaultArtifactRendersBlockedSkipOnly}")
    line(builder, "")

    line(builder, "## ES-only execution gate")
    line(builder, "")
    renderGate(builder, esOnlyView(artifact.esOnly))
    line(builder, "")

    line(builder, "## Qdrant-only execution gate")
    line(builder, "")
    renderGate(builder, qdrantOnlyView(artifact.qdrantOnly))
    line(builder, "")

    line(builder, "## Combined ES/Qdrant execution gate")
    line(builder, "")
    renderGate(builder, combinedView(artifact.combined))
    line(builder, "")

    line(builder, "## Standing production boundaries")
    line(builder, "")
    val c = artifact.combined
    line(builder, s"- default_beauty_search_es_backed: ${c.defaultBeautySearchEsBacked}")
    line(builder, s"- production_activation_approved: ${c.productionActivationApproved}")
    line(builder, s"- qdrant_production_activation_approved: ${c.qdrantProductionActivationApproved}")
    line(builder, s"- quality_green_claimed: ${c.qualityGreenClaimed}")
    line(builder, s"- production_readiness_claimed: ${c.productionReadinessClaimed}")
    line(builder, s"- route_activation_claimed: ${c.routeActivationClaimed}")
    line(builder, s"- serving_approval_claimed: ${c.servingApprovalClaimed}")
    line(builder, s"- es_client_created: ${c.esClientCreated}")
    line(builder, s"- qdrant_client_created: ${c.qdrantClientCreated}")
    line(builder, s"- production_beauty_search_called: ${c.productionBeautySearchCalled}")
    line(builder, s"- route_plugin_di_http_involved: ${c.routePluginDiHttpInvolved}")
    line(builder, s"- real_backend_call_implemented: ${c.realBackendCallImplemented}")
    line(builder, s"- real_backend_call_required: ${c.realBackendCallRequired}")
    line(builder, s"- hybrid_fallback_fusion_reranking_telemetry_route_switch_implemented: ${c.hybridFallbackFusionRerankingTelemetryRouteSwitchImplemented}")

    builder.result()
  }

  /** Primitive view of a single gate decision, so the three distinct result types share one renderer. */
  private final case class GateView(
    decision: String,
    pendingExplicitExecutionTask: Boolean,
    blocked: Boolean,
    checkpointDecision: String,
    prerequisitesComplete: Boolean,
    planned: Boolean,
    requiredEvidenceSchemaKind: String,
    requiredEvidenceCaptureArtifact: String,
    skipOrBlockReasons: List[String],
  )

  private def esOnlyView(result: M9BeautyQSearchEvalEsOnlyExecutionGateResult): GateView =
    GateView(
      decision = result.decision.render,
      pendingExplicitExecutionTask = result.pendingExplicitExecutionTask,
      blocked = result.blocked,
      checkpointDecision = result.checkpointDecision.render,
      prerequisitesComplete = result.esOnlyPrerequisitesComplete,
      planned = result.esOnlyPlanned,
      requiredEvidenceSchemaKind = result.requiredEvidenceSchemaKind.render,
      requiredEvidenceCaptureArtifact = result.requiredEvidenceCaptureArtifact,
      skipOrBlockReasons = result.reasons.map(_.render),
    )

  private def qdrantOnlyView(result: M9BeautyQSearchEvalQdrantOnlyExecutionGateResult): GateView =
    GateView(
      decision = result.decision.render,
      pendingExplicitExecutionTask = result.pendingExplicitExecutionTask,
      blocked = result.blocked,
      checkpointDecision = result.checkpointDecision.render,
      prerequisitesComplete = result.qdrantOnlyPrerequisitesComplete,
      planned = result.qdrantOnlyPlanned,
      requiredEvidenceSchemaKind = result.requiredEvidenceSchemaKind.render,
      requiredEvidenceCaptureArtifact = result.requiredEvidenceCaptureArtifact,
      skipOrBlockReasons = result.reasons.map(_.render),
    )

  private def combinedView(result: M9BeautyQSearchEvalCombinedExecutionGateResult): GateView =
    GateView(
      decision = result.decision.render,
      pendingExplicitExecutionTask = result.pendingExplicitExecutionTask,
      blocked = result.blocked,
      checkpointDecision = result.checkpointDecision.render,
      prerequisitesComplete = result.combinedPrerequisitesComplete,
      planned = result.combinedPlanned,
      requiredEvidenceSchemaKind = result.requiredEvidenceSchemaKind.render,
      requiredEvidenceCaptureArtifact = result.requiredEvidenceCaptureArtifact,
      skipOrBlockReasons = result.reasons.map(_.render),
    )

  private def renderGate(builder: StringBuilder, view: GateView): Unit = {
    line(builder, s"- gate_decision: ${view.decision}")
    line(builder, s"- pending_explicit_execution_task: ${view.pendingExplicitExecutionTask}")
    line(builder, s"- blocked: ${view.blocked}")
    line(builder, s"- checkpoint_decision: ${view.checkpointDecision}")
    line(builder, s"- prerequisites_complete: ${view.prerequisitesComplete}")
    line(builder, s"- planned: ${view.planned}")
    line(builder, s"- required_evidence_schema_kind: ${view.requiredEvidenceSchemaKind}")
    line(builder, s"- required_evidence_capture_artifact: ${view.requiredEvidenceCaptureArtifact}")
    line(builder, "- skip_or_block_reasons:")
    view.skipOrBlockReasons match {
      case Nil    => line(builder, "  - -")
      case values => values.foreach(v => line(builder, s"  - $v"))
    }
  }

  private def line(builder: StringBuilder, value: String): Unit = {
    builder.append(value)
    builder.append('\n')
  }
}
