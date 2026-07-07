package leaderboard.search

import cats.effect.Async
import distage.{Injector, ModuleDef}
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, HttpApi}
import leaderboard.plugins.BeautySearchRouteModules
import leaderboard.search.eval.M19IBeautyQComponentCombinationPolicyScaffold.ComponentCombinationPolicy
import leaderboard.search.eval.M20ControlledHybridServingSkeleton.{HybridServingControlState, M20HybridServingControl}
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz.*
import zio.{IO, Task}

final class M20ControlledHybridServingSkeletonSpec extends AnyWordSpec {

  // Offline/eval-only policy evidence carrying the M19I honesty flags. M20A consumes this strictly
  // as policy evidence; it never treats it as an execution approval.
  private val evidenceOnlyPolicy: ComponentCombinationPolicy =
    ComponentCombinationPolicy(
      rows = Nil,
      offlineEvalOnly = true,
      notServingPolicy = true,
      doesNotApproveHybrid = true,
      qdrantDoesNotOwnFacets = true,
      qdrantDoesNotOwnInferredFilters = true,
    )

  "M20A controlled hybrid serving skeleton (pure model)" should {

    "be disabled by default" in {
      val control = M20HybridServingControl.disabledByDefault(evidenceOnlyPolicy)
      assert(control.disabled)
      assert(control.state == HybridServingControlState.DisabledByDefault)
      assert(!control.state.explicitlySelected)
    }

    "approve no serving and no Qdrant production activation" in {
      val control = M20HybridServingControl.disabledByDefault(evidenceOnlyPolicy)
      assert(!control.servingApproved)
      assert(!control.qdrantProductionActivationApproved)
    }

    "enable no fallback, fusion, reranking, automatic Qdrant supplement, or route switch" in {
      val control = M20HybridServingControl.disabledByDefault(evidenceOnlyPolicy)
      assert(!control.fallbackEnabled)
      assert(!control.scoreFusionEnabled)
      assert(!control.rerankingEnabled)
      assert(!control.automaticQdrantSupplementEnabled)
      assert(!control.routeSwitchEnabled)
      assert(control.defaultBeautySearchRouteUnchanged)
      assert(control.executesNoHybridServing)
    }

    "consume the M19I policy only as policy evidence, not execution approval" in {
      val control = M20HybridServingControl.disabledByDefault(evidenceOnlyPolicy)
      assert(control.consumesPolicyAsEvidenceOnly)
      assert(control.policySource.offlineEvalOnly)
      assert(control.policySource.notServingPolicy)
      assert(control.policySource.doesNotApproveHybrid)

      // A policy lacking the offline/eval honesty flags cannot pass the evidence gate, proving the
      // flags are checked rather than assumed.
      val nonEvidencePolicy = evidenceOnlyPolicy.copy(offlineEvalOnly = false)
      assert(!M20HybridServingControl.disabledByDefault(nonEvidencePolicy).consumesPolicyAsEvidenceOnly)
    }

    "keep every safety invariant even when explicitly selected as a skeleton handle" in {
      val control = M20HybridServingControl.explicitlySelectedSkeleton(evidenceOnlyPolicy)
      assert(control.state.explicitlySelected)
      assert(!control.disabled)
      // Explicit selection is still a non-serving skeleton: nothing dangerous turns on.
      assert(!control.servingApproved)
      assert(!control.qdrantProductionActivationApproved)
      assert(control.executesNoHybridServing)
      assert(control.consumesPolicyAsEvidenceOnly)
    }
  }

  "M20A default route graph (module safety)" should {

    "keep the default /beauty-search graph ES-backed with the skeleton absent" in {
      val locator = produceDefaultGraph()
      val probe   = locator.get[DefaultGraphProbe]

      // Default route still contributes exactly one ES-backed BeautySearchApi.
      assert(probe.allHttpApis.collect { case api: BeautySearchApi[IO] => api }.size == 1)
      assert(probe.allHttpApis.size == 1)

      // The M20A skeleton is not bound by any default module.
      assert(locator.find[M20HybridServingControl].isEmpty)
    }

    "include the skeleton only when a caller explicitly selects it, without changing the default route" in {
      val locator = produceGraphWithExplicitSkeleton()
      val probe   = locator.get[ExplicitSkeletonGraphProbe]

      // The default route is unchanged: still exactly one ES-backed BeautySearchApi.
      assert(probe.allHttpApis.collect { case api: BeautySearchApi[IO] => api }.size == 1)
      assert(probe.allHttpApis.size == 1)

      // The explicitly selected skeleton is present and remains a disabled, non-serving handle.
      val control = locator.find[M20HybridServingControl].getOrElse(
        fail("explicitly selected M20 skeleton must be present in the graph")
      )
      assert(control.state.explicitlySelected)
      assert(!control.servingApproved)
      assert(control.executesNoHybridServing)
    }
  }

  private final case class DefaultGraphProbe(allHttpApis: Set[HttpApi[IO]])

  private final case class ExplicitSkeletonGraphProbe(
    allHttpApis: Set[HttpApi[IO]],
    control: M20HybridServingControl,
  )

  private def produceDefaultGraph() = {
    val module = new ModuleDef {
      include(BeautySearchRouteModules.seedCatalogInMemory[IO])
      make[Async[Task]].fromValue(Async[Task])
      make[DefaultGraphProbe].from {
        (beautySearchApi: BeautySearchApi[IO], allHttpApis: Set[HttpApi[IO]]) =>
          val _ = beautySearchApi
          DefaultGraphProbe(allHttpApis)
      }
    }

    Injector().produce(
      bindings = module,
      roots = Roots.target[DefaultGraphProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()
  }

  private def produceGraphWithExplicitSkeleton() = {
    val module = new ModuleDef {
      include(BeautySearchRouteModules.seedCatalogInMemory[IO])
      make[Async[Task]].fromValue(Async[Task])
      make[M20HybridServingControl].fromValue(
        M20HybridServingControl.explicitlySelectedSkeleton(evidenceOnlyPolicy)
      )
      make[ExplicitSkeletonGraphProbe].from {
        (
          beautySearchApi: BeautySearchApi[IO],
          allHttpApis: Set[HttpApi[IO]],
          control: M20HybridServingControl,
        ) =>
          val _ = beautySearchApi
          ExplicitSkeletonGraphProbe(allHttpApis, control)
      }
    }

    Injector().produce(
      bindings = module,
      roots = Roots.target[ExplicitSkeletonGraphProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()
  }
}
