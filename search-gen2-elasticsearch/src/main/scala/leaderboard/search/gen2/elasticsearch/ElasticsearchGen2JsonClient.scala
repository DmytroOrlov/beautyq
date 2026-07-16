package leaderboard.search.gen2.elasticsearch

import io.circe.{Json, parser}

import java.net.{URI, URISyntaxException}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.time.Duration

final class ElasticsearchGen2Endpoint private (val uri: URI)

object ElasticsearchGen2Endpoint {
  def fromString(value: String): Either[ElasticsearchGen2TransportConfigError, ElasticsearchGen2Endpoint] =
    try {
      val uri = new URI(value)
      if (!Set("http", "https").contains(uri.getScheme)) Left(ElasticsearchGen2TransportConfigError.InvalidEndpoint(value))
      else if (uri.getHost == null || uri.getHost.isEmpty) Left(ElasticsearchGen2TransportConfigError.InvalidEndpoint(value))
      else if (uri.getPort != -1 && (uri.getPort < 1 || uri.getPort > 65535))
        Left(ElasticsearchGen2TransportConfigError.InvalidEndpointPort(value, uri.getPort))
      else if (uri.getUserInfo != null || uri.getQuery != null || uri.getFragment != null) Left(ElasticsearchGen2TransportConfigError.InvalidEndpoint(value))
      else if (Option(uri.getPath).exists(path => path.nonEmpty && path != "/")) Left(ElasticsearchGen2TransportConfigError.InvalidEndpoint(value))
      else Right(new ElasticsearchGen2Endpoint(new URI(uri.getScheme, null, uri.getHost, uri.getPort, null, null, null)))
    } catch {
      case _: URISyntaxException => Left(ElasticsearchGen2TransportConfigError.InvalidEndpoint(value))
    }
}

sealed trait ElasticsearchGen2TransportConfigError
object ElasticsearchGen2TransportConfigError {
  final case class InvalidEndpoint(value: String) extends ElasticsearchGen2TransportConfigError
  final case class InvalidEndpointPort(endpoint: String, port: Int) extends ElasticsearchGen2TransportConfigError
  final case class NonPositiveConnectTimeout(value: Duration) extends ElasticsearchGen2TransportConfigError
  final case class NonPositiveRequestTimeout(value: Duration) extends ElasticsearchGen2TransportConfigError
}

final class ElasticsearchGen2TransportConfig private (
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
    if (connectTimeout.isZero || connectTimeout.isNegative) Left(ElasticsearchGen2TransportConfigError.NonPositiveConnectTimeout(connectTimeout))
    else if (requestTimeout.isZero || requestTimeout.isNegative) Left(ElasticsearchGen2TransportConfigError.NonPositiveRequestTimeout(requestTimeout))
    else Right(new ElasticsearchGen2TransportConfig(endpoint, connectTimeout, requestTimeout))
}

sealed trait ElasticsearchGen2TransportError
object ElasticsearchGen2TransportError {
  final case class InvalidRequestPath(method: String, path: String, reason: String) extends ElasticsearchGen2TransportError
  final case class RequestFailed(method: String, path: String, message: String) extends ElasticsearchGen2TransportError
  final case class ConnectionFailed(method: String, path: String, message: String) extends ElasticsearchGen2TransportError
  final case class HttpFailure(method: String, path: String, status: Int, body: String) extends ElasticsearchGen2TransportError
  final case class InvalidJsonResponse(method: String, path: String, status: Int, body: String, message: String) extends ElasticsearchGen2TransportError
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
    new JdkElasticsearchGen2JsonClient(
      config,
      HttpClient.newBuilder().connectTimeout(config.connectTimeout).followRedirects(HttpClient.Redirect.NEVER).build(),
    )
}

private final class JdkElasticsearchGen2JsonClient(
  config: ElasticsearchGen2TransportConfig,
  http: HttpClient,
) extends ElasticsearchGen2JsonClient {
  import ElasticsearchGen2TransportError.*

  def putJson(path: String, body: Json): Either[ElasticsearchGen2TransportError, Json] = jsonRequest("PUT", path, Some(body.noSpaces), "application/json")
  def post(path: String): Either[ElasticsearchGen2TransportError, Json] = jsonRequest("POST", path, None, "application/json")
  def postJson(path: String, body: Json): Either[ElasticsearchGen2TransportError, Json] = jsonRequest("POST", path, Some(body.noSpaces), "application/json")
  def postNdjson(path: String, body: String): Either[ElasticsearchGen2TransportError, Json] = jsonRequest("POST", path, Some(body), "application/x-ndjson")
  def getJson(path: String): Either[ElasticsearchGen2TransportError, Json] = jsonRequest("GET", path, None, "application/json")

  def delete(path: String): Either[ElasticsearchGen2TransportError, Unit] =
    send("DELETE", path, None, "application/json").map(_ => ())

  private def jsonRequest(method: String, path: String, body: Option[String], contentType: String): Either[ElasticsearchGen2TransportError, Json] =
    send(method, path, body, contentType).flatMap { response =>
      parser.parse(response.body()) match {
        case Right(json) => Right(json)
        case Left(error) => Left(InvalidJsonResponse(method, path, response.statusCode(), response.body(), error.message))
      }
    }

  private def send(
    method: String,
    path: String,
    body: Option[String],
    contentType: String,
  ): Either[ElasticsearchGen2TransportError, HttpResponse[String]] =
    requestUri(method, path).flatMap { uri =>
      try {
        val publisher = body match {
          case Some(value) => HttpRequest.BodyPublishers.ofString(value, StandardCharsets.UTF_8)
          case None        => HttpRequest.BodyPublishers.noBody()
        }
        val builder = HttpRequest.newBuilder(uri).timeout(config.requestTimeout).method(method, publisher).header("Accept", "application/json")
        if (body.nonEmpty) {
          builder.header("Content-Type", contentType)
          (): Unit
        }
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() >= 200 && response.statusCode() < 300) Right(response)
        else Left(HttpFailure(method, path, response.statusCode(), response.body()))
      } catch {
        case interrupted: InterruptedException =>
          Thread.currentThread().interrupt()
          Left(ConnectionFailed(method, path, Option(interrupted.getMessage).getOrElse(interrupted.getClass.getName)))
        case error: java.io.IOException => Left(ConnectionFailed(method, path, Option(error.getMessage).getOrElse(error.getClass.getName)))
        case error: IllegalArgumentException => Left(RequestFailed(method, path, Option(error.getMessage).getOrElse(error.getClass.getName)))
      }
    }

  private def requestUri(method: String, path: String): Either[ElasticsearchGen2TransportError, URI] =
    validatePath(path).left.map(reason => InvalidRequestPath(method, path, reason))
      .flatMap { _ =>
        try Right(new URI(config.endpoint.uri.getScheme, null, config.endpoint.uri.getHost, config.endpoint.uri.getPort, path, null, null))
        catch { case _: URISyntaxException => Left(InvalidRequestPath(method, path, "path cannot be represented as a URI")) }
      }

  private def validatePath(path: String): Either[String, Unit] =
    if (!path.startsWith("/")) Left("path must start with exactly one slash")
    else if (path.startsWith("//") || path.drop(1).contains("//")) Left("protocol-relative and repeated-slash paths are forbidden")
    else if (path.contains("?")) Left("query components are forbidden")
    else if (path.contains("#")) Left("fragment components are forbidden")
    else if (path.split('/').contains("..")) Left("parent traversal is forbidden")
    else if (path.contains("://")) Left("scheme or authority replacement is forbidden")
    else Right(())
}
