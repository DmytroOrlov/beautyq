package leaderboard.search.gen2.contract

import org.scalatest.wordspec.AnyWordSpec

import java.time.Instant
import java.util.UUID

final class SearchValueCodecSpec extends AnyWordSpec {

  // Neutral domain wrapper proving `imap`, independent of any real BeautyQ or other domain type.
  private final case class ProductCode(value: String)

  private val productCode: SearchValueCodec[ProductCode] =
    summon[SearchValueCodec[String]].imap(SearchValueTypeId("product-code"))(
      raw => if (raw.startsWith("PC-")) Right(ProductCode(raw)) else Left(s"'$raw' must start with 'PC-'"),
      wrapped => wrapped.value,
    )

  "built-in codecs" should {
    "round-trip a representative string value" in {
      val codec = summon[SearchValueCodec[String]]
      assert(codec.decodeCanonical(codec.encodeCanonical("alice")) == Right("alice"))
    }

    "round-trip a representative int value" in {
      val codec = summon[SearchValueCodec[Int]]
      assert(codec.decodeCanonical(codec.encodeCanonical(42)) == Right(42))
    }

    "round-trip a representative long value" in {
      val codec = summon[SearchValueCodec[Long]]
      assert(codec.decodeCanonical(codec.encodeCanonical(4294967296L)) == Right(4294967296L))
    }

    "round-trip a representative decimal value" in {
      val codec = summon[SearchValueCodec[BigDecimal]]
      val value = BigDecimal("12.50")
      assert(codec.decodeCanonical(codec.encodeCanonical(value)) == Right(value))
    }

    "round-trip a representative instant value" in {
      val codec = summon[SearchValueCodec[Instant]]
      val value = Instant.parse("2026-01-01T00:00:00Z")
      assert(codec.decodeCanonical(codec.encodeCanonical(value)) == Right(value))
    }

    "round-trip a representative boolean value" in {
      val codec = summon[SearchValueCodec[Boolean]]
      assert(codec.decodeCanonical(codec.encodeCanonical(true)) == Right(true))
      assert(codec.decodeCanonical(codec.encodeCanonical(false)) == Right(false))
    }

    "round-trip a representative UUID value" in {
      val codec = summon[SearchValueCodec[UUID]]
      val value = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
      assert(codec.decodeCanonical(codec.encodeCanonical(value)) == Right(value))
    }

    "round-trip a representative geo point value" in {
      val codec = summon[SearchValueCodec[GeoPoint]]
      val value = GeoPoint(BigDecimal("1.5"), BigDecimal("2.5"))
      assert(codec.decodeCanonical(codec.encodeCanonical(value)) == Right(value))
    }

    "expose the exact built-in logical type ids" in {
      assert(summon[SearchValueCodec[String]].typeId == SearchValueTypeId("string"))
      assert(summon[SearchValueCodec[Int]].typeId == SearchValueTypeId("int"))
      assert(summon[SearchValueCodec[Long]].typeId == SearchValueTypeId("long"))
      assert(summon[SearchValueCodec[BigDecimal]].typeId == SearchValueTypeId("decimal"))
      assert(summon[SearchValueCodec[Boolean]].typeId == SearchValueTypeId("boolean"))
      assert(summon[SearchValueCodec[Instant]].typeId == SearchValueTypeId("instant"))
      assert(summon[SearchValueCodec[UUID]].typeId == SearchValueTypeId("uuid"))
      assert(summon[SearchValueCodec[GeoPoint]].typeId == SearchValueTypeId("geo-point"))
    }

    "normalize 1.00, 1.0, and 1 to the same canonical decimal encoding" in {
      val codec = summon[SearchValueCodec[BigDecimal]]
      assert(codec.encodeCanonical(BigDecimal("1.00")) == "1")
      assert(codec.encodeCanonical(BigDecimal("1.0")) == "1")
      assert(codec.encodeCanonical(BigDecimal("1")) == "1")
    }

    "accept only lowercase true and false when decoding a boolean" in {
      val codec = summon[SearchValueCodec[Boolean]]
      assert(codec.decodeCanonical("true") == Right(true))
      assert(codec.decodeCanonical("false") == Right(false))
      assert(codec.decodeCanonical("True").isLeft)
      assert(codec.decodeCanonical("FALSE").isLeft)
      assert(codec.decodeCanonical("1").isLeft)
    }

    "reject malformed int, long, decimal, boolean, UUID, instant, and geo point input without throwing" in {
      assert(summon[SearchValueCodec[Int]].decodeCanonical("not-an-int").isLeft)
      assert(summon[SearchValueCodec[Long]].decodeCanonical("not-a-long").isLeft)
      assert(summon[SearchValueCodec[BigDecimal]].decodeCanonical("not-a-decimal").isLeft)
      assert(summon[SearchValueCodec[Boolean]].decodeCanonical("not-a-boolean").isLeft)
      assert(summon[SearchValueCodec[UUID]].decodeCanonical("not-a-uuid").isLeft)
      assert(summon[SearchValueCodec[Instant]].decodeCanonical("not-an-instant").isLeft)
      assert(summon[SearchValueCodec[GeoPoint]].decodeCanonical("not-a-geo-point").isLeft)
      assert(summon[SearchValueCodec[GeoPoint]].decodeCanonical("1.0,not-a-decimal").isLeft)
    }

    "accept only the exact standard lowercase UUID representation" in {
      val codec = summon[SearchValueCodec[UUID]]

      assert(codec.decodeCanonical("123e4567-e89b-12d3-a456-426614174000") == Right(UUID.fromString("123e4567-e89b-12d3-a456-426614174000")))
      assert(codec.decodeCanonical("123E4567-E89B-12D3-A456-426614174000").isLeft)
      assert(codec.decodeCanonical("1-1-1-1-1").isLeft)
      assert(codec.decodeCanonical(" 123e4567-e89b-12d3-a456-426614174000").isLeft)
      assert(codec.decodeCanonical("123e4567-e89b-12d3-a456-426614174000 ").isLeft)
    }

    "accept exactly the canonical int forms and reject every noncanonical variant" in {
      val codec = summon[SearchValueCodec[Int]]

      val accepted = Vector("0", "1", "-1", "42")
      val rejected = Vector("00", "01", "+1", "-0", " 1", "1 ")

      accepted.foreach { input =>
        assert(codec.decodeCanonical(input).map(codec.encodeCanonical) == Right(input))
      }
      rejected.foreach { input =>
        assert(codec.decodeCanonical(input).isLeft)
      }
    }

    "accept exactly the canonical long forms and reject every noncanonical variant" in {
      val codec = summon[SearchValueCodec[Long]]

      val accepted = Vector("0", "1", "-1", "4294967296", "-4294967296")
      val rejected = Vector("00", "01", "+1", "-0", " 1", "1 ")

      accepted.foreach { input =>
        assert(codec.decodeCanonical(input).map(codec.encodeCanonical) == Right(input))
      }
      rejected.foreach { input =>
        assert(codec.decodeCanonical(input).isLeft)
      }
    }

    "accept exactly the canonical instant forms and reject every noncanonical variant" in {
      val codec = summon[SearchValueCodec[Instant]]

      val accepted = Vector("2026-01-01T00:00:00Z", "2026-01-01T00:00:00.500Z")
      val rejected = Vector(
        "2026-01-01T00:00:00+00:00",
        "2026-01-01T00:00:00.000Z",
        "2026-01-01T00:00:00.5Z",
        "2026-01-01 00:00:00Z",
        " 2026-01-01T00:00:00Z",
        "not-an-instant",
      )

      accepted.foreach { input =>
        assert(codec.decodeCanonical(input).map(codec.encodeCanonical) == Right(input))
      }
      rejected.foreach { input =>
        assert(codec.decodeCanonical(input).isLeft)
      }
    }

    "accept exactly the canonical decimal forms and reject every noncanonical variant" in {
      val codec = summon[SearchValueCodec[BigDecimal]]

      val accepted = Vector("0", "1", "-1", "1.25", "100", "0.5")
      val rejected = Vector("1.00", "1.0", "1E+2", "1e2", "+1", "-0", ".5", "0.50", " 1", "1 ")

      accepted.foreach { input =>
        assert(codec.decodeCanonical(input).map(codec.encodeCanonical) == Right(input))
      }
      rejected.foreach { input =>
        assert(codec.decodeCanonical(input).isLeft)
      }
    }

    "accept exactly the canonical geo point forms and reject every noncanonical variant" in {
      val codec = summon[SearchValueCodec[GeoPoint]]

      val accepted = Vector("1.5,2.5", "0,0", "-1.25,10")
      val rejected = Vector(
        "1.50,2.5",
        "1.5,2.50",
        "1E+0,2",
        "+1,2",
        "-0,0",
        "1.5, 2.5",
        " 1.5,2.5",
        "1.5,2.5 ",
      )

      accepted.foreach { input =>
        assert(codec.decodeCanonical(input).map(codec.encodeCanonical) == Right(input))
      }
      rejected.foreach { input =>
        assert(codec.decodeCanonical(input).isLeft)
      }
    }

    "preserve type id, original input, and a non-empty message when rejecting a noncanonical int, decimal, or geo point" in {
      summon[SearchValueCodec[Int]].decodeCanonical("01") match {
        case Left(error) =>
          assert(error.typeId == SearchValueTypeId("int"))
          assert(error.input == "01")
          assert(error.message.nonEmpty)
        case Right(value) =>
          fail(s"expected a decode failure, got: $value")
      }

      summon[SearchValueCodec[BigDecimal]].decodeCanonical("1.50") match {
        case Left(error) =>
          assert(error.typeId == SearchValueTypeId("decimal"))
          assert(error.input == "1.50")
          assert(error.message.nonEmpty)
        case Right(value) =>
          fail(s"expected a decode failure, got: $value")
      }

      summon[SearchValueCodec[GeoPoint]].decodeCanonical("1.50,2.5") match {
        case Left(error) =>
          assert(error.typeId == SearchValueTypeId("geo-point"))
          assert(error.input == "1.50,2.5")
          assert(error.message.nonEmpty)
        case Right(value) =>
          fail(s"expected a decode failure, got: $value")
      }
    }

    "preserve target type id, original input, and a useful message on decode failure" in {
      summon[SearchValueCodec[Int]].decodeCanonical("not-an-int") match {
        case Left(error) =>
          assert(error.typeId == SearchValueTypeId("int"))
          assert(error.input == "not-an-int")
          assert(error.message.nonEmpty)
        case Right(value) =>
          fail(s"expected a decode failure, got: $value")
      }
    }
  }

  "imap" should {
    "change the logical type id" in {
      assert(productCode.typeId == SearchValueTypeId("product-code"))
    }

    "preserve canonical encoding by delegating through the base codec" in {
      assert(productCode.encodeCanonical(ProductCode("PC-123")) == "PC-123")
    }

    "decode successfully when the base codec and wrapper validation both succeed" in {
      assert(productCode.decodeCanonical("PC-123") == Right(ProductCode("PC-123")))
    }

    "return wrapper validation failures as typed decode errors under the new type id" in {
      productCode.decodeCanonical("bad-code") match {
        case Left(error) =>
          assert(error.typeId == SearchValueTypeId("product-code"))
          assert(error.input == "bad-code")
          assert(error.message.nonEmpty)
        case Right(value) =>
          fail(s"expected a decode failure, got: $value")
      }
    }

    "return base codec decode failures under the new type id as well" in {
      val positiveInt = summon[SearchValueCodec[Int]].imap(SearchValueTypeId("positive-int"))(
        raw => if (raw > 0) Right(raw) else Left(s"'$raw' must be positive"),
        wrapped => wrapped,
      )

      positiveInt.decodeCanonical("not-an-int") match {
        case Left(error) =>
          assert(error.typeId == SearchValueTypeId("positive-int"))
          assert(error.input == "not-an-int")
        case Right(value) =>
          fail(s"expected a decode failure, got: $value")
      }
    }
  }
}
