package leaderboard.search.gen2.elasticsearch

import io.circe.{Json, JsonObject, parser}

/** One `search_after` scalar value, restricted to exactly the Elasticsearch `search_after` scalar domain
  * (string, number, boolean, null) - arrays and objects are never valid sort-value components and are
  * rejected as a typed decode error rather than silently accepted. */
sealed trait ElasticsearchSearchAfterValue

object ElasticsearchSearchAfterValue {
  final case class Text(value: String) extends ElasticsearchSearchAfterValue
  final case class Number(value: io.circe.JsonNumber) extends ElasticsearchSearchAfterValue
  final case class Bool(value: Boolean) extends ElasticsearchSearchAfterValue
  case object Null extends ElasticsearchSearchAfterValue

  private[elasticsearch] def toJson(value: ElasticsearchSearchAfterValue): Json =
    value match {
      case Text(v)   => Json.fromString(v)
      case Number(v) => Json.fromJsonNumber(v)
      case Bool(v)   => Json.fromBoolean(v)
      case Null      => Json.Null
    }

  private[elasticsearch] def fromJson(json: Json): Option[ElasticsearchSearchAfterValue] =
    if (json.isNull) Some(Null)
    else
      json.asString.map(Text.apply)
        .orElse(json.asNumber.map(numberValue))
        .orElse(json.asBoolean.map(Bool.apply))

  private def numberValue(number: io.circe.JsonNumber): Number = Number(number)
}

/** A cursor-carried generation identifier. This value is deliberately untrusted: it is only a lookup
  * key for the lifecycle owner and is never an executable Elasticsearch target. */
final case class ElasticsearchGenerationReference(value: String)

/** The backend-owned, versioned Elasticsearch cursor state encoded into `BackendCursorState`'s opaque
  * string by [[ElasticsearchCursorStateCodec]]. Domain code never parses this itself; envelope parsing
  * (version/hash/base64 framing) remains owned by `SearchCursorEnvelope`. The generation reference is an
  * untrusted lifecycle lookup key; only lifecycle authorization turns it into an executable target.
  */
final case class ElasticsearchCursorState(
  protocolVersion: ElasticsearchSearchCompilerVersion,
  generationReference: ElasticsearchGenerationReference,
  searchAfterValues: Vector[ElasticsearchSearchAfterValue],
)

sealed trait ElasticsearchCursorStateError

object ElasticsearchCursorStateError {
  final case class MalformedJson(message: String) extends ElasticsearchCursorStateError
  final case class MissingField(name: String) extends ElasticsearchCursorStateError
  final case class UnsupportedProtocolVersion(actual: String) extends ElasticsearchCursorStateError
  final case class InvalidGenerationReference(value: String) extends ElasticsearchCursorStateError
  final case class UnsupportedSearchAfterValueShape(index: Int) extends ElasticsearchCursorStateError
  final case class SearchAfterArityMismatch(expected: Int, actual: Int) extends ElasticsearchCursorStateError
}

/** Deterministic JSON encoding/strict typed decoding for [[ElasticsearchCursorState]]. No offset/`from`
  * state is ever represented - only an explicit protocol version, the untrusted generation reference, and the
  * exact `search_after` scalar tuple. `decode` validates arity against the compiled sort vector's size, so
  * a state that no longer matches the current plan's sort shape is rejected rather than silently
  * truncated/padded. */
object ElasticsearchCursorStateCodec {

  private val ProtocolVersionKey = "protocolVersion"
  private val GenerationReferenceKey = "generationReference"
  private val SearchAfterKey     = "searchAfter"

  def encode(state: ElasticsearchCursorState): String =
    Json.obj(
      ProtocolVersionKey -> Json.fromString(state.protocolVersion.value),
      GenerationReferenceKey -> Json.fromString(state.generationReference.value),
      SearchAfterKey     -> Json.arr(state.searchAfterValues.map(ElasticsearchSearchAfterValue.toJson)*),
    ).noSpaces

  def decode(raw: String, expectedSearchAfterArity: Int): Either[ElasticsearchCursorStateError, ElasticsearchCursorState] =
    for {
      json           <- parser.parse(raw).left.map(error => ElasticsearchCursorStateError.MalformedJson(error.message))
      obj            <- json.asObject.toRight(ElasticsearchCursorStateError.MalformedJson("expected a JSON object"))
      protocolValue  <- stringField(obj, ProtocolVersionKey)
      _              <- requireCurrentProtocol(protocolValue)
      generationReference <- stringField(obj, GenerationReferenceKey)
      _                   <- requireGenerationReference(generationReference)
      searchAfter    <- arrayField(obj, SearchAfterKey)
      searchAfterValues <- decodeSearchAfterValues(searchAfter)
      _              <- requireArity(expectedSearchAfterArity, searchAfterValues.length)
    } yield ElasticsearchCursorState(ElasticsearchSearchCompilerVersion.Current, ElasticsearchGenerationReference(generationReference), searchAfterValues)

  private def stringField(obj: JsonObject, name: String): Either[ElasticsearchCursorStateError, String] =
    obj(name).flatMap(_.asString).toRight(ElasticsearchCursorStateError.MissingField(name))

  private def arrayField(obj: JsonObject, name: String): Either[ElasticsearchCursorStateError, Vector[Json]] =
    obj(name).flatMap(_.asArray).map(_.toVector).toRight(ElasticsearchCursorStateError.MissingField(name))

  private def requireCurrentProtocol(actual: String): Either[ElasticsearchCursorStateError, Unit] =
    if (actual == ElasticsearchSearchCompilerVersion.Current.value) Right(())
    else Left(ElasticsearchCursorStateError.UnsupportedProtocolVersion(actual))

  private def requireGenerationReference(value: String): Either[ElasticsearchCursorStateError, Unit] =
    if (value.nonEmpty) Right(()) else Left(ElasticsearchCursorStateError.InvalidGenerationReference(value))

  private def decodeSearchAfterValues(values: Vector[Json]): Either[ElasticsearchCursorStateError, Vector[ElasticsearchSearchAfterValue]] =
    values.zipWithIndex.foldLeft[Either[ElasticsearchCursorStateError, Vector[ElasticsearchSearchAfterValue]]](Right(Vector.empty)) {
      case (acc, (json, index)) =>
        acc.flatMap { decoded =>
          ElasticsearchSearchAfterValue.fromJson(json) match {
            case Some(value) => Right(decoded :+ value)
            case None        => Left(ElasticsearchCursorStateError.UnsupportedSearchAfterValueShape(index))
          }
        }
    }

  private def requireArity(expected: Int, actual: Int): Either[ElasticsearchCursorStateError, Unit] =
    if (expected == actual) Right(()) else Left(ElasticsearchCursorStateError.SearchAfterArityMismatch(expected, actual))
}
