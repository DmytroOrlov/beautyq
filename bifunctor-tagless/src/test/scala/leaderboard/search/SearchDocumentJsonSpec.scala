package leaderboard.search

import io.circe.Json
import io.circe.syntax.*
import leaderboard.model.QueryFailure
import leaderboard.search.document.SearchDocumentJson
import leaderboard.search.dsl.{SearchDocumentSpec, SearchField, SearchFieldKind, SearchGeoPoint, SearchValue}
import org.scalatest.wordspec.AnyWordSpec

final class SearchDocumentJsonSpec extends AnyWordSpec {
  "SearchDocumentJson" should {
    "build source JSON with nested objects for dotted field paths" in {
      val document = TestDocument(
        id = "doc-1",
        title = "Manicure",
        tag = "nails",
        count = 3,
        price = BigDecimal("12.50"),
        active = true,
        location = SearchGeoPoint(BigDecimal("52.5200"), BigDecimal("13.4050")),
        optional = Some("present"),
      )

      val json = SearchDocumentJson.sourceJson(documentSpec, document)

      assert(json == Json.obj(
        "title" -> Json.fromString("Manicure"),
        "tag" -> Json.fromString("nails"),
        "count" -> Json.fromInt(3),
        "price" -> BigDecimal("12.50").asJson,
        "active" -> Json.True,
        "location" -> Json.obj(
          "lat" -> BigDecimal("52.5200").asJson,
          "lon" -> BigDecimal("13.4050").asJson,
        ),
        "enumAttributes" -> Json.obj(
          "coverage" -> Json.fromString("gel"),
        ),
        "optional" -> Json.fromString("present"),
      ))
    }

    "encode every SearchValue shape with the existing JSON convention" in {
      val geoPoint = SearchGeoPoint(BigDecimal("52.5200"), BigDecimal("13.4050"))

      assert(SearchDocumentJson.encodeValue(SearchValue.Text("text")) == Json.fromString("text"))
      assert(SearchDocumentJson.encodeValue(SearchValue.Keyword("keyword")) == Json.fromString("keyword"))
      assert(SearchDocumentJson.encodeValue(SearchValue.Integer(7)) == Json.fromInt(7))
      assert(SearchDocumentJson.encodeValue(SearchValue.Decimal(BigDecimal("9.75"))) == BigDecimal("9.75").asJson)
      assert(SearchDocumentJson.encodeValue(SearchValue.Boolean(true)) == Json.True)
      assert(SearchDocumentJson.encodeValue(SearchValue.GeoPoint(geoPoint)) == Json.obj(
        "lat" -> BigDecimal("52.5200").asJson,
        "lon" -> BigDecimal("13.4050").asJson,
      ))
    }

    "build flat payload JSON only for explicitly requested field paths" in {
      val document = testDocument(optional = Some("payload-value"))

      val result = SearchDocumentJson.payload(documentSpec, List("title", "enumAttributes.coverage"), document)

      assert(result == Right(Map(
        "title" -> Json.fromString("Manicure"),
        "enumAttributes.coverage" -> Json.fromString("gel"),
      )))
    }

    "fail payload generation when a requested field path is absent" in {
      val result = SearchDocumentJson.payload(documentSpec, List("title", "missing"), testDocument())

      result match {
        case Left(QueryFailure.DomainFailure(message)) =>
          assert(message == "Search field 'missing' is not defined for index 'test-document'")
        case other =>
          fail(s"Expected missing field domain failure, got $other")
      }
    }

    "omit requested payload fields when the field extractor returns None" in {
      val result = SearchDocumentJson.payload(documentSpec, List("title", "optional"), testDocument(optional = None))

      assert(result == Right(Map("title" -> Json.fromString("Manicure"))))
    }
  }

  private final case class TestDocument(
    id: String,
    title: String,
    tag: String,
    count: Int,
    price: BigDecimal,
    active: Boolean,
    location: SearchGeoPoint,
    optional: Option[String],
  )

  private def testDocument(optional: Option[String] = Some("present")): TestDocument =
    TestDocument(
      id = "doc-1",
      title = "Manicure",
      tag = "nails",
      count = 3,
      price = BigDecimal("12.50"),
      active = true,
      location = SearchGeoPoint(BigDecimal("52.5200"), BigDecimal("13.4050")),
      optional = optional,
    )

  private val documentSpec: SearchDocumentSpec[TestDocument] =
    SearchDocumentSpec(
      indexName = "test-document",
      id = _.id,
      fields = List(
        SearchField(
          path = "title",
          kind = SearchFieldKind.Text,
          extract = document => Some(SearchValue.Text(document.title)),
        ),
        SearchField(
          path = "tag",
          kind = SearchFieldKind.Keyword,
          extract = document => Some(SearchValue.Keyword(document.tag)),
        ),
        SearchField(
          path = "count",
          kind = SearchFieldKind.Integer,
          extract = document => Some(SearchValue.Integer(document.count)),
        ),
        SearchField(
          path = "price",
          kind = SearchFieldKind.Decimal,
          extract = document => Some(SearchValue.Decimal(document.price)),
        ),
        SearchField(
          path = "active",
          kind = SearchFieldKind.Boolean,
          extract = document => Some(SearchValue.Boolean(document.active)),
        ),
        SearchField(
          path = "location",
          kind = SearchFieldKind.GeoPoint,
          extract = document => Some(SearchValue.GeoPoint(document.location)),
        ),
        SearchField(
          path = "enumAttributes.coverage",
          kind = SearchFieldKind.Keyword,
          extract = _ => Some(SearchValue.Keyword("gel")),
        ),
        SearchField(
          path = "optional",
          kind = SearchFieldKind.Keyword,
          extract = document => document.optional.map(SearchValue.Keyword.apply),
        ),
      ),
    )
}
