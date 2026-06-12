package leaderboard.search.elasticsearch

import io.circe.Json
import io.circe.parser.parse
import leaderboard.model.QueryFailure
import zio.{IO, ZIO}

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration

final class ElasticsearchHttpJsonClient(
  host: String,
  port: Int,
) extends ElasticsearchJsonClient {
  private val client  = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
  private val baseUri = s"http://$host:$port"

  override def putJson(path: String, json: Json): IO[QueryFailure, Json] =
    request("PUT", path, Some(json.noSpaces), "application/json").flatMap(parseJson)

  override def postJson(path: String, json: Json): IO[QueryFailure, Json] =
    request("POST", path, Some(json.noSpaces), "application/json").flatMap(parseJson)

  override def postNdjson(path: String, payload: String): IO[QueryFailure, Json] =
    request("POST", path, Some(payload), "application/x-ndjson").flatMap(parseJson)

  override def getJson(path: String): IO[QueryFailure, Json] =
    request("GET", path, None, "application/json").flatMap(parseJson)

  override def delete(path: String): IO[QueryFailure, Unit] =
    request("DELETE", path, None, "application/json").flatMap { response =>
      if (response.statusCode() / 100 == 2) ZIO.unit
      else ZIO.fail(ElasticsearchJsonClient.failure(s"Unexpected status ${response.statusCode()}: ${Option(response.body()).getOrElse("")}"))
    }

  private def request(
    method: String,
    path: String,
    body: Option[String],
    contentType: String,
  ): IO[QueryFailure, HttpResponse[String]] =
    ZIO.attemptBlocking {
      val builder    = HttpRequest.newBuilder(URI.create(baseUri + path)).timeout(Duration.ofSeconds(30))
      val withMethod = body match {
        case Some(payload) =>
          builder.header("Content-Type", contentType).method(method, HttpRequest.BodyPublishers.ofString(payload))
        case None =>
          builder.method(method, HttpRequest.BodyPublishers.noBody())
      }
      client.send(withMethod.build(), HttpResponse.BodyHandlers.ofString())
    }.mapError(error => QueryFailure.fromThrowable(ElasticsearchJsonClient.OperationName, error))

  private def parseJson(response: HttpResponse[String]): IO[QueryFailure, Json] = {
    val body = Option(response.body()).getOrElse("")
    if (response.statusCode() / 100 != 2)
      ZIO.fail(ElasticsearchJsonClient.failure(s"Unexpected status ${response.statusCode()}: $body"))
    else
      ZIO.fromEither(parse(body).left.map(error => ElasticsearchJsonClient.failure(error.getMessage)))
  }
}
