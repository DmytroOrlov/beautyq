package leaderboard.search.gen2.qdrant

import io.circe.Json
import leaderboard.search.gen2.transport.*

/** A Qdrant resource name is safe to place in one URI path segment. */
final class QdrantResourceName private (val value: String)

object QdrantResourceName {
  def from(value: String): Either[QdrantResourceNameError, QdrantResourceName] =
    if (value.length < 1 || value.length > 255) Left(QdrantResourceNameError.Invalid(value))
    else if (!value.matches("[A-Za-z0-9][A-Za-z0-9_.-]*")) Left(QdrantResourceNameError.Invalid(value))
    else Right(new QdrantResourceName(value))
}

sealed trait QdrantResourceNameError
object QdrantResourceNameError {
  final case class Invalid(value: String) extends QdrantResourceNameError
}

/** API keys remain an opaque header value and never appear in transport errors or diagnostics. */
final class QdrantApiKey private[qdrant] (private[qdrant] val value: String)

object QdrantApiKey {
  def from(value: String): Either[QdrantApiKeyError, QdrantApiKey] =
    if (value.trim.isEmpty) Left(QdrantApiKeyError.Blank)
    else Right(new QdrantApiKey(value))
}

sealed trait QdrantApiKeyError
object QdrantApiKeyError {
  case object Blank extends QdrantApiKeyError
}

/** Exact Qdrant 1.18.3 REST paths over the neutral Gen2 JSON transport. */
trait QdrantGen2Client {
  def getCollection(collection: QdrantResourceName): Either[Gen2HttpTransportError, Json]
  def listAliases(): Either[Gen2HttpTransportError, Json]
  def createCollection(collection: QdrantResourceName, body: Json): Either[Gen2HttpTransportError, Json]
  def createPayloadIndex(collection: QdrantResourceName, body: Json): Either[Gen2HttpTransportError, Json]
  def upsertPoints(collection: QdrantResourceName, body: Json): Either[Gen2HttpTransportError, Json]
  def countPoints(collection: QdrantResourceName): Either[Gen2HttpTransportError, Json]
  def updateAliases(body: Json): Either[Gen2HttpTransportError, Json]
  def queryPoints(target: QdrantResourceName, body: Json): Either[Gen2HttpTransportError, Json]
}

object QdrantGen2Client {
  def jdk(config: Gen2HttpTransportConfig, apiKey: Option[QdrantApiKey] = None): QdrantGen2Client =
    new DefaultQdrantGen2Client(Gen2JsonHttpClient.jdk(config), apiKey)

  def fromTransport(client: Gen2JsonHttpClient, apiKey: Option[QdrantApiKey] = None): QdrantGen2Client =
    new DefaultQdrantGen2Client(client, apiKey)
}

private final class DefaultQdrantGen2Client(
  http: Gen2JsonHttpClient,
  apiKey: Option[QdrantApiKey],
) extends QdrantGen2Client {
  private val headers: Vector[Gen2HttpHeader] = apiKey.map(key => Gen2HttpHeader("api-key", key.value)).toVector

  def getCollection(collection: QdrantResourceName): Either[Gen2HttpTransportError, Json] =
    http.getJson(collectionPath(collection), headers = headers)

  def listAliases(): Either[Gen2HttpTransportError, Json] =
    http.getJson("/aliases", headers = headers)

  def createCollection(collection: QdrantResourceName, body: Json): Either[Gen2HttpTransportError, Json] =
    http.putJson(collectionPath(collection), body, headers = headers)

  def createPayloadIndex(collection: QdrantResourceName, body: Json): Either[Gen2HttpTransportError, Json] =
    http.putJson(collectionPath(collection) + "/index", body, query = Vector(Gen2HttpQueryParameter("wait", "true")), headers = headers)

  def upsertPoints(collection: QdrantResourceName, body: Json): Either[Gen2HttpTransportError, Json] =
    http.putJson(collectionPath(collection) + "/points", body, query = Vector(Gen2HttpQueryParameter("wait", "true")), headers = headers)

  def countPoints(collection: QdrantResourceName): Either[Gen2HttpTransportError, Json] =
    http.postJson(collectionPath(collection) + "/points/count", Json.obj("exact" -> Json.fromBoolean(true)), headers = headers)

  def updateAliases(body: Json): Either[Gen2HttpTransportError, Json] =
    http.postJson("/collections/aliases", body, headers = headers)

  def queryPoints(target: QdrantResourceName, body: Json): Either[Gen2HttpTransportError, Json] =
    http.postJson(collectionPath(target) + "/points/query", body, headers = headers)

  private def collectionPath(collection: QdrantResourceName): String = s"/collections/${collection.value}"
}
