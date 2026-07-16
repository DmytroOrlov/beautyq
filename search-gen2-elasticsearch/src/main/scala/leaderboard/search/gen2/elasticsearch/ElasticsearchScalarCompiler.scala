package leaderboard.search.gen2.elasticsearch

import leaderboard.search.gen2.contract.*

import io.circe.Json

/** Shared backend-kind JSON representation for one canonical value, reused by both indexed-document
  * source compilation and query-value compilation - the exact same field codec and Elasticsearch
  * representation rules apply whether a value is being stored or queried, so this is the one mechanic
  * both compilers delegate to rather than each maintaining its own kind match.
  *
  * Decodes the just-encoded canonical string with the standard [[SearchValueCodec]] for its declared
  * [[SearchFieldKind]], never the field's own (possibly custom) codec: a field's codec is only guaranteed
  * to round-trip through itself, not to produce text compatible with its declared backend kind.
  * Keyword/Text values are stored/queried as-is, since any string is a valid keyword/text value. Returns
  * the neutral typed decode error on a kind mismatch; callers wrap it with their own document/request
  * context rather than this module inventing one.
  */
object ElasticsearchScalarCompiler {

  def toBackendJson(kind: SearchFieldKind, canonical: String): Either[SearchValueDecodeError, Json] =
    kind match {
      case SearchFieldKind.Keyword | SearchFieldKind.Text =>
        Right(Json.fromString(canonical))
      case SearchFieldKind.Integer =>
        SearchValueCodec.int.decodeCanonical(canonical).map(Json.fromInt)
      case SearchFieldKind.Long =>
        SearchValueCodec.long.decodeCanonical(canonical).map(Json.fromLong)
      case SearchFieldKind.Decimal =>
        SearchValueCodec.bigDecimal.decodeCanonical(canonical).map(Json.fromBigDecimal)
      case SearchFieldKind.Boolean =>
        SearchValueCodec.boolean.decodeCanonical(canonical).map(Json.fromBoolean)
      case SearchFieldKind.DateTime =>
        // strict_date_optional_time requires a standard ISO-8601 instant; the canonical string itself -
        // not the decoded Instant - is what gets stored/queried, since Instant's own canonical spelling
        // (enforced by requireCanonicalRoundTrip) is exactly that string.
        SearchValueCodec.instant.decodeCanonical(canonical).map(_ => Json.fromString(canonical))
      case SearchFieldKind.GeoPoint =>
        SearchValueCodec.geoPoint.decodeCanonical(canonical)
          .map(point => Json.obj("lat" -> Json.fromBigDecimal(point.lat), "lon" -> Json.fromBigDecimal(point.lon)))
    }
}
