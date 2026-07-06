package leaderboard.search.eval

/** Decision a single M12 fusion/reranking boundary/failure matrix row records.
  *
  * These are data-only verdicts about *policy-handling* cases, never scoring, fusion, reranking, or
  * backend execution. `Accepted` marks a policy-handling case the M12B policy catalog and experiment-plan
  * schema is allowed to carry; `Denied` marks a case the matrix must refuse (an executable/scored/reranked
  * policy drift, a fabricated artifact, or a production/serving/hybrid claim); `Skipped` is reserved for
  * handling cases that are neither accepted nor denied. No row runs ES or Qdrant, creates a client, or
  * touches a route, plugin, DI, or HTTP source.
  */
enum M12BeautyQSearchFusionRerankingMatrixDecision {
  case Accepted
  case Denied
  case Skipped

  def render: String =
    this match {
      case M12BeautyQSearchFusionRerankingMatrixDecision.Accepted => "accepted"
      case M12BeautyQSearchFusionRerankingMatrixDecision.Denied   => "denied"
      case M12BeautyQSearchFusionRerankingMatrixDecision.Skipped  => "skipped"
    }
}

/** Stable reason code attached to one M12 fusion/reranking boundary/failure matrix row.
  *
  * Reason codes are structured metric keys, deliberately preferred over long negated prose. Each code is
  * a data-only label for one policy-handling case; none implies scoring, fusion, reranking, a backend
  * call, a production route, or any quality/readiness/serving claim.
  */
enum M12BeautyQSearchFusionRerankingMatrixReasonCode {
  // Accepted baseline policy-handling cases.
  case EsBaselinePassthroughPlaceholderAccepted
  case QdrantBaselinePassthroughPlaceholderAccepted
  case CombinedUnionPlaceholderPolicyAccepted
  case CombinedIntersectionPlaceholderPolicyAccepted
  case TieBreakerPlaceholderPolicyAccepted
  case AcceptedNegativeControlExclusionPolicyAccepted
  case ManualOrNoOpZeroCountHandlingAccepted

  // Denied executable / scored / reranked policy drift.
  case ExecutablePolicyDriftDenied
  case RealScoredOrRerankedRowDriftDenied

  // Denied fabricated policy-handling artifacts.
  case FabricatedCandidateIdDenied
  case FabricatedProviderIdDenied
  case FabricatedScoreDenied
  case FabricatedRankDenied
  case FabricatedBackendResponseDenied
  case FabricatedFusedScoreDenied
  case FabricatedRerankedPositionDenied
  case FabricatedQualityLabelDenied
  case FabricatedFusionOutputDenied
  case FabricatedRerankingOutputDenied

  // Denied backend execution / client / route / plugin / DI / HTTP involvement.
  case BackendResultLegExecutedDenied
  case EsClientCreatedDenied
  case QdrantClientCreatedDenied
  case EsExecutedDenied
  case QdrantExecutedDenied
  case ProductionRouteOrBeautySearchCalledDenied
  case RoutePluginDiHttpInvolvedDenied

  // Denied production activation / route switch / Qdrant activation / hybrid / fallback / fusion /
  // reranking / telemetry / readiness / approval drift.
  case ProductionRouteSwitchOrDefaultSwitchDenied
  case QdrantProductionActivationApprovedDenied
  case HybridServingImplementedOrClaimedDenied
  case FallbackImplementedOrClaimedDenied
  case ProductionTelemetryImplementedOrClaimedDenied
  case QualityGreenClaimedDenied
  case RetrievalQualityClaimedDenied
  case ProductionReadinessClaimedDenied
  case RouteActivationClaimedDenied
  case ServingApprovalClaimedDenied

  def render: String =
    this match {
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.EsBaselinePassthroughPlaceholderAccepted       => "es_baseline_passthrough_placeholder_accepted"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.QdrantBaselinePassthroughPlaceholderAccepted   => "qdrant_baseline_passthrough_placeholder_accepted"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.CombinedUnionPlaceholderPolicyAccepted        => "combined_union_placeholder_policy_accepted"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.CombinedIntersectionPlaceholderPolicyAccepted => "combined_intersection_placeholder_policy_accepted"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.TieBreakerPlaceholderPolicyAccepted           => "tie_breaker_placeholder_policy_accepted"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.AcceptedNegativeControlExclusionPolicyAccepted =>
        "accepted_negative_control_exclusion_policy_accepted"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.ManualOrNoOpZeroCountHandlingAccepted          => "manual_or_no_op_zero_count_handling_accepted"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.ExecutablePolicyDriftDenied                    => "executable_policy_drift_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.RealScoredOrRerankedRowDriftDenied            => "real_scored_or_reranked_row_drift_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.FabricatedCandidateIdDenied                    => "fabricated_candidate_id_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.FabricatedProviderIdDenied                     => "fabricated_provider_id_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.FabricatedScoreDenied                          => "fabricated_score_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.FabricatedRankDenied                           => "fabricated_rank_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.FabricatedBackendResponseDenied                => "fabricated_backend_response_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.FabricatedFusedScoreDenied                     => "fabricated_fused_score_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.FabricatedRerankedPositionDenied               => "fabricated_reranked_position_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.FabricatedQualityLabelDenied                    => "fabricated_quality_label_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.FabricatedFusionOutputDenied                   => "fabricated_fusion_output_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.FabricatedRerankingOutputDenied                => "fabricated_reranking_output_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.BackendResultLegExecutedDenied                 => "backend_result_leg_executed_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.EsClientCreatedDenied                          => "es_client_created_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.QdrantClientCreatedDenied                      => "qdrant_client_created_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.EsExecutedDenied                               => "es_executed_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.QdrantExecutedDenied                           => "qdrant_executed_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.ProductionRouteOrBeautySearchCalledDenied      => "production_route_or_beauty_search_called_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.RoutePluginDiHttpInvolvedDenied               => "route_plugin_di_http_involved_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.ProductionRouteSwitchOrDefaultSwitchDenied     => "production_route_switch_or_default_switch_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.QdrantProductionActivationApprovedDenied      => "qdrant_production_activation_approved_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.HybridServingImplementedOrClaimedDenied        => "hybrid_serving_implemented_or_claimed_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.FallbackImplementedOrClaimedDenied             => "fallback_implemented_or_claimed_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.ProductionTelemetryImplementedOrClaimedDenied  => "production_telemetry_implemented_or_claimed_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.QualityGreenClaimedDenied                      => "quality_green_claimed_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.RetrievalQualityClaimedDenied                  => "retrieval_quality_claimed_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.ProductionReadinessClaimedDenied               => "production_readiness_claimed_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.RouteActivationClaimedDenied                   => "route_activation_claimed_denied"
      case M12BeautyQSearchFusionRerankingMatrixReasonCode.ServingApprovalClaimedDenied                   => "serving_approval_claimed_denied"
    }
}

object M12BeautyQSearchFusionRerankingMatrixReasonCode {

  /** Stable reason-code order: accepted baseline codes, then denied executable/scored/reranked drift,
    * fabrication, execution/client/route boundary, and production/hybrid/fallback/telemetry/readiness
    * codes.
    */
  val stableOrder: List[M12BeautyQSearchFusionRerankingMatrixReasonCode] = List(
    EsBaselinePassthroughPlaceholderAccepted,
    QdrantBaselinePassthroughPlaceholderAccepted,
    CombinedUnionPlaceholderPolicyAccepted,
    CombinedIntersectionPlaceholderPolicyAccepted,
    TieBreakerPlaceholderPolicyAccepted,
    AcceptedNegativeControlExclusionPolicyAccepted,
    ManualOrNoOpZeroCountHandlingAccepted,
    ExecutablePolicyDriftDenied,
    RealScoredOrRerankedRowDriftDenied,
    FabricatedCandidateIdDenied,
    FabricatedProviderIdDenied,
    FabricatedScoreDenied,
    FabricatedRankDenied,
    FabricatedBackendResponseDenied,
    FabricatedFusedScoreDenied,
    FabricatedRerankedPositionDenied,
    FabricatedQualityLabelDenied,
    FabricatedFusionOutputDenied,
    FabricatedRerankingOutputDenied,
    BackendResultLegExecutedDenied,
    EsClientCreatedDenied,
    QdrantClientCreatedDenied,
    EsExecutedDenied,
    QdrantExecutedDenied,
    ProductionRouteOrBeautySearchCalledDenied,
    RoutePluginDiHttpInvolvedDenied,
    ProductionRouteSwitchOrDefaultSwitchDenied,
    QdrantProductionActivationApprovedDenied,
    HybridServingImplementedOrClaimedDenied,
    FallbackImplementedOrClaimedDenied,
    ProductionTelemetryImplementedOrClaimedDenied,
    QualityGreenClaimedDenied,
    RetrievalQualityClaimedDenied,
    ProductionReadinessClaimedDenied,
    RouteActivationClaimedDenied,
    ServingApprovalClaimedDenied,
  )
}

/** Group an M12 fusion/reranking boundary/failure matrix row belongs to, used only to organise the
  * deterministic report.
  */
enum M12BeautyQSearchFusionRerankingMatrixGroup {
  case AcceptedBaselineHandling
  case NoiseProbeHandling
  case DeniedExecutableOrScoredOrRerankedDrift
  case DeniedFabrication
  case DeniedExecutionClientRouteBoundary
  case DeniedProductionActivationOrServingClaim

  def render: String =
    this match {
      case M12BeautyQSearchFusionRerankingMatrixGroup.AcceptedBaselineHandling               => "accepted_baseline_handling"
      case M12BeautyQSearchFusionRerankingMatrixGroup.NoiseProbeHandling                     => "noise_probe_handling"
      case M12BeautyQSearchFusionRerankingMatrixGroup.DeniedExecutableOrScoredOrRerankedDrift =>
        "denied_executable_or_scored_or_reranked_drift"
      case M12BeautyQSearchFusionRerankingMatrixGroup.DeniedFabrication                      => "denied_fabrication"
      case M12BeautyQSearchFusionRerankingMatrixGroup.DeniedExecutionClientRouteBoundary     => "denied_execution_client_route_boundary"
      case M12BeautyQSearchFusionRerankingMatrixGroup.DeniedProductionActivationOrServingClaim =>
        "denied_production_activation_or_serving_claim"
    }

  /** The decision every row in this group must carry. */
  def expectedDecision: M12BeautyQSearchFusionRerankingMatrixDecision =
    this match {
      case M12BeautyQSearchFusionRerankingMatrixGroup.AcceptedBaselineHandling =>
        M12BeautyQSearchFusionRerankingMatrixDecision.Accepted
      case M12BeautyQSearchFusionRerankingMatrixGroup.NoiseProbeHandling =>
        M12BeautyQSearchFusionRerankingMatrixDecision.Accepted
      case M12BeautyQSearchFusionRerankingMatrixGroup.DeniedExecutableOrScoredOrRerankedDrift =>
        M12BeautyQSearchFusionRerankingMatrixDecision.Denied
      case M12BeautyQSearchFusionRerankingMatrixGroup.DeniedFabrication =>
        M12BeautyQSearchFusionRerankingMatrixDecision.Denied
      case M12BeautyQSearchFusionRerankingMatrixGroup.DeniedExecutionClientRouteBoundary =>
        M12BeautyQSearchFusionRerankingMatrixDecision.Denied
      case M12BeautyQSearchFusionRerankingMatrixGroup.DeniedProductionActivationOrServingClaim =>
        M12BeautyQSearchFusionRerankingMatrixDecision.Denied
    }
}

/** One deterministic M12 fusion/reranking boundary/failure matrix row.
  *
  * A row is a pure data record about how the M12B policy catalog and experiment-plan schema must handle
  * a single policy-handling case. It carries a stable case id, a group, a data-only decision, an explicit
  * reason code, and a short detail string. It executes nothing and fabricates no candidate ids, provider
  * ids, scores, ranks, backend responses, fused scores, reranked positions, quality labels, fusion
  * outputs, or reranking outputs.
  */
final case class M12BeautyQSearchFusionRerankingMatrixRow(
  caseId: String,
  group: M12BeautyQSearchFusionRerankingMatrixGroup,
  decision: M12BeautyQSearchFusionRerankingMatrixDecision,
  reasonCode: M12BeautyQSearchFusionRerankingMatrixReasonCode,
  detail: String,
) {

  /** Whether this row's decision matches the decision its group requires. */
  def decisionMatchesGroup: Boolean = decision == group.expectedDecision

  def render: String = s"$caseId:${decision.render}:${reasonCode.render}"
}

final case class M12BeautyQSearchFusionRerankingBoundaryFailureMatrixMetric(
  name: String,
  value: String,
)

final case class M12BeautyQSearchFusionRerankingBoundaryFailureMatrixSummary(
  datasetId: String,
  artifactId: String,
  artifactVersion: String,
  consumedM12PolicyCatalogVerdict: String,
  consumedM12ExperimentPlanVerdict: String,
  consumedM12InputScaffoldVerdict: String,
  consumedM11ResultSchemaVerdict: String,
  consumedM11BoundaryFailureMatrixVerdict: String,
  consumedPolicyCatalogNameCount: Int,
  consumedPolicyCatalogNamesUnique: Boolean,
  consumedExperimentPlanRowCount: Int,
  consumedExperimentPlanEsBaselineRows: Int,
  consumedExperimentPlanQdrantBaselineRows: Int,
  consumedExperimentPlanCombinedRows: Int,
  consumedExperimentPlanAcceptedNegativeControlRows: Int,
  consumedExperimentPlanManualOrNoOpRows: Int,
  consumedExperimentPlanExecutableRows: Int,
  consumedExperimentPlanRealScoredOrRerankedRows: Int,
  matrixRowCount: Int,
  acceptedRowCount: Int,
  deniedRowCount: Int,
  skippedRowCount: Int,
  reasonCodeCounts: List[(M12BeautyQSearchFusionRerankingMatrixReasonCode, Int)],
  acceptedBaselineRows: List[M12BeautyQSearchFusionRerankingMatrixRow],
  noiseProbeRows: List[M12BeautyQSearchFusionRerankingMatrixRow],
  deniedExecutableScoredRerankedRows: List[M12BeautyQSearchFusionRerankingMatrixRow],
  deniedFabricationRows: List[M12BeautyQSearchFusionRerankingMatrixRow],
  deniedExecutionClientRouteRows: List[M12BeautyQSearchFusionRerankingMatrixRow],
  deniedProductionActivationServingRows: List[M12BeautyQSearchFusionRerankingMatrixRow],
  m12BoundaryFailureMatrixReady: Boolean,
  boundary: M10BeautyQSearchOfflineRoutingBoundary,
  verdict: String,
  metrics: List[M12BeautyQSearchFusionRerankingBoundaryFailureMatrixMetric],
)

/** M12 BeautyQ fusion/reranking boundary/failure matrix contract: a pure offline catalogue of how the
  * accepted M12B fusion/reranking policy catalog and experiment-plan schema must handle each
  * policy-handling case.
  *
  * This is a boundary/failure matrix only, never scoring, fusion, reranking, candidate retrieval,
  * backend execution, hybrid serving, fallback, telemetry, or production routing. It consumes the
  * accepted M12B policy catalog ([[M12BeautyQSearchFusionRerankingPolicyCatalog]]) and the accepted M12B
  * experiment-plan schema ([[M12BeautyQSearchFusionRerankingExperimentPlan]]) — including their verdicts,
  * the accepted-row plan total, and the standing boundary — and enumerates deterministic accepted/denied
  * policy-handling cases with explicit reason codes. It never calls production `/beauty-search`, never
  * creates an ES or Qdrant client, never runs Elasticsearch or Qdrant, and never touches a route, plugin,
  * DI, or HTTP source. It implements no scoring, fusion, reranking, candidate retrieval, fallback,
  * hybrid serving, telemetry, or production routing, and fabricates no candidate ids, provider ids,
  * scores, ranks, backend responses, fused scores, reranked positions, quality labels, fusion outputs,
  * or reranking outputs.
  *
  * Accepted rows mark the valid policy-handling cases the M12B catalog and plan permit (ES baseline
  * passthrough placeholder, Qdrant baseline passthrough placeholder, the three combined placeholder
  * policies — union, intersection, tie-breaker — the accepted negative-control exclusion policy, and the
  * current zero-count manual/no-op handling case). Denied rows mark executable/scored/reranked policy
  * drift, fabricated candidate/provider/score/rank/backend-response/fused-score/reranked-position/
  * quality-label/fusion-output/reranking-output artifacts, backend execution/client/route/plugin/DI/HTTP
  * drift, and any production activation, route switch, Qdrant production activation, hybrid serving,
  * fallback, telemetry, quality-green, retrieval-quality, production-readiness, route-activation, or
  * serving-approval claim. The readiness it reports is M12 *offline boundary-matrix* readiness only: it
  * claims no scoring readiness, no fusion/reranking execution readiness, no retrieval quality, no
  * production readiness, no route activation, no serving approval, and no execution readiness. Default
  * `/beauty-search` stays ES-backed, the Qdrant opt-in route stays disabled by default, and Qdrant
  * production activation stays not approved.
  */
object M12BeautyQSearchFusionRerankingBoundaryFailureMatrix {

  val MarkdownFilename: String =
    "m12-beautyq-fusion-reranking-boundary-failure-matrix.md"

  val ArtifactId: String = "m12-beautyq-fusion-reranking-boundary-failure-matrix"

  val ArtifactVersion: String = "v1"

  /** Readiness verdict: M12 offline fusion/reranking boundary-matrix readiness only. Deliberately carries
    * no scoring readiness, no fusion/reranking execution readiness, no retrieval-quality,
    * production-readiness, route-activation, serving-approval, or execution-readiness claim.
    */
  val Verdict: String =
    "m12_fusion_reranking_boundary_failure_matrix_ready_schema_only"

  /** Accepted M12B policy-catalog verdict this matrix consumes. */
  val ConsumedM12PolicyCatalogVerdict: String =
    M12BeautyQSearchFusionRerankingPolicyCatalog.Verdict

  /** Accepted M12B experiment-plan verdict this matrix consumes. */
  val ConsumedM12ExperimentPlanVerdict: String =
    M12BeautyQSearchFusionRerankingExperimentPlan.Verdict

  /** Accepted M12A input-scaffold verdict carried forward through the consumed M12B catalog and plan. */
  val ConsumedM12InputScaffoldVerdict: String =
    M12BeautyQSearchFusionRerankingInputScaffold.Verdict

  /** Accepted M11B result-schema verdict carried forward through the consumed M12A scaffold. */
  val ConsumedM11ResultSchemaVerdict: String =
    M12BeautyQSearchFusionRerankingInputScaffold.ConsumedM11ResultSchemaVerdict

  /** Accepted M11C boundary/failure matrix verdict carried forward through the consumed M12A scaffold. */
  val ConsumedM11BoundaryFailureMatrixVerdict: String =
    M12BeautyQSearchFusionRerankingInputScaffold.ConsumedM11BoundaryFailureMatrixVerdict

  import M12BeautyQSearchFusionRerankingMatrixDecision._
  import M12BeautyQSearchFusionRerankingMatrixGroup._
  import M12BeautyQSearchFusionRerankingMatrixReasonCode._

  /** Render the assigned future policy options of a consumed M12B plan row, for probe handling rows. */
  private def consumedPlanPolicies(queryId: String): String =
    M12BeautyQSearchFusionRerankingExperimentPlan.planRowFor(queryId) match {
      case Some(row) if row.assignedPolicies.isEmpty => "(none)"
      case Some(row)                                => row.assignedPolicies.map(_.render).mkString(", ")
      case None                                     => "(missing)"
    }

  /** The deterministic boundary/failure matrix, in stable case order. */
  val MatrixRows: List[M12BeautyQSearchFusionRerankingMatrixRow] = List(
    // Accepted baseline policy-handling cases.
    row(
      "case_es_baseline_passthrough_placeholder_accepted",
      AcceptedBaselineHandling,
      Accepted,
      EsBaselinePassthroughPlaceholderAccepted,
      "An ES baseline plan row carrying the es_baseline_passthrough placeholder policy is an accepted " +
        "policy-handling case in the M12B catalog.",
    ),
    row(
      "case_qdrant_baseline_passthrough_placeholder_accepted",
      AcceptedBaselineHandling,
      Accepted,
      QdrantBaselinePassthroughPlaceholderAccepted,
      "A Qdrant baseline plan row carrying the qdrant_baseline_passthrough placeholder policy is an " +
        "accepted policy-handling case in the M12B catalog.",
    ),
    row(
      "case_combined_union_placeholder_policy_accepted",
      AcceptedBaselineHandling,
      Accepted,
      CombinedUnionPlaceholderPolicyAccepted,
      "A combined experiment plan row carrying the combined_union_placeholder placeholder policy is an " +
        "accepted policy-handling case in the M12B catalog.",
    ),
    row(
      "case_combined_intersection_placeholder_policy_accepted",
      AcceptedBaselineHandling,
      Accepted,
      CombinedIntersectionPlaceholderPolicyAccepted,
      "A combined experiment plan row carrying the combined_intersection_placeholder placeholder " +
        "policy is an accepted policy-handling case in the M12B catalog.",
    ),
    row(
      "case_tie_breaker_placeholder_policy_accepted",
      AcceptedBaselineHandling,
      Accepted,
      TieBreakerPlaceholderPolicyAccepted,
      "A combined experiment plan row carrying the tie_breaker_placeholder placeholder policy is an " +
        "accepted policy-handling case in the M12B catalog.",
    ),
    row(
      "case_accepted_negative_control_exclusion_policy_accepted",
      AcceptedBaselineHandling,
      Accepted,
      AcceptedNegativeControlExclusionPolicyAccepted,
      "An accepted negative-control exclusion plan row carrying the " +
        "accepted_negative_control_exclusion_policy and no backend candidate policy is an accepted " +
        "policy-handling case in the M12B catalog.",
    ),
    row(
      "case_manual_or_no_op_zero_count_handling_accepted",
      AcceptedBaselineHandling,
      Accepted,
      ManualOrNoOpZeroCountHandlingAccepted,
      "The current zero-count manual/no-op exclusion plan rows retain a policy-handling case even " +
        "though their current count is zero; no backend candidate policy is assigned.",
    ),
    // Noise-probe handling rows derived from the consumed M12B experiment-plan schema.
    row(
      "case_q_noise_004_combined_placeholder_plan",
      NoiseProbeHandling,
      Accepted,
      CombinedUnionPlaceholderPolicyAccepted,
      s"q_noise_004 = gel removal maps to a combined placeholder experiment-plan row with non-executable " +
        s"combined policy options: ${consumedPlanPolicies("q_noise_004")}.",
    ),
    row(
      "case_q_noise_005_negative_control_exclusion_plan",
      NoiseProbeHandling,
      Accepted,
      AcceptedNegativeControlExclusionPolicyAccepted,
      s"q_noise_005 = lifting maps to the accepted_negative_control_exclusion_policy plan row with no " +
        s"backend candidate policy: ${consumedPlanPolicies("q_noise_005")}.",
    ),
    // Denied executable / scored / reranked policy drift.
    row(
      "case_executable_policy_drift",
      DeniedExecutableOrScoredOrRerankedDrift,
      Denied,
      ExecutablePolicyDriftDenied,
      "Any M12B policy option marked executable, any plan row that produces an executable policy, and " +
        "any code path that constructs a real policy algorithm are denied.",
    ),
    row(
      "case_real_scored_or_reranked_row_drift",
      DeniedExecutableOrScoredOrRerankedDrift,
      Denied,
      RealScoredOrRerankedRowDriftDenied,
      "Any plan row that records a real scored result or a real reranked result is denied; the M12B " +
        "catalog reports 0 executable policy rows and 0 real scored/reranked rows.",
    ),
    // Denied fabricated policy-handling artifacts.
    row(
      "case_fabricated_candidate_id",
      DeniedFabrication,
      Denied,
      FabricatedCandidateIdDenied,
      "Any fabricated candidate id in a plan row, in a probe detail, or anywhere else in the artifact " +
        "is denied.",
    ),
    row(
      "case_fabricated_provider_id",
      DeniedFabrication,
      Denied,
      FabricatedProviderIdDenied,
      "Any fabricated provider id is denied.",
    ),
    row(
      "case_fabricated_score",
      DeniedFabrication,
      Denied,
      FabricatedScoreDenied,
      "Any fabricated score is denied.",
    ),
    row(
      "case_fabricated_rank",
      DeniedFabrication,
      Denied,
      FabricatedRankDenied,
      "Any fabricated rank is denied.",
    ),
    row(
      "case_fabricated_backend_response",
      DeniedFabrication,
      Denied,
      FabricatedBackendResponseDenied,
      "Any fabricated backend response payload is denied.",
    ),
    row(
      "case_fabricated_fused_score",
      DeniedFabrication,
      Denied,
      FabricatedFusedScoreDenied,
      "Any fabricated fused score is denied.",
    ),
    row(
      "case_fabricated_reranked_position",
      DeniedFabrication,
      Denied,
      FabricatedRerankedPositionDenied,
      "Any fabricated reranked position is denied.",
    ),
    row(
      "case_fabricated_quality_label",
      DeniedFabrication,
      Denied,
      FabricatedQualityLabelDenied,
      "Any fabricated quality label is denied.",
    ),
    row(
      "case_fabricated_fusion_output",
      DeniedFabrication,
      Denied,
      FabricatedFusionOutputDenied,
      "Any fabricated fusion output is denied.",
    ),
    row(
      "case_fabricated_reranking_output",
      DeniedFabrication,
      Denied,
      FabricatedRerankingOutputDenied,
      "Any fabricated reranking output is denied.",
    ),
    // Denied backend execution / client / route / plugin / DI / HTTP involvement.
    row(
      "case_backend_result_leg_executed",
      DeniedExecutionClientRouteBoundary,
      Denied,
      BackendResultLegExecutedDenied,
      "Any plan row whose forwarded M12A result leg becomes executed, or any code path that flips a " +
        "pending/not-executed placeholder into an executed leg, is denied.",
    ),
    row(
      "case_es_client_created",
      DeniedExecutionClientRouteBoundary,
      Denied,
      EsClientCreatedDenied,
      "Creating an Elasticsearch client in this matrix is denied.",
    ),
    row(
      "case_qdrant_client_created",
      DeniedExecutionClientRouteBoundary,
      Denied,
      QdrantClientCreatedDenied,
      "Creating a Qdrant client in this matrix is denied.",
    ),
    row(
      "case_es_executed",
      DeniedExecutionClientRouteBoundary,
      Denied,
      EsExecutedDenied,
      "Executing Elasticsearch in this matrix is denied.",
    ),
    row(
      "case_qdrant_executed",
      DeniedExecutionClientRouteBoundary,
      Denied,
      QdrantExecutedDenied,
      "Executing Qdrant in this matrix is denied.",
    ),
    row(
      "case_production_route_or_beauty_search_called",
      DeniedExecutionClientRouteBoundary,
      Denied,
      ProductionRouteOrBeautySearchCalledDenied,
      "Calling production /beauty-search or invoking any production route from this matrix is denied.",
    ),
    row(
      "case_route_plugin_di_http_involved",
      DeniedExecutionClientRouteBoundary,
      Denied,
      RoutePluginDiHttpInvolvedDenied,
      "Any route, plugin, DI, or HTTP source involvement in this matrix is denied.",
    ),
    // Denied production activation / route switch / Qdrant activation / hybrid / fallback / telemetry /
    // readiness / approval drift.
    row(
      "case_production_route_switch_or_default_switch",
      DeniedProductionActivationOrServingClaim,
      Denied,
      ProductionRouteSwitchOrDefaultSwitchDenied,
      "Any production route switch, default route switch, or hybrid-serving-driven route switch is " +
        "denied.",
    ),
    row(
      "case_qdrant_production_activation_approved",
      DeniedProductionActivationOrServingClaim,
      Denied,
      QdrantProductionActivationApprovedDenied,
      "Any approval, claim, or implication that Qdrant production activation is approved is denied.",
    ),
    row(
      "case_hybrid_serving_implemented_or_claimed",
      DeniedProductionActivationOrServingClaim,
      Denied,
      HybridServingImplementedOrClaimedDenied,
      "Any hybrid serving implementation, hybrid serving implied claim, or combined experiment " +
        "placeholder row treated as production hybrid serving is denied.",
    ),
    row(
      "case_fallback_implemented_or_claimed",
      DeniedProductionActivationOrServingClaim,
      Denied,
      FallbackImplementedOrClaimedDenied,
      "Any fallback implementation, fallback implied claim, or fallback drift is denied.",
    ),
    row(
      "case_production_telemetry_implemented_or_claimed",
      DeniedProductionActivationOrServingClaim,
      Denied,
      ProductionTelemetryImplementedOrClaimedDenied,
      "Any production telemetry implementation, metrics client creation, or production telemetry " +
        "claim is denied.",
    ),
    row(
      "case_quality_green_claimed",
      DeniedProductionActivationOrServingClaim,
      Denied,
      QualityGreenClaimedDenied,
      "Any quality-green claim about M12 fusion/reranking, backend execution, or candidate retrieval " +
        "is denied.",
    ),
    row(
      "case_retrieval_quality_claimed",
      DeniedProductionActivationOrServingClaim,
      Denied,
      RetrievalQualityClaimedDenied,
      "Any retrieval-quality claim about M12 fusion/reranking, backend execution, or candidate " +
        "retrieval is denied.",
    ),
    row(
      "case_production_readiness_claimed",
      DeniedProductionActivationOrServingClaim,
      Denied,
      ProductionReadinessClaimedDenied,
      "Any production-readiness claim about M12 fusion/reranking is denied.",
    ),
    row(
      "case_route_activation_claimed",
      DeniedProductionActivationOrServingClaim,
      Denied,
      RouteActivationClaimedDenied,
      "Any route-activation claim about /beauty-search, the Qdrant opt-in route, or any production " +
        "route is denied.",
    ),
    row(
      "case_serving_approval_claimed",
      DeniedProductionActivationOrServingClaim,
      Denied,
      ServingApprovalClaimedDenied,
      "Any serving-approval claim about M12 fusion/reranking, hybrid serving, fallback, fusion, or " +
        "reranking is denied.",
    ),
  )

  /** Reason-code counts in stable reason-code order; codes with no rows report zero explicitly. */
  val ReasonCodeCounts: List[(M12BeautyQSearchFusionRerankingMatrixReasonCode, Int)] =
    M12BeautyQSearchFusionRerankingMatrixReasonCode.stableOrder.map { code =>
      code -> MatrixRows.count(_.reasonCode == code)
    }

  def rowsForGroup(
    group: M12BeautyQSearchFusionRerankingMatrixGroup
  ): List[M12BeautyQSearchFusionRerankingMatrixRow] =
    MatrixRows.filter(_.group == group)

  def matrixRowFor(caseId: String): Option[M12BeautyQSearchFusionRerankingMatrixRow] =
    MatrixRows.find(_.caseId == caseId)

  val DefaultSummary: M12BeautyQSearchFusionRerankingBoundaryFailureMatrixSummary =
    build()

  val MarkdownArtifact: M9OfflineEvalReportArtifact =
    M12BeautyQSearchFusionRerankingBoundaryFailureMatrixRenderer.markdownArtifact(
      filename = MarkdownFilename,
      summary = DefaultSummary,
    )

  def build(): M12BeautyQSearchFusionRerankingBoundaryFailureMatrixSummary = {
    val acceptedRowCount = MatrixRows.count(_.decision == Accepted)
    val deniedRowCount = MatrixRows.count(_.decision == Denied)
    val skippedRowCount = MatrixRows.count(_.decision == Skipped)

    val acceptedBaselineRows = rowsForGroup(AcceptedBaselineHandling)
    val noiseProbeRows = rowsForGroup(NoiseProbeHandling)
    val deniedExecutableScoredRerankedRows = rowsForGroup(DeniedExecutableOrScoredOrRerankedDrift)
    val deniedFabricationRows = rowsForGroup(DeniedFabrication)
    val deniedExecutionClientRouteRows = rowsForGroup(DeniedExecutionClientRouteBoundary)
    val deniedProductionActivationServingRows = rowsForGroup(DeniedProductionActivationOrServingClaim)

    val consumedCatalog = M12BeautyQSearchFusionRerankingPolicyCatalog.DefaultSummary
    val consumedPlan = M12BeautyQSearchFusionRerankingExperimentPlan.DefaultSummary

    val consumedPolicyCatalogNamesUnique =
      consumedCatalog.policyNames.map(_.render).distinct.size == consumedCatalog.policyCount

    // The matrix is ready when it consumes the accepted M12B experiment plan with a stable,
    // unique policy catalog, and every row's decision matches the decision its group requires
    // (accepted-groups accepted, denied-groups denied).
    val consumedExperimentPlanRowCountFromPlan = consumedPlan.planGroupCounts.map(_._2).sum
    val m12BoundaryFailureMatrixReady =
      consumedExperimentPlanRowCountFromPlan == M9BeautyQSearchEvalQueryDataset.Metadata.queryCount &&
        consumedPlan.executablePolicyRows == 0 &&
        consumedPlan.realScoredOrRerankedRows == 0 &&
        consumedPolicyCatalogNamesUnique &&
        MatrixRows.nonEmpty &&
        MatrixRows.forall(_.decisionMatchesGroup)

    val summaryWithoutMetrics = M12BeautyQSearchFusionRerankingBoundaryFailureMatrixSummary(
      datasetId = M9BeautyQSearchEvalQueryDataset.Metadata.datasetId,
      artifactId = ArtifactId,
      artifactVersion = ArtifactVersion,
      consumedM12PolicyCatalogVerdict = ConsumedM12PolicyCatalogVerdict,
      consumedM12ExperimentPlanVerdict = ConsumedM12ExperimentPlanVerdict,
      consumedM12InputScaffoldVerdict = ConsumedM12InputScaffoldVerdict,
      consumedM11ResultSchemaVerdict = ConsumedM11ResultSchemaVerdict,
      consumedM11BoundaryFailureMatrixVerdict = ConsumedM11BoundaryFailureMatrixVerdict,
      consumedPolicyCatalogNameCount = consumedCatalog.policyCount,
      consumedPolicyCatalogNamesUnique = consumedPolicyCatalogNamesUnique,
      consumedExperimentPlanRowCount = consumedExperimentPlanRowCountFromPlan,
      consumedExperimentPlanEsBaselineRows = consumedPlan.esBaselineRows,
      consumedExperimentPlanQdrantBaselineRows = consumedPlan.qdrantBaselineRows,
      consumedExperimentPlanCombinedRows = consumedPlan.combinedExperimentRows,
      consumedExperimentPlanAcceptedNegativeControlRows = consumedPlan.acceptedNegativeControlExclusionRows,
      consumedExperimentPlanManualOrNoOpRows = consumedPlan.manualOrNoOpRows,
      consumedExperimentPlanExecutableRows = consumedPlan.executablePolicyRows,
      consumedExperimentPlanRealScoredOrRerankedRows = consumedPlan.realScoredOrRerankedRows,
      matrixRowCount = MatrixRows.size,
      acceptedRowCount = acceptedRowCount,
      deniedRowCount = deniedRowCount,
      skippedRowCount = skippedRowCount,
      reasonCodeCounts = ReasonCodeCounts,
      acceptedBaselineRows = acceptedBaselineRows,
      noiseProbeRows = noiseProbeRows,
      deniedExecutableScoredRerankedRows = deniedExecutableScoredRerankedRows,
      deniedFabricationRows = deniedFabricationRows,
      deniedExecutionClientRouteRows = deniedExecutionClientRouteRows,
      deniedProductionActivationServingRows = deniedProductionActivationServingRows,
      m12BoundaryFailureMatrixReady = m12BoundaryFailureMatrixReady,
      boundary = consumedPlan.boundary,
      verdict = Verdict,
      metrics = Nil,
    )

    summaryWithoutMetrics.copy(metrics = metrics(summaryWithoutMetrics))
  }

  private def metrics(
    summary: M12BeautyQSearchFusionRerankingBoundaryFailureMatrixSummary
  ): List[M12BeautyQSearchFusionRerankingBoundaryFailureMatrixMetric] = {
    val b = summary.boundary
    List(
      metric("artifact_id", summary.artifactId),
      metric("artifact_version", summary.artifactVersion),
      metric("consumed_m12b_policy_catalog_verdict", summary.consumedM12PolicyCatalogVerdict),
      metric("consumed_m12b_experiment_plan_verdict", summary.consumedM12ExperimentPlanVerdict),
      metric("consumed_m12a_input_scaffold_verdict", summary.consumedM12InputScaffoldVerdict),
      metric("consumed_m11b_result_schema_verdict", summary.consumedM11ResultSchemaVerdict),
      metric("consumed_m11c_boundary_failure_matrix_verdict", summary.consumedM11BoundaryFailureMatrixVerdict),
      metric("consumed_policy_catalog_name_count", summary.consumedPolicyCatalogNameCount.toString),
      metric("consumed_policy_catalog_names_unique", summary.consumedPolicyCatalogNamesUnique.toString),
      metric("consumed_experiment_plan_row_count", summary.consumedExperimentPlanRowCount.toString),
      metric("consumed_experiment_plan_es_baseline_rows", summary.consumedExperimentPlanEsBaselineRows.toString),
      metric("consumed_experiment_plan_qdrant_baseline_rows", summary.consumedExperimentPlanQdrantBaselineRows.toString),
      metric("consumed_experiment_plan_combined_rows", summary.consumedExperimentPlanCombinedRows.toString),
      metric(
        "consumed_experiment_plan_accepted_negative_control_rows",
        summary.consumedExperimentPlanAcceptedNegativeControlRows.toString,
      ),
      metric("consumed_experiment_plan_manual_or_no_op_rows", summary.consumedExperimentPlanManualOrNoOpRows.toString),
      metric("consumed_experiment_plan_executable_rows", summary.consumedExperimentPlanExecutableRows.toString),
      metric(
        "consumed_experiment_plan_real_scored_or_reranked_rows",
        summary.consumedExperimentPlanRealScoredOrRerankedRows.toString,
      ),
      metric("matrix_row_count", summary.matrixRowCount.toString),
      metric("accepted_row_count", summary.acceptedRowCount.toString),
      metric("denied_row_count", summary.deniedRowCount.toString),
      metric("skipped_row_count", summary.skippedRowCount.toString),
      metric("reason_code_count_sum", summary.reasonCodeCounts.map(_._2).sum.toString),
      metric("accepted_baseline_row_count", summary.acceptedBaselineRows.size.toString),
      metric("noise_probe_row_count", summary.noiseProbeRows.size.toString),
      metric(
        "denied_executable_scored_reranked_row_count",
        summary.deniedExecutableScoredRerankedRows.size.toString,
      ),
      metric("denied_fabrication_row_count", summary.deniedFabricationRows.size.toString),
      metric("denied_execution_client_route_row_count", summary.deniedExecutionClientRouteRows.size.toString),
      metric(
        "denied_production_activation_serving_row_count",
        summary.deniedProductionActivationServingRows.size.toString,
      ),
      metric("m12_boundary_failure_matrix_ready", summary.m12BoundaryFailureMatrixReady.toString),
      metric("matrix_is_boundary_failure_matrix_only_not_scoring", true.toString),
      metric("matrix_is_boundary_failure_matrix_only_not_fusion", true.toString),
      metric("matrix_is_boundary_failure_matrix_only_not_reranking", true.toString),
      metric("matrix_is_boundary_failure_matrix_only_not_candidate_retrieval", true.toString),
      metric("matrix_is_boundary_failure_matrix_only_not_backend_execution", true.toString),
      metric("matrix_is_offline_report_not_production_routing", true.toString),
      metric("matrix_does_not_execute_es_or_qdrant", true.toString),
      metric("matrix_creates_no_es_or_qdrant_client", true.toString),
      metric("matrix_calls_no_production_beauty_search", true.toString),
      metric("matrix_involves_no_route_plugin_di_http", true.toString),
      metric("matrix_implements_no_fallback", true.toString),
      metric("matrix_implements_no_hybrid_serving", true.toString),
      metric("matrix_implements_no_production_telemetry", true.toString),
      metric("matrix_fabricates_no_candidate_ids_provider_ids_scores_ranks_backend_responses", true.toString),
      metric("matrix_fabricates_no_fused_scores_reranked_positions_quality_labels", true.toString),
      metric("matrix_fabricates_no_fusion_outputs_reranking_outputs", true.toString),
      metric("executable_scored_reranked_policy_drift_denied", true.toString),
      metric("fabricated_policy_handling_artifacts_denied", true.toString),
      metric("execution_client_route_boundary_drift_denied", true.toString),
      metric("production_activation_serving_claim_drift_denied", true.toString),
      metric("zero_count_manual_or_no_op_still_has_handling_case", true.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.DefaultBeautySearchEsBacked, b.defaultBeautySearchEsBacked.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QdrantOptInDisabledByDefault, b.qdrantOptInDisabledByDefault.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QdrantProductionActivationApproved, b.qdrantProductionActivationApproved.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ProductionRouteActivated, b.productionRouteActivated.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.DefaultRouteSwitched, b.defaultRouteSwitched.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ProductionBeautySearchCalled, b.productionBeautySearchCalled.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.EsClientCreated, b.esClientCreated.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QdrantClientCreated, b.qdrantClientCreated.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.EsExecuted, b.esExecuted.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QdrantExecuted, b.qdrantExecuted.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RoutePluginDiHttpInvolved, b.routePluginDiHttpInvolved.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RealBackendCallRequired, b.realBackendCallRequired.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RealBackendCallImplemented, b.realBackendCallImplemented.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.HybridServingImplied, b.hybridServingImplied.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.FallbackImplied, b.fallbackImplied.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ScoreFusionImplied, b.scoreFusionImplied.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RerankingImplied, b.rerankingImplied.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ProductionTelemetryImplied, b.productionTelemetryImplied.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.QualityGreenClaimed, b.qualityGreenClaimed.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ProductionReadinessClaimed, b.productionReadinessClaimed.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.RouteActivationClaimed, b.routeActivationClaimed.toString),
      metric(BeautyQSearchEvaluationMetricNames.SharedProductionPosture.ServingApprovalClaimed, b.servingApprovalClaimed.toString),
      metric("verdict", summary.verdict),
    )
  }

  private def row(
    caseId: String,
    group: M12BeautyQSearchFusionRerankingMatrixGroup,
    decision: M12BeautyQSearchFusionRerankingMatrixDecision,
    reasonCode: M12BeautyQSearchFusionRerankingMatrixReasonCode,
    detail: String,
  ): M12BeautyQSearchFusionRerankingMatrixRow =
    M12BeautyQSearchFusionRerankingMatrixRow(caseId, group, decision, reasonCode, detail)

  private def metric(
    name: String,
    value: String,
  ): M12BeautyQSearchFusionRerankingBoundaryFailureMatrixMetric =
    M12BeautyQSearchFusionRerankingBoundaryFailureMatrixMetric(name, value)
}

object M12BeautyQSearchFusionRerankingBoundaryFailureMatrixRenderer {

  def renderMarkdown(summary: M12BeautyQSearchFusionRerankingBoundaryFailureMatrixSummary): String = {
    val builder = new StringBuilder

    line(builder, "# M12 BeautyQ Fusion/Reranking Boundary/Failure Matrix")
    line(builder, "")
    line(builder, "Offline fusion/reranking boundary/failure matrix over the accepted M12B fusion/reranking policy")
    line(builder, "catalog and experiment-plan schema. This is an offline planning/eval artifact only. It is a")
    line(builder, "boundary/failure matrix only and is NOT scoring, NOT fusion execution, NOT reranking execution,")
    line(builder, "NOT candidate retrieval, NOT backend execution, and NOT production routing: no ES or Qdrant")
    line(builder, "client is created and neither backend is run. Each row is a data-only accepted/denied/skipped")
    line(builder, "decision with an explicit reason code about how the M12B policy catalog and experiment-plan")
    line(builder, "schema must handle one policy-handling case. Policies and plan rows remain non-executable and")
    line(builder, "no candidate ids, provider ids, scores, ranks, backend responses, fused scores, reranked")
    line(builder, "positions, quality labels, fusion outputs, or reranking outputs are fabricated. This artifact")
    line(builder, "reports M12 offline boundary-matrix readiness only (schema-only): it is not scoring readiness,")
    line(builder, "not fusion/reranking execution readiness, not retrieval quality, not production readiness, not")
    line(builder, "route activation, not serving approval, and not actual execution readiness.")
    line(builder, "")
    line(builder, "## Artifact identity")
    line(builder, "")
    line(builder, s"- artifact_id: ${renderText(summary.artifactId)}")
    line(builder, s"- artifact_version: ${renderText(summary.artifactVersion)}")
    line(builder, s"- dataset_id: ${renderText(summary.datasetId)}")
    line(builder, "")
    line(builder, "## Summary")
    line(builder, "")
    line(builder, s"- verdict: ${renderText(summary.verdict)}")
    line(builder, s"- consumed_m12b_policy_catalog_verdict: ${renderText(summary.consumedM12PolicyCatalogVerdict)}")
    line(builder, s"- consumed_m12b_experiment_plan_verdict: ${renderText(summary.consumedM12ExperimentPlanVerdict)}")
    line(builder, s"- consumed_m12a_input_scaffold_verdict: ${renderText(summary.consumedM12InputScaffoldVerdict)}")
    line(builder, s"- consumed_m11b_result_schema_verdict: ${renderText(summary.consumedM11ResultSchemaVerdict)}")
    line(builder, s"- consumed_m11c_boundary_failure_matrix_verdict: ${renderText(summary.consumedM11BoundaryFailureMatrixVerdict)}")
    line(builder, s"- consumed_policy_catalog_name_count: ${summary.consumedPolicyCatalogNameCount}")
    line(builder, s"- consumed_policy_catalog_names_unique: ${summary.consumedPolicyCatalogNamesUnique}")
    line(builder, s"- consumed_experiment_plan_row_count: ${summary.consumedExperimentPlanRowCount}")
    line(builder, s"- consumed_experiment_plan_es_baseline_rows: ${summary.consumedExperimentPlanEsBaselineRows}")
    line(builder, s"- consumed_experiment_plan_qdrant_baseline_rows: ${summary.consumedExperimentPlanQdrantBaselineRows}")
    line(builder, s"- consumed_experiment_plan_combined_rows: ${summary.consumedExperimentPlanCombinedRows}")
    line(builder, s"- consumed_experiment_plan_accepted_negative_control_rows: ${summary.consumedExperimentPlanAcceptedNegativeControlRows}")
    line(builder, s"- consumed_experiment_plan_manual_or_no_op_rows: ${summary.consumedExperimentPlanManualOrNoOpRows}")
    line(builder, s"- consumed_experiment_plan_executable_rows: ${summary.consumedExperimentPlanExecutableRows}")
    line(builder, s"- consumed_experiment_plan_real_scored_or_reranked_rows: ${summary.consumedExperimentPlanRealScoredOrRerankedRows}")
    line(builder, s"- matrix_row_count: ${summary.matrixRowCount}")
    line(builder, s"- accepted_row_count: ${summary.acceptedRowCount}")
    line(builder, s"- denied_row_count: ${summary.deniedRowCount}")
    line(builder, s"- skipped_row_count: ${summary.skippedRowCount}")
    line(builder, s"- m12_boundary_failure_matrix_ready: ${summary.m12BoundaryFailureMatrixReady}")
    line(builder, "")
    line(builder, "## Reason-code counts")
    line(builder, "")
    line(builder, "Reason codes are structured metric keys, preferred over long negated prose. Codes with no rows report zero.")
    line(builder, "")
    line(builder, "| reason_code | count |")
    line(builder, "|---|---|")
    summary.reasonCodeCounts.foreach { case (code, count) =>
      line(builder, s"| ${renderText(code.render)} | $count |")
    }
    line(builder, "")
    line(builder, "## Accepted baseline rows")
    line(builder, "")
    line(builder, "Valid M12B policy-handling cases the catalog and experiment-plan schema permit: ES baseline")
    line(builder, "passthrough placeholder, Qdrant baseline passthrough placeholder, the three combined placeholder")
    line(builder, "policies (union, intersection, tie-breaker), the accepted negative-control exclusion policy, and")
    line(builder, "the current zero-count manual/no-op handling case. Every accepted policy is non-executable.")
    line(builder, "")
    renderRowTable(builder, summary.acceptedBaselineRows)
    line(builder, "")
    line(builder, "## q_noise_004 and q_noise_005 handling rows")
    line(builder, "")
    line(builder, "Both ids share the q_noise_* prefix yet land in different accepted handling rows: q_noise_004 is")
    line(builder, "a combined placeholder experiment-plan row with non-executable combined policy options,")
    line(builder, "q_noise_005 is the accepted_negative_control_exclusion_policy row with no backend candidate policy.")
    line(builder, "")
    renderRowTable(builder, summary.noiseProbeRows)
    line(builder, "")
    line(builder, "## Denied executable / scored / reranked drift rows")
    line(builder, "")
    line(builder, "Any M12B policy that becomes executable, any plan row that records a real scored or reranked")
    line(builder, "result, and any code path that constructs a real policy algorithm are denied.")
    line(builder, "")
    renderRowTable(builder, summary.deniedExecutableScoredRerankedRows)
    line(builder, "")
    line(builder, "## Denied fabrication rows")
    line(builder, "")
    line(builder, "Any fabricated candidate id, provider id, score, rank, backend response, fused score, reranked")
    line(builder, "position, quality label, fusion output, or reranking output is denied.")
    line(builder, "")
    renderRowTable(builder, summary.deniedFabricationRows)
    line(builder, "")
    line(builder, "## Denied execution / client / route boundary rows")
    line(builder, "")
    line(builder, "Any backend execution, ES/Qdrant client creation, production /beauty-search call, or")
    line(builder, "route/plugin/DI/HTTP involvement is denied.")
    line(builder, "")
    renderRowTable(builder, summary.deniedExecutionClientRouteRows)
    line(builder, "")
    line(builder, "## Denied production activation / serving claim rows")
    line(builder, "")
    line(builder, "Any production route switch, Qdrant production activation, hybrid serving, fallback, production")
    line(builder, "telemetry, quality-green, retrieval-quality, production-readiness, route-activation, or")
    line(builder, "serving-approval claim is denied.")
    line(builder, "")
    renderRowTable(builder, summary.deniedProductionActivationServingRows)
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
    line(builder, "This is a boundary/failure matrix only, not scoring, not fusion execution, not reranking execution,")
    line(builder, "not candidate retrieval, not backend execution, and not production routing. No candidate ids,")
    line(builder, "provider ids, scores, ranks, backend responses, fused scores, reranked positions, quality labels,")
    line(builder, "fusion outputs, or reranking outputs are fabricated, and no production route, hybrid serving,")
    line(builder, "fallback, fusion, reranking, telemetry, or activation is implemented or claimed.")

    builder.result()
  }

  private def renderRowTable(
    builder: StringBuilder,
    rows: List[M12BeautyQSearchFusionRerankingMatrixRow],
  ): Unit = {
    line(builder, "| case_id | decision | reason_code | detail |")
    line(builder, "|---|---|---|---|")
    rows.foreach { r =>
      line(
        builder,
        s"| ${renderText(r.caseId)} | ${renderText(r.decision.render)} | ${renderText(r.reasonCode.render)} | ${renderText(r.detail)} |",
      )
    }
  }

  def markdownArtifact(
    filename: String,
    summary: M12BeautyQSearchFusionRerankingBoundaryFailureMatrixSummary,
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
