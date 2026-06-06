package leaderboard.search

import io.circe.Json
import io.circe.syntax.*
import leaderboard.search.document.VariantSearchDocument
import leaderboard.search.dsl.SearchGeoPoint
import leaderboard.search.elasticsearch.ElasticsearchSearchResponseInterpreter
import leaderboard.search.lexical.LexicalDocumentHit
import leaderboard.search.dsl.BeautySearchSpecV1
import org.scalatest.wordspec.AnyWordSpec

import java.util.UUID

final class ElasticsearchSearchResponseInterpreterSpec extends AnyWordSpec {
  "ElasticsearchSearchResponseInterpreter.documentHits" should {
    "decode ES hits into generic lexical hits and preserve matched query names as lexical diagnostics" in {
      val document = variantDocument()
      val response = searchResponseJson(
        esHitJson(
          source = document.asJson,
          score = Some(4.25),
          matchedQueries = List("serviceName", "attributeText"),
        )
      )

      val result = ElasticsearchSearchResponseInterpreter.documentHits(response)

      assert(result == Right(List(LexicalDocumentHit(document.variantId, 4.25, List("serviceName", "attributeText")))))
    }

    "default missing score to 0.0 and missing _matched_queries to Nil" in {
      val document = variantDocument(variantId = uuid("00000000-0000-0000-0000-000000000322"))
      val response = searchResponseJson(
        esHitJson(
          source = document.asJson,
          score = None,
          matchedQueries = Nil,
        )
      )

      val result = ElasticsearchSearchResponseInterpreter.documentHits(response)

      assert(result == Right(List(LexicalDocumentHit(document.variantId, 0.0d, Nil))))
    }

    "fail when _source is missing or invalid" in {
      val missingSourceResponse = searchResponseJson(
        Json.obj(
          "_score" -> Json.fromDoubleOrNull(1.0),
          "_matched_queries" -> Json.arr(Json.fromString("serviceName")),
        )
      )
      val invalidSourceResponse = searchResponseJson(
        Json.obj(
          "_score" -> Json.fromDoubleOrNull(1.0),
          "_matched_queries" -> Json.arr(Json.fromString("serviceName")),
          "_source" -> Json.fromString("not-a-document"),
        )
      )

      val missingSource = ElasticsearchSearchResponseInterpreter.documentHits(missingSourceResponse)
      val invalidSource = ElasticsearchSearchResponseInterpreter.documentHits(invalidSourceResponse)

      assert(missingSource.isLeft)
      assert(invalidSource.isLeft)
    }
  }

  "ElasticsearchSearchResponseInterpreter.interpret" should {
    "keep the existing BeautyQ response projection behavior unchanged" in {
      val document = variantDocument(variantId = uuid("00000000-0000-0000-0000-000000000399"))
      val response = searchResponseJson(
        esHitJson(
          source = document.asJson,
          score = Some(3.5),
          matchedQueries = List("serviceName"),
        )
      )

      val interpreted = ElasticsearchSearchResponseInterpreter.interpret(spec, input, intent, response)

      interpreted match {
        case Right(projected) =>
          assert(projected.variantCarousel.headOption.exists(_.variantId == document.variantId))
        case Left(error) =>
          fail(error.message)
      }
    }
  }

  private val spec = BeautySearchSpecV1.spec
  private val input = UserSearchInput("synthetic manicure query", None, None, limit = 10)
  private val intent = ParsedSearchIntent(
    originalQuery = input.query,
    normalizedTokens = List("synthetic", "manicure", "query"),
    explicitConstraints = Nil,
    softBoosts = Nil,
    remainingText = input.query,
  )

  private def searchResponseJson(hitJson: Json): Json =
    Json.obj(
      "hits" -> Json.obj(
        "hits" -> Json.arr(hitJson)
      )
    )

  private def esHitJson(source: Json, score: Option[Double], matchedQueries: List[String]): Json = {
    val fields = List.newBuilder[(String, Json)]
    score.foreach(value => fields += "_score" -> Json.fromDoubleOrNull(value))
    if (matchedQueries.nonEmpty) {
      fields += "_matched_queries" -> Json.arr(matchedQueries.map(Json.fromString): _*)
    }
    fields += "_source" -> source
    Json.obj(fields.result(): _*)
  }

  private def variantDocument(variantId: UUID = uuid("00000000-0000-0000-0000-000000000321")): VariantSearchDocument =
    VariantSearchDocument(
      variantId = variantId,
      masterServiceOfferId = uuid("00000000-0000-0000-0000-000000000222"),
      masterLocationId = uuid("00000000-0000-0000-0000-000000000333"),
      masterId = uuid("00000000-0000-0000-0000-000000000444"),
      serviceId = uuid("00000000-0000-0000-0000-000000000555"),
      categoryId = uuid("00000000-0000-0000-0000-000000000666"),
      serviceName = "Manicure",
      categoryName = "Nails",
      masterName = "Beauty Master",
      locationName = "Central Studio",
      address = "Main street 1",
      location = SearchGeoPoint(lat = BigDecimal("52.5200"), lon = BigDecimal("13.4050")),
      lat = BigDecimal("52.5200"),
      lon = BigDecimal("13.4050"),
      priceFrom = BigDecimal("25.00"),
      priceTo = BigDecimal("40.00"),
      durationMin = 45,
      enumAttributes = Map("coverage" -> "gel"),
      booleanAttributes = Map("with_removal" -> true),
      intAttributes = Map("nails_count" -> 10),
      bigDecimalAttributes = Map("rating" -> BigDecimal("4.8")),
      allText = "manicure nails beauty master central studio",
      serviceText = "manicure nails",
      attributeText = "coverage gel with removal",
      providerText = "beauty master central studio",
      locationText = "central studio main street 1 nails",
    )

  private def uuid(value: String): UUID = UUID.fromString(value)
}
