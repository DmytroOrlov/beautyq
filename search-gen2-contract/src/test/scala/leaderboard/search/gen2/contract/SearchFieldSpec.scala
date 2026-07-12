package leaderboard.search.gen2.contract

import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.util.UUID

final class SearchFieldSpec extends AnyWordSpec {

  private final case class ToyDocument(
    id: UUID,
    name: String,
    count: Int,
    viewCount: Long,
    price: BigDecimal,
    active: Boolean,
    publishedAt: Instant,
    location: GeoPoint,
    tags: Map[String, String],
  )

  private final case class ProductCode(value: String)

  private val productCodeCodec: SearchValueCodec[ProductCode] =
    summon[SearchValueCodec[String]].imap(SearchValueTypeId("product-code"))(
      raw => Right(ProductCode(raw)),
      wrapped => wrapped.value,
    )

  private val doc = ToyDocument(
    id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
    name = "alice",
    count = 3,
    viewCount = 4294967296L,
    price = BigDecimal("12.50"),
    active = true,
    publishedAt = Instant.parse("2026-01-01T00:00:00Z"),
    location = GeoPoint(BigDecimal("1.5"), BigDecimal("2.5")),
    tags = Map("color" -> "red"),
  )

  "field" should {
    "derive the path from a direct field selector" in {
      val handle = field[ToyDocument, String]("displayName", _.name).keyword
      assert(handle.path == FieldPath("name"))
    }

    "keep the explicit field id independent from the derived path" in {
      val handle = field[ToyDocument, String]("displayName", _.name).keyword
      assert(handle.id == FieldId("displayName"))
      assert(handle.id.value != handle.path.value)
    }

    "use required extraction and extract the direct value" in {
      val handle = field[ToyDocument, String]("displayName", _.name).keyword
      assert(handle.required)
      assert(handle.extract(doc) == Some("alice"))
    }
  }

  "computedField" should {
    "preserve the explicit dynamic path" in {
      val handle = computedField[ToyDocument, String](id = "color", path = "tags.color")(_.tags.get("color")).keyword
      assert(handle.path == FieldPath("tags.color"))
    }

    "use optional extraction" in {
      val handle = computedField[ToyDocument, String](id = "color", path = "tags.color")(_.tags.get("color")).keyword
      assert(!handle.required)
      assert(handle.extract(doc) == Some("red"))
      assert(handle.extract(doc.copy(tags = Map.empty)) == None)
    }
  }

  "selector derivation" should {
    "accept a direct field selector at compile time" in {
      assertCompiles("""field[ToyDocument, String]("displayName", _.name).keyword""")
    }

    "reject a nested field selector at compile time" in {
      assertDoesNotCompile("""field[ToyDocument, BigDecimal]("locationLat", _.location.lat).decimal""")
    }

    "reject a computed method selector at compile time" in {
      assertDoesNotCompile("""field[ToyDocument, String]("upper", _.name.toUpperCase).text""")
    }

    "reject a computed expression selector at compile time" in {
      assertDoesNotCompile("""field[ToyDocument, String]("combined", (d: ToyDocument) => d.name + "x").keyword""")
    }

    "reject a direct parameterless computed method selector at compile time" in {
      assertDoesNotCompile(
        """{
          |  final case class ComputedMethodProduct(id: java.util.UUID, name: String) {
          |    def normalizedName: String = name.trim.toLowerCase
          |  }
          |  field[ComputedMethodProduct, String]("normalizedName", _.normalizedName).text
          |}""".stripMargin
      )
    }

    "reject a computed method selector with the exact accepted diagnostic message" in {
      val errors = scala.compiletime.testing.typeCheckErrors(
        """{
          |  final case class DiagnosticProduct(id: java.util.UUID, name: String) {
          |    def normalizedName: String = name.trim.toLowerCase
          |  }
          |  field[DiagnosticProduct, String]("normalizedName", _.normalizedName).text
          |}""".stripMargin
      )

      assert(errors.nonEmpty)
      assert(
        errors.exists(
          _.message.contains(
            "Search field selector must be a direct field selection like _.fieldName; use computedField for nested, computed, or dynamic paths"
          )
        )
      )
    }
  }

  "kind builder" should {
    "reject text for a non-String value at compile time" in {
      assertDoesNotCompile("""field[ToyDocument, Int]("count", _.count).text""")
    }

    "require an exact Int for integer" in {
      assertCompiles("""field[ToyDocument, Int]("count", _.count).integer""")
      assertDoesNotCompile("""field[ToyDocument, String]("displayName", _.name).integer""")
    }

    "require an exact Long for long" in {
      assertCompiles("""field[ToyDocument, Long]("viewCount", _.viewCount).long""")
      assertDoesNotCompile("""field[ToyDocument, String]("displayName", _.name).long""")
    }

    "require an exact BigDecimal for decimal" in {
      assertCompiles("""field[ToyDocument, BigDecimal]("price", _.price).decimal""")
      assertDoesNotCompile("""field[ToyDocument, String]("displayName", _.name).decimal""")
    }

    "require an exact Instant for dateTime" in {
      assertCompiles("""field[ToyDocument, Instant]("publishedAt", _.publishedAt).dateTime""")
      assertDoesNotCompile("""field[ToyDocument, String]("displayName", _.name).dateTime""")
    }

    "require an exact Boolean for boolean" in {
      assertCompiles("""field[ToyDocument, Boolean]("active", _.active).boolean""")
      assertDoesNotCompile("""field[ToyDocument, String]("displayName", _.name).boolean""")
    }

    "require an exact GeoPoint for geoPoint" in {
      assertCompiles("""field[ToyDocument, GeoPoint]("location", _.location).geoPoint""")
      assertDoesNotCompile("""field[ToyDocument, String]("displayName", _.name).geoPoint""")
    }

    "work as keyword for a codec-mapped wrapper type" in {
      val handle =
        computedField[ToyDocument, ProductCode](id = "productCode", path = "productCode")(_ => Some(ProductCode("PC-1")))(using productCodeCodec).keyword

      assert(handle.kind == SearchFieldKind.Keyword)
      assert(handle.codec.typeId == SearchValueTypeId("product-code"))
    }
  }

  "fluent capability methods" should {
    "be immutable and additive" in {
      val base             = field[ToyDocument, String]("displayName", _.name).keyword
      val searchableField  = base.searchable

      assert(!base.capabilities.searchable)
      assert(searchableField.capabilities.searchable)
      assert(base.capabilities == FieldCapabilities())
    }

    "contain exactly the requested capability tags" in {
      val handle =
        field[ToyDocument, String]("displayName", _.name).keyword
          .withSemantic("product.name")
          .searchable
          .filterable(FilterOperator.Equal, FilterOperator.In)
          .facetable(FacetMode.Terms)
          .sortable(SortMode.Value)
          .groupable(GroupMode.Terms)
          .payloadEligible

      assert(handle.semantic == Some(FieldSemantic("product.name")))
      assert(handle.capabilities.searchable)
      assert(handle.capabilities.filterOperators == Set(FilterOperator.Equal, FilterOperator.In))
      assert(handle.capabilities.facetModes == Set(FacetMode.Terms))
      assert(handle.capabilities.sortModes == Set(SortMode.Value))
      assert(handle.capabilities.groupModes == Set(GroupMode.Terms))
      assert(handle.capabilities.payloadEligible)
    }
  }
}
