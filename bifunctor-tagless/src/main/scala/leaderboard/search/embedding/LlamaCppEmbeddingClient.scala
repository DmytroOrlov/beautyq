package leaderboard.search.embedding

import io.circe.Json
import io.circe.parser.parse
import leaderboard.model.QueryFailure
import leaderboard.search.beautyq.contract.BeautyQSearchRuntimeContract
import zio.{IO, ZIO}

import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.time.Duration

final class LlamaCppEmbeddingClient(config: LlamaCppEmbeddingClientConfig) extends EmbeddingClient {
  private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

  override def embed(text: String): IO[QueryFailure, Vector[Double]] =
    request(text).flatMap(response => LlamaCppEmbeddingClient.decodeEmbedding(response))

  private def request(text: String): IO[QueryFailure, HttpResponse[String]] =
    ZIO.attemptBlocking {
      val requestJson = Json.obj(
        "input" -> Json.fromString(text),
        "model" -> Json.fromString(BeautyQSearchRuntimeContract.ManagedLocalQdrantEmbeddingModelName),
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
    LlamaCppEmbeddingResponseDecoder.decodeEmbeddingJson(json)
}
