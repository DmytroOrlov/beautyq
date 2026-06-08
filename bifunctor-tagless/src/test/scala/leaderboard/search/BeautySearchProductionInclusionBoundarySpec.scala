package leaderboard.search

import distage.{Injector, ModuleDef}
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, BeautySearchProductionInclusionActivation, BeautySearchProductionInclusionHandle}
import leaderboard.http.tapir.{BeautySearchTapirEndpoints, TapirHttpSupport}
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.{BeautySearchSpec, BeautySearchSpecV1}
import leaderboard.search.parser.BeautySearchIntentParser
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz.*
import zio.{IO, ZIO}

final class BeautySearchProductionInclusionBoundarySpec extends AnyWordSpec {
  "Beauty search production inclusion boundary" should {
    "default to Disabled and expose an empty disabled handle" in {
      val handle = BeautySearchProductionInclusionHandle.disabled[IO]

      assert(BeautySearchProductionInclusionActivation.default == BeautySearchProductionInclusionActivation.Disabled)
      assert(!handle.isEnabled)
      assert(handle.toOption.isEmpty)
      assert(handle.api.isEmpty)
    }

    "not evaluate the API thunk when Disabled" in {
      val handle = BeautySearchProductionInclusionHandle.buildIfEnabled[IO](
        activation = BeautySearchProductionInclusionActivation.Disabled,
        api = throw new RuntimeException("disabled production inclusion must not construct BeautySearchApi"),
      )

      assert(!handle.isEnabled)
      assert(handle.toOption.isEmpty)
    }

    "evaluate the API thunk exactly once when Enabled" in {
      var apiEvaluations = 0

      val handle = BeautySearchProductionInclusionHandle.buildIfEnabled[IO](
        activation = BeautySearchProductionInclusionActivation.Enabled,
        api = {
          apiEvaluations += 1
          buildApi(new RecordingBeautySearchService(emptySearchResponse))
        },
      )

      assert(handle.isEnabled)
      assert(handle.toOption.nonEmpty)
      assert(apiEvaluations == 1)
    }

    "exclude API, service, and backend construction from the disabled app-graph boundary" in {
      val handle = buildHandle(disabledModule)

      assert(!handle.isEnabled)
      assert(handle.toOption.isEmpty)
    }

    "assemble the API, service, and fake backend only through an explicit enabled app-graph boundary" in {
      val handle = buildHandle(enabledModule)

      assert(handle.isEnabled)
      assert(handle.toOption.nonEmpty)
    }
  }

  private def disabledModule: ModuleDef = new ModuleDef {
    make[BeautySearchProductionInclusionHandle[IO]].fromValue(BeautySearchProductionInclusionHandle.disabled[IO])
  }

  private def enabledModule: ModuleDef = new ModuleDef {
    make[BeautySearchTapirEndpoints].fromValue(BeautySearchTapirEndpoints)
    make[TapirHttpSupport[IO]].from(new TapirHttpSupport[IO])
    make[BeautySearchSpec].fromValue(BeautySearchSpecV1.spec)
    make[BeautySearchIntentParser].from((spec: BeautySearchSpec) => new BeautySearchIntentParser(spec))
    make[BeautySearchBackend[IO]].from {
      new FailIfCalledBeautySearchBackend
    }
    make[BeautySearchService[IO]].from {
      (parser: BeautySearchIntentParser, backend: BeautySearchBackend[IO]) =>
        new BeautySearchService.Impl[IO](parser, backend)
    }
    make[BeautySearchApi[IO]].from {
      (
        service: BeautySearchService[IO],
        endpoints: BeautySearchTapirEndpoints,
        tapirHttpSupport: TapirHttpSupport[IO],
      ) =>
        new BeautySearchApi[IO](service, endpoints, tapirHttpSupport)
    }
    make[BeautySearchProductionInclusionHandle[IO]].from {
      (api: BeautySearchApi[IO]) =>
        BeautySearchProductionInclusionHandle.buildIfEnabled[IO](
          activation = BeautySearchProductionInclusionActivation.Enabled,
          api = api,
        )
    }
  }

  private def buildHandle(module: ModuleDef): BeautySearchProductionInclusionHandle[IO] = {
    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[BeautySearchProductionInclusionHandle[IO]],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[BeautySearchProductionInclusionHandle[IO]]
  }

  private def buildApi(service: BeautySearchService[IO]): BeautySearchApi[IO] =
    new BeautySearchApi[IO](service, BeautySearchTapirEndpoints, new TapirHttpSupport[IO])

  private final class RecordingBeautySearchService(
    response: BeautySearchResponse
  ) extends BeautySearchService[IO] {
    override def search(input: UserSearchInput): IO[QueryFailure, BeautySearchResponse] = ZIO.succeed(response)
  }

  private final class FailIfCalledBeautySearchBackend extends BeautySearchBackend[IO] {
    override def search(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, BeautySearchResponse] =
      ZIO.suspendSucceed(
        ZIO.fail(QueryFailure.domain(s"FailIfCalledBeautySearchBackend.search was unexpectedly called: $input"))
      )
  }

  private val emptySearchResponse: BeautySearchResponse =
    BeautySearchResponse(
      variantCarousel = Nil,
      providerCarousel = Nil,
      serviceIntentCarousel = Nil,
      facets = Nil,
      inferredFilters = Nil,
    )
}
