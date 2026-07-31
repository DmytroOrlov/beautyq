package leaderboard.search

import io.circe.Json
import leaderboard.HttpContractTestSupport
import leaderboard.api.{BeautySearchGen2Api, BeautySearchGen2Service}
import leaderboard.http.HttpApiFailure
import leaderboard.http.tapir.BeautySearchGen2TapirEndpoints
import leaderboard.search.beautyq.gen2.contract.BeautySearchRequestGen2
import leaderboard.search.beautyq.gen2.wiring.*
import leaderboard.search.beautyq.gen2.wiring.testkit.BeautyQOrchestrationTestKit
import leaderboard.search.gen2.{BeautyQSearchGen2HttpService, BeautyQSearchGen2Runtime, BeautyQSearchGen2RuntimeError}
import leaderboard.search.gen2.elasticsearch.ElasticsearchGenerationReference
import leaderboard.search.gen2.elasticsearch.lifecycle.ElasticsearchGenerationLifecycleError
import org.http4s.Status
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe, ZIO}
import zio.interop.catz.*

final class BeautyQSearchGen2HttpContractSpec extends AnyWordSpec with HttpContractTestSupport {
  "the native Gen2 HTTP contract at /beauty-search" should {
    "reject malformed JSON without invoking the service" in {
      val service = new BeautySearchGen2Service[IO] {
        def execute(request: BeautySearchRequestGen2): IO[HttpApiFailure, BeautyQSearchResponseGen2] =
          zio.ZIO.fail(HttpApiFailure.ServiceUnavailable("unexpected", "service must not be called"))
        def status: IO[HttpApiFailure, Json] =
          ZIO.succeed(Json.obj())
      }
      val api = new BeautySearchGen2Api[IO](service, BeautySearchGen2TapirEndpoints)

      val response = runIO(observe(api.http.orNotFound, postJson("/beauty-search", "{\"semanticText\":")))

      assert(response.status == Status.BadRequest)
    }

    "reject oversized transport bodies and cursors before backend execution" in {
      val service = new BeautySearchGen2Service[IO] {
        def execute(request: BeautySearchRequestGen2): IO[HttpApiFailure, BeautyQSearchResponseGen2] =
          ZIO.fail(HttpApiFailure.ServiceUnavailable("unexpected", "service must not be called"))
        def status: IO[HttpApiFailure, Json] = ZIO.succeed(Json.obj())
      }
      val api = new BeautySearchGen2Api[IO](service, BeautySearchGen2TapirEndpoints)
      val oversizedBody = " " * (leaderboard.search.beautyq.gen2.contract.BeautyQSearchRequestBudget.MaxTransportBodyBytes + 1)
      val bodyResponse = runIO(observe(api.http.orNotFound, postJson("/beauty-search", oversizedBody)))
      assert(bodyResponse.status == Status.BadRequest)
      assert(bodyResponse.body.contains("request_budget_exceeded"))

      val cursor = "c" * (leaderboard.search.beautyq.gen2.contract.BeautyQSearchRequestBudget.MaxCursorUtf8Bytes + 1)
      val cursorBody = s"""{"query":"nails","filters":[],"requestedFacets":[],"sort":[],"page":{"cursor":"$cursor","size":20}}"""
      val cursorResponse = runIO(observe(api.http.orNotFound, postJson("/beauty-search", cursorBody)))
      assert(cursorResponse.status == Status.BadRequest)
      assert(cursorResponse.body.contains("request_budget_exceeded"))
    }

    "keep the new route isolated and preserve typed service failures" in {
      val service = new BeautySearchGen2Service[IO] {
        def execute(request: BeautySearchRequestGen2): IO[HttpApiFailure, BeautyQSearchResponseGen2] =
          zio.ZIO.fail(HttpApiFailure.ServiceUnavailable("gen2_unavailable", "not serving"))
        def status: IO[HttpApiFailure, Json] =
          ZIO.succeed(Json.obj())
      }
      val api = new BeautySearchGen2Api[IO](service, BeautySearchGen2TapirEndpoints)

      val response = runIO(observe(api.http.orNotFound, postJson("/beauty-search", "{\"query\":\"nails\",\"filters\":[],\"requestedFacets\":[],\"sort\":[],\"page\":{\"cursor\":\"cursor-token\",\"size\":20},\"userLocation\":{\"lat\":53.58,\"lon\":10.08}}")))

      assert(response.status == Status.ServiceUnavailable)
    }

    "map a decoded BeautyQ request-budget violation to HTTP 400" in {
      val context = BeautyQOrchestrationTestKit.eligible()
      val application = BeautyQSearchApplication.make(BeautyQOrchestrationTestKit.materialized, context.baselineService, context.embedding, context.qdrant)
      val runtime = BeautyQSearchGen2Runtime.make(application, fullSearchStatus)
      val api = new BeautySearchGen2Api[IO](new BeautyQSearchGen2HttpService(runtime), BeautySearchGen2TapirEndpoints)
      val query = "q" * (leaderboard.search.beautyq.gen2.contract.BeautyQSearchRequestBudget.MaxQueryCodePoints + 1)
      val response = runIO(observe(api.http.orNotFound, postJson(
        "/beauty-search",
        s"""{"query":"$query","filters":[],"requestedFacets":[],"sort":[],"page":{"size":20}}""",
      )))
      assert(response.status == Status.BadRequest)
      assert(response.body.contains("request_budget_exceeded"))
    }

    "serve a successful full-search response" in {
      val context = BeautyQOrchestrationTestKit.eligible()
      val application = BeautyQSearchApplication.make(BeautyQOrchestrationTestKit.materialized, context.baselineService, context.embedding, context.qdrant)
      val runtime = BeautyQSearchGen2Runtime.make(application, fullSearchStatus)
      val api = new BeautySearchGen2Api[IO](new BeautyQSearchGen2HttpService(runtime), BeautySearchGen2TapirEndpoints)

      val response = runIO(observe(api.http.orNotFound, postJson("/beauty-search", validBody)))
      assert(response.status == Status.Ok)
      assert(response.body.contains("\"supplementStatus\":\"supplemented\""))
    }

    "return baseline-only and degradable results as successful HTTP responses" in {
      val baselineContext = BeautyQOrchestrationTestKit.eligible()
      val baselineApplication = BeautyQSearchApplication.makeBaselineOnly(BeautyQOrchestrationTestKit.materialized, baselineContext.baselineService)
      val baselineRuntime = BeautyQSearchGen2Runtime.make(
        baselineApplication,
        baselineOnlyStatus,
      )
      val baselineResponse = runIO(observe(new BeautySearchGen2Api[IO](new BeautyQSearchGen2HttpService(baselineRuntime), BeautySearchGen2TapirEndpoints).http.orNotFound, postJson("/beauty-search", validBody)))
      assert(baselineResponse.status == Status.Ok)
      assert(baselineResponse.body.contains("\"supplementStatus\":\"no_append\""))

      val degradedContext = BeautyQOrchestrationTestKit.timedOut
      val degradedApplication = BeautyQSearchApplication.make(BeautyQOrchestrationTestKit.materialized, degradedContext.baselineService, degradedContext.embedding, degradedContext.qdrant)
      val degradedRuntime = BeautyQSearchGen2Runtime.make(degradedApplication, fullSearchStatus)
      val degradedResponse = runIO(observe(new BeautySearchGen2Api[IO](new BeautyQSearchGen2HttpService(degradedRuntime), BeautySearchGen2TapirEndpoints).http.orNotFound, postJson("/beauty-search", validBody)))
      assert(degradedResponse.status == Status.Ok)
      assert(degradedResponse.body.contains("\"supplementStatus\":\"supplement_failed\""))
    }

    "return HTTP 409 for an exact stale-generation failure with the correct body" in {
      val service = new BeautySearchGen2Service[IO] {
        def execute(request: BeautySearchRequestGen2): IO[HttpApiFailure, BeautyQSearchResponseGen2] =
          zio.ZIO.fail(
            HttpApiFailure.Conflict(
              "stale_search_cursor",
              "Search cursor refers to a deleted generation; restart pagination without the cursor",
            )
          )
        def status: IO[HttpApiFailure, Json] =
          ZIO.succeed(Json.obj())
      }
      val api = new BeautySearchGen2Api[IO](service, BeautySearchGen2TapirEndpoints)

      val response = runIO(observe(api.http.orNotFound, postJson("/beauty-search", validBody)))
      assert(response.status == Status.Conflict)
      assert(response.body.contains("\"code\":\"stale_search_cursor\""))
      assert(response.body.contains("restart pagination without the cursor"))
    }

    "map a full StaleGeneration chain through toHttpApiFailure to HttpApiFailure.Conflict" in {
      val reference = ElasticsearchGenerationReference("test-stale-generation")
      val runtimeError = BeautyQSearchGen2RuntimeError.Application(
        BeautyQSearchApplicationError.Baseline(
          BeautyQElasticsearchBaselineServiceError.Lifecycle(
            ElasticsearchGenerationLifecycleError.StaleGeneration(reference)
          )
        )
      )

      val result = BeautyQSearchGen2HttpService.toHttpApiFailure(runtimeError)
      assert(result == HttpApiFailure.Conflict(
        "stale_search_cursor",
        "Search cursor refers to a deleted generation; restart pagination without the cursor",
      ))
    }

    "return HTTP 404 for the absent /beauty-search-gen2 path" in {
      val service = new BeautySearchGen2Service[IO] {
        def execute(request: BeautySearchRequestGen2): IO[HttpApiFailure, BeautyQSearchResponseGen2] =
          zio.ZIO.fail(HttpApiFailure.ServiceUnavailable("unexpected", "service must not be called"))
        def status: IO[HttpApiFailure, Json] =
          ZIO.succeed(Json.obj())
      }
      val api = new BeautySearchGen2Api[IO](service, BeautySearchGen2TapirEndpoints)

      val response = runIO(observe(api.http.orNotFound, postJson("/beauty-search-gen2", validBody)))
      assert(response.status == Status.NotFound)
    }
  }

  private val fullSearchStatus: StartupServingStatus = new StartupServingStatus(
    policy = SupplementStartupPolicy.Required,
    servingMode = BeautyQServingMode.FullSearch,
    condition = "healthy",
    reason = None,
    restartRequired = false,
    sourceContentFingerprint = "test-fp",
    projectedDocumentsFingerprint = "test-fp",
    elasticsearchReference = "test-ref",
    elasticsearchPhysicalTarget = "test-target",
    qdrantGenerationId = Some("test-gen"),
    qdrantPhysicalCollection = Some("test-col"),
  )

  private val baselineOnlyStatus: StartupServingStatus = new StartupServingStatus(
    policy = SupplementStartupPolicy.Disabled,
    servingMode = BeautyQServingMode.BaselineOnly,
    condition = "limited",
    reason = Some(new StartupServingStatus.Reason("qdrant_supplement_operator_disabled", "Qdrant supplement was disabled by operator policy; the complete Elasticsearch baseline was returned; restart is required", "supplement disabled by operator startup policy", None)),
    restartRequired = true,
    sourceContentFingerprint = "test-fp",
    projectedDocumentsFingerprint = "test-fp",
    elasticsearchReference = "test-ref",
    elasticsearchPhysicalTarget = "test-target",
    qdrantGenerationId = None,
    qdrantPhysicalCollection = None,
  )

  private val validBody =
    "{\"query\":\"relaxing appointment\",\"filters\":[],\"requestedFacets\":[],\"sort\":[],\"page\":{\"size\":20}}"

  private def runIO[E, A](effect: zio.ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
