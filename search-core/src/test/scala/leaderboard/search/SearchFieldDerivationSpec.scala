package leaderboard.search

import leaderboard.search.dsl.*
import org.scalatest.wordspec.AnyWordSpec

final class SearchFieldDerivationSpec extends AnyWordSpec {

  private final case class ToyDoc(
    id: Int,
    name: String,
    description: String,
    count: Int,
    price: BigDecimal,
    active: Boolean,
    location: SearchGeoPoint,
  )

  private val doc = ToyDoc(
    id = 7,
    name = "alice",
    description = "long form text",
    count = 3,
    price = BigDecimal("12.50"),
    active = true,
    location = SearchGeoPoint(BigDecimal("1.5"), BigDecimal("2.5")),
  )

  "SearchField.keyword" should {
    "derive the path from the selector and extract a keyword value" in {
      val field: SearchField[ToyDoc] = SearchField.keyword(_.name)

      assert(field.path == "name")
      assert(field.kind == SearchFieldKind.Keyword)
      assert(field.extract(doc) == Some(SearchValue.Keyword("alice")))
    }
  }

  "SearchField.keywordRendered" should {
    "derive the path from the selector and extract a rendered keyword value" in {
      val field: SearchField[ToyDoc] = SearchField.keywordRendered(_.id, _.toString)

      assert(field.path == "id")
      assert(field.kind == SearchFieldKind.Keyword)
      assert(field.extract(doc) == Some(SearchValue.Keyword("7")))
    }
  }

  "SearchField.text" should {
    "derive the path from the selector and extract a text value" in {
      val field: SearchField[ToyDoc] = SearchField.text(_.description)

      assert(field.path == "description")
      assert(field.kind == SearchFieldKind.Text)
      assert(field.extract(doc) == Some(SearchValue.Text("long form text")))
    }
  }

  "SearchField.integer" should {
    "derive the path from the selector and extract an integer value" in {
      val field: SearchField[ToyDoc] = SearchField.integer(_.count)

      assert(field.path == "count")
      assert(field.kind == SearchFieldKind.Integer)
      assert(field.extract(doc) == Some(SearchValue.Integer(3)))
    }
  }

  "SearchField.decimal" should {
    "derive the path from the selector and extract a decimal value" in {
      val field: SearchField[ToyDoc] = SearchField.decimal(_.price)

      assert(field.path == "price")
      assert(field.kind == SearchFieldKind.Decimal)
      assert(field.extract(doc) == Some(SearchValue.Decimal(BigDecimal("12.50"))))
    }
  }

  "SearchField.boolean" should {
    "derive the path from the selector and extract a boolean value" in {
      val field: SearchField[ToyDoc] = SearchField.boolean(_.active)

      assert(field.path == "active")
      assert(field.kind == SearchFieldKind.Boolean)
      assert(field.extract(doc) == Some(SearchValue.Boolean(true)))
    }
  }

  "SearchField.geoPoint" should {
    "derive the path from the selector and extract a geo point value" in {
      val field: SearchField[ToyDoc] = SearchField.geoPoint(_.location)

      assert(field.path == "location")
      assert(field.kind == SearchFieldKind.GeoPoint)
      assert(field.extract(doc) == Some(SearchValue.GeoPoint(SearchGeoPoint(BigDecimal("1.5"), BigDecimal("2.5")))))
    }
  }

  "SearchField selector derivation" should {
    "accept a direct field selector at compile time" in {
      assertCompiles("SearchField.keyword[ToyDoc](_.name)")
    }

    "reject a nested field selector at compile time" in {
      assertDoesNotCompile("SearchField.decimal[ToyDoc](_.location.lat)")
    }

    "reject a computed method selector at compile time" in {
      assertDoesNotCompile("SearchField.keyword[ToyDoc](_.name.toUpperCase)")
    }

    "reject a rendered-via-toString selector misused as a direct keyword at compile time" in {
      assertDoesNotCompile("SearchField.keyword[ToyDoc](_.id.toString)")
    }

    "reject a computed expression selector at compile time" in {
      assertDoesNotCompile("""SearchField.keyword[ToyDoc](doc => doc.name + "x")""")
    }
  }

  "SearchField helpers" should {
    "preserve semantic, flags, boost, and analyzer arguments exactly" in {
      val field: SearchField[ToyDoc] =
        SearchField.text(
          _.description,
          semantic = Some(SearchFieldSemantic.AllText),
          searchable = true,
          filterable = true,
          facetable = true,
          sortable = true,
          boost = 3.5,
          analyzer = Some("custom"),
        )

      assert(field.semantic.contains(SearchFieldSemantic.AllText))
      assert(field.searchable)
      assert(field.filterable)
      assert(field.facetable)
      assert(field.sortable)
      assert(field.boost == 3.5)
      assert(field.analyzer.contains("custom"))
    }
  }
}
