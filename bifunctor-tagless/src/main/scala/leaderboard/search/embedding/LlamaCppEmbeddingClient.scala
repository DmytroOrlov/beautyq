package leaderboard.search.embedding

import io.circe.{Decoder, Json}
import io.circe.parser.parse
import leaderboard.model.QueryFailure
import zio.{IO, ZIO}

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration

final case class LlamaCppEmbeddingClientConfig(
  baseUrl: String,
  endpointPath: String,
)

final class LlamaCppEmbeddingClient(config: LlamaCppEmbeddingClientConfig) extends EmbeddingClient {
  private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

  override def embed(text: String): IO[QueryFailure, Vector[Double]] =
    request(text).flatMap(response => LlamaCppEmbeddingClient.decodeEmbedding(response))

  private def request(text: String): IO[QueryFailure, HttpResponse[String]] =
    ZIO.attemptBlocking {
      val requestJson = Json.obj(
        "input" -> Json.fromString(text),
        "model" -> Json.fromString("local-llama-cpp-embedding"),
      )
      val request = HttpRequest
        .newBuilder(URI.create(config.baseUrl + config.endpointPath))
        .timeout(Duration.ofSeconds(30))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(requestJson.noSpaces))
        .build()

      client.send(request, HttpResponse.BodyHandlers.ofString())
    }.mapError(error => QueryFailure.fromThrowable("execute-llama-cpp-http", error))

}

object LlamaCppEmbeddingClient {
  def decodeEmbedding(response: HttpResponse[String]): IO[QueryFailure, Vector[Double]] = {
    val body = Option(response.body()).getOrElse("")
    if (response.statusCode() / 100 != 2) {
      ZIO.fail(QueryFailure.operation("llama-cpp-embedding", s"Unexpected status ${response.statusCode()}: $body"))
    } else {
      ZIO.fromEither(parse(body).left.map(error => QueryFailure.operation("llama-cpp-embedding", error.getMessage)))
        .flatMap { json =>
          decodeEmbeddingJson(json)
        }
    }
  }

  def decodeEmbeddingJson(json: Json): IO[QueryFailure, Vector[Double]] =
    for {
      data <- ZIO.fromEither(
        json.hcursor.downField("data").as[Vector[Json]].left.map(error => QueryFailure.operation("llama-cpp-embedding", error.message))
      )
      first <- ZIO.fromOption(data.headOption).orElseFail(QueryFailure.operation("llama-cpp-embedding", "Missing embedding data"))
      embedding <- ZIO.fromEither(
        first.hcursor.downField("embedding").as[Vector[Double]].left.map(error => QueryFailure.operation("llama-cpp-embedding", error.message))
      )
    } yield embedding
}
