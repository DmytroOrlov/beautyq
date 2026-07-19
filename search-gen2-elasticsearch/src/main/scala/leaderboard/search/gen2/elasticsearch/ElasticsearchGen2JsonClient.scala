package leaderboard.search.gen2.elasticsearch

import io.circe.Json
import leaderboard.search.gen2.transport.*

import java.time.Duration

/** Compatibility names for the existing Elasticsearch-facing callers. The implementation lives in
  * the neutral transport module; Elasticsearch owns only this thin adapter surface. */
final class ElasticsearchGen2Endpoint private[elasticsearch] (private[elasticsearch] val underlying: Gen2HttpEndpoint)

object ElasticsearchGen2Endpoint {
  def fromString(value: String): Either[ElasticsearchGen2TransportConfigError, ElasticsearchGen2Endpoint] =
    Gen2HttpEndpoint.fromString(value).map(new ElasticsearchGen2Endpoint(_))
}

type ElasticsearchGen2TransportConfigError = Gen2HttpTransportConfigError
object ElasticsearchGen2TransportConfigError {
  export Gen2HttpTransportConfigError.*
}

final class ElasticsearchGen2TransportConfig private[elasticsearch] (
  private[elasticsearch] val underlying: Gen2HttpTransportConfig,
  val endpoint: ElasticsearchGen2Endpoint,
  val connectTimeout: Duration,
  val requestTimeout: Duration,
)

object ElasticsearchGen2TransportConfig {
  def create(
    endpoint: ElasticsearchGen2Endpoint,
    connectTimeout: Duration,
    requestTimeout: Duration,
  ): Either[ElasticsearchGen2TransportConfigError, ElasticsearchGen2TransportConfig] =
    Gen2HttpTransportConfig.create(endpoint.underlying, connectTimeout, requestTimeout).map { config =>
      new ElasticsearchGen2TransportConfig(config, endpoint, connectTimeout, requestTimeout)
    }
}

type ElasticsearchGen2TransportError = Gen2HttpTransportError
object ElasticsearchGen2TransportError {
  export Gen2HttpTransportError.*
}

trait ElasticsearchGen2JsonClient {
  def putJson(path: String, body: Json): Either[ElasticsearchGen2TransportError, Json]
  def post(path: String): Either[ElasticsearchGen2TransportError, Json]
  def postJson(path: String, body: Json): Either[ElasticsearchGen2TransportError, Json]
  def postNdjson(path: String, body: String): Either[ElasticsearchGen2TransportError, Json]
  def getJson(path: String): Either[ElasticsearchGen2TransportError, Json]
  def delete(path: String): Either[ElasticsearchGen2TransportError, Unit]
}

object ElasticsearchGen2JsonClient {
  def jdk(config: ElasticsearchGen2TransportConfig): ElasticsearchGen2JsonClient =
    new AdapterElasticsearchGen2JsonClient(Gen2JsonHttpClient.jdk(config.underlying))
}

private final class AdapterElasticsearchGen2JsonClient(
  delegate: Gen2JsonHttpClient,
) extends ElasticsearchGen2JsonClient {
  def putJson(path: String, body: Json): Either[ElasticsearchGen2TransportError, Json] = delegate.putJson(path, body)
  def post(path: String): Either[ElasticsearchGen2TransportError, Json] = delegate.post(path)
  def postJson(path: String, body: Json): Either[ElasticsearchGen2TransportError, Json] = delegate.postJson(path, body)
  def postNdjson(path: String, body: String): Either[ElasticsearchGen2TransportError, Json] = delegate.postNdjson(path, body)
  def getJson(path: String): Either[ElasticsearchGen2TransportError, Json] = delegate.getJson(path)
  def delete(path: String): Either[ElasticsearchGen2TransportError, Unit] = delegate.delete(path)
}
