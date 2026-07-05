package leaderboard.search.embedding

import io.circe.Json
import leaderboard.model.QueryFailure
import zio.{IO, ZIO}

object LlamaCppEmbeddingResponseDecoder {
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
