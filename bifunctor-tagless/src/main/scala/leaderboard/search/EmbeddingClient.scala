package leaderboard.search

import leaderboard.model.QueryFailure
import zio.IO

trait EmbeddingClient {
  def embed(text: String): IO[QueryFailure, Vector[Double]]
}
