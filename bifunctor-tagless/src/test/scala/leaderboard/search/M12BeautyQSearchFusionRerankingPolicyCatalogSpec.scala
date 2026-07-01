package leaderboard.search

import leaderboard.search.eval.{
  M10BeautyQSearchOfflineRetrievalStrategyIntent,
  M10BeautyQSearchQueryCategory,
  M12BeautyQSearchFusionRerankingExperimentPlan,
  M12BeautyQSearchFusionRerankingExperimentPlanGroup,
  M12BeautyQSearchFusionRerankingExperimentPlanMetric,
  M12BeautyQSearchFusionRerankingExperimentPlanRenderer,
  M12BeautyQSearchFusionRerankingInputGroup,
  M12BeautyQSearchFusionRerankingInputScaffold,
  M12BeautyQSearchFusionRerankingPolicyCatalog,
  M12BeautyQSearchFusionRerankingPolicyCatalogMetric,
  M12BeautyQSearchFusionRerankingPolicyCatalogRenderer,
  M12BeautyQSearchFusionRerankingPolicyOption,
}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.charset.StandardCharsets
import scala.io.Source
import scala.util.Using

final class M12BeautyQSearchFusionRerankingPolicyCatalogSpec extends AnyWordSpec {

  private val policyCatalogArtifactPath: String =
    "/leaderboard/search/eval/m12-beautyq-fusion-reranking-policy-catalog.md"

  // Marketing/readiness tokens that must never appear as positive claims in either rendered artifact.
  private val forbiddenRenderedTokens: List[String] = List(
    "production_ready",
    "qdrant_ready",
    "hybrid_ready",
    "is production ready",
    "production-ready",
    "quality is green",
    "serving approval granted",
    "route activated",
    "backend execution ready",
    "execution ready",
    "hybrid serving enabled",
    "fallback enabled",
    "score fusion enabled",
    "reranking enabled",
    "fusion enabled",
    "scoring enabled",
  )

  // Tokens that would betray a fabricated candidate/result/fusion/reranking payload. None may appear.
  private val fabricationTokens: List[String] = List(
    "took_ms",
    "\"hits\"",
    "\"_id\"",
    "\"results\"",
    "\"payload\"",
    "doc_id",
    "cosine_score",
    "embedding",
    "fused_score:",
    "reranked_position:",
    "quality_label:",
    "fusion_output:",
    "reranking_output:",
  )

  "M12BeautyQSearchFusionRerankingPolicyCatalog inputs" should {

    "consume the accepted M12A input scaffold and still total 89 rows" in {
      val summary = M12BeautyQSearchFusionRerankingPolicyCatalog.DefaultSummary
      val scaffold = M12BeautyQSearchFusionRerankingInputScaffold.DefaultSummary

      assert(scaffold.fusionRerankingInputRows == 89)
      assert(summary.consumedInputRowCount == 89)
      assert(summary.consumedM12InputScaffoldVerdict == "m12_fusion_reranking_input_scaffold_ready_schema_only")
      assert(
        summary.consumedM12InputScaffoldVerdict == M12BeautyQSearchFusionRerankingInputScaffold.Verdict,
      )
      assert(metricValue(summary.metrics, "consumed_input_row_count") == "89")
    }
  }

  "M12BeautyQSearchFusionRerankingPolicyCatalog" should {

    "expose stable, unique, non-executable policy names" in {
      val summary = M12BeautyQSearchFusionRerankingPolicyCatalog.DefaultSummary

      val expected = List(
        "es_baseline_passthrough",
        "qdrant_baseline_passthrough",
        "combined_union_placeholder",
        "combined_intersection_placeholder",
        "tie_breaker_placeholder",
        "accepted_negative_control_exclusion_policy",
      )

      assert(summary.policyNames.map(_.render) == expected)
      assert(summary.policyNames.map(_.render).distinct.size == expected.size)
      assert(summary.policyCount == 6)
      assert(summary.policyNames.forall(!_.isExecutable))
      assert(metricValue(summary.metrics, "policy_count") == "6")
      assert(metricValue(summary.metrics, "policy_names_unique") == "6")
    }

    "categorize policies as backend baseline, combined experiment, or exclusion" in {
      val summary = M12BeautyQSearchFusionRerankingPolicyCatalog.DefaultSummary

      assert(summary.policyBackendBaselineCount == 2)
      assert(summary.policyCombinedExperimentCount == 3)
      assert(summary.policyExclusionCount == 1)
      assert(
        summary.policyBackendBaselineCount + summary.policyCombinedExperimentCount +
          summary.policyExclusionCount == summary.policyCount,
      )
      assert(
        summary.policyNames.filter(_.isBackendBaseline).toSet == Set(
          M12BeautyQSearchFusionRerankingPolicyOption.EsBaselinePassthrough,
          M12BeautyQSearchFusionRerankingPolicyOption.QdrantBaselinePassthrough,
        ),
      )
      assert(
        summary.policyNames.filter(_.isCombinedExperiment).toSet == Set(
          M12BeautyQSearchFusionRerankingPolicyOption.CombinedUnionPlaceholder,
          M12BeautyQSearchFusionRerankingPolicyOption.CombinedIntersectionPlaceholder,
          M12BeautyQSearchFusionRerankingPolicyOption.TieBreakerPlaceholder,
        ),
      )
      assert(
        summary.policyNames.filter(_.isExclusion).toSet == Set(
          M12BeautyQSearchFusionRerankingPolicyOption.AcceptedNegativeControlExclusionPolicy,
        ),
      )
    }

    "assign each M12A input group to the right named policy options" in {
      val options = M12BeautyQSearchFusionRerankingPolicyOption.optionsForInputGroup _
      assert(options(M12BeautyQSearchFusionRerankingInputGroup.EsOnlyPlaceholderInput) ==
        List(M12BeautyQSearchFusionRerankingPolicyOption.EsBaselinePassthrough))
      assert(options(M12BeautyQSearchFusionRerankingInputGroup.QdrantOnlyPlaceholderInput) ==
        List(M12BeautyQSearchFusionRerankingPolicyOption.QdrantBaselinePassthrough))
      assert(options(M12BeautyQSearchFusionRerankingInputGroup.CombinedComparisonPlaceholderInput) ==
        List(
          M12BeautyQSearchFusionRerankingPolicyOption.CombinedUnionPlaceholder,
          M12BeautyQSearchFusionRerankingPolicyOption.CombinedIntersectionPlaceholder,
          M12BeautyQSearchFusionRerankingPolicyOption.TieBreakerPlaceholder,
        ))
      assert(options(M12BeautyQSearchFusionRerankingInputGroup.AcceptedNegativeControlExclusionInput) ==
        List(M12BeautyQSearchFusionRerankingPolicyOption.AcceptedNegativeControlExclusionPolicy))
      assert(options(M12BeautyQSearchFusionRerankingInputGroup.ManualOrNoOpExclusionInput) == Nil)
    }
  }

  "M12BeautyQSearchFusionRerankingPolicyCatalog experiment plan counts" should {

    "produce the M12 policy-plan counts the catalog requires" in {
      val summary = M12BeautyQSearchFusionRerankingPolicyCatalog.DefaultSummary

      assert(summary.esBaselineRows == 15)
      assert(summary.qdrantBaselineRows == 1)
      assert(summary.combinedExperimentRows == 72)
      assert(summary.acceptedNegativeControlExclusionRows == 1)
      assert(summary.manualOrNoOpRows == 0)
      assert(summary.executablePolicyRows == 0)
      assert(summary.realScoredOrRerankedRows == 0)
      assert(
        summary.esBaselineRows + summary.qdrantBaselineRows + summary.combinedExperimentRows +
          summary.acceptedNegativeControlExclusionRows + summary.manualOrNoOpRows == 89,
      )
      assert(metricValue(summary.metrics, "es_baseline_rows") == "15")
      assert(metricValue(summary.metrics, "qdrant_baseline_rows") == "1")
      assert(metricValue(summary.metrics, "combined_experiment_rows") == "72")
      assert(metricValue(summary.metrics, "accepted_negative_control_exclusion_rows") == "1")
      assert(metricValue(summary.metrics, "manual_or_no_op_rows") == "0")
      assert(metricValue(summary.metrics, "executable_policy_rows") == "0")
      assert(metricValue(summary.metrics, "real_scored_or_reranked_rows") == "0")
    }
  }

  "M12BeautyQSearchFusionRerankingPolicyCatalog verdict and boundary" should {

    "carry the schema-only policy-catalog readiness verdict, not scoring or execution readiness" in {
      val summary = M12BeautyQSearchFusionRerankingPolicyCatalog.DefaultSummary

      assert(summary.verdict == "m12_fusion_reranking_policy_catalog_ready_schema_only")
      assert(summary.m12FusionRerankingPolicyCatalogReady)
      assert(metricValue(summary.metrics, "m12_fusion_reranking_policy_catalog_ready") == "true")
      assert(metricValue(summary.metrics, "m12_catalog_is_schema_only_not_scoring") == "true")
      assert(metricValue(summary.metrics, "m12_catalog_is_schema_only_not_fusion") == "true")
      assert(metricValue(summary.metrics, "m12_catalog_is_schema_only_not_reranking") == "true")
      assert(metricValue(summary.metrics, "m12_catalog_is_schema_only_not_candidate_retrieval") == "true")
      assert(metricValue(summary.metrics, "m12_catalog_is_schema_only_not_backend_execution") == "true")
      assert(metricValue(summary.metrics, "m12_catalog_is_offline_not_production_routing") == "true")
      assert(metricValue(summary.metrics, "m12_combined_experiment_policies_are_placeholders_only") == "true")
      assert(metricValue(summary.metrics, "m12_combined_experiment_policies_do_not_imply_hybrid_serving") == "true")
      assert(!summary.boundary.qualityGreenClaimed)
      assert(!summary.boundary.productionReadinessClaimed)
      assert(!summary.boundary.routeActivationClaimed)
      assert(!summary.boundary.servingApprovalClaimed)
    }

    "require and implement no real backend/client/route/plugin/DI/HTTP path" in {
      val b = M12BeautyQSearchFusionRerankingPolicyCatalog.DefaultSummary.boundary
      val metrics = M12BeautyQSearchFusionRerankingPolicyCatalog.DefaultSummary.metrics

      assert(!b.productionBeautySearchCalled)
      assert(!b.esClientCreated)
      assert(!b.qdrantClientCreated)
      assert(!b.esExecuted)
      assert(!b.qdrantExecuted)
      assert(!b.routePluginDiHttpInvolved)
      assert(!b.realBackendCallRequired)
      assert(!b.realBackendCallImplemented)
      assert(metricValue(metrics, "production_beauty_search_called") == "false")
      assert(metricValue(metrics, "es_client_created") == "false")
      assert(metricValue(metrics, "qdrant_client_created") == "false")
      assert(metricValue(metrics, "es_executed") == "false")
      assert(metricValue(metrics, "qdrant_executed") == "false")
      assert(metricValue(metrics, "route_plugin_di_http_involved") == "false")
      assert(metricValue(metrics, "real_backend_call_required") == "false")
      assert(metricValue(metrics, "real_backend_call_implemented") == "false")
    }

    "keep all production and Qdrant activation boundaries false/not approved" in {
      val b = M12BeautyQSearchFusionRerankingPolicyCatalog.DefaultSummary.boundary
      val metrics = M12BeautyQSearchFusionRerankingPolicyCatalog.DefaultSummary.metrics

      assert(b.defaultBeautySearchEsBacked)
      assert(b.qdrantOptInDisabledByDefault)
      assert(!b.qdrantProductionActivationApproved)
      assert(!b.productionRouteActivated)
      assert(!b.defaultRouteSwitched)
      assert(metricValue(metrics, "qdrant_production_activation_approved") == "false")
      assert(metricValue(metrics, "production_route_activated") == "false")
      assert(metricValue(metrics, "default_route_switched") == "false")
      assert(metricValue(metrics, "default_beauty_search_es_backed") == "true")
      assert(metricValue(metrics, "qdrant_opt_in_disabled_by_default") == "true")
    }

    "keep forbidden production/hybrid/fallback/fusion/reranking/telemetry boundaries false" in {
      val b = M12BeautyQSearchFusionRerankingPolicyCatalog.DefaultSummary.boundary
      val metrics = M12BeautyQSearchFusionRerankingPolicyCatalog.DefaultSummary.metrics

      assert(!b.hybridServingImplied)
      assert(!b.fallbackImplied)
      assert(!b.scoreFusionImplied)
      assert(!b.rerankingImplied)
      assert(!b.productionTelemetryImplied)
      assert(metricValue(metrics, "fallback_implied") == "false")
      assert(metricValue(metrics, "score_fusion_implied") == "false")
      assert(metricValue(metrics, "reranking_implied") == "false")
      assert(metricValue(metrics, "production_telemetry_implied") == "false")
    }
  }

  "M12BeautyQSearchFusionRerankingPolicyCatalog fabrications" should {

    "fabricate no candidate ids, provider ids, scores, ranks, backend responses, fused scores, reranked positions, quality labels, fusion outputs, or reranking outputs" in {
      val metrics = M12BeautyQSearchFusionRerankingPolicyCatalog.DefaultSummary.metrics
      val rendered = M12BeautyQSearchFusionRerankingPolicyCatalog.MarkdownArtifact.contents.toLowerCase

      fabricationTokens.foreach { token =>
        assert(!rendered.contains(token.toLowerCase), s"fabrication token present: $token")
      }
      assert(metricValue(metrics, "no_real_candidate_ids_provider_ids_scores_ranks_fabricated") == "true")
      assert(metricValue(metrics, "no_real_backend_responses_fabricated") == "true")
      assert(metricValue(metrics, "no_fused_scores_reranked_positions_quality_labels_fabricated") == "true")
      assert(metricValue(metrics, "no_fusion_outputs_reranking_outputs_fabricated") == "true")
    }
  }

  "M12BeautyQSearchFusionRerankingPolicyCatalog artifact" should {

    "carry no marketing readiness tokens as positive claims" in {
      val rendered = M12BeautyQSearchFusionRerankingPolicyCatalog.MarkdownArtifact.contents.toLowerCase

      forbiddenRenderedTokens.foreach { token =>
        assert(!rendered.contains(token), s"forbidden token present: $token")
      }
    }

    "include every required section and schema-only disclaimer" in {
      val rendered = M12BeautyQSearchFusionRerankingPolicyCatalog.MarkdownArtifact.contents

      assert(rendered.contains("## Policy catalog names"))
      assert(rendered.contains("## Experiment plan group counts"))
      assert(rendered.contains("## q_noise_004 and q_noise_005 mappings"))
      assert(rendered.contains("## Metrics"))
      assert(rendered.contains("## Boundary summary"))
      assert(rendered.contains("consumed_m12a_input_scaffold_verdict: m12_fusion_reranking_input_scaffold_ready_schema_only"))
      assert(rendered.contains("consumed_m11b_result_schema_verdict: m11_candidate_generation_result_schema_ready"))
      assert(rendered.contains("consumed_m11c_boundary_failure_matrix_verdict: m11_candidate_generation_boundary_failure_matrix_ready"))
      assert(rendered.contains("es_baseline_rows: 15"))
      assert(rendered.contains("qdrant_baseline_rows: 1"))
      assert(rendered.contains("combined_experiment_rows: 72"))
      assert(rendered.contains("accepted_negative_control_exclusion_rows: 1"))
      assert(rendered.contains("manual_or_no_op_rows: 0"))
      assert(rendered.contains("executable_policy_rows: 0"))
      assert(rendered.contains("real_scored_or_reranked_rows: 0"))
      assert(rendered.contains("schema-only"))
      assert(rendered.contains("NOT scoring"))
      assert(rendered.contains("NOT fusion"))
      assert(rendered.contains("NOT reranking"))
      assert(rendered.contains("NOT backend execution"))
      assert(rendered.contains("NOT production routing"))
    }

    "render a byte-for-byte deterministic checked-in artifact" in {
      val artifact = M12BeautyQSearchFusionRerankingPolicyCatalog.MarkdownArtifact
      val expected = readResource(policyCatalogArtifactPath)

      assert(artifact.filename == "m12-beautyq-fusion-reranking-policy-catalog.md")
      assert(artifact.contentType == "text/markdown; charset=utf-8")
      assert(
        artifact.contents == M12BeautyQSearchFusionRerankingPolicyCatalogRenderer
          .renderMarkdown(M12BeautyQSearchFusionRerankingPolicyCatalog.DefaultSummary),
      )
      assert(artifact.contents == expected)
      assert(
        M12BeautyQSearchFusionRerankingPolicyCatalogRenderer
          .renderMarkdown(M12BeautyQSearchFusionRerankingPolicyCatalog.build()) == expected,
      )
    }
  }

  private def metricValue(
    metrics: List[M12BeautyQSearchFusionRerankingPolicyCatalogMetric],
    name: String,
  ): String =
    metrics.find(_.name == name) match {
      case Some(metric) => metric.value
      case None         => fail(s"missing readiness metric $name")
    }

  private def readResource(path: String): String =
    Option(getClass.getResourceAsStream(path)) match {
      case Some(value) =>
        Using.resource(Source.fromInputStream(value, StandardCharsets.UTF_8.name()))(_.mkString)
      case None =>
        fail(s"missing checked-in resource $path")
    }
}

final class M12BeautyQSearchFusionRerankingExperimentPlanSpec extends AnyWordSpec {

  private val experimentPlanArtifactPath: String =
    "/leaderboard/search/eval/m12-beautyq-fusion-reranking-experiment-plan.md"

  // Marketing/readiness tokens that must never appear as positive claims in the rendered artifact.
  private val forbiddenRenderedTokens: List[String] = List(
    "production_ready",
    "qdrant_ready",
    "hybrid_ready",
    "is production ready",
    "production-ready",
    "quality is green",
    "serving approval granted",
    "route activated",
    "backend execution ready",
    "execution ready",
    "hybrid serving enabled",
    "fallback enabled",
    "score fusion enabled",
    "reranking enabled",
    "fusion enabled",
    "scoring enabled",
  )

  // Tokens that would betray a fabricated candidate/result/fusion/reranking payload. None may appear.
  private val fabricationTokens: List[String] = List(
    "took_ms",
    "\"hits\"",
    "\"_id\"",
    "\"results\"",
    "\"payload\"",
    "doc_id",
    "cosine_score",
    "embedding",
    "fused_score:",
    "reranked_position:",
    "quality_label:",
    "fusion_output:",
    "reranking_output:",
  )

  private def planGroupCount(
    counts: List[(M12BeautyQSearchFusionRerankingExperimentPlanGroup, Int)],
    group: M12BeautyQSearchFusionRerankingExperimentPlanGroup,
  ): Int =
    counts.collectFirst { case (`group`, count) => count }
      .getOrElse(fail(s"Missing M12 plan group count: ${group.render}"))

  "M12BeautyQSearchFusionRerankingExperimentPlan inputs" should {

    "consume the accepted M12A input scaffold and M12 policy catalog and still total 89 rows" in {
      val summary = M12BeautyQSearchFusionRerankingExperimentPlan.DefaultSummary
      val scaffold = M12BeautyQSearchFusionRerankingInputScaffold.DefaultSummary
      val catalog = M12BeautyQSearchFusionRerankingPolicyCatalog.DefaultSummary

      assert(scaffold.fusionRerankingInputRows == 89)
      assert(catalog.consumedInputRowCount == 89)
      assert(summary.consumedInputRowCount == 89)
      assert(summary.consumedM12InputScaffoldVerdict ==
        "m12_fusion_reranking_input_scaffold_ready_schema_only")
      assert(summary.consumedM12PolicyCatalogVerdict ==
        "m12_fusion_reranking_policy_catalog_ready_schema_only")
      assert(metricValue(summary.metrics, "consumed_m12a_input_rows") == "89")
      assert(metricValue(summary.metrics, "consumed_input_row_count") == "89")
    }

    "preserve the M12A counts: backend candidate 88, executable 0, real result 0, pending 160, combined pair 72, negative control 1" in {
      val summary = M12BeautyQSearchFusionRerankingExperimentPlan.DefaultSummary

      assert(summary.consumedM12ABackendCandidateRows == 88)
      assert(summary.consumedM12AExecutableRows == 0)
      assert(summary.consumedM12ARealCandidateResultRows == 0)
      assert(summary.consumedM12APendingNotExecutedResultLegRows == 160)
      assert(summary.consumedM12ACombinedComparisonPairPlaceholders == 72)
      assert(summary.consumedM12AAcceptedNegativeControlExclusions == 1)
      assert(metricValue(summary.metrics, "consumed_m12a_backend_candidate_rows") == "88")
      assert(metricValue(summary.metrics, "consumed_m12a_executable_rows") == "0")
      assert(metricValue(summary.metrics, "consumed_m12a_real_candidate_result_rows") == "0")
      assert(metricValue(summary.metrics, "consumed_m12a_pending_not_executed_result_leg_rows") == "160")
      assert(metricValue(summary.metrics, "consumed_m12a_combined_comparison_pair_placeholders") == "72")
      assert(metricValue(summary.metrics, "consumed_m12a_accepted_negative_control_exclusions") == "1")
    }
  }

  "M12BeautyQSearchFusionRerankingExperimentPlan rows" should {

    "produce one plan row per consumed M12A input envelope" in {
      val rows = M12BeautyQSearchFusionRerankingExperimentPlan.PlanRows

      assert(rows.size == 89)
      assert(rows.map(_.queryId).distinct.size == 89)
      assert(
        rows.map(_.queryId) ==
          M12BeautyQSearchFusionRerankingInputScaffold.InputEnvelopes.map(_.queryId),
      )
      assert(rows.forall(!_.isExecutable))
      assert(rows.forall(_.allPoliciesNonExecutable))
      assert(rows.forall(_.allLegsPendingNotExecuted))
    }
  }

  "M12BeautyQSearchFusionRerankingExperimentPlan group counts" should {

    "derive es_baseline=15, qdrant_baseline=1, combined=72, accepted_negative_control=1, manual_or_no_op=0" in {
      val summary = M12BeautyQSearchFusionRerankingExperimentPlan.DefaultSummary

      assert(summary.esBaselineRows == 15)
      assert(summary.qdrantBaselineRows == 1)
      assert(summary.combinedExperimentRows == 72)
      assert(summary.acceptedNegativeControlExclusionRows == 1)
      assert(summary.manualOrNoOpRows == 0)
      assert(planGroupCount(summary.planGroupCounts, M12BeautyQSearchFusionRerankingExperimentPlanGroup.EsBaselinePlan) == 15)
      assert(planGroupCount(summary.planGroupCounts, M12BeautyQSearchFusionRerankingExperimentPlanGroup.QdrantBaselinePlan) == 1)
      assert(planGroupCount(summary.planGroupCounts, M12BeautyQSearchFusionRerankingExperimentPlanGroup.CombinedExperimentPlan) == 72)
      assert(
        planGroupCount(summary.planGroupCounts, M12BeautyQSearchFusionRerankingExperimentPlanGroup.AcceptedNegativeControlExclusionPlan) == 1,
      )
      assert(planGroupCount(summary.planGroupCounts, M12BeautyQSearchFusionRerankingExperimentPlanGroup.ManualOrNoOpPlan) == 0)
      assert(
        summary.esBaselineRows + summary.qdrantBaselineRows + summary.combinedExperimentRows +
          summary.acceptedNegativeControlExclusionRows + summary.manualOrNoOpRows == 89,
      )
      assert(metricValue(summary.metrics, "es_baseline_rows") == "15")
      assert(metricValue(summary.metrics, "qdrant_baseline_rows") == "1")
      assert(metricValue(summary.metrics, "combined_experiment_rows") == "72")
      assert(metricValue(summary.metrics, "accepted_negative_control_exclusion_rows") == "1")
      assert(metricValue(summary.metrics, "manual_or_no_op_rows") == "0")
    }

    "report executable policy rows = 0 and real scored/reranked rows = 0" in {
      val summary = M12BeautyQSearchFusionRerankingExperimentPlan.DefaultSummary

      assert(summary.executablePolicyRows == 0)
      assert(summary.realScoredOrRerankedRows == 0)
      assert(metricValue(summary.metrics, "executable_policy_rows") == "0")
      assert(metricValue(summary.metrics, "real_scored_or_reranked_rows") == "0")
    }
  }

  "M12BeautyQSearchFusionRerankingExperimentPlan row shapes" should {

    "give each ES baseline row exactly the ES baseline passthrough policy" in {
      val rows = M12BeautyQSearchFusionRerankingExperimentPlan.PlanRows.filter(_.isBackendBaseline)

      assert(rows.nonEmpty)
      rows.foreach { row =>
        row.assignedPolicies match {
          case policy :: Nil =>
            assert(policy == M12BeautyQSearchFusionRerankingPolicyOption.EsBaselinePassthrough ||
              policy == M12BeautyQSearchFusionRerankingPolicyOption.QdrantBaselinePassthrough)
            assert(policy.isBackendBaseline)
          case other =>
            fail(s"Expected exactly one backend baseline policy for ${row.queryId}, got $other")
        }
        assert(!row.isCombinedExperiment)
        assert(!row.isExclusion)
        assert(row.planGroup.isBackendBaseline)
      }
    }

    "give each combined experiment row exactly the three combined placeholder policies" in {
      val rows = M12BeautyQSearchFusionRerankingExperimentPlan.PlanRows.filter(_.isCombinedExperiment)

      assert(rows.nonEmpty)
      rows.foreach { row =>
        assert(row.assignedPolicies.toSet == Set(
          M12BeautyQSearchFusionRerankingPolicyOption.CombinedUnionPlaceholder,
          M12BeautyQSearchFusionRerankingPolicyOption.CombinedIntersectionPlaceholder,
          M12BeautyQSearchFusionRerankingPolicyOption.TieBreakerPlaceholder,
        ))
        assert(!row.planGroup.isBackendBaseline)
        assert(row.planGroup.isCombinedExperiment)
        assert(!row.planGroup.isExclusion)
        assert(!row.isExecutable)
        assert(row.allPoliciesNonExecutable)
      }
    }

    "give accepted negative-control and manual/no-op rows no backend candidate policy" in {
      val summary = M12BeautyQSearchFusionRerankingExperimentPlan.DefaultSummary

      // Manual/no-op has 0 rows by current data; the catalog-level policy assignment for it is `Nil`.
      assert(
        M12BeautyQSearchFusionRerankingPolicyOption.optionsForInputGroup(
          M12BeautyQSearchFusionRerankingInputGroup.ManualOrNoOpExclusionInput,
        ).isEmpty,
      )

      val negativeControl = M12BeautyQSearchFusionRerankingExperimentPlan.PlanRows.filter(_.planGroup ==
        M12BeautyQSearchFusionRerankingExperimentPlanGroup.AcceptedNegativeControlExclusionPlan)

      assert(negativeControl.size == planGroupCount(
        summary.planGroupCounts,
        M12BeautyQSearchFusionRerankingExperimentPlanGroup.AcceptedNegativeControlExclusionPlan,
      ))
      negativeControl.foreach { row =>
        assert(!row.hasBackendCandidatePolicy)
        assert(row.assignedPolicies == List(
          M12BeautyQSearchFusionRerankingPolicyOption.AcceptedNegativeControlExclusionPolicy,
        ))
        assert(row.isExclusion)
        assert(row.planGroup.isExclusion)
      }

      assert(metricValue(summary.metrics, "accepted_negative_control_exclusion_has_no_backend_candidate_policy") == "true")
      assert(metricValue(summary.metrics, "manual_and_no_op_exclusions_have_no_backend_candidate_policy") == "true")
    }

    "map q_noise_004 to a combined placeholder experiment-plan row" in {
      val row = M12BeautyQSearchFusionRerankingExperimentPlan
        .planRowFor("q_noise_004")
        .getOrElse(fail("missing q_noise_004"))

      assert(row.category == M10BeautyQSearchQueryCategory.MixedIntent)
      assert(row.strategyIntent == M10BeautyQSearchOfflineRetrievalStrategyIntent.CombinedEsQdrantComparison)
      assert(row.inputGroup == M12BeautyQSearchFusionRerankingInputGroup.CombinedComparisonPlaceholderInput)
      assert(row.planGroup == M12BeautyQSearchFusionRerankingExperimentPlanGroup.CombinedExperimentPlan)
      assert(row.isCombinedExperiment)
      assert(row.assignedPolicies.toSet == Set(
        M12BeautyQSearchFusionRerankingPolicyOption.CombinedUnionPlaceholder,
        M12BeautyQSearchFusionRerankingPolicyOption.CombinedIntersectionPlaceholder,
        M12BeautyQSearchFusionRerankingPolicyOption.TieBreakerPlaceholder,
      ))
      assert(!row.isExecutable)
      assert(row.allLegsPendingNotExecuted)
      assert(row.pendingResultLegs.map(_.render) ==
        List("es:pending_not_executed", "qdrant:pending_not_executed"))
    }

    "map q_noise_005 to the accepted negative-control exclusion policy row" in {
      val row = M12BeautyQSearchFusionRerankingExperimentPlan
        .planRowFor("q_noise_005")
        .getOrElse(fail("missing q_noise_005"))

      assert(row.category == M10BeautyQSearchQueryCategory.NoisyAmbiguousNonBeautyIntent)
      assert(row.strategyIntent == M10BeautyQSearchOfflineRetrievalStrategyIntent.AcceptedNegativeControlExcluded)
      assert(row.inputGroup == M12BeautyQSearchFusionRerankingInputGroup.AcceptedNegativeControlExclusionInput)
      assert(row.planGroup ==
        M12BeautyQSearchFusionRerankingExperimentPlanGroup.AcceptedNegativeControlExclusionPlan)
      assert(!row.isCombinedExperiment)
      assert(!row.isBackendBaseline)
      assert(row.isExclusion)
      assert(row.assignedPolicies == List(
        M12BeautyQSearchFusionRerankingPolicyOption.AcceptedNegativeControlExclusionPolicy,
      ))
      assert(!row.hasBackendCandidatePolicy)
      assert(row.pendingResultLegs.isEmpty)
    }
  }

  "M12BeautyQSearchFusionRerankingExperimentPlan verdict and boundary" should {

    "carry the schema-only experiment-plan readiness verdict, not scoring or execution readiness" in {
      val summary = M12BeautyQSearchFusionRerankingExperimentPlan.DefaultSummary

      assert(summary.verdict == "m12_fusion_reranking_experiment_plan_ready_schema_only")
      assert(summary.m12FusionRerankingExperimentPlanReady)
      assert(metricValue(summary.metrics, "m12_fusion_reranking_experiment_plan_ready") == "true")
      assert(metricValue(summary.metrics, "m12_plan_is_schema_only_not_scoring") == "true")
      assert(metricValue(summary.metrics, "m12_plan_is_schema_only_not_fusion") == "true")
      assert(metricValue(summary.metrics, "m12_plan_is_schema_only_not_reranking") == "true")
      assert(metricValue(summary.metrics, "m12_plan_is_schema_only_not_candidate_retrieval") == "true")
      assert(metricValue(summary.metrics, "m12_plan_is_schema_only_not_backend_execution") == "true")
      assert(metricValue(summary.metrics, "m12_plan_is_offline_not_production_routing") == "true")
      assert(metricValue(summary.metrics, "m12_combined_experiment_plan_is_offline_not_hybrid_serving") == "true")
      assert(metricValue(summary.metrics, "m12_preserves_consumed_m12a_counts") == "true")
      assert(!summary.boundary.qualityGreenClaimed)
      assert(!summary.boundary.productionReadinessClaimed)
      assert(!summary.boundary.routeActivationClaimed)
      assert(!summary.boundary.servingApprovalClaimed)
    }

    "require and implement no real backend/client/route/plugin/DI/HTTP path" in {
      val b = M12BeautyQSearchFusionRerankingExperimentPlan.DefaultSummary.boundary
      val metrics = M12BeautyQSearchFusionRerankingExperimentPlan.DefaultSummary.metrics

      assert(!b.productionBeautySearchCalled)
      assert(!b.esClientCreated)
      assert(!b.qdrantClientCreated)
      assert(!b.esExecuted)
      assert(!b.qdrantExecuted)
      assert(!b.routePluginDiHttpInvolved)
      assert(!b.realBackendCallRequired)
      assert(!b.realBackendCallImplemented)
      assert(metricValue(metrics, "production_beauty_search_called") == "false")
      assert(metricValue(metrics, "es_client_created") == "false")
      assert(metricValue(metrics, "qdrant_client_created") == "false")
      assert(metricValue(metrics, "es_executed") == "false")
      assert(metricValue(metrics, "qdrant_executed") == "false")
      assert(metricValue(metrics, "route_plugin_di_http_involved") == "false")
      assert(metricValue(metrics, "real_backend_call_required") == "false")
      assert(metricValue(metrics, "real_backend_call_implemented") == "false")
    }

    "keep all production and Qdrant activation boundaries false/not approved" in {
      val b = M12BeautyQSearchFusionRerankingExperimentPlan.DefaultSummary.boundary
      val metrics = M12BeautyQSearchFusionRerankingExperimentPlan.DefaultSummary.metrics

      assert(b.defaultBeautySearchEsBacked)
      assert(b.qdrantOptInDisabledByDefault)
      assert(!b.qdrantProductionActivationApproved)
      assert(!b.productionRouteActivated)
      assert(!b.defaultRouteSwitched)
      assert(metricValue(metrics, "qdrant_production_activation_approved") == "false")
      assert(metricValue(metrics, "production_route_activated") == "false")
      assert(metricValue(metrics, "default_route_switched") == "false")
      assert(metricValue(metrics, "default_beauty_search_es_backed") == "true")
      assert(metricValue(metrics, "qdrant_opt_in_disabled_by_default") == "true")
    }

    "keep forbidden production/hybrid/fallback/fusion/reranking/telemetry boundaries false" in {
      val b = M12BeautyQSearchFusionRerankingExperimentPlan.DefaultSummary.boundary
      val metrics = M12BeautyQSearchFusionRerankingExperimentPlan.DefaultSummary.metrics

      assert(!b.hybridServingImplied)
      assert(!b.fallbackImplied)
      assert(!b.scoreFusionImplied)
      assert(!b.rerankingImplied)
      assert(!b.productionTelemetryImplied)
      assert(metricValue(metrics, "fallback_implied") == "false")
      assert(metricValue(metrics, "score_fusion_implied") == "false")
      assert(metricValue(metrics, "reranking_implied") == "false")
      assert(metricValue(metrics, "production_telemetry_implied") == "false")
    }
  }

  "M12BeautyQSearchFusionRerankingExperimentPlan fabrications" should {

    "fabricate no candidate ids, provider ids, scores, ranks, backend responses, fused scores, reranked positions, quality labels, fusion outputs, or reranking outputs" in {
      val metrics = M12BeautyQSearchFusionRerankingExperimentPlan.DefaultSummary.metrics
      val rendered = M12BeautyQSearchFusionRerankingExperimentPlan.MarkdownArtifact.contents.toLowerCase

      fabricationTokens.foreach { token =>
        assert(!rendered.contains(token.toLowerCase), s"fabrication token present: $token")
      }
      assert(metricValue(metrics, "no_real_candidate_ids_provider_ids_scores_ranks_fabricated") == "true")
      assert(metricValue(metrics, "no_real_backend_responses_fabricated") == "true")
      assert(metricValue(metrics, "no_fused_scores_reranked_positions_quality_labels_fabricated") == "true")
      assert(metricValue(metrics, "no_fusion_outputs_reranking_outputs_fabricated") == "true")
    }
  }

  "M12BeautyQSearchFusionRerankingExperimentPlan artifact" should {

    "carry no marketing readiness tokens as positive claims" in {
      val rendered = M12BeautyQSearchFusionRerankingExperimentPlan.MarkdownArtifact.contents.toLowerCase

      forbiddenRenderedTokens.foreach { token =>
        assert(!rendered.contains(token), s"forbidden token present: $token")
      }
    }

    "include every required section and schema-only disclaimer" in {
      val rendered = M12BeautyQSearchFusionRerankingExperimentPlan.MarkdownArtifact.contents

      assert(rendered.contains("## Policy catalog (assigned policies)"))
      assert(rendered.contains("## Experiment plan group counts"))
      assert(rendered.contains("## q_noise_004 and q_noise_005 mappings"))
      assert(rendered.contains("## Metrics"))
      assert(rendered.contains("## Boundary summary"))
      assert(rendered.contains("consumed_m12a_input_scaffold_verdict: m12_fusion_reranking_input_scaffold_ready_schema_only"))
      assert(rendered.contains("consumed_m12_policy_catalog_verdict: m12_fusion_reranking_policy_catalog_ready_schema_only"))
      assert(rendered.contains("consumed_m11b_result_schema_verdict: m11_candidate_generation_result_schema_ready"))
      assert(rendered.contains("consumed_m11c_boundary_failure_matrix_verdict: m11_candidate_generation_boundary_failure_matrix_ready"))
      assert(rendered.contains("es_baseline_rows: 15"))
      assert(rendered.contains("qdrant_baseline_rows: 1"))
      assert(rendered.contains("combined_experiment_rows: 72"))
      assert(rendered.contains("accepted_negative_control_exclusion_rows: 1"))
      assert(rendered.contains("manual_or_no_op_rows: 0"))
      assert(rendered.contains("executable_policy_rows: 0"))
      assert(rendered.contains("real_scored_or_reranked_rows: 0"))
      assert(rendered.contains("schema-only"))
      assert(rendered.contains("NOT scoring"))
      assert(rendered.contains("NOT fusion"))
      assert(rendered.contains("NOT reranking"))
      assert(rendered.contains("NOT backend execution"))
      assert(rendered.contains("NOT production routing"))
    }

    "map q_noise_004 and q_noise_005 explicitly in the noise-probe section" in {
      val rendered = M12BeautyQSearchFusionRerankingExperimentPlan.MarkdownArtifact.contents
      val probe = section(rendered, "## q_noise_004 and q_noise_005 mappings")

      assert(probe.contains("| q_noise_004 | combined_experiment_plan | combined_union_placeholder, combined_intersection_placeholder, tie_breaker_placeholder | false | false |"))
      assert(probe.contains("| q_noise_005 | accepted_negative_control_exclusion_plan | accepted_negative_control_exclusion_policy | false | false |"))
    }

    "render a byte-for-byte deterministic checked-in artifact" in {
      val artifact = M12BeautyQSearchFusionRerankingExperimentPlan.MarkdownArtifact
      val expected = readResource(experimentPlanArtifactPath)

      assert(artifact.filename == "m12-beautyq-fusion-reranking-experiment-plan.md")
      assert(artifact.contentType == "text/markdown; charset=utf-8")
      assert(
        artifact.contents == M12BeautyQSearchFusionRerankingExperimentPlanRenderer
          .renderMarkdown(M12BeautyQSearchFusionRerankingExperimentPlan.DefaultSummary),
      )
      assert(artifact.contents == expected)
      assert(
        M12BeautyQSearchFusionRerankingExperimentPlanRenderer
          .renderMarkdown(M12BeautyQSearchFusionRerankingExperimentPlan.build()) == expected,
      )
    }
  }

  private def metricValue(
    metrics: List[M12BeautyQSearchFusionRerankingExperimentPlanMetric],
    name: String,
  ): String =
    metrics.find(_.name == name) match {
      case Some(metric) => metric.value
      case None         => fail(s"missing readiness metric $name")
    }

  private def section(contents: String, heading: String): String = {
    val lines = contents.linesIterator.toList
    val start = lines.indexWhere(_ == heading)
    assert(start >= 0, s"missing section $heading")
    val rest = lines.drop(start + 1)
    val end = rest.indexWhere(_.startsWith("## "))
    (if (end < 0) rest else rest.take(end)).mkString("\n")
  }

  private def readResource(path: String): String =
    Option(getClass.getResourceAsStream(path)) match {
      case Some(value) =>
        Using.resource(Source.fromInputStream(value, StandardCharsets.UTF_8.name()))(_.mkString)
      case None =>
        fail(s"missing checked-in resource $path")
    }
}
