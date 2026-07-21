package leaderboard.plugins

import distage.ModuleDef
import leaderboard.api.{BeautySearchGen2Api, BeautySearchGen2Service, HttpApi}
import leaderboard.http.tapir.BeautySearchGen2TapirEndpoints
import leaderboard.search.gen2.{BeautyQSearchGen2HttpService, BeautyQSearchGen2Runtime}
import zio.IO

/** Explicit opt-in route composition. It is intentionally not included by
  * [[LeaderboardPlugin]]: V1 route ownership remains unchanged until the
  * single final cutover. */
object BeautySearchGen2PluginModules {
  def api: ModuleDef = new ModuleDef {
    make[BeautySearchGen2TapirEndpoints].fromValue(BeautySearchGen2TapirEndpoints)
    make[BeautySearchGen2Service[IO]].from((runtime: BeautyQSearchGen2Runtime) => new BeautyQSearchGen2HttpService(runtime))
    make[BeautySearchGen2Api[IO]]
    many[HttpApi[IO]].ref[BeautySearchGen2Api[IO]]
  }
}
