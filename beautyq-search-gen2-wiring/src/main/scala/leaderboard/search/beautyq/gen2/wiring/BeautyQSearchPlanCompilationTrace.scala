package leaderboard.search.beautyq.gen2.wiring

import leaderboard.search.beautyq.gen2.contract.*
import leaderboard.search.gen2.contract.*

/** Deterministic, human-readable diagnostic view of one plan compilation: the request that was decoded,
  * the intent that was parsed, the policy that was applied, the mode/labels/rules the compilation
  * produced, and the resulting validated plan. Every policy line is derived directly from
  * [[BeautyQSearchPlanPolicy]]'s own values rather than a second, hand-duplicated rendering of those
  * constants. This trace is not [[leaderboard.search.gen2.core.plan.PlanIdentity]], canonical encoding,
  * cursor input, backend JSON, or semantic query text; it never renders opaque cursor contents (see
  * [[SearchPlanTrace.render]]).
  */
object BeautyQSearchPlanCompilationTrace {

  def render(
    request: ValidatedBeautySearchRequestGen2,
    intent: ParsedBeautyIntentGen2,
    result: CompiledBeautyQSearchPlan,
  ): String =
    Vector(
      "=== request ===",
      BeautySearchRequestTrace.render(request),
      "",
      "=== intent ===",
      BeautyIntentTrace.render(intent),
      "",
      "=== policy ===",
      renderPolicy,
      "",
      "=== compilation ===",
      renderCompilation(result),
      "",
      "=== plan ===",
      SearchPlanTrace.render(result.plan),
    ).mkString("\n")

  private def renderPolicy: String =
    Vector(
      s"precedence=${BeautyQSearchPlanPolicy.constraintPrecedence.sourceOrder.mkString("[", ",", "]")}",
      s"geo-origin=${BeautyQSearchPlanPolicy.geoOriginPolicy.sourcePath}",
      s"facets=${BeautyQSearchPlanPolicy.facetRegistry.ids.map(_.value).mkString("[", ",", "]")}",
      s"groups=${if (BeautyQSearchPlanPolicy.groups.isEmpty) "empty" else BeautyQSearchPlanPolicy.groups.map(_.id.value).mkString("[", ",", "]")}",
      s"default-browse-code=${BeautyQSearchPlanPolicy.defaultBrowseNotice.code.value}",
    ).mkString("\n")

  private def renderCompilation(result: CompiledBeautyQSearchPlan): String =
    Vector(
      s"mode=${result.mode}",
      s"semantic-labels=${result.canonicalSemanticLabels.map(label => s"${label.stableKey}:${escape(label.text)}").mkString("[", ",", "]")}",
      s"matched-rules=${result.matchedRuleIds.map(_.value).mkString("[", ",", "]")}",
    ).mkString("\n")

  private def escape(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
}
