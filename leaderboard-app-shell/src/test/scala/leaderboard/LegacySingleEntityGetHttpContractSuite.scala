package leaderboard

import distage.ModuleDef
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.scalatest.{AssertZIO, SpecZIO}
import leaderboard.api.ProfileApi
import leaderboard.model.RankedProfile
import leaderboard.repo.Profiles
import leaderboard.services.Ranks
import org.http4s.Status
import zio.IO

import java.util.UUID

class LegacySingleEntityGetHttpContractSuite extends SpecZIO with AssertZIO with HttpContractTestSupport {
  override def config = super.config.copy(
    pluginConfig    = PluginConfig.cached(packagesEnabled = Seq("leaderboard.plugins")),
    moduleOverrides = super.config.moduleOverrides ++ new ModuleDef {
      make[ProfileApiContractState].fromEffect(ProfileApiContractState.make)
      make[Profiles[IO]].from((state: ProfileApiContractState) => state.profiles)
      make[Ranks[IO]].from((state: ProfileApiContractState) => state.ranks)
    },
  )

  "Legacy single entity GET contracts" should {
    "pin 200 and null for a missing legacy profile" in {
      (profileApi: ProfileApi[IO], state: ProfileApiContractState) =>
        val userId = UUID.fromString("22222222-2222-2222-2222-222222222222")

        for {
          _        <- state.setGetRankResult(Right(None))
          response <- observe(combineApis(profileApi), get(s"/profile/$userId"))
          _        <- assertIO(response.status === Status.Ok)
          _        <- assertIO(response.body === "null")
        } yield ()
    }

    "pin 200 and exact json for an existing legacy profile" in {
      (profileApi: ProfileApi[IO], state: ProfileApiContractState) =>
        val userId        = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val rankedProfile = RankedProfile("Kai", "S C A L A", rank = 3, score = 42)

        for {
          _        <- state.setGetRankResult(Right(Some(rankedProfile)))
          response <- observe(combineApis(profileApi), get(s"/profile/$userId"))
          _        <- assertIO(response.status === Status.Ok)
          _        <- assertIO(response.body === """{"name":"Kai","description":"S C A L A","rank":3,"score":42}""")
        } yield ()
    }
  }
}
