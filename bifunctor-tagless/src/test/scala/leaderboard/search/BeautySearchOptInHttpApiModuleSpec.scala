package leaderboard.search

import distage.{Injector, ModuleDef}
import izumi.distage.model.definition.{Activation, LocatorPrivacy}
import izumi.distage.model.plan.Roots
import leaderboard.api.{BeautySearchApi, HttpApi}
import leaderboard.http.tapir.TapirHttpSupport
import leaderboard.model.QueryFailure
import leaderboard.plugins.BeautySearchPluginModules
import org.scalatest.wordspec.AnyWordSpec
import zio.interop.catz.*
import zio.{IO, ZIO}

final class BeautySearchOptInHttpApiModuleSpec extends AnyWordSpec {
  "BeautySearchPluginModules.api" should {
    "contribute BeautySearchApi to the real HttpApi weak set only when explicitly included" in {
      val service = new RecordingBeautySearchService(emptySearchResponse)
      val probe   = buildProbe(service)
      val apis    = probe.allHttpApis

      assert(apis.size == 1)
      assert(apis.collect { case api: BeautySearchApi[IO] => api }.size == 1)
      assert(service.calls.isEmpty)
    }
  }

  private def buildProbe(service: RecordingBeautySearchService): HttpApiSetProbe = {
    val module = new ModuleDef {
      include(BeautySearchPluginModules.api[IO])
      make[TapirHttpSupport[IO]].from(new TapirHttpSupport[IO])
      make[BeautySearchService[IO]].fromValue(service)
      make[HttpApiSetProbe].from {
        (
          beautySearchApi: BeautySearchApi[IO],
          allHttpApis: Set[HttpApi[IO]],
        ) =>
          val _ = beautySearchApi
          HttpApiSetProbe(allHttpApis)
      }
    }

    val locator = Injector().produce(
      bindings = module,
      roots = Roots.target[HttpApiSetProbe],
      activation = Activation.empty,
      locatorPrivacy = LocatorPrivacy.PublicByDefault,
    ).unsafeGet()

    locator.get[HttpApiSetProbe]
  }

  private final case class HttpApiSetProbe(
    allHttpApis: Set[HttpApi[IO]]
  )

  private final class RecordingBeautySearchService(
    response: BeautySearchResponse
  ) extends BeautySearchService[IO] {
    private var recordedCalls: Vector[UserSearchInput] = Vector.empty

    def calls: Vector[UserSearchInput] = recordedCalls

    override def search(input: UserSearchInput): IO[QueryFailure, BeautySearchResponse] = {
      recordedCalls = recordedCalls :+ input
      ZIO.succeed(response)
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
}
