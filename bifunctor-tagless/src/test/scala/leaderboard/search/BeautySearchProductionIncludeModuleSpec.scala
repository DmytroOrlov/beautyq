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
      val fakeApi = buildFakeApi()
      val handle = BeautySearchProductionInclusionHandle(Some(fakeApi))
      val included = BeautySearchProductionIncludedApis.fromHandle(handle)

      assert(included.apis.size == 1)
      assert(included.apis.collect { case _: BeautySearchApi[IO] => true }.headOption.isDefined)
    }

    "be empty and not evaluate thunk when buildIfEnabled is Disabled" in {
      val handle = BeautySearchProductionInclusionHandle.buildIfEnabled[IO](
        activation = BeautySearchProductionInclusionActivation.Disabled,
        api = throw new RuntimeException("disabled buildIfEnabled must not evaluate API thunk"),
      )

      val included = BeautySearchProductionIncludedApis.fromHandle(handle)

      assert(included.apis.isEmpty)
    }

    "evaluate thunk exactly once when buildIfEnabled is Enabled" in {
      var apiConstructed = 0
      val handle = BeautySearchProductionInclusionHandle.buildIfEnabled[IO](
        activation = BeautySearchProductionInclusionActivation.Enabled,
        api = {
          apiConstructed += 1
          buildFakeApi()
        },
      )

      val included = BeautySearchProductionIncludedApis.fromHandle(handle)

      assert(included.apis.size == 1)
      assert(apiConstructed == 1)
    }

    "contribute no Beauty search API to the test-local HttpApi aggregation result when Disabled" in {
      val included = buildIncludedApis(disabledIncludeModule)

      assert(included.apis.isEmpty)
      assert(included.apis.collect { case api: BeautySearchApi[IO] => api }.isEmpty)
    }

    "contribute BeautySearchApi to the test-local HttpApi aggregation result only when explicitly Enabled" in {
      val included = buildIncludedApis(enabledIncludeModule)
      val beautyApis = included.apis.collect { case api: BeautySearchApi[IO] => api }

      assert(included.apis.size == 1)
      assert(beautyApis.size == 1)
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

  private def enabledIncludeModule: ModuleDef = new ModuleDef {
    include(enabledStackModule)
    make[BeautySearchProductionIncludedApis[IO]].from {
      (handle: BeautySearchProductionInclusionHandle[IO]) =>
        BeautySearchProductionIncludedApis.fromHandle(handle)
    }
  }

  private def enabledStackModule: ModuleDef = new ModuleDef {
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

  private def buildIncludedApis(module: ModuleDef): BeautySearchProductionIncludedApis[IO] = {
    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[BeautySearchProductionIncludedApis[IO]],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[BeautySearchProductionIncludedApis[IO]]
  }

  private def buildFakeApi(): BeautySearchApi[IO] = {
    val backend = new FailIfCalledBeautySearchBackend
    val service = new BeautySearchService.Impl[IO](
      new BeautySearchIntentParser(BeautySearchSpecV1.spec),
      backend,
    )
    new BeautySearchApi[IO](service, BeautySearchTapirEndpoints, new TapirHttpSupport[IO])
  }

  private final class FailIfCalledBeautySearchBackend extends BeautySearchBackend[IO] {
    override def search(input: UserSearchInput, intent: ParsedSearchIntent): IO[QueryFailure, BeautySearchResponse] =
      ZIO.suspendSucceed(
        ZIO.fail(QueryFailure.domain(s"FailIfCalledBeautySearchBackend.search was unexpectedly called: $input"))
      )
  }

 }
