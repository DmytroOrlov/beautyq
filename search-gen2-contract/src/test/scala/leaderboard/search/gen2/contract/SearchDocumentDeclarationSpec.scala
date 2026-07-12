package leaderboard.search.gen2.contract

import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.util.UUID

final class SearchDocumentDeclarationSpec extends AnyWordSpec {

  private final case class ToyDocument(
    id: UUID,
    name: String,
    enumAttributes: Map[String, String],
  )

  private val idField =
    field[ToyDocument, UUID]("productId", _.id)
      .keyword
      .withSemantic("product.id")
      .filterable(FilterOperator.Equal, FilterOperator.In)
      .sortable(SortMode.Value)
      .payloadEligible

  private val nameField =
    field[ToyDocument, String]("displayName", _.name)
      .text
      .withSemantic("product.name")
      .searchable

  private val colorField =
    computedField[ToyDocument, String](
      id = "color",
      path = "enumAttributes.color",
    )(_.enumAttributes.get("color"))
      .keyword
      .filterable(FilterOperator.Equal, FilterOperator.In)
      .facetable(FacetMode.Terms)
      .payloadEligible

  private val declaration: SearchDocumentDeclaration[ToyDocument, UUID] =
    searchDocument[ToyDocument]("products")
      .id(idField)
      .field(nameField)
      .field(colorField)
      .build
      .getOrElse(fail("expected a valid declaration"))

  private def capabilityViolationsOf(candidate: SearchField[ToyDocument, ?]): Vector[CapabilityViolation] =
    searchDocument[ToyDocument]("products").id(idField).field(candidate).build match {
      case Left(errors) =>
        errors.toVector.flatMap {
          case IncompatibleFieldCapabilities(_, _, violations) => violations
          case _                                                => Vector.empty
        }
      case Right(_) => Vector.empty
    }

  "the builder" should {
    "preserve ordinary field declaration order" in {
      assert(declaration.fields == Vector(nameField, colorField))
    }

    "store the identity separately from the ordinary fields" in {
      assert(declaration.identity == idField)
      assert(!declaration.fields.contains(idField))
    }

    "expose identity as the first element of allFields" in {
      assert(declaration.allFields == Vector(idField, nameField, colorField))
    }

    "reject a field for another document type at compile time" in {
      assertDoesNotCompile(
        """{
          |  final case class OtherDocument(value: String)
          |  searchDocument[ToyDocument]("products").id(idField).field(field[OtherDocument, String]("value", _.value).keyword)
          |}""".stripMargin
      )
    }

    "produce the same result via the builder and via direct validate given the same handles" in {
      val viaBuilder  = searchDocument[ToyDocument]("products").id(idField).field(nameField).field(colorField).build
      val viaValidate = SearchDocumentDeclaration.validate(SearchDocumentId("products"), idField, Vector(nameField, colorField))
      assert(viaBuilder == viaValidate)
    }
  }

  "identity validation" should {
    "reject an identity field with optional/computed extraction" in {
      val computedIdentity = computedField[ToyDocument, UUID](id = "productId", path = "id")(doc => Some(doc.id)).keyword
      val result            = SearchDocumentDeclaration.validate(SearchDocumentId("products"), computedIdentity, Vector(nameField))

      result match {
        case Left(errors) => assert(errors.toVector.contains(IdentityFieldMustBeRequired(FieldId("productId"))))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "reject an identity field repeated through .field(identity)" in {
      val result = searchDocument[ToyDocument]("products").id(idField).field(idField).field(nameField).build

      result match {
        case Left(errors) => assert(errors.toVector.contains(IdentityFieldRepeated(idField.id)))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }
  }

  "handle and duplicate detection" should {
    "reject an ordinary handle added twice" in {
      val result = searchDocument[ToyDocument]("products").id(idField).field(nameField).field(nameField).build

      result match {
        case Left(errors) => assert(errors.toVector.contains(RepeatedFieldHandle(nameField.id, 0, 1)))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "reject distinct fields with a duplicate field id" in {
      val duplicateIdField = computedField[ToyDocument, String](id = "displayName", path = "otherPath")(_ => Some("dup")).text
      val result            = searchDocument[ToyDocument]("products").id(idField).field(nameField).field(duplicateIdField).build

      result match {
        case Left(errors) => assert(errors.toVector.contains(DuplicateFieldId(FieldId("displayName"))))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "reject distinct fields with a duplicate field path" in {
      val duplicatePathField = computedField[ToyDocument, String](id = "otherId", path = "name")(_ => Some("dup")).text
      val result              = searchDocument[ToyDocument]("products").id(idField).field(nameField).field(duplicatePathField).build

      result match {
        case Left(errors) => assert(errors.toVector.contains(DuplicateFieldPath(FieldPath("name"))))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }
  }

  "document-level validation" should {
    "reject a document with no ordinary fields" in {
      val result = searchDocument[ToyDocument]("products").id(idField).build

      result match {
        case Left(errors) => assert(errors.toVector.contains(EmptySearchDocument(SearchDocumentId("products"))))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "reject an invalid document id" in {
      val result = searchDocument[ToyDocument]("").id(idField).field(nameField).build

      result match {
        case Left(errors) => assert(errors.toVector.contains(InvalidSearchDocumentId("")))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }
  }

  "per-field validation" should {
    "reject an invalid field id" in {
      val badIdField = computedField[ToyDocument, String](id = "bad id", path = "customPath")(_ => Some("x")).text
      val result      = searchDocument[ToyDocument]("products").id(idField).field(badIdField).build

      result match {
        case Left(errors) => assert(errors.toVector.contains(InvalidFieldId(FieldId("bad id"))))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "reject an invalid field path" in {
      val badPathField = computedField[ToyDocument, String](id = "customId", path = "bad path")(_ => Some("x")).text
      val result        = searchDocument[ToyDocument]("products").id(idField).field(badPathField).build

      result match {
        case Left(errors) => assert(errors.toVector.contains(InvalidFieldPath(FieldId("customId"), FieldPath("bad path"))))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "reject an invalid field semantic" in {
      val badSemanticField =
        computedField[ToyDocument, String](id = "customId", path = "customPath")(_ => Some("x")).text.withSemantic("bad semantic")
      val result = searchDocument[ToyDocument]("products").id(idField).field(badSemanticField).build

      result match {
        case Left(errors) => assert(errors.toVector.contains(InvalidFieldSemantic(FieldId("customId"), FieldSemantic("bad semantic"))))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "reject an invalid codec logical type id" in {
      val badTypeIdCodec: SearchValueCodec[String] =
        summon[SearchValueCodec[String]].imap(SearchValueTypeId("bad type"))(raw => Right(raw), identity)
      val badTypeField =
        computedField[ToyDocument, String](id = "customId", path = "customPath")(_ => Some("x"))(using badTypeIdCodec).text
      val result = searchDocument[ToyDocument]("products").id(idField).field(badTypeField).build

      result match {
        case Left(errors) => assert(errors.toVector.contains(InvalidSearchValueTypeId(FieldId("customId"), SearchValueTypeId("bad type"))))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }
  }

  "capability compatibility" should {
    "reject a searchable keyword field" in {
      val candidate = computedField[ToyDocument, String](id = "bad", path = "bad")(_ => Some("x")).keyword.searchable
      assert(capabilityViolationsOf(candidate).contains(CapabilityViolation.SearchableRequiresText))
    }

    "reject range filtering on a text field" in {
      val candidate = computedField[ToyDocument, String](id = "bad", path = "bad")(_ => Some("x")).text.filterable(FilterOperator.Range)
      assert(capabilityViolationsOf(candidate).contains(CapabilityViolation.UnsupportedFilterOperator(FilterOperator.Range)))
    }

    "reject geo-distance filtering on a decimal field" in {
      val candidate =
        computedField[ToyDocument, BigDecimal](id = "bad", path = "bad")(_ => Some(BigDecimal(1))).decimal.filterable(FilterOperator.GeoDistance)
      assert(capabilityViolationsOf(candidate).contains(CapabilityViolation.UnsupportedFilterOperator(FilterOperator.GeoDistance)))
    }

    "reject a terms facet on a geo point field" in {
      val candidate =
        computedField[ToyDocument, GeoPoint](id = "bad", path = "bad")(_ => Some(GeoPoint(BigDecimal(1), BigDecimal(1)))).geoPoint
          .facetable(FacetMode.Terms)
      assert(capabilityViolationsOf(candidate).contains(CapabilityViolation.UnsupportedFacetMode(FacetMode.Terms)))
    }

    "reject a value sort on a text field" in {
      val candidate = computedField[ToyDocument, String](id = "bad", path = "bad")(_ => Some("x")).text.sortable(SortMode.Value)
      assert(capabilityViolationsOf(candidate).contains(CapabilityViolation.UnsupportedSortMode(SortMode.Value)))
    }

    "reject a distance sort on a keyword field" in {
      val candidate = computedField[ToyDocument, String](id = "bad", path = "bad")(_ => Some("x")).keyword.sortable(SortMode.Distance)
      assert(capabilityViolationsOf(candidate).contains(CapabilityViolation.UnsupportedSortMode(SortMode.Distance)))
    }

    "reject terms grouping on a decimal field" in {
      val candidate =
        computedField[ToyDocument, BigDecimal](id = "bad", path = "bad")(_ => Some(BigDecimal(1))).decimal.groupable(GroupMode.Terms)
      assert(capabilityViolationsOf(candidate).contains(CapabilityViolation.UnsupportedGroupMode(GroupMode.Terms)))
    }

    "reject terms grouping on a long field" in {
      val candidate =
        computedField[ToyDocument, Long](id = "bad", path = "bad")(_ => Some(1L)).long.groupable(GroupMode.Terms)
      assert(capabilityViolationsOf(candidate).contains(CapabilityViolation.UnsupportedGroupMode(GroupMode.Terms)))
    }

    "reject a terms facet on a date-time field" in {
      val candidate =
        computedField[ToyDocument, Instant](id = "bad", path = "bad")(_ => Some(Instant.parse("2026-01-01T00:00:00Z")))
          .dateTime
          .facetable(FacetMode.Terms)
      assert(capabilityViolationsOf(candidate).contains(CapabilityViolation.UnsupportedFacetMode(FacetMode.Terms)))
    }

    "accept range filtering, range faceting, and value sort on a date-time field" in {
      val candidate =
        computedField[ToyDocument, Instant](id = "publishedAt", path = "publishedAt")(_ => Some(Instant.parse("2026-01-01T00:00:00Z")))
          .dateTime
          .filterable(FilterOperator.Range)
          .facetable(FacetMode.Range)
          .sortable(SortMode.Value)
      assert(capabilityViolationsOf(candidate).isEmpty)
    }
  }

  "deterministic error accumulation" should {
    "accumulate all independent validation errors in the documented order" in {
      val badIdentity          = computedField[ToyDocument, UUID](id = "productId", path = "id")(doc => Some(doc.id)).keyword
      val invalidIdField        = computedField[ToyDocument, String](id = "bad id", path = "customPathA")(_ => Some("x")).keyword
      val repeatedHandleField   = computedField[ToyDocument, String](id = "repeatedHandle", path = "repeatedHandlePath")(_ => Some("y")).keyword
      val dupIdFieldA           = computedField[ToyDocument, String](id = "sharedId", path = "pathA")(_ => Some("a")).keyword
      val dupIdFieldB           = computedField[ToyDocument, String](id = "sharedId", path = "pathB")(_ => Some("b")).keyword
      val dupPathFieldA         = computedField[ToyDocument, String](id = "idX", path = "sharedPath")(_ => Some("x")).keyword
      val dupPathFieldB         = computedField[ToyDocument, String](id = "idY", path = "sharedPath")(_ => Some("y")).keyword

      val fields = Vector(
        invalidIdField,
        badIdentity,
        repeatedHandleField,
        repeatedHandleField,
        dupIdFieldA,
        dupIdFieldB,
        dupPathFieldA,
        dupPathFieldB,
      )

      val result = SearchDocumentDeclaration.validate(SearchDocumentId(""), badIdentity, fields)

      result match {
        case Left(errors) =>
          assert(
            errors.toVector == Vector(
              InvalidSearchDocumentId(""),
              IdentityFieldMustBeRequired(FieldId("productId")),
              IdentityFieldRepeated(FieldId("productId")),
              InvalidFieldId(FieldId("bad id")),
              RepeatedFieldHandle(FieldId("repeatedHandle"), 2, 3),
              DuplicateFieldId(FieldId("sharedId")),
              DuplicateFieldPath(FieldPath("sharedPath")),
            )
          )
        case Right(value) =>
          fail(s"expected validation to fail, got: $value")
      }
    }
  }

  "structural view and rendering" should {
    "expose a structure that matches direct structural assertions" in {
      val structure = declaration.structure

      assert(structure.id == SearchDocumentId("products"))
      assert(
        structure.identity == SearchFieldStructure(
          id = FieldId("productId"),
          path = FieldPath("id"),
          kind = SearchFieldKind.Keyword,
          valueType = SearchValueTypeId("uuid"),
          semantic = Some(FieldSemantic("product.id")),
          presence = FieldPresence.Required,
          capabilities = idField.capabilities,
        )
      )
      assert(structure.fields.map(_.id) == Vector(FieldId("displayName"), FieldId("color")))
      assert(structure.fields.map(_.presence) == Vector(FieldPresence.Required, FieldPresence.Optional))
    }

    "render the exact golden structural text" in {
      val expected =
        """document products
          |├── identity productId
          |│   ├── path: id
          |│   ├── kind: keyword
          |│   ├── valueType: uuid
          |│   ├── semantic: product.id
          |│   ├── presence: required
          |│   └── capabilities: searchable=false; filter=[equal,in]; facet=[]; sort=[value]; group=[]; payload=true
          |└── fields
          |    ├── [0] displayName
          |    │   ├── path: name
          |    │   ├── kind: text
          |    │   ├── valueType: string
          |    │   ├── semantic: product.name
          |    │   ├── presence: required
          |    │   └── capabilities: searchable=true; filter=[]; facet=[]; sort=[]; group=[]; payload=false
          |    └── [1] color
          |        ├── path: enumAttributes.color
          |        ├── kind: keyword
          |        ├── valueType: string
          |        ├── semantic: -
          |        ├── presence: optional
          |        └── capabilities: searchable=false; filter=[equal,in]; facet=[terms]; sort=[]; group=[]; payload=true""".stripMargin

      assert(declaration.renderStructure == expected)
    }

    "render capabilities in enum declaration order regardless of insertion order" in {
      val reorderedField =
        computedField[ToyDocument, Int](id = "count", path = "count")(_ => Some(1)).integer
          .filterable(FilterOperator.Range, FilterOperator.Equal, FilterOperator.In)
          .facetable(FacetMode.Range, FacetMode.Terms)

      val document =
        searchDocument[ToyDocument]("products").id(idField).field(reorderedField).build.getOrElse(fail("expected a valid declaration"))

      assert(document.renderStructure.contains("filter=[equal,in,range]"))
      assert(document.renderStructure.contains("facet=[terms,range]"))
    }
  }
}
