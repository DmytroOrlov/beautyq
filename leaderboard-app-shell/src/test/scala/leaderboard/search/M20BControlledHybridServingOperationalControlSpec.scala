package leaderboard.search

import cats.effect.Async
import distage.{Injector, ModuleDef}
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, HttpApi}
import leaderboard.plugins.BeautySearchRouteModules
import leaderboard.search.eval.M19IBeautyQComponentCombinationPolicyScaffold.ComponentCombinationPolicy
import leaderboard.search.eval.M20ControlledHybridServingSkeleton.M20HybridServingControl
import leaderboard.search.eval.M20BControlledHybridServingOperationalControl.{
  KillSwitchState,
  M20BOperationalControl,
  ModuleProofScope,
  ReadinessInputs,
  RollbackTarget,
}
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz.*
import zio.{IO, Task}

final class M20BControlledHybridServingOperationalControlSpec extends AnyWordSpec {

  // Offline/eval-only policy evidence carrying the M19I honesty flags. M20B consumes this strictly as
  // policy evidence (through the M20A skeleton); it never treats it as an execution approval.
  private val evidenceOnlyPolicy: ComponentCombinationPolicy =
    ComponentCombinationPolicy(
      rows = Nil,
      offlineEvalOnly = true,
      notServingPolicy = true,
      doesNotApproveHybrid = true,
      qdrantDoesNotOwnFacets = true,
      qdrantDoesNotOwnInferredFilters = true,
    )

  private val skeleton: M20HybridServingControl =
    M20HybridServingControl.disabledByDefault(evidenceOnlyPolicy)

  private def defaultControl: M20BOperationalControl =
    M20BOperationalControl.disabledByDefault(skeleton)

  "M20B operational control (pure model)" should {

    "default to disabled / not serving" in {
      val control = defaultControl
      assert(control.effectiveServingDisabled)
      assert(control.effectiveServingLabel == "disabled_not_serving")
      assert(control.killSwitch == KillSwitchState.NotEngaged)
      assert(!control.readiness.anyPositive)
      assert(control.operatorStatus.effectiveServing == "disabled_not_serving")
    }

    "keep serving approval false and Qdrant production activation false" in {
      val control = defaultControl
      assert(!control.servingApproved)
      assert(!control.qdrantProductionActivationApproved)
      assert(!control.operatorStatus.servingApproved)
      assert(!control.operatorStatus.qdrantProductionActivationApproved)
    }

    "let the kill switch force effective serving disabled even with positive readiness inputs" in {
      // All readiness inputs positive: this alone never authorizes serving.
      val readyButUnapproved =
        defaultControl.copy(readiness = ReadinessInputs.allPositive)
      assert(readyButUnapproved.readiness.allPositive)
      assert(readyButUnapproved.effectiveServingDisabled) // still disabled: serving is unapproved
      // With all readiness positive, the serving-approval-independent disable signal reduces to the
      // kill switch — proving the kill switch overrides positive readiness, not the (always-false)
      // serving approval.
      assert(!readyButUnapproved.disabledByKillSwitchOrReadiness)

      val killed = readyButUnapproved.copy(killSwitch = KillSwitchState.Engaged)
      assert(killed.readiness.allPositive)
      assert(killed.killSwitch.engaged)
      assert(killed.disabledByKillSwitchOrReadiness)
      assert(killed.effectiveServingDisabled)
      assert(killed.blockers.contains("kill_switch_engaged"))
    }

    "encode that readiness data cannot enable serving while serving is unapproved" in {
      val control = defaultControl.copy(readiness = ReadinessInputs.allPositive)
      assert(control.readinessCannotEnableServing)
      assert(!control.servingApproved)
      assert(control.effectiveServingDisabled)
    }

    "consume the M19I/M20A policy as evidence only, never as serving approval" in {
      val control = defaultControl
      assert(control.consumesPolicyAsEvidenceOnly)
      assert(!control.servingApproved)

      // Stripping an M19I honesty flag breaks the evidence gate, proving the flags are checked.
      val nonEvidenceSkeleton =
        M20HybridServingControl.disabledByDefault(evidenceOnlyPolicy.copy(offlineEvalOnly = false))
      assert(!M20BOperationalControl.disabledByDefault(nonEvidenceSkeleton).consumesPolicyAsEvidenceOnly)
    }

    "enable no fallback, fusion, reranking, automatic Qdrant supplement, or route switch" in {
      val control = defaultControl
      val status  = control.operatorStatus
      assert(!status.fallbackEnabled)
      assert(!status.scoreFusionEnabled)
      assert(!status.rerankingEnabled)
      assert(!status.automaticQdrantSupplementEnabled)
      assert(!status.routeSwitchEnabled)
      assert(status.defaultBeautySearchRouteUnchanged)
      assert(control.executesNoHybridServing)
    }

    "expose a source-confirmed rollback target visible to operators" in {
      val control = defaultControl
      assert(control.rollbackTarget == RollbackTarget.EsBackedDefaultBeautySearchRoute)
      assert(control.rollbackTarget.sourceConfirmed)
      assert(control.operatorStatus.rollbackTarget == "es_backed_default_beauty_search_route")
      assert(control.operatorStatus.rollbackTargetSourceConfirmed)

      // The existing non-default in-memory seed route is also a source-confirmed rollback target.
      val inMemoryRollback = control.copy(rollbackTarget = RollbackTarget.InMemoryNonDefaultSeedRoute)
      assert(inMemoryRollback.rollbackTarget.sourceConfirmed)
      assert(inMemoryRollback.operatorStatus.rollbackTarget == "in_memory_non_default_seed_route")
    }

    "expose operator status listing every important blocker and safety flag" in {
      val control = defaultControl
      val status  = control.operatorStatus
      // Default blockers: serving unapproved, Qdrant activation unapproved, readiness incomplete.
      assert(status.blockers == List(
        "serving_not_approved",
        "qdrant_production_activation_not_approved",
        "readiness_incomplete",
      ))
      assert(!status.killSwitchEngaged)
      assert(!status.servingApproved)
      assert(!status.qdrantProductionActivationApproved)
      assert(status.policyConsumedAsEvidenceOnly)
    }

    "report an honest in-memory-only module-proof scope, never full production graph" in {
      val control = defaultControl
      assert(control.moduleProofScope == ModuleProofScope.InMemoryOnly)
      assert(control.operatorStatus.moduleProofScope == "in_memory_only")
      assert(control.moduleProofScope != ModuleProofScope.FullProductionGraph)
    }
  }

  "M20B default route graph (module safety, in-memory only)" should {

    // This mirrors the only route proof the M20A spec source-confirms: the in-memory route graph.
    // It does NOT prove a full production ES graph, which is why moduleProofScope is InMemoryOnly.
    "keep the in-memory /beauty-search graph intact with the M20B control absent by default" in {
      val module = new ModuleDef {
        include(BeautySearchRouteModules.seedCatalogInMemory[IO])
        make[Async[Task]].fromValue(Async[Task])
        make[InMemoryGraphProbe].from {
          (beautySearchApi: BeautySearchApi[IO], allHttpApis: Set[HttpApi[IO]]) =>
            val _ = beautySearchApi
            InMemoryGraphProbe(allHttpApis)
        }
      }

      val locator = Injector().produce(
        bindings = module,
        roots = Roots.target[InMemoryGraphProbe],
        activation = Activation.empty,
        locatorPrivacy = LocatorPrivacy.PublicByDefault,
      ).unsafeGet()

      val probe = locator.get[InMemoryGraphProbe]
      assert(probe.allHttpApis.collect { case api: BeautySearchApi[IO] => api }.size == 1)
      assert(probe.allHttpApis.size == 1)

      // The M20B control is not bound by any default module.
      assert(locator.find[M20BOperationalControl].isEmpty)
    }
  }

  private final case class InMemoryGraphProbe(allHttpApis: Set[HttpApi[IO]])
}
