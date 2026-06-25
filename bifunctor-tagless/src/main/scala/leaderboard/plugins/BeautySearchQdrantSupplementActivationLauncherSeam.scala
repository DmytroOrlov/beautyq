package leaderboard.plugins

import distage.ModuleDef

// Narrow launcher/role activation seam for `BeautySearchQdrantSupplementActivation`. Reads the
// explicit operator value from the environment and selects the corresponding module via the
// existing pure `BeautySearchQdrantSupplementActivationConfig` parser.
//
// Safe default: an absent/unset environment value selects `EsOnlyRollback`'s module
// (`BeautySearchRouteModules.apiElasticsearch`) -- the exact module `LeaderboardPlugin` already
// included before this seam existed. An unrecognized value fails closed by throwing at module
// composition time, so launcher startup halts loudly instead of silently selecting ready or falling
// back to the Qdrant supplement.
//
// This seam does not read HOCON/CLI, does not perform Qdrant readiness HTTP calls, does not change
// the public route/API shape, and does not wire any Qdrant client/lifecycle/startup-indexing
// infrastructure: selecting `qdrant-supplement-not-ready` / `qdrant-supplement-ready` still requires
// the caller to supply the lexical/semantic backend and document lookup bindings, exactly as for
// `BeautySearchQdrantSupplementActivation.moduleFor(...)` today (QP4/QP6/QP7/QP8).
object BeautySearchQdrantSupplementActivationLauncherSeam {
  val OperatorEnvVarName: String = "BEAUTYQ_QDRANT_SUPPLEMENT_ACTIVATION"

  // Pure: given an already-read operator value, select the module or fail closed.
  def selectedModuleOrThrow(operatorValue: Option[String]): ModuleDef =
    BeautySearchQdrantSupplementActivationConfig.moduleForOperatorValue(operatorValue) match {
      case Right(module) => module
      case Left(failure)  => throw new IllegalArgumentException(failure.message)
    }

  // Launcher-facing: reads the operator value from the environment.
  def selectedModuleFromEnvOrThrow(): ModuleDef =
    selectedModuleOrThrow(sys.env.get(OperatorEnvVarName))
}
