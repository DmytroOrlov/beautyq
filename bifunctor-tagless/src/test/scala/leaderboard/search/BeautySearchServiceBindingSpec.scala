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
      val backend  = new RecordingBeautySearchBackend(Right(response))
      val service  = buildService(backend)
      val input    = UserSearchInput(query = "Plain Query", userLat = Some(BigDecimal("53.58")), userLon = Some(BigDecimal("10.08")), limit = 3)
      val parser   = new BeautySearchIntentParser(BeautySearchSpecV1.spec)

      val result = runIO(service.search(input))

      assert(result == response)
      assert(backend.calls == Vector(BackendCall(input, parser.parse(input))))
      assert(backend.calls.size == 1)

      val parsedIntent = backend.calls.head.intent
      assert(parsedIntent.originalQuery == input.query)
      assert(parsedIntent.normalizedTokens == List("plain", "query"))
      assert(parsedIntent.remainingText == "plain query")
    }

    "pass through backend failures from the assembled service" in {
      val failure = QueryFailure.domain("fake backend failed")
      val backend = new RecordingBeautySearchBackend(Left(failure))
      val service = buildService(backend)
      val input   = UserSearchInput(query = "failure query", userLat = None, userLon = None)

      val result = runIO(service.search(input).either)

      assert(result == Left(failure))
      assert(backend.calls.size == 1)
      assert(backend.calls.head.input == input)
      assert(backend.calls.head.intent.originalQuery == input.query)
      assert(backend.calls.head.intent.remainingText == "failure query")
    }
  }

  private def buildService(backend: RecordingBeautySearchBackend): BeautySearchService[IO] = {
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

  private final class RecordingBeautySearchBackend(
    result: Either[QueryFailure, BeautySearchResponse]
  ) extends BeautySearchBackend[IO] {
    private var recordedCalls: Vector[BackendCall] = Vector.empty

    def calls: Vector[BackendCall] = recordedCalls

    override def search(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, BeautySearchResponse] = {
      recordedCalls = recordedCalls :+ BackendCall(input, intent)
      ZIO.fromEither(result)
    }
  }

  private final case class BackendCall(
    input: UserSearchInput,
    intent: ParsedSearchIntent,
  )

  private def runIO[E, A](effect: ZIO[Any, E, A]): A =
    Unsafe.unsafe { implicit unsafe =>
      Runtime.default.unsafe.run(effect).getOrThrowFiberFailure()
    }
}
