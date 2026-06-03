package leaderboard.search

import io.circe.Json
import io.circe.parser.parse
import leaderboard.model.QueryFailure
import zio.{IO, ZIO}

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration

final class QdrantTestClient(host: String, port: Int) {
  private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
  private val baseUri = s"http://$host:$port"

  def createCollection(path: String, json: Json): IO[QueryFailure, Json] =
    request("PUT", path, Some(json.noSpaces), "application/json").flatMap(parseJson("create-qdrant-collection", _))

  def deleteCollection(path: String): IO[QueryFailure, Unit] =
    request("DELETE", path).flatMap { response =>
      val body = Option(response.body).getOrElse("")
      if (response.statusCode / 100 == 2) ZIO.unit
      else ZIO.fail(QueryFailure.operation("delete-qdrant-collection", s"Unexpected status ${response.statusCode}: $body"))
    }

  private def request(
    method: String,
    path: String,
    body: Option[String] = None,
    contentType: String = "application/json",
  ): IO[QueryFailure, HttpResponse[String]] =
    ZIO.attemptBlocking {
      val builder = HttpRequest.newBuilder(URI.create(baseUri + path)).timeout(Duration.ofSeconds(30))
      val withMethod = body match {
        case Some(payload) => builder.header("Content-Type", contentType).method(method, HttpRequest.BodyPublishers.ofString(payload))
        case None => builder.method(method, HttpRequest.BodyPublishers.noBody())
      }
      client.send(withMethod.build(), HttpResponse.BodyHandlers.ofString())
    }.mapError(QueryFailure.fromThrowable("execute-qdrant-http", _))

  private def parseJson(operationName: String, response: HttpResponse[String]): IO[QueryFailure, Json] = {
    val body = Option(response.body).getOrElse("")
    if (response.statusCode / 100 != 2) {
      ZIO.fail(QueryFailure.operation(operationName, s"Unexpected status ${response.statusCode}: $body"))
    } else {
      ZIO.fromEither(parse(body).left.map(error => QueryFailure.operation(operationName, error.getMessage)))
    }
  }
}
