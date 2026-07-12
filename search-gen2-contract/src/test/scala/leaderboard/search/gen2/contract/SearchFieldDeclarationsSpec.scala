package leaderboard.search.gen2.contract

import leaderboard.model.{CanonicalStringValue, UuidBackedId}
import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.util.UUID

// Fixture types live in the companion object, a separate enclosing scope from the spec class below:
// an opaque type is transparent to its own underlying representation only *within* the scope where
// `opaque type` is textually declared. Declaring ProductId/ProductCode here (not inside the spec class
// itself) means the spec class sees them as fully opaque, exactly like a real business module sees
// MasterServiceOfferVariantId/ServiceCode declared in a different module - which is what the
// selector-driven `.inferred(...)` derivation this spec exercises actually depends on.
object SearchFieldDeclarationsSpec {
  opaque type ProductId = UUID

  object ProductId extends UuidBackedId[ProductId] {
    def apply(value: UUID): ProductId = value
    def unwrap(id: ProductId): UUID   = id
    given UuidBackedId[ProductId]     = this
  }

  final case class ProductCodeValidationError(value: String) {
    def message: String = s"Invalid ProductCode '$value'"
  }

  opaque type ProductCode = String

  object ProductCode extends CanonicalStringValue[ProductCode] {
    private val pattern = "^[a-z]+$".r

    def fromString(value: String): Either[ProductCodeValidationError, ProductCode] =
      if (pattern.matches(value)) Right(value) else Left(ProductCodeValidationError(value))

    extension (code: ProductCode) def value: String = code

    def decodeCanonical(value: String): Either[String, ProductCode] = fromString(value).left.map(_.message)
    def encodeCanonical(value: ProductCode): String                 = value.value

    given CanonicalStringValue[ProductCode] = this
  }

  final case class TestDocument(
    id: ProductId,
    code: ProductCode,
    name: String,
    description: String,
    count: Int,
    viewCount: Long,
    price: BigDecimal,
    active: Boolean,
    publishedAt: Instant,
    location: GeoPoint,
    intAttributes: Map[String, Int],
  )

  final case class AttrDefinition(code: String)
}

final class SearchFieldDeclarationsSpec extends AnyWordSpec {
  import SearchFieldDeclarationsSpec.*

  private val definitions = Vector(AttrDefinition("session_count"), AttrDefinition("max_clients"))

  private val fixtureId: ProductId     = ProductId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
  private val fixtureCode: ProductCode = ProductCode.fromString("widget").getOrElse(fail("expected a valid ProductCode fixture"))

  private val fixtureDoc: TestDocument = TestDocument(
    id = fixtureId,
    code = fixtureCode,
    name = "Widget",
    description = "A test widget",
    count = 3,
    viewCount = 4294967296L,
    price = BigDecimal("9.99"),
    active = true,
    publishedAt = Instant.parse("2026-01-01T00:00:00Z"),
    location = GeoPoint(BigDecimal("1.5"), BigDecimal("2.5")),
    intAttributes = Map("session_count" -> 5),
  )

  "inferred/keyword/text/integer/decimal/boolean/geoPoint direct fields" should {
    "derive default FieldId/FieldPath/FieldSemantic from the selector, with required extraction" in {
      val declarations = searchFields[TestDocument]("testDocuments")
      val idField       = declarations.inferred(_.id).declare
      assert(idField.id == FieldId("id"))
      assert(idField.path == FieldPath("id"))
      assert(idField.semantic == Some(FieldSemantic("id")))
      assert(idField.required)
      assert(idField.extract(fixtureDoc) == Some(fixtureId))
    }

    "infer Keyword for a UuidBackedId type and for a CanonicalStringValue type" in {
      val declarations = searchFields[TestDocument]("testDocuments")
      assert(declarations.inferred(_.id).declare.kind == SearchFieldKind.Keyword)
      assert(declarations.inferred(_.code).declare.kind == SearchFieldKind.Keyword)
    }

    "infer Integer for Int, Long for Long, Decimal for BigDecimal, Boolean for Boolean, DateTime for Instant, and GeoPoint for GeoPoint" in {
      val declarations = searchFields[TestDocument]("testDocuments")
      assert(declarations.inferred(_.count).declare.kind == SearchFieldKind.Integer)
      assert(declarations.inferred(_.viewCount).declare.kind == SearchFieldKind.Long)
      assert(declarations.inferred(_.price).declare.kind == SearchFieldKind.Decimal)
      assert(declarations.inferred(_.active).declare.kind == SearchFieldKind.Boolean)
      assert(declarations.inferred(_.publishedAt).declare.kind == SearchFieldKind.DateTime)
      assert(declarations.inferred(_.location).declare.kind == SearchFieldKind.GeoPoint)
    }

    "require keyword/text to stay an explicit choice for a raw String field - no default kind exists" in {
      assertDoesNotCompile("""searchFields[TestDocument]("testDocuments").inferred(_.name)""")
      assertCompiles("""searchFields[TestDocument]("testDocuments").keyword(_.name)""")
      assertCompiles("""searchFields[TestDocument]("testDocuments").text(_.name)""")
    }

    "declare integer/long/decimal/boolean/dateTime/geoPoint fields with the exact matching kind" in {
      val declarations = searchFields[TestDocument]("testDocuments")
      assert(declarations.integer(_.count).declare.kind == SearchFieldKind.Integer)
      assert(declarations.long(_.viewCount).declare.kind == SearchFieldKind.Long)
      assert(declarations.decimal(_.price).declare.kind == SearchFieldKind.Decimal)
      assert(declarations.boolean(_.active).declare.kind == SearchFieldKind.Boolean)
      assert(declarations.dateTime(_.publishedAt).declare.kind == SearchFieldKind.DateTime)
      assert(declarations.geoPoint(_.location).declare.kind == SearchFieldKind.GeoPoint)
    }
  }

  "named and withSemantic" should {
    "change only the FieldId, never the derived FieldPath" in {
      val declarations = searchFields[TestDocument]("testDocuments")
      val builtField     = declarations.inferred(_.id).named("productId").declare
      assert(builtField.id == FieldId("productId"))
      assert(builtField.path == FieldPath("id"))
    }

    "change only the semantic identity" in {
      val declarations = searchFields[TestDocument]("testDocuments")
      val builtField     = declarations.inferred(_.id).withSemantic("product.id").declare
      assert(builtField.semantic == Some(FieldSemantic("product.id")))
      assert(builtField.id == FieldId("id"))
    }
  }

  "capability chaining" should {
    "register the final configured field, not an earlier unconfigured copy" in {
      val declarations = searchFields[TestDocument]("testDocuments")
      val builtField = declarations
        .inferred(_.count)
        .filterable(FilterOperator.Range)
        .sortable(SortMode.Value)
        .payloadEligible
        .declare

      assert(builtField.capabilities.filterOperators == Set(FilterOperator.Range))
      assert(builtField.capabilities.sortModes == Set(SortMode.Value))
      assert(builtField.capabilities.payloadEligible)
      assert(declarations.staticFields == Vector(builtField))
    }
  }

  "declaration order and collection separation" should {
    "register static fields in declaration order" in {
      val declarations = searchFields[TestDocument]("testDocuments")
      val first          = declarations.inferred(_.id).declare
      val second         = declarations.keyword(_.name).declare
      val third          = declarations.inferred(_.count).declare
      assert(declarations.staticFields == Vector(first, second, third))
    }

    "keep static and dynamic collections separate, with allFields always staticFields ++ dynamicFields" in {
      val declarations  = searchFields[TestDocument]("testDocuments")
      val idField        = declarations.inferred(_.id).declare
      val dynamicFamily = declarations.dynamicMap(_.intAttributes, definitions)(_.code).inferred.declare
      assert(declarations.staticFields == Vector(idField))
      assert(declarations.dynamicFields == dynamicFamily.fields)
      assert(declarations.allFields == declarations.staticFields ++ declarations.dynamicFields)
    }
  }

  "dynamic map families" should {
    "derive exact prefixed id/path/semantic per definition code, with optional extraction" in {
      val declarations = searchFields[TestDocument]("testDocuments")
      val family         = declarations.dynamicMap(_.intAttributes, definitions)(_.code).inferred.declare

      val sessionCountField = family.byCode.getOrElse("session_count", fail("missing session_count field"))
      assert(sessionCountField.id == FieldId("intAttributes.session_count"))
      assert(sessionCountField.path == FieldPath("intAttributes.session_count"))
      assert(sessionCountField.semantic == Some(FieldSemantic("intAttributes.session_count")))
      assert(!sessionCountField.required)
      assert(sessionCountField.extract(fixtureDoc) == Some(5))

      val maxClientsField = family.byCode.getOrElse("max_clients", fail("missing max_clients field"))
      assert(maxClientsField.extract(fixtureDoc) == None)
    }

    "preserve definition order in entries/fields, converted to a Vector exactly once" in {
      val declarations = searchFields[TestDocument]("testDocuments")
      val family         = declarations.dynamicMap(_.intAttributes, definitions)(_.code).inferred.declare
      assert(family.entries.map(_._1) == Vector("session_count", "max_clients"))
      assert(family.fields.map(_.id.value) == Vector("intAttributes.session_count", "intAttributes.max_clients"))
    }

    "return the exact same handles from byCode as the ordered entries vector, by reference" in {
      val declarations = searchFields[TestDocument]("testDocuments")
      val family         = declarations.dynamicMap(_.intAttributes, definitions)(_.code).inferred.declare
      family.entries.foreach { case (code, entryField) => assert(family.byCode.getOrElse(code, fail(s"missing $code")) eq entryField) }
    }

    "reject duplicate definition codes before any Map is constructed" in {
      val declarations         = searchFields[TestDocument]("testDocuments")
      val duplicateDefinitions = Vector(AttrDefinition("dup"), AttrDefinition("dup"))
      assertThrows[IllegalStateException] {
        declarations.dynamicMap(_.intAttributes, duplicateDefinitions)(_.code).inferred.declare
      }
    }

    "expose keyword kind selection for enum-style dynamic families" in {
      val declarations = searchFields[TestDocument]("testDocuments")
      val family         = declarations.dynamicMap(_.intAttributes, definitions)(_.code).keyword(using SearchValueCodec.int).declare
      assert(family.fields.forall(_.kind == SearchFieldKind.Keyword))
    }

    "expose text kind selection for a String-valued dynamic family, distinct from keyword" in {
      final case class TranslatedDocument(id: ProductId, translations: Map[String, String])
      val declarations = searchFields[TranslatedDocument]("translatedDocuments")
      val family         = declarations.dynamicMap(_.translations, definitions)(_.code).text.searchable.declare
      assert(family.fields.forall(_.kind == SearchFieldKind.Text))
      assert(family.fields.forall(_.capabilities.searchable))
    }
  }

  "document construction" should {
    "exclude the identity by reference from ordinary document fields, preserving declared order" in {
      val declarations = searchFields[TestDocument]("testDocuments")
      val idField        = declarations.inferred(_.id).declare
      val nameField      = declarations.keyword(_.name).declare
      val family         = declarations.dynamicMap(_.intAttributes, definitions)(_.code).inferred.declare

      val document = declarations.document(idField)

      assert(document.identity eq idField)
      assert(!document.fields.exists(_ eq idField))
      assert(document.fields == Vector(nameField) ++ family.fields)
    }

    "require identity to have been declared in this same registry" in {
      val declarations   = searchFields[TestDocument]("testDocuments")
      val foreignIdField = searchFields[TestDocument]("otherDocuments").inferred(_.id).declare

      assertThrows[IllegalStateException] {
        declarations.document(foreignIdField)
      }
    }

    "fail further declaration after the registry is frozen by document(...)" in {
      val declarations = searchFields[TestDocument]("testDocuments")
      val idField        = declarations.inferred(_.id).declare
      declarations.keyword(_.name).declare
      declarations.document(idField)

      assertThrows[IllegalStateException] {
        declarations.keyword(_.description).declare
      }
    }

    "surface generic-kernel validation errors through validateDocument, not a second error model" in {
      val declarations = searchFields[TestDocument]("testDocuments")
      val idField        = declarations.inferred(_.id).declare
      declarations.keyword(_.name).named("id").declare

      declarations.validateDocument(idField) match {
        case Left(errors) => assert(errors.toVector.contains(DuplicateFieldId(FieldId("id"))))
        case Right(value) => fail(s"expected rejection, got: $value")
      }
    }

    "throw one IllegalStateException naming the document id and every deterministic validation error" in {
      val declarations = searchFields[TestDocument]("testDocuments")
      val idField        = declarations.inferred(_.id).declare
      declarations.keyword(_.name).named("id").declare

      val error = intercept[IllegalStateException] {
        declarations.document(idField)
      }
      assert(error.getMessage.contains("testDocuments"))
      assert(error.getMessage.contains(DuplicateFieldId(FieldId("id")).toString))
    }
  }

  "completeDocument construction" should {
    "reject a product member that is neither declared nor explicitly ignored" in {
      val declarations = searchFields[TestDocument]("testDocuments")
      val idField        = declarations.inferred(_.id).declare
      declarations.keyword(_.name).declare

      val error = intercept[IllegalStateException] {
        declarations.completeDocument(idField)
      }
      assert(
        error.getMessage.contains(
          "missing: code, description, count, viewCount, price, active, publishedAt, location, intAttributes"
        )
      )
    }

    "accept an explicitly ignored product member inventory" in {
      val declarations = searchFields[TestDocument]("testDocuments")
      val idField        = declarations.inferred(_.id).declare
      val nameField      = declarations.keyword(_.name).declare
      declarations.ignore(_.code)
      declarations.ignore(_.description)
      declarations.ignore(_.count)
      declarations.ignore(_.viewCount)
      declarations.ignore(_.price)
      declarations.ignore(_.active)
      declarations.ignore(_.publishedAt)
      declarations.ignore(_.location)
      declarations.ignore(_.intAttributes)

      val document = declarations.completeDocument(idField)
      assert(document.allFields == Vector(idField, nameField))
    }

    "treat a dynamic map family as coverage of its owning product member" in {
      val declarations = searchFields[TestDocument]("testDocuments")
      val idField        = declarations.inferred(_.id).declare
      declarations.inferred(_.code).declare
      declarations.keyword(_.name).declare
      declarations.text(_.description).declare
      declarations.inferred(_.count).declare
      declarations.inferred(_.viewCount).declare
      declarations.inferred(_.price).declare
      declarations.inferred(_.active).declare
      declarations.inferred(_.publishedAt).declare
      declarations.inferred(_.location).declare
      declarations.dynamicMap(_.intAttributes, definitions)(_.code).inferred.declare

      val document = declarations.completeDocument(idField)
      assert(document.allFields.size == 12)
    }
  }

  "the old low-level field/computedField/searchDocument APIs" should {
    "remain usable directly, unmigrated" in {
      val handle = field[TestDocument, String]("displayName", _.name).keyword
      assert(handle.path == FieldPath("name"))

      val countField = computedField[TestDocument, Int]("count", "count")(doc => Some(doc.count)).integer
      val result       = searchDocument[TestDocument]("testDocuments").id(handle).field(countField).build
      assert(result.isRight)
    }
  }

  "selector restrictions" should {
    "reject a nested selector at compile time" in {
      assertDoesNotCompile("""searchFields[TestDocument]("testDocuments").inferred(_.location.lat)""")
    }

    "reject a computed method selector at compile time" in {
      assertDoesNotCompile("""searchFields[TestDocument]("testDocuments").keyword(_.name.toUpperCase)""")
    }

    "reject a computed expression selector at compile time" in {
      assertDoesNotCompile("""searchFields[TestDocument]("testDocuments").text((d: TestDocument) => d.name + "x")""")
    }

    "reject a parameterless computed method selector at compile time" in {
      assertDoesNotCompile(
        """{
          |  final case class ComputedMethodDoc(id: java.util.UUID, name: String) {
          |    def normalizedName: String = name.trim.toLowerCase
          |  }
          |  searchFields[ComputedMethodDoc]("computedMethodDocs").text(_.normalizedName)
          |}""".stripMargin
      )
    }

    "reject dynamicMap on a nested/computed map selector at compile time" in {
      assertDoesNotCompile(
        """{
          |  final case class Wrapper(inner: TestDocument)
          |  searchFields[Wrapper]("wrappers").dynamicMap(_.inner.intAttributes, definitions)(_.code)
          |}""".stripMargin
      )
    }
  }
}
