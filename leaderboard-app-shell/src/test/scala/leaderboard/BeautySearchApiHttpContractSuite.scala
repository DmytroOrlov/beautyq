package leaderboard

import io.circe.parser.parse
import izumi.distage.testkit.scalatest.{AssertZIO, SpecZIO}
import leaderboard.api.BeautySearchApi
import leaderboard.http.tapir.BeautySearchTapirEndpoints
import leaderboard.model.{MasterId, MasterLocationId, MasterServiceOfferId, MasterServiceOfferVariantId, QueryFailure, ServiceId}
import leaderboard.model.Category.CategoryId
import leaderboard.search.*
import leaderboard.search.beautyq.contract.BeautyQSearchResponseProvenanceContract.{ExecutionModes, JsonFields, ResultOrigins}
import leaderboard.search.dsl.SearchConstraint
import org.http4s.{Request, Status}
import zio.interop.catz.*
import zio.{IO, Ref, Task, UIO, ZIO}

class BeautySearchApiHttpContractSuite extends SpecZIO with AssertZIO with HttpContractTestSupport {
  private val tapirEndpoints = BeautySearchTapirEndpoints

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
        _        <- assertIO(json.hcursor.downField(JsonFields.ExecutionMode).as[String].toOption.contains(ExecutionModes.EsOnly))
        _        <- assertIO(json.hcursor.downField("qdrantSupplement").downField("status").as[String].toOption.contains("not_used"))
        _        <- assertIO(json.hcursor.downField("variantCarousel").downArray.downField(JsonFields.ResultOrigin).as[String].toOption.contains(ResultOrigins.EsBaseline))
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
          response.body === """{"variantCarousel":[],"providerCarousel":[],"serviceIntentCarousel":[],"facets":[],"inferredFilters":[],"executionMode":"es_only","qdrantSupplement":{"status":"not_used","policy":"none","appendedVariantIds":[],"contribution":"none"}}"""
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

    "return Tapir default bad-input response and do not call the fake service for malformed JSON" in {
      assertDefaultBadRequestWithoutServiceCall(postJson("/beauty-search", """{"query":"broken""""))
    }

    "return Tapir default bad-input response and do not call the fake service for an empty body" in {
      assertDefaultBadRequestWithoutServiceCall(
        Request[Task](method = org.http4s.Method.POST, uri = org.http4s.Uri.unsafeFromString("/beauty-search"))
          .putHeaders(org.http4s.headers.`Content-Type`(org.http4s.MediaType.application.json))
      )
    }

    "return Tapir default bad-input response and do not call the fake service for a wrong limit type" in {
      assertDefaultBadRequestWithoutServiceCall(
        postJson("/beauty-search", """{"query":"маникюр","limit":"bad"}""")
      )
    }

    "return Tapir default bad-input response and do not call the fake service for a missing query" in {
      assertDefaultBadRequestWithoutServiceCall(
        postJson("/beauty-search", """{"userLat":53.58,"userLon":10.08,"limit":3}""")
      )
    }

    "return structured invalid_query and do not call the fake service for an empty query" in {
      assertStructuredBadRequestWithoutServiceCall(
        postJson("/beauty-search", """{"query":"","userLat":53.58,"userLon":10.08,"limit":3}"""),
        BeautySearchRequestContract.InvalidQuery,
      )
    }

    "return structured invalid_query and do not call the fake service for a whitespace-only query" in {
      assertStructuredBadRequestWithoutServiceCall(
        postJson("/beauty-search", """{"query":"   ","userLat":53.58,"userLon":10.08,"limit":3}"""),
        BeautySearchRequestContract.InvalidQuery,
      )
    }

    "return structured invalid_limit and do not call the fake service for zero limit" in {
      assertStructuredBadRequestWithoutServiceCall(
        postJson("/beauty-search", """{"query":"nails","userLat":53.58,"userLon":10.08,"limit":0}"""),
        BeautySearchRequestContract.InvalidLimit,
      )
    }

    "return structured invalid_limit and do not call the fake service for negative limit" in {
      assertStructuredBadRequestWithoutServiceCall(
        postJson("/beauty-search", """{"query":"nails","userLat":53.58,"userLon":10.08,"limit":-5}"""),
        BeautySearchRequestContract.InvalidLimit,
      )
    }

    "return structured invalid_limit and do not call the fake service for limit above the carousel maximum" in {
      val invalidLimit = BeautySearchRequestContract.MaxLimit + 1

      assertStructuredBadRequestWithoutServiceCall(
        postJson("/beauty-search", s"""{"query":"nails","userLat":53.58,"userLon":10.08,"limit":$invalidLimit}"""),
        BeautySearchRequestContract.InvalidLimit,
      )
    }

    "return structured invalid_latitude and do not call the fake service for out-of-range latitude" in {
      assertStructuredBadRequestWithoutServiceCall(
        postJson("/beauty-search", """{"query":"nails","userLat":90.1,"userLon":10.08,"limit":3}"""),
        BeautySearchRequestContract.InvalidLatitude,
      )
    }

    "return structured invalid_longitude and do not call the fake service for out-of-range longitude" in {
      assertStructuredBadRequestWithoutServiceCall(
        postJson("/beauty-search", """{"query":"nails","userLat":53.58,"userLon":180.1,"limit":3}"""),
        BeautySearchRequestContract.InvalidLongitude,
      )
    }
  }

  private def app(
    state: BeautySearchApiContractState
  ) =
    new BeautySearchApi[IO](state.service, tapirEndpoints).http.orNotFound

  private def assertDefaultBadRequestWithoutServiceCall(request: Request[Task]): Task[Unit] =
    for {
      state    <- BeautySearchApiContractState.make(Right(emptySearchResponse))
      response <- observe(app(state), request)
      inputs   <- state.inputs
      _        <- assertIO(response.status === Status.BadRequest)
      _        <- assertIO(inputs.isEmpty)
    } yield ()

  private def assertStructuredBadRequestWithoutServiceCall(
    request: Request[Task],
    error: BeautySearchRequestContract.SemanticError,
  ): Task[Unit] =
    for {
      state    <- BeautySearchApiContractState.make(Right(emptySearchResponse))
      response <- observe(app(state), request)
      inputs   <- state.inputs
      _        <- assertIO(response.status === Status.BadRequest)
      _        <- assertIO(response.body === s"""{"code":"${error.code}","message":"${error.message}"}""")
      _        <- assertIO(inputs.isEmpty)
    } yield ()

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
          variantId = MasterServiceOfferVariantId.fromString("11111111-1111-1111-1111-111111111111"),
          masterServiceOfferId = MasterServiceOfferId.fromString("22222222-2222-2222-2222-222222222222"),
          masterLocationId = MasterLocationId.fromString("33333333-3333-3333-3333-333333333333"),
          masterId = MasterId.fromString("44444444-4444-4444-4444-444444444444"),
          serviceId = ServiceId.fromString("55555555-5555-5555-5555-555555555555"),
          categoryId = CategoryId.fromString("66666666-6666-6666-6666-666666666666"),
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
          masterId = MasterId.fromString("44444444-4444-4444-4444-444444444444"),
          masterName = "Анна",
          masterLocationId = MasterLocationId.fromString("33333333-3333-3333-3333-333333333333"),
          locationName = "Wandsbek studio",
          address = "Wandsbeker Marktstrasse 1",
          matchingVariantCount = 1,
          sampleMatchingVariantIds = List(MasterServiceOfferVariantId.fromString("11111111-1111-1111-1111-111111111111")),
          bestScore = 9.5d,
          distanceKm = Some(BigDecimal("1.2")),
        )
      ),
      serviceIntentCarousel = List(
        ServiceIntentSearchResult(
          serviceId = ServiceId.fromString("55555555-5555-5555-5555-555555555555"),
          serviceName = "Маникюр",
          categoryId = CategoryId.fromString("66666666-6666-6666-6666-666666666666"),
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
