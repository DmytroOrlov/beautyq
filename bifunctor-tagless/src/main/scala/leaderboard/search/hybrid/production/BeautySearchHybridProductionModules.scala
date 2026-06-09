package leaderboard.search.hybrid.production

import distage.ModuleDef
import izumi.reflect.TagKK
import izumi.functional.bio.Error2
import leaderboard.search.hybrid.control.{
  BeautySearchHybridDecisionEvaluator,
  BeautySearchHybridDiagnosticsSink,
  BeautySearchHybridReadiness,
  BeautySearchHybridServingPolicy,
}

object BeautySearchHybridProductionModules {

  def disabled[F[+_, +_]: TagKK]: ModuleDef =
    new ModuleDef {
      make[BeautySearchHybridProductionActivation].fromValue(
        BeautySearchHybridProductionActivation.Disabled
      )

      make[BeautySearchHybridProductionHandle[F]].fromValue(
        BeautySearchHybridProductionHandle.disabled[F]
      )
    }

  def enabledControlPlaneOnly[F[+_, +_]: TagKK: Error2](
    policy: BeautySearchHybridServingPolicy,
  ): ModuleDef =
    new ModuleDef {
      make[BeautySearchHybridProductionActivation].fromValue(
        BeautySearchHybridProductionActivation.Enabled(policy)
      )

      make[BeautySearchHybridDecisionEvaluator[F]].from {
        (
          readiness: BeautySearchHybridReadiness[F],
          diagnosticsSink: BeautySearchHybridDiagnosticsSink[F],
        ) =>
          new BeautySearchHybridDecisionEvaluator[F](
            policy = policy,
            readiness = readiness,
            diagnosticsSink = diagnosticsSink,
          )
      }

      make[BeautySearchHybridProductionHandle[F]].from {
        (evaluator: BeautySearchHybridDecisionEvaluator[F]) =>
          BeautySearchHybridProductionHandle.enabled[F](evaluator)
      }
    }
}
