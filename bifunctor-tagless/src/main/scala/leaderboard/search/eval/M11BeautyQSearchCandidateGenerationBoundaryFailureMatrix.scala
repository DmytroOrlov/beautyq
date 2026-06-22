package leaderboard.search.eval

/** Decision a single M11 candidate-generation boundary/failure matrix row records.
  *
  * These are data-only verdicts about *result-shape handling*, never executions. `Accepted` marks a
  * saved-report shape the M11B result schema is allowed to carry; `Denied` marks a shape the schema must
  * refuse (an invalid backend-leg combination, a fabricated artifact, or a production/serving claim);
  * `Skipped` is reserved for handling cases that are neither accepted nor denied. No row runs ES or
  * Qdrant, creates a client, or touches a route, plugin, DI, or HTTP source.
  */
enum M11BeautyQSearchCandidateGenerationMatrixDecision {
  case Accepted
  case Denied
  case Skipped

  def render: String =
    this match {
      case M11BeautyQSearchCandidateGenerationMatrixDecision.Accepted => "accepted"
      case M11BeautyQSearchCandidateGenerationMatrixDecision.Denied   => "denied"
      case M11BeautyQSearchCandidateGenerationMatrixDecision.Skipped  => "skipped"
    }
}

/** Stable reason code attached to one M11 boundary/failure matrix row.
  *
  * Reason codes are structured metric keys, deliberately preferred over long negated prose. Each code is
  * a data-only label for one result-shape handling case; none implies a backend call, a production route,
  * or any quality/readiness/serving claim.
  */
enum M11BeautyQSearchCandidateGenerationMatrixReasonCode {
  // Accepted baseline result-shape handling.
  case EsOnlyPendingPlaceholderAccepted
  case QdrantOnlyPendingPlaceholderAccepted
  case CombinedPendingPlaceholdersAccepted
  case AcceptedNegativeControlNoBackendLegAccepted
  case ManualReviewExclusionNoBackendLegAccepted
  case NoOpNoiseExclusionNoBackendLegAccepted

  // Denied invalid backend-leg combinations.
  case EsOnlyWithQdrantLegDenied
  case QdrantOnlyWithEsLegDenied
  case CombinedMissingEsLegDenied
  case CombinedMissingQdrantLegDenied
  case NegativeControlWithBackendLegDenied
  case ManualOrNoOpWithBackendLegDenied

  // Denied fabricated candidate artifacts.
  case FabricatedCandidateIdDenied
  case FabricatedScoreOrRankDenied
  case FabricatedProviderIdDenied
  case FabricatedBackendResponseDenied

  // Denied production/serving involvement and claims.
  case ProductionRouteOrBeautySearchOrDiHttpDenied
  case ProductionActivationOrServingClaimDenied

  def render: String =
    this match {
      case M11BeautyQSearchCandidateGenerationMatrixReasonCode.EsOnlyPendingPlaceholderAccepted            => "es_only_pending_placeholder_accepted"
      case M11BeautyQSearchCandidateGenerationMatrixReasonCode.QdrantOnlyPendingPlaceholderAccepted        => "qdrant_only_pending_placeholder_accepted"
      case M11BeautyQSearchCandidateGenerationMatrixReasonCode.CombinedPendingPlaceholdersAccepted         => "combined_pending_placeholders_accepted"
      case M11BeautyQSearchCandidateGenerationMatrixReasonCode.AcceptedNegativeControlNoBackendLegAccepted => "accepted_negative_control_no_backend_leg_accepted"
      case M11BeautyQSearchCandidateGenerationMatrixReasonCode.ManualReviewExclusionNoBackendLegAccepted   => "manual_review_exclusion_no_backend_leg_accepted"
      case M11BeautyQSearchCandidateGenerationMatrixReasonCode.NoOpNoiseExclusionNoBackendLegAccepted      => "no_op_noise_exclusion_no_backend_leg_accepted"
      case M11BeautyQSearchCandidateGenerationMatrixReasonCode.EsOnlyWithQdrantLegDenied                   => "es_only_with_qdrant_leg_denied"
      case M11BeautyQSearchCandidateGenerationMatrixReasonCode.QdrantOnlyWithEsLegDenied                   => "qdrant_only_with_es_leg_denied"
      case M11BeautyQSearchCandidateGenerationMatrixReasonCode.CombinedMissingEsLegDenied                  => "combined_missing_es_leg_denied"
      case M11BeautyQSearchCandidateGenerationMatrixReasonCode.CombinedMissingQdrantLegDenied              => "combined_missing_qdrant_leg_denied"
      case M11BeautyQSearchCandidateGenerationMatrixReasonCode.NegativeControlWithBackendLegDenied         => "negative_control_with_backend_leg_denied"
      case M11BeautyQSearchCandidateGenerationMatrixReasonCode.ManualOrNoOpWithBackendLegDenied            => "manual_or_no_op_with_backend_leg_denied"
      case M11BeautyQSearchCandidateGenerationMatrixReasonCode.FabricatedCandidateIdDenied                 => "fabricated_candidate_id_denied"
      case M11BeautyQSearchCandidateGenerationMatrixReasonCode.FabricatedScoreOrRankDenied                 => "fabricated_score_or_rank_denied"
      case M11BeautyQSearchCandidateGenerationMatrixReasonCode.FabricatedProviderIdDenied                  => "fabricated_provider_id_denied"
      case M11BeautyQSearchCandidateGenerationMatrixReasonCode.FabricatedBackendResponseDenied             => "fabricated_backend_response_denied"
      case M11BeautyQSearchCandidateGenerationMatrixReasonCode.ProductionRouteOrBeautySearchOrDiHttpDenied => "production_route_or_beauty_search_or_di_http_denied"
      case M11BeautyQSearchCandidateGenerationMatrixReasonCode.ProductionActivationOrServingClaimDenied    => "production_activation_or_serving_claim_denied"
    }
}

object M11BeautyQSearchCandidateGenerationMatrixReasonCode {

  /** Stable reason-code order: accepted baseline codes, then denied leg-combination, fabrication, and
    * production/serving codes.
    */
  val stableOrder: List[M11BeautyQSearchCandidateGenerationMatrixReasonCode] = List(
    EsOnlyPendingPlaceholderAccepted,
    QdrantOnlyPendingPlaceholderAccepted,
    CombinedPendingPlaceholdersAccepted,
    AcceptedNegativeControlNoBackendLegAccepted,
    ManualReviewExclusionNoBackendLegAccepted,
    NoOpNoiseExclusionNoBackendLegAccepted,
    EsOnlyWithQdrantLegDenied,
    QdrantOnlyWithEsLegDenied,
    CombinedMissingEsLegDenied,
    CombinedMissingQdrantLegDenied,
    NegativeControlWithBackendLegDenied,
    ManualOrNoOpWithBackendLegDenied,
    FabricatedCandidateIdDenied,
    FabricatedScoreOrRankDenied,
    FabricatedProviderIdDenied,
    FabricatedBackendResponseDenied,
    ProductionRouteOrBeautySearchOrDiHttpDenied,
    ProductionActivationOrServingClaimDenied,
  )
}

/** Group an M11 boundary/failure matrix row belongs to, used only to organise the deterministic report. */
enum M11BeautyQSearchCandidateGenerationMatrixGroup {
  case AcceptedBaselineHandling
  case NoiseProbeHandling
  case DeniedBoundaryViolation
  case DeniedFabrication

  def render: String =
    this match {
      case M11BeautyQSearchCandidateGenerationMatrixGroup.AcceptedBaselineHandling => "accepted_baseline_handling"
      case M11BeautyQSearchCandidateGenerationMatrixGroup.NoiseProbeHandling       => "noise_probe_handling"
      case M11BeautyQSearchCandidateGenerationMatrixGroup.DeniedBoundaryViolation  => "denied_boundary_violation"
      case M11BeautyQSearchCandidateGenerationMatrixGroup.DeniedFabrication        => "denied_fabrication"
    }

  /** The decision every row in this group must carry. */
  def expectedDecision: M11BeautyQSearchCandidateGenerationMatrixDecision =
    this match {
      case M11BeautyQSearchCandidateGenerationMatrixGroup.AcceptedBaselineHandling =>
        M11BeautyQSearchCandidateGenerationMatrixDecision.Accepted
      case M11BeautyQSearchCandidateGenerationMatrixGroup.NoiseProbeHandling =>
        M11BeautyQSearchCandidateGenerationMatrixDecision.Accepted
      case M11BeautyQSearchCandidateGenerationMatrixGroup.DeniedBoundaryViolation =>
        M11BeautyQSearchCandidateGenerationMatrixDecision.Denied
      case M11BeautyQSearchCandidateGenerationMatrixGroup.DeniedFabrication =>
        M11BeautyQSearchCandidateGenerationMatrixDecision.Denied
    }
}

/** One deterministic M11 candidate-generation boundary/failure matrix row.
  *
  * A row is a pure data record about how the M11B result schema must handle a single result-shape case.
  * It carries a stable case id, a group, a data-only decision, an explicit reason code, and a short
  * detail string. It executes nothing and fabricates no candidate ids, scores, ranks, provider ids, or
  * backend responses.
  */
final case class M11BeautyQSearchCandidateGenerationMatrixRow(
  caseId: String,
  group: M11BeautyQSearchCandidateGenerationMatrixGroup,
  decision: M11BeautyQSearchCandidateGenerationMatrixDecision,
  reasonCode: M11BeautyQSearchCandidateGenerationMatrixReasonCode,
  detail: String,
) {

  /** Whether this row's decision matches the decision its group requires. */
  def decisionMatchesGroup: Boolean = decision == group.expectedDecision

  def render: String = s"$caseId:${decision.render}:${reasonCode.render}"
}

final case class M11BeautyQSearchCandidateGenerationBoundaryFailureMatrixMetric(
  name: String,
  value: String,
)

final case class M11BeautyQSearchCandidateGenerationBoundaryFailureMatrixSummary(
  datasetId: String,
  consumedM11ResultSchemaVerdict: String,
  consumedResultRowCount: Int,
  matrixRowCount: Int,
  acceptedRowCount: Int,
  deniedRowCount: Int,
  skippedRowCount: Int,
  reasonCodeCounts: List[(M11BeautyQSearchCandidateGenerationMatrixReasonCode, Int)],
  acceptedBaselineRows: List[M11BeautyQSearchCandidateGenerationMatrixRow],
  noiseProbeRows: List[M11BeautyQSearchCandidateGenerationMatrixRow],
  deniedBoundaryRows: List[M11BeautyQSearchCandidateGenerationMatrixRow],
  deniedFabricationRows: List[M11BeautyQSearchCandidateGenerationMatrixRow],
  m11BoundaryFailureMatrixReady: Boolean,
  boundary: M10BeautyQSearchOfflineRoutingBoundary,
  verdict: String,
  metrics: List[M11BeautyQSearchCandidateGenerationBoundaryFailureMatrixMetric],
)

/** M11 candidate-generation boundary/failure matrix contract: a pure offline catalogue of how the
  * accepted M11B result schema must handle each candidate-generation result-shape case.
  *
  * This is a boundary/failure matrix only, never backend execution and never production routing. It
  * consumes the accepted M11B result schema ([[M11BeautyQSearchCandidateGenerationResultSchema]]) —
  * including its verdict, its 63-row total, and its standing boundary — and enumerates deterministic
  * accepted/denied result-shape handling cases with explicit reason codes. It never calls production
  * `/beauty-search`, never creates an ES or Qdrant client, never runs Elasticsearch or Qdrant, and never
  * touches a route, plugin, DI, or HTTP source. It implements no candidate retrieval, scoring, fusion,
  * reranking, fallback, or telemetry, and fabricates no candidate ids, scores, ranks, provider ids, or
  * backend responses.
  *
  * Accepted rows mark valid pending/not-executed placeholder shapes (ES-only, Qdrant-only, combined) and
  * exclusion shapes with no backend legs (accepted negative control, manual review, no-op noise — the
  * last two retain handling cases even though their current counts are zero). Denied rows mark invalid
  * backend-leg combinations, fabricated candidate artifacts, and any production/serving involvement or
  * claim. The readiness it reports is M11 *offline boundary-matrix* readiness only: it claims no backend
  * quality green, retrieval quality, production readiness, route activation, serving approval, or
  * execution readiness. Default `/beauty-search` stays ES-backed, the Qdrant opt-in route stays disabled
  * by default, and Qdrant production activation stays not approved.
  */
object M11BeautyQSearchCandidateGenerationBoundaryFailureMatrix {

  val MarkdownFilename: String =
    "m11-beautyq-candidate-generation-boundary-failure-matrix.md"

  /** Readiness verdict: M11 offline boundary-matrix readiness only. Deliberately carries no quality,
    * retrieval-quality, production-readiness, route-activation, serving-approval, or execution-readiness
    * claim.
    */
  val Verdict: String =
    "m11_candidate_generation_boundary_failure_matrix_ready"

  /** Accepted M11B result-schema verdict this matrix consumes. */
  val ConsumedM11ResultSchemaVerdict: String =
    M11BeautyQSearchCandidateGenerationResultSchema.Verdict

  /** The accepted M11B result-row total (63) this matrix is asserted against. */
  val ConsumedResultRowCount: Int =
    M11BeautyQSearchCandidateGenerationResultSchema.DefaultSummary.totalRowCount

  /** Render the pending/not-executed result legs of an accepted M11B result row, for probe handling rows. */
  private def consumedResultLegs(queryId: String): String =
    M11BeautyQSearchCandidateGenerationResultSchema.resultRowFor(queryId) match {
      case Some(row) if row.resultLegs.isEmpty => "(none)"
      case Some(row)                           => row.resultLegs.map(_.render).mkString(", ")
      case None                                => "(missing)"
    }

  import M11BeautyQSearchCandidateGenerationMatrixDecision._
  import M11BeautyQSearchCandidateGenerationMatrixGroup._
  import M11BeautyQSearchCandidateGenerationMatrixReasonCode._

  /** The deterministic boundary/failure matrix, in stable case order. */
  val MatrixRows: List[M11BeautyQSearchCandidateGenerationMatrixRow] = List(
    // Accepted baseline result-shape handling.
    row(
      "case_es_only_pending_accepted",
      AcceptedBaselineHandling,
      Accepted,
      EsOnlyPendingPlaceholderAccepted,
      "ES-only row with a single pending/not-executed ES result-leg placeholder is an accepted saved-report shape.",
    ),
    row(
      "case_qdrant_only_pending_accepted",
      AcceptedBaselineHandling,
      Accepted,
      QdrantOnlyPendingPlaceholderAccepted,
      "Qdrant-only row with a single pending/not-executed Qdrant result-leg placeholder is an accepted saved-report shape.",
    ),
    row(
      "case_combined_pending_accepted",
      AcceptedBaselineHandling,
      Accepted,
      CombinedPendingPlaceholdersAccepted,
      "Combined row with separate pending/not-executed ES and Qdrant result-leg placeholders is an accepted offline comparison report shape.",
    ),
    row(
      "case_accepted_negative_control_no_leg_accepted",
      AcceptedBaselineHandling,
      Accepted,
      AcceptedNegativeControlNoBackendLegAccepted,
      "Accepted negative-control exclusion with no backend result legs is an accepted saved-report shape.",
    ),
    row(
      "case_manual_review_no_leg_accepted",
      AcceptedBaselineHandling,
      Accepted,
      ManualReviewExclusionNoBackendLegAccepted,
      "Manual-review exclusion with no backend result legs is an accepted handling case even though its current count is zero.",
    ),
    row(
      "case_no_op_noise_no_leg_accepted",
      AcceptedBaselineHandling,
      Accepted,
      NoOpNoiseExclusionNoBackendLegAccepted,
      "No-op/noise exclusion with no backend result legs is an accepted handling case even though its current count is zero.",
    ),
    // Noise-probe handling rows derived from the consumed M11B result schema.
    row(
      "case_q_noise_004_combined_pending",
      NoiseProbeHandling,
      Accepted,
      CombinedPendingPlaceholdersAccepted,
      s"q_noise_004 = gel removal maps to combined pending ES + Qdrant placeholders: ${consumedResultLegs("q_noise_004")}.",
    ),
    row(
      "case_q_noise_005_negative_control",
      NoiseProbeHandling,
      Accepted,
      AcceptedNegativeControlNoBackendLegAccepted,
      s"q_noise_005 = lifting maps to an accepted negative-control exclusion with no backend result legs: ${consumedResultLegs("q_noise_005")}.",
    ),
    // Denied invalid backend-leg combinations.
    row(
      "case_es_only_with_qdrant_leg",
      DeniedBoundaryViolation,
      Denied,
      EsOnlyWithQdrantLegDenied,
      "ES-only row carrying a Qdrant result leg is a denied invalid backend-leg combination.",
    ),
    row(
      "case_qdrant_only_with_es_leg",
      DeniedBoundaryViolation,
      Denied,
      QdrantOnlyWithEsLegDenied,
      "Qdrant-only row carrying an ES result leg is a denied invalid backend-leg combination.",
    ),
    row(
      "case_combined_missing_es_leg",
      DeniedBoundaryViolation,
      Denied,
      CombinedMissingEsLegDenied,
      "Combined row missing its ES result leg is a denied invalid backend-leg combination.",
    ),
    row(
      "case_combined_missing_qdrant_leg",
      DeniedBoundaryViolation,
      Denied,
      CombinedMissingQdrantLegDenied,
      "Combined row missing its Qdrant result leg is a denied invalid backend-leg combination.",
    ),
    row(
      "case_negative_control_with_backend_leg",
      DeniedBoundaryViolation,
      Denied,
      NegativeControlWithBackendLegDenied,
      "Accepted negative-control row carrying any backend result leg is a denied invalid backend-leg combination.",
    ),
    row(
      "case_manual_or_no_op_with_backend_leg",
      DeniedBoundaryViolation,
      Denied,
      ManualOrNoOpWithBackendLegDenied,
      "Manual-review or no-op/noise row carrying any backend result leg is a denied invalid backend-leg combination.",
    ),
    row(
      "case_production_route_or_beauty_search",
      DeniedBoundaryViolation,
      Denied,
      ProductionRouteOrBeautySearchOrDiHttpDenied,
      "Any production route, /beauty-search call, or route/plugin/DI/HTTP involvement is denied.",
    ),
    row(
      "case_production_activation_or_serving_claim",
      DeniedBoundaryViolation,
      Denied,
      ProductionActivationOrServingClaimDenied,
      "Any production activation, route switch, Qdrant production activation, hybrid serving, fallback, fusion, reranking, production telemetry, quality-green, retrieval-quality, production-readiness, route-activation, or serving-approval claim is denied.",
    ),
    // Denied fabricated candidate artifacts.
    row(
      "case_fabricated_candidate_id",
      DeniedFabrication,
      Denied,
      FabricatedCandidateIdDenied,
      "Any fabricated candidate id is denied.",
    ),
    row(
      "case_fabricated_score_or_rank",
      DeniedFabrication,
      Denied,
      FabricatedScoreOrRankDenied,
      "Any fabricated score or rank is denied.",
    ),
    row(
      "case_fabricated_provider_id",
      DeniedFabrication,
      Denied,
      FabricatedProviderIdDenied,
      "Any fabricated provider id is denied.",
    ),
    row(
      "case_fabricated_backend_response",
      DeniedFabrication,
      Denied,
      FabricatedBackendResponseDenied,
      "Any fabricated backend response payload is denied.",
    ),
  )

  /** Reason-code counts in stable reason-code order; codes with no rows report zero explicitly. */
  val ReasonCodeCounts: List[(M11BeautyQSearchCandidateGenerationMatrixReasonCode, Int)] =
    M11BeautyQSearchCandidateGenerationMatrixReasonCode.stableOrder.map { code =>
      code -> MatrixRows.count(_.reasonCode == code)
    }

  def rowsForGroup(
    group: M11BeautyQSearchCandidateGenerationMatrixGroup
  ): List[M11BeautyQSearchCandidateGenerationMatrixRow] =
    MatrixRows.filter(_.group == group)

  def matrixRowFor(caseId: String): Option[M11BeautyQSearchCandidateGenerationMatrixRow] =
    MatrixRows.find(_.caseId == caseId)

  val DefaultSummary: M11BeautyQSearchCandidateGenerationBoundaryFailureMatrixSummary =
    build()

  val MarkdownArtifact: M9OfflineEvalReportArtifact =
    M11BeautyQSearchCandidateGenerationBoundaryFailureMatrixRenderer.markdownArtifact(
      filename = MarkdownFilename,
      summary = DefaultSummary,
    )

  def build(): M11BeautyQSearchCandidateGenerationBoundaryFailureMatrixSummary = {
    val acceptedRowCount = MatrixRows.count(_.decision == Accepted)
    val deniedRowCount = MatrixRows.count(_.decision == Denied)
    val skippedRowCount = MatrixRows.count(_.decision == Skipped)

    val acceptedBaselineRows = rowsForGroup(AcceptedBaselineHandling)
    val noiseProbeRows = rowsForGroup(NoiseProbeHandling)
    val deniedBoundaryRows = rowsForGroup(DeniedBoundaryViolation)
    val deniedFabricationRows = rowsForGroup(DeniedFabrication)

    // The matrix is ready when it consumes the accepted 63-row M11B schema and every row's decision
    // matches the decision its group requires (accepted-groups accepted, denied-groups denied).
    val m11BoundaryFailureMatrixReady =
      ConsumedResultRowCount == 63 &&
        MatrixRows.nonEmpty &&
        MatrixRows.forall(_.decisionMatchesGroup)

    val summaryWithoutMetrics = M11BeautyQSearchCandidateGenerationBoundaryFailureMatrixSummary(
      datasetId = M9BeautyQSearchEvalQueryDataset.Metadata.datasetId,
      consumedM11ResultSchemaVerdict = ConsumedM11ResultSchemaVerdict,
      consumedResultRowCount = ConsumedResultRowCount,
      matrixRowCount = MatrixRows.size,
      acceptedRowCount = acceptedRowCount,
      deniedRowCount = deniedRowCount,
      skippedRowCount = skippedRowCount,
      reasonCodeCounts = ReasonCodeCounts,
      acceptedBaselineRows = acceptedBaselineRows,
      noiseProbeRows = noiseProbeRows,
      deniedBoundaryRows = deniedBoundaryRows,
      deniedFabricationRows = deniedFabricationRows,
      m11BoundaryFailureMatrixReady = m11BoundaryFailureMatrixReady,
      boundary = M11BeautyQSearchCandidateGenerationResultSchema.DefaultSummary.boundary,
      verdict = Verdict,
      metrics = Nil,
    )

    summaryWithoutMetrics.copy(metrics = metrics(summaryWithoutMetrics))
  }

  private def metrics(
    summary: M11BeautyQSearchCandidateGenerationBoundaryFailureMatrixSummary
  ): List[M11BeautyQSearchCandidateGenerationBoundaryFailureMatrixMetric] = {
    val b = summary.boundary
    List(
      metric("consumed_m11_result_schema_verdict", summary.consumedM11ResultSchemaVerdict),
      metric("consumed_result_row_count", summary.consumedResultRowCount.toString),
      metric("matrix_row_count", summary.matrixRowCount.toString),
      metric("accepted_row_count", summary.acceptedRowCount.toString),
      metric("denied_row_count", summary.deniedRowCount.toString),
      metric("skipped_row_count", summary.skippedRowCount.toString),
      metric("reason_code_count_sum", summary.reasonCodeCounts.map(_._2).sum.toString),
      metric("accepted_baseline_row_count", summary.acceptedBaselineRows.size.toString),
      metric("noise_probe_row_count", summary.noiseProbeRows.size.toString),
      metric("denied_boundary_row_count", summary.deniedBoundaryRows.size.toString),
      metric("denied_fabrication_row_count", summary.deniedFabricationRows.size.toString),
      metric("m11_boundary_failure_matrix_ready", summary.m11BoundaryFailureMatrixReady.toString),
      metric("matrix_is_boundary_failure_matrix_only_not_backend_execution", true.toString),
      metric("matrix_is_offline_report_not_production_routing", true.toString),
      metric("matrix_does_not_execute_es_or_qdrant", true.toString),
      metric("matrix_creates_no_es_or_qdrant_client", true.toString),
      metric("matrix_calls_no_production_beauty_search", true.toString),
      metric("matrix_involves_no_route_plugin_di_http", true.toString),
      metric("matrix_fabricates_no_candidate_ids_scores_ranks_provider_ids_backend_responses", true.toString),
      metric("invalid_backend_leg_combinations_denied", true.toString),
      metric("fabricated_candidate_artifacts_denied", true.toString),
      metric("production_route_and_serving_claims_denied", true.toString),
      metric("zero_count_manual_and_no_op_still_have_handling_cases", true.toString),
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

  private def row(
    caseId: String,
    group: M11BeautyQSearchCandidateGenerationMatrixGroup,
    decision: M11BeautyQSearchCandidateGenerationMatrixDecision,
    reasonCode: M11BeautyQSearchCandidateGenerationMatrixReasonCode,
    detail: String,
  ): M11BeautyQSearchCandidateGenerationMatrixRow =
    M11BeautyQSearchCandidateGenerationMatrixRow(caseId, group, decision, reasonCode, detail)

  private def metric(
    name: String,
    value: String,
  ): M11BeautyQSearchCandidateGenerationBoundaryFailureMatrixMetric =
    M11BeautyQSearchCandidateGenerationBoundaryFailureMatrixMetric(name, value)
}

object M11BeautyQSearchCandidateGenerationBoundaryFailureMatrixRenderer {

  def renderMarkdown(summary: M11BeautyQSearchCandidateGenerationBoundaryFailureMatrixSummary): String = {
    val builder = new StringBuilder

    line(builder, "# M11 BeautyQ Candidate-Generation Boundary/Failure Matrix")
    line(builder, "")
    line(builder, "Offline candidate-generation boundary/failure matrix over the accepted M11B candidate-generation result")
    line(builder, "schema. This is an offline planning/eval artifact only. It is a boundary/failure matrix only and is NOT")
    line(builder, "backend execution and NOT production routing: no ES or Qdrant client is created and neither backend is run.")
    line(builder, "Each row is a data-only accepted/denied/skipped decision with an explicit reason code about how the M11B")
    line(builder, "result schema must handle one result-shape case. Backend legs remain pending/not-executed placeholders and")
    line(builder, "no candidate ids, scores, ranks, provider ids, or backend responses are fabricated. This artifact reports M11")
    line(builder, "offline boundary-matrix readiness only: it is not backend quality green, not retrieval quality, not production")
    line(builder, "readiness, not route activation, not serving approval, and not actual execution readiness.")
    line(builder, "")
    line(builder, "## Summary")
    line(builder, "")
    line(builder, s"- dataset_id: ${renderText(summary.datasetId)}")
    line(builder, s"- verdict: ${renderText(summary.verdict)}")
    line(builder, s"- consumed_m11_result_schema_verdict: ${renderText(summary.consumedM11ResultSchemaVerdict)}")
    line(builder, s"- consumed_result_row_count: ${summary.consumedResultRowCount}")
    line(builder, s"- matrix_row_count: ${summary.matrixRowCount}")
    line(builder, s"- accepted_row_count: ${summary.acceptedRowCount}")
    line(builder, s"- denied_row_count: ${summary.deniedRowCount}")
    line(builder, s"- skipped_row_count: ${summary.skippedRowCount}")
    line(builder, s"- m11_boundary_failure_matrix_ready: ${summary.m11BoundaryFailureMatrixReady}")
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
    line(builder, "Valid pending/not-executed placeholder shapes and exclusion shapes with no backend legs. The manual-review")
    line(builder, "and no-op/noise rows retain handling cases even though their current counts are zero.")
    line(builder, "")
    renderRowTable(builder, summary.acceptedBaselineRows)
    line(builder, "")
    line(builder, "## q_noise_004 and q_noise_005 handling rows")
    line(builder, "")
    line(builder, "Both ids share the q_noise_* prefix yet land in different accepted handling rows: q_noise_004 is a combined")
    line(builder, "pending ES + Qdrant placeholder shape, q_noise_005 is an accepted negative-control exclusion with no legs.")
    line(builder, "")
    renderRowTable(builder, summary.noiseProbeRows)
    line(builder, "")
    line(builder, "## Denied boundary rows")
    line(builder, "")
    line(builder, "Invalid backend-leg combinations and any production route / serving involvement or claim.")
    line(builder, "")
    renderRowTable(builder, summary.deniedBoundaryRows)
    line(builder, "")
    line(builder, "## Denied fabrication rows")
    line(builder, "")
    line(builder, "Any fabricated candidate id, score, rank, provider id, or backend response.")
    line(builder, "")
    renderRowTable(builder, summary.deniedFabricationRows)
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
    line(builder, "This is a boundary/failure matrix only, not backend execution and not production routing; backend legs are")
    line(builder, "pending/not-executed placeholders with no fabricated candidate ids, scores, ranks, provider ids, or backend")
    line(builder, "responses, and no production route, hybrid serving, fallback, fusion, reranking, telemetry, or activation is")
    line(builder, "implemented or claimed.")

    builder.result()
  }

  private def renderRowTable(
    builder: StringBuilder,
    rows: List[M11BeautyQSearchCandidateGenerationMatrixRow],
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
    summary: M11BeautyQSearchCandidateGenerationBoundaryFailureMatrixSummary,
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
