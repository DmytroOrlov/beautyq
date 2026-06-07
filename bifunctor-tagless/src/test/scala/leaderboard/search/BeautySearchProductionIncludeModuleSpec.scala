package leaderboard.search

import distage.{Injector, ModuleDef}
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, BeautySearchProductionIncludedApis, BeautySearchProductionInclusionActivation, BeautySearchProductionInclusionHandle}
import leaderboard.http.tapir.{BeautySearchTapirEndpoints, TapirHttpSupport}
import leaderboard.model.QueryFailure
import leaderboard.search.dsl.{BeautySearchSpec, BeautySearchSpecV1}
import leaderboard.search.parser.BeautySearchIntentParser
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz.*
import zio.{IO, ZIO}

final class BeautySearchProductionIncludeModuleSpec extends AnyWordSpec {
  "BeautySearchProductionIncludedApis" should {
    "be empty when built from disabled handle" in {
      val handle = BeautySearchProductionInclusionHandle.disabled[IO]
      val included = BeautySearchProductionIncludedApis.fromHandle(handle)

      assert(included.apis.isEmpty)
    }

    "have exactly one API when built from enabled handle" in {
      val counters = ConstructionCounters()
      val fakeApi = buildFakeApi(counters)
      val handle = BeautySearchProductionInclusionHandle(Some(fakeApi))
      val included = BeautySearchProductionIncludedApis.fromHandle(handle)

      assert(included.apis.size == 1)
      assert(included.apis.collect { case _: BeautySearchApi[IO] => true }.headOption.isDefined)
    }

    "be empty and not evaluate thunk when buildIfEnabled is Disabled" in {
      val counters = ConstructionCounters()
      val handle = BeautySearchProductionInclusionHandle.buildIfEnabled[IO](
        activation = BeautySearchProductionInclusionActivation.Disabled,
        api = {
          counters.apiConstructed += 1
          buildFakeApi(counters)
        },
      )

      val included = BeautySearchProductionIncludedApis.fromHandle(handle)

      assert(included.apis.isEmpty)
      assert(counters.apiConstructed == 0)
    }

    "evaluate thunk exactly once when buildIfEnabled is Enabled" in {
      val counters = ConstructionCounters()
      val handle = BeautySearchProductionInclusionHandle.buildIfEnabled[IO](
        activation = BeautySearchProductionInclusionActivation.Enabled,
        api = {
          counters.apiConstructed += 1
          buildFakeApi(counters)
        },
      )

      val included = BeautySearchProductionIncludedApis.fromHandle(handle)

      assert(included.apis.size == 1)
      assert(counters.apiConstructed == 1)
    }

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
    make[BeautySearchProductionIncludedApis[IO]].from {
      (handle: BeautySearchProductionInclusionHandle[IO]) =>
        BeautySearchProductionIncludedApis.fromHandle(handle)
    }
  }

  private def enabledIncludeModule(counters: ConstructionCounters): ModuleDef = new ModuleDef {
    include(enabledStackModule(counters))
    make[BeautySearchProductionIncludedApis[IO]].from {
      (handle: BeautySearchProductionInclusionHandle[IO]) =>
        BeautySearchProductionIncludedApis.fromHandle(handle)
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

  private def buildIncludedApis(module: ModuleDef): BeautySearchProductionIncludedApis[IO] = {
    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[BeautySearchProductionIncludedApis[IO]],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[BeautySearchProductionIncludedApis[IO]]
  }

  private def buildFakeApi(counters: ConstructionCounters): BeautySearchApi[IO] = {
    val backend = new RecordingBeautySearchBackend(counters, emptySearchResponse)
    val service = new BeautySearchService.Impl[IO](
      new BeautySearchIntentParser(BeautySearchSpecV1.spec),
      backend,
    )
    new BeautySearchApi[IO](service, BeautySearchTapirEndpoints, new TapirHttpSupport[IO])
  }

  private final case class RecordingBeautySearchBackend(
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
