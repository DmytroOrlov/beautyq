package leaderboard.search

import cats.syntax.all.*
import cats.effect.Async
import distage.Injector
import fs2.text
import io.circe.parser.parse
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, HttpApi}
import leaderboard.plugins.LeaderboardPlugin
import leaderboard.{HttpContractTestSupport, ObservedResponse}
import org.http4s.{HttpApp, Request, Status}
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz.*
import zio.{IO, Runtime, Task, Unsafe, ZIO}

final class BeautySearchProductionRouteExposureSpec extends AnyWordSpec with HttpContractTestSupport {
  "LeaderboardPlugin.modules.api" should {
    "expose BeautySearchApi through the default API set and serve the seed-catalog route" in {
      val probe = buildProbe()
      val apis  = probe.allHttpApis

      assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)

      val response = runIO(
        observeRoute(
          apis,
          postJson("/beauty-search", """{"query":"","userLat":53.58,"userLon":10.08,"limit":3}"""),
        )
      )

      assert(response.status == Status.Ok)

      val json = parse(response.body).getOrElse(fail(s"invalid Beauty search response JSON: ${response.body}"))
      assert(json.hcursor.downField("variantCarousel").focus.exists(_.asArray.exists(_.nonEmpty)))
    }
  }

  private def buildProbe(): BeautySearchProductionRouteExposureProbe = {
    val module = new distage.ModuleDef {
      include(LeaderboardPlugin.modules.api[IO])
      make[Async[Task]].fromValue(Async[Task])
      make[BeautySearchProductionRouteExposureProbe].from {
        (
          beautySearchApi: BeautySearchApi[IO],
          allHttpApis: Set[HttpApi[IO]],
        ) =>
          val _ = beautySearchApi
          BeautySearchProductionRouteExposureProbe(allHttpApis)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[BeautySearchProductionRouteExposureProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[BeautySearchProductionRouteExposureProbe]
  }

  private def observeRoute(
    apis: Set[HttpApi[IO]],
    request: Request[Task],
  ): Task[ObservedResponse] = {
    val app: HttpApp[Task] = apis.map(_.http).toList.foldK.orNotFound

    app.run(request).flatMap {
      response =>
        response.body
          .through(text.utf8.decode)
          .compile
          .string
          .map(body => ObservedResponse(response.status, body))
    }
  }

  private final case class BeautySearchProductionRouteExposureProbe(
    allHttpApis: Set[HttpApi[IO]]
  )

  private def runIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
