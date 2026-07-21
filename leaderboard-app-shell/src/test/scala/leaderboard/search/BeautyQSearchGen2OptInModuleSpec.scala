package leaderboard.search

import distage.Injector
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.http.tapir.BeautySearchGen2TapirEndpoints
import leaderboard.plugins.BeautySearchGen2PluginModules
import org.scalatest.wordspec.AnyWordSpec

final class BeautyQSearchGen2OptInModuleSpec extends AnyWordSpec {
  "BeautySearchGen2PluginModules" should {
    "expose the independent API composition only through explicit opt-in" in {
      val module = new distage.ModuleDef {
        include(BeautySearchGen2PluginModules.api)
        make[Probe].from((endpoints: BeautySearchGen2TapirEndpoints) => Probe(endpoints))
      }
      val probe = Injector().produce(
        bindings = module,
        roots = Roots.target[Probe],
        activation = Activation.empty,
        locatorPrivacy = LocatorPrivacy.PublicByDefault,
      ).unsafeGet().get[Probe]

      assert(probe.endpoints.all.size == 1)
      assert(probe.endpoints.all.headOption.exists(_ eq probe.endpoints.searchBeautyGen2))
    }
  }

  private final case class Probe(endpoints: BeautySearchGen2TapirEndpoints)
}
