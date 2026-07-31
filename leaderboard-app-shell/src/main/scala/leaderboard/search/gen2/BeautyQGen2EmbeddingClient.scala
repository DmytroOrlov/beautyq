package leaderboard.search.gen2

import io.circe.{Json, HCursor}
import leaderboard.search.beautyq.gen2.wiring.BeautyQEmbeddingRequestError
import leaderboard.search.gen2.qdrant.*
import leaderboard.search.gen2.transport.*

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
        actualModel <- decodeModel(response.hcursor, input).left.map(toEmbeddingError)
        _ <- assertExpectedModel(input, actualModel)
        dataCursor = response.hcursor.downField("data")
        data <- dataCursor.as[Vector[Json]].left.map(_ => BeautyQEmbeddingRequestError.MalformedResponse("embedding response data is missing or not an array"))
        first <- data.headOption.toRight(BeautyQEmbeddingRequestError.MalformedResponse("embedding response data is empty"))
        values <- first.hcursor.downField("embedding").as[Vector[Double]].left.map(_ => BeautyQEmbeddingRequestError.MalformedResponse("embedding response vector is missing or not an array"))
        result <- QdrantEmbeddingResult.from(input, values).left.map(BeautyQEmbeddingRequestError.InvalidResult.apply)
      } yield result
    }

  private def decodeModel(cursor: HCursor, input: QdrantEmbeddingInput): Either[QdrantEmbeddingError, String] =
    cursor.downField("model").focus match {
      case None         => Left(QdrantEmbeddingError.ModelMismatch(input.modelValue, input.modelValue))
      case Some(value) if !value.isString => Left(QdrantEmbeddingError.ModelMismatch(input.modelValue, input.modelValue))
      case Some(value) => value.asString match {
        case Some(name) => Right(name)
        case None       => Left(QdrantEmbeddingError.ModelMismatch(input.modelValue, input.modelValue))
      }
    }

  private def assertExpectedModel(
    input: QdrantEmbeddingInput,
    actualName: String,
  ): Either[BeautyQEmbeddingRequestError, Unit] =
    if (actualName == input.modelValue.model) Right(())
    else {
      val actual = QdrantEmbeddingModelIdentity(
        provider = input.modelValue.provider,
        model = actualName,
        revision = input.modelValue.revision,
        dimension = input.modelValue.dimension,
        textFormatVersion = input.modelValue.textFormatVersion,
      )
      Left(BeautyQEmbeddingRequestError.InvalidResult(QdrantEmbeddingError.ModelMismatch(input.modelValue, actual)))
    }

  private def toEmbeddingError(error: QdrantEmbeddingError): BeautyQEmbeddingRequestError =
    BeautyQEmbeddingRequestError.InvalidResult(error)

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
