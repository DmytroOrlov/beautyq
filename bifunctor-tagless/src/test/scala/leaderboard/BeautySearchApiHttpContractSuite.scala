package leaderboard

import io.circe.parser.parse
import izumi.distage.testkit.scalatest.{AssertZIO, SpecZIO}
import leaderboard.http.HttpApiFailure
import leaderboard.http.tapir.{BeautySearchTapirEndpoints, TapirHttpSupport}
import leaderboard.model.QueryFailure
import leaderboard.search.*
import leaderboard.search.dsl.SearchConstraint
import org.http4s.Status
import sttp.tapir.server.ServerEndpoint
import sttp.capabilities.fs2.Fs2Streams
import zio.interop.catz.*
import zio.{IO, Ref, UIO, ZIO}

import java.util.UUID

class BeautySearchApiHttpContractSuite extends SpecZIO with AssertZIO with HttpContractTestSupport {
  private val tapirEndpoints  = BeautySearchTapirEndpoints
  private val tapirHttpSupport = new TapirHttpSupport[IO]

  "Beauty search route contract skeleton" should {
    "accept a valid POST request body and pass the UserSearchInput contract to the fake service" in {
      val responseBody = emptySearchResponse
      val requestBody  = """{"query":"маникюр рядом","userLat":53.58,"userLon":10.08,"limit":3}"""

      for {
        state    <- BeautySearchApiContractState.make(Right(responseBody))
        response <- observe(app(state), postJson("/beauty-search", requestBody))
        inputs   <- state.inputs
        _        <- assertIO(response.status === Status.Ok)
        _        <- assertIO(inputs === Vector(UserSearchInput("маникюр рядом", Some(BigDecimal("53.58")), Some(BigDecimal("10.08")), 3)))
      } yield ()
    }

    "return the expected response shape from a fake service" in {
      for {
        state    <- BeautySearchApiContractState.make(Right(nonEmptySearchResponse))
        response <- observe(app(state), postJson("/beauty-search", """{"query":"ресницы","userLat":null,"userLon":null,"limit":10}"""))
        json     <- ZIO.fromEither(parse(response.body).left.map(error => new RuntimeException(error.message)))
        _        <- assertIO(response.status === Status.Ok)
        _        <- assertIO(json.hcursor.downField("variantCarousel").focus.exists(_.asArray.exists(_.size == 1)))
        _        <- assertIO(json.hcursor.downField("providerCarousel").focus.exists(_.asArray.exists(_.size == 1)))
        _        <- assertIO(json.hcursor.downField("serviceIntentCarousel").focus.exists(_.asArray.exists(_.size == 1)))
        _        <- assertIO(json.hcursor.downField("facets").focus.exists(_.asArray.exists(_.size == 1)))
        _        <- assertIO(json.hcursor.downField("inferredFilters").focus.exists(_.asArray.exists(_.size == 1)))
        _        <- assertIO(json.hcursor.downField("variantCarousel").downArray.downField("variantId").as[String].toOption.contains("11111111-1111-1111-1111-111111111111"))
        _        <- assertIO(json.hcursor.downField("providerCarousel").downArray.downField("masterLocationId").as[String].toOption.contains("33333333-3333-3333-3333-333333333333"))
        _        <- assertIO(json.hcursor.downField("serviceIntentCarousel").downArray.downField("serviceId").as[String].toOption.contains("55555555-5555-5555-5555-555555555555"))
        _        <- assertIO(json.hcursor.downField("inferredFilters").downArray.downField("constraint").downField("type").as[String].toOption.contains("near_user"))
      } yield ()
    }

    "return an explicit empty result response with arrays instead of nulls" in {
      for {
        state    <- BeautySearchApiContractState.make(Right(emptySearchResponse))
        response <- observe(app(state), postJson("/beauty-search", """{"query":"нет результатов","userLat":null,"userLon":null,"limit":10}"""))
        _        <- assertIO(response.status === Status.Ok)
        _        <- assertIO(
          response.body === """{"variantCarousel":[],"providerCarousel":[],"serviceIntentCarousel":[],"facets":[],"inferredFilters":[]}"""
        )
      } yield ()
    }

    "return the existing server failure contract when the fake backend fails" in {
      for {
        state <- BeautySearchApiContractState.make(
          Left(QueryFailure.fromThrowable("beauty-search-contract", new RuntimeException("search-boom")))
        )
        response <- observe(app(state), postJson("/beauty-search", """{"query":"fail","userLat":null,"userLon":null,"limit":10}"""))
        _        <- assertIO(response.status === Status.InternalServerError)
        _        <- assertIO(response.body === "")
      } yield ()
    }

    "pass limit through and preserve fake service carousel sizes without endpoint trimming" in {
      val responseBody = nonEmptySearchResponse.copy(
        variantCarousel = List(nonEmptySearchResponse.variantCarousel.head, nonEmptySearchResponse.variantCarousel.head.copy(score = 8.0d))
      )

      for {
        state    <- BeautySearchApiContractState.make(Right(responseBody))
        response <- observe(app(state), postJson("/beauty-search", """{"query":"лимит","userLat":null,"userLon":null,"limit":1}"""))
        inputs   <- state.inputs
        json     <- ZIO.fromEither(parse(response.body).left.map(error => new RuntimeException(error.message)))
        _        <- assertIO(response.status === Status.Ok)
        _        <- assertIO(inputs.map(_.limit) === Vector(1))
        _        <- assertIO(json.hcursor.downField("variantCarousel").focus.exists(_.asArray.exists(_.size == 2)))
      } yield ()
    }

    "return current malformed-json semantics and do not call the fake service" in {
      for {
        state    <- BeautySearchApiContractState.make(Right(emptySearchResponse))
        response <- observe(app(state), postJson("/beauty-search", """{"query":"broken""""))
        inputs   <- state.inputs
        _        <- assertIO(response.status === Status.InternalServerError)
        _        <- assertIO(response.body === "")
        _        <- assertIO(inputs.isEmpty)
      } yield ()
    }
  }

  private def app(
    state: BeautySearchApiContractState
  ) =
    tapirHttpSupport.toRoutes(List(searchEndpoint(state))).orNotFound

  private def searchEndpoint(
    state: BeautySearchApiContractState
  ): ServerEndpoint[Fs2Streams[IO[Throwable, _]], IO[Throwable, _]] =
    tapirEndpoints.searchBeauty.serverLogic[IO[Throwable, _]] {
      input =>
        HttpApiFailure.fromQueryEffect(state.service.search(input))
    }

  private val emptySearchResponse: BeautySearchResponse =
    BeautySearchResponse(
      variantCarousel = Nil,
      providerCarousel = Nil,
      serviceIntentCarousel = Nil,
      facets = Nil,
      inferredFilters = Nil,
    )

  private val nonEmptySearchResponse: BeautySearchResponse =
    BeautySearchResponse(
      variantCarousel = List(
        VariantSearchResult(
          variantId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
          masterServiceOfferId = UUID.fromString("22222222-2222-2222-2222-222222222222"),
          masterLocationId = UUID.fromString("33333333-3333-3333-3333-333333333333"),
          masterId = UUID.fromString("44444444-4444-4444-4444-444444444444"),
          serviceId = UUID.fromString("55555555-5555-5555-5555-555555555555"),
          categoryId = UUID.fromString("66666666-6666-6666-6666-666666666666"),
          serviceName = "Маникюр",
          categoryName = "Ногти",
          masterName = "Анна",
          locationName = "Wandsbek studio",
          address = "Wandsbeker Marktstrasse 1",
          lat = BigDecimal("53.58"),
          lon = BigDecimal("10.08"),
          priceFrom = BigDecimal("30"),
          priceTo = BigDecimal("40"),
          durationMin = 60,
          enumAttributes = Map("nail_polish_type" -> "gel"),
          booleanAttributes = Map("with_removal" -> true),
          intAttributes = Map("session_count" -> 1),
          bigDecimalAttributes = Map("deposit_amount" -> BigDecimal("10")),
          score = 9.5d,
          distanceKm = Some(BigDecimal("1.2")),
        )
      ),
      providerCarousel = List(
        ProviderSearchResult(
          masterId = UUID.fromString("44444444-4444-4444-4444-444444444444"),
          masterName = "Анна",
          masterLocationId = UUID.fromString("33333333-3333-3333-3333-333333333333"),
          locationName = "Wandsbek studio",
          address = "Wandsbeker Marktstrasse 1",
          matchingVariantCount = 1,
          sampleMatchingVariantIds = List(UUID.fromString("11111111-1111-1111-1111-111111111111")),
          bestScore = 9.5d,
          distanceKm = Some(BigDecimal("1.2")),
        )
      ),
      serviceIntentCarousel = List(
        ServiceIntentSearchResult(
          serviceId = UUID.fromString("55555555-5555-5555-5555-555555555555"),
          serviceName = "Маникюр",
          categoryId = UUID.fromString("66666666-6666-6666-6666-666666666666"),
          categoryName = "Ногти",
          matchingVariantCount = 1,
          bestScore = 9.5d,
        )
      ),
      facets = List(BeautySearchFacet("serviceName", List(BeautySearchFacetValue("Маникюр", 1)))),
      inferredFilters = List(BeautySearchAppliedFilter(SearchConstraint.NearUser, explicit = false)),
    )
}

class BeautySearchApiContractState private (
  private val inputsRef: Ref[Vector[UserSearchInput]],
  private val resultRef: Ref[Either[QueryFailure, BeautySearchResponse]],
) {
  val service: BeautySearchService[IO] = new BeautySearchService[IO] {
    override def search(input: UserSearchInput): IO[QueryFailure, BeautySearchResponse] =
      inputsRef.update(_ :+ input) *> resultRef.get.flatMap(ZIO.fromEither(_))
  }

  def inputs: UIO[Vector[UserSearchInput]] =
    inputsRef.get
}

object BeautySearchApiContractState {
  def make(result: Either[QueryFailure, BeautySearchResponse]): UIO[BeautySearchApiContractState] =
    for {
      inputs    <- Ref.make(Vector.empty[UserSearchInput])
      resultRef <- Ref.make(result)
    } yield new BeautySearchApiContractState(inputs, resultRef)
}
