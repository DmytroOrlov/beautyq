package leaderboard.search

import cats.syntax.all.*
import cats.effect.Async
import distage.Injector
import fs2.text
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

final class BeautySearchProductionRouteErrorSpec extends AnyWordSpec with HttpContractTestSupport {
  "POST /beauty-search invalid request behavior" should {
    "return 500 with empty body for malformed JSON body" in {
      val probe = buildProbe()
      val apis  = probe.allHttpApis

      assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)

      val response = runIO(
        observeRoute(
          apis,
          postJson("/beauty-search", """{"query":"broken""""),
        )
      )

      assert(response.status == Status.InternalServerError)
      assert(response.body == "")
    }

    "return 500 with empty body for empty body" in {
      val probe = buildProbe()
      val apis  = probe.allHttpApis

      val response = runIO(
        observeRoute(
          apis,
          Request[Task](method = org.http4s.Method.POST, uri = org.http4s.Uri.unsafeFromString("/beauty-search")).putHeaders(org.http4s.headers.`Content-Type`(org.http4s.MediaType.application.json)),
        )
      )

      assert(response.status == Status.InternalServerError)
      assert(response.body == "")
    }

    "return 500 with empty body for wrong limit type" in {
      val probe = buildProbe()
      val apis  = probe.allHttpApis

      val response = runIO(
        observeRoute(
          apis,
          postJson("/beauty-search", """{"query":"маникюр","limit":"bad"}"""),
        )
      )

      assert(response.status == Status.InternalServerError)
      assert(response.body == "")
    }

    "return 500 with empty body for missing required field" in {
      val probe = buildProbe()
      val apis  = probe.allHttpApis

      val response = runIO(
        observeRoute(
          apis,
          postJson("/beauty-search", """{"userLat":53.58,"userLon":10.08,"limit":3}"""),
        )
      )

      assert(response.status == Status.InternalServerError)
      assert(response.body == "")
    }
  }

  private def buildProbe(): BeautySearchProductionRouteErrorProbe = {
    val module = new distage.ModuleDef {
      include(BeautySearchRouteModules.seedCatalogInMemory[IO])
      make[TapirHttpSupport[IO]].from(new TapirHttpSupport[IO])
      make[BeautySearchTapirEndpoints].fromValue(BeautySearchTapirEndpoints)
      make[Async[Task]].fromValue(Async[Task])
      make[BeautySearchProductionRouteErrorProbe].from {
        (
          beautySearchApi: BeautySearchApi[IO],
          allHttpApis: Set[HttpApi[IO]],
        ) =>
          val _ = beautySearchApi
          BeautySearchProductionRouteErrorProbe(allHttpApis)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[BeautySearchProductionRouteErrorProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[BeautySearchProductionRouteErrorProbe]
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

  private final case class BeautySearchProductionRouteErrorProbe(
    allHttpApis: Set[HttpApi[IO]]
  )

  private def runIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
