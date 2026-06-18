package leaderboard.search

import cats.effect.Async
import cats.syntax.all.*
import distage.{Injector, ModuleDef}
import fs2.text
import io.circe.parser.parse
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, HttpApi}
import leaderboard.plugins.BeautySearchRouteModules
import leaderboard.{HttpContractTestSupport, ObservedResponse}
import org.http4s.{HttpApp, Request, Status}
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz.*
import zio.{IO, Runtime, Task, Unsafe, ZIO}

final class BeautySearchOptInRouteModuleSpec extends AnyWordSpec with HttpContractTestSupport {
  "BeautySearchRouteModules.seedCatalogInMemory" should {
    "contribute the opt-in BeautySearchApi and serve a seed-catalog in-memory search route" in {
      val probe = buildProbe()
      val apis  = probe.allHttpApis

      assert(apis.size == 1)
      assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)

      val response = runIO(
        observeRoute(
          apis,
          postJson("/beauty-search", """{"query":"nails","userLat":53.58,"userLon":10.08,"limit":3}"""),
        )
      )

      assert(response.status == Status.Ok)

      val json = parse(response.body).getOrElse(fail(s"invalid Beauty search response JSON: ${response.body}"))
      assert(json.hcursor.downField("variantCarousel").focus.exists(_.asArray.exists(_.nonEmpty)))
    }
  }

  private def buildProbe(): BeautySearchOptInRouteModuleProbe = {
    val module = new ModuleDef {
      include(BeautySearchRouteModules.seedCatalogInMemory[IO])
      make[Async[Task]].fromValue(Async[Task])
      make[BeautySearchOptInRouteModuleProbe].from {
        (
          beautySearchApi: BeautySearchApi[IO],
          allHttpApis: Set[HttpApi[IO]],
        ) =>
          val _ = beautySearchApi
          BeautySearchOptInRouteModuleProbe(allHttpApis)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[BeautySearchOptInRouteModuleProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[BeautySearchOptInRouteModuleProbe]
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

  private final case class BeautySearchOptInRouteModuleProbe(
    allHttpApis: Set[HttpApi[IO]]
  )

  private def runIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
