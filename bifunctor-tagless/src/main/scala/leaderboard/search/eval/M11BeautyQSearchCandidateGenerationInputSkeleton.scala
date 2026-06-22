package leaderboard.search.eval

/** Offline backend a planned M11 candidate-generation request leg would study.
  *
  * These are offline study request legs only. An `Es` or `Qdrant` leg is a deterministic *request shape*
  * to feed a later offline candidate-generation study; it is NOT a production route, NOT a backend call,
  * and NOT an execution. No ES or Qdrant client is created and neither backend is executed.
  */
enum M11BeautyQSearchCandidateGenerationBackend {
  case Es
  case Qdrant

  def render: String =
    this match {
      case M11BeautyQSearchCandidateGenerationBackend.Es     => "es"
      case M11BeautyQSearchCandidateGenerationBackend.Qdrant => "qdrant"
    }
}

object M11BeautyQSearchCandidateGenerationBackend {
  val stableOrder: List[M11BeautyQSearchCandidateGenerationBackend] = List(Es, Qdrant)

  /** Deterministic, total mapping from an accepted M10 input group to its offline request legs.
    *
    * Backend study inputs get one (ES-only, Qdrant-only) or two (combined comparison) offline legs.
    * Non-backend dispositions (accepted negative-control exclusion, manual-review blocked, no-op noise)
    * get no legs and are therefore kept out of every backend request leg.
    */
  def requestLegsFor(
    group: M10BeautyQSearchM11CandidateGenerationInputGroup
  ): List[M11BeautyQSearchCandidateGenerationBackend] =
    group match {
      case M10BeautyQSearchM11CandidateGenerationInputGroup.EsCandidateGenerationStudyInput       => List(Es)
      case M10BeautyQSearchM11CandidateGenerationInputGroup.QdrantCandidateGenerationStudyInput   => List(Qdrant)
      case M10BeautyQSearchM11CandidateGenerationInputGroup.CombinedEsQdrantComparisonStudyInput  => List(Es, Qdrant)
      case M10BeautyQSearchM11CandidateGenerationInputGroup.AcceptedNegativeControlExclusionInput => Nil
      case M10BeautyQSearchM11CandidateGenerationInputGroup.ManualReviewBlockedInput              => Nil
      case M10BeautyQSearchM11CandidateGenerationInputGroup.NoOpNoiseInput                        => Nil
    }
}

/** One M11 candidate-generation request shape: an accepted M10C input row expressed as a deterministic
  * offline request skeleton with zero, one, or two backend request legs.
  *
  * This is a request *shape* only, never a backend execution. A combined-comparison shape carries both an
  * ES leg and a Qdrant leg as offline study inputs; it does NOT imply production hybrid serving, fallback,
  * score fusion, or reranking.
  */
final case class M11BeautyQSearchCandidateGenerationRequestShape(
  queryId: String,
  category: M10BeautyQSearchQueryCategory,
  strategyIntent: M10BeautyQSearchOfflineRetrievalStrategyIntent,
  inputGroup: M10BeautyQSearchM11CandidateGenerationInputGroup,
  requestLegs: List[M11BeautyQSearchCandidateGenerationBackend],
) {

  /** Whether this shape carries an offline ES request leg. */
  def hasEsLeg: Boolean = requestLegs.contains(M11BeautyQSearchCandidateGenerationBackend.Es)

  /** Whether this shape carries an offline Qdrant request leg. */
  def hasQdrantLeg: Boolean = requestLegs.contains(M11BeautyQSearchCandidateGenerationBackend.Qdrant)

  /** Whether this shape is an offline combined ES/Qdrant comparison study input (both legs present). */
  def isCombinedComparison: Boolean = hasEsLeg && hasQdrantLeg

  /** Whether this shape contributes any backend request leg at all. */
  def hasAnyRequestLeg: Boolean = requestLegs.nonEmpty
}

final case class M11BeautyQSearchCandidateGenerationInputSkeletonMetric(
  name: String,
  value: String,
)

final case class M11BeautyQSearchCandidateGenerationInputSkeletonSummary(
  datasetId: String,
  totalQueryCount: Int,
  consumedM10ReadinessVerdict: String,
  rowGroupCounts: List[(M10BeautyQSearchM11CandidateGenerationInputGroup, Int)],
  esOnlyRowCount: Int,
  qdrantOnlyRowCount: Int,
  combinedComparisonRowCount: Int,
  acceptedNegativeControlExclusionRowCount: Int,
  manualReviewBlockedRowCount: Int,
  noOpNoiseRowCount: Int,
  esRequestLegRowCount: Int,
  qdrantRequestLegRowCount: Int,
  combinedComparisonPairRowCount: Int,
  anchorShapes: List[M11BeautyQSearchCandidateGenerationRequestShape],
  noiseProbeShapes: List[M11BeautyQSearchCandidateGenerationRequestShape],
  m11CandidateGenerationInputSkeletonReady: Boolean,
  boundary: M10BeautyQSearchOfflineRoutingBoundary,
  verdict: String,
  metrics: List[M11BeautyQSearchCandidateGenerationInputSkeletonMetric],
)

/** M11 candidate-generation input skeleton contract: turns the accepted M10C retrieval-policy readiness
  * rows into deterministic *offline* candidate-generation request shapes.
  *
  * This is pure offline eval/planning work only. It consumes the accepted M10C readiness
  * ([[M10BeautyQSearchRetrievalPolicyReadiness]]) — including its verdict and standing boundary — and
  * derives one request shape per accepted row. It defines request shapes only, never backend execution:
  * it never calls production `/beauty-search`, never creates an ES or Qdrant client, never runs
  * Elasticsearch or Qdrant, and never touches a route, plugin, DI, or HTTP source. It implements no
  * candidate retrieval, scoring, fusion, reranking, fallback, or telemetry.
  *
  * The readiness it reports is M11 *offline input-skeleton* readiness only. It claims no backend quality
  * green, retrieval quality, production readiness, route activation, serving approval, or execution
  * readiness. A combined-comparison shape carries both an ES and a Qdrant offline leg as study inputs,
  * which is NOT production hybrid serving. Default `/beauty-search` stays ES-backed, the Qdrant opt-in
  * route stays disabled by default, and Qdrant production activation stays not approved.
  */
object M11BeautyQSearchCandidateGenerationInputSkeleton {

  val MarkdownFilename: String =
    "m11-beautyq-candidate-generation-input-skeleton.md"

  /** Readiness verdict: M11 offline input-skeleton readiness only. Deliberately carries no quality,
    * retrieval-quality, production-readiness, route-activation, serving-approval, or execution-readiness
    * claim.
    */
  val Verdict: String =
    "m11_candidate_generation_input_skeleton_ready"

  /** Accepted M10C readiness verdict this skeleton consumes. */
  val ConsumedM10ReadinessVerdict: String =
    M10BeautyQSearchRetrievalPolicyReadiness.Verdict

  /** Representative M9 anchors carried forward from the accepted M10C readiness rows. */
  val AnchorQueryIds: List[String] =
    M10BeautyQSearchRetrievalPolicyReadiness.AnchorQueryIds

  /** The two q_noise_* probe ids whose explicit request shapes prove the id prefix is not classification. */
  val NoiseProbeQueryIds: List[String] =
    M10BeautyQSearchRetrievalPolicyReadiness.NoiseProbeQueryIds

  /** One M11 request shape per accepted M10C input row, in dataset order. */
  val RequestShapes: List[M11BeautyQSearchCandidateGenerationRequestShape] =
    M10BeautyQSearchRetrievalPolicyReadiness.InputRows.map { row =>
      M11BeautyQSearchCandidateGenerationRequestShape(
        queryId = row.queryId,
        category = row.category,
        strategyIntent = row.strategyIntent,
        inputGroup = row.inputGroup,
        requestLegs = M11BeautyQSearchCandidateGenerationBackend.requestLegsFor(row.inputGroup),
      )
    }

  /** Row-group counts in stable group order, carried forward verbatim from accepted M10C readiness. */
  val RowGroupCounts: List[(M10BeautyQSearchM11CandidateGenerationInputGroup, Int)] =
    M10BeautyQSearchRetrievalPolicyReadiness.InputGroupCounts

  def requestShapeFor(queryId: String): Option[M11BeautyQSearchCandidateGenerationRequestShape] =
    RequestShapes.find(_.queryId == queryId)

  val DefaultSummary: M11BeautyQSearchCandidateGenerationInputSkeletonSummary =
    build()

  val MarkdownArtifact: M9OfflineEvalReportArtifact =
    M11BeautyQSearchCandidateGenerationInputSkeletonRenderer.markdownArtifact(
      filename = MarkdownFilename,
      summary = DefaultSummary,
    )

  def build(): M11BeautyQSearchCandidateGenerationInputSkeletonSummary = {
    val byGroup = RowGroupCounts.toMap

    def groupCount(group: M10BeautyQSearchM11CandidateGenerationInputGroup): Int =
      byGroup.getOrElse(group, 0)

    val esRequestLegRowCount =
      RequestShapes.count(_.hasEsLeg)
    val qdrantRequestLegRowCount =
      RequestShapes.count(_.hasQdrantLeg)
    val combinedComparisonPairRowCount =
      RequestShapes.count(_.isCombinedComparison)

    val anchorShapes = AnchorQueryIds.flatMap(requestShapeFor)
    val noiseProbeShapes = NoiseProbeQueryIds.flatMap(requestShapeFor)

    // The input skeleton is ready when no accepted row is left as an unresolved manual-review input.
    // Accepted negative-control exclusions are a deliberate, resolved disposition with no request legs.
    val m11CandidateGenerationInputSkeletonReady =
      groupCount(M10BeautyQSearchM11CandidateGenerationInputGroup.ManualReviewBlockedInput) == 0

    val summaryWithoutMetrics = M11BeautyQSearchCandidateGenerationInputSkeletonSummary(
      datasetId = M9BeautyQSearchEvalQueryDataset.Metadata.datasetId,
      totalQueryCount = RequestShapes.size,
      consumedM10ReadinessVerdict = ConsumedM10ReadinessVerdict,
      rowGroupCounts = RowGroupCounts,
      esOnlyRowCount = groupCount(M10BeautyQSearchM11CandidateGenerationInputGroup.EsCandidateGenerationStudyInput),
      qdrantOnlyRowCount = groupCount(M10BeautyQSearchM11CandidateGenerationInputGroup.QdrantCandidateGenerationStudyInput),
      combinedComparisonRowCount =
        groupCount(M10BeautyQSearchM11CandidateGenerationInputGroup.CombinedEsQdrantComparisonStudyInput),
      acceptedNegativeControlExclusionRowCount =
        groupCount(M10BeautyQSearchM11CandidateGenerationInputGroup.AcceptedNegativeControlExclusionInput),
      manualReviewBlockedRowCount =
        groupCount(M10BeautyQSearchM11CandidateGenerationInputGroup.ManualReviewBlockedInput),
      noOpNoiseRowCount = groupCount(M10BeautyQSearchM11CandidateGenerationInputGroup.NoOpNoiseInput),
      esRequestLegRowCount = esRequestLegRowCount,
      qdrantRequestLegRowCount = qdrantRequestLegRowCount,
      combinedComparisonPairRowCount = combinedComparisonPairRowCount,
      anchorShapes = anchorShapes,
      noiseProbeShapes = noiseProbeShapes,
      m11CandidateGenerationInputSkeletonReady = m11CandidateGenerationInputSkeletonReady,
      boundary = M10BeautyQSearchRetrievalPolicyReadiness.DefaultSummary.boundary,
      verdict = Verdict,
      metrics = Nil,
    )

    summaryWithoutMetrics.copy(metrics = metrics(summaryWithoutMetrics))
  }

  private def metrics(
    summary: M11BeautyQSearchCandidateGenerationInputSkeletonSummary
  ): List[M11BeautyQSearchCandidateGenerationInputSkeletonMetric] = {
    val b = summary.boundary
    List(
      metric("total_query_count", summary.totalQueryCount.toString),
      metric("consumed_m10_readiness_verdict", summary.consumedM10ReadinessVerdict),
      metric("row_group_count_sum", summary.rowGroupCounts.map(_._2).sum.toString),
      metric("es_only_row_count", summary.esOnlyRowCount.toString),
      metric("qdrant_only_row_count", summary.qdrantOnlyRowCount.toString),
      metric("combined_comparison_row_count", summary.combinedComparisonRowCount.toString),
      metric("accepted_negative_control_exclusion_row_count", summary.acceptedNegativeControlExclusionRowCount.toString),
      metric("manual_review_blocked_row_count", summary.manualReviewBlockedRowCount.toString),
      metric("no_op_noise_row_count", summary.noOpNoiseRowCount.toString),
      metric("es_request_leg_row_count", summary.esRequestLegRowCount.toString),
      metric("qdrant_request_leg_row_count", summary.qdrantRequestLegRowCount.toString),
      metric("combined_comparison_pair_row_count", summary.combinedComparisonPairRowCount.toString),
      metric("m11_candidate_generation_input_skeleton_ready", summary.m11CandidateGenerationInputSkeletonReady.toString),
      metric("m11_request_shapes_are_offline_study_inputs_not_production_routes", true.toString),
      metric("m11_request_shapes_are_request_shapes_not_backend_execution", true.toString),
      metric("combined_comparison_is_offline_study_input_not_hybrid_serving", true.toString),
      metric("accepted_negative_control_has_no_backend_request_leg", true.toString),
      metric("manual_and_no_op_have_no_backend_request_leg", true.toString),
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
  ): M11BeautyQSearchCandidateGenerationInputSkeletonMetric =
    M11BeautyQSearchCandidateGenerationInputSkeletonMetric(name, value)
}

object M11BeautyQSearchCandidateGenerationInputSkeletonRenderer {

  def renderMarkdown(summary: M11BeautyQSearchCandidateGenerationInputSkeletonSummary): String = {
    val builder = new StringBuilder

    line(builder, "# M11 BeautyQ Candidate-Generation Input Skeleton")
    line(builder, "")
    line(builder, "Offline candidate-generation request-shape skeleton over the accepted M10C retrieval-policy readiness")
    line(builder, "rows. This is an offline planning/eval artifact only. The request shapes below are offline study inputs")
    line(builder, "only and are NOT production routes and NOT backend execution: no ES or Qdrant client is created and")
    line(builder, "neither backend is run. This artifact reports M11 offline input-skeleton readiness only: it is not backend")
    line(builder, "quality green, not retrieval quality, not production readiness, not route activation, not serving approval,")
    line(builder, "and not actual execution readiness.")
    line(builder, "")
    line(builder, "## Summary")
    line(builder, "")
    line(builder, s"- dataset_id: ${renderText(summary.datasetId)}")
    line(builder, s"- verdict: ${renderText(summary.verdict)}")
    line(builder, s"- consumed_m10_readiness_verdict: ${renderText(summary.consumedM10ReadinessVerdict)}")
    line(builder, s"- total_query_count: ${summary.totalQueryCount}")
    line(builder, s"- es_request_leg_row_count: ${summary.esRequestLegRowCount}")
    line(builder, s"- qdrant_request_leg_row_count: ${summary.qdrantRequestLegRowCount}")
    line(builder, s"- combined_comparison_pair_row_count: ${summary.combinedComparisonPairRowCount}")
    line(builder, s"- accepted_negative_control_exclusion_row_count: ${summary.acceptedNegativeControlExclusionRowCount}")
    line(builder, s"- m11_candidate_generation_input_skeleton_ready: ${summary.m11CandidateGenerationInputSkeletonReady}")
    line(builder, "")
    line(builder, "## M11 row-group counts")
    line(builder, "")
    line(builder, "These are offline study input groups only, not production routes.")
    line(builder, "")
    line(builder, "| m11_input_group | count |")
    line(builder, "|---|---|")
    summary.rowGroupCounts.foreach { case (group, count) =>
      line(builder, s"| ${renderText(group.render)} | $count |")
    }
    line(builder, "")
    line(builder, "## Planned offline request-leg counts")
    line(builder, "")
    line(builder, "Request legs are offline study request shapes only, not backend execution. ES legs = ES-only rows +")
    line(builder, "combined-comparison ES legs; Qdrant legs = Qdrant-only rows + combined-comparison Qdrant legs.")
    line(builder, "")
    line(builder, "| metric | value |")
    line(builder, "|---|---|")
    line(builder, s"| es_request_leg_row_count | ${summary.esRequestLegRowCount} |")
    line(builder, s"| qdrant_request_leg_row_count | ${summary.qdrantRequestLegRowCount} |")
    line(builder, s"| combined_comparison_pair_row_count | ${summary.combinedComparisonPairRowCount} |")
    line(builder, "")
    line(builder, "## Representative anchors")
    line(builder, "")
    line(builder, "| query_id | category | offline_strategy_intent | m11_input_group | request_legs |")
    line(builder, "|---|---|---|---|---|")
    summary.anchorShapes.foreach(shape => line(builder, renderRow(shape)))
    line(builder, "")
    line(builder, "## Noise-probe request shapes")
    line(builder, "")
    line(builder, "Both ids share the q_noise_* prefix yet land in different M11 request shapes: q_noise_004 is a combined")
    line(builder, "ES/Qdrant offline comparison shape, q_noise_005 is an accepted negative-control exclusion with no legs.")
    line(builder, "")
    line(builder, "| query_id | category | offline_strategy_intent | m11_input_group | request_legs |")
    line(builder, "|---|---|---|---|---|")
    summary.noiseProbeShapes.foreach(shape => line(builder, renderRow(shape)))
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
    line(builder, "M11 request shapes are offline study inputs, not production routes and not backend execution; a combined")
    line(builder, "comparison shape is an offline study input and does not imply production hybrid serving.")

    builder.result()
  }

  private def renderRow(shape: M11BeautyQSearchCandidateGenerationRequestShape): String = {
    val legs =
      if (shape.requestLegs.isEmpty) "(none)"
      else shape.requestLegs.map(_.render).mkString(", ")
    s"| ${renderText(shape.queryId)} | ${renderText(shape.category.render)} | ${renderText(shape.strategyIntent.render)} | ${renderText(shape.inputGroup.render)} | ${renderText(legs)} |"
  }

  def markdownArtifact(
    filename: String,
    summary: M11BeautyQSearchCandidateGenerationInputSkeletonSummary,
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
