package leaderboard.search

import distage.{Injector, ModuleDef}
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.HttpContractTestSupport
import leaderboard.api.BeautySearchApi
import leaderboard.http.tapir.BeautySearchTapirEndpoints
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.{BeautySearchSpec, BeautySearchSpecV1}
import leaderboard.search.parser.BeautySearchIntentParser
import org.http4s.Status
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz.*
import zio.{IO, Runtime, Unsafe, ZIO}

final class BeautySearchAppGraphBoundarySpec extends AnyWordSpec with HttpContractTestSupport {
  "Beauty search app-graph boundary" should {
    "assemble the API/service/backend stack only through an explicit test-local module" in {
      val parser = new BeautySearchIntentParser(BeautySearchSpecV1.spec)
      val input = UserSearchInput("plain query", userLat = None, userLon = None, limit = 2)
      val expectedIntent = parser.parse(input)
      val backend = new ExpectingBeautySearchBackend(emptySearchResponse, expectedInput = input, expectedIntent = expectedIntent)
      val stack = buildStack(backend)

      val response = runIO(stack.service.search(input))

      assert(response == emptySearchResponse)
      assert(expectedIntent.remainingText == "plain query")
    }

    "expose the assembled API route from the same explicit test-local module" in {
      val input = UserSearchInput("маникюр", userLat = None, userLon = None, limit = 1)
      val parser = new BeautySearchIntentParser(BeautySearchSpecV1.spec)
      val expectedIntent = parser.parse(input)
      val backend = new ExpectingBeautySearchBackend(emptySearchResponse, expectedInput = input, expectedIntent = expectedIntent)
      val stack = buildStack(backend)

      val observed = runIO(
        observe(
          stack.api.http.orNotFound,
          postJson("/beauty-search", """{"query":"маникюр","userLat":null,"userLon":null,"limit":1}"""),
        )
      )

      assert(observed.status == Status.Ok)
      assert(observed.body == emptySearchResponseJson)
      assert(input.query == "маникюр")
      assert(input.limit == 1)
    }
  }

  private def buildStack(backend: ExpectingBeautySearchBackend): BeautySearchTestAppStack = {
    val module = new ModuleDef {
      make[BeautySearchTapirEndpoints].fromValue(BeautySearchTapirEndpoints)
      make[BeautySearchSpec].fromValue(BeautySearchSpecV1.spec)
      make[BeautySearchIntentParser].from((spec: BeautySearchSpec) => new BeautySearchIntentParser(spec))
      make[BeautySearchBackend[IO]].fromValue(backend)
      make[BeautySearchService[IO]].from {
        (parser: BeautySearchIntentParser, backend: BeautySearchBackend[IO]) =>
          new BeautySearchService.Impl[IO](parser, backend)
      }
      make[BeautySearchApi[IO]].from {
        (
          service: BeautySearchService[IO],
          endpoints: BeautySearchTapirEndpoints,
        ) =>
          new BeautySearchApi[IO](service, endpoints)
      }
      make[BeautySearchTestAppStack].from {
        (
          api: BeautySearchApi[IO],
          service: BeautySearchService[IO],
          backend: BeautySearchBackend[IO],
          parser: BeautySearchIntentParser,
          endpoints: BeautySearchTapirEndpoints,
        ) =>
          BeautySearchTestAppStack(api, service, backend, parser, endpoints)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[BeautySearchTestAppStack],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[BeautySearchTestAppStack]
  }

  private final case class BeautySearchTestAppStack(
    api: BeautySearchApi[IO],
    service: BeautySearchService[IO],
    backend: BeautySearchBackend[IO],
    parser: BeautySearchIntentParser,
    endpoints: BeautySearchTapirEndpoints,
  )

  private final class ExpectingBeautySearchBackend(
    response: BeautySearchResponse,
    expectedInput: UserSearchInput,
    expectedIntent: ParsedSearchIntent,
  ) extends BeautySearchBackend[IO] {
    override def search(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, BeautySearchResponse] = {
      if (input != expectedInput) {
        ZIO.fail(QueryFailure.domain(s"Unexpected input: expected $expectedInput, got $input"))
      } else if (intent != expectedIntent) {
        ZIO.fail(QueryFailure.domain(s"Unexpected intent: expected $expectedIntent, got $intent"))
      } else {
        ZIO.succeed(response)
      }
    }
  }

  private val emptySearchResponse: BeautySearchResponse =
    BeautySearchResponse(
      variantCarousel = Nil,
      providerCarousel = Nil,
      serviceIntentCarousel = Nil,
      facets = Nil,
      inferredFilters = Nil,
    )

  private val emptySearchResponseJson: String =
    """{"variantCarousel":[],"providerCarousel":[],"serviceIntentCarousel":[],"facets":[],"inferredFilters":[],"executionMode":"es_only","qdrantSupplement":{"status":"not_used","policy":"none","appendedVariantIds":[],"contribution":"none"}}"""

  private def runIO[E, A](effect: IO[E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
