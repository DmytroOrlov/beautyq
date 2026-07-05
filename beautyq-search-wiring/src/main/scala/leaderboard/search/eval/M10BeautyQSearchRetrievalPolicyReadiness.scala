package leaderboard.search.eval

/** M11 candidate-generation input group an M10 offline strategy intent maps to.
  *
  * These are offline study input groups only: they describe how the accepted M10 classification rows are
  * bundled for later *offline* M11 candidate-generation study. They are NOT production routes and they
  * never imply hybrid serving, fallback, score fusion, reranking, production telemetry, or any route
  * switch. The first three groups are backend candidate-generation study inputs; the remaining three are
  * non-backend dispositions (accepted negative-control exclusion, unresolved manual review, no-op noise).
  */
enum M10BeautyQSearchM11CandidateGenerationInputGroup {
  case EsCandidateGenerationStudyInput
  case QdrantCandidateGenerationStudyInput
  case CombinedEsQdrantComparisonStudyInput
  case AcceptedNegativeControlExclusionInput
  case ManualReviewBlockedInput
  case NoOpNoiseInput

  def render: String =
    this match {
      case M10BeautyQSearchM11CandidateGenerationInputGroup.EsCandidateGenerationStudyInput        => "es_candidate_generation_study_input"
      case M10BeautyQSearchM11CandidateGenerationInputGroup.QdrantCandidateGenerationStudyInput    => "qdrant_candidate_generation_study_input"
      case M10BeautyQSearchM11CandidateGenerationInputGroup.CombinedEsQdrantComparisonStudyInput   => "combined_es_qdrant_comparison_study_input"
      case M10BeautyQSearchM11CandidateGenerationInputGroup.AcceptedNegativeControlExclusionInput  => "accepted_negative_control_exclusion_input"
      case M10BeautyQSearchM11CandidateGenerationInputGroup.ManualReviewBlockedInput               => "manual_review_blocked_input"
      case M10BeautyQSearchM11CandidateGenerationInputGroup.NoOpNoiseInput                         => "no_op_noise_input"
    }

  /** Whether this group feeds offline M11 backend candidate-generation study (ES, Qdrant, or combined). */
  def isBackendCandidateGenerationInput: Boolean =
    this match {
      case M10BeautyQSearchM11CandidateGenerationInputGroup.EsCandidateGenerationStudyInput       => true
      case M10BeautyQSearchM11CandidateGenerationInputGroup.QdrantCandidateGenerationStudyInput   => true
      case M10BeautyQSearchM11CandidateGenerationInputGroup.CombinedEsQdrantComparisonStudyInput  => true
      case M10BeautyQSearchM11CandidateGenerationInputGroup.AcceptedNegativeControlExclusionInput => false
      case M10BeautyQSearchM11CandidateGenerationInputGroup.ManualReviewBlockedInput              => false
      case M10BeautyQSearchM11CandidateGenerationInputGroup.NoOpNoiseInput                        => false
    }

  /** Whether this group is an unresolved manual-review input still requiring future resolution. */
  def isUnresolvedManualReviewInput: Boolean =
    this == M10BeautyQSearchM11CandidateGenerationInputGroup.ManualReviewBlockedInput
}

object M10BeautyQSearchM11CandidateGenerationInputGroup {

  /** Stable input-group order: the three backend candidate-generation study inputs first, then the three
    * non-backend dispositions.
    */
  val stableOrder: List[M10BeautyQSearchM11CandidateGenerationInputGroup] = List(
    EsCandidateGenerationStudyInput,
    QdrantCandidateGenerationStudyInput,
    CombinedEsQdrantComparisonStudyInput,
    AcceptedNegativeControlExclusionInput,
    ManualReviewBlockedInput,
    NoOpNoiseInput,
  )

  /** Total, deterministic mapping from an accepted M10 offline strategy intent to its M11 input group. */
  def fromStrategyIntent(
    intent: M10BeautyQSearchOfflineRetrievalStrategyIntent
  ): M10BeautyQSearchM11CandidateGenerationInputGroup =
    intent match {
      case M10BeautyQSearchOfflineRetrievalStrategyIntent.EsOnlyCandidateRetrieval        => EsCandidateGenerationStudyInput
      case M10BeautyQSearchOfflineRetrievalStrategyIntent.QdrantOnlyCandidateRetrieval    => QdrantCandidateGenerationStudyInput
      case M10BeautyQSearchOfflineRetrievalStrategyIntent.CombinedEsQdrantComparison      => CombinedEsQdrantComparisonStudyInput
      case M10BeautyQSearchOfflineRetrievalStrategyIntent.AcceptedNegativeControlExcluded => AcceptedNegativeControlExclusionInput
      case M10BeautyQSearchOfflineRetrievalStrategyIntent.ManualReviewBlocked             => ManualReviewBlockedInput
      case M10BeautyQSearchOfflineRetrievalStrategyIntent.NoOpNoise                       => NoOpNoiseInput
    }
}

/** One M11 candidate-generation input row: an accepted M10 classification row bundled into its offline
  * input group.
  */
final case class M10BeautyQSearchM11InputRow(
  queryId: String,
  category: M10BeautyQSearchQueryCategory,
  strategyIntent: M10BeautyQSearchOfflineRetrievalStrategyIntent,
  inputGroup: M10BeautyQSearchM11CandidateGenerationInputGroup,
)

final case class M10BeautyQSearchRetrievalPolicyReadinessMetric(
  name: String,
  value: String,
)

final case class M10BeautyQSearchRetrievalPolicyReadinessSummary(
  datasetId: String,
  totalQueryCount: Int,
  categoryCounts: List[(M10BeautyQSearchQueryCategory, Int)],
  strategyIntentCounts: List[(M10BeautyQSearchOfflineRetrievalStrategyIntent, Int)],
  inputGroupCounts: List[(M10BeautyQSearchM11CandidateGenerationInputGroup, Int)],
  backendCandidateGenerationInputCount: Int,
  acceptedNegativeControlExclusionInputCount: Int,
  unresolvedManualReviewInputCount: Int,
  noOpNoiseInputCount: Int,
  anchorRows: List[M10BeautyQSearchM11InputRow],
  noiseProbeRows: List[M10BeautyQSearchM11InputRow],
  m11CandidateGenerationInputsReady: Boolean,
  boundary: M10BeautyQSearchOfflineRoutingBoundary,
  verdict: String,
  metrics: List[M10BeautyQSearchRetrievalPolicyReadinessMetric],
)

/** M10 retrieval-policy readiness contract: prepares deterministic offline M11 candidate-generation
  * input groups from the hardened M10B accepted-query classification coverage.
  *
  * This is pure offline eval/planning work only. It consumes the accepted full classification coverage
  * ([[M10BeautyQSearchFullQueryClassification]]) and bundles each accepted row into an offline M11
  * candidate-generation input group. It never calls production `/beauty-search`, never creates an ES or
  * Qdrant client, never runs Elasticsearch or Qdrant, and never touches a route, plugin, DI, or HTTP
  * source. It implements no production routing.
  *
  * The readiness it reports is M11 *offline input-preparation* readiness only. It claims no backend
  * quality green, retrieval quality, production readiness, route activation, serving approval, or actual
  * execution readiness. Mixed-intent rows map to an offline combined ES/Qdrant comparison study input,
  * which is NOT production hybrid serving. Default `/beauty-search` remains ES-backed, the Qdrant opt-in
  * route remains disabled by default, and Qdrant production activation remains not approved.
  */
object M10BeautyQSearchRetrievalPolicyReadiness {

  val MarkdownFilename: String =
    "m10-beautyq-retrieval-policy-readiness.md"

  /** Readiness verdict: M11 offline input-preparation readiness only. Deliberately carries no quality,
    * production-readiness, route-activation, serving-approval, or execution-readiness claim.
    */
  val Verdict: String =
    "m11_candidate_generation_inputs_ready_with_negative_control_exclusion"

  /** Representative M9 anchors carried forward from the accepted classification coverage. */
  val AnchorQueryIds: List[String] =
    M10BeautyQSearchFullQueryClassification.AnchorQueryIds

  /** The two q_noise_* probe ids whose explicit row mappings prove the id prefix is not classification. */
  val NoiseProbeQueryIds: List[String] =
    List("q_noise_004", "q_noise_005")

  /** One M11 input row per accepted classification row, in dataset order. */
  val InputRows: List[M10BeautyQSearchM11InputRow] =
    M10BeautyQSearchFullQueryClassification.FullDecisions.map { decision =>
      M10BeautyQSearchM11InputRow(
        queryId = decision.queryId,
        category = decision.category,
        strategyIntent = decision.strategyIntent,
        inputGroup =
          M10BeautyQSearchM11CandidateGenerationInputGroup.fromStrategyIntent(decision.strategyIntent),
      )
    }

  /** Input-group counts in stable group order; groups with no rows report zero explicitly. */
  val InputGroupCounts: List[(M10BeautyQSearchM11CandidateGenerationInputGroup, Int)] =
    M10BeautyQSearchM11CandidateGenerationInputGroup.stableOrder.map { group =>
      group -> InputRows.count(_.inputGroup == group)
    }

  def inputRowFor(queryId: String): Option[M10BeautyQSearchM11InputRow] =
    InputRows.find(_.queryId == queryId)

  def inputGroupFor(queryId: String): Option[M10BeautyQSearchM11CandidateGenerationInputGroup] =
    inputRowFor(queryId).map(_.inputGroup)

  val DefaultSummary: M10BeautyQSearchRetrievalPolicyReadinessSummary =
    build()

  val MarkdownArtifact: M9OfflineEvalReportArtifact =
    M10BeautyQSearchRetrievalPolicyReadinessRenderer.markdownArtifact(
      filename = MarkdownFilename,
      summary = DefaultSummary,
    )

  def build(): M10BeautyQSearchRetrievalPolicyReadinessSummary = {
    val byGroup = InputGroupCounts.toMap

    val backendCandidateGenerationInputCount =
      InputRows.count(_.inputGroup.isBackendCandidateGenerationInput)
    val acceptedNegativeControlExclusionInputCount =
      byGroup(M10BeautyQSearchM11CandidateGenerationInputGroup.AcceptedNegativeControlExclusionInput)
    val unresolvedManualReviewInputCount =
      InputRows.count(_.inputGroup.isUnresolvedManualReviewInput)
    val noOpNoiseInputCount =
      byGroup(M10BeautyQSearchM11CandidateGenerationInputGroup.NoOpNoiseInput)

    val anchorRows = AnchorQueryIds.flatMap(inputRowFor)
    val noiseProbeRows = NoiseProbeQueryIds.flatMap(inputRowFor)

    // M11 offline input preparation is ready when no row is left as an unresolved manual-review input:
    // accepted negative-control exclusions are a deliberate, resolved disposition and do not block it.
    val m11CandidateGenerationInputsReady = unresolvedManualReviewInputCount == 0

    val summaryWithoutMetrics = M10BeautyQSearchRetrievalPolicyReadinessSummary(
      datasetId = M9BeautyQSearchEvalQueryDataset.Metadata.datasetId,
      totalQueryCount = M10BeautyQSearchFullQueryClassification.DatasetQueryIds.size,
      categoryCounts = M10BeautyQSearchFullQueryClassification.CategoryCounts,
      strategyIntentCounts = M10BeautyQSearchFullQueryClassification.StrategyIntentCounts,
      inputGroupCounts = InputGroupCounts,
      backendCandidateGenerationInputCount = backendCandidateGenerationInputCount,
      acceptedNegativeControlExclusionInputCount = acceptedNegativeControlExclusionInputCount,
      unresolvedManualReviewInputCount = unresolvedManualReviewInputCount,
      noOpNoiseInputCount = noOpNoiseInputCount,
      anchorRows = anchorRows,
      noiseProbeRows = noiseProbeRows,
      m11CandidateGenerationInputsReady = m11CandidateGenerationInputsReady,
      boundary = M10BeautyQSearchOfflineRoutingBoundary.Standing,
      verdict = Verdict,
      metrics = Nil,
    )

    summaryWithoutMetrics.copy(metrics = metrics(summaryWithoutMetrics))
  }

  private def metrics(
    summary: M10BeautyQSearchRetrievalPolicyReadinessSummary
  ): List[M10BeautyQSearchRetrievalPolicyReadinessMetric] = {
    val b = summary.boundary
    val byGroup = summary.inputGroupCounts.toMap
    List(
      metric("total_query_count", summary.totalQueryCount.toString),
      metric("input_group_count_sum", summary.inputGroupCounts.map(_._2).sum.toString),
      metric(
        "es_candidate_generation_study_input_count",
        byGroup(M10BeautyQSearchM11CandidateGenerationInputGroup.EsCandidateGenerationStudyInput).toString,
      ),
      metric(
        "qdrant_candidate_generation_study_input_count",
        byGroup(M10BeautyQSearchM11CandidateGenerationInputGroup.QdrantCandidateGenerationStudyInput).toString,
      ),
      metric(
        "combined_es_qdrant_comparison_study_input_count",
        byGroup(M10BeautyQSearchM11CandidateGenerationInputGroup.CombinedEsQdrantComparisonStudyInput).toString,
      ),
      metric(
        "accepted_negative_control_exclusion_input_count",
        summary.acceptedNegativeControlExclusionInputCount.toString,
      ),
      metric("manual_review_blocked_input_count", summary.unresolvedManualReviewInputCount.toString),
      metric("no_op_noise_input_count", summary.noOpNoiseInputCount.toString),
      metric("backend_candidate_generation_input_count", summary.backendCandidateGenerationInputCount.toString),
      metric("unresolved_manual_review_input_count", summary.unresolvedManualReviewInputCount.toString),
      metric("m11_candidate_generation_inputs_ready", summary.m11CandidateGenerationInputsReady.toString),
      metric("m11_inputs_are_offline_study_inputs_not_production_routes", true.toString),
      metric("offline_strategy_intent_is_not_production_routing", true.toString),
      metric("mixed_intent_is_offline_combined_comparison_only", true.toString),
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

  private def metric(
    name: String,
    value: String,
  ): M10BeautyQSearchRetrievalPolicyReadinessMetric =
    M10BeautyQSearchRetrievalPolicyReadinessMetric(name, value)
}

object M10BeautyQSearchRetrievalPolicyReadinessRenderer {

  def renderMarkdown(summary: M10BeautyQSearchRetrievalPolicyReadinessSummary): String = {
    val builder = new StringBuilder

    line(builder, "# M10 BeautyQ Retrieval-Policy Readiness")
    line(builder, "")
    line(builder, "Offline M11 candidate-generation input-preparation contract over the accepted hardened M10B full")
    line(builder, s"${summary.totalQueryCount}-query classification coverage. This is an offline planning/eval artifact only. The M11 input groups")
    line(builder, "below are offline study inputs only and are NOT production routes; offline strategy intent and offline")
    line(builder, "input groups are distinct from production routing. This artifact reports M11 offline input-preparation")
    line(builder, "readiness only: it is not backend quality green, not retrieval quality, not production readiness, not")
    line(builder, "route activation, not serving approval, and not actual execution readiness.")
    line(builder, "")
    line(builder, "## Summary")
    line(builder, "")
    line(builder, s"- dataset_id: ${renderText(summary.datasetId)}")
    line(builder, s"- verdict: ${renderText(summary.verdict)}")
    line(builder, s"- total_query_count: ${summary.totalQueryCount}")
    line(builder, s"- backend_candidate_generation_input_count: ${summary.backendCandidateGenerationInputCount}")
    line(builder, s"- accepted_negative_control_exclusion_input_count: ${summary.acceptedNegativeControlExclusionInputCount}")
    line(builder, s"- unresolved_manual_review_input_count: ${summary.unresolvedManualReviewInputCount}")
    line(builder, s"- no_op_noise_input_count: ${summary.noOpNoiseInputCount}")
    line(builder, s"- m11_candidate_generation_inputs_ready: ${summary.m11CandidateGenerationInputsReady}")
    line(builder, "")
    line(builder, "## M10 category counts")
    line(builder, "")
    line(builder, "| category | count |")
    line(builder, "|---|---|")
    summary.categoryCounts.foreach { case (category, count) =>
      line(builder, s"| ${renderText(category.render)} | $count |")
    }
    line(builder, "")
    line(builder, "## M10 offline strategy intent counts")
    line(builder, "")
    line(builder, "These are offline study intents only, not production routes.")
    line(builder, "")
    line(builder, "| offline_strategy_intent | count |")
    line(builder, "|---|---|")
    summary.strategyIntentCounts.foreach { case (intent, count) =>
      line(builder, s"| ${renderText(intent.render)} | $count |")
    }
    line(builder, "")
    line(builder, "## M11 candidate-generation input group counts")
    line(builder, "")
    line(builder, "These are offline study input groups only, not production routes.")
    line(builder, "")
    line(builder, "| m11_input_group | count |")
    line(builder, "|---|---|")
    summary.inputGroupCounts.foreach { case (group, count) =>
      line(builder, s"| ${renderText(group.render)} | $count |")
    }
    line(builder, "")
    line(builder, "## Representative anchors")
    line(builder, "")
    line(builder, "| query_id | category | offline_strategy_intent | m11_input_group |")
    line(builder, "|---|---|---|---|")
    summary.anchorRows.foreach(row => line(builder, renderRow(row)))
    line(builder, "")
    line(builder, "## Noise-probe row mappings")
    line(builder, "")
    line(builder, "Both ids share the q_noise_* prefix yet land in different M11 input groups: the prefix carries no")
    line(builder, "classification meaning; the deterministic offline signals do.")
    line(builder, "")
    line(builder, "| query_id | category | offline_strategy_intent | m11_input_group |")
    line(builder, "|---|---|---|---|")
    summary.noiseProbeRows.foreach(row => line(builder, renderRow(row)))
    line(builder, "")
    line(builder, "## Metrics")
    line(builder, "")
    line(builder, "| metric | value |")
    line(builder, "|---|---|")
    summary.metrics.foreach(metric => line(builder, s"| ${renderText(metric.name)} | ${renderText(metric.value)} |"))
    line(builder, "")
    line(builder, "## Boundary")
    line(builder, "")
    line(builder, "Boundary posture is reported as structured booleans in the Metrics table above, not as prose negations.")
    line(builder, "M11 input groups are offline study inputs, not production routes; offline strategy intent and offline")
    line(builder, "input groups are distinct from production routing.")

    builder.result()
  }

  private def renderRow(row: M10BeautyQSearchM11InputRow): String =
    s"| ${renderText(row.queryId)} | ${renderText(row.category.render)} | ${renderText(row.strategyIntent.render)} | ${renderText(row.inputGroup.render)} |"

  def markdownArtifact(
    filename: String,
    summary: M10BeautyQSearchRetrievalPolicyReadinessSummary,
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
