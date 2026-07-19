package leaderboard.search.gen2.transport

import io.circe.{Json, parser}

import java.net.{URI, URISyntaxException, URLEncoder}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.time.Duration

/** A validated HTTP endpoint whose path is owned by the transport boundary. */
final class Gen2HttpEndpoint private[transport] (val uri: URI)

object Gen2HttpEndpoint {
  def fromString(value: String): Either[Gen2HttpTransportConfigError, Gen2HttpEndpoint] =
    try {
      val uri = new URI(value)
      if (!Set("http", "https").contains(uri.getScheme)) Left(Gen2HttpTransportConfigError.InvalidEndpoint(value))
      else if (uri.getHost == null || uri.getHost.isEmpty) Left(Gen2HttpTransportConfigError.InvalidEndpoint(value))
      else if (uri.getPort != -1 && (uri.getPort < 1 || uri.getPort > 65535))
        Left(Gen2HttpTransportConfigError.InvalidEndpointPort(value, uri.getPort))
      else if (uri.getUserInfo != null || uri.getQuery != null || uri.getFragment != null)
        Left(Gen2HttpTransportConfigError.InvalidEndpoint(value))
      else if (Option(uri.getPath).exists(path => path.nonEmpty && path != "/"))
        Left(Gen2HttpTransportConfigError.InvalidEndpoint(value))
      else Right(new Gen2HttpEndpoint(new URI(uri.getScheme, null, uri.getHost, uri.getPort, null, null, null)))
    } catch {
      case _: URISyntaxException => Left(Gen2HttpTransportConfigError.InvalidEndpoint(value))
    }
}

sealed trait Gen2HttpTransportConfigError
object Gen2HttpTransportConfigError {
  final case class InvalidEndpoint(value: String) extends Gen2HttpTransportConfigError
  final case class InvalidEndpointPort(endpoint: String, port: Int) extends Gen2HttpTransportConfigError
  final case class NonPositiveConnectTimeout(value: Duration) extends Gen2HttpTransportConfigError
  final case class NonPositiveRequestTimeout(value: Duration) extends Gen2HttpTransportConfigError
  final case class InvalidHeaderName(value: String) extends Gen2HttpTransportConfigError
}

final case class Gen2HttpQueryParameter(name: String, value: String)
final case class Gen2HttpHeader(name: String, value: String)

final class Gen2HttpTransportConfig private (
  val endpoint: Gen2HttpEndpoint,
  val connectTimeout: Duration,
  val requestTimeout: Duration,
  val defaultHeaders: Vector[Gen2HttpHeader],
)

object Gen2HttpTransportConfig {
  def create(
    endpoint: Gen2HttpEndpoint,
    connectTimeout: Duration,
    requestTimeout: Duration,
  ): Either[Gen2HttpTransportConfigError, Gen2HttpTransportConfig] =
    create(endpoint, connectTimeout, requestTimeout, Vector.empty)

  def create(
    endpoint: Gen2HttpEndpoint,
    connectTimeout: Duration,
    requestTimeout: Duration,
    defaultHeaders: Vector[Gen2HttpHeader],
  ): Either[Gen2HttpTransportConfigError, Gen2HttpTransportConfig] =
    if (connectTimeout.isZero || connectTimeout.isNegative)
      Left(Gen2HttpTransportConfigError.NonPositiveConnectTimeout(connectTimeout))
    else if (requestTimeout.isZero || requestTimeout.isNegative)
      Left(Gen2HttpTransportConfigError.NonPositiveRequestTimeout(requestTimeout))
    else {
      defaultHeaders.find(header => header.name.trim.isEmpty) match {
        case Some(header) => Left(Gen2HttpTransportConfigError.InvalidHeaderName(header.name))
        case None         => Right(new Gen2HttpTransportConfig(endpoint, connectTimeout, requestTimeout, defaultHeaders))
      }
    }
}

sealed trait Gen2HttpTransportError
object Gen2HttpTransportError {
  final case class InvalidRequestPath(method: String, path: String, reason: String) extends Gen2HttpTransportError
  final case class RequestFailed(method: String, path: String, message: String) extends Gen2HttpTransportError
  final case class ConnectionFailed(method: String, path: String, message: String) extends Gen2HttpTransportError
  final case class HttpFailure(method: String, path: String, status: Int, body: String) extends Gen2HttpTransportError
  final case class InvalidJsonResponse(method: String, path: String, status: Int, body: String, message: String) extends Gen2HttpTransportError
}

/**
  * Synchronous, backend-neutral JSON/HTTP transport. Backend adapters own endpoint paths and wire
  * bodies; this module owns URI confinement, query encoding, headers, JDK transport and typed errors.
  */
trait Gen2JsonHttpClient {
  def putJson(path: String, body: Json, query: Vector[Gen2HttpQueryParameter] = Vector.empty, headers: Vector[Gen2HttpHeader] = Vector.empty): Either[Gen2HttpTransportError, Json]
  def post(path: String, query: Vector[Gen2HttpQueryParameter] = Vector.empty, headers: Vector[Gen2HttpHeader] = Vector.empty): Either[Gen2HttpTransportError, Json]
  def postJson(path: String, body: Json, query: Vector[Gen2HttpQueryParameter] = Vector.empty, headers: Vector[Gen2HttpHeader] = Vector.empty): Either[Gen2HttpTransportError, Json]
  def postNdjson(path: String, body: String, query: Vector[Gen2HttpQueryParameter] = Vector.empty, headers: Vector[Gen2HttpHeader] = Vector.empty): Either[Gen2HttpTransportError, Json]
  def getJson(path: String, query: Vector[Gen2HttpQueryParameter] = Vector.empty, headers: Vector[Gen2HttpHeader] = Vector.empty): Either[Gen2HttpTransportError, Json]
  def delete(path: String, query: Vector[Gen2HttpQueryParameter] = Vector.empty, headers: Vector[Gen2HttpHeader] = Vector.empty): Either[Gen2HttpTransportError, Unit]
}

object Gen2JsonHttpClient {
  def jdk(config: Gen2HttpTransportConfig): Gen2JsonHttpClient =
    new JdkGen2JsonHttpClient(
      config,
      HttpClient.newBuilder().connectTimeout(config.connectTimeout).followRedirects(HttpClient.Redirect.NEVER).build(),
    )
}

private final class JdkGen2JsonHttpClient(
  config: Gen2HttpTransportConfig,
  http: HttpClient,
) extends Gen2JsonHttpClient {
  import Gen2HttpTransportError.*

  def putJson(path: String, body: Json, query: Vector[Gen2HttpQueryParameter], headers: Vector[Gen2HttpHeader]): Either[Gen2HttpTransportError, Json] =
    jsonRequest("PUT", path, Some(body.noSpaces), "application/json", query, headers)

  def post(path: String, query: Vector[Gen2HttpQueryParameter], headers: Vector[Gen2HttpHeader]): Either[Gen2HttpTransportError, Json] =
    jsonRequest("POST", path, None, "application/json", query, headers)

  def postJson(path: String, body: Json, query: Vector[Gen2HttpQueryParameter], headers: Vector[Gen2HttpHeader]): Either[Gen2HttpTransportError, Json] =
    jsonRequest("POST", path, Some(body.noSpaces), "application/json", query, headers)

  def postNdjson(path: String, body: String, query: Vector[Gen2HttpQueryParameter], headers: Vector[Gen2HttpHeader]): Either[Gen2HttpTransportError, Json] =
    jsonRequest("POST", path, Some(body), "application/x-ndjson", query, headers)

  def getJson(path: String, query: Vector[Gen2HttpQueryParameter], headers: Vector[Gen2HttpHeader]): Either[Gen2HttpTransportError, Json] =
    jsonRequest("GET", path, None, "application/json", query, headers)

  def delete(path: String, query: Vector[Gen2HttpQueryParameter], headers: Vector[Gen2HttpHeader]): Either[Gen2HttpTransportError, Unit] =
    send("DELETE", path, None, "application/json", query, headers).map(_ => ())

  private def jsonRequest(
    method: String,
    path: String,
    body: Option[String],
    contentType: String,
    query: Vector[Gen2HttpQueryParameter],
    headers: Vector[Gen2HttpHeader],
  ): Either[Gen2HttpTransportError, Json] =
    send(method, path, body, contentType, query, headers).flatMap { response =>
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
    query: Vector[Gen2HttpQueryParameter],
    headers: Vector[Gen2HttpHeader],
  ): Either[Gen2HttpTransportError, HttpResponse[String]] =
    requestUri(method, path, query).flatMap { uri =>
      try {
        val publisher = body match {
          case Some(value) => HttpRequest.BodyPublishers.ofString(value, StandardCharsets.UTF_8)
          case None        => HttpRequest.BodyPublishers.noBody()
        }
        val builder = HttpRequest.newBuilder(uri).timeout(config.requestTimeout).method(method, publisher)
        (Gen2HttpHeader("Accept", "application/json") +: (config.defaultHeaders ++ headers)).foreach { header =>
          builder.header(header.name, header.value)
        }
        body.foreach(_ => builder.header("Content-Type", contentType))
        val response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        if (response.statusCode() >= 200 && response.statusCode() < 300) Right(response)
        else Left(HttpFailure(method, path, response.statusCode(), response.body()))
      } catch {
        case interrupted: InterruptedException =>
          Thread.currentThread().interrupt()
          Left(ConnectionFailed(method, path, Option(interrupted.getMessage).getOrElse(interrupted.getClass.getName)))
        case error: java.io.IOException =>
          Left(ConnectionFailed(method, path, Option(error.getMessage).getOrElse(error.getClass.getName)))
        case error: IllegalArgumentException =>
          Left(RequestFailed(method, path, Option(error.getMessage).getOrElse(error.getClass.getName)))
      }
    }

  private def requestUri(method: String, path: String, query: Vector[Gen2HttpQueryParameter]): Either[Gen2HttpTransportError, URI] =
    validatePath(path).left.map(reason => InvalidRequestPath(method, path, reason)).flatMap { _ =>
      try {
        val queryString = query.map(parameter => s"${encode(parameter.name)}=${encode(parameter.value)}").mkString("&")
        val suffix = if (queryString.isEmpty) path else s"$path?$queryString"
        Right(URI.create(config.endpoint.uri.toString + suffix))
      } catch {
        case _: IllegalArgumentException => Left(InvalidRequestPath(method, path, "path or query cannot be represented as a URI"))
      }
    }

  private def encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

  private def validatePath(path: String): Either[String, Unit] =
    if (!path.startsWith("/")) Left("path must start with exactly one slash")
    else if (path.startsWith("//") || path.drop(1).contains("//")) Left("protocol-relative and repeated-slash paths are forbidden")
    else if (path.contains("?")) Left("query components are forbidden")
    else if (path.contains("#")) Left("fragment components are forbidden")
    else if (path.split('/').contains("..")) Left("parent traversal is forbidden")
    else if (path.contains("://")) Left("scheme or authority replacement is forbidden")
    else Right(())
}
