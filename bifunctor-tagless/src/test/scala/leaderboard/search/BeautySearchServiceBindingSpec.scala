package leaderboard.search

import distage.{Injector, ModuleDef}
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.{BeautySearchSpec, BeautySearchSpecV1}
import leaderboard.search.parser.BeautySearchIntentParser
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe, ZIO}

final class BeautySearchServiceBindingSpec extends AnyWordSpec {
  "BeautySearchService.Impl binding with a fake backend" should {
    "assemble through a focused test module and hand parsed intent to the backend" in {
      val response = emptySearchResponse
      val input    = UserSearchInput(query = "Plain Query", userLat = Some(BigDecimal("53.58")), userLon = Some(BigDecimal("10.08")), limit = 3)
      val parser   = new BeautySearchIntentParser(BeautySearchSpecV1.spec)
      val expectedIntent = parser.parse(input)
      val backend  = new ExpectingBeautySearchBackend(Right(response), expectedInput = input, expectedIntent = expectedIntent)
      val service  = buildService(backend)

      val result = runIO(service.search(input))

      assert(result == response)
      assert(expectedIntent.originalQuery == input.query)
      assert(expectedIntent.normalizedTokens == List("plain", "query"))
      assert(expectedIntent.remainingText == "plain query")
    }

    "pass through backend failures from the assembled service" in {
      val failure = QueryFailure.domain("fake backend failed")
      val input   = UserSearchInput(query = "failure query", userLat = None, userLon = None)
      val parser   = new BeautySearchIntentParser(BeautySearchSpecV1.spec)
      val expectedIntent = parser.parse(input)
      val backend = new ExpectingBeautySearchBackend(Left(failure), expectedInput = input, expectedIntent = expectedIntent)
      val service = buildService(backend)

      val result = runIO(service.search(input).either)

      assert(result == Left(failure))
      assert(expectedIntent.originalQuery == input.query)
      assert(expectedIntent.remainingText == "failure query")
    }
  }

  private def buildService(backend: ExpectingBeautySearchBackend): BeautySearchService[IO] = {
    val module = new ModuleDef {
      make[BeautySearchSpec].fromValue(BeautySearchSpecV1.spec)
      make[BeautySearchIntentParser].from((spec: BeautySearchSpec) => new BeautySearchIntentParser(spec))
      make[BeautySearchBackend[IO]].fromValue(backend)
      make[BeautySearchService[IO]].from {
        (parser: BeautySearchIntentParser, backend: BeautySearchBackend[IO]) =>
          new BeautySearchService.Impl[IO](parser, backend)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[BeautySearchService[IO]],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[BeautySearchService[IO]]
  }

  private val emptySearchResponse: BeautySearchResponse =
    BeautySearchResponse(
      variantCarousel = Nil,
      providerCarousel = Nil,
      serviceIntentCarousel = Nil,
      facets = Nil,
      inferredFilters = Nil,
    )

  private final class ExpectingBeautySearchBackend(
    result: Either[QueryFailure, BeautySearchResponse],
    expectedInput: UserSearchInput,
    expectedIntent: ParsedSearchIntent,
  ) extends BeautySearchBackend[IO] {
    override def search(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, BeautySearchResponse] = {
      if (input != expectedInput) {
        ZIO.fail(QueryFailure.domain(s"Unexpected input: expected $expectedInput, got $input"))
      } else if (intent != expectedIntent) {
        ZIO.fail(QueryFailure.domain(s"Unexpected intent: expected $expectedIntent, got $intent"))
      } else {
        ZIO.fromEither(result)
      }
    }
  }

  private def runIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
