package leaderboard

import distage.ModuleDef
import izumi.distage.plugins.PluginConfig
import izumi.distage.testkit.scalatest.{AssertZIO, SpecZIO}
import leaderboard.api.ProfileApi
import leaderboard.model.{QueryFailure, RankedProfile, UserId, UserProfile}
import leaderboard.repo.Profiles
import leaderboard.services.Ranks
import org.http4s.Status
import zio.{IO, Ref, UIO, ZIO}

import java.util.UUID

class ProfileApiHttpContractSuite extends SpecZIO with AssertZIO with HttpContractTestSupport {
  override def config = super.config.copy(
    pluginConfig = PluginConfig.cached(packagesEnabled = Seq("leaderboard.plugins")),
    moduleOverrides = super.config.moduleOverrides ++ new ModuleDef {
      make[ProfileApiContractState].fromEffect(ProfileApiContractState.make)
      make[Profiles[IO]].from((state: ProfileApiContractState) => state.profiles)
      make[Ranks[IO]].from((state: ProfileApiContractState) => state.ranks)
    },
  )

  "ProfileApi current http4s contracts" should {
    "return 200 and exact ranked profile json for an existing profile" in {
      (profileApi: ProfileApi[IO], state: ProfileApiContractState) =>
        val userId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val rankedProfile = RankedProfile("Kai", "S C A L A", rank = 3, score = 42)

        for {
          _ <- state.setGetRankResult(Right(Some(rankedProfile)))
          response <- observe(combineApis(profileApi), get(s"/profile/$userId"))
          _ <- assertIO(response.status === Status.Ok)
          _ <- assertIO(response.body === """{"name":"Kai","description":"S C A L A","rank":3,"score":42}""")
        } yield ()
    }

    "return 200 and null body for a missing profile" in {
      (profileApi: ProfileApi[IO], state: ProfileApiContractState) =>
        val userId = UUID.fromString("22222222-2222-2222-2222-222222222222")

        for {
          _ <- state.setGetRankResult(Right(None))
          response <- observe(combineApis(profileApi), get(s"/profile/$userId"))
          _ <- assertIO(response.status === Status.Ok)
          _ <- assertIO(response.body === "null")
        } yield ()
    }

    "return 200 with empty body and persist the exact profile payload on POST" in {
      (profileApi: ProfileApi[IO], state: ProfileApiContractState) =>
        val userId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val payload = """{"name":"Nori","description":"Bio enjoyer"}"""

        for {
          _ <- state.setSetProfileResult(Right(()))
          response <- observe(combineApis(profileApi), postJson(s"/profile/$userId", payload))
          saved <- state.savedProfiles
          _ <- assertIO(response.status === Status.Ok)
          _ <- assertIO(response.body === "")
          _ <- assertIO(saved === Vector(userId -> UserProfile("Nori", "Bio enjoyer")))
        } yield ()
    }

    "return current 404 semantics for GET with malformed UUID path" in {
      (profileApi: ProfileApi[IO]) =>
        for {
          response <- observe(combineApis(profileApi), get("/profile/not-a-uuid"))
          _ <- assertIO(response.status === Status.NotFound)
          _ <- assertIO(response.body === "Not found")
        } yield ()
    }

    "return current 404 semantics for POST with malformed UUID path" in {
      (profileApi: ProfileApi[IO]) =>
        for {
          response <- observe(
            combineApis(profileApi),
            postJson("/profile/not-a-uuid", """{"name":"Kai","description":"x"}"""),
          )
          _ <- assertIO(response.status === Status.NotFound)
          _ <- assertIO(response.body === "Not found")
        } yield ()
    }

    "return current malformed-json semantics and do not hit the repo on malformed JSON body" in {
      (profileApi: ProfileApi[IO], state: ProfileApiContractState) =>
        val userId = UUID.fromString("44444444-4444-4444-4444-444444444444")

        for {
          response <- observe(combineApis(profileApi), postJson(s"/profile/$userId", """{"name":"Kai""""))
          saved <- state.savedProfiles
          _ <- assertIO(response.status === Status.InternalServerError)
          _ <- assertIO(response.body === "")
          _ <- assertIO(saved.isEmpty)
        } yield ()
    }

    "return current missing-field semantics and do not hit the repo when a required field is absent" in {
      (profileApi: ProfileApi[IO], state: ProfileApiContractState) =>
        val userId = UUID.fromString("55555555-5555-5555-5555-555555555555")

        for {
          response <- observe(combineApis(profileApi), postJson(s"/profile/$userId", """{"name":"Kai"}"""))
          saved <- state.savedProfiles
          _ <- assertIO(response.status === Status.InternalServerError)
          _ <- assertIO(response.body === "")
          _ <- assertIO(saved.isEmpty)
        } yield ()
    }

    "return current invalid-field-type semantics and do not hit the repo on wrong json field types" in {
      (profileApi: ProfileApi[IO], state: ProfileApiContractState) =>
        val userId = UUID.fromString("66666666-6666-6666-6666-666666666666")

        for {
          response <- observe(combineApis(profileApi), postJson(s"/profile/$userId", """{"name":123,"description":"typed"}"""))
          saved <- state.savedProfiles
          _ <- assertIO(response.status === Status.InternalServerError)
          _ <- assertIO(response.body === "")
          _ <- assertIO(saved.isEmpty)
        } yield ()
    }

    "return current empty-body semantics and do not hit the repo on empty request bodies" in {
      (profileApi: ProfileApi[IO], state: ProfileApiContractState) =>
        val userId = UUID.fromString("77777777-7777-7777-7777-777777777777")

        for {
          response <- observe(combineApis(profileApi), postJson(s"/profile/$userId", ""))
          saved <- state.savedProfiles
          _ <- assertIO(response.status === Status.InternalServerError)
          _ <- assertIO(response.body === "")
          _ <- assertIO(saved.isEmpty)
        } yield ()
    }

    "return current server failure semantics when the ranks service fails" in {
      (profileApi: ProfileApi[IO], state: ProfileApiContractState) =>
        val userId = UUID.fromString("88888888-8888-8888-8888-888888888888")

        for {
          _ <- state.setGetRankResult(Left(QueryFailure.fromThrowable("get-rank", new RuntimeException("rank-boom"))))
          response <- observe(combineApis(profileApi), get(s"/profile/$userId"))
          _ <- assertIO(response.status === Status.InternalServerError)
          _ <- assertIO(response.body === "")
        } yield ()
    }

    "return current server failure semantics when the profiles repo fails on POST" in {
      (profileApi: ProfileApi[IO], state: ProfileApiContractState) =>
        val userId = UUID.fromString("99999999-9999-9999-9999-999999999999")

        for {
          _ <- state.setSetProfileResult(Left(QueryFailure.fromThrowable("set-profile", new RuntimeException("set-boom"))))
          response <- observe(
            combineApis(profileApi),
            postJson(s"/profile/$userId", """{"name":"Fail","description":"Case"}"""),
          )
          saved <- state.savedProfiles
          _ <- assertIO(response.status === Status.InternalServerError)
          _ <- assertIO(response.body === "")
          _ <- assertIO(saved.isEmpty)
        } yield ()
    }

    "return current server-level 404 semantics for an unknown route" in {
      (profileApi: ProfileApi[IO]) =>
        for {
          response <- observe(combineApis(profileApi), get("/totally-unknown"))
          _ <- assertIO(response.status === Status.NotFound)
          _ <- assertIO(response.body === "Not found")
        } yield ()
    }
  }
}

class ProfileApiContractState private (
  private val savedProfilesRef: Ref[Vector[(UserId, UserProfile)]],
  private val getRankResultRef: Ref[Either[QueryFailure, Option[RankedProfile]]],
  private val setProfileResultRef: Ref[Either[QueryFailure, Unit]],
) {
  val profiles: Profiles[IO] = new Profiles[IO] {
    def setProfile(userId: UserId, profile: UserProfile): IO[QueryFailure, Unit] =
      setProfileResultRef.get.flatMap {
        case Right(_) =>
          savedProfilesRef.update(_ :+ (userId -> profile))
        case Left(error) =>
          ZIO.fail(error)
      }

    def getProfile(userId: UserId): IO[QueryFailure, Option[UserProfile]] =
      savedProfilesRef.get.map(_.collectFirst { case (`userId`, profile) => profile })
  }

  val ranks: Ranks[IO] = new Ranks[IO] {
    def getRank(userId: UserId): IO[QueryFailure, Option[RankedProfile]] =
      getRankResultRef.get.flatMap(ZIO.fromEither(_))
  }

  def savedProfiles: UIO[Vector[(UserId, UserProfile)]] =
    savedProfilesRef.get

  def setGetRankResult(result: Either[QueryFailure, Option[RankedProfile]]): UIO[Unit] =
    getRankResultRef.set(result)

  def setSetProfileResult(result: Either[QueryFailure, Unit]): UIO[Unit] =
    setProfileResultRef.set(result)
}

object ProfileApiContractState {
  def make: UIO[ProfileApiContractState] =
    for {
      savedProfiles <- Ref.make(Vector.empty[(UserId, UserProfile)])
      getRankResult <- Ref.make[Either[QueryFailure, Option[RankedProfile]]](Right(None))
      setProfileResult <- Ref.make[Either[QueryFailure, Unit]](Right(()))
    } yield new ProfileApiContractState(savedProfiles, getRankResult, setProfileResult)
}
