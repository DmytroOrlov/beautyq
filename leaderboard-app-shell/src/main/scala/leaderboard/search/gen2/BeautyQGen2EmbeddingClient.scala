package leaderboard.search.gen2

import io.circe.Json
import leaderboard.search.beautyq.gen2.wiring.BeautyQEmbeddingRequestError
import leaderboard.search.gen2.qdrant.*
import leaderboard.search.gen2.transport.*

/** Synchronous Gen2 adapter for the embedding boundary. The app shell owns
  * endpoint configuration; the Qdrant contract owns input/result binding. */
final class BeautyQGen2EmbeddingClient private (
  client: Gen2JsonHttpClient,
  endpointPath: String,
) extends QdrantQueryEmbeddingPort[BeautyQEmbeddingRequestError] {
  def embed(input: QdrantEmbeddingInput): Either[BeautyQEmbeddingRequestError, QdrantEmbeddingResult] =
    client.postJson(
      endpointPath,
      Json.obj(
        "input" -> Json.fromString(input.textValue),
        "model" -> Json.fromString(input.modelValue.model),
      ),
    ).left.map(mapTransport).flatMap { response =>
      for {
        data <- response.hcursor.downField("data").as[Vector[Json]].left.map(error => BeautyQEmbeddingRequestError.Transport(error.message))
        first <- data.headOption.toRight(BeautyQEmbeddingRequestError.Transport("embedding response data is empty"))
        values <- first.hcursor.downField("embedding").as[Vector[Double]].left.map(error => BeautyQEmbeddingRequestError.Transport(error.message))
        result <- QdrantEmbeddingResult.from(input, values).left.map(BeautyQEmbeddingRequestError.InvalidResult.apply)
      } yield result
    }

  private def mapTransport(error: Gen2HttpTransportError): BeautyQEmbeddingRequestError = error match {
    case Gen2HttpTransportError.ConnectionFailed(_, _, message) => BeautyQEmbeddingRequestError.Unavailable(message)
    case Gen2HttpTransportError.HttpFailure(_, _, 408, body) => BeautyQEmbeddingRequestError.Timeout(body)
    case Gen2HttpTransportError.HttpFailure(_, _, _, body) => BeautyQEmbeddingRequestError.Transport(body)
    case Gen2HttpTransportError.InvalidJsonResponse(_, _, _, body, message) => BeautyQEmbeddingRequestError.Transport(s"$message: $body")
    case Gen2HttpTransportError.RequestFailed(_, _, message) => BeautyQEmbeddingRequestError.Transport(message)
    case Gen2HttpTransportError.InvalidRequestPath(_, _, reason) => BeautyQEmbeddingRequestError.Transport(reason)
  }
}

object BeautyQGen2EmbeddingClient {
  def fromTransport(client: Gen2JsonHttpClient, endpointPath: String): BeautyQGen2EmbeddingClient =
    new BeautyQGen2EmbeddingClient(client, endpointPath)

  def jdk(
    baseUrl: String,
    endpointPath: String,
    connectTimeout: java.time.Duration,
    requestTimeout: java.time.Duration,
  ): Either[Gen2HttpTransportConfigError, BeautyQGen2EmbeddingClient] =
    for {
      endpoint <- Gen2HttpEndpoint.fromString(baseUrl)
      config <- Gen2HttpTransportConfig.create(endpoint, connectTimeout, requestTimeout)
    } yield fromTransport(Gen2JsonHttpClient.jdk(config), endpointPath)
}
