package leaderboard.search.gen2.contract

import leaderboard.model.{CanonicalStringValue, UuidBackedId}
import org.scalatest.wordspec.AnyWordSpec
import scala.compiletime.testing.typeCheckErrors

import java.util.UUID

// Fixture types live in the companion object, a separate enclosing scope from the spec class below: an
// opaque type is transparent to its underlying representation only *within* the scope where `opaque
// type` is textually declared. Declaring them here (not inside the spec class) means the spec class
// sees WidgetId/WidgetCode as fully opaque - exactly like a real business module sees
// MasterServiceOfferVariantId/ServiceCode declared in a different module - and the built-in
// SearchValueCodec[UUID]/[String] givens do not also become valid matches for them.
object SearchValueCodecDerivationSpec {
  // A local opaque UUID-backed type whose companion registers only `UuidBackedId[WidgetId]` - no
  // `SearchValueCodec[WidgetId]` given is declared anywhere in this fixture.
  opaque type WidgetId = UUID

  object WidgetId extends UuidBackedId[WidgetId] {
    def apply(value: UUID): WidgetId = value
    def unwrap(id: WidgetId): UUID   = id
    given UuidBackedId[WidgetId]     = this
  }

  final case class WidgetCodeValidationError(value: String) {
    def message: String = s"Invalid WidgetCode '$value': expected lowercase letters only"
  }

  // A local canonical String wrapper whose companion registers only `CanonicalStringValue[WidgetCode]`
  // - no `SearchValueCodec[WidgetCode]` given is declared anywhere in this fixture.
  opaque type WidgetCode = String

  object WidgetCode extends CanonicalStringValue[WidgetCode] {
    private val pattern = "^[a-z]+$".r

    def fromString(value: String): Either[WidgetCodeValidationError, WidgetCode] =
      if (pattern.matches(value)) Right(value) else Left(WidgetCodeValidationError(value))

    extension (code: WidgetCode) def value: String = code

    def decodeCanonical(value: String): Either[String, WidgetCode] = fromString(value).left.map(_.message)
    def encodeCanonical(value: WidgetCode): String                 = value.value

    given CanonicalStringValue[WidgetCode] = this
  }
}

final class SearchValueCodecDerivationSpec extends AnyWordSpec {
  import SearchValueCodecDerivationSpec.*

  "SearchValueCodec.uuidBacked" should {
    "derive a codec automatically for a local UUID-backed nominal type with no explicit SearchValueCodec given in the fixture" in {
      val sample = UUID.fromString("11111111-2222-3333-4444-555555555555")
      val codec  = summon[SearchValueCodec[WidgetId]]
      assert(codec.decodeCanonical(codec.encodeCanonical(WidgetId(sample))) == Right(WidgetId(sample)))
    }

    "derive the nominal type's own simple name as the type id" in {
      assert(summon[SearchValueCodec[WidgetId]].typeId == SearchValueTypeId("WidgetId"))
    }

    "round-trip a canonical lowercase UUID" in {
      val sample = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
      val codec  = summon[SearchValueCodec[WidgetId]]
      assert(codec.encodeCanonical(WidgetId(sample)) == "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
      assert(codec.decodeCanonical("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee") == Right(WidgetId(sample)))
    }

    "retain the nominal type id when UUID decoding fails" in {
      summon[SearchValueCodec[WidgetId]].decodeCanonical("not-a-uuid") match {
        case Left(error)  => assert(error.typeId == SearchValueTypeId("WidgetId"))
        case Right(value) => fail(s"expected rejection, got $value")
      }
    }
  }

  "SearchValueCodec.canonicalString" should {
    "derive a codec automatically for a local canonical String wrapper with no explicit SearchValueCodec given in the fixture" in {
      WidgetCode.fromString("widget") match {
        case Right(code) =>
          val codec = summon[SearchValueCodec[WidgetCode]]
          assert(codec.decodeCanonical(codec.encodeCanonical(code)) == Right(code))
        case Left(error) =>
          fail(s"expected acceptance, got: ${error.message}")
      }
    }

    "derive the nominal type's own simple name as the type id" in {
      assert(summon[SearchValueCodec[WidgetCode]].typeId == SearchValueTypeId("WidgetCode"))
    }

    "round-trip every accepted canonical value" in {
      val codec = summon[SearchValueCodec[WidgetCode]]
      Vector("widget", "gadget", "sprocket").foreach {
        raw =>
          WidgetCode.fromString(raw) match {
            case Right(code) => assert(codec.decodeCanonical(codec.encodeCanonical(code)) == Right(code))
            case Left(error) => fail(s"expected acceptance for '$raw', got: ${error.message}")
          }
      }
    }

    "preserve the wrapper's own validation message when rejecting an invalid value" in {
      summon[SearchValueCodec[WidgetCode]].decodeCanonical("Not Valid") match {
        case Left(error) =>
          assert(error.typeId == SearchValueTypeId("WidgetCode"))
          assert(error.message == WidgetCodeValidationError("Not Valid").message)
        case Right(value) =>
          fail(s"expected rejection, got $value")
      }
    }
  }

  "built-in codecs" should {
    "remain unambiguous alongside the generic uuidBacked/canonicalString givens" in {
      assert(summon[SearchValueCodec[String]].typeId == SearchValueTypeId("string"))
      assert(summon[SearchValueCodec[Int]].typeId == SearchValueTypeId("int"))
      assert(summon[SearchValueCodec[BigDecimal]].typeId == SearchValueTypeId("decimal"))
      assert(summon[SearchValueCodec[Boolean]].typeId == SearchValueTypeId("boolean"))
      assert(summon[SearchValueCodec[UUID]].typeId == SearchValueTypeId("uuid"))
      assert(summon[SearchValueCodec[GeoPoint]].typeId == SearchValueTypeId("geo-point"))
    }
  }

  "SearchValueTypeId.derived" should {
    "reject an unresolved type parameter instead of inventing a type id" in {
      val errors = typeCheckErrors(
        """
          |def derive[A]: SearchValueTypeId = SearchValueTypeId.derived[A]
          |""".stripMargin
      )

      assert(errors.nonEmpty)
      assert(errors.exists(_.message.contains("requires a concrete named type")))
      assert(errors.exists(_.message.contains("unresolved type parameter")))
    }
  }
}
