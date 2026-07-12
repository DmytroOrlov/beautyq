package leaderboard.search.gen2.contract

import leaderboard.model.{CanonicalStringValue, UuidBackedId}

import java.time.Instant
import java.util.UUID
import scala.util.Try

final case class SearchValueDecodeError(
  typeId: SearchValueTypeId,
  input: String,
  message: String,
)

final case class GeoPoint(
  lat: BigDecimal,
  lon: BigDecimal,
)

/** Logical canonical value codec: type identity, deterministic canonical string encoding, decoding
  * from that representation, and invariant mapping for domain wrappers. Owns none of Circe/JSON,
  * Elasticsearch source, Qdrant payload, mapping/index types, analyzers, or backend transport.
  */
trait SearchValueCodec[A] { self =>
  def typeId: SearchValueTypeId

  def encodeCanonical(value: A): String

  def decodeCanonical(value: String): Either[SearchValueDecodeError, A]

  final def imap[B](
    newTypeId: SearchValueTypeId
  )(
    decodeValue: A => Either[String, B],
    encodeValue: B => A,
  ): SearchValueCodec[B] =
    new SearchValueCodec[B] {
      def typeId: SearchValueTypeId = newTypeId

      def encodeCanonical(value: B): String = self.encodeCanonical(encodeValue(value))

      def decodeCanonical(value: String): Either[SearchValueDecodeError, B] =
        self.decodeCanonical(value) match {
          case Left(baseError) => Left(baseError.copy(typeId = newTypeId))
          case Right(baseValue) =>
            decodeValue(baseValue) match {
              case Left(message)  => Left(SearchValueDecodeError(newTypeId, value, message))
              case Right(wrapped) => Right(wrapped)
            }
        }
    }
}

object SearchValueCodec {

  given string: SearchValueCodec[String] with {
    def typeId: SearchValueTypeId = SearchValueTypeId("string")
    def encodeCanonical(value: String): String = value
    def decodeCanonical(value: String): Either[SearchValueDecodeError, String] = Right(value)
  }

  given int: SearchValueCodec[Int] with {
    def typeId: SearchValueTypeId = SearchValueTypeId("int")
    def encodeCanonical(value: Int): String = value.toString
    def decodeCanonical(value: String): Either[SearchValueDecodeError, Int] =
      value.toIntOption match {
        case Some(parsed) =>
          requireCanonicalRoundTrip(
            value,
            parsed,
            encodeCanonical,
            SearchValueDecodeError(typeId, value, s"'$value' is not a canonical int representation"),
          )
        case None =>
          Left(SearchValueDecodeError(typeId, value, s"'$value' is not a valid int"))
      }
  }

  given long: SearchValueCodec[Long] with {
    def typeId: SearchValueTypeId = SearchValueTypeId("long")
    def encodeCanonical(value: Long): String = value.toString
    def decodeCanonical(value: String): Either[SearchValueDecodeError, Long] =
      value.toLongOption match {
        case Some(parsed) =>
          requireCanonicalRoundTrip(
            value,
            parsed,
            encodeCanonical,
            SearchValueDecodeError(typeId, value, s"'$value' is not a canonical long representation"),
          )
        case None =>
          Left(SearchValueDecodeError(typeId, value, s"'$value' is not a valid long"))
      }
  }

  given bigDecimal: SearchValueCodec[BigDecimal] with {
    def typeId: SearchValueTypeId = SearchValueTypeId("decimal")
    def encodeCanonical(value: BigDecimal): String = normalizeDecimal(value)
    def decodeCanonical(value: String): Either[SearchValueDecodeError, BigDecimal] =
      Try(BigDecimal(value)).toOption match {
        case Some(parsed) =>
          requireCanonicalRoundTrip(
            value,
            parsed,
            encodeCanonical,
            SearchValueDecodeError(typeId, value, s"'$value' is not a canonical decimal representation"),
          )
        case None =>
          Left(SearchValueDecodeError(typeId, value, s"'$value' is not a valid decimal"))
      }
  }

  given boolean: SearchValueCodec[Boolean] with {
    def typeId: SearchValueTypeId = SearchValueTypeId("boolean")
    def encodeCanonical(value: Boolean): String = value.toString
    def decodeCanonical(value: String): Either[SearchValueDecodeError, Boolean] =
      value match {
        case "true"  => Right(true)
        case "false" => Right(false)
        case other   => Left(SearchValueDecodeError(typeId, value, s"'$other' is not a valid boolean; only lowercase true/false are accepted"))
      }
  }

  // Instant.parse accepts multiple spellings of the same instant, including an explicit +00:00 offset
  // and redundant/truncated fractional seconds. Instant.toString emits the one canonical UTC spelling
  // used by this contract, so the exact round-trip check is load-bearing rather than cosmetic.
  given instant: SearchValueCodec[Instant] with {
    def typeId: SearchValueTypeId = SearchValueTypeId("instant")
    def encodeCanonical(value: Instant): String = value.toString
    def decodeCanonical(value: String): Either[SearchValueDecodeError, Instant] =
      Try(Instant.parse(value)).toOption match {
        case Some(parsed) =>
          requireCanonicalRoundTrip(
            value,
            parsed,
            encodeCanonical,
            SearchValueDecodeError(typeId, value, s"'$value' is not a canonical ISO-8601 instant representation"),
          )
        case None =>
          Left(SearchValueDecodeError(typeId, value, s"'$value' is not a valid ISO-8601 instant"))
      }
  }

  given uuid: SearchValueCodec[UUID] with {
    def typeId: SearchValueTypeId = SearchValueTypeId("uuid")
    def encodeCanonical(value: UUID): String = value.toString

    // UUID.fromString is lenient in ways the canonical form must not be: it accepts mixed-case hex
    // (always re-rendering lowercase) and tolerates hyphen-group lengths other than 8-4-4-4-12 (e.g.
    // "1-1-1-1-1" parses without error). Re-encoding the parsed value and requiring it to equal the
    // original input exactly is what actually enforces "standard lowercase UUID string only" - a
    // successful parse alone is not sufficient. Input is never lowercased or trimmed first.
    def decodeCanonical(value: String): Either[SearchValueDecodeError, UUID] =
      Try(UUID.fromString(value)).toOption match {
        case Some(parsed) if parsed.toString == value => Right(parsed)
        case _                                        => Left(SearchValueDecodeError(typeId, value, s"'$value' is not a valid canonical lowercase UUID"))
      }
  }

  given geoPoint: SearchValueCodec[GeoPoint] with {
    def typeId: SearchValueTypeId = SearchValueTypeId("geo-point")

    def encodeCanonical(value: GeoPoint): String =
      s"${normalizeDecimal(value.lat)},${normalizeDecimal(value.lon)}"

    def decodeCanonical(value: String): Either[SearchValueDecodeError, GeoPoint] =
      value.split(",", -1) match {
        case Array(latRaw, lonRaw) =>
          for {
            lat   <- Try(BigDecimal(latRaw)).toOption.toRight(SearchValueDecodeError(typeId, value, s"'$latRaw' is not a valid decimal latitude"))
            lon   <- Try(BigDecimal(lonRaw)).toOption.toRight(SearchValueDecodeError(typeId, value, s"'$lonRaw' is not a valid decimal longitude"))
            point <- requireCanonicalRoundTrip(
                       value,
                       GeoPoint(lat, lon),
                       encodeCanonical,
                       SearchValueDecodeError(typeId, value, s"'$value' is not a canonical '<lat>,<lon>' geo point representation"),
                     )
          } yield point
        case _ =>
          Left(SearchValueDecodeError(typeId, value, s"'$value' is not a valid '<lat>,<lon>' geo point"))
      }
  }

  // Generic logical codec for any UUID-backed nominal id, keyed off the same `UuidBackedId[A]`
  // evidence every other layer (Doobie/Tapir/Scalacheck) already derives its own adapter from - no
  // per-id `SearchValueCodec` declaration is needed in a business module. `inline` so the nominal type
  // id derivation macro below sees the concrete requested `A` at this given's use site.
  inline given uuidBacked[A](using id: UuidBackedId[A]): SearchValueCodec[A] =
    SearchValueCodec.uuid.imap(SearchValueTypeId.derived[A])(
      uuid => Right(id.apply(uuid)),
      id.unwrap,
    )

  // Generic logical codec for any validated canonical String wrapper (e.g. a stable business code),
  // keyed off `CanonicalStringValue[A]` evidence the wrapper's own companion registers once.
  inline given canonicalString[A](using value: CanonicalStringValue[A]): SearchValueCodec[A] =
    SearchValueCodec.string.imap(SearchValueTypeId.derived[A])(
      value.decodeCanonical,
      value.encodeCanonical,
    )

  // Shared canonical round-trip guard: a successful parse is not sufficient on its own, since e.g.
  // "01", "+1", or "1.50" all parse successfully but are not the one stable representation that
  // `encodeCanonical` produces for the parsed value.
  private def requireCanonicalRoundTrip[A](
    input: String,
    parsed: A,
    encode: A => String,
    decodeError: => SearchValueDecodeError,
  ): Either[SearchValueDecodeError, A] =
    if (encode(parsed) == input) Right(parsed) else Left(decodeError)

  private def normalizeDecimal(value: BigDecimal): String =
    value.bigDecimal.stripTrailingZeros.toPlainString match {
      case "-0" => "0"
      case other => other
    }
}
