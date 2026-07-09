package leaderboard.search

import leaderboard.model.{MasterServiceOfferVariantId, QueryFailure}
import leaderboard.search.dsl.{BeautySearchSpecV1, SearchConstraint}
import leaderboard.search.document.{BeautyQSearchCatalogSnapshot, BeautyQVariantSearchDocumentMaterialization, VariantSearchDocument}
import leaderboard.search.elasticsearch.{BeautyQElasticsearchInterpreterAdapter, ElasticsearchSearchInput, ElasticsearchSearchRequestInterpreter}
import leaderboard.search.inmemory.InMemorySearchBackend
import leaderboard.search.parser.BeautySearchIntentParser
import leaderboard.seed.BeautyQSeedLoader
import org.scalatest.wordspec.AnyWordSpec
import zio.{IO, Runtime, Unsafe}

import java.util.UUID

/** Budget/range queries are baseline + Elasticsearch-owned hard constraints. This spec proves the
  * [[SearchConstraint.PriceRange]] upper bound becomes an ES bool `filter` range clause over
  * the BeautyQ price field, and that the pure in-memory rollback/regression backend drops
  * above-threshold documents. No Qdrant / semantic path is involved.
  */
final class ElasticsearchBudgetRangeRequestSpec extends AnyWordSpec {

  private val parser = new BeautySearchIntentParser(BeautySearchSpecV1.spec)

  private val documents: List[VariantSearchDocument] = {
    val seedData = orFail("load BeautyQ seed", new BeautyQSeedLoader.ResourceLoader().load())
    val snapshot = BeautyQSearchCatalogSnapshot(
      categories                 = seedData.categories,
      services                   = seedData.services,
      serviceVariantSchemas      = seedData.serviceVariantSchemas,
      masters                    = seedData.masters,
      masterLocations            = seedData.masterLocations,
      masterServiceOffers        = seedData.masterServiceOffers,
      masterServiceOfferVariants = seedData.masterServiceOfferVariants,
    )
    orFail("build variant documents", BeautyQVariantSearchDocumentMaterialization.project(snapshot))
  }

  "ElasticsearchSearchRequestInterpreter budget range" should {

    "emit a bool filter range clause on priceFrom with lte and no gte for an upper-bound PriceRange" in {
      val request = orFail(
        "build ES request",
        ElasticsearchSearchRequestInterpreter.request(
          BeautySearchSpecV1.spec.runtimeSpec(Map.empty),
          elasticsearchInput(
            UserSearchInput("маникюр under 50", None, None),
            ParsedSearchIntent(
            originalQuery = "маникюр under 50",
            normalizedTokens = List("маникюр"),
            explicitConstraints = List(
              SearchConstraint.ServiceAny(Set("Маникюр")),
              SearchConstraint.PriceRange(None, Some(BigDecimal(50))),
            ),
            softBoosts = Nil,
            remainingText = "маникюр",
          ),
          ),
        ),
      )

      val priceFrom = request.hcursor
        .downField("query")
        .downField("bool")
        .downField("filter")
        .values
        .toList
        .flatten
        .map(_.hcursor)
        .find(_.downField("range").downField("priceFrom").focus.nonEmpty)
        .getOrElse(fail("expected a priceFrom range filter clause"))
        .downField("range")
        .downField("priceFrom")

      val lte = priceFrom.get[BigDecimal]("lte").getOrElse(fail("budget filter must set an lte upper bound"))
      assert(lte == BigDecimal(50))
      assert(priceFrom.downField("gte").focus.isEmpty, "upper-bound budget must not emit a gte lower bound")

      // The text query stays a separate must clause; it is not merged into the range filter.
      val mustQuery = request.hcursor
        .downField("query")
        .downField("bool")
        .downField("must")
        .downN(0)
        .downField("multi_match")
        .get[String]("query")
        .getOrElse(fail("text query must remain a separate multi_match must clause"))
      assert(mustQuery == "маникюр")
    }
  }

  "Pure in-memory rollback/regression backend budget behavior" should {

    "exclude above-threshold variants and keep below-threshold variants for `маникюр under 50`" in {
      val manicure = documents
        .find(_.enumAttributes.get("nail_service_type").contains("manicure"))
        .getOrElse(cancel("seed has no manicure variant to exercise the budget filter"))

      val belowThreshold = manicure.copy(
        variantId = MasterServiceOfferVariantId(UUID.fromString("00000000-0000-0000-0000-0000000000a1")),
        priceFrom = BigDecimal(39),
        priceTo = BigDecimal(39),
      )
      val aboveThreshold = manicure.copy(
        variantId = MasterServiceOfferVariantId(UUID.fromString("00000000-0000-0000-0000-0000000000a2")),
        priceFrom = BigDecimal(75),
        priceTo = BigDecimal(75),
      )

      val backend = new InMemorySearchBackend[IO](BeautySearchSpecV1.spec, List(belowThreshold, aboveThreshold))
      val service = new BeautySearchService.Impl[IO](parser, backend)

      val returnedIds = runIO(service.search(UserSearchInput("маникюр under 50", None, None)))
        .variantCarousel
        .map(_.variantId)
        .toSet

      assert(returnedIds.contains(belowThreshold.variantId))
      assert(!returnedIds.contains(aboveThreshold.variantId))
    }
  }

  private def orFail[A](context: String, result: Either[QueryFailure, A]): A =
    result match {
      case Right(value) => value
      case Left(error)  => fail(s"$context: ${error.message}")
    }

  private def runIO[A](effect: IO[QueryFailure, A]): A =
    Unsafe.unsafe(implicit unsafe => Runtime.default.unsafe.run(effect).getOrThrowFiberFailure())

  private def elasticsearchInput(input: UserSearchInput, intent: ParsedSearchIntent): ElasticsearchSearchInput[VariantSearchDocument] =
    orFail("build ES input", BeautyQElasticsearchInterpreterAdapter.input(BeautySearchSpecV1.spec, input, intent))
}
