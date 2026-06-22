package leaderboard.search.eval

/** One named M12 BeautyQ fusion/reranking policy option in the offline experiment policy catalog.
  *
  * These are names and constraints only, not implemented scoring, fusion, or reranking algorithms.
  * A policy option is a schema-only placeholder for a future offline experiment that would consume
  * the accepted M12A input scaffold; none of these runs, scores, fuses, reranks, or executes a
  * backend. Every policy in this catalog is `executable = false` and produces no candidate ids,
  * provider ids, scores, ranks, backend responses, fused scores, reranked positions, or quality
  * labels.
  */
enum M12BeautyQSearchFusionRerankingPolicyOption {
  case EsBaselinePassthrough
  case QdrantBaselinePassthrough
  case CombinedUnionPlaceholder
  case CombinedIntersectionPlaceholder
  case TieBreakerPlaceholder
  case AcceptedNegativeControlExclusionPolicy

  def render: String =
    this match {
      case M12BeautyQSearchFusionRerankingPolicyOption.EsBaselinePassthrough                   => "es_baseline_passthrough"
      case M12BeautyQSearchFusionRerankingPolicyOption.QdrantBaselinePassthrough               => "qdrant_baseline_passthrough"
      case M12BeautyQSearchFusionRerankingPolicyOption.CombinedUnionPlaceholder                => "combined_union_placeholder"
      case M12BeautyQSearchFusionRerankingPolicyOption.CombinedIntersectionPlaceholder         => "combined_intersection_placeholder"
      case M12BeautyQSearchFusionRerankingPolicyOption.TieBreakerPlaceholder                   => "tie_breaker_placeholder"
      case M12BeautyQSearchFusionRerankingPolicyOption.AcceptedNegativeControlExclusionPolicy  => "accepted_negative_control_exclusion_policy"
    }

  /** Whether this policy option is executable in any form. Every catalog option is non-executable. */
  def isExecutable: Boolean = false

  /** Whether this policy option is a backend-baseline (ES-only or Qdrant-only) placeholder. */
  def isBackendBaseline: Boolean =
    this match {
      case M12BeautyQSearchFusionRerankingPolicyOption.EsBaselinePassthrough     => true
      case M12BeautyQSearchFusionRerankingPolicyOption.QdrantBaselinePassthrough => true
      case _                                                                     => false
    }

  /** Whether this policy option is a combined-experiment placeholder (union, intersection, tie-breaker). */
  def isCombinedExperiment: Boolean =
    this match {
      case M12BeautyQSearchFusionRerankingPolicyOption.CombinedUnionPlaceholder        => true
      case M12BeautyQSearchFusionRerankingPolicyOption.CombinedIntersectionPlaceholder => true
      case M12BeautyQSearchFusionRerankingPolicyOption.TieBreakerPlaceholder           => true
      case _                                                                           => false
    }

  /** Whether this policy option is an exclusion policy (no backend candidate row). */
  def isExclusion: Boolean =
    this == M12BeautyQSearchFusionRerankingPolicyOption.AcceptedNegativeControlExclusionPolicy
}

object M12BeautyQSearchFusionRerankingPolicyOption {

  /** Stable policy-catalog order: backend baselines first, combined experiment placeholders, exclusion. */
  val stableOrder: List[M12BeautyQSearchFusionRerankingPolicyOption] = List(
    EsBaselinePassthrough,
    QdrantBaselinePassthrough,
    CombinedUnionPlaceholder,
    CombinedIntersectionPlaceholder,
    TieBreakerPlaceholder,
    AcceptedNegativeControlExclusionPolicy,
  )

  /** Total, deterministic mapping from an M12A input group to the future policy option the catalog
    * would plan for offline experiments.
    *
    * The three backend input groups map to their dedicated baseline or combined experiment placeholders;
    * the two exclusion input groups carry no backend candidate row and so the catalog only assigns the
    * accepted negative-control exclusion policy. Manual/no-op exclusion input has no policy row at all.
    */
  def optionsForInputGroup(
    group: M12BeautyQSearchFusionRerankingInputGroup
  ): List[M12BeautyQSearchFusionRerankingPolicyOption] =
    group match {
      case M12BeautyQSearchFusionRerankingInputGroup.EsOnlyPlaceholderInput =>
        List(EsBaselinePassthrough)
      case M12BeautyQSearchFusionRerankingInputGroup.QdrantOnlyPlaceholderInput =>
        List(QdrantBaselinePassthrough)
      case M12BeautyQSearchFusionRerankingInputGroup.CombinedComparisonPlaceholderInput =>
        List(CombinedUnionPlaceholder, CombinedIntersectionPlaceholder, TieBreakerPlaceholder)
      case M12BeautyQSearchFusionRerankingInputGroup.AcceptedNegativeControlExclusionInput =>
        List(AcceptedNegativeControlExclusionPolicy)
      case M12BeautyQSearchFusionRerankingInputGroup.ManualOrNoOpExclusionInput =>
        Nil
    }
}

final case class M12BeautyQSearchFusionRerankingPolicyCatalogMetric(
  name: String,
  value: String,
)

final case class M12BeautyQSearchFusionRerankingPolicyCatalogSummary(
  datasetId: String,
  consumedM12InputScaffoldVerdict: String,
  consumedM11ResultSchemaVerdict: String,
  consumedM11BoundaryFailureMatrixVerdict: String,
  policyNames: List[M12BeautyQSearchFusionRerankingPolicyOption],
  policyCount: Int,
  policyBackendBaselineCount: Int,
  policyCombinedExperimentCount: Int,
  policyExclusionCount: Int,
  consumedInputRowCount: Int,
  esBaselineRows: Int,
  qdrantBaselineRows: Int,
  combinedExperimentRows: Int,
  acceptedNegativeControlExclusionRows: Int,
  manualOrNoOpRows: Int,
  executablePolicyRows: Int,
  realScoredOrRerankedRows: Int,
  m12FusionRerankingPolicyCatalogReady: Boolean,
  boundary: M10BeautyQSearchOfflineRoutingBoundary,
  verdict: String,
  metrics: List[M12BeautyQSearchFusionRerankingPolicyCatalogMetric],
)

/** M12 BeautyQ fusion/reranking policy catalog contract: a pure offline catalog of named future policy
  * options for M12 BeautyQ fusion/reranking experiments.
  *
  * This is a policy-catalog-only contract: it defines policy names and constraints, never scoring,
  * fusion, reranking, candidate retrieval, fallback, telemetry, backend execution, or production
  * routing. The catalog is consumed by the M12 experiment-plan schema
  * ([[M12BeautyQSearchFusionRerankingExperimentPlan]]); it does not run, score, fuse, rerank, or call
  * any backend. It never calls production `/beauty-search`, never creates an ES or Qdrant client,
  * never runs Elasticsearch or Qdrant, and never touches a route, plugin, DI, or HTTP source. It
  * fabricates no candidate ids, provider ids, scores, ranks, backend responses, fused scores,
  * reranked positions, or quality labels.
  *
  * The readiness it reports is M12 *offline policy-catalog (schema-only)* readiness only. It claims no
  * scoring readiness, no fusion/reranking execution readiness, no retrieval quality, no production
  * readiness, no route activation, and no serving approval. Default `/beauty-search` stays ES-backed,
  * the Qdrant opt-in route stays disabled by default, and Qdrant production activation stays not
  * approved.
  */
object M12BeautyQSearchFusionRerankingPolicyCatalog {

  val MarkdownFilename: String =
    "m12-beautyq-fusion-reranking-policy-catalog.md"

  /** Readiness verdict: M12 offline policy-catalog readiness only, schema-only. Deliberately carries no
    * scoring readiness, fusion/reranking execution readiness, retrieval-quality, production-readiness,
    * route-activation, or serving-approval claim.
    */
  val Verdict: String =
    "m12_fusion_reranking_policy_catalog_ready_schema_only"

  /** Accepted M12A input-scaffold verdict this catalog consumes. */
  val ConsumedM12InputScaffoldVerdict: String =
    M12BeautyQSearchFusionRerankingInputScaffold.Verdict

  /** Accepted M11B result-schema verdict carried forward through the consumed M12A scaffold. */
  val ConsumedM11ResultSchemaVerdict: String =
    M12BeautyQSearchFusionRerankingInputScaffold.ConsumedM11ResultSchemaVerdict

  /** Accepted M11C boundary/failure matrix verdict carried forward through the consumed M12A scaffold. */
  val ConsumedM11BoundaryFailureMatrixVerdict: String =
    M12BeautyQSearchFusionRerankingInputScaffold.ConsumedM11BoundaryFailureMatrixVerdict

  /** The full policy catalog in stable order; this is the canonical list of named policy options. */
  val Policies: List[M12BeautyQSearchFusionRerankingPolicyOption] =
    M12BeautyQSearchFusionRerankingPolicyOption.stableOrder

  val PolicyCount: Int = Policies.size

  val BackendBaselinePolicyCount: Int = Policies.count(_.isBackendBaseline)
  val CombinedExperimentPolicyCount: Int = Policies.count(_.isCombinedExperiment)
  val ExclusionPolicyCount: Int = Policies.count(_.isExclusion)

  val DefaultSummary: M12BeautyQSearchFusionRerankingPolicyCatalogSummary =
    build()

  val MarkdownArtifact: M9OfflineEvalReportArtifact =
    M12BeautyQSearchFusionRerankingPolicyCatalogRenderer.markdownArtifact(
      filename = MarkdownFilename,
      summary = DefaultSummary,
    )

  def build(): M12BeautyQSearchFusionRerankingPolicyCatalogSummary = {
    val consumedInputRowCount =
      M12BeautyQSearchFusionRerankingInputScaffold.InputEnvelopes.size

    val esBaselineRows =
      M12BeautyQSearchFusionRerankingInputScaffold.InputEnvelopes.count { envelope =>
        envelope.inputGroup == M12BeautyQSearchFusionRerankingInputGroup.EsOnlyPlaceholderInput
      }
    val qdrantBaselineRows =
      M12BeautyQSearchFusionRerankingInputScaffold.InputEnvelopes.count { envelope =>
        envelope.inputGroup == M12BeautyQSearchFusionRerankingInputGroup.QdrantOnlyPlaceholderInput
      }
    val combinedExperimentRows =
      M12BeautyQSearchFusionRerankingInputScaffold.InputEnvelopes.count { envelope =>
        envelope.inputGroup == M12BeautyQSearchFusionRerankingInputGroup.CombinedComparisonPlaceholderInput
      }
    val acceptedNegativeControlExclusionRows =
      M12BeautyQSearchFusionRerankingInputScaffold.InputEnvelopes.count { envelope =>
        envelope.inputGroup ==
          M12BeautyQSearchFusionRerankingInputGroup.AcceptedNegativeControlExclusionInput
      }
    val manualOrNoOpRows =
      M12BeautyQSearchFusionRerankingInputScaffold.InputEnvelopes.count { envelope =>
        envelope.inputGroup == M12BeautyQSearchFusionRerankingInputGroup.ManualOrNoOpExclusionInput
      }

    // A schema-only policy catalog has no executable policy row and produces no scored/reranked rows.
    val executablePolicyRows = 0
    val realScoredOrRerankedRows = 0

    // The catalog is ready when its policy names are unique, every policy is non-executable, the
    // consumed M12A input scaffold totals 63, and the backend baseline / combined experiment /
    // exclusion plan counts are well-formed.
    val m12FusionRerankingPolicyCatalogReady =
      Policies.map(_.render).distinct.size == PolicyCount &&
        Policies.forall(!_.isExecutable) &&
        consumedInputRowCount == 63 &&
        esBaselineRows == 13 &&
        qdrantBaselineRows == 1 &&
        combinedExperimentRows == 48 &&
        acceptedNegativeControlExclusionRows == 1 &&
        manualOrNoOpRows == 0 &&
        executablePolicyRows == 0 &&
        realScoredOrRerankedRows == 0 &&
        esBaselineRows + qdrantBaselineRows + combinedExperimentRows +
          acceptedNegativeControlExclusionRows + manualOrNoOpRows == 63

    val summaryWithoutMetrics = M12BeautyQSearchFusionRerankingPolicyCatalogSummary(
      datasetId = M9BeautyQSearchEvalQueryDataset.Metadata.datasetId,
      consumedM12InputScaffoldVerdict = ConsumedM12InputScaffoldVerdict,
      consumedM11ResultSchemaVerdict = ConsumedM11ResultSchemaVerdict,
      consumedM11BoundaryFailureMatrixVerdict = ConsumedM11BoundaryFailureMatrixVerdict,
      policyNames = Policies,
      policyCount = PolicyCount,
      policyBackendBaselineCount = BackendBaselinePolicyCount,
      policyCombinedExperimentCount = CombinedExperimentPolicyCount,
      policyExclusionCount = ExclusionPolicyCount,
      consumedInputRowCount = consumedInputRowCount,
      esBaselineRows = esBaselineRows,
      qdrantBaselineRows = qdrantBaselineRows,
      combinedExperimentRows = combinedExperimentRows,
      acceptedNegativeControlExclusionRows = acceptedNegativeControlExclusionRows,
      manualOrNoOpRows = manualOrNoOpRows,
      executablePolicyRows = executablePolicyRows,
      realScoredOrRerankedRows = realScoredOrRerankedRows,
      m12FusionRerankingPolicyCatalogReady = m12FusionRerankingPolicyCatalogReady,
      boundary = M12BeautyQSearchFusionRerankingInputScaffold.DefaultSummary.boundary,
      verdict = Verdict,
      metrics = Nil,
    )

    summaryWithoutMetrics.copy(metrics = metrics(summaryWithoutMetrics))
  }

  private def metrics(
    summary: M12BeautyQSearchFusionRerankingPolicyCatalogSummary
  ): List[M12BeautyQSearchFusionRerankingPolicyCatalogMetric] = {
    val b = summary.boundary
    List(
      metric("consumed_m12a_input_scaffold_verdict", summary.consumedM12InputScaffoldVerdict),
      metric("consumed_m11b_result_schema_verdict", summary.consumedM11ResultSchemaVerdict),
      metric("consumed_m11c_boundary_failure_matrix_verdict", summary.consumedM11BoundaryFailureMatrixVerdict),
      metric("consumed_input_row_count", summary.consumedInputRowCount.toString),
      metric("policy_count", summary.policyCount.toString),
      metric("policy_names_unique", summary.policyNames.map(_.render).distinct.size.toString),
      metric("policy_backend_baseline_count", summary.policyBackendBaselineCount.toString),
      metric("policy_combined_experiment_count", summary.policyCombinedExperimentCount.toString),
      metric("policy_exclusion_count", summary.policyExclusionCount.toString),
      metric("es_baseline_rows", summary.esBaselineRows.toString),
      metric("qdrant_baseline_rows", summary.qdrantBaselineRows.toString),
      metric("combined_experiment_rows", summary.combinedExperimentRows.toString),
      metric("accepted_negative_control_exclusion_rows", summary.acceptedNegativeControlExclusionRows.toString),
      metric("manual_or_no_op_rows", summary.manualOrNoOpRows.toString),
      metric("executable_policy_rows", summary.executablePolicyRows.toString),
      metric("real_scored_or_reranked_rows", summary.realScoredOrRerankedRows.toString),
      metric("policy_plan_count_sum", (summary.esBaselineRows + summary.qdrantBaselineRows +
        summary.combinedExperimentRows + summary.acceptedNegativeControlExclusionRows +
        summary.manualOrNoOpRows).toString),
      metric("m12_fusion_reranking_policy_catalog_ready", summary.m12FusionRerankingPolicyCatalogReady.toString),
      metric("m12_catalog_is_schema_only_not_scoring", true.toString),
      metric("m12_catalog_is_schema_only_not_fusion", true.toString),
      metric("m12_catalog_is_schema_only_not_reranking", true.toString),
      metric("m12_catalog_is_schema_only_not_candidate_retrieval", true.toString),
      metric("m12_catalog_is_schema_only_not_backend_execution", true.toString),
      metric("m12_catalog_is_offline_not_production_routing", true.toString),
      metric("m12_combined_experiment_policies_are_placeholders_only", true.toString),
      metric("m12_combined_experiment_policies_do_not_imply_hybrid_serving", true.toString),
      metric("accepted_negative_control_exclusion_has_no_backend_candidate_policy", true.toString),
      metric("manual_and_no_op_exclusions_have_no_backend_candidate_policy", true.toString),
      metric("no_real_candidate_ids_provider_ids_scores_ranks_fabricated", true.toString),
      metric("no_real_backend_responses_fabricated", true.toString),
      metric("no_fused_scores_reranked_positions_quality_labels_fabricated", true.toString),
      metric("no_fusion_outputs_reranking_outputs_fabricated", true.toString),
      metric("default_beauty_search_es_backed", b.defaultBeautySearchEsBacked.toString),
      metric("qdrant_opt_in_disabled_by_default", b.qdrantOptInDisabledByDefault.toString),
      metric("qdrant_production_activation_approved", b.qdrantProductionActivationApproved.toString),
      metric("production_route_activated", b.productionRouteActivated.toString),
      metric("default_route_switched", b.defaultRouteSwitched.toString),
      metric("production_beauty_search_called", b.productionBeautySearchCalled.toString),
      metric("es_client_created", b.esClientCreated.toString),
      metric("qdrant_client_created", b.qdrantClientCreated.toString),
      metric("es_executed", b.esExecuted.toString),
      metric("qdrant_executed", b.qdrantExecuted.toString),
      metric("route_plugin_di_http_involved", b.routePluginDiHttpInvolved.toString),
      metric("real_backend_call_required", b.realBackendCallRequired.toString),
      metric("real_backend_call_implemented", b.realBackendCallImplemented.toString),
      metric("hybrid_serving_implied", b.hybridServingImplied.toString),
      metric("fallback_implied", b.fallbackImplied.toString),
      metric("score_fusion_implied", b.scoreFusionImplied.toString),
      metric("reranking_implied", b.rerankingImplied.toString),
      metric("production_telemetry_implied", b.productionTelemetryImplied.toString),
      metric("quality_green_claimed", b.qualityGreenClaimed.toString),
      metric("production_readiness_claimed", b.productionReadinessClaimed.toString),
      metric("route_activation_claimed", b.routeActivationClaimed.toString),
      metric("serving_approval_claimed", b.servingApprovalClaimed.toString),
      metric("verdict", summary.verdict),
    )
  }

  private def metric(
    name: String,
    value: String,
  ): M12BeautyQSearchFusionRerankingPolicyCatalogMetric =
    M12BeautyQSearchFusionRerankingPolicyCatalogMetric(name, value)
}

object M12BeautyQSearchFusionRerankingPolicyCatalogRenderer {

  def renderMarkdown(summary: M12BeautyQSearchFusionRerankingPolicyCatalogSummary): String = {
    val builder = new StringBuilder

    line(builder, "# M12 BeautyQ Fusion/Reranking Policy Catalog")
    line(builder, "")
    line(builder, "Offline fusion/reranking policy catalog and experiment-plan schema over the accepted M12A")
    line(builder, "fusion/reranking input scaffold. This is an offline planning/eval artifact only. The policy catalog")
    line(builder, "below defines named future policy options and the experiment plan assigns one schema-only")
    line(builder, "experiment-plan row per consumed M12A input envelope. It is a schema-only policy catalog and")
    line(builder, "experiment-plan artifact and is NOT scoring, NOT fusion, NOT reranking, NOT backend execution,")
    line(builder, "and NOT production routing: no ES or Qdrant client is created and neither backend is run, and")
    line(builder, "no candidate ids, provider ids, scores, ranks, backend responses, fused scores, reranked")
    line(builder, "positions, quality labels, fusion outputs, or reranking outputs are fabricated. This artifact")
    line(builder, "reports M12 offline policy-catalog and experiment-plan readiness only (schema-only): it is not")
    line(builder, "scoring readiness, not fusion/reranking execution readiness, not retrieval quality, not production")
    line(builder, "readiness, not route activation, and not serving approval.")
    line(builder, "")
    line(builder, "## Summary")
    line(builder, "")
    line(builder, s"- dataset_id: ${renderText(summary.datasetId)}")
    line(builder, s"- verdict: ${renderText(summary.verdict)}")
    line(builder, s"- consumed_m12a_input_scaffold_verdict: ${renderText(summary.consumedM12InputScaffoldVerdict)}")
    line(builder, s"- consumed_m11b_result_schema_verdict: ${renderText(summary.consumedM11ResultSchemaVerdict)}")
    line(builder, s"- consumed_m11c_boundary_failure_matrix_verdict: ${renderText(summary.consumedM11BoundaryFailureMatrixVerdict)}")
    line(builder, s"- consumed_input_row_count: ${summary.consumedInputRowCount}")
    line(builder, s"- policy_count: ${summary.policyCount}")
    line(builder, s"- es_baseline_rows: ${summary.esBaselineRows}")
    line(builder, s"- qdrant_baseline_rows: ${summary.qdrantBaselineRows}")
    line(builder, s"- combined_experiment_rows: ${summary.combinedExperimentRows}")
    line(builder, s"- accepted_negative_control_exclusion_rows: ${summary.acceptedNegativeControlExclusionRows}")
    line(builder, s"- manual_or_no_op_rows: ${summary.manualOrNoOpRows}")
    line(builder, s"- executable_policy_rows: ${summary.executablePolicyRows}")
    line(builder, s"- real_scored_or_reranked_rows: ${summary.realScoredOrRerankedRows}")
    line(builder, s"- m12_fusion_reranking_policy_catalog_ready: ${summary.m12FusionRerankingPolicyCatalogReady}")
    line(builder, "")
    line(builder, "## Policy catalog names")
    line(builder, "")
    line(builder, "The catalog is names and constraints only, not implemented scoring, fusion, or reranking")
    line(builder, "algorithms. Every policy option is non-executable and produces no candidate ids, provider ids,")
    line(builder, "scores, ranks, backend responses, fused scores, reranked positions, or quality labels.")
    line(builder, "")
    line(builder, "| policy_option | role |")
    line(builder, "|---|---|")
    summary.policyNames.foreach { policy =>
      val role =
        if (policy.isBackendBaseline) "backend_baseline"
        else if (policy.isCombinedExperiment) "combined_experiment_placeholder"
        else if (policy.isExclusion) "exclusion_policy"
        else "schema_only_placeholder"
      line(builder, s"| ${renderText(policy.render)} | $role |")
    }
    line(builder, "")
    line(builder, "## Experiment plan group counts")
    line(builder, "")
    line(builder, "Each consumed M12A input envelope becomes one schema-only experiment-plan row. Backend baseline")
    line(builder, "rows plan a dedicated future ES or Qdrant baseline passthrough policy; combined experiment rows")
    line(builder, "plan the three combined future placeholders (union, intersection, tie-breaker); the accepted")
    line(builder, "negative-control exclusion plans the exclusion policy; manual/no-op rows plan no policy row at")
    line(builder, "all. No row is executable and no real scored/reranked result is produced.")
    line(builder, "")
    line(builder, "| experiment_plan_group | count |")
    line(builder, "|---|---|")
    line(builder, s"| es_baseline_rows | ${summary.esBaselineRows} |")
    line(builder, s"| qdrant_baseline_rows | ${summary.qdrantBaselineRows} |")
    line(builder, s"| combined_experiment_rows | ${summary.combinedExperimentRows} |")
    line(builder, s"| accepted_negative_control_exclusion_rows | ${summary.acceptedNegativeControlExclusionRows} |")
    line(builder, s"| manual_or_no_op_rows | ${summary.manualOrNoOpRows} |")
    line(builder, s"| executable_policy_rows | ${summary.executablePolicyRows} |")
    line(builder, s"| real_scored_or_reranked_rows | ${summary.realScoredOrRerankedRows} |")
    line(builder, "")
    line(builder, "## q_noise_004 and q_noise_005 mappings")
    line(builder, "")
    line(builder, "Both ids share the q_noise_* prefix yet land in different M12 plan rows: q_noise_004 = gel removal")
    line(builder, "maps to a combined placeholder experiment-plan row (combined_union_placeholder,")
    line(builder, "combined_intersection_placeholder, tie_breaker_placeholder, all non-executable); q_noise_005 =")
    line(builder, "lifting maps to the accepted_negative_control_exclusion_policy row with no backend candidate")
    line(builder, "policy and no real scored/reranked output.")
    line(builder, "")
    line(builder, "| query_id | experiment_plan_group | assigned_policies | executable | real_scored_or_reranked |")
    line(builder, "|---|---|---|---|---|")
    line(builder, "| q_noise_004 | combined_experiment_rows | combined_union_placeholder, combined_intersection_placeholder, tie_breaker_placeholder | false | false |")
    line(builder, "| q_noise_005 | accepted_negative_control_exclusion_rows | accepted_negative_control_exclusion_policy | false | false |")
    line(builder, "")
    line(builder, "## Metrics")
    line(builder, "")
    line(builder, "| metric | value |")
    line(builder, "|---|---|")
    summary.metrics.foreach(metric => line(builder, s"| ${renderText(metric.name)} | ${renderText(metric.value)} |"))
    line(builder, "")
    line(builder, "## Boundary summary")
    line(builder, "")
    line(builder, "Boundary posture is reported as structured booleans in the Metrics table above, not as prose negations.")
    line(builder, "This is a schema-only policy catalog and experiment-plan artifact, not scoring, not fusion")
    line(builder, "execution, not reranking execution, not backend execution, and not production routing: no")
    line(builder, "candidate ids, provider ids, scores, ranks, backend responses, fused scores, reranked positions,")
    line(builder, "quality labels, fusion outputs, or reranking outputs are fabricated, and combined experiment")
    line(builder, "placeholders do not imply production hybrid serving.")

    builder.result()
  }

  def markdownArtifact(
    filename: String,
    summary: M12BeautyQSearchFusionRerankingPolicyCatalogSummary,
  ): M9OfflineEvalReportArtifact =
    M9OfflineEvalReportArtifact(
      filename = filename,
      contentType = "text/markdown; charset=utf-8",
      contents = renderMarkdown(summary),
    )

  private def renderText(value: String): String =
    value
      .replace("\r\n", " ")
      .replace('\n', ' ')
      .replace('\r', ' ')
      .replace("|", "\\|")

  private def line(builder: StringBuilder, value: String): Unit = {
    builder.append(value)
    builder.append('\n')
  }
}
