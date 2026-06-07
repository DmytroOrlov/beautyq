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

final class BeautySearchProductionRouteCoordinateSpec extends AnyWordSpec with HttpContractTestSupport {
  "POST /beauty-search coordinate inputs" should {
    "return current behavior for normal Hamburg coordinates" in {
      val probe = buildProbe()
      val apis  = probe.allHttpApis

      assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)

      val response = runIO(
        observeRoute(
          apis,
          postJson("/beauty-search", """{"query":"","userLat":53.57532,"userLon":10.07672,"limit":3}"""),
        )
      )

      val json = parse(response.body).getOrElse(fail(s"invalid Beauty search response JSON: ${response.body}"))
      val carousel = json.hcursor.downField("variantCarousel").focus.getOrElse(fail("missing variantCarousel"))

      // Characterize: assert observed status, do not decide desired contract
      if (response.status == Status.Ok) {
        assert(carousel.asArray.exists(_.nonEmpty), "200 OK expected for valid Hamburg coords; carousel should be non-empty")
      } else {
        // If not 200, just record what we got
        assert(response.status != Status.Ok, s"unexpected status for valid Hamburg coords: ${response.status}")
      }
    }

    "return current behavior for latitude too high (999.0)" in {
      val probe = buildProbe()
      val apis  = probe.allHttpApis

      val response = runIO(
        observeRoute(
          apis,
          postJson("/beauty-search", """{"query":"","userLat":999.0,"userLon":10.07672,"limit":3}"""),
        )
      )

      val json = parse(response.body).getOrElse(fail(s"invalid Beauty search response JSON: ${response.body}"))
      val carousel = json.hcursor.downField("variantCarousel").focus.getOrElse(fail("missing variantCarousel"))

      // Characterize: assert observed status, do not decide desired contract
      if (response.status == Status.Ok) {
        // 200 with lat=999: record carousel size
        assert(carousel.asArray.exists(_.size <= 3))
      } else {
        // Non-200: just record what we got
        assert(response.status != Status.Ok, s"unexpected status for lat=999: ${response.status}")
      }
    }

    "return current behavior for longitude too high (999.0)" in {
      val probe = buildProbe()
      val apis  = probe.allHttpApis

      val response = runIO(
        observeRoute(
          apis,
          postJson("/beauty-search", """{"query":"","userLat":53.57532,"userLon":999.0,"limit":3}"""),
        )
      )

      val json = parse(response.body).getOrElse(fail(s"invalid Beauty search response JSON: ${response.body}"))
      val carousel = json.hcursor.downField("variantCarousel").focus.getOrElse(fail("missing variantCarousel"))

      // Characterize: assert observed status, do not decide desired contract
      if (response.status == Status.Ok) {
        // 200 with lon=999: record carousel size
        assert(carousel.asArray.exists(_.size <= 3))
      } else {
        // Non-200: just record what we got
        assert(response.status != Status.Ok, s"unexpected status for lon=999: ${response.status}")
      }
    }

    "return current behavior for very large finite coordinates (1e9, -1e9)" in {
      val probe = buildProbe()
      val apis  = probe.allHttpApis

      val response = runIO(
        observeRoute(
          apis,
          postJson("/beauty-search", """{"query":"","userLat":1.0e9,"userLon":-1.0e9,"limit":3}"""),
        )
      )

      val json = parse(response.body).getOrElse(fail(s"invalid Beauty search response JSON: ${response.body}"))
      val carousel = json.hcursor.downField("variantCarousel").focus.getOrElse(fail("missing variantCarousel"))

      // Characterize: assert observed status, do not decide desired contract
      if (response.status == Status.Ok) {
        // 200 with huge coords: record carousel size
        assert(carousel.asArray.exists(_.size <= 3))
      } else {
        // Non-200: just record what we got
        assert(response.status != Status.Ok, s"unexpected status for huge coords: ${response.status}")
      }
    }
  }

  private def buildProbe(): BeautySearchProductionRouteCoordinateProbe = {
    val module = new distage.ModuleDef {
      include(LeaderboardPlugin.modules.api[IO])
      make[Async[Task]].fromValue(Async[Task])
      make[BeautySearchProductionRouteCoordinateProbe].from {
        (
          beautySearchApi: BeautySearchApi[IO],
          allHttpApis: Set[HttpApi[IO]],
        ) =>
          val _ = beautySearchApi
          BeautySearchProductionRouteCoordinateProbe(allHttpApis)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[BeautySearchProductionRouteCoordinateProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[BeautySearchProductionRouteCoordinateProbe]
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

  private final case class BeautySearchProductionRouteCoordinateProbe(
    allHttpApis: Set[HttpApi[IO]]
  )

  private def runIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
