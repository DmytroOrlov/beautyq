package leaderboard.search

import leaderboard.HttpContractTestSupport
import leaderboard.api.{BeautySearchGen2Api, BeautySearchGen2Service}
import leaderboard.http.HttpApiFailure
import leaderboard.http.tapir.BeautySearchGen2TapirEndpoints
import leaderboard.search.beautyq.gen2.contract.BeautySearchRequestGen2
import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.beautyq.gen2.wiring.testkit.BeautyQOrchestrationTestKit
import leaderboard.search.gen2.{BeautyQSearchGen2HttpService, BeautyQSearchGen2Runtime}
import org.http4s.Status
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe}
import zio.interop.catz.*

final class BeautyQSearchGen2HttpContractSpec extends AnyWordSpec with HttpContractTestSupport {
  "the opt-in Gen2 HTTP contract" should {
    "reject malformed JSON without invoking the service" in {
      val service = new BeautySearchGen2Service[IO] {
        def execute(request: BeautySearchRequestGen2): IO[HttpApiFailure, BeautyQSearchResponseGen2] =
          zio.ZIO.fail(HttpApiFailure.ServiceUnavailable("unexpected", "service must not be called"))
      }
      val api = new BeautySearchGen2Api[IO](service, BeautySearchGen2TapirEndpoints)

      val response = runIO(observe(api.http.orNotFound, postJson("/beauty-search-gen2", "{\"semanticText\":")))

      assert(response.status == Status.BadRequest)
    }

    "keep the new route isolated and preserve typed service failures" in {
      val service = new BeautySearchGen2Service[IO] {
        def execute(request: BeautySearchRequestGen2): IO[HttpApiFailure, BeautyQSearchResponseGen2] =
          zio.ZIO.fail(HttpApiFailure.ServiceUnavailable("gen2_unavailable", "not serving"))
      }
      val api = new BeautySearchGen2Api[IO](service, BeautySearchGen2TapirEndpoints)

      val response = runIO(observe(api.http.orNotFound, postJson("/beauty-search-gen2", "{\"query\":\"nails\",\"filters\":[],\"requestedFacets\":[],\"sort\":[],\"page\":{\"cursor\":\"cursor-token\",\"size\":20},\"userLocation\":{\"lat\":53.58,\"lon\":10.08}}")))

      assert(response.status == Status.ServiceUnavailable)
    }

    "serve a successful full-search response and keep the V1 route absent from opt-in composition" in {
      val context = BeautyQOrchestrationTestKit.eligible()
      val application = BeautyQSearchApplication.make(BeautyQOrchestrationTestKit.materialized, context.baselineService, context.embedding, context.qdrant)
      val runtime = BeautyQSearchGen2Runtime.make(application, BeautyQSupplementReadinessPolicy.evaluate(Set.empty))
      val api = new BeautySearchGen2Api[IO](new BeautyQSearchGen2HttpService(runtime), BeautySearchGen2TapirEndpoints)

      val response = runIO(observe(api.http.orNotFound, postJson("/beauty-search-gen2", validBody)))
      assert(response.status == Status.Ok)
      assert(response.body.contains("\"supplementStatus\":\"supplemented\""))

      val oldRoute = runIO(observe(api.http.orNotFound, postJson("/beauty-search", validBody)))
      assert(oldRoute.status == Status.NotFound)
    }

    "return baseline-only and degradable results as successful HTTP responses" in {
      val baselineContext = BeautyQOrchestrationTestKit.eligible()
      val baselineApplication = BeautyQSearchApplication.make(BeautyQOrchestrationTestKit.materialized, baselineContext.baselineService, baselineContext.embedding, baselineContext.qdrant)
      val baselineRuntime = BeautyQSearchGen2Runtime.make(
        baselineApplication,
        BeautyQSupplementReadinessPolicy.evaluate(Set(BeautyQSearchDependency.QdrantSupplement)),
      )
      val baselineResponse = runIO(observe(new BeautySearchGen2Api[IO](new BeautyQSearchGen2HttpService(baselineRuntime), BeautySearchGen2TapirEndpoints).http.orNotFound, postJson("/beauty-search-gen2", validBody)))
      assert(baselineResponse.status == Status.Ok)
      assert(baselineResponse.body.contains("\"supplementStatus\":\"no_append\""))

      val degradedContext = BeautyQOrchestrationTestKit.timedOut
      val degradedApplication = BeautyQSearchApplication.make(BeautyQOrchestrationTestKit.materialized, degradedContext.baselineService, degradedContext.embedding, degradedContext.qdrant)
      val degradedRuntime = BeautyQSearchGen2Runtime.make(degradedApplication, BeautyQSupplementReadinessPolicy.evaluate(Set.empty))
      val degradedResponse = runIO(observe(new BeautySearchGen2Api[IO](new BeautyQSearchGen2HttpService(degradedRuntime), BeautySearchGen2TapirEndpoints).http.orNotFound, postJson("/beauty-search-gen2", validBody)))
      assert(degradedResponse.status == Status.Ok)
      assert(degradedResponse.body.contains("\"supplementStatus\":\"supplement_failed\""))
    }
  }

  private val validBody =
    "{\"query\":\"relaxing appointment\",\"filters\":[],\"requestedFacets\":[],\"sort\":[],\"page\":{\"size\":20}}"

  private def runIO[E, A](effect: zio.ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
