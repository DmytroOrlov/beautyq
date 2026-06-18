package leaderboard.search

import cats.effect.Async
import distage.{Injector, ModuleDef}
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, HttpApi}
import leaderboard.model.QueryFailure
import leaderboard.plugins.BeautySearchPluginModules
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz.*
import zio.{IO, Task, ZIO}

final class BeautySearchOptInHttpApiModuleSpec extends AnyWordSpec {
  "BeautySearchPluginModules.api" should {
    "contribute BeautySearchApi to the real HttpApi weak set only when explicitly included" in {
      val service = new FailIfCalledBeautySearchService
      val probe   = buildProbe(service)
      val apis    = probe.allHttpApis

      assert(apis.size == 1)
      assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)
    }
  }

  private def buildProbe(service: FailIfCalledBeautySearchService): HttpApiSetProbe = {
    val module = new ModuleDef {
      include(BeautySearchPluginModules.api[IO])
      make[Async[Task]].fromValue(Async[Task])
      make[BeautySearchService[IO]].fromValue(service)
      make[HttpApiSetProbe].from {
        (
          beautySearchApi: BeautySearchApi[IO],
          allHttpApis: Set[HttpApi[IO]],
        ) =>
          val _ = beautySearchApi
          HttpApiSetProbe(allHttpApis)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[HttpApiSetProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[HttpApiSetProbe]
  }

  private final case class HttpApiSetProbe(
    allHttpApis: Set[HttpApi[IO]]
  )

  private final class FailIfCalledBeautySearchService extends BeautySearchService[IO] {
    override def search(input: UserSearchInput): IO[QueryFailure, BeautySearchResponse] =
      ZIO.suspendSucceed(
        ZIO.fail(QueryFailure.domain(s"FailIfCalledBeautySearchService.search was unexpectedly called with $input"))
      )
  }

}
