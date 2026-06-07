package leaderboard.search

import distage.{Injector, ModuleDef}
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, BeautySearchProductionInclusionActivation, BeautySearchProductionInclusionHandle, HttpApi}
import leaderboard.http.tapir.{BeautySearchTapirEndpoints, TapirHttpSupport}
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.{BeautySearchSpec, BeautySearchSpecV1}
import leaderboard.search.parser.BeautySearchIntentParser
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz.*
import zio.{IO, ZIO}

final class BeautySearchProductionIncludeModuleSpec extends AnyWordSpec {
  "Beauty search production include module shape" should {
    "contribute no Beauty search API to the test-local HttpApi aggregation result when Disabled" in {
      val counters = ConstructionCounters()
      val included = buildIncludedApis(disabledIncludeModule)

      assert(included.apis.isEmpty)
      assert(included.apis.collect { case api: BeautySearchApi[IO] => api }.isEmpty)
      assert(counters.apiConstructed == 0)
      assert(counters.serviceConstructed == 0)
      assert(counters.backendConstructed == 0)
      assert(counters.backendCalled == 0)
    }

    "contribute BeautySearchApi to the test-local HttpApi aggregation result only when explicitly Enabled" in {
      val counters = ConstructionCounters()
      val included = buildIncludedApis(enabledIncludeModule(counters))
      val beautyApis = included.apis.collect { case api: BeautySearchApi[IO] => api }

      assert(included.apis.size == 1)
      assert(beautyApis.size == 1)
      assert(counters.apiConstructed == 1)
      assert(counters.serviceConstructed == 1)
      assert(counters.backendConstructed == 1)
      assert(counters.backendCalled == 0)
    }
  }

  private def disabledHandleModule: ModuleDef = new ModuleDef {
    make[BeautySearchProductionInclusionHandle[IO]].fromValue(BeautySearchProductionInclusionHandle.disabled[IO])
  }

  private def disabledIncludeModule: ModuleDef = new ModuleDef {
    include(disabledHandleModule)
    include(testLocalIncludeModule)
  }

  private def enabledIncludeModule(counters: ConstructionCounters): ModuleDef = new ModuleDef {
    include(enabledStackModule(counters))
    include(testLocalIncludeModule)
  }

  private def testLocalIncludeModule: ModuleDef = new ModuleDef {
    make[BeautySearchIncludedApis[IO]].from {
      (handle: BeautySearchProductionInclusionHandle[IO]) =>
        BeautySearchIncludedApis(handle.toOption.toList)
    }
  }

  private def enabledStackModule(counters: ConstructionCounters): ModuleDef = new ModuleDef {
    make[BeautySearchTapirEndpoints].fromValue(BeautySearchTapirEndpoints)
    make[TapirHttpSupport[IO]].from(new TapirHttpSupport[IO])
    make[BeautySearchSpec].fromValue(BeautySearchSpecV1.spec)
    make[BeautySearchIntentParser].from((spec: BeautySearchSpec) => new BeautySearchIntentParser(spec))
    make[BeautySearchBackend[IO]].from {
      counters.backendConstructed += 1
      new RecordingBeautySearchBackend(counters, emptySearchResponse)
    }
    make[BeautySearchService[IO]].from {
      (parser: BeautySearchIntentParser, backend: BeautySearchBackend[IO]) =>
        counters.serviceConstructed += 1
        new BeautySearchService.Impl[IO](parser, backend)
    }
    make[BeautySearchApi[IO]].from {
      (
        service: BeautySearchService[IO],
        endpoints: BeautySearchTapirEndpoints,
        tapirHttpSupport: TapirHttpSupport[IO],
      ) =>
        counters.apiConstructed += 1
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

  private def buildIncludedApis(module: ModuleDef): BeautySearchIncludedApis[IO] = {
    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[BeautySearchIncludedApis[IO]],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[BeautySearchIncludedApis[IO]]
  }

  private final case class BeautySearchIncludedApis[F[+_, +_]](
    apis: List[HttpApi[F]]
  )

  private final class RecordingBeautySearchBackend(
    counters: ConstructionCounters,
    response: BeautySearchResponse,
  ) extends BeautySearchBackend[IO] {
    override def search(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, BeautySearchResponse] = {
      counters.backendCalled += 1
      ZIO.succeed(response)
    }
  }

  private final case class ConstructionCounters(
    var apiConstructed: Int = 0,
    var serviceConstructed: Int = 0,
    var backendConstructed: Int = 0,
    var backendCalled: Int = 0,
  )

  private val emptySearchResponse: BeautySearchResponse =
    BeautySearchResponse(
      variantCarousel = Nil,
      providerCarousel = Nil,
      serviceIntentCarousel = Nil,
      facets = Nil,
      inferredFilters = Nil,
    )
}
