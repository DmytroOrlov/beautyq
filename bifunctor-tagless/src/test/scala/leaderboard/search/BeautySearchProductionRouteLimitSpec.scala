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
import leaderboard.search.dsl.BeautySearchSpecV1
import leaderboard.{HttpContractTestSupport, ObservedResponse}
import org.http4s.{HttpApp, Request, Status}
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz.*
import zio.{IO, Runtime, Task, Unsafe, ZIO}

final class BeautySearchProductionRouteLimitSpec extends AnyWordSpec with HttpContractTestSupport {
  "POST /beauty-search limit parameter" should {
    "return variantCarousel size <= limit for normal positive limit" in {
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
      val carousel = json.hcursor.downField("variantCarousel").focus.getOrElse(fail("missing variantCarousel"))
      assert(carousel.asArray.exists(_.nonEmpty))
      assert(carousel.asArray.exists(_.size <= 3))
    }

    "return empty variantCarousel for zero limit" in {
      val probe = buildProbe()
      val apis  = probe.allHttpApis

      val response = runIO(
        observeRoute(
          apis,
          postJson("/beauty-search", """{"query":"","userLat":53.58,"userLon":10.08,"limit":0}"""),
        )
      )

      assert(response.status == Status.Ok)

      val json = parse(response.body).getOrElse(fail(s"invalid Beauty search response JSON: ${response.body}"))
      val carousel = json.hcursor.downField("variantCarousel").focus.getOrElse(fail("missing variantCarousel"))
      assert(carousel.asArray.exists(_.isEmpty))
    }

    "return empty variantCarousel for negative limit" in {
      val probe = buildProbe()
      val apis  = probe.allHttpApis

      val response = runIO(
        observeRoute(
          apis,
          postJson("/beauty-search", """{"query":"","userLat":53.58,"userLon":10.08,"limit":-5}"""),
        )
      )

      assert(response.status == Status.Ok)

      val json = parse(response.body).getOrElse(fail(s"invalid Beauty search response JSON: ${response.body}"))
      val carousel = json.hcursor.downField("variantCarousel").focus.getOrElse(fail("missing variantCarousel"))
      assert(carousel.asArray.exists(_.isEmpty))
    }

    "return variantCarousel size <= variantSize for huge limit" in {
      val probe = buildProbe()
      val apis  = probe.allHttpApis

      val response = runIO(
        observeRoute(
          apis,
          postJson("/beauty-search", """{"query":"","userLat":53.58,"userLon":10.08,"limit":100000}"""),
        )
      )

      assert(response.status == Status.Ok)

      val json = parse(response.body).getOrElse(fail(s"invalid Beauty search response JSON: ${response.body}"))
      val carousel = json.hcursor.downField("variantCarousel").focus.getOrElse(fail("missing variantCarousel"))
      val actualSize = carousel.asArray.map(_.size).getOrElse(0)
      val maxSize = BeautySearchSpecV1.spec.carouselSpec.variantSize
      assert(actualSize <= maxSize)
    }
  }

  private def buildProbe(): BeautySearchProductionRouteLimitProbe = {
    val module = new distage.ModuleDef {
      include(LeaderboardPlugin.modules.api[IO])
      make[Async[Task]].fromValue(Async[Task])
      make[BeautySearchProductionRouteLimitProbe].from {
        (
          beautySearchApi: BeautySearchApi[IO],
          allHttpApis: Set[HttpApi[IO]],
        ) =>
          val _ = beautySearchApi
          BeautySearchProductionRouteLimitProbe(allHttpApis)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[BeautySearchProductionRouteLimitProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[BeautySearchProductionRouteLimitProbe]
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

  private final case class BeautySearchProductionRouteLimitProbe(
    allHttpApis: Set[HttpApi[IO]]
  )

  private def runIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
