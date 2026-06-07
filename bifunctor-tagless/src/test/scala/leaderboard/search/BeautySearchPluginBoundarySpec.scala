package leaderboard.search

import distage.Injector
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchProductionInclusionActivation, BeautySearchProductionInclusionHandle, BeautySearchProductionIncludedApis}
import leaderboard.plugins.LeaderboardPlugin
import org.scalatest.wordspec.AnyWordSpec
import zio.IO

final class BeautySearchPluginBoundarySpec extends AnyWordSpec {
  "LeaderboardPlugin.modules.api" should {
    "bind the disabled Beauty search inclusion boundary without exposing routes" in {
      val locator = Injector().produce(
        bindings = new distage.ModuleDef {
          include(LeaderboardPlugin.modules.api[IO])
          make[BeautySearchPluginBoundaryState].from {
            (
              activation: BeautySearchProductionInclusionActivation,
              handle: BeautySearchProductionInclusionHandle[IO],
              included: BeautySearchProductionIncludedApis[IO],
            ) =>
              BeautySearchPluginBoundaryState(activation, handle, included)
          }
        },
        roots = Roots.target[BeautySearchPluginBoundaryState],
        activation = Activation.empty,
        locatorPrivacy = LocatorPrivacy.PublicByDefault,
      ).unsafeGet()

      val state = locator.get[BeautySearchPluginBoundaryState]

      assert(state.activation == BeautySearchProductionInclusionActivation.Disabled)
      assert(state.handle.api.isEmpty)
      assert(state.included.apis.isEmpty)
    }
  }
}

private final case class BeautySearchPluginBoundaryState(
  activation: BeautySearchProductionInclusionActivation,
  handle: BeautySearchProductionInclusionHandle[IO],
  included: BeautySearchProductionIncludedApis[IO],
)
