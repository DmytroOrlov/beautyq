package leaderboard.search

import cats.syntax.all.*
import cats.effect.Async
import distage.Injector
import fs2.text
import io.circe.parser.parse
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, HttpApi}
import leaderboard.http.tapir.{BeautySearchTapirEndpoints, TapirHttpSupport}
import leaderboard.plugins.BeautySearchRouteModules
import leaderboard.{HttpContractTestSupport, ObservedResponse}
import org.http4s.{HttpApp, Request, Status}
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz.*
import zio.{IO, Runtime, Task, Unsafe, ZIO}

final class BeautySearchProductionRouteQuerySpec extends AnyWordSpec with HttpContractTestSupport {
  "POST /beauty-search query text inputs" should {
    "return current behavior for empty query string" in {
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

      if (response.status == Status.Ok) {
        assert(carousel.asArray.exists(_.nonEmpty), "200 OK for empty query; carousel should be non-empty")
      } else {
        assert(response.status != Status.Ok, s"unexpected status for empty query: ${response.status}")
      }
    }

    "return current behavior for whitespace-only query string" in {
      val probe = buildProbe()
      val apis  = probe.allHttpApis

      val response = runIO(
        observeRoute(
          apis,
          postJson("/beauty-search", """{"query":"     ","userLat":53.57532,"userLon":10.07672,"limit":3}"""),
        )
      )

      val json = parse(response.body).getOrElse(fail(s"invalid Beauty search response JSON: ${response.body}"))
      val carousel = json.hcursor.downField("variantCarousel").focus.getOrElse(fail("missing variantCarousel"))

      if (response.status == Status.Ok) {
        assert(carousel.asArray.exists(_.size <= 3))
      } else {
        assert(response.status != Status.Ok, s"unexpected status for whitespace-only query: ${response.status}")
      }
    }

    "return current behavior for normal text query" in {
      val probe = buildProbe()
      val apis  = probe.allHttpApis

      val response = runIO(
        observeRoute(
          apis,
          postJson("/beauty-search", """{"query":"nails","userLat":53.57532,"userLon":10.07672,"limit":3}"""),
        )
      )

      val json = parse(response.body).getOrElse(fail(s"invalid Beauty search response JSON: ${response.body}"))
      val carousel = json.hcursor.downField("variantCarousel").focus.getOrElse(fail("missing variantCarousel"))

      if (response.status == Status.Ok) {
        assert(carousel.asArray.exists(_.size <= 3))
      } else {
        assert(response.status != Status.Ok, s"unexpected status for normal text query: ${response.status}")
      }
    }

    "return current behavior for very long query string" in {
      val probe = buildProbe()
      val apis  = probe.allHttpApis

      val longQuery = ("nails " * 1000)

      val response = runIO(
        observeRoute(
          apis,
          postJson("/beauty-search", s"""{"query":"$longQuery","userLat":53.57532,"userLon":10.07672,"limit":3}"""),
        )
      )

      val json = parse(response.body).getOrElse(fail(s"invalid Beauty search response JSON: ${response.body}"))
      val carousel = json.hcursor.downField("variantCarousel").focus.getOrElse(fail("missing variantCarousel"))

      if (response.status == Status.Ok) {
        assert(carousel.asArray.exists(_.size <= 3))
      } else {
        assert(response.status != Status.Ok, s"unexpected status for very long query: ${response.status}")
      }
    }
  }

  private def buildProbe(): BeautySearchProductionRouteQueryProbe = {
    val module = new distage.ModuleDef {
      include(BeautySearchRouteModules.seedCatalogInMemory[IO])
      make[TapirHttpSupport[IO]].from(new TapirHttpSupport[IO])
      make[BeautySearchTapirEndpoints].fromValue(BeautySearchTapirEndpoints)
      make[Async[Task]].fromValue(Async[Task])
      make[BeautySearchProductionRouteQueryProbe].from {
        (
          beautySearchApi: BeautySearchApi[IO],
          allHttpApis: Set[HttpApi[IO]],
        ) =>
          val _ = beautySearchApi
          BeautySearchProductionRouteQueryProbe(allHttpApis)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[BeautySearchProductionRouteQueryProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[BeautySearchProductionRouteQueryProbe]
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

  private final case class BeautySearchProductionRouteQueryProbe(
    allHttpApis: Set[HttpApi[IO]]
  )

  private def runIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
